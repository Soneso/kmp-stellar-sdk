package com.stellar.sdk.scval

import com.ionspin.kotlin.bignum.integer.BigInteger
import com.ionspin.kotlin.bignum.integer.Sign
import com.stellar.sdk.xdr.*

/**
 * Provides a range of methods to help you build and parse [SCValXdr] more conveniently.
 *
 * This utility class offers factory methods for creating Soroban Contract Values (SCVal) from
 * Kotlin native types and converting them back. All methods are optimized for production use
 * with proper validation and error handling.
 *
 * ## Usage Example
 * ```kotlin
 * // Create SCVal from primitive types
 * val int32Val = Scv.toInt32(42)
 * val stringVal = Scv.toString("hello")
 * val boolVal = Scv.toBoolean(true)
 *
 * // Create SCVal from collections
 * val vecVal = Scv.toVec(listOf(int32Val, stringVal))
 * val mapVal = Scv.toMap(linkedMapOf(
 *     Scv.toSymbol("key") to Scv.toInt64(100L)
 * ))
 *
 * // Convert back to Kotlin types
 * val intValue = Scv.fromInt32(int32Val)  // 42
 * val strValue = Scv.fromString(stringVal)  // "hello"
 * ```
 */
object Scv {

    // ============================================================================
    // Boolean
    // ============================================================================

    /**
     * Build a [SCValXdr] with the type of [SCValTypeXdr.SCV_BOOL].
     *
     * @param value boolean to convert
     * @return [SCValXdr] with the type of [SCValTypeXdr.SCV_BOOL]
     */
    fun toBoolean(value: Boolean): SCValXdr {
        return SCValXdr.B(value)
    }

    /**
     * Convert from [SCValXdr] with the type of [SCValTypeXdr.SCV_BOOL] to Boolean.
     *
     * @param scVal [SCValXdr] to convert
     * @return boolean value
     * @throws IllegalArgumentException if scVal type is not [SCValTypeXdr.SCV_BOOL]
     */
    fun fromBoolean(scVal: SCValXdr): Boolean {
        require(scVal is SCValXdr.B) {
            "invalid scVal type, expected SCV_BOOL, but got ${scVal.discriminant}"
        }
        return scVal.value
    }

    // ============================================================================
    // Void
    // ============================================================================

    /**
     * Build a [SCValXdr] with the type of [SCValTypeXdr.SCV_VOID].
     *
     * @return [SCValXdr] with the type of [SCValTypeXdr.SCV_VOID]
     */
    fun toVoid(): SCValXdr {
        return SCValXdr.Void(SCValTypeXdr.SCV_VOID)
    }

    /**
     * Parse from [SCValXdr] with the type of [SCValTypeXdr.SCV_VOID].
     * This function validates the type and returns Unit if successful.
     *
     * @param scVal [SCValXdr] to convert
     * @throws IllegalArgumentException if scVal type is not [SCValTypeXdr.SCV_VOID]
     */
    fun fromVoid(scVal: SCValXdr) {
        require(scVal.discriminant == SCValTypeXdr.SCV_VOID) {
            "invalid scVal type, expected SCV_VOID, but got ${scVal.discriminant}"
        }
    }

    // ============================================================================
    // Int32
    // ============================================================================

    /**
     * Build a [SCValXdr] with the type of [SCValTypeXdr.SCV_I32].
     *
     * @param value int32 to convert
     * @return [SCValXdr] with the type of [SCValTypeXdr.SCV_I32]
     */
    fun toInt32(value: Int): SCValXdr {
        return SCValXdr.I32(Int32Xdr(value))
    }

    /**
     * Convert from [SCValXdr] with the type of [SCValTypeXdr.SCV_I32] to Int.
     *
     * @param scVal [SCValXdr] to convert
     * @return the int32 value
     * @throws IllegalArgumentException if scVal type is not [SCValTypeXdr.SCV_I32]
     */
    fun fromInt32(scVal: SCValXdr): Int {
        require(scVal is SCValXdr.I32) {
            "invalid scVal type, expected SCV_I32, but got ${scVal.discriminant}"
        }
        return scVal.value.value
    }

