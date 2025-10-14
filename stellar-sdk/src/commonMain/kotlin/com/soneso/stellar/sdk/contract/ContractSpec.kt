package com.soneso.stellar.sdk.contract

import com.soneso.stellar.sdk.Address
import com.soneso.stellar.sdk.contract.exception.ContractSpecException
import com.soneso.stellar.sdk.xdr.*
import kotlin.math.abs

/**
 * Utility class for working with Soroban contract specifications.
 *
 * This class provides methods to find spec entries, convert native Kotlin values
 * to [SCValXdr] objects based on contract specifications, and simplify argument
 * preparation for contract function invocations.
 *
 * ## Core Features
 *
 * - **Automatic Type Conversion**: Convert native Kotlin types to XDR values based on contract specs
 * - **Address Auto-Detection**: Strings starting with "G" or "M" become account addresses, "C" becomes contract addresses
 * - **Collection Handling**: Automatic conversion of Lists, Maps, and tuples
 * - **Complex Types**: Full support for structs, unions, and enums
 * - **BigInteger Support**: Handle large numbers (u128/i128/u256/i256)
 *
 * ## Usage
 *
 * ```kotlin
 * // Create ContractSpec from spec entries
 * val spec = ContractSpec(specEntries)
 *
 * // Convert function arguments - much simpler than manual XDR construction!
 * val args = spec.funcArgsToXdrSCValues("swap", mapOf(
 *     "a" to "GABC...",        // String → Address (auto-detected as account)
 *     "token_a" to "CABC...",  // String → Address (auto-detected as contract)
 *     "amount_a" to 1000,      // Int → i128
 *     "min_b_for_a" to 4500    // Int → i128
 * ))
 *
 * // Introspection
 * val functions = spec.funcs()
 * val helloFunc = spec.getFunc("hello")
 * val structEntry = spec.findEntry("MyStruct")
 * ```
 *
 * @property entries The list of contract specification entries
 */
class ContractSpec(private val entries: List<SCSpecEntryXdr>) {

    /**
     * Returns all function specifications from the contract spec.
     *
     * @return List of function specifications
     */
    fun funcs(): List<SCSpecFunctionV0Xdr> {
        return entries.mapNotNull { entry ->
            when (entry) {
                is SCSpecEntryXdr.FunctionV0 -> entry.value
                else -> null
            }
        }
    }

    /**
     * Finds a specific function specification by name.
     *
     * @param name The function name to search for
     * @return The function specification, or null if not found
     */
    fun getFunc(name: String): SCSpecFunctionV0Xdr? {
        return funcs().firstOrNull { it.name.value == name }
    }

    /**
     * Finds any spec entry by name.
     * Searches across functions, structs, unions, enums, and error enums.
     *
     * @param name The entry name to search for
     * @return The spec entry, or null if not found
     */
    fun findEntry(name: String): SCSpecEntryXdr? {
        return entries.firstOrNull { entry ->
            when (entry) {
                is SCSpecEntryXdr.FunctionV0 -> entry.value.name.value == name
                is SCSpecEntryXdr.UdtStructV0 -> entry.value.name == name
                is SCSpecEntryXdr.UdtUnionV0 -> entry.value.name == name
                is SCSpecEntryXdr.UdtEnumV0 -> entry.value.name == name
                is SCSpecEntryXdr.UdtErrorEnumV0 -> entry.value.name == name
                else -> false
            }
        }
    }

    /**
     * Converts function arguments to XDR SCVal objects based on the function specification.
     *
     * This is the primary method that dramatically simplifies contract interaction by
     * automatically converting native Kotlin types to XDR based on the contract spec.
     *
     * @param functionName The function name
     * @param args Map of argument names to values
     * @return List of SCVal objects in the correct order for the function
     * @throws ContractSpecException if the function is not found or required arguments are missing
     */
    fun funcArgsToXdrSCValues(functionName: String, args: Map<String, Any?>): List<SCValXdr> {
        val func = getFunc(functionName)
            ?: throw ContractSpecException.functionNotFound(functionName)

        val scValues = mutableListOf<SCValXdr>()
        for (input in func.inputs) {
            val argName = input.name
            if (!args.containsKey(argName)) {
                throw ContractSpecException.argumentNotFound(argName, functionName = functionName)
            }

            val argValue = args[argName]
            val scValue = nativeToXdrSCVal(argValue, input.type)
            scValues.add(scValue)
        }

        return scValues
    }

