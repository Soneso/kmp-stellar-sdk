package com.stellar.sdk

import com.stellar.sdk.xdr.SorobanTransactionDataXdr
import kotlin.time.Duration

/**
 * Builds a new Transaction following the builder pattern.
 *
 * TransactionBuilder provides a fluent API for constructing Stellar transactions with all
 * necessary parameters and validations.
 *
 * ## Usage
 *
 * ```kotlin
 * val sourceAccount = Account("GABC...", 1234567890)
 *
 * val transaction = TransactionBuilder(sourceAccount, Network.TESTNET)
 *     .setBaseFee(100)
 *     .addOperation(
 *         PaymentOperation(
 *             destination = "GDEF...",
 *             asset = AssetTypeNative,
 *             amount = "10.0"
 *         )
 *     )
 *     .addMemo(MemoText("Payment"))
 *     .setTimeout(300) // 5 minutes from now
 *     .build()
 *
 * transaction.sign(keypair)
 * ```
 *
 * ## Fee Calculation
 *
 * The transaction fee is calculated as: `baseFee * operations.size`
 *
 * You must set the base fee explicitly using [setBaseFee] before building.
 *
 * ## Sequence Number Management
 *
 * The builder automatically increments the source account's sequence number when [build] is called.
 * This ensures the transaction has the correct sequence number (account.sequence + 1).
 *
 * @property sourceAccount The source account for this transaction
 * @property network The network this transaction is for (TESTNET, PUBLIC, etc.)
 *
 * @see Transaction
 * @see <a href="https://developers.stellar.org/docs/learn/fundamentals/transactions">Transactions</a>
 */