    // ============================================================================
    // UInt32
    // ============================================================================

    /**
     * Build a [SCValXdr] with the type of [SCValTypeXdr.SCV_U32].
     *
     * @param value uint32 to convert
     * @return [SCValXdr] with the type of [SCValTypeXdr.SCV_U32]
     */
    fun toUint32(value: UInt): SCValXdr {
        return SCValXdr.U32(Uint32Xdr(value))
    }

    /**
     * Convert from [SCValXdr] with the type of [SCValTypeXdr.SCV_U32] to UInt.
     *
     * @param scVal [SCValXdr] to convert
     * @return the uint32 value
     * @throws IllegalArgumentException if scVal type is not [SCValTypeXdr.SCV_U32]
     */
    fun fromUint32(scVal: SCValXdr): UInt {
        require(scVal is SCValXdr.U32) {
            "invalid scVal type, expected SCV_U32, but got ${scVal.discriminant}"
        }
        return scVal.value.value
    }

    // ============================================================================
    // Int64
    // ============================================================================

    /**
     * Build a [SCValXdr] with the type of [SCValTypeXdr.SCV_I64].
     *
     * @param value int64 to convert
     * @return [SCValXdr] with the type of [SCValTypeXdr.SCV_I64]
     */
    fun toInt64(value: Long): SCValXdr {
        return SCValXdr.I64(Int64Xdr(value))
    }

    /**
     * Convert from [SCValXdr] with the type of [SCValTypeXdr.SCV_I64] to Long.
     *
     * @param scVal [SCValXdr] to convert
     * @return the int64 value
     * @throws IllegalArgumentException if scVal type is not [SCValTypeXdr.SCV_I64]
     */
    fun fromInt64(scVal: SCValXdr): Long {
        require(scVal is SCValXdr.I64) {
            "invalid scVal type, expected SCV_I64, but got ${scVal.discriminant}"
        }
        return scVal.value.value
    }

    // ============================================================================
    // UInt64
    // ============================================================================

    /**
     * Build a [SCValXdr] with the type of [SCValTypeXdr.SCV_U64].
     *
     * @param value uint64 to convert
     * @return [SCValXdr] with the type of [SCValTypeXdr.SCV_U64]
     */
    fun toUint64(value: ULong): SCValXdr {
        return SCValXdr.U64(Uint64Xdr(value))
    }

    /**
     * Convert from [SCValXdr] with the type of [SCValTypeXdr.SCV_U64] to ULong.
     *
     * @param scVal [SCValXdr] to convert
     * @return the uint64 value
     * @throws IllegalArgumentException if scVal type is not [SCValTypeXdr.SCV_U64]
     */
    fun fromUint64(scVal: SCValXdr): ULong {
        require(scVal is SCValXdr.U64) {
            "invalid scVal type, expected SCV_U64, but got ${scVal.discriminant}"
        }
        return scVal.value.value
    }

    // ============================================================================
    // Int128
    // ============================================================================

    private val INT128_MIN_VALUE = BigInteger.TWO.negate().pow(127)
    private val INT128_MAX_VALUE = BigInteger.TWO.pow(127) - BigInteger.ONE

    /**
     * Build a [SCValXdr] with the type of [SCValTypeXdr.SCV_I128].
     *
     * @param value int128 to convert
     * @return [SCValXdr] with the type of [SCValTypeXdr.SCV_I128]
     * @throws IllegalArgumentException if value is out of int128 range
     */
    fun toInt128(value: BigInteger): SCValXdr {
        require(value >= INT128_MIN_VALUE && value <= INT128_MAX_VALUE) {
            "invalid value, expected between $INT128_MIN_VALUE and $INT128_MAX_VALUE, but got $value"
        }

        // Use platform-specific conversion
        val paddedBytes = bigIntegerToBytesSigned(value, 16)

        val hi = bytesToLong(paddedBytes.copyOfRange(0, 8))
        val lo = bytesToULong(paddedBytes.copyOfRange(8, 16))

        return SCValXdr.I128(
            Int128PartsXdr(
                hi = Int64Xdr(hi),
                lo = Uint64Xdr(lo)
            )
        )
    }

