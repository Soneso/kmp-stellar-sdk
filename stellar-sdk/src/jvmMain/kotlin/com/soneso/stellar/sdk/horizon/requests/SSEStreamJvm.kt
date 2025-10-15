package com.soneso.stellar.sdk.horizon.requests

import com.soneso.stellar.sdk.horizon.responses.Response
import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.utils.io.*
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.yield
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import java.io.IOException
import java.net.SocketException
import kotlin.coroutines.coroutineContext

/**
 * JVM implementation of SSE request handling.
 * Uses Ktor's streaming API to read SSE events line by line.
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
        httpClient.prepareGet(addClientIdentification(url)) {
            headers {
                append(HttpHeaders.Accept, "text/event-stream")
                append(HttpHeaders.CacheControl, "no-cache")
                if (lastEventId != null) {
                    append("Last-Event-ID", lastEventId)
                }
            }
            timeout {
                requestTimeoutMillis = HttpTimeout.INFINITE_TIMEOUT_MS
                connectTimeoutMillis = 30_000
                socketTimeoutMillis = HttpTimeout.INFINITE_TIMEOUT_MS
            }
        }.execute { response ->
            val statusCode = response.status.value

            if (statusCode !in 200..299) {
                val body = try {
                    response.bodyAsText()
                } catch (e: Exception) {
                    ""
                }
                onFailure(HttpResponseException(response, body), statusCode)
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
 * Parses an SSE stream from a ByteReadChannel.
 * Implements the SSE specification for parsing events.
 */
private suspend fun parseSSEStream(
    channel: ByteReadChannel,
    onEvent: (eventId: String?, data: String) -> Unit,
    onFailure: (error: Throwable, statusCode: Int?) -> Unit,
    statusCode: Int
) {
    var currentEventId: String? = null
    var currentData = StringBuilder()

    try {
        while (!channel.isClosedForRead) {
            coroutineContext.ensureActive()

            // Yield to allow other coroutines to run (important for event processing)
            yield()

            val line = try {
                channel.readUTF8Line() ?: break
            } catch (e: Exception) {
                if (coroutineContext.isActive) {
                    onFailure(e, statusCode)
                }
                break
            }

            when {
                line.isEmpty() -> {
                    // Empty line signals end of event
                    if (currentData.isNotEmpty()) {
                        val data = currentData.toString().trimEnd()
                        onEvent(currentEventId, data)
                        currentData = StringBuilder()
                        // Yield after processing event to allow listeners to react
                        yield()
                    }
                }
                line.startsWith(":") -> {
                    // Comment line, ignore
                    continue
                }
                line.startsWith("id:") -> {
                    currentEventId = line.substring(3).trim()
                }
                line.startsWith("data:") -> {
                    if (currentData.isNotEmpty()) {
                        currentData.append('\n')
                    }
                    currentData.append(line.substring(5).trimStart())
                }
                line.startsWith("event:") -> {
                    // Event type field - we ignore this for now
                    continue
                }
                line.startsWith("retry:") -> {
                    // Retry field - we ignore this as we have our own reconnection logic
                    continue
                }
            }
        }
    } catch (e: Exception) {
        if (coroutineContext.isActive) {
            onFailure(e, statusCode)
        }
    }
}

/**
 * Adds client identification query parameters to the URL.
 */
private fun addClientIdentification(url: Url): Url {
    return URLBuilder(url).apply {
        parameters.append("X-Client-Name", "kotlin-stellar-sdk")
        parameters.append("X-Client-Version", getSdkVersion())
    }.build()
}

/**
 * Gets the SDK version from package metadata.
 */
private fun getSdkVersion(): String {
    return try {
        SSEStream::class.java.`package`?.implementationVersion ?: "dev"
    } catch (e: Exception) {
        "dev"
    }
}

/**
 * JVM implementation of currentTimeMillis.
 */
internal actual fun currentTimeMillis(): Long {
    return System.currentTimeMillis()
}

/**
 * JVM implementation of JSON deserialization.
 */
internal actual fun <T> deserializeJson(json: String, serializer: KSerializer<T>): T {
    val jsonConfig = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        encodeDefaults = true
    }
    return jsonConfig.decodeFromString(serializer, json)
}

/**
 * JVM implementation of network error detection.
 */
internal actual fun isNetworkError(error: Throwable): Boolean {
    return error is IOException || error is SocketException
}

/**
 * Exception for HTTP response errors.
 */
private class HttpResponseException(
    val response: HttpResponse,
    val body: String
) : Exception("HTTP ${response.status.value}: $body")
