package com.soneso.stellar.sdk.unitTests.crypto

import com.soneso.stellar.sdk.crypto.getSha256Crypto
import kotlinx.coroutines.test.runTest
import kotlin.test.*

/**
 * Comprehensive unit tests for SHA-256 cryptographic hash operations.
 *
 * Tests cover:
 * - Hash output size (always 32 bytes)
 * - Deterministic hashing
 * - Known test vectors (NIST FIPS 180-4)
 * - Empty input
 * - Different inputs produce different hashes
 * - Large input hashing
 */
class Sha256CryptoTest {

    private val sha256 = getSha256Crypto()

    // ========== Library Info ==========

    @Test
    fun testLibraryNameIsNotBlank() {
        assertTrue(sha256.libraryName.isNotBlank(), "Library name should not be blank")
    }

    // ========== Output Size ==========

    @Test
    fun testHashReturns32Bytes() = runTest {
        val data = "hello".encodeToByteArray()
        val hash = sha256.hash(data)
        assertEquals(32, hash.size, "SHA-256 hash must be 32 bytes")
    }

    @Test
    fun testHashOfEmptyDataReturns32Bytes() = runTest {
        val hash = sha256.hash(ByteArray(0))
        assertEquals(32, hash.size, "SHA-256 hash of empty data must be 32 bytes")
    }

    // ========== Determinism ==========

    @Test
    fun testHashIsDeterministic() = runTest {
        val data = "deterministic".encodeToByteArray()
        val hash1 = sha256.hash(data)
        val hash2 = sha256.hash(data)
        assertTrue(hash1.contentEquals(hash2), "Same input should produce the same hash")
    }

    // ========== Known Test Vectors (NIST FIPS 180-4) ==========

    @Test
    fun testKnownVector_emptyString() = runTest {
        // SHA-256("") = e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855
        val expected = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
        val hash = sha256.hash(ByteArray(0))
        assertEquals(expected, hash.toHex(), "SHA-256 of empty string should match known vector")
    }

    @Test
    fun testKnownVector_abc() = runTest {
        // SHA-256("abc") = ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad
        val expected = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"
        val hash = sha256.hash("abc".encodeToByteArray())
        assertEquals(expected, hash.toHex(), "SHA-256 of 'abc' should match known vector")
    }

    @Test
    fun testKnownVector_abcdbcde() = runTest {
        // SHA-256("abcdbcdecdefdefgefghfghighijhijkijkljklmklmnlmnomnopnopq")
        // = 248d6a61d20638b8e5c026930c3e6039a33ce45964ff2167f6ecedd419db06c1
        val expected = "248d6a61d20638b8e5c026930c3e6039a33ce45964ff2167f6ecedd419db06c1"
        val input = "abcdbcdecdefdefgefghfghighijhijkijkljklmklmnlmnomnopnopq"
        val hash = sha256.hash(input.encodeToByteArray())
        assertEquals(expected, hash.toHex(), "SHA-256 should match known NIST vector")
    }

    @Test
    fun testKnownVector_longString() = runTest {
        // SHA-256("abcdefghbcdefghicdefghijdefghijkefghijklfghijklmghijklmnhijklmnoijklmnopjklmnopqklmnopqrlmnopqrsmnopqrstnopqrstu")
        // = cf5b16a778af8380036ce59e7b0492370b249b11e8f07a51afac45037afee9d1
        val expected = "cf5b16a778af8380036ce59e7b0492370b249b11e8f07a51afac45037afee9d1"
        val input = "abcdefghbcdefghicdefghijdefghijkefghijklfghijklmghijklmnhijklmnoijklmnopjklmnopqklmnopqrlmnopqrsmnopqrstnopqrstu"
        val hash = sha256.hash(input.encodeToByteArray())
        assertEquals(expected, hash.toHex(), "SHA-256 should match known NIST vector (long)")
    }

    // ========== Different Inputs ==========

    @Test
    fun testDifferentInputsProduceDifferentHashes() = runTest {
        val hash1 = sha256.hash("message1".encodeToByteArray())
        val hash2 = sha256.hash("message2".encodeToByteArray())
        assertFalse(hash1.contentEquals(hash2), "Different inputs should produce different hashes")
    }

    @Test
    fun testSingleBitDifference() = runTest {
        // Even a single bit difference should produce completely different hashes
        val data1 = byteArrayOf(0x00)
        val data2 = byteArrayOf(0x01)
        val hash1 = sha256.hash(data1)
        val hash2 = sha256.hash(data2)
        assertFalse(hash1.contentEquals(hash2), "Single bit difference should produce different hashes")
    }

    // ========== Large Input ==========

    @Test
    fun testHashOfLargeInput() = runTest {
        val largeData = ByteArray(100_000) { it.toByte() }
        val hash = sha256.hash(largeData)
        assertEquals(32, hash.size, "SHA-256 of large input must still be 32 bytes")

        // Hash again to verify determinism with large data
        val hash2 = sha256.hash(largeData)
        assertTrue(hash.contentEquals(hash2), "Large input hash should be deterministic")
    }

    // ========== Single Byte ==========

    @Test
    fun testHashOfSingleByte() = runTest {
        val hash = sha256.hash(byteArrayOf(0x00))
        assertEquals(32, hash.size)
        // SHA-256(0x00) = 6e340b9cffb37a989ca544e6bb780a2c78901d3fb33738768511a30617afa01d
        val expected = "6e340b9cffb37a989ca544e6bb780a2c78901d3fb33738768511a30617afa01d"
        assertEquals(expected, hash.toHex(), "SHA-256 of single zero byte should match known value")
    }

    // ========== Binary Data ==========

    @Test
    fun testHashOfBinaryData() = runTest {
        val binaryData = ByteArray(256) { it.toByte() }
        val hash = sha256.hash(binaryData)
        assertEquals(32, hash.size, "SHA-256 of binary data must be 32 bytes")
    }

    // ========== Helpers ==========

    private fun ByteArray.toHex(): String = joinToString("") {
        val hex = (it.toInt() and 0xFF).toString(16)
        if (hex.length == 1) "0$hex" else hex
    }
}
