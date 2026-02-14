// Copyright 2026 Soneso. All rights reserved.
// Use of this source code is governed by a license that can be
// found in the LICENSE file.

package com.soneso.stellar.sdk.unitTests.sep.sep06

import com.soneso.stellar.sdk.sep.sep06.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.*

/**
 * Tests for JSON parsing of SEP-6 response types.
 *
 * Verifies that all response data classes correctly deserialize from JSON,
 * including edge cases with optional fields, nested objects, and arrays.
 */
class Sep06ResponseParsingTest {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    // ========== Info Response Parsing ==========

    @Test
    fun testInfoResponseParsing() = runTest {
        val infoJson = """
            {
                "deposit": {
                    "USD": {
                        "enabled": true,
                        "authentication_required": true,
                        "min_amount": "10",
                        "max_amount": "10000",
                        "fee_fixed": "5",
                        "fee_percent": "0.5",
                        "fields": {
                            "email_address": {
                                "description": "Email for updates",
                                "optional": true
                            },
                            "type": {
                                "description": "Deposit type",
                                "choices": ["SEPA", "SWIFT"]
                            }
                        }
                    },
                    "BTC": {
                        "enabled": false
                    }
                },
                "deposit-exchange": {
                    "USD": {
                        "enabled": true,
                        "authentication_required": true,
                        "fields": {
                            "amount": {
                                "description": "Amount to deposit"
                            }
                        }
                    }
                },
                "withdraw": {
                    "USD": {
                        "enabled": true,
                        "authentication_required": true,
                        "min_amount": "50",
                        "max_amount": "5000",
                        "types": {
                            "bank_account": {
                                "fields": {
                                    "dest": {"description": "Bank account number"},
                                    "dest_extra": {"description": "Routing number", "optional": true}
                                }
                            },
                            "crypto": {
                                "fields": {
                                    "dest": {"description": "Crypto address"}
                                }
                            }
                        }
                    }
                },
                "withdraw-exchange": {
                    "USD": {
                        "enabled": false,
                        "authentication_required": true,
                        "types": {
                            "bank_account": {
                                "fields": {
                                    "dest": {"description": "Bank account"}
                                }
                            }
                        }
                    }
                },
                "fee": {
                    "enabled": true,
                    "authentication_required": true,
                    "description": "Fee endpoint for calculating fees"
                },
                "transaction": {
                    "enabled": true,
                    "authentication_required": true
                },
                "transactions": {
                    "enabled": true,
                    "authentication_required": false
                },
                "features": {
                    "account_creation": true,
                    "claimable_balances": false
                }
            }
        """.trimIndent()

        val response = json.decodeFromString<Sep06InfoResponse>(infoJson)

        // Verify deposit assets
        assertNotNull(response.deposit)
        assertEquals(2, response.deposit!!.size)

        val usdDeposit = response.deposit!!["USD"]!!
        assertTrue(usdDeposit.enabled)
        assertTrue(usdDeposit.authenticationRequired!!)
        assertEquals("10", usdDeposit.minAmount)
        assertEquals("10000", usdDeposit.maxAmount)
        assertEquals("5", usdDeposit.feeFixed)
        assertEquals("0.5", usdDeposit.feePercent)

        @Suppress("DEPRECATION")
        val depositFields = usdDeposit.fields!!
        assertEquals(2, depositFields.size)
        assertTrue(depositFields["email_address"]?.optional!!)
        assertEquals(listOf("SEPA", "SWIFT"), depositFields["type"]?.choices)

        assertFalse(response.deposit!!["BTC"]!!.enabled)

        // Verify deposit-exchange assets
        assertNotNull(response.depositExchange)
        assertTrue(response.depositExchange!!["USD"]!!.enabled)

        // Verify withdraw assets
        assertNotNull(response.withdraw)
        val usdWithdraw = response.withdraw!!["USD"]!!
        assertTrue(usdWithdraw.enabled)
        assertEquals("50", usdWithdraw.minAmount)
        assertEquals("5000", usdWithdraw.maxAmount)
        assertEquals(2, usdWithdraw.types!!.size)
        assertTrue(usdWithdraw.types!!["bank_account"]?.fields?.get("dest_extra")?.optional!!)

        // Verify withdraw-exchange assets
        assertNotNull(response.withdrawExchange)
        assertFalse(response.withdrawExchange!!["USD"]!!.enabled)

        // Verify fee endpoint info
        assertNotNull(response.fee)
        assertTrue(response.fee!!.enabled!!)
        assertTrue(response.fee!!.authenticationRequired!!)
        assertEquals("Fee endpoint for calculating fees", response.fee!!.description)

        // Verify transaction endpoint info
        assertNotNull(response.transaction)
        assertTrue(response.transaction!!.enabled!!)

        // Verify transactions endpoint info
        assertNotNull(response.transactions)
        assertTrue(response.transactions!!.enabled!!)
        assertFalse(response.transactions!!.authenticationRequired!!)

        // Verify features
        assertNotNull(response.features)
        assertTrue(response.features!!.accountCreation)
        assertFalse(response.features!!.claimableBalances)
    }

