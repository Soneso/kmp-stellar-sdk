package com.soneso.stellar.sdk.unitTests.rpc.responses

import com.soneso.stellar.sdk.StrKey
import com.soneso.stellar.sdk.rpc.SorobanServer
import com.soneso.stellar.sdk.rpc.exception.SimulationFailureDetails
import com.soneso.stellar.sdk.rpc.responses.ReportedContractError
import com.soneso.stellar.sdk.rpc.responses.SimulateTransactionResponse
import com.soneso.stellar.sdk.scval.Scv
import com.soneso.stellar.sdk.util.TestResourceUtil
import com.soneso.stellar.sdk.xdr.ContractEventBodyXdr
import com.soneso.stellar.sdk.xdr.ContractEventTypeXdr
import com.soneso.stellar.sdk.xdr.ContractEventV0Xdr
import com.soneso.stellar.sdk.xdr.ContractEventXdr
import com.soneso.stellar.sdk.xdr.ContractIDXdr
import com.soneso.stellar.sdk.xdr.DiagnosticEventXdr
import com.soneso.stellar.sdk.xdr.ExtensionPointXdr
import com.soneso.stellar.sdk.xdr.HashXdr
import com.soneso.stellar.sdk.xdr.SCErrorXdr
import com.soneso.stellar.sdk.xdr.SCSymbolXdr
import com.soneso.stellar.sdk.xdr.SCValXdr
import com.soneso.stellar.sdk.xdr.Uint32Xdr
import com.soneso.stellar.sdk.xdr.toXdrBase64
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockEngineConfig
import io.ktor.client.engine.mock.MockRequestHandler
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.assertEquals

/**
 * Captured simulation responses of stellar-rpc 30.0.1 and their 30.0.0 twins, and builders for
 * failed simulation responses and diagnostic events.
 */
internal object SimulationFixtures {
    /** A constructor whose threshold policy rejects the install: masked `Error(Context, ...)`. */
    const val CASE_A = "case-a"

    /** A registered signer with a bad signature: masked `Error(Auth, ...)`, code 3114. */
    const val CASE_B1 = "case-b1-registered-badsig"

    /** An unregistered signer: masked `Error(Auth, ...)`, code 3016. */
    const val CASE_B2 = "case-b2-unregistered"

    /** A registered signer that fails with the right challenge: no contract error event. */
    const val CASE_B3 = "case-b3-registered-rightchallenge-badsig"

    /** A policy read that fails with `Error(Contract, #3200)` in the header. */
    const val CASE_C1 = "case-c1-policy-get-threshold"

    /** A SAC transfer of a negative amount: `Error(Contract, #8)` in the header. */
    const val CASE_C2 = "case-c2-sac-negative-transfer"

    const val CASE_A_CONTRACT = "CAZJ3UVRY3R3S5C5BH32GMYBRSN23N75ZEEXEOLXOUUAHDFIMVP4AXUC"
    const val CASE_B1_CONTRACT = "CB26VN37RCVNTHJZDEPK6IRO2MMTS3Z2IEO5JD5BINY2OOJ5KKJG7NKY"
    const val CASE_B2_CONTRACT = "CABAV4UNU66OZ75MXQDX3FFO35WNQLLXG2HZAHKJNZ27C2LGAX4DK253"

    const val MASKED_AUTH = "HostError: Error(Auth, InvalidAction)"
    const val MASKED_CONTEXT = "HostError: Error(Context, InvalidAction)"

    /** Two contract IDs for events built in code. */
    const val CONTRACT_ONE = "CDCYWK73YTYFJZZSJ5V7EDFNHYBG4QN3VUNG2IGD27KJDDPNCZKBCBXK"
    const val CONTRACT_TWO = "CA3D5KRYM6CB7OWQ6TWYRR3Z4T7GNZLKERYNZGGA5SOAOPIFY6YQGAXE"

    /** Suffix of the failed-simulation messages for a contract error from the events. */
    fun suffix(code: UInt, contractId: String?): String =
        " (contract error in diagnostic events: Error(Contract, #$code)" +
            (contractId?.let { ", contract $it" } ?: "") + ")"

    private val json = Json { ignoreUnknownKeys = true }

    /** The JSON-RPC response body of the 30.0.1 capture, or of its 30.0.0 twin. */
    fun body(case: String, rpc30_0_0: Boolean = false): String {
        val name = if (rpc30_0_0) "$case-rpc30.0.0-response.json" else "$case-response.json"
        return TestResourceUtil.readFixtureBytes(name).decodeToString()
    }

    /** The `result` member of [body], decoded. */
    fun response(case: String, rpc30_0_0: Boolean = false): SimulateTransactionResponse {
        val result = json.parseToJsonElement(body(case, rpc30_0_0)).jsonObject.getValue("result")
        return json.decodeFromJsonElement(SimulateTransactionResponse.serializer(), result)
    }

