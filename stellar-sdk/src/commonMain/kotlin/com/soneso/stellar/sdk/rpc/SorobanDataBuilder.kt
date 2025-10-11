package com.soneso.stellar.sdk.rpc

import com.soneso.stellar.sdk.xdr.*

/**
 * Builder for constructing [SorobanTransactionDataXdr] structures.
 *
 * SorobanTransactionData contains the resource footprint and fee information required
 * for Soroban smart contract transactions. This builder provides a fluent API for
 * constructing and modifying these structures.
 *
 * ## Use Cases
 *
 * This builder is particularly useful for:
 * - Building [com.soneso.stellar.sdk.operations.ExtendFootprintTTLOperation] transactions
 * - Building [com.soneso.stellar.sdk.operations.RestoreFootprintOperation] transactions
 * - Manually constructing transaction footprints for testing
 * - Modifying simulation results before transaction submission
 *
 * For most Soroban transactions, you should use [SorobanServer.prepareTransaction] which
 * automatically populates the SorobanTransactionData from simulation results.
 *
 * ## Structure
 *
 * SorobanTransactionData consists of:
 * - **Resource Fee**: Additional fee for resource consumption (beyond base transaction fee)
 * - **Resources**: CPU, memory, and storage metrics
 *   - CPU Instructions: Number of WASM instructions to execute
 *   - Disk Read Bytes: Bytes read from ledger storage
 *   - Write Bytes: Bytes written to ledger storage and memory
 * - **Footprint**: Ledger keys accessed by the transaction
 *   - Read-Only: Keys read but not modified
 *   - Read-Write: Keys both read and modified
 *
 * ## Basic Usage
 *
 * ```kotlin
 * // Start with empty data
 * val builder = SorobanDataBuilder()
 *     .setResourceFee(50000)
 *     .setResources(SorobanDataBuilder.Resources(
 *         cpuInstructions = 1000000,
 *         diskReadBytes = 5000,
 *         writeBytes = 2000
 *     ))
 *     .setReadOnly(listOf(ledgerKey1))
 *     .setReadWrite(listOf(ledgerKey2))
 *
 * val sorobanData = builder.build()
 * ```
 *
 * ## From Simulation Results
 *
 * ```kotlin
 * // Start with simulation results
 * val simulation = server.simulateTransaction(tx)
 * val builder = SorobanDataBuilder(simulation.transactionData!!)
 *     .setResourceFee(simulation.minResourceFee!! + 10000) // Add buffer
 *
 * val sorobanData = builder.build()
 * ```
 *
 * ## Modifying Existing Data
 *
 * ```kotlin
 * // Modify existing soroban data
 * val updated = SorobanDataBuilder(transaction.sorobanData!!)
 *     .setReadWrite(listOf(additionalKey)) // Replace read-write keys
 *     .build()
 * ```
 *
 * ## Immutability
 *
 * The builder uses defensive copying to ensure immutability:
 * - Each setter returns a new builder instance
 * - The [build] method returns a deep copy of the data
 * - Original data structures are never mutated
 *
 * @see SorobanServer.prepareTransaction
 * @see com.soneso.stellar.sdk.operations.ExtendFootprintTTLOperation
 * @see com.soneso.stellar.sdk.operations.RestoreFootprintOperation
 * @see <a href="https://developers.stellar.org/docs/learn/smart-contract-internals/persisting-data">Soroban Data documentation</a>
 */
class SorobanDataBuilder {
    /**
     * The internal SorobanTransactionData being built.
     * This is mutable and copied on [build].
     */
    private var data: SorobanTransactionDataXdr