class TransactionBuilder(
    val sourceAccount: TransactionBuilderAccount,
    val network: Network
) {
    private var baseFee: Long? = null
    private var memo: Memo? = null
    private val operations: MutableList<Operation> = mutableListOf()
    private var preconditions: TransactionPreconditions = TransactionPreconditions()
    private var sorobanData: SorobanTransactionDataXdr? = null
    private var txTimeout: Long? = null

    /**
     * Returns the number of operations currently added to this transaction.
     *
     * @return The operations count
     */
    fun getOperationsCount(): Int = operations.size

    /**
     * Sets the base fee per operation for this transaction.
     *
     * The total transaction fee will be: baseFee * operations.size
     *
     * The minimum base fee is 100 stroops (0.00001 XLM).
     *
     * @param baseFee The base fee in stroops
     * @return This builder for method chaining
     * @throws IllegalArgumentException if baseFee is less than MIN_BASE_FEE
     */
    fun setBaseFee(baseFee: Long): TransactionBuilder {
        require(baseFee >= AbstractTransaction.MIN_BASE_FEE) {
            "baseFee cannot be smaller than the BASE_FEE (${AbstractTransaction.MIN_BASE_FEE}): $baseFee"
        }
        this.baseFee = baseFee
        return this
    }

    /**
     * Adds an operation to this transaction.
     *
     * Transactions must have between 1 and 100 operations.
     *
     * @param operation The operation to add
     * @return This builder for method chaining
     * @see <a href="https://developers.stellar.org/docs/learn/fundamentals/transactions/list-of-operations">List of Operations</a>
     */
    fun addOperation(operation: Operation): TransactionBuilder {
        operations.add(operation)
        return this
    }

    /**
     * Adds multiple operations to this transaction.
     *
     * @param operations The operations to add
     * @return This builder for method chaining
     */
    fun addOperations(operations: List<Operation>): TransactionBuilder {
        this.operations.addAll(operations)
        return this
    }

    /**
     * Adds a memo to this transaction.
     *
     * Only one memo can be added per transaction. Calling this method multiple times
     * will throw an exception.
     *
     * @param memo The memo to add
     * @return This builder for method chaining
     * @throws IllegalStateException if a memo has already been added
     * @see Memo
     */
    fun addMemo(memo: Memo): TransactionBuilder {
        check(this.memo == null) { "Memo has been already added." }
        this.memo = memo
        return this
    }

    /**
     * Adds preconditions to this transaction.
     *
     * Preconditions include time bounds, ledger bounds, minimum sequence numbers, and extra signers.
     *
     * @param preconditions The transaction preconditions
     * @return This builder for method chaining
     * @see TransactionPreconditions
     * @see <a href="https://developers.stellar.org/docs/learn/fundamentals/transactions/operations-and-transactions#preconditions">Preconditions</a>
     */
    fun addPreconditions(preconditions: TransactionPreconditions): TransactionBuilder {
        this.preconditions = preconditions
        return this
    }

    /**
     * Sets the transaction timeout in seconds from now.
     *
     * This is a convenience method that sets the maximum time bound to current time + timeout.
     * If you want infinite timeout, use TIMEOUT_INFINITE (0).
     *
     * You cannot use both setTimeout and explicit time bounds in preconditions.
     *
     * @param timeout The timeout in seconds (0 for infinite)
     * @return This builder for method chaining
     * @throws IllegalArgumentException if timeout is negative
     */
    fun setTimeout(timeout: Long): TransactionBuilder {
        require(timeout >= 0) { "timeout cannot be negative" }
        this.txTimeout = timeout
        return this
    }

    /**
     * Sets the transaction timeout using Kotlin Duration from now.
     *
     * This is a convenience method that sets the maximum time bound to current time + timeout.
     *
     * @param timeout The timeout duration
     * @return This builder for method chaining
     */
    fun setTimeout(timeout: Duration): TransactionBuilder {
        return setTimeout(timeout.inWholeSeconds)
    }

    /**
     * Adds time bounds to this transaction.
     *
     * This is a convenience method for setting time bounds without building full preconditions.
     *
     * @param timeBounds The time bounds
     * @return This builder for method chaining
     */
    fun addTimeBounds(timeBounds: TimeBounds): TransactionBuilder {
        this.preconditions = TransactionPreconditions(
            timeBounds = timeBounds,
            ledgerBounds = preconditions.ledgerBounds,
            minSequenceNumber = preconditions.minSequenceNumber,
            minSequenceAge = preconditions.minSequenceAge,
            minSequenceLedgerGap = preconditions.minSequenceLedgerGap,
            extraSigners = preconditions.extraSigners
        )
        return this
    }

    /**
     * Sets Soroban transaction data for smart contract operations.
     *
     * This data includes resource limits and footprint information obtained from
     * transaction simulation.
     *
     * @param sorobanData The Soroban transaction data
     * @return This builder for method chaining
     */
    fun setSorobanData(sorobanData: SorobanTransactionDataXdr): TransactionBuilder {
        this.sorobanData = sorobanData
        return this
    }

    /**
     * Builds the transaction and increments the source account's sequence number.
     *
     * This method performs validation and constructs the final Transaction object.
     * The source account's sequence number is incremented to ensure the next transaction
     * uses a higher sequence number.
     *
     * @return The built Transaction
     * @throws IllegalStateException if required fields are missing or validation fails
     */
    fun build(): Transaction {
        // Validate base fee is set
        val fee = baseFee ?: throw IllegalStateException(
            "baseFee has to be set. you must call setBaseFee()."
        )

        // Validate at least one operation
        require(operations.isNotEmpty()) {
            "At least one operation required"
        }

        // Calculate final preconditions
        val finalPreconditions = if (txTimeout != null) {
            // Cannot have both txTimeout and explicit time bounds
            check(preconditions.timeBounds == null) {
                "Can not set both TransactionPreconditions.timeBounds and timeout."
            }

            // Calculate max time
            val maxTime = if (txTimeout == TransactionPreconditions.TIMEOUT_INFINITE) {
                0L // Infinite timeout
            } else {
                val currentTimeSeconds = currentTimeMillis() / 1000L
                currentTimeSeconds + txTimeout!!
            }

            TransactionPreconditions(
                timeBounds = TimeBounds(minTime = 0L, maxTime = maxTime),
                ledgerBounds = preconditions.ledgerBounds,
                minSequenceNumber = preconditions.minSequenceNumber,
                minSequenceAge = preconditions.minSequenceAge,
                minSequenceLedgerGap = preconditions.minSequenceLedgerGap,
                extraSigners = preconditions.extraSigners
            )
        } else {
            preconditions
        }

        // Validate preconditions
        finalPreconditions.validate()

        // Get next sequence number from account
        val sequenceNumber = sourceAccount.getIncrementedSequenceNumber()

        // Calculate total fee (base fee * ops + soroban resource fee)
        val totalFee = fee * operations.size + (sorobanData?.resourceFee?.value ?: 0L)

        // Build transaction
        val transaction = Transaction(
            sourceAccount = sourceAccount.accountId,
            fee = totalFee,
            sequenceNumber = sequenceNumber,
            operations = operations.toList(),
            memo = memo ?: MemoNone,
            preconditions = finalPreconditions,
            sorobanData = sorobanData,
            network = network
        )

        // Update source account sequence number
        sourceAccount.setSequenceNumber(sequenceNumber)

        return transaction
    }

    companion object {
        /**
         * Gets the current time in milliseconds.
         *
         * This is abstracted to allow for testing and platform-specific implementations.
         *
         * @return Current time in milliseconds since epoch
         */
        internal fun currentTimeMillis(): Long {
            return kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
        }
    }
}