    /**
     * Converts a native Kotlin value to an SCValXdr based on the type specification.
     *
     * This is the core conversion method that handles all type mappings from Kotlin
     * native types to Stellar XDR values.
     *
     * @param value The native Kotlin value to convert
     * @param typeDef The target type specification
     * @return The converted SCValXdr
     * @throws ContractSpecException for invalid types or conversion failures
     */
    fun nativeToXdrSCVal(value: Any?, typeDef: SCSpecTypeDefXdr): SCValXdr {
        // Handle null values
        if (value == null) {
            return SCValXdr.Void(SCValTypeXdr.SCV_VOID)
        }

        // If already an SCValXdr, return as-is
        if (value is SCValXdr) {
            return value
        }

        return when (typeDef) {
            // Basic value types
            is SCSpecTypeDefXdr.Void -> handleValueType(value, typeDef)
            // Complex types
            is SCSpecTypeDefXdr.Option -> handleOptionType(value, typeDef)
            is SCSpecTypeDefXdr.Result -> handleResultType(value, typeDef)
            is SCSpecTypeDefXdr.Vec -> handleVecType(value, typeDef)
            is SCSpecTypeDefXdr.Map -> handleMapType(value, typeDef)
            is SCSpecTypeDefXdr.Tuple -> handleTupleType(value, typeDef)
            is SCSpecTypeDefXdr.BytesN -> handleBytesNType(value, typeDef)
            is SCSpecTypeDefXdr.Udt -> handleUDTType(value, typeDef)
        }
    }

    // ========== Private Helper Methods ==========

    /**
     * Handles basic value types (bool, numbers, strings, addresses, etc.)
     */
    private fun handleValueType(value: Any?, typeDef: SCSpecTypeDefXdr): SCValXdr {
        val typeDiscriminant = typeDef.discriminant

        return when (typeDiscriminant) {
            SCSpecTypeXdr.SC_SPEC_TYPE_VOID -> SCValXdr.Void(SCValTypeXdr.SCV_VOID)
            SCSpecTypeXdr.SC_SPEC_TYPE_BOOL -> {
                if (value !is Boolean) {
                    throw ContractSpecException.invalidType("Expected Boolean, got ${value?.let { it::class.simpleName } ?: "null"}")
                }
                SCValXdr.B(value)
            }
            SCSpecTypeXdr.SC_SPEC_TYPE_U32 -> {
                val intVal = parseInteger(value, "u32")
                if (intVal < 0 || intVal > 0xFFFFFFFFL) {
                    throw ContractSpecException.invalidType("Value $intVal out of range for u32")
                }
                SCValXdr.U32(Uint32Xdr(intVal.toUInt()))
            }
            SCSpecTypeXdr.SC_SPEC_TYPE_I32 -> {
                val intVal = parseInteger(value, "i32")
                if (intVal < Int.MIN_VALUE || intVal > Int.MAX_VALUE) {
                    throw ContractSpecException.invalidType("Value $intVal out of range for i32")
                }
                SCValXdr.I32(Int32Xdr(intVal.toInt()))
            }
            SCSpecTypeXdr.SC_SPEC_TYPE_U64 -> {
                val intVal = parseInteger(value, "u64")
                if (intVal < 0) {
                    throw ContractSpecException.invalidType("Value $intVal out of range for u64")
                }
                SCValXdr.U64(Uint64Xdr(intVal.toULong()))
            }
            SCSpecTypeXdr.SC_SPEC_TYPE_I64 -> {
                val intVal = parseInteger(value, "i64")
                SCValXdr.I64(Int64Xdr(intVal))
            }
            SCSpecTypeXdr.SC_SPEC_TYPE_TIMEPOINT -> {
                val intVal = parseInteger(value, "timepoint")
                if (intVal < 0) {
                    throw ContractSpecException.invalidType("Value $intVal out of range for timepoint")
                }
                SCValXdr.Timepoint(TimePointXdr(Uint64Xdr(intVal.toULong())))
            }
            SCSpecTypeXdr.SC_SPEC_TYPE_DURATION -> {
                val intVal = parseInteger(value, "duration")
                if (intVal < 0) {
                    throw ContractSpecException.invalidType("Value $intVal out of range for duration")
                }
                SCValXdr.Duration(DurationXdr(Uint64Xdr(intVal.toULong())))
            }
            SCSpecTypeXdr.SC_SPEC_TYPE_U128 -> handleU128Type(value)
            SCSpecTypeXdr.SC_SPEC_TYPE_I128 -> handleI128Type(value)
            SCSpecTypeXdr.SC_SPEC_TYPE_U256 -> handleU256Type(value)
            SCSpecTypeXdr.SC_SPEC_TYPE_I256 -> handleI256Type(value)
            SCSpecTypeXdr.SC_SPEC_TYPE_BYTES -> handleBytesType(value)
            SCSpecTypeXdr.SC_SPEC_TYPE_STRING -> {
                if (value !is String) {
                    throw ContractSpecException.invalidType("Expected String, got ${value?.let { it::class.simpleName } ?: "null"}")
                }
                SCValXdr.Str(SCStringXdr(value))
            }
            SCSpecTypeXdr.SC_SPEC_TYPE_SYMBOL -> {
                if (value !is String) {
                    throw ContractSpecException.invalidType("Expected String, got ${value?.let { it::class.simpleName } ?: "null"}")
                }
                SCValXdr.Sym(SCSymbolXdr(value))
            }
            SCSpecTypeXdr.SC_SPEC_TYPE_ADDRESS -> handleAddressType(value)
            else -> throw ContractSpecException.invalidType("Unsupported value type: $typeDiscriminant")
        }
    }

