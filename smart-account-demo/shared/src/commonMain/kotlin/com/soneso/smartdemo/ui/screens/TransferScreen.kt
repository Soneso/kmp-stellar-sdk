package com.soneso.smartdemo.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.soneso.smartdemo.config.DemoConfig
import com.soneso.smartdemo.state.ActivityLogState
import com.soneso.smartdemo.state.DemoState
import com.soneso.smartdemo.util.formatStroopsAsXlm
import com.soneso.stellar.sdk.AssetTypeNative
import com.soneso.stellar.sdk.Network
import com.soneso.stellar.sdk.rpc.SorobanServer
import kotlinx.coroutines.launch

class TransferScreen : Screen {
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val scope = rememberCoroutineScope()

        var tokenContract by remember { mutableStateOf(DemoConfig.NATIVE_TOKEN_CONTRACT) }
        var recipient by remember { mutableStateOf("") }
        var amount by remember { mutableStateOf("") }
        var isLoading by remember { mutableStateOf(false) }
        var errorMessage by remember { mutableStateOf<String?>(null) }
        var txHash by remember { mutableStateOf<String?>(null) }

        // Validation
        val recipientError = validateRecipient(recipient)
        val amountError = validateAmount(amount)
        val tokenContractError = validateTokenContract(tokenContract)
        val isFormValid = recipient.isNotBlank() &&
            amount.isNotBlank() &&
            recipientError == null &&
            amountError == null &&
            tokenContractError == null

        Scaffold(
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
                            text = "Send XLM tokens from your smart account to another Stellar address. " +
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
                            text = "Current Balance",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = "${DemoState.balance ?: "Unknown"} XLM",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = "From: ${DemoState.contractId?.let { "${it.take(8)}...${it.takeLast(8)}" } ?: "Unknown"}",
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }

                // Token Contract Field
                OutlinedTextField(
                    value = tokenContract,
                    onValueChange = {
                        tokenContract = it
                        errorMessage = null
                    },
                    label = { Text("Token Contract") },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isLoading && txHash == null,
                    singleLine = true,
                    isError = tokenContract.isNotBlank() && tokenContractError != null,
                    supportingText = {
                        if (tokenContract.isNotBlank() && tokenContractError != null) {
                            Text(tokenContractError)
                        } else {
                            Text("XLM native token contract (C-address)")
                        }
                    }
                )

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
                    label = { Text("Amount (XLM)") },
                    placeholder = { Text("e.g. 10.0") },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isLoading && txHash == null,
                    singleLine = true,
                    isError = amount.isNotBlank() && amountError != null,
                    supportingText = {
                        if (amount.isNotBlank() && amountError != null) {
                            Text(amountError)
                        } else {
                            Text("Amount in XLM to transfer")
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
                        Text(
                            text = errorMessage!!,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }

                // Transfer Button
                if (txHash == null) {
                    Button(
                        onClick = {
                            scope.launch {
                                isLoading = true
                                errorMessage = null

                                try {
                                    val kit = DemoState.kit
                                        ?: throw Exception("Smart Account Kit not initialized")

                                    val parsedAmount = amount.toDoubleOrNull()
                                        ?: throw Exception("Invalid amount")

                                    ActivityLogState.info(
                                        "Transferring $amount XLM to ${recipient.take(8)}..."
                                    )

                                    val result = kit.transactionOperations.transfer(
                                        tokenContract = tokenContract,
                                        recipient = recipient,
                                        amount = parsedAmount
                                    )

                                    if (result.success) {
                                        txHash = result.hash
                                        ActivityLogState.success(
                                            "Transfer successful! Hash: ${result.hash ?: "unknown"}"
                                        )

                                        // Refresh balance
                                        refreshBalance()
                                    } else {
                                        throw Exception(result.error ?: "Transfer failed")
                                    }
                                } catch (e: Exception) {
                                    val message = e.message ?: "Unknown error"
                                    if (message.contains("cancelled", ignoreCase = true) ||
                                        (message.contains("user", ignoreCase = true) &&
                                            (message.contains("cancel", ignoreCase = true) ||
                                                message.contains("abort", ignoreCase = true)))
                                    ) {
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
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isLoading && isFormValid && DemoState.kit != null
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier
                                    .size(20.dp)
                                    .padding(end = 8.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
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
                                Text(
                                    text = txHash!!,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = "Amount Sent",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text(
                                    text = "$amount XLM",
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
                                Text(
                                    text = "${DemoState.balance ?: "Unknown"} XLM",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
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
        val parsed = value.toDoubleOrNull()
        if (parsed == null) {
            return "Must be a valid number"
        }
        if (parsed <= 0) {
            return "Must be greater than zero"
        }
        return null
    }

    private fun validateTokenContract(value: String): String? {
        if (value.isBlank()) return "Token contract is required"
        if (!value.startsWith("C")) {
            return "Must start with C"
        }
        if (value.length != 56) {
            return "Must be 56 characters"
        }
        return null
    }

    private suspend fun refreshBalance() {
        try {
            val contractId = DemoState.contractId ?: return
            val server = SorobanServer(DemoConfig.RPC_URL)
            val balanceResponse = server.getSACBalance(
                contractId,
                AssetTypeNative,
                Network(DemoConfig.NETWORK_PASSPHRASE)
            )
            val newBalance = if (balanceResponse.balanceEntry != null) {
                formatStroopsAsXlm(balanceResponse.balanceEntry!!.amount)
            } else "0.0"
            DemoState.updateBalance(newBalance)
            ActivityLogState.info("Updated balance: $newBalance XLM")
        } catch (e: Exception) {
            ActivityLogState.error("Failed to refresh balance: ${e.message}")
        }
    }
}
