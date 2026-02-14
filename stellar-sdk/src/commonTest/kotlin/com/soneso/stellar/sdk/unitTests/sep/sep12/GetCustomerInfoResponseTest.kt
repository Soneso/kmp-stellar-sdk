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
import kotlin.test.assertTrue

class GetCustomerInfoResponseTest {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    @Test
    fun testDeserializeAcceptedWithAllFields() {
        val jsonString = """
            {
                "id": "d1ce2f48-3ff1-495d-9240-7a50d806cfed",
                "status": "ACCEPTED",
                "provided_fields": {
                    "first_name": {
                        "description": "The customer's first name",
                        "type": "string",
                        "status": "ACCEPTED"
                    },
                    "last_name": {
                        "description": "The customer's last name",
                        "type": "string",
                        "status": "ACCEPTED"
                    },
                    "email_address": {
                        "description": "The customer's email address",
                        "type": "string",
                        "status": "ACCEPTED"
                    }
                }
            }
        """.trimIndent()

        val response = json.decodeFromString<GetCustomerInfoResponse>(jsonString)

        assertEquals("d1ce2f48-3ff1-495d-9240-7a50d806cfed", response.id)
        assertEquals(CustomerStatus.ACCEPTED, response.status)
        assertNotNull(response.providedFields)
        assertEquals(3, response.providedFields!!.size)

        val firstName = response.providedFields!!["first_name"]
        assertNotNull(firstName)
        assertEquals("The customer's first name", firstName.description)
        assertEquals("string", firstName.type)
        assertEquals(FieldStatus.ACCEPTED, firstName.status)

        val lastName = response.providedFields!!["last_name"]
        assertNotNull(lastName)
        assertEquals("The customer's last name", lastName.description)
        assertEquals("string", lastName.type)
        assertEquals(FieldStatus.ACCEPTED, lastName.status)

        val emailAddress = response.providedFields!!["email_address"]
        assertNotNull(emailAddress)
        assertEquals("The customer's email address", emailAddress.description)
        assertEquals("string", emailAddress.type)
        assertEquals(FieldStatus.ACCEPTED, emailAddress.status)
    }

    @Test
    fun testDeserializeNeedsInfoWithMinimalFields() {
        val jsonString = """
            {
                "status": "NEEDS_INFO",
                "fields": {
                    "email_address": {
                        "description": "Email address of the customer",
                        "type": "string",
                        "optional": true
                    },
                    "mobile_number": {
                        "description": "phone number of the customer",
                        "type": "string"
                    }
                }
            }
        """.trimIndent()

        val response = json.decodeFromString<GetCustomerInfoResponse>(jsonString)

        assertNull(response.id)
        assertEquals(CustomerStatus.NEEDS_INFO, response.status)
        assertNotNull(response.fields)
        assertEquals(2, response.fields!!.size)

        val emailAddress = response.fields!!["email_address"]
        assertNotNull(emailAddress)
        assertEquals("Email address of the customer", emailAddress.description)
        assertEquals("string", emailAddress.type)
        assertEquals(true, emailAddress.optional)

        val mobileNumber = response.fields!!["mobile_number"]
        assertNotNull(mobileNumber)
        assertEquals("phone number of the customer", mobileNumber.description)
        assertEquals("string", mobileNumber.type)
        assertNull(mobileNumber.optional)
    }

    @Test
    fun testDeserializeFieldsMapParsing() {
        val jsonString = """
            {
                "status": "NEEDS_INFO",
                "fields": {
                    "id_type": {
                        "description": "Government issued ID",
                        "type": "string",
                        "choices": ["Passport", "Drivers License", "State ID"]
                    },
                    "photo_id_front": {
                        "description": "A clear photo of the front of the government issued ID",
                        "type": "binary"
                    }
                }
            }
        """.trimIndent()

        val response = json.decodeFromString<GetCustomerInfoResponse>(jsonString)

        assertEquals(CustomerStatus.NEEDS_INFO, response.status)
        assertNotNull(response.fields)
        assertEquals(2, response.fields!!.size)

        val idType = response.fields!!["id_type"]
        assertNotNull(idType)
        assertEquals("Government issued ID", idType.description)
        assertEquals("string", idType.type)
        assertNotNull(idType.choices)
        assertEquals(3, idType.choices!!.size)
        assertTrue(idType.choices!!.contains("Passport"))
        assertTrue(idType.choices!!.contains("Drivers License"))
        assertTrue(idType.choices!!.contains("State ID"))

        val photoIdFront = response.fields!!["photo_id_front"]
        assertNotNull(photoIdFront)
        assertEquals("A clear photo of the front of the government issued ID", photoIdFront.description)
        assertEquals("binary", photoIdFront.type)
        assertNull(photoIdFront.choices)
    }

    @Test
    fun testDeserializeProvidedFieldsMapParsing() {
        val jsonString = """
            {
                "id": "d1ce2f48-3ff1-495d-9240-7a50d806cfed",
                "status": "NEEDS_INFO",
                "provided_fields": {
                    "first_name": {
                        "description": "The customer's first name",
                        "type": "string",
                        "status": "ACCEPTED"
                    },
                    "last_name": {
                        "description": "The customer's last name",
                        "type": "string",
                        "status": "ACCEPTED"
                    }
                },
                "fields": {
                    "mobile_number": {
                        "description": "phone number of the customer",
                        "type": "string"
                    }
                }
            }
        """.trimIndent()

        val response = json.decodeFromString<GetCustomerInfoResponse>(jsonString)

        assertEquals("d1ce2f48-3ff1-495d-9240-7a50d806cfed", response.id)
        assertEquals(CustomerStatus.NEEDS_INFO, response.status)

        assertNotNull(response.providedFields)
        assertEquals(2, response.providedFields!!.size)

        val firstName = response.providedFields!!["first_name"]
        assertNotNull(firstName)
        assertEquals(FieldStatus.ACCEPTED, firstName.status)

        assertNotNull(response.fields)
        assertEquals(1, response.fields!!.size)
    }

