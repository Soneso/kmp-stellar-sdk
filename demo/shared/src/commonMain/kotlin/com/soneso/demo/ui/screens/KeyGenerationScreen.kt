package com.soneso.demo.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.soneso.demo.platform.getClipboard
import com.soneso.demo.stellar.KeyPairGenerationResult
import com.soneso.demo.stellar.generateRandomKeyPair
import com.soneso.demo.ui.components.StellarTopBar
import com.soneso.demo.ui.components.AnimatedButton
import com.soneso.demo.ui.components.InfoCard
import com.soneso.stellar.sdk.KeyPair
import kotlinx.coroutines.launch

class KeyGenerationScreen : Screen {
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val coroutineScope = rememberCoroutineScope()

        // State for the generated keypair
        var keypair by remember { mutableStateOf<KeyPair?>(null) }
        var isGenerating by remember { mutableStateOf(false) }
        var showSecret by remember { mutableStateOf(false) }
        var snackbarMessage by remember { mutableStateOf<String?>(null) }

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
                    title = "Key Generation",
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
                        title = "Stellar Keypair Generation",
                        description = "Generate a cryptographically secure Ed25519 keypair for Stellar network operations. " +
                                "The keypair consists of a public key (account ID starting with 'G') and a secret seed (starting with 'S')."
                    )

                    // Generate button with AnimatedButton component
                    AnimatedButton(
                        onClick = {
                            coroutineScope.launch {
                                isGenerating = true
                                try {
                                    when (val result = generateRandomKeyPair()) {
                                        is KeyPairGenerationResult.Success -> {
                                            keypair = result.keyPair
                                            showSecret = false // Hide secret by default when generating new key
                                            snackbarMessage = "New keypair generated successfully"
                                        }
                                        is KeyPairGenerationResult.Error -> {
                                            snackbarMessage = result.message
                                        }
                                    }
                                } finally {
                                    isGenerating = false
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        enabled = !isGenerating,
                        isLoading = isGenerating
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = if (keypair == null) "Generate Keypair" else "Generate New Keypair",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                    }

                    // Display generated keypair
                    keypair?.let { kp ->
                        // Public Key Card
                        KeyDisplayCard(
                            title = "Public Key (Account ID)",
                            value = kp.getAccountId(),
                            description = "This is your public address. Share this to receive payments.",
                            onCopy = {
                                coroutineScope.launch {
                                    val success = getClipboard().copyToClipboard(kp.getAccountId())
                                    snackbarMessage = if (success) {
                                        "Public key copied to clipboard"
                                    } else {
                                        "Failed to copy to clipboard"
                                    }
                                }
                            }
                        )

                        // Secret Seed Card
                        SecretKeyDisplayCard(
                            title = "Secret Seed",
                            keypair = kp,
                            description = "NEVER share this! Anyone with this seed can access your account.",
                            isVisible = showSecret,
                            onToggleVisibility = { showSecret = !showSecret },
                            onCopy = {
                                coroutineScope.launch {
                                    val secretSeed = kp.getSecretSeed()?.concatToString() ?: ""
                                    val success = getClipboard().copyToClipboard(secretSeed)
                                    snackbarMessage = if (success) {
                                        "Secret seed copied to clipboard"
                                    } else {
                                        "Failed to copy to clipboard"
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun KeyDisplayCard(
    title: String,
    value: String,
    description: String,
    onCopy: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, Color(0xFF0A4FD6).copy(alpha = 0.2f)),
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
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = Color(0xFF0639A3) // StellarBlueDark
                )

                IconButton(
                    onClick = onCopy,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Copy to clipboard",
                        tint = Color(0xFF0A4FD6), // StellarBlue
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            SelectionContainer {
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Medium
                    ),
                    fontFamily = FontFamily.Monospace,
                    color = Color(0xFF0639A3).copy(alpha = 0.9f),
                    modifier = Modifier.fillMaxWidth(),
                    lineHeight = 24.sp
                )
            }

            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF0639A3).copy(alpha = 0.7f),
                lineHeight = 20.sp
            )
        }
    }
}

@Composable
private fun SecretKeyDisplayCard(
    title: String,
    keypair: KeyPair,
    description: String,
    isVisible: Boolean,
    onToggleVisibility: () -> Unit,
    onCopy: () -> Unit
) {
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
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = Color(0xFFA85A00) // StarlightGoldDark
                )

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(
                        onClick = onToggleVisibility,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = if (isVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (isVisible) "Hide secret" else "Show secret",
                            tint = Color(0xFFD97706), // StarlightGold
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    IconButton(
                        onClick = onCopy,
                        modifier = Modifier.size(40.dp)
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
                    text = if (isVisible) {
                        keypair.getSecretSeed()?.concatToString() ?: ""
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
                text = description,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontWeight = FontWeight.Medium
                ),
                color = Color(0xFFA85A00).copy(alpha = 0.7f),
                lineHeight = 20.sp
            )
        }
    }
}
