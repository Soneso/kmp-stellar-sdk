package com.soneso.smartdemo.ui.screens

/**
 * Signer management UI section extracted from ContextRuleBuilderScreen.
 *
 * Renders the signer header, current signers list, and add-signer forms
 * (delegated, Ed25519, passkey). All state is received via parameters;
 * no SDK imports or business logic.
 *
 * Supports two modes:
 * - Create mode (isEditing = false): Original behavior using [SmartAccountSigner] list.
 * - Edit mode (isEditing = true): Uses [EditSignerEntry] list with on-chain tracking,
 *   "(on-chain)" badges, connected wallet protection, and existing signer reuse.
 */

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.soneso.smartdemo.config.DemoConfig
import com.soneso.smartdemo.flows.EditSignerEntry
import com.soneso.smartdemo.flows.buildDelegatedSigner
import com.soneso.smartdemo.flows.buildEd25519Signer
import com.soneso.smartdemo.flows.loadAvailablePasskeySigners
import com.soneso.smartdemo.flows.registerPasskeySigner
import com.soneso.smartdemo.state.ActivityLogState
import com.soneso.smartdemo.state.DemoState
import com.soneso.smartdemo.ui.components.SignerIdentityChip
import com.soneso.smartdemo.ui.components.signerChipColor
import com.soneso.smartdemo.util.formatSignerForDisplay
import com.soneso.smartdemo.util.hexToByteArray
import com.soneso.smartdemo.util.isUserCancellation
import com.soneso.smartdemo.util.truncateAddress
import com.soneso.stellar.sdk.smartaccount.core.ExternalSigner
import com.soneso.stellar.sdk.smartaccount.core.SmartAccountBuilders
import com.soneso.stellar.sdk.smartaccount.core.SmartAccountSigner
import com.soneso.stellar.sdk.smartaccount.oz.OZConstants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Composable section for managing signers on a context rule.
 *
 * Displays the signer header card, the current signers list, and the add-signer
 * forms (delegated, Ed25519, passkey). All mutable state is owned by the caller
 * and passed in via parameters and callbacks.
 *
 * In edit mode, additional parameters control on-chain badge display, connected
 * wallet signer protection, and existing signer reuse from other rules.
 *
 * @param isEditing True when editing an existing rule (shows on-chain badges, etc.).
 * @param editSignerEntries Signer entries with on-chain tracking (used in edit mode).
 * @param onEditSignerEntriesChanged Callback when edit entries change (edit mode).
 * @param existingSigners All signers from all on-chain rules for the reuse picker (edit mode).
 * @param connectedCredentialId Credential ID of the connected wallet's passkey (edit mode).
 */
