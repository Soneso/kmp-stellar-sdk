package com.soneso.stellar.sdk.contract.exception

/**
 * Exception thrown when ContractSpec operations fail.
 *
 * This exception is used for all type conversion and specification-related errors
 * when using [com.soneso.stellar.sdk.contract.ContractSpec] to convert native Kotlin
 * types to Stellar XDR types.
 *
 * ## Usage
 *
 * ```kotlin
 * throw ContractSpecException.functionNotFound("myFunction")
 * throw ContractSpecException.argumentNotFound("amount", functionName = "transfer")
 * throw ContractSpecException.invalidType("Expected Int, got String")
 * ```
 *
 * @property message The error message
 * @property functionName Optional function name where the error occurred
 * @property argumentName Optional argument name where the error occurred
 * @property entryName Optional spec entry name where the error occurred
 * @property cause The underlying cause, if any
 */
open class ContractSpecException(
    message: String,
    val functionName: String? = null,
    val argumentName: String? = null,
    val entryName: String? = null,
    cause: Throwable? = null
) : ContractException(message, assembledTransaction = null, cause = cause) {

    override fun toString(): String {
        var result = "ContractSpecException: $message"
        if (functionName != null) {
            result += " (function: $functionName)"
        }
        if (argumentName != null) {
            result += " (argument: $argumentName)"
        }
        if (entryName != null) {
            result += " (entry: $entryName)"
        }
        return result
    }

    companion object {
        /**
         * Creates an exception for when a function is not found in the contract spec.
         *
         * @param name The function name that was not found
         * @return A ContractSpecException with appropriate message
         */
        fun functionNotFound(name: String): ContractSpecException {
            return ContractSpecException(
                message = "Function not found: $name",
                functionName = name
            )
        }

        /**
         * Creates an exception for when a required argument is not provided.
         *
         * @param name The argument name that was not found
         * @param functionName Optional function name for context
         * @return A ContractSpecException with appropriate message
         */
        fun argumentNotFound(name: String, functionName: String? = null): ContractSpecException {
            return ContractSpecException(
                message = "Required argument not found: $name",
                argumentName = name,
                functionName = functionName
            )
        }

        /**
         * Creates an exception for when a spec entry is not found.
         *
         * @param name The entry name that was not found
         * @return A ContractSpecException with appropriate message
         */
        fun entryNotFound(name: String): ContractSpecException {
            return ContractSpecException(
                message = "Entry not found: $name",
                entryName = name
            )
        }

        /**
         * Creates an exception for invalid type conversion.
         *
         * @param message Description of the type error
         * @return A ContractSpecException with appropriate message
         */
        fun invalidType(message: String): ContractSpecException {
            return ContractSpecException("Invalid type: $message")
        }

        /**
         * Creates an exception for conversion failures.
         *
         * @param message Description of the conversion failure
         * @return A ContractSpecException with appropriate message
         */
        fun conversionFailed(message: String): ContractSpecException {
            return ContractSpecException("Conversion failed: $message")
        }

        /**
         * Creates an exception for invalid enum values.
         *
         * @param message Description of the invalid enum value
         * @return A ContractSpecException with appropriate message
         */
        fun invalidEnumValue(message: String): ContractSpecException {
            return ContractSpecException("Invalid enum value: $message")
        }
    }
}
