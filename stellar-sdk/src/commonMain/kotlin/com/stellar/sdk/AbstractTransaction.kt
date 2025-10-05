package com.stellar.sdk

import com.stellar.sdk.xdr.*
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * Abstract base class for transaction classes.
 *
 * Stellar supports two types of transactions:
 * - [Transaction] - Standard transaction containing operations
 * - FeeBumpTransaction - Transaction that wraps another transaction to bump its fee
 *
 * This abstract class provides common functionality for signing, hashing, and serializing transactions.
 *
 * ## Transaction Lifecycle
 *
 * 1. **Build** - Create transaction using TransactionBuilder
 * 2. **Sign** - Add signatures using sign() methods
 * 3. **Serialize** - Convert to XDR for network submission
 * 4. **Submit** - Send to Horizon or RPC server
 *
 * ## Signing
 *
 * Transactions must be signed by the source account and any other required signers.
 * Signatures are added using the sign() methods:
 *
 * ```kotlin
 * val transaction = TransactionBuilder(...)
 *     .build()
 *
 * // Sign with keypair
 * transaction.sign(keypair)
 *
 * // Sign with hash(x) preimage
 * transaction.signHashX(preimage)
 * ```
 *
 * ## Network Passphrase
 *
 * Each transaction is tied to a specific network (PUBLIC, TESTNET, etc.) through its network passphrase.
 * The network ID (SHA-256 hash of passphrase) is included in the transaction hash to prevent
 * replay attacks across different networks.
 *
 * @property network The network this transaction is for
 * @property signatures List of signatures attached to this transaction (mutable)
 *
 * @see <a href="https://developers.stellar.org/docs/learn/fundamentals/transactions">Transactions</a>
 * @see <a href="https://developers.stellar.org/docs/learn/encyclopedia/security/signatures-multisig">Signatures and Multisig</a>
 */
