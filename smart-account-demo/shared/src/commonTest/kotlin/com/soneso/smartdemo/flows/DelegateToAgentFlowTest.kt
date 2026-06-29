package com.soneso.smartdemo.flows

import com.ionspin.kotlin.bignum.integer.BigInteger
import com.soneso.smartdemo.config.DemoConfig
import com.soneso.smartdemo.util.hexToByteArray
import com.soneso.stellar.sdk.StrKey
import com.soneso.stellar.sdk.scval.Scv
import com.soneso.stellar.sdk.smartaccount.core.ExternalSigner
import com.soneso.stellar.sdk.smartaccount.core.SmartAccountSigner
import com.soneso.stellar.sdk.smartaccount.oz.ContextRuleType
import com.soneso.stellar.sdk.xdr.SCValXdr
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Unit tests for the delegate-to-agent flow.
 *
 * Covers the pure validation surface (agent Ed25519 public-key format checks, the strict
 * spending-limit amount grammar that must agree with the on-chain base-units encoding, and
 * the configured spending-limit policy address) and the [delegateToAgent] composition itself.
 *
 * The composition tests inject the [delegateToAgent] submission, ledger, and decimals seams so
 * the rule the flow hands to the SDK can be inspected without a chain or passkey ceremony. They
 * assert that the flow builds the fail-CLOSED rule: a `CallContract(token)` scope, a single
 * Ed25519 external signer carrying the 32-byte agent key under the configured verifier, a
 * spending-limit policy with the cap pre-encoded at the token scale, and
 * `validUntil = currentLedger + offset` (with no ledger read when the offset is zero and no
 * submission when the cap or key is invalid).
 */
class DelegateToAgentFlowTest {

    private val validKey = "a".repeat(64)

    /**
     * A demo token contract used as the [ContextRuleType.CallContract] scope. The composition
     * tests inject the decimal scale through the [delegateToAgent] decimals seam, so the rule
     * is composed at a deterministic precision without resolving the token's decimals() over RPC.
     */
    private val tokenContract = DemoConfig.NATIVE_TOKEN_CONTRACT

    private val agentKeyHex = "ab".repeat(32)

    private val agentKeyBytes = hexToByteArray(agentKeyHex)

    private val ledgersPerDay: UInt = 17_280u

    /** Records the rule the flow submits through the injected submission seam. */
    private class CapturedRule(
        val contextType: ContextRuleType,
        val name: String,
        val validUntil: UInt?,
        val signers: List<SmartAccountSigner>,
        val policies: List<FlowPolicyEntry>,
    )

    /**
     * Drives [delegateToAgent] with injected seams. The submission seam captures the composed
     * rule and returns [submitResult]; the ledger seam returns [currentLedger] + offset and
     * counts its calls so the zero-offset path can be asserted to skip the read.
     */
    private class Harness(
        val currentLedger: UInt = 50_000u,
        var submitResult: ContextRuleResult = ContextRuleResult(true, "delegationhash", null),
    ) {
        var captured: CapturedRule? = null
        var submitCount = 0
        var ledgerReadCount = 0

        suspend fun run(
            agentPublicKey: String,
            tokenContract: String,
            amount: String,
            periodLedgers: UInt,
            validUntilOffsetLedgers: UInt,
            tokenDecimals: Int,
        ): DelegationResult = delegateToAgent(
            agentPublicKey = agentPublicKey,
            tokenContract = tokenContract,
            amount = amount,
            periodLedgers = periodLedgers,
            validUntilOffsetLedgers = validUntilOffsetLedgers,
            submitContextRule = { contextType, name, validUntil, signers, policies ->
                submitCount++
                captured = CapturedRule(contextType, name, validUntil, signers, policies)
                submitResult
            },
            resolveValidUntilLedger = { offset ->
                ledgerReadCount++
                currentLedger + offset
            },
            resolveTokenDecimals = { tokenDecimals },
        )
    }

