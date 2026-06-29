package com.soneso.smartdemo.agent

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CoordinationClientTest {

    private fun requestJson(
        id: String = "req-1",
        status: String = "pending",
        args: List<String> = listOf("AAAA", "BBBB"),
        resultHash: String? = null,
        note: String? = null,
        includeAmount: Boolean = true,
        includeArgs: Boolean = true,
    ): String = buildJsonObject {
        put("id", id)
        put("smartAccount", "CSMART")
        put("target", "CTARGET")
        put("targetFn", "transfer")
        if (includeArgs) putJsonArray("args") { args.forEach { add(it) } }
        if (includeAmount) put("amount", "10.5")
        put("reason", 3016)
        put("status", status)
        put("createdAt", 1_782_485_036_185L)
        if (resultHash != null) put("resultHash", resultHash)
        if (note != null) put("note", note)
    }.toString()

    private val json = Json { ignoreUnknownKeys = true }

    private fun decode(text: String): CoordinationRequest =
        json.decodeFromString(CoordinationRequest.serializer(), text)

    // MARK: - CoordinationRequest decoding

    @Test
    fun parsesAFullRecordIncludingAbsentOptionals() {
        val request = decode(requestJson())
        assertEquals("req-1", request.id)
        assertEquals("CSMART", request.smartAccount)
        assertEquals("transfer", request.targetFn)
        assertEquals(listOf("AAAA", "BBBB"), request.args)
        assertEquals("10.5", request.amount)
        assertEquals(3016, request.reason)
        assertEquals("pending", request.status)
        assertNull(request.resolvedAt)
        assertNull(request.resultHash)
        assertNull(request.note)
        assertFalse(request.isResolved)
    }

    @Test
    fun isResolvedIsTrueForApprovedAndRejected() {
        assertTrue(decode(requestJson(status = "approved")).isResolved)
        assertTrue(decode(requestJson(status = "rejected")).isResolved)
    }

    @Test
    fun aMissingAmountDecodesAsTheEmptyString() {
        assertEquals("", decode(requestJson(includeAmount = false)).amount)
    }

    @Test
    fun aMissingArgsDecodesAsAnEmptyList() {
        // The decode tolerates an absent args array so the client stays compatible
        // with the server's wire contract instead of rejecting an omitted empty list.
        assertEquals(emptyList<String>(), decode(requestJson(includeArgs = false)).args)
    }

    // MARK: - HttpCoordinationClient

    private fun client(engine: MockEngine, baseUrl: String = "http://localhost:8787", token: String = "dev-token-change-me") =
        HttpCoordinationClient(baseUrl, token, HttpClient(engine))

    @Test
    fun createRequestPostsToRequestsWithTheBearerTokenAndExactBody() = runBlocking {
        var captured: HttpRequestData? = null
        val engine = MockEngine { request ->
            captured = request
            respond(requestJson(args = listOf("AAAA")), HttpStatusCode.Created, headersOf(HttpHeaders.ContentType, "application/json"))
        }

        val created = client(engine).createRequest(
            smartAccount = "CSMART",
            target = "CTARGET",
            targetFn = "transfer",
            args = listOf("AAAA"),
            amount = "10.5",
            reason = 3016,
        )

        assertEquals("req-1", created.id)
        assertEquals("pending", created.status)

        val req = assertNotNull(captured)
        assertEquals("POST", req.method.value)
        assertEquals("http://localhost:8787/requests", req.url.toString())
        assertEquals("Bearer dev-token-change-me", req.headers[HttpHeaders.Authorization])

        val body = Json.parseToJsonElement((req.body as TextContent).text).jsonObject
        assertEquals("CSMART", body["smartAccount"]!!.jsonPrimitive.content)
        assertEquals("CTARGET", body["target"]!!.jsonPrimitive.content)
        assertEquals("transfer", body["targetFn"]!!.jsonPrimitive.content)
        assertEquals(listOf("AAAA"), body["args"]!!.jsonArray.map { it.jsonPrimitive.content })
        assertEquals("10.5", body["amount"]!!.jsonPrimitive.content)
        assertEquals(3016, body["reason"]!!.jsonPrimitive.content.toInt())
    }

    @Test
    fun createRequestOmitsAmountFromTheBodyWhenNull() = runBlocking {
        var captured: HttpRequestData? = null
        val engine = MockEngine { request ->
            captured = request
            respond(requestJson(), HttpStatusCode.Created, headersOf(HttpHeaders.ContentType, "application/json"))
        }

        client(engine, token = "t").createRequest("CSMART", "CTARGET", "transfer", listOf("AAAA"), null, 3016)

        val body = Json.parseToJsonElement((assertNotNull(captured).body as TextContent).text).jsonObject
        assertFalse(body.containsKey("amount"))
    }

    @Test
    fun createRequestMapsANon201ErrorResponseToCoordinationException(): Unit = runBlocking {
        val engine = MockEngine {
            respond("""{"error":"bad body"}""", HttpStatusCode.BadRequest, headersOf(HttpHeaders.ContentType, "application/json"))
        }
        val error = assertFailsWith<CoordinationException> {
            client(engine, token = "t").createRequest("CSMART", "CTARGET", "transfer", emptyList(), null, 3016)
        }
        assertEquals(400, error.statusCode)
        assertTrue(error.message.contains("bad body"))
    }

    @Test
    fun getRequestGetsRequestsByIdWithTheBearerTokenAndTrimsTrailingSlash() = runBlocking {
        var captured: HttpRequestData? = null
        val engine = MockEngine { request ->
            captured = request
            respond(
                requestJson(status = "approved", resultHash = "RESULTHASH"),
                HttpStatusCode.OK,
                headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }

        // Trailing slash on the base URL must be trimmed.
        val request = client(engine, baseUrl = "http://localhost:8787/", token = "tok").getRequest("req-1")
        assertEquals("approved", request.status)
        assertEquals("RESULTHASH", request.resultHash)

        val req = assertNotNull(captured)
        assertEquals("GET", req.method.value)
        assertEquals("http://localhost:8787/requests/req-1", req.url.toString())
        assertEquals("Bearer tok", req.headers[HttpHeaders.Authorization])
    }

    @Test
    fun getRequestMapsA404ToCoordinationException(): Unit = runBlocking {
        val engine = MockEngine {
            respond("""{"error":"not found"}""", HttpStatusCode.NotFound, headersOf(HttpHeaders.ContentType, "application/json"))
        }
        val error = assertFailsWith<CoordinationException> {
            client(engine, token = "t").getRequest("missing")
        }
        assertEquals(404, error.statusCode)
    }
}
