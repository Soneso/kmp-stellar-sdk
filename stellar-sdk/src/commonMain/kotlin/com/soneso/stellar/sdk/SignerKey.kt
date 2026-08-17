package com.soneso.stellar.sdk

import com.soneso.stellar.sdk.xdr.SignerKeyEd25519SignedPayloadXdr
import com.soneso.stellar.sdk.xdr.SignerKeyXdr
import com.soneso.stellar.sdk.xdr.Uint256Xdr

/**
 * Represents a Stellar signer key used for transaction authorization.
 *
 * This sealed class supports four types of signers as defined in the Stellar protocol:
 *
 * - **ED25519** - Standard Ed25519 public key signer
 * - **PRE_AUTH_TX** - Pre-authorized transaction hash signer
 * - **HASH_X** - SHA-256 hash preimage signer (Hash-X)
 * - **ED25519_SIGNED_PAYLOAD** - Ed25519 signed payload signer (CAP-40)
 *
 * The Ed25519 Signed Payload Signer (introduced in CAP-40) is particularly useful for
 * multi-party contracts like payment channels, as it allows all parties to share a set of
 * transactions for signing and guarantees that if one transaction is signed and submitted,
 * information is revealed that allows all other transactions in the set to be authorized.
 *
 * Example usage:
 * ```kotlin
 * // Create an Ed25519 signer
 * val ed25519Signer = SignerKey.ed25519PublicKey(publicKeyBytes)
 *
 * // Create a signed payload signer
 * val payload = "transaction_hash".encodeToByteArray()
 * val payloadSigner = SignerKey.ed25519SignedPayload(publicKeyBytes, payload)
 * ```
 *
 * @see <a href="https://github.com/stellar/stellar-protocol/blob/master/core/cap-0040.md">CAP-40: Ed25519 Signed Payload Signer</a>
 */
sealed class SignerKey {

    /**
     * Gets the encoded string representation of this signer key.
     *
     * @return The StrKey-encoded representation of this signer key
     */
    abstract fun encodeSignerKey(): String

    /**
     * Converts this SignerKey to its XDR representation.
     *
     * @return The XDR SignerKeyXdr object
     */
    abstract fun toXdr(): SignerKeyXdr

    /**
     * Ed25519 public key signer.
     *
     * @property publicKey The 32-byte Ed25519 public key
     */
    data class Ed25519PublicKey(val publicKey: ByteArray) : SignerKey() {
        init {
            require(publicKey.size == 32) { "Ed25519 public key must be 32 bytes, got ${publicKey.size}" }
        }

        override fun encodeSignerKey(): String = StrKey.encodeEd25519PublicKey(publicKey)

        override fun toXdr(): SignerKeyXdr {
            return SignerKeyXdr.Ed25519(Uint256Xdr(publicKey))
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other == null || this::class != other::class) return false
            other as Ed25519PublicKey
            return publicKey.contentEquals(other.publicKey)
        }

        override fun hashCode(): Int = publicKey.contentHashCode()

