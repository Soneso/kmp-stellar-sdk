package com.soneso.demo.desktop

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.soneso.demo.App

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Stellar SDK Demo"
    ) {
        App()
    }
}
