package com.soneso.demo.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.soneso.demo.stellar.ContractDetailsResult
import com.soneso.demo.stellar.fetchContractDetails
import com.soneso.demo.ui.theme.LightExtendedColors
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

        // Validate contract ID
        fun validateContractId(id: String): String? {
            return when {
                id.isBlank() -> "Contract ID is required"
                !id.startsWith('C') -> "Contract ID must start with 'C'"
                id.length != 56 -> "Contract ID must be 56 characters long"
                else -> null
            }
        }

        // Function to fetch contract details
        fun fetchDetails() {
            val error = validateContractId(contractId)
            if (error != null) {
                validationError = error
            } else {
                coroutineScope.launch {
                    isLoading = true
                    detailsResult = null
                    try {
                        detailsResult = fetchContractDetails(contractId, useTestnet = true)
                    } finally {
                        isLoading = false
                    }
                }
            }
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Fetch Smart Contract Details") },
                    navigationIcon = {
                        IconButton(onClick = { navigator.pop() }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back"
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )
            },
            snackbarHost = { SnackbarHost(snackbarHostState) }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Information card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Soroban RPC: fetch and parse smart contract details",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Text(
                            text = "Enter a contract ID to fetch its WASM bytecode from the network and parse the contract specification including metadata and function definitions.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }

                // Demo Contract Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Example Testnet Contract",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Column(
                            modifier = Modifier.padding(start = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "The contract ID field is pre-filled with a testnet contract ID.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = "You can use it as-is or replace it with your own contract ID.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }

                // Contract ID Input Field
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
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    },
                    keyboardOptions = KeyboardOptions(
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = { fetchDetails() }
                    )
                )

                // Submit button
                Button(
                    onClick = { fetchDetails() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    enabled = !isLoading && contractId.isNotBlank()
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Fetching...")
                    } else {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Fetch Details")
                    }
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

                // Placeholder when no action taken
                if (detailsResult == null && !isLoading && contractId.isBlank()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Icon(
                        imageVector = Icons.Default.Code,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                    )
                    Text(
                        text = "Enter a contract ID to view its parsed specification",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
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
    // Success header card
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = LightExtendedColors.successContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Contract Found",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = LightExtendedColors.onSuccessContainer
            )
            Text(
                text = "Successfully fetched and parsed contract details",
                style = MaterialTheme.typography.bodyMedium,
                color = LightExtendedColors.onSuccessContainer
            )
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
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Contract Metadata",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            HorizontalDivider()

            // Environment Interface Version
            DetailRow(
                label = "Environment Interface Version",
                value = contractInfo.envInterfaceVersion.toString()
            )

            // Meta entries
            if (contractInfo.metaEntries.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Meta Entries (${contractInfo.metaEntries.size})",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
                contractInfo.metaEntries.forEach { (key, value) ->
                    DetailRow(label = key, value = value, monospace = true)
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

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Contract Spec Entries (${specEntries.size})",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            HorizontalDivider()

            if (sortedEntries.isEmpty()) {
                Text(
                    text = "No spec entries found",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            } else {
                sortedEntries.forEachIndexed { index, entry ->
                    if (index > 0) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    }
                    SpecEntryItem(entry)
                }
            }
        }
    }
}

@Composable
private fun SpecEntryItem(entry: SCSpecEntryXdr) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        when (entry) {
            is SCSpecEntryXdr.FunctionV0 -> {
                FunctionSpecItem(entry.value)
            }
            is SCSpecEntryXdr.UdtStructV0 -> {
                StructSpecItem(entry.value)
            }
            is SCSpecEntryXdr.UdtUnionV0 -> {
                UnionSpecItem(entry.value)
            }
            is SCSpecEntryXdr.UdtEnumV0 -> {
                EnumSpecItem(entry.value)
            }
            is SCSpecEntryXdr.UdtErrorEnumV0 -> {
                ErrorEnumSpecItem(entry.value)
            }
            is SCSpecEntryXdr.EventV0 -> {
                EventSpecItem(entry.value)
            }
        }
    }
}

@Composable
private fun FunctionSpecItem(function: SCSpecFunctionV0Xdr) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        ),
        onClick = { expanded = !expanded }
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "Function: ${function.name.value}",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (function.doc.isNotEmpty()) {
                Text(
                    text = function.doc,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }

            if (expanded) {
                // Input parameters
                if (function.inputs.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
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
            } else {
                // Summary when collapsed
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Inputs: ${function.inputs.size}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Outputs: ${function.outputs.size}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Text(
                text = if (expanded) "Click to collapse" else "Click to expand",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

@Composable
private fun StructSpecItem(struct: SCSpecUDTStructV0Xdr) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)
        ),
        onClick = { expanded = !expanded }
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "Struct: ${struct.name}",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
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

            if (expanded) {
                // Fields
                if (struct.fields.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
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
            } else {
                // Summary when collapsed
                Text(
                    text = "Fields: ${struct.fields.size}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Text(
                text = if (expanded) "Click to collapse" else "Click to expand",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

@Composable
private fun UnionSpecItem(union: SCSpecUDTUnionV0Xdr) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f)
        ),
        onClick = { expanded = !expanded }
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "Union: ${union.name}",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
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

            if (expanded) {
                // Cases
                if (union.cases.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
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
            } else {
                // Summary when collapsed
                Text(
                    text = "Cases: ${union.cases.size}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Text(
                text = if (expanded) "Click to collapse" else "Click to expand",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

@Composable
private fun EnumSpecItem(enum: SCSpecUDTEnumV0Xdr) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        onClick = { expanded = !expanded }
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "Enum: ${enum.name}",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
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

            if (expanded) {
                // Cases
                if (enum.cases.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
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
            } else {
                // Summary when collapsed
                Text(
                    text = "Cases: ${enum.cases.size}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Text(
                text = if (expanded) "Click to collapse" else "Click to expand",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

@Composable
private fun ErrorEnumSpecItem(errorEnum: SCSpecUDTErrorEnumV0Xdr) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
        ),
        onClick = { expanded = !expanded }
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "Error Enum: ${errorEnum.name}",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
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

            if (expanded) {
                // Cases
                if (errorEnum.cases.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
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
            } else {
                // Summary when collapsed
                Text(
                    text = "Cases: ${errorEnum.cases.size}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Text(
                text = if (expanded) "Click to collapse" else "Click to expand",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

@Composable
private fun EventSpecItem(event: SCSpecEventV0Xdr) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
        ),
        onClick = { expanded = !expanded }
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "Event: ${event.name.value}",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
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

            if (expanded) {
                // Prefix Topics
                if (event.prefixTopics.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
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
            } else {
                // Summary when collapsed
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Prefix Topics: ${event.prefixTopics.size}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Params: ${event.params.size}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Text(
                text = if (expanded) "Click to collapse" else "Click to expand",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
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
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = if (monospace) FontFamily.Monospace else FontFamily.Default,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ErrorCard(error: ContractDetailsResult.Error) {
    // Error card
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Error",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Text(
                text = error.message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            error.exception?.let { exception ->
                Text(
                    text = "Technical details: ${exception.message}",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
    }

    // Troubleshooting tips
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Troubleshooting",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Column(
                modifier = Modifier.padding(start = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "• Verify the contract ID is valid (starts with 'C' and is 56 characters)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Text(
                    text = "• Make sure the contract exists on testnet and has been deployed",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Text(
                    text = "• Check your internet connection",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Text(
                    text = "• Try again in a moment if you're being rate-limited",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }
    }
}
