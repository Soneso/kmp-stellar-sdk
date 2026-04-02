//
//  MacOSBridge.kt
//  Smart Account Demo
//
//  Copyright (c) 2026 Soneso. All rights reserved.
//

package com.soneso.smartdemo

import com.soneso.smartdemo.config.DemoConfig
import com.soneso.smartdemo.config.KNOWN_POLICIES
import com.soneso.smartdemo.flows.ContextRuleResult
import com.soneso.smartdemo.flows.FlowPolicyEntry
import com.soneso.smartdemo.flows.SignerEntry
import com.soneso.smartdemo.flows.TransferResult
import com.soneso.smartdemo.flows.WalletConnectionResult
import com.soneso.smartdemo.flows.WalletCreationResult
import com.soneso.smartdemo.flows.addContextRule
import com.soneso.smartdemo.flows.buildDelegatedSigner
import com.soneso.smartdemo.flows.buildEd25519Signer
import com.soneso.smartdemo.flows.buildSelectedSigners
import com.soneso.smartdemo.flows.connectWithAddress
import com.soneso.smartdemo.flows.deletePendingCredential
import com.soneso.smartdemo.flows.disconnect
import com.soneso.smartdemo.flows.initializeKit
import com.soneso.smartdemo.flows.loadAccountSigners
import com.soneso.smartdemo.flows.loadAvailablePasskeySigners
import com.soneso.smartdemo.flows.loadAvailableSigners
import com.soneso.smartdemo.flows.loadContextRule
import com.soneso.smartdemo.flows.loadContextRules
import com.soneso.smartdemo.flows.manualConnect
import com.soneso.smartdemo.flows.multiSignerTransfer
import com.soneso.smartdemo.flows.quickConnect
import com.soneso.smartdemo.flows.refreshBalances
import com.soneso.smartdemo.flows.registerPasskeySigner
import com.soneso.smartdemo.flows.removeContextRule
import com.soneso.smartdemo.flows.resolveAbsoluteLedger
import com.soneso.smartdemo.flows.retryPendingDeploy
import com.soneso.smartdemo.flows.transfer
import com.soneso.smartdemo.flows.updateContextRuleName
import com.soneso.smartdemo.flows.updateContextRuleValidUntil
import com.soneso.smartdemo.state.ActivityLogState
import com.soneso.smartdemo.state.DemoState
import com.soneso.smartdemo.util.buildSimpleThresholdScVal
import com.soneso.smartdemo.util.buildSpendingLimitScVal
import com.soneso.smartdemo.util.buildWeightedThresholdScVal
import com.soneso.smartdemo.util.formatContextType
import com.soneso.smartdemo.util.hexToByteArray
import com.soneso.smartdemo.util.isUserCancellation
import com.soneso.smartdemo.util.parseSingleContextRuleFromScVal
import com.soneso.smartdemo.util.toHexString
import com.soneso.smartdemo.util.truncateAddress
import com.soneso.stellar.sdk.KeyPair
import com.soneso.stellar.sdk.smartaccount.AppleWebAuthnProvider
import com.soneso.stellar.sdk.smartaccount.UserDefaultsStorageAdapter
import com.soneso.stellar.sdk.smartaccount.core.DelegatedSigner
import com.soneso.stellar.sdk.smartaccount.core.ExternalSigner
import com.soneso.stellar.sdk.smartaccount.core.SmartAccountBuilders
import com.soneso.stellar.sdk.smartaccount.core.SmartAccountSigner
import com.soneso.stellar.sdk.Util
import com.soneso.stellar.sdk.smartaccount.oz.ContextRuleType
import com.soneso.stellar.sdk.smartaccount.oz.OZConstants
import com.soneso.stellar.sdk.smartaccount.oz.ParsedContextRule
import com.soneso.stellar.sdk.smartaccount.oz.SelectedSigner
import com.soneso.stellar.sdk.smartaccount.oz.StoredCredential
import platform.AppKit.NSApplication
import platform.AppKit.NSWindow
import platform.AuthenticationServices.ASAuthorizationController
import platform.AuthenticationServices.ASAuthorizationControllerPresentationContextProvidingProtocol
import platform.AuthenticationServices.ASPresentationAnchor
import platform.darwin.NSObject
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

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
     * Returns the bridge version string.
     */
    fun getVersion(): String = "0.1.0"

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

            DemoState.webauthnProvider = webauthnProvider
            DemoState.storage = storage

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
        onProgress: (String) -> Unit
    ): WalletCreationResult = com.soneso.smartdemo.flows.createWallet(username, onProgress)

    // =========================================================================
    // MARK: - Wallet Connection
    // =========================================================================

    /**
     * Attempts to restore the last session or trigger a passkey authentication prompt.
     *
     * @return [WalletConnectionResult] on success, null if no wallet was found or restored.
     * @throws Exception if the WebAuthn ceremony or network call fails.
     */
    @Throws(Exception::class)
    suspend fun quickConnect(): WalletConnectionResult? =
        com.soneso.smartdemo.flows.quickConnect()

    /**
     * Two-step connect: authenticates a passkey first, then resolves the contract via the indexer.
     *
     * @return [WalletConnectionResult] on success, null if the contract cannot be resolved.
     * @throws Exception if the WebAuthn ceremony or indexer lookup fails.
     */
    @Throws(Exception::class)
    suspend fun manualConnect(): WalletConnectionResult? =
        com.soneso.smartdemo.flows.manualConnect()

    /**
     * Connects to a known contract address using any registered passkey.
     *
     * @param contractAddress C-address of the smart account contract.
     * @return [WalletConnectionResult] on success, null if connection fails.
     * @throws Exception if the WebAuthn ceremony or network call fails.
     */
    @Throws(Exception::class)
    suspend fun connectWithAddress(contractAddress: String): WalletConnectionResult? =
        com.soneso.smartdemo.flows.connectWithAddress(contractAddress)

    /**
     * Retries a pending wallet deployment for a previously registered passkey.
     *
     * @param credentialId Base64URL-encoded credential ID of the pending deployment.
     * @param contractId C-address of the contract to deploy or connect to, if known.
     * @return [WalletConnectionResult] on success, null if deployment cannot be completed.
     * @throws Exception if the network call fails.
     */
    @Throws(Exception::class)
    suspend fun retryPendingDeploy(
        credentialId: String,
        contractId: String?
    ): WalletConnectionResult? =
        com.soneso.smartdemo.flows.retryPendingDeploy(credentialId, contractId)

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
    ): TransferResult = com.soneso.smartdemo.flows.transfer(tokenContract, recipient, amount)

    /**
     * Transfers tokens using an explicit list of signers.
     *
     * Each [SignerDescriptor] in [signerDescriptors] is mapped to a [SelectedSigner]:
     * - "passkey": resolved from context rules by credential ID to obtain full key data.
     * - "delegated": constructed as [SelectedSigner.Wallet] by G-address.
     * - "ed25519": not supported in multi-signer transfer (ed25519 signers are passkey/external).
     *
     * Delegated signer keypairs must be provided in [delegatedSecretKeys] (G-address → secret key)
     * so the bridge can register them with the [ExternalSignerManagerAdapter] before the call.
     *
     * @param tokenContract C-address of the token contract.
     * @param recipient Recipient G-address or C-address.
     * @param amount Transfer amount as a decimal string.
     * @param signerDescriptors Ordered list of signers that must participate.
     * @param delegatedSecretKeys Map of G-address to Stellar secret key (S...) for delegated signers.
     * @return [TransferResult] with success flag, transaction hash, and optional error.
     * @throws Exception if the kit is not initialized, any secret key is invalid, or signing fails.
     */
    @Throws(Exception::class)
    suspend fun multiSignerTransfer(
        tokenContract: String,
        recipient: String,
        amount: String,
        signerDescriptors: List<SignerDescriptor>,
        delegatedSecretKeys: Map<String, String>
    ): TransferResult {
        // Register delegated keypairs so the ExternalSignerManagerAdapter can sign auth entries.
        val externalManager = DemoState.externalSignerManager
            ?: throw IllegalStateException("External signer manager not initialized")
        externalManager.removeAll()
        for ((_, secret) in delegatedSecretKeys) {
            if (secret.isNotBlank()) {
                externalManager.addFromSecret(secret)
            }
        }

        // Resolve signers. Passkey signers require a full ExternalSigner instance including
        // keyData, so we look them up from context rules by credential ID.
        val rules = try {
            loadContextRules()
        } catch (_: Exception) {
            emptyList()
        }
        val allPasskeySigners = rules.flatMap { it.signers }
            .filterIsInstance<ExternalSigner>()
            .filter { it.verifierAddress == DemoConfig.WEBAUTHN_VERIFIER_ADDRESS }
            .distinctBy { SmartAccountBuilders.getSignerKey(it) }

        val smartAccountSigners = mutableListOf<SmartAccountSigner>()
        for (desc in signerDescriptors) {
            when (desc.type.lowercase()) {
                "passkey" -> {
                    // Look up the ExternalSigner by credential ID suffix in keyData.
                    val found = allPasskeySigners.firstOrNull { signer ->
                        SmartAccountBuilders.getCredentialIdStringFromSigner(signer) == desc.value
                    }
                    if (found != null) {
                        smartAccountSigners.add(found)
                    } else {
                        ActivityLogState.error(
                            "Could not resolve passkey signer for credential: ${desc.value.take(16)}..."
                        )
                    }
                }
                "delegated" -> {
                    smartAccountSigners.add(buildDelegatedSigner(desc.value))
                }
                else -> {
                    ActivityLogState.error("Unsupported signer type in multi-signer transfer: ${desc.type}")
                }
            }
        }

        val selectedSigners = buildSelectedSigners(smartAccountSigners)
        return multiSignerTransfer(tokenContract, recipient, amount, selectedSigners)
    }

    // =========================================================================
    // MARK: - Account Signers
    // =========================================================================

    /**
     * Loads all unique signers registered on the connected smart account.
     *
     * @return List of [SignerEntry] (SDK type), each with a unique signer and rule memberships.
     * @throws IllegalStateException if the kit is not initialized.
     */
    @Throws(Exception::class)
    suspend fun loadAccountSigners(): List<SignerEntry> =
        com.soneso.smartdemo.flows.loadAccountSigners()

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
     * Requires passkey authentication. The UI should prevent removing the last rule.
     *
     * @param ruleId Rule ID as an Int (UInt internally). Use [loadContextRules] to get valid IDs.
     * @return [ContextRuleResult] with success flag and transaction hash.
     */
    @Throws(Exception::class)
    suspend fun removeContextRule(ruleId: Int): ContextRuleResult =
        com.soneso.smartdemo.flows.removeContextRule(ruleId.toUInt())

    /**
     * Updates the name of an existing context rule.
     *
     * Requires passkey authentication.
     *
     * @param ruleId Rule ID as an Int (converted to UInt internally).
     * @param name New name to store on-chain.
     * @return [ContextRuleResult] with success flag and transaction hash.
     */
    @Throws(Exception::class)
    suspend fun updateContextRuleName(ruleId: Int, name: String): ContextRuleResult =
        com.soneso.smartdemo.flows.updateContextRuleName(ruleId.toUInt(), name)

    /**
     * Updates the expiry of an existing context rule.
     *
     * If [validUntilOffset] is non-null, the bridge resolves the absolute ledger by adding
     * the offset to the current ledger sequence. Pass null to remove the expiry.
     *
     * Requires passkey authentication.
     *
     * @param ruleId Rule ID as an Int (converted to UInt internally).
     * @param validUntilOffset Ledger offset from now, or null to clear the expiry.
     * @return [ContextRuleResult] with success flag and transaction hash.
     */
    @Throws(Exception::class)
    suspend fun updateContextRuleValidUntil(ruleId: Int, validUntilOffset: Int?): ContextRuleResult {
        val absoluteLedger: UInt? = if (validUntilOffset != null) {
            resolveAbsoluteLedger(validUntilOffset.toUInt())
        } else {
            null
        }
        return com.soneso.smartdemo.flows.updateContextRuleValidUntil(ruleId.toUInt(), absoluteLedger)
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
     * @return [ContextRuleResult] with success flag and transaction hash.
     */
    @Throws(Exception::class)
    suspend fun addContextRule(
        contextTypeName: String,
        contextTypeParam: String?,
        name: String,
        validUntilOffset: Int?,
        signerDescriptors: List<SignerDescriptor>,
        policyDescriptors: List<PolicyDescriptor>
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
        val rules = try {
            loadContextRules()
        } catch (_: Exception) {
            emptyList()
        }
        val allPasskeySigners = rules.flatMap { it.signers }
            .filterIsInstance<ExternalSigner>()
            .filter { it.verifierAddress == DemoConfig.WEBAUTHN_VERIFIER_ADDRESS }
            .distinctBy { SmartAccountBuilders.getSignerKey(it) }
        val signers = buildSignerList(signerDescriptors, allPasskeySigners)

        // Build FlowPolicyEntry list. For each PolicyDescriptor the bridge constructs the correct
        // SCVal encoding based on policyType. The SDK's addContextRule expects a Map<String,SCValXdr>
        // but the flow layer wraps that in FlowPolicyEntry for convenience.
        val policies = buildPolicyEntries(policyDescriptors, signers)

        return com.soneso.smartdemo.flows.addContextRule(
            contextType = contextType,
            name = name,
            validUntil = validUntil,
            signers = signers,
            policies = policies
        )
    }

    /**
     * Loads a single context rule for pre-populating an edit form in Swift.
     *
     * Fetches the raw [SCValXdr] from the chain and parses it using [parseSingleContextRuleFromScVal],
     * then converts all Kotlin-specific types to their [ParsedRuleBridge] equivalents.
     *
     * @param ruleId Rule ID as an Int (converted to UInt internally).
     * @return [ParsedRuleBridge] with all rule fields in Swift-friendly format.
     * @throws Exception if the rule does not exist or the RPC call fails.
     */
    @Throws(Exception::class)
    suspend fun loadContextRuleForEdit(ruleId: Int): ParsedRuleBridge {
        val ruleIdUInt = ruleId.toUInt()
        val scVal = loadContextRule(ruleIdUInt)
        val parsed = parseSingleContextRuleFromScVal(scVal, ruleIdUInt)
            ?: throw IllegalStateException("Failed to parse context rule #$ruleId")

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

    // =========================================================================
    // MARK: - State Getters
    // =========================================================================

    /** Returns whether a wallet is currently connected. */
    fun isConnected(): Boolean = DemoState.isConnected

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

    /**
     * Returns the maximum number of signers allowed per context rule.
     *
     * The OpenZeppelin smart account contract enforces a per-rule signer limit.
     */
    fun getMaxSigners(): Int = OZConstants.MAX_SIGNERS

    /**
     * Returns the maximum number of policies allowed per context rule.
     *
     * The OpenZeppelin smart account contract enforces a per-rule policy limit.
     */
    fun getMaxPolicies(): Int = OZConstants.MAX_POLICIES

    /** Returns the approximate number of ledgers per hour (at ~5 s/ledger). */
    fun getLedgersPerHour(): Int = Util.LEDGERS_PER_HOUR.toInt()

    /** Returns the approximate number of ledgers per day (at ~5 s/ledger). */
    fun getLedgersPerDay(): Int = Util.LEDGERS_PER_DAY.toInt()

    // =========================================================================
    // MARK: - Utility Methods
    // =========================================================================

    /**
     * Formats a context type tag and optional parameter into a human-readable display string.
     *
     * @param contextTypeName Context type tag: "default", "call_contract", or "create_contract".
     * @param contextTypeParam Contract address or WASM hash hex. Null for "default".
     * @return Human-readable string such as "Default (Any Operation)" or "Call Contract: ABCD...WXYZ".
     */
    fun formatContextType(contextTypeName: String, contextTypeParam: String?): String {
        val type = buildContextRuleTypeNoThrow(contextTypeName, contextTypeParam)
        return formatContextType(type)
    }

    /**
     * Returns a human-readable description of a signer type string.
     *
     * @param signerType Signer type string: "passkey", "delegated", or "ed25519".
     * @return Display string such as "Passkey (WebAuthn)", "Stellar Account", or "Ed25519".
     */
    fun describeSignerType(signerType: String): String {
        return when (signerType.lowercase()) {
            "passkey" -> "Passkey (WebAuthn)"
            "delegated" -> "Stellar Account"
            "ed25519" -> "Ed25519"
            else -> signerType
        }
    }

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
     * @param secretSeed The secret seed (S...) to validate.
     * @param expectedAddress The expected G-address.
     * @return True if the seed derives the expected address.
     */
    @Throws(Exception::class)
    suspend fun validateDelegatedKey(secretSeed: String, expectedAddress: String): Boolean {
        return try {
            val keypair = KeyPair.fromSecretSeed(secretSeed)
            keypair.getAccountId() == expectedAddress
        } catch (_: Exception) {
            false
        }
    }

    // =========================================================================
    // MARK: - Private Helpers
    // =========================================================================

    /**
     * Constructs a [ContextRuleType] from a type name string and an optional parameter.
     *
     * @throws IllegalArgumentException if contextTypeName is "create_contract" and contextTypeParam
     *   is not a valid even-length hex string.
     */
    private fun buildContextRuleType(
        contextTypeName: String,
        contextTypeParam: String?
    ): ContextRuleType {
        return when (contextTypeName.lowercase()) {
            "call_contract" -> {
                val addr = contextTypeParam
                    ?: throw IllegalArgumentException("call_contract requires a contract address")
                ContextRuleType.CallContract(addr)
            }
            "create_contract" -> {
                val hex = contextTypeParam
                    ?: throw IllegalArgumentException("create_contract requires a WASM hash hex string")
                ContextRuleType.CreateContract(hexToByteArray(hex))
            }
            else -> ContextRuleType.Default
        }
    }

    /**
     * Non-throwing variant of [buildContextRuleType], returning [ContextRuleType.Default] on error.
     */
    private fun buildContextRuleTypeNoThrow(
        contextTypeName: String,
        contextTypeParam: String?
    ): ContextRuleType {
        return try {
            buildContextRuleType(contextTypeName, contextTypeParam)
        } catch (_: Exception) {
            ContextRuleType.Default
        }
    }

    /**
     * Converts a list of [SignerDescriptor] to [SmartAccountSigner] instances.
     *
     * Passkey descriptors are resolved from [allPasskeySigners] by credential ID.
     * Delegated descriptors are constructed directly from the address.
     * Ed25519 descriptors are constructed from a hex-encoded 32-byte public key.
     */
    /**
     * Passkey signers registered during this session via [registerPasskeySigner].
     * Stored here so they can be resolved in [buildSignerList] even before they
     * appear on any on-chain context rule.
     */
    private val registeredPasskeySigners = mutableListOf<ExternalSigner>()

    private fun buildSignerList(
        descriptors: List<SignerDescriptor>,
        allPasskeySigners: List<ExternalSigner>
    ): List<SmartAccountSigner> {
        // Combine on-chain signers with session-registered signers for lookup.
        val allKnown = allPasskeySigners + registeredPasskeySigners
        val result = mutableListOf<SmartAccountSigner>()
        for (desc in descriptors) {
            when (desc.type.lowercase()) {
                "passkey" -> {
                    // Try matching by credential ID first, then by keyData hex.
                    val found = allKnown.firstOrNull { signer ->
                        SmartAccountBuilders.getCredentialIdStringFromSigner(signer) == desc.value
                    } ?: allKnown.firstOrNull { signer ->
                        signer.keyData.toHexString() == desc.value
                    }
                    if (found != null) {
                        result.add(found)
                    } else {
                        ActivityLogState.error(
                            "Passkey signer not found for credential: ${desc.value.take(16)}..."
                        )
                    }
                }
                "delegated" -> {
                    result.add(buildDelegatedSigner(desc.value))
                }
                "ed25519" -> {
                    val pubKeyBytes = hexToByteArray(desc.value)
                    result.add(buildEd25519Signer(pubKeyBytes))
                }
                else -> {
                    ActivityLogState.error("Unknown signer type in descriptor: ${desc.type}")
                }
            }
        }
        return result
    }

    /**
     * Converts a list of [PolicyDescriptor] to [FlowPolicyEntry] instances with encoded SCVal params.
     *
     * For "threshold": reads the "threshold" key from params.
     * For "spending_limit": reads "amount" and "period_days" from params; period is converted to
     *   ledgers using [getLedgersPerDay].
     * For "weighted_threshold": reads "threshold" and parses "weights" as a
     *   comma-separated list of "address:weight" pairs; signer objects are resolved from [signers].
     */
    private fun buildPolicyEntries(
        descriptors: List<PolicyDescriptor>,
        signers: List<SmartAccountSigner>
    ): List<FlowPolicyEntry> {
        return descriptors.mapNotNull { desc ->
            try {
                val scVal = when (desc.policyType.lowercase()) {
                    "threshold" -> {
                        val threshold = desc.params["threshold"]?.toUIntOrNull()
                            ?: throw IllegalArgumentException(
                                "threshold policy requires 'threshold' param (got: ${desc.params["threshold"]})"
                            )
                        buildSimpleThresholdScVal(threshold)
                    }
                    "spending_limit" -> {
                        val amount = desc.params["amount"]
                            ?: throw IllegalArgumentException(
                                "spending_limit policy requires 'amount' param"
                            )
                        val periodDays = desc.params["period_days"]?.toUIntOrNull() ?: 1u
                        val periodLedgers = periodDays * getLedgersPerDay().toUInt()
                        buildSpendingLimitScVal(amount, periodLedgers)
                    }
                    "weighted_threshold" -> {
                        val threshold = desc.params["threshold"]?.toUIntOrNull()
                            ?: throw IllegalArgumentException(
                                "weighted_threshold policy requires 'threshold' param"
                            )
                        // Parse "addr1:3,addr2:2" weight pairs, resolving addresses to signers.
                        val weightsRaw = desc.params["weights"] ?: ""
                        val weightMap = mutableMapOf<SmartAccountSigner, UInt>()
                        if (weightsRaw.isNotBlank()) {
                            for (pair in weightsRaw.split(",")) {
                                val parts = pair.trim().split(":")
                                if (parts.size == 2) {
                                    val addr = parts[0].trim()
                                    val w = parts[1].trim().toUIntOrNull() ?: continue
                                    val signer = signers.firstOrNull { s ->
                                        when (s) {
                                            is DelegatedSigner -> s.address == addr
                                            else -> false
                                        }
                                    } ?: DelegatedSigner(addr)
                                    weightMap[signer] = w
                                }
                            }
                        }
                        buildWeightedThresholdScVal(weightMap, threshold)
                    }
                    else -> throw IllegalArgumentException("Unknown policy type: ${desc.policyType}")
                }
                FlowPolicyEntry(address = desc.policyAddress, scVal = scVal)
            } catch (e: Exception) {
                ActivityLogState.error(
                    "Failed to build policy ${desc.policyAddress}: ${e.message}"
                )
                null
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
                is DelegatedSigner -> SignerDescriptor(type = "delegated", value = signer.address)
                is ExternalSigner -> {
                    val credId = SmartAccountBuilders.getCredentialIdStringFromSigner(signer)
                    if (credId != null) {
                        SignerDescriptor(type = "passkey", value = credId)
                    } else {
                        // Ed25519 signer: expose hex-encoded public key (full keyData).
                        SignerDescriptor(type = "ed25519", value = signer.keyData.toHexString())
                    }
                }
            }
        }

        // Policies are stored on-chain; we expose their addresses without SCVal params.
        val policyDescs = rule.policies.map { address ->
            PolicyDescriptor(
                policyAddress = address,
                policyType = "unknown",
                params = emptyMap()
            )
        }

        return ParsedRuleBridge(
            name = rule.name,
            contextType = contextTypeName,
            contextTypeParam = contextTypeParam,
            validUntil = rule.validUntil?.toLong(),
            signers = signerDescs,
            policies = policyDescs
        )
    }

    /**
     * Converts a [SmartAccountSigner] to a [SignerInfoBridge].
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
                keyData = null
            )
            is ExternalSigner -> {
                val credId = SmartAccountBuilders.getCredentialIdStringFromSigner(signer)
                if (credId != null) {
                    SignerInfoBridge(
                        type = "passkey",
                        displayName = "Passkey (${credId.take(8)}...)",
                        identifier = credId,
                        canSign = canSign,
                        keyData = signer.keyData.toHexString()
                    )
                } else {
                    val hexKey = signer.keyData.toHexString()
                    SignerInfoBridge(
                        type = "ed25519",
                        displayName = "Ed25519 (${hexKey.take(8)}...)",
                        identifier = hexKey,
                        canSign = canSign,
                        keyData = null
                    )
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
 * This implementation returns the application's key window.
 */
private class MacOSPresentationContextProvider :
    NSObject(),
    ASAuthorizationControllerPresentationContextProvidingProtocol {

    override fun presentationAnchorForAuthorizationController(
        controller: ASAuthorizationController
    ): ASPresentationAnchor {
        return NSApplication.sharedApplication.keyWindow ?: NSWindow()
    }
}
