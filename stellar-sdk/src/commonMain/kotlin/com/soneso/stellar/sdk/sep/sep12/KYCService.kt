// Copyright 2025 Soneso. All rights reserved.
// Use of this source code is governed by a license that can be
// found in the LICENSE file.

package com.soneso.stellar.sdk.sep.sep12

import com.soneso.stellar.sdk.HttpTimeouts
import com.soneso.stellar.sdk.sep.sep01.StellarToml
import com.soneso.stellar.sdk.sep.sep12.exceptions.*
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.utils.io.core.*
import kotlinx.serialization.json.Json

/**
 * SEP-12 KYC (Know Your Customer) API client.
 *
 * Implements the client side of SEP-12 for collecting customer information for
 * regulatory compliance. This service allows wallets and applications to:
 * - Register customers with required KYC information
 * - Check KYC status and requirements
 * - Upload verification documents
 * - Update customer information
 * - Verify customer data (phone, email, etc.)
 * - Manage customer callbacks for status updates
 * - Delete customer data (GDPR compliance)
 *
 * The KYC process typically follows this workflow:
 * 1. Client authenticates with SEP-10 WebAuth to get a JWT token
 * 2. Client calls GET /customer to check what information is required
 * 3. Server responds with required fields based on customer status
 * 4. Client submits information via PUT /customer
 * 5. Client may need to verify fields (email, phone) via verification codes
 * 6. Process repeats until status is ACCEPTED or REJECTED
 *
 * Customer statuses:
 * - ACCEPTED: Customer has been approved
 * - PROCESSING: Customer information is being reviewed
 * - NEEDS_INFO: More information is required
 * - REJECTED: Customer was rejected
 *
 * Example - Complete KYC flow:
 * ```kotlin
 * // 1. Initialize KYC service from domain
 * val kycService = KYCService.fromDomain("testanchor.stellar.org")
 *
 * // 2. Get JWT token via WebAuth (SEP-10)
 * val webAuth = WebAuth.fromDomain("testanchor.stellar.org", Network.TESTNET)
 * val userKeyPair = KeyPair.fromSecretSeed("S...")
 * val authToken = webAuth.jwtToken(
 *     clientAccountId = userKeyPair.getAccountId(),
 *     signers = listOf(userKeyPair)
 * )
 *
 * // 3. Check what information is required
 * val infoRequest = GetCustomerInfoRequest(jwt = authToken.token)
 * val infoResponse = kycService.getCustomerInfo(infoRequest)
 *
 * when (infoResponse.status) {
 *     CustomerStatus.NEEDS_INFO -> {
 *         println("Required fields: ${infoResponse.fields?.keys}")
 *
 *         // 4. Submit customer information
 *         val putRequest = PutCustomerInfoRequest(
 *             jwt = authToken.token,
 *             kycFields = StandardKYCFields(
 *                 naturalPersonKYCFields = NaturalPersonKYCFields(
 *                     firstName = "John",
 *                     lastName = "Doe",
 *                     emailAddress = "john@example.com",
 *                     birthDate = LocalDate(1990, 1, 15)
 *                 )
 *             )
 *         )
 *         val putResponse = kycService.putCustomerInfo(putRequest)
 *         println("Customer ID: ${putResponse.id}")
 *     }
 *     CustomerStatus.ACCEPTED -> println("Customer already verified")
 *     CustomerStatus.PROCESSING -> println("Verification in progress")
 *     CustomerStatus.REJECTED -> println("Customer rejected: ${infoResponse.message}")
 * }
 * ```
 *
 * Example - With document upload:
 * ```kotlin
 * val putRequest = PutCustomerInfoRequest(
 *     jwt = authToken.token,
 *     kycFields = StandardKYCFields(
 *         naturalPersonKYCFields = NaturalPersonKYCFields(
 *             firstName = "John",
 *             lastName = "Doe",
 *             photoIdFront = loadImageBytes("passport_front.jpg"),
 *             photoIdBack = loadImageBytes("passport_back.jpg")
 *         )
 *     )
 * )
 * val response = kycService.putCustomerInfo(putRequest)
 * ```
 *
 * Example - Email verification:
 * ```kotlin
 * // After submitting email, verify with code sent by anchor
 * val verifyRequest = PutCustomerInfoRequest(
 *     jwt = authToken.token,
 *     id = customerId,
 *     verificationFields = mapOf(
 *         "email_address_verification" to "123456",
 *         "mobile_number_verification" to "654321"
 *     )
 * )
 * val response = kycService.putCustomerInfo(verifyRequest)
 * ```
 *
 * Example - Set up status callbacks:
 * ```kotlin
 * val callbackRequest = PutCustomerCallbackRequest(
 *     jwt = authToken.token,
 *     url = "https://myapp.com/kyc-webhook",
 *     account = userKeyPair.getAccountId()
 * )
 * kycService.putCustomerCallback(callbackRequest)
 * ```
 *
 * See also:
 * - [fromDomain] for automatic configuration from stellar.toml
 * - [getCustomerInfo] for checking KYC status
 * - [putCustomerInfo] for submitting customer data
 * - [WebAuth] for obtaining JWT tokens (SEP-10)
 * - [StandardKYCFields] for standard KYC field definitions (SEP-9)
 * - [SEP-0012 Specification](https://github.com/stellar/stellar-protocol/blob/master/ecosystem/sep-0012.md)
 *
 * @property serviceAddress The base URL of the KYC server
 * @property httpClient Optional custom HTTP client for testing
 * @property httpRequestHeaders Optional custom headers for all requests
 */
