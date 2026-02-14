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
import com.soneso.stellar.sdk.sep.sep05.exceptions.InvalidWordException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Unit tests for [MnemonicUtils] BIP-39 mnemonic operations.
 *
 * Tests cover:
 * - Mnemonic generation for all word lengths
 * - Mnemonic validation
 * - Entropy to mnemonic conversion and back
 * - Language detection
 * - Seed derivation
 */
class MnemonicUtilsTest {

    // ========== Generation Tests ==========

    @Test
    fun testGenerate12WordMnemonic() = runTest {
        val mnemonic = MnemonicUtils.generateMnemonic(MnemonicStrength.BITS_128)
        val words = mnemonic.split(" ")
        assertEquals(12, words.size, "12-word mnemonic should have 12 words")
        assertTrue(MnemonicUtils.validateMnemonic(mnemonic), "Generated mnemonic should be valid")
    }

    @Test
    fun testGenerate15WordMnemonic() = runTest {
        val mnemonic = MnemonicUtils.generateMnemonic(MnemonicStrength.BITS_160)
        val words = mnemonic.split(" ")
        assertEquals(15, words.size, "15-word mnemonic should have 15 words")
        assertTrue(MnemonicUtils.validateMnemonic(mnemonic), "Generated mnemonic should be valid")
    }

    @Test
    fun testGenerate18WordMnemonic() = runTest {
        val mnemonic = MnemonicUtils.generateMnemonic(MnemonicStrength.BITS_192)
        val words = mnemonic.split(" ")
        assertEquals(18, words.size, "18-word mnemonic should have 18 words")
        assertTrue(MnemonicUtils.validateMnemonic(mnemonic), "Generated mnemonic should be valid")
    }

    @Test
    fun testGenerate21WordMnemonic() = runTest {
        val mnemonic = MnemonicUtils.generateMnemonic(MnemonicStrength.BITS_224)
        val words = mnemonic.split(" ")
        assertEquals(21, words.size, "21-word mnemonic should have 21 words")
        assertTrue(MnemonicUtils.validateMnemonic(mnemonic), "Generated mnemonic should be valid")
    }

    @Test
    fun testGenerate24WordMnemonic() = runTest {
        val mnemonic = MnemonicUtils.generateMnemonic(MnemonicStrength.BITS_256)
        val words = mnemonic.split(" ")
        assertEquals(24, words.size, "24-word mnemonic should have 24 words")
        assertTrue(MnemonicUtils.validateMnemonic(mnemonic), "Generated mnemonic should be valid")
    }

    @Test
    fun testGenerateDefaultStrength() = runTest {
        // Default strength should be 256 bits (24 words)
        val mnemonic = MnemonicUtils.generateMnemonic()
        val words = mnemonic.split(" ")
        assertEquals(24, words.size, "Default mnemonic should be 24 words")
    }

    @Test
    fun testGeneratedMnemonicsAreUnique() = runTest {
        val mnemonics = (1..10).map { MnemonicUtils.generateMnemonic() }
        val uniqueMnemonics = mnemonics.toSet()
        assertEquals(10, uniqueMnemonics.size, "All generated mnemonics should be unique")
    }

    @Test
    fun testGenerateAllLanguages() = runTest {
        for (language in MnemonicLanguage.entries) {
            val mnemonic = MnemonicUtils.generateMnemonic(
                strength = MnemonicStrength.BITS_128,
                language = language
            )
            val words = mnemonic.split(" ")
            assertEquals(12, words.size, "Mnemonic for $language should have 12 words")
            assertTrue(
                MnemonicUtils.validateMnemonic(mnemonic, language),
                "Generated mnemonic for $language should be valid"
            )
        }
    }

    // ========== Validation Tests ==========

    @Test
    fun testValidateMnemonic_Valid12Words() = runTest {
        val valid = MnemonicUtils.validateMnemonic(
            "illness spike retreat truth genius clock brain pass fit cave bargain toe"
        )
        assertTrue(valid, "Valid 12-word mnemonic should pass validation")
    }

