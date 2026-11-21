//
//  DelegatedSignerGuardTest.kt
//  Stellar SDK Kotlin Multiplatform
//
//  Copyright (c) 2026 Soneso. All rights reserved.
//

package com.soneso.stellar.sdk.unitTests.smartaccount.oz

import com.soneso.stellar.sdk.Address
import com.soneso.stellar.sdk.KeyPair
import com.soneso.stellar.sdk.scval.Scv
import com.soneso.stellar.sdk.smartaccount.core.DelegatedSigner
import com.soneso.stellar.sdk.smartaccount.core.ExternalSigner
import com.soneso.stellar.sdk.smartaccount.core.SmartAccountSigner
import com.soneso.stellar.sdk.smartaccount.core.TransactionException
import com.soneso.stellar.sdk.smartaccount.core.ValidationException
import com.soneso.stellar.sdk.smartaccount.oz.ContextRuleType
import com.soneso.stellar.sdk.smartaccount.oz.OZSmartAccountConfig
import com.soneso.stellar.sdk.smartaccount.oz.OZSmartAccountKit
import com.soneso.stellar.sdk.smartaccount.oz.requireValidAddedSigners
import com.soneso.stellar.sdk.unitTests.smartaccount.SIMULATION_REACHED_SENTINEL
import com.soneso.stellar.sdk.unitTests.smartaccount.buildAccountEntryXdr
import com.soneso.stellar.sdk.unitTests.smartaccount.buildAccountThenSimulationErrorMockServer
import com.soneso.stellar.sdk.unitTests.smartaccount.buildConfig
import com.soneso.stellar.sdk.unitTests.smartaccount.buildNoRpcMockServer
import com.soneso.stellar.sdk.xdr.ClaimableBalanceIDXdr
import com.soneso.stellar.sdk.xdr.ContractExecutableXdr
import com.soneso.stellar.sdk.xdr.ContractIDPreimageFromAddressXdr
import com.soneso.stellar.sdk.xdr.ContractIDPreimageXdr
import com.soneso.stellar.sdk.xdr.ContractIDXdr
import com.soneso.stellar.sdk.xdr.CreateContractArgsV2Xdr
import com.soneso.stellar.sdk.xdr.HashXdr
import com.soneso.stellar.sdk.xdr.Int64Xdr
import com.soneso.stellar.sdk.xdr.InvokeContractArgsXdr
import com.soneso.stellar.sdk.xdr.MuxedContractXdr
import com.soneso.stellar.sdk.xdr.PoolIDXdr
import com.soneso.stellar.sdk.xdr.SCAddressXdr
import com.soneso.stellar.sdk.xdr.SCMapEntryXdr
import com.soneso.stellar.sdk.xdr.SCMapXdr
import com.soneso.stellar.sdk.xdr.SCSymbolXdr
import com.soneso.stellar.sdk.xdr.SCValXdr
import com.soneso.stellar.sdk.xdr.SorobanAddressCredentialsXdr
import com.soneso.stellar.sdk.xdr.SorobanAuthorizationEntryXdr
import com.soneso.stellar.sdk.xdr.SorobanAuthorizedFunctionXdr
import com.soneso.stellar.sdk.xdr.SorobanAuthorizedInvocationXdr
import com.soneso.stellar.sdk.xdr.SorobanCredentialsXdr
import com.soneso.stellar.sdk.xdr.Uint256Xdr
import com.soneso.stellar.sdk.xdr.Uint32Xdr
import com.soneso.stellar.sdk.xdr.Uint64Xdr
import com.soneso.stellar.sdk.xdr.toXdrBase64
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * The delegated signer guard (GHSA-jpg9-m7w4-c7wc): a delegated signer the SDK adds or signs an
 * addition of must be a Stellar account other than the shared default deployer, and never the
 * wallet itself. Signers that are already on chain are read without a check.
 */
class DelegatedSignerGuardTest {

    private val wallet = "CDLZFC3SYJYDZT7K67VZ75HPJVIEUVNIXF47ZG2FB2RMQQVU2HHGCYSC"
    private val otherContract = "CAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAD2KM"
    private val verifier = "CB26VN37RCVNTHJZDEPK6IRO2MMTS3Z2IEO5JD5BINY2OOJ5KKJG7NKY"
    private val account = "GA7QYNF7SOWQ3GLR2BGMZEHXAVIRZA4KVWLTJJFC7MGXUA74P7UJVSGZ"

