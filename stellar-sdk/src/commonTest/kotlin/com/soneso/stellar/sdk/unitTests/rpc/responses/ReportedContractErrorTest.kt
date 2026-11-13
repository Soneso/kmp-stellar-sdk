package com.soneso.stellar.sdk.unitTests.rpc.responses

import com.soneso.stellar.sdk.rpc.responses.ReportedContractError
import com.soneso.stellar.sdk.rpc.responses.ReportedContractError.Provenance.DIAGNOSTIC_EVENTS
import com.soneso.stellar.sdk.rpc.responses.ReportedContractError.Provenance.HOST_ERROR
import com.soneso.stellar.sdk.rpc.responses.SimulateTransactionResponse
import com.soneso.stellar.sdk.rpc.responses.SimulationFailureMessage
import com.soneso.stellar.sdk.scval.Scv
import com.soneso.stellar.sdk.unitTests.rpc.responses.SimulationFixtures.CASE_A
import com.soneso.stellar.sdk.unitTests.rpc.responses.SimulationFixtures.CASE_A_CONTRACT
import com.soneso.stellar.sdk.unitTests.rpc.responses.SimulationFixtures.CASE_B1
import com.soneso.stellar.sdk.unitTests.rpc.responses.SimulationFixtures.CASE_B1_CONTRACT
import com.soneso.stellar.sdk.unitTests.rpc.responses.SimulationFixtures.CASE_B2
import com.soneso.stellar.sdk.unitTests.rpc.responses.SimulationFixtures.CASE_B2_CONTRACT
import com.soneso.stellar.sdk.unitTests.rpc.responses.SimulationFixtures.CASE_B3
import com.soneso.stellar.sdk.unitTests.rpc.responses.SimulationFixtures.CASE_C1
import com.soneso.stellar.sdk.unitTests.rpc.responses.SimulationFixtures.CASE_C2
import com.soneso.stellar.sdk.unitTests.rpc.responses.SimulationFixtures.CONTRACT_ONE
import com.soneso.stellar.sdk.unitTests.rpc.responses.SimulationFixtures.CONTRACT_TWO
import com.soneso.stellar.sdk.unitTests.rpc.responses.SimulationFixtures.MASKED_AUTH
import com.soneso.stellar.sdk.unitTests.rpc.responses.SimulationFixtures.MASKED_CONTEXT
import com.soneso.stellar.sdk.unitTests.rpc.responses.SimulationFixtures.contractCode
import com.soneso.stellar.sdk.unitTests.rpc.responses.SimulationFixtures.contractErrorEvent
import com.soneso.stellar.sdk.unitTests.rpc.responses.SimulationFixtures.diagnosticEvent
import com.soneso.stellar.sdk.unitTests.rpc.responses.SimulationFixtures.response
import com.soneso.stellar.sdk.unitTests.rpc.responses.SimulationFixtures.truncated
import com.soneso.stellar.sdk.xdr.ContractEventTypeXdr
import com.soneso.stellar.sdk.xdr.SCErrorCodeXdr
import com.soneso.stellar.sdk.xdr.SCErrorTypeXdr
import com.soneso.stellar.sdk.xdr.SCErrorXdr
import com.soneso.stellar.sdk.xdr.SCStringXdr
import com.soneso.stellar.sdk.xdr.SCSymbolXdr
import com.soneso.stellar.sdk.xdr.SCValXdr
import com.soneso.stellar.sdk.xdr.Uint32Xdr
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * [SimulateTransactionResponse.reportedContractError] on the captured responses of
 * stellar-rpc 30.0.1 and 30.0.0, on host error headers in their accepted and rejected forms,
 * and on diagnostic events that do or do not report a contract error.
 */
class ReportedContractErrorTest {

    private fun reported(error: String?, events: List<String>?): ReportedContractError? =
        SimulateTransactionResponse(error = error, events = events).reportedContractError()

    private fun fromEvents(code: UInt, contractId: String?) =
        ReportedContractError(code, DIAGNOSTIC_EVENTS, contractId)

    private fun fromHeader(code: UInt) = ReportedContractError(code, HOST_ERROR, null)

