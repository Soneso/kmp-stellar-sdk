//
//  SACSpecTest.kt
//  Stellar SDK Kotlin Multiplatform
//
//  Copyright (c) 2026 Soneso. All rights reserved.
//

package com.soneso.stellar.sdk.unitTests.contract

import com.soneso.stellar.sdk.Util
import com.soneso.stellar.sdk.contract.SACClient
import com.soneso.stellar.sdk.contract.SACSpec
import com.soneso.stellar.sdk.xdr.SCSpecEntryXdr
import com.soneso.stellar.sdk.xdr.SCSpecFunctionV0Xdr
import com.soneso.stellar.sdk.xdr.SCSpecTypeXdr
import com.soneso.stellar.sdk.xdr.SCSymbolXdr
import com.soneso.stellar.sdk.xdr.XdrWriter
import kotlin.io.encoding.Base64
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * The function names of the pinned Stellar Asset Contract spec, sorted. SACClientTest,
 * ContractClientSacDetectionTest and the SAC integration test compare a client's method names
 * with this list, so a new pin updates the function set here only.
 */
internal val SAC_FUNCTION_NAMES: List<String> = listOf(
    "admin", "allowance", "approve", "authorized", "balance", "burn", "burn_from", "clawback",
    "decimals", "mint", "name", "set_admin", "set_authorized", "symbol", "transfer",
    "transfer_from", "trust"
)

/**
 * Tests for the embedded Stellar Asset Contract spec, [SACSpec], and its strict decoder,
 * [SACClient.specEntries] and `SACClient.decodeSpec`. The expected values are those of the
 * pinned upstream file; a new pin updates them in the same change.
 */
class SACSpecTest {

    private val pinnedCommit = "4f08d14f8b3e09e195f2cc4aab51c7f8a34f52ea"
    private val pinnedSha256 = "a88030febc66645d40b96deba8be16a6eb13c213768b4c1f9cd6278080d74669"

    private val eventNames = listOf(
        "Approve", "Burn", "Clawback", "Mint", "MintWithAmountOnly", "MintWithMuxedBytes",
        "MintWithMuxedString", "SetAdmin", "SetAuthorized", "Transfer", "TransferWithAmountOnly",
        "TransferWithMuxedBytes", "TransferWithMuxedString"
    )

    private suspend fun sha256Hex(bytes: ByteArray): String = Util.bytesToHex(Util.hash(bytes))

    private fun encode(entry: SCSpecEntryXdr): ByteArray {
        val writer = XdrWriter()
        entry.encode(writer)
        return writer.toByteArray()
    }

    private fun function(entries: List<SCSpecEntryXdr>, name: String): SCSpecFunctionV0Xdr =
        entries.filterIsInstance<SCSpecEntryXdr.FunctionV0>().single { it.value.name.value == name }.value

    @Test
    fun testTheConstantsHoldThePinnedFile() = runTest {
        assertEquals(pinnedCommit, SACSpec.UPSTREAM_COMMIT)
        assertEquals(pinnedSha256, SACSpec.SHA256)
        assertEquals(12_816, SACSpec.XDR_BASE64.length)

        val bytes = Base64.Default.decode(SACSpec.XDR_BASE64)
        assertEquals(9_612, bytes.size)
        assertEquals(SACSpec.SHA256, sha256Hex(bytes))
    }

    @Test
    fun testSpecEntriesHoldTheFunctionsAndEvents() = runTest {
        val entries = SACClient.specEntries()

        assertEquals(30, entries.size)
        entries.forEach { assertTrue(it is SCSpecEntryXdr.FunctionV0 || it is SCSpecEntryXdr.EventV0, "$it") }
        assertEquals(
            SAC_FUNCTION_NAMES,
            entries.filterIsInstance<SCSpecEntryXdr.FunctionV0>().map { it.value.name.value }.sorted()
        )
        assertEquals(
            eventNames,
            entries.filterIsInstance<SCSpecEntryXdr.EventV0>().map { it.value.name }.sorted()
        )
    }

    @Test
    fun testTheEntriesReencodeToTheDecodedBytes() = runTest {
        val bytes = Base64.Default.decode(SACSpec.XDR_BASE64)

        val reencoded = SACClient.specEntries().map { encode(it) }.reduce { all, next -> all + next }

        assertTrue(bytes.contentEquals(reencoded))
    }

