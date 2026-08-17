package com.soneso.stellar.sdk.unitTests.horizon.responses

import com.soneso.stellar.sdk.horizon.responses.TransactionResponse
import com.soneso.stellar.sdk.unitTests.ClaimableBalanceVectors
import com.soneso.stellar.sdk.xdr.InnerTransactionResultResultXdr
import com.soneso.stellar.sdk.xdr.TransactionResultCodeXdr
import com.soneso.stellar.sdk.xdr.TransactionResultResultXdr
import com.soneso.stellar.sdk.xdr.TransactionResultXdr
import com.soneso.stellar.sdk.xdr.XdrReader
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

/**
 * The claimable balance id a submitted transaction reports for its CreateClaimableBalance
 * operations.
 */
class CreatedClaimableBalanceIdTest {

    private val json = Json { ignoreUnknownKeys = true }

    /** Two CreateClaimableBalance operations, one at each index. */
    private val twoCreates =
        "AAAAAAAAAMgAAAAAAAAAAgAAAAAAAAAOAAAAAAAAAAA/DDS/k60NmXHQTMyQ9wVRHIOKrZc0pKL7DXo" +
            "D/H/omgAAAAAAAAAOAAAAAAAAAAD16n+z3hja6fEq+WzwdQdJAW+8umvwnpAqaeVFaHcdggAAAAA="

    /** A payment at index 0, a CreateClaimableBalance at index 1. */
    private val paymentThenCreate =
        "AAAAAAAAAMgAAAAAAAAAAgAAAAAAAAABAAAAAAAAAAAAAAAOAAAAAAAAAAD16n+z3hja6fEq+WzwdQd" +
            "JAW+8umvwnpAqaeVFaHcdggAAAAA="

    /** A fee bump wrapping a transaction whose only operation creates a claimable balance. */
    private val feeBump =
        "AAAAAAAAAZAAAAABMzMzMzMzMzMzMzMzMzMzMzMzMzMzMzMzMzMzMzMzMzMAAAAAAAAAyAAAAAAAAAA" +
            "BAAAAAAAAAA4AAAAAAAAAAPXqf7PeGNrp8Sr5bPB1B0kBb7y6a/CekCpp5UVodx2CAAAAAAAAAAA="

    /**
     * A transaction rejected before any operation ran: txBAD_SEQ carries no operation results
     * at all.
     */
    private val badSeq = "AAAAAAAAAGT////7AAAAAA=="

    /**
     * A transaction that failed after its first operation had already created a balance: the
     * CreateClaimableBalance at index 0 succeeded and carries an id, the payment at index 1
     * found no destination. A ledger that failed applies none of it, so no balance exists and
     * the result code is the only thing that says so.
     */
    private val failedAfterCreate =
        "AAAAAAAAAMj/////AAAAAgAAAAAAAAAOAAAAAAAAAAD16n+z3hja6fEq+WzwdQdJAW+8umvwnpAqaeV" +
            "FaHcdggAAAAAAAAAB////+wAAAAA="

    /**
     * A fee bump whose inner transaction failed after its only operation had created a balance.
     * The outer result reads txFEE_BUMP_INNER_FAILED and the inner one txFAILED, so either code
     * on its own is enough to say no balance exists.
     */
    private val feeBumpInnerFailed =
        "AAAAAAAAAZD////zMzMzMzMzMzMzMzMzMzMzMzMzMzMzMzMzMzMzMzMzMzMAAAAAAAAAyP////8AAAA" +
            "BAAAAAAAAAA4AAAAAAAAAAPXqf7PeGNrp8Sr5bPB1B0kBb7y6a/CekCpp5UVodx2CAAAAAAAAAAA="

    /**
     * A fee bump only the outer result says failed: it reads txFEE_BUMP_INNER_FAILED while the
     * inner one reads txSUCCESS and carries the balance the operation created. The outer code is
     * the only thing between that id and a caller.
     */
    private val outerFailedInnerSuccess =
        "AAAAAAAAAZD////zMzMzMzMzMzMzMzMzMzMzMzMzMzMzMzMzMzMzMzMzMzMAAAAAAAAAyAAAAAAAAAA" +
            "BAAAAAAAAAA4AAAAAAAAAAPXqf7PeGNrp8Sr5bPB1B0kBb7y6a/CekCpp5UVodx2CAAAAAAAAAAA="

