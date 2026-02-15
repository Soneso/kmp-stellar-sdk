package com.soneso.smartdemo.ui.screens

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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.soneso.smartdemo.state.ActivityLogState
import com.soneso.smartdemo.state.DemoState
import com.soneso.stellar.sdk.smartaccount.oz.CredentialDeploymentStatus
import com.soneso.stellar.sdk.smartaccount.oz.StoredCredential
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import kotlin.time.ExperimentalTime

class KnownSignersScreen : Screen {

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val scope = rememberCoroutineScope()

        var isLoading by remember { mutableStateOf(true) }
        var errorMessage by remember { mutableStateOf<String?>(null) }
        val walletCredentials = remember { mutableStateListOf<StoredCredential>() }
        val pendingCredentials = remember { mutableStateListOf<StoredCredential>() }

        fun loadCredentials() {
            scope.launch {
                isLoading = true
                errorMessage = null
                try {
                    val credentialManager = DemoState.kit?.credentialManager
                    if (credentialManager == null) {
                        errorMessage = "Smart Account Kit not initialized"
                        ActivityLogState.error("Kit not initialized")
                        return@launch
                    }

                    val allCredentials = credentialManager.getAllCredentials()
                    val currentContractId = DemoState.contractId

                    walletCredentials.clear()
                    pendingCredentials.clear()

                    for (credential in allCredentials) {
                        when {
                            credential.contractId == currentContractId &&
                                credential.deploymentStatus != CredentialDeploymentStatus.PENDING &&
                                credential.deploymentStatus != CredentialDeploymentStatus.FAILED -> {
                                walletCredentials.add(credential)
                            }
                            credential.deploymentStatus == CredentialDeploymentStatus.PENDING ||
                                credential.deploymentStatus == CredentialDeploymentStatus.FAILED -> {
                                pendingCredentials.add(credential)
                            }
                        }
                    }

                    ActivityLogState.info(
                        "Loaded ${walletCredentials.size} active and ${pendingCredentials.size} pending credentials"
                    )
                } catch (e: Exception) {
                    errorMessage = "Failed to load credentials: ${e.message}"
                    ActivityLogState.error("Failed to load credentials: ${e.message}")
                } finally {
                    isLoading = false
                }
            }
        }

        LaunchedEffect(Unit) {
            loadCredentials()
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Known Signers") },
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
                            text = "Locally Stored Credentials",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Locally stored passkey credentials and their status. " +
                                "These are passkeys stored on your device that can be used to sign transactions.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Refresh Button
                OutlinedButton(
                    onClick = { loadCredentials() },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isLoading
                ) {
                    Text(if (isLoading) "Loading..." else "Refresh")
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

                // Loading State
                if (isLoading && walletCredentials.isEmpty() && pendingCredentials.isEmpty()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(
                                text = "Loading credentials...",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }

                // Active Credentials Section
                if (!isLoading || walletCredentials.isNotEmpty()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Active Credentials",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            if (walletCredentials.isEmpty()) {
                                Text(
                                    text = "No stored credentials. Passkey information is stored in your device's credential manager.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                walletCredentials.forEachIndexed { index, credential ->
                                    if (index > 0) {
                                        HorizontalDivider(
                                            modifier = Modifier.padding(vertical = 12.dp),
                                            color = MaterialTheme.colorScheme.outlineVariant
                                        )
                                    }
                                    ActiveCredentialItem(credential)
                                }
                            }
                        }
                    }
                }

                // Pending Credentials Section
                if (!isLoading && pendingCredentials.isNotEmpty()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Pending Credentials",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            Text(
                                text = "These passkeys were created but wallet deployment is incomplete.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            pendingCredentials.forEachIndexed { index, credential ->
                                if (index > 0) {
                                    HorizontalDivider(
                                        modifier = Modifier.padding(vertical = 12.dp),
                                        color = MaterialTheme.colorScheme.outlineVariant
                                    )
                                }
                                PendingCredentialItem(credential)
                            }
                        }
                    }
                }

                // Empty State (no credentials at all)
                if (!isLoading && walletCredentials.isEmpty() && pendingCredentials.isEmpty() && errorMessage == null) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "No stored credentials",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Create a wallet to register your first passkey credential.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Back Button
                Button(
                    onClick = { navigator.pop() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Go Back")
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    @Composable
    private fun ActiveCredentialItem(credential: StoredCredential) {
        Column(
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Nickname and badges row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = credential.nickname ?: "Unnamed Passkey",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f, fill = false)
                )

                if (credential.isPrimary) {
                    StatusBadge(
                        text = "Primary",
                        color = Color(0xFF2196F3)
                    )
                }

                StatusBadge(
                    text = "Active",
                    color = Color(0xFF4CAF50)
                )
            }

            // Credential ID
            DetailRow(
                label = "Credential ID",
                value = truncateCredentialId(credential.credentialId)
            )

            // Created date
            DetailRow(
                label = "Created",
                value = formatTimestamp(credential.createdAt)
            )

            // Last used date
            if (credential.lastUsedAt != null) {
                DetailRow(
                    label = "Last Used",
                    value = formatTimestamp(credential.lastUsedAt!!)
                )
            }

            // Device type
            if (credential.deviceType != null) {
                val deviceLabel = when (credential.deviceType) {
                    "multiDevice" -> "Synced Passkey"
                    "singleDevice" -> "Device-bound"
                    else -> credential.deviceType!!
                }
                val backedUpSuffix = if (credential.backedUp == true) " (Backed up)" else ""
                DetailRow(
                    label = "Device Type",
                    value = "$deviceLabel$backedUpSuffix"
                )
            } else if (credential.backedUp == true) {
                DetailRow(
                    label = "Backed Up",
                    value = "Yes"
                )
            }
        }
    }

    @Composable
    private fun PendingCredentialItem(credential: StoredCredential) {
        Column(
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Nickname and status badge
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = credential.nickname ?: "Unnamed",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f, fill = false)
                )

                val isFailed = credential.deploymentStatus == CredentialDeploymentStatus.FAILED
                StatusBadge(
                    text = if (isFailed) "Failed" else "Pending",
                    color = if (isFailed) Color(0xFFF44336) else Color(0xFFFF9800)
                )
            }

            // Credential ID
            DetailRow(
                label = "Credential ID",
                value = truncateCredentialId(credential.credentialId)
            )

            // Created date
            DetailRow(
                label = "Created",
                value = formatTimestamp(credential.createdAt)
            )

            // Error message for failed deployments
            if (credential.deploymentError != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Text(
                        text = credential.deploymentError!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }
        }
    }

    @Composable
    private fun StatusBadge(text: String, color: Color) {
        Surface(
            color = color,
            shape = MaterialTheme.shapes.small
        ) {
            Text(
                text = text,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White
            )
        }
    }

    @Composable
    private fun DetailRow(label: String, value: String) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "$label:",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(100.dp)
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace
            )
        }
    }

    private fun truncateCredentialId(credentialId: String): String {
        if (credentialId.length <= 20) return credentialId
        return "${credentialId.take(20)}..."
    }

    @OptIn(ExperimentalTime::class)
    private fun formatTimestamp(epochMillis: Long): String {
        val instant = Instant.fromEpochMilliseconds(epochMillis)
        return instant.toString().substringBefore('T')
    }
}
