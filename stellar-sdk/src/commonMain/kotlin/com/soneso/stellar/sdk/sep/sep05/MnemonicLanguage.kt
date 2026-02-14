package com.soneso.stellar.sdk.sep.sep05

/**
 * Supported languages for BIP-39 mnemonic word lists.
 *
 * Each language has a standardized word list of exactly 2048 words
 * as defined in the BIP-39 specification. The word lists are carefully
 * curated to avoid ambiguous words and ensure reliable transcription.
 *
 * @see <a href="https://github.com/bitcoin/bips/blob/master/bip-0039.mediawiki">BIP-39 Specification</a>
 * @see <a href="https://github.com/bitcoin/bips/blob/master/bip-0039/bip-0039-wordlists.md">BIP-39 Word Lists</a>
 */
public enum class MnemonicLanguage {
    /** English - the reference implementation language */
    ENGLISH,
    /** Simplified Chinese (简体中文) */
    CHINESE_SIMPLIFIED,
    /** Traditional Chinese (繁體中文) */
    CHINESE_TRADITIONAL,
    /** French (Francais) */
    FRENCH,
    /** Italian (Italiano) */
    ITALIAN,
    /** Japanese (日本語) - uses ideographic space (U+3000) as word separator */
    JAPANESE,
    /** Korean (한국어) */
    KOREAN,
    /** Spanish (Espanol) */
    SPANISH,
    /** Malay (Bahasa Melayu) */
    MALAY
}
