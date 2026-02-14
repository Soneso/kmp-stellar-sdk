package com.soneso.smartdemo

import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.transitions.SlideTransition
import com.soneso.smartdemo.ui.screens.MainScreen
import com.soneso.smartdemo.ui.theme.SmartAccountTheme

@Composable
fun App() {
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
