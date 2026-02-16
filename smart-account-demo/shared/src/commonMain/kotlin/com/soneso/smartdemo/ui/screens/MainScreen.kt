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
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.soneso.smartdemo.config.DemoConfig
import com.soneso.smartdemo.config.KNOWN_POLICIES
import com.soneso.smartdemo.state.ActivityLogState
import com.soneso.smartdemo.state.DemoState
import com.soneso.smartdemo.state.LogLevel
import com.soneso.stellar.sdk.AssetTypeNative
import com.soneso.stellar.sdk.Network
import com.soneso.stellar.sdk.rpc.SorobanServer
import com.soneso.stellar.sdk.smartaccount.oz.InMemoryStorageAdapter
import com.soneso.stellar.sdk.smartaccount.oz.OZSmartAccountConfig
import com.soneso.stellar.sdk.smartaccount.oz.OZSmartAccountKit
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

class MainScreen : Screen {

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val scope = rememberCoroutineScope()

        var configExpanded by remember { mutableStateOf(true) }
        var policiesExpanded by remember { mutableStateOf(false) }
        var isRefreshingBalance by remember { mutableStateOf(false) }

        // Auto-initialize SDK on first launch
        LaunchedEffect(Unit) {
            if (DemoState.kit == null) {
                try {
                    val config = OZSmartAccountConfig(
                        rpcUrl = DemoConfig.RPC_URL,
                        networkPassphrase = DemoConfig.NETWORK_PASSPHRASE,
                        accountWasmHash = DemoState.accountWasmHash,
                        webauthnVerifierAddress = DemoState.webauthnVerifier,
                        relayerUrl = DemoState.relayerUrl.takeIf { it.isNotBlank() },
                        storage = InMemoryStorageAdapter()
                    )
                    val kit = OZSmartAccountKit.create(config)
                    DemoState.setKitInstance(kit)
                    ActivityLogState.success("SDK initialized with default configuration")
                    if (DemoState.relayerUrl.isNotBlank()) {
                        ActivityLogState.info("Relayer fee sponsoring enabled")
                    }
                } catch (e: Exception) {
                    ActivityLogState.error("Failed to initialize SDK: ${e.message}")
                }
            }
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text("Stellar Smart Account Demo")
                            Text(
                                text = "Testnet",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        titleContentColor = MaterialTheme.colorScheme.onPrimary
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
                // 1. Configuration Section
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
                                .clickable { configExpanded = !configExpanded },
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Configuration",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Icon(
                                imageVector = if (configExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = if (configExpanded) "Collapse" else "Expand"
                            )
                        }

                        if (configExpanded) {
                            Spacer(modifier = Modifier.height(12.dp))

                            OutlinedTextField(
                                value = DemoState.accountWasmHash,
                                onValueChange = { DemoState.accountWasmHash = it },
                                label = { Text("Account WASM Hash") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                isError = DemoState.accountWasmHash.length != 64 || !DemoState.accountWasmHash.all { it.isLetterOrDigit() }
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            OutlinedTextField(
                                value = DemoState.webauthnVerifier,
                                onValueChange = { DemoState.webauthnVerifier = it },
                                label = { Text("WebAuthn Verifier") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                isError = !DemoState.webauthnVerifier.startsWith("C") || DemoState.webauthnVerifier.length != 56
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            OutlinedTextField(
                                value = DemoState.ed25519Verifier,
                                onValueChange = { DemoState.ed25519Verifier = it },
                                label = { Text("Ed25519 Verifier") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                isError = !DemoState.ed25519Verifier.startsWith("C") || DemoState.ed25519Verifier.length != 56
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            OutlinedTextField(
                                value = DemoState.relayerUrl,
                                onValueChange = { DemoState.relayerUrl = it },
                                label = { Text("Relayer URL (Optional)") },
                                placeholder = { Text("Optional - for fee sponsoring") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            Button(
                                onClick = {
                                    scope.launch {
                                        try {
                                            // Validate inputs
                                            if (DemoState.accountWasmHash.length != 64 || !DemoState.accountWasmHash.all { it.isLetterOrDigit() }) {
                                                ActivityLogState.error("Invalid WASM hash: must be 64 hex characters")
                                                return@launch
                                            }
                                            if (!DemoState.webauthnVerifier.startsWith("C") || DemoState.webauthnVerifier.length != 56) {
                                                ActivityLogState.error("Invalid WebAuthn verifier: must start with 'C' and be 56 characters")
                                                return@launch
                                            }
                                            if (!DemoState.ed25519Verifier.startsWith("C") || DemoState.ed25519Verifier.length != 56) {
                                                ActivityLogState.error("Invalid Ed25519 verifier: must start with 'C' and be 56 characters")
                                                return@launch
                                            }

                                            val config = OZSmartAccountConfig(
                                                rpcUrl = DemoConfig.RPC_URL,
                                                networkPassphrase = DemoConfig.NETWORK_PASSPHRASE,
                                                accountWasmHash = DemoState.accountWasmHash,
                                                webauthnVerifierAddress = DemoState.webauthnVerifier,
                                                relayerUrl = DemoState.relayerUrl.takeIf { it.isNotBlank() },
                                                storage = InMemoryStorageAdapter()
                                            )
                                            val kit = OZSmartAccountKit.create(config)
                                            DemoState.setKitInstance(kit)
                                            ActivityLogState.success("SDK initialized")
                                            if (DemoState.relayerUrl.isNotBlank()) {
                                                ActivityLogState.info("Relayer fee sponsoring enabled")
                                            }
                                        } catch (e: Exception) {
                                            ActivityLogState.error("Configuration failed: ${e.message}")
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Apply Configuration")
                            }
                        }
                    }
                }

                // 2. Known Policies Section
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
                                .clickable { policiesExpanded = !policiesExpanded },
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Known Policy Contracts",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Icon(
                                imageVector = if (policiesExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = if (policiesExpanded) "Collapse" else "Expand"
                            )
                        }

                        if (policiesExpanded) {
                            Spacer(modifier = Modifier.height(12.dp))

                            KNOWN_POLICIES.forEach { policy ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = when (policy.type) {
                                            "threshold" -> Color(0xFF2196F3).copy(alpha = 0.1f)
                                            "spending_limit" -> Color(0xFF4CAF50).copy(alpha = 0.1f)
                                            "weighted_threshold" -> Color(0xFF9C27B0).copy(alpha = 0.1f)
                                            else -> MaterialTheme.colorScheme.surface
                                        }
                                    )
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Surface(
                                                color = when (policy.type) {
                                                    "threshold" -> Color(0xFF2196F3)
                                                    "spending_limit" -> Color(0xFF4CAF50)
                                                    "weighted_threshold" -> Color(0xFF9C27B0)
                                                    else -> MaterialTheme.colorScheme.primary
                                                },
                                                shape = MaterialTheme.shapes.small
                                            ) {
                                                Text(
                                                    text = policy.type,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = Color.White
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = policy.name,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = policy.description,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "${policy.address.take(8)}...${policy.address.takeLast(8)}",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontFamily = FontFamily.Monospace,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // 3. Wallet Status Section
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Wallet Status",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        if (!DemoState.isConnected) {
                            Text(
                                text = "No wallet connected",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = { navigator.push(WalletCreationScreen()) },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Create Wallet")
                                }
                                Button(
                                    onClick = { navigator.push(WalletConnectionScreen()) },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Connect Wallet")
                                }
                            }
                        } else {
                            // Contract Address
                            Text(
                                text = "Contract Address:",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = DemoState.contractId?.let { "${it.take(12)}...${it.takeLast(12)}" } ?: "Unknown",
                                style = MaterialTheme.typography.bodyMedium,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            // Credential ID
                            Text(
                                text = "Credential ID:",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = DemoState.credentialId?.take(16) ?: "Unknown",
                                style = MaterialTheme.typography.bodyMedium,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            // Balance
                            Text(
                                text = "Balance:",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "${DemoState.balance ?: "Loading..."} XLM",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                OutlinedButton(
                                    onClick = {
                                        scope.launch {
                                            isRefreshingBalance = true
                                            try {
                                                val contractId = DemoState.contractId
                                                if (contractId != null) {
                                                    val server = SorobanServer(DemoConfig.RPC_URL)
                                                    val result = server.getSACBalance(
                                                        contractId,
                                                        AssetTypeNative,
                                                        Network(DemoConfig.NETWORK_PASSPHRASE)
                                                    )
                                                    val xlmBalance = if (result.balanceEntry != null) {
                                                        com.soneso.smartdemo.util.formatStroopsAsXlm(result.balanceEntry!!.amount)
                                                    } else "0.0"
                                                    DemoState.updateBalance(xlmBalance)
                                                    ActivityLogState.success("Balance refreshed: $xlmBalance XLM")
                                                } else {
                                                    ActivityLogState.error("No contract ID available")
                                                }
                                            } catch (e: Exception) {
                                                ActivityLogState.error("Failed to refresh balance: ${e.message}")
                                            } finally {
                                                isRefreshingBalance = false
                                            }
                                        }
                                    },
                                    enabled = !isRefreshingBalance
                                ) {
                                    Text(if (isRefreshingBalance) "Refreshing..." else "Refresh")
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Navigation buttons
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Button(
                                        onClick = { navigator.push(ContextRulesScreen()) },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("Context Rules")
                                    }
                                    Button(
                                        onClick = { navigator.push(TransferScreen()) },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("Transfer")
                                    }
                                }
                                Button(
                                    onClick = { navigator.push(KnownSignersScreen()) },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Known Signers")
                                }
                                OutlinedButton(
                                    onClick = {
                                        scope.launch {
                                            try {
                                                DemoState.kit?.disconnect()
                                                DemoState.setConnected(false)
                                                ActivityLogState.info("Wallet disconnected")
                                            } catch (e: Exception) {
                                                ActivityLogState.error("Disconnect failed: ${e.message}")
                                            }
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Disconnect")
                                }
                            }
                        }
                    }
                }

                // 4. Activity Log Section
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Activity Log (${ActivityLogState.entries.size})",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            OutlinedButton(
                                onClick = { ActivityLogState.clear() },
                                enabled = ActivityLogState.entries.isNotEmpty()
                            ) {
                                Text("Clear")
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        if (ActivityLogState.entries.isEmpty()) {
                            Text(
                                text = "No activity yet",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                ActivityLogState.entries.forEach { entry ->
                                    LogEntryRow(entry)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @OptIn(kotlin.time.ExperimentalTime::class)
    @Composable
    private fun LogEntryRow(entry: com.soneso.smartdemo.state.LogEntry) {
        val timeString = remember(entry.timestamp) {
            val localTime = entry.timestamp.toLocalDateTime(TimeZone.currentSystemDefault())
            val h = localTime.hour.toString().padStart(2, '0')
            val m = localTime.minute.toString().padStart(2, '0')
            val s = localTime.second.toString().padStart(2, '0')
            "$h:$m:$s"
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = timeString,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(70.dp)
            )

            Surface(
                color = when (entry.level) {
                    LogLevel.INFO -> Color(0xFF2196F3)
                    LogLevel.SUCCESS -> Color(0xFF4CAF50)
                    LogLevel.ERROR -> Color(0xFFF44336)
                },
                shape = MaterialTheme.shapes.small
            ) {
                Text(
                    text = when (entry.level) {
                        LogLevel.INFO -> "INFO"
                        LogLevel.SUCCESS -> "OK"
                        LogLevel.ERROR -> "ERR"
                    },
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White
                )
            }

            Text(
                text = entry.message,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f)
            )
        }
    }
}
