package com.soneso.stellar.sdk.unitTests.horizon.requests

import com.soneso.stellar.sdk.horizon.HorizonServer
import com.soneso.stellar.sdk.horizon.requests.EventListener
import com.soneso.stellar.sdk.horizon.requests.RequestBuilder
import com.soneso.stellar.sdk.horizon.requests.SSEStream
import com.soneso.stellar.sdk.horizon.responses.Pageable
import com.soneso.stellar.sdk.horizon.responses.Response
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.utils.io.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlin.time.Duration.Companion.milliseconds

/**
 * Tests for [SSEStream] against the JVM `sseRequest` implementation.
 *
 * The stream runs its monitor and connection coroutines on [kotlinx.coroutines.Dispatchers.Default]
 * with real `delay`s, so these tests wait on wall-clock time instead of using `runTest`'s virtual
 * time. The HTTP client is a Ktor `MockEngine` with `HttpTimeout` installed, which `sseRequest`
 * requires because it configures per-request timeouts.
 */
class SSEStreamTest {

    /**
     * Minimal pageable payload. SSE framing and cursor handling are the subject here, so the
     * event body stays independent of any particular Horizon resource schema.
     */
    @Serializable
    private data class StreamedRecord(
        @SerialName("id") val id: String,
        @SerialName("paging_token") override val pagingToken: String,
        @SerialName("value") val value: String = ""
    ) : Response(), Pageable

    private class RecordingListener : EventListener<StreamedRecord> {
        val events = CopyOnWriteArrayList<StreamedRecord>()
        val failures = CopyOnWriteArrayList<Pair<Throwable?, Int?>>()

        override fun onEvent(event: StreamedRecord) {
            events.add(event)
        }

        override fun onFailure(error: Throwable?, responseCode: Int?) {
            failures.add(error to responseCode)
        }
    }

    /** Request builder without path segments, so repeated connects target a stable URL. */
    private class StreamEndpointRequestBuilder(
        httpClient: HttpClient,
        serverUri: Url
    ) : RequestBuilder(httpClient, serverUri, null)

    private val openStreams = CopyOnWriteArrayList<SSEStream<StreamedRecord>>()

    @AfterTest
    fun closeOpenStreams() {
        openStreams.forEach { it.close() }
        openStreams.clear()
    }

    private fun record(id: String, token: String, value: String): String =
        """{"id":"$id","paging_token":"$token","value":"$value"}"""

    /** Frames one SSE event: an `id` field, a `data` field and the terminating blank line. */
    private fun sseEvent(id: String?, data: String): String = buildString {
        if (id != null) append("id: ").append(id).append('\n')
        append("data: ").append(data).append('\n')
        append('\n')
    }

    private fun sseClient(engine: MockEngine): HttpClient = HttpClient(engine) {
        install(HttpTimeout)
    }

