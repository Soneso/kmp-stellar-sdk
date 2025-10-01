package com.stellar.sdk.horizon.requests

import com.stellar.sdk.horizon.responses.Page
import com.stellar.sdk.horizon.responses.effects.EffectResponse
import io.ktor.client.*
import io.ktor.http.*

/**
 * Builds requests connected to effects.
 *
 * Effects represent specific changes that occur in the ledger as a result of successful operations.
 *
 * @see [Effects Documentation](https://developers.stellar.org/api/resources/effects/)
 */
class EffectsRequestBuilder(
    httpClient: HttpClient,
    serverUri: Url
) : RequestBuilder(httpClient, serverUri, "effects") {

    /**
     * Builds request to `GET /accounts/{account}/effects`
     *
     * @param account Account for which to get effects
     * @return Current [EffectsRequestBuilder] instance
     * @see [Effects for Account](https://developers.stellar.org/api/resources/accounts/effects/)
     */
    fun forAccount(account: String): EffectsRequestBuilder {
        setSegments("accounts", account, "effects")
        return this
    }

    /**
     * Builds request to `GET /ledgers/{ledgerSeq}/effects`
     *
     * @param ledgerSeq Ledger for which to get effects
     * @return Current [EffectsRequestBuilder] instance
     * @see [Effects for Ledger](https://developers.stellar.org/api/resources/ledgers/effects/)
     */
    fun forLedger(ledgerSeq: Long): EffectsRequestBuilder {
        setSegments("ledgers", ledgerSeq.toString(), "effects")
        return this
    }

    /**
     * Builds request to `GET /transactions/{transactionId}/effects`
     *
     * @param transactionId Transaction ID for which to get effects
     * @return Current [EffectsRequestBuilder] instance
     * @see [Effects for Transaction](https://developers.stellar.org/api/resources/transactions/effects/)
     */
    fun forTransaction(transactionId: String): EffectsRequestBuilder {
        setSegments("transactions", transactionId, "effects")
        return this
    }

    /**
     * Builds request to `GET /operations/{operationId}/effects`
     *
     * @param operationId Operation ID for which to get effects
     * @return Current [EffectsRequestBuilder] instance
     * @see [Effects for Operation](https://developers.stellar.org/api/resources/operations/effects/)
     */
    fun forOperation(operationId: Long): EffectsRequestBuilder {
        setSegments("operations", operationId.toString(), "effects")
        return this
    }

    /**
     * Builds request to `GET /liquidity_pools/{poolId}/effects`
     *
     * @param liquidityPoolId Liquidity pool for which to get effects
     * @return Current [EffectsRequestBuilder] instance
     * @see [Effects for Liquidity Pool](https://developers.stellar.org/api/resources/liquiditypools/effects/)
     */
    fun forLiquidityPool(liquidityPoolId: String): EffectsRequestBuilder {
        setSegments("liquidity_pools", liquidityPoolId, "effects")
        return this
    }

    override fun cursor(cursor: String): EffectsRequestBuilder {
        super.cursor(cursor)
        return this
    }

    override fun limit(number: Int): EffectsRequestBuilder {
        super.limit(number)
        return this
    }

    override fun order(direction: Order): EffectsRequestBuilder {
        super.order(direction)
        return this
    }

    /**
     * Build and execute request.
     *
     * @return [Page] of [EffectResponse]
     * @throws com.stellar.sdk.horizon.exceptions.NetworkException All the exceptions below are subclasses of NetworkException
     * @throws com.stellar.sdk.horizon.exceptions.BadRequestException if the request fails due to a bad request (4xx)
     * @throws com.stellar.sdk.horizon.exceptions.BadResponseException if the request fails due to a bad response from the server (5xx)
     * @throws com.stellar.sdk.horizon.exceptions.TooManyRequestsException if the request fails due to too many requests sent to the server
     * @throws com.stellar.sdk.horizon.exceptions.RequestTimeoutException When Horizon returns a Timeout or connection timeout occurred
     * @throws com.stellar.sdk.horizon.exceptions.UnknownResponseException if the server returns an unknown status code
     * @throws com.stellar.sdk.horizon.exceptions.ConnectionErrorException When the request cannot be executed due to cancellation or connectivity problems
     */
    suspend fun execute(): Page<EffectResponse> {
        return executeGetRequest(buildUrl())
    }
}
