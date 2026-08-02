// Copyright 2025 Soneso. All rights reserved.
// Use of this source code is governed by a license that can be
// found in the LICENSE file.

package com.soneso.stellar.sdk.unitTests.sep.sep10

import com.soneso.stellar.sdk.sep.sep10.*
import com.soneso.stellar.sdk.*
import com.soneso.stellar.sdk.sep.sep10.exceptions.*
import com.soneso.stellar.sdk.xdr.*
import kotlinx.coroutines.test.runTest
import kotlin.test.*
import kotlin.time.ExperimentalTime

/**
 * Tests for validateChallenge() security validation checks.
 * Covers all 14 required SEP-10 validation requirements.
 */
class WebAuthValidationTest {

    private val testServerSecretSeed = "SDJHRQF4GCMIIKAAAQ6IHY42X73FQFLHUULAPSKKD4DFDM7UXWWCRHBE"
    private val testHomeDomain = "testanchor.stellar.org"
    private val testAuthEndpoint = "https://testanchor.stellar.org/auth"
    private val network = Network.TESTNET

    /**
     * Creates a valid challenge transaction XDR for testing.
     * Parameters allow modification of specific aspects to test validation failures.
     */
    @OptIn(ExperimentalTime::class)
    private suspend fun createValidChallengeXdr(
        clientKeyPair: KeyPair,
        serverSecretSeed: String = testServerSecretSeed,
        homeDomain: String = testHomeDomain,
        sequenceNumber: Long = -1L,
        memo: Long? = null,
        memoType: String = "MEMO_ID",
        useMuxedAccount: Boolean = false,
        operationType: String = "ManageData",
        firstOpSourceAccount: String? = null,
        dataName: String? = null,
        includeClientDomain: Boolean = false,
        clientDomainSource: String? = null,
        includeWebAuthDomain: Boolean = false,
        webAuthDomainValue: String? = null,
        useValidTimeBounds: Boolean = true,
        customMinTime: Long? = null,
        customMaxTime: Long? = null,
        signWithWrongKey: Boolean = false,
        addExtraSignature: Boolean = false,
        omitFirstOpSourceAccount: Boolean = false,
        omitWebAuthDomainValue: Boolean = false,
        extraOpDataName: String? = null,
        extraOpSource: String? = null,
        v2PreconditionsWithoutTimeBounds: Boolean = false,
        truncateServerSignature: Boolean = false,
        useV0Envelope: Boolean = false,
        txSourceAccountId: String? = null
    ): String {
        val serverKeyPair = KeyPair.fromSecretSeed(serverSecretSeed)
        // The transaction source defaults to the signing server; txSourceAccountId sets it
        // independently so a test can vary source and signature separately.
        val sourceAccount = Account(txSourceAccountId ?: serverKeyPair.getAccountId(), sequenceNumber)

        val builder = TransactionBuilder(sourceAccount, network)
            .setBaseFee(100)

        // Configure time bounds
        if (v2PreconditionsWithoutTimeBounds) {
            // A non-zero minSequenceAge forces PRECOND_V2 while leaving timeBounds unset
            builder.addPreconditions(TransactionPreconditions(minSequenceAge = 1L))
        } else if (useValidTimeBounds) {
            val currentTime = kotlin.time.Clock.System.now().toEpochMilliseconds() / 1000
            builder.addTimeBounds(
                TimeBounds(
                    minTime = customMinTime ?: (currentTime - 300),
                    maxTime = customMaxTime ?: (currentTime + 600)
                )
            )
        }

        // Add memo based on type
        when (memoType) {
            "MEMO_ID" -> memo?.let { builder.addMemo(MemoId(it.toULong())) }
            "MEMO_TEXT" -> builder.addMemo(MemoText("test memo text"))
            "MEMO_HASH" -> builder.addMemo(MemoHash("0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"))
            "MEMO_RETURN" -> builder.addMemo(MemoReturn("0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"))
        }

        // First operation
        val actualDataName = dataName ?: "$homeDomain auth"
        val firstOp = when (operationType) {
            "ManageData" -> ManageDataOperation(
                name = actualDataName,
                value = "test_value".encodeToByteArray()
            )
            "Payment" -> PaymentOperation(
                destination = clientKeyPair.getAccountId(),
                asset = AssetTypeNative,
                amount = "100"
            )
            else -> ManageDataOperation(
                name = actualDataName,
                value = "test_value".encodeToByteArray()
            )
        }

        // Set operation source
        if (!omitFirstOpSourceAccount) {
            val opSource = firstOpSourceAccount ?:
                (if (useMuxedAccount) {
                    val muxed = MuxedAccount(clientKeyPair.getAccountId(), 12345UL)
                    muxed.address
                } else {
                    clientKeyPair.getAccountId()
                })
            firstOp.sourceAccount = opSource
        }
        builder.addOperation(firstOp)

        // Optional: client_domain operation
        if (includeClientDomain) {
            val clientDomainOp = ManageDataOperation(
                name = "client_domain",
                value = "wallet.example.com".encodeToByteArray()
            )
            clientDomainOp.sourceAccount = clientDomainSource ?: serverKeyPair.getAccountId()
            builder.addOperation(clientDomainOp)
        }

        // Optional: web_auth_domain operation
        if (includeWebAuthDomain) {
            val webAuthValue = webAuthDomainValue ?: "testanchor.stellar.org"
            val webAuthDomainOp = ManageDataOperation(
                name = "web_auth_domain",
                value = if (omitWebAuthDomainValue) null else webAuthValue.encodeToByteArray()
            )
            webAuthDomainOp.sourceAccount = serverKeyPair.getAccountId()
            builder.addOperation(webAuthDomainOp)
        }

        // Optional: additional operation with an unrecognised data name
        if (extraOpDataName != null) {
            val extraOp = ManageDataOperation(
                name = extraOpDataName,
                value = "extra_value".encodeToByteArray()
            )
            extraOp.sourceAccount = extraOpSource ?: serverKeyPair.getAccountId()
            builder.addOperation(extraOp)
        }

        val transaction = builder.build()

        // Sign with correct or wrong keypair
        val signingKey = if (signWithWrongKey) {
            KeyPair.random()
        } else {
            serverKeyPair
        }
        transaction.sign(signingKey)

        // Optionally add extra signature
        if (addExtraSignature) {
            val extraSigner = KeyPair.random()
            transaction.sign(extraSigner)
        }

        // Optionally shorten the server signature so it is no longer a valid Ed25519 signature
        if (truncateServerSignature) {
            val original = transaction.signatures[0]
            transaction.signatures[0] = DecoratedSignature(
                hint = original.hint,
                signature = original.signature.copyOfRange(0, 32)
            )
        }

        if (useV0Envelope) {
            transaction.envelopeType = EnvelopeTypeXdr.ENVELOPE_TYPE_TX_V0
        }

        return transaction.toEnvelopeXdrBase64()
    }