    private val errorTopic = SCValXdr.Sym(SCSymbolXdr("error"))

    // ========== Captured responses ==========

    @Test
    fun testCaseA_constructorFailure_reportsTheEarliestContractErrorEvent() {
        for (rpc30_0_0 in listOf(false, true)) {
            val simulation = response(CASE_A, rpc30_0_0)
            assertEquals(13, simulation.events?.size, "capture rpc30_0_0=$rpc30_0_0")
            val reported = simulation.reportedContractError()
            assertEquals(fromEvents(3201u, CASE_A_CONTRACT), reported, "capture rpc30_0_0=$rpc30_0_0")
            assertEquals(
                " (contract error in diagnostic events: Error(Contract, #3201), contract $CASE_A_CONTRACT)",
                SimulationFailureMessage.suffix(reported)
            )
        }
    }

    @Test
    fun testCaseB_checkAuthFailures_reportTheSignerError() {
        for (rpc30_0_0 in listOf(false, true)) {
            assertEquals(
                fromEvents(3016u, CASE_B2_CONTRACT),
                response(CASE_B2, rpc30_0_0).reportedContractError(),
                "unregistered signer, rpc30_0_0=$rpc30_0_0"
            )
            assertEquals(
                fromEvents(3114u, CASE_B1_CONTRACT),
                response(CASE_B1, rpc30_0_0).reportedContractError(),
                "registered signer with a bad signature, rpc30_0_0=$rpc30_0_0"
            )
        }
    }

    @Test
    fun testCaseB3_noContractErrorEvent_reportsNothing() {
        for (rpc30_0_0 in listOf(false, true)) {
            assertNull(response(CASE_B3, rpc30_0_0).reportedContractError(), "rpc30_0_0=$rpc30_0_0")
        }
    }

    @Test
    fun testCaseC_contractErrorHeader_reportsTheHeaderCode() {
        for (rpc30_0_0 in listOf(false, true)) {
            val c1 = response(CASE_C1, rpc30_0_0).reportedContractError()
            assertEquals(fromHeader(3200u), c1, "policy read, rpc30_0_0=$rpc30_0_0")
            assertEquals("", SimulationFailureMessage.suffix(c1), "a header code adds no suffix")
            assertEquals(
                fromHeader(8u),
                response(CASE_C2, rpc30_0_0).reportedContractError(),
                "SAC transfer, rpc30_0_0=$rpc30_0_0"
            )
        }
    }

    // ========== Host error header ==========

    @Test
    fun testHeader_contractCodeWins_overAQualifyingEvent() {
        val c1 = response(CASE_C1)
        val withEvent = c1.copy(events = listOf(contractErrorEvent(3016u)) + c1.events.orEmpty())
        assertEquals(fromHeader(3200u), withEvent.reportedContractError())
    }

    @Test
    fun testHeader_onlyTheFirstLineIsRead() {
        // The 30.0.0 text of case-a names #3201 in its event log; without events nothing is found.
        val text = response(CASE_A, rpc30_0_0 = true).error!!
        assertEquals(true, text.contains("Error(Contract, #3201)"))
        assertNull(reported(text, null))
        assertNull(reported("simulation failed\nHostError: Error(Contract, #8)", null))
        assertNull(reported("$MASKED_CONTEXT\nHostError: Error(Contract, #8)", null))
    }

    @Test
    fun testHeader_everyLineTerminatorEndsTheFirstLine() {
        for (terminator in listOf("\n", "\u000B", "\u000C", "\r", "\r\n", "\u0085", "\u2028", "\u2029")) {
            assertEquals(
                fromHeader(8u),
                reported("HostError: Error(Contract, #8)${terminator}trailing text", null),
                "terminator U+${terminator[0].code.toString(16)}"
            )
        }
    }

    @Test
    fun testHeader_whitespaceForms() {
        assertEquals(fromHeader(8u), reported("  HostError:  Error( Contract ,  #8 )  ", null))
        assertEquals(fromHeader(8u), reported("Error(Contract, #8)", null))
        assertEquals(fromHeader(8u), reported("HostError:\u00A0Error(Contract,\u00A0#8)", null))
        assertEquals(fromHeader(8u), reported("\tHostError:\u3000Error(\u2003Contract,\u202F#8\t)", null))
        assertEquals(
            fromEvents(3016u, CONTRACT_ONE),
            reported("Error(Context, InvalidAction)", listOf(contractErrorEvent(3016u)))
        )
    }

