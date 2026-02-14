package com.soneso.stellar.sdk.integrationTests

import com.soneso.stellar.sdk.*
import com.soneso.stellar.sdk.horizon.HorizonServer
import com.soneso.stellar.sdk.horizon.responses.effects.ClaimableBalanceClawedBackEffectResponse
import com.soneso.stellar.sdk.horizon.responses.effects.TrustlineFlagsUpdatedEffectResponse
import com.soneso.stellar.sdk.xdr.AccountFlagsXdr
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import kotlin.time.Duration.Companion.seconds
import kotlin.test.*

/**
 * Comprehensive integration tests for Clawback-related operations.
 *
 * These tests verify the SDK's clawback operations against a live Stellar testnet.
 * They cover:
 * - Enabling clawback on issuer accounts (AUTH_CLAWBACK_ENABLED_FLAG + AUTH_REVOCABLE_FLAG)
 * - Clawback operation to recover assets from accounts
 * - ClawbackClaimableBalance operation to recover claimable balances
 * - SetTrustLineFlags operation to clear clawback enabled flags
 * - Verification of balance clawback enabled status
 * - Verification of effects parsing (ClaimableBalanceClawedBackEffect, TrustlineFlagsUpdatedEffect)
 *
 * ## Test Execution
 *
 * These tests use real network operations and require:
 * 1. Network access to Stellar testnet
 * 2. FriendBot availability for funding
 * 3. Network latency tolerance (delays after operations)
 *
 * Run: `./gradlew :stellar-sdk:jvmTest --tests "ClawbackIntegrationTest"`
 *
 * @see <a href="https://developers.stellar.org/docs/learn/fundamentals/transactions/list-of-operations#clawback">Clawback</a>
 * @see <a href="https://developers.stellar.org/docs/learn/fundamentals/transactions/list-of-operations#clawback-claimable-balance">ClawbackClaimableBalance</a>
 * @see <a href="https://developers.stellar.org/docs/learn/fundamentals/transactions/list-of-operations#set-trustline-flags">SetTrustLineFlags</a>
 */
