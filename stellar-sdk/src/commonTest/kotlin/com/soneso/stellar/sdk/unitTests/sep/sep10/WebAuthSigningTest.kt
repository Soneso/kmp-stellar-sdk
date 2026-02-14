// Copyright 2025 Soneso. All rights reserved.
// Use of this source code is governed by a license that can be
// found in the LICENSE file.

package com.soneso.stellar.sdk.unitTests.sep.sep10

import com.soneso.stellar.sdk.sep.sep10.*
import com.soneso.stellar.sdk.*
import com.soneso.stellar.sdk.sep.sep10.exceptions.GenericChallengeValidationException
import com.soneso.stellar.sdk.xdr.*
import kotlinx.coroutines.test.runTest
import kotlin.test.*
import kotlin.time.ExperimentalTime
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.io.encoding.Base64

/**
 * Tests for signTransaction() method.
 * Covers transaction signing functionality including multi-signature support.
 */
class WebAuthSigningTest {

    private val testServerSecretSeed = "SDJHRQF4GCMIIKAAAQ6IHY42X73FQFLHUULAPSKKD4DFDM7UXWWCRHBE"
    private val testHomeDomain = "testanchor.stellar.org"
    private val testAuthEndpoint = "https://testanchor.stellar.org/auth"
    private val network = Network.TESTNET

    /**
     * Creates a valid challenge transaction XDR for signing tests.
     */
    @OptIn(ExperimentalTime::class)
    private suspend fun createValidChallengeXdr(
        clientKeyPair: KeyPair,
        serverSecretSeed: String = testServerSecretSeed,
        homeDomain: String = testHomeDomain
    ): String {
        val serverKeyPair = KeyPair.fromSecretSeed(serverSecretSeed)
        val sourceAccount = Account(serverKeyPair.getAccountId(), -1L)

        val currentTime = kotlin.time.Clock.System.now().toEpochMilliseconds() / 1000
        val builder = TransactionBuilder(sourceAccount, network)
            .setBaseFee(100)
            .addTimeBounds(
                TimeBounds(
                    minTime = currentTime - 300,
                    maxTime = currentTime + 600
                )
            )

        // First operation: home domain auth
        val authOp = ManageDataOperation(
            name = "$homeDomain auth",
            value = "test_value".encodeToByteArray()
        )
        authOp.sourceAccount = clientKeyPair.getAccountId()
        builder.addOperation(authOp)

        val transaction = builder.build()
        transaction.sign(serverKeyPair)

        return transaction.toEnvelopeXdrBase64()
    }

    @Test
    fun testSignTransactionSingleSigner() = runTest {
        val serverKeyPair = KeyPair.fromSecretSeed(testServerSecretSeed)
        val clientKeyPair = KeyPair.random()

        // Create valid challenge
        val challengeXdr = createValidChallengeXdr(
            clientKeyPair = clientKeyPair
        )

        val webAuth = WebAuth(
            authEndpoint = testAuthEndpoint,
            network = network,
            serverSigningKey = serverKeyPair.getAccountId(),
            serverHomeDomain = testHomeDomain
        )

        // Sign with single client keypair
        val signedChallengeXdr = webAuth.signTransaction(
            challengeXdr = challengeXdr,
            signers = listOf(clientKeyPair)
        )

        // Parse signed transaction
        val signedTx = AbstractTransaction.fromEnvelopeXdr(signedChallengeXdr, network) as Transaction

        // Verify: Should have 2 signatures (server + client)
        assertEquals(2, signedTx.signatures.size, "Should have 2 signatures (server + client)")

        // Verify server signature is still present and valid
        val txHash = signedTx.hash()
        val serverSigValid = serverKeyPair.verify(txHash, signedTx.signatures[0].signature)
        assertTrue(serverSigValid, "Server signature should be valid")

        // Verify client signature is present and valid
        val clientSigValid = clientKeyPair.verify(txHash, signedTx.signatures[1].signature)
        assertTrue(clientSigValid, "Client signature should be valid")
    }

    @Test
    fun testSignTransactionMultipleSigners() = runTest {
        val serverKeyPair = KeyPair.fromSecretSeed(testServerSecretSeed)
        val clientKeyPair = KeyPair.random()
        val signer2 = KeyPair.random()
        val signer3 = KeyPair.random()

        val challengeXdr = createValidChallengeXdr(
            clientKeyPair = clientKeyPair
        )

        val webAuth = WebAuth(
            authEndpoint = testAuthEndpoint,
            network = network,
            serverSigningKey = serverKeyPair.getAccountId(),
            serverHomeDomain = testHomeDomain
        )

        // Sign with 3 client keypairs
        val signedChallengeXdr = webAuth.signTransaction(
            challengeXdr = challengeXdr,
            signers = listOf(clientKeyPair, signer2, signer3)
        )

        val signedTx = AbstractTransaction.fromEnvelopeXdr(signedChallengeXdr, network) as Transaction

        // Verify: Should have 4 signatures (server + 3 clients)
        assertEquals(4, signedTx.signatures.size, "Should have 4 signatures (server + 3 clients)")

        // Verify server signature is still valid
        val txHash = signedTx.hash()
        val serverSigValid = serverKeyPair.verify(txHash, signedTx.signatures[0].signature)
        assertTrue(serverSigValid, "Server signature should be valid")

        // Verify all client signatures are valid
        val clientSigValid = clientKeyPair.verify(txHash, signedTx.signatures[1].signature)
        assertTrue(clientSigValid, "Client signature 1 should be valid")

        val signer2Valid = signer2.verify(txHash, signedTx.signatures[2].signature)
        assertTrue(signer2Valid, "Client signature 2 should be valid")

        val signer3Valid = signer3.verify(txHash, signedTx.signatures[3].signature)
        assertTrue(signer3Valid, "Client signature 3 should be valid")
    }

