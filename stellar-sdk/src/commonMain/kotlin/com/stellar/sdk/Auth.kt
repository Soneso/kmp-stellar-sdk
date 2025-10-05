package com.stellar.sdk

import com.stellar.sdk.crypto.getEd25519Crypto
import com.stellar.sdk.scval.Scv
import com.stellar.sdk.xdr.*

/**
 * Helper class for signing Soroban authorization entries.
 *
 * This class provides methods to authorize smart contract invocations by:
 * - Signing existing authorization entries (typically from transaction simulation)
 * - Building new authorization entries from scratch
 * - Supporting custom signing logic through the [Signer] interface
 *
 * ## Usage Examples
 *
 * ### Authorize an existing entry with a KeyPair
 * ```kotlin
 * val entry = SorobanAuthorizationEntryXdr.fromXdrBase64("...")
 * val signer = KeyPair.fromSecretSeed("S...")
 * val network = Network.TESTNET
 * val validUntilLedgerSeq = 1000000L
 *
 * val signedEntry = Auth.authorizeEntry(
 *     entry = entry,
 *     signer = signer,
 *     validUntilLedgerSeq = validUntilLedgerSeq,
 *     network = network
 * )
 * ```
 *
 * ### Build a new authorization from scratch
 * ```kotlin
 * val invocation = SorobanAuthorizedInvocationXdr(...)
 * val signer = KeyPair.fromSecretSeed("S...")
 * val network = Network.TESTNET
 * val validUntilLedgerSeq = 1000000L
 *
 * val authEntry = Auth.authorizeInvocation(
 *     signer = signer,
 *     validUntilLedgerSeq = validUntilLedgerSeq,
 *     invocation = invocation,
 *     network = network
 * )
 * ```
 *
 * ### Use a custom signer
 * ```kotlin
 * val customSigner = object : Auth.Signer {
 *     override suspend fun sign(preimage: HashIDPreimageXdr): Auth.Signature {
 *         val payload = Util.hash(preimage.toXdrByteArray())
 *         // Custom signing logic (e.g., hardware wallet)
 *         val signature = myCustomSigningDevice.sign(payload)
 *         return Auth.Signature(publicKey = "G...", signature = signature)
 *     }
 * }
 *
 * val signedEntry = Auth.authorizeEntry(
 *     entry = entry,
 *     signer = customSigner,
 *     validUntilLedgerSeq = validUntilLedgerSeq,
 *     network = network
 * )
 * ```
 *
 * @see <a href="https://developers.stellar.org/docs/learn/encyclopedia/security/authorization/">Smart Contract Authorization</a>
 */
object Auth {

    /**
     * Authorizes an existing authorization entry using a KeyPair.
     *
     * This "fills out" the authorization entry with a signature, indicating to the
     * InvokeHostFunctionOperation it's attached to that:
     * - a particular identity (signing KeyPair) approves
     * - the execution of an invocation tree
     * - on a particular network (replay protection)
     * - until a particular ledger sequence is reached
     *
     * @param entry The base64 encoded unsigned Soroban authorization entry
     * @param signer The KeyPair to sign with (must correspond to the address in the entry)
     * @param validUntilLedgerSeq The exclusive future ledger sequence until which this is valid
     * @param network The network (incorporated into the signature for replay protection)
     * @return A signed Soroban authorization entry
     * @throws IllegalArgumentException if entry cannot be decoded or signature verification fails
     */
    suspend fun authorizeEntry(
        entry: String,
        signer: KeyPair,
        validUntilLedgerSeq: Long,
        network: Network
    ): SorobanAuthorizationEntryXdr {
        val entryXdr = try {
            SorobanAuthorizationEntryXdr.fromXdrBase64(entry)
        } catch (e: Exception) {
            throw IllegalArgumentException("Unable to convert entry to SorobanAuthorizationEntry", e)
        }
        return authorizeEntry(entryXdr, signer, validUntilLedgerSeq, network)
    }

