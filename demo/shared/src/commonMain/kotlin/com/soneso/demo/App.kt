package com.soneso.demo

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import cafe.adriel.voyager.navigator.Navigator
import com.soneso.demo.ui.screens.MainScreen

@Composable
fun App() {
    MaterialTheme {
        Navigator(MainScreen())
    }
}
