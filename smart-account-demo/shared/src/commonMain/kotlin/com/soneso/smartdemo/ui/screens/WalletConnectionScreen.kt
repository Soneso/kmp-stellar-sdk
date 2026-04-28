package com.soneso.smartdemo.ui.screens

/**
 * Wallet connection screen: Auto Connect, Connect via Indexer,
 * Connect with Address, and Pending Deployments.
 * All connection logic is handled by WalletConnectionFlow.
 */

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.soneso.smartdemo.flows.deletePendingCredential
import com.soneso.smartdemo.flows.finalizeConnect
import com.soneso.smartdemo.flows.loadPendingCredentials
import com.soneso.smartdemo.flows.connectWithAddress
import com.soneso.smartdemo.flows.manualConnect
import com.soneso.smartdemo.flows.quickConnect
import com.soneso.smartdemo.flows.retryPendingDeploy
import com.soneso.smartdemo.state.ActivityLogState
import com.soneso.smartdemo.state.DemoState
import com.soneso.smartdemo.ui.components.ContractPickerDialog
import com.soneso.smartdemo.util.isUserCancellation
import com.soneso.smartdemo.util.isValidContractAddress
import com.soneso.stellar.sdk.smartaccount.oz.ConnectWalletResult
import com.soneso.stellar.sdk.smartaccount.oz.StoredCredential
import kotlinx.coroutines.launch

private enum class ConnectionSection { AUTO, INDEXER, ADDRESS }

@Composable
private fun RowScope.LoadingButtonContent(
    text: String,
    isLoading: Boolean,
    indicatorSize: Dp = 20.dp,
    gap: Dp = 8.dp
) {
    if (isLoading) {
        CircularProgressIndicator(
            modifier = Modifier.size(indicatorSize),
            strokeWidth = 2.dp,
            color = MaterialTheme.colorScheme.onPrimary
        )
        Spacer(modifier = Modifier.width(gap))
    }
    Text(text)
}

@Composable
private fun InlineError(message: String?) {
    message?.let { error ->
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = error,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error
        )
    }
}

class WalletConnectionScreen : Screen {
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val scope = rememberCoroutineScope()

        // Tracks which section is currently connecting (null = idle).
        // Prevents concurrent connection attempts and shows the spinner on the active button.
        var activeConnection by remember { mutableStateOf<ConnectionSection?>(null) }

        // Inline error messages for each section
        var autoConnectError by remember { mutableStateOf<String?>(null) }
        var indexerConnectError by remember { mutableStateOf<String?>(null) }
        var addressConnectError by remember { mutableStateOf<String?>(null) }

        // Ambiguous-result picker state. Set when a connect flow returns
        // ConnectWalletResult.Ambiguous (the indexer reports the passkey as
        // a signer on more than one contract). The dialog is rendered as long
        // as this is non-null. The user's selection routes through
        // connectWithAddress to finalize the connect with the chosen contract.
        var ambiguousResult by remember { mutableStateOf<ConnectWalletResult.Ambiguous?>(null) }

        // All sections are always expanded (no collapse/expand toggle).

        // Recovery connect input
        var contractAddressInput by remember { mutableStateOf("") }

        // Pending deployments
        val pendingCredentials = remember { mutableStateListOf<StoredCredential>() }
        var pendingActionInProgress by remember { mutableStateOf(false) }

        fun clearAllErrors() {
            autoConnectError = null
            indexerConnectError = null
            addressConnectError = null
        }

        suspend fun refreshPendingList() {
            val updated = loadPendingCredentials()
            pendingCredentials.clear()
            pendingCredentials.addAll(updated)
        }

        fun launchConnection(
            section: ConnectionSection,
            setError: (String) -> Unit,
            nullResultMessage: String,
            connect: suspend () -> ConnectWalletResult?
        ) {
            clearAllErrors()
            scope.launch {
                activeConnection = section
                try {
                    when (val result = connect()) {
                        null -> setError(nullResultMessage)
                        is ConnectWalletResult.Connected -> navigator.pop()
                        is ConnectWalletResult.Ambiguous -> {
                            // Show the picker; do not pop yet. The picker's
                            // onSelected callback will re-launch the connect
                            // flow with the chosen contract address.
                            ambiguousResult = result
                        }
                    }
                } catch (e: Throwable) {
                    val message = e.message ?: "Unknown error"
                    if (isUserCancellation(message)) {
                        ActivityLogState.info("Passkey authentication cancelled")
                    } else {
                        setError(message)
                        ActivityLogState.error("Connection failed: $message")
                    }
                } finally {
                    activeConnection = null
                }
            }
        }

