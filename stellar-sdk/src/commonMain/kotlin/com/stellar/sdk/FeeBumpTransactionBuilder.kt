package com.stellar.sdk

/**
 * Builder for constructing [FeeBumpTransaction] instances.
 *
 * This builder provides a fluent API for creating fee bump transactions that wrap existing
 * transactions with higher fees. Fee bumps are useful when network congestion causes
 * transactions to get stuck in the queue.
 *
 * ## Usage with Base Fee (Recommended)
 *
 * ```kotlin
 * val feeBump = FeeBumpTransactionBuilder(originalTransaction)
 *     .setFeeSource("GFEE...")
 *     .setBaseFee(1000)  // Fee per operation
 *     .build()
 *
 * feeBump.sign(feeSourceKeypair)
 * ```
 *
 * ## Usage with Exact Fee
 *
 * ```kotlin
 * val feeBump = FeeBumpTransactionBuilder(originalTransaction)
 *     .setFeeSource("GFEE...")
 *     .setFee(10000)  // Total fee
 *     .build()
 * ```
 *
 * ## Important Notes
 *
 * - You must specify EITHER [setBaseFee] OR [setFee], not both
 * - The fee source account must sign the fee bump transaction
 * - The inner transaction must already be signed by its source account
 * - Fee bumps can only wrap regular transactions, not other fee bumps
 *
 * @property innerTransaction The transaction to wrap with a higher fee
 *
 * @see FeeBumpTransaction
 * @see <a href="https://developers.stellar.org/docs/encyclopedia/fee-bump-transactions">Fee-bump Transactions</a>
 */
class FeeBumpTransactionBuilder(
    private val innerTransaction: Transaction
) {
    private var feeSource: String? = null
    private var baseFee: Long? = null
    private var fee: Long? = null

    /**
     * Sets the account that will pay for the increased fee.
     *
     * This account must have sufficient balance to cover the fee increase. The fee source
     * can be the same as the inner transaction's source account, or a different account
     * that is sponsoring the fee increase.
     *
     * @param feeSource The account ID (G... or M... address)
     * @return This builder for method chaining
     */
    fun setFeeSource(feeSource: String): FeeBumpTransactionBuilder {
        this.feeSource = feeSource
        return this
    }

    /**
     * Sets the base fee per operation.
     *
     * The total fee will be calculated as:
     * ```
     * maxFee = (baseFee × (numInnerOperations + 1)) + sorobanResourceFee
     * ```
     *
     * The "+1" accounts for the fee bump operation itself.
     *
     * ## Validation
     *
     * - Must be at least [AbstractTransaction.MIN_BASE_FEE] (100 stroops)
     * - Must be higher than or equal to the inner transaction's base fee
     * - Cannot be set if [setFee] was already called
     *
     * @param baseFee The base fee per operation in stroops
     * @return This builder for method chaining
     * @throws IllegalStateException if [setFee] was already called
     */
    fun setBaseFee(baseFee: Long): FeeBumpTransactionBuilder {
        check(fee == null) {
            "Cannot set baseFee when fee is already set. Use either setBaseFee() or setFee(), not both."
        }
        this.baseFee = baseFee
        return this
    }

    /**
     * Sets the exact total fee for the transaction.
     *
     * This method allows precise control over the fee. Generally, it's better to use
     * [setBaseFee] which automatically calculates the fee based on the number of operations.
     *
     * ## Validation
     *
     * - Must be greater than or equal to the inner transaction's fee
     * - Cannot be set if [setBaseFee] was already called
     *
     * @param fee The total fee in stroops
     * @return This builder for method chaining
     * @throws IllegalStateException if [setBaseFee] was already called
     */
    fun setFee(fee: Long): FeeBumpTransactionBuilder {
        check(baseFee == null) {
            "Cannot set fee when baseFee is already set. Use either setBaseFee() or setFee(), not both."
        }
        this.fee = fee
        return this
    }

    /**
     * Builds the FeeBumpTransaction.
     *
     * ## Validation
     *
     * This method validates that:
     * - A fee source was set
     * - Either base fee or fee was set (but not both)
     * - All fee requirements are met
     *
     * @return A new FeeBumpTransaction
     * @throws IllegalStateException if required fields are missing
     * @throws IllegalArgumentException if fee validation fails
     */
    fun build(): FeeBumpTransaction {
        val source = feeSource
            ?: throw IllegalStateException("Fee source account must be set")

        return when {
            baseFee != null && fee == null -> {
                FeeBumpTransaction.createWithBaseFee(source, baseFee!!, innerTransaction)
            }
            fee != null && baseFee == null -> {
                FeeBumpTransaction.createWithFee(source, fee!!, innerTransaction)
            }
            baseFee != null && fee != null -> {
                throw IllegalStateException(
                    "Cannot set both baseFee and fee. Use either setBaseFee() or setFee(), not both."
                )
            }
            else -> {
                throw IllegalStateException(
                    "Must set either baseFee or fee using setBaseFee() or setFee()"
                )
            }
        }
    }
}