        override fun toString(): String = "SignerKey.Ed25519PublicKey(${encodeSignerKey()})"
    }

    /**
     * Pre-authorized transaction hash signer.
     *
     * Pre-authorized transaction signers allow a transaction to be authorized by including the
     * hash of a specific transaction as a signer. This is useful for creating transactions that can
     * only be executed if a specific other transaction is also executed.
     *
     * @property hash The 32-byte transaction hash
     */
    data class PreAuthTx(val hash: ByteArray) : SignerKey() {
        init {
            require(hash.size == 32) { "Pre-auth transaction hash must be 32 bytes, got ${hash.size}" }
        }

        override fun encodeSignerKey(): String = StrKey.encodePreAuthTx(hash)

        override fun toXdr(): SignerKeyXdr {
            return SignerKeyXdr.PreAuthTx(Uint256Xdr(hash))
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other == null || this::class != other::class) return false
            other as PreAuthTx
            return hash.contentEquals(other.hash)
        }

        override fun hashCode(): Int = hash.contentHashCode()

        override fun toString(): String = "SignerKey.PreAuthTx(${encodeSignerKey()})"
    }

    /**
     * SHA-256 hash preimage signer (Hash-X).
     *
     * Hash-X signers allow a transaction to be authorized by revealing the preimage of a specific
     * SHA-256 hash. This is useful for creating hashlocks and other cryptographic puzzles.
     *
     * @property hash The 32-byte SHA-256 hash
     */
    data class HashX(val hash: ByteArray) : SignerKey() {
        init {
            require(hash.size == 32) { "Hash-X must be 32 bytes, got ${hash.size}" }
        }

        override fun encodeSignerKey(): String = StrKey.encodeSha256Hash(hash)

        override fun toXdr(): SignerKeyXdr {
            return SignerKeyXdr.HashX(Uint256Xdr(hash))
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other == null || this::class != other::class) return false
            other as HashX
            return hash.contentEquals(other.hash)
        }

        override fun hashCode(): Int = hash.contentHashCode()

        override fun toString(): String = "SignerKey.HashX(${encodeSignerKey()})"
    }

    /**
     * Ed25519 signed payload signer (CAP-40).
     *
     * Ed25519 signed payload signers allow a transaction to be authorized by providing a
     * signature of a specific payload using an Ed25519 key. This is particularly useful for
     * multi-party contracts where signing one transaction reveals information that allows other
     * transactions to be authorized.
     *
     * @property ed25519PublicKey The 32-byte Ed25519 public key that must sign the payload
     * @property payload The payload to be signed (up to 64 bytes)
     */
    data class Ed25519SignedPayload(
        val ed25519PublicKey: ByteArray,
        val payload: ByteArray
    ) : SignerKey() {
        init {
            require(ed25519PublicKey.size == 32) {
                "Ed25519 public key must be 32 bytes, got ${ed25519PublicKey.size}"
            }
            require(payload.size in 1..SIGNED_PAYLOAD_MAX_PAYLOAD_LENGTH) {
                "Payload must be between 1 and $SIGNED_PAYLOAD_MAX_PAYLOAD_LENGTH bytes, got ${payload.size}"
            }
        }

        /**
         * Gets the StrKey-encoded representation of the Ed25519 public key.
         *
         * @return The StrKey-encoded Ed25519 public key (starts with 'G')
         */
        fun encodedEd25519PublicKey(): String = StrKey.encodeEd25519PublicKey(ed25519PublicKey)

        override fun encodeSignerKey(): String {
            // A signed payload strkey carries the 32-byte ed25519 public key, the payload length
            // as a big-endian four-byte value, and the payload padded with zeros to a four-byte
            // boundary.
            val payloadLength = payload.size
            val paddingSize = (4 - payloadLength % 4) % 4
            val paddedPayload = ByteArray(payloadLength + paddingSize)
            payload.copyInto(paddedPayload, 0, 0, payloadLength)

            val payloadLengthBytes = ByteArray(4) { i ->
                (payloadLength shr (24 - i * 8)).toByte()
            }

            val encoded = ed25519PublicKey + payloadLengthBytes + paddedPayload
            return StrKey.encodeSignedPayload(encoded)
        }

        override fun toXdr(): SignerKeyXdr {
            return SignerKeyXdr.Ed25519SignedPayload(
                SignerKeyEd25519SignedPayloadXdr(
                    ed25519 = Uint256Xdr(ed25519PublicKey),
                    payload = payload
                )
            )
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other == null || this::class != other::class) return false
            other as Ed25519SignedPayload
            if (!ed25519PublicKey.contentEquals(other.ed25519PublicKey)) return false
            if (!payload.contentEquals(other.payload)) return false
            return true
        }

        override fun hashCode(): Int {
            var result = ed25519PublicKey.contentHashCode()
            result = 31 * result + payload.contentHashCode()
            return result
        }

        override fun toString(): String = "SignerKey.Ed25519SignedPayload(${encodeSignerKey()})"
    }

    companion object {
        /**
         * Maximum payload length for Ed25519 signed payload signers.
         * As defined in CAP-40, the payload has a maximum size of 64 bytes.
         */
        const val SIGNED_PAYLOAD_MAX_PAYLOAD_LENGTH = 64

        /**
         * The character a signed payload strkey is written under.
         */
        private const val SIGNED_PAYLOAD_PREFIX = "P"

        /**
         * Characters a signed payload strkey has. The range is the codec's own, so the two
         * cannot drift apart.
         */
        private val signedPayloadEncodedLengths: IntRange = StrKey.SIGNED_PAYLOAD_STRKEY_LENGTHS

        /**
         * Creates a SignerKey from an encoded signer key string.
         *
         * This method automatically detects the signer key type based on the encoded string format and
         * creates the appropriate SignerKey instance.
         *
         * @param encodedSignerKey The StrKey-encoded signer key string
         * @return A new SignerKey instance
         * @throws IllegalArgumentException if [encodedSignerKey] is not a well-formed strkey of
         * one of the four signer key forms. It is the only exception this function raises: a
         * signed payload is checked against the framing it declares before any byte that framing
         * names is read. A string written under the character a signed payload carries and as
         * long as one is refused for the rule it broke; every other input is refused by naming
         * itself.
         */
        fun fromEncodedSignerKey(encodedSignerKey: String): SignerKey {
            return when {
                StrKey.isValidEd25519PublicKey(encodedSignerKey) -> {
                    ed25519PublicKey(StrKey.decodeEd25519PublicKey(encodedSignerKey))
                }
                StrKey.isValidPreAuthTx(encodedSignerKey) -> {
                    preAuthTx(StrKey.decodePreAuthTx(encodedSignerKey))
                }
                StrKey.isValidSha256Hash(encodedSignerKey) -> {
                    hashX(StrKey.decodeSha256Hash(encodedSignerKey))
                }
                StrKey.isValidSignedPayload(encodedSignerKey) -> {
                    // The signed payload format: public key (32) + declared length (4) + the
                    // payload padded with zeros to a four-byte boundary. Decoding checks that
                    // framing, so a string that reaches here declares a payload length the type
                    // admits and carries every byte of the payload that length names.
                    val decoded = StrKey.decodeSignedPayload(encodedSignerKey)

                    val publicKey = decoded.copyOfRange(0, 32)
                    val payloadLength = ((decoded[32].toInt() and 0xFF) shl 24) or
                            ((decoded[33].toInt() and 0xFF) shl 16) or
                            ((decoded[34].toInt() and 0xFF) shl 8) or
                            (decoded[35].toInt() and 0xFF)

                    val payload = decoded.copyOfRange(36, 36 + payloadLength)
                    ed25519SignedPayload(publicKey, payload)
                }
                else -> {
                    if (encodedSignerKey.startsWith(SIGNED_PAYLOAD_PREFIX) &&
                        encodedSignerKey.length in signedPayloadEncodedLengths
                    ) {
                        // The string is written under the character a signed payload carries and
                        // holds a count of characters one holds, so it describes a signed payload
                        // and no other signer key. Decoding it raises the rule it broke, which
                        // the refusal below cannot name.
                        StrKey.decodeSignedPayload(encodedSignerKey)
                        throw IllegalStateException(
                            "decodeSignedPayload accepted a string isValidSignedPayload rejected"
                        )
                    }
                    throw IllegalArgumentException("Invalid encoded signer key: $encodedSignerKey")
                }
            }
        }

        /**
         * Creates an Ed25519 public key signer from raw bytes.
         *
         * @param publicKey The 32-byte Ed25519 public key
         * @return A new Ed25519PublicKey signer
         */
        fun ed25519PublicKey(publicKey: ByteArray): Ed25519PublicKey = Ed25519PublicKey(publicKey)

        /**
         * Creates an Ed25519 public key signer from an encoded public key string.
         *
         * @param encodedPublicKey The StrKey-encoded Ed25519 public key (starts with 'G')
         * @return A new Ed25519PublicKey signer
         */
        fun ed25519PublicKey(encodedPublicKey: String): Ed25519PublicKey {
            return ed25519PublicKey(StrKey.decodeEd25519PublicKey(encodedPublicKey))
        }

        /**
         * Creates a pre-authorized transaction hash signer from raw bytes.
         *
         * @param hash The 32-byte transaction hash
         * @return A new PreAuthTx signer
         */
        fun preAuthTx(hash: ByteArray): PreAuthTx = PreAuthTx(hash)

        /**
         * Creates a pre-authorized transaction hash signer from an encoded hash string.
         *
         * @param encodedHash The StrKey-encoded pre-authorized transaction hash (starts with 'T')
         * @return A new PreAuthTx signer
         */
        fun preAuthTx(encodedHash: String): PreAuthTx {
            return preAuthTx(StrKey.decodePreAuthTx(encodedHash))
        }

        /**
         * Creates a Hash-X signer from raw bytes.
         *
         * @param hash The 32-byte SHA-256 hash
         * @return A new HashX signer
         */
        fun hashX(hash: ByteArray): HashX = HashX(hash)

        /**
         * Creates a Hash-X signer from an encoded hash string.
         *
         * @param encodedHash The StrKey-encoded SHA-256 hash (starts with 'X')
         * @return A new HashX signer
         */
        fun hashX(encodedHash: String): HashX {
            return hashX(StrKey.decodeSha256Hash(encodedHash))
        }

        /**
         * Creates an Ed25519 signed payload signer.
         *
         * @param ed25519PublicKey The 32-byte Ed25519 public key
         * @param payload The payload to be signed (up to 64 bytes)
         * @return A new Ed25519SignedPayload signer
         */
        fun ed25519SignedPayload(ed25519PublicKey: ByteArray, payload: ByteArray): Ed25519SignedPayload {
            return Ed25519SignedPayload(ed25519PublicKey, payload)
        }

        /**
         * Creates an Ed25519 signed payload signer from an encoded public key string.
         *
         * @param encodedPublicKey The StrKey-encoded Ed25519 public key (starts with 'G')
         * @param payload The payload to be signed (up to 64 bytes)
         * @return A new Ed25519SignedPayload signer
         */
        fun ed25519SignedPayload(encodedPublicKey: String, payload: ByteArray): Ed25519SignedPayload {
            return ed25519SignedPayload(StrKey.decodeEd25519PublicKey(encodedPublicKey), payload)
        }

        /**
         * Creates a SignerKey from its XDR representation.
         *
         * @param signerKey The XDR SignerKeyXdr object
         * @return A new SignerKey instance
         * @throws IllegalArgumentException if the XDR signer key type is unknown
         */
        fun fromXdr(signerKey: SignerKeyXdr): SignerKey {
            return when (signerKey) {
                is SignerKeyXdr.Ed25519 -> {
                    ed25519PublicKey(signerKey.value.value)
                }
                is SignerKeyXdr.PreAuthTx -> {
                    preAuthTx(signerKey.value.value)
                }
                is SignerKeyXdr.HashX -> {
                    hashX(signerKey.value.value)
                }
                is SignerKeyXdr.Ed25519SignedPayload -> {
                    ed25519SignedPayload(
                        ed25519PublicKey = signerKey.value.ed25519.value,
                        payload = signerKey.value.payload
                    )
                }
            }
        }
    }
}
