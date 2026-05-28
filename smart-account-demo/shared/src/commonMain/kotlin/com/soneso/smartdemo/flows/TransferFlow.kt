package com.soneso.smartdemo.flows

/**
 * Business logic for token transfers from a smart account.
 *
 * Demonstrates how to transfer tokens using OZSmartAccountKit.transactionOperations.transfer:
 * - XLM via the Stellar Asset Contract (SAC): pass the SAC address as [tokenContract].
 * - Custom Soroban tokens (e.g., DEMO): pass the token contract address.
 *
 * All transfers require passkey authentication — the SDK triggers a WebAuthn ceremony
 * to sign the Soroban authorization entry before submitting the transaction.
 *
 * After a successful transfer, XLM and DEMO balances are refreshed in [DemoState].
 *
 * Shared signer utilities ([buildSelectedSigners], [isSinglePasskeyTransfer]) are defined
 * in [ContextRuleEditTypes.kt] and imported here.
 */

import com.soneso.smartdemo.state.ActivityLogState
import com.soneso.smartdemo.state.DemoState
import com.soneso.smartdemo.util.ExternalSignerManagerAdapter
import com.soneso.smartdemo.util.SignerInfo
import com.soneso.smartdemo.util.extractSignersFromRules
import com.soneso.smartdemo.util.fetchAllContextRules
import com.soneso.smartdemo.util.refreshAllBalances
import com.soneso.smartdemo.wallet.DemoEd25519Adapter
import com.soneso.stellar.sdk.smartaccount.oz.OZExternalSignerManager
import com.soneso.stellar.sdk.smartaccount.oz.SelectedSigner

/**
 * Result of a token transfer.
 *
 * @property success True if the transfer was submitted and confirmed on-chain.
 * @property hash The Stellar transaction hash, or null if the transfer failed.
 * @property error Error message if [success] is false; null on success.
 */
data class TransferResult(
    val success: Boolean,
    val hash: String?,
    val error: String?
)

/**
 * Transfers tokens from the connected smart account to a recipient address.
 *
 * SDK workflow:
 * 1. Get the connected [OZSmartAccountKit] from [DemoState].
 * 2. Call OZSmartAccountKit.transactionOperations.transfer with the token contract,
 *    recipient, and amount. The SDK:
 *    a. Simulates the transaction to compute the Soroban auth entry.
 *    b. Triggers a WebAuthn authentication ceremony to sign the auth entry with the passkey.
 *    c. Submits the transaction to the network (via the relayer if configured).
 * 3. On success, refresh XLM and DEMO balances in [DemoState].
 *
 * Both XLM and custom Soroban tokens use the same transfer() API — the only difference
 * is the [tokenContract] address passed in. XLM uses the SAC address; custom tokens
 * use their own contract address.
 *
 * @param tokenContract The contract address (C-address) of the token to transfer.
 *   Use [DemoConfig.NATIVE_TOKEN_CONTRACT] for XLM or [DemoState.demoTokenContractId] for DEMO.
 * @param recipient The recipient's Stellar account (G-address) or contract (C-address).
 * @param amount The amount to transfer as a decimal string (e.g. "10" or "10.5").
 *   The SDK converts this to stroops internally.
 * @return [TransferResult] with success/failure status, transaction hash, and optional error.
 * @throws Exception if the kit is not initialized, amount is invalid, or the passkey
 *   ceremony is cancelled. Check [isUserCancellation] to distinguish user cancellations.
 */
suspend fun transfer(
    tokenContract: String,
    recipient: String,
    amount: String
): TransferResult {
    val kit = DemoState.kit
        ?: throw IllegalStateException("Smart Account Kit not initialized")

    // Call transactionOperations.transfer — this triggers the WebAuthn ceremony on the device
    // to sign the Soroban authorization entry, then submits the transaction to the network.
    val result = kit.transactionOperations.transfer(
        tokenContract = tokenContract,
        recipient = recipient,
        amount = amount
    )

    if (result.success) {
        ActivityLogState.success("Transfer successful! Hash: ${result.hash ?: "unknown"}")

        // Refresh both XLM and DEMO balances so the UI shows the updated amounts.
        DemoState.contractId?.let { refreshAllBalances(it) }
    }

    return TransferResult(
        success = result.success,
        hash = result.hash,
        error = result.error
    )
}

/**
 * Transfers tokens from the connected smart account using an explicit list of signers.
 *
 * SDK workflow:
 * 1. Get the connected [OZSmartAccountKit] from [DemoState].
 * 2. Call OZSmartAccountKit.multiSignerManager.multiSignerTransfer with the token contract,
 *    recipient, amount, and the explicit signer list. The SDK:
 *    a. Simulates the transaction to compute Soroban auth entries.
 *    b. For each [SelectedSigner.Passkey]: triggers one OS WebAuthn authentication prompt.
 *    c. For each [SelectedSigner.Wallet]: signs via the configured ExternalWalletAdapter.
 *    d. Submits the transaction to the network (via the relayer if configured).
 * 3. On success, refresh XLM and DEMO balances in [DemoState].
 *
 * The caller is responsible for registering any delegated signer keypairs in
 * [DemoState.externalSignerManager] before calling this function.
 *
 * @param tokenContract The contract address (C-address) of the token to transfer.
 * @param recipient The recipient's Stellar account (G-address) or contract (C-address).
 * @param amount The amount to transfer as a decimal string (e.g. "10" or "10.5").
 * @param selectedSigners All signers that must participate, in signing order.
 * @return [TransferResult] with success/failure status, transaction hash, and optional error.
 */
