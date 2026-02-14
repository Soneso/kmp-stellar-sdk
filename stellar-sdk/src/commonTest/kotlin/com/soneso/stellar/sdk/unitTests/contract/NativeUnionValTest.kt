package com.soneso.stellar.sdk.unitTests.contract

import com.soneso.stellar.sdk.contract.NativeUnionVal
import kotlin.test.*

/**
 * Comprehensive unit tests for NativeUnionVal sealed class and its subclasses.
 *
 * Tests cover:
 * - VoidCase construction and properties
 * - TupleCase construction and properties 
 * - Type checking methods (isVoidCase, isTupleCase)
 * - Data class equality and hashCode
 * - Edge cases with null/empty values
 * - String representation behavior
 */
class NativeUnionValTest {

    // ==================== VoidCase Tests ====================

    @Test
    fun testVoidCaseConstruction() {
        val voidCase = NativeUnionVal.VoidCase("Success")
        
        assertEquals("Success", voidCase.tag)
        assertTrue(voidCase.isVoidCase)
        assertFalse(voidCase.isTupleCase)
    }

    @Test
    fun testVoidCaseWithEmptyTag() {
        val voidCase = NativeUnionVal.VoidCase("")
        
        assertEquals("", voidCase.tag)
        assertTrue(voidCase.isVoidCase)
        assertFalse(voidCase.isTupleCase)
    }

    @Test
    fun testVoidCaseWithSpecialCharactersInTag() {
        val voidCase = NativeUnionVal.VoidCase("Error_Type-123")
        
        assertEquals("Error_Type-123", voidCase.tag)
        assertTrue(voidCase.isVoidCase)
        assertFalse(voidCase.isTupleCase)
    }

    @Test
    fun testVoidCaseEquality() {
        val voidCase1 = NativeUnionVal.VoidCase("Success")
        val voidCase2 = NativeUnionVal.VoidCase("Success")
        val voidCase3 = NativeUnionVal.VoidCase("Error")

        assertEquals(voidCase1, voidCase2)
        assertNotEquals(voidCase1, voidCase3)
        assertEquals(voidCase1.hashCode(), voidCase2.hashCode())
    }

    @Test
    fun testVoidCaseStringRepresentation() {
        val voidCase = NativeUnionVal.VoidCase("Success")
        val stringRepr = voidCase.toString()
        
        assertTrue(stringRepr.contains("Success"))
        assertTrue(stringRepr.contains("VoidCase"))
    }

    @Test
    fun testVoidCaseIsInstanceOfCorrectTypes() {
        val voidCase = NativeUnionVal.VoidCase("Success")
        
        assertTrue(voidCase is NativeUnionVal)
        assertTrue(voidCase is NativeUnionVal.VoidCase)
        assertFalse(voidCase is NativeUnionVal.TupleCase)
    }

    // ==================== TupleCase Tests ====================

    @Test
    fun testTupleCaseConstruction() {
        val values = listOf("field1", 42, true)
        val tupleCase = NativeUnionVal.TupleCase("Data", values)
        
        assertEquals("Data", tupleCase.tag)
        assertEquals(values, tupleCase.values)
        assertFalse(tupleCase.isVoidCase)
        assertTrue(tupleCase.isTupleCase)
    }

    @Test
    fun testTupleCaseWithEmptyValues() {
        val tupleCase = NativeUnionVal.TupleCase("Empty", emptyList<Any?>())
        
        assertEquals("Empty", tupleCase.tag)
        assertEquals(emptyList<Any?>(), tupleCase.values)
        assertFalse(tupleCase.isVoidCase)
        assertTrue(tupleCase.isTupleCase)
    }

    @Test
    fun testTupleCaseWithNullValues() {
        val values = listOf("field1", null, "field3")
        val tupleCase = NativeUnionVal.TupleCase("WithNull", values)
        
        assertEquals("WithNull", tupleCase.tag)
        assertEquals(values, tupleCase.values)
        assertEquals(3, tupleCase.values.size)
        assertNull(tupleCase.values[1])
    }

    @Test
    fun testTupleCaseWithMixedTypes() {
        val values = listOf<Any?>(
            "string",
            42,
            true,
            3.14,
            listOf(1, 2, 3),
            mapOf("key" to "value"),
            null
        )
        val tupleCase = NativeUnionVal.TupleCase("Mixed", values)
        
        assertEquals("Mixed", tupleCase.tag)
        assertEquals(values, tupleCase.values)
        assertEquals(7, tupleCase.values.size)
    }

