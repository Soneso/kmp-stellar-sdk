package com.soneso.demo.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.soneso.demo.platform.getClipboard
import com.soneso.demo.stellar.*
import com.soneso.demo.ui.theme.LightExtendedColors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class DeployContractScreen : Screen {
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val coroutineScope = rememberCoroutineScope()

        // State management
        var selectedContract by remember { mutableStateOf<ContractMetadata?>(null) }
        var sourceAccountId by remember { mutableStateOf("") }
        var secretKey by remember { mutableStateOf("") }
        var constructorArgValues by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
        var isDeploying by remember { mutableStateOf(false) }
        var deploymentResult by remember { mutableStateOf<DeployContractResult?>(null) }
        var validationErrors by remember { mutableStateOf<Map<String, String>>(emptyMap()) }

        val snackbarHostState = remember { SnackbarHostState() }

        // Validation functions
        fun validateInputs(): Map<String, String> {
            val errors = mutableMapOf<String, String>()

            // Validate source account
            if (sourceAccountId.isBlank()) {
                errors["sourceAccount"] = "Source account ID is required"
            } else if (!sourceAccountId.startsWith('G')) {
                errors["sourceAccount"] = "Account ID must start with 'G'"
            } else if (sourceAccountId.length != 56) {
                errors["sourceAccount"] = "Account ID must be 56 characters"
            }

            // Validate secret key
            if (secretKey.isBlank()) {
                errors["secretKey"] = "Secret key is required"
            } else if (!secretKey.startsWith('S')) {
                errors["secretKey"] = "Secret key must start with 'S'"
            } else if (secretKey.length != 56) {
                errors["secretKey"] = "Secret key must be 56 characters"
            }

            // Validate constructor arguments
            selectedContract?.let { contract ->
                if (contract.hasConstructor) {
                    for (param in contract.constructorParams) {
                        val value = constructorArgValues[param.name] ?: ""
                        if (value.isBlank()) {
                            errors["constructor_${param.name}"] = "${param.name} is required"
                        } else {
                            // Type-specific validation
                            when (param.type) {
                                ConstructorParamType.ADDRESS -> {
                                    if (!value.startsWith('G') || value.length != 56) {
                                        errors["constructor_${param.name}"] = "Must be a valid address (G...)"
                                    }
                                }
                                ConstructorParamType.U32 -> {
                                    value.toIntOrNull() ?: run {
                                        errors["constructor_${param.name}"] = "Must be a valid number"
                                    }
                                }
                                ConstructorParamType.STRING -> {
                                    // No additional validation for strings
                                }
                            }
                        }
                    }
                }
            }

            return errors
        }

        // Function to deploy contract
        fun deployContract() {
            val errors = validateInputs()
            if (errors.isNotEmpty()) {
                validationErrors = errors
                return
            }

            val contract = selectedContract
            if (contract == null) {
                coroutineScope.launch {
                    snackbarHostState.showSnackbar("Please select a contract to deploy")
                }
                return
            }

            coroutineScope.launch {
                isDeploying = true
                deploymentResult = null
                validationErrors = emptyMap()

                try {
                    // Build constructor arguments map with proper types
                    val constructorArgs = if (contract.hasConstructor) {
                        contract.constructorParams.associate { param ->
                            val value = constructorArgValues[param.name] ?: ""
                            val convertedValue: Any = when (param.type) {
                                ConstructorParamType.ADDRESS -> value
                                ConstructorParamType.STRING -> value
                                ConstructorParamType.U32 -> value.toInt()
                            }
                            param.name to convertedValue
                        }
                    } else {
                        emptyMap()
                    }

                    // Deploy using SDK
                    deploymentResult = com.soneso.demo.stellar.deployContract(
                        contractMetadata = contract,
                        constructorArgs = constructorArgs,
                        sourceAccountId = sourceAccountId,
                        secretKey = secretKey,
                        useTestnet = true
                    )
                } finally {
                    isDeploying = false
                }
            }
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Deploy a Smart Contract") },
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
                            text = "ContractClient.deploy(): One-step contract deployment",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Text(
                            text = "This demo showcases the SDK's high-level deployment API that handles WASM upload, contract deployment, and constructor invocation in a single call.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }

                // Contract selection dropdown
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "1. Select Contract",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        var expanded by remember { mutableStateOf(false) }

                        ExposedDropdownMenuBox(
                            expanded = expanded,
                            onExpandedChange = {
                                expanded = !expanded
                            }
                        ) {
                            OutlinedTextField(
                                value = selectedContract?.name ?: "",
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Demo Contract") },
                                trailingIcon = {
                                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .menuAnchor()
                            )

                            ExposedDropdownMenu(
                                expanded = expanded,
                                onDismissRequest = { expanded = false }
                            ) {
                                AVAILABLE_CONTRACTS.forEach { contract ->
                                    DropdownMenuItem(
                                        text = {
                                            Column {
                                                Text(
                                                    text = contract.name,
                                                    style = MaterialTheme.typography.bodyLarge
                                                )
                                                Text(
                                                    text = contract.description,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        },
                                        onClick = {
                                            selectedContract = contract
                                            // Reset constructor args when changing contract
                                            constructorArgValues = if (contract.hasConstructor) {
                                                contract.constructorParams.associate { it.name to "" }
                                            } else {
                                                emptyMap()
                                            }
                                            expanded = false
                                        }
                                    )
                                }
                            }
                        }

                        // Display selected contract description
                        selectedContract?.let { contract ->
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                                )
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = contract.description,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    if (contract.hasConstructor) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "Constructor required: ${contract.constructorParams.size} parameter(s)",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Source account inputs
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "2. Source Account",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        OutlinedTextField(
                            value = sourceAccountId,
                            onValueChange = {
                                sourceAccountId = it.trim()
                                validationErrors = validationErrors - "sourceAccount"
                                deploymentResult = null
                            },
                            label = { Text("Source Account ID") },
                            placeholder = { Text("G...") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            isError = validationErrors.containsKey("sourceAccount"),
                            supportingText = validationErrors["sourceAccount"]?.let { error ->
                                {
                                    Text(
                                        text = error,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            },
                            keyboardOptions = KeyboardOptions(
                                imeAction = ImeAction.Next
                            )
                        )

                        OutlinedTextField(
                            value = secretKey,
                            onValueChange = {
                                secretKey = it.trim()
                                validationErrors = validationErrors - "secretKey"
                                deploymentResult = null
                            },
                            label = { Text("Secret Key") },
                            placeholder = { Text("S...") },
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            isError = validationErrors.containsKey("secretKey"),
                            supportingText = validationErrors["secretKey"]?.let { error ->
                                {
                                    Text(
                                        text = error,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            },
                            keyboardOptions = KeyboardOptions(
                                imeAction = if (selectedContract?.hasConstructor == true) ImeAction.Next else ImeAction.Done
                            ),
                            keyboardActions = KeyboardActions(
                                onDone = {
                                    if (selectedContract?.hasConstructor != true) {
                                        deployContract()
                                    }
                                }
                            )
                        )
                    }
                }

                // Constructor arguments (if contract has constructor)
                selectedContract?.let { contract ->
                    if (contract.hasConstructor) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text(
                                    text = "3. Constructor Parameters",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )

                                contract.constructorParams.forEachIndexed { index, param ->
                                    val currentValue = constructorArgValues[param.name] ?: ""
                                    val isLast = index == contract.constructorParams.lastIndex

                                    OutlinedTextField(
                                        value = currentValue,
                                        onValueChange = { newValue ->
                                            constructorArgValues = constructorArgValues + (param.name to newValue)
                                            validationErrors = validationErrors - "constructor_${param.name}"
                                            deploymentResult = null
                                        },
                                        label = { Text(param.name) },
                                        placeholder = { Text(param.placeholder) },
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true,
                                        isError = validationErrors.containsKey("constructor_${param.name}"),
                                        supportingText = {
                                            val error = validationErrors["constructor_${param.name}"]
                                            if (error != null) {
                                                Text(
                                                    text = error,
                                                    color = MaterialTheme.colorScheme.error
                                                )
                                            } else {
                                                Text(
                                                    text = param.description,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        },
                                        keyboardOptions = KeyboardOptions(
                                            keyboardType = when (param.type) {
                                                ConstructorParamType.U32 -> KeyboardType.Number
                                                else -> KeyboardType.Text
                                            },
                                            imeAction = if (isLast) ImeAction.Done else ImeAction.Next
                                        ),
                                        keyboardActions = KeyboardActions(
                                            onDone = {
                                                if (isLast) {
                                                    deployContract()
                                                }
                                            }
                                        )
                                    )
                                }
                            }
                        }
                    }
                }

                // Deploy button
                Button(
                    onClick = { deployContract() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    enabled = !isDeploying && selectedContract != null &&
                            sourceAccountId.isNotBlank() && secretKey.isNotBlank()
                ) {
                    if (isDeploying) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Deploying...")
                    } else {
                        Icon(
                            imageVector = Icons.Default.CloudUpload,
                            contentDescription = null
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Deploy Contract")
                    }
                }

                // Result display
                deploymentResult?.let { result ->
                    when (result) {
                        is DeployContractResult.Success -> {
                            SuccessCard(result, snackbarHostState, coroutineScope)
                        }
                        is DeployContractResult.Error -> {
                            ErrorCard(result)
                        }
                    }
                }

                // Placeholder when no contract selected
                if (selectedContract == null && deploymentResult == null && !isDeploying) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Icon(
                        imageVector = Icons.Default.CloudUpload,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                    )
                    Text(
                        text = "Select a demo contract to begin deployment",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
private fun SuccessCard(result: DeployContractResult.Success, snackbarHostState: SnackbarHostState, scope: CoroutineScope) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = LightExtendedColors.successContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = LightExtendedColors.onSuccessContainer
                )
                Text(
                    text = "Deployment Successful",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = LightExtendedColors.onSuccessContainer
                )
            }

            HorizontalDivider()

            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Contract ID",
                    style = MaterialTheme.typography.labelMedium,
                    color = LightExtendedColors.onSuccessContainer.copy(alpha = 0.7f)
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = result.contractId,
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = FontFamily.Monospace,
                        color = LightExtendedColors.onSuccessContainer,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = {
                        scope.launch {
                            val success = getClipboard().copyToClipboard(result.contractId)
                            snackbarHostState.showSnackbar(
                                if (success) "Contract ID copied to clipboard"
                                else "Failed to copy to clipboard"
                            )
                        }
                    }) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy contract ID",
                            tint = LightExtendedColors.onSuccessContainer
                        )
                    }
                }
            }

            result.wasmId?.let { wasmId ->
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "WASM ID",
                        style = MaterialTheme.typography.labelMedium,
                        color = LightExtendedColors.onSuccessContainer.copy(alpha = 0.7f)
                    )
                    Text(
                        text = wasmId,
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = FontFamily.Monospace,
                        color = LightExtendedColors.onSuccessContainer
                    )
                }
            }

            Text(
                text = "You can now use this contract ID to interact with your deployed contract via the SDK's ContractClient.fromNetwork() method.",
                style = MaterialTheme.typography.bodySmall,
                color = LightExtendedColors.onSuccessContainer
            )
        }
    }
}

@Composable
private fun ErrorCard(error: DeployContractResult.Error) {
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
                text = "Deployment Failed",
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
                    text = "• Verify the source account has sufficient XLM balance (at least 100 XLM recommended)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Text(
                    text = "• Ensure the source account exists on testnet (use 'Fund Testnet Account' first)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Text(
                    text = "• Check that the secret key matches the source account ID",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Text(
                    text = "• Verify constructor arguments match the expected types",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Text(
                    text = "• Check your internet connection and Soroban RPC availability",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }
    }
}
