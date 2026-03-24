package com.soneso.smartdemo.ui.screens

/**
 * Context rules screen: lists all on-chain authorization rules for the connected account.
 * Rule loading and removal are handled by ContextRuleFlow.
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
import com.soneso.smartdemo.flows.loadContextRules
import com.soneso.smartdemo.flows.removeContextRule
import com.soneso.smartdemo.state.ActivityLogState
import com.soneso.smartdemo.state.DemoState
import com.soneso.smartdemo.util.signerTypeColor
import com.soneso.stellar.sdk.smartaccount.core.SmartAccountBuilders
import com.soneso.stellar.sdk.smartaccount.core.SmartAccountSigner
import com.soneso.stellar.sdk.smartaccount.oz.OZBuilders
import com.soneso.stellar.sdk.smartaccount.oz.ParsedContextRule
import androidx.compose.foundation.text.selection.SelectionContainer
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.launch

class ContextRulesScreen : Screen {

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val scope = rememberCoroutineScope()

        // Rule list state
        var rules by remember { mutableStateOf<List<ParsedContextRule>>(emptyList()) }
        var isLoading by remember { mutableStateOf(false) }
        var errorMessage by remember { mutableStateOf<String?>(null) }

        // UI expand/confirm state
        var expandedRuleId by remember { mutableStateOf<UInt?>(null) }
        var ruleToRemove by remember { mutableStateOf<ParsedContextRule?>(null) }
        var isRemoving by remember { mutableStateOf(false) }

        // Fetch rules on screen load
        LaunchedEffect(Unit) {
            if (DemoState.isConnected && DemoState.kit != null) {
                isLoading = true
                errorMessage = null
                try {
                    rules = loadContextRules()
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
                            val handler = CoroutineExceptionHandler { _, throwable ->
                                val msg = throwable.message ?: "Unknown error"
                                errorMessage = "Failed to remove rule: $msg"
                                ActivityLogState.error("Failed to remove rule: $msg")
                                isRemoving = false
                            }
                            scope.launch(handler) {
                                isRemoving = true
                                try {
                                    val result = removeContextRule(rule.id)
                                    if (result.success) {
                                        // Refresh rules after successful removal
                                        isLoading = true
                                        errorMessage = null
                                        try {
                                            rules = loadContextRules()
                                        } catch (e: Throwable) {
                                            errorMessage = "Failed to refresh rules: ${e.message}"
                                        } finally {
                                            isLoading = false
                                        }
                                    } else {
                                        val errMsg = result.error ?: "Unknown error"
                                        errorMessage = "Failed to remove rule: $errMsg"
                                    }
                                } catch (e: Throwable) {
                                    val msg = e.message ?: "Unknown error"
                                    errorMessage = "Failed to remove rule: $msg"
                                    ActivityLogState.error("Failed to remove rule: $msg")
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
                            val handler = CoroutineExceptionHandler { _, throwable ->
                                val msg = throwable.message ?: "Unknown error"
                                errorMessage = "Failed to fetch context rules: $msg"
                                ActivityLogState.error("Failed to fetch context rules: $msg")
                                isLoading = false
                            }
                            scope.launch(handler) {
                                isLoading = true
                                errorMessage = null
                                try {
                                    rules = loadContextRules()
                                } catch (e: Throwable) {
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
                        isRemoving = isRemoving,
                        canRemove = rules.size > 1
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
        isRemoving: Boolean,
        canRemove: Boolean = true
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
                                    enabled = !isRemoving && canRemove,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.error,
                                        contentColor = MaterialTheme.colorScheme.onError
                                    ),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(if (canRemove) "Remove Rule" else "Last Rule")
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
}
