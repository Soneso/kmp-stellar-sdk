package com.soneso.stellar.sdk

import kotlin.jvm.JvmOverloads

/**
 * Represents an account in the Stellar network with its sequence number.
 *
 * An Account object is required to build a transaction. It contains the account ID and the
 * current sequence number, which is needed to prevent transaction replay attacks.
 *
 * ## Usage
 *
 * ```kotlin
 * // Create account from account ID (public key)
 * val account = Account("GBRPYHIL2CI3FNQ4BXLFMNDLFJUNPU2HY3ZMFSHONUCEOASW7QC7OX2H", 2908908335136768L)
 *
 * // Create account from keypair
 * val keypair = KeyPair.fromSecretSeed("SCZANGBA5YHTNYVVV4C3U252E2B6P6F5T3U6MM63WBSBZATAQI3EBTQ4")
 * val account = Account(keypair, 2908908335136768L)
 * ```
 *
 * ## Sequence Numbers
 *
 * The sequence number is crucial for transaction ordering:
 * - It starts at 0 when an account is created
 * - Each transaction increments the sequence number by 1
 * - Transactions must use sequenceNumber + 1
 * - Multiple transactions can be built by incrementing the sequence number
 *
 * @property accountId The account ID (strkey encoded public key starting with 'G')
 * @property sequenceNumber The current sequence number of the account (mutable)
 *
 * @see TransactionBuilderAccount
 * @see KeyPair
 */
class Account private constructor(
    override val accountId: String,
    private var _sequenceNumber: Long,
    private val _keypair: KeyPair?
) : TransactionBuilderAccount {

    /**
     * Creates a new account from an account ID and sequence number.
     *
     * This constructor creates an account with a public-only keypair (derived from the account ID).
     * Such an account can be used to build transactions, but cannot sign them unless a keypair
     * with a private key is provided separately.
     *
     * @param accountId The account ID (strkey encoded public key starting with 'G')
     * @param sequenceNumber The current sequence number of the account
     * @throws IllegalArgumentException if the account ID is invalid
     */
    constructor(accountId: String, sequenceNumber: Long) : this(
        accountId = accountId,
        _sequenceNumber = sequenceNumber,
        _keypair = null
    ) {
        // Validate account ID
        require(StrKey.isValidEd25519PublicKey(accountId)) {
            "Invalid account ID: $accountId"
        }
    }

    /**
     * Creates a new account from a keypair and sequence number.
     *
     * This constructor creates an account that can both build and sign transactions
     * (if the keypair contains a private key).
     *
     * @param keypair The keypair for this account (may or may not contain a private key)
     * @param sequenceNumber The current sequence number of the account
     */
    constructor(keypair: KeyPair, sequenceNumber: Long) : this(
        accountId = keypair.getAccountId(),
        _sequenceNumber = sequenceNumber,
        _keypair = keypair
    )

    override val sequenceNumber: Long
        get() = _sequenceNumber

    override val keypair: KeyPair
        get() = _keypair ?: KeyPair.fromAccountId(accountId)

    override fun setSequenceNumber(seqNum: Long) {
        _sequenceNumber = seqNum
    }

    override fun getIncrementedSequenceNumber(): Long {
        return _sequenceNumber + 1
    }

    override fun incrementSequenceNumber() {
        _sequenceNumber++
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as Account

        if (accountId != other.accountId) return false
        if (_sequenceNumber != other._sequenceNumber) return false

        return true
    }

    override fun hashCode(): Int {
        var result = accountId.hashCode()
        result = 31 * result + _sequenceNumber.hashCode()
        return result
    }

    override fun toString(): String {
        return "Account(accountId='$accountId', sequenceNumber=$_sequenceNumber)"
    }
}
