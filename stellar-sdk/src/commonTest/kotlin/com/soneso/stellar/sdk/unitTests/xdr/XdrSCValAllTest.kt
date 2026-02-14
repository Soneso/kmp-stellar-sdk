package com.soneso.stellar.sdk.unitTests.xdr

import com.soneso.stellar.sdk.xdr.*
import kotlin.test.Test
import kotlin.test.assertEquals

class XdrSCValAllTest {

    private fun rt(value: SCValXdr) =
        XdrTestHelpers.assertXdrRoundTrip(value, { v, w -> v.encode(w) }, { r -> SCValXdr.decode(r) })

    @Test fun testBoolTrue() = rt(SCValXdr.B(true))
    @Test fun testBoolFalse() = rt(SCValXdr.B(false))
    @Test fun testVoidSCVVoid() = rt(SCValXdr.Void(SCValTypeXdr.SCV_VOID))
    @Test fun testVoidLedgerKeyContractInstance() = rt(SCValXdr.Void(SCValTypeXdr.SCV_LEDGER_KEY_CONTRACT_INSTANCE))

    @Test fun testErrorContractCode() = rt(SCValXdr.Error(SCErrorXdr.ContractCode(Uint32Xdr(42u))))
    @Test fun testErrorWasmVM() = rt(SCValXdr.Error(SCErrorXdr.Code(SCErrorCodeXdr.SCEC_INTERNAL_ERROR)))

    @Test fun testU32() = rt(SCValXdr.U32(Uint32Xdr(123456u)))
    @Test fun testU32Zero() = rt(SCValXdr.U32(Uint32Xdr(0u)))
    @Test fun testU32Max() = rt(SCValXdr.U32(Uint32Xdr(UInt.MAX_VALUE)))
    @Test fun testI32() = rt(SCValXdr.I32(Int32Xdr(-42)))
    @Test fun testI32Positive() = rt(SCValXdr.I32(Int32Xdr(Int.MAX_VALUE)))
    @Test fun testI32Negative() = rt(SCValXdr.I32(Int32Xdr(Int.MIN_VALUE)))
    @Test fun testU64() = rt(SCValXdr.U64(Uint64Xdr(999999UL)))
    @Test fun testU64Max() = rt(SCValXdr.U64(Uint64Xdr(ULong.MAX_VALUE)))
    @Test fun testI64() = rt(SCValXdr.I64(Int64Xdr(-999999L)))
    @Test fun testI64Positive() = rt(SCValXdr.I64(Int64Xdr(Long.MAX_VALUE)))

    @Test fun testTimepoint() = rt(SCValXdr.Timepoint(TimePointXdr(Uint64Xdr(1234567890UL))))
    @Test fun testDuration() = rt(SCValXdr.Duration(DurationXdr(Uint64Xdr(3600UL))))

    @Test fun testU128() = rt(SCValXdr.U128(UInt128PartsXdr(hi = Uint64Xdr(1UL), lo = Uint64Xdr(2UL))))
    @Test fun testI128() = rt(SCValXdr.I128(Int128PartsXdr(hi = Int64Xdr(-1L), lo = Uint64Xdr(ULong.MAX_VALUE))))
    @Test fun testU256() = rt(SCValXdr.U256(UInt256PartsXdr(
        hiHi = Uint64Xdr(1UL), hiLo = Uint64Xdr(2UL), loHi = Uint64Xdr(3UL), loLo = Uint64Xdr(4UL))))
    @Test fun testI256() = rt(SCValXdr.I256(Int256PartsXdr(
        hiHi = Int64Xdr(-1L), hiLo = Uint64Xdr(0UL), loHi = Uint64Xdr(0UL), loLo = Uint64Xdr(1UL))))

    @Test fun testBytesEmpty() = rt(SCValXdr.Bytes(SCBytesXdr(byteArrayOf())))
    @Test fun testBytesNonEmpty() = rt(SCValXdr.Bytes(SCBytesXdr(byteArrayOf(1, 2, 3, 4, 5))))
    @Test fun testString() = rt(SCValXdr.Str(SCStringXdr("Hello, Stellar!")))
    @Test fun testStringEmpty() = rt(SCValXdr.Str(SCStringXdr("")))
    @Test fun testSymbol() = rt(SCValXdr.Sym(SCSymbolXdr("transfer")))
    @Test fun testSymbolEmpty() = rt(SCValXdr.Sym(SCSymbolXdr("")))

