//
//  OZAuthDigestLiveIntegrationTest.kt
//  Stellar SDK Kotlin Multiplatform
//
//  Copyright (c) 2026 Soneso. All rights reserved.
//

package com.soneso.stellar.sdk.integrationTests

import com.ionspin.kotlin.bignum.integer.BigInteger
import com.soneso.stellar.sdk.AbstractTransaction
import com.soneso.stellar.sdk.Address
import com.soneso.stellar.sdk.AssetTypeNative
import com.soneso.stellar.sdk.FeeBumpTransaction
import com.soneso.stellar.sdk.InvokeHostFunctionOperation
import com.soneso.stellar.sdk.KeyPair
import com.soneso.stellar.sdk.Network
import com.soneso.stellar.sdk.Transaction
import com.soneso.stellar.sdk.TransactionBuilder
import com.soneso.stellar.sdk.Util
import com.soneso.stellar.sdk.addressCredentials
import com.soneso.stellar.sdk.contract.ContractClient
import com.soneso.stellar.sdk.rpc.SorobanServer
import com.soneso.stellar.sdk.rpc.assembleTransaction
import com.soneso.stellar.sdk.rpc.responses.GetTransactionStatus
import com.soneso.stellar.sdk.scval.Scv
import com.soneso.stellar.sdk.secureRandomBytes
import com.soneso.stellar.sdk.smartaccount.core.AuthDigestScheme
import com.soneso.stellar.sdk.smartaccount.core.ContractErrorCodes
import com.soneso.stellar.sdk.smartaccount.core.DelegatedSigner
import com.soneso.stellar.sdk.smartaccount.core.ExternalSigner
import com.soneso.stellar.sdk.smartaccount.core.SmartAccountAuth
import com.soneso.stellar.sdk.smartaccount.core.SmartAccountAuthPayloadCodec
import com.soneso.stellar.sdk.smartaccount.core.SmartAccountSigner
import com.soneso.stellar.sdk.smartaccount.oz.AllowCredential
import com.soneso.stellar.sdk.smartaccount.oz.ConnectedWallet
import com.soneso.stellar.sdk.smartaccount.oz.ContextRuleType
import com.soneso.stellar.sdk.smartaccount.oz.ExternalWalletAdapter
import com.soneso.stellar.sdk.smartaccount.oz.OZBuilders
import com.soneso.stellar.sdk.smartaccount.oz.OZConstants
import com.soneso.stellar.sdk.smartaccount.oz.OZIndexerClient
import com.soneso.stellar.sdk.smartaccount.oz.OZPolicyManager
import com.soneso.stellar.sdk.smartaccount.oz.OZRelayerClient
import com.soneso.stellar.sdk.smartaccount.oz.OZSmartAccountConfig
import com.soneso.stellar.sdk.smartaccount.oz.OZSmartAccountKit
import com.soneso.stellar.sdk.smartaccount.oz.PolicyInstallParams
import com.soneso.stellar.sdk.smartaccount.oz.SelectedSigner
import com.soneso.stellar.sdk.smartaccount.oz.SignAuthEntryOptions
import com.soneso.stellar.sdk.smartaccount.oz.SignAuthEntryResult
import com.soneso.stellar.sdk.smartaccount.oz.TransactionResult
import com.soneso.stellar.sdk.smartaccount.oz.WebAuthnAuthenticationResult
import com.soneso.stellar.sdk.smartaccount.oz.WebAuthnProvider
import com.soneso.stellar.sdk.smartaccount.oz.WebAuthnRegistrationResult
import com.soneso.stellar.sdk.unitTests.smartaccount.IndependentAuthDigest
import com.soneso.stellar.sdk.util.TestResourceUtil
import com.soneso.stellar.sdk.xdr.SCBytesXdr
import com.soneso.stellar.sdk.xdr.SCValXdr
import com.soneso.stellar.sdk.xdr.SorobanAuthorizationEntryXdr
import com.soneso.stellar.sdk.xdr.toXdrBase64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import java.math.BigInteger as JavaBigInteger
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.Signature
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/**
 * Live gate for signing on OpenZeppelin stellar-accounts wallets on testnet.
 *
 * Every case deploys a wallet of its own from a Friendbot-funded deployer, with one signer on the
 * Default rule, and funds it with native asset through the asset contract before it signs. A
 * case passes only when the network accepts the signatures the SDK produced, so a passing
 * transfer proves that the signer signed the digest the wallet's code checks.
 *
 * - Case A: passkey signer created through `createWallet`, one transfer through `submit`.
 * - Case B: Ed25519 External signer set by the constructor, one transfer through the
 *   multi-signer path. On a 0.8.0 wallet, case F follows on the entry B signed: the wallet's
 *   `auth_digest` view is simulated for that entry and compared with the local digest.
 * - Case C: Delegated signer (a Friendbot-funded account) set by the constructor, one transfer
 *   through the multi-signer path.
 * - Case D: as B; the signer adds a `CallContract` rule for the native asset contract with a
 *   second Ed25519 signer and a spending-limit policy, the second signer transfers under the
 *   limit with that rule selected explicitly, and a transfer over the limit is rejected.
 * - Case E (0.8.0 only): as B; the signer adds a `CreateContractExternalRef` rule with a second
 *   Ed25519 signer, and the second signer deploys a contract by that external reference with
 *   the rule found by resolution.
 *
 * The 0.7 regression runs cases A to D on wallets of the stellar-accounts 0.7.0 build. The smoke
 * test runs a deployment and a transfer through the Soneso relayer proxy and two lookups on the
 * Mercury indexer.
 *
 * The software passkey ([SoftwareP256WebAuthnProvider]) uses the JVM's P-256 implementation, so
 * the class runs on the JVM target only. Every key is generated at run time and never printed;
 * the `RESULT` lines name the case, the wallet and the transaction hash or the error.
 */
