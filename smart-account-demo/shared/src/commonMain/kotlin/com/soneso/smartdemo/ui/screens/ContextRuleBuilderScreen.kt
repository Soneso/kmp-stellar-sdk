package com.soneso.smartdemo.ui.screens

/**
 * Context rule builder screen: form for creating or editing a context rule.
 *
 * Business logic is delegated to ContextRuleFlow.kt:
 * - Network calls (add, remove, update, load rules, resolve ledger)
 * - Signer construction (delegated, Ed25519, passkey registration)
 *
 * Policy SCVal encoding is handled by PolicyScValBuilders.kt.
 *
 * The screen imports SDK data types (ContextRuleType, SmartAccountSigner, etc.)
 * for rendering and form state representation, and also calls SmartAccountBuilders
 * utility methods for signer comparison and display.
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.soneso.smartdemo.config.DemoConfig
import com.soneso.smartdemo.config.KNOWN_POLICIES
import com.soneso.smartdemo.config.PolicyInfo
import com.soneso.smartdemo.flows.ContextRuleResult
import com.soneso.smartdemo.flows.FlowPolicyEntry
import com.soneso.smartdemo.flows.addContextRule
import com.soneso.smartdemo.flows.buildDelegatedSigner
import com.soneso.smartdemo.flows.buildEd25519Signer
import com.soneso.smartdemo.flows.loadAvailablePasskeySigners
import com.soneso.smartdemo.flows.loadContextRule
import com.soneso.smartdemo.flows.registerPasskeySigner
import com.soneso.smartdemo.flows.resolveAbsoluteLedger
import com.soneso.smartdemo.flows.updateContextRuleName
import com.soneso.smartdemo.flows.updateContextRuleValidUntil
import com.soneso.smartdemo.platform.getClipboard
import com.soneso.smartdemo.state.ActivityLogState
import com.soneso.smartdemo.state.DemoState
import com.soneso.smartdemo.util.buildSimpleThresholdScVal
import com.soneso.smartdemo.util.describeSignerType
import com.soneso.smartdemo.util.buildSpendingLimitScVal
import com.soneso.smartdemo.util.buildWeightedThresholdScVal
import com.soneso.smartdemo.util.formatSignerForDisplay
import com.soneso.smartdemo.util.hexToByteArray
import com.soneso.smartdemo.util.isUserCancellation
import com.soneso.smartdemo.util.isValidSpendingAmount
import com.soneso.smartdemo.util.parseSingleContextRuleFromScVal
import com.soneso.smartdemo.util.signerTypeColor
import com.soneso.smartdemo.util.toHexString
import com.soneso.smartdemo.util.truncateAddress
import com.soneso.stellar.sdk.smartaccount.core.ExternalSigner
import com.soneso.stellar.sdk.Util
import com.soneso.stellar.sdk.smartaccount.core.SmartAccountBuilders
import com.soneso.stellar.sdk.smartaccount.core.SmartAccountSigner
import com.soneso.stellar.sdk.smartaccount.oz.ContextRuleType
import com.soneso.stellar.sdk.smartaccount.oz.OZConstants
import com.soneso.stellar.sdk.xdr.SCValXdr
import kotlinx.coroutines.launch

/**
 * Screen for creating or editing context rules on a smart account.
 *
 * Supports two modes:
 * - Create mode (editRuleId = null): Fresh form for adding a new context rule.
 * - Edit mode (editRuleId != null): Pre-populates the form with data from an existing rule.
 *
 * Includes rule configuration, signer management, policy configuration, and transaction submission.
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
        // expiryLedger always stores a ledger offset selected from the dropdown.
        // In edit mode the on-chain absolute ledger is shown as read-only info text;
        // the user must re-select a duration offset to submit a new expiry.
        var expiryLedger by remember { mutableStateOf("") }
        var existingExpiryLedger by remember { mutableStateOf<UInt?>(null) }
        // Tracks whether the user modified the expiry from its loaded state in edit mode.
        // Only true after the user toggles the checkbox or selects a new duration.
        var expiryModified by remember { mutableStateOf(false) }

        // --- Signer management state ---
        var signers by remember { mutableStateOf<List<SmartAccountSigner>>(emptyList()) }
        var signerAddMode by remember { mutableStateOf(SignerAddMode.DELEGATED) }
        var delegatedAddress by remember { mutableStateOf("") }
        var ed25519PubKeyHex by remember { mutableStateOf("") }
        var isLoadingPasskeys by remember { mutableStateOf(false) }
        var isRegistering by remember { mutableStateOf(false) }
        var availablePasskeys by remember { mutableStateOf<List<ExternalSigner>>(emptyList()) }
        var passkeysLoaded by remember { mutableStateOf(false) }
        var newPasskeyName by remember { mutableStateOf("") }

        // --- Policy management state ---
        var policies by remember { mutableStateOf<List<PolicyEntry>>(emptyList()) }
        var selectedPolicyType by remember { mutableStateOf<PolicyInfo?>(null) }
        // Simple threshold fields
        var thresholdValue by remember { mutableStateOf("") }
        // Spending limit fields
        var spendingLimitAmount by remember { mutableStateOf("") }
        var spendingLimitPeriodDays by remember { mutableStateOf("") }
        // Weighted threshold fields
        var weightedThresholdValue by remember { mutableStateOf("") }
        var signerWeights by remember { mutableStateOf<Map<String, String>>(emptyMap()) }

        // --- Submission state ---
        var isSubmitting by remember { mutableStateOf(false) }
        var submissionResult by remember { mutableStateOf<ContextRuleResult?>(null) }

        // --- UI state ---
        var isLoadingRule by remember { mutableStateOf(false) }
        var errorMessage by remember { mutableStateOf<String?>(null) }
        var fieldErrors by remember { mutableStateOf<Map<String, String>>(emptyMap()) }

        // --- Edit mode: load existing rule ---
        LaunchedEffect(editRuleId) {
            if (editRuleId != null && DemoState.isConnected && DemoState.kit != null) {
                isLoadingRule = true
                errorMessage = null
                try {
                    val ruleScVal = loadContextRule(editRuleId)
                    val parsed = parseSingleContextRuleFromScVal(ruleScVal, editRuleId)
                    if (parsed != null) {
                        ruleName = parsed.name
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
                            // Do not pre-populate expiryLedger: the value from on-chain
                            // is an absolute ledger number and must not be treated as an
                            // offset at submission time. Show it as informational text only.
                            existingExpiryLedger = parsed.validUntil
                        }
                        signers = parsed.signers
                        // Pre-populate policies from existing rule (addresses only, params not available)
                        policies = parsed.policies.mapNotNull { addr ->
                            val known = KNOWN_POLICIES.find { it.address == addr }
                            if (known != null) {
                                PolicyEntry(info = known, label = known.name, address = addr)
                            } else {
                                PolicyEntry(
                                    info = null,
                                    label = "Unknown Policy",
                                    address = addr
                                )
                            }
                        }
                        ActivityLogState.info("Loaded rule #$editRuleId for editing")
                    } else {
                        errorMessage = "Failed to parse rule #$editRuleId"
                    }
                } catch (e: Exception) {
                    errorMessage = "Failed to load rule #$editRuleId: ${e.message}"
                    ActivityLogState.error("Failed to load rule: ${e.message}")
                } finally {
                    isLoadingRule = false
                }
            }
        }

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

                // --- Submission result ---
                if (submissionResult != null) {
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

                // --- Main form (hidden after successful submission) ---
                if (DemoState.isConnected && !isLoadingRule && submissionResult?.success != true) {

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

                    // Context Type Selector
                    ContextTypeSelector(
                        selectedOption = contextTypeOption,
                        onOptionSelected = { option ->
                            contextTypeOption = option
                            // Pre-select the first available contract when switching to CallContract
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
                        enabled = !isSubmitting
                    )

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
                                // Time-based expiry dropdown. The ledger number is computed
                                // from the selected duration when the rule is submitted.
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
                                                        // Store the ledger offset; actual ledger number is
                                                        // computed at submission time by adding to currentLedger.
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

                    // Signer and policy sections are only shown in create mode.
                    // In edit mode, only name and expiry can be updated.
                    // Signer/policy changes use separate SDK calls (addSigner, removeSigner, etc.)
                    if (!isEditing) {

                    // ====================================================================
                    // Section 1B: Signer Management
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
                            if (fieldErrors.containsKey("signers")) {
                                Text(
                                    text = fieldErrors["signers"]!!,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }

                    // Current Signers List
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
                                    signers = signers.toMutableList().also { it.removeAt(index) }
                                    // Remove any weighted threshold entries for removed signers
                                    val removedKey = SmartAccountBuilders.getSignerKey(signer)
                                    signerWeights = signerWeights - removedKey
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

                    // Add Signer Section
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
                                    onModeSelected = { signerAddMode = it }
                                )

                                when (signerAddMode) {
                                    SignerAddMode.DELEGATED -> {
                                        OutlinedTextField(
                                            value = delegatedAddress,
                                            onValueChange = {
                                                delegatedAddress = it
                                                fieldErrors = fieldErrors - "delegatedAddress"
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
                                                        val signerError = validateNewSigner(newSigner, signers)
                                                        if (signerError != null) {
                                                            errors["delegatedAddress"] = signerError
                                                        } else {
                                                            signers = signers + newSigner
                                                            delegatedAddress = ""
                                                            ActivityLogState.info(
                                                                "Added delegated signer: ${truncateAddress(addr, 6)}"
                                                            )
                                                        }
                                                    }
                                                }
                                                fieldErrors = fieldErrors - "delegatedAddress" + errors
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
                                                ed25519PubKeyHex = it
                                                fieldErrors = fieldErrors - "ed25519PublicKey"
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
                                                        val signerError = validateNewSigner(newSigner, signers)
                                                        if (signerError != null) {
                                                            errors["ed25519PublicKey"] = signerError
                                                        } else {
                                                            signers = signers + newSigner
                                                            ed25519PubKeyHex = ""
                                                            ActivityLogState.info(
                                                                "Added Ed25519 signer: ${hex.take(8)}..."
                                                            )
                                                        }
                                                    }
                                                }
                                                fieldErrors = fieldErrors - "ed25519PublicKey" + errors
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
                                                    text = "You can reuse an account signer that is already stored in an existing " +
                                                            "context rule, or register a new passkey signer for this context rule.",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )

                                                // Load passkey signers from on-chain context rules, excluding
                                                // the wallet owner's passkey (it's on the Default rule and adding
                                                // it to another rule has no effect — Default already authorizes it).
                                                val connectedCredentialId = DemoState.credentialId
                                                Button(
                                                    onClick = {
                                                        scope.launch {
                                                            isLoadingPasskeys = true
                                                            try {
                                                                availablePasskeys = loadAvailablePasskeySigners(
                                                                    excludeCredentialId = connectedCredentialId
                                                                )
                                                                passkeysLoaded = true
                                                                if (availablePasskeys.isEmpty()) {
                                                                    ActivityLogState.info("No additional passkey signers found")
                                                                }
                                                            } catch (e: Throwable) {
                                                                ActivityLogState.error("Failed to load passkeys: ${e.message}")
                                                            } finally {
                                                                isLoadingPasskeys = false
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
                                                                        fieldErrors = fieldErrors + ("signers" to signerError)
                                                                    } else {
                                                                        signers = signers + passkey
                                                                        fieldErrors = fieldErrors - "signers"
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
                                                    onValueChange = { newPasskeyName = it },
                                                    label = { Text("Passkey Name") },
                                                    placeholder = { Text("e.g., Recovery Key") },
                                                    modifier = Modifier.fillMaxWidth(),
                                                    singleLine = true,
                                                    enabled = !isRegistering && !isSubmitting
                                                )
                                                Button(
                                                    onClick = {
                                                        scope.launch {
                                                            isRegistering = true
                                                            try {
                                                                val newSigner = registerPasskeySigner(newPasskeyName)

                                                                val signerError = validateNewSigner(newSigner, signers)
                                                                if (signerError != null) {
                                                                    fieldErrors = fieldErrors + ("signers" to signerError)
                                                                    ActivityLogState.info(signerError)
                                                                } else {
                                                                    signers = signers + newSigner
                                                                    fieldErrors = fieldErrors - "signers"
                                                                    // Also add to available list so it shows as "already added"
                                                                    availablePasskeys = availablePasskeys + newSigner
                                                                    passkeysLoaded = true
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
                                                                isRegistering = false
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

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 4.dp),
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                    )

                    // ====================================================================
                    // Section 2A: Policy Configuration
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
                                text = "Policies",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "Attach policies to constrain how operations are authorized. " +
                                        "Policies are optional. Maximum ${OZConstants.MAX_POLICIES} per rule.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Current Policies List
                    if (policies.isEmpty()) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            )
                        ) {
                            Text(
                                text = "No policies attached. Policies are optional.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    } else {
                        policies.forEachIndexed { index, policy ->
                            PolicyCard(
                                policy = policy,
                                onRemove = {
                                    policies = policies.toMutableList().also { it.removeAt(index) }
                                },
                                enabled = !isSubmitting
                            )
                        }
                        Text(
                            text = "${policies.size} ${if (policies.size == 1) "policy" else "policies"} attached",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Add Policy Section
                    if (!isSubmitting && policies.size < OZConstants.MAX_POLICIES) {
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
                                    text = "Add Policy",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )

                                // Policy Type Selector
                                PolicyTypeSelector(
                                    selectedPolicy = selectedPolicyType,
                                    onPolicySelected = {
                                        selectedPolicyType = it
                                        // Reset fields when switching type
                                        thresholdValue = ""
                                        spendingLimitAmount = ""
                                        spendingLimitPeriodDays = ""
                                        weightedThresholdValue = ""
                                        signerWeights = emptyMap()
                                        fieldErrors = fieldErrors - "threshold" - "spendingAmount" -
                                                "spendingPeriod" - "weightedThreshold" - "signerWeights" - "policy"
                                    },
                                    availablePolicies = KNOWN_POLICIES.filter { known ->
                                        policies.none { it.address == known.address }
                                    }
                                )

                                // Policy-specific fields
                                when (selectedPolicyType?.type) {
                                    "threshold" -> {
                                        // Simple Threshold
                                        Text(
                                            text = "Contract: ${truncateAddress(selectedPolicyType!!.address, 8)}",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontFamily = FontFamily.Monospace,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        OutlinedTextField(
                                            value = thresholdValue,
                                            onValueChange = { value ->
                                                thresholdValue = value.filter { it.isDigit() }
                                                fieldErrors = fieldErrors - "threshold"
                                            },
                                            label = { Text("Threshold (required signers)") },
                                            placeholder = { Text("e.g., 2") },
                                            modifier = Modifier.fillMaxWidth(),
                                            singleLine = true,
                                            isError = fieldErrors.containsKey("threshold"),
                                            supportingText = if (fieldErrors.containsKey("threshold")) {
                                                { Text(fieldErrors["threshold"]!!) }
                                            } else {
                                                { Text("Number of signers required to authorize (1 to ${signers.size.coerceAtLeast(1)})") }
                                            }
                                        )
                                        Button(
                                            onClick = {
                                                val errors = mutableMapOf<String, String>()
                                                val t = thresholdValue.toUIntOrNull()
                                                if (t == null || t == 0u || t > 15u) {
                                                    errors["threshold"] = "Must be between 1 and 15"
                                                } else if (signers.isNotEmpty() && t > signers.size.toUInt()) {
                                                    errors["threshold"] = "Cannot exceed signer count (${signers.size})"
                                                } else {
                                                    val scVal = buildSimpleThresholdScVal(t)
                                                    policies = policies + PolicyEntry(
                                                        info = selectedPolicyType!!,
                                                        label = "Threshold: $t-of-N",
                                                        address = selectedPolicyType!!.address,
                                                        scVal = scVal
                                                    )
                                                    thresholdValue = ""
                                                    selectedPolicyType = null
                                                    ActivityLogState.info("Added simple threshold policy (threshold=$t)")
                                                }
                                                fieldErrors = fieldErrors - "threshold" + errors
                                            },
                                            modifier = Modifier.fillMaxWidth(),
                                            enabled = thresholdValue.isNotBlank()
                                        ) {
                                            Text("Add Threshold Policy")
                                        }
                                    }

                                    "spending_limit" -> {
                                        // Spending Limit
                                        Text(
                                            text = "Contract: ${truncateAddress(selectedPolicyType!!.address, 8)}",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontFamily = FontFamily.Monospace,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        OutlinedTextField(
                                            value = spendingLimitAmount,
                                            onValueChange = { value ->
                                                // Allow digits and one decimal point
                                                val filtered = value.filter { it.isDigit() || it == '.' }
                                                if (filtered.count { it == '.' } <= 1) {
                                                    spendingLimitAmount = filtered
                                                }
                                                fieldErrors = fieldErrors - "spendingAmount"
                                            },
                                            label = { Text("Amount") },
                                            placeholder = { Text("e.g., 100.0") },
                                            modifier = Modifier.fillMaxWidth(),
                                            singleLine = true,
                                            isError = fieldErrors.containsKey("spendingAmount"),
                                            supportingText = if (fieldErrors.containsKey("spendingAmount")) {
                                                { Text(fieldErrors["spendingAmount"]!!) }
                                            } else {
                                                { Text("Maximum amount allowed per period") }
                                            }
                                        )
                                        OutlinedTextField(
                                            value = spendingLimitPeriodDays,
                                            onValueChange = { value ->
                                                spendingLimitPeriodDays = value.filter { it.isDigit() }
                                                fieldErrors = fieldErrors - "spendingPeriod"
                                            },
                                            label = { Text("Period (days)") },
                                            placeholder = { Text("e.g., 1") },
                                            modifier = Modifier.fillMaxWidth(),
                                            singleLine = true,
                                            isError = fieldErrors.containsKey("spendingPeriod"),
                                            supportingText = if (fieldErrors.containsKey("spendingPeriod")) {
                                                { Text(fieldErrors["spendingPeriod"]!!) }
                                            } else {
                                                {
                                                    val days = spendingLimitPeriodDays.toIntOrNull() ?: 0
                                                    val ledgers = days * Util.LEDGERS_PER_DAY
                                                    if (days > 0) {
                                                        Text("$days day(s) = $ledgers ledgers")
                                                    } else {
                                                        Text("The spending limit resets after this period. Example: amount 100 with period 1 means max 100 tokens per day.")
                                                    }
                                                }
                                            }
                                        )
                                        Button(
                                            onClick = {
                                                val errors = mutableMapOf<String, String>()
                                                val days = spendingLimitPeriodDays.toIntOrNull()
                                                if (!isValidSpendingAmount(spendingLimitAmount)) {
                                                    errors["spendingAmount"] = "Must be a positive number"
                                                }
                                                if (days == null || days <= 0) {
                                                    errors["spendingPeriod"] = "Must be at least 1 day"
                                                }
                                                if (errors.isEmpty()) {
                                                    val periodLedgers = (days!! * Util.LEDGERS_PER_DAY).toUInt()
                                                    val scVal = buildSpendingLimitScVal(spendingLimitAmount, periodLedgers)
                                                    // Capture values before clearing state so the log message is correct.
                                                    val logAmount = spendingLimitAmount
                                                    policies = policies + PolicyEntry(
                                                        info = selectedPolicyType!!,
                                                        label = "Limit: $spendingLimitAmount / $days day(s)",
                                                        address = selectedPolicyType!!.address,
                                                        scVal = scVal
                                                    )
                                                    spendingLimitAmount = ""
                                                    spendingLimitPeriodDays = ""
                                                    selectedPolicyType = null
                                                    ActivityLogState.info(
                                                        "Added spending limit policy ($logAmount per $days day(s))"
                                                    )
                                                }
                                                fieldErrors = fieldErrors - "spendingAmount" - "spendingPeriod" + errors
                                            },
                                            modifier = Modifier.fillMaxWidth(),
                                            enabled = spendingLimitAmount.isNotBlank() &&
                                                    spendingLimitPeriodDays.isNotBlank()
                                        ) {
                                            Text("Add Spending Limit Policy")
                                        }
                                    }

                                    "weighted_threshold" -> {
                                        // Weighted Threshold
                                        Text(
                                            text = "Contract: ${truncateAddress(selectedPolicyType!!.address, 8)}",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontFamily = FontFamily.Monospace,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        OutlinedTextField(
                                            value = weightedThresholdValue,
                                            onValueChange = { value ->
                                                weightedThresholdValue = value.filter { it.isDigit() }
                                                fieldErrors = fieldErrors - "weightedThreshold"
                                            },
                                            label = { Text("Weight Threshold") },
                                            placeholder = { Text("e.g., 100") },
                                            modifier = Modifier.fillMaxWidth(),
                                            singleLine = true,
                                            isError = fieldErrors.containsKey("weightedThreshold"),
                                            supportingText = if (fieldErrors.containsKey("weightedThreshold")) {
                                                { Text(fieldErrors["weightedThreshold"]!!) }
                                            } else {
                                                { Text("Minimum total weight required for authorization") }
                                            }
                                        )

                                        // Per-signer weight fields
                                        if (signers.isEmpty()) {
                                            Text(
                                                text = "Add signers above to configure per-signer weights.",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        } else {
                                            Text(
                                                text = "Per-Signer Weights",
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.Bold
                                            )
                                            signers.forEach { signer ->
                                                val key = SmartAccountBuilders.getSignerKey(signer)
                                                val displayInfo = formatSignerForDisplay(signer)
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Text(
                                                            text = "${displayInfo.type}: ${displayInfo.display}",
                                                            style = MaterialTheme.typography.bodySmall,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                    }
                                                    OutlinedTextField(
                                                        value = signerWeights[key] ?: "",
                                                        onValueChange = { value ->
                                                            signerWeights = signerWeights + (key to value.filter { it.isDigit() })
                                                            fieldErrors = fieldErrors - "signerWeights"
                                                        },
                                                        label = { Text("Weight") },
                                                        modifier = Modifier.width(100.dp),
                                                        singleLine = true
                                                    )
                                                }
                                            }
                                        }

                                        Button(
                                            onClick = {
                                                val errors = mutableMapOf<String, String>()
                                                val threshold = weightedThresholdValue.toUIntOrNull()
                                                if (threshold == null || threshold == 0u) {
                                                    errors["weightedThreshold"] = "Must be at least 1"
                                                }
                                                if (signers.isEmpty()) {
                                                    errors["signerWeights"] = "Add signers before configuring weights"
                                                } else {
                                                    // Validate all signers have weights
                                                    var allHaveWeights = true
                                                    var totalWeight = 0u
                                                    for (signer in signers) {
                                                        val key = SmartAccountBuilders.getSignerKey(signer)
                                                        val w = signerWeights[key]?.toUIntOrNull()
                                                        if (w == null || w == 0u) {
                                                            allHaveWeights = false
                                                            break
                                                        }
                                                        totalWeight += w
                                                    }
                                                    if (!allHaveWeights) {
                                                        errors["signerWeights"] = "All signers must have a weight >= 1"
                                                    } else if (threshold != null && totalWeight < threshold) {
                                                        errors["signerWeights"] =
                                                            "Total weight ($totalWeight) must be >= threshold ($threshold)"
                                                    }
                                                }
                                                if (errors.isEmpty() && threshold != null) {
                                                    val weightsMap = linkedMapOf<SmartAccountSigner, UInt>()
                                                    for (signer in signers) {
                                                        val key = SmartAccountBuilders.getSignerKey(signer)
                                                        val w = signerWeights[key]!!.toUInt()
                                                        weightsMap[signer] = w
                                                    }
                                                    val scVal = buildWeightedThresholdScVal(weightsMap, threshold)
                                                    val weightsDesc = weightsMap.entries.joinToString(", ") { (s, w) ->
                                                        val info = formatSignerForDisplay(s)
                                                        "${info.type}=$w"
                                                    }
                                                    policies = policies + PolicyEntry(
                                                        info = selectedPolicyType!!,
                                                        label = "Weighted: threshold=$threshold ($weightsDesc)",
                                                        address = selectedPolicyType!!.address,
                                                        scVal = scVal
                                                    )
                                                    weightedThresholdValue = ""
                                                    signerWeights = emptyMap()
                                                    selectedPolicyType = null
                                                    ActivityLogState.info(
                                                        "Added weighted threshold policy (threshold=$threshold)"
                                                    )
                                                }
                                                fieldErrors = fieldErrors - "weightedThreshold" - "signerWeights" + errors
                                            },
                                            modifier = Modifier.fillMaxWidth(),
                                            enabled = weightedThresholdValue.isNotBlank() && signers.isNotEmpty()
                                        ) {
                                            Text("Add Weighted Threshold Policy")
                                        }
                                    }

                                    else -> {
                                        // No policy type selected yet
                                        Text(
                                            text = "Select a policy type above to configure parameters.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }

                    } // end if (!isEditing) — signer and policy sections

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 4.dp),
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                    )

                    // ====================================================================
                    // Section 2B: Submission
                    // ====================================================================

                    Button(
                        onClick = {
                            // Validate the form before submission
                            val errors = validateForm(
                                ruleName = ruleName,
                                contextTypeOption = contextTypeOption,
                                contractAddress = contractAddress,
                                wasmHashHex = wasmHashHex,
                                hasExpiry = hasExpiry,
                                expiryLedger = expiryLedger,
                                signers = signers,
                                isEditing = isEditing,
                                expiryModified = expiryModified
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
                                    if (isEditing) {
                                        // Edit mode: update name then validUntil via flow functions.
                                        // Signer and policy changes require separate SDK calls.
                                        ActivityLogState.info("Updating rule #$editRuleId...")

                                        val nameResult = updateContextRuleName(editRuleId!!, ruleName.trim())
                                        if (!nameResult.success) {
                                            submissionResult = ContextRuleResult(
                                                success = false,
                                                hash = null,
                                                error = "Failed to update name: ${nameResult.error ?: "Unknown error"}"
                                            )
                                            return@launch
                                        }

                                        if (expiryModified) {
                                            val validUntilVal = resolveExpiryLedger(hasExpiry, expiryLedger)
                                            val validUntilResult = updateContextRuleValidUntil(editRuleId, validUntilVal)
                                            if (!validUntilResult.success) {
                                                submissionResult = ContextRuleResult(
                                                    success = false,
                                                    hash = null,
                                                    error = "Name updated but failed to update expiry: ${validUntilResult.error ?: "Unknown error"}"
                                                )
                                                return@launch
                                            }
                                            submissionResult = ContextRuleResult(
                                                success = true,
                                                hash = validUntilResult.hash,
                                                error = null
                                            )
                                            ActivityLogState.info(
                                                "Rule #$editRuleId updated successfully. Hash: ${validUntilResult.hash ?: "N/A"}"
                                            )
                                        } else {
                                            submissionResult = ContextRuleResult(
                                                success = true,
                                                hash = nameResult.hash,
                                                error = null
                                            )
                                            ActivityLogState.info(
                                                "Rule #$editRuleId updated successfully. Hash: ${nameResult.hash ?: "N/A"}"
                                            )
                                        }
                                    } else {
                                        // Create mode: add new context rule via flow
                                        val selectedContextType = when (contextTypeOption) {
                                            ContextTypeOption.DEFAULT -> ContextRuleType.Default
                                            ContextTypeOption.CALL_CONTRACT ->
                                                ContextRuleType.CallContract(contractAddress.trim())
                                            ContextTypeOption.CREATE_CONTRACT ->
                                                ContextRuleType.CreateContract(
                                                    hexToByteArray(wasmHashHex.trim().lowercase())
                                                )
                                        }

                                        val validUntilVal = resolveExpiryLedger(hasExpiry, expiryLedger)

                                        // Convert PolicyEntry list to FlowPolicyEntry for the flow
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
                                    }
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
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = DemoState.isConnected && !isSubmitting &&
                                ruleName.isNotBlank() && (isEditing || signers.isNotEmpty()) &&
                                submissionResult?.success != true
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
                                if (isEditing) "Update Context Rule" else "Create Context Rule"
                            )
                        }
                    }

                    if (isSubmitting) {
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

        // Build the list of available contract options. The DEMO token option is only included
        // when the token contract has been deployed and its address is known.
        val contractOptions = buildList {
            add(ContractOption("XLM Native Contract", DemoConfig.NATIVE_TOKEN_CONTRACT))
            val demoTokenId = DemoState.demoTokenContractId
            if (demoTokenId != null) {
                add(ContractOption("Demo Token Contract", demoTokenId))
            }
        }

        // Resolve the display label for the currently selected contract address.
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

            // Contract selection dropdown for CallContract
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

            // Additional field for CreateContract
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

    /**
     * Mode selector for adding different signer types.
     */
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun SignerModeSelector(
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
    private fun SignerCard(
        signer: SmartAccountSigner,
        onRemove: () -> Unit,
        enabled: Boolean = true
    ) {
        val typeDescription = describeSignerType(signer)
        val displayInfo = formatSignerForDisplay(signer)

        val chipColor = signerTypeColor(typeDescription)

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = chipColor.copy(alpha = 0.08f)
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    // Type badge
                    Surface(
                        color = chipColor,
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text(
                            text = displayInfo.type,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White
                        )
                    }

                    // Identifier
                    Text(
                        text = displayInfo.display,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

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
     * Dropdown selector for policy type.
     */
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun PolicyTypeSelector(
        selectedPolicy: PolicyInfo?,
        onPolicySelected: (PolicyInfo?) -> Unit,
        availablePolicies: List<PolicyInfo>
    ) {
        var expanded by remember { mutableStateOf(false) }

        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it }
        ) {
            OutlinedTextField(
                value = selectedPolicy?.name ?: "Select policy type...",
                onValueChange = {},
                readOnly = true,
                label = { Text("Policy Type") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier
                    .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                    .fillMaxWidth()
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                if (availablePolicies.isEmpty()) {
                    DropdownMenuItem(
                        text = {
                            Text(
                                "All policy types already added",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        onClick = { expanded = false },
                        enabled = false
                    )
                } else {
                    availablePolicies.forEach { policy ->
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(policy.name, style = MaterialTheme.typography.bodyMedium)
                                    Text(
                                        policy.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            },
                            onClick = {
                                onPolicySelected(policy)
                                expanded = false
                            }
                        )
                    }
                }
            }
        }
    }

    /**
     * Card displaying an attached policy with type badge and remove button.
     */
    @Composable
    private fun PolicyCard(
        policy: PolicyEntry,
        onRemove: () -> Unit,
        enabled: Boolean = true
    ) {
        val chipColor = when (policy.info?.type) {
            "threshold" -> Color(0xFFE65100)
            "spending_limit" -> Color(0xFF1565C0)
            "weighted_threshold" -> Color(0xFF6A1B9A)
            else -> Color(0xFF607D8B)
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = chipColor.copy(alpha = 0.08f)
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, top = 8.dp, bottom = 8.dp, end = 4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        // Type badge
                        Surface(
                            color = chipColor,
                            shape = MaterialTheme.shapes.small
                        ) {
                            Text(
                                text = policy.info?.name ?: "Policy",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White
                            )
                        }

                        // Policy label
                        Text(
                            text = policy.label,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Remove button
                    if (enabled) {
                        IconButton(
                            onClick = onRemove,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Remove policy",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
                // Address
                Text(
                    text = truncateAddress(policy.address, 8),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, top = 4.dp)
                )
            }
        }
    }

    // ========================================================================
    // Helpers
    // ========================================================================

    /**
     * Returns true if [newSigner] is already present in [signers] by identity,
     * using [SmartAccountBuilders.signersEqual] for comparison.
     */
    private fun isDuplicateSigner(
        signers: List<SmartAccountSigner>,
        newSigner: SmartAccountSigner
    ): Boolean = signers.any { SmartAccountBuilders.signersEqual(it, newSigner) }

    /**
     * Validates that [signer] can be added to [signers].
     *
     * Checks the maximum signer limit and duplicate identity in a single call.
     * Returns an error message string, or null if the signer is valid to add.
     */
    private fun validateNewSigner(
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
     * Converts the expiry form state to an absolute ledger number, or returns null
     * if no expiry is set.
     *
     * [expiryLedger] always holds a ledger offset selected from the duration dropdown.
     * The absolute ledger is resolved by fetching the current ledger from the RPC and
     * adding the offset.
     *
     * @param hasExpiry Whether the expiry checkbox is enabled.
     * @param expiryLedger Ledger offset string from the dropdown, or empty if none selected.
     * @return Absolute ledger number, or null if [hasExpiry] is false or the field is empty.
     */
    private suspend fun resolveExpiryLedger(hasExpiry: Boolean, expiryLedger: String): UInt? {
        if (!hasExpiry) return null
        val offset = expiryLedger.toUIntOrNull() ?: return null
        return resolveAbsoluteLedger(offset)
    }

    // ========================================================================
    // Validation
    // ========================================================================

    /**
     * Validates the complete form and returns a map of field name to error message.
     * An empty map means the form is valid.
     */
    private fun validateForm(
        ruleName: String,
        contextTypeOption: ContextTypeOption,
        contractAddress: String,
        wasmHashHex: String,
        hasExpiry: Boolean,
        expiryLedger: String,
        signers: List<SmartAccountSigner>,
        isEditing: Boolean = false,
        expiryModified: Boolean = true
    ): Map<String, String> {
        val errors = mutableMapOf<String, String>()

        // Rule name
        if (ruleName.isBlank()) {
            errors["ruleName"] = "Rule name is required"
        }

        // Context type specific
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

        // Expiry: skip validation in edit mode when the user hasn't changed the expiry,
        // since the existing on-chain value will be kept as-is.
        if (hasExpiry && !(isEditing && !expiryModified)) {
            if (expiryLedger.isBlank()) {
                errors["expiryLedger"] = "Please select an expiry duration"
            } else {
                val ledgerNum = expiryLedger.toUIntOrNull()
                if (ledgerNum == null || ledgerNum == 0u) {
                    errors["expiryLedger"] = "Must be a positive integer"
                }
            }
        }

        // Signers: in edit mode the signer section is hidden, so skip this check.
        if (!isEditing && signers.isEmpty()) {
            errors["signers"] = "At least one signer is required"
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

/**
 * Represents a policy attached to the current rule being built.
 */
private data class PolicyEntry(
    val info: PolicyInfo?,
    val label: String,
    val address: String,
    val scVal: SCValXdr? = null
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

/**
 * Signer add mode options for the dropdown selector.
 */
private enum class SignerAddMode(
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
