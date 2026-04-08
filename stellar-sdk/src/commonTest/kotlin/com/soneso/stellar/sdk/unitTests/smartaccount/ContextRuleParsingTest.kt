//
//  ContextRuleParsingTest.kt
//  Stellar SDK Kotlin Multiplatform
//
//  Copyright (c) 2026 Soneso. All rights reserved.
//

package com.soneso.stellar.sdk.unitTests.smartaccount

import com.soneso.stellar.sdk.Address
import com.soneso.stellar.sdk.Network
import com.soneso.stellar.sdk.StrKey
import com.soneso.stellar.sdk.scval.Scv
import com.soneso.stellar.sdk.smartaccount.core.DelegatedSigner
import com.soneso.stellar.sdk.smartaccount.core.ExternalSigner
import com.soneso.stellar.sdk.smartaccount.core.SmartAccountConstants
import com.soneso.stellar.sdk.smartaccount.core.ValidationException
import com.soneso.stellar.sdk.smartaccount.core.WalletException
import com.soneso.stellar.sdk.smartaccount.oz.ContextRuleType
import com.soneso.stellar.sdk.smartaccount.oz.OZContextRuleManager
import com.soneso.stellar.sdk.smartaccount.oz.OZSmartAccountConfig
import com.soneso.stellar.sdk.smartaccount.oz.OZSmartAccountKit
import com.soneso.stellar.sdk.smartaccount.oz.ParsedContextRule
import com.soneso.stellar.sdk.xdr.SCMapEntryXdr
import com.soneso.stellar.sdk.xdr.SCMapXdr
import com.soneso.stellar.sdk.xdr.SCValXdr
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Unit tests for [OZContextRuleManager.parseContextRule], [ContextRuleType.toScVal],
 * [ParsedContextRule] data class, and addContextRule input validation.
 *
 * These tests exercise parsing and validation logic that does not require a connected kit
 * or network access.
 */
class ContextRuleParsingTest {

    // ========================================================================
    // Test Fixtures
    // ========================================================================

    private val validContractAddress = "CAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAD2KM"
    private val validContractAddress2 = "CDLZFC3SYJYDZT7K67VZ75HPJVIEUVNIXF47ZG2FB2RMQQVU2HHGCYSC"
    private val validAccountAddress = "GA7QYNF7SOWQ3GLR2BGMZEHXAVIRZA4KVWLTJJFC7MGXUA74P7UJVSGZ"

    /** Generates a valid C-address from a seed byte to produce unique contract addresses. */
    private fun generateContractAddress(seed: Int): String {
        val bytes = ByteArray(32) { ((it + seed) % 256).toByte() }
        return StrKey.encodeContract(bytes)
    }

    private fun buildConfig(): OZSmartAccountConfig = OZSmartAccountConfig(
        rpcUrl = "https://soroban-testnet.stellar.org",
        networkPassphrase = Network.TESTNET.networkPassphrase,
        accountWasmHash = "a" + "0".repeat(63),
        webauthnVerifierAddress = validContractAddress
    )

    private suspend fun createConnectedKit(): OZSmartAccountKit {
        val kit = OZSmartAccountKit.create(buildConfig())
        kit.setConnectedState("test-credential-id", validContractAddress)
        return kit
    }

    /** Builds an SCValXdr.Map from a list of (symbolKey, value) pairs. */
    private fun buildMapScVal(entries: List<Pair<String, SCValXdr>>): SCValXdr {
        val mapEntries = entries.map { (key, value) ->
            SCMapEntryXdr(key = Scv.toSymbol(key), `val` = value)
        }
        return SCValXdr.Map(SCMapXdr(mapEntries))
    }

    /** Creates an Address SCValXdr from a valid contract C-address. */
    private fun addressScVal(contractAddress: String): SCValXdr {
        return Scv.toAddress(Address(contractAddress).toSCAddress())
    }

    /** Creates a Delegated signer Vec ScVal: Vec([Symbol("Delegated"), Address]). */
    private fun delegatedSignerScVal(address: String): SCValXdr {
        return Scv.toVec(listOf(
            Scv.toSymbol("Delegated"),
            Scv.toAddress(Address(address).toSCAddress())
        ))
    }

    /** Creates an External signer Vec ScVal: Vec([Symbol("External"), Address, Bytes]). */
    private fun externalSignerScVal(verifierAddress: String, keyData: ByteArray): SCValXdr {
        return Scv.toVec(listOf(
            Scv.toSymbol("External"),
            Scv.toAddress(Address(verifierAddress).toSCAddress()),
            Scv.toBytes(keyData)
        ))
    }

    /** Creates a Default context_type Vec: Vec([Symbol("Default")]). */
    private fun defaultContextTypeScVal(): SCValXdr {
        return Scv.toVec(listOf(Scv.toSymbol("Default")))
    }

    /** Creates a CallContract context_type Vec: Vec([Symbol("CallContract"), Address]). */
    private fun callContractContextTypeScVal(contractAddress: String): SCValXdr {
        return Scv.toVec(listOf(
            Scv.toSymbol("CallContract"),
            Scv.toAddress(Address(contractAddress).toSCAddress())
        ))
    }

