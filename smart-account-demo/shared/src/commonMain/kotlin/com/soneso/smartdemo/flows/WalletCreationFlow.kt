package com.soneso.smartdemo.flows

/**
 * Business logic for creating a new smart account wallet.
 *
 * Demonstrates the full wallet creation flow using the Smart Account SDK:
 * 1. Ensure the deployer account is funded (needed after testnet resets).
 * 2. Register a passkey via the platform's WebAuthn provider.
 * 3. Deploy the smart account contract to the Stellar network.
 * 4. Fund the wallet with XLM via the relayer.
 * 5. Deploy the DEMO token (if not already deployed) and mint 10,000 DEMO.
 *
 * Uses [OZSmartAccountKit.walletOperations.createWallet] as the main SDK entry point.
 * The DEMO token step is non-fatal -- wallet creation succeeds even if token minting fails.
 */

import com.soneso.smartdemo.config.DemoConfig
import com.soneso.smartdemo.state.ActivityLogState
import com.soneso.smartdemo.state.DemoState
import com.soneso.smartdemo.token.DemoTokenService
import com.soneso.smartdemo.util.fetchXlmBalance
import com.soneso.smartdemo.util.formatStroopsAsXlm
import com.soneso.smartdemo.util.isUserCancellation
import com.soneso.stellar.sdk.FriendBot
import com.soneso.stellar.sdk.rpc.SorobanServer
import com.soneso.stellar.sdk.smartaccount.oz.OZSmartAccountConfig

/**
 * Result of a successful wallet creation.
 *
 * @property credentialId The Base64URL-encoded passkey credential ID registered on the device.
 * @property contractId The C-address of the deployed smart account contract.
 * @property transactionHash The Stellar transaction hash for the deployment, if available.
 * @property xlmBalance The initial XLM balance formatted for display (e.g. "100.0").
 * @property demoTokenBalance The minted DEMO balance formatted for display, or null if minting failed.
 */
data class WalletCreationResult(
    val credentialId: String,
    val contractId: String,
    val transactionHash: String?,
    val nickname: String?,
    val xlmBalance: String,
    val demoTokenBalance: String?
)

/**
 * Creates a new smart account wallet with passkey authentication.
 *
 * SDK workflow:
 * 1. Ensure the deployer account is funded (needed after testnet reset). The deployer
 *    signs the deployment transaction on behalf of the user via the relayer.
 * 2. Call [OZSmartAccountKit.walletOperations.createWallet] with the specified autoSubmit
 *    and autoFund settings. This triggers the WebAuthn passkey registration on the device,
 *    then optionally deploys the smart account contract to the Stellar network.
 * 3. Update [DemoState] so all screens can see the new connection.
 * 4. Fetch the initial XLM balance via the Stellar Asset Contract (SAC).
 * 5. Deploy the DEMO token contract (idempotent) and mint 10,000 DEMO to the wallet.
 *    Failure here is non-fatal -- the wallet was already created successfully.
 *
 * When autoSubmit=false, steps 4 and 5 are skipped because the contract is not yet on-chain.
 * The credential is stored locally as "pending" and can be deployed later via
 * [retryPendingDeploy].
 *
 * The passkey is created using the platform's WebAuthn provider:
 * - Android: Credential Manager
 * - iOS/macOS: AuthenticationServices
 * - Web: navigator.credentials
 *
 * @param username Display name shown in the biometric/passkey prompt.
 * @param autoSubmit If true, deploy the contract immediately after passkey registration.
 * @param onProgress Callback for progress messages shown in the UI.
 * @return [WalletCreationResult] with the credential, contract address, and balances.
 * @throws Exception if wallet creation fails (passkey cancelled, network error, etc.).
 *   Check [isUserCancellation] to distinguish user cancellations from hard errors.
 */