    /**
     * Convert from [SCValXdr] with the type of [SCValTypeXdr.SCV_I128] to [BigInteger].
     *
     * @param scVal [SCValXdr] to convert
     * @return the int128 value
     * @throws IllegalArgumentException if scVal type is not [SCValTypeXdr.SCV_I128]
     */
    fun fromInt128(scVal: SCValXdr): BigInteger {
        require(scVal is SCValXdr.I128) {
            "invalid scVal type, expected SCV_I128, but got ${scVal.discriminant}"
        }

        val hiBytes = longToBytes(scVal.value.hi.value)
        val loBytes = ulongToBytes(scVal.value.lo.value)

        val fullBytes = ByteArray(16)
        hiBytes.copyInto(fullBytes, 0, 0, 8)
        loBytes.copyInto(fullBytes, 8, 0, 8)

        // Use platform-specific conversion
        return bytesToBigIntegerSigned(fullBytes)
    }

    // ============================================================================
    // UInt128
    // ============================================================================

    private val UINT128_MIN_VALUE = BigInteger.ZERO
    private val UINT128_MAX_VALUE = BigInteger.TWO.pow(128) - BigInteger.ONE

    /**
     * Build a [SCValXdr] with the type of [SCValTypeXdr.SCV_U128].
     *
     * @param value uint128 to convert
     * @return [SCValXdr] with the type of [SCValTypeXdr.SCV_U128]
     * @throws IllegalArgumentException if value is out of uint128 range
     */
    fun toUint128(value: BigInteger): SCValXdr {
        require(value >= UINT128_MIN_VALUE && value <= UINT128_MAX_VALUE) {
            "invalid value, expected between $UINT128_MIN_VALUE and $UINT128_MAX_VALUE, but got $value"
        }

        val bytes = value.toByteArray()
        val paddedBytes = ByteArray(16)
        val numBytesToCopy = minOf(bytes.size, 16)
        val copyStartIndex = bytes.size - numBytesToCopy
        bytes.copyInto(paddedBytes, 16 - numBytesToCopy, copyStartIndex, bytes.size)

        val hi = bytesToULong(paddedBytes.copyOfRange(0, 8))
        val lo = bytesToULong(paddedBytes.copyOfRange(8, 16))

        return SCValXdr.U128(
            UInt128PartsXdr(
                hi = Uint64Xdr(hi),
                lo = Uint64Xdr(lo)
            )
        )
    }

    /**
     * Convert from [SCValXdr] with the type of [SCValTypeXdr.SCV_U128] to [BigInteger].
     *
     * @param scVal [SCValXdr] to convert
     * @return the uint128 value
     * @throws IllegalArgumentException if scVal type is not [SCValTypeXdr.SCV_U128]
     */
    fun fromUint128(scVal: SCValXdr): BigInteger {
        require(scVal is SCValXdr.U128) {
            "invalid scVal type, expected SCV_U128, but got ${scVal.discriminant}"
        }

        val hiBytes = ulongToBytes(scVal.value.hi.value)
        val loBytes = ulongToBytes(scVal.value.lo.value)

        val fullBytes = ByteArray(16)
        hiBytes.copyInto(fullBytes, 0, 0, 8)
        loBytes.copyInto(fullBytes, 8, 0, 8)

        return BigInteger.fromByteArray(fullBytes, sign = Sign.POSITIVE)
    }

    // ============================================================================
    // Int256
    // ============================================================================

    private val INT256_MIN_VALUE = BigInteger.TWO.negate().pow(255)
    private val INT256_MAX_VALUE = BigInteger.TWO.pow(255) - BigInteger.ONE

