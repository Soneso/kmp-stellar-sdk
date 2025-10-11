package com.soneso.stellar.sdk.integrationTests

import com.soneso.stellar.sdk.*
import com.soneso.stellar.sdk.LiquidityPool
import com.soneso.stellar.sdk.horizon.HorizonServer
import com.soneso.stellar.sdk.horizon.requests.RequestBuilder
import com.soneso.stellar.sdk.horizon.responses.LiquidityPoolResponse
import com.soneso.stellar.sdk.horizon.responses.Page
import com.soneso.stellar.sdk.horizon.responses.effects.*
import com.soneso.stellar.sdk.horizon.responses.operations.*
import com.soneso.stellar.sdk.horizon.responses.TradeResponse
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlin.test.*
import kotlin.time.Duration.Companion.seconds

/**
 * Comprehensive integration tests for AMM (Automated Market Maker) and Liquidity Pool operations.
 *
 * These tests verify the SDK's liquidity pool operations against a live Stellar testnet.
 * They cover:
 * - Creating pool share trustlines (native and non-native asset pairs)
 * - Depositing assets into liquidity pools
 * - Withdrawing assets from liquidity pools
 * - Querying liquidity pool data, effects, operations, and trades
 * - Testing both hex and strkey pool ID formats
 * - Path payments through liquidity pools
 * - Trades queries
 *
 * ## Liquidity Pools
 *
 * Liquidity pools enable automated market makers (AMMs) on the Stellar network, allowing users
 * to deposit asset pairs and earn fees from trades. Each pool contains two assets in a constant
 * product market maker (x * y = k).
 *
 * ## Operations Tested
 *
 * - **ChangeTrustOperation**: Create trustline to liquidity pool shares
 * - **LiquidityPoolDepositOperation**: Deposit assets into a pool
 * - **LiquidityPoolWithdrawOperation**: Withdraw assets from a pool
 * - **PathPaymentStrictSendOperation**: Execute trades through pools
 *
 * ## Test Network
 *
 * All tests use Stellar testnet. To switch to futurenet, change the `testOn` variable.
 *
 * @see <a href="https://developers.stellar.org/docs/learn/encyclopedia/sdex/liquidity-on-stellar-sdex-liquidity-pools">Liquidity Pools</a>
 * @see <a href="https://developers.stellar.org/api/resources/liquiditypools/">Liquidity Pools API</a>
 */
class AMMIntegrationTest {

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

    // Shared test data
    private lateinit var testAccountKeyPair: KeyPair
    private lateinit var assetAIssuerKeyPair: KeyPair
    private lateinit var assetBIssuerKeyPair: KeyPair
    private lateinit var assetA: AssetTypeCreditAlphaNum4
    private lateinit var assetB: AssetTypeCreditAlphaNum12
    private var nonNativeLiquidityPoolId: String = ""
    private var nativeLiquidityPoolId: String = ""

    /**
     * Set up test accounts and assets.
     *
     * This method:
     * 1. Creates and funds three accounts (test account, asset A issuer, asset B issuer)
     * 2. Creates trustlines from test account to both assets
     * 3. Sends initial asset amounts to test account
     * 4. Verifies operations and effects can be parsed
     */
    @BeforeTest
    fun setUp() = runTest(timeout = 180.seconds) {
        // Create keypairs
        testAccountKeyPair = KeyPair.random()
        assetAIssuerKeyPair = KeyPair.random()
        assetBIssuerKeyPair = KeyPair.random()

        val testAccountId = testAccountKeyPair.getAccountId()
        val assetAIssuerId = assetAIssuerKeyPair.getAccountId()
        val assetBIssuerId = assetBIssuerKeyPair.getAccountId()

        // Fund accounts
        if (testOn == "testnet") {
            FriendBot.fundTestnetAccount(testAccountId)
            FriendBot.fundTestnetAccount(assetAIssuerId)
            FriendBot.fundTestnetAccount(assetBIssuerId)
        } else {
            FriendBot.fundFuturenetAccount(testAccountId)
            FriendBot.fundFuturenetAccount(assetAIssuerId)
            FriendBot.fundFuturenetAccount(assetBIssuerId)
        }

        delay(5000)

        // Create assets
        assetA = AssetTypeCreditAlphaNum4("SDK", assetAIssuerId)
        assetB = AssetTypeCreditAlphaNum12("FLUTTER", assetBIssuerId)

        // Create trustlines from test account to both assets
        val testAccount = horizonServer.accounts().account(testAccountId)
        var transaction = TransactionBuilder(
            sourceAccount = Account(testAccountId, testAccount.sequenceNumber),
            network = network
        )
            .addOperation(ChangeTrustOperation(asset = assetA, limit = ChangeTrustOperation.MAX_LIMIT))
            .addOperation(ChangeTrustOperation(asset = assetB, limit = ChangeTrustOperation.MAX_LIMIT))
            .setTimeout(TransactionPreconditions.TIMEOUT_INFINITE)
            .setBaseFee(AbstractTransaction.MIN_BASE_FEE)
            .build()

        transaction.sign(testAccountKeyPair)
        var response = horizonServer.submitTransaction(transaction.toEnvelopeXdrBase64())
        assertTrue(response.successful, "ChangeTrust operations should succeed")

        delay(3000)

        // Send assets from issuers to test account
        val paymentOp1 = PaymentOperation(
            destination = testAccountId,
            asset = assetA,
            amount = "19999191"
        )
        paymentOp1.sourceAccount = assetAIssuerId

        val paymentOp2 = PaymentOperation(
            destination = testAccountId,
            asset = assetB,
            amount = "19999191"
        )
        paymentOp2.sourceAccount = assetBIssuerId

        val assetAIssuerAccount = horizonServer.accounts().account(assetAIssuerId)
        transaction = TransactionBuilder(
            sourceAccount = Account(assetAIssuerId, assetAIssuerAccount.sequenceNumber),
            network = network
        )
            .addOperation(paymentOp1)
            .addOperation(paymentOp2)
            .setTimeout(TransactionPreconditions.TIMEOUT_INFINITE)
            .setBaseFee(AbstractTransaction.MIN_BASE_FEE)
            .build()

        transaction.sign(assetAIssuerKeyPair)
        transaction.sign(assetBIssuerKeyPair)
        response = horizonServer.submitTransaction(transaction.toEnvelopeXdrBase64())
        assertTrue(response.successful, "Payment operations should succeed")

        delay(3000)

        // Verify operations and effects can be parsed
        val operationsPage = horizonServer.operations().forAccount(assetAIssuerId).execute()
        assertTrue(operationsPage.records.isNotEmpty(), "Should have operations")

        val effectsPage = horizonServer.effects().forAccount(assetAIssuerId).execute()
        assertTrue(effectsPage.records.isNotEmpty(), "Should have effects")
    }

