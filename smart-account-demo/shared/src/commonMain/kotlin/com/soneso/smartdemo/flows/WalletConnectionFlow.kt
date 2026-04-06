package com.soneso.smartdemo.flows

/**
 * Business logic for connecting to an existing smart account wallet.
 *
 * Demonstrates three connection strategies provided by the Smart Account SDK:
 *
 * 1. Auto Connect — [OZWalletOperations.connectWallet] with prompt=true.
 *    Restores the last connected session if available. If no session exists,
 *    triggers passkey authentication and resolves the contract address via indexer.
 *
 * 2. Connect via Indexer — Two-step: [authenticatePasskey] then [connectWallet] with credentialId.
 *    Authenticates with a passkey, then uses the indexer service to look up the
 *    smart account contract associated with that credential.
 *
 * 3. Retry Pending Deploy — [connectWallet] with both credentialId and contractId.
 *    Used when a previous wallet creation registered the passkey but the on-chain
 *    deployment did not complete. The credential is stored as "pending" until the
 *    contract is successfully deployed.
 */

import com.soneso.smartdemo.config.DemoConfig
import com.soneso.smartdemo.state.ActivityLogState
import com.soneso.smartdemo.state.DemoState
import com.soneso.smartdemo.util.isUserCancellation
import com.soneso.smartdemo.util.refreshAllBalances
import com.soneso.stellar.sdk.smartaccount.oz.DeployPendingResult
import com.soneso.stellar.sdk.smartaccount.oz.OZWalletOperations
import com.soneso.stellar.sdk.smartaccount.oz.StoredCredential

/**
 * Result of a successful wallet connection.
 *
 * @property contractId The C-address of the connected smart account contract.
 * @property credentialId The Base64URL-encoded passkey credential ID.
 * @property restoredFromSession True if the connection was restored from a saved session
 *   without requiring a new WebAuthn authentication ceremony.
 */
data class WalletConnectionResult(
    val contractId: String,
    val credentialId: String,
    val restoredFromSession: Boolean
)

/**
 * Connects to an existing wallet using Auto Connect.
 *
 * SDK workflow:
 * 1. Call [OZSmartAccountKit.walletOperations.connectWallet] with prompt=true.
 *    The SDK first tries to restore from a saved session. If no session exists,
 *    it triggers a WebAuthn authentication ceremony to identify the wallet.
 * 2. On success, update [DemoState] and refresh XLM + DEMO balances.
 *
 * @return [WalletConnectionResult] on success, or null if no wallet was found.
 * @throws Exception if the WebAuthn ceremony fails or the network is unreachable.
 *   Check [isUserCancellation] to distinguish user cancellations from hard errors.
 */
suspend fun quickConnect(): WalletConnectionResult? {
    val kit = DemoState.kit
        ?: throw IllegalStateException("SDK not initialized")

    // connectWallet with prompt=true: restore from session if available,
    // otherwise trigger a WebAuthn authentication to identify the wallet.
    val result = kit.walletOperations.connectWallet(
        OZWalletOperations.ConnectWalletOptions(prompt = true)
    )

    // With prompt=true the SDK always prompts if needed, so null is unexpected.
    // We return null defensively in case the platform returns no credential.
    if (result == null) {
        ActivityLogState.info("No wallet session found")
        return null
    }

    if (result.restoredFromSession) {
        ActivityLogState.success("Restored from saved session")
    } else {
        ActivityLogState.success("Connected via passkey authentication")
    }

    // Update DemoState with the connected wallet's contract and credential.
    DemoState.setConnected(true, result.contractId, result.credentialId)

    // Fetch both XLM and DEMO balances after connection so the main screen shows
    // up-to-date values without requiring a manual refresh.
    refreshAllBalances(result.contractId)

    return WalletConnectionResult(
        contractId = result.contractId,
        credentialId = result.credentialId,
        restoredFromSession = result.restoredFromSession
    )
}

/**
 * Connects to a wallet using the two-step Connect via Indexer flow.
 *
 * SDK workflow:
 * 1. Call [OZSmartAccountKit.walletOperations.authenticatePasskey] to perform a
 *    WebAuthn authentication ceremony and obtain the credential ID.
 * 2. Call [OZSmartAccountKit.walletOperations.connectWallet] with the credential ID.
 *    The SDK uses the indexer to look up the smart account contract associated with
 *    that credential and loads the wallet.
 * 3. On success, update [DemoState] and refresh balances.
 *
 * This flow is used when the user wants to explicitly select their passkey credential
 * before the SDK resolves the associated contract address.
 *
 * @return [WalletConnectionResult] on success, or null if the contract could not be resolved.
 * @throws Exception if the WebAuthn ceremony fails or the indexer lookup fails.
 *   Check [isUserCancellation] to distinguish user cancellations from hard errors.
 */
suspend fun manualConnect(): WalletConnectionResult? {
    val kit = DemoState.kit
        ?: throw IllegalStateException("SDK not initialized")

    // Step 1: Authenticate with the platform's WebAuthn provider to get the credential ID.
    // This triggers the biometric / passkey UI on the device.
    val authResult = kit.walletOperations.authenticatePasskey()
    ActivityLogState.success("Authenticated with credential: ${authResult.credentialId.take(16)}...")

    // Step 2: Connect using the credential ID. The SDK queries the indexer to map the
    // credential to its deployed smart account contract address.
    // Throws WalletException.NotFound if no contract is indexed for this credential.
    ActivityLogState.info("Looking up contract for credential...")
    val result = kit.walletOperations.connectWallet(
        OZWalletOperations.ConnectWalletOptions(credentialId = authResult.credentialId)
    )

    if (result == null) {
        ActivityLogState.error("Failed to resolve contract for credential")
        return null
    }

    ActivityLogState.success("Connected to contract: ${result.contractId}")
    DemoState.setConnected(true, result.contractId, result.credentialId)
    refreshAllBalances(result.contractId)

    return WalletConnectionResult(
        contractId = result.contractId,
        credentialId = result.credentialId,
        restoredFromSession = result.restoredFromSession
    )
}

