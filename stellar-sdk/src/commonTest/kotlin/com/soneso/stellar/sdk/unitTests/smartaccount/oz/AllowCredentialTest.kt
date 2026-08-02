package com.soneso.stellar.sdk.unitTests.smartaccount.oz

import com.soneso.stellar.sdk.smartaccount.oz.AllowCredential
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AllowCredentialTest {

    // --- Construction ---

    @Test
    fun constructionWithIdOnly() {
        val id = byteArrayOf(0x01, 0x02, 0x03)
        val credential = AllowCredential(id = id)

        assertTrue(id.contentEquals(credential.id))
        assertNull(credential.transports)
    }

    @Test
    fun constructionWithIdAndTransports() {
        val id = byteArrayOf(0x0A, 0x0B, 0x0C)
        val transports = listOf("internal", "hybrid")
        val credential = AllowCredential(id = id, transports = transports)

        assertTrue(id.contentEquals(credential.id))
        assertEquals(transports, credential.transports)
    }

    @Test
    fun constructionWithExplicitNullTransports() {
        val id = byteArrayOf(0x10, 0x20)
        val credential = AllowCredential(id = id, transports = null)

        assertTrue(id.contentEquals(credential.id))
        assertNull(credential.transports)
    }

    // --- equals / contentEquals ---

    @Test
    fun equalsTwoInstancesWithSameByteContentAreEqual() {
        // Different ByteArray references, same content
        val id1 = byteArrayOf(0xDE.toByte(), 0xAD.toByte(), 0xBE.toByte(), 0xEF.toByte())
        val id2 = byteArrayOf(0xDE.toByte(), 0xAD.toByte(), 0xBE.toByte(), 0xEF.toByte())
        val transports = listOf("internal")

        val credential1 = AllowCredential(id = id1, transports = transports)
        val credential2 = AllowCredential(id = id2, transports = transports)

        assertFalse(id1 === id2, "Precondition: id arrays must be distinct references")
        assertEquals(credential1, credential2)
    }

    @Test
    fun equalsWithNullTransportsBothNull() {
        val id1 = byteArrayOf(0x01, 0x02)
        val id2 = byteArrayOf(0x01, 0x02)

        val credential1 = AllowCredential(id = id1, transports = null)
        val credential2 = AllowCredential(id = id2, transports = null)

        assertEquals(credential1, credential2)
    }

    @Test
    fun notEqualWhenIdsDiffer() {
        val credential1 = AllowCredential(id = byteArrayOf(0x01, 0x02))
        val credential2 = AllowCredential(id = byteArrayOf(0x01, 0x03))

        assertNotEquals(credential1, credential2)
    }

    @Test
    fun notEqualWhenTransportsDiffer() {
        val id = byteArrayOf(0x01, 0x02)
        val credential1 = AllowCredential(id = id.copyOf(), transports = listOf("internal"))
        val credential2 = AllowCredential(id = id.copyOf(), transports = listOf("usb"))

        assertNotEquals(credential1, credential2)
    }

    @Test
    fun notEqualWhenNullTransportsVsEmptyList() {
        val id = byteArrayOf(0x01, 0x02)
        val withNull = AllowCredential(id = id.copyOf(), transports = null)
        val withEmpty = AllowCredential(id = id.copyOf(), transports = emptyList())

        assertNotEquals(withNull, withEmpty)
    }

    @Test
    fun notEqualWhenOneTransportsIsNullAndOtherIsNonNull() {
        val id = byteArrayOf(0x01, 0x02)
        val withNull = AllowCredential(id = id.copyOf(), transports = null)
        val withValue = AllowCredential(id = id.copyOf(), transports = listOf("hybrid"))

        assertNotEquals(withNull, withValue)
        assertNotEquals(withValue, withNull)
    }

    @Test
    fun equalsWithSelfReturnsTrue() {
        val credential = AllowCredential(
            id = byteArrayOf(0x01, 0x02, 0x03),
            transports = listOf("internal")
        )

        @Suppress("SelfReferenceImmutableVar")
        assertEquals(credential, credential)
        assertTrue(credential == credential)
    }

    // --- hashCode ---

    @Test
    fun hashCodeConsistencyEqualObjectsHaveSameHashCode() {
        val id1 = byteArrayOf(0x11, 0x22, 0x33)
        val id2 = byteArrayOf(0x11, 0x22, 0x33)
        val transports = listOf("internal", "usb")

        val credential1 = AllowCredential(id = id1, transports = transports)
        val credential2 = AllowCredential(id = id2, transports = transports)

        assertEquals(credential1, credential2)
        assertEquals(credential1.hashCode(), credential2.hashCode())
    }

    @Test
    fun hashCodeDiffersForDifferentObjects() {
        val credential1 = AllowCredential(id = byteArrayOf(0x01), transports = listOf("internal"))
        val credential2 = AllowCredential(id = byteArrayOf(0x02), transports = listOf("internal"))

        // Not a strict contract guarantee, but practically they must differ for this simple case
        assertNotEquals(credential1.hashCode(), credential2.hashCode())
    }

    @Test
    fun hashCodeNullVsNonNullTransportsDiffers() {
        val id = byteArrayOf(0x01, 0x02)
        val withNull = AllowCredential(id = id.copyOf(), transports = null)
        val withValue = AllowCredential(id = id.copyOf(), transports = listOf("internal"))

        assertNotEquals(withNull.hashCode(), withValue.hashCode())
    }

    // --- fromId factory ---

    @Test
    fun fromIdCreatesCredentialWithNullTransports() {
        val id = byteArrayOf(0xAB.toByte(), 0xCD.toByte())
        val credential = AllowCredential.fromId(id)

        assertTrue(id.contentEquals(credential.id))
        assertNull(credential.transports)
    }

    @Test
    fun fromIdProducesEquivalentResultToDirectConstruction() {
        val id = byteArrayOf(0x01, 0x02, 0x03, 0x04)
        val fromFactory = AllowCredential.fromId(id)
        val direct = AllowCredential(id = id)

        assertEquals(direct, fromFactory)
    }

    // --- fromIds factory ---

    @Test
    fun fromIdsCreatesListWithAllNullTransports() {
        val ids = listOf(
            byteArrayOf(0x01),
            byteArrayOf(0x02, 0x03),
            byteArrayOf(0x04, 0x05, 0x06)
        )

        val credentials = AllowCredential.fromIds(ids)

        assertEquals(ids.size, credentials.size)
        ids.forEachIndexed { index, id ->
            assertTrue(id.contentEquals(credentials[index].id))
            assertNull(credentials[index].transports)
        }
    }

    @Test
    fun fromIdsEmptyListReturnsEmptyList() {
        val credentials = AllowCredential.fromIds(emptyList())

        assertTrue(credentials.isEmpty())
    }

    @Test
    fun fromIdsSingleElementList() {
        val id = byteArrayOf(0xFF.toByte(), 0x00)
        val credentials = AllowCredential.fromIds(listOf(id))

        assertEquals(1, credentials.size)
        assertTrue(id.contentEquals(credentials[0].id))
        assertNull(credentials[0].transports)
    }

    // --- transports values ---

    @Test
    fun commonTransportValuesArePreserved() {
        val id = byteArrayOf(0x01)
        val transports = listOf("internal", "hybrid", "usb", "ble", "nfc")
        val credential = AllowCredential(id = id, transports = transports)

        assertEquals(transports, credential.transports)
        assertEquals(5, credential.transports!!.size)
        assertTrue(credential.transports!!.contains("internal"))
        assertTrue(credential.transports!!.contains("hybrid"))
        assertTrue(credential.transports!!.contains("usb"))
        assertTrue(credential.transports!!.contains("ble"))
        assertTrue(credential.transports!!.contains("nfc"))
    }

    @Test
    fun transportOrderIsPreserved() {
        val id = byteArrayOf(0x01)
        val transports = listOf("nfc", "usb", "internal")
        val credential = AllowCredential(id = id, transports = transports)

        assertEquals("nfc", credential.transports!![0])
        assertEquals("usb", credential.transports!![1])
        assertEquals("internal", credential.transports!![2])
    }

    // --- ByteArray reference semantics ---

    @Test
    fun idStoredByReferenceNotCopied() {
        // Kotlin data classes do NOT deep-copy ByteArray fields.
        // This test documents the actual behavior: mutating the original array
        // is reflected in the stored id (no defensive copy is performed).
        val originalId = byteArrayOf(0x01, 0x02, 0x03)
        val credential = AllowCredential(id = originalId)

        // Verify the stored id initially matches
        assertTrue(byteArrayOf(0x01, 0x02, 0x03).contentEquals(credential.id))

        // Mutate the original array
        originalId[0] = 0xFF.toByte()

        // The stored id reflects the mutation because it shares the same reference
        assertEquals(0xFF.toByte(), credential.id[0])
    }

    @Test
    fun copyOfIdIsIndependent() = runTest {
        // When callers want isolation they must copy the array themselves.
        val originalId = byteArrayOf(0x01, 0x02, 0x03)
        val credential = AllowCredential(id = originalId.copyOf())

        originalId[0] = 0xFF.toByte()

        // credential.id was constructed from a copy — it is unaffected
        assertEquals(0x01.toByte(), credential.id[0])
    }
}
