import SwiftUI
import shared

// NOTE: This is a **native Swift UI** implementation, not Compose UI
// Compose Multiplatform's native macOS support is limited - the recommended approach
// for macOS is to use the JVM desktop target (see demo/desktopApp/)
//
// This native macOS app demonstrates:
// - Using the Stellar SDK from Swift
// - Native SwiftUI interface
// - Shared business logic from the KMP module
//
// The UI is intentionally kept simple to demonstrate KMP + Swift integration

@main
struct StellarDemoApp: App {
    var body: some Scene {
        WindowGroup {
            ContentView()
        }
        .windowStyle(.hiddenTitleBar)
    }
}

struct ContentView: View {
    @State private var keypairInfo: String = "Click 'Generate Keypair' to start"
    @State private var isLoading: Bool = false

    var body: some View {
        VStack(spacing: 20) {
            Text("Stellar SDK Demo")
                .font(.largeTitle)
                .fontWeight(.bold)

            Text("Native macOS App")
                .font(.title3)
                .foregroundColor(.secondary)

            Divider()
                .padding(.vertical)

            ScrollView {
                Text(keypairInfo)
                    .font(.system(.body, design: .monospaced))
                    .padding()
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(Color.gray.opacity(0.1))
                    .cornerRadius(8)
            }
            .frame(height: 300)

            HStack(spacing: 15) {
                Button("Generate Keypair") {
                    generateKeypair()
                }
                .buttonStyle(.borderedProminent)
                .disabled(isLoading)

                Button("Clear") {
                    keypairInfo = "Click 'Generate Keypair' to start"
                }
                .buttonStyle(.bordered)
            }

            Spacer()

            Text("Using Stellar KMP SDK")
                .font(.caption)
                .foregroundColor(.secondary)
        }
        .padding(30)
        .frame(minWidth: 600, minHeight: 500)
    }

    private func generateKeypair() {
        isLoading = true
        keypairInfo = "Generating keypair..."

        // Call Stellar SDK asynchronously
        Task {
            do {
                // Generate a random keypair using the Stellar SDK
                let keypair = try await KeyPair.Companion.shared.random()

                // Get account ID and secret seed
                let accountId = keypair.getAccountId()
                let secretSeed = String(keypair.getSecretSeed() ?? [])
                let canSign = keypair.canSign()
                let cryptoLib = KeyPair.Companion.shared.getCryptoLibraryName()

                // Sign some test data
                let testData = "Hello, Stellar!".data(using: .utf8)!
                let testBytes = [UInt8](testData)
                let kotlinBytes = KotlinByteArray(size: Int32(testBytes.count))
                for (index, byte) in testBytes.enumerated() {
                    kotlinBytes.set(index: Int32(index), value: Int8(bitPattern: byte))
                }

                let signature = try await keypair.sign(data: kotlinBytes)

                await MainActor.run {
                    keypairInfo = """
                    ✅ Keypair Generated Successfully!

                    Account ID (Public Key):
                    \(accountId)

                    Secret Seed (Keep Private!):
                    \(secretSeed)

                    Can Sign: \(canSign ? "Yes" : "No")
                    Crypto Library: \(cryptoLib)

                    Test Signature:
                    \(signature.description)

                    Note: In a real app, NEVER display the secret seed!
                    This is for demonstration purposes only.
                    """
                    isLoading = false
                }
            } catch {
                await MainActor.run {
                    keypairInfo = "❌ Error: \(error.localizedDescription)"
                    isLoading = false
                }
            }
        }
    }
}

#Preview {
    ContentView()
}
