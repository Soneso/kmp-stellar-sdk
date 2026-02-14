// Copyright 2026 Soneso. All rights reserved.
// Use of this source code is governed by a license that can be
// found in the LICENSE file.

package com.soneso.stellar.sdk.unitTests.sep.sep24

import com.soneso.stellar.sdk.sep.sep24.*
import com.soneso.stellar.sdk.sep.sep24.exceptions.*
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.*

class Sep24ServiceTest {

    private val serviceAddress = "https://test.anchor.com"
    private val jwtToken = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.test"
    private val transactionId = "82fhs729f63dh0v4"

    // Mock JSON Responses

    private val infoResponseJson = """
        {
            "deposit": {
                "USDC": {
                    "enabled": true,
                    "min_amount": "1",
                    "max_amount": "10000",
                    "fee_fixed": "5",
                    "fee_percent": "1",
                    "fee_minimum": "0.1"
                },
                "native": {
                    "enabled": true,
                    "min_amount": "0.01",
                    "max_amount": "1000"
                }
            },
            "withdraw": {
                "USDC": {
                    "enabled": true,
                    "min_amount": "10",
                    "max_amount": "5000",
                    "fee_fixed": "3",
                    "fee_percent": "0.5"
                }
            },
            "fee": {
                "enabled": true,
                "authentication_required": true
            },
            "features": {
                "account_creation": true,
                "claimable_balances": true
            }
        }
    """.trimIndent()

    private val feeResponseJson = """
        {
            "fee": "5.42"
        }
    """.trimIndent()

    private val interactiveResponseJson = """
        {
            "type": "interactive_customer_info_needed",
            "url": "https://anchor.example.com/sep24/transaction/interactive",
            "id": "$transactionId"
        }
    """.trimIndent()

    private val singleTransactionResponseJson = """
        {
            "transaction": {
                "id": "$transactionId",
                "kind": "deposit",
                "status": "pending_user_transfer_start",
                "status_eta": 3600,
                "kyc_verified": true,
                "more_info_url": "https://anchor.example.com/tx/$transactionId",
                "amount_in": "100",
                "amount_in_asset": "iso4217:USD",
                "amount_out": "95.58",
                "amount_out_asset": "stellar:USDC:GA5ZSEJYB37JRC5AVCIA5MOP4RHTM335X2KGX3IHOJAPP5RE34K4KZVN",
                "amount_fee": "4.42",
                "amount_fee_asset": "iso4217:USD",
                "fee_details": {
                    "total": "4.42",
                    "asset": "iso4217:USD",
                    "breakdown": [
                        {
                            "name": "Service fee",
                            "amount": "1.00",
                            "description": "Processing fee"
                        },
                        {
                            "name": "Network fee",
                            "amount": "3.42",
                            "description": "Payment network charges"
                        }
                    ]
                },
                "quote_id": "de762cda-a193-4961-861e-57b31fed6eb3",
                "started_at": "2021-04-30T07:00:00Z",
                "updated_at": "2021-04-30T07:10:00Z",
                "completed_at": null,
                "user_action_required_by": "2021-04-30T08:00:00Z",
                "stellar_transaction_id": null,
                "external_transaction_id": "ext123",
                "message": "Please send payment to complete deposit",
                "refunded": false,
                "from": "user@email.com",
                "to": "GBXTNX3ZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZ",
                "deposit_memo": "12345",
                "deposit_memo_type": "id",
                "claimable_balance_id": null
            }
        }
    """.trimIndent()

    private val transactionWithRefundsJson = """
        {
            "transaction": {
                "id": "$transactionId",
                "kind": "withdraw",
                "status": "refunded",
                "amount_in": "100",
                "amount_in_asset": "stellar:USDC:GA5ZSEJYB37JRC5AVCIA5MOP4RHTM335X2KGX3IHOJAPP5RE34K4KZVN",
                "amount_out": "95",
                "amount_out_asset": "iso4217:USD",
                "refunds": {
                    "amount_refunded": "100",
                    "amount_fee": "5",
                    "payments": [
                        {
                            "id": "b9d0b2292c4e09e8eb22d036171491e87b8d2086bf8b265874c8d182cb9c9020",
                            "id_type": "stellar",
                            "amount": "50",
                            "fee": "2.5"
                        },
                        {
                            "id": "ext456",
                            "id_type": "external",
                            "amount": "50",
                            "fee": "2.5"
                        }
                    ]
                },
                "from": "GBXTNX3ZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZ",
                "to": "user@email.com",
                "withdraw_anchor_account": "GCANCHORACCOUNTZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZ",
                "withdraw_memo": "67890",
                "withdraw_memo_type": "text"
            }
        }
    """.trimIndent()

    private val multipleTransactionsResponseJson = """
        {
            "transactions": [
                {
                    "id": "tx1",
                    "kind": "deposit",
                    "status": "completed",
                    "amount_in": "100",
                    "amount_out": "95",
                    "started_at": "2021-04-30T07:00:00Z",
                    "completed_at": "2021-04-30T07:30:00Z"
                },
                {
                    "id": "tx2",
                    "kind": "withdrawal",
                    "status": "pending_stellar",
                    "amount_in": "50",
                    "amount_out": "48",
                    "started_at": "2021-04-30T08:00:00Z"
                },
                {
                    "id": "tx3",
                    "kind": "deposit",
                    "status": "error",
                    "message": "Payment timeout",
                    "started_at": "2021-04-29T10:00:00Z"
                }
            ]
        }
    """.trimIndent()

