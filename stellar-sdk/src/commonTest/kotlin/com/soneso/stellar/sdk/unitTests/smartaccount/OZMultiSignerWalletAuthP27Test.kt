//
//  OZMultiSignerWalletAuthP27Test.kt
//  Stellar SDK Kotlin Multiplatform
//
//  Copyright (c) 2026 Soneso. All rights reserved.
//

package com.soneso.stellar.sdk.unitTests.smartaccount

import com.soneso.stellar.sdk.Address
import com.soneso.stellar.sdk.InvokeHostFunctionOperation
import com.soneso.stellar.sdk.KeyPair
import com.soneso.stellar.sdk.Network
import com.soneso.stellar.sdk.Transaction
import com.soneso.stellar.sdk.scval.Scv
import com.soneso.stellar.sdk.smartaccount.oz.ConnectedWallet
import com.soneso.stellar.sdk.smartaccount.oz.ExternalWalletAdapter
import com.soneso.stellar.sdk.smartaccount.oz.OZSmartAccountConfig
import com.soneso.stellar.sdk.smartaccount.oz.OZSmartAccountKit
import com.soneso.stellar.sdk.smartaccount.oz.SelectedSigner
import com.soneso.stellar.sdk.smartaccount.oz.SignAuthEntryOptions
import com.soneso.stellar.sdk.smartaccount.oz.SignAuthEntryResult
import com.soneso.stellar.sdk.xdr.SCValXdr
import com.soneso.stellar.sdk.xdr.SorobanAddressCredentialsXdr
import com.soneso.stellar.sdk.xdr.SorobanAuthorizationEntryXdr
import com.soneso.stellar.sdk.xdr.SorobanAuthorizedFunctionXdr
import com.soneso.stellar.sdk.xdr.SorobanAuthorizedInvocationXdr
import com.soneso.stellar.sdk.xdr.SorobanCredentialsXdr
import com.soneso.stellar.sdk.xdr.SCSymbolXdr
import com.soneso.stellar.sdk.xdr.InvokeContractArgsXdr
import com.soneso.stellar.sdk.xdr.Int64Xdr
import com.soneso.stellar.sdk.xdr.Uint32Xdr
import com.soneso.stellar.sdk.xdr.toXdrBase64
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Protocol 27 coverage for the wallet-address branch of [OZMultiSignerManager.submitWithMultipleSigners].
 *
 * When a simulated auth entry's address belongs to a connected wallet (a G-address that is
 * not the smart account contract), the manager signs it directly through the external wallet
 * adapter rather than via the smart account's __check_auth indirection. This drives that
 * branch end-to-end against a sequenced RPC mock and asserts the wallet's Ed25519 signature
 * is written into the entry under its original credential arm.
 */
@OptIn(ExperimentalEncodingApi::class)
class OZMultiSignerWalletAuthP27Test {

    // The wallet whose G-address matches the auth entry produced by simulation.
    // Initialized per-test inside runTest because KeyPair.fromSecretSeed is suspend.
    private lateinit var walletKeyPair: KeyPair
    private val walletAddress: String get() = walletKeyPair.getAccountId()

    /**
     * Wallet adapter that signs the supplied preimage XDR with [walletKeyPair], producing a
     * valid Ed25519 signature returned base64-encoded as the signed auth entry payload.
     */
    private inner class SigningWalletAdapter : ExternalWalletAdapter {
        var signCallCount = 0
            private set

        private val wallet = ConnectedWallet(
            address = walletAddress,
            walletId = "test-wallet",
            walletName = "Test Wallet"
        )

        override suspend fun connect(): ConnectedWallet? = wallet
        override suspend fun disconnect() {}
        override fun canSignFor(address: String): Boolean = address == walletAddress
        override fun getConnectedWallets(): List<ConnectedWallet> = listOf(wallet)
        override fun getWalletForAddress(address: String): ConnectedWallet? =
            if (address == walletAddress) wallet else null

        override suspend fun signAuthEntry(
            preimageXdr: String,
            options: SignAuthEntryOptions?
        ): SignAuthEntryResult {
            signCallCount++
            val preimageBytes = Base64.decode(preimageXdr)
            val signature = walletKeyPair.sign(com.soneso.stellar.sdk.Util.hash(preimageBytes))
            return SignAuthEntryResult(
                signedAuthEntry = Base64.encode(signature),
                signerAddress = walletAddress
            )
        }
    }

