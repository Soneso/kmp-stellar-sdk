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
    fun testDecode_allKnownCodesResolve() {
        val known = buildList {
            addAll((3000..3016).filter { it != 3001 })
            addAll(3110..3119)
            addAll(3200..3203)
            addAll(3210..3214)
            addAll(3220..3227)
        }
        assertEquals(43, known.size)
        for (code in known) {
            val decoded = ContractErrorCodes.decode(code)
            assertEquals(code, decoded?.code, "code $code must decode to a known error")
        }
    }
}
