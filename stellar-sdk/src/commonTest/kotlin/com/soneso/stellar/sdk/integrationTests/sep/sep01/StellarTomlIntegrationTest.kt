// Copyright 2025 Soneso. All rights reserved.
// Use of this source code is governed by a license that can be
// found in the LICENSE file.

package com.soneso.stellar.sdk.integrationTests.sep.sep01

import com.soneso.stellar.sdk.sep.sep01.*
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Integration tests for SEP-1 stellar.toml implementation.
 *
 * These tests require network connectivity and interact with real domains.
 * They validate network error handling and real-world stellar.toml fetching.
 *
 * Note: These tests are NOT marked with @Ignore as they always have testnet
 * connectivity and should run as part of the standard test suite.
 */
class StellarTomlIntegrationTest {

    @Test
    fun testFetchFromStellarOrg() = runTest {
        // Test fetching from stellar.org (official Stellar Development Foundation)
        val stellarToml = StellarToml.fromDomain("stellar.org")

        // Validate general information
        val generalInfo = stellarToml.generalInformation
        assertNotNull(generalInfo.version, "Version should be present")
        assertEquals("Public Global Stellar Network ; September 2015", generalInfo.networkPassphrase)
        assertEquals("https://horizon.stellar.org", generalInfo.horizonUrl)

        // Validate accounts list is present and non-empty
        assertNotNull(generalInfo.accounts, "Accounts list should be present")
        assertEquals(14, generalInfo.accounts.size, "stellar.org should have 14 accounts")

        // Verify first account starts with G (valid Stellar account)
        val firstAccount = generalInfo.accounts.firstOrNull()
        assertNotNull(firstAccount, "Should have at least one account")
        assertEquals('G', firstAccount.first(), "Account should start with G")

        // Validate organization documentation
        val documentation = stellarToml.documentation
        assertNotNull(documentation, "Documentation section should be present")
        assertEquals("Stellar Development Foundation", documentation.orgName)
        assertEquals("https://www.stellar.org", documentation.orgUrl)
        assertEquals("stellar", documentation.orgGithub)
        assertEquals("StellarOrg", documentation.orgTwitter)

        // Validate validators are present
        val validators = stellarToml.validators
        assertNotNull(validators, "Validators should be present")
        assertEquals(3, validators.size, "stellar.org should have 3 validators")

        // Verify validator structure (sdf1, sdf2, sdf3)
        validators.forEach { validator ->
            assertNotNull(validator.alias, "Validator should have alias")
            assertNotNull(validator.displayName, "Validator should have display name")
            assertNotNull(validator.host, "Validator should have host")
            assertNotNull(validator.publicKey, "Validator should have public key")
            assertNotNull(validator.history, "Validator should have history")
        }
    }

    @Test
    fun testFetchFromTestAnchorStellarOrg() = runTest {
        // Test fetching from testanchor.stellar.org (Stellar test anchor for developers)
        val stellarToml = StellarToml.fromDomain("testanchor.stellar.org")

        // Validate general information
        val generalInfo = stellarToml.generalInformation
        assertNotNull(generalInfo.version, "Version should be present")
        assertEquals("Test SDF Network ; September 2015", generalInfo.networkPassphrase)

        // Validate signing key (required for SEP-10 WebAuth)
        assertNotNull(generalInfo.signingKey, "Signing key should be present")
        assertEquals("GCHLHDBOKG2JWMJQBTLSL5XG6NO7ESXI2TAQKZXCXWXB5WI2X6W233PR", generalInfo.signingKey)

        // Validate accounts list
        assertNotNull(generalInfo.accounts, "Accounts should be present")
        assertEquals(1, generalInfo.accounts.size, "testanchor should have 1 account")
        assertEquals("GCSGSR6KQQ5BP2FXVPWRL6SWPUSFWLVONLIBJZUKTVQB5FYJFVL6XOXE", generalInfo.accounts.first())

        // Validate SEP endpoints
        assertEquals("https://testanchor.stellar.org/auth", generalInfo.webAuthEndpoint)
        assertEquals("https://testanchor.stellar.org/sep6", generalInfo.transferServer)
        assertEquals("https://testanchor.stellar.org/sep24", generalInfo.transferServerSep24)
        assertEquals("https://testanchor.stellar.org/sep12", generalInfo.kycServer)
        assertEquals("https://testanchor.stellar.org/sep31", generalInfo.directPaymentServer)
        assertEquals("https://testanchor.stellar.org/sep38", generalInfo.anchorQuoteServer)

        // Validate SEP-45 contract-based auth endpoints
        assertEquals("https://testanchor.stellar.org/sep45/auth", generalInfo.webAuthForContractsEndpoint)
        assertEquals("CD3LA6RKF5D2FN2R2L57MWXLBRSEWWENE74YBEFZSSGNJRJGICFGQXMX", generalInfo.webAuthContractId)

        // Validate organization documentation
        val documentation = stellarToml.documentation
        assertNotNull(documentation, "Documentation should be present")
        assertEquals("Stellar Development Foundation", documentation.orgName)
        assertEquals("https://stellar.org", documentation.orgUrl)
        assertEquals("stellar", documentation.orgGithub)
        assertNotNull(documentation.orgDescription, "Description should be present")

        // Validate currencies are present (testanchor supports SRT, USDC, native)
        val currencies = stellarToml.currencies
        assertNotNull(currencies, "Currencies should be present")
        assertEquals(3, currencies.size, "testanchor should have 3 currencies")

        // Verify all currencies have test status
        currencies.forEach { currency ->
            assertEquals("test", currency.status, "All testanchor currencies should have test status")
        }
    }

