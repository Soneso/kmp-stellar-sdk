package com.soneso.stellar.sdk

import com.soneso.stellar.sdk.crypto.getEd25519Crypto
import com.soneso.stellar.sdk.scval.Scv
import com.soneso.stellar.sdk.xdr.*

/**
 * Helper for signing Soroban authorization entries.
 *
 * Supports legacy ADDRESS credentials (Protocol 20+), ADDRESS_V2 credentials
 * (Protocol 27+), and ADDRESS_WITH_DELEGATES credentials (Protocol 27+) with
 * recursive delegate trees.
 *
 * ## Preimage selection
 *
 * The hash preimage type is determined by the credential arm:
 * - `ADDRESS` -> `ENVELOPE_TYPE_SOROBAN_AUTHORIZATION` (legacy; not address-bound)
 * - `ADDRESS_V2` and `ADDRESS_WITH_DELEGATES` -> `ENVELOPE_TYPE_SOROBAN_AUTHORIZATION_WITH_ADDRESS`
 *
 * For `ADDRESS_WITH_DELEGATES`, the address in the preimage is always the
 * top-level credential address, never a delegate address. All signers in the
 * tree (top-level and every delegate at any depth) sign the same hash.
 *
 * ## Signature write-back
 *
 * Signing appends a new `{public_key, signature}` map element to the node's
 * existing signature vector. A void signature becomes a one-element vector.
 * Existing non-void signatures are never overwritten. Append order is
 * call order; callers are responsible for supplying signatures in ascending
 * public-key order where the host requires it (G-address, medium threshold
 * multi-sig). Calling [authorizeEntry] twice with the same key on the same
 * node appends a duplicate the host will reject.
 *
 * ## Protocol gating
 *
 * Emitting `ADDRESS_V2` or `ADDRESS_WITH_DELEGATES` on a network below
 * Protocol 27 invalidates the transaction. Legacy `ADDRESS` is the default
 * everywhere; the new arms are opt-in via [AuthOptions.authV2] or
 * [attachDelegates].
 */
object Auth {

    // ============================================================================
    // Public API — authorizeEntry overloads
    // ============================================================================

    /**
     * Authorizes an existing authorization entry (base64) using a KeyPair.
     *
     * @param entry Base64-encoded unsigned Soroban authorization entry
     * @param signer KeyPair to sign with
     * @param validUntilLedgerSeq Exclusive future ledger sequence until which this is valid
     * @param network Network for replay protection
     * @param options Signing options; see [AuthOptions]
     * @return Signed authorization entry
     * @throws IllegalArgumentException if the entry cannot be decoded or the signature is invalid
     */
    suspend fun authorizeEntry(
        entry: String,
        signer: KeyPair,
        validUntilLedgerSeq: Long,
        network: Network,
        options: AuthOptions = AuthOptions()
    ): SorobanAuthorizationEntryXdr {
        val entryXdr = try {
            SorobanAuthorizationEntryXdr.fromXdrBase64(entry)
        } catch (e: Exception) {
            throw IllegalArgumentException("Unable to convert entry to SorobanAuthorizationEntry", e)
        }
        return authorizeEntry(entryXdr, signer, validUntilLedgerSeq, network, options)
    }

    /**
     * Authorizes an existing authorization entry using a KeyPair.
     *
     * @param entry Unsigned Soroban authorization entry
     * @param signer KeyPair to sign with
     * @param validUntilLedgerSeq Exclusive future ledger sequence until which this is valid
     * @param network Network for replay protection
     * @param options Signing options; see [AuthOptions]
     * @return Signed authorization entry
     * @throws IllegalArgumentException if the signature is invalid
     */
    suspend fun authorizeEntry(
        entry: SorobanAuthorizationEntryXdr,
        signer: KeyPair,
        validUntilLedgerSeq: Long,
        network: Network,
        options: AuthOptions = AuthOptions()
    ): SorobanAuthorizationEntryXdr {
        val entrySigner = Signer { preimage ->
            val payload = Util.hash(preimage.toXdrByteArray())
            val signature = signer.sign(payload)
            Signature(signer.getAccountId(), signature)
        }
        return authorizeEntry(entry, entrySigner, validUntilLedgerSeq, network, options)
    }

