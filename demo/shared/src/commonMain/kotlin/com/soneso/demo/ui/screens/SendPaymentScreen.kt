package com.soneso.demo.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.automirrored.filled.Send
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.soneso.demo.platform.getClipboard
import com.soneso.demo.stellar.SendPaymentResult
import com.soneso.demo.stellar.sendPayment
import com.soneso.demo.ui.FormValidation
import com.soneso.demo.ui.components.AnimatedButton
import com.soneso.demo.ui.components.InfoCard
import com.soneso.demo.ui.components.StellarTopBar
import kotlinx.coroutines.CoroutineScope
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
        // Validation functions
        fun validateInputs(): Map<String, String> {
            val errors = mutableMapOf<String, String>()

            FormValidation.validateAccountIdField(sourceAccountId)?.let {
                errors["sourceAccountId"] = it
            }

            FormValidation.validateAccountIdField(destinationAccountId)?.let {
                errors["destinationAccountId"] = it
            }

            if (assetType == AssetType.ISSUED) {
                FormValidation.validateAssetCodeField(assetCode)?.let {
                    errors["assetCode"] = it
                }

                FormValidation.validateAccountIdField(assetIssuer)?.let {
                    errors["assetIssuer"] = it
                }
            }

            // Amount validation (domain-specific, not in FormValidation)
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

            FormValidation.validateSecretSeedField(secretSeed)?.let {
                errors["secretSeed"] = it
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
                StellarTopBar(
                    title = "Send a Payment",
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
                InfoCard(
                    modifier = Modifier.fillMaxWidth(),
                    title = "Send a Payment on the Stellar Network",
                    description = "Transfer XLM (native asset) or any issued asset to another Stellar account. The destination account must exist, and for issued assets, must have an established trustline. Transaction fee (0.00001 XLM) is in addition to the payment amount.",
                    useRoundedShape = false,
                    titleStyle = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp,
                        lineHeight = 22.sp
                    ),
                    descriptionAlpha = 0.8f
                )

                // Payment Configuration Card - Gold
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFFFFFBF0) // Starlight Gold background
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = "Payment Configuration",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            ),
                            color = Color(0xFFA85A00) // Starlight Gold Dark
                        )

                        // Source Account ID
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
                                imeAction = ImeAction.Next
                            )
                        )

                        HorizontalDivider(
                            thickness = 1.dp,
                            color = Color(0xFFD97706).copy(alpha = 0.2f)
                        )

                        // Destination Account ID
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
                                imeAction = ImeAction.Next
                            )
                        )

                        HorizontalDivider(
                            thickness = 1.dp,
                            color = Color(0xFFD97706).copy(alpha = 0.2f)
                        )

                        // Asset Type Selection
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Asset Type",
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontWeight = FontWeight.SemiBold
                                ),
                                color = Color(0xFFA85A00)
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
                                    },
                                    colors = RadioButtonDefaults.colors(
                                        selectedColor = Color(0xFFD97706)
                                    )
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    "Native (XLM)",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = FontWeight.SemiBold
                                    ),
                                    color = Color(0xFFA85A00)
                                )
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
                                    },
                                    colors = RadioButtonDefaults.colors(
                                        selectedColor = Color(0xFFD97706)
                                    )
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    "Issued Asset (e.g., USD, EUR)",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = FontWeight.SemiBold
                                    ),
                                    color = Color(0xFFA85A00)
                                )
                            }
                        }

                        // Asset Code and Issuer (only for issued assets)
                        if (assetType == AssetType.ISSUED) {
                            HorizontalDivider(
                                thickness = 1.dp,
                                color = Color(0xFFD97706).copy(alpha = 0.2f)
                            )

                            OutlinedTextField(
                                value = assetCode,
                                onValueChange = {
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
                                    imeAction = ImeAction.Next
                                )
                            )

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
                                    imeAction = ImeAction.Next
                                )
                            )
                        }

                        HorizontalDivider(
                            thickness = 1.dp,
                            color = Color(0xFFD97706).copy(alpha = 0.2f)
                        )

                        // Amount
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
                                keyboardType = KeyboardType.Decimal,
                                imeAction = ImeAction.Next
                            )
                        )

                        HorizontalDivider(
                            thickness = 1.dp,
                            color = Color(0xFFD97706).copy(alpha = 0.2f)
                        )

                        // Secret Seed
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
                                onDone = { submitPayment() }
                            )
                        )
                    }
                }

                // Submit button with AnimatedButton
                AnimatedButton(
                    onClick = { submitPayment() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    enabled = !isLoading && sourceAccountId.isNotBlank() &&
                            destinationAccountId.isNotBlank() && amount.isNotBlank() &&
                            secretSeed.isNotBlank() &&
                            (assetType == AssetType.NATIVE || (assetCode.isNotBlank() && assetIssuer.isNotBlank())),
                    isLoading = isLoading,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF0A4FD6) // Stellar Blue
                    )
                ) {
                    if (!isLoading) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = null,
                            tint = Color.White
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                    }
                    Text(
                        if (isLoading) "Sending Payment..." else "Send Payment",
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Result display
                paymentResult?.let { result ->
                    when (result) {
                        is SendPaymentResult.Success -> {
                            SendPaymentSuccessCard(result, snackbarHostState, coroutineScope)
                        }
                        is SendPaymentResult.Error -> {
                            SendPaymentErrorCard(result)
                        }
                    }
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
private fun SendPaymentSuccessCard(success: SendPaymentResult.Success, snackbarHostState: SnackbarHostState, scope: CoroutineScope) {
    // Success header card - Teal
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFF0FDFA) // Nebula Teal Container
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
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = Color(0xFF0F766E), // Nebula Teal
                    modifier = Modifier.size(28.dp)
                )
                Text(
                    text = "Success",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp,
                        lineHeight = 22.sp
                    ),
                    color = Color(0xFF0F766E)
                )
            }
            Text(
                text = success.message,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    lineHeight = 22.sp
                ),
                color = Color(0xFF0F766E).copy(alpha = 0.8f)
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
            containerColor = Color(0xFFE8F1FF) // Nebula Blue
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
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = Color(0xFF0639A3)
                )
                Spacer(modifier = Modifier.height(8.dp))
                SelectionContainer {
                    Text(
                        text = success.transactionHash,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            lineHeight = 22.sp
                        ),
                        fontFamily = FontFamily.Monospace,
                        color = Color(0xFF0639A3).copy(alpha = 0.8f)
                    )
                }
            }
            IconButton(
                onClick = {
                    scope.launch {
                        val clipboard = getClipboard()
                        val copied = clipboard.copyToClipboard(success.transactionHash)
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

    // Payment Details Card - Blue
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
            Text(
                text = "Payment Details",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                ),
                color = Color(0xFF0639A3)
            )
            HorizontalDivider(
                thickness = 1.dp,
                color = Color(0xFF0639A3).copy(alpha = 0.2f)
            )

            SelectionContainer {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    PaymentDetailRow("From", success.source, monospace = true)
                    PaymentDetailRow("To", success.destination, monospace = true)
                    PaymentDetailRow("Amount", "${success.amount} ${success.assetCode}")

                    success.assetIssuer?.let { issuer ->
                        PaymentDetailRow("Asset Issuer", issuer, monospace = true)
                    }
                }
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
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge.copy(
                fontWeight = FontWeight.SemiBold
            ),
            color = Color(0xFF0639A3)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(
                lineHeight = 22.sp
            ),
            fontFamily = if (monospace) FontFamily.Monospace else FontFamily.Default,
            color = Color(0xFF0639A3).copy(alpha = 0.8f)
        )
    }
}

