package com.soneso.stellar.sdk.horizon.requests

import com.soneso.stellar.sdk.currentTimeMillis
import com.soneso.stellar.sdk.horizon.responses.Pageable
import com.soneso.stellar.sdk.horizon.responses.Response
import com.soneso.stellar.sdk.isFatal
import io.ktor.client.*
import io.ktor.http.*
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readUTF8Line
import kotlinx.coroutines.*
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlin.concurrent.Volatile
import kotlin.coroutines.coroutineContext
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Manages a Server-Sent Events (SSE) stream from a Horizon API endpoint.
 * Provides automatic reconnection, cursor management, and event delivery.
 *
 * This class handles:
 * - Auto-reconnect with configurable timeout
 * - Cursor tracking to resume from the last received event
 * - Event filtering (ignoring "hello" and "byebye" messages)
 * - Thread-safe lifecycle management
 * - Proper resource cleanup
 *
 * @param T The type of response objects expected from the stream
 * @property reconnectTimeout Duration to wait before attempting reconnection (default: 15 seconds)
 */
class SSEStream<T : Response> internal constructor(
    private val httpClient: HttpClient,
    private val requestBuilder: RequestBuilder,
    private val serializer: KSerializer<T>,
    private val listener: EventListener<T>,
    val reconnectTimeout: Duration = DEFAULT_RECONNECT_TIMEOUT
) {
    @Volatile
    private var isStopped = false

    @Volatile
    private var isClosed = true

    @Volatile
    private var latestEventTime = 0L

    @Volatile
    private var lastEventIdValue: String? = null

    @Volatile
    private var currentListenerId = 0L

    private var streamJob: Job? = null
    private var monitorJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    /**
     * Returns the paging token (cursor) of the last received event.
     * This can be used to resume streaming from a specific point.
     *
     * @return The last event ID (paging token), or null if no events have been received
     */
    val lastPagingToken: String?
        get() = lastEventIdValue

    init {
        start()
    }

    /**
     * Starts the SSE stream monitoring and connection management.
     * This method is called automatically by the constructor.
     */
    private fun start() {
        if (isStopped) {
            throw IllegalStateException("Stream has already been stopped")
        }

        // Start monitoring job that checks connection health
        monitorJob = scope.launch {
            while (isActive && !isStopped) {
                val currentTime = currentTimeMillis()
                val timeSinceLastEvent = currentTime - latestEventTime

                if (timeSinceLastEvent > reconnectTimeout.inWholeMilliseconds) {
                    latestEventTime = currentTime
                    if (!isClosed) {
                        isClosed = true
                    }
                }

                if (isClosed && !isStopped) {
                    isClosed = false
                    restart()
                }

                delay(MONITOR_INTERVAL)
            }
        }
    }

    /**
     * Restarts the SSE connection.
     * Cancels the current stream and creates a new one with the latest cursor.
     */
    private fun restart() {
        // Cancel existing stream
        streamJob?.cancel()

        // Increment listener ID to ignore events from old connections
        currentListenerId++
        val newListenerId = currentListenerId

        // Start new stream
        streamJob = scope.launch {
            try {
                doStreamRequest(newListenerId)
            } catch (e: CancellationException) {
                // Expected when closing
            } catch (e: Throwable) {
                // Throwable rather than Exception: on Kotlin/JS the HTTP engine reports
                // connectivity failures as kotlin.Error, which would otherwise escape the
                // stream coroutine instead of being reported and triggering a reconnect.
                if (isFatal(e)) throw e
                if (!isStopped && newListenerId == currentListenerId) {
                    listener.onFailure(e, null)
                    // Mark as closed to trigger reconnect
                    if (!isClosed) {
                        isClosed = true
                    }
                }
            }
        }
    }

    /**
     * Platform-specific SSE request implementation.
     * This method is expected to be implemented by platform-specific code.
     *
     * @param listenerId The unique ID for this listener instance
     */
    private suspend fun doStreamRequest(listenerId: Long) {
        val url = requestBuilder.buildUrl()
        sseRequest(
            httpClient = httpClient,
            url = url,
            lastEventId = lastEventIdValue,
            serializer = serializer,
            onEvent = { eventId, data ->
                handleEvent(listenerId, eventId, data)
            },
            onFailure = { error, statusCode ->
                handleFailure(listenerId, error, statusCode)
            },
            onClose = {
                handleClose(listenerId)
            }
        )
    }

    /**
     * Handles an incoming SSE event.
     *
     * @param listenerId The listener ID that received the event
     * @param eventId The event ID from the SSE stream
     * @param data The event data as a string
     */
    private fun handleEvent(listenerId: Long, eventId: String?, data: String) {
        if (isStopped || listenerId != currentListenerId) {
            return
        }

        // Update timestamp
        latestEventTime = currentTimeMillis()

        // Ignore system messages
        if (data == "\"hello\"" || data == "\"byebye\"") {
            return
        }

        try {
            // Deserialize the event
            val event = deserializeJson(data, serializer)

            // Update cursor if this is a pageable response
            if (event is Pageable) {
                requestBuilder.cursor(event.pagingToken)
            }

            // Store last event ID
            if (eventId != null) {
                lastEventIdValue = eventId
            }

            // Deliver event to listener
            listener.onEvent(event)
        } catch (e: Exception) {
            listener.onFailure(e, null)
        }
    }

    /**
     * Handles a stream failure.
     *
     * @param listenerId The listener ID that encountered the failure
     * @param error The error that occurred
     * @param statusCode The HTTP status code, if available
     */
    private fun handleFailure(listenerId: Long, error: Throwable, statusCode: Int?) {
        if (isStopped || listenerId != currentListenerId) {
            return
        }

        // A fatal failure (coroutine cancellation, platform-fatal error) must never be
        // reclassified as a reconnectable network error or delivered as a stream failure;
        // let it propagate.
        if (isFatal(error)) throw error

        // Network errors should trigger reconnect, not failure callback
        if (isNetworkError(error)) {
            if (!isClosed) {
                isClosed = true
            }
        } else {
            listener.onFailure(error, statusCode)
        }
    }

    /**
     * Handles stream closure.
     *
     * @param listenerId The listener ID for the closed stream
     */
    private fun handleClose(listenerId: Long) {
        if (isStopped || listenerId != currentListenerId) {
            return
        }
        if (!isClosed) {
            isClosed = true
        }
    }

    /**
     * Closes the SSE stream and releases all resources.
     * After calling this method, the stream cannot be restarted.
     */
    fun close() {
        if (!isStopped) {
            isStopped = true
            streamJob?.cancel()
            monitorJob?.cancel()
            scope.cancel()
        }
    }

    companion object {
        /**
         * Default timeout before attempting to reconnect (15 seconds).
         */
        val DEFAULT_RECONNECT_TIMEOUT: Duration = 15.seconds

        /**
         * Interval for checking connection health (200 milliseconds).
         */
        private val MONITOR_INTERVAL: Duration = 200.milliseconds

        /**
         * Creates and starts a new SSE stream.
         *
         * @param T The type of response objects expected from the stream
         * @param httpClient The HTTP client to use for the connection
         * @param requestBuilder The request builder containing URL and parameters
         * @param serializer The serializer for deserializing event data
         * @param listener The event listener for callbacks
         * @param reconnectTimeout Optional custom reconnect timeout
         * @return A new SSEStream instance
         */
        fun <T : Response> create(
            httpClient: HttpClient,
            requestBuilder: RequestBuilder,
            serializer: KSerializer<T>,
            listener: EventListener<T>,
            reconnectTimeout: Duration = DEFAULT_RECONNECT_TIMEOUT
        ): SSEStream<T> {
            return SSEStream(httpClient, requestBuilder, serializer, listener, reconnectTimeout)
        }
    }
}

