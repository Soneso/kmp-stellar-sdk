package com.soneso.stellar.sdk.unitTests.xdr

import com.soneso.stellar.sdk.xdr.*
import com.soneso.stellar.sdk.unitTests.xdr.XdrTestHelpers.assertXdrRoundTrip
import com.soneso.stellar.sdk.unitTests.xdr.XdrTestHelpers.accountId
import com.soneso.stellar.sdk.unitTests.xdr.XdrTestHelpers.assetNative
import com.soneso.stellar.sdk.unitTests.xdr.XdrTestHelpers.assetAlphaNum4
import com.soneso.stellar.sdk.unitTests.xdr.XdrTestHelpers.assetAlphaNum12
import com.soneso.stellar.sdk.unitTests.xdr.XdrTestHelpers.hashXdr
import com.soneso.stellar.sdk.unitTests.xdr.XdrTestHelpers.int32
import com.soneso.stellar.sdk.unitTests.xdr.XdrTestHelpers.int64
import com.soneso.stellar.sdk.unitTests.xdr.XdrTestHelpers.muxedAccountEd25519
import com.soneso.stellar.sdk.unitTests.xdr.XdrTestHelpers.poolId
import com.soneso.stellar.sdk.unitTests.xdr.XdrTestHelpers.sequenceNumber
import com.soneso.stellar.sdk.unitTests.xdr.XdrTestHelpers.uint32
import com.soneso.stellar.sdk.unitTests.xdr.XdrTestHelpers.uint64
import com.soneso.stellar.sdk.unitTests.xdr.XdrTestHelpers.uint256Xdr
import com.soneso.stellar.sdk.unitTests.xdr.XdrTestHelpers.thresholds
import com.soneso.stellar.sdk.unitTests.xdr.XdrTestHelpers.string32
import com.soneso.stellar.sdk.unitTests.xdr.XdrTestHelpers.price
import kotlin.test.Test

class XdrMiscTypesTest {

    // ========== SCMeta / SCEnvMeta ==========

    @Test
    fun testSCMetaKindEnum() {
        for (e in SCMetaKindXdr.entries) {
            assertXdrRoundTrip(e, { v, w -> v.encode(w) }, { r -> SCMetaKindXdr.decode(r) })
        }
    }

    @Test
    fun testSCEnvMetaKindEnum() {
        for (e in SCEnvMetaKindXdr.entries) {
            assertXdrRoundTrip(e, { v, w -> v.encode(w) }, { r -> SCEnvMetaKindXdr.decode(r) })
        }
    }

    @Test
    fun testSCMetaV0() {
        val meta = SCMetaV0Xdr(key = "version", `val` = "1.0.0")
        assertXdrRoundTrip(meta, { v, w -> v.encode(w) }, { r -> SCMetaV0Xdr.decode(r) })
    }

    @Test
    fun testSCMetaEntryV0() {
        val entry = SCMetaEntryXdr.V0(SCMetaV0Xdr("author", "test"))
        assertXdrRoundTrip(entry, { v, w -> v.encode(w) }, { r -> SCMetaEntryXdr.decode(r) })
    }

    @Test
    fun testSCEnvMetaEntryInterfaceVersion() {
        val iv = SCEnvMetaEntryInterfaceVersionXdr(
            protocol = uint32(21u),
            preRelease = uint32(0u)
        )
        assertXdrRoundTrip(iv, { v, w -> v.encode(w) }, { r -> SCEnvMetaEntryInterfaceVersionXdr.decode(r) })
    }

    @Test
    fun testSCEnvMetaEntry() {
        val entry = SCEnvMetaEntryXdr.InterfaceVersion(
            SCEnvMetaEntryInterfaceVersionXdr(
                protocol = uint32(22u),
                preRelease = uint32(1u)
            )
        )
        assertXdrRoundTrip(entry, { v, w -> v.encode(w) }, { r -> SCEnvMetaEntryXdr.decode(r) })
    }

    // ========== ContractCode types ==========

