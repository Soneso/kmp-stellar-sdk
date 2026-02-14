// Copyright 2025 Soneso. All rights reserved.
// Use of this source code is governed by a license that can be
// found in the LICENSE file.

package com.soneso.stellar.sdk.unitTests.sep.sep09

import com.soneso.stellar.sdk.sep.sep09.*
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StandardKYCFieldsTest {

    // ========== Fields Aggregation Tests ==========

    @Test
    fun testFieldsAggregatesFromNaturalPerson() {
        val kyc = StandardKYCFields(
            naturalPersonKYCFields = NaturalPersonKYCFields(
                firstName = "John",
                lastName = "Doe",
                emailAddress = "john@example.com"
            )
        )

        val fields = kyc.fields()

        assertEquals(3, fields.size)
        assertEquals("John", fields["first_name"])
        assertEquals("Doe", fields["last_name"])
        assertEquals("john@example.com", fields["email_address"])
    }

    @Test
    fun testFieldsAggregatesFromOrganization() {
        val kyc = StandardKYCFields(
            organizationKYCFields = OrganizationKYCFields(
                name = "Example Corp",
                VATNumber = "123456789",
                email = "contact@example.com"
            )
        )

        val fields = kyc.fields()

        assertEquals(3, fields.size)
        assertEquals("Example Corp", fields["organization.name"])
        assertEquals("123456789", fields["organization.VAT_number"])
        assertEquals("contact@example.com", fields["organization.email"])
    }

    @Test
    fun testFieldsAggregatesFromBothNaturalPersonAndOrganization() {
        val kyc = StandardKYCFields(
            naturalPersonKYCFields = NaturalPersonKYCFields(
                firstName = "John",
                lastName = "Doe"
            ),
            organizationKYCFields = OrganizationKYCFields(
                name = "Example Corp",
                VATNumber = "123456789"
            )
        )

        val fields = kyc.fields()

        assertEquals(4, fields.size)
        assertEquals("John", fields["first_name"])
        assertEquals("Doe", fields["last_name"])
        assertEquals("Example Corp", fields["organization.name"])
        assertEquals("123456789", fields["organization.VAT_number"])
    }

    @Test
    fun testFieldsEmptyWhenBothNull() {
        val kyc = StandardKYCFields()

        val fields = kyc.fields()

        assertTrue(fields.isEmpty())
    }

    @Test
    fun testFieldsWithOnlyNaturalPerson() {
        val kyc = StandardKYCFields(
            naturalPersonKYCFields = NaturalPersonKYCFields(
                firstName = "John",
                lastName = "Doe",
                birthDate = LocalDate(1990, 1, 15)
            ),
            organizationKYCFields = null
        )

        val fields = kyc.fields()

        assertEquals(3, fields.size)
        assertEquals("John", fields["first_name"])
        assertEquals("Doe", fields["last_name"])
        assertEquals("1990-01-15", fields["birth_date"])
    }

    @Test
    fun testFieldsWithOnlyOrganization() {
        val kyc = StandardKYCFields(
            naturalPersonKYCFields = null,
            organizationKYCFields = OrganizationKYCFields(
                name = "Example Corp",
                registrationNumber = "987654321"
            )
        )

        val fields = kyc.fields()

        assertEquals(2, fields.size)
        assertEquals("Example Corp", fields["organization.name"])
        assertEquals("987654321", fields["organization.registration_number"])
    }

    // ========== Files Aggregation Tests ==========

    @Test
    fun testFilesAggregatesFromNaturalPerson() {
        val photoIdFront = byteArrayOf(1, 2, 3, 4)
        val photoIdBack = byteArrayOf(5, 6, 7, 8)

        val kyc = StandardKYCFields(
            naturalPersonKYCFields = NaturalPersonKYCFields(
                photoIdFront = photoIdFront,
                photoIdBack = photoIdBack
            )
        )

        val files = kyc.files()

        assertEquals(2, files.size)
        assertTrue(files["photo_id_front"]!!.contentEquals(photoIdFront))
        assertTrue(files["photo_id_back"]!!.contentEquals(photoIdBack))
    }

    @Test
    fun testFilesAggregatesFromOrganization() {
        val incorporationDoc = byteArrayOf(1, 2, 3, 4)
        val proofAddress = byteArrayOf(5, 6, 7, 8)

        val kyc = StandardKYCFields(
            organizationKYCFields = OrganizationKYCFields(
                photoIncorporationDoc = incorporationDoc,
                photoProofAddress = proofAddress
            )
        )

        val files = kyc.files()

        assertEquals(2, files.size)
        assertTrue(files["organization.photo_incorporation_doc"]!!.contentEquals(incorporationDoc))
        assertTrue(files["organization.photo_proof_address"]!!.contentEquals(proofAddress))
    }

    @Test
    fun testFilesAggregatesFromBothNaturalPersonAndOrganization() {
        val photoIdFront = byteArrayOf(1, 2, 3)
        val incorporationDoc = byteArrayOf(4, 5, 6)

        val kyc = StandardKYCFields(
            naturalPersonKYCFields = NaturalPersonKYCFields(
                photoIdFront = photoIdFront
            ),
            organizationKYCFields = OrganizationKYCFields(
                photoIncorporationDoc = incorporationDoc
            )
        )

        val files = kyc.files()

        assertEquals(2, files.size)
        assertTrue(files["photo_id_front"]!!.contentEquals(photoIdFront))
        assertTrue(files["organization.photo_incorporation_doc"]!!.contentEquals(incorporationDoc))
    }

    @Test
    fun testFilesEmptyWhenBothNull() {
        val kyc = StandardKYCFields()

        val files = kyc.files()

        assertTrue(files.isEmpty())
    }

    @Test
    fun testFilesWithOnlyNaturalPerson() {
        val photoIdFront = byteArrayOf(1, 2, 3, 4)

        val kyc = StandardKYCFields(
            naturalPersonKYCFields = NaturalPersonKYCFields(
                photoIdFront = photoIdFront
            ),
            organizationKYCFields = null
        )

        val files = kyc.files()

        assertEquals(1, files.size)
        assertTrue(files["photo_id_front"]!!.contentEquals(photoIdFront))
    }

    @Test
    fun testFilesWithOnlyOrganization() {
        val incorporationDoc = byteArrayOf(1, 2, 3, 4)

        val kyc = StandardKYCFields(
            naturalPersonKYCFields = null,
            organizationKYCFields = OrganizationKYCFields(
                photoIncorporationDoc = incorporationDoc
            )
        )

        val files = kyc.files()

        assertEquals(1, files.size)
        assertTrue(files["organization.photo_incorporation_doc"]!!.contentEquals(incorporationDoc))
    }

    // ========== Nested Fields Tests ==========

    @Test
    fun testNaturalPersonWithNestedFinancialAccount() {
        val kyc = StandardKYCFields(
            naturalPersonKYCFields = NaturalPersonKYCFields(
                firstName = "John",
                lastName = "Doe",
                financialAccountKYCFields = FinancialAccountKYCFields(
                    bankName = "Example Bank",
                    bankAccountNumber = "1234567890"
                )
            )
        )

        val fields = kyc.fields()

        assertEquals(4, fields.size)
        assertEquals("John", fields["first_name"])
        assertEquals("Doe", fields["last_name"])
        assertEquals("Example Bank", fields["bank_name"])
        assertEquals("1234567890", fields["bank_account_number"])
    }

    @Test
    fun testNaturalPersonWithNestedCard() {
        val kyc = StandardKYCFields(
            naturalPersonKYCFields = NaturalPersonKYCFields(
                firstName = "John",
                cardKYCFields = CardKYCFields(
                    number = "4111111111111111",
                    holderName = "John Doe"
                )
            )
        )

        val fields = kyc.fields()

        assertEquals(3, fields.size)
        assertEquals("John", fields["first_name"])
        assertEquals("4111111111111111", fields["card.number"])
        assertEquals("John Doe", fields["card.holder_name"])
    }

    @Test
    fun testOrganizationWithNestedFinancialAccount() {
        val kyc = StandardKYCFields(
            organizationKYCFields = OrganizationKYCFields(
                name = "Example Corp",
                financialAccountKYCFields = FinancialAccountKYCFields(
                    bankName = "Corporate Bank",
                    bankAccountNumber = "9876543210"
                )
            )
        )

        val fields = kyc.fields()

        assertEquals(3, fields.size)
        assertEquals("Example Corp", fields["organization.name"])
        assertEquals("Corporate Bank", fields["organization.bank_name"])
        assertEquals("9876543210", fields["organization.bank_account_number"])
    }

    // ========== Complex Scenarios Tests ==========

    @Test
    fun testFullNaturalPersonKYC() {
        val kyc = StandardKYCFields(
            naturalPersonKYCFields = NaturalPersonKYCFields(
                firstName = "John",
                lastName = "Doe",
                emailAddress = "john@example.com",
                birthDate = LocalDate(1990, 1, 15),
                addressCountryCode = "USA",
                idType = "passport",
                idNumber = "123456789",
                photoIdFront = byteArrayOf(1, 2, 3),
                photoIdBack = byteArrayOf(4, 5, 6),
                financialAccountKYCFields = FinancialAccountKYCFields(
                    bankName = "Example Bank",
                    bankAccountNumber = "1234567890"
                )
            )
        )

        val fields = kyc.fields()
        val files = kyc.files()

        assertEquals(9, fields.size)
        assertEquals("John", fields["first_name"])
        assertEquals("Doe", fields["last_name"])
        assertEquals("john@example.com", fields["email_address"])
        assertEquals("1990-01-15", fields["birth_date"])
        assertEquals("USA", fields["address_country_code"])
        assertEquals("passport", fields["id_type"])
        assertEquals("123456789", fields["id_number"])
        assertEquals("Example Bank", fields["bank_name"])
        assertEquals("1234567890", fields["bank_account_number"])

        assertEquals(2, files.size)
        assertTrue(files["photo_id_front"]!!.contentEquals(byteArrayOf(1, 2, 3)))
        assertTrue(files["photo_id_back"]!!.contentEquals(byteArrayOf(4, 5, 6)))
    }

    @Test
    fun testFullOrganizationKYC() {
        val kyc = StandardKYCFields(
            organizationKYCFields = OrganizationKYCFields(
                name = "Example Corp",
                VATNumber = "123456789",
                registrationNumber = "987654321",
                directorName = "Jane Smith",
                email = "contact@example.com",
                addressCountryCode = "USA",
                photoIncorporationDoc = byteArrayOf(1, 2, 3),
                photoProofAddress = byteArrayOf(4, 5, 6),
                financialAccountKYCFields = FinancialAccountKYCFields(
                    bankName = "Corporate Bank",
                    bankAccountNumber = "9876543210"
                )
            )
        )

        val fields = kyc.fields()
        val files = kyc.files()

        assertEquals(8, fields.size)
        assertEquals("Example Corp", fields["organization.name"])
        assertEquals("123456789", fields["organization.VAT_number"])
        assertEquals("987654321", fields["organization.registration_number"])
        assertEquals("Jane Smith", fields["organization.director_name"])
        assertEquals("contact@example.com", fields["organization.email"])
        assertEquals("USA", fields["organization.address_country_code"])
        assertEquals("Corporate Bank", fields["organization.bank_name"])
        assertEquals("9876543210", fields["organization.bank_account_number"])

        assertEquals(2, files.size)
        assertTrue(files["organization.photo_incorporation_doc"]!!.contentEquals(byteArrayOf(1, 2, 3)))
        assertTrue(files["organization.photo_proof_address"]!!.contentEquals(byteArrayOf(4, 5, 6)))
    }

    @Test
    fun testMixedNaturalPersonAndOrganization() {
        val kyc = StandardKYCFields(
            naturalPersonKYCFields = NaturalPersonKYCFields(
                firstName = "John",
                lastName = "Doe",
                emailAddress = "john@example.com",
                photoIdFront = byteArrayOf(1, 2, 3)
            ),
            organizationKYCFields = OrganizationKYCFields(
                name = "Example Corp",
                VATNumber = "123456789",
                photoIncorporationDoc = byteArrayOf(4, 5, 6)
            )
        )

        val fields = kyc.fields()
        val files = kyc.files()

        assertEquals(5, fields.size)
        assertEquals("John", fields["first_name"])
        assertEquals("Doe", fields["last_name"])
        assertEquals("john@example.com", fields["email_address"])
        assertEquals("Example Corp", fields["organization.name"])
        assertEquals("123456789", fields["organization.VAT_number"])

        assertEquals(2, files.size)
        assertTrue(files["photo_id_front"]!!.contentEquals(byteArrayOf(1, 2, 3)))
        assertTrue(files["organization.photo_incorporation_doc"]!!.contentEquals(byteArrayOf(4, 5, 6)))
    }

    // ========== Edge Cases Tests ==========

    @Test
    fun testEmptyNaturalPersonFields() {
        val kyc = StandardKYCFields(
            naturalPersonKYCFields = NaturalPersonKYCFields()
        )

        val fields = kyc.fields()
        val files = kyc.files()

        assertTrue(fields.isEmpty())
        assertTrue(files.isEmpty())
    }

    @Test
    fun testEmptyOrganizationFields() {
        val kyc = StandardKYCFields(
            organizationKYCFields = OrganizationKYCFields()
        )

        val fields = kyc.fields()
        val files = kyc.files()

        assertTrue(fields.isEmpty())
        assertTrue(files.isEmpty())
    }

    @Test
    fun testBothEmptyFields() {
        val kyc = StandardKYCFields(
            naturalPersonKYCFields = NaturalPersonKYCFields(),
            organizationKYCFields = OrganizationKYCFields()
        )

        val fields = kyc.fields()
        val files = kyc.files()

        assertTrue(fields.isEmpty())
        assertTrue(files.isEmpty())
    }

    @Test
    fun testNaturalPersonWithAllNestedFields() {
        val kyc = StandardKYCFields(
            naturalPersonKYCFields = NaturalPersonKYCFields(
                firstName = "John",
                financialAccountKYCFields = FinancialAccountKYCFields(
                    bankName = "Example Bank"
                ),
                cardKYCFields = CardKYCFields(
                    token = "tok_visa_123"
                )
            )
        )

        val fields = kyc.fields()

        assertEquals(3, fields.size)
        assertEquals("John", fields["first_name"])
        assertEquals("Example Bank", fields["bank_name"])
        assertEquals("tok_visa_123", fields["card.token"])
    }

    @Test
    fun testOrganizationWithAllNestedFields() {
        val kyc = StandardKYCFields(
            organizationKYCFields = OrganizationKYCFields(
                name = "Example Corp",
                financialAccountKYCFields = FinancialAccountKYCFields(
                    bankName = "Corporate Bank"
                ),
                cardKYCFields = CardKYCFields(
                    token = "tok_corporate_123"
                )
            )
        )

        val fields = kyc.fields()

        assertEquals(3, fields.size)
        assertEquals("Example Corp", fields["organization.name"])
        assertEquals("Corporate Bank", fields["organization.bank_name"])
        assertEquals("tok_corporate_123", fields["card.token"])
    }

    @Test
    fun testFieldsDoNotCollide() {
        val kyc = StandardKYCFields(
            naturalPersonKYCFields = NaturalPersonKYCFields(
                addressCountryCode = "USA",
                city = "San Francisco"
            ),
            organizationKYCFields = OrganizationKYCFields(
                addressCountryCode = "USA",
                city = "New York"
            )
        )

        val fields = kyc.fields()

        assertEquals(4, fields.size)
        assertEquals("USA", fields["address_country_code"])
        assertEquals("San Francisco", fields["city"])
        assertEquals("USA", fields["organization.address_country_code"])
        assertEquals("New York", fields["organization.city"])
    }

    @Test
    fun testFilesDoNotCollide() {
        val naturalPersonPhoto = byteArrayOf(1, 2, 3)
        val organizationPhoto = byteArrayOf(4, 5, 6)

        val kyc = StandardKYCFields(
            naturalPersonKYCFields = NaturalPersonKYCFields(
                photoProofResidence = naturalPersonPhoto
            ),
            organizationKYCFields = OrganizationKYCFields(
                photoProofAddress = organizationPhoto
            )
        )

        val files = kyc.files()

        assertEquals(2, files.size)
        assertTrue(files["photo_proof_residence"]!!.contentEquals(naturalPersonPhoto))
        assertTrue(files["organization.photo_proof_address"]!!.contentEquals(organizationPhoto))
    }
}
