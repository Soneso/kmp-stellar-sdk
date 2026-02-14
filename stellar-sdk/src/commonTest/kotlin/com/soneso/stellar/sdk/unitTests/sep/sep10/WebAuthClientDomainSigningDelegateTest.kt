// Copyright 2025 Soneso. All rights reserved.
// Use of this source code is governed by a license that can be
// found in the LICENSE file.

package com.soneso.stellar.sdk.unitTests.sep.sep10

import com.soneso.stellar.sdk.sep.sep10.*
import com.soneso.stellar.sdk.*
import com.soneso.stellar.sdk.sep.sep10.ClientDomainSigningDelegate
import com.soneso.stellar.sdk.sep.sep10.WebAuth
import kotlinx.coroutines.test.runTest
import kotlin.test.*
import kotlin.time.ExperimentalTime

/**
 * Tests for ClientDomainSigningDelegate functionality.
 *
 * Covers external signing delegation for wallet company infrastructure:
 * - Wallet company's HSMs (Hardware Security Modules)
 * - Wallet backend key management (AWS KMS, Google Cloud KMS, Azure Key Vault)
 * - Multi-Party Computation (MPC) systems for wallet domain keys
 * - Multi-signature threshold accounts for wallet domain signing
 */
class WebAuthClientDomainSigningDelegateTest {

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
    fun testSignTransactionWithDelegateSuccess() = runTest {
        val serverKeyPair = KeyPair.fromSecretSeed(testServerSecretSeed)
        val clientKeyPair = KeyPair.random()
        val clientDomainKeyPair = KeyPair.random()

        // Create valid challenge
        val challengeXdr = createValidChallengeXdr(clientKeyPair = clientKeyPair)

        // Mock signing delegate that simulates HSM/custody service
        // Delegate now returns signed transaction XDR (not signature XDR)
        val signingDelegate = ClientDomainSigningDelegate { transactionXdr ->
            // Parse transaction
            val tx = AbstractTransaction.fromEnvelopeXdr(transactionXdr, network) as Transaction

            // Sign with client domain key
            tx.sign(clientDomainKeyPair)

            // Return signed transaction
            tx.toEnvelopeXdrBase64()
        }

        val webAuth = WebAuth(
            authEndpoint = testAuthEndpoint,
            network = network,
            serverSigningKey = serverKeyPair.getAccountId(),
            serverHomeDomain = testHomeDomain
        )

        // Sign with user keypair and delegate
        val signedChallengeXdr = webAuth.signTransaction(
            challengeXdr = challengeXdr,
            signers = listOf(clientKeyPair),
            clientDomainSigningDelegate = signingDelegate
        )

        // Parse signed transaction
        val signedTx = AbstractTransaction.fromEnvelopeXdr(signedChallengeXdr, network) as Transaction

        // Verify: Should have 3 signatures (server + client domain + client)
        assertEquals(3, signedTx.signatures.size, "Should have 3 signatures (server + client domain + client)")

        // Verify server signature (position 0)
        val txHash = signedTx.hash()
        assertTrue(
            serverKeyPair.verify(txHash, signedTx.signatures[0].signature),
            "Server signature should be valid"
        )

        // Verify client domain signature (position 1 - signed FIRST after server)
        assertTrue(
            clientDomainKeyPair.verify(txHash, signedTx.signatures[1].signature),
            "Client domain signature from delegate should be valid"
        )

        // Verify client signature (position 2 - signed AFTER client domain)
        assertTrue(
            clientKeyPair.verify(txHash, signedTx.signatures[2].signature),
            "Client signature should be valid"
        )
    }