    /**
     * Test creating a pool share trustline for non-native asset pair.
     *
     * This test:
     * 1. Creates a liquidity pool for assetA:assetB pair
     * 2. Establishes a trustline to the pool shares
     * 3. Queries the liquidity pool by reserve assets
     * 4. Stores the pool ID for later tests
     * 5. Tests converting pool ID between hex and strkey formats (if applicable)
     * 6. Verifies operations and effects can be parsed
     */
    @Test
    fun testCreatePoolShareTrustlineNonNative() = runTest(timeout = 120.seconds) {
        val testAccountId = testAccountKeyPair.getAccountId()
        val testAccount = horizonServer.accounts().account(testAccountId)

        // Create liquidity pool for assetA:assetB
        val liquidityPool = LiquidityPool(assetA, assetB)

        val transaction = TransactionBuilder(
            sourceAccount = Account(testAccountId, testAccount.sequenceNumber),
            network = network
        )
            .addOperation(ChangeTrustOperation(liquidityPool = liquidityPool, limit = ChangeTrustOperation.MAX_LIMIT))
            .setTimeout(TransactionPreconditions.TIMEOUT_INFINITE)
            .setBaseFee(AbstractTransaction.MIN_BASE_FEE)
            .build()

        transaction.sign(testAccountKeyPair)

        // Verify XDR encoding/decoding
        val envelope = transaction.toEnvelopeXdrBase64()
        val decodedTx = AbstractTransaction.fromEnvelopeXdr(envelope, network)
        assertEquals(envelope, decodedTx.toEnvelopeXdrBase64(), "XDR encoding should be reversible")

        val response = horizonServer.submitTransaction(envelope)
        assertTrue(response.successful, "ChangeTrust for pool share should succeed")

        delay(3000)

        // Query liquidity pool
        val poolsPage = horizonServer.liquidityPools()
            .forReserves(
                "${assetA.code}:${assetA.issuer}",
                "${assetB.code}:${assetB.issuer}"
            )
            .limit(4)
            .order(RequestBuilder.Order.ASC)
            .execute()

        assertTrue(poolsPage.records.isNotEmpty(), "Should find liquidity pool")
        nonNativeLiquidityPoolId = poolsPage.records.first().id
        println("Non-native pool ID (hex): $nonNativeLiquidityPoolId")

        // Test strkey encoding if pool ID is in hex format
        if (!nonNativeLiquidityPoolId.startsWith("L")) {
            val poolIdBytes = Util.hexToBytes(nonNativeLiquidityPoolId)
            val strKey = StrKey.encodeLiquidityPool(poolIdBytes)
            println("Non-native pool ID (strkey): $strKey")
            assertTrue(strKey.startsWith("L"), "StrKey pool ID should start with L")
        }

        // Verify operations and effects can be parsed
        val operationsPage = horizonServer.operations().forAccount(testAccountId).execute()
        assertTrue(operationsPage.records.isNotEmpty(), "Should have operations")

        val effectsPage = horizonServer.effects().forAccount(testAccountId).execute()
        assertTrue(effectsPage.records.isNotEmpty(), "Should have effects")
    }

