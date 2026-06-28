package com.soneso.smartdemo.ui.screens

/**
 * Main screen: wallet status dashboard, navigation, and activity log.
 * SDK initialization and balance refresh are handled by MainScreenFlow.
 *
 * When a wallet is connected but not yet deployed on-chain, a warning card is shown
 * with a "Deploy Now" button. Navigation buttons for features that require an on-chain
 * contract (Transfer, Context Rules, Account Signers) are disabled until deployment.
 */

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import com.soneso.smartdemo.flows.deployPendingAndProvision
import com.soneso.smartdemo.flows.disconnect
import com.soneso.smartdemo.flows.initializeKit
import com.soneso.smartdemo.flows.refreshBalances
import com.soneso.smartdemo.platform.Clipboard
import com.soneso.smartdemo.platform.getClipboard
import com.soneso.smartdemo.state.ActivityLogState
import com.soneso.smartdemo.state.DemoState
import com.soneso.smartdemo.state.LogEntry
import com.soneso.smartdemo.state.LogLevel
import kotlin.time.ExperimentalTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

class MainScreen : Screen {

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val scope = rememberCoroutineScope()
        val snackbarHostState = remember { SnackbarHostState() }
        val clipboard = remember { getClipboard() }

        // Controls the Refresh button loading state
        var isRefreshingBalance by remember { mutableStateOf(false) }

        // Controls the Deploy Now button in the undeployed warning card
        var isDeploying by remember { mutableStateOf(false) }
        var deployErrorMessage by remember { mutableStateOf<String?>(null) }