    /**
     * A fee bump only the inner result says failed: the outer result reads
     * txFEE_BUMP_INNER_SUCCESS while the inner one reads txFAILED. The inner code is the only
     * thing between the balance the operation result carries and a caller.
     */
    private val outerSuccessInnerFailed =
        "AAAAAAAAAZAAAAABMzMzMzMzMzMzMzMzMzMzMzMzMzMzMzMzMzMzMzMzMzMAAAAAAAAAyP////8AAAA" +
            "BAAAAAAAAAA4AAAAAAAAAAPXqf7PeGNrp8Sr5bPB1B0kBb7y6a/CekCpp5UVodx2CAAAAAAAAAAA="

    private val firstBalanceId = ClaimableBalanceVectors.strKey
    private val secondBalanceId = "BAAPL2T7WPPBRWXJ6EVPS3HQOUDUSALPXS5GX4E6SAVGTZKFNB3R3ATZWM"

    /**
     * A transaction response carrying [resultXdr]. Every other field holds what Horizon serves;
     * only the result is read for the balance id.
     */
    private fun response(resultXdr: String?, successful: Boolean = true): TransactionResponse {
        val result = if (resultXdr == null) "" else "\"result_xdr\": \"$resultXdr\","
        return json.decodeFromString(
            """
            {
                "id": "tx1",
                "paging_token": "123",
                "successful": $successful,
                "hash": "tx1",
                "ledger": 123,
                "created_at": "2022-01-01T00:00:00Z",
                "source_account": "GAJNSTFWKUKRXAHMPWG6BM4ACWNIS57S47KQZZQGQCM6H4WTM7VQUFMN",
                "source_account_sequence": 123,
                "fee_account": "GAJNSTFWKUKRXAHMPWG6BM4ACWNIS57S47KQZZQGQCM6H4WTM7VQUFMN",
                "fee_charged": 100,
                "max_fee": 1000,
                "operation_count": 1,
                $result
                "memo_type": "none",
                "signatures": ["sig1"],
                "_links": {
                    "self": {"href": "https://horizon-testnet.stellar.org/transactions/tx1"},
                    "account": {"href": "https://horizon-testnet.stellar.org/accounts/GAJNSTFWKUKRXAHMPWG6BM4ACWNIS57S47KQZZQGQCM6H4WTM7VQUFMN"},
                    "ledger": {"href": "https://horizon-testnet.stellar.org/ledgers/123"},
                    "operations": {"href": "https://horizon-testnet.stellar.org/transactions/tx1/operations"},
                    "effects": {"href": "https://horizon-testnet.stellar.org/transactions/tx1/effects"},
                    "precedes": {"href": "https://horizon-testnet.stellar.org/transactions?order=asc&cursor=123"},
                    "succeeds": {"href": "https://horizon-testnet.stellar.org/transactions?order=desc&cursor=123"}
                }
            }
            """.trimIndent()
        )
    }

    /**
     * Checks that [resultXdr] carries [outer] as its transaction result code and [inner] as the
     * result code of its inner transaction. Each fee bump case turns on one of the two codes, so
     * a fixture that came to spell another pair would leave its case green while the other code
     * did the rejecting.
     */
    @OptIn(ExperimentalEncodingApi::class)
    private fun assertFeeBumpResultCodes(
        resultXdr: String,
        outer: TransactionResultCodeXdr,
        inner: TransactionResultCodeXdr
    ) {
        val result = TransactionResultXdr.decode(XdrReader(Base64.decode(resultXdr)))
        val outerResult = assertIs<TransactionResultResultXdr.InnerResultPair>(
            result.result, "the fixture must carry a fee bump result"
        )
        assertEquals(outer, outerResult.discriminant, "the outer result code")
        val innerResult = assertIs<InnerTransactionResultResultXdr.Results>(
            outerResult.value.result.result, "the inner result must carry operation results"
        )
        assertEquals(inner, innerResult.discriminant, "the inner result code")
    }

    @Test
    fun testReportsTheBalanceIdOfEachOperation() {
        val response = response(twoCreates)
        assertEquals(firstBalanceId, response.getCreatedClaimableBalanceId())
        assertEquals(firstBalanceId, response.getCreatedClaimableBalanceId(operationIndex = 0))
        assertEquals(secondBalanceId, response.getCreatedClaimableBalanceId(operationIndex = 1))
    }

