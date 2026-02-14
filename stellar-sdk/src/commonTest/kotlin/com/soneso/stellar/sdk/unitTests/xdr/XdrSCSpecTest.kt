package com.soneso.stellar.sdk.unitTests.xdr

import com.soneso.stellar.sdk.xdr.*
import com.soneso.stellar.sdk.unitTests.xdr.XdrTestHelpers.assertXdrRoundTrip
import com.soneso.stellar.sdk.unitTests.xdr.XdrTestHelpers.uint32
import kotlin.test.Test

class XdrSCSpecTest {

    // ========== Enum types ==========

    @Test
    fun testSCSpecEntryKindEnum() {
        for (e in SCSpecEntryKindXdr.entries) {
            assertXdrRoundTrip(e, { v, w -> v.encode(w) }, { r -> SCSpecEntryKindXdr.decode(r) })
        }
    }

    @Test
    fun testSCSpecTypeEnum() {
        for (e in SCSpecTypeXdr.entries) {
            assertXdrRoundTrip(e, { v, w -> v.encode(w) }, { r -> SCSpecTypeXdr.decode(r) })
        }
    }

    @Test
    fun testSCSpecUDTUnionCaseV0KindEnum() {
        for (e in SCSpecUDTUnionCaseV0KindXdr.entries) {
            assertXdrRoundTrip(e, { v, w -> v.encode(w) }, { r -> SCSpecUDTUnionCaseV0KindXdr.decode(r) })
        }
    }

    @Test
    fun testSCSpecEventParamLocationV0Enum() {
        for (e in SCSpecEventParamLocationV0Xdr.entries) {
            assertXdrRoundTrip(e, { v, w -> v.encode(w) }, { r -> SCSpecEventParamLocationV0Xdr.decode(r) })
        }
    }

    @Test
    fun testSCSpecEventDataFormatEnum() {
        for (e in SCSpecEventDataFormatXdr.entries) {
            assertXdrRoundTrip(e, { v, w -> v.encode(w) }, { r -> SCSpecEventDataFormatXdr.decode(r) })
        }
    }

    // ========== SCSpecTypeDef variants ==========

    private fun voidTypeDef() = SCSpecTypeDefXdr.Void(SCSpecTypeXdr.SC_SPEC_TYPE_BOOL)

    @Test
    fun testSCSpecTypeDefVoidVariants() {
        val voidTypes = listOf(
            SCSpecTypeXdr.SC_SPEC_TYPE_VAL,
            SCSpecTypeXdr.SC_SPEC_TYPE_BOOL,
            SCSpecTypeXdr.SC_SPEC_TYPE_VOID,
            SCSpecTypeXdr.SC_SPEC_TYPE_ERROR,
            SCSpecTypeXdr.SC_SPEC_TYPE_U32,
            SCSpecTypeXdr.SC_SPEC_TYPE_I32,
            SCSpecTypeXdr.SC_SPEC_TYPE_U64,
            SCSpecTypeXdr.SC_SPEC_TYPE_I64,
            SCSpecTypeXdr.SC_SPEC_TYPE_TIMEPOINT,
            SCSpecTypeXdr.SC_SPEC_TYPE_DURATION,
            SCSpecTypeXdr.SC_SPEC_TYPE_U128,
            SCSpecTypeXdr.SC_SPEC_TYPE_I128,
            SCSpecTypeXdr.SC_SPEC_TYPE_U256,
            SCSpecTypeXdr.SC_SPEC_TYPE_I256,
            SCSpecTypeXdr.SC_SPEC_TYPE_BYTES,
            SCSpecTypeXdr.SC_SPEC_TYPE_STRING,
            SCSpecTypeXdr.SC_SPEC_TYPE_SYMBOL,
            SCSpecTypeXdr.SC_SPEC_TYPE_ADDRESS,
            SCSpecTypeXdr.SC_SPEC_TYPE_MUXED_ADDRESS
        )
        for (t in voidTypes) {
            val def = SCSpecTypeDefXdr.Void(t)
            assertXdrRoundTrip(def, { v, w -> v.encode(w) }, { r -> SCSpecTypeDefXdr.decode(r) })
        }
    }

