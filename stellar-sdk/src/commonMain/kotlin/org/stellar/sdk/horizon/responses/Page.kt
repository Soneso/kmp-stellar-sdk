package org.stellar.sdk.horizon.responses

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.stellar.sdk.horizon.exceptions.*

/**
 * Represents a page of objects in a paginated response.
 *
 * @param T The type of records in the page
 * @property records The list of records in this page
 * @property links Navigation links for pagination
 *
 * @see <a href="https://developers.stellar.org/api/introduction/pagination/">Page documentation</a>
 */
@Serializable
data class Page<T>(
    @SerialName("records")
    val records: List<T>,

    @SerialName("_links")
    val links: Links
) : Response() {

    /**
     * Links for navigating between pages.
     *
     * @property next Link to the next page of results
     * @property prev Link to the previous page of results
     * @property self Link to the current page
     */
    @Serializable
    data class Links(
        @SerialName("next")
        val next: Link? = null,

        @SerialName("prev")
        val prev: Link? = null,

        @SerialName("self")
        val self: Link
    )

    /**
     * Fetches the next page of results.
     *
     * @param httpClient The HTTP client to use for the request
     * @return The next page of results, or null if there is no next page
     * @throws NetworkException If the request fails
     * @throws BadRequestException If the request fails due to a bad request (4xx)
     * @throws BadResponseException If the request fails due to a bad response from the server (5xx)
     * @throws TooManyRequestsException If the request fails due to too many requests sent to the server
     * @throws RequestTimeoutException When Horizon returns a Timeout or connection timeout occurred
     * @throws UnknownResponseException If the server returns an unknown status code
     * @throws ConnectionErrorException When the request cannot be executed due to cancellation or connectivity problems
     */
    suspend inline fun <reified T> getNextPage(httpClient: HttpClient): Page<T>? {
        val nextLink = links.next ?: return null

        return try {
            val response = httpClient.get(nextLink.href)

            when (response.status.value) {
                in 200..299 -> response.body<Page<T>>()
                in 400..499 -> {
                    val body = response.body<String>()
                    when (response.status.value) {
                        429 -> throw TooManyRequestsException(response.status.value, body)
                        else -> throw BadRequestException(response.status.value, body)
                    }
                }
                in 500..599 -> {
                    val body = response.body<String>()
                    throw BadResponseException(response.status.value, body)
                }
                else -> {
                    val body = response.body<String>()
                    throw UnknownResponseException(response.status.value, body)
                }
            }
        } catch (e: NetworkException) {
            throw e
        } catch (e: Exception) {
            throw ConnectionErrorException(e)
        }
    }
}
