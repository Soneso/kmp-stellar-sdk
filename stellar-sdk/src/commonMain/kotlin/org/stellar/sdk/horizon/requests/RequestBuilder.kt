package org.stellar.sdk.horizon.requests

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import org.stellar.sdk.horizon.exceptions.*

/**
 * Abstract base class for all Horizon API request builders.
 * Provides common functionality for building and executing HTTP requests.
 */
abstract class RequestBuilder(
    protected val httpClient: HttpClient,
    protected val serverUri: Url,
    defaultSegment: String? = null
) {
    protected val uriBuilder: URLBuilder = URLBuilder(serverUri)
    protected val segments: MutableList<String> = mutableListOf()
    protected var segmentsAdded: Boolean = false

    init {
        if (defaultSegment != null) {
            setSegments(defaultSegment)
        }
    }

    /**
     * Sets URL path segments for this request.
     * Can only be called once per request builder instance.
     *
     * @param segments The path segments to add to the URL
     * @throws IllegalArgumentException if segments have already been added
     */
    protected fun setSegments(vararg segments: String): RequestBuilder {
        require(!segmentsAdded) { "URL segments have been already added." }
        segmentsAdded = true
        this.segments.clear()
        this.segments.addAll(segments)
        return this
    }

    /**
     * Sets the cursor parameter on the request.
     * A cursor is a value that points to a specific location in a collection of resources.
     * The cursor attribute itself is an opaque value meaning that users should not try to parse it.
     *
     * @param cursor A cursor is a value that points to a specific location in a collection of resources
     * @see <a href="https://developers.stellar.org/api/introduction/pagination/">Page documentation</a>
     */
    open fun cursor(cursor: String): RequestBuilder {
        uriBuilder.parameters["cursor"] = cursor
        return this
    }

    /**
     * Sets the limit parameter on the request.
     * It defines maximum number of records to return.
     * For range and default values check documentation of the endpoint requested.
     *
     * @param number Maximum number of records to return
     */
    open fun limit(number: Int): RequestBuilder {
        uriBuilder.parameters["limit"] = number.toString()
        return this
    }

    /**
     * Sets the order parameter on the request.
     *
     * @param direction The order direction (ASC or DESC)
     */
    open fun order(direction: Order): RequestBuilder {
        uriBuilder.parameters["order"] = direction.value
        return this
    }

    /**
     * Builds the final URL for the request.
     *
     * @return The constructed URL
     */
    internal fun buildUrl(): Url {
        if (segments.isNotEmpty()) {
            // Append segments to the path
            val currentPath = uriBuilder.encodedPath.trimEnd('/')
            val segmentsPath = segments.joinToString("/")
            uriBuilder.encodedPath = "$currentPath/$segmentsPath"
        }
        return uriBuilder.build()
    }

    /**
     * Executes a GET request and handles the response.
     *
     * @param T The type of the response object
     * @param url The URL to send the GET request to
     * @return The response object of type T
     * @throws NetworkException All the exceptions below are subclasses of NetworkException
     * @throws BadRequestException If the request fails due to a bad request (4xx)
     * @throws BadResponseException If the request fails due to a bad response from the server (5xx)
     * @throws TooManyRequestsException If the request fails due to too many requests sent to the server
     * @throws RequestTimeoutException When Horizon returns a Timeout or connection timeout occurred
     * @throws UnknownResponseException If the server returns an unknown status code
     * @throws ConnectionErrorException When the request cannot be executed due to cancellation or connectivity problems
     */
    protected suspend inline fun <reified T> executeGetRequest(url: Url): T {
        return try {
            val response = httpClient.get(url)

            when (response.status.value) {
                in 200..299 -> response.body<T>()
                in 400..499 -> {
                    val body = try {
                        response.body<String>()
                    } catch (e: Exception) {
                        ""
                    }
                    when (response.status.value) {
                        429 -> throw TooManyRequestsException(response.status.value, body)
                        else -> throw BadRequestException(response.status.value, body)
                    }
                }
                in 500..599 -> {
                    val body = try {
                        response.body<String>()
                    } catch (e: Exception) {
                        ""
                    }
                    throw BadResponseException(response.status.value, body)
                }
                else -> {
                    val body = try {
                        response.body<String>()
                    } catch (e: Exception) {
                        ""
                    }
                    throw UnknownResponseException(response.status.value, body)
                }
            }
        } catch (e: NetworkException) {
            throw e
        } catch (e: Exception) {
            throw ConnectionErrorException(e)
        }
    }

    /**
     * Represents possible order parameter values.
     */
    enum class Order(val value: String) {
        /** Ascending order */
        ASC("asc"),
        /** Descending order */
        DESC("desc")
    }
}
