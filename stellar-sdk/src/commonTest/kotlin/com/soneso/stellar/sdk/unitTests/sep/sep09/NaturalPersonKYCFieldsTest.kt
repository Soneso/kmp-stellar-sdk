// Copyright 2025 Soneso. All rights reserved.
// Use of this source code is governed by a license that can be
// found in the LICENSE file.

package com.soneso.stellar.sdk.unitTests.sep.sep09

import com.soneso.stellar.sdk.sep.sep09.*
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NaturalPersonKYCFieldsTest {

    // ========== Text Field Key Constants Tests ==========

    @Test
    fun testTextFieldKeyConstants() {
        // Verify all 28 text field key constants match SEP-09 spec
        assertEquals("last_name", NaturalPersonKYCFields.LAST_NAME)
        assertEquals("first_name", NaturalPersonKYCFields.FIRST_NAME)
        assertEquals("additional_name", NaturalPersonKYCFields.ADDITIONAL_NAME)
        assertEquals("address_country_code", NaturalPersonKYCFields.ADDRESS_COUNTRY_CODE)
        assertEquals("state_or_province", NaturalPersonKYCFields.STATE_OR_PROVINCE)
        assertEquals("city", NaturalPersonKYCFields.CITY)
        assertEquals("postal_code", NaturalPersonKYCFields.POSTAL_CODE)
        assertEquals("address", NaturalPersonKYCFields.ADDRESS)
        assertEquals("mobile_number", NaturalPersonKYCFields.MOBILE_NUMBER)
        assertEquals("mobile_number_format", NaturalPersonKYCFields.MOBILE_NUMBER_FORMAT)
        assertEquals("email_address", NaturalPersonKYCFields.EMAIL_ADDRESS)
        assertEquals("birth_date", NaturalPersonKYCFields.BIRTH_DATE)
        assertEquals("birth_place", NaturalPersonKYCFields.BIRTH_PLACE)
        assertEquals("birth_country_code", NaturalPersonKYCFields.BIRTH_COUNTRY_CODE)
        assertEquals("tax_id", NaturalPersonKYCFields.TAX_ID)
        assertEquals("tax_id_name", NaturalPersonKYCFields.TAX_ID_NAME)
        assertEquals("occupation", NaturalPersonKYCFields.OCCUPATION)
        assertEquals("employer_name", NaturalPersonKYCFields.EMPLOYER_NAME)
        assertEquals("employer_address", NaturalPersonKYCFields.EMPLOYER_ADDRESS)
        assertEquals("language_code", NaturalPersonKYCFields.LANGUAGE_CODE)
        assertEquals("id_type", NaturalPersonKYCFields.ID_TYPE)
        assertEquals("id_country_code", NaturalPersonKYCFields.ID_COUNTRY_CODE)
        assertEquals("id_issue_date", NaturalPersonKYCFields.ID_ISSUE_DATE)
        assertEquals("id_expiration_date", NaturalPersonKYCFields.ID_EXPIRATION_DATE)
        assertEquals("id_number", NaturalPersonKYCFields.ID_NUMBER)
        assertEquals("ip_address", NaturalPersonKYCFields.IP_ADDRESS)
        assertEquals("sex", NaturalPersonKYCFields.SEX)
        assertEquals("referral_id", NaturalPersonKYCFields.REFERRAL_ID)
    }

    // ========== Binary Field Key Constants Tests ==========

    @Test
    fun testBinaryFieldKeyConstants() {
        // Verify all 6 binary field key constants match SEP-09 spec
        assertEquals("photo_id_front", NaturalPersonKYCFields.PHOTO_ID_FRONT)
        assertEquals("photo_id_back", NaturalPersonKYCFields.PHOTO_ID_BACK)
        assertEquals("notary_approval_of_photo_id", NaturalPersonKYCFields.NOTARY_APPROVAL_OF_PHOTO_ID)
        assertEquals("photo_proof_residence", NaturalPersonKYCFields.PHOTO_PROOF_RESIDENCE)
        assertEquals("proof_of_income", NaturalPersonKYCFields.PROOF_OF_INCOME)
        assertEquals("proof_of_liveness", NaturalPersonKYCFields.PROOF_OF_LIVENESS)
    }

    // ========== Fields Method Tests ==========

    @Test
    fun testFieldsReturnsOnlyNonNullTextFields() {
        val person = NaturalPersonKYCFields(
            firstName = "John",
            lastName = "Doe",
            emailAddress = "john@example.com"
        )

        val fields = person.fields()

        assertEquals(3, fields.size)
        assertEquals("John", fields["first_name"])
        assertEquals("Doe", fields["last_name"])
        assertEquals("john@example.com", fields["email_address"])
    }

    @Test
    fun testFieldsWithAllTextFields() {
        val person = NaturalPersonKYCFields(
            lastName = "Doe",
            firstName = "John",
            additionalName = "Michael",
            addressCountryCode = "USA",
            stateOrProvince = "California",
            city = "San Francisco",
            postalCode = "94102",
            address = "123 Main St",
            mobileNumber = "+14155551234",
            mobileNumberFormat = "E.164",
            emailAddress = "john@example.com",
            birthDate = LocalDate(1990, 1, 15),
            birthPlace = "New York, NY, USA",
            birthCountryCode = "USA",
            taxId = "123-45-6789",
            taxIdName = "SSN",
            occupation = "123",
            employerName = "Example Corp",
            employerAddress = "456 Business Ave",
            languageCode = "en",
            idType = "passport",
            idCountryCode = "USA",
            idIssueDate = LocalDate(2020, 1, 1),
            idExpirationDate = LocalDate(2030, 1, 1),
            idNumber = "123456789",
            ipAddress = "192.168.1.1",
            sex = "male",
            referralId = "REF123"
        )

        val fields = person.fields()

        assertEquals(28, fields.size)
        assertEquals("Doe", fields["last_name"])
        assertEquals("John", fields["first_name"])
        assertEquals("Michael", fields["additional_name"])
        assertEquals("USA", fields["address_country_code"])
        assertEquals("California", fields["state_or_province"])
        assertEquals("San Francisco", fields["city"])
        assertEquals("94102", fields["postal_code"])
        assertEquals("123 Main St", fields["address"])
        assertEquals("+14155551234", fields["mobile_number"])
        assertEquals("E.164", fields["mobile_number_format"])
        assertEquals("john@example.com", fields["email_address"])
        assertEquals("1990-01-15", fields["birth_date"])
        assertEquals("New York, NY, USA", fields["birth_place"])
        assertEquals("USA", fields["birth_country_code"])
        assertEquals("123-45-6789", fields["tax_id"])
        assertEquals("SSN", fields["tax_id_name"])
        assertEquals("123", fields["occupation"])
        assertEquals("Example Corp", fields["employer_name"])
        assertEquals("456 Business Ave", fields["employer_address"])
        assertEquals("en", fields["language_code"])
        assertEquals("passport", fields["id_type"])
        assertEquals("USA", fields["id_country_code"])
        assertEquals("2020-01-01", fields["id_issue_date"])
        assertEquals("2030-01-01", fields["id_expiration_date"])
        assertEquals("123456789", fields["id_number"])
        assertEquals("192.168.1.1", fields["ip_address"])
        assertEquals("male", fields["sex"])
        assertEquals("REF123", fields["referral_id"])
    }

    @Test
    fun testFieldsEmptyWhenAllNull() {
        val person = NaturalPersonKYCFields()

        val fields = person.fields()

        assertTrue(fields.isEmpty())
    }

    // ========== LocalDate Serialization Tests ==========

    @Test
    fun testLocalDateSerializesAsYYYYMMDD() {
        val person = NaturalPersonKYCFields(
            birthDate = LocalDate(1990, 1, 15)
        )

        val fields = person.fields()

        assertEquals(1, fields.size)
        assertEquals("1990-01-15", fields["birth_date"])
    }

    @Test
    fun testAllDateFieldsSerialization() {
        val person = NaturalPersonKYCFields(
            birthDate = LocalDate(1990, 1, 15),
            idIssueDate = LocalDate(2020, 6, 1),
            idExpirationDate = LocalDate(2030, 6, 1)
        )

        val fields = person.fields()

        assertEquals(3, fields.size)
        assertEquals("1990-01-15", fields["birth_date"])
        assertEquals("2020-06-01", fields["id_issue_date"])
        assertEquals("2030-06-01", fields["id_expiration_date"])
    }

    @Test
    fun testDateFieldsWithSingleDigitDayMonth() {
        val person = NaturalPersonKYCFields(
            birthDate = LocalDate(1990, 1, 5)
        )

        val fields = person.fields()

        assertEquals("1990-01-05", fields["birth_date"])
    }

    // ========== Nested Fields Tests ==========

    @Test
    fun testNestedFinancialAccountFieldsIncluded() {
        val person = NaturalPersonKYCFields(
            firstName = "John",
            lastName = "Doe",
            financialAccountKYCFields = FinancialAccountKYCFields(
                bankName = "Example Bank",
                bankAccountNumber = "1234567890"
            )
        )

        val fields = person.fields()

        assertEquals(4, fields.size)
        assertEquals("John", fields["first_name"])
        assertEquals("Doe", fields["last_name"])
        assertEquals("Example Bank", fields["bank_name"])
        assertEquals("1234567890", fields["bank_account_number"])
    }

    @Test
    fun testNestedCardFieldsIncluded() {
        val person = NaturalPersonKYCFields(
            firstName = "John",
            lastName = "Doe",
            cardKYCFields = CardKYCFields(
                number = "4111111111111111",
                holderName = "John Doe"
            )
        )

        val fields = person.fields()

        assertEquals(4, fields.size)
        assertEquals("John", fields["first_name"])
        assertEquals("Doe", fields["last_name"])
        assertEquals("4111111111111111", fields["card.number"])
        assertEquals("John Doe", fields["card.holder_name"])
    }

    @Test
    fun testBothNestedFieldsIncluded() {
        val person = NaturalPersonKYCFields(
            firstName = "John",
            financialAccountKYCFields = FinancialAccountKYCFields(
                bankName = "Example Bank"
            ),
            cardKYCFields = CardKYCFields(
                token = "tok_visa_123"
            )
        )

        val fields = person.fields()

        assertEquals(3, fields.size)
        assertEquals("John", fields["first_name"])
        assertEquals("Example Bank", fields["bank_name"])
        assertEquals("tok_visa_123", fields["card.token"])
    }

    // ========== Files Method Tests ==========

    @Test
    fun testFilesReturnsOnlyNonNullBinaryFields() {
        val photoIdFront = byteArrayOf(1, 2, 3, 4)
        val photoIdBack = byteArrayOf(5, 6, 7, 8)

        val person = NaturalPersonKYCFields(
            photoIdFront = photoIdFront,
            photoIdBack = photoIdBack
        )

        val files = person.files()

        assertEquals(2, files.size)
        assertTrue(files["photo_id_front"]!!.contentEquals(photoIdFront))
        assertTrue(files["photo_id_back"]!!.contentEquals(photoIdBack))
    }

    @Test
    fun testFilesWithAllBinaryFields() {
        val photoIdFront = byteArrayOf(1, 2, 3)
        val photoIdBack = byteArrayOf(4, 5, 6)
        val notaryApproval = byteArrayOf(7, 8, 9)
        val proofResidence = byteArrayOf(10, 11, 12)
        val proofIncome = byteArrayOf(13, 14, 15)
        val proofLiveness = byteArrayOf(16, 17, 18)

        val person = NaturalPersonKYCFields(
            photoIdFront = photoIdFront,
            photoIdBack = photoIdBack,
            notaryApprovalOfPhotoId = notaryApproval,
            photoProofResidence = proofResidence,
            proofOfIncome = proofIncome,
            proofOfLiveness = proofLiveness
        )

        val files = person.files()

        assertEquals(6, files.size)
        assertTrue(files["photo_id_front"]!!.contentEquals(photoIdFront))
        assertTrue(files["photo_id_back"]!!.contentEquals(photoIdBack))
        assertTrue(files["notary_approval_of_photo_id"]!!.contentEquals(notaryApproval))
        assertTrue(files["photo_proof_residence"]!!.contentEquals(proofResidence))
        assertTrue(files["proof_of_income"]!!.contentEquals(proofIncome))
        assertTrue(files["proof_of_liveness"]!!.contentEquals(proofLiveness))
    }

    @Test
    fun testFilesEmptyWhenAllNull() {
        val person = NaturalPersonKYCFields()

        val files = person.files()

        assertTrue(files.isEmpty())
    }

    // ========== Equals/HashCode with ByteArray Tests ==========

    @Test
    fun testEqualsWithSameBinaryFields() {
        val photoId = byteArrayOf(1, 2, 3, 4)

        val person1 = NaturalPersonKYCFields(
            firstName = "John",
            photoIdFront = photoId
        )

        val person2 = NaturalPersonKYCFields(
            firstName = "John",
            photoIdFront = photoId
        )

        assertEquals(person1, person2)
    }

    @Test
    fun testEqualsWithDifferentBinaryFields() {
        val person1 = NaturalPersonKYCFields(
            firstName = "John",
            photoIdFront = byteArrayOf(1, 2, 3, 4)
        )

        val person2 = NaturalPersonKYCFields(
            firstName = "John",
            photoIdFront = byteArrayOf(5, 6, 7, 8)
        )

        assertFalse(person1 == person2)
    }

    @Test
    fun testHashCodeWithBinaryFields() {
        val photoId = byteArrayOf(1, 2, 3, 4)

        val person1 = NaturalPersonKYCFields(
            firstName = "John",
            photoIdFront = photoId
        )

        val person2 = NaturalPersonKYCFields(
            firstName = "John",
            photoIdFront = photoId
        )

        assertEquals(person1.hashCode(), person2.hashCode())
    }

    @Test
    fun testEqualsWithAllBinaryFieldsMatching() {
        val photoIdFront = byteArrayOf(1, 2, 3)
        val photoIdBack = byteArrayOf(4, 5, 6)

        val person1 = NaturalPersonKYCFields(
            firstName = "John",
            photoIdFront = photoIdFront,
            photoIdBack = photoIdBack
        )

        val person2 = NaturalPersonKYCFields(
            firstName = "John",
            photoIdFront = photoIdFront,
            photoIdBack = photoIdBack
        )

        assertEquals(person1, person2)
    }

    // ========== Copy Tests ==========

    @Test
    fun testCopyPreservesByteArrayContent() {
        val photoId = byteArrayOf(1, 2, 3, 4)

        val person1 = NaturalPersonKYCFields(
            firstName = "John",
            photoIdFront = photoId
        )

        val person2 = person1.copy(lastName = "Doe")

        assertEquals("John", person2.firstName)
        assertEquals("Doe", person2.lastName)
        assertTrue(person2.photoIdFront!!.contentEquals(photoId))
    }

    // ========== ID Type Tests ==========

    @Test
    fun testPassportID() {
        val person = NaturalPersonKYCFields(
            idType = "passport",
            idNumber = "123456789",
            idCountryCode = "USA",
            idIssueDate = LocalDate(2020, 1, 1),
            idExpirationDate = LocalDate(2030, 1, 1)
        )

        val fields = person.fields()

        assertEquals("passport", fields["id_type"])
        assertEquals("123456789", fields["id_number"])
        assertEquals("USA", fields["id_country_code"])
        assertEquals("2020-01-01", fields["id_issue_date"])
        assertEquals("2030-01-01", fields["id_expiration_date"])
    }

    @Test
    fun testDriversLicenseID() {
        val person = NaturalPersonKYCFields(
            idType = "drivers_license",
            idNumber = "D1234567",
            idCountryCode = "USA"
        )

        val fields = person.fields()

        assertEquals("drivers_license", fields["id_type"])
        assertEquals("D1234567", fields["id_number"])
    }

    @Test
    fun testNationalIDCard() {
        val person = NaturalPersonKYCFields(
            idType = "id_card",
            idNumber = "123456789",
            idCountryCode = "DEU"
        )

        val fields = person.fields()

        assertEquals("id_card", fields["id_type"])
        assertEquals("123456789", fields["id_number"])
        assertEquals("DEU", fields["id_country_code"])
    }

    // ========== International Standards Tests ==========

    @Test
    fun testE164PhoneNumberFormat() {
        val person = NaturalPersonKYCFields(
            mobileNumber = "+14155551234",
            mobileNumberFormat = "E.164"
        )

        val fields = person.fields()

        assertEquals("+14155551234", fields["mobile_number"])
        assertEquals("E.164", fields["mobile_number_format"])
    }

    @Test
    fun testISO3166CountryCodes() {
        val person = NaturalPersonKYCFields(
            addressCountryCode = "USA",
            birthCountryCode = "CAN",
            idCountryCode = "GBR"
        )

        val fields = person.fields()

        assertEquals("USA", fields["address_country_code"])
        assertEquals("CAN", fields["birth_country_code"])
        assertEquals("GBR", fields["id_country_code"])
    }

    @Test
    fun testISO639LanguageCode() {
        val person = NaturalPersonKYCFields(
            languageCode = "en"
        )

        val fields = person.fields()

        assertEquals("en", fields["language_code"])
    }

    @Test
    fun testISCOOccupationCode() {
        val person = NaturalPersonKYCFields(
            occupation = "123"
        )

        val fields = person.fields()

        assertEquals("123", fields["occupation"])
    }

    // ========== Edge Cases ==========

    @Test
    fun testFieldsWithEmptyStrings() {
        val person = NaturalPersonKYCFields(
            firstName = "",
            lastName = ""
        )

        val fields = person.fields()

        assertEquals(2, fields.size)
        assertEquals("", fields["first_name"])
        assertEquals("", fields["last_name"])
    }

    @Test
    fun testFieldsWithSpecialCharacters() {
        val person = NaturalPersonKYCFields(
            firstName = "Jean-Pierre",
            lastName = "O'Brien",
            address = "123 Main St., Apt #4B"
        )

        val fields = person.fields()

        assertEquals("Jean-Pierre", fields["first_name"])
        assertEquals("O'Brien", fields["last_name"])
        assertEquals("123 Main St., Apt #4B", fields["address"])
    }

    @Test
    fun testFieldsWithUnicodeCharacters() {
        val person = NaturalPersonKYCFields(
            firstName = "田中",
            lastName = "太郎",
            city = "東京"
        )

        val fields = person.fields()

        assertEquals("田中", fields["first_name"])
        assertEquals("太郎", fields["last_name"])
        assertEquals("東京", fields["city"])
    }

    @Test
    fun testEmptyByteArrays() {
        val person = NaturalPersonKYCFields(
            photoIdFront = byteArrayOf()
        )

        val files = person.files()

        assertEquals(1, files.size)
        assertTrue(files["photo_id_front"]!!.isEmpty())
    }

    @Test
    fun testMultilineAddress() {
        val person = NaturalPersonKYCFields(
            address = "123 Main Street\nApartment 4B\nBuilding C"
        )

        val fields = person.fields()

        assertEquals("123 Main Street\nApartment 4B\nBuilding C", fields["address"])
    }
}
