package com.soneso.stellar.sdk.crypto

import org.khronos.webgl.Uint8Array

/**
 * JavaScript implementation of SHA-256.
 *
 * Uses libsodium-wrappers-sumo for SHA-256 hashing with proper async initialization.
 * The sumo build is required because crypto_hash_sha256 is not included in the standard build.
 * Web Crypto API is avoided because its async nature can cause event loop deadlocks
 * when used in synchronous contexts.
 */
internal class Sha256CryptoJs : Sha256Crypto {
    override val libraryName: String = "libsodium-wrappers-sumo (crypto_hash_sha256)"

    override suspend fun hash(data: ByteArray): ByteArray {
        // Ensure libsodium is initialized before use
        LibsodiumInit.ensureInitialized()

        return try {
            val sodium = LibsodiumInit.getSodium()
            val dataArray = data.toUint8Array()

            // Call crypto_hash_sha256 via JS block to ensure proper type handling
            val result = js(
                """
                (function() {
                    return sodium.crypto_hash_sha256(dataArray);
                })()
                """
            ).unsafeCast<Uint8Array>()

            result.toByteArray()
        } catch (e: Throwable) {
            throw IllegalStateException("Failed to compute SHA-256 hash: ${e.message}", e)
        }
    }

    // Helper extension functions
    private fun ByteArray.toUint8Array(): Uint8Array {
        val array = Uint8Array(this.size)
        this.forEachIndexed { index, byte ->
            array.asDynamic()[index] = byte
        }
        return array
    }

    private fun Uint8Array.toByteArray(): ByteArray {
        return ByteArray(this.length) { index ->
            this.asDynamic()[index] as Byte
        }
    }
}

actual fun getSha256Crypto(): Sha256Crypto = Sha256CryptoJs()
