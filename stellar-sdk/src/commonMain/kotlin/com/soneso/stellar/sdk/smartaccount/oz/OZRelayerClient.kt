//
//  OZRelayerClient.kt
//  Stellar SDK Kotlin Multiplatform
//
//  Created by Claude on 27.01.26.
//  Copyright © 2026 Soneso. All rights reserved.
//

package com.soneso.stellar.sdk.smartaccount.oz
import com.soneso.stellar.sdk.smartaccount.core.*

import com.soneso.stellar.sdk.xdr.HostFunctionXdr
import com.soneso.stellar.sdk.xdr.SorobanAuthorizationEntryXdr
import com.soneso.stellar.sdk.xdr.TransactionEnvelopeXdr
import com.soneso.stellar.sdk.xdr.toXdrBase64
import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Known error codes returned by the relayer service.
 *
 * These codes identify specific failure conditions and can be used for
 * programmatic error handling.
 */
object RelayerErrorCodes {
    const val INVALID_PARAMS = "INVALID_PARAMS"
    const val INVALID_XDR = "INVALID_XDR"
    const val POOL_CAPACITY = "POOL_CAPACITY"
    const val SIMULATION_FAILED = "SIMULATION_FAILED"
    const val ONCHAIN_FAILED = "ONCHAIN_FAILED"
    const val INVALID_TIME_BOUNDS = "INVALID_TIME_BOUNDS"
    const val FEE_LIMIT_EXCEEDED = "FEE_LIMIT_EXCEEDED"
    const val UNAUTHORIZED = "UNAUTHORIZED"
    const val TIMEOUT = "TIMEOUT"
}

/**
 * Response from the relayer service.
 *
 * The relayer wraps transactions with fee bumps and submits them to Stellar,
 * enabling gasless onboarding for users with empty wallets.
 *
 * @property success Indicates whether the transaction was successfully submitted
 * @property transactionId Transaction ID assigned by the relayer (if successful)
 * @property hash The transaction hash if submission succeeded
 * @property status The transaction status (e.g., "PENDING", "SUCCESS", "ERROR")
 * @property error Error message if the request failed
 * @property errorCode Error code if the request failed (see [RelayerErrorCodes])
 * @property details Additional error details from the relayer (if available)
 */
data class RelayerResponse(
    val success: Boolean,
    val transactionId: String? = null,
    val hash: String? = null,
    val status: String? = null,
    val error: String? = null,
    val errorCode: String? = null,
    val details: JsonElement? = null
)

/**
 * Client for submitting transactions to an OpenZeppelin Smart Account relayer.
 *
 * The relayer provides fee sponsoring by wrapping user transactions with fee bumps,
 * enabling gasless onboarding and transactions for users with empty wallets.
 *
 * Two submission modes are supported:
 *
 * 1. **Host Function + Auth Entries**: Submit transaction components separately
 *    for the relayer to construct and wrap the full transaction.
 *
 * 2. **Signed Transaction XDR**: Submit a complete, signed transaction envelope
 *    for the relayer to wrap and submit.
 *
 * Example usage:
 * ```kotlin
 * val relayer = OZRelayerClient(relayerUrl = "https://relayer.example.com")
 *
 * // Mode 1: Submit host function and auth entries
 * val hostFunction = // ... HostFunctionXdr
 * val authEntries = // ... List<SorobanAuthorizationEntryXdr>
 * val response = relayer.send(hostFunction, authEntries)
 *
 * // Mode 2: Submit signed transaction XDR
 * val txEnvelope = // ... TransactionEnvelopeXdr
 * val response = relayer.sendXdr(txEnvelope)
 *
 * if (response.success) {
 *     println("Transaction hash: ${response.hash ?: "unknown"}")
 * } else {
 *     println("Error: ${response.error ?: "unknown"} (${response.errorCode ?: ""})")
 * }
 * ```
 *
 * @param relayerUrl The relayer endpoint URL (trailing slashes are stripped)
 * @param timeoutMs Default request timeout in milliseconds (default: 6 minutes for testnet retries)
 * @param httpClient Optional custom HTTP client for testing
 * @throws ConfigurationException.InvalidConfig if relayerUrl is blank
 */
