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
import com.soneso.smartdemo.util.SignerInfo
import com.soneso.smartdemo.util.extractSignersFromRules
import com.soneso.smartdemo.util.fetchAllContextRules
import com.soneso.smartdemo.util.refreshAllBalances
import com.soneso.stellar.sdk.smartaccount.oz.OZSmartAccountKit
import com.soneso.stellar.sdk.smartaccount.oz.SelectedSigner
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

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
 * Guards [registeredDelegatedAddresses] against concurrent registration and cleanup.
 */
private val delegatedRegistrationMutex = Mutex()

/**
 * G-addresses the demo has registered as in-memory delegated keypairs on the kit-owned
 * manager. Tracked so cleanup removes exactly these entries without disturbing wallet
 * connections or Ed25519 registrations.
 */
private val registeredDelegatedAddresses = mutableSetOf<String>()

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
 *   The SDK converts this to the token's base units.
 * @param decimals The token's decimal scale. Pass it when known (both demo tokens use 7)
 *   to skip the SDK's on-chain decimals() lookup; null lets the SDK fetch it.
 * @return [TransferResult] with success/failure status, transaction hash, and optional error.
 * @throws Exception if the kit is not initialized, amount is invalid, or the passkey
 *   ceremony is cancelled. Check [isUserCancellation] to distinguish user cancellations.
 */
