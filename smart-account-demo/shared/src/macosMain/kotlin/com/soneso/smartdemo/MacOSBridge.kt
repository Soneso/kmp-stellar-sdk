//
//  MacOSBridge.kt
//  Smart Account Demo
//
//  Copyright (c) 2026 Soneso. All rights reserved.
//

package com.soneso.smartdemo

import com.soneso.smartdemo.config.DemoConfig
import com.soneso.smartdemo.config.KNOWN_POLICIES
import com.soneso.smartdemo.flows.ApproveResult
import com.soneso.smartdemo.flows.ContextRuleResult
import com.soneso.smartdemo.flows.FlowPolicyEntry
import com.soneso.smartdemo.flows.SignerEntry
import com.soneso.smartdemo.flows.TransferResult
import com.soneso.smartdemo.flows.WalletCreationResult
import com.soneso.stellar.sdk.smartaccount.oz.ConnectWalletResult
import com.soneso.smartdemo.flows.addContextRule
import com.soneso.smartdemo.flows.buildDelegatedSigner
import com.soneso.smartdemo.flows.buildEd25519Signer
import com.soneso.smartdemo.flows.buildSelectedSigners
import com.soneso.smartdemo.flows.connectWithAddress
import com.soneso.smartdemo.flows.deletePendingCredential
import com.soneso.smartdemo.flows.disconnect
import com.soneso.smartdemo.flows.extractPasskeySigners
import com.soneso.smartdemo.flows.finalizeConnect
import com.soneso.smartdemo.flows.initializeKit
import com.soneso.smartdemo.flows.loadAccountSigners
import com.soneso.smartdemo.flows.loadAvailablePasskeySigners
import com.soneso.smartdemo.flows.loadAvailableSigners
import com.soneso.smartdemo.flows.loadContextRules
import com.soneso.smartdemo.flows.loadParsedContextRule
import com.soneso.smartdemo.flows.manualConnect
import com.soneso.smartdemo.flows.multiSignerApproveAllowanceWithEd25519
import com.soneso.smartdemo.flows.multiSignerTransferWithEd25519
import com.soneso.smartdemo.flows.quickConnect
import com.soneso.smartdemo.flows.refreshBalances
import com.soneso.smartdemo.flows.registerPasskeySigner
import com.soneso.smartdemo.flows.removeContextRule
import com.soneso.smartdemo.flows.resolveAbsoluteLedger
import com.soneso.smartdemo.flows.DeployAndProvisionResult
import com.soneso.smartdemo.flows.deployPendingAndProvision
import com.soneso.smartdemo.flows.transfer
import com.soneso.smartdemo.flows.ContextRuleEditDiff
import com.soneso.smartdemo.flows.EditSignerEntry
import com.soneso.smartdemo.flows.EditPolicyEntry
import com.soneso.smartdemo.flows.PolicyParams
import com.soneso.smartdemo.flows.Ed25519SignerIdentity
import com.soneso.smartdemo.flows.isSinglePasskeyTransfer
import com.soneso.smartdemo.flows.readPolicyParams
import com.soneso.stellar.sdk.smartaccount.core.SmartAccountConstants
import com.soneso.smartdemo.state.ActivityLogState
import com.soneso.smartdemo.state.DemoState
import com.soneso.smartdemo.util.buildSimpleThresholdScVal
import com.soneso.smartdemo.util.buildSpendingLimitScVal
import com.soneso.smartdemo.util.buildWeightedThresholdScVal
import com.soneso.smartdemo.util.describeSignerType
import com.soneso.smartdemo.util.formatContextType
import com.soneso.smartdemo.util.hexToByteArray
import com.soneso.smartdemo.util.isUserCancellation
import com.soneso.smartdemo.util.toHexString
import com.soneso.stellar.sdk.KeyPair
import com.soneso.stellar.sdk.smartaccount.AppleWebAuthnProvider
import com.soneso.stellar.sdk.smartaccount.UserDefaultsStorageAdapter
import com.soneso.stellar.sdk.smartaccount.core.DelegatedSigner
import com.soneso.stellar.sdk.smartaccount.core.ExternalSigner
import com.soneso.stellar.sdk.smartaccount.core.SmartAccountBuilders
import com.soneso.stellar.sdk.smartaccount.core.SmartAccountSigner
import com.soneso.stellar.sdk.Util
import com.soneso.stellar.sdk.smartaccount.oz.ContextRuleType
import com.soneso.stellar.sdk.smartaccount.oz.OZBuilders
import com.soneso.stellar.sdk.smartaccount.oz.ParsedContextRule
import com.soneso.stellar.sdk.smartaccount.oz.SelectedSigner
import com.soneso.stellar.sdk.smartaccount.oz.StoredCredential
import platform.AppKit.NSApplication
import platform.AppKit.NSWindow
import platform.AuthenticationServices.ASAuthorizationController
import platform.AuthenticationServices.ASAuthorizationControllerPresentationContextProvidingProtocol
import platform.AuthenticationServices.ASPresentationAnchor
import platform.darwin.NSObject
import kotlin.time.ExperimentalTime

/**
 * Bridge-friendly result for the connect-wallet flows.
 *
 * Kotlin sealed classes do not map cleanly to Swift, so the bridge exposes a
 * flat shape with two mutually-exclusive arms. **Exactly one** of [connected]
 * and [ambiguous] is non-null when the bridge returns a non-null result;
 * Swift callers switch on which is non-nil and use the corresponding fields.
 *
 * The bridge methods return `WalletConnectionBridgeResult?` overall; `null`
 * preserves the existing "no result -- show generic error" behavior.
 */
data class WalletConnectionBridgeResult(
    val connected: ConnectedResult? = null,
    val ambiguous: AmbiguousResult? = null
) {
    init {
        // Enforce the "exactly one of connected / ambiguous is non-null"
        // invariant at construction time. Without this, a future bridge
        // entry point could accidentally produce a result with both arms
        // null or both arms set, which Swift consumers would mishandle.
        require((connected == null) != (ambiguous == null)) {
            "WalletConnectionBridgeResult must have exactly one of connected / ambiguous set"
        }
    }

    /**
     * Single-contract connect result. Mirrors [ConnectWalletResult.Connected].
     */
    data class ConnectedResult(
        val contractId: String,
        val credentialId: String,
        val restoredFromSession: Boolean
    )

    /**
     * Multi-candidate result. Mirrors [ConnectWalletResult.Ambiguous].
     *
     * Swift should display [candidates] as a picker, then call
     * `finalizeConnect(credentialId, chosen)` on the bridge with the
     * `credentialId` from this result and the chosen contract address.
     * Reusing the credentialId avoids a second WebAuthn ceremony.
     */
    data class AmbiguousResult(
        val credentialId: String,
        val candidates: List<String>
    )
}

/**
 * Translates the SDK's [ConnectWalletResult] sealed type into the
 * bridge-friendly flat shape used by Swift consumers.
 */
private fun ConnectWalletResult.toBridgeResult(): WalletConnectionBridgeResult =
    when (this) {
        is ConnectWalletResult.Connected -> WalletConnectionBridgeResult(
            connected = WalletConnectionBridgeResult.ConnectedResult(
                contractId = contractId,
                credentialId = credentialId,
                restoredFromSession = restoredFromSession
            )
        )
        is ConnectWalletResult.Ambiguous -> WalletConnectionBridgeResult(
            ambiguous = WalletConnectionBridgeResult.AmbiguousResult(
                credentialId = credentialId,
                candidates = candidates
            )
        )
    }

/**
 * Bridge between native macOS SwiftUI and the Kotlin Smart Account Kit.
 *
 * The macOS app uses native SwiftUI (not Compose Multiplatform). This class exposes all
 * business logic flows as suspending Kotlin functions that Swift can call via
 * `async`/`await` after the Kotlin framework is linked.
 *
 * All public suspend methods delegate directly to the corresponding flow functions in
 * `com.soneso.smartdemo.flows.*`. The bridge adds only Swift-interop concerns:
 * - Converting sealed class variants (ContextRuleType, SmartAccountSigner, LogLevel) to strings.
 * - Converting UInt values to Int/Long to avoid Objective-C boxing issues.
 * - Reconstructing complex Kotlin types (SelectedSigner, FlowPolicyEntry) from simple bridge types.
 *
 * Error conventions:
 * - User operations that can partially succeed return result data classes
 *   ([TransferResult], [ApproveResult], [ContextRuleResult], [ContextRuleEditResultBridge])
 *   with a `success` flag and an optional error message.
 * - Submission paths throw to abort: an exception means nothing further was attempted and the
 *   Swift caller surfaces the message in its error UI.
 * - The connect family ([quickConnect], [manualConnect], [connectWithAddress]) and
 *   [fetchAllowance] return null on failure with the cause recorded in the activity log.
 *
 * DTO boundary: new bridge methods return `*Bridge` DTOs (see `BridgeTypes.kt`); existing
 * methods that return raw SDK/flow types ([loadPendingCredentials], [loadContextRules],
 * [registerPasskeySigner]) are converted when next substantially touched.
 *
 * Threading: all bridge methods must be called from the main thread. Kotlin/Native's
 * Objective-C-export restriction on suspend functions enforces this at runtime (no
 * `objcExportSuspendFunctionLaunchThreadRestriction` override is set), and bridge session
 * state relies on it.
 *
 * WebAuthn on macOS requires:
 * - macOS 13.0+ (Ventura) for passkey support.
 * - Associated Domains entitlement configured in the Xcode project.
 * - An `apple-app-site-association` file hosted on the RP ID domain.
 */
