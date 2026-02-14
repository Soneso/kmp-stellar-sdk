package com.soneso.stellar.sdk.unitTests.rpc

import com.soneso.stellar.sdk.rpc.*
import com.soneso.stellar.sdk.xdr.*
import kotlin.test.*

/**
 * Comprehensive tests for [SorobanDataBuilder].
 *
 * Tests all builder methods, constructors, validation, and edge cases.
 * Reference: Java SDK SorobanDataBuilder tests
 */
class SorobanDataBuilderTest {

    // Test data constants
    private val testResourceFee = 50000L
    private val testCpuInstructions = 1000000L
    private val testDiskReadBytes = 5000L
    private val testWriteBytes = 2000L

    // Helper to create test Resources
    private fun createTestResources(
        cpu: Long = testCpuInstructions,
        read: Long = testDiskReadBytes,
        write: Long = testWriteBytes
    ) = SorobanDataBuilder.Resources(cpu, read, write)

    // Helper to create a test ledger key (contract instance)
    private fun createTestLedgerKey(): LedgerKeyXdr {
        // Create a simple contract instance ledger key for testing
        val contractAddress = SCAddressXdr.ContractId(
            ContractIDXdr(HashXdr(ByteArray(32) { it.toByte() }))
        )
        return LedgerKeyXdr.ContractData(
            LedgerKeyContractDataXdr(
                contract = contractAddress,
                key = SCValXdr.U32(Uint32Xdr(0u)),
                durability = ContractDataDurabilityXdr.PERSISTENT
            )
        )
    }

    // ========== Constructor Tests ==========

    @Test
    fun testEmptyConstructor_initializesWithZeroValues() {
        // Given: Empty constructor
        val builder = SorobanDataBuilder()

        // When: Building without modifications
        val data = builder.build()

        // Then: All values should be zero/empty
        assertNotNull(data)
        assertEquals(0L, data.resourceFee.value)
        assertEquals(0u, data.resources.instructions.value)
        assertEquals(0u, data.resources.diskReadBytes.value)
        assertEquals(0u, data.resources.writeBytes.value)
        assertTrue(data.resources.footprint.readOnly.isEmpty())
        assertTrue(data.resources.footprint.readWrite.isEmpty())
        assertEquals(SorobanTransactionDataExtXdr.Void, data.ext)
    }

    @Test
    fun testConstructorFromBase64_parsesValidXdr() {
        // Given: Create initial data and encode it
        val originalData = SorobanDataBuilder()
            .setResourceFee(12345L)
            .setResources(createTestResources(100000, 200, 300))
            .build()
        val base64 = originalData.toXdrBase64()

        // When: Creating builder from base64
        val builder = SorobanDataBuilder(base64)
        val decodedData = builder.build()

        // Then: Data should match original
        assertEquals(12345L, decodedData.resourceFee.value)
        assertEquals(100000u, decodedData.resources.instructions.value)
        assertEquals(200u, decodedData.resources.diskReadBytes.value)
        assertEquals(300u, decodedData.resources.writeBytes.value)
    }

    @Test
    fun testConstructorFromBase64_invalidXdr_throwsException() {
        // Given: Invalid base64 string
        val invalidXdr = "not-valid-base64!!!"

        // When/Then: Should throw IllegalArgumentException
        val exception = assertFailsWith<IllegalArgumentException> {
            SorobanDataBuilder(invalidXdr)
        }

        assertTrue(exception.message?.contains("Invalid SorobanData") ?: false)
    }

