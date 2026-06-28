package com.soneso.smartdemo.agent

import com.soneso.stellar.sdk.smartaccount.core.ContractErrorCodes
import com.soneso.stellar.sdk.smartaccount.core.TransactionException
import com.soneso.stellar.sdk.smartaccount.oz.TransactionResult
import kotlin.test.Test
import kotlin.test.assertEquals
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

    // MARK: - classifyResult

    @Test
    fun successYieldsSucceededWithTheHash() {
        val outcome = classifyResult(TransactionResult(success = true, hash = "HASH"))
        assertEquals(CallOutcome.Succeeded("HASH"), outcome)
    }

    @Test
    fun contractCodeFailureYieldsRejected() {
        val outcome = classifyResult(TransactionResult(success = false, error = "Error(Contract, #3016)"))
        assertTrue(outcome is CallOutcome.Rejected)
        assertEquals(3016, outcome.errorCode)
        assertEquals("unauthorizedSigner", outcome.errorName)
    }

    @Test
    fun nonContractFailureYieldsFailed() {
        val outcome = classifyResult(TransactionResult(success = false, error = "timeout"))
        assertEquals(CallOutcome.Failed("timeout"), outcome)
    }

    @Test
    fun unknownContractCodeYieldsRejectedWithANullName() {
        val outcome = classifyResult(TransactionResult(success = false, error = "Error(Contract, #4242)"))
        assertTrue(outcome is CallOutcome.Rejected)
        assertEquals(4242, outcome.errorCode)
        assertNull(outcome.errorName)
    }

    // MARK: - classifyError

    @Test
    fun smartAccountExceptionWithAContractCodeYieldsRejected() {
        val outcome = classifyError(TransactionException.simulationFailed("Error(Contract, #3016)"))
        assertTrue(outcome is CallOutcome.Rejected)
        assertEquals(3016, outcome.errorCode)
    }

    @Test
    fun genericErrorWithoutACodeYieldsFailed() {
        val outcome = classifyError(RuntimeException("boom"))
        assertTrue(outcome is CallOutcome.Failed)
        assertTrue(outcome.message.contains("boom"))
    }
}
