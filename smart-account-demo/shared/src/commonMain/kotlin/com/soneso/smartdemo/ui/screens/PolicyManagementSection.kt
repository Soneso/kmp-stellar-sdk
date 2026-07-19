package com.soneso.smartdemo.ui.screens

/**
 * Policy management UI section extracted from ContextRuleBuilderScreen.
 *
 * Renders the policy header, current policies list, and add-policy forms
 * (threshold, spending limit, weighted threshold). All state is received
 * via parameters; no direct SDK network calls.
 *
 * Supports two modes:
 * - Create mode (isEditing = false): Original behavior using [PolicyEntry] list.
 * - Edit mode (isEditing = true): Uses [EditPolicyEntry] list with on-chain tracking,
 *   "(on-chain)" badges, pre-populated parameters, and modification tracking.
 */

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.soneso.smartdemo.config.KNOWN_POLICIES
import com.soneso.smartdemo.config.PolicyInfo
import com.soneso.smartdemo.flows.EditPolicyEntry
import com.soneso.smartdemo.state.ActivityLogState
import com.soneso.smartdemo.ui.components.SignerIdentityChip
import com.soneso.smartdemo.util.buildSimpleThresholdScVal
import com.soneso.smartdemo.util.buildSpendingLimitScVal
import com.soneso.smartdemo.util.buildWeightedThresholdScVal
import com.soneso.smartdemo.util.formatSignerForDisplay
import com.soneso.smartdemo.util.isValidSpendingAmount
import com.soneso.smartdemo.util.truncateAddress
import com.soneso.stellar.sdk.Util
import com.soneso.stellar.sdk.smartaccount.core.SmartAccountBuilders
import com.soneso.stellar.sdk.smartaccount.core.SmartAccountSigner
import com.soneso.stellar.sdk.smartaccount.oz.OZConstants
import com.soneso.stellar.sdk.xdr.SCValXdr

/**
 * Composable section for managing policies on a context rule.
 *
 * Displays the policy header card, the current policies list, and the add-policy
 * forms (threshold, spending limit, weighted threshold). All mutable state is
 * owned by the caller and passed in via parameters and callbacks.
 *
 * In edit mode, additional parameters control on-chain badge display,
 * pre-population of policy parameters, and modification tracking.
 *
 * @param isEditing True when editing an existing rule (shows on-chain badges, etc.).
 * @param editPolicyEntries Policy entries with on-chain tracking (used in edit mode).
 * @param onEditPolicyEntriesChanged Callback when edit entries change (edit mode).
 */
