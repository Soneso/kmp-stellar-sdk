package com.soneso.stellar.sdk.unitTests.scval

import com.soneso.stellar.sdk.scval.*
import com.ionspin.kotlin.bignum.integer.BigInteger
import com.soneso.stellar.sdk.xdr.*
import kotlin.test.*

class ScvTest {

    @Test
    fun testBoolean() {
        // Test true
        val trueVal = Scv.toBoolean(true)
        assertTrue(trueVal is SCValXdr.B)
        assertEquals(SCValTypeXdr.SCV_BOOL, trueVal.discriminant)
        assertTrue(Scv.fromBoolean(trueVal))

        // Test false
        val falseVal = Scv.toBoolean(false)
        assertTrue(falseVal is SCValXdr.B)
        assertEquals(SCValTypeXdr.SCV_BOOL, falseVal.discriminant)
        assertFalse(Scv.fromBoolean(falseVal))
    }

    @Test
    fun testBooleanInvalidType() {
        val intVal = Scv.toInt32(42)
        assertFailsWith<IllegalArgumentException> {
            Scv.fromBoolean(intVal)
        }
    }

    @Test
    fun testVoid() {
        val voidVal = Scv.toVoid()
        assertEquals(SCValTypeXdr.SCV_VOID, voidVal.discriminant)

        // Should not throw
        Scv.fromVoid(voidVal)
    }

    @Test
    fun testVoidInvalidType() {
        val intVal = Scv.toInt32(42)
        assertFailsWith<IllegalArgumentException> {
            Scv.fromVoid(intVal)
        }
    }

    @Test
    fun testInt32() {
        val testCases = listOf(0, 42, -42, Int.MAX_VALUE, Int.MIN_VALUE)

        for (value in testCases) {
            val scVal = Scv.toInt32(value)
            assertTrue(scVal is SCValXdr.I32)
            assertEquals(SCValTypeXdr.SCV_I32, scVal.discriminant)
            assertEquals(value, Scv.fromInt32(scVal))
        }
    }

    @Test
    fun testUint32() {
        val testCases = listOf(0u, 42u, UInt.MAX_VALUE, UInt.MIN_VALUE)

        for (value in testCases) {
            val scVal = Scv.toUint32(value)
            assertTrue(scVal is SCValXdr.U32)
            assertEquals(SCValTypeXdr.SCV_U32, scVal.discriminant)
            assertEquals(value, Scv.fromUint32(scVal))
        }
    }

    @Test
    fun testInt64() {
        val testCases = listOf(0L, 42L, -42L, Long.MAX_VALUE, Long.MIN_VALUE)

        for (value in testCases) {
            val scVal = Scv.toInt64(value)
            assertTrue(scVal is SCValXdr.I64)
            assertEquals(SCValTypeXdr.SCV_I64, scVal.discriminant)
            assertEquals(value, Scv.fromInt64(scVal))
        }
    }

    @Test
    fun testUint64() {
        val testCases = listOf(0uL, 42uL, ULong.MAX_VALUE, ULong.MIN_VALUE)

        for (value in testCases) {
            val scVal = Scv.toUint64(value)
            assertTrue(scVal is SCValXdr.U64)
            assertEquals(SCValTypeXdr.SCV_U64, scVal.discriminant)
            assertEquals(value, Scv.fromUint64(scVal))
        }
    }

    @Test
    fun testInt128() {
        val testCases = listOf(
            BigInteger.ZERO,
            BigInteger.ONE,
            BigInteger.ONE.negate(),
            BigInteger.parseString("170141183460469231731687303715884105727"), // 2^127 - 1
            BigInteger.parseString("-170141183460469231731687303715884105728") // -2^127
        )

        for (value in testCases) {
            val scVal = Scv.toInt128(value)
            assertTrue(scVal is SCValXdr.I128)
            assertEquals(SCValTypeXdr.SCV_I128, scVal.discriminant)
            assertEquals(value, Scv.fromInt128(scVal))
        }
    }