    /** Creates a CreateContract context_type Vec: Vec([Symbol("CreateContract"), Bytes]). */
    private fun createContractContextTypeScVal(wasmHash: ByteArray): SCValXdr {
        return Scv.toVec(listOf(
            Scv.toSymbol("CreateContract"),
            Scv.toBytes(wasmHash)
        ))
    }

    /** Builds a complete, valid context rule Map ScVal with all fields. */
    private fun buildFullRuleMap(
        id: UInt = 1u,
        name: String = "TestRule",
        contextType: SCValXdr = defaultContextTypeScVal(),
        signers: List<SCValXdr> = emptyList(),
        signerIds: List<UInt> = emptyList(),
        policies: List<SCValXdr> = emptyList(),
        policyIds: List<UInt> = emptyList(),
        validUntil: SCValXdr = Scv.toVoid()
    ): SCValXdr {
        return buildMapScVal(listOf(
            "id" to Scv.toUint32(id),
            "name" to Scv.toString(name),
            "context_type" to contextType,
            "signers" to Scv.toVec(signers),
            "signer_ids" to Scv.toVec(signerIds.map { Scv.toUint32(it) }),
            "policies" to Scv.toVec(policies),
            "policy_ids" to Scv.toVec(policyIds.map { Scv.toUint32(it) }),
            "valid_until" to validUntil
        ))
    }

    private fun secp256r1Key(): ByteArray {
        val key = ByteArray(SmartAccountConstants.SECP256R1_PUBLIC_KEY_SIZE)
        key[0] = SmartAccountConstants.UNCOMPRESSED_PUBKEY_PREFIX
        for (i in 1 until key.size) key[i] = (i % 256).toByte()
        return key
    }

    // ========================================================================
    // parseContextRule — Valid rules
    // ========================================================================

    @Test
    fun testParseContextRule_validRuleWithAllFields() = runTest {
        val kit = OZSmartAccountKit.create(buildConfig())
        val manager = kit.contextRuleManager

        val ruleMap = buildFullRuleMap(
            id = 42u,
            name = "MyRule",
            contextType = defaultContextTypeScVal(),
            signers = listOf(
                delegatedSignerScVal(validAccountAddress)
            ),
            signerIds = listOf(10u),
            policies = listOf(addressScVal(validContractAddress2)),
            policyIds = listOf(20u),
            validUntil = Scv.toUint32(999999u)
        )

        val result = manager.parseContextRule(ruleMap)

        assertEquals(42u, result.id)
        assertEquals("MyRule", result.name)
        assertIs<ContextRuleType.Default>(result.contextType)
        assertEquals(1, result.signers.size)
        assertIs<DelegatedSigner>(result.signers[0])
        assertEquals(validAccountAddress, (result.signers[0] as DelegatedSigner).address)
        assertEquals(listOf(10u), result.signerIds)
        assertEquals(1, result.policies.size)
        assertEquals(listOf(20u), result.policyIds)
        assertEquals(999999u, result.validUntil)
    }

    @Test
    fun testParseContextRule_defaultContextType() = runTest {
        val kit = OZSmartAccountKit.create(buildConfig())
        val manager = kit.contextRuleManager

        val ruleMap = buildFullRuleMap(contextType = defaultContextTypeScVal())
        val result = manager.parseContextRule(ruleMap)

        assertIs<ContextRuleType.Default>(result.contextType)
    }

    @Test
    fun testParseContextRule_callContractContextType() = runTest {
        val kit = OZSmartAccountKit.create(buildConfig())
        val manager = kit.contextRuleManager

        val ruleMap = buildFullRuleMap(
            contextType = callContractContextTypeScVal(validContractAddress2)
        )
        val result = manager.parseContextRule(ruleMap)

        assertIs<ContextRuleType.CallContract>(result.contextType)
        assertEquals(validContractAddress2, (result.contextType as ContextRuleType.CallContract).contractAddress)
    }

    @Test
    fun testParseContextRule_createContractContextType() = runTest {
        val kit = OZSmartAccountKit.create(buildConfig())
        val manager = kit.contextRuleManager

        val wasmHash = ByteArray(32) { (it + 5).toByte() }
        val ruleMap = buildFullRuleMap(
            contextType = createContractContextTypeScVal(wasmHash)
        )
        val result = manager.parseContextRule(ruleMap)

        assertIs<ContextRuleType.CreateContract>(result.contextType)
        assertTrue(wasmHash.contentEquals((result.contextType as ContextRuleType.CreateContract).wasmHash))
    }

    @Test
    fun testParseContextRule_emptySignersAndPolicies() = runTest {
        val kit = OZSmartAccountKit.create(buildConfig())
        val manager = kit.contextRuleManager

        val ruleMap = buildFullRuleMap(
            signers = emptyList(),
            signerIds = emptyList(),
            policies = emptyList(),
            policyIds = emptyList()
        )
        val result = manager.parseContextRule(ruleMap)

        assertTrue(result.signers.isEmpty())
        assertTrue(result.signerIds.isEmpty())
        assertTrue(result.policies.isEmpty())
        assertTrue(result.policyIds.isEmpty())
    }