    private suspend fun defaultDeployer(): String = OZSmartAccountConfig.createDefaultDeployer().getAccountId()

    private fun delegated(address: String): SCValXdr =
        Scv.toVec(listOf(Scv.toSymbol("Delegated"), Scv.toAddress(Address(address).toSCAddress())))

    private fun external(keyData: ByteArray, verifierAddress: String = verifier): SCValXdr = Scv.toVec(
        listOf(Scv.toSymbol("External"), Scv.toAddress(Address(verifierAddress).toSCAddress()), Scv.toBytes(keyData))
    )

    /** The SEP-23 muxed account of [account] with id 0. */
    private val muxedAccount = "MA7QYNF7SOWQ3GLR2BGMZEHXAVIRZA4KVWLTJJFC7MGXUA74P7UJVAAAAAAAAAAAAAJLK"

    private val passkeyKey = ByteArray(65) { if (it == 0) 0x04 else it.toByte() }

    private fun call(target: String, functionName: String, args: List<SCValXdr>) =
        SorobanAuthorizedFunctionXdr.ContractFn(InvokeContractArgsXdr(Address(target).toSCAddress(), SCSymbolXdr(functionName), args))

    private fun entry(
        function: SorobanAuthorizedFunctionXdr,
        subInvocations: List<SorobanAuthorizedInvocationXdr> = emptyList()
    ) = SorobanAuthorizationEntryXdr(
        credentials = SorobanCredentialsXdr.Address(
            SorobanAddressCredentialsXdr(Address(wallet).toSCAddress(), Int64Xdr(1L), Uint32Xdr(0u), Scv.toVoid())
        ),
        rootInvocation = SorobanAuthorizedInvocationXdr(function, subInvocations)
    )

    private fun addSigner(signer: SCValXdr, target: String = wallet) =
        call(target, "add_signer", listOf(Scv.toUint32(0u), signer))

    private fun batchAddSigner(signers: List<SCValXdr>) =
        call(wallet, "batch_add_signer", listOf(Scv.toUint32(0u), Scv.toVec(signers)))

    private fun addContextRule(signers: SCValXdr) = call(
        wallet,
        "add_context_rule",
        listOf(ContextRuleType.Default.toScVal(), Scv.toString("rule"), Scv.toVoid(), signers, SCValXdr.Map(SCMapXdr(emptyList<SCMapEntryXdr>())))
    )

    // MARK: - requireValidAddedSigners

    @Test
    fun rawAddSigner_contractDelegate_isRefusedNamingIt() = runTest {
        val ex = assertFailsWith<ValidationException.InvalidAddress> {
            requireValidAddedSigners(entry(addSigner(delegated(otherContract))), wallet)
        }
        assertTrue(ex.message.contains(otherContract), ex.message)
        assertTrue(ex.message.contains("G-address"), ex.message)
    }

    @Test
    fun rawAddSigner_walletAsItsOwnDelegate_isRefused() = runTest {
        val ex = assertFailsWith<ValidationException.InvalidInput> {
            requireValidAddedSigners(entry(addSigner(delegated(wallet))), wallet)
        }
        assertTrue(ex.message.contains("cannot be its own delegated signer"), ex.message)
        assertTrue(ex.message.contains(wallet), ex.message)
    }

    @Test
    fun rawAddSigner_defaultDeployer_isRefused() = runTest {
        val deployer = defaultDeployer()
        val ex = assertFailsWith<ValidationException.InvalidInput> {
            requireValidAddedSigners(entry(addSigner(delegated(deployer))), wallet)
        }
        assertTrue(ex.message.contains(deployer), ex.message)
        assertTrue(ex.message.contains("default deployer"), ex.message)
    }

    @Test
    fun rawAddSigner_accountDelegateAndExternalSigner_pass() = runTest {
        requireValidAddedSigners(entry(addSigner(delegated(account))), wallet)
        requireValidAddedSigners(entry(addSigner(external(passkeyKey))), wallet)
    }

    @Test
    fun rawAddSigner_externalSignerWithOversizedKey_isRefused() = runTest {
        val ex = assertFailsWith<ValidationException.InvalidInput> {
            requireValidAddedSigners(entry(addSigner(external(ByteArray(257)))), wallet)
        }
        assertTrue(ex.message.contains("got: 257"), ex.message)
    }

