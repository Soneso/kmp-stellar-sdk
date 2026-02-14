package com.soneso.stellar.sdk.unitTests.horizon.responses

import com.soneso.stellar.sdk.horizon.exceptions.*
import com.soneso.stellar.sdk.horizon.responses.Link
import com.soneso.stellar.sdk.horizon.responses.Page
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.test.*

class PageGetNextPageTest {

    @Serializable
    data class TestRecord(
        @SerialName("id")
        val id: String,
        @SerialName("value")
        val value: String
    )

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun testGetNextPageSuccess() = runTest {
        val nextPageJson = """
        {
            "_embedded": {
                "records": [
                    {
                        "id": "3",
                        "value": "test3"
                    },
                    {
                        "id": "4", 
                        "value": "test4"
                    }
                ]
            },
            "_links": {
                "self": {
                    "href": "https://horizon.stellar.org/accounts?cursor=next_token&limit=10&order=asc"
                }
            }
        }
        """.trimIndent()

        val mockEngine = MockEngine { request ->
            assertEquals("https://horizon.stellar.org/accounts?cursor=next_token&limit=10&order=asc", request.url.toString())
            respond(
                content = nextPageJson,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }

        val client = HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json(json)
            }
        }

        // Create a page with next link
        val originalPage = Page<TestRecord>(
            embedded = Page.Embedded(
                records = listOf(
                    TestRecord("1", "test1"),
                    TestRecord("2", "test2")
                )
            ),
            links = Page.Links(
                self = Link("https://horizon.stellar.org/accounts?cursor=&limit=10&order=asc"),
                next = Link("https://horizon.stellar.org/accounts?cursor=next_token&limit=10&order=asc"),
                prev = null
            )
        )

        val nextPage = originalPage.getNextPage<TestRecord>(client)
        
        assertNotNull(nextPage)
        assertEquals(2, nextPage.records.size)
        assertEquals("3", nextPage.records[0].id)
        assertEquals("test3", nextPage.records[0].value)
        assertEquals("4", nextPage.records[1].id)
        assertEquals("test4", nextPage.records[1].value)

