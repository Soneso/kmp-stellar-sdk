package com.soneso.demo.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.Check
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
import com.soneso.demo.stellar.TrustAssetResult
import com.soneso.demo.stellar.trustAsset
import com.soneso.demo.ui.theme.LightExtendedColors
import com.soneso.stellar.sdk.ChangeTrustOperation
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlin.time.Duration.Companion.seconds

class TrustAssetScreen : Screen {
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val coroutineScope = rememberCoroutineScope()

        // State management
        var accountId by remember { mutableStateOf("") }
        var assetCode by remember { mutableStateOf("") }
        var assetIssuer by remember { mutableStateOf("") }
        var trustLimit by remember { mutableStateOf("") }
        var secretSeed by remember { mutableStateOf("") }
        var isLoading by remember { mutableStateOf(false) }
        var trustResult by remember { mutableStateOf<TrustAssetResult?>(null) }
        var validationErrors by remember { mutableStateOf<Map<String, String>>(emptyMap()) }

        val snackbarHostState = remember { SnackbarHostState() }

        // Validation functions
        fun validateInputs(): Map<String, String> {
            val errors = mutableMapOf<String, String>()

            if (accountId.isBlank()) {
                errors["accountId"] = "Account ID is required"
            } else if (!accountId.startsWith('G')) {
                errors["accountId"] = "Account ID must start with 'G'"
            } else if (accountId.length != 56) {
                errors["accountId"] = "Account ID must be 56 characters"
            }

            if (assetCode.isBlank()) {
                errors["assetCode"] = "Asset code is required"
            } else if (assetCode.length > 12) {
                errors["assetCode"] = "Asset code cannot exceed 12 characters"
            } else {
                val invalidChars = assetCode.filter { char ->
                    char !in 'A'..'Z' && char !in '0'..'9'
                }
                if (invalidChars.isNotEmpty()) {
                    errors["assetCode"] = "Only uppercase letters and digits allowed"
                }
            }

            if (assetIssuer.isBlank()) {
                errors["assetIssuer"] = "Asset issuer is required"
            } else if (!assetIssuer.startsWith('G')) {
                errors["assetIssuer"] = "Asset issuer must start with 'G'"
            } else if (assetIssuer.length != 56) {
                errors["assetIssuer"] = "Asset issuer must be 56 characters"
            }

            if (trustLimit.isNotBlank()) {
                try {
                    val limitValue = trustLimit.toDouble()
                    if (limitValue < 0) {
                        errors["trustLimit"] = "Trust limit cannot be negative"
                    }
                } catch (e: NumberFormatException) {
                    errors["trustLimit"] = "Invalid number format"
                }
            }

            if (secretSeed.isBlank()) {
                errors["secretSeed"] = "Secret seed is required"
            } else if (!secretSeed.startsWith('S')) {
                errors["secretSeed"] = "Secret seed must start with 'S'"
            } else if (secretSeed.length != 56) {
                errors["secretSeed"] = "Secret seed must be 56 characters"
            }

            return errors
        }

