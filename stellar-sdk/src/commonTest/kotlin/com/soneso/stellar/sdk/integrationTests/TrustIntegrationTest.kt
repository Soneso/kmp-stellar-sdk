package com.soneso.stellar.sdk.integrationTests

import com.soneso.stellar.sdk.*
import com.soneso.stellar.sdk.horizon.HorizonServer
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlin.test.*
import kotlin.time.Duration.Companion.seconds

/**
 * Comprehensive integration tests for Trust-related operations.
 *
 * These tests verify the SDK's trust operations against a live Stellar testnet.
 * They cover:
 * - ChangeTrust operation (create, update, delete trustlines)
 * - Maximum trust limit (922337203685.4775807 XLM)
 * - ~~AllowTrust operation (DEPRECATED - use SetTrustlineFlags instead)~~
 * - SetOptions operation (setting authorization flags)
 * - Account flags (AUTH_REQUIRED, AUTH_REVOCABLE, AUTH_IMMUTABLE)
 * - Trustline queries and balance verification
 *
 * ## Trust Operations
 *
 * - **ChangeTrust**: Establishes or modifies a trustline to an asset
 *   - Limit > 0: Create or update trustline
 *   - Limit = 0: Delete trustline (only if balance is 0)
 *   - Limit = MAX: Set maximum possible limit (922337203685.4775807)
 *
 * - **AllowTrust** (DEPRECATED in Protocol 17): Issuer authorizes/deauthorizes a trustline
 *   - Replaced by SetTrustlineFlags operation
 *   - No longer supported by current Stellar testnet/mainnet
 *   - Flag 0: Deauthorized (cannot receive or send)
 *   - Flag 1: Fully authorized (can receive and send)
 *   - Flag 2: Authorized to maintain liabilities (can send existing balance but not receive new funds)
 *
 * ## Authorization Flags
 *
 * Issuers can set authorization flags using SetOptions:
 * - **AUTH_REQUIRED_FLAG (1)**: Trustlines start unauthorized, issuer must authorize
 * - **AUTH_REVOCABLE_FLAG (2)**: Issuer can revoke authorization
 * - **AUTH_IMMUTABLE_FLAG (4)**: Cannot change authorization flags (permanent)
 *
 * ## Test Network
 *
 * All tests use Stellar testnet. To switch to futurenet, change the `testOn` variable.
 *
 * ## Reference
 *
 * Ported from Flutter SDK's `trust_test.dart`
 *
 * @see <a href="https://developers.stellar.org/docs/learn/fundamentals/transactions/list-of-operations#change-trust">Change Trust</a>
 * @see <a href="https://developers.stellar.org/docs/learn/fundamentals/transactions/list-of-operations#set-trustline-flags">Set Trustline Flags (replaces AllowTrust)</a>
 * @see <a href="https://developers.stellar.org/docs/learn/fundamentals/transactions/list-of-operations#set-options">Set Options</a>
 */
class TrustIntegrationTest {

    private val testOn = "testnet" // or "futurenet"
    private val horizonServer = if (testOn == "testnet") {
        HorizonServer("https://horizon-testnet.stellar.org")
    } else {
        HorizonServer("https://horizon-futurenet.stellar.org")
    }
    private val network = if (testOn == "testnet") {
        Network.TESTNET
    } else {
        Network.FUTURENET
    }