    @Test
    fun rawBatchAddSigner_oneContractDelegateAmongAccounts_isRefused() = runTest {
        val ex = assertFailsWith<ValidationException.InvalidAddress> {
            requireValidAddedSigners(entry(batchAddSigner(listOf(delegated(account), delegated(otherContract)))), wallet)
        }
        assertTrue(ex.message.contains(otherContract), ex.message)
        requireValidAddedSigners(entry(batchAddSigner(listOf(delegated(account), external(passkeyKey)))), wallet)
    }

    @Test
    fun rawAddContextRule_signersArgument_isChecked() = runTest {
        assertFailsWith<ValidationException.InvalidAddress> {
            requireValidAddedSigners(entry(addContextRule(Scv.toVec(listOf(delegated(otherContract))))), wallet)
        }
        requireValidAddedSigners(entry(addContextRule(Scv.toVec(listOf(delegated(account))))), wallet)
    }

    @Test
    fun rawCallWhoseFunctionNameIsNotUtf8_addsNoSigner() = runTest {
        val name = SCSymbolXdr(byteArrayOf(0xff.toByte(), 0xfe.toByte(), 0x20, 0x61, 0x62, 0x63))
        val function = SorobanAuthorizedFunctionXdr.ContractFn(
            InvokeContractArgsXdr(Address(wallet).toSCAddress(), name, listOf(Scv.toUint32(0u), delegated(otherContract)))
        )
        requireValidAddedSigners(entry(function), wallet)
    }

    // The raw path applies the contract's and the kit's rules to the signer fields, not the
    // checks of the typed signers.

    @Test
    fun rawExternalSignerWithEmptyKeyData_passesInEverySignerAddingCall() = runTest {
        val empty = external(ByteArray(0))
        requireValidAddedSigners(entry(addSigner(empty)), wallet)
        requireValidAddedSigners(entry(batchAddSigner(listOf(empty))), wallet)
        requireValidAddedSigners(entry(addContextRule(Scv.toVec(listOf(empty)))), wallet)
    }

    @Test
    fun rawExternalSignerWithAnAccountVerifier_passes() = runTest {
        requireValidAddedSigners(entry(addSigner(external(passkeyKey, verifierAddress = account))), wallet)
    }

    @Test
    fun rawDelegatedMuxedAccount_isRefusedByTheAccountRule() = runTest {
        val ex = assertFailsWith<ValidationException.InvalidAddress> {
            requireValidAddedSigners(entry(addSigner(delegated(muxedAccount))), wallet)
        }
        assertTrue(
            ex.message.contains("A delegated signer must be a Stellar account (G-address), got: $muxedAccount"),
            ex.message
        )
    }

    // Signer addresses by type: the kit refuses a muxed contract (W...) and converts every other type.

    private val muxedContract: SCAddressXdr = SCAddressXdr.MuxedContract(
        MuxedContractXdr(Uint64Xdr(42uL), ContractIDXdr(HashXdr(ByteArray(32) { 7 })))
    )

    private fun delegatedAt(address: SCAddressXdr): SCValXdr = Scv.toVec(listOf(Scv.toSymbol("Delegated"), Scv.toAddress(address)))

    private fun externalAt(verifier: SCAddressXdr): SCValXdr =
        Scv.toVec(listOf(Scv.toSymbol("External"), Scv.toAddress(verifier), Scv.toBytes(passkeyKey)))

    @Test
    fun rawDelegatedMuxedContract_isRefusedAsAnUnsupportedAddressType() = runTest {
        val address = Address.fromSCAddress(muxedContract).toString()
        val ex = assertFailsWith<ValidationException.InvalidInput> {
            requireValidAddedSigners(entry(addSigner(delegatedAt(muxedContract))), wallet)
        }
        assertTrue(
            ex.message.contains("Delegated signer address has an unsupported address type: SC_ADDRESS_TYPE_MUXED_CONTRACT, got: $address"),
            ex.message
        )
    }

    @Test
    fun rawExternalVerifierMuxedContract_isRefusedAsAnUnsupportedAddressType() = runTest {
        val address = Address.fromSCAddress(muxedContract).toString()
        for (call in listOf(
            addSigner(externalAt(muxedContract)),
            batchAddSigner(listOf(externalAt(muxedContract))),
            addContextRule(Scv.toVec(listOf(externalAt(muxedContract))))
        )) {
            val ex = assertFailsWith<ValidationException.InvalidInput> { requireValidAddedSigners(entry(call), wallet) }
            assertTrue(
                ex.message.contains("External signer verifier address has an unsupported address type: SC_ADDRESS_TYPE_MUXED_CONTRACT, got: $address"),
                ex.message
            )
        }
    }

