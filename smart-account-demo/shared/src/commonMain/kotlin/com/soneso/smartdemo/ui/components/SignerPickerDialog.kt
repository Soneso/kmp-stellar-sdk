package com.soneso.smartdemo.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.soneso.stellar.sdk.KeyPair
import com.soneso.stellar.sdk.smartaccount.core.DelegatedSigner
import com.soneso.stellar.sdk.smartaccount.core.ExternalSigner
import com.soneso.smartdemo.config.DemoConfig
import com.soneso.smartdemo.state.DemoState
import com.soneso.smartdemo.util.formatSignerForDisplay
import com.soneso.smartdemo.util.truncateAddress
import com.soneso.stellar.sdk.smartaccount.core.SmartAccountBuilders
import com.soneso.stellar.sdk.smartaccount.core.SmartAccountSigner
import com.soneso.smartdemo.wallet.WalletConnector
import com.soneso.smartdemo.wallet.WalletConnection
import kotlinx.coroutines.launch

/**
 * Data class holding a validated KeyPair for a delegated signer whose secret key
 * has been entered and verified by the user.
 *
 * @property keyPair The derived KeyPair from the entered secret key
 */
data class DelegatedSignerKeyPair(
    val keyPair: KeyPair
)

/**
 * Represents how a delegated signer has been authorized for signing.
 */
private sealed class DelegatedSignerAuth {
    /** Authorized via a manually-entered secret key. */
    data class SecretKey(val keyPair: KeyPair) : DelegatedSignerAuth()

    /** Authorized via an externally connected wallet (e.g., Freighter). */
    data class Wallet(val connection: WalletConnection) : DelegatedSignerAuth()
}

/**
 * A dialog component for selecting signers in multi-signature transactions.
 *
 * Displays categorized lists of available signers (passkey, delegated, Ed25519)
 * and allows the user to select which ones to use for authorization. For delegated
 * signers, the user can enter a Stellar secret key or connect an external wallet
 * (where available) to enable signing.
 *
 * @param isOpen Whether the dialog is visible
 * @param onDismiss Called when the dialog is dismissed without confirming
 * @param availableSigners List of signers from context rules that could authorize the transaction
 * @param activeCredentialId The Base64URL credential ID of the currently connected passkey, or null
 * @param title Dialog title text
 * @param description Explanatory text shown below the title
 * @param onConfirm Called with the list of selected signers when the user confirms.
 *        The second parameter is a map of delegated signer addresses to their KeyPairs
 *        for signers where a secret key was entered.
 */
