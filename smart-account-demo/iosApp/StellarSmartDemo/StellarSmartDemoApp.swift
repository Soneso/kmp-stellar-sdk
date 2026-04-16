//
//  StellarSmartDemoApp.swift
//  Smart Account Demo
//
//  Created by Claude on 15.02.26.
//  Copyright © 2026 Soneso. All rights reserved.
//

import SwiftUI
import WalletConnectSign
import shared

// MARK: - App

@main
struct StellarSmartDemoApp: App {

    @UIApplicationDelegateAdaptor(AppDelegate.self) var appDelegate

    var body: some Scene {
        WindowGroup {
            ContentView()
                .onOpenURL { url in
                    // Forward deep link envelopes from the wallet app back to
                    // the WalletConnect Sign client so it can process responses
                    // that arrive via universal or custom-scheme links.
                    appDelegate.handleOpenURL(url)
                }
        }
    }
}

// MARK: - AppDelegate

/// Application delegate that configures the Reown networking stack at launch.
///
/// Reown (WalletConnect v2) requires the `Networking`, `Pair`, and `Sign` singletons
/// to be configured before any wallet operation is attempted. This delegate performs
/// that setup and wires the resulting `ReownWalletHandlerImpl` into the Kotlin
/// `DemoState` via `initSmartAccountKit`.
///
/// If `DemoConfig.REOWN_PROJECT_ID` is blank, wallet connection is silently disabled
/// and the rest of the app runs normally without it.
final class AppDelegate: NSObject, UIApplicationDelegate {

    private var reownHandler: ReownWalletHandlerImpl?

    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
    ) -> Bool {
        let projectId = DemoConfig.shared.REOWN_PROJECT_ID

        #if !targetEnvironment(simulator)
        if !projectId.isEmpty {
            ReownWalletHandlerImpl.configure(projectId: projectId)
            let handler = ReownWalletHandlerImpl()
            self.reownHandler = handler
            PlatformInit_iosKt.doInitSmartAccountKit(reownHandler: handler)
        } else {
            PlatformInit_iosKt.doInitSmartAccountKit(reownHandler: nil)
        }
        #else
        // WalletConnect requires Freighter Mobile on the device.
        // Skip wallet connector on simulator where it cannot work.
        PlatformInit_iosKt.doInitSmartAccountKit(reownHandler: nil)
        #endif

        return true
    }

    /// Forwards WalletConnect link-mode envelopes to the Sign client.
    ///
    /// When a wallet app responds to a session or signing request via a deep link,
    /// it opens the app with a URL containing a `wc_ev` query parameter. The Sign
    /// client decodes the envelope and resolves the pending request.
    func handleOpenURL(_ url: URL) {
        guard let components = URLComponents(url: url, resolvingAgainstBaseURL: false),
              let queryItems = components.queryItems,
              queryItems.contains(where: { $0.name == "wc_ev" }) else {
            return
        }
        do {
            try Sign.instance.dispatchEnvelope(url.absoluteString)
        } catch {
            print("[AppDelegate] Failed to dispatch WalletConnect envelope: \(error.localizedDescription)")
        }
    }
}

// MARK: - Views

struct ContentView: View {
    var body: some View {
        ComposeView()
            .ignoresSafeArea(.all)
    }
}

struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}