    /**
     * Test creating a pool share trustline for native asset pair.
     *
     * This test:
     * 1. Creates a liquidity pool for XLM:assetB pair
     * 2. Establishes a trustline to the pool shares
     * 3. Queries the liquidity pool by reserve assets
     * 4. Stores the pool ID for later tests
     * 5. Tests converting pool ID between hex and strkey formats (if applicable)
     * 6. Verifies operations and effects can be parsed
     */
    @Test
    fun testCreatePoolShareTrustlineNative() = runTest(timeout = 120.seconds) {
        val testAccountId = testAccountKeyPair.getAccountId()
        val testAccount = horizonServer.accounts().account(testAccountId)

        // Create liquidity pool for native:assetB
        val liquidityPool = LiquidityPool(AssetTypeNative, assetB)

        val transaction = TransactionBuilder(
            sourceAccount = Account(testAccountId, testAccount.sequenceNumber),
            network = network
        )
            .addOperation(ChangeTrustOperation(liquidityPool = liquidityPool, limit = ChangeTrustOperation.MAX_LIMIT))
            .setTimeout(TransactionPreconditions.TIMEOUT_INFINITE)
            .setBaseFee(AbstractTransaction.MIN_BASE_FEE)
            .build()

        transaction.sign(testAccountKeyPair)

        // Verify XDR encoding/decoding
        val envelope = transaction.toEnvelopeXdrBase64()
        val decodedTx = AbstractTransaction.fromEnvelopeXdr(envelope, network)
        assertEquals(envelope, decodedTx.toEnvelopeXdrBase64(), "XDR encoding should be reversible")

        val response = horizonServer.submitTransaction(envelope)
        assertTrue(response.successful, "ChangeTrust for pool share should succeed")

        delay(3000)

        // Query liquidity pool
        val poolsPage = horizonServer.liquidityPools()
            .forReserves("native", "${assetB.code}:${assetB.issuer}")
            .limit(4)
            .order(RequestBuilder.Order.ASC)
            .execute()

        assertTrue(poolsPage.records.isNotEmpty(), "Should find liquidity pool")
        nativeLiquidityPoolId = poolsPage.records.first().id
        println("Native pool ID (hex): $nativeLiquidityPoolId")

        // Test strkey encoding if pool ID is in hex format
        if (!nativeLiquidityPoolId.startsWith("L")) {
            val poolIdBytes = Util.hexToBytes(nativeLiquidityPoolId)
            val strKey = StrKey.encodeLiquidityPool(poolIdBytes)
            println("Native pool ID (strkey): $strKey")
            assertTrue(strKey.startsWith("L"), "StrKey pool ID should start with L")
        }

        // Verify operations and effects can be parsed
        val operationsPage = horizonServer.operations().forAccount(testAccountId).execute()
        assertTrue(operationsPage.records.isNotEmpty(), "Should have operations")

        val effectsPage = horizonServer.effects().forAccount(testAccountId).execute()
        assertTrue(effectsPage.records.isNotEmpty(), "Should have effects")
    }