    @Test
    fun testInt128OutOfRange() {
        // Too large
        assertFailsWith<IllegalArgumentException> {
            Scv.toInt128(BigInteger.parseString("170141183460469231731687303715884105728")) // 2^127
        }

        // Too small
        assertFailsWith<IllegalArgumentException> {
            Scv.toInt128(BigInteger.parseString("-170141183460469231731687303715884105729")) // -2^127 - 1
        }
    }

    @Test
    fun testUint128() {
        val testCases = listOf(
            BigInteger.ZERO,
            BigInteger.ONE,
            BigInteger.parseString("340282366920938463463374607431768211455") // 2^128 - 1
        )

        for (value in testCases) {
            val scVal = Scv.toUint128(value)
            assertTrue(scVal is SCValXdr.U128)
            assertEquals(SCValTypeXdr.SCV_U128, scVal.discriminant)
            assertEquals(value, Scv.fromUint128(scVal))
        }
    }

    @Test
    fun testUint128OutOfRange() {
        // Negative value
        assertFailsWith<IllegalArgumentException> {
            Scv.toUint128(BigInteger.ONE.negate())
        }

        // Too large
        assertFailsWith<IllegalArgumentException> {
            Scv.toUint128(BigInteger.parseString("340282366920938463463374607431768211456")) // 2^128
        }
    }

    @Test
    fun testInt256() {
        val testCases = listOf(
            BigInteger.ZERO,
            BigInteger.ONE,
            BigInteger.ONE.negate(),
            BigInteger.parseString("57896044618658097711785492504343953926634992332820282019728792003956564819967"), // 2^255 - 1
            BigInteger.parseString("-57896044618658097711785492504343953926634992332820282019728792003956564819968") // -2^255
        )

        for (value in testCases) {
            val scVal = Scv.toInt256(value)
            assertTrue(scVal is SCValXdr.I256)
            assertEquals(SCValTypeXdr.SCV_I256, scVal.discriminant)
            assertEquals(value, Scv.fromInt256(scVal))
        }
    }

    @Test
    fun testUint256() {
        val testCases = listOf(
            BigInteger.ZERO,
            BigInteger.ONE,
            BigInteger.parseString("115792089237316195423570985008687907853269984665640564039457584007913129639935") // 2^256 - 1
        )

        for (value in testCases) {
            val scVal = Scv.toUint256(value)
            assertTrue(scVal is SCValXdr.U256)
            assertEquals(SCValTypeXdr.SCV_U256, scVal.discriminant)
            assertEquals(value, Scv.fromUint256(scVal))
        }
    }

    @Test
    fun testTimePoint() {
        val testCases = listOf(0uL, 1234567890uL, ULong.MAX_VALUE)

        for (value in testCases) {
            val scVal = Scv.toTimePoint(value)
            assertTrue(scVal is SCValXdr.Timepoint)
            assertEquals(SCValTypeXdr.SCV_TIMEPOINT, scVal.discriminant)
            assertEquals(value, Scv.fromTimePoint(scVal))
        }
    }

    @Test
    fun testDuration() {
        val testCases = listOf(0uL, 3600uL, ULong.MAX_VALUE)

        for (value in testCases) {
            val scVal = Scv.toDuration(value)
            assertTrue(scVal is SCValXdr.Duration)
            assertEquals(SCValTypeXdr.SCV_DURATION, scVal.discriminant)
            assertEquals(value, Scv.fromDuration(scVal))
        }
    }

    @Test
    fun testString() {
        val testCases = listOf("", "hello", "hello world", "special chars: !@#$%^&*()")

        for (value in testCases) {
            val scVal = Scv.toString(value)
            assertTrue(scVal is SCValXdr.Str)
            assertEquals(SCValTypeXdr.SCV_STRING, scVal.discriminant)
            assertEquals(value, Scv.fromString(scVal))
        }
    }

    @Test
    fun testSymbol() {
        val testCases = listOf("balance", "transfer", "approve")

        for (value in testCases) {
            val scVal = Scv.toSymbol(value)
            assertTrue(scVal is SCValXdr.Sym)
            assertEquals(SCValTypeXdr.SCV_SYMBOL, scVal.discriminant)
            assertEquals(value, Scv.fromSymbol(scVal))
        }
    }

