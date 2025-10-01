package com.stellar.sdk.crypto

import java.security.MessageDigest

/**
 * JVM implementation of SHA-256 using Java's MessageDigest.
 *
 * Uses the standard Java Security API which is FIPS 140-2 compliant
 * and battle-tested across millions of applications.
 */
internal class Sha256CryptoJvm : Sha256Crypto {
    override val libraryName: String = "Java Security API (MessageDigest SHA-256)"

    override fun hash(data: ByteArray): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(data)
    }
}

actual fun getSha256Crypto(): Sha256Crypto = Sha256CryptoJvm()
