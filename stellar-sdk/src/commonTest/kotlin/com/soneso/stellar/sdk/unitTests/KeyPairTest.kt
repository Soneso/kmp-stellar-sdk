package com.soneso.stellar.sdk.unitTests

import com.soneso.stellar.sdk.*
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class KeyPairTest {

    @Test
    fun testRandomKeyPairGeneration() = runTest {
        val keypair1 = KeyPair.random()
        val keypair2 = KeyPair.random()

        // Verify we can sign
        assertTrue(keypair1.canSign())
        assertTrue(keypair2.canSign())

        // Verify keys are different
        assertFalse(keypair1.getPublicKey().contentEquals(keypair2.getPublicKey()))
        assertNotNull(keypair1.getSecretSeed())
        assertNotNull(keypair2.getSecretSeed())
    }

    @Test
    fun testFromSecretSeed() = runTest {
        val seed = "SDJHRQF4GCMIIKAAAQ6IHY42X73FQFLHUULAPSKKD4DFDM7UXWWCRHBE"
        val keypair = KeyPair.fromSecretSeed(seed)

        assertTrue(keypair.canSign())
        assertEquals(
            "GCZHXL5HXQX5ABDM26LHYRCQZ5OJFHLOPLZX47WEBP3V2PF5AVFK2A5D",
            keypair.getAccountId()
        )

        val secretSeed = keypair.getSecretSeed()
        assertNotNull(secretSeed)
        assertEquals(seed, secretSeed.concatToString())
    }

    @Test
    fun testFromSecretSeedCharArray() = runTest {
        val seed = "SDJHRQF4GCMIIKAAAQ6IHY42X73FQFLHUULAPSKKD4DFDM7UXWWCRHBE".toCharArray()
        val keypair = KeyPair.fromSecretSeed(seed)

        assertTrue(keypair.canSign())
        assertEquals(
            "GCZHXL5HXQX5ABDM26LHYRCQZ5OJFHLOPLZX47WEBP3V2PF5AVFK2A5D",
            keypair.getAccountId()
        )
    }

    @Test
    fun testFromAccountId() {
        val accountId = "GCZHXL5HXQX5ABDM26LHYRCQZ5OJFHLOPLZX47WEBP3V2PF5AVFK2A5D"
        val keypair = KeyPair.fromAccountId(accountId)

        assertFalse(keypair.canSign())
        assertEquals(accountId, keypair.getAccountId())
        assertNull(keypair.getSecretSeed())
    }

    @Test
    fun testFromPublicKey() {
        val accountId = "GCZHXL5HXQX5ABDM26LHYRCQZ5OJFHLOPLZX47WEBP3V2PF5AVFK2A5D"
        val publicKeyBytes = StrKey.decodeEd25519PublicKey(accountId)

        val keypair = KeyPair.fromPublicKey(publicKeyBytes)

        assertFalse(keypair.canSign())
        assertEquals(accountId, keypair.getAccountId())
        assertTrue(keypair.getPublicKey().contentEquals(publicKeyBytes))
    }

    @Test
    fun testSignAndVerify() = runTest {
        val keypair = KeyPair.random()
        val data = "test data to sign".encodeToByteArray()

        val signature = keypair.sign(data)

        // Signature should be 64 bytes (Ed25519)
        assertEquals(64, signature.size)

        // Verify signature with same keypair
        assertTrue(keypair.verify(data, signature))

        // Verify with different data should fail
        val differentData = "different data".encodeToByteArray()
        assertFalse(keypair.verify(differentData, signature))

        // Create a public-only keypair from the account ID
        val publicKeypair = KeyPair.fromAccountId(keypair.getAccountId())

        // Should be able to verify with public key only
        assertTrue(publicKeypair.verify(data, signature))
    }

    @Test
    fun testCannotSignWithoutPrivateKey() = runTest {
        val accountId = "GCZHXL5HXQX5ABDM26LHYRCQZ5OJFHLOPLZX47WEBP3V2PF5AVFK2A5D"
        val keypair = KeyPair.fromAccountId(accountId)

        assertFalse(keypair.canSign())

        val data = "test data".encodeToByteArray()
        assertFailsWith<IllegalStateException> {
            keypair.sign(data)
        }
    }

    @Test
    fun testEquals() = runTest {
        val seed = "SDJHRQF4GCMIIKAAAQ6IHY42X73FQFLHUULAPSKKD4DFDM7UXWWCRHBE"
        val keypair1 = KeyPair.fromSecretSeed(seed)
        val keypair2 = KeyPair.fromSecretSeed(seed)

        assertEquals(keypair1, keypair2)
        assertEquals(keypair1.hashCode(), keypair2.hashCode())

        val differentKeypair = KeyPair.random()
        assertNotEquals(keypair1, differentKeypair)
    }

    @Test
    fun testPublicOnlyEquality() {
        val accountId = "GCZHXL5HXQX5ABDM26LHYRCQZ5OJFHLOPLZX47WEBP3V2PF5AVFK2A5D"
        val keypair1 = KeyPair.fromAccountId(accountId)
        val keypair2 = KeyPair.fromAccountId(accountId)

        assertEquals(keypair1, keypair2)
        assertEquals(keypair1.hashCode(), keypair2.hashCode())
    }

    @Test
    fun testInvalidSecretSeed() = runTest {
        assertFailsWith<IllegalArgumentException> {
            KeyPair.fromSecretSeed("INVALIDKEYSEED")
        }
    }

    @Test
    fun testInvalidAccountId() {
        assertFailsWith<IllegalArgumentException> {
            KeyPair.fromAccountId("INVALIDACCOUNTID")
        }
    }

    @Test
    fun testInvalidPublicKeySize() {
        val tooShort = byteArrayOf(1, 2, 3)
        assertFailsWith<IllegalArgumentException> {
            KeyPair.fromPublicKey(tooShort)
        }
    }

    @Test
    fun testInvalidSecretSeedSize() = runTest {
        val tooShort = byteArrayOf(1, 2, 3)
        assertFailsWith<IllegalArgumentException> {
            KeyPair.fromSecretSeed(tooShort)
        }
    }

    @Test
    fun testGetPublicKeyReturnsCopy() = runTest {
        val keypair = KeyPair.random()
        val publicKey1 = keypair.getPublicKey()
        val publicKey2 = keypair.getPublicKey()

        // Should return copies, not same instance
        assertTrue(publicKey1.contentEquals(publicKey2))
        assertFalse(publicKey1 === publicKey2)

        // Modifying returned array shouldn't affect keypair
        val originalByte = publicKey1[0]
        publicKey1[0] = (originalByte.toInt() xor 0xFF).toByte()  // Flip all bits to guarantee change
        val publicKey3 = keypair.getPublicKey()
        assertFalse(publicKey1.contentEquals(publicKey3))
    }
}