class OZRelayerClient(
    relayerUrl: String,
    private val timeoutMs: Long = SmartAccountConstants.DEFAULT_RELAYER_TIMEOUT_MS,
    private val httpClient: HttpClient? = null
) {
    /**
     * Normalized URL with trailing slashes stripped.
     */
    private val normalizedUrl: String

    init {
        if (relayerUrl.isBlank()) {
            throw ConfigurationException.invalidConfig("Relayer URL is required")
        }
        if (!relayerUrl.startsWith("https://") && !relayerUrl.startsWith("http://localhost")) {
            throw ConfigurationException.invalidConfig(
                "Relayer URL must use HTTPS (or http://localhost for development): $relayerUrl"
            )
        }
        normalizedUrl = relayerUrl.trimEnd('/')
    }

    /**
     * JSON configuration for encoding/decoding requests and responses.
     */
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = false
    }

    /**
     * Whether the client is properly configured with a valid URL.
     */
    val isConfigured: Boolean
        get() = normalizedUrl.isNotBlank()

    // MARK: - Mode 1: Host Function + Auth Entries

    /**
     * Submits a transaction using host function and authorization entries.
     *
     * The relayer will construct a full transaction from these components,
     * wrap it with a fee bump, and submit it to the Stellar network.
     *
     * @param hostFunction The host function to execute
     * @param authEntries Authorization entries for the transaction
     * @param perRequestTimeoutMs Optional per-request timeout override in milliseconds
     * @return The relayer response with transaction hash or error
     */
    suspend fun send(
        hostFunction: HostFunctionXdr,
        authEntries: List<SorobanAuthorizationEntryXdr>,
        perRequestTimeoutMs: Long? = null
    ): RelayerResponse {
        // Encode host function to base64
        val funcBase64 = try {
            hostFunction.toXdrBase64()
        } catch (e: Exception) {
            return RelayerResponse(
                success = false,
                error = "Failed to encode host function to XDR: ${e.message}"
            )
        }

        // Encode auth entries to base64
        val authBase64Array = try {
            authEntries.map { it.toXdrBase64() }
        } catch (e: Exception) {
            return RelayerResponse(
                success = false,
                error = "Failed to encode auth entry to XDR: ${e.message}"
            )
        }

        // Build request payload as JsonObject to avoid kotlinx-serialization
        // errors with heterogeneous Map<String, Any> (String + List<String>)
        val payload = JsonObject(mapOf(
            "func" to JsonPrimitive(funcBase64),
            "auth" to JsonArray(authBase64Array.map { JsonPrimitive(it) })
        ))

        return performRequest(payload, perRequestTimeoutMs)
    }

    // MARK: - Mode 2: Signed Transaction XDR

    /**
     * Submits a complete signed transaction envelope.
     *
     * Use this for transactions that require source_account auth (e.g., deployment).
     * The relayer will fee-bump the signed transaction, preserving the inner signature.
     *
     * @param transactionEnvelope TransactionEnvelope XDR to submit
     * @param perRequestTimeoutMs Optional per-request timeout override in milliseconds
     * @return The relayer response with transaction hash or error
     */
    suspend fun sendXdr(
        transactionEnvelope: TransactionEnvelopeXdr,
        perRequestTimeoutMs: Long? = null
    ): RelayerResponse {
        // Encode transaction envelope to base64
        val xdrBase64 = try {
            transactionEnvelope.toXdrBase64()
        } catch (e: Exception) {
            return RelayerResponse(
                success = false,
                error = "Failed to encode transaction envelope to XDR: ${e.message}"
            )
        }

        // Build request payload as JsonObject for consistent serialization
        val payload = JsonObject(mapOf(
            "xdr" to JsonPrimitive(xdrBase64)
        ))

        return performRequest(payload, perRequestTimeoutMs)
    }

    // MARK: - Private Methods

    /**
     * Performs the HTTP request to the relayer.
     *
     * - Sends X-Client-Name and X-Client-Version headers
     * - On success: extracts fields from nested `data` wrapper if present
     * - On error (including non-2xx): parses error code from multiple locations
     * - On timeout: returns a response with TIMEOUT error code
     * - On network failure: returns a response with the error message
     *
     * Returns a RelayerResponse for all cases. Only XDR encoding failures
     * (before the request) return early responses.
     *
     * @param payload The JSON payload to send
     * @param perRequestTimeoutMs Optional per-request timeout override
     * @return The parsed relayer response (never throws)
     */
    private suspend fun performRequest(
        payload: JsonObject,
        perRequestTimeoutMs: Long? = null
    ): RelayerResponse {
        val effectiveTimeout = perRequestTimeoutMs ?: timeoutMs

        return withHttpClient(effectiveTimeout) { client ->
            val response = try {
                client.post(normalizedUrl) {
                    contentType(ContentType.Application.Json)
                    header("X-Client-Name", SmartAccountVersion.NAME)
                    header("X-Client-Version", SmartAccountVersion.VERSION)
                    setBody(payload)
                    timeout {
                        requestTimeoutMillis = effectiveTimeout
                        connectTimeoutMillis = effectiveTimeout
                        socketTimeoutMillis = effectiveTimeout
                    }
                }
            } catch (e: HttpRequestTimeoutException) {
                return@withHttpClient RelayerResponse(
                    success = false,
                    error = "Relayer request timed out",
                    errorCode = RelayerErrorCodes.TIMEOUT
                )
            } catch (e: Exception) {
                return@withHttpClient RelayerResponse(
                    success = false,
                    error = e.message ?: "Relayer request failed"
                )
            }

            // Parse the response body as JSON
            val responseBody = try {
                response.bodyAsText()
            } catch (e: Exception) {
                return@withHttpClient RelayerResponse(
                    success = false,
                    error = "Failed to read relayer response body"
                )
            }

            val responseJson = try {
                json.parseToJsonElement(responseBody).jsonObject
            } catch (e: Exception) {
                val truncated = if (responseBody.length > 200) responseBody.take(200) + "..." else responseBody
                return@withHttpClient RelayerResponse(
                    success = false,
                    error = "Failed to parse relayer response as JSON: $truncated"
                )
            }

            // Check for success: HTTP 2xx AND success=true in body
            val bodySuccess = responseJson["success"]?.jsonPrimitive?.booleanOrNull ?: false
            if (response.status.isSuccess() && bodySuccess) {
                // Extract from nested "data" wrapper if present, otherwise use top-level
                val data = (responseJson["data"] as? JsonObject) ?: responseJson
                return@withHttpClient RelayerResponse(
                    success = true,
                    transactionId = data["transactionId"]?.jsonPrimitive?.contentOrNull,
                    hash = data["hash"]?.jsonPrimitive?.contentOrNull,
                    status = data["status"]?.jsonPrimitive?.contentOrNull
                )
            }

            // Error response: extract error message and code
            val errorMessage = responseJson["error"]?.jsonPrimitive?.contentOrNull
                ?: responseJson["message"]?.jsonPrimitive?.contentOrNull
                ?: "Relayer request failed with status ${response.status.value}"
            val errorCode = extractErrorCode(responseJson)
            val details = responseJson["data"] ?: responseJson

            RelayerResponse(
                success = false,
                error = errorMessage,
                errorCode = errorCode,
                details = details
            )
        }
    }

    /**
     * Extracts the error code from a relayer error response.
     *
     * Checks multiple locations in order:
     * 1. Top-level `code` field
     * 2. Top-level `errorCode` field
     * 3. Nested `data.code` field
     *
     * @param responseJson The parsed JSON response object
     * @return The error code string, or null if none found
     */
    private fun extractErrorCode(responseJson: JsonObject): String? {
        // Check top-level "code"
        (responseJson["code"] as? JsonPrimitive)?.contentOrNull?.let { return it }

        // Check top-level "errorCode"
        (responseJson["errorCode"] as? JsonPrimitive)?.contentOrNull?.let { return it }

        // Check nested "data.code"
        val data = responseJson["data"] as? JsonObject
        if (data != null) {
            (data["code"] as? JsonPrimitive)?.contentOrNull?.let { return it }
        }

        return null
    }

    /**
     * Executes a block with an HTTP client, managing lifecycle properly.
     */
    private suspend fun <T> withHttpClient(
        effectiveTimeout: Long,
        block: suspend (HttpClient) -> T
    ): T {
        val client = httpClient ?: HttpClient {
            install(ContentNegotiation) {
                json(json)
            }
            install(HttpTimeout) {
                connectTimeoutMillis = effectiveTimeout
                requestTimeoutMillis = effectiveTimeout
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
}
