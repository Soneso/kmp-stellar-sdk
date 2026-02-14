// Copyright 2025 Soneso. All rights reserved.
// Use of this source code is governed by a license that can be
// found in the LICENSE file.

package com.soneso.stellar.sdk.unitTests.sep.sep09

import com.soneso.stellar.sdk.sep.sep09.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OrganizationKYCFieldsTest {

    // ========== Text Field Key Constants Tests ==========

    @Test
    fun testTextFieldKeyConstantsHaveOrganizationPrefix() {
        // Verify all 15 text field key constants have "organization." prefix
        assertEquals("organization.name", OrganizationKYCFields.NAME)
        assertEquals("organization.VAT_number", OrganizationKYCFields.VAT_NUMBER)
        assertEquals("organization.registration_number", OrganizationKYCFields.REGISTRATION_NUMBER)
        assertEquals("organization.registration_date", OrganizationKYCFields.REGISTRATION_DATE)
        assertEquals("organization.registered_address", OrganizationKYCFields.REGISTERED_ADDRESS)
        assertEquals("organization.number_of_shareholders", OrganizationKYCFields.NUMBER_OF_SHAREHOLDERS)
        assertEquals("organization.shareholder_name", OrganizationKYCFields.SHAREHOLDER_NAME)
        assertEquals("organization.address_country_code", OrganizationKYCFields.ADDRESS_COUNTRY_CODE)
        assertEquals("organization.state_or_province", OrganizationKYCFields.STATE_OR_PROVINCE)
        assertEquals("organization.city", OrganizationKYCFields.CITY)
        assertEquals("organization.postal_code", OrganizationKYCFields.POSTAL_CODE)
        assertEquals("organization.director_name", OrganizationKYCFields.DIRECTOR_NAME)
        assertEquals("organization.website", OrganizationKYCFields.WEBSITE)
        assertEquals("organization.email", OrganizationKYCFields.EMAIL)
        assertEquals("organization.phone", OrganizationKYCFields.PHONE)
    }

    // ========== Binary Field Key Constants Tests ==========

    @Test
    fun testBinaryFieldKeyConstantsHaveOrganizationPrefix() {
        // Verify all 2 binary field key constants have "organization." prefix
        assertEquals("organization.photo_incorporation_doc", OrganizationKYCFields.PHOTO_INCORPORATION_DOC)
        assertEquals("organization.photo_proof_address", OrganizationKYCFields.PHOTO_PROOF_ADDRESS)
    }

    // ========== Fields Method Tests ==========

    @Test
    fun testFieldsReturnsOnlyNonNullTextFieldsWithPrefix() {
        val org = OrganizationKYCFields(
            name = "Example Corp",
            VATNumber = "123456789",
            directorName = "Jane Smith"
        )

        val fields = org.fields()

        assertEquals(3, fields.size)
        assertEquals("Example Corp", fields["organization.name"])
        assertEquals("123456789", fields["organization.VAT_number"])
        assertEquals("Jane Smith", fields["organization.director_name"])
    }

    @Test
    fun testFieldsWithAllTextFields() {
        val org = OrganizationKYCFields(
            name = "Example Corp",
            VATNumber = "123456789",
            registrationNumber = "987654321",
            registrationDate = "2020-01-01",
            registeredAddress = "123 Business Blvd",
            numberOfShareholders = 5,
            shareholderName = "John Doe",
            addressCountryCode = "USA",
            stateOrProvince = "California",
            city = "San Francisco",
            postalCode = "94102",
            directorName = "Jane Smith",
            website = "https://example.com",
            email = "contact@example.com",
            phone = "+14155551234"
        )

        val fields = org.fields()

        assertEquals(15, fields.size)
        assertEquals("Example Corp", fields["organization.name"])
        assertEquals("123456789", fields["organization.VAT_number"])
        assertEquals("987654321", fields["organization.registration_number"])
        assertEquals("2020-01-01", fields["organization.registration_date"])
        assertEquals("123 Business Blvd", fields["organization.registered_address"])
        assertEquals("5", fields["organization.number_of_shareholders"])
        assertEquals("John Doe", fields["organization.shareholder_name"])
        assertEquals("USA", fields["organization.address_country_code"])
        assertEquals("California", fields["organization.state_or_province"])
        assertEquals("San Francisco", fields["organization.city"])
        assertEquals("94102", fields["organization.postal_code"])
        assertEquals("Jane Smith", fields["organization.director_name"])
        assertEquals("https://example.com", fields["organization.website"])
        assertEquals("contact@example.com", fields["organization.email"])
        assertEquals("+14155551234", fields["organization.phone"])
    }

    @Test
    fun testFieldsEmptyWhenAllNull() {
        val org = OrganizationKYCFields()

        val fields = org.fields()

        assertTrue(fields.isEmpty())
    }

    @Test
    fun testFieldsAllPrefixesPresent() {
        val org = OrganizationKYCFields(
            name = "Test Corp",
            email = "test@example.com"
        )

        val fields = org.fields()

        // Verify all keys have "organization." prefix
        fields.keys.forEach { key ->
            assertTrue(key.startsWith("organization."), "Key $key should start with 'organization.'")
        }
    }

    // ========== Number of Shareholders Tests ==========

    @Test
    fun testNumberOfShareholdersSerializesAsString() {
        val org = OrganizationKYCFields(
            numberOfShareholders = 10
        )

        val fields = org.fields()

        assertEquals(1, fields.size)
        assertEquals("10", fields["organization.number_of_shareholders"])
    }

    @Test
    fun testNumberOfShareholdersZero() {
        val org = OrganizationKYCFields(
            numberOfShareholders = 0
        )

        val fields = org.fields()

        assertEquals("0", fields["organization.number_of_shareholders"])
    }

    @Test
    fun testNumberOfShareholdersLargeValue() {
        val org = OrganizationKYCFields(
            numberOfShareholders = 1000000
        )

        val fields = org.fields()

        assertEquals("1000000", fields["organization.number_of_shareholders"])
    }

    // ========== Nested Financial Account Tests ==========

    @Test
    fun testNestedFinancialAccountFieldsWithOrganizationPrefix() {
        val org = OrganizationKYCFields(
            name = "Example Corp",
            financialAccountKYCFields = FinancialAccountKYCFields(
                bankName = "Corporate Bank",
                bankAccountNumber = "9876543210"
            )
        )

        val fields = org.fields()

        assertEquals(3, fields.size)
        assertEquals("Example Corp", fields["organization.name"])
        assertEquals("Corporate Bank", fields["organization.bank_name"])
        assertEquals("9876543210", fields["organization.bank_account_number"])
    }

    @Test
    fun testNestedFinancialAccountWithAllFields() {
        val org = OrganizationKYCFields(
            name = "Example Corp",
            financialAccountKYCFields = FinancialAccountKYCFields(
                bankName = "Corporate Bank",
                bankAccountType = "business",
                bankAccountNumber = "9876543210",
                bankNumber = "987654321",
                bankBranchNumber = "001"
            )
        )

        val fields = org.fields()

        assertEquals(6, fields.size)
        assertEquals("Corporate Bank", fields["organization.bank_name"])
        assertEquals("business", fields["organization.bank_account_type"])
        assertEquals("9876543210", fields["organization.bank_account_number"])
        assertEquals("987654321", fields["organization.bank_number"])
        assertEquals("001", fields["organization.bank_branch_number"])
    }

    @Test
    fun testNestedCardFieldsIncluded() {
        val org = OrganizationKYCFields(
            name = "Example Corp",
            cardKYCFields = CardKYCFields(
                token = "tok_corporate_card"
            )
        )

        val fields = org.fields()

        assertEquals(2, fields.size)
        assertEquals("Example Corp", fields["organization.name"])
        assertEquals("tok_corporate_card", fields["card.token"])
    }

    // ========== Files Method Tests ==========

    @Test
    fun testFilesReturnsOnlyNonNullBinaryFieldsWithPrefix() {
        val incorporationDoc = byteArrayOf(1, 2, 3, 4)
        val proofAddress = byteArrayOf(5, 6, 7, 8)

        val org = OrganizationKYCFields(
            photoIncorporationDoc = incorporationDoc,
            photoProofAddress = proofAddress
        )

        val files = org.files()

        assertEquals(2, files.size)
        assertTrue(files["organization.photo_incorporation_doc"]!!.contentEquals(incorporationDoc))
        assertTrue(files["organization.photo_proof_address"]!!.contentEquals(proofAddress))
    }

    @Test
    fun testFilesWithAllBinaryFields() {
        val incorporationDoc = byteArrayOf(1, 2, 3)
        val proofAddress = byteArrayOf(4, 5, 6)

        val org = OrganizationKYCFields(
            photoIncorporationDoc = incorporationDoc,
            photoProofAddress = proofAddress
        )

        val files = org.files()

        assertEquals(2, files.size)
        assertTrue(files["organization.photo_incorporation_doc"]!!.contentEquals(incorporationDoc))
        assertTrue(files["organization.photo_proof_address"]!!.contentEquals(proofAddress))
    }

    @Test
    fun testFilesEmptyWhenAllNull() {
        val org = OrganizationKYCFields()

        val files = org.files()

        assertTrue(files.isEmpty())
    }

    @Test
    fun testFilesAllPrefixesPresent() {
        val org = OrganizationKYCFields(
            photoIncorporationDoc = byteArrayOf(1, 2, 3)
        )

        val files = org.files()

        // Verify all keys have "organization." prefix
        files.keys.forEach { key ->
            assertTrue(key.startsWith("organization."), "Key $key should start with 'organization.'")
        }
    }

    // ========== Equals/HashCode with ByteArray Tests ==========

    @Test
    fun testEqualsWithSameBinaryFields() {
        val doc = byteArrayOf(1, 2, 3, 4)

        val org1 = OrganizationKYCFields(
            name = "Example Corp",
            photoIncorporationDoc = doc
        )

        val org2 = OrganizationKYCFields(
            name = "Example Corp",
            photoIncorporationDoc = doc
        )

        assertEquals(org1, org2)
    }

    @Test
    fun testEqualsWithDifferentBinaryFields() {
        val org1 = OrganizationKYCFields(
            name = "Example Corp",
            photoIncorporationDoc = byteArrayOf(1, 2, 3, 4)
        )

        val org2 = OrganizationKYCFields(
            name = "Example Corp",
            photoIncorporationDoc = byteArrayOf(5, 6, 7, 8)
        )

        assertFalse(org1 == org2)
    }

    @Test
    fun testHashCodeWithBinaryFields() {
        val doc = byteArrayOf(1, 2, 3, 4)

        val org1 = OrganizationKYCFields(
            name = "Example Corp",
            photoIncorporationDoc = doc
        )

        val org2 = OrganizationKYCFields(
            name = "Example Corp",
            photoIncorporationDoc = doc
        )

        assertEquals(org1.hashCode(), org2.hashCode())
    }

    @Test
    fun testEqualsWithAllBinaryFieldsMatching() {
        val incorporationDoc = byteArrayOf(1, 2, 3)
        val proofAddress = byteArrayOf(4, 5, 6)

        val org1 = OrganizationKYCFields(
            name = "Example Corp",
            photoIncorporationDoc = incorporationDoc,
            photoProofAddress = proofAddress
        )

        val org2 = OrganizationKYCFields(
            name = "Example Corp",
            photoIncorporationDoc = incorporationDoc,
            photoProofAddress = proofAddress
        )

        assertEquals(org1, org2)
    }

    // ========== Registration Information Tests ==========

    @Test
    fun testRegistrationInformation() {
        val org = OrganizationKYCFields(
            registrationNumber = "987654321",
            registrationDate = "2020-01-15",
            registeredAddress = "123 Business Blvd, Suite 100"
        )

        val fields = org.fields()

        assertEquals("987654321", fields["organization.registration_number"])
        assertEquals("2020-01-15", fields["organization.registration_date"])
        assertEquals("123 Business Blvd, Suite 100", fields["organization.registered_address"])
    }

    @Test
    fun testVATNumber() {
        val org = OrganizationKYCFields(
            name = "Example Corp",
            VATNumber = "EU123456789"
        )

        val fields = org.fields()

        assertEquals("EU123456789", fields["organization.VAT_number"])
    }

    // ========== Corporate Structure Tests ==========

    @Test
    fun testCorporateStructure() {
        val org = OrganizationKYCFields(
            numberOfShareholders = 3,
            shareholderName = "Primary Shareholder LLC",
            directorName = "Jane Smith"
        )

        val fields = org.fields()

        assertEquals("3", fields["organization.number_of_shareholders"])
        assertEquals("Primary Shareholder LLC", fields["organization.shareholder_name"])
        assertEquals("Jane Smith", fields["organization.director_name"])
    }

    @Test
    fun testMultipleShareholderRecursiveQuery() {
        val org = OrganizationKYCFields(
            shareholderName = "Ultimate Beneficial Owner"
        )

        val fields = org.fields()

        assertEquals("Ultimate Beneficial Owner", fields["organization.shareholder_name"])
    }

    // ========== Contact Information Tests ==========

    @Test
    fun testContactInformation() {
        val org = OrganizationKYCFields(
            website = "https://example.com",
            email = "contact@example.com",
            phone = "+14155551234"
        )

        val fields = org.fields()

        assertEquals("https://example.com", fields["organization.website"])
        assertEquals("contact@example.com", fields["organization.email"])
        assertEquals("+14155551234", fields["organization.phone"])
    }

    @Test
    fun testInternationalPhoneNumber() {
        val org = OrganizationKYCFields(
            phone = "+44 20 7946 0958"
        )

        val fields = org.fields()

        assertEquals("+44 20 7946 0958", fields["organization.phone"])
    }

    // ========== Address Information Tests ==========

    @Test
    fun testAddressInformation() {
        val org = OrganizationKYCFields(
            addressCountryCode = "USA",
            stateOrProvince = "California",
            city = "San Francisco",
            postalCode = "94102"
        )

        val fields = org.fields()

        assertEquals("USA", fields["organization.address_country_code"])
        assertEquals("California", fields["organization.state_or_province"])
        assertEquals("San Francisco", fields["organization.city"])
        assertEquals("94102", fields["organization.postal_code"])
    }

    @Test
    fun testInternationalAddress() {
        val org = OrganizationKYCFields(
            addressCountryCode = "JPN",
            stateOrProvince = "Tokyo",
            city = "Shibuya",
            postalCode = "150-0001"
        )

        val fields = org.fields()

        assertEquals("JPN", fields["organization.address_country_code"])
        assertEquals("Tokyo", fields["organization.state_or_province"])
        assertEquals("Shibuya", fields["organization.city"])
        assertEquals("150-0001", fields["organization.postal_code"])
    }

    // ========== Edge Cases ==========

    @Test
    fun testFieldsWithEmptyStrings() {
        val org = OrganizationKYCFields(
            name = "",
            email = ""
        )

        val fields = org.fields()

        assertEquals(2, fields.size)
        assertEquals("", fields["organization.name"])
        assertEquals("", fields["organization.email"])
    }

    @Test
    fun testFieldsWithSpecialCharacters() {
        val org = OrganizationKYCFields(
            name = "Example Corp & Co.",
            registeredAddress = "123 Main St., Suite #100"
        )

        val fields = org.fields()

        assertEquals("Example Corp & Co.", fields["organization.name"])
        assertEquals("123 Main St., Suite #100", fields["organization.registered_address"])
    }

    @Test
    fun testFieldsWithUnicodeCharacters() {
        val org = OrganizationKYCFields(
            name = "株式会社サンプル",
            city = "東京"
        )

        val fields = org.fields()

        assertEquals("株式会社サンプル", fields["organization.name"])
        assertEquals("東京", fields["organization.city"])
    }

    @Test
    fun testEmptyByteArrays() {
        val org = OrganizationKYCFields(
            photoIncorporationDoc = byteArrayOf()
        )

        val files = org.files()

        assertEquals(1, files.size)
        assertTrue(files["organization.photo_incorporation_doc"]!!.isEmpty())
    }

    @Test
    fun testMultilineRegisteredAddress() {
        val org = OrganizationKYCFields(
            registeredAddress = "123 Business Blvd\nSuite 100\nFloor 10"
        )

        val fields = org.fields()

        assertEquals("123 Business Blvd\nSuite 100\nFloor 10", fields["organization.registered_address"])
    }

    @Test
    fun testWebsiteWithHTTPS() {
        val org = OrganizationKYCFields(
            website = "https://www.example.com"
        )

        val fields = org.fields()

        assertEquals("https://www.example.com", fields["organization.website"])
    }

    @Test
    fun testEmailWithSubdomain() {
        val org = OrganizationKYCFields(
            email = "contact@kyc.example.com"
        )

        val fields = org.fields()

        assertEquals("contact@kyc.example.com", fields["organization.email"])
    }
}
