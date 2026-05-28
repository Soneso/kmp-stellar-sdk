package com.soneso.smartdemo.flows

/**
 * Business logic for the main screen.
 *
 * Demonstrates how to:
 * - Initialize OZSmartAccountKit with platform-specific WebAuthn and storage providers
 * - Derive the deterministic DEMO token contract address without a network call
 * - Refresh XLM and DEMO balances for the connected wallet
 * - Disconnect the active wallet session
 *
 * Uses [OZSmartAccountKit] as the entry point to all Smart Account SDK operations.
 */

import com.soneso.smartdemo.config.DemoConfig
import com.soneso.smartdemo.state.ActivityLogState
import com.soneso.smartdemo.state.DemoState
import com.soneso.smartdemo.token.DemoTokenService
import com.soneso.smartdemo.util.ExternalSignerManagerAdapter
import com.soneso.smartdemo.util.refreshAllBalances
import com.soneso.smartdemo.wallet.DemoEd25519Adapter
import com.soneso.stellar.sdk.smartaccount.oz.InMemoryStorageAdapter
import com.soneso.stellar.sdk.smartaccount.oz.OZExternalSignerManager
import com.soneso.stellar.sdk.smartaccount.oz.OZSmartAccountConfig
import com.soneso.stellar.sdk.smartaccount.oz.OZSmartAccountKit
import com.soneso.stellar.sdk.smartaccount.oz.StorageAdapter
import com.soneso.stellar.sdk.smartaccount.oz.WebAuthnProvider

/**
 * Initializes the OZSmartAccountKit and stores it in DemoState.
 *
 * SDK workflow:
 * 1. Build an [OZSmartAccountConfig] with RPC URL, network passphrase, WASM hash,
 *    WebAuthn verifier address, relayer URL, indexer URL, and platform-specific providers.
 * 2. Call [OZSmartAccountKit.create] to instantiate the kit.
 * 3. Store the kit in [DemoState] for use by all other flows.
 * 4. Derive the deterministic DEMO token contract address and store it in [DemoState]
 *    so [TransferScreen] can reference it immediately without a full deploy.
 *
 * The relayer enables fee-sponsored transactions so wallets do not need XLM for fees.
 * The indexer enables credential-to-contract lookup during wallet connection.
 *
 * @param webauthnProvider Platform WebAuthn implementation (Android, iOS/macOS, or Web).
 *   Null on platforms that have not yet provided their provider.
 * @param storage Platform storage adapter for persisting session state.
 *   Falls back to an in-memory adapter if null.
 */
