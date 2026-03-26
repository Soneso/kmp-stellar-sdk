//
//  SmartAccountAuth.kt
//  Stellar SDK Kotlin Multiplatform
//
//  Created by Claude on 27.01.26.
//  Copyright © 2026 Soneso. All rights reserved.
//

package com.soneso.stellar.sdk.smartaccount.core

import com.soneso.stellar.sdk.crypto.getSha256Crypto
import com.soneso.stellar.sdk.xdr.HashIDPreimageXdr
import com.soneso.stellar.sdk.xdr.HashIDPreimageSorobanAuthorizationXdr
import com.soneso.stellar.sdk.xdr.HashXdr
import com.soneso.stellar.sdk.xdr.Int64Xdr
import com.soneso.stellar.sdk.xdr.SCMapEntryXdr
import com.soneso.stellar.sdk.xdr.SCMapXdr
import com.soneso.stellar.sdk.xdr.SCValXdr
import com.soneso.stellar.sdk.xdr.SCVecXdr
import com.soneso.stellar.sdk.xdr.SorobanAddressCredentialsXdr
import com.soneso.stellar.sdk.xdr.SorobanAuthorizationEntryXdr
import com.soneso.stellar.sdk.xdr.SorobanCredentialsXdr
import com.soneso.stellar.sdk.xdr.Uint32Xdr
import com.soneso.stellar.sdk.xdr.XdrReader
import com.soneso.stellar.sdk.xdr.XdrWriter
import com.soneso.stellar.sdk.scval.Scv

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
 * // Sign the entry with a WebAuthn signature
 * val signedEntry = SmartAccountAuth.signAuthEntry(
 *     entry = authEntry,
 *     signer = webAuthnSigner,
 *     signature = webAuthnSignature,
 *     expirationLedger = currentLedger + 100u,
 *     networkPassphrase = Network.TESTNET.networkPassphrase
 * )
 * ```
 */
object SmartAccountAuth {

    // MARK: - Payload Hash Building

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
        // Validate credentials type
        val credentials = (entry.credentials as? SorobanCredentialsXdr.Address)?.value
            ?: throw TransactionException.signingFailed(
                "Credentials must be of type address to build auth payload hash"
            )

        // Step 1: Compute network ID (SHA-256 of network passphrase)
        val networkId = getSha256Crypto().hash(networkPassphrase.encodeToByteArray())

        // Step 2: Build HashIDPreimage::SorobanAuthorization
        val authPreimage = HashIDPreimageSorobanAuthorizationXdr(
            networkId = HashXdr(networkId),
            nonce = credentials.nonce,
            signatureExpirationLedger = Uint32Xdr(expirationLedger),
            invocation = entry.rootInvocation
        )

        val preimage = HashIDPreimageXdr.SorobanAuthorization(authPreimage)

        // Step 3: XDR encode the preimage
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

        // Step 4: Hash the encoded preimage
        return getSha256Crypto().hash(encodedPreimage)
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
        // Step 1: Compute network ID (SHA-256 of network passphrase)
        val networkId = getSha256Crypto().hash(networkPassphrase.encodeToByteArray())

        // Step 2: Build HashIDPreimage::SorobanAuthorization
        val authPreimage = HashIDPreimageSorobanAuthorizationXdr(
            networkId = HashXdr(networkId),
            nonce = nonce,
            signatureExpirationLedger = Uint32Xdr(expirationLedger),
            invocation = entry.rootInvocation
        )

        val preimage = HashIDPreimageXdr.SorobanAuthorization(authPreimage)

        // Step 3: XDR encode the preimage
        val encodedPreimage: ByteArray = try {
            val writer = XdrWriter()
            preimage.encode(writer)
            writer.toByteArray()
        } catch (e: Exception) {
            throw TransactionException.signingFailed(
                "Failed to XDR encode source account auth payload preimage",
                e
            )
        }

        // Step 4: Hash the encoded preimage
        return getSha256Crypto().hash(encodedPreimage)
    }

    // MARK: - Entry Signing

    /**
     * Signs an authorization entry with a Smart Account signer.
     *
     * Creates a new authorization entry with the provided signature added to the
     * signature map. This function performs the following steps:
     *
     * 1. Clones the entry via XDR round-trip (encode then decode)
     * 2. Sets the signature expiration ledger
     * 3. Builds the signer key ScVal from the signer
     * 4. Double XDR-encodes the signature value (CRITICAL)
     * 5. Creates a map entry with key=signer, value=double-encoded-signature
     * 6. Adds to or creates the signature map
     * 7. Sorts map entries by XDR-encoded key bytes (lowercase hex, lexicographic)
     *
     * CRITICAL DETAILS:
     * - The entry is cloned to avoid mutating the input
     * - Expiration MUST be set BEFORE building payload hash (done externally)
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
     * @param entry The authorization entry to sign
     * @param signer The Smart Account signer (delegated or external)
     * @param signature The signature object (WebAuthn, Ed25519, or Policy)
     * @param expirationLedger The ledger number at which the signature expires
     * @param networkPassphrase The network passphrase (unused but kept for API consistency)
     * @return A new signed authorization entry
     * @throws TransactionException.SigningFailed if credentials is not `.Address`
     *         type, if XDR encoding/decoding fails, or if map construction fails
     *
     * Example:
     * ```kotlin
     * val webAuthnSig = WebAuthnSignature(
     *     authenticatorData = authData,
     *     clientData = clientData,
     *     signature = signature
     * )
     *
     * val signedEntry = SmartAccountAuth.signAuthEntry(
     *     entry = unsignedEntry,
     *     signer = externalSigner,
     *     signature = webAuthnSig,
     *     expirationLedger = currentLedger + 100u,
     *     networkPassphrase = Network.TESTNET.networkPassphrase
     * )
     * ```
     */
    suspend fun signAuthEntry(
        entry: SorobanAuthorizationEntryXdr,
        signer: SmartAccountSigner,
        signature: SmartAccountSignature,
        expirationLedger: UInt,
        networkPassphrase: String
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

        // STEP 4: Add to signatures map
        val mapEntries: MutableList<SCMapEntryXdr> = mutableListOf()

        // Check if credentials.signature already has a Vec with a Map
        if (credentials.signature is com.soneso.stellar.sdk.xdr.SCValXdr.Vec) {
            val existingVecXdr = (credentials.signature as com.soneso.stellar.sdk.xdr.SCValXdr.Vec).value
            if (existingVecXdr != null && existingVecXdr.value.isNotEmpty()) {
                val firstElement = existingVecXdr.value[0]
                if (firstElement is com.soneso.stellar.sdk.xdr.SCValXdr.Map) {
                    // Append to existing map
                    firstElement.value?.let { mapXdr ->
                        mapEntries.addAll(mapXdr.value)
                    }
                }
            }
        }

        // Add new entry
        mapEntries.add(mapEntry)

        // STEP 5: Sort map entries by XDR-encoded key bytes (as lowercase hex, lexicographic)
        val sortedMapEntries = sortMapEntries(mapEntries)

        // Build the final signature structure: ScVal::Vec([ScVal::Map([entries...])])
        val signatureMap = try {
            val mapXdr = com.soneso.stellar.sdk.xdr.SCMapXdr(sortedMapEntries)
            com.soneso.stellar.sdk.xdr.SCValXdr.Map(mapXdr)
        } catch (e: Exception) {
            throw TransactionException.signingFailed(
                "Failed to create signature map",
                e
            )
        }

        val vecXdr = com.soneso.stellar.sdk.xdr.SCVecXdr(listOf(signatureMap))

        credentials = SorobanAddressCredentialsXdr(
            address = credentials.address,
            nonce = credentials.nonce,
            signatureExpirationLedger = credentials.signatureExpirationLedger,
            signature = com.soneso.stellar.sdk.xdr.SCValXdr.Vec(vecXdr)
        )

        // STEP 6: Create and return the signed entry
        return SorobanAuthorizationEntryXdr(
            credentials = SorobanCredentialsXdr.Address(credentials),
            rootInvocation = entryCopy.rootInvocation
        )
    }

    // MARK: - Signature Map Manipulation

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

        val mapEntries = mutableListOf<SCMapEntryXdr>()

        // Collect existing map entries
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

        // Add the new entry
        mapEntries.add(SCMapEntryXdr(key = signerKey, `val` = signatureValue))

        // Sort and rebuild
        val sortedEntries = sortMapEntries(mapEntries)
        val signatureMap = SCValXdr.Map(SCMapXdr(sortedEntries))
        val updatedCredentials = SorobanAddressCredentialsXdr(
            address = credentials.address,
            nonce = credentials.nonce,
            signatureExpirationLedger = credentials.signatureExpirationLedger,
            signature = SCValXdr.Vec(SCVecXdr(listOf(signatureMap)))
        )

        return SorobanAuthorizationEntryXdr(
            credentials = SorobanCredentialsXdr.Address(updatedCredentials),
            rootInvocation = entry.rootInvocation
        )
    }

    // MARK: - Helper Functions

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
                keyBytes.joinToString("") { byte ->
                    (byte.toInt() and 0xFF).toString(16).padStart(2, '0')
                }
            } catch (e: Exception) {
                throw TransactionException.signingFailed(
                    "Failed to XDR-encode signature map key for sorting: ${e.message}",
                    e
                )
            }
        }
    }
}
