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
        }
        .windowStyle(.hiddenTitleBar)
        .windowResizability(.contentSize)
    }
}

// MARK: - Material 3 Color Scheme

struct Material3Colors {
    // Primary colors
    static let primaryContainer = Color(red: 0.85, green: 0.90, blue: 1.0)
    static let onPrimaryContainer = Color(red: 0.0, green: 0.11, blue: 0.36)
    static let primary = Color(red: 0.13, green: 0.35, blue: 0.78)

    // Secondary colors
    static let secondaryContainer = Color(red: 0.85, green: 0.92, blue: 0.96)
    static let onSecondaryContainer = Color(red: 0.05, green: 0.20, blue: 0.30)

    // Tertiary colors (for secret seed)
    static let tertiaryContainer = Color(red: 0.98, green: 0.92, blue: 0.85)
    static let onTertiaryContainer = Color(red: 0.35, green: 0.18, blue: 0.03)

    // Error colors
    static let errorContainer = Color(red: 1.0, green: 0.85, blue: 0.85)
    static let onErrorContainer = Color(red: 0.4, green: 0.0, blue: 0.0)

    // Surface colors
    static let surface = Color(white: 0.98)
    static let onSurface = Color(white: 0.1)
    static let onSurfaceVariant = Color(white: 0.4)

    // Card elevation shadow
    static let cardShadow = Color.black.opacity(0.08)

    // Toast colors
    static let toastBackground = Color(red: 0.2, green: 0.2, blue: 0.22)
    static let onToast = Color.white
}

// MARK: - Toast Manager (Shared State)

@MainActor
class ToastManager: ObservableObject {
    @Published var currentToast: ToastData?

    func show(_ message: String, duration: TimeInterval = 2.5) {
        // Dismiss any existing toast
        currentToast = nil

        // Show new toast after a brief delay to ensure animation triggers
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.1) {
            self.currentToast = ToastData(message: message)

            // Auto-dismiss after duration
            DispatchQueue.main.asyncAfter(deadline: .now() + duration) {
                self.currentToast = nil
            }
        }
    }
}

struct ToastData: Identifiable, Equatable {
    let id = UUID()
    let message: String

    static func == (lhs: ToastData, rhs: ToastData) -> Bool {
        lhs.id == rhs.id
    }
}

// MARK: - Toast View

struct ToastView: View {
    let message: String

    var body: some View {
        HStack(spacing: 12) {
            Image(systemName: "checkmark.circle.fill")
                .font(.system(size: 16))
                .foregroundStyle(Material3Colors.onToast)

            Text(message)
                .font(.system(size: 14, weight: .medium))
                .foregroundStyle(Material3Colors.onToast)
        }
        .padding(.horizontal, 20)
        .padding(.vertical, 14)
        .background(Material3Colors.toastBackground)
        .cornerRadius(24)
        .shadow(color: Color.black.opacity(0.2), radius: 8, x: 0, y: 4)
    }
}

// MARK: - Toast Modifier

struct ToastModifier: ViewModifier {
    @ObservedObject var toastManager: ToastManager

    func body(content: Content) -> some View {
        ZStack {
            content

            // Toast overlay
            if let toast = toastManager.currentToast {
                VStack {
                    Spacer()

                    ToastView(message: toast.message)
                        .transition(.move(edge: .bottom).combined(with: .opacity))
                        .padding(.bottom, 32)
                }
                .animation(.spring(response: 0.4, dampingFraction: 0.8), value: toastManager.currentToast)
            }
        }
    }
}

extension View {
    func toast(_ toastManager: ToastManager) -> some View {
        modifier(ToastModifier(toastManager: toastManager))
    }
}

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
                    // Demo topic card
                    DemoTopicCard(
                        title: "Key Generation",
                        description: "Generate and manage Stellar keypairs",
                        icon: "key.fill",
                        destination: KeyGenerationScreen(toastManager: toastManager)
                    )

                    // Future topics can be added here
                    // Examples:
                    // - Transaction Signing
                    // - Account Operations
                    // - Payment Operations
                    // - Asset Management
                    // - Smart Contract Interactions
                }
            }
            .padding(16)
        }
        .background(Material3Colors.surface)
        .frame(minWidth: 700, minHeight: 600)
        .toast(toastManager)
    }
}

// MARK: - Demo Topic Card Component

struct DemoTopicCard<Destination: View>: View {
    let title: String
    let description: String
    let icon: String
    let destination: Destination

