package com.soneso.smartdemo.util

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Unit tests for [HttpCoordinationClient] driven by a Ktor [MockEngine].
 *
 * Asserts the inbox-side REST contract: the Bearer header on every call, the
 * `GET /requests?status=pending` listing wrapped under `requests`, the single-object
 * get/approve/reject shapes, the request bodies, and the non-2xx -> [CoordinationException]
 * mapping that extracts the `{ "error": ... }` field.
 */
class CoordinationClientTest {

    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")

    private fun client(engine: MockEngine): HttpCoordinationClient =
        HttpCoordinationClient(
            baseUrl = "https://coord.example/",
            token = "secret-token",
            client = HttpClient(engine),
        )

    private fun requestJson(
        id: String = "req-1",
        status: String = "pending",
        resultHash: String? = null,
        note: String? = null,
    ): String {
        val hashField = if (resultHash == null) "null" else "\"$resultHash\""
        val noteField = if (note == null) "null" else "\"$note\""
        return """
            {
              "id": "$id",
              "smartAccount": "CACCOUNT",
              "target": "CTOKEN",
              "targetFn": "transfer",
              "args": ["AAA", "BBB", "CCC"],
              "amount": "10",
              "reason": 3017,
              "status": "$status",
              "createdAt": 1700000000000,
              "resolvedAt": null,
              "resultHash": $hashField,
              "note": $noteField
            }
        """.trimIndent()
    }

    @Test
    fun listPendingSendsBearerAndStatusQueryAndUnwrapsRequests() = runTest {
        var seenAuth: String? = null
        var seenPath: String? = null
        var seenStatus: String? = null
        val engine = MockEngine { request ->
            seenAuth = request.headers[HttpHeaders.Authorization]
            seenPath = request.url.encodedPath
            seenStatus = request.url.parameters["status"]
            respond(
                content = """{ "requests": [ ${requestJson(id = "a")}, ${requestJson(id = "b")} ] }""",
                status = HttpStatusCode.OK,
                headers = jsonHeaders,
            )
        }

        val result = client(engine).listPending()

        assertEquals("Bearer secret-token", seenAuth)
        assertEquals("/requests", seenPath)
        assertEquals("pending", seenStatus)
        assertEquals(listOf("a", "b"), result.map { it.id })
        assertEquals(listOf("AAA", "BBB", "CCC"), result[0].args)
        assertEquals(3017, result[0].reason)
    }

    @Test
    fun listPendingTreatsMissingRequestsArrayAsEmpty() = runTest {
        val engine = MockEngine {
            respond(content = "{ }", status = HttpStatusCode.OK, headers = jsonHeaders)
        }
        assertTrue(client(engine).listPending().isEmpty())
    }

    @Test
    fun getRequestParsesSingleRecord() = runTest {
        var seenPath: String? = null
        val engine = MockEngine { request ->
            seenPath = request.url.encodedPath
            respond(content = requestJson(id = "req-7", status = "approved", resultHash = "HASH9"),
                status = HttpStatusCode.OK, headers = jsonHeaders)
        }

        val result = client(engine).getRequest("req-7")

        assertEquals("/requests/req-7", seenPath)
        assertEquals("req-7", result.id)
        assertEquals("approved", result.status)
        assertEquals("HASH9", result.resultHash)
        assertTrue(result.isResolved)
    }

    @Test
    fun approveSendsResultHashBody() = runTest {
        var seenMethod: HttpMethod? = null
        var seenPath: String? = null
        var seenBody: String? = null
        val engine = MockEngine { request ->
            seenMethod = request.method
            seenPath = request.url.encodedPath
            seenBody = (request.body as? TextContent)?.text
            respond(content = requestJson(status = "approved", resultHash = "HASHX"),
                status = HttpStatusCode.OK, headers = jsonHeaders)
        }

        val result = client(engine).approve("req-1", resultHash = "HASHX")

        assertEquals(HttpMethod.Post, seenMethod)
        assertEquals("/requests/req-1/approve", seenPath)
        assertTrue(seenBody!!.contains("\"resultHash\""))
        assertTrue(seenBody!!.contains("HASHX"))
        assertEquals("approved", result.status)
    }

    @Test
    fun rejectSendsNoteBody() = runTest {
        var seenBody: String? = null
        val engine = MockEngine { request ->
            seenBody = (request.body as? TextContent)?.text
            respond(content = requestJson(status = "rejected", note = "nope"),
                status = HttpStatusCode.OK, headers = jsonHeaders)
        }

        val result = client(engine).reject("req-1", note = "nope")

        assertTrue(seenBody!!.contains("\"note\""))
        assertTrue(seenBody!!.contains("nope"))
        assertEquals("rejected", result.status)
        assertEquals("nope", result.note)
    }

    @Test
    fun rejectOmitsNoteWhenNull() = runTest {
        var seenBody: String? = null
        val engine = MockEngine { request ->
            seenBody = (request.body as? TextContent)?.text
            respond(content = requestJson(status = "rejected"),
                status = HttpStatusCode.OK, headers = jsonHeaders)
        }

        val result = client(engine).reject("req-1", note = null)

        // explicitNulls = false drops the null note from the wire.
        assertTrue(!seenBody!!.contains("note"))
        assertNull(result.note)
    }

    @Test
    fun nonSuccessStatusMapsToCoordinationExceptionWithErrorField() = runTest {
        val engine = MockEngine {
            respond(
                content = """{ "error": "request not found" }""",
                status = HttpStatusCode.NotFound,
                headers = jsonHeaders,
            )
        }

        val ex = assertFailsWith<CoordinationException> {
            client(engine).getRequest("missing")
        }
        assertEquals(404, ex.statusCode)
        assertTrue(ex.message.contains("request not found"))
    }

    @Test
    fun conflictStatusIsReportedWithItsCode() = runTest {
        val engine = MockEngine {
            respond(
                content = """{ "error": "already resolved" }""",
                status = HttpStatusCode.Conflict,
                headers = jsonHeaders,
            )
        }

        val ex = assertFailsWith<CoordinationException> {
            client(engine).approve("req-1", resultHash = "H")
        }
        assertEquals(409, ex.statusCode)
    }
}