class OZAuthDigestLiveIntegrationTest {

    /** An account build with the spending-limit policy its rules use. */
    private class AccountBuild(val label: String, val wasmHash: String, val spendingLimitPolicy: String)

    private val rpcUrl = "https://soroban-testnet.stellar.org"
    private val network = Network.TESTNET
    private val webauthnVerifier = "CB26VN37RCVNTHJZDEPK6IRO2MMTS3Z2IEO5JD5BINY2OOJ5KKJG7NKY"
    private val ed25519Verifier = "CAW2Z46INPO5VIJEILMYSSEOLBVJIIII5GOE3TN5EUURSRM2FJCF7AJ6"
    private val relayerUrl = "https://smart-account-relayer-proxy.soneso.workers.dev"

    private val v080 = AccountBuild(
        label = "0.8.0",
        wasmHash = "c514d35ea67eaea9bfc1fb3327854c0c06111bc1ddebd4fe217c504c1004b9e1",
        spendingLimitPolicy = "CC3CEYQAP6NPNFIYSWL5MVZRCPJZGWX4XO7WDEJOUVGO2FZX5OONYPA5"
    )
    private val v070 = AccountBuild(
        label = "0.7.0",
        wasmHash = "86b49fe03f7df0ad1c2a28bd8361b923ab57096e09f397f92f0c00ae3bd06d28",
        spendingLimitPolicy = "CBQE7L3UNP5IR4I7IBKLS7NV256WHR5TTH26HTMUIK7WXJC6J64RSE2L"
    )

    /** The 0.7.0 build with the 0.8.0 spending-limit policy on its rule. */
    private val v070WithV080Policy = AccountBuild(
        label = "0.7.0 with the 0.8.0 spending limit",
        wasmHash = v070.wasmHash,
        spendingLimitPolicy = v080.spendingLimitPolicy
    )

    /** Spending limit of case D: 10 XLM in stroops per day. */
    private val spendingLimitStroops = BigInteger.fromLong(100_000_000L)
    private val spendingPeriodLedgers = 17_280u

    // ========================================================================
    // 0.8.0 cases
    // ========================================================================

    @Test
    fun testV080CaseA_passkeyTransfer() = live { caseA(v080) }

    @Test
    fun testV080CaseB_ed25519Transfer_thenCaseF_authDigestView() = live {
        val signed = caseB(v080)
        caseF(signed)
    }

    @Test
    fun testV080CaseC_delegatedTransfer() = live { caseC(v080) }

    @Test
    fun testV080CaseD_ruleWithSpendingLimit() = live { caseD(v080) }

    @Test
    fun testV080CaseE_externalRefRuleDeploysByReference() = live { caseE(v080) }