@Composable
private fun SendPaymentErrorCard(error: SendPaymentResult.Error) {
    // Error header card - Red
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
            Text(
                text = "Error",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp,
                    lineHeight = 22.sp
                ),
                color = Color(0xFF991B1B) // Nova Red Dark
            )
            Text(
                text = error.message,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    lineHeight = 22.sp
                ),
                color = Color(0xFF991B1B).copy(alpha = 0.8f)
            )

            // Technical details in nested card
            error.exception?.let { exception ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.small,
                    colors = CardDefaults.cardColors(
                        containerColor = Color.White
                    )
                ) {
                    SelectionContainer {
                        Text(
                            text = "Technical details: ${exception.message}",
                            style = MaterialTheme.typography.bodySmall.copy(
                                lineHeight = 20.sp
                            ),
                            fontFamily = FontFamily.Monospace,
                            color = Color(0xFF991B1B).copy(alpha = 0.7f),
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }
        }
    }

    // Troubleshooting tips - Purple
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFF3EFFF) // Stardust Purple
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Troubleshooting",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                ),
                color = Color(0xFF3D2373)
            )
            Column(
                modifier = Modifier.padding(start = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "• Verify all account IDs are valid and start with 'G'",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        lineHeight = 22.sp
                    ),
                    color = Color(0xFF3D2373).copy(alpha = 0.8f)
                )
                Text(
                    text = "• Ensure the destination account exists on the network",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        lineHeight = 22.sp
                    ),
                    color = Color(0xFF3D2373).copy(alpha = 0.8f)
                )
                Text(
                    text = "• Check that the source account has sufficient balance (including fees)",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        lineHeight = 22.sp
                    ),
                    color = Color(0xFF3D2373).copy(alpha = 0.8f)
                )
                Text(
                    text = "• For issued assets, verify the destination has a trustline",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        lineHeight = 22.sp
                    ),
                    color = Color(0xFF3D2373).copy(alpha = 0.8f)
                )
                Text(
                    text = "• Verify the secret seed matches the source account",
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