/**
 * Connects to a smart account using a known contract address and any registered passkey.
 *
 * This is the recovery flow: the user knows the contract address (saved during wallet
 * creation) and authenticates with a passkey that is registered as a signer on the
 * contract (e.g., a recovery passkey added via a Default context rule).
 *
 * The indexer is not needed — the contract address is provided directly.
 *
 * @param contractAddress The C-address of the smart account contract.
 * @return [WalletConnectionResult] on success, or null if connection failed.
 */
suspend fun connectWithAddress(contractAddress: String): WalletConnectionResult? {
    val kit = DemoState.kit
        ?: throw IllegalStateException("SDK not initialized")

    // Step 1: Authenticate with any passkey for this RP.
    val authResult = kit.walletOperations.authenticatePasskey()
    ActivityLogState.success("Authenticated with credential: ${authResult.credentialId.take(16)}...")

    // Step 2: Connect using both the credential ID and the provided contract address.
    ActivityLogState.info("Connecting to contract: ${contractAddress.take(8)}...")
    val result = kit.walletOperations.connectWallet(
        OZWalletOperations.ConnectWalletOptions(
            credentialId = authResult.credentialId,
            contractId = contractAddress
        )
    )

    if (result == null) {
        ActivityLogState.error("Failed to connect to contract: $contractAddress")
        return null
    }

    ActivityLogState.success("Connected to contract: ${result.contractId}")
    DemoState.setConnected(true, result.contractId, result.credentialId)
    refreshAllBalances(result.contractId)

    return WalletConnectionResult(
        contractId = result.contractId,
        credentialId = result.credentialId,
        restoredFromSession = result.restoredFromSession
    )
}

/**
 * Retries a pending wallet deployment for a stored credential.
 *
 * A "pending" credential exists when a previous wallet creation registered the passkey
 * on the device but the Stellar deployment transaction did not complete (e.g., network
 * timeout, app crash, or insufficient funds). The SDK stores such credentials locally
 * so the user can retry the deployment later.
 *
 * SDK workflow:
 * 1. Call [OZWalletOperations.deployPendingCredential] with the credential ID.
 *    The SDK looks up the stored credential (including its contract ID and public key)
 *    and submits the deploy transaction.
 * 2. On success, update [DemoState] and refresh balances.
 *
 * After this call succeeds, the caller should call [loadPendingCredentials] again to
 * refresh the pending list -- the flow does not auto-refresh it.
 *
 * @param credentialId The Base64URL-encoded credential ID of the pending deployment.
 * @return [WalletConnectionResult] on success.
 * @throws Exception if the credential is not found, missing required fields, or the
 *   deploy transaction fails.
 */
suspend fun retryPendingDeploy(
    credentialId: String,
): WalletConnectionResult {
    val kit = DemoState.kit
        ?: throw IllegalStateException("SDK not initialized")

    ActivityLogState.info("Retrying deployment for ${credentialId.take(16)}...")

    // deployPendingCredential looks up the credential (with its contractId and publicKey)
    // from storage and submits the deploy transaction. autoFund ensures the wallet gets
    // funded with XLM after deployment, matching the createWallet flow.
    val result: DeployPendingResult = kit.walletOperations.deployPendingCredential(
        credentialId = credentialId,
        autoFund = true,
        nativeTokenContract = DemoConfig.NATIVE_TOKEN_CONTRACT
    )

    ActivityLogState.success("Successfully deployed contract: ${result.contractId}")
    DemoState.setConnected(true, result.contractId, credentialId)
    refreshAllBalances(result.contractId)

    return WalletConnectionResult(
        contractId = result.contractId,
        credentialId = credentialId,
        restoredFromSession = false
    )
}

/**
 * Loads the list of pending (not yet deployed) credentials from local storage.
 *
 * SDK workflow:
 * - Calls [OZSmartAccountKit.credentialManager.getPendingCredentials] which reads from
 *   the configured [StorageAdapter] (Android Keystore, iOS Keychain, or in-memory).
 *
 * A credential is "pending" if the passkey was registered but the Stellar deployment
 * transaction was never successfully submitted or confirmed.
 *
 * @return List of [StoredCredential] objects, empty if no pending credentials exist.
 * @throws Exception if the kit is not initialized or storage read fails.
 */
suspend fun loadPendingCredentials(): List<StoredCredential> {
    val kit = DemoState.kit
        ?: throw IllegalStateException("SDK not initialized")

    // getPendingCredentials reads credentials that were registered but whose
    // contracts have not yet been confirmed on-chain.
    return kit.credentialManager.getPendingCredentials()
}

/**
 * Deletes a pending credential from local storage.
 *
 * SDK workflow:
 * - Calls [OZSmartAccountKit.credentialManager.deleteCredential] which removes
 *   the credential from the configured [StorageAdapter].
 *
 * Use this to clean up credentials for deployments that the user no longer wants to retry.
 *
 * @param credentialId The Base64URL-encoded credential ID to delete.
 * @return True on success, false if the deletion failed.
 */
suspend fun deletePendingCredential(credentialId: String): Boolean {
    val kit = DemoState.kit
        ?: throw IllegalStateException("SDK not initialized")

    return try {
        // deleteCredential removes the credential from the platform's storage adapter.
        kit.credentialManager.deleteCredential(credentialId)
        ActivityLogState.info("Deleted pending credential")
        true
    } catch (e: Exception) {
        ActivityLogState.error("Delete failed: ${e.message}")
        false
    }
}
