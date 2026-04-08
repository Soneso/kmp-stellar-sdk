package com.soneso.smartdemo.ui.screens

/**
 * Wallet creation screen: collects a username, triggers wallet creation via WalletCreationFlow,
 * and displays the resulting credential, contract address, and initial balances.
 *
 * When autoSubmit=false, the passkey is registered but the contract is not deployed on-chain.
 * In this case the screen shows an info card with a "Deploy Now" button instead of the full
 * success card with balances.
 */

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Checkbox
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
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
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.soneso.smartdemo.flows.WalletCreationResult
import com.soneso.smartdemo.flows.createWallet
import com.soneso.smartdemo.flows.deployPendingAndProvision
import com.soneso.smartdemo.platform.Clipboard
import com.soneso.smartdemo.platform.getClipboard
import com.soneso.smartdemo.state.ActivityLogState
import com.soneso.smartdemo.state.DemoState
import com.soneso.smartdemo.util.isUserCancellation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class WalletCreationScreen : Screen {
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val scope = rememberCoroutineScope()
        val snackbarHostState = remember { SnackbarHostState() }
        val clipboard = remember { getClipboard() }

        // Form input state
        var username by remember { mutableStateOf("") }
        var autoSubmit by remember { mutableStateOf(true) }

        // Loading / result state
        var isLoading by remember { mutableStateOf(false) }
        var progressMessage by remember { mutableStateOf("") }
        var errorMessage by remember { mutableStateOf<String?>(null) }
        var infoMessage by remember { mutableStateOf<String?>(null) }
        var createResult by remember { mutableStateOf<WalletCreationResult?>(null) }

        // Deploy-from-creation-screen state (for autoSubmit=false results)
        var isDeploying by remember { mutableStateOf(false) }
        var deployErrorMessage by remember { mutableStateOf<String?>(null) }
        var deployedSuccessfully by remember { mutableStateOf(false) }

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
                    label = { Text("Passkey Name") },
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

                // Auto-submit checkbox
                if (createResult == null && !isLoading) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = autoSubmit,
                            onCheckedChange = { autoSubmit = it }
                        )
                        Text(
                            text = "Auto-deploy after passkey registration",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.clickable { autoSubmit = !autoSubmit }
                        )
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
                                    val result = createWallet(username, autoSubmit) { progressMessage = it }
                                    createResult = result
                                    // If deployed, mark as such for the result card
                                    deployedSuccessfully = DemoState.isDeployed
                                } catch (e: Throwable) {
                                    val message = e.message ?: "Unknown error"
                                    if (isUserCancellation(message)) {
                                        infoMessage = "Passkey registration cancelled by user"
                                        ActivityLogState.info("User cancelled passkey registration")
                                    } else {
                                        errorMessage = "Failed to create wallet: $message\n\n" +
                                            "If a passkey was registered before the failure, " +
                                            "go to Connect Wallet and check Pending Deployments " +
                                            "to retry the deployment."
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
                    val result = createResult!!

                    if (deployedSuccessfully) {
                        // Deployed: show full success card with balances
                        DeployedResultCard(
                            result = result,
                            clipboard = clipboard,
                            snackbarHostState = snackbarHostState,
                            scope = scope
                        )
                    } else {
                        // Not deployed: show passkey-registered card with deploy option
                        UndeployedResultCard(
                            result = result,
                            clipboard = clipboard,
                            snackbarHostState = snackbarHostState,
                            scope = scope,
                            isDeploying = isDeploying,
                            deployErrorMessage = deployErrorMessage,
                            onDeploy = {
                                scope.launch {
                                    isDeploying = true
                                    deployErrorMessage = null
                                    try {
                                        val provisionResult = deployPendingAndProvision(
                                            credentialId = result.credentialId,
                                            onProgress = { progressMessage = it }
                                        )
                                        if (provisionResult.success) {
                                            deployedSuccessfully = true
                                            createResult = result.copy(
                                                xlmBalance = provisionResult.xlmBalance,
                                                demoTokenBalance = provisionResult.demoTokenBalance
                                            )
                                        } else {
                                            deployErrorMessage = "Deployment failed: ${provisionResult.error}"
                                            ActivityLogState.error("Deployment failed: ${provisionResult.error}")
                                        }
                                    } catch (e: Throwable) {
                                        deployErrorMessage = "Deployment failed: ${e.message}"
                                        ActivityLogState.error("Deployment failed: ${e.message}")
                                    } finally {
                                        isDeploying = false
                                    }
                                }
                            }
                        )
                    }

                    // Go to Main Screen Button (disabled during deployment)
                    Button(
                        onClick = { navigator.pop() },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isDeploying
                    ) {
                        Text("Go to Main Screen")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    @Composable
    private fun DeployedResultCard(
        result: WalletCreationResult,
        clipboard: Clipboard,
        snackbarHostState: SnackbarHostState,
        scope: CoroutineScope
    ) {
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
                    value = result.credentialId,
                    clipboard = clipboard,
                    snackbarHostState = snackbarHostState,
                    scope = scope
                )

                ResultField(
                    label = "Contract Address",
                    value = result.contractId,
                    clipboard = clipboard,
                    snackbarHostState = snackbarHostState,
                    scope = scope
                )

                if (result.transactionHash != null) {
                    ResultField(
                        label = "Transaction Hash",
                        value = result.transactionHash,
                        clipboard = clipboard,
                        snackbarHostState = snackbarHostState,
                        scope = scope
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Balance",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "${result.xlmBalance} XLM",
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    if (result.demoTokenBalance != null) {
                        Text(
                            text = "${result.demoTokenBalance} DEMO",
                            style = MaterialTheme.typography.bodyMedium,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
        }
    }

    @Composable
    private fun UndeployedResultCard(
        result: WalletCreationResult,
        clipboard: Clipboard,
        snackbarHostState: SnackbarHostState,
        scope: CoroutineScope,
        isDeploying: Boolean,
        deployErrorMessage: String?,
        onDeploy: () -> Unit
    ) {
        // Warning amber color for undeployed state
        val warningContainerColor = Color(0xFFFFF3E0) // Orange 50
        val warningTextColor = Color(0xFFE65100) // Orange 900

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Passkey Registered",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )

                ResultField(
                    label = "Credential ID",
                    value = result.credentialId,
                    clipboard = clipboard,
                    snackbarHostState = snackbarHostState,
                    scope = scope,
                    textColor = MaterialTheme.colorScheme.onSecondaryContainer
                )

                ResultField(
                    label = "Contract Address (derived)",
                    value = result.contractId,
                    clipboard = clipboard,
                    snackbarHostState = snackbarHostState,
                    scope = scope,
                    textColor = MaterialTheme.colorScheme.onSecondaryContainer
                )

                // Warning message
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = warningContainerColor
                    )
                ) {
                    Text(
                        text = "The wallet contract has not been deployed to the network yet. Deploy it now or later from the Connect Wallet screen.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = warningTextColor,
                        modifier = Modifier.padding(12.dp)
                    )
                }

                // Deploy error
                if (deployErrorMessage != null) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        SelectionContainer {
                            Text(
                                text = deployErrorMessage,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }
                }

                // Deploy progress
                if (isDeploying) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.padding(horizontal = 8.dp))
                        Text(
                            text = "Deploying contract...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }

                // Deploy Now button
                Button(
                    onClick = onDeploy,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isDeploying
                ) {
                    Text(if (isDeploying) "Deploying..." else "Deploy Now")
                }
            }
        }
    }

    @Composable
    private fun ResultField(
        label: String,
        value: String,
        clipboard: Clipboard,
        snackbarHostState: SnackbarHostState,
        scope: CoroutineScope,
        textColor: Color = MaterialTheme.colorScheme.onPrimaryContainer
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = textColor
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace,
                color = textColor,
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
                color = textColor.copy(alpha = 0.6f)
            )
        }
    }
}