    @Test
    fun testValidateMnemonic_Valid24Words() = runTest {
        val valid = MnemonicUtils.validateMnemonic(
            "bench hurt jump file august wise shallow faculty impulse spring exact slush thunder author capable act festival slice deposit sauce coconut afford frown better"
        )
        assertTrue(valid, "Valid 24-word mnemonic should pass validation")
    }

    @Test
    fun testValidateMnemonic_InvalidWordCount_TooFew() = runTest {
        assertFalse(MnemonicUtils.validateMnemonic("abandon"))
        assertFalse(MnemonicUtils.validateMnemonic("abandon abandon abandon"))
        assertFalse(MnemonicUtils.validateMnemonic("abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon")) // 11 words
    }

    @Test
    fun testValidateMnemonic_InvalidWordCount_TooMany() = runTest {
        val tooMany = "abandon ".repeat(25).trim()
        assertFalse(MnemonicUtils.validateMnemonic(tooMany))
    }

    @Test
    fun testValidateMnemonic_InvalidWordCount_NotMultipleOf3() = runTest {
        // 13 words - not a valid count
        assertFalse(MnemonicUtils.validateMnemonic("abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon"))
        // 14 words
        assertFalse(MnemonicUtils.validateMnemonic("abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon"))
    }

    @Test
    fun testValidateMnemonic_InvalidWord() = runTest {
        val invalid = MnemonicUtils.validateMnemonic(
            "invalid spike retreat truth genius clock brain pass fit cave bargain toe"
        )
        assertFalse(invalid, "Mnemonic with invalid word should fail validation")
    }

    @Test
    fun testValidateMnemonic_InvalidChecksum() = runTest {
        // All same words will have wrong checksum (unless it's "abandon" x12 ending with "about")
        val invalid = MnemonicUtils.validateMnemonic(
            "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon"
        )
        assertFalse(invalid, "Mnemonic with invalid checksum should fail validation")
    }

    @Test
    fun testValidateMnemonic_ValidChecksumAllZeroEntropy() = runTest {
        // This is the actual valid mnemonic for all-zero entropy
        val valid = MnemonicUtils.validateMnemonic(
            "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about"
        )
        assertTrue(valid, "Valid checksum mnemonic (all-zero entropy) should pass")
    }

    @Test
    fun testValidateMnemonic_EmptyString() = runTest {
        assertFalse(MnemonicUtils.validateMnemonic(""))
    }

    @Test
    fun testValidateMnemonic_OnlyWhitespace() = runTest {
        assertFalse(MnemonicUtils.validateMnemonic("   \t\n   "))
    }

    @Test
    fun testValidateMnemonic_WhitespaceHandling_LeadingTrailing() = runTest {
        val valid = MnemonicUtils.validateMnemonic(
            "  illness spike retreat truth genius clock brain pass fit cave bargain toe  "
        )
        assertTrue(valid, "Leading/trailing whitespace should be handled")
    }

    @Test
    fun testValidateMnemonic_WhitespaceHandling_MultipleSpaces() = runTest {
        val valid = MnemonicUtils.validateMnemonic(
            "illness  spike  retreat truth genius clock brain pass fit cave bargain toe"
        )
        assertTrue(valid, "Multiple spaces between words should be handled")
    }

    @Test
    fun testValidateMnemonic_WhitespaceHandling_Tabs() = runTest {
        val valid = MnemonicUtils.validateMnemonic(
            "illness\tspike\tretreat\ttruth\tgenius\tclock\tbrain\tpass\tfit\tcave\tbargain\ttoe"
        )
        assertTrue(valid, "Tab separators should be handled")
    }

    @Test
    fun testValidateMnemonic_CaseInsensitive() = runTest {
        // Uppercase input should be normalized to lowercase and validate
        val valid = MnemonicUtils.validateMnemonic(
            "ILLNESS SPIKE RETREAT TRUTH GENIUS CLOCK BRAIN PASS FIT CAVE BARGAIN TOE"
        )
        assertTrue(valid, "Uppercase mnemonic should validate (case-insensitive)")
    }

