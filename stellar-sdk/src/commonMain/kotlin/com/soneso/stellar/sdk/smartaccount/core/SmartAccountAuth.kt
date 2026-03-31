//
//  SmartAccountAuth.kt
//  Stellar SDK Kotlin Multiplatform
//
//  Copyright © 2026 Soneso. All rights reserved.
//

package com.soneso.stellar.sdk.smartaccount.core

import com.soneso.stellar.sdk.crypto.getSha256Crypto
import com.soneso.stellar.sdk.xdr.HashIDPreimageXdr
import com.soneso.stellar.sdk.xdr.HashIDPreimageSorobanAuthorizationXdr
import com.soneso.stellar.sdk.xdr.HashXdr
import com.soneso.stellar.sdk.xdr.Int64Xdr
import com.soneso.stellar.sdk.scval.Scv
import com.soneso.stellar.sdk.xdr.SCMapEntryXdr
import com.soneso.stellar.sdk.xdr.SCValXdr
import com.soneso.stellar.sdk.xdr.SorobanAddressCredentialsXdr
import com.soneso.stellar.sdk.xdr.SorobanAuthorizationEntryXdr
import com.soneso.stellar.sdk.xdr.SorobanCredentialsXdr
import com.soneso.stellar.sdk.xdr.Uint32Xdr
import com.soneso.stellar.sdk.xdr.SorobanAuthorizedInvocationXdr
import com.soneso.stellar.sdk.Util
import com.soneso.stellar.sdk.xdr.XdrReader
import com.soneso.stellar.sdk.xdr.XdrWriter

/**
 * Authentication utilities for Smart Account authorization entries.
 *
 * Provides functions to sign authorization entries and build authentication payload hashes
 * for Smart Account transactions. These utilities handle the complex XDR encoding and
 * signature map construction required by the Soroban authorization protocol.
 *
 * Key responsibilities:
 * - Building Soroban authorization payload hashes for WebAuthn challenges
 * - Signing authorization entries with Smart Account signers
 * - Managing signature expiration and map entry ordering
 * - Double XDR encoding of signature values
 *
 * Example usage:
 * ```kotlin
 * // Build payload hash for WebAuthn signing
 * val payloadHash = SmartAccountAuth.buildAuthPayloadHash(
 *     entry = authEntry,
 *     expirationLedger = currentLedger + 100u,
 *     networkPassphrase = Network.TESTNET.networkPassphrase
 * )
 *
 * // Compute the signature over the payload hash, then attach it to the entry
 * val signedEntry = SmartAccountAuth.signAuthEntry(
 *     entry = authEntry,
 *     signer = webAuthnSigner,
 *     signature = webAuthnSignature,
 *     expirationLedger = currentLedger + 100u
 * )
 * ```
 */
object SmartAccountAuth {

    // ========================================================================
    // Payload Hash Building
    // ========================================================================

    /**
     * Builds the authorization payload hash for signing.
     *
     * Computes the hash that must be signed to authorize a Soroban operation. This hash
     * is used as the WebAuthn challenge when collecting biometric signatures.
     *
     * The payload is constructed as:
     * ```
     * HashIdPreimage::SorobanAuthorization {
     *   networkId: SHA256(networkPassphrase as UTF-8),
     *   nonce: credentials.nonce,
     *   signatureExpirationLedger: expirationLedger,
     *   invocation: entry.rootInvocation
     * }
     * hash = SHA256(XDR_encode(payload))
     * ```
     *
     * CRITICAL: The entry must have `.Address` credentials and the expiration ledger
     * is used in the hash computation before any signatures are added.
     *
     * @param entry The authorization entry to build the payload hash for
     * @param expirationLedger The ledger number at which the signature expires
     * @param networkPassphrase The network passphrase (e.g., "Test SDF Network ; September 2015")
     * @return The 32-byte SHA-256 hash of the authorization payload
     * @throws TransactionException.SigningFailed if credentials is not `.Address`
     *         type or if XDR encoding fails
     *
     * Example:
     * ```kotlin
     * val hash = SmartAccountAuth.buildAuthPayloadHash(
     *     entry = authEntry,
     *     expirationLedger = 12345678u,
     *     networkPassphrase = Network.TESTNET.networkPassphrase
     * )
     * // Use hash as WebAuthn challenge
     * val webAuthnResponse = navigator.credentials.get(challenge = hash)
     * ```
     */
    suspend fun buildAuthPayloadHash(
        entry: SorobanAuthorizationEntryXdr,
        expirationLedger: UInt,
        networkPassphrase: String
    ): ByteArray {
        val credentials = (entry.credentials as? SorobanCredentialsXdr.Address)?.value
            ?: throw TransactionException.signingFailed(
                "Credentials must be of type address to build auth payload hash"
            )

        return hashAuthPreimage(
            nonce = credentials.nonce,
            expirationLedger = expirationLedger,
            invocation = entry.rootInvocation,
            networkPassphrase = networkPassphrase
        )
    }