    /** An auth entry whose top-level credential address is the wallet G-address. */
    private fun walletAuthEntry(): SorobanAuthorizationEntryXdr {
        val scAddress = Address(walletAddress).toSCAddress()
        return SorobanAuthorizationEntryXdr(
            credentials = SorobanCredentialsXdr.Address(
                SorobanAddressCredentialsXdr(
                    address = scAddress,
                    nonce = Int64Xdr(987654321L),
                    signatureExpirationLedger = Uint32Xdr(0u),
                    signature = Scv.toVoid()
                )
            ),
            rootInvocation = SorobanAuthorizedInvocationXdr(
                function = SorobanAuthorizedFunctionXdr.ContractFn(
                    InvokeContractArgsXdr(
                        contractAddress = Address(VERIFIER_B).toSCAddress(),
                        functionName = SCSymbolXdr("transfer"),
                        args = emptyList()
                    )
                ),
                subInvocations = emptyList()
            )
        )
    }

    /**
     * Decodes the auth entries the manager actually submitted, from the captured
     * sendTransaction JSON-RPC request body. The `params.transaction` field holds the
     * signed transaction envelope; the InvokeHostFunctionOperation in it carries the
     * auth entries handed to the network.
     */
    private fun submittedAuthEntries(sendBody: String): List<SorobanAuthorizationEntryXdr> {
        val root = Json.parseToJsonElement(sendBody).jsonObject
        val envelopeB64 = root["params"]!!.jsonObject["transaction"]!!.jsonPrimitive.content
        val tx = Transaction.fromEnvelopeXdr(envelopeB64, Network.TESTNET)
        val op = tx.operations.first { it is InvokeHostFunctionOperation } as InvokeHostFunctionOperation
        return op.auth
    }

    private fun buildConfig(deployer: KeyPair, adapter: ExternalWalletAdapter): OZSmartAccountConfig =
        OZSmartAccountConfig(
            rpcUrl = "https://soroban-testnet.stellar.org",
            networkPassphrase = Network.TESTNET.networkPassphrase,
            accountWasmHash = "a" + "0".repeat(63),
            webauthnVerifierAddress = VERIFIER_A,
            externalWallet = adapter,
            deployerKeypair = deployer
        )

    @Test
    fun test_submitWithMultipleSigners_walletAddressEntry_signedViaAdapter() = runTest {
        walletKeyPair = KeyPair.fromSecretSeed("SDJHRQF4GCMIIKAAAQ6IHY42X73FQFLHUULAPSKKD4DFDM7UXWWCRHBE")
        val deployer = KeyPair.random()
        val adapter = SigningWalletAdapter()

        val accountXdr = buildAccountEntryXdr(deployer).toXdrBase64()
        val walletEntry = walletAuthEntry()
        val authEntryXdr = walletEntry.toXdrBase64()
        val sorobanDataXdr = buildMinimalSorobanData().toXdrBase64()
        val countZeroXdr = SCValXdr.U32(Uint32Xdr(0u)).toXdrBase64()
        val txHash = "b4721e2a61e9a6b3c6c2e5c0d4c0a5f3e2d1c0b9a8f7e6d5c4b3a2f1e0d9c8b7"

        var capturedSendBody: String? = null
        val mockServer = buildSequentialMockServerWithSubmission(
            accountXdrBase64 = accountXdr,
            authEntryBase64 = authEntryXdr,
            sorobanDataBase64 = sorobanDataXdr,
            countXdrBase64 = countZeroXdr,
            txHash = txHash,
            onSendTransaction = { capturedSendBody = it }
        )

        val kit = OZSmartAccountKit.createWithServer(
            config = buildConfig(deployer = deployer, adapter = adapter),
            sorobanServer = mockServer
        )
        // The connected smart account contract is VERIFIER_B; the wallet entry address
        // differs from it, routing the entry into the direct wallet-signing branch.
        kit.setConnectedState("test-credential-id", VERIFIER_B)

        val result = kit.multiSignerManager.submitWithMultipleSigners(
            hostFunction = stubHostFunction(VERIFIER_B),
            selectedSigners = listOf(SelectedSigner.Wallet(address = walletAddress)),
            resolveContextRuleIds = { _, _ -> emptyList() }
        )

        assertTrue(result.success, "TransactionResult must be successful; error=${result.error}")
        assertEquals(txHash, result.hash)
        assertEquals(1, adapter.signCallCount, "wallet adapter must be asked to sign exactly once")

        // Inspect the actual entry that was submitted to the network.
        assertNotNull(capturedSendBody, "sendTransaction request must have been captured")
        val submitted = submittedAuthEntries(capturedSendBody!!)
        val walletEntrySubmitted = submitted.first {
            it.credentials is SorobanCredentialsXdr.Address &&
                Address.fromSCAddress(
                    (it.credentials as SorobanCredentialsXdr.Address).value.address
                ).toString() == walletAddress
        }
        val submittedCreds = (walletEntrySubmitted.credentials as SorobanCredentialsXdr.Address).value
        // The original credential arm is preserved (legacy ADDRESS, not rewritten to V2).
        // The expiration is stamped away from the original 0, and the wallet's signature
        // is written in (non-void).
        assertTrue(
            submittedCreds.signatureExpirationLedger.value > 0u,
            "submitted entry must carry a stamped (non-zero) expiration"
        )
        assertTrue(
            submittedCreds.signature !is SCValXdr.Void,
            "submitted wallet entry must carry the wallet signature (non-void)"
        )
    }

