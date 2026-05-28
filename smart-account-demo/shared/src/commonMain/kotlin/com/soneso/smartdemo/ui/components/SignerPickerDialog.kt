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
import com.soneso.smartdemo.flows.Ed25519SignerIdentity
import com.soneso.smartdemo.flows.VerificationResult
import com.soneso.smartdemo.flows.verifyDelegatedSecret
import com.soneso.smartdemo.flows.verifyEd25519Seed
import com.soneso.smartdemo.state.DemoState
import com.soneso.smartdemo.util.formatSignerForDisplay
import com.soneso.smartdemo.util.toHexString
import com.soneso.smartdemo.util.truncateAddress
import com.soneso.stellar.sdk.smartaccount.core.SmartAccountBuilders
import com.soneso.stellar.sdk.smartaccount.core.SmartAccountConstants
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
 * (where available) to enable signing. For Ed25519 signers, the user enters the
 * 64-character hex representation of the 32-byte secret seed, which is verified
 * by deriving the keypair and checking that the public key matches.
 *
 * @param isOpen Whether the dialog is visible
 * @param onDismiss Called when the dialog is dismissed without confirming
 * @param availableSigners List of signers from context rules that could authorize the transaction
 * @param activeCredentialId The Base64URL credential ID of the currently connected passkey, or null
 * @param title Dialog title text
 * @param description Explanatory text shown below the title
 * @param onConfirm Called with the selected signers when the user confirms. The second parameter
 *        is a map of delegated signer addresses to their KeyPairs for signers where a secret
 *        key was entered. The third parameter is a map of Ed25519 signer identities to their
 *        raw 32-byte seeds, collected from the local verification cache.
 */
