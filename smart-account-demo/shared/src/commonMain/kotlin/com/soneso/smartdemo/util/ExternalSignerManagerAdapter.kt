package com.soneso.smartdemo.util

/**
 * Implements ExternalWalletAdapter using in-memory Ed25519 keypairs for multi-signer transfers.
 *
 * This adapter is used with OZSmartAccountConfig.externalWallet so that
 * OZMultiSignerManager.multiSignerTransfer() can sign auth entries for delegated
 * (SelectedSigner.Wallet) signers whose secret keys are provided by the user.
 *
 * When multiSignerTransfer encounters a SelectedSigner.Wallet, the SDK:
 *   1. Validates via canSignFor()
 *   2. Builds the auth entry and preimage internally
 *   3. Calls signAuthEntry() with the base64-encoded HashIDPreimage XDR
 *
 * signAuthEntry() in this adapter:
 *   1. Decodes the HashIDPreimage from base64
 *   2. SHA-256 hashes the preimage bytes
 *   3. Ed25519-signs the hash with the registered keypair
 *   4. Returns the raw 64-byte signature as base64
 *
 * The SDK handles auth entry construction and the {public_key, signature} format.
 * Keypairs are held in memory only and cleared when removeAll() is called.
 */

import com.soneso.stellar.sdk.KeyPair
import com.soneso.stellar.sdk.crypto.getSha256Crypto
import com.soneso.stellar.sdk.smartaccount.oz.ConnectedWallet
import com.soneso.stellar.sdk.smartaccount.oz.ExternalWalletAdapter
import com.soneso.stellar.sdk.smartaccount.oz.SignAuthEntryOptions
import com.soneso.stellar.sdk.smartaccount.oz.SignAuthEntryResult

/**
 * In-memory ExternalWalletAdapter for keypair-based delegated signers.
 */
class ExternalSignerManagerAdapter : ExternalWalletAdapter {

    // Keypairs indexed by G-address. Populated via addFromSecret().
    private val keypairsByAddress = mutableMapOf<String, KeyPair>()

    /**
     * Registers an Ed25519 keypair signer from a Stellar secret key (S...).
     *
     * Must be called before invoking multiSignerTransfer() with a SelectedSigner.Wallet
     * for this address. The keypair is held in memory only — it is not persisted.
     *
     * @param secretKey A valid Stellar secret key (S...)
     * @return The G-address derived from the secret key
     * @throws Exception if the secret key is invalid
     */
    suspend fun addFromSecret(secretKey: String): String {
        val keypair = KeyPair.fromSecretSeed(secretKey)
        val address = keypair.getAccountId()
        keypairsByAddress[address] = keypair
        return address
    }

    /**
     * Removes a signer by G-address.
     */
    fun remove(address: String) {
        keypairsByAddress.remove(address)
    }

    /**
     * Removes all registered keypair signers.
     */
    fun removeAll() {
        keypairsByAddress.clear()
    }

    // MARK: - ExternalWalletAdapter

    /** Not supported — returns null. Use addFromSecret() to register keypairs. */
    override suspend fun connect(): ConnectedWallet? = null

    /** Clears all registered keypair signers. */
    override suspend fun disconnect() {
        removeAll()
    }

    /**
     * Signs a HashIDPreimage for the delegated signer at options.address.
     *
     * Called by the SDK's Auth.Signer wrapper in multiSignerTransfer(). The SDK
     * handles auth entry construction and signature format — this adapter only
     * needs to hash the preimage and sign with Ed25519.
     *
     * @param preimageXdr Base64-encoded HashIDPreimage XDR
     * @param options options.address identifies which keypair to sign with
     * @return SignAuthEntryResult with base64-encoded raw Ed25519 signature bytes
     */
    @OptIn(kotlin.io.encoding.ExperimentalEncodingApi::class)
    override suspend fun signAuthEntry(
        preimageXdr: String,
        options: SignAuthEntryOptions?
    ): SignAuthEntryResult {
        val address = options?.address
            ?: throw IllegalArgumentException(
                "signAuthEntry requires options.address to identify the keypair"
            )

        val keypair = keypairsByAddress[address]
            ?: throw IllegalStateException(
                "No keypair registered for address $address. " +
                    "Call addFromSecret() before invoking multiSignerTransfer()."
            )

        // Decode the preimage XDR and hash it
        val preimageBytes = kotlin.io.encoding.Base64.decode(preimageXdr)
        val payloadHash = getSha256Crypto().hash(preimageBytes)

        // Sign the hash with Ed25519
        val signatureBytes = keypair.sign(payloadHash)

        // Return raw signature bytes as base64
        return SignAuthEntryResult(
            signedAuthEntry = kotlin.io.encoding.Base64.encode(signatureBytes),
            signerAddress = address
        )
    }

    /**
     * Returns an empty list. Keypair signers are not surfaced as ConnectedWallet objects.
     */
    override fun getConnectedWallets(): List<ConnectedWallet> = emptyList()

    /**
     * Returns true if a keypair has been registered for the given address via addFromSecret().
     *
     * This synchronous form is required by ExternalWalletAdapter and is called by
     * OZMultiSignerManager.multiSignerTransfer().
     */
    override fun canSignFor(address: String): Boolean {
        return keypairsByAddress.containsKey(address)
    }

    /** Not supported — returns null. */
    override suspend fun reconnect(walletId: String): ConnectedWallet? = null
}