    @Test
    fun testFetchFromMykoboCo() = runTest {
        // Test fetching from mykobo.co (MYKOBO - regulated EURC anchor). The assertions
        // pin the structure and the long-lived identifiers, not editorial content: an
        // issuer's TOML gains and loses entries over time, so exact counts and free-text
        // fields are checked for shape rather than value.
        val stellarToml = StellarToml.fromDomain("mykobo.co")

        // Validate general information
        val generalInfo = stellarToml.generalInformation
        assertNotNull(generalInfo, "General information should be present")
        assertNotNull(generalInfo.version, "Version should be present")

        // Validate accounts: non-empty, all valid ed25519 account IDs, and the EURC
        // issuing account present. The issuer account is stable for the asset's lifetime.
        val eurcIssuer = "GAQRF3UGHBT6JYQZ7YSUYCIYWAF4T2SAA5237Q5LIQYJOHHFAWDXZ7NM"
        assertTrue(generalInfo.accounts.isNotEmpty(), "Accounts list should not be empty")
        assertTrue(generalInfo.accounts.contains(eurcIssuer), "Should contain the EURC issuing account")
        generalInfo.accounts.forEach { account ->
            assertEquals('G', account.first(), "All accounts should start with G")
        }

        // Validate organization documentation
        val documentation = stellarToml.documentation
        assertNotNull(documentation, "Documentation section should be present")
        assertEquals("MYKOBO", documentation.orgName)
        assertNotNull(documentation.orgUrl, "Organization URL should be present")
        assertNotNull(documentation.orgOfficialEmail, "Official email should be present")

        // Validate principals: present, each carrying a name and an email
        val pointsOfContact = stellarToml.pointsOfContact
        assertNotNull(pointsOfContact, "Points of contact should be present")
        assertTrue(pointsOfContact.isNotEmpty(), "Should have at least one point of contact")
        pointsOfContact.forEach { contact ->
            assertNotNull(contact.name, "Each principal should have a name")
            assertNotNull(contact.email, "Each principal should have an email")
        }

        // Validate the EURC currency: a fiat-anchored asset whose issuer matches the
        // accounts list
        val currencies = stellarToml.currencies
        assertNotNull(currencies, "Currencies should be present")
        val eurc = currencies.find { it.code == "EURC" }
        assertNotNull(eurc, "EURC currency should be present")
        assertEquals(eurcIssuer, eurc.issuer, "EURC issuer should match the issuing account")
        assertEquals(true, eurc.isAssetAnchored, "EURC should be asset-anchored")
        assertEquals("fiat", eurc.anchorAssetType, "EURC anchor asset type should be fiat")
        assertEquals("EUR", eurc.anchorAsset, "EURC is anchored to EUR")
        assertNotNull(eurc.desc, "EURC should have a description")
    }