    @Test
    fun testTupleCaseEquality() {
        val values1 = listOf("field1", 42)
        val values2 = listOf("field1", 42)
        val values3 = listOf("field1", 43)
        
        val tupleCase1 = NativeUnionVal.TupleCase("Data", values1)
        val tupleCase2 = NativeUnionVal.TupleCase("Data", values2)
        val tupleCase3 = NativeUnionVal.TupleCase("Data", values3)
        val tupleCase4 = NativeUnionVal.TupleCase("DataDiff", values1)

        assertEquals(tupleCase1, tupleCase2)
        assertNotEquals(tupleCase1, tupleCase3)
        assertNotEquals(tupleCase1, tupleCase4)
        assertEquals(tupleCase1.hashCode(), tupleCase2.hashCode())
    }

    @Test
    fun testTupleCaseStringRepresentation() {
        val values = listOf("field1", 42)
        val tupleCase = NativeUnionVal.TupleCase("Data", values)
        val stringRepr = tupleCase.toString()
        
        assertTrue(stringRepr.contains("Data"))
        assertTrue(stringRepr.contains("TupleCase"))
        assertTrue(stringRepr.contains("field1"))
        assertTrue(stringRepr.contains("42"))
    }

    @Test
    fun testTupleCaseIsInstanceOfCorrectTypes() {
        val tupleCase = NativeUnionVal.TupleCase("Data", listOf("value"))
        
        assertTrue(tupleCase is NativeUnionVal)
        assertTrue(tupleCase is NativeUnionVal.TupleCase)
        assertFalse(tupleCase is NativeUnionVal.VoidCase)
    }

    @Test
    fun testTupleCaseValuesReference() {
        val originalValues = listOf<Any?>("field1", 42)
        val tupleCase = NativeUnionVal.TupleCase("Data", originalValues)
        
        // The tuple case should store the exact reference passed in
        assertSame(originalValues, tupleCase.values)
        
        // Test with mutable list to verify behavior
        val mutableValues = mutableListOf<Any?>("field1", 42)
        val tupleCase2 = NativeUnionVal.TupleCase("Data", mutableValues)
        
        // Should maintain reference to the original list
        assertEquals(listOf<Any?>("field1", 42), tupleCase2.values)
        assertEquals(2, tupleCase2.values.size)
    }

    // ==================== Cross-Type Tests ====================

    @Test
    fun testVoidCaseAndTupleCaseAreNotEqual() {
        val voidCase = NativeUnionVal.VoidCase("Success")
        val tupleCase = NativeUnionVal.TupleCase("Success", emptyList<Any?>())
        
        assertNotEquals<Any>(voidCase, tupleCase)
        assertNotEquals<Any>(tupleCase, voidCase)
    }

    @Test
    fun testTypeCheckingConsistency() {
        val voidCase = NativeUnionVal.VoidCase("Success")
        val tupleCase = NativeUnionVal.TupleCase("Data", listOf("value"))
        
        // VoidCase checks
        assertTrue(voidCase.isVoidCase)
        assertFalse(voidCase.isTupleCase)
        assertEquals(voidCase.isVoidCase, voidCase is NativeUnionVal.VoidCase)
        assertEquals(voidCase.isTupleCase, voidCase is NativeUnionVal.TupleCase)
        
        // TupleCase checks
        assertFalse(tupleCase.isVoidCase)
        assertTrue(tupleCase.isTupleCase)
        assertEquals(tupleCase.isVoidCase, tupleCase is NativeUnionVal.VoidCase)
        assertEquals(tupleCase.isTupleCase, tupleCase is NativeUnionVal.TupleCase)
    }

    @Test
    fun testSealedClassExhaustiveness() {
        val voidCase: NativeUnionVal = NativeUnionVal.VoidCase("Success")
        val tupleCase: NativeUnionVal = NativeUnionVal.TupleCase("Data", listOf("value"))
        
        // Test that when expression covers all cases
        fun processUnion(union: NativeUnionVal): String = when (union) {
            is NativeUnionVal.VoidCase -> "void:${union.tag}"
            is NativeUnionVal.TupleCase -> "tuple:${union.tag}:${union.values.size}"
        }
        
        assertEquals("void:Success", processUnion(voidCase))
        assertEquals("tuple:Data:1", processUnion(tupleCase))
    }

    @Test
    fun testPolymorphicBehavior() {
        val unions: List<NativeUnionVal> = listOf(
            NativeUnionVal.VoidCase("Success"),
            NativeUnionVal.VoidCase("Error"),
            NativeUnionVal.TupleCase("Data", listOf("field1")),
            NativeUnionVal.TupleCase("Result", listOf("value1", "value2"))
        )
        
        val tags = unions.map { it.tag }
        val expectedTags = listOf("Success", "Error", "Data", "Result")
        
        assertEquals(expectedTags, tags)
        
        val voidCount = unions.count { it.isVoidCase }
        val tupleCount = unions.count { it.isTupleCase }
        
        assertEquals(2, voidCount)
        assertEquals(2, tupleCount)
    }

