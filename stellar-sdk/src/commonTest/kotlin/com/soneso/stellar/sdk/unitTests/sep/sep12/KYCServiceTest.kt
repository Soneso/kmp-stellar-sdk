// Copyright 2025 Soneso. All rights reserved.
// Use of this source code is governed by a license that can be
// found in the LICENSE file.

package com.soneso.stellar.sdk.unitTests.sep.sep12

import com.soneso.stellar.sdk.sep.sep12.*
import com.soneso.stellar.sdk.sep.sep09.NaturalPersonKYCFields
import com.soneso.stellar.sdk.sep.sep09.OrganizationKYCFields
import com.soneso.stellar.sdk.sep.sep09.StandardKYCFields
import com.soneso.stellar.sdk.sep.sep12.exceptions.*
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.utils.io.core.*
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json
import kotlin.test.*

class KYCServiceTest {

    private val serviceAddress = "https://api.stellar.org/kyc"
    private val jwtToken = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.test"
    private val customerId = "d1ce2f48-3ff1-495d-9240-7a50d806cfed"
    private val accountId = "GBWMCCC3NHSKLAOJDBKKYW7SSH2PFTTNVFKWSGLWGDLEBKLOVP5JLBBP"

    private fun createMockClient(
        responseContent: String,
        statusCode: HttpStatusCode = HttpStatusCode.OK,
        expectedPath: String = "/customer",
        expectedMethod: HttpMethod = HttpMethod.Get,
        validateAuth: Boolean = true,
        contentType: String = "application/json"
    ): HttpClient {
        val mockEngine = MockEngine { request ->
            if (request.url.encodedPath.contains(expectedPath)) {
                if (expectedMethod != request.method) {
                    respond(
                        content = """{"error": "Method not allowed"}""",
                        status = HttpStatusCode.MethodNotAllowed,
                        headers = headersOf(HttpHeaders.ContentType, "application/json")
                    )
                } else if (validateAuth && !request.headers["Authorization"]!!.contains("Bearer")) {
                    respond(
                        content = """{"error": "Unauthorized"}""",
                        status = HttpStatusCode.Unauthorized,
                        headers = headersOf(HttpHeaders.ContentType, "application/json")
                    )
                } else {
                    respond(
                        content = responseContent,
                        status = statusCode,
                        headers = headersOf(HttpHeaders.ContentType, contentType)
                    )
                }
            } else {
                respond(
                    content = """{"error": "Not found"}""",
                    status = HttpStatusCode.NotFound,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            }
        }

        return HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                })
            }
        }
    }

    @Test
    fun testFromDomainDiscoversKYCServer() = runTest {
        val stellarToml = """
            VERSION="2.0.0"
            KYC_SERVER="https://kyc.example.com"
            SIGNING_KEY="GBWMCCC3NHSKLAOJDBKKYW7SSH2PFTTNVFKWSGLWGDLEBKLOVP5JLBBP"
        """.trimIndent()

        val mockClient = createMockClient(
            responseContent = stellarToml,
            expectedPath = "/.well-known/stellar.toml",
            validateAuth = false,
            contentType = "text/plain"
        )

        val kycService = KYCService.fromDomain(
            domain = "example.com",
            httpClient = mockClient
        )

        assertNotNull(kycService)
    }

    @Test
    fun testFromDomainFallsBackToTransferServer() = runTest {
        val stellarToml = """
            VERSION="2.0.0"
            TRANSFER_SERVER="https://transfer.example.com"
            SIGNING_KEY="GBWMCCC3NHSKLAOJDBKKYW7SSH2PFTTNVFKWSGLWGDLEBKLOVP5JLBBP"
        """.trimIndent()

        val mockClient = createMockClient(
            responseContent = stellarToml,
            expectedPath = "/.well-known/stellar.toml",
            validateAuth = false,
            contentType = "text/plain"
        )

        val kycService = KYCService.fromDomain(
            domain = "example.com",
            httpClient = mockClient
        )

        assertNotNull(kycService)
    }

    @Test
    fun testFromDomainThrowsWhenNoServerFound() = runTest {
        val stellarToml = """
            VERSION="2.0.0"
            SIGNING_KEY="GBWMCCC3NHSKLAOJDBKKYW7SSH2PFTTNVFKWSGLWGDLEBKLOVP5JLBBP"
        """.trimIndent()

        val mockClient = createMockClient(
            responseContent = stellarToml,
            expectedPath = "/.well-known/stellar.toml",
            validateAuth = false,
            contentType = "text/plain"
        )

        assertFailsWith<IllegalStateException> {
            KYCService.fromDomain(
                domain = "example.com",
                httpClient = mockClient
            )
        }
    }

    @Test
    fun testGetCustomerInfoSuccess() = runTest {
        val responseJson = """
            {
                "id": "$customerId",
                "status": "ACCEPTED",
                "provided_fields": {
                    "first_name": {
                        "description": "The customer's first name",
                        "type": "string",
                        "status": "ACCEPTED"
                    }
                }
            }
        """.trimIndent()

        val mockClient = createMockClient(responseJson)
        val kycService = KYCService(serviceAddress, mockClient)

        val request = GetCustomerInfoRequest(jwt = jwtToken, id = customerId)
        val response = kycService.getCustomerInfo(request)

        assertEquals(customerId, response.id)
        assertEquals(CustomerStatus.ACCEPTED, response.status)
        assertNotNull(response.providedFields)
    }

    @Test
    fun testGetCustomerInfoWithAllParameters() = runTest {
        val responseJson = """
            {
                "id": "$customerId",
                "status": "NEEDS_INFO",
                "fields": {
                    "email_address": {
                        "description": "Email address",
                        "type": "string"
                    }
                }
            }
        """.trimIndent()

        val mockClient = createMockClient(responseJson)
        val kycService = KYCService(serviceAddress, mockClient)

        val request = GetCustomerInfoRequest(
            jwt = jwtToken,
            id = customerId,
            account = accountId,
            memo = "123",
            memoType = "id",
            type = "sep31-sender",
            transactionId = "tx123",
            lang = "en"
        )
        val response = kycService.getCustomerInfo(request)

        assertEquals(customerId, response.id)
        assertEquals(CustomerStatus.NEEDS_INFO, response.status)
    }

    @Test
    fun testGetCustomerInfoNeedsInfoStatus() = runTest {
        val responseJson = """
            {
                "status": "NEEDS_INFO",
                "fields": {
                    "mobile_number": {
                        "description": "phone number of the customer",
                        "type": "string"
                    },
                    "email_address": {
                        "description": "email address of the customer",
                        "type": "string",
                        "optional": true
                    }
                }
            }
        """.trimIndent()

        val mockClient = createMockClient(responseJson)
        val kycService = KYCService(serviceAddress, mockClient)

        val request = GetCustomerInfoRequest(jwt = jwtToken)
        val response = kycService.getCustomerInfo(request)

        assertEquals(CustomerStatus.NEEDS_INFO, response.status)
        assertNotNull(response.fields)
        assertEquals(2, response.fields!!.size)
    }

    @Test
    fun testGetCustomerInfoProcessingStatus() = runTest {
        val responseJson = """
            {
                "id": "$customerId",
                "status": "PROCESSING",
                "message": "Photo ID requires manual review. This process typically takes 1-2 business days.",
                "provided_fields": {
                    "photo_id_front": {
                        "description": "A clear photo of the front of the government issued ID",
                        "type": "binary",
                        "status": "PROCESSING"
                    }
                }
            }
        """.trimIndent()

        val mockClient = createMockClient(responseJson)
        val kycService = KYCService(serviceAddress, mockClient)

        val request = GetCustomerInfoRequest(jwt = jwtToken, id = customerId)
        val response = kycService.getCustomerInfo(request)

        assertEquals(CustomerStatus.PROCESSING, response.status)
        assertEquals("Photo ID requires manual review. This process typically takes 1-2 business days.", response.message)
    }

    @Test
    fun testGetCustomerInfoRejectedStatus() = runTest {
        val responseJson = """
            {
                "id": "$customerId",
                "status": "REJECTED",
                "message": "This person is on a sanctions list"
            }
        """.trimIndent()

        val mockClient = createMockClient(responseJson)
        val kycService = KYCService(serviceAddress, mockClient)

        val request = GetCustomerInfoRequest(jwt = jwtToken, id = customerId)
        val response = kycService.getCustomerInfo(request)

        assertEquals(CustomerStatus.REJECTED, response.status)
        assertEquals("This person is on a sanctions list", response.message)
    }

    @Test
    fun testPutCustomerInfoSuccess() = runTest {
        val responseJson = """{"id": "$customerId"}"""

        val mockClient = createMockClient(
            responseContent = responseJson,
            expectedMethod = HttpMethod.Put
        )
        val kycService = KYCService(serviceAddress, mockClient)

        val request = PutCustomerInfoRequest(
            jwt = jwtToken,
            kycFields = StandardKYCFields(
                naturalPersonKYCFields = NaturalPersonKYCFields(
                    firstName = "John",
                    lastName = "Doe"
                )
            )
        )
        val response = kycService.putCustomerInfo(request)

        assertEquals(customerId, response.id)
    }

    @Test
    fun testPutCustomerInfoWithSEP09Fields() = runTest {
        val responseJson = """{"id": "$customerId"}"""

        val mockClient = createMockClient(
            responseContent = responseJson,
            expectedMethod = HttpMethod.Put
        )
        val kycService = KYCService(serviceAddress, mockClient)

        val request = PutCustomerInfoRequest(
            jwt = jwtToken,
            kycFields = StandardKYCFields(
                naturalPersonKYCFields = NaturalPersonKYCFields(
                    firstName = "John",
                    lastName = "Doe",
                    emailAddress = "john@example.com",
                    birthDate = LocalDate(1990, 1, 15)
                )
            )
        )
        val response = kycService.putCustomerInfo(request)

        assertEquals(customerId, response.id)
    }

    @Test
    fun testPutCustomerInfoWithVerificationFields() = runTest {
        val responseJson = """{"id": "$customerId"}"""

        val mockClient = createMockClient(
            responseContent = responseJson,
            expectedMethod = HttpMethod.Put
        )
        val kycService = KYCService(serviceAddress, mockClient)

        val request = PutCustomerInfoRequest(
            jwt = jwtToken,
            id = customerId,
            verificationFields = mapOf(
                "email_address_verification" to "123456",
                "mobile_number_verification" to "654321"
            )
        )
        val response = kycService.putCustomerInfo(request)

        assertEquals(customerId, response.id)
    }

    @Test
    fun testPutCustomerInfoWithFileReferences() = runTest {
        val responseJson = """{"id": "$customerId"}"""

        val mockClient = createMockClient(
            responseContent = responseJson,
            expectedMethod = HttpMethod.Put
        )
        val kycService = KYCService(serviceAddress, mockClient)

        val request = PutCustomerInfoRequest(
            jwt = jwtToken,
            id = customerId,
            fileReferences = mapOf(
                "photo_id_front_file_id" to "file_abc123",
                "photo_id_back_file_id" to "file_def456"
            )
        )
        val response = kycService.putCustomerInfo(request)

        assertEquals(customerId, response.id)
    }

    @Test
    fun testPutCustomerInfoWithBinaryFieldsSendsMultipart() = runTest {
        val responseJson = """{"id": "$customerId"}"""

        // Sample binary data for photo ID
        val photoIdFrontBytes = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A) // PNG header
        val photoIdBackBytes = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte()) // JPEG header

        var contentTypeVerified = false
        var authHeaderVerified = false

        val mockEngine = MockEngine { request ->
            if (request.url.encodedPath.contains("/customer")) {
                if (request.method == HttpMethod.Put) {
                    // Verify Authorization header
                    val authHeader = request.headers["Authorization"]
                    authHeaderVerified = authHeader?.contains("Bearer $jwtToken") == true

                    // Verify Content-Type is multipart/form-data
                    val body = request.body
                    if (body is OutgoingContent.WriteChannelContent) {
                        val contentTypeHeader = body.contentType?.toString() ?: ""
                        contentTypeVerified = contentTypeHeader.startsWith("multipart/form-data")
                    }

                    respond(
                        content = responseJson,
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json")
                    )
                } else {
                    respond(
                        content = """{"error": "Method not allowed"}""",
                        status = HttpStatusCode.MethodNotAllowed,
                        headers = headersOf(HttpHeaders.ContentType, "application/json")
                    )
                }
            } else {
                respond(
                    content = """{"error": "Not found"}""",
                    status = HttpStatusCode.NotFound,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            }
        }

        val mockClient = HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                })
            }
        }

        val kycService = KYCService(serviceAddress, mockClient)

        val request = PutCustomerInfoRequest(
            jwt = jwtToken,
            kycFields = StandardKYCFields(
                naturalPersonKYCFields = NaturalPersonKYCFields(
                    firstName = "John",
                    lastName = "Doe",
                    emailAddress = "john@example.com",
                    photoIdFront = photoIdFrontBytes,
                    photoIdBack = photoIdBackBytes
                )
            )
        )

        val response = kycService.putCustomerInfo(request)

        assertEquals(customerId, response.id)
        assertTrue(contentTypeVerified, "Content-Type should be multipart/form-data")
        assertTrue(authHeaderVerified, "Authorization header should be present")
    }

    @Test
    fun testPutCustomerInfoWithCustomFilesSendsMultipart() = runTest {
        val responseJson = """{"id": "$customerId"}"""

        // Sample binary data for custom files
        val customDocumentBytes = byteArrayOf(0x25, 0x50, 0x44, 0x46) // PDF header

        var contentTypeVerified = false

        val mockEngine = MockEngine { request ->
            if (request.url.encodedPath.contains("/customer")) {
                if (request.method == HttpMethod.Put) {
                    // Verify Content-Type is multipart/form-data
                    val body = request.body
                    if (body is OutgoingContent.WriteChannelContent) {
                        val contentTypeHeader = body.contentType?.toString() ?: ""
                        contentTypeVerified = contentTypeHeader.startsWith("multipart/form-data")
                    }

                    respond(
                        content = responseJson,
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json")
                    )
                } else {
                    respond(
                        content = """{"error": "Method not allowed"}""",
                        status = HttpStatusCode.MethodNotAllowed,
                        headers = headersOf(HttpHeaders.ContentType, "application/json")
                    )
                }
            } else {
                respond(
                    content = """{"error": "Not found"}""",
                    status = HttpStatusCode.NotFound,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            }
        }

        val mockClient = HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                })
            }
        }

        val kycService = KYCService(serviceAddress, mockClient)

        val request = PutCustomerInfoRequest(
            jwt = jwtToken,
            kycFields = StandardKYCFields(
                naturalPersonKYCFields = NaturalPersonKYCFields(
                    firstName = "John",
                    lastName = "Doe"
                )
            ),
            customFiles = mapOf(
                "custom_document" to customDocumentBytes,
                "additional_proof" to byteArrayOf(1, 2, 3, 4, 5)
            )
        )

        val response = kycService.putCustomerInfo(request)

        assertEquals(customerId, response.id)
        assertTrue(contentTypeVerified, "Content-Type should be multipart/form-data")
    }

    @Test
    fun testPutCustomerInfoMultipartContentType() = runTest {
        val responseJson = """{"id": "$customerId"}"""

        val photoIdBytes = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47) // PNG header

        var actualContentType: String? = null

        val mockEngine = MockEngine { request ->
            if (request.url.encodedPath.contains("/customer")) {
                if (request.method == HttpMethod.Put) {
                    // Capture the Content-Type header
                    val body = request.body
                    if (body is OutgoingContent.WriteChannelContent) {
                        actualContentType = body.contentType?.toString()
                    }

                    respond(
                        content = responseJson,
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json")
                    )
                } else {
                    respond(
                        content = """{"error": "Method not allowed"}""",
                        status = HttpStatusCode.MethodNotAllowed,
                        headers = headersOf(HttpHeaders.ContentType, "application/json")
                    )
                }
            } else {
                respond(
                    content = """{"error": "Not found"}""",
                    status = HttpStatusCode.NotFound,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            }
        }

        val mockClient = HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                })
            }
        }

        val kycService = KYCService(serviceAddress, mockClient)

        val request = PutCustomerInfoRequest(
            jwt = jwtToken,
            kycFields = StandardKYCFields(
                naturalPersonKYCFields = NaturalPersonKYCFields(
                    firstName = "John",
                    lastName = "Doe",
                    photoIdFront = photoIdBytes
                )
            )
        )

        val response = kycService.putCustomerInfo(request)

        assertEquals(customerId, response.id)
        assertNotNull(actualContentType, "Content-Type should be set")
        assertTrue(
            actualContentType!!.startsWith("multipart/form-data"),
            "Content-Type should start with multipart/form-data, but was: $actualContentType"
        )
        assertTrue(
            actualContentType!!.contains("boundary="),
            "Content-Type should contain boundary parameter, but was: $actualContentType"
        )
    }

    @Test
    fun testPutCustomerInfoWithOnlyTextFieldsStillSendsMultipart() = runTest {
        val responseJson = """{"id": "$customerId"}"""

        var contentTypeVerified = false

        val mockEngine = MockEngine { request ->
            if (request.url.encodedPath.contains("/customer")) {
                if (request.method == HttpMethod.Put) {
                    // Verify Content-Type is multipart/form-data even without binary files
                    val body = request.body
                    if (body is OutgoingContent.WriteChannelContent) {
                        val contentTypeHeader = body.contentType?.toString() ?: ""
                        contentTypeVerified = contentTypeHeader.startsWith("multipart/form-data")
                    }

                    respond(
                        content = responseJson,
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json")
                    )
                } else {
                    respond(
                        content = """{"error": "Method not allowed"}""",
                        status = HttpStatusCode.MethodNotAllowed,
                        headers = headersOf(HttpHeaders.ContentType, "application/json")
                    )
                }
            } else {
                respond(
                    content = """{"error": "Not found"}""",
                    status = HttpStatusCode.NotFound,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            }
        }

        val mockClient = HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                })
            }
        }

        val kycService = KYCService(serviceAddress, mockClient)

        val request = PutCustomerInfoRequest(
            jwt = jwtToken,
            kycFields = StandardKYCFields(
                naturalPersonKYCFields = NaturalPersonKYCFields(
                    firstName = "John",
                    lastName = "Doe",
                    emailAddress = "john@example.com",
                    birthDate = LocalDate(1990, 1, 15)
                )
            )
        )

        val response = kycService.putCustomerInfo(request)

        assertEquals(customerId, response.id)
        assertTrue(
            contentTypeVerified,
            "Content-Type should be multipart/form-data even for text-only requests"
        )
    }

    @Test
    fun testPutCustomerInfoWithMixedSEP09AndCustomFields() = runTest {
        val responseJson = """{"id": "$customerId"}"""

        val photoBytes = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47)
        val documentBytes = byteArrayOf(0x25, 0x50, 0x44, 0x46)

        var contentTypeVerified = false

        val mockEngine = MockEngine { request ->
            if (request.url.encodedPath.contains("/customer")) {
                if (request.method == HttpMethod.Put) {
                    val body = request.body
                    if (body is OutgoingContent.WriteChannelContent) {
                        val contentTypeHeader = body.contentType?.toString() ?: ""
                        contentTypeVerified = contentTypeHeader.startsWith("multipart/form-data")
                    }

                    respond(
                        content = responseJson,
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json")
                    )
                } else {
                    respond(
                        content = """{"error": "Method not allowed"}""",
                        status = HttpStatusCode.MethodNotAllowed,
                        headers = headersOf(HttpHeaders.ContentType, "application/json")
                    )
                }
            } else {
                respond(
                    content = """{"error": "Not found"}""",
                    status = HttpStatusCode.NotFound,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            }
        }

        val mockClient = HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                })
            }
        }

        val kycService = KYCService(serviceAddress, mockClient)

        val request = PutCustomerInfoRequest(
            jwt = jwtToken,
            id = customerId,
            type = "sep31-sender",
            kycFields = StandardKYCFields(
                naturalPersonKYCFields = NaturalPersonKYCFields(
                    firstName = "John",
                    lastName = "Doe",
                    emailAddress = "john@example.com",
                    photoIdFront = photoBytes
                )
            ),
            customFields = mapOf(
                "custom_field_1" to "value1",
                "custom_field_2" to "value2"
            ),
            customFiles = mapOf(
                "custom_document" to documentBytes
            )
        )

        val response = kycService.putCustomerInfo(request)

        assertEquals(customerId, response.id)
        assertTrue(contentTypeVerified, "Content-Type should be multipart/form-data")
    }

    @Test
    fun testDeleteCustomerSuccess() = runTest {
        val mockClient = createMockClient(
            responseContent = "",
            expectedPath = "/customer/$accountId",
            expectedMethod = HttpMethod.Delete
        )
        val kycService = KYCService(serviceAddress, mockClient)

        val response = kycService.deleteCustomer(
            account = accountId,
            jwt = jwtToken
        )

        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun testDeleteCustomerWithMemo() = runTest {
        val mockClient = createMockClient(
            responseContent = "",
            expectedPath = "/customer/$accountId",
            expectedMethod = HttpMethod.Delete
        )
        val kycService = KYCService(serviceAddress, mockClient)

        val response = kycService.deleteCustomer(
            account = accountId,
            memo = "123",
            memoType = "id",
            jwt = jwtToken
        )

        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun testPutCustomerCallbackSuccess() = runTest {
        val mockClient = createMockClient(
            responseContent = "",
            expectedPath = "/customer/callback",
            expectedMethod = HttpMethod.Put
        )
        val kycService = KYCService(serviceAddress, mockClient)

        val request = PutCustomerCallbackRequest(
            jwt = jwtToken,
            url = "https://myapp.com/webhook",
            account = accountId
        )
        val response = kycService.putCustomerCallback(request)

        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun testPostCustomerFileSuccess() = runTest {
        val responseJson = """
            {
                "file_id": "file_d3d54529-6683-4341-9b66-4ac7d7504238",
                "content_type": "image/jpeg",
                "size": 4089371,
                "customer_id": "2bf95490-db23-442d-a1bd-c6fd5efb584e"
            }
        """.trimIndent()

        val mockClient = createMockClient(
            responseContent = responseJson,
            expectedPath = "/customer/files",
            expectedMethod = HttpMethod.Post
        )
        val kycService = KYCService(serviceAddress, mockClient)

        val fileBytes = byteArrayOf(1, 2, 3, 4, 5)
        val response = kycService.postCustomerFile(fileBytes, jwtToken)

        assertEquals("file_d3d54529-6683-4341-9b66-4ac7d7504238", response.fileId)
        assertEquals("image/jpeg", response.contentType)
        assertEquals(4089371L, response.size)
        assertEquals("2bf95490-db23-442d-a1bd-c6fd5efb584e", response.customerId)
    }

    @Test
    fun testPostCustomerFile413Error() = runTest {
        val mockClient = createMockClient(
            responseContent = """{"error": "File too large"}""",
            statusCode = HttpStatusCode.PayloadTooLarge,
            expectedPath = "/customer/files",
            expectedMethod = HttpMethod.Post
        )
        val kycService = KYCService(serviceAddress, mockClient)

        val fileBytes = byteArrayOf(1, 2, 3, 4, 5)
        assertFailsWith<FileTooLargeException> {
            kycService.postCustomerFile(fileBytes, jwtToken)
        }
    }

    @Test
    fun testGetCustomerFilesSuccess() = runTest {
        val responseJson = """
            {
                "files": [
                    {
                        "file_id": "file_d5c67b4c-173c-428c-baab-944f4b89a57f",
                        "content_type": "image/png",
                        "size": 6134063,
                        "customer_id": "2bf95490-db23-442d-a1bd-c6fd5efb584e"
                    },
                    {
                        "file_id": "file_d3d54529-6683-4341-9b66-4ac7d7504238",
                        "content_type": "image/jpeg",
                        "size": 4089371,
                        "customer_id": "2bf95490-db23-442d-a1bd-c6fd5efb584e"
                    }
                ]
            }
        """.trimIndent()

        val mockClient = createMockClient(
            responseContent = responseJson,
            expectedPath = "/customer/files"
        )
        val kycService = KYCService(serviceAddress, mockClient)

        val response = kycService.getCustomerFiles(
            jwt = jwtToken,
            customerId = customerId
        )

        assertEquals(2, response.files.size)
        assertEquals("file_d5c67b4c-173c-428c-baab-944f4b89a57f", response.files[0].fileId)
        assertEquals("image/png", response.files[0].contentType)
    }

    @Test
    fun testGetCustomerFilesEmptyList() = runTest {
        val responseJson = """{"files": []}"""

        val mockClient = createMockClient(
            responseContent = responseJson,
            expectedPath = "/customer/files"
        )
        val kycService = KYCService(serviceAddress, mockClient)

        val response = kycService.getCustomerFiles(jwt = jwtToken)

        assertTrue(response.files.isEmpty())
    }

    @Test
    fun test401ErrorThrowsUnauthorizedException() = runTest {
        val mockClient = createMockClient(
            responseContent = """{"error": "Unauthorized"}""",
            statusCode = HttpStatusCode.Unauthorized
        )
        val kycService = KYCService(serviceAddress, mockClient)

        val request = GetCustomerInfoRequest(jwt = "invalid_token")
        assertFailsWith<UnauthorizedException> {
            kycService.getCustomerInfo(request)
        }
    }

    @Test
    fun test404ErrorThrowsCustomerNotFoundException() = runTest {
        val mockClient = createMockClient(
            responseContent = """{"error": "Customer not found"}""",
            statusCode = HttpStatusCode.NotFound
        )
        val kycService = KYCService(serviceAddress, mockClient)

        val request = GetCustomerInfoRequest(jwt = jwtToken, id = "nonexistent")
        assertFailsWith<CustomerNotFoundException> {
            kycService.getCustomerInfo(request)
        }
    }

    @Test
    fun test400ErrorThrowsInvalidFieldException() = runTest {
        val mockClient = createMockClient(
            responseContent = """{"error": "Invalid email address"}""",
            statusCode = HttpStatusCode.BadRequest,
            expectedMethod = HttpMethod.Put
        )
        val kycService = KYCService(serviceAddress, mockClient)

        val request = PutCustomerInfoRequest(
            jwt = jwtToken,
            kycFields = StandardKYCFields(
                naturalPersonKYCFields = NaturalPersonKYCFields(
                    emailAddress = "invalid-email"
                )
            )
        )
        assertFailsWith<InvalidFieldException> {
            kycService.putCustomerInfo(request)
        }
    }

    // ========== Request Recording Helpers ==========

    private class RecordedRequest {
        var method: HttpMethod? = null
        var encodedPath: String = ""
        var queryParameters: Parameters = Parameters.Empty
        var headers: Headers = Headers.Empty
        var body: String = ""

        val authorization: String?
            get() = headers["Authorization"]
    }

    /**
     * Mock client that records the outgoing request and always answers with the given payload.
     */
    private fun recordingMockClient(
        recorded: RecordedRequest,
        responseContent: String,
        statusCode: HttpStatusCode = HttpStatusCode.OK
    ): HttpClient {
        val mockEngine = MockEngine { request ->
            recorded.method = request.method
            recorded.encodedPath = request.url.encodedPath
            recorded.queryParameters = request.url.parameters
            recorded.headers = request.headers
            recorded.body = request.body.toByteArray().decodeToString()

            respond(
                content = responseContent,
                status = statusCode,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }

        return HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                })
            }
        }
    }

    // ========== Request Construction Tests ==========

    @Test
    fun testCustomHeadersAreAppliedToRequests() = runTest {
        val recorded = RecordedRequest()
        val mockClient = recordingMockClient(recorded, """{"status": "ACCEPTED"}""")
        val kycService = KYCService(
            serviceAddress,
            mockClient,
            mapOf(
                "X-Client-Name" to "kmp-stellar-sdk",
                "X-Client-Version" to "1.2.3"
            )
        )

        val response = kycService.getCustomerInfo(GetCustomerInfoRequest(jwt = jwtToken))

        assertEquals(CustomerStatus.ACCEPTED, response.status)
        assertEquals("kmp-stellar-sdk", recorded.headers["X-Client-Name"])
        assertEquals("1.2.3", recorded.headers["X-Client-Version"])
        assertEquals("Bearer $jwtToken", recorded.authorization)
    }

    @Test
    fun testPutCustomerInfoSendsAccountMemoAndTransactionId() = runTest {
        val recorded = RecordedRequest()
        val mockClient = recordingMockClient(recorded, """{"id": "$customerId"}""")
        val kycService = KYCService(serviceAddress, mockClient)

        val response = kycService.putCustomerInfo(
            PutCustomerInfoRequest(
                jwt = jwtToken,
                account = accountId,
                memo = "1234567890",
                memoType = "id",
                type = "sep6-deposit",
                transactionId = "82fhs729f63dh0v4"
            )
        )

        assertEquals(customerId, response.id)
        assertEquals(HttpMethod.Put, recorded.method)
        assertEquals("/kyc/customer", recorded.encodedPath)
        assertTrue(recorded.body.contains("account"), "account key missing from body")
        assertTrue(recorded.body.contains(accountId), "account value missing from body")
        assertTrue(recorded.body.contains("memo_type"), "memo_type key missing from body")
        assertTrue(recorded.body.contains("1234567890"), "memo value missing from body")
        assertTrue(recorded.body.contains("sep6-deposit"), "type value missing from body")
        assertTrue(recorded.body.contains("82fhs729f63dh0v4"), "transaction id missing from body")
    }

    @Test
    fun testPutCustomerInfoOmitsUnsetOptionalFields() = runTest {
        val recorded = RecordedRequest()
        val mockClient = recordingMockClient(recorded, """{"id": "$customerId"}""")
        val kycService = KYCService(serviceAddress, mockClient)

        val response = kycService.putCustomerInfo(
            PutCustomerInfoRequest(
                jwt = jwtToken,
                kycFields = StandardKYCFields(
                    naturalPersonKYCFields = NaturalPersonKYCFields(firstName = "John")
                )
            )
        )

        assertEquals(customerId, response.id)
        assertTrue(recorded.body.contains("first_name"), "SEP-09 field missing from body")
        assertFalse(recorded.body.contains(accountId), "account must not be sent when unset")
        assertFalse(recorded.body.contains("memo_type"), "memo_type must not be sent when unset")
    }

    @Test
    fun testPutCustomerCallbackSendsAllIdentifiers() = runTest {
        val recorded = RecordedRequest()
        val mockClient = recordingMockClient(recorded, "")
        val kycService = KYCService(serviceAddress, mockClient)

        val response = kycService.putCustomerCallback(
            PutCustomerCallbackRequest(
                jwt = jwtToken,
                url = "https://myapp.com/webhook",
                id = customerId,
                account = accountId,
                memo = "1234567890"
            )
        )

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(HttpMethod.Put, recorded.method)
        assertEquals("/kyc/customer/callback", recorded.encodedPath)
        assertTrue(recorded.body.contains("https://myapp.com/webhook"), "url missing from body")
        assertTrue(recorded.body.contains(customerId), "id missing from body")
        assertTrue(recorded.body.contains(accountId), "account missing from body")
        assertTrue(recorded.body.contains("1234567890"), "memo missing from body")
    }

    @Test
    fun testPutCustomerCallbackWithUrlOnly() = runTest {
        val recorded = RecordedRequest()
        val mockClient = recordingMockClient(recorded, "")
        val kycService = KYCService(serviceAddress, mockClient)

        val response = kycService.putCustomerCallback(
            PutCustomerCallbackRequest(
                jwt = jwtToken,
                url = "https://myapp.com/webhook"
            )
        )

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(recorded.body.contains("https://myapp.com/webhook"), "url missing from body")
        assertFalse(recorded.body.contains(customerId), "id must not be sent when unset")
        assertFalse(recorded.body.contains(accountId), "account must not be sent when unset")
    }

    @Test
    fun testGetCustomerFilesByFileId() = runTest {
        val recorded = RecordedRequest()
        val responseJson = """
            {
                "files": [
                    {
                        "file_id": "file_d3d54529-6683-4341-9b66-4ac7d7504238",
                        "content_type": "image/jpeg",
                        "size": 4089371
                    }
                ]
            }
        """.trimIndent()
        val mockClient = recordingMockClient(recorded, responseJson)
        val kycService = KYCService(serviceAddress, mockClient)

        val response = kycService.getCustomerFiles(
            jwt = jwtToken,
            fileId = "file_d3d54529-6683-4341-9b66-4ac7d7504238"
        )

        assertEquals(1, response.files.size)
        assertEquals("/kyc/customer/files", recorded.encodedPath)
        assertEquals(
            "file_d3d54529-6683-4341-9b66-4ac7d7504238",
            recorded.queryParameters["file_id"]
        )
        assertNull(recorded.queryParameters["customer_id"])
    }

    @Test
    fun testDeleteCustomerSendsMemoQueryParameters() = runTest {
        val recorded = RecordedRequest()
        val mockClient = recordingMockClient(recorded, "")
        val kycService = KYCService(serviceAddress, mockClient)

        val response = kycService.deleteCustomer(
            account = accountId,
            memo = "1234567890",
            memoType = "id",
            jwt = jwtToken
        )

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(HttpMethod.Delete, recorded.method)
        assertEquals("/kyc/customer/$accountId", recorded.encodedPath)
        assertEquals("1234567890", recorded.queryParameters["memo"])
        assertEquals("id", recorded.queryParameters["memo_type"])
    }

    @Suppress("DEPRECATION")
    @Test
    fun testPutCustomerVerificationSubmitsCodes() = runTest {
        val recorded = RecordedRequest()
        val responseJson = """
            {
                "id": "$customerId",
                "status": "ACCEPTED"
            }
        """.trimIndent()
        val mockClient = recordingMockClient(recorded, responseJson)
        val kycService = KYCService(serviceAddress, mockClient)

        val response = kycService.putCustomerVerification(
            PutCustomerVerificationRequest(
                jwt = jwtToken,
                id = customerId,
                verificationFields = mapOf(
                    "email_address_verification" to "123456",
                    "mobile_number_verification" to "654321"
                )
            )
        )

        assertEquals(customerId, response.id)
        assertEquals(CustomerStatus.ACCEPTED, response.status)
        assertEquals(HttpMethod.Put, recorded.method)
        assertEquals("/kyc/customer/verification", recorded.encodedPath)
        assertEquals("Bearer $jwtToken", recorded.authorization)
        assertTrue(recorded.body.contains(customerId), "customer id missing from body")
        assertTrue(
            recorded.body.contains("email_address_verification"),
            "email verification key missing from body"
        )
        assertTrue(recorded.body.contains("123456"), "email verification code missing from body")
        assertTrue(
            recorded.body.contains("mobile_number_verification"),
            "mobile verification key missing from body"
        )
        assertTrue(recorded.body.contains("654321"), "mobile verification code missing from body")
    }

    // ========== Response Status Handling Tests ==========

    @Test
    fun testAcceptedStatusIsParsedAsSuccess() = runTest {
        val mockClient = createMockClient(
            responseContent = """{"id": "$customerId"}""",
            statusCode = HttpStatusCode.Accepted,
            expectedMethod = HttpMethod.Put
        )
        val kycService = KYCService(serviceAddress, mockClient)

        val response = kycService.putCustomerInfo(
            PutCustomerInfoRequest(
                jwt = jwtToken,
                kycFields = StandardKYCFields(
                    naturalPersonKYCFields = NaturalPersonKYCFields(firstName = "John")
                )
            )
        )

        assertEquals(customerId, response.id)
    }

    @Test
    fun test409ErrorThrowsCustomerAlreadyExistsExceptionWithId() = runTest {
        val mockClient = createMockClient(
            responseContent = """{"error": "customer already registered with id: $customerId"}""",
            statusCode = HttpStatusCode.Conflict,
            expectedMethod = HttpMethod.Put
        )
        val kycService = KYCService(serviceAddress, mockClient)

        val exception = assertFailsWith<CustomerAlreadyExistsException> {
            kycService.putCustomerInfo(
                PutCustomerInfoRequest(
                    jwt = jwtToken,
                    kycFields = StandardKYCFields(
                        naturalPersonKYCFields = NaturalPersonKYCFields(firstName = "John")
                    )
                )
            )
        }

        assertEquals(customerId, exception.existingCustomerId)
        assertTrue(exception.message!!.contains(customerId))
    }

    @Test
    fun test409ErrorWithoutCustomerIdInBody() = runTest {
        val mockClient = createMockClient(
            responseContent = """{"error": "conflict"}""",
            statusCode = HttpStatusCode.Conflict,
            expectedMethod = HttpMethod.Put
        )
        val kycService = KYCService(serviceAddress, mockClient)

        val exception = assertFailsWith<CustomerAlreadyExistsException> {
            kycService.putCustomerInfo(
                PutCustomerInfoRequest(
                    jwt = jwtToken,
                    kycFields = StandardKYCFields(
                        naturalPersonKYCFields = NaturalPersonKYCFields(firstName = "John")
                    )
                )
            )
        }

        assertNull(exception.existingCustomerId)
    }

    @Test
    fun testUnmappedStatusThrowsKYCExceptionWithStatusAndBody() = runTest {
        val mockClient = createMockClient(
            responseContent = """{"error": "anchor database unavailable"}""",
            statusCode = HttpStatusCode.InternalServerError
        )
        val kycService = KYCService(serviceAddress, mockClient)

        val exception = assertFailsWith<KYCException> {
            kycService.getCustomerInfo(GetCustomerInfoRequest(jwt = jwtToken))
        }

        val message = exception.message
        assertNotNull(message)
        assertTrue(message.contains("HTTP 500"), "status code missing from message: $message")
        assertTrue(
            message.contains("anchor database unavailable"),
            "response body missing from message: $message"
        )
    }

    @Test
    fun test400ErrorExtractsFieldNameFromMessage() = runTest {
        val mockClient = createMockClient(
            responseContent = """{"error": "field: last_name is required"}""",
            statusCode = HttpStatusCode.BadRequest,
            expectedMethod = HttpMethod.Put
        )
        val kycService = KYCService(serviceAddress, mockClient)

        val exception = assertFailsWith<InvalidFieldException> {
            kycService.putCustomerInfo(
                PutCustomerInfoRequest(
                    jwt = jwtToken,
                    kycFields = StandardKYCFields(
                        naturalPersonKYCFields = NaturalPersonKYCFields(firstName = "John")
                    )
                )
            )
        }

        assertEquals("last_name", exception.fieldName)
        assertEquals("field: last_name is required", exception.fieldError)
    }

    @Test
    fun test400ErrorWithoutErrorKeyUsesRawBody() = runTest {
        val body = """{"detail": "the submitted data was rejected"}"""
        val mockClient = createMockClient(
            responseContent = body,
            statusCode = HttpStatusCode.BadRequest,
            expectedMethod = HttpMethod.Put
        )
        val kycService = KYCService(serviceAddress, mockClient)

        val exception = assertFailsWith<InvalidFieldException> {
            kycService.putCustomerInfo(
                PutCustomerInfoRequest(
                    jwt = jwtToken,
                    kycFields = StandardKYCFields(
                        naturalPersonKYCFields = NaturalPersonKYCFields(firstName = "John")
                    )
                )
            )
        }

        assertEquals(body, exception.fieldError)
        assertNull(exception.fieldName)
    }

    @Test
    fun test400ErrorWithNonJsonBodyUsesRawBody() = runTest {
        val body = "Bad Request - the anchor could not parse the submission"
        val mockClient = createMockClient(
            responseContent = body,
            statusCode = HttpStatusCode.BadRequest,
            expectedMethod = HttpMethod.Put,
            contentType = "text/plain"
        )
        val kycService = KYCService(serviceAddress, mockClient)

        val exception = assertFailsWith<InvalidFieldException> {
            kycService.putCustomerInfo(
                PutCustomerInfoRequest(
                    jwt = jwtToken,
                    kycFields = StandardKYCFields(
                        naturalPersonKYCFields = NaturalPersonKYCFields(firstName = "John")
                    )
                )
            )
        }

        assertEquals(body, exception.fieldError)
    }

    @Test
    fun test404ErrorExtractsAccountIdFromBody() = runTest {
        val mockClient = createMockClient(
            responseContent = """{"error": "no customer registered for $accountId"}""",
            statusCode = HttpStatusCode.NotFound
        )
        val kycService = KYCService(serviceAddress, mockClient)

        val exception = assertFailsWith<CustomerNotFoundException> {
            kycService.getCustomerInfo(GetCustomerInfoRequest(jwt = jwtToken, account = accountId))
        }

        assertEquals(accountId, exception.accountId)
        assertTrue(exception.message!!.contains(accountId))
    }

    @Test
    fun testFileTooLargeErrorReportsSizeInBytes() = runTest {
        val mockClient = createMockClient(
            responseContent = """{"error": "file exceeds the limit, received 4194304 bytes"}""",
            statusCode = HttpStatusCode.PayloadTooLarge,
            expectedPath = "/customer/files",
            expectedMethod = HttpMethod.Post
        )
        val kycService = KYCService(serviceAddress, mockClient)

        val exception = assertFailsWith<FileTooLargeException> {
            kycService.postCustomerFile(byteArrayOf(1, 2, 3), jwtToken)
        }

        assertEquals(4194304L, exception.fileSize)
    }

    @Test
    fun testFileTooLargeErrorConvertsMegabytesToBytes() = runTest {
        val mockClient = createMockClient(
            responseContent = """{"error": "maximum upload size is 5 MB"}""",
            statusCode = HttpStatusCode.PayloadTooLarge,
            expectedPath = "/customer/files",
            expectedMethod = HttpMethod.Post
        )
        val kycService = KYCService(serviceAddress, mockClient)

        val exception = assertFailsWith<FileTooLargeException> {
            kycService.postCustomerFile(byteArrayOf(1, 2, 3), jwtToken)
        }

        assertEquals(5L * 1024 * 1024, exception.fileSize)
    }

    @Test
    fun testFileTooLargeErrorWithUnrepresentableSize() = runTest {
        val mockClient = createMockClient(
            responseContent = """{"error": "received 99999999999999999999999 bytes"}""",
            statusCode = HttpStatusCode.PayloadTooLarge,
            expectedPath = "/customer/files",
            expectedMethod = HttpMethod.Post
        )
        val kycService = KYCService(serviceAddress, mockClient)

        val exception = assertFailsWith<FileTooLargeException> {
            kycService.postCustomerFile(byteArrayOf(1, 2, 3), jwtToken)
        }

        assertNull(exception.fileSize)
    }

    @Test
    fun testPutCustomerInfoWithOrganizationFields() = runTest {
        val responseJson = """{"id": "$customerId"}"""

        val mockClient = createMockClient(
            responseContent = responseJson,
            expectedMethod = HttpMethod.Put
        )
        val kycService = KYCService(serviceAddress, mockClient)

        val request = PutCustomerInfoRequest(
            jwt = jwtToken,
            type = "sep31-receiver",
            kycFields = StandardKYCFields(
                organizationKYCFields = OrganizationKYCFields(
                    name = "Acme Corp",
                    registrationNumber = "123456789"
                )
            )
        )
        val response = kycService.putCustomerInfo(request)

        assertEquals(customerId, response.id)
    }
}
