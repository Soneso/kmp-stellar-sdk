package com.soneso.stellar.sdk.unitTests.sep.sep51

import com.soneso.stellar.sdk.xdr.AccountEntryExtensionV2ExtXdr
import com.soneso.stellar.sdk.xdr.AccountEntryExtensionV2Xdr
import com.soneso.stellar.sdk.xdr.AccountEntryExtensionV3Xdr
import com.soneso.stellar.sdk.xdr.BinaryFuseFilterTypeXdr
import com.soneso.stellar.sdk.xdr.ClaimableBalanceIDTypeXdr
import com.soneso.stellar.sdk.xdr.ContractCostTypeXdr
import com.soneso.stellar.sdk.xdr.ContractEventBodyXdr
import com.soneso.stellar.sdk.xdr.ContractEventTypeXdr
import com.soneso.stellar.sdk.xdr.ContractEventV0Xdr
import com.soneso.stellar.sdk.xdr.ContractEventXdr
import com.soneso.stellar.sdk.xdr.EnvelopeTypeXdr
import com.soneso.stellar.sdk.xdr.ExtensionPointXdr
import com.soneso.stellar.sdk.xdr.OperationResultCodeXdr
import com.soneso.stellar.sdk.xdr.PublicKeyTypeXdr
import com.soneso.stellar.sdk.xdr.SCSpecEventParamLocationV0Xdr
import com.soneso.stellar.sdk.xdr.SCSpecEventParamV0Xdr
import com.soneso.stellar.sdk.xdr.SCSpecFunctionInputV0Xdr
import com.soneso.stellar.sdk.xdr.SCSpecTypeDefXdr
import com.soneso.stellar.sdk.xdr.SCSpecTypeXdr
import com.soneso.stellar.sdk.xdr.SCSpecUDTStructFieldV0Xdr
import com.soneso.stellar.sdk.xdr.SCSpecUDTUnionCaseTupleV0Xdr
import com.soneso.stellar.sdk.xdr.SCValTypeXdr
import com.soneso.stellar.sdk.xdr.SCValXdr
import com.soneso.stellar.sdk.xdr.SerializedBinaryFuseFilterXdr
import com.soneso.stellar.sdk.xdr.ShortHashSeedXdr
import com.soneso.stellar.sdk.xdr.SponsorshipDescriptorXdr
import com.soneso.stellar.sdk.xdr.TimePointXdr
import com.soneso.stellar.sdk.xdr.TransactionResultCodeXdr
import com.soneso.stellar.sdk.xdr.Uint32Xdr
import com.soneso.stellar.sdk.xdr.Uint64Xdr
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Pins the JSON names SEP-0051 derives from the XDR identifiers, at the points where the
 * derivation is not obvious from the identifier alone.
 *
 * An enum member and a void union arm are named by the identifier converted to snake case with
 * the prefix shared by the whole enum removed; a struct key is the member identifier converted
 * to snake case. Both conversions treat a case boundary as a word boundary, the shared prefix is
 * cut back only as far as its last underscore, an enum with a single member has no shared prefix
 * to remove, and a name may never begin with a digit.
 *
 * Every expectation here is asserted from a constructed value rather than from a recorded
 * document, so it holds the derivation itself rather than any one rendering of it. The names
 * below are the XDR-JSON forms of the identifiers they stand beside.
 */
class Sep51NameDerivationTest {

    private fun text(element: JsonElement): String = (element as JsonPrimitive).content

    private fun keys(element: JsonElement): Set<String> = (element as JsonObject).keys

    /**
     * Holds a type carrying an XDR field named `type` to the key `type`, and to accepting the
     * escaped spelling `type_` on input without ever emitting it.
     */
    private fun assertTypeFieldKey(emitted: JsonElement, reEmit: (JsonElement) -> JsonElement) {
        val value = emitted as JsonObject
        assertTrue(value.containsKey("type"), "emitted keys ${value.keys}")
        assertFalse(value.containsKey("type_"), "emitted keys ${value.keys}")

        val aliased = JsonObject(value.mapKeys { if (it.key == "type") "type_" else it.key })
        assertEquals(value, reEmit(aliased))
    }

