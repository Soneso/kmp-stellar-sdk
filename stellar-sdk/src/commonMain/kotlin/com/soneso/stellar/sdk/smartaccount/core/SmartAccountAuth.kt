//
//  SmartAccountAuth.kt
//  Stellar SDK Kotlin Multiplatform
//
//  Copyright © 2026 Soneso. All rights reserved.
//

package com.soneso.stellar.sdk.smartaccount.core

import com.soneso.stellar.sdk.Auth
import com.soneso.stellar.sdk.addressCredentials
import com.soneso.stellar.sdk.withUpdatedAddressCredentials
import com.soneso.stellar.sdk.crypto.getSha256Crypto
import com.soneso.stellar.sdk.xdr.HashIDPreimageXdr
import com.soneso.stellar.sdk.xdr.Int64Xdr
import com.soneso.stellar.sdk.scval.Scv
import com.soneso.stellar.sdk.xdr.SCAddressXdr
import com.soneso.stellar.sdk.xdr.SCValTypeXdr
import com.soneso.stellar.sdk.xdr.SCValXdr
import com.soneso.stellar.sdk.xdr.SorobanAddressCredentialsXdr
import com.soneso.stellar.sdk.xdr.SorobanAuthorizationEntryXdr
import com.soneso.stellar.sdk.xdr.SorobanCredentialsXdr
import com.soneso.stellar.sdk.xdr.Uint32Xdr
import com.soneso.stellar.sdk.xdr.XdrReader
import com.soneso.stellar.sdk.xdr.XdrWriter
import com.soneso.stellar.sdk.xdr.toXdrBase64

/**
 * The digest that the `__check_auth` of an OpenZeppelin smart account build checks.
 *
 * Every signer of one auth entry commits to the same digest. Its form depends on the code
 * the smart account runs, so the kit reads the wallet's WASM hash before each signing
 * operation and selects the scheme through [com.soneso.stellar.sdk.smartaccount.oz.OZSmartAccountConfig.authDigestSchemes].
 */
enum class AuthDigestScheme {
    /**
     * The digest of stellar-accounts 0.7:
     * `auth_digest = SHA-256(signature_payload || XDR(ScVal::Vec([ScVal::U32(id), ...])))`.
     * Delegated signers authorize `__check_auth(Bytes(auth_digest))`.
     */
    LEGACY_CONCAT,

    /**
     * The digest of stellar-accounts 0.8.0:
     * `auth_digest = SHA-256(XDR(AuthDigestPreimage))`, where `AuthDigestPreimage` is the
     * `ScVal::Map` with the Symbol keys `account` (the smart account address),
     * `context_rule_ids` (`Vec` of `U32`) and `signature_payload` (32 `Bytes`). Delegated
     * signers authorize `__check_auth(AuthDigestPreimage)`.
     */
    PREIMAGE_V1
}

/**
 * The digest material every signer of one smart account auth entry commits to.
 *
 * @property signaturePayload The 32-byte payload the host passes to `__check_auth`.
 * @property authDigest The 32-byte digest that External signers (passkeys, Ed25519 keys) sign.
 * @property delegatedAuthArgs The `__check_auth` arguments that Delegated signers authorize.
 *   Two digests compare these by their XDR encoding.
 */
data class EntryAuthDigest(
    val signaturePayload: ByteArray,
    val authDigest: ByteArray,
    val delegatedAuthArgs: List<SCValXdr>
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as EntryAuthDigest

        return signaturePayload.contentEquals(other.signaturePayload) &&
            authDigest.contentEquals(other.authDigest) &&
            delegatedAuthArgsXdr() == other.delegatedAuthArgsXdr()
    }

    override fun hashCode(): Int {
        var result = signaturePayload.contentHashCode()
        result = 31 * result + authDigest.contentHashCode()
        result = 31 * result + delegatedAuthArgsXdr().hashCode()
        return result
    }

    private fun delegatedAuthArgsXdr(): List<String> = delegatedAuthArgs.map { it.toXdrBase64() }
}

