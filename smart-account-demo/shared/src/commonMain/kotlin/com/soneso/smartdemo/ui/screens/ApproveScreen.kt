package com.soneso.smartdemo.ui.screens

/**
 * Token allowance approval screen: select token, enter spender address, amount, and
 * expiration, then submit the approval.
 *
 * When the connected smart account has multiple signers (from context rules), a signer
 * picker is shown so the user can choose which signers co-authorize the transaction.
 * Approval logic is handled by ApproveFlow.
 */

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.soneso.stellar.sdk.StrKey
import com.soneso.smartdemo.flows.approveAllowance
import com.soneso.smartdemo.flows.buildSelectedSigners
import com.soneso.smartdemo.flows.fetchAllowance
import com.soneso.smartdemo.flows.isSinglePasskeyTransfer
import com.soneso.smartdemo.flows.loadAvailableSigners
import com.soneso.smartdemo.flows.multiSignerApproveAllowanceWithEd25519
import com.soneso.smartdemo.platform.getClipboard
import com.soneso.smartdemo.state.ActivityLogState
import com.soneso.smartdemo.state.DemoState
import com.soneso.smartdemo.ui.components.SignerPickerDialog
import com.soneso.smartdemo.util.SignerInfo
import com.soneso.smartdemo.util.isUserCancellation
import com.ionspin.kotlin.bignum.decimal.BigDecimal
import kotlinx.coroutines.launch

class ApproveScreen : Screen {