    // ========================================================================
    // 0.7 regression
    // ========================================================================

    @Test
    fun testV070CaseA_passkeyTransfer() = live { caseA(v070) }

    @Test
    fun testV070CaseB_ed25519Transfer() = live { caseB(v070) }

    @Test
    fun testV070CaseC_delegatedTransfer() = live { caseC(v070) }

    @Test
    fun testV070CaseD_ruleWithSpendingLimit() = live { caseD(v070) }

    @Test
    fun testV070CaseD_ruleWithV080SpendingLimit() = live { caseD(v070WithV080Policy) }

    // ========================================================================
    // Relayer proxy and indexer
    // ========================================================================

    @Test
    fun testSmoke_relayerDeployAndTransfer_indexerLookups() = live {
        val deployer = fundedAccount()

        // A passkey wallet deployed by createWallet through the relayer.
        val provider = SoftwareP256WebAuthnProvider()
        val passkeyKit = kit(v080, deployer, webauthnProvider = provider, relayerUrl = relayerUrl)
        val passkeyWallet = try {
            val created = passkeyKit.walletOperations.createWallet(userName = "Relayer smoke", autoSubmit = true)
            result("smoke-relayer-deploy", v080, created.contractId, created.transactionHash)
            assertNotNull(created.transactionHash, "the relayer deployment must report a transaction hash")
            created
        } finally {
            passkeyKit.close()
        }

        // An Ed25519 wallet deployed through the relayer and one transfer through it.
        val ed25519Kit = kit(v080, deployer, relayerUrl = relayerUrl)
        val ed25519Wallet = try {
            val publicKey = ed25519Kit.externalSigners.addEd25519FromRawKey(secureRandomBytes(32), ed25519Verifier)
            val walletId = deployWallet(
                deployer, v080, listOf(ExternalSigner(ed25519Verifier, publicKey)), relayer = OZRelayerClient(relayerUrl)
            )
            ed25519Kit.walletOperations.connectToContract(walletId)
            ed25519Kit.transactionOperations.fundWallet(nativeAssetContract())
            val transfer = ed25519Kit.multiSignerManager.multiSignerTransfer(
                tokenContract = nativeAssetContract(),
                recipient = deployer.getAccountId(),
                amount = "1",
                selectedSigners = listOf(SelectedSigner.Ed25519(ed25519Verifier, publicKey))
            )
            requireSuccess("smoke-relayer-transfer", v080, walletId, transfer)
            walletId
        } finally {
            ed25519Kit.close()
        }

        // The indexer returns the Ed25519 wallet under its verifier and the passkey wallet
        // under its credential.
        val indexer = assertNotNull(OZIndexerClient.forNetwork(network.networkPassphrase))
        try {
            awaitIndexed("smoke-indexer-address", ed25519Wallet) {
                indexer.lookupByAddress(ed25519Verifier).contracts.map { it.contractId }
            }
            awaitIndexed("smoke-indexer-credential", passkeyWallet.contractId) {
                indexer.lookupByCredentialId(passkeyWallet.credentialId).contracts.map { it.contractId }
            }
        } finally {
            indexer.close()
        }
    }

    // ========================================================================
    // Cases
    // ========================================================================

    private suspend fun caseA(build: AccountBuild) {
        val deployer = fundedAccount()
        val provider = SoftwareP256WebAuthnProvider()
        val kit = kit(build, deployer, webauthnProvider = provider)
        try {
            val created = kit.walletOperations.createWallet(
                userName = "Case A",
                autoSubmit = true,
                autoFund = true,
                nativeTokenContract = nativeAssetContract()
            )
            val transfer = kit.transactionOperations.transfer(
                tokenContract = nativeAssetContract(),
                recipient = deployer.getAccountId(),
                amount = "1"
            )
            requireSuccess("A", build, created.contractId, transfer)
        } finally {
            kit.close()
        }
    }

