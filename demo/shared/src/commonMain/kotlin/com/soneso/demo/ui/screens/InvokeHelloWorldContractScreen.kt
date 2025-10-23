package com.soneso.demo.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.soneso.demo.stellar.InvokeHelloWorldResult
import com.soneso.demo.stellar.invokeHelloWorldContract
import com.soneso.demo.ui.theme.LightExtendedColors
import kotlinx.coroutines.launch

class InvokeHelloWorldContractScreen : Screen {
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val coroutineScope = rememberCoroutineScope()

        // State management
        var contractId by remember { mutableStateOf("") }
        var toParameter by remember { mutableStateOf("") }
        var submitterAccountId by remember { mutableStateOf("") }
        var secretKey by remember { mutableStateOf("") }
        var isInvoking by remember { mutableStateOf(false) }
        var invocationResult by remember { mutableStateOf<InvokeHelloWorldResult?>(null) }
        var validationErrors by remember { mutableStateOf<Map<String, String>>(emptyMap()) }

        val snackbarHostState = remember { SnackbarHostState() }

        // Validation function
        fun validateInputs(): Map<String, String> {
            val errors = mutableMapOf<String, String>()

            // Validate contract ID
            if (contractId.isBlank()) {
                errors["contractId"] = "Contract ID is required"
            } else if (!contractId.startsWith('C')) {
                errors["contractId"] = "Contract ID must start with 'C'"
            } else if (contractId.length != 56) {
                errors["contractId"] = "Contract ID must be 56 characters"
            }

            // Validate "to" parameter
            if (toParameter.isBlank()) {
                errors["toParameter"] = "Name parameter is required"
            }

            // Validate submitter account ID
            if (submitterAccountId.isBlank()) {
                errors["submitterAccount"] = "Submitter account ID is required"
            } else if (!submitterAccountId.startsWith('G')) {
                errors["submitterAccount"] = "Account ID must start with 'G'"
            } else if (submitterAccountId.length != 56) {
                errors["submitterAccount"] = "Account ID must be 56 characters"
            }

            // Validate secret key
            if (secretKey.isBlank()) {
                errors["secretKey"] = "Secret key is required"
            } else if (!secretKey.startsWith('S')) {
                errors["secretKey"] = "Secret key must start with 'S'"
            } else if (secretKey.length != 56) {
                errors["secretKey"] = "Secret key must be 56 characters"
            }

            return errors
        }