    // -------------------------------------------------------------------------------------
    // A shared prefix is cut back only as far as its last underscore
    // -------------------------------------------------------------------------------------

    @Test
    fun anOperationResultCodeKeepsAPrefixHoldingNoUnderscore() {
        assertEquals("op_inner", text(OperationResultCodeXdr.opINNER.toXdrJsonElement()))
        assertEquals("op_bad_auth", text(OperationResultCodeXdr.opBAD_AUTH.toXdrJsonElement()))
    }

    @Test
    fun anOperationResultCodeReadsThoseNamesBack() {
        assertEquals(
            OperationResultCodeXdr.opINNER,
            OperationResultCodeXdr.fromXdrJsonElement(JsonPrimitive("op_inner"))
        )
        assertEquals(
            OperationResultCodeXdr.opBAD_AUTH,
            OperationResultCodeXdr.fromXdrJsonElement(JsonPrimitive("op_bad_auth"))
        )
    }

    @Test
    fun aTransactionResultCodeKeepsAPrefixHoldingNoUnderscore() {
        assertEquals("tx_success", text(TransactionResultCodeXdr.txSUCCESS.toXdrJsonElement()))
        assertEquals("tx_failed", text(TransactionResultCodeXdr.txFAILED.toXdrJsonElement()))
    }

    @Test
    fun aTransactionResultCodeReadsThoseNamesBack() {
        assertEquals(
            TransactionResultCodeXdr.txSUCCESS,
            TransactionResultCodeXdr.fromXdrJsonElement(JsonPrimitive("tx_success"))
        )
        assertEquals(
            TransactionResultCodeXdr.txFAILED,
            TransactionResultCodeXdr.fromXdrJsonElement(JsonPrimitive("tx_failed"))
        )
    }

    // -------------------------------------------------------------------------------------
    // An enum with one member has no shared prefix to remove
    // -------------------------------------------------------------------------------------

    @Test
    fun aSingleMemberEnumKeepsItsWholeMemberName() {
        assertEquals(
            "public_key_type_ed25519",
            text(PublicKeyTypeXdr.PUBLIC_KEY_TYPE_ED25519.toXdrJsonElement())
        )
        assertEquals(1, PublicKeyTypeXdr.entries.size)
    }

    @Test
    fun theClaimableBalanceIdTypeKeepsItsWholeMemberName() {
        assertEquals(
            "claimable_balance_id_type_v0",
            text(ClaimableBalanceIDTypeXdr.CLAIMABLE_BALANCE_ID_TYPE_V0.toXdrJsonElement())
        )
        assertEquals(1, ClaimableBalanceIDTypeXdr.entries.size)
    }

    @Test
    fun singleMemberEnumsReadTheirWholeMemberNameBack() {
        assertEquals(
            PublicKeyTypeXdr.PUBLIC_KEY_TYPE_ED25519,
            PublicKeyTypeXdr.fromXdrJsonElement(JsonPrimitive("public_key_type_ed25519"))
        )
        assertEquals(
            ClaimableBalanceIDTypeXdr.CLAIMABLE_BALANCE_ID_TYPE_V0,
            ClaimableBalanceIDTypeXdr.fromXdrJsonElement(
                JsonPrimitive("claimable_balance_id_type_v0")
            )
        )
    }

    // -------------------------------------------------------------------------------------
    // A case boundary is a word boundary
    // -------------------------------------------------------------------------------------

    @Test
    fun aCaseBoundaryStartsANewWordInAnEnumMemberName() {
        assertEquals("wasm_insn_exec", text(ContractCostTypeXdr.WasmInsnExec.toXdrJsonElement()))
        assertEquals(
            ContractCostTypeXdr.WasmInsnExec,
            ContractCostTypeXdr.fromXdrJsonElement(JsonPrimitive("wasm_insn_exec"))
        )
    }

