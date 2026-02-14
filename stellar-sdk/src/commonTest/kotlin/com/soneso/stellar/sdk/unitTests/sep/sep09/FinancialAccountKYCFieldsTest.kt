// Copyright 2025 Soneso. All rights reserved.
// Use of this source code is governed by a license that can be
// found in the LICENSE file.

package com.soneso.stellar.sdk.unitTests.sep.sep09

import com.soneso.stellar.sdk.sep.sep09.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FinancialAccountKYCFieldsTest {

    // ========== Field Key Constants Tests ==========

    @Test
    fun testFieldKeyConstants() {
        // Verify all 14 field key constants match SEP-09 spec exactly
        assertEquals("bank_name", FinancialAccountKYCFields.BANK_NAME)
        assertEquals("bank_account_type", FinancialAccountKYCFields.BANK_ACCOUNT_TYPE)
        assertEquals("bank_account_number", FinancialAccountKYCFields.BANK_ACCOUNT_NUMBER)
        assertEquals("bank_number", FinancialAccountKYCFields.BANK_NUMBER)
        assertEquals("bank_phone_number", FinancialAccountKYCFields.BANK_PHONE_NUMBER)
        assertEquals("bank_branch_number", FinancialAccountKYCFields.BANK_BRANCH_NUMBER)
        assertEquals("external_transfer_memo", FinancialAccountKYCFields.EXTERNAL_TRANSFER_MEMO)
        assertEquals("clabe_number", FinancialAccountKYCFields.CLABE_NUMBER)
        assertEquals("cbu_number", FinancialAccountKYCFields.CBU_NUMBER)
        assertEquals("cbu_alias", FinancialAccountKYCFields.CBU_ALIAS)
        assertEquals("mobile_money_number", FinancialAccountKYCFields.MOBILE_MONEY_NUMBER)
        assertEquals("mobile_money_provider", FinancialAccountKYCFields.MOBILE_MONEY_PROVIDER)
        assertEquals("crypto_address", FinancialAccountKYCFields.CRYPTO_ADDRESS)
        assertEquals("crypto_memo", FinancialAccountKYCFields.CRYPTO_MEMO)
    }

    // ========== Fields Method Tests ==========

    @Test
    fun testFieldsReturnsOnlyNonNullFields() {
        val account = FinancialAccountKYCFields(
            bankName = "Example Bank",
            bankAccountNumber = "1234567890",
            bankNumber = "123456789"
        )

        val fields = account.fields()

        assertEquals(3, fields.size)
        assertEquals("Example Bank", fields["bank_name"])
        assertEquals("1234567890", fields["bank_account_number"])
        assertEquals("123456789", fields["bank_number"])
    }

    @Test
    fun testFieldsWithAllFields() {
        val account = FinancialAccountKYCFields(
            bankName = "Example Bank",
            bankAccountType = "checking",
            bankAccountNumber = "1234567890",
            bankNumber = "123456789",
            bankPhoneNumber = "+14155551234",
            bankBranchNumber = "001",
            externalTransferMemo = "MEMO123",
            clabeNumber = "012345678901234567",
            cbuNumber = "1234567890123456789012",
            cbuAlias = "alias.bank",
            mobileMoneyNumber = "+14155551234",
            mobileMoneyProvider = "M-PESA",
            cryptoAddress = "GDJKBX7VK7FWPQHRLSCXKMFU37QHYUZ3VVYP2BVJDNMEJY3LBJMGHB75",
            cryptoMemo = "12345"
        )

        val fields = account.fields()

        assertEquals(14, fields.size)
        assertEquals("Example Bank", fields["bank_name"])
        assertEquals("checking", fields["bank_account_type"])
        assertEquals("1234567890", fields["bank_account_number"])
        assertEquals("123456789", fields["bank_number"])
        assertEquals("+14155551234", fields["bank_phone_number"])
        assertEquals("001", fields["bank_branch_number"])
        assertEquals("MEMO123", fields["external_transfer_memo"])
        assertEquals("012345678901234567", fields["clabe_number"])
        assertEquals("1234567890123456789012", fields["cbu_number"])
        assertEquals("alias.bank", fields["cbu_alias"])
        assertEquals("+14155551234", fields["mobile_money_number"])
        assertEquals("M-PESA", fields["mobile_money_provider"])
        assertEquals("GDJKBX7VK7FWPQHRLSCXKMFU37QHYUZ3VVYP2BVJDNMEJY3LBJMGHB75", fields["crypto_address"])
        assertEquals("12345", fields["crypto_memo"])
    }

    @Test
    fun testFieldsWithPrefix() {
        val account = FinancialAccountKYCFields(
            bankName = "Corporate Bank",
            bankAccountNumber = "9876543210"
        )

        val fields = account.fields(keyPrefix = "organization.")

        assertEquals(2, fields.size)
        assertEquals("Corporate Bank", fields["organization.bank_name"])
        assertEquals("9876543210", fields["organization.bank_account_number"])
    }

    @Test
    fun testFieldsWithEmptyPrefix() {
        val account = FinancialAccountKYCFields(
            bankName = "Test Bank"
        )

        val fields = account.fields(keyPrefix = "")

        assertEquals(1, fields.size)
        assertEquals("Test Bank", fields["bank_name"])
    }

    @Test
    fun testFieldsEmptyWhenAllNull() {
        val account = FinancialAccountKYCFields()

        val fields = account.fields()

        assertTrue(fields.isEmpty())
    }

    @Test
    fun testFieldsWithCustomPrefix() {
        val account = FinancialAccountKYCFields(
            bankName = "Test Bank",
            bankAccountNumber = "123"
        )

        val fields = account.fields(keyPrefix = "custom_prefix.")

        assertEquals(2, fields.size)
        assertEquals("Test Bank", fields["custom_prefix.bank_name"])
        assertEquals("123", fields["custom_prefix.bank_account_number"])
    }

    // ========== Deprecated Field Tests ==========

    @Test
    fun testDeprecatedCryptoMemoField() {
        @Suppress("DEPRECATION")
        val account = FinancialAccountKYCFields(
            cryptoAddress = "GDJKBX7VK7FWPQHRLSCXKMFU37QHYUZ3VVYP2BVJDNMEJY3LBJMGHB75",
            cryptoMemo = "DEPRECATED_MEMO"
        )

        val fields = account.fields()

        assertEquals(2, fields.size)
        assertEquals("GDJKBX7VK7FWPQHRLSCXKMFU37QHYUZ3VVYP2BVJDNMEJY3LBJMGHB75", fields["crypto_address"])
        assertEquals("DEPRECATED_MEMO", fields["crypto_memo"])
    }

    @Test
    fun testExternalTransferMemoPreferredOverCryptoMemo() {
        @Suppress("DEPRECATION")
        val account = FinancialAccountKYCFields(
            cryptoAddress = "GDJKBX7VK7FWPQHRLSCXKMFU37QHYUZ3VVYP2BVJDNMEJY3LBJMGHB75",
            externalTransferMemo = "NEW_MEMO",
            cryptoMemo = "OLD_MEMO"
        )

        val fields = account.fields()

        assertEquals(3, fields.size)
        assertEquals("NEW_MEMO", fields["external_transfer_memo"])
        assertEquals("OLD_MEMO", fields["crypto_memo"])
    }

    // ========== Regional Banking System Tests ==========

    @Test
    fun testCLABENumberForMexico() {
        val account = FinancialAccountKYCFields(
            bankName = "Banco de México",
            clabeNumber = "012345678901234567"
        )

        val fields = account.fields()

        assertEquals(2, fields.size)
        assertEquals("Banco de México", fields["bank_name"])
        assertEquals("012345678901234567", fields["clabe_number"])
    }

    @Test
    fun testCBUNumberForArgentina() {
        val account = FinancialAccountKYCFields(
            bankName = "Banco de Argentina",
            cbuNumber = "1234567890123456789012",
            cbuAlias = "mi.alias.banco"
        )

        val fields = account.fields()

        assertEquals(3, fields.size)
        assertEquals("Banco de Argentina", fields["bank_name"])
        assertEquals("1234567890123456789012", fields["cbu_number"])
        assertEquals("mi.alias.banco", fields["cbu_alias"])
    }

    @Test
    fun testMobileMoneyAccount() {
        val account = FinancialAccountKYCFields(
            mobileMoneyNumber = "+254712345678",
            mobileMoneyProvider = "M-PESA"
        )

        val fields = account.fields()

        assertEquals(2, fields.size)
        assertEquals("+254712345678", fields["mobile_money_number"])
        assertEquals("M-PESA", fields["mobile_money_provider"])
    }

    @Test
    fun testCryptocurrencyAccount() {
        val account = FinancialAccountKYCFields(
            cryptoAddress = "GDJKBX7VK7FWPQHRLSCXKMFU37QHYUZ3VVYP2BVJDNMEJY3LBJMGHB75",
            externalTransferMemo = "12345"
        )

        val fields = account.fields()

        assertEquals(2, fields.size)
        assertEquals("GDJKBX7VK7FWPQHRLSCXKMFU37QHYUZ3VVYP2BVJDNMEJY3LBJMGHB75", fields["crypto_address"])
        assertEquals("12345", fields["external_transfer_memo"])
    }

    // ========== Edge Cases ==========

    @Test
    fun testFieldsWithEmptyStrings() {
        val account = FinancialAccountKYCFields(
            bankName = "",
            bankAccountNumber = ""
        )

        val fields = account.fields()

        assertEquals(2, fields.size)
        assertEquals("", fields["bank_name"])
        assertEquals("", fields["bank_account_number"])
    }

    @Test
    fun testFieldsWithSpecialCharacters() {
        val account = FinancialAccountKYCFields(
            bankName = "Bank & Trust Co.",
            bankAccountNumber = "123-456-789",
            cbuAlias = "user@bank.com"
        )

        val fields = account.fields()

        assertEquals(3, fields.size)
        assertEquals("Bank & Trust Co.", fields["bank_name"])
        assertEquals("123-456-789", fields["bank_account_number"])
        assertEquals("user@bank.com", fields["cbu_alias"])
    }

    @Test
    fun testFieldsWithUnicodeCharacters() {
        val account = FinancialAccountKYCFields(
            bankName = "银行名称",
            bankBranchNumber = "支行001"
        )

        val fields = account.fields()

        assertEquals(2, fields.size)
        assertEquals("银行名称", fields["bank_name"])
        assertEquals("支行001", fields["bank_branch_number"])
    }
}
