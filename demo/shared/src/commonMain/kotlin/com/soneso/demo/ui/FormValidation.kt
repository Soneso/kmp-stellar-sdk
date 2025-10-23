package com.soneso.demo.ui

import com.soneso.demo.util.StellarValidation

/**
 * UI-focused form validation utilities.
 *
 * This utility object provides validation functions optimized for form field validation
 * in UI components. It wraps the core Stellar validation logic from [StellarValidation]
 * and provides shorter, more UI-appropriate error messages.
 *
 * ## Differences from StellarValidation
 *
 * - **Shorter error messages**: Optimized for display in form fields
 * - **Helpful hints**: Provides guidance for empty fields
 * - **UI-focused**: Designed for inline validation and error display
 *
 * ## Usage in Compose UI
 *
 * ```kotlin
 * var accountId by remember { mutableStateOf("") }
 * var accountIdError by remember { mutableStateOf<String?>(null) }
 *
 * OutlinedTextField(
 *     value = accountId,
 *     onValueChange = {
 *         accountId = it
 *         accountIdError = FormValidation.validateAccountIdField(it)
 *     },
 *     label = { Text("Account ID") },
 *     isError = accountIdError != null,
 *     supportingText = accountIdError?.let { { Text(it) } }
 * )
 * ```
 *
 * @see StellarValidation
 */
object FormValidation {

    /**
     * Validates an account ID field for UI display.
     *
     * This is a UI-friendly wrapper around [StellarValidation.validateAccountId]
     * that provides shorter error messages suitable for form fields.
     *
     * @param value The account ID to validate
     * @return Short error message if invalid, null if valid
     */
    fun validateAccountIdField(value: String): String? {
        if (value.isEmpty()) {
            return "Enter an account ID (G...)"
        }
        return StellarValidation.validateAccountId(value)
    }

    /**
     * Validates a secret seed field for UI display.
     *
     * This is a UI-friendly wrapper around [StellarValidation.validateSecretSeed]
     * that provides shorter error messages suitable for form fields.
     *
     * @param value The secret seed to validate
     * @return Short error message if invalid, null if valid
     */
    fun validateSecretSeedField(value: String): String? {
        if (value.isEmpty()) {
            return "Enter a secret seed (S...)"
        }
        return StellarValidation.validateSecretSeed(value)
    }

    /**
     * Validates a contract ID field for UI display.
     *
     * This is a UI-friendly wrapper around [StellarValidation.validateContractId]
     * that provides shorter error messages suitable for form fields.
     *
     * @param value The contract ID to validate
     * @return Short error message if invalid, null if valid
     */
    fun validateContractIdField(value: String): String? {
        if (value.isEmpty()) {
            return "Enter a contract ID (C...)"
        }
        return StellarValidation.validateContractId(value)
    }

    /**
     * Validates a transaction hash field for UI display.
     *
     * This is a UI-friendly wrapper around [StellarValidation.validateTransactionHash]
     * that provides shorter error messages suitable for form fields.
     *
     * @param value The transaction hash to validate
     * @return Short error message if invalid, null if valid
     */
    fun validateTransactionHashField(value: String): String? {
        if (value.isEmpty()) {
            return "Enter a transaction hash"
        }
        return StellarValidation.validateTransactionHash(value)
    }

    /**
     * Validates an asset code field for UI display.
     *
     * This is a UI-friendly wrapper around [StellarValidation.validateAssetCode]
     * that provides shorter error messages suitable for form fields.
     *
     * @param value The asset code to validate
     * @return Short error message if invalid, null if valid
     */
    fun validateAssetCodeField(value: String): String? {
        if (value.isEmpty()) {
            return "Enter an asset code"
        }
        return StellarValidation.validateAssetCode(value)
    }
}