    /** Runs case B and returns the wallet with the auth entry its signer signed. */
    private suspend fun caseB(build: AccountBuild): SignedWalletEntry {
        val deployer = fundedAccount()
        val kit = kit(build, deployer)
        try {
            val publicKey = kit.externalSigners.addEd25519FromRawKey(secureRandomBytes(32), ed25519Verifier)
            val walletId = deployWallet(deployer, build, listOf(ExternalSigner(ed25519Verifier, publicKey)))
            kit.walletOperations.connectToContract(walletId)
            kit.transactionOperations.fundWallet(nativeAssetContract())
            val transfer = kit.multiSignerManager.multiSignerTransfer(
                tokenContract = nativeAssetContract(),
                recipient = deployer.getAccountId(),
                amount = "1",
                selectedSigners = listOf(SelectedSigner.Ed25519(ed25519Verifier, publicKey))
            )
            val hash = requireSuccess("B", build, walletId, transfer)
            return SignedWalletEntry(
                walletId = walletId,
                source = deployer,
                entry = walletEntryOf(hash, walletId),
                ed25519PublicKey = publicKey
            )
        } finally {
            kit.close()
        }
    }

    private suspend fun caseC(build: AccountBuild) {
        val deployer = fundedAccount()
        val delegate = fundedAccount()
        val kit = kit(build, deployer, externalWallet = KeyPairWalletAdapter(delegate))
        try {
            val walletId = deployWallet(deployer, build, listOf(DelegatedSigner(delegate.getAccountId())))
            kit.walletOperations.connectToContract(walletId)
            kit.transactionOperations.fundWallet(nativeAssetContract())
            val transfer = kit.multiSignerManager.multiSignerTransfer(
                tokenContract = nativeAssetContract(),
                recipient = deployer.getAccountId(),
                amount = "1",
                selectedSigners = listOf(SelectedSigner.Wallet(delegate.getAccountId()))
            )
            requireSuccess("C", build, walletId, transfer)
        } finally {
            kit.close()
        }
    }

    private suspend fun caseD(build: AccountBuild) {
        val deployer = fundedAccount()
        val kit = kit(build, deployer)
        try {
            val firstKey = kit.externalSigners.addEd25519FromRawKey(secureRandomBytes(32), ed25519Verifier)
            val secondKey = kit.externalSigners.addEd25519FromRawKey(secureRandomBytes(32), ed25519Verifier)
            val walletId = deployWallet(deployer, build, listOf(ExternalSigner(ed25519Verifier, firstKey)))
            kit.walletOperations.connectToContract(walletId)
            kit.transactionOperations.fundWallet(nativeAssetContract())

            val ruleName = "Case D spending"
            val added = kit.contextRuleManager.addContextRule(
                contextType = ContextRuleType.CallContract(nativeAssetContract()),
                name = ruleName,
                signers = listOf(ExternalSigner(ed25519Verifier, secondKey)),
                policies = mapOf(
                    build.spendingLimitPolicy to
                        PolicyInstallParams.SpendingLimit(spendingLimitStroops, spendingPeriodLedgers).toScVal()
                ),
                selectedSigners = listOf(SelectedSigner.Ed25519(ed25519Verifier, firstKey))
            )
            requireSuccess("D-add-rule", build, walletId, added)

            val rule = kit.contextRuleManager.listContextRules().single { it.name == ruleName }
            assertEquals(listOf(build.spendingLimitPolicy), rule.policies, "the rule must carry the spending limit")
            val secondSigner = listOf(SelectedSigner.Ed25519(ed25519Verifier, secondKey))

            val underLimit = kit.multiSignerManager.multiSignerTransfer(
                tokenContract = nativeAssetContract(),
                recipient = deployer.getAccountId(),
                amount = "1",
                selectedSigners = secondSigner,
                resolveContextRuleIds = { _, _ -> listOf(rule.id) }
            )
            requireSuccess("D-under-limit", build, walletId, underLimit)

            val overLimit = runCatching {
                kit.multiSignerManager.multiSignerTransfer(
                    tokenContract = nativeAssetContract(),
                    recipient = deployer.getAccountId(),
                    amount = "20",
                    selectedSigners = secondSigner,
                    resolveContextRuleIds = { _, _ -> listOf(rule.id) }
                )
            }
            val failure = overLimit.exceptionOrNull()?.message
                ?: overLimit.getOrNull()?.takeIf { !it.success }?.error
            result("D-over-limit", build, walletId, error = failure ?: "accepted: ${overLimit.getOrNull()}")
            assertNotNull(failure, "a transfer over the limit must be rejected, got: ${overLimit.getOrNull()}")
            val decoded = assertNotNull(
                ContractErrorCodes.decodeFromMessage(failure),
                "the rejection must name a contract error, got: $failure"
            )
            assertEquals(3221, decoded.code, "the rejection must be SpendingLimitExceeded, got: $failure")
        } finally {
            kit.close()
        }
    }

