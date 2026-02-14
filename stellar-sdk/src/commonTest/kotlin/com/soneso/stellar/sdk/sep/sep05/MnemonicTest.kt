/*
 * Copyright 2025 Soneso
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.soneso.stellar.sdk.sep.sep05

import com.soneso.stellar.sdk.sep.sep05.exceptions.InvalidChecksumException
import com.soneso.stellar.sdk.sep.sep05.exceptions.InvalidEntropyException
import com.soneso.stellar.sdk.sep.sep05.exceptions.InvalidMnemonicException
import com.soneso.stellar.sdk.sep.sep05.exceptions.InvalidPathException
import com.soneso.stellar.sdk.sep.sep05.exceptions.InvalidWordException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Unit tests for [Mnemonic] SEP-5 HD key derivation implementation.
 */
class MnemonicTest {

    // ========== Mnemonic Creation Tests ==========

    @Test
    fun testMnemonicCreation() = runTest {
        val phrase = "illness spike retreat truth genius clock brain pass fit cave bargain toe"
        val m = Mnemonic.from(phrase)
        assertNotNull(m)
        m.close()
    }

    @Test
    fun testMnemonicCreation_AutoDetectLanguage() = runTest {
        val phrase = "illness spike retreat truth genius clock brain pass fit cave bargain toe"
        val m = Mnemonic.from(phrase, language = null)
        assertNotNull(m)
        m.close()
    }

    @Test
    fun testMnemonicCreation_ExplicitEnglish() = runTest {
        val phrase = "illness spike retreat truth genius clock brain pass fit cave bargain toe"
        val m = Mnemonic.from(phrase, language = MnemonicLanguage.ENGLISH)
        assertNotNull(m)
        m.close()
    }

    @Test
    fun testMnemonicCreation_InvalidMnemonic_BadWords() = runTest {
        assertFailsWith<InvalidMnemonicException> {
            Mnemonic.from("invalid mnemonic words here this is not valid at all really")
        }
    }

    @Test
    fun testMnemonicCreation_InvalidMnemonic_WrongWordCount() = runTest {
        assertFailsWith<InvalidMnemonicException> {
            Mnemonic.from("abandon abandon abandon")
        }
    }

    @Test
    fun testMnemonicCreation_InvalidMnemonic_BadChecksum() = runTest {
        assertFailsWith<InvalidMnemonicException> {
            Mnemonic.from("abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon")
        }
    }

    @Test
    fun testMnemonicCreation_EmptyMnemonic() = runTest {
        assertFailsWith<InvalidMnemonicException> {
            Mnemonic.from("")
        }
    }

    @Test
    fun testMnemonicCreation_WithPassphrase() = runTest {
        val phrase = "cable spray genius state float twenty onion head street palace net private method loan turn phrase state blanket interest dry amazing dress blast tube"
        val m = Mnemonic.from(phrase, passphrase = "p4ssphr4se")
        assertNotNull(m)
        m.close()
    }

    @Test
    fun testMnemonicFromEntropy() = runTest {
        val entropy = HexCodec.decode("00000000000000000000000000000000")
        val m = Mnemonic.fromEntropy(entropy)
        assertNotNull(m)
        m.close()
    }

    @Test
    fun testMnemonicFromBip39HexSeed() = runTest {
        val seedHex = "e4a5a632e70943ae7f07659df1332160937fad82587216a4c64315a0fb39497ee4a01f76ddab4cba68147977f3a147b6ad584c41808e8238a07f6cc4b582f186"
        val m = Mnemonic.fromBip39HexSeed(seedHex)
        assertNotNull(m)
        m.close()
    }

    @Test
    fun testMnemonicFromBip39Seed() = runTest {
        val seedHex = "e4a5a632e70943ae7f07659df1332160937fad82587216a4c64315a0fb39497ee4a01f76ddab4cba68147977f3a147b6ad584c41808e8238a07f6cc4b582f186"
        val seed = HexCodec.decode(seedHex)
        val m = Mnemonic.fromBip39Seed(seed)
        assertNotNull(m)
        m.close()
    }

