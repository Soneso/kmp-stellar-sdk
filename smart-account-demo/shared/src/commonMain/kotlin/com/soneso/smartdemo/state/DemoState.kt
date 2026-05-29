package com.soneso.smartdemo.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.soneso.smartdemo.util.ExternalSignerManagerAdapter
import com.soneso.smartdemo.wallet.DemoEd25519Adapter
import com.soneso.smartdemo.wallet.WalletConnector
import com.soneso.stellar.sdk.smartaccount.oz.OZSmartAccountKit
import com.soneso.stellar.sdk.smartaccount.oz.StorageAdapter
import com.soneso.stellar.sdk.smartaccount.oz.WebAuthnProvider

/**
 * Shared demo state holding the OZSmartAccountKit instance and wallet connection status.
 * Screens observe these values to react to wallet connection changes.
 */
object DemoState {
    /** The OZSmartAccountKit instance, null until configuration is applied. */
    var kit: OZSmartAccountKit? by mutableStateOf(null)
        private set

    /**
     * The wallet-connector adapter backing the external-wallet (Freighter) custody model.
     * Injected as the kit's wallet adapter at initialization; flows query it via
     * [kit.externalSigners] for SelectedSigner.Wallet signers connected through a wallet.
     */
    var walletSignerAdapter: ExternalSignerManagerAdapter? = null
        private set

    /**
     * The demo Ed25519 adapter injected into the kit via
     * OZSmartAccountConfig.externalEd25519Adapter at construction. Created once and reused.
     *
     * Flows that exercise the adapter custody path register verified seeds on it via
     * [DemoEd25519Adapter.add] before submitting and clear them via [DemoEd25519Adapter.clearAll]
     * afterwards. The in-process custody path registers keys on [kit.externalSigners] instead and
     * never touches this adapter.
     */
    var demoEd25519Adapter: DemoEd25519Adapter? = null
        private set

    /** Platform-specific WebAuthn provider, set by platform entry points (MainActivity, AppDelegate, etc.). */
    var webauthnProvider: WebAuthnProvider? = null
        private set

    /** Platform-specific storage adapter, set by platform entry points. */
    var storage: StorageAdapter? = null
        private set

    /** Platform-specific wallet connector for external wallet signing (Freighter).
     *  Set by platform entry points. Null on macOS where wallet connection is not supported. */
    var walletConnector: WalletConnector? = null
        private set

    /** Whether a wallet is currently connected. */
    var isConnected: Boolean by mutableStateOf(false)
        private set

    /** Whether the connected wallet's contract has been deployed on-chain. */
    var isDeployed: Boolean by mutableStateOf(false)
        private set

    /** The connected wallet's contract address (C-address), null if disconnected. */
    var contractId: String? by mutableStateOf(null)
        private set

    /** The active credential ID (Base64URL), null if disconnected. */
    var credentialId: String? by mutableStateOf(null)
        private set

    /** The connected wallet's XLM balance (display string), null if unknown. */
    var balance: String? by mutableStateOf(null)
        private set

    /** The DEMO token contract ID, null if not yet deployed. */
    var demoTokenContractId: String? by mutableStateOf(null)
        private set

    /** The connected wallet's DEMO token balance (display string), null if unknown. */
    var demoTokenBalance: String? by mutableStateOf(null)
        private set

    fun setKitInstance(newKit: OZSmartAccountKit) {
        kit = newKit
    }

    fun setWalletSignerAdapter(adapter: ExternalSignerManagerAdapter?) {
        walletSignerAdapter = adapter
    }

    fun setDemoEd25519Adapter(adapter: DemoEd25519Adapter?) {
        demoEd25519Adapter = adapter
    }

    fun setWebAuthnProvider(provider: WebAuthnProvider?) {
        webauthnProvider = provider
    }

    fun setStorage(storageAdapter: StorageAdapter?) {
        storage = storageAdapter
    }

    fun setWalletConnector(connector: WalletConnector?) {
        walletConnector = connector
    }

    fun setConnected(connected: Boolean, contract: String? = null, credential: String? = null) {
        isConnected = connected
        contractId = contract
        credentialId = credential
        if (!connected) {
            balance = null
            isDeployed = false
        }
    }

    fun updateDeployed(deployed: Boolean) {
        isDeployed = deployed
    }

    fun updateBalance(newBalance: String?) {
        balance = newBalance
    }

    fun updateDemoToken(contractId: String?) {
        demoTokenContractId = contractId
    }

    fun updateDemoTokenBalance(balance: String?) {
        demoTokenBalance = balance
    }

    fun reset() {
        kit = null
        walletSignerAdapter = null
        demoEd25519Adapter = null
        webauthnProvider = null
        storage = null
        // walletConnector is NOT reset — it is a platform-level singleton
        // injected at app startup. Wallet sessions are disconnected in the
        // disconnect flow before reset() is called.
        isConnected = false
        isDeployed = false
        contractId = null
        credentialId = null
        balance = null
        demoTokenContractId = null
        demoTokenBalance = null
    }
}
