// Copyright 2026 Soneso. All rights reserved.
// Use of this source code is governed by a license that can be
// found in the LICENSE file.

package com.soneso.stellar.sdk.sep.sep02

import com.soneso.stellar.sdk.sep.sep01.StellarToml
import com.soneso.stellar.sdk.sep.sep02.exceptions.Sep02FederationNotFoundException
import com.soneso.stellar.sdk.sep.sep02.exceptions.Sep02InvalidAddressException
import com.soneso.stellar.sdk.sep.sep02.exceptions.Sep02InvalidResponseException
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
 * SEP-2 Federation Protocol service client.
 *
 * Provides support for resolving human-readable Stellar addresses to account IDs and vice versa.
 * The federation protocol allows users to share addresses like `bob*stellar.org` instead of
 * raw public keys, making the Stellar network more user-friendly and reducing payment errors.
 *
 * This service handles:
 * - Resolving Stellar addresses (name*domain) to account IDs and memo information
 * - Reverse lookup of account IDs to Stellar addresses
 * - Transaction ID lookups
 * - Forward queries for payment routing
 * - Discovering federation servers from stellar.toml files
 *
 * Typical workflow:
 * 1. Create service instance via [fromDomain] or static [resolveStellarAddress]
 * 2. Resolve addresses to get account IDs and required memos
 * 3. Use resolved information to build and send payments
 *
 * Example - Resolve a Stellar address:
 * ```kotlin
 * val address = "bob*stellar.org"
 * val response = FederationService.resolveStellarAddress(address)
 *
 * println("Account ID: ${response.accountId}")
 * if (response.memoType != null && response.memo != null) {
 *     println("Memo required: ${response.memoType} = ${response.memo}")
 * }
 *
 * // Build payment with resolved information
 * val payment = PaymentOperation(
 *     destination = response.accountId,
 *     asset = AssetTypeNative(),
 *     amount = "10"
 * )
 * ```
 *
 * Example - Reverse lookup:
 * ```kotlin
 * val service = FederationService.fromDomain("stellar.org")
 * val response = service.resolveAccountId("GCIBUCGPOHWMMMFPFTDWBSVHQRT4DIBJ7AD6BZJYDITBK2LCVBYW7HUQ")
 *
 * println("Stellar address: ${response.stellarAddress}")
 * ```
 *
 * Example - Forward payment:
 * ```kotlin
 * val service = FederationService.fromDomain("stellar.org")
 * val response = service.resolveForward(
 *     mapOf(
 *         "forward_type" to "bank_account",
 *         "swift" to "BOPBPHMM",
 *         "acct" to "2382376"
 *     )
 * )
 *
 * // Use response.accountId for payment destination
 * ```
 *
 * See also:
 * - [fromDomain] for automatic configuration from stellar.toml
 * - [resolveStellarAddress] for one-step address resolution
 * - [parseAddress] for validating address format
 * - [FederationResponse] for response details
 * - [SEP-0002 Specification](https://github.com/stellar/stellar-protocol/blob/master/ecosystem/sep-0002.md)
 *
 * @property federationServerUrl The URL of the federation server
 */
class FederationService(
    val federationServerUrl: String,
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
         * Creates a FederationService by discovering the federation server from stellar.toml.
         *
         * Fetches the stellar.toml file from the specified domain and extracts the
         * `FEDERATION_SERVER` URL. This is the standard way to discover federation servers
         * for a given domain.
         *
         * @param domain The domain name (without protocol). E.g., "stellar.org"
         * @param httpClient Optional custom HTTP client for testing
         * @param httpRequestHeaders Optional custom headers for requests
         * @return FederationService configured with the federation server URL from stellar.toml
         * @throws Sep02FederationNotFoundException If the stellar.toml does not contain a
         *   `FEDERATION_SERVER` field
         *
         * Example:
         * ```kotlin
         * // Auto-configure from stellar.toml
         * val service = FederationService.fromDomain("stellar.org")
         *
         * // Resolve addresses using this domain's federation server
         * val response = service.resolveStellarAddress("bob*stellar.org")
         * ```
         */
        suspend fun fromDomain(
            domain: String,
            httpClient: HttpClient? = null,
            httpRequestHeaders: Map<String, String>? = null
        ): FederationService {
            val toml = StellarToml.fromDomain(
                domain = domain,
                httpClient = httpClient,
                httpRequestHeaders = httpRequestHeaders
            )

            val federationServerUrl = toml.generalInformation.federationServer
                ?: throw Sep02FederationNotFoundException(
                    "No federation server found in stellar.toml for domain: $domain"
                )

            return FederationService(
                federationServerUrl = federationServerUrl,
                httpClient = httpClient,
                httpRequestHeaders = httpRequestHeaders
            )
        }

        /**
         * Resolves a Stellar address in a single call by auto-discovering the federation server.
         *
         * This is a convenience method that combines [parseAddress], [fromDomain], and
         * [resolveStellarAddress] into a single operation. It automatically extracts the
         * domain from the address, discovers the federation server from stellar.toml,
         * and resolves the address.
         *
         * @param address The Stellar address to resolve (e.g., "bob*stellar.org")
         * @param httpClient Optional custom HTTP client for testing
         * @param httpRequestHeaders Optional custom headers for requests
         * @return The federation response containing account ID and optional memo information
         * @throws Sep02InvalidAddressException If the address format is invalid
         * @throws Sep02FederationNotFoundException If the domain's stellar.toml does not
         *   contain a federation server URL
         * @throws Sep02InvalidResponseException If the federation server returns an invalid
         *   response or the address cannot be resolved
         *
         * Example:
         * ```kotlin
         * // Resolve address in one step
         * val response = FederationService.resolveStellarAddress("bob*stellar.org")
         *
         * println("Account: ${response.accountId}")
         * if (response.memo != null) {
         *     println("Required memo (${response.memoType}): ${response.memo}")
         * }
         * ```
         */
        suspend fun resolveStellarAddress(
            address: String,
            httpClient: HttpClient? = null,
            httpRequestHeaders: Map<String, String>? = null
        ): FederationResponse {
            val (_, domain) = parseAddress(address)
            val service = fromDomain(domain, httpClient, httpRequestHeaders)
            return service.resolveStellarAddress(address)
        }

        /**
         * Parses and validates a Stellar address into username and domain components.
         *
         * A valid Stellar address must follow the format `username*domain` where:
         * - Username is the identifier portion (before the `*`)
         * - Domain is the federation server's domain (after the `*`)
         * - Neither component can be empty
         * - Only one `*` separator is allowed
         *
         * @param address The Stellar address to parse (e.g., "bob*stellar.org")
         * @return Pair of (username, domain)
         * @throws Sep02InvalidAddressException If the address format is invalid
         *
         * Example:
         * ```kotlin
         * val (username, domain) = FederationService.parseAddress("bob*stellar.org")
         * println("Username: $username")  // "bob"
         * println("Domain: $domain")      // "stellar.org"
         *
         * // Validation errors
         * FederationService.parseAddress("invalid")        // No * separator
         * FederationService.parseAddress("*stellar.org")   // Empty username
         * FederationService.parseAddress("bob*")           // Empty domain
         * FederationService.parseAddress("bob*multi*sep")  // Multiple separators
         * ```
         */
        fun parseAddress(address: String): Pair<String, String> {
            val parts = address.split("*")

            when {
                parts.size < 2 -> throw Sep02InvalidAddressException(
                    "Invalid Stellar address: '$address'. Expected format: username*domain"
                )
                parts.size > 2 -> throw Sep02InvalidAddressException(
                    "Invalid Stellar address: '$address'. Address contains multiple '*' characters"
                )
                parts[0].isEmpty() -> throw Sep02InvalidAddressException(
                    "Invalid Stellar address: '$address'. Username part is empty"
                )
                parts[1].isEmpty() -> throw Sep02InvalidAddressException(
                    "Invalid Stellar address: '$address'. Domain part is empty"
                )
            }

            return Pair(parts[0], parts[1])
        }
    }

    /**
     * Resolves a Stellar address to account information.
     *
     * Queries the federation server to resolve a human-readable Stellar address
     * (e.g., "bob*stellar.org") to the corresponding account ID and optional memo
     * information required for payments.
     *
     * @param address The Stellar address to resolve (must be in format "username*domain")
     * @return The federation response containing account ID and optional memo information
     * @throws Sep02InvalidAddressException If the address format is invalid
     * @throws Sep02InvalidResponseException If the federation server returns an invalid
     *   response or the address cannot be resolved
     *
     * Example:
     * ```kotlin
     * val service = FederationService.fromDomain("stellar.org")
     * val response = service.resolveStellarAddress("bob*stellar.org")
     *
     * println("Send payment to: ${response.accountId}")
     * if (response.memoType == "id" && response.memo != null) {
     *     // Attach ID memo when building payment
     *     val memo = MemoId(response.memo.toLong())
     * }
     * ```
     */
    suspend fun resolveStellarAddress(address: String): FederationResponse {
        parseAddress(address) // Validate format
        return executeQuery(mapOf("q" to address, "type" to "name"))
    }

    /**
     * Performs a reverse lookup to find the Stellar address for an account ID.
     *
     * Queries the federation server to find the human-readable Stellar address
     * associated with a given account ID (public key). Not all accounts have
     * federation addresses configured.
     *
     * @param accountId The Stellar account ID (G... address) to look up
     * @return The federation response containing the Stellar address if found
     * @throws Sep02InvalidResponseException If the federation server returns an invalid
     *   response or the account ID has no associated Stellar address
     *
     * Example:
     * ```kotlin
     * val service = FederationService.fromDomain("stellar.org")
     * val response = service.resolveAccountId("GCIBUCGPOHWMMMFPFTDWBSVHQRT4DIBJ7AD6BZJYDITBK2LCVBYW7HUQ")
     *
     * println("Stellar address: ${response.stellarAddress ?: "not found"}")
     * ```
     */
    suspend fun resolveAccountId(accountId: String): FederationResponse {
        return executeQuery(mapOf("q" to accountId, "type" to "id"))
    }

    /**
     * Looks up federation information for a transaction ID.
     *
     * Queries the federation server to find information associated with a
     * specific Stellar transaction. This is used to discover the sender or
     * recipient information for a completed transaction.
     *
     * @param txId The Stellar transaction hash to look up
     * @return The federation response containing account information
     * @throws Sep02InvalidResponseException If the federation server returns an invalid
     *   response or the transaction ID is not found
     *
     * Example:
     * ```kotlin
     * val service = FederationService.fromDomain("stellar.org")
     * val response = service.resolveTransactionId(
     *     "3389e9f0f1a65f19736cacf544c2e825313e8447f569233bb8db39aa607c8889"
     * )
     *
     * println("Transaction account: ${response.accountId}")
     * ```
     */
    suspend fun resolveTransactionId(txId: String): FederationResponse {
        return executeQuery(mapOf("q" to txId, "type" to "txid"))
    }

    /**
     * Performs a forward query for payment routing.
     *
     * Forward queries allow routing payments through the federation server using
     * custom parameters. The exact parameters depend on the federation server's
     * implementation (e.g., bank account details, routing numbers, etc.).
     *
     * Note: This method does NOT include the `q` parameter - only `type=forward`
     * and the provided forward parameters.
     *
     * @param forwardParams Map of forward-specific parameters required by the
     *   federation server (e.g., "forward_type", "swift", "acct")
     * @return The federation response containing the routing destination account
     * @throws Sep02InvalidResponseException If the federation server returns an invalid
     *   response or the forward query cannot be resolved
     *
     * Example:
     * ```kotlin
     * val service = FederationService.fromDomain("stellar.org")
     * val response = service.resolveForward(
     *     mapOf(
     *         "forward_type" to "bank_account",
     *         "swift" to "BOPBPHMM",
     *         "acct" to "2382376"
     *     )
     * )
     *
     * println("Route payment to: ${response.accountId}")
     * if (response.memo != null) {
     *     println("With memo (${response.memoType}): ${response.memo}")
     * }
     * ```
     */
    suspend fun resolveForward(forwardParams: Map<String, String>): FederationResponse {
        return executeQuery(mapOf("type" to "forward") + forwardParams)
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
     * SEP-2 does not use authentication, so this method only merges the custom headers
     * provided at construction time.
     */
    private fun buildHeaders(): Map<String, String> {
        val headers = mutableMapOf<String, String>()
        httpRequestHeaders?.let { headers.putAll(it) }
        return headers
    }

    /**
     * Executes a federation query and parses the response.
     *
     * Sends a GET request to the federation server with the provided query parameters
     * and parses the response into a [FederationResponse]. Handles both success
     * (2xx status) and error responses.
     */
    private suspend fun executeQuery(params: Map<String, String>): FederationResponse = withHttpClient { client ->
        val headers = buildHeaders()

        val response = client.get(federationServerUrl) {
            headers.forEach { (key, value) -> header(key, value) }
            params.forEach { (key, value) -> parameter(key, value) }
        }

        val body = response.bodyAsText()

        if (response.status.value in 200..299) {
            try {
                val jsonObject = json.decodeFromString<JsonObject>(body)
                FederationResponse.fromJson(jsonObject)
            } catch (e: Sep02InvalidResponseException) {
                throw e
            } catch (e: Exception) {
                throw Sep02InvalidResponseException(
                    "Failed to parse federation response: ${e.message}"
                )
            }
        } else {
            // Non-2xx: try to parse error field from JSON body
            val errorMessage = try {
                val jsonObject = json.decodeFromString<JsonObject>(body)
                jsonObject["error"]?.jsonPrimitive?.contentOrNull
            } catch (_: Exception) {
                null
            }

            val message = if (errorMessage != null) {
                "Federation request failed (HTTP ${response.status.value}): $errorMessage"
            } else {
                "Federation request failed (HTTP ${response.status.value}): ${response.status.description}"
            }
            throw Sep02InvalidResponseException(message)
        }
    }
}