    @Test
    fun testBothClientDomainKeyPairAndDelegateProvided() = runTest {
        val serverKeyPair = KeyPair.fromSecretSeed(testServerSecretSeed)
        val clientKeyPair = KeyPair.random()
        val clientDomainKeyPair = KeyPair.random()

        val challengeXdr = createValidChallengeXdr(clientKeyPair = clientKeyPair)

        val signingDelegate = ClientDomainSigningDelegate { _ ->
            throw IllegalStateException("Should not be called")
        }

        val webAuth = WebAuth(
            authEndpoint = testAuthEndpoint,
            network = network,
            serverSigningKey = serverKeyPair.getAccountId(),
            serverHomeDomain = testHomeDomain
        )

        // Should throw when both are provided
        val exception = assertFailsWith<IllegalArgumentException> {
            webAuth.signTransaction(
                challengeXdr = challengeXdr,
                signers = listOf(clientKeyPair),
                clientDomainKeyPair = clientDomainKeyPair,
                clientDomainSigningDelegate = signingDelegate
            )
        }

        assertTrue(
            exception.message?.contains("Cannot specify both") == true,
            "Error message should explain mutual exclusivity"
        )
        assertTrue(
            exception.message?.contains("clientDomainKeyPair") == true,
            "Error message should mention clientDomainKeyPair"
        )
        assertTrue(
            exception.message?.contains("clientDomainSigningDelegate") == true,
            "Error message should mention clientDomainSigningDelegate"
        )
    }

    @Test
    fun testClientDomainRequiresSigningMethod() = runTest {
        val clientKeyPair = KeyPair.random()

        val webAuth = WebAuth(
            authEndpoint = testAuthEndpoint,
            network = network,
            serverSigningKey = KeyPair.fromSecretSeed(testServerSecretSeed).getAccountId(),
            serverHomeDomain = testHomeDomain
        )

        // Should throw when clientDomain provided without signing method
        val exception = assertFailsWith<IllegalArgumentException> {
            webAuth.jwtToken(
                clientAccountId = clientKeyPair.getAccountId(),
                signers = listOf(clientKeyPair),
                clientDomain = "wallet.example.com"
                // Missing: clientDomainKeyPair and clientDomainSigningDelegate
            )
        }

        assertTrue(
            exception.message?.contains("clientDomain") == true,
            "Error message should mention clientDomain"
        )
        assertTrue(
            exception.message?.contains("either clientDomainKeyPair or clientDomainSigningDelegate") == true,
            "Error message should explain requirement"
        )
    }

    @Test
    fun testDelegateInvalidSignatureFormat() = runTest {
        val serverKeyPair = KeyPair.fromSecretSeed(testServerSecretSeed)
        val clientKeyPair = KeyPair.random()

        val challengeXdr = createValidChallengeXdr(clientKeyPair = clientKeyPair)

        // Delegate that returns invalid base64
        val invalidDelegate = ClientDomainSigningDelegate { _ ->
            "INVALID_BASE64_XDR!!!"
        }

        val webAuth = WebAuth(
            authEndpoint = testAuthEndpoint,
            network = network,
            serverSigningKey = serverKeyPair.getAccountId(),
            serverHomeDomain = testHomeDomain
        )

        // Should throw when delegate returns invalid data
        assertFailsWith<IllegalArgumentException> {
            webAuth.signTransaction(
                challengeXdr = challengeXdr,
                signers = listOf(clientKeyPair),
                clientDomainSigningDelegate = invalidDelegate
            )
        }
    }

    @Test
    fun testDelegateMalformedXdr() = runTest {
        val serverKeyPair = KeyPair.fromSecretSeed(testServerSecretSeed)
        val clientKeyPair = KeyPair.random()

        val challengeXdr = createValidChallengeXdr(clientKeyPair = clientKeyPair)

        // Delegate that returns invalid transaction (just a single byte)
        val malformedDelegate = ClientDomainSigningDelegate { _ ->
            kotlin.io.encoding.Base64.encode(byteArrayOf(0x00))
        }

        val webAuth = WebAuth(
            authEndpoint = testAuthEndpoint,
            network = network,
            serverSigningKey = serverKeyPair.getAccountId(),
            serverHomeDomain = testHomeDomain
        )

        // Should throw when delegate returns malformed XDR
        assertFailsWith<IllegalArgumentException> {
            webAuth.signTransaction(
                challengeXdr = challengeXdr,
                signers = listOf(clientKeyPair),
                clientDomainSigningDelegate = malformedDelegate
            )
        }
    }