    @Test
    fun test_submit_nonContractAuthEntry_extractsAddressCredentialsAndPassesThrough() = runTest {
        // OZTransactionOperations.submit extracts the inner address credentials for every
        // simulated auth entry across all arms. An entry whose address is not the smart
        // account contract is passed through unchanged; this exercises the arm-aware
        // addressCredentials() extraction at the head of the signing loop.
        walletKeyPair = KeyPair.fromSecretSeed("SDJHRQF4GCMIIKAAAQ6IHY42X73FQFLHUULAPSKKD4DFDM7UXWWCRHBE")
        val deployer = KeyPair.random()
        val adapter = SigningWalletAdapter()

        val accountXdr = buildAccountEntryXdr(deployer).toXdrBase64()
        // The simulated auth entry's address is the wallet G-address, which differs from the
        // connected contract VERIFIER_B, so the submit signing loop passes it through.
        val authEntryXdr = walletAuthEntry().toXdrBase64()
        val sorobanDataXdr = buildMinimalSorobanData().toXdrBase64()
        val countZeroXdr = SCValXdr.U32(Uint32Xdr(0u)).toXdrBase64()
        val txHash = "d4721e2a61e9a6b3c6c2e5c0d4c0a5f3e2d1c0b9a8f7e6d5c4b3a2f1e0d9c8b7"

        var capturedSendBody: String? = null
        val mockServer = buildSequentialMockServerWithSubmission(
            accountXdrBase64 = accountXdr,
            authEntryBase64 = authEntryXdr,
            sorobanDataBase64 = sorobanDataXdr,
            countXdrBase64 = countZeroXdr,
            txHash = txHash,
            onSendTransaction = { capturedSendBody = it }
        )

        val kit = OZSmartAccountKit.createWithServer(
            config = buildConfig(deployer = deployer, adapter = adapter),
            sorobanServer = mockServer
        )
        kit.setConnectedState("test-credential-id", VERIFIER_B)

        val result = kit.transactionOperations.submit(
            hostFunction = stubHostFunction(VERIFIER_B),
            auth = emptyList()
        )

        assertTrue(result.success, "TransactionResult must be successful; error=${result.error}")
        assertEquals(txHash, result.hash)

        // The submitted entry whose address is not the connected contract is passed through
        // unchanged: its credential arm, address and nonce survive the signing loop intact.
        assertNotNull(capturedSendBody, "sendTransaction request must have been captured")
        val submitted = submittedAuthEntries(capturedSendBody!!)
        val original = walletAuthEntry()
        val originalCreds = (original.credentials as SorobanCredentialsXdr.Address).value
        val passedThrough = submitted.first {
            it.credentials is SorobanCredentialsXdr.Address &&
                Address.fromSCAddress(
                    (it.credentials as SorobanCredentialsXdr.Address).value.address
                ).toString() == walletAddress
        }
        val passedCreds = (passedThrough.credentials as SorobanCredentialsXdr.Address).value
        // Credential arm preserved (still legacy ADDRESS).
        assertTrue(passedThrough.credentials is SorobanCredentialsXdr.Address)
        // Address and nonce unchanged from the simulated entry.
        assertEquals(originalCreds.nonce.value, passedCreds.nonce.value)
        assertEquals(walletAddress, Address.fromSCAddress(passedCreds.address).toString())
        // No signature was added: this signer was not the connected contract, so the entry
        // is forwarded with its original void signature.
        assertTrue(
            passedCreds.signature is SCValXdr.Void,
            "pass-through entry must keep its original void signature"
        )
    }
}
