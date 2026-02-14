// Copyright 2026 Soneso. All rights reserved.
// Use of this source code is governed by a license that can be
// found in the LICENSE file.

package com.soneso.stellar.sdk.unitTests.sep.sep30

import com.soneso.stellar.sdk.sep.sep30.*
import com.soneso.stellar.sdk.sep.sep30.exceptions.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlin.test.*

/**
 * Tests for JSON parsing of SEP-30 response types.
 *
 * Verifies that [Sep30AccountResponse.fromJson], [Sep30AccountsResponse.fromJson],
 * [Sep30SignatureResponse.fromJson], [Sep30ResponseIdentity.fromJson], and
 * [Sep30ResponseSigner.fromJson] correctly deserialize from JSON, including edge cases
 * with missing fields, nullable properties, and unknown keys.
 */
class Sep30ResponseParsingTest {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    // ========== Sep30AccountResponse - Single Identity ==========

    @Test
    fun testParseSep30AccountResponseSingleIdentity() = runTest {
        val jsonStr = """
            {
                "address": "GDQNY3PBOBD7WLWZGZQS6WNST7VJHPKXSQFWBHQFHV3XMUC5DHAST4",
                "identities": [
                    {"role": "owner", "authenticated": true}
                ],
                "signers": [
                    {"key": "GCXE4HKPNGT4FOBMGUH3RGSTFLHPECGQO3RLCSPY3MHCM5NHQWMIH5E"}
                ]
            }
        """.trimIndent()

        val jsonObject = json.decodeFromString<JsonObject>(jsonStr)
        val response = Sep30AccountResponse.fromJson(jsonObject)

        assertEquals("GDQNY3PBOBD7WLWZGZQS6WNST7VJHPKXSQFWBHQFHV3XMUC5DHAST4", response.address)
        assertEquals(1, response.identities.size)
        assertEquals("owner", response.identities[0].role)
        assertEquals(true, response.identities[0].authenticated)
        assertEquals(1, response.signers.size)
        assertEquals("GCXE4HKPNGT4FOBMGUH3RGSTFLHPECGQO3RLCSPY3MHCM5NHQWMIH5E", response.signers[0].key)
    }

    // ========== Sep30AccountResponse - Multiple Identities ==========

    @Test
    fun testParseSep30AccountResponseMultipleIdentities() = runTest {
        val jsonStr = """
            {
                "address": "GDQNY3PBOBD7WLWZGZQS6WNST7VJHPKXSQFWBHQFHV3XMUC5DHAST4",
                "identities": [
                    {"role": "owner", "authenticated": true},
                    {"role": "sender", "authenticated": false}
                ],
                "signers": [
                    {"key": "GCXE4HKPNGT4FOBMGUH3RGSTFLHPECGQO3RLCSPY3MHCM5NHQWMIH5E"},
                    {"key": "GBQB2BTHLUAHZWCAYXZPPQPCJ7JFQWJB5M3YAINOQJKAB7LLF7MS6FG"}
                ]
            }
        """.trimIndent()

        val jsonObject = json.decodeFromString<JsonObject>(jsonStr)
        val response = Sep30AccountResponse.fromJson(jsonObject)

        assertEquals("GDQNY3PBOBD7WLWZGZQS6WNST7VJHPKXSQFWBHQFHV3XMUC5DHAST4", response.address)
        assertEquals(2, response.identities.size)
        assertEquals("owner", response.identities[0].role)
        assertEquals(true, response.identities[0].authenticated)
        assertEquals("sender", response.identities[1].role)
        assertEquals(false, response.identities[1].authenticated)
        assertEquals(2, response.signers.size)
        assertEquals("GCXE4HKPNGT4FOBMGUH3RGSTFLHPECGQO3RLCSPY3MHCM5NHQWMIH5E", response.signers[0].key)
        assertEquals("GBQB2BTHLUAHZWCAYXZPPQPCJ7JFQWJB5M3YAINOQJKAB7LLF7MS6FG", response.signers[1].key)
    }

