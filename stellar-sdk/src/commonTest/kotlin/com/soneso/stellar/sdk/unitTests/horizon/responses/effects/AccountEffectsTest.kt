package com.soneso.stellar.sdk.unitTests.horizon.responses.effects

import com.soneso.stellar.sdk.horizon.responses.effects.*
import com.soneso.stellar.sdk.unitTests.horizon.responses.effects.EffectTestHelpers.LINKS_JSON
import com.soneso.stellar.sdk.unitTests.horizon.responses.effects.EffectTestHelpers.TEST_ACCOUNT
import com.soneso.stellar.sdk.unitTests.horizon.responses.effects.EffectTestHelpers.TEST_ACCOUNT_MUXED
import com.soneso.stellar.sdk.unitTests.horizon.responses.effects.EffectTestHelpers.TEST_ACCOUNT_MUXED_ID
import com.soneso.stellar.sdk.unitTests.horizon.responses.effects.EffectTestHelpers.TEST_CREATED_AT
import com.soneso.stellar.sdk.unitTests.horizon.responses.effects.EffectTestHelpers.TEST_PAGING_TOKEN
import com.soneso.stellar.sdk.unitTests.horizon.responses.effects.EffectTestHelpers.testLinks
import kotlinx.serialization.json.Json
import kotlin.test.*

class AccountEffectsTest {

    private val json = Json { ignoreUnknownKeys = true }

    // ==================== AccountCreatedEffectResponse ====================

    @Test
    fun testAccountCreatedConstruction() {
        val effect = AccountCreatedEffectResponse(
            id = "1",
            account = TEST_ACCOUNT,
            accountMuxed = null,
            accountMuxedId = null,
            type = "account_created",
            createdAt = TEST_CREATED_AT,
            pagingToken = TEST_PAGING_TOKEN,
            links = testLinks(),
            startingBalance = "10000.0000000"
        )
        assertEquals("1", effect.id)
        assertEquals(TEST_ACCOUNT, effect.account)
        assertNull(effect.accountMuxed)
        assertNull(effect.accountMuxedId)
        assertEquals("account_created", effect.type)
        assertEquals(TEST_CREATED_AT, effect.createdAt)
        assertEquals(TEST_PAGING_TOKEN, effect.pagingToken)
        assertEquals("10000.0000000", effect.startingBalance)
        assertNotNull(effect.links)
    }

    @Test
    fun testAccountCreatedJsonDeserialization() {
        val jsonStr = """
        {
            "id": "1",
            "account": "$TEST_ACCOUNT",
            "account_muxed": "$TEST_ACCOUNT_MUXED",
            "account_muxed_id": "$TEST_ACCOUNT_MUXED_ID",
            "type": "account_created",
            "created_at": "$TEST_CREATED_AT",
            "paging_token": "$TEST_PAGING_TOKEN",
            $LINKS_JSON,
            "starting_balance": "10000.0000000"
        }
        """
        val effect = json.decodeFromString<EffectResponse>(jsonStr)
        assertIs<AccountCreatedEffectResponse>(effect)
        assertEquals("1", effect.id)
        assertEquals(TEST_ACCOUNT, effect.account)
        assertEquals(TEST_ACCOUNT_MUXED, effect.accountMuxed)
        assertEquals(TEST_ACCOUNT_MUXED_ID, effect.accountMuxedId)
        assertEquals("account_created", effect.type)
        assertEquals("10000.0000000", effect.startingBalance)
    }

    @Test
    fun testAccountCreatedEquality() {
        val e1 = AccountCreatedEffectResponse("1", TEST_ACCOUNT, null, null, "account_created", TEST_CREATED_AT, TEST_PAGING_TOKEN, testLinks(), "100.0")
        val e2 = AccountCreatedEffectResponse("1", TEST_ACCOUNT, null, null, "account_created", TEST_CREATED_AT, TEST_PAGING_TOKEN, testLinks(), "100.0")
        assertEquals(e1, e2)
        assertEquals(e1.hashCode(), e2.hashCode())
    }

    // ==================== AccountRemovedEffectResponse ====================

