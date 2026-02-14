package com.soneso.stellar.sdk.integrationTests

import com.soneso.stellar.sdk.*
import com.soneso.stellar.sdk.horizon.HorizonServer
import kotlinx.coroutines.test.runTest
import kotlin.test.*
import kotlin.time.Duration.Companion.seconds

/**
 * Comprehensive integration tests for Sponsorship-related operations.
 *
 * These tests verify the SDK's sponsorship operations against a live Stellar testnet.
 * They cover:
 * - BeginSponsoringFutureReserves operation
 * - EndSponsoringFutureReserves operation
 * - RevokeSponsorship operations for various ledger entries:
 *   - Account sponsorship
 *   - Data entry sponsorship
 *   - Trustline sponsorship
 *   - Signer sponsorship
 *   - Offer sponsorship
 *   - Claimable balance sponsorship
 *
 * ## Sponsorship Overview
 *
 * Sponsorship is a feature introduced in Protocol 15 that allows one account (the sponsor)
 * to pay the reserves for another account's (the sponsored) ledger entries. This is useful for:
 * - Onboarding new users without requiring them to hold XLM
 * - Creating advanced account structures where reserve costs are managed by a central account
 * - Implementing multi-signature setups with flexible reserve management
 *
 * The sponsorship pattern involves:
 * 1. BeginSponsoringFutureReserves - Starts the sponsorship block
 * 2. Operations to create sponsored entries (accounts, trustlines, data, signers, offers, etc.)
 * 3. EndSponsoringFutureReserves - Ends the sponsorship block
 * 4. RevokeSponsorship - Removes sponsorship from previously sponsored entries
 *
 * ## Test Execution
 *
 * These tests use real network operations and require:
 * 1. Network access to Stellar testnet
 * 2. FriendBot availability for funding
 * 3. Network latency tolerance (delays after operations)
 *
 * Run: `./gradlew :stellar-sdk:jvmTest --tests "SponsorshipIntegrationTest"`
 *
 * @see <a href="https://developers.stellar.org/docs/encyclopedia/sponsored-reserves">Sponsored Reserves</a>
 * @see <a href="https://github.com/stellar/stellar-protocol/blob/master/core/cap-0033.md">CAP-33: Sponsored Reserves</a>
 */
