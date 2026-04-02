//
//  SecureRandom.kt
//  Stellar SDK Kotlin Multiplatform
//
//  Copyright (c) 2026 Soneso. All rights reserved.
//

package com.soneso.stellar.sdk

import java.security.SecureRandom

/**
 * Generates cryptographically secure random bytes using java.security.SecureRandom.
 */
actual fun secureRandomBytes(size: Int): ByteArray {
    require(size > 0) { "Size must be positive" }
    val bytes = ByteArray(size)
    SecureRandom().nextBytes(bytes)
    return bytes
}
