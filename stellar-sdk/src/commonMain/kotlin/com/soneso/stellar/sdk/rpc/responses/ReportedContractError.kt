package com.soneso.stellar.sdk.rpc.responses

import com.soneso.stellar.sdk.StrKey
import com.soneso.stellar.sdk.xdr.ContractEventBodyXdr
import com.soneso.stellar.sdk.xdr.ContractEventTypeXdr
import com.soneso.stellar.sdk.xdr.DiagnosticEventXdr
import com.soneso.stellar.sdk.xdr.SCErrorXdr
import com.soneso.stellar.sdk.xdr.SCSymbolXdr
import com.soneso.stellar.sdk.xdr.SCValXdr
import com.soneso.stellar.sdk.xdr.fromXdrBase64

/**
 * The contract error that a failed simulation reports.
 *
 * A failed simulation names a contract error in one of two places. The host error header, the
 * first line of [SimulateTransactionResponse.error], can name it as `Error(Contract, #N)`
 * ([Provenance.HOST_ERROR]). When the header is a masked failure instead, `Error(Context, ...)`
 * after a failing constructor or `Error(Auth, ...)` after a failing `__check_auth`, the code is
 * taken from the earliest diagnostic event that reports a contract error
 * ([Provenance.DIAGNOSTIC_EVENTS]).
 *
 * The selection from the diagnostic events is a heuristic: an earlier caught error, several
 * failing contracts or an unrelated rolled-back error can win, and a foreign contract's code can
 * collide with a known error table. Without RPC debug events in the simulation response, a code is
 * available only from an `Error(Contract, #N)` header.
 *
 * @property code The contract error code, an unsigned 32-bit value
 * @property provenance Where the code was found
 * @property contractId The C... strkey of the contract that emitted the diagnostic event; null for
 *   [Provenance.HOST_ERROR] and for an event without a contract ID
 */
data class ReportedContractError(
    val code: UInt,
    val provenance: Provenance,
    val contractId: String?
) {
    /**
     * Where a [ReportedContractError] was found.
     */
    enum class Provenance {
        /** The host error header of the simulation error names `Error(Contract, #N)`. */
        HOST_ERROR,

        /** The earliest diagnostic event that reports a contract error, after a masked header. */
        DIAGNOSTIC_EVENTS
    }
}

/**
 * The host error header: the first line of a simulation error.
 */
internal sealed class HostErrorHeader {
    /** `Error(Contract, #N)` with N in the u32 range. */
    data class Contract(val code: UInt) : HostErrorHeader()

    /** `Error(Context, ...)` or `Error(Auth, ...)`: the host replaced the original error. */
    data object Masked : HostErrorHeader()

    /** Any other error type, or a contract value outside the u32 range. */
    data object Other : HostErrorHeader()

    companion object {
        // Horizontal whitespace: tab and the Unicode space separators. Written out because the
        // regular expression engines of the JVM, JS and Native targets read `\s` differently.
        private const val WS = "[\t \u00A0\u1680\u2000-\u200A\u202F\u205F\u3000]"

        private val HEADER = Regex(
            "$WS*(?:HostError:$WS*)?Error\\($WS*([A-Za-z]+)$WS*,$WS*([^()]*?)$WS*\\)$WS*"
        )

        private val CONTRACT_VALUE = Regex("#[0-9]+")

        private val LINE_BREAKS = charArrayOf(
            '\n', '\u000B', '\u000C', '\r', '\u0085', '\u2028', '\u2029'
        )

        /**
         * Parses the first line of [text]. Returns null when the line is not a host error header.
         */
        fun parse(text: String): HostErrorHeader? {
            val end = text.indexOfAny(LINE_BREAKS)
            val firstLine = if (end < 0) text else text.substring(0, end)
            val match = HEADER.matchEntire(firstLine) ?: return null
            val value = match.groupValues[2]
            return when (match.groupValues[1]) {
                "Contract" -> {
                    val code = if (CONTRACT_VALUE.matches(value)) value.substring(1).toUIntOrNull() else null
                    if (code != null) Contract(code) else Other
                }
                "Context", "Auth" -> Masked
                else -> Other
            }
        }
    }
}