    /**
     * The member identifier `signerSponsoringIDs` breaks at every case boundary, so the run of
     * capitals becomes its own words and the key reads `signer_sponsoring_i_ds`.
     */
    @Test
    fun aCaseBoundaryStartsANewWordInAStructKey() {
        val value = AccountEntryExtensionV2Xdr(
            Uint32Xdr(1u),
            Uint32Xdr(2u),
            listOf(SponsorshipDescriptorXdr(null)),
            AccountEntryExtensionV2ExtXdr.Void
        )
        assertEquals(
            setOf("num_sponsored", "num_sponsoring", "signer_sponsoring_i_ds", "ext"),
            keys(value.toXdrJsonElement())
        )
    }

    @Test
    fun aStructKeyBrokenAtACaseBoundaryIsReadBack() {
        val document = "{\"num_sponsored\":1,\"num_sponsoring\":2," +
            "\"signer_sponsoring_i_ds\":[null],\"ext\":\"v0\"}"
        val decoded = AccountEntryExtensionV2Xdr.fromXdrJson(document)
        assertEquals(1u, decoded.numSponsored.value)
        assertEquals(1, decoded.signerSponsoringIDs.size)
        assertEquals(document, decoded.toXdrJson())
    }

    // -------------------------------------------------------------------------------------
    // A stripped name may not begin with a digit
    // -------------------------------------------------------------------------------------

    /**
     * Stripping the shared prefix `BINARY_FUSE_FILTER_` would leave each member starting with a
     * digit, so the first character of the prefix is kept.
     */
    @Test
    fun aStrippedNameKeepsEnoughOfThePrefixToAvoidALeadingDigit() {
        assertEquals(
            listOf("b8_bit", "b16_bit", "b32_bit"),
            BinaryFuseFilterTypeXdr.entries.map { text(it.toXdrJsonElement()) }
        )
    }

    @Test
    fun theBinaryFuseFilterTypeReadsThoseNamesBack() {
        assertEquals(
            BinaryFuseFilterTypeXdr.entries.toList(),
            listOf("b8_bit", "b16_bit", "b32_bit").map {
                BinaryFuseFilterTypeXdr.fromXdrJsonElement(JsonPrimitive(it))
            }
        )
    }

    // -------------------------------------------------------------------------------------
    // The ordinary stripped case
    // -------------------------------------------------------------------------------------

    @Test
    fun anEnumDropsThePrefixSharedByAllOfItsMembers() {
        assertEquals("u32", text(SCValTypeXdr.SCV_U32.toXdrJsonElement()))
        assertEquals("tx_v0", text(EnvelopeTypeXdr.ENVELOPE_TYPE_TX_V0.toXdrJsonElement()))
    }

    @Test
    fun anEnumReadsItsStrippedMemberNamesBack() {
        assertEquals(SCValTypeXdr.SCV_U32, SCValTypeXdr.fromXdrJsonElement(JsonPrimitive("u32")))
        assertEquals(
            EnvelopeTypeXdr.ENVELOPE_TYPE_TX_V0,
            EnvelopeTypeXdr.fromXdrJsonElement(JsonPrimitive("tx_v0"))
        )
    }

    // -------------------------------------------------------------------------------------
    // The six types carrying an XDR field named `type`
    // -------------------------------------------------------------------------------------

    @Test
    fun aContractEventKeysItsTypeFieldAsType() {
        val value = ContractEventXdr(
            ExtensionPointXdr.Void,
            null,
            ContractEventTypeXdr.CONTRACT,
            ContractEventBodyXdr.V0(
                ContractEventV0Xdr(emptyList(), SCValXdr.Void(SCValTypeXdr.SCV_VOID))
            )
        )
        assertTypeFieldKey(value.toXdrJsonElement()) {
            ContractEventXdr.fromXdrJsonElement(it).toXdrJsonElement()
        }
    }