/**
 * Authentication utilities for Smart Account authorization entries.
 *
 * Provides functions to sign authorization entries and build authentication payload hashes
 * for Smart Account transactions. These utilities handle the complex XDR encoding and
 * signature map construction required by the Soroban authorization protocol.
 *
 * Key responsibilities:
 * - Building Soroban authorization payload hashes and the auth digests signers sign
 * - Signing authorization entries with Smart Account signers
 * - Managing signature expiration and map entry ordering
 * - Variant-specific encoding of signature values per [SmartAccountSignature.toAuthPayloadBytes]
 *
 * Example usage:
 * ```kotlin
 * // Compute the digest the wallet's code checks (the WebAuthn challenge)
 * val digest = SmartAccountAuth.computeEntryAuthDigest(
 *     entry = authEntry,
 *     expirationLedger = currentLedger + 100u,
 *     networkPassphrase = Network.TESTNET.networkPassphrase,
 *     contextRuleIds = listOf(0u),
 *     scheme = AuthDigestScheme.PREIMAGE_V1
 * )
 *
 * // Compute the signature over digest.authDigest, then attach it to the entry
 * val signedEntry = SmartAccountAuth.signAuthEntry(
 *     entry = authEntry,
 *     signer = webAuthnSigner,
 *     signature = webAuthnSignature,
 *     expirationLedger = currentLedger + 100u,
 *     contextRuleIds = listOf(0u)
 * )
 * ```
 */
object SmartAccountAuth {

    // ========================================================================
    // Payload Hash Building
    // ========================================================================

    /**
     * Computes the stellar-accounts 0.7 auth digest, [AuthDigestScheme.LEGACY_CONCAT] only.
     *
     * The digest is computed as:
     * ```
     * auth_digest = SHA-256(signaturePayload || contextRuleIds.toXDR())
     * ```
     *
     * Where `contextRuleIds.toXDR()` is the XDR encoding of `ScVal::Vec([ScVal::U32(id), ...])`.
     * A wallet on stellar-accounts 0.8.0 checks the [AuthDigestScheme.PREIMAGE_V1] digest
     * instead, which this function cannot produce.
     *
     * @param signaturePayload The 32-byte signature payload hash from [buildAuthPayloadHash]
     * @param contextRuleIds The context rule IDs to bind into the digest
     * @return The 32-byte SHA-256 auth digest
     * @throws TransactionException.SigningFailed if XDR encoding fails
     */
    @Deprecated(
        message = "Computes the stellar-accounts 0.7 digest only. Use computeEntryAuthDigest " +
            "with the AuthDigestScheme of the code the wallet runs."
    )
    suspend fun buildAuthDigest(
        signaturePayload: ByteArray,
        contextRuleIds: List<UInt>
    ): ByteArray = legacyAuthDigest(signaturePayload, contextRuleIds)

    /**
     * Computes the signature payload, the auth digest and the delegated `__check_auth`
     * arguments of a smart account auth entry for [scheme].
     *
     * The signature payload is [buildAuthPayloadHash] of the entry. With
     * [AuthDigestScheme.LEGACY_CONCAT] the digest is
     * `SHA-256(signature_payload || XDR(ScVal::Vec([ScVal::U32(id), ...])))` and Delegated
     * signers authorize `Bytes(auth_digest)`. With [AuthDigestScheme.PREIMAGE_V1] the digest is
     * `SHA-256(XDR(AuthDigestPreimage))`, the preimage binding the entry's credential address as
     * the smart account, and Delegated signers authorize the preimage itself.
     *
     * The scheme must be the one the wallet's `__check_auth` checks. A signature over the other
     * digest is rejected by the contract when the transaction is simulated or submitted, after
     * every signer has signed, and nothing executes.
     *
     * Example:
     * ```kotlin
     * val digest = SmartAccountAuth.computeEntryAuthDigest(
     *     entry = authEntry,
     *     expirationLedger = currentLedger + 100u,
     *     networkPassphrase = Network.TESTNET.networkPassphrase,
     *     contextRuleIds = listOf(0u),
     *     scheme = AuthDigestScheme.PREIMAGE_V1
     * )
     * // digest.authDigest is the WebAuthn challenge and the message an Ed25519 signer signs
     * ```
     *
     * @param entry The smart account authorization entry. Must carry address-bearing
     *   credentials (Address, AddressV2, or AddressWithDelegates).
     * @param expirationLedger The ledger number at which the signatures expire
     * @param networkPassphrase The network passphrase
     * @param contextRuleIds The context rule IDs the signatures satisfy, one per auth context
     * @param scheme The digest scheme of the code the smart account runs
     * @return The signature payload, the auth digest and the delegated `__check_auth` arguments
     * @throws TransactionException.SigningFailed if the credentials are source-account (Void)
     *   or if XDR encoding fails
     */
    suspend fun computeEntryAuthDigest(
        entry: SorobanAuthorizationEntryXdr,
        expirationLedger: UInt,
        networkPassphrase: String,
        contextRuleIds: List<UInt>,
        scheme: AuthDigestScheme
    ): EntryAuthDigest {
        val signaturePayload = buildAuthPayloadHash(entry, expirationLedger, networkPassphrase)
        return when (scheme) {
            AuthDigestScheme.LEGACY_CONCAT -> {
                val authDigest = legacyAuthDigest(signaturePayload, contextRuleIds)
                EntryAuthDigest(
                    signaturePayload = signaturePayload,
                    authDigest = authDigest,
                    delegatedAuthArgs = listOf(Scv.toBytes(authDigest))
                )
            }
            AuthDigestScheme.PREIMAGE_V1 -> {
                // buildAuthPayloadHash has already refused credentials without an address.
                val account = entry.credentials.addressCredentials()!!.address
                val preimage = buildAuthDigestPreimage(account, signaturePayload, contextRuleIds)
                EntryAuthDigest(
                    signaturePayload = signaturePayload,
                    authDigest = getSha256Crypto().hash(encodeScVal(preimage, "AuthDigestPreimage")),
                    delegatedAuthArgs = listOf(preimage)
                )
            }
        }
    }