class MacOSBridge {

    // =========================================================================
    // MARK: - Initialization
    // =========================================================================

    /**
     * Initializes the Smart Account Kit with macOS-specific providers and runs the full
     * [initializeKit] flow.
     *
     * This method:
     * 1. Creates an [AppleWebAuthnProvider] using [DemoConfig.DEFAULT_RP_ID] and [DemoConfig.RP_NAME].
     * 2. Creates a [UserDefaultsStorageAdapter] isolated to the macOS app's suite name.
     * 3. Stores both providers in [DemoState] so they are available to other flows.
     * 4. Calls [initializeKit] to construct the [OZSmartAccountKit] instance, configure the
     *    relayer and indexer, and derive the deterministic DEMO token contract address.
     *
     * Call this from `onAppear` or the SwiftUI `App.init` before any user interaction.
     *
     * Example from Swift:
     * ```swift
     * Task { await bridge.initializeKit() }
     * ```
     *
     * @throws Exception if provider construction or kit initialization fails.
     */
    @Throws(Exception::class)
    suspend fun initializeKit() {
        try {
            val webauthnProvider = AppleWebAuthnProvider(
                rpId = DemoConfig.DEFAULT_RP_ID,
                rpName = DemoConfig.RP_NAME
            )
            // On macOS, ASAuthorizationController requires a presentation context
            // that provides the window anchor for the passkey sheet.
            webauthnProvider.presentationContextProvider = MacOSPresentationContextProvider()

            val storage = UserDefaultsStorageAdapter(
                suiteName = "com.soneso.stellar.smartdemo.macos"
            )

            DemoState.setWebAuthnProvider(webauthnProvider)
            DemoState.setStorage(storage)

            initializeKit(webauthnProvider, storage)
        } catch (e: Exception) {
            ActivityLogState.error("Failed to initialize: ${e.message}")
            throw e
        }
    }

    // =========================================================================
    // MARK: - Wallet Creation
    // =========================================================================

    /**
     * Creates a new smart account wallet with passkey authentication.
     *
     * Triggers a Touch ID / passkey registration sheet and deploys the smart account
     * contract to the Stellar testnet. On success the wallet is connected and balances
     * are refreshed in [DemoState].
     *
     * @param username Display name shown in the passkey registration prompt.
     * @param onProgress Callback for incremental progress messages (called on the coroutine dispatcher).
     * @return [WalletCreationResult] with credential ID, contract address, and initial balances.
     * @throws Exception if the passkey ceremony fails, the network is unreachable, or
     *   deployment fails. Use [isUserCancellation] to distinguish deliberate cancellations.
     */
    @Throws(Exception::class)
    suspend fun createWallet(
        username: String,
        autoSubmit: Boolean = true,
        onProgress: (String) -> Unit
    ): WalletCreationResult = com.soneso.smartdemo.flows.createWallet(username, autoSubmit, onProgress)

    // =========================================================================
    // MARK: - Wallet Connection
    // =========================================================================

    /**
     * Attempts to restore the last session or trigger a passkey authentication prompt.
     *
     * @return Bridge result. When non-null, exactly one of `connected` and
     *   `ambiguous` is set. `ambiguous` indicates the indexer reported the
     *   passkey on more than one contract; Swift should show a picker and
     *   then call [finalizeConnect] with the credential ID from the result
     *   and the chosen contract. Null means no wallet was found or restored.
     * @throws Exception if the WebAuthn ceremony or network call fails.
     */
    @Throws(Exception::class)
    suspend fun quickConnect(): WalletConnectionBridgeResult? =
        com.soneso.smartdemo.flows.quickConnect()?.toBridgeResult()

    /**
     * Two-step connect: authenticates a passkey first, then resolves the contract via the indexer.
     *
     * @return Bridge result. See [quickConnect] for the connected/ambiguous shape.
     * @throws Exception if the WebAuthn ceremony or indexer lookup fails.
     */
    @Throws(Exception::class)
    suspend fun manualConnect(): WalletConnectionBridgeResult? =
        com.soneso.smartdemo.flows.manualConnect()?.toBridgeResult()

    /**
     * Connects to a known contract address using any registered passkey.
     *
     * Supplying an explicit contractId bypasses the SDK's discovery cascade,
     * so the result is always the connected arm (or null on failure). This
     * is the manual address-entry path: Swift calls it when the user types a
     * contract address directly. (Ambiguous results are finalized via
     * [finalizeConnect] instead, which reuses the authenticated credential.)
     *
     * @param contractAddress C-address of the smart account contract.
     * @return Bridge result with the connected arm populated, or null if the
     *   connect failed.
     * @throws Exception if the WebAuthn ceremony or network call fails.
     */
    @Throws(Exception::class)
    suspend fun connectWithAddress(contractAddress: String): WalletConnectionBridgeResult? =
        com.soneso.smartdemo.flows.connectWithAddress(contractAddress)?.toBridgeResult()

    /**
     * Finalizes a connect after the user picks a contract from the
     * ambiguous-result picker. Reuses the credential ID from the
     * authentication that produced the Ambiguous result, skipping a second
     * WebAuthn prompt.
     *
     * @param credentialId Base64URL-encoded credential ID from the original
     *   authentication.
     * @param contractAddress The C-address chosen by the user.
     * @return Bridge result with the connected arm populated, or null if the
     *   connect failed.
     * @throws Exception if the network call fails.
     */
    @Throws(Exception::class)
    suspend fun finalizeConnect(
        credentialId: String,
        contractAddress: String
    ): WalletConnectionBridgeResult? =
        com.soneso.smartdemo.flows.finalizeConnect(credentialId, contractAddress)?.toBridgeResult()

    /**
     * Deploys a pending wallet and provisions it with XLM and DEMO tokens.
     *
     * Combines deployment with DEMO token minting and balance refresh by delegating
     * to the shared flow the Compose app also uses, so Swift gets identical
     * post-deploy provisioning.
     *
     * @param credentialId Base64URL-encoded credential ID of the pending deployment.
     * @param onProgress Callback for progress messages shown in the UI.
     * @return [DeployAndProvisionResult] with deployment status and balances.
     */
    @Throws(Exception::class)
    suspend fun deployPendingAndProvision(
        credentialId: String,
        onProgress: (String) -> Unit = {}
    ): DeployAndProvisionResult =
        com.soneso.smartdemo.flows.deployPendingAndProvision(credentialId, onProgress)

    /**
     * Returns the list of pending (not yet deployed) credentials from local storage.
     *
     * @return List of [StoredCredential] objects, empty if none exist.
     * @throws Exception if the kit is not initialized or the storage read fails.
     */
    @Throws(Exception::class)
    suspend fun loadPendingCredentials(): List<StoredCredential> =
        com.soneso.smartdemo.flows.loadPendingCredentials()

    /**
     * Deletes a pending credential from local storage.
     *
     * @param credentialId Base64URL-encoded credential ID to delete.
     * @return True if the deletion succeeded, false on error.
     */
    @Throws(Exception::class)
    suspend fun deletePendingCredential(credentialId: String): Boolean =
        com.soneso.smartdemo.flows.deletePendingCredential(credentialId)

    // =========================================================================
    // MARK: - Session Management
    // =========================================================================

    /**
     * Refreshes XLM and DEMO token balances for the connected wallet.
     *
     * @throws IllegalStateException if no wallet is connected.
     */
    @Throws(Exception::class)
    suspend fun refreshBalances() = com.soneso.smartdemo.flows.refreshBalances()

    /**
     * Disconnects the active wallet session.
     *
     * @throws Exception if the kit is not initialized.
     */
    @Throws(Exception::class)
    suspend fun disconnect() = com.soneso.smartdemo.flows.disconnect()

    // =========================================================================
    // MARK: - Transfers
    // =========================================================================

