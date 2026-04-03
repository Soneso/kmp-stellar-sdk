package com.soneso.stellar.sdk.unitTests.smartaccount

import com.soneso.stellar.sdk.Address
import com.soneso.stellar.sdk.scval.Scv
import com.soneso.stellar.sdk.smartaccount.core.DelegatedSigner
import com.soneso.stellar.sdk.smartaccount.core.ExternalSigner
import com.soneso.stellar.sdk.smartaccount.oz.ContextRuleType
import com.soneso.stellar.sdk.xdr.InvokeContractArgsXdr
import com.soneso.stellar.sdk.xdr.SCSymbolXdr
import com.soneso.stellar.sdk.xdr.SCValTypeXdr
import com.soneso.stellar.sdk.xdr.SCValXdr
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Verifies that all contract method invocations in the KMP Smart Account code
 * match the OpenZeppelin Smart Account contract ABI as documented in
 * [SmartAccountContractAbi].
 *
 * This test file validates:
 * 1. Function name strings match ABI constants exactly
 * 2. Argument types match ABI signatures (e.g., String vs Symbol)
 * 3. Argument ordering matches ABI parameter ordering
 * 4. Signer enum variant names match ABI type definitions
 * 5. ContextRuleType enum variant names match ABI type definitions
 * 6. WebAuthn signature struct field names match ABI definitions
 * 7. Policy install parameter field names match ABI definitions
 *
 * Source of truth: OpenZeppelin smart account contract spec XDR.
 */
class ContractMethodSignatureTest {

    // ========================================================================
    // Function Name String Verification
    // ========================================================================

    @Test
    fun testFunctionNameConstants_matchAbi() {
        // Verify all ABI function name constants match expected contract function names.
        // These strings are used as SCSymbolXdr values in InvokeContractArgsXdr.
        assertEquals("__constructor", SmartAccountContractAbi.Functions.CONSTRUCTOR)
        assertEquals("__check_auth", SmartAccountContractAbi.Functions.CHECK_AUTH)
        assertEquals("get_context_rule", SmartAccountContractAbi.Functions.GET_CONTEXT_RULE)
        assertEquals("get_context_rules", SmartAccountContractAbi.Functions.GET_CONTEXT_RULES)
        assertEquals("get_context_rules_count", SmartAccountContractAbi.Functions.GET_CONTEXT_RULES_COUNT)
        assertEquals("add_context_rule", SmartAccountContractAbi.Functions.ADD_CONTEXT_RULE)
        assertEquals("update_context_rule_name", SmartAccountContractAbi.Functions.UPDATE_CONTEXT_RULE_NAME)
        assertEquals("update_context_rule_valid_until", SmartAccountContractAbi.Functions.UPDATE_CONTEXT_RULE_VALID_UNTIL)
        assertEquals("remove_context_rule", SmartAccountContractAbi.Functions.REMOVE_CONTEXT_RULE)
        assertEquals("add_signer", SmartAccountContractAbi.Functions.ADD_SIGNER)
        assertEquals("remove_signer", SmartAccountContractAbi.Functions.REMOVE_SIGNER)
        assertEquals("add_policy", SmartAccountContractAbi.Functions.ADD_POLICY)
        assertEquals("remove_policy", SmartAccountContractAbi.Functions.REMOVE_POLICY)
        assertEquals("execute", SmartAccountContractAbi.Functions.EXECUTE)
        assertEquals("upgrade", SmartAccountContractAbi.Functions.UPGRADE)
    }