    @Test
    fun testValidateMnemonic_MixedCase() = runTest {
        val valid = MnemonicUtils.validateMnemonic(
            "Illness Spike Retreat Truth Genius Clock Brain Pass Fit Cave Bargain Toe"
        )
        assertTrue(valid, "Mixed case mnemonic should validate (case-insensitive)")
    }

    // ========== Entropy Conversion Tests ==========

    @Test
    fun testEntropyToMnemonic_AllZeros128Bits() = runTest {
        val entropy = HexCodec.decode("00000000000000000000000000000000") // 16 bytes
        val mnemonic = MnemonicUtils.entropyToMnemonic(entropy)
        assertEquals(
            "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about",
            mnemonic
        )
    }

    @Test
    fun testEntropyToMnemonic_AllOnes128Bits() = runTest {
        val entropy = HexCodec.decode("ffffffffffffffffffffffffffffffff") // 16 bytes
        val mnemonic = MnemonicUtils.entropyToMnemonic(entropy)
        assertEquals(12, mnemonic.split(" ").size)
        assertTrue(MnemonicUtils.validateMnemonic(mnemonic))
    }

    @Test
    fun testEntropyToMnemonic_AndBack() = runTest {
        val entropy = HexCodec.decode("00000000000000000000000000000000")
        val mnemonic = MnemonicUtils.entropyToMnemonic(entropy)
        val recoveredEntropy = MnemonicUtils.mnemonicToEntropy(mnemonic)
        assertContentEquals(entropy, recoveredEntropy)
    }

    @Test
    fun testEntropyToMnemonic_RoundTrip160Bits() = runTest {
        val entropy = HexCodec.decode("0000000000000000000000000000000000000000") // 20 bytes
        val mnemonic = MnemonicUtils.entropyToMnemonic(entropy)
        assertEquals(15, mnemonic.split(" ").size)
        val recoveredEntropy = MnemonicUtils.mnemonicToEntropy(mnemonic)
        assertContentEquals(entropy, recoveredEntropy)
    }

    @Test
    fun testEntropyToMnemonic_RoundTrip192Bits() = runTest {
        val entropy = HexCodec.decode("000000000000000000000000000000000000000000000000") // 24 bytes
        val mnemonic = MnemonicUtils.entropyToMnemonic(entropy)
        assertEquals(18, mnemonic.split(" ").size)
        val recoveredEntropy = MnemonicUtils.mnemonicToEntropy(mnemonic)
        assertContentEquals(entropy, recoveredEntropy)
    }

    @Test
    fun testEntropyToMnemonic_RoundTrip224Bits() = runTest {
        val entropy = HexCodec.decode("00000000000000000000000000000000000000000000000000000000") // 28 bytes
        val mnemonic = MnemonicUtils.entropyToMnemonic(entropy)
        assertEquals(21, mnemonic.split(" ").size)
        val recoveredEntropy = MnemonicUtils.mnemonicToEntropy(mnemonic)
        assertContentEquals(entropy, recoveredEntropy)
    }

    @Test
    fun testEntropyToMnemonic_RoundTrip256Bits() = runTest {
        val entropy = HexCodec.decode("0000000000000000000000000000000000000000000000000000000000000000") // 32 bytes
        val mnemonic = MnemonicUtils.entropyToMnemonic(entropy)
        assertEquals(24, mnemonic.split(" ").size)
        val recoveredEntropy = MnemonicUtils.mnemonicToEntropy(mnemonic)
        assertContentEquals(entropy, recoveredEntropy)
    }

    @Test
    fun testMnemonicToEntropy_InvalidWordThrows() = runTest {
        assertFailsWith<InvalidWordException> {
            MnemonicUtils.mnemonicToEntropy(
                "invalid spike retreat truth genius clock brain pass fit cave bargain toe"
            )
        }
    }

