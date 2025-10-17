package com.soneso.demo

import androidx.compose.runtime.Composable

// MacOS native with Compose Multiplatform has limited support compared to iOS
// The `application` function and `ComposeUIViewController` APIs are not available for native macOS
//
// For a true native macOS app, you would need to either:
// 1. Use the JVM desktop target (which is the recommended Compose Multiplatform approach for macOS)
// 2. Build a native SwiftUI UI (not using Compose)
// 3. Wait for better native macOS support in future Compose Multiplatform versions
//
// For now, this module provides access to the shared business logic and App composable
// for potential integration, but full native macOS + Compose integration is limited.

@Suppress("unused")
fun getAppComposable(): @Composable () -> Unit = {
    App()
}
