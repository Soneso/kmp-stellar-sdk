// Copyright 2026 Soneso. All rights reserved.
// Use of this source code is governed by a license that can be
// found in the LICENSE file.

package com.soneso.stellar.sdk.sep.sep08

import com.soneso.stellar.sdk.Network
import com.soneso.stellar.sdk.horizon.HorizonServer
import com.soneso.stellar.sdk.sep.sep01.StellarToml
import com.soneso.stellar.sdk.sep.sep08.exceptions.Sep08IncompleteInitDataException
import com.soneso.stellar.sdk.sep.sep08.exceptions.Sep08InvalidActionResponseException
import com.soneso.stellar.sdk.sep.sep08.exceptions.Sep08InvalidTransactionResponseException
import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * SEP-8 Regulated Assets service client.
 *
 * Provides support for regulated assets on the Stellar network. Regulated assets are assets
 * that require issuer approval before transactions involving them can be submitted. The issuer
 * publishes an approval server URL in their stellar.toml file, and all transactions involving
 * the regulated asset must be submitted to the approval server for authorization.
 *
 * This service handles:
 * - Discovering regulated assets and their approval servers from stellar.toml
 * - Checking whether an issuer account has authorization required and revocable flags set
 * - Submitting transactions to approval servers for regulatory approval
 * - Completing action flows when additional user interaction is required (e.g., KYC)
 *
 * Typical workflow:
 * 1. Create service instance via [fromDomain]
 * 2. Check available regulated assets via [regulatedAssets]
 * 3. Build a transaction involving the regulated asset
 * 4. Submit the transaction to the approval server via [postTransaction]
 * 5. Handle the response (approved, revised, pending, action required, or rejected)
 * 6. If action required, complete the action via [postAction] and resubmit
 *
 * Example - Discover regulated assets:
 * ```kotlin
 * val sep08 = Sep08Service.fromDomain("example.com")
 *
 * sep08.regulatedAssets.forEach { asset ->
 *     println("${asset.code}:${asset.issuer}")
 *     println("Approval server: ${asset.approvalServer}")
 *     asset.approvalCriteria?.let { println("Criteria: $it") }
 * }
 * ```
 *
 * Example - Submit transaction for approval:
 * ```kotlin
 * val sep08 = Sep08Service.fromDomain("example.com")
 * val asset = sep08.regulatedAssets.first()
 *
 * // Build and sign a payment transaction involving the regulated asset
 * val tx = TransactionBuilder(sourceAccount, network)
 *     .addOperation(PaymentOperation.Builder(destination, asset.toAsset(), "100").build())
 *     .setTimeout(180)
 *     .setBaseFee(100)
 *     .build()
 * tx.sign(senderKeyPair)
 *
 * // Submit to approval server
 * val response = sep08.postTransaction(tx.toEnvelopeXdrBase64(), asset.approvalServer)
 *
 * when (response) {
 *     is Sep08PostTransactionResponse.Success -> {
 *         // Submit response.tx to the Stellar network
 *         horizonServer.submitTransaction(response.tx)
 *     }
 *     is Sep08PostTransactionResponse.Revised -> {
 *         // Review and sign the revised transaction, then submit
 *         println("Revised: ${response.message}")
 *     }
 *     is Sep08PostTransactionResponse.Pending -> {
 *         // Wait and retry after response.timeout milliseconds
 *     }
 *     is Sep08PostTransactionResponse.ActionRequired -> {
 *         // Direct user to response.actionUrl
 *     }
 *     is Sep08PostTransactionResponse.Rejected -> {
 *         // Transaction rejected: response.error
 *     }
 * }
 * ```
 *
 * Example - Handle action required flow:
 * ```kotlin
 * val actionResponse = sep08.postAction(
 *     url = response.actionUrl,
 *     actionFields = mapOf("email_address" to "user@example.com")
 * )
 *
 * when (actionResponse) {
 *     is Sep08PostActionResponse.Done -> {
 *         // Resubmit the original transaction
 *         val retryResponse = sep08.postTransaction(tx, asset.approvalServer)
 *     }
 *     is Sep08PostActionResponse.NextUrl -> {
 *         // Direct user to actionResponse.nextUrl
 *     }
 * }
 * ```
 *
 * See also:
 * - [fromDomain] for automatic configuration from stellar.toml
 * - [Sep08PostTransactionResponse] for approval server response types
 * - [Sep08PostActionResponse] for action URL response types
 * - [RegulatedAsset] for regulated asset details
 * - [SEP-0008 Specification](https://github.com/stellar/stellar-protocol/blob/master/ecosystem/sep-0008.md)
 *
 * @property tomlData The parsed stellar.toml data for the issuer's domain
 * @property regulatedAssets List of regulated assets discovered from the stellar.toml
 * @property network The Stellar network this service operates on
 * @property horizonServer The Horizon server instance used for account queries
 */
class Sep08Service(
    val tomlData: StellarToml,
    val regulatedAssets: List<RegulatedAsset>,
    private val network: Network,
    private val horizonServer: HorizonServer,
    private val httpClient: HttpClient? = null,
    private val httpRequestHeaders: Map<String, String>? = null
) {
    /**
     * JSON configuration for parsing server responses.
     */
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    companion object {
        /**
         * Creates a Sep08Service by discovering regulated assets from stellar.toml.
         *
         * Fetches the stellar.toml file from the specified domain and extracts all regulated
         * asset entries. The network and Horizon URL are resolved from the provided parameters
         * or from the stellar.toml configuration.
         *
         * Resolution order for network:
         * 1. Explicit [network] parameter if provided
         * 2. NETWORK_PASSPHRASE from stellar.toml, mapped to known networks or created as custom
         *
         * Resolution order for Horizon URL:
         * 1. Explicit [horizonUrl] parameter if provided
         * 2. HORIZON_URL from stellar.toml
         * 3. Default Horizon URL for the resolved network (public, testnet, or futurenet)
         *
         * @param domain The domain name (without protocol). E.g., "example.com"
         * @param horizonUrl Optional Horizon server URL override
         * @param network Optional network override
         * @param httpClient Optional custom HTTP client for testing
         * @param httpRequestHeaders Optional custom headers for requests
         * @return Sep08Service configured with regulated assets from the domain's stellar.toml
         * @throws Sep08IncompleteInitDataException If the network passphrase or Horizon URL
         *   cannot be determined
         *
         * Example:
         * ```kotlin
         * // Auto-configure from stellar.toml
         * val sep08 = Sep08Service.fromDomain("example.com")
         *
         * // With explicit network and Horizon URL
         * val sep08Testnet = Sep08Service.fromDomain(
         *     domain = "testanchor.example.com",
         *     horizonUrl = "https://horizon-testnet.stellar.org",
         *     network = Network.TESTNET
         * )
         * ```
         */
        suspend fun fromDomain(
            domain: String,
            horizonUrl: String? = null,
            network: Network? = null,
            httpClient: HttpClient? = null,
            httpRequestHeaders: Map<String, String>? = null
        ): Sep08Service {
            val tomlData = StellarToml.fromDomain(
                domain = domain,
                httpClient = httpClient,
                httpRequestHeaders = httpRequestHeaders
            )

            // Resolve network
            val resolvedNetwork = if (network != null) {
                network
            } else {
                val passphrase = tomlData.generalInformation.networkPassphrase
                    ?: throw Sep08IncompleteInitDataException(
                        "Network passphrase not found in stellar.toml for domain: $domain"
                    )
                when (passphrase) {
                    Network.PUBLIC.networkPassphrase -> Network.PUBLIC
                    Network.TESTNET.networkPassphrase -> Network.TESTNET
                    Network.FUTURENET.networkPassphrase -> Network.FUTURENET
                    Network.STANDALONE.networkPassphrase -> Network.STANDALONE
                    Network.SANDBOX.networkPassphrase -> Network.SANDBOX
                    else -> Network(passphrase)
                }
            }

            // Resolve Horizon URL
            val resolvedHorizonUrl = if (horizonUrl != null) {
                horizonUrl
            } else if (tomlData.generalInformation.horizonUrl != null) {
                tomlData.generalInformation.horizonUrl
            } else {
                when (resolvedNetwork.networkPassphrase) {
                    Network.PUBLIC.networkPassphrase -> "https://horizon.stellar.org"
                    Network.TESTNET.networkPassphrase -> "https://horizon-testnet.stellar.org"
                    Network.FUTURENET.networkPassphrase -> "https://horizon-futurenet.stellar.org"
                    else -> throw Sep08IncompleteInitDataException(
                        "Horizon URL could not be determined for domain: $domain"
                    )
                }
            }

            val horizonServer = HorizonServer(resolvedHorizonUrl)

            // Extract regulated assets from currencies
            val regulatedAssets = tomlData.currencies
                ?.filter { currency ->
                    currency.regulated == true &&
                        currency.code != null &&
                        currency.issuer != null &&
                        currency.approvalServer != null
                }
                ?.map { currency ->
                    RegulatedAsset(
                        code = currency.code!!,
                        issuer = currency.issuer!!,
                        approvalServer = currency.approvalServer!!,
                        approvalCriteria = currency.approvalCriteria
                    )
                }
                ?: emptyList()

            return Sep08Service(
                tomlData = tomlData,
                regulatedAssets = regulatedAssets,
                network = resolvedNetwork,
                horizonServer = horizonServer,
                httpClient = httpClient,
                httpRequestHeaders = httpRequestHeaders
            )
        }
    }

    /**
     * Checks whether the issuer of a regulated asset has the `auth_required` and
     * `auth_revocable` flags set on their account.
     *
     * Both flags must be set for an asset to be properly configured as a regulated asset
     * per SEP-8. The `auth_required` flag ensures all trustlines must be approved, and the
     * `auth_revocable` flag allows the issuer to revoke authorization when needed.
     *
     * @param asset The regulated asset to check
     * @return `true` if both `auth_required` and `auth_revocable` are set, `false` otherwise
     * @throws com.soneso.stellar.sdk.horizon.exceptions.BadRequestException If the issuer
     *   account does not exist on the network
     * @throws com.soneso.stellar.sdk.horizon.exceptions.NetworkException On network errors
     *
     * Example:
     * ```kotlin
     * val asset = sep08.regulatedAssets.first()
     * if (sep08.authorizationRequired(asset)) {
     *     println("${asset.code} requires transaction approval")
     * } else {
     *     println("${asset.code} issuer flags not properly configured for SEP-8")
     * }
     * ```
     */
    suspend fun authorizationRequired(asset: RegulatedAsset): Boolean {
        val account = horizonServer.accounts().account(asset.issuer)
        return account.flags.authRequired && account.flags.authRevocable
    }

    /**
     * Submits a transaction to the approval server for regulatory review.
     *
     * The transaction XDR envelope is sent as a JSON payload to the specified approval server.
     * The server evaluates the transaction against its compliance rules and returns one of
     * five possible outcomes (success, revised, pending, action_required, or rejected).
     *
     * @param tx The base64-encoded transaction envelope XDR to submit for approval
     * @param approvalServer The URL of the SEP-8 approval server
     * @return [Sep08PostTransactionResponse] containing the approval server's decision
     * @throws Sep08InvalidTransactionResponseException If the server returns a malformed
     *   response or an unexpected HTTP status code
     *
     * Example:
     * ```kotlin
     * val asset = sep08.regulatedAssets.first()
     * val response = sep08.postTransaction(
     *     tx = transaction.toEnvelopeXdrBase64(),
     *     approvalServer = asset.approvalServer
     * )
     *
     * when (response) {
     *     is Sep08PostTransactionResponse.Success -> {
     *         // Approved - submit response.tx to the network
     *     }
     *     is Sep08PostTransactionResponse.Revised -> {
     *         // Revised - review response.tx and response.message
     *     }
     *     is Sep08PostTransactionResponse.Pending -> {
     *         // Pending - retry after response.timeout milliseconds
     *     }
     *     is Sep08PostTransactionResponse.ActionRequired -> {
     *         // Action needed - direct user to response.actionUrl
     *     }
     *     is Sep08PostTransactionResponse.Rejected -> {
     *         // Rejected - display response.error
     *     }
     * }
     * ```
     */
    suspend fun postTransaction(
        tx: String,
        approvalServer: String
    ): Sep08PostTransactionResponse = withHttpClient { client ->
        val headers = buildHeaders()

        val response = client.post(approvalServer) {
            headers.forEach { (key, value) -> header(key, value) }
            contentType(ContentType.Application.Json)
            setBody(mapOf("tx" to tx))
        }

        val body = response.bodyAsText()

        when (response.status.value) {
            200 -> {
                val jsonObject = json.decodeFromString<JsonObject>(body)
                Sep08PostTransactionResponse.fromJson(jsonObject)
            }
            400 -> {
                try {
                    val jsonObject = json.decodeFromString<JsonObject>(body)
                    val status = jsonObject["status"]?.jsonPrimitive?.contentOrNull
                    if (status == "rejected") {
                        Sep08PostTransactionResponse.fromJson(jsonObject)
                    } else {
                        throw Sep08InvalidTransactionResponseException(
                            "Unexpected HTTP status: ${response.status.value}, body: $body"
                        )
                    }
                } catch (e: Sep08InvalidTransactionResponseException) {
                    throw e
                } catch (e: Exception) {
                    throw Sep08InvalidTransactionResponseException(
                        "Unexpected HTTP status: ${response.status.value}, body: $body"
                    )
                }
            }
            else -> {
                throw Sep08InvalidTransactionResponseException(
                    "Unexpected HTTP status: ${response.status.value}, body: $body"
                )
            }
        }
    }

    /**
     * Submits action fields to an action URL provided by the approval server.
     *
     * When the approval server returns an [Sep08PostTransactionResponse.ActionRequired] response
     * with `actionMethod = "POST"`, the client must submit the required fields to the action URL.
     * This method handles that POST request and parses the response.
     *
     * The action URL may return either a "done" response (indicating the client should resubmit
     * the original transaction) or a "next_url" response (indicating additional steps are needed).
     *
     * @param url The action URL from the [Sep08PostTransactionResponse.ActionRequired] response
     * @param actionFields Map of field names to values as required by the action URL
     * @return [Sep08PostActionResponse] indicating whether the action is complete or more steps
     *   are needed
     * @throws Sep08InvalidActionResponseException If the action URL returns a malformed response
     *   or an unexpected HTTP status code
     *
     * Example:
     * ```kotlin
     * // After receiving an ActionRequired response
     * val actionResponse = sep08.postAction(
     *     url = actionRequired.actionUrl,
     *     actionFields = mapOf(
     *         "email_address" to "user@example.com",
     *         "mobile_number" to "+1234567890"
     *     )
     * )
     *
     * when (actionResponse) {
     *     is Sep08PostActionResponse.Done -> {
     *         // Resubmit the original transaction to the approval server
     *         val retryResponse = sep08.postTransaction(tx, approvalServer)
     *     }
     *     is Sep08PostActionResponse.NextUrl -> {
     *         // Direct user to actionResponse.nextUrl
     *         println("Next step: ${actionResponse.message}")
     *     }
     * }
     * ```
     */
    suspend fun postAction(
        url: String,
        actionFields: Map<String, String>
    ): Sep08PostActionResponse = withHttpClient { client ->
        val headers = buildHeaders()

        val response = client.post(url) {
            headers.forEach { (key, value) -> header(key, value) }
            contentType(ContentType.Application.Json)
            setBody(actionFields)
        }

        val body = response.bodyAsText()

        when (response.status.value) {
            200 -> {
                val jsonObject = json.decodeFromString<JsonObject>(body)
                Sep08PostActionResponse.fromJson(jsonObject)
            }
            else -> {
                throw Sep08InvalidActionResponseException(
                    "Unexpected HTTP status: ${response.status.value}, body: $body"
                )
            }
        }
    }

    // ========== Private HTTP Utilities ==========

    /**
     * Executes a block with an HTTP client, managing lifecycle properly.
     *
     * If a custom [httpClient] was provided at construction time, it is reused and not closed.
     * Otherwise, a temporary client is created with standard timeout settings and closed
     * after the block completes.
     */
    private suspend fun <T> withHttpClient(block: suspend (HttpClient) -> T): T {
        val client = httpClient ?: HttpClient {
            install(ContentNegotiation) {
                json(json)
            }
            install(HttpTimeout) {
                connectTimeoutMillis = 10_000
                requestTimeoutMillis = 30_000
            }
        }

        return try {
            block(client)
        } finally {
            if (httpClient == null) {
                client.close()
            }
        }
    }

    /**
     * Builds request headers from custom headers.
     *
     * SEP-8 does not use JWT authentication (unlike SEP-6/SEP-24), so this method
     * only merges the custom headers provided at construction time.
     */
    private fun buildHeaders(): Map<String, String> {
        val headers = mutableMapOf<String, String>()
        httpRequestHeaders?.let { headers.putAll(it) }
        return headers
    }
}
