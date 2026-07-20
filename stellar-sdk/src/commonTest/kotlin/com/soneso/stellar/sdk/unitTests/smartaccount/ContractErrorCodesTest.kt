//
//  ContractErrorCodesTest.kt
//  Stellar SDK Kotlin Multiplatform
//
//  Copyright (c) 2026 Soneso. All rights reserved.
//

package com.soneso.stellar.sdk.unitTests.smartaccount

import com.soneso.stellar.sdk.smartaccount.core.ContractErrorCodes
import com.soneso.stellar.sdk.smartaccount.core.OZContractError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Decoding of OZ smart-account, WebAuthn, and policy contract error codes into their
 * defining contract and variant name.
 */
class ContractErrorCodesTest {

    @Test
    fun testDecode_smartAccountCodes() {
        assertEquals(OZContractError(3000, "SmartAccountError", "ContextRuleNotFound"), ContractErrorCodes.decode(3000))
        assertEquals(OZContractError(3015, "SmartAccountError", "NameTooLong"), ContractErrorCodes.decode(3015))
        assertEquals(OZContractError(3016, "SmartAccountError", "UnauthorizedSigner"), ContractErrorCodes.decode(3016))
    }

    @Test
    fun testDecode_webAuthnCode() {
        assertEquals(OZContractError(3114, "WebAuthnError", "ChallengeInvalid"), ContractErrorCodes.decode(3114))
        assertEquals(OZContractError(3110, "WebAuthnError", "SignaturePayloadInvalid"), ContractErrorCodes.decode(3110))
    }

    @Test
    fun testDecode_policyCode() {
        assertEquals(OZContractError(3221, "SpendingLimitError", "SpendingLimitExceeded"), ContractErrorCodes.decode(3221))
        assertEquals(OZContractError(3227, "SpendingLimitError", "OnlyCallContractAllowed"), ContractErrorCodes.decode(3227))
    }

    @Test
    fun testDecode_repeatedNames_disambiguatedByContract() {
        // "NotAllowed" appears in all three policy enums at distinct codes.
        assertEquals("SimpleThresholdError", ContractErrorCodes.decode(3202)?.contract)
        assertEquals("WeightedThresholdError", ContractErrorCodes.decode(3213)?.contract)
        assertEquals("SpendingLimitError", ContractErrorCodes.decode(3223)?.contract)
        assertEquals("NotAllowed", ContractErrorCodes.decode(3202)?.name)
        assertEquals("NotAllowed", ContractErrorCodes.decode(3213)?.name)
        assertEquals("NotAllowed", ContractErrorCodes.decode(3223)?.name)

        // "MathOverflow" exists in both the smart account and weighted-threshold enums.
        assertEquals(OZContractError(3012, "SmartAccountError", "MathOverflow"), ContractErrorCodes.decode(3012))
        assertEquals(OZContractError(3212, "WeightedThresholdError", "MathOverflow"), ContractErrorCodes.decode(3212))
    }

    @Test
    fun testDecode_unusedAndGapCodes_returnNull() {
        // 3001 is unused in SmartAccountError; the policy enums have gaps.
        assertNull(ContractErrorCodes.decode(3001))
        assertNull(ContractErrorCodes.decode(3204))
        assertNull(ContractErrorCodes.decode(3215))
        assertNull(ContractErrorCodes.decode(3120))
    }

    @Test
    fun testDecode_outOfRangeCodes_returnNull() {
        assertNull(ContractErrorCodes.decode(0))
        assertNull(ContractErrorCodes.decode(2999))
        assertNull(ContractErrorCodes.decode(9999))
        assertNull(ContractErrorCodes.decode(-1))
    }

    @Test
    fun testConstants_matchDecodeTable() {
        assertEquals(3000, ContractErrorCodes.CONTEXT_RULE_NOT_FOUND)
        assertEquals(3015, ContractErrorCodes.NAME_TOO_LONG)
        assertEquals(3016, ContractErrorCodes.UNAUTHORIZED_SIGNER)
        assertEquals("NameTooLong", ContractErrorCodes.decode(ContractErrorCodes.NAME_TOO_LONG)?.name)
    }

