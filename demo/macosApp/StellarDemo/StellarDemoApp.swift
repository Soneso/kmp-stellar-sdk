import SwiftUI
import shared

// Native macOS app using SwiftUI that mirrors the Compose Multiplatform UI structure
//
// Note: This native macOS app uses SwiftUI instead of Compose Multiplatform because
// Compose does not currently support native macOS window management (only JVM desktop).
//
// The UI structure mirrors the Compose version:
// - MainScreen: Landing page with topic list (Material 3 design)
// - KeyGenerationScreen: Full keypair generation with Material 3-inspired design
// - FundAccountScreen: Testnet account funding
// - AccountDetailsScreen: Fetch and display account details from Horizon
// - TrustAssetScreen: Establish trustlines to assets
// - SendPaymentScreen: Transfer XLM or issued assets
// - ContractDetailsScreen: Fetch and parse smart contract details
//
// Business logic is shared with other platforms via the Kotlin Multiplatform module.
// For a true Compose UI on macOS, see demo/desktopApp/ (JVM desktop target).

@main
struct StellarDemoApp: App {
    var body: some Scene {
        WindowGroup {
            NavigationStack {
                MainScreen()
            }
            .accentColor(Material3Colors.primary)
        }
        .windowStyle(.hiddenTitleBar)
        .windowResizability(.contentSize)
    }
}
