package com.soneso.smartdemo.agent

import com.soneso.smartdemo.coordination.RequestStore
import com.soneso.smartdemo.coordination.coordinationModule
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO as ClientCIO
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.cio.CIO as ServerCIO
import io.ktor.server.engine.embeddedServer
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import java.net.InetAddress
import java.net.ServerSocket
import java.util.UUID
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.junit.Assume.assumeTrue

/**
 * End-to-end verification of the coordination wire contract against the REAL
 * coordination server hosted in-process.
 *
 * Gated on the `RUN_COORDINATION_E2E` environment variable. Unless it is set to
 * `true` the single test is skipped via a JUnit assumption before any server is
 * started, so a default `:smart-account-demo:reference-agent:test` run neither
 * binds a socket nor exercises the network path.
 *
 * When enabled, the real Ktor [coordinationModule] is started on an ephemeral
 * loopback port with a per-run bearer token, its `/health` endpoint is polled
 * until ready, and the full request lifecycle is driven through:
 *   - the agent's own [HttpCoordinationClient] for the create and poll calls it
 *     makes in production (`POST /requests`, `GET /requests/{id}`), proving the
 *     agent client speaks the real server's contract; and
 *   - a plain authenticated Ktor client for the inbox-side calls the agent never
 *     makes (`GET /requests?status=`, `POST /requests/{id}/approve`,
 *     `POST /requests/{id}/reject`).
 *
 * The server is always stopped in a `finally` block.
 */
class CoordinationE2ETest {

    private val json = Json { ignoreUnknownKeys = true }

    @OptIn(ExperimentalEncodingApi::class)
    @Test
    fun drivesTheFullLifecycleAgainstTheRealServer() {
        assumeTrue(
            "RUN_COORDINATION_E2E is not 'true'; skipping in-process coordination e2e",
            System.getenv("RUN_COORDINATION_E2E") == "true",
        )

        token = "e2e-token-${UUID.randomUUID()}"
        val port = freeLoopbackPort()
        val baseUrl = "http://127.0.0.1:$port"
        val store = RequestStore()

        // The actual coordination Ktor application, bound to loopback only.
        val server = embeddedServer(ServerCIO, host = "127.0.0.1", port = port) {
            coordinationModule(store, token)
        }
        server.start(wait = false)

        val agentHttp = HttpClient(ClientCIO)
        val inbox = HttpClient(ClientCIO)
        // The exact client the reference agent uses in production.
        val agentClient = HttpCoordinationClient(baseUrl, token, agentHttp)

        try {
            runBlocking {
                awaitHealthy(inbox, baseUrl)

                // Agent-shaped escalation, built exactly as the reference agent's
                // createRequest call does: opaque base64 args, a contract error
                // reason code, and the optional amount string.
                val args = listOf(
                    Base64.encode("scval-from".encodeToByteArray()),
                    Base64.encode("scval-to".encodeToByteArray()),
                    Base64.encode("scval-amount".encodeToByteArray()),
                )
                val smartAccount = "CSMARTACCOUNTXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX"
                val target = "CTOKENXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX"
                val reason = 3016

                val created = agentClient.createRequest(
                    smartAccount = smartAccount,
                    target = target,
                    targetFn = AgentRunner.TARGET_FN,
                    args = args,
                    amount = "10.5",
                    reason = reason,
                )

                assertTrue(created.id.isNotEmpty(), "server must assign a non-empty id")
                assertEquals(CoordinationRequest.STATUS_PENDING, created.status)
                assertEquals(smartAccount, created.smartAccount)
                assertEquals(target, created.target)
                assertEquals(AgentRunner.TARGET_FN, created.targetFn)
                assertEquals(args, created.args, "server must echo the opaque base64 args verbatim")
                assertEquals("10.5", created.amount)
                assertEquals(reason, created.reason)
                assertEquals(null, created.resolvedAt)
                assertEquals(null, created.resultHash)
                assertEquals(null, created.note)
                assertTrue(created.createdAt > 0L)

                // Inbox lists the pending request and finds the created id.
                val pending = listRequests(inbox, baseUrl, status = "pending")
                val pendingIds = pending.map { it.jsonObject["id"]!!.jsonPrimitive.content }
                assertTrue(created.id in pendingIds, "created request must appear in the pending list")

                // The agent's own poll call sees the same pending record.
                val polledPending = agentClient.getRequest(created.id)
                assertEquals(created.id, polledPending.id)
                assertEquals(CoordinationRequest.STATUS_PENDING, polledPending.status)
                assertEquals(args, polledPending.args)
                assertTrue(!polledPending.isResolved)

                // Inbox approves with a result hash; request transitions to approved.
                val resultHash = "txhash-${UUID.randomUUID()}"
                val approved = approve(inbox, baseUrl, created.id, resultHash)
                assertEquals("approved", approved["status"]!!.jsonPrimitive.content)
                assertEquals(resultHash, approved["resultHash"]!!.jsonPrimitive.content)
                assertTrue(
                    approved["resolvedAt"]!!.jsonPrimitive.longOrNull != null,
                    "approval must stamp resolvedAt",
                )

                // The agent observes the resolved approval through its poll client.
                val polledApproved = agentClient.getRequest(created.id)
                assertEquals(CoordinationRequest.STATUS_APPROVED, polledApproved.status)
                assertEquals(resultHash, polledApproved.resultHash)
                assertNotNull(polledApproved.resolvedAt)
                assertTrue(polledApproved.isResolved)

                // Second escalation drives the reject path with a note.
                val second = agentClient.createRequest(
                    smartAccount = smartAccount,
                    target = target,
                    targetFn = AgentRunner.TARGET_FN,
                    args = args,
                    amount = "10.5",
                    reason = reason,
                )
                assertEquals(CoordinationRequest.STATUS_PENDING, second.status)

                val note = "rejected by policy reviewer"
                val rejected = reject(inbox, baseUrl, second.id, note)
                assertEquals("rejected", rejected["status"]!!.jsonPrimitive.content)
                assertEquals(note, rejected["note"]!!.jsonPrimitive.content)
                assertTrue(rejected["resultHash"] is JsonNull, "a rejection must not carry a resultHash")
                assertTrue(
                    rejected["resolvedAt"]!!.jsonPrimitive.longOrNull != null,
                    "rejection must stamp resolvedAt",
                )

                val polledRejected = agentClient.getRequest(second.id)
                assertEquals(CoordinationRequest.STATUS_REJECTED, polledRejected.status)
                assertEquals(note, polledRejected.note)
                assertNotNull(polledRejected.resolvedAt)
                assertTrue(polledRejected.isResolved)
            }
        } finally {
            agentHttp.close()
            inbox.close()
            server.stop(gracePeriodMillis = 200, timeoutMillis = 1_000)
        }
    }

