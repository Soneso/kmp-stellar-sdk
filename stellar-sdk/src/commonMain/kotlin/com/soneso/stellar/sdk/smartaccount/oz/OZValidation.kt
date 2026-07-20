//
//  OZValidation.kt
//  Stellar SDK Kotlin Multiplatform
//
//  Copyright (c) 2026 Soneso. All rights reserved.
//

package com.soneso.stellar.sdk.smartaccount.oz

import com.soneso.stellar.sdk.StrKey
import com.soneso.stellar.sdk.smartaccount.core.ExternalSigner
import com.soneso.stellar.sdk.smartaccount.core.SmartAccountSigner
import com.soneso.stellar.sdk.smartaccount.core.ValidationException
import com.soneso.stellar.sdk.xdr.SCValXdr

/**
 * Validates that the given string is a valid Stellar contract address (C...).
 * Uses full StrKey validation including CRC16 checksum.
 *
 * @param address The address string to validate.
 * @param fieldName The field name used in the error message.
 * @throws ValidationException.InvalidAddress if the address is not a valid contract address.
 */
internal fun requireContractAddress(address: String, fieldName: String) {
    if (!StrKey.isValidContract(address)) {
        throw ValidationException.invalidAddress(
            "$fieldName must be a valid contract address (C...), got: $address"
        )
    }
}

/**
 * Validates that the given string is a valid Stellar address (G... account or C... contract).
 * Uses full StrKey validation including CRC16 checksum.
 *
 * @param address The address string to validate.
 * @param fieldName The field name used in the error message.
 * @throws ValidationException.InvalidAddress if the address is not a valid Stellar address.
 */
/**
 * Checks if a URL is a valid localhost URL for development.
 *
 * Matches `http://localhost` exactly, or followed by `:` (port) or `/` (path).
 * Rejects URLs like `http://localhost.evil.com`.
 */
internal fun isLocalhostUrl(url: String): Boolean {
    if (!url.startsWith("http://localhost")) return false
    val suffix = url.removePrefix("http://localhost")
    return suffix.isEmpty() || suffix[0] == ':' || suffix[0] == '/'
}

internal fun requireStellarAddress(address: String, fieldName: String) {
    if (!StrKey.isValidEd25519PublicKey(address) && !StrKey.isValidContract(address)) {
        throw ValidationException.invalidAddress(
            "$fieldName must be a valid Stellar address (G... or C...), got: $address"
        )
    }
}

/**
 * Validates a context rule name: non-empty and within the OZ contract's
 * [OZConstants.MAX_NAME_SIZE]-byte (UTF-8) limit. Rejecting oversized names client-side
 * turns an opaque on-chain failure into a clear error before submission.
 *
 * @param name The context rule name to validate.
 * @throws ValidationException.InvalidInput if the name is empty or exceeds the byte limit.
 */
internal fun requireValidContextRuleName(name: String) {
    if (name.isEmpty()) {
        throw ValidationException.invalidInput("name", "Context rule name cannot be empty")
    }
    val byteLength = name.encodeToByteArray().size
    if (byteLength > OZConstants.MAX_NAME_SIZE) {
        throw ValidationException.invalidInput(
            "name",
            "Context rule name cannot exceed ${OZConstants.MAX_NAME_SIZE} bytes, got: $byteLength"
        )
    }
}

/**
 * Validates that no external signer's key data exceeds the OZ contract's
 * [OZConstants.MAX_EXTERNAL_KEY_SIZE]-byte limit. Delegated signers carry no key data and
 * are skipped.
 *
 * @param signers The signers to validate.
 * @throws ValidationException.InvalidInput if any external signer's key data is too large.
 */
internal fun requireValidSigners(signers: List<SmartAccountSigner>) {
    for (signer in signers) {
        if (signer is ExternalSigner && signer.keyData.size > OZConstants.MAX_EXTERNAL_KEY_SIZE) {
            throw ValidationException.invalidInput(
                "keyData",
                "External signer key data cannot exceed ${OZConstants.MAX_EXTERNAL_KEY_SIZE} bytes, " +
                    "got: ${signer.keyData.size}"
            )
        }
    }
}

/**
 * Validates constructor / context-rule policies: at most [OZConstants.MAX_POLICIES] entries,
 * each keyed by a valid contract address (C...). The values are pre-encoded install-param
 * ScVals and are not inspected here.
 *
 * @param policies Policy install params keyed by policy contract address.
 * @throws ValidationException.InvalidInput if too many policies are supplied.
 * @throws ValidationException.InvalidAddress if a policy key is not a valid contract address.
 */
internal fun requireValidPolicies(policies: Map<String, SCValXdr>) {
    if (policies.size > OZConstants.MAX_POLICIES) {
        throw ValidationException.invalidInput(
            "policies",
            "Cannot install more than ${OZConstants.MAX_POLICIES} policies, got: ${policies.size}"
        )
    }
    for ((address, _) in policies) {
        requireContractAddress(address, "policyAddress")
    }
}