    /** Decodes the `spending_limit` (I128) field from a spending-limit policy SCVal map. */
    private fun spendingLimitBaseUnits(scVal: SCValXdr): BigInteger {
        for ((key, value) in Scv.fromMap(scVal)) {
            if (Scv.fromSymbol(key) == "spending_limit") return Scv.fromInt128(value)
        }
        fail("spending-limit policy SCVal has no spending_limit field")
    }

    /** Decodes the `period_ledgers` (U32) field from a spending-limit policy SCVal map. */
    private fun spendingLimitPeriodLedgers(scVal: SCValXdr): UInt {
        for ((key, value) in Scv.fromMap(scVal)) {
            if (Scv.fromSymbol(key) == "period_ledgers") return Scv.fromUint32(value)
        }
        fail("spending-limit policy SCVal has no period_ledgers field")
    }

    @Test
    fun emptyAgentKeyIsNotFlagged() {
        assertNull(validateAgentPublicKey(""))
        assertNull(validateAgentPublicKey("   "))
    }

    @Test
    fun agentKeyMustBe64HexCharacters() {
        assertEquals("Must be 64 hex characters (32 bytes), got 8", validateAgentPublicKey("deadbeef"))
        assertNull(validateAgentPublicKey(validKey))
        assertNull(validateAgentPublicKey(validKey.uppercase()))
    }

    @Test
    fun agentKeyRejectsNonHexCharacters() {
        val withG = "g".repeat(64)
        assertEquals("Invalid hex characters", validateAgentPublicKey(withG))
    }

    @Test
    fun emptyAmountIsNotFlagged() {
        assertNull(validateDelegationAmount(""))
    }

    @Test
    fun amountRejectsScientificNotation() {
        assertEquals("Scientific notation is not supported", validateDelegationAmount("1e3"))
    }

    @Test
    fun amountRejectsCommaDecimalSeparator() {
        assertEquals("Use a dot for the decimal separator", validateDelegationAmount("1,5"))
    }

    @Test
    fun amountRejectsMultipleDots() {
        assertEquals("Must be a valid number", validateDelegationAmount("1.2.3"))
    }

    @Test
    fun amountRejectsNonNumeric() {
        assertEquals("Must be a valid number", validateDelegationAmount("abc"))
    }

    @Test
    fun amountRejectsZeroAndNegative() {
        assertEquals("Must be greater than zero", validateDelegationAmount("0"))
        assertEquals("Must be greater than zero", validateDelegationAmount("-5"))
    }

    @Test
    fun amountRejectsNonFiniteAndHexFloatLiterals() {
        // toDoubleOrNull parses these, but none is a finite decimal cap the on-chain
        // amount grammar would accept, so the validator must reject them.
        assertEquals("Must be a valid number", validateDelegationAmount("NaN"))
        assertEquals("Must be a valid number", validateDelegationAmount("Infinity"))
        assertEquals("Must be a valid number", validateDelegationAmount("-Infinity"))
        assertEquals("Must be a valid number", validateDelegationAmount("0x1p4"))
        assertEquals("Must be a valid number", validateDelegationAmount("0x1.8p3"))
        // A grammatically-decimal value too large to represent overflows to Infinity on parse.
        assertEquals("Must be a valid number", validateDelegationAmount("9".repeat(400)))
    }

    @Test
    fun amountAcceptsPositiveDecimal() {
        assertNull(validateDelegationAmount("100"))
        assertNull(validateDelegationAmount("0.5"))
        assertNull(validateDelegationAmount("10.1234567"))
    }

    @Test
    fun spendingLimitPolicyAddressIsConfiguredAndValid() {
        assertTrue(spendingLimitPolicyAddress.isNotEmpty())
        assertTrue(StrKey.isValidContract(spendingLimitPolicyAddress))
    }