    @Test
    fun testSignTransactionPreservesServerSignature() = runTest {
        val serverKeyPair = KeyPair.fromSecretSeed(testServerSecretSeed)
        val clientKeyPair = KeyPair.random()

        val challengeXdr = createValidChallengeXdr(
            clientKeyPair = clientKeyPair
        )

        // Parse original challenge to get server signature
        val originalTx = AbstractTransaction.fromEnvelopeXdr(challengeXdr, network) as Transaction
        val originalServerSig = originalTx.signatures[0].signature

        val webAuth = WebAuth(
            authEndpoint = testAuthEndpoint,
            network = network,
            serverSigningKey = serverKeyPair.getAccountId(),
            serverHomeDomain = testHomeDomain
        )

        // Sign with client
        val signedChallengeXdr = webAuth.signTransaction(
            challengeXdr = challengeXdr,
            signers = listOf(clientKeyPair)
        )

        val signedTx = AbstractTransaction.fromEnvelopeXdr(signedChallengeXdr, network) as Transaction
        val newServerSig = signedTx.signatures[0].signature

        // Verify server signature is preserved (exact same bytes)
        assertTrue(
            originalServerSig.contentEquals(newServerSig),
            "Server signature should be preserved exactly"
        )

        // Verify server signature is still valid
        val txHash = signedTx.hash()
        val serverSigValid = serverKeyPair.verify(txHash, newServerSig)
        assertTrue(serverSigValid, "Server signature should still be valid")
    }

    @Test
    @OptIn(ExperimentalEncodingApi::class)
    fun testSignTransactionInvalidXdr() = runTest {
        val clientKeyPair = KeyPair.random()
        val serverKeyPair = KeyPair.fromSecretSeed(testServerSecretSeed)

        val webAuth = WebAuth(
            authEndpoint = testAuthEndpoint,
            network = network,
            serverSigningKey = serverKeyPair.getAccountId(),
            serverHomeDomain = testHomeDomain
        )

        // Invalid base64 XDR
        assertFailsWith<IllegalArgumentException> {
            webAuth.signTransaction(
                challengeXdr = "INVALID_BASE64_XDR!!!",
                signers = listOf(clientKeyPair)
            )
        }
    }

