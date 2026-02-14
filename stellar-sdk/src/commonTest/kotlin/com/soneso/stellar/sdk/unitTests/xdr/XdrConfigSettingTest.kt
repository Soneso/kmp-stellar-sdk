package com.soneso.stellar.sdk.unitTests.xdr

import com.soneso.stellar.sdk.xdr.*
import kotlin.test.Test

/**
 * Tests for all ConfigSetting-related XDR types and the ConfigSettingEntry union.
 */
class XdrConfigSettingTest {

    // ---- ConfigSettingID enum ----

    @Test fun testConfigSettingIDEnum() {
        for (id in ConfigSettingIDXdr.entries) {
            XdrTestHelpers.assertXdrRoundTrip(id, { a, w -> a.encode(w) }, { r -> ConfigSettingIDXdr.decode(r) })
        }
    }

    // ---- ConfigSettingContractComputeV0 ----

    @Test fun testContractComputeV0() {
        val v = ConfigSettingContractComputeV0Xdr(
            ledgerMaxInstructions = Int64Xdr(100000000L),
            txMaxInstructions = Int64Xdr(50000000L),
            feeRatePerInstructionsIncrement = Int64Xdr(25L),
            txMemoryLimit = Uint32Xdr(41943040u)
        )
        XdrTestHelpers.assertXdrRoundTrip(v, { a, w -> a.encode(w) }, { r -> ConfigSettingContractComputeV0Xdr.decode(r) })
    }

    // ---- ConfigSettingContractLedgerCostV0 ----

    @Test fun testContractLedgerCostV0() {
        val v = ConfigSettingContractLedgerCostV0Xdr(
            ledgerMaxDiskReadEntries = Uint32Xdr(200u),
            ledgerMaxDiskReadBytes = Uint32Xdr(200000u),
            ledgerMaxWriteLedgerEntries = Uint32Xdr(100u),
            ledgerMaxWriteBytes = Uint32Xdr(100000u),
            txMaxDiskReadEntries = Uint32Xdr(50u),
            txMaxDiskReadBytes = Uint32Xdr(50000u),
            txMaxWriteLedgerEntries = Uint32Xdr(25u),
            txMaxWriteBytes = Uint32Xdr(25000u),
            feeDiskReadLedgerEntry = Int64Xdr(1000L),
            feeWriteLedgerEntry = Int64Xdr(2000L),
            feeDiskRead1Kb = Int64Xdr(500L),
            sorobanStateTargetSizeBytes = Int64Xdr(50000000L),
            rentFee1KbSorobanStateSizeLow = Int64Xdr(100L),
            rentFee1KbSorobanStateSizeHigh = Int64Xdr(10000L),
            sorobanStateRentFeeGrowthFactor = Uint32Xdr(1000u)
        )
        XdrTestHelpers.assertXdrRoundTrip(v, { a, w -> a.encode(w) }, { r -> ConfigSettingContractLedgerCostV0Xdr.decode(r) })
    }

    // ---- ConfigSettingContractHistoricalDataV0 ----

    @Test fun testContractHistoricalDataV0() {
        val v = ConfigSettingContractHistoricalDataV0Xdr(feeHistorical1Kb = Int64Xdr(300L))
        XdrTestHelpers.assertXdrRoundTrip(v, { a, w -> a.encode(w) }, { r -> ConfigSettingContractHistoricalDataV0Xdr.decode(r) })
    }

    // ---- ConfigSettingContractEventsV0 ----

    @Test fun testContractEventsV0() {
        val v = ConfigSettingContractEventsV0Xdr(
            txMaxContractEventsSizeBytes = Uint32Xdr(8192u),
            feeContractEvents1Kb = Int64Xdr(200L)
        )
        XdrTestHelpers.assertXdrRoundTrip(v, { a, w -> a.encode(w) }, { r -> ConfigSettingContractEventsV0Xdr.decode(r) })
    }

    // ---- ConfigSettingContractBandwidthV0 ----

    @Test fun testContractBandwidthV0() {
        val v = ConfigSettingContractBandwidthV0Xdr(
            ledgerMaxTxsSizeBytes = Uint32Xdr(1048576u),
            txMaxSizeBytes = Uint32Xdr(65536u),
            feeTxSize1Kb = Int64Xdr(1624L)
        )
        XdrTestHelpers.assertXdrRoundTrip(v, { a, w -> a.encode(w) }, { r -> ConfigSettingContractBandwidthV0Xdr.decode(r) })
    }

    // ---- ConfigSettingContractExecutionLanesV0 ----

