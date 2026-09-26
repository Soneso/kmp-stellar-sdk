package com.soneso.stellar.sdk.unitTests.horizon.requests

import com.soneso.stellar.sdk.Util
import com.soneso.stellar.sdk.horizon.HorizonServer
import com.soneso.stellar.sdk.horizon.requests.sseRequest
import com.soneso.stellar.sdk.horizon.responses.Response
import com.soneso.stellar.sdk.unitTests.FirstRequestCapture
import com.soneso.stellar.sdk.unitTests.assertSdkClientIdentification
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.utils.io.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.fail

/**
 * Tests for the request the native `sseRequest` implementation sends.
 *
 * The HTTP clients are Ktor `MockEngine` clients with `HttpTimeout` installed, which
 * `sseRequest` requires because it configures per-request timeouts. The mock response body is
 * empty, so the stream ends after the request and `sseRequest` returns.
 */
class SSEStreamNativeTest {

    @Serializable
    private data class StreamedRecord(@SerialName("id") val id: String) : Response()

    private val streamUrl = Url("https://horizon.example.org/transactions")

    /**
     * MockEngine client that records its requests. With [clientName] and [clientVersion] set,
     * its `DefaultRequest` carries the client identification headers the way
     * [HorizonServer.createDefaultHttpClient] does, or with the values an application injects.
     */
    private fun mockClient(
        requests: MutableList<HttpRequestData>,
        clientName: String? = null,
        clientVersion: String? = null
    ): HttpClient {
        val engine = MockEngine { request ->
            requests.add(request)
            respond(
                content = ByteReadChannel(""),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "text/event-stream")
            )
        }
        return HttpClient(engine) {
            install(HttpTimeout)
            if (clientName != null && clientVersion != null) {
                install(DefaultRequest) {
                    header("X-Client-Name", clientName)
                    header("X-Client-Version", clientVersion)
                }
            }
        }
    }

    private suspend fun captureStreamRequest(client: HttpClient, requests: List<HttpRequestData>): HttpRequestData {
        sseRequest(
            httpClient = client,
            url = streamUrl,
            lastEventId = null,
            serializer = StreamedRecord.serializer(),
            onEvent = { _, _ -> },
            onFailure = { error, _ -> fail("Unexpected stream failure: $error") },
            onClose = {}
        )

        assertEquals(1, requests.size, "Exactly one request expected")
        return requests[0]
    }

    @Test
    fun testStreamRequestCarriesSseHeadersAndClientIdentification() = runTest {
        val requests = mutableListOf<HttpRequestData>()
        val client = mockClient(requests, "kmp-stellar-sdk", Util.getSdkVersion())

        val request = captureStreamRequest(client, requests)

        assertEquals("text/event-stream", request.headers[HttpHeaders.Accept])
        assertEquals(listOf("kmp-stellar-sdk"), request.headers.getAll("X-Client-Name"))
        assertEquals(listOf(Util.getSdkVersion()), request.headers.getAll("X-Client-Version"))
        assertNull(request.url.parameters["X-Client-Name"], "Client identification travels in headers only")
        assertNull(request.url.parameters["X-Client-Version"], "Client identification travels in headers only")
    }

    @Test
    fun testStreamRequestCarriesTheInjectedClientIdentification() = runTest {
        val requests = mutableListOf<HttpRequestData>()
        val client = mockClient(requests, "my-app", "1.0")

        val request = captureStreamRequest(client, requests)

        assertEquals(listOf("my-app"), request.headers.getAll("X-Client-Name"))
        assertEquals(listOf("1.0"), request.headers.getAll("X-Client-Version"))
    }

    @Test
    fun testStreamRequestThroughClientWithoutIdentificationSendsNone() = runTest {
        val requests = mutableListOf<HttpRequestData>()
        val client = mockClient(requests)

        val request = captureStreamRequest(client, requests)

        assertNull(request.headers["X-Client-Name"])
        assertNull(request.headers["X-Client-Version"])
        assertNull(request.url.parameters["X-Client-Name"])
        assertNull(request.url.parameters["X-Client-Version"])
    }

    @Test
    fun testStreamThroughHorizonDefaultClientSendsClientIdentification() = runTest {
        // The real default client of HorizonServer; the capture aborts the request before any
        // network access.
        val client = HorizonServer.createDefaultHttpClient()
        val capture = FirstRequestCapture(client)
        try {
            sseRequest(
                httpClient = client,
                url = streamUrl,
                lastEventId = null,
                serializer = StreamedRecord.serializer(),
                onEvent = { _, _ -> },
                onFailure = { _, _ -> },
                onClose = {}
            )

            assertSdkClientIdentification(capture.request ?: fail("No request reached the send pipeline"))
        } finally {
            client.close()
        }
    }
}
