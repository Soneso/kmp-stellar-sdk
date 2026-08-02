// Copyright 2025 Soneso. All rights reserved.
// Use of this source code is governed by a license that can be
// found in the LICENSE file.

package com.soneso.stellar.sdk.unitTests.sep.sep01

import com.soneso.stellar.sdk.sep.sep01.*
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.URLProtocol
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StellarTomlTest {

    @Test
    fun testFromTomlString() {
        val toml = """
            # Sample stellar.toml
            VERSION="2.0.0"

            NETWORK_PASSPHRASE="Public Global Stellar Network ; September 2015"
            FEDERATION_SERVER="https://stellarid.io/federation/"
            AUTH_SERVER="https://api.domain.com/auth"
            TRANSFER_SERVER="https://api.domain.com"
            SIGNING_KEY="GBBHQ7H4V6RRORKYLHTCAWP6MOHNORRFJSDPXDFYDGJB2LPZUFPXUEW3"
            HORIZON_URL="https://horizon.domain.com"
            ACCOUNTS=[
                "GD5DJQDDBKGAYNEAXU562HYGOOSYAEOO6AS53PZXBOZGCP5M2OPGMZV3",
                "GAENZLGHJGJRCMX5VCHOLHQXU3EMCU5XWDNU4BGGJFNLI2EL354IVBK7",
                "GAOO3LWBC4XF6VWRP5ESJ6IBHAISVJMSBTALHOQM2EZG7Q477UWA6L7U"
            ]
            DIRECT_PAYMENT_SERVER="https://test.direct-payment.com"
            ANCHOR_QUOTE_SERVER="https://test.anchor-quote.com"
            WEB_AUTH_FOR_CONTRACTS_ENDPOINT="https://api.example.com:8001/contracts/auth"
            WEB_AUTH_CONTRACT_ID="CCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCC"

            [DOCUMENTATION]
            ORG_NAME="Organization Name"
            ORG_DBA="Organization DBA"
            ORG_URL="https://www.domain.com"
            ORG_LOGO="https://www.domain.com/awesomelogo.png"
            ORG_DESCRIPTION="Description of issuer"
            ORG_PHYSICAL_ADDRESS="123 Sesame Street, New York, NY 12345, United States"
            ORG_PHYSICAL_ADDRESS_ATTESTATION="https://www.domain.com/address_attestation.jpg"
            ORG_PHONE_NUMBER="1 (123)-456-7890"
            ORG_PHONE_NUMBER_ATTESTATION="https://www.domain.com/phone_attestation.jpg"
            ORG_KEYBASE="accountname"
            ORG_TWITTER="orgtweet"
            ORG_GITHUB="orgcode"
            ORG_OFFICIAL_EMAIL="info@domain.com"
            ORG_SUPPORT_EMAIL="support@domain.com"

            [[PRINCIPALS]]
            name="Jane Jedidiah Johnson"
            email="jane@domain.com"
            keybase="crypto_jane"
            twitter="crypto_jane"
            github="crypto_jane"
            id_photo_hash="be688838ca8686e5c90689bf2ab585cef1137c999b48c70b92f67a5c34dc15697b5d11c982ed6d71be1e1e7f7b4e0733884aa97c3f7a339a8ed03577cf74be09"
            verification_photo_hash="016ba8c4cfde65af99cb5fa8b8a37e2eb73f481b3ae34991666df2e04feb6c038666ebd1ec2b6f623967756033c702dde5f423f7d47ab6ed1827ff53783731f7"

            [[CURRENCIES]]
            code="USD"
            issuer="GCZJM35NKGVK47BB4SPBDV25477PZYIYPVVG453LPYFNXLS3FGHDXOCM"
            display_decimals=2

            [[CURRENCIES]]
            code="BTC"
            issuer="GAOO3LWBC4XF6VWRP5ESJ6IBHAISVJMSBTALHOQM2EZG7Q477UWA6L7U"
            display_decimals=7
            anchor_asset_type="crypto"
            anchor_asset="BTC"
            redemption_instructions="Use SEP6 with our federation server"
            collateral_addresses=["2C1mCx3ukix1KfegAY5zgQJV7sanAciZpv"]
            collateral_address_signatures=["304502206e21798a42fae0e854281abd38bacd1aeed3ee3738d9e1446618c4571d10"]

            # asset with meta info
            [[CURRENCIES]]
            code="GOAT"
            issuer="GD5T6IPRNCKFOHQWT264YPKOZAWUMMZOLZBJ6BNQMUGPWGRLBK3U7ZNP"
            display_decimals=2
            name="goat share"
            desc="1 GOAT token entitles you to a share of revenue from Elkins Goat Farm."
            conditions="There will only ever be 10,000 GOAT tokens in existence. We will distribute the revenue share annually on Jan. 15th"
            image="https://static.thenounproject.com/png/2292360-200.png"
            fixed_number=10000

            [[CURRENCIES]]
            code="CCRT"
            issuer="GD5T6IPRNCKFOHQWT264YPKOZAWUMMZOLZBJ6BNQMUGPWGRLBK3U7ZNP"
            contract="CC4DZNN2TPLUOAIRBI3CY7TGRFFCCW6GNVVRRQ3QIIBY6TM6M2RVMBMC"
            display_decimals=2
            name="ccrt"
            desc="contract test"

            [[VALIDATORS]]
            ALIAS="domain-au"
            DISPLAY_NAME="Domain Australia"
            HOST="core-au.domain.com:11625"
            PUBLIC_KEY="GD5DJQDDBKGAYNEAXU562HYGOOSYAEOO6AS53PZXBOZGCP5M2OPGMZV3"
            HISTORY="http://history.domain.com/prd/core-live/core_live_001/"

            [[VALIDATORS]]
            ALIAS="domain-sg"
            DISPLAY_NAME="Domain Singapore"
            HOST="core-sg.domain.com:11625"
            PUBLIC_KEY="GAENZLGHJGJRCMX5VCHOLHQXU3EMCU5XWDNU4BGGJFNLI2EL354IVBK7"
            HISTORY="http://history.domain.com/prd/core-live/core_live_002/"

            [[VALIDATORS]]
            ALIAS="domain-us"
            DISPLAY_NAME="Domain United States"
            HOST="core-us.domain.com:11625"
            PUBLIC_KEY="GAOO3LWBC4XF6VWRP5ESJ6IBHAISVJMSBTALHOQM2EZG7Q477UWA6L7U"
            HISTORY="http://history.domain.com/prd/core-live/core_live_003/"
        """.trimIndent()

        val stellarToml = StellarToml.parse(toml)

        // Test GeneralInformation
        val generalInfo = stellarToml.generalInformation
        assertEquals("2.0.0", generalInfo.version)
        assertEquals("Public Global Stellar Network ; September 2015", generalInfo.networkPassphrase)
        assertEquals("https://stellarid.io/federation/", generalInfo.federationServer)
        assertEquals("https://api.domain.com/auth", generalInfo.authServer)
        assertEquals("https://api.domain.com", generalInfo.transferServer)
        assertNull(generalInfo.transferServerSep24)
        assertNull(generalInfo.kycServer)
        assertNull(generalInfo.webAuthEndpoint)
        assertEquals("GBBHQ7H4V6RRORKYLHTCAWP6MOHNORRFJSDPXDFYDGJB2LPZUFPXUEW3", generalInfo.signingKey)
        assertEquals("https://horizon.domain.com", generalInfo.horizonUrl)
        assertTrue(generalInfo.accounts.contains("GD5DJQDDBKGAYNEAXU562HYGOOSYAEOO6AS53PZXBOZGCP5M2OPGMZV3"))
        assertTrue(generalInfo.accounts.contains("GAENZLGHJGJRCMX5VCHOLHQXU3EMCU5XWDNU4BGGJFNLI2EL354IVBK7"))
        assertTrue(generalInfo.accounts.contains("GAOO3LWBC4XF6VWRP5ESJ6IBHAISVJMSBTALHOQM2EZG7Q477UWA6L7U"))
        assertNull(generalInfo.uriRequestSigningKey)
        assertEquals("https://test.direct-payment.com", generalInfo.directPaymentServer)
        assertEquals("https://test.anchor-quote.com", generalInfo.anchorQuoteServer)
        assertEquals("https://api.example.com:8001/contracts/auth", generalInfo.webAuthForContractsEndpoint)
        assertEquals("CCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCC", generalInfo.webAuthContractId)

        // Test Documentation
        val documentation = stellarToml.documentation
        assertNotNull(documentation)
        assertEquals("Organization Name", documentation.orgName)
        assertEquals("Organization DBA", documentation.orgDba)
        assertEquals("https://www.domain.com", documentation.orgUrl)
        assertEquals("https://www.domain.com/awesomelogo.png", documentation.orgLogo)
        assertEquals("Description of issuer", documentation.orgDescription)
        assertEquals("123 Sesame Street, New York, NY 12345, United States", documentation.orgPhysicalAddress)
        assertEquals("https://www.domain.com/address_attestation.jpg", documentation.orgPhysicalAddressAttestation)
        assertEquals("1 (123)-456-7890", documentation.orgPhoneNumber)
        assertEquals("https://www.domain.com/phone_attestation.jpg", documentation.orgPhoneNumberAttestation)
        assertEquals("accountname", documentation.orgKeybase)
        assertEquals("orgtweet", documentation.orgTwitter)
        assertEquals("orgcode", documentation.orgGithub)
        assertEquals("info@domain.com", documentation.orgOfficialEmail)
        assertEquals("support@domain.com", documentation.orgSupportEmail)
        assertNull(documentation.orgLicensingAuthority)
        assertNull(documentation.orgLicenseType)
        assertNull(documentation.orgLicenseNumber)

        // Test Points of Contact
        val pointsOfContact = stellarToml.pointsOfContact
        assertNotNull(pointsOfContact)
        assertEquals(1, pointsOfContact.size)
        val poc = pointsOfContact.first()
        assertEquals("Jane Jedidiah Johnson", poc.name)
        assertEquals("jane@domain.com", poc.email)
        assertEquals("crypto_jane", poc.keybase)
        assertNull(poc.telegram)
        assertEquals("crypto_jane", poc.twitter)
        assertEquals("crypto_jane", poc.github)
        assertEquals("be688838ca8686e5c90689bf2ab585cef1137c999b48c70b92f67a5c34dc15697b5d11c982ed6d71be1e1e7f7b4e0733884aa97c3f7a339a8ed03577cf74be09", poc.idPhotoHash)
        assertEquals("016ba8c4cfde65af99cb5fa8b8a37e2eb73f481b3ae34991666df2e04feb6c038666ebd1ec2b6f623967756033c702dde5f423f7d47ab6ed1827ff53783731f7", poc.verificationPhotoHash)

        // Test Currencies
        val currencies = stellarToml.currencies
        assertNotNull(currencies)
        assertEquals(4, currencies.size)

        val usd = currencies[0]
        assertEquals("USD", usd.code)
        assertEquals("GCZJM35NKGVK47BB4SPBDV25477PZYIYPVVG453LPYFNXLS3FGHDXOCM", usd.issuer)
        assertEquals(2, usd.displayDecimals)

        val btc = currencies[1]
        assertEquals("BTC", btc.code)
        assertEquals("GAOO3LWBC4XF6VWRP5ESJ6IBHAISVJMSBTALHOQM2EZG7Q477UWA6L7U", btc.issuer)
        assertEquals(7, btc.displayDecimals)
        assertEquals("crypto", btc.anchorAssetType)
        assertEquals("BTC", btc.anchorAsset)
        assertEquals("Use SEP6 with our federation server", btc.redemptionInstructions)
        assertNotNull(btc.collateralAddresses)
        assertTrue(btc.collateralAddresses!!.contains("2C1mCx3ukix1KfegAY5zgQJV7sanAciZpv"))
        assertNotNull(btc.collateralAddressSignatures)
        assertTrue(btc.collateralAddressSignatures!!.contains("304502206e21798a42fae0e854281abd38bacd1aeed3ee3738d9e1446618c4571d10"))

        val goat = currencies[2]
        assertEquals("GOAT", goat.code)
        assertEquals("GD5T6IPRNCKFOHQWT264YPKOZAWUMMZOLZBJ6BNQMUGPWGRLBK3U7ZNP", goat.issuer)
        assertEquals(2, goat.displayDecimals)
        assertEquals("goat share", goat.name)
        assertEquals("1 GOAT token entitles you to a share of revenue from Elkins Goat Farm.", goat.desc)
        assertEquals("There will only ever be 10,000 GOAT tokens in existence. We will distribute the revenue share annually on Jan. 15th", goat.conditions)
        assertEquals("https://static.thenounproject.com/png/2292360-200.png", goat.image)
        assertEquals(10000L, goat.fixedNumber)

        val ccrt = currencies[3]
        assertEquals("CCRT", ccrt.code)
        assertEquals("CC4DZNN2TPLUOAIRBI3CY7TGRFFCCW6GNVVRRQ3QIIBY6TM6M2RVMBMC", ccrt.contract)

        // Test Validators
        val validators = stellarToml.validators
        assertNotNull(validators)
        assertEquals(3, validators.size)

        val validatorAu = validators[0]
        assertEquals("domain-au", validatorAu.alias)
        assertEquals("Domain Australia", validatorAu.displayName)
        assertEquals("core-au.domain.com:11625", validatorAu.host)
        assertEquals("GD5DJQDDBKGAYNEAXU562HYGOOSYAEOO6AS53PZXBOZGCP5M2OPGMZV3", validatorAu.publicKey)
        assertEquals("http://history.domain.com/prd/core-live/core_live_001/", validatorAu.history)

        val validatorSg = validators[1]
        assertEquals("domain-sg", validatorSg.alias)
        assertEquals("Domain Singapore", validatorSg.displayName)
        assertEquals("core-sg.domain.com:11625", validatorSg.host)
        assertEquals("GAENZLGHJGJRCMX5VCHOLHQXU3EMCU5XWDNU4BGGJFNLI2EL354IVBK7", validatorSg.publicKey)
        assertEquals("http://history.domain.com/prd/core-live/core_live_002/", validatorSg.history)

        val validatorUs = validators[2]
        assertEquals("domain-us", validatorUs.alias)
        assertEquals("Domain United States", validatorUs.displayName)
        assertEquals("core-us.domain.com:11625", validatorUs.host)
        assertEquals("GAOO3LWBC4XF6VWRP5ESJ6IBHAISVJMSBTALHOQM2EZG7Q477UWA6L7U", validatorUs.publicKey)
        assertEquals("http://history.domain.com/prd/core-live/core_live_003/", validatorUs.history)
    }

    @Test
    fun testFromTomlStringWithIncorrectHeaders() {
        val toml = """
            # Sample stellar.toml with incorrect headers
            VERSION="2.0.0"

            NETWORK_PASSPHRASE="Public Global Stellar Network ; September 2015"
            FEDERATION_SERVER="https://stellarid.io/federation/"
            AUTH_SERVER="https://api.domain.com/auth"
            TRANSFER_SERVER="https://api.domain.com"
            SIGNING_KEY="GBBHQ7H4V6RRORKYLHTCAWP6MOHNORRFJSDPXDFYDGJB2LPZUFPXUEW3"
            HORIZON_URL="https://horizon.domain.com"
            ACCOUNTS=[
                "GD5DJQDDBKGAYNEAXU562HYGOOSYAEOO6AS53PZXBOZGCP5M2OPGMZV3",
                "GAENZLGHJGJRCMX5VCHOLHQXU3EMCU5XWDNU4BGGJFNLI2EL354IVBK7",
                "GAOO3LWBC4XF6VWRP5ESJ6IBHAISVJMSBTALHOQM2EZG7Q477UWA6L7U"
            ]
            DIRECT_PAYMENT_SERVER="https://test.direct-payment.com"
            ANCHOR_QUOTE_SERVER="https://test.anchor-quote.com"

            [[DOCUMENTATION]]
            ORG_NAME="Organization Name"
            ORG_DBA="Organization DBA"
            ORG_URL="https://www.domain.com"
            ORG_LOGO="https://www.domain.com/awesomelogo.png"
            ORG_DESCRIPTION="Description of issuer"
            ORG_PHYSICAL_ADDRESS="123 Sesame Street, New York, NY 12345, United States"
            ORG_PHYSICAL_ADDRESS_ATTESTATION="https://www.domain.com/address_attestation.jpg"
            ORG_PHONE_NUMBER="1 (123)-456-7890"
            ORG_PHONE_NUMBER_ATTESTATION="https://www.domain.com/phone_attestation.jpg"
            ORG_KEYBASE="accountname"
            ORG_TWITTER="orgtweet"
            ORG_GITHUB="orgcode"
            ORG_OFFICIAL_EMAIL="info@domain.com"
            ORG_SUPPORT_EMAIL="support@domain.com"

            [PRINCIPALS]
            name="Jane Jedidiah Johnson"
            email="jane@domain.com"
            keybase="crypto_jane"
            twitter="crypto_jane"
            github="crypto_jane"
            id_photo_hash="be688838ca8686e5c90689bf2ab585cef1137c999b48c70b92f67a5c34dc15697b5d11c982ed6d71be1e1e7f7b4e0733884aa97c3f7a339a8ed03577cf74be09"
            verification_photo_hash="016ba8c4cfde65af99cb5fa8b8a37e2eb73f481b3ae34991666df2e04feb6c038666ebd1ec2b6f623967756033c702dde5f423f7d47ab6ed1827ff53783731f7"

            [CURRENCIES]
            code="USD"
            issuer="GCZJM35NKGVK47BB4SPBDV25477PZYIYPVVG453LPYFNXLS3FGHDXOCM"
            display_decimals=2

            [VALIDATORS]
            ALIAS="domain-au"
            DISPLAY_NAME="Domain Australia"
            HOST="core-au.domain.com:11625"
            PUBLIC_KEY="GD5DJQDDBKGAYNEAXU562HYGOOSYAEOO6AS53PZXBOZGCP5M2OPGMZV3"
            HISTORY="http://history.domain.com/prd/core-live/core_live_001/"
        """.trimIndent()

        // This should succeed after safeguarding (correcting invalid headers)
        val stellarToml = StellarToml.parse(toml)

        // Verify that parsing still succeeded
        val generalInfo = stellarToml.generalInformation
        assertEquals("2.0.0", generalInfo.version)
        assertEquals("Public Global Stellar Network ; September 2015", generalInfo.networkPassphrase)

        // Verify documentation was parsed (after correcting [[DOCUMENTATION]] to [DOCUMENTATION])
        assertNotNull(stellarToml.documentation)
        assertEquals("Organization Name", stellarToml.documentation!!.orgName)

        // Verify principals were parsed (after correcting [PRINCIPALS] to [[PRINCIPALS]])
        assertNotNull(stellarToml.pointsOfContact)
        assertEquals(1, stellarToml.pointsOfContact!!.size)
        assertEquals("Jane Jedidiah Johnson", stellarToml.pointsOfContact!!.first().name)

        // Verify currencies were parsed (after correcting [CURRENCIES] to [[CURRENCIES]])
        assertNotNull(stellarToml.currencies)
        assertEquals(1, stellarToml.currencies!!.size)
        assertEquals("USD", stellarToml.currencies!!.first().code)

        // Verify validators were parsed (after correcting [VALIDATORS] to [[VALIDATORS]])
        assertNotNull(stellarToml.validators)
        assertEquals(1, stellarToml.validators!!.size)
        assertEquals("domain-au", stellarToml.validators!!.first().alias)
    }

    @Test
    fun testOptionalFieldsOfEverySection() {
        val toml = """
            VERSION="2.7.0"
            TRANSFER_SERVER_SEP0024="https://api.domain.com/sep24"
            KYC_SERVER="https://api.domain.com/kyc"
            WEB_AUTH_ENDPOINT="https://api.domain.com/auth"
            URI_REQUEST_SIGNING_KEY="GD5DJQDDBKGAYNEAXU562HYGOOSYAEOO6AS53PZXBOZGCP5M2OPGMZV3"

            [DOCUMENTATION]
            ORG_LICENSING_AUTHORITY="Financial Services Commission"
            ORG_LICENSE_TYPE="Money Services Business"
            ORG_LICENSE_NUMBER="MSB-123456"

            [[PRINCIPALS]]
            telegram="crypto_jane_tg"

            [[CURRENCIES]]
            toml="https://domain.com/.well-known/USDC.toml"

            [[CURRENCIES]]
            code_template="BTC?????"
            status="test"
            is_unlimited=true
            is_asset_anchored=false
            attestation_of_reserve="https://domain.com/proof-of-reserve.pdf"
            collateral_addresses=["2C1mCx3ukix1KfegAY5zgQJV7sanAciZpv"]
            collateral_address_messages=["Proof of collateral ownership"]
            max_number=500000
            regulated=true
            approval_server="https://domain.com/tx_approve"
            approval_criteria="Payments must stay below the daily limit"

            [[VALIDATORS]]
            ALIAS="alias-only"

            [[VALIDATORS]]
            HOST="core-only.domain.com:11625"
        """.trimIndent()

        val stellarToml = StellarToml.parse(toml)

        val generalInfo = stellarToml.generalInformation
        assertEquals("https://api.domain.com/sep24", generalInfo.transferServerSep24)
        assertEquals("https://api.domain.com/kyc", generalInfo.kycServer)
        assertEquals("https://api.domain.com/auth", generalInfo.webAuthEndpoint)
        assertEquals(
            "GD5DJQDDBKGAYNEAXU562HYGOOSYAEOO6AS53PZXBOZGCP5M2OPGMZV3",
            generalInfo.uriRequestSigningKey
        )
        assertTrue(generalInfo.accounts.isEmpty(), "ACCOUNTS is absent, so the list must be empty")

        val documentation = stellarToml.documentation
        assertNotNull(documentation)
        assertEquals("Financial Services Commission", documentation.orgLicensingAuthority)
        assertEquals("Money Services Business", documentation.orgLicenseType)
        assertEquals("MSB-123456", documentation.orgLicenseNumber)
        assertNull(documentation.orgName)

        val pointsOfContact = stellarToml.pointsOfContact
        assertNotNull(pointsOfContact)
        assertEquals(1, pointsOfContact.size)
        val poc = pointsOfContact.first()
        assertEquals("crypto_jane_tg", poc.telegram)
        assertNull(poc.name)
        assertNull(poc.email)
        assertNull(poc.keybase)
        assertNull(poc.twitter)
        assertNull(poc.github)
        assertNull(poc.idPhotoHash)
        assertNull(poc.verificationPhotoHash)

        val currencies = stellarToml.currencies
        assertNotNull(currencies)
        assertEquals(2, currencies.size)

        val linked = currencies[0]
        assertEquals("https://domain.com/.well-known/USDC.toml", linked.toml)
        assertNull(linked.code)
        assertNull(linked.codeTemplate)
        assertNull(linked.status)
        assertNull(linked.isUnlimited)
        assertNull(linked.isAssetAnchored)
        assertNull(linked.attestationOfReserve)
        assertNull(linked.collateralAddressMessages)

        val templated = currencies[1]
        assertNull(templated.toml)
        assertEquals("BTC?????", templated.codeTemplate)
        assertEquals("test", templated.status)
        assertEquals(true, templated.isUnlimited)
        assertEquals(false, templated.isAssetAnchored)
        assertEquals("https://domain.com/proof-of-reserve.pdf", templated.attestationOfReserve)
        assertEquals(listOf("Proof of collateral ownership"), templated.collateralAddressMessages)
        assertEquals(listOf("2C1mCx3ukix1KfegAY5zgQJV7sanAciZpv"), templated.collateralAddresses)
        assertEquals(500000L, templated.maxNumber)
        assertEquals(true, templated.regulated)
        assertEquals("https://domain.com/tx_approve", templated.approvalServer)
        assertEquals("Payments must stay below the daily limit", templated.approvalCriteria)

        val validators = stellarToml.validators
        assertNotNull(validators)
        assertEquals(2, validators.size)

        val aliasOnly = validators[0]
        assertEquals("alias-only", aliasOnly.alias)
        assertNull(aliasOnly.displayName)
        assertNull(aliasOnly.publicKey)
        assertNull(aliasOnly.host)
        assertNull(aliasOnly.history)

        val hostOnly = validators[1]
        assertNull(hostOnly.alias)
        assertEquals("core-only.domain.com:11625", hostOnly.host)
    }

    @Test
    fun testSectionsDeclaredAsPlainArraysProduceNoEntries() {
        // PRINCIPALS, CURRENCIES and VALIDATORS must be arrays of tables. When a file
        // declares them as arrays of scalars instead, the non-table entries are dropped.
        val toml = """
            VERSION="2.0.0"
            PRINCIPALS=["Jane Jedidiah Johnson"]
            CURRENCIES=["USD"]
            VALIDATORS=["domain-au"]
        """.trimIndent()

        val stellarToml = StellarToml.parse(toml)

        assertEquals("2.0.0", stellarToml.generalInformation.version)
        assertEquals(emptyList(), stellarToml.pointsOfContact)
        assertEquals(emptyList(), stellarToml.currencies)
        assertEquals(emptyList(), stellarToml.validators)
    }

    @Test
    fun testAccountsTableCorrectedToArrayOfTables() {
        // `[ACCOUNTS]` is invalid TOML for the SEP-1 ACCOUNTS list. The parser rewrites it
        // to `[[ACCOUNTS]]` so the keys below it stay scoped to that section instead of
        // leaking into the root table.
        val toml = """
            VERSION="2.0.0"

            [ACCOUNTS]
            HORIZON_URL="https://leaked.example.com"
        """.trimIndent()

        val stellarToml = StellarToml.parse(toml)

        assertEquals("2.0.0", stellarToml.generalInformation.version)
        assertNull(
            stellarToml.generalInformation.horizonUrl,
            "Keys inside the ACCOUNTS section must not be read as root-level keys"
        )
        assertTrue(stellarToml.generalInformation.accounts.isEmpty())
    }

    // ========== Empty/Malformed TOML Tests ==========

    @Test
    fun testEmptyToml() {
        val toml = ""
        val stellarToml = StellarToml.parse(toml)

        // Should parse successfully with defaults
        val generalInfo = stellarToml.generalInformation
        assertNull(generalInfo.version)
        assertNull(generalInfo.networkPassphrase)
        assertNull(stellarToml.documentation)
        assertNull(stellarToml.currencies)
        assertNull(stellarToml.validators)
    }

    @Test
    fun testOnlyComments() {
        val toml = """
            # This is a comment
            # Another comment
            # Just comments, no data
        """.trimIndent()

        val stellarToml = StellarToml.parse(toml)

        // Should parse successfully with defaults
        assertNull(stellarToml.generalInformation.version)
        assertNull(stellarToml.documentation)
    }

    @Test
    fun testUnclosedBrackets() {
        val toml = """
            ACCOUNTS=[
                "GD5DJQDDBKGAYNEAXU562HYGOOSYAEOO6AS53PZXBOZGCP5M2OPGMZV3"
        """.trimIndent()

        // Parser should handle this gracefully by treating incomplete array as partial data
        val stellarToml = StellarToml.parse(toml)
        assertNotNull(stellarToml.generalInformation)
    }

    @Test
    fun testInvalidKeyValueSyntax() {
        val toml = """
            VERSION="2.0.0"
            INVALID LINE WITHOUT EQUALS
            SIGNING_KEY="GBBHQ7H4V6RRORKYLHTCAWP6MOHNORRFJSDPXDFYDGJB2LPZUFPXUEW3"
        """.trimIndent()

        // Should skip invalid line and parse valid entries
        val stellarToml = StellarToml.parse(toml)
        assertEquals("2.0.0", stellarToml.generalInformation.version)
        assertEquals("GBBHQ7H4V6RRORKYLHTCAWP6MOHNORRFJSDPXDFYDGJB2LPZUFPXUEW3", stellarToml.generalInformation.signingKey)
    }

    @Test
    fun testMalformedArrayTables() {
        val toml = """
            VERSION="2.0.0"

            [[CURRENCIES
            code="USD"
            issuer="GCZJM35NKGVK47BB4SPBDV25477PZYIYPVVG453LPYFNXLS3FGHDXOCM"
        """.trimIndent()

        // Should handle malformed array table declaration
        val stellarToml = StellarToml.parse(toml)
        assertEquals("2.0.0", stellarToml.generalInformation.version)
    }

    @Test
    fun testMixedTableAndArrayTable() {
        val toml = """
            VERSION="2.0.0"

            [CURRENCIES]
            code="USD"

            [[CURRENCIES]]
            code="EUR"
        """.trimIndent()

        // Should handle mixed table/array table gracefully (second declaration wins)
        val stellarToml = StellarToml.parse(toml)
        assertNotNull(stellarToml.currencies)
    }

    @Test
    fun testDuplicateKeys() {
        val toml = """
            VERSION="1.0.0"
            VERSION="2.0.0"
            SIGNING_KEY="FIRST_KEY"
            SIGNING_KEY="SECOND_KEY"
        """.trimIndent()

        // Should use last value for duplicate keys
        val stellarToml = StellarToml.parse(toml)
        assertEquals("2.0.0", stellarToml.generalInformation.version)
        assertEquals("SECOND_KEY", stellarToml.generalInformation.signingKey)
    }

    // ========== Data Validation Tests ==========

    @Test
    fun testInvalidStellarAccountIds() {
        val toml = """
            VERSION="2.0.0"
            SIGNING_KEY="INVALID_KEY_TOO_SHORT"
            ACCOUNTS=[
                "ALSO_INVALID",
                "GD5DJQDDBKGAYNEAXU562HYGOOSYAEOO6AS53PZXBOZGCP5M2OPGMZV3"
            ]
        """.trimIndent()

        // Parser should accept any string (validation happens at usage time)
        val stellarToml = StellarToml.parse(toml)
        assertEquals("INVALID_KEY_TOO_SHORT", stellarToml.generalInformation.signingKey)
        assertTrue(stellarToml.generalInformation.accounts.contains("ALSO_INVALID"))
    }

    @Test
    fun testInvalidUrlsInServiceEndpoints() {
        val toml = """
            VERSION="2.0.0"
            WEB_AUTH_ENDPOINT="not-a-valid-url"
            TRANSFER_SERVER="also invalid"
            HORIZON_URL="https://valid.example.com"
        """.trimIndent()

        // Parser should accept any string (URL validation happens at usage time)
        val stellarToml = StellarToml.parse(toml)
        assertEquals("not-a-valid-url", stellarToml.generalInformation.webAuthEndpoint)
        assertEquals("also invalid", stellarToml.generalInformation.transferServer)
        assertEquals("https://valid.example.com", stellarToml.generalInformation.horizonUrl)
    }

    @Test
    fun testInvalidContractAddresses() {
        val toml = """
            [[CURRENCIES]]
            code="TOKEN"
            contract="INVALID_CONTRACT_ID"
        """.trimIndent()

        // Parser should accept any string (validation happens at usage time)
        val stellarToml = StellarToml.parse(toml)
        assertNotNull(stellarToml.currencies)
        assertEquals("INVALID_CONTRACT_ID", stellarToml.currencies!!.first().contract)
    }

    @Test
    fun testCurrencyWithoutCode() {
        val toml = """
            [[CURRENCIES]]
            issuer="GCZJM35NKGVK47BB4SPBDV25477PZYIYPVVG453LPYFNXLS3FGHDXOCM"
            display_decimals=2
        """.trimIndent()

        // Should parse successfully with null code
        val stellarToml = StellarToml.parse(toml)
        assertNotNull(stellarToml.currencies)
        val currency = stellarToml.currencies!!.first()
        assertNull(currency.code)
        assertEquals("GCZJM35NKGVK47BB4SPBDV25477PZYIYPVVG453LPYFNXLS3FGHDXOCM", currency.issuer)
    }

    @Test
    fun testCurrencyWithoutIssuer() {
        val toml = """
            [[CURRENCIES]]
            code="USD"
            display_decimals=2
        """.trimIndent()

        // Should parse successfully with null issuer
        val stellarToml = StellarToml.parse(toml)
        assertNotNull(stellarToml.currencies)
        val currency = stellarToml.currencies!!.first()
        assertEquals("USD", currency.code)
        assertNull(currency.issuer)
    }

    @Test
    fun testInvalidEmailFormats() {
        val toml = """
            [DOCUMENTATION]
            ORG_OFFICIAL_EMAIL="not-an-email"
            ORG_SUPPORT_EMAIL="also invalid"
        """.trimIndent()

        // Parser should accept any string (validation happens at usage time)
        val stellarToml = StellarToml.parse(toml)
        assertNotNull(stellarToml.documentation)
        assertEquals("not-an-email", stellarToml.documentation!!.orgOfficialEmail)
        assertEquals("also invalid", stellarToml.documentation!!.orgSupportEmail)
    }

    @Test
    fun testInvalidAssetCodes() {
        val toml = """
            [[CURRENCIES]]
            code="THIS_IS_TOO_LONG_FOR_STELLAR"
            issuer="GCZJM35NKGVK47BB4SPBDV25477PZYIYPVVG453LPYFNXLS3FGHDXOCM"

            [[CURRENCIES]]
            code=""
            issuer="GAOO3LWBC4XF6VWRP5ESJ6IBHAISVJMSBTALHOQM2EZG7Q477UWA6L7U"
        """.trimIndent()

        // Parser should accept any string (validation happens at usage time)
        val stellarToml = StellarToml.parse(toml)
        assertNotNull(stellarToml.currencies)
        assertEquals(2, stellarToml.currencies!!.size)
        assertEquals("THIS_IS_TOO_LONG_FOR_STELLAR", stellarToml.currencies!![0].code)
        assertEquals("", stellarToml.currencies!![1].code)
    }

    @Test
    fun testOutOfRangeNumericValues() {
        val toml = """
            [[CURRENCIES]]
            code="TEST"
            display_decimals=999
            fixed_number=999999999999999999
            max_number=-1
        """.trimIndent()

        // Parser should accept any numeric value
        val stellarToml = StellarToml.parse(toml)
        assertNotNull(stellarToml.currencies)
        val currency = stellarToml.currencies!!.first()
        assertEquals(999, currency.displayDecimals)
        assertEquals(999999999999999999L, currency.fixedNumber)
        assertEquals(-1L, currency.maxNumber)
    }

    // ========== TOML Parser Edge Cases ==========

    @Test
    fun testWindowsLineEndings() {
        val toml = "VERSION=\"2.0.0\"\r\nSIGNING_KEY=\"GBBHQ7H4V6RRORKYLHTCAWP6MOHNORRFJSDPXDFYDGJB2LPZUFPXUEW3\"\r\n"

        val stellarToml = StellarToml.parse(toml)
        assertEquals("2.0.0", stellarToml.generalInformation.version)
        assertEquals("GBBHQ7H4V6RRORKYLHTCAWP6MOHNORRFJSDPXDFYDGJB2LPZUFPXUEW3", stellarToml.generalInformation.signingKey)
    }

    @Test
    fun testUnicodeCharactersInValues() {
        val toml = """
            [DOCUMENTATION]
            ORG_NAME="企業名"
            ORG_DESCRIPTION="Société financière"
            ORG_PHYSICAL_ADDRESS="123 Straße, München, Deutschland"
        """.trimIndent()

        val stellarToml = StellarToml.parse(toml)
        assertNotNull(stellarToml.documentation)
        assertEquals("企業名", stellarToml.documentation!!.orgName)
        assertEquals("Société financière", stellarToml.documentation!!.orgDescription)
        assertEquals("123 Straße, München, Deutschland", stellarToml.documentation!!.orgPhysicalAddress)
    }

    @Test
    fun testEscapeSequencesInStrings() {
        val toml = """
            [DOCUMENTATION]
            ORG_NAME="Organization \"Quoted\" Name"
            ORG_DESCRIPTION="Line with\nnewline"
        """.trimIndent()

        // Basic TOML parser may not handle all escape sequences
        val stellarToml = StellarToml.parse(toml)
        assertNotNull(stellarToml.documentation)
        // Parser stores the literal string as-is
        assertNotNull(stellarToml.documentation!!.orgName)
    }

    @Test
    fun testVeryLongValues() {
        val longString = "A".repeat(10000)
        val toml = """
            VERSION="2.0.0"
            [DOCUMENTATION]
            ORG_DESCRIPTION="$longString"
        """.trimIndent()

        val stellarToml = StellarToml.parse(toml)
        assertNotNull(stellarToml.documentation)
        assertEquals(longString, stellarToml.documentation!!.orgDescription)
    }

    @Test
    fun testBoundaryNumericValues() {
        val toml = """
            [[CURRENCIES]]
            code="TEST"
            display_decimals=0
            fixed_number=1
            max_number=9223372036854775807
        """.trimIndent()

        val stellarToml = StellarToml.parse(toml)
        assertNotNull(stellarToml.currencies)
        val currency = stellarToml.currencies!!.first()
        assertEquals(0, currency.displayDecimals)
        assertEquals(1L, currency.fixedNumber)
        assertEquals(9223372036854775807L, currency.maxNumber)
    }

    @Test
    fun testMixedWhitespace() {
        val toml = "VERSION=\"2.0.0\"\n\t  SIGNING_KEY=\"GBBHQ7H4V6RRORKYLHTCAWP6MOHNORRFJSDPXDFYDGJB2LPZUFPXUEW3\"  \n"

        val stellarToml = StellarToml.parse(toml)
        assertEquals("2.0.0", stellarToml.generalInformation.version)
        assertEquals("GBBHQ7H4V6RRORKYLHTCAWP6MOHNORRFJSDPXDFYDGJB2LPZUFPXUEW3", stellarToml.generalInformation.signingKey)
    }

    // ==================== fromDomain (loopback carve-out) ====================

    private val minimalToml = "VERSION=\"2.0.0\"\n"

    /** Captures the protocol+host+port of the request the mock receives, so tests can
     *  assert which scheme [StellarToml.fromDomain] actually selected. */
    private fun captureTomlFetchUrl(): Pair<HttpClient, () -> URLProtocol?> {
        var captured: URLProtocol? = null
        val engine = MockEngine { request ->
            captured = request.url.protocol
            respond(
                content = minimalToml,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "text/plain"),
            )
        }
        return HttpClient(engine) to { captured }
    }

    @Test
    fun fromDomain_productionDomain_usesHttps() = runTest {
        val (client, captured) = captureTomlFetchUrl()
        val toml = StellarToml.fromDomain("anchor.example.org", client)
        assertEquals("2.0.0", toml.generalInformation.version)
        assertEquals(URLProtocol.HTTPS, captured())
    }

    @Test
    fun fromDomain_loopbackLocalhost_usesHttp() = runTest {
        val (client, captured) = captureTomlFetchUrl()
        val toml = StellarToml.fromDomain("localhost:8080", client)
        assertEquals("2.0.0", toml.generalInformation.version)
        assertEquals(URLProtocol.HTTP, captured())
    }

    @Test
    fun fromDomain_loopbackIpv4_usesHttp() = runTest {
        val (client, captured) = captureTomlFetchUrl()
        StellarToml.fromDomain("127.0.0.1:8080", client)
        assertEquals(URLProtocol.HTTP, captured())
    }

    @Test
    fun fromDomain_loopbackIpv6_usesHttp() = runTest {
        val (client, captured) = captureTomlFetchUrl()
        StellarToml.fromDomain("[::1]:8080", client)
        assertEquals(URLProtocol.HTTP, captured())
    }

    @Test
    fun fromDomain_localhostNoPort_usesHttp() = runTest {
        val (client, captured) = captureTomlFetchUrl()
        StellarToml.fromDomain("localhost", client)
        assertEquals(URLProtocol.HTTP, captured())
    }

    @Test
    fun fromDomain_uppercaseLocalhost_usesHttp() = runTest {
        // Host comparison must be case-insensitive.
        val (client, captured) = captureTomlFetchUrl()
        StellarToml.fromDomain("LOCALHOST:8080", client)
        assertEquals(URLProtocol.HTTP, captured())
    }

    @Test
    fun fromDomain_localhostLookalike_usesHttps() = runTest {
        // "localhost.evil.com" must NOT match the carve-out — falls through to https.
        val (client, captured) = captureTomlFetchUrl()
        StellarToml.fromDomain("localhost.evil.com", client)
        assertEquals(URLProtocol.HTTPS, captured())
    }

    @Test
    fun fromDomain_loopbackIpv4Lookalike_usesHttps() = runTest {
        // "127.0.0.1.evil.com" must NOT match — falls through to https.
        val (client, captured) = captureTomlFetchUrl()
        StellarToml.fromDomain("127.0.0.1.evil.com", client)
        assertEquals(URLProtocol.HTTPS, captured())
    }

    // ==================== currencyFromUrl ====================

    private val externalCurrencyToml = """
        code="USDC"
        issuer="GCZJM35NKGVK47BB4SPBDV25477PZYIYPVVG453LPYFNXLS3FGHDXOCM"
        display_decimals=2
        name="US Dollar Coin"
        desc="Fully reserved US dollar stablecoin"
        is_asset_anchored=true
        anchor_asset_type="fiat"
        anchor_asset="USD"
        redemption_instructions="Redeem via SEP-6"
        collateral_addresses=["2C1mCx3ukix1KfegAY5zgQJV7sanAciZpv"]
        regulated=false
    """.trimIndent()

    private fun currencyTomlClient(
        body: String = externalCurrencyToml,
        status: HttpStatusCode = HttpStatusCode.OK,
        onRequest: ((HttpRequestData) -> Unit)? = null,
    ): HttpClient {
        val engine = MockEngine { request ->
            onRequest?.invoke(request)
            respond(
                content = body,
                status = status,
                headers = headersOf(HttpHeaders.ContentType, "text/plain"),
            )
        }
        return HttpClient(engine)
    }

    @Test
    fun currencyFromUrl_parsesExternalCurrencyFile() = runTest {
        var requestedUrl: String? = null
        val client = currencyTomlClient(onRequest = { requestedUrl = it.url.toString() })

        val currency = StellarToml.currencyFromUrl(
            "https://domain.com/.well-known/USDC.toml",
            client,
        )

        assertEquals("https://domain.com/.well-known/USDC.toml", requestedUrl)
        assertEquals("USDC", currency.code)
        assertEquals("GCZJM35NKGVK47BB4SPBDV25477PZYIYPVVG453LPYFNXLS3FGHDXOCM", currency.issuer)
        assertEquals(2, currency.displayDecimals)
        assertEquals("US Dollar Coin", currency.name)
        assertEquals("Fully reserved US dollar stablecoin", currency.desc)
        assertEquals(true, currency.isAssetAnchored)
        assertEquals("fiat", currency.anchorAssetType)
        assertEquals("USD", currency.anchorAsset)
        assertEquals("Redeem via SEP-6", currency.redemptionInstructions)
        assertEquals(listOf("2C1mCx3ukix1KfegAY5zgQJV7sanAciZpv"), currency.collateralAddresses)
        assertEquals(false, currency.regulated)
        assertNull(currency.toml)
    }

    @Test
    fun currencyFromUrl_forwardsCustomHeaders() = runTest {
        var userAgent: String? = null
        var apiKey: String? = null
        val client = currencyTomlClient(onRequest = {
            userAgent = it.headers["X-User-Agent"]
            apiKey = it.headers["X-Api-Key"]
        })

        StellarToml.currencyFromUrl(
            toml = "https://domain.com/.well-known/USDC.toml",
            httpClient = client,
            httpRequestHeaders = mapOf("X-User-Agent" to "MyWallet/1.0", "X-Api-Key" to "abc123"),
        )

        assertEquals("MyWallet/1.0", userAgent)
        assertEquals("abc123", apiKey)
    }

    @Test
    fun currencyFromUrl_nonOkStatus_throwsIllegalStateException() = runTest {
        val client = currencyTomlClient(body = "Not Found", status = HttpStatusCode.NotFound)

        val exception = assertFailsWith<IllegalStateException> {
            StellarToml.currencyFromUrl("https://domain.com/.well-known/USDC.toml", client)
        }
        assertEquals("Currency toml not found, response status code 404", exception.message)
    }

    // ==================== TOML value parsing ====================

    @Test
    fun testEmptyArrayValue() {
        val stellarToml = StellarToml.parse("ACCOUNTS=[]")
        assertEquals(emptyList(), stellarToml.generalInformation.accounts)
    }

    @Test
    fun testNestedArrayValue() {
        // Nested arrays parse into nested lists, so only the top-level string entries
        // remain after the ACCOUNTS list is filtered to strings.
        val stellarToml = StellarToml.parse(
            "ACCOUNTS=[[\"GD5DJQDDBKGAYNEAXU562HYGOOSYAEOO6AS53PZXBOZGCP5M2OPGMZV3\"]," +
                "\"GAENZLGHJGJRCMX5VCHOLHQXU3EMCU5XWDNU4BGGJFNLI2EL354IVBK7\"]"
        )
        assertEquals(
            listOf("GAENZLGHJGJRCMX5VCHOLHQXU3EMCU5XWDNU4BGGJFNLI2EL354IVBK7"),
            stellarToml.generalInformation.accounts
        )
    }

    @Test
    fun testArraySeparatorsInsideQuotedElements() {
        // Commas and brackets inside a quoted element are literal content, not separators.
        val stellarToml = StellarToml.parse("""ACCOUNTS=["one,two","three[four]five"]""")
        assertEquals(listOf("one,two", "three[four]five"), stellarToml.generalInformation.accounts)
    }

    @Test
    fun testArrayWithEmptyElements() {
        // Consecutive and trailing commas produce empty elements, which are dropped.
        val stellarToml = StellarToml.parse("""ACCOUNTS=["one",,"two",]""")
        assertEquals(listOf("one", "two"), stellarToml.generalInformation.accounts)
    }

    @Test
    fun testFloatValueForIntegerField() {
        val toml = """
            [[CURRENCIES]]
            code="TEST"
            display_decimals=2.75
            fixed_number=42.9
        """.trimIndent()

        val stellarToml = StellarToml.parse(toml)
        val currency = stellarToml.currencies!!.first()
        assertEquals(2, currency.displayDecimals)
        assertEquals(42L, currency.fixedNumber)
    }

    @Test
    fun testUnterminatedQuotedValue() {
        // A value that opens a quote but never closes it is not a string literal, so the
        // raw text (including the leading quote) is kept.
        val stellarToml = StellarToml.parse("VERSION=\"2.0.0")
        assertEquals("\"2.0.0", stellarToml.generalInformation.version)
    }
}