    @Test
    fun testValidationSuccess() = runTest {
        val serverKeyPair = KeyPair.fromSecretSeed(testServerSecretSeed)
        val clientKeyPair = KeyPair.random()

        val challengeXdr = createValidChallengeXdr(
            clientKeyPair = clientKeyPair
        )

        val webAuth = WebAuth(
            authEndpoint = testAuthEndpoint,
            network = network,
            serverSigningKey = serverKeyPair.getAccountId(),
            serverHomeDomain = testHomeDomain
        )

        // Should not throw any exception
        webAuth.validateChallenge(
            challengeXdr = challengeXdr,
            clientAccountId = clientKeyPair.getAccountId()
        )
    }

    @Test
    fun testValidationTransactionSourceAccountMustBeServerAccount() = runTest {
        val serverKeyPair = KeyPair.fromSecretSeed(testServerSecretSeed)
        val clientKeyPair = KeyPair.random()
        val otherAccount = KeyPair.random()

        // Signed by the server, but sourced by a different account
        val challengeXdr = createValidChallengeXdr(
            clientKeyPair = clientKeyPair,
            txSourceAccountId = otherAccount.getAccountId()
        )

        val webAuth = WebAuth(
            authEndpoint = testAuthEndpoint,
            network = network,
            serverSigningKey = serverKeyPair.getAccountId(),
            serverHomeDomain = testHomeDomain
        )

        val exception = assertFailsWith<InvalidTransactionSourceAccountException> {
            webAuth.validateChallenge(
                challengeXdr = challengeXdr,
                clientAccountId = clientKeyPair.getAccountId()
            )
        }

        assertTrue(
            exception.message?.contains(serverKeyPair.getAccountId()) == true,
            "The expected server account is named, got: ${exception.message}"
        )
        assertTrue(
            exception.message?.contains(otherAccount.getAccountId()) == true,
            "The offending source account is named, got: ${exception.message}"
        )
    }

