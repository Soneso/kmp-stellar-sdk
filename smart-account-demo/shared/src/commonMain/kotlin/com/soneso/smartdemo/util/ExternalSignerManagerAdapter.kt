package com.soneso.smartdemo.util

/**
 * Implements ExternalWalletAdapter for multi-signer transfers using two signing backends:
 *
 * 1. **In-memory Ed25519 keypairs** — from secret key entry in the signer picker dialog.
 * 2. **External wallet connector** — from a connected Freighter wallet (web or mobile).
 *
 * The SDK's OZExternalSignerManager routes SelectedSigner.Wallet signers to this adapter.
 * This adapter reports capabilities via canSignFor() and handles signing via signAuthEntry().
 *
 * For keypair signers, signAuthEntry():
 *   1. Decodes the HashIDPreimage from base64
 *   2. SHA-256 hashes the preimage bytes
 *   3. Ed25519-signs the hash with the registered keypair
 *   4. Returns the raw 64-byte signature as base64
 *
 * For wallet-connected signers, signAuthEntry() delegates to WalletConnector.signAuthEntry()
 * which handles signing externally (Freighter extension or WalletConnect).
 */

import com.soneso.smartdemo.wallet.WalletConnector
import com.soneso.stellar.sdk.KeyPair
import com.soneso.stellar.sdk.crypto.getSha256Crypto
import com.soneso.stellar.sdk.smartaccount.oz.ConnectedWallet
import com.soneso.stellar.sdk.smartaccount.oz.ExternalWalletAdapter
import com.soneso.stellar.sdk.smartaccount.oz.SignAuthEntryOptions
import com.soneso.stellar.sdk.smartaccount.oz.SignAuthEntryResult

/**
 * ExternalWalletAdapter supporting both keypair and external wallet signing.
 */
class ExternalSignerManagerAdapter : ExternalWalletAdapter {

    // Keypairs indexed by G-address. Populated via addFromSecret().
    private val keypairsByAddress = mutableMapOf<String, KeyPair>()

    /** External wallet connector for Freighter signing. Set by DemoState injection. */
    var walletConnector: WalletConnector? = null

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

    /** Clears all registered keypair signers and disconnects any active wallet session. */
    override suspend fun disconnect() {
        // Disconnect wallet session if active
        walletConnector?.let { connector ->
            connector.getConnectedAddress()?.let { address ->
                connector.disconnect(address)
            }
        }
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
                "signAuthEntry requires options.address to identify the signer"
            )

        // Check wallet connector first for wallet-connected addresses
        walletConnector?.let { connector ->
            if (connector.isConnected(address)) {
                val signature = connector.signAuthEntry(preimageXdr, address)
                return SignAuthEntryResult(
                    signedAuthEntry = signature,
                    signerAddress = address
                )
            }
        }

        // Fall back to keypair signing
        val keypair = keypairsByAddress[address]
            ?: throw IllegalStateException(
                "No signer available for address $address. " +
                    "Register a keypair via addFromSecret() or connect a wallet."
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
     * Returns true if a keypair or wallet connection is available for the given address.
     *
     * Checks keypairs first (from secret key entry), then the wallet connector
     * (from Freighter connection). Called synchronously by the SDK.
     */
    override fun canSignFor(address: String): Boolean {
        if (keypairsByAddress.containsKey(address)) return true
        return walletConnector?.isConnected(address) == true
    }

    /** Not supported — returns null. */
    override suspend fun reconnect(walletId: String): ConnectedWallet? = null
}
