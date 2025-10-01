package com.stellar.sdk.horizon.requests

import io.ktor.client.*
import io.ktor.http.*
import com.stellar.sdk.horizon.exceptions.*
import com.stellar.sdk.horizon.responses.Page
import com.stellar.sdk.horizon.responses.operations.OperationResponse

/**
 * Builds requests connected to payments.
 *
 * Payments are a subset of operations - only operations that are payment-related.
 *
 * @see <a href="https://developers.stellar.org/api/resources/operations/list-payments/">Payments documentation</a>
 */
class PaymentsRequestBuilder(
    httpClient: HttpClient,
    serverUri: Url
) : RequestBuilder(httpClient, serverUri, "payments") {

    private val toJoin: MutableSet<String> = mutableSetOf()

    /**
     * Builds request to GET /accounts/{account}/payments
     * Returns all payment operations for a specific account.
     *
     * @param account Account for which to get payments
     * @return This request builder instance
     * @see <a href="https://developers.stellar.org/api/resources/accounts/payments/">Payments for Account</a>
     */
    fun forAccount(account: String): PaymentsRequestBuilder {
        setSegments("accounts", account, "payments")
        return this
    }

    /**
     * Builds request to GET /ledgers/{ledgerSeq}/payments
     * Returns all payment operations in a specific ledger.
     *
     * @param ledgerSeq Ledger sequence number for which to get payments
     * @return This request builder instance
     * @see <a href="https://developers.stellar.org/api/resources/ledgers/payments/">Payments for Ledger</a>
     */
    fun forLedger(ledgerSeq: Long): PaymentsRequestBuilder {
        setSegments("ledgers", ledgerSeq.toString(), "payments")
        return this
    }

    /**
     * Builds request to GET /transactions/{transactionId}/payments
     * Returns all payment operations in a specific transaction.
     *
     * @param transactionId Transaction ID for which to get payments
     * @return This request builder instance
     * @see <a href="https://developers.stellar.org/api/resources/transactions/payments/">Payments for Transaction</a>
     */
    fun forTransaction(transactionId: String): PaymentsRequestBuilder {
        setSegments("transactions", transactionId, "payments")
        return this
    }

    /**
     * Adds a parameter defining whether to include transactions in the response.
     * By default transaction data is not included.
     *
     * @param include Set to true to include transaction data in the payment operations response
     * @return This request builder instance
     */
    fun includeTransactions(include: Boolean): PaymentsRequestBuilder {
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
     * @return Page of operation responses (payment operations only)
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

    override fun cursor(cursor: String): PaymentsRequestBuilder {
        super.cursor(cursor)
        return this
    }

    override fun limit(number: Int): PaymentsRequestBuilder {
        super.limit(number)
        return this
    }

    override fun order(direction: Order): PaymentsRequestBuilder {
        super.order(direction)
        return this
    }
}
