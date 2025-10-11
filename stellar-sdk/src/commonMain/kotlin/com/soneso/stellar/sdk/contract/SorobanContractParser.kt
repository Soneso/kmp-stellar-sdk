// Copyright 2024 Soneso. All rights reserved.
// Use of this source code is governed by a license that can be
// found in the LICENSE file.

package com.soneso.stellar.sdk.contract

import com.soneso.stellar.sdk.xdr.*

/**
 * Parses a Soroban contract byte code to extract Environment Meta, Contract Spec, and Contract Meta.
 *
 * This parser implements the Soroban contract specification as described in:
 * https://developers.stellar.org/docs/tools/sdks/build-your-own
 *
 * The contract byte code (WASM) contains embedded metadata in custom sections:
 * - contractenvmetav0: Environment metadata (protocol version)
 * - contractspecv0: Contract specification entries (functions, structs, unions, enums, events)
 * - contractmetav0: Contract metadata (key-value pairs for application/tooling use)
 *
 * Example usage:
 * ```kotlin
 * val wasmBytes = // ... load WASM file
 * try {
 *     val contractInfo = SorobanContractParser.parseContractByteCode(wasmBytes)
 *     println("Protocol version: ${contractInfo.envInterfaceVersion}")
 *     contractInfo.specEntries.forEach { entry ->
 *         // Process spec entries
 *     }
 * } catch (e: SorobanContractParserException) {
 *     println("Failed to parse contract: ${e.message}")
 * }
 * ```
 */
object SorobanContractParser {

    /**
     * Parses a Soroban contract byte code to extract Environment Meta, Contract Spec, and Contract Meta.
     *
     * @param byteCode The WASM byte code of the Soroban contract
     * @return [SorobanContractInfo] containing the parsed environment version, spec entries, and meta entries
     * @throws SorobanContractParserException if the byte code is invalid or cannot be parsed
     */
    fun parseContractByteCode(byteCode: ByteArray): SorobanContractInfo {
        // Parse environment metadata
        val envMeta = parseEnvironmentMeta(byteCode)
            ?: throw SorobanContractParserException("Invalid byte code: environment meta not found.")

        val interfaceVersion = when (envMeta) {
            is SCEnvMetaEntryXdr.InterfaceVersion -> envMeta.value.protocol.value.toULong()
            else -> throw SorobanContractParserException("Invalid byte code: environment interface version not found.")
        }

        // Parse contract specification entries
        val specEntries = parseContractSpec(byteCode)
            ?: throw SorobanContractParserException("Invalid byte code: spec entries not found.")

        // Parse contract metadata (may be empty)
        val metaEntries = parseMeta(byteCode)

        return SorobanContractInfo(
            envInterfaceVersion = interfaceVersion,
            specEntries = specEntries,
            metaEntries = metaEntries
        )
    }

