import SwiftUI
import shared

// MARK: - Key Generation Screen (matches Compose KeyGenerationScreen)

struct KeyGenerationScreen: View {
    @Environment(\.dismiss) var dismiss
    @ObservedObject var toastManager: ToastManager
    @State private var keypairData: KeyPair?
    @State private var isGenerating = false
    @State private var showSecret = false

    private let bridge = MacOSBridge()

    var body: some View {
        ScrollView {
            VStack(spacing: 16) {
                // Information card (matches Compose secondaryContainer)
                VStack(alignment: .leading, spacing: 8) {
                    Text("Stellar Keypair Generation")
                        .font(.system(size: 16, weight: .semibold))
                        .foregroundStyle(Material3Colors.onSecondaryContainer)

                    Text("Generate a cryptographically secure Ed25519 keypair for Stellar network operations. The keypair consists of a public key (account ID starting with 'G') and a secret seed (starting with 'S').")
                        .font(.system(size: 13))
                        .foregroundStyle(Material3Colors.onSecondaryContainer)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(16)
                .background(Material3Colors.secondaryContainer)
                .cornerRadius(12)

                // Generate button
                Button(action: generateKeypair) {
                    HStack(spacing: 8) {
                        if isGenerating {
                            ProgressView()
                                .controlSize(.small)
                                .tint(.white)
                            Text("Generating...")
                        } else {
                            Image(systemName: "arrow.clockwise")
                            Text(keypairData == nil ? "Generate Keypair" : "Generate New Keypair")
                        }
                    }
                    .frame(maxWidth: .infinity)
                    .frame(height: 56)
                    .background(isGenerating ? Material3Colors.primary.opacity(0.6) : Material3Colors.primary)
                    .foregroundColor(.white)
                    .cornerRadius(12)
                }
                .disabled(isGenerating)
                .buttonStyle(.plain)

                // Display generated keypair
                if let data = keypairData {
                    // Public Key Card
                    KeyDisplayCard(
                        title: "Public Key (Account ID)",
                        value: data.getAccountId(),
                        description: "This is your public address. Share this to receive payments.",
                        backgroundColor: Color.white,
                        textColor: Material3Colors.onSurface,
                        descriptionColor: Material3Colors.onSurfaceVariant,
                        iconColor: Material3Colors.primary,
                        onCopy: {
                            copyToClipboard(data.getAccountId())
                            toastManager.show("Public key copied to clipboard")
                        }
                    )

                    // Secret Seed Card (matches Compose tertiaryContainer)
                    SecretKeyDisplayCard(
                        title: "Secret Seed",
                        keypair: data,
                        description: "NEVER share this! Anyone with this seed can access your account.",
                        isVisible: $showSecret,
                        onCopy: {
                            copyToClipboard(data.getSecretSeedAsString() ?? "")
                            toastManager.show("Secret seed copied to clipboard")
                        }
                    )

                    // Security warning (matches Compose errorContainer)
                    VStack(alignment: .leading, spacing: 8) {
                        Text("Security Warning")
                            .font(.system(size: 14, weight: .semibold))
                            .foregroundStyle(Material3Colors.onErrorContainer)

                        Text("Keep your secret seed safe! Store it in a secure password manager or write it down and keep it in a safe place. Anyone who has access to your secret seed can access and control your account.")
                            .font(.system(size: 13))
                            .foregroundStyle(Material3Colors.onErrorContainer)
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(16)
                    .background(Material3Colors.errorContainer)
                    .cornerRadius(12)

                } else if !isGenerating {
                    Spacer()
                        .frame(height: 32)

                    Text("Tap the button above to generate a new Stellar keypair")
                        .font(.system(size: 14))
                        .foregroundStyle(Material3Colors.onSurfaceVariant)
                        .multilineTextAlignment(.center)
                }
            }
            .padding(16)
        }
        .background(Material3Colors.surface)
        .navigationTitle("Key Generation")
        .toolbar {
            ToolbarItem(placement: .navigation) {
                Button(action: {
                    dismiss()
                }) {
                    HStack(spacing: 4) {
                        Image(systemName: "chevron.left")
                            .font(.system(size: 14, weight: .semibold))
                        Text("Back")
                            .font(.system(size: 14))
                    }
                    .foregroundColor(Material3Colors.primary)
                }
                .buttonStyle(.plain)
            }
        }
    }

    private func generateKeypair() {
        isGenerating = true

        Task {
            do {
                let data = try await bridge.generateKeypair()
                await MainActor.run {
                    keypairData = data
                    showSecret = false
                    isGenerating = false
                    toastManager.show("New keypair generated successfully")
                }
            } catch {
                await MainActor.run {
                    isGenerating = false
                    toastManager.show("Error: \(error.localizedDescription)")
                }
            }
        }
    }

    private func copyToClipboard(_ text: String) {
        let pasteboard = NSPasteboard.general
        pasteboard.clearContents()
        pasteboard.setString(text, forType: .string)
    }
}
