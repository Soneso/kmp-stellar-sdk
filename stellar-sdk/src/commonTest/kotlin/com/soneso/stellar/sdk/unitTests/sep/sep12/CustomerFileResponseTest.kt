// Copyright 2025 Soneso. All rights reserved.
// Use of this source code is governed by a license that can be
// found in the LICENSE file.

package com.soneso.stellar.sdk.unitTests.sep.sep12

import com.soneso.stellar.sdk.sep.sep12.*
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class CustomerFileResponseTest {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    @Test
    fun testDeserializeAllFields() {
        val jsonString = """
            {
                "file_id": "file_d3d54529-6683-4341-9b66-4ac7d7504238",
                "content_type": "image/jpeg",
                "size": 4089371,
                "customer_id": "2bf95490-db23-442d-a1bd-c6fd5efb584e"
            }
        """.trimIndent()

        val response = json.decodeFromString<CustomerFileResponse>(jsonString)

        assertEquals("file_d3d54529-6683-4341-9b66-4ac7d7504238", response.fileId)
        assertEquals("image/jpeg", response.contentType)
        assertEquals(4089371L, response.size)
        assertEquals("2bf95490-db23-442d-a1bd-c6fd5efb584e", response.customerId)
    }

    @Test
    fun testDeserializeWithoutCustomerId() {
        val jsonString = """
            {
                "file_id": "file_abc123xyz",
                "content_type": "application/pdf",
                "size": 2048576
            }
        """.trimIndent()

        val response = json.decodeFromString<CustomerFileResponse>(jsonString)

        assertEquals("file_abc123xyz", response.fileId)
        assertEquals("application/pdf", response.contentType)
        assertEquals(2048576L, response.size)
        assertNull(response.customerId)
    }

    @Test
    fun testDeserializeImagePNG() {
        val jsonString = """
            {
                "file_id": "file_png123",
                "content_type": "image/png",
                "size": 6134063,
                "customer_id": "customer_123"
            }
        """.trimIndent()

        val response = json.decodeFromString<CustomerFileResponse>(jsonString)

        assertEquals("file_png123", response.fileId)
        assertEquals("image/png", response.contentType)
        assertEquals(6134063L, response.size)
        assertNotNull(response.customerId)
    }

    @Test
    fun testDeserializeImageJPEG() {
        val jsonString = """
            {
                "file_id": "file_jpeg456",
                "content_type": "image/jpeg",
                "size": 1234567,
                "customer_id": "customer_456"
            }
        """.trimIndent()

        val response = json.decodeFromString<CustomerFileResponse>(jsonString)

        assertEquals("file_jpeg456", response.fileId)
        assertEquals("image/jpeg", response.contentType)
        assertEquals(1234567L, response.size)
        assertNotNull(response.customerId)
    }

    @Test
    fun testDeserializePDF() {
        val jsonString = """
            {
                "file_id": "file_pdf789",
                "content_type": "application/pdf",
                "size": 9876543
            }
        """.trimIndent()

        val response = json.decodeFromString<CustomerFileResponse>(jsonString)

        assertEquals("file_pdf789", response.fileId)
        assertEquals("application/pdf", response.contentType)
        assertEquals(9876543L, response.size)
    }

    @Test
    fun testDeserializeOctetStream() {
        val jsonString = """
            {
                "file_id": "file_bin000",
                "content_type": "application/octet-stream",
                "size": 512000
            }
        """.trimIndent()

        val response = json.decodeFromString<CustomerFileResponse>(jsonString)

        assertEquals("file_bin000", response.fileId)
        assertEquals("application/octet-stream", response.contentType)
        assertEquals(512000L, response.size)
    }

    @Test
    fun testDeserializeSmallFile() {
        val jsonString = """
            {
                "file_id": "file_small",
                "content_type": "text/plain",
                "size": 100
            }
        """.trimIndent()

        val response = json.decodeFromString<CustomerFileResponse>(jsonString)

        assertEquals("file_small", response.fileId)
        assertEquals("text/plain", response.contentType)
        assertEquals(100L, response.size)
    }

    @Test
    fun testDeserializeLargeFile() {
        val jsonString = """
            {
                "file_id": "file_large",
                "content_type": "video/mp4",
                "size": 52428800
            }
        """.trimIndent()

        val response = json.decodeFromString<CustomerFileResponse>(jsonString)

        assertEquals("file_large", response.fileId)
        assertEquals("video/mp4", response.contentType)
        assertEquals(52428800L, response.size)
    }

    @Test
    fun testDeserializeFileIdFormats() {
        val uuidFormat = """
            {
                "file_id": "d3d54529-6683-4341-9b66-4ac7d7504238",
                "content_type": "image/jpeg",
                "size": 1000
            }
        """.trimIndent()

        val response1 = json.decodeFromString<CustomerFileResponse>(uuidFormat)
        assertEquals("d3d54529-6683-4341-9b66-4ac7d7504238", response1.fileId)

        val prefixedFormat = """
            {
                "file_id": "file_abc123def456",
                "content_type": "image/jpeg",
                "size": 1000
            }
        """.trimIndent()

        val response2 = json.decodeFromString<CustomerFileResponse>(prefixedFormat)
        assertEquals("file_abc123def456", response2.fileId)
    }
}
