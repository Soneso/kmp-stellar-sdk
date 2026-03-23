package com.soneso.smartdemo.ui.screens

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
import androidx.compose.material3.Button
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
import com.soneso.stellar.sdk.smartaccount.core.SmartAccountBuilders
import com.soneso.stellar.sdk.smartaccount.core.SmartAccountSigner
import com.soneso.stellar.sdk.smartaccount.oz.ContextRuleType
import com.soneso.stellar.sdk.smartaccount.oz.OZBuilders
import com.soneso.stellar.sdk.smartaccount.oz.ParsedContextRule
import com.soneso.stellar.sdk.smartaccount.oz.SmartAccountSharedUtils
import com.soneso.stellar.sdk.xdr.SCValXdr
import kotlinx.coroutines.launch

/**
 * Displays all signers registered on the connected smart account, consolidated
 * from all on-chain context rules (Default, CallContract, CreateContract).
 */
class KnownSignersScreen : Screen {

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val scope = rememberCoroutineScope()

        data class SignerEntry(
            val signer: SmartAccountSigner,
            val rules: List<ParsedContextRule>
        )

        var isLoading by remember { mutableStateOf(false) }
        var errorMessage by remember { mutableStateOf<String?>(null) }
        val signerEntries = remember { mutableStateListOf<SignerEntry>() }

        fun loadSigners() {
            scope.launch {
                isLoading = true
                errorMessage = null
                signerEntries.clear()
                try {
                    val rules = fetchAllContextRules()

                    // Consolidate signers across all rules, grouped by unique key
                    val signerMap = linkedMapOf<String, Pair<SmartAccountSigner, MutableList<ParsedContextRule>>>()
                    for (rule in rules) {
                        for (signer in rule.signers) {
                            val key = SmartAccountBuilders.getSignerKey(signer)
                            val existing = signerMap[key]
                            if (existing == null) {
                                signerMap[key] = Pair(signer, mutableListOf(rule))
                            } else {
                                existing.second.add(rule)
                            }
                        }
                    }

                    for ((_, pair) in signerMap) {
                        signerEntries.add(SignerEntry(pair.first, pair.second))
                    }

                    ActivityLogState.info("Loaded ${signerEntries.size} unique signer(s) from ${rules.size} context rule(s)")
                } catch (e: Exception) {
                    errorMessage = "Failed to load signers: ${e.message}"
                    ActivityLogState.error("Failed to load signers: ${e.message}")
                } finally {
                    isLoading = false
                }
            }
        }

