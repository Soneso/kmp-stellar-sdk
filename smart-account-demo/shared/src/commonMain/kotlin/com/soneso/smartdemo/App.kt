package com.soneso.smartdemo

import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.transitions.SlideTransition
import com.soneso.smartdemo.flows.createApprovalInboxFlow
import com.soneso.smartdemo.state.DemoState
import com.soneso.smartdemo.ui.screens.MainScreen
import com.soneso.smartdemo.ui.theme.SmartAccountTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

/** Interval between approval-inbox pending-count polls while the coordination server responds. */
private const val INBOX_POLL_INTERVAL_MS = 8_000L

/**
 * Interval between reachability probes while the coordination server is unavailable. Slower
 * than the healthy-poll interval: the only goal while down is to notice recovery, and each
 * failed probe logs a browser-level network error that cannot be suppressed.
 */
private const val INBOX_UNAVAILABLE_POLL_INTERVAL_MS = 60_000L

@Composable
fun App() {
    // Poll the coordination server for pending agent escalations, keeping the top-bar
    // bell badge and the server-availability state current regardless of which screen is
    // on top. The poll runs from app start: while disconnected the count is always 0 (the
    // flow scopes requests to the connected account), but the probe still tracks whether
    // the server is reachable so the bell reflects availability before connecting.
    LaunchedEffect(Unit) {
        val flow = createApprovalInboxFlow()
        while (true) {
            try {
                DemoState.pendingRequestCount = flow.pendingCount()
                DemoState.coordinationAvailable = true
            } catch (e: CancellationException) {
                // Cancellation must propagate so the poll loop stops instead of looping
                // on a cancelled coroutine.
                throw e
            } catch (_: Throwable) {
                // Coordination server unreachable (not started, offline): mark the
                // agent-approval feature unavailable so the bell disables, clear the
                // badge, and keep probing so the bell re-enables when the server is up.
                // Throwable, not Exception: on Kotlin/JS a failed fetch surfaces as a
                // kotlin.Error, and an uncaught throwable here cancels the composition
                // scope and freezes the whole UI.
                DemoState.coordinationAvailable = false
                DemoState.pendingRequestCount = 0
            }
            delay(
                if (DemoState.coordinationAvailable == false) INBOX_UNAVAILABLE_POLL_INTERVAL_MS
                else INBOX_POLL_INTERVAL_MS
            )
        }
    }

    SmartAccountTheme {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.TopCenter
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 1200.dp)
            ) {
                Navigator(MainScreen()) { navigator ->
                    SlideTransition(
                        navigator = navigator,
                        animationSpec = tween(durationMillis = 300)
                    )
                }
            }
        }
    }
}