    /**
     * Test depositing assets into a non-native liquidity pool.
     *
     * This test:
     * 1. Deposits 250 units of assetA and assetB into the non-native pool
     * 2. Verifies the deposit transaction succeeds
     * 3. Tests XDR encoding/decoding
     * 4. If pool ID is hex, also tests deposit with strkey format
     * 5. Verifies operations and effects can be parsed
     */
    @Test
    fun testDepositNonNative() = runTest(timeout = 120.seconds) {
        // First create the pool trustline
        testCreatePoolShareTrustlineNonNative()

        delay(3000)

        val testAccountId = testAccountKeyPair.getAccountId()
        var testAccount = horizonServer.accounts().account(testAccountId)

        println("Depositing to non-native pool ID: $nonNativeLiquidityPoolId")

        val depositOp = LiquidityPoolDepositOperation(
            liquidityPoolId = nonNativeLiquidityPoolId,
            maxAmountA = "250.0",
            maxAmountB = "250.0",
            minPrice = Price.fromString("1.0"),
            maxPrice = Price.fromString("2.0")
        )

        var transaction = TransactionBuilder(
            sourceAccount = Account(testAccountId, testAccount.sequenceNumber),
            network = network
        )
            .addOperation(depositOp)
            .setTimeout(TransactionPreconditions.TIMEOUT_INFINITE)
            .setBaseFee(AbstractTransaction.MIN_BASE_FEE)
            .build()

        transaction.sign(testAccountKeyPair)

        // Verify XDR encoding/decoding
        val envelope = transaction.toEnvelopeXdrBase64()
        val decodedTx = AbstractTransaction.fromEnvelopeXdr(envelope, network)
        assertEquals(envelope, decodedTx.toEnvelopeXdrBase64(), "XDR encoding should be reversible")

        var response = horizonServer.submitTransaction(envelope)
        assertTrue(response.successful, "Liquidity pool deposit should succeed")

        delay(3000)

        // Verify operations and effects can be parsed
        val operationsPage = horizonServer.operations().forAccount(testAccountId).execute()
        assertTrue(operationsPage.records.isNotEmpty(), "Should have operations")

        val effectsPage = horizonServer.effects().forAccount(testAccountId).execute()
        assertTrue(effectsPage.records.isNotEmpty(), "Should have effects")

        // If pool ID is hex, also test with strkey format
        if (!nonNativeLiquidityPoolId.startsWith("L")) {
            val poolIdBytes = Util.hexToBytes(nonNativeLiquidityPoolId)
            val strKey = StrKey.encodeLiquidityPool(poolIdBytes)

            // Decode strkey back to hex
            val decodedBytes = StrKey.decodeLiquidityPool(strKey)
            val hexFromStrKey = Util.bytesToHex(decodedBytes).lowercase()

            val depositOpStrKey = LiquidityPoolDepositOperation(
                liquidityPoolId = hexFromStrKey,
                maxAmountA = "10.0",
                maxAmountB = "10.0",
                minPrice = Price.fromString("1.0"),
                maxPrice = Price.fromString("2.0")
            )

            testAccount = horizonServer.accounts().account(testAccountId)
            transaction = TransactionBuilder(
                sourceAccount = Account(testAccountId, testAccount.sequenceNumber),
                network = network
            )
                .addOperation(depositOpStrKey)
                .setTimeout(TransactionPreconditions.TIMEOUT_INFINITE)
                .setBaseFee(AbstractTransaction.MIN_BASE_FEE)
                .build()

            transaction.sign(testAccountKeyPair)
            response = horizonServer.submitTransaction(transaction.toEnvelopeXdrBase64())
            assertTrue(response.successful, "Deposit with strkey-converted pool ID should succeed")

            delay(3000)
        }
    }

    /**
     * Test depositing assets into a native liquidity pool.
     *
     * This test:
     * 1. Deposits 250 units of XLM and assetB into the native pool
     * 2. Verifies the deposit transaction succeeds
     * 3. Tests XDR encoding/decoding
     * 4. If pool ID is hex, also tests deposit with strkey format
     * 5. Verifies operations and effects can be parsed
     */
    @Test
    fun testDepositNative() = runTest(timeout = 120.seconds) {
        // First create the pool trustline
        testCreatePoolShareTrustlineNative()

        delay(3000)

        val testAccountId = testAccountKeyPair.getAccountId()
        var testAccount = horizonServer.accounts().account(testAccountId)

        println("Depositing to native pool ID: $nativeLiquidityPoolId")

        val depositOp = LiquidityPoolDepositOperation(
            liquidityPoolId = nativeLiquidityPoolId,
            maxAmountA = "250.0",
            maxAmountB = "250.0",
            minPrice = Price.fromString("1.0"),
            maxPrice = Price.fromString("2.0")
        )

        var transaction = TransactionBuilder(
            sourceAccount = Account(testAccountId, testAccount.sequenceNumber),
            network = network
        )
            .addOperation(depositOp)
            .setTimeout(TransactionPreconditions.TIMEOUT_INFINITE)
            .setBaseFee(AbstractTransaction.MIN_BASE_FEE)
            .build()

        transaction.sign(testAccountKeyPair)

        // Verify XDR encoding/decoding
        val envelope = transaction.toEnvelopeXdrBase64()
        val decodedTx = AbstractTransaction.fromEnvelopeXdr(envelope, network)
        assertEquals(envelope, decodedTx.toEnvelopeXdrBase64(), "XDR encoding should be reversible")

        var response = horizonServer.submitTransaction(envelope)
        assertTrue(response.successful, "Liquidity pool deposit should succeed")

        delay(3000)

        // Verify operations and effects can be parsed
        val operationsPage = horizonServer.operations().forAccount(testAccountId).execute()
        assertTrue(operationsPage.records.isNotEmpty(), "Should have operations")

        val effectsPage = horizonServer.effects().forAccount(testAccountId).execute()
        assertTrue(effectsPage.records.isNotEmpty(), "Should have effects")

        // If pool ID is hex, also test with strkey format
        if (!nativeLiquidityPoolId.startsWith("L")) {
            val poolIdBytes = Util.hexToBytes(nativeLiquidityPoolId)
            val strKey = StrKey.encodeLiquidityPool(poolIdBytes)

            // Decode strkey back to hex
            val decodedBytes = StrKey.decodeLiquidityPool(strKey)
            val hexFromStrKey = Util.bytesToHex(decodedBytes).lowercase()

            val depositOpStrKey = LiquidityPoolDepositOperation(
                liquidityPoolId = hexFromStrKey,
                maxAmountA = "250.0",
                maxAmountB = "250.0",
                minPrice = Price.fromString("1.0"),
                maxPrice = Price.fromString("2.0")
            )

            testAccount = horizonServer.accounts().account(testAccountId)
            transaction = TransactionBuilder(
                sourceAccount = Account(testAccountId, testAccount.sequenceNumber),
                network = network
            )
                .addOperation(depositOpStrKey)
                .setTimeout(TransactionPreconditions.TIMEOUT_INFINITE)
                .setBaseFee(AbstractTransaction.MIN_BASE_FEE)
                .build()

            transaction.sign(testAccountKeyPair)
            response = horizonServer.submitTransaction(transaction.toEnvelopeXdrBase64())
            assertTrue(response.successful, "Deposit with strkey-converted pool ID should succeed")

            delay(3000)
        }
    }

