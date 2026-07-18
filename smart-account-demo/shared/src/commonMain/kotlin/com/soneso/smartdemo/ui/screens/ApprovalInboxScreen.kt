package com.soneso.smartdemo.ui.screens

/**
 * Approval inbox screen (steps 4 + 5 of the agent-signer flow).
 *
 * Lists the policy-rejected smart-account calls the autonomous agent escalated to the
 * coordination server, scoped to the connected smart account. Each card shows the smart
 * account, target contract, function, the decoded rejection reason, and — as the
 * authoritative consent data — the recipient and on-chain amount DECODED from the call
 * arguments that actually execute (never the server-supplied display amount). Approving
 * rebuilds the agent's exact call and re-submits it under the user's Default rule
 * (single-signer passkey), then reports the resulting transaction hash back.
 *
 * All SDK and HTTP interaction is delegated to [ApprovalInboxFlow]. This screen never
 * references SDK kit classes or the HTTP client directly.
 */

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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.soneso.smartdemo.flows.ApprovedEntry
import com.soneso.smartdemo.flows.DecodedCall
import com.soneso.smartdemo.flows.DecodedCallKind
import com.soneso.smartdemo.flows.approvedEntryFor
import com.soneso.smartdemo.flows.createApprovalInboxFlow
import com.soneso.smartdemo.flows.describeRejectionReason
import com.soneso.smartdemo.platform.getClipboard
import com.soneso.smartdemo.platform.getUrlOpener
import com.soneso.smartdemo.state.DemoState
import com.soneso.smartdemo.util.CoordinationException
import com.soneso.smartdemo.util.CoordinationRequest
import com.soneso.smartdemo.util.truncateAddress
import kotlinx.coroutines.launch

class ApprovalInboxScreen : Screen {

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val scope = rememberCoroutineScope()
        val snackbarHostState = remember { SnackbarHostState() }
        val flow = remember { createApprovalInboxFlow() }
        val clipboard = remember { getClipboard() }
        val urlOpener = remember { getUrlOpener() }

        var isLoading by remember { mutableStateOf(false) }
        var loaded by remember { mutableStateOf(false) }
        var loadError by remember { mutableStateOf<String?>(null) }
        val pending = remember { mutableStateListOf<CoordinationRequest>() }
        // Decoded consent data per request, populated once in load() (which can fetch the
        // token's decimals over RPC) so the card never decodes XDR during composition.
        val decodedById = remember { mutableStateMapOf<String, DecodedCall>() }

        // IDs with an action in flight (card spinner) and a global in-flight guard so a
        // second card cannot start a concurrent approval.
        val busyIds = remember { mutableStateListOf<String>() }
        var actionInFlight by remember { mutableStateOf(false) }
        // IDs whose transaction confirmed on-chain but whose report-back is outstanding.
        val reportPending = remember { mutableStateListOf<String>() }
        // Session-scoped confirmed approvals, newest first, kept visible and copyable after
        // the confirmation toast disappears. Presentation state only; the durable
        // never-re-submit guard lives in the flow's confirmed-hash store.
        val approvedResults = remember { mutableStateListOf<ApprovedEntry>() }

        var rejectTarget by remember { mutableStateOf<CoordinationRequest?>(null) }
        var rejectNote by remember { mutableStateOf("") }

        suspend fun load() {
            isLoading = true
            loadError = null
            try {
                val result = flow.loadPending()
                pending.clear()
                pending.addAll(result)
                decodedById.clear()
                result.forEach { decodedById[it.id] = flow.decodeCall(it) }
                reportPending.clear()
                reportPending.addAll(result.map { it.id }.filter { flow.isAwaitingReport(it) })
                DemoState.setPendingRequestCount(result.size)
            } catch (e: CoordinationException) {
                loadError = "Could not reach the coordination server: ${e.message}"
            } catch (e: Throwable) {
                loadError = "Could not load pending approvals: ${e.message ?: "Unknown error"}"
            } finally {
                isLoading = false
                loaded = true
            }
        }

        fun removeResolved(id: String) {
            pending.removeAll { it.id == id }
            reportPending.remove(id)
            decodedById.remove(id)
            DemoState.setPendingRequestCount(pending.size)
        }

        fun showSnack(message: String) {
            scope.launch { snackbarHostState.showSnackbar(message) }
        }

        fun recordApproved(entry: ApprovedEntry) {
            approvedResults.removeAll { it.requestId == entry.requestId }
            approvedResults.add(0, entry)
        }

