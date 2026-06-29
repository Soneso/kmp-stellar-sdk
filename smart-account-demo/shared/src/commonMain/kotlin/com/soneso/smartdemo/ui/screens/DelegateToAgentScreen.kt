package com.soneso.smartdemo.ui.screens

/**
 * Delegate-to-agent screen (step 2 of the agent-signer flow).
 *
 * Grants an autonomous agent a scoped, spend-capped, time-bounded authority on the
 * connected smart account by composing ONE `addContextRule` call: `CallContract(token)`
 * scope + an Ed25519 external signer (the agent's pasted public key) + a spending-limit
 * policy + a `validUntil` bound. The agent owns the matching secret; only its public key
 * is entered here.
 *
 * All SDK interaction is delegated to the flow layer ([delegateToAgent],
 * [resolveSpendingLimitDecimals]). This screen never calls SDK types directly.
 */

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.soneso.smartdemo.config.DemoConfig
import com.soneso.smartdemo.flows.DelegationResult
import com.soneso.smartdemo.flows.delegateToAgent
import com.soneso.smartdemo.flows.resolveSpendingLimitDecimals
import com.soneso.smartdemo.flows.validateAgentPublicKey
import com.soneso.smartdemo.flows.validateDelegationAmount
import com.soneso.smartdemo.platform.getClipboard
import com.soneso.smartdemo.state.ActivityLogState
import com.soneso.smartdemo.state.DemoState
import com.soneso.smartdemo.util.isValidContractAddress
import com.soneso.smartdemo.util.truncateAddress
import com.soneso.stellar.sdk.Util
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Debounce before a custom token's `decimals()` is fetched for the display hint, so a burst
 * of keystrokes while pasting or editing a contract address collapses to a single RPC call.
 */
private const val TOKEN_DECIMALS_DEBOUNCE_MS = 400L

/** Spending-limit rolling-window presets, in ledgers (~5s per ledger on testnet). */
private enum class DelegatePeriodOption(val label: String, val ledgers: UInt) {
    PER_HOUR("Per hour", Util.LEDGERS_PER_HOUR.toUInt()),
    PER_DAY("Per day", Util.LEDGERS_PER_DAY.toUInt()),
    PER_7_DAYS("Per 7 days", (Util.LEDGERS_PER_DAY * 7).toUInt()),
    PER_30_DAYS("Per 30 days", (Util.LEDGERS_PER_DAY * 30).toUInt()),
}

/** Rule-expiry (`validUntil`) presets, expressed as a ledger offset from now. */
private enum class DelegateExpiryOption(val label: String, val offset: UInt) {
    ONE_DAY("1 day (~24h)", Util.LEDGERS_PER_DAY.toUInt()),
    THREE_DAYS("3 days", (Util.LEDGERS_PER_DAY * 3).toUInt()),
    SEVEN_DAYS("7 days", (Util.LEDGERS_PER_DAY * 7).toUInt()),
    THIRTY_DAYS("30 days", (Util.LEDGERS_PER_DAY * 30).toUInt()),
}

class DelegateToAgentScreen : Screen {

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val scope = rememberCoroutineScope()
        val snackbarHostState = remember { SnackbarHostState() }
        val clipboard = remember { getClipboard() }

        // --- Form state ---
        var agentKey by remember { mutableStateOf("") }
        var tokenContract by remember {
            mutableStateOf(DemoState.demoTokenContractId ?: DemoConfig.NATIVE_TOKEN_CONTRACT)
        }
        var amount by remember { mutableStateOf("") }
        var period by remember { mutableStateOf(DelegatePeriodOption.PER_DAY) }
        var expiry by remember { mutableStateOf(DelegateExpiryOption.ONE_DAY) }

        var agentKeyError by remember { mutableStateOf<String?>(null) }
        var tokenError by remember { mutableStateOf<String?>(null) }
        var amountError by remember { mutableStateOf<String?>(null) }

        // Resolved decimal scale of the guarded token, used ONLY for the display hint below.
        // The submit path does not trust it: delegateToAgent re-resolves the decimals itself
        // and fails closed. Defaults to the demo token's scale until a custom token's
        // decimals() is fetched.
        var tokenDecimals by remember { mutableStateOf(DemoConfig.DEMO_TOKEN_DECIMALS) }

        // --- Operation state ---
        var isSubmitting by remember { mutableStateOf(false) }
        var errorMessage by remember { mutableStateOf<String?>(null) }
        var result by remember { mutableStateOf<DelegationResult?>(null) }
        var resultPeriod by remember { mutableStateOf(DelegatePeriodOption.PER_DAY) }
        var resultExpiry by remember { mutableStateOf(DelegateExpiryOption.ONE_DAY) }

