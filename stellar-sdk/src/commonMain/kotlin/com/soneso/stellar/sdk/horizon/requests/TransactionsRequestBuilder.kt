package com.soneso.stellar.sdk.horizon.requests

import io.ktor.client.*
import io.ktor.http.*
import com.soneso.stellar.sdk.horizon.exceptions.*
import com.soneso.stellar.sdk.horizon.responses.Page
import com.soneso.stellar.sdk.horizon.responses.TransactionResponse

/**
 * Builds requests connected to transactions.
 *
 * @see <a href="https://developers.stellar.org/api/resources/transactions/">Transactions documentation</a>
 */
class TransactionsRequestBuilder(
    httpClient: HttpClient,
    serverUri: Url
) : RequestBuilder(httpClient, serverUri, "transactions") {

    /**
     * Requests a specific transaction by hash.
     *
     * @param transactionHash The transaction hash to fetch
     * @return The transaction response
     * @throws NetworkException All the exceptions below are subclasses of NetworkException
     * @throws BadRequestException If the request fails due to a bad request (4xx)
     * @throws BadResponseException If the request fails due to a bad response from the server (5xx)
     * @throws TooManyRequestsException If the request fails due to too many requests sent to the server
     * @throws RequestTimeoutException When Horizon returns a Timeout or connection timeout occurred
     * @throws UnknownResponseException If the server returns an unknown status code
     * @throws ConnectionErrorException When the request cannot be executed due to cancellation or connectivity problems
     */
    suspend fun transaction(transactionHash: String): TransactionResponse {
        setSegments("transactions", transactionHash)
        return executeGetRequest(buildUrl())
    }

    /**
     * Builds request to GET /accounts/{account}/transactions
     * Returns all transactions for a specific account.
     *
     * @param account Account for which to get transactions
     * @return This request builder instance
     * @see <a href="https://developers.stellar.org/api/resources/accounts/transactions/">Transactions for Account</a>
     */
    fun forAccount(account: String): TransactionsRequestBuilder {
        setSegments("accounts", account, "transactions")
        return this
    }

    /**
     * Builds request to GET /claimable_balances/{claimable_balance_id}/transactions
     * Returns all transactions for a specific claimable balance.
     *
     * @param claimableBalance Claimable Balance ID for which to get transactions
     * @return This request builder instance
     * @see <a href="https://developers.stellar.org/api/resources/claimablebalances/transactions/">Transactions for ClaimableBalance</a>
     */
    fun forClaimableBalance(claimableBalance: String): TransactionsRequestBuilder {
        setSegments("claimable_balances", claimableBalance, "transactions")
        return this
    }

    /**
     * Builds request to GET /ledgers/{ledgerSeq}/transactions
     * Returns all transactions in a specific ledger.
     *
     * @param ledgerSeq Ledger sequence number for which to get transactions
     * @return This request builder instance
     * @see <a href="https://developers.stellar.org/api/resources/ledgers/transactions/">Transactions for Ledger</a>
     */
    fun forLedger(ledgerSeq: Long): TransactionsRequestBuilder {
        setSegments("ledgers", ledgerSeq.toString(), "transactions")
        return this
    }

    /**
     * Builds request to GET /liquidity_pools/{liquidity_pool_id}/transactions
     * Returns all transactions for a specific liquidity pool.
     *
     * @param liquidityPoolId Liquidity pool ID for which to get transactions
     * @return This request builder instance
     * @see <a href="https://developers.stellar.org/api/resources/liquiditypools/transactions/">Transactions for Liquidity Pool</a>
     */
    fun forLiquidityPool(liquidityPoolId: String): TransactionsRequestBuilder {
        setSegments("liquidity_pools", liquidityPoolId, "transactions")
        return this
    }

    /**
     * Adds a parameter defining whether to include failed transactions.
     * By default only successful transactions are returned.
     *
     * @param value Set to true to include failed transactions
     * @return This request builder instance
     */
    fun includeFailed(value: Boolean): TransactionsRequestBuilder {
        uriBuilder.parameters["include_failed"] = value.toString()
        return this
    }

    /**
     * Build and execute request.
     *
     * @return Page of transaction responses
     * @throws NetworkException All the exceptions below are subclasses of NetworkException
     * @throws BadRequestException If the request fails due to a bad request (4xx)
     * @throws BadResponseException If the request fails due to a bad response from the server (5xx)
     * @throws TooManyRequestsException If the request fails due to too many requests sent to the server
     * @throws RequestTimeoutException When Horizon returns a Timeout or connection timeout occurred
     * @throws UnknownResponseException If the server returns an unknown status code
     * @throws ConnectionErrorException When the request cannot be executed due to cancellation or connectivity problems
     */
    suspend fun execute(): Page<TransactionResponse> {
        return executeGetRequest(buildUrl())
    }

    override fun cursor(cursor: String): TransactionsRequestBuilder {
        super.cursor(cursor)
        return this
    }

    override fun limit(number: Int): TransactionsRequestBuilder {
        super.limit(number)
        return this
    }

    override fun order(direction: Order): TransactionsRequestBuilder {
        super.order(direction)
        return this
    }
}