    /**
     * Transfers tokens from the connected wallet to a recipient using the primary passkey.
     *
     * The SDK triggers a Touch ID / passkey authentication sheet before submitting the
     * transaction. Balances are refreshed in [DemoState] on success.
     *
     * @param tokenContract C-address of the token contract. Use [getNativeTokenContract]
     *   for XLM or [getDemoTokenContractId] for DEMO.
     * @param recipient Recipient G-address or C-address.
     * @param amount Transfer amount as a decimal string (e.g. "10" or "10.5").
     * @return [TransferResult] with success flag, transaction hash, and optional error.
     */
    @Throws(Exception::class)
    suspend fun transfer(
        tokenContract: String,
        recipient: String,
        amount: String
    ): TransferResult = com.soneso.smartdemo.flows.transfer(
        tokenContract = tokenContract,
        recipient = recipient,
        amount = amount,
        decimals = knownTokenDecimals(tokenContract)
    )

    /**
     * Returns the known decimal scale for the demo's two tokens (XLM SAC and the DEMO
     * token, both 7), or null for any other contract so the SDK fetches the scale
     * on-chain. Passing the known scale skips one read-only simulation per transfer.
     */
    private fun knownTokenDecimals(tokenContract: String): Int? = when (tokenContract) {
        DemoConfig.NATIVE_TOKEN_CONTRACT -> 7 // XLM SAC scale
        DemoState.demoTokenContractId -> DemoConfig.DEMO_TOKEN_DECIMALS
        else -> null
    }

    /**
     * Transfers tokens using an explicit list of signers.
     *
     * Each [SignerDescriptor] in [signerDescriptors] is mapped to a [SelectedSigner]:
     * - "passkey": resolved from context rules by credential ID to obtain full key data.
     * - "delegated": constructed as [SelectedSigner.Wallet] by G-address.
     * - "ed25519": constructed from the hex-encoded public key in [desc.value]. The
     *   corresponding raw seed must be provided in [ed25519SecretKeys] keyed as
     *   `"<verifierAddress>:<publicKeyHex>"`. Secrets are registered in-process via
     *   [OZExternalSignerManager.addEd25519FromRawKey] and cleared after submission.
     *
     * Delegated signer keypairs must be provided in [delegatedSecretKeys] (G-address → secret key)
     * so the bridge can register them as in-memory keypairs on the kit-owned external-signer
     * manager before the call. Delegated and Ed25519 signing material is cleared after submission.
     *
     * @param tokenContract C-address of the token contract.
     * @param recipient Recipient G-address or C-address.
     * @param amount Transfer amount as a decimal string.
     * @param signerDescriptors Ordered list of signers that must participate.
     * @param delegatedSecretKeys Map of G-address to Stellar secret key (S...) for delegated signers.
     * @param ed25519SecretKeys Map of `"<verifierAddress>:<publicKeyHex>"` to hex-encoded 32-byte
     *   seed for Ed25519 signers. Registered in-process; cleared on completion.
     * @return [TransferResult] with success flag, transaction hash, and optional error.
     * @throws Exception if the kit is not initialized, any secret key is invalid, or signing fails.
     */
    @Throws(Exception::class)
    suspend fun multiSignerTransfer(
        tokenContract: String,
        recipient: String,
        amount: String,
        signerDescriptors: List<SignerDescriptor>,
        delegatedSecretKeys: Map<String, String>,
        ed25519SecretKeys: Map<String, String> = emptyMap()
    ): TransferResult {
        val selectedSigners = resolveMultiSignerSelection(signerDescriptors)
        return multiSignerTransferWithEd25519(
            tokenContract = tokenContract,
            recipient = recipient,
            amount = amount,
            decimals = knownTokenDecimals(tokenContract),
            selectedSigners = selectedSigners,
            delegatedKeyPairs = toDelegatedKeyPairs(delegatedSecretKeys),
            ed25519Secrets = toEd25519Secrets(ed25519SecretKeys)
        )
    }

    // =========================================================================
    // MARK: - Approve (Token Allowance)
    // =========================================================================

    /**
     * Approves a token allowance by calling the token contract's approve() directly.
     * Single-signer path using the connected passkey.
     *
     * @param tokenContract C-address of the token contract.
     * @param spenderAddress G-address or C-address being granted the allowance.
     * @param amount Decimal amount string (e.g., "100", "10.5").
     * @param expirationLedgerOffset Ledger offset from now as Int (converted to UInt internally).
     * @return [ApproveResult] with success flag, transaction hash, and optional error.
     */
    @Throws(Exception::class)
    suspend fun approveAllowance(
        tokenContract: String,
        spenderAddress: String,
        amount: String,
        expirationLedgerOffset: Int
    ): ApproveResult = com.soneso.smartdemo.flows.approveAllowance(
        tokenContract = tokenContract,
        spenderAddress = spenderAddress,
        amount = amount,
        expirationLedgerOffset = expirationLedgerOffset.toUInt()
    )

    /**
     * Approves a token allowance with multi-signer authorization.
     *
     * Ed25519 signers use the adapter custody path: secrets are registered on the
     * [com.soneso.smartdemo.wallet.DemoEd25519Adapter] injected into the kit via
     * OZSmartAccountConfig.externalEd25519Adapter, consulted by the kit-owned external-signer
     * manager ahead of its in-memory registry, and cleared after submission.
     *
     * @param tokenContract C-address of the token contract.
     * @param spenderAddress G-address or C-address being granted the allowance.
     * @param amount Decimal amount string.
     * @param expirationLedgerOffset Ledger offset from now as Int (converted to UInt internally).
     * @param signerDescriptors Ordered list of signers that must participate.
     * @param delegatedSecretKeys Map of G-address to Stellar secret key (S...) for delegated signers.
     * @param ed25519SecretKeys Map of `"<verifierAddress>:<publicKeyHex>"` to hex-encoded 32-byte
     *   seed for Ed25519 signers. Registered via adapter; cleared on completion.
     * @return [ApproveResult] with success flag, transaction hash, and optional error.
     */
    @Throws(Exception::class)
    suspend fun multiSignerApproveAllowance(
        tokenContract: String,
        spenderAddress: String,
        amount: String,
        expirationLedgerOffset: Int,
        signerDescriptors: List<SignerDescriptor>,
        delegatedSecretKeys: Map<String, String>,
        ed25519SecretKeys: Map<String, String> = emptyMap()
    ): ApproveResult {
        val selectedSigners = resolveMultiSignerSelection(signerDescriptors)
        return multiSignerApproveAllowanceWithEd25519(
            tokenContract = tokenContract,
            spenderAddress = spenderAddress,
            amount = amount,
            expirationLedgerOffset = expirationLedgerOffset.toUInt(),
            selectedSigners = selectedSigners,
            delegatedKeyPairs = toDelegatedKeyPairs(delegatedSecretKeys),
            ed25519Secrets = toEd25519Secrets(ed25519SecretKeys)
        )
    }

    /**
     * Fetches the current token allowance granted by the smart account to a spender.
     * Read-only call, no signing needed.
     *
     * @param tokenContract C-address of the token contract.
     * @param spenderAddress G-address or C-address of the spender.
     * @return Formatted allowance amount string (e.g., "100.0"), or null on error.
     */
    suspend fun fetchAllowance(
        tokenContract: String,
        spenderAddress: String
    ): String? = com.soneso.smartdemo.flows.fetchAllowance(tokenContract, spenderAddress)

    // =========================================================================
    // MARK: - Account Signers
    // =========================================================================

    /**
     * Loads all unique signers registered on the connected smart account.
     *
     * Each [SignerEntry] from the flow is converted to a [SignerEntryBridge]: the signer is
     * classified via [convertSignerInfoToBridge] and each rule membership carries a
     * pre-formatted context type description, so Swift renders without inspecting Kotlin
     * sealed class types.
     *
     * @return List of [SignerEntryBridge], each with a unique signer and its rule memberships.
     * @throws IllegalStateException if the kit is not initialized.
     */
    @Throws(Exception::class)
    suspend fun loadAccountSigners(): List<SignerEntryBridge> =
        com.soneso.smartdemo.flows.loadAccountSigners().map { entry ->
            SignerEntryBridge(
                signerInfo = convertSignerInfoToBridge(entry.signer, canSign = false),
                ruleMemberships = entry.contextRules.map { rule ->
                    RuleMembershipBridge(
                        ruleId = rule.id.toLong(),
                        ruleName = rule.name,
                        typeDescription = formatContextType(rule.contextType)
                    )
                }
            )
        }

    // =========================================================================
    // MARK: - Context Rules
    // =========================================================================

    /**
     * Loads all context rules from the connected smart account.
     *
     * @return Parsed list of [ParsedContextRule], sorted by rule ID.
     * @throws IllegalStateException if the kit is not initialized.
     */
    @Throws(Exception::class)
    suspend fun loadContextRules(): List<ParsedContextRule> =
        com.soneso.smartdemo.flows.loadContextRules()