    @Test
    fun testPassphraseCreatesDistinctMnemonics() = runTest {
        val phrase = "illness spike retreat truth genius clock brain pass fit cave bargain toe"
        val mNoPass = Mnemonic.from(phrase, passphrase = "")
        val mWithPass = Mnemonic.from(phrase, passphrase = "test passphrase")

        // Different passphrases should produce different seeds
        assertTrue(!mNoPass.getBip39SeedHex().contentEquals(mWithPass.getBip39SeedHex()))

        mNoPass.close()
        mWithPass.close()
    }

    // ========== Key Derivation Tests ==========

    @Test
    fun testKeyDerivation_Index0() = runTest {
        val phrase = "illness spike retreat truth genius clock brain pass fit cave bargain toe"
        val m = Mnemonic.from(phrase)

        val keyPair = m.getKeyPair(0)
        assertEquals("GDRXE2BQUC3AZNPVFSCEZ76NJ3WWL25FYFK6RGZGIEKWE4SOOHSUJUJ6", keyPair.getAccountId())

        m.close()
    }

    @Test
    fun testKeyDerivation_MultipleIndices() = runTest {
        val phrase = "illness spike retreat truth genius clock brain pass fit cave bargain toe"
        val m = Mnemonic.from(phrase)

        val accounts = (0..9).map { m.getAccountId(it) }

        // All accounts should be unique
        assertEquals(10, accounts.toSet().size)

        m.close()
    }

    @Test
    fun testKeyDerivation_ConsistentResults() = runTest {
        val phrase = "illness spike retreat truth genius clock brain pass fit cave bargain toe"
        val m1 = Mnemonic.from(phrase)
        val m2 = Mnemonic.from(phrase)

        // Same mnemonic should produce same keys
        assertEquals(m1.getAccountId(0), m2.getAccountId(0))
        assertEquals(m1.getAccountId(5), m2.getAccountId(5))

        m1.close()
        m2.close()
    }

    @Test
    fun testKeyDerivation_NegativeIndex() = runTest {
        val phrase = "illness spike retreat truth genius clock brain pass fit cave bargain toe"
        val m = Mnemonic.from(phrase)

        assertFailsWith<IllegalArgumentException> {
            m.getKeyPair(-1)
        }

        m.close()
    }

    @Test
    fun testGetAccountId() = runTest {
        val phrase = "illness spike retreat truth genius clock brain pass fit cave bargain toe"
        val m = Mnemonic.from(phrase)

        val accountId = m.getAccountId(0)
        val keyPair = m.getKeyPair(0)

        assertEquals(keyPair.getAccountId(), accountId)

        m.close()
    }

    @Test
    fun testGetPublicKey() = runTest {
        val phrase = "illness spike retreat truth genius clock brain pass fit cave bargain toe"
        val m = Mnemonic.from(phrase)

        val publicKey = m.getPublicKey(0)
        val keyPair = m.getKeyPair(0)

        assertContentEquals(keyPair.getPublicKey(), publicKey)

        m.close()
    }

    @Test
    fun testGetPrivateKey() = runTest {
        val phrase = "illness spike retreat truth genius clock brain pass fit cave bargain toe"
        val m = Mnemonic.from(phrase)

        val privateKey = m.getPrivateKey(0)
        assertEquals(32, privateKey.size)

        m.close()
    }

    // ========== Seed Access Tests ==========

    @Test
    fun testGetBip39Seed() = runTest {
        val phrase = "illness spike retreat truth genius clock brain pass fit cave bargain toe"
        val m = Mnemonic.from(phrase)

        val seed = m.getBip39Seed()
        assertEquals(64, seed.size)

        m.close()
    }

    @Test
    fun testGetBip39SeedHex() = runTest {
        val phrase = "illness spike retreat truth genius clock brain pass fit cave bargain toe"
        val m = Mnemonic.from(phrase)

        val seedHex = m.getBip39SeedHex()
        assertEquals(128, seedHex.length)
        assertEquals(
            "e4a5a632e70943ae7f07659df1332160937fad82587216a4c64315a0fb39497ee4a01f76ddab4cba68147977f3a147b6ad584c41808e8238a07f6cc4b582f186",
            seedHex
        )

        m.close()
    }