    /**
     * Authorizes an existing authorization entry using a KeyPair.
     *
     * This "fills out" the authorization entry with a signature, indicating to the
     * InvokeHostFunctionOperation it's attached to that:
     * - a particular identity (signing KeyPair) approves
     * - the execution of an invocation tree
     * - on a particular network (replay protection)
     * - until a particular ledger sequence is reached
     *
     * @param entry The unsigned Soroban authorization entry
     * @param signer The KeyPair to sign with (must correspond to the address in the entry)
     * @param validUntilLedgerSeq The exclusive future ledger sequence until which this is valid
     * @param network The network (incorporated into the signature for replay protection)
     * @return A signed Soroban authorization entry
     * @throws IllegalArgumentException if signature verification fails
     */
    suspend fun authorizeEntry(
        entry: SorobanAuthorizationEntryXdr,
        signer: KeyPair,
        validUntilLedgerSeq: Long,
        network: Network
    ): SorobanAuthorizationEntryXdr {
        val entrySigner = Signer { preimage ->
            val data = preimage.toXdrByteArray()
            val payload = Util.hash(data)
            val signature = signer.sign(payload)
            Signature(signer.getAccountId(), signature)
        }
        return authorizeEntry(entry, entrySigner, validUntilLedgerSeq, network)
    }

    /**
     * Authorizes an existing authorization entry using a custom Signer.
     *
     * This "fills out" the authorization entry with a signature, indicating to the
     * InvokeHostFunctionOperation it's attached to that:
     * - a particular identity (custom Signer) approves
     * - the execution of an invocation tree
     * - on a particular network (replay protection)
     * - until a particular ledger sequence is reached
     *
     * @param entry The base64 encoded unsigned Soroban authorization entry
     * @param signer A function that signs the hash of a HashIDPreimage
     * @param validUntilLedgerSeq The exclusive future ledger sequence until which this is valid
     * @param network The network (incorporated into the signature for replay protection)
     * @return A signed Soroban authorization entry
     * @throws IllegalArgumentException if entry cannot be decoded or signature verification fails
     */
    suspend fun authorizeEntry(
        entry: String,
        signer: Signer,
        validUntilLedgerSeq: Long,
        network: Network
    ): SorobanAuthorizationEntryXdr {
        val entryXdr = try {
            SorobanAuthorizationEntryXdr.fromXdrBase64(entry)
        } catch (e: Exception) {
            throw IllegalArgumentException("Unable to convert entry to SorobanAuthorizationEntry", e)
        }
        return authorizeEntry(entryXdr, signer, validUntilLedgerSeq, network)
    }

    /**
     * Authorizes an existing authorization entry using a custom Signer.
     *
     * This "fills out" the authorization entry with a signature, indicating to the
     * InvokeHostFunctionOperation it's attached to that:
     * - a particular identity (custom Signer) approves
     * - the execution of an invocation tree
     * - on a particular network (replay protection)
     * - until a particular ledger sequence is reached
     *
     * @param entry The unsigned Soroban authorization entry
     * @param signer A function that signs the hash of a HashIDPreimage
     * @param validUntilLedgerSeq The exclusive future ledger sequence until which this is valid
     * @param network The network (incorporated into the signature for replay protection)
     * @return A signed Soroban authorization entry
     * @throws IllegalArgumentException if signature verification fails
     */
    suspend fun authorizeEntry(
        entry: SorobanAuthorizationEntryXdr,
        signer: Signer,
        validUntilLedgerSeq: Long,
        network: Network
    ): SorobanAuthorizationEntryXdr {
        return authorizeEntryInternal(entry, signer, validUntilLedgerSeq, network)
    }

