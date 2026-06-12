//
//  SmartAccountUtils.kt
//  Stellar SDK Kotlin Multiplatform
//
//  Copyright © 2026 Soneso. All rights reserved.
//

package com.soneso.stellar.sdk.smartaccount.core

import com.ionspin.kotlin.bignum.integer.BigInteger
import com.soneso.stellar.sdk.KeyPair
import com.soneso.stellar.sdk.StrKey
import com.soneso.stellar.sdk.crypto.getSha256Crypto
import com.soneso.stellar.sdk.xdr.ContractIDPreimageFromAddressXdr
import com.soneso.stellar.sdk.xdr.ContractIDPreimageXdr
import com.soneso.stellar.sdk.xdr.HashIDPreimageContractIDXdr
import com.soneso.stellar.sdk.xdr.HashIDPreimageXdr
import com.soneso.stellar.sdk.xdr.HashXdr
import com.soneso.stellar.sdk.xdr.SCAddressXdr
import com.soneso.stellar.sdk.xdr.Uint256Xdr
import com.soneso.stellar.sdk.xdr.XdrWriter

/**
 * Utility functions for Smart Account operations.
 *
 * Provides cryptographic utilities for WebAuthn signature processing,
 * public key extraction, and contract address derivation.
 */
object SmartAccountUtils {

    // secp256r1 curve order and half-order, pre-computed as companion-level constants.
    // Reference: https://github.com/stellar/stellar-protocol/discussions/1435#discussioncomment-8809175
    private val curveOrder = BigInteger.parseString(
        "ffffffff00000000ffffffffffffffffbce6faada7179e84f3b9cac2fc632551",
        16
    )
    private val halfCurveOrder = curveOrder / BigInteger.TWO

    // secp256r1 curve parameters for point-on-curve validation (FIPS 186-4 / SEC 2).
    // p:  the prime field modulus
    // a:  curve coefficient a = p - 3
    // b:  curve coefficient b
    private val curveP = BigInteger.parseString(
        "ffffffff00000001000000000000000000000000ffffffffffffffffffffffff",
        16
    )
    private val curveA = BigInteger.parseString(
        "ffffffff00000001000000000000000000000000fffffffffffffffffffffffc",
        16
    )
    private val curveB = BigInteger.parseString(
        "5ac635d8aa3a93e7b3ebbd55769886bc651d06b0cc53b0f63bce3c3e27d2604b",
        16
    )

    // ========================================================================
    // Signature Normalization
    // ========================================================================

