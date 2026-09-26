package com.soneso.stellar.sdk.unitTests.horizon.requests

import com.soneso.stellar.sdk.Util
import com.soneso.stellar.sdk.horizon.requests.addClientIdentification
import io.ktor.http.*
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Tests for the stream URL the JS `sseRequest` implementation hands to `EventSource`.
 *
 * `EventSource` cannot set request headers, so the client identification travels as query
 * parameters, which Horizon reads when the headers are absent.
 */
class SSEStreamJsTest {

    @Test
    fun testStreamUrlCarriesClientIdentificationQueryParameters() {
        val url = Url(addClientIdentification(Url("https://horizon.example.org/transactions?cursor=now")))

        assertEquals("/transactions", url.encodedPath)
        assertEquals("now", url.parameters["cursor"], "Existing query parameters are kept")
        assertEquals(listOf("kmp-stellar-sdk"), url.parameters.getAll("X-Client-Name"))
        assertEquals(listOf(Util.getSdkVersion()), url.parameters.getAll("X-Client-Version"))
    }
}
