package com.soneso.stellar.sdk.unitTests.horizon.responses.operations

import com.soneso.stellar.sdk.horizon.responses.Link
import com.soneso.stellar.sdk.horizon.responses.operations.OperationResponse

/**
 * Shared helpers for operation response tests.
 */
object OperationTestHelpers {

    fun testLinks() = OperationResponse.Links(
        effects = Link(href = "https://horizon.stellar.org/operations/12345/effects"),
        precedes = Link(href = "https://horizon.stellar.org/effects?cursor=12345&order=asc"),
        self = Link(href = "https://horizon.stellar.org/operations/12345"),
        succeeds = Link(href = "https://horizon.stellar.org/effects?cursor=12345&order=desc"),
        transaction = Link(href = "https://horizon.stellar.org/transactions/abc123")
    )

    const val LINKS_JSON = """
        "_links": {
            "effects": { "href": "https://horizon.stellar.org/operations/12345/effects" },
            "precedes": { "href": "https://horizon.stellar.org/effects?cursor=12345&order=asc" },
            "self": { "href": "https://horizon.stellar.org/operations/12345" },
            "succeeds": { "href": "https://horizon.stellar.org/effects?cursor=12345&order=desc" },
            "transaction": { "href": "https://horizon.stellar.org/transactions/abc123" }
        }
    """

    const val TEST_ID = "12345"
    const val TEST_SOURCE_ACCOUNT = "GAAZI4TCR3TY5OJHCTJC2A4QSY6CJWJH5IAJTGKIN2ER7LBNVKOCCWN7"
    const val TEST_SOURCE_ACCOUNT_MUXED = "MAAZI4TCR3TY5OJHCTJC2A4QSY6CJWJH5IAJTGKIN2ER7LBNVKOCCWAAAAAAAAAAAAAGPQ"
    const val TEST_SOURCE_ACCOUNT_MUXED_ID = "1234567890"
    const val TEST_PAGING_TOKEN = "12345"
    const val TEST_CREATED_AT = "2023-01-15T12:00:00Z"
    const val TEST_TX_HASH = "abc123def456"
    const val TEST_ACCOUNT = "GDUKMGUGDZQK6YHYA5Z6AY2G4XDSZPSZ3SW5UN3ARVMO6QSRDWP5YLEX"
    const val TEST_ACCOUNT_2 = "GA5ZSEJYB37JRC5AVCIA5MOP4RHTM335X2KGX3IHOJAPP5RE34K4KZVN"
    const val TEST_ACCOUNT_3 = "GBXGQJWVLWOYHFLVTKWV5FGHA3LNYY2JQKM7OAEZ7Y3PFKIMZLHSE5N"

    /** Minimal base JSON fields shared by all operations (without leading/trailing braces). */
    fun baseFieldsJson(type: String): String = """
        "id": "$TEST_ID",
        "source_account": "$TEST_SOURCE_ACCOUNT",
        "paging_token": "$TEST_PAGING_TOKEN",
        "created_at": "$TEST_CREATED_AT",
        "transaction_hash": "$TEST_TX_HASH",
        "transaction_successful": true,
        "type": "$type",
        $LINKS_JSON
    """.trimIndent()
}
