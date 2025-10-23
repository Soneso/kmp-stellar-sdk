package com.soneso.demo.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
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
import com.soneso.demo.platform.getClipboard
import com.soneso.demo.stellar.FetchTransactionResult
import com.soneso.demo.stellar.fetchTransactionFromHorizon
import com.soneso.demo.stellar.fetchTransactionFromRpc
import com.soneso.demo.ui.theme.LightExtendedColors
import com.soneso.stellar.sdk.horizon.responses.TransactionResponse
import com.soneso.stellar.sdk.horizon.responses.operations.*
import com.soneso.stellar.sdk.rpc.responses.GetTransactionResponse
import com.soneso.stellar.sdk.rpc.responses.GetTransactionStatus
import com.soneso.stellar.sdk.xdr.*
import kotlinx.coroutines.launch
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

class FetchTransactionScreen : Screen {
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val coroutineScope = rememberCoroutineScope()

        // State management
        var transactionHash by remember { mutableStateOf("639cce30b6c166224010e31abf4ae60468d3480bfc5140eb2abf0eea3e4edf99") }
        var useHorizon by remember { mutableStateOf(true) }
        var isLoading by remember { mutableStateOf(false) }
        var fetchResult by remember { mutableStateOf<FetchTransactionResult?>(null) }
        var validationError by remember { mutableStateOf<String?>(null) }

        val snackbarHostState = remember { SnackbarHostState() }

        // Function to copy to clipboard with snackbar feedback
        fun copyToClipboard(text: String, label: String) {
            coroutineScope.launch {
                val success = getClipboard().copyToClipboard(text)
                snackbarHostState.showSnackbar(
                    message = if (success) "$label copied to clipboard" else "Failed to copy to clipboard",
                    duration = SnackbarDuration.Short
                )
            }
        }

        // Validate transaction hash
        fun validateTransactionHash(hash: String): String? {
            return when {
                hash.isBlank() -> "Transaction hash is required"
                hash.length != 64 -> "Transaction hash must be 64 characters long"
                !hash.matches(Regex("^[0-9a-fA-F]{64}$")) -> "Transaction hash must be a valid hexadecimal string"
                else -> null
            }
        }

        // Function to fetch transaction
        fun fetchTransaction() {
            val error = validateTransactionHash(transactionHash)
            if (error != null) {
                validationError = error
            } else {
                coroutineScope.launch {
                    isLoading = true
                    fetchResult = null
                    try {
                        fetchResult = if (useHorizon) {
                            fetchTransactionFromHorizon(transactionHash)
                        } else {
                            fetchTransactionFromRpc(transactionHash)
                        }
                    } finally {
                        isLoading = false
                    }
                }
            }
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Fetch Transaction Details") },
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
                            text = "Fetch transaction details from Horizon or Soroban RPC",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Text(
                            text = "Enter a transaction hash to retrieve comprehensive transaction information. " +
                                    "Choose between Horizon API (for general transactions) or Soroban RPC (for smart contract transactions).",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }

                // Compact API Selection
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
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
                                text = if (useHorizon) "Using Horizon API" else "Using Soroban RPC",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "Switch between Horizon API and RPC API",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }
                        Switch(
                            checked = useHorizon,
                            onCheckedChange = {
                                useHorizon = it
                                fetchResult = null
                            }
                        )
                    }
                }

                // Transaction Hash Input Field with hint
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "This is a demo transaction hash. You can replace it with your own transaction hash to fetch different transaction details.",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = transactionHash,
                        onValueChange = {
                            transactionHash = it.trim().lowercase()
                            validationError = null
                            fetchResult = null
                        },
                        label = { Text("Transaction Hash") },
                        placeholder = { Text("64-character hex string") },
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
                            onDone = { fetchTransaction() }
                        )
                    )
                }

                // Submit button
                Button(
                    onClick = { fetchTransaction() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    enabled = !isLoading && transactionHash.isNotBlank()
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
                        Text("Fetch Transaction")
                    }
                }

                // Result display
                fetchResult?.let { result ->
                    when (result) {
                        is FetchTransactionResult.HorizonSuccess -> {
                            HorizonTransactionCards(result.transaction, result.operations, ::copyToClipboard)
                        }
                        is FetchTransactionResult.RpcSuccess -> {
                            RpcTransactionCards(result.transaction, ::copyToClipboard)
                        }
                        is FetchTransactionResult.Error -> {
                            ErrorCard(result)
                        }
                    }
                }

                // Placeholder when no action taken
                if (fetchResult == null && !isLoading && transactionHash.isBlank()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Icon(
                        imageVector = Icons.Default.Receipt,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                    )
                    Text(
                        text = "Enter a transaction hash to view its details",
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
private fun HorizonTransactionCards(
    transaction: TransactionResponse,
    operations: List<OperationResponse>,
    onCopy: (String, String) -> Unit
) {
    // Success header card
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (transaction.successful) {
                LightExtendedColors.successContainer
            } else {
                MaterialTheme.colorScheme.errorContainer
            }
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = if (transaction.successful) "Transaction Successful" else "Transaction Failed",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (transaction.successful) {
                    LightExtendedColors.onSuccessContainer
                } else {
                    MaterialTheme.colorScheme.onErrorContainer
                }
            )
            Text(
                text = "Fetched from Horizon API",
                style = MaterialTheme.typography.bodyMedium,
                color = if (transaction.successful) {
                    LightExtendedColors.onSuccessContainer
                } else {
                    MaterialTheme.colorScheme.onErrorContainer
                }
            )
        }
    }

    // Basic Information Card
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
                text = "Basic Information",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            HorizontalDivider()

            CopyableDetailRow("Hash", transaction.hash, onCopy)
            DetailRow("ID", transaction.id)
            DetailRow("Successful", if (transaction.successful) "Yes" else "No")
            DetailRow("Ledger", transaction.ledger.toString())
            DetailRow("Created At", transaction.createdAt)
            DetailRow("Paging Token", transaction.pagingToken)
        }
    }

    // Account and Fees Card
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
                text = "Account and Fees",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            HorizontalDivider()

            CopyableDetailRow("Source Account", transaction.sourceAccount, onCopy)
            transaction.accountMuxed?.let { muxed ->
                CopyableDetailRow("Account Muxed", muxed, onCopy)
            }
            transaction.accountMuxedId?.let { muxedId ->
                DetailRow("Account Muxed ID", muxedId)
            }
            DetailRow("Source Sequence", transaction.sourceAccountSequence.toString())
            CopyableDetailRow("Fee Account", transaction.feeAccount, onCopy)
            DetailRow("Fee Charged", "${transaction.feeCharged} stroops (${transaction.feeCharged / 10_000_000.0} XLM)")
            DetailRow("Max Fee", "${transaction.maxFee} stroops (${transaction.maxFee / 10_000_000.0} XLM)")
        }
    }

    // Operations Card (expandable)
    if (operations.isNotEmpty()) {
        OperationsCard(operations, onCopy)
    }

    // Memo Card
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
                text = "Memo",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            HorizontalDivider()

            DetailRow("Memo Type", transaction.memoType)
            transaction.memoValue?.let { memo ->
                CopyableDetailRow("Memo Value", memo, onCopy)
            }
            transaction.memoBytes?.let { bytes ->
                CopyableDetailRow("Memo Bytes", bytes, onCopy)
            }
        }
    }

    // Signatures Card
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
                text = "Signatures (${transaction.signatures.size})",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            HorizontalDivider()

            if (transaction.signatures.isEmpty()) {
                Text(
                    text = "No signatures",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            } else {
                transaction.signatures.forEachIndexed { index, signature ->
                    CopyableDetailRow("Signature ${index + 1}", signature, onCopy)
                }
            }
        }
    }

    // XDR Data Card
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
                text = "XDR Data",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            HorizontalDivider()

            transaction.envelopeXdr?.let { xdr ->
                CopyableDetailRow("Envelope XDR", xdr, onCopy)
            }
            transaction.resultXdr?.let { xdr ->
                CopyableDetailRow("Result XDR", xdr, onCopy)
            }
            transaction.resultMetaXdr?.let { xdr ->
                CopyableDetailRow("Result Meta XDR", xdr, onCopy)
            }
            transaction.feeMetaXdr?.let { xdr ->
                CopyableDetailRow("Fee Meta XDR", xdr, onCopy)
            }
        }
    }
}