    @Test
    fun testValidationAcceptsChallengeSourcedByServerAccount() = runTest {
        val serverKeyPair = KeyPair.fromSecretSeed(testServerSecretSeed)
        val clientKeyPair = KeyPair.random()

        val challengeXdr = createValidChallengeXdr(
            clientKeyPair = clientKeyPair,
            txSourceAccountId = serverKeyPair.getAccountId()
        )

        val webAuth = WebAuth(
            authEndpoint = testAuthEndpoint,
            network = network,
            serverSigningKey = serverKeyPair.getAccountId(),
            serverHomeDomain = testHomeDomain
        )

        webAuth.validateChallenge(
            challengeXdr = challengeXdr,
            clientAccountId = clientKeyPair.getAccountId()
        )
    }

    @Test
    fun testValidationInvalidTransactionType() = runTest {
        // Create a FeeBumpTransaction instead of regular Transaction
        val serverKeyPair = KeyPair.fromSecretSeed(testServerSecretSeed)
        val clientKeyPair = KeyPair.random()

        // First create a regular transaction
        val regularTx = createValidChallengeXdr(clientKeyPair = clientKeyPair)
        val innerTx = AbstractTransaction.fromEnvelopeXdr(regularTx, network) as Transaction

        // Create fee bump transaction
        val feeBumpTx = FeeBumpTransaction.createWithFee(
            feeSource = serverKeyPair.getAccountId(),
            fee = 200L,
            innerTransaction = innerTx
        )
        feeBumpTx.sign(serverKeyPair)
        val feeBumpXdr = feeBumpTx.toEnvelopeXdrBase64()

        val webAuth = WebAuth(
            authEndpoint = testAuthEndpoint,
            network = network,
            serverSigningKey = serverKeyPair.getAccountId(),
            serverHomeDomain = testHomeDomain
        )

        // Should throw GenericChallengeValidationException
        assertFailsWith<GenericChallengeValidationException> {
            webAuth.validateChallenge(
                challengeXdr = feeBumpXdr,
                clientAccountId = clientKeyPair.getAccountId()
            )
        }
    }

    @Test
    fun testValidationInvalidSequenceNumber() = runTest {
        val serverKeyPair = KeyPair.fromSecretSeed(testServerSecretSeed)
        val clientKeyPair = KeyPair.random()

        // Create challenge with sequence number 100 instead of 0
        val challengeXdr = createValidChallengeXdr(
            clientKeyPair = clientKeyPair,
            sequenceNumber = 100L
        )

        val webAuth = WebAuth(
            authEndpoint = testAuthEndpoint,
            network = network,
            serverSigningKey = serverKeyPair.getAccountId(),
            serverHomeDomain = testHomeDomain
        )

        // Should throw InvalidSequenceNumberException
        val exception = assertFailsWith<InvalidSequenceNumberException> {
            webAuth.validateChallenge(
                challengeXdr = challengeXdr,
                clientAccountId = clientKeyPair.getAccountId()
            )
        }

        assertTrue(exception.message?.contains("sequence") == true)
    }

    @Test
    fun testValidationInvalidMemoType() = runTest {
        val serverKeyPair = KeyPair.fromSecretSeed(testServerSecretSeed)
        val clientKeyPair = KeyPair.random()

        // Create challenge with MEMO_TEXT instead of MEMO_ID
        val challengeXdr = createValidChallengeXdr(
            clientKeyPair = clientKeyPair,
            memoType = "MEMO_TEXT"
        )

        val webAuth = WebAuth(
            authEndpoint = testAuthEndpoint,
            network = network,
            serverSigningKey = serverKeyPair.getAccountId(),
            serverHomeDomain = testHomeDomain
        )

        // Should throw InvalidMemoTypeException
        assertFailsWith<InvalidMemoTypeException> {
            webAuth.validateChallenge(
                challengeXdr = challengeXdr,
                clientAccountId = clientKeyPair.getAccountId()
            )
        }
    }

