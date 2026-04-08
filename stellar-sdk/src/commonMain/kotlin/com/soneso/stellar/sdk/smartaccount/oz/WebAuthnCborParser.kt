//
//  WebAuthnCborParser.kt
//  Stellar SDK Kotlin Multiplatform
//
//  Copyright (c) 2026 Soneso. All rights reserved.
//

package com.soneso.stellar.sdk.smartaccount.oz

/**
 * Pure Kotlin CBOR parsing utilities for WebAuthn attestation and authenticator data.
 *
 * This object consolidates CBOR parsing logic that was previously duplicated across the
 * Android, JavaScript, and Apple WebAuthn provider implementations. It has no platform
 * dependencies and can be called from any source set.
 *
 * All methods are designed to be resilient to malformed or truncated input. They return
 * null instead of throwing when data cannot be parsed, allowing callers to implement
 * graceful fallback strategies.
 *
 * Authenticator data structure (WebAuthn specification):
 * ```
 * [0..31]      rpIdHash         (32 bytes, SHA-256 of the relying party ID)
 * [32]         flags            (1 byte, bit field)
 * [33..36]     signCount        (4 bytes, big-endian)
 * [37..52]     aaguid           (16 bytes, if AT flag set)
 * [53..54]     credentialIdLen  (2 bytes, big-endian uint16, if AT flag set)
 * [55..55+N-1] credentialId     (N bytes, if AT flag set)
 * [55+N..]     COSE public key  (variable, if AT flag set)
 * ```
 *
 * Flag bits at offset 32 (relevant to this parser):
 * - Bit 6 (0x40): AT -- Attested credential data included
 * - Bit 3 (0x08): BE -- Backup Eligibility (multi-device credential)
 * - Bit 4 (0x10): BS -- Backup State (currently backed up)
 */
internal object WebAuthnCborParser {

    // =========================================================================
    // Named constants for all magic numbers
    // =========================================================================

    /** Minimum length of valid authenticator data (rpIdHash + flags + signCount). */
    const val AUTH_DATA_MIN_LENGTH: Int = 37

    /** Byte offset of the flags field within authenticator data. */
    const val FLAGS_OFFSET: Int = 32

    /** Flag bit indicating Backup Eligibility (multi-device credential). */
    const val FLAG_BE: Int = 0x08

    /** Flag bit indicating Backup State (credential is currently backed up). */
    const val FLAG_BS: Int = 0x10

    /**
     * Minimum size of the attested credential data header within authenticator data:
     * rpIdHash (32) + flags (1) + signCount (4) + aaguid (16) + credentialIdLen (2) = 55.
     */
    const val ATTESTED_CRED_DATA_HEADER_SIZE: Int = 55

    /** Size in bytes of an uncompressed secp256r1 public key (0x04 prefix + X + Y). */
    const val UNCOMPRESSED_KEY_SIZE: Int = 65

    /** Uncompressed EC point prefix byte (SEC 1). */
    const val UNCOMPRESSED_KEY_PREFIX: Byte = 0x04

    /** String constant for single-device credential type. */
    const val DEVICE_TYPE_SINGLE: String = "singleDevice"

    /** String constant for multi-device (cloud-synced) credential type. */
    const val DEVICE_TYPE_MULTI: String = "multiDevice"

    /**
     * CBOR-encoded key for the "authData" field in a WebAuthn attestation object map.
     *
     * Encoding: 0x68 (text string, length 8) followed by ASCII bytes for "authData".
     */
    private val AUTH_DATA_CBOR_KEY: ByteArray = byteArrayOf(
        0x68,                                               // text string, length 8
        0x61, 0x75, 0x74, 0x68, 0x44, 0x61, 0x74, 0x61    // "authData"
    )

