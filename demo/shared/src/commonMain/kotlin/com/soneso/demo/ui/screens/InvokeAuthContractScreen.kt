package com.soneso.demo.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.soneso.demo.platform.getClipboard
import com.soneso.demo.stellar.InvokeAuthContractResult
import com.soneso.demo.stellar.invokeAuthContract
import com.soneso.demo.ui.FormValidation
import com.soneso.demo.ui.components.AnimatedButton
import com.soneso.demo.ui.components.InfoCardMediumTitle
import com.soneso.demo.ui.components.StellarTopBar
import com.soneso.stellar.sdk.KeyPair
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class InvokeAuthContractScreen : Screen {
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val coroutineScope = rememberCoroutineScope()

        // State management
        var contractId by remember { mutableStateOf("") }
        var userAccountId by remember { mutableStateOf("") }
        var userSecretKey by remember { mutableStateOf("") }
        var sourceAccountId by remember { mutableStateOf("") }
        var sourceSecretKey by remember { mutableStateOf("") }
        var value by remember { mutableStateOf("1") }
        var useSameAccount by remember { mutableStateOf(true) }
        var isInvoking by remember { mutableStateOf(false) }
        var result by remember { mutableStateOf<InvokeAuthContractResult?>(null) }
        var validationErrors by remember { mutableStateOf<Map<String, String>>(emptyMap()) }

        val snackbarHostState = remember { SnackbarHostState() }

        // Auto-fill source fields when useSameAccount is checked
        LaunchedEffect(useSameAccount, userAccountId, userSecretKey) {
            if (useSameAccount) {
                sourceAccountId = userAccountId
                sourceSecretKey = userSecretKey
            }
        }

        // Validation function
        // Validation function
        fun validateInputs(): Map<String, String> {
            val errors = mutableMapOf<String, String>()

            FormValidation.validateContractIdField(contractId)?.let {
                errors["contractId"] = it
            }

            FormValidation.validateAccountIdField(userAccountId)?.let {
                errors["userAccount"] = it
            }

            FormValidation.validateSecretSeedField(userSecretKey)?.let {
                errors["userSecret"] = it
            }

            // Validate source account (only if not using same account)
            if (!useSameAccount) {
                FormValidation.validateAccountIdField(sourceAccountId)?.let {
                    errors["sourceAccount"] = it
                }

                FormValidation.validateSecretSeedField(sourceSecretKey)?.let {
                    errors["sourceSecret"] = it
                }
            }

            // Validate value (domain-specific, not in FormValidation)
            if (value.isBlank()) {
                errors["value"] = "Value is required"
            } else {
                val intValue = value.toIntOrNull()
                if (intValue == null) {
                    errors["value"] = "Value must be a valid integer"
                } else if (intValue < 0) {
                    errors["value"] = "Value must be non-negative"
                }
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
                result = null
                validationErrors = emptyMap()

                try {
                    val userKeyPair = KeyPair.fromSecretSeed(userSecretKey)
                    val sourceKeyPair = if (useSameAccount) {
                        userKeyPair
                    } else {
                        KeyPair.fromSecretSeed(sourceSecretKey)
                    }

                    result = invokeAuthContract(
                        contractId = contractId,
                        userAccountId = userAccountId,
                        userKeyPair = userKeyPair,
                        sourceAccountId = if (useSameAccount) userAccountId else sourceAccountId,
                        sourceKeyPair = sourceKeyPair,
                        value = value.toInt()
                    )
                } catch (e: Exception) {
                    result = InvokeAuthContractResult.Failure(
                        message = e.message ?: "Unknown error occurred",
                        exception = e
                    )
                } finally {
                    isInvoking = false
                }
            }
        }

        Scaffold(
            topBar = {
                StellarTopBar(
                    title = "Invoke Auth Contract",
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
                    title = "Dynamic Authorization Handling",
                    description = "This demo showcases a unified production-ready pattern for handling Soroban contract " +
                            "authorization. It uses needsNonInvokerSigningBy() to automatically detect whether " +
                            "same-invoker (automatic) or different-invoker (manual) authorization is needed, " +
                            "and conditionally calls signAuthEntries() only when required."
                )

                // Contract Configuration & Accounts - Gold Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFFFFFBF0) // Warm gold background
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
                            color = Color(0xFFA85A00) // Gold Dark
                        )

                        // Contract ID field
                        OutlinedTextField(
                            value = contractId,
                            onValueChange = {
                                contractId = it.trim()
                                validationErrors = validationErrors - "contractId"
                                result = null
                            },
                            label = { Text("Contract ID", color = Color(0xFFA85A00)) },
                            placeholder = { Text("C...") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            isError = validationErrors.containsKey("contractId"),
                            supportingText = validationErrors["contractId"]?.let { error ->
                                {
                                    Text(
                                        text = error,
                                        color = Color(0xFF991B1B) // Nova Red Dark
                                    )
                                }
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFFD97706), // Starlight Gold
                                focusedLabelColor = Color(0xFFA85A00),
                                cursorColor = Color(0xFFD97706)
                            ),
                            keyboardOptions = KeyboardOptions(
                                imeAction = ImeAction.Next
                            )
                        )

                        // Increment Value field
                        OutlinedTextField(
                            value = value,
                            onValueChange = {
                                value = it
                                validationErrors = validationErrors - "value"
                                result = null
                            },
                            label = { Text("Increment Value", color = Color(0xFFA85A00)) },
                            placeholder = { Text("1") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            isError = validationErrors.containsKey("value"),
                            supportingText = validationErrors["value"]?.let { error ->
                                {
                                    Text(
                                        text = error,
                                        color = Color(0xFF991B1B)
                                    )
                                }
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFFD97706),
                                focusedLabelColor = Color(0xFFA85A00),
                                cursorColor = Color(0xFFD97706)
                            ),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Number,
                                imeAction = ImeAction.Next
                            )
                        )

                        HorizontalDivider(color = Color(0xFFD97706).copy(alpha = 0.2f))

                        Text(
                            text = "User Account (Counter Owner)",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                lineHeight = 22.sp
                            ),
                            color = Color(0xFFA85A00)
                        )

                        // User Account ID field
                        OutlinedTextField(
                            value = userAccountId,
                            onValueChange = {
                                userAccountId = it.trim()
                                validationErrors = validationErrors - "userAccount"
                                result = null
                            },
                            label = { Text("User Account ID", color = Color(0xFFA85A00)) },
                            placeholder = { Text("G...") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            isError = validationErrors.containsKey("userAccount"),
                            supportingText = validationErrors["userAccount"]?.let { error ->
                                {
                                    Text(
                                        text = error,
                                        color = Color(0xFF991B1B)
                                    )
                                }
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFFD97706),
                                focusedLabelColor = Color(0xFFA85A00),
                                cursorColor = Color(0xFFD97706)
                            ),
                            keyboardOptions = KeyboardOptions(
                                imeAction = ImeAction.Next
                            )
                        )

                        // User Secret Key field
                        OutlinedTextField(
                            value = userSecretKey,
                            onValueChange = {
                                userSecretKey = it.trim()
                                validationErrors = validationErrors - "userSecret"
                                result = null
                            },
                            label = { Text("User Secret Key", color = Color(0xFFA85A00)) },
                            placeholder = { Text("S...") },
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            isError = validationErrors.containsKey("userSecret"),
                            supportingText = validationErrors["userSecret"]?.let { error ->
                                {
                                    Text(
                                        text = error,
                                        color = Color(0xFF991B1B)
                                    )
                                }
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFFD97706),
                                focusedLabelColor = Color(0xFFA85A00),
                                cursorColor = Color(0xFFD97706)
                            ),
                            keyboardOptions = KeyboardOptions(
                                imeAction = ImeAction.Next
                            )
                        )

                        HorizontalDivider(color = Color(0xFFD97706).copy(alpha = 0.2f))

                        // Same Account Toggle
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Use Same Account as Source",
                                    style = MaterialTheme.typography.titleSmall.copy(
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = Color(0xFFA85A00)
                                )
                                Text(
                                    text = if (useSameAccount) {
                                        "Same-invoker: Automatic authorization"
                                    } else {
                                        "Different-invoker: Manual auth required"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFFA85A00).copy(alpha = 0.7f)
                                )
                            }
                            Switch(
                                checked = useSameAccount,
                                onCheckedChange = {
                                    useSameAccount = it
                                    result = null
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color(0xFFD97706),
                                    checkedTrackColor = Color(0xFFD97706).copy(alpha = 0.5f)
                                )
                            )
                        }
                    }
                }

                // Source account card (only shown if not using same account) - Gold
                if (!useSameAccount) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium,
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFFFFFBF0) // Warm gold background
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Text(
                                text = "Source Account (Transaction Submitter)",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    lineHeight = 22.sp
                                ),
                                color = Color(0xFFA85A00) // Gold Dark
                            )

                            OutlinedTextField(
                                value = sourceAccountId,
                                onValueChange = {
                                    sourceAccountId = it.trim()
                                    validationErrors = validationErrors - "sourceAccount"
                                    result = null
                                },
                                label = { Text("Source Account ID", color = Color(0xFFA85A00)) },
                                placeholder = { Text("G...") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                isError = validationErrors.containsKey("sourceAccount"),
                                supportingText = validationErrors["sourceAccount"]?.let { error ->
                                    {
                                        Text(
                                            text = error,
                                            color = Color(0xFF991B1B)
                                        )
                                    }
                                },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color(0xFFD97706),
                                    focusedLabelColor = Color(0xFFA85A00),
                                    cursorColor = Color(0xFFD97706)
                                ),
                                keyboardOptions = KeyboardOptions(
                                    imeAction = ImeAction.Next
                                )
                            )

                            OutlinedTextField(
                                value = sourceSecretKey,
                                onValueChange = {
                                    sourceSecretKey = it.trim()
                                    validationErrors = validationErrors - "sourceSecret"
                                    result = null
                                },
                                label = { Text("Source Secret Key", color = Color(0xFFA85A00)) },
                                placeholder = { Text("S...") },
                                visualTransformation = PasswordVisualTransformation(),
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                isError = validationErrors.containsKey("sourceSecret"),
                                supportingText = validationErrors["sourceSecret"]?.let { error ->
                                    {
                                        Text(
                                            text = error,
                                            color = Color(0xFF991B1B)
                                        )
                                    }
                                },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color(0xFFD97706),
                                    focusedLabelColor = Color(0xFFA85A00),
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
                }

                // Invoke button with AnimatedButton
                AnimatedButton(
                    onClick = { invokeContract() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    enabled = contractId.isNotBlank() &&
                            userAccountId.isNotBlank() &&
                            userSecretKey.isNotBlank() &&
                            value.isNotBlank() &&
                            (useSameAccount || (sourceAccountId.isNotBlank() && sourceSecretKey.isNotBlank())),
                    isLoading = isInvoking,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF0A4FD6) // Stellar Blue
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
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Result display
                result?.let { res ->
                    when (res) {
                        is InvokeAuthContractResult.Success -> {
                            SuccessCard(res, snackbarHostState, coroutineScope)
                        }
                        is InvokeAuthContractResult.Failure -> {
                            ErrorCard(res.message, res.exception)

                            Spacer(modifier = Modifier.height(12.dp))

                            // Troubleshooting tips - Purple - ONLY ON ERROR
                            InfoCardMediumTitle(
                                modifier = Modifier.fillMaxWidth(),
                                title = "Troubleshooting",
                                description = "• Ensure the contract ID is correct and the auth contract is deployed on testnet\n" +
                                        "• Verify both accounts have sufficient XLM balance for fees\n" +
                                        "• Check that secret keys match their respective account IDs\n" +
                                        "• Deploy the auth contract (soroban_auth_contract.wasm) using 'Deploy a Smart Contract'\n" +
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
private fun SuccessCard(result: InvokeAuthContractResult.Success, snackbarHostState: SnackbarHostState, scope: CoroutineScope) {
    // Success Header - Teal
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFF0FDFA) // Nebula Teal Container
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
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

    // Counter Value & Scenario - Blue Cards
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
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Counter Value
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Counter Value",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = Color(0xFF0639A3).copy(alpha = 0.7f)
                )
                SelectionContainer {
                    Text(
                        text = result.counterValue.toString(),
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Bold,
                            lineHeight = 22.sp
                        ),
                        fontFamily = FontFamily.Monospace,
                        color = Color(0xFF0639A3) // Stellar Blue Dark
                    )
                }
            }

            HorizontalDivider(color = Color(0xFF0639A3).copy(alpha = 0.2f))

            // Detected Scenario
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Detected Scenario",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = Color(0xFF0639A3).copy(alpha = 0.7f)
                )
                SelectionContainer {
                    Text(
                        text = result.scenario,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            lineHeight = 22.sp
                        ),
                        color = Color(0xFF0639A3)
                    )
                }
            }

            HorizontalDivider(color = Color(0xFF0639A3).copy(alpha = 0.2f))

            // Authorization Required From
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Authorization Required From",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = Color(0xFF0639A3).copy(alpha = 0.7f)
                )
                SelectionContainer {
                    if (result.whoNeedsToSign.isEmpty()) {
                        Text(
                            text = "None (automatic authorization)",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                lineHeight = 22.sp
                            ),
                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                            color = Color(0xFF0639A3)
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            result.whoNeedsToSign.forEach { accountId ->
                                Text(
                                    text = "• $accountId",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = FontWeight.SemiBold,
                                        lineHeight = 22.sp
                                    ),
                                    fontFamily = FontFamily.Monospace,
                                    color = Color(0xFF0639A3)
                                )
                            }
                        }
                    }
                }
            }

            HorizontalDivider(color = Color(0xFF0639A3).copy(alpha = 0.2f))

            // Explanation
            Text(
                text = "The SDK automatically detected the authorization scenario using needsNonInvokerSigningBy() " +
                        "and conditionally signed auth entries only when needed. This is the production-ready pattern " +
                        "for handling both same-invoker and different-invoker scenarios.",
                style = MaterialTheme.typography.bodySmall.copy(
                    fontWeight = FontWeight.SemiBold,
                    lineHeight = 22.sp
                ),
                color = Color(0xFF0639A3).copy(alpha = 0.8f)
            )
        }
    }

    // Transaction Hash Card - Blue
    var isCopyHovered by remember { mutableStateOf(false) }
    val copyScale by animateFloatAsState(
        targetValue = if (isCopyHovered) 1.1f else 1f,
        label = "copyScale"
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFE8F1FF)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Transaction Hash",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = Color(0xFF0639A3).copy(alpha = 0.7f)
                )
                Spacer(modifier = Modifier.height(8.dp))
                SelectionContainer {
                    Text(
                        text = result.transactionHash,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            lineHeight = 22.sp
                        ),
                        fontFamily = FontFamily.Monospace,
                        color = Color(0xFF0639A3)
                    )
                }
            }
            IconButton(
                onClick = {
                    scope.launch {
                        val clipboard = getClipboard()
                        val copied = clipboard.copyToClipboard(result.transactionHash)
                        snackbarHostState.showSnackbar(
                            if (copied) "Transaction hash copied to clipboard"
                            else "Failed to copy to clipboard"
                        )
                    }
                },
                modifier = Modifier.scale(copyScale)
            ) {
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = "Copy transaction hash",
                    tint = Color(0xFF0639A3)
                )
            }
        }
    }
}

@Composable
private fun ErrorCard(message: String, exception: Throwable?) {
    // Error Header - Red
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
                text = message,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    lineHeight = 22.sp
                ),
                color = Color(0xFF991B1B)
            )

            // Technical details in white nested card
            if (exception != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = Color.White
                    )
                ) {
                    SelectionContainer {
                        Text(
                            text = exception.toString(),
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontWeight = FontWeight.Normal,
                                lineHeight = 20.sp
                            ),
                            fontFamily = FontFamily.Monospace,
                            color = Color(0xFF991B1B).copy(alpha = 0.8f),
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            }
        }
    }
}
}