    @Test
    fun testAccountRemovedConstruction() {
        val effect = AccountRemovedEffectResponse(
            id = "2", account = TEST_ACCOUNT, accountMuxed = null, accountMuxedId = null,
            type = "account_removed", createdAt = TEST_CREATED_AT,
            pagingToken = TEST_PAGING_TOKEN, links = testLinks()
        )
        assertEquals("2", effect.id)
        assertEquals("account_removed", effect.type)
    }

    @Test
    fun testAccountRemovedJsonDeserialization() {
        val jsonStr = """
        {
            "id": "2",
            "account": "$TEST_ACCOUNT",
            "type": "account_removed",
            "created_at": "$TEST_CREATED_AT",
            "paging_token": "$TEST_PAGING_TOKEN",
            $LINKS_JSON
        }
        """
        val effect = json.decodeFromString<EffectResponse>(jsonStr)
        assertIs<AccountRemovedEffectResponse>(effect)
        assertEquals("account_removed", effect.type)
    }

    @Test
    fun testAccountRemovedEquality() {
        val e1 = AccountRemovedEffectResponse("2", TEST_ACCOUNT, null, null, "account_removed", TEST_CREATED_AT, TEST_PAGING_TOKEN, testLinks())
        val e2 = AccountRemovedEffectResponse("2", TEST_ACCOUNT, null, null, "account_removed", TEST_CREATED_AT, TEST_PAGING_TOKEN, testLinks())
        assertEquals(e1, e2)
        assertEquals(e1.hashCode(), e2.hashCode())
    }

    // ==================== AccountCreditedEffectResponse ====================

    @Test
    fun testAccountCreditedConstruction() {
        val effect = AccountCreditedEffectResponse(
            id = "3", account = TEST_ACCOUNT, accountMuxed = null, accountMuxedId = null,
            type = "account_credited", createdAt = TEST_CREATED_AT,
            pagingToken = TEST_PAGING_TOKEN, links = testLinks(),
            amount = "100.0000000", assetType = "credit_alphanum4",
            assetCode = "USD", assetIssuer = TEST_ACCOUNT
        )
        assertEquals("100.0000000", effect.amount)
        assertEquals("credit_alphanum4", effect.assetType)
        assertEquals("USD", effect.assetCode)
        assertEquals(TEST_ACCOUNT, effect.assetIssuer)
    }

    @Test
    fun testAccountCreditedNativeAsset() {
        val effect = AccountCreditedEffectResponse(
            id = "3", account = TEST_ACCOUNT, accountMuxed = null, accountMuxedId = null,
            type = "account_credited", createdAt = TEST_CREATED_AT,
            pagingToken = TEST_PAGING_TOKEN, links = testLinks(),
            amount = "50.0", assetType = "native"
        )
        assertNull(effect.assetCode)
        assertNull(effect.assetIssuer)
    }

    @Test
    fun testAccountCreditedJsonDeserialization() {
        val jsonStr = """
        {
            "id": "3",
            "account": "$TEST_ACCOUNT",
            "type": "account_credited",
            "created_at": "$TEST_CREATED_AT",
            "paging_token": "$TEST_PAGING_TOKEN",
            $LINKS_JSON,
            "amount": "100.0000000",
            "asset_type": "credit_alphanum4",
            "asset_code": "USD",
            "asset_issuer": "$TEST_ACCOUNT"
        }
        """
        val effect = json.decodeFromString<EffectResponse>(jsonStr)
        assertIs<AccountCreditedEffectResponse>(effect)
        assertEquals("100.0000000", effect.amount)
        assertEquals("USD", effect.assetCode)
    }

    @Test
    fun testAccountCreditedEquality() {
        val e1 = AccountCreditedEffectResponse("3", TEST_ACCOUNT, null, null, "account_credited", TEST_CREATED_AT, TEST_PAGING_TOKEN, testLinks(), "100.0", "native")
        val e2 = AccountCreditedEffectResponse("3", TEST_ACCOUNT, null, null, "account_credited", TEST_CREATED_AT, TEST_PAGING_TOKEN, testLinks(), "100.0", "native")
        assertEquals(e1, e2)
        assertEquals(e1.hashCode(), e2.hashCode())
    }

    // ==================== AccountDebitedEffectResponse ====================

