// Copyright 2026 Soneso. All rights reserved.
// Use of this source code is governed by a license that can be
// found in the LICENSE file.

package com.soneso.stellar.sdk.integrationTests.sep.sep08

import com.soneso.stellar.sdk.*
import com.soneso.stellar.sdk.horizon.HorizonServer
import com.soneso.stellar.sdk.integrationTests.realDelay
import com.soneso.stellar.sdk.sep.sep01.StellarToml
import com.soneso.stellar.sdk.sep.sep08.RegulatedAsset
import com.soneso.stellar.sdk.sep.sep08.Sep08PostActionResponse
import com.soneso.stellar.sdk.sep.sep08.Sep08PostTransactionResponse
import com.soneso.stellar.sdk.sep.sep08.Sep08Service
import com.soneso.stellar.sdk.xdr.AccountFlagsXdr
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.*
import kotlin.time.Duration.Companion.seconds

/**
 * Integration tests for SEP-8 Regulated Assets service.
 *
 * These tests validate SEP-8 functionality including:
 * - Authorization flag checking against live Stellar testnet accounts
 * - Stellar TOML regulated asset parsing
 * - Transaction approval flows using mock approval servers (all 5 response types)
 * - Request body validation for postTransaction and postAction
 * - Action URL response handling
 * - fromDomain construction with mock HTTP
 * - Realistic regulated transaction pattern (SetTrustLineFlags sandwich)
 *
 * Tests 1 and 2 interact with the live Stellar testnet (FriendBot funding,
 * SetOptions transactions). Tests 3-14 use mock HTTP clients and do not
 * require network access.
 *
 * Reference:
 * - SEP-8 Specification: https://github.com/stellar/stellar-protocol/blob/master/ecosystem/sep-0008.md
 */
class Sep08IntegrationTest {

    private val network = Network.TESTNET
    private val horizonUrl = "https://horizon-testnet.stellar.org"
    private val horizonServer = HorizonServer(horizonUrl)

    private val jsonConfig = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    /**
     * Shared TOML content used across multiple tests. Matches the pattern from
     * the Flutter and PHP SDKs with two regulated assets (GOAT, JACK) and one
     * non-regulated asset (NOP).
     */
    private val anchorToml = """
        # Sample stellar.toml
        VERSION="2.0.0"

        NETWORK_PASSPHRASE="Test SDF Network ; September 2015"
        WEB_AUTH_ENDPOINT="https://api.anchor.org/auth"
        TRANSFER_SERVER_SEP0024="http://api.stellar.org/transfer-sep24/"
        ANCHOR_QUOTE_SERVER="http://api.stellar.org/quotes-sep38/"
        SIGNING_KEY="GBWMCCC3NHSKLAOJDBKKYW7SSH2PFTTNVFKWSGLWGDLEBKLOVP5JLBBP"

        [[CURRENCIES]]
        code="GOAT"
        regulated=true
        approval_server="https://goat.io/tx_approve"
        approval_criteria="The goat approval server will ensure that transactions are compliant with NFO regulation"

        [[CURRENCIES]]
        code="NOP"
        issuer="GBWMCCC3NHSKLAOJDBKKYW7SSH2PFTTNVFKWSGLWGDLEBKLOVP5JLBBP"
        display_decimals=2

        [[CURRENCIES]]
        code="JACK"
        regulated=true
        approval_server="https://jack.io/tx_approve"
        approval_criteria="The jack approval server will ensure that transactions are compliant with NFO regulation"
    """.trimIndent()

    /**
     * Funds a testnet account via FriendBot and waits for the funding to settle.
     */
    private suspend fun fundAccount(accountId: String) {
        val funded = FriendBot.fundTestnetAccount(accountId)
        assertTrue(funded, "Account funding should succeed")
        realDelay(5000)
    }

    /**
     * Sets AUTH_REQUIRED and AUTH_REVOCABLE flags on an issuer account.
     *
     * Loads the account from Horizon, builds and signs a SetOptions transaction
     * with the combined flag value, submits it, and waits for processing.
     */
    private suspend fun setAuthFlags(issuerKeyPair: KeyPair) {
        val issuerId = issuerKeyPair.getAccountId()
        val issuerAccount = horizonServer.accounts().account(issuerId)

        val setFlagsValue = AccountFlagsXdr.AUTH_REQUIRED_FLAG.value or
            AccountFlagsXdr.AUTH_REVOCABLE_FLAG.value

        val transaction = TransactionBuilder(
            sourceAccount = Account(issuerId, issuerAccount.sequenceNumber),
            network = network
        )
            .addOperation(SetOptionsOperation(setFlags = setFlagsValue))
            .setTimeout(TransactionPreconditions.TIMEOUT_INFINITE)
            .setBaseFee(AbstractTransaction.MIN_BASE_FEE)
            .build()

        transaction.sign(issuerKeyPair)

        val response = horizonServer.submitTransaction(transaction.toEnvelopeXdrBase64())
        assertTrue(response.successful, "SetOptions transaction should succeed")

        realDelay(5000)
    }

    /**
     * Creates a Sep08Service instance using a mock HTTP client.
     *
     * The returned service has an empty regulated assets list and empty TOML data.
     * It is intended for testing postTransaction and postAction flows where the
     * TOML content is irrelevant.
     */
    private fun createServiceWithMockClient(mockClient: HttpClient): Sep08Service {
        val emptyToml = StellarToml.parse("")
        return Sep08Service(
            tomlData = emptyToml,
            regulatedAssets = emptyList(),
            network = network,
            horizonServer = horizonServer,
            httpClient = mockClient
        )
    }

