//
//  SecureRandom.kt
//  Stellar SDK Kotlin Multiplatform
//
//  Copyright (c) 2026 Soneso. All rights reserved.
//

package com.soneso.stellar.sdk

import org.khronos.webgl.Uint8Array

/**
 * Generates cryptographically secure random bytes using Web Crypto API.
 *
 * Uses crypto.getRandomValues() which is available in all modern browsers
 * and Node.js. This is the recommended way to generate cryptographic randomness
 * in JavaScript environments.
 */
actual fun secureRandomBytes(size: Int): ByteArray {
    require(size > 0) { "Size must be positive" }

    return try {
        val array = Uint8Array(size)
        js("crypto.getRandomValues(array)")
        array.toByteArray()
    } catch (e: Throwable) {
        throw IllegalStateException("Failed to generate secure random bytes: ${e.message}", e)
    }
}

private fun Uint8Array.toByteArray(): ByteArray {
    return ByteArray(this.length) { index ->
        this.asDynamic()[index] as Byte
    }
}