    @Test
    fun testParseContextRule_multipleSigners_delegatedAndExternal() = runTest {
        val kit = OZSmartAccountKit.create(buildConfig())
        val manager = kit.contextRuleManager

        val keyData = secp256r1Key()
        val ruleMap = buildFullRuleMap(
            signers = listOf(
                delegatedSignerScVal(validAccountAddress),
                externalSignerScVal(validContractAddress2, keyData)
            ),
            signerIds = listOf(1u, 2u)
        )
        val result = manager.parseContextRule(ruleMap)

        assertEquals(2, result.signers.size)
        assertIs<DelegatedSigner>(result.signers[0])
        assertIs<ExternalSigner>(result.signers[1])
        assertEquals(validAccountAddress, (result.signers[0] as DelegatedSigner).address)
        assertEquals(validContractAddress2, (result.signers[1] as ExternalSigner).verifierAddress)
        assertTrue(keyData.contentEquals((result.signers[1] as ExternalSigner).keyData))
    }

    @Test
    fun testParseContextRule_validUntilVoid_noExpiration() = runTest {
        val kit = OZSmartAccountKit.create(buildConfig())
        val manager = kit.contextRuleManager

        val ruleMap = buildFullRuleMap(validUntil = Scv.toVoid())
        val result = manager.parseContextRule(ruleMap)

        assertNull(result.validUntil)
    }

    @Test
    fun testParseContextRule_validUntilU32_hasExpiration() = runTest {
        val kit = OZSmartAccountKit.create(buildConfig())
        val manager = kit.contextRuleManager

        val ruleMap = buildFullRuleMap(validUntil = Scv.toUint32(12345678u))
        val result = manager.parseContextRule(ruleMap)

        assertEquals(12345678u, result.validUntil)
    }

    @Test
    fun testParseContextRule_validUntilFieldMissing_returnsNull() = runTest {
        val kit = OZSmartAccountKit.create(buildConfig())
        val manager = kit.contextRuleManager

        // Build a map without the valid_until field
        val ruleMap = buildMapScVal(listOf(
            "id" to Scv.toUint32(1u),
            "name" to Scv.toString("NoExpiry"),
            "context_type" to defaultContextTypeScVal(),
            "signers" to Scv.toVec(emptyList()),
            "signer_ids" to Scv.toVec(emptyList()),
            "policies" to Scv.toVec(emptyList()),
            "policy_ids" to Scv.toVec(emptyList())
        ))
        val result = manager.parseContextRule(ruleMap)

        assertNull(result.validUntil)
    }

    // ========================================================================
    // parseContextRule — Missing required fields
    // ========================================================================

    @Test
    fun testParseContextRule_missingId_throwsValidationException() = runTest {
        val kit = OZSmartAccountKit.create(buildConfig())
        val manager = kit.contextRuleManager

        val ruleMap = buildMapScVal(listOf(
            "name" to Scv.toString("TestRule"),
            "context_type" to defaultContextTypeScVal()
        ))

        val ex = assertFailsWith<ValidationException> {
            manager.parseContextRule(ruleMap)
        }
        assertTrue(ex.message!!.contains("id"), "Exception message should mention 'id'")
    }

    @Test
    fun testParseContextRule_missingName_throwsValidationException() = runTest {
        val kit = OZSmartAccountKit.create(buildConfig())
        val manager = kit.contextRuleManager

        val ruleMap = buildMapScVal(listOf(
            "id" to Scv.toUint32(1u),
            "context_type" to defaultContextTypeScVal()
        ))

        val ex = assertFailsWith<ValidationException> {
            manager.parseContextRule(ruleMap)
        }
        assertTrue(ex.message!!.contains("name"), "Exception message should mention 'name'")
    }

    @Test
    fun testParseContextRule_missingContextType_throwsValidationException() = runTest {
        val kit = OZSmartAccountKit.create(buildConfig())
        val manager = kit.contextRuleManager

        val ruleMap = buildMapScVal(listOf(
            "id" to Scv.toUint32(1u),
            "name" to Scv.toString("TestRule")
        ))

        val ex = assertFailsWith<ValidationException> {
            manager.parseContextRule(ruleMap)
        }
        assertTrue(ex.message!!.contains("context_type"), "Exception message should mention 'context_type'")
    }

    // ========================================================================
    // parseContextRule — Invalid field types
    // ========================================================================

    @Test
    fun testParseContextRule_idNotU32_throwsValidationException() = runTest {
        val kit = OZSmartAccountKit.create(buildConfig())
        val manager = kit.contextRuleManager

        val ruleMap = buildMapScVal(listOf(
            "id" to Scv.toString("not-a-number"),
            "name" to Scv.toString("TestRule"),
            "context_type" to defaultContextTypeScVal()
        ))

        val ex = assertFailsWith<ValidationException> {
            manager.parseContextRule(ruleMap)
        }
        assertTrue(ex.message!!.contains("id"), "Exception message should mention 'id'")
    }

    @Test
    fun testParseContextRule_nameNotString_throwsValidationException() = runTest {
        val kit = OZSmartAccountKit.create(buildConfig())
        val manager = kit.contextRuleManager

        val ruleMap = buildMapScVal(listOf(
            "id" to Scv.toUint32(1u),
            "name" to Scv.toUint32(42u),
            "context_type" to defaultContextTypeScVal()
        ))

        val ex = assertFailsWith<ValidationException> {
            manager.parseContextRule(ruleMap)
        }
        assertTrue(ex.message!!.contains("name"), "Exception message should mention 'name'")
    }