    @Test
    fun rawSignerAddressTypesTheKitConverts_pass() = runTest {
        val convertible = listOf(
            Address(account).toSCAddress(),
            Address(verifier).toSCAddress(),
            Address(muxedAccount).toSCAddress(),
            SCAddressXdr.ClaimableBalanceId(ClaimableBalanceIDXdr.V0(HashXdr(ByteArray(32) { 3 }))),
            SCAddressXdr.LiquidityPoolId(PoolIDXdr(HashXdr(ByteArray(32) { 5 })))
        )
        for (verifierAddress in convertible) {
            requireValidAddedSigners(entry(addSigner(externalAt(verifierAddress))), wallet)
        }
        requireValidAddedSigners(entry(addSigner(delegatedAt(Address(account).toSCAddress()))), wallet)
    }

    @Test
    fun rawAddSigner_nestedInASubInvocation_isRefused() = runTest {
        val root = call(otherContract, "execute", emptyList())
        val nested = SorobanAuthorizedInvocationXdr(
            call(otherContract, "route", emptyList()),
            listOf(SorobanAuthorizedInvocationXdr(addSigner(delegated(otherContract)), emptyList()))
        )
        assertFailsWith<ValidationException.InvalidAddress> {
            requireValidAddedSigners(entry(root, listOf(nested)), wallet)
        }
    }

    @Test
    fun rawAddSigner_underACreateContractInvocation_isRefused() = runTest {
        val deploy = SorobanAuthorizedFunctionXdr.CreateContractV2HostFn(
            CreateContractArgsV2Xdr(
                ContractIDPreimageXdr.FromAddress(ContractIDPreimageFromAddressXdr(Address(wallet).toSCAddress(), Uint256Xdr(ByteArray(32)))),
                ContractExecutableXdr.WasmHash(HashXdr(ByteArray(32) { 1 })),
                emptyList()
            )
        )
        val nested = SorobanAuthorizedInvocationXdr(addSigner(delegated(otherContract)), emptyList())
        requireValidAddedSigners(entry(deploy), wallet)
        assertFailsWith<ValidationException.InvalidAddress> {
            requireValidAddedSigners(entry(deploy, listOf(nested)), wallet)
        }
    }

    @Test
    fun rawAddSigner_onAnotherContract_isIgnored() = runTest {
        requireValidAddedSigners(entry(addSigner(delegated(otherContract), target = otherContract)), wallet)
    }

    @Test
    fun otherWalletFunctions_areIgnored() = runTest {
        requireValidAddedSigners(entry(call(wallet, "remove_signer", listOf(Scv.toUint32(0u), delegated(otherContract)))), wallet)
    }

    @Test
    fun rawListArgumentThatIsNoVec_isRefusedNamingTheCall() = runTest {
        val batch = call(wallet, "batch_add_signer", listOf(Scv.toUint32(0u), Scv.toUint32(5u)))
        val ex = assertFailsWith<ValidationException.InvalidInput> {
            requireValidAddedSigners(entry(batch), wallet)
        }
        assertTrue(ex.message.contains("batch_add_signer"), ex.message)
        assertTrue(ex.message.contains("argument 1"), ex.message)

        assertFailsWith<ValidationException.InvalidInput> {
            requireValidAddedSigners(entry(addContextRule(Scv.toString("signers"))), wallet)
        }
    }

    @Test
    fun rawCallWithoutTheSignerArgument_isLeftToTheContract() = runTest {
        requireValidAddedSigners(entry(call(wallet, "add_signer", listOf(Scv.toUint32(0u)))), wallet)
        requireValidAddedSigners(entry(call(wallet, "add_context_rule", listOf(ContextRuleType.Default.toScVal()))), wallet)
    }

    @Test
    fun rawVecWithoutValue_countsAsEmpty() = runTest {
        requireValidAddedSigners(entry(call(wallet, "batch_add_signer", listOf(Scv.toUint32(0u), SCValXdr.Vec(null)))), wallet)
    }