    /**
     * The 10-byte CBOR map prefix that begins an ES256 COSE key for secp256r1.
     *
     * Encodes the first four CBOR map entries:
     * - 1 (kty): 2 (EC2)
     * - 3 (alg): -7 (ES256)
     * - -1 (crv): 1 (P-256)
     * - -2 (x): bstr of length 32 (header only)
     */
    private val COSE_ES256_KEY_PREFIX: ByteArray = byteArrayOf(
        0xa5.toByte(), 0x01, 0x02, 0x03, 0x26.toByte(),
        0x20.toByte(), 0x01, 0x21, 0x58, 0x20.toByte()
    )

    /**
     * Parsed authenticator flags from WebAuthn authenticator data.
     *
     * @property deviceType "singleDevice" if the credential is device-bound,
     *   "multiDevice" if it is eligible for cloud sync. Null if the flags byte
     *   could not be read.
     * @property backedUp True if the credential is currently backed up or synced to cloud,
     *   false if not, null if the flags byte could not be read.
     */
    data class AuthenticatorFlags(
        val deviceType: String?,
        val backedUp: Boolean?
    )

    // =========================================================================
    // 1. Attestation object parsing
    // =========================================================================

    /**
     * Extracts the raw authenticator data from a CBOR-encoded WebAuthn attestation object.
     *
     * A WebAuthn attestation object is a CBOR map with the following structure:
     * ```
     * {
     *   "fmt":      text string (attestation format, e.g. "none", "packed")
     *   "attStmt":  map        (attestation statement, may be empty)
     *   "authData": bstr       (raw authenticator data bytes)
     * }
     * ```
     *
     * This method performs a full CBOR map iteration to locate the "authData" key and
     * return its byte string value. The iteration approach is more robust than pattern
     * matching because it correctly handles variable-length values in preceding map entries
     * (such as non-empty attestation statements).
     *
     * @param attestationObject Raw CBOR-encoded attestation object bytes.
     * @return Authenticator data bytes, or null if the attestation object is malformed,
     *   empty, not a CBOR map, or does not contain an "authData" key.
     */
    fun extractAuthenticatorDataFromAttestation(attestationObject: ByteArray): ByteArray? {
        if (attestationObject.isEmpty()) return null

        var offset = 0
        val firstByte = attestationObject[offset].toInt() and 0xFF

        // Verify top-level CBOR type is a map (major type 5)
        val majorType = firstByte shr 5
        if (majorType != 5) return null

        val additionalInfo = firstByte and 0x1F
        val mapSize: Int

        when {
            additionalInfo < 24 -> {
                mapSize = additionalInfo
                offset = 1
            }
            additionalInfo == 24 -> {
                if (offset + 1 >= attestationObject.size) return null
                mapSize = attestationObject[offset + 1].toInt() and 0xFF
                offset = 2
            }
            else -> return null // Maps with more than 255 entries are not expected in attestation
        }

        // Iterate through map key-value pairs looking for "authData"
        for (i in 0 until mapSize) {
            if (offset >= attestationObject.size) return null

            val keyResult = readCborTextString(attestationObject, offset) ?: return null
            val key = keyResult.first
            offset = keyResult.second

            if (key == "authData") {
                val valueResult = readCborByteString(attestationObject, offset) ?: return null
                return valueResult.first
            } else {
                // Skip the value for this key to advance to the next map entry
                offset = skipCborValue(attestationObject, offset) ?: return null
            }
        }

        return null
    }

    // =========================================================================
    // 2. COSE key extraction
    // =========================================================================

