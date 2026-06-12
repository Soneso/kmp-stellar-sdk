package com.soneso.smartdemo.ui.screens

/**
 * Context rule builder screen: form for creating or editing a context rule.
 *
 * Business logic is delegated to flow layer files:
 * - ContextRuleFlow.kt: Network calls (add, remove, update, load rules, resolve ledger)
 * - ContextRuleEditFlow.kt: Edit-mode orchestrator (sequential execution, auth guard)
 * - ContextRuleEditTypes.kt: Shared types (EditSignerEntry, EditPolicyEntry, etc.)
 *
 * UI sections are delegated to extracted components:
 * - SignerManagementSection: Signer header, list, add forms
 * - PolicyManagementSection: Policy header, list, add forms
 *
 * Policy SCVal encoding is handled by PolicyScValBuilders.kt.
 */

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.soneso.smartdemo.config.DemoConfig
import com.soneso.smartdemo.config.KNOWN_POLICIES
import com.soneso.smartdemo.config.PolicyInfo
import com.soneso.smartdemo.flows.ContextRuleEditDiff
import com.soneso.smartdemo.flows.ContextRuleEditResult
import com.soneso.smartdemo.flows.ContextRuleResult
import com.soneso.smartdemo.flows.EditPolicyEntry
import com.soneso.smartdemo.flows.EditSignerEntry
import com.soneso.smartdemo.flows.FlowPolicyEntry
import com.soneso.smartdemo.flows.addContextRule
import com.soneso.smartdemo.flows.buildSelectedSigners
import com.soneso.smartdemo.flows.isSinglePasskeyTransfer
import com.soneso.smartdemo.flows.loadAllOnChainSigners
import com.soneso.smartdemo.flows.loadAvailableSigners
import com.soneso.smartdemo.flows.loadParsedContextRule
import com.soneso.smartdemo.flows.readPolicyParamsWithServer
import com.soneso.smartdemo.flows.withInProcessMultiSigner
import com.soneso.smartdemo.flows.resolveAbsoluteLedger
import com.soneso.smartdemo.flows.submitContextRuleEdits
import com.soneso.smartdemo.platform.getClipboard
import com.soneso.smartdemo.state.ActivityLogState
import com.soneso.smartdemo.state.DemoState
import com.soneso.smartdemo.ui.components.SignerPickerDialog
import com.soneso.smartdemo.util.hexToByteArray
import com.soneso.smartdemo.util.isUserCancellation
import com.soneso.smartdemo.util.toHexString
import com.soneso.stellar.sdk.Util
import com.soneso.stellar.sdk.rpc.SorobanServer
import com.soneso.stellar.sdk.smartaccount.core.ExternalSigner
import com.soneso.stellar.sdk.smartaccount.core.SmartAccountBuilders
import com.soneso.stellar.sdk.smartaccount.core.SmartAccountSigner
import com.soneso.stellar.sdk.smartaccount.oz.ContextRuleType
import com.soneso.stellar.sdk.smartaccount.oz.OZBuilders
import com.soneso.stellar.sdk.smartaccount.oz.OZConstants
import com.soneso.stellar.sdk.smartaccount.oz.SelectedSigner
import kotlinx.coroutines.launch

/**
 * Screen for creating or editing context rules on a smart account.
 *
 * Supports two modes:
 * - Create mode (editRuleId = null): Fresh form for adding a new context rule.
 * - Edit mode (editRuleId != null): Pre-populates the form with data from an existing
 *   rule. Supports adding/removing signers, adding/removing/modifying policies,
 *   updating name and expiry. Each change is a separate on-chain transaction.
 */