    @Test
    fun testSCSpecTypeDefOption() {
        val def = SCSpecTypeDefXdr.Option(
            SCSpecTypeOptionXdr(SCSpecTypeDefXdr.Void(SCSpecTypeXdr.SC_SPEC_TYPE_U32))
        )
        assertXdrRoundTrip(def, { v, w -> v.encode(w) }, { r -> SCSpecTypeDefXdr.decode(r) })
    }

    @Test
    fun testSCSpecTypeDefResult() {
        val def = SCSpecTypeDefXdr.Result(
            SCSpecTypeResultXdr(
                okType = SCSpecTypeDefXdr.Void(SCSpecTypeXdr.SC_SPEC_TYPE_BOOL),
                errorType = SCSpecTypeDefXdr.Void(SCSpecTypeXdr.SC_SPEC_TYPE_ERROR)
            )
        )
        assertXdrRoundTrip(def, { v, w -> v.encode(w) }, { r -> SCSpecTypeDefXdr.decode(r) })
    }

    @Test
    fun testSCSpecTypeDefVec() {
        val def = SCSpecTypeDefXdr.Vec(
            SCSpecTypeVecXdr(SCSpecTypeDefXdr.Void(SCSpecTypeXdr.SC_SPEC_TYPE_U64))
        )
        assertXdrRoundTrip(def, { v, w -> v.encode(w) }, { r -> SCSpecTypeDefXdr.decode(r) })
    }

    @Test
    fun testSCSpecTypeDefMap() {
        val def = SCSpecTypeDefXdr.Map(
            SCSpecTypeMapXdr(
                keyType = SCSpecTypeDefXdr.Void(SCSpecTypeXdr.SC_SPEC_TYPE_SYMBOL),
                valueType = SCSpecTypeDefXdr.Void(SCSpecTypeXdr.SC_SPEC_TYPE_U128)
            )
        )
        assertXdrRoundTrip(def, { v, w -> v.encode(w) }, { r -> SCSpecTypeDefXdr.decode(r) })
    }

    @Test
    fun testSCSpecTypeDefTuple() {
        val def = SCSpecTypeDefXdr.Tuple(
            SCSpecTypeTupleXdr(listOf(
                SCSpecTypeDefXdr.Void(SCSpecTypeXdr.SC_SPEC_TYPE_U32),
                SCSpecTypeDefXdr.Void(SCSpecTypeXdr.SC_SPEC_TYPE_BOOL)
            ))
        )
        assertXdrRoundTrip(def, { v, w -> v.encode(w) }, { r -> SCSpecTypeDefXdr.decode(r) })
    }

    @Test
    fun testSCSpecTypeDefBytesN() {
        val def = SCSpecTypeDefXdr.BytesN(SCSpecTypeBytesNXdr(uint32(32u)))
        assertXdrRoundTrip(def, { v, w -> v.encode(w) }, { r -> SCSpecTypeDefXdr.decode(r) })
    }

    @Test
    fun testSCSpecTypeDefUdt() {
        val def = SCSpecTypeDefXdr.Udt(SCSpecTypeUDTXdr("MyStruct"))
        assertXdrRoundTrip(def, { v, w -> v.encode(w) }, { r -> SCSpecTypeDefXdr.decode(r) })
    }

    // ========== SCSpec sub-types ==========

    @Test
    fun testSCSpecTypeOption() {
        val opt = SCSpecTypeOptionXdr(SCSpecTypeDefXdr.Void(SCSpecTypeXdr.SC_SPEC_TYPE_I32))
        assertXdrRoundTrip(opt, { v, w -> v.encode(w) }, { r -> SCSpecTypeOptionXdr.decode(r) })
    }

