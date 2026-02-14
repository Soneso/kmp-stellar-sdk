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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Unit tests for [WordList] BIP-39 word list handling.
 *
 * Tests verify:
 * - All 9 languages have exactly 2048 words
 * - Word index lookup works correctly
 * - Case sensitivity behavior
 * - No duplicate words in any language
 */
class WordListTest {

    // ========== Word Count Tests ==========

    @Test
    fun testAllLanguagesHave2048Words() {
        for (language in MnemonicLanguage.entries) {
            val wordList = WordList.getWordList(language)
            assertEquals(
                MnemonicConstants.WORD_LIST_SIZE,
                wordList.size,
                "Language $language should have exactly ${MnemonicConstants.WORD_LIST_SIZE} words, got ${wordList.size}"
            )
        }
    }

    @Test
    fun testEnglishWordListSize() {
        val wordList = WordList.getWordList(MnemonicLanguage.ENGLISH)
        assertEquals(2048, wordList.size)
    }

    @Test
    fun testChineseSimplifiedWordListSize() {
        val wordList = WordList.getWordList(MnemonicLanguage.CHINESE_SIMPLIFIED)
        assertEquals(2048, wordList.size)
    }

    @Test
    fun testChineseTraditionalWordListSize() {
        val wordList = WordList.getWordList(MnemonicLanguage.CHINESE_TRADITIONAL)
        assertEquals(2048, wordList.size)
    }

    @Test
    fun testFrenchWordListSize() {
        val wordList = WordList.getWordList(MnemonicLanguage.FRENCH)
        assertEquals(2048, wordList.size)
    }

    @Test
    fun testItalianWordListSize() {
        val wordList = WordList.getWordList(MnemonicLanguage.ITALIAN)
        assertEquals(2048, wordList.size)
    }

    @Test
    fun testJapaneseWordListSize() {
        val wordList = WordList.getWordList(MnemonicLanguage.JAPANESE)
        assertEquals(2048, wordList.size)
    }

    @Test
    fun testKoreanWordListSize() {
        val wordList = WordList.getWordList(MnemonicLanguage.KOREAN)
        assertEquals(2048, wordList.size)
    }

    @Test
    fun testSpanishWordListSize() {
        val wordList = WordList.getWordList(MnemonicLanguage.SPANISH)
        assertEquals(2048, wordList.size)
    }

    @Test
    fun testMalayWordListSize() {
        val wordList = WordList.getWordList(MnemonicLanguage.MALAY)
        assertEquals(2048, wordList.size)
    }

    // ========== Word Index Lookup Tests ==========

    @Test
    fun testWordIndexLookup() {
        // First word in English is "abandon" at index 0
        val index0 = WordList.getWordIndex("abandon", MnemonicLanguage.ENGLISH)
        assertEquals(0, index0)

        // Last word in English is "zoo" at index 2047
        val index2047 = WordList.getWordIndex("zoo", MnemonicLanguage.ENGLISH)
        assertEquals(2047, index2047)
    }

    @Test
    fun testWordIndexLookupNotFound() {
        val index = WordList.getWordIndex("notaword", MnemonicLanguage.ENGLISH)
        assertNull(index)
    }

    @Test
    fun testWordIndexLookupEmpty() {
        val index = WordList.getWordIndex("", MnemonicLanguage.ENGLISH)
        assertNull(index)
    }

    @Test
    fun testWordIndexLookupCaseSensitive() {
        // BIP-39 words are lowercase - uppercase should not match
        val lowercaseIndex = WordList.getWordIndex("abandon", MnemonicLanguage.ENGLISH)
        assertEquals(0, lowercaseIndex)

        // Uppercase should not match (word list stores lowercase)
        val uppercaseIndex = WordList.getWordIndex("ABANDON", MnemonicLanguage.ENGLISH)
        assertNull(uppercaseIndex, "BIP-39 word lookup should be case-sensitive (lowercase only)")

        val mixedCaseIndex = WordList.getWordIndex("Abandon", MnemonicLanguage.ENGLISH)
        assertNull(mixedCaseIndex, "BIP-39 word lookup should be case-sensitive (lowercase only)")
    }

    @Test
    fun testWordIndexLookupAllEnglishWords() {
        val wordList = WordList.getWordList(MnemonicLanguage.ENGLISH)
        for ((expectedIndex, word) in wordList.withIndex()) {
            val actualIndex = WordList.getWordIndex(word, MnemonicLanguage.ENGLISH)
            assertNotNull(actualIndex, "Word '$word' should be found")
            assertEquals(expectedIndex, actualIndex, "Index mismatch for word '$word'")
        }
    }

