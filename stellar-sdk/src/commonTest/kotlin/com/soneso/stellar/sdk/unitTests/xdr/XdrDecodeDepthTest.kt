package com.soneso.stellar.sdk.unitTests.xdr

import com.soneso.stellar.sdk.xdr.*
import kotlin.test.*

class XdrDecodeDepthTest {
    private fun vectors(depth: Int): ByteArray = XdrWriter().apply {
        repeat(depth) { index ->
            writeInt(SCValTypeXdr.SCV_VEC.value)
            writeInt(1)
            writeInt(if (index == depth - 1) 0 else 1)
        }
    }.toByteArray()

    private fun assertVectors(value: SCValXdr, depth: Int) {
        var current = value
        repeat(depth - 1) { current = assertIs<SCValXdr.Vec>(current).value!!.value.single() }
        assertTrue(assertIs<SCValXdr.Vec>(current).value!!.value.isEmpty())
    }

    private fun depthFailure(cap: Int, decode: () -> Unit) {
        assertEquals("XDR decode recursion depth ${cap + 1} exceeds cap $cap",
            assertFailsWith<IllegalArgumentException>(block = decode).message)
    }

    @Test
    fun vectorsShareTheBudgetAndRestoreItAfterSuccessAndFailure() {
        for (failsFirst in listOf(false, true)) {
            val reader = XdrReader(vectors(if (failsFirst) 501 else 500) + vectors(500))
            if (failsFirst) {
                depthFailure(500) { SCValXdr.decode(reader) }
                assertEquals(SCValTypeXdr.SCV_VEC.value, reader.readInt())
                assertEquals(1, reader.readInt())
                assertEquals(0, reader.readInt())
            } else {
                assertVectors(SCValXdr.decode(reader), 500)
            }
            assertVectors(SCValXdr.decode(reader), 500)
        }
    }

    @Test
    fun mapsCountEntriesAndValuesTowardTheSameBudget() {
        fun bytes(levels: Int) = XdrWriter().apply {
            repeat(levels) { index ->
                writeInt(SCValTypeXdr.SCV_MAP.value)
                writeInt(1)
                writeInt(if (index == levels - 1) 0 else 1)
                if (index < levels - 1) writeInt(SCValTypeXdr.SCV_VOID.value)
            }
        }.toByteArray()
        var node = SCValXdr.decode(XdrReader(bytes(250)))
        repeat(249) {
            val entry = assertIs<SCValXdr.Map>(node).value!!.value.single()
            assertEquals(SCValXdr.Void(SCValTypeXdr.SCV_VOID), entry.key)
            node = entry.`val`
        }
        assertTrue(assertIs<SCValXdr.Map>(node).value!!.value.isEmpty())
        depthFailure(500) { SCValXdr.decode(XdrReader(bytes(251))) }
    }

    @Test
    fun optionalPredicatesUseTheSameBoundary() {
        fun bytes(depth: Int) = XdrWriter().apply {
            repeat(depth - 1) {
                writeInt(ClaimPredicateTypeXdr.CLAIM_PREDICATE_NOT.value)
                writeInt(1)
            }
            writeInt(ClaimPredicateTypeXdr.CLAIM_PREDICATE_UNCONDITIONAL.value)
        }.toByteArray()
        var node = ClaimPredicateXdr.decode(XdrReader(bytes(500)))
        repeat(499) { node = assertIs<ClaimPredicateXdr.NotPredicate>(node).value!! }
        assertEquals(ClaimPredicateXdr.Void, node)
        depthFailure(500) { ClaimPredicateXdr.decode(XdrReader(bytes(501))) }
    }

