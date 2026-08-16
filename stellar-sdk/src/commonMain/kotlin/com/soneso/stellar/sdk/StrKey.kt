package com.soneso.stellar.sdk

import kotlin.experimental.and

/**
 * Platform-specific Base32 codec interface.
 *
 * [isInAlphabet] is the gate every strkey decode passes through. It accepts only the 32
 * characters of the base32 alphabet, so the pad character, whitespace and any other byte make it
 * return false and the same string is accepted or rejected identically on every platform.
 * [decode] takes input that gate accepts, and reports anything else as an invalid argument
 * rather than decoding it to bytes the input does not spell.
 */
internal expect object Base32Codec {
    fun encode(data: ByteArray): ByteArray
    fun decode(data: ByteArray): ByteArray
    fun isInAlphabet(data: ByteArray): Boolean
}

/**
 * Number of base32 characters a strkey carrying [dataLength] payload bytes encodes to.
 *
 * A strkey encodes one version byte, the payload and two checksum bytes, base32 without
 * padding, so the encoded length is `ceil((1 + dataLength + 2) * 8 / 5)`.
 */
private fun encodedStrKeyLength(dataLength: Int): Int = ((1 + dataLength + 2) * 8 + 4) / 5

/**
 * [length] rounded up to the four-byte boundary variable-length opaque data is padded to.
 */
private fun paddedToFourBytes(length: Int): Int = (length + 3) / 4 * 4

/**
 * The characters a strkey is written in, in the order that gives each its five-bit value.
 *
 * Every codec in this module builds its tables from this one declaration, so no platform can
 * hold a different idea of which characters a strkey may contain.
 */
internal const val BASE32_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"

/**
 * Renders the lengths [range] admits for an error message: the single value of a one-element
 * range, or the bounds of a wider one.
 */
private fun expectedLengthText(range: IntRange): String =
    if (range.first == range.last) "${range.first}" else "between ${range.first} and ${range.last}"

/**
 * StrKey is a helper class for encoding and decoding Stellar keys to/from strings.
 * Stellar uses a base32 encoding with checksums called "strkey" for human-readable keys.
 */
object StrKey {

    /**
     * Strkey type discriminants together with the payload sizes each type admits.
     *
     * [dataLengths] is the single source of the length expectations: it is what the decoded
     * payload is checked against, and through [encodedStrKeyLength] it also fixes the number
     * of characters the encoded strkey has.
     */
    private enum class VersionByte(val value: Byte, val dataLengths: IntRange) {
        ACCOUNT_ID((6 shl 3).toByte(), 32..32),           // G
        MED25519_PUBLIC_KEY((12 shl 3).toByte(), 40..40), // M
        SEED((18 shl 3).toByte(), 32..32),                // S
        PRE_AUTH_TX((19 shl 3).toByte(), 32..32),         // T
        SHA256_HASH((23 shl 3).toByte(), 32..32),         // X
        SIGNED_PAYLOAD((15 shl 3).toByte(), 40..100),     // P
        CONTRACT((2 shl 3).toByte(), 32..32),             // C
        LIQUIDITY_POOL((11 shl 3).toByte(), 32..32),      // L
        CLAIMABLE_BALANCE((1 shl 3).toByte(), 33..33);    // B

        /**
         * Character counts an encoded strkey of this type can have.
         *
         * For every type but [SIGNED_PAYLOAD] the range holds a single value. A signed
         * payload carries a variable payload padded to a four-byte boundary, so its range
         * also spans lengths that no well-formed key produces; the exact length follows
         * from the declared payload length.
         */
        val encodedLengths: IntRange =
            encodedStrKeyLength(dataLengths.first)..encodedStrKeyLength(dataLengths.last)

        companion object {
            fun fromValue(value: Byte): VersionByte? = entries.find { it.value == value }
        }
    }

    /**
     * Bytes a signed payload spends on the ed25519 public key it names.
     */
    private const val SIGNED_PAYLOAD_KEY_SIZE = 32