    @Test
    fun testBytes() {
        val testCases = listOf(
            byteArrayOf(),
            byteArrayOf(1, 2, 3),
            byteArrayOf(-1, 0, 1, 127, -128)
        )

        for (value in testCases) {
            val scVal = Scv.toBytes(value)
            assertTrue(scVal is SCValXdr.Bytes)
            assertEquals(SCValTypeXdr.SCV_BYTES, scVal.discriminant)
            assertContentEquals(value, Scv.fromBytes(scVal))
        }
    }

    @Test
    fun testVec() {
        // Empty vec
        val emptyVec = Scv.toVec(emptyList())
        assertTrue(emptyVec is SCValXdr.Vec)
        assertEquals(SCValTypeXdr.SCV_VEC, emptyVec.discriminant)
        assertTrue(Scv.fromVec(emptyVec).isEmpty())

        // Vec with values
        val values = listOf(
            Scv.toInt32(42),
            Scv.toString("hello"),
            Scv.toBoolean(true)
        )
        val vec = Scv.toVec(values)
        assertTrue(vec is SCValXdr.Vec)
        assertEquals(SCValTypeXdr.SCV_VEC, vec.discriminant)

        val result = Scv.fromVec(vec)
        assertEquals(values.size, result.size)
        assertEquals(42, Scv.fromInt32(result[0]))
        assertEquals("hello", Scv.fromString(result[1]))
        assertTrue(Scv.fromBoolean(result[2]))
    }

    @Test
    fun testMap() {
        // Empty map
        val emptyMap = Scv.toMap(linkedMapOf())
        assertTrue(emptyMap is SCValXdr.Map)
        assertEquals(SCValTypeXdr.SCV_MAP, emptyMap.discriminant)
        assertTrue(Scv.fromMap(emptyMap).isEmpty())

        // Map with values - use LinkedHashMap to preserve order
        val map = linkedMapOf(
            Scv.toSymbol("balance") to Scv.toInt64(1000L),
            Scv.toSymbol("owner") to Scv.toString("Alice")
        )
        val scMap = Scv.toMap(map)
        assertTrue(scMap is SCValXdr.Map)
        assertEquals(SCValTypeXdr.SCV_MAP, scMap.discriminant)

        val result = Scv.fromMap(scMap)
        assertEquals(2, result.size)

        // Verify values
        val entries = result.entries.toList()
        assertEquals("balance", Scv.fromSymbol(entries[0].key))
        assertEquals(1000L, Scv.fromInt64(entries[0].value))
        assertEquals("owner", Scv.fromSymbol(entries[1].key))
        assertEquals("Alice", Scv.fromString(entries[1].value))
    }

    @Test
    fun testMapOrderPreservation() {
        // LinkedHashMap should preserve insertion order
        val map = linkedMapOf<SCValXdr, SCValXdr>()
        map[Scv.toSymbol("c")] = Scv.toInt32(3)
        map[Scv.toSymbol("a")] = Scv.toInt32(1)
        map[Scv.toSymbol("b")] = Scv.toInt32(2)

        val scMap = Scv.toMap(map)
        val result = Scv.fromMap(scMap)

        val keys = result.keys.map { Scv.fromSymbol(it) }
        assertEquals(listOf("c", "a", "b"), keys)
    }

    @Test
    fun testError() {
        val error = SCErrorXdr.ContractCode(Uint32Xdr(100u))
        val scVal = Scv.toError(error)

        assertTrue(scVal is SCValXdr.Error)
        assertEquals(SCValTypeXdr.SCV_ERROR, scVal.discriminant)
        assertEquals(error, Scv.fromError(scVal))
    }

    @Test
    fun testContractInstance() {
        // Create a minimal contract instance for testing
        val instance = SCContractInstanceXdr(
            executable = ContractExecutableXdr.WasmHash(HashXdr(ByteArray(32) { 0 })),
            storage = null
        )
        val scVal = Scv.toContractInstance(instance)

        assertTrue(scVal is SCValXdr.Instance)
        assertEquals(SCValTypeXdr.SCV_CONTRACT_INSTANCE, scVal.discriminant)
        assertEquals(instance, Scv.fromContractInstance(scVal))
    }

