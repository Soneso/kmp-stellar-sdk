package com.soneso.smartdemo.flows

/**
 * Business logic for token transfers from a smart account.
 *
 * Demonstrates how to transfer tokens using [OZSmartAccountKit.transactionOperations.transfer]:
 * - XLM via the Stellar Asset Contract (SAC): pass the SAC address as [tokenContract].
 * - Custom Soroban tokens (e.g., DEMO): pass the token contract address.
 *
 * All transfers require passkey authentication — the SDK triggers a WebAuthn ceremony
 * to sign the Soroban authorization entry before submitting the transaction.
 *
 * After a successful transfer, XLM and DEMO balances are refreshed in [DemoState].
 */

import com.soneso.smartdemo.state.ActivityLogState
import com.soneso.smartdemo.state.DemoState
import com.soneso.smartdemo.util.isUserCancellation
import com.soneso.smartdemo.util.refreshAllBalances
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
 * 2. Call [OZSmartAccountKit.transactionOperations.transfer] with the token contract,
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
 * 2. Call [OZSmartAccountKit.multiSignerManager.multiSignerTransfer] with the token contract,
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