    @Test
    fun testValidationInvalidMemoValue() = runTest {
        val serverKeyPair = KeyPair.fromSecretSeed(testServerSecretSeed)
        val clientKeyPair = KeyPair.random()

        // Create challenge with memo=123
        val challengeXdr = createValidChallengeXdr(
            clientKeyPair = clientKeyPair,
            memo = 123L
        )

        val webAuth = WebAuth(
            authEndpoint = testAuthEndpoint,
            network = network,
            serverSigningKey = serverKeyPair.getAccountId(),
            serverHomeDomain = testHomeDomain
        )

        // Validate with expected memo=456 (mismatch)
        val exception = assertFailsWith<InvalidMemoValueException> {
            webAuth.validateChallenge(
                challengeXdr = challengeXdr,
                clientAccountId = clientKeyPair.getAccountId(),
                expectedMemo = 456L
            )
        }

        assertTrue(exception.message?.contains("456") == true)
        assertTrue(exception.message?.contains("123") == true)
    }

    @Test
    fun testValidationMemoWithMuxedAccount() = runTest {
        val serverKeyPair = KeyPair.fromSecretSeed(testServerSecretSeed)
        val clientKeyPair = KeyPair.random()
        val muxedAccount = MuxedAccount(clientKeyPair.getAccountId(), 9876543210UL)

        // Create challenge with memo
        val challengeXdr = createValidChallengeXdr(
            clientKeyPair = clientKeyPair,
            memo = 12345L
        )

        val webAuth = WebAuth(
            authEndpoint = testAuthEndpoint,
            network = network,
            serverSigningKey = serverKeyPair.getAccountId(),
            serverHomeDomain = testHomeDomain
        )

        // Validate with M... address - should throw
        assertFailsWith<MemoWithMuxedAccountException> {
            webAuth.validateChallenge(
                challengeXdr = challengeXdr,
                clientAccountId = muxedAccount.address  // M... address
            )
        }
    }

    @Test
    fun testValidationInvalidOperationType() = runTest {
        val serverKeyPair = KeyPair.fromSecretSeed(testServerSecretSeed)
        val clientKeyPair = KeyPair.random()

        // Create challenge with Payment operation instead of ManageData
        val challengeXdr = createValidChallengeXdr(
            clientKeyPair = clientKeyPair,
            operationType = "Payment"
        )

        val webAuth = WebAuth(
            authEndpoint = testAuthEndpoint,
            network = network,
            serverSigningKey = serverKeyPair.getAccountId(),
            serverHomeDomain = testHomeDomain
        )

        // Should throw InvalidOperationTypeException
        assertFailsWith<InvalidOperationTypeException> {
            webAuth.validateChallenge(
                challengeXdr = challengeXdr,
                clientAccountId = clientKeyPair.getAccountId()
            )
        }
    }

    @Test
    fun testValidationInvalidSourceAccount() = runTest {
        val serverKeyPair = KeyPair.fromSecretSeed(testServerSecretSeed)
        val clientKeyPair = KeyPair.random()
        val wrongKeyPair = KeyPair.random()

        // Create challenge with wrong source account in first operation
        val challengeXdr = createValidChallengeXdr(
            clientKeyPair = clientKeyPair,
            firstOpSourceAccount = wrongKeyPair.getAccountId()
        )

        val webAuth = WebAuth(
            authEndpoint = testAuthEndpoint,
            network = network,
            serverSigningKey = serverKeyPair.getAccountId(),
            serverHomeDomain = testHomeDomain
        )

        // Should throw InvalidSourceAccountException
        assertFailsWith<InvalidSourceAccountException> {
            webAuth.validateChallenge(
                challengeXdr = challengeXdr,
                clientAccountId = clientKeyPair.getAccountId()
            )
        }
    }

    @Test
    fun testValidationInvalidHomeDomain() = runTest {
        val serverKeyPair = KeyPair.fromSecretSeed(testServerSecretSeed)
        val clientKeyPair = KeyPair.random()

        // Create challenge with wrong home domain in first operation
        val challengeXdr = createValidChallengeXdr(
            clientKeyPair = clientKeyPair,
            dataName = "wrongdomain.com auth"
        )

        val webAuth = WebAuth(
            authEndpoint = testAuthEndpoint,
            network = network,
            serverSigningKey = serverKeyPair.getAccountId(),
            serverHomeDomain = testHomeDomain
        )

        // Should throw InvalidHomeDomainException
        assertFailsWith<InvalidHomeDomainException> {
            webAuth.validateChallenge(
                challengeXdr = challengeXdr,
                clientAccountId = clientKeyPair.getAccountId()
            )
        }
    }

