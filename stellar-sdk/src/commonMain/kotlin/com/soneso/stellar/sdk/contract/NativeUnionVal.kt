package com.soneso.stellar.sdk.contract

/**
 * Represents a union value for Soroban contract specifications.
 * Used when passing union type values to contract functions.
 *
 * Union types in Stellar contracts can have two forms:
 * 1. Void case - just a tag name (e.g., "Success", "Error")
 * 2. Tuple case - a tag name with associated values (e.g., "Data" with values `["field1", "field2"]`)
 *
 * ## Usage
 *
 * ```kotlin
 * // Void case (no associated values)
 * val success = NativeUnionVal.VoidCase("Success")
 *
 * // Tuple case (with associated values)
 * val data = NativeUnionVal.TupleCase("Data", listOf("field1", "field2"))
 * ```
 *
 * @property tag The union case name/tag
 */
sealed class NativeUnionVal {
    abstract val tag: String

    /**
     * Represents a void union case (no associated values).
     *
     * @property tag The union case name
     */
    data class VoidCase(override val tag: String) : NativeUnionVal()

    /**
     * Represents a tuple union case with associated values.
     *
     * @property tag The union case name
     * @property values The associated values for this union case
     */
    data class TupleCase(
        override val tag: String,
        val values: List<Any?>
    ) : NativeUnionVal()

    /**
     * Returns true if this is a void case (no associated values).
     */
    val isVoidCase: Boolean get() = this is VoidCase

    /**
     * Returns true if this is a tuple case (has associated values).
     */
    val isTupleCase: Boolean get() = this is TupleCase
}