    companion object {
        private val EXPIRATION_LABELS = listOf("1 day", "10 days", "30 days")
        private val EXPIRATION_OFFSETS = listOf(17_280u, 172_800u, 518_400u)
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val scope = rememberCoroutineScope()
        val clipboard = remember { getClipboard() }
        val snackbarHostState = remember { SnackbarHostState() }

        // Form state
        var spender by remember { mutableStateOf("") }
        var amount by remember { mutableStateOf("") }
        var selectedExpirationIndex by remember { mutableStateOf(0) }
        var expirationDropdownExpanded by remember { mutableStateOf(false) }

        // Loading / result state
        var isLoading by remember { mutableStateOf(false) }
        var errorMessage by remember { mutableStateOf<String?>(null) }
        var txHash by remember { mutableStateOf<String?>(null) }
        var currentAllowance by remember { mutableStateOf<String?>(null) }
        var allowanceFetched by remember { mutableStateOf(false) }

        // Snapshots captured at submission time for the success card
        var submittedSpender by remember { mutableStateOf("") }
        var submittedAmount by remember { mutableStateOf("") }

        // Multi-signer state: available signers loaded from context rules
        var availableSigners by remember { mutableStateOf<List<SignerInfo>>(emptyList()) }
        var signersLoaded by remember { mutableStateOf(false) }

        // Signer picker dialog visibility
        var showSignerPicker by remember { mutableStateOf(false) }

        // Uses the DEMO token contract exclusively
        val tokenContract = DemoState.demoTokenContractId ?: ""

        // Validation
        val spenderError = validateSpender(spender)
        val amountError = validateAmount(amount)
        val isFormValid = spender.isNotBlank() &&
            amount.isNotBlank() &&
            spenderError == null &&
            amountError == null &&
            DemoState.demoTokenContractId != null

        fun handleApproveError(message: String) {
            if (isUserCancellation(message)) {
                errorMessage = "Passkey authentication cancelled"
                ActivityLogState.info("Passkey authentication cancelled")
            } else {
                errorMessage = "Approve failed: $message"
                ActivityLogState.error(message)
            }
        }

        // Load available signers from context rules when the wallet is connected.
        // This determines whether to show the single-signer or multi-signer path.
        LaunchedEffect(DemoState.isConnected, DemoState.kit) {
            if (DemoState.isConnected && DemoState.kit != null) {
                availableSigners = loadAvailableSigners()
                signersLoaded = true
            }
        }

        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                TopAppBar(
                    title = { Text("Approve") },
                    navigationIcon = {
                        IconButton(onClick = { navigator.pop() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        titleContentColor = MaterialTheme.colorScheme.onPrimary,
                        navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                    )
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Not connected guard
                if (!DemoState.isConnected) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Text(
                            text = "No wallet connected. Please connect a wallet first.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(16.dp)
                        )
                    }

                    Button(
                        onClick = { navigator.pop() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Go Back")
                    }
                    return@Scaffold
                }

                // Info Card
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
                            text = "Token Allowance",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Approve a token spending allowance for another address. " +
                                "The spender can transfer up to the approved amount from your " +
                                "smart account until the allowance expires.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Current Balance Display
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "DEMO Balance",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = "${DemoState.demoTokenBalance ?: "0.0"} DEMO",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }

                // Token Contract Display
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "Token Contract",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = if (DemoState.demoTokenContractId != null) {
                                "DEMO (${DemoState.demoTokenContractId})"
                            } else {
                                "DEMO token not deployed"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Spender Address Input
                OutlinedTextField(
                    value = spender,
                    onValueChange = {
                        spender = it
                        errorMessage = null
                    },
                    label = { Text("Spender Address") },
                    placeholder = { Text("G... or C...") },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isLoading && txHash == null,
                    singleLine = true,
                    isError = spender.isNotBlank() && spenderError != null,
                    supportingText = {
                        if (spender.isNotBlank() && spenderError != null) {
                            Text(spenderError)
                        } else {
                            Text("Address to grant the allowance to")
                        }
                    }
                )

                // Amount Input
                OutlinedTextField(
                    value = amount,
                    onValueChange = {
                        amount = it
                        errorMessage = null
                    },
                    label = { Text("Amount") },
                    placeholder = { Text("e.g. 10.0") },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isLoading && txHash == null,
                    singleLine = true,
                    isError = amount.isNotBlank() && amountError != null,
                    supportingText = {
                        if (amount.isNotBlank() && amountError != null) {
                            Text(amountError)
                        } else {
                            Text("Amount to approve")
                        }
                    }
                )

                // Expiration Selection
                ExposedDropdownMenuBox(
                    expanded = expirationDropdownExpanded && !isLoading && txHash == null,
                    onExpandedChange = {
                        if (!isLoading && txHash == null) expirationDropdownExpanded = it
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = EXPIRATION_LABELS[selectedExpirationIndex],
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Expiration") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expirationDropdownExpanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                        enabled = !isLoading && txHash == null,
                        colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors()
                    )
                    ExposedDropdownMenu(
                        expanded = expirationDropdownExpanded && !isLoading && txHash == null,
                        onDismissRequest = { expirationDropdownExpanded = false }
                    ) {
                        EXPIRATION_LABELS.forEachIndexed { index, label ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = {
                                    selectedExpirationIndex = index
                                    expirationDropdownExpanded = false
                                }
                            )
                        }
                    }
                }

                // Error Message
                if (errorMessage != null) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        SelectionContainer {
                            Text(
                                text = errorMessage!!,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }
                }

                // Approve Button — shown when txHash is null (no completed approval yet)
                if (txHash == null) {
                    Button(
                        onClick = {
                            // If only 1 signer (or signers not yet loaded), do a simple approve.
                            // If multiple signers are available, show the signer picker first.
                            if (!signersLoaded || availableSigners.size <= 1) {
                                // Single-signer path: trigger passkey auth and submit directly
                                scope.launch {
                                    isLoading = true
                                    errorMessage = null

                                    try {
                                        ActivityLogState.info(
                                            "Approving $amount DEMO for ${spender.take(8)}..."
                                        )

                                        val capturedSpender = spender
                                        val capturedAmount = amount

                                        val result = approveAllowance(
                                            tokenContract = tokenContract,
                                            spenderAddress = capturedSpender,
                                            amount = capturedAmount,
                                            expirationLedgerOffset = EXPIRATION_OFFSETS[selectedExpirationIndex]
                                        )

                                        if (result.success) {
                                            submittedSpender = capturedSpender
                                            submittedAmount = capturedAmount
                                            txHash = result.hash
                                            scope.launch {
                                                currentAllowance = fetchAllowance(tokenContract, capturedSpender)
                                                allowanceFetched = true
                                            }
                                        } else {
                                            throw Exception(result.error ?: "Approve failed")
                                        }
                                    } catch (e: Throwable) {
                                        handleApproveError(e.message ?: "Unknown error")
                                    } finally {
                                        isLoading = false
                                    }
                                }
                            } else {
                                // Multi-signer path: show signer picker to collect signers
                                showSignerPicker = true
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isLoading && isFormValid && DemoState.kit != null
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text(if (isLoading) "Approving..." else "Approve")
                    }
                }

                // Success Result Section
                if (txHash != null) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "Approve Successful",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )

                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = "Transaction Hash",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Row(
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = txHash!!,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontFamily = FontFamily.Monospace,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    OutlinedButton(
                                        onClick = {
                                            scope.launch {
                                                clipboard.copyToClipboard(txHash!!)
                                                snackbarHostState.showSnackbar("Transaction hash copied")
                                            }
                                        },
                                        modifier = Modifier.padding(start = 8.dp)
                                    ) {
                                        Text("Copy", style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }

                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = "Amount Approved",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text(
                                    text = "$submittedAmount DEMO",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }

                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = "Spender",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text(
                                    text = submittedSpender,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = "Current Allowance",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text(
                                    text = when {
                                        currentAllowance != null -> "$currentAllowance DEMO"
                                        allowanceFetched -> "Unable to fetch"
                                        else -> "Loading..."
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }

                    // New Approve Button
                    Button(
                        onClick = {
                            spender = ""
                            amount = ""
                            txHash = null
                            errorMessage = null
                            currentAllowance = null
                            allowanceFetched = false
                            selectedExpirationIndex = 0
                            submittedSpender = ""
                            submittedAmount = ""
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("New Approve")
                    }

                    // Back to Main Button
                    Button(
                        onClick = { navigator.pop() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Go to Main Screen")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }

        // Signer picker dialog — shown when multiple signers are available.
        // The dialog handles secret key entry for delegated signers (Stellar keypairs).
        // On confirm, keypairs are registered in the external signer manager via
        // registerDelegatedKeypairs(), then multiSignerApproveAllowance() is called.
        if (showSignerPicker) {
            SignerPickerDialog(
                isOpen = true,
                onDismiss = { showSignerPicker = false },
                availableSigners = availableSigners.map { it.signer },
                activeCredentialId = DemoState.credentialId,
                title = "Select Signers",
                description = "Choose which signers co-authorize this approval. " +
                    "For Stellar account signers, enter the secret key to enable signing.",
                onConfirm = { selectedSigners, delegatedKeyPairs, ed25519Secrets ->
                    showSignerPicker = false

                    scope.launch {
                        isLoading = true
                        errorMessage = null

                        val capturedSpender = spender
                        val capturedAmount = amount

                        try {
                            if (isSinglePasskeyTransfer(selectedSigners)) {
                                ActivityLogState.info(
                                    "Approving $capturedAmount DEMO for ${capturedSpender.take(8)}..."
                                )

                                val result = approveAllowance(
                                    tokenContract = tokenContract,
                                    spenderAddress = capturedSpender,
                                    amount = capturedAmount,
                                    expirationLedgerOffset = EXPIRATION_OFFSETS[selectedExpirationIndex]
                                )

                                if (result.success) {
                                    submittedSpender = capturedSpender
                                    submittedAmount = capturedAmount
                                    txHash = result.hash
                                    scope.launch {
                                        currentAllowance = fetchAllowance(tokenContract, capturedSpender)
                                        allowanceFetched = true
                                    }
                                } else {
                                    throw Exception(result.error ?: "Approve failed")
                                }
                            } else {
                                val selected = buildSelectedSigners(selectedSigners)

                                ActivityLogState.info(
                                    "Multi-signer approve: $capturedAmount DEMO for ${capturedSpender.take(8)}... " +
                                        "(${selected.size} signer(s))"
                                )

                                val result = multiSignerApproveAllowanceWithEd25519(
                                    tokenContract = tokenContract,
                                    spenderAddress = capturedSpender,
                                    amount = capturedAmount,
                                    expirationLedgerOffset = EXPIRATION_OFFSETS[selectedExpirationIndex],
                                    selectedSigners = selected,
                                    delegatedKeyPairs = delegatedKeyPairs,
                                    ed25519Secrets = ed25519Secrets
                                )

                                if (result.success) {
                                    submittedSpender = capturedSpender
                                    submittedAmount = capturedAmount
                                    txHash = result.hash
                                    scope.launch {
                                        currentAllowance = fetchAllowance(tokenContract, capturedSpender)
                                        allowanceFetched = true
                                    }
                                } else {
                                    throw Exception(result.error ?: "Approve failed")
                                }
                            }
                        } catch (e: Throwable) {
                            handleApproveError(e.message ?: "Unknown error")
                        } finally {
                            isLoading = false
                        }
                    }
                }
            )
        }
    }

    private fun validateSpender(value: String): String? {
        if (value.isBlank()) return null
        if (!StrKey.isValidEd25519PublicKey(value) && !StrKey.isValidContract(value)) {
            return "Must be a valid Stellar account (G...) or contract (C...) address"
        }
        return null
    }

    private fun validateAmount(value: String): String? {
        if (value.isBlank()) return null
        if (value.contains('e', ignoreCase = true)) {
            return "Scientific notation is not supported"
        }
        val parsed = try {
            BigDecimal.parseString(value)
        } catch (e: Exception) {
            return "Must be a valid number"
        }
        if (parsed <= BigDecimal.ZERO) {
            return "Must be greater than zero"
        }
        return null
    }
}