    /**
     * Builds the authorization payload hash for source_account credentials.
     *
     * This is used when converting source_account credentials to Address credentials,
     * typically for relayer fee sponsoring. The payload is constructed similarly to
     * buildAuthPayloadHash but uses the provided nonce and expiration since there are
     * no existing credentials yet.
     *
     * The payload is constructed as:
     * ```
     * HashIdPreimage::SorobanAuthorization {
     *   networkId: SHA256(networkPassphrase as UTF-8),
     *   nonce: provided nonce,
     *   signatureExpirationLedger: expirationLedger,
     *   invocation: entry.rootInvocation
     * }
     * hash = SHA256(XDR_encode(payload))
     * ```
     *
     * @param entry The authorization entry with source_account credentials
     * @param nonce The nonce to use for the new Address credentials
     * @param expirationLedger The ledger number at which the signature expires
     * @param networkPassphrase The network passphrase
     * @return The 32-byte SHA-256 hash of the authorization payload
     * @throws TransactionException.SigningFailed if XDR encoding fails
     */
    suspend fun buildSourceAccountAuthPayloadHash(
        entry: SorobanAuthorizationEntryXdr,
        nonce: Int64Xdr,
        expirationLedger: UInt,
        networkPassphrase: String
    ): ByteArray {
        return hashAuthPreimage(
            nonce = nonce,
            expirationLedger = expirationLedger,
            invocation = entry.rootInvocation,
            networkPassphrase = networkPassphrase
        )
    }

    // ========================================================================
    // Entry Signing
    // ========================================================================