    /**
     * Extracts the uncompressed secp256r1 public key from a CBOR-encoded COSE key.
     *
     * A COSE key for ES256 (secp256r1) is a CBOR map. The relevant entries are:
     * - Key label -2 (CBOR: 0x21): X coordinate (32-byte bstr)
     * - Key label -3 (CBOR: 0x22): Y coordinate (32-byte bstr)
     *
     * CBOR encodes negative integers as: `-(n+1)`, so -2 is encoded as major type 1,
     * additional info 1 (byte 0x21), and -3 as major type 1, additional info 2 (byte 0x22).
     *
     * If the data does not begin with a CBOR map header, or if either coordinate cannot
     * be found by map iteration, this method falls back to pattern matching using
     * [COSE_ES256_KEY_PREFIX] to locate the key structure directly.
     *
     * @param coseKeyData Raw CBOR-encoded COSE key bytes, starting at the first byte of
     *   the COSE map (i.e., the portion of authenticator data from the credential offset
     *   onwards).
     * @return Uncompressed secp256r1 public key (65 bytes: 0x04 || X || Y), or null if
     *   neither the map iteration nor the pattern-matching fallback can locate valid
     *   32-byte X and Y coordinates.
     */
    fun extractPublicKeyFromCoseKey(coseKeyData: ByteArray): ByteArray? {
        if (coseKeyData.isEmpty()) return null

        val firstByte = coseKeyData[0].toInt() and 0xFF
        val majorType = firstByte shr 5

        if (majorType == 5) {
            // Full CBOR map iteration
            val result = extractCoseKeyByMapIteration(coseKeyData)
            if (result != null) return result
        }

        // Fallback: pattern matching for the well-known ES256 key prefix
        return extractPublicKeyByPattern(coseKeyData)
    }

    /**
     * Iterates the CBOR map in the given data to find key labels -2 (X) and -3 (Y).
     *
     * @param coseKeyData Raw CBOR data beginning with a map header.
     * @return Uncompressed 65-byte public key, or null if X or Y are not found or
     *   the map header is malformed.
     */
    private fun extractCoseKeyByMapIteration(coseKeyData: ByteArray): ByteArray? {
        val firstByte = coseKeyData[0].toInt() and 0xFF
        val additionalInfo = firstByte and 0x1F

        val mapSize: Int
        var offset: Int

        when {
            additionalInfo < 24 -> {
                mapSize = additionalInfo
                offset = 1
            }
            additionalInfo == 24 -> {
                if (coseKeyData.size < 2) return null
                mapSize = coseKeyData[1].toInt() and 0xFF
                offset = 2
            }
            else -> return null
        }

        var x: ByteArray? = null
        var y: ByteArray? = null

        for (i in 0 until mapSize) {
            if (offset >= coseKeyData.size) break

            val keyByte = coseKeyData[offset].toInt() and 0xFF
            val keyMajorType = keyByte shr 5
            val keyInfo = keyByte and 0x1F

            when {
                keyMajorType == 1 && keyInfo == 1 -> {
                    // CBOR negative integer 0x21 = -2 => X coordinate
                    offset++
                    val result = readCborByteString(coseKeyData, offset)
                    if (result != null) {
                        x = result.first
                        offset = result.second
                    } else {
                        offset = skipCborValue(coseKeyData, offset) ?: return null
                    }
                }
                keyMajorType == 1 && keyInfo == 2 -> {
                    // CBOR negative integer 0x22 = -3 => Y coordinate
                    offset++
                    val result = readCborByteString(coseKeyData, offset)
                    if (result != null) {
                        y = result.first
                        offset = result.second
                    } else {
                        offset = skipCborValue(coseKeyData, offset) ?: return null
                    }
                }
                else -> {
                    // Skip this key-value pair
                    offset = skipCborHead(coseKeyData, offset) ?: return null
                    offset = skipCborValue(coseKeyData, offset) ?: return null
                }
            }

            if (x != null && y != null) break
        }

        if (x == null || y == null || x.size != 32 || y.size != 32) return null

        return buildUncompressedKey(x, y)
    }

    /**
     * Pattern-matching fallback that searches for the ES256 COSE key prefix and extracts
     * X and Y coordinates from the fixed offsets that follow the prefix.
     *
     * Searches for [COSE_ES256_KEY_PREFIX] (10 bytes) anywhere in [data]. If found:
     * - X is the 32 bytes immediately after the prefix.
     * - Y is the 32 bytes starting 3 bytes after X (the 3 bytes are the CBOR-encoded
     *   map key -3 followed by a 32-byte bstr header: `0x22 0x58 0x20`).
     *
     * @param data Byte array to search within.
     * @return Uncompressed 65-byte public key, or null if the prefix is not found or
     *   there is insufficient data following it.
     */
    private fun extractPublicKeyByPattern(data: ByteArray): ByteArray? {
        val prefixIndex = findSubarray(data, COSE_ES256_KEY_PREFIX)
        if (prefixIndex < 0) return null

        val xStart = prefixIndex + COSE_ES256_KEY_PREFIX.size
        val yStart = xStart + 32 + 3 // 3 bytes: CBOR key -3 (0x22) + bstr header (0x58 0x20)
        val requiredLength = yStart + 32

        if (data.size < requiredLength) return null

        val x = data.copyOfRange(xStart, xStart + 32)
        val y = data.copyOfRange(yStart, yStart + 32)

        return buildUncompressedKey(x, y)
    }