    private suspend fun caseE(build: AccountBuild) {
        val deployer = fundedAccount()
        val kit = kit(build, deployer)
        try {
            val firstKey = kit.externalSigners.addEd25519FromRawKey(secureRandomBytes(32), ed25519Verifier)
            val secondKey = kit.externalSigners.addEd25519FromRawKey(secureRandomBytes(32), ed25519Verifier)
            val walletId = deployWallet(deployer, build, listOf(ExternalSigner(ed25519Verifier, firstKey)))
            kit.walletOperations.connectToContract(walletId)

            // The owner fixture holds the tag for the hello world contract's code.
            val owner = ContractClient.deploy(
                wasmBytes = TestResourceUtil.readWasmFile("p28_owner_fixture.wasm"),
                source = deployer.getAccountId(),
                signer = deployer,
                network = network,
                rpcUrl = rpcUrl
            )
            val helloWasmId = ContractClient.install(
                wasmBytes = TestResourceUtil.readWasmFile("soroban_hello_world_contract.wasm"),
                source = deployer.getAccountId(),
                signer = deployer,
                network = network,
                rpcUrl = rpcUrl
            )
            val tag = "sa08-live-${Util.bytesToHex(secureRandomBytes(4))}"
            owner.invoke<SCValXdr>(
                functionName = "set_executable",
                parameters = listOf(Scv.toString(tag), SCValXdr.Bytes(SCBytesXdr(Util.hexToBytes(helloWasmId)))),
                source = deployer.getAccountId(),
                signer = deployer
            )

            val ruleName = "Case E reference"
            val added = kit.contextRuleManager.addContextRule(
                contextType = OZBuilders.createCreateContractExternalRefContextType(owner.contractId, tag),
                name = ruleName,
                signers = listOf(ExternalSigner(ed25519Verifier, secondKey)),
                selectedSigners = listOf(SelectedSigner.Ed25519(ed25519Verifier, firstKey))
            )
            requireSuccess("E-add-rule", build, walletId, added)

            val rule = kit.contextRuleManager.listContextRules().single { it.name == ruleName }
            val ruleType = assertIs<ContextRuleType.CreateContractExternalRef>(rule.contextType)
            assertEquals(owner.contractId, ruleType.owner, "the listed rule must name the owner")
            assertEquals(tag, ruleType.tag, "the listed rule must name the tag")

            val salt = secureRandomBytes(32)
            val deployment = InvokeHostFunctionOperation.createContractFromExternalRef(
                executableOwner = Address(owner.contractId),
                tag = tag,
                address = Address(walletId),
                salt = salt
            )
            val deployed = kit.multiSignerManager.submitWithMultipleSigners(
                hostFunction = deployment.hostFunction,
                selectedSigners = listOf(SelectedSigner.Ed25519(ed25519Verifier, secondKey))
            )
            requireSuccess("E-deploy-by-reference", build, walletId, deployed)

            val createdId = Address.deriveContractId(Address(walletId), salt, network)
            val hello = ContractClient.forContract(createdId, rpcUrl, network)
            val answer = hello.funcResToNative(
                "hello",
                hello.invoke<SCValXdr>(
                    functionName = "hello",
                    parameters = listOf(Scv.toString("friend")),
                    source = deployer.getAccountId(),
                    signer = deployer
                )
            )
            assertEquals(listOf("Hello", "friend"), answer, "the contract deployed by reference must answer")
            println("RESULT case=E-hello build=${build.label} contract=$createdId answer=$answer")
        } finally {
            kit.close()
        }
    }