    @Test
    fun testConstructorFromXdrObject_createsDeepCopy() {
        // Given: Existing SorobanTransactionData XDR object
        val original = SorobanDataBuilder()
            .setResourceFee(99999L)
            .setResources(createTestResources(50000, 100, 200))
            .build()

        // When: Creating builder from XDR object
        val builder = SorobanDataBuilder(original)
        val copy = builder.build()

        // Then: Should be a deep copy with same values
        assertEquals(original.resourceFee.value, copy.resourceFee.value)
        assertEquals(original.resources.instructions.value, copy.resources.instructions.value)
        assertEquals(original.resources.diskReadBytes.value, copy.resources.diskReadBytes.value)
        assertEquals(original.resources.writeBytes.value, copy.resources.writeBytes.value)

        // Verify it's actually a copy by modifying the builder
        val modified = builder.setResourceFee(11111L).build()
        assertEquals(99999L, copy.resourceFee.value) // Original copy unchanged
        assertEquals(11111L, modified.resourceFee.value) // New build has new value
    }

    // ========== Resources Tests ==========

    @Test
    fun testResources_validValues_createsSuccessfully() {
        // Given: Valid resource values
        val resources = createTestResources()

        // Then: Resources created successfully
        assertEquals(testCpuInstructions, resources.cpuInstructions)
        assertEquals(testDiskReadBytes, resources.diskReadBytes)
        assertEquals(testWriteBytes, resources.writeBytes)
    }

    @Test
    fun testResources_zeroValues_isValid() {
        // Given: Zero values for all resources
        val resources = SorobanDataBuilder.Resources(
            cpuInstructions = 0,
            diskReadBytes = 0,
            writeBytes = 0
        )

        // Then: All zeros are valid
        assertEquals(0, resources.cpuInstructions)
        assertEquals(0, resources.diskReadBytes)
        assertEquals(0, resources.writeBytes)
    }

    @Test
    fun testResources_maxUint32Values_isValid() {
        // Given: Maximum uint32 values
        val maxUint32 = 0xFFFFFFFFL
        val resources = SorobanDataBuilder.Resources(
            cpuInstructions = maxUint32,
            diskReadBytes = maxUint32,
            writeBytes = maxUint32
        )

        // Then: Max values are accepted
        assertEquals(maxUint32, resources.cpuInstructions)
        assertEquals(maxUint32, resources.diskReadBytes)
        assertEquals(maxUint32, resources.writeBytes)
    }

    @Test
    fun testResources_negativeCpuInstructions_throwsException() {
        // Given: Negative CPU instructions
        val exception = assertFailsWith<IllegalArgumentException> {
            SorobanDataBuilder.Resources(
                cpuInstructions = -1,
                diskReadBytes = 1000,
                writeBytes = 1000
            )
        }

        // Then: Exception message mentions CPU instructions
        assertTrue(exception.message?.contains("CPU instructions") ?: false)
        assertTrue(exception.message?.contains("non-negative") ?: false)
    }

    @Test
    fun testResources_negativeDiskReadBytes_throwsException() {
        // Given: Negative disk read bytes
        val exception = assertFailsWith<IllegalArgumentException> {
            SorobanDataBuilder.Resources(
                cpuInstructions = 1000,
                diskReadBytes = -1,
                writeBytes = 1000
            )
        }

        // Then: Exception message mentions disk read bytes
        assertTrue(exception.message?.contains("Disk read bytes") ?: false)
        assertTrue(exception.message?.contains("non-negative") ?: false)
    }

    @Test
    fun testResources_negativeWriteBytes_throwsException() {
        // Given: Negative write bytes
        val exception = assertFailsWith<IllegalArgumentException> {
            SorobanDataBuilder.Resources(
                cpuInstructions = 1000,
                diskReadBytes = 1000,
                writeBytes = -1
            )
        }

        // Then: Exception message mentions write bytes
        assertTrue(exception.message?.contains("Write bytes") ?: false)
        assertTrue(exception.message?.contains("non-negative") ?: false)
    }

    @Test
    fun testResources_cpuInstructionsExceedsUint32_throwsException() {
        // Given: CPU instructions exceeding uint32 max
        val tooLarge = 0x100000000L // 2^32
        val exception = assertFailsWith<IllegalArgumentException> {
            SorobanDataBuilder.Resources(
                cpuInstructions = tooLarge,
                diskReadBytes = 1000,
                writeBytes = 1000
            )
        }

        // Then: Exception message mentions uint32 limit
        assertTrue(exception.message?.contains("CPU instructions") ?: false)
        assertTrue(exception.message?.contains("uint32") ?: false)
    }