suspend fun transfer(
    tokenContract: String,
    recipient: String,
    amount: String,
    decimals: Int? = null
): TransferResult {
    val kit = DemoState.kit
        ?: throw IllegalStateException("Smart Account Kit not initialized")

    // Call transactionOperations.transfer — this triggers the WebAuthn ceremony on the device
    // to sign the Soroban authorization entry, then submits the transaction to the network.
    val result = kit.transactionOperations.transfer(
        tokenContract = tokenContract,
        recipient = recipient,
        amount = amount,
        decimals = decimals
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
 *    c. For each [SelectedSigner.Wallet]: signs via [kit.externalSigners].
 *    d. Submits the transaction to the network (via the relayer if configured).
 * 3. On success, refresh XLM and DEMO balances in [DemoState].
 *
 * The caller is responsible for registering any delegated signer keypairs via
 * [registerDelegatedKeypairs] before calling this function.
 *
 * @param tokenContract The contract address (C-address) of the token to transfer.
 * @param recipient The recipient's Stellar account (G-address) or contract (C-address).
 * @param amount The amount to transfer as a decimal string (e.g. "10" or "10.5").
 * @param decimals The token's decimal scale. Pass it when known (both demo tokens use 7)
 *   to skip the SDK's on-chain decimals() lookup; null lets the SDK fetch it.
 * @param selectedSigners All signers that must participate, in signing order.
 * @return [TransferResult] with success/failure status, transaction hash, and optional error.
 */
suspend fun multiSignerTransfer(
    tokenContract: String,
    recipient: String,
    amount: String,
    decimals: Int? = null,
    selectedSigners: List<SelectedSigner>
): TransferResult {
    val kit = DemoState.kit
        ?: throw IllegalStateException("Smart Account Kit not initialized")

    // multiSignerTransfer collects signatures from all listed signers in order,
    // then submits the transaction. Passkey signers trigger WebAuthn prompts;
    // wallet signers sign via kit.externalSigners (in-memory keypair or wallet adapter).
    val result = kit.multiSignerManager.multiSignerTransfer(
        tokenContract = tokenContract,
        recipient = recipient,
        amount = amount,
        decimals = decimals,
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
            externalSigners = kit.externalSigners
        )
    } catch (_: Exception) {
        emptyList()
    }
}

/**
 * Registers delegated signer keypairs as in-memory keypairs on [kit.externalSigners] so that
 * [multiSignerTransfer] can sign authorization entries for [SelectedSigner.Wallet] signers.
 *
 * Each keypair is added via OZExternalSignerManager.addFromSecret so the manager resolves it
 * through its in-memory keypair custody model. Any demo delegated keypairs registered by an
 * earlier operation are removed first to prevent stale keys from accumulating across operations.
 * The addresses are tracked so [clearDelegatedKeypairs] removes exactly these entries.
 *
 * @param delegatedKeyPairs Map of G-address to [com.soneso.stellar.sdk.KeyPair].
 */
suspend fun registerDelegatedKeypairs(
    delegatedKeyPairs: Map<String, com.soneso.stellar.sdk.KeyPair>
) {
    val kit = DemoState.kit ?: return
    delegatedRegistrationMutex.withLock {
        for (address in registeredDelegatedAddresses) {
            kit.externalSigners.remove(address)
        }
        registeredDelegatedAddresses.clear()
        for ((_, keyPair) in delegatedKeyPairs) {
            val secretSeed = keyPair.getSecretSeed() ?: continue
            val address = kit.externalSigners.addFromSecret(secretSeed.concatToString())
            registeredDelegatedAddresses.add(address)
        }
    }
}

/**
 * Runs [block] with in-process multi-signer material registered on the kit-owned manager.
 *
 * Registers each Ed25519 seed via OZExternalSignerManager.addEd25519FromRawKey and each delegated
 * G-address keypair via [registerDelegatedKeypairs], runs [block], then clears all registered
 * material in a finally so nothing leaks on success, failure, or cancellation. Registration runs
 * inside the try so partial registration interrupted by an exception or cancellation is still
 * cleaned up.
 *
 * Use this for the in-process custody path: the demo's adapter ([DemoState.demoEd25519Adapter])
 * holds no seed for these keys, so the manager resolves them through its in-memory registry rather
 * than the adapter.
 *
 * @param delegatedKeyPairs Map of G-address to [com.soneso.stellar.sdk.KeyPair] for delegated signers.
 * @param ed25519Secrets Map of signer identity to 32-byte raw seed from the picker's local cache.
 * @param block The operation to run while the signing material is registered.
 * @return The value returned by [block].
 */
suspend fun <T> withInProcessMultiSigner(
    delegatedKeyPairs: Map<String, com.soneso.stellar.sdk.KeyPair>,
    ed25519Secrets: Map<Ed25519SignerIdentity, ByteArray>,
    block: suspend () -> T
): T {
    val kit = DemoState.kit
    return try {
        if (kit != null) {
            for ((identity, seed) in ed25519Secrets) {
                kit.externalSigners.addEd25519FromRawKey(
                    secretKeyBytes = seed,
                    verifierAddress = identity.verifierAddress
                )
            }
        }
        registerDelegatedKeypairs(delegatedKeyPairs)
        block()
    } finally {
        clearInProcessEd25519Keys(ed25519Secrets.keys)
        clearDelegatedKeypairs()
    }
}

/**
 * Transfers tokens using an explicit list of signers, demonstrating the in-process Ed25519
 * custody path.
 *
 * Wraps the transfer in [withInProcessMultiSigner], which registers each Ed25519 seed directly on
 * [kit.externalSigners] via addEd25519FromRawKey and each delegated keypair, then clears all
 * in-memory signing material in a finally regardless of outcome. Because the demo's adapter
 * ([DemoState.demoEd25519Adapter]) holds no seed for these keys, the manager resolves them through
 * its in-memory registry rather than the adapter.
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
    decimals: Int? = null,
    selectedSigners: List<SelectedSigner>,
    delegatedKeyPairs: Map<String, com.soneso.stellar.sdk.KeyPair>,
    ed25519Secrets: Map<Ed25519SignerIdentity, ByteArray>
): TransferResult = withInProcessMultiSigner(delegatedKeyPairs, ed25519Secrets) {
    multiSignerTransfer(
        tokenContract = tokenContract,
        recipient = recipient,
        amount = amount,
        decimals = decimals,
        selectedSigners = selectedSigners
    )
}

/**
 * Approves a token allowance using an explicit list of signers, demonstrating the Ed25519
 * adapter custody path.
 *
 * Registers all seeds on [DemoState.demoEd25519Adapter] before submitting. The adapter is injected
 * into the kit via OZSmartAccountConfig.externalEd25519Adapter and consulted by [kit.externalSigners]
 * ahead of its in-memory registry, so these keys resolve through the adapter. Clears all in-memory
 * signing material in a finally block regardless of outcome, so partial registration interrupted by
 * an exception or cancellation is still cleaned up.
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
    val adapter = DemoState.demoEd25519Adapter
    return try {
        if (ed25519Secrets.isNotEmpty() && adapter != null) {
            for ((identity, seed) in ed25519Secrets) {
                adapter.add(identity, seed)
            }
        }
        registerDelegatedKeypairs(delegatedKeyPairs)
        multiSignerApproveAllowance(
            tokenContract = tokenContract,
            spenderAddress = spenderAddress,
            amount = amount,
            expirationLedgerOffset = expirationLedgerOffset,
            selectedSigners = selectedSigners
        )
    } finally {
        adapter?.clearAll()
        clearDelegatedKeypairs()
    }
}

/**
 * Removes all in-memory delegated keypairs the demo registered on [kit.externalSigners].
 *
 * Removes exactly the G-addresses tracked by [registerDelegatedKeypairs], leaving wallet
 * connections and Ed25519 registrations untouched. Must be called in a `finally` block around
 * the submission so it executes on both success and failure. A no-op when no kit is initialized
 * or no delegated keypairs are tracked.
 */
suspend fun clearDelegatedKeypairs() {
    val kit = DemoState.kit ?: return
    delegatedRegistrationMutex.withLock {
        for (address in registeredDelegatedAddresses) {
            kit.externalSigners.remove(address)
        }
        registeredDelegatedAddresses.clear()
    }
}

/**
 * Removes the in-process Ed25519 keys registered for the current operation from
 * [kit.externalSigners].
 *
 * @param identities The signer identities whose in-memory keys should be removed.
 */
private suspend fun clearInProcessEd25519Keys(identities: Set<Ed25519SignerIdentity>) {
    val kit = DemoState.kit ?: return
    for (identity in identities) {
        kit.externalSigners.removeEd25519(identity.verifierAddress, identity.publicKey)
    }
}
