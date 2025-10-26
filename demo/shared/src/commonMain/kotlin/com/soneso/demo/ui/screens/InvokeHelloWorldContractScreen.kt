package com.soneso.demo.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.soneso.demo.stellar.InvokeHelloWorldResult
import com.soneso.demo.stellar.invokeHelloWorldContract
import com.soneso.demo.ui.FormValidation
import com.soneso.demo.ui.components.AnimatedButton
import com.soneso.demo.ui.components.InfoCardMediumTitle
import com.soneso.demo.ui.components.StellarTopBar
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

        // Validation function using FormValidation utility
        fun validateInputs(): Map<String, String> {
            val errors = mutableMapOf<String, String>()

            FormValidation.validateContractIdField(contractId)?.let {
                errors["contractId"] = it
            }

            if (toParameter.isBlank()) {
                errors["toParameter"] = "Name parameter is required"
            }

            FormValidation.validateAccountIdField(submitterAccountId)?.let {
                errors["submitterAccount"] = it
            }

            FormValidation.validateSecretSeedField(secretKey)?.let {
                errors["secretKey"] = it
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
                StellarTopBar(
                    title = "Invoke Hello World Contract",
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
                    title = "Beginner-friendly Contract Invocation",
                    description = "This demo showcases the SDK's high-level contract invocation API with automatic type conversion. " +
                            "The invoke() method accepts Map-based arguments and handles XDR conversion, transaction building, " +
                            "signing, submission, and result parsing automatically."
                )

                // Input fields card - Gold (consolidated)
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            width = 1.dp,
                            color = Color(0xFFD97706).copy(alpha = 0.3f),
                            shape = MaterialTheme.shapes.medium
                        ),
                    shape = MaterialTheme.shapes.medium,
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFFFFFBF0) // Warm Gold
                    )
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
                                        color = Color(0xFF991B1B)
                                    )
                                }
                            } ?: {
                                Text(
                                    text = "Deploy hello world contract first using 'Deploy a Smart Contract'",
                                    color = Color(0xFFA85A00).copy(alpha = 0.7f)
                                )
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFFD97706),
                                focusedLabelColor = Color(0xFFD97706),
                                cursorColor = Color(0xFFD97706)
                            ),
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
                                        color = Color(0xFF991B1B)
                                    )
                                }
                            } ?: {
                                Text(
                                    text = "The name to greet in the hello function",
                                    color = Color(0xFFA85A00).copy(alpha = 0.7f)
                                )
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFFD97706),
                                focusedLabelColor = Color(0xFFD97706),
                                cursorColor = Color(0xFFD97706)
                            ),
                            keyboardOptions = KeyboardOptions(
                                imeAction = ImeAction.Next
                            )
                        )

                        HorizontalDivider(color = Color(0xFFD97706).copy(alpha = 0.2f))

                        Text(
                            text = "Submitter Account",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            color = Color(0xFFA85A00)
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
                                        color = Color(0xFF991B1B)
                                    )
                                }
                            } ?: {
                                Text(
                                    text = "Account that will sign and submit the transaction",
                                    color = Color(0xFFA85A00).copy(alpha = 0.7f)
                                )
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFFD97706),
                                focusedLabelColor = Color(0xFFD97706),
                                cursorColor = Color(0xFFD97706)
                            ),
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
                                        color = Color(0xFF991B1B)
                                    )
                                }
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFFD97706),
                                focusedLabelColor = Color(0xFFD97706),
                                cursorColor = Color(0xFFD97706)
                            ),
                            keyboardOptions = KeyboardOptions(
                                imeAction = ImeAction.Done
                            ),
                            keyboardActions = KeyboardActions(
                                onDone = { invokeContract() }
                            )
                        )
                    }
                }

                // Invoke button with AnimatedButton
                AnimatedButton(
                    onClick = { invokeContract() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    enabled = contractId.isNotBlank() &&
                            toParameter.isNotBlank() &&
                            submitterAccountId.isNotBlank() &&
                            secretKey.isNotBlank(),
                    isLoading = isInvoking,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF0A4FD6), // Stellar Blue
                        contentColor = Color.White
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = Color.White
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "Invoke Contract",
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontWeight = FontWeight.Bold
                        )
                    )
                }

                // Result display
                invocationResult?.let { result ->
                    when (result) {
                        is InvokeHelloWorldResult.Success -> {
                            SuccessCard(result)
                        }
                        is InvokeHelloWorldResult.Error -> {
                            ErrorCard(result)

                            Spacer(modifier = Modifier.height(12.dp))

                            // Purple troubleshooting tips card - ONLY ON ERROR
                            InfoCardMediumTitle(
                                modifier = Modifier.fillMaxWidth(),
                                title = "Troubleshooting",
                                description = "• Ensure the contract ID is correct and the contract is deployed on testnet\n" +
                                        "• Verify the submitter account has sufficient XLM balance for fees\n" +
                                        "• Check that the secret key matches the submitter account ID\n" +
                                        "• Make sure you deployed the Hello World contract first (not another contract)\n" +
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
private fun SuccessCard(result: InvokeHelloWorldResult.Success) {
    // Teal success header
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFF0FDFA) // Nebula Teal Container
        )
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = Color(0xFF0F766E), // Nebula Teal Dark
                modifier = Modifier.size(28.dp)
            )
            Text(
                text = "Success",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    lineHeight = 22.sp
                ),
                color = Color(0xFF0F766E)
            )
        }
    }

    // Blue greeting response card
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFE8F1FF) // Nebula Blue
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Greeting Response",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    lineHeight = 22.sp
                ),
                color = Color(0xFF0639A3) // Stellar Blue Dark
            )

            SelectionContainer {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = Color.White
                    )
                ) {
                    Text(
                        text = result.greeting,
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Medium,
                            lineHeight = 28.sp
                        ),
                        color = Color(0xFF0639A3),
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }

            HorizontalDivider(color = Color(0xFF0639A3).copy(alpha = 0.2f))

            Text(
                text = "The contract function was successfully invoked using ContractClient.invoke() with automatic type conversion from Map arguments to Soroban XDR types.",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    lineHeight = 22.sp
                ),
                color = Color(0xFF0639A3).copy(alpha = 0.8f)
            )
        }
    }
}

@Composable
private fun ErrorCard(error: InvokeHelloWorldResult.Error) {
    // Red error header card
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFFEF2F2) // Nova Red Container
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Error,
                    contentDescription = null,
                    tint = Color(0xFF991B1B), // Nova Red Dark
                    modifier = Modifier.size(28.dp)
                )
                Text(
                    text = "Error",
                    style = MaterialTheme.typography.titleLarge.copy(
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

            // Technical details in white nested card
            error.exception?.let { exception ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = Color.White
                    )
                ) {
                    SelectionContainer {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
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
                                    fontFamily = FontFamily.Monospace,
                                    lineHeight = 20.sp
                                ),
                                color = Color(0xFF991B1B).copy(alpha = 0.8f)
                            )
                        }
                    }
                }
            }
        }
    }
}
}
