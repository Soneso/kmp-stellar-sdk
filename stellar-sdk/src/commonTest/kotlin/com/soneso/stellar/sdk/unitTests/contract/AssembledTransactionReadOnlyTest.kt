//
//  AssembledTransactionReadOnlyTest.kt
//  Stellar SDK Kotlin Multiplatform
//
//  Copyright (c) 2026 Soneso. All rights reserved.
//

package com.soneso.stellar.sdk.unitTests.contract

import com.ionspin.kotlin.bignum.integer.BigInteger
import com.soneso.stellar.sdk.Address
import com.soneso.stellar.sdk.Auth
import com.soneso.stellar.sdk.InvokeHostFunctionOperation
import com.soneso.stellar.sdk.KeyPair
import com.soneso.stellar.sdk.Network
import com.soneso.stellar.sdk.contract.AssembledTransaction
import com.soneso.stellar.sdk.contract.ContractClient
import com.soneso.stellar.sdk.contract.SACClient
import com.soneso.stellar.sdk.contract.exception.NoSignatureNeededException
import com.soneso.stellar.sdk.scval.Scv
import com.soneso.stellar.sdk.xdr.SCValXdr
import com.soneso.stellar.sdk.xdr.SorobanCredentialsXdr
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for the read-only state of [AssembledTransaction], built through
 * [ContractClient.forSpecEntries] with the embedded Stellar Asset Contract spec on a [SacRpc]
 * server. A read-only transaction simulates without an account request and refuses to sign,
 * submit, or restore; every test pins the requests the server received.
 */
class AssembledTransactionReadOnlyTest {

    private val sacId = "CDLZFC3SYJYDZT7K67VZ75HPJVIEUVNIXF47ZG2FB2RMQQVU2HHGCYSC"

    /** A [SacRpc] whose every simulateTransaction request gets the result [simulateResult]. */
    private fun mockRpc(sourceAccount: String, simulateResult: String) =
        SacRpc(sourceAccount, listOf(rpcResult(simulateResult)))

    private suspend fun client(rpc: SacRpc, readOnly: Boolean): ContractClient = ContractClient.forSpecEntries(
        sacId, REFUSING_RPC_URL, Network.TESTNET, SACClient.specEntries(), readOnly, rpc.server
    )

    /**
     * Builds and simulates a transfer of one base unit from [from] to a random account, with
     * [source] as its source.
     */
    private suspend fun ContractClient.transfer(source: String, from: String = source): AssembledTransaction<SCValXdr> =
        buildInvoke(
            functionName = "transfer",
            arguments = mapOf(
                "from" to Address(from),
                "to" to Address(KeyPair.random().getAccountId()),
                "amount" to BigInteger.ONE
            ),
            source = source,
            signer = null
        )

    @Test
    fun testAReadOnlyBuildSendsOnlyTheSimulationAtSequenceOne() = runTest {
        val source = KeyPair.random().getAccountId()
        val rpc = mockRpc(source, simulateResultJson(Scv.toUint32(7u)))
        val transaction = client(rpc, readOnly = true)
            .buildInvoke<SCValXdr>("decimals", emptyMap(), source, signer = null)

        assertEquals(listOf("simulateTransaction"), rpc.methods)
        transaction.simulate()

        assertEquals(listOf("simulateTransaction", "simulateTransaction"), rpc.methods)
        val simulated = rpc.simulations()
        assertEquals(2, simulated.size)
        simulated.forEach {
            assertEquals(source, it.sourceAccount)
            assertEquals(1L, it.transaction.seqNum.value.value)
        }
        assertEquals(7u, Scv.fromUint32(transaction.result()))
    }

    @Test
    fun testAReadOnlyWriteRefusesToSignSubmitOrRestore() = runTest {
        val source = KeyPair.random().getAccountId()
        val rpc = mockRpc(source, simulateResultJson(Scv.toVoid(), listOf(writeAuthEntry(sacId))))
        val transaction = client(rpc, readOnly = true).transfer(source)
        val attempts: List<Pair<String, suspend () -> Unit>> = listOf(
            "sign()" to { transaction.sign() },
            "sign(force = true)" to { transaction.sign(force = true) },
            "sign(KeyPair.random())" to { transaction.sign(KeyPair.random()) },
            "signAndSubmit()" to { transaction.signAndSubmit() },
            "restoreFootprint()" to { transaction.restoreFootprint() }
        )

        for ((name, attempt) in attempts) {
            val failure = assertFailsWith<IllegalStateException>(name) { attempt() }
            assertEquals(readOnlyRefusal(source), failure.message, name)
            assertNull(transaction.signed, name)
        }

        // A hand signature on the built transaction does not get it sent either.
        transaction.builtTransaction!!.sign(KeyPair.random())
        val failure = assertFailsWith<IllegalStateException> { transaction.submit() }
        assertEquals(readOnlyRefusal(source), failure.message)
        assertNull(transaction.signed)
        assertNull(transaction.sendTransactionResponse)
        assertEquals(listOf("simulateTransaction"), rpc.methods)
    }

