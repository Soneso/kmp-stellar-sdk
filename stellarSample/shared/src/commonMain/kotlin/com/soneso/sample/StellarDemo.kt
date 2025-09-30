package com.soneso.sample

import com.stellar.sdk.KeyPair

/**
 * Shared business logic demonstrating Stellar SDK usage across all platforms.
 * This class contains all the core functionality, while platform-specific UIs
 * (SwiftUI, Compose, Kotlin/JS) handle the presentation layer.
 */
class StellarDemo {
    private var currentKeyPair: KeyPair? = null

    /**
     * Generate a random keypair using the platform's crypto library.
     */
    fun generateRandomKeyPair(): KeyPairInfo {
        val keypair = KeyPair.random()
        currentKeyPair = keypair

        return KeyPairInfo(
            accountId = keypair.getAccountId(),
            secretSeed = keypair.getSecretSeed()?.concatToString(),
            canSign = keypair.canSign(),
            cryptoLibrary = KeyPair.getCryptoLibraryName()
        )
    }

    /**
     * Create a keypair from a secret seed string.
     */
    fun createFromSeed(seed: String): Result<KeyPairInfo> {
        return try {
            val keypair = KeyPair.fromSecretSeed(seed)
            currentKeyPair = keypair

            Result.success(KeyPairInfo(
                accountId = keypair.getAccountId(),
                secretSeed = keypair.getSecretSeed()?.concatToString(),
                canSign = keypair.canSign(),
                cryptoLibrary = KeyPair.getCryptoLibraryName()
            ))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Create a keypair from an account ID (public key only).
     */
    fun createFromAccountId(accountId: String): Result<KeyPairInfo> {
        return try {
            val keypair = KeyPair.fromAccountId(accountId)
            currentKeyPair = keypair

            Result.success(KeyPairInfo(
                accountId = keypair.getAccountId(),
                secretSeed = null, // No secret seed for public-only keypairs
                canSign = keypair.canSign(),
                cryptoLibrary = KeyPair.getCryptoLibraryName()
            ))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Sign a message with the current keypair.
     */
    fun signMessage(message: String): Result<SignatureResult> {
        val kp = currentKeyPair
        if (kp == null) {
            return Result.failure(IllegalStateException("No keypair available"))
        }

        if (!kp.canSign()) {
            return Result.failure(IllegalStateException("Keypair cannot sign (no private key)"))
        }

        return try {
            val data = message.encodeToByteArray()
            val signature = kp.sign(data)

            Result.success(SignatureResult(
                signature = signature,
                publicKey = kp.getPublicKey(),
                message = message
            ))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Verify a signature against a message.
     */
    fun verifySignature(signature: SignatureResult): Boolean {
        val kp = currentKeyPair ?: return false
        val data = signature.message.encodeToByteArray()
        return kp.verify(data, signature.signature)
    }

    /**
     * Run the complete test suite.
     */
    fun runTestSuite(): List<TestResult> {
        return listOf(
            testRandomGeneration(),
            testFromSeed(),
            testFromAccountId(),
            testSigning(),
            testVerification(),
            testInvalidSeed(),
            testPublicKeyOnly(),
            testCryptoLibrary()
        )
    }

    private fun testRandomGeneration(): TestResult {
        val startTime = currentTimeMillis()
        return try {
            val info = generateRandomKeyPair()
            val duration = currentTimeMillis() - startTime

            if (info.accountId.startsWith("G") && info.accountId.length == 56 &&
                info.secretSeed?.startsWith("S") == true && info.canSign) {
                TestResult(
                    name = "Random KeyPair Generation",
                    passed = true,
                    message = "Generated keypair with account ${info.accountId.take(8)}...",
                    duration = duration
                )
            } else {
                TestResult(
                    name = "Random KeyPair Generation",
                    passed = false,
                    message = "Invalid keypair format",
                    duration = duration
                )
            }
        } catch (e: Exception) {
            TestResult(
                name = "Random KeyPair Generation",
                passed = false,
                message = "Error: ${e.message}",
                duration = currentTimeMillis() - startTime
            )
        }
    }

    private fun testFromSeed(): TestResult {
        val startTime = currentTimeMillis()
        val testSeed = "SDJHRQF4GCMIIKAAAQ6IHY42X73FQFLHUULAPSKKD4DFDM7UXWWCRHBE"
        return try {
            val result = createFromSeed(testSeed)
            val duration = currentTimeMillis() - startTime

            if (result.isSuccess) {
                val info = result.getOrThrow()
                TestResult(
                    name = "KeyPair from Secret Seed",
                    passed = true,
                    message = "Created keypair: ${info.accountId.take(8)}...",
                    duration = duration
                )
            } else {
                TestResult(
                    name = "KeyPair from Secret Seed",
                    passed = false,
                    message = "Failed: ${result.exceptionOrNull()?.message}",
                    duration = duration
                )
            }
        } catch (e: Exception) {
            TestResult(
                name = "KeyPair from Secret Seed",
                passed = false,
                message = "Error: ${e.message}",
                duration = currentTimeMillis() - startTime
            )
        }
    }

    private fun testFromAccountId(): TestResult {
        val startTime = currentTimeMillis()
        val testAccountId = "GCZHXL5HXQX5ABDM26LHYRCQZ5OJFHLOPLZX47WEBP3V2PF5AVFK2A5D"
        return try {
            val result = createFromAccountId(testAccountId)
            val duration = currentTimeMillis() - startTime

            if (result.isSuccess) {
                val info = result.getOrThrow()
                TestResult(
                    name = "KeyPair from Account ID",
                    passed = true,
                    message = "Created public-only keypair",
                    duration = duration
                )
            } else {
                TestResult(
                    name = "KeyPair from Account ID",
                    passed = false,
                    message = "Failed: ${result.exceptionOrNull()?.message}",
                    duration = duration
                )
            }
        } catch (e: Exception) {
            TestResult(
                name = "KeyPair from Account ID",
                passed = false,
                message = "Error: ${e.message}",
                duration = currentTimeMillis() - startTime
            )
        }
    }

    private fun testSigning(): TestResult {
        val startTime = currentTimeMillis()
        return try {
            generateRandomKeyPair() // Ensure we have a keypair that can sign
            val result = signMessage("Hello Stellar!")
            val duration = currentTimeMillis() - startTime

            if (result.isSuccess) {
                TestResult(
                    name = "Message Signing",
                    passed = true,
                    message = "Signed message successfully",
                    duration = duration
                )
            } else {
                TestResult(
                    name = "Message Signing",
                    passed = false,
                    message = "Failed: ${result.exceptionOrNull()?.message}",
                    duration = duration
                )
            }
        } catch (e: Exception) {
            TestResult(
                name = "Message Signing",
                passed = false,
                message = "Error: ${e.message}",
                duration = currentTimeMillis() - startTime
            )
        }
    }

    private fun testVerification(): TestResult {
        val startTime = currentTimeMillis()
        return try {
            generateRandomKeyPair()
            val signResult = signMessage("Test verification")
            val duration = currentTimeMillis() - startTime

            if (signResult.isSuccess) {
                val signature = signResult.getOrThrow()
                val isValid = verifySignature(signature)

                if (isValid) {
                    TestResult(
                        name = "Signature Verification",
                        passed = true,
                        message = "Signature verified successfully",
                        duration = duration
                    )
                } else {
                    TestResult(
                        name = "Signature Verification",
                        passed = false,
                        message = "Signature verification failed",
                        duration = duration
                    )
                }
            } else {
                TestResult(
                    name = "Signature Verification",
                    passed = false,
                    message = "Could not create signature to verify",
                    duration = duration
                )
            }
        } catch (e: Exception) {
            TestResult(
                name = "Signature Verification",
                passed = false,
                message = "Error: ${e.message}",
                duration = currentTimeMillis() - startTime
            )
        }
    }

    private fun testInvalidSeed(): TestResult {
        val startTime = currentTimeMillis()
        return try {
            val result = createFromSeed("INVALID_SEED")
            val duration = currentTimeMillis() - startTime

            if (result.isFailure) {
                TestResult(
                    name = "Invalid Secret Seed Handling",
                    passed = true,
                    message = "Correctly rejected invalid seed",
                    duration = duration
                )
            } else {
                TestResult(
                    name = "Invalid Secret Seed Handling",
                    passed = false,
                    message = "Should have rejected invalid seed",
                    duration = duration
                )
            }
        } catch (e: Exception) {
            TestResult(
                name = "Invalid Secret Seed Handling",
                passed = false,
                message = "Error: ${e.message}",
                duration = currentTimeMillis() - startTime
            )
        }
    }

    private fun testPublicKeyOnly(): TestResult {
        val startTime = currentTimeMillis()
        val testAccountId = "GCZHXL5HXQX5ABDM26LHYRCQZ5OJFHLOPLZX47WEBP3V2PF5AVFK2A5D"
        return try {
            val result = createFromAccountId(testAccountId)
            val duration = currentTimeMillis() - startTime

            if (result.isSuccess) {
                val info = result.getOrThrow()
                if (!info.canSign && info.secretSeed == null) {
                    TestResult(
                        name = "Public Key Only KeyPair",
                        passed = true,
                        message = "Public-only keypair cannot sign (correct)",
                        duration = duration
                    )
                } else {
                    TestResult(
                        name = "Public Key Only KeyPair",
                        passed = false,
                        message = "Public-only keypair should not be able to sign",
                        duration = duration
                    )
                }
            } else {
                TestResult(
                    name = "Public Key Only KeyPair",
                    passed = false,
                    message = "Failed: ${result.exceptionOrNull()?.message}",
                    duration = duration
                )
            }
        } catch (e: Exception) {
            TestResult(
                name = "Public Key Only KeyPair",
                passed = false,
                message = "Error: ${e.message}",
                duration = currentTimeMillis() - startTime
            )
        }
    }

    private fun testCryptoLibrary(): TestResult {
        val startTime = currentTimeMillis()
        return try {
            val libName = KeyPair.getCryptoLibraryName()
            val duration = currentTimeMillis() - startTime

            if (libName.isNotEmpty()) {
                TestResult(
                    name = "Crypto Library Detection",
                    passed = true,
                    message = "Using: $libName",
                    duration = duration
                )
            } else {
                TestResult(
                    name = "Crypto Library Detection",
                    passed = false,
                    message = "Library name is empty",
                    duration = duration
                )
            }
        } catch (e: Exception) {
            TestResult(
                name = "Crypto Library Detection",
                passed = false,
                message = "Error: ${e.message}",
                duration = currentTimeMillis() - startTime
            )
        }
    }

    private fun currentTimeMillis(): Long {
        // Platform-specific implementation will be provided via expect/actual
        return 0L // Placeholder for now
    }
}

/**
 * Information about a Stellar keypair.
 */
data class KeyPairInfo(
    val accountId: String,
    val secretSeed: String?,
    val canSign: Boolean,
    val cryptoLibrary: String
)

/**
 * Result of signing a message.
 */
data class SignatureResult(
    val signature: ByteArray,
    val publicKey: ByteArray,
    val message: String
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as SignatureResult

        if (!signature.contentEquals(other.signature)) return false
        if (!publicKey.contentEquals(other.publicKey)) return false
        if (message != other.message) return false

        return true
    }

    override fun hashCode(): Int {
        var result = signature.contentHashCode()
        result = 31 * result + publicKey.contentHashCode()
        result = 31 * result + message.hashCode()
        return result
    }
}

/**
 * Result of a test execution.
 */
data class TestResult(
    val name: String,
    val passed: Boolean,
    val message: String,
    val duration: Long
)