        // Runs a single card action under the global in-flight guard: it refuses a concurrent
        // second action, owns the per-card spinner (busyIds) and the guard flag, and clears
        // both when the action finishes (even on cancellation). Callers supply only the body.
        fun runCardAction(id: String, block: suspend () -> Unit) {
            if (actionInFlight) { showSnack("Another approval is in progress."); return }
            actionInFlight = true
            busyIds.add(id)
            scope.launch {
                try {
                    block()
                } finally {
                    busyIds.remove(id)
                    actionInFlight = false
                }
            }
        }

        fun approve(request: CoordinationRequest) = runCardAction(request.id) {
            val decoded = decodedById[request.id] ?: flow.decodeCall(request)
            val result = flow.approveRequest(request)
            when {
                result.success -> {
                    recordApproved(approvedEntryFor(request, decoded, result))
                    removeResolved(request.id)
                    showSnack("Approved.")
                }
                result.confirmedOnChain -> {
                    // Confirmed on-chain but the report-back failed: surface the result in the
                    // persistent approved list and keep the card so the user can retry the
                    // report without re-submitting the call. This also covers the
                    // confirmed-without-hash case, whose report is retryable too.
                    recordApproved(approvedEntryFor(request, decoded, result))
                    if (!reportPending.contains(request.id)) reportPending.add(request.id)
                    showSnack(
                        result.error
                            ?: "Transaction confirmed on-chain, but reporting it back failed. Retry the report."
                    )
                }
                else -> showSnack(result.error ?: "Approval failed.")
            }
        }

        fun retryReport(request: CoordinationRequest) = runCardAction(request.id) {
            val decoded = decodedById[request.id] ?: flow.decodeCall(request)
            val result = flow.retryReport(request)
            if (result.success) {
                recordApproved(approvedEntryFor(request, decoded, result))
                removeResolved(request.id)
                showSnack("Reported.")
            } else {
                showSnack(result.error ?: "Reporting failed.")
            }
        }

        fun reject(request: CoordinationRequest, note: String) = runCardAction(request.id) {
            val result = flow.rejectRequest(request, note)
            if (result.success) {
                removeResolved(request.id)
                showSnack("Rejected.")
            } else {
                showSnack(result.error ?: "Rejection failed.")
            }
        }

        LaunchedEffect(Unit) {
            if (!loaded) load()
        }

        // Reject dialog
        val target = rejectTarget
        if (target != null) {
            AlertDialog(
                onDismissRequest = { rejectTarget = null },
                title = { Text("Reject escalation") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Why are you rejecting this call?")
                        OutlinedTextField(
                            value = rejectNote,
                            onValueChange = { rejectNote = it },
                            label = { Text("Note (optional)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            val note = rejectNote
                            rejectTarget = null
                            reject(target, note)
                        }
                    ) {
                        Text("Reject", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { rejectTarget = null }) { Text("Cancel") }
                }
            )
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Approval Inbox") },
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
                // Description
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
                            text = "Agent Escalations",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Calls the agent attempted that its on-chain policy rejected. Approving " +
                                "re-submits the exact call under your Default rule (single-signer passkey); " +
                                "rejecting declines it. The recipient and amount shown are decoded from the " +
                                "call that executes.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Connection note
                if (DemoState.isConnected) {
                    Text(
                        text = "Approvals sign as ${truncateAddress(DemoState.contractId ?: "")}. " +
                            "Only escalations for this account are shown.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        text = "Connect a wallet to review escalations for your smart account. The inbox " +
                            "shows only the calls raised against the account you are connected to.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                OutlinedButton(
                    onClick = { scope.launch { load() } },
                    enabled = !isLoading,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (isLoading) "Loading..." else "Refresh")
                }

                if (approvedResults.isNotEmpty()) {
                    ApprovedSection(
                        entries = approvedResults,
                        onCopy = { hash ->
                            scope.launch {
                                clipboard.copyToClipboard(hash)
                                snackbarHostState.showSnackbar("Transaction hash copied")
                            }
                        },
                        onView = { url -> scope.launch { urlOpener.openUrl(url) } }
                    )
                }

                when {
                    isLoading && !loaded -> {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(32.dp))
                        }
                    }
                    loadError != null -> {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                SelectionContainer {
                                    Text(
                                        text = loadError!!,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                                OutlinedButton(onClick = { scope.launch { load() } }) {
                                    Text("Retry")
                                }
                            }
                        }
                    }
                    pending.isEmpty() -> {
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
                                    text = "No pending approvals",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "When the agent escalates a policy-rejected call it appears here " +
                                        "for you to approve or reject.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    else -> {
                        pending.forEach { request ->
                            // Decoded in load(); a request without an entry is not rendered
                            // rather than decoded during composition.
                            val decoded = decodedById[request.id] ?: return@forEach
                            RequestCard(
                                request = request,
                                decoded = decoded,
                                busy = busyIds.contains(request.id),
                                enabled = !actionInFlight,
                                needsReport = reportPending.contains(request.id),
                                onApprove = { approve(request) },
                                onReject = {
                                    if (actionInFlight) {
                                        showSnack("Another approval is in progress.")
                                    } else {
                                        rejectNote = ""
                                        rejectTarget = request
                                    }
                                },
                                onRetryReport = { retryReport(request) }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    @Composable
    private fun ApprovedSection(
        entries: List<ApprovedEntry>,
        onCopy: (String) -> Unit,
        onView: (String) -> Unit
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Approved",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = "Confirmed approvals from this session. Copy the transaction hash or open it on " +
                        "the block explorer.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                entries.forEachIndexed { index, entry ->
                    if (index > 0) {
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f)
                        )
                    }
                    ApprovedResultRow(entry = entry, onCopy = onCopy, onView = onView)
                }
            }
        }
    }

