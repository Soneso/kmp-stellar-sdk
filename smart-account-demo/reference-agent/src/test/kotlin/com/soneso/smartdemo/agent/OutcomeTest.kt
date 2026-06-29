package com.soneso.smartdemo.agent

import com.soneso.stellar.sdk.smartaccount.core.ContractErrorCodes
import com.soneso.stellar.sdk.smartaccount.core.TransactionException
import com.soneso.stellar.sdk.smartaccount.oz.TransactionResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OutcomeTest {

    // MARK: - ContractErrorClassifier

    @Test
    fun parsesTheContractErrorCodeFromAHostErrorMessage() {
        assertEquals(3016, ContractErrorClassifier.parseContractErrorCode("HostError: Error(Contract, #3016)"))
    }

    @Test
    fun returnsNullWhenNoContractCodeIsPresent() {
        assertNull(ContractErrorClassifier.parseContractErrorCode("network unreachable"))
    }

    @Test
    fun mapsKnownContractErrorCodesToNames() {
        assertEquals("unauthorizedSigner", ContractErrorClassifier.nameForCode(ContractErrorCodes.UNAUTHORIZED_SIGNER))
        assertEquals("keyDataTooLarge", ContractErrorClassifier.nameForCode(3013))
        assertNull(ContractErrorClassifier.nameForCode(9999))
    }

    @Test
    fun mapsPolicyDenialCodesToNames() {
        assertEquals("spendingLimitExceeded", ContractErrorClassifier.nameForCode(3221))
        assertEquals("spendingLimitNotAllowed", ContractErrorClassifier.nameForCode(3223))
        assertEquals("simpleThresholdNotAllowed", ContractErrorClassifier.nameForCode(3202))
        assertEquals("weightedThresholdNotAllowed", ContractErrorClassifier.nameForCode(3213))
    }

    @Test
    fun isEscalatableOnlyForPolicyDenialCodes() {
        assertTrue(ContractErrorClassifier.isEscalatable(3221))
        assertTrue(ContractErrorClassifier.isEscalatable(3202))
        assertTrue(ContractErrorClassifier.isEscalatable(3213))
        assertTrue(ContractErrorClassifier.isEscalatable(3223))
        // A credential fault, a policy-installation fault, and an unknown code are
        // not policy denials and must not be escalatable.
        assertFalse(ContractErrorClassifier.isEscalatable(ContractErrorCodes.UNAUTHORIZED_SIGNER))
        assertFalse(ContractErrorClassifier.isEscalatable(3220))
        assertFalse(ContractErrorClassifier.isEscalatable(9999))
    }

    // MARK: - classifyResult

    @Test
    fun successYieldsSucceededWithTheHash() {
        val outcome = classifyResult(TransactionResult(success = true, hash = "HASH"))
        assertEquals(CallOutcome.Succeeded("HASH"), outcome)
    }

    @Test
    fun aPolicyDenialCodeYieldsRejected() {
        // 3221 is the spending-limit policy's "limit exceeded" denial: a policy
        // decision the agent escalates for human review.
        val outcome = classifyResult(TransactionResult(success = false, error = "HostError: Error(Contract, #3221)"))
        assertTrue(outcome is CallOutcome.Rejected)
        assertEquals(3221, outcome.errorCode)
        assertEquals("spendingLimitExceeded", outcome.errorName)
        assertTrue(outcome.rawMessage.contains("#3221"))
    }

    @Test
    fun aNonPolicyContractCodeYieldsFailed() {
        // 3016 (UNAUTHORIZED_SIGNER) is a contract error code but a credential
        // fault, not a policy denial, so it is a non-escalatable failure.
        val outcome = classifyResult(TransactionResult(success = false, error = "Error(Contract, #3016)"))
        assertTrue(outcome is CallOutcome.Failed)
        assertTrue(outcome.message.contains("#3016"))
    }

    @Test
    fun nonContractFailureYieldsFailed() {
        val outcome = classifyResult(TransactionResult(success = false, error = "timeout"))
        assertEquals(CallOutcome.Failed("timeout"), outcome)
    }

    @Test
    fun anUnknownContractCodeYieldsFailed() {
        // A parseable but non-allowlisted contract code is not a policy decision.
        val outcome = classifyResult(TransactionResult(success = false, error = "Error(Contract, #4242)"))
        assertTrue(outcome is CallOutcome.Failed)
        assertTrue(outcome.message.contains("#4242"))
    }

    // MARK: - classifyError

    @Test
    fun smartAccountExceptionWithAPolicyCodeYieldsRejected() {
        val outcome = classifyError(TransactionException.simulationFailed("Error(Contract, #3221)"))
        assertTrue(outcome is CallOutcome.Rejected)
        assertEquals(3221, outcome.errorCode)
    }

    @Test
    fun smartAccountExceptionWithANonPolicyCodeYieldsFailed() {
        val outcome = classifyError(TransactionException.simulationFailed("Error(Contract, #3016)"))
        assertTrue(outcome is CallOutcome.Failed)
        assertTrue(outcome.message.contains("#3016"))
    }

    @Test
    fun genericErrorWithoutACodeYieldsFailed() {
        val outcome = classifyError(RuntimeException("boom"))
        assertTrue(outcome is CallOutcome.Failed)
        assertTrue(outcome.message.contains("boom"))
    }
}
