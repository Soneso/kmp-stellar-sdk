package com.soneso.stellar.sdk.unitTests.horizon.responses

import com.soneso.stellar.sdk.horizon.responses.AccountDataResponse
import kotlinx.serialization.json.Json
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.assertContentEquals

class AccountDataResponseTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun testDeserialization() {
        val dataJson = """{"value": "dGVzdA=="}"""
        val response = json.decodeFromString<AccountDataResponse>(dataJson)
        assertEquals("dGVzdA==", response.value)
    }

    @Test
    fun testDecodedValue() {
        val dataJson = """{"value": "dGVzdA=="}"""
        val response = json.decodeFromString<AccountDataResponse>(dataJson)
        val decoded = response.decodedValue
        assertContentEquals("test".encodeToByteArray(), decoded)
    }

    @Test
    fun testDecodedString() {
        val dataJson = """{"value": "dGVzdA=="}"""
        val response = json.decodeFromString<AccountDataResponse>(dataJson)
        assertEquals("test", response.decodedString)
    }

    @Test
    fun testDecodedStringOrNull() {
        val dataJson = """{"value": "dGVzdA=="}"""
        val response = json.decodeFromString<AccountDataResponse>(dataJson)
        assertEquals("test", response.decodedStringOrNull)
    }

    @Test
    fun testEmptyBase64Value() {
        val dataJson = """{"value": ""}"""
        val response = json.decodeFromString<AccountDataResponse>(dataJson)
        assertEquals("", response.value)
    }

    @Test
    fun testEquality() {
        val response1 = AccountDataResponse("dGVzdA==")
        val response2 = AccountDataResponse("dGVzdA==")
        val response3 = AccountDataResponse("b3RoZXI=")
        assertEquals(response1, response2)
        assertEquals(response1.hashCode(), response2.hashCode())
        assertTrue(response1 != response3)
    }

    @Test
    fun testToString() {
        val response = AccountDataResponse("dGVzdA==")
        val str = response.toString()
        assertTrue(str.contains("dGVzdA=="))
        assertTrue(str.contains("test"))
    }

    @Test
    fun testEqualityWithSameInstance() {
        val response = AccountDataResponse("dGVzdA==")
        // The identity fast-path must agree with the value comparison for a distinct equal
        // instance; comparing an instance's hashCode to itself could not detect a broken
        // equals/hashCode contract.
        val equalInstance = AccountDataResponse("dGVzdA==")
        assertTrue(response.equals(response))
        assertTrue(response.equals(equalInstance))
        assertEquals(equalInstance.hashCode(), response.hashCode())
    }

    @Test
    fun testEqualityWithNullAndForeignType() {
        val response = AccountDataResponse("dGVzdA==")
        assertFalse(response.equals(null))
        assertFalse(response.equals("dGVzdA=="))
    }

    @Test
    fun testUndecodableValue() {
        val response = AccountDataResponse("not base64 at all!")
        assertFailsWith<IllegalArgumentException> {
            response.decodedValue
        }
        assertNull(response.decodedStringOrNull)

        val str = response.toString()
        assertTrue(str.contains("not base64 at all!"))
        assertTrue(str.contains("decodedStringOrNull=null"))
    }

    @OptIn(ExperimentalEncodingApi::class)
    @Test
    fun testValueThatIsNotUtf8() {
        // A data entry may hold arbitrary bytes; these two are not a valid UTF-8 sequence
        val binary = byteArrayOf(0xFF.toByte(), 0xFE.toByte())
        val response = AccountDataResponse(Base64.encode(binary))

        assertContentEquals(binary, response.decodedValue, "The raw bytes are returned unchanged")
        assertFailsWith<CharacterCodingException> { response.decodedString }
        assertNull(response.decodedStringOrNull, "The safe accessor reports binary data as null")
        assertTrue(response.toString().contains("decodedStringOrNull=null"))
    }

    @OptIn(ExperimentalEncodingApi::class)
    @Test
    fun testMultibyteUtf8Value() {
        val text = "hello é☃"
        val response = AccountDataResponse(Base64.encode(text.encodeToByteArray()))

        assertEquals(text, response.decodedString)
        assertEquals(text, response.decodedStringOrNull)
    }
}