class ContextRuleBuilderScreen(
    private val editRuleId: UInt? = null
) : Screen {

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val scope = rememberCoroutineScope()
        val snackbarHostState = remember { SnackbarHostState() }
        val clipboard = remember { getClipboard() }

        val isEditing = editRuleId != null

        // --- Rule configuration state ---
        var ruleName by remember { mutableStateOf("") }
        var contextTypeOption by remember { mutableStateOf(ContextTypeOption.DEFAULT) }
        var contractAddress by remember { mutableStateOf("") }
        var wasmHashHex by remember { mutableStateOf("") }
        var hasExpiry by remember { mutableStateOf(false) }
        var expiryLedger by remember { mutableStateOf("") }
        var existingExpiryLedger by remember { mutableStateOf<UInt?>(null) }
        var expiryModified by remember { mutableStateOf(false) }

        // --- Signer management state (create mode) ---
        var signers by remember { mutableStateOf<List<SmartAccountSigner>>(emptyList()) }
        var signerAddMode by remember { mutableStateOf(SignerAddMode.DELEGATED) }
        var delegatedAddress by remember { mutableStateOf("") }
        var ed25519PubKeyHex by remember { mutableStateOf("") }
        var isLoadingPasskeys by remember { mutableStateOf(false) }
        var isRegistering by remember { mutableStateOf(false) }
        var availablePasskeys by remember { mutableStateOf<List<ExternalSigner>>(emptyList()) }
        var passkeysLoaded by remember { mutableStateOf(false) }
        var newPasskeyName by remember { mutableStateOf("") }

        // --- Policy management state (create mode) ---
        var policies by remember { mutableStateOf<List<PolicyEntry>>(emptyList()) }
        var selectedPolicyType by remember { mutableStateOf<PolicyInfo?>(null) }
        var thresholdValue by remember { mutableStateOf("") }
        var spendingLimitAmount by remember { mutableStateOf("") }
        var spendingLimitPeriodDays by remember { mutableStateOf("") }
        var weightedThresholdValue by remember { mutableStateOf("") }
        var signerWeights by remember { mutableStateOf<Map<String, String>>(emptyMap()) }

        // --- Edit-mode state ---
        var originalSignerEntries by remember { mutableStateOf<List<EditSignerEntry>>(emptyList()) }
        var signerEntries by remember { mutableStateOf<List<EditSignerEntry>>(emptyList()) }
        var originalPolicyEntries by remember { mutableStateOf<List<EditPolicyEntry>>(emptyList()) }
        var policyEntries by remember { mutableStateOf<List<EditPolicyEntry>>(emptyList()) }
        var originalName by remember { mutableStateOf("") }
        var allOnChainSigners by remember { mutableStateOf<List<SmartAccountSigner>>(emptyList()) }

        // --- Submission state ---
        var isSubmitting by remember { mutableStateOf(false) }
        var submissionResult by remember { mutableStateOf<ContextRuleResult?>(null) }

        // --- Edit-mode submission state ---
        var editResult by remember { mutableStateOf<ContextRuleEditResult?>(null) }
        var editProgressMessage by remember { mutableStateOf("") }
        var editProgressCompletedSteps by remember { mutableStateOf(0) }
        var editProgressTotalSteps by remember { mutableStateOf(0) }
        var showEditSignerPicker by remember { mutableStateOf(false) }

        // --- Create mode multi-signer state ---
        var createAvailableSigners by remember { mutableStateOf<List<SmartAccountSigner>>(emptyList()) }
        var createSignersLoaded by remember { mutableStateOf(false) }
        var showCreateSignerPicker by remember { mutableStateOf(false) }

        // --- UI state ---
        var isLoadingRule by remember { mutableStateOf(false) }
        var errorMessage by remember { mutableStateOf<String?>(null) }
        var fieldErrors by remember { mutableStateOf<Map<String, String>>(emptyMap()) }

        // Shared function to populate edit state from a parsed rule.
        // Called both on initial load and on reload after partial failure.
        suspend fun populateEditState(ruleId: UInt) {
            val parsed = loadParsedContextRule(ruleId)
            val contractId = DemoState.contractId ?: return

            ruleName = parsed.name
            originalName = parsed.name
            when (val ct = parsed.contextType) {
                is ContextRuleType.Default -> {
                    contextTypeOption = ContextTypeOption.DEFAULT
                }
                is ContextRuleType.CallContract -> {
                    contextTypeOption = ContextTypeOption.CALL_CONTRACT
                    contractAddress = ct.contractAddress
                }
                is ContextRuleType.CreateContract -> {
                    contextTypeOption = ContextTypeOption.CREATE_CONTRACT
                    wasmHashHex = ct.wasmHash.toHexString()
                }
            }
            if (parsed.validUntil != null) {
                hasExpiry = true
                existingExpiryLedger = parsed.validUntil
            } else {
                hasExpiry = false
                existingExpiryLedger = null
            }
            expiryLedger = ""
            expiryModified = false

            // Signers: positionally aligned with ParsedContextRule.signerIds
            val loadedSignerEntries = parsed.signers.mapIndexed { i, signer ->
                EditSignerEntry(
                    signer = signer,
                    onChainId = parsed.signerIds.getOrNull(i),
                    isOriginal = true,
                    isPending = false
                )
            }
            signerEntries = loadedSignerEntries
            originalSignerEntries = loadedSignerEntries.toList()
            signers = parsed.signers

            // Policies: positionally aligned with ParsedContextRule.policyIds.
            // Read on-chain params for each known policy using a single server instance.
            SorobanServer(DemoConfig.RPC_URL).use { server ->
                val loadedPolicyEntries = parsed.policies.mapIndexed { i, addr ->
                    val known = KNOWN_POLICIES.find { it.address == addr }
                    val params = if (known != null) {
                        readPolicyParamsWithServer(server, contractId, addr, known.type, parsed.id)
                    } else null

                    EditPolicyEntry(
                        info = known,
                        label = known?.name ?: "Unknown Policy",
                        address = addr,
                        scVal = null,
                        onChainId = parsed.policyIds.getOrNull(i),
                        isOriginal = true,
                        modified = false,
                        originalParams = params
                    )
                }
                policyEntries = loadedPolicyEntries
                originalPolicyEntries = loadedPolicyEntries.toList()
                policies = loadedPolicyEntries.map { e ->
                    PolicyEntry(info = e.info, label = e.label, address = e.address, scVal = e.scVal)
                }
            }

            // Load all signers from all on-chain rules for the "Reuse Signer" picker.
            allOnChainSigners = loadAllOnChainSigners()

            ActivityLogState.info(
                "Loaded rule #$ruleId for editing: " +
                    "${loadedSignerEntries.size} signer(s), " +
                    "${policyEntries.size} policy/policies"
            )
        }

        // --- Edit mode: load existing rule ---
        LaunchedEffect(editRuleId) {
            if (editRuleId != null && DemoState.isConnected && DemoState.kit != null) {
                isLoadingRule = true
                errorMessage = null
                try {
                    populateEditState(editRuleId)
                } catch (e: Exception) {
                    errorMessage = "Failed to load rule #$editRuleId: ${e.message}"
                    ActivityLogState.error("Failed to load rule: ${e.message}")
                } finally {
                    isLoadingRule = false
                }
            }
        }

        // Load available signers for multi-signer authorization (both create and edit modes).
        // This includes all signers from all on-chain rules (active passkey, other passkeys,
        // delegated signers) — needed for the signer picker dialog.
        LaunchedEffect(DemoState.isConnected, DemoState.kit) {
            if (DemoState.isConnected && DemoState.kit != null) {
                try {
                    val signerInfos = loadAvailableSigners()
                    createAvailableSigners = signerInfos.map { it.signer }
                    createSignersLoaded = true
                } catch (_: Exception) {
                    createSignersLoaded = true
                }
            }
        }

        // Compute the edit diff for operation summary and submission.
        val editDiff: ContextRuleEditDiff? = if (isEditing && editRuleId != null) {
            val nameChanged = ruleName.trim() != originalName
            // Only entries that are themselves originals can cancel out an original in the
            // removal diff. A staged entry that equals a dropped original (same signer
            // re-added, or a same-type policy whose contract address matches) must NOT
            // suppress the removal — the removal and the add are both submitted, in that
            // order, otherwise the contract rejects the add as a duplicate (3007/3009).
            val removedSigners = originalSignerEntries.filter { orig ->
                signerEntries.none { it.isOriginal && SmartAccountBuilders.signersEqual(it.signer, orig.signer) }
            }
            val newSigners = signerEntries.filter { entry ->
                !entry.isOriginal
            }
            val removedPolicies = originalPolicyEntries.filter { orig ->
                policyEntries.none { it.isOriginal && it.address == orig.address }
            }
            val newPolicies = policyEntries.filter { entry ->
                !entry.isOriginal
            }
            val modifiedPolicies = policyEntries.filter { entry ->
                entry.isOriginal && entry.modified
            }
            val expiryChanged = expiryModified
            val newExpiry: UInt? = if (expiryChanged) {
                if (hasExpiry && expiryLedger.isNotBlank()) {
                    // Will be resolved at submission time
                    expiryLedger.toUIntOrNull()
                } else if (!hasExpiry) {
                    null
                } else {
                    existingExpiryLedger
                }
            } else {
                null
            }

            ContextRuleEditDiff(
                ruleId = editRuleId,
                nameChanged = nameChanged,
                newName = if (nameChanged) ruleName.trim() else null,
                newSigners = newSigners,
                removedSigners = removedSigners,
                newPolicies = newPolicies,
                removedPolicies = removedPolicies,
                modifiedPolicies = modifiedPolicies,
                expiryChanged = expiryChanged,
                newExpiry = newExpiry
            )
        } else null

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(if (isEditing) "Edit Context Rule" else "Add Context Rule") },
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
            },
            snackbarHost = { SnackbarHost(snackbarHostState) }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // --- Not connected ---
                if (!DemoState.isConnected) {
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
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "No wallet connected",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "Connect a wallet to create or edit context rules.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // --- Loading indicator for edit mode ---
                if (isLoadingRule) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(32.dp))
                        Text(
                            text = "Loading rule #$editRuleId...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // --- Error message ---
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

                // --- Create mode submission result ---
                if (!isEditing && submissionResult != null) {
                    val result = submissionResult!!
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = if (result.success)
                                Color(0xFF4CAF50).copy(alpha = 0.12f)
                            else
                                MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = if (result.success) "Transaction Successful" else "Transaction Failed",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (result.success)
                                    Color(0xFF2E7D32)
                                else
                                    MaterialTheme.colorScheme.onErrorContainer
                            )
                            if (result.hash != null) {
                                Text(
                                    text = "Hash: ${result.hash}",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = FontFamily.Monospace,
                                    color = if (result.success)
                                        Color(0xFF2E7D32)
                                    else
                                        MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.clickable {
                                        scope.launch {
                                            clipboard.copyToClipboard(result.hash)
                                            snackbarHostState.showSnackbar("Hash copied to clipboard")
                                        }
                                    }
                                )
                                Text(
                                    text = "Tap to copy",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (result.success)
                                        Color(0xFF2E7D32).copy(alpha = 0.6f)
                                    else
                                        MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.6f)
                                )
                            }
                            if (result.error != null) {
                                Text(
                                    text = result.error,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                            if (result.success) {
                                Button(
                                    onClick = { navigator.pop() },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Go Back")
                                }
                            }
                        }
                    }
                }

                // --- Edit mode result ---
                if (isEditing && editResult != null) {
                    val result = editResult!!
                    val isFullSuccess = result.success && !result.partialDueToAuthGuard
                    val isPartialSuccess = result.success && result.partialDueToAuthGuard
                    val cardContainerColor = when {
                        isFullSuccess -> Color(0xFF4CAF50).copy(alpha = 0.12f)
                        isPartialSuccess -> Color(0xFF2196F3).copy(alpha = 0.12f)
                        else -> MaterialTheme.colorScheme.errorContainer
                    }
                    val titleColor = when {
                        isFullSuccess -> Color(0xFF2E7D32)
                        isPartialSuccess -> Color(0xFF1565C0)
                        else -> MaterialTheme.colorScheme.onErrorContainer
                    }
                    val bodyColor = when {
                        isFullSuccess -> Color(0xFF2E7D32)
                        isPartialSuccess -> Color(0xFF1565C0)
                        else -> MaterialTheme.colorScheme.onErrorContainer
                    }
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = cardContainerColor
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = when {
                                    isFullSuccess -> "All Changes Applied"
                                    isPartialSuccess -> "Partial Update"
                                    else -> "Update Failed"
                                },
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = titleColor
                            )
                            Text(
                                text = "${result.completedOperations} of ${result.totalOperations} operation(s) completed",
                                style = MaterialTheme.typography.bodySmall,
                                color = bodyColor
                            )
                            if (result.transactionHashes.isNotEmpty()) {
                                Text(
                                    text = "Transaction Hashes",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = bodyColor
                                )
                                for (hash in result.transactionHashes) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = hash,
                                            style = MaterialTheme.typography.bodySmall,
                                            fontFamily = FontFamily.Monospace,
                                            color = bodyColor,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                        OutlinedButton(
                                            onClick = {
                                                scope.launch {
                                                    clipboard.copyToClipboard(hash)
                                                    snackbarHostState.showSnackbar("Hash copied")
                                                }
                                            },
                                            modifier = Modifier.padding(start = 4.dp)
                                        ) {
                                            Text("Copy", style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                }
                            }
                            if (result.authGuardMessage != null) {
                                Text(
                                    text = result.authGuardMessage,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFF1565C0)
                                )
                            }
                            if (result.error != null) {
                                Text(
                                    text = result.error,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                            if (result.failedStep != null) {
                                Text(
                                    text = "Failed at: ${result.failedStep}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                            if (isFullSuccess) {
                                Button(
                                    onClick = { navigator.pop() },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Done")
                                }
                            }
                        }
                    }
                }

                // --- Main form ---
                val createSuccess = !isEditing && submissionResult?.success == true
                val editSuccess = isEditing && editResult?.success == true && editResult?.partialDueToAuthGuard != true
                if (DemoState.isConnected && !isLoadingRule && !createSuccess && !editSuccess) {

                    // ====================================================================
                    // Section 1A: Rule Configuration
                    // ====================================================================

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
                                text = "Rule Configuration",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "Define the context type and basic settings for this rule.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Rule Name
                    OutlinedTextField(
                        value = ruleName,
                        onValueChange = {
                            ruleName = it
                            fieldErrors = fieldErrors - "ruleName"
                        },
                        label = { Text("Rule Name") },
                        placeholder = { Text("e.g., DefaultRule, TokenTransfers") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        enabled = !isSubmitting,
                        isError = fieldErrors.containsKey("ruleName"),
                        supportingText = if (fieldErrors.containsKey("ruleName")) {
                            { Text(fieldErrors["ruleName"]!!) }
                        } else null
                    )

                    // Context Type Selector (disabled in edit mode)
                    ContextTypeSelector(
                        selectedOption = contextTypeOption,
                        onOptionSelected = { option ->
                            contextTypeOption = option
                            if (option == ContextTypeOption.CALL_CONTRACT && contractAddress.isEmpty()) {
                                contractAddress = DemoConfig.NATIVE_TOKEN_CONTRACT
                            }
                            fieldErrors = fieldErrors - "contractAddress" - "wasmHash"
                        },
                        contractAddress = contractAddress,
                        onContractAddressChanged = {
                            contractAddress = it
                            fieldErrors = fieldErrors - "contractAddress"
                        },
                        wasmHashHex = wasmHashHex,
                        onWasmHashChanged = {
                            wasmHashHex = it
                            fieldErrors = fieldErrors - "wasmHash"
                        },
                        contractAddressError = fieldErrors["contractAddress"],
                        wasmHashError = fieldErrors["wasmHash"],
                        enabled = !isSubmitting && !isEditing
                    )

                    if (isEditing) {
                        Text(
                            text = "Context type cannot be changed after creation.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Expiry Section
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = hasExpiry,
                                    onCheckedChange = {
                                        hasExpiry = it
                                        expiryModified = true
                                        if (!it) {
                                            expiryLedger = ""
                                            fieldErrors = fieldErrors - "expiryLedger"
                                        }
                                    },
                                    enabled = !isSubmitting
                                )
                                Text(
                                    text = "Set Expiry",
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(start = 8.dp)
                                )
                            }
                            AnimatedVisibility(
                                visible = hasExpiry,
                                enter = expandVertically(),
                                exit = shrinkVertically()
                            ) {
                                var expiryDropdownExpanded by remember { mutableStateOf(false) }
                                val expiryOptions = listOf(
                                    "5 min" to (Util.LEDGERS_PER_HOUR / 12),
                                    "30 min" to (Util.LEDGERS_PER_HOUR / 2),
                                    "1 hour" to Util.LEDGERS_PER_HOUR,
                                    "1 day" to Util.LEDGERS_PER_DAY,
                                    "10 days" to (Util.LEDGERS_PER_DAY * 10)
                                )
                                val selectedLabel = expiryOptions.find {
                                    it.second.toString() == expiryLedger
                                }?.first ?: if (expiryLedger.isNotEmpty()) "Custom" else "Select duration..."

                                Column(
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    ExposedDropdownMenuBox(
                                        expanded = expiryDropdownExpanded,
                                        onExpandedChange = { if (!isSubmitting) expiryDropdownExpanded = it }
                                    ) {
                                        OutlinedTextField(
                                            value = selectedLabel,
                                            onValueChange = {},
                                            readOnly = true,
                                            label = { Text("Time from now") },
                                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expiryDropdownExpanded) },
                                            modifier = Modifier
                                                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                                .fillMaxWidth(),
                                            enabled = !isSubmitting,
                                            isError = fieldErrors.containsKey("expiryLedger"),
                                            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors()
                                        )
                                        ExposedDropdownMenu(
                                            expanded = expiryDropdownExpanded,
                                            onDismissRequest = { expiryDropdownExpanded = false }
                                        ) {
                                            expiryOptions.forEach { (label, ledgers) ->
                                                DropdownMenuItem(
                                                    text = { Text(label) },
                                                    onClick = {
                                                        expiryLedger = ledgers.toString()
                                                        expiryDropdownExpanded = false
                                                        expiryModified = true
                                                        fieldErrors = fieldErrors - "expiryLedger"
                                                    }
                                                )
                                            }
                                        }
                                    }
                                    if (fieldErrors.containsKey("expiryLedger")) {
                                        Text(
                                            text = fieldErrors["expiryLedger"]!!,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.error
                                        )
                                    }
                                    Text(
                                        text = "The rule will expire after the selected duration from the current ledger.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    if (isEditing && existingExpiryLedger != null) {
                                        Text(
                                            text = "Current on-chain expiry: ledger $existingExpiryLedger. " +
                                                "Select a duration above to replace it.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 4.dp),
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                    )

                    // ====================================================================
                    // Section 1B: Signer Management (delegated to extracted component)
                    // ====================================================================

                    SignerManagementSection(
                        signers = signers,
                        onSignersChanged = { signers = it },
                        signerAddMode = signerAddMode,
                        onSignerAddModeChanged = { signerAddMode = it },
                        delegatedAddress = delegatedAddress,
                        onDelegatedAddressChanged = { delegatedAddress = it },
                        ed25519PubKeyHex = ed25519PubKeyHex,
                        onEd25519PubKeyHexChanged = { ed25519PubKeyHex = it },
                        isLoadingPasskeys = isLoadingPasskeys,
                        onIsLoadingPasskeysChanged = { isLoadingPasskeys = it },
                        isRegistering = isRegistering,
                        onIsRegisteringChanged = { isRegistering = it },
                        availablePasskeys = availablePasskeys,
                        onAvailablePasskeysChanged = { availablePasskeys = it },
                        passkeysLoaded = passkeysLoaded,
                        onPasskeysLoadedChanged = { passkeysLoaded = it },
                        newPasskeyName = newPasskeyName,
                        onNewPasskeyNameChanged = { newPasskeyName = it },
                        signerWeights = signerWeights,
                        onSignerWeightsChanged = { signerWeights = it },
                        fieldErrors = fieldErrors,
                        onFieldErrorsChanged = { fieldErrors = it },
                        isSubmitting = isSubmitting,
                        scope = scope,
                        isEditing = isEditing,
                        editSignerEntries = signerEntries,
                        onEditSignerEntriesChanged = { updated ->
                            signerEntries = updated
                            signers = updated.map { it.signer }
                        },
                        existingSigners = allOnChainSigners,
                        connectedCredentialId = DemoState.credentialId
                    )

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 4.dp),
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                    )

                    // ====================================================================
                    // Section 2A: Policy Configuration (delegated to extracted component)
                    // ====================================================================

                    PolicyManagementSection(
                        policies = policies,
                        onPoliciesChanged = { policies = it },
                        signers = signers,
                        selectedPolicyType = selectedPolicyType,
                        onSelectedPolicyTypeChanged = { selectedPolicyType = it },
                        thresholdValue = thresholdValue,
                        onThresholdValueChanged = { thresholdValue = it },
                        spendingLimitAmount = spendingLimitAmount,
                        onSpendingLimitAmountChanged = { spendingLimitAmount = it },
                        spendingLimitPeriodDays = spendingLimitPeriodDays,
                        onSpendingLimitPeriodDaysChanged = { spendingLimitPeriodDays = it },
                        weightedThresholdValue = weightedThresholdValue,
                        onWeightedThresholdValueChanged = { weightedThresholdValue = it },
                        signerWeights = signerWeights,
                        onSignerWeightsChanged = { signerWeights = it },
                        fieldErrors = fieldErrors,
                        onFieldErrorsChanged = { fieldErrors = it },
                        isSubmitting = isSubmitting,
                        isEditing = isEditing,
                        editPolicyEntries = policyEntries,
                        onEditPolicyEntriesChanged = { updated ->
                            policyEntries = updated
                            policies = updated.map { e ->
                                PolicyEntry(info = e.info, label = e.label, address = e.address, scVal = e.scVal)
                            }
                        }
                    )

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 4.dp),
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                    )

                    // ====================================================================
                    // Section 2B: Operation Summary + Submission
                    // ====================================================================

                    // Edit mode: operation summary
                    if (isEditing && editDiff != null) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                if (editDiff.isEmpty) {
                                    Text(
                                        text = "No changes to apply",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                } else {
                                    val parts = mutableListOf<String>()
                                    if (editDiff.nameChanged) parts.add("name update")
                                    if (editDiff.newSigners.isNotEmpty())
                                        parts.add("${editDiff.newSigners.size} signer(s) to add")
                                    if (editDiff.removedSigners.isNotEmpty())
                                        parts.add("${editDiff.removedSigners.size} signer(s) to remove")
                                    if (editDiff.newPolicies.isNotEmpty())
                                        parts.add("${editDiff.newPolicies.size} policy/policies to add")
                                    if (editDiff.removedPolicies.isNotEmpty())
                                        parts.add("${editDiff.removedPolicies.size} policy/policies to remove")
                                    if (editDiff.modifiedPolicies.isNotEmpty())
                                        parts.add("${editDiff.modifiedPolicies.size} policy/policies to update")
                                    if (editDiff.expiryChanged) parts.add("expiry update")

                                    Text(
                                        text = "Pending changes: ${parts.joinToString(", ")}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "${editDiff.totalOperations} passkey prompt(s) required",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    // Edit mode: progress display
                    if (isEditing && isSubmitting && editProgressTotalSteps > 0) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = Color(0xFF2196F3).copy(alpha = 0.08f)
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(24.dp),
                                        strokeWidth = 2.dp
                                    )
                                    Text(
                                        text = "Step ${editProgressCompletedSteps + 1} of $editProgressTotalSteps",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                if (editProgressMessage.isNotEmpty()) {
                                    Text(
                                        text = editProgressMessage,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    // Submit button
                    Button(
                        onClick = {
                            if (isEditing) {
                                // Edit mode validation
                                val errors = validateFormForEdit(
                                    ruleName = ruleName,
                                    signerEntries = signerEntries,
                                    policyEntries = policyEntries,
                                    hasExpiry = hasExpiry,
                                    expiryLedger = expiryLedger,
                                    expiryModified = expiryModified
                                )
                                if (errors.isNotEmpty()) {
                                    fieldErrors = errors
                                    errorMessage = "Please fix the validation errors above."
                                    return@Button
                                }

                                if (editDiff == null || editDiff.isEmpty) {
                                    errorMessage = "No changes to apply."
                                    return@Button
                                }

                                fieldErrors = emptyMap()
                                errorMessage = null
                                editResult = null

                                // Multi-signer detection: check on-chain signers (original state)
                                val onChainSigners = originalSignerEntries.map { it.signer }
                                val singlePasskey = isSinglePasskeyTransfer(onChainSigners)

                                if (!singlePasskey && onChainSigners.size > 1) {
                                    // Show signer picker for multi-signer rules
                                    showEditSignerPicker = true
                                } else {
                                    // Single-signer mode: submit directly
                                    isSubmitting = true
                                    editProgressCompletedSteps = 0
                                    editProgressTotalSteps = editDiff.totalOperations

                                    scope.launch {
                                        try {
                                            // Resolve expiry if needed
                                            val resolvedDiff = resolveEditDiffExpiry(editDiff)
                                            var stepCount = 0
                                            val result = submitContextRuleEdits(
                                                diff = resolvedDiff,
                                                selectedSigners = emptyList()
                                            ) { msg ->
                                                editProgressMessage = msg
                                                stepCount++
                                                editProgressCompletedSteps = stepCount - 1
                                            }
                                            handleEditResult(
                                                result = result,
                                                onEditResult = { editResult = it },
                                                onReload = {
                                                    scope.launch {
                                                        try {
                                                            populateEditState(editRuleId!!)
                                                        } catch (e: Exception) {
                                                            errorMessage = "Failed to reload rule: ${e.message}"
                                                        }
                                                    }
                                                },
                                                onError = { errorMessage = it }
                                            )
                                        } catch (e: Throwable) {
                                            val msg = e.message ?: "Unknown error"
                                            if (isUserCancellation(msg)) {
                                                editResult = ContextRuleEditResult(
                                                    success = false,
                                                    completedOperations = editProgressCompletedSteps,
                                                    totalOperations = editDiff.totalOperations,
                                                    partialDueToAuthGuard = false,
                                                    authGuardMessage = null,
                                                    error = "Passkey authentication cancelled",
                                                    failedStep = null
                                                )
                                                ActivityLogState.info("Edit cancelled by user")
                                            } else {
                                                errorMessage = msg
                                                ActivityLogState.error("Edit failed: $msg")
                                            }
                                        } finally {
                                            isSubmitting = false
                                        }
                                    }
                                }
                            } else {
                                // Create mode validation
                                val errors = validateForm(
                                    ruleName = ruleName,
                                    contextTypeOption = contextTypeOption,
                                    contractAddress = contractAddress,
                                    wasmHashHex = wasmHashHex,
                                    hasExpiry = hasExpiry,
                                    expiryLedger = expiryLedger,
                                    signers = signers
                                )
                                if (errors.isNotEmpty()) {
                                    fieldErrors = errors
                                    errorMessage = "Please fix the validation errors above."
                                    return@Button
                                }

                                fieldErrors = emptyMap()
                                errorMessage = null
                                isSubmitting = true
                                submissionResult = null

                                scope.launch {
                                    try {
                                        // Create mode: check if multi-signer needed
                                        val needsMultiSigner = createSignersLoaded &&
                                            createAvailableSigners.size > 1 &&
                                            !isSinglePasskeyTransfer(createAvailableSigners)

                                        if (needsMultiSigner) {
                                            showCreateSignerPicker = true
                                            return@launch
                                        }

                                        val selectedContextType = buildContextType(
                                            contextTypeOption, contractAddress, wasmHashHex
                                        )
                                        val validUntilVal = resolveExpiryLedger(hasExpiry, expiryLedger)
                                        val flowPolicies = policies.map { policy ->
                                            FlowPolicyEntry(address = policy.address, scVal = policy.scVal)
                                        }

                                        val result = addContextRule(
                                            contextType = selectedContextType,
                                            name = ruleName.trim(),
                                            validUntil = validUntilVal,
                                            signers = signers,
                                            policies = flowPolicies
                                        )

                                        submissionResult = result
                                    } catch (e: Throwable) {
                                        submissionResult = ContextRuleResult(
                                            success = false,
                                            hash = null,
                                            error = e.message ?: "Unknown error"
                                        )
                                        ActivityLogState.error("Transaction failed: ${e.message}")
                                    } finally {
                                        isSubmitting = false
                                    }
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = DemoState.isConnected && !isSubmitting &&
                            ruleName.isNotBlank() &&
                            (isEditing || signers.isNotEmpty()) &&
                            submissionResult?.success != true &&
                            !(isEditing && editDiff?.isEmpty == true)
                    ) {
                        if (isSubmitting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Submitting...")
                        } else {
                            Text(
                                if (isEditing) "Apply Changes" else "Create Context Rule"
                            )
                        }
                    }

                    if (isSubmitting && !isEditing) {
                        Text(
                            text = "Transaction in progress...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }

        // Signer picker dialog for multi-signer create mode
        if (showCreateSignerPicker) {
            SignerPickerDialog(
                isOpen = true,
                onDismiss = {
                    showCreateSignerPicker = false
                    isSubmitting = false
                },
                availableSigners = createAvailableSigners,
                activeCredentialId = DemoState.credentialId,
                title = "Select Signers",
                description = "Choose which signers co-authorize creating this context rule. " +
                    "For Stellar account signers, enter the secret key to enable signing.",
                onConfirm = { selectedSigners, delegatedKeyPairs, ed25519Secrets ->
                    showCreateSignerPicker = false

                    scope.launch {
                        isSubmitting = true
                        submissionResult = null

                        try {
                            val selectedContextType = buildContextType(
                                contextTypeOption, contractAddress, wasmHashHex
                            )
                            val validUntilVal = resolveExpiryLedger(hasExpiry, expiryLedger)
                            val flowPolicies = policies.map { policy ->
                                FlowPolicyEntry(address = policy.address, scVal = policy.scVal)
                            }

                            val selected = if (isSinglePasskeyTransfer(selectedSigners)) {
                                emptyList()
                            } else {
                                buildSelectedSigners(selectedSigners)
                            }

                            ActivityLogState.info(
                                "Creating context rule with multi-signer authorization " +
                                    "(${if (selected.isEmpty()) "single-signer" else "${selected.size} signer(s)"})"
                            )

                            val result = withInProcessMultiSigner(delegatedKeyPairs, ed25519Secrets) {
                                addContextRule(
                                    contextType = selectedContextType,
                                    name = ruleName.trim(),
                                    validUntil = validUntilVal,
                                    signers = signers,
                                    policies = flowPolicies,
                                    selectedSigners = selected
                                )
                            }
                            submissionResult = result
                        } catch (e: Throwable) {
                            val msg = e.message ?: "Unknown error"
                            if (isUserCancellation(msg)) {
                                submissionResult = ContextRuleResult(
                                    success = false, hash = null,
                                    error = "Passkey authentication cancelled"
                                )
                            } else {
                                submissionResult = ContextRuleResult(
                                    success = false, hash = null, error = msg
                                )
                                ActivityLogState.error("Transaction failed: $msg")
                            }
                        } finally {
                            isSubmitting = false
                        }
                    }
                }
            )
        }

        // Signer picker dialog for multi-signer edit mode
        if (showEditSignerPicker && editDiff != null) {
            // Use all available signers from all on-chain rules (same source as create mode).
            // This includes the active passkey, other passkeys, and delegated signers.
            SignerPickerDialog(
                isOpen = true,
                onDismiss = {
                    showEditSignerPicker = false
                },
                availableSigners = createAvailableSigners,
                activeCredentialId = DemoState.credentialId,
                title = "Select Signers",
                description = "Choose which signers co-authorize editing this context rule. " +
                    "For Stellar account signers, enter the secret key to enable signing.",
                onConfirm = { selectedSigners, delegatedKeyPairs, ed25519Secrets ->
                    showEditSignerPicker = false
                    isSubmitting = true
                    editProgressCompletedSteps = 0
                    editProgressTotalSteps = editDiff.totalOperations

                    scope.launch {
                        try {
                            val selected: List<SelectedSigner> =
                                if (isSinglePasskeyTransfer(selectedSigners)) {
                                    emptyList()
                                } else {
                                    buildSelectedSigners(selectedSigners)
                                }

                            val resolvedDiff = resolveEditDiffExpiry(editDiff)
                            var stepCount = 0
                            // Keep the in-process signing material registered for the entire edit
                            // sequence — submitContextRuleEdits runs each change as a separate
                            // transaction and clears once when the whole sequence completes.
                            val result = withInProcessMultiSigner(delegatedKeyPairs, ed25519Secrets) {
                                submitContextRuleEdits(
                                    diff = resolvedDiff,
                                    selectedSigners = selected
                                ) { msg ->
                                    editProgressMessage = msg
                                    stepCount++
                                    editProgressCompletedSteps = stepCount - 1
                                }
                            }
                            handleEditResult(
                                result = result,
                                onEditResult = { editResult = it },
                                onReload = {
                                    scope.launch {
                                        try {
                                            populateEditState(editRuleId!!)
                                        } catch (e: Exception) {
                                            errorMessage = "Failed to reload rule: ${e.message}"
                                        }
                                    }
                                },
                                onError = { errorMessage = it }
                            )
                        } catch (e: Throwable) {
                            val msg = e.message ?: "Unknown error"
                            if (isUserCancellation(msg)) {
                                editResult = ContextRuleEditResult(
                                    success = false,
                                    completedOperations = editProgressCompletedSteps,
                                    totalOperations = editDiff.totalOperations,
                                    partialDueToAuthGuard = false,
                                    authGuardMessage = null,
                                    error = "Passkey authentication cancelled",
                                    failedStep = null
                                )
                                ActivityLogState.info("Edit cancelled by user")
                            } else {
                                errorMessage = msg
                                ActivityLogState.error("Edit failed: $msg")
                            }
                        } finally {
                            isSubmitting = false
                        }
                    }
                }
            )
        }
    }

    // ========================================================================
    // Composable Sub-Components
    // ========================================================================

    /**
     * Dropdown selector for context rule type.
     */
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun ContextTypeSelector(
        selectedOption: ContextTypeOption,
        onOptionSelected: (ContextTypeOption) -> Unit,
        contractAddress: String,
        onContractAddressChanged: (String) -> Unit,
        wasmHashHex: String,
        onWasmHashChanged: (String) -> Unit,
        contractAddressError: String?,
        wasmHashError: String?,
        enabled: Boolean = true
    ) {
        var contextTypeExpanded by remember { mutableStateOf(false) }
        var contractDropdownExpanded by remember { mutableStateOf(false) }

        val contractOptions = buildList {
            add(ContractOption("XLM Native Contract", DemoConfig.NATIVE_TOKEN_CONTRACT))
            val demoTokenId = DemoState.demoTokenContractId
            if (demoTokenId != null) {
                add(ContractOption("Demo Token Contract", demoTokenId))
            }
        }

        val selectedContractLabel = contractOptions.find { it.address == contractAddress }?.label
            ?: if (contractAddress.isNotEmpty()) contractAddress else contractOptions.firstOrNull()?.label ?: ""

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ExposedDropdownMenuBox(
                expanded = contextTypeExpanded,
                onExpandedChange = { if (enabled) contextTypeExpanded = it }
            ) {
                OutlinedTextField(
                    value = selectedOption.displayName,
                    onValueChange = {},
                    readOnly = true,
                    enabled = enabled,
                    label = { Text("Context Type") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = contextTypeExpanded) },
                    modifier = Modifier
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                        .fillMaxWidth()
                )
                ExposedDropdownMenu(
                    expanded = contextTypeExpanded,
                    onDismissRequest = { contextTypeExpanded = false }
                ) {
                    ContextTypeOption.entries.forEach { option ->
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(option.displayName, style = MaterialTheme.typography.bodyMedium)
                                    Text(
                                        option.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            },
                            onClick = {
                                onOptionSelected(option)
                                contextTypeExpanded = false
                            }
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = selectedOption == ContextTypeOption.CALL_CONTRACT,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                ExposedDropdownMenuBox(
                    expanded = contractDropdownExpanded,
                    onExpandedChange = { if (enabled) contractDropdownExpanded = it }
                ) {
                    OutlinedTextField(
                        value = selectedContractLabel,
                        onValueChange = {},
                        readOnly = true,
                        enabled = enabled,
                        label = { Text("Contract") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = contractDropdownExpanded) },
                        modifier = Modifier
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                            .fillMaxWidth(),
                        isError = contractAddressError != null,
                        supportingText = if (contractAddressError != null) {
                            { Text(contractAddressError) }
                        } else null
                    )
                    ExposedDropdownMenu(
                        expanded = contractDropdownExpanded,
                        onDismissRequest = { contractDropdownExpanded = false }
                    ) {
                        contractOptions.forEach { option ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(option.label, style = MaterialTheme.typography.bodyMedium)
                                        Text(
                                            option.address.take(12) + "...",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontFamily = FontFamily.Monospace,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                },
                                onClick = {
                                    onContractAddressChanged(option.address)
                                    contractDropdownExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = selectedOption == ContextTypeOption.CREATE_CONTRACT,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                OutlinedTextField(
                    value = wasmHashHex,
                    onValueChange = onWasmHashChanged,
                    label = { Text("WASM Hash (hex)") },
                    placeholder = { Text("64 hex characters") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = enabled,
                    isError = wasmHashError != null,
                    supportingText = if (wasmHashError != null) {
                        { Text(wasmHashError) }
                    } else null
                )
            }
        }
    }

    // ========================================================================
    // Helpers
    // ========================================================================

    /**
     * Converts the expiry form state to an absolute ledger number, or returns null
     * if no expiry is set.
     */
    private suspend fun resolveExpiryLedger(hasExpiry: Boolean, expiryLedger: String): UInt? {
        if (!hasExpiry) return null
        val offset = expiryLedger.toUIntOrNull() ?: return null
        return resolveAbsoluteLedger(offset)
    }

    /**
     * Builds the context type from the form state.
     */
    private fun buildContextType(
        option: ContextTypeOption,
        contractAddress: String,
        wasmHashHex: String
    ): ContextRuleType {
        return when (option) {
            ContextTypeOption.DEFAULT -> OZBuilders.createDefaultContextType()
            ContextTypeOption.CALL_CONTRACT ->
                OZBuilders.createCallContractContextType(contractAddress.trim())
            ContextTypeOption.CREATE_CONTRACT ->
                OZBuilders.createCreateContractContextType(wasmHashHex.trim().lowercase())
        }
    }

    /**
     * Resolves the expiry field in the edit diff to an absolute ledger number.
     * The diff's newExpiry field stores the ledger offset from the dropdown;
     * this function converts it to an absolute ledger by adding the current ledger.
     */
    private suspend fun resolveEditDiffExpiry(diff: ContextRuleEditDiff): ContextRuleEditDiff {
        if (!diff.expiryChanged) return diff
        val offset = diff.newExpiry
        val absoluteExpiry = if (offset != null && offset > 0u) {
            resolveAbsoluteLedger(offset)
        } else {
            null // Remove expiry
        }
        return diff.copy(newExpiry = absoluteExpiry)
    }

    /**
     * Handles the result of an edit submission, routing to the appropriate UI state.
     */
    private fun handleEditResult(
        result: ContextRuleEditResult,
        onEditResult: (ContextRuleEditResult) -> Unit,
        onReload: () -> Unit,
        onError: (String?) -> Unit
    ) {
        onEditResult(result)
        if (result.success) {
            if (result.partialDueToAuthGuard) {
                onReload()
            }
            // Full success: do not navigate back automatically.
            // The result card shows a "Done" button for the user.
        } else {
            onError(result.error)
            onReload()
        }
    }

    // ========================================================================
    // Validation
    // ========================================================================

    /**
     * Validates the complete form for create mode and returns a map of field name to
     * error message. An empty map means the form is valid.
     */
    private fun validateForm(
        ruleName: String,
        contextTypeOption: ContextTypeOption,
        contractAddress: String,
        wasmHashHex: String,
        hasExpiry: Boolean,
        expiryLedger: String,
        signers: List<SmartAccountSigner>
    ): Map<String, String> {
        val errors = mutableMapOf<String, String>()

        if (ruleName.isBlank()) {
            errors["ruleName"] = "Rule name is required"
        }

        when (contextTypeOption) {
            ContextTypeOption.CALL_CONTRACT -> {
                if (contractAddress.isEmpty()) {
                    errors["contractAddress"] = "A contract must be selected"
                }
            }
            ContextTypeOption.CREATE_CONTRACT -> {
                val hex = wasmHashHex.trim().lowercase()
                if (hex.isEmpty()) {
                    errors["wasmHash"] = "WASM hash is required"
                } else if (hex.length != 64) {
                    errors["wasmHash"] = "Must be 64 hex characters (32 bytes), got ${hex.length}"
                } else if (!hex.all { it in '0'..'9' || it in 'a'..'f' }) {
                    errors["wasmHash"] = "Invalid hex characters"
                }
            }
            ContextTypeOption.DEFAULT -> { /* no extra validation */ }
        }

        if (hasExpiry) {
            if (expiryLedger.isBlank()) {
                errors["expiryLedger"] = "Please select an expiry duration"
            } else {
                val ledgerNum = expiryLedger.toUIntOrNull()
                if (ledgerNum == null || ledgerNum == 0u) {
                    errors["expiryLedger"] = "Must be a positive integer"
                }
            }
        }

        if (signers.isEmpty()) {
            errors["signers"] = "At least one signer is required"
        }

        return errors
    }

    /**
     * Validates the form for edit mode. Checks that:
     * - Rule name is not blank
     * - At least one signer OR one policy remains (contract error 3004)
     * - Signer count does not exceed maximum
     * - Policy count does not exceed maximum
     * - Expiry is valid if modified
     */
    private fun validateFormForEdit(
        ruleName: String,
        signerEntries: List<EditSignerEntry>,
        policyEntries: List<EditPolicyEntry>,
        hasExpiry: Boolean,
        expiryLedger: String,
        expiryModified: Boolean
    ): Map<String, String> {
        val errors = mutableMapOf<String, String>()

        if (ruleName.isBlank()) {
            errors["ruleName"] = "Rule name is required"
        }

        if (signerEntries.isEmpty() && policyEntries.isEmpty()) {
            errors["signers"] = "At least one signer or policy must remain"
        }

        if (signerEntries.size > OZConstants.MAX_SIGNERS) {
            errors["signers"] = "Maximum ${OZConstants.MAX_SIGNERS} signers allowed"
        }

        if (policyEntries.size > OZConstants.MAX_POLICIES) {
            errors["policies"] = "Maximum ${OZConstants.MAX_POLICIES} policies allowed"
        }

        // Check for duplicate signers
        for (i in signerEntries.indices) {
            for (j in i + 1 until signerEntries.size) {
                if (SmartAccountBuilders.signersEqual(signerEntries[i].signer, signerEntries[j].signer)) {
                    errors["signers"] = "Duplicate signers detected"
                }
            }
        }

        // Expiry validation only when modified
        if (expiryModified && hasExpiry) {
            if (expiryLedger.isBlank()) {
                errors["expiryLedger"] = "Please select an expiry duration"
            } else {
                val ledgerNum = expiryLedger.toUIntOrNull()
                if (ledgerNum == null || ledgerNum == 0u) {
                    errors["expiryLedger"] = "Must be a positive integer"
                }
            }
        }

        return errors
    }
}

// ============================================================================
// Data Classes
// ============================================================================

/**
 * A known contract available for selection in the CallContract context type dropdown.
 */
private data class ContractOption(
    val label: String,
    val address: String
)


// ============================================================================
// Enums
// ============================================================================

/**
 * Context rule type options for the dropdown selector.
 */
private enum class ContextTypeOption(
    val displayName: String,
    val description: String
) {
    DEFAULT(
        "Default (Any Operation)",
        "Matches any operation that does not match a more specific rule"
    ),
    CALL_CONTRACT(
        "Call Contract",
        "Matches invocations to a specific contract address"
    ),
    CREATE_CONTRACT(
        "Create Contract",
        "Matches contract deployments using a specific WASM hash"
    )
}