    /**
     * Test withdrawing assets from a non-native liquidity pool.
     *
     * This test:
     * 1. Ensures deposits have been made to the pool
     * 2. Withdraws 100 units of pool shares
     * 3. Verifies the withdrawal transaction succeeds
     * 4. Tests XDR encoding/decoding
     * 5. If pool ID is hex, also tests withdrawal with strkey format
     * 6. Verifies operations and effects can be parsed
     */
    @Test
    fun testWithdrawNonNative() = runTest(timeout = 120.seconds) {
        // First make a deposit
        testDepositNonNative()

        delay(3000)

        val testAccountId = testAccountKeyPair.getAccountId()
        var testAccount = horizonServer.accounts().account(testAccountId)

        println("Withdrawing from non-native pool ID: $nonNativeLiquidityPoolId")

        val withdrawOp = LiquidityPoolWithdrawOperation(
            liquidityPoolId = nonNativeLiquidityPoolId,
            amount = "100",
            minAmountA = "100",
            minAmountB = "100"
        )

        var transaction = TransactionBuilder(
            sourceAccount = Account(testAccountId, testAccount.sequenceNumber),
            network = network
        )
            .addOperation(withdrawOp)
            .setTimeout(TransactionPreconditions.TIMEOUT_INFINITE)
            .setBaseFee(AbstractTransaction.MIN_BASE_FEE)
            .build()

        transaction.sign(testAccountKeyPair)

        // Verify XDR encoding/decoding
        val envelope = transaction.toEnvelopeXdrBase64()
        val decodedTx = AbstractTransaction.fromEnvelopeXdr(envelope, network)
        assertEquals(envelope, decodedTx.toEnvelopeXdrBase64(), "XDR encoding should be reversible")

        var response = horizonServer.submitTransaction(envelope)
        assertTrue(response.successful, "Liquidity pool withdrawal should succeed")

        delay(3000)

        // Verify operations and effects can be parsed
        val operationsPage = horizonServer.operations().forAccount(testAccountId).execute()
        assertTrue(operationsPage.records.isNotEmpty(), "Should have operations")

        val effectsPage = horizonServer.effects().forAccount(testAccountId).execute()
        assertTrue(effectsPage.records.isNotEmpty(), "Should have effects")

        // If pool ID is hex, also test with strkey format
        if (!nonNativeLiquidityPoolId.startsWith("L")) {
            val poolIdBytes = Util.hexToBytes(nonNativeLiquidityPoolId)
            val strKey = StrKey.encodeLiquidityPool(poolIdBytes)

            // Decode strkey back to hex
            val decodedBytes = StrKey.decodeLiquidityPool(strKey)
            val hexFromStrKey = Util.bytesToHex(decodedBytes).lowercase()

            val withdrawOpStrKey = LiquidityPoolWithdrawOperation(
                liquidityPoolId = hexFromStrKey,
                amount = "100",
                minAmountA = "100",
                minAmountB = "100"
            )

            testAccount = horizonServer.accounts().account(testAccountId)
            transaction = TransactionBuilder(
                sourceAccount = Account(testAccountId, testAccount.sequenceNumber),
                network = network
            )
                .addOperation(withdrawOpStrKey)
                .setTimeout(TransactionPreconditions.TIMEOUT_INFINITE)
                .setBaseFee(AbstractTransaction.MIN_BASE_FEE)
                .build()

            transaction.sign(testAccountKeyPair)
            response = horizonServer.submitTransaction(transaction.toEnvelopeXdrBase64())
            assertTrue(response.successful, "Withdrawal with strkey-converted pool ID should succeed")

            delay(3000)
        }
    }