    /**
     * Removes a context rule from the smart account.
     *
     * Requires passkey authentication or multi-signer authorization when signerDescriptors
     * is non-empty. The UI should prevent removing the last rule.
     *
     * @param ruleId Rule ID as an Int (UInt internally). Use [loadContextRules] to get valid IDs.
     * @param signerDescriptors Selected signers for multi-signer auth. Empty = single-signer mode.
     * @param delegatedSecretKeys Map of G-address to secret key for delegated signers. Empty if none.
     * @param ed25519SecretKeys Map of `"<verifierAddress>:<publicKeyHex>"` to hex-encoded 32-byte seed
     *   for in-process Ed25519 co-signers. Empty if none.
     * @return [ContextRuleResult] with success flag and transaction hash.
     */
    @Throws(Exception::class)
    suspend fun removeContextRule(
        ruleId: Int,
        signerDescriptors: List<SignerDescriptor> = emptyList(),
        delegatedSecretKeys: Map<String, String> = emptyMap(),
        ed25519SecretKeys: Map<String, String> = emptyMap()
    ): ContextRuleResult {
        val selectedSigners = resolveSelectedSigners(signerDescriptors, loadOnChainPasskeySigners())

        return com.soneso.smartdemo.flows.withInProcessMultiSigner(
            toDelegatedKeyPairs(delegatedSecretKeys),
            toEd25519Secrets(ed25519SecretKeys)
        ) {
            com.soneso.smartdemo.flows.removeContextRule(ruleId.toUInt(), selectedSigners)
        }
    }

    /**
     * Adds a new context rule to the smart account.
     *
     * The bridge constructs all Kotlin SDK types internally from the Swift-friendly descriptors:
     * - [contextTypeName] and [contextTypeParam] → [ContextRuleType] sealed class instance.
     * - [signerDescriptors] → [List<SmartAccountSigner>] via the appropriate builder function.
     * - [policyDescriptors] → [List<FlowPolicyEntry>] with encoded [SCValXdr] parameters.
     * - [validUntilOffset] is resolved to an absolute ledger via [resolveAbsoluteLedger].
     *
     * Passkey signer descriptors are resolved from the current on-chain context rules. Ed25519
     * signer descriptors are constructed from hex-encoded public keys.
     *
     * Requires passkey authentication.
     *
     * @param contextTypeName Context type tag: "default", "call_contract", or "create_contract".
     * @param contextTypeParam For "call_contract": target contract C-address.
     *   For "create_contract": lowercase hex WASM hash. Null for "default".
     * @param name Human-readable rule name stored on-chain.
     * @param validUntilOffset Ledger offset from now for rule expiry, or null for no expiry.
     * @param signerDescriptors Signers to register on this rule.
     * @param policyDescriptors Policies to enforce on this rule.
     * @param signerDescriptorsForAuth Selected signers for multi-signer auth. Empty = single-signer mode.
     * @param delegatedSecretKeysForAuth Map of G-address to secret key for delegated signers. Empty if none.
     * @param ed25519SecretKeys Map of `"<verifierAddress>:<publicKeyHex>"` to hex-encoded 32-byte seed
     *   for in-process Ed25519 co-signers. Empty if none.
     * @return [ContextRuleResult] with success flag and transaction hash.
     */
    @Throws(Exception::class)
    suspend fun addContextRule(
        contextTypeName: String,
        contextTypeParam: String?,
        name: String,
        validUntilOffset: Int?,
        signerDescriptors: List<SignerDescriptor>,
        policyDescriptors: List<PolicyDescriptor>,
        signerDescriptorsForAuth: List<SignerDescriptor> = emptyList(),
        delegatedSecretKeysForAuth: Map<String, String> = emptyMap(),
        ed25519SecretKeys: Map<String, String> = emptyMap()
    ): ContextRuleResult {
        // Build ContextRuleType.
        val contextType = buildContextRuleType(contextTypeName, contextTypeParam)

        // Resolve absolute ledger for optional expiry.
        val validUntil: UInt? = if (validUntilOffset != null) {
            resolveAbsoluteLedger(validUntilOffset.toUInt())
        } else {
            null
        }

        // Resolve signers. Passkey signers are looked up from on-chain context rules by credential
        // ID so the bridge can obtain their full key data (verifier address + keyData bytes).
        val allPasskeySigners = loadOnChainPasskeySigners()
        val signers = buildSignerList(signerDescriptors, allPasskeySigners)

        // Build FlowPolicyEntry list. For each PolicyDescriptor the bridge constructs the correct
        // SCVal encoding based on policyType. The SDK's addContextRule expects a Map<String,SCValXdr>
        // but the flow layer wraps that in FlowPolicyEntry for convenience.
        val policies = buildPolicyEntries(policyDescriptors, signers)

        // Resolve selected signers for multi-signer mode.
        val selectedSigners = resolveSelectedSigners(signerDescriptorsForAuth, allPasskeySigners)

        return com.soneso.smartdemo.flows.withInProcessMultiSigner(
            toDelegatedKeyPairs(delegatedSecretKeysForAuth),
            toEd25519Secrets(ed25519SecretKeys)
        ) {
            com.soneso.smartdemo.flows.addContextRule(
                contextType = contextType,
                name = name,
                validUntil = validUntil,
                signers = signers,
                policies = policies,
                selectedSigners = selectedSigners
            )
        }
    }

    /**
     * Loads a single context rule for pre-populating an edit form in Swift.
     *
     * Fetches the fully parsed [ParsedContextRule] via [loadParsedContextRule] (which uses
     * [OZContextRuleManager.listContextRules]) and converts it to a [ParsedRuleBridge].
     *
     * @param ruleId Rule ID as an Int (converted to UInt internally).
     * @return [ParsedRuleBridge] with rule fields in Swift-friendly format.
     * @throws Exception if the rule does not exist or the RPC call fails.
     */
    @Throws(Exception::class)
    suspend fun loadContextRuleForEdit(ruleId: Int): ParsedRuleBridge {
        val parsed = loadParsedContextRule(ruleId.toUInt())
        return convertRuleToBridge(parsed)
    }

    // =========================================================================
    // MARK: - Signer Registration
    // =========================================================================

    /**
     * Registers a new passkey via the WebAuthn ceremony and returns the constructed signer.
     *
     * Triggers a Touch ID / passkey registration sheet. The returned [ExternalSigner] can be
     * used immediately in [addContextRule] by extracting its credential ID for a
     * [SignerDescriptor] of type "passkey".
     *
     * @param name Display name shown in the passkey registration prompt.
     * @return The constructed [ExternalSigner] ready to be added to a context rule.
     * @throws IllegalStateException if the WebAuthn provider is not initialized.
     * @throws Exception if the registration ceremony fails or is cancelled.
     */
    @Throws(Exception::class)
    suspend fun registerPasskeySigner(name: String): ExternalSigner {
        val signer = com.soneso.smartdemo.flows.registerPasskeySigner(name)
        registeredPasskeySigners.add(signer)
        return signer
    }

    // =========================================================================
    // MARK: - Ledger Resolution
    // =========================================================================

    /**
     * Resolves a ledger offset to an absolute ledger number by querying the current ledger.
     *
     * @param offset Ledger offset from now (e.g. 720 for approximately 1 hour at 5 s/ledger).
     * @return Absolute ledger as an Int (converted from UInt internally).
     * @throws Exception if the RPC call fails.
     */
    @Throws(Exception::class)
    suspend fun resolveAbsoluteLedger(offset: Int): Int =
        com.soneso.smartdemo.flows.resolveAbsoluteLedger(offset.toUInt()).toInt()

    // =========================================================================
    // MARK: - Available Signers
    // =========================================================================

    /**
     * Loads the available signers for the connected wallet and converts them to bridge types.
     *
     * Used by the transfer screen signer picker to populate the list of signers Swift can display.
     *
     * @return List of [SignerInfoBridge] with type, display name, identifier, signing capability,
     *   and optional hex key data.
     */
    @Throws(Exception::class)
    suspend fun loadAvailableSigners(): List<SignerInfoBridge> {
        val signerInfos = com.soneso.smartdemo.flows.loadAvailableSigners()
        return signerInfos.map { info -> convertSignerInfoToBridge(info.signer, info.canSign) }
    }

    /**
     * Loads available passkey signers from on-chain context rules, optionally excluding the
     * currently connected wallet's own passkey.
     *
     * Used by the rule builder's passkey tab to show other registered passkeys for selection.
     *
     * @param excludeCredentialId Credential ID to exclude (the connected wallet owner), or null
     *   to include all passkeys.
     * @return List of [SignerInfoBridge] for each unique passkey signer.
     */
    @Throws(Exception::class)
    suspend fun loadAvailablePasskeySigners(excludeCredentialId: String?): List<SignerInfoBridge> {
        val passkeys = com.soneso.smartdemo.flows.loadAvailablePasskeySigners(excludeCredentialId)
        return passkeys.map { signer -> convertSignerInfoToBridge(signer, canSign = false) }
    }

