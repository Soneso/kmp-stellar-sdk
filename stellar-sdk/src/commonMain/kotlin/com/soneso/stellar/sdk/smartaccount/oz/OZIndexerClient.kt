//
//  OZIndexerClient.kt
//  Stellar SDK Kotlin Multiplatform
//
//  Copyright (c) 2026 Soneso. All rights reserved.
//

package com.soneso.stellar.sdk.smartaccount.oz
import com.soneso.stellar.sdk.smartaccount.core.*

import com.soneso.stellar.sdk.Util
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

// MARK: - Response Models

/**
 * Response from looking up a credential ID in the indexer.
 *
 * Contains the credential ID, all contracts where this credential is registered as a signer,
 * and the total count of contracts.
 */
@Serializable
data class CredentialLookupResponse(
    @SerialName("credential_id")
    val credentialId: String,
    val contracts: List<IndexedContractSummary>,
    val count: Int
)

/**
 * Response from looking up a signer address in the indexer.
 *
 * Contains the signer address, all contracts where this address is registered as a signer,
 * and the total count of contracts.
 */
@Serializable
data class AddressLookupResponse(
    @SerialName("signer_address")
    val signerAddress: String,
    val contracts: List<IndexedContractSummary>,
    val count: Int
)

/**
 * Response containing full details of a smart account contract.
 *
 * Includes the contract ID, summary information, and all context rules with their signers and policies.
 */
@Serializable
data class ContractDetailsResponse(
    @SerialName("contract_id")
    val contractId: String,
    val summary: IndexedContractSummary,
    @SerialName("context_rules")
    val contextRules: List<IndexedContextRule>
)

/**
 * Summary information about a smart account contract.
 *
 * Contains aggregate counts and metadata about signers, policies, and context rules.
 */
@Serializable
data class IndexedContractSummary(
    @SerialName("contract_id")
    val contractId: String,
    @SerialName("context_rule_count")
    val contextRuleCount: Int,
    @SerialName("external_signer_count")
    val externalSignerCount: Int,
    @SerialName("delegated_signer_count")
    val delegatedSignerCount: Int,
    @SerialName("native_signer_count")
    val nativeSignerCount: Int,
    @SerialName("first_seen_ledger")
    val firstSeenLedger: Int,
    @SerialName("last_seen_ledger")
    val lastSeenLedger: Int,
    @SerialName("context_rule_ids")
    val contextRuleIds: List<Int>
)

/**
 * A context rule within a smart account contract.
 *
 * Defines authorization requirements (signers and policies) for a specific context
 * (e.g., "Default" or "Call Token Contract X").
 */
@Serializable
data class IndexedContextRule(
    @SerialName("context_rule_id")
    val contextRuleId: Int,
    val signers: List<IndexedSigner>,
    val policies: List<IndexedPolicy>
)

/**
 * A signer within a context rule.
 *
 * Can be either an external signer (WebAuthn/passkey with credential ID) or a delegated
 * signer (Stellar address using built-in signature verification).
 */
@Serializable
data class IndexedSigner(
    @SerialName("signer_type")
    val signerType: String,  // "External" or "Delegated"
    @SerialName("signer_address")
    val signerAddress: String? = null,
    @SerialName("credential_id")
    val credentialId: String? = null
)

/**
 * A policy attached to a context rule.
 *
 * Policies enforce additional authorization requirements beyond signature verification
 * (e.g., spending limits, time locks, threshold requirements).
 */
@Serializable
data class IndexedPolicy(
    @SerialName("policy_address")
    val policyAddress: String,
    @SerialName("install_params")
    val installParams: JsonElement? = null  // Can be any JSON structure
)

/**
 * Response from the indexer stats endpoint.
 *
 * Contains aggregate statistics about the indexer state including total contracts,
 * credentials, and event type breakdowns.
 */
@Serializable
data class IndexerStatsResponse(
    val stats: IndexerStats
)

/**
 * Statistics about the indexer state.
 */
@Serializable
data class IndexerStats(
    @SerialName("total_events")
    val totalEvents: Long,
    @SerialName("unique_contracts")
    val uniqueContracts: Long,
    @SerialName("unique_credentials")
    val uniqueCredentials: Long,
    @SerialName("first_ledger")
    val firstLedger: Long,
    @SerialName("last_ledger")
    val lastLedger: Long,
    @SerialName("eventTypes")
    val eventTypes: List<EventTypeCount>
)

/**
 * Count of events by type.
 */
@Serializable
data class EventTypeCount(
    @SerialName("event_type")
    val eventType: String,
    val count: Long
)

/**
 * Response from the health check endpoint.
 */
@Serializable
data class HealthCheckResponse(
    val status: String
)

// MARK: - Indexer Client