    private val emptyTransactionsResponseJson = """
        {
            "transactions": []
        }
    """.trimIndent()

    private fun createMockClient(
        responseContent: String,
        statusCode: HttpStatusCode = HttpStatusCode.OK,
        expectedPath: String,
        expectedMethod: HttpMethod = HttpMethod.Get,
        validateAuth: Boolean = false,
        validateParams: ((Parameters) -> Boolean)? = null
    ): HttpClient {
        val mockEngine = MockEngine { request ->
            if (request.url.encodedPath.contains(expectedPath)) {
                if (expectedMethod != request.method) {
                    respond(
                        content = """{"error": "Method not allowed"}""",
                        status = HttpStatusCode.MethodNotAllowed,
                        headers = headersOf(HttpHeaders.ContentType, "application/json")
                    )
                } else if (validateAuth && request.headers["Authorization"]?.contains("Bearer") != true) {
                    respond(
                        content = """{"error": "Unauthorized"}""",
                        status = HttpStatusCode.Unauthorized,
                        headers = headersOf(HttpHeaders.ContentType, "application/json")
                    )
                } else if (validateParams != null && !validateParams(request.url.parameters)) {
                    respond(
                        content = """{"error": "Invalid parameters"}""",
                        status = HttpStatusCode.BadRequest,
                        headers = headersOf(HttpHeaders.ContentType, "application/json")
                    )
                } else {
                    respond(
                        content = responseContent,
                        status = statusCode,
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

        return HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                })
            }
        }
    }

    // ========== info() Tests ==========

    @Test
    fun testInfoSuccess() = runTest {
        val mockClient = createMockClient(
            responseContent = infoResponseJson,
            expectedPath = "/info"
        )
        val service = Sep24Service(serviceAddress, mockClient)

        val response = service.info()

        assertNotNull(response.depositAssets)
        assertEquals(2, response.depositAssets!!.size)

        val usdcDeposit = response.depositAssets!!["USDC"]!!
        assertTrue(usdcDeposit.enabled)
        assertEquals("1", usdcDeposit.minAmount)
        assertEquals("10000", usdcDeposit.maxAmount)
        assertEquals("5", usdcDeposit.feeFixed)
        assertEquals("1", usdcDeposit.feePercent)
        assertEquals("0.1", usdcDeposit.feeMinimum)

        val nativeDeposit = response.depositAssets!!["native"]!!
        assertTrue(nativeDeposit.enabled)
        assertEquals("0.01", nativeDeposit.minAmount)
        assertEquals("1000", nativeDeposit.maxAmount)
        assertNull(nativeDeposit.feeFixed)

        assertNotNull(response.withdrawAssets)
        assertEquals(1, response.withdrawAssets!!.size)

        val usdcWithdraw = response.withdrawAssets!!["USDC"]!!
        assertTrue(usdcWithdraw.enabled)
        assertEquals("10", usdcWithdraw.minAmount)
        assertEquals("5000", usdcWithdraw.maxAmount)

        assertNotNull(response.feeEndpoint)
        assertTrue(response.feeEndpoint!!.enabled)
        assertTrue(response.feeEndpoint!!.authenticationRequired)

        assertNotNull(response.features)
        assertTrue(response.features!!.accountCreation)
        assertTrue(response.features!!.claimableBalances)
    }