class KYCService(
    private val serviceAddress: String,
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
         * Creates a KYCService by discovering the endpoint from stellar.toml.
         *
         * Fetches the stellar.toml file from the specified domain and extracts the
         * KYC server address. First checks for KYC_SERVER, falls back to TRANSFER_SERVER
         * if not found (some anchors use the transfer server for KYC endpoints).
         *
         * @param domain The domain name hosting the stellar.toml file
         * @param httpClient Optional custom HTTP client for testing
         * @param httpRequestHeaders Optional custom headers for requests
         * @return KYCService configured with the domain's KYC endpoint
         * @throws IllegalStateException If neither KYC_SERVER nor TRANSFER_SERVER is found
         *
         * Example:
         * ```kotlin
         * val kycService = KYCService.fromDomain("testanchor.stellar.org")
         * ```
         */
        suspend fun fromDomain(
            domain: String,
            httpClient: HttpClient? = null,
            httpRequestHeaders: Map<String, String>? = null
        ): KYCService {
            val stellarToml = StellarToml.fromDomain(
                domain = domain,
                httpClient = httpClient,
                httpRequestHeaders = httpRequestHeaders
            )

            val address = stellarToml.generalInformation.kycServer
                ?: stellarToml.generalInformation.transferServer
                ?: throw IllegalStateException(
                    "KYC_SERVER or TRANSFER_SERVER not found in stellar.toml for domain: $domain"
                )

            return KYCService(
                serviceAddress = address,
                httpClient = httpClient,
                httpRequestHeaders = httpRequestHeaders
            )
        }
    }

    /**
     * Retrieves customer information and KYC status from the anchor.
     *
     * This endpoint serves two primary purposes:
     * 1. Discover required fields: If no customer exists, returns the fields needed to register
     * 2. Check KYC status: For existing customers, returns current status and additional requirements
     *
     * @param request GetCustomerInfoRequest containing authentication and identification
     * @return GetCustomerInfoResponse with status and field requirements
     * @throws CustomerNotFoundException If customer not found (404)
     * @throws UnauthorizedException If JWT token is invalid (401)
     * @throws KYCException For other errors
     *
     * Example:
     * ```kotlin
     * val request = GetCustomerInfoRequest(
     *     jwt = authToken,
     *     account = userAccountId,
     *     type = "sep31-sender"
     * )
     * val response = kycService.getCustomerInfo(request)
     *
     * when (response.status) {
     *     CustomerStatus.NEEDS_INFO -> {
     *         response.fields?.forEach { (fieldName, fieldInfo) ->
     *             println("Field: $fieldName - ${fieldInfo.description}")
     *         }
     *     }
     *     CustomerStatus.ACCEPTED -> println("Customer approved")
     *     else -> println("Status: ${response.status}")
     * }
     * ```
     */
    suspend fun getCustomerInfo(request: GetCustomerInfoRequest): GetCustomerInfoResponse {
        return withHttpClient { client ->
            val url = buildUrl("$serviceAddress/customer") {
                request.id?.let { parameters.append("id", it) }
                request.account?.let { parameters.append("account", it) }
                request.memo?.let { parameters.append("memo", it) }
                request.memoType?.let { parameters.append("memo_type", it) }
                request.type?.let { parameters.append("type", it) }
                request.transactionId?.let { parameters.append("transaction_id", it) }
                request.lang?.let { parameters.append("lang", it) }
            }

            val response = client.get(url) {
                header("Authorization", "Bearer ${request.jwt}")
                applyCustomHeaders()
            }

            handleResponse(response) { body ->
                json.decodeFromString<GetCustomerInfoResponse>(body)
            }
        }
    }

    /**
     * Uploads or updates customer information for KYC verification.
     *
     * Submits customer data to the anchor in an authenticated and idempotent manner.
     * Supports text fields, binary file uploads, verification codes, and file references.
     *
     * @param request PutCustomerInfoRequest containing customer data and authentication
     * @return PutCustomerInfoResponse with the customer ID
     * @throws InvalidFieldException If field validation fails (400)
     * @throws UnauthorizedException If JWT token is invalid (401)
     * @throws CustomerAlreadyExistsException If customer already exists (409)
     * @throws KYCException For other errors
     *
     * Example:
     * ```kotlin
     * val request = PutCustomerInfoRequest(
     *     jwt = authToken,
     *     kycFields = StandardKYCFields(
     *         naturalPersonKYCFields = NaturalPersonKYCFields(
     *             firstName = "John",
     *             lastName = "Doe",
     *             emailAddress = "john@example.com"
     *         )
     *     )
     * )
     * val response = kycService.putCustomerInfo(request)
     * println("Customer ID: ${response.id}")
     * ```
     */
    suspend fun putCustomerInfo(request: PutCustomerInfoRequest): PutCustomerInfoResponse {
        return withHttpClient { client ->
            val url = "$serviceAddress/customer"

            // Build fields map (text fields first)
            val fields = mutableMapOf<String, String>()
            request.id?.let { fields["id"] = it }
            request.account?.let { fields["account"] = it }
            request.memo?.let { fields["memo"] = it }
            request.memoType?.let { fields["memo_type"] = it }
            request.type?.let { fields["type"] = it }
            request.transactionId?.let { fields["transaction_id"] = it }

            // Add SEP-09 fields
            request.kycFields?.let { kycFields ->
                kycFields.naturalPersonKYCFields?.let { fields.putAll(it.fields()) }
                kycFields.organizationKYCFields?.let { fields.putAll(it.fields()) }
            }

            // Add custom fields
            request.customFields?.let { fields.putAll(it) }

            // Add verification fields
            request.verificationFields?.let { fields.putAll(it) }

            // Add file references
            request.fileReferences?.let { fields.putAll(it) }

            // Build files map (binary fields at the end - SEP-12 requirement)
            val files = mutableMapOf<String, ByteArray>()
            request.kycFields?.let { kycFields ->
                kycFields.naturalPersonKYCFields?.let { files.putAll(it.files()) }
                kycFields.organizationKYCFields?.let { files.putAll(it.files()) }
            }

            // Add custom files
            request.customFiles?.let { files.putAll(it) }

            // Send multipart/form-data request (required by most anchors per SEP-12)
            val response = client.put(url) {
                header("Authorization", "Bearer ${request.jwt}")
                applyCustomHeaders()
                setBody(
                    MultiPartFormDataContent(
                        formData {
                            // Add text fields first
                            fields.forEach { (key, value) ->
                                append(key, value)
                            }
                            // Add binary files at the end (SEP-12 requirement)
                            files.forEach { (key, value) ->
                                append(key, value, Headers.build {
                                    append(HttpHeaders.ContentType, "application/octet-stream")
                                    append(HttpHeaders.ContentDisposition, "filename=\"$key\"")
                                })
                            }
                        }
                    )
                )
            }

            handleResponse(response) { body ->
                json.decodeFromString<PutCustomerInfoResponse>(body)
            }
        }
    }

    /**
     * Verifies customer information fields using confirmation codes (DEPRECATED).
     *
     * This endpoint is deprecated in favor of using PUT /customer with verificationFields.
     * Use [putCustomerInfo] with verificationFields parameter instead.
     *
     * @param request PutCustomerVerificationRequest with customer ID and verification codes
     * @return GetCustomerInfoResponse with updated customer status
     * @throws UnauthorizedException If JWT token is invalid (401)
     * @throws CustomerNotFoundException If customer not found (404)
     * @throws KYCException For other errors
     */
    @Deprecated(
        message = "Use putCustomerInfo with verificationFields instead",
        replaceWith = ReplaceWith("putCustomerInfo(PutCustomerInfoRequest(jwt, id = request.id, verificationFields = request.verificationFields))")
    )
    suspend fun putCustomerVerification(request: PutCustomerVerificationRequest): GetCustomerInfoResponse {
        return withHttpClient { client ->
            val url = "$serviceAddress/customer/verification"

            val fields = mutableMapOf<String, String>()
            request.id?.let { fields["id"] = it }
            request.verificationFields?.let { fields.putAll(it) }

            val response = client.put(url) {
                header("Authorization", "Bearer ${request.jwt}")
                applyCustomHeaders()
                setBody(
                    MultiPartFormDataContent(
                        formData {
                            fields.forEach { (key, value) ->
                                append(key, value)
                            }
                        }
                    )
                )
            }

            handleResponse(response) { body ->
                json.decodeFromString<GetCustomerInfoResponse>(body)
            }
        }
    }

    /**
     * Deletes all personal information for a customer (GDPR compliance).
     *
     * Removes all customer data stored by the anchor. This is used to comply with
     * privacy regulations like GDPR's "right to be forgotten".
     *
     * @param account The Stellar account ID of the customer to delete
     * @param memo Optional memo if account is shared
     * @param memoType Type of memo (id, text, or hash)
     * @param jwt SEP-10 JWT token proving ownership of the account
     * @return HttpResponse - 200 OK on successful deletion
     * @throws UnauthorizedException If JWT token is invalid (401)
     * @throws CustomerNotFoundException If customer not found (404)
     * @throws KYCException For other errors
     *
     * Example:
     * ```kotlin
     * kycService.deleteCustomer(
     *     account = userAccountId,
     *     memo = null,
     *     memoType = null,
     *     jwt = authToken
     * )
     * println("Customer data deleted")
     * ```
     */
    suspend fun deleteCustomer(
        account: String,
        memo: String? = null,
        memoType: String? = null,
        jwt: String
    ): HttpResponse {
        return withHttpClient { client ->
            val url = buildUrl("$serviceAddress/customer/$account") {
                memo?.let { parameters.append("memo", it) }
                memoType?.let { parameters.append("memo_type", it) }
            }

            val response = client.delete(url) {
                header("Authorization", "Bearer $jwt")
                applyCustomHeaders()
            }

            // For DELETE, we return the raw response
            handleResponse(response) { response }
        }
    }

    /**
     * Registers a callback URL to receive KYC status updates.
     *
     * Allows clients to receive webhook notifications when customer KYC status changes.
     * The anchor will POST updates to the provided URL.
     *
     * @param request PutCustomerCallbackRequest with callback URL and customer identification
     * @return HttpResponse - 200 OK on successful registration
     * @throws UnauthorizedException If JWT token is invalid (401)
     * @throws KYCException For other errors
     *
     * Example:
     * ```kotlin
     * val request = PutCustomerCallbackRequest(
     *     jwt = authToken,
     *     url = "https://myapp.com/webhooks/kyc-status",
     *     account = userAccountId
     * )
     * kycService.putCustomerCallback(request)
     * ```
     */
    suspend fun putCustomerCallback(request: PutCustomerCallbackRequest): HttpResponse {
        return withHttpClient { client ->
            val url = "$serviceAddress/customer/callback"

            val fields = mutableMapOf<String, String>()
            fields["url"] = request.url
            request.id?.let { fields["id"] = it }
            request.account?.let { fields["account"] = it }
            request.memo?.let { fields["memo"] = it }

            val response = client.put(url) {
                header("Authorization", "Bearer ${request.jwt}")
                applyCustomHeaders()
                setBody(
                    MultiPartFormDataContent(
                        formData {
                            fields.forEach { (key, value) ->
                                append(key, value)
                            }
                        }
                    )
                )
            }

            handleResponse(response) { response }
        }
    }


    /**
     * Uploads a binary file separately from customer information.
     *
     * Decouples file uploads from PUT /customer requests. Once uploaded, the returned
     * file_id can be used in subsequent PUT /customer requests.
     *
     * @param file Binary file data as ByteArray
     * @param jwt SEP-10 JWT token for authentication
     * @return CustomerFileResponse containing the file_id and metadata
     * @throws FileTooLargeException If file exceeds size limit (413)
     * @throws UnauthorizedException If JWT token is invalid (401)
     * @throws KYCException For other errors
     *
     * Example:
     * ```kotlin
     * val photoBytes = loadImageBytes("passport_front.jpg")
     * val fileResponse = kycService.postCustomerFile(photoBytes, authToken)
     * println("File uploaded: ${fileResponse.fileId}")
     *
     * // Use file_id in PUT /customer request
     * val putRequest = PutCustomerInfoRequest(
     *     jwt = authToken,
     *     fileReferences = mapOf("photo_id_front_file_id" to fileResponse.fileId)
     * )
     * kycService.putCustomerInfo(putRequest)
     * ```
     */
    suspend fun postCustomerFile(file: ByteArray, jwt: String): CustomerFileResponse {
        return withHttpClient { client ->
            val url = "$serviceAddress/customer/files"

            val response = client.submitFormWithBinaryData(
                url = url,
                formData = formData {
                    append("file", file, Headers.build {
                        append(HttpHeaders.ContentType, "application/octet-stream")
                        append(HttpHeaders.ContentDisposition, "filename=\"file\"")
                    })
                }
            ) {
                method = HttpMethod.Post
                header("Authorization", "Bearer $jwt")
                applyCustomHeaders()
            }

            handleResponse(response) { body ->
                json.decodeFromString<CustomerFileResponse>(body)
            }
        }
    }

    /**
     * Retrieves information about files previously uploaded.
     *
     * Allows clients to query metadata about uploaded files by either file ID or customer ID.
     *
     * @param jwt SEP-10 JWT token for authentication
     * @param fileId Optional file ID to retrieve specific file info
     * @param customerId Optional customer ID to retrieve all files for a customer
     * @return GetCustomerFilesResponse containing a list of file metadata
     * @throws UnauthorizedException If JWT token is invalid (401)
     * @throws KYCException For other errors
     *
     * Example:
     * ```kotlin
     * // Get specific file info
     * val filesResponse = kycService.getCustomerFiles(
     *     jwt = authToken,
     *     fileId = fileId
     * )
     *
     * // Get all files for a customer
     * val filesResponse = kycService.getCustomerFiles(
     *     jwt = authToken,
     *     customerId = customerId
     * )
     * ```
     */
    suspend fun getCustomerFiles(
        jwt: String,
        fileId: String? = null,
        customerId: String? = null
    ): GetCustomerFilesResponse {
        return withHttpClient { client ->
            val url = buildUrl("$serviceAddress/customer/files") {
                fileId?.let { parameters.append("file_id", it) }
                customerId?.let { parameters.append("customer_id", it) }
            }

            val response = client.get(url) {
                header("Authorization", "Bearer $jwt")
                applyCustomHeaders()
            }

            handleResponse(response) { body ->
                json.decodeFromString<GetCustomerFilesResponse>(body)
            }
        }
    }

    /**
     * Executes a block with an HTTP client, managing lifecycle properly.
     */
    private suspend fun <T> withHttpClient(block: suspend (HttpClient) -> T): T {
        val client = httpClient ?: HttpClient {
            install(ContentNegotiation) {
                json(json)
            }
            install(HttpTimeout) {
                connectTimeoutMillis = HttpTimeouts.CONNECT_TIMEOUT_MS
                requestTimeoutMillis = HttpTimeouts.REQUEST_TIMEOUT_MS
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
     * Builds a URL with query parameters.
     */
    private fun buildUrl(baseUrl: String, block: URLBuilder.() -> Unit = {}): String {
        return URLBuilder(baseUrl).apply(block).buildString()
    }

    /**
     * Applies custom HTTP headers to a request.
     */
    private fun HttpRequestBuilder.applyCustomHeaders() {
        httpRequestHeaders?.forEach { (key, value) ->
            header(key, value)
        }
    }

    /**
     * Handles HTTP response and maps errors to KYC exceptions.
     */
    private suspend fun <T> handleResponse(
        response: HttpResponse,
        parser: suspend (String) -> T
    ): T {
        val statusCode = response.status.value
        val body = response.bodyAsText()

        return when (statusCode) {
            200, 202 -> parser(body)  // 200 OK or 202 Accepted (async processing)
            400 -> {
                // Try to parse error details from response
                val errorMessage = try {
                    val errorJson = json.decodeFromString<Map<String, String>>(body)
                    errorJson["error"] ?: body
                } catch (e: Exception) {
                    body
                }

                // Check if it's a field-specific error
                val fieldName = extractFieldName(errorMessage)
                throw InvalidFieldException(fieldName, errorMessage)
            }
            401 -> throw UnauthorizedException()
            404 -> {
                // Extract account ID from request or response if possible
                val accountId = extractAccountId(body)
                throw CustomerNotFoundException(accountId)
            }
            409 -> {
                // Try to extract existing customer ID from response
                val existingId = extractCustomerId(body)
                throw CustomerAlreadyExistsException(existingId)
            }
            413 -> {
                // Try to extract file size from response
                val fileSize = extractFileSize(body)
                throw FileTooLargeException(fileSize)
            }
            else -> throw KYCException(
                "HTTP $statusCode: ${response.status.description} - $body"
            )
        }
    }

    /**
     * Extracts field name from error message.
     */
    private fun extractFieldName(errorMessage: String): String? {
        // Try to extract field name from common error message patterns
        val patterns = listOf(
            Regex("field[:\\s]+['\"]?([a-z_]+)['\"]?", RegexOption.IGNORE_CASE),
            Regex("['\"]?([a-z_]+)['\"]?[:\\s]+is\\s+invalid", RegexOption.IGNORE_CASE)
        )

        for (pattern in patterns) {
            pattern.find(errorMessage)?.let { match ->
                return match.groupValues.getOrNull(1)
            }
        }

        return null
    }

    /**
     * Extracts account ID from error message.
     */
    private fun extractAccountId(errorMessage: String): String {
        // Try to extract Stellar account ID (G...) from error message
        val pattern = Regex("(G[A-Z0-9]{55})")
        return pattern.find(errorMessage)?.groupValues?.getOrNull(1) ?: "unknown"
    }

    /**
     * Extracts customer ID from error message.
     */
    private fun extractCustomerId(errorMessage: String): String? {
        // Try to extract customer ID from error message
        val pattern = Regex("id[:\\s]+['\"]?([a-f0-9-]+)['\"]?", RegexOption.IGNORE_CASE)
        return pattern.find(errorMessage)?.groupValues?.getOrNull(1)
    }

    /**
     * Extracts file size from error message.
     */
    private fun extractFileSize(errorMessage: String): Long? {
        // Try to extract file size from error message
        val patterns = listOf(
            Regex("(\\d+)\\s*bytes?", RegexOption.IGNORE_CASE),
            Regex("(\\d+)\\s*MB", RegexOption.IGNORE_CASE)
        )

        for (pattern in patterns) {
            pattern.find(errorMessage)?.let { match ->
                val size = match.groupValues.getOrNull(1)?.toLongOrNull()
                if (size != null) {
                    // Convert MB to bytes if pattern matched MB
                    return if (pattern.pattern.contains("MB", ignoreCase = true)) {
                        size * 1024 * 1024
                    } else {
                        size
                    }
                }
            }
        }

        return null
    }
}