    // ========== Sep30AccountResponse - Authenticated Field ==========

    @Test
    fun testParseSep30AccountResponseWithAuthenticated() = runTest {
        val jsonStr = """
            {
                "address": "GDQNY3PBOBD7WLWZGZQS6WNST7VJHPKXSQFWBHQFHV3XMUC5DHAST4",
                "identities": [
                    {"role": "owner", "authenticated": true}
                ],
                "signers": [
                    {"key": "GCXE4HKPNGT4FOBMGUH3RGSTFLHPECGQO3RLCSPY3MHCM5NHQWMIH5E"}
                ]
            }
        """.trimIndent()

        val jsonObject = json.decodeFromString<JsonObject>(jsonStr)
        val response = Sep30AccountResponse.fromJson(jsonObject)

        assertEquals(true, response.identities[0].authenticated)
    }

    @Test
    fun testParseSep30AccountResponseWithoutAuthenticated() = runTest {
        val jsonStr = """
            {
                "address": "GDQNY3PBOBD7WLWZGZQS6WNST7VJHPKXSQFWBHQFHV3XMUC5DHAST4",
                "identities": [
                    {"role": "owner"}
                ],
                "signers": [
                    {"key": "GCXE4HKPNGT4FOBMGUH3RGSTFLHPECGQO3RLCSPY3MHCM5NHQWMIH5E"}
                ]
            }
        """.trimIndent()

        val jsonObject = json.decodeFromString<JsonObject>(jsonStr)
        val response = Sep30AccountResponse.fromJson(jsonObject)

        assertNull(response.identities[0].authenticated)
    }

    // ========== Sep30AccountResponse - Empty Signers ==========

    @Test
    fun testParseSep30AccountResponseEmptySigners() = runTest {
        val jsonStr = """
            {
                "address": "GDQNY3PBOBD7WLWZGZQS6WNST7VJHPKXSQFWBHQFHV3XMUC5DHAST4",
                "identities": [
                    {"role": "owner", "authenticated": true}
                ],
                "signers": []
            }
        """.trimIndent()

        val jsonObject = json.decodeFromString<JsonObject>(jsonStr)
        val response = Sep30AccountResponse.fromJson(jsonObject)

        assertEquals("GDQNY3PBOBD7WLWZGZQS6WNST7VJHPKXSQFWBHQFHV3XMUC5DHAST4", response.address)
        assertEquals(1, response.identities.size)
        assertTrue(response.signers.isEmpty())
    }

    // ========== Sep30AccountsResponse - Multiple Accounts ==========

    @Test
    fun testParseSep30AccountsResponseMultipleAccounts() = runTest {
        val jsonStr = """
            {
                "accounts": [
                    {
                        "address": "GDQNY3PBOBD7WLWZGZQS6WNST7VJHPKXSQFWBHQFHV3XMUC5DHAST4",
                        "identities": [
                            {"role": "owner", "authenticated": true}
                        ],
                        "signers": [
                            {"key": "GCXE4HKPNGT4FOBMGUH3RGSTFLHPECGQO3RLCSPY3MHCM5NHQWMIH5E"}
                        ]
                    },
                    {
                        "address": "GBQB2BTHLUAHZWCAYXZPPQPCJ7JFQWJB5M3YAINOQJKAB7LLF7MS6FG",
                        "identities": [
                            {"role": "sender", "authenticated": true},
                            {"role": "receiver"}
                        ],
                        "signers": [
                            {"key": "GCXE4HKPNGT4FOBMGUH3RGSTFLHPECGQO3RLCSPY3MHCM5NHQWMIH5E"}
                        ]
                    }
                ]
            }
        """.trimIndent()

        val jsonObject = json.decodeFromString<JsonObject>(jsonStr)
        val response = Sep30AccountsResponse.fromJson(jsonObject)

        assertEquals(2, response.accounts.size)
        assertEquals("GDQNY3PBOBD7WLWZGZQS6WNST7VJHPKXSQFWBHQFHV3XMUC5DHAST4", response.accounts[0].address)
        assertEquals(1, response.accounts[0].identities.size)
        assertEquals("GBQB2BTHLUAHZWCAYXZPPQPCJ7JFQWJB5M3YAINOQJKAB7LLF7MS6FG", response.accounts[1].address)
        assertEquals(2, response.accounts[1].identities.size)
        assertEquals("sender", response.accounts[1].identities[0].role)
        assertNull(response.accounts[1].identities[1].authenticated)
    }