/**
 * Client for interacting with the OpenZeppelin Smart Account indexer service.
 *
 * The indexer maps WebAuthn credential IDs and signer addresses to deployed smart account
 * contract addresses, enabling "Connect Wallet" discovery and contract exploration.
 *
 * Example usage:
 * ```kotlin
 * val client = OZIndexerClient(indexerUrl = "https://indexer.example.com")
 *
 * // Look up contracts by credential ID
 * val credentialResponse = client.lookupByCredentialId("abc123...")
 * println("Found ${credentialResponse.count} contracts")
 *
 * // Look up contracts by signer address
 * val addressResponse = client.lookupByAddress("GABC123...")
 * println("Signer is registered in ${addressResponse.count} contracts")
 *
 * // Get full contract details
 * val contractDetails = client.getContract("CABC123...")
 * println("Contract has ${contractDetails.contextRules.size} context rules")
 * ```
 */
class OZIndexerClient(
    private val indexerUrl: String,
    timeoutMs: Long = OZConstants.DEFAULT_INDEXER_TIMEOUT_MS
) {
    init {
        if (indexerUrl.isBlank()) {
            throw ConfigurationException.invalidConfig("Indexer URL is required")
        }
        if (!indexerUrl.startsWith("https://") && !indexerUrl.startsWith("http://localhost")) {
            throw ConfigurationException.invalidConfig(
                "Indexer URL must use HTTPS (or http://localhost for development): $indexerUrl"
            )
        }
    }

    private val httpClient: HttpClient = createHttpClient(timeoutMs)

    companion object {
        /**
         * Default indexer URLs by network passphrase.
         *
         * Maps standard Stellar network passphrases to their corresponding indexer endpoints.
         * These URLs are maintained by the OpenZeppelin team and should be used as defaults
         * when no custom indexer URL is provided.
         */
        val DEFAULT_INDEXER_URLS: Map<String, String> = mapOf(
            "Test SDF Network ; September 2015" to "https://smart-account-indexer.sdf-ecosystem.workers.dev"
            // Mainnet URL will be added when available
        )

        /**
         * Returns the default indexer URL for a given network passphrase.
         *
         * @param networkPassphrase The Stellar network passphrase
         * @return The default indexer URL, or null if no default is configured for this network
         */
        fun getDefaultUrl(networkPassphrase: String): String? = DEFAULT_INDEXER_URLS[networkPassphrase]

        /**
         * Creates an OZIndexerClient for a specific network using the default indexer URL.
         *
         * Uses the default indexer endpoint for known networks (testnet, mainnet).
         * Returns null if no default URL is configured for the network.
         *
         * @param networkPassphrase The Stellar network passphrase
         * @param timeoutMs Optional request timeout in milliseconds
         * @return An OZIndexerClient configured for the network, or null if no default URL exists
         *
         * Example:
         * ```kotlin
         * val client = OZIndexerClient.forNetwork("Test SDF Network ; September 2015")
         * if (client != null) {
         *     val contracts = client.lookupByCredentialId(credentialId)
         * }
         * ```
         */
        fun forNetwork(
            networkPassphrase: String,
            timeoutMs: Long = OZConstants.DEFAULT_INDEXER_TIMEOUT_MS
        ): OZIndexerClient? {
            val url = getDefaultUrl(networkPassphrase) ?: return null
            return OZIndexerClient(url, timeoutMs)
        }
    }

    /**
     * Creates an HTTP client configured for indexer requests.
     *
     * @param timeoutMs Request timeout in milliseconds
     * @return Configured HttpClient instance
     */
    private fun createHttpClient(timeoutMs: Long): HttpClient = HttpClient {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
                prettyPrint = false
                encodeDefaults = false
            })
        }
        install(HttpTimeout) {
            requestTimeoutMillis = timeoutMs
            connectTimeoutMillis = minOf(timeoutMs, 10_000)
        }
    }

    /**
     * Looks up smart account contracts by WebAuthn credential ID.
     *
     * Queries the indexer for all smart account contracts where the specified credential ID
     * is registered as an external signer in any context rule.
     *
     * @param credentialId The credential ID to look up (base64url-encoded, no padding).
     *                     Will be converted to hex for the API call.
     * @return A response containing the credential ID, matching contracts, and count.
     * @throws ValidationException.InvalidInput if the request fails or returns invalid data.
     */
    suspend fun lookupByCredentialId(credentialId: String): CredentialLookupResponse {
        val hexCredentialId = base64UrlToHex(credentialId)
        val url = "${indexerUrl.trimEnd('/')}/api/lookup/$hexCredentialId"
        return performRequest(url)
    }

    /**
     * Looks up smart account contracts by signer address.
     *
     * Queries the indexer for all smart account contracts where the specified address
     * is registered as a delegated signer in any context rule.
     *
     * @param address The signer address to look up (G... or C... format).
     * @return A response containing the signer address, matching contracts, and count.
     * @throws ValidationException.InvalidAddress if the address format is invalid.
     * @throws ValidationException.InvalidInput if the request fails or returns invalid data.
     */
    suspend fun lookupByAddress(address: String): AddressLookupResponse {
        if (!address.startsWith("G") && !address.startsWith("C")) {
            throw ValidationException.invalidAddress("Signer address must start with 'G' or 'C', got: $address")
        }

        val url = "${indexerUrl.trimEnd('/')}/api/lookup/address/$address"
        return performRequest(url)
    }

    /**
     * Gets detailed information about a smart account contract.
     *
     * Retrieves full contract details including summary information and all context rules
     * with their signers and policies.
     *
     * @param contractId The contract ID to query (C... format).
     * @return A response containing the contract ID, summary, and all context rules.
     * @throws ValidationException.InvalidAddress if the contract ID format is invalid.
     * @throws ValidationException.InvalidInput if the request fails or returns invalid data.
     */
    suspend fun getContract(contractId: String): ContractDetailsResponse {
        if (!contractId.startsWith("C")) {
            throw ValidationException.invalidAddress("Contract ID must start with 'C', got: $contractId")
        }

        val url = "${indexerUrl.trimEnd('/')}/api/contract/$contractId"
        return performRequest(url)
    }

    /**
     * Gets statistics from the indexer.
     *
     * Retrieves aggregate statistics about the indexer state including:
     * - Total number of events processed
     * - Number of unique contracts indexed
     * - Number of unique credentials indexed
     * - Ledger range (first and last seen)
     * - Breakdown of events by type
     *
     * Useful for debugging and monitoring indexer health.
     *
     * @return Indexer statistics
     * @throws ValidationException.InvalidInput if the request fails or returns invalid data.
     */
    suspend fun getStats(): IndexerStatsResponse {
        val url = "${indexerUrl.trimEnd('/')}/api/stats"
        return performRequest(url)
    }

    /**
     * Checks if the indexer service is healthy and reachable.
     *
     * Performs a lightweight health check by calling the root endpoint and verifying
     * the service returns a successful status.
     *
     * This method does not throw exceptions - it returns false for any error condition
     * (network failure, timeout, unhealthy response).
     *
     * Note: Uses Throwable instead of Exception because on Kotlin/JS, Ktor network
     * failures throw JavaScript Error objects that map to Throwable, not Exception.
     *
     * @return true if the indexer is healthy and reachable, false otherwise
     */
    suspend fun isHealthy(): Boolean {
        return try {
            val response: HttpResponse = httpClient.get("${indexerUrl.trimEnd('/')}/") {
                headers {
                    append(HttpHeaders.Accept, ContentType.Application.Json.toString())
                }
            }

            if (!response.status.isSuccess()) {
                return false
            }

            val healthCheck: HealthCheckResponse = response.body()
            healthCheck.status == "ok"
        } catch (_: Throwable) {
            // Catches all errors including JS Error ("Fail to fetch") which is
            // a Throwable but not an Exception on Kotlin/JS.
            false
        }
    }

    /**
     * Closes the HTTP client and releases resources.
     *
     * Should be called when the client is no longer needed to free up system resources.
     * After calling close, this client instance cannot be used again.
     */
    fun close() {
        httpClient.close()
    }

    // MARK: - Private Helper Methods

    /**
     * Performs an HTTP GET request and decodes the JSON response.
     *
     * @param url The full URL to request
     * @return The decoded response object
     * @throws ValidationException.InvalidInput for network, timeout, or decoding errors
     */
    private suspend inline fun <reified T> performRequest(url: String): T {
        try {
            val response: HttpResponse = httpClient.get(url) {
                headers {
                    append(HttpHeaders.Accept, ContentType.Application.Json.toString())
                }
            }

            // Handle non-200 status codes
            if (!response.status.isSuccess()) {
                val errorBody = try {
                    response.bodyAsText()
                } catch (e: Exception) {
                    "(unable to decode response body)"
                }
                val truncatedBody = if (errorBody.length > 200) errorBody.take(200) + "..." else errorBody
                throw ValidationException.InvalidInput(
                    "Indexer returned HTTP ${response.status.value}: $truncatedBody"
                )
            }

            return response.body()
        } catch (e: HttpRequestTimeoutException) {
            throw ValidationException.InvalidInput(
                "Indexer request timed out: $url",
                e
            )
        } catch (e: ValidationException) {
            // Re-throw validation exceptions as-is
            throw e
        } catch (e: Exception) {
            val errorMessage = e.message ?: e.toString()
            throw ValidationException.InvalidInput(
                "Indexer request failed: $errorMessage",
                e
            )
        }
    }

    /**
     * Converts a base64url-encoded string to hex encoding.
     *
     * The SDK stores credential IDs in base64url format (RFC 4648, no padding).
     * The indexer API expects hex encoding (no 0x prefix).
     *
     * @param base64url The base64url-encoded string (no padding)
     * @return The hex-encoded string (lowercase, no 0x prefix)
     * @throws ValidationException.InvalidInput if the input is not valid base64url
     */
    private fun base64UrlToHex(base64url: String): String {
        val bytes = try {
            Util.base64urlDecode(base64url)
        } catch (e: IllegalArgumentException) {
            throw ValidationException.InvalidInput(
                "Failed to decode base64url credential ID: $base64url"
            )
        }

        return bytes.joinToString("") { byte ->
            (byte.toInt() and 0xFF).toString(16).padStart(2, '0')
        }
    }
}