    @Test fun testContractExecutionLanesV0() {
        val v = ConfigSettingContractExecutionLanesV0Xdr(ledgerMaxTxCount = Uint32Xdr(100u))
        XdrTestHelpers.assertXdrRoundTrip(v, { a, w -> a.encode(w) }, { r -> ConfigSettingContractExecutionLanesV0Xdr.decode(r) })
    }

    // ---- ConfigSettingContractParallelComputeV0 ----

    @Test fun testContractParallelComputeV0() {
        val v = ConfigSettingContractParallelComputeV0Xdr(ledgerMaxDependentTxClusters = Uint32Xdr(16u))
        XdrTestHelpers.assertXdrRoundTrip(v, { a, w -> a.encode(w) }, { r -> ConfigSettingContractParallelComputeV0Xdr.decode(r) })
    }

    // ---- ConfigSettingContractLedgerCostExtV0 ----

    @Test fun testContractLedgerCostExtV0() {
        val v = ConfigSettingContractLedgerCostExtV0Xdr(
            txMaxFootprintEntries = Uint32Xdr(50u),
            feeWrite1Kb = Int64Xdr(4000L)
        )
        XdrTestHelpers.assertXdrRoundTrip(v, { a, w -> a.encode(w) }, { r -> ConfigSettingContractLedgerCostExtV0Xdr.decode(r) })
    }

    // ---- ConfigSettingSCPTiming ----

    @Test fun testSCPTiming() {
        val v = ConfigSettingSCPTimingXdr(
            ledgerTargetCloseTimeMilliseconds = Uint32Xdr(5000u),
            nominationTimeoutInitialMilliseconds = Uint32Xdr(1000u),
            nominationTimeoutIncrementMilliseconds = Uint32Xdr(500u),
            ballotTimeoutInitialMilliseconds = Uint32Xdr(1000u),
            ballotTimeoutIncrementMilliseconds = Uint32Xdr(500u)
        )
        XdrTestHelpers.assertXdrRoundTrip(v, { a, w -> a.encode(w) }, { r -> ConfigSettingSCPTimingXdr.decode(r) })
    }

    // ---- StateArchivalSettings ----

    @Test fun testStateArchivalSettings() {
        val v = StateArchivalSettingsXdr(
            maxEntryTtl = Uint32Xdr(6312000u),
            minTemporaryTtl = Uint32Xdr(16u),
            minPersistentTtl = Uint32Xdr(120960u),
            persistentRentRateDenominator = Int64Xdr(2103L),
            tempRentRateDenominator = Int64Xdr(4206L),
            maxEntriesToArchive = Uint32Xdr(100u),
            liveSorobanStateSizeWindowSampleSize = Uint32Xdr(30u),
            liveSorobanStateSizeWindowSamplePeriod = Uint32Xdr(5u),
            evictionScanSize = Uint32Xdr(100000u),
            startingEvictionScanLevel = Uint32Xdr(7u)
        )
        XdrTestHelpers.assertXdrRoundTrip(v, { a, w -> a.encode(w) }, { r -> StateArchivalSettingsXdr.decode(r) })
    }

    // ---- EvictionIterator ----

    @Test fun testEvictionIterator() {
        val v = EvictionIteratorXdr(
            bucketListLevel = Uint32Xdr(3u),
            isCurrBucket = true,
            bucketFileOffset = Uint64Xdr(1024UL)
        )
        XdrTestHelpers.assertXdrRoundTrip(v, { a, w -> a.encode(w) }, { r -> EvictionIteratorXdr.decode(r) })
    }

    // ---- ContractCostParams / ContractCostParamEntry ----

    @Test fun testContractCostParamEntry() {
        val v = ContractCostParamEntryXdr(
            ext = ExtensionPointXdr.Void,
            constTerm = Int64Xdr(100L),
            linearTerm = Int64Xdr(10L)
        )
        XdrTestHelpers.assertXdrRoundTrip(v, { a, w -> a.encode(w) }, { r -> ContractCostParamEntryXdr.decode(r) })
    }

    @Test fun testContractCostParams() {
        val entry = ContractCostParamEntryXdr(ExtensionPointXdr.Void, Int64Xdr(50L), Int64Xdr(5L))
        val v = ContractCostParamsXdr(listOf(entry))
        XdrTestHelpers.assertXdrRoundTrip(v, { a, w -> a.encode(w) }, { r -> ContractCostParamsXdr.decode(r) })
    }

    // ---- ContractCostType enum ----