    @Test
    fun testValidationInvalidClientDomainSource() = runTest {
        val serverKeyPair = KeyPair.fromSecretSeed(testServerSecretSeed)
        val clientKeyPair = KeyPair.random()
        val wrongKeyPair = KeyPair.random()

        // Create challenge with client_domain operation but wrong source
        val challengeXdr = createValidChallengeXdr(
            clientKeyPair = clientKeyPair,
            includeClientDomain = true,
            clientDomainSource = wrongKeyPair.getAccountId()
        )

        val webAuth = WebAuth(
            authEndpoint = testAuthEndpoint,
            network = network,
            serverSigningKey = serverKeyPair.getAccountId(),
            serverHomeDomain = testHomeDomain
        )

        // Should throw InvalidClientDomainSourceException
        assertFailsWith<InvalidClientDomainSourceException> {
            webAuth.validateChallenge(
                challengeXdr = challengeXdr,
                clientAccountId = clientKeyPair.getAccountId(),
                clientDomainAccountId = serverKeyPair.getAccountId()
            )
        }
    }

    @Test
    fun testValidationInvalidWebAuthDomain() = runTest {
        val serverKeyPair = KeyPair.fromSecretSeed(testServerSecretSeed)
        val clientKeyPair = KeyPair.random()

        // Create challenge with wrong web_auth_domain value
        val challengeXdr = createValidChallengeXdr(
            clientKeyPair = clientKeyPair,
            includeWebAuthDomain = true,
            webAuthDomainValue = "wrongdomain.com"
        )

        val webAuth = WebAuth(
            authEndpoint = testAuthEndpoint,
            network = network,
            serverSigningKey = serverKeyPair.getAccountId(),
            serverHomeDomain = testHomeDomain
        )

        // Should throw InvalidWebAuthDomainException
        assertFailsWith<InvalidWebAuthDomainException> {
            webAuth.validateChallenge(
                challengeXdr = challengeXdr,
                clientAccountId = clientKeyPair.getAccountId()
            )
        }
    }

    @Test
    @OptIn(ExperimentalTime::class)
    fun testValidationInvalidTimeBoundsExpired() = runTest {
        val serverKeyPair = KeyPair.fromSecretSeed(testServerSecretSeed)
        val clientKeyPair = KeyPair.random()

        // Create challenge with expired time bounds (in the past)
        val currentTime = kotlin.time.Clock.System.now().toEpochMilliseconds() / 1000
        val challengeXdr = createValidChallengeXdr(
            clientKeyPair = clientKeyPair,
            customMinTime = currentTime - 1000,  // 1000 seconds ago
            customMaxTime = currentTime - 400   // 400 seconds ago (outside grace period)
        )

        val webAuth = WebAuth(
            authEndpoint = testAuthEndpoint,
            network = network,
            serverSigningKey = serverKeyPair.getAccountId(),
            serverHomeDomain = testHomeDomain
        )

        // Should throw InvalidTimeBoundsException
        assertFailsWith<InvalidTimeBoundsException> {
            webAuth.validateChallenge(
                challengeXdr = challengeXdr,
                clientAccountId = clientKeyPair.getAccountId()
            )
        }
    }

    @Test
    fun testValidationInvalidTimeBoundsMissing() = runTest {
        val serverKeyPair = KeyPair.fromSecretSeed(testServerSecretSeed)
        val clientKeyPair = KeyPair.random()

        // Create challenge without time bounds
        val challengeXdr = createValidChallengeXdr(
            clientKeyPair = clientKeyPair,
            useValidTimeBounds = false
        )

        val webAuth = WebAuth(
            authEndpoint = testAuthEndpoint,
            network = network,
            serverSigningKey = serverKeyPair.getAccountId(),
            serverHomeDomain = testHomeDomain
        )

        // Should throw InvalidTimeBoundsException
        assertFailsWith<InvalidTimeBoundsException> {
            webAuth.validateChallenge(
                challengeXdr = challengeXdr,
                clientAccountId = clientKeyPair.getAccountId()
            )
        }
    }

    @Test
    fun testValidationInvalidSignatureCount() = runTest {
        val serverKeyPair = KeyPair.fromSecretSeed(testServerSecretSeed)
        val clientKeyPair = KeyPair.random()

        // Create challenge with 2 signatures (server + extra)
        val challengeXdr = createValidChallengeXdr(
            clientKeyPair = clientKeyPair,
            addExtraSignature = true
        )

        val webAuth = WebAuth(
            authEndpoint = testAuthEndpoint,
            network = network,
            serverSigningKey = serverKeyPair.getAccountId(),
            serverHomeDomain = testHomeDomain
        )

        // Should throw InvalidSignatureCountException
        val exception = assertFailsWith<InvalidSignatureCountException> {
            webAuth.validateChallenge(
                challengeXdr = challengeXdr,
                clientAccountId = clientKeyPair.getAccountId()
            )
        }

        assertTrue(exception.message?.contains("2") == true || exception.message?.contains("exactly 1") == true)
    }

