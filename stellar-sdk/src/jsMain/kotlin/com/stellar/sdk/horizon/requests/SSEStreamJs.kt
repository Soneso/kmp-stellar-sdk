package com.stellar.sdk.horizon.requests

import com.stellar.sdk.horizon.responses.Response
import io.ktor.client.*
import io.ktor.http.*
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import org.w3c.dom.events.Event
import kotlin.js.Date

/**
 * JS implementation of SSE request handling.
 * Uses the browser's EventSource API for SSE support.
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
    suspendCancellableCoroutine<Unit> { continuation ->
        val fullUrl = addClientIdentification(url)

        // Create EventSource with optional last event ID
        val eventSource = EventSource(fullUrl)

        // Handle incoming messages
        eventSource.onmessage = { event ->
            try {
                val messageEvent = event.unsafeCast<MessageEventData>()
                val data = messageEvent.data as? String

                if (data != null) {
                    // EventSource automatically handles the "id" field
                    // The last event ID is available via lastEventId property
                    val eventId = messageEvent.lastEventId?.takeIf { it.isNotEmpty() }
                    onEvent(eventId, data)
                }
            } catch (e: Exception) {
                onFailure(e, null)
            }
        }

        // Handle errors
        eventSource.onerror = { _ ->
            val error = SSEException("EventSource error")
            onFailure(error, null)
            // Don't close here - let the reconnection logic handle it
        }

        // Handle open
        eventSource.onopen = { _ ->
            // Connection opened successfully
        }

        // Cleanup when coroutine is cancelled
        continuation.invokeOnCancellation {
            eventSource.close()
            onClose()
        }

        // The coroutine will remain suspended until cancelled
        // EventSource handles reconnection automatically
    }
}

/**
 * Adds client identification query parameters to the URL.
 */
private fun addClientIdentification(url: Url): String {
    return URLBuilder(url).apply {
        parameters.append("X-Client-Name", "kotlin-stellar-sdk")
        parameters.append("X-Client-Version", getSdkVersion())
    }.buildString()
}

/**
 * Gets the SDK version.
 */
private fun getSdkVersion(): String {
    return "dev" // In production, this could be injected during build
}

/**
 * JS implementation of currentTimeMillis.
 */
internal actual fun currentTimeMillis(): Long {
    return Date.now().toLong()
}

/**
 * JS implementation of JSON deserialization.
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
 * JS implementation of network error detection.
 */
internal actual fun isNetworkError(error: Throwable): Boolean {
    // In JS, most network errors are generic exceptions
    return error is SSEException || error.message?.contains("network", ignoreCase = true) == true
}

/**
 * Exception for SSE errors.
 */
private class SSEException(message: String) : Exception(message)

/**
 * External declaration for browser EventSource API.
 */
external class EventSource(url: String, eventSourceInitDict: EventSourceInit = definedExternally) {
    val url: String
    val readyState: Short
    val withCredentials: Boolean

    var onopen: ((Event) -> Unit)?
    var onmessage: ((Event) -> Unit)?
    var onerror: ((Event) -> Unit)?

    fun close()

    companion object {
        val CONNECTING: Short
        val OPEN: Short
        val CLOSED: Short
    }
}

external interface EventSourceInit {
    var withCredentials: Boolean?
}

/**
 * External declaration for MessageEvent data structure.
 */
external interface MessageEventData {
    val data: Any?
    val origin: String?
    val lastEventId: String?
    val source: Any?
    val ports: Array<Any>?
}