    @Test fun testVecNull() = rt(SCValXdr.Vec(null))
    @Test fun testVecEmpty() = rt(SCValXdr.Vec(SCVecXdr(emptyList())))
    @Test fun testVecWithElements() = rt(SCValXdr.Vec(SCVecXdr(listOf(
        SCValXdr.U32(Uint32Xdr(1u)), SCValXdr.U32(Uint32Xdr(2u)), SCValXdr.B(true)
    ))))

    @Test fun testMapNull() = rt(SCValXdr.Map(null))
    @Test fun testMapEmpty() = rt(SCValXdr.Map(SCMapXdr(emptyList())))
    @Test fun testMapWithEntries() = rt(SCValXdr.Map(SCMapXdr(listOf(
        SCMapEntryXdr(key = SCValXdr.Sym(SCSymbolXdr("name")), `val` = SCValXdr.Str(SCStringXdr("Alice"))),
        SCMapEntryXdr(key = SCValXdr.Sym(SCSymbolXdr("age")), `val` = SCValXdr.U32(Uint32Xdr(30u)))
    ))))

    @Test fun testAddressAccount() = rt(SCValXdr.Address(SCAddressXdr.AccountId(XdrTestHelpers.accountId())))
    @Test fun testAddressContract() = rt(SCValXdr.Address(SCAddressXdr.ContractId(XdrTestHelpers.contractId())))

    @Test fun testInstanceStellarAsset() = rt(SCValXdr.Instance(
        SCContractInstanceXdr(executable = ContractExecutableXdr.Void, storage = null)
    ))

    @Test fun testInstanceWasmWithStorage() = rt(SCValXdr.Instance(
        SCContractInstanceXdr(
            executable = ContractExecutableXdr.WasmHash(XdrTestHelpers.hashXdr()),
            storage = SCMapXdr(listOf(SCMapEntryXdr(
                key = SCValXdr.Sym(SCSymbolXdr("key1")), `val` = SCValXdr.U32(Uint32Xdr(10u))
            )))
        )
    ))

    @Test fun testInstanceWasmNoStorage() = rt(SCValXdr.Instance(
        SCContractInstanceXdr(executable = ContractExecutableXdr.WasmHash(XdrTestHelpers.hashXdr()), storage = null)
    ))

    @Test fun testNonceKey() = rt(SCValXdr.NonceKey(SCNonceKeyXdr(nonce = Int64Xdr(9876543210L))))

    @Test fun testNestedVec() {
        val inner = SCValXdr.Vec(SCVecXdr(listOf(SCValXdr.U32(Uint32Xdr(1u)))))
        rt(SCValXdr.Vec(SCVecXdr(listOf(inner, SCValXdr.B(false)))))
    }

    @Test fun testNestedMap() {
        val innerMap = SCValXdr.Map(SCMapXdr(listOf(
            SCMapEntryXdr(SCValXdr.Sym(SCSymbolXdr("inner")), SCValXdr.U64(Uint64Xdr(42UL)))
        )))
        rt(SCValXdr.Map(SCMapXdr(listOf(
            SCMapEntryXdr(SCValXdr.Sym(SCSymbolXdr("data")), innerMap)
        ))))
    }

    @Test fun testErrorContextType() = rt(SCValXdr.Error(SCErrorXdr.Code(SCErrorCodeXdr.SCEC_ARITH_DOMAIN)))
    @Test fun testErrorStorageType() = rt(SCValXdr.Error(SCErrorXdr.Code(SCErrorCodeXdr.SCEC_MISSING_VALUE)))
    @Test fun testErrorBudgetType() = rt(SCValXdr.Error(SCErrorXdr.Code(SCErrorCodeXdr.SCEC_EXCEEDED_LIMIT)))

    @Test fun testAddressLiquidityPool() = rt(SCValXdr.Address(SCAddressXdr.LiquidityPoolId(XdrTestHelpers.poolId())))
    @Test fun testAddressClaimableBalance() = rt(SCValXdr.Address(
        SCAddressXdr.ClaimableBalanceId(ClaimableBalanceIDXdr.V0(XdrTestHelpers.hashXdr()))
    ))
    @Test fun testAddressMuxed() = rt(SCValXdr.Address(SCAddressXdr.MuxedAccount(
        MuxedEd25519AccountXdr(id = Uint64Xdr(12345UL), ed25519 = XdrTestHelpers.uint256Xdr())
    )))
}