    @Test
    fun testDelegateWrongSignature() = runTest {
        val serverKeyPair = KeyPair.fromSecretSeed(testServerSecretSeed)
        val clientKeyPair = KeyPair.random()
        val wrongKeyPair = KeyPair.random() // Different key than expected

        val challengeXdr = createValidChallengeXdr(clientKeyPair = clientKeyPair)

        // Delegate that signs with wrong key
        val wrongSignatureDelegate = ClientDomainSigningDelegate { transactionXdr ->
            val tx = AbstractTransaction.fromEnvelopeXdr(transactionXdr, network) as Transaction

            // Sign with WRONG key
            tx.sign(wrongKeyPair)

            // Return signed transaction
            tx.toEnvelopeXdrBase64()
        }

        val webAuth = WebAuth(
            authEndpoint = testAuthEndpoint,
            network = network,
            serverSigningKey = serverKeyPair.getAccountId(),
            serverHomeDomain = testHomeDomain
        )

        // Should NOT throw - signature is valid format, just from different key
        // Server will reject during token submission
        val signedChallengeXdr = webAuth.signTransaction(
            challengeXdr = challengeXdr,
            signers = listOf(clientKeyPair),
            clientDomainSigningDelegate = wrongSignatureDelegate
        )

        val signedTx = AbstractTransaction.fromEnvelopeXdr(signedChallengeXdr, network) as Transaction

        // Should have all signatures (server + wrong client domain + client)
        assertEquals(3, signedTx.signatures.size)

        // Verify the wrong signature was added at position 1 (client domain signs first)
        val txHash = signedTx.hash()
        assertFalse(
            wrongKeyPair.verify(txHash, signedTx.signatures[1].signature).not(),
            "Signature should be from wrong key at position 1"
        )
    }

    @Test
    fun testMultipleSignersPlusDelegate() = runTest {
        val serverKeyPair = KeyPair.fromSecretSeed(testServerSecretSeed)
        val clientKeyPair = KeyPair.random()
        val signer2 = KeyPair.random()
        val signer3 = KeyPair.random()
        val clientDomainKeyPair = KeyPair.random()

        val challengeXdr = createValidChallengeXdr(clientKeyPair = clientKeyPair)

        val signingDelegate = ClientDomainSigningDelegate { transactionXdr ->
            val tx = AbstractTransaction.fromEnvelopeXdr(transactionXdr, network) as Transaction
            tx.sign(clientDomainKeyPair)
            tx.toEnvelopeXdrBase64()
        }

        val webAuth = WebAuth(
            authEndpoint = testAuthEndpoint,
            network = network,
            serverSigningKey = serverKeyPair.getAccountId(),
            serverHomeDomain = testHomeDomain
        )

        // Sign with 3 regular signers + delegate
        val signedChallengeXdr = webAuth.signTransaction(
            challengeXdr = challengeXdr,
            signers = listOf(clientKeyPair, signer2, signer3),
            clientDomainSigningDelegate = signingDelegate
        )

        val signedTx = AbstractTransaction.fromEnvelopeXdr(signedChallengeXdr, network) as Transaction

        // Verify: Should have 5 signatures (server + client domain + 3 clients)
        assertEquals(5, signedTx.signatures.size, "Should have 5 signatures")

        // Verify all signatures are valid in correct order
        // Order: server (0), client domain (1), client (2), signer2 (3), signer3 (4)
        val txHash = signedTx.hash()
        assertTrue(serverKeyPair.verify(txHash, signedTx.signatures[0].signature))
        assertTrue(clientDomainKeyPair.verify(txHash, signedTx.signatures[1].signature))
        assertTrue(clientKeyPair.verify(txHash, signedTx.signatures[2].signature))
        assertTrue(signer2.verify(txHash, signedTx.signatures[3].signature))
        assertTrue(signer3.verify(txHash, signedTx.signatures[4].signature))
    }

    @Test
    fun testDelegateExceptionHandling() = runTest {
        val serverKeyPair = KeyPair.fromSecretSeed(testServerSecretSeed)
        val clientKeyPair = KeyPair.random()

        val challengeXdr = createValidChallengeXdr(clientKeyPair = clientKeyPair)

        // Delegate that throws exception (simulating HSM unavailable)
        val failingDelegate = ClientDomainSigningDelegate { _ ->
            throw IllegalStateException("HSM unavailable")
        }

        val webAuth = WebAuth(
            authEndpoint = testAuthEndpoint,
            network = network,
            serverSigningKey = serverKeyPair.getAccountId(),
            serverHomeDomain = testHomeDomain
        )

        // Exception should propagate
        val exception = assertFailsWith<IllegalStateException> {
            webAuth.signTransaction(
                challengeXdr = challengeXdr,
                signers = listOf(clientKeyPair),
                clientDomainSigningDelegate = failingDelegate
            )
        }

        assertEquals("HSM unavailable", exception.message)
    }

