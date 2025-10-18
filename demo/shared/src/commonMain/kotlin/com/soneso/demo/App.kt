package com.soneso.demo

import androidx.compose.runtime.Composable
import cafe.adriel.voyager.navigator.Navigator
import com.soneso.demo.ui.screens.MainScreen
import com.soneso.demo.ui.theme.StellarTheme

@Composable
fun App() {
    StellarTheme {
        Navigator(MainScreen())
    }
}
