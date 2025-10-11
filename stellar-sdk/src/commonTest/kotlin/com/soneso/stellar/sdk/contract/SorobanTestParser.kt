// Copyright 2024 Soneso. All rights reserved.
// Use of this source code is governed by a license that can be
// found in the LICENSE file.

package com.soneso.stellar.sdk.contract

import com.soneso.stellar.sdk.xdr.*

/**
 * Helper functions for testing Soroban contract parser functionality.
 * Provides utilities to format and display parsed contract information.
 */
object SorobanTestParser {

    /**
     * Gets a human-readable description of an SCSpecTypeDef.
     *
     * @param specType The type definition to describe
     * @return A string describing the type
     */
    fun getSpecTypeInfo(specType: SCSpecTypeDefXdr): String {
        return when (specType.discriminant) {
            SCSpecTypeXdr.SC_SPEC_TYPE_VAL -> "val"
            SCSpecTypeXdr.SC_SPEC_TYPE_BOOL -> "bool"
            SCSpecTypeXdr.SC_SPEC_TYPE_VOID -> "void"
            SCSpecTypeXdr.SC_SPEC_TYPE_ERROR -> "error"
            SCSpecTypeXdr.SC_SPEC_TYPE_U32 -> "u32"
            SCSpecTypeXdr.SC_SPEC_TYPE_I32 -> "i32"
            SCSpecTypeXdr.SC_SPEC_TYPE_U64 -> "u64"
            SCSpecTypeXdr.SC_SPEC_TYPE_I64 -> "i64"
            SCSpecTypeXdr.SC_SPEC_TYPE_TIMEPOINT -> "timepoint"
            SCSpecTypeXdr.SC_SPEC_TYPE_DURATION -> "duration"
            SCSpecTypeXdr.SC_SPEC_TYPE_U128 -> "u128"
            SCSpecTypeXdr.SC_SPEC_TYPE_I128 -> "i128"
            SCSpecTypeXdr.SC_SPEC_TYPE_U256 -> "u256"
            SCSpecTypeXdr.SC_SPEC_TYPE_I256 -> "i256"
            SCSpecTypeXdr.SC_SPEC_TYPE_BYTES -> "bytes"
            SCSpecTypeXdr.SC_SPEC_TYPE_STRING -> "string"
            SCSpecTypeXdr.SC_SPEC_TYPE_SYMBOL -> "symbol"
            SCSpecTypeXdr.SC_SPEC_TYPE_ADDRESS -> "address"
            SCSpecTypeXdr.SC_SPEC_TYPE_MUXED_ADDRESS -> "muxed address"
            SCSpecTypeXdr.SC_SPEC_TYPE_OPTION -> {
                val optionType = when (specType) {
                    is SCSpecTypeDefXdr.Option -> specType.value
                    else -> null
                }
                val valueType = optionType?.valueType?.let { getSpecTypeInfo(it) } ?: "unknown"
                "option (value type: $valueType)"
            }
            SCSpecTypeXdr.SC_SPEC_TYPE_RESULT -> {
                val resultType = when (specType) {
                    is SCSpecTypeDefXdr.Result -> specType.value
                    else -> null
                }
                val okType = resultType?.okType?.let { getSpecTypeInfo(it) } ?: "unknown"
                val errorType = resultType?.errorType?.let { getSpecTypeInfo(it) } ?: "unknown"
                "result (ok type: $okType, error type: $errorType)"
            }
            SCSpecTypeXdr.SC_SPEC_TYPE_VEC -> {
                val vecType = when (specType) {
                    is SCSpecTypeDefXdr.Vec -> specType.value
                    else -> null
                }
                val elementType = vecType?.elementType?.let { getSpecTypeInfo(it) } ?: "unknown"
                "vec (element type: $elementType)"
            }
            SCSpecTypeXdr.SC_SPEC_TYPE_MAP -> {
                val mapType = when (specType) {
                    is SCSpecTypeDefXdr.Map -> specType.value
                    else -> null
                }
                val keyType = mapType?.keyType?.let { getSpecTypeInfo(it) } ?: "unknown"
                val valueType = mapType?.valueType?.let { getSpecTypeInfo(it) } ?: "unknown"
                "map (key type: $keyType, value type: $valueType)"
            }
            SCSpecTypeXdr.SC_SPEC_TYPE_TUPLE -> {
                val tupleType = when (specType) {
                    is SCSpecTypeDefXdr.Tuple -> specType.value
                    else -> null
                }
                val valueTypesStr = tupleType?.valueTypes?.joinToString(", ") { getSpecTypeInfo(it) } ?: ""
                "tuple (value types: [$valueTypesStr])"
            }
            SCSpecTypeXdr.SC_SPEC_TYPE_BYTES_N -> {
                val bytesNType = when (specType) {
                    is SCSpecTypeDefXdr.BytesN -> specType.value
                    else -> null
                }
                "bytesN (n: ${bytesNType?.n})"
            }
            SCSpecTypeXdr.SC_SPEC_TYPE_UDT -> {
                val udtType = when (specType) {
                    is SCSpecTypeDefXdr.Udt -> specType.value
                    else -> null
                }
                "udt (name: ${udtType?.name ?: "unknown"})"
            }
            else -> "unknown"
        }
    }