        // Load pending credentials on screen entry
        LaunchedEffect(Unit) {
            try {
                refreshPendingList()
            } catch (e: Exception) {
                ActivityLogState.error("Failed to load pending credentials: ${e.message}")
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
                // 1. Auto Connect
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Auto Connect",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "Restores the last connected session if available. " +
                                "If no session exists, triggers passkey authentication " +
                                "and tries to resolve the contract address automatically via indexer.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Button(
                            onClick = {
                                launchConnection(
                                    section = ConnectionSection.AUTO,
                                    setError = { autoConnectError = it },
                                    nullResultMessage = "No wallet found for this passkey",
                                    connect = { quickConnect() }
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = activeConnection == null && DemoState.kit != null
                        ) {
                            LoadingButtonContent(
                                text = "Auto Connect",
                                isLoading = activeConnection == ConnectionSection.AUTO
                            )
                        }

                        InlineError(autoConnectError)
                    }
                }

                // 2. Connect via Indexer
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Connect via Indexer",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "Authenticates with a passkey, then uses the indexer service " +
                                "to look up the smart account contract associated with that credential.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Button(
                            onClick = {
                                launchConnection(
                                    section = ConnectionSection.INDEXER,
                                    setError = { indexerConnectError = it },
                                    nullResultMessage = "No contract found for this credential",
                                    connect = { manualConnect() }
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = activeConnection == null && DemoState.kit != null
                        ) {
                            LoadingButtonContent(
                                text = "Connect via Indexer",
                                isLoading = activeConnection == ConnectionSection.INDEXER
                            )
                        }

                        InlineError(indexerConnectError)
                    }
                }

                // 3. Connect with Address
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Connect with Address",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "Connect to a smart account using a known contract address. " +
                                "Authenticates with a passkey that is registered as a signer on the contract. " +
                                "Use this to reconnect with a recovery signer.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        val isAddressValid = isValidContractAddress(contractAddressInput.trim())

                        OutlinedTextField(
                            value = contractAddressInput,
                            onValueChange = {
                                contractAddressInput = it
                                addressConnectError = null
                            },
                            label = { Text("Contract Address") },
                            placeholder = { Text("C...") },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = activeConnection == null,
                            singleLine = true,
                            isError = contractAddressInput.isNotBlank() && !isAddressValid
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Button(
                            onClick = {
                                launchConnection(
                                    section = ConnectionSection.ADDRESS,
                                    setError = { addressConnectError = it },
                                    nullResultMessage = "Could not connect to the provided contract address",
                                    connect = { connectWithAddress(contractAddressInput.trim()) }
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = activeConnection == null &&
                                DemoState.kit != null &&
                                isAddressValid
                        ) {
                            LoadingButtonContent(
                                text = "Connect",
                                isLoading = activeConnection == ConnectionSection.ADDRESS
                            )
                        }

                        InlineError(addressConnectError)
                    }
                }

                // 4. Pending Deployments
                if (pendingCredentials.isNotEmpty()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Pending Deployments (${pendingCredentials.size})",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = "These credentials were registered but contract deployment " +
                                    "may not have completed. Retry the deployment or delete the credential.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )

                            Spacer(modifier = Modifier.height(12.dp))

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
                                                text = "${credential.credentialId.take(12)}...${credential.credentialId.takeLast(8)}" +
                                                    (credential.nickname?.let { " ($it)" } ?: ""),
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
                                                            pendingActionInProgress = true
                                                            try {
                                                                val result = retryPendingDeploy(
                                                                    credential.credentialId,
                                                                )
                                                                refreshPendingList()
                                                                navigator.pop()
                                                            } catch (e: Throwable) {
                                                                ActivityLogState.error("Retry failed: ${e.message}")
                                                            } finally {
                                                                pendingActionInProgress = false
                                                            }
                                                        }
                                                    },
                                                    modifier = Modifier.weight(1f),
                                                    enabled = !pendingActionInProgress
                                                ) {
                                                    if (pendingActionInProgress) {
                                                        CircularProgressIndicator(
                                                            modifier = Modifier.size(16.dp),
                                                            strokeWidth = 2.dp,
                                                            color = MaterialTheme.colorScheme.onPrimary
                                                        )
                                                        Spacer(modifier = Modifier.width(8.dp))
                                                        Text("Deploying...")
                                                    } else {
                                                        Text("Retry Deploy")
                                                    }
                                                }

                                                OutlinedButton(
                                                    onClick = {
                                                        scope.launch {
                                                            pendingActionInProgress = true
                                                            try {
                                                                deletePendingCredential(credential.credentialId)
                                                                refreshPendingList()
                                                            } catch (e: Throwable) {
                                                                ActivityLogState.error("Delete failed: ${e.message}")
                                                            } finally {
                                                                pendingActionInProgress = false
                                                            }
                                                        }
                                                    },
                                                    modifier = Modifier.weight(1f),
                                                    enabled = !pendingActionInProgress
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

        // Picker dialog rendered when a connect flow returned Ambiguous.
        // The user selects a contract; we then call finalizeConnect with the
        // credentialId from the original authentication, skipping a second
        // WebAuthn prompt. (We do not reuse connectWithAddress here because
        // it always re-authenticates -- which would (a) prompt the user
        // twice and (b) let them pick a different passkey on the second
        // prompt, which would be on a different signer set than the chosen
        // contract expects.)
        ContractPickerDialog(
            isOpen = ambiguousResult != null,
            candidates = ambiguousResult?.candidates ?: emptyList(),
            onDismiss = { ambiguousResult = null },
            onSelected = { chosen ->
                val authedCredentialId = ambiguousResult?.credentialId
                ambiguousResult = null
                if (authedCredentialId != null) {
                    launchConnection(
                        section = ConnectionSection.ADDRESS,
                        setError = { addressConnectError = it },
                        nullResultMessage = "Could not connect to the selected wallet",
                        connect = { finalizeConnect(authedCredentialId, chosen) }
                    )
                }
            }
        )
    }
}