    /**
     * Loads all unique signers from all on-chain context rules, excluding the connected
     * wallet's own passkey. Used by the "Reuse Signer" picker in edit mode.
     *
     * @return List of [SignerInfoBridge] for each unique signer across all rules.
     */
    @Throws(Exception::class)
    suspend fun loadAllOnChainSigners(): List<SignerInfoBridge> {
        val uniqueSigners = com.soneso.smartdemo.flows.loadAllOnChainSigners()
        return uniqueSigners.map { signer -> convertSignerInfoToBridge(signer, canSign = false) }
    }

    // =========================================================================
    // MARK: - State Getters
    // =========================================================================

    /** Returns whether a wallet is currently connected. */
    fun isConnected(): Boolean = DemoState.isConnected

    /** Returns whether the connected wallet's contract has been deployed on-chain. */
    fun isWalletDeployed(): Boolean = DemoState.isDeployed

    /** Returns the connected wallet's contract address, or null if disconnected. */
    fun getContractId(): String? = DemoState.contractId

    /** Returns the active passkey credential ID (Base64URL), or null if disconnected. */
    fun getCredentialId(): String? = DemoState.credentialId

    /** Returns the connected wallet's XLM balance display string, or null if unknown. */
    fun getBalance(): String? = DemoState.balance

    /** Returns the connected wallet's DEMO token balance display string, or null if unknown. */
    fun getDemoTokenBalance(): String? = DemoState.demoTokenBalance

    /** Returns the DEMO token contract ID, or null if not yet deployed. */
    fun getDemoTokenContractId(): String? = DemoState.demoTokenContractId

    /**
     * Returns the current activity log entries as [ActivityLogEntryBridge] instances.
     *
     * Entries are ordered from newest to oldest (the same order as [ActivityLogState.entries]).
     */
    @OptIn(ExperimentalTime::class)
    fun getActivityLogEntries(): List<ActivityLogEntryBridge> {
        return ActivityLogState.entries.map { entry ->
            ActivityLogEntryBridge(
                message = entry.message,
                level = entry.level.name,
                timestampMs = entry.timestamp.toEpochMilliseconds()
            )
        }
    }

    /** Clears all activity log entries. */
    fun clearActivityLog() = ActivityLogState.clear()

    // =========================================================================
    // MARK: - Config Getters
    // =========================================================================

    /**
     * Returns the list of known policy contracts for the current testnet configuration.
     *
     * Used by the rule builder to show pre-configured policy options.
     */
    fun getKnownPolicies(): List<PolicyInfoBridge> =
        KNOWN_POLICIES.map { p ->
            PolicyInfoBridge(
                type = p.type,
                name = p.name,
                description = p.description,
                address = p.address
            )
        }

    /** Returns the native XLM token SAC contract address. */
    fun getNativeTokenContract(): String = DemoConfig.NATIVE_TOKEN_CONTRACT

    /** Returns the Ed25519 verifier contract address. */
    fun getEd25519VerifierAddress(): String = DemoConfig.ED25519_VERIFIER_ADDRESS

    /** Returns the WebAuthn verifier contract address. */
    fun getWebauthnVerifierAddress(): String = DemoConfig.WEBAUTHN_VERIFIER_ADDRESS

    /** Returns the approximate number of ledgers per hour (at ~5 s/ledger). */
    fun getLedgersPerHour(): Int = Util.LEDGERS_PER_HOUR.toInt()

    /** Returns the approximate number of ledgers per day (at ~5 s/ledger). */
    fun getLedgersPerDay(): Int = Util.LEDGERS_PER_DAY.toInt()

    // =========================================================================
    // MARK: - Utility Methods
    // =========================================================================

    /**
     * Truncates an address string for display by keeping a prefix and suffix separated by "...".
     *
     * @param address Full address string.
     * @param prefixLen Number of leading characters to keep.
     * @param suffixLen Number of trailing characters to keep.
     * @return Truncated address like "GABC1234...WXYZ5678", or the full address if short enough.
     */
    fun truncateAddress(address: String, prefixLen: Int = 8, suffixLen: Int = 8): String {
        if (address.length <= prefixLen + suffixLen + 3) return address
        return "${address.take(prefixLen)}...${address.takeLast(suffixLen)}"
    }

    /**
     * Returns whether a passkey operation error message represents a user-initiated cancellation.
     *
     * @param message Error message from a caught exception.
     * @return True if the message contains "cancelled", or "user" combined with "cancel"/"abort".
     */
    fun isUserCancellation(message: String): Boolean = com.soneso.smartdemo.util.isUserCancellation(message)

    /**
     * Extracts the credential ID string from an [ExternalSigner].
     *
     * Used by the macOS app after passkey registration to get the correct
     * credential identifier for building [SignerDescriptor] objects.
     *
     * @return The Base64URL-encoded credential ID, or null if extraction fails.
     */
    fun getCredentialIdFromSigner(signer: ExternalSigner): String? {
        return SmartAccountBuilders.getCredentialIdStringFromSigner(signer)
    }