    @Test
    fun testInfoResponseMinimalParsing() = runTest {
        val minimalJson = """
            {
                "deposit": {
                    "native": {
                        "enabled": true
                    }
                },
                "withdraw": {
                    "native": {
                        "enabled": true
                    }
                }
            }
        """.trimIndent()

        val response = json.decodeFromString<Sep06InfoResponse>(minimalJson)

        assertNotNull(response.deposit)
        assertTrue(response.deposit!!["native"]!!.enabled)
        assertNull(response.deposit!!["native"]!!.authenticationRequired)
        assertNull(response.deposit!!["native"]!!.minAmount)

        assertNull(response.depositExchange)
        assertNull(response.withdrawExchange)
        assertNull(response.fee)
        assertNull(response.features)
    }

    // ========== Deposit Response Parsing ==========

    @Test
    fun testDepositResponseParsing() = runTest {
        val depositJson = """
            {
                "id": "tx-123456",
                "how": "Send payment to Bank: 12345 Account: 67890",
                "eta": 3600,
                "min_amount": "10",
                "max_amount": "1000",
                "fee_fixed": "5",
                "fee_percent": "0.5",
                "extra_info": {
                    "message": "Please include your reference number"
                },
                "instructions": {
                    "organization.bank_number": {
                        "value": "12345",
                        "description": "Bank routing number"
                    },
                    "organization.bank_account_number": {
                        "value": "67890",
                        "description": "Bank account number"
                    },
                    "organization.bank_name": {
                        "value": "Example Bank",
                        "description": "Bank name"
                    }
                }
            }
        """.trimIndent()

        val response = json.decodeFromString<Sep06DepositResponse>(depositJson)

        assertEquals("tx-123456", response.id)
        @Suppress("DEPRECATION")
        assertEquals("Send payment to Bank: 12345 Account: 67890", response.how)
        assertEquals(3600, response.eta)
        assertEquals("10", response.minAmount)
        assertEquals("1000", response.maxAmount)
        assertEquals("5", response.feeFixed)
        assertEquals("0.5", response.feePercent)

        assertNotNull(response.extraInfo)
        assertEquals("Please include your reference number", response.extraInfo!!.message)

        assertNotNull(response.instructions)
        assertEquals(3, response.instructions!!.size)
        assertEquals("12345", response.instructions!!["organization.bank_number"]?.value)
        assertEquals("Bank routing number", response.instructions!!["organization.bank_number"]?.description)
    }

    @Test
    fun testDepositResponseMinimalParsing() = runTest {
        val minimalJson = """
            {
                "id": "tx-123"
            }
        """.trimIndent()

        val response = json.decodeFromString<Sep06DepositResponse>(minimalJson)

        assertEquals("tx-123", response.id)
        assertNull(response.how)
        assertNull(response.eta)
        assertNull(response.minAmount)
        assertNull(response.maxAmount)
        assertNull(response.feeFixed)
        assertNull(response.feePercent)
        assertNull(response.extraInfo)
        assertNull(response.instructions)
    }