        // Resolve the guarded token's decimal scale for the display hint whenever the token
        // changes. The native and demo tokens resolve without a network call; any other token
        // is fetched over RPC, but only once it is a complete, valid contract address and after
        // a short debounce, so the decimals() call does not fire on every keystroke. Re-keying
        // on the token cancels an in-flight resolution when the token changes, so a stale late
        // response cannot overwrite a newer one. Failures are non-fatal: keep the current scale.
        LaunchedEffect(tokenContract) {
            val token = tokenContract.trim()
            if (token.isEmpty() ||
                token == DemoConfig.NATIVE_TOKEN_CONTRACT ||
                token == DemoState.demoTokenContractId
            ) {
                tokenDecimals = DemoConfig.DEMO_TOKEN_DECIMALS
                return@LaunchedEffect
            }
            if (!isValidContractAddress(token)) return@LaunchedEffect
            delay(TOKEN_DECIMALS_DEBOUNCE_MS)
            try {
                tokenDecimals = resolveSpendingLimitDecimals(token)
            } catch (e: CancellationException) {
                // Re-keying the effect cancels this RPC resolution; let the
                // cancellation propagate instead of swallowing it as a failure.
                throw e
            } catch (_: Exception) {
                // Keep the previous scale.
            }
        }

        val isFormValid = agentKey.trim().isNotEmpty() && agentKeyError == null &&
            tokenContract.trim().isNotEmpty() && tokenError == null &&
            amount.trim().isNotEmpty() && amountError == null

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Delegate to Agent") },
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
                val successResult = result?.takeIf { it.success }

