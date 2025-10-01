package com.stellar.sdk

import com.stellar.sdk.xdr.*
import kotlin.jvm.JvmStatic

/**
 * Represents a multiplexed (muxed) account on Stellar's network.
 *
 * A muxed account is an extension of the regular account that allows multiple entities to share
 * the same ed25519 key pair as their account ID while providing a unique identifier for each
 * entity.
 *
 * A muxed account consists of two parts:
 * - The ed25519 account ID, which starts with the letter "G"
 * - An optional account multiplexing ID, which is a 64-bit unsigned integer
 *
 * When the multiplexing ID is set, the address starts with "M" instead of "G".
 *
 * ## Usage
 *
 * ```kotlin
 * // Create from regular account ID
 * val account = MuxedAccount("GBRPYHIL2CI3FNQ4BXLFMNDLFJUNPU2HY3ZMFSHONUCEOASW7QC7OX2H")
 *
 * // Create from muxed account address
 * val muxedAccount = MuxedAccount("MAAAAAAAAAAAAAB7BQ2L7E5NBWMXDUCMZSIPOBKRDSBYVLMXGSSKF6YNPIB7Y77ITLVL6")
 * println(muxedAccount.accountId)  // Returns underlying G... address
 * println(muxedAccount.id)         // Returns the muxed ID
 *
 * // Create from account ID and muxed ID
 * val muxed = MuxedAccount("GBRPYHIL2CI3FNQ4BXLFMNDLFJUNPU2HY3ZMFSHONUCEOASW7QC7OX2H", 123456789UL)
 * println(muxed.address)           // Returns M... address
 * ```
 *
 * @property accountId The ed25519 account ID (always starts with 'G')
 * @property id The optional account multiplexing ID (null for regular accounts)
 *
 * @see <a href="https://developers.stellar.org/docs/learn/encyclopedia/transactions-specialized/pooled-accounts-muxed-accounts-memos#muxed-accounts">Muxed Accounts</a>
 */
class MuxedAccount {
    /**
     * The ed25519 account ID. It starts with the letter "G".
     */
    val accountId: String

    /**
     * Alias for accountId. Returns the underlying ed25519 account ID (always starts with 'G').
     * This exists for compatibility with different naming conventions.
     */
    val ed25519AccountId: String
        get() = accountId

    /**
     * The optional account multiplexing ID. It is a 64-bit unsigned integer.
     * Null if this is a regular (non-muxed) account.
     */
    val id: ULong?

    /**
     * Creates a new muxed account from the given ed25519 account ID and optional multiplexing ID.
     *
     * @param accountId The ed25519 account ID. It must be a valid account ID starting with "G"
     * @param id The optional account multiplexing ID. It can be null if not set
     * @throws IllegalArgumentException if the provided account ID is invalid
     */
    constructor(accountId: String, id: ULong? = null) {
        require(StrKey.isValidEd25519PublicKey(accountId)) {
            "Invalid account ID: $accountId"
        }
        this.accountId = accountId
        this.id = id
    }

    /**
     * Creates a new muxed account from the given muxed account address.
     *
     * The address can be either:
     * - A regular account ID (starting with "G")
     * - A muxed account address (starting with "M")
     *
     * @param address The muxed account address
     * @throws IllegalArgumentException if the provided address is invalid
     */
    constructor(address: String) {
        when {
            StrKey.isValidEd25519PublicKey(address) -> {
                this.accountId = address
                this.id = null
            }
            StrKey.isValidMed25519PublicKey(address) -> {
                // Decode M... address to get account ID and muxed ID
                val rawMed25519 = StrKey.decodeMed25519PublicKey(address)

                // StrKey format is: 8 bytes (id) + 32 bytes (ed25519 public key)
                require(rawMed25519.size == 40) {
                    "Invalid muxed account address: expected 40 bytes, got ${rawMed25519.size}"
                }

                // Extract ID (first 8 bytes, big-endian)
                val idBytes = rawMed25519.copyOfRange(0, 8)
                val idValue = idBytes.fold(0UL) { acc, byte ->
                    (acc shl 8) or (byte.toUByte().toULong())
                }

                // Extract ed25519 public key (last 32 bytes)
                val ed25519Bytes = rawMed25519.copyOfRange(8, 40)

                this.accountId = StrKey.encodeEd25519PublicKey(ed25519Bytes)
                this.id = idValue
            }
            else -> {
                throw IllegalArgumentException("Invalid address: $address")
            }
        }
    }

    /**
     * Returns the account address representation of this muxed account.
     *
     * @return The account address. It starts with "M" if the multiplexing ID is set,
     *         or with "G" if the multiplexing ID is not set
     */
    val address: String
        get() {
            val muxedId = id ?: return accountId

            // Encode as M... address
            val ed25519Bytes = StrKey.decodeEd25519PublicKey(accountId)

            // Build raw muxed format for StrKey: 8 bytes (id) + 32 bytes (ed25519)
            val rawMed25519 = ByteArray(40)

            // Write ID as big-endian 8 bytes (first 8 bytes)
            var currentId = muxedId
            for (i in 7 downTo 0) {
                rawMed25519[i] = (currentId and 0xFFUL).toByte()
                currentId = currentId shr 8
            }

            // Write ed25519 public key (last 32 bytes)
            ed25519Bytes.copyInto(rawMed25519, destinationOffset = 8)

            return StrKey.encodeMed25519PublicKey(rawMed25519)
        }

    /**
     * Converts this muxed account to its XDR representation.
     *
     * @return The XDR MuxedAccount object
     */
    fun toXdr(): MuxedAccountXdr {
        val muxedId = id
        return if (muxedId == null) {
            // Regular account (KEY_TYPE_ED25519)
            val ed25519Bytes = StrKey.decodeEd25519PublicKey(accountId)
            MuxedAccountXdr.Ed25519(Uint256Xdr(ed25519Bytes))
        } else {
            // Muxed account (KEY_TYPE_MUXED_ED25519)
            val ed25519Bytes = StrKey.decodeEd25519PublicKey(accountId)
            val med25519 = MuxedAccountMed25519Xdr(
                id = Uint64Xdr(muxedId),
                ed25519 = Uint256Xdr(ed25519Bytes)
            )
            MuxedAccountXdr.Med25519(med25519)
        }
    }

    companion object {
        /**
         * Creates a new muxed account from the given XDR representation.
         *
         * @param xdr The XDR representation of the muxed account
         * @return A new MuxedAccount instance
         * @throws IllegalArgumentException if the provided XDR is invalid
         */
        @JvmStatic
        fun fromXdr(xdr: MuxedAccountXdr): MuxedAccount {
            return when (xdr) {
                is MuxedAccountXdr.Ed25519 -> {
                    val accountId = StrKey.encodeEd25519PublicKey(xdr.value.value)
                    MuxedAccount(accountId, null)
                }
                is MuxedAccountXdr.Med25519 -> {
                    val accountId = StrKey.encodeEd25519PublicKey(xdr.value.ed25519.value)
                    val id = xdr.value.id.value
                    MuxedAccount(accountId, id)
                }
            }
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as MuxedAccount

        if (accountId != other.accountId) return false
        if (id != other.id) return false

        return true
    }

    override fun hashCode(): Int {
        var result = accountId.hashCode()
        result = 31 * result + (id?.hashCode() ?: 0)
        return result
    }

    override fun toString(): String {
        return if (id != null) {
            "MuxedAccount(address=$address, accountId=$accountId, id=$id)"
        } else {
            "MuxedAccount(address=$address)"
        }
    }
}