    /** A JSON-RPC simulateTransaction body with [error] and, when non-null, [events]. */
    fun simulateErrorBody(error: String, events: List<String>?): String = buildJsonObject {
        put("jsonrpc", "2.0")
        put("id", "1")
        putJsonObject("result") {
            put("error", error)
            if (events != null) {
                putJsonArray("events") { events.forEach { add(it) } }
            }
            put("latestLedger", 100)
        }
    }.toString()

    private val errorTopic = SCValXdr.Sym(SCSymbolXdr("error"))

    /** A base64 DiagnosticEvent with the given parts. */
    fun diagnosticEvent(
        topics: List<SCValXdr>,
        data: SCValXdr = Scv.toVoid(),
        contractId: String? = CONTRACT_ONE,
        type: ContractEventTypeXdr = ContractEventTypeXdr.DIAGNOSTIC,
        inSuccessfulContractCall: Boolean = false
    ): String = DiagnosticEventXdr(
        inSuccessfulContractCall = inSuccessfulContractCall,
        event = ContractEventXdr(
            ext = ExtensionPointXdr.Void,
            contractId = contractId?.let { ContractIDXdr(HashXdr(StrKey.decodeContract(it))) },
            type = type,
            body = ContractEventBodyXdr.V0(ContractEventV0Xdr(topics, data))
        )
    ).toXdrBase64()

    /** The `Error(Contract, #code)` value. */
    fun contractCode(code: UInt): SCValXdr = SCValXdr.Error(SCErrorXdr.ContractCode(Uint32Xdr(code)))

    /** A diagnostic event that reports contract error [code]: topics `error` and the code. */
    fun contractErrorEvent(
        code: UInt,
        contractId: String? = CONTRACT_ONE,
        inSuccessfulContractCall: Boolean = false
    ): String = diagnosticEvent(
        topics = listOf(errorTopic, contractCode(code)),
        contractId = contractId,
        inSuccessfulContractCall = inSuccessfulContractCall
    )

    /** [event] with its last [bytes] bytes cut off, encoded as valid base64. */
    @OptIn(ExperimentalEncodingApi::class)
    fun truncated(event: String, bytes: Int = 4): String {
        val raw = Base64.decode(event)
        return Base64.encode(raw.copyOf(raw.size - bytes))
    }

    /** Asserts the message, the diagnostic events and the reported contract error of [failure]. */
    fun <F> assertCarries(failure: F, message: String, events: List<String>, reported: ReportedContractError?)
        where F : Throwable, F : SimulationFailureDetails {
        assertEquals(message, failure.message)
        assertEquals(events, failure.diagnosticEventsXdr)
        assertEquals(reported, failure.reportedContractError)
    }
}

/** A JSON-RPC reply: the body and its HTTP status. */
internal data class RpcReply(val body: String, val status: HttpStatusCode = HttpStatusCode.OK)

/**
 * A scripted Soroban RPC. The n-th request is answered with the n-th entry of [script], and the
 * JSON-RPC method of every request is recorded in [methods] in order. A request beyond the
 * script is recorded and answered with a JSON-RPC error, so a flow that asks for more fails and
 * shows the extra method in [methods].
 *
 * [server] builds a new client over the same script each time, because the code under test may
 * close a server it created.
 */
internal class ScriptedRpc(private val script: List<RpcReply>) {
    val methods = mutableListOf<String>()

    constructor(vararg bodies: String) : this(bodies.map { RpcReply(it) })

    private val handler: MockRequestHandler = { request ->
        val body = request.body.toByteArray().decodeToString()
        val index = methods.size
        methods.add(METHOD.find(body)?.groupValues?.get(1) ?: "")
        val reply = script.getOrNull(index) ?: RpcReply(UNEXPECTED_REQUEST)
        respond(
            content = ByteReadChannel(reply.body),
            status = reply.status,
            headers = headersOf(HttpHeaders.ContentType, "application/json")
        )
    }

    /**
     * A server over the script. [unconfined] runs the handler on [Dispatchers.Unconfined], which a
     * flow with a `delay`-based poll under `runTest` needs.
     */
    fun server(url: String = "https://soroban-testnet.stellar.org", unconfined: Boolean = false): SorobanServer {
        val engine = if (unconfined) {
            MockEngine(MockEngineConfig().apply {
                dispatcher = Dispatchers.Unconfined
                addHandler(handler)
            })
        } else {
            MockEngine(handler)
        }
        val client = HttpClient(engine) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = false })
            }
        }
        return SorobanServer(url, client)
    }

    fun count(method: String): Int = methods.count { it == method }

    private companion object {
        val METHOD = Regex("\"method\"\\s*:\\s*\"([^\"]+)\"")

        const val UNEXPECTED_REQUEST =
            """{"jsonrpc":"2.0","id":"1","error":{"code":-32600,"message":"request beyond the script"}}"""
    }
}
