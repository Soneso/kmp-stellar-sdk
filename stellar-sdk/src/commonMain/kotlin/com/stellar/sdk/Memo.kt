package com.stellar.sdk

import com.stellar.sdk.xdr.*
import kotlin.jvm.JvmName
import kotlin.jvm.JvmStatic

/**
 * The memo contains optional extra information. It is the responsibility of the client to interpret
 * this value.
 *
 * Memos can be one of the following types:
 * - [MemoNone]: Empty memo (no additional information)
 * - [MemoText]: A string up to 28-bytes long
 * - [MemoId]: A 64 bit unsigned integer
 * - [MemoHash]: A 32 byte hash
 * - [MemoReturn]: A 32 byte hash intended to be interpreted as the hash of the transaction the sender is refunding
 *
 * ## Usage
 *
 * ```kotlin
 * // No memo
 * val memo1 = MemoNone
 *
 * // Text memo (max 28 bytes)
 * val memo2 = MemoText("Hello Stellar")
 *
 * // ID memo
 * val memo3 = MemoId(123456789UL)
 *
 * // Hash memo (from hex string)
 * val memo4 = MemoHash("e98869bba8bce08c10b78406202127f3888c25454cd37b02600862452751f526")
 *
 * // Hash memo (from bytes)
 * val hash = ByteArray(32) { it.toByte() }
 * val memo5 = MemoHash(hash)
 *
 * // Return hash memo
 * val memo6 = MemoReturn("e98869bba8bce08c10b78406202127f3888c25454cd37b02600862452751f526")
 * ```
 *
 * @see <a href="https://developers.stellar.org/docs/learn/encyclopedia/transactions-specialized/memos">Memos</a>
 */
sealed class Memo {

    /**
     * Converts this memo to its XDR representation.
     *
     * @return The XDR Memo object
     */
    abstract fun toXdr(): MemoXdr

    companion object {
        /**
         * Creates a Memo from its XDR representation.
         *
         * @param xdr The XDR Memo object
         * @return A Memo instance
         * @throws IllegalArgumentException if the XDR type is unknown
         */
        @JvmStatic
        fun fromXdr(xdr: MemoXdr): Memo {
            return when (xdr) {
                is MemoXdr.Void -> MemoNone
                is MemoXdr.Text -> MemoText(xdr.value)
                is MemoXdr.Id -> MemoId(xdr.value.value)
                is MemoXdr.Hash -> MemoHash(xdr.value.value)
                is MemoXdr.RetHash -> MemoReturn(xdr.value.value)
            }
        }
    }
}

/**
 * Represents MEMO_NONE - an empty memo with no additional information.
 */
data object MemoNone : Memo() {
    override fun toXdr(): MemoXdr = MemoXdr.Void

    override fun toString(): String = ""
}

/**
 * Represents MEMO_TEXT - a UTF-8 string up to 28 bytes long.
 *
 * @property text The text content of the memo
 * @throws IllegalArgumentException if the text is longer than 28 bytes when UTF-8 encoded
 */
class MemoText : Memo {
    val text: String
    val bytes: ByteArray

    /**
     * Creates a MEMO_TEXT from a string.
     *
     * @param text The text content (will be UTF-8 encoded)
     * @throws IllegalArgumentException if the UTF-8 encoded text exceeds 28 bytes
     */
    constructor(text: String) {
        val encodedBytes = text.encodeToByteArray()
        require(encodedBytes.size <= 28) {
            "Text memo cannot be more than 28 bytes. The provided text is ${encodedBytes.size} bytes when UTF-8 encoded."
        }
        this.text = text
        this.bytes = encodedBytes
    }

    /**
     * Creates a MEMO_TEXT from raw bytes.
     *
     * @param bytes The raw bytes (max 28 bytes)
     * @throws IllegalArgumentException if bytes array is longer than 28 bytes
     */
    constructor(bytes: ByteArray) {
        require(bytes.size <= 28) {
            "Text memo cannot be more than 28 bytes. The provided bytes array has ${bytes.size} bytes."
        }
        this.bytes = bytes
        this.text = bytes.decodeToString()
    }

