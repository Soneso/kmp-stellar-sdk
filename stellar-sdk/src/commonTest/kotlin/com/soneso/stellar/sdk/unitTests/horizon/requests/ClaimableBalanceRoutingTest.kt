package com.soneso.stellar.sdk.unitTests.horizon.requests

import com.soneso.stellar.sdk.horizon.HorizonServer
import com.soneso.stellar.sdk.unitTests.ClaimableBalanceVectors
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * The route and the claimable balance id spelling the request builders put on the wire.
 *
 * Horizon serves a claimable balance under one spelling of its id, the hexadecimal of the XDR
 * form. Every spelling a caller may hold has to reach that route, and an id naming no balance
 * has to be reported before a request is sent.
 */
class ClaimableBalanceRoutingTest {

    private val serverUrl = "https://horizon-testnet.stellar.org"

    private val strKey = ClaimableBalanceVectors.strKey

    /** The spelling Horizon serves: the hexadecimal of the XDR form. */
    private val horizonHex = ClaimableBalanceVectors.paddedHex

    private val spellings = ClaimableBalanceVectors.spellings

    /**
     * Ids naming no claimable balance, beyond [ClaimableBalanceVectors.rejections]: a length no
     * spelling has, and the legacy 63-character id.
     */
    private val rejected = listOf(
        "not a claimable balance id",
        "BAAAAAAAJXMXZMNAXINW4GQYRNE55L523HRUZAHCO7BXEX4BLR2XYLIF3X7IL2Y"
    ) + ClaimableBalanceVectors.rejections

    private val claimableBalanceJson = """
        {
            "id": "$horizonHex",
            "asset": "native",
            "amount": "12.3000000",
            "sponsor": "GAAZI4TCR3TY5OJHCTJC2A4QSY6CJWJH5IAJTGKIN2ER7LBNVKOCCWN7",
            "last_modified_ledger": 7877447,
            "last_modified_time": "2021-11-18T03:47:47Z",
            "paging_token": "12884905984-$horizonHex",
            "claimants": [
                {
                    "destination": "GCXKG6RN4ONIEPCMNFB732A436Z5PNDSRLGWK7GBLCMQLIFO4S7EYWVU",
                    "predicate": {"unconditional": true}
                }
            ],
            "_links": {
                "self": {"href": "$serverUrl/claimable_balances/$horizonHex"}
            }
        }
    """.trimIndent()

    private val emptyPageJson = """
        {
            "_links": {"self": {"href": "$serverUrl/claimable_balances"}},
            "_embedded": {"records": []}
        }
    """.trimIndent()

