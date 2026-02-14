package com.soneso.stellar.sdk.crypto

import kotlinx.cinterop.*
import libsodium.*

/**
 * Native (iOS/macOS) implementation of SHA-256 using libsodium.
 *
 * Uses libsodium's crypto_hash_sha256 function which is:
 * - Audited and battle-tested
 * - Used by stellar-core
 * - Constant-time operations
 * - Memory-safe
 */
@OptIn(ExperimentalForeignApi::class, UnsafeNumber::class)
internal class Sha256CryptoNative : Sha256Crypto {
    override val libraryName: String = "libsodium (crypto_hash_sha256)"

    companion object {
        init {
            // Initialize libsodium
            val result = sodium_init()
            if (result < 0) {
                throw IllegalStateException("Failed to initialize libsodium")
            }
        }
    }

    override suspend fun hash(data: ByteArray): ByteArray {
        return memScoped {
            // Allocate output buffer for 32-byte hash
            val output = allocArray<UByteVar>(32)

            // Hash the data (empty input is valid for SHA-256)
            if (data.isEmpty()) {
                val result = crypto_hash_sha256(output, null, 0u)
                require(result == 0) { "SHA-256 hash failed" }
            } else {
                data.usePinned { pinnedData ->
                    val result = crypto_hash_sha256(
                        output,
                        pinnedData.addressOf(0).reinterpret(),
                        data.size.toULong()
                    )
                    require(result == 0) { "SHA-256 hash failed" }
                }
            }

            // Convert to ByteArray
            ByteArray(32) { i ->
                output[i].toByte()
            }
        }
    }
}

actual fun getSha256Crypto(): Sha256Crypto = Sha256CryptoNative()