    @Test
    fun testBackwardCompatibilityWithoutDelegate() = runTest {
        val serverKeyPair = KeyPair.fromSecretSeed(testServerSecretSeed)
        val clientKeyPair = KeyPair.random()

        val challengeXdr = createValidChallengeXdr(clientKeyPair = clientKeyPair)

        val webAuth = WebAuth(
            authEndpoint = testAuthEndpoint,
            network = network,
            serverSigningKey = serverKeyPair.getAccountId(),
            serverHomeDomain = testHomeDomain
            // No delegate
        )

        // Should work exactly as before - without delegate
        val signedChallengeXdr = webAuth.signTransaction(
            challengeXdr = challengeXdr,
            signers = listOf(clientKeyPair)
        )

        val signedTx = AbstractTransaction.fromEnvelopeXdr(signedChallengeXdr, network) as Transaction

        // Should have only 2 signatures (server + client)
        assertEquals(2, signedTx.signatures.size, "Should have 2 signatures (no client domain)")

        val txHash = signedTx.hash()
        assertTrue(serverKeyPair.verify(txHash, signedTx.signatures[0].signature))
        assertTrue(clientKeyPair.verify(txHash, signedTx.signatures[1].signature))
    }

    @Test
    fun testLocalKeyPairStillWorks() = runTest {
        val serverKeyPair = KeyPair.fromSecretSeed(testServerSecretSeed)
        val clientKeyPair = KeyPair.random()
        val clientDomainKeyPair = KeyPair.random()

        val challengeXdr = createValidChallengeXdr(clientKeyPair = clientKeyPair)

        val webAuth = WebAuth(
            authEndpoint = testAuthEndpoint,
            network = network,
            serverSigningKey = serverKeyPair.getAccountId(),
            serverHomeDomain = testHomeDomain
        )

        // Use local keypair (existing behavior)
        val signedChallengeXdr = webAuth.signTransaction(
            challengeXdr = challengeXdr,
            signers = listOf(clientKeyPair),
            clientDomainKeyPair = clientDomainKeyPair
        )

        val signedTx = AbstractTransaction.fromEnvelopeXdr(signedChallengeXdr, network) as Transaction

        // Should have 3 signatures (server + client domain + client)
        assertEquals(3, signedTx.signatures.size)

        // Order: server (0), client domain (1), client (2)
        val txHash = signedTx.hash()
        assertTrue(serverKeyPair.verify(txHash, signedTx.signatures[0].signature))
        assertTrue(clientDomainKeyPair.verify(txHash, signedTx.signatures[1].signature))
        assertTrue(clientKeyPair.verify(txHash, signedTx.signatures[2].signature))
    }

    @Test
    fun testDelegateReceivesFullTransactionXdr() = runTest {
        val serverKeyPair = KeyPair.fromSecretSeed(testServerSecretSeed)
        val clientKeyPair = KeyPair.random()
        val clientDomainKeyPair = KeyPair.random()

        val challengeXdr = createValidChallengeXdr(clientKeyPair = clientKeyPair)
        val originalTx = AbstractTransaction.fromEnvelopeXdr(challengeXdr, network)

        var receivedXdr: String? = null

        // Delegate that captures the transaction XDR it receives
        val capturingDelegate = ClientDomainSigningDelegate { transactionXdr ->
            receivedXdr = transactionXdr

            val tx = AbstractTransaction.fromEnvelopeXdr(transactionXdr, network) as Transaction
            tx.sign(clientDomainKeyPair)
            tx.toEnvelopeXdrBase64()
        }

        val webAuth = WebAuth(
            authEndpoint = testAuthEndpoint,
            network = network,
            serverSigningKey = serverKeyPair.getAccountId(),
            serverHomeDomain = testHomeDomain
        )

        webAuth.signTransaction(
            challengeXdr = challengeXdr,
            signers = listOf(clientKeyPair),
            clientDomainSigningDelegate = capturingDelegate
        )

        // Verify delegate received full transaction XDR
        assertNotNull(receivedXdr, "Delegate should have received transaction XDR")

        // Verify the XDR can be parsed and contains only the server signature
        // (client domain signing happens BEFORE user signing now)
        val receivedTx = AbstractTransaction.fromEnvelopeXdr(receivedXdr!!, network) as Transaction

        // Should have only server signature (client domain signing happens first, before user)
        assertEquals(1, receivedTx.signatures.size, "Delegate should receive tx with only server sig")

        // Verify transaction hash matches original (same transaction, just more signatures)
        assertEquals(
            originalTx.hash().toList(),
            receivedTx.hash().toList(),
            "Transaction hash should be same"
        )
    }