    @Test
    fun anEventParameterKeysItsTypeFieldAsType() {
        val value = SCSpecEventParamV0Xdr(
            "",
            "amount",
            U32_TYPE_DEF,
            SCSpecEventParamLocationV0Xdr.SC_SPEC_EVENT_PARAM_LOCATION_DATA
        )
        assertTypeFieldKey(value.toXdrJsonElement()) {
            SCSpecEventParamV0Xdr.fromXdrJsonElement(it).toXdrJsonElement()
        }
    }

    @Test
    fun aFunctionInputKeysItsTypeFieldAsType() {
        val value = SCSpecFunctionInputV0Xdr("", "amount", U32_TYPE_DEF)
        assertTypeFieldKey(value.toXdrJsonElement()) {
            SCSpecFunctionInputV0Xdr.fromXdrJsonElement(it).toXdrJsonElement()
        }
    }

    @Test
    fun aStructFieldKeysItsTypeFieldAsType() {
        val value = SCSpecUDTStructFieldV0Xdr("", "amount", U32_TYPE_DEF)
        assertTypeFieldKey(value.toXdrJsonElement()) {
            SCSpecUDTStructFieldV0Xdr.fromXdrJsonElement(it).toXdrJsonElement()
        }
    }

    @Test
    fun aUnionCaseTupleKeysItsTypeFieldAsType() {
        val value = SCSpecUDTUnionCaseTupleV0Xdr("", "case", listOf(U32_TYPE_DEF))
        assertTypeFieldKey(value.toXdrJsonElement()) {
            SCSpecUDTUnionCaseTupleV0Xdr.fromXdrJsonElement(it).toXdrJsonElement()
        }
    }

    @Test
    fun aSerializedBinaryFuseFilterKeysItsTypeFieldAsType() {
        val value = SerializedBinaryFuseFilterXdr(
            BinaryFuseFilterTypeXdr.BINARY_FUSE_FILTER_8_BIT,
            ShortHashSeedXdr(ByteArray(16)),
            ShortHashSeedXdr(ByteArray(16) { 1 }),
            Uint32Xdr(1u),
            Uint32Xdr(2u),
            Uint32Xdr(3u),
            Uint32Xdr(4u),
            Uint32Xdr(5u),
            ByteArray(0)
        )
        assertTypeFieldKey(value.toXdrJsonElement()) {
            SerializedBinaryFuseFilterXdr.fromXdrJsonElement(it).toXdrJsonElement()
        }
    }

    // -------------------------------------------------------------------------------------
    // Integer-cased unions
    // -------------------------------------------------------------------------------------

    @Test
    fun anIntegerCasedUnionNamesItsVoidArmByItsCaseNumber() {
        assertEquals("v0", text(ExtensionPointXdr.Void.toXdrJsonElement()))
        assertEquals("v0", text(AccountEntryExtensionV2ExtXdr.Void.toXdrJsonElement()))
        assertEquals(
            AccountEntryExtensionV2ExtXdr.Void,
            AccountEntryExtensionV2ExtXdr.fromXdrJsonElement(JsonPrimitive("v0"))
        )
    }

    @Test
    fun anIntegerCasedUnionNamesItsValueArmByItsCaseNumber() {
        val value = AccountEntryExtensionV2ExtXdr.V3(
            AccountEntryExtensionV3Xdr(
                ExtensionPointXdr.Void,
                Uint32Xdr(7u),
                TimePointXdr(Uint64Xdr(9uL))
            )
        )
        assertEquals(setOf("v3"), keys(value.toXdrJsonElement()))
        assertEquals(
            "{\"v3\":{\"ext\":\"v0\",\"seq_ledger\":7,\"seq_time\":\"9\"}}",
            value.toXdrJson()
        )
        assertEquals(
            value,
            AccountEntryExtensionV2ExtXdr.fromXdrJsonElement(value.toXdrJsonElement())
        )
    }

    private companion object {
        /** A void arm of the type definition union, used where only the key under it matters. */
        val U32_TYPE_DEF: SCSpecTypeDefXdr = SCSpecTypeDefXdr.Void(SCSpecTypeXdr.SC_SPEC_TYPE_U32)
    }
}
