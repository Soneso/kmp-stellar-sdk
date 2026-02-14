package com.soneso.stellar.sdk.sep.sep05

/**
 * Mnemonic strength levels defining entropy size and resulting word count.
 *
 * Higher entropy provides stronger security but results in longer mnemonic phrases.
 * The recommended strength for most use cases is [BITS_256] (24 words).
 *
 * The relationship between entropy and word count follows the BIP-39 formula:
 * - Checksum bits = entropy bits / 32
 * - Total bits = entropy bits + checksum bits
 * - Word count = total bits / 11
 *
 * @property entropyBits The number of entropy bits used to generate the mnemonic
 * @property wordCount The resulting number of mnemonic words
 *
 * @see <a href="https://github.com/bitcoin/bips/blob/master/bip-0039.mediawiki">BIP-39 Specification</a>
 */
public enum class MnemonicStrength(public val entropyBits: Int, public val wordCount: Int) {
    /** 128 bits of entropy resulting in 12 words - adequate security */
    BITS_128(128, 12),
    /** 160 bits of entropy resulting in 15 words - good security */
    BITS_160(160, 15),
    /** 192 bits of entropy resulting in 18 words - strong security */
    BITS_192(192, 18),
    /** 224 bits of entropy resulting in 21 words - very strong security */
    BITS_224(224, 21),
    /** 256 bits of entropy resulting in 24 words - maximum security (recommended) */
    BITS_256(256, 24);

    public companion object {
        /**
         * Find the strength level for a given word count.
         *
         * @param wordCount The number of mnemonic words (12, 15, 18, 21, or 24)
         * @return The corresponding strength, or null if not a valid word count
         */
        public fun fromWordCount(wordCount: Int): MnemonicStrength? {
            return entries.find { it.wordCount == wordCount }
        }

        /**
         * Find the strength level for a given entropy size.
         *
         * @param bits The number of entropy bits (128, 160, 192, 224, or 256)
         * @return The corresponding strength, or null if not a valid entropy size
         */
        public fun fromEntropyBits(bits: Int): MnemonicStrength? {
            return entries.find { it.entropyBits == bits }
        }
    }
}