    /**
     * Authorizes an existing authorization entry (base64) using a custom [Signer].
     *
     * @param entry Base64-encoded unsigned Soroban authorization entry
     * @param signer Custom signer
     * @param validUntilLedgerSeq Exclusive future ledger sequence until which this is valid
     * @param network Network for replay protection
     * @param options Signing options; see [AuthOptions]
     * @return Signed authorization entry
     * @throws IllegalArgumentException if the entry cannot be decoded or the signature is invalid
     */
    suspend fun authorizeEntry(
        entry: String,
        signer: Signer,
        validUntilLedgerSeq: Long,
        network: Network,
        options: AuthOptions = AuthOptions()
    ): SorobanAuthorizationEntryXdr {
        val entryXdr = try {
            SorobanAuthorizationEntryXdr.fromXdrBase64(entry)
        } catch (e: Exception) {
            throw IllegalArgumentException("Unable to convert entry to SorobanAuthorizationEntry", e)
        }
        return authorizeEntry(entryXdr, signer, validUntilLedgerSeq, network, options)
    }

    /**
     * Authorizes an existing authorization entry using a custom [Signer].
     *
     * @param entry Unsigned Soroban authorization entry
     * @param signer Custom signer
     * @param validUntilLedgerSeq Exclusive future ledger sequence until which this is valid
     * @param network Network for replay protection
     * @param options Signing options; see [AuthOptions]
     * @return Signed authorization entry
     * @throws IllegalArgumentException if the signature is invalid
     */
    suspend fun authorizeEntry(
        entry: SorobanAuthorizationEntryXdr,
        signer: Signer,
        validUntilLedgerSeq: Long,
        network: Network,
        options: AuthOptions = AuthOptions()
    ): SorobanAuthorizationEntryXdr {
        return authorizeEntryInternal(entry, signer, validUntilLedgerSeq, network, options)
    }

    // ============================================================================
    // Public API — authorizeInvocation
    // ============================================================================

    /**
     * Builds and signs a new authorization entry from scratch using a KeyPair.
     *
     * @param signer KeyPair to sign with
     * @param validUntilLedgerSeq Exclusive future ledger sequence until which this is valid
     * @param invocation Invocation tree being authorized (typically from simulation)
     * @param network Network for replay protection
     * @param authV2 When true, creates ADDRESS_V2 credentials instead of the legacy
     *   ADDRESS arm. ADDRESS_V2 requires Protocol 27 or later; emitting it on an
     *   older network invalidates the transaction.
     * @return Signed authorization entry
     */
    suspend fun authorizeInvocation(
        signer: KeyPair,
        validUntilLedgerSeq: Long,
        invocation: SorobanAuthorizedInvocationXdr,
        network: Network,
        authV2: Boolean = false
    ): SorobanAuthorizationEntryXdr {
        val entrySigner = Signer { preimage ->
            val payload = Util.hash(preimage.toXdrByteArray())
            val signature = signer.sign(payload)
            Signature(signer.getAccountId(), signature)
        }
        return authorizeInvocation(
            entrySigner,
            signer.getAccountId(),
            validUntilLedgerSeq,
            invocation,
            network,
            authV2
        )
    }

    /**
     * Builds and signs a new authorization entry from scratch using a custom [Signer].
     *
     * @param signer Custom signer
     * @param publicKey Public identity of the signer (G... address)
     * @param validUntilLedgerSeq Exclusive future ledger sequence until which this is valid
     * @param invocation Invocation tree being authorized (typically from simulation)
     * @param network Network for replay protection
     * @param authV2 When true, creates ADDRESS_V2 credentials instead of the legacy
     *   ADDRESS arm. ADDRESS_V2 requires Protocol 27 or later; emitting it on an
     *   older network invalidates the transaction.
     * @return Signed authorization entry
     */
    suspend fun authorizeInvocation(
        signer: Signer,
        publicKey: String,
        validUntilLedgerSeq: Long,
        invocation: SorobanAuthorizedInvocationXdr,
        network: Network,
        authV2: Boolean = false
    ): SorobanAuthorizationEntryXdr {
        val nonce = generateNonce()
        val addressCredentials = SorobanAddressCredentialsXdr(
            address = Address(publicKey).toSCAddress(),
            nonce = Int64Xdr(nonce),
            signatureExpirationLedger = Uint32Xdr(validUntilLedgerSeq.toUInt()),
            signature = Scv.toVoid()
        )
        val credentials = if (authV2) {
            SorobanCredentialsXdr.AddressV2(addressCredentials)
        } else {
            SorobanCredentialsXdr.Address(addressCredentials)
        }
        val entry = SorobanAuthorizationEntryXdr(
            credentials = credentials,
            rootInvocation = invocation
        )
        return authorizeEntry(entry, signer, validUntilLedgerSeq, network)
    }

