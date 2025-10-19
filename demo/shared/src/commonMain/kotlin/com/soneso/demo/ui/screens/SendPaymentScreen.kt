package com.soneso.demo.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.automirrored.filled.Send
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
import com.soneso.demo.stellar.SendPaymentResult
import com.soneso.demo.stellar.sendPayment
import com.soneso.demo.ui.theme.LightExtendedColors
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlin.time.Duration.Companion.seconds

class SendPaymentScreen : Screen {
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val coroutineScope = rememberCoroutineScope()

        // State management
        var sourceAccountId by remember { mutableStateOf("") }
        var destinationAccountId by remember { mutableStateOf("") }
        var assetType by remember { mutableStateOf(AssetType.NATIVE) }
        var assetCode by remember { mutableStateOf("") }
        var assetIssuer by remember { mutableStateOf("") }
        var amount by remember { mutableStateOf("") }
        var secretSeed by remember { mutableStateOf("") }
        var isLoading by remember { mutableStateOf(false) }
        var paymentResult by remember { mutableStateOf<SendPaymentResult?>(null) }
        var validationErrors by remember { mutableStateOf<Map<String, String>>(emptyMap()) }

        val snackbarHostState = remember { SnackbarHostState() }

        // Validation functions
        fun validateInputs(): Map<String, String> {
            val errors = mutableMapOf<String, String>()

            if (sourceAccountId.isBlank()) {
                errors["sourceAccountId"] = "Source account ID is required"
            } else if (!sourceAccountId.startsWith('G')) {
                errors["sourceAccountId"] = "Source account ID must start with 'G'"
            } else if (sourceAccountId.length != 56) {
                errors["sourceAccountId"] = "Source account ID must be 56 characters"
            }

            if (destinationAccountId.isBlank()) {
                errors["destinationAccountId"] = "Destination account ID is required"
            } else if (!destinationAccountId.startsWith('G')) {
                errors["destinationAccountId"] = "Destination account ID must start with 'G'"
            } else if (destinationAccountId.length != 56) {
                errors["destinationAccountId"] = "Destination account ID must be 56 characters"
            }

            if (assetType == AssetType.ISSUED) {
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
            }

            if (amount.isBlank()) {
                errors["amount"] = "Amount is required"
            } else {
                try {
                    val amountValue = amount.toDouble()
                    if (amountValue <= 0) {
                        errors["amount"] = "Amount must be greater than 0"
                    }
                } catch (e: NumberFormatException) {
                    errors["amount"] = "Invalid number format"
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

        // Function to submit payment
        fun submitPayment() {
            val errors = validateInputs()
            if (errors.isNotEmpty()) {
                validationErrors = errors
                return
            }

            coroutineScope.launch {
                isLoading = true
                paymentResult = null
                validationErrors = emptyMap()
                try {
                    // Add 60 second timeout to prevent indefinite hanging
                    paymentResult = withTimeout(60.seconds) {
                        sendPayment(
                            sourceAccountId = sourceAccountId,
                            destinationAccountId = destinationAccountId,
                            assetCode = if (assetType == AssetType.NATIVE) "native" else assetCode,
                            assetIssuer = if (assetType == AssetType.NATIVE) null else assetIssuer,
                            amount = amount,
                            secretSeed = secretSeed,
                            useTestnet = true
                        )
                    }
                } catch (e: Exception) {
                    // Catch timeout and other exceptions
                    paymentResult = SendPaymentResult.Error(
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
                    title = { Text("Send a Payment") },
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
                            text = "Send a payment on the Stellar network",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Text(
                            text = "Transfer XLM (native asset) or any issued asset to another Stellar account. " +
                                    "The destination account must exist, and for issued assets, must have an established trustline.",
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
                                text = "• Destination account must exist on the network",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                            Text(
                                text = "• For issued assets, destination must have a trustline",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                            Text(
                                text = "• Transaction fee (0.00001 XLM) is in addition to the payment",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                            Text(
                                text = "• Minimum payment amount is 0.0000001",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                    }
                }

                // Source Account ID Input
                OutlinedTextField(
                    value = sourceAccountId,
                    onValueChange = {
                        sourceAccountId = it.trim()
                        validationErrors = validationErrors - "sourceAccountId"
                        paymentResult = null
                    },
                    label = { Text("Source Account ID") },
                    placeholder = { Text("G... (your account)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = validationErrors.containsKey("sourceAccountId"),
                    supportingText = validationErrors["sourceAccountId"]?.let { error ->
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

                // Destination Account ID Input
                OutlinedTextField(
                    value = destinationAccountId,
                    onValueChange = {
                        destinationAccountId = it.trim()
                        validationErrors = validationErrors - "destinationAccountId"
                        paymentResult = null
                    },
                    label = { Text("Destination Account ID") },
                    placeholder = { Text("G... (recipient's account)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = validationErrors.containsKey("destinationAccountId"),
                    supportingText = validationErrors["destinationAccountId"]?.let { error ->
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

                // Asset Type Selection
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Asset Type",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = assetType == AssetType.NATIVE,
                                onClick = {
                                    assetType = AssetType.NATIVE
                                    paymentResult = null
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Native (XLM)")
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = assetType == AssetType.ISSUED,
                                onClick = {
                                    assetType = AssetType.ISSUED
                                    paymentResult = null
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Issued Asset (e.g., USD, EUR)")
                        }
                    }
                }

                // Asset Code Input (only for issued assets)
                if (assetType == AssetType.ISSUED) {
                    OutlinedTextField(
                        value = assetCode,
                        onValueChange = {
                            // Auto-uppercase for asset codes
                            assetCode = it.uppercase().trim()
                            validationErrors = validationErrors - "assetCode"
                            paymentResult = null
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
                            paymentResult = null
                        },
                        label = { Text("Asset Issuer") },
                        placeholder = { Text("G... (issuer's account)") },
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
                }

                // Amount Input
                OutlinedTextField(
                    value = amount,
                    onValueChange = {
                        amount = it.trim()
                        validationErrors = validationErrors - "amount"
                        paymentResult = null
                    },
                    label = { Text("Amount") },
                    placeholder = { Text("10.0") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = validationErrors.containsKey("amount"),
                    supportingText = validationErrors["amount"]?.let { error ->
                        {
                            Text(
                                text = error,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal,
                        imeAction = ImeAction.Next
                    )
                )

                // Secret Seed Input
                OutlinedTextField(
                    value = secretSeed,
                    onValueChange = {
                        secretSeed = it.trim()
                        validationErrors = validationErrors - "secretSeed"
                        paymentResult = null
                    },
                    label = { Text("Source Secret Seed") },
                    placeholder = { Text("S... (for signing)") },
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
                        onDone = { submitPayment() }
                    )
                )

                // Submit button
                Button(
                    onClick = { submitPayment() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    enabled = !isLoading && sourceAccountId.isNotBlank() &&
                            destinationAccountId.isNotBlank() && amount.isNotBlank() &&
                            secretSeed.isNotBlank() &&
                            (assetType == AssetType.NATIVE || (assetCode.isNotBlank() && assetIssuer.isNotBlank()))
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Sending Payment...")
                    } else {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = null
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Send Payment")
                    }
                }

                // Result display
                paymentResult?.let { result ->
                    when (result) {
                        is SendPaymentResult.Success -> {
                            SendPaymentSuccessCard(result)
                        }
                        is SendPaymentResult.Error -> {
                            SendPaymentErrorCard(result)
                        }
                    }
                }

                // Placeholder when no action taken
                if (paymentResult == null && !isLoading &&
                    (sourceAccountId.isBlank() || destinationAccountId.isBlank() ||
                            amount.isBlank() || secretSeed.isBlank())
                ) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                    )
                    Text(
                        text = "Fill in the required fields to send a payment on the Stellar testnet",
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
 * Asset type enum for UI selection.
 */
enum class AssetType {
    NATIVE,
    ISSUED
}

@Composable
private fun SendPaymentSuccessCard(success: SendPaymentResult.Success) {
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
                    text = "Payment Sent Successfully",
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

    // Payment Details Card
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
                text = "Payment Details",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            HorizontalDivider()

            PaymentDetailRow("From", success.source, monospace = true)
            PaymentDetailRow("To", success.destination, monospace = true)
            PaymentDetailRow("Amount", "${success.amount} ${success.assetCode}")

            // Display asset issuer if present (for issued assets)
            success.assetIssuer?.let { issuer ->
                PaymentDetailRow("Asset Issuer", issuer, monospace = true)
            }

            PaymentDetailRow("Transaction Hash", success.transactionHash, monospace = true)
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
                    text = "• The payment has been successfully recorded on the Stellar ledger",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Text(
                    text = "• The destination account now has the funds available",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Text(
                    text = "• Use 'Fetch Account Details' to verify the updated balances",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Text(
                    text = "• View the transaction on Stellar Expert or other block explorers",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }
    }
}

@Composable
private fun PaymentDetailRow(
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
private fun SendPaymentErrorCard(error: SendPaymentResult.Error) {
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
                text = "Payment Failed",
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
                    text = "• Verify all account IDs are valid and start with 'G'",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Text(
                    text = "• Ensure the destination account exists (or create it with CreateAccount)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Text(
                    text = "• Check that the source account has sufficient balance (including fees)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Text(
                    text = "• For issued assets, verify the destination has a trustline",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Text(
                    text = "• Verify the secret seed matches the source account",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Text(
                    text = "• Ensure you have a stable internet connection",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }
    }
}