    /**
     * Test withdrawing assets from a native liquidity pool.
     *
     * This test:
     * 1. Ensures deposits have been made to the pool
     * 2. Withdraws 1 unit of pool shares
     * 3. Verifies the withdrawal transaction succeeds
     * 4. Tests XDR encoding/decoding
     * 5. If pool ID is hex, also tests withdrawal with strkey format
     * 6. Verifies operations and effects can be parsed
     */
    @Test
    fun testWithdrawNative() = runTest(timeout = 120.seconds) {
        // First make a deposit
        testDepositNative()

        delay(3000)

        val testAccountId = testAccountKeyPair.getAccountId()
        var testAccount = horizonServer.accounts().account(testAccountId)

        println("Withdrawing from native pool ID: $nativeLiquidityPoolId")

        val withdrawOp = LiquidityPoolWithdrawOperation(
            liquidityPoolId = nativeLiquidityPoolId,
            amount = "1",
            minAmountA = "1",
            minAmountB = "1"
        )

        var transaction = TransactionBuilder(
            sourceAccount = Account(testAccountId, testAccount.sequenceNumber),
            network = network
        )
            .addOperation(withdrawOp)
            .setTimeout(TransactionPreconditions.TIMEOUT_INFINITE)
            .setBaseFee(AbstractTransaction.MIN_BASE_FEE)
            .build()

        transaction.sign(testAccountKeyPair)

        // Verify XDR encoding/decoding
        val envelope = transaction.toEnvelopeXdrBase64()
        val decodedTx = AbstractTransaction.fromEnvelopeXdr(envelope, network)
        assertEquals(envelope, decodedTx.toEnvelopeXdrBase64(), "XDR encoding should be reversible")

        var response = horizonServer.submitTransaction(envelope)
        assertTrue(response.successful, "Liquidity pool withdrawal should succeed")

        delay(3000)

        // Verify operations and effects can be parsed
        val operationsPage = horizonServer.operations().forAccount(testAccountId).execute()
        assertTrue(operationsPage.records.isNotEmpty(), "Should have operations")

        val effectsPage = horizonServer.effects().forAccount(testAccountId).execute()
        assertTrue(effectsPage.records.isNotEmpty(), "Should have effects")

        // If pool ID is hex, also test with strkey format
        if (!nativeLiquidityPoolId.startsWith("L")) {
            val poolIdBytes = Util.hexToBytes(nativeLiquidityPoolId)
            val strKey = StrKey.encodeLiquidityPool(poolIdBytes)

            // Decode strkey back to hex
            val decodedBytes = StrKey.decodeLiquidityPool(strKey)
            val hexFromStrKey = Util.bytesToHex(decodedBytes).lowercase()

            val withdrawOpStrKey = LiquidityPoolWithdrawOperation(
                liquidityPoolId = hexFromStrKey,
                amount = "1",
                minAmountA = "1",
                minAmountB = "1"
            )

            testAccount = horizonServer.accounts().account(testAccountId)
            transaction = TransactionBuilder(
                sourceAccount = Account(testAccountId, testAccount.sequenceNumber),
                network = network
            )
                .addOperation(withdrawOpStrKey)
                .setTimeout(TransactionPreconditions.TIMEOUT_INFINITE)
                .setBaseFee(AbstractTransaction.MIN_BASE_FEE)
                .build()

            transaction.sign(testAccountKeyPair)
            response = horizonServer.submitTransaction(transaction.toEnvelopeXdrBase64())
            assertTrue(response.successful, "Withdrawal with strkey-converted pool ID should succeed")

            delay(3000)
        }
    }

