package com.soneso.demo.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.soneso.demo.stellar.AccountFundingResult
import com.soneso.demo.stellar.KeyPairGenerationResult
import com.soneso.demo.stellar.fundTestnetAccount
import com.soneso.demo.stellar.generateRandomKeyPair
import com.soneso.demo.ui.theme.LightExtendedColors
import kotlinx.coroutines.launch

class FundAccountScreen : Screen {
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val coroutineScope = rememberCoroutineScope()

        // State management
        var accountId by remember { mutableStateOf("") }
        var isLoading by remember { mutableStateOf(false) }
        var isGeneratingKey by remember { mutableStateOf(false) }
        var fundingResult by remember { mutableStateOf<AccountFundingResult?>(null) }
        var snackbarMessage by remember { mutableStateOf<String?>(null) }
        var validationError by remember { mutableStateOf<String?>(null) }

        val snackbarHostState = remember { SnackbarHostState() }

        // Validate account ID
        fun validateAccountId(id: String): String? {
            return when {
                id.isBlank() -> "Account ID is required"
                !id.startsWith('G') -> "Account ID must start with 'G'"
                id.length != 56 -> "Account ID must be 56 characters long"
                else -> null
            }
        }

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
                TopAppBar(
                    title = { Text("Fund Testnet Account") },
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
                            text = "Friendbot: fund a testnet network account",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Text(
                            text = "The friendbot is a horizon API endpoint that will fund an account with 10,000 lumens on the testnet network.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }

                // Public Key Input Field
                OutlinedTextField(
                    value = accountId,
                    onValueChange = {
                        accountId = it.trim()
                        validationError = null
                        fundingResult = null
                    },
                    label = { Text("Public Key") },
                    placeholder = { Text("G...") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = validationError != null,
                    supportingText = validationError?.let { error ->
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
                        onDone = {
                            // Trigger funding on keyboard done
                            val error = validateAccountId(accountId)
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
                    )
                )

                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Generate and fill button
                    OutlinedButton(
                        onClick = {
                            coroutineScope.launch {
                                isGeneratingKey = true
                                try {
                                    when (val result = generateRandomKeyPair()) {
                                        is KeyPairGenerationResult.Success -> {
                                            accountId = result.keyPair.getAccountId()
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
                            .height(56.dp),
                        enabled = !isLoading && !isGeneratingKey
                    ) {
                        if (isGeneratingKey) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Generating...",
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.bodySmall
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Generate & Fill",
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }

                    // Get lumens button
                    Button(
                        onClick = {
                            val error = validateAccountId(accountId)
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
                        enabled = !isLoading && !isGeneratingKey && accountId.isNotBlank()
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Funding...")
                        } else {
                            Icon(
                                imageVector = Icons.Default.AccountBalance,
                                contentDescription = null
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Get lumens")
                        }
                    }
                }

                // Result display
                fundingResult?.let { result ->
                    when (result) {
                        is AccountFundingResult.Success -> {
                            // Success card with custom success colors
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
                                    Text(
                                        text = "Success",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = LightExtendedColors.onSuccessContainer
                                    )
                                    Text(
                                        text = "Successfully funded ${shortenAccountId(result.accountId)} on testnet",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = LightExtendedColors.onSuccessContainer
                                    )
                                    Text(
                                        text = result.message,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = LightExtendedColors.onSuccessContainer
                                    )
                                }
                            }
                        }
                        is AccountFundingResult.Error -> {
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
                                        text = "Error",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                    Text(
                                        text = result.message,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                    result.exception?.let { exception ->
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
                                            text = "• Check that the account ID is valid (starts with 'G' and is 56 characters)",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer
                                        )
                                        Text(
                                            text = "• If the account was already funded, it cannot be funded again",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer
                                        )
                                        Text(
                                            text = "• Verify you have an internet connection",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer
                                        )
                                        Text(
                                            text = "• Try generating a new keypair if the issue persists",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Placeholder when no action taken
                if (fundingResult == null && !isLoading && accountId.isBlank()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Enter a public key or generate a new keypair to fund the account with testnet XLM",
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
