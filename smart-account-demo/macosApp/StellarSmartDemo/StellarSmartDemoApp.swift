import SwiftUI
import shared

@main
struct StellarSmartDemoApp: App {
    var body: some Scene {
        WindowGroup {
            NavigationStack {
                VStack(spacing: 20) {
                    Text("Smart Account Kit")
                        .font(.largeTitle)
                        .fontWeight(.bold)

                    Text("Passkey-based Stellar smart accounts with policy-driven signing")
                        .font(.body)
                        .foregroundColor(.secondary)
                        .multilineTextAlignment(.center)
                        .padding(.horizontal, 40)

                    Text("Demo features coming soon.")
                        .font(.callout)
                        .foregroundColor(.secondary)
                        .padding(.top, 20)
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .background(Color(nsColor: .windowBackgroundColor))
                .navigationTitle("Stellar Smart Account Demo")
            }
        }
        .windowResizability(.contentSize)
    }
}
