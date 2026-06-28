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

/** Interval between approval-inbox pending-count polls while a wallet is connected. */
private const val INBOX_POLL_INTERVAL_MS = 8_000L

@Composable
fun App() {
    // Poll the coordination server for pending agent escalations while connected, keeping
    // the top-bar bell badge current regardless of which screen is on top. The effect is
    // keyed on the connection state so it starts on connect and cancels on disconnect.
    LaunchedEffect(DemoState.isConnected) {
        if (!DemoState.isConnected) return@LaunchedEffect
        val flow = createApprovalInboxFlow()
        while (true) {
            try {
                DemoState.setPendingRequestCount(flow.pendingCount())
            } catch (e: CancellationException) {
                // Cancellation (disconnect re-keys the effect) must propagate so the
                // poll loop stops instead of looping on a cancelled coroutine.
                throw e
            } catch (_: Exception) {
                // Transient failure (server down, offline): keep the last known count and
                // try again on the next tick.
            }
            delay(INBOX_POLL_INTERVAL_MS)
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