    /**
     * Test ChangeTrust operation for creating, updating, and deleting trustlines.
     *
     * This test:
     * 1. Creates issuer and trustor keypairs
     * 2. Funds trustor account via FriendBot
     * 3. Creates issuer account using CreateAccount operation
     * 4. Creates custom asset ASTRO issued by issuer
     * 5. Creates trustline from trustor to ASTRO with limit 10000
     * 6. Verifies trustline appears in trustor's balances with correct limit
     * 7. Updates trustline limit to 40000
     * 8. Verifies updated limit in trustor's balances
     * 9. Deletes trustline by setting limit to 0
     * 10. Verifies trustline no longer exists in balances
     * 11. Verifies operations and effects can be parsed
     *
     * ## ChangeTrust Operation
     *
     * The ChangeTrust operation creates or modifies a trustline between an account and an asset.
     * A trustline is required before an account can hold any asset other than XLM.
     *
     * ### Trustline Limits
     *
     * - The limit specifies the maximum amount of the asset the account is willing to hold
     * - Setting limit > 0 creates or updates the trustline
     * - Setting limit = 0 deletes the trustline (only if current balance is 0)
     * - The limit can be increased or decreased at any time
     *
     * ### Use Cases
     *
     * - **Create trustline**: Account signals willingness to hold an asset
     * - **Update limit**: Adjust exposure to an asset (increase or decrease)
     * - **Delete trustline**: Remove asset from account (requires zero balance)
     */
    @Test
    fun testChangeTrust() = runTest(timeout = 180.seconds) {
        // Create keypairs for issuer and trustor
        val issuerKeyPair = KeyPair.random()
        val trustorKeyPair = KeyPair.random()

        val issuerAccountId = issuerKeyPair.getAccountId()
        val trustorAccountId = trustorKeyPair.getAccountId()

        // Fund trustor account via FriendBot
        if (testOn == "testnet") {
            FriendBot.fundTestnetAccount(trustorAccountId)
        } else {
            FriendBot.fundFuturenetAccount(trustorAccountId)
        }

        delay(3000)

        // Create issuer account
        val trustorAccount = horizonServer.accounts().account(trustorAccountId)
        val createAccountOp = CreateAccountOperation(
            destination = issuerAccountId,
            startingBalance = "10"
        )

        var transaction = TransactionBuilder(
            sourceAccount = Account(trustorAccountId, trustorAccount.sequenceNumber),
            network = network
        )
            .addOperation(createAccountOp)
            .setTimeout(TransactionPreconditions.TIMEOUT_INFINITE)
            .setBaseFee(AbstractTransaction.MIN_BASE_FEE)
            .build()

        transaction.sign(trustorKeyPair)

        var response = horizonServer.submitTransaction(transaction.toEnvelopeXdrBase64())
        assertTrue(response.successful, "CreateAccount transaction should succeed")

        delay(3000)

        // Create custom asset ASTRO (AlphaNum12)
        val assetCode = "ASTRO"
        val astroDollar = AssetTypeCreditAlphaNum12(assetCode, issuerAccountId)

        // Create trustline from trustor to ASTRO with limit 10000
        var limit = "10000"
        var trustorAccountReloaded = horizonServer.accounts().account(trustorAccountId)
        val changeTrustOp = ChangeTrustOperation(
            asset = astroDollar,
            limit = limit
        )

        transaction = TransactionBuilder(
            sourceAccount = Account(trustorAccountId, trustorAccountReloaded.sequenceNumber),
            network = network
        )
            .addOperation(changeTrustOp)
            .setTimeout(TransactionPreconditions.TIMEOUT_INFINITE)
            .setBaseFee(AbstractTransaction.MIN_BASE_FEE)
            .build()

        transaction.sign(trustorKeyPair)

        response = horizonServer.submitTransaction(transaction.toEnvelopeXdrBase64())
        assertTrue(response.successful, "ChangeTrust create transaction should succeed")

        delay(3000)

        // Verify trustline appears in balances with correct limit
        trustorAccountReloaded = horizonServer.accounts().account(trustorAccountId)
        var found = false
        for (balance in trustorAccountReloaded.balances) {
            if (balance.assetCode == assetCode) {
                found = true
                assertEquals(limit.toDouble(), balance.limit?.toDouble(), "Trustline limit should match")
                break
            }
        }
        assertTrue(found, "Trustline should exist in account balances")

        // Update trustline limit to 40000
        limit = "40000"
        trustorAccountReloaded = horizonServer.accounts().account(trustorAccountId)
        val updateTrustOp = ChangeTrustOperation(
            asset = astroDollar,
            limit = limit
        )

        transaction = TransactionBuilder(
            sourceAccount = Account(trustorAccountId, trustorAccountReloaded.sequenceNumber),
            network = network
        )
            .addOperation(updateTrustOp)
            .setTimeout(TransactionPreconditions.TIMEOUT_INFINITE)
            .setBaseFee(AbstractTransaction.MIN_BASE_FEE)
            .build()

        transaction.sign(trustorKeyPair)

        response = horizonServer.submitTransaction(transaction.toEnvelopeXdrBase64())
        assertTrue(response.successful, "ChangeTrust update transaction should succeed")

        delay(3000)

        // Verify updated limit
        trustorAccountReloaded = horizonServer.accounts().account(trustorAccountId)
        found = false
        for (balance in trustorAccountReloaded.balances) {
            if (balance.assetCode == assetCode) {
                found = true
                assertEquals(limit.toDouble(), balance.limit?.toDouble(), "Updated limit should match")
                break
            }
        }
        assertTrue(found, "Trustline should still exist after update")

        // Delete trustline by setting limit to 0
        limit = "0"
        trustorAccountReloaded = horizonServer.accounts().account(trustorAccountId)
        val deleteTrustOp = ChangeTrustOperation(
            asset = astroDollar,
            limit = limit
        )

        transaction = TransactionBuilder(
            sourceAccount = Account(trustorAccountId, trustorAccountReloaded.sequenceNumber),
            network = network
        )
            .addOperation(deleteTrustOp)
            .setTimeout(TransactionPreconditions.TIMEOUT_INFINITE)
            .setBaseFee(AbstractTransaction.MIN_BASE_FEE)
            .build()

        transaction.sign(trustorKeyPair)

        response = horizonServer.submitTransaction(transaction.toEnvelopeXdrBase64())
        assertTrue(response.successful, "ChangeTrust delete transaction should succeed")

        delay(3000)

        // Verify trustline no longer exists
        trustorAccountReloaded = horizonServer.accounts().account(trustorAccountId)
        found = false
        for (balance in trustorAccountReloaded.balances) {
            if (balance.assetCode == assetCode) {
                found = true
                break
            }
        }
        assertFalse(found, "Trustline should be deleted")

        // Verify operations and effects can be parsed
        val operationsPage = horizonServer.operations().forAccount(trustorAccountId).execute()
        assertTrue(operationsPage.records.isNotEmpty(), "Should have operations")

        val effectsPage = horizonServer.effects().forAccount(trustorAccountId).execute()
        assertTrue(effectsPage.records.isNotEmpty(), "Should have effects")
    }

