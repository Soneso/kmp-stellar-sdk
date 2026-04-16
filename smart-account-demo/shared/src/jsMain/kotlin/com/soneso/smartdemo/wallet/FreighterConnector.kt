package com.soneso.smartdemo.wallet

import kotlinx.coroutines.await
import kotlin.js.Promise

// ---------------------------------------------------------------------------
// External declarations for @stellar/freighter-api v6.x
//
// All functions return Promise<dynamic>. They always resolve (never reject).
// Errors are returned in the `error` field: { code: number, message: string }.
//
// API shapes (from github.com/stellar/freighter/@stellar/freighter-api/src/):
//   requestAccess()   -> { address: string, error?: {code,message} }
//   signAuthEntry()   -> { signedAuthEntry: string|null, signerAddress: string, error?: {code,message} }
//   getNetworkDetails() -> { networkPassphrase: string, network: string, ..., error?: {code,message} }
// ---------------------------------------------------------------------------

private external interface FreighterApi {
    fun requestAccess(): Promise<dynamic>
    fun signAuthEntry(entryXdr: String, opts: dynamic): Promise<dynamic>
    fun getNetworkDetails(): Promise<dynamic>
}

private fun loadFreighterApi(): FreighterApi? {
    return try {
        js("require('@stellar/freighter-api')").unsafeCast<FreighterApi?>()
    } catch (_: Throwable) {
        null
    }
}

// ---------------------------------------------------------------------------
// FreighterConnector
// ---------------------------------------------------------------------------

/**
 * Web implementation of [WalletConnector] using the Freighter browser extension.
 *
 * Uses @stellar/freighter-api v6.x which returns objects with optional error fields.
 * All API calls resolve (never reject); errors are in `response.error`.
 *
 * signAuthEntry opts uses `address` (the API maps it to `accountToSign` internally).
 * The extension returns `signedAuthEntry` as a base64 string (API >= 4.2.0).
 */
class FreighterConnector : WalletConnector {

    private var connectedAddress: String? = null

    override suspend fun connect(): WalletConnection? {
        val api = loadFreighterApi()
            ?: throw WalletConnectionException("Freighter API module is not available.")

        // requestAccess() -> { address: string, error?: {code, message} }
        val result = api.requestAccess().await()

        // Check for error (user rejection, extension not installed)
        val error = result?.error
        if (error != null && error != js("undefined")) {
            val code = error.code
            val message = error.message?.unsafeCast<String?>() ?: "Unknown error"
            // code -4 = user rejected
            if (code == -4) return null
            throw WalletConnectionException("Freighter: $message")
        }

        val address = result?.address?.unsafeCast<String?>()
        if (address.isNullOrBlank()) return null

        connectedAddress = address
        return WalletConnection(address = address, walletName = "Freighter")
    }

    override suspend fun disconnect(address: String) {
        if (connectedAddress == address) {
            connectedAddress = null
        }
    }

    /**
     * Signs a Soroban auth entry via the Freighter extension.
     *
     * signAuthEntry(xdr, {address}) -> { signedAuthEntry: string|null, signerAddress, error? }
     * In v6.x the extension returns signedAuthEntry as a base64 string.
     */
    override suspend fun signAuthEntry(authEntryPreimageXdr: String, address: String): String {
        val api = loadFreighterApi()
            ?: throw WalletSigningException("Freighter API module is not available.")

        val opts = js("{}")
        opts.address = address

        val result = api.signAuthEntry(authEntryPreimageXdr, opts).await()

        // Check for error
        val error = result?.error
        if (error != null && error != js("undefined")) {
            val message = error.message?.unsafeCast<String?>() ?: "Unknown error"
            throw WalletSigningException("Freighter signing failed: $message")
        }

        val signedAuthEntry = result?.signedAuthEntry
        if (signedAuthEntry == null || signedAuthEntry == js("undefined")) {
            throw WalletSigningException("Freighter returned no signature for address $address")
        }

        // v6.x returns base64 string. Handle Buffer fallback for safety.
        val signature: String = if (jsTypeOf(signedAuthEntry) == "string") {
            signedAuthEntry.unsafeCast<String>()
        } else {
            // Buffer/Uint8Array fallback
            js("btoa(String.fromCharCode.apply(null, new Uint8Array(signedAuthEntry)))").unsafeCast<String>()
        }

        if (signature.isBlank()) {
            throw WalletSigningException("Freighter returned an empty signature for address $address")
        }

        return signature
    }

    override fun isConnected(address: String): Boolean = connectedAddress == address

    override fun getConnectedAddress(): String? = connectedAddress

    override suspend fun getNetworkPassphrase(): String? {
        val api = loadFreighterApi() ?: return null
        return try {
            val details = api.getNetworkDetails().await()
            val error = details?.error
            if (error != null && error != js("undefined")) return null
            val passphrase = details?.networkPassphrase
            if (passphrase == null || passphrase == js("undefined")) return null
            passphrase.unsafeCast<String>().takeIf { it.isNotBlank() }
        } catch (_: Throwable) {
            null
        }
    }
}