    /**
     * Builds and authorizes a new entry from scratch using a KeyPair.
     *
     * This builds an entry from scratch, allowing you to express authorization as a function of:
     * - a particular identity (signing KeyPair)
     * - approving the execution of an invocation tree
     * - on a particular network
     * - until a particular ledger sequence is reached
     *
     * @param signer The KeyPair to sign with
     * @param validUntilLedgerSeq The exclusive future ledger sequence until which this is valid
     * @param invocation The invocation tree being authorized (typically from simulation)
     * @param network The network (incorporated into the signature for replay protection)
     * @return A signed Soroban authorization entry
     * @throws IllegalArgumentException if authorization fails
     */
    suspend fun authorizeInvocation(
        signer: KeyPair,
        validUntilLedgerSeq: Long,
        invocation: SorobanAuthorizedInvocationXdr,
        network: Network
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
            network
        )
    }

    /**
     * Builds and authorizes a new entry from scratch using a custom Signer.
     *
     * This builds an entry from scratch, allowing you to express authorization as a function of:
     * - a particular identity (custom Signer)
     * - approving the execution of an invocation tree
     * - on a particular network
     * - until a particular ledger sequence is reached
     *
     * @param signer A function that signs the hash of a HashIDPreimage
     * @param publicKey The public identity of the signer (G... address)
     * @param validUntilLedgerSeq The exclusive future ledger sequence until which this is valid
     * @param invocation The invocation tree being authorized (typically from simulation)
     * @param network The network (incorporated into the signature for replay protection)
     * @return A signed Soroban authorization entry
     * @throws IllegalArgumentException if authorization fails
     */
    suspend fun authorizeInvocation(
        signer: Signer,
        publicKey: String,
        validUntilLedgerSeq: Long,
        invocation: SorobanAuthorizedInvocationXdr,
        network: Network
    ): SorobanAuthorizationEntryXdr {
        val nonce = generateNonce()
        val entry = SorobanAuthorizationEntryXdr(
            credentials = SorobanCredentialsXdr.Address(
                SorobanAddressCredentialsXdr(
                    address = Address(publicKey).toSCAddress(),
                    nonce = Int64Xdr(nonce),
                    signatureExpirationLedger = Uint32Xdr(validUntilLedgerSeq.toUInt()),
                    signature = Scv.toVoid()
                )
            ),
            rootInvocation = invocation
        )
        return authorizeEntry(entry, signer, validUntilLedgerSeq, network)
    }

    /**
     * A signature, consisting of a public key and a signature.
     *
     * @property publicKey The signer's account ID (G... address)
     * @property signature The 64-byte Ed25519 signature
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
     * An interface for signing a HashIDPreimage to produce a signature.
     *
     * This enables custom signing logic such as:
     * - Hardware wallet signing
     * - Multi-signature coordination
     * - Custom key management systems
     *
     * ## Implementation Example
     * ```kotlin
     * val signer = object : Auth.Signer {
     *     override suspend fun sign(preimage: HashIDPreimageXdr): Auth.Signature {
     *         // 1. Convert preimage to bytes and hash
     *         val payload = Util.hash(preimage.toXdrByteArray())
     *         // 2. Sign with your custom method
     *         val signature = myHardwareWallet.sign(payload)
     *         // 3. Return signature with public key
     *         return Auth.Signature("G...", signature)
     *     }
     * }
     * ```
     */
    fun interface Signer {
        /**
         * Signs a HashIDPreimage and returns the signature.
         *
         * The implementation should:
         * 1. Convert the preimage to XDR bytes
         * 2. Hash the bytes with SHA-256
         * 3. Sign the hash with Ed25519
         * 4. Return the public key and signature
         *
         * @param preimage The hash preimage to sign
         * @return The signature containing public key and signature bytes
         */
        suspend fun sign(preimage: HashIDPreimageXdr): Signature
    }

    // ============================================================================
    // Internal Helper Methods
    // ============================================================================

    /**
     * Core authorization logic that handles signing an entry.
     *
     * @param entry The authorization entry to sign
     * @param signer The signer function
     * @param validUntilLedgerSeq The expiration ledger sequence
     * @param network The network for replay protection
     * @return A signed authorization entry
     */
    private suspend fun authorizeEntryInternal(
        entry: SorobanAuthorizationEntryXdr,
        signer: Signer,
        validUntilLedgerSeq: Long,
        network: Network
    ): SorobanAuthorizationEntryXdr {
        // 1. Clone the entry to avoid mutation
        val clone = cloneEntry(entry)

        // 2. Check if credentials are address-based (only type we can sign)
        if (clone.credentials !is SorobanCredentialsXdr.Address) {
            return clone
        }

        // 3. Update signature expiration in credentials
        val addressCredentials = (clone.credentials as SorobanCredentialsXdr.Address).value
        val updatedCredentials = addressCredentials.copy(
            signatureExpirationLedger = Uint32Xdr(validUntilLedgerSeq.toUInt())
        )

        // 4. Build the hash preimage
        val preimage = buildHashIDPreimage(
            networkId = network.networkId(),
            nonce = updatedCredentials.nonce,
            invocation = clone.rootInvocation,
            signatureExpirationLedger = updatedCredentials.signatureExpirationLedger
        )

        // 5. Sign the preimage
        val signature = signer.sign(preimage)

        // 6. Verify the signature
        verifySignature(preimage, signature)

        // 7. Build the signature SCVal and update credentials
        val signatureScVal = buildSignatureScVal(signature)
        val signedCredentials = updatedCredentials.copy(signature = signatureScVal)

        // 8. Return the signed entry
        return clone.copy(credentials = SorobanCredentialsXdr.Address(signedCredentials))
    }

    /**
     * Builds a HashIDPreimage for Soroban authorization signing.
     *
     * @param networkId The SHA-256 hash of the network passphrase
     * @param nonce The nonce from the address credentials
     * @param invocation The invocation tree being authorized
     * @param signatureExpirationLedger The signature expiration ledger sequence
     * @return A HashIDPreimage ready for signing
     */
    private fun buildHashIDPreimage(
        networkId: ByteArray,
        nonce: Int64Xdr,
        invocation: SorobanAuthorizedInvocationXdr,
        signatureExpirationLedger: Uint32Xdr
    ): HashIDPreimageXdr {
        return HashIDPreimageXdr.SorobanAuthorization(
            HashIDPreimageSorobanAuthorizationXdr(
                networkId = HashXdr(networkId),
                nonce = nonce,
                signatureExpirationLedger = signatureExpirationLedger,
                invocation = invocation
            )
        )
    }

    /**
     * Verifies that a signature is valid for the given preimage.
     *
     * @param preimage The hash preimage that was signed
     * @param signature The signature to verify
     * @throws IllegalArgumentException if signature verification fails
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
     * Builds a signature SCVal structure per Stellar account contract specification.
     *
     * The structure is: Vec<Map<Symbol, Bytes>> where the map contains:
     * - "public_key": Bytes (32-byte Ed25519 public key)
     * - "signature": Bytes (64-byte Ed25519 signature)
     *
     * @see <a href="https://developers.stellar.org/docs/learn/encyclopedia/contract-development/contract-interactions/stellar-transaction#stellar-account-signatures">Stellar Account Signatures</a>
     * @param signature The signature containing public key and signature bytes
     * @return An SCVal containing the signature structure
     */
    private fun buildSignatureScVal(signature: Signature): SCValXdr {
        val publicKeyBytes = KeyPair.fromAccountId(signature.publicKey).getPublicKey()

        val sigMap = linkedMapOf(
            Scv.toSymbol("public_key") to Scv.toBytes(publicKeyBytes),
            Scv.toSymbol("signature") to Scv.toBytes(signature.signature)
        )

        return Scv.toVec(listOf(Scv.toMap(sigMap)))
    }

    /**
     * Generates a cryptographically secure random nonce.
     *
     * Uses the platform's Ed25519 crypto implementation to generate 32 random bytes,
     * then converts the first 8 bytes to a Long.
     *
     * @return A random Long suitable for use as a nonce
     */
    private suspend fun generateNonce(): Long {
        // Use the existing crypto infrastructure for secure random generation
        val randomBytes = getEd25519Crypto().generatePrivateKey() // 32 bytes

        // Convert first 8 bytes to Long
        var nonce = 0L
        for (i in 0..7) {
            nonce = (nonce shl 8) or (randomBytes[i].toLong() and 0xFF)
        }

        return nonce
    }

    /**
     * Creates a deep copy of a SorobanAuthorizationEntry.
     *
     * This ensures we don't mutate the input parameter.
     *
     * @param entry The entry to clone
     * @return A deep copy of the entry
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
     * Converts an XDR object to its byte array representation.
     *
     * @return The XDR-encoded byte array
     */
    private fun HashIDPreimageXdr.toXdrByteArray(): ByteArray {
        val writer = XdrWriter()
        encode(writer)
        return writer.toByteArray()
    }

    /**
     * Converts an XDR object to its byte array representation.
     *
     * @return The XDR-encoded byte array
     */
    private fun SorobanAuthorizationEntryXdr.toXdrByteArray(): ByteArray {
        val writer = XdrWriter()
        encode(writer)
        return writer.toByteArray()
    }
}
