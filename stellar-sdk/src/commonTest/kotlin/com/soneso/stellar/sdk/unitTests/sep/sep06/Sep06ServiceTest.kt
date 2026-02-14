// Copyright 2026 Soneso. All rights reserved.
// Use of this source code is governed by a license that can be
// found in the LICENSE file.

package com.soneso.stellar.sdk.unitTests.sep.sep06

import com.soneso.stellar.sdk.sep.sep06.*
import com.soneso.stellar.sdk.sep.sep06.exceptions.*
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.*

class Sep06ServiceTest {

    private val serviceAddress = "https://test.anchor.com/sep6"
    private val jwtToken = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.test"
    private val transactionId = "9421871e-0623-4356-b7b5-5996da122f3e"
    private val accountId = "GBWMCCC3NHSKLAOJDBKKYW7SSH2PFTTNVFKWSGLWGDLEBKLOVP5JLBBP"

    // ========== Mock JSON Responses ==========

    private val infoResponseJson = """
        {
            "deposit": {
                "USD": {
                    "enabled": true,
                    "authentication_required": true,
                    "min_amount": "0.1",
                    "max_amount": "1000",
                    "fee_fixed": "5",
                    "fee_percent": "1",
                    "fields": {
                        "email_address": {
                            "description": "your email address for transaction status updates",
                            "optional": true
                        },
                        "amount": {
                            "description": "amount in USD that you plan to deposit"
                        },
                        "country_code": {
                            "description": "The ISO 3166-1 alpha-3 code of the user's current address",
                            "choices": ["USA", "PRI"]
                        },
                        "type": {
                            "description": "type of deposit to make",
                            "choices": ["SEPA", "SWIFT", "cash"]
                        }
                    }
                },
                "ETH": {
                    "enabled": true,
                    "authentication_required": false
                }
            },
            "deposit-exchange": {
                "USD": {
                    "enabled": false,
                    "authentication_required": true,
                    "fields": {
                        "email_address": {
                            "description": "your email address for transaction status updates",
                            "optional": true
                        }
                    }
                }
            },
            "withdraw": {
                "USD": {
                    "enabled": true,
                    "authentication_required": true,
                    "min_amount": "0.1",
                    "max_amount": "1000",
                    "types": {
                        "bank_account": {
                            "fields": {
                                "dest": {"description": "your bank account number"},
                                "dest_extra": {"description": "your routing number"},
                                "bank_branch": {"description": "address of your bank branch"},
                                "phone_number": {"description": "your phone number in case there's an issue"},
                                "country_code": {
                                    "description": "The ISO 3166-1 alpha-3 code of the user's current address",
                                    "choices": ["USA", "PRI"]
                                }
                            }
                        },
                        "cash": {
                            "fields": {
                                "dest": {
                                    "description": "your email address. Your cashout PIN will be sent here.",
                                    "optional": true
                                }
                            }
                        }
                    }
                },
                "ETH": {
                    "enabled": false
                }
            },
            "withdraw-exchange": {
                "USD": {
                    "enabled": false,
                    "authentication_required": true,
                    "types": {
                        "bank_account": {
                            "fields": {
                                "dest": {"description": "your bank account number"}
                            }
                        }
                    }
                }
            },
            "fee": {
                "enabled": false,
                "description": "Fees vary from 3 to 7 percent based on the assets transacted."
            },
            "transactions": {
                "enabled": true,
                "authentication_required": true
            },
            "transaction": {
                "enabled": false,
                "authentication_required": true
            },
            "features": {
                "account_creation": true,
                "claimable_balances": true
            }
        }
    """.trimIndent()

    private val depositResponseJson = """
        {
            "id": "$transactionId",
            "instructions": {
                "organization.bank_number": {
                    "value": "121122676",
                    "description": "US bank routing number"
                },
                "organization.bank_account_number": {
                    "value": "13719713158835300",
                    "description": "US bank account number"
                }
            },
            "how": "Make a payment to Bank: 121122676 Account: 13719713158835300"
        }
    """.trimIndent()

    private val depositWithEtaResponseJson = """
        {
            "id": "$transactionId",
            "instructions": {
                "organization.crypto_address": {
                    "value": "rNXEkKCxvfLcM1h4HJkaj2FtmYuAWrHGbf",
                    "description": "Ripple address"
                },
                "organization.crypto_memo": {
                    "value": "88",
                    "description": "Ripple tag"
                }
            },
            "how": "Make a payment to Ripple address rNXEkKCxvfLcM1h4HJkaj2FtmYuAWrHGbf with tag 88",
            "eta": 60,
            "fee_percent": "0.1",
            "extra_info": {
                "message": "You must include the tag. If the amount is more than 1000 XRP, deposit will take 24h to complete."
            }
        }
    """.trimIndent()

    private val withdrawResponseJson = """
        {
            "account_id": "GCIBUCGPOHWMMMFPFTDWBSVHQRT4DIBJ7AD6BZJYDITBK2LCVBYW7HUQ",
            "memo_type": "id",
            "memo": "123",
            "id": "$transactionId"
        }
    """.trimIndent()

    private val feeResponseJson = """
        {
            "fee": "0.013"
        }
    """.trimIndent()

    private val singleTransactionResponseJson = """
        {
            "transaction": {
                "id": "82fhs729f63dh0v4",
                "kind": "deposit",
                "status": "pending_external",
                "status_eta": 3600,
                "external_transaction_id": "2dd16cb409513026fbe7defc0c6f826c2d2c65c3da993f747d09bf7dafd31093",
                "amount_in": "18.34",
                "amount_out": "18.24",
                "amount_fee": "0.1",
                "started_at": "2017-03-20T17:05:32Z",
                "fee_details": {
                    "total": "0.1",
                    "asset": "iso4217:USD"
                }
            }
        }
    """.trimIndent()