    /**
     * Creates a mock HttpClient with ContentNegotiation installed.
     */
    private fun createMockHttpClient(mockEngine: MockEngine): HttpClient {
        return HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json(jsonConfig)
            }
        }
    }

    /**
     * Builds a canonical SEP-8 regulated transaction with the approval server
     * sandwich pattern:
     * - Op 1: SetTrustLineFlags - authorize the trustor for the regulated asset
     * - Op 2: ManageBuyOffer - the user's actual operation
     * - Op 3: SetTrustLineFlags - set trustor to AUTHORIZED_TO_MAINTAIN_LIABILITIES
     *
     * This is the pattern used by the Flutter and PHP SDK integration tests.
     *
     * @param sourceAccountId The source account for the transaction
     * @param sourceSequence The current sequence number of the source account
     * @param trustorId The account whose trustline flags are being modified
     * @param regulatedAsset The regulated asset for the operations
     * @param issuerId The issuer account ID (source for SetTrustLineFlags ops)
     * @return The built (unsigned) Transaction
     */
    private fun buildRegulatedTransaction(
        sourceAccountId: String,
        sourceSequence: Long,
        trustorId: String,
        regulatedAsset: Asset,
        issuerId: String
    ): Transaction {
        // Op 1: Issuer fully authorizes the trustor for the regulated asset
        val op1 = SetTrustLineFlagsOperation(
            trustor = trustorId,
            asset = regulatedAsset,
            clearFlags = 0,
            setFlags = SetTrustLineFlagsOperation.AUTHORIZED_FLAG
        ).apply { sourceAccount = issuerId }

        // Op 2: The user's actual operation (buy the regulated asset)
        val op2 = ManageBuyOfferOperation(
            selling = AssetTypeNative,
            buying = regulatedAsset,
            buyAmount = "10",
            price = Price(1, 10) // 0.1
        )

        // Op 3: Issuer sets trustor to AUTHORIZED_TO_MAINTAIN_LIABILITIES
        val op3 = SetTrustLineFlagsOperation(
            trustor = trustorId,
            asset = regulatedAsset,
            clearFlags = SetTrustLineFlagsOperation.AUTHORIZED_FLAG,
            setFlags = SetTrustLineFlagsOperation.AUTHORIZED_TO_MAINTAIN_LIABILITIES_FLAG
        ).apply { sourceAccount = issuerId }

        return TransactionBuilder(
            sourceAccount = Account(sourceAccountId, sourceSequence),
            network = network
        )
            .addOperation(op1)
            .addOperation(op2)
            .addOperation(op3)
            .setTimeout(TransactionPreconditions.TIMEOUT_INFINITE)
            .setBaseFee(AbstractTransaction.MIN_BASE_FEE)
            .build()
    }

    // ==================== Live Testnet Tests ====================

    /**
     * Verifies that authorizationRequired returns true for an issuer account
     * with AUTH_REQUIRED and AUTH_REVOCABLE flags set.
     *
     * Steps:
     * 1. Generate random keypair for issuer
     * 2. Fund via FriendBot
     * 3. Set AUTH_REQUIRED + AUTH_REVOCABLE flags via SetOptionsOperation
     * 4. Create RegulatedAsset referencing the issuer
     * 5. Call authorizationRequired and assert it returns true
     */
    @Test
    fun testAuthorizationRequiredWithRegulatedIssuer() = runTest(timeout = 120.seconds) {
        val issuerKeyPair = KeyPair.random()
        val issuerId = issuerKeyPair.getAccountId()

        println("Issuer account: $issuerId")

        fundAccount(issuerId)
        setAuthFlags(issuerKeyPair)

        // Verify flags were set on the account
        val updatedAccount = horizonServer.accounts().account(issuerId)
        assertTrue(updatedAccount.flags.authRequired, "AUTH_REQUIRED should be set")
        assertTrue(updatedAccount.flags.authRevocable, "AUTH_REVOCABLE should be set")

        val regulatedAsset = RegulatedAsset(
            code = "REG",
            issuer = issuerId,
            approvalServer = "https://example.com/tx_approve"
        )

        val emptyToml = StellarToml.parse("")
        val sep08Service = Sep08Service(
            tomlData = emptyToml,
            regulatedAssets = listOf(regulatedAsset),
            network = network,
            horizonServer = horizonServer
        )

        val result = sep08Service.authorizationRequired(regulatedAsset)
        assertTrue(result, "authorizationRequired should return true for regulated issuer")

        println("authorizationRequired correctly returned true for regulated issuer")
    }

    /**
     * Verifies that authorizationRequired returns false for an account
     * without authorization flags.
     *
     * Steps:
     * 1. Generate random keypair
     * 2. Fund via FriendBot (no auth flags set)
     * 3. Create RegulatedAsset referencing the account as issuer
     * 4. Call authorizationRequired and assert it returns false
     */
    @Test
    fun testAuthorizationNotRequiredWithoutFlags() = runTest(timeout = 60.seconds) {
        val keyPair = KeyPair.random()
        val accountId = keyPair.getAccountId()

        println("Account without flags: $accountId")

        fundAccount(accountId)

        // Verify flags are not set
        val account = horizonServer.accounts().account(accountId)
        assertFalse(account.flags.authRequired, "AUTH_REQUIRED should not be set")
        assertFalse(account.flags.authRevocable, "AUTH_REVOCABLE should not be set")

        val regulatedAsset = RegulatedAsset(
            code = "TEST",
            issuer = accountId,
            approvalServer = "https://example.com/tx_approve"
        )

        val emptyToml = StellarToml.parse("")
        val sep08Service = Sep08Service(
            tomlData = emptyToml,
            regulatedAssets = listOf(regulatedAsset),
            network = network,
            horizonServer = horizonServer
        )

        val result = sep08Service.authorizationRequired(regulatedAsset)
        assertFalse(result, "authorizationRequired should return false without auth flags")

        println("authorizationRequired correctly returned false for unregulated issuer")
    }

    // ==================== TOML Parsing Test ====================

    /**
     * Verifies that regulated asset entries are correctly parsed from stellar.toml
     * content and that Sep08Service can be constructed from the parsed data.
     *
     * Steps:
     * 1. Create a TOML string with regulated and non-regulated currencies
     * 2. Parse with StellarToml.parse()
     * 3. Verify currency fields (regulated, approvalServer, approvalCriteria)
     * 4. Create Sep08Service from the parsed TOML data
     * 5. Verify regulatedAssets list contains the correct entries
     */
    @Test
    fun testStellarTomlRegulatedAssetParsing() = runTest {
        val toml = """
            NETWORK_PASSPHRASE = "Test SDF Network ; September 2015"
            HORIZON_URL = "https://horizon-testnet.stellar.org"

            [[CURRENCIES]]
            code = "GOAT"
            issuer = "GDUKMGUGDZQK6YHYA5Z6AY2G4XDSZPSZ3SW5UN3ARVMO6QSRDWP5YLEX"
            regulated = true
            approval_server = "https://goat.example.com/tx_approve"
            approval_criteria = "Transactions must be below 500 GOAT per account per day"
            status = "live"
            name = "Goat Token"

            [[CURRENCIES]]
            code = "USD"
            issuer = "GCZJM35NKGVK47BB4SPBDV25477PBER2BAJVZL5A6BQFTDWCYUSEROCI"
            status = "live"
            name = "US Dollar"

            [[CURRENCIES]]
            code = "REG2"
            issuer = "GDUKMGUGDZQK6YHYA5Z6AY2G4XDSZPSZ3SW5UN3ARVMO6QSRDWP5YLEX"
            regulated = true
            approval_server = "https://reg2.example.com/approve"
            status = "test"
            name = "Regulated Token Two"
        """.trimIndent()

        val stellarToml = StellarToml.parse(toml)

        // Verify all currencies were parsed
        assertNotNull(stellarToml.currencies, "Currencies should be parsed")
        assertEquals(3, stellarToml.currencies!!.size, "Should have 3 currencies")

        // Verify GOAT (regulated)
        val goat = stellarToml.currencies!![0]
        assertEquals("GOAT", goat.code)
        assertEquals("GDUKMGUGDZQK6YHYA5Z6AY2G4XDSZPSZ3SW5UN3ARVMO6QSRDWP5YLEX", goat.issuer)
        assertTrue(goat.regulated == true, "GOAT should be regulated")
        assertEquals("https://goat.example.com/tx_approve", goat.approvalServer)
        assertEquals("Transactions must be below 500 GOAT per account per day", goat.approvalCriteria)

        // Verify USD (not regulated)
        val usd = stellarToml.currencies!![1]
        assertEquals("USD", usd.code)
        assertNull(usd.regulated, "USD should not have regulated flag")
        assertNull(usd.approvalServer, "USD should not have approval server")
        assertNull(usd.approvalCriteria, "USD should not have approval criteria")

        // Verify REG2 (regulated, no criteria)
        val reg2 = stellarToml.currencies!![2]
        assertEquals("REG2", reg2.code)
        assertTrue(reg2.regulated == true, "REG2 should be regulated")
        assertEquals("https://reg2.example.com/approve", reg2.approvalServer)
        assertNull(reg2.approvalCriteria, "REG2 should not have approval criteria")

        // Create Sep08Service from parsed TOML with explicit network and horizonUrl
        val regulatedAssets = stellarToml.currencies!!
            .filter { it.regulated == true && it.code != null && it.issuer != null && it.approvalServer != null }
            .map { currency ->
                RegulatedAsset(
                    code = currency.code!!,
                    issuer = currency.issuer!!,
                    approvalServer = currency.approvalServer!!,
                    approvalCriteria = currency.approvalCriteria
                )
            }

        val sep08Service = Sep08Service(
            tomlData = stellarToml,
            regulatedAssets = regulatedAssets,
            network = network,
            horizonServer = horizonServer
        )

        assertEquals(2, sep08Service.regulatedAssets.size, "Should have 2 regulated assets")

        val goatAsset = sep08Service.regulatedAssets[0]
        assertEquals("GOAT", goatAsset.code)
        assertEquals("GDUKMGUGDZQK6YHYA5Z6AY2G4XDSZPSZ3SW5UN3ARVMO6QSRDWP5YLEX", goatAsset.issuer)
        assertEquals("https://goat.example.com/tx_approve", goatAsset.approvalServer)
        assertEquals("Transactions must be below 500 GOAT per account per day", goatAsset.approvalCriteria)

        val reg2Asset = sep08Service.regulatedAssets[1]
        assertEquals("REG2", reg2Asset.code)
        assertEquals("https://reg2.example.com/approve", reg2Asset.approvalServer)
        assertNull(reg2Asset.approvalCriteria)

        println("TOML parsing validated: ${sep08Service.regulatedAssets.size} regulated assets found")
    }

    // ==================== Mock Approval Server Tests ====================

    /**
     * Verifies that postTransaction correctly parses a "success" response
     * from the approval server using a realistic regulated transaction with
     * the SetTrustLineFlags sandwich pattern.
     *
     * Steps:
     * 1. Fund accounts for the issuer and user
     * 2. Set AUTH_REQUIRED + AUTH_REVOCABLE flags on the issuer
     * 3. Build a regulated transaction (authorize, buy offer, deauthorize)
     * 4. Create a mock HTTP client returning a success response
     * 5. Verify the request body contains the "tx" field with correct XDR
     * 6. Verify response is Sep08PostTransactionResponse.Success
     */
    @Test
    fun testPostTransactionWithMockApprovalServer() = runTest(timeout = 120.seconds) {
        val issuerKeyPair = KeyPair.random()
        val userKeyPair = KeyPair.random()
        val issuerId = issuerKeyPair.getAccountId()
        val userId = userKeyPair.getAccountId()

        fundAccount(issuerId)
        fundAccount(userId)
        setAuthFlags(issuerKeyPair)

        val regulatedAsset = AssetTypeCreditAlphaNum4("GOAT", issuerId)

        // Build the canonical SEP-8 sandwich transaction
        val userAccount = horizonServer.accounts().account(userId)
        val transaction = buildRegulatedTransaction(
            sourceAccountId = userId,
            sourceSequence = userAccount.sequenceNumber,
            trustorId = userId,
            regulatedAsset = regulatedAsset,
            issuerId = issuerId
        )
        val txXdr = transaction.toEnvelopeXdrBase64()

        // Capture and verify the request body
        var capturedRequestBody: String? = null
        var capturedMethod: HttpMethod? = null
        val mockEngine = MockEngine { request ->
            capturedMethod = request.method
            capturedRequestBody = request.body.toByteArray().decodeToString()
            respond(
                content = """{"status": "success", "tx": "$txXdr", "message": "Transaction approved"}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val mockClient = createMockHttpClient(mockEngine)

        val sep08Service = createServiceWithMockClient(mockClient)
        val response = sep08Service.postTransaction(txXdr, "https://goat.io/tx_approve")

        // Verify request
        assertEquals(HttpMethod.Post, capturedMethod, "Request method should be POST")
        assertNotNull(capturedRequestBody, "Request body should not be null")
        assertTrue(capturedRequestBody!!.contains("\"tx\""), "Request body should contain 'tx' field")
        assertTrue(capturedRequestBody!!.contains(txXdr), "Request body should contain the transaction XDR")

        // Verify response
        assertTrue(response is Sep08PostTransactionResponse.Success, "Response should be Success")
        val success = response as Sep08PostTransactionResponse.Success
        assertEquals(txXdr, success.tx, "Approved tx should match")
        assertEquals("Transaction approved", success.message)

        println("postTransaction success flow verified with regulated transaction pattern")
    }

    /**
     * Verifies that postTransaction correctly parses a "revised" response
     * from the approval server using a realistic regulated transaction.
     *
     * Steps:
     * 1. Fund accounts and set auth flags on issuer
     * 2. Build a regulated transaction (sandwich pattern)
     * 3. Create mock returning a revised response with modified tx
     * 4. Verify request body contains "tx" field
     * 5. Verify response is Sep08PostTransactionResponse.Revised
     */
    @Test
    fun testPostTransactionRevisedFlow() = runTest(timeout = 120.seconds) {
        val issuerKeyPair = KeyPair.random()
        val userKeyPair = KeyPair.random()
        val issuerId = issuerKeyPair.getAccountId()
        val userId = userKeyPair.getAccountId()

        fundAccount(issuerId)
        fundAccount(userId)
        setAuthFlags(issuerKeyPair)

        val regulatedAsset = AssetTypeCreditAlphaNum4("GOAT", issuerId)

        val userAccount = horizonServer.accounts().account(userId)
        val transaction = buildRegulatedTransaction(
            sourceAccountId = userId,
            sourceSequence = userAccount.sequenceNumber,
            trustorId = userId,
            regulatedAsset = regulatedAsset,
            issuerId = issuerId
        )
        val txXdr = transaction.toEnvelopeXdrBase64()

        // Mock returns "revised" with a modified tx (concatenated to simulate modification)
        val revisedTxXdr = txXdr + txXdr
        var capturedRequestBody: String? = null
        val mockEngine = MockEngine { request ->
            capturedRequestBody = request.body.toByteArray().decodeToString()
            respond(
                content = """{"status": "revised", "tx": "$revisedTxXdr", "message": "Compliance operations added by approval server"}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val mockClient = createMockHttpClient(mockEngine)

        val sep08Service = createServiceWithMockClient(mockClient)
        val response = sep08Service.postTransaction(txXdr, "https://goat.io/tx_approve")

        // Verify request body
        assertNotNull(capturedRequestBody, "Request body should not be null")
        assertTrue(capturedRequestBody!!.contains("\"tx\""), "Request body should contain 'tx' field")
        assertTrue(capturedRequestBody!!.contains(txXdr), "Request body should contain the transaction XDR")

        // Verify response
        assertTrue(response is Sep08PostTransactionResponse.Revised, "Response should be Revised")
        val revised = response as Sep08PostTransactionResponse.Revised
        assertEquals(revisedTxXdr, revised.tx, "Revised tx should match")
        assertEquals("Compliance operations added by approval server", revised.message)

        println("postTransaction revised flow verified")
    }

    /**
     * Verifies that postTransaction correctly parses a "pending" response
     * from the approval server.
     *
     * The approval server returns HTTP 200 with status "pending", a timeout value,
     * and a message. This indicates the server needs more time to evaluate the
     * transaction and the client should resubmit after the timeout period.
     *
     * Steps:
     * 1. Fund accounts and set auth flags on issuer
     * 2. Build a regulated transaction (sandwich pattern)
     * 3. Create mock returning a pending response with timeout=3
     * 4. Verify request body contains "tx" field
     * 5. Verify response is Sep08PostTransactionResponse.Pending with correct fields
     */
    @Test
    fun testPostTransactionPendingFlow() = runTest(timeout = 120.seconds) {
        val issuerKeyPair = KeyPair.random()
        val userKeyPair = KeyPair.random()
        val issuerId = issuerKeyPair.getAccountId()
        val userId = userKeyPair.getAccountId()

        fundAccount(issuerId)
        fundAccount(userId)
        setAuthFlags(issuerKeyPair)

        val regulatedAsset = AssetTypeCreditAlphaNum4("GOAT", issuerId)

        val userAccount = horizonServer.accounts().account(userId)
        val transaction = buildRegulatedTransaction(
            sourceAccountId = userId,
            sourceSequence = userAccount.sequenceNumber,
            trustorId = userId,
            regulatedAsset = regulatedAsset,
            issuerId = issuerId
        )
        val txXdr = transaction.toEnvelopeXdrBase64()

        var capturedRequestBody: String? = null
        val mockEngine = MockEngine { request ->
            capturedRequestBody = request.body.toByteArray().decodeToString()
            respond(
                content = """{"status": "pending", "timeout": 3, "message": "Verifications are pending."}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val mockClient = createMockHttpClient(mockEngine)

        val sep08Service = createServiceWithMockClient(mockClient)
        val response = sep08Service.postTransaction(txXdr, "https://goat.io/tx_approve")

        // Verify request body
        assertNotNull(capturedRequestBody, "Request body should not be null")
        assertTrue(capturedRequestBody!!.contains("\"tx\""), "Request body should contain 'tx' field")
        assertTrue(capturedRequestBody!!.contains(txXdr), "Request body should contain the transaction XDR")

        // Verify response
        assertTrue(response is Sep08PostTransactionResponse.Pending, "Response should be Pending")
        val pending = response as Sep08PostTransactionResponse.Pending
        assertEquals(3, pending.timeout, "Timeout should be 3 milliseconds")
        assertEquals("Verifications are pending.", pending.message)

        println("postTransaction pending flow verified")
        println("  Timeout: ${pending.timeout}")
        println("  Message: ${pending.message}")
    }

    /**
     * Verifies that postTransaction correctly parses a "rejected" response
     * from the approval server.
     *
     * The approval server returns HTTP 400 with status "rejected" and an error
     * message. This indicates the transaction cannot be approved.
     *
     * Steps:
     * 1. Fund accounts and set auth flags on issuer
     * 2. Build a regulated transaction (sandwich pattern)
     * 3. Create mock returning HTTP 400 with rejected status
     * 4. Verify request body contains "tx" field
     * 5. Verify response is Sep08PostTransactionResponse.Rejected with error
     */
    @Test
    fun testPostTransactionRejectedFlow() = runTest(timeout = 120.seconds) {
        val issuerKeyPair = KeyPair.random()
        val userKeyPair = KeyPair.random()
        val issuerId = issuerKeyPair.getAccountId()
        val userId = userKeyPair.getAccountId()

        fundAccount(issuerId)
        fundAccount(userId)
        setAuthFlags(issuerKeyPair)

        val regulatedAsset = AssetTypeCreditAlphaNum4("GOAT", issuerId)

        val userAccount = horizonServer.accounts().account(userId)
        val transaction = buildRegulatedTransaction(
            sourceAccountId = userId,
            sourceSequence = userAccount.sequenceNumber,
            trustorId = userId,
            regulatedAsset = regulatedAsset,
            issuerId = issuerId
        )
        val txXdr = transaction.toEnvelopeXdrBase64()

        var capturedRequestBody: String? = null
        val mockEngine = MockEngine { request ->
            capturedRequestBody = request.body.toByteArray().decodeToString()
            respond(
                content = """{"status": "rejected", "error": "Destination account is blocked."}""",
                status = HttpStatusCode.BadRequest,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val mockClient = createMockHttpClient(mockEngine)

        val sep08Service = createServiceWithMockClient(mockClient)
        val response = sep08Service.postTransaction(txXdr, "https://goat.io/tx_approve")

        // Verify request body
        assertNotNull(capturedRequestBody, "Request body should not be null")
        assertTrue(capturedRequestBody!!.contains("\"tx\""), "Request body should contain 'tx' field")
        assertTrue(capturedRequestBody!!.contains(txXdr), "Request body should contain the transaction XDR")

        // Verify response
        assertTrue(response is Sep08PostTransactionResponse.Rejected, "Response should be Rejected")
        val rejected = response as Sep08PostTransactionResponse.Rejected
        assertEquals("Destination account is blocked.", rejected.error)

        println("postTransaction rejected flow verified")
        println("  Error: ${rejected.error}")
    }

    /**
     * Verifies that postTransaction correctly parses an "action_required" response
     * from the approval server.
     *
     * The approval server returns HTTP 200 with status "action_required", an action URL,
     * action method, action fields, and a message. This indicates the user must complete
     * an action (e.g., KYC) before the transaction can be approved.
     *
     * Steps:
     * 1. Fund accounts and set auth flags on issuer
     * 2. Build a regulated transaction (sandwich pattern)
     * 3. Create mock returning action_required with URL, method, and fields
     * 4. Verify request body contains "tx" field
     * 5. Verify response is Sep08PostTransactionResponse.ActionRequired with all fields
     */
    @Test
    fun testPostTransactionActionRequiredFlow() = runTest(timeout = 120.seconds) {
        val issuerKeyPair = KeyPair.random()
        val userKeyPair = KeyPair.random()
        val issuerId = issuerKeyPair.getAccountId()
        val userId = userKeyPair.getAccountId()

        fundAccount(issuerId)
        fundAccount(userId)
        setAuthFlags(issuerKeyPair)

        val regulatedAsset = AssetTypeCreditAlphaNum4("GOAT", issuerId)

        val userAccount = horizonServer.accounts().account(userId)
        val transaction = buildRegulatedTransaction(
            sourceAccountId = userId,
            sourceSequence = userAccount.sequenceNumber,
            trustorId = userId,
            regulatedAsset = regulatedAsset,
            issuerId = issuerId
        )
        val txXdr = transaction.toEnvelopeXdrBase64()

        val actionUrl = "https://goat.io/kyc?account=$userId"
        var capturedRequestBody: String? = null
        val mockEngine = MockEngine { request ->
            capturedRequestBody = request.body.toByteArray().decodeToString()
            respond(
                content = """{
                    "status": "action_required",
                    "message": "Please provide your email address and mobile number.",
                    "action_url": "$actionUrl",
                    "action_method": "POST",
                    "action_fields": ["email_address", "mobile_number"]
                }""".trimIndent(),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val mockClient = createMockHttpClient(mockEngine)

        val sep08Service = createServiceWithMockClient(mockClient)
        val response = sep08Service.postTransaction(txXdr, "https://goat.io/tx_approve")

        // Verify request body
        assertNotNull(capturedRequestBody, "Request body should not be null")
        assertTrue(capturedRequestBody!!.contains("\"tx\""), "Request body should contain 'tx' field")
        assertTrue(capturedRequestBody!!.contains(txXdr), "Request body should contain the transaction XDR")

        // Verify response
        assertTrue(
            response is Sep08PostTransactionResponse.ActionRequired,
            "Response should be ActionRequired"
        )
        val actionRequired = response as Sep08PostTransactionResponse.ActionRequired
        assertEquals(
            "Please provide your email address and mobile number.",
            actionRequired.message
        )
        assertEquals(actionUrl, actionRequired.actionUrl)
        assertEquals("POST", actionRequired.actionMethod)
        assertNotNull(actionRequired.actionFields, "Action fields should be present")
        assertTrue(
            actionRequired.actionFields!!.contains("email_address"),
            "Action fields should contain email_address"
        )
        assertTrue(
            actionRequired.actionFields!!.contains("mobile_number"),
            "Action fields should contain mobile_number"
        )

        println("postTransaction action_required flow verified")
        println("  Message: ${actionRequired.message}")
        println("  Action URL: ${actionRequired.actionUrl}")
        println("  Action method: ${actionRequired.actionMethod}")
        println("  Action fields: ${actionRequired.actionFields}")
    }

    /**
     * Verifies that postAction correctly parses a "no_further_action_required"
     * response and validates the request body contains the action fields.
     *
     * Steps:
     * 1. Create mock client that captures the request body
     * 2. Call postAction with action fields (email, mobile)
     * 3. Verify request method is POST
     * 4. Verify request body contains email_address and mobile_number
     * 5. Verify response is Sep08PostActionResponse.Done
     */
    @Test
    fun testPostActionDoneWithRequestBodyValidation() = runTest {
        var capturedMethod: HttpMethod? = null
        var capturedRequestBody: String? = null
        val mockEngine = MockEngine { request ->
            capturedMethod = request.method
            capturedRequestBody = request.body.toByteArray().decodeToString()
            respond(
                content = """{"result": "no_further_action_required"}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val mockClient = createMockHttpClient(mockEngine)

        val service = createServiceWithMockClient(mockClient)
        val response = service.postAction(
            url = "https://goat.io/action",
            actionFields = mapOf(
                "email_address" to "test@mail.com",
                "mobile_number" to "+3472829839222"
            )
        )

        // Verify request
        assertEquals(HttpMethod.Post, capturedMethod, "Request method should be POST")
        assertNotNull(capturedRequestBody, "Request body should not be null")
        assertTrue(
            capturedRequestBody!!.contains("test@mail.com"),
            "Request body should contain email_address value"
        )
        assertTrue(
            capturedRequestBody!!.contains("+3472829839222"),
            "Request body should contain mobile_number value"
        )

        // Verify response
        assertTrue(response is Sep08PostActionResponse.Done, "Response should be Done")

        println("postAction 'no_further_action_required' flow verified with request body validation")
    }

    /**
     * Verifies that postAction correctly parses a "follow_next_url" response
     * and validates the request body.
     *
     * Steps:
     * 1. Create mock client returning follow_next_url with URL and message
     * 2. Call postAction with action fields
     * 3. Verify request body contains the action fields
     * 4. Verify response is Sep08PostActionResponse.NextUrl with correct fields
     */
    @Test
    fun testPostActionNextUrlWithRequestBodyValidation() = runTest {
        val actionUrl = "https://goat.io/action"
        var capturedMethod: HttpMethod? = null
        var capturedRequestBody: String? = null
        val mockEngine = MockEngine { request ->
            capturedMethod = request.method
            capturedRequestBody = request.body.toByteArray().decodeToString()
            respond(
                content = """{
                    "result": "follow_next_url",
                    "next_url": "$actionUrl",
                    "message": "Please submit mobile number"
                }""".trimIndent(),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val mockClient = createMockHttpClient(mockEngine)

        val service = createServiceWithMockClient(mockClient)
        val response = service.postAction(
            url = actionUrl,
            actionFields = mapOf("email_address" to "test@mail.com")
        )

        // Verify request
        assertEquals(HttpMethod.Post, capturedMethod, "Request method should be POST")
        assertNotNull(capturedRequestBody, "Request body should not be null")
        assertTrue(
            capturedRequestBody!!.contains("test@mail.com"),
            "Request body should contain email_address value"
        )

        // Verify response
        assertTrue(response is Sep08PostActionResponse.NextUrl, "Response should be NextUrl")
        val nextUrl = response as Sep08PostActionResponse.NextUrl
        assertEquals(actionUrl, nextUrl.nextUrl)
        assertEquals("Please submit mobile number", nextUrl.message)

        println("postAction 'follow_next_url' flow verified with request body validation")
        println("  Next URL: ${nextUrl.nextUrl}")
        println("  Message: ${nextUrl.message}")
    }

    // ==================== fromDomain Tests ====================

    /**
     * Verifies that Sep08Service.fromDomain correctly fetches and parses
     * the stellar.toml from a domain, resolves the network passphrase, and
     * extracts regulated assets.
     *
     * Since the TOML currencies lack an issuer field, no regulated assets
     * will be extracted (matching Flutter/PHP behavior). The network passphrase
     * should be resolved from the TOML.
     *
     * Steps:
     * 1. Create mock HTTP client that serves stellar.toml at the well-known path
     * 2. Call Sep08Service.fromDomain with the mock client
     * 3. Verify the network passphrase matches testnet
     * 4. Verify regulated assets list is empty (currencies have no issuer)
     */
    @Test
    fun testFromDomainWithMockHttpClient() = runTest {
        val mockEngine = MockEngine { request ->
            if (request.url.toString().contains("api.anchor.org") &&
                request.url.toString().contains("stellar.toml")
            ) {
                respond(
                    content = anchorToml,
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "text/plain")
                )
            } else {
                respond(
                    content = """{"error": "Bad request"}""",
                    status = HttpStatusCode.BadRequest,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            }
        }

        val mockClient = HttpClient(mockEngine)

        val service = Sep08Service.fromDomain(
            domain = "api.anchor.org",
            httpClient = mockClient
        )

        // Verify network passphrase was resolved from TOML
        assertEquals(
            Network.TESTNET.networkPassphrase,
            service.tomlData.generalInformation.networkPassphrase,
            "Network passphrase in TOML should match testnet"
        )

        // Currencies in the TOML have no issuer field, so no regulated assets are extracted
        assertEquals(
            0,
            service.regulatedAssets.size,
            "Should have 0 regulated assets (currencies lack issuer field)"
        )

        println("fromDomain test verified: network resolved, ${service.regulatedAssets.size} regulated assets")
    }

    /**
     * Verifies that Sep08Service.fromDomain correctly extracts regulated assets
     * when the stellar.toml currencies include issuer fields.
     *
     * Steps:
     * 1. Create a TOML with fully-specified regulated currencies (including issuers)
     * 2. Serve it via mock HTTP client
     * 3. Call Sep08Service.fromDomain
     * 4. Verify regulated assets are correctly extracted
     * 5. Verify network passphrase propagation
     */
    @Test
    fun testFromDomainWithRegulatedAssetsAndNetworkPropagation() = runTest {
        val tomlWithIssuers = """
            NETWORK_PASSPHRASE="Test SDF Network ; September 2015"
            HORIZON_URL="https://horizon-testnet.stellar.org"

            [[CURRENCIES]]
            code="GOAT"
            issuer="GDUKMGUGDZQK6YHYA5Z6AY2G4XDSZPSZ3SW5UN3ARVMO6QSRDWP5YLEX"
            regulated=true
            approval_server="https://goat.io/tx_approve"
            approval_criteria="Goat approval criteria"

            [[CURRENCIES]]
            code="NOP"
            issuer="GBWMCCC3NHSKLAOJDBKKYW7SSH2PFTTNVFKWSGLWGDLEBKLOVP5JLBBP"
            display_decimals=2
        """.trimIndent()

        val mockEngine = MockEngine { request ->
            if (request.url.toString().contains("stellar.toml")) {
                respond(
                    content = tomlWithIssuers,
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "text/plain")
                )
            } else {
                respond(
                    content = """{"error": "Not found"}""",
                    status = HttpStatusCode.NotFound,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            }
        }

        val mockClient = HttpClient(mockEngine)

        val service = Sep08Service.fromDomain(
            domain = "example.com",
            httpClient = mockClient
        )

        // Verify network was resolved from TOML NETWORK_PASSPHRASE
        assertEquals(
            Network.TESTNET.networkPassphrase,
            service.tomlData.generalInformation.networkPassphrase,
            "Network passphrase in TOML should match testnet"
        )

        // Verify regulated assets were extracted
        assertEquals(1, service.regulatedAssets.size, "Should have 1 regulated asset")
        val goatAsset = service.regulatedAssets[0]
        assertEquals("GOAT", goatAsset.code)
        assertEquals("GDUKMGUGDZQK6YHYA5Z6AY2G4XDSZPSZ3SW5UN3ARVMO6QSRDWP5YLEX", goatAsset.issuer)
        assertEquals("https://goat.io/tx_approve", goatAsset.approvalServer)
        assertEquals("Goat approval criteria", goatAsset.approvalCriteria)

        println("fromDomain with regulated assets verified: ${service.regulatedAssets.size} assets found")
    }

    // ==================== Full Regulated Transaction Pattern Test ====================

    /**
     * Verifies the complete SEP-8 flow using the canonical regulated transaction
     * pattern (SetTrustLineFlags sandwich) with all five response types exercised
     * sequentially.
     *
     * This test mirrors the monolithic test pattern from the Flutter and PHP SDKs
     * but uses separate mock clients per response to maintain isolation.
     *
     * Steps:
     * 1. Fund issuer (with auth flags) and user accounts on testnet
     * 2. Build the regulated transaction (authorize, buy offer, deauthorize)
     * 3. Submit to mock approval server for each of the 5 response types
     * 4. Verify each response is correctly parsed
     * 5. Test postAction done and next_url responses
     */
    @Test
    fun testFullRegulatedTransactionFlowAllResponses() = runTest(timeout = 180.seconds) {
        val issuerKeyPair = KeyPair.random()
        val userKeyPair = KeyPair.random()
        val issuerId = issuerKeyPair.getAccountId()
        val userId = userKeyPair.getAccountId()

        println("Issuer: $issuerId")
        println("User: $userId")

        fundAccount(issuerId)
        fundAccount(userId)
        setAuthFlags(issuerKeyPair)

        val regulatedAsset = AssetTypeCreditAlphaNum4("GOAT", issuerId)

        val userAccount = horizonServer.accounts().account(userId)
        val transaction = buildRegulatedTransaction(
            sourceAccountId = userId,
            sourceSequence = userAccount.sequenceNumber,
            trustorId = userId,
            regulatedAsset = regulatedAsset,
            issuerId = issuerId
        )
        val txXdr = transaction.toEnvelopeXdrBase64()
        val actionUrl = "https://goat.io/action"

        // --- Success ---
        val successEngine = MockEngine { request ->
            respond(
                content = """{"status": "success", "tx": "$txXdr", "message": "hello"}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val successService = createServiceWithMockClient(createMockHttpClient(successEngine))
        val successResponse = successService.postTransaction(txXdr, "https://goat.io/tx_approve")
        assertTrue(successResponse is Sep08PostTransactionResponse.Success)
        val success = successResponse as Sep08PostTransactionResponse.Success
        assertEquals(txXdr, success.tx)
        assertEquals("hello", success.message)

        // --- Revised ---
        val revisedTxXdr = txXdr + txXdr
        val revisedEngine = MockEngine { request ->
            respond(
                content = """{"status": "revised", "tx": "$revisedTxXdr", "message": "hello"}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val revisedService = createServiceWithMockClient(createMockHttpClient(revisedEngine))
        val revisedResponse = revisedService.postTransaction(txXdr, "https://goat.io/tx_approve")
        assertTrue(revisedResponse is Sep08PostTransactionResponse.Revised)
        val revised = revisedResponse as Sep08PostTransactionResponse.Revised
        assertEquals(revisedTxXdr, revised.tx)
        assertEquals("hello", revised.message)

        // --- Pending ---
        val pendingEngine = MockEngine { request ->
            respond(
                content = """{"status": "pending", "timeout": 3, "message": "hello"}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val pendingService = createServiceWithMockClient(createMockHttpClient(pendingEngine))
        val pendingResponse = pendingService.postTransaction(txXdr, "https://goat.io/tx_approve")
        assertTrue(pendingResponse is Sep08PostTransactionResponse.Pending)
        val pending = pendingResponse as Sep08PostTransactionResponse.Pending
        assertEquals(3, pending.timeout)
        assertEquals("hello", pending.message)

        // --- Rejected ---
        val rejectedEngine = MockEngine { request ->
            respond(
                content = """{"status": "rejected", "error": "hello"}""",
                status = HttpStatusCode.BadRequest,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val rejectedService = createServiceWithMockClient(createMockHttpClient(rejectedEngine))
        val rejectedResponse = rejectedService.postTransaction(txXdr, "https://goat.io/tx_approve")
        assertTrue(rejectedResponse is Sep08PostTransactionResponse.Rejected)
        val rejected = rejectedResponse as Sep08PostTransactionResponse.Rejected
        assertEquals("hello", rejected.error)

        // --- Action Required ---
        val actionRequiredEngine = MockEngine { request ->
            respond(
                content = """{
                    "status": "action_required",
                    "message": "hello",
                    "action_url": "$actionUrl",
                    "action_method": "POST",
                    "action_fields": ["email_address", "mobile_number"]
                }""".trimIndent(),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val actionRequiredService = createServiceWithMockClient(createMockHttpClient(actionRequiredEngine))
        val actionRequiredResponse = actionRequiredService.postTransaction(
            txXdr, "https://goat.io/tx_approve"
        )
        assertTrue(actionRequiredResponse is Sep08PostTransactionResponse.ActionRequired)
        val actionRequired = actionRequiredResponse as Sep08PostTransactionResponse.ActionRequired
        assertEquals("hello", actionRequired.message)
        assertEquals(actionUrl, actionRequired.actionUrl)
        assertEquals("POST", actionRequired.actionMethod)
        assertTrue(actionRequired.actionFields!!.contains("email_address"))
        assertTrue(actionRequired.actionFields!!.contains("mobile_number"))

        // --- Post Action: Done ---
        val actionDoneEngine = MockEngine { request ->
            respond(
                content = """{"result": "no_further_action_required"}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val actionDoneService = createServiceWithMockClient(createMockHttpClient(actionDoneEngine))
        val actionDoneResponse = actionDoneService.postAction(
            url = actionUrl,
            actionFields = mapOf(
                "email_address" to "test@mail.com",
                "mobile_number" to "+3472829839222"
            )
        )
        assertTrue(actionDoneResponse is Sep08PostActionResponse.Done)

        // --- Post Action: Next URL ---
        val actionNextEngine = MockEngine { request ->
            respond(
                content = """{
                    "result": "follow_next_url",
                    "next_url": "$actionUrl",
                    "message": "Please submit mobile number"
                }""".trimIndent(),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val actionNextService = createServiceWithMockClient(createMockHttpClient(actionNextEngine))
        val actionNextResponse = actionNextService.postAction(
            url = actionUrl,
            actionFields = mapOf("email_address" to "test@mail.com")
        )
        assertTrue(actionNextResponse is Sep08PostActionResponse.NextUrl)
        val nextUrl = actionNextResponse as Sep08PostActionResponse.NextUrl
        assertEquals(actionUrl, nextUrl.nextUrl)
        assertEquals("Please submit mobile number", nextUrl.message)

        println("Full regulated transaction flow verified with all 5 response types + 2 action types")
    }
}