    /**
     * Build a [SCValXdr] with the type of [SCValTypeXdr.SCV_I256].
     *
     * @param value int256 to convert
     * @return [SCValXdr] with the type of [SCValTypeXdr.SCV_I256]
     * @throws IllegalArgumentException if value is out of int256 range
     */
    fun toInt256(value: BigInteger): SCValXdr {
        require(value >= INT256_MIN_VALUE && value <= INT256_MAX_VALUE) {
            "invalid value, expected between $INT256_MIN_VALUE and $INT256_MAX_VALUE, but got $value"
        }

        // Use platform-specific conversion
        val paddedBytes = bigIntegerToBytesSigned(value, 32)

        val hiHi = bytesToLong(paddedBytes.copyOfRange(0, 8))
        val hiLo = bytesToULong(paddedBytes.copyOfRange(8, 16))
        val loHi = bytesToULong(paddedBytes.copyOfRange(16, 24))
        val loLo = bytesToULong(paddedBytes.copyOfRange(24, 32))

        return SCValXdr.I256(
            Int256PartsXdr(
                hiHi = Int64Xdr(hiHi),
                hiLo = Uint64Xdr(hiLo),
                loHi = Uint64Xdr(loHi),
                loLo = Uint64Xdr(loLo)
            )
        )
    }

    /**
     * Convert from [SCValXdr] with the type of [SCValTypeXdr.SCV_I256] to [BigInteger].
     *
     * @param scVal [SCValXdr] to convert
     * @return the int256 value
     * @throws IllegalArgumentException if scVal type is not [SCValTypeXdr.SCV_I256]
     */
    fun fromInt256(scVal: SCValXdr): BigInteger {
        require(scVal is SCValXdr.I256) {
            "invalid scVal type, expected SCV_I256, but got ${scVal.discriminant}"
        }

        val fullBytes = ByteArray(32)
        longToBytes(scVal.value.hiHi.value).copyInto(fullBytes, 0, 0, 8)
        ulongToBytes(scVal.value.hiLo.value).copyInto(fullBytes, 8, 0, 8)
        ulongToBytes(scVal.value.loHi.value).copyInto(fullBytes, 16, 0, 8)
        ulongToBytes(scVal.value.loLo.value).copyInto(fullBytes, 24, 0, 8)

        // Use platform-specific conversion
        return bytesToBigIntegerSigned(fullBytes)
    }

    // ============================================================================
    // UInt256
    // ============================================================================

    private val UINT256_MIN_VALUE = BigInteger.ZERO
    private val UINT256_MAX_VALUE = BigInteger.TWO.pow(256) - BigInteger.ONE

    /**
     * Build a [SCValXdr] with the type of [SCValTypeXdr.SCV_U256].
     *
     * @param value uint256 to convert
     * @return [SCValXdr] with the type of [SCValTypeXdr.SCV_U256]
     * @throws IllegalArgumentException if value is out of uint256 range
     */
    fun toUint256(value: BigInteger): SCValXdr {
        require(value >= UINT256_MIN_VALUE && value <= UINT256_MAX_VALUE) {
            "invalid value, expected between $UINT256_MIN_VALUE and $UINT256_MAX_VALUE, but got $value"
        }

        val bytes = value.toByteArray()
        val paddedBytes = ByteArray(32)
        val numBytesToCopy = minOf(bytes.size, 32)
        val copyStartIndex = bytes.size - numBytesToCopy
        bytes.copyInto(paddedBytes, 32 - numBytesToCopy, copyStartIndex, bytes.size)

        val hiHi = bytesToULong(paddedBytes.copyOfRange(0, 8))
        val hiLo = bytesToULong(paddedBytes.copyOfRange(8, 16))
        val loHi = bytesToULong(paddedBytes.copyOfRange(16, 24))
        val loLo = bytesToULong(paddedBytes.copyOfRange(24, 32))

        return SCValXdr.U256(
            UInt256PartsXdr(
                hiHi = Uint64Xdr(hiHi),
                hiLo = Uint64Xdr(hiLo),
                loHi = Uint64Xdr(loHi),
                loLo = Uint64Xdr(loLo)
            )
        )
    }