    /**
     * Case F: the wallet's `auth_digest` view, simulated for the preimage of the entry case B
     * signed, returns the digest the SDK computes for that entry, the digest built from the
     * stellar-accounts definition, and the digest the entry's Ed25519 signature verifies over.
     */
    private suspend fun caseF(signed: SignedWalletEntry) {
        val credentials = assertNotNull(signed.entry.credentials.addressCredentials(), "the wallet entry must carry address credentials")
        val payload = SmartAccountAuthPayloadCodec.read(credentials.signature)
        val local = SmartAccountAuth.computeEntryAuthDigest(
            entry = signed.entry,
            expirationLedger = credentials.signatureExpirationLedger.value,
            networkPassphrase = network.networkPassphrase,
            contextRuleIds = payload.contextRuleIds,
            scheme = AuthDigestScheme.PREIMAGE_V1
        )
        val preimage = IndependentAuthDigest.preimage(signed.walletId, local.signaturePayload, payload.contextRuleIds)
        val independent = IndependentAuthDigest.preimageDigest(signed.walletId, local.signaturePayload, payload.contextRuleIds)

        val view = ContractClient.forContractWithoutSpec(signed.walletId, rpcUrl, network).invoke<SCValXdr>(
            functionName = "auth_digest",
            parameters = listOf(preimage),
            source = signed.source.getAccountId(),
            signer = null
        )
        val contractDigest = assertIs<SCValXdr.Bytes>(view, "auth_digest must return bytes").value.value

        val signature = payload.signers.entries.single().value
        val signatureVerifies = KeyPair.fromPublicKey(signed.ed25519PublicKey).verify(contractDigest, signature)

        println(
            "RESULT case=F build=0.8.0 wallet=${signed.walletId} ruleIds=${payload.contextRuleIds} " +
                "signaturePayload=${Util.bytesToHex(local.signaturePayload)} " +
                "contract=${Util.bytesToHex(contractDigest)} local=${Util.bytesToHex(local.authDigest)} " +
                "independent=${Util.bytesToHex(independent)} signatureVerifies=$signatureVerifies"
        )
        assertContentEquals(contractDigest, local.authDigest, "the SDK digest must equal the contract's")
        assertContentEquals(contractDigest, independent, "the definition's digest must equal the contract's")
        assertEquals(
            listOf(preimage.toXdrBase64()),
            local.delegatedAuthArgs.map { it.toXdrBase64() },
            "a Delegated signer authorizes the preimage"
        )
        assertTrue(signatureVerifies, "the signer's signature must verify over the contract's digest")
    }

    // ========================================================================
    // Helpers
    // ========================================================================

    /** A wallet with the auth entry its Ed25519 signer signed in a confirmed transaction. */
    private class SignedWalletEntry(
        val walletId: String,
        val source: KeyPair,
        val entry: SorobanAuthorizationEntryXdr,
        val ed25519PublicKey: ByteArray
    )

    private fun live(block: suspend () -> Unit): TestResult = runTest(timeout = 900.seconds) {
        // Off the test dispatcher, so confirmation polling waits wall-clock time.
        withContext(Dispatchers.Default) { block() }
    }

    private suspend fun fundedAccount(): KeyPair {
        val account = KeyPair.random()
        fundTestAccountAndAwaitVisibility(account.getAccountId(), rpcUrl = rpcUrl)
        return account
    }

    private suspend fun nativeAssetContract(): String = AssetTypeNative.getContractId(network)

    private suspend fun kit(
        build: AccountBuild,
        deployer: KeyPair,
        webauthnProvider: WebAuthnProvider? = null,
        externalWallet: ExternalWalletAdapter? = null,
        relayerUrl: String? = null
    ): OZSmartAccountKit = OZSmartAccountKit.create(
        OZSmartAccountConfig(
            rpcUrl = rpcUrl,
            networkPassphrase = network.networkPassphrase,
            accountWasmHash = build.wasmHash,
            webauthnVerifierAddress = webauthnVerifier,
            deployerKeypair = deployer,
            webauthnProvider = webauthnProvider,
            externalWallet = externalWallet,
            relayerUrl = relayerUrl
        )
    )

