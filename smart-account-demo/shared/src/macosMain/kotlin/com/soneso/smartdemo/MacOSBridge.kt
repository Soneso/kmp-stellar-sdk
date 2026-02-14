package com.soneso.smartdemo

/**
 * Bridge between Swift UI and Kotlin business logic for native macOS app.
 *
 * Native macOS Compose Multiplatform does not support macOS window management,
 * so the macOS app uses SwiftUI with business logic provided via this bridge.
 */
class MacOSBridge {

    /**
     * Placeholder for future smart account bridge methods.
     * Business logic functions will be added here as features are implemented.
     */
    fun getVersion(): String {
        return "0.1.0"
    }
}
