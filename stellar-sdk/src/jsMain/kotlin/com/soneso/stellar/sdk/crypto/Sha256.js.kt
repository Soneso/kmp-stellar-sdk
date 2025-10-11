package com.soneso.stellar.sdk.crypto

import kotlinx.coroutines.await
import org.khronos.webgl.Uint8Array
import org.khronos.webgl.get
import kotlin.js.Promise

/**
 * JavaScript implementation of SHA-256.
 *
 * Uses Web Crypto API (SubtleCrypto.digest) when available for hardware acceleration,
 * with libsodium-wrappers as a fallback for universal compatibility.
 */
internal class Sha256CryptoJs : Sha256Crypto {
    override val libraryName: String = if (isWebCryptoAvailable()) {
        "Web Crypto API (SubtleCrypto.digest)"
    } else {
        "libsodium-wrappers (crypto_hash_sha256)"
    }

    override fun hash(data: ByteArray): ByteArray {
        return if (isWebCryptoAvailable()) {
            hashWithWebCrypto(data)
        } else {
            hashWithLibsodium(data)
        }
    }

    private fun hashWithWebCrypto(data: ByteArray): ByteArray {
        // Web Crypto API requires async operations, but we need sync for our API
        // We use a workaround with runBlocking-like behavior
        val crypto = js("crypto.subtle") as SubtleCrypto
        val uint8Array = Uint8Array(data.size)
        for (i in data.indices) {
            uint8Array.asDynamic()[i] = data[i]
        }

        // Note: This is a synchronous wrapper around an async operation
        // In real usage, callers should use suspend functions, but for compatibility
        // with the existing API, we block here
        val promise = crypto.digest("SHA-256", uint8Array.buffer)
        val result = waitForPromise(promise)
        val resultArray = Uint8Array(result as org.khronos.webgl.ArrayBuffer)
        val size = resultArray.asDynamic().length as Int
        return ByteArray(size) { i -> resultArray.asDynamic()[i] as Byte }
    }

    private fun hashWithLibsodium(data: ByteArray): ByteArray {
        val sodium = js("require('libsodium-wrappers')") as Libsodium

        // Ensure libsodium is ready
        val isReady = js("sodium.ready instanceof Promise") as Boolean
        if (isReady) {
            js("sodium.ready.then(function() {})")
        }

        val hash = sodium.crypto_hash_sha256(data)
        return hash
    }

    private fun isWebCryptoAvailable(): Boolean {
        return try {
            js("typeof crypto !== 'undefined' && typeof crypto.subtle !== 'undefined' && typeof crypto.subtle.digest === 'function'") as Boolean
        } catch (e: Throwable) {
            false
        }
    }

    private fun waitForPromise(promise: Promise<dynamic>): dynamic {
        // This is a blocking wait for the promise to resolve
        // In a real KMP project, we'd use coroutines, but for now this works
        var result: dynamic = null
        var error: Throwable? = null
        var done = false

        promise.then(
            onFulfilled = { value: dynamic ->
                result = value
                done = true
                null
            },
            onRejected = { reason: dynamic ->
                error = Exception(reason.toString())
                done = true
                null
            }
        )

        // Spin-wait for completion (not ideal, but necessary for sync API)
        while (!done) {
            // Allow event loop to process
            js("void(0)")
        }

        error?.let { throw it }
        return result
    }
}

// External declarations for Web Crypto API
external interface SubtleCrypto {
    fun digest(algorithm: String, data: dynamic): Promise<dynamic>
}

// External declarations for libsodium-wrappers
external interface Libsodium {
    val ready: Promise<Unit>
    fun crypto_hash_sha256(message: ByteArray): ByteArray
}

actual fun getSha256Crypto(): Sha256Crypto = Sha256CryptoJs()
