//
//  MainViewController.kt
//  Smart Account Demo
//
//  Created by Claude on 15.02.26.
//  Copyright © 2026 Soneso. All rights reserved.
//

package com.soneso.smartdemo

import androidx.compose.ui.window.ComposeUIViewController
import platform.UIKit.UIViewController

/**
 * Creates the main UIViewController for the iOS app.
 *
 * This function initializes the Smart Account Kit with Apple-specific providers
 * (AppleWebAuthnProvider and UserDefaultsStorageAdapter) before creating the
 * Compose UI controller.
 *
 * The initialization happens asynchronously on the main coroutine scope, so the
 * UI will load immediately while the kit initializes in the background.
 *
 * @return UIViewController wrapping the Compose Multiplatform UI
 */
fun MainViewController(): UIViewController {
    // Initialize Smart Account Kit with iOS-specific providers
    initSmartAccountKit()

    // Return Compose UI controller
    return ComposeUIViewController {
        App()
    }
}