        // Auto-initialize the SDK when platform providers become available
        LaunchedEffect(DemoState.webauthnProvider, DemoState.storage) {
            if (DemoState.kit == null && DemoState.webauthnProvider != null) {
                try {
                    initializeKit(DemoState.webauthnProvider, DemoState.storage)
                } catch (e: Exception) {
                    ActivityLogState.error("Failed to initialize SDK: ${e.message}")
                }
            }
        }

        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text("Stellar Smart Account Demo")
                            Text(
                                text = "Testnet",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    },
                    actions = {
                        InboxBell(
                            pendingCount = DemoState.pendingRequestCount,
                            onClick = { navigator.push(ApprovalInboxScreen()) }
                        )
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        titleContentColor = MaterialTheme.colorScheme.onPrimary,
                        actionIconContentColor = MaterialTheme.colorScheme.onPrimary
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
                // 1. Wallet Status Section
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
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = DemoState.contractId?.let { "${it.take(12)}...${it.takeLast(12)}" } ?: "Unknown",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.weight(1f)
                                )
                                if (DemoState.contractId != null) {
                                    OutlinedButton(
                                        onClick = {
                                            scope.launch {
                                                clipboard.copyToClipboard(DemoState.contractId!!)
                                                snackbarHostState.showSnackbar("Contract address copied")
                                            }
                                        },
                                        modifier = Modifier.padding(start = 8.dp)
                                    ) {
                                        Text("Copy", style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Credential ID
                            Text(
                                text = "Credential ID:",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = DemoState.credentialId ?: "Unknown",
                                style = MaterialTheme.typography.bodyMedium,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            // Undeployed warning card
                            if (!DemoState.isDeployed) {
                                UndeployedWarningCard(
                                    isDeploying = isDeploying,
                                    deployErrorMessage = deployErrorMessage,
                                    onDeploy = {
                                        val credId = DemoState.credentialId ?: return@UndeployedWarningCard
                                        scope.launch {
                                            isDeploying = true
                                            deployErrorMessage = null
                                            try {
                                                val result = deployPendingAndProvision(credId)
                                                if (!result.success) {
                                                    deployErrorMessage = "Deployment failed: ${result.error}"
                                                    ActivityLogState.error("Deployment failed: ${result.error}")
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

                                Spacer(modifier = Modifier.height(8.dp))
                            }

                            // Balances (only show when deployed)
                            if (DemoState.isDeployed) {
                                Text(
                                    text = "Balance:",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "${DemoState.balance ?: "Loading..."} XLM",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                        Text(
                                            text = "${DemoState.demoTokenBalance ?: "0.0"} DEMO",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            scope.launch {
                                                isRefreshingBalance = true
                                                try {
                                                    refreshBalances()
                                                } catch (e: Throwable) {
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
                                        modifier = Modifier.weight(1f),
                                        enabled = DemoState.isDeployed,
                                        colors = if (DemoState.isDeployed) {
                                            ButtonDefaults.buttonColors()
                                        } else {
                                            ButtonDefaults.buttonColors(
                                                disabledContainerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                                                disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                            )
                                        }
                                    ) {
                                        Text("Context Rules")
                                    }
                                    Button(
                                        onClick = { navigator.push(TransferScreen()) },
                                        modifier = Modifier.weight(1f),
                                        enabled = DemoState.isDeployed,
                                        colors = if (DemoState.isDeployed) {
                                            ButtonDefaults.buttonColors()
                                        } else {
                                            ButtonDefaults.buttonColors(
                                                disabledContainerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                                                disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                            )
                                        }
                                    ) {
                                        Text("Transfer")
                                    }
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Button(
                                        onClick = { navigator.push(ApproveScreen()) },
                                        modifier = Modifier.weight(1f),
                                        enabled = DemoState.isDeployed,
                                        colors = if (DemoState.isDeployed) {
                                            ButtonDefaults.buttonColors()
                                        } else {
                                            ButtonDefaults.buttonColors(
                                                disabledContainerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                                                disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                            )
                                        }
                                    ) {
                                        Text("Approve")
                                    }
                                    Button(
                                        onClick = { navigator.push(KnownSignersScreen()) },
                                        modifier = Modifier.weight(1f),
                                        enabled = DemoState.isDeployed,
                                        colors = if (DemoState.isDeployed) {
                                            ButtonDefaults.buttonColors()
                                        } else {
                                            ButtonDefaults.buttonColors(
                                                disabledContainerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                                                disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                            )
                                        }
                                    ) {
                                        Text("Account Signers")
                                    }
                                }
                                OutlinedButton(
                                    onClick = {
                                        scope.launch {
                                            try {
                                                disconnect()
                                            } catch (e: Throwable) {
                                                ActivityLogState.error("Disconnect failed: ${e.message}")
                                            }
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    enabled = !isDeploying
                                ) {
                                    Text("Disconnect")
                                }
                            }
                        }
                    }
                }

                // 2. Activity Log Section
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
                                    LogEntryRow(
                                        entry = entry,
                                        clipboard = clipboard,
                                        snackbarHostState = snackbarHostState,
                                        scope = scope
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Badged inbox bell shown in the top app bar. The badge displays the number of pending
     * agent escalations for the connected account; tapping opens the approval inbox.
     */
    @Composable
    private fun InboxBell(pendingCount: Int, onClick: () -> Unit) {
        IconButton(onClick = onClick) {
            BadgedBox(
                badge = {
                    if (pendingCount > 0) {
                        Badge {
                            Text(if (pendingCount > 99) "99+" else pendingCount.toString())
                        }
                    }
                }
            ) {
                Icon(
                    imageVector = Icons.Default.Notifications,
                    contentDescription = "Approval inbox"
                )
            }
        }
    }

    @Composable
    private fun UndeployedWarningCard(
        isDeploying: Boolean,
        deployErrorMessage: String?,
        onDeploy: () -> Unit
    ) {
        val warningContainerColor = Color(0xFFFFF3E0) // Orange 50
        val warningContentColor = Color(0xFFE65100) // Orange 900

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = warningContainerColor
            )
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Wallet Not Deployed",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = warningContentColor
                )
                Text(
                    text = "Your passkey is registered but the smart account contract has not been deployed to the network. Deploy it to start using your wallet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = warningContentColor
                )

                if (deployErrorMessage != null) {
                    Text(
                        text = deployErrorMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                if (isDeploying) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = warningContentColor
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Deploying contract...",
                            style = MaterialTheme.typography.bodySmall,
                            color = warningContentColor
                        )
                    }
                }

                Button(
                    onClick = onDeploy,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isDeploying,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = warningContentColor,
                        contentColor = Color.White
                    )
                ) {
                    Text(if (isDeploying) "Deploying..." else "Deploy Now")
                }
            }
        }
    }

    @OptIn(ExperimentalTime::class)
    @Composable
    private fun LogEntryRow(
        entry: LogEntry,
        clipboard: Clipboard,
        snackbarHostState: SnackbarHostState,
        scope: CoroutineScope
    ) {
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
                .clickable {
                    scope.launch {
                        clipboard.copyToClipboard(entry.message)
                        snackbarHostState.showSnackbar("Log message copied to clipboard")
                    }
                }
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