    @Test
    fun testDepositInstructionsParsing() = runTest {
        val instructionsJson = """
            {
                "id": "tx-456",
                "instructions": {
                    "organization.crypto_address": {
                        "value": "1BvBMSEYstWetqTFn5Au4m4GFg7xJaNVN2",
                        "description": "Bitcoin deposit address"
                    },
                    "organization.crypto_memo": {
                        "value": "12345",
                        "description": "Memo to include with payment"
                    }
                }
            }
        """.trimIndent()

        val response = json.decodeFromString<Sep06DepositResponse>(instructionsJson)

        assertNotNull(response.instructions)
        assertEquals(2, response.instructions!!.size)

        val cryptoAddress = response.instructions!!["organization.crypto_address"]!!
        assertEquals("1BvBMSEYstWetqTFn5Au4m4GFg7xJaNVN2", cryptoAddress.value)
        assertEquals("Bitcoin deposit address", cryptoAddress.description)

        val cryptoMemo = response.instructions!!["organization.crypto_memo"]!!
        assertEquals("12345", cryptoMemo.value)
        assertEquals("Memo to include with payment", cryptoMemo.description)
    }

    // ========== Withdraw Response Parsing ==========

    @Test
    fun testWithdrawResponseParsing() = runTest {
        val withdrawJson = """
            {
                "account_id": "GCIBUCGPOHWMMMFPFTDWBSVHQRT4DIBJ7AD6BZJYDITBK2LCVBYW7HUQ",
                "memo_type": "id",
                "memo": "12345",
                "id": "tx-withdraw-123",
                "eta": 7200,
                "min_amount": "50",
                "max_amount": "10000",
                "fee_fixed": "10",
                "fee_percent": "1",
                "extra_info": {
                    "message": "Withdrawal will be processed within 24 hours"
                }
            }
        """.trimIndent()

        val response = json.decodeFromString<Sep06WithdrawResponse>(withdrawJson)

        assertEquals("GCIBUCGPOHWMMMFPFTDWBSVHQRT4DIBJ7AD6BZJYDITBK2LCVBYW7HUQ", response.accountId)
        assertEquals("id", response.memoType)
        assertEquals("12345", response.memo)
        assertEquals("tx-withdraw-123", response.id)
        assertEquals(7200, response.eta)
        assertEquals("50", response.minAmount)
        assertEquals("10000", response.maxAmount)
        assertEquals("10", response.feeFixed)
        assertEquals("1", response.feePercent)
        assertNotNull(response.extraInfo)
        assertEquals("Withdrawal will be processed within 24 hours", response.extraInfo!!.message)
    }

    @Test
    fun testWithdrawResponseMinimalParsing() = runTest {
        val minimalJson = """
            {
                "account_id": "GCIBUCGPOHWMMMFPFTDWBSVHQRT4DIBJ7AD6BZJYDITBK2LCVBYW7HUQ",
                "memo_type": "text",
                "memo": "test",
                "id": "tx-123"
            }
        """.trimIndent()

        val response = json.decodeFromString<Sep06WithdrawResponse>(minimalJson)

        assertEquals("GCIBUCGPOHWMMMFPFTDWBSVHQRT4DIBJ7AD6BZJYDITBK2LCVBYW7HUQ", response.accountId)
        assertEquals("text", response.memoType)
        assertEquals("test", response.memo)
        assertEquals("tx-123", response.id)
        assertNull(response.eta)
        assertNull(response.minAmount)
        assertNull(response.maxAmount)
        assertNull(response.feeFixed)
        assertNull(response.feePercent)
        assertNull(response.extraInfo)
    }

    // ========== Transaction Parsing ==========