    // =========================================================================
    // 3. SPKI key extraction
    // =========================================================================

    /**
     * Extracts an uncompressed secp256r1 public key from SubjectPublicKeyInfo (SPKI) bytes.
     *
     * The SPKI structure for a P-256 key (RFC 5480 / SEC 1) is:
     * ```
     * SEQUENCE {
     *   SEQUENCE {
     *     OID 1.2.840.10045.2.1   (id-ecPublicKey)
     *     OID 1.2.840.10045.3.1.7 (secp256r1 / prime256v1)
     *   }
     *   BIT STRING { 0x04 || X (32 bytes) || Y (32 bytes) }
     * }
     * ```
     *
     * The total SPKI encoding is typically 91 bytes. The uncompressed public key
     * (65 bytes) occupies the last 65 bytes of the structure and always starts
     * with the 0x04 uncompressed point prefix.
     *
     * This method uses pure byte slicing: if [spkiBytes] is at least 65 bytes long
     * and the byte at `size - 65` equals 0x04, the last 65 bytes are returned.
     *
     * @param spkiBytes Raw SPKI/DER-encoded public key bytes (typically from
     *   `response.getPublicKey()` in the WebAuthn browser API).
     * @return Uncompressed 65-byte secp256r1 public key (0x04 || X || Y), or null if
     *   the input is shorter than 65 bytes or does not have the expected 0x04 prefix
     *   at the computed offset.
     */
    fun extractPublicKeyFromSpki(spkiBytes: ByteArray): ByteArray? {
        if (spkiBytes.size < UNCOMPRESSED_KEY_SIZE) return null

        val candidateStart = spkiBytes.size - UNCOMPRESSED_KEY_SIZE
        if (spkiBytes[candidateStart] != UNCOMPRESSED_KEY_PREFIX) return null

        return spkiBytes.copyOfRange(candidateStart, spkiBytes.size)
    }

    // =========================================================================
    // 4. Authenticator flags parsing
    // =========================================================================

    /**
     * Parses the flags byte from raw authenticator data and extracts device type and
     * backup state.
     *
     * The flags byte is located at offset [FLAGS_OFFSET] (32) within authenticator data.
     * Two bits are relevant:
     * - Bit 3 ([FLAG_BE] = 0x08): BE -- Backup Eligibility. When set, the credential is
     *   eligible for cloud synchronisation across devices (device type = [DEVICE_TYPE_MULTI]).
     *   When clear, the credential is bound to a single device ([DEVICE_TYPE_SINGLE]).
     * - Bit 4 ([FLAG_BS] = 0x10): BS -- Backup State. When set, the credential is currently
     *   backed up or synced to a cloud provider.
     *
     * If [authenticatorData] is null or shorter than [FLAGS_OFFSET] + 1 bytes, both
     * [AuthenticatorFlags.deviceType] and [AuthenticatorFlags.backedUp] are null, indicating
     * that the device type and backup state are genuinely unknown.
     *
     * @param authenticatorData Raw authenticator data bytes (directly from the authenticator
     *   response, not CBOR-wrapped). May be null.
     * @return Parsed [AuthenticatorFlags] containing device type and backup status.
     *   Fields are null when the flags byte cannot be read.
     */
    fun parseAuthenticatorFlags(authenticatorData: ByteArray?): AuthenticatorFlags {
        if (authenticatorData == null || authenticatorData.size <= FLAGS_OFFSET) {
            return AuthenticatorFlags(deviceType = null, backedUp = null)
        }

        val flags = authenticatorData[FLAGS_OFFSET].toInt() and 0xFF

        val deviceType = if (flags and FLAG_BE != 0) DEVICE_TYPE_MULTI else DEVICE_TYPE_SINGLE
        val backedUp = (flags and FLAG_BS) != 0

        return AuthenticatorFlags(deviceType = deviceType, backedUp = backedUp)
    }

