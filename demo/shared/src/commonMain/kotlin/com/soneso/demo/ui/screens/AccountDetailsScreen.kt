package com.soneso.demo.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.soneso.demo.stellar.AccountDetailsResult
import com.soneso.demo.stellar.fetchAccountDetails
import com.soneso.demo.ui.theme.LightExtendedColors
import com.soneso.stellar.sdk.horizon.responses.AccountResponse
import kotlinx.coroutines.launch

class AccountDetailsScreen : Screen {
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val coroutineScope = rememberCoroutineScope()

        // State management
        var accountId by remember { mutableStateOf("") }
        var isLoading by remember { mutableStateOf(false) }
        var detailsResult by remember { mutableStateOf<AccountDetailsResult?>(null) }
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

        // Function to fetch account details
        fun fetchDetails() {
            val error = validateAccountId(accountId)
            if (error != null) {
                validationError = error
            } else {
                coroutineScope.launch {
                    isLoading = true
                    detailsResult = null
                    try {
                        detailsResult = fetchAccountDetails(accountId, useTestnet = true)
                    } finally {
                        isLoading = false
                    }
                }
            }
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Fetch Account Details") },
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
                            text = "Horizon API: fetch account details",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Text(
                            text = "Enter a Stellar account ID to retrieve comprehensive account information including balances, signers, thresholds, and more.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }

                // Account ID Input Field
                OutlinedTextField(
                    value = accountId,
                    onValueChange = {
                        accountId = it.trim()
                        validationError = null
                        detailsResult = null
                    },
                    label = { Text("Account ID") },
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
                        onDone = { fetchDetails() }
                    )
                )

                // Submit button
                Button(
                    onClick = { fetchDetails() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    enabled = !isLoading && accountId.isNotBlank()
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Fetching...")
                    } else {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Fetch Details")
                    }
                }

                // Result display
                detailsResult?.let { result ->
                    when (result) {
                        is AccountDetailsResult.Success -> {
                            AccountDetailsCard(result.accountResponse)
                        }
                        is AccountDetailsResult.Error -> {
                            ErrorCard(result)
                        }
                    }
                }

                // Placeholder when no action taken
                if (detailsResult == null && !isLoading && accountId.isBlank()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                    )
                    Text(
                        text = "Enter an account ID to view its details on the testnet",
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
private fun AccountDetailsCard(account: AccountResponse) {
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
            Text(
                text = "Account Found",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = LightExtendedColors.onSuccessContainer
            )
            Text(
                text = "Successfully fetched details for account ${shortenAccountId(account.accountId)}",
                style = MaterialTheme.typography.bodyMedium,
                color = LightExtendedColors.onSuccessContainer
            )
        }
    }

    // Basic Information
    DetailsSectionCard("Basic Information") {
        DetailRow("Account ID", account.accountId, monospace = true)
        DetailRow("Sequence Number", account.sequenceNumber.toString())
        DetailRow("Subentry Count", account.subentryCount.toString())
        account.homeDomain?.let { DetailRow("Home Domain", it) }
        DetailRow("Last Modified Ledger", account.lastModifiedLedger.toString())
        DetailRow("Last Modified Time", account.lastModifiedTime)
    }

    // Balances
    DetailsSectionCard("Balances (${account.balances.size})") {
        account.balances.forEachIndexed { index, balance ->
            if (index > 0) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            }
            BalanceItem(balance)
        }
    }

    // Thresholds
    DetailsSectionCard("Thresholds") {
        DetailRow("Low Threshold", account.thresholds.lowThreshold.toString())
        DetailRow("Medium Threshold", account.thresholds.medThreshold.toString())
        DetailRow("High Threshold", account.thresholds.highThreshold.toString())
    }

    // Flags
    DetailsSectionCard("Authorization Flags") {
        FlagRow("Auth Required", account.flags.authRequired)
        FlagRow("Auth Revocable", account.flags.authRevocable)
        FlagRow("Auth Immutable", account.flags.authImmutable)
        FlagRow("Auth Clawback Enabled", account.flags.authClawbackEnabled)
    }

    // Signers
    DetailsSectionCard("Signers (${account.signers.size})") {
        account.signers.forEachIndexed { index, signer ->
            if (index > 0) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            }
            SignerItem(signer)
        }
    }

    // Data Entries
    if (account.data.isNotEmpty()) {
        DetailsSectionCard("Data Entries (${account.data.size})") {
            account.data.entries.forEachIndexed { index, entry ->
                if (index > 0) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                }
                DetailRow(entry.key, entry.value, monospace = true)
            }
        }
    }

    // Sponsorship Information
    if (account.sponsor != null || (account.numSponsoring ?: 0) > 0 || (account.numSponsored ?: 0) > 0) {
        DetailsSectionCard("Sponsorship") {
            account.sponsor?.let { DetailRow("Sponsor", it, monospace = true) }
            account.numSponsoring?.let { DetailRow("Number Sponsoring", it.toString()) }
            account.numSponsored?.let { DetailRow("Number Sponsored", it.toString()) }
        }
    }
}

@Composable
private fun DetailsSectionCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            HorizontalDivider()
            content()
        }
    }
}

@Composable
private fun DetailRow(
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
private fun FlagRow(label: String, enabled: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Card(
            colors = CardDefaults.cardColors(
                containerColor = if (enabled) {
                    LightExtendedColors.successContainer
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                }
            )
        ) {
            Text(
                text = if (enabled) "Enabled" else "Disabled",
                style = MaterialTheme.typography.labelSmall,
                color = if (enabled) {
                    LightExtendedColors.onSuccessContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                },
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
    }
}

@Composable
private fun BalanceItem(balance: AccountResponse.Balance) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Asset type
        Text(
            text = when {
                balance.assetType == "native" -> "Native (XLM)"
                balance.assetCode != null -> "${balance.assetCode} (${balance.assetType})"
                balance.liquidityPoolId != null -> "Liquidity Pool"
                else -> balance.assetType
            },
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // Balance
        DetailRow("Balance", balance.balance)

        // Asset issuer (if not native)
        balance.assetIssuer?.let {
            DetailRow("Issuer", it, monospace = true)
        }

        // Liquidity pool ID (if applicable)
        balance.liquidityPoolId?.let {
            DetailRow("Pool ID", it, monospace = true)
        }

        // Additional details
        balance.limit?.let { DetailRow("Limit", it) }
        balance.buyingLiabilities?.let { DetailRow("Buying Liabilities", it) }
        balance.sellingLiabilities?.let { DetailRow("Selling Liabilities", it) }

        // Authorization flags
        balance.isAuthorized?.let {
            FlagRow("Authorized", it)
        }
        balance.isAuthorizedToMaintainLiabilities?.let {
            FlagRow("Authorized to Maintain Liabilities", it)
        }
        balance.isClawbackEnabled?.let {
            FlagRow("Clawback Enabled", it)
        }
    }
}

@Composable
private fun SignerItem(signer: AccountResponse.Signer) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        DetailRow("Key", signer.key, monospace = true)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Type: ${signer.type}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Weight: ${signer.weight}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        signer.sponsor?.let {
            DetailRow("Sponsor", it, monospace = true)
        }
    }
}

@Composable
private fun ErrorCard(error: AccountDetailsResult.Error) {
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
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Column(
                modifier = Modifier.padding(start = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "• Verify the account ID is valid (starts with 'G' and is 56 characters)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Text(
                    text = "• Make sure the account exists on testnet (fund it via Friendbot if needed)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Text(
                    text = "• Check your internet connection",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Text(
                    text = "• Try again in a moment if you're being rate-limited",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
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