suspend fun initializeKit(
    webauthnProvider: WebAuthnProvider?,
    storage: StorageAdapter?
) {
    // Create the external signer adapter for keypair-based delegated signers.
    // This bridges the ExternalWalletAdapter interface to in-memory Ed25519 keypairs,
    // enabling multi-signer transfers where a delegated Stellar account co-signs.
    val externalSignerManagerAdapter = ExternalSignerManagerAdapter()
    externalSignerManagerAdapter.walletConnector = DemoState.walletConnector

    // Create the Ed25519 external signer manager used by multi-signer operations that
    // include SelectedSigner.Ed25519 signers. The manager is shared across flows:
    // - Transfer flow: uses the in-process keypair path (no adapter assigned on the manager).
    // - Approve flow: sets a DemoEd25519Adapter on the manager before submitting and
    //   clears it via clearDelegatedKeypairs() afterwards.
    val ed25519SignerManager = OZExternalSignerManager(
        networkPassphrase = DemoConfig.NETWORK_PASSPHRASE,
        walletAdapter = null,
        walletConnectionStorage = null
    )

    // The DemoEd25519Adapter is used exclusively by the Approve flow (adapter callback path).
    // It is pre-created here so it can be set on the manager before each submission and
    // removed afterwards, without allocating a new instance per operation.
    val demoEd25519Adapter = DemoEd25519Adapter()

    // Build the SDK configuration from DemoConfig constants.
    // takeIf { isNotBlank() } ensures blank strings are treated as absent (no relayer/indexer).
    // externalWallet bridges ExternalSignerManagerAdapter to the ExternalWalletAdapter interface
    // required by multiSignerTransfer() for delegated signer support.
    // externalSignerManager wires in the Ed25519 manager so SelectedSigner.Ed25519 signers
    // can be resolved during multi-signer operations.
    val config = OZSmartAccountConfig(
        rpcUrl = DemoConfig.RPC_URL,
        networkPassphrase = DemoConfig.NETWORK_PASSPHRASE,
        accountWasmHash = DemoConfig.ACCOUNT_WASM_HASH,
        webauthnVerifierAddress = DemoConfig.WEBAUTHN_VERIFIER_ADDRESS,
        relayerUrl = DemoConfig.DEFAULT_RELAYER_URL.takeIf { it.isNotBlank() },
        indexerUrl = DemoConfig.DEFAULT_INDEXER_URL.takeIf { it.isNotBlank() },
        webauthnProvider = webauthnProvider,
        storage = storage ?: InMemoryStorageAdapter(),
        externalWallet = externalSignerManagerAdapter,
        externalSignerManager = ed25519SignerManager,
        maxContextRuleScanId = DemoConfig.MAX_CONTEXT_RULE_SCAN_ID.toUInt()
    )

    // Create the kit instance. This is the main entry point to all SDK operations.
    val kit = OZSmartAccountKit.create(config)
    DemoState.setKitInstance(kit)
    DemoState.setExternalSignerManager(externalSignerManagerAdapter)
    DemoState.setEd25519SignerManager(ed25519SignerManager)
    DemoState.setDemoEd25519Adapter(demoEd25519Adapter)

    ActivityLogState.success("SDK initialized")

    // Log optional feature availability so developers can see what is enabled.
    if (DemoConfig.DEFAULT_RELAYER_URL.isNotBlank()) {
        ActivityLogState.info("Relayer fee sponsoring enabled")
    }
    if (DemoConfig.DEFAULT_INDEXER_URL.isNotBlank()) {
        ActivityLogState.info("Indexer lookup enabled")
    }

    // Derive the DEMO token contract address using the same deterministic salt and admin keypair
    // that DemoTokenService uses for deployment. This is a pure computation — no network call.
    // Storing it here means TransferScreen can show the DEMO option immediately on first launch.
    val tokenAddress = DemoTokenService.deriveTokenContractAddress(DemoConfig.NETWORK_PASSPHRASE)
    DemoState.updateDemoToken(tokenAddress)
}

/**
 * Refreshes XLM and DEMO token balances for the connected wallet.
 *
 * SDK workflow:
 * 1. Read the connected wallet's contract address from [DemoState].
 * 2. Call [refreshAllBalances] which fetches XLM via the Stellar Asset Contract (SAC)
 *    and DEMO via [ContractClient.invoke], then updates [DemoState].
 *
 * Logs the refreshed balance values on success.
 *
 * @throws IllegalStateException if no wallet is connected (no contract ID available).
 */
suspend fun refreshBalances() {
    val contractId = DemoState.contractId
        ?: throw IllegalStateException("No contract ID available")

    // refreshAllBalances fetches XLM via SAC and DEMO via ContractClient concurrently,
    // then updates DemoState.balance and DemoState.demoTokenBalance.
    refreshAllBalances(contractId)

    ActivityLogState.success(
        "Balances refreshed: ${DemoState.balance ?: "0.0"} XLM, ${DemoState.demoTokenBalance ?: "0.0"} DEMO"
    )
}

/**
 * Disconnects the active wallet session.
 *
 * SDK workflow:
 * 1. Call [OZSmartAccountKit.disconnect] to clear the session state stored by the kit.
 * 2. Update [DemoState] to reflect the disconnected state.
 *
 * After disconnect, [DemoState.isConnected] is false and screens will show the
 * "Connect Wallet" prompt.
 *
 * @throws Exception if the kit is not initialized.
 */
suspend fun disconnect() {
    // disconnect() clears the session stored by the kit's StorageAdapter,
    // so the next launch will not automatically restore the previous wallet.
    DemoState.kit?.disconnect()
    DemoState.setConnected(false)
    ActivityLogState.info("Wallet disconnected")
}
