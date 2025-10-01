package com.stellar.sdk.horizon.requests

import io.ktor.client.*
import io.ktor.http.*
import com.stellar.sdk.horizon.exceptions.*
import com.stellar.sdk.horizon.responses.AccountResponse
import com.stellar.sdk.horizon.responses.Page

/**
 * Builds requests connected to accounts.
 *
 * @see <a href="https://developers.stellar.org/api/resources/accounts/">Accounts documentation</a>
 */
class AccountsRequestBuilder(
    httpClient: HttpClient,
    serverUri: Url
) : RequestBuilder(httpClient, serverUri, "accounts") {

    companion object {
        private const val ASSET_PARAMETER_NAME = "asset"
        private const val LIQUIDITY_POOL_PARAMETER_NAME = "liquidity_pool"
        private const val SIGNER_PARAMETER_NAME = "signer"
        private const val SPONSOR_PARAMETER_NAME = "sponsor"
    }

    /**
     * Requests a specific account by account ID.
     *
     * @param accountId The account ID to fetch
     * @return The account response
     * @throws NetworkException All the exceptions below are subclasses of NetworkException
     * @throws BadRequestException If the request fails due to a bad request (4xx)
     * @throws BadResponseException If the request fails due to a bad response from the server (5xx)
     * @throws TooManyRequestsException If the request fails due to too many requests sent to the server
     * @throws RequestTimeoutException When Horizon returns a Timeout or connection timeout occurred
     * @throws UnknownResponseException If the server returns an unknown status code
     * @throws ConnectionErrorException When the request cannot be executed due to cancellation or connectivity problems
     */
    suspend fun account(accountId: String): AccountResponse {
        setSegments("accounts", accountId)
        return executeGetRequest(buildUrl())
    }

    /**
     * Returns all accounts that contain a specific signer.
     *
     * @param signer Account ID of the signer
     * @return This request builder instance
     * @throws IllegalArgumentException If asset, liquidity_pool, or sponsor parameters are already set
     * @see <a href="https://developers.stellar.org/api/resources/accounts/list/">Accounts</a>
     */
    fun forSigner(signer: String): AccountsRequestBuilder {
        require(uriBuilder.parameters[ASSET_PARAMETER_NAME] == null) {
            "cannot set both asset and signer"
        }
        require(uriBuilder.parameters[LIQUIDITY_POOL_PARAMETER_NAME] == null) {
            "cannot set both liquidity_pool and signer"
        }
        require(uriBuilder.parameters[SPONSOR_PARAMETER_NAME] == null) {
            "cannot set both sponsor and signer"
        }
        uriBuilder.parameters[SIGNER_PARAMETER_NAME] = signer
        return this
    }

    /**
     * Returns all accounts who are trustees to a specific asset.
     *
     * @param assetCode The asset code
     * @param assetIssuer The asset issuer account ID
     * @return This request builder instance
     * @throws IllegalArgumentException If liquidity_pool, signer, or sponsor parameters are already set
     * @see <a href="https://developers.stellar.org/api/resources/accounts/list/">Accounts</a>
     */
    fun forAsset(assetCode: String, assetIssuer: String): AccountsRequestBuilder {
        require(uriBuilder.parameters[LIQUIDITY_POOL_PARAMETER_NAME] == null) {
            "cannot set both liquidity_pool and asset"
        }
        require(uriBuilder.parameters[SIGNER_PARAMETER_NAME] == null) {
            "cannot set both signer and asset"
        }
        require(uriBuilder.parameters[SPONSOR_PARAMETER_NAME] == null) {
            "cannot set both sponsor and asset"
        }
        uriBuilder.parameters[ASSET_PARAMETER_NAME] = "$assetCode:$assetIssuer"
        return this
    }

    /**
     * Returns all accounts who have trustlines to the specified liquidity pool.
     *
     * @param liquidityPoolId Liquidity Pool ID
     * @return This request builder instance
     * @throws IllegalArgumentException If asset, signer, or sponsor parameters are already set
     * @see <a href="https://developers.stellar.org/api/resources/accounts/list/">Accounts</a>
     */
    fun forLiquidityPool(liquidityPoolId: String): AccountsRequestBuilder {
        require(uriBuilder.parameters[ASSET_PARAMETER_NAME] == null) {
            "cannot set both asset and liquidity_pool"
        }
        require(uriBuilder.parameters[SIGNER_PARAMETER_NAME] == null) {
            "cannot set both signer and liquidity_pool"
        }
        require(uriBuilder.parameters[SPONSOR_PARAMETER_NAME] == null) {
            "cannot set both sponsor and liquidity_pool"
        }
        uriBuilder.parameters[LIQUIDITY_POOL_PARAMETER_NAME] = liquidityPoolId
        return this
    }

    /**
     * Returns all accounts who are sponsored by a given account or have subentries which are
     * sponsored by a given account.
     *
     * @param sponsor Account ID of the sponsor
     * @return This request builder instance
     * @throws IllegalArgumentException If asset, liquidity_pool, or signer parameters are already set
     * @see <a href="https://developers.stellar.org/api/resources/accounts/list/">Accounts</a>
     */
    fun forSponsor(sponsor: String): AccountsRequestBuilder {
        require(uriBuilder.parameters[ASSET_PARAMETER_NAME] == null) {
            "cannot set both asset and sponsor"
        }
        require(uriBuilder.parameters[LIQUIDITY_POOL_PARAMETER_NAME] == null) {
            "cannot set both liquidity_pool and sponsor"
        }
        require(uriBuilder.parameters[SIGNER_PARAMETER_NAME] == null) {
            "cannot set both signer and sponsor"
        }
        uriBuilder.parameters[SPONSOR_PARAMETER_NAME] = sponsor
        return this
    }

    /**
     * Build and execute request.
     *
     * @return Page of account responses
     * @throws NetworkException All the exceptions below are subclasses of NetworkException
     * @throws BadRequestException If the request fails due to a bad request (4xx)
     * @throws BadResponseException If the request fails due to a bad response from the server (5xx)
     * @throws TooManyRequestsException If the request fails due to too many requests sent to the server
     * @throws RequestTimeoutException When Horizon returns a Timeout or connection timeout occurred
     * @throws UnknownResponseException If the server returns an unknown status code
     * @throws ConnectionErrorException When the request cannot be executed due to cancellation or connectivity problems
     */
    suspend fun execute(): Page<AccountResponse> {
        return executeGetRequest(buildUrl())
    }

    override fun cursor(cursor: String): AccountsRequestBuilder {
        super.cursor(cursor)
        return this
    }

    override fun limit(number: Int): AccountsRequestBuilder {
        super.limit(number)
        return this
    }

    override fun order(direction: Order): AccountsRequestBuilder {
        super.order(direction)
        return this
    }
}