    @Test
    fun testSCSpecTypeResult() {
        val res = SCSpecTypeResultXdr(
            okType = SCSpecTypeDefXdr.Void(SCSpecTypeXdr.SC_SPEC_TYPE_U64),
            errorType = SCSpecTypeDefXdr.Void(SCSpecTypeXdr.SC_SPEC_TYPE_ERROR)
        )
        assertXdrRoundTrip(res, { v, w -> v.encode(w) }, { r -> SCSpecTypeResultXdr.decode(r) })
    }

    @Test
    fun testSCSpecTypeVec() {
        val vec = SCSpecTypeVecXdr(SCSpecTypeDefXdr.Void(SCSpecTypeXdr.SC_SPEC_TYPE_BYTES))
        assertXdrRoundTrip(vec, { v, w -> v.encode(w) }, { r -> SCSpecTypeVecXdr.decode(r) })
    }

    @Test
    fun testSCSpecTypeMap() {
        val map = SCSpecTypeMapXdr(
            keyType = SCSpecTypeDefXdr.Void(SCSpecTypeXdr.SC_SPEC_TYPE_ADDRESS),
            valueType = SCSpecTypeDefXdr.Void(SCSpecTypeXdr.SC_SPEC_TYPE_U128)
        )
        assertXdrRoundTrip(map, { v, w -> v.encode(w) }, { r -> SCSpecTypeMapXdr.decode(r) })
    }

    @Test
    fun testSCSpecTypeTuple() {
        val tuple = SCSpecTypeTupleXdr(listOf(
            SCSpecTypeDefXdr.Void(SCSpecTypeXdr.SC_SPEC_TYPE_BOOL),
            SCSpecTypeDefXdr.Void(SCSpecTypeXdr.SC_SPEC_TYPE_I64)
        ))
        assertXdrRoundTrip(tuple, { v, w -> v.encode(w) }, { r -> SCSpecTypeTupleXdr.decode(r) })
    }

    @Test
    fun testSCSpecTypeBytesN() {
        val bn = SCSpecTypeBytesNXdr(uint32(64u))
        assertXdrRoundTrip(bn, { v, w -> v.encode(w) }, { r -> SCSpecTypeBytesNXdr.decode(r) })
    }

    @Test
    fun testSCSpecTypeUDT() {
        val udt = SCSpecTypeUDTXdr("TokenInfo")
        assertXdrRoundTrip(udt, { v, w -> v.encode(w) }, { r -> SCSpecTypeUDTXdr.decode(r) })
    }

    // ========== Function input / Function ==========

    @Test
    fun testSCSpecFunctionInputV0() {
        val input = SCSpecFunctionInputV0Xdr(
            doc = "Amount to transfer",
            name = "amount",
            type = SCSpecTypeDefXdr.Void(SCSpecTypeXdr.SC_SPEC_TYPE_I128)
        )
        assertXdrRoundTrip(input, { v, w -> v.encode(w) }, { r -> SCSpecFunctionInputV0Xdr.decode(r) })
    }

    @Test
    fun testSCSpecFunctionV0() {
        val fn = SCSpecFunctionV0Xdr(
            doc = "Transfer tokens",
            name = SCSymbolXdr("transfer"),
            inputs = listOf(
                SCSpecFunctionInputV0Xdr("From address", "from", SCSpecTypeDefXdr.Void(SCSpecTypeXdr.SC_SPEC_TYPE_ADDRESS)),
                SCSpecFunctionInputV0Xdr("To address", "to", SCSpecTypeDefXdr.Void(SCSpecTypeXdr.SC_SPEC_TYPE_ADDRESS)),
                SCSpecFunctionInputV0Xdr("Amount", "amount", SCSpecTypeDefXdr.Void(SCSpecTypeXdr.SC_SPEC_TYPE_I128))
            ),
            outputs = listOf(SCSpecTypeDefXdr.Void(SCSpecTypeXdr.SC_SPEC_TYPE_VOID))
        )
        assertXdrRoundTrip(fn, { v, w -> v.encode(w) }, { r -> SCSpecFunctionV0Xdr.decode(r) })
    }