    // =========================================================================
    // 5. Low-level CBOR helpers (accessible for testing)
    // =========================================================================

    /**
     * Reads a CBOR byte string (major type 2) at the given offset.
     *
     * Supports byte strings with lengths encoded as:
     * - 0 to 23 bytes (inline additional info)
     * - 1-byte length prefix (additional info 24)
     * - 2-byte big-endian length prefix (additional info 25)
     * - 4-byte big-endian length prefix (additional info 26, with overflow guard)
     *
     * @param data The CBOR-encoded byte array.
     * @param offset Byte offset of the CBOR byte string header.
     * @return A [Pair] of (decoded bytes, offset after the byte string), or null if
     *   the data is truncated, the major type is not 2, or a 4-byte length overflows
     *   [Int.MAX_VALUE].
     */
    fun readCborByteString(data: ByteArray, offset: Int): Pair<ByteArray, Int>? {
        if (offset >= data.size) return null

        val firstByte = data[offset].toInt() and 0xFF
        val majorType = firstByte shr 5

        if (majorType != 2) return null // Not a byte string

        val additionalInfo = firstByte and 0x1F
        val length: Int
        val dataStart: Int

        when {
            additionalInfo < 24 -> {
                length = additionalInfo
                dataStart = offset + 1
            }
            additionalInfo == 24 -> {
                if (offset + 1 >= data.size) return null
                length = data[offset + 1].toInt() and 0xFF
                dataStart = offset + 2
            }
            additionalInfo == 25 -> {
                if (offset + 2 >= data.size) return null
                length = ((data[offset + 1].toInt() and 0xFF) shl 8) or
                    (data[offset + 2].toInt() and 0xFF)
                dataStart = offset + 3
            }
            additionalInfo == 26 -> {
                if (offset + 4 >= data.size) return null
                val raw = ((data[offset + 1].toInt() and 0xFF) shl 24) or
                    ((data[offset + 2].toInt() and 0xFF) shl 16) or
                    ((data[offset + 3].toInt() and 0xFF) shl 8) or
                    (data[offset + 4].toInt() and 0xFF)
                if (raw < 0) return null // Integer overflow guard: length > Int.MAX_VALUE
                length = raw
                dataStart = offset + 5
            }
            else -> return null // Indefinite-length or 8-byte length not supported
        }

        if (dataStart + length > data.size) return null

        val bytes = data.copyOfRange(dataStart, dataStart + length)
        return Pair(bytes, dataStart + length)
    }

    /**
     * Reads a CBOR text string (major type 3) at the given offset.
     *
     * Supports text strings with lengths encoded as:
     * - 0 to 23 bytes (inline additional info)
     * - 1-byte length prefix (additional info 24)
     * - 2-byte big-endian length prefix (additional info 25)
     *
     * The raw bytes are decoded as UTF-8 via [decodeToString].
     *
     * @param data The CBOR-encoded byte array.
     * @param offset Byte offset of the CBOR text string header.
     * @return A [Pair] of (decoded string, offset after the text string), or null if
     *   the data is truncated or the major type is not 3.
     */
    fun readCborTextString(data: ByteArray, offset: Int): Pair<String, Int>? {
        if (offset >= data.size) return null

        val firstByte = data[offset].toInt() and 0xFF
        val majorType = firstByte shr 5

        if (majorType != 3) return null // Not a text string

        val additionalInfo = firstByte and 0x1F
        val length: Int
        val dataStart: Int

        when {
            additionalInfo < 24 -> {
                length = additionalInfo
                dataStart = offset + 1
            }
            additionalInfo == 24 -> {
                if (offset + 1 >= data.size) return null
                length = data[offset + 1].toInt() and 0xFF
                dataStart = offset + 2
            }
            additionalInfo == 25 -> {
                if (offset + 2 >= data.size) return null
                length = ((data[offset + 1].toInt() and 0xFF) shl 8) or
                    (data[offset + 2].toInt() and 0xFF)
                dataStart = offset + 3
            }
            else -> return null
        }

        if (dataStart + length > data.size) return null

        val text = data.copyOfRange(dataStart, dataStart + length).decodeToString()
        return Pair(text, dataStart + length)
    }

