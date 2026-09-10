// Copyright 2026 Soneso. All rights reserved.
// Use of this source code is governed by a license that can be
// found in the LICENSE file.

package com.soneso.stellar.sdk.unitTests.scval

import com.ionspin.kotlin.bignum.integer.BigInteger
import com.soneso.stellar.sdk.Address
import com.soneso.stellar.sdk.Util
import com.soneso.stellar.sdk.scval.Scv
import com.soneso.stellar.sdk.scval.toNative
import com.soneso.stellar.sdk.xdr.ContractExecutableXdr
import com.soneso.stellar.sdk.xdr.ContractIDXdr
import com.soneso.stellar.sdk.xdr.HashXdr
import com.soneso.stellar.sdk.xdr.Int64Xdr
import com.soneso.stellar.sdk.xdr.SCAddressXdr
import com.soneso.stellar.sdk.xdr.SCContractInstanceXdr
import com.soneso.stellar.sdk.xdr.SCErrorCodeXdr
import com.soneso.stellar.sdk.xdr.SCErrorTypeXdr
import com.soneso.stellar.sdk.xdr.SCErrorXdr
import com.soneso.stellar.sdk.xdr.SCMapEntryXdr
import com.soneso.stellar.sdk.xdr.SCMapXdr
import com.soneso.stellar.sdk.xdr.SCNonceKeyXdr
import com.soneso.stellar.sdk.xdr.SCValTypeXdr
import com.soneso.stellar.sdk.xdr.SCValXdr
import com.soneso.stellar.sdk.xdr.fromXdrBase64
import com.soneso.stellar.sdk.xdr.toXdrBase64
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ScValToNativeTest {
    private val accountId = "GA7QYNF7SOWQ3GLR2BGMZEHXAVIRZA4KVWLTJJFC7MGXUA74P7UJVSGZ"
    private val contractId = "CA7QYNF7SOWQ3GLR2BGMZEHXAVIRZA4KVWLTJJFC7MGXUA74P7UJUWDA"
    private val muxedAccountId = "MAQAA5L65LSYH7CQ3VTJ7F3HHLGCL3DSLAR2Y47263D56MNNGHSQSAAAAAAAAAAE2LP26"
    private val claimableBalanceId = "BAAD6DBUX6J22DMZOHIEZTEQ64CVCHEDRKWZONFEUL5Q26QD7R76RGR4TU"
    private val liquidityPoolId = "LA7QYNF7SOWQ3GLR2BGMZEHXAVIRZA4KVWLTJJFC7MGXUA74P7UJUPJN"

    private fun scMap(vararg entries: Pair<SCValXdr, SCValXdr>): SCValXdr =
        Scv.toMap(linkedMapOf(*entries))

    private fun nativeMap(scVal: SCValXdr): Map<*, *> = assertIs<Map<*, *>>(scVal.toNative())

    private fun addressValue(strKey: String): SCValXdr = Scv.toAddress(Address(strKey).toSCAddress())

    private fun errorValue(): SCValXdr =
        Scv.toError(SCErrorXdr.Code(SCErrorTypeXdr.SCE_CONTEXT, SCErrorCodeXdr.SCEC_INVALID_INPUT))

    private fun instanceValue(): SCValXdr =
        Scv.toContractInstance(SCContractInstanceXdr(ContractExecutableXdr.Void, null))

    private fun nonceKeyValue(): SCValXdr = Scv.toLedgerKeyNonce(SCNonceKeyXdr(Int64Xdr(42L)))

    /**
     * A contract address whose hash is one byte short, which the strkey encoder rejects.
     */
    private fun illFormedAddress(): SCAddressXdr =
        SCAddressXdr.ContractId(ContractIDXdr(HashXdr(ByteArray(31))))

    /**
     * Asserts that a map holding the two entries falls back in both insertion orders.
     */
    private fun assertMapFallsBackInEitherOrder(first: Pair<SCValXdr, SCValXdr>, second: Pair<SCValXdr, SCValXdr>) {
        val firstThenSecond = scMap(first, second)
        assertSame(firstThenSecond, firstThenSecond.toNative())

        val secondThenFirst = scMap(second, first)
        assertSame(secondThenFirst, secondThenFirst.toNative())
    }

    // ------------------------------------------------------------------
    // Scalars
    // ------------------------------------------------------------------

    @Test
    fun testBoolean() {
        assertEquals(true, Scv.toBoolean(true).toNative())
        assertEquals(false, Scv.toBoolean(false).toNative())
    }

    @Test
    fun testVoid() {
        assertNull(Scv.toVoid().toNative())
    }

    @Test
    fun testLedgerKeyContractInstance() {
        val scVal = Scv.toLedgerKeyContractInstance()
        assertSame(scVal, scVal.toNative())
    }

    @Test
    fun testVoidWithForeignDiscriminant() {
        val scVal = SCValXdr.Void(SCValTypeXdr.SCV_U32)
        assertSame(scVal, scVal.toNative())
    }

    @Test
    fun testUint32() {
        val zero = Scv.toUint32(0u).toNative()
        assertIs<UInt>(zero)
        assertEquals(0u, zero)

        val max = Scv.toUint32(4294967295u).toNative()
        assertIs<UInt>(max)
        assertEquals(4294967295u, max)
    }

    @Test
    fun testInt32() {
        val min = Scv.toInt32(Int.MIN_VALUE).toNative()
        assertIs<Int>(min)
        assertEquals(Int.MIN_VALUE, min)

        val max = Scv.toInt32(Int.MAX_VALUE).toNative()
        assertIs<Int>(max)
        assertEquals(Int.MAX_VALUE, max)
    }

    @Test
    fun testUint64() {
        val zero = Scv.toUint64(0u).toNative()
        assertTrue(zero is ULong)
        assertEquals(0uL, zero)

        val max = Scv.toUint64(ULong.MAX_VALUE).toNative()
        assertTrue(max is ULong)
        assertEquals(18446744073709551615uL, max)
    }

    @Test
    fun testInt64() {
        val min = Scv.toInt64(Long.MIN_VALUE).toNative()
        assertIs<Long>(min)
        assertEquals(Long.MIN_VALUE, min)

        val max = Scv.toInt64(Long.MAX_VALUE).toNative()
        assertIs<Long>(max)
        assertEquals(Long.MAX_VALUE, max)
    }

    @Test
    fun testTimepoint() {
        val result = Scv.toTimePoint(1700000000u).toNative()
        assertIs<ULong>(result)
        assertEquals(1700000000uL, result)
    }

    @Test
    fun testDuration() {
        val result = Scv.toDuration(ULong.MAX_VALUE).toNative()
        assertIs<ULong>(result)
        assertEquals(ULong.MAX_VALUE, result)
    }

    @Test
    fun testUint128() {
        val value = BigInteger.parseString("340282366920938463463374607431768211455")
        val result = Scv.toUint128(value).toNative()
        assertIs<BigInteger>(result)
        assertEquals(value, result)
    }

    @Test
    fun testInt128() {
        val min = BigInteger.parseString("-170141183460469231731687303715884105728")
        val minResult = Scv.toInt128(min).toNative()
        assertIs<BigInteger>(minResult)
        assertEquals(min, minResult)

        val minusOne = BigInteger.parseString("-1")
        val minusOneResult = Scv.toInt128(minusOne).toNative()
        assertIs<BigInteger>(minusOneResult)
        assertEquals(minusOne, minusOneResult)
    }

    @Test
    fun testUint256() {
        val value = BigInteger.parseString(
            "115792089237316195423570985008687907853269984665640564039457584007913129639935"
        )
        val result = Scv.toUint256(value).toNative()
        assertIs<BigInteger>(result)
        assertEquals(value, result)
    }

    @Test
    fun testInt256() {
        val value = BigInteger.parseString(
            "-57896044618658097711785492504343953926634992332820282019728792003956564819968"
        )
        val result = Scv.toInt256(value).toNative()
        assertIs<BigInteger>(result)
        assertEquals(value, result)
    }

    @Test
    fun testBytes() {
        val bytes = byteArrayOf(0, 1, 255.toByte())
        val scVal = Scv.toBytes(bytes)
        val result = scVal.toNative()
        assertIs<ByteArray>(result)
        assertContentEquals(byteArrayOf(0, 1, 255.toByte()), result)
        assertSame(Scv.fromBytes(scVal), result)
    }

    @Test
    fun testString() {
        assertEquals("hello", Scv.toString("hello").toNative())
        assertEquals("Grüße, 世界", Scv.toString("Grüße, 世界").toNative())
    }

    @Test
    fun testSymbol() {
        val result = Scv.toSymbol("transfer").toNative()
        assertIs<String>(result)
        assertEquals("transfer", result)
    }

    // ------------------------------------------------------------------
    // Vec
    // ------------------------------------------------------------------

    @Test
    fun testNestedVec() {
        val scVal = Scv.toVec(
            listOf(Scv.toUint32(1u), Scv.toSymbol("a"), Scv.toVec(listOf(Scv.toBoolean(true))))
        )
        val result = assertIs<List<*>>(scVal.toNative())
        assertEquals(3, result.size)
        assertIs<UInt>(result[0])
        assertEquals(1u, result[0])
        assertIs<String>(result[1])
        assertEquals("a", result[1])
        val inner = assertIs<List<*>>(result[2])
        assertEquals(listOf(true), inner)
        assertEquals(listOf(1u, "a", listOf(true)), result)
    }

    @Test
    fun testEmptyVec() {
        assertEquals(emptyList<Any?>(), Scv.toVec(emptyList()).toNative())
    }

    @Test
    fun testVecWithoutPayload() {
        assertEquals(emptyList<Any?>(), SCValXdr.Vec(null).toNative())
    }

    @Test
    fun testVecWithVoidElement() {
        val result = assertIs<List<*>>(Scv.toVec(listOf(Scv.toVoid(), Scv.toUint32(1u))).toNative())
        assertEquals(2, result.size)
        assertNull(result[0])
        assertEquals(1u, result[1])
    }

    @Test
    fun testVecContainsMapFallback() {
        val mapWithVecKey = scMap(Scv.toVec(listOf(Scv.toUint32(1u))) to Scv.toUint32(2u))
        val result = assertIs<List<*>>(Scv.toVec(listOf(Scv.toUint32(7u), mapWithVecKey)).toNative())
        assertEquals(2, result.size)
        assertEquals(7u, result[0])
        assertSame(mapWithVecKey, result[1])
    }

    @Test
    fun testVecIsNotMutated() {
        val scVal = Scv.toVec(listOf(Scv.toUint32(1u), Scv.toSymbol("a")))
        val elements = (scVal as SCValXdr.Vec).value!!.value
        val snapshot = elements.toList()

        scVal.toNative()

        assertSame(elements, scVal.value!!.value)
        assertEquals(snapshot, scVal.value!!.value)
    }

    // ------------------------------------------------------------------
    // Map
    // ------------------------------------------------------------------

    @Test
    fun testMapWithSymbolKeys() {
        val result = nativeMap(
            scMap(Scv.toSymbol("name") to Scv.toString("Alice"), Scv.toSymbol("age") to Scv.toUint32(30u))
        )
        assertEquals(2, result.size)
        assertEquals("Alice", result["name"])
        assertEquals(30u, result["age"])
        assertEquals(listOf("name", "age"), result.keys.toList())
    }

    @Test
    fun testMapWithUint32Keys() {
        val result = nativeMap(
            scMap(Scv.toUint32(1u) to Scv.toSymbol("one"), Scv.toUint32(2u) to Scv.toSymbol("two"))
        )
        assertEquals(2, result.size)
        assertEquals("one", result[1u])
        assertEquals("two", result[2u])
        assertEquals(listOf(1u, 2u), result.keys.toList())
    }

    @Test
    fun testMapWithInt64Key() {
        val result = nativeMap(scMap(Scv.toInt64(-5L) to Scv.toSymbol("minus five")))
        assertEquals(1, result.size)
        assertEquals("minus five", result[-5L])
    }

    @Test
    fun testMapWithUint64Key() {
        val result = nativeMap(scMap(Scv.toUint64(ULong.MAX_VALUE) to Scv.toSymbol("max")))
        assertEquals(1, result.size)
        assertEquals("max", result[ULong.MAX_VALUE])
        assertIs<ULong>(result.keys.single())
    }

    @Test
    fun testMapWithInt128Key() {
        val key = BigInteger.parseString("170141183460469231731687303715884105727")
        val result = nativeMap(scMap(Scv.toInt128(key) to Scv.toSymbol("big")))
        assertEquals(1, result.size)
        assertEquals("big", result[key])
    }

    @Test
    fun testMapWithBooleanKey() {
        val result = nativeMap(scMap(Scv.toBoolean(true) to Scv.toSymbol("yes")))
        assertEquals(1, result.size)
        assertEquals("yes", result[true])
    }

    @Test
    fun testMapWithVoidKey() {
        val result = nativeMap(scMap(Scv.toVoid() to Scv.toSymbol("nothing")))
        assertEquals(1, result.size)
        assertTrue(result.containsKey(null))
        assertEquals("nothing", result[null])
    }

    @Test
    fun testMapWithBytesKey() {
        val result = nativeMap(scMap(Scv.toBytes(byteArrayOf(1, 2)) to Scv.toSymbol("hex")))
        assertEquals(1, result.size)
        assertEquals("hex", result["0102"])
        assertEquals(Util.bytesToHex(byteArrayOf(1, 2)), result.keys.single())
    }

    @Test
    fun testMapWithBytesKeyUsesLowercaseHex() {
        val result = nativeMap(scMap(Scv.toBytes(byteArrayOf(0xAB.toByte(), 0xCD.toByte())) to Scv.toSymbol("hex")))
        assertEquals(1, result.size)
        assertEquals("hex", result["abcd"])
        assertFalse(result.containsKey("ABCD"))
        assertEquals("abcd", result.keys.single())
    }

    @Test
    fun testMapWithAddressKeys() {
        val result = nativeMap(
            scMap(
                addressValue(accountId) to Scv.toSymbol("account"),
                addressValue(contractId) to Scv.toSymbol("contract"),
                addressValue(muxedAccountId) to Scv.toSymbol("muxed"),
                addressValue(claimableBalanceId) to Scv.toSymbol("claimable balance"),
                addressValue(liquidityPoolId) to Scv.toSymbol("liquidity pool")
            )
        )
        assertEquals(5, result.size)
        assertEquals("account", result[accountId])
        assertEquals("contract", result[contractId])
        assertEquals("muxed", result[muxedAccountId])
        assertEquals("claimable balance", result[claimableBalanceId])
        assertEquals("liquidity pool", result[liquidityPoolId])
        assertEquals(
            listOf(accountId, contractId, muxedAccountId, claimableBalanceId, liquidityPoolId),
            result.keys.toList()
        )
    }

    @Test
    fun testMapWithRemainingScalarKeyArms() {
        val u128 = BigInteger.parseString("340282366920938463463374607431768211455")
        val u256 = BigInteger.parseString(
            "115792089237316195423570985008687907853269984665640564039457584007913129639935"
        )
        val i256 = BigInteger.parseString(
            "-57896044618658097711785492504343953926634992332820282019728792003956564819968"
        )
        val result = nativeMap(
            scMap(
                Scv.toString("text") to Scv.toUint32(1u),
                Scv.toInt32(Int.MIN_VALUE) to Scv.toUint32(2u),
                Scv.toTimePoint(1700000000u) to Scv.toUint32(3u),
                Scv.toDuration(ULong.MAX_VALUE) to Scv.toUint32(4u),
                Scv.toUint128(u128) to Scv.toUint32(5u),
                Scv.toUint256(u256) to Scv.toUint32(6u),
                Scv.toInt256(i256) to Scv.toUint32(7u)
            )
        )
        assertEquals(7, result.size)
        assertEquals(1u, result["text"])
        assertEquals(2u, result[Int.MIN_VALUE])
        assertEquals(3u, result[1700000000uL])
        assertEquals(4u, result[ULong.MAX_VALUE])
        assertEquals(5u, result[u128])
        assertEquals(6u, result[u256])
        assertEquals(7u, result[i256])
        assertEquals(
            listOf("text", Int.MIN_VALUE, 1700000000uL, ULong.MAX_VALUE, u128, u256, i256),
            result.keys.toList()
        )
    }

    @Test
    fun testMapWithVoidValue() {
        val result = nativeMap(scMap(Scv.toSymbol("a") to Scv.toVoid(), Scv.toSymbol("b") to Scv.toUint32(1u)))
        assertEquals(2, result.size)
        assertTrue(result.containsKey("a"))
        assertNull(result["a"])
        assertEquals(1u, result["b"])
    }

    @Test
    fun testMapContainsFallbackValue() {
        val error = errorValue()
        val result = nativeMap(scMap(Scv.toSymbol("k") to error))
        assertEquals(1, result.size)
        assertSame(error, result["k"])
    }

    @Test
    fun testMapContainsMapFallbackValue() {
        val innerMap = scMap(Scv.toVec(listOf(Scv.toUint32(1u))) to Scv.toUint32(2u))
        val result = nativeMap(scMap(Scv.toSymbol("inner") to innerMap, Scv.toSymbol("n") to Scv.toUint32(1u)))
        assertEquals(2, result.size)
        assertSame(innerMap, result["inner"])
        assertEquals(1u, result["n"])
        assertEquals(listOf("inner", "n"), result.keys.toList())
    }

    @Test
    fun testEmptyMap() {
        val result = nativeMap(Scv.toMap(LinkedHashMap()))
        assertTrue(result.isEmpty())
    }

    @Test
    fun testMapWithoutPayload() {
        val result = nativeMap(SCValXdr.Map(null))
        assertTrue(result.isEmpty())
    }

    @Test
    fun testMapIsNotMutated() {
        val scVal = scMap(Scv.toSymbol("a") to Scv.toUint32(1u), Scv.toSymbol("b") to Scv.toUint32(2u))
        val entries = (scVal as SCValXdr.Map).value!!.value
        val snapshot = entries.toList()

        nativeMap(scVal)

        assertSame(entries, scVal.value!!.value)
        assertEquals(snapshot, scVal.value!!.value)
    }

    // ------------------------------------------------------------------
    // Map key distinctness
    // ------------------------------------------------------------------

    @Test
    fun testUint32AndUint64KeysAreDistinct() {
        val result = nativeMap(scMap(Scv.toUint32(5u) to Scv.toSymbol("u32"), Scv.toUint64(5u) to Scv.toSymbol("u64")))
        assertEquals(2, result.size)
        assertEquals("u32", result[5u])
        assertEquals("u64", result[5uL])
        assertEquals(listOf<Any>(5u, 5uL), result.keys.toList())
    }

    @Test
    fun testUint64AndInt64KeysAreDistinct() {
        val result = nativeMap(scMap(Scv.toUint64(5u) to Scv.toSymbol("u64"), Scv.toInt64(5L) to Scv.toSymbol("i64")))
        assertEquals(2, result.size)
        assertEquals("u64", result[5uL])
        assertEquals("i64", result[5L])
        assertEquals(listOf<Any>(5uL, 5L), result.keys.toList())
    }

    @Test
    fun testBooleanAndUint32KeysAreDistinct() {
        val result = nativeMap(scMap(Scv.toBoolean(true) to Scv.toSymbol("bool"), Scv.toUint32(1u) to Scv.toSymbol("u32")))
        assertEquals(2, result.size)
        assertEquals("bool", result[true])
        assertEquals("u32", result[1u])
        assertEquals(listOf<Any>(true, 1u), result.keys.toList())
    }

    @Test
    fun testSymbolAndUint32KeysAreDistinct() {
        val result = nativeMap(scMap(Scv.toSymbol("5") to Scv.toSymbol("sym"), Scv.toUint32(5u) to Scv.toSymbol("u32")))
        assertEquals(2, result.size)
        assertEquals("sym", result["5"])
        assertEquals("u32", result[5u])
        assertEquals(listOf<Any>("5", 5u), result.keys.toList())
    }

    @Test
    fun testUint64AndInt128KeysOfDifferentValueAreDistinct() {
        val six = BigInteger.parseString("6")

        val primitiveFirst = nativeMap(
            scMap(Scv.toUint64(5u) to Scv.toSymbol("u64"), Scv.toInt128(six) to Scv.toSymbol("i128"))
        )
        assertEquals(2, primitiveFirst.size)
        assertEquals("u64", primitiveFirst[5uL])
        assertEquals("i128", primitiveFirst[six])
        assertEquals(listOf<Any>(5uL, six), primitiveFirst.keys.toList())

        val wideFirst = nativeMap(
            scMap(Scv.toInt128(six) to Scv.toSymbol("i128"), Scv.toUint64(5u) to Scv.toSymbol("u64"))
        )
        assertEquals(2, wideFirst.size)
        assertEquals("i128", wideFirst[six])
        assertEquals("u64", wideFirst[5uL])
        assertEquals(listOf<Any>(six, 5uL), wideFirst.keys.toList())
    }

    @Test
    fun testUint64MaxAndNegativeInt128KeysAreDistinct() {
        val minusOne = BigInteger.parseString("-1")

        val primitiveFirst = nativeMap(
            scMap(Scv.toUint64(ULong.MAX_VALUE) to Scv.toSymbol("u64"), Scv.toInt128(minusOne) to Scv.toSymbol("i128"))
        )
        assertEquals(2, primitiveFirst.size)
        assertEquals("u64", primitiveFirst[ULong.MAX_VALUE])
        assertEquals("i128", primitiveFirst[minusOne])
        assertEquals(listOf<Any>(ULong.MAX_VALUE, minusOne), primitiveFirst.keys.toList())

        val wideFirst = nativeMap(
            scMap(Scv.toInt128(minusOne) to Scv.toSymbol("i128"), Scv.toUint64(ULong.MAX_VALUE) to Scv.toSymbol("u64"))
        )
        assertEquals(2, wideFirst.size)
        assertEquals("i128", wideFirst[minusOne])
        assertEquals("u64", wideFirst[ULong.MAX_VALUE])
        assertEquals(listOf<Any>(minusOne, ULong.MAX_VALUE), wideFirst.keys.toList())
    }

    @Test
    fun testBytesAndUint32KeysAreDistinct() {
        val result = nativeMap(
            scMap(Scv.toBytes(byteArrayOf(0x35)) to Scv.toSymbol("bytes"), Scv.toUint32(5u) to Scv.toSymbol("u32"))
        )
        assertEquals(2, result.size)
        assertEquals("bytes", result["35"])
        assertEquals("u32", result[5u])
        assertEquals(listOf<Any>("35", 5u), result.keys.toList())
    }

    // ------------------------------------------------------------------
    // Map fallbacks
    // ------------------------------------------------------------------

    @Test
    fun testMapWithDuplicateKeyFallsBack() {
        val scVal = SCValXdr.Map(
            SCMapXdr(
                listOf(
                    SCMapEntryXdr(Scv.toSymbol("a"), Scv.toUint32(1u)),
                    SCMapEntryXdr(Scv.toSymbol("a"), Scv.toUint32(2u))
                )
            )
        )
        assertSame(scVal, scVal.toNative())
    }

    @Test
    fun testMapWithEqualWideIntegerKeysFallsBack() {
        val seven = BigInteger.parseString("7")
        val scVal = scMap(Scv.toUint128(seven) to Scv.toSymbol("u128"), Scv.toInt256(seven) to Scv.toSymbol("i256"))
        assertSame(scVal, scVal.toNative())
    }

    @Test
    fun testMapWithUint64AndInt128KeysOfEqualValueFallsBack() {
        val five = BigInteger.parseString("5")
        assertMapFallsBackInEitherOrder(
            Scv.toUint64(5u) to Scv.toSymbol("u64"),
            Scv.toInt128(five) to Scv.toSymbol("i128")
        )
    }

    @Test
    fun testMapWithUint32AndUint128KeysOfEqualValueFallsBack() {
        val five = BigInteger.parseString("5")
        assertMapFallsBackInEitherOrder(
            Scv.toUint32(5u) to Scv.toSymbol("u32"),
            Scv.toUint128(five) to Scv.toSymbol("u128")
        )
    }

    @Test
    fun testMapWithInt32AndInt256KeysOfEqualValueFallsBack() {
        val minusFive = BigInteger.parseString("-5")
        assertMapFallsBackInEitherOrder(
            Scv.toInt32(-5) to Scv.toSymbol("i32"),
            Scv.toInt256(minusFive) to Scv.toSymbol("i256")
        )
    }

    @Test
    fun testMapWithInt64AndUint256KeysOfEqualValueFallsBack() {
        val five = BigInteger.parseString("5")
        assertMapFallsBackInEitherOrder(
            Scv.toInt64(5L) to Scv.toSymbol("i64"),
            Scv.toUint256(five) to Scv.toSymbol("u256")
        )
    }

    @Test
    fun testMapWithUint64MaxAndUint128KeysOfEqualValueFallsBack() {
        val uint64Max = BigInteger.parseString("18446744073709551615")
        assertMapFallsBackInEitherOrder(
            Scv.toUint64(ULong.MAX_VALUE) to Scv.toSymbol("u64"),
            Scv.toUint128(uint64Max) to Scv.toSymbol("u128")
        )
    }

    @Test
    fun testMapWithTimepointAndInt128KeysOfEqualValueFallsBack() {
        val timepoint = BigInteger.parseString("1700000000")
        assertMapFallsBackInEitherOrder(
            Scv.toTimePoint(1700000000u) to Scv.toSymbol("timepoint"),
            Scv.toInt128(timepoint) to Scv.toSymbol("i128")
        )
    }

    @Test
    fun testMapWithDurationAndUint256KeysOfEqualValueFallsBack() {
        val duration = BigInteger.parseString("3600")
        assertMapFallsBackInEitherOrder(
            Scv.toDuration(3600u) to Scv.toSymbol("duration"),
            Scv.toUint256(duration) to Scv.toSymbol("u256")
        )
    }

    @Test
    fun testMapWithBytesAndSymbolSpellingItsHexFallsBack() {
        val scVal = scMap(
            Scv.toBytes(byteArrayOf(0x30, 0x31)) to Scv.toSymbol("bytes"),
            Scv.toSymbol("3031") to Scv.toSymbol("sym")
        )
        assertSame(scVal, scVal.toNative())
    }

    @Test
    fun testMapWithUnrepresentableKeyFallsBack() {
        val keys = listOf(
            "vec" to Scv.toVec(listOf(Scv.toUint32(1u))),
            "map" to scMap(Scv.toSymbol("inner") to Scv.toUint32(1u)),
            "error" to errorValue(),
            "instance" to instanceValue(),
            "nonce key" to nonceKeyValue(),
            "executable tag" to Scv.toExecutableTag("v1"),
            "ledger key contract instance" to Scv.toLedgerKeyContractInstance()
        )
        for ((name, key) in keys) {
            val scVal = scMap(Scv.toSymbol("first") to Scv.toUint32(1u), key to Scv.toUint32(2u))
            assertSame(scVal, scVal.toNative(), "map with a $name key")
        }
    }

    @Test
    fun testMapWithIllFormedAddressKeyFallsBack() {
        val scVal = scMap(Scv.toAddress(illFormedAddress()) to Scv.toUint32(1u))
        assertSame(scVal, scVal.toNative())
    }

    // ------------------------------------------------------------------
    // Addresses
    // ------------------------------------------------------------------

    @Test
    fun testAddressValues() {
        assertEquals(accountId, addressValue(accountId).toNative())
        assertEquals(contractId, addressValue(contractId).toNative())
        assertEquals(muxedAccountId, addressValue(muxedAccountId).toNative())
        assertEquals(claimableBalanceId, addressValue(claimableBalanceId).toNative())
        assertEquals(liquidityPoolId, addressValue(liquidityPoolId).toNative())
    }

    @Test
    fun testIllFormedAddressValue() {
        val address = illFormedAddress()
        assertFailsWith<IllegalArgumentException> { Address.fromSCAddress(address) }

        val scVal = Scv.toAddress(address)
        assertSame(scVal, scVal.toNative())
    }

    // ------------------------------------------------------------------
    // Arms with no native representation
    // ------------------------------------------------------------------

    @Test
    fun testErrorValue() {
        val scVal = errorValue()
        assertSame(scVal, scVal.toNative())
    }

    @Test
    fun testContractInstanceValue() {
        val scVal = instanceValue()
        assertSame(scVal, scVal.toNative())
    }

    @Test
    fun testNonceKeyValue() {
        val scVal = nonceKeyValue()
        assertSame(scVal, scVal.toNative())
    }

    @Test
    fun testExecutableTagValue() {
        val scVal = Scv.toExecutableTag("v1")
        assertSame(scVal, scVal.toNative())

        val rawTag = Scv.toExecutableTagBytes(byteArrayOf(0xFF.toByte(), 0xFE.toByte()))
        assertSame(rawTag, rawTag.toNative())
    }

    // ------------------------------------------------------------------
    // Wire round trip
    // ------------------------------------------------------------------

    @Test
    fun testDecodedValueConvertsLikeFactoryBuiltValue() {
        val amount = BigInteger.parseString("-170141183460469231731687303715884105728")
        val original = scMap(
            Scv.toSymbol("items") to Scv.toVec(listOf(Scv.toUint32(1u), Scv.toSymbol("two"))),
            Scv.toSymbol("amount") to Scv.toInt128(amount),
            Scv.toSymbol("owner") to addressValue(contractId),
            Scv.toSymbol("count") to Scv.toUint64(9223372036854775808uL),
            Scv.toSymbol("data") to Scv.toBytes(byteArrayOf(0, 1, 255.toByte()))
        )
        val decoded = SCValXdr.fromXdrBase64(original.toXdrBase64())

        val fromOriginal = nativeMap(original)
        val fromDecoded = nativeMap(decoded)

        assertEquals(listOf("items", "amount", "owner", "count", "data"), fromDecoded.keys.toList())
        assertEquals(fromOriginal.keys.toList(), fromDecoded.keys.toList())

        assertEquals(listOf(1u, "two"), fromDecoded["items"])
        assertEquals(fromOriginal["items"], fromDecoded["items"])

        assertEquals(amount, fromDecoded["amount"])
        assertEquals(fromOriginal["amount"], fromDecoded["amount"])

        assertEquals(contractId, fromDecoded["owner"])
        assertEquals(fromOriginal["owner"], fromDecoded["owner"])

        assertEquals(9223372036854775808uL, fromDecoded["count"])
        assertEquals(fromOriginal["count"], fromDecoded["count"])

        val decodedBytes = assertIs<ByteArray>(fromDecoded["data"])
        assertContentEquals(byteArrayOf(0, 1, 255.toByte()), decodedBytes)
        assertContentEquals(assertIs<ByteArray>(fromOriginal["data"]), decodedBytes)
    }
}