    @Test
    fun testSignTransactionFeeBumpRejected() = runTest {
        val serverKeyPair = KeyPair.fromSecretSeed(testServerSecretSeed)
        val clientKeyPair = KeyPair.random()

        // Create regular transaction first
        val regularTxXdr = createValidChallengeXdr(clientKeyPair = clientKeyPair)
        val innerTx = AbstractTransaction.fromEnvelopeXdr(regularTxXdr, network) as Transaction

        // Create fee bump transaction using factory method
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
            webAuth.signTransaction(
                challengeXdr = feeBumpXdr,
                signers = listOf(clientKeyPair)
            )
        }
    }

    @Test
    @OptIn(ExperimentalTime::class, ExperimentalEncodingApi::class)
    fun testSignTransactionInvalidEnvelopeType() = runTest {
        val serverKeyPair = KeyPair.fromSecretSeed(testServerSecretSeed)
        val clientKeyPair = KeyPair.random()

        // Create a transaction with V0 envelope type manually
        val currentTime = kotlin.time.Clock.System.now().toEpochMilliseconds() / 1000

        // Build a V0 transaction envelope manually using XDR
        val v0Tx = TransactionV0Xdr(
            sourceAccountEd25519 = Uint256Xdr(serverKeyPair.getPublicKey()),
            fee = Uint32Xdr(100u),
            seqNum = SequenceNumberXdr(Int64Xdr(0L)),
            timeBounds = TimeBoundsXdr(
                TimePointXdr(Uint64Xdr((currentTime - 300).toULong())),
                TimePointXdr(Uint64Xdr((currentTime + 600).toULong()))
            ),
            memo = MemoXdr.Void,
            operations = listOf(),
            ext = TransactionV0ExtXdr.Void
        )

        val v0Envelope = TransactionEnvelopeXdr.V0(
            TransactionV0EnvelopeXdr(
                tx = v0Tx,
                signatures = listOf()
            )
        )

        val writer = XdrWriter()
        v0Envelope.encode(writer)
        val v0XdrBytes = writer.toByteArray()
        val v0Xdr = Base64.encode(v0XdrBytes)

        val webAuth = WebAuth(
            authEndpoint = testAuthEndpoint,
            network = network,
            serverSigningKey = serverKeyPair.getAccountId(),
            serverHomeDomain = testHomeDomain
        )

        // Should throw GenericChallengeValidationException for V0 envelope
        assertFailsWith<IllegalArgumentException> {
            webAuth.signTransaction(
                challengeXdr = v0Xdr,
                signers = listOf(clientKeyPair)
            )
        }
    }

    @Test
    fun testSignTransactionEmptySignersList() = runTest {
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

        // Sign with empty signers list - should still work (just preserves server sig)
        val signedChallengeXdr = webAuth.signTransaction(
            challengeXdr = challengeXdr,
            signers = emptyList()
        )

        val signedTx = AbstractTransaction.fromEnvelopeXdr(signedChallengeXdr, network) as Transaction

        // Should still have 1 signature (server only)
        assertEquals(1, signedTx.signatures.size, "Should have 1 signature (server only)")
    }

    @Test
    fun testSignTransactionIdempotent() = runTest {
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

        // Sign once
        val signedOnce = webAuth.signTransaction(
            challengeXdr = challengeXdr,
            signers = listOf(clientKeyPair)
        )

        // Sign the already-signed transaction again with same keypair
        val signedTwice = webAuth.signTransaction(
            challengeXdr = signedOnce,
            signers = listOf(clientKeyPair)
        )

        val txSignedTwice = AbstractTransaction.fromEnvelopeXdr(signedTwice, network) as Transaction

        // Should now have 3 signatures (server + client + client again)
        assertEquals(3, txSignedTwice.signatures.size, "Should have 3 signatures")

        // All signatures should be valid
        val txHash = txSignedTwice.hash()
        assertTrue(serverKeyPair.verify(txHash, txSignedTwice.signatures[0].signature))
        assertTrue(clientKeyPair.verify(txHash, txSignedTwice.signatures[1].signature))
        assertTrue(clientKeyPair.verify(txHash, txSignedTwice.signatures[2].signature))
    }

    @Test
    fun testSignTransactionWithDifferentNetworks() = runTest {
        val serverKeyPair = KeyPair.fromSecretSeed(testServerSecretSeed)
        val clientKeyPair = KeyPair.random()

        // Create challenge on TESTNET
        val challengeXdr = createValidChallengeXdr(
            clientKeyPair = clientKeyPair
        )

        // Try to sign with PUBLIC network WebAuth instance
        val webAuthPublic = WebAuth(
            authEndpoint = testAuthEndpoint,
            network = Network.PUBLIC,  // Different network!
            serverSigningKey = serverKeyPair.getAccountId(),
            serverHomeDomain = testHomeDomain
        )

        // Should still work (signing doesn't validate network),
        // but signatures will be for wrong network
        val signedChallengeXdr = webAuthPublic.signTransaction(
            challengeXdr = challengeXdr,
            signers = listOf(clientKeyPair)
        )

        // Verify transaction was signed
        val signedTx = AbstractTransaction.fromEnvelopeXdr(signedChallengeXdr, Network.PUBLIC) as Transaction
        assertEquals(2, signedTx.signatures.size)
    }

    @Test
    fun testSignTransactionPreservesTransactionStructure() = runTest {
        val serverKeyPair = KeyPair.fromSecretSeed(testServerSecretSeed)
        val clientKeyPair = KeyPair.random()

        val challengeXdr = createValidChallengeXdr(
            clientKeyPair = clientKeyPair
        )

        // Parse original transaction
        val originalTx = AbstractTransaction.fromEnvelopeXdr(challengeXdr, network) as Transaction
        val originalOperationsCount = originalTx.operations.size
        val originalSequence = originalTx.sequenceNumber
        val originalFee = originalTx.fee
        val originalSource = originalTx.sourceAccount

        val webAuth = WebAuth(
            authEndpoint = testAuthEndpoint,
            network = network,
            serverSigningKey = serverKeyPair.getAccountId(),
            serverHomeDomain = testHomeDomain
        )

        // Sign transaction
        val signedChallengeXdr = webAuth.signTransaction(
            challengeXdr = challengeXdr,
            signers = listOf(clientKeyPair)
        )

        // Parse signed transaction
        val signedTx = AbstractTransaction.fromEnvelopeXdr(signedChallengeXdr, network) as Transaction

        // Verify transaction structure is preserved
        assertEquals(originalOperationsCount, signedTx.operations.size, "Operations count should be preserved")
        assertEquals(originalSequence, signedTx.sequenceNumber, "Sequence number should be preserved")
        assertEquals(originalFee, signedTx.fee, "Fee should be preserved")
        assertEquals(originalSource, signedTx.sourceAccount, "Source account should be preserved")

        // Only signatures should have changed
        assertNotEquals(originalTx.signatures.size, signedTx.signatures.size, "Signature count should change")
    }
}