    @Test
    fun testHeader_rejectedForms_reportNothing() {
        val qualifying = listOf(contractErrorEvent(3016u))
        assertNull(reported("HostError: Error (Contract, #3200)", qualifying))
        assertNull(reported("HostError: Error (Context, InvalidAction)", qualifying))
        assertNull(reported("HostError: Error(Contract, #8) and more", qualifying))
        assertNull(reported("prefix HostError: Error(Context, InvalidAction)", qualifying))
        assertNull(reported("HostError: Error(Context)", qualifying))
        assertNull(reported("HostError: Error(Contract, #8, #9)", qualifying))
        assertNull(reported("", qualifying))
        assertNull(reported(null, qualifying))
    }

    @Test
    fun testHeader_otherErrorTypes_doNotReadTheEvents() {
        val qualifying = listOf(contractErrorEvent(3016u))
        for (type in listOf("Storage", "Budget", "WasmVm", "Value", "Crypto")) {
            assertNull(reported("HostError: Error($type, InvalidAction)", qualifying), type)
        }
    }

    @Test
    fun testHeader_contractValueOutsideU32_reportsNothingAndReadsNoEvents() {
        val qualifying = listOf(contractErrorEvent(3016u))
        assertEquals(fromHeader(0u), reported("HostError: Error(Contract, #0)", qualifying))
        assertEquals(fromHeader(UInt.MAX_VALUE), reported("HostError: Error(Contract, #4294967295)", qualifying))
        assertNull(reported("HostError: Error(Contract, #4294967296)", qualifying))
        assertNull(reported("HostError: Error(Contract, #-1)", qualifying))
        assertNull(reported("HostError: Error(Contract, InvalidAction)", qualifying))
        assertNull(reported("HostError: Error(Contract, #)", qualifying))
    }

    // ========== Masked header and diagnostic events ==========

    @Test
    fun testMasked_withoutEvents_reportsNothing() {
        assertNull(reported(MASKED_AUTH, null))
        assertNull(reported(MASKED_AUTH, emptyList()))
        assertNull(reported(MASKED_CONTEXT, emptyList()))
    }

    @Test
    fun testSuccessfulSimulation_reportsNothing() {
        assertNull(reported(null, listOf(contractErrorEvent(3016u))))
    }

    @Test
    fun testEvents_thatDoNotReportAContractError_areSkipped() {
        val valid = contractErrorEvent(3016u)
        val notReporting = mapOf(
            "invalid base64" to "%%% not base64 %%%",
            "truncated XDR" to truncated(valid),
            "contract event" to diagnosticEvent(
                topics = listOf(errorTopic, contractCode(3016u)),
                type = ContractEventTypeXdr.CONTRACT
            ),
            "one topic" to diagnosticEvent(topics = listOf(errorTopic)),
            "three topics" to diagnosticEvent(topics = listOf(errorTopic, contractCode(3016u), Scv.toVoid())),
            "string topic" to diagnosticEvent(
                topics = listOf(SCValXdr.Str(SCStringXdr("error")), contractCode(3016u))
            ),
            "other symbol" to diagnosticEvent(topics = listOf(SCValXdr.Sym(SCSymbolXdr("fn_call")), contractCode(3016u))),
            "u32 instead of an error" to diagnosticEvent(
                topics = listOf(errorTopic, SCValXdr.U32(Uint32Xdr(3016u)))
            ),
            "host error code" to diagnosticEvent(
                topics = listOf(
                    errorTopic,
                    SCValXdr.Error(SCErrorXdr.Code(SCErrorTypeXdr.SCE_AUTH, SCErrorCodeXdr.SCEC_INVALID_ACTION))
                )
            ),
            "code only in data" to diagnosticEvent(
                topics = listOf(
                    errorTopic,
                    SCValXdr.Error(SCErrorXdr.Code(SCErrorTypeXdr.SCE_CONTEXT, SCErrorCodeXdr.SCEC_INVALID_ACTION))
                ),
                data = contractCode(3201u)
            )
        )
        for ((name, event) in notReporting) {
            assertNull(reported(MASKED_AUTH, listOf(event)), name)
        }
    }