    /**
     * Encodes the `AuthDigestPreimage` contract struct of stellar-accounts 0.8.0: an
     * `ScVal::Map` with the Symbol keys in name order, `account`, `context_rule_ids` and
     * `signature_payload`.
     *
     * @param account The smart account performing the authorization check
     * @param signaturePayload The 32-byte payload the host passes to `__check_auth`
     * @param contextRuleIds The context rule IDs, one per auth context
     * @throws IllegalArgumentException if [signaturePayload] is not 32 bytes
     */
    internal fun buildAuthDigestPreimage(
        account: SCAddressXdr,
        signaturePayload: ByteArray,
        contextRuleIds: List<UInt>
    ): SCValXdr {
        require(signaturePayload.size == SIGNATURE_PAYLOAD_SIZE) {
            "Signature payload must be $SIGNATURE_PAYLOAD_SIZE bytes, got ${signaturePayload.size}"
        }
        return Scv.toMap(
            linkedMapOf(
                Scv.toSymbol("account") to Scv.toAddress(account),
                Scv.toSymbol("context_rule_ids") to Scv.toVec(contextRuleIds.map { id -> Scv.toUint32(id) }),
                Scv.toSymbol("signature_payload") to Scv.toBytes(signaturePayload)
            )
        )
    }

    private const val SIGNATURE_PAYLOAD_SIZE = 32

    private suspend fun legacyAuthDigest(
        signaturePayload: ByteArray,
        contextRuleIds: List<UInt>
    ): ByteArray {
        val ruleIdsScVal = Scv.toVec(contextRuleIds.map { id -> Scv.toUint32(id) })
        val ruleIdsXdr = encodeScVal(ruleIdsScVal, "context rule IDs ScVal")
        return getSha256Crypto().hash(signaturePayload + ruleIdsXdr)
    }

    /**
     * XDR-encodes [value].
     *
     * @throws TransactionException.SigningFailed naming [description] if XDR encoding fails
     */
    private fun encodeScVal(value: SCValXdr, description: String): ByteArray = try {
        val writer = XdrWriter()
        value.encode(writer)
        writer.toByteArray()
    } catch (e: Exception) {
        throw TransactionException.signingFailed("Failed to XDR encode $description", e)
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
     * The preimage type is selected by the credential arm. Legacy ADDRESS credentials
     * use the ENVELOPE_TYPE_SOROBAN_AUTHORIZATION preimage; ADDRESS_V2 and
     * ADDRESS_WITH_DELEGATES use the address-bound
     * ENVELOPE_TYPE_SOROBAN_AUTHORIZATION_WITH_ADDRESS preimage. The host reconstructs
     * the preimage from the submitted credential arm, so a mismatched arm yields a hash
     * the host rejects. Selection is delegated to [Auth.buildHashIDPreimage].
     *
     * The expiration ledger is applied to the credentials before the preimage is built;
     * the host reconstructs the preimage from the submitted credentials, so the
     * expiration must be present at hash time.
     *
     * @param entry The authorization entry to build the payload hash for. Must carry
     *   address-bearing credentials (Address, AddressV2, or AddressWithDelegates).
     * @param expirationLedger The ledger number at which the signature expires
     * @param networkPassphrase The network passphrase (e.g., "Test SDF Network ; September 2015")
     * @return The 32-byte SHA-256 hash of the authorization payload
     * @throws TransactionException.SigningFailed if the credentials are source-account
     *         (Void) or if XDR encoding fails
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
        val addressCredentials = entry.credentials.addressCredentials()
            ?: throw TransactionException.signingFailed(
                "Credentials must be address-bearing (Address, AddressV2, or " +
                    "AddressWithDelegates) to build auth payload hash"
            )

        val networkId = getSha256Crypto().hash(networkPassphrase.encodeToByteArray())
        val updatedAddressCredentials = addressCredentials.copy(
            signatureExpirationLedger = Uint32Xdr(expirationLedger)
        )
        val preimage = Auth.buildHashIDPreimage(
            credentials = entry.credentials.withUpdatedAddressCredentials(updatedAddressCredentials),
            networkId = networkId,
            invocation = entry.rootInvocation
        )

        return hashPreimage(preimage)
    }

