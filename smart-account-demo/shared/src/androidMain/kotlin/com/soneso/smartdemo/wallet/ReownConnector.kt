package com.soneso.smartdemo.wallet

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.reown.android.CoreClient
import com.reown.sign.client.Sign
import com.reown.sign.client.SignClient
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * WalletConnect v2 (Reown SignClient) implementation of [WalletConnector] for Android.
 *
 * Connects to Freighter Mobile via deep link using the Stellar WalletConnect namespace:
 * - Chain: `stellar:testnet`
 * - Methods: `["stellar_signAuthEntry"]`
 *
 * Session lifecycle:
 * 1. [connect] creates a pairing via CoreClient.Pairing, proposes a session with the Stellar
 *    namespace, opens Freighter via deep link, then waits for session approval via the dapp
 *    delegate callback.
 * 2. [signAuthEntry] sends a `stellar_signAuthEntry` request to the active session and waits
 *    for the wallet to approve and return a base64-encoded Ed25519 signature.
 * 3. [disconnect] terminates the active WalletConnect session.
 *
 * Must be instantiated after [SmartDemoApplication.onCreate] completes so that SignClient is
 * already initialized.
 *
 * @param context Android application context used for launching deep links.
 */
class ReownConnector(private val context: Context) : WalletConnector {

    private val connectMutex = Mutex()

    /** Topic of the active WalletConnect session, null if not connected. */
    @Volatile
    private var sessionTopic: String? = null

    /** G-address from the active session's approved accounts. */
    @Volatile
    private var connectedAddress: String? = null

    /**
     * Deferred awaited during [connect] until the wallet approves or rejects the session.
     * Protected by [connectMutex] — only one session proposal may be in flight at a time.
     */
    @Volatile
    private var pendingSessionDeferred: CompletableDeferred<Sign.Model.ApprovedSession>? = null

    /**
     * Map from request ID to the deferred awaited in [signAuthEntry].
     * Synchronized on itself because multiple concurrent signing requests are supported.
     */
    private val pendingRequestDeferreds = mutableMapOf<Long, CompletableDeferred<Sign.Model.SessionRequestResponse>>()

    // -------------------------------------------------------------------------
    // Dapp delegate — handles async callbacks from SignClient
    // Declared before init block so the property is initialized when setDappDelegate is called.
    // -------------------------------------------------------------------------

    private val dappDelegate = object : SignClient.DappDelegate {

        override fun onSessionApproved(approvedSession: Sign.Model.ApprovedSession) {
            Log.d(TAG, "onSessionApproved: topic=${approvedSession.topic}")
            pendingSessionDeferred?.complete(approvedSession)
        }

        override fun onSessionRejected(rejectedSession: Sign.Model.RejectedSession) {
            Log.w(TAG, "onSessionRejected: reason=${rejectedSession.reason}")
            pendingSessionDeferred?.completeExceptionally(
                WalletConnectionException("Wallet rejected the session: ${rejectedSession.reason}")
            )
        }

        override fun onSessionRequestResponse(response: Sign.Model.SessionRequestResponse) {
            val requestId = response.result.id
            Log.d(TAG, "onSessionRequestResponse: requestId=$requestId")
            synchronized(pendingRequestDeferreds) {
                pendingRequestDeferreds[requestId]?.complete(response)
            }
        }

        override fun onProposalExpired(proposal: Sign.Model.ExpiredProposal) {
            Log.w(TAG, "onProposalExpired: pairingTopic=${proposal.pairingTopic}")
            // Do not fail the deferred here. In mobile-to-mobile flows, the relay
            // connection is suspended while the user is in Freighter. The SDK may
            // fire onProposalExpired before the relay reconnects and delivers the
            // session approval. Let the connect() timeout handle the actual failure.
        }

        override fun onRequestExpired(request: Sign.Model.ExpiredRequest) {
            Log.w(TAG, "onRequestExpired: requestId=${request.id}")
            synchronized(pendingRequestDeferreds) {
                pendingRequestDeferreds[request.id]?.completeExceptionally(
                    WalletSigningException("Signing request expired before the wallet responded.")
                )
            }
        }

        override fun onSessionDelete(deletedSession: Sign.Model.DeletedSession) {
            Log.d(TAG, "onSessionDelete: $deletedSession")
            val currentTopic = sessionTopic
            if (currentTopic != null &&
                deletedSession is Sign.Model.DeletedSession.Success &&
                deletedSession.topic == currentTopic
            ) {
                sessionTopic = null
                connectedAddress = null
                Log.i(TAG, "Session deleted by wallet. Local state cleared.")
            }
        }

        override fun onSessionUpdate(updatedSession: Sign.Model.UpdatedSession) {
            Log.d(TAG, "onSessionUpdate: topic=${updatedSession.topic}")
        }

        override fun onSessionExtend(session: Sign.Model.Session) {
            Log.d(TAG, "onSessionExtend: topic=${session.topic}")
        }

        override fun onSessionEvent(sessionEvent: Sign.Model.SessionEvent) {
            Log.d(TAG, "onSessionEvent: name=${sessionEvent.name}")
        }

        override fun onConnectionStateChange(state: Sign.Model.ConnectionState) {
            Log.d(TAG, "onConnectionStateChange: isAvailable=${state.isAvailable}")
        }

        override fun onError(error: Sign.Model.Error) {
            Log.e(TAG, "SignClient delegate error: ${error.throwable.message}", error.throwable)
        }
    }

