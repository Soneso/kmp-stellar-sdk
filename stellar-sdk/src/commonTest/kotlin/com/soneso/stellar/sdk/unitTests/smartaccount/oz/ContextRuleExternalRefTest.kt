//
//  ContextRuleExternalRefTest.kt
//  Stellar SDK Kotlin Multiplatform
//
//  Copyright (c) 2026 Soneso. All rights reserved.
//

package com.soneso.stellar.sdk.unitTests.smartaccount.oz

import com.soneso.stellar.sdk.Address
import com.soneso.stellar.sdk.scval.Scv
import com.soneso.stellar.sdk.smartaccount.core.ValidationException
import com.soneso.stellar.sdk.smartaccount.oz.ContextRuleType
import com.soneso.stellar.sdk.smartaccount.oz.OZBuilders
import com.soneso.stellar.sdk.smartaccount.oz.OZSmartAccountKit
import com.soneso.stellar.sdk.smartaccount.oz.ParsedContextRule
import com.soneso.stellar.sdk.unitTests.smartaccount.buildConfig
import com.soneso.stellar.sdk.xdr.ContractExecutableExternalRefXdr
import com.soneso.stellar.sdk.xdr.ContractExecutableXdr
import com.soneso.stellar.sdk.xdr.ContractIDXdr
import com.soneso.stellar.sdk.xdr.ContractIDPreimageFromAddressXdr
import com.soneso.stellar.sdk.xdr.ContractIDPreimageXdr
import com.soneso.stellar.sdk.xdr.CreateContractArgsV2Xdr
import com.soneso.stellar.sdk.xdr.CreateContractArgsXdr
import com.soneso.stellar.sdk.xdr.HashXdr
import com.soneso.stellar.sdk.xdr.InvokeContractArgsXdr
import com.soneso.stellar.sdk.xdr.SCAddressXdr
import com.soneso.stellar.sdk.xdr.SCMapEntryXdr
import com.soneso.stellar.sdk.xdr.SCMapXdr
import com.soneso.stellar.sdk.xdr.SCSymbolXdr
import com.soneso.stellar.sdk.xdr.SCValXdr
import com.soneso.stellar.sdk.xdr.SorobanAuthorizationEntryXdr
import com.soneso.stellar.sdk.xdr.SorobanAuthorizedFunctionXdr
import com.soneso.stellar.sdk.xdr.SorobanAuthorizedInvocationXdr
import com.soneso.stellar.sdk.xdr.SorobanCredentialsXdr
import com.soneso.stellar.sdk.xdr.Uint256Xdr
import com.soneso.stellar.sdk.xdr.toXdrBase64
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * The `CreateContractExternalRef` context rule kind of stellar-accounts 0.8: its on-chain
 * encoding, parsing, the builder, the extraction from both create-contract invocation arms and
 * rule matching.
 */
class ContextRuleExternalRefTest {

    private val owner = "CDLZFC3SYJYDZT7K67VZ75HPJVIEUVNIXF47ZG2FB2RMQQVU2HHGCYSC"
    private val otherOwner = "CAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAD2KM"
    private val wallet = "CB26VN37RCVNTHJZDEPK6IRO2MMTS3Z2IEO5JD5BINY2OOJ5KKJG7NKY"
    private val account = "GA7QYNF7SOWQ3GLR2BGMZEHXAVIRZA4KVWLTJJFC7MGXUA74P7UJVSGZ"

    private suspend fun manager() = OZSmartAccountKit.create(buildConfig()).also {
        it.setConnectedState("test-credential-id", wallet)
    }.contextRuleManager

    private fun ruleScVal(id: UInt, contextType: SCValXdr): SCValXdr {
        val fields = listOf(
            "context_type" to contextType,
            "id" to Scv.toUint32(id),
            "name" to Scv.toString("rule $id"),
            "policies" to Scv.toVec(emptyList()),
            "policy_ids" to Scv.toVec(emptyList()),
            "signer_ids" to Scv.toVec(emptyList()),
            "signers" to Scv.toVec(emptyList()),
            "valid_until" to Scv.toVoid()
        )
        return SCValXdr.Map(SCMapXdr(fields.map { (key, value) -> SCMapEntryXdr(Scv.toSymbol(key), value) }))
    }

    private fun externalRefScVal(ownerAddress: String, tag: String): SCValXdr = Scv.toVec(
        listOf(Scv.toSymbol("CreateContractExternalRef"), Scv.toAddress(Address(ownerAddress).toSCAddress()), Scv.toString(tag))
    )

    private fun externalRefExecutable(ownerAddress: String, tag: String) = ContractExecutableXdr.ExternalRef(
        ContractExecutableExternalRefXdr(Address(ownerAddress).toSCAddress(), tag.encodeToByteArray())
    )

    private val contractIdPreimage = ContractIDPreimageXdr.FromAddress(
        ContractIDPreimageFromAddressXdr(Address(wallet).toSCAddress(), Uint256Xdr(ByteArray(32)))
    )