    /**
     * Formats a function specification entry for display.
     *
     * @param function The function specification to format
     * @return A formatted string describing the function
     */
    fun printFunction(function: SCSpecFunctionV0Xdr): String {
        val sb = StringBuilder()
        sb.appendLine("Function: ${function.name.value}")

        function.inputs.forEachIndexed { index, input ->
            sb.appendLine("  input[$index] name: ${input.name}")
            sb.appendLine("  input[$index] type: ${getSpecTypeInfo(input.type)}")
            if (input.doc.isNotEmpty()) {
                sb.appendLine("  input[$index] doc: ${input.doc}")
            }
        }

        function.outputs.forEachIndexed { index, output ->
            sb.appendLine("  output[$index] type: ${getSpecTypeInfo(output)}")
        }

        if (function.doc.isNotEmpty()) {
            sb.appendLine("  doc: ${function.doc}")
        }

        return sb.toString()
    }

    /**
     * Formats a UDT struct specification entry for display.
     *
     * @param udtStruct The UDT struct specification to format
     * @return A formatted string describing the struct
     */
    fun printUdtStruct(udtStruct: SCSpecUDTStructV0Xdr): String {
        val sb = StringBuilder()
        sb.appendLine("UDT Struct: ${udtStruct.name}")

        if (udtStruct.lib.isNotEmpty()) {
            sb.appendLine("  lib: ${udtStruct.lib}")
        }

        udtStruct.fields.forEachIndexed { index, field ->
            sb.appendLine("  field[$index] name: ${field.name}")
            sb.appendLine("  field[$index] type: ${getSpecTypeInfo(field.type)}")
            if (field.doc.isNotEmpty()) {
                sb.appendLine("  field[$index] doc: ${field.doc}")
            }
        }

        if (udtStruct.doc.isNotEmpty()) {
            sb.appendLine("  doc: ${udtStruct.doc}")
        }

        return sb.toString()
    }

    /**
     * Formats a UDT union specification entry for display.
     *
     * @param udtUnion The UDT union specification to format
     * @return A formatted string describing the union
     */
    fun printUdtUnion(udtUnion: SCSpecUDTUnionV0Xdr): String {
        val sb = StringBuilder()
        sb.appendLine("UDT Union: ${udtUnion.name}")

        if (udtUnion.lib.isNotEmpty()) {
            sb.appendLine("  lib: ${udtUnion.lib}")
        }

        udtUnion.cases.forEachIndexed { index, uCase ->
            when (uCase.discriminant) {
                SCSpecUDTUnionCaseV0KindXdr.SC_SPEC_UDT_UNION_CASE_VOID_V0 -> {
                    val voidCase = when (uCase) {
                        is SCSpecUDTUnionCaseV0Xdr.VoidCase -> uCase.value
                        else -> null
                    }
                    sb.appendLine("  case[$index] is voidV0")
                    sb.appendLine("  case[$index] name: ${voidCase?.name ?: "unknown"}")
                    if (voidCase?.doc?.isNotEmpty() == true) {
                        sb.appendLine("  case[$index] doc: ${voidCase.doc}")
                    }
                }
                SCSpecUDTUnionCaseV0KindXdr.SC_SPEC_UDT_UNION_CASE_TUPLE_V0 -> {
                    val tupleCase = when (uCase) {
                        is SCSpecUDTUnionCaseV0Xdr.TupleCase -> uCase.value
                        else -> null
                    }
                    sb.appendLine("  case[$index] is tupleV0")
                    sb.appendLine("  case[$index] name: ${tupleCase?.name ?: "unknown"}")
                    val valueTypesStr = tupleCase?.type?.joinToString(", ") { getSpecTypeInfo(it) } ?: ""
                    sb.appendLine("  case[$index] types: [$valueTypesStr]")
                    if (tupleCase?.doc?.isNotEmpty() == true) {
                        sb.appendLine("  case[$index] doc: ${tupleCase.doc}")
                    }
                }
            }
        }

        if (udtUnion.doc.isNotEmpty()) {
            sb.appendLine("  doc: ${udtUnion.doc}")
        }

        return sb.toString()
    }