    @Test
    fun testMnemonicToEntropy_InvalidWordExceptionContainsWord() = runTest {
        val exception = assertFailsWith<InvalidWordException> {
            MnemonicUtils.mnemonicToEntropy(
                "xyz spike retreat truth genius clock brain pass fit cave bargain toe"
            )
        }
        assertEquals("xyz", exception.word)
        assertEquals(MnemonicLanguage.ENGLISH, exception.language)
    }

    @Test
    fun testMnemonicToEntropy_InvalidChecksumThrows() = runTest {
        assertFailsWith<InvalidChecksumException> {
            MnemonicUtils.mnemonicToEntropy(
                "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon"
            )
        }
    }

    @Test
    fun testMnemonicToEntropy_InvalidWordCountThrows() = runTest {
        assertFailsWith<InvalidMnemonicException> {
            MnemonicUtils.mnemonicToEntropy("abandon abandon abandon")
        }
    }

    @Test
    fun testEntropyToMnemonic_InvalidSizeTooSmall() = runTest {
        val tooSmall = ByteArray(15) // Must be at least 16
        assertFailsWith<InvalidEntropyException> {
            MnemonicUtils.entropyToMnemonic(tooSmall)
        }
    }

    @Test
    fun testEntropyToMnemonic_InvalidSizeTooLarge() = runTest {
        val tooLarge = ByteArray(33) // Must be at most 32
        assertFailsWith<InvalidEntropyException> {
            MnemonicUtils.entropyToMnemonic(tooLarge)
        }
    }

    @Test
    fun testEntropyToMnemonic_InvalidSizeNotMultipleOf4() = runTest {
        val notMultiple = ByteArray(17) // Must be multiple of 4
        assertFailsWith<InvalidEntropyException> {
            MnemonicUtils.entropyToMnemonic(notMultiple)
        }
    }

    // ========== Language Detection Tests ==========

    @Test
    fun testDetectLanguage_English() {
        val language = MnemonicUtils.detectLanguage(
            "illness spike retreat truth genius clock brain pass fit cave bargain toe"
        )
        assertEquals(MnemonicLanguage.ENGLISH, language)
    }

    @Test
    fun testDetectLanguage_EnglishCaseInsensitive() {
        val language = MnemonicUtils.detectLanguage(
            "ILLNESS SPIKE RETREAT TRUTH GENIUS CLOCK BRAIN PASS FIT CAVE BARGAIN TOE"
        )
        assertEquals(MnemonicLanguage.ENGLISH, language)
    }

    @Test
    fun testDetectLanguage_Unknown() {
        val language = MnemonicUtils.detectLanguage("foo bar baz qux")
        assertNull(language, "Unknown words should return null")
    }

    @Test
    fun testDetectLanguage_Empty() {
        val language = MnemonicUtils.detectLanguage("")
        assertNull(language)
    }

    @Test
    fun testDetectLanguage_SingleWord() {
        val language = MnemonicUtils.detectLanguage("abandon")
        // "abandon" exists in English
        assertEquals(MnemonicLanguage.ENGLISH, language)
    }

    // ========== Seed Derivation Tests ==========

    @Test
    fun testMnemonicToSeed() = runTest {
        val mnemonic = "illness spike retreat truth genius clock brain pass fit cave bargain toe"
        val seed = MnemonicUtils.mnemonicToSeed(mnemonic)
        assertEquals(64, seed.size, "BIP-39 seed must be 64 bytes")

        // Verify against known test vector
        val expectedSeedHex = "e4a5a632e70943ae7f07659df1332160937fad82587216a4c64315a0fb39497ee4a01f76ddab4cba68147977f3a147b6ad584c41808e8238a07f6cc4b582f186"
        assertEquals(expectedSeedHex, HexCodec.encode(seed))
    }