    @Test
    fun testAccountDebitedConstruction() {
        val effect = AccountDebitedEffectResponse(
            id = "4", account = TEST_ACCOUNT, accountMuxed = null, accountMuxedId = null,
            type = "account_debited", createdAt = TEST_CREATED_AT,
            pagingToken = TEST_PAGING_TOKEN, links = testLinks(),
            amount = "200.0000000", assetType = "credit_alphanum12",
            assetCode = "LONGASSETCODE", assetIssuer = TEST_ACCOUNT
        )
        assertEquals("200.0000000", effect.amount)
        assertEquals("credit_alphanum12", effect.assetType)
        assertEquals("LONGASSETCODE", effect.assetCode)
    }

    @Test
    fun testAccountDebitedJsonDeserialization() {
        val jsonStr = """
        {
            "id": "4",
            "account": "$TEST_ACCOUNT",
            "type": "account_debited",
            "created_at": "$TEST_CREATED_AT",
            "paging_token": "$TEST_PAGING_TOKEN",
            $LINKS_JSON,
            "amount": "200.0000000",
            "asset_type": "native"
        }
        """
        val effect = json.decodeFromString<EffectResponse>(jsonStr)
        assertIs<AccountDebitedEffectResponse>(effect)
        assertEquals("200.0000000", effect.amount)
        assertEquals("native", effect.assetType)
        assertNull(effect.assetCode)
        assertNull(effect.assetIssuer)
    }

    @Test
    fun testAccountDebitedEquality() {
        val e1 = AccountDebitedEffectResponse("4", TEST_ACCOUNT, null, null, "account_debited", TEST_CREATED_AT, TEST_PAGING_TOKEN, testLinks(), "200.0", "native")
        val e2 = AccountDebitedEffectResponse("4", TEST_ACCOUNT, null, null, "account_debited", TEST_CREATED_AT, TEST_PAGING_TOKEN, testLinks(), "200.0", "native")
        assertEquals(e1, e2)
        assertEquals(e1.hashCode(), e2.hashCode())
    }

    // ==================== AccountThresholdsUpdatedEffectResponse ====================

    @Test
    fun testAccountThresholdsUpdatedConstruction() {
        val effect = AccountThresholdsUpdatedEffectResponse(
            id = "5", account = TEST_ACCOUNT, accountMuxed = null, accountMuxedId = null,
            type = "account_thresholds_updated", createdAt = TEST_CREATED_AT,
            pagingToken = TEST_PAGING_TOKEN, links = testLinks(),
            lowThreshold = 1, medThreshold = 2, highThreshold = 3
        )
        assertEquals(1, effect.lowThreshold)
        assertEquals(2, effect.medThreshold)
        assertEquals(3, effect.highThreshold)
    }

    @Test
    fun testAccountThresholdsUpdatedJsonDeserialization() {
        val jsonStr = """
        {
            "id": "5",
            "account": "$TEST_ACCOUNT",
            "type": "account_thresholds_updated",
            "created_at": "$TEST_CREATED_AT",
            "paging_token": "$TEST_PAGING_TOKEN",
            $LINKS_JSON,
            "low_threshold": 1,
            "med_threshold": 5,
            "high_threshold": 10
        }
        """
        val effect = json.decodeFromString<EffectResponse>(jsonStr)
        assertIs<AccountThresholdsUpdatedEffectResponse>(effect)
        assertEquals(1, effect.lowThreshold)
        assertEquals(5, effect.medThreshold)
        assertEquals(10, effect.highThreshold)
    }

    @Test
    fun testAccountThresholdsUpdatedEquality() {
        val e1 = AccountThresholdsUpdatedEffectResponse("5", TEST_ACCOUNT, null, null, "account_thresholds_updated", TEST_CREATED_AT, TEST_PAGING_TOKEN, testLinks(), 1, 2, 3)
        val e2 = AccountThresholdsUpdatedEffectResponse("5", TEST_ACCOUNT, null, null, "account_thresholds_updated", TEST_CREATED_AT, TEST_PAGING_TOKEN, testLinks(), 1, 2, 3)
        assertEquals(e1, e2)
        assertEquals(e1.hashCode(), e2.hashCode())
    }

    // ==================== AccountHomeDomainUpdatedEffectResponse ====================

