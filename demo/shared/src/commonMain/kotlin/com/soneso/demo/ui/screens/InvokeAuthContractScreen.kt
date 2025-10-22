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
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Error
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
import com.soneso.demo.stellar.InvokeAuthContractResult
import com.soneso.demo.stellar.invokeAuthContract
import com.soneso.demo.ui.theme.LightExtendedColors
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

            // Validate user account ID
            if (userAccountId.isBlank()) {
                errors["userAccount"] = "User account ID is required"
            } else if (!userAccountId.startsWith('G')) {
                errors["userAccount"] = "Account ID must start with 'G'"
            } else if (userAccountId.length != 56) {
                errors["userAccount"] = "Account ID must be 56 characters"
            }

            // Validate user secret key
            if (userSecretKey.isBlank()) {
                errors["userSecret"] = "User secret key is required"
            } else if (!userSecretKey.startsWith('S')) {
                errors["userSecret"] = "Secret key must start with 'S'"
            } else if (userSecretKey.length != 56) {
                errors["userSecret"] = "Secret key must be 56 characters"
            }

            // Validate source account ID (only if not using same account)
            if (!useSameAccount) {
                if (sourceAccountId.isBlank()) {
                    errors["sourceAccount"] = "Source account ID is required"
                } else if (!sourceAccountId.startsWith('G')) {
                    errors["sourceAccount"] = "Account ID must start with 'G'"
                } else if (sourceAccountId.length != 56) {
                    errors["sourceAccount"] = "Account ID must be 56 characters"
                }

                // Validate source secret key
                if (sourceSecretKey.isBlank()) {
                    errors["sourceSecret"] = "Source secret key is required"
                } else if (!sourceSecretKey.startsWith('S')) {
                    errors["sourceSecret"] = "Secret key must start with 'S'"
                } else if (sourceSecretKey.length != 56) {
                    errors["sourceSecret"] = "Secret key must be 56 characters"
                }
            }

            // Validate value
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
                TopAppBar(
                    title = { Text("Invoke Auth Contract") },
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
                            text = "Dynamic Authorization Handling",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Text(
                            text = "This demo showcases a unified production-ready pattern for handling Soroban contract " +
                                    "authorization. It uses needsNonInvokerSigningBy() to automatically detect whether " +
                                    "same-invoker (automatic) or different-invoker (manual) authorization is needed, " +
                                    "and conditionally calls signAuthEntries() only when required.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }

                // Educational card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Authorization Scenarios",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )

                        Text(
                            text = "Same-Invoker: User and source are the same account",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                        Text(
                            text = "• Authorization is automatic (no extra signatures needed)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.padding(start = 8.dp)
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = "Different-Invoker: User and source are different accounts",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                        Text(
                            text = "• User must explicitly authorize the operation (manual signature required)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.padding(start = 8.dp)
                        )

                        Spacer(modifier = Modifier.height(8.dp))
                        HorizontalDivider()
                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = "Tip: Deploy the auth contract (soroban_auth_contract.wasm) using 'Deploy a Smart Contract' first",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                }

                // Contract details card
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
                                result = null
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
                                    text = "Deploy auth contract first (soroban_auth_contract.wasm)",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            keyboardOptions = KeyboardOptions(
                                imeAction = ImeAction.Next
                            )
                        )

                        OutlinedTextField(
                            value = value,
                            onValueChange = {
                                value = it
                                validationErrors = validationErrors - "value"
                                result = null
                            },
                            label = { Text("Increment Value") },
                            placeholder = { Text("1") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            isError = validationErrors.containsKey("value"),
                            supportingText = validationErrors["value"]?.let { error ->
                                {
                                    Text(
                                        text = error,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            } ?: {
                                Text(
                                    text = "Amount to increment the counter",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Number,
                                imeAction = ImeAction.Next
                            )
                        )
                    }
                }

                // User account card
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
                            text = "User Account (Counter Owner)",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        OutlinedTextField(
                            value = userAccountId,
                            onValueChange = {
                                userAccountId = it.trim()
                                validationErrors = validationErrors - "userAccount"
                                result = null
                            },
                            label = { Text("User Account ID") },
                            placeholder = { Text("G...") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            isError = validationErrors.containsKey("userAccount"),
                            supportingText = validationErrors["userAccount"]?.let { error ->
                                {
                                    Text(
                                        text = error,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            } ?: {
                                Text(
                                    text = "Account that owns the counter being incremented",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            keyboardOptions = KeyboardOptions(
                                imeAction = ImeAction.Next
                            )
                        )

                        OutlinedTextField(
                            value = userSecretKey,
                            onValueChange = {
                                userSecretKey = it.trim()
                                validationErrors = validationErrors - "userSecret"
                                result = null
                            },
                            label = { Text("User Secret Key") },
                            placeholder = { Text("S...") },
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            isError = validationErrors.containsKey("userSecret"),
                            supportingText = validationErrors["userSecret"]?.let { error ->
                                {
                                    Text(
                                        text = error,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            } ?: {
                                Text(
                                    text = "Required to sign authorization entries (if different-invoker)",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            keyboardOptions = KeyboardOptions(
                                imeAction = ImeAction.Next
                            )
                        )
                    }
                }

                // Same account toggle
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Use Same Account as Source",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (useSameAccount) {
                                    "Same-invoker: User submits for themselves (automatic auth)"
                                } else {
                                    "Different-invoker: Different account submits (manual auth required)"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = useSameAccount,
                            onCheckedChange = {
                                useSameAccount = it
                                result = null
                            }
                        )
                    }
                }

                // Source account card (only shown if not using same account)
                if (!useSameAccount) {
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
                                text = "Source Account (Transaction Submitter)",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            OutlinedTextField(
                                value = sourceAccountId,
                                onValueChange = {
                                    sourceAccountId = it.trim()
                                    validationErrors = validationErrors - "sourceAccount"
                                    result = null
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
                                } ?: {
                                    Text(
                                        text = "Different account that will submit the transaction",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                },
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
                                label = { Text("Source Secret Key") },
                                placeholder = { Text("S...") },
                                visualTransformation = PasswordVisualTransformation(),
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                isError = validationErrors.containsKey("sourceSecret"),
                                supportingText = validationErrors["sourceSecret"]?.let { error ->
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
                }

                // Invoke button
                Button(
                    onClick = { invokeContract() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    enabled = !isInvoking &&
                            contractId.isNotBlank() &&
                            userAccountId.isNotBlank() &&
                            userSecretKey.isNotBlank() &&
                            value.isNotBlank() &&
                            (useSameAccount || (sourceAccountId.isNotBlank() && sourceSecretKey.isNotBlank()))
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
                result?.let { res ->
                    when (res) {
                        is InvokeAuthContractResult.Success -> {
                            SuccessCard(res, snackbarHostState, coroutineScope)
                        }
                        is InvokeAuthContractResult.Failure -> {
                            ErrorCard(res.message, res.exception)
                        }
                    }
                }

                // Placeholder when no action taken
                if (result == null && !isInvoking && contractId.isBlank()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                    )
                    Text(
                        text = "Enter contract and account details to test authorization patterns",
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
private fun SuccessCard(result: InvokeAuthContractResult.Success, snackbarHostState: SnackbarHostState, scope: CoroutineScope) {
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
                // Counter value
                Text(
                    text = "Counter Value",
                    style = MaterialTheme.typography.labelMedium,
                    color = LightExtendedColors.onSuccessContainer.copy(alpha = 0.7f)
                )
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = LightExtendedColors.onSuccessContainer.copy(alpha = 0.1f)
                    )
                ) {
                    Text(
                        text = result.counterValue.toString(),
                        style = MaterialTheme.typography.headlineMedium,
                        fontFamily = FontFamily.Default,
                        fontWeight = FontWeight.Bold,
                        color = LightExtendedColors.onSuccessContainer,
                        modifier = Modifier.padding(16.dp)
                    )
                }

                // Detected scenario
                Text(
                    text = "Detected Scenario",
                    style = MaterialTheme.typography.labelMedium,
                    color = LightExtendedColors.onSuccessContainer.copy(alpha = 0.7f)
                )
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = LightExtendedColors.onSuccessContainer.copy(alpha = 0.1f)
                    )
                ) {
                    Text(
                        text = result.scenario,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium,
                        color = LightExtendedColors.onSuccessContainer,
                        modifier = Modifier.padding(16.dp)
                    )
                }

                // Who needed to sign
                Text(
                    text = "Authorization Required From",
                    style = MaterialTheme.typography.labelMedium,
                    color = LightExtendedColors.onSuccessContainer.copy(alpha = 0.7f)
                )
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = LightExtendedColors.onSuccessContainer.copy(alpha = 0.1f)
                    )
                ) {
                    if (result.whoNeedsToSign.isEmpty()) {
                        Text(
                            text = "None (automatic authorization)",
                            style = MaterialTheme.typography.bodyMedium,
                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                            color = LightExtendedColors.onSuccessContainer,
                            modifier = Modifier.padding(16.dp)
                        )
                    } else {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            result.whoNeedsToSign.forEach { accountId ->
                                Text(
                                    text = "• ${accountId.take(8)}...${accountId.takeLast(8)}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontFamily = FontFamily.Monospace,
                                    color = LightExtendedColors.onSuccessContainer
                                )
                            }
                        }
                    }
                }
            }

            Text(
                text = "The SDK automatically detected the authorization scenario using needsNonInvokerSigningBy() " +
                        "and conditionally signed auth entries only when needed. This is the production-ready pattern " +
                        "for handling both same-invoker and different-invoker scenarios.",
                style = MaterialTheme.typography.bodySmall,
                color = LightExtendedColors.onSuccessContainer
            )
        }
    }

    // Transaction Hash Card
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Transaction Hash",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = result.transactionHash,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            IconButton(onClick = {
                scope.launch {
                    val clipboard = getClipboard()
                    val copied = clipboard.copyToClipboard(result.transactionHash)
                    snackbarHostState.showSnackbar(
                        if (copied) "Transaction hash copied to clipboard"
                        else "Failed to copy to clipboard"
                    )
                }
            }) {
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = "Copy transaction hash",
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}

@Composable
private fun ErrorCard(message: String, exception: Throwable?) {
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
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
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
                    text = "• Ensure the contract ID is correct and the auth contract is deployed on testnet",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Text(
                    text = "• Verify both accounts have sufficient XLM balance for fees",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Text(
                    text = "• Check that secret keys match their respective account IDs",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Text(
                    text = "• Deploy the auth contract (soroban_auth_contract.wasm) using 'Deploy a Smart Contract'",
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