    @Test
    fun testJwtTokenMutualExclusivityValidation() = runTest {
        val clientKeyPair = KeyPair.random()
        val clientDomainKeyPair = KeyPair.random()
        val signingDelegate = ClientDomainSigningDelegate { _ ->
            throw IllegalStateException("Should not be called")
        }

        val webAuth = WebAuth(
            authEndpoint = testAuthEndpoint,
            network = network,
            serverSigningKey = KeyPair.fromSecretSeed(testServerSecretSeed).getAccountId(),
            serverHomeDomain = testHomeDomain
        )

        // Should throw when both are provided to jwtToken
        val exception = assertFailsWith<IllegalArgumentException> {
            webAuth.jwtToken(
                clientAccountId = clientKeyPair.getAccountId(),
                signers = listOf(clientKeyPair),
                clientDomain = "wallet.example.com",
                clientDomainKeyPair = clientDomainKeyPair,
                clientDomainSigningDelegate = signingDelegate
            )
        }

        assertTrue(
            exception.message?.contains("Cannot specify both") == true,
            "jwtToken should validate mutual exclusivity"
        )
    }

    @Test
    fun testInstanceLevelDelegateInWebAuthConstructor() = runTest {
        val serverKeyPair = KeyPair.fromSecretSeed(testServerSecretSeed)
        val clientKeyPair = KeyPair.random()
        val clientDomainKeyPair = KeyPair.random()

        val challengeXdr = createValidChallengeXdr(clientKeyPair = clientKeyPair)

        // Create delegate
        val signingDelegate = ClientDomainSigningDelegate { transactionXdr ->
            val tx = AbstractTransaction.fromEnvelopeXdr(transactionXdr, network) as Transaction
            tx.sign(clientDomainKeyPair)
            tx.toEnvelopeXdrBase64()
        }

        // Pass delegate in constructor
        val webAuth = WebAuth(
            authEndpoint = testAuthEndpoint,
            network = network,
            serverSigningKey = serverKeyPair.getAccountId(),
            serverHomeDomain = testHomeDomain,
            clientDomainSigningDelegate = signingDelegate
        )

        // Sign transaction without passing delegate (should use instance-level delegate)
        val signedChallengeXdr = webAuth.signTransaction(
            challengeXdr = challengeXdr,
            signers = listOf(clientKeyPair)
            // Note: No clientDomainSigningDelegate parameter
        )

        val signedTx = AbstractTransaction.fromEnvelopeXdr(signedChallengeXdr, network) as Transaction

        // Should have 2 signatures (server + client)
        // Instance-level delegate is used only in jwtToken(), not in signTransaction()
        assertEquals(2, signedTx.signatures.size, "signTransaction() doesn't use instance delegate")
    }

