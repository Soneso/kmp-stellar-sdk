package com.soneso.demo.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.soneso.demo.stellar.ContractDetailsResult
import com.soneso.demo.stellar.fetchContractDetails
import com.soneso.demo.ui.components.AnimatedButton
import com.soneso.demo.ui.components.InfoCardMediumTitle
import com.soneso.demo.ui.components.StellarTopBar
import com.soneso.demo.util.StellarValidation
import com.soneso.stellar.sdk.contract.SorobanContractInfo
import com.soneso.stellar.sdk.xdr.*
import kotlinx.coroutines.launch

class ContractDetailsScreen : Screen {
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val coroutineScope = rememberCoroutineScope()

        // State management
        var contractId by remember { mutableStateOf("CBNCMQU5VCEVFASCPT4CCQX2LGYJK6YZ7LOIZLRXDEVJYQB7K6UTQNWW") }
        var isLoading by remember { mutableStateOf(false) }
        var detailsResult by remember { mutableStateOf<ContractDetailsResult?>(null) }
        var validationError by remember { mutableStateOf<String?>(null) }

        val snackbarHostState = remember { SnackbarHostState() }

        // Function to fetch contract details
        fun fetchDetails() {
            val error = StellarValidation.validateContractId(contractId)
            if (error != null) {
                validationError = error
            } else {
                coroutineScope.launch {
                    isLoading = true
                    detailsResult = null
                    try {
                        detailsResult = fetchContractDetails(contractId)
                    } finally {
                        isLoading = false
                    }
                }
            }
        }

        Scaffold(
            topBar = {
                StellarTopBar(
                    title = "Fetch Smart Contract Details",
                    onNavigationClick = { navigator.pop() }
                )
            },
            snackbarHost = { SnackbarHost(snackbarHostState) }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.TopCenter
            ) {
                Column(
                    modifier = Modifier
                        .widthIn(max = 800.dp)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                    ) {
                // Information card - Purple
                InfoCardMediumTitle(
                    modifier = Modifier.fillMaxWidth(),
                    title = "Soroban RPC: Fetch and Parse Smart Contract Details",
                    description = "Enter a contract ID to fetch its WASM bytecode from the network and parse the contract specification including metadata and function definitions. The field is pre-filled with a testnet contract ID."
                )

                // Contract ID Input Field - Gold
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFFFFFBF0) // Starlight Gold background
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = "Contract Configuration",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                lineHeight = 22.sp
                            ),
                            color = Color(0xFFA85A00) // Starlight Gold Dark
                        )

                        OutlinedTextField(
                            value = contractId,
                            onValueChange = {
                                contractId = it.trim()
                                validationError = null
                                detailsResult = null
                            },
                            label = { Text("Contract ID") },
                            placeholder = { Text("C...") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            isError = validationError != null,
                            supportingText = validationError?.let { error ->
                                {
                                    Text(
                                        text = error,
                                        color = Color(0xFF991B1B) // Nova Red
                                    )
                                }
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFFD97706), // Starlight Gold
                                focusedLabelColor = Color(0xFFD97706),
                                cursorColor = Color(0xFFD97706)
                            ),
                            keyboardOptions = KeyboardOptions(
                                imeAction = ImeAction.Done
                            ),
                            keyboardActions = KeyboardActions(
                                onDone = { fetchDetails() }
                            )
                        )
                    }
                }

                // Submit button
                AnimatedButton(
                    onClick = { fetchDetails() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    enabled = !isLoading && contractId.isNotBlank(),
                    isLoading = isLoading,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF0A4FD6), // Stellar Blue
                        contentColor = Color.White
                    )
                ) {
                    if (!isLoading) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = Color.White
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(
                        if (isLoading) "Fetching..." else "Fetch Contract Details",
                        fontWeight = FontWeight.Bold
                    )
                }

                // Result display
                detailsResult?.let { result ->
                    when (result) {
                        is ContractDetailsResult.Success -> {
                            ContractInfoCards(result.contractInfo)
                        }
                        is ContractDetailsResult.Error -> {
                            ErrorCard(result)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Converts an XDR spec type definition to a human-readable string representation.
 * Handles all type cases recursively including primitives, collections, and user-defined types.
 */
private fun getSpecTypeInfo(specType: SCSpecTypeDefXdr): String {
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
            val valueType = getSpecTypeInfo((specType as SCSpecTypeDefXdr.Option).value.valueType)
            "option (value type: $valueType)"
        }
        SCSpecTypeXdr.SC_SPEC_TYPE_RESULT -> {
            val resultType = (specType as SCSpecTypeDefXdr.Result).value
            val okType = getSpecTypeInfo(resultType.okType)
            val errorType = getSpecTypeInfo(resultType.errorType)
            "result (ok type: $okType, error type: $errorType)"
        }
        SCSpecTypeXdr.SC_SPEC_TYPE_VEC -> {
            val elementType = getSpecTypeInfo((specType as SCSpecTypeDefXdr.Vec).value.elementType)
            "vec (element type: $elementType)"
        }
        SCSpecTypeXdr.SC_SPEC_TYPE_MAP -> {
            val mapType = (specType as SCSpecTypeDefXdr.Map).value
            val keyType = getSpecTypeInfo(mapType.keyType)
            val valueType = getSpecTypeInfo(mapType.valueType)
            "map (key type: $keyType, value type: $valueType)"
        }
        SCSpecTypeXdr.SC_SPEC_TYPE_TUPLE -> {
            val valueTypes = (specType as SCSpecTypeDefXdr.Tuple).value.valueTypes
            val valueTypesStr = valueTypes.joinToString(", ") { getSpecTypeInfo(it) }
            "tuple (value types: [$valueTypesStr])"
        }
        SCSpecTypeXdr.SC_SPEC_TYPE_BYTES_N -> {
            val n = (specType as SCSpecTypeDefXdr.BytesN).value.n.value
            "bytesN (n: $n)"
        }
        SCSpecTypeXdr.SC_SPEC_TYPE_UDT -> {
            val name = (specType as SCSpecTypeDefXdr.Udt).value.name
            "udt (name: $name)"
        }
        else -> "unknown"
    }
}

@Composable
private fun ContractInfoCards(contractInfo: SorobanContractInfo) {
    // Success header card - Teal
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFF0FDFA) // Nebula Teal Container
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                modifier = Modifier.size(28.dp),
                tint = Color(0xFF0F766E) // Nebula Teal Dark
            )
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "Success",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        lineHeight = 22.sp
                    ),
                    color = Color(0xFF0F766E)
                )
                Text(
                    text = "Contract details fetched and parsed successfully",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        lineHeight = 22.sp
                    ),
                    color = Color(0xFF0F766E).copy(alpha = 0.8f)
                )
            }
        }
    }

    // Contract Metadata Card
    ContractMetadataCard(contractInfo)

    // Contract Spec Entries Card
    ContractSpecEntriesCard(contractInfo.specEntries)
}

