package com.soneso.demo.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.soneso.demo.platform.getClipboard
import com.soneso.demo.stellar.*
import com.soneso.demo.ui.FormValidation
import com.soneso.demo.ui.components.AnimatedButton
import com.soneso.demo.ui.components.InfoCardMediumTitle
import com.soneso.demo.ui.components.StellarTopBar
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
            FormValidation.validateAccountIdField(sourceAccountId)?.let {
                errors["sourceAccount"] = it
            }

            // Validate secret key
            FormValidation.validateSecretSeedField(secretKey)?.let {
                errors["secretKey"] = it
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
                                    FormValidation.validateAccountIdField(value)?.let { error ->
                                        errors["constructor_${param.name}"] = error
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
                    )
                } finally {
                    isDeploying = false
                }
            }
        }

        Scaffold(
            topBar = {
                StellarTopBar(
                    title = "Deploy Smart Contract",
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
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                // Information card (Purple)
                InfoCardMediumTitle(
                    modifier = Modifier.fillMaxWidth(),
                    title = "ContractClient.deploy(): One-step contract deployment",
                    description = "This demo showcases the SDK's high-level deployment API that handles WASM upload, contract deployment, and constructor invocation in a single call.",
                    useBorder = true,
                    useRoundedShape = true
                )

                // Consolidated Input Card (Gold)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFFFFFBF0)
                    ),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, Color(0xFFFFEDD5))
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = "Deployment Configuration",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                lineHeight = 22.sp
                            ),
                            color = Color(0xFFD97706)
                        )

                        HorizontalDivider(color = Color(0xFFFFEDD5))

                        // Step 1: Contract Selection
                        Text(
                            text = "1. Select Contract",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = Color(0xFF2D3548)
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
                            Surface(
                                color = Color.White.copy(alpha = 0.6f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = contract.description,
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            lineHeight = 20.sp
                                        ),
                                        color = Color(0xFF2D3548)
                                    )
                                    if (contract.hasConstructor) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "Constructor required: ${contract.constructorParams.size} parameter(s)",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color(0xFF6B7280),
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            }
                        }

                        HorizontalDivider(color = Color(0xFFFFEDD5))

                        // Step 2: Source Account
                        Text(
                            text = "2. Source Account",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = Color(0xFF2D3548)
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

                        // Step 3: Constructor Parameters (if applicable)
                        selectedContract?.let { contract ->
                            if (contract.hasConstructor) {
                                HorizontalDivider(color = Color(0xFFFFEDD5))

                                Text(
                                    text = "3. Constructor Parameters",
                                    style = MaterialTheme.typography.titleSmall.copy(
                                        fontWeight = FontWeight.SemiBold
                                    ),
                                    color = Color(0xFF2D3548)
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

                // Deploy button with AnimatedButton
                AnimatedButton(
                    onClick = { deployContract() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    enabled = selectedContract != null &&
                            sourceAccountId.isNotBlank() && secretKey.isNotBlank(),
                    isLoading = isDeploying,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF0A4FD6)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CloudUpload,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Deploy Contract",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                }

                // Result display
                deploymentResult?.let { result ->
                    when (result) {
                        is DeployContractResult.Success -> {
                            SuccessCard(result, snackbarHostState, coroutineScope)
                        }
                        is DeployContractResult.Error -> {
                            ErrorCard(result)

                            Spacer(modifier = Modifier.height(12.dp))

                            // Troubleshooting tips (Purple) - ONLY ON ERROR
                            InfoCardMediumTitle(
                                modifier = Modifier.fillMaxWidth(),
                                title = "Troubleshooting Tips",
                                description = "• Verify the source account has sufficient XLM balance (at least 100 XLM recommended)\n" +
                                        "• Ensure the source account exists on testnet (use 'Fund Testnet Account' first)\n" +
                                        "• Check that the secret key matches the source account ID\n" +
                                        "• Verify constructor arguments match the expected types\n" +
                                        "• Check your internet connection and Soroban RPC availability"
                            )
                        }
                    }
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
            containerColor = Color(0xFFF0FDFA)
        ),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, Color(0xFFCCFBF1))
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = Color(0xFF0F766E),
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = "Deployment Successful",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        lineHeight = 22.sp
                    ),
                    color = Color(0xFF0F766E)
                )
            }

            HorizontalDivider(color = Color(0xFFCCFBF1))

            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Contract ID",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = Color(0xFF0F766E)
                )
                SelectionContainer {
                    Surface(
                        color = Color.White.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(12.dp)
                        ) {
                            Text(
                                text = result.contractId,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 13.sp,
                                    lineHeight = 20.sp
                                ),
                                color = Color(0xFF1A1F2E),
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = {
                                    scope.launch {
                                        val success = getClipboard().copyToClipboard(result.contractId)
                                        snackbarHostState.showSnackbar(
                                            if (success) "Contract ID copied to clipboard"
                                            else "Failed to copy to clipboard"
                                        )
                                    }
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = "Copy contract ID",
                                    tint = Color(0xFF0F766E),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }

            Text(
                text = "You can now use this contract ID to interact with your deployed contract via the SDK's ContractClient.forContract() method.",
                style = MaterialTheme.typography.bodySmall.copy(
                    lineHeight = 18.sp
                ),
                color = Color(0xFF2D3548)
            )
        }
    }
}

@Composable
private fun ErrorCard(error: DeployContractResult.Error) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFFEF2F2)
        ),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, Color(0xFFFEE2E2))
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Deployment Failed",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    lineHeight = 22.sp
                ),
                color = Color(0xFF991B1B)
            )

            HorizontalDivider(color = Color(0xFFFEE2E2))

            Text(
                text = error.message,
                style = MaterialTheme.typography.bodyMedium.copy(
                    lineHeight = 22.sp
                ),
                color = Color(0xFF2D3548)
            )
            error.exception?.let { exception ->
                Surface(
                    color = Color.White.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "Technical details: ${exception.message}",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            lineHeight = 18.sp
                        ),
                        color = Color(0xFF6B7280),
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
        }
    }
}
}
