// Copyright 2025 Soneso. All rights reserved.
// Use of this source code is governed by a license that can be
// found in the LICENSE file.

package com.soneso.stellar.sdk.unitTests.sep.sep12

import com.soneso.stellar.sdk.sep.sep12.*
import com.soneso.stellar.sdk.MuxedAccount
import com.soneso.stellar.sdk.StrKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for parsing JWT sub account values in different formats.
 *
 * SEP-12 supports multiple account identification formats:
 * - Standard account: G... (Ed25519 public key)
 * - Account with memo: G...:memo (account + memo ID)
 * - Muxed account: M... (multiplexed account with embedded memo)
 *
 * This test validates parsing logic for these formats.
 */
class MuxedAccountParsingTest {

    @Test
    fun testParseStandardGAccountAddress() {
        val accountId = "GBWMCCC3NHSKLAOJDBKKYW7SSH2PFTTNVFKWSGLWGDLEBKLOVP5JLBBP"

        assertTrue(accountId.startsWith("G"))
        assertEquals(56, accountId.length)
        assertTrue(StrKey.isValidEd25519PublicKey(accountId))
    }

    @Test
    fun testParseAccountWithMemoFormat() {
        val accountWithMemo = "GBWMCCC3NHSKLAOJDBKKYW7SSH2PFTTNVFKWSGLWGDLEBKLOVP5JLBBP:123456789"

        val parts = accountWithMemo.split(":")
        assertEquals(2, parts.size)

        val accountId = parts[0]
        val memo = parts[1]

        assertTrue(accountId.startsWith("G"))
        assertTrue(StrKey.isValidEd25519PublicKey(accountId))
        assertEquals("123456789", memo)
    }

    @Test
    fun testParseAccountWithMemoExtractionLogic() {
        fun extractAccountAndMemo(value: String): Pair<String, String?> {
            val parts = value.split(":")
            return if (parts.size == 2) {
                Pair(parts[0], parts[1])
            } else {
                Pair(value, null)
            }
        }

        val withMemo = extractAccountAndMemo("GBWMCCC3NHSKLAOJDBKKYW7SSH2PFTTNVFKWSGLWGDLEBKLOVP5JLBBP:123")
        assertEquals("GBWMCCC3NHSKLAOJDBKKYW7SSH2PFTTNVFKWSGLWGDLEBKLOVP5JLBBP", withMemo.first)
        assertEquals("123", withMemo.second)

        val withoutMemo = extractAccountAndMemo("GBWMCCC3NHSKLAOJDBKKYW7SSH2PFTTNVFKWSGLWGDLEBKLOVP5JLBBP")
        assertEquals("GBWMCCC3NHSKLAOJDBKKYW7SSH2PFTTNVFKWSGLWGDLEBKLOVP5JLBBP", withoutMemo.first)
        assertNull(withoutMemo.second)
    }

    @Test
    fun testParseMuxedAccountAddress() {
        val muxedAccountId = "MA7QYNF7SOWQ3GLR2BGMZEHXAVIRZA4KVWLTJJFC7MGXUA74P7UJVAAAAAAAAAAAAAJLK"

        assertTrue(muxedAccountId.startsWith("M"))
        assertEquals(69, muxedAccountId.length)
        assertTrue(StrKey.isValidMed25519PublicKey(muxedAccountId))
    }

    @Test
    fun testMemoExtractionFromMuxedAccount() {
        val muxedAccountId = "MA7QYNF7SOWQ3GLR2BGMZEHXAVIRZA4KVWLTJJFC7MGXUA74P7UJVAAAAAAAAAAAAAJLK"

        val muxedAccount = MuxedAccount(muxedAccountId)
        assertNotNull(muxedAccount.id)
        assertNotNull(muxedAccount.accountId)

        assertTrue(muxedAccount.accountId.startsWith("G"))
    }

    @Test
    fun testInvalidAccountFormat() {
        val invalidAccount = "INVALID_ACCOUNT_FORMAT"

        val isValidG = invalidAccount.startsWith("G") && StrKey.isValidEd25519PublicKey(invalidAccount)
        val isValidM = invalidAccount.startsWith("M") && StrKey.isValidMed25519PublicKey(invalidAccount)

        assertTrue(!isValidG)
        assertTrue(!isValidM)
    }

