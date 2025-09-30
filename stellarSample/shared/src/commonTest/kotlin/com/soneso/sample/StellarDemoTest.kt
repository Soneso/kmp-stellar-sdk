package com.soneso.sample

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertEquals

class StellarDemoTest {

    @Test
    fun testRandomKeyPairGeneration() {
        val demo = StellarDemo()
        val keypair = demo.generateRandomKeyPair()

        assertTrue(keypair.accountId.startsWith("G"))
        assertEquals(56, keypair.accountId.length)
        assertNotNull(keypair.secretSeed)
        assertTrue(keypair.secretSeed!!.startsWith("S"))
        assertTrue(keypair.canSign)
        assertTrue(keypair.cryptoLibrary.isNotEmpty())
    }

    @Test
    fun testFromSecretSeed() {
        val demo = StellarDemo()
        val seed = "SDJHRQF4GCMIIKAAAQ6IHY42X73FQFLHUULAPSKKD4DFDM7UXWWCRHBE"
        val result = demo.createFromSeed(seed)

        assertTrue(result.isSuccess)
        val keypair = result.getOrThrow()
        assertNotNull(keypair.secretSeed)
        assertTrue(keypair.canSign)
    }

    @Test
    fun testFromAccountId() {
        val demo = StellarDemo()
        val accountId = "GCZHXL5HXQX5ABDM26LHYRCQZ5OJFHLOPLZX47WEBP3V2PF5AVFK2A5D"
        val result = demo.createFromAccountId(accountId)

        assertTrue(result.isSuccess)
        val keypair = result.getOrThrow()
        assertEquals(accountId, keypair.accountId)
        assertNull(keypair.secretSeed)
        assertFalse(keypair.canSign)
    }

    @Test
    fun testSigning() {
        val demo = StellarDemo()
        demo.generateRandomKeyPair()

        val result = demo.signMessage("Hello Stellar!")
        assertTrue(result.isSuccess)

        val signature = result.getOrThrow()
        assertNotNull(signature.signature)
        assertNotNull(signature.publicKey)
        assertEquals("Hello Stellar!", signature.message)
    }

    @Test
    fun testVerification() {
        val demo = StellarDemo()
        demo.generateRandomKeyPair()

        val signResult = demo.signMessage("Test message")
        assertTrue(signResult.isSuccess)

        val signature = signResult.getOrThrow()
        val isValid = demo.verifySignature(signature)
        assertTrue(isValid)
    }

    @Test
    fun testInvalidSeed() {
        val demo = StellarDemo()
        val result = demo.createFromSeed("INVALID_SEED")

        assertTrue(result.isFailure)
    }

    @Test
    fun testPublicKeyOnlyCannotSign() {
        val demo = StellarDemo()
        val accountId = "GCZHXL5HXQX5ABDM26LHYRCQZ5OJFHLOPLZX47WEBP3V2PF5AVFK2A5D"
        demo.createFromAccountId(accountId)

        val result = demo.signMessage("Test")
        assertTrue(result.isFailure)
    }

    @Test
    fun testRunTestSuite() {
        val demo = StellarDemo()
        val results = demo.runTestSuite()

        assertEquals(8, results.size)

        // Check that most tests pass (at least 7 out of 8)
        val passedCount = results.count { it.passed }
        assertTrue(passedCount >= 7, "Expected at least 7 tests to pass, got $passedCount")
    }
}
