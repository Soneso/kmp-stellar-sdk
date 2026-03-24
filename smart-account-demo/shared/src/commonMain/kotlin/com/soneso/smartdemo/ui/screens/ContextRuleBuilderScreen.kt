package com.soneso.smartdemo.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
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
import com.soneso.smartdemo.state.ActivityLogState
import com.soneso.smartdemo.state.DemoState
import com.soneso.stellar.sdk.scval.Scv
import com.soneso.stellar.sdk.smartaccount.core.DelegatedSigner
import com.soneso.stellar.sdk.smartaccount.core.ExternalSigner
import com.soneso.stellar.sdk.smartaccount.core.SmartAccountBuilders
import com.soneso.stellar.sdk.smartaccount.core.SmartAccountConstants
import com.soneso.stellar.sdk.smartaccount.core.SmartAccountSigner
import com.soneso.stellar.sdk.smartaccount.oz.ContextRuleType
import com.soneso.stellar.sdk.smartaccount.oz.SmartAccountSharedUtils
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

        val isEditing = editRuleId != null

        // --- Rule configuration state ---
        var ruleName by remember { mutableStateOf("") }
        var contextTypeOption by remember { mutableStateOf(ContextTypeOption.DEFAULT) }
        var contractAddress by remember { mutableStateOf("") }
        var wasmHashHex by remember { mutableStateOf("") }
        var hasExpiry by remember { mutableStateOf(false) }
        var expiryLedger by remember { mutableStateOf("") }

        // --- Signer management state ---
        var signers by remember { mutableStateOf<List<SmartAccountSigner>>(emptyList()) }
        var signerAddMode by remember { mutableStateOf(SignerAddMode.DELEGATED) }
        var delegatedAddress by remember { mutableStateOf("") }
        var ed25519PubKeyHex by remember { mutableStateOf("") }

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
        var submissionResult by remember { mutableStateOf<SubmissionResult?>(null) }

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
                    val kit = DemoState.kit!!
                    val ruleScVal = kit.contextRuleManager.getContextRule(editRuleId)
                    val parsed = parseRuleFromScVal(ruleScVal, editRuleId)
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
                            expiryLedger = parsed.validUntil.toString()
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
                        Text(
                            text = errorMessage!!,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(16.dp)
                        )
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
                                        MaterialTheme.colorScheme.onErrorContainer
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

                // --- Main form (visible when connected and not loading) ---
                if (DemoState.isConnected && !isLoadingRule) {

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
                                        if (!it) {
                                            expiryLedger = ""
                                            fieldErrors = fieldErrors - "expiryLedger"
                                        }
                                    },
                                    enabled = !isSubmitting
                                )
                                Text(
                                    text = "Set Expiry (Valid Until Ledger)",
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(start = 8.dp)
                                )
                            }
                            AnimatedVisibility(
                                visible = hasExpiry,
                                enter = expandVertically(),
                                exit = shrinkVertically()
                            ) {
                                Column(
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    OutlinedTextField(
                                        value = expiryLedger,
                                        onValueChange = { value ->
                                            // Allow only digits
                                            expiryLedger = value.filter { it.isDigit() }
                                            fieldErrors = fieldErrors - "expiryLedger"
                                        },
                                        label = { Text("Ledger Number") },
                                        placeholder = { Text("e.g., 12345678") },
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true,
                                        enabled = !isSubmitting,
                                        isError = fieldErrors.containsKey("expiryLedger"),
                                        supportingText = if (fieldErrors.containsKey("expiryLedger")) {
                                            { Text(fieldErrors["expiryLedger"]!!) }
                                        } else null
                                    )
                                    Text(
                                        text = "Hint: ~${SmartAccountConstants.LEDGERS_PER_DAY} ledgers per day " +
                                                "(~${SmartAccountConstants.LEDGERS_PER_HOUR} per hour). " +
                                                "30 days = ~${SmartAccountConstants.LEDGERS_PER_DAY * 30} ledgers.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 4.dp),
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                    )

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
                                        "At least one signer is required. Maximum ${SmartAccountConstants.MAX_SIGNERS}.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
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
                                                    // Check for duplicates
                                                    val newSigner = try {
                                                        DelegatedSigner(addr)
                                                    } catch (e: Exception) {
                                                        errors["delegatedAddress"] = "Invalid address: ${e.message}"
                                                        null
                                                    }
                                                    if (newSigner != null) {
                                                        val isDuplicate = signers.any {
                                                            SmartAccountBuilders.signersEqual(it, newSigner)
                                                        }
                                                        if (isDuplicate) {
                                                            errors["delegatedAddress"] = "This signer is already added"
                                                        } else if (signers.size >= SmartAccountConstants.MAX_SIGNERS) {
                                                            errors["delegatedAddress"] =
                                                                "Maximum ${SmartAccountConstants.MAX_SIGNERS} signers allowed"
                                                        } else {
                                                            signers = signers + newSigner
                                                            delegatedAddress = ""
                                                            ActivityLogState.info(
                                                                "Added delegated signer: ${SmartAccountBuilders.truncateAddress(addr, 6)}"
                                                            )
                                                        }
                                                    }
                                                }
                                                fieldErrors = errors
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
                                                fieldErrors = fieldErrors - "ed25519PubKey"
                                            },
                                            label = { Text("Ed25519 Public Key (hex)") },
                                            placeholder = { Text("64 hex characters") },
                                            modifier = Modifier.fillMaxWidth(),
                                            singleLine = true,
                                            isError = fieldErrors.containsKey("ed25519PubKey"),
                                            supportingText = if (fieldErrors.containsKey("ed25519PubKey")) {
                                                { Text(fieldErrors["ed25519PubKey"]!!) }
                                            } else null
                                        )
                                        Text(
                                            text = "Uses verifier: ${SmartAccountBuilders.truncateAddress(DemoConfig.ED25519_VERIFIER_ADDRESS, 6)}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Button(
                                            onClick = {
                                                val errors = mutableMapOf<String, String>()
                                                val hex = ed25519PubKeyHex.trim().lowercase()
                                                if (hex.isEmpty()) {
                                                    errors["ed25519PubKey"] = "Public key is required"
                                                } else if (hex.length != 64) {
                                                    errors["ed25519PubKey"] =
                                                        "Must be 64 hex characters (32 bytes), got ${hex.length}"
                                                } else if (!hex.all { it in '0'..'9' || it in 'a'..'f' }) {
                                                    errors["ed25519PubKey"] = "Invalid hex characters"
                                                } else {
                                                    val pubKeyBytes = hexToByteArray(hex)
                                                    val newSigner = try {
                                                        ExternalSigner.ed25519(
                                                            verifierAddress = DemoConfig.ED25519_VERIFIER_ADDRESS,
                                                            publicKey = pubKeyBytes
                                                        )
                                                    } catch (e: Exception) {
                                                        errors["ed25519PubKey"] = "Invalid key: ${e.message}"
                                                        null
                                                    }
                                                    if (newSigner != null) {
                                                        val isDuplicate = signers.any {
                                                            SmartAccountBuilders.signersEqual(it, newSigner)
                                                        }
                                                        if (isDuplicate) {
                                                            errors["ed25519PubKey"] = "This signer is already added"
                                                        } else if (signers.size >= SmartAccountConstants.MAX_SIGNERS) {
                                                            errors["ed25519PubKey"] =
                                                                "Maximum ${SmartAccountConstants.MAX_SIGNERS} signers allowed"
                                                        } else {
                                                            signers = signers + newSigner
                                                            ed25519PubKeyHex = ""
                                                            ActivityLogState.info(
                                                                "Added Ed25519 signer: ${hex.take(8)}..."
                                                            )
                                                        }
                                                    }
                                                }
                                                fieldErrors = errors
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
                                                    text = "Passkey registration requires a WebAuthn ceremony on the " +
                                                            "platform. The wallet's primary passkey was registered " +
                                                            "during wallet creation. To add additional passkey signers, " +
                                                            "use the wallet creation flow or the external signer manager.",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                Text(
                                                    text = "Uses verifier: ${SmartAccountBuilders.truncateAddress(DemoConfig.WEBAUTHN_VERIFIER_ADDRESS, 6)}",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                OutlinedButton(
                                                    onClick = {
                                                        ActivityLogState.info(
                                                            "Passkey registration requires a platform-specific WebAuthn ceremony. " +
                                                                    "Use the Wallet Creation screen or External Signer Manager to register new passkeys."
                                                        )
                                                    },
                                                    modifier = Modifier.fillMaxWidth()
                                                ) {
                                                    Text("Register New Passkey (Not Available Here)")
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
                                        "Policies are optional. Maximum ${SmartAccountConstants.MAX_POLICIES} per rule.",
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
                            text = "${policies.size} policy/policies attached",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Add Policy Section
                    if (!isSubmitting && policies.size < SmartAccountConstants.MAX_POLICIES) {
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
                                            text = "Contract: ${SmartAccountBuilders.truncateAddress(selectedPolicyType!!.address, 8)}",
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
                                                fieldErrors = errors
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
                                            text = "Contract: ${SmartAccountBuilders.truncateAddress(selectedPolicyType!!.address, 8)}",
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
                                                    val ledgers = days * SmartAccountConstants.LEDGERS_PER_DAY
                                                    if (days > 0) {
                                                        Text("$days day(s) = $ledgers ledgers")
                                                    } else {
                                                        Text("Number of days for the spending period")
                                                    }
                                                }
                                            }
                                        )
                                        Button(
                                            onClick = {
                                                val errors = mutableMapOf<String, String>()
                                                val amount = spendingLimitAmount.toDoubleOrNull()
                                                val days = spendingLimitPeriodDays.toIntOrNull()
                                                if (amount == null || amount <= 0.0) {
                                                    errors["spendingAmount"] = "Must be a positive number"
                                                }
                                                if (days == null || days <= 0) {
                                                    errors["spendingPeriod"] = "Must be at least 1 day"
                                                }
                                                if (errors.isEmpty()) {
                                                    val stroops = SmartAccountSharedUtils.amountToStroops(amount!!)
                                                    val periodLedgers = (days!! * SmartAccountConstants.LEDGERS_PER_DAY).toUInt()
                                                    val scVal = buildSpendingLimitScVal(stroops, periodLedgers)
                                                    policies = policies + PolicyEntry(
                                                        info = selectedPolicyType!!,
                                                        label = "Limit: $amount / $days day(s)",
                                                        address = selectedPolicyType!!.address,
                                                        scVal = scVal
                                                    )
                                                    spendingLimitAmount = ""
                                                    spendingLimitPeriodDays = ""
                                                    selectedPolicyType = null
                                                    ActivityLogState.info(
                                                        "Added spending limit policy ($amount per $days day(s))"
                                                    )
                                                }
                                                fieldErrors = errors
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
                                            text = "Contract: ${SmartAccountBuilders.truncateAddress(selectedPolicyType!!.address, 8)}",
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
                                                val displayInfo = SmartAccountBuilders.formatSignerForDisplay(signer)
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
                                                        val info = SmartAccountBuilders.formatSignerForDisplay(s)
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
                                                fieldErrors = errors
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

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 4.dp),
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                    )

                    // ====================================================================
                    // Section 2B: Submission
                    // ====================================================================

                    if (isEditing) {
                        // Edit mode: update name and validUntil only
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = Color(0xFFFF9800).copy(alpha = 0.08f)
                            )
                        ) {
                            Text(
                                text = "Edit mode updates name and expiry only. Signer and policy " +
                                        "changes require individual add/remove operations via the " +
                                        "Signer Manager and Policy Manager screens.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }

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
                                    val kit = DemoState.kit!!

                                    if (isEditing) {
                                        // Edit mode: update name and validUntil
                                        ActivityLogState.info("Updating rule #$editRuleId...")

                                        val nameResult = kit.contextRuleManager.updateName(
                                            id = editRuleId!!,
                                            name = ruleName.trim()
                                        )
                                        if (!nameResult.success) {
                                            submissionResult = SubmissionResult(
                                                success = false,
                                                error = "Failed to update name: ${nameResult.error ?: "Unknown error"}"
                                            )
                                            ActivityLogState.error("Failed to update rule name: ${nameResult.error}")
                                            return@launch
                                        }

                                        val validUntilVal = if (hasExpiry) {
                                            expiryLedger.toUIntOrNull()
                                        } else null

                                        val validUntilResult = kit.contextRuleManager.updateValidUntil(
                                            id = editRuleId,
                                            validUntil = validUntilVal
                                        )
                                        if (!validUntilResult.success) {
                                            submissionResult = SubmissionResult(
                                                success = false,
                                                error = "Name updated but failed to update expiry: ${validUntilResult.error ?: "Unknown error"}"
                                            )
                                            ActivityLogState.error("Failed to update validUntil: ${validUntilResult.error}")
                                            return@launch
                                        }

                                        submissionResult = SubmissionResult(
                                            success = true,
                                            hash = validUntilResult.hash
                                        )
                                        ActivityLogState.info(
                                            "Rule #$editRuleId updated successfully. Hash: ${validUntilResult.hash ?: "N/A"}"
                                        )
                                    } else {
                                        // Create mode: add new context rule
                                        ActivityLogState.info("Submitting new context rule...")

                                        val selectedContextType = when (contextTypeOption) {
                                            ContextTypeOption.DEFAULT -> ContextRuleType.Default
                                            ContextTypeOption.CALL_CONTRACT ->
                                                ContextRuleType.CallContract(contractAddress.trim())
                                            ContextTypeOption.CREATE_CONTRACT ->
                                                ContextRuleType.CreateContract(
                                                    hexToByteArray(wasmHashHex.trim().lowercase())
                                                )
                                        }

                                        val validUntilVal = if (hasExpiry) {
                                            expiryLedger.toUIntOrNull()
                                        } else null

                                        // Build policies map: address -> SCValXdr
                                        val policiesMap = mutableMapOf<String, SCValXdr>()
                                        for (policy in policies) {
                                            if (policy.scVal != null) {
                                                policiesMap[policy.address] = policy.scVal
                                            }
                                            // Policies loaded from edit mode without scVal are skipped
                                            // (they are already installed on-chain)
                                        }

                                        val result = kit.contextRuleManager.addContextRule(
                                            contextType = selectedContextType,
                                            name = ruleName.trim(),
                                            validUntil = validUntilVal,
                                            signers = signers,
                                            policies = policiesMap
                                        )

                                        submissionResult = SubmissionResult(
                                            success = result.success,
                                            hash = result.hash,
                                            error = result.error
                                        )

                                        if (result.success) {
                                            ActivityLogState.info(
                                                "Context rule created successfully. Hash: ${result.hash ?: "N/A"}"
                                            )
                                        } else {
                                            ActivityLogState.error(
                                                "Failed to create context rule: ${result.error ?: "Unknown error"}"
                                            )
                                        }
                                    }
                                } catch (e: Exception) {
                                    submissionResult = SubmissionResult(
                                        success = false,
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
                                ruleName.isNotBlank() && signers.isNotEmpty() &&
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
                            text = "Transaction in progress. This may take up to 30 seconds...",
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
        val typeDescription = SmartAccountBuilders.describeSignerType(signer)
        val displayInfo = SmartAccountBuilders.formatSignerForDisplay(signer)

        val chipColor = when (typeDescription) {
            "Passkey (WebAuthn)" -> Color(0xFF9C27B0)
            "Stellar Account" -> Color(0xFF2196F3)
            "Ed25519" -> Color(0xFF009688)
            else -> Color(0xFF607D8B)
        }

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
                    text = SmartAccountBuilders.truncateAddress(policy.address, 8),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, top = 4.dp)
                )
            }
        }
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
        signers: List<SmartAccountSigner>
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

        // Expiry
        if (hasExpiry) {
            if (expiryLedger.isBlank()) {
                errors["expiryLedger"] = "Ledger number is required when expiry is enabled"
            } else {
                val ledgerNum = expiryLedger.toUIntOrNull()
                if (ledgerNum == null || ledgerNum == 0u) {
                    errors["expiryLedger"] = "Must be a positive integer"
                }
            }
        }

        // Signers
        if (signers.isEmpty()) {
            errors["signers"] = "At least one signer is required"
        }

        return errors
    }

    // ========================================================================
    // Policy ScVal Builders
    // ========================================================================

    /**
     * Builds the SCValXdr for a simple threshold policy.
     * Map structure: { "threshold": U32(threshold) }
     */
    private fun buildSimpleThresholdScVal(threshold: UInt): SCValXdr {
        val map = linkedMapOf(
            Scv.toSymbol("threshold") to Scv.toUint32(threshold)
        )
        return Scv.toMap(map)
    }

    /**
     * Builds the SCValXdr for a spending limit policy.
     * Map structure: { "period_ledgers": U32(periodLedgers), "spending_limit": I128(stroops) }
     */
    private fun buildSpendingLimitScVal(stroops: Long, periodLedgers: UInt): SCValXdr {
        val limitI128 = SmartAccountSharedUtils.stroopsToI128ScVal(stroops)
        val map = linkedMapOf(
            Scv.toSymbol("period_ledgers") to Scv.toUint32(periodLedgers),
            Scv.toSymbol("spending_limit") to limitI128
        )
        return Scv.toMap(map)
    }

    /**
     * Builds the SCValXdr for a weighted threshold policy.
     * Map structure: { "signer_weights": Map[Signer => U32], "threshold": U32(threshold) }
     */
    private fun buildWeightedThresholdScVal(
        weights: Map<SmartAccountSigner, UInt>,
        threshold: UInt
    ): SCValXdr {
        val weightsMap = linkedMapOf<SCValXdr, SCValXdr>()
        for ((signer, weight) in weights) {
            weightsMap[signer.toScVal()] = Scv.toUint32(weight)
        }
        val sortedWeightsMap = SmartAccountSharedUtils.sortMapByKeyXdr(weightsMap)

        val map = linkedMapOf(
            Scv.toSymbol("signer_weights") to Scv.toMap(sortedWeightsMap),
            Scv.toSymbol("threshold") to Scv.toUint32(threshold)
        )
        return Scv.toMap(map)
    }

    // ========================================================================
    // Edit Mode: ScVal Parsing (reuses patterns from ContextRulesScreen)
    // ========================================================================

    private data class ParsedRuleData(
        val id: UInt,
        val contextType: ContextRuleType,
        val name: String,
        val signers: List<SmartAccountSigner>,
        val policies: List<String>,
        val validUntil: UInt?
    )

    /**
     * Parses a single context rule from its ScVal map representation.
     */
    private fun parseRuleFromScVal(scVal: SCValXdr, fallbackId: UInt): ParsedRuleData? {
        val map = (scVal as? SCValXdr.Map)?.value?.value ?: return null

        var id: UInt = fallbackId
        var contextType: ContextRuleType = ContextRuleType.Default
        var name = ""
        var signers = listOf<SmartAccountSigner>()
        var policies = listOf<String>()
        var validUntil: UInt? = null

        for (entry in map) {
            val fieldName = (entry.key as? SCValXdr.Sym)?.value?.value ?: continue
            val fieldValue = entry.`val`

            when (fieldName) {
                "id" -> {
                    id = (fieldValue as? SCValXdr.U32)?.value?.value ?: fallbackId
                }
                "context_type" -> {
                    contextType = parseContextType(fieldValue)
                }
                "name" -> {
                    name = when (fieldValue) {
                        is SCValXdr.Str -> fieldValue.value.value
                        is SCValXdr.Sym -> fieldValue.value.value
                        else -> ""
                    }
                }
                "signers" -> {
                    signers = parseSigners(fieldValue)
                }
                "policies" -> {
                    policies = parsePolicies(fieldValue)
                }
                "valid_until" -> {
                    validUntil = when (fieldValue) {
                        is SCValXdr.U32 -> fieldValue.value.value
                        is SCValXdr.Void -> null
                        else -> null
                    }
                }
            }
        }

        return ParsedRuleData(
            id = id,
            contextType = contextType,
            name = name,
            signers = signers,
            policies = policies,
            validUntil = validUntil
        )
    }

    private fun parseContextType(scVal: SCValXdr): ContextRuleType {
        val vec = (scVal as? SCValXdr.Vec)?.value?.value ?: return ContextRuleType.Default
        if (vec.isEmpty()) return ContextRuleType.Default

        val tag = (vec[0] as? SCValXdr.Sym)?.value?.value ?: return ContextRuleType.Default

        return when (tag) {
            "Default" -> ContextRuleType.Default
            "CallContract" -> {
                if (vec.size >= 2) {
                    val address = (vec[1] as? SCValXdr.Address)?.value
                    if (address != null) {
                        val addressStr = SmartAccountSharedUtils.extractAddressString(address)
                        if (addressStr != null) {
                            ContextRuleType.CallContract(addressStr)
                        } else ContextRuleType.Default
                    } else ContextRuleType.Default
                } else ContextRuleType.Default
            }
            "CreateContract" -> {
                if (vec.size >= 2) {
                    val bytes = (vec[1] as? SCValXdr.Bytes)?.value?.value
                    if (bytes != null) {
                        ContextRuleType.CreateContract(bytes)
                    } else ContextRuleType.Default
                } else ContextRuleType.Default
            }
            else -> ContextRuleType.Default
        }
    }

    private fun parseSigners(scVal: SCValXdr): List<SmartAccountSigner> {
        val vec = (scVal as? SCValXdr.Vec)?.value?.value ?: return emptyList()
        return vec.mapNotNull { signerVal -> parseSingleSigner(signerVal) }
    }

    private fun parseSingleSigner(scVal: SCValXdr): SmartAccountSigner? {
        val vec = (scVal as? SCValXdr.Vec)?.value?.value ?: return null
        if (vec.isEmpty()) return null

        val tag = (vec[0] as? SCValXdr.Sym)?.value?.value ?: return null

        return when (tag) {
            "Delegated" -> {
                if (vec.size >= 2) {
                    val address = (vec[1] as? SCValXdr.Address)?.value
                    if (address != null) {
                        val addressStr = SmartAccountSharedUtils.extractAddressString(address)
                        if (addressStr != null) {
                            try { DelegatedSigner(addressStr) } catch (_: Exception) { null }
                        } else null
                    } else null
                } else null
            }
            "External" -> {
                if (vec.size >= 3) {
                    val verifier = (vec[1] as? SCValXdr.Address)?.value
                    val keyData = (vec[2] as? SCValXdr.Bytes)?.value?.value
                    if (verifier != null && keyData != null) {
                        val verifierStr = SmartAccountSharedUtils.extractAddressString(verifier)
                        if (verifierStr != null) {
                            try { ExternalSigner(verifierStr, keyData) } catch (_: Exception) { null }
                        } else null
                    } else null
                } else null
            }
            else -> null
        }
    }

    private fun parsePolicies(scVal: SCValXdr): List<String> {
        return when (scVal) {
            is SCValXdr.Vec -> {
                val vec = scVal.value?.value ?: return emptyList()
                vec.mapNotNull { element ->
                    val address = (element as? SCValXdr.Address)?.value
                    if (address != null) SmartAccountSharedUtils.extractAddressString(address) else null
                }
            }
            is SCValXdr.Map -> {
                val entries = scVal.value?.value ?: return emptyList()
                entries.mapNotNull { mapEntry ->
                    val address = (mapEntry.key as? SCValXdr.Address)?.value
                    if (address != null) SmartAccountSharedUtils.extractAddressString(address) else null
                }
            }
            else -> emptyList()
        }
    }

    // ========================================================================
    // Utility Functions
    // ========================================================================

    /**
     * Converts a hex string to a ByteArray.
     */
    private fun hexToByteArray(hex: String): ByteArray {
        require(hex.length % 2 == 0) { "Hex string must have even length" }
        return ByteArray(hex.length / 2) { i ->
            val index = i * 2
            hex.substring(index, index + 2).toInt(16).toByte()
        }
    }

    /**
     * Converts a ByteArray to a lowercase hex string.
     */
    private fun ByteArray.toHexString(): String {
        return joinToString("") { byte ->
            (byte.toInt() and 0xFF).toString(16).padStart(2, '0')
        }
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

/**
 * Result of a submission attempt.
 */
private data class SubmissionResult(
    val success: Boolean,
    val hash: String? = null,
    val error: String? = null
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