    // ==================== Edge Cases ====================

    @Test
    fun testLongTagNames() {
        val longTag = "VeryLongTagNameThatExceedsTypicalLengthExpectations".repeat(3)
        
        val voidCase = NativeUnionVal.VoidCase(longTag)
        val tupleCase = NativeUnionVal.TupleCase(longTag, listOf("value"))
        
        assertEquals(longTag, voidCase.tag)
        assertEquals(longTag, tupleCase.tag)
    }

    @Test
    fun testUnicodeTagNames() {
        val unicodeTag = "成功_🎉_Success"
        
        val voidCase = NativeUnionVal.VoidCase(unicodeTag)
        val tupleCase = NativeUnionVal.TupleCase(unicodeTag, listOf("value"))
        
        assertEquals(unicodeTag, voidCase.tag)
        assertEquals(unicodeTag, tupleCase.tag)
    }

    @Test
    fun testLargeValuesList() {
        val largeValues = (1..1000).map { "value$it" }
        val tupleCase = NativeUnionVal.TupleCase("LargeData", largeValues)
        
        assertEquals("LargeData", tupleCase.tag)
        assertEquals(1000, tupleCase.values.size)
        assertEquals("value1", tupleCase.values[0])
        assertEquals("value1000", tupleCase.values[999])
    }

    @Test
    fun testNestedCollectionsInValues() {
        val nestedValues = listOf<Any?>(
            listOf("inner1", "inner2"),
            mapOf("key1" to "value1", "key2" to "value2"),
            setOf(1, 2, 3),
            arrayOf("array1", "array2").toList()
        )
        val tupleCase = NativeUnionVal.TupleCase("Nested", nestedValues)
        
        assertEquals("Nested", tupleCase.tag)
        assertEquals(4, tupleCase.values.size)
        assertEquals(listOf("inner1", "inner2"), tupleCase.values[0])
    }

    @Test
    fun testDataClassComponentFunctions() {
        // VoidCase component functions
        val voidCase = NativeUnionVal.VoidCase("Success")
        val (voidTag) = voidCase
        assertEquals("Success", voidTag)
        
        // TupleCase component functions
        val values = listOf("field1", 42)
        val tupleCase = NativeUnionVal.TupleCase("Data", values)
        val (tupleTag, tupleValues) = tupleCase
        assertEquals("Data", tupleTag)
        assertEquals(values, tupleValues)
    }

    @Test
    fun testDataClassCopyFunction() {
        // VoidCase copy
        val voidCase = NativeUnionVal.VoidCase("Success")
        val copiedVoidCase = voidCase.copy(tag = "Error")
        assertEquals("Error", copiedVoidCase.tag)
        assertNotEquals(voidCase, copiedVoidCase)
        
        // TupleCase copy
        val tupleCase = NativeUnionVal.TupleCase("Data", listOf("field1"))
        val copiedTupleCase = tupleCase.copy(tag = "NewData")
        assertEquals("NewData", copiedTupleCase.tag)
        assertEquals(tupleCase.values, copiedTupleCase.values)
        assertNotEquals(tupleCase, copiedTupleCase)
        
        val copiedWithNewValues = tupleCase.copy(values = listOf("field2"))
        assertEquals("Data", copiedWithNewValues.tag)
        assertEquals(listOf("field2"), copiedWithNewValues.values)
        assertNotEquals(tupleCase, copiedWithNewValues)
    }

    // ==================== Type Safety Tests ====================

    @Test
    fun testSealedClassTypeGuards() {
        fun getVoidTag(union: NativeUnionVal): String? {
            return if (union is NativeUnionVal.VoidCase) {
                union.tag
            } else {
                null
            }
        }
        
        fun getTupleValues(union: NativeUnionVal): List<Any?>? {
            return if (union is NativeUnionVal.TupleCase) {
                union.values
            } else {
                null
            }
        }
        
        val voidCase = NativeUnionVal.VoidCase("Success")
        val tupleCase = NativeUnionVal.TupleCase("Data", listOf("value"))
        
        assertEquals("Success", getVoidTag(voidCase))
        assertNull(getVoidTag(tupleCase))
        
        assertNull(getTupleValues(voidCase))
        assertEquals(listOf("value"), getTupleValues(tupleCase))
    }

    @Test
    fun testSmartCasting() {
        val union: NativeUnionVal = NativeUnionVal.TupleCase("Data", listOf("field1", 42))
        
        if (union is NativeUnionVal.TupleCase) {
            // Smart cast should work here
            assertEquals("Data", union.tag)
            assertEquals(2, union.values.size)
            assertEquals("field1", union.values[0])
            assertEquals(42, union.values[1])
        } else {
            fail("Union should be TupleCase")
        }
    }
}