    private fun entry(function: SorobanAuthorizedFunctionXdr, sub: List<SorobanAuthorizedInvocationXdr> = emptyList()) =
        SorobanAuthorizationEntryXdr(SorobanCredentialsXdr.Void, SorobanAuthorizedInvocationXdr(function, sub))

    private fun createV1(executable: ContractExecutableXdr) =
        SorobanAuthorizedFunctionXdr.CreateContractHostFn(CreateContractArgsXdr(contractIdPreimage, executable))

    private fun createV2(executable: ContractExecutableXdr) = SorobanAuthorizedFunctionXdr.CreateContractV2HostFn(
        CreateContractArgsV2Xdr(contractIdPreimage, executable, listOf(Scv.toUint32(1u)))
    )

    private fun parsedRule(id: UInt, type: ContextRuleType) =
        ParsedContextRule(id, type, "rule $id", emptyList(), emptyList(), emptyList(), emptyList(), null)

    // MARK: - Encoding and parsing

    @Test
    fun toScVal_encodesSymbolOwnerAndTag() {
        assertEquals(
            externalRefScVal(owner, "account-v1").toXdrBase64(),
            ContextRuleType.CreateContractExternalRef(owner, "account-v1").toScVal().toXdrBase64()
        )
    }

    @Test
    fun toScVal_invalidOwner_throwsInvalidAddressNamingIt() {
        val ex = assertFailsWith<ValidationException.InvalidAddress> {
            ContextRuleType.CreateContractExternalRef("not-an-address", "tag").toScVal()
        }
        assertTrue(ex.message.contains("not-an-address"), ex.message)
    }

    @Test
    fun parseContextRule_externalRef_roundTrips() = runTest {
        val type = ContextRuleType.CreateContractExternalRef(owner, "account-v1")
        val parsed = manager().parseContextRule(ruleScVal(3u, type.toScVal()))
        assertEquals(3u, parsed.id)
        assertEquals(type, parsed.contextType)
    }

    @Test
    fun parseContextRule_externalRefWithoutTag_throwsNamingTheElementCount() = runTest {
        val truncated = Scv.toVec(listOf(Scv.toSymbol("CreateContractExternalRef"), Scv.toAddress(Address(owner).toSCAddress())))
        val ex = assertFailsWith<ValidationException.InvalidInput> {
            manager().parseContextRule(ruleScVal(1u, truncated))
        }
        assertTrue(ex.message.contains("got 1 element(s)"), ex.message)
    }

    @Test
    fun parseContextRule_externalRefOwnerNotAnAddress_throws() = runTest {
        val bad = Scv.toVec(listOf(Scv.toSymbol("CreateContractExternalRef"), Scv.toString(owner), Scv.toString("tag")))
        val ex = assertFailsWith<ValidationException.InvalidInput> { manager().parseContextRule(ruleScVal(1u, bad)) }
        assertTrue(ex.message.contains("CreateContractExternalRef owner"), ex.message)
    }

    @Test
    fun parseContextRule_externalRefTagNotAString_throws() = runTest {
        val bad = Scv.toVec(
            listOf(Scv.toSymbol("CreateContractExternalRef"), Scv.toAddress(Address(owner).toSCAddress()), Scv.toUint32(1u))
        )
        val ex = assertFailsWith<ValidationException.InvalidInput> { manager().parseContextRule(ruleScVal(1u, bad)) }
        assertTrue(ex.message.contains("CreateContractExternalRef tag"), ex.message)
    }

    @Test
    fun parseContextRule_unknownDiscriminant_stillThrows() = runTest {
        val unknown = Scv.toVec(listOf(Scv.toSymbol("CreateContractFromRegistry"), Scv.toString("x")))
        val ex = assertFailsWith<ValidationException.InvalidInput> { manager().parseContextRule(ruleScVal(1u, unknown)) }
        assertTrue(ex.message.contains("CreateContractFromRegistry"), ex.message)
    }

    // MARK: - Builder

    @Test
    fun builder_contractOwner_buildsTheRuleType() {
        assertEquals(
            ContextRuleType.CreateContractExternalRef(owner, "account-v1"),
            OZBuilders.createCreateContractExternalRefContextType(owner, "account-v1")
        )
    }

    @Test
    fun builder_accountOwner_throwsInvalidAddress() {
        val ex = assertFailsWith<ValidationException.InvalidAddress> {
            OZBuilders.createCreateContractExternalRefContextType(account, "account-v1")
        }
        assertTrue(ex.message.contains(account), ex.message)
    }

    // MARK: - Extraction and matching

    @Test
    fun resolve_createContractV1WithExternalRef_matchesTheEqualRule() = runTest {
        val rules = listOf(
            parsedRule(1u, ContextRuleType.CreateContractExternalRef(otherOwner, "account-v1")),
            parsedRule(2u, ContextRuleType.CreateContractExternalRef(owner, "account-v2")),
            parsedRule(3u, ContextRuleType.CreateContractExternalRef(owner, "account-v1"))
        )
        val ids = manager().resolveContextRuleIdsForEntry(entry(createV1(externalRefExecutable(owner, "account-v1"))), emptyList(), rules)
        assertEquals(listOf(3u), ids)
    }