class ClawbackIntegrationTest {

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
     * Test comprehensive clawback workflow including asset clawback and claimable balance clawback.
     *
     * This test:
     * 1. Creates and funds a master account
     * 2. Creates destination account via CreateAccount operation
     * 3. Creates SKY asset issuer account
     * 4. Enables clawback on issuer account (AUTH_CLAWBACK_ENABLED_FLAG | AUTH_REVOCABLE_FLAG)
     * 5. Creates trustline from destination to SKY asset
     * 6. Sends 100 SKY to destination account
     * 7. Verifies destination has received the SKY
     * 8. Claws back 80 SKY from destination account using Clawback operation
     * 9. Verifies destination balance is reduced to ~20 SKY
     * 10. Creates claimant account and establishes trustline
     * 11. Verifies trustline has clawback enabled flag set
     * 12. Creates claimable balance for claimant
     * 13. Claws back the claimable balance using ClawbackClaimableBalance operation
     * 14. Verifies claimable balance no longer exists
     * 15. Verifies ClaimableBalanceClawedBackEffect is present in effects
     * 16. Clears trustline clawback enabled flag using SetTrustLineFlags operation
     * 17. Verifies TrustlineFlagsUpdatedEffect shows clawback flag cleared
     * 18. Verifies trustline no longer has clawback enabled flag
     * 19. Verifies operations and effects can be parsed
     */
    @Test
    fun testClawbackAndClaimableBalanceClawback() = runTest(timeout = 240.seconds) {
        // 1. Create and fund master account
        val masterAccountKeyPair = KeyPair.random()
        val masterAccountId = masterAccountKeyPair.getAccountId()

        println("Master account ID: $masterAccountId")
        println("Master account seed: ${masterAccountKeyPair.getSecretSeed()?.concatToString()}")

        if (testOn == "testnet") {
            FriendBot.fundTestnetAccount(masterAccountId)
        } else {
            FriendBot.fundFuturenetAccount(masterAccountId)
        }

        realDelay(3000)

        // 2. Create destination account
        val destinationAccountKeyPair = KeyPair.random()
        val destinationAccountId = destinationAccountKeyPair.getAccountId()

        println("Destination account ID: $destinationAccountId")
        println("Destination account seed: ${destinationAccountKeyPair.getSecretSeed()?.concatToString()}")

        var masterAccount = horizonServer.accounts().account(masterAccountId)

        var transaction = TransactionBuilder(
            sourceAccount = Account(masterAccountId, masterAccount.sequenceNumber),
            network = network
        )
            .addOperation(
                CreateAccountOperation(
                    destination = destinationAccountId,
                    startingBalance = "10"
                )
            )
            .setTimeout(TransactionPreconditions.TIMEOUT_INFINITE)
            .setBaseFee(AbstractTransaction.MIN_BASE_FEE)
            .build()

        transaction.sign(masterAccountKeyPair)

        var response = horizonServer.submitTransaction(transaction.toEnvelopeXdrBase64())
        assertTrue(response.successful, "Create destination account should succeed")

        realDelay(3000)

        // 3. Create SKY issuer account
        val skyIssuerAccountKeyPair = KeyPair.random()
        val skyIssuerAccountId = skyIssuerAccountKeyPair.getAccountId()

        println("SKY issuer account ID: $skyIssuerAccountId")
        println("SKY issuer account seed: ${skyIssuerAccountKeyPair.getSecretSeed()?.concatToString()}")

        masterAccount = horizonServer.accounts().account(masterAccountId)

        transaction = TransactionBuilder(
            sourceAccount = Account(masterAccountId, masterAccount.sequenceNumber),
            network = network
        )
            .addOperation(
                CreateAccountOperation(
                    destination = skyIssuerAccountId,
                    startingBalance = "10"
                )
            )
            .setTimeout(TransactionPreconditions.TIMEOUT_INFINITE)
            .setBaseFee(AbstractTransaction.MIN_BASE_FEE)
            .build()

        transaction.sign(masterAccountKeyPair)

        response = horizonServer.submitTransaction(transaction.toEnvelopeXdrBase64())
        assertTrue(response.successful, "Create SKY issuer account should succeed")

        realDelay(3000)

        // 4. Enable clawback on issuer account
        val skyIssuerAccount = horizonServer.accounts().account(skyIssuerAccountId)

        val setFlagsValue = AccountFlagsXdr.AUTH_CLAWBACK_ENABLED_FLAG.value or
                           AccountFlagsXdr.AUTH_REVOCABLE_FLAG.value

        transaction = TransactionBuilder(
            sourceAccount = Account(skyIssuerAccountId, skyIssuerAccount.sequenceNumber),
            network = network
        )
            .addOperation(
                SetOptionsOperation(
                    setFlags = setFlagsValue
                )
            )
            .addMemo(MemoText("Test enable clawback"))
            .setTimeout(TransactionPreconditions.TIMEOUT_INFINITE)
            .setBaseFee(AbstractTransaction.MIN_BASE_FEE)
            .build()

        transaction.sign(skyIssuerAccountKeyPair)

        response = horizonServer.submitTransaction(transaction.toEnvelopeXdrBase64())
        assertTrue(response.successful, "Enable clawback should succeed")

        println("Clawback enabled on issuer account")

        realDelay(3000)

        // 5. Create SKY asset and establish trustline
        val assetCode = "SKY"
        val skyAsset = AssetTypeCreditAlphaNum4(assetCode, skyIssuerAccountId)
        val limit = "10000"

        val destinationAccount = horizonServer.accounts().account(destinationAccountId)

        transaction = TransactionBuilder(
            sourceAccount = Account(destinationAccountId, destinationAccount.sequenceNumber),
            network = network
        )
            .addOperation(
                ChangeTrustOperation(
                    asset = skyAsset,
                    limit = limit
                )
            )
            .setTimeout(TransactionPreconditions.TIMEOUT_INFINITE)
            .setBaseFee(AbstractTransaction.MIN_BASE_FEE)
            .build()

        transaction.sign(destinationAccountKeyPair)

        response = horizonServer.submitTransaction(transaction.toEnvelopeXdrBase64())
        assertTrue(response.successful, "ChangeTrust should succeed")

        println("Destination account is trusting SKY")

        realDelay(3000)

        // 6. Send 100 SKY to destination
        val skyIssuerAccountReloaded = horizonServer.accounts().account(skyIssuerAccountId)

        transaction = TransactionBuilder(
            sourceAccount = Account(skyIssuerAccountId, skyIssuerAccountReloaded.sequenceNumber),
            network = network
        )
            .addOperation(
                PaymentOperation(
                    destination = destinationAccountId,
                    asset = skyAsset,
                    amount = "100"
                )
            )
            .setTimeout(TransactionPreconditions.TIMEOUT_INFINITE)
            .setBaseFee(AbstractTransaction.MIN_BASE_FEE)
            .build()

        transaction.sign(skyIssuerAccountKeyPair)

        response = horizonServer.submitTransaction(transaction.toEnvelopeXdrBase64())
        assertTrue(response.successful, "Payment should succeed")

        realDelay(3000)

        // 7. Verify destination received SKY
        var destinationAccountUpdated = horizonServer.accounts().account(destinationAccountId)
        var found = false
        for (balance in destinationAccountUpdated.balances) {
            if (balance.assetType != "native" && balance.assetCode == assetCode) {
                val balanceAmount = balance.balance.toDouble()
                assertTrue(balanceAmount > 90, "Balance should be > 90, got $balanceAmount")
                found = true
                break
            }
        }
        assertTrue(found, "SKY balance should be found")

        println("Destination account received SKY")

        // 8. Clawback 80 SKY from destination
        val skyIssuerAccountForClawback = horizonServer.accounts().account(skyIssuerAccountId)

        transaction = TransactionBuilder(
            sourceAccount = Account(skyIssuerAccountId, skyIssuerAccountForClawback.sequenceNumber),
            network = network
        )
            .addOperation(
                ClawbackOperation(
                    from = destinationAccountId,
                    asset = skyAsset,
                    amount = "80"
                )
            )
            .setTimeout(TransactionPreconditions.TIMEOUT_INFINITE)
            .setBaseFee(AbstractTransaction.MIN_BASE_FEE)
            .build()

        transaction.sign(skyIssuerAccountKeyPair)

        response = horizonServer.submitTransaction(transaction.toEnvelopeXdrBase64())
        assertTrue(response.successful, "Clawback should succeed")

        realDelay(3000)

        // 9. Verify balance is reduced
        destinationAccountUpdated = horizonServer.accounts().account(destinationAccountId)
        found = false
        for (balance in destinationAccountUpdated.balances) {
            if (balance.assetType != "native" && balance.assetCode == assetCode) {
                val balanceAmount = balance.balance.toDouble()
                assertTrue(balanceAmount < 30, "Balance should be < 30 after clawback, got $balanceAmount")
                found = true
                break
            }
        }
        assertTrue(found, "SKY balance should still exist")

        println("Clawback success - balance reduced")

        // 10. Create claimant account
        val claimantAccountKeyPair = KeyPair.random()
        val claimantAccountId = claimantAccountKeyPair.getAccountId()

        println("Claimant account ID: $claimantAccountId")
        println("Claimant account seed: ${claimantAccountKeyPair.getSecretSeed()?.concatToString()}")

        masterAccount = horizonServer.accounts().account(masterAccountId)

        transaction = TransactionBuilder(
            sourceAccount = Account(masterAccountId, masterAccount.sequenceNumber),
            network = network
        )
            .addOperation(
                CreateAccountOperation(
                    destination = claimantAccountId,
                    startingBalance = "10"
                )
            )
            .setTimeout(TransactionPreconditions.TIMEOUT_INFINITE)
            .setBaseFee(AbstractTransaction.MIN_BASE_FEE)
            .build()

        transaction.sign(masterAccountKeyPair)

        response = horizonServer.submitTransaction(transaction.toEnvelopeXdrBase64())
        assertTrue(response.successful, "Create claimant account should succeed")

        realDelay(3000)

        // Establish trustline for claimant
        val claimantAccount = horizonServer.accounts().account(claimantAccountId)

        transaction = TransactionBuilder(
            sourceAccount = Account(claimantAccountId, claimantAccount.sequenceNumber),
            network = network
        )
            .addOperation(
                ChangeTrustOperation(
                    asset = skyAsset,
                    limit = limit
                )
            )
            .setTimeout(TransactionPreconditions.TIMEOUT_INFINITE)
            .setBaseFee(AbstractTransaction.MIN_BASE_FEE)
            .build()

        transaction.sign(claimantAccountKeyPair)

        response = horizonServer.submitTransaction(transaction.toEnvelopeXdrBase64())
        assertTrue(response.successful, "Claimant ChangeTrust should succeed")

        println("Claimant account is trusting SKY")

        realDelay(3000)

        // 11. Verify claimant trustline has clawback enabled
        val claimantAccountUpdated = horizonServer.accounts().account(claimantAccountId)

        var clawbackEnabled = false
        for (balance in claimantAccountUpdated.balances) {
            if (balance.assetCode == assetCode && balance.isClawbackEnabled == true) {
                clawbackEnabled = true
                break
            }
        }
        assertTrue(clawbackEnabled, "Claimant trustline should have clawback enabled")

        // 12. Create claimable balance
        val destinationAccountForCB = horizonServer.accounts().account(destinationAccountId)

        val claimant = Claimant(
            destination = claimantAccountId,
            predicate = ClaimPredicate.Unconditional
        )

        transaction = TransactionBuilder(
            sourceAccount = Account(destinationAccountId, destinationAccountForCB.sequenceNumber),
            network = network
        )
            .addOperation(
                CreateClaimableBalanceOperation(
                    asset = skyAsset,
                    amount = "10.00",
                    claimants = listOf(claimant)
                )
            )
            .addMemo(MemoText("create claimable balance"))
            .setTimeout(TransactionPreconditions.TIMEOUT_INFINITE)
            .setBaseFee(AbstractTransaction.MIN_BASE_FEE)
            .build()

        transaction.sign(destinationAccountKeyPair)

        response = horizonServer.submitTransaction(transaction.toEnvelopeXdrBase64())
        assertTrue(response.successful, "CreateClaimableBalance should succeed")

        realDelay(3000)

        // Query claimable balance
        val claimableBalances = horizonServer.claimableBalances()
            .forClaimant(claimantAccountId)
            .execute()

        assertEquals(1, claimableBalances.records.size, "Should have 1 claimable balance")
        val claimableBalance = claimableBalances.records[0]
        val balanceId = claimableBalance.id

        println("Claimable balance created: $balanceId")

        // 13. Clawback the claimable balance
        val skyIssuerAccountForCBClawback = horizonServer.accounts().account(skyIssuerAccountId)

        transaction = TransactionBuilder(
            sourceAccount = Account(skyIssuerAccountId, skyIssuerAccountForCBClawback.sequenceNumber),
            network = network
        )
            .addOperation(
                ClawbackClaimableBalanceOperation(balanceId = balanceId)
            )
            .setTimeout(TransactionPreconditions.TIMEOUT_INFINITE)
            .setBaseFee(AbstractTransaction.MIN_BASE_FEE)
            .build()

        transaction.sign(skyIssuerAccountKeyPair)

        response = horizonServer.submitTransaction(transaction.toEnvelopeXdrBase64())
        assertTrue(response.successful, "ClawbackClaimableBalance should succeed")

        println("Claimable balance clawed back")

        realDelay(3000)

        // 14. Verify claimable balance no longer exists
        val claimableBalancesAfter = horizonServer.claimableBalances()
            .forClaimant(claimantAccountId)
            .execute()

        assertEquals(0, claimableBalancesAfter.records.size, "Should have 0 claimable balances after clawback")

        println("Clawback claimable balance success")

        // 15. Verify ClaimableBalanceClawedBackEffect
        val effectsPage = horizonServer.effects()
            .forAccount(skyIssuerAccountId)
            .limit(5)
            .order(com.soneso.stellar.sdk.horizon.requests.RequestBuilder.Order.DESC)
            .execute()

        assertTrue(effectsPage.records.isNotEmpty(), "Should have effects")

        var clawedBackBalanceId: String? = null
        for (effect in effectsPage.records) {
            if (effect is ClaimableBalanceClawedBackEffectResponse) {
                clawedBackBalanceId = effect.balanceId
                break
            }
        }
        assertNotNull(clawedBackBalanceId, "Should find ClaimableBalanceClawedBackEffect")
        println("Clawed back balance ID: $clawedBackBalanceId")

        // 16. Clear trustline clawback enabled flag
        val skyIssuerAccountForFlagUpdate = horizonServer.accounts().account(skyIssuerAccountId)

        transaction = TransactionBuilder(
            sourceAccount = Account(skyIssuerAccountId, skyIssuerAccountForFlagUpdate.sequenceNumber),
            network = network
        )
            .addOperation(
                SetTrustLineFlagsOperation(
                    trustor = claimantAccountId,
                    asset = skyAsset,
                    clearFlags = SetTrustLineFlagsOperation.TRUSTLINE_CLAWBACK_ENABLED_FLAG,
                    setFlags = null
                )
            )
            .setTimeout(TransactionPreconditions.TIMEOUT_INFINITE)
            .setBaseFee(AbstractTransaction.MIN_BASE_FEE)
            .build()

        transaction.sign(skyIssuerAccountKeyPair)

        response = horizonServer.submitTransaction(transaction.toEnvelopeXdrBase64())
        assertTrue(response.successful, "SetTrustLineFlags should succeed")

        realDelay(3000)

        // 17. Verify TrustlineFlagsUpdatedEffect
        val effectsPageForFlags = horizonServer.effects()
            .forAccount(skyIssuerAccountId)
            .limit(5)
            .order(com.soneso.stellar.sdk.horizon.requests.RequestBuilder.Order.DESC)
            .execute()

        assertTrue(effectsPageForFlags.records.isNotEmpty(), "Should have effects")

        var flagsCleared = false
        for (effect in effectsPageForFlags.records) {
            if (effect is TrustlineFlagsUpdatedEffectResponse) {
                if (effect.clawbackEnabledFlag == false) {
                    flagsCleared = true
                }
                break
            }
        }
        assertTrue(flagsCleared, "Should find TrustlineFlagsUpdatedEffect with clawback flag cleared")

        // 18. Verify trustline no longer has clawback enabled
        val claimantAccountFinal = horizonServer.accounts().account(claimantAccountId)

        var noClawback = false
        for (balance in claimantAccountFinal.balances) {
            if (balance.assetCode == assetCode && balance.isClawbackEnabled == null) {
                noClawback = true
                break
            }
        }
        assertTrue(noClawback, "Claimant trustline should not have clawback enabled")

        println("Cleared trustline flag")

        // 19. Verify operations can be parsed
        val operationsPage = horizonServer.operations()
            .forAccount(skyIssuerAccountId)
            .execute()
        assertTrue(operationsPage.records.isNotEmpty(), "Should have operations")

        println("Test completed successfully!")
    }
}