    /**
     * Formats a UDT enum specification entry for display.
     *
     * @param udtEnum The UDT enum specification to format
     * @return A formatted string describing the enum
     */
    fun printUdtEnum(udtEnum: SCSpecUDTEnumV0Xdr): String {
        val sb = StringBuilder()
        sb.appendLine("UDT Enum: ${udtEnum.name}")

        if (udtEnum.lib.isNotEmpty()) {
            sb.appendLine("  lib: ${udtEnum.lib}")
        }

        udtEnum.cases.forEachIndexed { index, uCase ->
            sb.appendLine("  case[$index] name: ${uCase.name}")
            sb.appendLine("  case[$index] value: ${uCase.value}")
            if (uCase.doc.isNotEmpty()) {
                sb.appendLine("  case[$index] doc: ${uCase.doc}")
            }
        }

        if (udtEnum.doc.isNotEmpty()) {
            sb.appendLine("  doc: ${udtEnum.doc}")
        }

        return sb.toString()
    }

    /**
     * Formats a UDT error enum specification entry for display.
     *
     * @param udtErrorEnum The UDT error enum specification to format
     * @return A formatted string describing the error enum
     */
    fun printUdtErrorEnum(udtErrorEnum: SCSpecUDTErrorEnumV0Xdr): String {
        val sb = StringBuilder()
        sb.appendLine("UDT Error Enum: ${udtErrorEnum.name}")

        if (udtErrorEnum.lib.isNotEmpty()) {
            sb.appendLine("  lib: ${udtErrorEnum.lib}")
        }

        udtErrorEnum.cases.forEachIndexed { index, uCase ->
            sb.appendLine("  case[$index] name: ${uCase.name}")
            sb.appendLine("  case[$index] value: ${uCase.value}")
            if (uCase.doc.isNotEmpty()) {
                sb.appendLine("  case[$index] doc: ${uCase.doc}")
            }
        }

        if (udtErrorEnum.doc.isNotEmpty()) {
            sb.appendLine("  doc: ${udtErrorEnum.doc}")
        }

        return sb.toString()
    }

    /**
     * Formats an event specification entry for display.
     *
     * @param event The event specification to format
     * @return A formatted string describing the event
     */
    fun printEvent(event: SCSpecEventV0Xdr): String {
        val sb = StringBuilder()
        sb.appendLine("Event: ${event.name.value}")
        sb.appendLine("  lib: ${event.lib}")

        event.prefixTopics.forEachIndexed { index, prefixTopic ->
            sb.appendLine("  prefixTopic[$index]: $prefixTopic")
        }

        event.params.forEachIndexed { index, param ->
            sb.appendLine("  param[$index] name: ${param.name}")
            if (param.doc.isNotEmpty()) {
                sb.appendLine("  param[$index] doc: ${param.doc}")
            }
            sb.appendLine("  param[$index] type: ${getSpecTypeInfo(param.type)}")

            val locationStr = when (param.location.value) {
                SCSpecEventParamLocationV0Xdr.SC_SPEC_EVENT_PARAM_LOCATION_DATA.value -> "data"
                SCSpecEventParamLocationV0Xdr.SC_SPEC_EVENT_PARAM_LOCATION_TOPIC_LIST.value -> "topic list"
                else -> "unknown"
            }
            sb.appendLine("  param[$index] location: $locationStr")
        }

        val dataFormatStr = when (event.dataFormat.value) {
            SCSpecEventDataFormatXdr.SC_SPEC_EVENT_DATA_FORMAT_SINGLE_VALUE.value -> "single value"
            SCSpecEventDataFormatXdr.SC_SPEC_EVENT_DATA_FORMAT_MAP.value -> "map"
            SCSpecEventDataFormatXdr.SC_SPEC_EVENT_DATA_FORMAT_VEC.value -> "vec"
            else -> "unknown"
        }
        sb.appendLine("  data format: $dataFormatStr")

        if (event.doc.isNotEmpty()) {
            sb.appendLine("  doc: ${event.doc}")
        }

        return sb.toString()
    }