    @Test
    fun rawMalformedSigner_isRefused() = runTest {
        assertFailsWith<TransactionException.SigningFailed> {
            requireValidAddedSigners(entry(addSigner(Scv.toVec(listOf(Scv.toSymbol("Delegated"))))), wallet)
        }
        assertFailsWith<TransactionException.SigningFailed> {
            requireValidAddedSigners(entry(addSigner(Scv.toUint32(1u))), wallet)
        }
    }

    // MARK: - Manager entry points

    private suspend fun kitReachingSimulation(): OZSmartAccountKit {
        val deployer = KeyPair.random()
        val kit = OZSmartAccountKit.createWithServer(
            buildConfig().copy(deployerKeypair = deployer),
            buildAccountThenSimulationErrorMockServer(buildAccountEntryXdr(deployer).toXdrBase64())
        )
        kit.setConnectedState("test-credential-id", wallet)
        return kit
    }

    private suspend fun kitWithoutRpc(): OZSmartAccountKit {
        val kit = OZSmartAccountKit.createWithServer(buildConfig(), buildNoRpcMockServer())
        kit.setConnectedState("test-credential-id", wallet)
        return kit
    }

    @Test
    fun addDelegated_contractWalletOrDefaultDeployer_isRefusedBeforeAnyRequest() = runTest {
        val kit = kitWithoutRpc()
        assertFailsWith<ValidationException.InvalidAddress> { kit.signerManager.addDelegated(0u, otherContract) }
        assertFailsWith<ValidationException.InvalidInput> { kit.signerManager.addDelegated(0u, wallet) }
        val deployerEx = assertFailsWith<ValidationException.InvalidInput> { kit.signerManager.addDelegated(0u, defaultDeployer()) }
        assertTrue(deployerEx.message.contains(defaultDeployer()), deployerEx.message)
    }

    @Test
    fun addDelegated_accountAddress_reachesSimulation() = runTest {
        val kit = kitReachingSimulation()
        val ex = assertFailsWith<TransactionException.SimulationFailed> { kit.signerManager.addDelegated(0u, account) }
        assertTrue(ex.message.contains(SIMULATION_REACHED_SENTINEL), ex.message)
    }

    @Test
    fun addContextRule_refusedDelegates_areRefusedBeforeAnyRequest() = runTest {
        val kit = kitWithoutRpc()
        val passkey = ExternalSigner(verifier, passkeyKey)
        for (refused in listOf<SmartAccountSigner>(DelegatedSigner(otherContract), DelegatedSigner(wallet), DelegatedSigner(defaultDeployer()))) {
            assertFailsWith<ValidationException> {
                kit.contextRuleManager.addContextRule(ContextRuleType.Default, "rule", signers = listOf(passkey, refused))
            }
        }
    }

    @Test
    fun addContextRule_accountDelegate_reachesSimulation() = runTest {
        val kit = kitReachingSimulation()
        val ex = assertFailsWith<TransactionException.SimulationFailed> {
            kit.contextRuleManager.addContextRule(ContextRuleType.Default, "rule", signers = listOf(DelegatedSigner(account)))
        }
        assertTrue(ex.message.contains(SIMULATION_REACHED_SENTINEL), ex.message)
    }

    // MARK: - Reads stay open

    @Test
    fun listedRuleWithAContractDelegate_parses() = runTest {
        val kit = kitWithoutRpc()
        val fields = listOf(
            "context_type" to ContextRuleType.Default.toScVal(),
            "id" to Scv.toUint32(0u),
            "name" to Scv.toString("legacy"),
            "policies" to Scv.toVec(emptyList()),
            "policy_ids" to Scv.toVec(emptyList()),
            "signer_ids" to Scv.toVec(listOf(Scv.toUint32(0u))),
            "signers" to Scv.toVec(listOf(delegated(otherContract))),
            "valid_until" to Scv.toVoid()
        )
        val parsed = kit.contextRuleManager.parseContextRule(
            SCValXdr.Map(SCMapXdr(fields.map { (key, value) -> SCMapEntryXdr(Scv.toSymbol(key), value) }))
        )
        assertEquals(listOf<SmartAccountSigner>(DelegatedSigner(otherContract)), parsed.signers)
    }

    @Test
    fun delegatedSignerType_holdsAContractAddress() {
        assertEquals(otherContract, DelegatedSigner(otherContract).address)
    }
}