@OptIn(ExperimentalEncodingApi::class)
abstract class AbstractTransaction(
    val network: Network
) {
    /**
     * List of signatures attached to this transaction.
     *
     * Signatures can be added via [sign] methods or directly to this list.
     * The list is mutable to support incremental signing.
     */
    val signatures: MutableList<DecoratedSignature> = mutableListOf()

    /**
     * Returns the signature base - the data that must be signed.
     *
     * The signature base consists of the network ID hash concatenated with the transaction XDR.
     * This data is hashed and signed by transaction signers.
     *
     * @return The signature base bytes
     */
    abstract fun signatureBase(): ByteArray

    /**
     * Returns the transaction hash (SHA-256 of signature base).
     *
     * The transaction hash uniquely identifies the transaction on the network.
     * It's also what gets signed by transaction signers.
     *
     * @return The 32-byte SHA-256 hash
     */
    fun hash(): ByteArray {
        return Util.hash(signatureBase())
    }

    /**
     * Returns the transaction hash as a lowercase hexadecimal string.
     *
     * This format is commonly used when displaying transaction IDs.
     *
     * @return The transaction hash as a 64-character hex string
     */
    fun hashHex(): String {
        return Util.bytesToHex(hash()).lowercase()
    }

    /**
     * Converts this transaction to its XDR envelope representation.
     *
     * The envelope includes the transaction data and all signatures.
     *
     * @return The XDR TransactionEnvelope object
     */
    abstract fun toEnvelopeXdr(): TransactionEnvelopeXdr

    /**
     * Returns base64-encoded TransactionEnvelope XDR.
     *
     * This is the format required when submitting transactions to Horizon or RPC servers.
     *
     * @return The base64-encoded envelope
     */
    fun toEnvelopeXdrBase64(): String {
        val writer = XdrWriter()
        toEnvelopeXdr().encode(writer)
        val bytes = writer.toByteArray()
        return Base64.encode(bytes)
    }

    /**
     * Adds a signature by signing the transaction hash with the provided keypair.
     *
     * The signature is created by:
     * 1. Computing the transaction hash
     * 2. Signing the hash with the keypair's private key
     * 3. Creating a decorated signature with the signature hint
     * 4. Adding it to the signatures list
     *
     * @param signer The keypair to sign with (must have private key)
     * @throws IllegalStateException if the keypair doesn't contain a private key
     */
    suspend fun sign(signer: KeyPair) {
        val txHash = hash()
        val decoratedSignature = signer.signDecorated(txHash)
        signatures.add(decoratedSignature)
    }

    /**
     * Adds a hash(x) signature by revealing the preimage.
     *
     * This is used for hash(x) signers where the signature is the preimage itself,
     * and the signer key is the hash of the preimage.
     *
     * The signature hint is the last 4 bytes of the hash of the preimage.
     *
     * @param preimage The preimage bytes whose hash equals the signer's hash
     */
    fun signHashX(preimage: ByteArray) {
        val hash = Util.hash(preimage)
        val hint = hash.copyOfRange(hash.size - 4, hash.size)
        signatures.add(DecoratedSignature(hint, preimage))
    }

    companion object {
        /**
         * Minimum base fee per operation in stroops (0.00001 XLM).
         *
         * The actual transaction fee is calculated as: baseFee * operations.size
         *
         * If the fee is below the network minimum, the transaction will fail.
         *
         * @see <a href="https://developers.stellar.org/docs/learn/fundamentals/fees-resource-limits-metering#inclusion-fee">Fees</a>
         */
        const val MIN_BASE_FEE = 100L

        /**
         * Creates an AbstractTransaction from a base64-encoded transaction envelope XDR.
         *
         * @param envelope The base64-encoded envelope
         * @param network The network this transaction is for
         * @return Transaction or FeeBumpTransaction instance
         * @throws IllegalArgumentException if the envelope is malformed or type is unsupported
         */
        fun fromEnvelopeXdr(envelope: String, network: Network): AbstractTransaction {
            val bytes = try {
                Base64.decode(envelope)
            } catch (e: Exception) {
                throw IllegalArgumentException("Invalid base64 encoding: ${e.message}", e)
            }
            return fromEnvelopeXdr(bytes, network)
        }

        /**
         * Creates an AbstractTransaction from transaction envelope XDR bytes.
         *
         * @param envelopeBytes The XDR envelope bytes
         * @param network The network this transaction is for
         * @return Transaction or FeeBumpTransaction instance
         * @throws IllegalArgumentException if the envelope type is unsupported
         */
        fun fromEnvelopeXdr(envelopeBytes: ByteArray, network: Network): AbstractTransaction {
            val reader = XdrReader(envelopeBytes)
            val envelope = TransactionEnvelopeXdr.decode(reader)
            return fromEnvelopeXdr(envelope, network)
        }

        /**
         * Creates an AbstractTransaction from a TransactionEnvelopeXdr object.
         *
         * @param envelope The XDR envelope
         * @param network The network this transaction is for
         * @return Transaction or FeeBumpTransaction instance
         * @throws IllegalArgumentException if the envelope type is unsupported
         */
        fun fromEnvelopeXdr(envelope: TransactionEnvelopeXdr, network: Network): AbstractTransaction {
            return when (envelope) {
                is TransactionEnvelopeXdr.V0 -> Transaction.fromV0EnvelopeXdr(envelope.value, network)
                is TransactionEnvelopeXdr.V1 -> Transaction.fromV1EnvelopeXdr(envelope.value, network)
                is TransactionEnvelopeXdr.FeeBump -> {
                    FeeBumpTransaction.fromFeeBumpTransactionEnvelope(envelope.value, network)
                }
            }
        }

        /**
         * Helper method to get the signature base for a transaction.
         *
         * The signature base consists of:
         * - Network ID hash (32 bytes)
         * - Tagged transaction (discriminant + transaction XDR)
         *
         * @param taggedTransaction The tagged transaction XDR
         * @param network The network for this transaction
         * @return The signature base bytes
         */
        internal fun getTransactionSignatureBase(
            taggedTransaction: TransactionSignaturePayloadTaggedTransactionXdr,
            network: Network
        ): ByteArray {
            val networkId = HashXdr(network.networkId())
            val payload = TransactionSignaturePayloadXdr(
                networkId = networkId,
                taggedTransaction = taggedTransaction
            )
            val writer = XdrWriter()
            payload.encode(writer)
            return writer.toByteArray()
        }
    }
}