    /**
     * Formats a complete contract info for display.
     *
     * @param contractInfo The contract info to format
     * @return A formatted string describing the entire contract
     */
    fun printContractInfo(contractInfo: SorobanContractInfo): String {
        val sb = StringBuilder()

        sb.appendLine("--------------------------------")
        sb.appendLine("Env Meta:")
        sb.appendLine("")
        sb.appendLine("Interface version: ${contractInfo.envInterfaceVersion}")
        sb.appendLine("--------------------------------")
        sb.appendLine("Contract Meta:")
        sb.appendLine("")

        contractInfo.metaEntries.forEach { (key, value) ->
            sb.appendLine("$key: $value")
        }

        sb.appendLine("--------------------------------")
        sb.appendLine("Contract Spec:")
        sb.appendLine("")

        contractInfo.specEntries.forEachIndexed { index, specEntry ->
            when (specEntry.discriminant) {
                SCSpecEntryKindXdr.SC_SPEC_ENTRY_FUNCTION_V0 -> {
                    val function = when (specEntry) {
                        is SCSpecEntryXdr.FunctionV0 -> specEntry.value
                        else -> null
                    }
                    function?.let { sb.append(printFunction(it)) }
                }
                SCSpecEntryKindXdr.SC_SPEC_ENTRY_UDT_STRUCT_V0 -> {
                    val udtStruct = when (specEntry) {
                        is SCSpecEntryXdr.UdtStructV0 -> specEntry.value
                        else -> null
                    }
                    udtStruct?.let { sb.append(printUdtStruct(it)) }
                }
                SCSpecEntryKindXdr.SC_SPEC_ENTRY_UDT_UNION_V0 -> {
                    val udtUnion = when (specEntry) {
                        is SCSpecEntryXdr.UdtUnionV0 -> specEntry.value
                        else -> null
                    }
                    udtUnion?.let { sb.append(printUdtUnion(it)) }
                }
                SCSpecEntryKindXdr.SC_SPEC_ENTRY_UDT_ENUM_V0 -> {
                    val udtEnum = when (specEntry) {
                        is SCSpecEntryXdr.UdtEnumV0 -> specEntry.value
                        else -> null
                    }
                    udtEnum?.let { sb.append(printUdtEnum(it)) }
                }
                SCSpecEntryKindXdr.SC_SPEC_ENTRY_UDT_ERROR_ENUM_V0 -> {
                    val udtErrorEnum = when (specEntry) {
                        is SCSpecEntryXdr.UdtErrorEnumV0 -> specEntry.value
                        else -> null
                    }
                    udtErrorEnum?.let { sb.append(printUdtErrorEnum(it)) }
                }
                SCSpecEntryKindXdr.SC_SPEC_ENTRY_EVENT_V0 -> {
                    val event = when (specEntry) {
                        is SCSpecEntryXdr.EventV0 -> specEntry.value
                        else -> null
                    }
                    event?.let { sb.append(printEvent(it)) }
                }
                else -> {
                    sb.appendLine("specEntry [$index] -> kind(${specEntry.discriminant.value}): unknown")
                }
            }
            sb.appendLine("")
        }

        sb.appendLine("--------------------------------")

        return sb.toString()
    }
}
