package com.soneso.smartdemo.coordination

import io.ktor.client.request.header
import io.ktor.client.request.options
import io.ktor.client.request.post
import io.ktor.client.request.get
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val TOKEN = "test-token-123"

class HttpTest {

    private fun httpTest(
        store: RequestStore = RequestStore(),
        block: suspend ApplicationTestBuilder.() -> Unit,
    ) = testApplication {
        application { coordinationModule(store, TOKEN) }
        block()
    }

    private fun createBody(
        smartAccount: String = "CSMART",
        target: String = "CTARGET",
        targetFn: String = "transfer",
        args: List<String> = listOf("AAAA", "BBBB"),
        amount: String? = "10.5",
        reason: JsonElement = JsonPrimitive(3016),
    ): JsonObject = buildJsonObject {
        put("smartAccount", smartAccount)
        put("target", target)
        put("targetFn", targetFn)
        putJsonArray("args") { args.forEach { add(it) } }
        if (amount != null) put("amount", amount)
        put("reason", reason)
    }

    private suspend fun HttpResponse.obj(): JsonObject = Json.parseToJsonElement(bodyAsText()).jsonObject

    private suspend fun ApplicationTestBuilder.post(path: String, body: String): HttpResponse =
        client.post(path) {
            header(HttpHeaders.Authorization, "Bearer $TOKEN")
            contentType(ContentType.Application.Json)
            setBody(body)
        }

    private suspend fun ApplicationTestBuilder.authedGet(path: String): HttpResponse =
        client.get(path) { header(HttpHeaders.Authorization, "Bearer $TOKEN") }

    private suspend fun ApplicationTestBuilder.createRequest(body: JsonObject = createBody()): String =
        post("/requests", body.toString()).obj()["id"]!!.jsonPrimitive.content

    // MARK: - health

    @Test
    fun healthReturnsOkWithoutAuth() = httpTest {
        val response = client.get("/health")
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("ok", response.obj()["status"]!!.jsonPrimitive.content)
    }

    // MARK: - auth

    @Test
    fun rejectsAMissingAuthorizationHeaderWith401() = httpTest {
        val response = client.get("/requests")
        assertEquals(HttpStatusCode.Unauthorized, response.status)
        assertTrue(response.obj()["error"]!!.jsonPrimitive.content.isNotEmpty())
    }