    /**
     * Convert from [SCValXdr] with the type of [SCValTypeXdr.SCV_U256] to [BigInteger].
     *
     * @param scVal [SCValXdr] to convert
     * @return the uint256 value
     * @throws IllegalArgumentException if scVal type is not [SCValTypeXdr.SCV_U256]
     */
    fun fromUint256(scVal: SCValXdr): BigInteger {
        require(scVal is SCValXdr.U256) {
            "invalid scVal type, expected SCV_U256, but got ${scVal.discriminant}"
        }

        val fullBytes = ByteArray(32)
        ulongToBytes(scVal.value.hiHi.value).copyInto(fullBytes, 0, 0, 8)
        ulongToBytes(scVal.value.hiLo.value).copyInto(fullBytes, 8, 0, 8)
        ulongToBytes(scVal.value.loHi.value).copyInto(fullBytes, 16, 0, 8)
        ulongToBytes(scVal.value.loLo.value).copyInto(fullBytes, 24, 0, 8)

        return BigInteger.fromByteArray(fullBytes, sign = Sign.POSITIVE)
    }

    // ============================================================================
    // TimePoint
    // ============================================================================

    /**
     * Build a [SCValXdr] with the type of [SCValTypeXdr.SCV_TIMEPOINT].
     *
     * @param timePoint timePoint to convert (uint64)
     * @return [SCValXdr] with the type of [SCValTypeXdr.SCV_TIMEPOINT]
     */
    fun toTimePoint(timePoint: ULong): SCValXdr {
        return SCValXdr.Timepoint(TimePointXdr(Uint64Xdr(timePoint)))
    }

    /**
     * Convert from [SCValXdr] with the type of [SCValTypeXdr.SCV_TIMEPOINT] to ULong.
     *
     * @param scVal [SCValXdr] to convert
     * @return the timePoint (uint64)
     * @throws IllegalArgumentException if scVal type is not [SCValTypeXdr.SCV_TIMEPOINT]
     */
    fun fromTimePoint(scVal: SCValXdr): ULong {
        require(scVal is SCValXdr.Timepoint) {
            "invalid scVal type, expected SCV_TIMEPOINT, but got ${scVal.discriminant}"
        }
        return scVal.value.value.value
    }

    // ============================================================================
    // Duration
    // ============================================================================

    /**
     * Build a [SCValXdr] with the type of [SCValTypeXdr.SCV_DURATION].
     *
     * @param duration duration to convert (uint64)
     * @return [SCValXdr] with the type of [SCValTypeXdr.SCV_DURATION]
     */
    fun toDuration(duration: ULong): SCValXdr {
        return SCValXdr.Duration(DurationXdr(Uint64Xdr(duration)))
    }

    /**
     * Convert from [SCValXdr] with the type of [SCValTypeXdr.SCV_DURATION] to ULong.
     *
     * @param scVal [SCValXdr] to convert
     * @return the duration (uint64)
     * @throws IllegalArgumentException if scVal type is not [SCValTypeXdr.SCV_DURATION]
     */
    fun fromDuration(scVal: SCValXdr): ULong {
        require(scVal is SCValXdr.Duration) {
            "invalid scVal type, expected SCV_DURATION, but got ${scVal.discriminant}"
        }
        return scVal.value.value.value
    }

    // ============================================================================
    // String
    // ============================================================================

    /**
     * Build a [SCValXdr] with the type of [SCValTypeXdr.SCV_STRING].
     *
     * @param string string to convert
     * @return [SCValXdr] with the type of [SCValTypeXdr.SCV_STRING]
     */
    fun toString(string: String): SCValXdr {
        return SCValXdr.Str(SCStringXdr(string))
    }

    /**
     * Convert from [SCValXdr] with the type of [SCValTypeXdr.SCV_STRING] to String.
     *
     * @param scVal [SCValXdr] to convert
     * @return the string value
     * @throws IllegalArgumentException if scVal type is not [SCValTypeXdr.SCV_STRING]
     */
    fun fromString(scVal: SCValXdr): String {
        require(scVal is SCValXdr.Str) {
            "invalid scVal type, expected SCV_STRING, but got ${scVal.discriminant}"
        }
        return scVal.value.value
    }

    // ============================================================================
    // Symbol
    // ============================================================================

