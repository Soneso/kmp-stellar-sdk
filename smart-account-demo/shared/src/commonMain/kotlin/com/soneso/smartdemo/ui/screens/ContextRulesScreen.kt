package com.soneso.smartdemo.ui.screens

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.material3.TextButton
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
import com.soneso.smartdemo.state.ActivityLogState
import com.soneso.smartdemo.state.DemoState
import com.soneso.stellar.sdk.smartaccount.core.DelegatedSigner
import com.soneso.stellar.sdk.smartaccount.core.ExternalSigner
import com.soneso.stellar.sdk.smartaccount.core.SmartAccountBuilders
import com.soneso.stellar.sdk.smartaccount.core.SmartAccountSigner
import com.soneso.stellar.sdk.smartaccount.oz.ContextRuleType
import com.soneso.stellar.sdk.smartaccount.oz.OZBuilders
import com.soneso.stellar.sdk.smartaccount.oz.ParsedContextRule
import com.soneso.stellar.sdk.smartaccount.oz.SmartAccountSharedUtils
import com.soneso.stellar.sdk.xdr.SCValXdr
import kotlinx.coroutines.launch

class ContextRulesScreen : Screen {

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val scope = rememberCoroutineScope()

        var rules by remember { mutableStateOf<List<ParsedContextRule>>(emptyList()) }
        var isLoading by remember { mutableStateOf(false) }
        var errorMessage by remember { mutableStateOf<String?>(null) }
        var expandedRuleId by remember { mutableStateOf<UInt?>(null) }
        var ruleToRemove by remember { mutableStateOf<ParsedContextRule?>(null) }
        var isRemoving by remember { mutableStateOf(false) }

        // Fetch rules on screen load
        LaunchedEffect(Unit) {
            if (DemoState.isConnected && DemoState.kit != null) {
                isLoading = true
                errorMessage = null
                try {
                    rules = fetchAllContextRules()
                    ActivityLogState.info("Fetched ${rules.size} context rule(s)")
                } catch (e: Exception) {
                    val msg = e.message ?: "Unknown error"
                    errorMessage = "Failed to fetch context rules: $msg"
                    ActivityLogState.error("Failed to fetch context rules: $msg")
                } finally {
                    isLoading = false
                }
            }
        }