    @Test
    fun testTheFunctionsTheClientCallsHaveTheirSignatures() = runTest {
        val entries = SACClient.specEntries()

        fun inputs(name: String) = function(entries, name).inputs.map { it.name to it.type.discriminant }
        fun outputs(name: String) = function(entries, name).outputs.map { it.discriminant }

        assertEquals(listOf("id" to SCSpecTypeXdr.SC_SPEC_TYPE_ADDRESS), inputs("balance"))
        assertEquals(listOf(SCSpecTypeXdr.SC_SPEC_TYPE_I128), outputs("balance"))
        assertEquals(emptyList(), inputs("decimals"))
        assertEquals(listOf(SCSpecTypeXdr.SC_SPEC_TYPE_U32), outputs("decimals"))
        assertEquals(emptyList(), inputs("name"))
        assertEquals(listOf(SCSpecTypeXdr.SC_SPEC_TYPE_STRING), outputs("name"))
        assertEquals(emptyList(), inputs("symbol"))
        assertEquals(listOf(SCSpecTypeXdr.SC_SPEC_TYPE_STRING), outputs("symbol"))
        assertEquals(
            listOf(
                "from" to SCSpecTypeXdr.SC_SPEC_TYPE_ADDRESS,
                "to" to SCSpecTypeXdr.SC_SPEC_TYPE_MUXED_ADDRESS,
                "amount" to SCSpecTypeXdr.SC_SPEC_TYPE_I128
            ),
            inputs("transfer")
        )
        assertEquals(emptyList(), outputs("transfer"))
    }

    @Test
    fun testATruncatedConstantFailsTheDigestCheck() = runTest {
        // The constant without its last four characters decodes to the first 9,609 bytes.
        val truncated = SACSpec.XDR_BASE64.dropLast(4)

        val failure = assertFailsWith<IllegalStateException> {
            SACClient.decodeSpec(truncated, SACSpec.SHA256)
        }

        assertEquals(
            "The embedded SAC spec has sha256 0c15c7a293dba569d8a304726d3571816a6f7dda83382874004d7977b31b2460, " +
                "expected a88030febc66645d40b96deba8be16a6eb13c213768b4c1f9cd6278080d74669",
            failure.message
        )
    }

    @Test
    fun testInvalidBase64Fails() = runTest {
        val failure = assertFailsWith<IllegalStateException> {
            SACClient.decodeSpec("not base64!", SACSpec.SHA256)
        }

        assertEquals("The embedded SAC spec is not valid base64", failure.message)
        assertIs<IllegalArgumentException>(failure.cause)
    }

    @Test
    fun testBytesAfterTheLastEntryFailAtTheirOffset() = runTest {
        val bytes = Base64.Default.decode(SACSpec.XDR_BASE64) + ByteArray(4) { 0xFF.toByte() }

        val failure = assertFailsWith<IllegalStateException> {
            SACClient.decodeSpec(Base64.Default.encode(bytes), sha256Hex(bytes))
        }

        assertEquals(
            "The embedded SAC spec does not decode at byte offset 9612: Unknown SCSpecEntryKindXdr value: -1",
            failure.message
        )
        assertEquals("Unknown SCSpecEntryKindXdr value: -1", assertIs<IllegalArgumentException>(failure.cause).message)
    }

    @Test
    fun testInvalidUtf8InEmbeddedDocFailsReencodingAtEntryOffset() = runTest {
        val entries = SACClient.specEntries()
        val index = entries.indexOfFirst { it is SCSpecEntryXdr.FunctionV0 && it.value.name.value == "balance" }
        assertTrue(index > 0)
        assertTrue(assertIs<SCSpecEntryXdr.FunctionV0>(entries[index]).value.doc.isNotEmpty())
        val entryOffset = entries.take(index).sumOf { encode(it).size }
        val bytes = Base64.Default.decode(SACSpec.XDR_BASE64)
        // The doc bytes follow the entry discriminant and the doc length.
        bytes[entryOffset + 8] = 0xFF.toByte()

        val failure = assertFailsWith<IllegalStateException> {
            SACClient.decodeSpec(Base64.Default.encode(bytes), sha256Hex(bytes))
        }

        assertEquals(
            "The embedded SAC spec does not decode at byte offset $entryOffset: the entry re-encodes to different bytes",
            failure.message
        )
    }

    @Test
    fun testANonZeroPaddingByteFailsDecoding() = runTest {
        val bytes = encode(
            SCSpecEntryXdr.FunctionV0(
                SCSpecFunctionV0Xdr(doc = "", name = SCSymbolXdr("abc"), inputs = emptyList(), outputs = emptyList())
            )
        )
        // Discriminant, doc length, name length, then the three name bytes and one padding byte.
        val paddingOffset = 4 + 4 + 4 + 3
        assertEquals(0, bytes[paddingOffset].toInt())
        bytes[paddingOffset] = 1

        val failure = assertFailsWith<IllegalStateException> {
            SACClient.decodeSpec(Base64.Default.encode(bytes), sha256Hex(bytes))
        }

        assertEquals(
            "The embedded SAC spec does not decode at byte offset 0: XDR padding at offset 15 must be zero, got 1",
            failure.message
        )
    }
}