    /**
     * Deploys a wallet of [build] whose Default rule holds [signers], from [deployer] with a
     * random salt, directly or through [relayer], and returns its address once it is confirmed.
     */
    private suspend fun deployWallet(
        deployer: KeyPair,
        build: AccountBuild,
        signers: List<SmartAccountSigner>,
        relayer: OZRelayerClient? = null
    ): String {
        val salt = secureRandomBytes(32)
        val operation = InvokeHostFunctionOperation.createContract(
            wasmId = build.wasmHash,
            address = Address(deployer.getAccountId()),
            constructorArgs = listOf(Scv.toVec(signers.map { it.toScVal() }), OZPolicyManager.policiesToScVal(emptyMap())),
            salt = salt
        )
        val walletId = Address.deriveContractId(Address(deployer.getAccountId()), salt, network)
        SorobanServer(rpcUrl).use { server ->
            val transaction = TransactionBuilder(server.getAccount(deployer.getAccountId()), network)
                .setBaseFee(AbstractTransaction.MIN_BASE_FEE)
                .addOperation(operation)
                // The relayer refuses transactions valid for more than 60 seconds.
                .setTimeout(OZConstants.DEFAULT_TIMEOUT_SECONDS.toLong())
                .build()
            val hash = if (relayer == null) {
                val prepared = server.prepareTransaction(transaction)
                prepared.sign(deployer)
                val sent = server.sendTransaction(prepared)
                assertNotNull(sent.hash, "the deployment of $walletId must be accepted, got: $sent")
            } else {
                // The relayer wraps the transaction in a fee bump, so its own fee is the resource fee.
                val simulation = server.simulateTransaction(transaction)
                assertEquals(null, simulation.error, "the deployment of $walletId must simulate")
                val assembled = assembleTransaction(transaction, simulation)
                val relayed = Transaction(
                    sourceAccount = assembled.sourceAccount,
                    fee = assertNotNull(simulation.minResourceFee, "the simulation must name the resource fee"),
                    sequenceNumber = assembled.sequenceNumber,
                    operations = assembled.operations,
                    memo = assembled.memo,
                    preconditions = assembled.preconditions,
                    sorobanData = assembled.sorobanData,
                    network = assembled.network
                )
                relayed.sign(deployer)
                val response = relayer.sendXdr(relayed.toEnvelopeXdr())
                assertTrue(response.success, "the relayer must accept the deployment of $walletId, got: $response")
                assertNotNull(response.hash, "the relayer must report the deployment hash, got: $response")
            }
            val confirmed = server.pollTransaction(hash, maxAttempts = 60)
            assertEquals(
                GetTransactionStatus.SUCCESS, confirmed.status,
                "the deployment $hash of $walletId must succeed, got: ${confirmed.status}"
            )
            println("RESULT case=deploy build=${build.label} wallet=$walletId tx=$hash${if (relayer != null) " via=relayer" else ""}")
        }
        return walletId
    }

    /** The entry of [walletId] in the confirmed transaction [hash], as the network received it. */
    private suspend fun walletEntryOf(hash: String, walletId: String): SorobanAuthorizationEntryXdr =
        SorobanServer(rpcUrl).use { server ->
            val response = server.pollTransaction(hash, maxAttempts = 60)
            val envelope = assertNotNull(response.envelopeXdr, "transaction $hash must carry its envelope")
            val transaction = when (val parsed = AbstractTransaction.fromEnvelopeXdr(envelope, network)) {
                is Transaction -> parsed
                is FeeBumpTransaction -> parsed.innerTransaction
                else -> error("transaction $hash is neither a transaction nor a fee bump: $parsed")
            }
            val operation = assertIs<InvokeHostFunctionOperation>(transaction.operations.single())
            operation.auth.single { entry ->
                val address = entry.credentials.addressCredentials()?.address
                address != null && Address.fromSCAddress(address).toString() == walletId
            }
        }

    /** Polls the indexer until [lookup] lists [contractId], for at most three minutes. */
    private suspend fun awaitIndexed(case: String, contractId: String, lookup: suspend () -> List<String>) {
        var listed: List<String> = emptyList()
        repeat(18) { attempt ->
            listed = lookup()
            if (contractId in listed) {
                println("RESULT case=$case wallet=$contractId listedAfterAttempts=${attempt + 1} count=${listed.size}")
                return
            }
            realDelay(10_000)
        }
        println("RESULT case=$case wallet=$contractId error=not listed after 18 lookups, count=${listed.size}")
        throw AssertionError("the indexer must list $contractId, got ${listed.size} contracts without it")
    }

    private fun requireSuccess(case: String, build: AccountBuild, walletId: String, outcome: TransactionResult): String {
        result(case, build, walletId, outcome.hash, outcome.error.takeIf { !outcome.success })
        assertTrue(outcome.success, "case $case on ${build.label} must succeed, got: ${outcome.error}")
        return assertNotNull(outcome.hash, "case $case on ${build.label} must report a transaction hash")
    }