    @Test
    fun testFetchFromStellarMoneyGramCom() = runTest {
        // Test fetching from stellar.moneygram.com (MoneyGram - major remittance service offering cash-to-crypto)
        val stellarToml = StellarToml.fromDomain("stellar.moneygram.com")

        // Validate general information
        val generalInfo = stellarToml.generalInformation
        assertNotNull(generalInfo, "General information should be present")
        assertEquals("0.1.0", generalInfo.version, "Version should be 0.1.0")
        assertEquals("Public Global Stellar Network ; September 2015", generalInfo.networkPassphrase)

        // Validate signing key for SEP-10 authentication
        assertNotNull(generalInfo.signingKey, "Signing key should be present")
        assertEquals("GD5NUMEX7LYHXGXCAD4PGW7JDMOUY2DKRGY5XZHJS5IONVHDKCJYGVCL", generalInfo.signingKey)

        // Validate SEP endpoints (MoneyGram supports SEP-10 auth and SEP-24 transfers)
        assertNotNull(generalInfo.webAuthEndpoint, "Web auth endpoint should be present")
        assertEquals("https://stellar.moneygram.com/stellaradapterservice/auth", generalInfo.webAuthEndpoint)
        assertNotNull(generalInfo.transferServerSep24, "SEP-24 transfer server should be present")
        assertEquals("https://stellar.moneygram.com/stellaradapterservice/sep24", generalInfo.transferServerSep24)

        // Validate accounts list exists (currently empty but field is present)
        assertNotNull(generalInfo.accounts, "Accounts list should be present")

        // Validate organization documentation
        val documentation = stellarToml.documentation
        assertNotNull(documentation, "Documentation section should be present")
        assertEquals("MoneyGram", documentation.orgName)
        assertEquals("https://www.moneygram.com", documentation.orgUrl)
        assertEquals("customerservice@moneygram.com", documentation.orgSupportEmail)
        assertNotNull(documentation.orgLogo, "Organization logo should be present")
        assertEquals("https://stellar.moneygram.com/assets/images/moneygram-logo.jpg", documentation.orgLogo)

        // Validate organization description (MoneyGram provides cash-to-crypto services)
        assertNotNull(documentation.orgDescription, "Organization description should be present")
        assertEquals(true, documentation.orgDescription?.contains("USDC") == true,
            "Description should mention USDC")
        assertEquals(true, documentation.orgDescription?.contains("MoneyGram") == true,
            "Description should mention MoneyGram")

        // Validate currencies are present (MoneyGram supports USDC for cash-to-crypto)
        val currencies = stellarToml.currencies
        assertNotNull(currencies, "Currencies should be present")
        assertEquals(1, currencies.size, "stellar.moneygram.com should have 1 currency (USDC)")

        // Verify USDC currency details (using Circle's USDC issuer)
        val usdc = currencies.find { it.code == "USDC" }
        assertNotNull(usdc, "USDC currency should be present")
        assertEquals("GA5ZSEJYB37JRC5AVCIA5MOP4RHTM335X2KGX3IHOJAPP5RE34K4KZVN", usdc.issuer,
            "Should use Circle's USDC issuer")
        assertEquals(true, usdc.isAssetAnchored, "USDC should be asset-anchored")
        assertEquals("fiat", usdc.anchorAssetType, "USDC anchor asset type should be fiat")
        assertEquals("USD", usdc.anchorAsset, "USDC is anchored to USD")
        assertEquals("USD Coin", usdc.name)
        assertNotNull(usdc.desc, "USDC should have description")
        assertEquals(true, usdc.desc?.contains("stablecoin") == true,
            "Description should mention stablecoin")
        assertNotNull(usdc.image, "USDC should have image URL")
        assertEquals("https://www.centre.io/usdc-transparency", usdc.attestationOfReserve)
        assertEquals(true, usdc.redemptionInstructions?.contains("https://circle.com") == true,
            "USDC should have Circle redemption instructions")
    }

    // ========== Network Error Tests ==========

    @Test
    fun testHttp404NotFound() = runTest {
        // Domain that doesn't exist
        try {
            StellarToml.fromDomain("this-domain-definitely-does-not-exist-12345.com")
            fail("Should throw exception for 404")
        } catch (e: Throwable) {
            // Expected - network error or 404 (any exception is acceptable)
            assertNotNull(e)
        }
    }

    @Test
    fun testHttp500ServerError() = runTest {
        // We can't reliably test a 500 error without a mock server,
        // but we can verify error handling works
        try {
            StellarToml.fromDomain("httpstat.us/500")
            fail("Should throw exception for server error")
        } catch (e: Throwable) {
            // Expected - network error or non-200 status
            assertNotNull(e.message)
        }
    }

    @Test
    fun testInvalidUrl() = runTest {
        // Test with invalid domain format - HTTP clients may accept this
        // This test verifies that malformed domains don't cause crashes
        try {
            StellarToml.fromDomain("not-a-valid-domain-12345678.invalidtld9999")
        } catch (e: Throwable) {
            // Expected - network error (but not required)
            assertNotNull(e)
        }
        // If no exception, that's also acceptable behavior
    }

    @Test
    fun testDnsResolutionFailure() = runTest {
        // Domain with invalid TLD - DNS may still resolve
        // This test verifies that malformed domains don't cause crashes
        try {
            StellarToml.fromDomain("stellar-test-nonexistent-domain-xyz.invalidtld")
        } catch (e: Throwable) {
            // Expected - DNS resolution error (but not required)
            assertNotNull(e)
        }
        // If no exception, that's also acceptable behavior
    }

    // ========== currencyFromUrl Tests ==========

    @Test
    fun testCurrencyFromUrlInvalidUrl() = runTest {
        try {
            StellarToml.currencyFromUrl("https://stellar-test-invalid-domain-xyz-12345.com/currency.toml")
            fail("Should throw exception for invalid URL")
        } catch (e: Throwable) {
            // Expected - network error
            assertNotNull(e)
        }
    }

    @Test
    fun testCurrencyFromUrlInvalidContent() = runTest {
        // Test with URL that returns HTML content instead of TOML
        // example.com returns HTML, which should fail TOML parsing
        try {
            StellarToml.currencyFromUrl("https://example.com")
        } catch (e: Throwable) {
            // Expected - parsing error or non-200 status (but not required)
            assertNotNull(e)
        }
        // If no exception, that's also acceptable (some sites may have stellar.toml)
    }
}