    // ========== Uniqueness Tests ==========

    @Test
    fun testNoLanguageHasDuplicates() {
        for (language in MnemonicLanguage.entries) {
            val wordList = WordList.getWordList(language)
            val uniqueWords = wordList.toSet()
            assertEquals(
                wordList.size,
                uniqueWords.size,
                "Language $language has duplicate words: ${wordList.groupingBy { it }.eachCount().filter { it.value > 1 }}"
            )
        }
    }

    @Test
    fun testEnglishNoDuplicates() {
        val wordList = WordList.getWordList(MnemonicLanguage.ENGLISH)
        assertEquals(wordList.size, wordList.toSet().size, "English word list has duplicates")
    }

    // ========== Known Words Tests ==========

    @Test
    fun testEnglishKnownWords() {
        val wordList = WordList.getWordList(MnemonicLanguage.ENGLISH)

        // First word
        assertEquals("abandon", wordList[0])

        // Last word
        assertEquals("zoo", wordList[2047])

        // Some known middle words (from test vectors)
        assertTrue(wordList.contains("illness"))
        assertTrue(wordList.contains("spike"))
        assertTrue(wordList.contains("retreat"))
        assertTrue(wordList.contains("truth"))
        assertTrue(wordList.contains("genius"))
        assertTrue(wordList.contains("clock"))
        assertTrue(wordList.contains("brain"))
        assertTrue(wordList.contains("pass"))
        assertTrue(wordList.contains("fit"))
        assertTrue(wordList.contains("cave"))
        assertTrue(wordList.contains("bargain"))
        assertTrue(wordList.contains("toe"))
    }

    @Test
    fun testEnglishSpecificIndices() {
        // Verify specific indices for commonly used words
        assertEquals(0, WordList.getWordIndex("abandon", MnemonicLanguage.ENGLISH))
        assertEquals(1, WordList.getWordIndex("ability", MnemonicLanguage.ENGLISH))
        assertEquals(2, WordList.getWordIndex("able", MnemonicLanguage.ENGLISH))
        assertEquals(2047, WordList.getWordIndex("zoo", MnemonicLanguage.ENGLISH))
    }

    // ========== Caching Tests ==========

    @Test
    fun testWordListCaching() {
        // Get the word list twice and verify same instance is returned
        val list1 = WordList.getWordList(MnemonicLanguage.ENGLISH)
        val list2 = WordList.getWordList(MnemonicLanguage.ENGLISH)

        // Should be the same cached instance
        assertTrue(list1 === list2, "Word list should be cached and return same instance")
    }

    @Test
    fun testWordIndexCaching() {
        // Access the same index multiple times - should use cached lookup map
        val index1 = WordList.getWordIndex("abandon", MnemonicLanguage.ENGLISH)
        val index2 = WordList.getWordIndex("abandon", MnemonicLanguage.ENGLISH)
        assertEquals(index1, index2)
    }

    // ========== All Languages Index Lookup Tests ==========

    @Test
    fun testAllLanguagesHaveValidFirstWord() {
        for (language in MnemonicLanguage.entries) {
            val wordList = WordList.getWordList(language)
            val firstWord = wordList[0]
            val index = WordList.getWordIndex(firstWord, language)
            assertEquals(0, index, "First word '$firstWord' of $language should be at index 0")
        }
    }

    @Test
    fun testAllLanguagesHaveValidLastWord() {
        for (language in MnemonicLanguage.entries) {
            val wordList = WordList.getWordList(language)
            val lastWord = wordList[2047]
            val index = WordList.getWordIndex(lastWord, language)
            assertEquals(2047, index, "Last word '$lastWord' of $language should be at index 2047")
        }
    }

    // ========== Cross-Language Tests ==========

    @Test
    fun testEnglishWordNotInChineseSimplified() {
        // "abandon" is English-only
        val englishIndex = WordList.getWordIndex("abandon", MnemonicLanguage.ENGLISH)
        assertNotNull(englishIndex)

        val chineseIndex = WordList.getWordIndex("abandon", MnemonicLanguage.CHINESE_SIMPLIFIED)
        assertNull(chineseIndex, "English word 'abandon' should not be in Chinese Simplified list")
    }

    @Test
    fun testAllLanguagesAreDistinct() {
        // While some words might overlap between languages, the lists should be different
        val englishList = WordList.getWordList(MnemonicLanguage.ENGLISH)
        val frenchList = WordList.getWordList(MnemonicLanguage.FRENCH)

        // These should not be identical
        assertTrue(englishList != frenchList, "English and French word lists should be different")
    }
}
