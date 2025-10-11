package com.soneso.stellar.sdk.horizon.requests

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import com.soneso.stellar.sdk.horizon.exceptions.*
import com.soneso.stellar.sdk.horizon.responses.HealthResponse
import kotlinx.serialization.json.Json

/**
 * Builds requests for the health endpoint.
 *
 * The health endpoint provides information about the current operational status of the Horizon server.
 * It returns three key indicators that determine whether the server is functioning properly:
 * - Database connectivity
 * - Stellar Core availability
 * - Stellar Core synchronization status
 *
 * The server is considered healthy when all three indicators are true.
 *
 * This endpoint has no parameters - it simply returns the current health status.
 *
 * **Note**: The health endpoint returns `Content-Type: text/plain` instead of `application/json`,
 * so this builder handles the response parsing differently from other endpoints.
 *
 * Example usage:
 * ```kotlin
 * val server = HorizonServer("https://horizon.stellar.org")
 *
 * // Get current health status
 * val health = server.health().execute()
 *
 * if (health.isHealthy) {
 *     println("Server is healthy")
 *     println("Database connected: ${health.databaseConnected}")
 *     println("Core up: ${health.coreUp}")
 *     println("Core synced: ${health.coreSynced}")
 * } else {
 *     println("Server is experiencing issues")
 *     if (!health.databaseConnected) println("Database is not connected")
 *     if (!health.coreUp) println("Stellar Core is not up")
 *     if (!health.coreSynced) println("Stellar Core is not synced")
 * }
 * ```
 *
 * @see <a href="https://developers.stellar.org/docs/data/horizon/api-reference/structure/health">Health endpoint documentation</a>
 */
class HealthRequestBuilder(
    httpClient: HttpClient,
    serverUri: Url
) : RequestBuilder(httpClient, serverUri, "health") {

    /**
     * JSON parser configured to handle health response.
     */
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    /**
     * Build and execute request to get health status.
     *
     * **Note**: The health endpoint returns `Content-Type: text/plain` instead of `application/json`,
     * so this method manually parses the JSON response from the text body.
     *
     * @return HealthResponse containing current health status
     * @throws NetworkException All the exceptions below are subclasses of NetworkException
     * @throws BadRequestException If the request fails due to a bad request (4xx)
     * @throws BadResponseException If the request fails due to a bad response from the server (5xx)
     * @throws TooManyRequestsException If the request fails due to too many requests sent to the server
     * @throws RequestTimeoutException When Horizon returns a Timeout or connection timeout occurred
     * @throws UnknownResponseException If the server returns an unknown status code
     * @throws ConnectionErrorException When the request cannot be executed due to cancellation or connectivity problems
     *
     * @see <a href="https://developers.stellar.org/docs/data/horizon/api-reference/structure/health">Health endpoint documentation</a>
     */
    suspend fun execute(): HealthResponse {
        return try {
            val response = httpClient.get(buildUrl())

            when (response.status.value) {
                in 200..299 -> {
                    // Health endpoint returns text/plain, so we need to read as text and parse manually
                    val bodyText = response.body<String>()
                    json.decodeFromString<HealthResponse>(bodyText)
                }
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
     * Health endpoint doesn't support cursor pagination.
     * This method throws UnsupportedOperationException.
     */
    override fun cursor(cursor: String): HealthRequestBuilder {
        throw UnsupportedOperationException("cursor() is not supported on health endpoint")
    }

    /**
     * Health endpoint doesn't support limit parameter.
     * This method throws UnsupportedOperationException.
     */
    override fun limit(number: Int): HealthRequestBuilder {
        throw UnsupportedOperationException("limit() is not supported on health endpoint")
    }

    /**
     * Health endpoint doesn't support order parameter.
     * This method throws UnsupportedOperationException.
     */
    override fun order(direction: Order): HealthRequestBuilder {
        throw UnsupportedOperationException("order() is not supported on health endpoint")
    }
}
