package com.soneso.smartdemo.util

/**
 * Implements [ExternalWalletAdapter] for the external-wallet custody model.
 *
 * Delegates capability checks and signing to a connected external wallet (Freighter on web
 * or mobile) via a [WalletConnector]. The kit-owned OZExternalSignerManager injects this
 * adapter as the wallet backend and routes SelectedSigner.Wallet signers whose address is
 * connected through the wallet to it.
 *
 * In-memory Ed25519 keypair custody is handled natively by the manager
 * (kit.externalSigners.addFromSecret); this adapter covers only the wallet-connector path.
 */

import com.soneso.smartdemo.wallet.WalletConnector
import com.soneso.stellar.sdk.smartaccount.oz.ConnectedWallet
import com.soneso.stellar.sdk.smartaccount.oz.ExternalWalletAdapter
import com.soneso.stellar.sdk.smartaccount.oz.SignAuthEntryOptions
import com.soneso.stellar.sdk.smartaccount.oz.SignAuthEntryResult

/**
 * [ExternalWalletAdapter] backed by an external wallet connector.
 */
class ExternalSignerManagerAdapter : ExternalWalletAdapter {

    /** External wallet connector for Freighter signing. Set by DemoState injection. */
    var walletConnector: WalletConnector? = null

    // MARK: - ExternalWalletAdapter

    /** Not supported — wallet connection is initiated through the platform connector. */
    override suspend fun connect(): ConnectedWallet? = null

    /** Disconnects any active wallet session. */
    override suspend fun disconnect() {
        walletConnector?.let { connector ->
            connector.getConnectedAddress()?.let { address ->
                connector.disconnect(address)
            }
        }
    }

    /**
     * Signs a HashIDPreimage for the wallet-connected signer at options.address.
     *
     * Delegates to [WalletConnector.signAuthEntry], which performs signing externally
     * (Freighter extension or WalletConnect).
     *
     * @param preimageXdr Base64-encoded HashIDPreimage XDR.
     * @param options options.address identifies which connected wallet to sign with.
     * @return SignAuthEntryResult with the base64-encoded signature.
     */
    override suspend fun signAuthEntry(
        preimageXdr: String,
        options: SignAuthEntryOptions?
    ): SignAuthEntryResult {
        val address = options?.address
            ?: throw IllegalArgumentException(
                "signAuthEntry requires options.address to identify the signer"
            )

        val connector = walletConnector
            ?: throw IllegalStateException(
                "No wallet connector available for address $address. Connect a wallet first."
            )

        val signature = connector.signAuthEntry(preimageXdr, address)
        return SignAuthEntryResult(
            signedAuthEntry = signature,
            signerAddress = address
        )
    }

    /**
     * Returns an empty list. Wallet connections are surfaced through the connector, not here.
     */
    override fun getConnectedWallets(): List<ConnectedWallet> = emptyList()

    /**
     * Returns true when the wallet connector reports an active session for the given address.
     */
    override fun canSignFor(address: String): Boolean {
        return walletConnector?.isConnected(address) == true
    }
}