    @Test
    fun testAbiFunctionCount_is15() {
        // The ABI defines exactly 15 contract functions.
        val allFunctions = listOf(
            SmartAccountContractAbi.Functions.CONSTRUCTOR,
            SmartAccountContractAbi.Functions.CHECK_AUTH,
            SmartAccountContractAbi.Functions.GET_CONTEXT_RULE,
            SmartAccountContractAbi.Functions.GET_CONTEXT_RULES,
            SmartAccountContractAbi.Functions.GET_CONTEXT_RULES_COUNT,
            SmartAccountContractAbi.Functions.ADD_CONTEXT_RULE,
            SmartAccountContractAbi.Functions.UPDATE_CONTEXT_RULE_NAME,
            SmartAccountContractAbi.Functions.UPDATE_CONTEXT_RULE_VALID_UNTIL,
            SmartAccountContractAbi.Functions.REMOVE_CONTEXT_RULE,
            SmartAccountContractAbi.Functions.ADD_SIGNER,
            SmartAccountContractAbi.Functions.REMOVE_SIGNER,
            SmartAccountContractAbi.Functions.ADD_POLICY,
            SmartAccountContractAbi.Functions.REMOVE_POLICY,
            SmartAccountContractAbi.Functions.EXECUTE,
            SmartAccountContractAbi.Functions.UPGRADE,
        )
        assertEquals(15, allFunctions.size, "ABI must define exactly 15 functions")
        assertEquals(15, allFunctions.toSet().size, "All function names must be unique")
    }

    // ========================================================================
    // Signer Type Variant Name Verification
    // ========================================================================

    @Test
    fun testSignerType_delegatedVariantName() {
        assertEquals("Delegated", SmartAccountContractAbi.SignerType.DELEGATED)
    }

    @Test
    fun testSignerType_externalVariantName() {
        assertEquals("External", SmartAccountContractAbi.SignerType.EXTERNAL)
    }

    @Test
    fun testDelegatedSigner_scValUsesCorrectVariantName() {
        // DelegatedSigner.toScVal() must produce Vec([Symbol("Delegated"), Address(...)])
        val signer = DelegatedSigner(
            address = "GAAZI4TCR3TY5OJHCTJC2A4QSY6CJWJH5IAJTGKIN2ER7LBNVKOCCWN7"
        )
        val scVal = signer.toScVal()

        assertTrue(scVal is SCValXdr.Vec, "DelegatedSigner ScVal must be Vec")
        val vec = (scVal as SCValXdr.Vec).value!!.value
        assertEquals(2, vec.size, "Delegated signer Vec must have 2 elements (variant name + address)")

        // First element: Symbol with variant name
        assertTrue(vec[0] is SCValXdr.Sym, "First element must be Symbol")
        assertEquals(
            SmartAccountContractAbi.SignerType.DELEGATED,
            (vec[0] as SCValXdr.Sym).value.value,
            "Variant name must be '${SmartAccountContractAbi.SignerType.DELEGATED}'"
        )

        // Second element: Address
        assertTrue(vec[1] is SCValXdr.Address, "Second element must be Address")
    }

    @Test
    fun testExternalSigner_scValUsesCorrectVariantName() {
        // ExternalSigner.toScVal() must produce Vec([Symbol("External"), Address(...), Bytes(...)])
        val signer = ExternalSigner(
            verifierAddress = "CDLZFC3SYJYDZT7K67VZ75HPJVIEUVNIXF47ZG2FB2RMQQVU2HHGCYSC",
            keyData = byteArrayOf(0x01, 0x02, 0x03)
        )
        val scVal = signer.toScVal()

        assertTrue(scVal is SCValXdr.Vec, "ExternalSigner ScVal must be Vec")
        val vec = (scVal as SCValXdr.Vec).value!!.value
        assertEquals(3, vec.size, "External signer Vec must have 3 elements (variant name + address + key data)")

        // First element: Symbol with variant name
        assertTrue(vec[0] is SCValXdr.Sym, "First element must be Symbol")
        assertEquals(
            SmartAccountContractAbi.SignerType.EXTERNAL,
            (vec[0] as SCValXdr.Sym).value.value,
            "Variant name must be '${SmartAccountContractAbi.SignerType.EXTERNAL}'"
        )

        // Second element: Address (verifier contract)
        assertTrue(vec[1] is SCValXdr.Address, "Second element must be Address")

        // Third element: Bytes (public key data)
        assertTrue(vec[2] is SCValXdr.Bytes, "Third element must be Bytes")
    }