    /**
     * Parse integer from various input types
     */
    private fun parseInteger(value: Any?, typeName: String): Long {
        return when (value) {
            is Int -> value.toLong()
            is Long -> value
            is UInt -> value.toLong()
            is ULong -> value.toLong()
            is Double -> value.toLong()
            is Float -> value.toLong()
            is String -> value.toLongOrNull()
                ?: throw ContractSpecException.invalidType("Cannot parse \"$value\" as integer for $typeName")
            else -> throw ContractSpecException.invalidType("Expected integer type, got ${value?.let { it::class.simpleName } ?: "null"} for $typeName")
        }
    }

    /**
     * Handle 128-bit unsigned integer conversion
     */
    private fun handleU128Type(value: Any?): SCValXdr {
        // Handle small integers
        val intVal = parseInteger(value, "u128")
        if (intVal < 0) {
            throw ContractSpecException.invalidType("Value $intVal out of range for u128")
        }

        // Simple conversion for values that fit in long
        val hi = 0UL
        val lo = intVal.toULong()
        return SCValXdr.U128(UInt128PartsXdr(Uint64Xdr(hi), Uint64Xdr(lo)))
    }

    /**
     * Handle 128-bit signed integer conversion
     */
    private fun handleI128Type(value: Any?): SCValXdr {
        val intVal = parseInteger(value, "i128")

        // Sign extension for negative values
        val hi = if (intVal < 0) -1L else 0L
        val lo = intVal
        return SCValXdr.I128(Int128PartsXdr(Int64Xdr(hi), Uint64Xdr(lo.toULong())))
    }

    /**
     * Handle 256-bit unsigned integer conversion
     */
    private fun handleU256Type(value: Any?): SCValXdr {
        val intVal = parseInteger(value, "u256")
        if (intVal < 0) {
            throw ContractSpecException.invalidType("Value $intVal out of range for u256")
        }

        // For small integers, we can represent them in the lower 64 bits
        val hiHi = 0UL
        val hiLo = 0UL
        val loHi = 0UL
        val loLo = intVal.toULong()

        return SCValXdr.U256(
            UInt256PartsXdr(
                hiHi = Uint64Xdr(hiHi),
                hiLo = Uint64Xdr(hiLo),
                loHi = Uint64Xdr(loHi),
                loLo = Uint64Xdr(loLo)
            )
        )
    }