    // ============================================================================
    // Public API — delegate tree construction
    // ============================================================================

    /**
     * Constructs a WITH_DELEGATES authorization entry from an ADDRESS or ADDRESS_V2
     * entry, attaching a sorted, validated delegate tree.
     *
     * The [delegates] list (and every nested delegate array) is sorted ascending by
     * the XDR-encoded bytes of each delegate's [SCAddressXdr]. This order is required
     * by the host; strkey ordering differs (G... < C... as strings, but ACCOUNT <
     * CONTRACT by XDR discriminant, so account addresses sort before contract
     * addresses under XDR byte comparison).
     *
     * Within any single delegate array, duplicate addresses are rejected. The same
     * address may appear at different nesting levels.
     *
     * The top-level signature in the returned entry is void; call [authorizeEntry]
     * (with [AuthOptions.forAddress] = null to sign the top-level, or set to a
     * delegate's address to sign that node) to add signatures.
     *
     * After attaching delegates the initial simulation does not include the delegate
     * authorization. Re-simulate in enforcing mode to capture the updated resource
     * usage before submitting (see CAP-0071-01 Appendix).
     *
     * @param entry Source entry with ADDRESS or ADDRESS_V2 credentials. Throws if the
     *   entry already carries AddressWithDelegates credentials.
     * @param validUntilLedgerSeq Expiration ledger sequence for the top-level credentials
     * @param delegates Delegate descriptors; sorted and duplicate-checked on construction
     * @return A new entry with AddressWithDelegates credentials and void top-level signature
     * @throws IllegalArgumentException if the entry already has AddressWithDelegates
     *   credentials, if the source entry has Void credentials, if any address is invalid
     *   or muxed, or if duplicates exist within a single delegate array
     */
    fun attachDelegates(
        entry: SorobanAuthorizationEntryXdr,
        validUntilLedgerSeq: Long,
        delegates: List<DelegateDescriptor>
    ): SorobanAuthorizationEntryXdr {
        require(entry.credentials !is SorobanCredentialsXdr.AddressWithDelegates) {
            "Entry already has AddressWithDelegates credentials; cannot re-attach delegates"
        }
        val baseCredentials = entry.credentials.requireAddressCredentials()

        val xdrDelegates = sortAndValidateDelegates(delegates.map { it.toXdr() })

        val updatedBase = baseCredentials.copy(
            signatureExpirationLedger = Uint32Xdr(validUntilLedgerSeq.toUInt()),
            signature = Scv.toVoid()
        )
        val newCredentials = SorobanCredentialsXdr.AddressWithDelegates(
            SorobanAddressCredentialsWithDelegatesXdr(
                addressCredentials = updatedBase,
                delegates = xdrDelegates
            )
        )
        return entry.copy(credentials = newCredentials)
    }

    // ============================================================================
    // Public types
    // ============================================================================

    /**
     * Options controlling [authorizeEntry] behavior.
     *
     * @property forAddress When null (default), the top-level credential address is
     *   signed. When set to a StrKey address, the signature is routed to every node
     *   in the credential tree (top-level or delegate, depth-first) whose address
     *   matches; throws if no matching node is found. Muxed (M...) addresses are
     *   not valid Soroban auth addresses and are not accepted.
     */
    data class AuthOptions(
        val forAddress: String? = null
    )