    /**
     * Creates a new builder with empty SorobanTransactionData.
     *
     * Initializes with:
     * - Resource fee: 0
     * - CPU instructions: 0
     * - Disk read bytes: 0
     * - Write bytes: 0
     * - Empty read-only footprint
     * - Empty read-write footprint
     */
    constructor() {
        data = SorobanTransactionDataXdr(
            ext = SorobanTransactionDataExtXdr.Void,
            resources = SorobanResourcesXdr(
                footprint = LedgerFootprintXdr(
                    readOnly = emptyList(),
                    readWrite = emptyList()
                ),
                instructions = Uint32Xdr(0u),
                diskReadBytes = Uint32Xdr(0u),
                writeBytes = Uint32Xdr(0u)
            ),
            resourceFee = Int64Xdr(0L)
        )
    }

    /**
     * Creates a new builder from base64-encoded XDR string.
     *
     * Parses the XDR and uses it as the starting point for building.
     * Useful when working with simulation results or existing transactions.
     *
     * ## Example
     *
     * ```kotlin
     * val simulation = server.simulateTransaction(tx)
     * val builder = SorobanDataBuilder(simulation.transactionData!!)
     * ```
     *
     * @param sorobanData Base64-encoded SorobanTransactionData XDR
     * @throws IllegalArgumentException If the XDR is invalid
     */
    constructor(sorobanData: String) {
        try {
            data = SorobanTransactionDataXdr.fromXdrBase64(sorobanData)
        } catch (e: Exception) {
            throw IllegalArgumentException("Invalid SorobanData: $sorobanData", e)
        }
    }

    /**
     * Creates a new builder from existing SorobanTransactionData.
     *
     * Creates a deep copy of the provided data to ensure immutability.
     * Any modifications through builder methods create a new instance.
     *
     * ## Example
     *
     * ```kotlin
     * val existing = transaction.sorobanData!!
     * val modified = SorobanDataBuilder(existing)
     *     .setResourceFee(existing.resourceFee + 5000)
     *     .build()
     * ```
     *
     * @param sorobanData The SorobanTransactionData to copy
     */
    constructor(sorobanData: SorobanTransactionDataXdr) {
        // Create deep copy by encoding and decoding
        try {
            val writer = XdrWriter()
            sorobanData.encode(writer)
            val reader = XdrReader(writer.toByteArray())
            data = SorobanTransactionDataXdr.decode(reader)
        } catch (e: Exception) {
            throw IllegalArgumentException("Invalid SorobanData: $sorobanData", e)
        }
    }

    /**
     * Sets the resource fee portion of the Soroban data.
     *
     * The resource fee is charged in addition to the base transaction fee to cover
     * the cost of contract execution resources (CPU, memory, storage).
     *
     * This fee is typically calculated from simulation results:
     * ```kotlin
     * builder.setResourceFee(simulation.minResourceFee!! + buffer)
     * ```
     *
     * ## Fee Calculation
     *
     * Total transaction fee = Base fee + Resource fee
     * - Base fee: 100 stroops × number of operations
     * - Resource fee: Depends on contract execution (from simulation)
     *
     * @param fee The resource fee in stroops (must be non-negative)
     * @return This builder instance for chaining
     * @throws IllegalArgumentException If fee is negative
     */
    fun setResourceFee(fee: Long): SorobanDataBuilder {
        require(fee >= 0) { "Resource fee must be non-negative, got $fee" }
        data = data.copy(resourceFee = Int64Xdr(fee))
        return this
    }

