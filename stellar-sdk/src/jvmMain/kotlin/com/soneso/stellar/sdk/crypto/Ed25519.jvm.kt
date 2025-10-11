package com.soneso.stellar.sdk.crypto

import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer
import java.security.SecureRandom
import java.security.Security
import org.bouncycastle.jce.provider.BouncyCastleProvider

/**
 * JVM implementation of Ed25519 cryptographic operations using BouncyCastle.
 *
 * BouncyCastle is a mature, well-tested cryptographic library that provides
 * secure Ed25519 implementation compliant with RFC 8032.
 */
internal class JvmEd25519Crypto : Ed25519Crypto {

    override val libraryName: String = "BouncyCastle"

    companion object {
        init {
            // Register BouncyCastle as a security provider
            if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
                Security.addProvider(BouncyCastleProvider())
            }
        }
    }

    override suspend fun generatePrivateKey(): ByteArray {
        val privateKey = Ed25519PrivateKeyParameters(SecureRandom())
        return privateKey.encoded
    }

    override suspend fun derivePublicKey(privateKey: ByteArray): ByteArray {
        require(privateKey.size == 32) { "Private key must be 32 bytes" }

        val ed25519PrivateKey = Ed25519PrivateKeyParameters(privateKey, 0)
        val publicKey = ed25519PrivateKey.generatePublicKey()
        return publicKey.encoded
    }

    override suspend fun sign(data: ByteArray, privateKey: ByteArray): ByteArray {
        require(privateKey.size == 32) { "Private key must be 32 bytes" }

        val ed25519PrivateKey = Ed25519PrivateKeyParameters(privateKey, 0)
        val signer = Ed25519Signer()
        signer.init(true, ed25519PrivateKey)
        signer.update(data, 0, data.size)
        return signer.generateSignature()
    }

    override suspend fun verify(data: ByteArray, signature: ByteArray, publicKey: ByteArray): Boolean {
        require(publicKey.size == 32) { "Public key must be 32 bytes" }
        require(signature.size == 64) { "Signature must be 64 bytes" }

        val ed25519PublicKey = Ed25519PublicKeyParameters(publicKey, 0)
        val verifier = Ed25519Signer()
        verifier.init(false, ed25519PublicKey)
        verifier.update(data, 0, data.size)
        return verifier.verifySignature(signature)
    }
}

/**
 * Get the JVM-specific Ed25519 crypto implementation.
 */
actual fun getEd25519Crypto(): Ed25519Crypto = JvmEd25519Crypto()