@Composable
fun SignerPickerDialog(
    isOpen: Boolean,
    onDismiss: () -> Unit,
    availableSigners: List<SmartAccountSigner>,
    activeCredentialId: String?,
    title: String = "Select Signers",
    description: String = "Choose which signers to use for this transaction.",
    onConfirm: (
        selectedSigners: List<SmartAccountSigner>,
        delegatedKeyPairs: Map<String, KeyPair>,
        ed25519Secrets: Map<Ed25519SignerIdentity, ByteArray>
    ) -> Unit
) {
    if (!isOpen) return

    val scope = rememberCoroutineScope()
    val walletConnector = DemoState.walletConnector

    // Selection state: track selected signer unique keys
    val selectedSignerKeys = remember { mutableStateMapOf<String, Boolean>() }

    // Delegated signer auth state — covers both secret-key and wallet-connected signers
    val delegatedSignerAuth = remember { mutableStateMapOf<String, DelegatedSignerAuth>() }

    // Ed25519 signer local cache — verified seeds stored here until Confirm is called.
    // Seeds are NEVER registered with any adapter here; that happens in the flow's
    // submission path after onConfirm is called.
    val ed25519VerifiedSeeds = remember { mutableStateMapOf<Ed25519SignerIdentity, ByteArray>() }

    // Secret key input state (shared between delegated and Ed25519 signer rows)
    var secretKeyInputAddress by remember { mutableStateOf<String?>(null) }
    var secretKeyValue by remember { mutableStateOf("") }
    var secretKeyError by remember { mutableStateOf<String?>(null) }
    var isValidatingKey by remember { mutableStateOf(false) }
    var secretKeyVisible by remember { mutableStateOf(false) }

    // Ed25519 hex-secret input state — tracks which verifier address is currently open
    var ed25519InputVerifierAddress by remember { mutableStateOf<String?>(null) }
    var ed25519HexValue by remember { mutableStateOf("") }
    var ed25519HexError by remember { mutableStateOf<String?>(null) }
    var isValidatingEd25519 by remember { mutableStateOf(false) }
    var ed25519SecretVisible by remember { mutableStateOf(false) }

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
                SmartAccountBuilders.getCredentialIdFromSigner(signer) == null &&
                (signer as ExternalSigner).keyData.size == SmartAccountConstants.ED25519_PUBLIC_KEY_SIZE
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
    // wallet session is disconnected, and all local caches are cleared.
    DisposableEffect(isOpen) {
        onDispose {
            secretKeyValue = ""
            secretKeyError = null
            secretKeyInputAddress = null
            secretKeyVisible = false
            ed25519HexValue = ""
            ed25519HexError = null
            ed25519InputVerifierAddress = null
            ed25519SecretVisible = false
            walletErrors.clear()
            // Drop Ed25519 seeds from the local cache — nothing was registered anywhere.
            ed25519VerifiedSeeds.clear()

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
                                                when (val result = verifyDelegatedSecret(
                                                    secret = secretKeyValue,
                                                    expectedAccountId = signer.address
                                                )) {
                                                    is VerificationResult.Failure -> {
                                                        secretKeyError = result.errorMessage
                                                    }
                                                    is VerificationResult.Success -> {
                                                        delegatedSignerAuth[signer.address] =
                                                            DelegatedSignerAuth.SecretKey(result.keypair)
                                                        selectedSignerKeys[signer.uniqueKey] = true
                                                        secretKeyInputAddress = null
                                                        secretKeyValue = ""
                                                        secretKeyVisible = false
                                                    }
                                                }
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
                            val identity = Ed25519SignerIdentity(
                                verifierAddress = external.verifierAddress,
                                publicKey = external.keyData
                            )
                            val isVerified = ed25519VerifiedSeeds.containsKey(identity)
                            val isSelected = selectedSignerKeys[signer.uniqueKey] == true
                            val isEnteringHex = ed25519InputVerifierAddress == external.verifierAddress
                            val displayInfo = formatSignerForDisplay(signer)

                            Ed25519SignerRow(
                                verifierAddress = external.verifierAddress,
                                displayInfo = displayInfo.display,
                                isVerified = isVerified,
                                isSelected = isSelected,
                                onToggle = {
                                    if (isVerified) {
                                        selectedSignerKeys[signer.uniqueKey] =
                                            !(selectedSignerKeys[signer.uniqueKey] ?: false)
                                    }
                                },
                                isEnteringHex = isEnteringHex,
                                onEnterKeyClick = {
                                    ed25519InputVerifierAddress = external.verifierAddress
                                    ed25519HexValue = ""
                                    ed25519HexError = null
                                    ed25519SecretVisible = false
                                }
                            )

                            // Hex-secret input form — shared SecretKeyInputForm parameterised for Ed25519
                            if (isEnteringHex) {
                                Ed25519HexInputForm(
                                    publicKey = external.keyData,
                                    hexValue = ed25519HexValue,
                                    onHexChange = { value ->
                                        ed25519HexValue = value
                                        ed25519HexError = null
                                    },
                                    hexError = ed25519HexError,
                                    isValidating = isValidatingEd25519,
                                    secretVisible = ed25519SecretVisible,
                                    onToggleVisibility = { ed25519SecretVisible = !ed25519SecretVisible },
                                    onCancel = {
                                        ed25519InputVerifierAddress = null
                                        ed25519HexValue = ""
                                        ed25519HexError = null
                                        ed25519SecretVisible = false
                                    },
                                    onSubmit = {
                                        scope.launch {
                                            isValidatingEd25519 = true
                                            ed25519HexError = null
                                            try {
                                                when (val result = verifyEd25519Seed(
                                                    hex = ed25519HexValue,
                                                    expectedPublicKey = external.keyData
                                                )) {
                                                    is VerificationResult.Failure -> {
                                                        ed25519HexError = result.errorMessage
                                                    }
                                                    is VerificationResult.Success -> {
                                                        val seedBytes = result.seedBytes
                                                            ?: return@launch
                                                        ed25519VerifiedSeeds[identity] = seedBytes
                                                        selectedSignerKeys[signer.uniqueKey] = true
                                                        ed25519InputVerifierAddress = null
                                                        ed25519HexValue = ""
                                                        ed25519SecretVisible = false
                                                    }
                                                }
                                            } finally {
                                                isValidatingEd25519 = false
                                            }
                                        }
                                    }
                                )
                            }
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
                        // Pass the Ed25519 verified seeds snapshot. The picker does NOT register
                        // these with any adapter — that is the flow's responsibility after onConfirm.
                        val ed25519Snapshot = ed25519VerifiedSeeds.toMap()
                        onConfirm(selected, keyPairs, ed25519Snapshot)
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
 * Shared card frame for a signer row.
 *
 * Renders the toggle/checkbox/identifier/status/badge layout and delegates the
 * action-button area (below the main row) to [actionContent]. Both delegated and
 * Ed25519 signer rows are thin wrappers that supply their type-specific inputs here.
 *
 * @param isEnabled Whether the checkbox and card tap are enabled.
 * @param isSelected Whether the checkbox is checked.
 * @param isEnteringKey Whether the row is currently in key-entry mode (affects bottom padding).
 * @param onToggle Called when the user taps the row or the checkbox.
 * @param primaryText Main identifier text shown in monospace (e.g. truncated address or display info).
 * @param secondaryText Optional secondary label below [primaryText].
 * @param statusText Status line shown below the secondary text; null hides it.
 * @param statusColor Color for [statusText].
 * @param badgeContent Optional composable for the badge shown on the trailing edge.
 * @param actionContent Optional composable for action buttons shown below the main row.
 */
@Composable
private fun SignerRow(
    isEnabled: Boolean,
    isSelected: Boolean,
    isEnteringKey: Boolean,
    onToggle: () -> Unit,
    primaryText: String,
    secondaryText: String? = null,
    statusText: String,
    statusColor: androidx.compose.ui.graphics.Color,
    badgeContent: (@Composable () -> Unit)? = null,
    actionContent: (@Composable () -> Unit)? = null,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = if (isEnteringKey) 0.dp else 8.dp)
            .clickable(enabled = isEnabled, onClick = onToggle),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected && isEnabled) {
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
                    enabled = isEnabled
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = primaryText,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (secondaryText != null) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = secondaryText,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.bodySmall,
                        color = statusColor
                    )
                }
                badgeContent?.invoke()
            }
            actionContent?.invoke()
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
    val statusText = when {
        hasKeyPair -> "Ready to sign"
        isWalletConnected -> "Freighter - Ready to sign"
        else -> "Enter secret key or connect wallet to enable signing"
    }
    val statusColor = if (isAuthorized) Color(0xFF4CAF50)
    else MaterialTheme.colorScheme.onSurfaceVariant

    SignerRow(
        isEnabled = isAuthorized,
        isSelected = isSelected,
        isEnteringKey = isEnteringKey,
        onToggle = onToggle,
        primaryText = truncateAddress(signer.address, 6),
        statusText = statusText,
        statusColor = statusColor,
        badgeContent = {
            when {
                hasKeyPair -> Surface(
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
                isWalletConnected -> Surface(
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
        },
        actionContent = {
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
                        OutlinedButton(
                            onClick = onConnectWalletClick,
                            enabled = !isAnotherWalletConnected,
                            modifier = Modifier.height(32.dp),
                            contentPadding = ButtonDefaults.ButtonWithIconContentPadding
                        ) {
                            Text(
                                text = "Connect Freighter",
                                style = MaterialTheme.typography.labelSmall
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
    )
}

/**
 * Shared card composable for collecting and verifying a signer secret.
 *
 * Renders a Card containing a header row with a title and close button, a password-style
 * OutlinedTextField with a show/hide toggle, an action-button row (Cancel / Verify), and a
 * warning footer. The caller supplies all text strings and callbacks; this composable owns
 * no state.
 *
 * @param headerText Text shown in the header row, typically including the truncated address.
 * @param placeholder Placeholder shown inside the empty text field.
 * @param inputValue Current text field content.
 * @param onInputChange Called on every keystroke with the new raw value.
 * @param isVisible Whether the field content is shown in plain text.
 * @param onToggleVisibility Called when the user taps the eye icon.
 * @param validationError Non-null error message displayed below the field; null hides it.
 * @param isValidating When true the Verify button shows a spinner and is disabled.
 * @param warningText Text shown in the amber warning footer card.
 * @param onVerify Called when the user taps Verify (submission CTA).
 * @param onCancel Called when the user taps Cancel or the close icon.
 */
@Composable
private fun SignerSecretInputCard(
    headerText: String,
    placeholder: String,
    inputValue: String,
    onInputChange: (String) -> Unit,
    isVisible: Boolean,
    onToggleVisibility: () -> Unit,
    validationError: String?,
    isValidating: Boolean,
    warningText: String,
    onVerify: () -> Unit,
    onCancel: () -> Unit
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
                    text = headerText,
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

            // Secret input field
            OutlinedTextField(
                value = inputValue,
                onValueChange = onInputChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(placeholder) },
                singleLine = true,
                visualTransformation = if (isVisible) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
                trailingIcon = {
                    IconButton(onClick = onToggleVisibility) {
                        Icon(
                            imageVector = if (isVisible) {
                                Icons.Default.VisibilityOff
                            } else {
                                Icons.Default.Visibility
                            },
                            contentDescription = if (isVisible) "Hide" else "Show",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                },
                isError = validationError != null,
                supportingText = if (validationError != null) {
                    { Text(validationError) }
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
                    onClick = onVerify,
                    enabled = !isValidating && inputValue.trim().isNotEmpty()
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

            // Warning footer
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                )
            ) {
                Text(
                    text = warningText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(8.dp)
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
    SignerSecretInputCard(
        headerText = "Secret key for ${truncateAddress(address, 6)}",
        placeholder = "S...",
        inputValue = secretKeyValue,
        onInputChange = onSecretKeyChange,
        isVisible = secretKeyVisible,
        onToggleVisibility = onToggleVisibility,
        validationError = secretKeyError,
        isValidating = isValidating,
        warningText = "Your secret key is stored in memory only and cleared when this dialog closes.",
        onVerify = onSubmit,
        onCancel = onCancel
    )
}

@Composable
private fun Ed25519HexInputForm(
    publicKey: ByteArray,
    hexValue: String,
    onHexChange: (String) -> Unit,
    hexError: String?,
    isValidating: Boolean,
    secretVisible: Boolean,
    onToggleVisibility: () -> Unit,
    onCancel: () -> Unit,
    onSubmit: () -> Unit
) {
    SignerSecretInputCard(
        headerText = "Ed25519 secret for ${publicKey.toHexString().take(8)}...",
        placeholder = "64-character hex (32-byte seed)",
        inputValue = hexValue,
        onInputChange = onHexChange,
        isVisible = secretVisible,
        onToggleVisibility = onToggleVisibility,
        validationError = hexError,
        isValidating = isValidating,
        warningText = "Your secret seed is stored in memory only and cleared after signing.",
        onVerify = onSubmit,
        onCancel = onCancel
    )
}

/**
 * Row for an Ed25519 external signer.
 *
 * The user enters a 64-character hex seed to enable signing. Wraps [SignerRow]
 * with Ed25519-specific labels and the "Enter Secret" action button.
 */
@Composable
private fun Ed25519SignerRow(
    verifierAddress: String,
    displayInfo: String,
    isVerified: Boolean,
    isSelected: Boolean,
    onToggle: () -> Unit,
    isEnteringHex: Boolean,
    onEnterKeyClick: () -> Unit
) {
    val statusText = if (isVerified) "Ready to sign" else "Enter secret seed to enable signing"
    val statusColor = if (isVerified) Color(0xFF4CAF50)
    else MaterialTheme.colorScheme.onSurfaceVariant

    SignerRow(
        isEnabled = isVerified,
        isSelected = isSelected,
        isEnteringKey = isEnteringHex,
        onToggle = onToggle,
        primaryText = displayInfo,
        secondaryText = "Verifier: ${truncateAddress(verifierAddress, 6)}",
        statusText = statusText,
        statusColor = statusColor,
        badgeContent = {
            if (isVerified) {
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
            } else {
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
        },
        actionContent = if (!isVerified && !isEnteringHex) {
            {
                Spacer(modifier = Modifier.height(8.dp))
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
                        text = "Enter Secret",
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        } else null
    )
}
