package com.soneso.stellar.sdk.unitTests.xdr

import com.soneso.stellar.sdk.xdr.*
import kotlin.test.Test
import kotlin.test.assertEquals

class XdrSorobanDataTest {

    private fun rtSorobanData(value: SorobanTransactionDataXdr) =
        XdrTestHelpers.assertXdrRoundTrip(value, { v, w -> v.encode(w) }, { r -> SorobanTransactionDataXdr.decode(r) })

    @Test
    fun testSorobanTransactionDataBasic() {
        rtSorobanData(SorobanTransactionDataXdr(
            ext = SorobanTransactionDataExtXdr.Void,
            resources = SorobanResourcesXdr(
                footprint = LedgerFootprintXdr(readOnly = emptyList(), readWrite = emptyList()),
                instructions = Uint32Xdr(100000u),
                diskReadBytes = Uint32Xdr(1024u),
                writeBytes = Uint32Xdr(512u)
            ),
            resourceFee = Int64Xdr(50000L)
        ))
    }

    @Test
    fun testSorobanTransactionDataWithFootprint() {
        val accountKey = LedgerKeyXdr.Account(LedgerKeyAccountXdr(XdrTestHelpers.accountId()))
        rtSorobanData(SorobanTransactionDataXdr(
            ext = SorobanTransactionDataExtXdr.Void,
            resources = SorobanResourcesXdr(
                footprint = LedgerFootprintXdr(readOnly = listOf(accountKey), readWrite = emptyList()),
                instructions = Uint32Xdr(200000u),
                diskReadBytes = Uint32Xdr(2048u),
                writeBytes = Uint32Xdr(1024u)
            ),
            resourceFee = Int64Xdr(100000L)
        ))
    }

    @Test
    fun testSorobanResourcesXdr() {
        val original = SorobanResourcesXdr(
            footprint = LedgerFootprintXdr(emptyList(), emptyList()),
            instructions = Uint32Xdr(50000u),
            diskReadBytes = Uint32Xdr(256u),
            writeBytes = Uint32Xdr(128u)
        )
        val writer = XdrWriter()
        original.encode(writer)
        val decoded = SorobanResourcesXdr.decode(XdrReader(writer.toByteArray()))
        assertEquals(original, decoded)
    }

    @Test
    fun testLedgerFootprintEmpty() {
        val original = LedgerFootprintXdr(emptyList(), emptyList())
        val writer = XdrWriter()
        original.encode(writer)
        val decoded = LedgerFootprintXdr.decode(XdrReader(writer.toByteArray()))
        assertEquals(original, decoded)
    }

    @Test
    fun testLedgerFootprintMultipleKeys() {
        val key1 = LedgerKeyXdr.Account(LedgerKeyAccountXdr(XdrTestHelpers.accountId()))
        val key2 = LedgerKeyXdr.Account(LedgerKeyAccountXdr(XdrTestHelpers.accountIdAlt()))
        XdrTestHelpers.assertXdrRoundTrip(
            LedgerFootprintXdr(readOnly = listOf(key1), readWrite = listOf(key2)),
            { v, w -> v.encode(w) }, { r -> LedgerFootprintXdr.decode(r) })
    }

    @Test
    fun testSorobanTransactionDataExtVoid() {
        val writer = XdrWriter()
        SorobanTransactionDataExtXdr.Void.encode(writer)
        val decoded = SorobanTransactionDataExtXdr.decode(XdrReader(writer.toByteArray()))
        assertEquals(SorobanTransactionDataExtXdr.Void, decoded)
    }

    @Test
    fun testSorobanDataMaxResources() {
        rtSorobanData(SorobanTransactionDataXdr(
            ext = SorobanTransactionDataExtXdr.Void,
            resources = SorobanResourcesXdr(
                footprint = LedgerFootprintXdr(emptyList(), emptyList()),
                instructions = Uint32Xdr(UInt.MAX_VALUE),
                diskReadBytes = Uint32Xdr(UInt.MAX_VALUE),
                writeBytes = Uint32Xdr(UInt.MAX_VALUE)
            ),
            resourceFee = Int64Xdr(Long.MAX_VALUE)
        ))
    }

    @Test
    fun testContractExecutableWasm() {
        XdrTestHelpers.assertXdrRoundTrip(
            ContractExecutableXdr.WasmHash(XdrTestHelpers.hashXdr()),
            { v, w -> v.encode(w) }, { r -> ContractExecutableXdr.decode(r) })
    }

    @Test
    fun testContractExecutableStellarAsset() {
        val writer = XdrWriter()
        ContractExecutableXdr.Void.encode(writer)
        val decoded = ContractExecutableXdr.decode(XdrReader(writer.toByteArray()))
        assertEquals(ContractExecutableXdr.Void, decoded)
    }

    @Test
    fun testHostFunctionWasm() {
        XdrTestHelpers.assertXdrRoundTrip(
            HostFunctionXdr.Wasm(byteArrayOf(0, 0x61, 0x73, 0x6D)),
            { v, w -> v.encode(w) }, { r -> HostFunctionXdr.decode(r) })
    }