    @Test
    fun testResources_diskReadBytesExceedsUint32_throwsException() {
        // Given: Disk read bytes exceeding uint32 max
        val tooLarge = 0x100000000L // 2^32
        val exception = assertFailsWith<IllegalArgumentException> {
            SorobanDataBuilder.Resources(
                cpuInstructions = 1000,
                diskReadBytes = tooLarge,
                writeBytes = 1000
            )
        }

        // Then: Exception message mentions uint32 limit
        assertTrue(exception.message?.contains("Disk read bytes") ?: false)
        assertTrue(exception.message?.contains("uint32") ?: false)
    }

    @Test
    fun testResources_writeBytesExceedsUint32_throwsException() {
        // Given: Write bytes exceeding uint32 max
        val tooLarge = 0x100000000L // 2^32
        val exception = assertFailsWith<IllegalArgumentException> {
            SorobanDataBuilder.Resources(
                cpuInstructions = 1000,
                diskReadBytes = 1000,
                writeBytes = tooLarge
            )
        }

        // Then: Exception message mentions uint32 limit
        assertTrue(exception.message?.contains("Write bytes") ?: false)
        assertTrue(exception.message?.contains("uint32") ?: false)
    }

    // ========== Builder Method Tests ==========

    @Test
    fun testSetResourceFee_validValue_setsSuccessfully() {
        // Given: Builder with valid resource fee
        val builder = SorobanDataBuilder()

        // When: Setting resource fee
        builder.setResourceFee(testResourceFee)
        val data = builder.build()

        // Then: Resource fee is set correctly
        assertEquals(testResourceFee, data.resourceFee.value)
    }

    @Test
    fun testSetResourceFee_zeroValue_isValid() {
        // Given: Builder with zero resource fee
        val builder = SorobanDataBuilder()

        // When: Setting zero resource fee
        builder.setResourceFee(0)
        val data = builder.build()

        // Then: Zero is valid
        assertEquals(0L, data.resourceFee.value)
    }

    @Test
    fun testSetResourceFee_negativeValue_throwsException() {
        // Given: Builder with negative resource fee
        val builder = SorobanDataBuilder()

        // When/Then: Should throw IllegalArgumentException
        val exception = assertFailsWith<IllegalArgumentException> {
            builder.setResourceFee(-1)
        }

        assertTrue(exception.message?.contains("Resource fee") ?: false)
        assertTrue(exception.message?.contains("non-negative") ?: false)
    }

    @Test
    fun testSetResources_validResources_setsSuccessfully() {
        // Given: Builder and valid resources
        val builder = SorobanDataBuilder()
        val resources = createTestResources()

        // When: Setting resources
        builder.setResources(resources)
        val data = builder.build()

        // Then: Resources are set correctly
        assertEquals(testCpuInstructions.toUInt(), data.resources.instructions.value)
        assertEquals(testDiskReadBytes.toUInt(), data.resources.diskReadBytes.value)
        assertEquals(testWriteBytes.toUInt(), data.resources.writeBytes.value)
    }

    @Test
    fun testSetReadOnly_validKeys_setsSuccessfully() {
        // Given: Builder and valid ledger keys
        val builder = SorobanDataBuilder()
        val ledgerKeys = listOf(createTestLedgerKey())

        // When: Setting read-only keys
        builder.setReadOnly(ledgerKeys)
        val data = builder.build()

        // Then: Read-only footprint is set correctly
        assertEquals(1, data.resources.footprint.readOnly.size)
    }

    @Test
    fun testSetReadOnly_nullKeys_leavesUnchanged() {
        // Given: Builder with existing read-only keys
        val builder = SorobanDataBuilder()
        val originalKey = createTestLedgerKey()
        builder.setReadOnly(listOf(originalKey))

        // When: Setting null (should leave unchanged)
        builder.setReadOnly(null)
        val data = builder.build()

        // Then: Original keys are preserved
        assertEquals(1, data.resources.footprint.readOnly.size)
    }