    @Test
    fun testParseContextRule_contextTypeNotVec_throwsValidationException() = runTest {
        val kit = OZSmartAccountKit.create(buildConfig())
        val manager = kit.contextRuleManager

        val ruleMap = buildMapScVal(listOf(
            "id" to Scv.toUint32(1u),
            "name" to Scv.toString("TestRule"),
            "context_type" to Scv.toString("Default")
        ))

        val ex = assertFailsWith<ValidationException> {
            manager.parseContextRule(ruleMap)
        }
        assertTrue(ex.message!!.contains("context_type"), "Exception message should mention 'context_type'")
    }

    @Test
    fun testParseContextRule_signersNotVec_throwsValidationException() = runTest {
        val kit = OZSmartAccountKit.create(buildConfig())
        val manager = kit.contextRuleManager

        val ruleMap = buildMapScVal(listOf(
            "id" to Scv.toUint32(1u),
            "name" to Scv.toString("TestRule"),
            "context_type" to defaultContextTypeScVal(),
            "signers" to Scv.toString("not-a-vec")
        ))

        val ex = assertFailsWith<ValidationException> {
            manager.parseContextRule(ruleMap)
        }
        assertTrue(ex.message!!.contains("signers"), "Exception message should mention 'signers'")
    }

    @Test
    fun testParseContextRule_signerIdsEntryNotU32_throwsValidationException() = runTest {
        val kit = OZSmartAccountKit.create(buildConfig())
        val manager = kit.contextRuleManager

        val ruleMap = buildMapScVal(listOf(
            "id" to Scv.toUint32(1u),
            "name" to Scv.toString("TestRule"),
            "context_type" to defaultContextTypeScVal(),
            "signer_ids" to Scv.toVec(listOf(Scv.toString("not-a-u32")))
        ))

        val ex = assertFailsWith<ValidationException> {
            manager.parseContextRule(ruleMap)
        }
        assertTrue(ex.message!!.contains("signer_ids"), "Exception message should mention 'signer_ids'")
    }

    @Test
    fun testParseContextRule_validUntilInvalidType_throwsValidationException() = runTest {
        val kit = OZSmartAccountKit.create(buildConfig())
        val manager = kit.contextRuleManager

        val ruleMap = buildFullRuleMap(validUntil = Scv.toString("not-a-u32"))

        val ex = assertFailsWith<ValidationException> {
            manager.parseContextRule(ruleMap)
        }
        assertTrue(ex.message!!.contains("valid_until"), "Exception message should mention 'valid_until'")
    }

    // ========================================================================
    // parseContextRule — Non-Map input
    // ========================================================================

    @Test
    fun testParseContextRule_nonMapInput_throwsValidationException() = runTest {
        val kit = OZSmartAccountKit.create(buildConfig())
        val manager = kit.contextRuleManager

        val ex = assertFailsWith<ValidationException> {
            manager.parseContextRule(Scv.toVec(emptyList()))
        }
        assertTrue(ex.message!!.contains("Map"), "Exception message should mention 'Map'")
    }

    @Test
    fun testParseContextRule_voidInput_throwsValidationException() = runTest {
        val kit = OZSmartAccountKit.create(buildConfig())
        val manager = kit.contextRuleManager

        assertFailsWith<ValidationException> {
            manager.parseContextRule(Scv.toVoid())
        }
    }

    @Test
    fun testParseContextRule_stringInput_throwsValidationException() = runTest {
        val kit = OZSmartAccountKit.create(buildConfig())
        val manager = kit.contextRuleManager

        assertFailsWith<ValidationException> {
            manager.parseContextRule(Scv.toString("not-a-map"))
        }
    }

    // ========================================================================
    // parseContextRule — Context type edge cases
    // ========================================================================

    @Test
    fun testParseContextRule_emptyContextTypeVec_throwsValidationException() = runTest {
        val kit = OZSmartAccountKit.create(buildConfig())
        val manager = kit.contextRuleManager

        val ruleMap = buildFullRuleMap(contextType = Scv.toVec(emptyList()))

        val ex = assertFailsWith<ValidationException> {
            manager.parseContextRule(ruleMap)
        }
        assertTrue(ex.message!!.contains("context_type"), "Exception message should mention 'context_type'")
    }

    @Test
    fun testParseContextRule_unknownContextTypeDiscriminant_throwsValidationException() = runTest {
        val kit = OZSmartAccountKit.create(buildConfig())
        val manager = kit.contextRuleManager

        val unknownType = Scv.toVec(listOf(Scv.toSymbol("Unknown")))
        val ruleMap = buildFullRuleMap(contextType = unknownType)

        val ex = assertFailsWith<ValidationException> {
            manager.parseContextRule(ruleMap)
        }
        assertTrue(ex.message!!.contains("Unknown"), "Exception message should contain the unknown discriminant")
    }

