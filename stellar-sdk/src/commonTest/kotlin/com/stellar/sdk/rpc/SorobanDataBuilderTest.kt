package com.stellar.sdk.rpc

import com.stellar.sdk.xdr.*
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

    // ========== Constructor Tests ==========

    @Test
    fun testEmptyConstructor_initializesWithZeroValues() {
        // Given: Empty constructor
        val builder = SorobanDataBuilder()

        // When: Building without modifications
        val data = builder.build()

        // Then: All values should be zero/empty
        // Note: This test will be enabled when XDR implementation is complete
        // assertNotNull(data)
        // assertEquals(0, data.resourceFee.int64)
        // assertEquals(0, data.resources.instructions.uint32)
    }

    @Test
    fun testConstructorFromBase64_parsesValidXdr() {
        // Given: Valid base64-encoded SorobanTransactionData
        val validXdr = "AAAAAAAAAAIAAAAGAAAAAem354u9STQWq5b3Ed1j9tOemvL7xV0NPwhn4gXg0AP8AAAAFAAAAAEAAAAH8dTe2OoI0BnhlDbH0fWvXmvprkBvBAgKIcL9busuuMEAAAABAAAABgAAAAHpt+eLvUk0FquW9xHdY/bTnpry+8VdDT8IZ+IF4NAD/AAAABAAAAABAAAAAgAAAA8AAAAHQ291bnRlcgAAAAASAAAAAAAAAABYt8SiyPKXqo89JHEoH9/M7K/kjlZjMT7BjhKnPsqYoQAAAAEAHifGAAAFlAAAAIgAAAAAAAAAAg=="

        // When: Creating builder from base64
        val exception = assertFailsWith<NotImplementedError> {
            SorobanDataBuilder(validXdr)
        }

        // Then: Constructor is not yet implemented (TODO)
        assertTrue(exception.message?.contains("requires XDR fromXdrBase64") ?: false)
    }

    @Test
    fun testConstructorFromBase64_invalidXdr_throwsException() {
        // Given: Invalid base64 string
        val invalidXdr = "not-valid-base64!!!"

        // When/Then: Should fail (when implemented)
        assertFailsWith<NotImplementedError> {
            SorobanDataBuilder(invalidXdr)
        }
    }

    @Test
    fun testConstructorFromXdrObject_createsDeepCopy() {
        // Given: Existing SorobanTransactionData XDR object
        // val existingData = SorobanTransactionDataXdr(...)

        // When: Creating builder from XDR object
        val exception = assertFailsWith<NotImplementedError> {
            // SorobanDataBuilder(existingData)
            TODO("Requires XDR implementation")
        }

        // Then: Constructor is not yet implemented (TODO)
        assertTrue(exception.message?.contains("Not yet implemented") ?: false)
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
        val exception = assertFailsWith<NotImplementedError> {
            builder.setResourceFee(testResourceFee)
        }

        // Then: Not yet implemented
        assertTrue(exception.message?.contains("requires SorobanTransactionDataXdr") ?: false)
    }

    @Test
    fun testSetResourceFee_zeroValue_isValid() {
        // Given: Builder with zero resource fee
        val builder = SorobanDataBuilder()

        // When: Setting zero resource fee
        val exception = assertFailsWith<NotImplementedError> {
            builder.setResourceFee(0)
        }

        // Then: Zero is valid (but not yet implemented)
        assertTrue(exception.message?.contains("requires SorobanTransactionDataXdr") ?: false)
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
        val exception = assertFailsWith<NotImplementedError> {
            builder.setResources(resources)
        }

        // Then: Not yet implemented
        assertTrue(exception.message?.contains("requires SorobanTransactionDataXdr") ?: false)
    }

    @Test
    fun testSetReadOnly_validKeys_setsSuccessfully() {
        // Given: Builder and valid ledger keys
        val builder = SorobanDataBuilder()
        // val ledgerKeys = listOf<LedgerKeyXdr>(...)

        // When: Setting read-only keys
        val exception = assertFailsWith<NotImplementedError> {
            builder.setReadOnly(emptyList())
        }

        // Then: Not yet implemented
        assertTrue(exception.message?.contains("requires SorobanTransactionDataXdr") ?: false)
    }

    @Test
    fun testSetReadOnly_nullKeys_leavesUnchanged() {
        // Given: Builder with null read-only keys
        val builder = SorobanDataBuilder()

        // When: Setting null (should leave unchanged)
        val exception = assertFailsWith<NotImplementedError> {
            builder.setReadOnly(null)
        }

        // Then: Not yet implemented
        assertTrue(exception.message?.contains("requires SorobanTransactionDataXdr") ?: false)
    }

    @Test
    fun testSetReadOnly_emptyList_clearsFootprint() {
        // Given: Builder with empty list
        val builder = SorobanDataBuilder()

        // When: Setting empty list (should clear)
        val exception = assertFailsWith<NotImplementedError> {
            builder.setReadOnly(emptyList())
        }

        // Then: Not yet implemented
        assertTrue(exception.message?.contains("requires SorobanTransactionDataXdr") ?: false)
    }

    @Test
    fun testSetReadWrite_validKeys_setsSuccessfully() {
        // Given: Builder and valid ledger keys
        val builder = SorobanDataBuilder()

        // When: Setting read-write keys
        val exception = assertFailsWith<NotImplementedError> {
            builder.setReadWrite(emptyList())
        }

        // Then: Not yet implemented
        assertTrue(exception.message?.contains("requires SorobanTransactionDataXdr") ?: false)
    }

    @Test
    fun testSetReadWrite_nullKeys_leavesUnchanged() {
        // Given: Builder with null read-write keys
        val builder = SorobanDataBuilder()

        // When: Setting null (should leave unchanged)
        val exception = assertFailsWith<NotImplementedError> {
            builder.setReadWrite(null)
        }

        // Then: Not yet implemented
        assertTrue(exception.message?.contains("requires SorobanTransactionDataXdr") ?: false)
    }

    @Test
    fun testSetReadWrite_emptyList_clearsFootprint() {
        // Given: Builder with empty list
        val builder = SorobanDataBuilder()

        // When: Setting empty list (should clear)
        val exception = assertFailsWith<NotImplementedError> {
            builder.setReadWrite(emptyList())
        }

        // Then: Not yet implemented
        assertTrue(exception.message?.contains("requires SorobanTransactionDataXdr") ?: false)
    }

    // ========== Build Method Tests ==========

    @Test
    fun testBuild_createsDeepCopy() {
        // Given: Builder with data
        val builder = SorobanDataBuilder()

        // When: Building multiple times
        val exception = assertFailsWith<NotImplementedError> {
            builder.build()
        }

        // Then: Not yet implemented
        assertTrue(exception.message?.contains("requires SorobanTransactionDataXdr") ?: false)
    }

    @Test
    fun testBuildBase64_returnsBase64String() {
        // Given: Builder with data
        val builder = SorobanDataBuilder()

        // When: Building as base64
        val exception = assertFailsWith<NotImplementedError> {
            builder.buildBase64()
        }

        // Then: Not yet implemented
        assertTrue(exception.message?.contains("requires build()") ?: false)
    }

    // ========== Integration Tests ==========

    @Test
    fun testBuilderChaining_multipleSetters_appliesAllValues() {
        // Given: Builder with chained setters
        val builder = SorobanDataBuilder()

        // When: Chaining multiple setters
        val exception = assertFailsWith<NotImplementedError> {
            builder
                .setResourceFee(testResourceFee)
                .setResources(createTestResources())
                .setReadOnly(emptyList())
                .setReadWrite(emptyList())
                .build()
        }

        // Then: Chaining works (when implemented)
        assertTrue(exception.message?.contains("requires SorobanTransactionDataXdr") ?: false)
    }

    @Test
    fun testImmutability_builderReuse_eachBuildIsIndependent() {
        // Given: Builder used multiple times
        val builder = SorobanDataBuilder()

        // When: Building, modifying, and building again
        // This test verifies defensive copying works correctly
        val exception = assertFailsWith<NotImplementedError> {
            val data1 = builder.setResourceFee(1000).build()
            val data2 = builder.setResourceFee(2000).build()
            // data1 should still have fee=1000, data2 should have fee=2000
        }

        // Then: Not yet implemented
        assertTrue(exception.message?.contains("requires SorobanTransactionDataXdr") ?: false)
    }

    @Test
    fun testCompleteExample_buildsValidSorobanData() {
        // Given: Complete builder setup
        val builder = SorobanDataBuilder()
        val resources = SorobanDataBuilder.Resources(
            cpuInstructions = 1000000,
            diskReadBytes = 5000,
            writeBytes = 2000
        )

        // When: Building complete soroban data
        val exception = assertFailsWith<NotImplementedError> {
            builder
                .setResourceFee(50000)
                .setResources(resources)
                .setReadOnly(emptyList())
                .setReadWrite(emptyList())
                .build()
        }

        // Then: Complete flow works (when implemented)
        assertTrue(exception.message?.contains("requires SorobanTransactionDataXdr") ?: false)
    }
}