    @Test
    fun testSCSpecFunctionV0NoInputs() {
        val fn = SCSpecFunctionV0Xdr(
            doc = "",
            name = SCSymbolXdr("balance"),
            inputs = emptyList(),
            outputs = listOf(SCSpecTypeDefXdr.Void(SCSpecTypeXdr.SC_SPEC_TYPE_I128))
        )
        assertXdrRoundTrip(fn, { v, w -> v.encode(w) }, { r -> SCSpecFunctionV0Xdr.decode(r) })
    }

    // ========== Struct ==========

    @Test
    fun testSCSpecUDTStructFieldV0() {
        val field = SCSpecUDTStructFieldV0Xdr(
            doc = "User balance",
            name = "balance",
            type = SCSpecTypeDefXdr.Void(SCSpecTypeXdr.SC_SPEC_TYPE_I128)
        )
        assertXdrRoundTrip(field, { v, w -> v.encode(w) }, { r -> SCSpecUDTStructFieldV0Xdr.decode(r) })
    }

    @Test
    fun testSCSpecUDTStructV0() {
        val struct = SCSpecUDTStructV0Xdr(
            doc = "Token metadata",
            lib = "",
            name = "TokenMeta",
            fields = listOf(
                SCSpecUDTStructFieldV0Xdr("Name", "name", SCSpecTypeDefXdr.Void(SCSpecTypeXdr.SC_SPEC_TYPE_STRING)),
                SCSpecUDTStructFieldV0Xdr("Symbol", "symbol", SCSpecTypeDefXdr.Void(SCSpecTypeXdr.SC_SPEC_TYPE_SYMBOL))
            )
        )
        assertXdrRoundTrip(struct, { v, w -> v.encode(w) }, { r -> SCSpecUDTStructV0Xdr.decode(r) })
    }

    // ========== Union ==========

    @Test
    fun testSCSpecUDTUnionCaseVoidV0() {
        val vc = SCSpecUDTUnionCaseVoidV0Xdr(doc = "None variant", name = "None")
        assertXdrRoundTrip(vc, { v, w -> v.encode(w) }, { r -> SCSpecUDTUnionCaseVoidV0Xdr.decode(r) })
    }

    @Test
    fun testSCSpecUDTUnionCaseTupleV0() {
        val tc = SCSpecUDTUnionCaseTupleV0Xdr(
            doc = "Some variant",
            name = "Some",
            type = listOf(SCSpecTypeDefXdr.Void(SCSpecTypeXdr.SC_SPEC_TYPE_U32))
        )
        assertXdrRoundTrip(tc, { v, w -> v.encode(w) }, { r -> SCSpecUDTUnionCaseTupleV0Xdr.decode(r) })
    }

    @Test
    fun testSCSpecUDTUnionCaseV0Void() {
        val c = SCSpecUDTUnionCaseV0Xdr.VoidCase(
            SCSpecUDTUnionCaseVoidV0Xdr("doc", "Empty")
        )
        assertXdrRoundTrip(c, { v, w -> v.encode(w) }, { r -> SCSpecUDTUnionCaseV0Xdr.decode(r) })
    }

    @Test
    fun testSCSpecUDTUnionCaseV0Tuple() {
        val c = SCSpecUDTUnionCaseV0Xdr.TupleCase(
            SCSpecUDTUnionCaseTupleV0Xdr(
                "doc",
                "Value",
                listOf(SCSpecTypeDefXdr.Void(SCSpecTypeXdr.SC_SPEC_TYPE_I64))
            )
        )
        assertXdrRoundTrip(c, { v, w -> v.encode(w) }, { r -> SCSpecUDTUnionCaseV0Xdr.decode(r) })
    }

    @Test
    fun testSCSpecUDTUnionV0() {
        val union = SCSpecUDTUnionV0Xdr(
            doc = "Option-like type",
            lib = "",
            name = "MyOption",
            cases = listOf(
                SCSpecUDTUnionCaseV0Xdr.VoidCase(SCSpecUDTUnionCaseVoidV0Xdr("", "None")),
                SCSpecUDTUnionCaseV0Xdr.TupleCase(
                    SCSpecUDTUnionCaseTupleV0Xdr("", "Some", listOf(SCSpecTypeDefXdr.Void(SCSpecTypeXdr.SC_SPEC_TYPE_U64)))
                )
            )
        )
        assertXdrRoundTrip(union, { v, w -> v.encode(w) }, { r -> SCSpecUDTUnionV0Xdr.decode(r) })
    }