    init {
        SignClient.setDappDelegate(dappDelegate)
    }

    // -------------------------------------------------------------------------
    // WalletConnector implementation
    // -------------------------------------------------------------------------

    override suspend fun connect(): WalletConnection? {
        val deferred = CompletableDeferred<Sign.Model.ApprovedSession>()

        connectMutex.withLock {
            if (pendingSessionDeferred != null) {
                throw WalletConnectionException("A connection attempt is already in progress.")
            }
            pendingSessionDeferred = deferred
        }

        return try {
            val pairingUri = createPairingAndConnect()

            // Launch Freighter (or any WalletConnect-compatible wallet) via deep link.
            launchWalletDeepLink(pairingUri)

            val session = withTimeoutOrNull(SESSION_TIMEOUT_MS) { deferred.await() }
                ?: throw WalletConnectionException("Wallet did not respond within the timeout period.")

            val address = extractAddressFromSession(session)

            connectMutex.withLock {
                sessionTopic = session.topic
                connectedAddress = address
            }

            Log.d(TAG, "Session established: topic=${session.topic}, address=$address")
            WalletConnection(address = address, walletName = WALLET_NAME)
        } catch (e: WalletConnectionException) {
            throw e
        } catch (e: Exception) {
            throw WalletConnectionException("Failed to connect to wallet: ${e.message}", e)
        } finally {
            connectMutex.withLock { pendingSessionDeferred = null }
        }
    }

    override suspend fun disconnect(address: String) {
        val topic: String

        connectMutex.withLock {
            if (connectedAddress != address) return
            topic = sessionTopic ?: return
            sessionTopic = null
            connectedAddress = null
        }

        val disconnectDeferred = CompletableDeferred<Unit>()

        SignClient.disconnect(
            disconnect = Sign.Params.Disconnect(sessionTopic = topic),
            onSuccess = { _ -> disconnectDeferred.complete(Unit) },
            onError = { error ->
                // Local state is already cleared; log but do not propagate.
                Log.w(TAG, "SignClient.disconnect error: ${error.throwable.message}")
                disconnectDeferred.complete(Unit)
            }
        )

        withTimeoutOrNull(DISCONNECT_TIMEOUT_MS) { disconnectDeferred.await() }
        Log.d(TAG, "Disconnected session: topic=$topic")
    }

    override suspend fun signAuthEntry(authEntryPreimageXdr: String, address: String): String {
        // Snapshot session state under the mutex to avoid a race with onSessionDelete
        val topic = connectMutex.withLock {
            if (connectedAddress == address) sessionTopic else null
        }
        if (topic == null) {
            throw WalletSigningException("No active wallet session for address $address.")
        }

        val sendDeferred = CompletableDeferred<Sign.Model.SentRequest>()

        val request = Sign.Params.Request(
            sessionTopic = topic,
            method = METHOD_SIGN_AUTH_ENTRY,
            params = buildSignParams(authEntryPreimageXdr),
            chainId = STELLAR_CHAIN_ID
        )

        SignClient.request(
            request = request,
            onSuccess = { sentRequest -> sendDeferred.complete(sentRequest) },
            onError = { error ->
                sendDeferred.completeExceptionally(
                    WalletSigningException(
                        "Failed to send sign request: ${error.throwable.message}",
                        error.throwable
                    )
                )
            }
        )

        val sentRequest = try {
            withTimeoutOrNull(REQUEST_SEND_TIMEOUT_MS) { sendDeferred.await() }
                ?: throw WalletSigningException("Timed out waiting for request send acknowledgement.")
        } catch (e: WalletSigningException) {
            throw e
        } catch (e: Exception) {
            throw WalletSigningException("Failed to send sign request: ${e.message}", e)
        }

        // Open Freighter so the user can see and approve the signing request.
        // The request was sent via the relay; Freighter needs to be in the foreground.
        openWalletForSigning()

        val responseDeferred = CompletableDeferred<Sign.Model.SessionRequestResponse>()
        synchronized(pendingRequestDeferreds) {
            pendingRequestDeferreds[sentRequest.requestId] = responseDeferred
        }

        return try {
            val response = withTimeoutOrNull(SIGNING_TIMEOUT_MS) { responseDeferred.await() }
                ?: throw WalletSigningException("Wallet did not respond to signing request within the timeout period.")

            parseSignAuthEntryResponse(response)
        } finally {
            synchronized(pendingRequestDeferreds) {
                pendingRequestDeferreds.remove(sentRequest.requestId)
            }
        }
    }