    /**
     * Build a [SCValXdr] with the type of [SCValTypeXdr.SCV_SYMBOL].
     *
     * @param symbol symbol to convert
     * @return [SCValXdr] with the type of [SCValTypeXdr.SCV_SYMBOL]
     */
    fun toSymbol(symbol: String): SCValXdr {
        return SCValXdr.Sym(SCSymbolXdr(symbol))
    }

    /**
     * Convert from [SCValXdr] with the type of [SCValTypeXdr.SCV_SYMBOL] to String.
     *
     * @param scVal [SCValXdr] to convert
     * @return the symbol
     * @throws IllegalArgumentException if scVal type is not [SCValTypeXdr.SCV_SYMBOL]
     */
    fun fromSymbol(scVal: SCValXdr): String {
        require(scVal is SCValXdr.Sym) {
            "invalid scVal type, expected SCV_SYMBOL, but got ${scVal.discriminant}"
        }
        return scVal.value.value
    }

    // ============================================================================
    // Bytes
    // ============================================================================

    /**
     * Build a [SCValXdr] with the type of [SCValTypeXdr.SCV_BYTES].
     *
     * @param bytes bytes to convert
     * @return [SCValXdr] with the type of [SCValTypeXdr.SCV_BYTES]
     */
    fun toBytes(bytes: ByteArray): SCValXdr {
        return SCValXdr.Bytes(SCBytesXdr(bytes))
    }

    /**
     * Convert from [SCValXdr] with the type of [SCValTypeXdr.SCV_BYTES] to ByteArray.
     *
     * @param scVal [SCValXdr] to convert
     * @return the bytes
     * @throws IllegalArgumentException if scVal type is not [SCValTypeXdr.SCV_BYTES]
     */
    fun fromBytes(scVal: SCValXdr): ByteArray {
        require(scVal is SCValXdr.Bytes) {
            "invalid scVal type, expected SCV_BYTES, but got ${scVal.discriminant}"
        }
        return scVal.value.value
    }

    // ============================================================================
    // Vec (Array/List)
    // ============================================================================

    /**
     * Build a [SCValXdr] with the type of [SCValTypeXdr.SCV_VEC].
     *
     * @param vec list of SCVal to convert
     * @return [SCValXdr] with the type of [SCValTypeXdr.SCV_VEC]
     */
    fun toVec(vec: List<SCValXdr>): SCValXdr {
        return SCValXdr.Vec(SCVecXdr(vec))
    }

    /**
     * Convert from [SCValXdr] with the type of [SCValTypeXdr.SCV_VEC] to List.
     *
     * @param scVal [SCValXdr] to convert
     * @return the vec
     * @throws IllegalArgumentException if scVal type is not [SCValTypeXdr.SCV_VEC]
     */
    fun fromVec(scVal: SCValXdr): List<SCValXdr> {
        require(scVal is SCValXdr.Vec) {
            "invalid scVal type, expected SCV_VEC, but got ${scVal.discriminant}"
        }
        return scVal.value?.value ?: emptyList()
    }

    // ============================================================================
    // Map
    // ============================================================================

    /**
     * Build a [SCValXdr] with the type of [SCValTypeXdr.SCV_MAP].
     *
     * Uses LinkedHashMap to preserve the order of map entries for deterministic XDR generation.
     *
     * @param map map to convert (order is preserved)
     * @return [SCValXdr] with the type of [SCValTypeXdr.SCV_MAP]
     */
    fun toMap(map: LinkedHashMap<SCValXdr, SCValXdr>): SCValXdr {
        val entries = map.map { (key, value) ->
            SCMapEntryXdr(key = key, `val` = value)
        }
        return SCValXdr.Map(SCMapXdr(entries))
    }