    // ========== Enum ==========

    @Test
    fun testSCSpecUDTEnumCaseV0() {
        val c = SCSpecUDTEnumCaseV0Xdr(doc = "Active state", name = "Active", value = uint32(0u))
        assertXdrRoundTrip(c, { v, w -> v.encode(w) }, { r -> SCSpecUDTEnumCaseV0Xdr.decode(r) })
    }

    @Test
    fun testSCSpecUDTEnumV0() {
        val e = SCSpecUDTEnumV0Xdr(
            doc = "Token status",
            lib = "",
            name = "Status",
            cases = listOf(
                SCSpecUDTEnumCaseV0Xdr("Active", "Active", uint32(0u)),
                SCSpecUDTEnumCaseV0Xdr("Paused", "Paused", uint32(1u))
            )
        )
        assertXdrRoundTrip(e, { v, w -> v.encode(w) }, { r -> SCSpecUDTEnumV0Xdr.decode(r) })
    }

    // ========== Error enum ==========

    @Test
    fun testSCSpecUDTErrorEnumCaseV0() {
        val c = SCSpecUDTErrorEnumCaseV0Xdr(doc = "Not found", name = "NotFound", value = uint32(1u))
        assertXdrRoundTrip(c, { v, w -> v.encode(w) }, { r -> SCSpecUDTErrorEnumCaseV0Xdr.decode(r) })
    }

    @Test
    fun testSCSpecUDTErrorEnumV0() {
        val e = SCSpecUDTErrorEnumV0Xdr(
            doc = "Contract errors",
            lib = "",
            name = "Error",
            cases = listOf(
                SCSpecUDTErrorEnumCaseV0Xdr("Not found", "NotFound", uint32(1u)),
                SCSpecUDTErrorEnumCaseV0Xdr("Unauthorized", "Unauthorized", uint32(2u))
            )
        )
        assertXdrRoundTrip(e, { v, w -> v.encode(w) }, { r -> SCSpecUDTErrorEnumV0Xdr.decode(r) })
    }

    // ========== Event ==========

    @Test
    fun testSCSpecEventParamV0() {
        val p = SCSpecEventParamV0Xdr(
            doc = "Amount transferred",
            name = "amount",
            type = SCSpecTypeDefXdr.Void(SCSpecTypeXdr.SC_SPEC_TYPE_I128),
            location = SCSpecEventParamLocationV0Xdr.SC_SPEC_EVENT_PARAM_LOCATION_DATA
        )
        assertXdrRoundTrip(p, { v, w -> v.encode(w) }, { r -> SCSpecEventParamV0Xdr.decode(r) })
    }

    @Test
    fun testSCSpecEventV0() {
        val event = SCSpecEventV0Xdr(
            doc = "Transfer event",
            lib = "",
            name = SCSymbolXdr("transfer"),
            prefixTopics = listOf(SCSymbolXdr("TOKEN")),
            params = listOf(
                SCSpecEventParamV0Xdr("From", "from", SCSpecTypeDefXdr.Void(SCSpecTypeXdr.SC_SPEC_TYPE_ADDRESS), SCSpecEventParamLocationV0Xdr.SC_SPEC_EVENT_PARAM_LOCATION_TOPIC_LIST),
                SCSpecEventParamV0Xdr("Amount", "amount", SCSpecTypeDefXdr.Void(SCSpecTypeXdr.SC_SPEC_TYPE_I128), SCSpecEventParamLocationV0Xdr.SC_SPEC_EVENT_PARAM_LOCATION_DATA)
            ),
            dataFormat = SCSpecEventDataFormatXdr.SC_SPEC_EVENT_DATA_FORMAT_SINGLE_VALUE
        )
        assertXdrRoundTrip(event, { v, w -> v.encode(w) }, { r -> SCSpecEventV0Xdr.decode(r) })
    }