    @Test
    fun mixedSpecFieldsAndUnionArmsShareOneBudget() {
        val kinds = listOf(SCSpecTypeXdr.SC_SPEC_TYPE_VEC, SCSpecTypeXdr.SC_SPEC_TYPE_OPTION,
            SCSpecTypeXdr.SC_SPEC_TYPE_MAP)
        val bytes = XdrWriter().apply {
            repeat(250) { index ->
                val kind = kinds[index % kinds.size]
                writeInt(kind.value)
                if (kind == SCSpecTypeXdr.SC_SPEC_TYPE_MAP) writeInt(SCSpecTypeXdr.SC_SPEC_TYPE_U32.value)
            }
            writeInt(SCSpecTypeXdr.SC_SPEC_TYPE_U32.value)
        }.toByteArray()
        val reader = XdrReader(bytes)
        assertEquals(SCSpecTypeXdr.SC_SPEC_TYPE_VEC.value, reader.readInt())
        var node = SCSpecTypeVecXdr.decode(reader).elementType
        repeat(249) { index ->
            assertEquals(kinds[(index + 1) % kinds.size], node.discriminant)
            node = when (val current = node) {
                is SCSpecTypeDefXdr.Vec -> current.value.elementType
                is SCSpecTypeDefXdr.Option -> current.value.valueType
                is SCSpecTypeDefXdr.Map -> {
                    assertEquals(SCSpecTypeXdr.SC_SPEC_TYPE_U32, current.value.keyType.discriminant)
                    current.value.valueType
                }
                else -> error("Unexpected spec type: ${current.discriminant}")
            }
        }
        assertEquals(SCSpecTypeXdr.SC_SPEC_TYPE_U32, node.discriminant)
        depthFailure(500) { SCSpecTypeDefXdr.decode(XdrReader(bytes)) }
    }

    @Test
    fun siblingBreadthDoesNotConsumeDepth() {
        val bytes = XdrWriter().apply {
            writeInt(SCValTypeXdr.SCV_VEC.value)
            writeInt(1)
            writeInt(1000)
            repeat(1000) { writeInt(SCValTypeXdr.SCV_VOID.value) }
        }.toByteArray()
        val values = assertIs<SCValXdr.Vec>(SCValXdr.decode(XdrReader(bytes))).value!!.value
        assertEquals(1000, values.size)
        values.forEach { assertEquals(SCValXdr.Void(SCValTypeXdr.SCV_VOID), it) }
    }

    private fun delegates(nodes: Int, signatureDepth: Int = 1): ByteArray = XdrWriter().apply {
        repeat(nodes) { index ->
            writeInt(SCAddressTypeXdr.SC_ADDRESS_TYPE_CONTRACT.value)
            writeFixedOpaque(ByteArray(32))
            writeFixedOpaque(vectors(if (index == nodes - 1) signatureDepth else 1))
            writeInt(if (index == nodes - 1) 0 else 1)
        }
    }.toByteArray()

    private fun assertDelegates(root: SorobanDelegateSignatureXdr, signatureDepth: Int = 1) {
        var node = root
        repeat(129) { index ->
            assertContentEquals(ByteArray(32), assertIs<SCAddressXdr.ContractId>(node.address).value.value.value)
            assertVectors(node.signature, if (index == 128) signatureDepth else 1)
            if (index == 128) assertTrue(node.nestedDelegates.isEmpty())
            else node = node.nestedDelegates.single()
        }
    }

    @Test
    fun delegateBoundaryAndCounterRecovery() {
        for (failsFirst in listOf(false, true)) {
            val reader = XdrReader(delegates(if (failsFirst) 130 else 129) + delegates(129))
            if (failsFirst) {
                depthFailure(129) { SorobanDelegateSignatureXdr.decode(reader) }
                reader.readFixedOpaque(52)
            } else {
                assertDelegates(SorobanDelegateSignatureXdr.decode(reader))
            }
            assertDelegates(SorobanDelegateSignatureXdr.decode(reader))
        }
        assertDelegates(SorobanDelegateSignatureXdr.decode(XdrReader(delegates(129, 371))), 371)
        depthFailure(500) { SorobanDelegateSignatureXdr.decode(XdrReader(delegates(129, 372))) }
    }
}
