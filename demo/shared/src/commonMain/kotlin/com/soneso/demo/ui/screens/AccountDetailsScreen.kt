package com.soneso.demo.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.soneso.demo.stellar.AccountDetailsResult
import com.soneso.demo.stellar.fetchAccountDetails
import com.soneso.demo.ui.FormValidation
import com.soneso.demo.ui.components.AnimatedButton
import com.soneso.demo.ui.components.InfoCard
import com.soneso.demo.ui.components.StellarTopBar
import com.soneso.demo.ui.theme.LightExtendedColors
import com.soneso.stellar.sdk.horizon.responses.AccountResponse
import kotlinx.coroutines.launch
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

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

        // Function to fetch account details
        fun fetchDetails() {
            val error = FormValidation.validateAccountIdField(accountId)
            if (error != null) {
                validationError = error
            } else {
                coroutineScope.launch {
                    isLoading = true
                    detailsResult = null
                    try {
                        detailsResult = fetchAccountDetails(accountId)
                    } finally {
                        isLoading = false
                    }
                }
            }
        }

        Scaffold(
            topBar = {
                StellarTopBar(
                    title = "Account Details",
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
                    title = "Horizon API: Fetch Account Details",
                    description = "Enter a Stellar account ID to retrieve comprehensive account information including balances, signers, thresholds, flags, and sponsorship details from the testnet."
                )

                // Account ID Input Field with modern styling
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
                                color = MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    },
                    keyboardOptions = KeyboardOptions(
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = { fetchDetails() }
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF0A4FD6),
                        focusedLabelColor = Color(0xFF0A4FD6),
                        cursorColor = Color(0xFF0A4FD6)
                    ),
                    shape = RoundedCornerShape(12.dp)
                )

                // Submit button with AnimatedButton component
                AnimatedButton(
                    onClick = { fetchDetails() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    enabled = !isLoading && accountId.isNotBlank(),
                    isLoading = isLoading,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF0A4FD6), // StellarBlue
                        contentColor = Color.White
                    )
                ) {
                    if (!isLoading) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                    }
                    Text(
                        text = if (isLoading) "Fetching..." else "Fetch Details",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.SemiBold
                        )
                    )
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
            }
        }
    }
}

/**
 * Helper function to check if a decoded string is printable/valid UTF-8.
 * Allows letters, digits, whitespace, and common punctuation.
 */
private fun isPrintableString(str: String): Boolean {
    return str.all { char ->
        char.isLetterOrDigit() ||
        char.isWhitespace() ||
        char in "!\"#\$%&'()*+,-./:;<=>?@[\\]^_`{|}~"
    }
}

/**
 * Decodes a base64-encoded data entry value and determines how to display it.
 *
 * Returns a Pair of:
 * - First: The display value (decoded string or original base64)
 * - Second: Description of the format ("decoded string" or "base64-encoded binary")
 */
@OptIn(ExperimentalEncodingApi::class)
private fun decodeDataEntryValue(base64Value: String): Pair<String, String> {
    return try {
        // Decode the base64 value
        val decodedBytes = Base64.decode(base64Value)
        val decodedString = decodedBytes.decodeToString()

        // Check if the decoded value is printable UTF-8
        if (isPrintableString(decodedString)) {
            Pair(decodedString, "decoded string")
        } else {
            // Binary data - show base64
            Pair(base64Value, "base64-encoded binary")
        }
    } catch (e: Exception) {
        // If decoding fails, show base64
        Pair(base64Value, "base64-encoded (decode failed)")
    }
}

