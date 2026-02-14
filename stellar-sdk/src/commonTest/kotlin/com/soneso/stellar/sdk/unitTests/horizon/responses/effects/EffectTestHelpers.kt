package com.soneso.stellar.sdk.unitTests.horizon.responses.effects

import com.soneso.stellar.sdk.horizon.responses.Link
import com.soneso.stellar.sdk.horizon.responses.effects.EffectResponse

/**
 * Shared helpers for effect response tests.
 */
object EffectTestHelpers {
    /** A reusable EffectLinks instance for tests */
    fun testLinks() = EffectResponse.EffectLinks(
        operation = Link(href = "https://horizon.stellar.org/operations/12345", templated = false),
        precedes = Link(href = "https://horizon.stellar.org/effects?cursor=12345&order=asc"),
        succeeds = Link(href = "https://horizon.stellar.org/effects?cursor=12345&order=desc")
    )

    /** Standard _links JSON block */
    const val LINKS_JSON = """
        "_links": {
            "operation": { "href": "https://horizon.stellar.org/operations/12345", "templated": false },
            "precedes": { "href": "https://horizon.stellar.org/effects?cursor=12345&order=asc" },
            "succeeds": { "href": "https://horizon.stellar.org/effects?cursor=12345&order=desc" }
        }
    """

    const val TEST_ACCOUNT = "GAAZI4TCR3TY5OJHCTJC2A4QSY6CJWJH5IAJTGKIN2ER7LBNVKOCCWN7"
    const val TEST_ACCOUNT_MUXED = "MAAZI4TCR3TY5OJHCTJC2A4QSY6CJWJH5IAJTGKIN2ER7LBNVKOCCWAAAAAAAAAAAAAGPQ"
    const val TEST_ACCOUNT_MUXED_ID = "1234567890"
    const val TEST_ACCOUNT_2 = "GDUKMGUGDZQK6YHYA5Z6AY2G4XDSZPSZ3SW5UN3ARVMO6QSRDWP5YLEX"
    const val TEST_ACCOUNT_3 = "GA5ZSEJYB37JRC5AVCIA5MOP4RHTM335X2KGX3IHOJAPP5RE34K4KZVN"
    const val TEST_CREATED_AT = "2023-01-15T12:00:00Z"
    const val TEST_PAGING_TOKEN = "12345-1"
}
