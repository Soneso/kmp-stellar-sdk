//
//  TokenDecimalsTest.kt
//  Stellar SDK Kotlin Multiplatform
//
//  Copyright (c) 2026 Soneso. All rights reserved.
//

package com.soneso.stellar.sdk.unitTests.smartaccount

import com.ionspin.kotlin.bignum.integer.BigInteger
import com.soneso.stellar.sdk.KeyPair
import com.soneso.stellar.sdk.Network
import com.soneso.stellar.sdk.scval.Scv
import com.soneso.stellar.sdk.smartaccount.core.TransactionException
import com.soneso.stellar.sdk.smartaccount.core.ValidationException
import com.soneso.stellar.sdk.smartaccount.core.WalletException
import com.soneso.stellar.sdk.smartaccount.oz.OZSmartAccountConfig
import com.soneso.stellar.sdk.smartaccount.oz.OZSmartAccountKit
import com.soneso.stellar.sdk.smartaccount.oz.OZTransactionOperations
import com.soneso.stellar.sdk.rpc.SorobanServer
import com.soneso.stellar.sdk.xdr.SCValXdr
import com.soneso.stellar.sdk.xdr.Uint32Xdr
import com.soneso.stellar.sdk.xdr.toXdrBase64
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Unit tests for the token-decimals support in the smart-account transfer and
 * spending-limit paths.
 *
 * Covers:
 * - [OZTransactionOperations.amountToBaseUnits]: accepted shapes, rejection matrix,
 *   decimals bounds, big values
 * - [OZTransactionOperations.baseUnitsToI128ScVal]: encoding and i128 range tagging
 * - [OZTransactionOperations.fetchTokenDecimals]: u32 result parsing via a mock RPC
 *   server, non-u32 rejection, address validation
 * - decimals parameter plumbing in transfer / multiSignerTransfer / addSpendingLimit
 *   (offline validation paths)
 */
class TokenDecimalsTest {

    // ========================================================================
    // Test Fixtures
    // ========================================================================

    private val validContractAddress =
        "CAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAD2KM"
    private val validContractAddress2 =
        "CDLZFC3SYJYDZT7K67VZ75HPJVIEUVNIXF47ZG2FB2RMQQVU2HHGCYSC"
    private val validAccountAddress =
        "GA7QYNF7SOWQ3GLR2BGMZEHXAVIRZA4KVWLTJJFC7MGXUA74P7UJVSGZ"

    private fun buildConfig(deployer: KeyPair? = null): OZSmartAccountConfig =
        OZSmartAccountConfig(
            rpcUrl = "https://soroban-testnet.stellar.org",
            networkPassphrase = Network.TESTNET.networkPassphrase,
            accountWasmHash = "a" + "0".repeat(63),
            webauthnVerifierAddress = validContractAddress,
            deployerKeypair = deployer
        )