    @Test
    fun testGetBip39Seed_ReturnsDefensiveCopy() = runTest {
        val phrase = "illness spike retreat truth genius clock brain pass fit cave bargain toe"
        val m = Mnemonic.from(phrase)

        val seed1 = m.getBip39Seed()
        val seed2 = m.getBip39Seed()

        // Should be equal but not same instance
        assertContentEquals(seed1, seed2)

        // Modifying one should not affect the other
        seed1[0] = 0xFF.toByte()
        assertTrue(seed1[0] != seed2[0])

        m.close()
    }

    // ========== Cleanup Tests ==========

    @Test
    fun testClose_ZerosSeed() = runTest {
        val phrase = "illness spike retreat truth genius clock brain pass fit cave bargain toe"
        val m = Mnemonic.from(phrase)

        val seedBefore = m.getBip39Seed()
        assertTrue(seedBefore.any { it != 0.toByte() })

        m.close()

        val seedAfter = m.getBip39Seed()
        assertEquals(0, seedAfter.size)
    }

    // ========== Mnemonic Generation Tests ==========

    @Test
    fun testGenerate12WordsMnemonic() = runTest {
        val phrase = Mnemonic.generate12WordsMnemonic()
        val words = phrase.split(" ")
        assertEquals(12, words.size)
        assertTrue(Mnemonic.validate(phrase))
    }

    @Test
    fun testGenerate15WordsMnemonic() = runTest {
        val phrase = Mnemonic.generate15WordsMnemonic()
        val words = phrase.split(" ")
        assertEquals(15, words.size)
        assertTrue(Mnemonic.validate(phrase))
    }

    @Test
    fun testGenerate18WordsMnemonic() = runTest {
        val phrase = Mnemonic.generate18WordsMnemonic()
        val words = phrase.split(" ")
        assertEquals(18, words.size)
        assertTrue(Mnemonic.validate(phrase))
    }

    @Test
    fun testGenerate21WordsMnemonic() = runTest {
        val phrase = Mnemonic.generate21WordsMnemonic()
        val words = phrase.split(" ")
        assertEquals(21, words.size)
        assertTrue(Mnemonic.validate(phrase))
    }

    @Test
    fun testGenerate24WordsMnemonic() = runTest {
        val phrase = Mnemonic.generate24WordsMnemonic()
        val words = phrase.split(" ")
        assertEquals(24, words.size)
        assertTrue(Mnemonic.validate(phrase))
    }

    @Test
    fun testGeneratedMnemonicsAreUnique() = runTest {
        val mnemonics = (1..10).map { Mnemonic.generate24WordsMnemonic() }
        assertEquals(10, mnemonics.toSet().size)
    }

    // ========== Validation Tests ==========

    @Test
    fun testValidate_ValidMnemonic() = runTest {
        assertTrue(Mnemonic.validate("illness spike retreat truth genius clock brain pass fit cave bargain toe"))
    }

    @Test
    fun testValidate_InvalidMnemonic() = runTest {
        assertTrue(!Mnemonic.validate("invalid mnemonic"))
    }

    @Test
    fun testDetectLanguage_English() {
        val language = Mnemonic.detectLanguage("illness spike retreat truth genius clock brain pass fit cave bargain toe")
        assertEquals(MnemonicLanguage.ENGLISH, language)
    }

    @Test
    fun testDetectLanguage_Unknown() {
        val language = Mnemonic.detectLanguage("foo bar baz qux")
        assertEquals(null, language)
    }

    // ========== Invalid Path Tests ==========

    @Test
    fun testDerivePath_InvalidFormat() = runTest {
        val phrase = "illness spike retreat truth genius clock brain pass fit cave bargain toe"
        val m = Mnemonic.from(phrase)

        assertFailsWith<InvalidPathException> {
            m.derivePath("invalid/path/format")
        }

        m.close()
    }

