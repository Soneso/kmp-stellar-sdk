package com.soneso.smartdemo.ui.screens

import androidx.compose.foundation.clickable
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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.soneso.smartdemo.config.DemoConfig
import com.soneso.smartdemo.platform.getClipboard
import com.soneso.smartdemo.state.ActivityLogState
import com.soneso.smartdemo.state.DemoState
import com.soneso.smartdemo.token.DemoTokenService
import com.soneso.smartdemo.util.fetchXlmBalance
import com.soneso.smartdemo.util.isUserCancellation
import com.soneso.stellar.sdk.FriendBot
import com.soneso.stellar.sdk.rpc.SorobanServer
import com.soneso.stellar.sdk.smartaccount.oz.CreateWalletResult
import com.soneso.stellar.sdk.smartaccount.oz.OZSmartAccountConfig
import kotlinx.coroutines.launch

class WalletCreationScreen : Screen {
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val scope = rememberCoroutineScope()
        val snackbarHostState = remember { SnackbarHostState() }
        val clipboard = remember { getClipboard() }

        var username by remember { mutableStateOf("Smart Account User") }
        var isLoading by remember { mutableStateOf(false) }
        var progressMessage by remember { mutableStateOf("") }
        var errorMessage by remember { mutableStateOf<String?>(null) }
        var infoMessage by remember { mutableStateOf<String?>(null) }
        var createResult by remember { mutableStateOf<CreateWalletResult?>(null) }
        var balance by remember { mutableStateOf<String?>(null) }
        var demoTokenBalance by remember { mutableStateOf<String?>(null) }

        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
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

                // Progress indicator (shown during wallet creation)
                if (isLoading) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(32.dp),
                                strokeWidth = 3.dp
                            )
                            Text(
                                text = progressMessage.ifEmpty { "Creating..." },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Create Wallet Button
                if (createResult == null && !isLoading) {
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

                                    // Ensure the deployer account is funded. After a testnet reset
                                    // the account no longer exists and deployment would fail.
                                    progressMessage = "Creating wallet..."
                                    val deployer = OZSmartAccountConfig.createDefaultDeployer()
                                    try {
                                        SorobanServer(DemoConfig.RPC_URL).getAccount(deployer.getAccountId())
                                    } catch (e: Exception) {
                                        ActivityLogState.info("Funding deployer account...")
                                        FriendBot.fundTestnetAccount(deployer.getAccountId())
                                        kotlinx.coroutines.delay(5000)
                                    }

                                    ActivityLogState.info("Creating wallet with username: $username")

                                    val result = kit.walletOperations.createWallet(
                                        userName = username,
                                        autoSubmit = true,
                                        autoFund = true,
                                        nativeTokenContract = DemoConfig.NATIVE_TOKEN_CONTRACT
                                    )

                                    ActivityLogState.success("Wallet created successfully")
                                    ActivityLogState.info("Credential ID: ${result.credentialId}")
                                    ActivityLogState.info("Contract ID: ${result.contractId}")

                                    if (result.transactionHash != null) {
                                        ActivityLogState.info("Transaction Hash: ${result.transactionHash}")
                                    }

                                    // Update DemoState connection
                                    DemoState.setConnected(true, result.contractId, result.credentialId)

                                    // Fetch XLM balance
                                    try {
                                        balance = fetchXlmBalance(result.contractId)
                                        DemoState.updateBalance(balance)
                                        ActivityLogState.info("Balance: $balance XLM")
                                    } catch (e: Exception) {
                                        ActivityLogState.error("Failed to fetch balance: ${e.message}")
                                    }

                                    // Deploy demo token and mint DEMO to the new wallet.
                                    // Failure here is non-fatal — wallet creation has already succeeded.
                                    try {
                                        progressMessage = "Deploying demo token..."
                                        ActivityLogState.info("Deploying demo token...")
                                        val tokenService = DemoTokenService(
                                            DemoConfig.RPC_URL,
                                            DemoConfig.NETWORK_PASSPHRASE
                                        )
                                        val tokenResult = tokenService.ensureTokenAndMint(result.contractId)
                                        DemoState.updateDemoToken(tokenResult.tokenContractId)

                                        val mintedFormatted = com.soneso.smartdemo.util.formatStroopsAsXlm(tokenResult.amountMinted)
                                        demoTokenBalance = mintedFormatted
                                        DemoState.updateDemoTokenBalance(mintedFormatted)
                                        ActivityLogState.success("Minted 10,000 DEMO to wallet")
                                    } catch (e: Exception) {
                                        ActivityLogState.error("Demo token minting failed: ${e.message}")
                                    }

                                    // Show result only after everything completes
                                    createResult = result

                                } catch (e: Exception) {
                                    val message = e.message ?: "Unknown error"
                                    if (isUserCancellation(message)) {
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
                        enabled = username.isNotBlank() && DemoState.kit != null
                    ) {
                        Text("Create Wallet")
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
                                value = createResult!!.credentialId,
                                clipboard = clipboard,
                                snackbarHostState = snackbarHostState,
                                scope = scope
                            )

                            ResultField(
                                label = "Contract Address",
                                value = createResult!!.contractId,
                                clipboard = clipboard,
                                snackbarHostState = snackbarHostState,
                                scope = scope
                            )

                            if (createResult!!.transactionHash != null) {
                                ResultField(
                                    label = "Transaction Hash",
                                    value = createResult!!.transactionHash!!,
                                    clipboard = clipboard,
                                    snackbarHostState = snackbarHostState,
                                    scope = scope
                                )
                            }

                            if (balance != null || demoTokenBalance != null) {
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(
                                        text = "Balance",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    if (balance != null) {
                                        Text(
                                            text = "$balance XLM",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontFamily = FontFamily.Monospace,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                    if (demoTokenBalance != null) {
                                        Text(
                                            text = "$demoTokenBalance DEMO",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontFamily = FontFamily.Monospace,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                }
                            }
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
    private fun ResultField(
        label: String,
        value: String,
        clipboard: com.soneso.smartdemo.platform.Clipboard,
        snackbarHostState: SnackbarHostState,
        scope: kotlinx.coroutines.CoroutineScope
    ) {
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
                modifier = Modifier.clickable {
                    scope.launch {
                        clipboard.copyToClipboard(value)
                        snackbarHostState.showSnackbar("$label copied to clipboard")
                    }
                }
            )
            Text(
                text = "Tap to copy",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f)
            )
        }
    }
}