    /**
     * Parses a DER-encoded secp256r1 signature and returns R and S as BigIntegers.
     *
     * Validates the full DER structure, strips leading 0x00 padding bytes from both
     * components, and enforces secp256r1-specific constraints:
     * - R and S must each be at most 32 bytes after stripping
     * - R and S must not be all-zero (invalid ECDSA values)
     * - R and S must each be strictly less than the curve order
     *
     * DER format: `0x30 [total_len] 0x02 [r_len] [r_bytes] 0x02 [s_len] [s_bytes]`
     *
     * @param derSignature DER-encoded signature bytes
     * @return Pair of (r, s) as validated BigIntegers in [1, n-1]
     * @throws ValidationException.InvalidInput if the DER structure is malformed or
     *         the R/S values violate secp256r1 constraints
     */
    internal fun parseDerSignature(derSignature: ByteArray): Pair<BigInteger, BigInteger> {
        if (derSignature.size < 8 || derSignature[0] != 0x30.toByte()) {
            throw ValidationException.invalidInput(
                "derSignature",
                "Invalid DER signature format"
            )
        }

        // Validate total length field: byte 1 encodes the length of the remaining contents,
        // so the full signature must be exactly 2 + totalLength bytes.
        val totalLength = derSignature[1].toInt() and 0xFF
        if (2 + totalLength != derSignature.size) {
            throw ValidationException.invalidInput(
                "derSignature",
                "Invalid DER signature format: declared length does not match actual size"
            )
        }

        // Parse R component
        var offset = 2
        if (offset + 1 >= derSignature.size || derSignature[offset] != 0x02.toByte()) {
            throw ValidationException.invalidInput(
                "derSignature",
                "Invalid DER signature format: missing r component marker"
            )
        }

        val rLength = derSignature[offset + 1].toInt() and 0xFF
        if (rLength == 0 || offset + 2 + rLength > derSignature.size) {
            throw ValidationException.invalidInput(
                "derSignature",
                "Invalid DER signature format: truncated r component"
            )
        }

        var r = derSignature.copyOfRange(offset + 2, offset + 2 + rLength)

        // Strip leading 0x00 padding from R
        while (r.size > 1 && r[0] == 0x00.toByte()) {
            r = r.copyOfRange(1, r.size)
        }

        // Parse S component
        offset = offset + 2 + rLength
        if (offset + 1 >= derSignature.size || derSignature[offset] != 0x02.toByte()) {
            throw ValidationException.invalidInput(
                "derSignature",
                "Invalid DER signature format: missing s component marker"
            )
        }

        val sLength = derSignature[offset + 1].toInt() and 0xFF
        if (sLength == 0 || offset + 2 + sLength > derSignature.size) {
            throw ValidationException.invalidInput(
                "derSignature",
                "Invalid DER signature format: truncated s component"
            )
        }

        var s = derSignature.copyOfRange(offset + 2, offset + 2 + sLength)

        // Strip leading 0x00 padding from S
        while (s.size > 1 && s[0] == 0x00.toByte()) {
            s = s.copyOfRange(1, s.size)
        }

        // Validate no trailing bytes after S component
        val endOffset = offset + 2 + sLength
        if (endOffset != derSignature.size) {
            throw ValidationException.invalidInput(
                "derSignature",
                "Invalid DER signature format: trailing bytes after s component"
            )
        }

        // secp256r1 is a 256-bit curve: R and S must each fit in 32 bytes
        if (r.size > 32) {
            throw ValidationException.invalidInput(
                "derSignature",
                "Invalid DER signature: r component exceeds 32 bytes after stripping (${r.size} bytes)"
            )
        }
        if (s.size > 32) {
            throw ValidationException.invalidInput(
                "derSignature",
                "Invalid DER signature: s component exceeds 32 bytes after stripping (${s.size} bytes)"
            )
        }

        // R = 0 and S = 0 are invalid ECDSA values
        if (r.size == 1 && r[0] == 0x00.toByte()) {
            throw ValidationException.invalidInput(
                "derSignature",
                "Invalid DER signature: r component is zero (invalid ECDSA value)"
            )
        }
        if (s.size == 1 && s[0] == 0x00.toByte()) {
            throw ValidationException.invalidInput(
                "derSignature",
                "Invalid DER signature: s component is zero (invalid ECDSA value)"
            )
        }

        // R and S must be in [1, n-1]; validate the upper bound here.
        // The zero check above already guarantees the lower bound (> 0).
        val rBigInt = bytesToUnsignedBigInteger(r)
        if (rBigInt >= curveOrder) {
            throw ValidationException.invalidInput(
                "derSignature",
                "Invalid DER signature: r component exceeds curve order"
            )
        }
        val sBigInt = bytesToUnsignedBigInteger(s)
        if (sBigInt >= curveOrder) {
            throw ValidationException.invalidInput(
                "derSignature",
                "Invalid DER signature: s component exceeds curve order"
            )
        }

        return Pair(rBigInt, sBigInt)
    }

    /**
     * Normalizes a DER-encoded secp256r1 signature to compact format with low-S normalization.
     *
     * This function performs the following steps:
     * 1. Parses DER format via [parseDerSignature], which returns R and S as BigIntegers
     * 2. Normalizes S to low-S form if S > halfOrder
     * 3. Pads both R and S to exactly 32 bytes
     * 4. Returns concatenated R || S (64 bytes total)
     *
     * Low-S normalization ensures that s values greater than half the curve order
     * are converted to their complements (n - s), which is required for Stellar/Soroban
     * signature verification.
     *
     * @param derSignature DER-encoded signature bytes
     * @return Compact 64-byte signature (32-byte r || 32-byte s)
     * @throws ValidationException.InvalidInput if the DER format is invalid
     *
     * Example:
     * ```kotlin
     * val derSig = byteArrayOf(...)  // DER-encoded signature from WebAuthn
     * val compactSig = SmartAccountUtils.normalizeSignature(derSig)
     * // compactSig is now 64 bytes: r (32 bytes) || s (32 bytes)
     * ```
     */
    fun normalizeSignature(derSignature: ByteArray): ByteArray {
        val (rBigInt, sBigInt) = parseDerSignature(derSignature)

        // If s > halfOrder, normalize: s = n - s
        val sNormalized = if (sBigInt > halfCurveOrder) curveOrder - sBigInt else sBigInt

        val rPadded = bigIntegerToUnsignedBytes(rBigInt, 32)
        val sPadded = bigIntegerToUnsignedBytes(sNormalized, 32)

        return rPadded + sPadded
    }