    /**
     * Reads the length value from a CBOR item head (applicable to major types 2, 3, 4, 5).
     *
     * Does not read the actual content — only the length field and the header bytes.
     *
     * Supports lengths encoded as:
     * - Inline (0..23)
     * - 1-byte (additional info 24)
     * - 2-byte big-endian (additional info 25)
     * - 4-byte big-endian (additional info 26, with overflow guard)
     *
     * @param data The CBOR-encoded byte array.
     * @param offset Byte offset of the CBOR item head.
     * @return A [Pair] of (length value, offset immediately after the head bytes), or
     *   null if the data is truncated or the additional info encodes an unsupported
     *   or overflowing length.
     */
    fun readCborLength(data: ByteArray, offset: Int): Pair<Int, Int>? {
        if (offset >= data.size) return null

        val firstByte = data[offset].toInt() and 0xFF
        val additionalInfo = firstByte and 0x1F

        return when {
            additionalInfo < 24 -> Pair(additionalInfo, offset + 1)
            additionalInfo == 24 -> {
                if (offset + 1 >= data.size) null
                else Pair(data[offset + 1].toInt() and 0xFF, offset + 2)
            }
            additionalInfo == 25 -> {
                if (offset + 2 >= data.size) null
                else Pair(
                    ((data[offset + 1].toInt() and 0xFF) shl 8) or
                        (data[offset + 2].toInt() and 0xFF),
                    offset + 3
                )
            }
            additionalInfo == 26 -> {
                if (offset + 4 >= data.size) null
                else {
                    val raw = ((data[offset + 1].toInt() and 0xFF) shl 24) or
                        ((data[offset + 2].toInt() and 0xFF) shl 16) or
                        ((data[offset + 3].toInt() and 0xFF) shl 8) or
                        (data[offset + 4].toInt() and 0xFF)
                    if (raw < 0) null // Integer overflow guard
                    else Pair(raw, offset + 5)
                }
            }
            else -> null
        }
    }