    @Test
    fun testSetReadOnly_emptyList_clearsFootprint() {
        // Given: Builder with existing read-only keys
        val builder = SorobanDataBuilder()
        builder.setReadOnly(listOf(createTestLedgerKey()))

        // When: Setting empty list (should clear)
        builder.setReadOnly(emptyList())
        val data = builder.build()

        // Then: Footprint is cleared
        assertTrue(data.resources.footprint.readOnly.isEmpty())
    }

    @Test
    fun testSetReadWrite_validKeys_setsSuccessfully() {
        // Given: Builder and valid ledger keys
        val builder = SorobanDataBuilder()
        val ledgerKeys = listOf(createTestLedgerKey())

        // When: Setting read-write keys
        builder.setReadWrite(ledgerKeys)
        val data = builder.build()

        // Then: Read-write footprint is set correctly
        assertEquals(1, data.resources.footprint.readWrite.size)
    }

    @Test
    fun testSetReadWrite_nullKeys_leavesUnchanged() {
        // Given: Builder with existing read-write keys
        val builder = SorobanDataBuilder()
        val originalKey = createTestLedgerKey()
        builder.setReadWrite(listOf(originalKey))

        // When: Setting null (should leave unchanged)
        builder.setReadWrite(null)
        val data = builder.build()

        // Then: Original keys are preserved
        assertEquals(1, data.resources.footprint.readWrite.size)
    }

    @Test
    fun testSetReadWrite_emptyList_clearsFootprint() {
        // Given: Builder with existing read-write keys
        val builder = SorobanDataBuilder()
        builder.setReadWrite(listOf(createTestLedgerKey()))

        // When: Setting empty list (should clear)
        builder.setReadWrite(emptyList())
        val data = builder.build()

        // Then: Footprint is cleared
        assertTrue(data.resources.footprint.readWrite.isEmpty())
    }

    // ========== Build Method Tests ==========

    @Test
    fun testBuild_createsDeepCopy() {
        // Given: Builder with data
        val builder = SorobanDataBuilder()
            .setResourceFee(1000)

        // When: Building multiple times
        val data1 = builder.build()
        val data2 = builder.setResourceFee(2000).build()

        // Then: Each build is independent
        assertEquals(1000L, data1.resourceFee.value)
        assertEquals(2000L, data2.resourceFee.value)
    }

    @Test
    fun testBuildBase64_returnsBase64String() {
        // Given: Builder with data
        val builder = SorobanDataBuilder()
            .setResourceFee(1000)
            .setResources(createTestResources())

        // When: Building as base64
        val base64 = builder.buildBase64()

        // Then: Should be valid base64 that can be decoded
        assertNotNull(base64)
        assertTrue(base64.isNotEmpty())

        // Verify it can be decoded back
        val decoded = SorobanDataBuilder(base64).build()
        assertEquals(1000L, decoded.resourceFee.value)
        assertEquals(testCpuInstructions.toUInt(), decoded.resources.instructions.value)
    }

    // ========== Integration Tests ==========

    @Test
    fun testBuilderChaining_multipleSetters_appliesAllValues() {
        // Given: Builder with chained setters
        val resources = createTestResources()
        val readOnlyKeys = listOf(createTestLedgerKey())
        val readWriteKeys = listOf(createTestLedgerKey())

        // When: Chaining multiple setters
        val data = SorobanDataBuilder()
            .setResourceFee(testResourceFee)
            .setResources(resources)
            .setReadOnly(readOnlyKeys)
            .setReadWrite(readWriteKeys)
            .build()

        // Then: All values are applied correctly
        assertEquals(testResourceFee, data.resourceFee.value)
        assertEquals(testCpuInstructions.toUInt(), data.resources.instructions.value)
        assertEquals(testDiskReadBytes.toUInt(), data.resources.diskReadBytes.value)
        assertEquals(testWriteBytes.toUInt(), data.resources.writeBytes.value)
        assertEquals(1, data.resources.footprint.readOnly.size)
        assertEquals(1, data.resources.footprint.readWrite.size)
    }

