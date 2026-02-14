// Copyright 2025 Soneso. All rights reserved.
// Use of this source code is governed by a license that can be
// found in the LICENSE file.

package com.soneso.stellar.sdk.unitTests.sep.sep12

import com.soneso.stellar.sdk.sep.sep12.*
import com.soneso.stellar.sdk.sep.sep09.NaturalPersonKYCFields
import com.soneso.stellar.sdk.sep.sep09.OrganizationKYCFields
import com.soneso.stellar.sdk.sep.sep09.StandardKYCFields
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PutCustomerInfoRequestTest {

    private val jwtToken = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.test"
    private val customerId = "d1ce2f48-3ff1-495d-9240-7a50d806cfed"
    private val accountId = "GBWMCCC3NHSKLAOJDBKKYW7SSH2PFTTNVFKWSGLWGDLEBKLOVP5JLBBP"

    @Test
    fun testRequestWithSEP09KYCFields() {
        val request = PutCustomerInfoRequest(
            jwt = jwtToken,
            kycFields = StandardKYCFields(
                naturalPersonKYCFields = NaturalPersonKYCFields(
                    firstName = "John",
                    lastName = "Doe",
                    emailAddress = "john@example.com",
                    birthDate = LocalDate(1990, 1, 15)
                )
            )
        )

        assertEquals(jwtToken, request.jwt)
        assertNotNull(request.kycFields)
        assertNotNull(request.kycFields!!.naturalPersonKYCFields)
        assertEquals("John", request.kycFields!!.naturalPersonKYCFields!!.firstName)
        assertEquals("Doe", request.kycFields!!.naturalPersonKYCFields!!.lastName)
        assertEquals("john@example.com", request.kycFields!!.naturalPersonKYCFields!!.emailAddress)
        assertEquals(LocalDate(1990, 1, 15), request.kycFields!!.naturalPersonKYCFields!!.birthDate)
    }

    @Test
    fun testRequestWithCustomFields() {
        val request = PutCustomerInfoRequest(
            jwt = jwtToken,
            id = customerId,
            customFields = mapOf(
                "custom_field_1" to "value1",
                "custom_field_2" to "value2",
                "tax_id" to "123-45-6789"
            )
        )

        assertEquals(jwtToken, request.jwt)
        assertEquals(customerId, request.id)
        assertNotNull(request.customFields)
        assertEquals(3, request.customFields!!.size)
        assertEquals("value1", request.customFields!!["custom_field_1"])
        assertEquals("value2", request.customFields!!["custom_field_2"])
        assertEquals("123-45-6789", request.customFields!!["tax_id"])
    }

    @Test
    fun testRequestWithCustomFiles() {
        val documentBytes = byteArrayOf(1, 2, 3, 4, 5)
        val photoBytes = byteArrayOf(6, 7, 8, 9, 10)

        val request = PutCustomerInfoRequest(
            jwt = jwtToken,
            id = customerId,
            customFiles = mapOf(
                "custom_document" to documentBytes,
                "custom_photo" to photoBytes
            )
        )

        assertEquals(jwtToken, request.jwt)
        assertEquals(customerId, request.id)
        assertNotNull(request.customFiles)
        assertEquals(2, request.customFiles!!.size)
        assertTrue(documentBytes.contentEquals(request.customFiles!!["custom_document"]!!))
        assertTrue(photoBytes.contentEquals(request.customFiles!!["custom_photo"]!!))
    }

    @Test
    fun testRequestWithVerificationFields() {
        val request = PutCustomerInfoRequest(
            jwt = jwtToken,
            id = customerId,
            verificationFields = mapOf(
                "email_address_verification" to "123456",
                "mobile_number_verification" to "654321"
            )
        )

        assertEquals(jwtToken, request.jwt)
        assertEquals(customerId, request.id)
        assertNotNull(request.verificationFields)
        assertEquals(2, request.verificationFields!!.size)
        assertEquals("123456", request.verificationFields!!["email_address_verification"])
        assertEquals("654321", request.verificationFields!!["mobile_number_verification"])
    }

    @Test
    fun testRequestWithFileReferences() {
        val request = PutCustomerInfoRequest(
            jwt = jwtToken,
            id = customerId,
            fileReferences = mapOf(
                "photo_id_front_file_id" to "file_d3d54529-6683-4341-9b66-4ac7d7504238",
                "photo_id_back_file_id" to "file_a1b2c3d4-5678-90ab-cdef-1234567890ab"
            )
        )

        assertEquals(jwtToken, request.jwt)
        assertEquals(customerId, request.id)
        assertNotNull(request.fileReferences)
        assertEquals(2, request.fileReferences!!.size)
        assertEquals("file_d3d54529-6683-4341-9b66-4ac7d7504238", request.fileReferences!!["photo_id_front_file_id"])
        assertEquals("file_a1b2c3d4-5678-90ab-cdef-1234567890ab", request.fileReferences!!["photo_id_back_file_id"])
    }

    @Test
    fun testRequestCombiningAllFieldTypes() {
        val photoIdBytes = byteArrayOf(1, 2, 3, 4, 5)

        val request = PutCustomerInfoRequest(
            jwt = jwtToken,
            id = customerId,
            account = accountId,
            memo = "test_memo",
            type = "sep31-sender",
            kycFields = StandardKYCFields(
                naturalPersonKYCFields = NaturalPersonKYCFields(
                    firstName = "John",
                    lastName = "Doe"
                )
            ),
            customFields = mapOf(
                "custom_field" to "custom_value"
            ),
            customFiles = mapOf(
                "custom_photo" to photoIdBytes
            ),
            verificationFields = mapOf(
                "email_address_verification" to "123456"
            ),
            fileReferences = mapOf(
                "photo_id_front_file_id" to "file_abc123"
            )
        )

        assertEquals(jwtToken, request.jwt)
        assertEquals(customerId, request.id)
        assertEquals(accountId, request.account)
        assertEquals("test_memo", request.memo)
        assertEquals("sep31-sender", request.type)
        assertNotNull(request.kycFields)
        assertNotNull(request.customFields)
        assertNotNull(request.customFiles)
        assertNotNull(request.verificationFields)
        assertNotNull(request.fileReferences)
    }

    @Test
    fun testRequestWithOrganizationKYCFields() {
        val request = PutCustomerInfoRequest(
            jwt = jwtToken,
            type = "sep31-receiver",
            kycFields = StandardKYCFields(
                organizationKYCFields = OrganizationKYCFields(
                    name = "Acme Corp",
                    registrationNumber = "123456789",
                    directorName = "Jane Smith",
                    email = "contact@acme.com"
                )
            )
        )

        assertEquals(jwtToken, request.jwt)
        assertEquals("sep31-receiver", request.type)
        assertNotNull(request.kycFields)
        assertNotNull(request.kycFields!!.organizationKYCFields)
        assertEquals("Acme Corp", request.kycFields!!.organizationKYCFields!!.name)
        assertEquals("123456789", request.kycFields!!.organizationKYCFields!!.registrationNumber)
        assertEquals("Jane Smith", request.kycFields!!.organizationKYCFields!!.directorName)
        assertEquals("contact@acme.com", request.kycFields!!.organizationKYCFields!!.email)
    }

    @Test
    fun testRequestWithMixedNaturalPersonAndOrganization() {
        val request = PutCustomerInfoRequest(
            jwt = jwtToken,
            kycFields = StandardKYCFields(
                naturalPersonKYCFields = NaturalPersonKYCFields(
                    firstName = "John",
                    lastName = "Doe"
                ),
                organizationKYCFields = OrganizationKYCFields(
                    name = "Acme Corp",
                    registrationNumber = "123456789"
                )
            )
        )

        assertEquals(jwtToken, request.jwt)
        assertNotNull(request.kycFields)
        assertNotNull(request.kycFields!!.naturalPersonKYCFields)
        assertNotNull(request.kycFields!!.organizationKYCFields)
        assertEquals("John", request.kycFields!!.naturalPersonKYCFields!!.firstName)
        assertEquals("Acme Corp", request.kycFields!!.organizationKYCFields!!.name)
    }

    @Test
    fun testRequestWithTransactionId() {
        val transactionId = "82fhs729f63dh0v4"

        val request = PutCustomerInfoRequest(
            jwt = jwtToken,
            transactionId = transactionId,
            kycFields = StandardKYCFields(
                naturalPersonKYCFields = NaturalPersonKYCFields(
                    firstName = "John"
                )
            )
        )

        assertEquals(jwtToken, request.jwt)
        assertEquals(transactionId, request.transactionId)
    }

    @Test
    fun testRequestWithMemoAndMemoType() {
        val request = PutCustomerInfoRequest(
            jwt = jwtToken,
            account = accountId,
            memo = "123456789",
            memoType = "id"
        )

        assertEquals(jwtToken, request.jwt)
        assertEquals(accountId, request.account)
        assertEquals("123456789", request.memo)
        assertEquals("id", request.memoType)
    }

    @Test
    fun testRequestMinimalFields() {
        val request = PutCustomerInfoRequest(
            jwt = jwtToken
        )

        assertEquals(jwtToken, request.jwt)
    }
}