    override fun isConnected(address: String): Boolean =
        connectedAddress == address && sessionTopic != null

    override fun getConnectedAddress(): String? = connectedAddress

    override suspend fun getNetworkPassphrase(): String? = null

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    /**
     * Creates a WalletConnect pairing and proposes a session with the Stellar namespace.
     *
     * Must be called from a coroutine. [CoreClient.Pairing.create] is a blocking call and must
     * run on [Dispatchers.IO] (the caller uses `withContext(Dispatchers.IO)`).
     * [SignClient.connect] fires its callbacks asynchronously; this function bridges them via
     * [CompletableDeferred] so the caller can suspend until the URI is available.
     *
     * @return The WalletConnect pairing URI (`wc:...`) to pass to the wallet via deep link.
     * @throws WalletConnectionException if pairing creation or session proposal fails.
     */
    private suspend fun createPairingAndConnect(): String {
        val pairingErrorHolder = arrayOfNulls<Throwable>(1)

        // CoreClient.Pairing.create is a blocking call — must run on IO dispatcher.
        val pairing = withContext(Dispatchers.IO) {
            CoreClient.Pairing.create(
                onError = { error -> pairingErrorHolder[0] = error.throwable }
            )
        } ?: run {
            val cause = pairingErrorHolder[0]
            throw WalletConnectionException(
                "Failed to create pairing: ${cause?.message ?: "Reown CoreClient may not be initialized."}",
                cause
            )
        }

        val connectDeferred = CompletableDeferred<String>()

        val requiredNamespaces = mapOf(
            STELLAR_NAMESPACE to Sign.Model.Namespace.Proposal(
                chains = listOf(STELLAR_CHAIN_ID),
                methods = listOf(METHOD_SIGN_AUTH_ENTRY),
                events = emptyList()
            )
        )

        val connectParams = Sign.Params.Connect(
            namespaces = requiredNamespaces,
            pairing = pairing
        )

        // onSuccess delivers the pairing URI. It fires asynchronously inside a coroutine scope
        // launched by SignClient, so we suspend with connectDeferred.await() below.
        SignClient.connect(
            connect = connectParams,
            onSuccess = { uri -> connectDeferred.complete(uri) },
            onError = { error ->
                connectDeferred.completeExceptionally(
                    WalletConnectionException(
                        "Session proposal failed: ${error.throwable.message}",
                        error.throwable
                    )
                )
            }
        )

        return try {
            withTimeoutOrNull(REQUEST_SEND_TIMEOUT_MS) { connectDeferred.await() }
                ?: throw WalletConnectionException("Timed out waiting for session proposal URI.")
        } catch (e: WalletConnectionException) {
            throw e
        } catch (e: Exception) {
            throw WalletConnectionException("Session proposal failed: ${e.message}", e)
        }
    }

    /**
     * Launches the wallet app via a WalletConnect URI deep link.
     *
     * Freighter Mobile's deep link format (from the Reown wallet registry):
     *   freighterwallet://wc-redirect?uri={RFC3986 percent-encoded WC URI}
     */
    private fun launchWalletDeepLink(wcUri: String) {
        try {
            val encoded = Uri.encode(wcUri, "")
            val freighterUri = Uri.parse("freighterwallet://wc-redirect?uri=$encoded")
            val intent = Intent(Intent.ACTION_VIEW, freighterUri).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.w(TAG, "Could not launch Freighter Mobile: ${e.message}")
        }
    }