    @Test
    fun testAnswersNullForAnOperationThatCreatesNoBalance() {
        val response = response(paymentThenCreate)
        assertNull(response.getCreatedClaimableBalanceId())
        assertEquals(secondBalanceId, response.getCreatedClaimableBalanceId(operationIndex = 1))
    }

    @Test
    fun testAnswersNullForAnIndexNoOperationSitsAt() {
        val response = response(twoCreates)
        assertNull(response.getCreatedClaimableBalanceId(operationIndex = 2))
        assertNull(response.getCreatedClaimableBalanceId(operationIndex = Int.MAX_VALUE))
        assertNull(response.getCreatedClaimableBalanceId(operationIndex = -1))
        assertNull(response.getCreatedClaimableBalanceId(operationIndex = Int.MIN_VALUE))
    }

    @Test
    fun testReadsTheInnerOperationsOfAFeeBump() {
        val response = response(feeBump)
        assertEquals(secondBalanceId, response.getCreatedClaimableBalanceId())
        assertNull(response.getCreatedClaimableBalanceId(operationIndex = 1))
    }

    @Test
    fun testAnswersNullForAResultCarryingNoOperationResults() {
        assertNull(response(badSeq, successful = false).getCreatedClaimableBalanceId())
    }

    @Test
    fun testAnswersNullForABalanceCreatedByATransactionThatThenFailed() {
        // The operation result at index 0 carries a balance id, so reading the operation
        // results without judging the transaction result code would report one for a
        // transaction the ledger never applied.
        val response = response(failedAfterCreate, successful = false)
        assertNull(response.getCreatedClaimableBalanceId())
        assertNull(response.getCreatedClaimableBalanceId(operationIndex = 0))
        assertNull(response.getCreatedClaimableBalanceId(operationIndex = 1))
    }

    @Test
    fun testAnswersNullForABalanceCreatedByAFeeBumpThatThenFailed() {
        assertFeeBumpResultCodes(
            feeBumpInnerFailed,
            outer = TransactionResultCodeXdr.txFEE_BUMP_INNER_FAILED,
            inner = TransactionResultCodeXdr.txFAILED
        )
        val response = response(feeBumpInnerFailed, successful = false)
        assertNull(response.getCreatedClaimableBalanceId())
        assertNull(response.getCreatedClaimableBalanceId(operationIndex = 0))
    }

    @Test
    fun testAnswersNullWhenOnlyTheOuterResultOfAFeeBumpSaysItFailed() {
        assertFeeBumpResultCodes(
            outerFailedInnerSuccess,
            outer = TransactionResultCodeXdr.txFEE_BUMP_INNER_FAILED,
            inner = TransactionResultCodeXdr.txSUCCESS
        )
        val response = response(outerFailedInnerSuccess, successful = false)
        assertNull(response.getCreatedClaimableBalanceId())
        assertNull(response.getCreatedClaimableBalanceId(operationIndex = 0))
    }

    @Test
    fun testAnswersNullWhenOnlyTheInnerResultOfAFeeBumpSaysItFailed() {
        assertFeeBumpResultCodes(
            outerSuccessInnerFailed,
            outer = TransactionResultCodeXdr.txFEE_BUMP_INNER_SUCCESS,
            inner = TransactionResultCodeXdr.txFAILED
        )
        // The outer result reads as a success, so Horizon reports the transaction as one. The
        // result code, not that flag, is what says the ledger applied no operation.
        val response = response(outerSuccessInnerFailed, successful = true)
        assertNull(response.getCreatedClaimableBalanceId())
        assertNull(response.getCreatedClaimableBalanceId(operationIndex = 0))
    }

    @Test
    fun testAnswersNullWhenTheResultCannotBeRead() {
        assertNull(response(null).getCreatedClaimableBalanceId())
        assertNull(response("").getCreatedClaimableBalanceId())
        assertNull(response("not base64 at all !!").getCreatedClaimableBalanceId())
        // Valid base64 whose bytes are no transaction result.
        assertNull(response("AAAAAQ==").getCreatedClaimableBalanceId())
    }
}
