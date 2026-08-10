package com.soneso.stellar.sdk.unitTests.sep.sep51

import com.soneso.stellar.sdk.xdr.SCValTypeXdr
import com.soneso.stellar.sdk.xdr.SCValXdr
import com.soneso.stellar.sdk.xdr.XdrJson
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * The XDR-JSON (SEP-0051) decoder walks a document with the call stack, so an arbitrarily
 * nested document would exhaust it. Decoding therefore refuses a document nested deeper than
 * the limit the binary decoder applies to recursive types.
 *
 * `SCVal` is the type that can nest without bound: its `vec` arm holds a list of `SCVal`, so
 * each level of the value adds an object and an array to the document.
 */
class Sep51RecursionLimitTest {

    /** Wraps [inner] in `{"vec": [ ... ]}` [depth] times, adding two document levels each time. */
    private fun nest(depth: Int, inner: JsonElement = JsonPrimitive("void")): JsonElement {
        var element = inner
        repeat(depth) {
            element = buildJsonObject { put("vec", buildJsonArray { add(element) }) }
        }
        return element
    }

    @Test
    fun aModestlyNestedValueDecodes() {
        val value = SCValXdr.fromXdrJsonElement(nest(4))
        assertTrue(value is SCValXdr.Vec)
    }

    @Test
    fun aTreeNestedPastTheLimitIsRejected() {
        val error = assertFailsWith<IllegalArgumentException> {
            SCValXdr.fromXdrJsonElement(nest(100))
        }
        assertTrue(error.message!!.contains("nests deeper than the limit"), error.message!!)
    }

    @Test
    fun textNestedPastTheLimitIsRejected() {
        val document = XdrJson.encodeToString(nest(100))
        val error = assertFailsWith<IllegalArgumentException> {
            SCValXdr.fromXdrJson(document)
        }
        assertTrue(error.message!!.contains("nests deeper than the limit"), error.message!!)
    }

    /**
     * The boundary itself, not only a depth far past it: the deepest tree the limit admits is one
     * where the innermost value sits at the limit, and one wrapping more must be the first depth
     * refused. Without both, an off-by-one in either direction would still satisfy the cases
     * above.
     */
    @Test
    fun theDeepestTreeTheLimitAdmitsDecodes() {
        val value = SCValXdr.fromXdrJsonElement(nest(DEEPEST_ACCEPTED))
        assertTrue(value is SCValXdr.Vec)
    }

    @Test
    fun aTreeOneWrappingDeeperThanTheLimitAdmitsIsRejected() {
        val error = assertFailsWith<IllegalArgumentException> {
            SCValXdr.fromXdrJsonElement(nest(DEEPEST_ACCEPTED + 1))
        }
        assertTrue(error.message!!.contains("nests deeper than the limit"), error.message!!)
    }

    /**
     * The limit is a property of the document, not of the entry point it arrives through, so a
     * value at the boundary must be admitted or refused identically whether it is handed over as
     * text or as an already-parsed tree.
     */
    @Test
    fun textAndTreeAdmitTheSameDepths() {
        listOf(DEEPEST_ACCEPTED - 1, DEEPEST_ACCEPTED, DEEPEST_ACCEPTED + 1).forEach { wrappings ->
            val tree = nest(wrappings)
            val text = XdrJson.encodeToString(tree)
            val treeAccepted = runCatching { SCValXdr.fromXdrJsonElement(tree) }.isSuccess
            val textAccepted = runCatching { SCValXdr.fromXdrJson(text) }.isSuccess
            assertEquals(treeAccepted, textAccepted, "disagreement at $wrappings wrappings")
        }
    }

    @Test
    fun theInnermostValueSurvivesARoundTrip() {
        val decoded = SCValXdr.fromXdrJsonElement(nest(1))
        val vec = (decoded as SCValXdr.Vec).value!!.value
        assertEquals(1, vec.size)
        assertEquals(SCValTypeXdr.SCV_VOID, vec.single().discriminant)
        assertEquals(nest(1), decoded.toXdrJsonElement())
    }

    private companion object {
        /**
         * The most wrappings a value can carry and still decode. Each wrapping adds an object and
         * an array, so a value wrapped `n` times nests `2n` containers deep; the innermost value
         * is a primitive and costs no nesting.
         */
        val DEEPEST_ACCEPTED: Int = XdrJson.RECURSION_CAP / 2
    }
}