    /**
     * A client serving [body] for every request, recording the path of each one it is asked
     * for. The mock matches every path, so a request for a route the SDK should never build is
     * recorded here rather than escaping to the network.
     */
    private fun recordingClient(body: String, recorded: MutableList<String>): HttpClient {
        val engine = MockEngine { request ->
            recorded.add(request.url.encodedPath)
            respond(
                content = body,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        return HttpClient(engine) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true; isLenient = true })
            }
        }
    }

    // ========== The id every claimable balance route carries ==========

    @Test
    fun testClaimableBalanceAsksForTheHorizonHexOfEverySpelling() = runTest {
        for (spelling in spellings) {
            val recorded = mutableListOf<String>()
            val server = HorizonServer(
                serverUrl,
                httpClient = recordingClient(claimableBalanceJson, recorded)
            )

            val balance = server.claimableBalances().claimableBalance(spelling)

            assertEquals(horizonHex, balance.id, "spelling: $spelling")
            assertEquals(listOf("/claimable_balances/$horizonHex"), recorded, "spelling: $spelling")
            server.close()
        }
    }

    @Test
    fun testOperationsForClaimableBalanceAsksForTheHorizonHexOfEverySpelling() = runTest {
        for (spelling in spellings) {
            val recorded = mutableListOf<String>()
            val server =
                HorizonServer(serverUrl, httpClient = recordingClient(emptyPageJson, recorded))

            val builder = server.operations().forClaimableBalance(spelling)
            assertEquals(
                "/claimable_balances/$horizonHex/operations", builder.buildUrl().encodedPath,
                "spelling: $spelling"
            )

            builder.execute()
            assertEquals(
                listOf("/claimable_balances/$horizonHex/operations"), recorded,
                "spelling: $spelling"
            )
            server.close()
        }
    }

    @Test
    fun testTransactionsForClaimableBalanceAsksForTheHorizonHexOfEverySpelling() = runTest {
        for (spelling in spellings) {
            val recorded = mutableListOf<String>()
            val server =
                HorizonServer(serverUrl, httpClient = recordingClient(emptyPageJson, recorded))

            val builder = server.transactions().forClaimableBalance(spelling)
            assertEquals(
                "/claimable_balances/$horizonHex/transactions", builder.buildUrl().encodedPath,
                "spelling: $spelling"
            )

            builder.execute()
            assertEquals(
                listOf("/claimable_balances/$horizonHex/transactions"), recorded,
                "spelling: $spelling"
            )
            server.close()
        }
    }

    @Test
    fun testTheRouteCarriesTheCursorAndTheHorizonHexTogether() {
        val server = HorizonServer(serverUrl)
        val url = server.transactions().forClaimableBalance(strKey).cursor("now").buildUrl()
        assertEquals("/claimable_balances/$horizonHex/transactions", url.encodedPath)
        assertEquals("now", url.parameters["cursor"])
        server.close()
    }

    // ========== An id naming no claimable balance ==========

    @Test
    fun testClaimableBalanceRejectsAnIdNamingNoBalanceBeforeAnyRequest() = runTest {
        for (candidate in rejected) {
            val recorded = mutableListOf<String>()
            val server = HorizonServer(
                serverUrl,
                httpClient = recordingClient(claimableBalanceJson, recorded)
            )

            assertFailsWith<IllegalArgumentException>("\"$candidate\" must be rejected") {
                server.claimableBalances().claimableBalance(candidate)
            }
            assertTrue(recorded.isEmpty(), "\"$candidate\": no request may be sent")
            server.close()
        }
    }

    @Test
    fun testOperationsForClaimableBalanceRejectsAnIdNamingNoBalanceBeforeAnyRequest() = runTest {
        for (candidate in rejected) {
            val recorded = mutableListOf<String>()
            val server =
                HorizonServer(serverUrl, httpClient = recordingClient(emptyPageJson, recorded))

            assertFailsWith<IllegalArgumentException>("\"$candidate\" must be rejected") {
                server.operations().forClaimableBalance(candidate)
            }
            assertTrue(recorded.isEmpty(), "\"$candidate\": no request may be sent")
            server.close()
        }
    }

    @Test
    fun testTransactionsForClaimableBalanceRejectsAnIdNamingNoBalanceBeforeAnyRequest() = runTest {
        for (candidate in rejected) {
            val recorded = mutableListOf<String>()
            val server =
                HorizonServer(serverUrl, httpClient = recordingClient(emptyPageJson, recorded))

            assertFailsWith<IllegalArgumentException>("\"$candidate\" must be rejected") {
                server.transactions().forClaimableBalance(candidate)
            }
            assertTrue(recorded.isEmpty(), "\"$candidate\": no request may be sent")
            server.close()
        }
    }

    @Test
    fun testTheRejectionNamesTheAcceptedSpellings() {
        val server = HorizonServer(serverUrl)
        val failure = assertFailsWith<IllegalArgumentException> {
            server.operations().forClaimableBalance("not a claimable balance id")
        }
        assertEquals(
            "claimable balance id must be a 58 character strkey (B...), or hex of the bare id " +
                "(64 characters), which a discriminant may prefix to 66 or 72 characters; " +
                "26 characters given",
            failure.message
        )
        server.close()
    }
}