    @Test
    fun testTransactionParsingComplete() = runTest {
        val transactionJson = """
            {
                "transaction": {
                    "id": "tx-complete-123",
                    "kind": "deposit",
                    "status": "completed",
                    "status_eta": 0,
                    "more_info_url": "https://anchor.example.com/tx/123",
                    "amount_in": "100.00",
                    "amount_in_asset": "iso4217:USD",
                    "amount_out": "95.50",
                    "amount_out_asset": "stellar:USDC:GA5ZSEJYB37JRC5AVCIA5MOP4RHTM335X2KGX3IHOJAPP5RE34K4KZVN",
                    "amount_fee": "4.50",
                    "amount_fee_asset": "iso4217:USD",
                    "fee_details": {
                        "total": "4.50",
                        "asset": "iso4217:USD",
                        "details": [
                            {
                                "name": "Service fee",
                                "amount": "2.00",
                                "description": "Platform service fee"
                            },
                            {
                                "name": "Network fee",
                                "amount": "2.50",
                                "description": "Blockchain network fee"
                            }
                        ]
                    },
                    "quote_id": "quote-456",
                    "from": "user@example.com",
                    "to": "GDESTINATION...",
                    "external_extra": "REF123",
                    "external_extra_text": "Bank reference",
                    "deposit_memo": "memo123",
                    "deposit_memo_type": "text",
                    "started_at": "2024-01-15T10:00:00Z",
                    "updated_at": "2024-01-15T10:30:00Z",
                    "completed_at": "2024-01-15T11:00:00Z",
                    "user_action_required_by": "2024-01-15T12:00:00Z",
                    "stellar_transaction_id": "abc123def456",
                    "external_transaction_id": "ext-789",
                    "message": "Deposit completed successfully",
                    "refunded": false,
                    "claimable_balance_id": null
                }
            }
        """.trimIndent()

        val response = json.decodeFromString<Sep06TransactionResponse>(transactionJson)
        val tx = response.transaction

        assertEquals("tx-complete-123", tx.id)
        assertEquals("deposit", tx.kind)
        assertEquals("completed", tx.status)
        assertEquals(0, tx.statusEta)
        assertEquals("https://anchor.example.com/tx/123", tx.moreInfoUrl)
        assertEquals("100.00", tx.amountIn)
        assertEquals("iso4217:USD", tx.amountInAsset)
        assertEquals("95.50", tx.amountOut)
        assertEquals("stellar:USDC:GA5ZSEJYB37JRC5AVCIA5MOP4RHTM335X2KGX3IHOJAPP5RE34K4KZVN", tx.amountOutAsset)
        @Suppress("DEPRECATION")
        assertEquals("4.50", tx.amountFee)
        @Suppress("DEPRECATION")
        assertEquals("iso4217:USD", tx.amountFeeAsset)
        assertEquals("quote-456", tx.quoteId)
        assertEquals("user@example.com", tx.from)
        assertEquals("GDESTINATION...", tx.to)
        assertEquals("REF123", tx.externalExtra)
        assertEquals("Bank reference", tx.externalExtraText)
        assertEquals("memo123", tx.depositMemo)
        assertEquals("text", tx.depositMemoType)
        assertEquals("2024-01-15T10:00:00Z", tx.startedAt)
        assertEquals("2024-01-15T10:30:00Z", tx.updatedAt)
        assertEquals("2024-01-15T11:00:00Z", tx.completedAt)
        assertEquals("2024-01-15T12:00:00Z", tx.userActionRequiredBy)
        assertEquals("abc123def456", tx.stellarTransactionId)
        assertEquals("ext-789", tx.externalTransactionId)
        assertEquals("Deposit completed successfully", tx.message)
        @Suppress("DEPRECATION")
        assertFalse(tx.refunded!!)
        assertNull(tx.claimableBalanceId)

        // Verify fee details
        assertNotNull(tx.feeDetails)
        assertEquals("4.50", tx.feeDetails!!.total)
        assertEquals("iso4217:USD", tx.feeDetails!!.asset)
        assertEquals(2, tx.feeDetails!!.details!!.size)
        assertEquals("Service fee", tx.feeDetails!!.details!![0].name)
        assertEquals("2.00", tx.feeDetails!!.details!![0].amount)
        assertEquals("Platform service fee", tx.feeDetails!!.details!![0].description)
    }

    @Test
    fun testTransactionParsingWithdrawal() = runTest {
        val transactionJson = """
            {
                "transaction": {
                    "id": "tx-withdraw-456",
                    "kind": "withdrawal",
                    "status": "pending_stellar",
                    "status_eta": 600,
                    "amount_in": "500.00",
                    "amount_out": "490.00",
                    "amount_fee": "10.00",
                    "started_at": "2024-01-15T14:00:00Z",
                    "withdraw_anchor_account": "GANCHORACCOUNTXXX",
                    "withdraw_memo": "withdraw123",
                    "withdraw_memo_type": "id"
                }
            }
        """.trimIndent()

        val response = json.decodeFromString<Sep06TransactionResponse>(transactionJson)
        val tx = response.transaction

        assertEquals("withdrawal", tx.kind)
        assertEquals("pending_stellar", tx.status)
        assertEquals(600, tx.statusEta)
        assertEquals("GANCHORACCOUNTXXX", tx.withdrawAnchorAccount)
        assertEquals("withdraw123", tx.withdrawMemo)
        assertEquals("id", tx.withdrawMemoType)
    }