    // ========================================================================
    // Public Key Extraction
    // ========================================================================

    /**
     * Extracts the secp256r1 public key from a WebAuthn registration response using
     * multiple fallback strategies.
     *
     * Tries three strategies in order:
     *
     * 1. **Direct public key**: If [publicKey] is provided, validate it as a 65-byte
     *    uncompressed secp256r1 key (0x04 prefix) and verify the point lies on the
     *    secp256r1 curve. This is the primary path -- WebAuthnProvider implementations
     *    should return the public key directly in [WebAuthnRegistrationResult.publicKey].
     *
     * 2. **Authenticator data parsing**: If [authenticatorData] is provided, parse the
     *    attested credential data structure to extract X/Y coordinates from the COSE key.
     *
     * 3. **Attestation object pattern matching**: If [attestationObject] is provided,
     *    search for the COSE key prefix pattern and extract X/Y coordinates.
     *
     * At least one of the three parameters must be non-null.
     *
     * **Compressed keys (0x02/0x03 prefix) are not supported.** WebAuthn platforms must
     * provide uncompressed keys. If a compressed key is detected, an exception is thrown
     * immediately and no fallback is attempted.
     *
     * @param publicKey Optional direct public key bytes (may include COSE/SPKI wrapping;
     *        the last 65 bytes are used if longer than 65 bytes)
     * @param authenticatorData Optional raw authenticator data from WebAuthn registration
     * @param attestationObject Optional raw attestation object from WebAuthn registration
     * @return Uncompressed secp256r1 public key (65 bytes: 0x04 prefix + X + Y)
     * @throws ValidationException.InvalidInput if a compressed key prefix (0x02 or 0x03)
     *         is detected, or if the public key cannot be extracted from any provided source
     *
     * Example:
     * ```kotlin
     * // Strategy 1: Direct public key from provider
     * val pk = SmartAccountUtils.extractPublicKeyFromRegistration(
     *     publicKey = registrationResult.publicKey
     * )
     *
     * // Strategy 2: From authenticator data
     * val pk = SmartAccountUtils.extractPublicKeyFromRegistration(
     *     authenticatorData = authData
     * )
     *
     * // Strategy 3: From attestation object (fallback)
     * val pk = SmartAccountUtils.extractPublicKeyFromRegistration(
     *     attestationObject = attestObj
     * )
     * ```
     */
    fun extractPublicKeyFromRegistration(
        publicKey: ByteArray? = null,
        authenticatorData: ByteArray? = null,
        attestationObject: ByteArray? = null
    ): ByteArray {
        // Strategy 1: Try direct public key
        if (publicKey != null && publicKey.isNotEmpty()) {
            val candidate = if (publicKey.size > SmartAccountConstants.SECP256R1_PUBLIC_KEY_SIZE) {
                // Take the last 65 bytes (handles COSE/SPKI-wrapped keys)
                publicKey.copyOfRange(
                    publicKey.size - SmartAccountConstants.SECP256R1_PUBLIC_KEY_SIZE,
                    publicKey.size
                )
            } else {
                publicKey
            }

            if (candidate.size == SmartAccountConstants.SECP256R1_PUBLIC_KEY_SIZE &&
                candidate[0] == SmartAccountConstants.UNCOMPRESSED_PUBKEY_PREFIX
            ) {
                // Validate the extracted point before accepting it.
                // This ensures even directly-supplied keys are rejected when off-curve,
                // consistent with the validation applied by Strategies 2 and 3.
                validatePointOnCurve(
                    candidate.copyOfRange(1, 33),
                    candidate.copyOfRange(33, 65)
                )
                return candidate
            }

            // Compressed point formats (0x02 even-Y, 0x03 odd-Y) are not supported.
            // Soroban expects uncompressed keys and WebAuthn platforms must provide them.
            // Throw immediately — do not silently fall through to other strategies.
            if (candidate[0] == 0x02.toByte() || candidate[0] == 0x03.toByte()) {
                throw ValidationException.invalidInput(
                    "publicKey",
                    "Compressed secp256r1 key format (prefix 0x${candidate[0].toInt().and(0xFF).toString(16).padStart(2, '0')}) " +
                        "is not supported; the platform must provide an uncompressed key (0x04 prefix)"
                )
            }

            // Non-key data (e.g. CBOR/attestation bytes): fall through to other strategies.
        }

        // Strategy 2: Try authenticator data parsing
        if (authenticatorData != null) {
            val extracted = extractPublicKeyFromAuthenticatorData(authenticatorData)
            if (extracted != null) {
                return extracted
            }
        }

        // Strategy 3: Try attestation object pattern matching
        if (attestationObject != null) {
            return extractPublicKeyFromAttestationObject(attestationObject)
        }

        throw ValidationException.invalidInput(
            "registration",
            "Could not extract public key from attestation response: " +
                "no valid publicKey, authenticatorData, or attestationObject provided"
        )
    }