    /**
     * Validates that a secret seed derives the expected Stellar G-address.
     *
     * Never throws: any decoding or derivation failure returns false.
     *
     * @param secretSeed The secret seed (S...) to validate.
     * @param expectedAddress The expected G-address.
     * @return True if the seed derives the expected address.
     */
    suspend fun validateDelegatedKey(secretSeed: String, expectedAddress: String): Boolean {
        return try {
            val keypair = KeyPair.fromSecretSeed(secretSeed)
            keypair.getAccountId() == expectedAddress
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Validates that a hex-encoded 32-byte Ed25519 seed derives the expected public key.
     *
     * Decodes [secretHex] to 32 bytes, derives the corresponding [KeyPair], and compares
     * the derived 32-byte public key to [expectedPublicKeyHex].
     *
     * Never throws: any decoding or derivation failure returns false.
     *
     * @param secretHex Lowercase hex string encoding the 32-byte raw Ed25519 seed.
     * @param expectedPublicKeyHex Lowercase hex string encoding the expected 32-byte public key.
     * @return `true` when the derived public key matches [expectedPublicKeyHex] byte-for-byte.
     *   `false` on any decoding failure, wrong-length input, or key mismatch.
     */
    suspend fun validateEd25519Key(secretHex: String, expectedPublicKeyHex: String): Boolean {
        return try {
            val seedBytes = hexToByteArray(secretHex)
            if (seedBytes.size != SmartAccountConstants.ED25519_SECRET_KEY_SIZE) return false
            val keypair = KeyPair.fromSecretSeed(seedBytes)
            keypair.getPublicKey().toHexString() == expectedPublicKeyHex
        } catch (_: Exception) {
            false
        }
    }


    // =========================================================================
    // MARK: - Context Rule Edit
    // =========================================================================

    /**
     * Submits context rule edit operations using the edit flow orchestrator.
     *
     * Converts Swift-friendly bridge types to internal Kotlin types and delegates to
     * [com.soneso.smartdemo.flows.submitContextRuleEdits]. For multi-signer rules,
     * delegated signer keypairs must be pre-registered via [delegatedSecretKeys].
     *
     * @param editDiff The computed diff from Swift form state.
     * @param signerDescriptors Selected signers for multi-signer auth. Empty = single-signer mode.
     * @param delegatedSecretKeys Map of G-address to secret key for delegated signers in the picker.
     * @param ed25519SecretKeys Map of `"<verifierAddress>:<publicKeyHex>"` to hex-encoded 32-byte seed
     *   for in-process Ed25519 co-signers. Empty if none.
     * @param onProgress Callback for per-operation progress messages.
     * @return [ContextRuleEditResultBridge] with completion details or error.
     * @throws IllegalStateException if a new-signer descriptor or removed signer ID cannot be
     *   resolved against the on-chain context rules.
     */
    @Throws(Exception::class)
    suspend fun submitContextRuleEdits(
        editDiff: ContextRuleEditDiffBridge,
        signerDescriptors: List<SignerDescriptor>,
        delegatedSecretKeys: Map<String, String>,
        ed25519SecretKeys: Map<String, String> = emptyMap(),
        onProgress: (String) -> Unit
    ): ContextRuleEditResultBridge {
        // Load rules once; derive passkey signers from the same snapshot to avoid a second RPC call.
        // A fetch failure propagates: new and removed signers are resolved against this snapshot,
        // so continuing without it would silently drop requested operations.
        val rules = loadContextRules()
        val allPasskeySigners = extractPasskeySigners(rules)

        // Build new signer entries from descriptors.
        val newSignerEntries = editDiff.newSignerDescriptors.map { desc ->
            val signer = resolveSignerFromDescriptor(desc, allPasskeySigners)
                ?: throw IllegalStateException("Could not resolve signer: ${desc.type}/${desc.value.take(16)}")
            EditSignerEntry(
                signer = signer,
                onChainId = null,
                isOriginal = false,
                isPending = desc.isPending
            )
        }

        // Build removed signer entries (need on-chain IDs and signer objects for the flow).
        val removedSignerEntries = editDiff.removedSignerIds.map { signerId ->
            val rule = rules.flatMap { r ->
                r.signers.mapIndexedNotNull { i, s ->
                    if (r.signerIds.getOrNull(i)?.toInt() == signerId) Pair(s, r.signerIds[i])
                    else null
                }
            }.firstOrNull()
            if (rule == null) {
                // Fail the whole submission: silently dropping the removal would
                // leave the signer on the rule while the UI reports success.
                val message = "Could not find signer with on-chain ID $signerId for removal"
                ActivityLogState.error(message)
                throw IllegalStateException(message)
            }
            EditSignerEntry(
                signer = rule.first,
                onChainId = rule.second,
                isOriginal = true
            )
        }

        // Build policy entries (new, removed, modified). Weighted threshold weights may
        // reference new, removed, or existing signers, so SCVal encoding resolves against
        // the combined signer pool.
        val stagedSigners = newSignerEntries.map { it.signer } +
            removedSignerEntries.map { it.signer }
        val existingSigners = rules.flatMap { it.signers }
        val allSigners = (stagedSigners + existingSigners)
            .distinctBy { SmartAccountBuilders.getSignerKey(it) }

        val newPolicyEntries = editDiff.newPolicyDescriptors.map { desc ->
            toEditPolicyEntry(desc, allSigners, onChainId = null, modified = false)
        }

        val removedPolicyEntries = editDiff.removedPolicyIds.map { policyId ->
            EditPolicyEntry(
                info = null,
                label = "",
                address = "",
                scVal = null,
                onChainId = policyId.toUInt(),
                isOriginal = true
            )
        }

        val modifiedPolicyEntries = editDiff.modifiedPolicyDescriptors.mapIndexed { index, desc ->
            toEditPolicyEntry(
                desc,
                allSigners,
                onChainId = editDiff.modifiedPolicyIds.getOrNull(index)?.toUInt(),
                modified = true
            )
        }

        // Build the internal ContextRuleEditDiff.
        val diff = ContextRuleEditDiff(
            ruleId = editDiff.ruleId.toUInt(),
            nameChanged = editDiff.nameChanged,
            newName = editDiff.newName,
            newSigners = newSignerEntries,
            removedSigners = removedSignerEntries,
            newPolicies = newPolicyEntries,
            removedPolicies = removedPolicyEntries,
            modifiedPolicies = modifiedPolicyEntries,
            expiryChanged = editDiff.expiryChanged,
            newExpiry = editDiff.newExpiry?.toUInt()
        )

        // Resolve selected signers for multi-signer mode.
        val selectedSigners = resolveSelectedSigners(signerDescriptors, allPasskeySigners)

        // Call the flow orchestrator. Keep the in-process signing material registered for the
        // entire edit sequence — submitContextRuleEdits runs each change as a separate transaction
        // and the wrapper clears once when the whole sequence completes.
        val result = com.soneso.smartdemo.flows.withInProcessMultiSigner(
            toDelegatedKeyPairs(delegatedSecretKeys),
            toEd25519Secrets(ed25519SecretKeys)
        ) {
            com.soneso.smartdemo.flows.submitContextRuleEdits(
                diff = diff,
                selectedSigners = selectedSigners,
                onProgress = onProgress
            )
        }

        return ContextRuleEditResultBridge(
            success = result.success,
            completedOperations = result.completedOperations,
            totalOperations = result.totalOperations,
            partialDueToAuthGuard = result.partialDueToAuthGuard,
            authGuardMessage = result.authGuardMessage,
            error = result.error,
            failedStep = result.failedStep,
            transactionHashes = result.transactionHashes
        )
    }

    /**
     * Reads on-chain policy parameters for a specific policy on a context rule.
     *
     * Delegates to [com.soneso.smartdemo.flows.readPolicyParams] and converts the result
     * to [PolicyParamsBridge] for Swift consumption.
     *
     * @param policyAddress The policy contract C-address to read from.
     * @param policyType The policy type identifier ("threshold", "spending_limit", "weighted_threshold").
     * @param contextRuleId The context rule ID the policy is installed on.
     * @return [PolicyParamsBridge] with parsed parameters, or null if the entry cannot be read.
     */
    @Throws(Exception::class)
    suspend fun readPolicyParams(
        policyAddress: String,
        policyType: String,
        contextRuleId: Int
    ): PolicyParamsBridge? {
        val contractId = DemoState.contractId
            ?: throw IllegalStateException("No contract ID available")

        val params = com.soneso.smartdemo.flows.readPolicyParams(
            smartAccountContractId = contractId,
            policyAddress = policyAddress,
            policyType = policyType,
            contextRuleId = contextRuleId.toUInt()
        ) ?: return null

        return PolicyParamsBridge(
            type = params.type,
            threshold = params.threshold?.toInt(),
            spendingLimit = params.spendingLimit,
            periodDays = params.periodDays,
            signerWeights = params.signerWeights?.mapValues { it.value.toInt() }
        )
    }

    // =========================================================================
    // MARK: - Private Helpers
    // =========================================================================

    /**
     * Converts a map of G-address to Stellar secret key (S...) into a map of G-address to [KeyPair].
     *
     * Blank secret values are skipped. The resulting map is consumed by the shared registration
     * flows, which derive the signing source from each keypair's secret seed.
     *
     * @param delegatedSecretKeys Map of G-address to Stellar secret key (S...).
     * @return Map of G-address to [KeyPair] for the non-blank entries.
     */
    private suspend fun toDelegatedKeyPairs(
        delegatedSecretKeys: Map<String, String>
    ): Map<String, KeyPair> {
        val result = mutableMapOf<String, KeyPair>()
        for ((address, secret) in delegatedSecretKeys) {
            if (secret.isNotBlank()) {
                result[address] = KeyPair.fromSecretSeed(secret)
            }
        }
        return result
    }

    /**
     * Converts a map of `"<verifierAddress>:<publicKeyHex>"` to hex-encoded 32-byte seed into the
     * [Ed25519SignerIdentity] to raw-seed map consumed by the shared Ed25519 flows.
     *
     * Blank seed values are skipped.
     *
     * @param ed25519SecretKeys Map of `"<verifierAddress>:<publicKeyHex>"` to hex-encoded 32-byte seed.
     * @return Map of [Ed25519SignerIdentity] to raw 32-byte seed for the non-blank entries.
     */
    private fun toEd25519Secrets(
        ed25519SecretKeys: Map<String, String>
    ): Map<Ed25519SignerIdentity, ByteArray> {
        val result = mutableMapOf<Ed25519SignerIdentity, ByteArray>()
        for ((identityKey, seedHex) in ed25519SecretKeys) {
            if (seedHex.isBlank()) continue
            val (verifierAddress, pubKeyHex) = splitEd25519IdentityKey(identityKey)
            val identity = Ed25519SignerIdentity(
                verifierAddress = verifierAddress,
                publicKey = hexToByteArray(pubKeyHex)
            )
            result[identity] = hexToByteArray(seedHex)
        }
        return result
    }

    /**
     * Loads all unique passkey signers currently registered on-chain across all context rules.
     *
     * @throws Exception if rule loading fails; callers are submission paths where aborting
     *   with the error is preferable to matching against an empty signer pool.
     */
    private suspend fun loadOnChainPasskeySigners(): List<ExternalSigner> {
        return extractPasskeySigners(loadContextRules())
    }

    /**
     * Resolves Swift signer descriptors to a list of [SelectedSigner] instances for multi-signer
     * auth. Returns an empty list when the input is empty or when the resolution collapses to
     * a single-passkey case (which uses the implicit signing path).
     *
     * @throws IllegalStateException if any descriptor cannot be resolved to a signer.
     */
    private suspend fun resolveSelectedSigners(
        signerDescriptors: List<SignerDescriptor>,
        allPasskeySigners: List<ExternalSigner>,
    ): List<SelectedSigner> {
        if (signerDescriptors.isEmpty()) return emptyList()
        val smartAccountSigners = signerDescriptors.map { desc ->
            val signer = resolveSignerFromDescriptor(desc, allPasskeySigners)
            if (signer == null) {
                // Fail the whole submission: silently dropping the signer would
                // submit with fewer signers than selected while the UI reports success.
                val message = "Could not resolve signer: ${desc.type}/${desc.value.take(16)}"
                ActivityLogState.error(message)
                throw IllegalStateException(message)
            }
            signer
        }
        return if (isSinglePasskeyTransfer(smartAccountSigners)) {
            emptyList()
        } else {
            buildSelectedSigners(smartAccountSigners)
        }
    }

    /**
     * Resolves an ordered list of Swift signer descriptors into the [SelectedSigner] list for a
     * multi-signer transfer or approve operation.
     *
     * Resolution delegates to [resolveSignerFromDescriptor]: passkey descriptors are matched
     * against the on-chain passkey signers (loaded from the current context rules) plus any
     * session-registered passkeys, by credential ID with a keyData-hex fallback; delegated and
     * Ed25519 descriptors are constructed directly. Registration and
     * cleanup of delegated and Ed25519 signing material are owned by the shared
     * [multiSignerTransferWithEd25519] and [multiSignerApproveAllowanceWithEd25519] flows, which
     * register before submission and clear in a finally block. This helper only performs descriptor
     * resolution.
     *
     * @param signerDescriptors Ordered list of Swift signer descriptors.
     * @return The resolved [SelectedSigner] list, in signing order.
     * @throws IllegalStateException if any descriptor cannot be resolved to a signer.
     */
    private suspend fun resolveMultiSignerSelection(
        signerDescriptors: List<SignerDescriptor>
    ): List<SelectedSigner> {
        val allPasskeySigners = loadOnChainPasskeySigners()
        val smartAccountSigners = mutableListOf<SmartAccountSigner>()
        for (desc in signerDescriptors) {
            val signer = resolveSignerFromDescriptor(desc, allPasskeySigners)
            if (signer == null) {
                // Fail the whole submission: silently dropping the signer would
                // submit with fewer signers than selected while the UI reports success.
                val message = if (desc.type.lowercase() == "passkey") {
                    "Could not resolve passkey signer for credential: ${desc.value.take(16)}..."
                } else {
                    "Unsupported signer type: ${desc.type}"
                }
                ActivityLogState.error(message)
                throw IllegalStateException(message)
            }
            smartAccountSigners.add(signer)
        }
        return buildSelectedSigners(smartAccountSigners)
    }

    /**
     * Splits an Ed25519 identity key of the form `"<verifierAddress>:<publicKeyHex>"` into
     * its two components.
     *
     * The verifier address is a C-strkey (56 characters). The colon separator appears at
     * index 56. Everything after the colon is the hex-encoded public key.
     *
     * @throws IllegalArgumentException if the key does not contain the expected separator.
     */
    private fun splitEd25519IdentityKey(identityKey: String): Pair<String, String> {
        val separatorIndex = identityKey.indexOf(':')
        require(separatorIndex > 0) {
            "Ed25519 identity key must be in the form '<verifierAddress>:<publicKeyHex>'"
        }
        return Pair(
            identityKey.substring(0, separatorIndex),
            identityKey.substring(separatorIndex + 1)
        )
    }

    /**
     * Constructs a [ContextRuleType] from a type name string and an optional parameter.
     *
     * @throws IllegalArgumentException if contextTypeName is not one of "default",
     *   "call_contract", or "create_contract", if "call_contract" or "create_contract" is
     *   missing its parameter, or if the "create_contract" parameter is not a valid
     *   even-length hex string.
     */
    private fun buildContextRuleType(
        contextTypeName: String,
        contextTypeParam: String?
    ): ContextRuleType {
        return when (contextTypeName.lowercase()) {
            "call_contract" -> {
                val addr = contextTypeParam
                    ?: throw IllegalArgumentException("call_contract requires a contract address")
                OZBuilders.createCallContractContextType(addr)
            }
            "create_contract" -> {
                val hex = contextTypeParam
                    ?: throw IllegalArgumentException("create_contract requires a WASM hash hex string")
                OZBuilders.createCreateContractContextType(hex)
            }
            "default" -> OZBuilders.createDefaultContextType()
            // An unrecognized tag must not fall through to Default — that would grant the
            // broadest authorization instead of what the caller asked for.
            else -> throw IllegalArgumentException("Unknown context type: $contextTypeName")
        }
    }

    /**
     * Passkey signers registered during this session via [registerPasskeySigner].
     * Stored here so they can be resolved in [buildSignerList] even before they
     * appear on any on-chain context rule.
     *
     * Unsynchronized; safe only under the class's main-thread-only calling contract.
     */
    private val registeredPasskeySigners = mutableListOf<ExternalSigner>()

    /**
     * Converts a list of [SignerDescriptor] to [SmartAccountSigner] instances via
     * [resolveSignerFromDescriptor]: passkey descriptors are resolved from
     * [allPasskeySigners] and [registeredPasskeySigners] by credential ID with a
     * keyData-hex fallback; delegated and Ed25519 descriptors are constructed
     * directly. An unresolvable descriptor aborts the submission.
     */
    private fun buildSignerList(
        descriptors: List<SignerDescriptor>,
        allPasskeySigners: List<ExternalSigner>
    ): List<SmartAccountSigner> {
        val result = mutableListOf<SmartAccountSigner>()
        for (desc in descriptors) {
            val signer = resolveSignerFromDescriptor(desc, allPasskeySigners)
            if (signer == null) {
                // Fail the whole submission: silently dropping the signer would
                // submit with fewer signers than selected while the UI reports success.
                val message = if (desc.type.lowercase() == "passkey") {
                    "Passkey signer not found for credential: ${desc.value.take(16)}..."
                } else {
                    "Unknown signer type in descriptor: ${desc.type}"
                }
                ActivityLogState.error(message)
                throw IllegalStateException(message)
            }
            result.add(signer)
        }
        return result
    }

    /**
     * Converts a list of [PolicyDescriptor] to [FlowPolicyEntry] instances with encoded SCVal
     * params. SCVal encoding delegates to [buildPolicyScVal]; a failed policy build aborts
     * the submission.
     */
    private fun buildPolicyEntries(
        descriptors: List<PolicyDescriptor>,
        signers: List<SmartAccountSigner>
    ): List<FlowPolicyEntry> {
        return descriptors.map { desc ->
            try {
                FlowPolicyEntry(address = desc.policyAddress, scVal = buildPolicyScVal(desc, signers))
            } catch (e: Exception) {
                // Fail the whole submission: silently dropping the policy would create
                // the rule without it while the UI reports success.
                ActivityLogState.error(
                    "Failed to build policy ${desc.policyAddress}: ${e.message}"
                )
                throw e
            }
        }
    }

    /**
     * Converts a [ParsedContextRule] to a [ParsedRuleBridge] for Swift consumption.
     */
    private fun convertRuleToBridge(rule: ParsedContextRule): ParsedRuleBridge {
        val (contextTypeName, contextTypeParam) = when (val ct = rule.contextType) {
            is ContextRuleType.Default -> Pair("default", null)
            is ContextRuleType.CallContract -> Pair("call_contract", ct.contractAddress)
            is ContextRuleType.CreateContract -> Pair("create_contract", ct.wasmHash.toHexString())
        }

        val signerDescs = rule.signers.map { signer ->
            when (signer) {
                is DelegatedSigner -> SignerDescriptor(type = "delegated", value = signer.address, isPending = false)
                is ExternalSigner -> {
                    val credId = SmartAccountBuilders.getCredentialIdStringFromSigner(signer)
                    if (credId != null) {
                        SignerDescriptor(type = "passkey", value = credId, isPending = false)
                    } else {
                        // Ed25519 signer: expose hex-encoded public key (full keyData).
                        SignerDescriptor(type = "ed25519", value = signer.keyData.toHexString(), isPending = false)
                    }
                }
            }
        }

        // Policies are stored on-chain; we expose their addresses and resolve known policy types.
        val policyDescs = rule.policies.map { address ->
            val known = KNOWN_POLICIES.find { it.address == address }
            PolicyDescriptor(
                policyAddress = address,
                policyType = known?.type ?: "unknown",
                params = emptyMap()
            )
        }

        return ParsedRuleBridge(
            name = rule.name,
            contextType = contextTypeName,
            contextTypeParam = contextTypeParam,
            validUntil = rule.validUntil?.toLong(),
            signers = signerDescs,
            signerIds = rule.signerIds.map { it.toInt() },
            policies = policyDescs,
            policyIds = rule.policyIds.map { it.toInt() }
        )
    }

    /**
     * Converts a [SmartAccountSigner] to a [SignerInfoBridge].
     *
     * External signers are classified by credential ID first (passkey), then by key size
     * (32-byte key data = Ed25519); anything else is an external verifier signer labeled
     * via [describeSignerType].
     *
     * @param signer The signer instance.
     * @param canSign Whether this signer can currently sign (true for the connected passkey or
     *   registered delegated keypairs).
     */
    private fun convertSignerInfoToBridge(
        signer: SmartAccountSigner,
        canSign: Boolean
    ): SignerInfoBridge {
        return when (signer) {
            is DelegatedSigner -> SignerInfoBridge(
                type = "delegated",
                displayName = "Stellar Account",
                identifier = signer.address,
                canSign = canSign,
                keyData = null,
                verifierAddress = null
            )
            is ExternalSigner -> {
                val credId = SmartAccountBuilders.getCredentialIdStringFromSigner(signer)
                if (credId != null) {
                    SignerInfoBridge(
                        type = "passkey",
                        displayName = "Passkey (${credId.take(8)}...)",
                        identifier = credId,
                        canSign = canSign,
                        keyData = signer.keyData.toHexString(),
                        verifierAddress = signer.verifierAddress
                    )
                } else if (signer.keyData.size == SmartAccountConstants.ED25519_PUBLIC_KEY_SIZE) {
                    val hexKey = signer.keyData.toHexString()
                    SignerInfoBridge(
                        type = "ed25519",
                        displayName = "Ed25519 (${hexKey.take(8)}...)",
                        identifier = hexKey,
                        canSign = canSign,
                        keyData = null,
                        verifierAddress = signer.verifierAddress
                    )
                } else {
                    SignerInfoBridge(
                        type = "external",
                        displayName = "${describeSignerType(signer)} " +
                            "(${truncateAddress(signer.verifierAddress, 4, 4)})",
                        identifier = signer.keyData.toHexString(),
                        canSign = canSign,
                        keyData = null,
                        verifierAddress = signer.verifierAddress
                    )
                }
            }
        }
    }

    /**
     * Resolves a single [SignerDescriptor] to a [SmartAccountSigner] instance.
     *
     * Passkey descriptors are resolved from [allPasskeySigners] and [registeredPasskeySigners]
     * by credential ID. Delegated and Ed25519 descriptors are constructed directly.
     *
     * @return The resolved signer, or null if a passkey signer cannot be found.
     */
    private fun resolveSignerFromDescriptor(
        desc: SignerDescriptor,
        allPasskeySigners: List<ExternalSigner>
    ): SmartAccountSigner? {
        val allKnown = allPasskeySigners + registeredPasskeySigners
        return when (desc.type.lowercase()) {
            "passkey" -> {
                allKnown.firstOrNull { signer ->
                    SmartAccountBuilders.getCredentialIdStringFromSigner(signer) == desc.value
                } ?: allKnown.firstOrNull { signer ->
                    signer.keyData.toHexString() == desc.value
                }
            }
            "delegated" -> buildDelegatedSigner(desc.value)
            "ed25519" -> buildEd25519Signer(hexToByteArray(desc.value))
            else -> null
        }
    }

    /**
     * Builds an SCVal from a [PolicyDescriptor] for use in add/create/modify policy operations.
     *
     * For "threshold": reads the "threshold" key from params.
     * For "spending_limit": reads "amount" and "period_days" from params; period is converted to
     *   ledgers using [getLedgersPerDay].
     * For "weighted_threshold": reads "threshold" and parses "weights" as a
     *   comma-separated list of "identifier:weight" pairs; signer objects are resolved
     *   from [signers] via [signerMatchesIdentifier].
     *
     * @param desc The policy descriptor with type and params.
     * @param signers Available signers for resolving weighted threshold weights.
     * @return The encoded SCVal for the policy install parameters.
     */
    private fun buildPolicyScVal(
        desc: PolicyDescriptor,
        signers: List<SmartAccountSigner>
    ): com.soneso.stellar.sdk.xdr.SCValXdr {
        return when (desc.policyType.lowercase()) {
            "threshold" -> {
                val threshold = desc.params["threshold"]?.toUIntOrNull()
                    ?: throw IllegalArgumentException(
                        "threshold policy requires 'threshold' param (got: ${desc.params["threshold"]})"
                    )
                buildSimpleThresholdScVal(threshold)
            }
            "spending_limit" -> {
                val amount = desc.params["amount"]
                    ?: throw IllegalArgumentException("spending_limit policy requires 'amount' param")
                val periodDays = desc.params["period_days"]?.toUIntOrNull() ?: 1u
                val periodLedgers = periodDays * getLedgersPerDay().toUInt()
                buildSpendingLimitScVal(amount, periodLedgers)
            }
            "weighted_threshold" -> {
                val threshold = desc.params["threshold"]?.toUIntOrNull()
                    ?: throw IllegalArgumentException("weighted_threshold policy requires 'threshold' param")
                buildWeightedThresholdScVal(
                    parseSignerWeights(desc.params["weights"] ?: "", signers),
                    threshold
                )
            }
            else -> throw IllegalArgumentException("Unknown policy type: ${desc.policyType}")
        }
    }

    /**
     * Builds an [EditPolicyEntry] for the new-policy and modified-policy arms of an edit
     * diff: KNOWN_POLICIES lookup, SCVal encoding via [buildPolicyScVal], and entry
     * assembly. A new policy ([modified] = false) is not on-chain yet, and a modified
     * policy is always an original, so `isOriginal` mirrors [modified] at both call sites.
     *
     * @param desc The policy descriptor with type and params.
     * @param signers Available signers for resolving weighted threshold weights.
     * @param onChainId The on-chain policy ID for modified policies, null for new ones.
     * @param modified True when the entry updates an existing on-chain policy.
     */
    private fun toEditPolicyEntry(
        desc: PolicyDescriptor,
        signers: List<SmartAccountSigner>,
        onChainId: UInt?,
        modified: Boolean
    ): EditPolicyEntry {
        val known = KNOWN_POLICIES.find { it.address == desc.policyAddress }
        return EditPolicyEntry(
            info = known?.let { com.soneso.smartdemo.config.PolicyInfo(it.type, it.name, it.description, it.address) },
            label = known?.name ?: "Unknown Policy",
            address = desc.policyAddress,
            scVal = buildPolicyScVal(desc, signers),
            onChainId = onChainId,
            isOriginal = modified,
            modified = modified
        )
    }

    /**
     * Parses a comma-separated `"identifier:weight"` list into a signer-weight map,
     * resolving each identifier against [signers].
     *
     * Identifier semantics match the Swift entry identifiers: a G-address for delegated
     * signers, the Base64URL credential ID for passkeys, and the hex-encoded key data
     * for Ed25519 signers.
     *
     * @throws IllegalArgumentException when an entry is not in `"identifier:weight"`
     *   format, an identifier resolves to none of the provided signers, or a weight
     *   is not a positive integer.
     */
    private fun parseSignerWeights(
        weightsRaw: String,
        signers: List<SmartAccountSigner>
    ): Map<SmartAccountSigner, UInt> {
        val weightMap = mutableMapOf<SmartAccountSigner, UInt>()
        if (weightsRaw.isBlank()) return weightMap
        for (pair in weightsRaw.split(",")) {
            val parts = pair.trim().split(":")
            if (parts.size != 2) {
                throw IllegalArgumentException(
                    "weighted_threshold weights entry must use 'identifier:weight' format, got: '${pair.trim()}'"
                )
            }
            val identifier = parts[0].trim()
            val w = parts[1].trim().toUIntOrNull()?.takeIf { it > 0u }
                ?: throw IllegalArgumentException(
                    "weighted_threshold weight for '$identifier' must be a positive integer, got: ${parts[1].trim()}"
                )
            val signer = signers.firstOrNull { s -> signerMatchesIdentifier(s, identifier) }
                ?: throw IllegalArgumentException(
                    "weighted_threshold weight references a signer that is not on the rule: $identifier"
                )
            weightMap[signer] = w
        }
        return weightMap
    }

    /**
     * Returns true when [identifier] denotes [signer]: the address of a delegated
     * signer, the Base64URL credential ID of a passkey signer, or the hex-encoded
     * key data of an Ed25519 signer (case-insensitive hex).
     */
    private fun signerMatchesIdentifier(signer: SmartAccountSigner, identifier: String): Boolean {
        return when (signer) {
            is DelegatedSigner -> signer.address == identifier
            is ExternalSigner -> {
                val credId = SmartAccountBuilders.getCredentialIdStringFromSigner(signer)
                if (credId != null) {
                    credId == identifier
                } else {
                    signer.keyData.toHexString().equals(identifier, ignoreCase = true)
                }
            }
        }
    }

}

/**
 * Provides the macOS window anchor for ASAuthorizationController passkey sheets.
 *
 * On macOS, ASAuthorizationController requires a presentation context provider that
 * returns the NSWindow in which to present the authorization sheet. Without this,
 * the controller fails with error code 1004 ("No host window provided").
 *
 * This implementation returns the application's key window, falling back to the main
 * window and then to the first visible window, so the passkey sheet stays attached to
 * a window the user can see. A detached NSWindow is constructed only when the
 * application has no visible window at all.
 */
private class MacOSPresentationContextProvider :
    NSObject(),
    ASAuthorizationControllerPresentationContextProvidingProtocol {

    override fun presentationAnchorForAuthorizationController(
        controller: ASAuthorizationController
    ): ASPresentationAnchor {
        val app = NSApplication.sharedApplication
        return app.keyWindow
            ?: app.mainWindow
            ?: app.windows.filterIsInstance<NSWindow>().firstOrNull { it.visible }
            ?: NSWindow()
    }
}