    @Test
    fun testContractDataDurabilityTemporary() {
        val writer = XdrWriter()
        ContractDataDurabilityXdr.TEMPORARY.encode(writer)
        val decoded = ContractDataDurabilityXdr.decode(XdrReader(writer.toByteArray()))
        assertEquals(ContractDataDurabilityXdr.TEMPORARY, decoded)
    }

    @Test
    fun testContractDataDurabilityPersistent() {
        val writer = XdrWriter()
        ContractDataDurabilityXdr.PERSISTENT.encode(writer)
        val decoded = ContractDataDurabilityXdr.decode(XdrReader(writer.toByteArray()))
        assertEquals(ContractDataDurabilityXdr.PERSISTENT, decoded)
    }

    @Test
    fun testSCAddressAccount() {
        XdrTestHelpers.assertXdrRoundTrip(
            SCAddressXdr.AccountId(XdrTestHelpers.accountId()),
            { v, w -> v.encode(w) }, { r -> SCAddressXdr.decode(r) })
    }

    @Test
    fun testSCAddressContract() {
        XdrTestHelpers.assertXdrRoundTrip(
            SCAddressXdr.ContractId(XdrTestHelpers.contractId()),
            { v, w -> v.encode(w) }, { r -> SCAddressXdr.decode(r) })
    }

    @Test
    fun testSCErrorContractCode() {
        XdrTestHelpers.assertXdrRoundTrip(
            SCErrorXdr.ContractCode(Uint32Xdr(42u)),
            { v, w -> v.encode(w) }, { r -> SCErrorXdr.decode(r) })
    }

    @Test
    fun testSCErrorCodeArithDomain() {
        XdrTestHelpers.assertXdrRoundTrip(
            SCErrorXdr.Code(SCErrorCodeXdr.SCEC_ARITH_DOMAIN),
            { v, w -> v.encode(w) }, { r -> SCErrorXdr.decode(r) })
    }

    @Test
    fun testClaimAtomOrderBook() {
        XdrTestHelpers.assertXdrRoundTrip(
            ClaimAtomXdr.OrderBook(ClaimOfferAtomXdr(
                sellerId = XdrTestHelpers.accountId(), offerId = XdrTestHelpers.int64(100L),
                assetSold = XdrTestHelpers.assetNative(), amountSold = XdrTestHelpers.int64(500L),
                assetBought = XdrTestHelpers.assetAlphaNum4(), amountBought = XdrTestHelpers.int64(250L)
            )),
            { v, w -> v.encode(w) }, { r -> ClaimAtomXdr.decode(r) })
    }

    @Test
    fun testClaimAtomV0() {
        XdrTestHelpers.assertXdrRoundTrip(
            ClaimAtomXdr.V0(ClaimOfferAtomV0Xdr(
                sellerEd25519 = XdrTestHelpers.uint256Xdr(), offerId = XdrTestHelpers.int64(200L),
                assetSold = XdrTestHelpers.assetAlphaNum4(), amountSold = XdrTestHelpers.int64(1000L),
                assetBought = XdrTestHelpers.assetNative(), amountBought = XdrTestHelpers.int64(500L)
            )),
            { v, w -> v.encode(w) }, { r -> ClaimAtomXdr.decode(r) })
    }

    @Test
    fun testClaimAtomLiquidityPool() {
        XdrTestHelpers.assertXdrRoundTrip(
            ClaimAtomXdr.LiquidityPool(ClaimLiquidityAtomXdr(
                liquidityPoolId = XdrTestHelpers.poolId(),
                assetSold = XdrTestHelpers.assetNative(), amountSold = XdrTestHelpers.int64(3000L),
                assetBought = XdrTestHelpers.assetAlphaNum12(), amountBought = XdrTestHelpers.int64(1500L)
            )),
            { v, w -> v.encode(w) }, { r -> ClaimAtomXdr.decode(r) })
    }

    @Test
    fun testManageOfferSuccessDeleted() {
        XdrTestHelpers.assertXdrRoundTrip(
            ManageOfferSuccessResultXdr(offersClaimed = emptyList(), offer = ManageOfferSuccessResultOfferXdr.Void),
            { v, w -> v.encode(w) }, { r -> ManageOfferSuccessResultXdr.decode(r) })
    }

    @Test
    fun testManageOfferSuccessCreated() {
        val offerEntry = OfferEntryXdr(
            sellerId = XdrTestHelpers.accountId(), offerId = XdrTestHelpers.int64(1L),
            selling = XdrTestHelpers.assetNative(), buying = XdrTestHelpers.assetAlphaNum4(),
            amount = XdrTestHelpers.int64(1000L), price = XdrTestHelpers.price(1, 1),
            flags = XdrTestHelpers.uint32(0u), ext = OfferEntryExtXdr.Void
        )
        XdrTestHelpers.assertXdrRoundTrip(
            ManageOfferSuccessResultXdr(offersClaimed = emptyList(), offer = ManageOfferSuccessResultOfferXdr.Offer(offerEntry)),
            { v, w -> v.encode(w) }, { r -> ManageOfferSuccessResultXdr.decode(r) })
    }
}