        // Function to invoke contract
        fun invokeContract() {
            val errors = validateInputs()
            if (errors.isNotEmpty()) {
                validationErrors = errors
                return
            }

            coroutineScope.launch {
                isInvoking = true
                invocationResult = null
                validationErrors = emptyMap()

                try {
                    invocationResult = invokeHelloWorldContract(
                        contractId = contractId,
                        to = toParameter,
                        submitterAccountId = submitterAccountId,
                        secretKey = secretKey,
                    )
                } finally {
                    isInvoking = false
                }
            }
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Invoke Hello World Contract") },
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
                            text = "ContractClient.invoke(): Beginner-friendly contract invocation",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Text(
                            text = "This demo showcases the SDK's high-level contract invocation API with automatic type conversion. " +
                                    "The invoke() method accepts Map-based arguments and handles XDR conversion, transaction building, " +
                                    "signing, submission, and result parsing automatically.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }

                // Input fields card
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
                            text = "Contract Details",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        OutlinedTextField(
                            value = contractId,
                            onValueChange = {
                                contractId = it.trim()
                                validationErrors = validationErrors - "contractId"
                                invocationResult = null
                            },
                            label = { Text("Contract ID") },
                            placeholder = { Text("C...") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            isError = validationErrors.containsKey("contractId"),
                            supportingText = validationErrors["contractId"]?.let { error ->
                                {
                                    Text(
                                        text = error,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            } ?: {
                                Text(
                                    text = "Deploy hello world contract first using 'Deploy a Smart Contract'",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            keyboardOptions = KeyboardOptions(
                                imeAction = ImeAction.Next
                            )
                        )

                        OutlinedTextField(
                            value = toParameter,
                            onValueChange = {
                                toParameter = it
                                validationErrors = validationErrors - "toParameter"
                                invocationResult = null
                            },
                            label = { Text("Name (to parameter)") },
                            placeholder = { Text("Alice") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            isError = validationErrors.containsKey("toParameter"),
                            supportingText = validationErrors["toParameter"]?.let { error ->
                                {
                                    Text(
                                        text = error,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            } ?: {
                                Text(
                                    text = "The name to greet in the hello function",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            keyboardOptions = KeyboardOptions(
                                imeAction = ImeAction.Next
                            )
                        )
                    }
                }

                // Submitter account card
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
                            text = "Submitter Account",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        OutlinedTextField(
                            value = submitterAccountId,
                            onValueChange = {
                                submitterAccountId = it.trim()
                                validationErrors = validationErrors - "submitterAccount"
                                invocationResult = null
                            },
                            label = { Text("Submitter Account ID") },
                            placeholder = { Text("G...") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            isError = validationErrors.containsKey("submitterAccount"),
                            supportingText = validationErrors["submitterAccount"]?.let { error ->
                                {
                                    Text(
                                        text = error,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            } ?: {
                                Text(
                                    text = "Account that will sign and submit the transaction",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
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
                                invocationResult = null
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
                                imeAction = ImeAction.Done
                            ),
                            keyboardActions = KeyboardActions(
                                onDone = { invokeContract() }
                            )
                        )
                    }
                }

                // Invoke button
                Button(
                    onClick = { invokeContract() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    enabled = !isInvoking &&
                            contractId.isNotBlank() &&
                            toParameter.isNotBlank() &&
                            submitterAccountId.isNotBlank() &&
                            secretKey.isNotBlank()
                ) {
                    if (isInvoking) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Invoking...")
                    } else {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Invoke Contract")
                    }
                }

                // Result display
                invocationResult?.let { result ->
                    when (result) {
                        is InvokeHelloWorldResult.Success -> {
                            SuccessCard(result)
                        }
                        is InvokeHelloWorldResult.Error -> {
                            ErrorCard(result)
                        }
                    }
                }

                // Placeholder when no action taken
                if (invocationResult == null && !isInvoking && contractId.isBlank()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                    )
                    Text(
                        text = "Enter contract details to invoke the hello function",
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
private fun SuccessCard(result: InvokeHelloWorldResult.Success) {
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
                    text = "Contract Invocation Successful",
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
                    text = "Greeting Response",
                    style = MaterialTheme.typography.labelMedium,
                    color = LightExtendedColors.onSuccessContainer.copy(alpha = 0.7f)
                )
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = LightExtendedColors.onSuccessContainer.copy(alpha = 0.1f)
                    )
                ) {
                    Text(
                        text = result.greeting,
                        style = MaterialTheme.typography.titleLarge,
                        fontFamily = FontFamily.Default,
                        fontWeight = FontWeight.Medium,
                        color = LightExtendedColors.onSuccessContainer,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }

            Text(
                text = "The contract function was successfully invoked using ContractClient.invoke() with automatic type conversion from Map arguments to Soroban XDR types.",
                style = MaterialTheme.typography.bodySmall,
                color = LightExtendedColors.onSuccessContainer
            )
        }
    }
}

@Composable
private fun ErrorCard(error: InvokeHelloWorldResult.Error) {
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
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Error,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onErrorContainer
                )
                Text(
                    text = "Invocation Failed",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
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
                    text = "• Ensure the contract ID is correct and the contract is deployed on testnet",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Text(
                    text = "• Verify the submitter account has sufficient XLM balance for fees",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Text(
                    text = "• Check that the secret key matches the submitter account ID",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Text(
                    text = "• Make sure you deployed the Hello World contract first (not another contract)",
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