@Composable
private fun OperationsCard(
    operations: List<OperationResponse>,
    onCopy: (String, String) -> Unit
) {
    // Track which operations are expanded
    var expandedOperations by remember { mutableStateOf(setOf<Int>()) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Operations (${operations.size})",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Click on an operation to view details",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
            HorizontalDivider()

            operations.forEachIndexed { index, operation ->
                val isExpanded = expandedOperations.contains(index)

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            expandedOperations = if (isExpanded) {
                                expandedOperations - index
                            } else {
                                expandedOperations + index
                            }
                        },
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Operation header (always visible)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Operation ${index + 1}: ${operation.type.replace("_", " ").uppercase()}",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Icon(
                                imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = if (isExpanded) "Collapse" else "Expand"
                            )
                        }

                        // Operation ID row (always visible, full ID with copy button)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "Operation ID",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                )
                                SelectionContainer {
                                    Text(
                                        text = operation.id,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontFamily = FontFamily.Monospace,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            IconButton(
                                onClick = { onCopy(operation.id, "Operation ID") },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = "Copy operation ID",
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        // Expandable operation details
                        AnimatedVisibility(
                            visible = isExpanded,
                            enter = fadeIn() + expandVertically(),
                            exit = fadeOut() + shrinkVertically()
                        ) {
                            Column(
                                modifier = Modifier.padding(top = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                HorizontalDivider()
                                OperationDetails(operation, onCopy)
                            }
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
 * Decodes a base64-encoded ManageData value and determines how to display it.
 *
 * Returns a Pair of:
 * - First: The display value (decoded string or original base64)
 * - Second: Description of the format ("decoded string" or "base64-encoded binary")
 */
@OptIn(ExperimentalEncodingApi::class)
private fun decodeManageDataValue(base64Value: String): Pair<String, String> {
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
private fun OperationDetails(
    operation: OperationResponse,
    onCopy: (String, String) -> Unit
) {
    // Common fields for all operations
    CopyableDetailRow("Source Account", operation.sourceAccount, onCopy)
    operation.sourceAccountMuxed?.let {
        CopyableDetailRow("Source Muxed", it, onCopy)
    }
    CopyableDetailRow("Transaction Hash", operation.transactionHash, onCopy)
    DetailRow("Created At", operation.createdAt)
    DetailRow("Transaction Successful", if (operation.transactionSuccessful) "Yes" else "No")

    HorizontalDivider()
    Text(
        text = "Operation-Specific Data",
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(vertical = 4.dp)
    )

    // Operation-specific fields based on type
    when (operation) {
        is PaymentOperationResponse -> {
            CopyableDetailRow("From", operation.from, onCopy)
            CopyableDetailRow("To", operation.to, onCopy)
            DetailRow("Amount", operation.amount)
            DetailRow("Asset Type", operation.assetType)
            operation.assetCode?.let { DetailRow("Asset Code", it) }
            operation.assetIssuer?.let { CopyableDetailRow("Asset Issuer", it, onCopy) }
        }
        is CreateAccountOperationResponse -> {
            CopyableDetailRow("Funder", operation.funder, onCopy)
            CopyableDetailRow("Account", operation.account, onCopy)
            DetailRow("Starting Balance", "${operation.startingBalance} XLM")
        }
        is ChangeTrustOperationResponse -> {
            CopyableDetailRow("Trustor", operation.trustor, onCopy)
            operation.trustee?.let { CopyableDetailRow("Trustee", it, onCopy) }
            DetailRow("Asset Type", operation.assetType)
            operation.assetCode?.let { DetailRow("Asset Code", it) }
            operation.assetIssuer?.let { CopyableDetailRow("Asset Issuer", it, onCopy) }
            DetailRow("Limit", operation.limit)
            operation.liquidityPoolId?.let { CopyableDetailRow("Liquidity Pool ID", it, onCopy) }
        }
        is ManageSellOfferOperationResponse -> {
            DetailRow("Offer ID", operation.offerId.toString())
            DetailRow("Amount", operation.amount)
            DetailRow("Price", operation.price)
            DetailRow("Selling Asset", operation.sellingAssetType)
            operation.sellingAssetCode?.let { DetailRow("Selling Code", it) }
            operation.sellingAssetIssuer?.let { CopyableDetailRow("Selling Issuer", it, onCopy) }
            DetailRow("Buying Asset", operation.buyingAssetType)
            operation.buyingAssetCode?.let { DetailRow("Buying Code", it) }
            operation.buyingAssetIssuer?.let { CopyableDetailRow("Buying Issuer", it, onCopy) }
        }
        is ManageBuyOfferOperationResponse -> {
            DetailRow("Offer ID", operation.offerId.toString())
            DetailRow("Amount", operation.amount)
            DetailRow("Price", operation.price)
            DetailRow("Selling Asset", operation.sellingAssetType)
            operation.sellingAssetCode?.let { DetailRow("Selling Code", it) }
            operation.sellingAssetIssuer?.let { CopyableDetailRow("Selling Issuer", it, onCopy) }
            DetailRow("Buying Asset", operation.buyingAssetType)
            operation.buyingAssetCode?.let { DetailRow("Buying Code", it) }
            operation.buyingAssetIssuer?.let { CopyableDetailRow("Buying Issuer", it, onCopy) }
        }
        is CreatePassiveSellOfferOperationResponse -> {
            DetailRow("Offer ID", operation.offerId.toString())
            DetailRow("Amount", operation.amount)
            DetailRow("Price", operation.price)
            DetailRow("Selling Asset", operation.sellingAssetType)
            operation.sellingAssetCode?.let { DetailRow("Selling Code", it) }
            operation.sellingAssetIssuer?.let { CopyableDetailRow("Selling Issuer", it, onCopy) }
            DetailRow("Buying Asset", operation.buyingAssetType)
            operation.buyingAssetCode?.let { DetailRow("Buying Code", it) }
            operation.buyingAssetIssuer?.let { CopyableDetailRow("Buying Issuer", it, onCopy) }
        }
        is SetOptionsOperationResponse -> {
            operation.homeDomain?.let { DetailRow("Home Domain", it) }
            operation.inflationDestination?.let { CopyableDetailRow("Inflation Dest", it, onCopy) }
            operation.masterKeyWeight?.let { DetailRow("Master Weight", it.toString()) }
            operation.lowThreshold?.let { DetailRow("Low Threshold", it.toString()) }
            operation.medThreshold?.let { DetailRow("Med Threshold", it.toString()) }
            operation.highThreshold?.let { DetailRow("High Threshold", it.toString()) }
            operation.signerKey?.let { CopyableDetailRow("Signer Key", it, onCopy) }
            operation.signerWeight?.let { DetailRow("Signer Weight", it.toString()) }
            operation.setFlagStrings?.let { flags ->
                if (flags.isNotEmpty()) {
                    DetailRow("Set Flags", flags.joinToString(", "))
                }
            }
            operation.clearFlagStrings?.let { flags ->
                if (flags.isNotEmpty()) {
                    DetailRow("Clear Flags", flags.joinToString(", "))
                }
            }
        }
        is AccountMergeOperationResponse -> {
            CopyableDetailRow("Account", operation.account, onCopy)
            CopyableDetailRow("Into", operation.into, onCopy)
        }
        is ManageDataOperationResponse -> {
            DetailRow("Name", operation.name)
            operation.value?.let { base64Value ->
                // Decode and intelligently display the value
                val (displayValue, formatDescription) = decodeManageDataValue(base64Value)

                // Show the format description
                Text(
                    text = "Value Format",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
                Text(
                    text = formatDescription,
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Default,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                // Show the decoded or base64 value with copy button
                CopyableDetailRow("Value", displayValue, onCopy)
            } ?: run {
                Text(
                    text = "Value: null (data entry deletion)",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }
        is BumpSequenceOperationResponse -> {
            DetailRow("Bump To", operation.bumpTo.toString())
        }
        is InvokeHostFunctionOperationResponse -> {
            DetailRow("Function", operation.function)
            operation.address?.let { CopyableDetailRow("Address", it, onCopy) }
            operation.parameters?.let { params ->
                if (params.isNotEmpty()) {
                    Text(
                        text = "Parameters (${params.size}):",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                    params.forEachIndexed { idx, param ->
                        DetailRow("  Param ${idx + 1} (${param.type})", param.value)
                    }
                }
            }
            operation.assetBalanceChanges?.let { changes ->
                if (changes.isNotEmpty()) {
                    Text(
                        text = "Asset Balance Changes:",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                    changes.forEach { change ->
                        DetailRow("  ${change.type}", "${change.amount} (${change.assetCode ?: "native"})")
                    }
                }
            }
        }
        is PathPaymentStrictReceiveOperationResponse -> {
            CopyableDetailRow("From", operation.from, onCopy)
            CopyableDetailRow("To", operation.to, onCopy)
            DetailRow("Source Amount", operation.sourceAmount)
            DetailRow("Source Asset", operation.sourceAssetType)
            operation.sourceAssetCode?.let { DetailRow("Source Code", it) }
            operation.sourceAssetIssuer?.let { CopyableDetailRow("Source Issuer", it, onCopy) }
            DetailRow("Amount", operation.amount)
            DetailRow("Asset Type", operation.assetType)
            operation.assetCode?.let { DetailRow("Asset Code", it) }
            operation.assetIssuer?.let { CopyableDetailRow("Asset Issuer", it, onCopy) }
            if (operation.path.isNotEmpty()) {
                DetailRow("Path Length", operation.path.size.toString())
            }
        }
        is PathPaymentStrictSendOperationResponse -> {
            CopyableDetailRow("From", operation.from, onCopy)
            CopyableDetailRow("To", operation.to, onCopy)
            DetailRow("Amount", operation.amount)
            DetailRow("Asset Type", operation.assetType)
            operation.assetCode?.let { DetailRow("Asset Code", it) }
            operation.assetIssuer?.let { CopyableDetailRow("Asset Issuer", it, onCopy) }
            DetailRow("Dest Min", operation.destinationMin)
            DetailRow("Dest Asset", operation.sourceAssetType)
            operation.sourceAssetCode?.let { DetailRow("Dest Code", it) }
            operation.sourceAssetIssuer?.let { CopyableDetailRow("Dest Issuer", it, onCopy) }
            if (operation.path.isNotEmpty()) {
                DetailRow("Path Length", operation.path.size.toString())
            }
        }
        is AllowTrustOperationResponse -> {
            CopyableDetailRow("Trustor", operation.trustor, onCopy)
            CopyableDetailRow("Trustee", operation.trustee, onCopy)
            DetailRow("Asset Type", operation.assetType)
            operation.assetCode?.let { DetailRow("Asset Code", it) }
            DetailRow("Authorize", operation.authorize.toString())
            operation.authorizeToMaintainLiabilities?.let {
                DetailRow("Auth Liabilities", it.toString())
            }
        }
        is ClaimClaimableBalanceOperationResponse -> {
            CopyableDetailRow("Balance ID", operation.balanceId, onCopy)
            CopyableDetailRow("Claimant", operation.claimant, onCopy)
        }
        is CreateClaimableBalanceOperationResponse -> {
            DetailRow("Asset", operation.asset)
            DetailRow("Amount", operation.amount)
            Text(
                text = "Claimants (${operation.claimants.size}):",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold
            )
            operation.claimants.forEach { claimant ->
                CopyableDetailRow("  ", claimant.destination, onCopy)
            }
        }
        is ClawbackOperationResponse -> {
            CopyableDetailRow("From", operation.from, onCopy)
            DetailRow("Amount", operation.amount)
            DetailRow("Asset Type", operation.assetType)
            operation.assetCode?.let { DetailRow("Asset Code", it) }
            operation.assetIssuer?.let { CopyableDetailRow("Asset Issuer", it, onCopy) }
        }
        is ClawbackClaimableBalanceOperationResponse -> {
            CopyableDetailRow("Balance ID", operation.balanceId, onCopy)
        }
        is SetTrustLineFlagsOperationResponse -> {
            CopyableDetailRow("Trustor", operation.trustor, onCopy)
            DetailRow("Asset Type", operation.assetType)
            operation.assetCode?.let { DetailRow("Asset Code", it) }
            operation.assetIssuer?.let { CopyableDetailRow("Asset Issuer", it, onCopy) }
            operation.setFlagStrings?.let { flags ->
                if (flags.isNotEmpty()) {
                    DetailRow("Set Flags", flags.joinToString(", "))
                }
            }
            operation.clearFlagStrings?.let { flags ->
                if (flags.isNotEmpty()) {
                    DetailRow("Clear Flags", flags.joinToString(", "))
                }
            }
        }
        is LiquidityPoolDepositOperationResponse -> {
            CopyableDetailRow("Liquidity Pool ID", operation.liquidityPoolId, onCopy)
            DetailRow("Shares Received", operation.sharesReceived)
            operation.reservesMax.forEachIndexed { idx, reserve ->
                DetailRow("Max Reserve ${idx + 1}", reserve.amount)
            }
            operation.reservesDeposited.forEachIndexed { idx, reserve ->
                DetailRow("Deposited ${idx + 1}", reserve.amount)
            }
        }
        is LiquidityPoolWithdrawOperationResponse -> {
            CopyableDetailRow("Liquidity Pool ID", operation.liquidityPoolId, onCopy)
            DetailRow("Shares", operation.shares)
            operation.reservesMin.forEachIndexed { idx, reserve ->
                DetailRow("Min Reserve ${idx + 1}", reserve.amount)
            }
            operation.reservesReceived.forEachIndexed { idx, reserve ->
                DetailRow("Received ${idx + 1}", reserve.amount)
            }
        }
        is BeginSponsoringFutureReservesOperationResponse -> {
            CopyableDetailRow("Sponsored ID", operation.sponsoredId, onCopy)
        }
        is EndSponsoringFutureReservesOperationResponse -> {
            CopyableDetailRow("Begin Sponsor", operation.beginSponsor, onCopy)
        }
        is RevokeSponsorshipOperationResponse -> {
            operation.accountId?.let { CopyableDetailRow("Account ID", it, onCopy) }
            operation.claimableBalanceId?.let { CopyableDetailRow("Claimable Balance", it, onCopy) }
            operation.dataAccountId?.let { CopyableDetailRow("Data Account", it, onCopy) }
            operation.dataName?.let { DetailRow("Data Name", it) }
            operation.offerId?.let { DetailRow("Offer ID", it.toString()) }
            operation.trustlineAccountId?.let { CopyableDetailRow("Trustline Account", it, onCopy) }
            operation.trustlineAsset?.let { DetailRow("Trustline Asset", it) }
            operation.signerAccountId?.let { CopyableDetailRow("Signer Account", it, onCopy) }
            operation.signerKey?.let { CopyableDetailRow("Signer Key", it, onCopy) }
        }
        is ExtendFootprintTTLOperationResponse -> {
            DetailRow("Extend To", operation.extendTo.toString())
        }
        is RestoreFootprintOperationResponse -> {
            Text(
                text = "Restores archived contract data",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
        is InflationOperationResponse -> {
            Text(
                text = "Inflation operation (deprecated)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
        else -> {
            Text(
                text = "Operation type: ${operation.type}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
private fun RpcTransactionCards(
    transaction: GetTransactionResponse,
    onCopy: (String, String) -> Unit
) {
    // Success header card
    val isSuccess = transaction.status == GetTransactionStatus.SUCCESS
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isSuccess) {
                LightExtendedColors.successContainer
            } else {
                MaterialTheme.colorScheme.errorContainer
            }
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = when (transaction.status) {
                    GetTransactionStatus.SUCCESS -> "Transaction Successful"
                    GetTransactionStatus.FAILED -> "Transaction Failed"
                    GetTransactionStatus.NOT_FOUND -> "Transaction Not Found"
                },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (isSuccess) {
                    LightExtendedColors.onSuccessContainer
                } else {
                    MaterialTheme.colorScheme.onErrorContainer
                }
            )
            Text(
                text = "Fetched from Soroban RPC",
                style = MaterialTheme.typography.bodyMedium,
                color = if (isSuccess) {
                    LightExtendedColors.onSuccessContainer
                } else {
                    MaterialTheme.colorScheme.onErrorContainer
                }
            )
        }
    }

    // Basic Information Card
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
                text = "Transaction Information",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            HorizontalDivider()

            DetailRow("Status", transaction.status.toString())
            transaction.txHash?.let { hash ->
                CopyableDetailRow("Transaction Hash", hash, onCopy)
            }
            transaction.ledger?.let { ledger ->
                DetailRow("Ledger", ledger.toString())
            }
            transaction.createdAt?.let { timestamp ->
                DetailRow("Created At", timestamp.toString())
            }
            transaction.applicationOrder?.let { order ->
                DetailRow("Application Order", order.toString())
            }
            transaction.feeBump?.let { isFeeBump ->
                DetailRow("Fee Bump", if (isFeeBump) "Yes" else "No")
            }
        }
    }

    // Ledger Window Card
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
                text = "Ledger Window",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            HorizontalDivider()

            transaction.latestLedger?.let { latest ->
                DetailRow("Latest Ledger", latest.toString())
            }
            transaction.latestLedgerCloseTime?.let { time ->
                DetailRow("Latest Ledger Close", time.toString())
            }
            transaction.oldestLedger?.let { oldest ->
                DetailRow("Oldest Ledger", oldest.toString())
            }
            transaction.oldestLedgerCloseTime?.let { time ->
                DetailRow("Oldest Ledger Close", time.toString())
            }
        }
    }

    // XDR Data Card
    if (transaction.envelopeXdr != null || transaction.resultXdr != null || transaction.resultMetaXdr != null) {
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
                    text = "XDR Data",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                HorizontalDivider()

                transaction.envelopeXdr?.let { xdr ->
                    CopyableDetailRow("Envelope XDR", xdr, onCopy)
                }
                transaction.resultXdr?.let { xdr ->
                    CopyableDetailRow("Result XDR", xdr, onCopy)
                }
                transaction.resultMetaXdr?.let { xdr ->
                    CopyableDetailRow("Result Meta XDR", xdr, onCopy)
                }
            }
        }
    }

    // Events Card with expandable details (if present)
    transaction.events?.let { events ->
        EventsCard(events, transaction.ledger, onCopy)
    }

    // Return Value Card (if present)
    transaction.getResultValue()?.let { resultValue ->
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f)
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Contract Return Value",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                HorizontalDivider()

                DetailRow("Type", resultValue.discriminant.toString())

                // Make the value copyable using formatSCVal for proper display
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "Value",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                        SelectionContainer {
                            Text(
                                text = formatSCVal(resultValue),
                                style = MaterialTheme.typography.bodyMedium,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    IconButton(
                        onClick = { onCopy(formatSCVal(resultValue), "Contract Return Value") },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy return value",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

/**
 * Displays contract events from Soroban RPC transactions in an expandable format.
 *
 * Events are organized by type (diagnostic, transaction, and contract events) and can be
 * expanded to view detailed information including:
 * - Event type (SYSTEM, CONTRACT, or DIAGNOSTIC)
 * - Contract ID (if applicable)
 * - Topics (list of SCVal values, often used for event indexing)
 * - Data (the event payload as an SCVal)
 * - Ledger information
 *
 * Each event can be individually expanded/collapsed by clicking on it. The display follows
 * the same pattern as the operations display for consistency.
 *
 * @param events The Events object containing diagnostic, transaction, and contract events
 * @param ledger The ledger number where the transaction was included (for context)
 * @param onCopy Callback function for copying text to clipboard with label
 */
@Composable
private fun EventsCard(
    events: com.soneso.stellar.sdk.rpc.responses.Events,
    ledger: Long?,
    onCopy: (String, String) -> Unit
) {
    // Parse all events and flatten them into a single list with metadata
    val allEvents = remember(events) {
        buildList {
            // Diagnostic events
            events.parseDiagnosticEventsXdr()?.forEachIndexed { index, diagnosticEvent ->
                add(EventItem.DiagnosticEvent(index, diagnosticEvent))
            }

            // Transaction events
            events.parseTransactionEventsXdr()?.forEachIndexed { index, transactionEvent ->
                add(EventItem.TransactionEvent(index, transactionEvent))
            }

            // Contract events (nested by operation)
            events.parseContractEventsXdr()?.forEachIndexed { operationIndex, operationEvents ->
                operationEvents.forEachIndexed { eventIndex, contractEvent ->
                    add(EventItem.ContractEvent(operationIndex, eventIndex, contractEvent))
                }
            }
        }
    }

    // Show diagnostic information if no events were parsed
    if (allEvents.isEmpty()) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Events Debug Information",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                HorizontalDivider()

                // Show raw events object info
                Text(
                    text = "Events object: present",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Show parsing results
                val diagnostic = events.parseDiagnosticEventsXdr()
                val transaction = events.parseTransactionEventsXdr()
                val contract = events.parseContractEventsXdr()

                Text(
                    text = "Diagnostic events: ${diagnostic?.size ?: "null"}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Transaction events: ${transaction?.size ?: "null"}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Contract events: ${contract?.sumOf { it.size } ?: "null"}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Show raw events string for debugging
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Raw events data:",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                SelectionContainer {
                    Text(
                        text = events.toString(),
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        return
    }

    // Track which events are expanded
    var expandedEvents by remember { mutableStateOf(setOf<Int>()) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Contract Events (${allEvents.size})",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Click on an event to view details",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
            HorizontalDivider()

            allEvents.forEachIndexed { index, eventItem ->
                val isExpanded = expandedEvents.contains(index)

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            expandedEvents = if (isExpanded) {
                                expandedEvents - index
                            } else {
                                expandedEvents + index
                            }
                        },
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Event header (always visible)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = eventItem.getTitle(),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = eventItem.getSubtitle(),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                )
                            }
                            Icon(
                                imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = if (isExpanded) "Collapse" else "Expand"
                            )
                        }

                        // Expandable event details
                        AnimatedVisibility(
                            visible = isExpanded,
                            enter = fadeIn() + expandVertically(),
                            exit = fadeOut() + shrinkVertically()
                        ) {
                            Column(
                                modifier = Modifier.padding(top = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                HorizontalDivider()
                                EventDetails(eventItem, ledger, onCopy)
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Sealed class representing different types of events that can be displayed.
 * Provides a unified interface for rendering event metadata and details.
 */
private sealed class EventItem {
    abstract fun getTitle(): String
    abstract fun getSubtitle(): String
    abstract fun getContractEvent(): ContractEventXdr?

    data class DiagnosticEvent(val index: Int, val event: DiagnosticEventXdr) : EventItem() {
        override fun getTitle() = "Diagnostic Event ${index + 1}"
        override fun getSubtitle() = "Runtime debugging information"
        override fun getContractEvent() = event.event
    }

    data class TransactionEvent(val index: Int, val event: TransactionEventXdr) : EventItem() {
        override fun getTitle() = "Transaction Event ${index + 1}"
        override fun getSubtitle() = "Transaction-level event"
        override fun getContractEvent() = event.event
    }

    data class ContractEvent(val operationIndex: Int, val eventIndex: Int, val event: ContractEventXdr) : EventItem() {
        override fun getTitle() = "Contract Event (Op ${operationIndex + 1}.${eventIndex + 1})"
        override fun getSubtitle() = "Emitted by smart contract"
        override fun getContractEvent() = event
    }
}

/**
 * Displays detailed information about a contract event.
 *
 * Shows all available event fields including:
 * - Event type (SYSTEM, CONTRACT, or DIAGNOSTIC)
 * - Contract ID (if present)
 * - Topics (indexed event parameters)
 * - Data (event payload)
 * - Ledger information
 *
 * Topics and data are displayed as their SCVal discriminant with the string representation,
 * providing insight into the event structure without requiring full XDR parsing.
 */
@Composable
private fun EventDetails(
    eventItem: EventItem,
    ledger: Long?,
    onCopy: (String, String) -> Unit
) {
    val contractEvent = eventItem.getContractEvent() ?: return

    // Display event type
    DetailRow("Event Type", contractEvent.type.name)

    // Display contract ID if present
    contractEvent.contractId?.let { contractId ->
        val contractIdStr = try {
            com.soneso.stellar.sdk.StrKey.encodeContract(contractId.value.value)
        } catch (e: Exception) {
            "Invalid contract ID"
        }
        CopyableDetailRow("Contract ID", contractIdStr, onCopy)
    } ?: run {
        DetailRow("Contract ID", "None (system event)")
    }

    // Display in successful contract call status
    DetailRow("In Successful Contract Call", "Yes")

    // Display ledger information if available
    ledger?.let {
        DetailRow("Ledger", it.toString())
    }

    HorizontalDivider()
    Text(
        text = "Event Data",
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(vertical = 4.dp)
    )

    // Extract topics and data from the event body
    when (val body = contractEvent.body) {
        is ContractEventBodyXdr.V0 -> {
            val eventData = body.value

            // Display topics
            if (eventData.topics.isNotEmpty()) {
                Text(
                    text = "Topics (${eventData.topics.size}):",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 8.dp)
                )
                eventData.topics.forEachIndexed { index, topic ->
                    val topicDescription = formatSCVal(topic, indentLevel = 0)
                    Column(
                        modifier = Modifier.padding(start = 8.dp, top = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "Topic ${index + 1}:",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                        SelectionContainer {
                            Text(
                                text = topicDescription,
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                Text(
                    text = "No topics",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.padding(start = 8.dp)
                )
            }

            // Display event data/value
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Value:",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold
            )
            val dataDescription = formatSCVal(eventData.data, indentLevel = 0)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                SelectionContainer(
                    modifier = Modifier.weight(1f).padding(start = 8.dp, top = 4.dp)
                ) {
                    Text(
                        text = dataDescription,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(
                    onClick = { onCopy(dataDescription, "Event Value") },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Copy event value",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

/**
 * Formats an SCVal for display, showing both the type and a human-readable representation.
 *
 * This function recursively expands complex types (Maps and Vecs) to show their complete contents,
 * with proper indentation for nested structures. This provides developers with comprehensive insight
 * into event data without requiring manual XDR parsing.
 *
 * Key features:
 * - Map types: Expands to show all key-value pairs with numbering and indentation
 * - Vec types: Expands to show all elements with numbering and indentation
 * - Nested structures: Recursively formats with increased indentation
 * - Simple types: Shows type label and formatted value
 *
 * @param scVal The SCVal to format
 * @param indentLevel The current indentation level (used for nested structures)
 * @return A formatted string showing the type and value, with newlines and indentation for complex types
 */
private fun formatSCVal(scVal: SCValXdr, indentLevel: Int = 0): String {
    val indent = "  ".repeat(indentLevel)
    val nextIndent = "  ".repeat(indentLevel + 1)

    return when (scVal) {
        is SCValXdr.B -> "Bool: ${scVal.value}"
        is SCValXdr.Void -> "Void"
        is SCValXdr.Error -> "Error: ${scVal.value}"
        is SCValXdr.U32 -> "U32: ${scVal.value.value}"
        is SCValXdr.I32 -> "I32: ${scVal.value.value}"
        is SCValXdr.U64 -> "U64: ${scVal.value.value}"
        is SCValXdr.I64 -> "I64: ${scVal.value.value}"
        is SCValXdr.Timepoint -> "Timepoint: ${scVal.value.value}"
        is SCValXdr.Duration -> "Duration: ${scVal.value.value}"
        is SCValXdr.U128 -> "U128: ${formatU128(scVal.value)}"
        is SCValXdr.I128 -> "I128: ${formatI128(scVal.value)}"
        is SCValXdr.U256 -> "U256: ${formatU256(scVal.value)}"
        is SCValXdr.I256 -> "I256: ${formatI256(scVal.value)}"
        is SCValXdr.Bytes -> "Bytes: ${formatBytes(scVal.value.value)}"
        is SCValXdr.Str -> "String: \"${scVal.value.value}\""
        is SCValXdr.Sym -> "Symbol: ${scVal.value.value}"

        is SCValXdr.Vec -> {
            val elements = scVal.value?.value
            if (elements.isNullOrEmpty()) {
                "Vec: [0 elements]"
            } else {
                val formattedElements = elements.mapIndexed { index, element ->
                    val formattedElement = formatSCVal(element, indentLevel + 1)
                    "${nextIndent}Element ${index + 1}: $formattedElement"
                }
                "Vec: [${elements.size} elements]\n${formattedElements.joinToString("\n")}"
            }
        }

        is SCValXdr.Map -> {
            val entries = scVal.value?.value
            if (entries.isNullOrEmpty()) {
                "Map: {0 entries}"
            } else {
                val formattedEntries = entries.mapIndexed { index, entry ->
                    val keyStr = formatSCVal(entry.key, indentLevel + 1)
                    val valStr = formatSCVal(entry.`val`, indentLevel + 1)
                    "${nextIndent}Entry ${index + 1}:\n${nextIndent}  Key: $keyStr\n${nextIndent}  Value: $valStr"
                }
                "Map: {${entries.size} entries}\n${formattedEntries.joinToString("\n")}"
            }
        }

        is SCValXdr.Address -> "Address: ${formatAddress(scVal.value)}"
        is SCValXdr.Instance -> "ContractInstance"
        is SCValXdr.NonceKey -> "LedgerKeyNonce: ${scVal.value.nonce.value}"
    }
}

/**
 * Formats an SCAddress as a strkey-encoded string.
 */
private fun formatAddress(address: SCAddressXdr): String {
    return when (address) {
        is SCAddressXdr.AccountId -> {
            try {
                val publicKey = address.value.value
                when (publicKey) {
                    is PublicKeyXdr.Ed25519 -> {
                        com.soneso.stellar.sdk.StrKey.encodeEd25519PublicKey(publicKey.value.value)
                    }
                }
            } catch (e: Exception) {
                "Invalid account ID"
            }
        }
        is SCAddressXdr.ContractId -> {
            try {
                val hashBytes = address.value.value.value
                com.soneso.stellar.sdk.StrKey.encodeContract(hashBytes)
            } catch (e: Exception) {
                "Invalid contract ID"
            }
        }
        is SCAddressXdr.MuxedAccount -> {
            "Muxed: ID=${address.value.id.value}"
        }
        is SCAddressXdr.ClaimableBalanceId -> {
            try {
                // StrKey.encodeClaimableBalance handles both 32-byte (hash only) and 33-byte (type + hash) inputs
                val balanceId = address.value
                when (balanceId) {
                    is ClaimableBalanceIDXdr.V0 -> {
                        com.soneso.stellar.sdk.StrKey.encodeClaimableBalance(balanceId.value.value)
                    }
                }
            } catch (e: Exception) {
                "Claimable Balance: ${address.value}"
            }
        }
        is SCAddressXdr.LiquidityPoolId -> {
            "Liquidity Pool: ${address.value}"
        }
    }
}

/**
 * Formats a U128 value as a decimal string.
 */
private fun formatU128(value: UInt128PartsXdr): String {
    val hi = value.hi.value.toULong()
    val lo = value.lo.value.toULong()
    return if (hi == 0UL) {
        lo.toString()
    } else {
        "${hi shl 64 or lo}"
    }
}

/**
 * Formats an I128 value as a decimal string.
 */
private fun formatI128(value: Int128PartsXdr): String {
    val hi = value.hi.value
    val lo = value.lo.value.toULong()
    return if (hi == 0L && lo <= Long.MAX_VALUE.toULong()) {
        lo.toLong().toString()
    } else {
        "${hi}:${lo}"
    }
}

/**
 * Formats a U256 value as a hex string (showing first/last bytes).
 */
private fun formatU256(value: UInt256PartsXdr): String {
    return "0x...${value.hiHi.value.toString(16)}"
}

/**
 * Formats an I256 value as a hex string (showing first/last bytes).
 */
private fun formatI256(value: Int256PartsXdr): String {
    return "0x...${value.hiHi.value.toString(16)}"
}

/**
 * Formats a byte array, showing length and first few bytes.
 */
private fun formatBytes(bytes: ByteArray): String {
    return if (bytes.size <= 32) {
        bytes.joinToString("") { it.toHexString() }
    } else {
        "${bytes.take(16).joinToString("") { it.toHexString() }}...${bytes.takeLast(8).joinToString("") { it.toHexString() }} (${bytes.size} bytes)"
    }
}

/**
 * Converts a byte to a 2-character hex string (platform-independent).
 */
private fun Byte.toHexString(): String {
    val value = this.toInt() and 0xFF
    val hex = value.toString(16)
    return if (hex.length == 1) "0$hex" else hex
}

/**
 * Reusable component for displaying a detail row with full value and copy button.
 * Uses SelectionContainer for text selection and IconButton for copying.
 */
@Composable
private fun CopyableDetailRow(
    label: String,
    value: String,
    onCopy: (String, String) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
            SelectionContainer {
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        IconButton(
            onClick = { onCopy(value, label) },
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                imageVector = Icons.Default.ContentCopy,
                contentDescription = "Copy $label",
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

/**
 * Non-copyable detail row for simple values (numbers, booleans, etc.)
 */
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
private fun ErrorCard(error: FetchTransactionResult.Error) {
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
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Column(
                modifier = Modifier.padding(start = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "• Verify the transaction hash is correct (64 hex characters)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Text(
                    text = "• Make sure you're using the correct API (Horizon vs Soroban RPC)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Text(
                    text = "• Confirm the transaction exists on testnet",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Text(
                    text = "• Check if the transaction is outside the retention window (RPC only)",
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