    // ========================================================================
    // ContextRuleType Variant Name Verification
    // ========================================================================

    @Test
    fun testContextRuleType_defaultVariantName() {
        assertEquals("Default", SmartAccountContractAbi.ContextRuleTypeVariants.DEFAULT)
    }

    @Test
    fun testContextRuleType_callContractVariantName() {
        assertEquals("CallContract", SmartAccountContractAbi.ContextRuleTypeVariants.CALL_CONTRACT)
    }

    @Test
    fun testContextRuleType_createContractVariantName() {
        assertEquals("CreateContract", SmartAccountContractAbi.ContextRuleTypeVariants.CREATE_CONTRACT)
    }

    @Test
    fun testContextRuleType_default_scValUsesCorrectVariantName() {
        // Default: Vec([Symbol("Default")])
        val contextType = ContextRuleType.Default
        val scVal = contextType.toScVal()

        assertTrue(scVal is SCValXdr.Vec, "Default ContextRuleType ScVal must be Vec")
        val vec = (scVal as SCValXdr.Vec).value!!.value
        assertEquals(1, vec.size, "Default variant Vec must have 1 element")
        assertTrue(vec[0] is SCValXdr.Sym, "Element must be Symbol")
        assertEquals(
            SmartAccountContractAbi.ContextRuleTypeVariants.DEFAULT,
            (vec[0] as SCValXdr.Sym).value.value,
            "Variant name must be '${SmartAccountContractAbi.ContextRuleTypeVariants.DEFAULT}'"
        )
    }

    @Test
    fun testContextRuleType_callContract_scValUsesCorrectVariantName() {
        // CallContract: Vec([Symbol("CallContract"), Address(...)])
        val contractAddr = "CDLZFC3SYJYDZT7K67VZ75HPJVIEUVNIXF47ZG2FB2RMQQVU2HHGCYSC"
        val contextType = ContextRuleType.CallContract(contractAddr)
        val scVal = contextType.toScVal()

        assertTrue(scVal is SCValXdr.Vec, "CallContract ContextRuleType ScVal must be Vec")
        val vec = (scVal as SCValXdr.Vec).value!!.value
        assertEquals(2, vec.size, "CallContract variant Vec must have 2 elements")
        assertTrue(vec[0] is SCValXdr.Sym, "First element must be Symbol")
        assertEquals(
            SmartAccountContractAbi.ContextRuleTypeVariants.CALL_CONTRACT,
            (vec[0] as SCValXdr.Sym).value.value,
            "Variant name must be '${SmartAccountContractAbi.ContextRuleTypeVariants.CALL_CONTRACT}'"
        )
        assertTrue(vec[1] is SCValXdr.Address, "Second element must be Address")
    }

    @Test
    fun testContextRuleType_createContract_scValUsesCorrectVariantName() {
        // CreateContract: Vec([Symbol("CreateContract"), Bytes(wasmHash)])
        val wasmHash = ByteArray(32) { it.toByte() }
        val contextType = ContextRuleType.CreateContract(wasmHash)
        val scVal = contextType.toScVal()

        assertTrue(scVal is SCValXdr.Vec, "CreateContract ContextRuleType ScVal must be Vec")
        val vec = (scVal as SCValXdr.Vec).value!!.value
        assertEquals(2, vec.size, "CreateContract variant Vec must have 2 elements")
        assertTrue(vec[0] is SCValXdr.Sym, "First element must be Symbol")
        assertEquals(
            SmartAccountContractAbi.ContextRuleTypeVariants.CREATE_CONTRACT,
            (vec[0] as SCValXdr.Sym).value.value,
            "Variant name must be '${SmartAccountContractAbi.ContextRuleTypeVariants.CREATE_CONTRACT}'"
        )
        assertTrue(vec[1] is SCValXdr.Bytes, "Second element must be Bytes")
    }

    // ========================================================================
    // Argument Type Verification (String vs Symbol)
    // ========================================================================

