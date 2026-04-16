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
 * Platform initialization (AppleWebAuthnProvider, UserDefaultsStorageAdapter, and the
 * optional Reown wallet connector) is performed by the Swift AppDelegate via
 * [initSmartAccountKit] before this function is called. The Compose UI controller
 * is therefore created with all providers already set in [DemoState].
 *
 * @return UIViewController wrapping the Compose Multiplatform UI
 */
fun MainViewController(): UIViewController {
    return ComposeUIViewController {
        App()
    }
}
