package com.stellar.sdk

import com.stellar.sdk.xdr.DecoratedSignatureXdr
import com.stellar.sdk.xdr.SignatureHintXdr
import com.stellar.sdk.xdr.SignatureXdr

/**
 * Represents a decorated signature on a Stellar transaction.
 *
 * A decorated signature consists of:
 * - A signature hint (last 4 bytes of the public key)
 * - The actual signature bytes
 *
 * The hint helps identify which key signed the transaction without transmitting the full public key.
 *
 * ## Usage
 *
 * ```kotlin
 * // Sign a transaction
 * val keypair = KeyPair.random()
 * val transaction = TransactionBuilder(...)
 *     .build()
 * transaction.sign(keypair)
 *
 * // Access signatures
 * val signatures: List<DecoratedSignature> = transaction.signatures
 * ```
 *
 * @property hint The signature hint - last 4 bytes of the public key used for signing
 * @property signature The Ed25519 signature bytes (64 bytes)
 *
 * @see <a href="https://developers.stellar.org/docs/learn/encyclopedia/security/signatures-multisig">Signatures and Multisig</a>
 */
data class DecoratedSignature(
    val hint: ByteArray,
    val signature: ByteArray
) {
    init {
        require(hint.size == 4) { "Signature hint must be exactly 4 bytes, got ${hint.size}" }
        require(signature.size <= 64) { "Signature must be at most 64 bytes, got ${signature.size}" }
    }

    /**
     * Converts this decorated signature to its XDR representation.
     *
     * @return The XDR DecoratedSignatureXdr object
     */
    fun toXdr(): DecoratedSignatureXdr {
        return DecoratedSignatureXdr(
            hint = SignatureHintXdr(hint),
            signature = SignatureXdr(signature)
        )
    }

    /**
     * Custom equals implementation to properly compare byte arrays.
     */
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as DecoratedSignature

        if (!hint.contentEquals(other.hint)) return false
        if (!signature.contentEquals(other.signature)) return false

        return true
    }

    /**
     * Custom hashCode implementation to properly hash byte arrays.
     */
    override fun hashCode(): Int {
        var result = hint.contentHashCode()
        result = 31 * result + signature.contentHashCode()
        return result
    }

    /**
     * Returns a string representation of this decorated signature.
     */
    override fun toString(): String {
        return "DecoratedSignature(hint=${Util.bytesToHex(hint)}, signature=${Util.bytesToHex(signature).take(16)}...)"
    }

    companion object {
        /**
         * Creates a DecoratedSignature from its XDR representation.
         *
         * @param xdr The XDR DecoratedSignatureXdr object
         * @return A DecoratedSignature instance
         */
        fun fromXdr(xdr: DecoratedSignatureXdr): DecoratedSignature {
            return DecoratedSignature(
                hint = xdr.hint.value,
                signature = xdr.signature.value
            )
        }
    }
}