    @Test
    fun delegateComposesCallContractScopeEd25519SignerSpendingLimitAndValidUntil() = runTest {
        val harness = Harness(currentLedger = 50_000u)

        val result = harness.run(
            agentPublicKey = agentKeyHex,
            tokenContract = tokenContract,
            amount = "100",
            periodLedgers = ledgersPerDay,
            validUntilOffsetLedgers = ledgersPerDay,
            tokenDecimals = DemoConfig.DEMO_TOKEN_DECIMALS,
        )

        assertTrue(result.success)
        assertEquals("delegationhash", result.hash)
        assertEquals(1, harness.submitCount)

        val rule = harness.captured ?: fail("submission seam was not invoked")

        // 1. CallContract(token) scope — the rule matches only the one scoped token contract.
        val contextType = rule.contextType
        assertTrue(contextType is ContextRuleType.CallContract)
        assertEquals(tokenContract, contextType.contractAddress)

        // 2. A single Ed25519 external signer carrying the 32-byte agent key under the verifier.
        assertEquals(1, rule.signers.size)
        val signer = rule.signers.single()
        assertTrue(signer is ExternalSigner)
        assertEquals(DemoConfig.ED25519_VERIFIER_ADDRESS, signer.verifierAddress)
        assertEquals(32, signer.keyData.size)
        assertTrue(signer.keyData.contentEquals(agentKeyBytes))

        // 3. A spending-limit policy with the cap pre-encoded at the token scale.
        assertEquals(1, rule.policies.size)
        val policy = rule.policies.single()
        assertEquals(spendingLimitPolicyAddress, policy.address)
        val policyScVal = policy.scVal ?: fail("spending-limit policy has no SCVal params")
        // 100 * 10^7 = 1_000_000_000 base units at 7 decimals.
        assertEquals(BigInteger.fromLong(1_000_000_000L), spendingLimitBaseUnits(policyScVal))
        assertEquals(ledgersPerDay, spendingLimitPeriodLedgers(policyScVal))

        // 4. validUntil = currentLedger + offset, resolved from a single ledger read.
        assertEquals(50_000u + ledgersPerDay, rule.validUntil)
        assertEquals(1, harness.ledgerReadCount)
        assertEquals(DEFAULT_DELEGATION_RULE_NAME, rule.name)
    }

    @Test
    fun delegateSummaryCapturesTheAuthorisedRule() = runTest {
        val harness = Harness(currentLedger = 12_345u)

        val result = harness.run(
            agentPublicKey = agentKeyHex,
            tokenContract = tokenContract,
            amount = "50",
            periodLedgers = 720u,
            validUntilOffsetLedgers = ledgersPerDay,
            tokenDecimals = DemoConfig.DEMO_TOKEN_DECIMALS,
        )

        assertTrue(result.success)
        val summary = result.summary ?: fail("successful delegation has no summary")
        assertEquals(agentKeyHex, summary.agentPublicKey)
        assertEquals(tokenContract, summary.tokenContract)
        assertEquals("50", summary.amount)
        assertEquals(720u, summary.periodLedgers)
        assertEquals(12_345u + ledgersPerDay, summary.validUntilLedger)
        assertEquals(DEFAULT_DELEGATION_RULE_NAME, summary.ruleName)
        assertEquals(spendingLimitPolicyAddress, summary.spendingLimitPolicyAddress)
        assertEquals(DemoConfig.ED25519_VERIFIER_ADDRESS, summary.verifierAddress)
    }

    @Test
    fun delegateConvertsFractionalCapAtTheTokenScale() = runTest {
        val harness = Harness()

        harness.run(
            agentPublicKey = agentKeyHex,
            tokenContract = tokenContract,
            amount = "1.5",
            periodLedgers = ledgersPerDay,
            validUntilOffsetLedgers = ledgersPerDay,
            tokenDecimals = DemoConfig.DEMO_TOKEN_DECIMALS,
        )

        val policyScVal = harness.captured!!.policies.single().scVal!!
        // 1.5 * 10^7 = 15_000_000 base units.
        assertEquals(BigInteger.fromLong(15_000_000L), spendingLimitBaseUnits(policyScVal))
    }