    @Test
    fun testParseContextRule_callContractMissingAddress_throwsValidationException() = runTest {
        val kit = OZSmartAccountKit.create(buildConfig())
        val manager = kit.contextRuleManager

        // CallContract with only the discriminant symbol, no address element
        val badCallContract = Scv.toVec(listOf(Scv.toSymbol("CallContract")))
        val ruleMap = buildFullRuleMap(contextType = badCallContract)

        val ex = assertFailsWith<ValidationException> {
            manager.parseContextRule(ruleMap)
        }
        assertTrue(ex.message!!.contains("CallContract"), "Exception message should mention 'CallContract'")
    }

    @Test
    fun testParseContextRule_createContractMissingHash_throwsValidationException() = runTest {
        val kit = OZSmartAccountKit.create(buildConfig())
        val manager = kit.contextRuleManager

        val badCreateContract = Scv.toVec(listOf(Scv.toSymbol("CreateContract")))
        val ruleMap = buildFullRuleMap(contextType = badCreateContract)

        val ex = assertFailsWith<ValidationException> {
            manager.parseContextRule(ruleMap)
        }
        assertTrue(ex.message!!.contains("CreateContract"), "Exception message should mention 'CreateContract'")
    }

    // ========================================================================
    // parseContextRule — Signer parsing edge cases
    // ========================================================================

    @Test
    fun testParseContextRule_unknownSignerDiscriminant_throwsValidationException() = runTest {
        val kit = OZSmartAccountKit.create(buildConfig())
        val manager = kit.contextRuleManager

        val unknownSigner = Scv.toVec(listOf(Scv.toSymbol("UnknownType")))
        val ruleMap = buildFullRuleMap(
            signers = listOf(unknownSigner),
            signerIds = listOf(1u)
        )

        val ex = assertFailsWith<ValidationException> {
            manager.parseContextRule(ruleMap)
        }
        assertTrue(ex.message!!.contains("UnknownType"), "Exception message should contain the unknown discriminant")
    }

    @Test
    fun testParseContextRule_emptySignerVec_throwsValidationException() = runTest {
        val kit = OZSmartAccountKit.create(buildConfig())
        val manager = kit.contextRuleManager

        val emptySigner = Scv.toVec(emptyList())
        val ruleMap = buildFullRuleMap(
            signers = listOf(emptySigner),
            signerIds = listOf(1u)
        )

        val ex = assertFailsWith<ValidationException> {
            manager.parseContextRule(ruleMap)
        }
        assertTrue(ex.message!!.contains("signer"), "Exception message should mention 'signer'")
    }

    @Test
    fun testParseContextRule_delegatedSignerMissingAddress_throwsValidationException() = runTest {
        val kit = OZSmartAccountKit.create(buildConfig())
        val manager = kit.contextRuleManager

        val badDelegated = Scv.toVec(listOf(Scv.toSymbol("Delegated")))
        val ruleMap = buildFullRuleMap(
            signers = listOf(badDelegated),
            signerIds = listOf(1u)
        )

        val ex = assertFailsWith<ValidationException> {
            manager.parseContextRule(ruleMap)
        }
        assertTrue(ex.message!!.contains("Delegated"), "Exception message should mention 'Delegated'")
    }

    @Test
    fun testParseContextRule_externalSignerMissingKeyData_throwsValidationException() = runTest {
        val kit = OZSmartAccountKit.create(buildConfig())
        val manager = kit.contextRuleManager

        // External with only discriminant + address, missing keyData bytes
        val badExternal = Scv.toVec(listOf(
            Scv.toSymbol("External"),
            Scv.toAddress(Address(validContractAddress).toSCAddress())
        ))
        val ruleMap = buildFullRuleMap(
            signers = listOf(badExternal),
            signerIds = listOf(1u)
        )

        val ex = assertFailsWith<ValidationException> {
            manager.parseContextRule(ruleMap)
        }
        assertTrue(ex.message!!.contains("External"), "Exception message should mention 'External'")
    }

    // ========================================================================
    // ContextRuleType.toScVal()
    // ========================================================================

    @Test
    fun testContextRuleType_defaultToScVal() {
        val scVal = ContextRuleType.Default.toScVal()

        assertIs<SCValXdr.Vec>(scVal)
        val elements = Scv.fromVec(scVal)
        assertEquals(1, elements.size)
        assertEquals("Default", Scv.fromSymbol(elements[0]))
    }

    @Test
    fun testContextRuleType_callContractToScVal() {
        val scVal = ContextRuleType.CallContract(validContractAddress2).toScVal()

        assertIs<SCValXdr.Vec>(scVal)
        val elements = Scv.fromVec(scVal)
        assertEquals(2, elements.size)
        assertEquals("CallContract", Scv.fromSymbol(elements[0]))
        assertIs<SCValXdr.Address>(elements[1])

        // Round-trip: parse the address back
        val parsedAddress = Address.fromSCVal(elements[1]).toString()
        assertEquals(validContractAddress2, parsedAddress)
    }

    @Test
    fun testContextRuleType_createContractToScVal() {
        val wasmHash = ByteArray(32) { (it * 3).toByte() }
        val scVal = ContextRuleType.CreateContract(wasmHash).toScVal()

        assertIs<SCValXdr.Vec>(scVal)
        val elements = Scv.fromVec(scVal)
        assertEquals(2, elements.size)
        assertEquals("CreateContract", Scv.fromSymbol(elements[0]))
        assertIs<SCValXdr.Bytes>(elements[1])

        val parsedHash = Scv.fromBytes(elements[1])
        assertTrue(wasmHash.contentEquals(parsedHash))
    }