    @Test
    fun testValidationInvalidSignature() = runTest {
        val serverKeyPair = KeyPair.fromSecretSeed(testServerSecretSeed)
        val clientKeyPair = KeyPair.random()

        // Create challenge signed by wrong keypair
        val challengeXdr = createValidChallengeXdr(
            clientKeyPair = clientKeyPair,
            signWithWrongKey = true
        )

        val webAuth = WebAuth(
            authEndpoint = testAuthEndpoint,
            network = network,
            serverSigningKey = serverKeyPair.getAccountId(),
            serverHomeDomain = testHomeDomain
        )

        // Should throw InvalidSignatureException
        val exception = assertFailsWith<InvalidSignatureException> {
            webAuth.validateChallenge(
                challengeXdr = challengeXdr,
                clientAccountId = clientKeyPair.getAccountId()
            )
        }

        assertTrue(
            exception.message?.contains("signature") == true ||
            exception.message?.contains("invalid") == true
        )
    }

    @Test
    fun testValidationWithWebAuthDomainSuccess() = runTest {
        val serverKeyPair = KeyPair.fromSecretSeed(testServerSecretSeed)
        val clientKeyPair = KeyPair.random()

        // Create valid challenge with web_auth_domain
        val challengeXdr = createValidChallengeXdr(
            clientKeyPair = clientKeyPair,
            includeWebAuthDomain = true,
            webAuthDomainValue = "testanchor.stellar.org"  // Matches endpoint host
        )

        val webAuth = WebAuth(
            authEndpoint = testAuthEndpoint,
            network = network,
            serverSigningKey = serverKeyPair.getAccountId(),
            serverHomeDomain = testHomeDomain
        )

        // Should not throw
        webAuth.validateChallenge(
            challengeXdr = challengeXdr,
            clientAccountId = clientKeyPair.getAccountId()
        )
    }

    @Test
    fun testValidationWithMuxedAccountSuccess() = runTest {
        val serverKeyPair = KeyPair.fromSecretSeed(testServerSecretSeed)
        val clientKeyPair = KeyPair.random()
        val muxedAccount = MuxedAccount(clientKeyPair.getAccountId(), 12345UL)

        // Create challenge (operation source is underlying G... account)
        val challengeXdr = createValidChallengeXdr(
            clientKeyPair = clientKeyPair
        )

        val webAuth = WebAuth(
            authEndpoint = testAuthEndpoint,
            network = network,
            serverSigningKey = serverKeyPair.getAccountId(),
            serverHomeDomain = testHomeDomain
        )

        // Validate with M... address should succeed
        webAuth.validateChallenge(
            challengeXdr = challengeXdr,
            clientAccountId = muxedAccount.address
        )
    }

    @Test
    fun testValidationMalformedXdrRejected() = runTest {
        val serverKeyPair = KeyPair.fromSecretSeed(testServerSecretSeed)
        val clientKeyPair = KeyPair.random()

        val webAuth = WebAuth(
            authEndpoint = testAuthEndpoint,
            network = network,
            serverSigningKey = serverKeyPair.getAccountId(),
            serverHomeDomain = testHomeDomain
        )

        val exception = assertFailsWith<GenericChallengeValidationException> {
            webAuth.validateChallenge(
                challengeXdr = "not-a-transaction-envelope",
                clientAccountId = clientKeyPair.getAccountId()
            )
        }

        assertTrue(exception.message?.contains("Invalid transaction XDR") == true)
    }

    @Test
    fun testValidationV0EnvelopeRejected() = runTest {
        val serverKeyPair = KeyPair.fromSecretSeed(testServerSecretSeed)
        val clientKeyPair = KeyPair.random()

        // SEP-10 requires ENVELOPE_TYPE_TX; the legacy V0 envelope must be refused
        val challengeXdr = createValidChallengeXdr(
            clientKeyPair = clientKeyPair,
            useV0Envelope = true
        )

        val webAuth = WebAuth(
            authEndpoint = testAuthEndpoint,
            network = network,
            serverSigningKey = serverKeyPair.getAccountId(),
            serverHomeDomain = testHomeDomain
        )

        val exception = assertFailsWith<GenericChallengeValidationException> {
            webAuth.validateChallenge(
                challengeXdr = challengeXdr,
                clientAccountId = clientKeyPair.getAccountId()
            )
        }

        assertTrue(exception.message?.contains("envelope type") == true)
        assertTrue(exception.message?.contains("ENVELOPE_TYPE_TX") == true)
    }