/**
 * Platform-specific SSE request implementation.
 * Expected to be implemented by each platform.
 */
internal expect suspend fun <T : Response> sseRequest(
    httpClient: HttpClient,
    url: Url,
    lastEventId: String?,
    serializer: KSerializer<T>,
    onEvent: (eventId: String?, data: String) -> Unit,
    onFailure: (error: Throwable, statusCode: Int?) -> Unit,
    onClose: () -> Unit
)

/**
 * Reads Server-Sent Events from [channel] line by line and passes the id and data of each
 * complete event to [onEvent]; a read error goes to [onFailure] with [statusCode]. The JVM and
 * native [sseRequest] call it, the JS one receives its events from the browser `EventSource`.
 */
internal suspend fun parseSSEStream(
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
 * Decodes the data of an SSE event with [serializer]. Unknown keys are ignored, the input is
 * read leniently, and a null for a non-nullable property or an unknown enum value takes the
 * property's default where one is declared.
 */
internal fun <T> deserializeJson(json: String, serializer: KSerializer<T>): T {
    val jsonConfig = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        encodeDefaults = true
    }
    return jsonConfig.decodeFromString(serializer, json)
}

/**
 * Platform-specific network error detection.
 */
internal expect fun isNetworkError(error: Throwable): Boolean

/**
 * Reports an SSE response whose status is outside 200 to 299, with the response [body].
 */
internal class HttpResponseException(
    val status: HttpStatusCode,
    val body: String
) : Exception("HTTP ${status.value}: $body")