    private fun result(case: String, build: AccountBuild, walletId: String, hash: String? = null, error: String? = null) {
        println("RESULT case=$case build=${build.label} wallet=$walletId ${if (error != null) "error=$error" else "tx=$hash"}")
    }

    /**
     * A WebAuthn authenticator in software: a P-256 key generated per registration, assertions
     * with the user present and user verified flags over client data that carries the
     * challenge as given.
     */
    private class SoftwareP256WebAuthnProvider : WebAuthnProvider {
        private var credentialId: ByteArray? = null
        private var privateKey: java.security.PrivateKey? = null

        override suspend fun register(challenge: ByteArray, userId: ByteArray, userName: String): WebAuthnRegistrationResult {
            val generator = KeyPairGenerator.getInstance("EC")
            generator.initialize(ECGenParameterSpec("secp256r1"))
            val keyPair = generator.generateKeyPair()
            val point = (keyPair.public as ECPublicKey).w
            val id = secureRandomBytes(16)
            credentialId = id
            privateKey = keyPair.private
            return WebAuthnRegistrationResult(
                credentialId = id,
                publicKey = byteArrayOf(0x04) + point.affineX.toFixed32() + point.affineY.toFixed32(),
                attestationObject = ByteArray(0),
                transports = listOf("internal"),
                deviceType = "singleDevice",
                backedUp = false
            )
        }

        @OptIn(ExperimentalEncodingApi::class)
        override suspend fun authenticate(
            challenge: ByteArray,
            allowCredentials: List<AllowCredential>?
        ): WebAuthnAuthenticationResult {
            val id = checkNotNull(credentialId) { "authenticate called before register" }
            val key = checkNotNull(privateKey) { "authenticate called before register" }
            check(allowCredentials == null || allowCredentials.any { it.id.contentEquals(id) }) {
                "the allowed credentials do not include the registered one"
            }
            val clientDataJson = (
                "{\"type\":\"webauthn.get\",\"challenge\":\"" +
                    Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT).encode(challenge) +
                    "\",\"origin\":\"https://localhost\",\"crossOrigin\":false}"
                ).encodeToByteArray()
            val sha256 = MessageDigest.getInstance("SHA-256")
            // rpIdHash, flags user present and user verified, sign count 1.
            val authenticatorData = sha256.digest("localhost".encodeToByteArray()) + byteArrayOf(0x05, 0, 0, 0, 1)
            val signer = Signature.getInstance("SHA256withECDSA")
            signer.initSign(key)
            signer.update(authenticatorData + sha256.digest(clientDataJson))
            return WebAuthnAuthenticationResult(
                credentialId = id,
                authenticatorData = authenticatorData,
                clientDataJSON = clientDataJson,
                signature = signer.sign()
            )
        }

        private fun JavaBigInteger.toFixed32(): ByteArray {
            val bytes = toByteArray().dropWhile { it == 0.toByte() }.toByteArray()
            require(bytes.size <= 32) { "a P-256 coordinate has at most 32 bytes, got ${bytes.size}" }
            return ByteArray(32 - bytes.size) + bytes
        }
    }

    /** An external wallet that signs auth entries with one account key held in memory. */
    private class KeyPairWalletAdapter(private val keyPair: KeyPair) : ExternalWalletAdapter {
        private val wallet = ConnectedWallet(address = keyPair.getAccountId(), walletId = "keypair", walletName = "Key pair")

        override suspend fun connect(): ConnectedWallet = wallet

        override suspend fun disconnect() = Unit

        @OptIn(ExperimentalEncodingApi::class)
        override suspend fun signAuthEntry(preimageXdr: String, options: SignAuthEntryOptions?): SignAuthEntryResult {
            check(options?.address == null || options.address == keyPair.getAccountId()) {
                "asked to sign for ${options?.address}, holding ${keyPair.getAccountId()}"
            }
            val hash = MessageDigest.getInstance("SHA-256").digest(Base64.decode(preimageXdr))
            return SignAuthEntryResult(
                signedAuthEntry = Base64.encode(keyPair.sign(hash)),
                signerAddress = keyPair.getAccountId()
            )
        }

        override fun getConnectedWallets(): List<ConnectedWallet> = listOf(wallet)

        override fun canSignFor(address: String): Boolean = address == keyPair.getAccountId()
    }
}