    /**
     * Attaches a pre-computed signature to an authorization entry.
     *
     * This method does NOT perform cryptographic signing. The caller is responsible
     * for computing the signature over the correct payload hash. Use
     * [buildAuthPayloadHash] with the same [expirationLedger] value to obtain
     * the hash before calling this method.
     *
     * Attaching the signature involves the following steps:
     * 1. Clones the entry via XDR round-trip (encode then decode)
     * 2. Sets the signature expiration ledger on the credentials
     * 3. Builds the signer key ScVal from the signer
     * 4. Double XDR-encodes the signature value (CRITICAL)
     * 5. Creates a map entry with key=signer, value=double-encoded-signature
     * 6. Merges with any existing signatures (multi-signer accumulation)
     * 7. Sorts map entries by XDR-encoded key bytes (lowercase hex, lexicographic)
     * 8. Returns the entry with the updated signature map
     *
     * CRITICAL DETAILS:
     * - The input entry is never mutated; a deep clone is returned
     * - Signature value uses DOUBLE XDR encoding: encode the ScVal to bytes,
     *   then wrap those bytes in a new ScVal::Bytes
     * - Map entries MUST be sorted by their XDR-encoded key bytes as lowercase hex
     * - Credentials must be of type `.Address`
     *
     * The signature map format is:
     * ```
     * ScVal::Vec([
     *   ScVal::Map([
     *     { key: signer.toScVal(), value: ScVal::Bytes(XDR_encode(signatureScVal)) },
     *     ...
     *   ])
     * ])
     * ```
     *
     * @param entry The authorization entry to attach the signature to
     * @param signer The Smart Account signer (delegated or external)
     * @param signature The pre-computed signature object (WebAuthn, Ed25519, or Policy)
     * @param expirationLedger The ledger number at which the signature expires.
     *        Must match the value passed to [buildAuthPayloadHash] when producing the signature.
     * @return A new authorization entry with the signature attached
     * @throws TransactionException.SigningFailed if credentials is not `.Address`
     *         type, if XDR encoding/decoding fails, or if map construction fails
     *
     * Example:
     * ```kotlin
     * val expirationLedger = currentLedger + 100u
     * val payloadHash = SmartAccountAuth.buildAuthPayloadHash(
     *     entry = unsignedEntry,
     *     expirationLedger = expirationLedger,
     *     networkPassphrase = Network.TESTNET.networkPassphrase
     * )
     * val webAuthnSig = WebAuthnSignature(
     *     authenticatorData = authData,
     *     clientData = clientData,
     *     signature = signOverPayloadHash(payloadHash)
     * )
     * val signedEntry = SmartAccountAuth.signAuthEntry(
     *     entry = unsignedEntry,
     *     signer = externalSigner,
     *     signature = webAuthnSig,
     *     expirationLedger = expirationLedger
     * )
     * ```
     */
    suspend fun signAuthEntry(
        entry: SorobanAuthorizationEntryXdr,
        signer: SmartAccountSigner,
        signature: SmartAccountSignature,
        expirationLedger: UInt
    ): SorobanAuthorizationEntryXdr {
        // STEP 1: Clone entry via XDR round-trip to avoid mutating input
        val entryBytes: ByteArray = try {
            val writer = XdrWriter()
            entry.encode(writer)
            writer.toByteArray()
        } catch (e: Exception) {
            throw TransactionException.signingFailed(
                "Failed to XDR encode authorization entry for cloning",
                e
            )
        }

        val entryCopy: SorobanAuthorizationEntryXdr = try {
            val reader = XdrReader(entryBytes)
            SorobanAuthorizationEntryXdr.decode(reader)
        } catch (e: Exception) {
            throw TransactionException.signingFailed(
                "Failed to XDR decode authorization entry after cloning",
                e
            )
        }

        // STEP 2: Set expiration (BEFORE building payload - though payload is built externally)
        var credentials = (entryCopy.credentials as? SorobanCredentialsXdr.Address)?.value
            ?: throw TransactionException.signingFailed(
                "Credentials must be of type address to sign auth entry"
            )

        credentials = SorobanAddressCredentialsXdr(
            address = credentials.address,
            nonce = credentials.nonce,
            signatureExpirationLedger = Uint32Xdr(expirationLedger),
            signature = credentials.signature
        )

        // STEP 3: Build signature map entry
        // KEY: Signer identity as ScVal
        val signerKey = try {
            signer.toScVal()
        } catch (e: Exception) {
            throw TransactionException.signingFailed(
                "Failed to convert signer to SCVal",
                e
            )
        }

        // VALUE: Double XDR-encoded signature
        // Step A: signature.toScVal() is already a ScVal
        val signatureScVal = signature.toScVal()

        // Step B: XDR-encode that ScVal into raw bytes
        val sigXdrBytes: ByteArray = try {
            val writer = XdrWriter()
            signatureScVal.encode(writer)
            writer.toByteArray()
        } catch (e: Exception) {
            throw TransactionException.signingFailed(
                "Failed to XDR encode signature ScVal",
                e
            )
        }

        // Step C: Wrap those raw bytes in a new ScVal::Bytes
        val signatureValue = Scv.toBytes(sigXdrBytes)

        // Create map entry
        val mapEntry = SCMapEntryXdr(key = signerKey, `val` = signatureValue)

        // STEP 4-6: Add to signatures map and return updated entry
        return appendSignatureMapEntry(
            entry = entryCopy,
            credentials = credentials,
            mapEntry = mapEntry
        )
    }

    // ========================================================================
    // Signature Map Manipulation
    // ========================================================================

    /**
     * Adds a raw key/value entry to the auth entry's signature map.
     *
     * Used for delegated signer placeholders where the value is `Bytes(empty)`
     * rather than a double-XDR-encoded signature. The entry is cloned, the map
     * entry is appended, and the map is re-sorted.
     *
     * @param entry The auth entry to modify
     * @param signerKey The signer identity ScVal (map key)
     * @param signatureValue The raw ScVal to use as the map value
     * @return A new auth entry with the map entry added
     */
    fun addRawSignatureMapEntry(
        entry: SorobanAuthorizationEntryXdr,
        signerKey: SCValXdr,
        signatureValue: SCValXdr
    ): SorobanAuthorizationEntryXdr {
        val credentials = (entry.credentials as? SorobanCredentialsXdr.Address)?.value
            ?: throw TransactionException.signingFailed(
                "Credentials must be of type address to add signature map entry"
            )

        return appendSignatureMapEntry(
            entry = entry,
            credentials = credentials,
            mapEntry = SCMapEntryXdr(key = signerKey, `val` = signatureValue)
        )
    }

    // ========================================================================
    // Helper Functions
    // ========================================================================