    @Test
    @OptIn(ExperimentalTime::class)
    fun testDelegateTransactionModificationDetected() = runTest {
        val serverKeyPair = KeyPair.fromSecretSeed(testServerSecretSeed)
        val clientKeyPair = KeyPair.random()
        val clientDomainKeyPair = KeyPair.random()

        val challengeXdr = createValidChallengeXdr(clientKeyPair = clientKeyPair)

        // Delegate that modifies the transaction (adds an operation - malicious behavior)
        val maliciousDelegate = ClientDomainSigningDelegate { transactionXdr ->
            val tx = AbstractTransaction.fromEnvelopeXdr(transactionXdr, network) as Transaction

            // Sign the transaction
            tx.sign(clientDomainKeyPair)

            // Malicious: Add extra operation to change transaction hash
            // In reality this is hard to do without breaking XDR, but we simulate by creating new tx
            val modifiedTx = AbstractTransaction.fromEnvelopeXdr(transactionXdr, network) as Transaction
            modifiedTx.sign(clientDomainKeyPair)

            // Add a dummy operation to the envelope directly (this will change the hash)
            // We can't easily add operations to Transaction, so we'll return different XDR
            val newSourceAccount = Account(serverKeyPair.getAccountId(), -1L)
            val currentTime = kotlin.time.Clock.System.now().toEpochMilliseconds() / 1000
            val modifiedBuilder = TransactionBuilder(newSourceAccount, network)
                .setBaseFee(100)
                .addTimeBounds(TimeBounds(minTime = currentTime - 300, maxTime = currentTime + 600))

            val op1 = ManageDataOperation(name = "$testHomeDomain auth", value = "test_value".encodeToByteArray())
            op1.sourceAccount = clientKeyPair.getAccountId()
            modifiedBuilder.addOperation(op1)

            // Add extra malicious operation
            val op2 = ManageDataOperation(name = "malicious", value = "bad".encodeToByteArray())
            op2.sourceAccount = serverKeyPair.getAccountId()
            modifiedBuilder.addOperation(op2)

            val maliciousTx = modifiedBuilder.build()
            maliciousTx.sign(serverKeyPair)
            maliciousTx.sign(clientKeyPair)
            maliciousTx.sign(clientDomainKeyPair)

            maliciousTx.toEnvelopeXdrBase64()
        }

        val webAuth = WebAuth(
            authEndpoint = testAuthEndpoint,
            network = network,
            serverSigningKey = serverKeyPair.getAccountId(),
            serverHomeDomain = testHomeDomain
        )

        // Should throw when delegate modifies the transaction
        val exception = assertFailsWith<IllegalStateException> {
            webAuth.signTransaction(
                challengeXdr = challengeXdr,
                signers = listOf(clientKeyPair),
                clientDomainSigningDelegate = maliciousDelegate
            )
        }

        assertTrue(
            exception.message?.contains("Transaction was modified") == true,
            "Should detect transaction modification"
        )
        assertTrue(
            exception.message?.contains("only add") == true,
            "Should explain delegate must only add signatures"
        )
    }

    @Test
    fun testDelegateNoSignaturesAdded() = runTest {
        val serverKeyPair = KeyPair.fromSecretSeed(testServerSecretSeed)
        val clientKeyPair = KeyPair.random()

        val challengeXdr = createValidChallengeXdr(clientKeyPair = clientKeyPair)

        // Delegate that doesn't add any signatures (incorrect behavior)
        val noSignatureDelegate = ClientDomainSigningDelegate { transactionXdr ->
            // Return transaction unchanged (no signature added)
            transactionXdr
        }

        val webAuth = WebAuth(
            authEndpoint = testAuthEndpoint,
            network = network,
            serverSigningKey = serverKeyPair.getAccountId(),
            serverHomeDomain = testHomeDomain
        )

        // Should throw when delegate adds no signatures
        val exception = assertFailsWith<IllegalStateException> {
            webAuth.signTransaction(
                challengeXdr = challengeXdr,
                signers = listOf(clientKeyPair),
                clientDomainSigningDelegate = noSignatureDelegate
            )
        }

        assertTrue(
            exception.message?.contains("must add at least one signature") == true,
            "Should detect that no signatures were added"
        )
        assertTrue(
            exception.message?.contains("No new signatures were found") == true,
            "Should explain no new signatures found"
        )
    }