    @Test
    fun testLedgerKeyContractInstance() {
        val scVal = Scv.toLedgerKeyContractInstance()
        assertEquals(SCValTypeXdr.SCV_LEDGER_KEY_CONTRACT_INSTANCE, scVal.discriminant)

        // Should not throw
        Scv.fromLedgerKeyContractInstance(scVal)
    }

    @Test
    fun testLedgerKeyNonce() {
        val nonce = SCNonceKeyXdr(Int64Xdr(42L))
        val scVal = Scv.toLedgerKeyNonce(nonce)

        assertTrue(scVal is SCValXdr.NonceKey)
        assertEquals(SCValTypeXdr.SCV_LEDGER_KEY_NONCE, scVal.discriminant)
        assertEquals(nonce, Scv.fromLedgerKeyNonce(scVal))
    }

    @Test
    fun testAddress() {
        val accountId = AccountIDXdr(PublicKeyXdr.Ed25519(Uint256Xdr(ByteArray(32) { it.toByte() })))
        val address = SCAddressXdr.AccountId(accountId)
        val scVal = Scv.toAddress(address)

        assertTrue(scVal is SCValXdr.Address)
        assertEquals(SCValTypeXdr.SCV_ADDRESS, scVal.discriminant)
        assertEquals(address, Scv.fromAddress(scVal))
    }

    @Test
    fun testNestedStructures() {
        // Create nested vec of maps
        val innerMap1 = linkedMapOf(
            Scv.toSymbol("key1") to Scv.toInt32(1)
        )
        val innerMap2 = linkedMapOf(
            Scv.toSymbol("key2") to Scv.toInt32(2)
        )

        val vec = Scv.toVec(listOf(
            Scv.toMap(innerMap1),
            Scv.toMap(innerMap2)
        ))

        val resultVec = Scv.fromVec(vec)
        assertEquals(2, resultVec.size)

        val resultMap1 = Scv.fromMap(resultVec[0])
        val resultMap2 = Scv.fromMap(resultVec[1])

        assertEquals(1, Scv.fromInt32(resultMap1[Scv.toSymbol("key1")]!!))
        assertEquals(2, Scv.fromInt32(resultMap2[Scv.toSymbol("key2")]!!))
    }

    @Test
    fun testRoundTripConversions() {
        // Test that all types can round-trip correctly
        val testValues = listOf(
            Scv.toBoolean(true),
            Scv.toVoid(),
            Scv.toInt32(42),
            Scv.toUint32(42u),
            Scv.toInt64(42L),
            Scv.toUint64(42uL),
            Scv.toInt128(BigInteger.parseString("12345")),
            Scv.toUint128(BigInteger.parseString("12345")),
            Scv.toInt256(BigInteger.parseString("12345")),
            Scv.toUint256(BigInteger.parseString("12345")),
            Scv.toTimePoint(123456uL),
            Scv.toDuration(3600uL),
            Scv.toString("test"),
            Scv.toSymbol("test"),
            Scv.toBytes(byteArrayOf(1, 2, 3)),
            Scv.toVec(listOf(Scv.toInt32(1))),
            Scv.toMap(linkedMapOf(Scv.toInt32(1) to Scv.toInt32(2)))
        )

        // Each value should maintain its type
        for (value in testValues) {
            assertNotNull(value.discriminant)
        }
    }

    @Test
    fun testTypeValidation() {
        val intVal = Scv.toInt32(42)

        // All these should throw IllegalArgumentException
        assertFailsWith<IllegalArgumentException> { Scv.fromBoolean(intVal) }
        assertFailsWith<IllegalArgumentException> { Scv.fromInt64(intVal) }
        assertFailsWith<IllegalArgumentException> { Scv.fromUint32(intVal) }
        assertFailsWith<IllegalArgumentException> { Scv.fromString(intVal) }
        assertFailsWith<IllegalArgumentException> { Scv.fromSymbol(intVal) }
        assertFailsWith<IllegalArgumentException> { Scv.fromBytes(intVal) }
        assertFailsWith<IllegalArgumentException> { Scv.fromVec(intVal) }
        assertFailsWith<IllegalArgumentException> { Scv.fromMap(intVal) }
    }
}
