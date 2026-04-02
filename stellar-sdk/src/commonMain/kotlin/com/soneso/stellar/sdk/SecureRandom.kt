//
//  SecureRandom.kt
//  Stellar SDK Kotlin Multiplatform
//
//  Copyright (c) 2026 Soneso. All rights reserved.
//

package com.soneso.stellar.sdk

/**
 * Generates cryptographically secure random bytes.
 *
 * Uses platform-specific CSPRNG:
 * - JVM: java.security.SecureRandom
 * - JS: crypto.getRandomValues
 * - Native: libsodium randombytes_buf
 *
 * @param size Number of random bytes to generate (must be positive)
 * @return ByteArray of cryptographically secure random bytes
 * @throws IllegalArgumentException if size is not positive
 */
expect fun secureRandomBytes(size: Int): ByteArray
