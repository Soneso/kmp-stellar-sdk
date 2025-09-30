package org.stellar.sdk.horizon.requests

import io.ktor.client.*
import io.ktor.http.*
import org.stellar.sdk.horizon.exceptions.*
import org.stellar.sdk.horizon.responses.Page
import org.stellar.sdk.horizon.responses.operations.OperationResponse

/**
 * Builds requests connected to operations.
 *
 * @see <a href="https://developers.stellar.org/api/resources/operations/">Operations documentation</a>
 */
class OperationsRequestBuilder(
    httpClient: HttpClient,
    serverUri: Url
) : RequestBuilder(httpClient, serverUri, "operations") {

    private val toJoin: MutableSet<String> = mutableSetOf()

    /**
     * Requests a specific operation by ID.
     *
     * @param operationId The operation ID to fetch
     * @return The operation response
     * @throws NetworkException All the exceptions below are subclasses of NetworkException
     * @throws BadRequestException If the request fails due to a bad request (4xx)
     * @throws BadResponseException If the request fails due to a bad response from the server (5xx)
     * @throws TooManyRequestsException If the request fails due to too many requests sent to the server
     * @throws RequestTimeoutException When Horizon returns a Timeout or connection timeout occurred
     * @throws UnknownResponseException If the server returns an unknown status code
     * @throws ConnectionErrorException When the request cannot be executed due to cancellation or connectivity problems
     */
    suspend fun operation(operationId: Long): OperationResponse {
        setSegments("operations", operationId.toString())
        return executeGetRequest(buildUrl())
    }

    /**
     * Builds request to GET /accounts/{account}/operations
     * Returns all operations for a specific account.
     *
     * @param account Account for which to get operations
     * @return This request builder instance
     * @see <a href="https://developers.stellar.org/api/resources/accounts/operations/">Operations for Account</a>
     */
    fun forAccount(account: String): OperationsRequestBuilder {
        setSegments("accounts", account, "operations")
        return this
    }

    /**
     * Builds request to GET /claimable_balances/{claimable_balance_id}/operations
     * Returns all operations for a specific claimable balance.
     *
     * @param claimableBalance Claimable Balance ID for which to get operations
     * @return This request builder instance
     * @see <a href="https://developers.stellar.org/api/resources/claimablebalances/operations/">Operations for ClaimableBalance</a>
     */
    fun forClaimableBalance(claimableBalance: String): OperationsRequestBuilder {
        setSegments("claimable_balances", claimableBalance, "operations")
        return this
    }

    /**
     * Builds request to GET /ledgers/{ledgerSeq}/operations
     * Returns all operations in a specific ledger.
     *
     * @param ledgerSeq Ledger sequence number for which to get operations
     * @return This request builder instance
     * @see <a href="https://developers.stellar.org/api/resources/ledgers/operations/">Operations for Ledger</a>
     */
    fun forLedger(ledgerSeq: Long): OperationsRequestBuilder {
        setSegments("ledgers", ledgerSeq.toString(), "operations")
        return this
    }

    /**
     * Builds request to GET /transactions/{transactionId}/operations
     * Returns all operations in a specific transaction.
     *
     * @param transactionId Transaction ID for which to get operations
     * @return This request builder instance
     * @see <a href="https://developers.stellar.org/api/resources/transactions/operations/">Operations for Transaction</a>
     */
    fun forTransaction(transactionId: String): OperationsRequestBuilder {
        setSegments("transactions", transactionId, "operations")
        return this
    }

    /**
     * Builds request to GET /liquidity_pools/{liquidity_pool_id}/operations
     * Returns all operations for a specific liquidity pool.
     *
     * @param liquidityPoolId Liquidity pool ID for which to get operations
     * @return This request builder instance
     * @see <a href="https://developers.stellar.org/api/resources/liquiditypools/operations/">Operations for Liquidity Pool</a>
     */
    fun forLiquidityPool(liquidityPoolId: String): OperationsRequestBuilder {
        setSegments("liquidity_pools", liquidityPoolId, "operations")
        return this
    }

    /**
     * Adds a parameter defining whether to include operations of failed transactions.
     * By default only operations of successful transactions are returned.
     *
     * @param value Set to true to include operations of failed transactions
     * @return This request builder instance
     */
    fun includeFailed(value: Boolean): OperationsRequestBuilder {
        uriBuilder.parameters["include_failed"] = value.toString()
        return this
    }

    /**
     * Adds a parameter defining whether to include transactions in the response.
     * By default transaction data is not included.
     *
     * @param include Set to true to include transaction data in the operations response
     * @return This request builder instance
     */
    fun includeTransactions(include: Boolean): OperationsRequestBuilder {
        updateToJoin("transactions", include)
        return this
    }

    private fun updateToJoin(value: String, include: Boolean) {
        if (include) {
            toJoin.add(value)
        } else {
            toJoin.remove(value)
        }

        if (toJoin.isEmpty()) {
            uriBuilder.parameters.remove("join")
        } else {
            uriBuilder.parameters["join"] = toJoin.joinToString(",")
        }
    }

    /**
     * Build and execute request.
     *
     * @return Page of operation responses
     * @throws NetworkException All the exceptions below are subclasses of NetworkException
     * @throws BadRequestException If the request fails due to a bad request (4xx)
     * @throws BadResponseException If the request fails due to a bad response from the server (5xx)
     * @throws TooManyRequestsException If the request fails due to too many requests sent to the server
     * @throws RequestTimeoutException When Horizon returns a Timeout or connection timeout occurred
     * @throws UnknownResponseException If the server returns an unknown status code
     * @throws ConnectionErrorException When the request cannot be executed due to cancellation or connectivity problems
     */
    suspend fun execute(): Page<OperationResponse> {
        return executeGetRequest(buildUrl())
    }

    override fun cursor(cursor: String): OperationsRequestBuilder {
        super.cursor(cursor)
        return this
    }

    override fun limit(number: Int): OperationsRequestBuilder {
        super.limit(number)
        return this
    }

    override fun order(direction: Order): OperationsRequestBuilder {
        super.order(direction)
        return this
    }
}