    @Test
    fun testValidationExpectedMemoMissingFromChallenge() = runTest {
        val serverKeyPair = KeyPair.fromSecretSeed(testServerSecretSeed)
        val clientKeyPair = KeyPair.random()

        // Challenge carries no memo at all while the caller expects one
        val challengeXdr = createValidChallengeXdr(clientKeyPair = clientKeyPair)

        val webAuth = WebAuth(
            authEndpoint = testAuthEndpoint,
            network = network,
            serverSigningKey = serverKeyPair.getAccountId(),
            serverHomeDomain = testHomeDomain
        )

        val exception = assertFailsWith<InvalidMemoValueException> {
            webAuth.validateChallenge(
                challengeXdr = challengeXdr,
                clientAccountId = clientKeyPair.getAccountId(),
                expectedMemo = 4242L
            )
        }

        assertTrue(exception.message?.contains("4242") == true)
    }

    @Test
    fun testValidationOperationWithoutSourceAccountRejected() = runTest {
        val serverKeyPair = KeyPair.fromSecretSeed(testServerSecretSeed)
        val clientKeyPair = KeyPair.random()

        // Without an explicit operation source the operation would inherit the
        // transaction source, so the challenge would not be bound to the client
        val challengeXdr = createValidChallengeXdr(
            clientKeyPair = clientKeyPair,
            omitFirstOpSourceAccount = true
        )

        val webAuth = WebAuth(
            authEndpoint = testAuthEndpoint,
            network = network,
            serverSigningKey = serverKeyPair.getAccountId(),
            serverHomeDomain = testHomeDomain
        )

        val exception = assertFailsWith<InvalidSourceAccountException> {
            webAuth.validateChallenge(
                challengeXdr = challengeXdr,
                clientAccountId = clientKeyPair.getAccountId()
            )
        }

        assertTrue(exception.message?.contains("Expected source account") == true)
    }

    @Test
    fun testValidationWebAuthDomainOperationWithoutValueRejected() = runTest {
        val serverKeyPair = KeyPair.fromSecretSeed(testServerSecretSeed)
        val clientKeyPair = KeyPair.random()

        // A web_auth_domain entry with no value cannot be matched against the endpoint host
        val challengeXdr = createValidChallengeXdr(
            clientKeyPair = clientKeyPair,
            includeWebAuthDomain = true,
            omitWebAuthDomainValue = true
        )

        val webAuth = WebAuth(
            authEndpoint = testAuthEndpoint,
            network = network,
            serverSigningKey = serverKeyPair.getAccountId(),
            serverHomeDomain = testHomeDomain
        )

        val exception = assertFailsWith<InvalidWebAuthDomainException> {
            webAuth.validateChallenge(
                challengeXdr = challengeXdr,
                clientAccountId = clientKeyPair.getAccountId()
            )
        }

        assertTrue(exception.message?.contains(testAuthEndpoint) == true)
    }

    @Test
    fun testValidationAdditionalOperationNotSourcedByServerRejected() = runTest {
        val serverKeyPair = KeyPair.fromSecretSeed(testServerSecretSeed)
        val clientKeyPair = KeyPair.random()
        val strangerKeyPair = KeyPair.random()

        // Operations beyond the first that are neither client_domain nor web_auth_domain
        // must be sourced by the server signing key
        val challengeXdr = createValidChallengeXdr(
            clientKeyPair = clientKeyPair,
            extraOpDataName = "unexpected_entry",
            extraOpSource = strangerKeyPair.getAccountId()
        )

        val webAuth = WebAuth(
            authEndpoint = testAuthEndpoint,
            network = network,
            serverSigningKey = serverKeyPair.getAccountId(),
            serverHomeDomain = testHomeDomain
        )

        val exception = assertFailsWith<InvalidSourceAccountException> {
            webAuth.validateChallenge(
                challengeXdr = challengeXdr,
                clientAccountId = clientKeyPair.getAccountId()
            )
        }

        assertTrue(exception.message?.contains(serverKeyPair.getAccountId()) == true)
        assertTrue(exception.message?.contains(strangerKeyPair.getAccountId()) == true)
    }