    /**
     * A signature: public key and 64-byte Ed25519 signature bytes.
     *
     * @property publicKey Signer's account ID (G... address)
     * @property signature 64-byte Ed25519 signature
     */
    data class Signature(
        val publicKey: String,
        val signature: ByteArray
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other == null || this::class != other::class) return false
            other as Signature
            if (publicKey != other.publicKey) return false
            if (!signature.contentEquals(other.signature)) return false
            return true
        }

        override fun hashCode(): Int {
            var result = publicKey.hashCode()
            result = 31 * result + signature.contentHashCode()
            return result
        }
    }

    /**
     * Signs a [HashIDPreimageXdr] and returns the resulting [Signature].
     *
     * Typical implementation:
     * 1. Encode the preimage to bytes and hash with SHA-256
     * 2. Sign the 32-byte hash with Ed25519
     * 3. Return the public key and signature
     *
     * The same preimage hash is used for every node in a WITH_DELEGATES tree;
     * callers signing delegate nodes receive the same preimage as the top-level
     * signer.
     */
    fun interface Signer {
        /**
         * @param preimage The hash preimage to sign
         * @return Signature containing public key and signature bytes
         */
        suspend fun sign(preimage: HashIDPreimageXdr): Signature
    }

    // ============================================================================
    // Internal implementation
    // ============================================================================

    /**
     * Core authorization logic.
     *
     * Source-account (Void) credentials are returned unchanged (clone only).
     * For all three address arms, expiration is set before hashing, and the
     * preimage type is selected per the arm. When [AuthOptions.forAddress] is
     * set, the signature is routed into every matching node in the tree; when
     * null the top-level credentials are signed.
     */
    private suspend fun authorizeEntryInternal(
        entry: SorobanAuthorizationEntryXdr,
        signer: Signer,
        validUntilLedgerSeq: Long,
        network: Network,
        options: AuthOptions = AuthOptions()
    ): SorobanAuthorizationEntryXdr {
        val clone = cloneEntry(entry)

        // Source-account credentials need no signing.
        val addressCredentials = clone.credentials.addressCredentials()
            ?: return clone

        // Set expiration before building the preimage — the network reconstructs
        // the preimage from the submitted credentials, so the expiration value
        // must be present at hash time.
        val updatedCredentials = addressCredentials.copy(
            signatureExpirationLedger = Uint32Xdr(validUntilLedgerSeq.toUInt())
        )

        val preimage = buildHashIDPreimage(
            credentials = clone.credentials.withUpdatedAddressCredentials(updatedCredentials),
            networkId = network.networkId(),
            invocation = clone.rootInvocation
        )

        val signature = signer.sign(preimage)
        verifySignature(preimage, signature)

        val targetAddress = options.forAddress

        return if (targetAddress == null) {
            // Sign the top-level credentials.
            val newSig = buildSignatureScVal(signature, updatedCredentials.signature)
            val signedCredentials = updatedCredentials.copy(signature = newSig)
            clone.copy(credentials = clone.credentials.withUpdatedAddressCredentials(signedCredentials))
        } else {
            // Route the signature to every node whose address matches targetAddress.
            signForAddress(clone, updatedCredentials, signature, preimage, targetAddress)
        }
    }

    /**
     * Routes a signature to every node in the credential tree whose address
     * matches [targetAddress] (StrKey).
     *
     * Checks both the top-level credential address and every delegate node
     * (depth-first). Throws if no matching node is found. The top-level
     * expiration is always set; signature is only appended to matching nodes.
     *
     * Muxed (M...) target addresses are rejected; they are not valid Soroban
     * auth addresses.
     */
    private fun signForAddress(
        clone: SorobanAuthorizationEntryXdr,
        updatedTopCredentials: SorobanAddressCredentialsXdr,
        signature: Signature,
        preimage: HashIDPreimageXdr,
        targetAddress: String
    ): SorobanAuthorizationEntryXdr {
        val targetAddr = Address(targetAddress)
        require(targetAddr.addressType != Address.AddressType.MUXED_ACCOUNT) {
            "Muxed (M...) addresses are not valid Soroban auth addresses: $targetAddress"
        }
        val targetBytes = targetAddr.toSCAddress().toXdrBytes()

        val topLevelBytes = updatedTopCredentials.address.toXdrBytes()
        val topLevelMatches = topLevelBytes.contentEquals(targetBytes)

        // For AddressWithDelegates, also traverse the delegate tree.
        val delegateMatchFound = when (clone.credentials) {
            is SorobanCredentialsXdr.AddressWithDelegates -> {
                val delegates = clone.credentials.value.delegates
                findDelegateNodes(delegates, targetBytes).isNotEmpty()
            }
            else -> false
        }

        require(topLevelMatches || delegateMatchFound) {
            "No node in the credential tree matches the target address: $targetAddress"
        }

        val newCredentials: SorobanCredentialsXdr = when (clone.credentials) {
            is SorobanCredentialsXdr.Void -> {
                // Unreachable: caller already returned early for Void.
                clone.credentials
            }
            is SorobanCredentialsXdr.Address -> {
                val newSig = if (topLevelMatches) {
                    buildSignatureScVal(signature, updatedTopCredentials.signature)
                } else {
                    updatedTopCredentials.signature
                }
                SorobanCredentialsXdr.Address(updatedTopCredentials.copy(signature = newSig))
            }
            is SorobanCredentialsXdr.AddressV2 -> {
                val newSig = if (topLevelMatches) {
                    buildSignatureScVal(signature, updatedTopCredentials.signature)
                } else {
                    updatedTopCredentials.signature
                }
                SorobanCredentialsXdr.AddressV2(updatedTopCredentials.copy(signature = newSig))
            }
            is SorobanCredentialsXdr.AddressWithDelegates -> {
                val topSig = if (topLevelMatches) {
                    buildSignatureScVal(signature, updatedTopCredentials.signature)
                } else {
                    updatedTopCredentials.signature
                }
                val updatedTopWithSig = updatedTopCredentials.copy(signature = topSig)

                val updatedDelegates = clone.credentials.value.delegates.mapMatchingDelegates(targetBytes) { node ->
                    node.copy(signature = buildSignatureScVal(signature, node.signature))
                }

                SorobanCredentialsXdr.AddressWithDelegates(
                    SorobanAddressCredentialsWithDelegatesXdr(
                        addressCredentials = updatedTopWithSig,
                        delegates = updatedDelegates
                    )
                )
            }
        }

        return clone.copy(credentials = newCredentials)
    }

    // ============================================================================
    // Preimage builder — the single construction point for all signing code
    // ============================================================================

    /**
     * Builds the [HashIDPreimageXdr] for a Soroban authorization entry.
     *
     * Preimage type is determined by the credential arm:
     * - [SorobanCredentialsXdr.Address] -> [HashIDPreimageXdr.SorobanAuthorization]
     *   (legacy; ENVELOPE_TYPE_SOROBAN_AUTHORIZATION, not address-bound)
     * - [SorobanCredentialsXdr.AddressV2] and [SorobanCredentialsXdr.AddressWithDelegates]
     *   -> [HashIDPreimageXdr.SorobanAuthorizationWithAddress]
     *   (ENVELOPE_TYPE_SOROBAN_AUTHORIZATION_WITH_ADDRESS; address is always the
     *   top-level credential address, never a delegate address)
     *
     * The [signatureExpirationLedger] in [credentials] must already be set to the
     * correct value before calling this function; the network reconstructs the
     * preimage from the submitted credentials.
     *
     * Exhaustive over [SorobanCredentialsXdr] arms.
     */
    internal fun buildHashIDPreimage(
        credentials: SorobanCredentialsXdr,
        networkId: ByteArray,
        invocation: SorobanAuthorizedInvocationXdr
    ): HashIDPreimageXdr = when (credentials) {
        is SorobanCredentialsXdr.Void ->
            throw IllegalArgumentException(
                "Cannot build a hash preimage for source-account (Void) credentials"
            )
        is SorobanCredentialsXdr.Address -> {
            val c = credentials.value
            HashIDPreimageXdr.SorobanAuthorization(
                HashIDPreimageSorobanAuthorizationXdr(
                    networkId = HashXdr(networkId),
                    nonce = c.nonce,
                    signatureExpirationLedger = c.signatureExpirationLedger,
                    invocation = invocation
                )
            )
        }
        is SorobanCredentialsXdr.AddressV2 -> {
            val c = credentials.value
            HashIDPreimageXdr.SorobanAuthorizationWithAddress(
                HashIDPreimageSorobanAuthorizationWithAddressXdr(
                    networkId = HashXdr(networkId),
                    nonce = c.nonce,
                    signatureExpirationLedger = c.signatureExpirationLedger,
                    address = c.address,
                    invocation = invocation
                )
            )
        }
        is SorobanCredentialsXdr.AddressWithDelegates -> {
            val c = credentials.value.addressCredentials
            HashIDPreimageXdr.SorobanAuthorizationWithAddress(
                HashIDPreimageSorobanAuthorizationWithAddressXdr(
                    networkId = HashXdr(networkId),
                    nonce = c.nonce,
                    signatureExpirationLedger = c.signatureExpirationLedger,
                    address = c.address,
                    invocation = invocation
                )
            )
        }
    }

    // ============================================================================
    // Private helpers
    // ============================================================================

    /**
     * Verifies that [signature] is a valid Ed25519 signature for [preimage].
     *
     * @throws IllegalArgumentException if verification fails
     */
    private suspend fun verifySignature(
        preimage: HashIDPreimageXdr,
        signature: Signature
    ) {
        val payload = Util.hash(preimage.toXdrByteArray())
        val keyPair = KeyPair.fromAccountId(signature.publicKey)
        if (!keyPair.verify(payload, signature.signature)) {
            throw IllegalArgumentException("Signature does not match payload")
        }
    }

    /**
     * Appends a new `{public_key, signature}` map element to the existing signature
     * SCVal, returning the updated vector.
     *
     * When [existingSignature] is void, the result is a one-element vector.
     * When it is already a Vec, the new element is appended at the end (call order).
     * The existing elements are never reordered or removed.
     *
     * Callers that need ascending public-key order (G-address, medium-threshold
     * multi-sig) must supply signatures in that order across successive calls.
     */
    private fun buildSignatureScVal(signature: Signature, existingSignature: SCValXdr): SCValXdr {
        val publicKeyBytes = KeyPair.fromAccountId(signature.publicKey).getPublicKey()
        val newSigMap = linkedMapOf(
            Scv.toSymbol("public_key") to Scv.toBytes(publicKeyBytes),
            Scv.toSymbol("signature") to Scv.toBytes(signature.signature)
        )

        val existingElements = mutableListOf<SCValXdr>()
        if (existingSignature is SCValXdr.Vec) {
            val vec = existingSignature.value?.value
            if (vec != null) existingElements.addAll(vec)
        }
        existingElements.add(Scv.toMap(newSigMap))
        return Scv.toVec(existingElements)
    }

    /**
     * Generates a cryptographically secure random nonce.
     *
     * Uses the platform's Ed25519 crypto implementation to generate 32 random bytes,
     * then converts the first 8 bytes to a Long.
     */
    private suspend fun generateNonce(): Long {
        val randomBytes = getEd25519Crypto().generatePrivateKey()
        var nonce = 0L
        for (i in 0..7) {
            nonce = (nonce shl 8) or (randomBytes[i].toLong() and 0xFF)
        }
        return nonce
    }

    /**
     * Creates a deep copy of a [SorobanAuthorizationEntryXdr] via XDR round-trip.
     *
     * @throws IllegalArgumentException if cloning fails
     */
    private fun cloneEntry(entry: SorobanAuthorizationEntryXdr): SorobanAuthorizationEntryXdr {
        return try {
            val bytes = entry.toXdrByteArray()
            val reader = XdrReader(bytes)
            SorobanAuthorizationEntryXdr.decode(reader)
        } catch (e: Exception) {
            throw IllegalArgumentException("Unable to clone SorobanAuthorizationEntry", e)
        }
    }

    /**
     * XDR-encodes this [HashIDPreimageXdr] to a byte array.
     */
    private fun HashIDPreimageXdr.toXdrByteArray(): ByteArray {
        val writer = XdrWriter()
        encode(writer)
        return writer.toByteArray()
    }

    /**
     * XDR-encodes this [SorobanAuthorizationEntryXdr] to a byte array.
     */
    private fun SorobanAuthorizationEntryXdr.toXdrByteArray(): ByteArray {
        val writer = XdrWriter()
        encode(writer)
        return writer.toByteArray()
    }
}
