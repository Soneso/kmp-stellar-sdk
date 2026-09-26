package com.soneso.stellar.sdk.unitTests

import com.soneso.stellar.sdk.Util
import io.ktor.client.*
import io.ktor.client.request.*
import kotlin.concurrent.Volatile
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Records the first request [client] sends, as the client's plugins built it, and aborts every
 * request in the send pipeline before it reaches the engine.
 *
 * `DefaultRequest` and the other request plugins have applied by the time the send pipeline
 * runs, so the recorded request shows what a real client puts on the wire, and no network access
 * takes place. Every request through [client] then fails with [RequestCapturedException].
 */
internal class FirstRequestCapture(client: HttpClient) {

    /** The first request [client] sent, or null while none has been sent. */
    @Volatile
    var request: HttpRequestBuilder? = null
        private set

    init {
        client.sendPipeline.intercept(HttpSendPipeline.Before) {
            if (request == null) {
                request = HttpRequestBuilder().takeFrom(context)
            }
            throw RequestCapturedException()
        }
    }
}

/** Thrown by [FirstRequestCapture] in place of sending a request. */
internal class RequestCapturedException : IllegalStateException("Request captured before network access")

/**
 * Sends one GET request to [url] through [client] and returns it as recorded by
 * [FirstRequestCapture]. Installs the capture on [client], so every later request through it is
 * aborted as well.
 */
internal suspend fun captureFirstRequest(
    client: HttpClient,
    url: String = "https://example.org/"
): HttpRequestBuilder {
    val capture = FirstRequestCapture(client)
    try {
        client.get(url)
    } catch (_: RequestCapturedException) {
    }
    return capture.request ?: error("No request reached the send pipeline")
}

/**
 * Asserts that [request] carries exactly one `X-Client-Name` equal to `kmp-stellar-sdk` (the
 * value of `Util.CLIENT_NAME`) and exactly one `X-Client-Version` equal to [Util.getSdkVersion],
 * as headers and not as query parameters.
 */
internal fun assertSdkClientIdentification(request: HttpRequestBuilder) {
    assertEquals(listOf("kmp-stellar-sdk"), request.headers.getAll("X-Client-Name"))
    assertEquals(listOf(Util.getSdkVersion()), request.headers.getAll("X-Client-Version"))
    assertNull(request.url.parameters["X-Client-Name"], "Client identification travels in headers only")
    assertNull(request.url.parameters["X-Client-Version"], "Client identification travels in headers only")
}
