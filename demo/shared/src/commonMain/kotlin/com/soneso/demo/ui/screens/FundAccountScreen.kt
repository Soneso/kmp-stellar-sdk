package com.soneso.demo.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.soneso.demo.platform.getClipboard
import com.soneso.demo.stellar.AccountFundingResult
import com.soneso.demo.stellar.KeyPairGenerationResult
import com.soneso.demo.stellar.fundTestnetAccount
import com.soneso.demo.stellar.generateRandomKeyPair
import com.soneso.demo.ui.FormValidation
import com.soneso.demo.ui.components.AnimatedButton
import com.soneso.demo.ui.components.InfoCard
import com.soneso.demo.ui.components.StellarTopBar
import kotlinx.coroutines.launch

class FundAccountScreen : Screen {
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val coroutineScope = rememberCoroutineScope()

        // State management
        var accountId by remember { mutableStateOf("") }
        var generatedSecretSeed by remember { mutableStateOf("") }
        var secretSeedVisible by remember { mutableStateOf(false) }
        var isLoading by remember { mutableStateOf(false) }
        var isGeneratingKey by remember { mutableStateOf(false) }
        var fundingResult by remember { mutableStateOf<AccountFundingResult?>(null) }
        var snackbarMessage by remember { mutableStateOf<String?>(null) }
        var validationError by remember { mutableStateOf<String?>(null) }

        val snackbarHostState = remember { SnackbarHostState() }

        // Show snackbar when message changes
        LaunchedEffect(snackbarMessage) {
            snackbarMessage?.let { message ->
                snackbarHostState.showSnackbar(
                    message = message,
                    duration = SnackbarDuration.Short
                )
                snackbarMessage = null
            }
        }