    private val transactionsResponseJson = """
        {
            "transactions": [
                {
                    "id": "82fhs729f63dh0v4",
                    "kind": "deposit",
                    "status": "pending_external",
                    "status_eta": 3600,
                    "external_transaction_id": "2dd16cb409513026fbe7defc0c6f826c2d2c65c3da993f747d09bf7dafd31093",
                    "amount_in": "18.34",
                    "amount_out": "18.24",
                    "amount_fee": "0.1",
                    "started_at": "2017-03-20T17:05:32Z"
                },
                {
                    "id": "52fys79f63dh3v2",
                    "kind": "deposit-exchange",
                    "status": "pending_anchor",
                    "status_eta": 3600,
                    "external_transaction_id": "2dd16cb409513026fbe7defc0c6f826c2d2c65c3da993f747d09bf7dafd31093",
                    "amount_in": "500",
                    "amount_in_asset": "iso4217:BRL",
                    "amount_out": "100",
                    "amount_out_asset": "stellar:USDC:GA5ZSEJYB37JRC5AVCIA5MOP4RHTM335X2KGX3IHOJAPP5RE34K4KZVN",
                    "amount_fee": "0.1",
                    "amount_fee_asset": "iso4217:BRL",
                    "started_at": "2021-06-11T17:05:32Z"
                },
                {
                    "id": "82fhs729f63dh0v4",
                    "kind": "withdrawal",
                    "status": "completed",
                    "amount_in": "510",
                    "amount_out": "490",
                    "amount_fee": "5",
                    "started_at": "2017-03-20T17:00:02Z",
                    "completed_at": "2017-03-20T17:09:58Z",
                    "stellar_transaction_id": "17a670bc424ff5ce3b386dbfaae9990b66a2a37b4fbe51547e8794962a3f9e6a",
                    "external_transaction_id": "1238234",
                    "withdraw_anchor_account": "GBANAGOAXH5ONSBI2I6I5LHP2TCRHWMZIAMGUQH2TNKQNCOGJ7GC3ZOL",
                    "withdraw_memo": "186384",
                    "withdraw_memo_type": "id",
                    "refunds": {
                        "amount_refunded": "10",
                        "amount_fee": "5",
                        "payments": [
                            {
                                "id": "b9d0b2292c4e09e8eb22d036171491e87b8d2086bf8b265874c8d182cb9c9020",
                                "id_type": "stellar",
                                "amount": "10",
                                "fee": "5"
                            }
                        ]
                    }
                },
                {
                    "id": "52fys79f63dh3v1",
                    "kind": "withdrawal",
                    "status": "pending_transaction_info_update",
                    "amount_in": "750.00",
                    "started_at": "2017-03-20T17:00:02Z",
                    "required_info_message": "We were unable to send funds to the provided bank account. Bank error: 'Account does not exist'. Please provide the correct bank account address.",
                    "required_info_updates": {
                        "dest": {"description": "your bank account number"},
                        "dest_extra": {"description": "your routing number"}
                    }
                },
                {
                    "id": "52fys79f63dh3v2",
                    "kind": "withdrawal-exchange",
                    "status": "pending_anchor",
                    "status_eta": 3600,
                    "stellar_transaction_id": "17a670bc424ff5ce3b386dbfaae9990b66a2a37b4fbe51547e8794962a3f9e6a",
                    "amount_in": "100",
                    "amount_in_asset": "stellar:USDC:GA5ZSEJYB37JRC5AVCIA5MOP4RHTM335X2KGX3IHOJAPP5RE34K4KZVN",
                    "amount_out": "500",
                    "amount_out_asset": "iso4217:BRL",
                    "amount_fee": "0.1",
                    "amount_fee_asset": "stellar:USDC:GA5ZSEJYB37JRC5AVCIA5MOP4RHTM335X2KGX3IHOJAPP5RE34K4KZVN",
                    "started_at": "2021-06-11T17:05:32Z"
                }
            ]
        }
    """.trimIndent()

    private val authenticationRequiredJson = """
        {
            "type": "authentication_required",
            "error": "Authentication required"
        }
    """.trimIndent()

    private val customerInfoNeededJson = """
        {
            "type": "non_interactive_customer_info_needed",
            "fields": ["family_name", "given_name", "address", "tax_id"]
        }
    """.trimIndent()

    private val customerInfoStatusPendingJson = """
        {
            "type": "customer_info_status",
            "status": "pending",
            "more_info_url": "https://api.example.com/kycstatus?account=GACW7NONV43MZIFHCOKCQJAKSJSISSICFVUJ2C6EZIW5773OU3HD64VI",
            "eta": 3600
        }
    """.trimIndent()

    private val customerInfoStatusDeniedJson = """
        {
            "type": "customer_info_status",
            "status": "denied",
            "more_info_url": "https://api.example.com/kycstatus?account=GACW7NONV43MZIFHCOKCQJAKSJSISSICFVUJ2C6EZIW5773OU3HD64VI"
        }
    """.trimIndent()

    // ========== Helper Methods ==========

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
                    content = """{"error": "Not found: ${request.url.encodedPath}"}""",
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
        val service = Sep06Service.fromUrl(serviceAddress, mockClient)

        val response = service.info()

        // Verify deposit assets
        assertNotNull(response.deposit)
        assertEquals(2, response.deposit!!.size)

        val usdDeposit = response.deposit!!["USD"]!!
        assertTrue(usdDeposit.enabled)
        assertTrue(usdDeposit.authenticationRequired!!)
        assertEquals("0.1", usdDeposit.minAmount)
        assertEquals("1000", usdDeposit.maxAmount)
        assertEquals("5", usdDeposit.feeFixed)
        assertEquals("1", usdDeposit.feePercent)