    /**
     * Sets the resource consumption metrics.
     *
     * These metrics define the maximum resources the transaction can consume.
     * They are typically obtained from simulation results and should not be
     * set manually unless you know what you're doing.
     *
     * **Warning**: Setting these values too low will cause transaction failure.
     * Setting them too high wastes fees. Always use simulation results when possible.
     *
     * ## Resource Types
     *
     * - **CPU Instructions**: WASM instruction count for contract execution
     * - **Disk Read Bytes**: Bytes read from ledger entries
     * - **Write Bytes**: Bytes written to ledger and memory
     *
     * @param resources The resource metrics to set
     * @return This builder instance for chaining
     * @throws IllegalArgumentException If any resource value is negative
     *
     * @see Resources
     */
    fun setResources(resources: Resources): SorobanDataBuilder {
        require(resources.cpuInstructions >= 0) {
            "CPU instructions must be non-negative, got ${resources.cpuInstructions}"
        }
        require(resources.diskReadBytes >= 0) {
            "Disk read bytes must be non-negative, got ${resources.diskReadBytes}"
        }
        require(resources.writeBytes >= 0) {
            "Write bytes must be non-negative, got ${resources.writeBytes}"
        }

        // Update the resources in the data structure
        data = data.copy(
            resources = data.resources.copy(
                instructions = Uint32Xdr(resources.cpuInstructions.toUInt()),
                diskReadBytes = Uint32Xdr(resources.diskReadBytes.toUInt()),
                writeBytes = Uint32Xdr(resources.writeBytes.toUInt())
            )
        )
        return this
    }

    /**
     * Sets the read-only portion of the storage footprint.
     *
     * The read-only footprint contains ledger keys that the transaction will read
     * but not modify. This includes:
     * - Contract code (contracts being invoked)
     * - Contract data being read
     * - Account data being inspected
     *
     * ## Footprint Requirements
     *
     * All ledger entries accessed by a Soroban transaction MUST be declared in the
     * footprint. Accessing undeclared entries causes transaction failure.
     *
     * ## Null Behavior
     *
     * - `null`: Leaves the read-only footprint unchanged
     * - Empty collection: Clears the read-only footprint
     * - Non-empty collection: Replaces the read-only footprint
     *
     * @param readOnly The ledger keys to set as read-only (null to leave unchanged)
     * @return This builder instance for chaining
     */
    fun setReadOnly(readOnly: Collection<LedgerKeyXdr>?): SorobanDataBuilder {
        if (readOnly != null) {
            data = data.copy(
                resources = data.resources.copy(
                    footprint = data.resources.footprint.copy(
                        readOnly = readOnly.toList()
                    )
                )
            )
        }
        return this
    }

    /**
     * Sets the read-write portion of the storage footprint.
     *
     * The read-write footprint contains ledger keys that the transaction will both
     * read and potentially modify. This includes:
     * - Contract data being written
     * - Account balances being updated
     * - Contract state being modified
     *
     * ## Footprint Requirements
     *
     * All ledger entries modified by a Soroban transaction MUST be in the read-write
     * footprint. Attempting to modify entries only in read-only or not in the footprint
     * causes transaction failure.
     *
     * ## Null Behavior
     *
     * - `null`: Leaves the read-write footprint unchanged
     * - Empty collection: Clears the read-write footprint
     * - Non-empty collection: Replaces the read-write footprint
     *
     * @param readWrite The ledger keys to set as read-write (null to leave unchanged)
     * @return This builder instance for chaining
     */
    fun setReadWrite(readWrite: Collection<LedgerKeyXdr>?): SorobanDataBuilder {
        if (readWrite != null) {
            data = data.copy(
                resources = data.resources.copy(
                    footprint = data.resources.footprint.copy(
                        readWrite = readWrite.toList()
                    )
                )
            )
        }
        return this
    }

    /**
     * Builds the final SorobanTransactionData.
     *
     * Returns a deep copy of the constructed data to ensure immutability.
     * The builder can continue to be used after calling build().
     *
     * ## Example
     *
     * ```kotlin
     * val data1 = builder.setResourceFee(1000).build()
     * val data2 = builder.setResourceFee(2000).build()
     * // data1 still has fee = 1000, data2 has fee = 2000
     * ```
     *
     * @return A copy of the SorobanTransactionData
     */
    fun build(): SorobanTransactionDataXdr {
        // Create deep copy by encoding and decoding
        val writer = XdrWriter()
        data.encode(writer)
        val reader = XdrReader(writer.toByteArray())
        return SorobanTransactionDataXdr.decode(reader)
    }