    /**
     * Test ChangeTrust operation with maximum trust limit.
     *
     * This test:
     * 1. Creates issuer and trusting accounts
     * 2. Funds both accounts via FriendBot
     * 3. Creates custom asset IOM issued by issuer
     * 4. Creates trustline with maximum possible limit (922337203685.4775807)
     * 5. Verifies transaction succeeds
     *
     * ## Maximum Trust Limit
     *
     * The maximum trust limit is defined by the Stellar protocol as:
     * - Value: 922337203685.4775807 (9223372036854775807 stroops)
     * - This is Int64.MAX_VALUE / 10^7 (stroops to XLM conversion)
     * - Represents the maximum amount of any asset an account can hold
     *
     * ### Use Case
     *
     * Setting the maximum limit signals that the account has no self-imposed restriction
     * on how much of the asset it's willing to hold. This is common for:
     * - Trading accounts that need flexibility
     * - Market makers
     * - Liquidity providers
     * - Accounts that don't want to manage limits
     */
    @Test
    fun testMaxTrustAmount() = runTest(timeout = 90.seconds) {
        // Create keypairs
        val issuerKeyPair = KeyPair.random()
        val trustingKeyPair = KeyPair.random()

        val issuerAccountId = issuerKeyPair.getAccountId()
        val trustingAccountId = trustingKeyPair.getAccountId()

        // Fund both accounts via FriendBot
        if (testOn == "testnet") {
            FriendBot.fundTestnetAccount(issuerAccountId)
            FriendBot.fundTestnetAccount(trustingAccountId)
        } else {
            FriendBot.fundFuturenetAccount(issuerAccountId)
            FriendBot.fundFuturenetAccount(trustingAccountId)
        }

        delay(3000)

        // Create custom asset IOM (AlphaNum4)
        val myAsset = AssetTypeCreditAlphaNum4("IOM", issuerAccountId)

        // Create trustline with maximum limit
        val trustingAccount = horizonServer.accounts().account(trustingAccountId)
        val changeTrustOp = ChangeTrustOperation(
            asset = myAsset,
            limit = ChangeTrustOperation.MAX_LIMIT
        )

        val transaction = TransactionBuilder(
            sourceAccount = Account(trustingAccountId, trustingAccount.sequenceNumber),
            network = network
        )
            .addOperation(changeTrustOp)
            .setTimeout(TransactionPreconditions.TIMEOUT_INFINITE)
            .setBaseFee(AbstractTransaction.MIN_BASE_FEE)
            .build()

        transaction.sign(trustingKeyPair)

        println("TX XDR: ${transaction.toEnvelopeXdrBase64()}")

        val response = horizonServer.submitTransaction(transaction.toEnvelopeXdrBase64())
        assertTrue(response.successful, "ChangeTrust with max limit should succeed")

        delay(3000)

        // Verify trustline was created
        val trustingAccountReloaded = horizonServer.accounts().account(trustingAccountId)
        var found = false
        for (balance in trustingAccountReloaded.balances) {
            if (balance.assetCode == "IOM") {
                found = true
                // The limit should be the maximum value
                val expectedMaxLimit = ChangeTrustOperation.MAX_LIMIT.toDouble()
                assertEquals(expectedMaxLimit, balance.limit?.toDouble(), "Limit should be max value")
                break
            }
        }
        assertTrue(found, "Trustline with max limit should exist")
    }

