//
//  OZValidation.kt
//  Stellar SDK Kotlin Multiplatform
//
//  Copyright (c) 2026 Soneso. All rights reserved.
//

package com.soneso.stellar.sdk.smartaccount.oz

import com.soneso.stellar.sdk.StrKey
import com.soneso.stellar.sdk.smartaccount.core.ValidationException

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