    /** Reserves and immediately releases a free loopback TCP port. */
    private fun freeLoopbackPort(): Int =
        ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).use { it.localPort }

    /** Polls `GET /health` until the server answers `200`, or fails after the budget. */
    private suspend fun awaitHealthy(client: HttpClient, baseUrl: String) {
        repeat(HEALTH_ATTEMPTS) {
            val ready = try {
                client.get("$baseUrl/health").status == HttpStatusCode.OK
            } catch (_: Exception) {
                false
            }
            if (ready) return
            delay(HEALTH_INTERVAL_MILLIS)
        }
        error("coordination server did not become healthy on $baseUrl")
    }

    private suspend fun listRequests(client: HttpClient, baseUrl: String, status: String): JsonArray {
        val response = client.get("$baseUrl/requests?status=$status") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        assertEquals(HttpStatusCode.OK, response.status)
        return response.obj()["requests"]!!.jsonArray
    }

    private suspend fun approve(
        client: HttpClient,
        baseUrl: String,
        id: String,
        resultHash: String,
    ): JsonObject {
        val body = buildJsonObject { put("resultHash", resultHash) }.toString()
        val response = client.post("$baseUrl/requests/$id/approve") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(body)
        }
        assertEquals(HttpStatusCode.OK, response.status)
        return response.obj()
    }

    private suspend fun reject(
        client: HttpClient,
        baseUrl: String,
        id: String,
        note: String,
    ): JsonObject {
        val body = buildJsonObject { put("note", note) }.toString()
        val response = client.post("$baseUrl/requests/$id/reject") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(body)
        }
        assertEquals(HttpStatusCode.OK, response.status)
        return response.obj()
    }

    private suspend fun HttpResponse.obj(): JsonObject =
        json.parseToJsonElement(bodyAsText()).jsonObject

    /**
     * The bearer token for the inbox-side raw client. Set once at the start of the
     * gated test and read by the authenticated helpers above. Holding it as a field
     * keeps the helper signatures focused on the call being made.
     */
    private lateinit var token: String

    companion object {
        private const val HEALTH_ATTEMPTS = 100
        private const val HEALTH_INTERVAL_MILLIS = 50L
    }
}
