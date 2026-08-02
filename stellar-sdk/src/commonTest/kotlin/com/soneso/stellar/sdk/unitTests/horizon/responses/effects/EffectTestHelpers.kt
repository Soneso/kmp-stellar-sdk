package com.soneso.stellar.sdk.unitTests.horizon.responses.effects

import com.soneso.stellar.sdk.horizon.responses.Link
import com.soneso.stellar.sdk.horizon.responses.effects.EffectResponse
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Shared helpers for effect response tests.
 */
object EffectTestHelpers {
    const val TEST_OPERATION_HREF = "https://horizon.stellar.org/operations/12345"
    const val TEST_PRECEDES_HREF = "https://horizon.stellar.org/effects?cursor=12345&order=asc"
    const val TEST_SUCCEEDS_HREF = "https://horizon.stellar.org/effects?cursor=12345&order=desc"

    /** A reusable EffectLinks instance for tests */
    fun testLinks() = EffectResponse.EffectLinks(
        operation = Link(href = TEST_OPERATION_HREF, templated = false),
        precedes = Link(href = TEST_PRECEDES_HREF),
        succeeds = Link(href = TEST_SUCCEEDS_HREF)
    )

    /**
     * Asserts that [links] carries the operation, precedes and succeeds targets produced by
     * [testLinks] and encoded in [LINKS_JSON]. Each link is checked individually so that a
     * mixed-up link role is detected.
     */
    fun assertStandardLinks(links: EffectResponse.EffectLinks) {
        assertEquals(TEST_OPERATION_HREF, links.operation.href)
        assertEquals(false, links.operation.templated)
        assertEquals(TEST_PRECEDES_HREF, links.precedes.href)
        assertNull(links.precedes.templated)
        assertEquals(TEST_SUCCEEDS_HREF, links.succeeds.href)
        assertNull(links.succeeds.templated)
    }

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
