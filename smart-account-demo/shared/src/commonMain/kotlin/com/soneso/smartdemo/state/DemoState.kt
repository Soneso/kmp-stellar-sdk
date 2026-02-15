package com.soneso.smartdemo.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.soneso.stellar.sdk.smartaccount.oz.OZSmartAccountKit

/**
 * Shared demo state holding the OZSmartAccountKit instance and wallet connection status.
 * Screens observe these values to react to wallet connection changes.
 */
object DemoState {
    /** The OZSmartAccountKit instance, null until configuration is applied. */
    var kit: OZSmartAccountKit? by mutableStateOf(null)
        private set

    /** Whether a wallet is currently connected. */
    var isConnected: Boolean by mutableStateOf(false)
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

    /** Current editable configuration values. */
    var accountWasmHash: String by mutableStateOf(com.soneso.smartdemo.config.DemoConfig.ACCOUNT_WASM_HASH)
    var webauthnVerifier: String by mutableStateOf(com.soneso.smartdemo.config.DemoConfig.WEBAUTHN_VERIFIER_ADDRESS)
    var ed25519Verifier: String by mutableStateOf(com.soneso.smartdemo.config.DemoConfig.ED25519_VERIFIER_ADDRESS)
    var relayerUrl: String by mutableStateOf(com.soneso.smartdemo.config.DemoConfig.DEFAULT_RELAYER_URL)

    fun setKitInstance(newKit: OZSmartAccountKit) {
        kit = newKit
    }

    fun setConnected(connected: Boolean, contract: String? = null, credential: String? = null) {
        isConnected = connected
        contractId = contract
        credentialId = credential
        if (!connected) balance = null
    }

    fun updateBalance(newBalance: String?) {
        balance = newBalance
    }

    fun reset() {
        kit = null
        isConnected = false
        contractId = null
        credentialId = null
        balance = null
    }
}