    /**
     * Builds the authorization payload hash for source_account credentials.
     *
     * This is used when converting source_account (Void) credentials to fresh
     * address-bearing credentials, typically for relayer fee sponsoring. The payload
     * uses the provided address, nonce, and expiration since there are no
     * existing address credentials yet.
     *
     * [useUpgradedAuth] selects the credential arm the signature is built for, and with
     * it the preimage type. The upgraded ADDRESS_V2 arm hashes the address-bound
     * ENVELOPE_TYPE_SOROBAN_AUTHORIZATION_WITH_ADDRESS preimage:
     * ```
     * HashIdPreimage::SorobanAuthorizationWithAddress {
     *   networkId: SHA256(networkPassphrase as UTF-8),
     *   nonce: provided nonce,
     *   signatureExpirationLedger: expirationLedger,
     *   address: provided address,
     *   invocation: entry.rootInvocation
     * }
     * hash = SHA256(XDR_encode(payload))
     * ```
     * The legacy ADDRESS arm hashes the ENVELOPE_TYPE_SOROBAN_AUTHORIZATION preimage,
     * which carries the same fields except the address. [address] is still the address
     * of the credentials the signature is attached to in both arms; the legacy preimage
     * simply does not include it. Selection is delegated to [Auth.buildHashIDPreimage].
     *
     * The host reconstructs the preimage from the submitted credential arm, so the arm
     * chosen here must match the arm the signature is attached to.
     *
     * @param entry The authorization entry with source_account credentials
     * @param address The address carried by the new credentials
     * @param nonce The nonce to use for the new credentials
     * @param expirationLedger The ledger number at which the signature expires
     * @param networkPassphrase The network passphrase
     * @param useUpgradedAuth True (the default) hashes the ADDRESS_V2 address-bound
     *   preimage; false hashes the legacy ADDRESS preimage.
     * @return The 32-byte SHA-256 hash of the authorization payload
     * @throws TransactionException.SigningFailed if XDR encoding fails
     */
    suspend fun buildSourceAccountAuthPayloadHash(
        entry: SorobanAuthorizationEntryXdr,
        address: SCAddressXdr,
        nonce: Int64Xdr,
        expirationLedger: UInt,
        networkPassphrase: String,
        useUpgradedAuth: Boolean = true
    ): ByteArray {
        val networkId = getSha256Crypto().hash(networkPassphrase.encodeToByteArray())
        val addressCredentials = SorobanAddressCredentialsXdr(
            address = address,
            nonce = nonce,
            signatureExpirationLedger = Uint32Xdr(expirationLedger),
            signature = SCValXdr.Void(SCValTypeXdr.SCV_VOID)
        )
        val credentials = if (useUpgradedAuth) {
            SorobanCredentialsXdr.AddressV2(addressCredentials)
        } else {
            SorobanCredentialsXdr.Address(addressCredentials)
        }
        val preimage = Auth.buildHashIDPreimage(
            credentials = credentials,
            networkId = networkId,
            invocation = entry.rootInvocation
        )
        return hashPreimage(preimage)
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

        var credentials = entryCopy.credentials.addressCredentials()
            ?: throw TransactionException.signingFailed(
                "Credentials must be address-bearing (Address, AddressV2, or " +
                    "AddressWithDelegates) to sign auth entry"
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

        // Build updated credentials with new signature, preserving the credential arm.
        val updatedCredentials = SorobanAddressCredentialsXdr(
            address = credentials.address,
            nonce = credentials.nonce,
            signatureExpirationLedger = credentials.signatureExpirationLedger,
            signature = payloadScVal
        )

        return SorobanAuthorizationEntryXdr(
            credentials = entryCopy.credentials.withUpdatedAddressCredentials(updatedCredentials),
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
        val credentials = entry.credentials.addressCredentials()
            ?: throw TransactionException.signingFailed(
                "Credentials must be address-bearing (Address, AddressV2, or " +
                    "AddressWithDelegates) to add signature map entry"
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
            credentials = entry.credentials.withUpdatedAddressCredentials(updatedCredentials),
            rootInvocation = entry.rootInvocation
        )
    }

    // ========================================================================
    // Helper Functions
    // ========================================================================

    /**
     * XDR-encodes [preimage] and returns SHA-256(encoded bytes).
     *
     * @throws TransactionException.SigningFailed if XDR encoding fails
     */
    private suspend fun hashPreimage(preimage: HashIDPreimageXdr): ByteArray {
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