        client.close()
    }

    @Test
    fun testGetNextPageNoNextLink() = runTest {
        val page = Page<TestRecord>(
            embedded = Page.Embedded(
                records = listOf(TestRecord("1", "test1"))
            ),
            links = Page.Links(
                self = Link("https://horizon.stellar.org/accounts"),
                next = null,
                prev = null
            )
        )

        val mockEngine = MockEngine { 
            error("Should not make any request when next link is null")
        }

        val client = HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json(json)
            }
        }

        val nextPage = page.getNextPage<TestRecord>(client)
        assertNull(nextPage)

        client.close()
    }

    @Test
    fun testGetNextPageBadRequest400() = runTest {
        val mockEngine = MockEngine {
            respond(
                content = "Bad Request",
                status = HttpStatusCode.BadRequest,
                headers = headersOf(HttpHeaders.ContentType, "text/plain")
            )
        }

        val client = HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json(json)
            }
        }

        val page = Page<TestRecord>(
            embedded = null,
            links = Page.Links(
                self = Link("https://horizon.stellar.org/accounts"),
                next = Link("https://horizon.stellar.org/accounts?cursor=bad"),
                prev = null
            )
        )

        val exception = assertFailsWith<BadRequestException> {
            page.getNextPage<TestRecord>(client)
        }

        assertEquals(400, exception.code)
        assertEquals("Bad Request", exception.body)

        client.close()
    }

    @Test
    fun testGetNextPageTooManyRequests429() = runTest {
        val mockEngine = MockEngine {
            respond(
                content = "Too Many Requests",
                status = HttpStatusCode.TooManyRequests,
                headers = headersOf(HttpHeaders.ContentType, "text/plain")
            )
        }

        val client = HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json(json)
            }
        }

        val page = Page<TestRecord>(
            embedded = null,
            links = Page.Links(
                self = Link("https://horizon.stellar.org/accounts"),
                next = Link("https://horizon.stellar.org/accounts?cursor=rate_limited"),
                prev = null
            )
        )

        val exception = assertFailsWith<TooManyRequestsException> {
            page.getNextPage<TestRecord>(client)
        }

        assertEquals(429, exception.code)
        assertEquals("Too Many Requests", exception.body)

        client.close()
    }

    @Test
    fun testGetNextPageServerError500() = runTest {
        val mockEngine = MockEngine {
            respond(
                content = "Internal Server Error",
                status = HttpStatusCode.InternalServerError,
                headers = headersOf(HttpHeaders.ContentType, "text/plain")
            )
        }

        val client = HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json(json)
            }
        }

        val page = Page<TestRecord>(
            embedded = null,
            links = Page.Links(
                self = Link("https://horizon.stellar.org/accounts"),
                next = Link("https://horizon.stellar.org/accounts?cursor=server_error"),
                prev = null
            )
        )

        val exception = assertFailsWith<BadResponseException> {
            page.getNextPage<TestRecord>(client)
        }

        assertEquals(500, exception.code)
        assertEquals("Internal Server Error", exception.body)

        client.close()
    }

    @Test 
    fun testGetNextPageServerError503() = runTest {
        val mockEngine = MockEngine {
            respond(
                content = "Service Unavailable",
                status = HttpStatusCode.ServiceUnavailable,
                headers = headersOf(HttpHeaders.ContentType, "text/plain")
            )
        }

        val client = HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json(json)
            }
        }

        val page = Page<TestRecord>(
            embedded = null,
            links = Page.Links(
                self = Link("https://horizon.stellar.org/accounts"),
                next = Link("https://horizon.stellar.org/accounts?cursor=unavailable"),
                prev = null
            )
        )

        val exception = assertFailsWith<BadResponseException> {
            page.getNextPage<TestRecord>(client)
        }

        assertEquals(503, exception.code)
        assertEquals("Service Unavailable", exception.body)

        client.close()
    }

    @Test
    fun testGetNextPageUnknownStatus() = runTest {
        val mockEngine = MockEngine {
            respond(
                content = "Unknown Status",
                status = HttpStatusCode(999, "Unknown"),
                headers = headersOf(HttpHeaders.ContentType, "text/plain")
            )
        }

        val client = HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json(json)
            }
        }

        val page = Page<TestRecord>(
            embedded = null,
            links = Page.Links(
                self = Link("https://horizon.stellar.org/accounts"),
                next = Link("https://horizon.stellar.org/accounts?cursor=unknown"),
                prev = null
            )
        )

        val exception = assertFailsWith<UnknownResponseException> {
            page.getNextPage<TestRecord>(client)
        }

        assertEquals(999, exception.code)
        assertEquals("Unknown Status", exception.body)

        client.close()
    }

    @Test
    fun testGetNextPageConnectionError() = runTest {
        val mockEngine = MockEngine {
            throw RuntimeException("Network connection failed")
        }

        val client = HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json(json)
            }
        }

        val page = Page<TestRecord>(
            embedded = null,
            links = Page.Links(
                self = Link("https://horizon.stellar.org/accounts"),
                next = Link("https://horizon.stellar.org/accounts?cursor=connection_fail"),
                prev = null
            )
        )

        val exception = assertFailsWith<ConnectionErrorException> {
            page.getNextPage<TestRecord>(client)
        }

        assertNotNull(exception.cause)
        assertTrue(exception.cause is RuntimeException)
        assertEquals("Network connection failed", exception.cause!!.message)

        client.close()
    }

    @Test
    fun testGetNextPageWithOtherBadRequestStatus() = runTest {
        val mockEngine = MockEngine {
            respond(
                content = "Not Found", 
                status = HttpStatusCode.NotFound,
                headers = headersOf(HttpHeaders.ContentType, "text/plain")
            )
        }

        val client = HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json(json)
            }
        }

        val page = Page<TestRecord>(
            embedded = null,
            links = Page.Links(
                self = Link("https://horizon.stellar.org/accounts"),
                next = Link("https://horizon.stellar.org/accounts?cursor=not_found"),
                prev = null
            )
        )

        val exception = assertFailsWith<BadRequestException> {
            page.getNextPage<TestRecord>(client)
        }

        assertEquals(404, exception.code)
        assertEquals("Not Found", exception.body)

        client.close()
    }
}