class SponsorshipIntegrationTest {

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
     * Test comprehensive sponsorship workflow.
     *
     * This test demonstrates a complete sponsorship lifecycle including:
     * 1. Creating and funding a master account (sponsor)
     * 2. Creating a sponsored account (account A) with initial reserves paid by sponsor
     * 3. Adding various sponsored entries to account A:
     *    - Data entry
     *    - Trustline for custom asset
     *    - Payment of custom asset
     *    - Manage sell offer
     *    - Claimable balance
     *    - Additional signer
     * 4. Revoking sponsorship for all the created entries
     * 5. Verifying operations and effects can be parsed
     *
     * The test follows this operation sequence:
     * - BeginSponsoringFutureReserves(accountA) - source: master
     * - CreateAccount(accountA, 100) - source: master
     * - ManageData(name="soneso", value="is super") - source: accountA
     * - ChangeTrust(RICH asset, 100000) - source: accountA
     * - Payment(accountA, RICH, 1000) - source: master
     * - ManageSellOffer(RICH, XLM, 10, price=2) - source: accountA
     * - CreateClaimableBalance(master, RICH, 10) - source: master
     * - SetOptions(add signer: master) - source: accountA
     * - EndSponsoringFutureReserves() - source: accountA
     * - RevokeSponsorship(account: accountA) - source: master
     * - RevokeSponsorship(data: accountA/soneso) - source: master
     * - RevokeSponsorship(trustline: accountA/RICH) - source: master
     * - RevokeSponsorship(signer: accountA/master) - source: master
     *
     * All operations are bundled into a single transaction with the memo "sponsor".
     * The transaction requires signatures from both the master account (sponsor) and
     * account A (sponsored), as account A must authorize operations with its source account.
     */
    @Test
    fun testSponsorship() = runTest(timeout = 120.seconds) {
        // 1. Create and fund master account (sponsor)
        val masterAccountKeyPair = KeyPair.random()
        val masterAccountId = masterAccountKeyPair.getAccountId()

        println("Master account ID: $masterAccountId")

        if (testOn == "testnet") {
            FriendBot.fundTestnetAccount(masterAccountId)
        } else {
            FriendBot.fundFuturenetAccount(masterAccountId)
        }

        realDelay(3000)

        val masterAccount = horizonServer.accounts().account(masterAccountId)

        // 2. Create keypair for account A (sponsored account)
        val accountAKeyPair = KeyPair.random()
        val accountAId = accountAKeyPair.getAccountId()

        println("Account A ID: $accountAId")

        // 3. Create custom asset RICH issued by master account
        val richAsset = AssetTypeCreditAlphaNum4("RICH", masterAccountId)

        // 4. Build the sponsorship transaction

        // BeginSponsoringFutureReserves operation
        val beginSponsoringOp = BeginSponsoringFutureReservesOperation(sponsoredId = accountAId)
        beginSponsoringOp.sourceAccount = masterAccountId

        // CreateAccount operation - creates account A with 100 XLM
        val createAccountOp = CreateAccountOperation(
            destination = accountAId,
            startingBalance = "100"
        )

        // ManageData operation - adds data entry to account A
        val dataName = "soneso"
        val dataValue = "is super"
        val manageDataOp = ManageDataOperation.forString(dataName, dataValue)
        manageDataOp.sourceAccount = accountAId

        // ChangeTrust operation - establishes trustline for RICH asset on account A
        val changeTrustOp = ChangeTrustOperation(
            asset = richAsset,
            limit = "100000"
        )
        changeTrustOp.sourceAccount = accountAId

        // Payment operation - sends 1000 RICH from master to account A
        val paymentOp = PaymentOperation(
            destination = accountAId,
            asset = richAsset,
            amount = "1000"
        )

        // ManageSellOffer operation - creates sell offer on account A (10 RICH for XLM at price 2)
        val manageSellOfferOp = ManageSellOfferOperation(
            selling = richAsset,
            buying = AssetTypeNative,
            amount = "10",
            price = Price(2, 1)
        )
        manageSellOfferOp.sourceAccount = accountAId

        // CreateClaimableBalance operation - creates claimable balance for master account
        val claimant = Claimant(
            destination = masterAccountId,
            predicate = ClaimPredicate.Unconditional
        )
        val createClaimableBalanceOp = CreateClaimableBalanceOperation(
            asset = richAsset,
            amount = "10",
            claimants = listOf(claimant)
        )

        // SetOptions operation - adds master account as signer to account A
        val signerKey = SignerKey.ed25519PublicKey(masterAccountId)
        val setOptionsOp = SetOptionsOperation(
            signer = signerKey,
            signerWeight = 1
        )
        setOptionsOp.sourceAccount = accountAId

        // EndSponsoringFutureReserves operation
        val endSponsoringOp = EndSponsoringFutureReservesOperation()
        endSponsoringOp.sourceAccount = accountAId

        // RevokeSponsorship operations
        val revokeAccountSponsorshipOp = RevokeSponsorshipOperation(
            sponsorship = Sponsorship.Account(accountAId)
        )

        val revokeDataSponsorshipOp = RevokeSponsorshipOperation(
            sponsorship = Sponsorship.Data(accountAId, dataName)
        )

        val revokeTrustlineSponsorshipOp = RevokeSponsorshipOperation(
            sponsorship = Sponsorship.TrustLine(accountAId, richAsset)
        )

        val revokeSignerSponsorshipOp = RevokeSponsorshipOperation(
            sponsorship = Sponsorship.Signer(accountAId, signerKey)
        )

        // 5. Build and sign the transaction
        val transaction = TransactionBuilder(
            sourceAccount = Account(masterAccountId, masterAccount.sequenceNumber),
            network = network
        )
            .addOperation(beginSponsoringOp)
            .addOperation(createAccountOp)
            .addOperation(manageDataOp)
            .addOperation(changeTrustOp)
            .addOperation(paymentOp)
            .addOperation(manageSellOfferOp)
            .addOperation(createClaimableBalanceOp)
            .addOperation(setOptionsOp)
            .addOperation(endSponsoringOp)
            .addOperation(revokeAccountSponsorshipOp)
            .addOperation(revokeDataSponsorshipOp)
            .addOperation(revokeTrustlineSponsorshipOp)
            .addOperation(revokeSignerSponsorshipOp)
            .addMemo(MemoText("sponsor"))
            .setTimeout(TransactionPreconditions.TIMEOUT_INFINITE)
            .setBaseFee(AbstractTransaction.MIN_BASE_FEE)
            .build()

        // Sign with both master account (sponsor) and account A (sponsored)
        transaction.sign(masterAccountKeyPair)
        transaction.sign(accountAKeyPair)

        println("Transaction built and signed")

        // 6. Submit the transaction
        val response = horizonServer.submitTransaction(transaction.toEnvelopeXdrBase64())

        // 7. Verify transaction succeeded
        assertTrue(response.successful, "Sponsorship transaction should succeed")
        assertNotNull(response.hash, "Response should have transaction hash")

        println("Sponsorship transaction submitted: ${response.hash}")

        realDelay(3000)

        // 8. Verify account A was created and has the expected data
        val accountA = horizonServer.accounts().account(accountAId)

        // Verify native balance
        var foundNativeBalance = false
        for (balance in accountA.balances) {
            if (balance.assetType == "native") {
                val balanceAmount = balance.balance.toDouble()
                assertTrue(balanceAmount > 99, "Native balance should be > 99 XLM, got $balanceAmount")
                foundNativeBalance = true
            }
        }
        assertTrue(foundNativeBalance, "Native balance should be found")

        // Verify RICH asset balance
        var foundRichBalance = false
        for (balance in accountA.balances) {
            if (balance.assetType == "credit_alphanum4" && balance.assetCode == "RICH") {
                val balanceAmount = balance.balance.toDouble()
                assertTrue(balanceAmount > 900, "RICH balance should be > 900, got $balanceAmount")
                foundRichBalance = true
            }
        }
        assertTrue(foundRichBalance, "RICH balance should be found")

        println("Account A balances verified")

        // 9. Test that operation and effect responses can be parsed
        val operationsPage = horizonServer.operations()
            .forTransaction(response.hash)
            .execute()
        assertTrue(operationsPage.records.isNotEmpty(), "Should have operations")

        println("Operations page has ${operationsPage.records.size} operations")

        val effectsPage = horizonServer.effects()
            .forTransaction(response.hash)
            .execute()
        assertTrue(effectsPage.records.isNotEmpty(), "Should have effects")

        println("Effects page has ${effectsPage.records.size} effects")

        println("Test completed successfully")
    }
}