    @Test
    fun testAccountTypeDetection() {
        fun detectAccountType(value: String): String {
            val account = value.split(":")[0]
            return when {
                account.startsWith("G") && StrKey.isValidEd25519PublicKey(account) -> "standard"
                account.startsWith("M") && StrKey.isValidMed25519PublicKey(account) -> "muxed"
                else -> "unknown"
            }
        }

        val standardAccount = "GBWMCCC3NHSKLAOJDBKKYW7SSH2PFTTNVFKWSGLWGDLEBKLOVP5JLBBP"
        assertEquals("standard", detectAccountType(standardAccount))

        val standardWithMemo = "GBWMCCC3NHSKLAOJDBKKYW7SSH2PFTTNVFKWSGLWGDLEBKLOVP5JLBBP:123"
        assertEquals("standard", detectAccountType(standardWithMemo))

        val muxedAccount = "MA7QYNF7SOWQ3GLR2BGMZEHXAVIRZA4KVWLTJJFC7MGXUA74P7UJVAAAAAAAAAAAAAJLK"
        assertEquals("muxed", detectAccountType(muxedAccount))
    }

    @Test
    fun testMultipleAccountFormatsInJWTSub() {
        data class ParsedAccount(
            val accountId: String,
            val memo: String?,
            val isMuxed: Boolean
        )

        fun parseJwtSubAccount(subValue: String): ParsedAccount {
            val parts = subValue.split(":")
            val accountPart = parts[0]

            return when {
                accountPart.startsWith("M") && StrKey.isValidMed25519PublicKey(accountPart) -> {
                    val muxedAccount = MuxedAccount(accountPart)
                    ParsedAccount(
                        accountId = muxedAccount.accountId,
                        memo = muxedAccount.id?.toString(),
                        isMuxed = true
                    )
                }
                accountPart.startsWith("G") && StrKey.isValidEd25519PublicKey(accountPart) -> {
                    ParsedAccount(
                        accountId = accountPart,
                        memo = if (parts.size == 2) parts[1] else null,
                        isMuxed = false
                    )
                }
                else -> throw IllegalArgumentException("Invalid account format: $subValue")
            }
        }

        val standard = parseJwtSubAccount("GBWMCCC3NHSKLAOJDBKKYW7SSH2PFTTNVFKWSGLWGDLEBKLOVP5JLBBP")
        assertEquals("GBWMCCC3NHSKLAOJDBKKYW7SSH2PFTTNVFKWSGLWGDLEBKLOVP5JLBBP", standard.accountId)
        assertNull(standard.memo)
        assertTrue(!standard.isMuxed)

        val withMemo = parseJwtSubAccount("GBWMCCC3NHSKLAOJDBKKYW7SSH2PFTTNVFKWSGLWGDLEBKLOVP5JLBBP:123456")
        assertEquals("GBWMCCC3NHSKLAOJDBKKYW7SSH2PFTTNVFKWSGLWGDLEBKLOVP5JLBBP", withMemo.accountId)
        assertEquals("123456", withMemo.memo)
        assertTrue(!withMemo.isMuxed)

        val muxed = parseJwtSubAccount("MA7QYNF7SOWQ3GLR2BGMZEHXAVIRZA4KVWLTJJFC7MGXUA74P7UJVAAAAAAAAAAAAAJLK")
        assertTrue(muxed.accountId.startsWith("G"))
        assertNotNull(muxed.memo)
        assertTrue(muxed.isMuxed)
    }

    @Test
    fun testEdgeCasesInAccountParsing() {
        fun parseAccountSafely(value: String): Pair<String, String?>? {
            return try {
                val parts = value.split(":")
                val account = parts[0]

                if (account.startsWith("G") && StrKey.isValidEd25519PublicKey(account)) {
                    Pair(account, if (parts.size == 2) parts[1] else null)
                } else if (account.startsWith("M") && StrKey.isValidMed25519PublicKey(account)) {
                    val muxedAccount = MuxedAccount(account)
                    Pair(muxedAccount.accountId, muxedAccount.id?.toString())
                } else {
                    null
                }
            } catch (e: Exception) {
                null
            }
        }

        val valid = parseAccountSafely("GBWMCCC3NHSKLAOJDBKKYW7SSH2PFTTNVFKWSGLWGDLEBKLOVP5JLBBP")
        assertNotNull(valid)

        val invalid = parseAccountSafely("INVALID")
        assertNull(invalid)

        val empty = parseAccountSafely("")
        assertNull(empty)
    }
}