    @Test
    fun testDecode_fullTableMatchesContractEnums() {
        // Independent transcription of the contract enums; a transposed name or wrong
        // contract in the production table fails here.
        val expected = mapOf(
            3000 to ("SmartAccountError" to "ContextRuleNotFound"),
            3002 to ("SmartAccountError" to "UnvalidatedContext"),
            3003 to ("SmartAccountError" to "ExternalVerificationFailed"),
            3004 to ("SmartAccountError" to "NoSignersAndPolicies"),
            3005 to ("SmartAccountError" to "PastValidUntil"),
            3006 to ("SmartAccountError" to "SignerNotFound"),
            3007 to ("SmartAccountError" to "DuplicateSigner"),
            3008 to ("SmartAccountError" to "PolicyNotFound"),
            3009 to ("SmartAccountError" to "DuplicatePolicy"),
            3010 to ("SmartAccountError" to "TooManySigners"),
            3011 to ("SmartAccountError" to "TooManyPolicies"),
            3012 to ("SmartAccountError" to "MathOverflow"),
            3013 to ("SmartAccountError" to "KeyDataTooLarge"),
            3014 to ("SmartAccountError" to "ContextRuleIdsLengthMismatch"),
            3015 to ("SmartAccountError" to "NameTooLong"),
            3016 to ("SmartAccountError" to "UnauthorizedSigner"),
            3110 to ("WebAuthnError" to "SignaturePayloadInvalid"),
            3111 to ("WebAuthnError" to "ClientDataTooLong"),
            3112 to ("WebAuthnError" to "JsonParseError"),
            3113 to ("WebAuthnError" to "TypeFieldInvalid"),
            3114 to ("WebAuthnError" to "ChallengeInvalid"),
            3115 to ("WebAuthnError" to "AuthDataFormatInvalid"),
            3116 to ("WebAuthnError" to "PresentBitNotSet"),
            3117 to ("WebAuthnError" to "VerifiedBitNotSet"),
            3118 to ("WebAuthnError" to "BackupEligibilityAndStateNotSet"),
            3119 to ("WebAuthnError" to "KeyDataInvalid"),
            3200 to ("SimpleThresholdError" to "SmartAccountNotInstalled"),
            3201 to ("SimpleThresholdError" to "InvalidThreshold"),
            3202 to ("SimpleThresholdError" to "NotAllowed"),
            3203 to ("SimpleThresholdError" to "AlreadyInstalled"),
            3210 to ("WeightedThresholdError" to "SmartAccountNotInstalled"),
            3211 to ("WeightedThresholdError" to "InvalidThreshold"),
            3212 to ("WeightedThresholdError" to "MathOverflow"),
            3213 to ("WeightedThresholdError" to "NotAllowed"),
            3214 to ("WeightedThresholdError" to "AlreadyInstalled"),
            3220 to ("SpendingLimitError" to "SmartAccountNotInstalled"),
            3221 to ("SpendingLimitError" to "SpendingLimitExceeded"),
            3222 to ("SpendingLimitError" to "InvalidLimitOrPeriod"),
            3223 to ("SpendingLimitError" to "NotAllowed"),
            3224 to ("SpendingLimitError" to "HistoryCapacityExceeded"),
            3225 to ("SpendingLimitError" to "AlreadyInstalled"),
            3226 to ("SpendingLimitError" to "LessThanZero"),
            3227 to ("SpendingLimitError" to "OnlyCallContractAllowed")
        )
        assertEquals(43, expected.size)
        for ((code, contractAndName) in expected) {
            val decoded = ContractErrorCodes.decode(code)
            assertEquals(OZContractError(code, contractAndName.first, contractAndName.second), decoded)
        }
    }

    // MARK: - decodeFromMessage

    @Test
    fun testDecodeFromMessage_simulationErrorString() {
        val message = "Transaction simulation failed: Simulation error: HostError: Error(Context, InvalidAction) " +
            "Event log: [Diagnostic Event] topics:[error, Error(Context, InvalidAction)], " +
            "data:[\"constructor invocation has failed with error\", Error(Contract, #3201)]"
        assertEquals(OZContractError(3201, "SimpleThresholdError", "InvalidThreshold"), ContractErrorCodes.decodeFromMessage(message))
    }

    @Test
    fun testDecodeFromMessage_whitespaceVariants() {
        assertEquals(3227, ContractErrorCodes.decodeFromMessage("Error(Contract, #3227)")?.code)
        assertEquals(3227, ContractErrorCodes.decodeFromMessage("Error( Contract , #3227 )")?.code)
    }

    @Test
    fun testDecodeFromMessage_skipsUnknownCodeAndReturnsFirstKnown() {
        // 3001 is unused in the contract enums; the scan continues to the next marker.
        val message = "Error(Contract, #3001) then Error(Contract, #3114)"
        assertEquals(OZContractError(3114, "WebAuthnError", "ChallengeInvalid"), ContractErrorCodes.decodeFromMessage(message))
    }

    @Test
    fun testDecodeFromMessage_noMatchReturnsNull() {
        assertNull(ContractErrorCodes.decodeFromMessage(null))
        assertNull(ContractErrorCodes.decodeFromMessage("no contract error here"))
        assertNull(ContractErrorCodes.decodeFromMessage("Error(Contract, #3001)"))
        assertNull(ContractErrorCodes.decodeFromMessage("Error(WasmVm, InternalError)"))
    }

    @Test
    fun testDecodeFromMessage_numericCodeOverflowingIntIsSkipped() {
        // The (\d+) marker matches, but a value that overflows Int makes toIntOrNull() null,
        // so the marker is skipped instead of being misdecoded.
        assertNull(ContractErrorCodes.decodeFromMessage("Error(Contract, #99999999999999999)"))
        // A known marker following an overflowing one is still found.
        assertEquals(
            3114,
            ContractErrorCodes.decodeFromMessage(
                "Error(Contract, #99999999999999999) then Error(Contract, #3114)"
            )?.code
        )
    }
}