    /**
     * Convert from [SCValXdr] with the type of [SCValTypeXdr.SCV_MAP] to LinkedHashMap.
     *
     * @param scVal [SCValXdr] to convert
     * @return the map (order is preserved)
     * @throws IllegalArgumentException if scVal type is not [SCValTypeXdr.SCV_MAP]
     */
    fun fromMap(scVal: SCValXdr): LinkedHashMap<SCValXdr, SCValXdr> {
        require(scVal is SCValXdr.Map) {
            "invalid scVal type, expected SCV_MAP, but got ${scVal.discriminant}"
        }
        val map = LinkedHashMap<SCValXdr, SCValXdr>()
        scVal.value?.value?.forEach { entry ->
            map[entry.key] = entry.`val`
        }
        return map
    }

    // ============================================================================
    // Error
    // ============================================================================

    /**
     * Build a [SCValXdr] with the type of [SCValTypeXdr.SCV_ERROR].
     *
     * @param error [SCErrorXdr] to convert
     * @return [SCValXdr] with the type of [SCValTypeXdr.SCV_ERROR]
     */
    fun toError(error: SCErrorXdr): SCValXdr {
        return SCValXdr.Error(error)
    }

    /**
     * Convert from [SCValXdr] with the type of [SCValTypeXdr.SCV_ERROR] to [SCErrorXdr].
     *
     * @param scVal [SCValXdr] to convert
     * @return the error
     * @throws IllegalArgumentException if scVal type is not [SCValTypeXdr.SCV_ERROR]
     */
    fun fromError(scVal: SCValXdr): SCErrorXdr {
        require(scVal is SCValXdr.Error) {
            "invalid scVal type, expected SCV_ERROR, but got ${scVal.discriminant}"
        }
        return scVal.value
    }

    // ============================================================================
    // Contract Instance
    // ============================================================================

    /**
     * Build a [SCValXdr] with the type of [SCValTypeXdr.SCV_CONTRACT_INSTANCE].
     *
     * @param instance [SCContractInstanceXdr] to convert
     * @return [SCValXdr] with the type of [SCValTypeXdr.SCV_CONTRACT_INSTANCE]
     */
    fun toContractInstance(instance: SCContractInstanceXdr): SCValXdr {
        return SCValXdr.Instance(instance)
    }

    /**
     * Convert from [SCValXdr] with the type of [SCValTypeXdr.SCV_CONTRACT_INSTANCE] to [SCContractInstanceXdr].
     *
     * @param scVal [SCValXdr] to convert
     * @return the contract instance
     * @throws IllegalArgumentException if scVal type is not [SCValTypeXdr.SCV_CONTRACT_INSTANCE]
     */
    fun fromContractInstance(scVal: SCValXdr): SCContractInstanceXdr {
        require(scVal is SCValXdr.Instance) {
            "invalid scVal type, expected SCV_CONTRACT_INSTANCE, but got ${scVal.discriminant}"
        }
        return scVal.value
    }

    // ============================================================================
    // Ledger Key Contract Instance
    // ============================================================================

    /**
     * Build a [SCValXdr] with the type of [SCValTypeXdr.SCV_LEDGER_KEY_CONTRACT_INSTANCE].
     *
     * @return [SCValXdr] with the type of [SCValTypeXdr.SCV_LEDGER_KEY_CONTRACT_INSTANCE]
     */
    fun toLedgerKeyContractInstance(): SCValXdr {
        return SCValXdr.Void(SCValTypeXdr.SCV_LEDGER_KEY_CONTRACT_INSTANCE)
    }

    /**
     * Parse from [SCValXdr] with the type of [SCValTypeXdr.SCV_LEDGER_KEY_CONTRACT_INSTANCE].
     * This function validates the type and returns Unit if successful.
     *
     * @param scVal [SCValXdr] to convert
     * @throws IllegalArgumentException if scVal type is not [SCValTypeXdr.SCV_LEDGER_KEY_CONTRACT_INSTANCE]
     */
    fun fromLedgerKeyContractInstance(scVal: SCValXdr) {
        require(scVal.discriminant == SCValTypeXdr.SCV_LEDGER_KEY_CONTRACT_INSTANCE) {
            "invalid scVal type, expected SCV_LEDGER_KEY_CONTRACT_INSTANCE, but got ${scVal.discriminant}"
        }
    }

    // ============================================================================
    // Ledger Key Nonce
    // ============================================================================