    /**
     * Extracts the secp256r1 public key from WebAuthn authenticator data.
     *
     * Parses the authenticator data structure (defined in the WebAuthn specification)
     * to locate and extract the COSE public key from the attested credential data.
     *
     * This is an internal extraction strategy used by [extractPublicKeyFromRegistration].
     * Callers should use [extractPublicKeyFromRegistration] instead of calling this directly.
     *
     * Authenticator data layout:
     * ```
     * [0..31]   rpIdHash          (32 bytes)
     * [32]      flags             (1 byte)
     * [33..36]  signCount         (4 bytes, big-endian)
     * [37..52]  aaguid            (16 bytes) -- if AT flag set
     * [53..54]  credentialIdLen   (2 bytes, big-endian) -- if AT flag set
     * [55..55+N-1] credentialId  (N bytes) -- if AT flag set
     * [55+N..]  COSE public key   (variable) -- if AT flag set
     * ```
     *
     * The COSE public key for ES256 (P-256) has this structure:
     * ```
     * a5 01 02 03 26 20 01 21 58 20   (10-byte prefix)
     * [32 bytes X coordinate]
     * 22 58 20                         (3-byte separator)
     * [32 bytes Y coordinate]
     * ```
     *
     * **Validation performed:**
     * - The 10-byte ES256 COSE key prefix is verified at the credential data offset before
     *   parsing. A prefix mismatch causes null to be returned (not an exception),
     *   consistent with the early-exit pattern for non-ES256 or missing credential data.
     * - Coordinate parsing is delegated to [WebAuthnCborParser.extractPublicKeyFromCoseKey],
     *   which handles CBOR map iteration (order-tolerant) and a strict pattern fallback
     *   that validates the 3-byte Y-coordinate separator (0x22, 0x58, 0x20).
     * - The extracted (X, Y) point is verified to lie on the secp256r1 curve.
     *
     * @param authenticatorData Raw authenticator data bytes
     * @return Uncompressed secp256r1 public key (65 bytes), or null if the data
     *         is too short, does not contain attested credential data, or does not carry
     *         an ES256 COSE key at the expected offset
     * @throws ValidationException.InvalidInput if the COSE key structure is malformed or
     *         the extracted key coordinates do not lie on the secp256r1 curve
     */
    internal fun extractPublicKeyFromAuthenticatorData(authenticatorData: ByteArray): ByteArray? {
        // Header minimum: 37 (rpIdHash + flags + signCount) + 16 (AAGUID) + 2 (credIdLen) = 55.
        // Only the header is checked here; the COSE key portion (prefix 10 + X 32 +
        // separator 3 + Y 32) is gated downstream by the prefix and full-key-length checks.
        if (authenticatorData.size < 55) {
            return null
        }

        // Check AT (attested credential data) flag (bit 6 of flags byte)
        val flags = authenticatorData[32].toInt() and 0xFF
        if (flags and 0x40 == 0) {
            // No attested credential data present
            return null
        }

        // Read credential ID length from bytes 53-54 (big-endian uint16)
        val credentialIdLength =
            ((authenticatorData[53].toInt() and 0xFF) shl 8) or
                (authenticatorData[54].toInt() and 0xFF)

        // COSE key starts at offset 55 + credentialIdLength
        // The COSE prefix is 10 bytes, then X is 32 bytes, separator is 3 bytes, Y is 32 bytes
        val coseKeyStart = 55 + credentialIdLength

        // Validate the 10-byte ES256 COSE key prefix before reading X and Y.
        // The prefix [0xA5, 0x01, 0x02, 0x03, 0x26, 0x20, 0x01, 0x21, 0x58, 0x20] encodes
        // the CBOR map header and the kty, alg, and crv parameters for an ES256 P-256 key.
        // If the prefix does not match, this is not an ES256 COSE key; return null so the
        // caller can fall through to the next extraction strategy.
        val expectedCosePrefix = byteArrayOf(
            0xA5.toByte(), 0x01, 0x02, 0x03, 0x26.toByte(), 0x20,
            0x01, 0x21, 0x58, 0x20
        )
        if (authenticatorData.size < coseKeyStart + 10) {
            return null
        }
        val actualPrefix = authenticatorData.copyOfRange(coseKeyStart, coseKeyStart + 10)
        if (!actualPrefix.contentEquals(expectedCosePrefix)) {
            return null  // Not an ES256 COSE key
        }

        // Full-key length guard. Returns null (not throw) so the public method falls
        // through to the attestation-object strategy when the buffer is truncated after
        // the prefix but before the complete key (prefix 10 + X 32 + separator 3 + Y 32).
        val fullKeyRequiredLength = coseKeyStart + 10 + 32 + 3 + 32
        if (authenticatorData.size < fullKeyRequiredLength) {
            return null
        }

        // All structural gates passed — delegate parsing to WebAuthnCborParser, which
        // handles CBOR map iteration (order-tolerant) and a strict pattern fallback
        // (validates the [0x22, 0x58, 0x20] Y-coordinate separator).
        val coseKeySlice = authenticatorData.copyOfRange(coseKeyStart, authenticatorData.size)
        val publicKey = WebAuthnCborParser.extractPublicKeyFromCoseKey(coseKeySlice)
            ?: throw ValidationException.invalidInput(
                "authenticatorData",
                "COSE key structure is invalid: could not extract secp256r1 " +
                    "coordinates from authenticator data"
            )

        // Verify the extracted point lies on the secp256r1 curve.
        // This prevents accepting garbage coordinates that happen to follow the COSE prefix.
        validatePointOnCurve(
            publicKey.copyOfRange(1, 33),
            publicKey.copyOfRange(33, 65)
        )

        return publicKey
    }