@Composable
fun SignerManagementSection(
    signers: List<SmartAccountSigner>,
    onSignersChanged: (List<SmartAccountSigner>) -> Unit,
    signerAddMode: SignerAddMode,
    onSignerAddModeChanged: (SignerAddMode) -> Unit,
    delegatedAddress: String,
    onDelegatedAddressChanged: (String) -> Unit,
    ed25519PubKeyHex: String,
    onEd25519PubKeyHexChanged: (String) -> Unit,
    isLoadingPasskeys: Boolean,
    onIsLoadingPasskeysChanged: (Boolean) -> Unit,
    isRegistering: Boolean,
    onIsRegisteringChanged: (Boolean) -> Unit,
    availablePasskeys: List<ExternalSigner>,
    onAvailablePasskeysChanged: (List<ExternalSigner>) -> Unit,
    passkeysLoaded: Boolean,
    onPasskeysLoadedChanged: (Boolean) -> Unit,
    newPasskeyName: String,
    onNewPasskeyNameChanged: (String) -> Unit,
    signerWeights: Map<String, String>,
    onSignerWeightsChanged: (Map<String, String>) -> Unit,
    fieldErrors: Map<String, String>,
    onFieldErrorsChanged: (Map<String, String>) -> Unit,
    isSubmitting: Boolean,
    scope: CoroutineScope,
    isEditing: Boolean = false,
    editSignerEntries: List<EditSignerEntry> = emptyList(),
    onEditSignerEntriesChanged: ((List<EditSignerEntry>) -> Unit)? = null,
    existingSigners: List<SmartAccountSigner> = emptyList(),
    connectedCredentialId: String? = null
) {
    // --- Signer Header Card ---
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
                text = "Signers",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Add signers who can authorize operations matching this context. " +
                        "At least one signer is required. Maximum ${OZConstants.MAX_SIGNERS}.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (isEditing) {
                Text(
                    text = "Each signer change requires a separate passkey authentication.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            if (fieldErrors.containsKey("signers")) {
                Text(
                    text = fieldErrors["signers"]!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }

    // --- Current Signers List ---
    if (isEditing) {
        // Edit mode: use EditSignerEntry list with on-chain tracking
        if (editSignerEntries.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Text(
                    text = "No signers added yet. Add at least one signer below.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp)
                )
            }
        } else {
            editSignerEntries.forEachIndexed { index, entry ->
                val isConnectedSigner = isConnectedWalletSigner(entry.signer, connectedCredentialId)
                EditSignerCard(
                    entry = entry,
                    isConnectedSigner = isConnectedSigner,
                    onRemove = {
                        val updated = editSignerEntries.toMutableList().also { it.removeAt(index) }
                        onEditSignerEntriesChanged?.invoke(updated)
                        // Also update the legacy signers list
                        onSignersChanged(updated.map { it.signer })
                        // Remove any weighted threshold entries for removed signers
                        val removedKey = SmartAccountBuilders.getSignerKey(entry.signer)
                        onSignerWeightsChanged(signerWeights - removedKey)
                    },
                    enabled = !isSubmitting && !isConnectedSigner
                )
            }
            Text(
                text = "${editSignerEntries.size} signer(s) added",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    } else {
        // Create mode: use plain SmartAccountSigner list
        if (signers.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Text(
                    text = "No signers added yet. Add at least one signer below.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp)
                )
            }
        } else {
            signers.forEachIndexed { index, signer ->
                SignerCard(
                    signer = signer,
                    onRemove = {
                        val updated = signers.toMutableList().also { it.removeAt(index) }
                        onSignersChanged(updated)
                        // Remove any weighted threshold entries for removed signers
                        val removedKey = SmartAccountBuilders.getSignerKey(signer)
                        onSignerWeightsChanged(signerWeights - removedKey)
                    },
                    enabled = !isSubmitting
                )
            }
            Text(
                text = "${signers.size} signer(s) added",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    // --- Add Signer Section ---
    if (!isSubmitting) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Add Signer",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )

                // Signer mode selector
                SignerModeSelector(
                    selectedMode = signerAddMode,
                    onModeSelected = onSignerAddModeChanged
                )

                // Helper to add a signer in either mode
                val effectiveSigners = if (isEditing) editSignerEntries.map { it.signer } else signers

                when (signerAddMode) {
                    SignerAddMode.DELEGATED -> {
                        OutlinedTextField(
                            value = delegatedAddress,
                            onValueChange = {
                                onDelegatedAddressChanged(it)
                                onFieldErrorsChanged(fieldErrors - "delegatedAddress")
                            },
                            label = { Text("Stellar Address (G-address)") },
                            placeholder = { Text("GABC...") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            isError = fieldErrors.containsKey("delegatedAddress"),
                            supportingText = if (fieldErrors.containsKey("delegatedAddress")) {
                                { Text(fieldErrors["delegatedAddress"]!!) }
                            } else null
                        )

                        if (DemoState.walletConnector != null) {
                            var isGettingAddress by remember { mutableStateOf(false) }

                            OutlinedButton(
                                onClick = {
                                    scope.launch {
                                        isGettingAddress = true
                                        try {
                                            val connection = DemoState.walletConnector?.connect()
                                            if (connection != null) {
                                                onDelegatedAddressChanged(connection.address)
                                                onFieldErrorsChanged(fieldErrors - "delegatedAddress")
                                                // Ephemeral connection — fetch address only, then clean up
                                                try {
                                                    DemoState.walletConnector?.disconnect(connection.address)
                                                } catch (_: Exception) { }
                                            }
                                        } catch (e: Exception) {
                                            onFieldErrorsChanged(
                                                fieldErrors + ("delegatedAddress" to
                                                        "Failed to get address from wallet: ${e.message}")
                                            )
                                        } finally {
                                            isGettingAddress = false
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !isGettingAddress && !isSubmitting
                            ) {
                                if (isGettingAddress) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                }
                                Text("Import from Freighter")
                            }
                        }
                        Button(
                            onClick = {
                                val errors = mutableMapOf<String, String>()
                                val addr = delegatedAddress.trim()
                                if (addr.isEmpty()) {
                                    errors["delegatedAddress"] = "Address is required"
                                } else if (!addr.startsWith("G") || addr.length != 56) {
                                    errors["delegatedAddress"] = "Must be a valid G-address (56 characters)"
                                } else {
                                    val newSigner = try {
                                        buildDelegatedSigner(addr)
                                    } catch (e: Exception) {
                                        errors["delegatedAddress"] = "Invalid address: ${e.message}"
                                        null
                                    }
                                    if (newSigner != null) {
                                        val signerError = validateNewSigner(newSigner, effectiveSigners)
                                        if (signerError != null) {
                                            errors["delegatedAddress"] = signerError
                                        } else {
                                            if (isEditing) {
                                                val newEntry = EditSignerEntry(
                                                    signer = newSigner,
                                                    onChainId = null,
                                                    isOriginal = false
                                                )
                                                onEditSignerEntriesChanged?.invoke(editSignerEntries + newEntry)
                                                onSignersChanged((editSignerEntries + newEntry).map { it.signer })
                                            } else {
                                                onSignersChanged(signers + newSigner)
                                            }
                                            onDelegatedAddressChanged("")
                                            ActivityLogState.info(
                                                "Added delegated signer: ${truncateAddress(addr, 6)}"
                                            )
                                        }
                                    }
                                }
                                onFieldErrorsChanged(fieldErrors - "delegatedAddress" + errors)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = delegatedAddress.isNotBlank()
                        ) {
                            Text("Add Delegated Signer")
                        }
                    }

                    SignerAddMode.ED25519 -> {
                        OutlinedTextField(
                            value = ed25519PubKeyHex,
                            onValueChange = {
                                onEd25519PubKeyHexChanged(it)
                                onFieldErrorsChanged(fieldErrors - "ed25519PublicKey")
                            },
                            label = { Text("Ed25519 Public Key (hex)") },
                            placeholder = { Text("64 hex characters") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            isError = fieldErrors.containsKey("ed25519PublicKey"),
                            supportingText = if (fieldErrors.containsKey("ed25519PublicKey")) {
                                { Text(fieldErrors["ed25519PublicKey"]!!) }
                            } else null
                        )
                        Text(
                            text = "Uses verifier: ${truncateAddress(DemoConfig.ED25519_VERIFIER_ADDRESS, 6)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Button(
                            onClick = {
                                val errors = mutableMapOf<String, String>()
                                val hex = ed25519PubKeyHex.trim().lowercase()
                                if (hex.isEmpty()) {
                                    errors["ed25519PublicKey"] = "Public key is required"
                                } else if (hex.length != 64) {
                                    errors["ed25519PublicKey"] =
                                        "Must be 64 hex characters (32 bytes), got ${hex.length}"
                                } else if (!hex.all { it in '0'..'9' || it in 'a'..'f' }) {
                                    errors["ed25519PublicKey"] = "Invalid hex characters"
                                } else {
                                    val pubKeyBytes = hexToByteArray(hex)
                                    val newSigner = try {
                                        buildEd25519Signer(pubKeyBytes)
                                    } catch (e: Exception) {
                                        errors["ed25519PublicKey"] = "Invalid key: ${e.message}"
                                        null
                                    }
                                    if (newSigner != null) {
                                        val signerError = validateNewSigner(newSigner, effectiveSigners)
                                        if (signerError != null) {
                                            errors["ed25519PublicKey"] = signerError
                                        } else {
                                            if (isEditing) {
                                                val newEntry = EditSignerEntry(
                                                    signer = newSigner,
                                                    onChainId = null,
                                                    isOriginal = false
                                                )
                                                onEditSignerEntriesChanged?.invoke(editSignerEntries + newEntry)
                                                onSignersChanged((editSignerEntries + newEntry).map { it.signer })
                                            } else {
                                                onSignersChanged(signers + newSigner)
                                            }
                                            onEd25519PubKeyHexChanged("")
                                            ActivityLogState.info(
                                                "Added Ed25519 signer: ${hex.take(8)}..."
                                            )
                                        }
                                    }
                                }
                                onFieldErrorsChanged(fieldErrors - "ed25519PublicKey" + errors)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = ed25519PubKeyHex.isNotBlank()
                        ) {
                            Text("Add Ed25519 Signer")
                        }
                    }

                    SignerAddMode.PASSKEY -> {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = Color(0xFF9C27B0).copy(alpha = 0.08f)
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "Passkey (WebAuthn) Signer",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF7B1FA2)
                                )
                                Text(
                                    text = if (isEditing)
                                        "You can reuse a signer from any existing context rule, " +
                                                "or register a new passkey signer for this context rule."
                                    else
                                        "You can reuse an account signer that is already stored in an existing " +
                                                "context rule, or register a new passkey signer for this context rule.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                // In edit mode with existingSigners available, show them directly
                                // without requiring a "Load" button click.
                                if (isEditing && existingSigners.isNotEmpty()) {
                                    Text(
                                        text = "Available signers from existing context rules:",
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                    existingSigners.forEach { existingSigner ->
                                        val displayInfo = formatSignerForDisplay(existingSigner)
                                        val alreadyAdded = isDuplicateSigner(effectiveSigners, existingSigner)
                                        OutlinedButton(
                                            onClick = {
                                                if (!alreadyAdded) {
                                                    val signerError = validateNewSigner(existingSigner, effectiveSigners)
                                                    if (signerError != null) {
                                                        onFieldErrorsChanged(fieldErrors + ("signers" to signerError))
                                                    } else {
                                                        val newEntry = EditSignerEntry(
                                                            signer = existingSigner,
                                                            onChainId = null,
                                                            isOriginal = false
                                                        )
                                                        onEditSignerEntriesChanged?.invoke(editSignerEntries + newEntry)
                                                        onSignersChanged((editSignerEntries + newEntry).map { it.signer })
                                                        onFieldErrorsChanged(fieldErrors - "signers")
                                                        ActivityLogState.success("Added signer: ${displayInfo.display}")
                                                    }
                                                }
                                            },
                                            modifier = Modifier.fillMaxWidth(),
                                            enabled = !alreadyAdded && !isSubmitting
                                        ) {
                                            Text(
                                                if (alreadyAdded) "${displayInfo.display} (already added)"
                                                else "Add: ${displayInfo.display}"
                                            )
                                        }
                                    }

                                    HorizontalDivider(
                                        modifier = Modifier.padding(vertical = 4.dp),
                                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                                    )
                                }

                                // Load passkey signers from on-chain context rules, excluding
                                // the wallet owner's passkey (it's on the Default rule and adding
                                // it to another rule has no effect — Default already authorizes it).
                                // In edit mode, existingSigners are already displayed above, so
                                // this button loads passkey signers not already covered.
                                if (!isEditing) {
                                    val connectedCredentialIdForLoad = DemoState.credentialId
                                    Button(
                                        onClick = {
                                            scope.launch {
                                                onIsLoadingPasskeysChanged(true)
                                                try {
                                                    val loaded = loadAvailablePasskeySigners(
                                                        excludeCredentialId = connectedCredentialIdForLoad
                                                    )
                                                    onAvailablePasskeysChanged(loaded)
                                                    onPasskeysLoadedChanged(true)
                                                    if (loaded.isEmpty()) {
                                                        ActivityLogState.info("No additional passkey signers found")
                                                    }
                                                } catch (e: Throwable) {
                                                    ActivityLogState.error("Failed to load passkeys: ${e.message}")
                                                } finally {
                                                    onIsLoadingPasskeysChanged(false)
                                                }
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        enabled = !isLoadingPasskeys && !isRegistering && !isSubmitting && !passkeysLoaded
                                    ) {
                                        if (isLoadingPasskeys) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(20.dp),
                                                strokeWidth = 2.dp,
                                                color = MaterialTheme.colorScheme.onPrimary
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                        }
                                        Text(if (isLoadingPasskeys) "Loading..." else "Reuse Signer")
                                    }

                                    // Show available passkeys (excluding wallet owner's) as selectable items
                                    if (passkeysLoaded && availablePasskeys.isNotEmpty()) {
                                        Text(
                                            text = "Available signers from existing context rules:",
                                            style = MaterialTheme.typography.labelMedium
                                        )
                                        availablePasskeys.forEach { passkey ->
                                            val displayInfo = formatSignerForDisplay(passkey)
                                            val alreadyAdded = isDuplicateSigner(signers, passkey)
                                            OutlinedButton(
                                                onClick = {
                                                    if (!alreadyAdded) {
                                                        val signerError = validateNewSigner(passkey, signers)
                                                        if (signerError != null) {
                                                            onFieldErrorsChanged(fieldErrors + ("signers" to signerError))
                                                        } else {
                                                            onSignersChanged(signers + passkey)
                                                            onFieldErrorsChanged(fieldErrors - "signers")
                                                            ActivityLogState.success("Added passkey signer: ${displayInfo.display}")
                                                        }
                                                    }
                                                },
                                                modifier = Modifier.fillMaxWidth(),
                                                enabled = !alreadyAdded && !isSubmitting
                                            ) {
                                                Text(
                                                    if (alreadyAdded) "${displayInfo.display} (already added)"
                                                    else "Add: ${displayInfo.display}"
                                                )
                                            }
                                        }
                                    }

                                    if (passkeysLoaded && availablePasskeys.isEmpty()) {
                                        Text(
                                            text = "No existing passkey signers found on this account.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                HorizontalDivider(
                                    modifier = Modifier.padding(vertical = 4.dp),
                                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                                )

                                // Register a new passkey signer via WebAuthn ceremony
                                Text(
                                    text = "Register a new passkey signer for this context rule:",
                                    style = MaterialTheme.typography.labelMedium
                                )
                                OutlinedTextField(
                                    value = newPasskeyName,
                                    onValueChange = onNewPasskeyNameChanged,
                                    label = { Text("Passkey Name") },
                                    placeholder = { Text("e.g., Recovery Key") },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    enabled = !isRegistering && !isSubmitting
                                )
                                Button(
                                    onClick = {
                                        scope.launch {
                                            onIsRegisteringChanged(true)
                                            try {
                                                val newSigner = registerPasskeySigner(newPasskeyName)

                                                val signerError = validateNewSigner(newSigner, effectiveSigners)
                                                if (signerError != null) {
                                                    onFieldErrorsChanged(fieldErrors + ("signers" to signerError))
                                                    ActivityLogState.info(signerError)
                                                } else {
                                                    if (isEditing) {
                                                        val newEntry = EditSignerEntry(
                                                            signer = newSigner,
                                                            onChainId = null,
                                                            isOriginal = false,
                                                            isPending = true
                                                        )
                                                        onEditSignerEntriesChanged?.invoke(editSignerEntries + newEntry)
                                                        onSignersChanged((editSignerEntries + newEntry).map { it.signer })
                                                    } else {
                                                        onSignersChanged(signers + newSigner)
                                                    }
                                                    onFieldErrorsChanged(fieldErrors - "signers")
                                                    // Also add to available list so it shows as "already added"
                                                    onAvailablePasskeysChanged(availablePasskeys + newSigner)
                                                    onPasskeysLoadedChanged(true)
                                                    onNewPasskeyNameChanged("")
                                                    ActivityLogState.success("Registered and added new passkey signer")
                                                }
                                            } catch (e: Throwable) {
                                                val message = e.message ?: "Unknown error"
                                                if (isUserCancellation(message)) {
                                                    ActivityLogState.info("Passkey registration cancelled")
                                                } else {
                                                    ActivityLogState.error("Failed to register passkey: $message")
                                                }
                                            } finally {
                                                onIsRegisteringChanged(false)
                                            }
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    enabled = !isRegistering && !isLoadingPasskeys && !isSubmitting && newPasskeyName.isNotBlank()
                                ) {
                                    if (isRegistering) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(20.dp),
                                            strokeWidth = 2.dp,
                                            color = MaterialTheme.colorScheme.onPrimary
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                    }
                                    Text(if (isRegistering) "Registering..." else "Register New")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ============================================================================
// Sub-Components
// ============================================================================

/**
 * Mode selector for adding different signer types.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SignerModeSelector(
    selectedMode: SignerAddMode,
    onModeSelected: (SignerAddMode) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            value = selectedMode.displayName,
            onValueChange = {},
            readOnly = true,
            label = { Text("Signer Type") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            SignerAddMode.entries.forEach { mode ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(mode.displayName, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                mode.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    onClick = {
                        onModeSelected(mode)
                        expanded = false
                    }
                )
            }
        }
    }
}

/**
 * Card displaying a single signer with type badge and remove button.
 */
@Composable
internal fun SignerCard(
    signer: SmartAccountSigner,
    onRemove: () -> Unit,
    enabled: Boolean = true
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = signerChipColor(signer).copy(alpha = 0.08f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            SignerIdentityChip(
                signer = signer,
                modifier = Modifier.weight(1f)
            )

            // Remove button
            if (enabled) {
                IconButton(
                    onClick = onRemove,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Remove signer",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

/**
 * Card displaying a signer in edit mode with on-chain badge and protection for
 * the connected wallet's own signer.
 */
@Composable
internal fun EditSignerCard(
    entry: EditSignerEntry,
    isConnectedSigner: Boolean,
    onRemove: () -> Unit,
    enabled: Boolean = true
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = signerChipColor(entry.signer).copy(alpha = 0.08f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            SignerIdentityChip(
                signer = entry.signer,
                modifier = Modifier.weight(1f),
                showOnChainBadge = entry.isOriginal
            )

            // Remove button (disabled for connected wallet's own signer)
            if (enabled) {
                IconButton(
                    onClick = onRemove,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Remove signer",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp)
                    )
                }
            } else if (isConnectedSigner) {
                Text(
                    text = "You",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 8.dp)
                )
            }
        }
    }
}

// ============================================================================
// Helpers
// ============================================================================

/**
 * Returns true if [newSigner] is already present in [signers] by identity,
 * using [SmartAccountBuilders.signersEqual] for comparison.
 */
internal fun isDuplicateSigner(
    signers: List<SmartAccountSigner>,
    newSigner: SmartAccountSigner
): Boolean = signers.any { SmartAccountBuilders.signersEqual(it, newSigner) }

/**
 * Validates that [signer] can be added to [signers].
 *
 * Checks the maximum signer limit and duplicate identity in a single call.
 * Returns an error message string, or null if the signer is valid to add.
 */
internal fun validateNewSigner(
    signer: SmartAccountSigner,
    signers: List<SmartAccountSigner>
): String? {
    if (signers.size >= OZConstants.MAX_SIGNERS) {
        return "Maximum ${OZConstants.MAX_SIGNERS} signers allowed"
    }
    if (isDuplicateSigner(signers, signer)) {
        return "This signer is already added"
    }
    return null
}

/**
 * Checks whether the given signer is the connected wallet's own passkey signer.
 *
 * @param signer The signer to check.
 * @param connectedCredentialId The credential ID of the connected wallet's passkey.
 * @return True if [signer] is the connected wallet's passkey.
 */
private fun isConnectedWalletSigner(
    signer: SmartAccountSigner,
    connectedCredentialId: String?
): Boolean {
    if (connectedCredentialId == null) return false
    val credId = SmartAccountBuilders.getCredentialIdStringFromSigner(signer)
    return credId != null && credId == connectedCredentialId
}

// ============================================================================
// Enums
// ============================================================================

/**
 * Signer add mode options for the dropdown selector.
 */
enum class SignerAddMode(
    val displayName: String,
    val description: String
) {
    DELEGATED(
        "Delegated (G-address)",
        "Stellar account using native require_auth verification"
    ),
    ED25519(
        "Ed25519 Public Key",
        "Ed25519 key verified by an external verifier contract"
    ),
    PASSKEY(
        "Passkey (WebAuthn)",
        "Passkey verified by the WebAuthn verifier contract"
    )
}
