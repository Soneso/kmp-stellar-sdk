package com.soneso.stellar.sdk.unitTests.xdr.json

import com.soneso.stellar.sdk.Address
import com.soneso.stellar.sdk.xdr.ContractExecutableExternalRefXdr
import com.soneso.stellar.sdk.xdr.SCValXdr
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * XDR-JSON (SEP-0051) behaviour of the bytes-backed executable tag positions: the tag
 * bytes render through the escape ladder and parse back exactly, for any byte content,
 * including bytes no Kotlin String can carry.
 *
 * The golden documents are pinned as text. The tag bytes 0xC0, 0xFF and 0xFE never
 * appear in valid UTF-8 and take `\xNN` escapes; 0x00 takes its short escape `\0`. The
 * JSON encoder then doubles each backslash, which is why the documents carry two per
 * escape.
 */
class XdrJsonExecutableTagTest {

    private val tagBytes = byteArrayOf(0xC0.toByte(), 0x00, 0xFF.toByte(), 0xFE.toByte())
    private val ownerContractId = "CDCYWK73YTYFJZZSJ5V7EDFNHYBG4QN3VUNG2IGD27KJDDPNCZKBCBXK"

    @Test
    fun scValExecutableTagRoundTripsNonUtf8BytesThroughTheEscapeLadder() {
        val value = SCValXdr.ExecutableTag(tagBytes)
        val text = value.toXdrJson()
        assertEquals("{\"executable_tag\":\"\\\\xc0\\\\0\\\\xff\\\\xfe\"}", text)

        val decoded = SCValXdr.fromXdrJson(text)
        assertIs<SCValXdr.ExecutableTag>(decoded)
        assertContentEquals(tagBytes, decoded.value)
    }

    @Test
    fun contractExecutableExternalRefRoundTripsANonUtf8TagThroughTheEscapeLadder() {
        val owner = Address(ownerContractId).toSCAddress()
        val ref = ContractExecutableExternalRefXdr(executableOwner = owner, tag = tagBytes)
        val text = ref.toXdrJson()
        assertEquals(
            "{\"executable_owner\":\"$ownerContractId\",\"tag\":\"\\\\xc0\\\\0\\\\xff\\\\xfe\"}",
            text
        )

        val decoded = ContractExecutableExternalRefXdr.fromXdrJson(text)
        assertContentEquals(tagBytes, decoded.tag)
    }
}