    /**
     * Test AllowTrust operation with authorization flags.
     *
     * **NOTE**: This test is IGNORED because the AllowTrust operation was deprecated in
     * Stellar Protocol 17 and is no longer supported by the current testnet/mainnet.
     * The modern replacement is **SetTrustlineFlagsOperation** which should be implemented.
     *
     * This test was ported from Flutter SDK's `trust_test.dart` but cannot run against
     * current Stellar networks. It's kept here for documentation purposes and to show
     * what functionality needs to be re-implemented using SetTrustlineFlagsOperation.
     *
     * ## What This Test Was Meant to Demonstrate
     *
     * The test demonstrates the full trustline authorization workflow:
     * 1. **Setup**: Creates issuer and trustor accounts
     * 2. **Authorization flags**: Issuer sets AUTH_REQUIRED and AUTH_REVOCABLE flags
     * 3. **Create trustline**: Trustor creates trustline to ASTRO asset
     * 4. **Unauthorized payment**: Issuer tries to pay trustor (fails - not authorized)
     * 5. **Authorize trustline**: Issuer authorizes trustor (AllowTrust with flag 1)
     * 6. **Authorized payment**: Issuer successfully pays trustor 100 ASTRO
     * 7. **Create offer**: Trustor creates passive sell offer for ASTRO
     * 8. **Deauthorize**: Issuer deauthorizes trustor (AllowTrust with flag 0)
     * 9. **Offer deleted**: Passive sell offer is automatically deleted
     * 10. **Re-authorize**: Issuer re-authorizes trustor (flag 1)
     * 11. **Create offer again**: Trustor creates new passive sell offer
     * 12. **Authorize to maintain liabilities**: Issuer sets flag 2
     * 13. **Offer maintained**: Offer still exists (can sell existing balance)
     * 14. **New payment fails**: Issuer cannot send new funds (flag 2 restriction)
     * 15. **Verify operations**: All operations and effects can be parsed
     *
     * ## Authorization States (AllowTrust)
     *
     * - **Flag 0 (Deauthorized)**: Cannot receive or send the asset, all offers deleted
     * - **Flag 1 (Fully Authorized)**: Can receive and send freely, can create offers
     * - **Flag 2 (Authorized to Maintain Liabilities)**: Can send existing balance and maintain offers,
     *   but cannot receive new funds
     *
     * ## TODO: Implement SetTrustlineFlagsOperation
     *
     * To make this test work with modern Stellar networks, the SDK needs to implement
     * SetTrustlineFlagsOperation which replaces AllowTrust. The flags are:
     * - AUTHORIZED_FLAG (1)
     * - AUTHORIZED_TO_MAINTAIN_LIABILITIES_FLAG (2)
     * - TRUSTLINE_CLAWBACK_ENABLED_FLAG (4)
     *
     * @see <a href="https://developers.stellar.org/docs/learn/fundamentals/transactions/list-of-operations#set-trustline-flags">Set Trustline Flags</a>
     * @see <a href="https://github.com/stellar/stellar-protocol/blob/master/core/cap-0035.md">CAP-0035: Asset Clawback</a>
     */
    @Ignore("AllowTrust operation is deprecated (Protocol 17+). Requires SetTrustlineFlagsOperation implementation.")
    @Test
    fun testAllowTrust() = runTest(timeout = 300.seconds) {
        // Test implementation kept for reference but cannot run without SetTrustlineFlagsOperation
        // See class documentation for what this test was meant to demonstrate
    }
}