    override fun toXdr(): MemoXdr = MemoXdr.Text(text)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as MemoText

        return bytes.contentEquals(other.bytes)
    }

    override fun hashCode(): Int {
        return bytes.contentHashCode()
    }

    override fun toString(): String = text
}

/**
 * Represents MEMO_ID - a 64-bit unsigned integer.
 *
 * @property id The unsigned 64-bit integer ID
 */
data class MemoId(val id: ULong) : Memo() {
    override fun toXdr(): MemoXdr = MemoXdr.Id(Uint64Xdr(id))

    override fun toString(): String = id.toString()
}

/**
 * Represents MEMO_HASH - a 32-byte hash.
 *
 * This is typically the hash of what to pull from the content server.
 *
 * @property bytes The 32-byte hash
 * @throws IllegalArgumentException if the bytes array is not exactly 32 bytes
 */
class MemoHash : Memo {
    val bytes: ByteArray

    /**
     * Creates a MEMO_HASH from a byte array.
     *
     * @param bytes The 32-byte hash
     * @throws IllegalArgumentException if bytes is not exactly 32 bytes
     */
    constructor(bytes: ByteArray) {
        require(bytes.size == 32) {
            "MEMO_HASH must be exactly 32 bytes, got ${bytes.size} bytes"
        }
        this.bytes = bytes
    }

    /**
     * Creates a MEMO_HASH from a hex-encoded string.
     *
     * @param hexString The 64-character hex string (case-insensitive)
     * @throws IllegalArgumentException if the hex string is invalid or doesn't decode to 32 bytes
     */
    constructor(hexString: String) : this(Util.hexToBytes(hexString.lowercase())) {
        require(hexString.length == 64) {
            "MEMO_HASH hex string must be 64 characters (32 bytes), got ${hexString.length} characters"
        }
    }

    /**
     * Returns the hex representation of this hash.
     *
     * @return A lowercase 64-character hex string
     */
    val hexValue: String
        get() = Util.bytesToHex(bytes).lowercase()

    override fun toXdr(): MemoXdr = MemoXdr.Hash(HashXdr(bytes))

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as MemoHash

        return bytes.contentEquals(other.bytes)
    }

    override fun hashCode(): Int {
        return bytes.contentHashCode()
    }

    override fun toString(): String = hexValue
}

/**
 * Represents MEMO_RETURN - a 32-byte hash intended to be interpreted as the hash of the
 * transaction the sender is refunding.
 *
 * @property bytes The 32-byte hash
 * @throws IllegalArgumentException if the bytes array is not exactly 32 bytes
 */
class MemoReturn : Memo {
    val bytes: ByteArray

    /**
     * Creates a MEMO_RETURN from a byte array.
     *
     * @param bytes The 32-byte return hash
     * @throws IllegalArgumentException if bytes is not exactly 32 bytes
     */
    constructor(bytes: ByteArray) {
        require(bytes.size == 32) {
            "MEMO_RETURN must be exactly 32 bytes, got ${bytes.size} bytes"
        }
        this.bytes = bytes
    }

    /**
     * Creates a MEMO_RETURN from a hex-encoded string.
     *
     * @param hexString The 64-character hex string (case-insensitive)
     * @throws IllegalArgumentException if the hex string is invalid or doesn't decode to 32 bytes
     */
    constructor(hexString: String) : this(Util.hexToBytes(hexString.lowercase())) {
        require(hexString.length == 64) {
            "MEMO_RETURN hex string must be 64 characters (32 bytes), got ${hexString.length} characters"
        }
    }

    /**
     * Returns the hex representation of this return hash.
     *
     * @return A lowercase 64-character hex string
     */
    val hexValue: String
        get() = Util.bytesToHex(bytes).lowercase()

    override fun toXdr(): MemoXdr = MemoXdr.RetHash(HashXdr(bytes))

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as MemoReturn

        return bytes.contentEquals(other.bytes)
    }

    override fun hashCode(): Int {
        return bytes.contentHashCode()
    }

    override fun toString(): String = hexValue
}