        @Suppress("DEPRECATION")
        val fields = usdDeposit.fields!!
        assertEquals(4, fields.size)
        assertEquals("your email address for transaction status updates", fields["email_address"]?.description)
        assertTrue(fields["email_address"]?.optional!!)
        assertTrue(fields["country_code"]?.choices!!.contains("USA"))
        assertTrue(fields["type"]?.choices!!.contains("SWIFT"))

        val ethDeposit = response.deposit!!["ETH"]!!
        assertTrue(ethDeposit.enabled)
        assertFalse(ethDeposit.authenticationRequired!!)

        // Verify deposit-exchange assets
        assertNotNull(response.depositExchange)
        val usdDepositExchange = response.depositExchange!!["USD"]!!
        assertFalse(usdDepositExchange.enabled)
        assertTrue(usdDepositExchange.authenticationRequired!!)

        // Verify withdraw assets
        assertNotNull(response.withdraw)
        val usdWithdraw = response.withdraw!!["USD"]!!
        assertTrue(usdWithdraw.enabled)
        assertTrue(usdWithdraw.authenticationRequired!!)
        assertEquals("0.1", usdWithdraw.minAmount)
        assertEquals("1000", usdWithdraw.maxAmount)

        val types = usdWithdraw.types!!
        assertEquals(2, types.size)
        assertNotNull(types["bank_account"]?.fields?.get("country_code")?.choices?.contains("PRI"))
        assertTrue(types["cash"]?.fields?.get("dest")?.optional!!)

        assertFalse(response.withdraw!!["ETH"]!!.enabled)

        // Verify withdraw-exchange assets
        assertNotNull(response.withdrawExchange)
        val usdWithdrawExchange = response.withdrawExchange!!["USD"]!!
        assertFalse(usdWithdrawExchange.enabled)
        assertTrue(usdWithdrawExchange.authenticationRequired!!)

        // Verify fee endpoint
        assertNotNull(response.fee)
        assertFalse(response.fee!!.enabled!!)
        assertNotNull(response.fee!!.description)

        // Verify transactions endpoint
        assertNotNull(response.transactions)
        assertTrue(response.transactions!!.enabled!!)
        assertTrue(response.transactions!!.authenticationRequired!!)

        // Verify transaction endpoint
        assertNotNull(response.transaction)
        assertFalse(response.transaction!!.enabled!!)
        assertTrue(response.transaction!!.authenticationRequired!!)