    @Test
    fun testTransactionParsingWithRefunds() = runTest {
        val transactionJson = """
            {
                "transaction": {
                    "id": "tx-refund-789",
                    "kind": "withdrawal",
                    "status": "refunded",
                    "amount_in": "1000.00",
                    "amount_out": "0",
                    "started_at": "2024-01-10T10:00:00Z",
                    "completed_at": "2024-01-12T10:00:00Z",
                    "refunds": {
                        "amount_refunded": "990.00",
                        "amount_fee": "10.00",
                        "payments": [
                            {
                                "id": "stellar-tx-hash-1",
                                "id_type": "stellar",
                                "amount": "500.00",
                                "fee": "5.00"
                            },
                            {
                                "id": "stellar-tx-hash-2",
                                "id_type": "stellar",
                                "amount": "490.00",
                                "fee": "5.00"
                            }
                        ]
                    }
                }
            }
        """.trimIndent()

        val response = json.decodeFromString<Sep06TransactionResponse>(transactionJson)
        val tx = response.transaction

        assertEquals("refunded", tx.status)

        assertNotNull(tx.refunds)
        assertEquals("990.00", tx.refunds!!.amountRefunded)
        assertEquals("10.00", tx.refunds!!.amountFee)
        assertEquals(2, tx.refunds!!.payments.size)

        val payment1 = tx.refunds!!.payments[0]
        assertEquals("stellar-tx-hash-1", payment1.id)
        assertEquals("stellar", payment1.idType)
        assertEquals("500.00", payment1.amount)
        assertEquals("5.00", payment1.fee)

        val payment2 = tx.refunds!!.payments[1]
        assertEquals("stellar-tx-hash-2", payment2.id)
        assertEquals("stellar", payment2.idType)
        assertEquals("490.00", payment2.amount)
        assertEquals("5.00", payment2.fee)
    }

    @Test
    fun testTransactionParsingWithRequiredInfoUpdates() = runTest {
        val transactionJson = """
            {
                "transaction": {
                    "id": "tx-info-update-123",
                    "kind": "withdrawal",
                    "status": "pending_transaction_info_update",
                    "amount_in": "750.00",
                    "started_at": "2024-01-15T10:00:00Z",
                    "required_info_message": "Please provide updated bank details",
                    "required_info_updates": {
                        "dest": {
                            "description": "Your bank account number"
                        },
                        "dest_extra": {
                            "description": "Your routing number",
                            "optional": true
                        }
                    }
                }
            }
        """.trimIndent()

        val response = json.decodeFromString<Sep06TransactionResponse>(transactionJson)
        val tx = response.transaction

        assertEquals("pending_transaction_info_update", tx.status)
        assertEquals("Please provide updated bank details", tx.requiredInfoMessage)

        assertNotNull(tx.requiredInfoUpdates)
        assertEquals(2, tx.requiredInfoUpdates!!.size)
        assertEquals("Your bank account number", tx.requiredInfoUpdates!!["dest"]?.description)
        assertTrue(tx.requiredInfoUpdates!!["dest_extra"]?.optional!!)
    }

    @Test
    fun testTransactionParsingWithInstructions() = runTest {
        val transactionJson = """
            {
                "transaction": {
                    "id": "tx-instructions-123",
                    "kind": "deposit",
                    "status": "pending_user_transfer_start",
                    "started_at": "2024-01-15T10:00:00Z",
                    "instructions": {
                        "organization.bank_number": {
                            "value": "121122676",
                            "description": "US bank routing number"
                        },
                        "organization.bank_account_number": {
                            "value": "13719713158835300",
                            "description": "US bank account number"
                        }
                    }
                }
            }
        """.trimIndent()

        val response = json.decodeFromString<Sep06TransactionResponse>(transactionJson)
        val tx = response.transaction

        assertNotNull(tx.instructions)
        assertEquals(2, tx.instructions!!.size)
        assertEquals("121122676", tx.instructions!!["organization.bank_number"]?.value)
        assertEquals("US bank routing number", tx.instructions!!["organization.bank_number"]?.description)
    }