    @Test
    fun testEvents_entriesThatDoNotDecode_doNotHideALaterReport() {
        val events = listOf(
            "%%%",
            truncated(contractErrorEvent(3201u)),
            contractErrorEvent(3114u, CONTRACT_ONE),
            "AAAA",
            contractErrorEvent(3016u, CONTRACT_TWO)
        )
        assertEquals(fromEvents(3114u, CONTRACT_ONE), reported(MASKED_AUTH, events))
        assertEquals(
            fromEvents(3016u, CONTRACT_TWO),
            reported(MASKED_AUTH, listOf(diagnosticEvent(listOf(errorTopic)), "%%%", contractErrorEvent(3016u, CONTRACT_TWO)))
        )
    }

    @Test
    fun testEvents_inSuccessfulContractCall_isSkipped() {
        val events = listOf(
            contractErrorEvent(3114u, CONTRACT_ONE, inSuccessfulContractCall = true),
            contractErrorEvent(3016u, CONTRACT_TWO)
        )
        assertEquals(fromEvents(3016u, CONTRACT_TWO), reported(MASKED_CONTEXT, events))
    }

    @Test
    fun testEvents_earliestReportWins() {
        assertEquals(
            fromEvents(3114u, CONTRACT_ONE),
            reported(MASKED_AUTH, listOf(contractErrorEvent(3114u, CONTRACT_ONE), contractErrorEvent(3016u, CONTRACT_TWO)))
        )
        assertEquals(
            fromEvents(3016u, CONTRACT_TWO),
            reported(MASKED_AUTH, listOf(contractErrorEvent(3016u, CONTRACT_TWO), contractErrorEvent(3114u, CONTRACT_ONE)))
        )
        val duplicates = listOf(
            contractErrorEvent(3114u, CONTRACT_ONE),
            contractErrorEvent(3016u, CONTRACT_TWO),
            contractErrorEvent(3114u, CONTRACT_ONE),
            contractErrorEvent(3016u, CONTRACT_TWO)
        )
        assertEquals(fromEvents(3114u, CONTRACT_ONE), reported(MASKED_AUTH, duplicates))
    }

    @Test
    fun testEvents_unknownCodeIsNotSkipped() {
        val events = listOf(contractErrorEvent(9999u, CONTRACT_ONE), contractErrorEvent(3016u, CONTRACT_TWO))
        assertEquals(fromEvents(9999u, CONTRACT_ONE), reported(MASKED_AUTH, events))
    }

    @Test
    fun testEvents_u32Bounds_areAccepted() {
        assertEquals(fromEvents(0u, CONTRACT_ONE), reported(MASKED_AUTH, listOf(contractErrorEvent(0u))))
        assertEquals(
            fromEvents(UInt.MAX_VALUE, CONTRACT_ONE),
            reported(MASKED_AUTH, listOf(contractErrorEvent(UInt.MAX_VALUE)))
        )
    }

    @Test
    fun testEvents_withoutContractId_reportWithoutIt() {
        val reported = reported(MASKED_CONTEXT, listOf(contractErrorEvent(3016u, contractId = null)))
        assertEquals(fromEvents(3016u, null), reported)
        assertEquals(
            " (contract error in diagnostic events: Error(Contract, #3016))",
            SimulationFailureMessage.suffix(reported)
        )
    }

    @Test
    fun testSuffix_onlyForEventProvenance() {
        assertEquals("", SimulationFailureMessage.suffix(null))
        assertEquals("", SimulationFailureMessage.suffix(fromHeader(8u)))
        assertEquals(
            " (contract error in diagnostic events: Error(Contract, #4294967295), contract $CONTRACT_TWO)",
            SimulationFailureMessage.suffix(fromEvents(UInt.MAX_VALUE, CONTRACT_TWO))
        )
    }
}