    @Test
    fun testDeserializeAllCustomerStatuses() {
        val acceptedJson = """{"status": "ACCEPTED"}"""
        val acceptedResponse = json.decodeFromString<GetCustomerInfoResponse>(acceptedJson)
        assertEquals(CustomerStatus.ACCEPTED, acceptedResponse.status)

        val processingJson = """{"status": "PROCESSING"}"""
        val processingResponse = json.decodeFromString<GetCustomerInfoResponse>(processingJson)
        assertEquals(CustomerStatus.PROCESSING, processingResponse.status)

        val needsInfoJson = """{"status": "NEEDS_INFO"}"""
        val needsInfoResponse = json.decodeFromString<GetCustomerInfoResponse>(needsInfoJson)
        assertEquals(CustomerStatus.NEEDS_INFO, needsInfoResponse.status)

        val rejectedJson = """{"status": "REJECTED"}"""
        val rejectedResponse = json.decodeFromString<GetCustomerInfoResponse>(rejectedJson)
        assertEquals(CustomerStatus.REJECTED, rejectedResponse.status)
    }

    @Test
    fun testDeserializeAllFieldStatusesInProvidedFields() {
        val jsonString = """
            {
                "id": "d1ce2f48-3ff1-495d-9240-7a50d806cfed",
                "status": "NEEDS_INFO",
                "provided_fields": {
                    "accepted_field": {
                        "description": "Accepted field",
                        "type": "string",
                        "status": "ACCEPTED"
                    },
                    "processing_field": {
                        "description": "Processing field",
                        "type": "string",
                        "status": "PROCESSING"
                    },
                    "rejected_field": {
                        "description": "Rejected field",
                        "type": "string",
                        "status": "REJECTED",
                        "error": "Invalid format"
                    },
                    "verification_field": {
                        "description": "Verification required field",
                        "type": "string",
                        "status": "VERIFICATION_REQUIRED"
                    }
                }
            }
        """.trimIndent()

        val response = json.decodeFromString<GetCustomerInfoResponse>(jsonString)

        assertNotNull(response.providedFields)
        assertEquals(4, response.providedFields!!.size)

        assertEquals(FieldStatus.ACCEPTED, response.providedFields!!["accepted_field"]?.status)
        assertEquals(FieldStatus.PROCESSING, response.providedFields!!["processing_field"]?.status)
        assertEquals(FieldStatus.REJECTED, response.providedFields!!["rejected_field"]?.status)
        assertEquals("Invalid format", response.providedFields!!["rejected_field"]?.error)
        assertEquals(FieldStatus.VERIFICATION_REQUIRED, response.providedFields!!["verification_field"]?.status)
    }

    @Test
    fun testDeserializeProcessingWithMessage() {
        val jsonString = """
            {
                "id": "d1ce2f48-3ff1-495d-9240-7a50d806cfed",
                "status": "PROCESSING",
                "message": "Photo ID requires manual review. This process typically takes 1-2 business days.",
                "provided_fields": {
                    "photo_id_front": {
                        "description": "A clear photo of the front of the government issued ID",
                        "type": "binary",
                        "status": "PROCESSING"
                    }
                }
            }
        """.trimIndent()

        val response = json.decodeFromString<GetCustomerInfoResponse>(jsonString)

        assertEquals("d1ce2f48-3ff1-495d-9240-7a50d806cfed", response.id)
        assertEquals(CustomerStatus.PROCESSING, response.status)
        assertEquals("Photo ID requires manual review. This process typically takes 1-2 business days.", response.message)
        assertNotNull(response.providedFields)
        assertEquals(1, response.providedFields!!.size)
        assertEquals(FieldStatus.PROCESSING, response.providedFields!!["photo_id_front"]?.status)
    }

    @Test
    fun testDeserializeRejectedWithMessage() {
        val jsonString = """
            {
                "id": "d1ce2f48-3ff1-495d-9240-7a50d806cfed",
                "status": "REJECTED",
                "message": "This person is on a sanctions list"
            }
        """.trimIndent()

        val response = json.decodeFromString<GetCustomerInfoResponse>(jsonString)

        assertEquals("d1ce2f48-3ff1-495d-9240-7a50d806cfed", response.id)
        assertEquals(CustomerStatus.REJECTED, response.status)
        assertEquals("This person is on a sanctions list", response.message)
    }

    @Test
    fun testDeserializeVerificationRequired() {
        val jsonString = """
            {
                "id": "d1ce2f48-3ff1-495d-9240-7a50d806cfed",
                "status": "NEEDS_INFO",
                "provided_fields": {
                    "mobile_number": {
                        "description": "phone number of the customer",
                        "type": "string",
                        "status": "VERIFICATION_REQUIRED"
                    }
                }
            }
        """.trimIndent()

        val response = json.decodeFromString<GetCustomerInfoResponse>(jsonString)

        assertEquals("d1ce2f48-3ff1-495d-9240-7a50d806cfed", response.id)
        assertEquals(CustomerStatus.NEEDS_INFO, response.status)
        assertNotNull(response.providedFields)
        assertEquals(1, response.providedFields!!.size)

        val mobileNumber = response.providedFields!!["mobile_number"]
        assertNotNull(mobileNumber)
        assertEquals("phone number of the customer", mobileNumber.description)
        assertEquals("string", mobileNumber.type)
        assertEquals(FieldStatus.VERIFICATION_REQUIRED, mobileNumber.status)
    }
}