    /**
     * Handle 256-bit signed integer conversion
     */
    private fun handleI256Type(value: Any?): SCValXdr {
        val intVal = parseInteger(value, "i256")

        // Sign extension for negative values
        val hiHi = if (intVal < 0) -1L else 0L
        val hiLo = if (intVal < 0) -1L else 0L
        val loHi = if (intVal < 0) -1L else 0L
        val loLo = intVal

        return SCValXdr.I256(
            Int256PartsXdr(
                hiHi = Int64Xdr(hiHi),
                hiLo = Uint64Xdr(hiLo.toULong()),
                loHi = Uint64Xdr(loHi.toULong()),
                loLo = Uint64Xdr(loLo.toULong())
            )
        )
    }

    /**
     * Handle bytes type conversion
     */
    private fun handleBytesType(value: Any?): SCValXdr {
        val bytes = when (value) {
            is ByteArray -> value
            is List<*> -> {
                @Suppress("UNCHECKED_CAST")
                (value as? List<Byte>)?.toByteArray()
                    ?: throw ContractSpecException.invalidType("Expected List<Byte>, got List<${value.firstOrNull()?.let { it::class.simpleName } ?: "null"}>")
            }
            is String -> {
                // Assume hex string
                try {
                    hexToBytes(value)
                } catch (e: Exception) {
                    throw ContractSpecException.conversionFailed("Cannot convert string \"$value\" to bytes: ${e.message}")
                }
            }
            else -> throw ContractSpecException.invalidType("Expected ByteArray, List<Byte>, or hex String, got ${value?.let { it::class.simpleName } ?: "null"}")
        }

        return SCValXdr.Bytes(SCBytesXdr(bytes))
    }

    /**
     * Handle address type conversion with auto-detection
     */
    private fun handleAddressType(value: Any?): SCValXdr {
        if (value !is String) {
            throw ContractSpecException.invalidType("Expected String address, got ${value?.let { it::class.simpleName } ?: "null"}")
        }

        // Auto-detect address type by prefix
        val address = try {
            Address(value)
        } catch (e: Exception) {
            throw ContractSpecException.invalidType("Invalid address format: $value - ${e.message}")
        }

        return address.toSCVal()
    }

    /**
     * Handle option type (nullable values)
     */
    private fun handleOptionType(value: Any?, typeDef: SCSpecTypeDefXdr.Option): SCValXdr {
        if (value == null) {
            return SCValXdr.Void(SCValTypeXdr.SCV_VOID)
        }

        return nativeToXdrSCVal(value, typeDef.value.valueType)
    }

    /**
     * Handle result type (success/error union)
     */
    private fun handleResultType(value: Any?, typeDef: SCSpecTypeDefXdr.Result): SCValXdr {
        throw ContractSpecException.conversionFailed("Result type conversion not yet implemented")
    }

    /**
     * Handle vector type
     */
    private fun handleVecType(value: Any?, typeDef: SCSpecTypeDefXdr.Vec): SCValXdr {
        if (value !is List<*>) {
            throw ContractSpecException.invalidType("Expected List, got ${value?.let { it::class.simpleName } ?: "null"}")
        }

        val scValues = value.map { item ->
            nativeToXdrSCVal(item, typeDef.value.elementType)
        }

        return SCValXdr.Vec(SCVecXdr(scValues))
    }

    /**
     * Handle map type
     */
    private fun handleMapType(value: Any?, typeDef: SCSpecTypeDefXdr.Map): SCValXdr {
        if (value !is Map<*, *>) {
            throw ContractSpecException.invalidType("Expected Map, got ${value?.let { it::class.simpleName } ?: "null"}")
        }

        val entries = value.map { (key, mapValue) ->
            val keyVal = nativeToXdrSCVal(key, typeDef.value.keyType)
            val valueVal = nativeToXdrSCVal(mapValue, typeDef.value.valueType)
            SCMapEntryXdr(keyVal, valueVal)
        }

        return SCValXdr.Map(SCMapXdr(entries))
    }