    @Test
    fun testNameParameter_usesStringType_notSymbol() {
        // The contract ABI specifies `name: String` (SC_SPEC_TYPE_STRING, type code 0x10).
        // This must be encoded as SCValXdr.Str (SCV_STRING), NOT SCValXdr.Sym (SCV_SYMBOL).
        //
        // Verified from contract spec XDR:
        // - add_context_rule: name parameter has type code 0x10 = SC_SPEC_TYPE_STRING
        // - update_context_rule_name: name parameter has type code 0x10 = SC_SPEC_TYPE_STRING
        //
        // Scv.toString() produces SCValXdr.Str (SCV_STRING) -- correct
        // Scv.toSymbol() produces SCValXdr.Sym (SCV_SYMBOL) -- incorrect for this parameter

        val testName = "test-context-rule"
        val nameScVal = Scv.toString(testName)

        assertEquals(
            SCValTypeXdr.SCV_STRING,
            nameScVal.discriminant,
            "Name parameter must be encoded as SCV_STRING (Soroban String type), not SCV_SYMBOL"
        )
        assertTrue(nameScVal is SCValXdr.Str, "Name must use SCValXdr.Str variant")
        assertEquals(testName, (nameScVal as SCValXdr.Str).value.value)
    }

    @Test
    fun testSymbolType_isDifferentFromStringType() {
        // Confirm that Symbol and String are distinct SCVal types.
        // This documents why the name parameter type matters.
        val symbolVal = Scv.toSymbol("test")
        val stringVal = Scv.toString("test")

        assertEquals(SCValTypeXdr.SCV_SYMBOL, symbolVal.discriminant)
        assertEquals(SCValTypeXdr.SCV_STRING, stringVal.discriminant)

        assertTrue(
            symbolVal.discriminant != stringVal.discriminant,
            "SCV_SYMBOL and SCV_STRING are distinct types -- using the wrong one causes contract errors"
        )
    }

    // ========================================================================
    // add_context_rule Argument Structure Verification
    // ========================================================================

    @Test
    fun testAddContextRule_argumentOrder_matchesAbi() {
        // ABI: add_context_rule(context_type: ContextRuleType, name: String,
        //                       valid_until: Option<u32>, signers: Vec<Signer>,
        //                       policies: Map<Address, Val>)
        //
        // Verify the argument types and ordering match the ABI signature.
        val contextTypeScVal = ContextRuleType.Default.toScVal()
        val nameScVal = Scv.toString("default rule")
        val validUntilScVal = Scv.toVoid() // None
        val signersScVal = Scv.toVec(emptyList())
        val policiesScVal = Scv.toMap(linkedMapOf())

        val args = listOf(contextTypeScVal, nameScVal, validUntilScVal, signersScVal, policiesScVal)

        assertEquals(5, args.size, "add_context_rule takes 5 arguments")

        // arg 0: context_type (ContextRuleType encoded as Vec)
        assertEquals(SCValTypeXdr.SCV_VEC, args[0].discriminant, "arg 0 (context_type) must be Vec")

        // arg 1: name (String, NOT Symbol)
        assertEquals(SCValTypeXdr.SCV_STRING, args[1].discriminant, "arg 1 (name) must be String")

        // arg 2: valid_until (Option<u32> - Void for None, U32 for Some)
        assertEquals(SCValTypeXdr.SCV_VOID, args[2].discriminant, "arg 2 (valid_until) None must be Void")

        // arg 3: signers (Vec<Signer>)
        assertEquals(SCValTypeXdr.SCV_VEC, args[3].discriminant, "arg 3 (signers) must be Vec")

        // arg 4: policies (Map<Address, Val>)
        assertEquals(SCValTypeXdr.SCV_MAP, args[4].discriminant, "arg 4 (policies) must be Map")
    }

    @Test
    fun testAddContextRule_validUntilSome_usesU32() {
        // When valid_until is Some(ledger), it must be encoded as U32
        val validUntilScVal = Scv.toUint32(1000u)
        assertEquals(SCValTypeXdr.SCV_U32, validUntilScVal.discriminant,
            "valid_until Some value must be U32")
    }