    @Test
    fun testTransactionParsingWithClaimableBalance() = runTest {
        val transactionJson = """
            {
                "transaction": {
                    "id": "tx-claimable-123",
                    "kind": "deposit",
                    "status": "completed",
                    "amount_in": "100.00",
                    "amount_out": "95.00",
                    "started_at": "2024-01-15T10:00:00Z",
                    "completed_at": "2024-01-15T11:00:00Z",
                    "claimable_balance_id": "00000000c8ec6eb6b989c6e2a81f7bf0e1b30e9c8a3c3f5a5d0a4b3c2d1e0f9a8b"
                }
            }
        """.trimIndent()

        val response = json.decodeFromString<Sep06TransactionResponse>(transactionJson)
        val tx = response.transaction

        assertEquals("00000000c8ec6eb6b989c6e2a81f7bf0e1b30e9c8a3c3f5a5d0a4b3c2d1e0f9a8b", tx.claimableBalanceId)
    }

    // ========== Transactions List Parsing ==========

    @Test
    fun testTransactionsListParsing() = runTest {
        val transactionsJson = """
            {
                "transactions": [
                    {
                        "id": "tx-1",
                        "kind": "deposit",
                        "status": "completed",
                        "started_at": "2024-01-15T10:00:00Z"
                    },
                    {
                        "id": "tx-2",
                        "kind": "withdrawal",
                        "status": "pending_stellar",
                        "started_at": "2024-01-16T10:00:00Z"
                    },
                    {
                        "id": "tx-3",
                        "kind": "deposit-exchange",
                        "status": "pending_anchor",
                        "started_at": "2024-01-17T10:00:00Z"
                    }
                ]
            }
        """.trimIndent()

        val response = json.decodeFromString<Sep06TransactionsResponse>(transactionsJson)

        assertEquals(3, response.transactions.size)

        assertEquals("tx-1", response.transactions[0].id)
        assertEquals("deposit", response.transactions[0].kind)
        assertEquals("completed", response.transactions[0].status)

        assertEquals("tx-2", response.transactions[1].id)
        assertEquals("withdrawal", response.transactions[1].kind)
        assertEquals("pending_stellar", response.transactions[1].status)

        assertEquals("tx-3", response.transactions[2].id)
        assertEquals("deposit-exchange", response.transactions[2].kind)
        assertEquals("pending_anchor", response.transactions[2].status)
    }

    @Test
    fun testEmptyTransactionsListParsing() = runTest {
        val transactionsJson = """
            {
                "transactions": []
            }
        """.trimIndent()

        val response = json.decodeFromString<Sep06TransactionsResponse>(transactionsJson)

        assertEquals(0, response.transactions.size)
    }

    // ========== Fee Response Parsing ==========

    @Test
    fun testFeeResponseParsing() = runTest {
        val feeJson = """
            {
                "fee": "5.50"
            }
        """.trimIndent()

        val response = json.decodeFromString<Sep06FeeResponse>(feeJson)

        assertEquals("5.50", response.fee)
    }

    // ========== Fee Details Parsing ==========

    @Test
    fun testFeeDetailsParsing() = runTest {
        val feeDetailsJson = """
            {
                "total": "10.00",
                "asset": "iso4217:USD",
                "details": [
                    {
                        "name": "Processing fee",
                        "amount": "5.00",
                        "description": "Fee for processing the transaction"
                    },
                    {
                        "name": "Network fee",
                        "amount": "3.00",
                        "description": "Blockchain network cost"
                    },
                    {
                        "name": "Service fee",
                        "amount": "2.00"
                    }
                ]
            }
        """.trimIndent()

        val feeDetails = json.decodeFromString<Sep06FeeDetails>(feeDetailsJson)

        assertEquals("10.00", feeDetails.total)
        assertEquals("iso4217:USD", feeDetails.asset)
        assertEquals(3, feeDetails.details!!.size)

        assertEquals("Processing fee", feeDetails.details!![0].name)
        assertEquals("5.00", feeDetails.details!![0].amount)
        assertEquals("Fee for processing the transaction", feeDetails.details!![0].description)

        assertEquals("Network fee", feeDetails.details!![1].name)
        assertEquals("3.00", feeDetails.details!![1].amount)
        assertEquals("Blockchain network cost", feeDetails.details!![1].description)

        assertEquals("Service fee", feeDetails.details!![2].name)
        assertEquals("2.00", feeDetails.details!![2].amount)
        assertNull(feeDetails.details!![2].description)
    }

