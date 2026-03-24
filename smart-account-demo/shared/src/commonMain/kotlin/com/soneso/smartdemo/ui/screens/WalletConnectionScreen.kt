package com.soneso.smartdemo.ui.screens

/**
 * Wallet connection screen: Quick Connect, Manual Connect, and Pending Deployments.
 * All connection logic is handled by WalletConnectionFlow.
 */

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
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
import androidx.compose.material3.CircularProgressIndicator
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
import com.soneso.smartdemo.flows.deletePendingCredential
import com.soneso.smartdemo.flows.loadPendingCredentials
import com.soneso.smartdemo.flows.manualConnect
import com.soneso.smartdemo.flows.quickConnect
import com.soneso.smartdemo.flows.retryPendingDeploy
import com.soneso.smartdemo.state.ActivityLogState
import com.soneso.smartdemo.state.DemoState
import com.soneso.smartdemo.util.isUserCancellation
import com.soneso.stellar.sdk.smartaccount.oz.StoredCredential
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.launch

class WalletConnectionScreen : Screen {
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val scope = rememberCoroutineScope()

        // Connection loading states
        var isConnecting by remember { mutableStateOf(false) }
        var isAuthenticating by remember { mutableStateOf(false) }

        // UI expand/collapse state for collapsible sections
        var manualExpanded by remember { mutableStateOf(false) }
        var pendingExpanded by remember { mutableStateOf(false) }

        // Authenticated credential ID shown in the manual connect section
        var authenticatedCredentialId by remember { mutableStateOf<String?>(null) }

        // Pending deployments list (StoredCredential is a data type needed for display)
        val pendingCredentials = remember { mutableStateListOf<StoredCredential>() }
        var isLoadingPending by remember { mutableStateOf(true) }

        // Load pending credentials on screen entry
        LaunchedEffect(Unit) {
            try {
                val pending = loadPendingCredentials()
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
                                val handler = CoroutineExceptionHandler { _, throwable ->
                                    val message = throwable.message ?: "Unknown error"
                                    if (isUserCancellation(message)) {
                                        ActivityLogState.info("Passkey authentication cancelled")
                                    } else {
                                        ActivityLogState.error("Connection failed: $message")
                                    }
                                    isConnecting = false
                                }
                                scope.launch(handler) {
                                    isConnecting = true
                                    try {
                                        val result = quickConnect()
                                        if (result != null) {
                                            navigator.pop()
                                        }
                                    } catch (e: Throwable) {
                                        val message = e.message ?: "Unknown error"
                                        if (isUserCancellation(message)) {
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
                            if (isConnecting) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                            }
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

                            // Authenticate + connect in one step via manualConnect()
                            Button(
                                onClick = {
                                    val handler = CoroutineExceptionHandler { _, throwable ->
                                        val message = throwable.message ?: "Unknown error"
                                        if (isUserCancellation(message)) {
                                            ActivityLogState.info("Passkey authentication cancelled")
                                        } else {
                                            ActivityLogState.error("Authentication failed: $message")
                                        }
                                        isAuthenticating = false
                                    }
                                    scope.launch(handler) {
                                        isAuthenticating = true
                                        try {
                                            val result = manualConnect()
                                            if (result != null) {
                                                authenticatedCredentialId = result.credentialId
                                                navigator.pop()
                                            }
                                        } catch (e: Throwable) {
                                            val message = e.message ?: "Unknown error"
                                            if (isUserCancellation(message)) {
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
                                if (isAuthenticating) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.onPrimary
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                }
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
                                                    text = buildString {
                                                        append("${credential.credentialId.take(12)}...${credential.credentialId.takeLast(8)}")
                                                        if (credential.nickname != null) {
                                                            append(" (${credential.nickname})")
                                                        }
                                                    },
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
                                                            val handler = CoroutineExceptionHandler { _, throwable ->
                                                                ActivityLogState.error("Retry failed: ${throwable.message}")
                                                            }
                                                            scope.launch(handler) {
                                                                try {
                                                                    val result = retryPendingDeploy(
                                                                        credential.credentialId,
                                                                        credential.contractId
                                                                    )
                                                                    if (result != null) {
                                                                        // Refresh pending list after successful deploy
                                                                        val updated = loadPendingCredentials()
                                                                        pendingCredentials.clear()
                                                                        pendingCredentials.addAll(updated)
                                                                        navigator.pop()
                                                                    }
                                                                } catch (e: Throwable) {
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
                                                            val handler = CoroutineExceptionHandler { _, throwable ->
                                                                ActivityLogState.error("Delete failed: ${throwable.message}")
                                                            }
                                                            scope.launch(handler) {
                                                                try {
                                                                    deletePendingCredential(credential.credentialId)
                                                                    // Refresh pending list after deletion
                                                                    val updated = loadPendingCredentials()
                                                                    pendingCredentials.clear()
                                                                    pendingCredentials.addAll(updated)
                                                                } catch (e: Throwable) {
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
}