suspend fun multiSignerTransfer(
    tokenContract: String,
    recipient: String,
    amount: String,
    selectedSigners: List<SelectedSigner>
): TransferResult {
    val kit = DemoState.kit
        ?: throw IllegalStateException("Smart Account Kit not initialized")

    // multiSignerTransfer collects signatures from all listed signers in order,
    // then submits the transaction. Passkey signers trigger WebAuthn prompts;
    // wallet signers sign via the ExternalWalletAdapter registered in the kit config.
    val result = kit.multiSignerManager.multiSignerTransfer(
        tokenContract = tokenContract,
        recipient = recipient,
        amount = amount,
        selectedSigners = selectedSigners
    )

    if (result.success) {
        ActivityLogState.success("Multi-signer transfer successful! Hash: ${result.hash ?: "unknown"}")

        // Refresh both XLM and DEMO balances so the UI shows the updated amounts.
        DemoState.contractId?.let { refreshAllBalances(it) }
    }

    return TransferResult(
        success = result.success,
        hash = result.hash,
        error = result.error
    )
}

/**
 * Loads the available signers for the connected smart account by fetching context rules.
 *
 * On failure, returns an empty list so the caller can fall back to single-signer mode.
 *
 * @return List of [SignerInfo] with signing capability flags, or empty list on error.
 */
suspend fun loadAvailableSigners(): List<SignerInfo> {
    val kit = DemoState.kit ?: return emptyList()
    return try {
        val rules = fetchAllContextRules(kit)
        extractSignersFromRules(
            rules = rules,
            connectedCredentialId = DemoState.credentialId,
            externalWallet = DemoState.externalSignerManager,
            ed25519SignerManager = DemoState.ed25519SignerManager
        )
    } catch (_: Exception) {
        emptyList()
    }
}

/**
 * Registers delegated signer keypairs in [DemoState.externalSignerManager] so that
 * [multiSignerTransfer] can sign authorization entries for [SelectedSigner.Wallet] signers.
 *
 * The SDK's multiSignerTransfer calls [ExternalWalletAdapter.canSignFor] and
 * [ExternalWalletAdapter.signAuthEntry] for each wallet signer — these rely on the
 * keypairs being registered in the adapter before the call.
 *
 * Any previously registered keypairs are cleared before adding the new set to prevent
 * stale keys from accumulating across transfers.
 *
 * @param delegatedKeyPairs Map of G-address to [com.soneso.stellar.sdk.KeyPair].
 */
suspend fun registerDelegatedKeypairs(
    delegatedKeyPairs: Map<String, com.soneso.stellar.sdk.KeyPair>
) {
    val externalManager = DemoState.externalSignerManager as? ExternalSignerManagerAdapter ?: return
    externalManager.removeAll()
    for ((_, keyPair) in delegatedKeyPairs) {
        val secretSeed = keyPair.getSecretSeed() ?: continue
        externalManager.addFromSecret(secretSeed.concatToString())
    }
}

/**
 * Registers Ed25519 signing secrets in [adapter] so the adapter can fulfil
 * signAuthDigest calls during the submission pipeline.
 *
 * Called immediately before the multi-signer submission so that secrets are never held
 * in the adapter longer than necessary. After submission (success or failure), call
 * [clearDelegatedKeypairs] to remove all registered material.
 *
 * @param adapter The [DemoEd25519Adapter] instance that will be assigned to the manager.
 * @param ed25519Secrets Map from signer identity to 32-byte raw seed, as collected by
 *   the signer picker's local cache.
 */
suspend fun registerEd25519Keypairs(
    adapter: DemoEd25519Adapter,
    ed25519Secrets: Map<Ed25519SignerIdentity, ByteArray>
) {
    for ((identity, seed) in ed25519Secrets) {
        adapter.add(identity, seed)
    }
}

/**
 * Transfers tokens using an explicit list of signers, registering Ed25519 secrets
 * via the in-process path before submission and clearing them afterwards.
 *
 * Iterates [ed25519Secrets] and registers each raw seed directly on [DemoState.ed25519SignerManager]
 * via addEd25519FromRawKey. No adapter is assigned. Clears all in-memory signing material
 * (delegated and Ed25519) in a finally block regardless of outcome.
 *
 * @param tokenContract The contract address (C-address) of the token to transfer.
 * @param recipient The recipient's Stellar account (G-address) or contract (C-address).
 * @param amount The amount to transfer as a decimal string.
 * @param selectedSigners All signers that must participate, in signing order.
 * @param delegatedKeyPairs Map of G-address to [com.soneso.stellar.sdk.KeyPair] for delegated signers.
 * @param ed25519Secrets Map of signer identity to 32-byte raw seed from the picker's local cache.
 * @return [TransferResult] with success/failure status, transaction hash, and optional error.
 */