    /**
     * Bytes a signed payload spends before the payload itself: the ed25519 public key followed
     * by the declared payload length, written as a big-endian four-byte value.
     */
    private const val SIGNED_PAYLOAD_HEADER_SIZE = SIGNED_PAYLOAD_KEY_SIZE + 4

    /**
     * Payload lengths a signed payload can declare.
     *
     * The upper bound follows from the payload sizes the type admits: the largest of them is
     * the header plus a payload padded to the four-byte boundary, so the two bounds cannot
     * drift apart.
     */
    private val declaredPayloadLengths: IntRange =
        1..(VersionByte.SIGNED_PAYLOAD.dataLengths.last - SIGNED_PAYLOAD_HEADER_SIZE)

    /**
     * The type discriminant a claimable balance id is written under.
     *
     * The XDR union a claimable balance id describes declares a single case, so this is the one
     * value its wire form carries and the one value a B... strkey can spell.
     */
    internal const val CLAIMABLE_BALANCE_V0_DISCRIMINANT: Byte = 0

    /**
     * Bytes an XDR union spends on its type discriminant, written big-endian.
     *
     * The XDR wire form of a claimable balance id opens with the discriminant in this width,
     * where the strkey body carries it in a single byte.
     */
    internal const val XDR_UNION_DISCRIMINANT_SIZE = 4

    /**
     * Bytes the strkey body of a claimable balance id spends on its type discriminant.
     */
    internal const val CLAIMABLE_BALANCE_DISCRIMINANT_SIZE = 1

    /**
     * Bytes the strkey body of a claimable balance id holds: the type discriminant followed by
     * the hash.
     */
    internal val CLAIMABLE_BALANCE_BODY_SIZE: Int =
        VersionByte.CLAIMABLE_BALANCE.dataLengths.last

    /**
     * Bytes a claimable balance id hash holds.
     */
    internal val CLAIMABLE_BALANCE_HASH_SIZE: Int =
        CLAIMABLE_BALANCE_BODY_SIZE - CLAIMABLE_BALANCE_DISCRIMINANT_SIZE

    /**
     * Bytes the XDR wire form of a claimable balance id holds: the four-byte union
     * discriminant followed by the hash.
     */
    internal val CLAIMABLE_BALANCE_XDR_SIZE: Int =
        XDR_UNION_DISCRIMINANT_SIZE + CLAIMABLE_BALANCE_HASH_SIZE

    /**
     * Characters an encoded claimable balance strkey (B...) has.
     */
    internal val CLAIMABLE_BALANCE_STRKEY_LENGTH: Int =
        VersionByte.CLAIMABLE_BALANCE.encodedLengths.first

    // Decoding table for base32
    private val decodingTable: ByteArray = ByteArray(256) { 0xff.toByte() }.apply {
        BASE32_ALPHABET.forEachIndexed { index, char ->
            this[char.code] = index.toByte()
        }
    }

    /**
     * Encodes raw bytes to strkey ed25519 public key (G...)
     */
    fun encodeEd25519PublicKey(data: ByteArray): String {
        val dataLengths = VersionByte.ACCOUNT_ID.dataLengths
        require(data.size in dataLengths) {
            "Public key must be ${expectedLengthText(dataLengths)} bytes, got ${data.size}"
        }
        return encodeCheck(VersionByte.ACCOUNT_ID, data).concatToString()
    }

    /**
     * Decodes strkey ed25519 public key (G...) to raw bytes
     *
     * @param data The strkey to decode. An ed25519 public key strkey is 56 characters long.
     * @return The 32 raw public key bytes
     * @throws IllegalArgumentException if the string is not 56 characters long, contains
     * characters outside the base32 alphabet, does not carry the ed25519 public key version
     * byte, or fails the checksum
     */
    fun decodeEd25519PublicKey(data: String): ByteArray {
        return decodeCheck(VersionByte.ACCOUNT_ID, data.toCharArray())
    }