    @Test
    fun testSCSpecEventV0EmptyParams() {
        val event = SCSpecEventV0Xdr(
            doc = "",
            lib = "",
            name = SCSymbolXdr("init"),
            prefixTopics = emptyList(),
            params = emptyList(),
            dataFormat = SCSpecEventDataFormatXdr.SC_SPEC_EVENT_DATA_FORMAT_VEC
        )
        assertXdrRoundTrip(event, { v, w -> v.encode(w) }, { r -> SCSpecEventV0Xdr.decode(r) })
    }

    // ========== SCSpecEntry ==========

    @Test
    fun testSCSpecEntryFunction() {
        val entry = SCSpecEntryXdr.FunctionV0(
            SCSpecFunctionV0Xdr("", SCSymbolXdr("hello"), emptyList(), emptyList())
        )
        assertXdrRoundTrip(entry, { v, w -> v.encode(w) }, { r -> SCSpecEntryXdr.decode(r) })
    }

    @Test
    fun testSCSpecEntryStruct() {
        val entry = SCSpecEntryXdr.UdtStructV0(
            SCSpecUDTStructV0Xdr("", "", "Point", listOf(
                SCSpecUDTStructFieldV0Xdr("", "x", SCSpecTypeDefXdr.Void(SCSpecTypeXdr.SC_SPEC_TYPE_I32)),
                SCSpecUDTStructFieldV0Xdr("", "y", SCSpecTypeDefXdr.Void(SCSpecTypeXdr.SC_SPEC_TYPE_I32))
            ))
        )
        assertXdrRoundTrip(entry, { v, w -> v.encode(w) }, { r -> SCSpecEntryXdr.decode(r) })
    }

    @Test
    fun testSCSpecEntryUnion() {
        val entry = SCSpecEntryXdr.UdtUnionV0(
            SCSpecUDTUnionV0Xdr("", "", "Result", listOf(
                SCSpecUDTUnionCaseV0Xdr.VoidCase(SCSpecUDTUnionCaseVoidV0Xdr("", "Ok")),
                SCSpecUDTUnionCaseV0Xdr.VoidCase(SCSpecUDTUnionCaseVoidV0Xdr("", "Err"))
            ))
        )
        assertXdrRoundTrip(entry, { v, w -> v.encode(w) }, { r -> SCSpecEntryXdr.decode(r) })
    }

    @Test
    fun testSCSpecEntryEnum() {
        val entry = SCSpecEntryXdr.UdtEnumV0(
            SCSpecUDTEnumV0Xdr("", "", "Color", listOf(
                SCSpecUDTEnumCaseV0Xdr("", "Red", uint32(0u)),
                SCSpecUDTEnumCaseV0Xdr("", "Blue", uint32(1u))
            ))
        )
        assertXdrRoundTrip(entry, { v, w -> v.encode(w) }, { r -> SCSpecEntryXdr.decode(r) })
    }

    @Test
    fun testSCSpecEntryErrorEnum() {
        val entry = SCSpecEntryXdr.UdtErrorEnumV0(
            SCSpecUDTErrorEnumV0Xdr("", "", "Error", listOf(
                SCSpecUDTErrorEnumCaseV0Xdr("", "NotFound", uint32(404u))
            ))
        )
        assertXdrRoundTrip(entry, { v, w -> v.encode(w) }, { r -> SCSpecEntryXdr.decode(r) })
    }

    @Test
    fun testSCSpecEntryEvent() {
        val entry = SCSpecEntryXdr.EventV0(
            SCSpecEventV0Xdr("", "", SCSymbolXdr("e"), emptyList(), emptyList(),
                SCSpecEventDataFormatXdr.SC_SPEC_EVENT_DATA_FORMAT_MAP)
        )
        assertXdrRoundTrip(entry, { v, w -> v.encode(w) }, { r -> SCSpecEntryXdr.decode(r) })
    }
}