    // ========== Sep30AccountsResponse - Empty Accounts ==========

    @Test
    fun testParseSep30AccountsResponseEmptyAccounts() = runTest {
        val jsonStr = """
            {
                "accounts": []
            }
        """.trimIndent()

        val jsonObject = json.decodeFromString<JsonObject>(jsonStr)
        val response = Sep30AccountsResponse.fromJson(jsonObject)

        assertTrue(response.accounts.isEmpty())
    }

    // ========== Sep30SignatureResponse ==========

    @Test
    fun testParseSep30SignatureResponse() = runTest {
        val jsonStr = """
            {
                "signature": "YpVelqPAKsYTP...",
                "network_passphrase": "Test SDF Network ; September 2015"
            }
        """.trimIndent()

        val jsonObject = json.decodeFromString<JsonObject>(jsonStr)
        val response = Sep30SignatureResponse.fromJson(jsonObject)

        assertEquals("YpVelqPAKsYTP...", response.signature)
        assertEquals("Test SDF Network ; September 2015", response.networkPassphrase)
    }

    // ========== Sep30ResponseIdentity - With Role ==========

    @Test
    fun testParseSep30ResponseIdentityWithRole() = runTest {
        val jsonStr = """
            {"role": "owner", "authenticated": true}
        """.trimIndent()

        val jsonObject = json.decodeFromString<JsonObject>(jsonStr)
        val identity = Sep30ResponseIdentity.fromJson(jsonObject)

        assertEquals("owner", identity.role)
        assertEquals(true, identity.authenticated)
    }

    // ========== Sep30ResponseIdentity - Without Role ==========

    @Test
    fun testParseSep30ResponseIdentityWithoutRole() = runTest {
        val jsonStr = """
            {"authenticated": true}
        """.trimIndent()

        val jsonObject = json.decodeFromString<JsonObject>(jsonStr)
        val identity = Sep30ResponseIdentity.fromJson(jsonObject)

        assertNull(identity.role)
        assertEquals(true, identity.authenticated)
    }

    // ========== Sep30ResponseSigner ==========

    @Test
    fun testParseSep30ResponseSigner() = runTest {
        val jsonStr = """
            {"key": "GCXE4HKPNGT4FOBMGUH3RGSTFLHPECGQO3RLCSPY3MHCM5NHQWMIH5E"}
        """.trimIndent()

        val jsonObject = json.decodeFromString<JsonObject>(jsonStr)
        val signer = Sep30ResponseSigner.fromJson(jsonObject)

        assertEquals("GCXE4HKPNGT4FOBMGUH3RGSTFLHPECGQO3RLCSPY3MHCM5NHQWMIH5E", signer.key)
    }

    // ========== Edge Cases - Unknown Fields ==========

    @Test
    fun testParseSep30AccountResponseExtraUnknownFields() = runTest {
        val jsonStr = """
            {
                "address": "GDQNY3PBOBD7WLWZGZQS6WNST7VJHPKXSQFWBHQFHV3XMUC5DHAST4",
                "identities": [
                    {"role": "owner", "authenticated": true, "extra_field": "ignored"}
                ],
                "signers": [
                    {"key": "GCXE4HKPNGT4FOBMGUH3RGSTFLHPECGQO3RLCSPY3MHCM5NHQWMIH5E", "weight": 1}
                ],
                "unknown_top_level": "should be ignored",
                "debug_info": 42
            }
        """.trimIndent()

        val jsonObject = json.decodeFromString<JsonObject>(jsonStr)
        val response = Sep30AccountResponse.fromJson(jsonObject)

        assertEquals("GDQNY3PBOBD7WLWZGZQS6WNST7VJHPKXSQFWBHQFHV3XMUC5DHAST4", response.address)
        assertEquals(1, response.identities.size)
        assertEquals("owner", response.identities[0].role)
        assertEquals(1, response.signers.size)
        assertEquals("GCXE4HKPNGT4FOBMGUH3RGSTFLHPECGQO3RLCSPY3MHCM5NHQWMIH5E", response.signers[0].key)
    }

