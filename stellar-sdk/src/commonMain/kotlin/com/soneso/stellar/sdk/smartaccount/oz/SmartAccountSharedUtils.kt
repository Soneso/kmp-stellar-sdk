//
//  SmartAccountSharedUtils.kt
//  Stellar SDK Kotlin Multiplatform
//
//  Created by Claude on 27.01.26.
//  Copyright © 2026 Soneso. All rights reserved.
//

package com.soneso.stellar.sdk.smartaccount.oz

import com.soneso.stellar.sdk.smartaccount.core.*
import com.soneso.stellar.sdk.InvokeHostFunctionOperation
import com.soneso.stellar.sdk.MemoNone
import com.soneso.stellar.sdk.Network
import com.soneso.stellar.sdk.TransactionBuilder
import com.soneso.stellar.sdk.xdr.HostFunctionXdr
import com.soneso.stellar.sdk.xdr.Int64Xdr
import com.soneso.stellar.sdk.xdr.Int128PartsXdr
import com.soneso.stellar.sdk.xdr.SCAddressXdr
import com.soneso.stellar.sdk.xdr.SCMapEntryXdr
import com.soneso.stellar.sdk.xdr.SCValXdr
import com.soneso.stellar.sdk.xdr.Uint64Xdr
import com.soneso.stellar.sdk.xdr.PublicKeyXdr
import com.soneso.stellar.sdk.xdr.XdrWriter
import com.soneso.stellar.sdk.StrKey
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * Shared utility functions for Smart Account operations.
 *
 * Provides reusable helpers used across multiple Smart Account components:
 * - Transaction simulation and result extraction
 * - Amount conversion (XLM to stroops)
 * - Stroops to I128 ScVal conversion
 * - Base64URL encoding/decoding
 * - Address string extraction from SCAddressXDR
 *
 * These utilities are extracted to eliminate duplication across
 * OZContextRuleManager, OZMultiSignerManager, OZTransactionOperations,
 * and OZWalletOperations.
 */
object SmartAccountSharedUtils {

    // MARK: - Transaction Simulation

    /**
     * Simulates a host function and extracts the return value.
     *
     * Performs the following steps:
     * 1. Fetches the deployer account
     * 2. Builds a transaction with the host function
     * 3. Simulates the transaction
     * 4. Extracts and returns the result value from simulation
     *
     * Used for query operations that don't require transaction submission.
     *
     * @param hostFunction The host function to simulate
     * @param kit The OZSmartAccountKit instance providing deployer and server access
     * @return The SCVal return value from the simulation
     * @throws SmartAccountException if simulation fails or result extraction fails
     */
    suspend fun simulateAndExtractResult(
        hostFunction: HostFunctionXdr,
        kit: OZSmartAccountKit
    ): SCValXdr {
        // Get deployer account
        val deployer = kit.getDeployer()
        val deployerAccount = kit.sorobanServer.getAccount(deployer.getAccountId())

        // Build operation
        val operation = InvokeHostFunctionOperation(hostFunction, emptyList())

        // Build transaction for simulation
        val transaction = TransactionBuilder(deployerAccount, Network(kit.config.networkPassphrase))
            .setBaseFee(100)
            .addOperation(operation)
            .addMemo(MemoNone)
            .setTimeout(300)
            .build()

        // Simulate transaction
        val simulation = kit.sorobanServer.simulateTransaction(transaction)

        // Check for simulation errors
        if (simulation.error != null) {
            throw TransactionException.simulationFailed("Simulation error: ${simulation.error}")
        }

        // Extract result
        val results = simulation.results
        if (results.isNullOrEmpty()) {
            throw TransactionException.simulationFailed("No results returned from simulation")
        }

        return results[0].parseXdr()
            ?: throw TransactionException.simulationFailed("No return value in simulation result")
    }

    // MARK: - Amount Conversion

    /**
     * Converts an XLM amount to stroops.
     *
     * Uses Double precision for arithmetic with proper rounding.
     * Validates that the resulting stroops value is positive and within Long range.
     *
     * @param amount The amount in XLM (must be positive)
     * @return The amount in stroops (1 XLM = 10,000,000 stroops)
     * @throws ValidationException.InvalidInput if conversion would overflow or result is invalid
     */
    fun amountToStroops(amount: Double): Long {
        val stroopsDouble = amount * SmartAccountConstants.STROOPS_PER_XLM

        // Round to nearest integer
        val stroops = stroopsDouble.toLong()

        // Validate range
        if (stroops <= 0 || stroops > Long.MAX_VALUE) {
            throw ValidationException.invalidInput(
                "amount",
                "Amount out of valid range, got: $amount"
            )
        }

        return stroops
    }

    /**
     * Converts stroops (Long) to I128 ScVal.
     *
     * For positive values within Long range, the high part is 0 and the low part
     * contains the value as ULong.
     *
     * @param stroops The amount in stroops
     * @return ScVal::I128 representation
     */
    fun stroopsToI128ScVal(stroops: Long): SCValXdr {
        val i128Parts = Int128PartsXdr(hi = Int64Xdr(0L), lo = Uint64Xdr(stroops.toULong()))
        return SCValXdr.I128(i128Parts)
    }

    // MARK: - Base64URL Encoding/Decoding