@Composable
fun SignerPickerDialog(
    isOpen: Boolean,
    onDismiss: () -> Unit,
    availableSigners: List<SmartAccountSigner>,
    activeCredentialId: String?,
    title: String = "Select Signers",
    description: String = "Choose which signers to use for this transaction.",
    onConfirm: (selectedSigners: List<SmartAccountSigner>, delegatedKeyPairs: Map<String, KeyPair>) -> Unit
) {
    if (!isOpen) return

    val scope = rememberCoroutineScope()
    val walletConnector = DemoState.walletConnector

    // Selection state: track selected signer unique keys
    val selectedSignerKeys = remember { mutableStateMapOf<String, Boolean>() }

    // Delegated signer auth state — covers both secret-key and wallet-connected signers
    val delegatedSignerAuth = remember { mutableStateMapOf<String, DelegatedSignerAuth>() }

    // Secret key input state
    var secretKeyInputAddress by remember { mutableStateOf<String?>(null) }
    var secretKeyValue by remember { mutableStateOf("") }
    var secretKeyError by remember { mutableStateOf<String?>(null) }
    var isValidatingKey by remember { mutableStateOf(false) }
    var secretKeyVisible by remember { mutableStateOf(false) }

    // Wallet connection state — address of the signer currently being connected (loading state)
    var walletConnectingAddress by remember { mutableStateOf<String?>(null) }

    // Per-signer wallet error messages
    val walletErrors = remember { mutableStateMapOf<String, String>() }

    // Track which signer address has an active wallet connection
    // (only one delegated signer may have a wallet connection at a time)
    val walletConnectedAddress: String? = delegatedSignerAuth.entries
        .firstOrNull { it.value is DelegatedSignerAuth.Wallet }
        ?.key

    // Categorize signers
    val passkeySigners = remember(availableSigners) {
        availableSigners.filter { signer ->
            signer is ExternalSigner &&
                SmartAccountBuilders.getCredentialIdFromSigner(signer) != null
        }
    }

    val delegatedSigners = remember(availableSigners) {
        availableSigners.filterIsInstance<DelegatedSigner>()
    }

    val ed25519Signers = remember(availableSigners) {
        availableSigners.filter { signer ->
            signer is ExternalSigner &&
                SmartAccountBuilders.getCredentialIdFromSigner(signer) == null
        }
    }

    // Auto-select active passkey and signers with stored auth on open
    LaunchedEffect(isOpen, activeCredentialId, availableSigners) {
        if (isOpen) {
            // Auto-select the active passkey
            if (activeCredentialId != null) {
                val activePasskey = passkeySigners.find { signer ->
                    SmartAccountBuilders.signerMatchesCredentialId(signer, activeCredentialId)
                }
                if (activePasskey != null) {
                    selectedSignerKeys[activePasskey.uniqueKey] = true
                }
            }

            // Auto-select delegated signers that already have auth stored
            for ((address, _) in delegatedSignerAuth) {
                val matchingSigner = delegatedSigners.find { it.address == address }
                if (matchingSigner != null) {
                    selectedSignerKeys[matchingSigner.uniqueKey] = true
                }
            }
        }
    }

    // Clean up state when the dialog closes. DisposableEffect fires onDispose when
    // isOpen changes in either direction. When opening (false->true), delegatedSignerAuth
    // is empty so the disconnect is a no-op. When closing (true->false), any active
    // wallet session is disconnected. Backgrounding does not trigger disposal.
    DisposableEffect(isOpen) {
        onDispose {
            secretKeyValue = ""
            secretKeyError = null
            secretKeyInputAddress = null
            secretKeyVisible = false
            walletErrors.clear()

            val addressToDisconnect = delegatedSignerAuth.entries
                .firstOrNull { it.value is DelegatedSignerAuth.Wallet }
                ?.key
            if (addressToDisconnect != null && walletConnector != null) {
                scope.launch {
                    try {
                        walletConnector.disconnect(addressToDisconnect)
                    } catch (_: Throwable) {
                        // Best-effort disconnect — ignore errors on cleanup
                    }
                }
            }
            delegatedSignerAuth.clear()
            selectedSignerKeys.clear()
        }
    }

    val selectedCount = selectedSignerKeys.count { it.value }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 32.dp)
                .heightIn(max = 600.dp),
            shape = MaterialTheme.shapes.large,
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                // Header
                DialogHeader(
                    title = title,
                    onClose = onDismiss
                )

                // Scrollable content
                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp)
                ) {
                    // Description
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    // Passkey Signers Section
                    if (passkeySigners.isNotEmpty()) {
                        SignerSectionHeader(label = "Passkey Signers")
                        passkeySigners.forEach { signer ->
                            val credIdStr = SmartAccountBuilders.getCredentialIdStringFromSigner(signer)
                            val isActive = credIdStr != null && credIdStr == activeCredentialId
                            val isSelected = selectedSignerKeys[signer.uniqueKey] == true

                            PasskeySignerRow(
                                signer = signer,
                                credentialIdHint = credIdStr?.take(16) ?: "Unknown",
                                isActive = isActive,
                                isSelected = isSelected,
                                onToggle = {
                                    selectedSignerKeys[signer.uniqueKey] =
                                        !(selectedSignerKeys[signer.uniqueKey] ?: false)
                                }
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    // Stellar Account Signers (Delegated) Section
                    if (delegatedSigners.isNotEmpty()) {
                        SignerSectionHeader(label = "Stellar Account Signers")
                        delegatedSigners.forEach { signer ->
                            val signerAuth = delegatedSignerAuth[signer.address]
                            val hasKeyPair = signerAuth is DelegatedSignerAuth.SecretKey
                            val isWalletConnected = signerAuth is DelegatedSignerAuth.Wallet
                            val isSelected = selectedSignerKeys[signer.uniqueKey] == true
                            val isEnteringKey = secretKeyInputAddress == signer.address
                            val isConnecting = walletConnectingAddress == signer.address
                            val isAnotherWalletConnected = walletConnectedAddress != null &&
                                walletConnectedAddress != signer.address

                            DelegatedSignerRow(
                                signer = signer,
                                hasKeyPair = hasKeyPair,
                                isSelected = isSelected,
                                onToggle = {
                                    if (hasKeyPair || isWalletConnected) {
                                        selectedSignerKeys[signer.uniqueKey] =
                                            !(selectedSignerKeys[signer.uniqueKey] ?: false)
                                    }
                                },
                                isEnteringKey = isEnteringKey,
                                onEnterKeyClick = {
                                    secretKeyInputAddress = signer.address
                                    secretKeyValue = ""
                                    secretKeyError = null
                                    secretKeyVisible = false
                                },
                                walletAvailable = walletConnector != null,
                                isWalletConnected = isWalletConnected,
                                isAnotherWalletConnected = isAnotherWalletConnected,
                                walletConnecting = isConnecting,
                                walletError = walletErrors[signer.address],
                                onConnectWalletClick = {
                                    walletErrors.remove(signer.address)
                                    walletConnectingAddress = signer.address
                                    scope.launch {
                                        try {
                                            val connector = walletConnector ?: return@launch
                                            val connection = connector.connect()
                                            if (connection == null) {
                                                // User cancelled — nothing to do
                                                return@launch
                                            }

                                            // Verify address matches
                                            if (connection.address != signer.address) {
                                                walletErrors[signer.address] =
                                                    "Connected wallet address does not match this signer. " +
                                                        "Expected: ${signer.address}, got: ${connection.address}"
                                                return@launch
                                            }

                                            // Verify network
                                            val networkPassphrase = walletConnector.getNetworkPassphrase()
                                            if (networkPassphrase != null &&
                                                networkPassphrase != DemoConfig.NETWORK_PASSPHRASE
                                            ) {
                                                walletErrors[signer.address] =
                                                    "Wallet is connected to a different network."
                                                // Disconnect the mismatched session
                                                try {
                                                    walletConnector.disconnect(connection.address)
                                                } catch (_: Throwable) {
                                                    // Best-effort
                                                }
                                                return@launch
                                            }

                                            // Success
                                            delegatedSignerAuth[signer.address] =
                                                DelegatedSignerAuth.Wallet(connection)
                                            selectedSignerKeys[signer.uniqueKey] = true
                                        } catch (e: Throwable) {
                                            walletErrors[signer.address] =
                                                "Connection failed: ${e.message ?: "Unknown error"}"
                                        } finally {
                                            walletConnectingAddress = null
                                        }
                                    }
                                },
                                onDisconnectWalletClick = {
                                    val auth = delegatedSignerAuth[signer.address]
                                    delegatedSignerAuth.remove(signer.address)
                                    selectedSignerKeys[signer.uniqueKey] = false
                                    walletErrors.remove(signer.address)
                                    if (auth is DelegatedSignerAuth.Wallet && walletConnector != null) {
                                        scope.launch {
                                            try {
                                                walletConnector.disconnect(signer.address)
                                            } catch (_: Throwable) {
                                                // Best-effort
                                            }
                                        }
                                    }
                                }
                            )

                            // Secret key input form
                            if (isEnteringKey) {
                                SecretKeyInputForm(
                                    address = signer.address,
                                    secretKeyValue = secretKeyValue,
                                    onSecretKeyChange = { value ->
                                        secretKeyValue = value
                                        secretKeyError = null
                                    },
                                    secretKeyError = secretKeyError,
                                    isValidating = isValidatingKey,
                                    secretKeyVisible = secretKeyVisible,
                                    onToggleVisibility = { secretKeyVisible = !secretKeyVisible },
                                    onCancel = {
                                        secretKeyInputAddress = null
                                        secretKeyValue = ""
                                        secretKeyError = null
                                        secretKeyVisible = false
                                    },
                                    onSubmit = {
                                        scope.launch {
                                            isValidatingKey = true
                                            secretKeyError = null
                                            try {
                                                val trimmed = secretKeyValue.trim()
                                                if (!trimmed.startsWith("S") || trimmed.length != 56) {
                                                    secretKeyError = "Invalid secret key. Must start with S and be 56 characters."
                                                    return@launch
                                                }

                                                val keyPair = KeyPair.fromSecretSeed(trimmed)
                                                val derivedAddress = keyPair.getAccountId()

                                                if (derivedAddress != signer.address) {
                                                    secretKeyError = "Secret key does not match this address."
                                                    return@launch
                                                }

                                                // Store the auth and auto-select the signer
                                                delegatedSignerAuth[signer.address] =
                                                    DelegatedSignerAuth.SecretKey(keyPair)
                                                selectedSignerKeys[signer.uniqueKey] = true
                                                secretKeyInputAddress = null
                                                secretKeyValue = ""
                                                secretKeyVisible = false
                                            } catch (e: Throwable) {
                                                secretKeyError = "Invalid secret key: ${e.message}"
                                            } finally {
                                                isValidatingKey = false
                                            }
                                        }
                                    }
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    // Ed25519 External Signers Section
                    if (ed25519Signers.isNotEmpty()) {
                        SignerSectionHeader(label = "Ed25519 Signers")
                        ed25519Signers.forEach { signer ->
                            val external = signer as ExternalSigner
                            val isSelected = selectedSignerKeys[signer.uniqueKey] == true
                            val displayInfo = formatSignerForDisplay(signer)

                            Ed25519SignerRow(
                                verifierAddress = external.verifierAddress,
                                displayInfo = displayInfo.display,
                                isSelected = isSelected,
                                onToggle = {
                                    selectedSignerKeys[signer.uniqueKey] =
                                        !(selectedSignerKeys[signer.uniqueKey] ?: false)
                                }
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    // Empty state
                    if (availableSigners.isEmpty()) {
                        Text(
                            text = "No signers available for this context.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 24.dp)
                        )
                    }
                }

                // Footer
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                DialogFooter(
                    selectedCount = selectedCount,
                    onCancel = onDismiss,
                    onConfirm = {
                        val selected = availableSigners.filter { signer ->
                            selectedSignerKeys[signer.uniqueKey] == true
                        }
                        // Build the delegated keypairs map from secret-key-authorized signers only.
                        // Wallet-authorized signers are handled separately by the caller via
                        // WalletConnector.signAuthEntry and do not need a local KeyPair.
                        val keyPairs = delegatedSignerAuth
                            .filterValues { it is DelegatedSignerAuth.SecretKey }
                            .mapValues { (_, auth) -> (auth as DelegatedSignerAuth.SecretKey).keyPair }
                        onConfirm(selected, keyPairs)
                    }
                )
            }
        }
    }
}

// ============================================================================
// Dialog Subcomponents
// ============================================================================

@Composable
private fun DialogHeader(
    title: String,
    onClose: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        IconButton(onClick = onClose) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Close",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
}

@Composable
private fun DialogFooter(
    selectedCount: Int,
    onCancel: () -> Unit,
    onConfirm: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextButton(onClick = onCancel) {
            Text("Cancel")
        }
        Spacer(modifier = Modifier.width(8.dp))
        Button(
            onClick = onConfirm,
            enabled = selectedCount > 0
        ) {
            Text("Confirm ($selectedCount selected)")
        }
    }
}

@Composable
private fun SignerSectionHeader(label: String) {
    Text(
        text = label,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(bottom = 8.dp)
    )
}

// ============================================================================
// Signer Row Components
// ============================================================================

@Composable
private fun PasskeySignerRow(
    signer: SmartAccountSigner,
    credentialIdHint: String,
    isActive: Boolean,
    isSelected: Boolean,
    onToggle: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .clickable(onClick = onToggle),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = isSelected,
                onCheckedChange = { onToggle() }
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Passkey",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (isActive) {
                        Surface(
                            color = Color(0xFF4CAF50),
                            shape = MaterialTheme.shapes.small
                        ) {
                            Text(
                                text = "Active",
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = credentialIdHint + if (credentialIdHint.length >= 16) "..." else "",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Surface(
                color = MaterialTheme.colorScheme.secondary,
                shape = MaterialTheme.shapes.small
            ) {
                Text(
                    text = "WebAuthn",
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSecondary
                )
            }
        }
    }
}

/**
 * Row for a delegated (Stellar account) signer.
 *
 * Supports two authorization modes: entering a secret key directly, or connecting
 * an external wallet (e.g., Freighter). The wallet option is hidden when
 * [walletAvailable] is false (e.g., macOS).
 */
@Composable
private fun DelegatedSignerRow(
    signer: DelegatedSigner,
    hasKeyPair: Boolean,
    isSelected: Boolean,
    onToggle: () -> Unit,
    isEnteringKey: Boolean,
    onEnterKeyClick: () -> Unit,
    walletAvailable: Boolean,
    isWalletConnected: Boolean,
    isAnotherWalletConnected: Boolean,
    walletConnecting: Boolean,
    walletError: String?,
    onConnectWalletClick: () -> Unit,
    onDisconnectWalletClick: () -> Unit
) {
    val isAuthorized = hasKeyPair || isWalletConnected
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = if (isEnteringKey) 0.dp else 8.dp)
            .clickable(enabled = isAuthorized, onClick = onToggle),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected && isAuthorized) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            }
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onToggle() },
                    enabled = isAuthorized
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = truncateAddress(signer.address, 6),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    when {
                        hasKeyPair -> Text(
                            text = "Ready to sign",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF4CAF50)
                        )
                        isWalletConnected -> Text(
                            text = "Freighter - Ready to sign",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF4CAF50)
                        )
                        else -> Text(
                            text = "Enter secret key or connect wallet to enable signing",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Badge on the right
                when {
                    hasKeyPair -> {
                        Surface(
                            color = Color(0xFF4CAF50),
                            shape = MaterialTheme.shapes.small
                        ) {
                            Text(
                                text = "Verified",
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White
                            )
                        }
                    }
                    isWalletConnected -> {
                        Surface(
                            color = Color(0xFF1565C0),
                            shape = MaterialTheme.shapes.small
                        ) {
                            Text(
                                text = "Freighter",
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White
                            )
                        }
                    }
                }
            }

            // Action buttons row — shown when no auth is present and not in key-entry mode
            if (!isAuthorized && !isEnteringKey && !walletConnecting) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onEnterKeyClick,
                        modifier = Modifier.height(32.dp),
                        contentPadding = ButtonDefaults.ButtonWithIconContentPadding
                    ) {
                        Icon(
                            imageVector = Icons.Default.Key,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Enter Key",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }

                    if (walletAvailable) {
                        Column {
                            OutlinedButton(
                                onClick = onConnectWalletClick,
                                enabled = !isAnotherWalletConnected,
                                modifier = Modifier.height(32.dp),
                                contentPadding = ButtonDefaults.ButtonWithIconContentPadding
                            ) {
                                Text(
                                    text = "Connect Wallet",
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                            Text(
                                text = "Freighter only",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                modifier = Modifier.padding(start = 4.dp, top = 2.dp)
                            )
                        }
                    }
                }
            }

            // Connecting / loading state
            if (walletConnecting) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onEnterKeyClick,
                        modifier = Modifier.height(32.dp),
                        contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                        enabled = false
                    ) {
                        Icon(
                            imageVector = Icons.Default.Key,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Enter Key",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                    OutlinedButton(
                        onClick = {},
                        enabled = false,
                        modifier = Modifier.height(32.dp),
                        contentPadding = ButtonDefaults.ButtonWithIconContentPadding
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Connecting...",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }

            // Disconnect button for wallet-connected signers
            if (isWalletConnected) {
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(
                    onClick = onDisconnectWalletClick,
                    contentPadding = ButtonDefaults.TextButtonContentPadding
                ) {
                    Text(
                        text = "Disconnect",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            // Error message
            if (walletError != null) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Error: $walletError",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun SecretKeyInputForm(
    address: String,
    secretKeyValue: String,
    onSecretKeyChange: (String) -> Unit,
    secretKeyError: String?,
    isValidating: Boolean,
    secretKeyVisible: Boolean,
    onToggleVisibility: () -> Unit,
    onCancel: () -> Unit,
    onSubmit: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, bottom = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Header row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Secret key for ${truncateAddress(address, 6)}",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                IconButton(
                    onClick = onCancel,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Cancel",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Secret key input field
            OutlinedTextField(
                value = secretKeyValue,
                onValueChange = onSecretKeyChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("S...") },
                singleLine = true,
                visualTransformation = if (secretKeyVisible) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
                trailingIcon = {
                    IconButton(onClick = onToggleVisibility) {
                        Icon(
                            imageVector = if (secretKeyVisible) {
                                Icons.Default.VisibilityOff
                            } else {
                                Icons.Default.Visibility
                            },
                            contentDescription = if (secretKeyVisible) "Hide" else "Show",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                },
                isError = secretKeyError != null,
                supportingText = if (secretKeyError != null) {
                    { Text(secretKeyError) }
                } else null,
                textStyle = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace
                )
            )

            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onCancel) {
                    Text("Cancel", style = MaterialTheme.typography.labelMedium)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = onSubmit,
                    enabled = !isValidating && secretKeyValue.trim().isNotEmpty()
                ) {
                    if (isValidating) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                    Text(
                        text = if (isValidating) "Verifying..." else "Verify",
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }

            // Warning text
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                )
            ) {
                Text(
                    text = "Your secret key is stored in memory only and cleared when this dialog closes.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(8.dp)
                )
            }
        }
    }
}

@Composable
private fun Ed25519SignerRow(
    verifierAddress: String,
    displayInfo: String,
    isSelected: Boolean,
    onToggle: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .clickable(onClick = onToggle),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = isSelected,
                onCheckedChange = { onToggle() }
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = displayInfo,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Verifier: ${truncateAddress(verifierAddress, 6)}",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Surface(
                color = MaterialTheme.colorScheme.secondary,
                shape = MaterialTheme.shapes.small
            ) {
                Text(
                    text = "Ed25519",
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSecondary
                )
            }
        }
    }
}