    /**
     * Skips a single CBOR value at the given offset and returns the offset of the next value.
     *
     * Handles all standard CBOR major types:
     * - 0 (unsigned int): skip head only
     * - 1 (negative int): skip head only
     * - 2 (byte string): skip head + content
     * - 3 (text string): skip head + content
     * - 4 (array): skip head + N recursively skipped items
     * - 5 (map): skip head + N recursively skipped key-value pairs
     * - 6 (tag): skip tag head + 1 recursively skipped tagged value
     * - 7 (float/simple): skip head only (1, 2, 3, 5, or 9 bytes depending on additional info)
     *
     * @param data The CBOR-encoded byte array.
     * @param offset Byte offset of the CBOR value to skip.
     * @return The byte offset immediately after the skipped value, or null if the data
     *   is truncated or an unsupported encoding is encountered.
     */
    fun skipCborValue(data: ByteArray, offset: Int): Int? {
        if (offset >= data.size) return null

        val firstByte = data[offset].toInt() and 0xFF
        val majorType = firstByte shr 5
        val additionalInfo = firstByte and 0x1F

        return when (majorType) {
            0, 1 -> {
                // Unsigned integer or negative integer: head only
                skipCborHead(data, offset)
            }
            2, 3 -> {
                // Byte string or text string: head + content
                val lengthResult = readCborLength(data, offset) ?: return null
                val length = lengthResult.first
                val contentStart = lengthResult.second
                if (contentStart + length > data.size) return null
                contentStart + length
            }
            4 -> {
                // Array: head + N items
                val lengthResult = readCborLength(data, offset) ?: return null
                val count = lengthResult.first
                var pos = lengthResult.second
                for (j in 0 until count) {
                    pos = skipCborValue(data, pos) ?: return null
                }
                pos
            }
            5 -> {
                // Map: head + N key-value pairs
                val lengthResult = readCborLength(data, offset) ?: return null
                val count = lengthResult.first
                var pos = lengthResult.second
                for (j in 0 until count) {
                    pos = skipCborValue(data, pos) ?: return null // key
                    pos = skipCborValue(data, pos) ?: return null // value
                }
                pos
            }
            6 -> {
                // Tag: skip tag head + tagged value
                val headEnd = skipCborHead(data, offset) ?: return null
                skipCborValue(data, headEnd)
            }
            7 -> {
                // Simple value or float
                when (additionalInfo) {
                    in 0..23 -> offset + 1
                    24 -> if (offset + 1 < data.size) offset + 2 else null
                    25 -> if (offset + 2 < data.size) offset + 3 else null
                    26 -> if (offset + 4 < data.size) offset + 5 else null
                    27 -> if (offset + 8 < data.size) offset + 9 else null
                    else -> null
                }
            }
            else -> null
        }
    }

    /**
     * Skips the initial byte (and any additional info bytes) of a CBOR item head.
     *
     * This advances past the type/length header without reading or skipping the content.
     * Used for integer types (major types 0 and 1) where there is no subsequent content,
     * and internally within [skipCborValue] for tags.
     *
     * @param data The CBOR-encoded byte array.
     * @param offset Byte offset of the CBOR item head.
     * @return The byte offset immediately after the head, or null if the data is truncated.
     */
    fun skipCborHead(data: ByteArray, offset: Int): Int? {
        if (offset >= data.size) return null

        val firstByte = data[offset].toInt() and 0xFF
        val additionalInfo = firstByte and 0x1F

        return when {
            additionalInfo < 24 -> offset + 1
            additionalInfo == 24 -> if (offset + 1 < data.size) offset + 2 else null
            additionalInfo == 25 -> if (offset + 2 < data.size) offset + 3 else null
            additionalInfo == 26 -> if (offset + 4 < data.size) offset + 5 else null
            additionalInfo == 27 -> if (offset + 8 < data.size) offset + 9 else null
            else -> null
        }
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    /**
     * Constructs an uncompressed secp256r1 public key byte array from X and Y coordinates.
     *
     * @param x 32-byte X coordinate.
     * @param y 32-byte Y coordinate.
     * @return 65-byte array: [UNCOMPRESSED_KEY_PREFIX] || x || y.
     */
    private fun buildUncompressedKey(x: ByteArray, y: ByteArray): ByteArray {
        val publicKey = ByteArray(UNCOMPRESSED_KEY_SIZE)
        publicKey[0] = UNCOMPRESSED_KEY_PREFIX
        x.copyInto(publicKey, 1)
        y.copyInto(publicKey, 33)
        return publicKey
    }

    /**
     * Searches for the first occurrence of [needle] within [haystack].
     *
     * Uses a naive linear scan. WebAuthn attestation objects are small (typically
     * a few hundred bytes), so the performance of a naive scan is acceptable.
     *
     * @param haystack The byte array to search within.
     * @param needle The byte array to search for.
     * @return The index of the first occurrence of [needle] in [haystack], or -1 if
     *   [needle] is not found or is longer than [haystack].
     */
    private fun findSubarray(haystack: ByteArray, needle: ByteArray): Int {
        if (needle.isEmpty() || needle.size > haystack.size) return -1
        outer@ for (i in 0..haystack.size - needle.size) {
            for (j in needle.indices) {
                if (haystack[i + j] != needle[j]) continue@outer
            }
            return i
        }
        return -1
    }
}