    /**
     * Checks validity of Stellar account ID (G...)
     *
     * @param accountId The strkey to check
     * @return true if [accountId] is a 56-character G... strkey with a matching checksum,
     * false otherwise
     */
    fun isValidEd25519PublicKey(accountId: String): Boolean {
        return try {
            decodeEd25519PublicKey(accountId)
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Encodes raw bytes to strkey ed25519 secret seed (S...)
     */
    fun encodeEd25519SecretSeed(data: ByteArray): CharArray {
        val dataLengths = VersionByte.SEED.dataLengths
        require(data.size in dataLengths) {
            "Secret seed must be ${expectedLengthText(dataLengths)} bytes, got ${data.size}"
        }
        return encodeCheck(VersionByte.SEED, data)
    }

    /**
     * Decodes strkey ed25519 secret seed (S...) to raw bytes
     *
     * @param data The strkey to decode. An ed25519 secret seed strkey is 56 characters long.
     * @return The 32 raw seed bytes
     * @throws IllegalArgumentException if the input is not 56 characters long, contains
     * characters outside the base32 alphabet, does not carry the secret seed version byte,
     * or fails the checksum
     */
    fun decodeEd25519SecretSeed(data: CharArray): ByteArray {
        return decodeCheck(VersionByte.SEED, data)
    }

    /**
     * Checks validity of seed (S...)
     *
     * @param seed The strkey to check
     * @return true if [seed] is a 56-character S... strkey with a matching checksum,
     * false otherwise
     */
    fun isValidEd25519SecretSeed(seed: CharArray): Boolean {
        return try {
            decodeEd25519SecretSeed(seed)
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Encodes raw bytes to strkey muxed ed25519 public key (M...)
     */
    fun encodeMed25519PublicKey(data: ByteArray): String {
        val dataLengths = VersionByte.MED25519_PUBLIC_KEY.dataLengths
        require(data.size in dataLengths) {
            "Muxed public key must be ${expectedLengthText(dataLengths)} bytes, got ${data.size}"
        }
        return encodeCheck(VersionByte.MED25519_PUBLIC_KEY, data).concatToString()
    }

    /**
     * Decodes strkey muxed ed25519 public key (M...) to raw bytes
     *
     * @param data The strkey to decode. A muxed ed25519 public key strkey is 69 characters long.
     * @return The 40 raw bytes: the 32-byte ed25519 public key followed by the 8-byte muxed id
     * @throws IllegalArgumentException if the string is not 69 characters long, contains
     * characters outside the base32 alphabet, leaves the unused trailing bits of its last
     * character non-zero, does not carry the muxed ed25519 public key version byte, or fails
     * the checksum
     */
    fun decodeMed25519PublicKey(data: String): ByteArray {
        return decodeCheck(VersionByte.MED25519_PUBLIC_KEY, data.toCharArray())
    }

    /**
     * Checks validity of muxed ed25519 public key (M...)
     *
     * Muxed accounts (M...) are virtual accounts that share the same underlying ed25519 key
     * but have different IDs. They are used for memo-less payments as defined in SEP-0023.
     *
     * @param med25519PublicKey The muxed public key to check
     * @return true if [med25519PublicKey] is a 69-character M... strkey whose unused trailing
     * bits are zero and whose checksum matches, false otherwise
     * @see <a href="https://github.com/stellar/stellar-protocol/blob/master/ecosystem/sep-0023.md">SEP-0023</a>
     */
    fun isValidMed25519PublicKey(med25519PublicKey: String): Boolean {
        return try {
            decodeMed25519PublicKey(med25519PublicKey)
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Encodes raw bytes to strkey pre-authorized transaction hash (T...)
     */
    fun encodePreAuthTx(data: ByteArray): String {
        val dataLengths = VersionByte.PRE_AUTH_TX.dataLengths
        require(data.size in dataLengths) {
            "Pre-auth transaction hash must be ${expectedLengthText(dataLengths)} bytes, got ${data.size}"
        }
        return encodeCheck(VersionByte.PRE_AUTH_TX, data).concatToString()
    }

    /**
     * Decodes strkey pre-authorized transaction hash (T...) to raw bytes
     *
     * @param data The strkey to decode. A pre-authorized transaction strkey is 56 characters long.
     * @return The 32 raw hash bytes
     * @throws IllegalArgumentException if the string is not 56 characters long, contains
     * characters outside the base32 alphabet, does not carry the pre-authorized transaction
     * version byte, or fails the checksum
     */
    fun decodePreAuthTx(data: String): ByteArray {
        return decodeCheck(VersionByte.PRE_AUTH_TX, data.toCharArray())
    }

    /**
     * Checks validity of pre-authorized transaction hash (T...)
     *
     * @param preAuthTx The strkey to check
     * @return true if [preAuthTx] is a 56-character T... strkey with a matching checksum,
     * false otherwise
     */
    fun isValidPreAuthTx(preAuthTx: String): Boolean {
        return try {
            decodePreAuthTx(preAuthTx)
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Encodes raw bytes to strkey SHA-256 hash (X...)
     */
    fun encodeSha256Hash(data: ByteArray): String {
        val dataLengths = VersionByte.SHA256_HASH.dataLengths
        require(data.size in dataLengths) {
            "SHA-256 hash must be ${expectedLengthText(dataLengths)} bytes, got ${data.size}"
        }
        return encodeCheck(VersionByte.SHA256_HASH, data).concatToString()
    }

    /**
     * Decodes strkey SHA-256 hash (X...) to raw bytes
     *
     * @param data The strkey to decode. A SHA-256 hash strkey is 56 characters long.
     * @return The 32 raw hash bytes
     * @throws IllegalArgumentException if the string is not 56 characters long, contains
     * characters outside the base32 alphabet, does not carry the SHA-256 hash version byte,
     * or fails the checksum
     */
    fun decodeSha256Hash(data: String): ByteArray {
        return decodeCheck(VersionByte.SHA256_HASH, data.toCharArray())
    }

    /**
     * Checks validity of SHA-256 hash (X...)
     *
     * @param sha256Hash The strkey to check
     * @return true if [sha256Hash] is a 56-character X... strkey with a matching checksum,
     * false otherwise
     */
    fun isValidSha256Hash(sha256Hash: String): Boolean {
        return try {
            decodeSha256Hash(sha256Hash)
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Encodes raw bytes to strkey signed payload (P...)
     *
     * The framing [data] carries is checked here as well as on decode, so every string this
     * function returns is one [decodeSignedPayload] accepts.
     *
     * @param data The 32-byte ed25519 public key, the declared payload length as a big-endian
     * four-byte value, and the payload padded with zeros to a four-byte boundary
     * @return The encoded strkey, 69 to 165 characters long
     * @throws IllegalArgumentException if [data] is outside 40 to 100 bytes, declares a payload
     * length outside 1 to 64, has a size that does not fit its declared payload length exactly,
     * or leaves a padding byte after the payload non-zero
     */
    fun encodeSignedPayload(data: ByteArray): String {
        val dataLengths = VersionByte.SIGNED_PAYLOAD.dataLengths
        require(data.size in dataLengths) {
            "Signed payload must be ${expectedLengthText(dataLengths)} bytes, got ${data.size}"
        }
        requireSignedPayloadFraming(data)
        return encodeCheck(VersionByte.SIGNED_PAYLOAD, data).concatToString()
    }

    /**
     * Decodes strkey signed payload (P...) to raw bytes
     *
     * @param data The strkey to decode. A signed payload strkey is between 69 and 165
     * characters long, depending on the size of the payload it carries.
     * @return The raw bytes: the 32-byte ed25519 public key, the 4-byte declared payload
     * length and the payload padded to a four-byte boundary
     * @throws IllegalArgumentException if the string length is outside 69 to 165 characters,
     * has a length that leaves a partially filled trailing character, contains characters
     * outside the base32 alphabet, leaves the unused trailing bits of its last character
     * non-zero, does not carry the signed payload version byte, decodes to fewer than 40 or
     * more than 100 data bytes, declares a payload length outside 1 to 64, decodes to a size
     * that does not fit its declared payload length exactly, leaves a padding byte after the
     * payload non-zero, or fails the checksum
     */
    fun decodeSignedPayload(data: String): ByteArray {
        return decodeCheck(VersionByte.SIGNED_PAYLOAD, data.toCharArray())
    }

    /**
     * Checks validity of signed payload (P...)
     *
     * @param signedPayload The strkey to check
     * @return true if [signedPayload] is a signed payload strkey [decodeSignedPayload] accepts,
     * false otherwise
     */
    fun isValidSignedPayload(signedPayload: String): Boolean {
        return try {
            decodeSignedPayload(signedPayload)
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Encodes raw bytes to strkey contract address (C...)
     */
    fun encodeContract(data: ByteArray): String {
        val dataLengths = VersionByte.CONTRACT.dataLengths
        require(data.size in dataLengths) {
            "Contract address must be ${expectedLengthText(dataLengths)} bytes, got ${data.size}"
        }
        return encodeCheck(VersionByte.CONTRACT, data).concatToString()
    }

    /**
     * Decodes strkey contract address (C...) to raw bytes
     *
     * @param data The strkey to decode. A contract address strkey is 56 characters long.
     * @return The 32 raw contract id bytes
     * @throws IllegalArgumentException if the string is not 56 characters long, contains
     * characters outside the base32 alphabet, does not carry the contract version byte,
     * or fails the checksum
     */
    fun decodeContract(data: String): ByteArray {
        return decodeCheck(VersionByte.CONTRACT, data.toCharArray())
    }

    /**
     * Checks validity of contract address (C...)
     *
     * @param address The strkey to check
     * @return true if [address] is a 56-character C... strkey with a matching checksum,
     * false otherwise
     */
    fun isValidContract(address: String): Boolean {
        return try {
            decodeContract(address)
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Encodes raw bytes to strkey liquidity pool ID (L...)
     */
    fun encodeLiquidityPool(data: ByteArray): String {
        val dataLengths = VersionByte.LIQUIDITY_POOL.dataLengths
        require(data.size in dataLengths) {
            "Liquidity pool ID must be ${expectedLengthText(dataLengths)} bytes, got ${data.size}"
        }
        return encodeCheck(VersionByte.LIQUIDITY_POOL, data).concatToString()
    }

    /**
     * Decodes strkey liquidity pool ID (L...) to raw bytes
     *
     * @param data The strkey to decode. A liquidity pool id strkey is 56 characters long.
     * @return The 32 raw liquidity pool id bytes
     * @throws IllegalArgumentException if the string is not 56 characters long, contains
     * characters outside the base32 alphabet, does not carry the liquidity pool version byte,
     * or fails the checksum
     */
    fun decodeLiquidityPool(data: String): ByteArray {
        return decodeCheck(VersionByte.LIQUIDITY_POOL, data.toCharArray())
    }

    /**
     * Checks validity of liquidity pool ID (L...)
     *
     * @param liquidityPoolId The strkey to check
     * @return true if [liquidityPoolId] is a 56-character L... strkey with a matching
     * checksum, false otherwise
     */
    fun isValidLiquidityPool(liquidityPoolId: String): Boolean {
        return try {
            decodeLiquidityPool(liquidityPoolId)
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Encodes raw bytes to strkey claimable balance ID (B...)
     *
     * The type discriminant [data] carries is checked here as well as on decode, so every string
     * this function returns is one [decodeClaimableBalance] accepts.
     *
     * @param data The 33-byte strkey body (the type discriminant followed by the 32-byte
     * hash), the 32-byte hash alone, or the 36-byte XDR wire form (the four-byte big-endian
     * union discriminant followed by the hash)
     * @return The encoded strkey, 58 characters long
     * @throws IllegalArgumentException if [data] has none of those widths, or if the
     * discriminant in the 33- or 36-byte form is not the one the XDR union declares
     */
    fun encodeClaimableBalance(data: ByteArray): String {
        val fullData = when (data.size) {
            // The hash on its own names no type, so it is written under the one type a
            // claimable balance id has.
            CLAIMABLE_BALANCE_HASH_SIZE -> byteArrayOf(CLAIMABLE_BALANCE_V0_DISCRIMINANT) + data
            // The discriminant followed by the hash, which the check below holds to the one
            // type the XDR union declares.
            CLAIMABLE_BALANCE_BODY_SIZE -> data
            // The XDR wire form. Every byte of the wider discriminant is judged before the id
            // is narrowed to the strkey body, so a value that names another type cannot reach
            // the strkey by having its high bytes dropped.
            CLAIMABLE_BALANCE_XDR_SIZE -> {
                requireClaimableBalanceXdrDiscriminant(data)
                byteArrayOf(CLAIMABLE_BALANCE_V0_DISCRIMINANT) +
                    data.copyOfRange(XDR_UNION_DISCRIMINANT_SIZE, data.size)
            }
            else -> {
                throw IllegalArgumentException(
                    "Claimable balance ID must be $CLAIMABLE_BALANCE_HASH_SIZE bytes (hash only), " +
                        "$CLAIMABLE_BALANCE_BODY_SIZE bytes (type + hash) or " +
                        "$CLAIMABLE_BALANCE_XDR_SIZE bytes (XDR form), got ${data.size} bytes"
                )
            }
        }
        requireClaimableBalanceDiscriminant(fullData)
        return encodeCheck(VersionByte.CLAIMABLE_BALANCE, fullData).concatToString()
    }

    /**
     * Decodes strkey claimable balance ID (B...) to raw bytes
     *
     * @param data The strkey to decode. A claimable balance id strkey is 58 characters long.
     * @return The 33 raw bytes: the type discriminant followed by the 32-byte hash
     * @throws IllegalArgumentException if the string is not 58 characters long, contains
     * characters outside the base32 alphabet, leaves the unused trailing bits of its last
     * character non-zero, does not carry the claimable balance version byte, carries a type
     * discriminant other than the one the XDR union declares, or fails the checksum
     */
    fun decodeClaimableBalance(data: String): ByteArray {
        return decodeCheck(VersionByte.CLAIMABLE_BALANCE, data.toCharArray())
    }

    /**
     * Checks validity of claimable balance ID (B...)
     *
     * @param claimableBalanceId The strkey to check
     * @return true if [claimableBalanceId] is a 58-character B... strkey whose unused trailing
     * bits are zero, whose type discriminant is the one the XDR union declares and whose
     * checksum matches, false otherwise
     */
    fun isValidClaimableBalance(claimableBalanceId: String): Boolean {
        return try {
            decodeClaimableBalance(claimableBalanceId)
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun encodeCheck(versionByte: VersionByte, data: ByteArray): CharArray {
        val payload = byteArrayOf(versionByte.value) + data
        val checksum = calculateChecksum(payload)
        val unencoded = payload + checksum
        val encoded = Base32Codec.encode(unencoded)

        // Remove padding
        val unpaddedLength = encoded.indexOfFirst { it == '='.code.toByte() }.let {
            if (it == -1) encoded.size else it
        }

        return encoded.take(unpaddedLength).map { it.toInt().toChar() }.toCharArray()
    }

    private fun decodeCheck(versionByte: VersionByte, encoded: CharArray): ByteArray {
        // The requested type fixes the length of its encoded form, so the length is checked
        // against the caller's expectation before anything is decoded. This rejects every
        // string that cannot describe a strkey of the requested type and bounds the work an
        // unvalidated input can cause.
        require(encoded.size in versionByte.encodedLengths) {
            "Invalid encoded length, expected ${expectedLengthText(versionByte.encodedLengths)} " +
                "characters, got ${encoded.size}"
        }

        // The conversion below narrows every character to its low byte, and every check after it
        // reads those bytes. A character outside the ASCII range would be narrowed onto whatever
        // alphabet character its low byte spells, which would let two different strings decode
        // to one key, so only characters that survive the narrowing unchanged get past here.
        require(encoded.all { it.code <= 0x7F }) { "Invalid base32 encoded string" }

        val bytes = encoded.map { it.code.toByte() }.toByteArray()

        // Validate no leftover character
        val leftoverBits = (bytes.size * 5) % 8
        require(leftoverBits < 5) { "Encoded char array has leftover character" }

        // Validate unused bits are zero
        if (leftoverBits > 0) {
            val lastChar = bytes.last()
            val decodedLastChar = decodingTable[lastChar.toInt() and 0xFF]
            val leftoverBitsMask = (0x0f shr (4 - leftoverBits)).toByte()
            require((decodedLastChar and leftoverBitsMask) == 0.toByte()) { "Unused bits should be set to 0" }
        }

        val decoded = base32Decode(bytes)
        val decodedVersionByte = decoded[0]
        val decodedVersion = VersionByte.fromValue(decodedVersionByte)
            ?: throw IllegalArgumentException("Version byte is invalid")

        val payload = decoded.copyOfRange(0, decoded.size - 2)
        val data = payload.copyOfRange(1, payload.size)
        val checksum = decoded.copyOfRange(decoded.size - 2, decoded.size)

        // Validate data length. The type read from the decoded data decides how many payload
        // bytes are admissible, and this runs ahead of any type-specific validation so that
        // such validation can rely on the payload having a size its type admits.
        require(data.size in decodedVersion.dataLengths) {
            "Invalid data length, expected ${expectedLengthText(decodedVersion.dataLengths)} " +
                "bytes, got ${data.size}"
        }

        // Validation of the structure a type frames inside its payload. The size check above
        // has already established a size the type admits, which is what lets each branch read
        // the fields its type defines. A type whose payload is an opaque key of a fixed size
        // frames nothing further and needs no branch.
        when (decodedVersion) {
            VersionByte.SIGNED_PAYLOAD -> requireSignedPayloadFraming(data)
            VersionByte.CLAIMABLE_BALANCE -> requireClaimableBalanceDiscriminant(data)
            else -> Unit
        }

        require(decodedVersion == versionByte) { "Version byte mismatch" }

        val expectedChecksum = calculateChecksum(payload)
        require(expectedChecksum.contentEquals(checksum)) { "Checksum invalid" }

        return data
    }

    /**
     * Checks the framing a signed payload carries: the ed25519 public key, the declared payload
     * length, and the payload padded with zeros to a four-byte boundary. It is the framing the
     * XDR wire form defines, so a strkey that passes here describes a signed payload the wire
     * decoder reads back unchanged.
     *
     * [data] must hold at least [SIGNED_PAYLOAD_HEADER_SIZE] bytes. The per-type payload size
     * check establishes that, and it is what makes reading the declared length here safe.
     *
     * @throws IllegalArgumentException if the declared payload length is outside the range a
     * signed payload can carry, if the payload size does not fit the declared length exactly,
     * or if a padding byte after the payload is non-zero
     */
    private fun requireSignedPayloadFraming(data: ByteArray) {
        var declaredLength = 0L
        for (index in SIGNED_PAYLOAD_KEY_SIZE until SIGNED_PAYLOAD_HEADER_SIZE) {
            declaredLength = (declaredLength shl 8) or (data[index].toLong() and 0xFF)
        }

        require(
            declaredLength >= declaredPayloadLengths.first &&
                declaredLength <= declaredPayloadLengths.last
        ) {
            "Invalid signed payload declared length, expected " +
                "${expectedLengthText(declaredPayloadLengths)} bytes, got $declaredLength"
        }

        val payloadLength = declaredLength.toInt()
        val requiredSize = SIGNED_PAYLOAD_HEADER_SIZE + paddedToFourBytes(payloadLength)
        require(data.size == requiredSize) {
            "Invalid signed payload size, a declared length of $payloadLength requires " +
                "$requiredSize bytes, got ${data.size}"
        }

        for (index in SIGNED_PAYLOAD_HEADER_SIZE + payloadLength until data.size) {
            require(data[index] == 0.toByte()) {
                "Invalid signed payload padding, expected zero at index $index after a declared " +
                    "length of $payloadLength, got ${data[index].toInt() and 0xFF}"
            }
        }
    }

    /**
     * Checks the type discriminant a claimable balance id carries. Only
     * [CLAIMABLE_BALANCE_V0_DISCRIMINANT] names a case the XDR union declares, so a payload
     * written under any other value describes a type the wire decoder cannot read back.
     *
     * [data] must hold at least one byte. The per-type payload size check establishes that, and
     * it is what makes reading the discriminant here safe.
     *
     * @throws IllegalArgumentException if the discriminant is not the one the union declares
     */
    private fun requireClaimableBalanceDiscriminant(data: ByteArray) {
        require(data[0] == CLAIMABLE_BALANCE_V0_DISCRIMINANT) {
            "Invalid claimable balance discriminant, expected " +
                "$CLAIMABLE_BALANCE_V0_DISCRIMINANT, got ${data[0].toInt() and 0xFF}"
        }
    }

    /**
     * Checks the type discriminant the XDR wire form of a claimable balance id carries: the
     * type as a big-endian value across [XDR_UNION_DISCRIMINANT_SIZE] bytes. All of them are
     * read, so a value that differs only in the bytes above the last one is rejected rather
     * than narrowed away.
     *
     * [data] must hold at least [XDR_UNION_DISCRIMINANT_SIZE] bytes. The per-width size check
     * establishes that, and it is what makes reading the discriminant here safe.
     *
     * @throws IllegalArgumentException if the discriminant is not the one the union declares
     */
    private fun requireClaimableBalanceXdrDiscriminant(data: ByteArray) {
        var carried = 0L
        for (index in 0 until XDR_UNION_DISCRIMINANT_SIZE) {
            carried = (carried shl 8) or (data[index].toLong() and 0xFF)
        }
        require(carried == CLAIMABLE_BALANCE_V0_DISCRIMINANT.toLong()) {
            "Invalid claimable balance discriminant, expected " +
                "$CLAIMABLE_BALANCE_V0_DISCRIMINANT, got 0x${carried.toString(16)}"
        }
    }

    /**
     * Calculates CRC16-XModem checksum
     */
    private fun calculateChecksum(bytes: ByteArray): ByteArray {
        var crc = 0x0000

        for (byte in bytes) {
            var code = (crc ushr 8) and 0xFF
            code = code xor (byte.toInt() and 0xFF)
            code = code xor (code ushr 4)
            crc = (crc shl 8) and 0xFFFF
            crc = crc xor code
            code = (code shl 5) and 0xFFFF
            crc = crc xor code
            code = (code shl 7) and 0xFFFF
            crc = crc xor code
        }

        // Return little-endian
        return byteArrayOf(crc.toByte(), (crc ushr 8).toByte())
    }


    private fun base32Decode(data: ByteArray): ByteArray {
        // Validate all characters are in alphabet
        require(Base32Codec.isInAlphabet(data)) {
            "Invalid base32 encoded string"
        }

        return Base32Codec.decode(data)
    }
}