    /**
     * Opens Freighter to the foreground so the user can approve a signing request.
     * The request is already delivered via the relay; Freighter just needs to be visible.
     */
    private fun openWalletForSigning() {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("freighterwallet://")).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.w(TAG, "Could not bring Freighter to foreground: ${e.message}")
        }
    }

    /**
     * Extracts the first G-address from the session's approved accounts.
     *
     * Account entries use the format `stellar:testnet:<G-address>`.
     */
    private fun extractAddressFromSession(session: Sign.Model.ApprovedSession): String {
        val accounts = session.namespaces[STELLAR_NAMESPACE]?.accounts
            ?: throw WalletConnectionException("Session does not contain a '$STELLAR_NAMESPACE' namespace.")
        if (accounts.isEmpty()) {
            throw WalletConnectionException("Session was approved with no accounts in the '$STELLAR_NAMESPACE' namespace.")
        }
        // Format: stellar:testnet:GABC... — take everything after the second colon.
        val entry = accounts.first()
        val parts = entry.split(":")
        if (parts.size < 3) {
            throw WalletConnectionException("Unexpected account entry format in session: $entry")
        }
        return parts.drop(2).joinToString(":")
    }

    /**
     * Builds the JSON params for a `stellar_signAuthEntry` request.
     *
     * Freighter expects `requestParams` to be an object: `{"entryXdr":"<base64>"}`.
     * The Reown SDK wraps this as `request.params` in the JSON-RPC message.
     */
    private fun buildSignParams(authEntryPreimageXdr: String): String {
        val escaped = authEntryPreimageXdr
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
        return """{"entryXdr":"$escaped"}"""
    }

    /**
     * Parses the wallet's response to a `stellar_signAuthEntry` request.
     *
     * Freighter returns: { signedAuthEntry: "base64...", signerAddress: "G..." }
     *
     * In Reown 1.6.12, JsonRpcResult.result is Any? — it may be a deserialized Map,
     * a JSON string, or a plain string depending on the relay response format.
     */
    private fun parseSignAuthEntryResponse(response: Sign.Model.SessionRequestResponse): String {
        return when (val jsonRpcResponse = response.result) {
            is Sign.Model.JsonRpcResponse.JsonRpcResult -> {
                val result = jsonRpcResponse.result
                    ?: throw WalletSigningException("Wallet returned null result")

                Log.d(TAG, "Sign response: class=${result::class.java.name}")

                // Freighter returns: {"signedAuthEntry":"<base64>","signerAddress":"G..."}
                //
                // On iOS (Swift), the Reown SDK gives this as a dictionary (AnyCodable).
                // On Android (Kotlin), the Reown engine converts it to a String using
                // Map.toString(), producing: {signedAuthEntry=<base64>, signerAddress=G...}
                // This is NOT valid JSON — it uses = instead of : and has no quotes.
                // See: reown-kotlin EngineMapper.kt — `result = result.toString()`
                //
                // Parse the signedAuthEntry value from this format.
                val str = result.toString()
                val prefix = "signedAuthEntry="
                val startIndex = str.indexOf(prefix)
                if (startIndex < 0) {
                    throw WalletSigningException("Response missing signedAuthEntry: $str")
                }
                val valueStart = startIndex + prefix.length
                // The value ends at ", signerAddress=" or at "}"
                val nextField = str.indexOf(", ", valueStart)
                val valueEnd = if (nextField >= 0) nextField else str.indexOf("}", valueStart)
                if (valueEnd < 0) {
                    throw WalletSigningException("Could not parse signedAuthEntry value from: $str")
                }
                str.substring(valueStart, valueEnd)
            }
            is Sign.Model.JsonRpcResponse.JsonRpcError -> {
                throw WalletSigningException(
                    "Wallet rejected the signing request: ${jsonRpcResponse.message} (code ${jsonRpcResponse.code})"
                )
            }
        }
    }


    private companion object {
        private const val TAG = "ReownConnector"

        private const val STELLAR_NAMESPACE = "stellar"
        private const val STELLAR_CHAIN_ID = "stellar:testnet"
        private const val METHOD_SIGN_AUTH_ENTRY = "stellar_signAuthEntry"
        private const val WALLET_NAME = "Freighter"

        /** Maximum time to wait for the wallet to approve the session after the deep link opens. */
        private const val SESSION_TIMEOUT_MS = 120_000L

        /** Maximum time to wait for the request send acknowledgement from SignClient. */
        private const val REQUEST_SEND_TIMEOUT_MS = 30_000L

        /** Maximum time to wait for a signing response from the wallet. */
        private const val SIGNING_TIMEOUT_MS = 120_000L

        /** Maximum time to wait for the disconnect call to complete. */
        private const val DISCONNECT_TIMEOUT_MS = 10_000L
    }
}