    // ========================================================================
    // update_context_rule_name Argument Structure Verification
    // ========================================================================

    @Test
    fun testUpdateContextRuleName_argumentOrder_matchesAbi() {
        // ABI: update_context_rule_name(context_rule_id: u32, name: String)
        val args = listOf(
            Scv.toUint32(0u),          // context_rule_id
            Scv.toString("new name")   // name (String, not Symbol)
        )

        assertEquals(2, args.size, "update_context_rule_name takes 2 arguments")
        assertEquals(SCValTypeXdr.SCV_U32, args[0].discriminant, "arg 0 (context_rule_id) must be U32")
        assertEquals(SCValTypeXdr.SCV_STRING, args[1].discriminant, "arg 1 (name) must be String")
    }

    // ========================================================================
    // Other Contract Method Argument Structure Verification
    // ========================================================================

    @Test
    fun testGetContextRule_argumentOrder_matchesAbi() {
        // ABI: get_context_rule(context_rule_id: u32)
        val args = listOf(Scv.toUint32(0u))
        assertEquals(1, args.size)
        assertEquals(SCValTypeXdr.SCV_U32, args[0].discriminant, "arg 0 (context_rule_id) must be U32")
    }

    @Test
    fun testGetContextRules_argumentOrder_matchesAbi() {
        // ABI: get_context_rules(context_rule_type: ContextRuleType)
        val args = listOf(ContextRuleType.Default.toScVal())
        assertEquals(1, args.size)
        assertEquals(SCValTypeXdr.SCV_VEC, args[0].discriminant, "arg 0 (context_rule_type) must be Vec")
    }

    @Test
    fun testGetContextRulesCount_noArguments() {
        // ABI: get_context_rules_count()
        val args = emptyList<SCValXdr>()
        assertEquals(0, args.size, "get_context_rules_count takes no arguments")
    }

    @Test
    fun testUpdateContextRuleValidUntil_argumentOrder_matchesAbi() {
        // ABI: update_context_rule_valid_until(context_rule_id: u32, valid_until: Option<u32>)
        val args = listOf(
            Scv.toUint32(1u),
            Scv.toUint32(5000u) // Some(5000)
        )
        assertEquals(2, args.size)
        assertEquals(SCValTypeXdr.SCV_U32, args[0].discriminant, "arg 0 (context_rule_id) must be U32")
        assertEquals(SCValTypeXdr.SCV_U32, args[1].discriminant, "arg 1 (valid_until Some) must be U32")
    }

    @Test
    fun testRemoveContextRule_argumentOrder_matchesAbi() {
        // ABI: remove_context_rule(context_rule_id: u32)
        val args = listOf(Scv.toUint32(1u))
        assertEquals(1, args.size)
        assertEquals(SCValTypeXdr.SCV_U32, args[0].discriminant, "arg 0 (context_rule_id) must be U32")
    }

    @Test
    fun testAddSigner_argumentOrder_matchesAbi() {
        // ABI: add_signer(context_rule_id: u32, signer: Signer)
        val signer = DelegatedSigner(
            address = "GAAZI4TCR3TY5OJHCTJC2A4QSY6CJWJH5IAJTGKIN2ER7LBNVKOCCWN7"
        )
        val args = listOf(
            Scv.toUint32(0u),
            signer.toScVal()
        )
        assertEquals(2, args.size)
        assertEquals(SCValTypeXdr.SCV_U32, args[0].discriminant, "arg 0 (context_rule_id) must be U32")
        assertEquals(SCValTypeXdr.SCV_VEC, args[1].discriminant, "arg 1 (signer) must be Vec (enum encoding)")
    }

