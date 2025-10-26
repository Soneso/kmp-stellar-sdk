package com.soneso.demo.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
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
import com.soneso.demo.platform.getClipboard
import com.soneso.demo.stellar.TrustAssetResult
import com.soneso.demo.stellar.trustAsset
import com.soneso.demo.ui.FormValidation
import com.soneso.demo.ui.components.AnimatedButton
import com.soneso.demo.ui.components.InfoCard
import com.soneso.demo.ui.components.StellarTopBar
import com.soneso.stellar.sdk.ChangeTrustOperation
import kotlinx.coroutines.CoroutineScope
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
        var assetCode by remember { mutableStateOf("SRT") }
        var assetIssuer by remember { mutableStateOf("GCDNJUBQSX7AJWLJACMJ7I4BC3Z47BQUTMHEICZLE6MU4KQBRYG5JY6B") }
        var trustLimit by remember { mutableStateOf("") }
        var secretSeed by remember { mutableStateOf("") }
        var isLoading by remember { mutableStateOf(false) }
        var trustResult by remember { mutableStateOf<TrustAssetResult?>(null) }
        var validationErrors by remember { mutableStateOf<Map<String, String>>(emptyMap()) }

        val snackbarHostState = remember { SnackbarHostState() }

        // Validation functions
        // Validation functions
        fun validateInputs(): Map<String, String> {
            val errors = mutableMapOf<String, String>()

            FormValidation.validateAccountIdField(accountId)?.let {
                errors["accountId"] = it
            }

            FormValidation.validateAssetCodeField(assetCode)?.let {
                errors["assetCode"] = it
            }

            FormValidation.validateAccountIdField(assetIssuer)?.let {
                errors["assetIssuer"] = it
            }

            // Trust limit validation (domain-specific, not in FormValidation)
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

            FormValidation.validateSecretSeedField(secretSeed)?.let {
                errors["secretSeed"] = it
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
                StellarTopBar(
                    title = "Trust Asset",
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
                // Information card with celestial styling
                InfoCard(
                    modifier = Modifier.fillMaxWidth(),
                    title = "Establish Asset Trustline",
                    description = "A trustline is required before an account can hold non-native assets. The pre-filled SRT asset is a testnet example from Stellar's testnet anchor. You can replace these values with any asset you want to trust."
                )

                // Input Fields Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, Color(0xFFD97706).copy(alpha = 0.3f)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFFFFFBF0) // Warm light gold background
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = "Asset Configuration",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            color = Color(0xFFA85A00) // StarlightGoldDark
                        )

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
                                        color = MaterialTheme.colorScheme.error,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            },
                            keyboardOptions = KeyboardOptions(
                                imeAction = ImeAction.Next
                            ),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFFD97706),
                                focusedLabelColor = Color(0xFFD97706),
                                cursorColor = Color(0xFFD97706)
                            ),
                            shape = RoundedCornerShape(12.dp)
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
                                        color = MaterialTheme.colorScheme.error,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            },
                            keyboardOptions = KeyboardOptions(
                                imeAction = ImeAction.Next
                            ),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFFD97706),
                                focusedLabelColor = Color(0xFFD97706),
                                cursorColor = Color(0xFFD97706)
                            ),
                            shape = RoundedCornerShape(12.dp)
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
                                        color = MaterialTheme.colorScheme.error,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            },
                            keyboardOptions = KeyboardOptions(
                                imeAction = ImeAction.Next
                            ),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFFD97706),
                                focusedLabelColor = Color(0xFFD97706),
                                cursorColor = Color(0xFFD97706)
                            ),
                            shape = RoundedCornerShape(12.dp)
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
                                        color = MaterialTheme.colorScheme.error,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            },
                            keyboardOptions = KeyboardOptions(
                                imeAction = ImeAction.Next
                            ),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFFD97706),
                                focusedLabelColor = Color(0xFFD97706),
                                cursorColor = Color(0xFFD97706)
                            ),
                            shape = RoundedCornerShape(12.dp)
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
                                        color = MaterialTheme.colorScheme.error,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            },
                            keyboardOptions = KeyboardOptions(
                                imeAction = ImeAction.Done
                            ),
                            keyboardActions = KeyboardActions(
                                onDone = { submitTrustAsset() }
                            ),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFFD97706),
                                focusedLabelColor = Color(0xFFD97706),
                                cursorColor = Color(0xFFD97706)
                            ),
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                }

                // Submit button using AnimatedButton component
                AnimatedButton(
                    onClick = { submitTrustAsset() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    enabled = !isLoading && accountId.isNotBlank() && assetCode.isNotBlank() &&
                            assetIssuer.isNotBlank() && secretSeed.isNotBlank(),
                    isLoading = isLoading,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF0A4FD6), // StellarBlue
                        contentColor = Color.White
                    )
                ) {
                    if (!isLoading) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                    }
                    Text(
                        text = if (isLoading) "Creating Trustline..." else "Create Trustline",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                }

                // Result display
                trustResult?.let { result ->
                    when (result) {
                        is TrustAssetResult.Success -> {
                            TrustAssetSuccessCard(result, snackbarHostState, coroutineScope)
                        }
                        is TrustAssetResult.Error -> {
                            TrustAssetErrorCard(result)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TrustAssetSuccessCard(success: TrustAssetResult.Success, snackbarHostState: SnackbarHostState, scope: CoroutineScope) {
    // Success header card with teal celestial styling
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, Color(0xFF0D9488).copy(alpha = 0.3f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFF0FDFA) // NebulaTealContainer
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
                    tint = Color(0xFF0F766E), // NebulaTealDark
                    modifier = Modifier.size(28.dp)
                )
                Text(
                    text = "Success",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = Color(0xFF0F766E) // NebulaTealDark
                )
            }
            SelectionContainer {
                Text(
                    text = if (success.limit == "0" || success.limit.toDoubleOrNull() == 0.0) {
                        "Trustline successfully removed"
                    } else {
                        "Trustline established successfully"
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color(0xFF0F766E).copy(alpha = 0.9f),
                    lineHeight = 24.sp
                )
            }
        }
    }

    // Transaction Hash Card with blue styling
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, Color(0xFF0A4FD6).copy(alpha = 0.3f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFE8F1FF) // NebulaBlue
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Transaction Hash",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = Color(0xFF0639A3) // StellarBlueDark
                )

                val copyInteractionSource = remember { MutableInteractionSource() }
                val isCopyHovered by copyInteractionSource.collectIsHoveredAsState()
                val copyButtonScale by animateFloatAsState(
                    targetValue = if (isCopyHovered) 1.1f else 1f,
                    animationSpec = tween(durationMillis = 150),
                    label = "copy_button_scale"
                )

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
                    modifier = Modifier
                        .scale(copyButtonScale)
                        .hoverable(interactionSource = copyInteractionSource)
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Copy transaction hash",
                        tint = Color(0xFF0A4FD6), // StellarBlue
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            SelectionContainer {
                Text(
                    text = success.transactionHash,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Medium
                    ),
                    fontFamily = FontFamily.Monospace,
                    color = Color(0xFF0639A3).copy(alpha = 0.9f),
                    lineHeight = 22.sp
                )
            }
        }
    }

    // Transaction Details Card with blue styling
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, Color(0xFF0A4FD6).copy(alpha = 0.3f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFE8F1FF) // NebulaBlue
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Transaction Details",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold
                ),
                color = Color(0xFF0639A3) // StellarBlueDark
            )

            HorizontalDivider(color = Color(0xFF0A4FD6).copy(alpha = 0.2f))

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
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = FontWeight.SemiBold
            ),
            color = Color(0xFF0639A3).copy(alpha = 0.7f) // StellarBlueDark
        )
        SelectionContainer {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Medium
                ),
                fontFamily = if (monospace) FontFamily.Monospace else FontFamily.Default,
                color = Color(0xFF0639A3).copy(alpha = 0.9f),
                lineHeight = 22.sp
            )
        }
    }
}