    @Test
    fun testAccountHomeDomainUpdatedConstruction() {
        val effect = AccountHomeDomainUpdatedEffectResponse(
            id = "6", account = TEST_ACCOUNT, accountMuxed = null, accountMuxedId = null,
            type = "account_home_domain_updated", createdAt = TEST_CREATED_AT,
            pagingToken = TEST_PAGING_TOKEN, links = testLinks(),
            homeDomain = "stellar.org"
        )
        assertEquals("stellar.org", effect.homeDomain)
    }

    @Test
    fun testAccountHomeDomainUpdatedJsonDeserialization() {
        val jsonStr = """
        {
            "id": "6",
            "account": "$TEST_ACCOUNT",
            "type": "account_home_domain_updated",
            "created_at": "$TEST_CREATED_AT",
            "paging_token": "$TEST_PAGING_TOKEN",
            $LINKS_JSON,
            "home_domain": "example.com"
        }
        """
        val effect = json.decodeFromString<EffectResponse>(jsonStr)
        assertIs<AccountHomeDomainUpdatedEffectResponse>(effect)
        assertEquals("example.com", effect.homeDomain)
    }

    @Test
    fun testAccountHomeDomainUpdatedEquality() {
        val e1 = AccountHomeDomainUpdatedEffectResponse("6", TEST_ACCOUNT, null, null, "account_home_domain_updated", TEST_CREATED_AT, TEST_PAGING_TOKEN, testLinks(), "stellar.org")
        val e2 = AccountHomeDomainUpdatedEffectResponse("6", TEST_ACCOUNT, null, null, "account_home_domain_updated", TEST_CREATED_AT, TEST_PAGING_TOKEN, testLinks(), "stellar.org")
        assertEquals(e1, e2)
        assertEquals(e1.hashCode(), e2.hashCode())
    }

    // ==================== AccountFlagsUpdatedEffectResponse ====================

    @Test
    fun testAccountFlagsUpdatedConstruction() {
        val effect = AccountFlagsUpdatedEffectResponse(
            id = "7", account = TEST_ACCOUNT, accountMuxed = null, accountMuxedId = null,
            type = "account_flags_updated", createdAt = TEST_CREATED_AT,
            pagingToken = TEST_PAGING_TOKEN, links = testLinks(),
            authRequiredFlag = true, authRevokableFlag = false
        )
        assertEquals(true, effect.authRequiredFlag)
        assertEquals(false, effect.authRevokableFlag)
    }

    @Test
    fun testAccountFlagsUpdatedNullFlags() {
        val effect = AccountFlagsUpdatedEffectResponse(
            id = "7", account = TEST_ACCOUNT, accountMuxed = null, accountMuxedId = null,
            type = "account_flags_updated", createdAt = TEST_CREATED_AT,
            pagingToken = TEST_PAGING_TOKEN, links = testLinks()
        )
        assertNull(effect.authRequiredFlag)
        assertNull(effect.authRevokableFlag)
    }

    @Test
    fun testAccountFlagsUpdatedJsonDeserialization() {
        val jsonStr = """
        {
            "id": "7",
            "account": "$TEST_ACCOUNT",
            "type": "account_flags_updated",
            "created_at": "$TEST_CREATED_AT",
            "paging_token": "$TEST_PAGING_TOKEN",
            $LINKS_JSON,
            "auth_required_flag": true,
            "auth_revokable_flag": true
        }
        """
        val effect = json.decodeFromString<EffectResponse>(jsonStr)
        assertIs<AccountFlagsUpdatedEffectResponse>(effect)
        assertEquals(true, effect.authRequiredFlag)
        assertEquals(true, effect.authRevokableFlag)
    }

    @Test
    fun testAccountFlagsUpdatedEquality() {
        val e1 = AccountFlagsUpdatedEffectResponse("7", TEST_ACCOUNT, null, null, "account_flags_updated", TEST_CREATED_AT, TEST_PAGING_TOKEN, testLinks(), true, false)
        val e2 = AccountFlagsUpdatedEffectResponse("7", TEST_ACCOUNT, null, null, "account_flags_updated", TEST_CREATED_AT, TEST_PAGING_TOKEN, testLinks(), true, false)
        assertEquals(e1, e2)
        assertEquals(e1.hashCode(), e2.hashCode())
    }