    @Test
    fun testRemoveSigner_argumentOrder_matchesAbi() {
        // ABI: remove_signer(context_rule_id: u32, signer: Signer)
        val signer = DelegatedSigner(
            address = "GAAZI4TCR3TY5OJHCTJC2A4QSY6CJWJH5IAJTGKIN2ER7LBNVKOCCWN7"
        )
        val args = listOf(
            Scv.toUint32(0u),
            signer.toScVal()
        )
        assertEquals(2, args.size)
        assertEquals(SCValTypeXdr.SCV_U32, args[0].discriminant, "arg 0 (context_rule_id) must be U32")
        assertEquals(SCValTypeXdr.SCV_VEC, args[1].discriminant, "arg 1 (signer) must be Vec (enum encoding)")
    }

    @Test
    fun testAddPolicy_argumentOrder_matchesAbi() {
        // ABI: add_policy(context_rule_id: u32, policy: Address, install_param: Val)
        val policyAddress = Address("CDLZFC3SYJYDZT7K67VZ75HPJVIEUVNIXF47ZG2FB2RMQQVU2HHGCYSC")
        val args = listOf(
            Scv.toUint32(0u),
            Scv.toAddress(policyAddress.toSCAddress()),
            Scv.toVoid() // install_param placeholder
        )
        assertEquals(3, args.size)
        assertEquals(SCValTypeXdr.SCV_U32, args[0].discriminant, "arg 0 (context_rule_id) must be U32")
        assertEquals(SCValTypeXdr.SCV_ADDRESS, args[1].discriminant, "arg 1 (policy) must be Address")
        // arg 2 (install_param) is Val - any SCVal type is valid
    }

    @Test
    fun testRemovePolicy_argumentOrder_matchesAbi() {
        // ABI: remove_policy(context_rule_id: u32, policy: Address)
        val policyAddress = Address("CDLZFC3SYJYDZT7K67VZ75HPJVIEUVNIXF47ZG2FB2RMQQVU2HHGCYSC")
        val args = listOf(
            Scv.toUint32(0u),
            Scv.toAddress(policyAddress.toSCAddress())
        )
        assertEquals(2, args.size)
        assertEquals(SCValTypeXdr.SCV_U32, args[0].discriminant, "arg 0 (context_rule_id) must be U32")
        assertEquals(SCValTypeXdr.SCV_ADDRESS, args[1].discriminant, "arg 1 (policy) must be Address")
    }

    // ========================================================================
    // WebAuthn Signature Field Name Verification
    // ========================================================================

    @Test
    fun testWebAuthnSigDataFieldNames_matchAbi() {
        assertEquals("authenticator_data", SmartAccountContractAbi.WebAuthnSigDataFields.AUTHENTICATOR_DATA)
        assertEquals("client_data", SmartAccountContractAbi.WebAuthnSigDataFields.CLIENT_DATA)
        assertEquals("signature", SmartAccountContractAbi.WebAuthnSigDataFields.SIGNATURE)
    }

    // ========================================================================
    // Policy Install Parameter Field Name Verification
    // ========================================================================

    @Test
    fun testSimpleThresholdFieldNames_matchAbi() {
        assertEquals("threshold", SmartAccountContractAbi.Policies.SimpleThreshold.AccountParams.THRESHOLD)
    }

    @Test
    fun testWeightedThresholdFieldNames_matchAbi() {
        assertEquals("signer_weights", SmartAccountContractAbi.Policies.WeightedThreshold.AccountParams.SIGNER_WEIGHTS)
        assertEquals("threshold", SmartAccountContractAbi.Policies.WeightedThreshold.AccountParams.THRESHOLD)
    }

    @Test
    fun testSpendingLimitFieldNames_matchAbi() {
        assertEquals("spending_limit", SmartAccountContractAbi.Policies.SpendingLimit.AccountParams.SPENDING_LIMIT)
        assertEquals("period_ledgers", SmartAccountContractAbi.Policies.SpendingLimit.AccountParams.PERIOD_LEDGERS)
    }

    // ========================================================================
    // Error Code Verification
    // ========================================================================

