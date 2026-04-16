package com.soneso.smartdemo.wallet

/**
 * Abstraction for connecting an external Stellar wallet (e.g., Freighter) to the demo app.
 *
 * Platform implementations:
 * - Web: [FreighterConnector] using @stellar/freighter-api (browser extension)
 * - Android: [ReownConnector] using Reown SignClient (WalletConnect v2)
 * - iOS: [ReownConnector] using Reown Swift SDK (WalletConnect v2)
 * - macOS: Not available (null connector)
 *
 * Injected via [DemoState.setWalletConnector] by platform entry points before UI renders.
 */
interface WalletConnector {

    /**
     * Initiates a wallet connection and returns the connected account details.
     *
     * @return The connected wallet info, or null if the user cancelled or the wallet is unavailable
     * @throws WalletConnectionException on connection errors
     */
    suspend fun connect(): WalletConnection?

    /**
     * Disconnects the wallet session for the given address.
     *
     * Cleans up any active WalletConnect session or local state.
     *
     * @param address The G-address to disconnect
     */
    suspend fun disconnect(address: String)

    /**
     * Signs a Soroban auth entry preimage using the connected wallet.
     *
     * The wallet performs SHA-256 hashing and Ed25519 signing internally.
     * The returned value must be a base64-encoded raw 64-byte Ed25519 signature.
     *
     * @param authEntryPreimageXdr Base64-encoded HashIdPreimage XDR
     * @param address The G-address of the signer in the wallet
     * @return Base64-encoded raw Ed25519 signature (64 bytes)
     * @throws WalletSigningException if the wallet rejects or fails to sign
     */
    suspend fun signAuthEntry(authEntryPreimageXdr: String, address: String): String

    /**
     * Returns true if a wallet session is active for the given address.
     */
    fun isConnected(address: String): Boolean

    /**
     * Returns the G-address of the currently connected wallet, or null if not connected.
     */
    fun getConnectedAddress(): String?

    /**
     * Returns the network passphrase reported by the connected wallet, or null if unavailable.
     *
     * Used to verify the wallet is on the same network as the demo app.
     */
    suspend fun getNetworkPassphrase(): String?
}

/**
 * Information about a successfully connected wallet.
 *
 * @property address The Stellar G-address from the wallet
 * @property walletName Human-readable wallet name (e.g., "Freighter")
 */
data class WalletConnection(
    val address: String,
    val walletName: String
)

/**
 * Thrown when wallet connection fails.
 */
class WalletConnectionException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)

/**
 * Thrown when the wallet rejects or fails to sign an auth entry.
 */
class WalletSigningException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)

/**
 * Thrown when the connected wallet is on a different network than expected.
 */
class WalletNetworkMismatchException(
    val expected: String,
    val actual: String
) : Exception("Wallet is connected to a different network. Expected: $expected, got: $actual")
