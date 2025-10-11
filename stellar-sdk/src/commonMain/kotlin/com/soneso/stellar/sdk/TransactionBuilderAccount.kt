package com.soneso.stellar.sdk

/**
 * Specifies interface for Account object used in Transaction building.
 *
 * This interface defines the minimum requirements for an account to be used when building
 * Stellar transactions. Implementations must provide the account ID, a KeyPair for the account,
 * and methods to manage the sequence number.
 *
 * The sequence number is a critical component of transaction building:
 * - Every transaction must have a sequence number equal to the current sequence number + 1
 * - After a transaction is submitted and confirmed, the account's sequence number increases
 * - Multiple transactions can be built with incrementing sequence numbers
 *
 * @see Account
 */
interface TransactionBuilderAccount {
    /**
     * Returns the account ID (strkey encoded public key starting with 'G').
     *
     * @return The account ID
     */
    val accountId: String

    /**
     * Returns the keypair associated with this account.
     *
     * Note: This may be a public-only keypair (created from account ID) or a full keypair
     * with private key. To sign transactions, a keypair with private key is required.
     *
     * @return The KeyPair associated with this account
     */
    val keypair: KeyPair

    /**
     * Returns the current sequence number of this account.
     *
     * @return The current sequence number
     */
    val sequenceNumber: Long

    /**
     * Sets the current sequence number on this account.
     *
     * @param seqNum The new sequence number
     */
    fun setSequenceNumber(seqNum: Long)

    /**
     * Returns the sequence number incremented by one, but does not modify the internal counter.
     *
     * This is useful when you need to know what the next sequence number will be without
     * actually incrementing the account's sequence number.
     *
     * @return The current sequence number + 1
     */
    fun getIncrementedSequenceNumber(): Long

    /**
     * Increments the sequence number in this object by one.
     *
     * This should be called after successfully submitting a transaction to keep the
     * local account state in sync with the network state.
     */
    fun incrementSequenceNumber()
}