suspend fun multiSignerTransferWithEd25519(
    tokenContract: String,
    recipient: String,
    amount: String,
    selectedSigners: List<SelectedSigner>,
    delegatedKeyPairs: Map<String, com.soneso.stellar.sdk.KeyPair>,
    ed25519Secrets: Map<Ed25519SignerIdentity, ByteArray>
): TransferResult {
    val ed25519Manager = DemoState.ed25519SignerManager
    for ((identity, seed) in ed25519Secrets) {
        ed25519Manager?.addEd25519FromRawKey(
            secretKeyBytes = seed,
            verifierAddress = identity.verifierAddress
        )
    }
    return try {
        registerDelegatedKeypairs(delegatedKeyPairs)
        multiSignerTransfer(
            tokenContract = tokenContract,
            recipient = recipient,
            amount = amount,
            selectedSigners = selectedSigners
        )
    } finally {
        clearDelegatedKeypairs(
            ed25519SignerManager = ed25519Manager,
            ed25519Adapter = null
        )
    }
}

/**
 * Approves a token allowance using an explicit list of signers, registering Ed25519 secrets
 * via the adapter callback path before submission and clearing them afterwards.
 *
 * Assigns [DemoState.demoEd25519Adapter] to [DemoState.ed25519SignerManager] and registers
 * all seeds in the adapter before submitting. Clears all in-memory signing material in a
 * finally block regardless of outcome.
 *
 * @param tokenContract The contract address (C-address) of the token.
 * @param spenderAddress The spender's Stellar address (G-address or C-address).
 * @param amount The allowance amount as a decimal string.
 * @param expirationLedgerOffset Number of ledgers from now until the allowance expires.
 * @param selectedSigners All signers that must participate, in signing order.
 * @param delegatedKeyPairs Map of G-address to [com.soneso.stellar.sdk.KeyPair] for delegated signers.
 * @param ed25519Secrets Map of signer identity to 32-byte raw seed from the picker's local cache.
 * @return [ApproveResult] with success/failure status, transaction hash, and optional error.
 */
suspend fun multiSignerApproveAllowanceWithEd25519(
    tokenContract: String,
    spenderAddress: String,
    amount: String,
    expirationLedgerOffset: UInt,
    selectedSigners: List<SelectedSigner>,
    delegatedKeyPairs: Map<String, com.soneso.stellar.sdk.KeyPair>,
    ed25519Secrets: Map<Ed25519SignerIdentity, ByteArray>
): ApproveResult {
    val ed25519Manager = DemoState.ed25519SignerManager
    val ed25519Adapter = DemoState.demoEd25519Adapter
    if (ed25519Secrets.isNotEmpty() && ed25519Adapter != null && ed25519Manager != null) {
        ed25519Manager.ed25519Adapter = ed25519Adapter
        registerEd25519Keypairs(ed25519Adapter, ed25519Secrets)
    }
    return try {
        registerDelegatedKeypairs(delegatedKeyPairs)
        multiSignerApproveAllowance(
            tokenContract = tokenContract,
            spenderAddress = spenderAddress,
            amount = amount,
            expirationLedgerOffset = expirationLedgerOffset,
            selectedSigners = selectedSigners
        )
    } finally {
        clearDelegatedKeypairs(
            ed25519SignerManager = ed25519Manager,
            ed25519Adapter = if (ed25519Secrets.isNotEmpty()) ed25519Adapter else null
        )
    }
}

/**
 * Clears all in-memory signing material registered for a multi-signer operation.
 *
 * Covers both paths in a single call:
 * - Wallet (delegated G-address) signers: clears [DemoState.externalSignerManager].
 * - Ed25519 adapter signers: calls [DemoEd25519Adapter.clearAll] on [ed25519Adapter] and
 *   removes the adapter reference from [ed25519SignerManager].
 *
 * Must be called in a `finally` block around the submission so it executes on both
 * success and failure. Calling when either manager is null is a no-op.
 *
 * @param ed25519SignerManager The [OZExternalSignerManager] whose [ed25519Adapter] field is cleared.
 * @param ed25519Adapter The [DemoEd25519Adapter] whose seed registry is cleared. Null-safe.
 */
suspend fun clearDelegatedKeypairs(
    ed25519SignerManager: OZExternalSignerManager?,
    ed25519Adapter: DemoEd25519Adapter?
) {
    // Clear delegated (G-address / wallet) signers
    val externalManager = DemoState.externalSignerManager as? ExternalSignerManagerAdapter
    externalManager?.removeAll()

    // Clear Ed25519 adapter seeds and detach the adapter from the manager
    ed25519Adapter?.clearAll()
    ed25519SignerManager?.ed25519Adapter = null
}
