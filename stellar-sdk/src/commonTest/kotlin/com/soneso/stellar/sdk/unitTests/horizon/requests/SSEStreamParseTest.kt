package com.soneso.stellar.sdk.unitTests.horizon.requests

import com.soneso.stellar.sdk.horizon.requests.parseSSEStream
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** Event framing of [parseSSEStream], the parser behind the JVM and native SSE requests. */
class SSEStreamParseTest {

    @Test
    fun testEventsCarryTheLastIdAndTheirJoinedDataLines() = runTest {
        val events = mutableListOf<Pair<String?, String>>()
        val failures = mutableListOf<Throwable>()

        parseSSEStream(
            channel = ByteReadChannel("id: 7\ndata: first\n\ndata: second\ndata: third\n\n"),
            onEvent = { eventId, data -> events.add(eventId to data) },
            onFailure = { error, _ -> failures.add(error) },
            statusCode = 200
        )

        assertEquals(listOf<Pair<String?, String>>("7" to "first", "7" to "second\nthird"), events)
        assertEquals(emptyList(), failures)
    }
}
