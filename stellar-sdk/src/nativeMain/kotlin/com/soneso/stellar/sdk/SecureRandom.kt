//
//  SecureRandom.kt
//  Stellar SDK Kotlin Multiplatform
//
//  Copyright (c) 2026 Soneso. All rights reserved.
//

@file:OptIn(ExperimentalForeignApi::class)

package com.soneso.stellar.sdk

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import libsodium.randombytes_buf

/**
 * Generates cryptographically secure random bytes using libsodium.
 *
 * Uses randombytes_buf() which is backed by the system CSPRNG (arc4random_buf on Apple platforms).
 */
actual fun secureRandomBytes(size: Int): ByteArray {
    require(size > 0) { "Size must be positive" }

    return UByteArray(size).apply {
        usePinned { pinned ->
            randombytes_buf(pinned.addressOf(0), size.toULong())
        }
    }.asByteArray()
}