    /**
     * Extracts the secp256r1 public key from a raw WebAuthn attestation object.
     *
     * Searches for the COSE key structure prefix in raw attestation data and extracts
     * the X and Y coordinates of the public key. The result is formatted as an
     * uncompressed public key with the 0x04 prefix.
     *
     * This is an internal extraction strategy used by [extractPublicKeyFromRegistration].
     * Callers should use [extractPublicKeyFromRegistration] instead of calling this directly.
     *
     * This strategy works regardless of the CBOR structure surrounding the key: it
     * probes for the known 10-byte COSE key prefix for ES256 (P-256) keys, then
     * delegates parsing to [WebAuthnCborParser.extractPublicKeyFromCoseKey].
     *
     * COSE key structure:
     * ```
     * Prefix: [0xa5, 0x01, 0x02, 0x03, 0x26, 0x20, 0x01, 0x21, 0x58, 0x20]
     * X coordinate: next 32 bytes
     * Y marker:  3 bytes [0x22, 0x58, 0x20]
     * Y coordinate: next 32 bytes
     * Result: 0x04 || X (32 bytes) || Y (32 bytes) = 65 bytes total
     * ```
     *
     * **Validation performed:**
     * - The 10-byte ES256 COSE key prefix must be present somewhere in the data;
     *   its absence is reported as a distinct error.
     * - Coordinate parsing is delegated to [WebAuthnCborParser.extractPublicKeyFromCoseKey],
     *   which handles CBOR map iteration (order-tolerant) and a strict pattern fallback
     *   that validates the 3-byte Y-coordinate separator (0x22, 0x58, 0x20) and the
     *   available data length.
     * - The extracted (X, Y) point is verified to lie on the secp256r1 curve.
     *
     * @param attestationObject Raw attestation object data from WebAuthn registration
     * @return Uncompressed secp256r1 public key (65 bytes: 0x04 prefix + X + Y)
     * @throws ValidationException.InvalidInput if the COSE key prefix is not found,
     *         if the COSE key structure is malformed (truncated data or an invalid
     *         Y-coordinate separator), or if the extracted coordinates do not lie on
     *         the secp256r1 curve
     */
    internal fun extractPublicKeyFromAttestationObject(attestationObject: ByteArray): ByteArray {
        // COSE key prefix for secp256r1 public keys in WebAuthn attestation
        val prefix = byteArrayOf(
            0xa5.toByte(), 0x01, 0x02, 0x03, 0x26.toByte(), 0x20.toByte(),
            0x01, 0x21, 0x58, 0x20.toByte()
        )

        // Search for prefix in attestation object
        val prefixIndex = findSubarray(attestationObject, prefix)
        if (prefixIndex < 0) {
            throw ValidationException.invalidInput(
                "attestationObject",
                "COSE key prefix not found in attestation"
            )
        }

        // Prefix present — delegate parsing to WebAuthnCborParser. Its pattern fallback
        // locates the prefix, checks the available data length, and strictly validates
        // the [0x22, 0x58, 0x20] Y-coordinate separator.
        val publicKey = WebAuthnCborParser.extractPublicKeyFromCoseKey(attestationObject)
            ?: throw ValidationException.invalidInput(
                "attestationObject",
                "COSE key structure is malformed: could not extract secp256r1 " +
                    "coordinates from attestation object"
            )

        // Verify the extracted point lies on the secp256r1 curve.
        // This prevents accepting garbage coordinates that happen to surround the COSE prefix.
        validatePointOnCurve(
            publicKey.copyOfRange(1, 33),
            publicKey.copyOfRange(33, 65)
        )

        return publicKey
    }