@Composable
private fun AccountDetailsCard(account: AccountResponse) {
    // Basic Information
    DetailsSectionCard("Basic Information") {
        DetailRow("Account ID", account.accountId, monospace = true)
        DetailRow("Sequence Number", account.sequenceNumber.toString())
        DetailRow("Subentry Count", account.subentryCount.toString())
        account.homeDomain?.let { DetailRow("Home Domain", it) }
        DetailRow("Last Modified Ledger", account.lastModifiedLedger.toString())
        DetailRow("Last Modified Time", account.lastModifiedTime)
    }

    // Balances - Individual collapsible entries with Native XLM first
    // Sort balances: Native (XLM) first, then others
    val sortedBalances = remember(account.balances) {
        account.balances.sortedWith(
            compareByDescending<AccountResponse.Balance> { it.assetType == "native" }
                .thenBy { it.assetCode ?: "" }
        )
    }

    DetailsSectionCard("Balances (${account.balances.size})") {
        sortedBalances.forEachIndexed { index, balance ->
            if (index > 0) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            }
            CollapsibleBalanceItem(balance)
        }
    }

    // Thresholds
    DetailsSectionCard("Thresholds") {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            ThresholdItem("Low", account.thresholds.lowThreshold.toString())
            ThresholdItem("Medium", account.thresholds.medThreshold.toString())
            ThresholdItem("High", account.thresholds.highThreshold.toString())
        }
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
                DataEntryItem(entry.key, entry.value)
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
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold
                ),
                color = Color(0xFF0639A3) // StellarBlueDark
            )
            HorizontalDivider(color = Color(0xFF0639A3).copy(alpha = 0.2f))
            content()
        }
    }
}

@Composable
private fun CollapsibleDetailsSectionCard(
    title: String,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    val headerInteractionSource = remember { MutableInteractionSource() }
    val isHeaderHovered by headerInteractionSource.collectIsHoveredAsState()

    val chevronRotation by animateFloatAsState(
        targetValue = if (isExpanded) 180f else 0f,
        animationSpec = tween(durationMillis = 300),
        label = "chevron_rotation"
    )

    val headerElevation by animateDpAsState(
        targetValue = if (isHeaderHovered) 3.dp else 2.dp,
        animationSpec = tween(durationMillis = 150),
        label = "header_elevation"
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, Color(0xFF0A4FD6).copy(alpha = 0.2f)),
        elevation = CardDefaults.cardElevation(defaultElevation = headerElevation),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFE8F1FF) // NebulaBlue
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Clickable header with expand/collapse icon
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = headerInteractionSource,
                        indication = null,
                        onClick = onToggle
                    )
                    .hoverable(interactionSource = headerInteractionSource),
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
                Icon(
                    imageVector = Icons.Default.ExpandMore,
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                    tint = Color(0xFF0639A3).copy(alpha = 0.7f),
                    modifier = Modifier
                        .size(24.dp)
                        .rotate(chevronRotation)
                )
            }

            // Animated content visibility
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically(
                    animationSpec = tween(durationMillis = 300)
                ) + fadeIn(animationSpec = tween(durationMillis = 300)),
                exit = shrinkVertically(
                    animationSpec = tween(durationMillis = 300)
                ) + fadeOut(animationSpec = tween(durationMillis = 300))
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    HorizontalDivider(color = Color(0xFF0639A3).copy(alpha = 0.2f))
                    content()
                }
            }
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
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge.copy(
                fontWeight = FontWeight.SemiBold
            ),
            color = Color(0xFF0639A3).copy(alpha = 0.7f)
        )
        SelectionContainer {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = if (monospace) FontFamily.Monospace else FontFamily.Default,
                color = Color(0xFF0639A3).copy(alpha = 0.95f),
                lineHeight = 22.sp
            )
        }
    }
}

@Composable
private fun ThresholdItem(label: String, value: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = FontWeight.SemiBold
            ),
            color = Color(0xFF0639A3).copy(alpha = 0.7f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Bold
            ),
            color = Color(0xFF0639A3)
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
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.Medium
            ),
            color = Color(0xFF0639A3).copy(alpha = 0.9f)
        )
        Card(
            shape = RoundedCornerShape(8.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (enabled) {
                    Color(0xFF0D9488) // NebulaTeal
                } else {
                    Color(0xFF9CA3AF).copy(alpha = 0.3f) // MeteorGray
                }
            )
        ) {
            Text(
                text = if (enabled) "Enabled" else "Disabled",
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.SemiBold
                ),
                color = if (enabled) {
                    Color.White
                } else {
                    Color(0xFF4A5568) // SpaceLight
                },
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }
    }
}