    private fun awaitUntil(timeoutMillis: Long = 10_000, description: String, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return
            Thread.sleep(5)
        }
        fail("Timed out after $timeoutMillis ms waiting for: $description")
    }

    private fun startStream(
        engine: MockEngine,
        listener: RecordingListener,
        reconnectTimeoutMillis: Long = 30_000,
        serverUri: String = SERVER_URI
    ): SSEStream<StreamedRecord> {
        val client = sseClient(engine)
        val builder = StreamEndpointRequestBuilder(client, Url(serverUri))
        val stream = SSEStream.create(
            httpClient = client,
            requestBuilder = builder,
            serializer = StreamedRecord.serializer(),
            listener = listener,
            reconnectTimeout = reconnectTimeoutMillis.milliseconds
        )
        openStreams.add(stream)
        return stream
    }

    /** MockEngine that replays [bodies] in order, repeating the last one for further requests. */
    private fun engineServing(vararg bodies: String): Pair<MockEngine, CopyOnWriteArrayList<HttpRequestData>> {
        val requests = CopyOnWriteArrayList<HttpRequestData>()
        val index = AtomicInteger(0)
        val engine = MockEngine { request ->
            requests.add(request)
            val body = bodies[minOf(index.getAndIncrement(), bodies.size - 1)]
            respond(
                content = ByteReadChannel(body),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "text/event-stream")
            )
        }
        return engine to requests
    }

    @Test
    fun testStreamDeliversParsedEventsToListener() {
        val body = sseEvent("1", record("a", "1", "first")) + sseEvent("2", record("b", "2", "second"))
        val (engine, _) = engineServing(body)
        val listener = RecordingListener()

        startStream(engine, listener)

        awaitUntil(description = "two events delivered") { listener.events.size >= 2 }
        assertContentEquals(listOf("a", "b"), listener.events.take(2).map { it.id })
        assertContentEquals(listOf("first", "second"), listener.events.take(2).map { it.value })
        assertTrue(listener.failures.isEmpty(), "No failure expected, got ${listener.failures}")
    }

    @Test
    fun testStreamIgnoresHelloAndByebyeControlMessages() {
        val body = sseEvent(null, "\"hello\"") +
            sseEvent("7", record("real", "7", "payload")) +
            sseEvent(null, "\"byebye\"")
        val (engine, _) = engineServing(body)
        val listener = RecordingListener()

        startStream(engine, listener)

        awaitUntil(description = "the single non-control event") { listener.events.isNotEmpty() }
        Thread.sleep(150)
        assertEquals(1, listener.events.size, "Control messages must not be delivered")
        assertEquals("real", listener.events[0].id)
        assertTrue(listener.failures.isEmpty(), "Control messages must not be reported as failures")
    }

    @Test
    fun testStreamSkipsCommentEventTypeAndRetryFields() {
        val body = ": keep-alive comment\n" +
            "event: transaction\n" +
            "retry: 5000\n" +
            "id: 42\n" +
            "data: ${record("only", "42", "value")}\n" +
            "\n"
        val (engine, _) = engineServing(body)
        val listener = RecordingListener()

        val stream = startStream(engine, listener)

        awaitUntil(description = "event past the ignored fields") { listener.events.isNotEmpty() }
        assertEquals(1, listener.events.size)
        assertEquals("only", listener.events[0].id)
        assertEquals("42", stream.lastPagingToken)
    }

    @Test
    fun testStreamJoinsDataSplitAcrossMultipleLines() {
        val body = "id: 9\n" +
            "data: {\"id\":\"split\",\n" +
            "data: \"paging_token\":\"9\",\"value\":\"joined\"}\n" +
            "\n"
        val (engine, _) = engineServing(body)
        val listener = RecordingListener()

        startStream(engine, listener)

        awaitUntil(description = "event assembled from two data lines") { listener.events.isNotEmpty() }
        assertEquals("split", listener.events[0].id)
        assertEquals("joined", listener.events[0].value)
    }

    @Test
    fun testLastPagingTokenReflectsMostRecentEventId() {
        val body = sseEvent("100", record("a", "100", "x")) + sseEvent("200", record("b", "200", "y"))
        val (engine, _) = engineServing(body)
        val listener = RecordingListener()

        val stream = startStream(engine, listener)

        assertNull(stream.lastPagingToken, "No cursor is known before the first event")
        awaitUntil(description = "two events delivered") { listener.events.size >= 2 }
        assertEquals("200", stream.lastPagingToken)
    }

    @Test
    fun testReconnectResumesFromLastEventIdAndCursor() {
        val first = sseEvent("55", record("a", "77", "x"))
        val (engine, requests) = engineServing(first, "")
        val listener = RecordingListener()

        startStream(engine, listener)

        awaitUntil(description = "a reconnect after the first response ended") { requests.size >= 2 }
        val reconnect = requests[1]
        assertEquals("55", reconnect.headers["Last-Event-ID"], "Reconnect resumes from the last event id")
        assertEquals("77", reconnect.url.parameters["cursor"], "Reconnect carries the last paging token as cursor")
        assertNull(requests[0].headers["Last-Event-ID"], "The initial connect has no event id to resume from")
        assertNull(requests[0].url.parameters["cursor"], "The initial connect has no cursor")
    }

    @Test
    fun testStreamRequestCarriesSseHeadersAndClientIdentification() {
        val (engine, requests) = engineServing(sseEvent("1", record("a", "1", "x")))
        val listener = RecordingListener()

        startStream(engine, listener)

        awaitUntil(description = "the initial request") { requests.isNotEmpty() }
        val request = requests[0]
        assertEquals("text/event-stream", request.headers[HttpHeaders.Accept])
        assertEquals("no-cache", request.headers[HttpHeaders.CacheControl])
        assertEquals("kotlin-stellar-sdk", request.url.parameters["X-Client-Name"])
        assertNotNull(request.url.parameters["X-Client-Version"], "SDK version is sent for server-side tracking")
    }

    @Test
    fun testHttpErrorStatusIsReportedToListener() {
        val requests = CopyOnWriteArrayList<HttpRequestData>()
        val engine = MockEngine { request ->
            requests.add(request)
            respond(
                content = ByteReadChannel("""{"title":"Internal Server Error"}"""),
                status = HttpStatusCode.InternalServerError,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val listener = RecordingListener()

        startStream(engine, listener)

        awaitUntil(description = "the error to reach the listener") { listener.failures.isNotEmpty() }
        val (error, code) = listener.failures[0]
        assertEquals(500, code, "The HTTP status is passed through to the listener")
        assertNotNull(error)
        assertTrue(
            error.message?.contains("Internal Server Error") == true,
            "The response body is included in the reported error, got: ${error.message}"
        )
        assertTrue(listener.events.isEmpty(), "An error response yields no events")
    }

    @Test
    fun testUnparsableEventDataIsReportedWithoutStoppingTheStream() {
        val body = sseEvent("1", """{"id":"missing-token"}""") +
            sseEvent("2", record("good", "2", "value"))
        val (engine, _) = engineServing(body)
        val listener = RecordingListener()

        startStream(engine, listener)

        awaitUntil(description = "the deserialization failure") { listener.failures.isNotEmpty() }
        awaitUntil(description = "the well-formed event that follows") { listener.events.isNotEmpty() }
        assertNotNull(listener.failures[0].first)
        assertNull(listener.failures[0].second, "A deserialization failure carries no HTTP status")
        assertEquals("good", listener.events[0].id, "Parsing resumes with the next event")
    }

    @Test
    fun testCloseStopsEventDeliveryAndReconnection() {
        val channels = CopyOnWriteArrayList<ByteChannel>()
        val requests = CopyOnWriteArrayList<HttpRequestData>()
        val engine = MockEngine { request ->
            requests.add(request)
            val channel = ByteChannel(autoFlush = true)
            channels.add(channel)
            respond(
                content = channel,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "text/event-stream")
            )
        }
        val listener = RecordingListener()

        val stream = startStream(engine, listener, reconnectTimeoutMillis = 500)

        awaitUntil(description = "the connection to be established") { channels.isNotEmpty() }
        runBlocking { channels[0].writeStringUtf8(sseEvent("1", record("before", "1", "x"))) }
        awaitUntil(description = "the event sent before close") { listener.events.isNotEmpty() }

        stream.close()
        val requestsAtClose = requests.size
        // close() cancels the request coroutine, which may already have torn the response channel
        // down; either way the event pushed afterwards must not reach the listener.
        runBlocking { runCatching { channels[0].writeStringUtf8(sseEvent("2", record("after", "2", "y"))) } }
        Thread.sleep(1_200)

        assertEquals(1, listener.events.size, "No event is delivered after close()")
        assertEquals("before", listener.events[0].id)
        assertEquals(
            requestsAtClose,
            requests.size,
            "close() stops the monitor, so no reconnect is attempted despite the short reconnect timeout"
        )
    }

    @Test
    fun testStreamReconnectsWhenNoEventArrivesWithinReconnectTimeout() {
        val channels = CopyOnWriteArrayList<ByteChannel>()
        val requests = CopyOnWriteArrayList<HttpRequestData>()
        val engine = MockEngine { request ->
            requests.add(request)
            val channel = ByteChannel(autoFlush = true)
            channels.add(channel)
            respond(
                content = channel,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "text/event-stream")
            )
        }
        val listener = RecordingListener()

        startStream(engine, listener, reconnectTimeoutMillis = 400)

        awaitUntil(description = "a reconnect on an idle connection") { requests.size >= 3 }
        assertTrue(
            listener.events.isEmpty(),
            "An idle connection produces no events"
        )
    }

    @Test
    fun testRequestBuilderStreamUsesTheBuilderEndpoint() {
        val (engine, requests) = engineServing(sseEvent("1", record("a", "1", "x")))
        val client = sseClient(engine)
        val listener = RecordingListener()

        val stream = HorizonServer(SERVER_URI, client)
            .transactions()
            .stream(StreamedRecord.serializer(), listener, 30_000.milliseconds)
        try {
            awaitUntil(description = "the initial request") { requests.isNotEmpty() }
            assertEquals("/transactions", requests[0].url.encodedPath)
            awaitUntil(description = "the event delivered through the builder stream") {
                listener.events.isNotEmpty()
            }
            assertEquals("a", listener.events[0].id)
        } finally {
            stream.close()
        }
    }

    @Test
    fun testBuildUrlIsIdempotentAcrossReconnects() {
        val engine = MockEngine { respond("{}") }
        val builder = HorizonServer(SERVER_URI, sseClient(engine)).transactions()

        val first = builder.buildUrl()
        val second = builder.buildUrl()

        // Pinning the expected value as well as the equality: two calls agreeing on a wrong
        // path would otherwise satisfy the test.
        assertEquals("/transactions", first.encodedPath)
        assertEquals(
            first.toString(),
            second.toString(),
            "Rebuilding the URL must target the same endpoint; SSEStream rebuilds it on every reconnect"
        )
    }

    @Test
    fun testReconnectTargetsTheSameEndpointAsTheInitialConnect() {
        val (engine, requests) = engineServing(sseEvent("1", record("a", "1", "x")), "")
        val client = sseClient(engine)
        val listener = RecordingListener()

        val stream = HorizonServer(SERVER_URI, client)
            .transactions()
            .stream(StreamedRecord.serializer(), listener, 30_000.milliseconds)
        try {
            awaitUntil(description = "a reconnect after the first response ended") { requests.size >= 2 }
            assertEquals(
                "/transactions",
                requests[1].url.encodedPath,
                "Reconnect must hit the same endpoint, not a duplicated path"
            )
        } finally {
            stream.close()
        }
    }

    @Test
    fun testClaimableBalanceStreamAsksForTheHorizonHexOfEverySpelling() {
        // A stream builds its URL from the request builder's path segments, so the spelling the
        // builder normalizes an id to is the one every reconnect carries.
        for (spelling in CLAIMABLE_BALANCE_SPELLINGS) {
            val (engine, requests) = engineServing(sseEvent("1", record("a", "1", "x")))
            val listener = RecordingListener()
            val stream = HorizonServer(SERVER_URI, sseClient(engine))
                .transactions()
                .forClaimableBalance(spelling)
                .stream(StreamedRecord.serializer(), listener, 30_000.milliseconds)
            try {
                awaitUntil(description = "the stream connects for spelling $spelling") {
                    requests.isNotEmpty()
                }
                assertEquals(
                    "/claimable_balances/$CLAIMABLE_BALANCE_HORIZON_HEX/transactions",
                    requests[0].url.encodedPath,
                    "spelling: $spelling"
                )
            } finally {
                stream.close()
            }
        }
    }

    @Test
    fun testClaimableBalanceStreamReportsAnIdNamingNoBalanceBeforeConnecting() {
        val (engine, requests) = engineServing("")
        assertFailsWith<IllegalArgumentException> {
            HorizonServer(SERVER_URI, sseClient(engine))
                .transactions()
                .forClaimableBalance("not a claimable balance id")
        }
        assertTrue(requests.isEmpty(), "no request may be sent for an id naming no balance")
    }

    companion object {
        private const val SERVER_URI = "https://horizon-testnet.stellar.org"

        /** The 32-byte hash of the claimable balance the spelling vectors name. */
        private const val CLAIMABLE_BALANCE_HASH_HEX =
            "3f0c34bf93ad0d9971d04ccc90f705511c838aad9734a4a2fb0d7a03fc7fe89a"

        /** The spelling Horizon serves: the hexadecimal of the XDR form. */
        private const val CLAIMABLE_BALANCE_HORIZON_HEX = "00000000$CLAIMABLE_BALANCE_HASH_HEX"

        /** Every spelling of the one balance, the strkey and a case variant among them. */
        private val CLAIMABLE_BALANCE_SPELLINGS = listOf(
            "BAAD6DBUX6J22DMZOHIEZTEQ64CVCHEDRKWZONFEUL5Q26QD7R76RGR4TU",
            CLAIMABLE_BALANCE_HASH_HEX,
            "00$CLAIMABLE_BALANCE_HASH_HEX",
            CLAIMABLE_BALANCE_HORIZON_HEX,
            CLAIMABLE_BALANCE_HASH_HEX.uppercase()
        )
    }
}