    @Test
    fun testValidationAdditionalOperationSourcedByServerAccepted() = runTest {
        val serverKeyPair = KeyPair.fromSecretSeed(testServerSecretSeed)
        val clientKeyPair = KeyPair.random()

        val challengeXdr = createValidChallengeXdr(
            clientKeyPair = clientKeyPair,
            extraOpDataName = "unexpected_entry"
        )

        val webAuth = WebAuth(
            authEndpoint = testAuthEndpoint,
            network = network,
            serverSigningKey = serverKeyPair.getAccountId(),
            serverHomeDomain = testHomeDomain
        )

        // Server-sourced extra operations are permitted
        webAuth.validateChallenge(
            challengeXdr = challengeXdr,
            clientAccountId = clientKeyPair.getAccountId()
        )
    }

    @Test
    fun testValidationV2PreconditionsWithoutTimeBoundsRejected() = runTest {
        val serverKeyPair = KeyPair.fromSecretSeed(testServerSecretSeed)
        val clientKeyPair = KeyPair.random()

        // PRECOND_V2 without a timeBounds member leaves the challenge valid forever
        val challengeXdr = createValidChallengeXdr(
            clientKeyPair = clientKeyPair,
            v2PreconditionsWithoutTimeBounds = true
        )

        val webAuth = WebAuth(
            authEndpoint = testAuthEndpoint,
            network = network,
            serverSigningKey = serverKeyPair.getAccountId(),
            serverHomeDomain = testHomeDomain
        )

        val exception = assertFailsWith<InvalidTimeBoundsException> {
            webAuth.validateChallenge(
                challengeXdr = challengeXdr,
                clientAccountId = clientKeyPair.getAccountId()
            )
        }

        assertTrue(exception.message?.contains("must have time bounds set") == true)
    }

    @Test
    fun testValidationUnparsableServerSigningKeyRejected() = runTest {
        val clientKeyPair = KeyPair.random()

        val challengeXdr = createValidChallengeXdr(clientKeyPair = clientKeyPair)

        // Configured signing key is not a decodable account address
        val webAuth = WebAuth(
            authEndpoint = testAuthEndpoint,
            network = network,
            serverSigningKey = "GNOTAVALIDACCOUNTID",
            serverHomeDomain = testHomeDomain
        )

        val exception = assertFailsWith<InvalidSignatureException> {
            webAuth.validateChallenge(
                challengeXdr = challengeXdr,
                clientAccountId = clientKeyPair.getAccountId()
            )
        }

        assertTrue(exception.message?.contains("GNOTAVALIDACCOUNTID") == true)
    }

    @Test
    fun testValidationMalformedSignatureRejected() = runTest {
        val serverKeyPair = KeyPair.fromSecretSeed(testServerSecretSeed)
        val clientKeyPair = KeyPair.random()

        // Signature blob is not 64 bytes, so verification cannot even be attempted
        val challengeXdr = createValidChallengeXdr(
            clientKeyPair = clientKeyPair,
            truncateServerSignature = true
        )

        val webAuth = WebAuth(
            authEndpoint = testAuthEndpoint,
            network = network,
            serverSigningKey = serverKeyPair.getAccountId(),
            serverHomeDomain = testHomeDomain
        )

        val exception = assertFailsWith<InvalidSignatureException> {
            webAuth.validateChallenge(
                challengeXdr = challengeXdr,
                clientAccountId = clientKeyPair.getAccountId()
            )
        }

        assertTrue(exception.message?.contains(serverKeyPair.getAccountId()) == true)
    }

    @Test
    fun testValidationWithMemoSuccess() = runTest {
        val serverKeyPair = KeyPair.fromSecretSeed(testServerSecretSeed)
        val clientKeyPair = KeyPair.random()
        val memo = 98765L

        // Create challenge with memo
        val challengeXdr = createValidChallengeXdr(
            clientKeyPair = clientKeyPair,
            memo = memo
        )

        val webAuth = WebAuth(
            authEndpoint = testAuthEndpoint,
            network = network,
            serverSigningKey = serverKeyPair.getAccountId(),
            serverHomeDomain = testHomeDomain
        )

        // Validate with matching expected memo
        webAuth.validateChallenge(
            challengeXdr = challengeXdr,
            clientAccountId = clientKeyPair.getAccountId(),
            expectedMemo = memo
        )
    }
}
