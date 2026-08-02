package com.soneso.stellar.sdk.horizon.requests

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import com.soneso.stellar.sdk.horizon.exceptions.*
import com.soneso.stellar.sdk.horizon.responses.Response
import com.soneso.stellar.sdk.isFatal
import com.soneso.stellar.sdk.readErrorBodyOrFallback
import kotlinx.serialization.KSerializer
import kotlin.time.Duration

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
            segmentsAdded = false // Allow overwriting default segment
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
     * The path segments are applied to a copy of the builder state, leaving [uriBuilder]
     * untouched, so repeated calls on the same request builder yield the same URL. SSE
     * streams rely on this: [SSEStream] rebuilds the URL on every reconnect.
     *
     * @return The constructed URL
     */
    internal fun buildUrl(): Url {
        val base = uriBuilder.build()
        if (segments.isEmpty()) {
            return base
        }
        val segmentsPath = segments.joinToString("/")
        return URLBuilder(base).apply {
            encodedPath = "${base.encodedPath.trimEnd('/')}/$segmentsPath"
        }.build()
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
                    val body = readErrorBodyOrFallback("") { response.body<String>() }
                    when (response.status.value) {
                        429 -> throw TooManyRequestsException(response.status.value, body)
                        else -> throw BadRequestException(response.status.value, body)
                    }
                }
                in 500..599 -> {
                    val body = readErrorBodyOrFallback("") { response.body<String>() }
                    throw BadResponseException(response.status.value, body)
                }
                else -> {
                    val body = readErrorBodyOrFallback("") { response.body<String>() }
                    throw UnknownResponseException(response.status.value, body)
                }
            }
        } catch (e: NetworkException) {
            throw e
        } catch (e: Throwable) {
            // Throwable rather than Exception: on Kotlin/JS the HTTP engine reports
            // connectivity failures as kotlin.Error, which must surface as the
            // documented ConnectionErrorException instead of escaping unwrapped.
            if (isFatal(e)) throw e
            throw ConnectionErrorException(e)
        }
    }

    /**
     * Creates a Server-Sent Events (SSE) stream for this request.
     * The stream will automatically reconnect on connection loss and resume from the last received event.
     *
     * @param T The type of response objects expected from the stream
     * @param serializer The serializer for deserializing event data
     * @param listener The event listener for handling incoming events and failures
     * @param reconnectTimeout Optional custom reconnection timeout (default: 15 seconds)
     * @return An SSEStream instance that can be closed to stop streaming
     *
     * @see SSEStream
     * @see EventListener
     */
    fun <T : Response> stream(
        serializer: KSerializer<T>,
        listener: EventListener<T>,
        reconnectTimeout: Duration = SSEStream.DEFAULT_RECONNECT_TIMEOUT
    ): SSEStream<T> {
        return SSEStream.create(
            httpClient = httpClient,
            requestBuilder = this,
            serializer = serializer,
            listener = listener,
            reconnectTimeout = reconnectTimeout
        )
    }

    /**
     * Sets an asset parameter on the request.
     * The asset is encoded as "assetCode:issuerAccountId" for credit assets or "native" for XLM.
     *
     * @param parameterName The name of the query parameter
     * @param assetType The asset type (native, credit_alphanum4, credit_alphanum12)
     * @param assetCode The asset code (null for native)
     * @param assetIssuer The asset issuer (null for native)
     */
    protected fun setAssetParameter(
        parameterName: String,
        assetType: String,
        assetCode: String? = null,
        assetIssuer: String? = null
    ) {
        val encodedAsset = when (assetType) {
            "native" -> "native"
            "credit_alphanum4", "credit_alphanum12" -> {
                require(assetCode != null && assetIssuer != null) {
                    "Asset code and issuer must be provided for credit assets"
                }
                "$assetCode:$assetIssuer"
            }
            else -> throw IllegalArgumentException("Unsupported asset type: $assetType")
        }
        uriBuilder.parameters[parameterName] = encodedAsset
    }

    /**
     * Sets asset type, code, and issuer parameters on the request.
     * Used for endpoints that accept asset details as separate query parameters.
     *
     * @param prefix The prefix for the parameter names (e.g., "buying", "selling", "base", "counter")
     * @param assetType The asset type (native, credit_alphanum4, credit_alphanum12)
     * @param assetCode The asset code (null for native)
     * @param assetIssuer The asset issuer (null for native)
     */
    protected fun setAssetTypeParameters(
        prefix: String,
        assetType: String,
        assetCode: String? = null,
        assetIssuer: String? = null
    ) {
        uriBuilder.parameters["${prefix}_asset_type"] = assetType
        if (assetCode != null) {
            uriBuilder.parameters["${prefix}_asset_code"] = assetCode
        }
        if (assetIssuer != null) {
            uriBuilder.parameters["${prefix}_asset_issuer"] = assetIssuer
        }
    }

    /**
     * Encodes an asset to canonical string format for use in query parameters.
     * Native assets become "native", credit assets become "CODE:ISSUER".
     *
     * @param assetType The asset type (native, credit_alphanum4, credit_alphanum12)
     * @param assetCode The asset code (null for native)
     * @param assetIssuer The asset issuer (null for native)
     * @return The encoded asset string
     */
    protected fun encodeAsset(assetType: String, assetCode: String? = null, assetIssuer: String? = null): String {
        return when (assetType) {
            "native" -> "native"
            "credit_alphanum4", "credit_alphanum12" -> {
                require(assetCode != null && assetIssuer != null) {
                    "Asset code and issuer must be provided for credit assets"
                }
                "$assetCode:$assetIssuer"
            }
            else -> throw IllegalArgumentException("Unsupported asset type: $assetType")
        }
    }

    /**
     * Sets a parameter consisting of a comma-separated list of assets.
     * Used for endpoints like pathfinding that accept multiple assets.
     *
     * @param parameterName The name of the query parameter
     * @param assets List of assets, where each asset is a triple of (type, code, issuer)
     */
    protected fun setAssetsParameter(
        parameterName: String,
        assets: List<Triple<String, String?, String?>>
    ) {
        val encodedAssets = assets.map { (type, code, issuer) ->
            encodeAsset(type, code, issuer)
        }
        uriBuilder.parameters[parameterName] = encodedAssets.joinToString(",")
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