    @Test
    fun testFeeDetailsMinimalParsing() = runTest {
        val feeDetailsJson = """
            {
                "total": "5.00",
                "asset": "stellar:USDC:GA5ZSEJYB37JRC5AVCIA5MOP4RHTM335X2KGX3IHOJAPP5RE34K4KZVN"
            }
        """.trimIndent()

        val feeDetails = json.decodeFromString<Sep06FeeDetails>(feeDetailsJson)

        assertEquals("5.00", feeDetails.total)
        assertEquals("stellar:USDC:GA5ZSEJYB37JRC5AVCIA5MOP4RHTM335X2KGX3IHOJAPP5RE34K4KZVN", feeDetails.asset)
        assertNull(feeDetails.details)
    }

    // ========== Refunds Parsing ==========

    @Test
    fun testRefundsParsing() = runTest {
        val refundsJson = """
            {
                "amount_refunded": "100.00",
                "amount_fee": "5.00",
                "payments": [
                    {
                        "id": "stellar-tx-abc123",
                        "id_type": "stellar",
                        "amount": "60.00",
                        "fee": "3.00"
                    },
                    {
                        "id": "external-ref-456",
                        "id_type": "external",
                        "amount": "40.00",
                        "fee": "2.00"
                    }
                ]
            }
        """.trimIndent()

        val refunds = json.decodeFromString<Sep06Refunds>(refundsJson)

        assertEquals("100.00", refunds.amountRefunded)
        assertEquals("5.00", refunds.amountFee)
        assertEquals(2, refunds.payments.size)

        val payment1 = refunds.payments[0]
        assertEquals("stellar-tx-abc123", payment1.id)
        assertEquals("stellar", payment1.idType)
        assertEquals("60.00", payment1.amount)
        assertEquals("3.00", payment1.fee)

        val payment2 = refunds.payments[1]
        assertEquals("external-ref-456", payment2.id)
        assertEquals("external", payment2.idType)
        assertEquals("40.00", payment2.amount)
        assertEquals("2.00", payment2.fee)
    }

    @Test
    fun testRefundsSinglePayment() = runTest {
        val refundsJson = """
            {
                "amount_refunded": "50.00",
                "amount_fee": "2.50",
                "payments": [
                    {
                        "id": "single-payment-id",
                        "id_type": "stellar",
                        "amount": "50.00",
                        "fee": "2.50"
                    }
                ]
            }
        """.trimIndent()

        val refunds = json.decodeFromString<Sep06Refunds>(refundsJson)

        assertEquals(1, refunds.payments.size)
        assertEquals("single-payment-id", refunds.payments[0].id)
    }

    // ========== Edge Cases ==========

    @Test
    fun testTransactionWithNullFields() = runTest {
        val transactionJson = """
            {
                "transaction": {
                    "id": "tx-minimal",
                    "kind": "deposit",
                    "status": "incomplete",
                    "amount_in": null,
                    "amount_out": null,
                    "completed_at": null,
                    "stellar_transaction_id": null
                }
            }
        """.trimIndent()

        val response = json.decodeFromString<Sep06TransactionResponse>(transactionJson)
        val tx = response.transaction

        assertEquals("tx-minimal", tx.id)
        assertEquals("deposit", tx.kind)
        assertEquals("incomplete", tx.status)
        assertNull(tx.amountIn)
        assertNull(tx.amountOut)
        assertNull(tx.completedAt)
        assertNull(tx.stellarTransactionId)
    }

    @Test
    fun testUnknownFieldsIgnored() = runTest {
        val transactionJson = """
            {
                "transaction": {
                    "id": "tx-unknown-fields",
                    "kind": "deposit",
                    "status": "completed",
                    "unknown_field": "should be ignored",
                    "another_unknown": 12345,
                    "nested_unknown": {
                        "key": "value"
                    }
                }
            }
        """.trimIndent()

        val response = json.decodeFromString<Sep06TransactionResponse>(transactionJson)
        val tx = response.transaction

        assertEquals("tx-unknown-fields", tx.id)
        assertEquals("deposit", tx.kind)
        assertEquals("completed", tx.status)
    }
}
