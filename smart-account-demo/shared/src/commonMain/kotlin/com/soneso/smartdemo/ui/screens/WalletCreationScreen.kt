package com.soneso.smartdemo.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.soneso.smartdemo.config.DemoConfig
import com.soneso.smartdemo.state.ActivityLogState
import com.soneso.smartdemo.state.DemoState
import com.soneso.stellar.sdk.AssetTypeNative
import com.soneso.stellar.sdk.Network
import com.soneso.stellar.sdk.rpc.SorobanServer
import com.soneso.stellar.sdk.smartaccount.oz.CreateWalletResult
import kotlinx.coroutines.launch

class WalletCreationScreen : Screen {
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val scope = rememberCoroutineScope()

        var username by remember { mutableStateOf("Smart Account User") }
        var autoDeploy by remember { mutableStateOf(true) }
        var autoFund by remember { mutableStateOf(true) }
        var isLoading by remember { mutableStateOf(false) }
        var errorMessage by remember { mutableStateOf<String?>(null) }
        var infoMessage by remember { mutableStateOf<String?>(null) }
        var createResult by remember { mutableStateOf<CreateWalletResult?>(null) }
        var fundedAmount by remember { mutableStateOf<Double?>(null) }
        var balance by remember { mutableStateOf<String?>(null) }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Create Wallet") },
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
                            text = "Wallet Creation",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Creating a wallet will register a passkey with your device and deploy a smart account contract to the Stellar network. The passkey is used to authenticate and sign transactions.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Username Input
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Passkey Display Name") },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isLoading && createResult == null,
                    singleLine = true
                )

                // Options Section
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Options",
                            style = MaterialTheme.typography.titleMedium
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = autoDeploy,
                                onCheckedChange = {
                                    autoDeploy = it
                                    if (!it) autoFund = false
                                },
                                enabled = !isLoading && createResult == null
                            )
                            Text(
                                text = "Auto-deploy contract",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = autoFund,
                                onCheckedChange = { autoFund = it },
                                enabled = autoDeploy && !isLoading && createResult == null
                            )
                            Text(
                                text = "Auto-fund with XLM",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(start = 8.dp)
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
                        Text(
                            text = errorMessage!!,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }

                // Info Message
                if (infoMessage != null) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                        )
                    ) {
                        Text(
                            text = infoMessage!!,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }

                // Create Wallet Button
                if (createResult == null) {
                    Button(
                        onClick = {
                            scope.launch {
                                isLoading = true
                                errorMessage = null
                                infoMessage = null

                                try {
                                    val kit = DemoState.kit
                                    if (kit == null) {
                                        errorMessage = "Smart Account Kit not initialized"
                                        ActivityLogState.error("Kit not initialized")
                                        return@launch
                                    }

                                    ActivityLogState.info("Creating wallet with username: $username")

                                    val result = kit.walletOperations.createWallet(
                                        userName = username,
                                        autoSubmit = autoDeploy,
                                        autoFund = autoFund,
                                        nativeTokenContract = if (autoFund) DemoConfig.NATIVE_TOKEN_CONTRACT else null
                                    )

                                    createResult = result
                                    ActivityLogState.success("Wallet created successfully")
                                    ActivityLogState.info("Credential ID: ${result.credentialId}")
                                    ActivityLogState.info("Contract ID: ${result.contractId}")

                                    if (result.transactionHash != null) {
                                        ActivityLogState.info("Transaction Hash: ${result.transactionHash}")
                                    }

                                    // Update DemoState
                                    DemoState.setConnected(true, result.contractId, result.credentialId)

                                    // Fetch balance if deployed
                                    if (autoDeploy) {
                                        try {
                                            val server = SorobanServer(DemoConfig.RPC_URL)
                                            val balanceResponse = server.getSACBalance(
                                                result.contractId,
                                                AssetTypeNative,
                                                Network(DemoConfig.NETWORK_PASSPHRASE)
                                            )
                                            balance = if (balanceResponse.balanceEntry != null) {
                                                com.soneso.smartdemo.util.formatStroopsAsXlm(balanceResponse.balanceEntry!!.amount)
                                            } else "0.0"
                                            DemoState.updateBalance(balance)
                                            ActivityLogState.info("Balance: $balance XLM")
                                        } catch (e: Exception) {
                                            ActivityLogState.error("Failed to fetch balance: ${e.message}")
                                        }
                                    }

                                } catch (e: Exception) {
                                    val message = e.message ?: "Unknown error"
                                    if (message.contains("cancelled", ignoreCase = true) ||
                                        message.contains("user", ignoreCase = true)) {
                                        infoMessage = "Passkey registration cancelled by user"
                                        ActivityLogState.info("User cancelled passkey registration")
                                    } else {
                                        errorMessage = "Failed to create wallet: $message"
                                        ActivityLogState.error(message)
                                    }
                                } finally {
                                    isLoading = false
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isLoading && username.isNotBlank() && DemoState.kit != null
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.padding(end = 8.dp)
                            )
                        }
                        Text(if (isLoading) "Creating..." else "Create Wallet")
                    }
                }

                // Result Section
                if (createResult != null) {
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
                                text = "Wallet Created Successfully",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )

                            ResultField(
                                label = "Credential ID",
                                value = createResult!!.credentialId
                            )

                            ResultField(
                                label = "Contract Address",
                                value = createResult!!.contractId
                            )

                            if (createResult!!.transactionHash != null) {
                                ResultField(
                                    label = "Transaction Hash",
                                    value = createResult!!.transactionHash!!
                                )
                            }

                            if (fundedAmount != null) {
                                ResultField(
                                    label = "Funded Amount",
                                    value = "$fundedAmount XLM"
                                )
                            }

                            if (balance != null) {
                                ResultField(
                                    label = "Current Balance",
                                    value = "$balance XLM"
                                )
                            }
                        }
                    }

                    // Fund Wallet Button (shown if created but not auto-funded)
                    if (!autoFund && fundedAmount == null) {
                        Button(
                            onClick = {
                                scope.launch {
                                    isLoading = true
                                    errorMessage = null

                                    try {
                                        val kit = DemoState.kit
                                        if (kit == null) {
                                            errorMessage = "Smart Account Kit not initialized"
                                            return@launch
                                        }

                                        ActivityLogState.info("Funding wallet...")
                                        val amount = kit.transactionOperations.fundWallet(
                                            nativeTokenContract = DemoConfig.NATIVE_TOKEN_CONTRACT
                                        )
                                        fundedAmount = amount
                                        ActivityLogState.success("Wallet funded with $amount XLM")

                                        // Fetch updated balance
                                        try {
                                            val server = SorobanServer(DemoConfig.RPC_URL)
                                            val balanceResponse = server.getSACBalance(
                                                createResult!!.contractId,
                                                AssetTypeNative,
                                                Network(DemoConfig.NETWORK_PASSPHRASE)
                                            )
                                            balance = if (balanceResponse.balanceEntry != null) {
                                                com.soneso.smartdemo.util.formatStroopsAsXlm(balanceResponse.balanceEntry!!.amount)
                                            } else "0.0"
                                            DemoState.updateBalance(balance)
                                            ActivityLogState.info("Balance: $balance XLM")
                                        } catch (e: Exception) {
                                            ActivityLogState.error("Failed to fetch balance: ${e.message}")
                                        }

                                    } catch (e: Exception) {
                                        errorMessage = "Failed to fund wallet: ${e.message}"
                                        ActivityLogState.error(e.message ?: "Failed to fund wallet")
                                    } finally {
                                        isLoading = false
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !isLoading
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.padding(end = 8.dp)
                                )
                            }
                            Text(if (isLoading) "Funding..." else "Fund Wallet")
                        }
                    }

                    // Go to Main Screen Button
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

    @Composable
    private fun ResultField(label: String, value: String) {
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
