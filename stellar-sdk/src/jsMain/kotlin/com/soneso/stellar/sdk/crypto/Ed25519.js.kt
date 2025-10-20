package com.soneso.stellar.sdk.crypto

import org.khronos.webgl.Uint8Array

/**
 * JavaScript implementation of Ed25519 cryptographic operations.
 *
 * ## Implementation Strategy
 *
 * This implementation uses libsodium.js for Ed25519 operations:
 *
 * - **Library**: libsodium-wrappers-sumo (0.7.13 via npm)
 * - **Algorithm**: Ed25519 via `crypto_sign_*` functions
 * - **Security**: Audited C library compiled to WebAssembly
 * - **Compatibility**: Works in all browsers and Node.js
 * - **Initialization**: Lazy - happens on first crypto operation
 *
 * All methods are suspend functions to properly handle libsodium initialization.
 *
 * ## Why This Approach?
 *
 * 1. **Self-contained**: SDK manages its own dependencies
 * 2. **Production-ready**: Proper error handling and initialization
 * 3. **Coroutine-friendly**: Natural suspend function API
 * 4. **Test-friendly**: Works in test environments without manual setup
 *
 * @see <a href="https://github.com/jedisct1/libsodium.js">libsodium.js</a>
 */
internal class JsEd25519Crypto : Ed25519Crypto {

    override val libraryName: String = "libsodium-wrappers-sumo"

    companion object {
        private const val SEED_BYTES = 32
        private const val PUBLIC_KEY_BYTES = 32
        private const val SECRET_KEY_BYTES = 64
        private const val SIGNATURE_BYTES = 64
    }

    /**
     * Generates a new random Ed25519 private key (32 bytes).
     *
     * Uses Web Crypto API's `crypto.getRandomValues()` for cryptographically
     * secure random number generation.
     */
    override suspend fun generatePrivateKey(): ByteArray {
        // We don't need libsodium for random generation - use Web Crypto API
        return try {
            val array = Uint8Array(SEED_BYTES)
            js("crypto.getRandomValues(array)")
            array.toByteArray()
        } catch (e: Throwable) {
            throw IllegalStateException("Failed to generate random private key: ${e.message}", e)
        }
    }

    /**
     * Derives the Ed25519 public key from a private key (seed).
     *
     * @param privateKey The 32-byte Ed25519 seed
     * @return The 32-byte Ed25519 public key
     */
    override suspend fun derivePublicKey(privateKey: ByteArray): ByteArray {
        require(privateKey.size == SEED_BYTES) { "Private key must be $SEED_BYTES bytes" }

        // Ensure libsodium is initialized
        LibsodiumInit.ensureInitialized()

        return try {
            val sodium = LibsodiumInit.getSodium()
            val seedArray = privateKey.toUint8Array()

            // Use libsodium to derive keypair from seed
            val result = js(
                """
                (function() {
                    var keypair = sodium.crypto_sign_seed_keypair(seedArray);
                    return keypair.publicKey;
                })()
                """
            ).unsafeCast<Uint8Array>()

            result.toByteArray()
        } catch (e: Throwable) {
            throw IllegalStateException("Failed to derive public key: ${e.message}", e)
        }
    }

    /**
     * Signs data using Ed25519.
     *
     * @param data The data to sign
     * @param privateKey The 32-byte Ed25519 seed
     * @return The 64-byte Ed25519 signature
     */
    override suspend fun sign(data: ByteArray, privateKey: ByteArray): ByteArray {
        require(privateKey.size == SEED_BYTES) { "Private key must be $SEED_BYTES bytes" }

        // Ensure libsodium is initialized
        LibsodiumInit.ensureInitialized()

        return try {
            val sodium = LibsodiumInit.getSodium()
            val dataArray = data.toUint8Array()
            val seedArray = privateKey.toUint8Array()

            // Derive keypair and sign
            val result = js(
                """
                (function() {
                    var keypair = sodium.crypto_sign_seed_keypair(seedArray);
                    var signature = sodium.crypto_sign_detached(dataArray, keypair.privateKey);
                    return signature;
                })()
                """
            ).unsafeCast<Uint8Array>()

            result.toByteArray()
        } catch (e: Throwable) {
            throw IllegalStateException("Failed to sign data: ${e.message}", e)
        }
    }

    /**
     * Verifies an Ed25519 signature.
     *
     * @param data The data that was signed
     * @param signature The 64-byte signature
     * @param publicKey The 32-byte Ed25519 public key
     * @return true if the signature is valid, false otherwise
     */
    override suspend fun verify(data: ByteArray, signature: ByteArray, publicKey: ByteArray): Boolean {
        require(publicKey.size == PUBLIC_KEY_BYTES) { "Public key must be $PUBLIC_KEY_BYTES bytes" }
        require(signature.size == SIGNATURE_BYTES) { "Signature must be $SIGNATURE_BYTES bytes" }

        // Ensure libsodium is initialized
        LibsodiumInit.ensureInitialized()

        return try {
            val sodium = LibsodiumInit.getSodium()
            val dataArray = data.toUint8Array()
            val signatureArray = signature.toUint8Array()
            val publicKeyArray = publicKey.toUint8Array()

            // Verify signature
            val result = js(
                """
                (function() {
                    try {
                        return sodium.crypto_sign_verify_detached(signatureArray, dataArray, publicKeyArray);
                    } catch (e) {
                        return false;
                    }
                })()
                """
            ).unsafeCast<Boolean>()

            result
        } catch (e: Throwable) {
            false
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

/**
 * Get the platform-specific Ed25519 crypto implementation.
 *
 * Returns the JavaScript implementation using libsodium.js.
 */
actual fun getEd25519Crypto(): Ed25519Crypto = JsEd25519Crypto()
