package com.soneso.smartdemo.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.soneso.smartdemo.util.ExternalSignerManagerAdapter
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
     * The external signer manager adapter used for delegated (keypair) signers.
     * Set during kit initialization so TransferScreen can register secret keys.
     */
    var externalSignerManager: ExternalSignerManagerAdapter? = null
        private set

    /** Platform-specific WebAuthn provider, set by platform entry points (MainActivity, AppDelegate, etc.). */
    var webauthnProvider: WebAuthnProvider? = null
        private set

    /** Platform-specific storage adapter, set by platform entry points. */
    var storage: StorageAdapter? = null
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

    fun setExternalSignerManager(manager: ExternalSignerManagerAdapter?) {
        externalSignerManager = manager
    }

    fun setWebAuthnProvider(provider: WebAuthnProvider?) {
        webauthnProvider = provider
    }

    fun setStorage(storageAdapter: StorageAdapter?) {
        storage = storageAdapter
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

    fun setDeployed(deployed: Boolean) {
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
        externalSignerManager = null
        webauthnProvider = null
        storage = null
        isConnected = false
        isDeployed = false
        contractId = null
        credentialId = null
        balance = null
        demoTokenContractId = null
        demoTokenBalance = null
    }
}