    @Test
    fun testImmutability_builderReuse_eachBuildIsIndependent() {
        // Given: Builder used multiple times
        val builder = SorobanDataBuilder()

        // When: Building, modifying, and building again
        val data1 = builder.setResourceFee(1000).build()
        val data2 = builder.setResourceFee(2000).build()
        val data3 = builder.setResourceFee(3000).build()

        // Then: Each build is independent
        assertEquals(1000L, data1.resourceFee.value)
        assertEquals(2000L, data2.resourceFee.value)
        assertEquals(3000L, data3.resourceFee.value)
    }

    @Test
    fun testCompleteExample_buildsValidSorobanData() {
        // Given: Complete builder setup
        val resources = SorobanDataBuilder.Resources(
            cpuInstructions = 1000000,
            diskReadBytes = 5000,
            writeBytes = 2000
        )
        val readOnlyKey = createTestLedgerKey()
        val readWriteKey = createTestLedgerKey()

        // When: Building complete soroban data
        val data = SorobanDataBuilder()
            .setResourceFee(50000)
            .setResources(resources)
            .setReadOnly(listOf(readOnlyKey))
            .setReadWrite(listOf(readWriteKey))
            .build()

        // Then: All values are correct
        assertNotNull(data)
        assertEquals(50000L, data.resourceFee.value)
        assertEquals(1000000u, data.resources.instructions.value)
        assertEquals(5000u, data.resources.diskReadBytes.value)
        assertEquals(2000u, data.resources.writeBytes.value)
        assertEquals(1, data.resources.footprint.readOnly.size)
        assertEquals(1, data.resources.footprint.readWrite.size)
    }

    @Test
    fun testRoundTrip_base64EncodingDecoding() {
        // Given: Complete soroban data
        val original = SorobanDataBuilder()
            .setResourceFee(12345L)
            .setResources(createTestResources(100000, 2000, 3000))
            .setReadOnly(listOf(createTestLedgerKey()))
            .build()

        // When: Encoding to base64 and decoding back
        val base64 = original.toXdrBase64()
        val decoded = SorobanTransactionDataXdr.fromXdrBase64(base64)

        // Then: Decoded data matches original
        assertEquals(original.resourceFee.value, decoded.resourceFee.value)
        assertEquals(original.resources.instructions.value, decoded.resources.instructions.value)
        assertEquals(original.resources.diskReadBytes.value, decoded.resources.diskReadBytes.value)
        assertEquals(original.resources.writeBytes.value, decoded.resources.writeBytes.value)
        assertEquals(original.resources.footprint.readOnly.size, decoded.resources.footprint.readOnly.size)
    }

    @Test
    fun testModifyExistingData_preservesUnmodifiedFields() {
        // Given: Existing soroban data with all fields set
        val original = SorobanDataBuilder()
            .setResourceFee(10000)
            .setResources(createTestResources())
            .setReadOnly(listOf(createTestLedgerKey()))
            .setReadWrite(listOf(createTestLedgerKey()))
            .build()

        // When: Modifying only the resource fee
        val modified = SorobanDataBuilder(original)
            .setResourceFee(20000)
            .build()

        // Then: Only resource fee changed, other fields preserved
        assertEquals(20000L, modified.resourceFee.value)
        assertEquals(original.resources.instructions.value, modified.resources.instructions.value)
        assertEquals(original.resources.diskReadBytes.value, modified.resources.diskReadBytes.value)
        assertEquals(original.resources.writeBytes.value, modified.resources.writeBytes.value)
        assertEquals(original.resources.footprint.readOnly.size, modified.resources.footprint.readOnly.size)
        assertEquals(original.resources.footprint.readWrite.size, modified.resources.footprint.readWrite.size)
    }
}