        // Remove confirmation dialog
        if (ruleToRemove != null) {
            AlertDialog(
                onDismissRequest = { ruleToRemove = null },
                title = { Text("Remove Context Rule") },
                text = {
                    Text(
                        "Remove rule #${ruleToRemove!!.id} \"${ruleToRemove!!.name}\"? " +
                                "This action requires smart account authorization and cannot be undone."
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            val rule = ruleToRemove!!
                            ruleToRemove = null
                            scope.launch {
                                isRemoving = true
                                try {
                                    val kit = DemoState.kit
                                        ?: throw IllegalStateException("Kit not initialized")
                                    ActivityLogState.info("Removing context rule #${rule.id}...")
                                    val result = kit.contextRuleManager.removeContextRule(rule.id)
                                    if (result.success) {
                                        ActivityLogState.success(
                                            "Context rule #${rule.id} removed. Hash: ${result.hash ?: "N/A"}"
                                        )
                                        // Refresh rules
                                        isLoading = true
                                        errorMessage = null
                                        try {
                                            rules = fetchAllContextRules()
                                            ActivityLogState.info("Refreshed: ${rules.size} context rule(s)")
                                        } catch (e: Exception) {
                                            errorMessage = "Failed to refresh rules: ${e.message}"
                                        } finally {
                                            isLoading = false
                                        }
                                    } else {
                                        val errMsg = result.error ?: "Unknown error"
                                        ActivityLogState.error("Failed to remove rule: $errMsg")
                                        errorMessage = "Failed to remove rule: $errMsg"
                                    }
                                } catch (e: Exception) {
                                    val msg = e.message ?: "Unknown error"
                                    ActivityLogState.error("Failed to remove rule: $msg")
                                    errorMessage = "Failed to remove rule: $msg"
                                } finally {
                                    isRemoving = false
                                }
                            }
                        }
                    ) {
                        Text("Remove", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { ruleToRemove = null }) {
                        Text("Cancel")
                    }
                }
            )
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Context Rules") },
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
                // Description card
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
                            text = "On-Chain Authorization Rules",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Context rules define who can authorize what operations on this smart account. " +
                                    "Each rule specifies signers and policies that control access for a given context type.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                isLoading = true
                                errorMessage = null
                                try {
                                    rules = fetchAllContextRules()
                                    ActivityLogState.info("Refreshed: ${rules.size} context rule(s)")
                                } catch (e: Exception) {
                                    val msg = e.message ?: "Unknown error"
                                    errorMessage = "Failed to fetch context rules: $msg"
                                    ActivityLogState.error("Failed to fetch context rules: $msg")
                                } finally {
                                    isLoading = false
                                }
                            }
                        },
                        enabled = !isLoading && !isRemoving && DemoState.isConnected,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(if (isLoading) "Loading..." else "Refresh")
                    }
                    Button(
                        onClick = {
                            navigator.push(ContextRuleBuilderScreen())
                        },
                        enabled = !isLoading && !isRemoving && DemoState.isConnected,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("+ Add Rule")
                    }
                }

                // Error card
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

                // Not connected state
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
                                text = "Connect a wallet to view context rules.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Loading state
                if (isLoading && rules.isEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(32.dp)
                        )
                        Text(
                            text = "Loading context rules...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Removing indicator
                if (isRemoving) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp))
                            Text(
                                text = "Removing context rule (requires authorization)...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }

                // Empty state
                if (!isLoading && rules.isEmpty() && DemoState.isConnected && errorMessage == null) {
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
                                text = "No context rules found",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "This wallet may use a default configuration, or rules may not have been created yet.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Rules list
                rules.forEach { rule ->
                    ContextRuleCard(
                        rule = rule,
                        isExpanded = expandedRuleId == rule.id,
                        onToggleExpand = {
                            expandedRuleId = if (expandedRuleId == rule.id) null else rule.id
                        },
                        onEdit = {
                            navigator.push(ContextRuleBuilderScreen(editRuleId = rule.id))
                        },
                        onRemove = { ruleToRemove = rule },
                        isRemoving = isRemoving
                    )
                }

                // Rule count summary
                if (rules.isNotEmpty()) {
                    Text(
                        text = "${rules.size} context rule(s) loaded",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    @Composable
    private fun ContextRuleCard(
        rule: ParsedContextRule,
        isExpanded: Boolean,
        onToggleExpand: () -> Unit,
        onEdit: () -> Unit,
        onRemove: () -> Unit,
        isRemoving: Boolean
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Header (always visible, clickable to expand)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onToggleExpand() }
                        .padding(16.dp)
                ) {
                    // Top row: rule ID + name + expand icon
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            // Rule ID badge
                            Surface(
                                color = MaterialTheme.colorScheme.primary,
                                shape = MaterialTheme.shapes.small
                            ) {
                                Text(
                                    text = "#${rule.id}",
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Text(
                                text = rule.name.ifEmpty { "Unnamed Rule" },
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Icon(
                            imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = if (isExpanded) "Collapse" else "Expand",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Context type
                    Text(
                        text = OZBuilders.formatContextType(rule.contextType),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Summary badges
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Signers badge
                        Surface(
                            color = Color(0xFF2196F3).copy(alpha = 0.15f),
                            shape = MaterialTheme.shapes.small
                        ) {
                            Text(
                                text = "${rule.signers.size} signer${if (rule.signers.size != 1) "s" else ""}",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF1565C0)
                            )
                        }

                        // Policies badge
                        Surface(
                            color = Color(0xFF4CAF50).copy(alpha = 0.15f),
                            shape = MaterialTheme.shapes.small
                        ) {
                            Text(
                                text = "${rule.policies.size} polic${if (rule.policies.size != 1) "ies" else "y"}",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF2E7D32)
                            )
                        }

                        // Expiry badge
                        if (rule.validUntil != null) {
                            Surface(
                                color = Color(0xFFFF9800).copy(alpha = 0.15f),
                                shape = MaterialTheme.shapes.small
                            ) {
                                Text(
                                    text = "Expires: ledger ${rule.validUntil}",
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFFE65100)
                                )
                            }
                        }
                    }
                }

                // Expanded details
                AnimatedVisibility(
                    visible = isExpanded,
                    enter = expandVertically(),
                    exit = shrinkVertically()
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))

                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // Signers section
                            SignersSection(rule.signers)

                            // Policies section
                            PoliciesSection(rule.policies)

                            // Action buttons
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = onEdit,
                                    enabled = !isRemoving,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Edit Rule")
                                }
                                Button(
                                    onClick = onRemove,
                                    enabled = !isRemoving,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.error,
                                        contentColor = MaterialTheme.colorScheme.onError
                                    ),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Remove Rule")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun SignersSection(signers: List<SmartAccountSigner>) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "Signers",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )

            if (signers.isEmpty()) {
                Text(
                    text = "No signers (policy-only rule)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                signers.forEach { signer ->
                    SignerChip(signer)
                }
            }
        }
    }

    @Composable
    private fun SignerChip(signer: SmartAccountSigner) {
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
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
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
        }
    }

    @Composable
    private fun PoliciesSection(policies: List<String>) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "Policies",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )

            if (policies.isEmpty()) {
                Text(
                    text = "No policies (signer-only rule)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                policies.forEach { policyAddress ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFF4CAF50).copy(alpha = 0.08f)
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                color = Color(0xFF4CAF50),
                                shape = MaterialTheme.shapes.small
                            ) {
                                Text(
                                    text = "P",
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Text(
                                text = SmartAccountBuilders.truncateAddress(policyAddress, 8),
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }

    // ============================================================================
    // Data Fetching and Parsing
    // ============================================================================

    /**
     * Fetches all context rules by querying the total count and then
     * fetching each rule individually by ID. This captures rules of all
     * context types (Default, CallContract, CreateContract).
     */
    private suspend fun fetchAllContextRules(): List<ParsedContextRule> {
        val kit = DemoState.kit ?: throw IllegalStateException("Kit not initialized")
        val contextMgr = kit.contextRuleManager

        val allRules = mutableListOf<ParsedContextRule>()
        val seenIds = mutableSetOf<UInt>()

        // Get the total rule count, then fetch each rule by ID.
        val totalCount = try {
            contextMgr.getContextRulesCount()
        } catch (e: Exception) {
            ActivityLogState.error("Failed to get rule count: ${e.message}")
            0u
        }

        if (totalCount == 0u) {
            // Fallback: try fetching Default rules directly
            try {
                val defaultScVal = contextMgr.getContextRules(ContextRuleType.Default)
                val defaultRules = parseContextRulesFromScVal(defaultScVal)
                for (rule in defaultRules) {
                    if (seenIds.add(rule.id)) allRules.add(rule)
                }
            } catch (_: Exception) {
                // No default rules or contract doesn't support this call
            }
            return allRules.sortedBy { it.id }
        }

        // Fetch individual rules by ID (IDs are sequential starting from 0)
        for (id in 0u until totalCount) {
            try {
                val ruleScVal = contextMgr.getContextRule(id)
                val parsed = parseSingleContextRuleFromScVal(ruleScVal, id)
                if (parsed != null && seenIds.add(parsed.id)) {
                    allRules.add(parsed)
                }
            } catch (e: Exception) {
                // Rule may not exist at this ID (could have been removed)
                ActivityLogState.info("Rule #$id not found or could not be parsed")
            }
        }

        return allRules.sortedBy { it.id }
    }

    /**
     * Parses a Vec of context rules returned by get_context_rules.
     *
     * The returned SCVal is a Vec where each element is a Map (struct) with fields:
     * context_type, id, name, policies, signers, valid_until
     */
    private fun parseContextRulesFromScVal(scVal: SCValXdr): List<ParsedContextRule> {
        val vec = (scVal as? SCValXdr.Vec)?.value?.value ?: return emptyList()
        return vec.mapNotNull { element ->
            parseSingleContextRuleFromScVal(element, null)
        }
    }

    /**
     * Parses a single context rule from its ScVal representation.
     *
     * Soroban structs are encoded as ScMap with Symbol keys in alphabetical order:
     * { context_type, id, name, policies, signers, valid_until }
     */
    private fun parseSingleContextRuleFromScVal(
        scVal: SCValXdr,
        fallbackId: UInt?
    ): ParsedContextRule? {
        val map = (scVal as? SCValXdr.Map)?.value?.value ?: return null

        var id: UInt = fallbackId ?: 0u
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
                    id = (fieldValue as? SCValXdr.U32)?.value?.value ?: (fallbackId ?: 0u)
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

        return ParsedContextRule(
            id = id,
            contextType = contextType,
            name = name,
            signers = signers,
            policies = policies,
            validUntil = validUntil
        )
    }

    /**
     * Parses a context type from its ScVal representation.
     *
     * On-chain format:
     * - Default: Vec[Symbol("Default")]
     * - CallContract: Vec[Symbol("CallContract"), Address(contractAddress)]
     * - CreateContract: Vec[Symbol("CreateContract"), Bytes(wasmHash)]
     */
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
                        } else {
                            ContextRuleType.Default
                        }
                    } else {
                        ContextRuleType.Default
                    }
                } else {
                    ContextRuleType.Default
                }
            }

            "CreateContract" -> {
                if (vec.size >= 2) {
                    val bytes = (vec[1] as? SCValXdr.Bytes)?.value?.value
                    if (bytes != null) {
                        ContextRuleType.CreateContract(bytes)
                    } else {
                        ContextRuleType.Default
                    }
                } else {
                    ContextRuleType.Default
                }
            }

            else -> ContextRuleType.Default
        }
    }

    /**
     * Parses signers from a ScVal Vec.
     *
     * Each signer is a Vec:
     * - Delegated: Vec[Symbol("Delegated"), Address(address)]
     * - External: Vec[Symbol("External"), Address(verifier), Bytes(keyData)]
     */
    private fun parseSigners(scVal: SCValXdr): List<SmartAccountSigner> {
        val vec = (scVal as? SCValXdr.Vec)?.value?.value ?: return emptyList()
        return vec.mapNotNull { signerVal ->
            parseSingleSigner(signerVal)
        }
    }

    /**
     * Parses a single signer from its ScVal Vec representation.
     */
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
                            try {
                                DelegatedSigner(addressStr)
                            } catch (_: Exception) {
                                null
                            }
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
                            try {
                                ExternalSigner(verifierStr, keyData)
                            } catch (_: Exception) {
                                null
                            }
                        } else null
                    } else null
                } else null
            }

            else -> null
        }
    }

    /**
     * Parses policy addresses from a ScVal.
     *
     * Policies can be encoded as:
     * - Vec[Address, ...] (list of policy contract addresses)
     * - Map[Address -> ScVal, ...] (policy address -> install params, extract keys)
     */
    private fun parsePolicies(scVal: SCValXdr): List<String> {
        return when (scVal) {
            is SCValXdr.Vec -> {
                val vec = scVal.value?.value ?: return emptyList()
                vec.mapNotNull { element ->
                    val address = (element as? SCValXdr.Address)?.value
                    if (address != null) {
                        SmartAccountSharedUtils.extractAddressString(address)
                    } else null
                }
            }

            is SCValXdr.Map -> {
                val entries = scVal.value?.value ?: return emptyList()
                entries.mapNotNull { mapEntry ->
                    val address = (mapEntry.key as? SCValXdr.Address)?.value
                    if (address != null) {
                        SmartAccountSharedUtils.extractAddressString(address)
                    } else null
                }
            }

            else -> emptyList()
        }
    }
}