    @Test
    fun testContractCodeCostInputs() {
        val inputs = ContractCodeCostInputsXdr(
            ext = ExtensionPointXdr.Void,
            nInstructions = uint32(1000u),
            nFunctions = uint32(10u),
            nGlobals = uint32(5u),
            nTableEntries = uint32(2u),
            nTypes = uint32(20u),
            nDataSegments = uint32(3u),
            nElemSegments = uint32(1u),
            nImports = uint32(4u),
            nExports = uint32(6u),
            nDataSegmentBytes = uint32(256u)
        )
        assertXdrRoundTrip(inputs, { v, w -> v.encode(w) }, { r -> ContractCodeCostInputsXdr.decode(r) })
    }

    @Test
    fun testContractCodeEntryV1() {
        val entry = ContractCodeEntryV1Xdr(
            ext = ExtensionPointXdr.Void,
            costInputs = ContractCodeCostInputsXdr(
                ext = ExtensionPointXdr.Void,
                nInstructions = uint32(500u),
                nFunctions = uint32(5u),
                nGlobals = uint32(2u),
                nTableEntries = uint32(0u),
                nTypes = uint32(10u),
                nDataSegments = uint32(1u),
                nElemSegments = uint32(0u),
                nImports = uint32(3u),
                nExports = uint32(2u),
                nDataSegmentBytes = uint32(128u)
            )
        )
        assertXdrRoundTrip(entry, { v, w -> v.encode(w) }, { r -> ContractCodeEntryV1Xdr.decode(r) })
    }

    // ========== LedgerKey contract types ==========

    @Test
    fun testLedgerKeyContractCode() {
        val key = LedgerKeyContractCodeXdr(hash = hashXdr())
        assertXdrRoundTrip(key, { v, w -> v.encode(w) }, { r -> LedgerKeyContractCodeXdr.decode(r) })
    }

    @Test
    fun testLedgerKeyContractData() {
        val key = LedgerKeyContractDataXdr(
            contract = SCAddressXdr.ContractId(ContractIDXdr(hashXdr())),
            key = SCValXdr.Sym(SCSymbolXdr("balance")),
            durability = ContractDataDurabilityXdr.PERSISTENT
        )
        assertXdrRoundTrip(key, { v, w -> v.encode(w) }, { r -> LedgerKeyContractDataXdr.decode(r) })
    }

    // ========== LiquidityPool types ==========

    @Test
    fun testLiquidityPoolTypeEnum() {
        for (e in LiquidityPoolTypeXdr.entries) {
            assertXdrRoundTrip(e, { v, w -> v.encode(w) }, { r -> LiquidityPoolTypeXdr.decode(r) })
        }
    }

    @Test
    fun testLiquidityPoolConstantProductParameters() {
        val params = LiquidityPoolConstantProductParametersXdr(
            assetA = assetNative(),
            assetB = assetAlphaNum4(),
            fee = int32(30)
        )
        assertXdrRoundTrip(params, { v, w -> v.encode(w) }, { r -> LiquidityPoolConstantProductParametersXdr.decode(r) })
    }

    @Test
    fun testLiquidityPoolEntryConstantProduct() {
        val cp = LiquidityPoolEntryConstantProductXdr(
            params = LiquidityPoolConstantProductParametersXdr(
                assetA = assetNative(),
                assetB = assetAlphaNum4(),
                fee = int32(30)
            ),
            reserveA = int64(1000000L),
            reserveB = int64(500000L),
            totalPoolShares = int64(1500000L),
            poolSharesTrustLineCount = int64(10L)
        )
        assertXdrRoundTrip(cp, { v, w -> v.encode(w) }, { r -> LiquidityPoolEntryConstantProductXdr.decode(r) })
    }

    @Test
    fun testLiquidityPoolEntryBody() {
        val body = LiquidityPoolEntryBodyXdr.ConstantProduct(
            LiquidityPoolEntryConstantProductXdr(
                params = LiquidityPoolConstantProductParametersXdr(assetNative(), assetAlphaNum4(), int32(30)),
                reserveA = int64(100L),
                reserveB = int64(200L),
                totalPoolShares = int64(300L),
                poolSharesTrustLineCount = int64(2L)
            )
        )
        assertXdrRoundTrip(body, { v, w -> v.encode(w) }, { r -> LiquidityPoolEntryBodyXdr.decode(r) })
    }

