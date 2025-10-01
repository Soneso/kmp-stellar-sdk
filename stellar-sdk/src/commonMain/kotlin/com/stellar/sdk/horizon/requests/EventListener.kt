package com.stellar.sdk.horizon.requests

/**
 * Listener interface for Server-Sent Events (SSE) streams.
 * Implement this interface to handle events and failures from streaming endpoints.
 *
 * @param T The type of event objects that will be received
 */
interface EventListener<T> {
    /**
     * Called when a new event is received from the stream.
     *
     * @param event The deserialized event object
     */
    fun onEvent(event: T)

    /**
     * Called when the stream encounters an error.
     * This method is invoked for network errors, deserialization failures,
     * or HTTP errors from the server.
     *
     * @param error The exception that caused the failure, if available
     * @param responseCode The HTTP status code from the response, if available
     */
    fun onFailure(error: Throwable?, responseCode: Int?)
}
