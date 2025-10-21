import SwiftUI
import shared

// MARK: - Main Screen (matches Compose MainScreen)

struct MainScreen: View {
    @StateObject private var toastManager = ToastManager()

    var body: some View {
        ScrollView {
            VStack(spacing: 24) {
                // App Title
                VStack(spacing: 8) {
                    Text("Stellar SDK Demo")
                        .font(.system(size: 32, weight: .bold))
                        .foregroundStyle(Material3Colors.onSurface)

                    Text("Explore the Stellar SDK features")
                        .font(.system(size: 16))
                        .foregroundStyle(Material3Colors.onSurfaceVariant)
                }
                .frame(maxWidth: .infinity, alignment: .center)
                .padding(.top, 16)

                // Demo topics list
                LazyVStack(spacing: 12) {
                    // Key Generation topic
                    DemoTopicCard(
                        title: "Key Generation",
                        description: "Generate and manage Stellar keypairs",
                        icon: "key.fill",
                        destination: KeyGenerationScreen(toastManager: toastManager)
                    )

                    // Fund Testnet Account topic
                    DemoTopicCard(
                        title: "Fund Testnet Account",
                        description: "Get test XLM from Friendbot for testnet development",
                        icon: "dollarsign.circle.fill",
                        destination: FundAccountScreen(toastManager: toastManager)
                    )

                    // Fetch Account Details topic
                    DemoTopicCard(
                        title: "Fetch Account Details",
                        description: "Retrieve comprehensive account information from Horizon",
                        icon: "person.text.rectangle.fill",
                        destination: AccountDetailsScreen(toastManager: toastManager)
                    )

                    // Trust Asset topic
                    DemoTopicCard(
                        title: "Trust Asset",
                        description: "Establish trustlines to receive non-native assets",
                        icon: "link.badge.plus",
                        destination: TrustAssetScreen(toastManager: toastManager)
                    )

                    // Send a Payment topic
                    DemoTopicCard(
                        title: "Send a Payment",
                        description: "Transfer XLM or issued assets to another account",
                        icon: "paperplane.fill",
                        destination: SendPaymentScreen(toastManager: toastManager)
                    )

                    // Fetch Smart Contract Details topic
                    DemoTopicCard(
                        title: "Fetch Smart Contract Details",
                        description: "Parse contract WASM to view metadata and specification",
                        icon: "curlybraces",
                        destination: ContractDetailsScreen(toastManager: toastManager)
                    )

                    // Deploy Smart Contract topic
                    DemoTopicCard(
                        title: "Deploy Smart Contract",
                        description: "Deploy Soroban contracts with one-step SDK API",
                        icon: "arrow.up.doc.fill",
                        destination: DeployContractView(toastManager: toastManager)
                    )

                    // Invoke Hello World Contract topic
                    DemoTopicCard(
                        title: "Invoke Hello World Contract",
                        description: "Call contract functions with beginner-friendly API",
                        icon: "play.circle.fill",
                        destination: InvokeHelloWorldContractScreen(toastManager: toastManager)
                    )

                    // Invoke Auth Contract topic
                    DemoTopicCard(
                        title: "Invoke Auth Contract",
                        description: "Dynamic authorization handling: same-invoker vs different-invoker scenarios",
                        icon: "checkmark.shield.fill",
                        destination: InvokeAuthContractScreen(toastManager: toastManager)
                    )
                }
            }
            .padding(16)
        }
        .background(Material3Colors.surface)
        .frame(minWidth: 700, minHeight: 600)
        .navigationTitle("Stellar SDK Demo")
        .toast(toastManager)
    }
}
