package com.soneso.stellar.sdk.horizon.requests

import io.ktor.client.HttpClient
import io.ktor.http.Url
import com.soneso.stellar.sdk.horizon.responses.RootResponse

/**
 * Builds requests for the root endpoint.
 *
 * The root endpoint provides information about the Horizon server and the Stellar network
 * it's connected to, including version information, protocol versions, and network passphrase.
 *
 * Example usage:
 * ```
 * val server = HorizonServer("https://horizon.stellar.org")
 * val rootInfo = server.root().execute()
 * println("Network: ${rootInfo.networkPassphrase}")
 * println("Horizon version: ${rootInfo.horizonVersion}")
 * ```
 *
 * @constructor Creates a RootRequestBuilder
 * @param httpClient The HTTP client to use for requests
 * @param serverUri The base URI of the Horizon server
 */
class RootRequestBuilder(
    httpClient: HttpClient,
    serverUri: Url
) : RequestBuilder(httpClient, serverUri, "") {

    /**
     * Executes the request to get root endpoint information.
     *
     * @return [RootResponse] containing server and network information
     * @throws com.soneso.stellar.sdk.horizon.exceptions.NetworkException All the exceptions below are subclasses of NetworkException
     * @throws com.soneso.stellar.sdk.horizon.exceptions.BadRequestException if the request fails due to a bad request (4xx)
     * @throws com.soneso.stellar.sdk.horizon.exceptions.BadResponseException if the request fails due to a bad response from the server (5xx)
     * @throws com.soneso.stellar.sdk.horizon.exceptions.TooManyRequestsException if the request fails due to too many requests sent to the server
     * @throws com.soneso.stellar.sdk.horizon.exceptions.RequestTimeoutException when Horizon returns a Timeout or connection timeout occurred
     * @throws com.soneso.stellar.sdk.horizon.exceptions.UnknownResponseException if the server returns an unknown status code
     * @throws com.soneso.stellar.sdk.horizon.exceptions.ConnectionErrorException when the request cannot be executed due to cancellation or connectivity problems
     */
    suspend fun execute(): RootResponse {
        return executeGetRequest(buildUrl())
    }
}