@Composable
private fun TrustAssetErrorCard(error: TrustAssetResult.Error) {
    // Error card with red celestial styling
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, Color(0xFFDC2626).copy(alpha = 0.3f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFFEF2F2) // NovaRedContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Failed to Establish Trustline",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold
                ),
                color = Color(0xFF991B1B) // NovaRedDark
            )
            SelectionContainer {
                Text(
                    text = error.message,
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color(0xFF991B1B).copy(alpha = 0.9f),
                    lineHeight = 24.sp
                )
            }
            error.exception?.let { exception ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color.White
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Technical Details:",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = Color(0xFF991B1B)
                        )
                        SelectionContainer {
                            Text(
                                text = exception.message ?: "Unknown error",
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                color = Color(0xFF991B1B).copy(alpha = 0.85f),
                                lineHeight = 18.sp
                            )
                        }
                    }
                }
            }
        }
    }

    // Troubleshooting tips card with purple styling
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFF3EFFF) // StardustPurple
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Troubleshooting Tips",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold
                ),
                color = Color(0xFF3D2373) // CosmicPurpleDark
            )
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TroubleshootingItem("Verify all inputs are valid (account ID and issuer start with 'G', secret seed starts with 'S')")
                TroubleshootingItem("Ensure the account has been funded (at least 0.5 XLM for reserve)")
                TroubleshootingItem("Check that the secret seed matches the account ID")
                TroubleshootingItem("Verify the asset issuer account exists on the network")
                TroubleshootingItem("Asset codes must be uppercase letters and digits only")
            }
                }
        }
    }
}

@Composable
private fun TroubleshootingItem(text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "•",
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFF3D2373), // CosmicPurpleDark
            modifier = Modifier.padding(top = 2.dp)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFF3D2373).copy(alpha = 0.85f),
            lineHeight = 22.sp,
            modifier = Modifier.weight(1f)
        )
    }
}