    /**
     * Encodes data to Base64URL format (RFC 4648 Section 5, no padding).
     *
     * Uses URL-safe alphabet: `-` instead of `+`, `_` instead of `/`.
     * Padding `=` characters are stripped.
     *
     * @param data The data to encode
     * @return Base64URL-encoded string without padding
     */
    @OptIn(ExperimentalEncodingApi::class)
    fun base64urlEncode(data: ByteArray): String {
        return Base64.UrlSafe.encode(data).trimEnd('=')
    }

    /**
     * Decodes a Base64URL-encoded string to data.
     *
     * Accepts input with or without padding. Uses URL-safe alphabet:
     * `-` instead of `+`, `_` instead of `/`.
     *
     * @param string The Base64URL-encoded string (with or without padding)
     * @return Decoded data, or null if decoding fails
     */
    @OptIn(ExperimentalEncodingApi::class)
    fun base64urlDecode(string: String): ByteArray? {
        // Add padding if needed - Base64.UrlSafe.decode() requires it
        val padded = when (string.length % 4) {
            2 -> string + "=="
            3 -> string + "="
            else -> string
        }

        return try {
            Base64.UrlSafe.decode(padded)
        } catch (e: Exception) {
            null
        }
    }

    // MARK: - ScMap Key Sorting

    /**
     * Encodes an SCValXdr to its XDR byte representation.
     *
     * Used for deterministic key comparison when sorting ScMap entries.
     *
     * @param scVal The SCVal to encode
     * @return The XDR-encoded bytes
     */
    fun scValToXdrBytes(scVal: SCValXdr): ByteArray {
        val writer = XdrWriter()
        scVal.encode(writer)
        return writer.toByteArray()
    }

    /**
     * Sorts ScMap entries by lexicographic comparison of their keys' XDR byte representation.
     *
     * Soroban mandates that ScMap keys are sorted lexicographically by their XDR-encoded
     * bytes. This function takes a LinkedHashMap of ScVal entries and returns a new
     * LinkedHashMap with entries sorted by their key's XDR encoding.
     *
     * This matches the behavior of the TypeScript Smart Account Kit SDK which sorts
     * policy parameter maps and policy address maps before encoding to XDR.
     *
     * @param map The unsorted map of ScVal key-value pairs
     * @return A new LinkedHashMap with entries sorted by XDR-encoded key bytes
     */
    fun sortMapByKeyXdr(map: LinkedHashMap<SCValXdr, SCValXdr>): LinkedHashMap<SCValXdr, SCValXdr> {
        val sorted = LinkedHashMap<SCValXdr, SCValXdr>()
        map.entries
            .sortedWith(Comparator { a, b ->
                val aBytes = scValToXdrBytes(a.key)
                val bBytes = scValToXdrBytes(b.key)
                compareByteArraysLexicographically(aBytes, bBytes)
            })
            .forEach { (key, value) ->
                sorted[key] = value
            }
        return sorted
    }

    /**
     * Sorts a list of SCMapEntryXdr by lexicographic comparison of their keys' XDR bytes.
     *
     * Soroban mandates that ScMap keys are sorted lexicographically by their XDR-encoded
     * bytes. This function sorts entries in-place.
     *
     * @param entries The list of map entries to sort
     * @return A new list with entries sorted by XDR-encoded key bytes
     */
    fun sortMapEntriesByKeyXdr(entries: List<SCMapEntryXdr>): List<SCMapEntryXdr> {
        return entries.sortedWith(Comparator { a, b ->
            val aBytes = scValToXdrBytes(a.key)
            val bBytes = scValToXdrBytes(b.key)
            compareByteArraysLexicographically(aBytes, bBytes)
        })
    }

    /**
     * Compares two byte arrays lexicographically (unsigned byte comparison).
     *
     * Compares each byte as unsigned values. If all compared bytes are equal,
     * the shorter array is considered less than the longer one.
     *
     * @param a First byte array
     * @param b Second byte array
     * @return Negative if a < b, positive if a > b, zero if equal
     */
    private fun compareByteArraysLexicographically(a: ByteArray, b: ByteArray): Int {
        val minLength = minOf(a.size, b.size)
        for (i in 0 until minLength) {
            val aByte = a[i].toInt() and 0xFF
            val bByte = b[i].toInt() and 0xFF
            if (aByte != bByte) {
                return aByte - bByte
            }
        }
        return a.size - b.size
    }

    // MARK: - Address Extraction

    /**
     * Extracts a string address from an SCAddressXDR.
     *
     * Returns the G-address for account types or the C-address for contract types.
     *
     * @param address The SCAddressXDR to extract from
     * @return The string address, or null if extraction fails
     */
    fun extractAddressString(address: SCAddressXdr): String? {
        return when (address) {
            is SCAddressXdr.AccountId -> {
                // Account address: G-address
                try {
                    val publicKey = address.value.value
                    when (publicKey) {
                        is PublicKeyXdr.Ed25519 -> {
                            StrKey.encodeEd25519PublicKey(publicKey.value.value)
                        }
                    }
                } catch (e: Exception) {
                    null
                }
            }
            is SCAddressXdr.ContractId -> {
                // Contract address: C-address
                try {
                    com.soneso.stellar.sdk.StrKey.encodeContract(address.value.value.value)
                } catch (e: Exception) {
                    null
                }
            }
            else -> null
        }
    }
}