    @Test
    fun testInfoWithLangParameter() = runTest {
        var langParamVerified = false
        val mockEngine = MockEngine { request ->
            if (request.url.encodedPath.contains("/info")) {
                val params = request.url.parameters
                langParamVerified = params["lang"] == "es"

                respond(
                    content = infoResponseJson,
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
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
        val service = Sep24Service(serviceAddress, mockClient)

        service.info(lang = "es")

        assertTrue(langParamVerified, "Language parameter should be included")
    }

    @Test
    fun testInfo400Error() = runTest {
        val mockClient = createMockClient(
            responseContent = """{"error": "Invalid request"}""",
            statusCode = HttpStatusCode.BadRequest,
            expectedPath = "/info"
        )
        val service = Sep24Service(serviceAddress, mockClient)

        val exception = assertFailsWith<Sep24InvalidRequestException> {
            service.info()
        }
        assertEquals("Invalid request", exception.message)
    }

    // ========== fee() Tests ==========

    @Test
    fun testFeeWithoutAuth() = runTest {
        var requestParamsVerified = false
        val mockEngine = MockEngine { request ->
            if (request.url.encodedPath.contains("/fee")) {
                val params = request.url.parameters
                requestParamsVerified = params["operation"] == "deposit" &&
                        params["asset_code"] == "USDC" &&
                        params["amount"] == "100"

                respond(
                    content = feeResponseJson,
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
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
        val service = Sep24Service(serviceAddress, mockClient)

        val response = service.fee(Sep24FeeRequest(
            operation = "deposit",
            assetCode = "USDC",
            amount = "100"
        ))

        assertEquals("5.42", response.fee)
        assertTrue(requestParamsVerified, "Request parameters should be correct")
    }

    @Test
    fun testFeeWithAuth() = runTest {
        var authHeaderVerified = false
        val mockEngine = MockEngine { request ->
            if (request.url.encodedPath.contains("/fee")) {
                val authHeader = request.headers["Authorization"]
                authHeaderVerified = authHeader?.contains("Bearer $jwtToken") == true

                respond(
                    content = feeResponseJson,
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
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
        val service = Sep24Service(serviceAddress, mockClient)

        val response = service.fee(Sep24FeeRequest(
            operation = "withdraw",
            assetCode = "USDC",
            amount = "50",
            jwt = jwtToken
        ))

        assertEquals("5.42", response.fee)
        assertTrue(authHeaderVerified, "Authorization header should be present")
    }

    @Test
    fun testFeeAuthRequired() = runTest {
        val mockClient = createMockClient(
            responseContent = """{"type": "authentication_required", "error": "Authentication required"}""",
            statusCode = HttpStatusCode.Forbidden,
            expectedPath = "/fee"
        )
        val service = Sep24Service(serviceAddress, mockClient)

        assertFailsWith<Sep24AuthenticationRequiredException> {
            service.fee(Sep24FeeRequest(
                operation = "deposit",
                assetCode = "USDC",
                amount = "100"
            ))
        }
    }

    // ========== deposit() Tests ==========

    @Test
    fun testDepositSuccess() = runTest {
        var authHeaderVerified = false
        var methodVerified = false
        val mockEngine = MockEngine { request ->
            if (request.url.encodedPath.contains("/transactions/deposit/interactive")) {
                if (request.method == HttpMethod.Post) {
                    val authHeader = request.headers["Authorization"]
                    authHeaderVerified = authHeader?.contains("Bearer $jwtToken") == true
                    methodVerified = true

                    respond(
                        content = interactiveResponseJson,
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
        val service = Sep24Service(serviceAddress, mockClient)

        val response = service.deposit(Sep24DepositRequest(
            assetCode = "USDC",
            jwt = jwtToken,
            account = "GBXTNX3ZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZ",
            amount = "100",
            claimableBalanceSupported = true
        ))

        assertEquals("interactive_customer_info_needed", response.type)
        assertEquals("https://anchor.example.com/sep24/transaction/interactive", response.url)
        assertEquals(transactionId, response.id)
        assertTrue(authHeaderVerified, "Authorization header should be present")
        assertTrue(methodVerified, "POST method should be used")
    }

    @Test
    fun testDepositWithAllFields() = runTest {
        val mockClient = createMockClient(
            responseContent = interactiveResponseJson,
            expectedPath = "/transactions/deposit/interactive",
            expectedMethod = HttpMethod.Post
        )
        val service = Sep24Service(serviceAddress, mockClient)

        val response = service.deposit(Sep24DepositRequest(
            assetCode = "USDC",
            jwt = jwtToken,
            assetIssuer = "GA5ZSEJYB37JRC5AVCIA5MOP4RHTM335X2KGX3IHOJAPP5RE34K4KZVN",
            sourceAsset = "iso4217:USD",
            amount = "100",
            quoteId = "de762cda-a193-4961-861e-57b31fed6eb3",
            account = "GBXTNX3ZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZ",
            memo = "test memo",
            memoType = "text",
            walletName = "Test Wallet",
            walletUrl = "https://wallet.example.com",
            lang = "en-US",
            claimableBalanceSupported = true,
            customerId = "cust123",
            kycFields = mapOf("first_name" to "John", "last_name" to "Doe")
        ))

        assertEquals(transactionId, response.id)
    }

    @Test
    fun testDeposit400Error() = runTest {
        val mockClient = createMockClient(
            responseContent = """{"error": "Invalid asset code"}""",
            statusCode = HttpStatusCode.BadRequest,
            expectedPath = "/transactions/deposit/interactive",
            expectedMethod = HttpMethod.Post
        )
        val service = Sep24Service(serviceAddress, mockClient)

        val exception = assertFailsWith<Sep24InvalidRequestException> {
            service.deposit(Sep24DepositRequest(
                assetCode = "INVALID",
                jwt = jwtToken
            ))
        }
        assertEquals("Invalid asset code", exception.message)
    }

    @Test
    fun testDeposit403AuthError() = runTest {
        val mockClient = createMockClient(
            responseContent = """{"type": "authentication_required", "error": "Invalid JWT"}""",
            statusCode = HttpStatusCode.Forbidden,
            expectedPath = "/transactions/deposit/interactive",
            expectedMethod = HttpMethod.Post
        )
        val service = Sep24Service(serviceAddress, mockClient)

        assertFailsWith<Sep24AuthenticationRequiredException> {
            service.deposit(Sep24DepositRequest(
                assetCode = "USDC",
                jwt = "invalid_token"
            ))
        }
    }

    @Test
    fun testDepositWithKycFieldsVerification() = runTest {
        // Verify that KYC fields are properly included in the multipart request body
        var requestBodyContent = ""
        val mockEngine = MockEngine { request ->
            if (request.url.encodedPath.contains("/transactions/deposit/interactive")) {
                // Capture the request body for verification
                requestBodyContent = request.body.toByteArray().decodeToString()

                respond(
                    content = interactiveResponseJson,
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
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
        val service = Sep24Service(serviceAddress, mockClient)

        val response = service.deposit(Sep24DepositRequest(
            assetCode = "USDC",
            jwt = jwtToken,
            account = "GCFXHS4GXL6BVUCXBWXGTITROWLVYXQKQLF4YH5O5JT3YZXCYPAFBJZB",
            kycFields = mapOf(
                "first_name" to "John",
                "last_name" to "Doe",
                "email_address" to "john.doe@example.com",
                "bank_account_number" to "1234567890"
            ),
            customerId = "customer-123"
        ))

        assertEquals("82fhs729f63dh0v4", response.id)

        // Verify KYC fields are present in the request body
        assertTrue(requestBodyContent.contains("first_name"), "Request should contain first_name field")
        assertTrue(requestBodyContent.contains("John"), "Request should contain first_name value")
        assertTrue(requestBodyContent.contains("last_name"), "Request should contain last_name field")
        assertTrue(requestBodyContent.contains("Doe"), "Request should contain last_name value")
        assertTrue(requestBodyContent.contains("email_address"), "Request should contain email_address field")
        assertTrue(requestBodyContent.contains("john.doe@example.com"), "Request should contain email_address value")
        assertTrue(requestBodyContent.contains("bank_account_number"), "Request should contain bank_account_number field")
        assertTrue(requestBodyContent.contains("1234567890"), "Request should contain bank_account_number value")
        assertTrue(requestBodyContent.contains("customer_id"), "Request should contain customer_id field")
        assertTrue(requestBodyContent.contains("customer-123"), "Request should contain customer_id value")
    }

    // ========== withdraw() Tests ==========

    @Test
    fun testWithdrawSuccess() = runTest {
        var authHeaderVerified = false
        val mockEngine = MockEngine { request ->
            if (request.url.encodedPath.contains("/transactions/withdraw/interactive")) {
                if (request.method == HttpMethod.Post) {
                    val authHeader = request.headers["Authorization"]
                    authHeaderVerified = authHeader?.contains("Bearer $jwtToken") == true

                    respond(
                        content = interactiveResponseJson,
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
        val service = Sep24Service(serviceAddress, mockClient)

        val response = service.withdraw(Sep24WithdrawRequest(
            assetCode = "USDC",
            jwt = jwtToken,
            amount = "50"
        ))

        assertEquals("interactive_customer_info_needed", response.type)
        assertEquals(transactionId, response.id)
        assertTrue(authHeaderVerified, "Authorization header should be present")
    }

    @Test
    fun testWithdrawWithRefundMemo() = runTest {
        val mockClient = createMockClient(
            responseContent = interactiveResponseJson,
            expectedPath = "/transactions/withdraw/interactive",
            expectedMethod = HttpMethod.Post
        )
        val service = Sep24Service(serviceAddress, mockClient)

        @Suppress("DEPRECATION")
        val response = service.withdraw(Sep24WithdrawRequest(
            assetCode = "USDC",
            jwt = jwtToken,
            amount = "50",
            account = "GBXTNX3ZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZ",
            memo = "deprecated memo",
            memoType = "text",
            refundMemo = "refund_memo_123",
            refundMemoType = "text"
        ))

        assertEquals(transactionId, response.id)
    }

    @Test
    fun testWithdraw400Error() = runTest {
        val mockClient = createMockClient(
            responseContent = """{"error": "Amount below minimum"}""",
            statusCode = HttpStatusCode.BadRequest,
            expectedPath = "/transactions/withdraw/interactive",
            expectedMethod = HttpMethod.Post
        )
        val service = Sep24Service(serviceAddress, mockClient)

        val exception = assertFailsWith<Sep24InvalidRequestException> {
            service.withdraw(Sep24WithdrawRequest(
                assetCode = "USDC",
                jwt = jwtToken,
                amount = "0.01"
            ))
        }
        assertEquals("Amount below minimum", exception.message)
    }

    @Test
    fun testWithdrawWithKycFieldsVerification() = runTest {
        // Verify that KYC fields are properly included in the multipart request body
        var requestBodyContent = ""
        val mockEngine = MockEngine { request ->
            if (request.url.encodedPath.contains("/transactions/withdraw/interactive")) {
                // Capture the request body for verification
                requestBodyContent = request.body.toByteArray().decodeToString()

                respond(
                    content = interactiveResponseJson,
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
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
        val service = Sep24Service(serviceAddress, mockClient)

        val response = service.withdraw(Sep24WithdrawRequest(
            assetCode = "USDC",
            jwt = jwtToken,
            account = "GCFXHS4GXL6BVUCXBWXGTITROWLVYXQKQLF4YH5O5JT3YZXCYPAFBJZB",
            kycFields = mapOf(
                "first_name" to "Jane",
                "last_name" to "Smith",
                "email_address" to "jane.smith@example.com",
                "organization.name" to "Acme Corp",
                "organization.bank_account_number" to "9876543210"
            ),
            customerId = "org-customer-456",
            refundMemo = "refund123",
            refundMemoType = "text"
        ))

        assertEquals("82fhs729f63dh0v4", response.id)

        // Verify KYC fields are present in the request body
        assertTrue(requestBodyContent.contains("first_name"), "Request should contain first_name field")
        assertTrue(requestBodyContent.contains("Jane"), "Request should contain first_name value")
        assertTrue(requestBodyContent.contains("last_name"), "Request should contain last_name field")
        assertTrue(requestBodyContent.contains("Smith"), "Request should contain last_name value")
        assertTrue(requestBodyContent.contains("email_address"), "Request should contain email_address field")
        assertTrue(requestBodyContent.contains("jane.smith@example.com"), "Request should contain email_address value")
        // Organization KYC fields (SEP-9)
        assertTrue(requestBodyContent.contains("organization.name"), "Request should contain organization.name field")
        assertTrue(requestBodyContent.contains("Acme Corp"), "Request should contain organization.name value")
        assertTrue(requestBodyContent.contains("organization.bank_account_number"), "Request should contain organization.bank_account_number field")
        assertTrue(requestBodyContent.contains("9876543210"), "Request should contain organization.bank_account_number value")
        // Customer ID and refund fields
        assertTrue(requestBodyContent.contains("customer_id"), "Request should contain customer_id field")
        assertTrue(requestBodyContent.contains("org-customer-456"), "Request should contain customer_id value")
        assertTrue(requestBodyContent.contains("refund_memo"), "Request should contain refund_memo field")
        assertTrue(requestBodyContent.contains("refund123"), "Request should contain refund_memo value")
    }

    // ========== transactions() Tests ==========

    @Test
    fun testTransactionsSuccess() = runTest {
        var requestParamsVerified = false
        val mockEngine = MockEngine { request ->
            if (request.url.encodedPath.contains("/transactions")) {
                val params = request.url.parameters
                requestParamsVerified = params["asset_code"] == "USDC" &&
                        params["limit"] == "10" &&
                        params["kind"] == "deposit"

                respond(
                    content = multipleTransactionsResponseJson,
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
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
        val service = Sep24Service(serviceAddress, mockClient)

        val response = service.transactions(Sep24TransactionsRequest(
            assetCode = "USDC",
            jwt = jwtToken,
            limit = 10,
            kind = "deposit"
        ))

        assertEquals(3, response.transactions.size)

        val tx1 = response.transactions[0]
        assertEquals("tx1", tx1.id)
        assertEquals("deposit", tx1.kind)
        assertEquals("completed", tx1.status)
        assertEquals("100", tx1.amountIn)
        assertEquals("95", tx1.amountOut)

        val tx2 = response.transactions[1]
        assertEquals("tx2", tx2.id)
        assertEquals("withdrawal", tx2.kind)
        assertEquals("pending_stellar", tx2.status)

        val tx3 = response.transactions[2]
        assertEquals("tx3", tx3.id)
        assertEquals("error", tx3.status)
        assertEquals("Payment timeout", tx3.message)

        assertTrue(requestParamsVerified, "Request parameters should be correct")
    }

    @Test
    fun testTransactionsEmpty() = runTest {
        val mockClient = createMockClient(
            responseContent = emptyTransactionsResponseJson,
            expectedPath = "/transactions"
        )
        val service = Sep24Service(serviceAddress, mockClient)

        val response = service.transactions(Sep24TransactionsRequest(
            assetCode = "USDC",
            jwt = jwtToken
        ))

        assertTrue(response.transactions.isEmpty())
    }

    @Test
    fun testTransactionsWithPagination() = runTest {
        var requestParamsVerified = false
        val mockEngine = MockEngine { request ->
            if (request.url.encodedPath.contains("/transactions")) {
                val params = request.url.parameters
                requestParamsVerified = params["paging_id"] == "tx123" &&
                        params["no_older_than"] == "2021-04-30T00:00:00Z"

                respond(
                    content = multipleTransactionsResponseJson,
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
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
        val service = Sep24Service(serviceAddress, mockClient)

        service.transactions(Sep24TransactionsRequest(
            assetCode = "USDC",
            jwt = jwtToken,
            pagingId = "tx123",
            noOlderThan = "2021-04-30T00:00:00Z"
        ))

        assertTrue(requestParamsVerified, "Pagination parameters should be included")
    }

    @Test
    fun testTransactions403Error() = runTest {
        val mockClient = createMockClient(
            responseContent = """{"type": "authentication_required", "error": "JWT expired"}""",
            statusCode = HttpStatusCode.Forbidden,
            expectedPath = "/transactions"
        )
        val service = Sep24Service(serviceAddress, mockClient)

        assertFailsWith<Sep24AuthenticationRequiredException> {
            service.transactions(Sep24TransactionsRequest(
                assetCode = "USDC",
                jwt = "expired_token"
            ))
        }
    }

    // ========== transaction() Tests ==========

    @Test
    fun testTransactionByIdSuccess() = runTest {
        var requestParamsVerified = false
        val mockEngine = MockEngine { request ->
            if (request.url.encodedPath.contains("/transaction")) {
                val params = request.url.parameters
                requestParamsVerified = params["id"] == transactionId

                respond(
                    content = singleTransactionResponseJson,
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
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
        val service = Sep24Service(serviceAddress, mockClient)

        val response = service.transaction(Sep24TransactionRequest(
            jwt = jwtToken,
            id = transactionId
        ))

        val tx = response.transaction
        assertEquals(transactionId, tx.id)
        assertEquals("deposit", tx.kind)
        assertEquals("pending_user_transfer_start", tx.status)
        assertEquals(3600, tx.statusEta)
        assertTrue(tx.kycVerified!!)
        assertEquals("https://anchor.example.com/tx/$transactionId", tx.moreInfoUrl)

        assertEquals("100", tx.amountIn)
        assertEquals("iso4217:USD", tx.amountInAsset)
        assertEquals("95.58", tx.amountOut)
        assertEquals("stellar:USDC:GA5ZSEJYB37JRC5AVCIA5MOP4RHTM335X2KGX3IHOJAPP5RE34K4KZVN", tx.amountOutAsset)

        @Suppress("DEPRECATION")
        assertEquals("4.42", tx.amountFee)
        @Suppress("DEPRECATION")
        assertEquals("iso4217:USD", tx.amountFeeAsset)

        assertNotNull(tx.feeDetails)
        assertEquals("4.42", tx.feeDetails!!.total)
        assertEquals("iso4217:USD", tx.feeDetails!!.asset)
        assertEquals(2, tx.feeDetails!!.breakdown!!.size)
        assertEquals("Service fee", tx.feeDetails!!.breakdown!![0].name)
        assertEquals("1.00", tx.feeDetails!!.breakdown!![0].amount)
        assertEquals("Processing fee", tx.feeDetails!!.breakdown!![0].description)

        assertEquals("de762cda-a193-4961-861e-57b31fed6eb3", tx.quoteId)
        assertEquals("2021-04-30T07:00:00Z", tx.startedAt)
        assertEquals("2021-04-30T07:10:00Z", tx.updatedAt)
        assertNull(tx.completedAt)
        assertEquals("2021-04-30T08:00:00Z", tx.userActionRequiredBy)

        assertNull(tx.stellarTransactionId)
        assertEquals("ext123", tx.externalTransactionId)
        assertEquals("Please send payment to complete deposit", tx.message)
        @Suppress("DEPRECATION")
        assertFalse(tx.refunded!!)

        assertEquals("user@email.com", tx.from)
        assertEquals("GBXTNX3ZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZ", tx.to)
        assertEquals("12345", tx.depositMemo)
        assertEquals("id", tx.depositMemoType)
        assertNull(tx.claimableBalanceId)

        assertTrue(requestParamsVerified, "Request parameters should be correct")
    }

    @Test
    fun testTransactionByStellarTransactionId() = runTest {
        var requestParamsVerified = false
        val mockEngine = MockEngine { request ->
            if (request.url.encodedPath.contains("/transaction")) {
                val params = request.url.parameters
                requestParamsVerified = params["stellar_transaction_id"] == "stellar_tx_hash"

                respond(
                    content = singleTransactionResponseJson,
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
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
        val service = Sep24Service(serviceAddress, mockClient)

        service.transaction(Sep24TransactionRequest(
            jwt = jwtToken,
            stellarTransactionId = "stellar_tx_hash"
        ))

        assertTrue(requestParamsVerified, "Stellar transaction ID parameter should be used")
    }

    @Test
    fun testTransactionByExternalTransactionId() = runTest {
        var requestParamsVerified = false
        val mockEngine = MockEngine { request ->
            if (request.url.encodedPath.contains("/transaction")) {
                val params = request.url.parameters
                requestParamsVerified = params["external_transaction_id"] == "ext123"

                respond(
                    content = singleTransactionResponseJson,
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
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
        val service = Sep24Service(serviceAddress, mockClient)

        service.transaction(Sep24TransactionRequest(
            jwt = jwtToken,
            externalTransactionId = "ext123"
        ))

        assertTrue(requestParamsVerified, "External transaction ID parameter should be used")
    }

    @Test
    fun testTransactionWithRefunds() = runTest {
        val mockClient = createMockClient(
            responseContent = transactionWithRefundsJson,
            expectedPath = "/transaction"
        )
        val service = Sep24Service(serviceAddress, mockClient)

        val response = service.transaction(Sep24TransactionRequest(
            jwt = jwtToken,
            id = transactionId
        ))

        val tx = response.transaction
        assertEquals("refunded", tx.status)
        assertEquals("withdraw", tx.kind)

        assertNotNull(tx.refunds)
        assertEquals("100", tx.refunds!!.amountRefunded)
        assertEquals("5", tx.refunds!!.amountFee)
        assertEquals(2, tx.refunds!!.payments.size)

        val payment1 = tx.refunds!!.payments[0]
        assertEquals("b9d0b2292c4e09e8eb22d036171491e87b8d2086bf8b265874c8d182cb9c9020", payment1.id)
        assertEquals("stellar", payment1.idType)
        assertEquals("50", payment1.amount)
        assertEquals("2.5", payment1.fee)

        val payment2 = tx.refunds!!.payments[1]
        assertEquals("ext456", payment2.id)
        assertEquals("external", payment2.idType)

        assertEquals("GCANCHORACCOUNTZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZ", tx.withdrawAnchorAccount)
        assertEquals("67890", tx.withdrawMemo)
        assertEquals("text", tx.withdrawMemoType)
    }

    @Test
    fun testTransaction404NotFound() = runTest {
        val mockClient = createMockClient(
            responseContent = """{"error": "Transaction not found"}""",
            statusCode = HttpStatusCode.NotFound,
            expectedPath = "/transaction"
        )
        val service = Sep24Service(serviceAddress, mockClient)

        assertFailsWith<Sep24TransactionNotFoundException> {
            service.transaction(Sep24TransactionRequest(
                jwt = jwtToken,
                id = "nonexistent"
            ))
        }
    }

    // ========== Transaction Status Tests ==========

    @Test
    fun testTransactionStatusEnum() = runTest {
        val mockClient = createMockClient(
            responseContent = singleTransactionResponseJson,
            expectedPath = "/transaction"
        )
        val service = Sep24Service(serviceAddress, mockClient)

        val response = service.transaction(Sep24TransactionRequest(
            jwt = jwtToken,
            id = transactionId
        ))

        val tx = response.transaction
        assertEquals(Sep24TransactionStatus.PENDING_USER_TRANSFER_START, tx.getStatusEnum())
        assertFalse(tx.isTerminal())
    }

    @Test
    fun testAllTransactionStatuses() = runTest {
        // Test all 16 status values
        val statuses = listOf(
            "incomplete" to Sep24TransactionStatus.INCOMPLETE,
            "pending_user_transfer_start" to Sep24TransactionStatus.PENDING_USER_TRANSFER_START,
            "pending_user_transfer_complete" to Sep24TransactionStatus.PENDING_USER_TRANSFER_COMPLETE,
            "pending_external" to Sep24TransactionStatus.PENDING_EXTERNAL,
            "pending_anchor" to Sep24TransactionStatus.PENDING_ANCHOR,
            "on_hold" to Sep24TransactionStatus.ON_HOLD,
            "pending_stellar" to Sep24TransactionStatus.PENDING_STELLAR,
            "pending_trust" to Sep24TransactionStatus.PENDING_TRUST,
            "pending_user" to Sep24TransactionStatus.PENDING_USER,
            "completed" to Sep24TransactionStatus.COMPLETED,
            "refunded" to Sep24TransactionStatus.REFUNDED,
            "expired" to Sep24TransactionStatus.EXPIRED,
            "no_market" to Sep24TransactionStatus.NO_MARKET,
            "too_small" to Sep24TransactionStatus.TOO_SMALL,
            "too_large" to Sep24TransactionStatus.TOO_LARGE,
            "error" to Sep24TransactionStatus.ERROR
        )

        statuses.forEach { (statusStr, expectedEnum) ->
            assertEquals(expectedEnum, Sep24TransactionStatus.fromValue(statusStr))
        }
    }

    @Test
    fun testTerminalStatuses() = runTest {
        val terminalStatuses = listOf(
            "completed", "refunded", "expired", "error", "no_market", "too_small", "too_large"
        )

        val nonTerminalStatuses = listOf(
            "incomplete", "pending_user_transfer_start", "pending_user_transfer_complete",
            "pending_external", "pending_anchor", "on_hold", "pending_stellar",
            "pending_trust", "pending_user"
        )

        terminalStatuses.forEach { status ->
            assertTrue(Sep24TransactionStatus.isTerminal(status), "$status should be terminal")
        }

        nonTerminalStatuses.forEach { status ->
            assertFalse(Sep24TransactionStatus.isTerminal(status), "$status should not be terminal")
        }
    }

    @Test
    fun testUnknownStatus() = runTest {
        assertNull(Sep24TransactionStatus.fromValue("unknown_status"))
    }

    // ========== pollTransaction() Tests ==========

    @Test
    fun testPollTransactionUntilCompleted() = runTest {
        var callCount = 0
        val statusChanges = mutableListOf<String>()

        val mockEngine = MockEngine { request ->
            if (request.url.encodedPath.contains("/transaction")) {
                callCount++
                val txJson = when (callCount) {
                    1 -> """{"transaction": {"id": "$transactionId", "kind": "deposit", "status": "pending_anchor"}}"""
                    2 -> """{"transaction": {"id": "$transactionId", "kind": "deposit", "status": "pending_stellar"}}"""
                    else -> """{"transaction": {"id": "$transactionId", "kind": "deposit", "status": "completed"}}"""
                }

                respond(
                    content = txJson,
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
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
        val service = Sep24Service(serviceAddress, mockClient)

        val tx = service.pollTransaction(
            request = Sep24TransactionRequest(jwt = jwtToken, id = transactionId),
            pollIntervalMs = 100,
            maxAttempts = 10,
            onStatusChange = { statusChanges.add(it.status) }
        )

        assertEquals("completed", tx.status)
        assertTrue(tx.isTerminal())
        assertEquals(3, callCount)
        assertEquals(listOf("pending_anchor", "pending_stellar", "completed"), statusChanges)
    }

    @Test
    fun testPollTransactionUntilError() = runTest {
        var callCount = 0

        val mockEngine = MockEngine { request ->
            if (request.url.encodedPath.contains("/transaction")) {
                callCount++
                val txJson = when (callCount) {
                    1 -> """{"transaction": {"id": "$transactionId", "kind": "deposit", "status": "pending_user_transfer_start"}}"""
                    else -> """{"transaction": {"id": "$transactionId", "kind": "deposit", "status": "error", "message": "Payment timeout"}}"""
                }

                respond(
                    content = txJson,
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
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
        val service = Sep24Service(serviceAddress, mockClient)

        val tx = service.pollTransaction(
            request = Sep24TransactionRequest(jwt = jwtToken, id = transactionId),
            pollIntervalMs = 100,
            maxAttempts = 10
        )

        assertEquals("error", tx.status)
        assertEquals("Payment timeout", tx.message)
        assertTrue(tx.isTerminal())
        assertEquals(2, callCount)
    }

    @Test
    fun testPollTransactionTimeout() = runTest {
        val mockEngine = MockEngine { request ->
            if (request.url.encodedPath.contains("/transaction")) {
                respond(
                    content = """{"transaction": {"id": "$transactionId", "kind": "deposit", "status": "pending_anchor"}}""",
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
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
        val service = Sep24Service(serviceAddress, mockClient)

        val exception = assertFailsWith<Sep24Exception> {
            service.pollTransaction(
                request = Sep24TransactionRequest(jwt = jwtToken, id = transactionId),
                pollIntervalMs = 10,
                maxAttempts = 3
            )
        }

        assertTrue(exception.message!!.contains("Polling timeout after 3 attempts"))
    }

    // ========== Error Handling Tests ==========

    @Test
    fun testServerError500() = runTest {
        val mockClient = createMockClient(
            responseContent = """{"error": "Internal server error"}""",
            statusCode = HttpStatusCode.InternalServerError,
            expectedPath = "/info"
        )
        val service = Sep24Service(serviceAddress, mockClient)

        val exception = assertFailsWith<Sep24ServerErrorException> {
            service.info()
        }
        assertTrue(exception.message!!.contains("500"))
        assertEquals(500, exception.statusCode)
    }

    @Test
    fun testServerError403NonAuth() = runTest {
        val mockClient = createMockClient(
            responseContent = """{"error": "Forbidden", "type": "forbidden"}""",
            statusCode = HttpStatusCode.Forbidden,
            expectedPath = "/info"
        )
        val service = Sep24Service(serviceAddress, mockClient)

        val exception = assertFailsWith<Sep24ServerErrorException> {
            service.info()
        }
        assertTrue(exception.message!!.contains("403"))
        assertEquals(403, exception.statusCode)
    }

    @Test
    fun testErrorMessageParsing() = runTest {
        val mockClient = createMockClient(
            responseContent = """{"error": "Custom error message"}""",
            statusCode = HttpStatusCode.BadRequest,
            expectedPath = "/info"
        )
        val service = Sep24Service(serviceAddress, mockClient)

        val exception = assertFailsWith<Sep24InvalidRequestException> {
            service.info()
        }
        assertEquals("Custom error message", exception.message)
    }

    @Test
    fun testErrorMessageParsingInvalidJson() = runTest {
        val mockClient = createMockClient(
            responseContent = "Plain text error",
            statusCode = HttpStatusCode.BadRequest,
            expectedPath = "/info"
        )
        val service = Sep24Service(serviceAddress, mockClient)

        val exception = assertFailsWith<Sep24InvalidRequestException> {
            service.info()
        }
        assertEquals("Plain text error", exception.message)
    }

    // ========== Account Types Tests ==========

    @Test
    fun testDepositWithStandardAccount() = runTest {
        // Standard G... account
        val standardAccount = "GCFXHS4GXL6BVUCXBWXGTITROWLVYXQKQLF4YH5O5JT3YZXCYPAFBJZB"

        val mockClient = createMockClient(
            responseContent = interactiveResponseJson,
            expectedPath = "/transactions/deposit/interactive",
            expectedMethod = HttpMethod.Post
        )
        val service = Sep24Service(serviceAddress, mockClient)

        val response = service.deposit(Sep24DepositRequest(
            assetCode = "USDC",
            jwt = jwtToken,
            account = standardAccount
        ))

        assertEquals(transactionId, response.id)
        assertEquals("interactive_customer_info_needed", response.type)
    }

    @Test
    fun testDepositWithMuxedAccount() = runTest {
        // Muxed M... account (with embedded ID)
        val muxedAccount = "MCFXHS4GXL6BVUCXBWXGTITROWLVYXQKQLF4YH5O5JT3YZXCYPAFBJAAAAAAAAAAAAGPQ4"

        val mockClient = createMockClient(
            responseContent = interactiveResponseJson,
            expectedPath = "/transactions/deposit/interactive",
            expectedMethod = HttpMethod.Post
        )
        val service = Sep24Service(serviceAddress, mockClient)

        val response = service.deposit(Sep24DepositRequest(
            assetCode = "USDC",
            jwt = jwtToken,
            account = muxedAccount
        ))

        assertEquals(transactionId, response.id)
        assertEquals("interactive_customer_info_needed", response.type)
    }

    @Test
    fun testDepositWithContractAccount() = runTest {
        // Contract C... account
        val contractAccount = "CDLZFC3SYJYDZT7K67VZ75HPJVIEUVNIXF47ZG2FB2RMQQVU2HHGCYSC"

        val mockClient = createMockClient(
            responseContent = interactiveResponseJson,
            expectedPath = "/transactions/deposit/interactive",
            expectedMethod = HttpMethod.Post
        )
        val service = Sep24Service(serviceAddress, mockClient)

        val response = service.deposit(Sep24DepositRequest(
            assetCode = "USDC",
            jwt = jwtToken,
            account = contractAccount
        ))

        assertEquals(transactionId, response.id)
        assertEquals("interactive_customer_info_needed", response.type)
    }
}