    private fun buildMockHttpClient(engine: MockEngine): HttpClient = HttpClient(engine) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
                encodeDefaults = false
            })
        }
    }

    /**
     * Creates a kit whose Soroban server answers the two-request `fetchTokenDecimals`
     * sequence: getLedgerEntries (deployer account lookup) followed by
     * simulateTransaction returning [decimalsResultXdrBase64] as the result SCVal.
     */
    private suspend fun createKitWithDecimalsMock(decimalsResultXdrBase64: String): OZSmartAccountKit {
        val deployer = KeyPair.random()
        val accountXdr = buildAccountEntryXdr(deployer).toXdrBase64()
        val sorobanDataXdr = buildMinimalSorobanData().toXdrBase64()

        var requestIndex = 0
        val mockEngine = MockEngine { _ ->
            val responseBody = when (requestIndex++) {
                0 -> ledgerEntriesResponseJson(accountXdr)
                1 -> simulateCountResponseJson(decimalsResultXdrBase64, sorobanDataXdr)
                else -> error("Unexpected RPC request at index ${requestIndex - 1}")
            }
            respond(
                content = ByteReadChannel(responseBody),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val server = SorobanServer(
            "https://soroban-testnet.stellar.org",
            buildMockHttpClient(mockEngine)
        )
        return OZSmartAccountKit.createWithServer(
            config = buildConfig(deployer),
            sorobanServer = server
        )
    }

    // ========================================================================
    // amountToBaseUnits - Accepted Shapes
    // ========================================================================

    @Test
    fun amountToBaseUnits_integerAmount_sevenDecimals() {
        val result = OZTransactionOperations.amountToBaseUnits("100", 7)
        assertEquals(BigInteger.parseString("1000000000"), result)
    }

    @Test
    fun amountToBaseUnits_fractionalAmount_sevenDecimals() {
        val result = OZTransactionOperations.amountToBaseUnits("100.5", 7)
        assertEquals(BigInteger.parseString("1005000000"), result)
    }

    @Test
    fun amountToBaseUnits_sixDecimals() {
        // The motivating case: a 6-decimal token must scale by 10^6, not 10^7
        val result = OZTransactionOperations.amountToBaseUnits("100", 6)
        assertEquals(BigInteger.parseString("100000000"), result)
    }

    @Test
    fun amountToBaseUnits_zeroDecimals_integerOnly() {
        val result = OZTransactionOperations.amountToBaseUnits("42", 0)
        assertEquals(BigInteger.parseString("42"), result)
    }

    @Test
    fun amountToBaseUnits_fullFractionalWidth() {
        val result = OZTransactionOperations.amountToBaseUnits("1.0000001", 7)
        assertEquals(BigInteger.parseString("10000001"), result)
    }

    @Test
    fun amountToBaseUnits_whitespaceTrimmed() {
        val result = OZTransactionOperations.amountToBaseUnits(" 10 ", 7)
        assertEquals(BigInteger.parseString("100000000"), result)
    }

    @Test
    fun amountToBaseUnits_leadingZeros() {
        val result = OZTransactionOperations.amountToBaseUnits("007", 7)
        assertEquals(BigInteger.parseString("70000000"), result)
    }

    @Test
    fun amountToBaseUnits_maxDecimals() {
        val result = OZTransactionOperations.amountToBaseUnits(
            "1",
            OZTransactionOperations.MAX_TOKEN_DECIMALS
        )
        assertEquals(BigInteger.parseString("1" + "0".repeat(38)), result)
    }

    // ========================================================================
    // amountToBaseUnits - Rejection Matrix
    // ========================================================================

    @Test
    fun amountToBaseUnits_negativeDecimals_throws() {
        val ex = assertFailsWith<ValidationException.InvalidAmount> {
            OZTransactionOperations.amountToBaseUnits("10", -1)
        }
        assertTrue(ex.message!!.contains("must not be negative"))
    }

    @Test
    fun amountToBaseUnits_decimalsAboveMax_throws() {
        val ex = assertFailsWith<ValidationException.InvalidAmount> {
            OZTransactionOperations.amountToBaseUnits("10", 39)
        }
        assertTrue(ex.message!!.contains("must not exceed"))
    }

    @Test
    fun amountToBaseUnits_empty_throws() {
        assertFailsWith<ValidationException.InvalidAmount> {
            OZTransactionOperations.amountToBaseUnits("", 7)
        }
    }

    @Test
    fun amountToBaseUnits_blank_throws() {
        assertFailsWith<ValidationException.InvalidAmount> {
            OZTransactionOperations.amountToBaseUnits("   ", 7)
        }
    }

    @Test
    fun amountToBaseUnits_nonNumeric_throws() {
        assertFailsWith<ValidationException.InvalidAmount> {
            OZTransactionOperations.amountToBaseUnits("abc", 7)
        }
    }

    @Test
    fun amountToBaseUnits_scientificNotation_throws() {
        assertFailsWith<ValidationException.InvalidAmount> {
            OZTransactionOperations.amountToBaseUnits("1e5", 7)
        }
    }

    @Test
    fun amountToBaseUnits_negativeAmount_throws() {
        assertFailsWith<ValidationException.InvalidAmount> {
            OZTransactionOperations.amountToBaseUnits("-5", 7)
        }
    }

    @Test
    fun amountToBaseUnits_zero_throws() {
        val ex = assertFailsWith<ValidationException.InvalidAmount> {
            OZTransactionOperations.amountToBaseUnits("0", 7)
        }
        assertTrue(ex.message!!.contains("greater than zero"))
    }

    @Test
    fun amountToBaseUnits_zeroPointZero_throws() {
        assertFailsWith<ValidationException.InvalidAmount> {
            OZTransactionOperations.amountToBaseUnits("0.0", 7)
        }
    }

    @Test
    fun amountToBaseUnits_tooManyFractionalDigits_throws() {
        val ex = assertFailsWith<ValidationException.InvalidAmount> {
            OZTransactionOperations.amountToBaseUnits("1.00000001", 7)
        }
        assertTrue(ex.message!!.contains("more than 7 fractional digits"))
    }

    @Test
    fun amountToBaseUnits_fractionWithZeroDecimals_throws() {
        assertFailsWith<ValidationException.InvalidAmount> {
            OZTransactionOperations.amountToBaseUnits("1.5", 0)
        }
    }

    @Test
    fun amountToBaseUnits_trailingDot_throws() {
        assertFailsWith<ValidationException.InvalidAmount> {
            OZTransactionOperations.amountToBaseUnits("10.", 7)
        }
    }

    @Test
    fun amountToBaseUnits_leadingDot_throws() {
        assertFailsWith<ValidationException.InvalidAmount> {
            OZTransactionOperations.amountToBaseUnits(".5", 7)
        }
    }

    // ========================================================================
    // baseUnitsToI128ScVal
    // ========================================================================

    @Test
    fun baseUnitsToI128ScVal_roundTrips() {
        val baseUnits = BigInteger.parseString("1005000000")
        val scVal = OZTransactionOperations.baseUnitsToI128ScVal(baseUnits, "100.5")
        assertEquals(baseUnits, Scv.fromInt128(scVal))
    }

    @Test
    fun baseUnitsToI128ScVal_outOfRange_throwsInvalidAmount() {
        // 2^127 is one past the i128 maximum
        val tooBig = BigInteger.parseString("170141183460469231731687303715884105728")
        val ex = assertFailsWith<ValidationException.InvalidAmount> {
            OZTransactionOperations.baseUnitsToI128ScVal(tooBig, "huge")
        }
        assertTrue(ex.message!!.contains("i128"))
    }

    // ========================================================================
    // fetchTokenDecimals
    // ========================================================================

    @Test
    fun fetchTokenDecimals_returnsU32Value() = runTest {
        val decimalsXdr = SCValXdr.U32(Uint32Xdr(6u)).toXdrBase64()
        val kit = createKitWithDecimalsMock(decimalsXdr)

        val decimals = kit.transactionOperations.fetchTokenDecimals(validContractAddress)

        assertEquals(6, decimals)
    }

    @Test
    fun fetchTokenDecimals_nonU32Result_throwsSimulationFailed() = runTest {
        val symbolXdr = Scv.toSymbol("nope").toXdrBase64()
        val kit = createKitWithDecimalsMock(symbolXdr)

        val ex = assertFailsWith<TransactionException.SimulationFailed> {
            kit.transactionOperations.fetchTokenDecimals(validContractAddress)
        }
        assertNotNull(ex.message)
        assertTrue(ex.message!!.contains("did not return a valid u32 decimals value"))
    }

    @Test
    fun fetchTokenDecimals_invalidContractAddress_throws() = runTest {
        // Address validation fires before any RPC call, so no mock responses are needed
        val kit = OZSmartAccountKit.create(buildConfig())

        assertFailsWith<ValidationException.InvalidAddress> {
            kit.transactionOperations.fetchTokenDecimals(validAccountAddress)
        }
    }

    // ========================================================================
    // decimals plumbing - multiSignerTransfer / addSpendingLimit (offline)
    // ========================================================================

    @Test
    fun multiSignerTransfer_explicitDecimals_invalidAmount_throws() = runTest {
        val kit = OZSmartAccountKit.create(buildConfig())
        kit.setConnectedState("test-credential-id", validContractAddress2)

        assertFailsWith<ValidationException.InvalidAmount> {
            kit.multiSignerManager.multiSignerTransfer(
                tokenContract = validContractAddress,
                recipient = validAccountAddress,
                amount = "1.00000001",
                decimals = 7,
                selectedSigners = emptyList()
            )
        }
    }

    @Test
    fun addSpendingLimit_invalidAmount_throwsTaggedInvalidInput() = runTest {
        // The spending-limit conversion runs before the connected-state check,
        // so the validation path is exercised offline.
        val kit = OZSmartAccountKit.create(buildConfig())

        val ex = assertFailsWith<ValidationException.InvalidInput> {
            kit.policyManager.addSpendingLimit(
                contextRuleId = 0u,
                policyAddress = validContractAddress,
                spendingLimit = "abc",
                periodLedgers = 17280u
            )
        }
        assertNotNull(ex.message)
        assertTrue(ex.message!!.contains("spendingLimit"))
    }

    @Test
    fun addSpendingLimit_sixDecimals_acceptsSixFractionalDigits() = runTest {
        // "1.123456" is valid for decimals = 6; the conversion succeeds and the
        // call then fails at the connected-state check, proving the decimals
        // parameter reached the conversion.
        val kit = OZSmartAccountKit.create(buildConfig())

        assertFailsWith<WalletException.NotConnected> {
            kit.policyManager.addSpendingLimit(
                contextRuleId = 0u,
                policyAddress = validContractAddress,
                spendingLimit = "1.123456",
                periodLedgers = 17280u,
                decimals = 6
            )
        }
    }

    @Test
    fun addSpendingLimit_sevenFractionalDigitsWithSixDecimals_throws() = runTest {
        val kit = OZSmartAccountKit.create(buildConfig())

        val ex = assertFailsWith<ValidationException.InvalidInput> {
            kit.policyManager.addSpendingLimit(
                contextRuleId = 0u,
                policyAddress = validContractAddress,
                spendingLimit = "1.1234567",
                periodLedgers = 17280u,
                decimals = 6
            )
        }
        assertTrue(ex.message!!.contains("spendingLimit"))
    }
}