    @Test
    fun testMnemonicToSeed_WithPassphrase() = runTest {
        val mnemonic = "cable spray genius state float twenty onion head street palace net private method loan turn phrase state blanket interest dry amazing dress blast tube"
        val seed = MnemonicUtils.mnemonicToSeed(mnemonic, "p4ssphr4se")
        assertEquals(64, seed.size)
        // The exact seed value is verified in Sep05TestVectorsTest
    }

    @Test
    fun testMnemonicToSeed_EmptyPassphraseSameAsNoPassphrase() = runTest {
        val mnemonic = "illness spike retreat truth genius clock brain pass fit cave bargain toe"
        val seed1 = MnemonicUtils.mnemonicToSeed(mnemonic)
        val seed2 = MnemonicUtils.mnemonicToSeed(mnemonic, "")
        assertContentEquals(seed1, seed2, "Empty passphrase should produce same result as no passphrase")
    }

    @Test
    fun testMnemonicToSeed_DifferentPassphrasesDifferentSeeds() = runTest {
        val mnemonic = "illness spike retreat truth genius clock brain pass fit cave bargain toe"
        val seed1 = MnemonicUtils.mnemonicToSeed(mnemonic, "passphrase1")
        val seed2 = MnemonicUtils.mnemonicToSeed(mnemonic, "passphrase2")
        assertFalse(seed1.contentEquals(seed2), "Different passphrases should produce different seeds")
    }

    @Test
    fun testMnemonicToSeedHex() = runTest {
        val mnemonic = "illness spike retreat truth genius clock brain pass fit cave bargain toe"
        val seedHex = MnemonicUtils.mnemonicToSeedHex(mnemonic)
        assertEquals(128, seedHex.length, "Hex seed should be 128 characters (64 bytes)")
        assertEquals(
            "e4a5a632e70943ae7f07659df1332160937fad82587216a4c64315a0fb39497ee4a01f76ddab4cba68147977f3a147b6ad584c41808e8238a07f6cc4b582f186",
            seedHex
        )
    }

    // ========== Mnemonic Strength Tests ==========

    @Test
    fun testGetMnemonicStrength_12Words() {
        val strength = MnemonicUtils.getMnemonicStrength(
            "illness spike retreat truth genius clock brain pass fit cave bargain toe"
        )
        assertEquals(MnemonicStrength.BITS_128, strength)
    }

    @Test
    fun testGetMnemonicStrength_15Words() {
        val mnemonic = "abandon ".repeat(14) + "ability" // 15 words
        val strength = MnemonicUtils.getMnemonicStrength(mnemonic.trim())
        assertEquals(MnemonicStrength.BITS_160, strength)
    }

    @Test
    fun testGetMnemonicStrength_18Words() {
        val mnemonic = "abandon ".repeat(17) + "ability" // 18 words
        val strength = MnemonicUtils.getMnemonicStrength(mnemonic.trim())
        assertEquals(MnemonicStrength.BITS_192, strength)
    }

    @Test
    fun testGetMnemonicStrength_21Words() {
        val mnemonic = "abandon ".repeat(20) + "ability" // 21 words
        val strength = MnemonicUtils.getMnemonicStrength(mnemonic.trim())
        assertEquals(MnemonicStrength.BITS_224, strength)
    }

    @Test
    fun testGetMnemonicStrength_24Words() {
        val strength = MnemonicUtils.getMnemonicStrength(
            "bench hurt jump file august wise shallow faculty impulse spring exact slush thunder author capable act festival slice deposit sauce coconut afford frown better"
        )
        assertEquals(MnemonicStrength.BITS_256, strength)
    }

    @Test
    fun testGetMnemonicStrength_InvalidWordCount() {
        val strength = MnemonicUtils.getMnemonicStrength("abandon abandon abandon")
        assertNull(strength, "Invalid word count should return null")
    }

    @Test
    fun testGetMnemonicStrength_Empty() {
        val strength = MnemonicUtils.getMnemonicStrength("")
        assertNull(strength)
    }
}