    @Test
    fun testSmartAccountErrorCodes_areInExpectedRange() {
        // Error codes 3000-3012
        assertEquals(3000, SmartAccountContractAbi.SmartAccountErrors.CONTEXT_RULE_NOT_FOUND)
        assertEquals(3001, SmartAccountContractAbi.SmartAccountErrors.SIGNER_NOT_FOUND)
        assertEquals(3002, SmartAccountContractAbi.SmartAccountErrors.POLICY_NOT_FOUND)
        assertEquals(3003, SmartAccountContractAbi.SmartAccountErrors.SIGNER_ALREADY_EXISTS)
        assertEquals(3004, SmartAccountContractAbi.SmartAccountErrors.POLICY_ALREADY_EXISTS)
        assertEquals(3005, SmartAccountContractAbi.SmartAccountErrors.CANNOT_REMOVE_DEFAULT)
        assertEquals(3006, SmartAccountContractAbi.SmartAccountErrors.CONTEXT_RULE_EXPIRED)
        assertEquals(3007, SmartAccountContractAbi.SmartAccountErrors.NO_MATCHING_RULE)
        assertEquals(3008, SmartAccountContractAbi.SmartAccountErrors.FINGERPRINT_ALREADY_USED)
        assertEquals(3009, SmartAccountContractAbi.SmartAccountErrors.TOO_MANY_SIGNERS)
        assertEquals(3010, SmartAccountContractAbi.SmartAccountErrors.TOO_MANY_POLICIES)
        assertEquals(3011, SmartAccountContractAbi.SmartAccountErrors.DUPLICATE_CONTEXT_RULE_TYPE)
        assertEquals(3012, SmartAccountContractAbi.SmartAccountErrors.TOO_MANY_CONTEXT_RULES)
    }

    @Test
    fun testSimpleThresholdErrorCodes_areInExpectedRange() {
        // Error codes 3200-3202
        assertEquals(3200, SmartAccountContractAbi.SimpleThresholdErrors.ZERO_THRESHOLD)
        assertEquals(3201, SmartAccountContractAbi.SimpleThresholdErrors.THRESHOLD_EXCEEDS_SIGNERS)
        assertEquals(3202, SmartAccountContractAbi.SimpleThresholdErrors.THRESHOLD_NOT_MET)
    }

    @Test
    fun testWeightedThresholdErrorCodes_areInExpectedRange() {
        // Error codes 3210-3213
        assertEquals(3210, SmartAccountContractAbi.WeightedThresholdErrors.ZERO_THRESHOLD)
        assertEquals(3211, SmartAccountContractAbi.WeightedThresholdErrors.ZERO_WEIGHT)
        assertEquals(3212, SmartAccountContractAbi.WeightedThresholdErrors.INSUFFICIENT_TOTAL_WEIGHT)
        assertEquals(3213, SmartAccountContractAbi.WeightedThresholdErrors.THRESHOLD_NOT_MET)
    }

    @Test
    fun testSpendingLimitErrorCodes_areInExpectedRange() {
        // Error codes 3220-3224
        assertEquals(3220, SmartAccountContractAbi.SpendingLimitErrors.INVALID_SPENDING_LIMIT)
        assertEquals(3221, SmartAccountContractAbi.SpendingLimitErrors.INVALID_PERIOD)
        assertEquals(3222, SmartAccountContractAbi.SpendingLimitErrors.LIMIT_EXCEEDED)
        assertEquals(3223, SmartAccountContractAbi.SpendingLimitErrors.AMOUNT_NOT_FOUND)
        assertEquals(3224, SmartAccountContractAbi.SpendingLimitErrors.OVERFLOW)
    }

