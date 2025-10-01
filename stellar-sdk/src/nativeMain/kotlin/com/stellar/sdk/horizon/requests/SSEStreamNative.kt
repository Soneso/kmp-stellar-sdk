package com.stellar.sdk.horizon.requests

import com.stellar.sdk.horizon.responses.Response
import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.utils.io.*
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import platform.posix.gettimeofday
import platform.posix.timeval
import kotlin.coroutines.coroutineContext

/**
 * Native (iOS/macOS) implementation of SSE request handling.
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
                onFailure(HttpResponseException(response.status, body), statusCode)
                return@execute
            }

            val channel = response.bodyAsChannel()
            parseSSEStream(channel, onEvent, onFailure, statusCode)
        }
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
 * Gets the SDK version.
 */
private fun getSdkVersion(): String {
    return "dev" // In production, this could be injected during build
}

/**
 * Native implementation of currentTimeMillis using gettimeofday.
 */
@OptIn(ExperimentalForeignApi::class)
internal actual fun currentTimeMillis(): Long {
    memScoped {
        val timeVal = alloc<timeval>()
        gettimeofday(timeVal.ptr, null)
        return (timeVal.tv_sec * 1000L) + (timeVal.tv_usec / 1000L)
    }
}

/**
 * Native implementation of JSON deserialization.
 */
internal actual fun <T> deserializeJson(json: String, serializer: KSerializer<T>): T {
    return Json.decodeFromString(serializer, json)
}

/**
 * Native implementation of network error detection.
 */
internal actual fun isNetworkError(error: Throwable): Boolean {
    // Check if it's a network-related error
    val message = error.message?.lowercase() ?: ""
    return message.contains("connection") ||
            message.contains("network") ||
            message.contains("socket") ||
            message.contains("timeout") ||
            error is HttpResponseException
}

/**
 * Exception for HTTP response errors.
 */
private class HttpResponseException(
    val status: HttpStatusCode,
    val body: String
) : Exception("HTTP ${status.value}: $body")
