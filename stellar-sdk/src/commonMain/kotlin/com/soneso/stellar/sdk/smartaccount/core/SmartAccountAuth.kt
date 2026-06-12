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
import com.soneso.stellar.sdk.xdr.SCValXdr
import com.soneso.stellar.sdk.xdr.SorobanAddressCredentialsXdr
import com.soneso.stellar.sdk.xdr.SorobanAuthorizationEntryXdr
import com.soneso.stellar.sdk.xdr.SorobanCredentialsXdr
import com.soneso.stellar.sdk.xdr.Uint32Xdr
import com.soneso.stellar.sdk.xdr.SorobanAuthorizedInvocationXdr
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
 * - Variant-specific encoding of signature values per [SmartAccountSignature.toAuthPayloadBytes]
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
     * Computes the auth digest that binds context rule IDs to the signature payload.
     *
     * The digest is computed as:
     * ```
     * auth_digest = SHA-256(signaturePayload || contextRuleIds.toXDR())
     * ```
     *
     * Where `contextRuleIds.toXDR()` is the XDR encoding of `ScVal::Vec([ScVal::U32(id), ...])`.
     *
     * @param signaturePayload The 32-byte signature payload hash from [buildAuthPayloadHash]
     * @param contextRuleIds The context rule IDs to bind into the digest
     * @return The 32-byte SHA-256 auth digest
     * @throws TransactionException.SigningFailed if XDR encoding fails
     */
    suspend fun buildAuthDigest(
        signaturePayload: ByteArray,
        contextRuleIds: List<UInt>
    ): ByteArray {
        val ruleIdsScVal = Scv.toVec(contextRuleIds.map { id -> Scv.toUint32(id) })

        val ruleIdsXdr: ByteArray = try {
            val writer = XdrWriter()
            ruleIdsScVal.encode(writer)
            writer.toByteArray()
        } catch (e: Exception) {
            throw TransactionException.signingFailed(
                "Failed to XDR encode context rule IDs ScVal",
                e
            )
        }

        val concatenated = signaturePayload + ruleIdsXdr
        return getSha256Crypto().hash(concatenated)
    }

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
     * Signs a single Soroban authorization entry with the provided signature.
     *
     * Encoding of the signature bytes is delegated to [SmartAccountSignature.toAuthPayloadBytes];
     * see that contract for variant-specific wire shape.
     *
     * The input entry is never mutated; a deep clone is produced via XDR round-trip before any
     * modification. Multiple calls on the same entry accumulate signatures in the credentials map,
     * enabling multi-signer flows.
     *
     * @param entry The authorization entry to sign.
     * @param signer The Smart Account signer whose slot receives the signature (delegated or external).
     * @param signature The [SmartAccountSignature] to attach (variant determines wire encoding).
     * @param expirationLedger The ledger at which this signature expires. Must match the value
     *   passed to [buildAuthPayloadHash] when the signature was computed.
     * @param contextRuleIds The context rule IDs the signature satisfies. Defaults to empty.
     * @return The signed authorization entry with the signature inserted into the signer slot.
     * @throws TransactionException.SigningFailed on XDR encoding/decoding failure or when
     *   credentials are not of type Address.
     */
    suspend fun signAuthEntry(
        entry: SorobanAuthorizationEntryXdr,
        signer: SmartAccountSigner,
        signature: SmartAccountSignature,
        expirationLedger: UInt,
        contextRuleIds: List<UInt> = emptyList()
    ): SorobanAuthorizationEntryXdr {
        // Clone entry via XDR round-trip to avoid mutating input
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

        // VALUE: Verifier-appropriate bytes for the on-wire signers map.
        // The exact content is verifier-dependent: WebAuthn/Policy XDR-encode their ScVal;
        // Ed25519 passes the raw 64-byte signature directly. See
        // SmartAccountSignature.toAuthPayloadBytes for the per-type contract.
        val sigXdrBytes: ByteArray = try {
            signature.toAuthPayloadBytes()
        } catch (e: Exception) {
            throw TransactionException.signingFailed(
                "Failed to encode signature bytes for auth payload",
                e
            )
        }

        // Read existing payload from credentials (Void -> empty payload)
        val existingPayload = SmartAccountAuthPayloadCodec.read(credentials.signature)

        // Preserve or override context rule IDs
        val updatedPayload = SmartAccountAuthPayload(
            signers = existingPayload.signers,
            contextRuleIds = if (contextRuleIds.isNotEmpty()) contextRuleIds else existingPayload.contextRuleIds
        )

        // Upsert signer with double-XDR-encoded signature bytes
        SmartAccountAuthPayloadCodec.upsertSigner(updatedPayload, signer, sigXdrBytes)

        // Write updated payload back as SCVal
        val payloadScVal = SmartAccountAuthPayloadCodec.write(updatedPayload)

        // Build updated credentials with new signature
        val updatedCredentials = SorobanAddressCredentialsXdr(
            address = credentials.address,
            nonce = credentials.nonce,
            signatureExpirationLedger = credentials.signatureExpirationLedger,
            signature = payloadScVal
        )

        return SorobanAuthorizationEntryXdr(
            credentials = SorobanCredentialsXdr.Address(updatedCredentials),
            rootInvocation = entryCopy.rootInvocation
        )
    }

    // ========================================================================
    // Signature Map Manipulation
    // ========================================================================

    /**
     * Adds a raw key/value entry to the auth entry's signature map.
     *
     * Used for delegated signer placeholders where the value is `Bytes(empty)`
     * rather than a double-XDR-encoded signature. Uses the v0.7.0 AuthPayload format.
     *
     * @param entry The auth entry to modify
     * @param signerKey The signer identity ScVal (map key)
     * @param signatureValue The raw ScVal to use as the map value. If `SCValXdr.Bytes`, the bytes
     *        are stored directly. Otherwise the value is XDR-encoded and the resulting bytes stored.
     * @param contextRuleIds The context rule IDs to bind into the payload (optional).
     * @return A new auth entry with the map entry added
     */
    fun addRawSignatureMapEntry(
        entry: SorobanAuthorizationEntryXdr,
        signerKey: SCValXdr,
        signatureValue: SCValXdr,
        contextRuleIds: List<UInt> = emptyList()
    ): SorobanAuthorizationEntryXdr {
        val credentials = (entry.credentials as? SorobanCredentialsXdr.Address)?.value
            ?: throw TransactionException.signingFailed(
                "Credentials must be of type address to add signature map entry"
            )

        // Read existing payload
        val existingPayload = SmartAccountAuthPayloadCodec.read(credentials.signature)

        // Preserve or override context rule IDs
        val updatedPayload = SmartAccountAuthPayload(
            signers = existingPayload.signers,
            contextRuleIds = if (contextRuleIds.isNotEmpty()) contextRuleIds else existingPayload.contextRuleIds
        )

        // Extract bytes from signatureValue: use raw bytes if Bytes, otherwise XDR-encode
        val sigBytes = when (signatureValue) {
            is SCValXdr.Bytes -> signatureValue.value.value
            else -> {
                try {
                    val writer = XdrWriter()
                    signatureValue.encode(writer)
                    writer.toByteArray()
                } catch (e: Exception) {
                    throw TransactionException.signingFailed(
                        "Failed to XDR-encode raw signature value",
                        e
                    )
                }
            }
        }

        // Parse signer from key ScVal and upsert
        val signer = SmartAccountAuthPayloadCodec.signerFromScVal(signerKey)
        updatedPayload.signers[signer] = sigBytes

        // Write updated payload back as SCVal
        val payloadScVal = SmartAccountAuthPayloadCodec.write(updatedPayload)

        val updatedCredentials = SorobanAddressCredentialsXdr(
            address = credentials.address,
            nonce = credentials.nonce,
            signatureExpirationLedger = credentials.signatureExpirationLedger,
            signature = payloadScVal
        )

        return SorobanAuthorizationEntryXdr(
            credentials = SorobanCredentialsXdr.Address(updatedCredentials),
            rootInvocation = entry.rootInvocation
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
}
