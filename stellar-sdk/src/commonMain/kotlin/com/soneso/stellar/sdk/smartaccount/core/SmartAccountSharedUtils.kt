//
//  SmartAccountSharedUtils.kt
//  Stellar SDK Kotlin Multiplatform
//
//  Created by Claude on 27.01.26.
//  Copyright © 2026 Soneso. All rights reserved.
//

package com.soneso.stellar.sdk.smartaccount.core

import com.soneso.stellar.sdk.smartaccount.oz.OZSmartAccountKit
import com.soneso.stellar.sdk.InvokeHostFunctionOperation
import com.soneso.stellar.sdk.MemoNone
import com.soneso.stellar.sdk.Network
import com.soneso.stellar.sdk.TransactionBuilder
import com.soneso.stellar.sdk.scval.Scv
import com.soneso.stellar.sdk.xdr.HostFunctionXdr
import com.soneso.stellar.sdk.xdr.SCAddressXdr
import com.soneso.stellar.sdk.xdr.SCMapEntryXdr
import com.soneso.stellar.sdk.xdr.SCValXdr
import com.soneso.stellar.sdk.xdr.XdrWriter
import com.soneso.stellar.sdk.Address
import com.ionspin.kotlin.bignum.decimal.BigDecimal
import com.ionspin.kotlin.bignum.decimal.RoundingMode
import com.ionspin.kotlin.bignum.integer.BigInteger
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
     * Converts a decimal XLM amount string to stroops.
     *
     * Parses the string as a decimal number, multiplies by 10,000,000 (stroops per XLM),
     * and rounds to the nearest integer using BigDecimal arithmetic to avoid floating-point
     * precision loss. Supports up to 7 decimal places (e.g. "10.5" → 105000000).
     *
     * @param amount The amount in XLM as a decimal string (must be non-empty and positive)
     * @return The amount in stroops as a BigInteger (1 XLM = 10,000,000 stroops)
     * @throws ValidationException.InvalidInput if the string is empty, not a valid number,
     *         or the resulting value is not positive
     */
    fun amountToStroops(amount: String): BigInteger {
        if (amount.isBlank()) {
            throw ValidationException.invalidInput(
                "amount",
                "Amount must not be empty"
            )
        }

        if (amount.contains('e', ignoreCase = true)) {
            throw ValidationException.invalidInput(
                "amount",
                "Scientific notation is not supported, got: $amount"
            )
        }

        val decimal = try {
            BigDecimal.parseString(amount)
        } catch (e: Exception) {
            throw ValidationException.invalidInput(
                "amount",
                "Amount is not a valid number, got: $amount"
            )
        }

        if (decimal <= BigDecimal.ZERO) {
            throw ValidationException.invalidInput(
                "amount",
                "Amount must be greater than zero, got: $amount"
            )
        }

        val stroopsPerXlm = BigDecimal.fromLong(SmartAccountConstants.STROOPS_PER_XLM)
        val stroopsDecimal = decimal.multiply(stroopsPerXlm)

        // Round to nearest integer (7 decimal places max precision for stroops)
        val stroops = stroopsDecimal.roundToDigitPositionAfterDecimalPoint(
            0,
            RoundingMode.ROUND_HALF_AWAY_FROM_ZERO
        ).toBigInteger()

        if (stroops <= BigInteger.ZERO) {
            throw ValidationException.invalidInput(
                "amount",
                "Amount too small; minimum is 0.0000001 (1 stroop), got: $amount"
            )
        }

        return stroops
    }

    /**
     * Converts stroops (BigInteger) to I128 ScVal.
     *
     * Delegates to [Scv.toInt128] which handles the full 128-bit range correctly,
     * including values larger than Long.MAX_VALUE.
     *
     * @param stroops The amount in stroops (must be within I128 range)
     * @return ScVal::I128 representation
     * @throws IllegalArgumentException if the value is outside the I128 range
     */
    fun stroopsToI128ScVal(stroops: BigInteger): SCValXdr {
        return try {
            Scv.toInt128(stroops)
        } catch (e: IllegalArgumentException) {
            throw ValidationException.invalidInput(
                "stroops",
                "Amount exceeds I128 range: $stroops"
            )
        }
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
        return try {
            Address.fromSCAddress(address).toString()
        } catch (_: Exception) {
            null
        }
    }
}