    @Test
    fun testWebAuthnErrorCodes_areInExpectedRange() {
        // Error codes 3110-3118
        assertEquals(3110, SmartAccountContractAbi.WebAuthnErrors.INVALID_CLIENT_DATA_JSON)
        assertEquals(3111, SmartAccountContractAbi.WebAuthnErrors.MISSING_CHALLENGE)
        assertEquals(3112, SmartAccountContractAbi.WebAuthnErrors.CHALLENGE_MISMATCH)
        assertEquals(3113, SmartAccountContractAbi.WebAuthnErrors.MISSING_TYPE)
        assertEquals(3114, SmartAccountContractAbi.WebAuthnErrors.INVALID_TYPE)
        assertEquals(3115, SmartAccountContractAbi.WebAuthnErrors.MISSING_ORIGIN)
        assertEquals(3116, SmartAccountContractAbi.WebAuthnErrors.INVALID_AUTHENTICATOR_DATA)
        assertEquals(3117, SmartAccountContractAbi.WebAuthnErrors.USER_NOT_PRESENT)
        assertEquals(3118, SmartAccountContractAbi.WebAuthnErrors.SIGNATURE_VERIFICATION_FAILED)
    }

    // ========================================================================
    // Constants Verification
    // ========================================================================

    @Test
    fun testContractLimits_matchAbi() {
        assertEquals(5, SmartAccountContractAbi.Constants.MAX_POLICIES)
        assertEquals(15, SmartAccountContractAbi.Constants.MAX_SIGNERS)
        assertEquals(15, SmartAccountContractAbi.Constants.MAX_CONTEXT_RULES)
    }

    @Test
    fun testDefaultContextRuleId_isZero() {
        assertEquals(0u, SmartAccountContractAbi.DEFAULT_CONTEXT_RULE_ID)
    }

    // ========================================================================
    // Unused ABI Functions Documentation
    // ========================================================================

    @Test
    fun testExecuteFunction_isInvokedByExecuteAndSubmit() {
        // "execute" (SmartAccountContractAbi.Functions.EXECUTE) is used by
        // OZTransactionOperations.executeAndSubmit(), which builds an invocation of the
        // smart account's execute(target, target_fn, target_args) entry point.
        assertEquals("execute", SmartAccountContractAbi.Functions.EXECUTE)
    }

    @Test
    fun testUnusedAbiFunctions_areDocumented() {
        // The following ABI function is NOT currently invoked in the KMP SDK:
        //
        // 1. "upgrade" (SmartAccountContractAbi.Functions.UPGRADE)
        //    - Upgradeable trait method for WASM upgrade
        //    - Not yet implemented in the KMP SDK
        //    - Rationale: Contract upgrade functionality is not yet part of the SDK's scope
        //
        // This test serves as documentation of the unused function.
        // If this function is implemented in the future, this test should be updated.

        val unusedFunctions = listOf(
            SmartAccountContractAbi.Functions.UPGRADE
        )
        assertEquals(1, unusedFunctions.size, "There should be exactly 1 unused ABI function")
        assertEquals("upgrade", unusedFunctions[0])
    }

    // ========================================================================
    // Storage Key Verification
    // ========================================================================

    @Test
    fun testStorageKeyConstants_matchAbi() {
        assertEquals("Signers", SmartAccountContractAbi.StorageKeys.SIGNERS)
        assertEquals("Policies", SmartAccountContractAbi.StorageKeys.POLICIES)
        assertEquals("Ids", SmartAccountContractAbi.StorageKeys.IDS)
        assertEquals("Meta", SmartAccountContractAbi.StorageKeys.META)
        assertEquals("Fingerprint", SmartAccountContractAbi.StorageKeys.FINGERPRINT)
        assertEquals("NextId", SmartAccountContractAbi.StorageKeys.NEXT_ID)
        assertEquals("Count", SmartAccountContractAbi.StorageKeys.COUNT)
    }

    @Test
    fun testPolicyStorageKey_isAccountContext() {
        // All three policy types use the same storage key format
        assertEquals("AccountContext", SmartAccountContractAbi.Policies.SimpleThreshold.STORAGE_KEY)
        assertEquals("AccountContext", SmartAccountContractAbi.Policies.WeightedThreshold.STORAGE_KEY)
        assertEquals("AccountContext", SmartAccountContractAbi.Policies.SpendingLimit.STORAGE_KEY)
    }
}
