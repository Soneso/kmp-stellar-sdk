// Copyright 2025 Soneso. All rights reserved.
// Use of this source code is governed by a license that can be
// found in the LICENSE file.

package com.soneso.stellar.sdk.unitTests.sep.sep09

import com.soneso.stellar.sdk.sep.sep09.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CardKYCFieldsTest {

    // ========== Field Key Constants Tests ==========

    @Test
    fun testFieldKeyConstantsHaveCardPrefix() {
        // Verify all 11 field key constants have "card." prefix
        assertEquals("card.number", CardKYCFields.NUMBER)
        assertEquals("card.expiration_date", CardKYCFields.EXPIRATION_DATE)
        assertEquals("card.cvc", CardKYCFields.CVC)
        assertEquals("card.holder_name", CardKYCFields.HOLDER_NAME)
        assertEquals("card.network", CardKYCFields.NETWORK)
        assertEquals("card.postal_code", CardKYCFields.POSTAL_CODE)
        assertEquals("card.country_code", CardKYCFields.COUNTRY_CODE)
        assertEquals("card.state_or_province", CardKYCFields.STATE_OR_PROVINCE)
        assertEquals("card.city", CardKYCFields.CITY)
        assertEquals("card.address", CardKYCFields.ADDRESS)
        assertEquals("card.token", CardKYCFields.TOKEN)
    }

    // ========== Fields Method Tests ==========

    @Test
    fun testFieldsReturnsOnlyNonNullFieldsWithPrefix() {
        val card = CardKYCFields(
            number = "4111111111111111",
            expirationDate = "29-11",
            cvc = "123"
        )

        val fields = card.fields()

        assertEquals(3, fields.size)
        assertEquals("4111111111111111", fields["card.number"])
        assertEquals("29-11", fields["card.expiration_date"])
        assertEquals("123", fields["card.cvc"])
    }

    @Test
    fun testFieldsWithAllFields() {
        val card = CardKYCFields(
            number = "4111111111111111",
            expirationDate = "29-11",
            cvc = "123",
            holderName = "John Doe",
            network = "Visa",
            postalCode = "12345",
            countryCode = "US",
            stateOrProvince = "CA",
            city = "San Francisco",
            address = "123 Main St\nApt 4",
            token = "tok_visa_1234"
        )

        val fields = card.fields()

        assertEquals(11, fields.size)
        assertEquals("4111111111111111", fields["card.number"])
        assertEquals("29-11", fields["card.expiration_date"])
        assertEquals("123", fields["card.cvc"])
        assertEquals("John Doe", fields["card.holder_name"])
        assertEquals("Visa", fields["card.network"])
        assertEquals("12345", fields["card.postal_code"])
        assertEquals("US", fields["card.country_code"])
        assertEquals("CA", fields["card.state_or_province"])
        assertEquals("San Francisco", fields["card.city"])
        assertEquals("123 Main St\nApt 4", fields["card.address"])
        assertEquals("tok_visa_1234", fields["card.token"])
    }

    @Test
    fun testFieldsEmptyWhenAllNull() {
        val card = CardKYCFields()

        val fields = card.fields()

        assertTrue(fields.isEmpty())
    }

    // ========== Card Network Tests ==========

    @Test
    fun testVisaCard() {
        val card = CardKYCFields(
            number = "4111111111111111",
            expirationDate = "29-11",
            cvc = "123",
            holderName = "John Doe",
            network = "Visa"
        )

        val fields = card.fields()

        assertEquals(5, fields.size)
        assertEquals("Visa", fields["card.network"])
    }

    @Test
    fun testMastercardCard() {
        val card = CardKYCFields(
            number = "5555555555554444",
            expirationDate = "29-11",
            cvc = "123",
            holderName = "Jane Smith",
            network = "Mastercard"
        )

        val fields = card.fields()

        assertEquals(5, fields.size)
        assertEquals("Mastercard", fields["card.network"])
    }

    @Test
    fun testAmexCard() {
        val card = CardKYCFields(
            number = "378282246310005",
            expirationDate = "29-11",
            cvc = "1234",
            holderName = "Alice Johnson",
            network = "AmEx"
        )

        val fields = card.fields()

        assertEquals(5, fields.size)
        assertEquals("AmEx", fields["card.network"])
    }

    // ========== Tokenized Card Tests ==========

    @Test
    fun testTokenizedCard() {
        val card = CardKYCFields(
            token = "tok_visa_1234567890",
            holderName = "John Doe",
            countryCode = "US"
        )

        val fields = card.fields()

        assertEquals(3, fields.size)
        assertEquals("tok_visa_1234567890", fields["card.token"])
        assertEquals("John Doe", fields["card.holder_name"])
        assertEquals("US", fields["card.country_code"])
    }

    @Test
    fun testTokenizedCardWithoutSensitiveData() {
        val card = CardKYCFields(
            token = "tok_stripe_abc123",
            holderName = "Jane Smith"
        )

        val fields = card.fields()

        assertEquals(2, fields.size)
        assertEquals("tok_stripe_abc123", fields["card.token"])
        assertEquals("Jane Smith", fields["card.holder_name"])
    }

    // ========== Billing Address Tests ==========

    @Test
    fun testBillingAddressFields() {
        val card = CardKYCFields(
            holderName = "John Doe",
            postalCode = "12345",
            countryCode = "US",
            stateOrProvince = "CA",
            city = "San Francisco",
            address = "123 Main Street"
        )

        val fields = card.fields()

        assertEquals(6, fields.size)
        assertEquals("12345", fields["card.postal_code"])
        assertEquals("US", fields["card.country_code"])
        assertEquals("CA", fields["card.state_or_province"])
        assertEquals("San Francisco", fields["card.city"])
        assertEquals("123 Main Street", fields["card.address"])
    }

    @Test
    fun testMultilineAddress() {
        val card = CardKYCFields(
            address = "123 Main Street\nApartment 4B\nBuilding C"
        )

        val fields = card.fields()

        assertEquals(1, fields.size)
        assertEquals("123 Main Street\nApartment 4B\nBuilding C", fields["card.address"])
    }

    // ========== Expiration Date Format Tests ==========

    @Test
    fun testExpirationDateYYMMFormat() {
        val card = CardKYCFields(
            expirationDate = "29-11"
        )

        val fields = card.fields()

        assertEquals(1, fields.size)
        assertEquals("29-11", fields["card.expiration_date"])
    }

    @Test
    fun testExpirationDateVariousFormats() {
        val card1 = CardKYCFields(expirationDate = "29-11")
        val card2 = CardKYCFields(expirationDate = "2029-11")
        val card3 = CardKYCFields(expirationDate = "11/29")

        assertEquals("29-11", card1.fields()["card.expiration_date"])
        assertEquals("2029-11", card2.fields()["card.expiration_date"])
        assertEquals("11/29", card3.fields()["card.expiration_date"])
    }

    // ========== CVC Tests ==========

    @Test
    fun testThreeDigitCVC() {
        val card = CardKYCFields(
            cvc = "123"
        )

        val fields = card.fields()

        assertEquals(1, fields.size)
        assertEquals("123", fields["card.cvc"])
    }

    @Test
    fun testFourDigitCVC() {
        val card = CardKYCFields(
            cvc = "1234"
        )

        val fields = card.fields()

        assertEquals(1, fields.size)
        assertEquals("1234", fields["card.cvc"])
    }

    // ========== International Cards Tests ==========

    @Test
    fun testInternationalCardWithCountryCode() {
        val card = CardKYCFields(
            number = "4111111111111111",
            expirationDate = "29-11",
            cvc = "123",
            holderName = "Hans Mueller",
            countryCode = "DE",
            stateOrProvince = "Bavaria",
            city = "Munich",
            postalCode = "80331"
        )

        val fields = card.fields()

        assertEquals(8, fields.size)
        assertEquals("DE", fields["card.country_code"])
        assertEquals("Bavaria", fields["card.state_or_province"])
        assertEquals("Munich", fields["card.city"])
        assertEquals("80331", fields["card.postal_code"])
    }

    @Test
    fun testJapaneseCard() {
        val card = CardKYCFields(
            holderName = "田中太郎",
            countryCode = "JP",
            city = "東京",
            postalCode = "100-0001"
        )

        val fields = card.fields()

        assertEquals(4, fields.size)
        assertEquals("田中太郎", fields["card.holder_name"])
        assertEquals("JP", fields["card.country_code"])
        assertEquals("東京", fields["card.city"])
        assertEquals("100-0001", fields["card.postal_code"])
    }

    // ========== Edge Cases ==========

    @Test
    fun testFieldsWithEmptyStrings() {
        val card = CardKYCFields(
            number = "",
            holderName = ""
        )

        val fields = card.fields()

        assertEquals(2, fields.size)
        assertEquals("", fields["card.number"])
        assertEquals("", fields["card.holder_name"])
    }

    @Test
    fun testFieldsWithSpecialCharacters() {
        val card = CardKYCFields(
            holderName = "O'Brien-Smith",
            address = "123 Main St., Apt #4B"
        )

        val fields = card.fields()

        assertEquals(2, fields.size)
        assertEquals("O'Brien-Smith", fields["card.holder_name"])
        assertEquals("123 Main St., Apt #4B", fields["card.address"])
    }

    @Test
    fun testFieldsWithLongCardNumber() {
        val card = CardKYCFields(
            number = "1234567890123456789"
        )

        val fields = card.fields()

        assertEquals(1, fields.size)
        assertEquals("1234567890123456789", fields["card.number"])
    }

    @Test
    fun testFieldsAllPrefixesPresent() {
        val card = CardKYCFields(
            number = "4111111111111111",
            holderName = "Test User"
        )

        val fields = card.fields()

        // Verify all keys have "card." prefix
        fields.keys.forEach { key ->
            assertTrue(key.startsWith("card."), "Key $key should start with 'card.'")
        }
    }
}