    /**
     * Build a [SCValXdr] with the type of [SCValTypeXdr.SCV_LEDGER_KEY_NONCE].
     *
     * @param nonce nonce to convert
     * @return [SCValXdr] with the type of [SCValTypeXdr.SCV_LEDGER_KEY_NONCE]
     */
    fun toLedgerKeyNonce(nonce: SCNonceKeyXdr): SCValXdr {
        return SCValXdr.NonceKey(nonce)
    }

    /**
     * Convert from [SCValXdr] with the type of [SCValTypeXdr.SCV_LEDGER_KEY_NONCE] to [SCNonceKeyXdr].
     *
     * @param scVal [SCValXdr] to convert
     * @return the nonce
     * @throws IllegalArgumentException if scVal type is not [SCValTypeXdr.SCV_LEDGER_KEY_NONCE]
     */
    fun fromLedgerKeyNonce(scVal: SCValXdr): SCNonceKeyXdr {
        require(scVal is SCValXdr.NonceKey) {
            "invalid scVal type, expected SCV_LEDGER_KEY_NONCE, but got ${scVal.discriminant}"
        }
        return scVal.value
    }

    // ============================================================================
    // Address
    // ============================================================================

    /**
     * Build a [SCValXdr] with the type of [SCValTypeXdr.SCV_ADDRESS].
     *
     * @param address address to convert
     * @return [SCValXdr] with the type of [SCValTypeXdr.SCV_ADDRESS]
     */
    fun toAddress(address: SCAddressXdr): SCValXdr {
        return SCValXdr.Address(address)
    }

    /**
     * Convert from [SCValXdr] with the type of [SCValTypeXdr.SCV_ADDRESS] to [SCAddressXdr].
     *
     * @param scVal [SCValXdr] to convert
     * @return the address
     * @throws IllegalArgumentException if scVal type is not [SCValTypeXdr.SCV_ADDRESS]
     */
    fun fromAddress(scVal: SCValXdr): SCAddressXdr {
        require(scVal is SCValXdr.Address) {
            "invalid scVal type, expected SCV_ADDRESS, but got ${scVal.discriminant}"
        }
        return scVal.value
    }

    // ============================================================================
    // Internal utility functions for byte conversions
    // ============================================================================

    private fun longToBytes(value: Long): ByteArray {
        val result = ByteArray(8)
        var v = value
        for (i in 7 downTo 0) {
            result[i] = (v and 0xFF).toByte()
            v = v shr 8
        }
        return result
    }

    private fun bytesToLong(bytes: ByteArray): Long {
        require(bytes.size == 8) { "array length is not 8" }
        return ((bytes[0].toLong() and 0xFF) shl 56) or
                ((bytes[1].toLong() and 0xFF) shl 48) or
                ((bytes[2].toLong() and 0xFF) shl 40) or
                ((bytes[3].toLong() and 0xFF) shl 32) or
                ((bytes[4].toLong() and 0xFF) shl 24) or
                ((bytes[5].toLong() and 0xFF) shl 16) or
                ((bytes[6].toLong() and 0xFF) shl 8) or
                (bytes[7].toLong() and 0xFF)
    }

    private fun ulongToBytes(value: ULong): ByteArray {
        val result = ByteArray(8)
        var v = value
        for (i in 7 downTo 0) {
            result[i] = (v and 0xFFu).toByte()
            v = v shr 8
        }
        return result
    }

    private fun bytesToULong(bytes: ByteArray): ULong {
        require(bytes.size == 8) { "array length is not 8" }
        return ((bytes[0].toULong() and 0xFFu) shl 56) or
                ((bytes[1].toULong() and 0xFFu) shl 48) or
                ((bytes[2].toULong() and 0xFFu) shl 40) or
                ((bytes[3].toULong() and 0xFFu) shl 32) or
                ((bytes[4].toULong() and 0xFFu) shl 24) or
                ((bytes[5].toULong() and 0xFFu) shl 16) or
                ((bytes[6].toULong() and 0xFFu) shl 8) or
                (bytes[7].toULong() and 0xFFu)
    }
}