        // Function to submit trust asset transaction
        fun submitTrustAsset() {
            val errors = validateInputs()
            if (errors.isNotEmpty()) {
                validationErrors = errors
                return
            }

            coroutineScope.launch {
                isLoading = true
                trustResult = null
                validationErrors = emptyMap()
                try {
                    val limit = if (trustLimit.isBlank()) {
                        ChangeTrustOperation.MAX_LIMIT
                    } else {
                        trustLimit
                    }
                    // Add 60 second timeout to prevent indefinite hanging
                    trustResult = withTimeout(60.seconds) {
                        trustAsset(
                            accountId = accountId,
                            assetCode = assetCode,
                            assetIssuer = assetIssuer,
                            secretSeed = secretSeed,
                            limit = limit,
                            useTestnet = true
                        )
                    }
                } catch (e: Exception) {
                    // Catch timeout and other exceptions
                    trustResult = TrustAssetResult.Error(
                        message = "Request timed out or failed: ${e.message ?: "Unknown error"}",
                        exception = e
                    )
                } finally {
                    isLoading = false
                }
            }
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Trust Asset") },
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
                            text = "Establish a trustline to an asset",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Text(
                            text = "A trustline is required before an account can hold non-native assets (assets other than XLM). " +
                                    "The trustline includes a limit that defines the maximum amount of the asset the account is willing to hold.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }

                // Important Notes Card
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
                            text = "Important Notes",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                        Column(
                            modifier = Modifier.padding(start = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "• Account must have at least 0.5 XLM for the trustline reserve",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                            Text(
                                text = "• Asset codes must be 1-12 uppercase alphanumeric characters",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                            Text(
                                text = "• Leave limit empty for maximum trust (recommended)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                            Text(
                                text = "• Setting limit to '0' removes the trustline (requires zero asset balance)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                    }
                }

                // Account ID Input
                OutlinedTextField(
                    value = accountId,
                    onValueChange = {
                        accountId = it.trim()
                        validationErrors = validationErrors - "accountId"
                        trustResult = null
                    },
                    label = { Text("Account ID") },
                    placeholder = { Text("G...") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = validationErrors.containsKey("accountId"),
                    supportingText = validationErrors["accountId"]?.let { error ->
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

                // Asset Code Input
                OutlinedTextField(
                    value = assetCode,
                    onValueChange = {
                        // Auto-uppercase for asset codes
                        assetCode = it.uppercase().trim()
                        validationErrors = validationErrors - "assetCode"
                        trustResult = null
                    },
                    label = { Text("Asset Code") },
                    placeholder = { Text("USD, EUR, USDC, etc.") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = validationErrors.containsKey("assetCode"),
                    supportingText = validationErrors["assetCode"]?.let { error ->
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

                // Asset Issuer Input
                OutlinedTextField(
                    value = assetIssuer,
                    onValueChange = {
                        assetIssuer = it.trim()
                        validationErrors = validationErrors - "assetIssuer"
                        trustResult = null
                    },
                    label = { Text("Asset Issuer") },
                    placeholder = { Text("G...") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = validationErrors.containsKey("assetIssuer"),
                    supportingText = validationErrors["assetIssuer"]?.let { error ->
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

                // Trust Limit Input (Optional)
                OutlinedTextField(
                    value = trustLimit,
                    onValueChange = {
                        trustLimit = it.trim()
                        validationErrors = validationErrors - "trustLimit"
                        trustResult = null
                    },
                    label = { Text("Trust Limit (Optional)") },
                    placeholder = { Text("Leave empty for maximum") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = validationErrors.containsKey("trustLimit"),
                    supportingText = validationErrors["trustLimit"]?.let { error ->
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

                // Secret Seed Input
                OutlinedTextField(
                    value = secretSeed,
                    onValueChange = {
                        secretSeed = it.trim()
                        validationErrors = validationErrors - "secretSeed"
                        trustResult = null
                    },
                    label = { Text("Secret Seed") },
                    placeholder = { Text("S...") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    isError = validationErrors.containsKey("secretSeed"),
                    supportingText = validationErrors["secretSeed"]?.let { error ->
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
                        onDone = { submitTrustAsset() }
                    )
                )

                // Submit button
                Button(
                    onClick = { submitTrustAsset() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    enabled = !isLoading && accountId.isNotBlank() && assetCode.isNotBlank() &&
                            assetIssuer.isNotBlank() && secretSeed.isNotBlank()
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Creating Trustline...")
                    } else {
                        Icon(
                            imageVector = Icons.Default.AttachMoney,
                            contentDescription = null
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Establish Trustline")
                    }
                }

                // Result display
                trustResult?.let { result ->
                    when (result) {
                        is TrustAssetResult.Success -> {
                            TrustAssetSuccessCard(result)
                        }
                        is TrustAssetResult.Error -> {
                            TrustAssetErrorCard(result)
                        }
                    }
                }

                // Placeholder when no action taken
                if (trustResult == null && !isLoading &&
                    (accountId.isBlank() || assetCode.isBlank() || assetIssuer.isBlank() || secretSeed.isBlank())
                ) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Icon(
                        imageVector = Icons.Default.AttachMoney,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                    )
                    Text(
                        text = "Fill in the required fields to establish a trustline to an asset",
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
private fun TrustAssetSuccessCard(success: TrustAssetResult.Success) {
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
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = LightExtendedColors.onSuccessContainer,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = "Trustline Established",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = LightExtendedColors.onSuccessContainer
                )
            }
            Text(
                text = success.message,
                style = MaterialTheme.typography.bodyMedium,
                color = LightExtendedColors.onSuccessContainer
            )
        }
    }

    // Transaction Details Card
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Transaction Details",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            HorizontalDivider()

            TrustAssetDetailRow("Asset Code", success.assetCode)
            TrustAssetDetailRow("Asset Issuer", success.assetIssuer, monospace = true)
            TrustAssetDetailRow(
                "Trust Limit",
                if (success.limit == ChangeTrustOperation.MAX_LIMIT) {
                    "Maximum (${success.limit})"
                } else {
                    success.limit
                }
            )
            TrustAssetDetailRow("Transaction Hash", success.transactionHash, monospace = true)
        }
    }

    // Next Steps Card
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
                text = "What's Next?",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Column(
                modifier = Modifier.padding(start = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "• You can now receive ${success.assetCode} from the asset issuer",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Text(
                    text = "• Check your account balances to see the new trustline",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Text(
                    text = "• Use the 'Fetch Account Details' feature to view your trustlines",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Text(
                    text = "• You can modify the trust limit or remove the trustline later",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }
    }
}

@Composable
private fun TrustAssetDetailRow(
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
private fun TrustAssetErrorCard(error: TrustAssetResult.Error) {
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
                text = "Failed to Establish Trustline",
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
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Column(
                modifier = Modifier.padding(start = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "• Verify all inputs are valid (account ID and issuer start with 'G', secret seed starts with 'S')",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Text(
                    text = "• Ensure the account has been funded (at least 0.5 XLM for reserve)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Text(
                    text = "• Check that the secret seed matches the account ID",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Text(
                    text = "• Verify the asset issuer account exists on the network",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Text(
                    text = "• Ensure you have a stable internet connection",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Text(
                    text = "• Asset codes must be uppercase letters and digits only",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }
    }
}