@Composable
private fun CollapsibleBalanceItem(balance: AccountResponse.Balance) {
    var isExpanded by remember { mutableStateOf(false) }

    val headerInteractionSource = remember { MutableInteractionSource() }
    val isHeaderHovered by headerInteractionSource.collectIsHoveredAsState()

    val chevronRotation by animateFloatAsState(
        targetValue = if (isExpanded) 180f else 0f,
        animationSpec = tween(durationMillis = 300),
        label = "balance_chevron_rotation"
    )

    val headerScale by animateFloatAsState(
        targetValue = if (isHeaderHovered) 1.01f else 1f,
        animationSpec = tween(durationMillis = 150),
        label = "balance_header_scale"
    )

    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Clickable header showing asset type and balance
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .scale(headerScale)
                .clickable(
                    interactionSource = headerInteractionSource,
                    indication = null,
                    onClick = { isExpanded = !isExpanded }
                )
                .hoverable(interactionSource = headerInteractionSource),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Asset type
                Text(
                    text = when {
                        balance.assetType == "native" -> "XLM"
                        balance.assetCode != null -> balance.assetCode!!
                        balance.liquidityPoolId != null -> "Liquidity Pool"
                        else -> balance.assetType
                    },
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = Color(0xFF0639A3) // StellarBlueDark
                )

                // Balance amount
                Text(
                    text = balance.balance,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = FontWeight.Medium
                    ),
                    color = Color(0xFF0639A3).copy(alpha = 0.85f)
                )
            }

            // Chevron icon
            Icon(
                imageVector = Icons.Default.ExpandMore,
                contentDescription = if (isExpanded) "Collapse" else "Expand",
                tint = Color(0xFF0639A3).copy(alpha = 0.7f),
                modifier = Modifier
                    .size(24.dp)
                    .rotate(chevronRotation)
            )
        }

        // Animated expandable content
        AnimatedVisibility(
            visible = isExpanded,
            enter = expandVertically(
                animationSpec = tween(durationMillis = 300)
            ) + fadeIn(animationSpec = tween(durationMillis = 300)),
            exit = shrinkVertically(
                animationSpec = tween(durationMillis = 300)
            ) + fadeOut(animationSpec = tween(durationMillis = 300))
        ) {
            Column(
                modifier = Modifier.padding(top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
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
private fun DataEntryItem(key: String, base64Value: String) {
    // Decode the base64 value and determine display format
    val (displayValue, formatDescription) = decodeDataEntryValue(base64Value)

    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Entry key
        Text(
            text = key,
            style = MaterialTheme.typography.labelLarge.copy(
                fontWeight = FontWeight.SemiBold
            ),
            color = Color(0xFF0639A3).copy(alpha = 0.7f)
        )

        // Format description
        Text(
            text = "Format: $formatDescription",
            style = MaterialTheme.typography.labelSmall,
            color = Color(0xFF0639A3).copy(alpha = 0.6f)
        )

        // Display value
        SelectionContainer {
            Text(
                text = displayValue,
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = if (formatDescription.startsWith("base64")) FontFamily.Monospace else FontFamily.Default,
                color = Color(0xFF0639A3).copy(alpha = 0.95f),
                lineHeight = 22.sp
            )
        }
    }
}

@Composable
private fun ErrorCard(error: AccountDetailsResult.Error) {
    // Error card with celestial styling
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
                text = "Error",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold
                ),
                color = Color(0xFF991B1B) // NovaRedDark
            )
            Text(
                text = error.message,
                style = MaterialTheme.typography.bodyLarge,
                color = Color(0xFF991B1B).copy(alpha = 0.9f),
                lineHeight = 24.sp
            )
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

    // Troubleshooting tips card
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
                TroubleshootingItem("Verify the account ID is valid (starts with 'G' and is 56 characters)")
                TroubleshootingItem("Make sure the account exists on testnet (fund it via Friendbot if needed)")
                TroubleshootingItem("Check your internet connection")
                TroubleshootingItem("Try again in a moment if you're being rate-limited")
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