    @Test
    fun testDelegateMultipleSignaturesAllowed() = runTest {
        val serverKeyPair = KeyPair.fromSecretSeed(testServerSecretSeed)
        val clientKeyPair = KeyPair.random()
        val clientDomainKeyPair1 = KeyPair.random()
        val clientDomainKeyPair2 = KeyPair.random()

        val challengeXdr = createValidChallengeXdr(clientKeyPair = clientKeyPair)

        // Delegate that adds TWO signatures (for multi-sig threshold account)
        val multiSigDelegate = ClientDomainSigningDelegate { transactionXdr ->
            val tx = AbstractTransaction.fromEnvelopeXdr(transactionXdr, network) as Transaction

            // Add two signatures to meet threshold requirement
            tx.sign(clientDomainKeyPair1)
            tx.sign(clientDomainKeyPair2)

            tx.toEnvelopeXdrBase64()
        }

        val webAuth = WebAuth(
            authEndpoint = testAuthEndpoint,
            network = network,
            serverSigningKey = serverKeyPair.getAccountId(),
            serverHomeDomain = testHomeDomain
        )

        // Should succeed with 2 client domain signatures
        val signedChallengeXdr = webAuth.signTransaction(
            challengeXdr = challengeXdr,
            signers = listOf(clientKeyPair),
            clientDomainSigningDelegate = multiSigDelegate
        )

        val signedTx = AbstractTransaction.fromEnvelopeXdr(signedChallengeXdr, network) as Transaction

        // Verify: Should have 4 signatures (server + 2 client domain + client)
        assertEquals(4, signedTx.signatures.size, "Should have 4 signatures (server + 2 client domain + client)")

        // Verify all signatures are valid in correct order
        // Order: server (0), client domain 1 (1), client domain 2 (2), client (3)
        val txHash = signedTx.hash()
        assertTrue(serverKeyPair.verify(txHash, signedTx.signatures[0].signature), "Server signature should be valid")
        assertTrue(clientDomainKeyPair1.verify(txHash, signedTx.signatures[1].signature), "Client domain signature 1 should be valid")
        assertTrue(clientDomainKeyPair2.verify(txHash, signedTx.signatures[2].signature), "Client domain signature 2 should be valid")
        assertTrue(clientKeyPair.verify(txHash, signedTx.signatures[3].signature), "Client signature should be valid")
    }

    @Test
    fun testDelegateReordersSignatures() = runTest {
        // Test that delegate can reorder signatures and we still extract correctly
        val serverKeyPair = KeyPair.fromSecretSeed(testServerSecretSeed)
        val clientKeyPair = KeyPair.random()
        val clientDomainKeyPair = KeyPair.random()

        val challengeXdr = createValidChallengeXdr(clientKeyPair = clientKeyPair)

        // Delegate that REORDERS signatures (adds at beginning)
        val delegate = ClientDomainSigningDelegate { txXdr ->
            val tx = AbstractTransaction.fromEnvelopeXdr(txXdr, network) as Transaction

            // Get existing signatures
            val existingSignatures = tx.signatures.toList()

            // Add client domain signature
            tx.sign(clientDomainKeyPair)
            val clientSignature = tx.signatures.last()

            // REORDER: Put client signature FIRST, then existing signatures
            tx.signatures.clear()
            tx.signatures.add(clientSignature)
            tx.signatures.addAll(existingSignatures)

            tx.toEnvelopeXdrBase64()
        }

        val webAuth = WebAuth(
            authEndpoint = testAuthEndpoint,
            network = network,
            serverSigningKey = serverKeyPair.getAccountId(),
            serverHomeDomain = testHomeDomain
        )

        // Should succeed despite reordering
        val signedChallengeXdr = webAuth.signTransaction(
            challengeXdr = challengeXdr,
            signers = listOf(clientKeyPair),
            clientDomainSigningDelegate = delegate
        )

        val signedTx = AbstractTransaction.fromEnvelopeXdr(signedChallengeXdr, network) as Transaction

        // Should have 3 signatures total
        assertEquals(3, signedTx.signatures.size, "Should have 3 signatures total")

        // Verify client domain signature was added (should be in the transaction somewhere)
        val txHash = signedTx.hash()
        val hasClientDomainSig = signedTx.signatures.any { sig ->
            clientDomainKeyPair.verify(txHash, sig.signature)
        }
        assertTrue(hasClientDomainSig, "Client domain signature should be present")

        // Verify server signature is present
        val hasServerSig = signedTx.signatures.any { sig ->
            serverKeyPair.verify(txHash, sig.signature)
        }
        assertTrue(hasServerSig, "Server signature should be present")

        // Verify client signature is present
        val hasClientSig = signedTx.signatures.any { sig ->
            clientKeyPair.verify(txHash, sig.signature)
        }
        assertTrue(hasClientSig, "Client signature should be present")
    }