        // Verify features
        assertNotNull(response.features)
        assertTrue(response.features!!.accountCreation)
        assertTrue(response.features!!.claimableBalances)
    }

    @Test
    fun testInfoWithLangParameter() = runTest {
        var langParamVerified = false
        val mockEngine = MockEngine { request ->
            if (request.url.encodedPath.contains("/info")) {
                langParamVerified = request.url.parameters["lang"] == "es"
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
                json(Json { ignoreUnknownKeys = true; isLenient = true })
            }
        }
        val service = Sep06Service.fromUrl(serviceAddress, mockClient)

        service.info(language = "es")

        assertTrue(langParamVerified, "Language parameter should be included")
    }

    @Test
    fun testInfoWithJwt() = runTest {
        var authHeaderVerified = false
        val mockEngine = MockEngine { request ->
            if (request.url.encodedPath.contains("/info")) {
                authHeaderVerified = request.headers["Authorization"]?.contains("Bearer $jwtToken") == true
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
                json(Json { ignoreUnknownKeys = true; isLenient = true })
            }
        }
        val service = Sep06Service.fromUrl(serviceAddress, mockClient)

        service.info(jwt = jwtToken)

        assertTrue(authHeaderVerified, "Authorization header should be present")
    }

    // ========== deposit() Tests ==========

    @Test
    fun testDepositSuccess() = runTest {
        var requestParamsVerified = false
        var authHeaderVerified = false
        val mockEngine = MockEngine { request ->
            if (request.url.encodedPath.contains("/deposit") && !request.url.encodedPath.contains("deposit-exchange")) {
                val params = request.url.parameters
                requestParamsVerified = params["asset_code"] == "USD" &&
                        params["account"] == accountId &&
                        params["amount"] == "123.45"
                authHeaderVerified = request.headers["Authorization"]?.contains("Bearer $jwtToken") == true

                respond(
                    content = depositResponseJson,
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
                json(Json { ignoreUnknownKeys = true; isLenient = true })
            }
        }
        val service = Sep06Service.fromUrl(serviceAddress, mockClient)

        val response = service.deposit(Sep06DepositRequest(
            assetCode = "USD",
            account = accountId,
            jwt = jwtToken,
            amount = "123.45"
        ))

        @Suppress("DEPRECATION")
        assertEquals("Make a payment to Bank: 121122676 Account: 13719713158835300", response.how)
        assertEquals(transactionId, response.id)
        assertNull(response.feeFixed)

        val instructions = response.instructions
        assertNotNull(instructions)
        assertEquals("121122676", instructions["organization.bank_number"]?.value)
        assertEquals("US bank routing number", instructions["organization.bank_number"]?.description)
        assertEquals("13719713158835300", instructions["organization.bank_account_number"]?.value)
        assertEquals("US bank account number", instructions["organization.bank_account_number"]?.description)

        assertTrue(requestParamsVerified, "Request parameters should be correct")
        assertTrue(authHeaderVerified, "Authorization header should be present")
    }

    @Test
    fun testDepositWithEtaAndFees() = runTest {
        val mockClient = createMockClient(
            responseContent = depositWithEtaResponseJson,
            expectedPath = "/deposit"
        )
        val service = Sep06Service.fromUrl(serviceAddress, mockClient)

        val response = service.deposit(Sep06DepositRequest(
            assetCode = "XRP",
            account = accountId,
            jwt = jwtToken,
            amount = "300"
        ))

        assertEquals(transactionId, response.id)
        assertEquals(60, response.eta)
        assertEquals("0.1", response.feePercent)
        assertNotNull(response.extraInfo)
        assertEquals(
            "You must include the tag. If the amount is more than 1000 XRP, deposit will take 24h to complete.",
            response.extraInfo!!.message
        )

        val instructions = response.instructions!!
        assertEquals("rNXEkKCxvfLcM1h4HJkaj2FtmYuAWrHGbf", instructions["organization.crypto_address"]?.value)
        assertEquals("88", instructions["organization.crypto_memo"]?.value)
    }

    @Test
    fun testDepositWithAllParameters() = runTest {
        var allParamsVerified = false
        val mockEngine = MockEngine { request ->
            if (request.url.encodedPath.contains("/deposit") && !request.url.encodedPath.contains("deposit-exchange")) {
                val params = request.url.parameters
                allParamsVerified = params["asset_code"] == "USD" &&
                        params["account"] == accountId &&
                        params["asset_issuer"] == "GA5ZSEJYB37JRC5AVCIA5MOP4RHTM335X2KGX3IHOJAPP5RE34K4KZVN" &&
                        params["memo_type"] == "text" &&
                        params["memo"] == "test-memo" &&
                        params["email_address"] == "test@example.com" &&
                        params["type"] == "SEPA" &&
                        params["funding_method"] == "bank_transfer" &&
                        params["amount"] == "100" &&
                        params["country_code"] == "USA" &&
                        params["claimable_balance_supported"] == "true" &&
                        params["customer_id"] == "cust-123" &&
                        params["location_id"] == "loc-456" &&
                        params["lang"] == "en"

                respond(
                    content = depositResponseJson,
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
                json(Json { ignoreUnknownKeys = true; isLenient = true })
            }
        }
        val service = Sep06Service.fromUrl(serviceAddress, mockClient)

        @Suppress("DEPRECATION")
        service.deposit(Sep06DepositRequest(
            assetCode = "USD",
            account = accountId,
            jwt = jwtToken,
            assetIssuer = "GA5ZSEJYB37JRC5AVCIA5MOP4RHTM335X2KGX3IHOJAPP5RE34K4KZVN",
            memoType = "text",
            memo = "test-memo",
            emailAddress = "test@example.com",
            type = "SEPA",
            fundingMethod = "bank_transfer",
            amount = "100",
            countryCode = "USA",
            claimableBalanceSupported = true,
            customerId = "cust-123",
            locationId = "loc-456",
            lang = "en"
        ))

        assertTrue(allParamsVerified, "All parameters should be passed correctly")
    }

    @Test
    fun testDepositWithExtraFields() = runTest {
        var extraFieldsVerified = false
        val mockEngine = MockEngine { request ->
            if (request.url.encodedPath.contains("/deposit") && !request.url.encodedPath.contains("deposit-exchange")) {
                val params = request.url.parameters
                extraFieldsVerified = params["custom_field_1"] == "value1" &&
                        params["custom_field_2"] == "value2"

                respond(
                    content = depositResponseJson,
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
                json(Json { ignoreUnknownKeys = true; isLenient = true })
            }
        }
        val service = Sep06Service.fromUrl(serviceAddress, mockClient)

        service.deposit(Sep06DepositRequest(
            assetCode = "USD",
            account = accountId,
            jwt = jwtToken,
            extraFields = mapOf(
                "custom_field_1" to "value1",
                "custom_field_2" to "value2"
            )
        ))

        assertTrue(extraFieldsVerified, "Extra fields should be passed correctly")
    }

    // ========== depositExchange() Tests ==========

    @Test
    fun testDepositExchangeSuccess() = runTest {
        var requestParamsVerified = false
        val mockEngine = MockEngine { request ->
            if (request.url.encodedPath.contains("/deposit-exchange")) {
                val params = request.url.parameters
                requestParamsVerified = params["destination_asset"] == "XYZ" &&
                        params["source_asset"] == "iso4217:USD" &&
                        params["amount"] == "100" &&
                        params["account"] == accountId &&
                        params["quote_id"] == "282837" &&
                        params["location_id"] == "999"

                respond(
                    content = depositResponseJson,
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
                json(Json { ignoreUnknownKeys = true; isLenient = true })
            }
        }
        val service = Sep06Service.fromUrl(serviceAddress, mockClient)

        val response = service.depositExchange(Sep06DepositExchangeRequest(
            destinationAsset = "XYZ",
            sourceAsset = "iso4217:USD",
            amount = "100",
            account = accountId,
            jwt = jwtToken,
            quoteId = "282837",
            locationId = "999"
        ))

        assertEquals(transactionId, response.id)
        assertTrue(requestParamsVerified, "Request parameters should be correct")
    }

    // ========== withdraw() Tests ==========

    @Test
    fun testWithdrawSuccess() = runTest {
        var requestParamsVerified = false
        var authHeaderVerified = false
        val mockEngine = MockEngine { request ->
            if (request.url.encodedPath.contains("/withdraw") && !request.url.encodedPath.contains("withdraw-exchange")) {
                val params = request.url.parameters
                requestParamsVerified = params["asset_code"] == "XLM" &&
                        params["type"] == "crypto" &&
                        params["dest"] == "GCTTGO5ABSTHABXWL2FMHPZ2XFOZDXJYJN5CKFRKXMPAAWZW3Y3JZ3JK" &&
                        params["account"] == accountId &&
                        params["amount"] == "120.0"
                authHeaderVerified = request.headers["Authorization"]?.contains("Bearer $jwtToken") == true

                respond(
                    content = withdrawResponseJson,
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
                json(Json { ignoreUnknownKeys = true; isLenient = true })
            }
        }
        val service = Sep06Service.fromUrl(serviceAddress, mockClient)

        @Suppress("DEPRECATION")
        val response = service.withdraw(Sep06WithdrawRequest(
            assetCode = "XLM",
            type = "crypto",
            jwt = jwtToken,
            dest = "GCTTGO5ABSTHABXWL2FMHPZ2XFOZDXJYJN5CKFRKXMPAAWZW3Y3JZ3JK",
            account = accountId,
            amount = "120.0"
        ))

        assertEquals("GCIBUCGPOHWMMMFPFTDWBSVHQRT4DIBJ7AD6BZJYDITBK2LCVBYW7HUQ", response.accountId)
        assertEquals("id", response.memoType)
        assertEquals("123", response.memo)
        assertEquals(transactionId, response.id)

        assertTrue(requestParamsVerified, "Request parameters should be correct")
        assertTrue(authHeaderVerified, "Authorization header should be present")
    }

    @Test
    fun testWithdrawWithRefundMemo() = runTest {
        var refundParamsVerified = false
        val mockEngine = MockEngine { request ->
            if (request.url.encodedPath.contains("/withdraw") && !request.url.encodedPath.contains("withdraw-exchange")) {
                val params = request.url.parameters
                refundParamsVerified = params["refund_memo"] == "refund123" &&
                        params["refund_memo_type"] == "text"

                respond(
                    content = withdrawResponseJson,
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
                json(Json { ignoreUnknownKeys = true; isLenient = true })
            }
        }
        val service = Sep06Service.fromUrl(serviceAddress, mockClient)

        @Suppress("DEPRECATION")
        service.withdraw(Sep06WithdrawRequest(
            assetCode = "USD",
            type = "bank_account",
            jwt = jwtToken,
            dest = "1234567890",
            destExtra = "021000021",
            refundMemo = "refund123",
            refundMemoType = "text"
        ))

        assertTrue(refundParamsVerified, "Refund parameters should be passed correctly")
    }

    // ========== withdrawExchange() Tests ==========

    @Test
    fun testWithdrawExchangeSuccess() = runTest {
        var requestParamsVerified = false
        val mockEngine = MockEngine { request ->
            if (request.url.encodedPath.contains("/withdraw-exchange")) {
                val params = request.url.parameters
                requestParamsVerified = params["source_asset"] == "XYZ" &&
                        params["destination_asset"] == "iso4217:USD" &&
                        params["amount"] == "700" &&
                        params["type"] == "bank_account" &&
                        params["quote_id"] == "282837" &&
                        params["location_id"] == "999"

                respond(
                    content = withdrawResponseJson,
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
                json(Json { ignoreUnknownKeys = true; isLenient = true })
            }
        }
        val service = Sep06Service.fromUrl(serviceAddress, mockClient)

        @Suppress("DEPRECATION")
        val response = service.withdrawExchange(Sep06WithdrawExchangeRequest(
            sourceAsset = "XYZ",
            destinationAsset = "iso4217:USD",
            amount = "700",
            type = "bank_account",
            jwt = jwtToken,
            quoteId = "282837",
            locationId = "999"
        ))

        assertEquals("GCIBUCGPOHWMMMFPFTDWBSVHQRT4DIBJ7AD6BZJYDITBK2LCVBYW7HUQ", response.accountId)
        assertEquals("id", response.memoType)
        assertEquals("123", response.memo)
        assertEquals(transactionId, response.id)
        assertTrue(requestParamsVerified, "Request parameters should be correct")
    }

    // ========== fee() Tests ==========

    @Test
    fun testFeeSuccess() = runTest {
        var requestParamsVerified = false
        val mockEngine = MockEngine { request ->
            if (request.url.encodedPath.contains("/fee")) {
                val params = request.url.parameters
                requestParamsVerified = params["operation"] == "deposit" &&
                        params["asset_code"] == "ETH" &&
                        params["amount"] == "2034.09" &&
                        params["type"] == "SEPA"

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
                json(Json { ignoreUnknownKeys = true; isLenient = true })
            }
        }
        val service = Sep06Service.fromUrl(serviceAddress, mockClient)

        val response = service.fee(Sep06FeeRequest(
            operation = "deposit",
            assetCode = "ETH",
            amount = "2034.09",
            type = "SEPA",
            jwt = jwtToken
        ))

        assertEquals("0.013", response.fee)
        assertTrue(requestParamsVerified, "Request parameters should be correct")
    }

    // ========== transactions() Tests ==========

    @Test
    fun testTransactionsSuccess() = runTest {
        var requestParamsVerified = false
        val mockEngine = MockEngine { request ->
            if (request.url.encodedPath.contains("/transactions") && !request.url.encodedPath.contains("/transaction/")) {
                val params = request.url.parameters
                requestParamsVerified = params["asset_code"] == "XLM" &&
                        params["account"] == accountId

                respond(
                    content = transactionsResponseJson,
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
                json(Json { ignoreUnknownKeys = true; isLenient = true })
            }
        }
        val service = Sep06Service.fromUrl(serviceAddress, mockClient)

        val response = service.transactions(Sep06TransactionsRequest(
            assetCode = "XLM",
            account = accountId,
            jwt = jwtToken
        ))

        assertEquals(5, response.transactions.size)

        // Verify first transaction (deposit)
        val tx1 = response.transactions[0]
        assertEquals("82fhs729f63dh0v4", tx1.id)
        assertEquals("deposit", tx1.kind)
        assertEquals("pending_external", tx1.status)
        assertEquals(3600, tx1.statusEta)
        assertEquals("2dd16cb409513026fbe7defc0c6f826c2d2c65c3da993f747d09bf7dafd31093", tx1.externalTransactionId)
        assertEquals("18.34", tx1.amountIn)
        assertEquals("18.24", tx1.amountOut)
        @Suppress("DEPRECATION")
        assertEquals("0.1", tx1.amountFee)
        assertEquals("2017-03-20T17:05:32Z", tx1.startedAt)

        // Verify second transaction (deposit-exchange)
        val tx2 = response.transactions[1]
        assertEquals("52fys79f63dh3v2", tx2.id)
        assertEquals("deposit-exchange", tx2.kind)
        assertEquals("pending_anchor", tx2.status)
        assertEquals("500", tx2.amountIn)
        assertEquals("iso4217:BRL", tx2.amountInAsset)
        assertEquals("100", tx2.amountOut)
        assertEquals("stellar:USDC:GA5ZSEJYB37JRC5AVCIA5MOP4RHTM335X2KGX3IHOJAPP5RE34K4KZVN", tx2.amountOutAsset)
        @Suppress("DEPRECATION")
        assertEquals("iso4217:BRL", tx2.amountFeeAsset)

        // Verify third transaction (withdrawal with refunds)
        val tx3 = response.transactions[2]
        assertEquals("withdrawal", tx3.kind)
        assertEquals("completed", tx3.status)
        assertEquals("2017-03-20T17:09:58Z", tx3.completedAt)
        assertEquals("17a670bc424ff5ce3b386dbfaae9990b66a2a37b4fbe51547e8794962a3f9e6a", tx3.stellarTransactionId)
        assertEquals("GBANAGOAXH5ONSBI2I6I5LHP2TCRHWMZIAMGUQH2TNKQNCOGJ7GC3ZOL", tx3.withdrawAnchorAccount)
        assertEquals("186384", tx3.withdrawMemo)
        assertEquals("id", tx3.withdrawMemoType)

        val refunds = tx3.refunds!!
        assertEquals("10", refunds.amountRefunded)
        assertEquals("5", refunds.amountFee)
        assertEquals(1, refunds.payments.size)
        assertEquals("b9d0b2292c4e09e8eb22d036171491e87b8d2086bf8b265874c8d182cb9c9020", refunds.payments[0].id)
        assertEquals("stellar", refunds.payments[0].idType)
        assertEquals("10", refunds.payments[0].amount)
        assertEquals("5", refunds.payments[0].fee)

        // Verify fourth transaction (pending_transaction_info_update)
        val tx4 = response.transactions[3]
        assertEquals("pending_transaction_info_update", tx4.status)
        assertEquals(
            "We were unable to send funds to the provided bank account. Bank error: 'Account does not exist'. Please provide the correct bank account address.",
            tx4.requiredInfoMessage
        )
        assertNotNull(tx4.requiredInfoUpdates)
        assertEquals(2, tx4.requiredInfoUpdates!!.size)
        assertEquals("your bank account number", tx4.requiredInfoUpdates!!["dest"]?.description)
        assertEquals("your routing number", tx4.requiredInfoUpdates!!["dest_extra"]?.description)

        // Verify fifth transaction (withdrawal-exchange)
        val tx5 = response.transactions[4]
        assertEquals("withdrawal-exchange", tx5.kind)
        assertEquals("stellar:USDC:GA5ZSEJYB37JRC5AVCIA5MOP4RHTM335X2KGX3IHOJAPP5RE34K4KZVN", tx5.amountInAsset)
        assertEquals("iso4217:BRL", tx5.amountOutAsset)

        assertTrue(requestParamsVerified, "Request parameters should be correct")
    }

    @Test
    fun testTransactionsWithPagination() = runTest {
        var paginationParamsVerified = false
        val mockEngine = MockEngine { request ->
            if (request.url.encodedPath.contains("/transactions")) {
                val params = request.url.parameters
                paginationParamsVerified = params["no_older_than"] == "2023-01-15T00:00:00Z" &&
                        params["limit"] == "10" &&
                        params["kind"] == "deposit" &&
                        params["paging_id"] == "82fhs729f63dh0v4" &&
                        params["lang"] == "es"

                respond(
                    content = transactionsResponseJson,
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
                json(Json { ignoreUnknownKeys = true; isLenient = true })
            }
        }
        val service = Sep06Service.fromUrl(serviceAddress, mockClient)

        service.transactions(Sep06TransactionsRequest(
            assetCode = "XLM",
            account = accountId,
            jwt = jwtToken,
            noOlderThan = "2023-01-15T00:00:00Z",
            limit = 10,
            kind = "deposit",
            pagingId = "82fhs729f63dh0v4",
            lang = "es"
        ))

        assertTrue(paginationParamsVerified, "Pagination parameters should be passed correctly")
    }

    // ========== transaction() Tests ==========

    @Test
    fun testTransactionById() = runTest {
        var requestParamsVerified = false
        val mockEngine = MockEngine { request ->
            // Check for singular /transaction (not /transactions)
            if (request.url.encodedPath.endsWith("/transaction") ||
                (request.url.encodedPath.contains("/transaction") && !request.url.encodedPath.contains("/transactions"))) {
                val params = request.url.parameters
                requestParamsVerified = params["id"] == "82fhs729f63dh0v4"

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
                json(Json { ignoreUnknownKeys = true; isLenient = true })
            }
        }
        val service = Sep06Service.fromUrl(serviceAddress, mockClient)

        val response = service.transaction(Sep06TransactionRequest(
            id = "82fhs729f63dh0v4",
            jwt = jwtToken
        ))

        val tx = response.transaction
        assertEquals("82fhs729f63dh0v4", tx.id)
        assertEquals("deposit", tx.kind)
        assertEquals("pending_external", tx.status)
        assertEquals(3600, tx.statusEta)
        assertEquals("18.34", tx.amountIn)
        assertEquals("18.24", tx.amountOut)
        @Suppress("DEPRECATION")
        assertEquals("0.1", tx.amountFee)
        assertEquals("2017-03-20T17:05:32Z", tx.startedAt)

        assertNotNull(tx.feeDetails)
        assertEquals("0.1", tx.feeDetails!!.total)
        assertEquals("iso4217:USD", tx.feeDetails!!.asset)

        assertTrue(requestParamsVerified, "Request parameters should be correct")
    }

    @Test
    fun testTransactionByStellarTransactionId() = runTest {
        var requestParamsVerified = false
        val mockEngine = MockEngine { request ->
            if (request.url.encodedPath.contains("/transaction") && !request.url.encodedPath.contains("/transactions")) {
                val params = request.url.parameters
                requestParamsVerified = params["stellar_transaction_id"] == "17a670bc424ff5ce3b386dbfaae9990b66a2a37b4fbe51547e8794962a3f9e6a"

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
                json(Json { ignoreUnknownKeys = true; isLenient = true })
            }
        }
        val service = Sep06Service.fromUrl(serviceAddress, mockClient)

        service.transaction(Sep06TransactionRequest(
            stellarTransactionId = "17a670bc424ff5ce3b386dbfaae9990b66a2a37b4fbe51547e8794962a3f9e6a",
            jwt = jwtToken
        ))

        assertTrue(requestParamsVerified, "Stellar transaction ID parameter should be used")
    }

    @Test
    fun testTransactionByExternalTransactionId() = runTest {
        var requestParamsVerified = false
        val mockEngine = MockEngine { request ->
            if (request.url.encodedPath.contains("/transaction") && !request.url.encodedPath.contains("/transactions")) {
                val params = request.url.parameters
                requestParamsVerified = params["external_transaction_id"] == "ext-123456"

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
                json(Json { ignoreUnknownKeys = true; isLenient = true })
            }
        }
        val service = Sep06Service.fromUrl(serviceAddress, mockClient)

        service.transaction(Sep06TransactionRequest(
            externalTransactionId = "ext-123456",
            jwt = jwtToken
        ))

        assertTrue(requestParamsVerified, "External transaction ID parameter should be used")
    }

    // ========== patchTransaction() Tests ==========

    @Test
    fun testPatchTransactionSuccess() = runTest {
        var pathVerified = false
        var methodVerified = false
        var authHeaderVerified = false
        val mockEngine = MockEngine { request ->
            if (request.url.encodedPath.contains("/transaction/82fhs729f63dh0v4")) {
                pathVerified = true
                methodVerified = request.method == HttpMethod.Patch
                authHeaderVerified = request.headers["Authorization"]?.contains("Bearer $jwtToken") == true

                respond(
                    content = "",
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
                json(Json { ignoreUnknownKeys = true; isLenient = true })
            }
        }
        val service = Sep06Service.fromUrl(serviceAddress, mockClient)

        val response = service.patchTransaction(Sep06PatchTransactionRequest(
            id = "82fhs729f63dh0v4",
            fields = mapOf(
                "dest" to "12345678901234",
                "dest_extra" to "021000021"
            ),
            jwt = jwtToken
        ))

        assertEquals(200, response.status.value)
        assertTrue(pathVerified, "Path should include transaction ID")
        assertTrue(methodVerified, "PATCH method should be used")
        assertTrue(authHeaderVerified, "Authorization header should be present")
    }

    // ========== Error Handling Tests ==========

    @Test
    fun testAuthenticationRequired() = runTest {
        val mockClient = createMockClient(
            responseContent = authenticationRequiredJson,
            statusCode = HttpStatusCode.Forbidden,
            expectedPath = "/deposit"
        )
        val service = Sep06Service.fromUrl(serviceAddress, mockClient)

        assertFailsWith<Sep06AuthenticationRequiredException> {
            service.deposit(Sep06DepositRequest(
                assetCode = "USD",
                account = accountId,
                jwt = "invalid_token"
            ))
        }
    }

    @Test
    fun testCustomerInformationNeeded() = runTest {
        val mockClient = createMockClient(
            responseContent = customerInfoNeededJson,
            statusCode = HttpStatusCode.Forbidden,
            expectedPath = "/deposit"
        )
        val service = Sep06Service.fromUrl(serviceAddress, mockClient)

        val exception = assertFailsWith<Sep06CustomerInformationNeededException> {
            service.deposit(Sep06DepositRequest(
                assetCode = "USD",
                account = accountId,
                jwt = jwtToken
            ))
        }

        assertEquals(4, exception.fields.size)
        assertTrue(exception.fields.contains("family_name"))
        assertTrue(exception.fields.contains("given_name"))
        assertTrue(exception.fields.contains("address"))
        assertTrue(exception.fields.contains("tax_id"))
    }

    @Test
    fun testCustomerInformationStatusPending() = runTest {
        val mockClient = createMockClient(
            responseContent = customerInfoStatusPendingJson,
            statusCode = HttpStatusCode.Forbidden,
            expectedPath = "/deposit"
        )
        val service = Sep06Service.fromUrl(serviceAddress, mockClient)

        val exception = assertFailsWith<Sep06CustomerInformationStatusException> {
            service.deposit(Sep06DepositRequest(
                assetCode = "USD",
                account = accountId,
                jwt = jwtToken
            ))
        }

        assertEquals("pending", exception.status)
        assertEquals(
            "https://api.example.com/kycstatus?account=GACW7NONV43MZIFHCOKCQJAKSJSISSICFVUJ2C6EZIW5773OU3HD64VI",
            exception.moreInfoUrl
        )
        assertEquals(3600, exception.eta)
    }

    @Test
    fun testCustomerInformationStatusDenied() = runTest {
        val mockClient = createMockClient(
            responseContent = customerInfoStatusDeniedJson,
            statusCode = HttpStatusCode.Forbidden,
            expectedPath = "/withdraw"
        )
        val service = Sep06Service.fromUrl(serviceAddress, mockClient)

        val exception = assertFailsWith<Sep06CustomerInformationStatusException> {
            @Suppress("DEPRECATION")
            service.withdraw(Sep06WithdrawRequest(
                assetCode = "USD",
                type = "bank_account",
                jwt = jwtToken
            ))
        }

        assertEquals("denied", exception.status)
        assertEquals(
            "https://api.example.com/kycstatus?account=GACW7NONV43MZIFHCOKCQJAKSJSISSICFVUJ2C6EZIW5773OU3HD64VI",
            exception.moreInfoUrl
        )
    }

    @Test
    fun testInvalidRequest() = runTest {
        val mockClient = createMockClient(
            responseContent = """{"error": "Asset code 'INVALID' is not supported"}""",
            statusCode = HttpStatusCode.BadRequest,
            expectedPath = "/deposit"
        )
        val service = Sep06Service.fromUrl(serviceAddress, mockClient)

        val exception = assertFailsWith<Sep06InvalidRequestException> {
            service.deposit(Sep06DepositRequest(
                assetCode = "INVALID",
                account = accountId,
                jwt = jwtToken
            ))
        }

        assertEquals("Asset code 'INVALID' is not supported", exception.errorMessage)
    }

    @Test
    fun testServerError() = runTest {
        val mockClient = createMockClient(
            responseContent = """{"error": "Internal server error"}""",
            statusCode = HttpStatusCode.InternalServerError,
            expectedPath = "/info"
        )
        val service = Sep06Service.fromUrl(serviceAddress, mockClient)

        val exception = assertFailsWith<Sep06ServerErrorException> {
            service.info()
        }

        assertEquals(500, exception.statusCode)
        assertEquals("Internal server error", exception.errorMessage)
    }

    @Test
    fun testTransactionNotFound() = runTest {
        val mockClient = createMockClient(
            responseContent = """{"error": "Transaction not found"}""",
            statusCode = HttpStatusCode.NotFound,
            expectedPath = "/transaction"
        )
        val service = Sep06Service.fromUrl(serviceAddress, mockClient)

        assertFailsWith<Sep06TransactionNotFoundException> {
            service.transaction(Sep06TransactionRequest(
                id = "nonexistent",
                jwt = jwtToken
            ))
        }
    }

    @Test
    fun testPatchTransactionNotFound() = runTest {
        val mockClient = createMockClient(
            responseContent = """{"error": "Transaction not found"}""",
            statusCode = HttpStatusCode.NotFound,
            expectedPath = "/transaction/nonexistent",
            expectedMethod = HttpMethod.Patch
        )
        val service = Sep06Service.fromUrl(serviceAddress, mockClient)

        assertFailsWith<Sep06TransactionNotFoundException> {
            service.patchTransaction(Sep06PatchTransactionRequest(
                id = "nonexistent",
                fields = mapOf("dest" to "12345"),
                jwt = jwtToken
            ))
        }
    }

    @Test
    fun testServerError503() = runTest {
        val mockClient = createMockClient(
            responseContent = """{"error": "Service temporarily unavailable"}""",
            statusCode = HttpStatusCode.ServiceUnavailable,
            expectedPath = "/deposit"
        )
        val service = Sep06Service.fromUrl(serviceAddress, mockClient)

        val exception = assertFailsWith<Sep06ServerErrorException> {
            service.deposit(Sep06DepositRequest(
                assetCode = "USD",
                account = accountId,
                jwt = jwtToken
            ))
        }

        assertEquals(503, exception.statusCode)
    }

    @Test
    fun testErrorMessageParsingWithPlainText() = runTest {
        val mockClient = createMockClient(
            responseContent = "Plain text error response",
            statusCode = HttpStatusCode.BadRequest,
            expectedPath = "/deposit"
        )
        val service = Sep06Service.fromUrl(serviceAddress, mockClient)

        val exception = assertFailsWith<Sep06InvalidRequestException> {
            service.deposit(Sep06DepositRequest(
                assetCode = "USD",
                account = accountId,
                jwt = jwtToken
            ))
        }

        assertEquals("Plain text error response", exception.errorMessage)
    }

    // ========== Account Types Tests ==========

    @Test
    fun testDepositWithStandardAccount() = runTest {
        val standardAccount = "GCFXHS4GXL6BVUCXBWXGTITROWLVYXQKQLF4YH5O5JT3YZXCYPAFBJZB"
        val mockClient = createMockClient(
            responseContent = depositResponseJson,
            expectedPath = "/deposit"
        )
        val service = Sep06Service.fromUrl(serviceAddress, mockClient)

        val response = service.deposit(Sep06DepositRequest(
            assetCode = "USD",
            account = standardAccount,
            jwt = jwtToken
        ))

        assertEquals(transactionId, response.id)
    }

    @Test
    fun testDepositWithMuxedAccount() = runTest {
        val muxedAccount = "MCFXHS4GXL6BVUCXBWXGTITROWLVYXQKQLF4YH5O5JT3YZXCYPAFBJAAAAAAAAAAAAGPQ4"
        val mockClient = createMockClient(
            responseContent = depositResponseJson,
            expectedPath = "/deposit"
        )
        val service = Sep06Service.fromUrl(serviceAddress, mockClient)

        val response = service.deposit(Sep06DepositRequest(
            assetCode = "USD",
            account = muxedAccount,
            jwt = jwtToken
        ))

        assertEquals(transactionId, response.id)
    }

    @Test
    fun testDepositWithContractAccount() = runTest {
        val contractAccount = "CDLZFC3SYJYDZT7K67VZ75HPJVIEUVNIXF47ZG2FB2RMQQVU2HHGCYSC"
        val mockClient = createMockClient(
            responseContent = depositResponseJson,
            expectedPath = "/deposit"
        )
        val service = Sep06Service.fromUrl(serviceAddress, mockClient)

        val response = service.deposit(Sep06DepositRequest(
            assetCode = "USD",
            account = contractAccount,
            jwt = jwtToken
        ))

        assertEquals(transactionId, response.id)
    }
}