    /**
     * Extracts and parses the environment metadata from the contract byte code.
     *
     * @param byteCode The contract byte code
     * @return The parsed [SCEnvMetaEntryXdr] or null if not found or invalid
     */
    private fun parseEnvironmentMeta(byteCode: ByteArray): SCEnvMetaEntryXdr? {
        return try {
            // Try to extract environment metadata between markers
            var metaEnvEntryBytes = extractBytesBetween(
                byteCode,
                "contractenvmetav0".encodeToByteArray(),
                "contractmetav0".encodeToByteArray()
            )

            if (metaEnvEntryBytes == null) {
                metaEnvEntryBytes = extractBytesBetween(
                    byteCode,
                    "contractenvmetav0".encodeToByteArray(),
                    "contractspecv0".encodeToByteArray()
                )
            }

            if (metaEnvEntryBytes == null) {
                metaEnvEntryBytes = extractBytesToEnd(byteCode, "contractenvmetav0".encodeToByteArray())
            }

            if (metaEnvEntryBytes == null) {
                return null
            }

            SCEnvMetaEntryXdr.decode(XdrReader(metaEnvEntryBytes))
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Extracts and parses all contract specification entries from the contract byte code.
     *
     * @param byteCode The contract byte code
     * @return List of [SCSpecEntryXdr] or null if not found
     */
    private fun parseContractSpec(byteCode: ByteArray): List<SCSpecEntryXdr>? {
        var specBytes = extractBytesBetween(
            byteCode,
            "contractspecv0".encodeToByteArray(),
            "contractenvmetav0".encodeToByteArray()
        )

        if (specBytes == null) {
            specBytes = extractBytesBetween(
                byteCode,
                "contractspecv0".encodeToByteArray(),
                "contractspecv0".encodeToByteArray()
            )
        }

        if (specBytes == null) {
            specBytes = extractBytesToEnd(byteCode, "contractspecv0".encodeToByteArray())
        }

        if (specBytes == null) {
            return null
        }

        val result = mutableListOf<SCSpecEntryXdr>()

        var currentSpecBytes: ByteArray? = specBytes
        while (currentSpecBytes != null && currentSpecBytes.isNotEmpty()) {
            try {
                val entry = SCSpecEntryXdr.decode(XdrReader(currentSpecBytes))

                // Only include valid spec entry types
                when (entry.discriminant) {
                    SCSpecEntryKindXdr.SC_SPEC_ENTRY_FUNCTION_V0,
                    SCSpecEntryKindXdr.SC_SPEC_ENTRY_UDT_STRUCT_V0,
                    SCSpecEntryKindXdr.SC_SPEC_ENTRY_UDT_UNION_V0,
                    SCSpecEntryKindXdr.SC_SPEC_ENTRY_UDT_ENUM_V0,
                    SCSpecEntryKindXdr.SC_SPEC_ENTRY_UDT_ERROR_ENUM_V0,
                    SCSpecEntryKindXdr.SC_SPEC_ENTRY_EVENT_V0 -> {
                        result.add(entry)

                        // Encode the entry back to determine how many bytes it consumed
                        val writer = XdrWriter()
                        entry.encode(writer)
                        val entryBytes = writer.toByteArray()

                        // Skip past the bytes we just decoded
                        currentSpecBytes = if (entryBytes.size < currentSpecBytes.size) {
                            currentSpecBytes.copyOfRange(entryBytes.size, currentSpecBytes.size)
                        } else {
                            ByteArray(0)
                        }
                    }
                    else -> {
                        // Unknown entry type, stop parsing
                        break
                    }
                }
            } catch (e: Exception) {
                // Error decoding entry, stop parsing
                break
            }
        }

        return result
    }

    /**
     * Extracts and parses all contract metadata entries from the contract byte code.
     *
     * @param byteCode The contract byte code
     * @return Map of metadata key-value pairs (may be empty)
     */
    private fun parseMeta(byteCode: ByteArray): Map<String, String> {
        var metaBytes = extractBytesBetween(
            byteCode,
            "contractmetav0".encodeToByteArray(),
            "contractenvmetav0".encodeToByteArray()
        )

        if (metaBytes == null) {
            metaBytes = extractBytesBetween(
                byteCode,
                "contractmetav0".encodeToByteArray(),
                "contractspecv0".encodeToByteArray()
            )
        }

        if (metaBytes == null) {
            metaBytes = extractBytesToEnd(byteCode, "contractmetav0".encodeToByteArray())
        }

        val result = mutableMapOf<String, String>()

        if (metaBytes == null) {
            return result
        }

        var currentMetaBytes: ByteArray? = metaBytes
        while (currentMetaBytes != null && currentMetaBytes.isNotEmpty()) {
            try {
                val entry = SCMetaEntryXdr.decode(XdrReader(currentMetaBytes))

                when (entry) {
                    is SCMetaEntryXdr.V0 -> {
                        result[entry.value.key] = entry.value.`val`

                        // Encode the entry back to determine how many bytes it consumed
                        val writer = XdrWriter()
                        entry.encode(writer)
                        val entryBytes = writer.toByteArray()

                        // Skip past the bytes we just decoded
                        currentMetaBytes = if (entryBytes.size < currentMetaBytes.size) {
                            currentMetaBytes.copyOfRange(entryBytes.size, currentMetaBytes.size)
                        } else {
                            ByteArray(0)
                        }
                    }
                    else -> {
                        // Unknown meta entry type, stop parsing
                        break
                    }
                }
            } catch (e: Exception) {
                // Error decoding entry, stop parsing
                break
            }
        }

        return result
    }

    /**
     * Extracts a byte sequence between two marker byte sequences.
     * This is a binary-safe operation that does not corrupt binary WASM data.
     *
     * @param input The input ByteArray to search
     * @param startSymbol The start marker (not included in result)
     * @param endSymbol The end marker (not included in result)
     * @return The extracted ByteArray or null if markers not found
     */
    private fun extractBytesBetween(
        input: ByteArray,
        startSymbol: ByteArray,
        endSymbol: ByteArray
    ): ByteArray? {
        val startIndex = indexOfBytes(input, startSymbol)
        if (startIndex == -1) {
            return null
        }

        val endIndex = indexOfBytes(input, endSymbol, startIndex + startSymbol.size)
        if (endIndex == -1) {
            return null
        }

        return input.copyOfRange(startIndex + startSymbol.size, endIndex)
    }

    /**
     * Extracts a byte sequence from a marker to the end of the input.
     * This is a binary-safe operation that does not corrupt binary WASM data.
     *
     * @param input The input ByteArray to search
     * @param startSymbol The start marker (not included in result)
     * @return The extracted ByteArray or null if marker not found
     */
    private fun extractBytesToEnd(input: ByteArray, startSymbol: ByteArray): ByteArray? {
        val startIndex = indexOfBytes(input, startSymbol)
        if (startIndex == -1) {
            return null
        }

        return input.copyOfRange(startIndex + startSymbol.size, input.size)
    }

    /**
     * Finds the first occurrence of a byte sequence within another byte sequence.
     * This is similar to String.indexOf() but for ByteArray.
     *
     * @param input The ByteArray to search in
     * @param pattern The byte pattern to search for
     * @param startIndex The index to start searching from (default 0)
     * @return The index of the first occurrence, or -1 if not found
     */
    private fun indexOfBytes(input: ByteArray, pattern: ByteArray, startIndex: Int = 0): Int {
        if (pattern.isEmpty() || input.size < pattern.size) {
            return -1
        }

        val maxIndex = input.size - pattern.size
        for (i in startIndex..maxIndex) {
            var match = true
            for (j in pattern.indices) {
                if (input[i + j] != pattern[j]) {
                    match = false
                    break
                }
            }
            if (match) {
                return i
            }
        }

        return -1
    }
}

/**
 * Exception thrown when the [SorobanContractParser] fails to parse the given byte code.
 *
 * @property message Detailed error message describing the parsing failure
 */
class SorobanContractParserException(message: String) : Exception(message)

/**
 * Stores information parsed from a Soroban contract byte code, including
 * Environment Meta, Contract Spec Entries, and Contract Meta Entries.
 *
 * See also: https://developers.stellar.org/docs/tools/sdks/build-your-own
 *
 * @property envInterfaceVersion Environment interface protocol version from Environment Meta
 * @property specEntries Contract Spec Entries. There is a SCSpecEntry for every function, struct,
 *                       union, enum, error enum, and event exported by the contract.
 * @property metaEntries Contract Meta Entries as key-value pairs. Contracts may store any metadata
 *                       that can be used by applications and tooling off-network.
 */
data class SorobanContractInfo(
    val envInterfaceVersion: ULong,
    val specEntries: List<SCSpecEntryXdr>,
    val metaEntries: Map<String, String>
)