    @Test fun testContractCostTypeEnum() {
        for (ct in ContractCostTypeXdr.entries) {
            XdrTestHelpers.assertXdrRoundTrip(ct, { a, w -> a.encode(w) }, { r -> ContractCostTypeXdr.decode(r) })
        }
    }

    // ---- ConfigUpgradeSetKey / ConfigUpgradeSet ----

    @Test fun testConfigUpgradeSetKey() {
        val v = ConfigUpgradeSetKeyXdr(
            contractId = XdrTestHelpers.contractId(),
            contentHash = XdrTestHelpers.hashXdr()
        )
        XdrTestHelpers.assertXdrRoundTrip(v, { a, w -> a.encode(w) }, { r -> ConfigUpgradeSetKeyXdr.decode(r) })
    }

    @Test fun testConfigUpgradeSet() {
        val entry = ConfigSettingEntryXdr.ContractMaxSizeBytes(Uint32Xdr(65536u))
        val v = ConfigUpgradeSetXdr(listOf(entry))
        XdrTestHelpers.assertXdrRoundTrip(v, { a, w -> a.encode(w) }, { r -> ConfigUpgradeSetXdr.decode(r) })
    }

    // ---- ConfigSettingEntry union (all cases) ----

    @Test fun testConfigSettingEntryContractMaxSizeBytes() {
        val v = ConfigSettingEntryXdr.ContractMaxSizeBytes(Uint32Xdr(65536u))
        XdrTestHelpers.assertXdrRoundTrip(v, { a, w -> a.encode(w) }, { r -> ConfigSettingEntryXdr.decode(r) })
    }

    @Test fun testConfigSettingEntryContractCompute() {
        val v = ConfigSettingEntryXdr.ContractCompute(
            ConfigSettingContractComputeV0Xdr(Int64Xdr(1L), Int64Xdr(2L), Int64Xdr(3L), Uint32Xdr(4u))
        )
        XdrTestHelpers.assertXdrRoundTrip(v, { a, w -> a.encode(w) }, { r -> ConfigSettingEntryXdr.decode(r) })
    }

    @Test fun testConfigSettingEntryContractLedgerCost() {
        val cost = ConfigSettingContractLedgerCostV0Xdr(
            Uint32Xdr(1u), Uint32Xdr(2u), Uint32Xdr(3u), Uint32Xdr(4u),
            Uint32Xdr(5u), Uint32Xdr(6u), Uint32Xdr(7u), Uint32Xdr(8u),
            Int64Xdr(9L), Int64Xdr(10L), Int64Xdr(11L), Int64Xdr(12L),
            Int64Xdr(13L), Int64Xdr(14L), Uint32Xdr(15u)
        )
        val v = ConfigSettingEntryXdr.ContractLedgerCost(cost)
        XdrTestHelpers.assertXdrRoundTrip(v, { a, w -> a.encode(w) }, { r -> ConfigSettingEntryXdr.decode(r) })
    }

    @Test fun testConfigSettingEntryContractHistoricalData() {
        val v = ConfigSettingEntryXdr.ContractHistoricalData(ConfigSettingContractHistoricalDataV0Xdr(Int64Xdr(100L)))
        XdrTestHelpers.assertXdrRoundTrip(v, { a, w -> a.encode(w) }, { r -> ConfigSettingEntryXdr.decode(r) })
    }

    @Test fun testConfigSettingEntryContractEvents() {
        val v = ConfigSettingEntryXdr.ContractEvents(ConfigSettingContractEventsV0Xdr(Uint32Xdr(8192u), Int64Xdr(200L)))
        XdrTestHelpers.assertXdrRoundTrip(v, { a, w -> a.encode(w) }, { r -> ConfigSettingEntryXdr.decode(r) })
    }

    @Test fun testConfigSettingEntryContractBandwidth() {
        val v = ConfigSettingEntryXdr.ContractBandwidth(ConfigSettingContractBandwidthV0Xdr(Uint32Xdr(1000u), Uint32Xdr(500u), Int64Xdr(100L)))
        XdrTestHelpers.assertXdrRoundTrip(v, { a, w -> a.encode(w) }, { r -> ConfigSettingEntryXdr.decode(r) })
    }

    @Test fun testConfigSettingEntryContractCostParamsCpuInsns() {
        val params = ContractCostParamsXdr(listOf(ContractCostParamEntryXdr(ExtensionPointXdr.Void, Int64Xdr(1L), Int64Xdr(2L))))
        val v = ConfigSettingEntryXdr.ContractCostParamsCpuInsns(params)
        XdrTestHelpers.assertXdrRoundTrip(v, { a, w -> a.encode(w) }, { r -> ConfigSettingEntryXdr.decode(r) })
    }

