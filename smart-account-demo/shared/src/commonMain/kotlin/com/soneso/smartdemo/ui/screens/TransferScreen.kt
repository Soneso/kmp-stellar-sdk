package com.soneso.smartdemo.ui.screens

/**
 * Token transfer screen: select token, enter recipient and amount, submit transfer.
 *
 * When the connected smart account has multiple signers (from context rules), a signer
 * picker is shown so the user can choose which signers co-authorize the transaction.
 * Transfer logic is handled by TransferFlow.
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
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import com.soneso.smartdemo.config.DemoConfig
import com.soneso.smartdemo.flows.multiSignerTransfer
import com.soneso.smartdemo.flows.transfer
import com.soneso.smartdemo.platform.getClipboard
import com.soneso.smartdemo.state.ActivityLogState
import com.soneso.smartdemo.state.DemoState
import com.soneso.smartdemo.ui.components.SignerPickerDialog
import com.soneso.smartdemo.util.SignerInfo
import com.soneso.smartdemo.util.extractSignersFromRules
import com.soneso.smartdemo.util.fetchAllContextRules
import com.soneso.smartdemo.util.isUserCancellation
import androidx.compose.foundation.text.selection.SelectionContainer
import com.soneso.stellar.sdk.smartaccount.core.DelegatedSigner
import com.soneso.stellar.sdk.smartaccount.core.ExternalSigner
import com.soneso.stellar.sdk.smartaccount.core.SmartAccountBuilders
import com.soneso.stellar.sdk.smartaccount.core.SmartAccountSigner
import com.soneso.stellar.sdk.smartaccount.oz.SelectedSigner
import com.ionspin.kotlin.bignum.decimal.BigDecimal
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.launch

class TransferScreen : Screen {

    companion object {
        private const val TOKEN_OPTION_XLM = "xlm"
        private const val TOKEN_OPTION_DEMO = "demo"
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val scope = rememberCoroutineScope()
        val clipboard = remember { getClipboard() }
        val snackbarHostState = remember { SnackbarHostState() }

        // Form state
        var selectedTokenOption by remember { mutableStateOf(TOKEN_OPTION_XLM) }
        var tokenDropdownExpanded by remember { mutableStateOf(false) }
        var recipient by remember { mutableStateOf("") }
        var amount by remember { mutableStateOf("") }

        // Loading / result state
        var isLoading by remember { mutableStateOf(false) }
        var errorMessage by remember { mutableStateOf<String?>(null) }
        var txHash by remember { mutableStateOf<String?>(null) }

        // Multi-signer state: available signers loaded from context rules
        var availableSigners by remember { mutableStateOf<List<SignerInfo>>(emptyList()) }
        var signersLoaded by remember { mutableStateOf(false) }

        // Signer picker dialog visibility
        var showSignerPicker by remember { mutableStateOf(false) }

        val tokenContract = if (selectedTokenOption == TOKEN_OPTION_XLM) {
            DemoConfig.NATIVE_TOKEN_CONTRACT
        } else {
            DemoState.demoTokenContractId ?: ""
        }

        // Validation
        val recipientError = validateRecipient(recipient)
        val amountError = validateAmount(amount)
        val isFormValid = recipient.isNotBlank() &&
            amount.isNotBlank() &&
            recipientError == null &&
            amountError == null &&
            (selectedTokenOption == TOKEN_OPTION_XLM || DemoState.demoTokenContractId != null)

        // Load available signers from context rules when the wallet is connected.
        // This determines whether to show the single-signer or multi-signer path.
        LaunchedEffect(DemoState.isConnected, DemoState.kit) {
            if (DemoState.isConnected && DemoState.kit != null) {
                try {
                    val rules = fetchAllContextRules(DemoState.kit!!)
                    val signers = extractSignersFromRules(
                        rules = rules,
                        connectedCredentialId = DemoState.credentialId,
                        externalWallet = DemoState.externalSignerManager
                    )
                    availableSigners = signers
                    signersLoaded = true
                } catch (_: Exception) {
                    // Graceful fallback: treat as single-signer
                    signersLoaded = true
                }
            }
        }

        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                TopAppBar(
                    title = { Text("Transfer") },
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
                            text = "Token Transfer",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Send tokens from your smart account to another Stellar address. " +
                                "This requires passkey authentication to sign the transaction.",
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
                            text = "Balance",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Text(
                                text = "${DemoState.balance ?: "0.0"} XLM",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            if (DemoState.demoTokenBalance != null) {
                                Text(
                                    text = "${DemoState.demoTokenBalance} DEMO",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }
                }

                // Token Selection
                ExposedDropdownMenuBox(
                    expanded = tokenDropdownExpanded && !isLoading && txHash == null,
                    onExpandedChange = {
                        if (!isLoading && txHash == null) tokenDropdownExpanded = it
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = if (selectedTokenOption == TOKEN_OPTION_XLM) "XLM (Native)" else "Demo Token (DEMO)",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Token") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = tokenDropdownExpanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                        enabled = !isLoading && txHash == null,
                        colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors()
                    )
                    ExposedDropdownMenu(
                        expanded = tokenDropdownExpanded && !isLoading && txHash == null,
                        onDismissRequest = { tokenDropdownExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("XLM (Native)") },
                            onClick = {
                                selectedTokenOption = TOKEN_OPTION_XLM
                                tokenDropdownExpanded = false
                                errorMessage = null
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Demo Token (DEMO)") },
                            onClick = {
                                selectedTokenOption = TOKEN_OPTION_DEMO
                                tokenDropdownExpanded = false
                                errorMessage = null
                            },
                            enabled = DemoState.demoTokenContractId != null
                        )
                    }
                }

                // Recipient Address Input
                OutlinedTextField(
                    value = recipient,
                    onValueChange = {
                        recipient = it
                        errorMessage = null
                    },
                    label = { Text("Recipient Address") },
                    placeholder = { Text("G... or C... address") },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isLoading && txHash == null,
                    singleLine = true,
                    isError = recipient.isNotBlank() && recipientError != null,
                    supportingText = {
                        if (recipient.isNotBlank() && recipientError != null) {
                            Text(recipientError)
                        } else {
                            Text("Stellar account (G...) or contract (C...) address")
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
                            Text("Amount to transfer")
                        }
                    }
                )

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

                // Transfer Button — shown when txHash is null (no completed transfer yet)
                if (txHash == null) {
                    Button(
                        onClick = {
                            // If only 1 signer (or signers not yet loaded), do a simple transfer.
                            // If multiple signers are available, show the signer picker first.
                            if (!signersLoaded || availableSigners.size <= 1) {
                                // Single-signer path: trigger passkey auth and submit directly
                                val handler = CoroutineExceptionHandler { _, throwable ->
                                    val message = throwable.message ?: "Unknown error"
                                    if (isUserCancellation(message)) {
                                        errorMessage = "Passkey authentication cancelled"
                                        ActivityLogState.info("Passkey authentication cancelled")
                                    } else {
                                        errorMessage = "Transfer failed: $message"
                                        ActivityLogState.error(message)
                                    }
                                    isLoading = false
                                }
                                scope.launch(handler) {
                                    isLoading = true
                                    errorMessage = null

                                    try {
                                        val tokenLabel = if (selectedTokenOption == TOKEN_OPTION_XLM) "XLM" else "DEMO"
                                        ActivityLogState.info(
                                            "Transferring $amount $tokenLabel to ${recipient.take(8)}..."
                                        )

                                        val result = transfer(
                                            tokenContract = tokenContract,
                                            recipient = recipient,
                                            amount = amount
                                        )

                                        if (result.success) {
                                            txHash = result.hash
                                        } else {
                                            throw Exception(result.error ?: "Transfer failed")
                                        }
                                    } catch (e: Throwable) {
                                        val message = e.message ?: "Unknown error"
                                        if (isUserCancellation(message)) {
                                            errorMessage = "Passkey authentication cancelled"
                                            ActivityLogState.info("Passkey authentication cancelled")
                                        } else {
                                            errorMessage = "Transfer failed: $message"
                                            ActivityLogState.error(message)
                                        }
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
                        Text(if (isLoading) "Transferring..." else "Transfer")
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
                                text = "Transfer Successful",
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
                                    text = "Amount Sent",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text(
                                    text = if (selectedTokenOption == TOKEN_OPTION_XLM) "$amount XLM" else "$amount DEMO",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }

                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = "Recipient",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text(
                                    text = recipient,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = "Updated Balance",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    Text(
                                        text = "${DemoState.balance ?: "0.0"} XLM",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    if (DemoState.demoTokenBalance != null) {
                                        Text(
                                            text = "${DemoState.demoTokenBalance} DEMO",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // New Transfer Button
                    Button(
                        onClick = {
                            recipient = ""
                            amount = ""
                            txHash = null
                            errorMessage = null
                            selectedTokenOption = TOKEN_OPTION_XLM
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("New Transfer")
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
        // On confirm, keypairs are registered in the external signer manager, then
        // multiSignerTransfer() is called with the selected signer list.
        if (showSignerPicker) {
            SignerPickerDialog(
                isOpen = true,
                onDismiss = { showSignerPicker = false },
                availableSigners = availableSigners.map { it.signer },
                activeCredentialId = DemoState.credentialId,
                title = "Select Signers",
                description = "Choose which signers co-authorize this transfer. " +
                    "For Stellar account signers, enter the secret key to enable signing.",
                onConfirm = { selectedSigners, delegatedKeyPairs ->
                    showSignerPicker = false

                    // Use the simple single-signer transfer path only when exactly
                    // one passkey is selected AND it is the active/connected passkey.
                    // The simple path always signs with the connected passkey, so it
                    // cannot be used for non-active passkeys from context rules.
                    val hasDelegatedSigners = selectedSigners.any { it is DelegatedSigner }
                    val isSinglePasskey = selectedSigners.size == 1 &&
                        !hasDelegatedSigners &&
                        selectedSigners[0] is ExternalSigner &&
                        SmartAccountBuilders.getCredentialIdStringFromSigner(selectedSigners[0]) ==
                            DemoState.credentialId

                    val handler = CoroutineExceptionHandler { _, throwable ->
                        val message = throwable.message ?: "Unknown error"
                        if (isUserCancellation(message)) {
                            errorMessage = "Passkey authentication cancelled"
                            ActivityLogState.info("Passkey authentication cancelled")
                        } else {
                            errorMessage = "Transfer failed: $message"
                            ActivityLogState.error(message)
                        }
                        isLoading = false
                    }
                    scope.launch(handler) {
                        isLoading = true
                        errorMessage = null

                        try {
                            val tokenLabel = if (selectedTokenOption == TOKEN_OPTION_XLM) "XLM" else "DEMO"

                            if (isSinglePasskey) {
                                // Single passkey selected — use the standard transfer path
                                ActivityLogState.info(
                                    "Transferring $amount $tokenLabel to ${recipient.take(8)}..."
                                )

                                val result = transfer(
                                    tokenContract = tokenContract,
                                    recipient = recipient,
                                    amount = amount
                                )

                                if (result.success) {
                                    txHash = result.hash
                                } else {
                                    throw Exception(result.error ?: "Transfer failed")
                                }
                            } else {
                                // Multiple signers or delegated signers — use multi-signer path

                                // Register delegated signer keypairs in the external signer manager
                                // before calling multiSignerTransfer. The adapter's canSignFor()
                                // and signAuthEntry() rely on these registrations.
                                val externalManager = DemoState.externalSignerManager
                                if (externalManager != null && delegatedKeyPairs.isNotEmpty()) {
                                    for ((address, keyPair) in delegatedKeyPairs) {
                                        val secretSeed = keyPair.getSecretSeed()
                                        if (secretSeed != null) {
                                            externalManager.addFromSecret(secretSeed.concatToString())
                                        }
                                    }
                                }

                                val selected = buildSelectedSigners(selectedSigners)

                                ActivityLogState.info(
                                    "Multi-signer transfer: $amount $tokenLabel to ${recipient.take(8)}... " +
                                        "(${selected.size} signer(s))"
                                )

                                val result = multiSignerTransfer(
                                    tokenContract = tokenContract,
                                    recipient = recipient,
                                    amount = amount,
                                    selectedSigners = selected
                                )

                                if (result.success) {
                                    txHash = result.hash
                                } else {
                                    throw Exception(result.error ?: "Transfer failed")
                                }
                            }
                        } catch (e: Throwable) {
                            val message = e.message ?: "Unknown error"
                            if (isUserCancellation(message)) {
                                errorMessage = "Passkey authentication cancelled"
                                ActivityLogState.info("Passkey authentication cancelled")
                            } else {
                                errorMessage = "Transfer failed: $message"
                                ActivityLogState.error(message)
                            }
                        } finally {
                            isLoading = false
                        }
                    }
                }
            )
        }
    }

    /**
     * Converts a list of selected SmartAccountSigner objects (from SignerPickerDialog)
     * into the SelectedSigner list required by multiSignerTransfer().
     *
     * - ExternalSigner with a credential ID → SelectedSigner.Passkey with full keyData
     * - DelegatedSigner → SelectedSigner.Wallet(address)
     * - Other ExternalSigner (no credential ID) → skipped (not yet supported)
     */
    private fun buildSelectedSigners(signers: List<SmartAccountSigner>): List<SelectedSigner> {
        val result = mutableListOf<SelectedSigner>()
        for (signer in signers) {
            when {
                signer is ExternalSigner -> {
                    val credIdStr = SmartAccountBuilders.getCredentialIdStringFromSigner(signer)
                    val credIdBytes = SmartAccountBuilders.getCredentialIdFromSigner(signer)
                    if (credIdStr != null) {
                        result.add(SelectedSigner.Passkey(
                            credentialId = credIdStr,
                            credentialIdBytes = credIdBytes,
                            keyData = signer.keyData
                        ))
                    }
                }
                signer is DelegatedSigner -> {
                    // Delegated (keypair) signer: identified by G-address
                    result.add(SelectedSigner.Wallet(address = signer.address))
                }
            }
        }
        return result
    }

    private fun validateRecipient(value: String): String? {
        if (value.isBlank()) return null
        if (!value.startsWith("G") && !value.startsWith("C")) {
            return "Must start with G (account) or C (contract)"
        }
        if (value.length != 56) {
            return "Must be 56 characters"
        }
        if (value == DemoState.contractId) {
            return "Cannot transfer to your own account"
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