    // ========== Error Cases - Missing Required Fields ==========

    @Test
    fun testParseSep30AccountResponseMissingAddress() = runTest {
        val jsonStr = """
            {
                "identities": [{"role": "owner"}],
                "signers": [{"key": "GCXE4HKPNGT4FOBMGUH3RGSTFLHPECGQO3RLCSPY3MHCM5NHQWMIH5E"}]
            }
        """.trimIndent()

        val jsonObject = json.decodeFromString<JsonObject>(jsonStr)

        val exception = assertFailsWith<Sep30InvalidResponseException> {
            Sep30AccountResponse.fromJson(jsonObject)
        }
        assertTrue(exception.message!!.contains("address"))
    }

    @Test
    fun testParseSep30AccountResponseMissingIdentities() = runTest {
        val jsonStr = """
            {
                "address": "GDQNY3PBOBD7WLWZGZQS6WNST7VJHPKXSQFWBHQFHV3XMUC5DHAST4",
                "signers": [{"key": "GCXE4HKPNGT4FOBMGUH3RGSTFLHPECGQO3RLCSPY3MHCM5NHQWMIH5E"}]
            }
        """.trimIndent()

        val jsonObject = json.decodeFromString<JsonObject>(jsonStr)

        val exception = assertFailsWith<Sep30InvalidResponseException> {
            Sep30AccountResponse.fromJson(jsonObject)
        }
        assertTrue(exception.message!!.contains("identities"))
    }

    @Test
    fun testParseSep30AccountResponseMissingSigners() = runTest {
        val jsonStr = """
            {
                "address": "GDQNY3PBOBD7WLWZGZQS6WNST7VJHPKXSQFWBHQFHV3XMUC5DHAST4",
                "identities": [{"role": "owner"}]
            }
        """.trimIndent()

        val jsonObject = json.decodeFromString<JsonObject>(jsonStr)

        val exception = assertFailsWith<Sep30InvalidResponseException> {
            Sep30AccountResponse.fromJson(jsonObject)
        }
        assertTrue(exception.message!!.contains("signers"))
    }

    @Test
    fun testParseSep30SignatureResponseMissingSignature() = runTest {
        val jsonStr = """
            {
                "network_passphrase": "Test SDF Network ; September 2015"
            }
        """.trimIndent()

        val jsonObject = json.decodeFromString<JsonObject>(jsonStr)

        val exception = assertFailsWith<Sep30InvalidResponseException> {
            Sep30SignatureResponse.fromJson(jsonObject)
        }
        assertTrue(exception.message!!.contains("signature"))
    }

    @Test
    fun testParseSep30SignatureResponseMissingNetworkPassphrase() = runTest {
        val jsonStr = """
            {
                "signature": "YpVelqPAKsYTP..."
            }
        """.trimIndent()

        val jsonObject = json.decodeFromString<JsonObject>(jsonStr)

        val exception = assertFailsWith<Sep30InvalidResponseException> {
            Sep30SignatureResponse.fromJson(jsonObject)
        }
        assertTrue(exception.message!!.contains("network_passphrase"))
    }

    @Test
    fun testParseSep30ResponseSignerMissingKey() = runTest {
        val jsonStr = """{}"""

        val jsonObject = json.decodeFromString<JsonObject>(jsonStr)

        val exception = assertFailsWith<Sep30InvalidResponseException> {
            Sep30ResponseSigner.fromJson(jsonObject)
        }
        assertTrue(exception.message!!.contains("key"))
    }
}
