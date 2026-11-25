package com.soneso.stellar.sdk.horizon.requests

import com.soneso.stellar.sdk.HttpTimeouts
import com.soneso.stellar.sdk.horizon.responses.Response
import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.serialization.KSerializer
import java.io.IOException
import java.net.SocketException
import kotlin.coroutines.coroutineContext

/**
 * JVM implementation of SSE request handling.
 * Uses Ktor's streaming API to read SSE events line by line.
 *
 * The client identification headers `X-Client-Name` and `X-Client-Version` come from the
 * client's `DefaultRequest` configuration, as for every other Horizon request.
 */
internal actual suspend fun <T : Response> sseRequest(
    httpClient: HttpClient,
    url: Url,
    lastEventId: String?,
    serializer: KSerializer<T>,
    onEvent: (eventId: String?, data: String) -> Unit,
    onFailure: (error: Throwable, statusCode: Int?) -> Unit,
    onClose: () -> Unit
) {
    try {
        httpClient.prepareGet(url) {
            headers {
                append(HttpHeaders.Accept, "text/event-stream")
                append(HttpHeaders.CacheControl, "no-cache")
                if (lastEventId != null) {
                    append("Last-Event-ID", lastEventId)
                }
            }
            timeout {
                requestTimeoutMillis = null  // Infinite timeout for SSE streams
                connectTimeoutMillis = HttpTimeouts.SSE_CONNECT_TIMEOUT_MS
                socketTimeoutMillis = null  // Infinite timeout for SSE streams
            }
        }.execute { response ->
            val statusCode = response.status.value

            if (statusCode !in 200..299) {
                val body = try {
                    response.bodyAsText()
                } catch (e: Exception) {
                    ""
                }
                onFailure(HttpResponseException(response.status, body), statusCode)
                return@execute
            }

            val channel = response.bodyAsChannel()
            parseSSEStream(channel, onEvent, onFailure, statusCode)
        }
    } catch (e: IOException) {
        onFailure(e, null)
    } catch (e: Exception) {
        if (coroutineContext.isActive) {
            onFailure(e, null)
        }
    } finally {
        coroutineContext.ensureActive()
        onClose()
    }
}

/**
 * JVM implementation of network error detection.
 */
internal actual fun isNetworkError(error: Throwable): Boolean {
    return error is IOException || error is SocketException
}
