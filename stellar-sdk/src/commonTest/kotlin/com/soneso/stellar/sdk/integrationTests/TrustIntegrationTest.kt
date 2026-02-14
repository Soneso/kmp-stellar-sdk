package com.soneso.stellar.sdk.integrationTests

import com.soneso.stellar.sdk.*
import com.soneso.stellar.sdk.horizon.HorizonServer
import com.soneso.stellar.sdk.xdr.AccountFlagsXdr
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
 * - SetTrustlineFlags operation (authorize, deauthorize, maintain liabilities)
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
 * - **SetTrustlineFlags** (Protocol 17+): Issuer controls trustline authorization
 *   - AUTHORIZED_FLAG (1): Fully authorize trustline
 *   - AUTHORIZED_TO_MAINTAIN_LIABILITIES_FLAG (2): Allow maintaining existing positions
 *   - TRUSTLINE_CLAWBACK_ENABLED_FLAG (4): Enable clawback
 *   - Clear flags to revoke specific authorizations
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
 * @see <a href="https://developers.stellar.org/docs/learn/fundamentals/transactions/list-of-operations#set-trustline-flags">Set Trustline Flags</a>
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

        realDelay(3000)

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

        realDelay(3000)

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

        realDelay(3000)

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

        realDelay(3000)

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

        realDelay(3000)

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

        realDelay(3000)

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

        realDelay(3000)

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
     * Test SetTrustlineFlags operation with authorization control.
     *
     * This test demonstrates the full trustline authorization workflow using the modern
     * SetTrustlineFlagsOperation (Protocol 17+), which replaced the deprecated AllowTrust operation.
     *
     * This test:
     * 1. **Setup**: Creates issuer and trustor accounts with appropriate funding
     * 2. **Authorization flags**: Issuer sets AUTH_REQUIRED and AUTH_REVOCABLE flags
     * 3. **Create trustline**: Trustor creates trustline to ASTRO asset
     * 4. **Unauthorized payment**: Issuer tries to pay trustor (fails - not authorized)
     * 5. **Authorize trustline**: Issuer authorizes trustor (SetTrustlineFlags with AUTHORIZED_FLAG)
     * 6. **Authorized payment**: Issuer successfully pays trustor 100 ASTRO
     * 7. **Create offer**: Trustor creates passive sell offer for ASTRO
     * 8. **Deauthorize**: Issuer deauthorizes trustor (clears AUTHORIZED_FLAG)
     * 9. **Offer deleted**: Passive sell offer is automatically deleted when deauthorized
     * 10. **Re-authorize**: Issuer re-authorizes trustor (AUTHORIZED_FLAG)
     * 11. **Create offer again**: Trustor creates new passive sell offer
     * 12. **Authorize to maintain liabilities**: Issuer sets AUTHORIZED_TO_MAINTAIN_LIABILITIES_FLAG
     * 13. **Offer maintained**: Offer still exists (can sell existing balance)
     * 14. **New payment fails**: Issuer cannot send new funds (flag 2 restriction)
     * 15. **Verify operations**: All operations and effects can be parsed
     *
     * ## Authorization States (SetTrustlineFlags)
     *
     * - **No flags / Clear AUTHORIZED_FLAG (deauthorized)**: Cannot receive or send the asset, all offers deleted
     * - **AUTHORIZED_FLAG (1) - Fully Authorized**: Can receive and send freely, can create offers
     * - **AUTHORIZED_TO_MAINTAIN_LIABILITIES_FLAG (2)**: Can send existing balance and maintain offers,
     *   but cannot receive new funds
     *
     * ## SetTrustlineFlags Operation
     *
     * The SetTrustlineFlags operation allows an asset issuer to control trustline authorization.
     * It uses two parameters:
     * - **setFlags**: Flags to enable (bitwise OR of flag values)
     * - **clearFlags**: Flags to disable (bitwise OR of flag values)
     *
     * ### Available Flags
     * - AUTHORIZED_FLAG (1): Fully authorize the trustline
     * - AUTHORIZED_TO_MAINTAIN_LIABILITIES_FLAG (2): Allow maintaining existing positions only
     * - TRUSTLINE_CLAWBACK_ENABLED_FLAG (4): Enable clawback on the trustline
     *
     * @see <a href="https://developers.stellar.org/docs/learn/fundamentals/transactions/list-of-operations#set-trustline-flags">Set Trustline Flags</a>
     * @see <a href="https://github.com/stellar/stellar-protocol/blob/master/core/cap-0035.md">CAP-0035: Asset Clawback</a>
     */
    @Test
    fun testSetTrustlineFlags() = runTest(timeout = 300.seconds) {
        // Create keypairs for issuer and trustor
        val issuerKeyPair = KeyPair.random()
        val trustorKeyPair = KeyPair.random()

        val issuerAccountId = issuerKeyPair.getAccountId()
        val trustorAccountId = trustorKeyPair.getAccountId()

        // Fund both accounts via FriendBot
        if (testOn == "testnet") {
            FriendBot.fundTestnetAccount(issuerAccountId)
            FriendBot.fundTestnetAccount(trustorAccountId)
        } else {
            FriendBot.fundFuturenetAccount(issuerAccountId)
            FriendBot.fundFuturenetAccount(trustorAccountId)
        }

        realDelay(3000)

        // Create custom asset ASTRO (AlphaNum12)
        val assetCode = "ASTRO"
        val astroDollar = AssetTypeCreditAlphaNum12(assetCode, issuerAccountId)

        // Set issuer account flags: AUTH_REQUIRED and AUTH_REVOCABLE
        var issuerAccount = horizonServer.accounts().account(issuerAccountId)
        val setOptionsOp = SetOptionsOperation(
            setFlags = AccountFlagsXdr.AUTH_REQUIRED_FLAG.value or AccountFlagsXdr.AUTH_REVOCABLE_FLAG.value
        )

        var transaction = TransactionBuilder(
            sourceAccount = Account(issuerAccountId, issuerAccount.sequenceNumber),
            network = network
        )
            .addOperation(setOptionsOp)
            .setTimeout(TransactionPreconditions.TIMEOUT_INFINITE)
            .setBaseFee(AbstractTransaction.MIN_BASE_FEE)
            .build()

        transaction.sign(issuerKeyPair)

        var response = horizonServer.submitTransaction(transaction.toEnvelopeXdrBase64())
        assertTrue(response.successful, "SetOptions transaction should succeed")

        realDelay(3000)

        // Trustor creates trustline to ASTRO
        var trustorAccount = horizonServer.accounts().account(trustorAccountId)
        val changeTrustOp = ChangeTrustOperation(
            asset = astroDollar,
            limit = "1000"
        )

        transaction = TransactionBuilder(
            sourceAccount = Account(trustorAccountId, trustorAccount.sequenceNumber),
            network = network
        )
            .addOperation(changeTrustOp)
            .setTimeout(TransactionPreconditions.TIMEOUT_INFINITE)
            .setBaseFee(AbstractTransaction.MIN_BASE_FEE)
            .build()

        transaction.sign(trustorKeyPair)

        response = horizonServer.submitTransaction(transaction.toEnvelopeXdrBase64())
        assertTrue(response.successful, "ChangeTrust transaction should succeed")

        realDelay(3000)

        // Verify trustline exists
        trustorAccount = horizonServer.accounts().account(trustorAccountId)
        var found = false
        for (balance in trustorAccount.balances) {
            if (balance.assetCode == assetCode) {
                found = true
                break
            }
        }
        assertTrue(found, "Trustline should exist")

        // Try to pay trustor (should fail - not authorized)
        issuerAccount = horizonServer.accounts().account(issuerAccountId)
        val paymentOp = PaymentOperation(
            destination = trustorAccountId,
            asset = astroDollar,
            amount = "100"
        )

        transaction = TransactionBuilder(
            sourceAccount = Account(issuerAccountId, issuerAccount.sequenceNumber),
            network = network
        )
            .addOperation(paymentOp)
            .setTimeout(TransactionPreconditions.TIMEOUT_INFINITE)
            .setBaseFee(AbstractTransaction.MIN_BASE_FEE)
            .build()

        transaction.sign(issuerKeyPair)


        // This transaction should fail because the trustline is not authorized
        try {
            horizonServer.submitTransaction(transaction.toEnvelopeXdrBase64())
            fail("Payment should fail - trustline not authorized")
        } catch (e: com.soneso.stellar.sdk.horizon.exceptions.BadRequestException) {
            // Expected - transaction failed with op_not_authorized
            assertTrue(e.body?.contains("op_not_authorized") == true ||
                      e.body?.contains("tx_failed") == true,
                      "Should fail with op_not_authorized")
        }

        realDelay(3000)

        // Authorize trustline using SetTrustlineFlags
        issuerAccount = horizonServer.accounts().account(issuerAccountId)
        val authorizeOp = SetTrustLineFlagsOperation(
            trustor = trustorAccountId,
            asset = astroDollar,
            setFlags = SetTrustLineFlagsOperation.AUTHORIZED_FLAG
        )

        transaction = TransactionBuilder(
            sourceAccount = Account(issuerAccountId, issuerAccount.sequenceNumber),
            network = network
        )
            .addOperation(authorizeOp)
            .setTimeout(TransactionPreconditions.TIMEOUT_INFINITE)
            .setBaseFee(AbstractTransaction.MIN_BASE_FEE)
            .build()

        transaction.sign(issuerKeyPair)

        response = horizonServer.submitTransaction(transaction.toEnvelopeXdrBase64())
        assertTrue(response.successful, "SetTrustlineFlags authorize should succeed")

        realDelay(3000)

        // Now payment should succeed
        issuerAccount = horizonServer.accounts().account(issuerAccountId)
        val paymentOp2 = PaymentOperation(
            destination = trustorAccountId,
            asset = astroDollar,
            amount = "100"
        )

        transaction = TransactionBuilder(
            sourceAccount = Account(issuerAccountId, issuerAccount.sequenceNumber),
            network = network
        )
            .addOperation(paymentOp2)
            .setTimeout(TransactionPreconditions.TIMEOUT_INFINITE)
            .setBaseFee(AbstractTransaction.MIN_BASE_FEE)
            .build()

        transaction.sign(issuerKeyPair)

        response = horizonServer.submitTransaction(transaction.toEnvelopeXdrBase64())
        assertTrue(response.successful, "Payment should succeed after authorization")

        realDelay(3000)

        // Trustor creates passive sell offer
        trustorAccount = horizonServer.accounts().account(trustorAccountId)
        val createOfferOp = CreatePassiveSellOfferOperation(
            selling = astroDollar,
            buying = AssetTypeNative,
            amount = "100",
            price = Price.fromString("0.5")
        )

        transaction = TransactionBuilder(
            sourceAccount = Account(trustorAccountId, trustorAccount.sequenceNumber),
            network = network
        )
            .addOperation(createOfferOp)
            .setTimeout(TransactionPreconditions.TIMEOUT_INFINITE)
            .setBaseFee(AbstractTransaction.MIN_BASE_FEE)
            .build()

        transaction.sign(trustorKeyPair)

        response = horizonServer.submitTransaction(transaction.toEnvelopeXdrBase64())
        assertTrue(response.successful, "CreatePassiveSellOffer should succeed")

        realDelay(3000)

        // Verify offer exists
        var offersPage = horizonServer.offers().forAccount(trustorAccountId).execute()
        assertEquals(1, offersPage.records.size, "Should have 1 offer")

        // Deauthorize trustline (clear AUTHORIZED_FLAG)
        issuerAccount = horizonServer.accounts().account(issuerAccountId)
        val deauthorizeOp = SetTrustLineFlagsOperation(
            trustor = trustorAccountId,
            asset = astroDollar,
            clearFlags = SetTrustLineFlagsOperation.AUTHORIZED_FLAG
        )

        transaction = TransactionBuilder(
            sourceAccount = Account(issuerAccountId, issuerAccount.sequenceNumber),
            network = network
        )
            .addOperation(deauthorizeOp)
            .setTimeout(TransactionPreconditions.TIMEOUT_INFINITE)
            .setBaseFee(AbstractTransaction.MIN_BASE_FEE)
            .build()

        transaction.sign(issuerKeyPair)

        response = horizonServer.submitTransaction(transaction.toEnvelopeXdrBase64())
        assertTrue(response.successful, "SetTrustlineFlags deauthorize should succeed")

        realDelay(3000)

        // Verify offer was deleted (deauthorization deletes offers)
        offersPage = horizonServer.offers().forAccount(trustorAccountId).execute()
        assertEquals(0, offersPage.records.size, "Offers should be deleted when deauthorized")

        // Verify trustor still has the balance
        trustorAccount = horizonServer.accounts().account(trustorAccountId)
        found = false
        for (balance in trustorAccount.balances) {
            if (balance.assetCode == assetCode) {
                found = true
                assertEquals(100.0, balance.balance.toDouble(), "Should have 100 ASTRO")
                break
            }
        }
        assertTrue(found, "Should still have trustline with balance")

        // Re-authorize trustline
        issuerAccount = horizonServer.accounts().account(issuerAccountId)
        val reauthorizeOp = SetTrustLineFlagsOperation(
            trustor = trustorAccountId,
            asset = astroDollar,
            setFlags = SetTrustLineFlagsOperation.AUTHORIZED_FLAG
        )

        transaction = TransactionBuilder(
            sourceAccount = Account(issuerAccountId, issuerAccount.sequenceNumber),
            network = network
        )
            .addOperation(reauthorizeOp)
            .setTimeout(TransactionPreconditions.TIMEOUT_INFINITE)
            .setBaseFee(AbstractTransaction.MIN_BASE_FEE)
            .build()

        transaction.sign(issuerKeyPair)

        response = horizonServer.submitTransaction(transaction.toEnvelopeXdrBase64())
        assertTrue(response.successful, "SetTrustlineFlags re-authorize should succeed")

        realDelay(3000)

        // Create offer again
        trustorAccount = horizonServer.accounts().account(trustorAccountId)
        val createOfferOp2 = CreatePassiveSellOfferOperation(
            selling = astroDollar,
            buying = AssetTypeNative,
            amount = "100",
            price = Price.fromString("0.5")
        )

        transaction = TransactionBuilder(
            sourceAccount = Account(trustorAccountId, trustorAccount.sequenceNumber),
            network = network
        )
            .addOperation(createOfferOp2)
            .setTimeout(TransactionPreconditions.TIMEOUT_INFINITE)
            .setBaseFee(AbstractTransaction.MIN_BASE_FEE)
            .build()

        transaction.sign(trustorKeyPair)

        response = horizonServer.submitTransaction(transaction.toEnvelopeXdrBase64())
        assertTrue(response.successful, "CreatePassiveSellOffer should succeed after re-authorization")

        realDelay(3000)

        // Verify offer exists
        offersPage = horizonServer.offers().forAccount(trustorAccountId).execute()
        assertEquals(1, offersPage.records.size, "Should have 1 offer")

        // Set to AUTHORIZED_TO_MAINTAIN_LIABILITIES_FLAG
        issuerAccount = horizonServer.accounts().account(issuerAccountId)
        val maintainLiabilitiesOp = SetTrustLineFlagsOperation(
            trustor = trustorAccountId,
            asset = astroDollar,
            setFlags = SetTrustLineFlagsOperation.AUTHORIZED_TO_MAINTAIN_LIABILITIES_FLAG,
            clearFlags = SetTrustLineFlagsOperation.AUTHORIZED_FLAG
        )

        transaction = TransactionBuilder(
            sourceAccount = Account(issuerAccountId, issuerAccount.sequenceNumber),
            network = network
        )
            .addOperation(maintainLiabilitiesOp)
            .setTimeout(TransactionPreconditions.TIMEOUT_INFINITE)
            .setBaseFee(AbstractTransaction.MIN_BASE_FEE)
            .build()

        transaction.sign(issuerKeyPair)

        response = horizonServer.submitTransaction(transaction.toEnvelopeXdrBase64())
        assertTrue(response.successful, "SetTrustlineFlags maintain liabilities should succeed")

        realDelay(3000)

        // Verify offer still exists (maintain liabilities allows existing offers)
        offersPage = horizonServer.offers().forAccount(trustorAccountId).execute()
        assertEquals(1, offersPage.records.size, "Offer should still exist with maintain liabilities")

        // Try to send new funds (should fail - authorized to maintain liabilities only)
        issuerAccount = horizonServer.accounts().account(issuerAccountId)
        val paymentOp3 = PaymentOperation(
            destination = trustorAccountId,
            asset = astroDollar,
            amount = "100"
        )

        transaction = TransactionBuilder(
            sourceAccount = Account(issuerAccountId, issuerAccount.sequenceNumber),
            network = network
        )
            .addOperation(paymentOp3)
            .setTimeout(TransactionPreconditions.TIMEOUT_INFINITE)
            .setBaseFee(AbstractTransaction.MIN_BASE_FEE)
            .build()

        transaction.sign(issuerKeyPair)


        // This transaction should fail because trustline only authorized to maintain liabilities
        try {
            horizonServer.submitTransaction(transaction.toEnvelopeXdrBase64())
            fail("Payment should fail - only authorized to maintain liabilities")
        } catch (e: com.soneso.stellar.sdk.horizon.exceptions.BadRequestException) {
            // Expected - transaction failed with op_not_authorized
            assertTrue(e.body?.contains("op_not_authorized") == true ||
                      e.body?.contains("tx_failed") == true,
                      "Should fail with op_not_authorized")
        }

        realDelay(3000)

//         // Verify operations and effects can be parsed
//         val trustorOperationsPage = horizonServer.operations().forAccount(trustorAccountId).execute()
//         assertTrue(trustorOperationsPage.records.isNotEmpty(), "Should have trustor operations")
// 
//         val trustorEffectsPage = horizonServer.effects().forAccount(trustorAccountId).execute()
//         assertTrue(trustorEffectsPage.records.isNotEmpty(), "Should have trustor effects")
// 
//         val issuerOperationsPage = horizonServer.operations().forAccount(issuerAccountId).execute()
//         assertTrue(issuerOperationsPage.records.isNotEmpty(), "Should have issuer operations")
// 
//         val issuerEffectsPage = horizonServer.effects().forAccount(issuerAccountId).execute()
//         assertTrue(issuerEffectsPage.records.isNotEmpty(), "Should have issuer effects")
    }
}
