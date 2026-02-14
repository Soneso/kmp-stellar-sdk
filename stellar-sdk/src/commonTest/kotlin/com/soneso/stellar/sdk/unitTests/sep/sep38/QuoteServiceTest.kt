// Copyright 2025 Soneso. All rights reserved.
// Use of this source code is governed by a license that can be
// found in the LICENSE file.

package com.soneso.stellar.sdk.unitTests.sep.sep38

import com.soneso.stellar.sdk.sep.sep38.*
import com.soneso.stellar.sdk.sep.sep38.exceptions.*
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.*

class QuoteServiceTest {

    private val serviceAddress = "https://test.anchor.com"
    private val jwtToken = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.test"
    private val quoteId = "de762cda-a193-4961-861e-57b31fed6eb3"

    private fun createMockClient(
        responseContent: String,
        statusCode: HttpStatusCode = HttpStatusCode.OK,
        expectedPath: String,
        expectedMethod: HttpMethod = HttpMethod.Get,
        validateAuth: Boolean = false,
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

    // info() endpoint tests

    @Test
    fun testInfoSuccess() = runTest {
        val responseJson = """
            {
                "assets": [
                    {
                        "asset": "stellar:USDC:GA5ZSEJYB37JRC5AVCIA5MOP4RHTM335X2KGX3IHOJAPP5RE34K4KZVN",
                        "country_codes": ["US", "BR"]
                    },
                    {
                        "asset": "iso4217:BRL",
                        "sell_delivery_methods": [
                            {
                                "name": "PIX",
                                "description": "Send BRL directly via PIX"
                            }
                        ],
                        "buy_delivery_methods": [
                            {
                                "name": "PIX",
                                "description": "Receive BRL directly via PIX"
                            }
                        ],
                        "country_codes": ["BR"]
                    }
                ]
            }
        """.trimIndent()

        val mockClient = createMockClient(
            responseContent = responseJson,
            expectedPath = "/info"
        )
        val service = QuoteService(serviceAddress, mockClient)

        val response = service.info()

        assertEquals(2, response.assets.size)
        assertEquals("stellar:USDC:GA5ZSEJYB37JRC5AVCIA5MOP4RHTM335X2KGX3IHOJAPP5RE34K4KZVN", response.assets[0].asset)
        assertEquals("iso4217:BRL", response.assets[1].asset)
        assertNotNull(response.assets[1].sellDeliveryMethods)
        assertEquals("PIX", response.assets[1].sellDeliveryMethods!![0].name)
    }

    @Test
    fun testInfoWithJwtToken() = runTest {
        val responseJson = """
            {
                "assets": [
                    {
                        "asset": "stellar:USDC:GA5ZSEJYB37JRC5AVCIA5MOP4RHTM335X2KGX3IHOJAPP5RE34K4KZVN"
                    }
                ]
            }
        """.trimIndent()

        var authHeaderVerified = false
        val mockEngine = MockEngine { request ->
            if (request.url.encodedPath.contains("/info")) {
                val authHeader = request.headers["Authorization"]
                authHeaderVerified = authHeader?.contains("Bearer $jwtToken") == true

                respond(
                    content = responseJson,
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
        val service = QuoteService(serviceAddress, mockClient)

        val response = service.info(jwtToken)

        assertEquals(1, response.assets.size)
        assertTrue(authHeaderVerified, "Authorization header should be present")
    }

    @Test
    fun testInfo400Error() = runTest {
        val mockClient = createMockClient(
            responseContent = """{"error": "Invalid request"}""",
            statusCode = HttpStatusCode.BadRequest,
            expectedPath = "/info"
        )
        val service = QuoteService(serviceAddress, mockClient)

        val exception = assertFailsWith<Sep38BadRequestException> {
            service.info()
        }
        assertEquals("Invalid request", exception.error)
    }

    // prices() endpoint tests

    @Test
    fun testPricesWithSellAsset() = runTest {
        val responseJson = """
            {
                "buy_assets": [
                    {
                        "asset": "iso4217:BRL",
                        "price": "5.42",
                        "decimals": 2
                    },
                    {
                        "asset": "iso4217:USD",
                        "price": "1.00",
                        "decimals": 2
                    }
                ]
            }
        """.trimIndent()

        var requestUrlVerified = false
        val mockEngine = MockEngine { request ->
            if (request.url.encodedPath.contains("/prices")) {
                val params = request.url.parameters
                requestUrlVerified = params["sell_asset"] == "stellar:USDC:GA5Z" &&
                        params["sell_amount"] == "100"

                respond(
                    content = responseJson,
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
        val service = QuoteService(serviceAddress, mockClient)

        val response = service.prices(
            sellAsset = "stellar:USDC:GA5Z",
            sellAmount = "100"
        )

        assertNotNull(response.buyAssets)
        assertEquals(2, response.buyAssets!!.size)
        assertEquals("iso4217:BRL", response.buyAssets!![0].asset)
        assertEquals("5.42", response.buyAssets!![0].price)
        assertEquals(2, response.buyAssets!![0].decimals)
        assertNull(response.sellAssets)
        assertTrue(requestUrlVerified, "Request URL parameters should be correct")
    }

    @Test
    fun testPricesWithBuyAsset() = runTest {
        val responseJson = """
            {
                "sell_assets": [
                    {
                        "asset": "iso4217:BRL",
                        "price": "0.18",
                        "decimals": 2
                    },
                    {
                        "asset": "iso4217:USD",
                        "price": "1.00",
                        "decimals": 2
                    }
                ]
            }
        """.trimIndent()

        var requestUrlVerified = false
        val mockEngine = MockEngine { request ->
            if (request.url.encodedPath.contains("/prices")) {
                val params = request.url.parameters
                requestUrlVerified = params["buy_asset"] == "stellar:USDC:GA5Z" &&
                        params["buy_amount"] == "100"

                respond(
                    content = responseJson,
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
        val service = QuoteService(serviceAddress, mockClient)

        val response = service.prices(
            buyAsset = "stellar:USDC:GA5Z",
            buyAmount = "100"
        )

        assertNotNull(response.sellAssets)
        assertEquals(2, response.sellAssets!!.size)
        assertEquals("iso4217:BRL", response.sellAssets!![0].asset)
        assertEquals("0.18", response.sellAssets!![0].price)
        assertEquals(2, response.sellAssets!![0].decimals)
        assertNull(response.buyAssets)
        assertTrue(requestUrlVerified, "Request URL parameters should be correct")
    }

    @Test
    fun testPricesValidationBothAssetsProvided() = runTest {
        val mockClient = createMockClient(
            responseContent = "",
            expectedPath = "/prices"
        )
        val service = QuoteService(serviceAddress, mockClient)

        val exception = assertFailsWith<IllegalArgumentException> {
            service.prices(
                sellAsset = "stellar:USDC:GA5Z",
                buyAsset = "iso4217:BRL"
            )
        }
        assertTrue(exception.message!!.contains("Must provide either sellAsset or buyAsset, but not both"))
    }

    @Test
    fun testPricesValidationNeitherAssetProvided() = runTest {
        val mockClient = createMockClient(
            responseContent = "",
            expectedPath = "/prices"
        )
        val service = QuoteService(serviceAddress, mockClient)

        val exception = assertFailsWith<IllegalArgumentException> {
            service.prices()
        }
        assertTrue(exception.message!!.contains("Must provide either sellAsset or buyAsset, but not both"))
    }

    // price() endpoint tests

    @Test
    fun testPriceWithSellAmount() = runTest {
        val responseJson = """
            {
                "total_price": "5.42",
                "price": "5.00",
                "sell_amount": "542",
                "buy_amount": "100",
                "fee": {
                    "total": "42.00",
                    "asset": "iso4217:BRL"
                }
            }
        """.trimIndent()

        var requestUrlVerified = false
        val mockEngine = MockEngine { request ->
            if (request.url.encodedPath.contains("/price")) {
                val params = request.url.parameters
                requestUrlVerified = params["context"] == "sep6" &&
                        params["sell_asset"] == "iso4217:BRL" &&
                        params["buy_asset"] == "stellar:USDC:GA5Z" &&
                        params["sell_amount"] == "542" &&
                        params["sell_delivery_method"] == "PIX"

                respond(
                    content = responseJson,
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
        val service = QuoteService(serviceAddress, mockClient)

        val response = service.price(
            context = "sep6",
            sellAsset = "iso4217:BRL",
            buyAsset = "stellar:USDC:GA5Z",
            sellAmount = "542",
            sellDeliveryMethod = "PIX"
        )

        assertEquals("5.42", response.totalPrice)
        assertEquals("5.00", response.price)
        assertEquals("542", response.sellAmount)
        assertEquals("100", response.buyAmount)
        assertEquals("42.00", response.fee.total)
        assertEquals("iso4217:BRL", response.fee.asset)
        assertTrue(requestUrlVerified, "Request URL parameters should be correct")
    }

    @Test
    fun testPriceWithBuyAmount() = runTest {
        val responseJson = """
            {
                "total_price": "0.20",
                "price": "0.18",
                "sell_amount": "100",
                "buy_amount": "500",
                "fee": {
                    "total": "10.00",
                    "asset": "stellar:USDC:GA5Z"
                }
            }
        """.trimIndent()

        var requestUrlVerified = false
        val mockEngine = MockEngine { request ->
            if (request.url.encodedPath.contains("/price")) {
                val params = request.url.parameters
                requestUrlVerified = params["context"] == "sep31" &&
                        params["sell_asset"] == "stellar:USDC:GA5Z" &&
                        params["buy_asset"] == "iso4217:BRL" &&
                        params["buy_amount"] == "500" &&
                        params["buy_delivery_method"] == "PIX" &&
                        params["country_code"] == "BR"

                respond(
                    content = responseJson,
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
        val service = QuoteService(serviceAddress, mockClient)

        val response = service.price(
            context = "sep31",
            sellAsset = "stellar:USDC:GA5Z",
            buyAsset = "iso4217:BRL",
            buyAmount = "500",
            buyDeliveryMethod = "PIX",
            countryCode = "BR"
        )

        assertEquals("0.20", response.totalPrice)
        assertEquals("0.18", response.price)
        assertEquals("100", response.sellAmount)
        assertEquals("500", response.buyAmount)
        assertEquals("10.00", response.fee.total)
        assertTrue(requestUrlVerified, "Request URL parameters should be correct")
    }

    @Test
    fun testPriceValidationBothAmountsProvided() = runTest {
        val mockClient = createMockClient(
            responseContent = "",
            expectedPath = "/price"
        )
        val service = QuoteService(serviceAddress, mockClient)

        val exception = assertFailsWith<IllegalArgumentException> {
            service.price(
                context = "sep6",
                sellAsset = "iso4217:BRL",
                buyAsset = "stellar:USDC:GA5Z",
                sellAmount = "100",
                buyAmount = "50"
            )
        }
        assertTrue(exception.message!!.contains("Must provide either sellAmount or buyAmount, but not both"))
    }

    @Test
    fun testPriceValidationNeitherAmountProvided() = runTest {
        val mockClient = createMockClient(
            responseContent = "",
            expectedPath = "/price"
        )
        val service = QuoteService(serviceAddress, mockClient)

        val exception = assertFailsWith<IllegalArgumentException> {
            service.price(
                context = "sep6",
                sellAsset = "iso4217:BRL",
                buyAsset = "stellar:USDC:GA5Z"
            )
        }
        assertTrue(exception.message!!.contains("Must provide either sellAmount or buyAmount, but not both"))
    }

    @Test
    fun testPrice400Error() = runTest {
        val mockClient = createMockClient(
            responseContent = """{"error": "Invalid asset format"}""",
            statusCode = HttpStatusCode.BadRequest,
            expectedPath = "/price"
        )
        val service = QuoteService(serviceAddress, mockClient)

        val exception = assertFailsWith<Sep38BadRequestException> {
            service.price(
                context = "sep6",
                sellAsset = "invalid",
                buyAsset = "stellar:USDC:GA5Z",
                sellAmount = "100"
            )
        }
        assertEquals("Invalid asset format", exception.error)
    }

    // postQuote() endpoint tests

    @Test
    fun testPostQuoteSuccessWithSellAmount() = runTest {
        val responseJson = """
            {
                "id": "$quoteId",
                "expires_at": "2021-04-30T07:42:23Z",
                "total_price": "5.42",
                "price": "5.00",
                "sell_asset": "iso4217:BRL",
                "sell_amount": "542",
                "sell_delivery_method": "PIX",
                "buy_asset": "stellar:USDC:GA5Z",
                "buy_amount": "100",
                "fee": {
                    "total": "42.00",
                    "asset": "iso4217:BRL"
                }
            }
        """.trimIndent()

        var authHeaderVerified = false
        var methodVerified = false
        val mockEngine = MockEngine { request ->
            if (request.url.encodedPath.contains("/quote")) {
                if (request.method == HttpMethod.Post) {
                    val authHeader = request.headers["Authorization"]
                    authHeaderVerified = authHeader?.contains("Bearer $jwtToken") == true
                    methodVerified = true

                    respond(
                        content = responseJson,
                        status = HttpStatusCode.Created,
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
        val service = QuoteService(serviceAddress, mockClient)

        val request = Sep38QuoteRequest(
            context = "sep6",
            sellAsset = "iso4217:BRL",
            buyAsset = "stellar:USDC:GA5Z",
            sellAmount = "542",
            sellDeliveryMethod = "PIX",
            countryCode = "BR"
        )

        val response = service.postQuote(request, jwtToken)

        assertEquals(quoteId, response.id)
        assertEquals("2021-04-30T07:42:23Z", response.expiresAt)
        assertEquals("5.42", response.totalPrice)
        assertEquals("5.00", response.price)
        assertEquals("iso4217:BRL", response.sellAsset)
        assertEquals("542", response.sellAmount)
        assertEquals("PIX", response.sellDeliveryMethod)
        assertEquals("stellar:USDC:GA5Z", response.buyAsset)
        assertEquals("100", response.buyAmount)
        assertEquals("42.00", response.fee.total)
        assertTrue(authHeaderVerified, "Authorization header should be present")
        assertTrue(methodVerified, "POST method should be used")
    }

    @Test
    fun testPostQuoteSuccessWithBuyAmount() = runTest {
        val responseJson = """
            {
                "id": "$quoteId",
                "expires_at": "2021-04-30T07:42:23Z",
                "total_price": "0.20",
                "price": "0.18",
                "sell_asset": "stellar:USDC:GA5Z",
                "sell_amount": "100",
                "buy_asset": "iso4217:BRL",
                "buy_amount": "500",
                "buy_delivery_method": "PIX",
                "fee": {
                    "total": "10.00",
                    "asset": "stellar:USDC:GA5Z"
                }
            }
        """.trimIndent()

        val mockClient = createMockClient(
            responseContent = responseJson,
            statusCode = HttpStatusCode.Created,
            expectedPath = "/quote",
            expectedMethod = HttpMethod.Post
        )
        val service = QuoteService(serviceAddress, mockClient)

        val request = Sep38QuoteRequest(
            context = "sep31",
            sellAsset = "stellar:USDC:GA5Z",
            buyAsset = "iso4217:BRL",
            buyAmount = "500",
            buyDeliveryMethod = "PIX"
        )

        val response = service.postQuote(request, jwtToken)

        assertEquals(quoteId, response.id)
        assertEquals("0.20", response.totalPrice)
        assertEquals("0.18", response.price)
        assertEquals("stellar:USDC:GA5Z", response.sellAsset)
        assertEquals("100", response.sellAmount)
        assertEquals("iso4217:BRL", response.buyAsset)
        assertEquals("500", response.buyAmount)
        assertEquals("PIX", response.buyDeliveryMethod)
        assertEquals("10.00", response.fee.total)
    }

    @Test
    fun testPostQuoteValidationBothAmountsProvided() = runTest {
        val mockClient = createMockClient(
            responseContent = "",
            expectedPath = "/quote",
            expectedMethod = HttpMethod.Post
        )
        val service = QuoteService(serviceAddress, mockClient)

        val request = Sep38QuoteRequest(
            context = "sep6",
            sellAsset = "iso4217:BRL",
            buyAsset = "stellar:USDC:GA5Z",
            sellAmount = "542",
            buyAmount = "100"
        )

        val exception = assertFailsWith<IllegalArgumentException> {
            service.postQuote(request, jwtToken)
        }
        assertTrue(exception.message!!.contains("Must provide either sellAmount or buyAmount in request, but not both"))
    }

    @Test
    fun testPostQuoteValidationNeitherAmountProvided() = runTest {
        val mockClient = createMockClient(
            responseContent = "",
            expectedPath = "/quote",
            expectedMethod = HttpMethod.Post
        )
        val service = QuoteService(serviceAddress, mockClient)

        val request = Sep38QuoteRequest(
            context = "sep6",
            sellAsset = "iso4217:BRL",
            buyAsset = "stellar:USDC:GA5Z"
        )

        val exception = assertFailsWith<IllegalArgumentException> {
            service.postQuote(request, jwtToken)
        }
        assertTrue(exception.message!!.contains("Must provide either sellAmount or buyAmount in request, but not both"))
    }

    @Test
    fun testPostQuote403Error() = runTest {
        val mockClient = createMockClient(
            responseContent = """{"error": "Permission denied"}""",
            statusCode = HttpStatusCode.Forbidden,
            expectedPath = "/quote",
            expectedMethod = HttpMethod.Post
        )
        val service = QuoteService(serviceAddress, mockClient)

        val request = Sep38QuoteRequest(
            context = "sep6",
            sellAsset = "iso4217:BRL",
            buyAsset = "stellar:USDC:GA5Z",
            sellAmount = "542"
        )

        val exception = assertFailsWith<Sep38PermissionDeniedException> {
            service.postQuote(request, jwtToken)
        }
        assertEquals("Permission denied", exception.error)
    }

    @Test
    fun testPostQuote400Error() = runTest {
        val mockClient = createMockClient(
            responseContent = """{"error": "Invalid delivery method"}""",
            statusCode = HttpStatusCode.BadRequest,
            expectedPath = "/quote",
            expectedMethod = HttpMethod.Post
        )
        val service = QuoteService(serviceAddress, mockClient)

        val request = Sep38QuoteRequest(
            context = "sep6",
            sellAsset = "iso4217:BRL",
            buyAsset = "stellar:USDC:GA5Z",
            sellAmount = "542",
            sellDeliveryMethod = "INVALID"
        )

        val exception = assertFailsWith<Sep38BadRequestException> {
            service.postQuote(request, jwtToken)
        }
        assertEquals("Invalid delivery method", exception.error)
    }

    // getQuote() endpoint tests

    @Test
    fun testGetQuoteSuccess() = runTest {
        val responseJson = """
            {
                "id": "$quoteId",
                "expires_at": "2021-04-30T07:42:23Z",
                "total_price": "5.42",
                "price": "5.00",
                "sell_asset": "iso4217:BRL",
                "sell_amount": "542",
                "sell_delivery_method": "PIX",
                "buy_asset": "stellar:USDC:GA5Z",
                "buy_amount": "100",
                "fee": {
                    "total": "42.00",
                    "asset": "iso4217:BRL"
                }
            }
        """.trimIndent()

        var authHeaderVerified = false
        val mockEngine = MockEngine { request ->
            if (request.url.encodedPath.contains("/quote/$quoteId")) {
                val authHeader = request.headers["Authorization"]
                authHeaderVerified = authHeader?.contains("Bearer $jwtToken") == true

                respond(
                    content = responseJson,
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
        val service = QuoteService(serviceAddress, mockClient)

        val response = service.getQuote(quoteId, jwtToken)

        assertEquals(quoteId, response.id)
        assertEquals("2021-04-30T07:42:23Z", response.expiresAt)
        assertEquals("5.42", response.totalPrice)
        assertEquals("5.00", response.price)
        assertEquals("iso4217:BRL", response.sellAsset)
        assertEquals("542", response.sellAmount)
        assertEquals("PIX", response.sellDeliveryMethod)
        assertEquals("stellar:USDC:GA5Z", response.buyAsset)
        assertEquals("100", response.buyAmount)
        assertEquals("42.00", response.fee.total)
        assertTrue(authHeaderVerified, "Authorization header should be present")
    }

    @Test
    fun testGetQuote404Error() = runTest {
        val mockClient = createMockClient(
            responseContent = """{"error": "Quote not found"}""",
            statusCode = HttpStatusCode.NotFound,
            expectedPath = "/quote/$quoteId"
        )
        val service = QuoteService(serviceAddress, mockClient)

        val exception = assertFailsWith<Sep38NotFoundException> {
            service.getQuote(quoteId, jwtToken)
        }
        assertEquals("Quote not found", exception.error)
    }

    @Test
    fun testGetQuote403Error() = runTest {
        val mockClient = createMockClient(
            responseContent = """{"error": "Invalid token"}""",
            statusCode = HttpStatusCode.Forbidden,
            expectedPath = "/quote/$quoteId"
        )
        val service = QuoteService(serviceAddress, mockClient)

        val exception = assertFailsWith<Sep38PermissionDeniedException> {
            service.getQuote(quoteId, jwtToken)
        }
        assertEquals("Invalid token", exception.error)
    }
}