    @Test
    fun delegateWithZeroExpiryOffsetProducesNoValidUntilAndNoLedgerRead() = runTest {
        val harness = Harness()

        val result = harness.run(
            agentPublicKey = agentKeyHex,
            tokenContract = tokenContract,
            amount = "10",
            periodLedgers = ledgersPerDay,
            validUntilOffsetLedgers = 0u,
            tokenDecimals = DemoConfig.DEMO_TOKEN_DECIMALS,
        )

        assertTrue(result.success)
        assertEquals(1, harness.submitCount)
        assertNull(harness.captured!!.validUntil)
        assertEquals(0, harness.ledgerReadCount)
    }

    @Test
    fun delegateRejectsMalformedAgentKeyWithoutSubmitting() = runTest {
        val harness = Harness()

        val result = harness.run(
            agentPublicKey = "not-a-valid-key",
            tokenContract = tokenContract,
            amount = "10",
            periodLedgers = ledgersPerDay,
            validUntilOffsetLedgers = ledgersPerDay,
            tokenDecimals = DemoConfig.DEMO_TOKEN_DECIMALS,
        )

        assertTrue(!result.success)
        assertTrue(result.error!!.contains("hex"))
        assertEquals(0, harness.submitCount)
        assertEquals(0, harness.ledgerReadCount)
    }

    @Test
    fun delegateFailsClosedOnAnInvalidCapBeforeSubmitting() = runTest {
        val harness = Harness()

        val result = harness.run(
            agentPublicKey = agentKeyHex,
            tokenContract = tokenContract,
            amount = "0",
            periodLedgers = ledgersPerDay,
            validUntilOffsetLedgers = ledgersPerDay,
            tokenDecimals = DemoConfig.DEMO_TOKEN_DECIMALS,
        )

        assertTrue(!result.success)
        assertEquals(0, harness.submitCount)
        assertEquals(0, harness.ledgerReadCount)
    }

    @Test
    fun delegateFailsClosedWhenTheCapExceedsTheTokenPrecision() = runTest {
        val harness = Harness()

        // Eight fractional digits cannot be represented at the 7-decimal token scale; the
        // pre-encode must reject it before any submission rather than silently truncating.
        val result = harness.run(
            agentPublicKey = agentKeyHex,
            tokenContract = tokenContract,
            amount = "1.23456789",
            periodLedgers = ledgersPerDay,
            validUntilOffsetLedgers = ledgersPerDay,
            tokenDecimals = DemoConfig.DEMO_TOKEN_DECIMALS,
        )

        assertTrue(!result.success)
        assertEquals(0, harness.submitCount)
    }

    @Test
    fun delegateFailsClosedWhenTokenDecimalsCannotBeResolved() = runTest {
        var submitCount = 0

        // A non-native custom token whose decimals() cannot be read must abort the delegation
        // before submitting, rather than encoding the cap at a guessed scale.
        val result = delegateToAgent(
            agentPublicKey = agentKeyHex,
            tokenContract = tokenContract,
            amount = "10",
            periodLedgers = ledgersPerDay,
            validUntilOffsetLedgers = ledgersPerDay,
            submitContextRule = { _, _, _, _, _ ->
                submitCount++
                ContextRuleResult(true, "unexpected", null)
            },
            resolveValidUntilLedger = { offset -> offset },
            resolveTokenDecimals = { throw IllegalStateException("rpc down") },
        )

        assertTrue(!result.success)
        assertEquals(0, submitCount)
        assertTrue(result.error!!.contains("decimal precision"))
    }

