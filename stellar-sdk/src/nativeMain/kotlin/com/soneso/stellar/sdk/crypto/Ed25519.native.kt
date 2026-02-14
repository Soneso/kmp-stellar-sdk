package com.soneso.stellar.sdk.crypto

import kotlinx.cinterop.*
import libsodium.*
import platform.posix.memcpy

/**
 * Native (iOS/macOS) implementation of Ed25519 cryptographic operations using libsodium.
 *
 * libsodium is a production-ready, audited cryptographic library that provides:
 * - RFC 8032 compliant Ed25519 implementation
 * - Constant-time operations to prevent timing attacks
 * - Memory-safe operations with proper zeroing
 * - Cross-platform compatibility
 *
 * Installation requirements:
 * - macOS: `brew install libsodium`
 * - iOS: libsodium must be built for iOS targets or use CocoaPods/SPM
 *
 * @see <a href="https://libsodium.gitbook.io/doc/">libsodium documentation</a>
 */
@OptIn(ExperimentalForeignApi::class)
internal class NativeEd25519Crypto : Ed25519Crypto {

    override val libraryName: String = "libsodium"

    companion object {
        init {
            // Initialize libsodium
            val result = sodium_init()
            if (result < 0) {
                throw IllegalStateException("Failed to initialize libsodium")
            }
        }

        // Ed25519 constants from libsodium
        private const val SEED_BYTES = 32  // crypto_sign_SEEDBYTES
        private const val PUBLIC_KEY_BYTES = 32  // crypto_sign_PUBLICKEYBYTES
        private const val SECRET_KEY_BYTES = 64  // crypto_sign_SECRETKEYBYTES
        private const val SIGNATURE_BYTES = 64  // crypto_sign_BYTES
    }

    override suspend fun generatePrivateKey(): ByteArray {
        return UByteArray(SEED_BYTES).apply {
            usePinned { pinned ->
                // Generate cryptographically secure random bytes
                randombytes_buf(pinned.addressOf(0), SEED_BYTES.toULong())
            }
        }.asByteArray()
    }

    override suspend fun derivePublicKey(privateKey: ByteArray): ByteArray {
        require(privateKey.size == SEED_BYTES) { "Private key must be $SEED_BYTES bytes" }

        return memScoped {
            val publicKey = UByteArray(PUBLIC_KEY_BYTES)
            val secretKey = UByteArray(SECRET_KEY_BYTES)
            val seed = privateKey.asUByteArray()

            seed.usePinned { seedPinned ->
                publicKey.usePinned { pkPinned ->
                    secretKey.usePinned { skPinned ->
                        // Derive keypair from seed
                        val result = crypto_sign_seed_keypair(
                            pk = pkPinned.addressOf(0),
                            sk = skPinned.addressOf(0),
                            seed = seedPinned.addressOf(0)
                        )

                        if (result != 0) {
                            throw IllegalStateException("Failed to derive public key from seed")
                        }
                    }
                }
            }

            // Zero out secret key from memory for security
            secretKey.fill(0u)

            publicKey.asByteArray()
        }
    }

    override suspend fun sign(data: ByteArray, privateKey: ByteArray): ByteArray {
        require(privateKey.size == SEED_BYTES) { "Private key must be $SEED_BYTES bytes" }

        return memScoped {
            val publicKey = UByteArray(PUBLIC_KEY_BYTES)
            val secretKey = UByteArray(SECRET_KEY_BYTES)
            val signature = UByteArray(SIGNATURE_BYTES)
            val seed = privateKey.asUByteArray()

            seed.usePinned { seedPinned ->
                publicKey.usePinned { pkPinned ->
                    secretKey.usePinned { skPinned ->
                        // First derive the full keypair from seed
                        val keypairResult = crypto_sign_seed_keypair(
                            pk = pkPinned.addressOf(0),
                            sk = skPinned.addressOf(0),
                            seed = seedPinned.addressOf(0)
                        )

                        if (keypairResult != 0) {
                            throw IllegalStateException("Failed to derive keypair from seed")
                        }

                        // Sign the data (empty data is valid for Ed25519)
                        signature.usePinned { sigPinned ->
                            val signedLength = alloc<ULongVar>()

                            val signResult = if (data.isEmpty()) {
                                crypto_sign_detached(
                                    sig = sigPinned.addressOf(0),
                                    siglen_p = signedLength.ptr,
                                    m = null,
                                    mlen = 0u,
                                    sk = skPinned.addressOf(0)
                                )
                            } else {
                                val dataU = data.asUByteArray()
                                dataU.usePinned { dataPinned ->
                                    crypto_sign_detached(
                                        sig = sigPinned.addressOf(0),
                                        siglen_p = signedLength.ptr,
                                        m = dataPinned.addressOf(0),
                                        mlen = data.size.toULong(),
                                        sk = skPinned.addressOf(0)
                                    )
                                }
                            }

                            if (signResult != 0) {
                                throw IllegalStateException("Failed to sign data")
                            }

                            if (signedLength.value != SIGNATURE_BYTES.toULong()) {
                                throw IllegalStateException("Unexpected signature length: ${signedLength.value}")
                            }
                        }
                    }
                }
            }

            // Zero out sensitive key material from memory
            secretKey.fill(0u)

            signature.asByteArray()
        }
    }

    override suspend fun verify(data: ByteArray, signature: ByteArray, publicKey: ByteArray): Boolean {
        require(publicKey.size == PUBLIC_KEY_BYTES) { "Public key must be $PUBLIC_KEY_BYTES bytes" }
        require(signature.size == SIGNATURE_BYTES) { "Signature must be $SIGNATURE_BYTES bytes" }

        return memScoped {
            val signatureU = signature.asUByteArray()
            val publicKeyU = publicKey.asUByteArray()

            signatureU.usePinned { sigPinned ->
                publicKeyU.usePinned { pkPinned ->
                    // Empty data is valid for Ed25519
                    val result = if (data.isEmpty()) {
                        crypto_sign_verify_detached(
                            sig = sigPinned.addressOf(0),
                            m = null,
                            mlen = 0u,
                            pk = pkPinned.addressOf(0)
                        )
                    } else {
                        val dataU = data.asUByteArray()
                        dataU.usePinned { dataPinned ->
                            crypto_sign_verify_detached(
                                sig = sigPinned.addressOf(0),
                                m = dataPinned.addressOf(0),
                                mlen = data.size.toULong(),
                                pk = pkPinned.addressOf(0)
                            )
                        }
                    }

                    result == 0
                }
            }
        }
    }
}

/**
 * Get the native-specific Ed25519 crypto implementation using libsodium.
 */
actual fun getEd25519Crypto(): Ed25519Crypto = NativeEd25519Crypto()