    var body: some View {
        NavigationLink(destination: destination) {
            HStack(spacing: 16) {
                // Icon
                Image(systemName: icon)
                    .font(.system(size: 36))
                    .foregroundStyle(Material3Colors.primary)
                    .frame(width: 40, height: 40)

                // Content
                VStack(alignment: .leading, spacing: 4) {
                    Text(title)
                        .font(.system(size: 16, weight: .semibold))
                        .foregroundStyle(Material3Colors.onSurface)

                    Text(description)
                        .font(.system(size: 14))
                        .foregroundStyle(Material3Colors.onSurfaceVariant)
                }
                .frame(maxWidth: .infinity, alignment: .leading)

                // Chevron
                Image(systemName: "chevron.right")
                    .font(.system(size: 14, weight: .semibold))
                    .foregroundStyle(Material3Colors.onSurfaceVariant)
            }
            .padding(16)
            .background(Color.white)
            .cornerRadius(12)
            .shadow(color: Material3Colors.cardShadow, radius: 2, x: 0, y: 1)
        }
        .buttonStyle(.plain)
    }
}

// MARK: - Key Generation Screen (matches Compose KeyGenerationScreen)

struct KeyGenerationScreen: View {
    @Environment(\.dismiss) var dismiss
    @ObservedObject var toastManager: ToastManager
    @State private var keypairData: KeyPairData?
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
                        value: data.accountId,
                        description: "This is your public address. Share this to receive payments.",
                        backgroundColor: Color.white,
                        textColor: Material3Colors.onSurface,
                        descriptionColor: Material3Colors.onSurfaceVariant,
                        iconColor: Material3Colors.primary,
                        onCopy: {
                            copyToClipboard(data.accountId)
                            toastManager.show("Public key copied to clipboard")
                        }
                    )

                    // Secret Seed Card (matches Compose tertiaryContainer)
                    SecretKeyDisplayCard(
                        title: "Secret Seed",
                        value: data.secretSeed,
                        description: "NEVER share this! Anyone with this seed can access your account.",
                        isVisible: $showSecret,
                        onCopy: {
                            copyToClipboard(data.secretSeed)
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
        .navigationBarBackButtonHidden(false)
        .toolbarBackground(Material3Colors.primaryContainer, for: .windowToolbar)
        .toolbarColorScheme(.dark, for: .windowToolbar)
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

// MARK: - Reusable Components

struct KeyDisplayCard: View {
    let title: String
    let value: String
    let description: String
    let backgroundColor: Color
    let textColor: Color
    let descriptionColor: Color
    let iconColor: Color
    let onCopy: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                Text(title)
                    .font(.system(size: 16, weight: .semibold))
                    .foregroundStyle(textColor)

                Spacer()

                Button(action: onCopy) {
                    Image(systemName: "doc.on.doc")
                        .font(.system(size: 16))
                        .foregroundStyle(iconColor)
                }
                .buttonStyle(.plain)
                .help("Copy to clipboard")
            }

            Text(value)
                .font(.system(.body, design: .monospaced))
                .foregroundStyle(descriptionColor)
                .textSelection(.enabled)
                .lineLimit(nil)
                .fixedSize(horizontal: false, vertical: true)

            Text(description)
                .font(.system(size: 13))
                .foregroundStyle(descriptionColor)
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(backgroundColor)
        .cornerRadius(12)
        .shadow(color: Material3Colors.cardShadow, radius: 2, x: 0, y: 1)
    }
}

struct SecretKeyDisplayCard: View {
    let title: String
    let value: String
    let description: String
    @Binding var isVisible: Bool
    let onCopy: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                Text(title)
                    .font(.system(size: 16, weight: .semibold))
                    .foregroundStyle(Material3Colors.onTertiaryContainer)

                Spacer()

                Button(action: { isVisible.toggle() }) {
                    Image(systemName: isVisible ? "eye.slash.fill" : "eye.fill")
                        .font(.system(size: 16))
                        .foregroundStyle(Material3Colors.onTertiaryContainer)
                }
                .buttonStyle(.plain)
                .help(isVisible ? "Hide secret" : "Show secret")

                Button(action: onCopy) {
                    Image(systemName: "doc.on.doc")
                        .font(.system(size: 16))
                        .foregroundStyle(Material3Colors.onTertiaryContainer)
                }
                .buttonStyle(.plain)
                .help("Copy to clipboard")
            }

            Text(isVisible ? value : String(repeating: "•", count: 56))
                .font(.system(.body, design: .monospaced))
                .foregroundStyle(Material3Colors.onTertiaryContainer)
                .textSelection(.enabled)
                .lineLimit(nil)
                .fixedSize(horizontal: false, vertical: true)

            Text(description)
                .font(.system(size: 13))
                .foregroundStyle(Material3Colors.onTertiaryContainer)
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Material3Colors.tertiaryContainer)
        .cornerRadius(12)
        .shadow(color: Material3Colors.cardShadow, radius: 2, x: 0, y: 1)
    }
}

// MARK: - Previews

#Preview("Main Screen") {
    NavigationStack {
        MainScreen()
    }
}

#Preview("Key Generation Screen") {
    NavigationStack {
        KeyGenerationScreen(toastManager: ToastManager())
    }
}