    /**
     * Handle tuple type
     */
    private fun handleTupleType(value: Any?, typeDef: SCSpecTypeDefXdr.Tuple): SCValXdr {
        if (value !is List<*>) {
            throw ContractSpecException.invalidType("Expected List, got ${value?.let { it::class.simpleName } ?: "null"}")
        }

        if (value.size != typeDef.value.valueTypes.size) {
            throw ContractSpecException.invalidType(
                "Tuple length mismatch: expected ${typeDef.value.valueTypes.size}, got ${value.size}"
            )
        }

        val scValues = value.mapIndexed { index, item ->
            nativeToXdrSCVal(item, typeDef.value.valueTypes[index])
        }

        return SCValXdr.Vec(SCVecXdr(scValues))
    }

    /**
     * Handle bytesN type (fixed-length bytes)
     */
    private fun handleBytesNType(value: Any?, typeDef: SCSpecTypeDefXdr.BytesN): SCValXdr {
        val expectedLength = typeDef.value.n.value.toInt()

        val bytes = when (value) {
            is ByteArray -> value
            is List<*> -> {
                @Suppress("UNCHECKED_CAST")
                (value as? List<Byte>)?.toByteArray()
                    ?: throw ContractSpecException.invalidType("Expected List<Byte>, got List<${value.firstOrNull()?.let { it::class.simpleName } ?: "null"}>")
            }
            is String -> {
                try {
                    hexToBytes(value)
                } catch (e: Exception) {
                    throw ContractSpecException.conversionFailed("Cannot convert string \"$value\" to bytes: ${e.message}")
                }
            }
            else -> throw ContractSpecException.invalidType("Expected ByteArray, List<Byte>, or hex String, got ${value?.let { it::class.simpleName } ?: "null"}")
        }

        if (bytes.size != expectedLength) {
            throw ContractSpecException.invalidType(
                "BytesN length mismatch: expected $expectedLength, got ${bytes.size}"
            )
        }

        return SCValXdr.Bytes(SCBytesXdr(bytes))
    }

    /**
     * Handle user-defined type (struct, union, enum)
     */
    private fun handleUDTType(value: Any?, typeDef: SCSpecTypeDefXdr.Udt): SCValXdr {
        val entry = findEntry(typeDef.value.name)
            ?: throw ContractSpecException.entryNotFound(typeDef.value.name)

        return when (entry) {
            is SCSpecEntryXdr.UdtStructV0 -> handleStructType(value, entry.value)
            is SCSpecEntryXdr.UdtUnionV0 -> handleUnionType(value, entry.value)
            is SCSpecEntryXdr.UdtEnumV0 -> handleEnumType(value, entry.value)
            else -> throw ContractSpecException.invalidType("Unsupported UDT type: ${entry.discriminant}")
        }
    }

    /**
     * Handle struct type conversion
     */
    private fun handleStructType(value: Any?, structDef: SCSpecUDTStructV0Xdr): SCValXdr {
        if (value !is Map<*, *>) {
            throw ContractSpecException.invalidType(
                "Expected Map<String, Any?> for struct ${structDef.name}, got ${value?.let { it::class.simpleName } ?: "null"}"
            )
        }

        @Suppress("UNCHECKED_CAST")
        val valueMap = value as? Map<String, Any?>
            ?: throw ContractSpecException.invalidType("Struct map must have String keys")

        // Determine if this should be a map or vector based on field names
        val useMap = structDef.fields.any { field -> !isNumericString(field.name) }

        if (useMap) {
            // Use map representation
            val entries = structDef.fields.map { field ->
                if (!valueMap.containsKey(field.name)) {
                    throw ContractSpecException.argumentNotFound(field.name)
                }
                val keyVal = SCValXdr.Sym(SCSymbolXdr(field.name))
                val fieldValue = nativeToXdrSCVal(valueMap[field.name], field.type)
                SCMapEntryXdr(keyVal, fieldValue)
            }
            return SCValXdr.Map(SCMapXdr(entries))
        } else {
            // Use vector representation (all fields are numeric)
            val sortedFields = structDef.fields.sortedBy { it.name.toInt() }
            val scValues = sortedFields.map { field ->
                if (!valueMap.containsKey(field.name)) {
                    throw ContractSpecException.argumentNotFound(field.name)
                }
                nativeToXdrSCVal(valueMap[field.name], field.type)
            }
            return SCValXdr.Vec(SCVecXdr(scValues))
        }
    }