    /**
     * Builds and returns the SorobanTransactionData as a base64-encoded XDR string.
     *
     * Convenience method equivalent to `build().toXdrBase64()`.
     * Useful for debugging or when working with APIs that expect XDR strings.
     *
     * ## Example
     *
     * ```kotlin
     * val xdr = builder.setResourceFee(1000).buildBase64()
     * println("Soroban data: $xdr")
     * ```
     *
     * @return Base64-encoded XDR string
     */
    fun buildBase64(): String {
        return build().toXdrBase64()
    }

    /**
     * Resource consumption metrics for Soroban transactions.
     *
     * These metrics define the maximum resources a transaction can consume during
     * execution. They are used to:
     * - Calculate resource fees
     * - Ensure network capacity limits aren't exceeded
     * - Provide execution guarantees
     *
     * ## Obtaining Resource Values
     *
     * **Always use simulation results when possible:**
     * ```kotlin
     * val simulation = server.simulateTransaction(tx)
     * // Extract from simulation's transactionData
     * ```
     *
     * **Manual construction (testing only):**
     * ```kotlin
     * val resources = SorobanDataBuilder.Resources(
     *     cpuInstructions = 1000000,
     *     diskReadBytes = 5000,
     *     writeBytes = 2000
     * )
     * ```
     *
     * ## Resource Limits
     *
     * Each resource has network-level limits:
     * - CPU Instructions: ~100M per transaction (varies by network)
     * - Disk Read: ~200KB per transaction
     * - Write: ~100KB per transaction
     *
     * Exceeding these limits causes transaction failure.
     *
     * @property cpuInstructions Number of WASM CPU instructions (uint32, represented as Long)
     * @property diskReadBytes Number of bytes read from ledger storage (uint32, represented as Long)
     * @property writeBytes Number of bytes written to ledger and memory (uint32, represented as Long)
     *
     * @see <a href="https://developers.stellar.org/docs/learn/smart-contract-internals/fees-and-metering">Fees and Metering documentation</a>
     */
    data class Resources(
        /**
         * Number of CPU instructions the contract execution can consume.
         *
         * This corresponds to WASM instruction count. More complex contracts
         * require more instructions. Typical values range from 100K to 10M.
         *
         * Stored as uint32 in XDR but represented as Long for convenience.
         * Values must be in range [0, 4294967295].
         */
        val cpuInstructions: Long,

        /**
         * Number of bytes read from ledger entries.
         *
         * Includes all data read from:
         * - Contract code
         * - Contract storage
         * - Account data
         * - Other ledger entries
         *
         * Stored as uint32 in XDR but represented as Long for convenience.
         * Values must be in range [0, 4294967295].
         */
        val diskReadBytes: Long,

        /**
         * Number of bytes written to ledger and memory.
         *
         * Includes:
         * - Contract storage writes
         * - Event emissions
         * - Return value serialization
         * - Memory allocations
         *
         * Stored as uint32 in XDR but represented as Long for convenience.
         * Values must be in range [0, 4294967295].
         */
        val writeBytes: Long
    ) {
        init {
            require(cpuInstructions >= 0) {
                "CPU instructions must be non-negative, got $cpuInstructions"
            }
            require(cpuInstructions <= 0xFFFFFFFFL) {
                "CPU instructions must fit in uint32, got $cpuInstructions"
            }
            require(diskReadBytes >= 0) {
                "Disk read bytes must be non-negative, got $diskReadBytes"
            }
            require(diskReadBytes <= 0xFFFFFFFFL) {
                "Disk read bytes must fit in uint32, got $diskReadBytes"
            }
            require(writeBytes >= 0) {
                "Write bytes must be non-negative, got $writeBytes"
            }
            require(writeBytes <= 0xFFFFFFFFL) {
                "Write bytes must fit in uint32, got $writeBytes"
            }
        }
    }
}