    @Test
    fun resolve_createContractV2WithExternalRef_matchesTheEqualRule() = runTest {
        val rules = listOf(
            parsedRule(4u, ContextRuleType.CreateContractExternalRef(owner, "account-v1")),
            parsedRule(5u, ContextRuleType.CallContract(owner))
        )
        val ids = manager().resolveContextRuleIdsForEntry(entry(createV2(externalRefExecutable(owner, "account-v1"))), emptyList(), rules)
        assertEquals(listOf(4u), ids)
    }

    @Test
    fun resolve_externalRefInASubInvocation_isExtracted() = runTest {
        val call = SorobanAuthorizedFunctionXdr.ContractFn(InvokeContractArgsXdr(Address(owner).toSCAddress(), SCSymbolXdr("deploy"), emptyList()))
        val rules = listOf(
            parsedRule(6u, ContextRuleType.CallContract(owner)),
            parsedRule(7u, ContextRuleType.CreateContractExternalRef(owner, "account-v1"))
        )
        val ids = manager().resolveContextRuleIdsForEntry(
            entry(call, listOf(SorobanAuthorizedInvocationXdr(createV2(externalRefExecutable(owner, "account-v1")), emptyList()))),
            emptyList(),
            rules
        )
        assertEquals(listOf(6u, 7u), ids)
    }

    @Test
    fun resolve_externalRefOwnerThatIsNoAddress_throwsNamingTheExecutable() = runTest {
        val malformedOwner = SCAddressXdr.ContractId(ContractIDXdr(HashXdr(ByteArray(31))))
        val executable = ContractExecutableXdr.ExternalRef(
            ContractExecutableExternalRefXdr(malformedOwner, "account-v1".encodeToByteArray())
        )
        val ex = assertFailsWith<ValidationException.InvalidInput> {
            manager().resolveContextRuleIdsForEntry(entry(createV1(executable)), emptyList(), listOf(parsedRule(1u, ContextRuleType.Default)))
        }
        assertTrue(ex.message.contains("Invalid input for executable: Failed to parse the executable reference owner"), ex.message)
        assertTrue(ex.message.contains("got 31"), "Message names the owner's length: ${ex.message}")
    }

    @Test
    fun resolve_otherOwnerOrTagOnly_matchesNoRule() = runTest {
        val rules = listOf(
            parsedRule(1u, ContextRuleType.CreateContractExternalRef(otherOwner, "account-v1")),
            parsedRule(2u, ContextRuleType.CreateContractExternalRef(owner, "account-v2")),
            parsedRule(3u, ContextRuleType.CreateContract(ByteArray(32)))
        )
        val ex = assertFailsWith<ValidationException.InvalidInput> {
            manager().resolveContextRuleIdsForEntry(entry(createV1(externalRefExecutable(owner, "account-v1"))), emptyList(), rules)
        }
        assertTrue(
            ex.message.contains("No context rule matches CreateContractExternalRef(owner=$owner, tag=account-v1)"),
            ex.message
        )
    }

    @Test
    fun resolve_externalRefWithOnlyADefaultRule_usesTheDefault() = runTest {
        val rules = listOf(parsedRule(9u, ContextRuleType.Default))
        val ids = manager().resolveContextRuleIdsForEntry(entry(createV1(externalRefExecutable(owner, "account-v1"))), emptyList(), rules)
        assertEquals(listOf(9u), ids)
    }

    @Test
    fun resolve_wasmHashCreate_doesNotMatchAnExternalRefRule() = runTest {
        val wasmCreate = createV1(ContractExecutableXdr.WasmHash(HashXdr(ByteArray(32) { 7 })))
        val rules = listOf(
            parsedRule(1u, ContextRuleType.CreateContractExternalRef(owner, "account-v1")),
            parsedRule(2u, ContextRuleType.CreateContract(ByteArray(32) { 7 }))
        )
        assertEquals(listOf(2u), manager().resolveContextRuleIdsForEntry(entry(wasmCreate), emptyList(), rules))
    }

    @Test
    fun resolve_stellarAssetCreate_stillThrows() = runTest {
        val ex = assertFailsWith<ValidationException.InvalidInput> {
            manager().resolveContextRuleIdsForEntry(entry(createV1(ContractExecutableXdr.Void)), emptyList(), listOf(parsedRule(1u, ContextRuleType.Default)))
        }
        assertTrue(ex.message.contains("Stellar Asset Contract"), ex.message)
    }

    @Test
    fun equality_ownerAndTagBothCount() {
        val type = ContextRuleType.CreateContractExternalRef(owner, "account-v1")
        assertEquals(type, ContextRuleType.CreateContractExternalRef(owner, "account-v1"))
        assertNotEquals<ContextRuleType>(type, ContextRuleType.CreateContractExternalRef(otherOwner, "account-v1"))
        assertNotEquals<ContextRuleType>(type, ContextRuleType.CreateContractExternalRef(owner, "account-v2"))
    }
}