    // ========================================================================
    // Contract Salt
    // ========================================================================

    /**
     * Computes the contract salt from a WebAuthn credential ID.
     *
     * The salt is used as part of the contract address derivation process to ensure
     * each credential ID results in a unique contract address. This is computed as
     * the SHA-256 hash of the credential ID.
     *
     * @param credentialId WebAuthn credential ID
     * @return SHA-256 hash of the credential ID (32 bytes)
     *
     * Example:
     * ```kotlin
     * val credentialId = ... // from WebAuthn registration
     * val salt = SmartAccountUtils.getContractSalt(credentialId)
     * ```
     */
    suspend fun getContractSalt(credentialId: ByteArray): ByteArray {
        return getSha256Crypto().hash(credentialId)
    }

    // ========================================================================
    // Contract Address Derivation
    // ========================================================================

    /**
     * Derives the smart account contract address from a credential ID and deployer.
     *
     * Computes the deterministic contract address that will be created when deploying
     * a smart account contract with the given credential ID from the specified deployer
     * account on the specified network.
     *
     * Algorithm:
     * ```
     * salt = SHA256(credentialId)
     * deployerAddress = SCAddress::Account(deployerPublicKey)
     * networkId = SHA256(networkPassphrase as UTF-8)
     *
     * preimage = HashIdPreimage::ContractId {
     *   networkId: networkId,
     *   contractIdPreimage: ContractIdPreimage::FromAddress {
     *     address: deployerAddress,
     *     salt: Uint256(salt)
     *   }
     * }
     *
     * contractIdBytes = SHA256(XDR_encode(preimage))
     * contractId = StrKey.encodeContract(contractIdBytes)
     * ```
     *
     * @param credentialId WebAuthn credential ID used to generate the salt
     * @param deployerPublicKey Stellar account ID (G-address) of the deployer
     * @param networkPassphrase Network passphrase (e.g., "Test SDF Network ; September 2015")
     * @return Contract address as a C-address (StrKey encoded)
     * @throws ValidationException.InvalidAddress if the deployer public key is invalid
     * @throws ValidationException.InvalidInput if contract ID encoding fails
     * @throws TransactionException.SigningFailed if hash computation fails
     *
     * Example:
     * ```kotlin
     * val credentialId = ... // from WebAuthn registration
     * val deployerKey = "GBXYZ..." // deployer G-address
     * val networkPassphrase = "Test SDF Network ; September 2015"
     * val contractAddress = SmartAccountUtils.deriveContractAddress(
     *     credentialId,
     *     deployerKey,
     *     networkPassphrase
     * )
     * println("Contract will be deployed at: $contractAddress")
     * ```
     */
    suspend fun deriveContractAddress(
        credentialId: ByteArray,
        deployerPublicKey: String,
        networkPassphrase: String
    ): String {
        // Step 1: Compute contract salt from credential ID
        val contractSalt = getContractSalt(credentialId)

        // Step 2: Create deployer SCAddress from public key
        val deployerAddress: SCAddressXdr = try {
            val keyPair = KeyPair.fromAccountId(deployerPublicKey)
            SCAddressXdr.AccountId(keyPair.getXdrAccountId())
        } catch (e: Exception) {
            throw ValidationException.invalidAddress(
                deployerPublicKey,
                e
            )
        }

        // Step 3: Compute network ID (SHA-256 of network passphrase)
        val networkIdBytes = getSha256Crypto().hash(networkPassphrase.encodeToByteArray())
        val networkId = HashXdr(networkIdBytes)

        // Step 4: Construct ContractIDPreimage::FromAddress
        val contractIdPreimage = ContractIDPreimageXdr.FromAddress(
            ContractIDPreimageFromAddressXdr(
                address = deployerAddress,
                salt = Uint256Xdr(contractSalt)
            )
        )

        // Step 5: Construct HashIDPreimage::ContractID
        val hashIdPreimageContractId = HashIDPreimageContractIDXdr(
            networkId = networkId,
            contractIdPreimage = contractIdPreimage
        )

        val preimage = HashIDPreimageXdr.ContractID(hashIdPreimageContractId)

        // Step 6: XDR encode the preimage
        val encodedPreimage: ByteArray = try {
            val writer = XdrWriter()
            preimage.encode(writer)
            writer.toByteArray()
        } catch (e: Exception) {
            throw TransactionException.signingFailed(
                "Failed to XDR encode contract ID preimage",
                e
            )
        }

        // Step 7: Hash the encoded preimage
        val contractIdBytes = getSha256Crypto().hash(encodedPreimage)

        // Step 8: Encode as StrKey contract ID (C-address)
        return try {
            StrKey.encodeContract(contractIdBytes)
        } catch (e: Exception) {
            throw ValidationException.invalidInput(
                "contractId",
                "Failed to encode contract ID: ${e.message}",
                e
            )
        }
    }

