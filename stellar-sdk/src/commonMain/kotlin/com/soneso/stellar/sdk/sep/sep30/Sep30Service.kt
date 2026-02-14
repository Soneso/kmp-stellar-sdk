// Copyright 2026 Soneso. All rights reserved.
// Use of this source code is governed by a license that can be
// found in the LICENSE file.

package com.soneso.stellar.sdk.sep.sep30

import com.soneso.stellar.sdk.sep.sep30.exceptions.Sep30BadRequestException
import com.soneso.stellar.sdk.sep.sep30.exceptions.Sep30ConflictException
import com.soneso.stellar.sdk.sep.sep30.exceptions.Sep30InvalidResponseException
import com.soneso.stellar.sdk.sep.sep30.exceptions.Sep30NotFoundException
import com.soneso.stellar.sdk.sep.sep30.exceptions.Sep30UnauthorizedException
import com.soneso.stellar.sdk.sep.sep30.exceptions.Sep30UnknownResponseException
import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.http.content.TextContent
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * SEP-30 Account Recovery service client.
 *
 * Provides support for multi-party recovery of Stellar accounts. Recovery servers hold
 * signing keys for registered accounts and can sign recovery transactions after the
 * account owner authenticates via one or more identity verification methods.
 *
 * This service handles:
 * - Registering accounts with identity providers and authentication methods
 * - Updating identity configurations for registered accounts
 * - Requesting transaction signatures from recovery signers
 * - Querying account recovery details and listing registered accounts
 * - Deleting account recovery registrations
 *
 * All endpoints require a SEP-10 JWT authentication token.
 *
 * Typical workflow:
 * 1. Create service instance with the recovery server URL
 * 2. Authenticate via SEP-10 to obtain a JWT token
 * 3. Register an account with identity providers via [registerAccount]
 * 4. When recovery is needed, authenticate identities
 * 5. Request transaction signatures via [signTransaction]
 *
 * Example - Register an account:
 * ```kotlin
 * val sep30 = Sep30Service("https://recovery.example.com")
 *
 * val emailAuth = Sep30AuthMethod(type = "email", value = "user@example.com")
 * val phoneAuth = Sep30AuthMethod(type = "phone_number", value = "+14155551234")
 * val ownerIdentity = Sep30RequestIdentity(
 *     role = "owner",
 *     authMethods = listOf(emailAuth, phoneAuth)
 * )
 * val request = Sep30Request(identities = listOf(ownerIdentity))
 *
 * val response = sep30.registerAccount(accountAddress, request, jwtToken)
 * println("Registered: ${response.address}")
 * response.signers.forEach { println("Signer: ${it.key}") }
 * ```
 *
 * Example - Sign a recovery transaction:
 * ```kotlin
 * val signatureResponse = sep30.signTransaction(
 *     address = accountAddress,
 *     signingAddress = response.signers.first().key,
 *     transaction = transaction.toEnvelopeXdrBase64(),
 *     jwt = jwtToken
 * )
 * println("Signature: ${signatureResponse.signature}")
 * println("Network: ${signatureResponse.networkPassphrase}")
 * ```
 *
 * Example - List registered accounts:
 * ```kotlin
 * val accountsResponse = sep30.accounts(jwt = jwtToken)
 * accountsResponse.accounts.forEach { account ->
 *     println("Account: ${account.address}")
 * }
 * ```
 *
 * See also:
 * - [Sep30Request] for request construction
 * - [Sep30AccountResponse] for account response details
 * - [Sep30SignatureResponse] for signature response details
 * - [SEP-0030 Specification](https://github.com/stellar/stellar-protocol/blob/master/ecosystem/sep-0030.md)
 *
 * @property serviceUrl The base URL of the SEP-30 recovery server
 * @param httpClient Optional custom HTTP client for testing or custom configuration
 * @param httpRequestHeaders Optional custom headers applied to all requests
 */
class Sep30Service(
    val serviceUrl: String,
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

    /**
     * Base URL with trailing slashes removed for consistent path construction.
     */
    private val baseUrl: String = serviceUrl.trimEnd('/')

    /**
     * Registers an account with the recovery server.
     *
     * Creates a new account registration with the specified identity providers and
     * authentication methods. The recovery server will generate signing keys for the
     * registered identities that can later be used to sign recovery transactions.
     *
     * @param address The Stellar account address to register
     * @param request The identity configuration with authentication methods
     * @param jwt SEP-10 authentication token
     * @return [Sep30AccountResponse] with registered identities and signing addresses
     * @throws Sep30BadRequestException If the request is malformed or contains invalid parameters (HTTP 400)
     * @throws Sep30UnauthorizedException If the JWT token is missing, invalid, or expired (HTTP 401)
     * @throws Sep30NotFoundException If the endpoint is not found (HTTP 404)
     * @throws Sep30ConflictException If the account is already registered (HTTP 409)
     * @throws Sep30InvalidResponseException If the server returns HTTP 200 with a malformed body
     * @throws Sep30UnknownResponseException If the server returns an unexpected HTTP status code
     *
     * Example:
     * ```kotlin
     * val emailAuth = Sep30AuthMethod(type = "email", value = "user@example.com")
     * val ownerIdentity = Sep30RequestIdentity(role = "owner", authMethods = listOf(emailAuth))
     * val request = Sep30Request(identities = listOf(ownerIdentity))
     *
     * val response = sep30.registerAccount("GABC...", request, jwtToken)
     * println("Account: ${response.address}")
     * println("Signers: ${response.signers.map { it.key }}")
     * ```
     */
    suspend fun registerAccount(
        address: String,
        request: Sep30Request,
        jwt: String
    ): Sep30AccountResponse = withHttpClient { client ->
        val response = client.post("$baseUrl/accounts/$address") {
            applyHeaders(jwt)
            setBody(TextContent(mapToJsonString(request.toJson()), ContentType.Application.Json))
        }
        handleAccountResponse(response)
    }

    /**
     * Updates the identity configuration for a registered account.
     *
     * Replaces all existing identities with those provided in the request. This is not
     * a merge operation -- identities not included in the request are removed.
     *
     * @param address The Stellar account address to update
     * @param request The new identity configuration (replaces existing identities)
     * @param jwt SEP-10 authentication token
     * @return [Sep30AccountResponse] with updated identities and signing addresses
     * @throws Sep30BadRequestException If the request is malformed or contains invalid parameters (HTTP 400)
     * @throws Sep30UnauthorizedException If the JWT token is missing, invalid, or expired (HTTP 401)
     * @throws Sep30NotFoundException If the account is not registered (HTTP 404)
     * @throws Sep30ConflictException If there is a conflict with the current account state (HTTP 409)
     * @throws Sep30InvalidResponseException If the server returns HTTP 200 with a malformed body
     * @throws Sep30UnknownResponseException If the server returns an unexpected HTTP status code
     *
     * Example:
     * ```kotlin
     * val newAuth = Sep30AuthMethod(type = "phone_number", value = "+14155559876")
     * val updatedIdentity = Sep30RequestIdentity(role = "owner", authMethods = listOf(newAuth))
     * val request = Sep30Request(identities = listOf(updatedIdentity))
     *
     * val response = sep30.updateIdentitiesForAccount("GABC...", request, jwtToken)
     * ```
     */
    suspend fun updateIdentitiesForAccount(
        address: String,
        request: Sep30Request,
        jwt: String
    ): Sep30AccountResponse = withHttpClient { client ->
        val response = client.put("$baseUrl/accounts/$address") {
            applyHeaders(jwt)
            setBody(TextContent(mapToJsonString(request.toJson()), ContentType.Application.Json))
        }
        handleAccountResponse(response)
    }

    /**
     * Requests the recovery server to sign a transaction.
     *
     * Submits a transaction to be signed by one of the account's registered signing
     * addresses. The signing address must correspond to one of the signers returned
     * when the account was registered or queried.
     *
     * The authenticated identity must have sufficient permissions for the signing
     * address to authorize the signature.
     *
     * @param address The Stellar account address
     * @param signingAddress The signing address from the account's registered signers
     * @param transaction The base64-encoded transaction envelope XDR to sign
     * @param jwt SEP-10 authentication token
     * @return [Sep30SignatureResponse] with the signature and network passphrase
     * @throws Sep30BadRequestException If the request is malformed (HTTP 400)
     * @throws Sep30UnauthorizedException If the JWT token is invalid or identity not authenticated (HTTP 401)
     * @throws Sep30NotFoundException If the account or signing address is not found (HTTP 404)
     * @throws Sep30ConflictException If there is a signing conflict (HTTP 409)
     * @throws Sep30InvalidResponseException If the server returns HTTP 200 with a malformed body
     * @throws Sep30UnknownResponseException If the server returns an unexpected HTTP status code
     *
     * Example:
     * ```kotlin
     * val signatureResponse = sep30.signTransaction(
     *     address = "GABC...",
     *     signingAddress = "GDEF...",
     *     transaction = transaction.toEnvelopeXdrBase64(),
     *     jwt = jwtToken
     * )
     * println("Signature: ${signatureResponse.signature}")
     * println("Network: ${signatureResponse.networkPassphrase}")
     * ```
     */
    suspend fun signTransaction(
        address: String,
        signingAddress: String,
        transaction: String,
        jwt: String
    ): Sep30SignatureResponse = withHttpClient { client ->
        val response = client.post("$baseUrl/accounts/$address/sign/$signingAddress") {
            applyHeaders(jwt)
            setBody(TextContent(mapToJsonString(mapOf("transaction" to transaction)), ContentType.Application.Json))
        }
        handleSignatureResponse(response)
    }

    /**
     * Retrieves the recovery details for a registered account.
     *
     * Returns the account's recovery configuration including identities, their
     * authentication status, and the signing addresses controlled by the recovery server.
     *
     * @param address The Stellar account address to query
     * @param jwt SEP-10 authentication token
     * @return [Sep30AccountResponse] with identities and signers
     * @throws Sep30BadRequestException If the request is malformed (HTTP 400)
     * @throws Sep30UnauthorizedException If the JWT token is invalid or expired (HTTP 401)
     * @throws Sep30NotFoundException If the account is not registered (HTTP 404)
     * @throws Sep30ConflictException If there is a conflict retrieving the account (HTTP 409)
     * @throws Sep30InvalidResponseException If the server returns HTTP 200 with a malformed body
     * @throws Sep30UnknownResponseException If the server returns an unexpected HTTP status code
     *
     * Example:
     * ```kotlin
     * val details = sep30.accountDetails("GABC...", jwtToken)
     * println("Account: ${details.address}")
     * details.identities.forEach { identity ->
     *     println("Role: ${identity.role}, Authenticated: ${identity.authenticated}")
     * }
     * details.signers.forEach { signer ->
     *     println("Signer: ${signer.key}")
     * }
     * ```
     */
    suspend fun accountDetails(
        address: String,
        jwt: String
    ): Sep30AccountResponse = withHttpClient { client ->
        val response = client.get("$baseUrl/accounts/$address") {
            applyHeaders(jwt)
            contentType(ContentType.Application.Json)
        }
        handleAccountResponse(response)
    }

    /**
     * Deletes an account's recovery registration from the server.
     *
     * Permanently removes the account's recovery configuration. This operation
     * is irreversible. The server returns the account details as they were before deletion.
     *
     * @param address The Stellar account address to delete
     * @param jwt SEP-10 authentication token
     * @return [Sep30AccountResponse] with the account details before deletion
     * @throws Sep30BadRequestException If the request is malformed (HTTP 400)
     * @throws Sep30UnauthorizedException If the JWT token is invalid or expired (HTTP 401)
     * @throws Sep30NotFoundException If the account is not registered (HTTP 404)
     * @throws Sep30ConflictException If there is a conflict during deletion (HTTP 409)
     * @throws Sep30InvalidResponseException If the server returns HTTP 200 with a malformed body
     * @throws Sep30UnknownResponseException If the server returns an unexpected HTTP status code
     *
     * Example:
     * ```kotlin
     * val deletedAccount = sep30.deleteAccount("GABC...", jwtToken)
     * println("Deleted account: ${deletedAccount.address}")
     * ```
     */
    suspend fun deleteAccount(
        address: String,
        jwt: String
    ): Sep30AccountResponse = withHttpClient { client ->
        val response = client.delete("$baseUrl/accounts/$address") {
            applyHeaders(jwt)
            contentType(ContentType.Application.Json)
        }
        handleAccountResponse(response)
    }

    /**
     * Lists all accounts accessible by the authenticated user.
     *
     * Returns accounts that the authenticated user has permission to access.
     * Supports cursor-based pagination via the [after] parameter.
     *
     * @param jwt SEP-10 authentication token
     * @param after Optional cursor for pagination (Stellar account address to start after)
     * @return [Sep30AccountsResponse] with the list of accessible accounts
     * @throws Sep30BadRequestException If the request is malformed (HTTP 400)
     * @throws Sep30UnauthorizedException If the JWT token is invalid or expired (HTTP 401)
     * @throws Sep30NotFoundException If the endpoint is not found (HTTP 404)
     * @throws Sep30ConflictException If there is a retrieval conflict (HTTP 409)
     * @throws Sep30InvalidResponseException If the server returns HTTP 200 with a malformed body
     * @throws Sep30UnknownResponseException If the server returns an unexpected HTTP status code
     *
     * Example:
     * ```kotlin
     * // Get first page
     * val page1 = sep30.accounts(jwt = jwtToken)
     * page1.accounts.forEach { println("Account: ${it.address}") }
     *
     * // Get next page using last account address as cursor
     * if (page1.accounts.isNotEmpty()) {
     *     val lastAddress = page1.accounts.last().address
     *     val page2 = sep30.accounts(jwt = jwtToken, after = lastAddress)
     * }
     * ```
     */
    suspend fun accounts(
        jwt: String,
        after: String? = null
    ): Sep30AccountsResponse = withHttpClient { client ->
        val url = buildString {
            append("$baseUrl/accounts")
            if (after != null) {
                append("?after=$after")
            }
        }
        val response = client.get(url) {
            applyHeaders(jwt)
            contentType(ContentType.Application.Json)
        }
        handleAccountsResponse(response)
    }

    // ========== Private JSON Serialization Helpers ==========

    /**
     * Converts a [Map] to a JSON string for use as an HTTP request body.
     *
     * Handles nested maps, lists, strings, numbers, booleans, and nulls.
     * This avoids relying on Ktor's ContentNegotiation `guessSerializer` which cannot
     * serialize heterogeneous `Map<String, Any?>` types.
     */
    private fun mapToJsonString(map: Map<String, Any?>): String {
        val jsonElement = mapToJsonElement(map)
        return json.encodeToString(JsonElement.serializer(), jsonElement)
    }

    /**
     * Converts any value to a [JsonElement] for serialization.
     */
    private fun anyToJsonElement(value: Any?): JsonElement = when (value) {
        null -> JsonNull
        is String -> JsonPrimitive(value)
        is Number -> JsonPrimitive(value)
        is Boolean -> JsonPrimitive(value)
        is Map<*, *> -> JsonObject(
            value.entries.associate { (k, v) -> k.toString() to anyToJsonElement(v) }
        )
        is List<*> -> JsonArray(value.map { anyToJsonElement(it) })
        else -> JsonPrimitive(value.toString())
    }

    /**
     * Converts a [Map] to a [JsonObject].
     */
    private fun mapToJsonElement(map: Map<String, Any?>): JsonElement {
        return JsonObject(map.entries.associate { (k, v) -> k to anyToJsonElement(v) })
    }

    // ========== Private Response Handlers ==========

    /**
     * Handles an HTTP response expected to contain a [Sep30AccountResponse].
     *
     * Maps HTTP status codes to appropriate exceptions and parses the response body
     * on success.
     */
    private suspend fun handleAccountResponse(response: HttpResponse): Sep30AccountResponse {
        val body = response.bodyAsText()
        return when (response.status.value) {
            200 -> {
                try {
                    val jsonObject = json.decodeFromString<JsonObject>(body)
                    Sep30AccountResponse.fromJson(jsonObject)
                } catch (e: Sep30InvalidResponseException) {
                    throw e
                } catch (e: Exception) {
                    throw Sep30InvalidResponseException(
                        "Failed to parse SEP-30 account response: ${e.message}"
                    )
                }
            }
            400 -> throw Sep30BadRequestException(extractErrorMessage(body))
            401 -> throw Sep30UnauthorizedException(extractErrorMessage(body))
            404 -> throw Sep30NotFoundException(extractErrorMessage(body))
            409 -> throw Sep30ConflictException(extractErrorMessage(body))
            else -> throw Sep30UnknownResponseException(
                statusCode = response.status.value,
                responseBody = body,
                message = "Unexpected HTTP status: ${response.status.value}"
            )
        }
    }

    /**
     * Handles an HTTP response expected to contain a [Sep30SignatureResponse].
     *
     * Maps HTTP status codes to appropriate exceptions and parses the response body
     * on success.
     */
    private suspend fun handleSignatureResponse(response: HttpResponse): Sep30SignatureResponse {
        val body = response.bodyAsText()
        return when (response.status.value) {
            200 -> {
                try {
                    val jsonObject = json.decodeFromString<JsonObject>(body)
                    Sep30SignatureResponse.fromJson(jsonObject)
                } catch (e: Sep30InvalidResponseException) {
                    throw e
                } catch (e: Exception) {
                    throw Sep30InvalidResponseException(
                        "Failed to parse SEP-30 signature response: ${e.message}"
                    )
                }
            }
            400 -> throw Sep30BadRequestException(extractErrorMessage(body))
            401 -> throw Sep30UnauthorizedException(extractErrorMessage(body))
            404 -> throw Sep30NotFoundException(extractErrorMessage(body))
            409 -> throw Sep30ConflictException(extractErrorMessage(body))
            else -> throw Sep30UnknownResponseException(
                statusCode = response.status.value,
                responseBody = body,
                message = "Unexpected HTTP status: ${response.status.value}"
            )
        }
    }

    /**
     * Handles an HTTP response expected to contain a [Sep30AccountsResponse].
     *
     * Maps HTTP status codes to appropriate exceptions and parses the response body
     * on success.
     */
    private suspend fun handleAccountsResponse(response: HttpResponse): Sep30AccountsResponse {
        val body = response.bodyAsText()
        return when (response.status.value) {
            200 -> {
                try {
                    val jsonObject = json.decodeFromString<JsonObject>(body)
                    Sep30AccountsResponse.fromJson(jsonObject)
                } catch (e: Sep30InvalidResponseException) {
                    throw e
                } catch (e: Exception) {
                    throw Sep30InvalidResponseException(
                        "Failed to parse SEP-30 accounts response: ${e.message}"
                    )
                }
            }
            400 -> throw Sep30BadRequestException(extractErrorMessage(body))
            401 -> throw Sep30UnauthorizedException(extractErrorMessage(body))
            404 -> throw Sep30NotFoundException(extractErrorMessage(body))
            409 -> throw Sep30ConflictException(extractErrorMessage(body))
            else -> throw Sep30UnknownResponseException(
                statusCode = response.status.value,
                responseBody = body,
                message = "Unexpected HTTP status: ${response.status.value}"
            )
        }
    }

    // ========== Private HTTP Utilities ==========

    /**
     * Extracts an error message from a JSON response body.
     *
     * Attempts to parse the body as JSON and extract the `"error"` field.
     * If parsing fails or the field is missing, returns the raw body as the message.
     */
    private fun extractErrorMessage(body: String): String {
        return try {
            val jsonObject = json.decodeFromString<JsonObject>(body)
            jsonObject["error"]?.jsonPrimitive?.contentOrNull ?: body
        } catch (_: Exception) {
            body
        }
    }

    /**
     * Applies common headers to an HTTP request, including the Authorization bearer
     * token and any custom headers provided at construction time.
     */
    private fun HttpRequestBuilder.applyHeaders(jwt: String) {
        header("Authorization", "Bearer $jwt")
        httpRequestHeaders?.forEach { (key, value) -> header(key, value) }
    }

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
}