    @Composable
    private fun ApprovedResultRow(
        entry: ApprovedEntry,
        onCopy: (String) -> Unit,
        onView: (String) -> Unit
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            entry.contextLabel?.let { label ->
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            val hash = entry.hash
            if (hash != null) {
                Text(
                    text = "Transaction Hash",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                // The full hash is shown in a SelectionContainer so it can be selected and
                // copied directly; the Copy button copies the full hash too.
                SelectionContainer {
                    Text(
                        text = hash,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = { onCopy(hash) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Copy", style = MaterialTheme.typography.labelSmall)
                    }
                    entry.explorerUrl?.let { url ->
                        OutlinedButton(
                            onClick = { onView(url) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("View on Explorer", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            } else {
                Text(
                    text = "Confirmed on-chain (no transaction hash returned).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }

    @Composable
    private fun RequestCard(
        request: CoordinationRequest,
        decoded: DecodedCall,
        busy: Boolean,
        enabled: Boolean,
        needsReport: Boolean,
        onApprove: () -> Unit,
        onReject: () -> Unit,
        onRetryReport: () -> Unit
    ) {
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
                // Header: function + reason chip
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = request.targetFn,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text(
                            text = describeRejectionReason(request.reason),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                KeyValueRow("Smart Account", truncateAddress(request.smartAccount), monospace = true)
                KeyValueRow("Target", truncateAddress(request.target), monospace = true)
                KeyValueRow("Function", request.targetFn)

                DecodedRows(decoded)

                if (needsReport) {
                    Text(
                        text = "Confirmed on-chain. Reporting the result back to the agent failed; retry the " +
                            "report (the call is not re-submitted).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(
                        onClick = onRetryReport,
                        enabled = enabled && !busy,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (busy) "Reporting..." else "Retry report")
                    }
                } else {
                    val canApprove = enabled && !busy && decoded.kind != DecodedCallKind.UNDECODABLE
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = onApprove,
                            enabled = canApprove,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(if (busy) "Approving..." else "Approve")
                        }
                        OutlinedButton(
                            onClick = onReject,
                            enabled = enabled && !busy,
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            ),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Reject")
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun DecodedRows(decoded: DecodedCall) {
        when (decoded.kind) {
            DecodedCallKind.TRANSFER, DecodedCallKind.APPROVE -> {
                KeyValueRow(
                    decoded.recipientLabel ?: "Recipient",
                    truncateAddress(decoded.recipient ?: "-"),
                    monospace = true
                )
                KeyValueRow("Amount", decoded.amount ?: "-", emphasised = true)
            }
            DecodedCallKind.UNKNOWN -> {
                Text(
                    text = "Arguments",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                decoded.arguments.forEach { arg ->
                    KeyValueRow(arg.label, arg.value, monospace = true)
                }
            }
            DecodedCallKind.UNDECODABLE -> {
                Text(
                    text = decoded.error ?: "Cannot decode the stored call arguments. Do not approve.",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }

    @Composable
    private fun KeyValueRow(
        label: String,
        value: String,
        monospace: Boolean = false,
        emphasised: Boolean = false
    ) {
        // Fixed-width label column with the value left-aligned beside it. A SpaceBetween row
        // flings the value to the far edge on wide web layouts; weighting the value keeps it
        // next to its label while still filling the remaining width on mobile.
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top
        ) {
            Text(
                text = label,
                modifier = Modifier.width(120.dp),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.4.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                modifier = Modifier.weight(1f),
                style = if (emphasised) {
                    MaterialTheme.typography.bodyMedium
                } else {
                    MaterialTheme.typography.bodySmall
                },
                fontFamily = if (monospace) FontFamily.Monospace else FontFamily.Default,
                fontWeight = if (emphasised) FontWeight.Bold else FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