    /**
     * Hashes a Soroban authorization preimage.
     *
     * Constructs a `HashIDPreimage::SorobanAuthorization` from the given parameters,
     * XDR-encodes it, and returns SHA-256(encoded bytes). Used by both
     * [buildAuthPayloadHash] and [buildSourceAccountAuthPayloadHash].
     *
     * @param nonce The nonce from the address credentials
     * @param expirationLedger The signature expiration ledger number
     * @param invocation The root invocation from the authorization entry
     * @param networkPassphrase The network passphrase
     * @return The 32-byte SHA-256 hash of the encoded preimage
     * @throws TransactionException.SigningFailed if XDR encoding fails
     */
    private suspend fun hashAuthPreimage(
        nonce: Int64Xdr,
        expirationLedger: UInt,
        invocation: SorobanAuthorizedInvocationXdr,
        networkPassphrase: String
    ): ByteArray {
        val networkId = getSha256Crypto().hash(networkPassphrase.encodeToByteArray())

        val authPreimage = HashIDPreimageSorobanAuthorizationXdr(
            networkId = HashXdr(networkId),
            nonce = nonce,
            signatureExpirationLedger = Uint32Xdr(expirationLedger),
            invocation = invocation
        )

        val preimage = HashIDPreimageXdr.SorobanAuthorization(authPreimage)

        val encodedPreimage: ByteArray = try {
            val writer = XdrWriter()
            preimage.encode(writer)
            writer.toByteArray()
        } catch (e: Exception) {
            throw TransactionException.signingFailed(
                "Failed to XDR encode auth payload preimage",
                e
            )
        }

        return getSha256Crypto().hash(encodedPreimage)
    }

    /**
     * Appends a signature map entry to an authorization entry's credential signature map.
     *
     * Collects existing map entries from the credentials signature field (if any),
     * appends the new entry, sorts all entries by XDR-encoded key bytes (lowercase hex,
     * lexicographic), rebuilds the signature Vec/Map structure, and returns a new
     * [SorobanAuthorizationEntryXdr] with updated credentials. The input [entry] is not mutated.
     *
     * @param entry The authorization entry whose root invocation is preserved
     * @param credentials The address credentials containing the existing signature map
     * @param mapEntry The new map entry to append
     * @return A new authorization entry with the map entry added and the map re-sorted
     */
    private fun appendSignatureMapEntry(
        entry: SorobanAuthorizationEntryXdr,
        credentials: SorobanAddressCredentialsXdr,
        mapEntry: SCMapEntryXdr
    ): SorobanAuthorizationEntryXdr {
        val mapEntries = mutableListOf<SCMapEntryXdr>()

        if (credentials.signature is SCValXdr.Vec) {
            val existingVecXdr = (credentials.signature as SCValXdr.Vec).value
            if (existingVecXdr != null && existingVecXdr.value.isNotEmpty()) {
                val firstElement = existingVecXdr.value[0]
                if (firstElement is SCValXdr.Map) {
                    firstElement.value?.let { mapXdr ->
                        mapEntries.addAll(mapXdr.value)
                    }
                }
            }
        }

        mapEntries.add(mapEntry)

        val sortedEntries = sortMapEntries(mapEntries)
        val signatureMap = Scv.toMap(linkedMapOf<SCValXdr, SCValXdr>().apply {
            sortedEntries.forEach { e -> put(e.key, e.`val`) }
        })
        val updatedCredentials = SorobanAddressCredentialsXdr(
            address = credentials.address,
            nonce = credentials.nonce,
            signatureExpirationLedger = credentials.signatureExpirationLedger,
            signature = Scv.toVec(listOf(signatureMap))
        )

        return SorobanAuthorizationEntryXdr(
            credentials = SorobanCredentialsXdr.Address(updatedCredentials),
            rootInvocation = entry.rootInvocation
        )
    }

    /**
     * Sorts map entries by XDR-encoded key bytes (lowercase hex, lexicographic).
     *
     * This is CRITICAL for contract compatibility. The smart account contract expects
     * signature map entries to be sorted in a specific order based on the XDR-encoded
     * key bytes converted to lowercase hex strings.
     *
     * @param entries The list of map entries to sort
     * @return Sorted list of map entries
     */
    private fun sortMapEntries(entries: List<SCMapEntryXdr>): List<SCMapEntryXdr> {
        return entries.sortedBy { entry ->
            try {
                // Encode the key to XDR bytes
                val writer = XdrWriter()
                entry.key.encode(writer)
                val keyBytes = writer.toByteArray()

                // Convert to lowercase hex string
                Util.bytesToHex(keyBytes)
            } catch (e: Exception) {
                throw TransactionException.signingFailed(
                    "Failed to XDR-encode signature map key for sorting: ${e.message}",
                    e
                )
            }
        }
    }
}