        Scaffold(
            topBar = {
                StellarTopBar(
                    title = "Fund Testnet Account",
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
                    title = "Friendbot: Testnet Account Funding",
                    description = "The Friendbot is a Horizon API endpoint that funds accounts with 10,000 XLM on the testnet network. " +
                            "This is essential for testing your Stellar applications without using real funds. " +
                            "Enter a public key or generate a new keypair to fund the account with testnet XLM."
                )

                // Public Key Input Field with modern styling
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
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Account Information",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            color = Color(0xFFA85A00) // StarlightGoldDark
                        )

                        OutlinedTextField(
                            value = accountId,
                            onValueChange = {
                                accountId = it.trim()
                                validationError = null
                                fundingResult = null
                            },
                            label = { Text("Public Key (Account ID)") },
                            placeholder = { Text("G...") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            isError = validationError != null,
                            supportingText = validationError?.let { error ->
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
                                onDone = {
                                    // Trigger funding on keyboard done
                                    val error = FormValidation.validateAccountIdField(accountId)
                                    if (error != null) {
                                        validationError = error
                                    } else {
                                        coroutineScope.launch {
                                            isLoading = true
                                            fundingResult = null
                                            try {
                                                fundingResult = fundTestnetAccount(accountId)
                                            } finally {
                                                isLoading = false
                                            }
                                        }
                                    }
                                }
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

                // Secret Seed Field (only shown when generated)
                if (generatedSecretSeed.isNotEmpty()) {
                    val visibilityInteractionSource = remember { MutableInteractionSource() }
                    val isVisibilityHovered by visibilityInteractionSource.collectIsHoveredAsState()

                    val copyInteractionSource = remember { MutableInteractionSource() }
                    val isCopyHovered by copyInteractionSource.collectIsHoveredAsState()

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
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Secret Seed (Private Key)",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = Color(0xFFA85A00) // StarlightGoldDark
                                )

                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    val visibilityButtonScale by animateFloatAsState(
                                        targetValue = if (isVisibilityHovered) 1.1f else 1f,
                                        animationSpec = tween(durationMillis = 150),
                                        label = "visibility_button_scale"
                                    )

                                    IconButton(
                                        onClick = { secretSeedVisible = !secretSeedVisible },
                                        modifier = Modifier
                                            .size(40.dp)
                                            .scale(visibilityButtonScale)
                                            .hoverable(interactionSource = visibilityInteractionSource)
                                    ) {
                                        Icon(
                                            imageVector = if (secretSeedVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                            contentDescription = if (secretSeedVisible) "Hide secret" else "Show secret",
                                            tint = Color(0xFFD97706), // StarlightGold
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }

                                    val copyButtonScale by animateFloatAsState(
                                        targetValue = if (isCopyHovered) 1.1f else 1f,
                                        animationSpec = tween(durationMillis = 150),
                                        label = "copy_button_scale"
                                    )

                                    IconButton(
                                        onClick = {
                                            coroutineScope.launch {
                                                try {
                                                    getClipboard().copyToClipboard(generatedSecretSeed)
                                                    snackbarHostState.showSnackbar("Secret seed copied to clipboard")
                                                } catch (e: Exception) {
                                                    snackbarHostState.showSnackbar("Failed to copy: ${e.message}")
                                                }
                                            }
                                        },
                                        modifier = Modifier
                                            .size(40.dp)
                                            .scale(copyButtonScale)
                                            .hoverable(interactionSource = copyInteractionSource)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ContentCopy,
                                            contentDescription = "Copy to clipboard",
                                            tint = Color(0xFFD97706), // StarlightGold
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }

                            SelectionContainer {
                                Text(
                                    text = if (secretSeedVisible) {
                                        generatedSecretSeed
                                    } else {
                                        "•".repeat(56)
                                    },
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = FontWeight.Medium
                                    ),
                                    fontFamily = FontFamily.Monospace,
                                    color = Color(0xFFA85A00).copy(alpha = 0.9f),
                                    modifier = Modifier.fillMaxWidth(),
                                    lineHeight = 24.sp
                                )
                            }

                            Text(
                                text = "NEVER share this! Anyone with this seed can access your account.",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontWeight = FontWeight.Medium
                                ),
                                color = Color(0xFFA85A00).copy(alpha = 0.7f),
                                lineHeight = 20.sp
                            )
                        }
                    }
                }

                // Action buttons with hover effects
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Get lumens button
                    AnimatedButton(
                        onClick = {
                            val error = FormValidation.validateAccountIdField(accountId)
                            if (error != null) {
                                validationError = error
                                snackbarMessage = error
                            } else {
                                coroutineScope.launch {
                                    isLoading = true
                                    fundingResult = null
                                    try {
                                        fundingResult = fundTestnetAccount(accountId)
                                    } finally {
                                        isLoading = false
                                    }
                                }
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp),
                        enabled = !isLoading && !isGeneratingKey && accountId.isNotBlank(),
                        isLoading = isLoading
                    ) {
                        if (!isLoading) {
                            Icon(
                                imageVector = Icons.Default.AccountBalance,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                        }
                        Text(
                            text = if (isLoading) "Funding..." else "Get Lumens",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                    }

                    // Generate and fill button (outlined style)
                    AnimatedButton(
                        onClick = {
                            coroutineScope.launch {
                                isGeneratingKey = true
                                try {
                                    when (val result = generateRandomKeyPair()) {
                                        is KeyPairGenerationResult.Success -> {
                                            accountId = result.keyPair.getAccountId()
                                            generatedSecretSeed = (result.keyPair.getSecretSeed() ?: CharArray(0)).concatToString()
                                            secretSeedVisible = false
                                            validationError = null
                                            fundingResult = null
                                            snackbarMessage = "New keypair generated and filled"
                                        }
                                        is KeyPairGenerationResult.Error -> {
                                            snackbarMessage = "Failed to generate keypair: ${result.message}"
                                        }
                                    }
                                } finally {
                                    isGeneratingKey = false
                                }
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp)
                            .border(
                                width = 2.dp,
                                color = Color(0xFF5E3FBE), // CosmicPurple
                                shape = RoundedCornerShape(12.dp)
                            ),
                        enabled = !isLoading && !isGeneratingKey,
                        isLoading = isGeneratingKey,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.Transparent,
                            contentColor = Color(0xFF5E3FBE) // CosmicPurple
                        ),
                        shadowColor = Color.Transparent
                    ) {
                        if (!isGeneratingKey) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text(
                            text = if (isGeneratingKey) "Generating..." else "Generate & Fill",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                    }
                }

                // Result display
                fundingResult?.let { result ->
                    when (result) {
                        is AccountFundingResult.Success -> {
                            // Success card with teal celestial styling
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
                                    Text(
                                        text = "Funded",
                                        style = MaterialTheme.typography.titleLarge.copy(
                                            fontWeight = FontWeight.Bold
                                        ),
                                        color = Color(0xFF0F766E) // NebulaTealDark
                                    )
                                    SelectionContainer {
                                        Text(
                                            text = "${result.accountId} has been successfully funded on the Stellar testnet.",
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = Color(0xFF0F766E).copy(alpha = 0.9f),
                                            lineHeight = 24.sp
                                        )
                                    }
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
                                                text = "Details:",
                                                style = MaterialTheme.typography.labelLarge.copy(
                                                    fontWeight = FontWeight.SemiBold
                                                ),
                                                color = Color(0xFF0F766E)
                                            )
                                            SelectionContainer {
                                                Text(
                                                    text = result.message,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontFamily = FontFamily.Monospace,
                                                    color = Color(0xFF0F766E).copy(alpha = 0.85f),
                                                    lineHeight = 20.sp
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        is AccountFundingResult.Error -> {
                            // Error card
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
                                        text = "Funding Failed",
                                        style = MaterialTheme.typography.titleLarge.copy(
                                            fontWeight = FontWeight.Bold
                                        ),
                                        color = Color(0xFF991B1B) // NovaRedDark
                                    )
                                    Text(
                                        text = result.message,
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = Color(0xFF991B1B).copy(alpha = 0.9f),
                                        lineHeight = 24.sp
                                    )
                                    result.exception?.let { exception ->
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

                            // Troubleshooting tips card
                            InfoCard(modifier = Modifier.fillMaxWidth()) {
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
                                    TroubleshootingItem("Check that the account ID is valid (starts with 'G' and is 56 characters)")
                                    TroubleshootingItem("If the account was already funded, it cannot be funded again")
                                    TroubleshootingItem("Verify you have an internet connection")
                                    TroubleshootingItem("Try generating a new keypair if the issue persists")
                                }
                            }
                        }
                    }
                }
            }
                }
        }
    }
}

/**
 * Composable for troubleshooting list items
 */
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

/**
 * Shortens an account ID for display purposes.
 * Shows first 4 and last 4 characters with "..." in between.
 *
 * @param accountId The full account ID
 * @return Shortened account ID (e.g., "GABC...XYZ1")
 */
private fun shortenAccountId(accountId: String): String {
    return if (accountId.length > 12) {
        "${accountId.take(4)}...${accountId.takeLast(4)}"
    } else {
        accountId
    }
}