    // ========================================================================
    // Private Helper Functions
    // ========================================================================

    /**
     * Validates that the point (x, y) lies on the secp256r1 curve.
     *
     * Verifies the short Weierstrass equation: y^2 ≡ x^3 + a*x + b (mod p)
     *
     * Constants used (FIPS 186-4 / SEC 2):
     * - p  = 0xffffffff00000001000000000000000000000000ffffffffffffffffffffffff
     * - a  = p - 3 = 0xffffffff00000001000000000000000000000000fffffffffffffffffffffffc
     * - b  = 0x5ac635d8aa3a93e7b3ebbd55769886bc651d06b0cc53b0f63bce3c3e27d2604b
     *
     * This check prevents accepting coordinates extracted from malformed or adversarially
     * crafted COSE structures that happen to match the expected byte patterns.
     *
     * Also verifies that both coordinates are strictly less than the field prime p.
     * Values >= p are not valid field elements and must be rejected to prevent
     * multiple byte-encodings from being accepted for the same logical point.
     *
     * @param x 32-byte big-endian X coordinate
     * @param y 32-byte big-endian Y coordinate
     * @throws ValidationException.InvalidInput if the coordinates exceed the field prime
     *         or if the point does not lie on the secp256r1 curve
     */
    private fun validatePointOnCurve(x: ByteArray, y: ByteArray) {
        val xBig = bytesToUnsignedBigInteger(x)
        val yBig = bytesToUnsignedBigInteger(y)

        // Reject the point at infinity and trivially invalid coordinates.
        if (xBig == BigInteger.ZERO || yBig == BigInteger.ZERO) {
            throw ValidationException.invalidInput(
                "publicKey",
                "Extracted secp256r1 coordinates contain a zero component; " +
                    "the point is not a valid curve point"
            )
        }

        // Coordinates must be strictly less than the field prime.
        // Values >= p are not valid field elements; accepting them would silently reduce
        // them mod p and could admit multiple encodings of the same logical point.
        if (xBig >= curveP || yBig >= curveP) {
            throw ValidationException.invalidInput(
                "publicKey",
                "Extracted secp256r1 coordinates exceed the field prime"
            )
        }

        // Compute left side: y^2 mod p
        val lhs = (yBig * yBig).mod(curveP)

        // Compute right side: x^3 + a*x + b mod p
        val x3 = (xBig * xBig * xBig).mod(curveP)
        val ax = (curveA * xBig).mod(curveP)
        val rhs = (x3 + ax + curveB).mod(curveP)

        if (lhs != rhs) {
            throw ValidationException.invalidInput(
                "publicKey",
                "Extracted secp256r1 public key coordinates are not on the P-256 curve; " +
                    "the attestation data may be malformed or corrupted"
            )
        }
    }