    /**
     * Test comprehensive liquidity pool queries.
     *
     * This test:
     * 1. Queries effects for the liquidity pool
     * 2. Verifies effect types (trustline created, pool created, deposited, withdrew)
     * 3. Queries transactions for the pool
     * 4. Queries operations for the pool
     * 5. Verifies operation types match expected sequence
     * 6. Queries all liquidity pools
     * 7. Queries specific pool by ID (hex and strkey)
     * 8. Queries pools by reserve assets
     * 9. Creates new accounts and executes path payment through pool
     * 10. Queries trades for the pool
     * 11. Tests forAccount query parameter
     * 12. Verifies all query results can be parsed correctly
     */
    @Test
    fun testLiquidityPoolQueries() = runTest(timeout = 180.seconds) {
        // First execute deposit and withdrawal to have data
        testWithdrawNonNative()

        delay(5000)

        // Query effects for liquidity pool
        val effectsPage = horizonServer.effects()
            .forLiquidityPool(nonNativeLiquidityPoolId)
            .limit(6)
            .order(RequestBuilder.Order.ASC)
            .execute()

        val effects = effectsPage.records
        assertEquals(6, effects.size, "Should have 6 effects")
        assertTrue(effects[0] is TrustlineCreatedEffectResponse, "First effect should be trustline created")
        assertTrue(effects[1] is LiquidityPoolCreatedEffectResponse, "Second effect should be pool created")
        assertTrue(effects[2] is LiquidityPoolDepositedEffectResponse, "Third effect should be deposit")
        assertTrue(effects[3] is LiquidityPoolDepositedEffectResponse, "Fourth effect should be deposit")
        assertTrue(effects[4] is LiquidityPoolWithdrewEffectResponse, "Fifth effect should be withdrawal")
        assertTrue(effects[5] is LiquidityPoolWithdrewEffectResponse, "Sixth effect should be withdrawal")

        // Query transactions for liquidity pool
        val transactionsPage = horizonServer.transactions()
            .forLiquidityPool(nonNativeLiquidityPoolId)
            .limit(1)
            .order(RequestBuilder.Order.DESC)
            .execute()

        assertEquals(1, transactionsPage.records.size, "Should have 1 transaction")
        val transaction = transactionsPage.records.first()

        // Query effects for transaction
        val txEffectsPage = horizonServer.effects()
            .forTransaction(transaction.hash)
            .limit(3)
            .order(RequestBuilder.Order.ASC)
            .execute()

        assertTrue(txEffectsPage.records.isNotEmpty(), "Should have effects for transaction")

        // Query operations for liquidity pool
        val operationsPage = horizonServer.operations()
            .forLiquidityPool(nonNativeLiquidityPoolId)
            .limit(5)
            .order(RequestBuilder.Order.ASC)
            .execute()

        val operations = operationsPage.records
        assertEquals(5, operations.size, "Should have 5 operations")
        assertTrue(operations[0] is ChangeTrustOperationResponse, "First operation should be ChangeTrust")
        assertTrue(operations[1] is LiquidityPoolDepositOperationResponse, "Second operation should be deposit")
        assertTrue(operations[2] is LiquidityPoolDepositOperationResponse, "Third operation should be deposit")
        assertTrue(operations[3] is LiquidityPoolWithdrawOperationResponse, "Fourth operation should be withdrawal")
        assertTrue(operations[4] is LiquidityPoolWithdrawOperationResponse, "Fifth operation should be withdrawal")

        // Query all liquidity pools
        val allPoolsPage = horizonServer.liquidityPools()
            .limit(4)
            .order(RequestBuilder.Order.ASC)
            .execute()

        assertTrue(allPoolsPage.records.size >= 2, "Should have at least 2 pools")

        // Query specific pool by ID
        var pool = horizonServer.liquidityPools().liquidityPool(nonNativeLiquidityPoolId)
        assertEquals(30, pool.feeBp, "Pool fee should be 30 basis points")
        assertEquals(nonNativeLiquidityPoolId, pool.id, "Pool ID should match")

        // If pool ID is hex, also test with strkey
        if (!nonNativeLiquidityPoolId.startsWith("L")) {
            val poolIdBytes = Util.hexToBytes(nonNativeLiquidityPoolId)
            val strKey = StrKey.encodeLiquidityPool(poolIdBytes)

            // Decode strkey back to hex
            val decodedBytes = StrKey.decodeLiquidityPool(strKey)
            val hexFromStrKey = Util.bytesToHex(decodedBytes).lowercase()

            pool = horizonServer.liquidityPools().liquidityPool(hexFromStrKey)
            assertEquals(30, pool.feeBp, "Pool fee should be 30 basis points")
            assertEquals(nonNativeLiquidityPoolId, pool.id, "Pool ID should match")
        }

        // Query pools by reserve assets
        val poolsByReservesPage = horizonServer.liquidityPools()
            .forReserves(
                "${assetA.code}:${assetA.issuer}",
                "${assetB.code}:${assetB.issuer}"
            )
            .limit(4)
            .order(RequestBuilder.Order.ASC)
            .execute()

        assertTrue(poolsByReservesPage.records.isNotEmpty(), "Should find pools by reserves")
        assertEquals(nonNativeLiquidityPoolId, poolsByReservesPage.records.first().id, "Should find our pool")

        // Create new accounts for path payment test
        val accXKeyPair = KeyPair.random()
        val accYKeyPair = KeyPair.random()
        val accXId = accXKeyPair.getAccountId()
        val accYId = accYKeyPair.getAccountId()

        if (testOn == "testnet") {
            FriendBot.fundTestnetAccount(accXId)
            FriendBot.fundTestnetAccount(accYId)
        } else {
            FriendBot.fundFuturenetAccount(accXId)
            FriendBot.fundFuturenetAccount(accYId)
        }

        delay(5000)

        // Create trustlines for new accounts
        val changeTrustOp1 = ChangeTrustOperation(
            asset = assetA,
            limit = ChangeTrustOperation.MAX_LIMIT
        )
        changeTrustOp1.sourceAccount = accXId

        val changeTrustOp2 = ChangeTrustOperation(
            asset = assetB,
            limit = ChangeTrustOperation.MAX_LIMIT
        )
        changeTrustOp2.sourceAccount = accYId

        val accX = horizonServer.accounts().account(accXId)
        var tx = TransactionBuilder(
            sourceAccount = Account(accXId, accX.sequenceNumber),
            network = network
        )
            .addOperation(changeTrustOp1)
            .addOperation(changeTrustOp2)
            .setTimeout(TransactionPreconditions.TIMEOUT_INFINITE)
            .setBaseFee(AbstractTransaction.MIN_BASE_FEE)
            .build()

        tx.sign(accXKeyPair)
        tx.sign(accYKeyPair)
        var response = horizonServer.submitTransaction(tx.toEnvelopeXdrBase64())
        assertTrue(response.successful, "ChangeTrust operations should succeed")

        delay(3000)

        // Send assetA to accX
        val paymentOp = PaymentOperation(
            destination = accXId,
            asset = assetA,
            amount = "19999191"
        )
        paymentOp.sourceAccount = assetAIssuerKeyPair.getAccountId()

        val assetAIssuerAccount = horizonServer.accounts().account(assetAIssuerKeyPair.getAccountId())
        tx = TransactionBuilder(
            sourceAccount = Account(assetAIssuerKeyPair.getAccountId(), assetAIssuerAccount.sequenceNumber),
            network = network
        )
            .addOperation(paymentOp)
            .setTimeout(TransactionPreconditions.TIMEOUT_INFINITE)
            .setBaseFee(AbstractTransaction.MIN_BASE_FEE)
            .build()

        tx.sign(assetAIssuerKeyPair)
        response = horizonServer.submitTransaction(tx.toEnvelopeXdrBase64())
        assertTrue(response.successful, "Payment operation should succeed")

        delay(3000)

        // Execute path payment strict send through liquidity pool
        val pathPaymentOp = PathPaymentStrictSendOperation(
            sendAsset = assetA,
            sendAmount = "10",
            destination = accYId,
            destAsset = assetB,
            destMin = "1"
        )

        val accXReloaded = horizonServer.accounts().account(accXId)
        tx = TransactionBuilder(
            sourceAccount = Account(accXId, accXReloaded.sequenceNumber),
            network = network
        )
            .addOperation(pathPaymentOp)
            .setTimeout(TransactionPreconditions.TIMEOUT_INFINITE)
            .setBaseFee(AbstractTransaction.MIN_BASE_FEE)
            .build()

        tx.sign(accXKeyPair)
        response = horizonServer.submitTransaction(tx.toEnvelopeXdrBase64())
        assertTrue(response.successful, "Path payment should succeed")

        delay(3000)

        // Query trades for liquidity pool
        var tradesPage = horizonServer.trades()
            .forLiquidityPool(nonNativeLiquidityPoolId)
            .execute()

        assertTrue(tradesPage.records.isNotEmpty(), "Should have trades")
        val trade = tradesPage.records.first()
        assertNotNull(trade.baseLiquidityPoolId, "Trade should have pool ID")
        assertEquals(nonNativeLiquidityPoolId, trade.baseLiquidityPoolId!!, "Trade should reference pool")

        // If pool ID is hex, also test trades query with strkey
        if (!nonNativeLiquidityPoolId.startsWith("L")) {
            val poolIdBytes = Util.hexToBytes(nonNativeLiquidityPoolId)
            val strKey = StrKey.encodeLiquidityPool(poolIdBytes)

            // Decode strkey back to hex
            val decodedBytes = StrKey.decodeLiquidityPool(strKey)
            val hexFromStrKey = Util.bytesToHex(decodedBytes).lowercase()

            tradesPage = horizonServer.trades()
                .forLiquidityPool(hexFromStrKey)
                .execute()

            assertTrue(tradesPage.records.isNotEmpty(), "Should have trades with strkey")
            assertNotNull(tradesPage.records.first().baseLiquidityPoolId, "Trade should have pool ID")
            assertEquals(nonNativeLiquidityPoolId, tradesPage.records.first().baseLiquidityPoolId!!,
                "Trade should reference pool")
        }

        // Verify operations and effects can be parsed for new accounts
        val accXOperationsPage = horizonServer.operations().forAccount(accXId).execute()
        assertTrue(accXOperationsPage.records.isNotEmpty(), "Should have operations")

        val accXEffectsPage = horizonServer.effects().forAccount(accXId).execute()
        assertTrue(accXEffectsPage.records.isNotEmpty(), "Should have effects")
    }

    /**
     * Test forAccount query parameter for liquidity pools.
     *
     * This test:
     * 1. Creates pool trustlines for the test account
     * 2. Queries liquidity pools using forAccount parameter
     * 3. Verifies the response contains expected pools
     */
    @Test
    fun testForAccountQueryParameter() = runTest(timeout = 120.seconds) {
        // First create pool trustlines
        testCreatePoolShareTrustlineNonNative()
        testCreatePoolShareTrustlineNative()

        delay(3000)

        val testAccountId = testAccountKeyPair.getAccountId()

        // Execute the request with forAccount filter
        val poolsPage = horizonServer.liquidityPools()
            .forAccount(testAccountId)
            .limit(10)
            .execute()

        // The request should succeed and return pools for our account
        assertTrue(poolsPage.records.isNotEmpty(), "Should have pools for account")

        // Verify that the pools include our created pools
        val poolIds = poolsPage.records.map { it.id }
        val hasNonNativePool = poolIds.contains(nonNativeLiquidityPoolId)
        val hasNativePool = poolIds.contains(nativeLiquidityPoolId)

        assertTrue(
            hasNonNativePool || hasNativePool,
            "Should contain at least one of our pools"
        )
    }
}