@Composable
private fun ContractMetadataCard(contractInfo: SorobanContractInfo) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFE8F1FF) // Nebula Blue
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    width = 1.dp,
                    color = Color(0xFF0A4FD6).copy(alpha = 0.2f),
                    shape = RoundedCornerShape(16.dp)
                )
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Contract Metadata",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    lineHeight = 22.sp
                ),
                color = Color(0xFF0639A3) // Stellar Blue Dark
            )
            HorizontalDivider(color = Color(0xFF0A4FD6).copy(alpha = 0.2f))

            // Environment Interface Version
            DetailRow(
                label = "Environment Interface Version",
                value = contractInfo.envInterfaceVersion.toString()
            )

            // Meta entries
            if (contractInfo.metaEntries.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Meta Entries (${contractInfo.metaEntries.size})",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = Color(0xFF0639A3).copy(alpha = 0.7f)
                )
                SelectionContainer {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        contractInfo.metaEntries.forEach { (key, value) ->
                            DetailRow(label = key, value = value, monospace = true)
                        }
                    }
                }
            } else {
                DetailRow(
                    label = "Meta Entries",
                    value = "None"
                )
            }
        }
    }
}

@Composable
private fun ContractSpecEntriesCard(specEntries: List<SCSpecEntryXdr>) {
    // Sort spec entries by type
    val sortedEntries = specEntries.sortedBy { entry ->
        when (entry) {
            is SCSpecEntryXdr.FunctionV0 -> 0
            is SCSpecEntryXdr.UdtStructV0 -> 1
            is SCSpecEntryXdr.UdtUnionV0 -> 2
            is SCSpecEntryXdr.UdtEnumV0 -> 3
            is SCSpecEntryXdr.UdtErrorEnumV0 -> 4
            is SCSpecEntryXdr.EventV0 -> 5
        }
    }

    // Track which entries are expanded
    var expandedEntries by remember { mutableStateOf(setOf<Int>()) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFE8F1FF) // Nebula Blue
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    width = 1.dp,
                    color = Color(0xFF0A4FD6).copy(alpha = 0.2f),
                    shape = RoundedCornerShape(16.dp)
                )
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Contract Spec Entries (${specEntries.size})",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    lineHeight = 22.sp
                ),
                color = Color(0xFF0639A3) // Stellar Blue Dark
            )
            Text(
                text = "Click on an entry to view details",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    lineHeight = 22.sp
                ),
                color = Color(0xFF0639A3).copy(alpha = 0.7f)
            )
            HorizontalDivider(color = Color(0xFF0A4FD6).copy(alpha = 0.2f))

            if (sortedEntries.isEmpty()) {
                Text(
                    text = "No spec entries found",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        lineHeight = 22.sp
                    ),
                    color = Color(0xFF0639A3).copy(alpha = 0.7f),
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            } else {
                sortedEntries.forEachIndexed { index, entry ->
                    val isExpanded = expandedEntries.contains(index)

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                expandedEntries = if (isExpanded) {
                                    expandedEntries - index
                                } else {
                                    expandedEntries + index
                                }
                            },
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Entry header (always visible)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = getEntryTitle(entry),
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = getEntrySummary(entry),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                    )
                                }
                                Icon(
                                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = if (isExpanded) "Collapse" else "Expand"
                                )
                            }

                            // Expandable entry details
                            AnimatedVisibility(
                                visible = isExpanded,
                                enter = fadeIn() + expandVertically(),
                                exit = fadeOut() + shrinkVertically()
                            ) {
                                Column(
                                    modifier = Modifier.padding(top = 8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    HorizontalDivider()
                                    SpecEntryDetails(entry)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Gets the title for a spec entry based on its type.
 */
private fun getEntryTitle(entry: SCSpecEntryXdr): String {
    return when (entry) {
        is SCSpecEntryXdr.FunctionV0 -> "Function: ${entry.value.name.value}"
        is SCSpecEntryXdr.UdtStructV0 -> "Struct: ${entry.value.name}"
        is SCSpecEntryXdr.UdtUnionV0 -> "Union: ${entry.value.name}"
        is SCSpecEntryXdr.UdtEnumV0 -> "Enum: ${entry.value.name}"
        is SCSpecEntryXdr.UdtErrorEnumV0 -> "Error Enum: ${entry.value.name}"
        is SCSpecEntryXdr.EventV0 -> "Event: ${entry.value.name.value}"
    }
}

/**
 * Gets a summary for a spec entry when collapsed.
 */
private fun getEntrySummary(entry: SCSpecEntryXdr): String {
    return when (entry) {
        is SCSpecEntryXdr.FunctionV0 -> "Inputs: ${entry.value.inputs.size}, Outputs: ${entry.value.outputs.size}"
        is SCSpecEntryXdr.UdtStructV0 -> "Fields: ${entry.value.fields.size}"
        is SCSpecEntryXdr.UdtUnionV0 -> "Cases: ${entry.value.cases.size}"
        is SCSpecEntryXdr.UdtEnumV0 -> "Cases: ${entry.value.cases.size}"
        is SCSpecEntryXdr.UdtErrorEnumV0 -> "Cases: ${entry.value.cases.size}"
        is SCSpecEntryXdr.EventV0 -> "Prefix Topics: ${entry.value.prefixTopics.size}, Params: ${entry.value.params.size}"
    }
}

/**
 * Displays detailed information about a spec entry.
 */
@Composable
private fun SpecEntryDetails(entry: SCSpecEntryXdr) {
    when (entry) {
        is SCSpecEntryXdr.FunctionV0 -> FunctionSpecDetails(entry.value)
        is SCSpecEntryXdr.UdtStructV0 -> StructSpecDetails(entry.value)
        is SCSpecEntryXdr.UdtUnionV0 -> UnionSpecDetails(entry.value)
        is SCSpecEntryXdr.UdtEnumV0 -> EnumSpecDetails(entry.value)
        is SCSpecEntryXdr.UdtErrorEnumV0 -> ErrorEnumSpecDetails(entry.value)
        is SCSpecEntryXdr.EventV0 -> EventSpecDetails(entry.value)
    }
}

@Composable
private fun FunctionSpecDetails(function: SCSpecFunctionV0Xdr) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (function.doc.isNotEmpty()) {
            Text(
                text = function.doc,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }

        // Input parameters
        if (function.inputs.isNotEmpty()) {
            Text(
                text = "Inputs (${function.inputs.size}):",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            function.inputs.forEachIndexed { index, input ->
                Column(
                    modifier = Modifier.padding(start = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = "[$index] ${input.name}",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Type: ${getSpecTypeInfo(input.type)}",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )
                    if (input.doc.isNotEmpty()) {
                        Text(
                            text = "Doc: ${input.doc}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        } else {
            Text(
                text = "Inputs: None",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Output types
        if (function.outputs.isNotEmpty()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Outputs (${function.outputs.size}):",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            function.outputs.forEachIndexed { index, output ->
                Text(
                    text = "[$index] ${getSpecTypeInfo(output)}",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    modifier = Modifier.padding(start = 12.dp)
                )
            }
        } else {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Outputs: None (void)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun StructSpecDetails(struct: SCSpecUDTStructV0Xdr) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (struct.doc.isNotEmpty()) {
            Text(
                text = struct.doc,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
        if (struct.lib.isNotEmpty()) {
            Text(
                text = "Lib: ${struct.lib}",
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
            )
        }

        // Fields
        if (struct.fields.isNotEmpty()) {
            Text(
                text = "Fields (${struct.fields.size}):",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            struct.fields.forEachIndexed { index, field ->
                Column(
                    modifier = Modifier.padding(start = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = "[$index] ${field.name}",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Type: ${getSpecTypeInfo(field.type)}",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )
                    if (field.doc.isNotEmpty()) {
                        Text(
                            text = "Doc: ${field.doc}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        } else {
            Text(
                text = "Fields: None",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun UnionSpecDetails(union: SCSpecUDTUnionV0Xdr) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (union.doc.isNotEmpty()) {
            Text(
                text = union.doc,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
        if (union.lib.isNotEmpty()) {
            Text(
                text = "Lib: ${union.lib}",
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
            )
        }

        // Cases
        if (union.cases.isNotEmpty()) {
            Text(
                text = "Cases (${union.cases.size}):",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            union.cases.forEachIndexed { index, uCase ->
                Column(
                    modifier = Modifier.padding(start = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    when (uCase) {
                        is SCSpecUDTUnionCaseV0Xdr.VoidCase -> {
                            Text(
                                text = "[$index] ${uCase.value.name} (void)",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (uCase.value.doc.isNotEmpty()) {
                                Text(
                                    text = "Doc: ${uCase.value.doc}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                )
                            }
                        }
                        is SCSpecUDTUnionCaseV0Xdr.TupleCase -> {
                            Text(
                                text = "[$index] ${uCase.value.name} (tuple)",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            val types = uCase.value.type.joinToString(", ") { getSpecTypeInfo(it) }
                            Text(
                                text = "Types: [$types]",
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                            )
                            if (uCase.value.doc.isNotEmpty()) {
                                Text(
                                    text = "Doc: ${uCase.value.doc}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }
                }
            }
        } else {
            Text(
                text = "Cases: None",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun EnumSpecDetails(enum: SCSpecUDTEnumV0Xdr) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (enum.doc.isNotEmpty()) {
            Text(
                text = enum.doc,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
        if (enum.lib.isNotEmpty()) {
            Text(
                text = "Lib: ${enum.lib}",
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
            )
        }

        // Cases
        if (enum.cases.isNotEmpty()) {
            Text(
                text = "Cases (${enum.cases.size}):",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            enum.cases.forEachIndexed { index, enumCase ->
                Column(
                    modifier = Modifier.padding(start = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = "[$index] ${enumCase.name}",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Value: ${enumCase.value}",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )
                    if (enumCase.doc.isNotEmpty()) {
                        Text(
                            text = "Doc: ${enumCase.doc}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        } else {
            Text(
                text = "Cases: None",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ErrorEnumSpecDetails(errorEnum: SCSpecUDTErrorEnumV0Xdr) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (errorEnum.doc.isNotEmpty()) {
            Text(
                text = errorEnum.doc,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
        if (errorEnum.lib.isNotEmpty()) {
            Text(
                text = "Lib: ${errorEnum.lib}",
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
            )
        }

        // Cases
        if (errorEnum.cases.isNotEmpty()) {
            Text(
                text = "Cases (${errorEnum.cases.size}):",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            errorEnum.cases.forEachIndexed { index, errorCase ->
                Column(
                    modifier = Modifier.padding(start = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = "[$index] ${errorCase.name}",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Value: ${errorCase.value}",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )
                    if (errorCase.doc.isNotEmpty()) {
                        Text(
                            text = "Doc: ${errorCase.doc}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        } else {
            Text(
                text = "Cases: None",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun EventSpecDetails(event: SCSpecEventV0Xdr) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (event.doc.isNotEmpty()) {
            Text(
                text = event.doc,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
        Text(
            text = "Lib: ${event.lib}",
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
        )

        // Prefix Topics
        if (event.prefixTopics.isNotEmpty()) {
            Text(
                text = "Prefix Topics (${event.prefixTopics.size}):",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            event.prefixTopics.forEachIndexed { index, topic ->
                Text(
                    text = "[$index] $topic",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    modifier = Modifier.padding(start = 12.dp)
                )
            }
        } else {
            Text(
                text = "Prefix Topics: None",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Parameters
        if (event.params.isNotEmpty()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Params (${event.params.size}):",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            event.params.forEachIndexed { index, param ->
                Column(
                    modifier = Modifier.padding(start = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = "[$index] ${param.name}",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Type: ${getSpecTypeInfo(param.type)}",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )
                    val location = when (param.location) {
                        SCSpecEventParamLocationV0Xdr.SC_SPEC_EVENT_PARAM_LOCATION_DATA -> "data"
                        SCSpecEventParamLocationV0Xdr.SC_SPEC_EVENT_PARAM_LOCATION_TOPIC_LIST -> "topic list"
                        else -> "unknown"
                    }
                    Text(
                        text = "Location: $location",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )
                    if (param.doc.isNotEmpty()) {
                        Text(
                            text = "Doc: ${param.doc}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        } else {
            Text(
                text = "Params: None",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Data format
        Spacer(modifier = Modifier.height(4.dp))
        val dataFormat = when (event.dataFormat) {
            SCSpecEventDataFormatXdr.SC_SPEC_EVENT_DATA_FORMAT_SINGLE_VALUE -> "single value"
            SCSpecEventDataFormatXdr.SC_SPEC_EVENT_DATA_FORMAT_MAP -> "map"
            SCSpecEventDataFormatXdr.SC_SPEC_EVENT_DATA_FORMAT_VEC -> "vec"
            else -> "unknown"
        }
        Text(
            text = "Data Format: $dataFormat",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun DetailRow(
    label: String,
    value: String,
    monospace: Boolean = false
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = FontWeight.Bold
            ),
            color = Color(0xFF0639A3).copy(alpha = 0.7f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.SemiBold,
                lineHeight = 22.sp
            ),
            fontFamily = if (monospace) FontFamily.Monospace else FontFamily.Default,
            color = Color(0xFF0639A3)
        )
    }
}

@Composable
private fun ErrorCard(error: ContractDetailsResult.Error) {
    // Error header card - Red
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFFEF2F2) // Nova Red Container
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    width = 1.dp,
                    color = Color(0xFF991B1B).copy(alpha = 0.3f),
                    shape = RoundedCornerShape(16.dp)
                )
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Error,
                    contentDescription = null,
                    modifier = Modifier.size(28.dp),
                    tint = Color(0xFF991B1B) // Nova Red Dark
                )
                Text(
                    text = "Error",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        lineHeight = 22.sp
                    ),
                    color = Color(0xFF991B1B)
                )
            }
            Text(
                text = error.message,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    lineHeight = 22.sp
                ),
                color = Color(0xFF991B1B).copy(alpha = 0.9f)
            )

            // Technical details in nested white card
            error.exception?.let { exception ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = Color.White
                    ),
                    shape = RoundedCornerShape(12.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    SelectionContainer {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "Technical Details",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Bold
                                ),
                                color = Color(0xFF991B1B).copy(alpha = 0.7f)
                            )
                            Text(
                                text = exception.message ?: "Unknown error",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    lineHeight = 20.sp
                                ),
                                fontFamily = FontFamily.Monospace,
                                color = Color(0xFF991B1B)
                            )
                        }
                    }
                }
            }
        }
    }

    // Troubleshooting tips - Purple
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFF3EFFF) // Stardust Purple
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    width = 1.dp,
                    color = Color(0xFF5E3FBE).copy(alpha = 0.2f),
                    shape = RoundedCornerShape(16.dp)
                )
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Troubleshooting",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    lineHeight = 22.sp
                ),
                color = Color(0xFF3D2373) // Cosmic Purple Dark
            )
            Column(
                modifier = Modifier.padding(start = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "• Verify the contract ID is valid (starts with 'C' and is 56 characters)",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        lineHeight = 22.sp
                    ),
                    color = Color(0xFF3D2373).copy(alpha = 0.8f)
                )
                Text(
                    text = "• Make sure the contract exists on testnet and has been deployed",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        lineHeight = 22.sp
                    ),
                    color = Color(0xFF3D2373).copy(alpha = 0.8f)
                )
                Text(
                    text = "• Check your internet connection",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        lineHeight = 22.sp
                    ),
                    color = Color(0xFF3D2373).copy(alpha = 0.8f)
                )
                Text(
                    text = "• Try again in a moment if you're being rate-limited",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        lineHeight = 22.sp
                    ),
                    color = Color(0xFF3D2373).copy(alpha = 0.8f)
                )
            }
                }
        }
    }
}
