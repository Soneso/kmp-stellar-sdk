package com.soneso.stellar.sdk.unitTests.horizon.requests

import com.soneso.stellar.sdk.horizon.requests.parseSSEStream
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.close
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals

/** Event framing and failure reporting of [parseSSEStream], the parser behind the JVM and native SSE requests. */
@OptIn(ExperimentalCoroutinesApi::class)
class SSEStreamParseTest {

    private val events = mutableListOf<Pair<String?, String>>()
    private val failures = mutableListOf<Pair<Throwable, Int?>>()

    private suspend fun parse(channel: ByteReadChannel, onEvent: (String?, String) -> Unit = { id, data -> events.add(id to data) }) =
        parseSSEStream(channel, onEvent, { error, statusCode -> failures.add(error to statusCode) }, statusCode = 200)

    @Test
    fun testEventsCarryTheLastIdAndTheirJoinedDataLines() = runTest {
        parse(ByteReadChannel("id: 7\ndata: first\n\n: keepalive\n\nunknown: value\ndata: second\ndata: third\n\n"))
        assertEquals(listOf<Pair<String?, String>>("7" to "first", "7" to "second\nthird"), events)
        assertEquals(emptyList(), failures)
    }

    @Test
    fun testReadFailureIsReportedWithTheStatusCode() = runTest {
        val channel = ByteChannel(autoFlush = true)
        val parsing = launch { parse(channel) }
        advanceUntilIdle() // The parser now waits for a line.
        val failure = IOException("SSE body interrupted")
        channel.close(failure) // Ktor rethrows it wrapped in ClosedByteChannelException.
        parsing.join()
        val reported = failures.map { (error, code) -> generateSequence(error) { it.cause }.last() to code }
        assertEquals(listOf<Pair<Throwable, Int?>>(failure to 200), reported)
    }

    @Test
    fun testListenerThatClosesTheStreamEndsTheParseWithoutFailure() = runTest {
        val input = ByteReadChannel("data: first\n\ndata: second\n\n")
        launch { parse(input) { id, data -> events.add(id to data); this@launch.cancel() } }.join()
        assertEquals(listOf<Pair<String?, String>>(null to "first"), events)
        assertEquals(emptyList(), failures)
    }

    @Test
    fun testListenerFailureEndsTheParseAndIsReported() = runTest {
        val failure = IllegalStateException("listener failed")
        parse(ByteReadChannel("data: first\n\ndata: second\n\n")) { id, data -> events.add(id to data); throw failure }
        assertEquals(listOf<Pair<String?, String>>(null to "first"), events)
        assertEquals(listOf<Pair<Throwable, Int?>>(failure to 200), failures)
    }
}
