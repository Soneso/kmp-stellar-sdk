package com.soneso.smartdemo.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
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
import com.soneso.smartdemo.state.ActivityLogState
import com.soneso.smartdemo.state.DemoState
import com.soneso.stellar.sdk.AssetTypeNative
import com.soneso.stellar.sdk.Network
import com.soneso.stellar.sdk.rpc.SorobanServer
import com.soneso.stellar.sdk.smartaccount.oz.AuthenticatePasskeyResult
import com.soneso.stellar.sdk.smartaccount.oz.OZWalletOperations
import com.soneso.stellar.sdk.smartaccount.oz.StoredCredential
import kotlinx.coroutines.launch

class WalletConnectionScreen : Screen {
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val scope = rememberCoroutineScope()

        var isConnecting by remember { mutableStateOf(false) }
        var isAuthenticating by remember { mutableStateOf(false) }
        var manualExpanded by remember { mutableStateOf(false) }
        var pendingExpanded by remember { mutableStateOf(true) }

        var authenticatedCredentialId by remember { mutableStateOf<String?>(null) }

        val pendingCredentials = remember { mutableStateListOf<StoredCredential>() }
        var isLoadingPending by remember { mutableStateOf(true) }

        // Load pending credentials on screen entry
        LaunchedEffect(Unit) {
            try {
                val pending = DemoState.kit?.credentialManager?.getPendingCredentials() ?: emptyList()
                pendingCredentials.clear()
                pendingCredentials.addAll(pending)
            } catch (e: Exception) {
                ActivityLogState.error("Failed to load pending credentials: ${e.message}")
            } finally {
                isLoadingPending = false
            }
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Connect Wallet") },
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
                // 1. Auto-Connect Section
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Quick Connect",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "Connect with automatic session restoration or passkey authentication.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Button(
                            onClick = {
                                scope.launch {
                                    isConnecting = true
                                    try {
                                        val kit = DemoState.kit
                                        if (kit == null) {
                                            ActivityLogState.error("SDK not initialized")
                                            return@launch
                                        }

                                        // Use prompt = true so WebAuthn is triggered if no session
                                        val result = kit.walletOperations.connectWallet(
                                            OZWalletOperations.ConnectWalletOptions(prompt = true)
                                        )

                                        if (result == null) {
                                            // Should not happen with prompt = true, but handle defensively
                                            ActivityLogState.info("No wallet session found")
                                            return@launch
                                        }

                                        if (result.restoredFromSession) {
                                            ActivityLogState.success("Restored from saved session")
                                        } else {
                                            ActivityLogState.success("Connected via passkey authentication")
                                        }

                                        DemoState.setConnected(true, result.contractId, result.credentialId)

                                        // Fetch balance
                                        try {
                                            val server = SorobanServer(DemoConfig.RPC_URL)
                                            val balanceResult = server.getSACBalance(
                                                result.contractId,
                                                AssetTypeNative,
                                                Network(DemoConfig.NETWORK_PASSPHRASE)
                                            )
                                            val xlmBalance = if (balanceResult.balanceEntry != null) {
                                                com.soneso.smartdemo.util.formatStroopsAsXlm(balanceResult.balanceEntry!!.amount)
                                            } else "0.0"
                                            DemoState.updateBalance(xlmBalance)
                                        } catch (e: Exception) {
                                            ActivityLogState.error("Failed to fetch balance: ${e.message}")
                                        }

                                        navigator.pop()
                                    } catch (e: Exception) {
                                        val message = e.message ?: "Unknown error"
                                        if (message.contains("user", ignoreCase = true) &&
                                            (message.contains("cancel", ignoreCase = true) ||
                                             message.contains("abort", ignoreCase = true))) {
                                            ActivityLogState.info("Passkey authentication cancelled")
                                        } else {
                                            ActivityLogState.error("Connection failed: $message")
                                        }
                                    } finally {
                                        isConnecting = false
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !isConnecting && DemoState.kit != null
                        ) {
                            Text(if (isConnecting) "Connecting..." else "Connect")
                        }
                    }
                }

                // 2. Manual Connect Section
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { manualExpanded = !manualExpanded },
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Manual Connect (Advanced)",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Icon(
                                imageVector = if (manualExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = if (manualExpanded) "Collapse" else "Expand"
                            )
                        }

                        if (manualExpanded) {
                            Spacer(modifier = Modifier.height(12.dp))

                            Text(
                                text = "Authenticate first, then discover and select from multiple contracts.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            // Step 1: Authenticate
                            Button(
                                onClick = {
                                    scope.launch {
                                        isAuthenticating = true
                                        try {
                                            val kit = DemoState.kit
                                            if (kit == null) {
                                                ActivityLogState.error("SDK not initialized")
                                                return@launch
                                            }

                                            val authResult: AuthenticatePasskeyResult = kit.walletOperations.authenticatePasskey()
                                            authenticatedCredentialId = authResult.credentialId
                                            ActivityLogState.success("Authenticated with credential: ${authResult.credentialId.take(16)}...")

                                            // Connect with credential ID - SDK will handle indexer lookup internally.
                                            // Throws WalletException.NotFound if no contract is found for this credential.
                                            ActivityLogState.info("Looking up contract for credential...")
                                            val result = kit.walletOperations.connectWallet(
                                                OZWalletOperations.ConnectWalletOptions(credentialId = authResult.credentialId)
                                            )

                                            if (result == null) {
                                                ActivityLogState.error("Failed to resolve contract for credential")
                                                return@launch
                                            }

                                            ActivityLogState.success("Connected to contract: ${result.contractId}")
                                            DemoState.setConnected(true, result.contractId, result.credentialId)
                                            fetchBalance(result.contractId)
                                            navigator.pop()
                                        } catch (e: Exception) {
                                            val message = e.message ?: "Unknown error"
                                            if (message.contains("user", ignoreCase = true) &&
                                                (message.contains("cancel", ignoreCase = true) ||
                                                 message.contains("abort", ignoreCase = true))) {
                                                ActivityLogState.info("Passkey authentication cancelled")
                                            } else {
                                                ActivityLogState.error("Authentication failed: $message")
                                            }
                                        } finally {
                                            isAuthenticating = false
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !isAuthenticating && DemoState.kit != null
                            ) {
                                Text(if (isAuthenticating) "Authenticating..." else "Authenticate Passkey")
                            }

                            // Show authenticated credential
                            if (authenticatedCredentialId != null) {
                                Spacer(modifier = Modifier.height(12.dp))

                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surface
                                    )
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Text(
                                            text = "Authenticated Credential:",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = authenticatedCredentialId!!.take(24) + "...",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // 3. Pending Credentials Section
                if (pendingCredentials.isNotEmpty() || isLoadingPending) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { pendingExpanded = !pendingExpanded },
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Pending Deployments (${pendingCredentials.count()})",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                                Icon(
                                    imageVector = if (pendingExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = if (pendingExpanded) "Collapse" else "Expand",
                                    tint = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }

                            if (pendingExpanded) {
                                Spacer(modifier = Modifier.height(12.dp))

                                if (isLoadingPending) {
                                    Text(
                                        text = "Loading pending credentials...",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                } else if (pendingCredentials.isEmpty()) {
                                    Text(
                                        text = "No pending deployments",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                } else {
                                    pendingCredentials.forEach { credential ->
                                        Card(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 4.dp),
                                            colors = CardDefaults.cardColors(
                                                containerColor = MaterialTheme.colorScheme.surface
                                            )
                                        ) {
                                            Column(modifier = Modifier.padding(12.dp)) {
                                                Text(
                                                    text = "Credential ID:",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                Text(
                                                    text = "${credential.credentialId.take(12)}...${credential.credentialId.takeLast(8)}",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    fontFamily = FontFamily.Monospace
                                                )

                                                Spacer(modifier = Modifier.height(4.dp))

                                                Text(
                                                    text = "Contract ID:",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                Text(
                                                    text = credential.contractId?.let { "${it.take(12)}...${it.takeLast(12)}" } ?: "Unknown",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    fontFamily = FontFamily.Monospace
                                                )

                                                Spacer(modifier = Modifier.height(8.dp))

                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    Button(
                                                        onClick = {
                                                            scope.launch {
                                                                try {
                                                                    val kit = DemoState.kit
                                                                    if (kit == null) {
                                                                        ActivityLogState.error("SDK not initialized")
                                                                        return@launch
                                                                    }

                                                                    ActivityLogState.info("Retrying deployment for ${credential.credentialId.take(16)}...")
                                                                    // connectWallet throws WalletException.NotFound if the credential cannot be resolved.
                                                                    val result = kit.walletOperations.connectWallet(
                                                                        OZWalletOperations.ConnectWalletOptions(
                                                                            credentialId = credential.credentialId,
                                                                            contractId = credential.contractId
                                                                        )
                                                                    )

                                                                    if (result == null) {
                                                                        ActivityLogState.error("Failed to connect with pending credential")
                                                                        return@launch
                                                                    }

                                                                    ActivityLogState.success("Successfully deployed contract")
                                                                    DemoState.setConnected(true, result.contractId, result.credentialId)
                                                                    fetchBalance(result.contractId)

                                                                    // Refresh pending list
                                                                    val updated = kit.credentialManager.getPendingCredentials()
                                                                    pendingCredentials.clear()
                                                                    pendingCredentials.addAll(updated)

                                                                    navigator.pop()
                                                                } catch (e: Exception) {
                                                                    ActivityLogState.error("Retry failed: ${e.message}")
                                                                }
                                                            }
                                                        },
                                                        modifier = Modifier.weight(1f)
                                                    ) {
                                                        Text("Retry Deploy")
                                                    }

                                                    OutlinedButton(
                                                        onClick = {
                                                            scope.launch {
                                                                try {
                                                                    val kit = DemoState.kit
                                                                    if (kit == null) {
                                                                        ActivityLogState.error("SDK not initialized")
                                                                        return@launch
                                                                    }

                                                                    kit.credentialManager.deleteCredential(credential.credentialId)
                                                                    ActivityLogState.info("Deleted pending credential")

                                                                    // Refresh pending list
                                                                    val updated = kit.credentialManager.getPendingCredentials()
                                                                    pendingCredentials.clear()
                                                                    pendingCredentials.addAll(updated)
                                                                } catch (e: Exception) {
                                                                    ActivityLogState.error("Delete failed: ${e.message}")
                                                                }
                                                            }
                                                        },
                                                        modifier = Modifier.weight(1f)
                                                    ) {
                                                        Text("Delete")
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

    }

    private suspend fun fetchBalance(contractId: String) {
        try {
            val server = SorobanServer(DemoConfig.RPC_URL)
            val balanceResult = server.getSACBalance(
                contractId,
                AssetTypeNative,
                Network(DemoConfig.NETWORK_PASSPHRASE)
            )
            val xlmBalance = if (balanceResult.balanceEntry != null) {
                (balanceResult.balanceEntry!!.amount.toDouble() / DemoConfig.STROOPS_PER_XLM).toString()
            } else "0.00"
            DemoState.updateBalance(xlmBalance)
            ActivityLogState.success("Balance: $xlmBalance XLM")
        } catch (e: Exception) {
            ActivityLogState.error("Failed to fetch balance: ${e.message}")
        }
    }
}