        LaunchedEffect(Unit) {
            if (DemoState.isConnected && DemoState.kit != null) {
                loadSigners()
            }
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Account Signers") },
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
                            text = "Account Signers",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "All signers registered on this smart account across all context rules.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
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
                                text = "Connect a wallet to view account signers",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    // Refresh button
                    OutlinedButton(
                        onClick = { loadSigners() },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isLoading
                    ) {
                        Text(if (isLoading) "Loading..." else "Refresh")
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

                    // Loading state
                    if (isLoading && signerEntries.isEmpty()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(32.dp))
                            Text(
                                text = "Loading signers...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Empty state
                    if (!isLoading && signerEntries.isEmpty() && errorMessage == null) {
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
                                    text = "No signers found on this account",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    // Signers list
                    if (signerEntries.isNotEmpty()) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            )
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = "${signerEntries.size} signer${if (signerEntries.size != 1) "s" else ""}",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )

                                Spacer(modifier = Modifier.height(12.dp))

                                signerEntries.forEachIndexed { index, entry ->
                                    if (index > 0) {
                                        HorizontalDivider(
                                            modifier = Modifier.padding(vertical = 12.dp),
                                            color = MaterialTheme.colorScheme.outlineVariant
                                        )
                                    }
                                    SignerEntryItem(entry.signer, entry.rules)
                                }
                            }
                        }
                    }
                }

                // Back button
                Button(
                    onClick = { navigator.pop() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Go Back")
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    @Composable
    private fun SignerEntryItem(signer: SmartAccountSigner, rules: List<ParsedContextRule>) {
        val typeDescription = SmartAccountBuilders.describeSignerType(signer)
        val displayInfo = SmartAccountBuilders.formatSignerForDisplay(signer)

        val chipColor = when (typeDescription) {
            "Passkey (WebAuthn)" -> Color(0xFF9C27B0)
            "Stellar Account" -> Color(0xFF2196F3)
            "Ed25519" -> Color(0xFF009688)
            else -> Color(0xFF607D8B)
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // Signer type badge and identifier
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
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

                Text(
                    text = displayInfo.display,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }

            // Context rule memberships
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                rules.forEach { rule ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                            shape = MaterialTheme.shapes.small
                        ) {
                            Text(
                                text = "#${rule.id}",
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Text(
                            text = rule.name.ifEmpty { "Unnamed Rule" },
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = OZBuilders.formatContextType(rule.contextType),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }

    // ============================================================================
    // Data Fetching (mirrors ContextRulesScreen.fetchAllContextRules logic)
    // ============================================================================

    private suspend fun fetchAllContextRules(): List<ParsedContextRule> {
        val kit = DemoState.kit ?: throw IllegalStateException("Kit not initialized")
        val contextMgr = kit.contextRuleManager

        val allRules = mutableListOf<ParsedContextRule>()
        val seenIds = mutableSetOf<UInt>()

        val totalCount = try {
            contextMgr.getContextRulesCount()
        } catch (e: Exception) {
            ActivityLogState.error("Failed to get rule count: ${e.message}")
            0u
        }

        if (totalCount == 0u) {
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

        for (id in 0u until totalCount) {
            try {
                val ruleScVal = contextMgr.getContextRule(id)
                val parsed = parseSingleContextRuleFromScVal(ruleScVal, id)
                if (parsed != null && seenIds.add(parsed.id)) {
                    allRules.add(parsed)
                }
            } catch (_: Exception) {
                // Rule may not exist at this ID (could have been removed)
            }
        }

        return allRules.sortedBy { it.id }
    }

    private fun parseContextRulesFromScVal(scVal: SCValXdr): List<ParsedContextRule> {
        val vec = (scVal as? SCValXdr.Vec)?.value?.value ?: return emptyList()
        return vec.mapNotNull { element ->
            parseSingleContextRuleFromScVal(element, null)
        }
    }

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

    private fun parseContextType(scVal: SCValXdr): ContextRuleType {
        val vec = (scVal as? SCValXdr.Vec)?.value?.value ?: return ContextRuleType.Default
        if (vec.isEmpty()) return ContextRuleType.Default

        val tag = (vec[0] as? SCValXdr.Sym)?.value?.value ?: return ContextRuleType.Default

        return when (tag) {
            "Default" -> ContextRuleType.Default
            "CallContract" -> {
                if (vec.size >= 2) {
                    val address = (vec[1] as? SCValXdr.Address)?.value
                    val addressStr = if (address != null) SmartAccountSharedUtils.extractAddressString(address) else null
                    if (addressStr != null) ContextRuleType.CallContract(addressStr) else ContextRuleType.Default
                } else ContextRuleType.Default
            }
            "CreateContract" -> {
                if (vec.size >= 2) {
                    val bytes = (vec[1] as? SCValXdr.Bytes)?.value?.value
                    if (bytes != null) ContextRuleType.CreateContract(bytes) else ContextRuleType.Default
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
                    val addressStr = if (address != null) SmartAccountSharedUtils.extractAddressString(address) else null
                    if (addressStr != null) {
                        try { com.soneso.stellar.sdk.smartaccount.core.DelegatedSigner(addressStr) } catch (_: Exception) { null }
                    } else null
                } else null
            }
            "External" -> {
                if (vec.size >= 3) {
                    val verifier = (vec[1] as? SCValXdr.Address)?.value
                    val keyData = (vec[2] as? SCValXdr.Bytes)?.value?.value
                    val verifierStr = if (verifier != null) SmartAccountSharedUtils.extractAddressString(verifier) else null
                    if (verifierStr != null && keyData != null) {
                        try { com.soneso.stellar.sdk.smartaccount.core.ExternalSigner(verifierStr, keyData) } catch (_: Exception) { null }
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
}