    @Test
    fun rejectsAWrongBearerTokenWith401() = httpTest {
        val response = client.get("/requests") { header(HttpHeaders.Authorization, "Bearer wrong") }
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun rejectsANonBearerSchemeWith401() = httpTest {
        val response = client.get("/requests") { header(HttpHeaders.Authorization, "Basic $TOKEN") }
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun rejectsASameLengthWrongBearerTokenWith401() = httpTest {
        // A wrong token of the SAME length as the configured one drives the constant-time
        // comparison through its full equal-length xor path, not the length short-circuit.
        val sameLengthWrong = "x".repeat(TOKEN.length)
        assertEquals(TOKEN.length, sameLengthWrong.length)
        assertNotEquals(TOKEN, sameLengthWrong)
        val response = client.get("/requests") { header(HttpHeaders.Authorization, "Bearer $sameLengthWrong") }
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun acceptsTheConfiguredBearerToken() = httpTest {
        assertEquals(HttpStatusCode.OK, authedGet("/requests").status)
    }

    // MARK: - POST /requests

    @Test
    fun createsAPendingRequestAndReturns201WithTheFullObject() = httpTest {
        val response = post("/requests", createBody().toString())
        assertEquals(HttpStatusCode.Created, response.status)
        val body = response.obj()
        assertTrue(body["id"]!!.jsonPrimitive.content.isNotEmpty())
        assertEquals("pending", body["status"]!!.jsonPrimitive.content)
        assertTrue(body["createdAt"]!!.jsonPrimitive.longOrNull != null)
        assertTrue(body["resolvedAt"] is JsonNull)
        assertTrue(body["resultHash"] is JsonNull)
        assertTrue(body["note"] is JsonNull)
        assertEquals("CSMART", body["smartAccount"]!!.jsonPrimitive.content)
        assertEquals("transfer", body["targetFn"]!!.jsonPrimitive.content)
        assertEquals(listOf("AAAA", "BBBB"), body["args"]!!.jsonArray.map { it.jsonPrimitive.content })
        assertEquals(3016, body["reason"]!!.jsonPrimitive.int)
    }

    @Test
    fun assignsAUuidV4Id() = httpTest {
        val id = post("/requests", createBody().toString()).obj()["id"]!!.jsonPrimitive.content
        val pattern = Regex("^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$")
        assertTrue(pattern.matches(id), "id was $id")
    }

    @Test
    fun defaultsAmountToAnEmptyStringWhenOmitted() = httpTest {
        val response = post("/requests", createBody(amount = null).toString())
        assertEquals("", response.obj()["amount"]!!.jsonPrimitive.content)
    }

    @Test
    fun coercesAnExplicitNullAmountToAnEmptyString() = httpTest {
        // The iOS and Dart servers coerce a missing OR null amount to "". An explicit
        // JSON null must be accepted and stored as the empty string, not rejected.
        val body = buildJsonObject {
            createBody(amount = null).forEach { (k, v) -> put(k, v) }
            put("amount", JsonNull)
        }
        val response = post("/requests", body.toString())
        assertEquals(HttpStatusCode.Created, response.status)
        assertEquals("", response.obj()["amount"]!!.jsonPrimitive.content)
    }

    @Test
    fun ignoresClientSuppliedIdStatusCreatedAt() = httpTest {
        val body = buildJsonObject {
            createBody().forEach { (k, v) -> put(k, v) }
            put("id", "client-id")
            put("status", "approved")
            put("createdAt", 1)
        }
        val created = post("/requests", body.toString()).obj()
        assertNotEquals("client-id", created["id"]!!.jsonPrimitive.content)
        assertEquals("pending", created["status"]!!.jsonPrimitive.content)
        assertNotEquals(1L, created["createdAt"]!!.jsonPrimitive.long)
    }

    @Test
    fun returns400OnAMalformedJsonBody() = httpTest {
        val response = post("/requests", "{not json")
        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertTrue(response.obj()["error"]!!.jsonPrimitive.content.isNotEmpty())
    }

    @Test
    fun returns400WhenARequiredFieldIsMissing() = httpTest {
        val body = buildJsonObject {
            createBody().forEach { (k, v) -> if (k != "targetFn") put(k, v) }
        }
        assertEquals(HttpStatusCode.BadRequest, post("/requests", body.toString()).status)
    }

    @Test
    fun returns400WhenReasonIsNotAnInteger() = httpTest {
        val response = post("/requests", createBody(reason = JsonPrimitive("oops")).toString())
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun returns400WhenArgsContainsANonStringElement() = httpTest {
        val response = post("/requests", """{"smartAccount":"CSMART","target":"CTARGET","targetFn":"transfer","args":["ok",5],"reason":3016}""")
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun returns400WhenARequiredStringFieldIsEmpty() = httpTest {
        val response = post("/requests", createBody(smartAccount = "").toString())
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    // MARK: - GET /requests

    @Test
    fun listsNewestFirst() = httpTest {
        val first = createRequest()
        val second = createRequest()
        val list = authedGet("/requests").obj()["requests"]!!.jsonArray
        assertEquals(2, list.size)
        assertEquals(second, list.first().jsonObject["id"]!!.jsonPrimitive.content)
        assertEquals(first, list.last().jsonObject["id"]!!.jsonPrimitive.content)
    }

    @Test
    fun filtersByStatus() = httpTest {
        val pendingId = createRequest()
        val toApprove = createRequest()
        post("/requests/$toApprove/approve", """{"resultHash":"h"}""")

        val pending = authedGet("/requests?status=pending").obj()["requests"]!!.jsonArray
        assertEquals(1, pending.size)
        assertEquals(pendingId, pending.first().jsonObject["id"]!!.jsonPrimitive.content)

        val approved = authedGet("/requests?status=approved").obj()["requests"]!!.jsonArray
        assertEquals(1, approved.size)
        assertEquals(toApprove, approved.first().jsonObject["id"]!!.jsonPrimitive.content)
    }

    @Test
    fun returns400OnAnUnknownStatusFilter() = httpTest {
        assertEquals(HttpStatusCode.BadRequest, authedGet("/requests?status=bogus").status)
    }

    // MARK: - GET /requests/{id}

    @Test
    fun returnsTheRequest() = httpTest {
        val id = createRequest()
        val response = authedGet("/requests/$id")
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(id, response.obj()["id"]!!.jsonPrimitive.content)
    }

    @Test
    fun returns404ForAnUnknownId() = httpTest {
        val response = authedGet("/requests/does-not-exist")
        assertEquals(HttpStatusCode.NotFound, response.status)
        assertTrue(response.obj()["error"]!!.jsonPrimitive.content.isNotEmpty())
    }

    // MARK: - POST /requests/{id}/approve

    @Test
    fun approvesAPendingRequestAndReturnsTheUpdatedObject() = httpTest {
        val id = createRequest()
        val response = post("/requests/$id/approve", """{"resultHash":"tx-hash-xyz"}""")
        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.obj()
        assertEquals("approved", body["status"]!!.jsonPrimitive.content)
        assertEquals("tx-hash-xyz", body["resultHash"]!!.jsonPrimitive.content)
        assertTrue(body["resolvedAt"]!!.jsonPrimitive.longOrNull != null)
    }

    @Test
    fun approve404ForAnUnknownId() = httpTest {
        assertEquals(HttpStatusCode.NotFound, post("/requests/missing/approve", """{"resultHash":"h"}""").status)
    }

    @Test
    fun approveReturns409WhenAlreadyResolved() = httpTest {
        val id = createRequest()
        post("/requests/$id/approve", """{"resultHash":"h1"}""")
        assertEquals(HttpStatusCode.Conflict, post("/requests/$id/approve", """{"resultHash":"h2"}""").status)
    }

    @Test
    fun approveReturns400WhenResultHashIsMissing() = httpTest {
        val id = createRequest()
        assertEquals(HttpStatusCode.BadRequest, post("/requests/$id/approve", "{}").status)
    }

    // MARK: - POST /requests/{id}/reject

    @Test
    fun rejectsAPendingRequestWithANote() = httpTest {
        val id = createRequest()
        val response = post("/requests/$id/reject", """{"note":"looks malicious"}""")
        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.obj()
        assertEquals("rejected", body["status"]!!.jsonPrimitive.content)
        assertEquals("looks malicious", body["note"]!!.jsonPrimitive.content)
        assertTrue(body["resolvedAt"]!!.jsonPrimitive.longOrNull != null)
    }

    @Test
    fun rejectsWithAnEmptyBodyNoteOptional() = httpTest {
        val id = createRequest()
        val response = client.post("/requests/$id/reject") {
            header(HttpHeaders.Authorization, "Bearer $TOKEN")
        }
        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.obj()["note"] is JsonNull)
    }

    @Test
    fun reject404ForAnUnknownId() = httpTest {
        val response = client.post("/requests/missing/reject") {
            header(HttpHeaders.Authorization, "Bearer $TOKEN")
        }
        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun rejectReturns409WhenAlreadyResolved() = httpTest {
        val id = createRequest()
        client.post("/requests/$id/reject") { header(HttpHeaders.Authorization, "Bearer $TOKEN") }
        val second = client.post("/requests/$id/reject") {
            header(HttpHeaders.Authorization, "Bearer $TOKEN")
        }
        assertEquals(HttpStatusCode.Conflict, second.status)
    }

    // MARK: - CORS

    @Test
    fun preflightReturns204WithCorsHeadersAndNoAuth() = httpTest {
        val response = client.options("/requests")
        assertEquals(HttpStatusCode.NoContent, response.status)
        assertEquals("*", response.headers[HttpHeaders.AccessControlAllowOrigin])
        assertTrue((response.headers[HttpHeaders.AccessControlAllowMethods] ?: "").contains("POST"))
        assertTrue((response.headers[HttpHeaders.AccessControlAllowHeaders] ?: "").contains("Authorization"))
    }

    @Test
    fun corsHeadersArePresentOnNormalResponses() = httpTest {
        assertEquals("*", client.get("/health").headers[HttpHeaders.AccessControlAllowOrigin])
    }

    @Test
    fun corsHeadersArePresentOn401Responses() = httpTest {
        val response = client.get("/requests")
        assertEquals(HttpStatusCode.Unauthorized, response.status)
        assertEquals("*", response.headers[HttpHeaders.AccessControlAllowOrigin])
    }

    @Test
    fun anUnmatchedRouteReturns404WithCorsHeadersAndAJsonErrorBody() = httpTest {
        val response = authedGet("/no-such-route")
        assertEquals(HttpStatusCode.NotFound, response.status)
        assertEquals("*", response.headers[HttpHeaders.AccessControlAllowOrigin])
        assertTrue(response.obj()["error"]!!.jsonPrimitive.content.isNotEmpty())
    }
}