    @Test
    fun testContextRuleType_callContractInvalidAddress_throwsValidationException() {
        assertFailsWith<ValidationException> {
            ContextRuleType.CallContract("INVALID_ADDRESS").toScVal()
        }
    }

    // ========================================================================
    // ContextRuleType equality and hashCode
    // ========================================================================

    @Test
    fun testContextRuleType_defaultEquality() {
        assertEquals(ContextRuleType.Default, ContextRuleType.Default)
    }

    @Test
    fun testContextRuleType_callContractEquality() {
        val a = ContextRuleType.CallContract(validContractAddress)
        val b = ContextRuleType.CallContract(validContractAddress)
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun testContextRuleType_createContractEquality() {
        val hash = ByteArray(32) { it.toByte() }
        val a = ContextRuleType.CreateContract(hash.copyOf())
        val b = ContextRuleType.CreateContract(hash.copyOf())
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    // ========================================================================
    // ParsedContextRule data class
    // ========================================================================

    @Test
    fun testParsedContextRule_constructionAndFieldAccess() {
        val signer = DelegatedSigner(validAccountAddress)
        val rule = ParsedContextRule(
            id = 5u,
            contextType = ContextRuleType.Default,
            name = "TestRule",
            signers = listOf(signer),
            signerIds = listOf(10u),
            policies = listOf(validContractAddress2),
            policyIds = listOf(20u),
            validUntil = 100u
        )

        assertEquals(5u, rule.id)
        assertIs<ContextRuleType.Default>(rule.contextType)
        assertEquals("TestRule", rule.name)
        assertEquals(1, rule.signers.size)
        assertEquals(listOf(10u), rule.signerIds)
        assertEquals(listOf(validContractAddress2), rule.policies)
        assertEquals(listOf(20u), rule.policyIds)
        assertEquals(100u, rule.validUntil)
    }

    @Test
    fun testParsedContextRule_nullValidUntil() {
        val rule = ParsedContextRule(
            id = 0u,
            contextType = ContextRuleType.Default,
            name = "NoExpiry",
            signers = emptyList(),
            signerIds = emptyList(),
            policies = emptyList(),
            policyIds = emptyList(),
            validUntil = null
        )

        assertNull(rule.validUntil)
    }

    @Test
    fun testParsedContextRule_dataClassCopy() {
        val original = ParsedContextRule(
            id = 1u,
            contextType = ContextRuleType.Default,
            name = "Original",
            signers = emptyList(),
            signerIds = emptyList(),
            policies = emptyList(),
            policyIds = emptyList(),
            validUntil = null
        )

        val copy = original.copy(name = "Modified", id = 2u)
        assertEquals(2u, copy.id)
        assertEquals("Modified", copy.name)
        assertEquals(original.contextType, copy.contextType)
    }

    // ========================================================================
    // addContextRule — Input validation (without network)
    // ========================================================================

    @Test
    fun testAddContextRule_notConnected_throwsWalletNotConnected() = runTest {
        val kit = OZSmartAccountKit.create(buildConfig())
        val manager = kit.contextRuleManager

        assertFailsWith<WalletException.NotConnected> {
            manager.addContextRule(
                contextType = ContextRuleType.Default,
                name = "Rule",
                signers = listOf(DelegatedSigner(validAccountAddress))
            )
        }
    }

    @Test
    fun testAddContextRule_emptyName_throwsValidationException() = runTest {
        val kit = createConnectedKit()
        val manager = kit.contextRuleManager

        val ex = assertFailsWith<ValidationException> {
            manager.addContextRule(
                contextType = ContextRuleType.Default,
                name = "",
                signers = listOf(DelegatedSigner(validAccountAddress))
            )
        }
        assertTrue(ex.message!!.contains("name"), "Exception message should mention 'name'")
    }

    @Test
    fun testAddContextRule_emptySignersAndPolicies_throwsValidationException() = runTest {
        val kit = createConnectedKit()
        val manager = kit.contextRuleManager

        val ex = assertFailsWith<ValidationException> {
            manager.addContextRule(
                contextType = ContextRuleType.Default,
                name = "EmptyRule",
                signers = emptyList(),
                policies = emptyMap()
            )
        }
        assertTrue(ex.message!!.contains("signer") || ex.message!!.contains("policy"),
            "Exception message should mention signers or policies")
    }

    @Test
    fun testAddContextRule_tooManySigners_throwsValidationException() = runTest {
        val kit = createConnectedKit()
        val manager = kit.contextRuleManager

        // Create 16 signers (MAX_SIGNERS is 15)
        val signers = (0 until 16).map {
            DelegatedSigner(validAccountAddress)
        }

        val ex = assertFailsWith<ValidationException> {
            manager.addContextRule(
                contextType = ContextRuleType.Default,
                name = "TooManySigners",
                signers = signers
            )
        }
        assertTrue(ex.message!!.contains("15") || ex.message!!.contains("signers"),
            "Exception message should mention signer limit")
    }

    @Test
    fun testAddContextRule_tooManyPolicies_throwsValidationException() = runTest {
        val kit = createConnectedKit()
        val manager = kit.contextRuleManager

        // Generate 6 unique valid C-addresses (MAX_POLICIES is 5)
        val policies = (0 until 6).associate { i ->
            generateContractAddress(i * 10) to Scv.toVoid()
        }
        assertEquals(6, policies.size, "Precondition: must have 6 unique policy addresses")

        val ex = assertFailsWith<ValidationException> {
            manager.addContextRule(
                contextType = ContextRuleType.Default,
                name = "TooManyPolicies",
                signers = listOf(DelegatedSigner(validAccountAddress)),
                policies = policies
            )
        }
        assertTrue(ex.message!!.contains("5") || ex.message!!.contains("policies"),
            "Exception message should mention policy limit")
    }

    @Test
    fun testAddContextRule_invalidPolicyAddress_throwsValidationException() = runTest {
        val kit = createConnectedKit()
        val manager = kit.contextRuleManager

        val ex = assertFailsWith<ValidationException> {
            manager.addContextRule(
                contextType = ContextRuleType.Default,
                name = "BadPolicy",
                signers = listOf(DelegatedSigner(validAccountAddress)),
                policies = mapOf("INVALID_ADDRESS" to Scv.toVoid())
            )
        }
        assertTrue(ex.message!!.contains("address") || ex.message!!.contains("Address"),
            "Exception message should mention address validation")
    }

    @Test
    fun testAddContextRule_gAddressAsPolicy_throwsValidationException() = runTest {
        val kit = createConnectedKit()
        val manager = kit.contextRuleManager

        // Policy addresses must be C-addresses (contracts), not G-addresses (accounts)
        val ex = assertFailsWith<ValidationException> {
            manager.addContextRule(
                contextType = ContextRuleType.Default,
                name = "GAddressPolicy",
                signers = listOf(DelegatedSigner(validAccountAddress)),
                policies = mapOf(validAccountAddress to Scv.toVoid())
            )
        }
        assertNotNull(ex.message)
    }

    // ========================================================================
    // parseContextRule — Round-trip: toScVal then parse back
    // ========================================================================

    @Test
    fun testRoundTrip_defaultContextType() = runTest {
        val kit = OZSmartAccountKit.create(buildConfig())
        val manager = kit.contextRuleManager

        val originalType = ContextRuleType.Default
        val scVal = originalType.toScVal()
        val ruleMap = buildFullRuleMap(contextType = scVal)
        val parsed = manager.parseContextRule(ruleMap)

        assertIs<ContextRuleType.Default>(parsed.contextType)
    }

    @Test
    fun testRoundTrip_callContractContextType() = runTest {
        val kit = OZSmartAccountKit.create(buildConfig())
        val manager = kit.contextRuleManager

        val originalType = ContextRuleType.CallContract(validContractAddress2)
        val scVal = originalType.toScVal()
        val ruleMap = buildFullRuleMap(contextType = scVal)
        val parsed = manager.parseContextRule(ruleMap)

        assertIs<ContextRuleType.CallContract>(parsed.contextType)
        assertEquals(validContractAddress2, (parsed.contextType as ContextRuleType.CallContract).contractAddress)
    }

    @Test
    fun testRoundTrip_createContractContextType() = runTest {
        val kit = OZSmartAccountKit.create(buildConfig())
        val manager = kit.contextRuleManager

        val wasmHash = ByteArray(32) { (it * 7).toByte() }
        val originalType = ContextRuleType.CreateContract(wasmHash)
        val scVal = originalType.toScVal()
        val ruleMap = buildFullRuleMap(contextType = scVal)
        val parsed = manager.parseContextRule(ruleMap)

        assertIs<ContextRuleType.CreateContract>(parsed.contextType)
        assertTrue(wasmHash.contentEquals((parsed.contextType as ContextRuleType.CreateContract).wasmHash))
    }

    // ========================================================================
    // parseContextRule — Multiple policies parsing
    // ========================================================================

    @Test
    fun testParseContextRule_multiplePolicies() = runTest {
        val kit = OZSmartAccountKit.create(buildConfig())
        val manager = kit.contextRuleManager

        val ruleMap = buildFullRuleMap(
            policies = listOf(
                addressScVal(validContractAddress),
                addressScVal(validContractAddress2)
            ),
            policyIds = listOf(10u, 20u)
        )
        val result = manager.parseContextRule(ruleMap)

        assertEquals(2, result.policies.size)
        assertEquals(2, result.policyIds.size)
        assertEquals(10u, result.policyIds[0])
        assertEquals(20u, result.policyIds[1])
    }

    // ========================================================================
    // parseContextRule — Fields looked up by key name, not position
    // ========================================================================

    @Test
    fun testParseContextRule_fieldsInNonAlphabeticalOrder() = runTest {
        val kit = OZSmartAccountKit.create(buildConfig())
        val manager = kit.contextRuleManager

        // Deliberately put fields in reverse alphabetical order to verify
        // the parser looks up by key name, not by position
        val ruleMap = buildMapScVal(listOf(
            "valid_until" to Scv.toVoid(),
            "signers" to Scv.toVec(emptyList()),
            "signer_ids" to Scv.toVec(emptyList()),
            "policies" to Scv.toVec(emptyList()),
            "policy_ids" to Scv.toVec(emptyList()),
            "name" to Scv.toString("ReversedFields"),
            "id" to Scv.toUint32(99u),
            "context_type" to defaultContextTypeScVal()
        ))

        val result = manager.parseContextRule(ruleMap)
        assertEquals(99u, result.id)
        assertEquals("ReversedFields", result.name)
        assertIs<ContextRuleType.Default>(result.contextType)
    }

    // ========================================================================
    // parseContextRule — Non-symbol keys are silently skipped
    // ========================================================================

    @Test
    fun testParseContextRule_nonSymbolKeysIgnored() = runTest {
        val kit = OZSmartAccountKit.create(buildConfig())
        val manager = kit.contextRuleManager

        // Add entries with non-symbol keys alongside valid entries
        val entries = listOf(
            SCMapEntryXdr(key = Scv.toUint32(0u), `val` = Scv.toString("ignored")),
            SCMapEntryXdr(key = Scv.toSymbol("id"), `val` = Scv.toUint32(7u)),
            SCMapEntryXdr(key = Scv.toSymbol("name"), `val` = Scv.toString("WithExtra")),
            SCMapEntryXdr(key = Scv.toSymbol("context_type"), `val` = defaultContextTypeScVal()),
            SCMapEntryXdr(key = Scv.toBytes(ByteArray(4)), `val` = Scv.toVoid())
        )
        val ruleMap = SCValXdr.Map(SCMapXdr(entries))

        val result = manager.parseContextRule(ruleMap)
        assertEquals(7u, result.id)
        assertEquals("WithExtra", result.name)
    }

    // ========================================================================
    // parseContextRule — Policy address parsing errors
    // ========================================================================

    @Test
    fun testParseContextRule_policiesEntryNotAddress_throwsValidationException() = runTest {
        val kit = OZSmartAccountKit.create(buildConfig())
        val manager = kit.contextRuleManager

        val ruleMap = buildMapScVal(listOf(
            "id" to Scv.toUint32(1u),
            "name" to Scv.toString("BadPolicies"),
            "context_type" to defaultContextTypeScVal(),
            "policies" to Scv.toVec(listOf(Scv.toString("not-an-address")))
        ))

        val ex = assertFailsWith<ValidationException> {
            manager.parseContextRule(ruleMap)
        }
        assertTrue(ex.message!!.contains("policies"), "Exception message should mention 'policies'")
    }

    @Test
    fun testParseContextRule_policyIdsNotVec_throwsValidationException() = runTest {
        val kit = OZSmartAccountKit.create(buildConfig())
        val manager = kit.contextRuleManager

        val ruleMap = buildMapScVal(listOf(
            "id" to Scv.toUint32(1u),
            "name" to Scv.toString("BadPolicyIds"),
            "context_type" to defaultContextTypeScVal(),
            "policy_ids" to Scv.toString("not-a-vec")
        ))

        val ex = assertFailsWith<ValidationException> {
            manager.parseContextRule(ruleMap)
        }
        assertTrue(ex.message!!.contains("policy_ids"), "Exception message should mention 'policy_ids'")
    }

    @Test
    fun testParseContextRule_policyIdsEntryNotU32_throwsValidationException() = runTest {
        val kit = OZSmartAccountKit.create(buildConfig())
        val manager = kit.contextRuleManager

        val ruleMap = buildMapScVal(listOf(
            "id" to Scv.toUint32(1u),
            "name" to Scv.toString("BadPolicyIdEntry"),
            "context_type" to defaultContextTypeScVal(),
            "policy_ids" to Scv.toVec(listOf(Scv.toString("not-a-u32")))
        ))

        val ex = assertFailsWith<ValidationException> {
            manager.parseContextRule(ruleMap)
        }
        assertTrue(ex.message!!.contains("policy_ids"), "Exception message should mention 'policy_ids'")
    }

    // ========================================================================
    // parseContextRule — Signer not a Vec
    // ========================================================================

    @Test
    fun testParseContextRule_signerEntryNotVec_throwsValidationException() = runTest {
        val kit = OZSmartAccountKit.create(buildConfig())
        val manager = kit.contextRuleManager

        val ruleMap = buildMapScVal(listOf(
            "id" to Scv.toUint32(1u),
            "name" to Scv.toString("BadSigner"),
            "context_type" to defaultContextTypeScVal(),
            "signers" to Scv.toVec(listOf(Scv.toString("not-a-signer-vec")))
        ))

        val ex = assertFailsWith<ValidationException> {
            manager.parseContextRule(ruleMap)
        }
        assertTrue(ex.message!!.contains("signer"), "Exception message should mention 'signer'")
    }

    // ========================================================================
    // parseContextRule — Context type discriminant not a symbol
    // ========================================================================

    @Test
    fun testParseContextRule_contextTypeDiscriminantNotSymbol_throwsValidationException() = runTest {
        val kit = OZSmartAccountKit.create(buildConfig())
        val manager = kit.contextRuleManager

        val badType = Scv.toVec(listOf(Scv.toUint32(42u)))
        val ruleMap = buildFullRuleMap(contextType = badType)

        val ex = assertFailsWith<ValidationException> {
            manager.parseContextRule(ruleMap)
        }
        assertTrue(ex.message!!.contains("context_type"), "Exception message should mention 'context_type'")
    }
}