@Composable
fun PolicyManagementSection(
    policies: List<PolicyEntry>,
    onPoliciesChanged: (List<PolicyEntry>) -> Unit,
    signers: List<SmartAccountSigner>,
    selectedPolicyType: PolicyInfo?,
    onSelectedPolicyTypeChanged: (PolicyInfo?) -> Unit,
    thresholdValue: String,
    onThresholdValueChanged: (String) -> Unit,
    spendingLimitAmount: String,
    onSpendingLimitAmountChanged: (String) -> Unit,
    spendingLimitPeriodDays: String,
    onSpendingLimitPeriodDaysChanged: (String) -> Unit,
    weightedThresholdValue: String,
    onWeightedThresholdValueChanged: (String) -> Unit,
    signerWeights: Map<String, String>,
    onSignerWeightsChanged: (Map<String, String>) -> Unit,
    fieldErrors: Map<String, String>,
    onFieldErrorsChanged: (Map<String, String>) -> Unit,
    isSubmitting: Boolean,
    isEditing: Boolean = false,
    editPolicyEntries: List<EditPolicyEntry> = emptyList(),
    onEditPolicyEntriesChanged: ((List<EditPolicyEntry>) -> Unit)? = null,
    captionModifier: Modifier = Modifier
) {
    // --- Policy Header Card ---
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
            if (isEditing) {
                Text(
                    text = "Each policy change requires a separate passkey authentication.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }

    // --- Current Policies List ---
    if (isEditing) {
        // Edit mode: use EditPolicyEntry list with on-chain tracking
        if (editPolicyEntries.isEmpty()) {
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
            editPolicyEntries.forEachIndexed { index, entry ->
                EditPolicyCard(
                    entry = entry,
                    onRemove = {
                        val updated = editPolicyEntries.toMutableList().also { it.removeAt(index) }
                        onEditPolicyEntriesChanged?.invoke(updated)
                        onPoliciesChanged(updated.map { e ->
                            PolicyEntry(info = e.info, label = e.label, address = e.address, scVal = e.scVal)
                        })
                    },
                    enabled = !isSubmitting
                )
            }
            Text(
                text = "${editPolicyEntries.size} ${if (editPolicyEntries.size == 1) "policy" else "policies"} attached",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = captionModifier
            )
        }

        // Edit mode: inline editing forms for existing policies with original params
        editPolicyEntries.forEachIndexed { index, entry ->
            if (entry.isOriginal && entry.originalParams != null) {
                EditPolicyParamsForm(
                    entry = entry,
                    signers = signers,
                    signerWeights = signerWeights,
                    onSignerWeightsChanged = onSignerWeightsChanged,
                    onEntryUpdated = { updatedEntry ->
                        val updated = editPolicyEntries.toMutableList()
                        updated[index] = updatedEntry
                        onEditPolicyEntriesChanged?.invoke(updated)
                        onPoliciesChanged(updated.map { e ->
                            PolicyEntry(info = e.info, label = e.label, address = e.address, scVal = e.scVal)
                        })
                    },
                    isSubmitting = isSubmitting
                )
            }
        }
    } else {
        // Create mode: use plain PolicyEntry list
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
                        val updated = policies.toMutableList().also { it.removeAt(index) }
                        onPoliciesChanged(updated)
                    },
                    enabled = !isSubmitting
                )
            }
            Text(
                text = "${policies.size} ${if (policies.size == 1) "policy" else "policies"} attached",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = captionModifier
            )
        }
    }

    // --- Add Policy Section ---
    val currentPolicyCount = if (isEditing) editPolicyEntries.size else policies.size
    val currentPolicyAddresses = if (isEditing) editPolicyEntries.map { it.address } else policies.map { it.address }
    if (currentPolicyCount < OZConstants.MAX_POLICIES) {
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
                        onSelectedPolicyTypeChanged(it)
                        // Reset fields when switching type
                        onThresholdValueChanged("")
                        onSpendingLimitAmountChanged("")
                        onSpendingLimitPeriodDaysChanged("")
                        onWeightedThresholdValueChanged("")
                        onSignerWeightsChanged(emptyMap())
                        onFieldErrorsChanged(
                            fieldErrors - "threshold" - "spendingAmount" -
                                    "spendingPeriod" - "weightedThreshold" - "signerWeights" - "policy"
                        )
                    },
                    availablePolicies = KNOWN_POLICIES.filter { known ->
                        currentPolicyAddresses.none { it == known.address }
                    },
                    enabled = !isSubmitting
                )

                // Policy-specific fields
                when (selectedPolicyType?.type) {
                    "threshold" -> {
                        // Simple Threshold
                        Text(
                            text = "Contract: ${truncateAddress(selectedPolicyType.address, 8)}",
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedTextField(
                            value = thresholdValue,
                            onValueChange = { value ->
                                onThresholdValueChanged(value.filter { it.isDigit() })
                                onFieldErrorsChanged(fieldErrors - "threshold")
                            },
                            label = { Text("Threshold (required signers)") },
                            placeholder = { Text("e.g., 2") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            enabled = !isSubmitting,
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
                                    if (isEditing) {
                                        val newEntry = EditPolicyEntry(
                                            info = selectedPolicyType,
                                            label = "Threshold: $t-of-N",
                                            address = selectedPolicyType.address,
                                            scVal = scVal,
                                            onChainId = null,
                                            isOriginal = false
                                        )
                                        onEditPolicyEntriesChanged?.invoke(editPolicyEntries + newEntry)
                                        onPoliciesChanged((editPolicyEntries + newEntry).map { e ->
                                            PolicyEntry(info = e.info, label = e.label, address = e.address, scVal = e.scVal)
                                        })
                                    } else {
                                        onPoliciesChanged(
                                            policies + PolicyEntry(
                                                info = selectedPolicyType,
                                                label = "Threshold: $t-of-N",
                                                address = selectedPolicyType.address,
                                                scVal = scVal
                                            )
                                        )
                                    }
                                    onThresholdValueChanged("")
                                    onSelectedPolicyTypeChanged(null)
                                    ActivityLogState.info("Added simple threshold policy (threshold=$t)")
                                }
                                onFieldErrorsChanged(fieldErrors - "threshold" + errors)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = thresholdValue.isNotBlank() && !isSubmitting
                        ) {
                            Text("Add Threshold Policy")
                        }
                    }

                    "spending_limit" -> {
                        // Spending Limit
                        Text(
                            text = "Contract: ${truncateAddress(selectedPolicyType.address, 8)}",
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
                                    onSpendingLimitAmountChanged(filtered)
                                }
                                onFieldErrorsChanged(fieldErrors - "spendingAmount")
                            },
                            label = { Text("Amount") },
                            placeholder = { Text("e.g., 100.0") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            enabled = !isSubmitting,
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
                                onSpendingLimitPeriodDaysChanged(value.filter { it.isDigit() })
                                onFieldErrorsChanged(fieldErrors - "spendingPeriod")
                            },
                            label = { Text("Period (days)") },
                            placeholder = { Text("e.g., 1") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            enabled = !isSubmitting,
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
                                    if (isEditing) {
                                        val newEntry = EditPolicyEntry(
                                            info = selectedPolicyType,
                                            label = "Limit: $spendingLimitAmount / $days day(s)",
                                            address = selectedPolicyType.address,
                                            scVal = scVal,
                                            onChainId = null,
                                            isOriginal = false
                                        )
                                        onEditPolicyEntriesChanged?.invoke(editPolicyEntries + newEntry)
                                        onPoliciesChanged((editPolicyEntries + newEntry).map { e ->
                                            PolicyEntry(info = e.info, label = e.label, address = e.address, scVal = e.scVal)
                                        })
                                    } else {
                                        onPoliciesChanged(
                                            policies + PolicyEntry(
                                                info = selectedPolicyType,
                                                label = "Limit: $spendingLimitAmount / $days day(s)",
                                                address = selectedPolicyType.address,
                                                scVal = scVal
                                            )
                                        )
                                    }
                                    onSpendingLimitAmountChanged("")
                                    onSpendingLimitPeriodDaysChanged("")
                                    onSelectedPolicyTypeChanged(null)
                                    ActivityLogState.info(
                                        "Added spending limit policy ($logAmount per $days day(s))"
                                    )
                                }
                                onFieldErrorsChanged(fieldErrors - "spendingAmount" - "spendingPeriod" + errors)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = spendingLimitAmount.isNotBlank() &&
                                    spendingLimitPeriodDays.isNotBlank() && !isSubmitting
                        ) {
                            Text("Add Spending Limit Policy")
                        }
                    }

                    "weighted_threshold" -> {
                        // Weighted Threshold
                        Text(
                            text = "Contract: ${truncateAddress(selectedPolicyType.address, 8)}",
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedTextField(
                            value = weightedThresholdValue,
                            onValueChange = { value ->
                                onWeightedThresholdValueChanged(value.filter { it.isDigit() })
                                onFieldErrorsChanged(fieldErrors - "weightedThreshold")
                            },
                            label = { Text("Weight Threshold") },
                            placeholder = { Text("e.g., 100") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            enabled = !isSubmitting,
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
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    SignerIdentityChip(
                                        signer = signer,
                                        modifier = Modifier.weight(1f)
                                    )
                                    OutlinedTextField(
                                        value = signerWeights[key] ?: "",
                                        onValueChange = { value ->
                                            onSignerWeightsChanged(signerWeights + (key to value.filter { it.isDigit() }))
                                            onFieldErrorsChanged(fieldErrors - "signerWeights")
                                        },
                                        label = { Text("Weight") },
                                        modifier = Modifier.width(100.dp),
                                        singleLine = true,
                                        enabled = !isSubmitting
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
                                    if (isEditing) {
                                        val newEntry = EditPolicyEntry(
                                            info = selectedPolicyType,
                                            label = "Weighted: threshold=$threshold ($weightsDesc)",
                                            address = selectedPolicyType.address,
                                            scVal = scVal,
                                            onChainId = null,
                                            isOriginal = false
                                        )
                                        onEditPolicyEntriesChanged?.invoke(editPolicyEntries + newEntry)
                                        onPoliciesChanged((editPolicyEntries + newEntry).map { e ->
                                            PolicyEntry(info = e.info, label = e.label, address = e.address, scVal = e.scVal)
                                        })
                                    } else {
                                        onPoliciesChanged(
                                            policies + PolicyEntry(
                                                info = selectedPolicyType,
                                                label = "Weighted: threshold=$threshold ($weightsDesc)",
                                                address = selectedPolicyType.address,
                                                scVal = scVal
                                            )
                                        )
                                    }
                                    onWeightedThresholdValueChanged("")
                                    onSignerWeightsChanged(emptyMap())
                                    onSelectedPolicyTypeChanged(null)
                                    ActivityLogState.info(
                                        "Added weighted threshold policy (threshold=$threshold)"
                                    )
                                }
                                onFieldErrorsChanged(fieldErrors - "weightedThreshold" - "signerWeights" + errors)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = weightedThresholdValue.isNotBlank() && signers.isNotEmpty() && !isSubmitting
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
}

// ============================================================================
// Edit Mode: Inline Policy Parameter Editing
// ============================================================================

/**
 * Inline form for editing parameters of an existing on-chain policy.
 *
 * Renders form fields pre-populated from [EditPolicyEntry.originalParams] and
 * tracks modifications. When the user changes a parameter, the entry's [modified]
 * flag is set to true and the [scVal] is rebuilt.
 */
@Composable
internal fun EditPolicyParamsForm(
    entry: EditPolicyEntry,
    signers: List<SmartAccountSigner>,
    signerWeights: Map<String, String>,
    onSignerWeightsChanged: (Map<String, String>) -> Unit,
    onEntryUpdated: (EditPolicyEntry) -> Unit,
    isSubmitting: Boolean
) {
    val params = entry.originalParams ?: return

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF2196F3).copy(alpha = 0.06f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Edit ${entry.info?.name ?: "Policy"} Parameters",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            if (entry.modified) {
                Text(
                    text = "Parameters modified (will be updated on submit)",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFE65100)
                )
            }

            when (params.type) {
                "threshold" -> {
                    var editThreshold by remember(entry.onChainId) {
                        mutableStateOf(params.threshold?.toString() ?: "")
                    }
                    // Sync from original on first load
                    LaunchedEffect(entry.onChainId) {
                        editThreshold = params.threshold?.toString() ?: ""
                    }

                    OutlinedTextField(
                        value = editThreshold,
                        onValueChange = { value ->
                            val filtered = value.filter { it.isDigit() }
                            editThreshold = filtered
                            val t = filtered.toUIntOrNull()
                            if (t != null && t > 0u && t <= 15u) {
                                val changed = t != params.threshold
                                val scVal = buildSimpleThresholdScVal(t)
                                onEntryUpdated(
                                    entry.copy(
                                        modified = changed,
                                        scVal = if (changed) scVal else null,
                                        label = "Threshold: $t-of-N"
                                    )
                                )
                            }
                        },
                        label = { Text("Threshold (required signers)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        enabled = !isSubmitting,
                        supportingText = {
                            Text("Current on-chain value: ${params.threshold ?: "unknown"}")
                        }
                    )
                }

                "spending_limit" -> {
                    var editAmount by remember(entry.onChainId) {
                        mutableStateOf(params.spendingLimit ?: "")
                    }
                    var editPeriodDays by remember(entry.onChainId) {
                        mutableStateOf(params.periodDays?.toString() ?: "")
                    }
                    LaunchedEffect(entry.onChainId) {
                        editAmount = params.spendingLimit ?: ""
                        editPeriodDays = params.periodDays?.toString() ?: ""
                    }

                    OutlinedTextField(
                        value = editAmount,
                        onValueChange = { value ->
                            val filtered = value.filter { it.isDigit() || it == '.' }
                            if (filtered.count { it == '.' } <= 1) {
                                editAmount = filtered
                                updateSpendingLimitEntry(
                                    entry, params, filtered, editPeriodDays, onEntryUpdated
                                )
                            }
                        },
                        label = { Text("Amount") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        enabled = !isSubmitting,
                        supportingText = {
                            Text("Current on-chain value: ${params.spendingLimit ?: "unknown"}")
                        }
                    )

                    OutlinedTextField(
                        value = editPeriodDays,
                        onValueChange = { value ->
                            val filtered = value.filter { it.isDigit() }
                            editPeriodDays = filtered
                            updateSpendingLimitEntry(
                                entry, params, editAmount, filtered, onEntryUpdated
                            )
                        },
                        label = { Text("Period (days)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        enabled = !isSubmitting,
                        supportingText = {
                            Text("Current on-chain value: ${params.periodDays ?: "unknown"} day(s)")
                        }
                    )
                }

                "weighted_threshold" -> {
                    var editWThreshold by remember(entry.onChainId) {
                        mutableStateOf(params.threshold?.toString() ?: "")
                    }
                    LaunchedEffect(entry.onChainId) {
                        editWThreshold = params.threshold?.toString() ?: ""
                    }

                    OutlinedTextField(
                        value = editWThreshold,
                        onValueChange = { value ->
                            val filtered = value.filter { it.isDigit() }
                            editWThreshold = filtered
                            updateWeightedThresholdEntry(
                                entry, params, filtered, signerWeights, signers, onEntryUpdated
                            )
                        },
                        label = { Text("Weight Threshold") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        enabled = !isSubmitting,
                        supportingText = {
                            Text("Current on-chain value: ${params.threshold ?: "unknown"}")
                        }
                    )

                    // Per-signer weight fields with original values
                    if (signers.isNotEmpty()) {
                        Text(
                            text = "Per-Signer Weights",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                        // Pre-populate weights from original params on first render
                        LaunchedEffect(entry.onChainId) {
                            val origWeights = params.signerWeights ?: emptyMap()
                            val prePopulated = mutableMapOf<String, String>()
                            for (signer in signers) {
                                val key = SmartAccountBuilders.getSignerKey(signer)
                                val origWeight = origWeights[key]
                                if (origWeight != null) {
                                    prePopulated[key] = origWeight.toString()
                                }
                            }
                            if (prePopulated.isNotEmpty()) {
                                onSignerWeightsChanged(signerWeights + prePopulated)
                            }
                        }
                        signers.forEach { signer ->
                            val key = SmartAccountBuilders.getSignerKey(signer)
                            val origWeight = params.signerWeights?.get(key)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                SignerIdentityChip(
                                    signer = signer,
                                    modifier = Modifier.weight(1f),
                                    subline = origWeight?.let { "On-chain: $it" }
                                )
                                OutlinedTextField(
                                    value = signerWeights[key] ?: "",
                                    onValueChange = { value ->
                                        val updatedWeights = signerWeights + (key to value.filter { it.isDigit() })
                                        onSignerWeightsChanged(updatedWeights)
                                        updateWeightedThresholdEntry(
                                            entry, params, editWThreshold, updatedWeights, signers, onEntryUpdated
                                        )
                                    },
                                    label = { Text("Weight") },
                                    modifier = Modifier.width(100.dp),
                                    singleLine = true,
                                    enabled = !isSubmitting
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
 * Rebuilds the spending limit entry when amount or period changes.
 */
private fun updateSpendingLimitEntry(
    entry: EditPolicyEntry,
    params: com.soneso.smartdemo.flows.PolicyParams,
    amountStr: String,
    periodDaysStr: String,
    onEntryUpdated: (EditPolicyEntry) -> Unit
) {
    val days = periodDaysStr.toIntOrNull()
    if (!isValidSpendingAmount(amountStr) || days == null || days <= 0) return

    val amountChanged = amountStr != params.spendingLimit
    val periodChanged = days != params.periodDays
    val changed = amountChanged || periodChanged

    val periodLedgers = (days * Util.LEDGERS_PER_DAY).toUInt()
    val scVal = buildSpendingLimitScVal(amountStr, periodLedgers)
    onEntryUpdated(
        entry.copy(
            modified = changed,
            scVal = if (changed) scVal else null,
            label = "Limit: $amountStr / $days day(s)"
        )
    )
}

/**
 * Rebuilds the weighted threshold entry when threshold or any weight changes.
 *
 * Only updates the entry when all values are valid (threshold > 0, all signers
 * have weight > 0, total weight >= threshold). Sets [EditPolicyEntry.modified]
 * only when values differ from the original on-chain parameters.
 */
private fun updateWeightedThresholdEntry(
    entry: EditPolicyEntry,
    params: com.soneso.smartdemo.flows.PolicyParams,
    thresholdStr: String,
    currentWeights: Map<String, String>,
    signers: List<SmartAccountSigner>,
    onEntryUpdated: (EditPolicyEntry) -> Unit
) {
    val threshold = thresholdStr.toUIntOrNull()
    if (threshold == null || threshold == 0u) return
    if (signers.isEmpty()) return

    // Validate all signers have valid weights and compute total
    val weightsMap = linkedMapOf<SmartAccountSigner, UInt>()
    var totalWeight = 0u
    for (signer in signers) {
        val key = SmartAccountBuilders.getSignerKey(signer)
        val w = currentWeights[key]?.toUIntOrNull()
        if (w == null || w == 0u) return
        weightsMap[signer] = w
        totalWeight += w
    }

    // Total weight must meet or exceed threshold
    if (totalWeight < threshold) return

    // Determine if anything changed from on-chain values
    val thresholdChanged = threshold != params.threshold
    val weightsChanged = params.signerWeights?.let { orig ->
        weightsMap.any { (signer, w) ->
            val key = SmartAccountBuilders.getSignerKey(signer)
            orig[key] != w
        }
    } ?: true

    val changed = thresholdChanged || weightsChanged
    val scVal = buildWeightedThresholdScVal(weightsMap, threshold)
    val weightsDesc = weightsMap.entries.joinToString(", ") { (s, w) ->
        val info = formatSignerForDisplay(s)
        "${info.type}=$w"
    }
    onEntryUpdated(
        entry.copy(
            modified = changed,
            scVal = if (changed) scVal else null,
            label = "Weighted: threshold=$threshold ($weightsDesc)"
        )
    )
}

// ============================================================================
// Sub-Components
// ============================================================================

/**
 * Dropdown selector for policy type.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PolicyTypeSelector(
    selectedPolicy: PolicyInfo?,
    onPolicySelected: (PolicyInfo?) -> Unit,
    availablePolicies: List<PolicyInfo>,
    enabled: Boolean = true
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { if (enabled) expanded = it }
    ) {
        OutlinedTextField(
            value = selectedPolicy?.name ?: "Select policy type...",
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
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
internal fun PolicyCard(
    policy: PolicyEntry,
    onRemove: () -> Unit,
    enabled: Boolean = true
) {
    val chipColor = policyChipColor(policy.info?.type)

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

/**
 * Card displaying a policy in edit mode with on-chain badge.
 */
@Composable
internal fun EditPolicyCard(
    entry: EditPolicyEntry,
    onRemove: () -> Unit,
    enabled: Boolean = true
) {
    val chipColor = policyChipColor(entry.info?.type)

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
                            text = entry.info?.name ?: "Policy",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White
                        )
                    }

                    // On-chain badge
                    if (entry.isOriginal) {
                        Surface(
                            color = Color(0xFF4CAF50),
                            shape = MaterialTheme.shapes.small
                        ) {
                            Text(
                                text = "(on-chain)",
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White
                            )
                        }
                    }

                    // Modified badge
                    if (entry.modified) {
                        Surface(
                            color = Color(0xFFE65100),
                            shape = MaterialTheme.shapes.small
                        ) {
                            Text(
                                text = "(modified)",
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White
                            )
                        }
                    }

                    // Policy label
                    Text(
                        text = entry.label,
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
                text = truncateAddress(entry.address, 8),
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, top = 4.dp)
            )
        }
    }
}

// ============================================================================
// Helpers
// ============================================================================

/**
 * Returns the chip color for a given policy type identifier.
 */
private fun policyChipColor(type: String?): Color = when (type) {
    "threshold" -> Color(0xFFE65100)
    "spending_limit" -> Color(0xFF1565C0)
    "weighted_threshold" -> Color(0xFF6A1B9A)
    else -> Color(0xFF607D8B)
}

// ============================================================================
// Data Classes
// ============================================================================

/**
 * Represents a policy attached to the current rule being built.
 */
data class PolicyEntry(
    val info: PolicyInfo?,
    val label: String,
    val address: String,
    val scVal: SCValXdr? = null
)
