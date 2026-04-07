package com.soneso.smartdemo.flows

/**
 * Business logic for token allowance approval from a smart account.
 *
 * Calls the token contract's approve() function directly (not through the smart
 * account's execute() entry point). This allows contract-specific context rules
 * (CallContract) to authorize the operation, enabling multi-signer scenarios
 * without requiring the Default rule's signer.
 *
 * Provides single-signer and multi-signer paths for the SEP-41 approve() function,
 * plus a read-only allowance query. All functions operate on the connected smart
 * account stored in [DemoState].
 */

import com.soneso.smartdemo.config.DemoConfig
import com.soneso.smartdemo.state.ActivityLogState
import com.soneso.smartdemo.state.DemoState
import com.soneso.smartdemo.token.DemoTokenService
import com.soneso.smartdemo.util.parseDemoBalance
import com.soneso.stellar.sdk.Address
import com.soneso.stellar.sdk.Network
import com.soneso.stellar.sdk.Util
import com.soneso.stellar.sdk.contract.ContractClient
import com.soneso.stellar.sdk.scval.Scv
import com.soneso.stellar.sdk.smartaccount.oz.SelectedSigner
import com.soneso.stellar.sdk.xdr.SCValXdr

/**
 * Result of a token approve operation.
 *
 * @property success True if the approval was submitted and confirmed on-chain.
 * @property hash The Stellar transaction hash, or null if the operation failed.
 * @property error Error message if [success] is false; null on success.
 */
data class ApproveResult(
    val success: Boolean,
    val hash: String?,
    val error: String?
)

/**
 * Builds the approve() arguments as pre-encoded SCVal list.
 */
private fun buildApproveArgs(
    smartAccountAddress: String,
    spenderAddress: String,
    amount: String,
    absoluteLedger: UInt
): List<SCValXdr> {
    val amountStroops = Util.amountToStroops(amount)
    return listOf(
        Scv.toAddress(Address(smartAccountAddress).toSCAddress()),  // from
        Scv.toAddress(Address(spenderAddress).toSCAddress()),      // spender
        Util.stroopsToI128ScVal(amountStroops),                    // amount (i128)
        Scv.toUint32(absoluteLedger)                               // expiration_ledger (u32)
    )
}

/**
 * Approves a token allowance from the connected smart account to a spender.
 *
 * Uses [OZTransactionOperations.contractCall] to call the token contract's approve()
 * function directly. The smart account's context rules for CallContract(tokenContract)
 * authorize the operation.
 *
 * @param tokenContract The contract address (C-address) of the token.
 * @param spenderAddress The spender's Stellar address (G-address or C-address).
 * @param amount The allowance amount as a decimal string (e.g. "100" or "50.5").
 * @param expirationLedgerOffset Number of ledgers from now until the allowance expires.
 * @return [ApproveResult] with success/failure status, transaction hash, and optional error.
 * @throws IllegalStateException if the kit or contract ID is not initialized.
 */
suspend fun approveAllowance(
    tokenContract: String,
    spenderAddress: String,
    amount: String,
    expirationLedgerOffset: UInt
): ApproveResult {
    val kit = DemoState.kit
        ?: throw IllegalStateException("Smart Account Kit not initialized")

    val smartAccountAddress = DemoState.contractId
        ?: throw IllegalStateException("Smart account contract ID not set")

    val absoluteLedger = resolveAbsoluteLedger(expirationLedgerOffset)
    val args = buildApproveArgs(smartAccountAddress, spenderAddress, amount, absoluteLedger)

    val result = kit.transactionOperations.contractCall(
        target = tokenContract,
        targetFn = "approve",
        targetArgs = args
    )

    if (result.success) {
        ActivityLogState.success("Approve successful! Hash: ${result.hash ?: "unknown"}")
    } else {
        ActivityLogState.error("Approve failed: ${result.error ?: "unknown error"}")
    }

    return ApproveResult(
        success = result.success,
        hash = result.hash,
        error = result.error
    )
}

/**
 * Approves a token allowance using an explicit list of signers.
 *
 * Uses [OZMultiSignerManager.multiSignerContractCall] to call the token contract's
 * approve() function directly. This allows CallContract(tokenContract) rules with
 * multi-signer policies (e.g. threshold 2-of-3) to authorize the operation.
 *
 * The caller must register any delegated signer keypairs via [registerDelegatedKeypairs]
 * before calling this function.
 *
 * @param tokenContract The contract address (C-address) of the token.
 * @param spenderAddress The spender's Stellar address (G-address or C-address).
 * @param amount The allowance amount as a decimal string (e.g. "100" or "50.5").
 * @param expirationLedgerOffset Number of ledgers from now until the allowance expires.
 * @param selectedSigners All signers that must participate, in signing order.
 * @return [ApproveResult] with success/failure status, transaction hash, and optional error.
 * @throws IllegalStateException if the kit or contract ID is not initialized.
 */
suspend fun multiSignerApproveAllowance(
    tokenContract: String,
    spenderAddress: String,
    amount: String,
    expirationLedgerOffset: UInt,
    selectedSigners: List<SelectedSigner>
): ApproveResult {
    val kit = DemoState.kit
        ?: throw IllegalStateException("Smart Account Kit not initialized")

    val smartAccountAddress = DemoState.contractId
        ?: throw IllegalStateException("Smart account contract ID not set")

    val absoluteLedger = resolveAbsoluteLedger(expirationLedgerOffset)
    val args = buildApproveArgs(smartAccountAddress, spenderAddress, amount, absoluteLedger)

    val result = kit.multiSignerManager.multiSignerContractCall(
        target = tokenContract,
        targetFn = "approve",
        targetArgs = args,
        selectedSigners = selectedSigners
    )

    if (result.success) {
        ActivityLogState.success("Multi-signer approve successful! Hash: ${result.hash ?: "unknown"}")
    } else {
        ActivityLogState.error("Multi-signer approve failed: ${result.error ?: "unknown error"}")
    }

    return ApproveResult(
        success = result.success,
        hash = result.hash,
        error = result.error
    )
}

/**
 * Fetches the current token allowance granted by the smart account to a spender.
 *
 * This is a read-only simulation call — no transaction is submitted and no signing
 * is required. Uses the token admin account as the simulation source.
 *
 * Waits 5 seconds before querying to allow ledger state to propagate after
 * an approve transaction is confirmed.
 *
 * @param tokenContract The contract address (C-address) of the token.
 * @param spenderAddress The spender's Stellar address (G-address or C-address).
 * @return Formatted allowance string (e.g. "100.0"), or null on any error.
 */
suspend fun fetchAllowance(
    tokenContract: String,
    spenderAddress: String
): String? {
    val smartAccountAddress = DemoState.contractId ?: return null

    // Wait for ledger state to propagate after the approve transaction.
    kotlinx.coroutines.delay(5000)

    val client = try {
        ContractClient.forContract(
            contractId = tokenContract,
            rpcUrl = DemoConfig.RPC_URL,
            network = Network(DemoConfig.NETWORK_PASSPHRASE)
        )
    } catch (_: Exception) {
        return null
    }

    return try {
        val sourceAccountId = DemoTokenService.getAdminAccountId()
        val result = client.invoke<SCValXdr>(
            functionName = "allowance",
            arguments = mapOf(
                "from" to smartAccountAddress,
                "spender" to spenderAddress
            ),
            source = sourceAccountId,
            signer = null
        )
        parseDemoBalance(result)
    } catch (_: Exception) {
        null
    } finally {
        client.close()
    }
}
