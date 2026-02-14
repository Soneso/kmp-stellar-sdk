package com.soneso.stellar.sdk.unitTests.horizon.responses

import com.soneso.stellar.sdk.horizon.responses.AccountDataResponse
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
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
}
