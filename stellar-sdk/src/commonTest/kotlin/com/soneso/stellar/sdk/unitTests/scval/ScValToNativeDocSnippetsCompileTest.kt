// Copyright 2026 Soneso. All rights reserved.
// Use of this source code is governed by a license that can be
// found in the LICENSE file.

package com.soneso.stellar.sdk.unitTests.scval

import com.ionspin.kotlin.bignum.integer.BigInteger
import com.soneso.stellar.sdk.Address
import com.soneso.stellar.sdk.scval.Scv
import com.soneso.stellar.sdk.scval.toNative
import com.soneso.stellar.sdk.xdr.SCValXdr
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Runs the `toNative()` example the documentation shows and asserts the values it quotes, so
 * a change to the conversion that would make the documentation wrong fails the build instead.
 */
class ScValToNativeDocSnippetsCompileTest {

    @Test
    fun specLessConversion() {
        // A u64 above Long.MAX_VALUE converts to a ULong and keeps its exact value
        val count = Scv.toUint64(18446744073709551615uL).toNative()
        assertTrue(count is ULong)
        assertEquals(18446744073709551615uL, count)

        // A map with symbol keys converts to a Map keyed by String, in entry order
        val owner = "GA7QYNF7SOWQ3GLR2BGMZEHXAVIRZA4KVWLTJJFC7MGXUA74P7UJVSGZ"
        val record = Scv.toMap(
            linkedMapOf(
                Scv.toSymbol("name") to Scv.toString("Alice"),
                Scv.toSymbol("age") to Scv.toUint32(30u),
                Scv.toSymbol("balance") to Scv.toInt128(BigInteger.parseString("1000000000")),
                Scv.toSymbol("owner") to Scv.toAddress(Address(owner).toSCAddress())
            )
        )
        val fields = record.toNative() as Map<*, *>
        val name = fields["name"] as String
        val age = fields["age"] as UInt
        val balance = fields["balance"] as BigInteger
        val ownerAddress = fields["owner"] as String
        assertEquals("Alice", name)
        assertEquals(30u, age)
        assertEquals(BigInteger.parseString("1000000000"), balance)
        assertEquals(owner, ownerAddress)
        assertEquals(listOf("name", "age", "balance", "owner"), fields.keys.toList())

        // A vec converts to a List with each element converted in turn
        val items = Scv.toVec(
            listOf(Scv.toUint32(1u), Scv.toSymbol("a"), Scv.toVec(listOf(Scv.toBoolean(true))))
        ).toNative()
        assertEquals(listOf(1u, "a", listOf(true)), items)

        // A map whose key has no native representation comes back as the SCValXdr itself
        val keyedByVec = Scv.toMap(
            linkedMapOf(Scv.toVec(listOf(Scv.toUint32(1u))) to Scv.toUint32(2u))
        )
        val result = keyedByVec.toNative()
        assertTrue(result is SCValXdr)
        assertSame(keyedByVec, result)
    }
}