    @Test
    fun testLiquidityPoolEntry() {
        val entry = LiquidityPoolEntryXdr(
            liquidityPoolId = poolId(),
            body = LiquidityPoolEntryBodyXdr.ConstantProduct(
                LiquidityPoolEntryConstantProductXdr(
                    params = LiquidityPoolConstantProductParametersXdr(assetNative(), assetAlphaNum12(), int32(30)),
                    reserveA = int64(1000L),
                    reserveB = int64(2000L),
                    totalPoolShares = int64(3000L),
                    poolSharesTrustLineCount = int64(5L)
                )
            )
        )
        assertXdrRoundTrip(entry, { v, w -> v.encode(w) }, { r -> LiquidityPoolEntryXdr.decode(r) })
    }

    @Test
    fun testLiquidityPoolParameters() {
        val params = LiquidityPoolParametersXdr.ConstantProduct(
            LiquidityPoolConstantProductParametersXdr(assetNative(), assetAlphaNum4(), int32(30))
        )
        assertXdrRoundTrip(params, { v, w -> v.encode(w) }, { r -> LiquidityPoolParametersXdr.decode(r) })
    }

    // ========== SerializedBinaryFuseFilter ==========

    @Test
    fun testBinaryFuseFilterTypeEnum() {
        for (e in BinaryFuseFilterTypeXdr.entries) {
            assertXdrRoundTrip(e, { v, w -> v.encode(w) }, { r -> BinaryFuseFilterTypeXdr.decode(r) })
        }
    }

    @Test
    fun testSerializedBinaryFuseFilter() {
        val filter = SerializedBinaryFuseFilterXdr(
            type = BinaryFuseFilterTypeXdr.BINARY_FUSE_FILTER_8_BIT,
            inputHashSeed = ShortHashSeedXdr(ByteArray(16) { it.toByte() }),
            filterSeed = ShortHashSeedXdr(ByteArray(16) { (it + 16).toByte() }),
            segmentLength = uint32(100u),
            segementLengthMask = uint32(127u),
            segmentCount = uint32(3u),
            segmentCountLength = uint32(300u),
            fingerprintLength = uint32(50u),
            fingerprints = ByteArray(50) { (it * 2).toByte() }
        )
        assertXdrRoundTrip(filter, { v, w -> v.encode(w) }, { r -> SerializedBinaryFuseFilterXdr.decode(r) })
    }

    // ========== SorobanResourcesExtV0 ==========

    @Test
    fun testSorobanResourcesExtV0() {
        val ext = SorobanResourcesExtV0Xdr(
            archivedSorobanEntries = listOf(uint32(0u), uint32(2u), uint32(5u))
        )
        assertXdrRoundTrip(ext, { v, w -> v.encode(w) }, { r -> SorobanResourcesExtV0Xdr.decode(r) })
    }

    @Test
    fun testSorobanResourcesExtV0Empty() {
        val ext = SorobanResourcesExtV0Xdr(archivedSorobanEntries = emptyList())
        assertXdrRoundTrip(ext, { v, w -> v.encode(w) }, { r -> SorobanResourcesExtV0Xdr.decode(r) })
    }

    // ========== SorobanTransactionMetaExtV1 ==========

    @Test
    fun testSorobanTransactionMetaExtV1() {
        val meta = SorobanTransactionMetaExtV1Xdr(
            ext = ExtensionPointXdr.Void,
            totalNonRefundableResourceFeeCharged = int64(1000L),
            totalRefundableResourceFeeCharged = int64(500L),
            rentFeeCharged = int64(200L)
        )
        assertXdrRoundTrip(meta, { v, w -> v.encode(w) }, { r -> SorobanTransactionMetaExtV1Xdr.decode(r) })
    }
}