/**
 * Finds the contract error that a failed simulation reports.
 */
internal object SimulationContractErrors {
    private val ERROR_TOPIC = SCValXdr.Sym(SCSymbolXdr("error"))

    /**
     * Returns the contract error reported by a simulation with the given [error] text and
     * base64 diagnostic [events], or null. Never throws.
     *
     * A contract error in the host error header wins. Only after a masked header are the events
     * read, one by one in emission order; an entry that does not decode is skipped. The earliest
     * diagnostic event outside a successful contract call whose topics are exactly the symbol
     * `error` and a contract error code is taken, whatever its code. The text after the first
     * line of [error] is never read.
     */
    fun extract(error: String?, events: List<String>?): ReportedContractError? {
        if (error == null) return null
        return when (val header = HostErrorHeader.parse(error)) {
            is HostErrorHeader.Contract ->
                ReportedContractError(header.code, ReportedContractError.Provenance.HOST_ERROR, null)
            HostErrorHeader.Masked -> events?.let { earliestReportedInEvents(it) }
            HostErrorHeader.Other, null -> null
        }
    }

    private fun earliestReportedInEvents(events: List<String>): ReportedContractError? {
        for (encoded in events) {
            val reported = try {
                reportedBy(DiagnosticEventXdr.fromXdrBase64(encoded))
            } catch (e: Exception) {
                null
            }
            if (reported != null) return reported
        }
        return null
    }

    private fun reportedBy(diagnosticEvent: DiagnosticEventXdr): ReportedContractError? {
        if (diagnosticEvent.inSuccessfulContractCall) return null
        val event = diagnosticEvent.event
        if (event.type != ContractEventTypeXdr.DIAGNOSTIC) return null
        val topics = when (val body = event.body) {
            is ContractEventBodyXdr.V0 -> body.value.topics
        }
        if (topics.size != 2 || topics[0] != ERROR_TOPIC) return null
        val contractCode = (topics[1] as? SCValXdr.Error)?.value as? SCErrorXdr.ContractCode ?: return null
        val contractId = event.contractId?.let { StrKey.encodeContract(it.value.value) }
        return ReportedContractError(
            contractCode.value.value,
            ReportedContractError.Provenance.DIAGNOSTIC_EVENTS,
            contractId
        )
    }
}

/**
 * The text that failed-simulation messages append for a contract error found in the
 * diagnostic events.
 */
internal object SimulationFailureMessage {
    /**
     * Returns ` (contract error in diagnostic events: Error(Contract, #N), contract C...)`, without
     * the contract part when the event has no contract ID, for a [reported] error with
     * [ReportedContractError.Provenance.DIAGNOSTIC_EVENTS]; an empty string otherwise.
     */
    fun suffix(reported: ReportedContractError?): String {
        if (reported == null || reported.provenance != ReportedContractError.Provenance.DIAGNOSTIC_EVENTS) {
            return ""
        }
        val contract = if (reported.contractId != null) ", contract ${reported.contractId}" else ""
        return " (contract error in diagnostic events: Error(Contract, #${reported.code})$contract)"
    }

    /**
     * Returns [prefix], the [hostError] text and the [suffix] for [reported].
     */
    fun compose(prefix: String, hostError: String, reported: ReportedContractError?): String =
        prefix + hostError + suffix(reported)
}

/**
 * An immutable copy of a list of base64 diagnostic events.
 */
internal class EventSnapshot(source: List<String>) : AbstractList<String>() {
    private val items: List<String> = source.toList()

    override val size: Int
        get() = items.size

    override fun get(index: Int): String = items[index]
}