    @Test
    fun testDelegateDuplicateSignaturePrevented() = runTest {
        // Test that if delegate returns a signature that already exists,
        // we don't add a duplicate
        val serverKeyPair = KeyPair.fromSecretSeed(testServerSecretSeed)
        val clientKeyPair = KeyPair.random()

        val challengeXdr = createValidChallengeXdr(clientKeyPair = clientKeyPair)

        // Delegate that tries to re-add the server signature (already exists)
        val delegate = ClientDomainSigningDelegate { txXdr ->
            // Just return the transaction unchanged
            // This simulates a delegate that doesn't add any new signatures
            txXdr
        }

        val webAuth = WebAuth(
            authEndpoint = testAuthEndpoint,
            network = network,
            serverSigningKey = serverKeyPair.getAccountId(),
            serverHomeDomain = testHomeDomain
        )

        // Should fail because no new signatures were added
        val exception = assertFailsWith<IllegalStateException> {
            webAuth.signTransaction(
                challengeXdr = challengeXdr,
                signers = listOf(clientKeyPair),
                clientDomainSigningDelegate = delegate
            )
        }

        assertTrue(exception.message?.contains("No new signatures were found") == true,
            "Should detect no new signatures")
    }

    @Test
    fun testDelegateAddsMultipleSignaturesWithReordering() = runTest {
        // Test that delegate can add multiple signatures AND reorder them
        val serverKeyPair = KeyPair.fromSecretSeed(testServerSecretSeed)
        val clientKeyPair = KeyPair.random()
        val clientDomainKeyPair1 = KeyPair.random()
        val clientDomainKeyPair2 = KeyPair.random()

        val challengeXdr = createValidChallengeXdr(clientKeyPair = clientKeyPair)

        // Delegate that adds TWO signatures and reorders them
        val delegate = ClientDomainSigningDelegate { txXdr ->
            val tx = AbstractTransaction.fromEnvelopeXdr(txXdr, network) as Transaction

            // Get existing signatures
            val existingSignatures = tx.signatures.toList()

            // Add two client domain signatures
            tx.sign(clientDomainKeyPair1)
            val sig1 = tx.signatures.last()
            tx.sign(clientDomainKeyPair2)
            val sig2 = tx.signatures.last()

            // REORDER: Put new signatures at beginning
            tx.signatures.clear()
            tx.signatures.add(sig2)
            tx.signatures.add(sig1)
            tx.signatures.addAll(existingSignatures)

            tx.toEnvelopeXdrBase64()
        }

        val webAuth = WebAuth(
            authEndpoint = testAuthEndpoint,
            network = network,
            serverSigningKey = serverKeyPair.getAccountId(),
            serverHomeDomain = testHomeDomain
        )

        // Should succeed despite reordering
        val signedChallengeXdr = webAuth.signTransaction(
            challengeXdr = challengeXdr,
            signers = listOf(clientKeyPair),
            clientDomainSigningDelegate = delegate
        )

        val signedTx = AbstractTransaction.fromEnvelopeXdr(signedChallengeXdr, network) as Transaction

        // Should have 4 signatures total (server + 2 client domain + client)
        assertEquals(4, signedTx.signatures.size, "Should have 4 signatures total")

        // Verify all expected signatures are present (order doesn't matter)
        val txHash = signedTx.hash()
        val hasServerSig = signedTx.signatures.any { serverKeyPair.verify(txHash, it.signature) }
        val hasClientDomain1Sig = signedTx.signatures.any { clientDomainKeyPair1.verify(txHash, it.signature) }
        val hasClientDomain2Sig = signedTx.signatures.any { clientDomainKeyPair2.verify(txHash, it.signature) }
        val hasClientSig = signedTx.signatures.any { clientKeyPair.verify(txHash, it.signature) }

        assertTrue(hasServerSig, "Server signature should be present")
        assertTrue(hasClientDomain1Sig, "Client domain signature 1 should be present")
        assertTrue(hasClientDomain2Sig, "Client domain signature 2 should be present")
        assertTrue(hasClientSig, "Client signature should be present")
    }
}