                if (!DemoState.isConnected) {
                    NotConnectedCard()
                } else if (successResult != null) {
                    ResultCard(
                        result = successResult,
                        period = resultPeriod,
                        expiry = resultExpiry,
                        onCopy = { value, what ->
                            scope.launch {
                                clipboard.copyToClipboard(value)
                                snackbarHostState.showSnackbar("$what copied to clipboard")
                            }
                        },
                        onDone = { navigator.pop() }
                    )
                } else {
                    DescriptionCard()

                    // Agent Ed25519 public key
                    OutlinedTextField(
                        value = agentKey,
                        onValueChange = {
                            agentKey = it
                            agentKeyError = validateAgentPublicKey(it)
                            errorMessage = null
                        },
                        label = { Text("Agent Ed25519 Public Key (hex)") },
                        placeholder = { Text("64 hex characters") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        enabled = !isSubmitting,
                        isError = agentKeyError != null,
                        supportingText = {
                            Text(
                                agentKeyError
                                    ?: "The agent's Ed25519 public key in hex (printed on its startup line)"
                            )
                        }
                    )

                    // Scoped token contract
                    OutlinedTextField(
                        value = tokenContract,
                        onValueChange = {
                            tokenContract = it
                            val trimmed = it.trim()
                            tokenError = if (trimmed.isEmpty() || isValidContractAddress(trimmed)) {
                                null
                            } else {
                                "Must be a valid Stellar contract (C...) address"
                            }
                            errorMessage = null
                        },
                        label = { Text("Token Contract") },
                        placeholder = { Text("C...") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        enabled = !isSubmitting,
                        isError = tokenError != null,
                        supportingText = {
                            Text(tokenError ?: "The only token the agent may call (defaults to the demo token)")
                        }
                    )

                    // Spending cap
                    OutlinedTextField(
                        value = amount,
                        onValueChange = {
                            amount = it
                            amountError = validateDelegationAmount(it)
                            errorMessage = null
                        },
                        label = { Text("Spending Limit") },
                        placeholder = { Text("e.g. 100.0") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        enabled = !isSubmitting,
                        isError = amountError != null,
                        supportingText = {
                            Text(
                                amountError
                                    ?: "Maximum the agent may spend per period " +
                                    "(up to $tokenDecimals decimal places)"
                            )
                        }
                    )

                    // Spending-limit period
                    EnumDropdown(
                        label = "Spending Limit Period",
                        selectedLabel = period.label,
                        options = DelegatePeriodOption.entries.map { it.label },
                        enabled = !isSubmitting,
                        onSelect = { index -> period = DelegatePeriodOption.entries[index] }
                    )

                    // Rule expiry
                    EnumDropdown(
                        label = "Rule Expires In",
                        selectedLabel = expiry.label,
                        options = DelegateExpiryOption.entries.map { it.label },
                        enabled = !isSubmitting,
                        onSelect = { index -> expiry = DelegateExpiryOption.entries[index] }
                    )

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

                    Button(
                        onClick = {
                            // Final validation pass before submit.
                            val keyError = validateAgentPublicKey(agentKey)
                                ?: if (agentKey.trim().isEmpty()) "Agent public key is required" else null
                            val amtError = validateDelegationAmount(amount)
                                ?: if (amount.trim().isEmpty()) "Spending limit is required" else null
                            val trimmedToken = tokenContract.trim()
                            val tokError = when {
                                trimmedToken.isEmpty() -> "Token contract is required"
                                !isValidContractAddress(trimmedToken) ->
                                    "Must be a valid Stellar contract (C...) address"
                                else -> null
                            }
                            if (keyError != null || amtError != null || tokError != null) {
                                agentKeyError = keyError
                                amountError = amtError
                                tokenError = tokError
                                return@Button
                            }

                            errorMessage = null
                            isSubmitting = true
                            val submitPeriod = period
                            val submitExpiry = expiry

                            scope.launch {
                                try {
                                    val submitted = delegateToAgent(
                                        agentPublicKey = agentKey,
                                        tokenContract = trimmedToken,
                                        amount = amount.trim(),
                                        periodLedgers = submitPeriod.ledgers,
                                        validUntilOffsetLedgers = submitExpiry.offset
                                    )
                                    if (submitted.success) {
                                        resultPeriod = submitPeriod
                                        resultExpiry = submitExpiry
                                        result = submitted
                                    } else {
                                        errorMessage = submitted.error ?: "Delegation failed."
                                    }
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (e: Throwable) {
                                    // delegateToAgent maps its own failures (including a
                                    // passkey cancellation) to DelegationResult.error; this
                                    // guards only against an unexpected throw escaping it.
                                    val msg = e.message ?: "Unknown error"
                                    ActivityLogState.error("Delegation failed: $msg")
                                    errorMessage = msg
                                } finally {
                                    isSubmitting = false
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = isFormValid && !isSubmitting
                    ) {
                        if (isSubmitting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Submitting (requires authorization)...")
                        } else {
                            Text("Delegate to Agent")
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }

    @Composable
    private fun DescriptionCard() {
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
                    text = "Delegate to an Agent",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Register an agent as an Ed25519 external signer in one context rule: " +
                        "scoped to a single token contract, capped by a spending limit, and expiring " +
                        "after a set time. The agent holds its own secret; paste only its public key " +
                        "(64-char hex).",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    @Composable
    private fun NotConnectedCard() {
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
                    text = "Connect a wallet to delegate to an agent.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    @Composable
    private fun ResultCard(
        result: DelegationResult,
        period: DelegatePeriodOption,
        expiry: DelegateExpiryOption,
        onCopy: (value: String, what: String) -> Unit,
        onDone: () -> Unit
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF4CAF50).copy(alpha = 0.12f)
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Agent Authorised",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF2E7D32)
                )
                Text(
                    text = "The agent can now sign calls to the scoped token, up to the spending cap, " +
                        "until the rule expires.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF2E7D32)
                )

                val summary = result.summary
                if (summary != null) {
                    KeyValueRow("Agent Key", summary.agentPublicKey, monospace = true)
                    KeyValueRow("Scope", "CallContract")
                    CopyableRow(
                        label = "Token Contract",
                        value = summary.tokenContract,
                        onCopy = { onCopy(summary.tokenContract, "Token contract") }
                    )
                    Text(
                        text = "Start the reference agent with this token contract (AGENT_TOKEN_CONTRACT).",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF2E7D32)
                    )
                    KeyValueRow("Cap", "${summary.amount} ${period.label.lowercase()}")
                    KeyValueRow(
                        "Expires",
                        summary.validUntilLedger?.let { "Ledger $it (${expiry.label})" } ?: "Never"
                    )
                    KeyValueRow("Verifier", truncateAddress(summary.verifierAddress), monospace = true)
                    KeyValueRow("Policy", truncateAddress(summary.spendingLimitPolicyAddress), monospace = true)
                }

                if (result.hash != null) {
                    Text(
                        text = "Hash: ${result.hash}",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = Color(0xFF2E7D32),
                        modifier = Modifier.clickable { onCopy(result.hash, "Transaction hash") }
                    )
                    Text(
                        text = "Tap to copy",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF2E7D32).copy(alpha = 0.6f)
                    )
                }

                Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) {
                    Text("Done")
                }
            }
        }
    }

    @Composable
    private fun KeyValueRow(label: String, value: String, monospace: Boolean = false) {
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = if (monospace) FontFamily.Monospace else FontFamily.Default
            )
        }
    }

    /**
     * A labelled monospace value that copies its full content when tapped. Used
     * for the scoped token contract on the result card so it can be pasted into
     * the reference agent's `AGENT_TOKEN_CONTRACT`.
     */
    @Composable
    private fun CopyableRow(label: String, value: String, onCopy: () -> Unit) {
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = Color(0xFF2E7D32),
                modifier = Modifier.clickable(onClick = onCopy)
            )
            Text(
                text = "Tap to copy",
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF2E7D32).copy(alpha = 0.6f)
            )
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun EnumDropdown(
        label: String,
        selectedLabel: String,
        options: List<String>,
        enabled: Boolean,
        onSelect: (Int) -> Unit
    ) {
        var expanded by remember { mutableStateOf(false) }
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { if (enabled) expanded = it }
        ) {
            OutlinedTextField(
                value = selectedLabel,
                onValueChange = {},
                readOnly = true,
                enabled = enabled,
                label = { Text(label) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier
                    .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                    .fillMaxWidth(),
                colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors()
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                options.forEachIndexed { index, optionLabel ->
                    DropdownMenuItem(
                        text = { Text(optionLabel) },
                        onClick = {
                            onSelect(index)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}