    @Test
    fun testDerivePath_InvalidIndex() = runTest {
        val phrase = "illness spike retreat truth genius clock brain pass fit cave bargain toe"
        val m = Mnemonic.from(phrase)

        assertFailsWith<InvalidPathException> {
            m.derivePath("m/44'/abc'/0'")
        }

        m.close()
    }

    @Test
    fun testDerivePath_EmptyPath() = runTest {
        val phrase = "illness spike retreat truth genius clock brain pass fit cave bargain toe"
        val m = Mnemonic.from(phrase)

        assertFailsWith<InvalidPathException> {
            m.derivePath("")
        }

        m.close()
    }

    // ========== Exception Tests ==========

    @Test
    fun testInvalidPathException_ToString() {
        val exception = InvalidPathException("bad/path", "Test message")
        assertTrue(exception.toString().contains("bad/path"))
        assertEquals("bad/path", exception.path)
    }

    @Test
    fun testInvalidChecksumException_ToString() {
        val exception = InvalidChecksumException("Checksum failed")
        assertTrue(exception.toString().contains("checksum"))
    }

    @Test
    fun testInvalidEntropyException_ToString() {
        val exception = InvalidEntropyException("Invalid size")
        assertTrue(exception.toString().contains("entropy"))
    }

    @Test
    fun testInvalidMnemonicException_ToString() {
        val exception = InvalidMnemonicException("Invalid mnemonic")
        assertTrue(exception.toString().contains("mnemonic"))
    }

    @Test
    fun testInvalidWordException_ToString() {
        val exception = InvalidWordException("badword", MnemonicLanguage.ENGLISH)
        assertTrue(exception.toString().contains("badword"))
        assertTrue(exception.toString().contains("ENGLISH"))
        assertEquals("badword", exception.word)
        assertEquals(MnemonicLanguage.ENGLISH, exception.language)
    }

    // ========== MnemonicStrength Tests ==========

    @Test
    fun testMnemonicStrength_FromWordCount_Valid() {
        assertEquals(MnemonicStrength.BITS_128, MnemonicStrength.fromWordCount(12))
        assertEquals(MnemonicStrength.BITS_160, MnemonicStrength.fromWordCount(15))
        assertEquals(MnemonicStrength.BITS_192, MnemonicStrength.fromWordCount(18))
        assertEquals(MnemonicStrength.BITS_224, MnemonicStrength.fromWordCount(21))
        assertEquals(MnemonicStrength.BITS_256, MnemonicStrength.fromWordCount(24))
    }

    @Test
    fun testMnemonicStrength_FromWordCount_Invalid() {
        assertNull(MnemonicStrength.fromWordCount(10))
        assertNull(MnemonicStrength.fromWordCount(13))
        assertNull(MnemonicStrength.fromWordCount(0))
    }

    @Test
    fun testMnemonicStrength_FromEntropyBits_Valid() {
        assertEquals(MnemonicStrength.BITS_128, MnemonicStrength.fromEntropyBits(128))
        assertEquals(MnemonicStrength.BITS_160, MnemonicStrength.fromEntropyBits(160))
        assertEquals(MnemonicStrength.BITS_192, MnemonicStrength.fromEntropyBits(192))
        assertEquals(MnemonicStrength.BITS_224, MnemonicStrength.fromEntropyBits(224))
        assertEquals(MnemonicStrength.BITS_256, MnemonicStrength.fromEntropyBits(256))
    }

    @Test
    fun testMnemonicStrength_FromEntropyBits_Invalid() {
        assertNull(MnemonicStrength.fromEntropyBits(100))
        assertNull(MnemonicStrength.fromEntropyBits(129))
        assertNull(MnemonicStrength.fromEntropyBits(0))
    }

    @Test
    fun testMnemonicStrength_Properties() {
        assertEquals(128, MnemonicStrength.BITS_128.entropyBits)
        assertEquals(12, MnemonicStrength.BITS_128.wordCount)
        assertEquals(256, MnemonicStrength.BITS_256.entropyBits)
        assertEquals(24, MnemonicStrength.BITS_256.wordCount)
    }
}