suspend fun createWallet(
    username: String,
    autoSubmit: Boolean = true,
    onProgress: (String) -> Unit
): WalletCreationResult {
    val kit = DemoState.kit
        ?: throw IllegalStateException("Smart Account Kit not initialized")

    onProgress("Creating wallet...")

    // Step 1: The deployer account signs deployment transactions on testnet. After a testnet
    // reset the account no longer exists, so we check and fund it via Friendbot if needed.
    // OZSmartAccountConfig.createDefaultDeployer() returns the well-known testnet deployer keypair.
    val deployer = OZSmartAccountConfig.createDefaultDeployer()
    try {
        SorobanServer(DemoConfig.RPC_URL).use { server ->
            server.getAccount(deployer.getAccountId())
        }
    } catch (e: Exception) {
        // Deployer account does not exist -- fund it via Friendbot and wait for network confirmation.
        ActivityLogState.info("Funding deployer account...")
        FriendBot.fundTestnetAccount(deployer.getAccountId())
        kotlinx.coroutines.delay(5000)
    }

    ActivityLogState.info("Creating wallet with username: $username")

    // Step 2: createWallet triggers the WebAuthn ceremony on the device (passkey registration),
    // then deploys the smart account contract using the deployer and native token contract.
    // - autoSubmit: controls whether the SDK submits the transaction automatically.
    // - autoFund: if true, the relayer funds the new wallet with XLM after deployment.
    // - nativeTokenContract: the XLM SAC address, used to top up the wallet via the relayer.
    val result = kit.walletOperations.createWallet(
        userName = username,
        autoSubmit = autoSubmit,
        autoFund = autoSubmit,
        nativeTokenContract = DemoConfig.NATIVE_TOKEN_CONTRACT
    )

    if (autoSubmit) {
        ActivityLogState.success("Wallet created successfully")
    } else {
        ActivityLogState.success("Passkey registered (deployment pending)")
    }
    ActivityLogState.info("Credential ID: ${result.credentialId}")
    ActivityLogState.info("Contract ID: ${result.contractId}")

    if (result.transactionHash != null) {
        ActivityLogState.info("Transaction Hash: ${result.transactionHash}")
    }

    // Step 3: Update DemoState so all screens know the wallet is connected.
    DemoState.setConnected(true, result.contractId, result.credentialId)

    // Track whether the contract was actually deployed on-chain.
    if (autoSubmit && result.transactionHash != null) {
        DemoState.setDeployed(true)
    } else {
        DemoState.setDeployed(false)
    }

    // Steps 4-5 only apply when the contract is deployed on-chain.
    var xlmBalance = "0.0"
    var demoTokenBalance: String? = null

    if (autoSubmit) {
        // Step 4: Fetch the initial XLM balance via the Stellar Asset Contract (SAC).
        // The relayer funded the wallet with XLM; fetching it confirms the funding succeeded.
        try {
            xlmBalance = fetchXlmBalance(result.contractId)
            DemoState.updateBalance(xlmBalance)
            ActivityLogState.info("Balance: $xlmBalance XLM")
        } catch (e: Exception) {
            ActivityLogState.error("Failed to fetch balance: ${e.message}")
        }

        // Step 5: Deploy the DEMO token (idempotent) and mint 10,000 DEMO to the new wallet.
        // The DEMO token is a demo-only Soroban token used to showcase token transfers.
        // This step is non-fatal: if it fails, the wallet was already created and the user
        // can still use XLM transfers. The error is logged but not re-thrown.
        try {
            onProgress("Deploying demo token...")
            ActivityLogState.info("Deploying demo token...")

            val tokenService = DemoTokenService(
                DemoConfig.RPC_URL,
                DemoConfig.NETWORK_PASSPHRASE
            )
            // ensureTokenAndMint deploys the contract if not already deployed, then mints to the wallet.
            val tokenResult = tokenService.ensureTokenAndMint(result.contractId)

            // Store the DEMO token contract address for use by TransferScreen.
            DemoState.updateDemoToken(tokenResult.tokenContractId)

            val mintedFormatted = formatStroopsAsXlm(tokenResult.amountMinted)
            demoTokenBalance = mintedFormatted
            DemoState.updateDemoTokenBalance(mintedFormatted)
            ActivityLogState.success("Minted 10,000 DEMO to wallet")
        } catch (e: Exception) {
            // DEMO token failure is logged but does not fail wallet creation.
            ActivityLogState.error("Demo token minting failed: ${e.message}")
        }
    }

    return WalletCreationResult(
        credentialId = result.credentialId,
        contractId = result.contractId,
        transactionHash = result.transactionHash,
        nickname = result.nickname,
        xlmBalance = xlmBalance,
        demoTokenBalance = demoTokenBalance
    )
}