    /**
     * Converts an unsigned byte array to BigInteger.
     *
     * This function interprets the byte array as an unsigned big-endian integer.
     *
     * @param bytes Unsigned byte array (big-endian)
     * @return BigInteger representation
     */
    private fun bytesToUnsignedBigInteger(bytes: ByteArray): BigInteger {
        // Convert bytes to hex string
        val hex = bytes.joinToString("") { byte ->
            (byte.toInt() and 0xFF).toString(16).padStart(2, '0')
        }
        return BigInteger.parseString(hex, 16)
    }

    /**
     * Converts a BigInteger to unsigned byte array with padding.
     *
     * @param value BigInteger value (must be non-negative)
     * @param byteCount Target byte count (left-padded with zeros if needed)
     * @return Unsigned byte array of exact length byteCount
     * @throws IllegalArgumentException if value is negative or requires more than [byteCount] bytes
     */
    private fun bigIntegerToUnsignedBytes(value: BigInteger, byteCount: Int): ByteArray {
        require(value >= BigInteger.ZERO) {
            "Cannot convert negative BigInteger to unsigned bytes"
        }

        // Convert to hex string
        val hex = value.toString(16)

        // Ensure even number of hex digits
        val paddedHex = if (hex.length % 2 != 0) "0$hex" else hex

        // Convert hex string to byte array
        val bytes = ByteArray(paddedHex.length / 2) { i ->
            val index = i * 2
            paddedHex.substring(index, index + 2).toInt(16).toByte()
        }

        // Left-pad with zeros or throw if too large
        return when {
            bytes.size == byteCount -> bytes
            bytes.size < byteCount -> {
                // Left-pad with zeros
                val padded = ByteArray(byteCount)
                bytes.copyInto(padded, byteCount - bytes.size)
                padded
            }
            else -> throw IllegalArgumentException(
                "BigInteger value requires ${bytes.size} bytes, exceeds target size of $byteCount"
            )
        }
    }

    /**
     * Finds the first occurrence of a subarray within a larger array.
     *
     * Uses a simple but efficient sliding window algorithm to search for
     * the subarray. Returns -1 if not found.
     *
     * @param array The array to search in
     * @param subarray The subarray to search for
     * @return Index of first occurrence, or -1 if not found
     */
    internal fun findSubarray(array: ByteArray, subarray: ByteArray): Int {
        if (subarray.isEmpty() || array.size < subarray.size) {
            return -1
        }

        // Search for the subarray
        for (i in 0..(array.size - subarray.size)) {
            var found = true
            for (j in subarray.indices) {
                if (array[i + j] != subarray[j]) {
                    found = false
                    break
                }
            }
            if (found) {
                return i
            }
        }

        return -1
    }
}