    /**
     * Handle union type conversion
     */
    private fun handleUnionType(value: Any?, unionDef: SCSpecUDTUnionV0Xdr): SCValXdr {
        if (value !is NativeUnionVal) {
            throw ContractSpecException.invalidType(
                "Expected NativeUnionVal for union ${unionDef.name}, got ${value?.let { it::class.simpleName } ?: "null"}"
            )
        }

        // Find the matching union case
        var matchingCase: SCSpecUDTUnionCaseV0Xdr? = null
        for (unionCase in unionDef.cases) {
            val caseName = when (unionCase) {
                is SCSpecUDTUnionCaseV0Xdr.VoidCase -> unionCase.value.name
                is SCSpecUDTUnionCaseV0Xdr.TupleCase -> unionCase.value.name
            }

            if (caseName == value.tag) {
                matchingCase = unionCase
                break
            }
        }

        if (matchingCase == null) {
            throw ContractSpecException.invalidEnumValue(
                "Unknown union case \"${value.tag}\" for union ${unionDef.name}"
            )
        }

        val scValues = mutableListOf<SCValXdr>()

        // Add the tag as a symbol
        scValues.add(SCValXdr.Sym(SCSymbolXdr(value.tag)))

        // Handle the case value
        when (matchingCase) {
            is SCSpecUDTUnionCaseV0Xdr.VoidCase -> {
                // Void case - just the tag
            }
            is SCSpecUDTUnionCaseV0Xdr.TupleCase -> {
                val tupleCase = matchingCase.value

                if (value !is NativeUnionVal.TupleCase || value.values.size != tupleCase.type.size) {
                    throw ContractSpecException.invalidType(
                        "Union case \"${value.tag}\" expects ${tupleCase.type.size} values, got ${(value as? NativeUnionVal.TupleCase)?.values?.size ?: 0}"
                    )
                }

                for (i in tupleCase.type.indices) {
                    scValues.add(nativeToXdrSCVal(value.values[i], tupleCase.type[i]))
                }
            }
        }

        return SCValXdr.Vec(SCVecXdr(scValues))
    }

    /**
     * Handle enum type conversion
     */
    private fun handleEnumType(value: Any?, enumDef: SCSpecUDTEnumV0Xdr): SCValXdr {
        val enumValue: UInt = when (value) {
            is Int -> value.toUInt()
            is UInt -> value
            is Long -> value.toUInt()
            is ULong -> value.toUInt()
            is String -> {
                // Find enum case by name
                val enumCase = enumDef.cases.firstOrNull { it.name == value }
                    ?: throw ContractSpecException.invalidEnumValue(
                        "Unknown enum case \"$value\" for enum ${enumDef.name}"
                    )
                enumCase.value.value
            }
            else -> throw ContractSpecException.invalidType(
                "Expected Int or String for enum ${enumDef.name}, got ${value?.let { it::class.simpleName } ?: "null"}"
            )
        }

        // Validate enum value
        val validValues = enumDef.cases.map { it.value.value }.toSet()
        if (!validValues.contains(enumValue)) {
            throw ContractSpecException.invalidEnumValue(
                "Invalid enum value $enumValue for enum ${enumDef.name}"
            )
        }

        return SCValXdr.U32(Uint32Xdr(enumValue))
    }

    /**
     * Check if a string represents a numeric value
     */
    private fun isNumericString(str: String): Boolean {
        return str.toIntOrNull() != null
    }

    /**
     * Convert hex string to bytes
     */
    private fun hexToBytes(hex: String): ByteArray {
        val cleanHex = hex.removePrefix("0x").replace(" ", "")
        require(cleanHex.length % 2 == 0) { "Hex string must have even length" }

        return ByteArray(cleanHex.length / 2) { i ->
            cleanHex.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
    }
}