    // ==================== AccountInflationDestinationUpdatedEffectResponse ====================

    @Test
    fun testAccountInflationDestinationUpdatedConstruction() {
        val effect = AccountInflationDestinationUpdatedEffectResponse(
            id = "8", account = TEST_ACCOUNT, accountMuxed = null, accountMuxedId = null,
            type = "account_inflation_destination_updated", createdAt = TEST_CREATED_AT,
            pagingToken = TEST_PAGING_TOKEN, links = testLinks()
        )
        assertEquals("8", effect.id)
        assertEquals("account_inflation_destination_updated", effect.type)
    }

    @Test
    fun testAccountInflationDestinationUpdatedJsonDeserialization() {
        val jsonStr = """
        {
            "id": "8",
            "account": "$TEST_ACCOUNT",
            "type": "account_inflation_destination_updated",
            "created_at": "$TEST_CREATED_AT",
            "paging_token": "$TEST_PAGING_TOKEN",
            $LINKS_JSON
        }
        """
        val effect = json.decodeFromString<EffectResponse>(jsonStr)
        assertIs<AccountInflationDestinationUpdatedEffectResponse>(effect)
        assertEquals("account_inflation_destination_updated", effect.type)
    }

    @Test
    fun testAccountInflationDestinationUpdatedEquality() {
        val e1 = AccountInflationDestinationUpdatedEffectResponse("8", TEST_ACCOUNT, null, null, "account_inflation_destination_updated", TEST_CREATED_AT, TEST_PAGING_TOKEN, testLinks())
        val e2 = AccountInflationDestinationUpdatedEffectResponse("8", TEST_ACCOUNT, null, null, "account_inflation_destination_updated", TEST_CREATED_AT, TEST_PAGING_TOKEN, testLinks())
        assertEquals(e1, e2)
        assertEquals(e1.hashCode(), e2.hashCode())
    }

    // ==================== Type hierarchy tests ====================

    @Test
    fun testAccountEffectsAreEffectResponse() {
        val created = AccountCreatedEffectResponse("1", TEST_ACCOUNT, null, null, "account_created", TEST_CREATED_AT, TEST_PAGING_TOKEN, testLinks(), "100.0")
        val removed = AccountRemovedEffectResponse("2", TEST_ACCOUNT, null, null, "account_removed", TEST_CREATED_AT, TEST_PAGING_TOKEN, testLinks())
        val credited = AccountCreditedEffectResponse("3", TEST_ACCOUNT, null, null, "account_credited", TEST_CREATED_AT, TEST_PAGING_TOKEN, testLinks(), "100.0", "native")
        val debited = AccountDebitedEffectResponse("4", TEST_ACCOUNT, null, null, "account_debited", TEST_CREATED_AT, TEST_PAGING_TOKEN, testLinks(), "100.0", "native")
        val thresholds = AccountThresholdsUpdatedEffectResponse("5", TEST_ACCOUNT, null, null, "account_thresholds_updated", TEST_CREATED_AT, TEST_PAGING_TOKEN, testLinks(), 1, 2, 3)
        val homeDomain = AccountHomeDomainUpdatedEffectResponse("6", TEST_ACCOUNT, null, null, "account_home_domain_updated", TEST_CREATED_AT, TEST_PAGING_TOKEN, testLinks(), "stellar.org")
        val flags = AccountFlagsUpdatedEffectResponse("7", TEST_ACCOUNT, null, null, "account_flags_updated", TEST_CREATED_AT, TEST_PAGING_TOKEN, testLinks())
        val inflation = AccountInflationDestinationUpdatedEffectResponse("8", TEST_ACCOUNT, null, null, "account_inflation_destination_updated", TEST_CREATED_AT, TEST_PAGING_TOKEN, testLinks())

        assertIs<EffectResponse>(created)
        assertIs<EffectResponse>(removed)
        assertIs<EffectResponse>(credited)
        assertIs<EffectResponse>(debited)
        assertIs<EffectResponse>(thresholds)
        assertIs<EffectResponse>(homeDomain)
        assertIs<EffectResponse>(flags)
        assertIs<EffectResponse>(inflation)
    }
}