    @Test
    fun delegateSurfacesAnOnChainFailureAsASanitisedError() = runTest {
        val harness = Harness(
            submitResult = ContextRuleResult(false, null, "simulation rejected the rule"),
        )

        val result = harness.run(
            agentPublicKey = agentKeyHex,
            tokenContract = tokenContract,
            amount = "10",
            periodLedgers = ledgersPerDay,
            validUntilOffsetLedgers = ledgersPerDay,
            tokenDecimals = DemoConfig.DEMO_TOKEN_DECIMALS,
        )

        assertTrue(!result.success)
        assertEquals(1, harness.submitCount)
        assertTrue(result.error!!.contains("simulation rejected the rule"))
        assertNull(result.summary)
    }

    @Test
    fun delegatePropagatesAnExpiryResolutionFailureWithoutSubmitting() = runTest {
        var submitCount = 0

        val result = delegateToAgent(
            agentPublicKey = agentKeyHex,
            tokenContract = tokenContract,
            amount = "10",
            periodLedgers = ledgersPerDay,
            validUntilOffsetLedgers = ledgersPerDay,
            submitContextRule = { _, _, _, _, _ ->
                submitCount++
                ContextRuleResult(true, "unexpected", null)
            },
            resolveValidUntilLedger = { throw IllegalStateException("rpc down") },
            resolveTokenDecimals = { DemoConfig.DEMO_TOKEN_DECIMALS },
        )

        assertTrue(!result.success)
        assertEquals(0, submitCount)
        assertTrue(result.error!!.contains("expiry"))
    }

    @Test
    fun delegateRejectsAConcurrentSecondSubmission() = runTest {
        val firstReachedSubmit = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()

        // The first delegation parks inside the submission seam while holding the in-flight
        // guard, so a second call issued meanwhile must be rejected without submitting.
        val first = async {
            delegateToAgent(
                agentPublicKey = agentKeyHex,
                tokenContract = tokenContract,
                amount = "10",
                periodLedgers = ledgersPerDay,
                validUntilOffsetLedgers = 0u,
                submitContextRule = { _, _, _, _, _ ->
                    firstReachedSubmit.complete(Unit)
                    releaseFirst.await()
                    ContextRuleResult(true, "firsthash", null)
                },
                resolveValidUntilLedger = { offset -> offset },
                resolveTokenDecimals = { DemoConfig.DEMO_TOKEN_DECIMALS },
            )
        }

        firstReachedSubmit.await()

        var secondSubmitCount = 0
        val second = delegateToAgent(
            agentPublicKey = agentKeyHex,
            tokenContract = tokenContract,
            amount = "10",
            periodLedgers = ledgersPerDay,
            validUntilOffsetLedgers = 0u,
            submitContextRule = { _, _, _, _, _ ->
                secondSubmitCount++
                ContextRuleResult(true, "secondhash", null)
            },
            resolveValidUntilLedger = { offset -> offset },
            resolveTokenDecimals = { DemoConfig.DEMO_TOKEN_DECIMALS },
        )

        assertTrue(!second.success)
        assertTrue(second.error!!.contains("already in progress"))
        assertEquals(0, secondSubmitCount)

        // Release the first call; once it completes and frees the guard a later call succeeds.
        releaseFirst.complete(Unit)
        val firstResult = first.await()
        assertTrue(firstResult.success)
        assertEquals("firsthash", firstResult.hash)

        val third = delegateToAgent(
            agentPublicKey = agentKeyHex,
            tokenContract = tokenContract,
            amount = "10",
            periodLedgers = ledgersPerDay,
            validUntilOffsetLedgers = 0u,
            submitContextRule = { _, _, _, _, _ -> ContextRuleResult(true, "thirdhash", null) },
            resolveValidUntilLedger = { offset -> offset },
            resolveTokenDecimals = { DemoConfig.DEMO_TOKEN_DECIMALS },
        )
        assertTrue(third.success)
        assertEquals("thirdhash", third.hash)
    }
}