    @Test fun testConfigSettingEntryContractCostParamsMemBytes() {
        val params = ContractCostParamsXdr(listOf(ContractCostParamEntryXdr(ExtensionPointXdr.Void, Int64Xdr(3L), Int64Xdr(4L))))
        val v = ConfigSettingEntryXdr.ContractCostParamsMemBytes(params)
        XdrTestHelpers.assertXdrRoundTrip(v, { a, w -> a.encode(w) }, { r -> ConfigSettingEntryXdr.decode(r) })
    }

    @Test fun testConfigSettingEntryContractDataKeySizeBytes() {
        val v = ConfigSettingEntryXdr.ContractDataKeySizeBytes(Uint32Xdr(200u))
        XdrTestHelpers.assertXdrRoundTrip(v, { a, w -> a.encode(w) }, { r -> ConfigSettingEntryXdr.decode(r) })
    }

    @Test fun testConfigSettingEntryContractDataEntrySizeBytes() {
        val v = ConfigSettingEntryXdr.ContractDataEntrySizeBytes(Uint32Xdr(65536u))
        XdrTestHelpers.assertXdrRoundTrip(v, { a, w -> a.encode(w) }, { r -> ConfigSettingEntryXdr.decode(r) })
    }

    @Test fun testConfigSettingEntryStateArchival() {
        val settings = StateArchivalSettingsXdr(
            Uint32Xdr(1u), Uint32Xdr(2u), Uint32Xdr(3u), Int64Xdr(4L), Int64Xdr(5L),
            Uint32Xdr(6u), Uint32Xdr(7u), Uint32Xdr(8u), Uint32Xdr(9u), Uint32Xdr(10u)
        )
        val v = ConfigSettingEntryXdr.StateArchivalSettings(settings)
        XdrTestHelpers.assertXdrRoundTrip(v, { a, w -> a.encode(w) }, { r -> ConfigSettingEntryXdr.decode(r) })
    }

    @Test fun testConfigSettingEntryContractExecutionLanes() {
        val v = ConfigSettingEntryXdr.ContractExecutionLanes(ConfigSettingContractExecutionLanesV0Xdr(Uint32Xdr(100u)))
        XdrTestHelpers.assertXdrRoundTrip(v, { a, w -> a.encode(w) }, { r -> ConfigSettingEntryXdr.decode(r) })
    }

    @Test fun testConfigSettingEntryLiveSorobanStateSizeWindow() {
        val v = ConfigSettingEntryXdr.LiveSorobanStateSizeWindow(listOf(Uint64Xdr(100UL), Uint64Xdr(200UL)))
        XdrTestHelpers.assertXdrRoundTrip(v, { a, w -> a.encode(w) }, { r -> ConfigSettingEntryXdr.decode(r) })
    }

    @Test fun testConfigSettingEntryEvictionIterator() {
        val v = ConfigSettingEntryXdr.EvictionIterator(EvictionIteratorXdr(Uint32Xdr(5u), false, Uint64Xdr(0UL)))
        XdrTestHelpers.assertXdrRoundTrip(v, { a, w -> a.encode(w) }, { r -> ConfigSettingEntryXdr.decode(r) })
    }

    @Test fun testConfigSettingEntryContractParallelCompute() {
        val v = ConfigSettingEntryXdr.ContractParallelCompute(ConfigSettingContractParallelComputeV0Xdr(Uint32Xdr(8u)))
        XdrTestHelpers.assertXdrRoundTrip(v, { a, w -> a.encode(w) }, { r -> ConfigSettingEntryXdr.decode(r) })
    }

    @Test fun testConfigSettingEntryContractLedgerCostExt() {
        val v = ConfigSettingEntryXdr.ContractLedgerCostExt(ConfigSettingContractLedgerCostExtV0Xdr(Uint32Xdr(50u), Int64Xdr(4000L)))
        XdrTestHelpers.assertXdrRoundTrip(v, { a, w -> a.encode(w) }, { r -> ConfigSettingEntryXdr.decode(r) })
    }

    @Test fun testConfigSettingEntryContractSCPTiming() {
        val v = ConfigSettingEntryXdr.ContractSCPTiming(
            ConfigSettingSCPTimingXdr(Uint32Xdr(5000u), Uint32Xdr(1000u), Uint32Xdr(500u), Uint32Xdr(1000u), Uint32Xdr(500u))
        )
        XdrTestHelpers.assertXdrRoundTrip(v, { a, w -> a.encode(w) }, { r -> ConfigSettingEntryXdr.decode(r) })
    }
}