    @Test
    fun testARestorePreambleWithRestoreThrowsTheRefusalUnwrapped() = runTest {
        val source = KeyPair.random().getAccountId()
        val rpc = mockRpc(source, restorePreambleResultJson(listOf(writeAuthEntry(sacId))))

        val failure = assertFailsWith<IllegalStateException> { client(rpc, readOnly = true).transfer(source) }

        assertEquals(readOnlyRefusal(source), failure.message)
        assertNull(failure.cause)
        assertEquals(listOf("simulateTransaction"), rpc.methods)
    }

    @Test
    fun testAReadOnlyReadCallKeepsTheNoSignatureNeededRefusalAndRefusesAForcedSignature() = runTest {
        val source = KeyPair.random().getAccountId()
        val rpc = mockRpc(source, simulateResultJson(Scv.toUint32(7u)))
        val transaction = client(rpc, readOnly = true)
            .buildInvoke<SCValXdr>("decimals", emptyMap(), source, signer = null)

        val failure = assertFailsWith<NoSignatureNeededException> { transaction.sign() }

        assertEquals(
            "This is a read call. It requires no signature or submitting. Set force=true to sign and submit anyway.",
            failure.message
        )

        // force skips the read-call check only; the read-only refusal still holds.
        val forced: List<Pair<String, suspend () -> Unit>> = listOf(
            "sign(force = true)" to { transaction.sign(force = true) },
            "sign(KeyPair.random(), force = true)" to { transaction.sign(KeyPair.random(), force = true) },
            "signAndSubmit(force = true)" to { transaction.signAndSubmit(force = true) }
        )
        for ((name, attempt) in forced) {
            val refusal = assertFailsWith<IllegalStateException>(name) { attempt() }
            assertEquals(readOnlyRefusal(source), refusal.message, name)
            assertNull(transaction.signed, name)
        }
        assertEquals(listOf("simulateTransaction"), rpc.methods)
    }

    @Test
    fun testAReadOnlyTransactionSignsTheAuthEntryOfAnotherAccount() = runTest {
        val source = SACClient.READ_ONLY_SOURCE_ACCOUNT_ID
        val sender = KeyPair.random()
        val senderEntry = unsignedAddressAuthEntry(sacId, sender.getAccountId())
        val rpc = mockRpc(source, simulateResultJson(Scv.toVoid(), listOf(senderEntry)))
        val transaction = client(rpc, readOnly = true).transfer(source = source, from = sender.getAccountId())
        assertEquals(source, transaction.builtTransaction!!.sourceAccount)
        assertEquals(setOf(sender.getAccountId()), transaction.needsNonInvokerSigningBy())

        transaction.signAuthEntries(sender, validUntilLedgerSequence = 5000L)

        val operation = assertIs<InvokeHostFunctionOperation>(transaction.builtTransaction!!.operations.single())
        val credentials = assertIs<SorobanCredentialsXdr.Address>(operation.auth.single().credentials).value
        assertEquals(5000u, credentials.signatureExpirationLedger.value)
        assertTrue(Auth.carriesSignature(credentials.signature))
        assertEquals(emptySet(), transaction.needsNonInvokerSigningBy())
        assertEquals(listOf("simulateTransaction"), rpc.methods)
    }

    @Test
    fun testWithoutTheFlagTheZeroKeySourceLoadsItsAccount() = runTest {
        val source = SACClient.READ_ONLY_SOURCE_ACCOUNT_ID
        val rpc = mockRpc(source, simulateResultJson(Scv.toUint32(7u)))

        client(rpc, readOnly = false).buildInvoke<SCValXdr>("decimals", emptyMap(), source, signer = null)

        assertEquals(listOf("getLedgerEntries", "getLedgerEntries", "simulateTransaction"), rpc.methods)
        val simulated = rpc.simulations().single()
        assertEquals(source, simulated.sourceAccount)
        assertEquals(MOCK_RPC_SOURCE_SEQ + 1, simulated.transaction.seqNum.value.value)
    }
}
