import SwiftUI
import shared

// MARK: - Kotlin Interop Extensions

extension KeyPair {
    /// Convert Kotlin CharArray to Swift String
    /// The secret seed is kept as CharArray in the SDK for better security,
    /// so we only convert it to String in the UI layer when needed for display.
    func getSecretSeedAsString() -> String? {
        guard let charArray = getSecretSeed() else { return nil }
        var characters: [Character] = []
        for i in 0..<charArray.size {
            let unicodeValue = UInt16(charArray.get(index: i))
            if let scalar = UnicodeScalar(unicodeValue) {
                characters.append(Character(scalar))
            }
        }
        return String(characters)
    }
}

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
            .accentColor(Material3Colors.primary) // Apply blue accent to all interactive elements including back button
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

    // Success colors (matching primaryContainer for success states)
    static let successContainer = Color(red: 0.85, green: 0.95, blue: 0.87)
    static let onSuccessContainer = Color(red: 0.0, green: 0.3, blue: 0.1)

    // Surface colors
    static let surface = Color(white: 0.98)
    static let surfaceVariant = Color(white: 0.94)
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

// MARK: - Fund Account Screen (matches Compose FundAccountScreen)

struct FundAccountScreen: View {
    @Environment(\.dismiss) var dismiss
    @ObservedObject var toastManager: ToastManager
    @State private var accountId = ""
    @State private var isGenerating = false
    @State private var isFunding = false
    @State private var fundingResult: AccountFundingResult?
    @State private var validationError: String?

    private let bridge = MacOSBridge()

    var body: some View {
        ScrollView {
            VStack(spacing: 16) {
                infoCard
                inputField
                actionButtons
                resultView
                placeholderView
            }
            .padding(16)
        }
        .background(Material3Colors.surface)
        .navigationTitle("Fund Testnet Account")
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

    // MARK: - View Components

    private var infoCard: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Friendbot: fund a testnet network account")
                .font(.system(size: 16, weight: .semibold))
                .foregroundStyle(Material3Colors.onSecondaryContainer)

            Text("The friendbot is a horizon API endpoint that will fund an account with 10,000 lumens on the testnet network.")
                .font(.system(size: 13))
                .foregroundStyle(Material3Colors.onSecondaryContainer)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(16)
        .background(Material3Colors.secondaryContainer)
        .cornerRadius(12)
    }

    private var inputField: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text("Public Key")
                .font(.system(size: 12, weight: .medium))
                .foregroundStyle(Material3Colors.onSurfaceVariant)

            TextField("G...", text: $accountId)
                .textFieldStyle(.plain)
                .font(.system(.body, design: .monospaced))
                .padding(12)
                .background(Color.white)
                .overlay(
                    RoundedRectangle(cornerRadius: 4)
                        .stroke(validationError != nil ? Material3Colors.onErrorContainer : Material3Colors.onSurfaceVariant.opacity(0.3), lineWidth: 1)
                )
                .onChange(of: accountId) { _ in
                    validationError = nil
                    fundingResult = nil
                }

            if let error = validationError {
                Text(error)
                    .font(.system(size: 12))
                    .foregroundStyle(Material3Colors.onErrorContainer)
            }
        }
    }

    private var actionButtons: some View {
        HStack(spacing: 8) {
            generateButton
            fundButton
        }
    }

    private var generateButton: some View {
        Button(action: generateAndFill) {
            HStack(spacing: 4) {
                if isGenerating {
                    ProgressView()
                        .controlSize(.small)
                    Text("Generating...")
                        .font(.system(size: 13))
                } else {
                    Image(systemName: "arrow.clockwise")
                        .font(.system(size: 16))
                    Text("Generate & Fill")
                        .font(.system(size: 13))
                }
            }
            .frame(maxWidth: .infinity)
            .frame(height: 56)
            .foregroundColor(Material3Colors.primary)
            .overlay(
                RoundedRectangle(cornerRadius: 12)
                    .stroke(Material3Colors.primary, lineWidth: 1)
            )
        }
        .disabled(isGenerating || isFunding)
        .buttonStyle(.plain)
    }

    private var fundButton: some View {
        Button(action: fundAccount) {
            HStack(spacing: 8) {
                if isFunding {
                    ProgressView()
                        .controlSize(.small)
                        .tint(.white)
                    Text("Funding...")
                } else {
                    Image(systemName: "dollarsign.circle")
                    Text("Get lumens")
                }
            }
            .frame(maxWidth: .infinity)
            .frame(height: 56)
            .background((isFunding || accountId.isEmpty) ? Material3Colors.primary.opacity(0.6) : Material3Colors.primary)
            .foregroundColor(.white)
            .cornerRadius(12)
        }
        .disabled(isGenerating || isFunding || accountId.isEmpty)
        .buttonStyle(.plain)
    }

    @ViewBuilder
    private var resultView: some View {
        if let result = fundingResult {
            if let success = result as? AccountFundingResult.Success {
                successCard(success)
            } else if let error = result as? AccountFundingResult.Error {
                errorCard(error)
                troubleshootingCard
            }
        }
    }

    private func successCard(_ success: AccountFundingResult.Success) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Success")
                .font(.system(size: 16, weight: .semibold))
                .foregroundStyle(Material3Colors.onSuccessContainer)

            Text("Successfully funded \(shortenAccountId(success.accountId)) on testnet")
                .font(.system(size: 14))
                .foregroundStyle(Material3Colors.onSuccessContainer)

            Text(success.message)
                .font(.system(size: 13))
                .foregroundStyle(Material3Colors.onSuccessContainer)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(16)
        .background(Material3Colors.successContainer)
        .cornerRadius(12)
    }

    private func errorCard(_ error: AccountFundingResult.Error) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Error")
                .font(.system(size: 16, weight: .semibold))
                .foregroundStyle(Material3Colors.onErrorContainer)

            Text(error.message)
                .font(.system(size: 14))
                .foregroundStyle(Material3Colors.onErrorContainer)

            if let exception = error.exception {
                Text("Technical details: \(exception.message ?? "Unknown error")")
                    .font(.system(size: 13, design: .monospaced))
                    .foregroundStyle(Material3Colors.onErrorContainer)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(16)
        .background(Material3Colors.errorContainer)
        .cornerRadius(12)
    }

    private var troubleshootingCard: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Troubleshooting")
                .font(.system(size: 14, weight: .semibold))
                .foregroundStyle(Material3Colors.onSecondaryContainer)

            VStack(alignment: .leading, spacing: 4) {
                Text("• Check that the account ID is valid (starts with 'G' and is 56 characters)")
                    .font(.system(size: 13))
                    .foregroundStyle(Material3Colors.onSecondaryContainer)

                Text("• If the account was already funded, it cannot be funded again")
                    .font(.system(size: 13))
                    .foregroundStyle(Material3Colors.onSecondaryContainer)

                Text("• Verify you have an internet connection")
                    .font(.system(size: 13))
                    .foregroundStyle(Material3Colors.onSecondaryContainer)

                Text("• Try generating a new keypair if the issue persists")
                    .font(.system(size: 13))
                    .foregroundStyle(Material3Colors.onSecondaryContainer)
            }
            .padding(.leading, 8)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(16)
        .background(Material3Colors.secondaryContainer)
        .cornerRadius(12)
    }

    @ViewBuilder
    private var placeholderView: some View {
        if fundingResult == nil && !isFunding && accountId.isEmpty {
            Spacer()
                .frame(height: 16)

            Text("Enter a public key or generate a new keypair to fund the account with testnet XLM")
                .font(.system(size: 14))
                .foregroundStyle(Material3Colors.onSurfaceVariant)
                .multilineTextAlignment(.center)
        }
    }

    // MARK: - Actions

    private func validateAccountId(_ id: String) -> String? {
        if id.isEmpty {
            return "Account ID is required"
        }
        if !id.hasPrefix("G") {
            return "Account ID must start with 'G'"
        }
        if id.count != 56 {
            return "Account ID must be 56 characters long"
        }
        return nil
    }

    private func generateAndFill() {
        isGenerating = true

        Task {
            do {
                let keypair = try await bridge.generateKeypair()
                await MainActor.run {
                    accountId = keypair.getAccountId()
                    validationError = nil
                    fundingResult = nil
                    isGenerating = false
                    toastManager.show("New keypair generated and filled")
                }
            } catch {
                await MainActor.run {
                    isGenerating = false
                    toastManager.show("Failed to generate keypair: \(error.localizedDescription)")
                }
            }
        }
    }

    private func fundAccount() {
        // Validate before funding
        if let error = validateAccountId(accountId) {
            validationError = error
            toastManager.show(error)
            return
        }

        isFunding = true
        fundingResult = nil

        Task {
            do {
                let result = try await bridge.fundAccount(accountId: accountId)
                await MainActor.run {
                    fundingResult = result
                    isFunding = false
                }
            } catch {
                await MainActor.run {
                    fundingResult = AccountFundingResult.Error(
                        message: "Failed to fund account: \(error.localizedDescription)",
                        exception: error as? KotlinThrowable
                    )
                    isFunding = false
                }
            }
        }
    }

    private func shortenAccountId(_ id: String) -> String {
        if id.count > 12 {
            return "\(id.prefix(4))...\(id.suffix(4))"
        }
        return id
    }
}

// MARK: - Account Details Screen (matches Compose AccountDetailsScreen)

struct AccountDetailsScreen: View {
    @Environment(\.dismiss) var dismiss
    @ObservedObject var toastManager: ToastManager
    @State private var accountId = ""
    @State private var isFetching = false
    @State private var detailsResult: AccountDetailsResult?
    @State private var validationError: String?

    private let bridge = MacOSBridge()

    var body: some View {
        ScrollView {
            VStack(spacing: 16) {
                infoCard
                inputField
                fetchButton
                resultView
                placeholderView
            }
            .padding(16)
        }
        .background(Material3Colors.surface)
        .navigationTitle("Fetch Account Details")
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

    // MARK: - View Components

    private var infoCard: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Horizon API: fetch account details")
                .font(.system(size: 16, weight: .semibold))
                .foregroundStyle(Material3Colors.onSecondaryContainer)

            Text("Enter a Stellar account ID to retrieve comprehensive account information including balances, signers, thresholds, and more.")
                .font(.system(size: 13))
                .foregroundStyle(Material3Colors.onSecondaryContainer)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(16)
        .background(Material3Colors.secondaryContainer)
        .cornerRadius(12)
    }

    private var inputField: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text("Account ID")
                .font(.system(size: 12, weight: .medium))
                .foregroundStyle(Material3Colors.onSurfaceVariant)

            TextField("G...", text: $accountId)
                .textFieldStyle(.plain)
                .font(.system(.body, design: .monospaced))
                .padding(12)
                .background(Color.white)
                .overlay(
                    RoundedRectangle(cornerRadius: 4)
                        .stroke(validationError != nil ? Material3Colors.onErrorContainer : Material3Colors.onSurfaceVariant.opacity(0.3), lineWidth: 1)
                )
                .onChange(of: accountId) { _ in
                    validationError = nil
                    detailsResult = nil
                }

            if let error = validationError {
                Text(error)
                    .font(.system(size: 12))
                    .foregroundStyle(Material3Colors.onErrorContainer)
            }
        }
    }

    private var fetchButton: some View {
        Button(action: fetchDetails) {
            HStack(spacing: 8) {
                if isFetching {
                    ProgressView()
                        .controlSize(.small)
                        .tint(.white)
                    Text("Fetching...")
                } else {
                    Image(systemName: "magnifyingglass")
                    Text("Fetch Details")
                }
            }
            .frame(maxWidth: .infinity)
            .frame(height: 56)
            .background((isFetching || accountId.isEmpty) ? Material3Colors.primary.opacity(0.6) : Material3Colors.primary)
            .foregroundColor(.white)
            .cornerRadius(12)
        }
        .disabled(isFetching || accountId.isEmpty)
        .buttonStyle(.plain)
    }

    @ViewBuilder
    private var resultView: some View {
        if let result = detailsResult {
            switch result {
            case let success as AccountDetailsResult.Success:
                AccountDetailsCard(account: success.accountResponse)
            case let error as AccountDetailsResult.Error:
                errorCard(error)
                troubleshootingCard
            default:
                EmptyView()
            }
        }
    }

    private func errorCard(_ error: AccountDetailsResult.Error) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Error")
                .font(.system(size: 16, weight: .semibold))
                .foregroundStyle(Material3Colors.onErrorContainer)

            Text(error.message)
                .font(.system(size: 14))
                .foregroundStyle(Material3Colors.onErrorContainer)

            if let exception = error.exception {
                Text("Technical details: \(exception.message ?? "Unknown error")")
                    .font(.system(size: 13, design: .monospaced))
                    .foregroundStyle(Material3Colors.onErrorContainer)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(16)
        .background(Material3Colors.errorContainer)
        .cornerRadius(12)
    }

    private var troubleshootingCard: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Troubleshooting")
                .font(.system(size: 14, weight: .semibold))
                .foregroundStyle(Material3Colors.onSecondaryContainer)

            VStack(alignment: .leading, spacing: 4) {
                Text("• Verify the account ID is valid (starts with 'G' and is 56 characters)")
                    .font(.system(size: 13))
                    .foregroundStyle(Material3Colors.onSecondaryContainer)

                Text("• Make sure the account exists on testnet (fund it via Friendbot if needed)")
                    .font(.system(size: 13))
                    .foregroundStyle(Material3Colors.onSecondaryContainer)

                Text("• Check your internet connection")
                    .font(.system(size: 13))
                    .foregroundStyle(Material3Colors.onSecondaryContainer)

                Text("• Try again in a moment if you're being rate-limited")
                    .font(.system(size: 13))
                    .foregroundStyle(Material3Colors.onSecondaryContainer)
            }
            .padding(.leading, 8)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(16)
        .background(Material3Colors.secondaryContainer)
        .cornerRadius(12)
    }

    @ViewBuilder
    private var placeholderView: some View {
        if detailsResult == nil && !isFetching && accountId.isEmpty {
            Spacer()
                .frame(height: 16)

            Image(systemName: "person.text.rectangle")
                .font(.system(size: 64))
                .foregroundStyle(Material3Colors.onSurfaceVariant.opacity(0.3))

            Text("Enter an account ID to view its details on the testnet")
                .font(.system(size: 14))
                .foregroundStyle(Material3Colors.onSurfaceVariant)
                .multilineTextAlignment(.center)
        }
    }

    // MARK: - Actions

    private func validateAccountId(_ id: String) -> String? {
        if id.isEmpty {
            return "Account ID is required"
        }
        if !id.hasPrefix("G") {
            return "Account ID must start with 'G'"
        }
        if id.count != 56 {
            return "Account ID must be 56 characters long"
        }
        return nil
    }

    private func fetchDetails() {
        // Validate before fetching
        if let error = validateAccountId(accountId) {
            validationError = error
            toastManager.show(error)
            return
        }

        isFetching = true
        detailsResult = nil

        Task {
            do {
                let result = try await bridge.fetchAccountDetails(accountId: accountId, useTestnet: true)
                await MainActor.run {
                    detailsResult = result
                    isFetching = false
                }
            } catch {
                await MainActor.run {
                    detailsResult = AccountDetailsResult.Error(
                        message: "Failed to fetch account details: \(error.localizedDescription)",
                        exception: error as? KotlinThrowable
                    )
                    isFetching = false
                }
            }
        }
    }

    private func shortenAccountId(_ id: String) -> String {
        if id.count > 12 {
            return "\(id.prefix(4))...\(id.suffix(4))"
        }
        return id
    }
}

// MARK: - Trust Asset Screen (matches Compose TrustAssetScreen)

struct TrustAssetScreen: View {
    @Environment(\.dismiss) var dismiss
    @ObservedObject var toastManager: ToastManager
    @State private var accountId = ""
    @State private var assetCode = "SRT"
    @State private var assetIssuer = "GCDNJUBQSX7AJWLJACMJ7I4BC3Z47BQUTMHEICZLE6MU4KQBRYG5JY6B"
    @State private var trustLimit = ""
    @State private var secretSeed = ""
    @State private var showSecret = false
    @State private var isSubmitting = false
    @State private var trustResult: TrustAssetResult?
    @State private var validationError: String?

    private let bridge = MacOSBridge()

    var body: some View {
        ScrollView {
            VStack(spacing: 16) {
                infoCard
                inputFields
                submitButton
                resultView
                placeholderView
            }
            .padding(16)
        }
        .background(Material3Colors.surface)
        .navigationTitle("Trust Asset")
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

    // MARK: - View Components

    private var infoCard: some View {
        VStack(spacing: 12) {
            VStack(alignment: .leading, spacing: 8) {
                Text("Establish a Trustline")
                    .font(.system(size: 16, weight: .semibold))
                    .foregroundStyle(Material3Colors.onSecondaryContainer)

                Text("A trustline is required before an account can hold non-native assets (assets other than XLM). This creates a ChangeTrust operation that allows your account to hold up to a specified limit of the asset.")
                    .font(.system(size: 13))
                    .foregroundStyle(Material3Colors.onSecondaryContainer)

                Divider()
                    .padding(.vertical, 4)

                Text("Important: Your account must have at least 0.5 XLM base reserve for each trustline.")
                    .font(.system(size: 12, weight: .medium))
                    .foregroundStyle(Material3Colors.onSecondaryContainer)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(16)
            .background(Material3Colors.secondaryContainer)
            .cornerRadius(12)

            // Example Asset Card
            VStack(alignment: .leading, spacing: 8) {
                Text("Example Testnet Asset")
                    .font(.system(size: 14, weight: .bold))
                    .foregroundStyle(Material3Colors.onPrimaryContainer)

                VStack(alignment: .leading, spacing: 4) {
                    Text("The asset code and issuer fields are pre-filled with SRT, a testnet asset provided by Stellar as part of the testnet anchor.")
                        .font(.system(size: 13))
                        .foregroundStyle(Material3Colors.onPrimaryContainer)

                    Text("You can replace these values with your own asset if you want to trust a different asset.")
                        .font(.system(size: 13))
                        .foregroundStyle(Material3Colors.onPrimaryContainer)
                }
                .padding(.leading, 8)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(16)
            .background(Material3Colors.primaryContainer)
            .cornerRadius(12)
        }
    }

    private var inputFields: some View {
        VStack(spacing: 12) {
            // Account ID field
            VStack(alignment: .leading, spacing: 4) {
                Text("Account ID")
                    .font(.system(size: 12, weight: .medium))
                    .foregroundStyle(Material3Colors.onSurfaceVariant)

                TextField("G...", text: $accountId)
                    .textFieldStyle(.plain)
                    .font(.system(.body, design: .monospaced))
                    .padding(12)
                    .background(Color.white)
                    .overlay(
                        RoundedRectangle(cornerRadius: 4)
                            .stroke(Material3Colors.onSurfaceVariant.opacity(0.3), lineWidth: 1)
                    )
                    .onChange(of: accountId) { _ in
                        validationError = nil
                        trustResult = nil
                    }
            }

            // Asset Code field
            VStack(alignment: .leading, spacing: 4) {
                Text("Asset Code")
                    .font(.system(size: 12, weight: .medium))
                    .foregroundStyle(Material3Colors.onSurfaceVariant)

                TextField("USD, EUR, etc.", text: $assetCode)
                    .textFieldStyle(.plain)
                    .font(.system(.body, design: .monospaced))
                    .padding(12)
                    .background(Color.white)
                    .overlay(
                        RoundedRectangle(cornerRadius: 4)
                            .stroke(Material3Colors.onSurfaceVariant.opacity(0.3), lineWidth: 1)
                    )
                    .onChange(of: assetCode) { _ in
                        validationError = nil
                        trustResult = nil
                    }

                Text("1-12 uppercase alphanumeric characters")
                    .font(.system(size: 11))
                    .foregroundStyle(Material3Colors.onSurfaceVariant.opacity(0.7))
            }

            // Asset Issuer field
            VStack(alignment: .leading, spacing: 4) {
                Text("Asset Issuer")
                    .font(.system(size: 12, weight: .medium))
                    .foregroundStyle(Material3Colors.onSurfaceVariant)

                TextField("G...", text: $assetIssuer)
                    .textFieldStyle(.plain)
                    .font(.system(.body, design: .monospaced))
                    .padding(12)
                    .background(Color.white)
                    .overlay(
                        RoundedRectangle(cornerRadius: 4)
                            .stroke(Material3Colors.onSurfaceVariant.opacity(0.3), lineWidth: 1)
                    )
                    .onChange(of: assetIssuer) { _ in
                        validationError = nil
                        trustResult = nil
                    }
            }

            // Trust Limit field (optional)
            VStack(alignment: .leading, spacing: 4) {
                Text("Trust Limit (Optional)")
                    .font(.system(size: 12, weight: .medium))
                    .foregroundStyle(Material3Colors.onSurfaceVariant)

                TextField("Leave empty for maximum", text: $trustLimit)
                    .textFieldStyle(.plain)
                    .font(.system(.body, design: .monospaced))
                    .padding(12)
                    .background(Color.white)
                    .overlay(
                        RoundedRectangle(cornerRadius: 4)
                            .stroke(Material3Colors.onSurfaceVariant.opacity(0.3), lineWidth: 1)
                    )
                    .onChange(of: trustLimit) { _ in
                        validationError = nil
                        trustResult = nil
                    }

                Text("Maximum amount of the asset to hold (defaults to ~922 trillion)")
                    .font(.system(size: 11))
                    .foregroundStyle(Material3Colors.onSurfaceVariant.opacity(0.7))
            }

            // Secret Seed field (secure)
            VStack(alignment: .leading, spacing: 4) {
                HStack {
                    Text("Secret Seed")
                        .font(.system(size: 12, weight: .medium))
                        .foregroundStyle(Material3Colors.onSurfaceVariant)

                    Spacer()

                    Button(action: { showSecret.toggle() }) {
                        Image(systemName: showSecret ? "eye.slash.fill" : "eye.fill")
                            .font(.system(size: 14))
                            .foregroundStyle(Material3Colors.onSurfaceVariant)
                    }
                    .buttonStyle(.plain)
                    .help(showSecret ? "Hide secret" : "Show secret")
                }

                if showSecret {
                    TextField("S...", text: $secretSeed)
                        .textFieldStyle(.plain)
                        .font(.system(.body, design: .monospaced))
                        .padding(12)
                        .background(Material3Colors.tertiaryContainer)
                        .overlay(
                            RoundedRectangle(cornerRadius: 4)
                                .stroke(Material3Colors.onTertiaryContainer.opacity(0.3), lineWidth: 1)
                        )
                        .onChange(of: secretSeed) { _ in
                            validationError = nil
                            trustResult = nil
                        }
                } else {
                    SecureField("S...", text: $secretSeed)
                        .textFieldStyle(.plain)
                        .font(.system(.body, design: .monospaced))
                        .padding(12)
                        .background(Material3Colors.tertiaryContainer)
                        .overlay(
                            RoundedRectangle(cornerRadius: 4)
                                .stroke(Material3Colors.onTertiaryContainer.opacity(0.3), lineWidth: 1)
                        )
                        .onChange(of: secretSeed) { _ in
                            validationError = nil
                            trustResult = nil
                        }
                }

                Text("Required for signing the transaction")
                    .font(.system(size: 11))
                    .foregroundStyle(Material3Colors.onSurfaceVariant.opacity(0.7))
            }

            if let error = validationError {
                Text(error)
                    .font(.system(size: 12))
                    .foregroundStyle(Material3Colors.onErrorContainer)
                    .padding(.top, 4)
            }
        }
    }

    private var submitButton: some View {
        Button(action: submitTrustline) {
            HStack(spacing: 8) {
                if isSubmitting {
                    ProgressView()
                        .controlSize(.small)
                        .tint(.white)
                    Text("Submitting...")
                } else {
                    Image(systemName: "link.badge.plus")
                    Text("Establish Trustline")
                }
            }
            .frame(maxWidth: .infinity)
            .frame(height: 56)
            .background(isFormValid ? Material3Colors.primary : Material3Colors.primary.opacity(0.6))
            .foregroundColor(.white)
            .cornerRadius(12)
        }
        .disabled(!isFormValid || isSubmitting)
        .buttonStyle(.plain)
    }

    @ViewBuilder
    private var resultView: some View {
        if let result = trustResult {
            switch result {
            case let success as TrustAssetResult.Success:
                TrustAssetSuccessCards(success: success)
            case let error as TrustAssetResult.Error:
                errorCard(error)
                troubleshootingCard
            default:
                EmptyView()
            }
        }
    }

    private func errorCard(_ error: TrustAssetResult.Error) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Error")
                .font(.system(size: 16, weight: .semibold))
                .foregroundStyle(Material3Colors.onErrorContainer)

            Text(error.message)
                .font(.system(size: 14))
                .foregroundStyle(Material3Colors.onErrorContainer)

            if let exception = error.exception {
                Text("Technical details: \(exception.message ?? "Unknown error")")
                    .font(.system(size: 13, design: .monospaced))
                    .foregroundStyle(Material3Colors.onErrorContainer)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(16)
        .background(Material3Colors.errorContainer)
        .cornerRadius(12)
    }

    private var troubleshootingCard: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Troubleshooting")
                .font(.system(size: 14, weight: .semibold))
                .foregroundStyle(Material3Colors.onSecondaryContainer)

            VStack(alignment: .leading, spacing: 4) {
                Text("• Ensure the account exists and has been funded (use Friendbot for testnet)")
                    .font(.system(size: 13))
                    .foregroundStyle(Material3Colors.onSecondaryContainer)

                Text("• Verify the account has at least 0.5 XLM base reserve for the trustline")
                    .font(.system(size: 13))
                    .foregroundStyle(Material3Colors.onSecondaryContainer)

                Text("• Check that the asset issuer account is valid and exists")
                    .font(.system(size: 13))
                    .foregroundStyle(Material3Colors.onSecondaryContainer)

                Text("• Confirm the secret seed matches the account ID")
                    .font(.system(size: 13))
                    .foregroundStyle(Material3Colors.onSecondaryContainer)

                Text("• Asset code must be 1-12 uppercase alphanumeric characters")
                    .font(.system(size: 13))
                    .foregroundStyle(Material3Colors.onSecondaryContainer)
            }
            .padding(.leading, 8)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(16)
        .background(Material3Colors.secondaryContainer)
        .cornerRadius(12)
    }

    @ViewBuilder
    private var placeholderView: some View {
        if trustResult == nil && !isSubmitting {
            Spacer()
                .frame(height: 16)

            Image(systemName: "link.badge.plus")
                .font(.system(size: 64))
                .foregroundStyle(Material3Colors.onSurfaceVariant.opacity(0.3))

            Text("Fill in the details above to establish a trustline to an asset")
                .font(.system(size: 14))
                .foregroundStyle(Material3Colors.onSurfaceVariant)
                .multilineTextAlignment(.center)
        }
    }

    // MARK: - Computed Properties

    private var isFormValid: Bool {
        !accountId.isEmpty && !assetCode.isEmpty && !assetIssuer.isEmpty && !secretSeed.isEmpty
    }

    // MARK: - Actions

    private func submitTrustline() {
        isSubmitting = true
        trustResult = nil
        validationError = nil

        Task {
            do {
                // Use maximum limit if not specified (922337203685.4775807)
                let limit = trustLimit.isEmpty ? "922337203685.4775807" : trustLimit
                let result = try await bridge.trustAsset(
                    accountId: accountId,
                    assetCode: assetCode,
                    assetIssuer: assetIssuer,
                    secretSeed: secretSeed,
                    limit: limit,
                    useTestnet: true
                )
                await MainActor.run {
                    trustResult = result
                    isSubmitting = false
                }
            } catch {
                await MainActor.run {
                    trustResult = TrustAssetResult.Error(
                        message: "Failed to establish trustline: \(error.localizedDescription)",
                        exception: error as? KotlinThrowable
                    )
                    isSubmitting = false
                }
            }
        }
    }

    private func shortenAccountId(_ id: String) -> String {
        if id.count > 12 {
            return "\(id.prefix(4))...\(id.suffix(4))"
        }
        return id
    }
}


// MARK: - Send a Payment Screen (matches Compose SendPaymentScreen)

struct SendPaymentScreen: View {
    @Environment(\.dismiss) var dismiss
    @ObservedObject var toastManager: ToastManager
    @State private var sourceAccountId = ""
    @State private var destinationAccountId = ""
    @State private var assetType: AssetType = .native
    @State private var assetCode = ""
    @State private var assetIssuer = ""
    @State private var amount = ""
    @State private var secretSeed = ""
    @State private var showSecret = false
    @State private var isSubmitting = false
    @State private var paymentResult: SendPaymentResult?
    @State private var validationErrors: [String: String] = [:]
    
    private let bridge = MacOSBridge()
    
    var body: some View {
        ScrollView {
            VStack(spacing: 16) {
                infoCard
                importantNotesCard
                inputFields
                submitButton
                resultView
                placeholderView
            }
            .padding(16)
        }
        .background(Material3Colors.surface)
        .navigationTitle("Send a Payment")
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
    
    // MARK: - View Components
    
    private var infoCard: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Send a payment on the Stellar network")
                .font(.system(size: 16, weight: .semibold))
                .foregroundStyle(Material3Colors.onSecondaryContainer)
            
            Text("Transfer XLM (native asset) or any issued asset to another Stellar account. The destination account must exist, and for issued assets, must have an established trustline.")
                .font(.system(size: 13))
                .foregroundStyle(Material3Colors.onSecondaryContainer)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(16)
        .background(Material3Colors.secondaryContainer)
        .cornerRadius(12)
    }
    
    private var importantNotesCard: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Important Notes")
                .font(.system(size: 14, weight: .bold))
                .foregroundStyle(Material3Colors.onTertiaryContainer)
            
            VStack(alignment: .leading, spacing: 4) {
                Text("• Destination account must exist on the network")
                    .font(.system(size: 12))
                    .foregroundStyle(Material3Colors.onTertiaryContainer)
                
                Text("• For issued assets, destination must have a trustline")
                    .font(.system(size: 12))
                    .foregroundStyle(Material3Colors.onTertiaryContainer)
                
                Text("• Transaction fee (0.00001 XLM) is in addition to the payment")
                    .font(.system(size: 12))
                    .foregroundStyle(Material3Colors.onTertiaryContainer)
                
                Text("• Minimum payment amount is 0.0000001")
                    .font(.system(size: 12))
                    .foregroundStyle(Material3Colors.onTertiaryContainer)
            }
            .padding(.leading, 8)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(16)
        .background(Material3Colors.tertiaryContainer)
        .cornerRadius(12)
    }
    
    private var inputFields: some View {
        VStack(spacing: 12) {
            // Source Account ID
            VStack(alignment: .leading, spacing: 4) {
                Text("Source Account ID")
                    .font(.system(size: 12, weight: .medium))
                    .foregroundStyle(Material3Colors.onSurfaceVariant)
                
                TextField("G... (your account)", text: $sourceAccountId)
                    .textFieldStyle(.plain)
                    .font(.system(.body, design: .monospaced))
                    .padding(12)
                    .background(Color.white)
                    .overlay(
                        RoundedRectangle(cornerRadius: 4)
                            .stroke(validationErrors["sourceAccountId"] != nil ? Material3Colors.onErrorContainer : Material3Colors.onSurfaceVariant.opacity(0.3), lineWidth: 1)
                    )
                    .onChange(of: sourceAccountId) { _ in
                        validationErrors.removeValue(forKey: "sourceAccountId")
                        paymentResult = nil
                    }
                
                if let error = validationErrors["sourceAccountId"] {
                    Text(error)
                        .font(.system(size: 12))
                        .foregroundStyle(Material3Colors.onErrorContainer)
                }
            }
            
            // Destination Account ID
            VStack(alignment: .leading, spacing: 4) {
                Text("Destination Account ID")
                    .font(.system(size: 12, weight: .medium))
                    .foregroundStyle(Material3Colors.onSurfaceVariant)
                
                TextField("G... (recipient's account)", text: $destinationAccountId)
                    .textFieldStyle(.plain)
                    .font(.system(.body, design: .monospaced))
                    .padding(12)
                    .background(Color.white)
                    .overlay(
                        RoundedRectangle(cornerRadius: 4)
                            .stroke(validationErrors["destinationAccountId"] != nil ? Material3Colors.onErrorContainer : Material3Colors.onSurfaceVariant.opacity(0.3), lineWidth: 1)
                    )
                    .onChange(of: destinationAccountId) { _ in
                        validationErrors.removeValue(forKey: "destinationAccountId")
                        paymentResult = nil
                    }
                
                if let error = validationErrors["destinationAccountId"] {
                    Text(error)
                        .font(.system(size: 12))
                        .foregroundStyle(Material3Colors.onErrorContainer)
                }
            }
            
            // Asset Type Selection
            VStack(alignment: .leading, spacing: 8) {
                Text("Asset Type")
                    .font(.system(size: 14, weight: .bold))
                    .foregroundStyle(Material3Colors.onSurface)
                
                VStack(spacing: 8) {
                    Button(action: {
                        assetType = .native
                        paymentResult = nil
                    }) {
                        HStack(spacing: 12) {
                            Image(systemName: assetType == .native ? "checkmark.circle.fill" : "circle")
                                .foregroundStyle(Material3Colors.primary)
                            Text("Native (XLM)")
                                .foregroundStyle(Material3Colors.onSurface)
                            Spacer()
                        }
                        .padding(12)
                        .background(Color.white)
                        .cornerRadius(8)
                    }
                    .buttonStyle(.plain)
                    
                    Button(action: {
                        assetType = .issued
                        paymentResult = nil
                    }) {
                        HStack(spacing: 12) {
                            Image(systemName: assetType == .issued ? "checkmark.circle.fill" : "circle")
                                .foregroundStyle(Material3Colors.primary)
                            Text("Issued Asset (e.g., USD, EUR)")
                                .foregroundStyle(Material3Colors.onSurface)
                            Spacer()
                        }
                        .padding(12)
                        .background(Color.white)
                        .cornerRadius(8)
                    }
                    .buttonStyle(.plain)
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(16)
            .background(Material3Colors.surface)
            .cornerRadius(12)
            
            // Asset Code (conditional)
            if assetType == .issued {
                VStack(alignment: .leading, spacing: 4) {
                    Text("Asset Code")
                        .font(.system(size: 12, weight: .medium))
                        .foregroundStyle(Material3Colors.onSurfaceVariant)
                    
                    TextField("USD, EUR, USDC, etc.", text: Binding(
                        get: { assetCode },
                        set: { assetCode = $0.uppercased().trimmingCharacters(in: .whitespaces) }
                    ))
                    .textFieldStyle(.plain)
                    .font(.system(.body, design: .monospaced))
                    .padding(12)
                    .background(Color.white)
                    .overlay(
                        RoundedRectangle(cornerRadius: 4)
                            .stroke(validationErrors["assetCode"] != nil ? Material3Colors.onErrorContainer : Material3Colors.onSurfaceVariant.opacity(0.3), lineWidth: 1)
                    )
                    .onChange(of: assetCode) { _ in
                        validationErrors.removeValue(forKey: "assetCode")
                        paymentResult = nil
                    }
                    
                    if let error = validationErrors["assetCode"] {
                        Text(error)
                            .font(.system(size: 12))
                            .foregroundStyle(Material3Colors.onErrorContainer)
                    }
                }
            }
            
            // Asset Issuer (conditional)
            if assetType == .issued {
                VStack(alignment: .leading, spacing: 4) {
                    Text("Asset Issuer")
                        .font(.system(size: 12, weight: .medium))
                        .foregroundStyle(Material3Colors.onSurfaceVariant)
                    
                    TextField("G... (issuer's account)", text: $assetIssuer)
                        .textFieldStyle(.plain)
                        .font(.system(.body, design: .monospaced))
                        .padding(12)
                        .background(Color.white)
                        .overlay(
                            RoundedRectangle(cornerRadius: 4)
                                .stroke(validationErrors["assetIssuer"] != nil ? Material3Colors.onErrorContainer : Material3Colors.onSurfaceVariant.opacity(0.3), lineWidth: 1)
                        )
                        .onChange(of: assetIssuer) { _ in
                            validationErrors.removeValue(forKey: "assetIssuer")
                            paymentResult = nil
                        }
                    
                    if let error = validationErrors["assetIssuer"] {
                        Text(error)
                            .font(.system(size: 12))
                            .foregroundStyle(Material3Colors.onErrorContainer)
                    }
                }
            }
            
            // Amount
            VStack(alignment: .leading, spacing: 4) {
                Text("Amount")
                    .font(.system(size: 12, weight: .medium))
                    .foregroundStyle(Material3Colors.onSurfaceVariant)
                
                TextField("10.0", text: $amount)
                    .textFieldStyle(.plain)
                    .font(.system(.body, design: .monospaced))
                    .padding(12)
                    .background(Color.white)
                    .overlay(
                        RoundedRectangle(cornerRadius: 4)
                            .stroke(validationErrors["amount"] != nil ? Material3Colors.onErrorContainer : Material3Colors.onSurfaceVariant.opacity(0.3), lineWidth: 1)
                    )
                    .onChange(of: amount) { _ in
                        validationErrors.removeValue(forKey: "amount")
                        paymentResult = nil
                    }
                
                if let error = validationErrors["amount"] {
                    Text(error)
                        .font(.system(size: 12))
                        .foregroundStyle(Material3Colors.onErrorContainer)
                }
            }
            
            // Secret Seed (secure)
            VStack(alignment: .leading, spacing: 4) {
                HStack {
                    Text("Source Secret Seed")
                        .font(.system(size: 12, weight: .medium))
                        .foregroundStyle(Material3Colors.onSurfaceVariant)
                    
                    Spacer()
                    
                    Button(action: { showSecret.toggle() }) {
                        Image(systemName: showSecret ? "eye.slash.fill" : "eye.fill")
                            .font(.system(size: 14))
                            .foregroundStyle(Material3Colors.onSurfaceVariant)
                    }
                    .buttonStyle(.plain)
                    .help(showSecret ? "Hide secret" : "Show secret")
                }
                
                if showSecret {
                    TextField("S... (for signing)", text: $secretSeed)
                        .textFieldStyle(.plain)
                        .font(.system(.body, design: .monospaced))
                        .padding(12)
                        .background(Material3Colors.tertiaryContainer)
                        .overlay(
                            RoundedRectangle(cornerRadius: 4)
                                .stroke(validationErrors["secretSeed"] != nil ? Material3Colors.onErrorContainer : Material3Colors.onTertiaryContainer.opacity(0.3), lineWidth: 1)
                        )
                        .onChange(of: secretSeed) { _ in
                            validationErrors.removeValue(forKey: "secretSeed")
                            paymentResult = nil
                        }
                } else {
                    SecureField("S... (for signing)", text: $secretSeed)
                        .textFieldStyle(.plain)
                        .font(.system(.body, design: .monospaced))
                        .padding(12)
                        .background(Material3Colors.tertiaryContainer)
                        .overlay(
                            RoundedRectangle(cornerRadius: 4)
                                .stroke(validationErrors["secretSeed"] != nil ? Material3Colors.onErrorContainer : Material3Colors.onTertiaryContainer.opacity(0.3), lineWidth: 1)
                        )
                        .onChange(of: secretSeed) { _ in
                            validationErrors.removeValue(forKey: "secretSeed")
                            paymentResult = nil
                        }
                }
                
                if let error = validationErrors["secretSeed"] {
                    Text(error)
                        .font(.system(size: 12))
                        .foregroundStyle(Material3Colors.onErrorContainer)
                }
            }
        }
    }
    
    private var submitButton: some View {
        Button(action: submitPayment) {
            HStack(spacing: 8) {
                if isSubmitting {
                    ProgressView()
                        .controlSize(.small)
                        .tint(.white)
                    Text("Sending Payment...")
                } else {
                    Image(systemName: "paperplane.fill")
                    Text("Send Payment")
                }
            }
            .frame(maxWidth: .infinity)
            .frame(height: 56)
            .background(isFormValid && !isSubmitting ? Material3Colors.primary : Material3Colors.primary.opacity(0.6))
            .foregroundColor(.white)
            .cornerRadius(12)
        }
        .disabled(!isFormValid || isSubmitting)
        .buttonStyle(.plain)
    }
    
    @ViewBuilder
    private var resultView: some View {
        if let result = paymentResult {
            switch result {
            case let success as SendPaymentResult.Success:
                PaymentSuccessCards(success: success)
            case let error as SendPaymentResult.Error:
                errorCard(error)
                troubleshootingCard
            default:
                EmptyView()
            }
        }
    }
    
    private func errorCard(_ error: SendPaymentResult.Error) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Payment Failed")
                .font(.system(size: 16, weight: .semibold))
                .foregroundStyle(Material3Colors.onErrorContainer)
            
            Text(error.message)
                .font(.system(size: 14))
                .foregroundStyle(Material3Colors.onErrorContainer)
            
            if let exception = error.exception {
                Text("Technical details: \(exception.message ?? "Unknown error")")
                    .font(.system(size: 13, design: .monospaced))
                    .foregroundStyle(Material3Colors.onErrorContainer)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(16)
        .background(Material3Colors.errorContainer)
        .cornerRadius(12)
    }
    
    private var troubleshootingCard: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Troubleshooting")
                .font(.system(size: 14, weight: .semibold))
                .foregroundStyle(Material3Colors.onSecondaryContainer)
            
            VStack(alignment: .leading, spacing: 4) {
                Text("• Verify all account IDs are valid and start with 'G'")
                    .font(.system(size: 13))
                    .foregroundStyle(Material3Colors.onSecondaryContainer)
                
                Text("• Ensure the destination account exists (or create it with CreateAccount)")
                    .font(.system(size: 13))
                    .foregroundStyle(Material3Colors.onSecondaryContainer)
                
                Text("• Check that the source account has sufficient balance (including fees)")
                    .font(.system(size: 13))
                    .foregroundStyle(Material3Colors.onSecondaryContainer)
                
                Text("• For issued assets, verify the destination has a trustline")
                    .font(.system(size: 13))
                    .foregroundStyle(Material3Colors.onSecondaryContainer)
                
                Text("• Verify the secret seed matches the source account")
                    .font(.system(size: 13))
                    .foregroundStyle(Material3Colors.onSecondaryContainer)
                
                Text("• Ensure you have a stable internet connection")
                    .font(.system(size: 13))
                    .foregroundStyle(Material3Colors.onSecondaryContainer)
            }
            .padding(.leading, 8)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(16)
        .background(Material3Colors.secondaryContainer)
        .cornerRadius(12)
    }
    
    @ViewBuilder
    private var placeholderView: some View {
        if paymentResult == nil && !isSubmitting && (sourceAccountId.isEmpty || destinationAccountId.isEmpty || amount.isEmpty || secretSeed.isEmpty) {
            Spacer()
                .frame(height: 16)
            
            Image(systemName: "paperplane")
                .font(.system(size: 64))
                .foregroundStyle(Material3Colors.onSurfaceVariant.opacity(0.3))
            
            Text("Fill in the required fields to send a payment on the Stellar testnet")
                .font(.system(size: 14))
                .foregroundStyle(Material3Colors.onSurfaceVariant)
                .multilineTextAlignment(.center)
        }
    }
    
    // MARK: - Computed Properties
    
    private var isFormValid: Bool {
        !sourceAccountId.isEmpty && !destinationAccountId.isEmpty && !amount.isEmpty && !secretSeed.isEmpty &&
        (assetType == .native || (!assetCode.isEmpty && !assetIssuer.isEmpty))
    }
    
    // MARK: - Actions
    
    private func submitPayment() {
        let errors = validateInputs()
        if !errors.isEmpty {
            validationErrors = errors
            return
        }
        
        isSubmitting = true
        paymentResult = nil
        validationErrors = [:]
        
        Task {
            do {
                let assetCodeValue = assetType == .native ? "native" : assetCode
                let assetIssuerValue = assetType == .native ? nil : assetIssuer
                
                let result = try await bridge.sendPayment(
                    sourceAccountId: sourceAccountId,
                    destinationAccountId: destinationAccountId,
                    assetCode: assetCodeValue,
                    assetIssuer: assetIssuerValue,
                    amount: amount,
                    secretSeed: secretSeed,
                    useTestnet: true
                )
                await MainActor.run {
                    paymentResult = result
                    isSubmitting = false
                }
            } catch {
                await MainActor.run {
                    paymentResult = SendPaymentResult.Error(
                        message: "Failed to send payment: \(error.localizedDescription)",
                        exception: error as? KotlinThrowable
                    )
                    isSubmitting = false
                }
            }
        }
    }
    
    private func validateInputs() -> [String: String] {
        var errors: [String: String] = [:]
        
        if sourceAccountId.isEmpty {
            errors["sourceAccountId"] = "Source account ID is required"
        } else if !sourceAccountId.hasPrefix("G") {
            errors["sourceAccountId"] = "Source account ID must start with 'G'"
        } else if sourceAccountId.count != 56 {
            errors["sourceAccountId"] = "Source account ID must be 56 characters"
        }
        
        if destinationAccountId.isEmpty {
            errors["destinationAccountId"] = "Destination account ID is required"
        } else if !destinationAccountId.hasPrefix("G") {
            errors["destinationAccountId"] = "Destination account ID must start with 'G'"
        } else if destinationAccountId.count != 56 {
            errors["destinationAccountId"] = "Destination account ID must be 56 characters"
        }
        
        if assetType == .issued {
            if assetCode.isEmpty {
                errors["assetCode"] = "Asset code is required"
            } else if assetCode.count > 12 {
                errors["assetCode"] = "Asset code cannot exceed 12 characters"
            } else {
                let invalidChars = assetCode.filter { char in
                    !(char >= "A" && char <= "Z") && !(char >= "0" && char <= "9")
                }
                if !invalidChars.isEmpty {
                    errors["assetCode"] = "Only uppercase letters and digits allowed"
                }
            }
            
            if assetIssuer.isEmpty {
                errors["assetIssuer"] = "Asset issuer is required"
            } else if !assetIssuer.hasPrefix("G") {
                errors["assetIssuer"] = "Asset issuer must start with 'G'"
            } else if assetIssuer.count != 56 {
                errors["assetIssuer"] = "Asset issuer must be 56 characters"
            }
        }
        
        if amount.isEmpty {
            errors["amount"] = "Amount is required"
        } else if let amountValue = Double(amount), amountValue <= 0 {
            errors["amount"] = "Amount must be greater than 0"
        } else if Double(amount) == nil {
            errors["amount"] = "Invalid number format"
        }
        
        if secretSeed.isEmpty {
            errors["secretSeed"] = "Secret seed is required"
        } else if !secretSeed.hasPrefix("S") {
            errors["secretSeed"] = "Secret seed must start with 'S'"
        } else if secretSeed.count != 56 {
            errors["secretSeed"] = "Secret seed must be 56 characters"
        }
        
        return errors
    }
    
    private func shortenAccountId(_ id: String) -> String {
        if id.count > 12 {
            return "\(id.prefix(4))...\(id.suffix(4))"
        }
        return id
    }
}

// MARK: - Asset Type Enum

enum AssetType {
    case native
    case issued
}

// MARK: - Payment Success Cards

struct PaymentSuccessCards: View {
    let success: SendPaymentResult.Success
    
    var body: some View {
        VStack(spacing: 16) {
            // Success header card (green)
            VStack(alignment: .leading, spacing: 8) {
                HStack(spacing: 8) {
                    Image(systemName: "checkmark.circle.fill")
                        .font(.system(size: 24))
                        .foregroundStyle(Material3Colors.onSuccessContainer)
                    
                    Text("Payment Sent Successfully")
                        .font(.system(size: 16, weight: .bold))
                        .foregroundStyle(Material3Colors.onSuccessContainer)
                }
                
                Text(success.message)
                    .font(.system(size: 14))
                    .foregroundStyle(Material3Colors.onSuccessContainer)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(16)
            .background(Material3Colors.successContainer)
            .cornerRadius(12)
            
            // Payment Details Card
            VStack(alignment: .leading, spacing: 12) {
                Text("Payment Details")
                    .font(.system(size: 14, weight: .bold))
                    .foregroundStyle(Material3Colors.onSurfaceVariant)
                
                Divider()
                
                PaymentDetailRow(label: "From", value: success.source)
                PaymentDetailRow(label: "To", value: success.destination)
                PaymentDetailRow(label: "Amount", value: "\(success.amount) \(success.assetCode)")
                
                if let issuer = success.assetIssuer {
                    PaymentDetailRow(label: "Asset Issuer", value: issuer)
                }
                
                PaymentDetailRow(label: "Transaction Hash", value: success.transactionHash)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(16)
            .background(Material3Colors.surfaceVariant)
            .cornerRadius(12)
            
            // What's Next? Card
            VStack(alignment: .leading, spacing: 8) {
                Text("What's Next?")
                    .font(.system(size: 14, weight: .bold))
                    .foregroundStyle(Material3Colors.onSecondaryContainer)
                
                VStack(alignment: .leading, spacing: 4) {
                    Text("• The payment has been successfully recorded on the Stellar ledger")
                        .font(.system(size: 13))
                        .foregroundStyle(Material3Colors.onSecondaryContainer)
                    
                    Text("• The destination account now has the funds available")
                        .font(.system(size: 13))
                        .foregroundStyle(Material3Colors.onSecondaryContainer)
                    
                    Text("• Use 'Fetch Account Details' to verify the updated balances")
                        .font(.system(size: 13))
                        .foregroundStyle(Material3Colors.onSecondaryContainer)
                    
                    Text("• View the transaction on Stellar Expert or other block explorers")
                        .font(.system(size: 13))
                        .foregroundStyle(Material3Colors.onSecondaryContainer)
                }
                .padding(.leading, 8)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(16)
            .background(Material3Colors.secondaryContainer)
            .cornerRadius(12)
        }
    }
}

// MARK: - Payment Detail Row

struct PaymentDetailRow: View {
    let label: String
    let value: String
    
    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(label)
                .font(.system(size: 12, weight: .medium))
                .foregroundStyle(Material3Colors.onSurfaceVariant.opacity(0.7))
            
            Text(value)
                .font(.system(.body, design: .monospaced))
                .foregroundStyle(Material3Colors.onSurfaceVariant)
                .textSelection(.enabled)
        }
    }
}



// MARK: - Trust Asset Success Cards (matches Compose three-card design)

struct TrustAssetSuccessCards: View {
    let success: TrustAssetResult.Success

    var body: some View {
        VStack(spacing: 16) {
            // Success header card (green)
            VStack(alignment: .leading, spacing: 8) {
                HStack(spacing: 8) {
                    Image(systemName: "checkmark.circle.fill")
                        .font(.system(size: 24))
                        .foregroundStyle(Material3Colors.onSuccessContainer)

                    Text("Trustline Established")
                        .font(.system(size: 16, weight: .bold))
                        .foregroundStyle(Material3Colors.onSuccessContainer)
                }

                Text("Trustline established successfully. Transaction hash: \(success.transactionHash)")
                    .font(.system(size: 14))
                    .foregroundStyle(Material3Colors.onSuccessContainer)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(16)
            .background(Material3Colors.successContainer)
            .cornerRadius(12)

            // Transaction Details Card (light purple/surfaceVariant)
            VStack(alignment: .leading, spacing: 12) {
                Text("Transaction Details")
                    .font(.system(size: 14, weight: .bold))
                    .foregroundStyle(Material3Colors.onSurfaceVariant)

                Divider()

                TrustAssetDetailRow(label: "Asset Code", value: success.assetCode)
                TrustAssetDetailRow(label: "Asset Issuer", value: success.assetIssuer, monospace: true)
                TrustAssetDetailRow(
                    label: "Trust Limit",
                    value: success.limit == "922337203685.4775807"
                        ? "Maximum (\(success.limit))"
                        : success.limit
                )
                TrustAssetDetailRow(label: "Transaction Hash", value: success.transactionHash, monospace: true)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(16)
            .background(Material3Colors.surfaceVariant)
            .cornerRadius(12)

            // What's Next? Card (light blue/secondaryContainer)
            VStack(alignment: .leading, spacing: 8) {
                Text("What's Next?")
                    .font(.system(size: 14, weight: .bold))
                    .foregroundStyle(Material3Colors.onSecondaryContainer)

                VStack(alignment: .leading, spacing: 4) {
                    Text("• You can now receive \(success.assetCode) from the asset issuer")
                        .font(.system(size: 13))
                        .foregroundStyle(Material3Colors.onSecondaryContainer)

                    Text("• Check your account balances to see the new trustline")
                        .font(.system(size: 13))
                        .foregroundStyle(Material3Colors.onSecondaryContainer)

                    Text("• Use the 'Fetch Account Details' feature to view your trustlines")
                        .font(.system(size: 13))
                        .foregroundStyle(Material3Colors.onSecondaryContainer)

                    Text("• You can modify the trust limit or remove the trustline later")
                        .font(.system(size: 13))
                        .foregroundStyle(Material3Colors.onSecondaryContainer)
                }
                .padding(.leading, 8)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(16)
            .background(Material3Colors.secondaryContainer)
            .cornerRadius(12)
        }
    }
}

// MARK: - Trust Asset Detail Row Component

struct TrustAssetDetailRow: View {
    let label: String
    let value: String
    var monospace: Bool = false

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(label)
                .font(.system(size: 12, weight: .medium))
                .foregroundStyle(Material3Colors.onSurfaceVariant.opacity(0.7))

            Text(value)
                .font(monospace ? .system(.body, design: .monospaced) : .system(.body))
                .foregroundStyle(Material3Colors.onSurfaceVariant)
                .textSelection(.enabled)
        }
    }
}

// MARK: - Account Details Display Components

struct AccountDetailsCard: View {
    let account: AccountResponse

    var body: some View {
        VStack(spacing: 12) {
            // Success header
            VStack(alignment: .leading, spacing: 8) {
                Text("Account Found")
                    .font(.system(size: 16, weight: .bold))
                    .foregroundStyle(Material3Colors.onSuccessContainer)

                Text("Successfully fetched details for account \(shortenAccountId(account.accountId))")
                    .font(.system(size: 14))
                    .foregroundStyle(Material3Colors.onSuccessContainer)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(16)
            .background(Material3Colors.successContainer)
            .cornerRadius(12)

            // Basic Information
            DetailsSectionCard(title: "Basic Information") {
                DetailRow(label: "Account ID", value: account.accountId, monospace: true)
                DetailRow(label: "Sequence Number", value: account.sequenceNumber.description)
                DetailRow(label: "Subentry Count", value: account.subentryCount.description)
                if let homeDomain = account.homeDomain {
                    DetailRow(label: "Home Domain", value: homeDomain)
                }
                DetailRow(label: "Last Modified Ledger", value: account.lastModifiedLedger.description)
                DetailRow(label: "Last Modified Time", value: account.lastModifiedTime)
            }

            // Balances
            DetailsSectionCard(title: "Balances (\(account.balances.count))") {
                ForEach(Array(account.balances.enumerated()), id: \.offset) { index, balance in
                    if index > 0 {
                        Divider()
                            .padding(.vertical, 8)
                    }
                    BalanceItem(balance: balance)
                }
            }

            // Thresholds
            DetailsSectionCard(title: "Thresholds") {
                DetailRow(label: "Low Threshold", value: account.thresholds.lowThreshold.description)
                DetailRow(label: "Medium Threshold", value: account.thresholds.medThreshold.description)
                DetailRow(label: "High Threshold", value: account.thresholds.highThreshold.description)
            }

            // Flags
            DetailsSectionCard(title: "Authorization Flags") {
                FlagRow(label: "Auth Required", enabled: account.flags.authRequired)
                FlagRow(label: "Auth Revocable", enabled: account.flags.authRevocable)
                FlagRow(label: "Auth Immutable", enabled: account.flags.authImmutable)
                FlagRow(label: "Auth Clawback Enabled", enabled: account.flags.authClawbackEnabled)
            }

            // Signers
            DetailsSectionCard(title: "Signers (\(account.signers.count))") {
                ForEach(Array(account.signers.enumerated()), id: \.offset) { index, signer in
                    if index > 0 {
                        Divider()
                            .padding(.vertical, 8)
                    }
                    SignerItem(signer: signer)
                }
            }

            // Data Entries
            if !account.data.isEmpty {
                DetailsSectionCard(title: "Data Entries (\(account.data.count))") {
                    ForEach(Array(account.data.keys.sorted()), id: \.self) { key in
                        if let value = account.data[key] {
                            DetailRow(label: key, value: value, monospace: true)
                        }
                    }
                }
            }

            // Sponsorship Information
            if account.sponsor != nil || (account.numSponsoring != nil && account.numSponsoring!.int32Value > 0) || (account.numSponsored != nil && account.numSponsored!.int32Value > 0) {
                DetailsSectionCard(title: "Sponsorship") {
                    if let sponsor = account.sponsor {
                        DetailRow(label: "Sponsor", value: sponsor, monospace: true)
                    }
                    if let numSponsoring = account.numSponsoring {
                        DetailRow(label: "Number Sponsoring", value: numSponsoring.description)
                    }
                    if let numSponsored = account.numSponsored {
                        DetailRow(label: "Number Sponsored", value: numSponsored.description)
                    }
                }
            }
        }
    }

    private func shortenAccountId(_ id: String) -> String {
        if id.count > 12 {
            return "\(id.prefix(4))...\(id.suffix(4))"
        }
        return id
    }
}

struct DetailsSectionCard<Content: View>: View {
    let title: String
    @ViewBuilder let content: () -> Content

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(title)
                .font(.system(size: 14, weight: .bold))
                .foregroundStyle(Material3Colors.onSurfaceVariant)

            Divider()

            VStack(alignment: .leading, spacing: 8) {
                content()
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(16)
        .background(Material3Colors.surfaceVariant)
        .cornerRadius(12)
    }
}

struct DetailRow: View {
    let label: String
    let value: String
    var monospace: Bool = false

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(label)
                .font(.system(size: 12, weight: .medium))
                .foregroundStyle(Material3Colors.onSurfaceVariant.opacity(0.7))

            Text(value)
                .font(monospace ? .system(.body, design: .monospaced) : .system(.body))
                .foregroundStyle(Material3Colors.onSurfaceVariant)
                .textSelection(.enabled)
        }
    }
}

struct FlagRow: View {
    let label: String
    let enabled: Bool

    var body: some View {
        HStack {
            Text(label)
                .font(.system(size: 14))
                .foregroundStyle(Material3Colors.onSurfaceVariant)

            Spacer()

            Text(enabled ? "Enabled" : "Disabled")
                .font(.system(size: 12, weight: .medium))
                .foregroundStyle(enabled ? Material3Colors.onSuccessContainer : Material3Colors.onSurfaceVariant.opacity(0.7))
                .padding(.horizontal, 8)
                .padding(.vertical, 4)
                .background(enabled ? Material3Colors.successContainer : Material3Colors.surfaceVariant)
                .cornerRadius(4)
        }
    }
}

struct BalanceItem: View {
    let balance: AccountResponse.Balance

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            // Asset type
            Text(assetTitle)
                .font(.system(size: 14, weight: .bold))
                .foregroundStyle(Material3Colors.onSurfaceVariant)

            // Balance
            DetailRow(label: "Balance", value: balance.balance)

            // Asset issuer (if not native)
            if let issuer = balance.assetIssuer {
                DetailRow(label: "Issuer", value: issuer, monospace: true)
            }

            // Liquidity pool ID (if applicable)
            if let poolId = balance.liquidityPoolId {
                DetailRow(label: "Pool ID", value: poolId, monospace: true)
            }

            // Additional details
            if let limit = balance.limit {
                DetailRow(label: "Limit", value: limit)
            }
            if let buyingLiabilities = balance.buyingLiabilities {
                DetailRow(label: "Buying Liabilities", value: buyingLiabilities)
            }
            if let sellingLiabilities = balance.sellingLiabilities {
                DetailRow(label: "Selling Liabilities", value: sellingLiabilities)
            }

            // Authorization flags
            if let isAuthorized = balance.isAuthorized {
                FlagRow(label: "Authorized", enabled: isAuthorized.boolValue)
            }
            if let isAuthorizedToMaintainLiabilities = balance.isAuthorizedToMaintainLiabilities {
                FlagRow(label: "Authorized to Maintain Liabilities", enabled: isAuthorizedToMaintainLiabilities.boolValue)
            }
            if let isClawbackEnabled = balance.isClawbackEnabled {
                FlagRow(label: "Clawback Enabled", enabled: isClawbackEnabled.boolValue)
            }
        }
    }

    private var assetTitle: String {
        if balance.assetType == "native" {
            return "Native (XLM)"
        } else if let assetCode = balance.assetCode {
            return "\(assetCode) (\(balance.assetType))"
        } else if balance.liquidityPoolId != nil {
            return "Liquidity Pool"
        } else {
            return balance.assetType
        }
    }
}

struct SignerItem: View {
    let signer: AccountResponse.Signer

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            DetailRow(label: "Key", value: signer.key, monospace: true)

            HStack {
                Text("Type: \(signer.type)")
                    .font(.system(size: 14))
                    .foregroundStyle(Material3Colors.onSurfaceVariant)

                Spacer()

                Text("Weight: \(signer.weight.description)")
                    .font(.system(size: 14, weight: .bold))
                    .foregroundStyle(Material3Colors.onSurfaceVariant)
            }

            if let sponsor = signer.sponsor {
                DetailRow(label: "Sponsor", value: sponsor, monospace: true)
            }
        }
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
    let keypair: KeyPair
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

            Text(isVisible ? (keypair.getSecretSeedAsString() ?? "") : String(repeating: "•", count: 56))
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

// MARK: - Contract Details Screen (Soroban Smart Contracts)

struct ContractDetailsScreen: View {
    @Environment(\.dismiss) var dismiss
    @ObservedObject var toastManager: ToastManager
    @State private var contractId = "CBNCMQU5VCEVFASCPT4CCQX2LGYJK6YZ7LOIZLRXDEVJYQB7K6UTQNWW"
    @State private var isFetching = false
    @State private var detailsResult: ContractDetailsResult?
    @State private var validationError: String?
    
    private let bridge = MacOSBridge()
    
    var body: some View {
        ScrollView {
            VStack(spacing: 16) {
                infoCard
                exampleCard
                inputField
                fetchButton
                resultView
                placeholderView
            }
            .padding(16)
        }
        .background(Material3Colors.surface)
        .navigationTitle("Fetch Smart Contract Details")
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
    
    // MARK: - View Components
    
    private var infoCard: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Soroban RPC: fetch and parse smart contract details")
                .font(.system(size: 16, weight: .semibold))
                .foregroundStyle(Material3Colors.onSecondaryContainer)
            
            Text("Enter a contract ID to fetch its WASM bytecode from the network and parse the contract specification including metadata and function definitions.")
                .font(.system(size: 13))
                .foregroundStyle(Material3Colors.onSecondaryContainer)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(16)
        .background(Material3Colors.secondaryContainer)
        .cornerRadius(12)
    }
    
    private var exampleCard: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Example Testnet Contract")
                .font(.system(size: 14, weight: .bold))
                .foregroundStyle(Material3Colors.onPrimaryContainer)
            
            VStack(alignment: .leading, spacing: 4) {
                Text("The contract ID field is pre-filled with a testnet contract ID.")
                    .font(.system(size: 13))
                    .foregroundStyle(Material3Colors.onPrimaryContainer)
                
                Text("You can use it as-is or replace it with your own contract ID.")
                    .font(.system(size: 13))
                    .foregroundStyle(Material3Colors.onPrimaryContainer)
            }
            .padding(.leading, 8)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(16)
        .background(Material3Colors.primaryContainer)
        .cornerRadius(12)
    }
    
    private var inputField: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text("Contract ID")
                .font(.system(size: 12, weight: .medium))
                .foregroundStyle(Material3Colors.onSurfaceVariant)
            
            TextField("C...", text: $contractId)
                .textFieldStyle(.plain)
                .font(.system(.body, design: .monospaced))
                .padding(12)
                .background(Color.white)
                .overlay(
                    RoundedRectangle(cornerRadius: 4)
                        .stroke(validationError != nil ? Material3Colors.onErrorContainer : Material3Colors.onSurfaceVariant.opacity(0.3), lineWidth: 1)
                )
                .onChange(of: contractId) { _ in
                    validationError = nil
                    detailsResult = nil
                }
            
            if let error = validationError {
                Text(error)
                    .font(.system(size: 12))
                    .foregroundStyle(Material3Colors.onErrorContainer)
            }
        }
    }
    
    private var fetchButton: some View {
        Button(action: fetchDetails) {
            HStack(spacing: 8) {
                if isFetching {
                    ProgressView()
                        .controlSize(.small)
                        .tint(.white)
                    Text("Fetching...")
                } else {
                    Image(systemName: "magnifyingglass")
                    Text("Fetch Details")
                }
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, 14)
            .background(isFetching || contractId.isEmpty ? Material3Colors.onSurfaceVariant.opacity(0.3) : Material3Colors.primary)
            .foregroundStyle(.white)
            .cornerRadius(8)
        }
        .buttonStyle(.plain)
        .disabled(isFetching || contractId.isEmpty)
    }
    
    @ViewBuilder
    private var resultView: some View {
        if let result = detailsResult {
            switch result {
            case let success as ContractDetailsResult.Success:
                ContractInfoView(contractInfo: success.contractInfo)
            case let error as ContractDetailsResult.Error:
                ContractErrorView(error: error)
            default:
                EmptyView()
            }
        }
    }
    
    @ViewBuilder
    private var placeholderView: some View {
        if detailsResult == nil && !isFetching && contractId.isEmpty {
            VStack(spacing: 16) {
                Image(systemName: "curlybraces")
                    .font(.system(size: 64))
                    .foregroundStyle(Material3Colors.onSurfaceVariant.opacity(0.3))
                
                Text("Enter a contract ID to view its parsed specification")
                    .font(.system(size: 14))
                    .foregroundStyle(Material3Colors.onSurfaceVariant)
                    .multilineTextAlignment(.center)
            }
            .padding(.vertical, 32)
        }
    }
    
    // MARK: - Actions
    
    private func fetchDetails() {
        let error = validateContractId()
        if let error = error {
            validationError = error
            return
        }
        
        isFetching = true
        detailsResult = nil
        validationError = nil
        
        Task {
            do {
                let result = try await bridge.fetchContractDetails(contractId: contractId, useTestnet: true)
                await MainActor.run {
                    detailsResult = result
                    isFetching = false
                }
            } catch {
                await MainActor.run {
                    detailsResult = ContractDetailsResult.Error(
                        message: "Failed to fetch contract details: \(error.localizedDescription)",
                        exception: error as? KotlinThrowable
                    )
                    isFetching = false
                }
            }
        }
    }
    
    private func validateContractId() -> String? {
        if contractId.isEmpty {
            return "Contract ID is required"
        } else if !contractId.hasPrefix("C") {
            return "Contract ID must start with 'C'"
        } else if contractId.count != 56 {
            return "Contract ID must be 56 characters long"
        }
        return nil
    }
}

// MARK: - Contract Info View

struct ContractInfoView: View {
    let contractInfo: SorobanContractInfo
    
    var body: some View {
        VStack(spacing: 16) {
            // Success header
            VStack(alignment: .leading, spacing: 8) {
                Text("Contract Found")
                    .font(.system(size: 16, weight: .bold))
                    .foregroundStyle(Material3Colors.onSuccessContainer)
                
                Text("Successfully fetched and parsed contract details")
                    .font(.system(size: 14))
                    .foregroundStyle(Material3Colors.onSuccessContainer)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(16)
            .background(Material3Colors.successContainer)
            .cornerRadius(12)
            
            // Contract Metadata Card
            ContractMetadataView(contractInfo: contractInfo)
            
            // Contract Spec Entries Card
            ContractSpecEntriesView(specEntries: contractInfo.specEntries)
        }
    }
}

// MARK: - Contract Metadata View

struct ContractMetadataView: View {
    let contractInfo: SorobanContractInfo
    
    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Contract Metadata")
                .font(.system(size: 14, weight: .bold))
                .foregroundStyle(Material3Colors.onSurfaceVariant)
            
            Divider()
            
            // Environment Interface Version
            DetailRow(label: "Environment Interface Version", value: String(contractInfo.envInterfaceVersion))
            
            // Meta entries
            if !contractInfo.metaEntries.isEmpty {
                Spacer().frame(height: 8)
                Text("Meta Entries (\(contractInfo.metaEntries.count))")
                    .font(.system(size: 12, weight: .medium))
                    .foregroundStyle(Material3Colors.onSurfaceVariant.opacity(0.7))
                
                ForEach(Array(contractInfo.metaEntries.keys.sorted()), id: \.self) { key in
                    if let value = contractInfo.metaEntries[key] {
                        DetailRow(label: key, value: value as! String, monospace: true)
                    }
                }
            } else {
                DetailRow(label: "Meta Entries", value: "None")
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(16)
        .background(Material3Colors.surfaceVariant)
        .cornerRadius(12)
    }
}

// MARK: - Contract Spec Entries View

struct ContractSpecEntriesView: View {
    let specEntries: [SCSpecEntryXdr]
    
    var sortedEntries: [SCSpecEntryXdr] {
        specEntries.sorted { entry1, entry2 in
            priority(for: entry1) < priority(for: entry2)
        }
    }
    
    private func priority(for entry: SCSpecEntryXdr) -> Int {
        if entry is SCSpecEntryXdr.FunctionV0 { return 0 }
        if entry is SCSpecEntryXdr.UdtStructV0 { return 1 }
        if entry is SCSpecEntryXdr.UdtUnionV0 { return 2 }
        if entry is SCSpecEntryXdr.UdtEnumV0 { return 3 }
        if entry is SCSpecEntryXdr.UdtErrorEnumV0 { return 4 }
        if entry is SCSpecEntryXdr.EventV0 { return 5 }
        return 6
    }
    
    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Contract Spec Entries (\(specEntries.count))")
                .font(.system(size: 14, weight: .bold))
                .foregroundStyle(Material3Colors.onSurfaceVariant)
            
            Divider()
            
            if sortedEntries.isEmpty {
                Text("No spec entries found")
                    .font(.system(size: 14))
                    .foregroundStyle(Material3Colors.onSurfaceVariant.opacity(0.7))
                    .padding(.vertical, 8)
            } else {
                ForEach(Array(sortedEntries.enumerated()), id: \.offset) { index, entry in
                    if index > 0 {
                        Divider()
                            .padding(.vertical, 8)
                    }
                    SpecEntryItemView(entry: entry)
                }
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(16)
        .background(Material3Colors.surfaceVariant)
        .cornerRadius(12)
    }
}

// MARK: - Spec Entry Item View

struct SpecEntryItemView: View {
    let entry: SCSpecEntryXdr
    @State private var isExpanded = false
    
    var body: some View {
        if let function = entry as? SCSpecEntryXdr.FunctionV0 {
            FunctionSpecView(function: function.value, isExpanded: $isExpanded)
        } else if let structEntry = entry as? SCSpecEntryXdr.UdtStructV0 {
            StructSpecView(structDef: structEntry.value, isExpanded: $isExpanded)
        } else if let union = entry as? SCSpecEntryXdr.UdtUnionV0 {
            UnionSpecView(unionDef: union.value, isExpanded: $isExpanded)
        } else if let enumEntry = entry as? SCSpecEntryXdr.UdtEnumV0 {
            EnumSpecView(enumDef: enumEntry.value, isExpanded: $isExpanded)
        } else if let errorEnum = entry as? SCSpecEntryXdr.UdtErrorEnumV0 {
            ErrorEnumSpecView(errorEnum: errorEnum.value, isExpanded: $isExpanded)
        } else if let event = entry as? SCSpecEntryXdr.EventV0 {
            EventSpecView(event: event.value, isExpanded: $isExpanded)
        }
    }
}

// MARK: - Function Spec View

struct FunctionSpecView: View {
    let function: SCSpecFunctionV0Xdr
    @Binding var isExpanded: Bool
    
    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            Button(action: { isExpanded.toggle() }) {
                VStack(alignment: .leading, spacing: 6) {
                    Text("Function: \(function.name)")
                        .font(.system(size: 14, weight: .bold))
                        .foregroundStyle(Material3Colors.onSurfaceVariant)
                    
                    if !function.doc.isEmpty {
                        Text(function.doc)
                            .font(.system(size: 12))
                            .foregroundStyle(Material3Colors.onSurfaceVariant.opacity(0.7))
                    }
                    
                    if isExpanded {
                        functionDetails
                    } else {
                        HStack(spacing: 12) {
                            Text("Inputs: \(function.inputs.count)")
                                .font(.system(size: 12))
                            Text("Outputs: \(function.outputs.count)")
                                .font(.system(size: 12))
                        }
                        .foregroundStyle(Material3Colors.onSurfaceVariant)
                    }
                    
                    Text(isExpanded ? "Click to collapse" : "Click to expand")
                        .font(.system(size: 11))
                        .foregroundStyle(Material3Colors.primary)
                        .padding(.top, 4)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
            }
            .buttonStyle(.plain)
        }
        .padding(12)
        .background(Material3Colors.primaryContainer.opacity(0.3))
        .cornerRadius(8)
    }
    
    @ViewBuilder
    private var functionDetails: some View {
        if !function.inputs.isEmpty {
            Spacer().frame(height: 4)
            Text("Inputs (\(function.inputs.count)):")
                .font(.system(size: 12, weight: .bold))
                .foregroundStyle(Material3Colors.onSurfaceVariant)
            
            ForEach(Array(function.inputs.enumerated()), id: \.offset) { index, input in
                VStack(alignment: .leading, spacing: 2) {
                    Text("[\(index)] \(input.name)")
                        .font(.system(size: 12, weight: .medium))
                        .foregroundStyle(Material3Colors.onSurfaceVariant)
                    Text("Type: \(getSpecTypeInfo(input.type))")
                        .font(.system(size: 11, design: .monospaced))
                        .foregroundStyle(Material3Colors.onSurfaceVariant.opacity(0.8))
                    if !input.doc.isEmpty {
                        Text("Doc: \(input.doc)")
                            .font(.system(size: 11))
                            .foregroundStyle(Material3Colors.onSurfaceVariant.opacity(0.7))
                    }
                }
                .padding(.leading, 12)
            }
        } else {
            Text("Inputs: None")
                .font(.system(size: 12))
                .foregroundStyle(Material3Colors.onSurfaceVariant)
        }
        
        if !function.outputs.isEmpty {
            Spacer().frame(height: 4)
            Text("Outputs (\(function.outputs.count)):")
                .font(.system(size: 12, weight: .bold))
                .foregroundStyle(Material3Colors.onSurfaceVariant)
            
            ForEach(Array(function.outputs.enumerated()), id: \.offset) { index, output in
                Text("[\(index)] \(getSpecTypeInfo(output))")
                    .font(.system(size: 11, design: .monospaced))
                    .foregroundStyle(Material3Colors.onSurfaceVariant.opacity(0.8))
                    .padding(.leading, 12)
            }
        } else {
            Spacer().frame(height: 4)
            Text("Outputs: None (void)")
                .font(.system(size: 12))
                .foregroundStyle(Material3Colors.onSurfaceVariant)
        }
    }
}

// MARK: - Struct Spec View

struct StructSpecView: View {
    let structDef: SCSpecUDTStructV0Xdr
    @Binding var isExpanded: Bool
    
    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            Button(action: { isExpanded.toggle() }) {
                VStack(alignment: .leading, spacing: 6) {
                    Text("Struct: \(structDef.name)")
                        .font(.system(size: 14, weight: .bold))
                        .foregroundStyle(Material3Colors.onSurfaceVariant)
                    
                    if !structDef.doc.isEmpty {
                        Text(structDef.doc)
                            .font(.system(size: 12))
                            .foregroundStyle(Material3Colors.onSurfaceVariant.opacity(0.7))
                    }
                    
                    if !structDef.lib.isEmpty {
                        Text("Lib: \(structDef.lib)")
                            .font(.system(size: 12, design: .monospaced))
                            .foregroundStyle(Material3Colors.onSurfaceVariant.opacity(0.8))
                    }
                    
                    if isExpanded {
                        structDetails
                    } else {
                        Text("Fields: \(structDef.fields.count)")
                            .font(.system(size: 12))
                            .foregroundStyle(Material3Colors.onSurfaceVariant)
                    }
                    
                    Text(isExpanded ? "Click to collapse" : "Click to expand")
                        .font(.system(size: 11))
                        .foregroundStyle(Material3Colors.primary)
                        .padding(.top, 4)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
            }
            .buttonStyle(.plain)
        }
        .padding(12)
        .background(Material3Colors.secondaryContainer.opacity(0.3))
        .cornerRadius(8)
    }
    
    @ViewBuilder
    private var structDetails: some View {
        if !structDef.fields.isEmpty {
            Spacer().frame(height: 4)
            Text("Fields (\(structDef.fields.count)):")
                .font(.system(size: 12, weight: .bold))
                .foregroundStyle(Material3Colors.onSurfaceVariant)
            
            ForEach(Array(structDef.fields.enumerated()), id: \.offset) { index, field in
                VStack(alignment: .leading, spacing: 2) {
                    Text("[\(index)] \(field.name)")
                        .font(.system(size: 12, weight: .medium))
                        .foregroundStyle(Material3Colors.onSurfaceVariant)
                    Text("Type: \(getSpecTypeInfo(field.type))")
                        .font(.system(size: 11, design: .monospaced))
                        .foregroundStyle(Material3Colors.onSurfaceVariant.opacity(0.8))
                    if !field.doc.isEmpty {
                        Text("Doc: \(field.doc)")
                            .font(.system(size: 11))
                            .foregroundStyle(Material3Colors.onSurfaceVariant.opacity(0.7))
                    }
                }
                .padding(.leading, 12)
            }
        } else {
            Text("Fields: None")
                .font(.system(size: 12))
                .foregroundStyle(Material3Colors.onSurfaceVariant)
        }
    }
}

// MARK: - Union Spec View

struct UnionSpecView: View {
    let unionDef: SCSpecUDTUnionV0Xdr
    @Binding var isExpanded: Bool
    
    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            Button(action: { isExpanded.toggle() }) {
                VStack(alignment: .leading, spacing: 6) {
                    Text("Union: \(unionDef.name)")
                        .font(.system(size: 14, weight: .bold))
                        .foregroundStyle(Material3Colors.onSurfaceVariant)
                    
                    if !unionDef.doc.isEmpty {
                        Text(unionDef.doc)
                            .font(.system(size: 12))
                            .foregroundStyle(Material3Colors.onSurfaceVariant.opacity(0.7))
                    }
                    
                    if !unionDef.lib.isEmpty {
                        Text("Lib: \(unionDef.lib)")
                            .font(.system(size: 12, design: .monospaced))
                            .foregroundStyle(Material3Colors.onSurfaceVariant.opacity(0.8))
                    }
                    
                    if isExpanded {
                        // Cases detail
                        if !unionDef.cases.isEmpty {
                            Spacer().frame(height: 4)
                            Text("Cases (\(unionDef.cases.count)):")
                                .font(.system(size: 12, weight: .bold))
                                .foregroundStyle(Material3Colors.onSurfaceVariant)
                            
                            ForEach(Array(unionDef.cases.enumerated()), id: \.offset) { index, uCase in
                                VStack(alignment: .leading, spacing: 2) {
                                    if let voidCase = uCase as? SCSpecUDTUnionCaseV0Xdr.VoidCase {
                                        Text("[\(index)] \(voidCase.value.name) (void)")
                                            .font(.system(size: 12, weight: .medium))
                                            .foregroundStyle(Material3Colors.onSurfaceVariant)
                                        if !voidCase.value.doc.isEmpty {
                                            Text("Doc: \(voidCase.value.doc)")
                                                .font(.system(size: 11))
                                                .foregroundStyle(Material3Colors.onSurfaceVariant.opacity(0.7))
                                        }
                                    } else if let tupleCase = uCase as? SCSpecUDTUnionCaseV0Xdr.TupleCase {
                                        Text("[\(index)] \(tupleCase.value.name) (tuple)")
                                            .font(.system(size: 12, weight: .medium))
                                            .foregroundStyle(Material3Colors.onSurfaceVariant)
                                        let types = tupleCase.value.type.map { getSpecTypeInfo($0) }.joined(separator: ", ")
                                        Text("Types: [\(types)]")
                                            .font(.system(size: 11, design: .monospaced))
                                            .foregroundStyle(Material3Colors.onSurfaceVariant.opacity(0.8))
                                        if !tupleCase.value.doc.isEmpty {
                                            Text("Doc: \(tupleCase.value.doc)")
                                                .font(.system(size: 11))
                                                .foregroundStyle(Material3Colors.onSurfaceVariant.opacity(0.7))
                                        }
                                    }
                                }
                                .padding(.leading, 12)
                            }
                        } else {
                            Text("Cases: None")
                                .font(.system(size: 12))
                                .foregroundStyle(Material3Colors.onSurfaceVariant)
                        }
                    } else {
                        // Summary when collapsed
                        Text("Cases: \(unionDef.cases.count)")
                            .font(.system(size: 12))
                            .foregroundStyle(Material3Colors.onSurfaceVariant)
                    }
                    
                    Text(isExpanded ? "Click to collapse" : "Click to expand")
                        .font(.system(size: 11))
                        .foregroundStyle(Material3Colors.primary)
                        .padding(.top, 4)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
            }
            .buttonStyle(.plain)
        }
        .padding(12)
        .background(Material3Colors.tertiaryContainer.opacity(0.3))
        .cornerRadius(8)
    }
}


// MARK: - Enum Spec View

struct EnumSpecView: View {
    let enumDef: SCSpecUDTEnumV0Xdr
    @Binding var isExpanded: Bool
    
    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            Button(action: { isExpanded.toggle() }) {
                VStack(alignment: .leading, spacing: 6) {
                    Text("Enum: \(enumDef.name)")
                        .font(.system(size: 14, weight: .bold))
                        .foregroundStyle(Material3Colors.onSurfaceVariant)
                    
                    if !enumDef.doc.isEmpty {
                        Text(enumDef.doc)
                            .font(.system(size: 12))
                            .foregroundStyle(Material3Colors.onSurfaceVariant.opacity(0.7))
                    }
                    
                    Text("Cases: \(enumDef.cases.count)")
                        .font(.system(size: 12))
                        .foregroundStyle(Material3Colors.onSurfaceVariant)
                    
                    Text(isExpanded ? "Click to collapse" : "Click to expand")
                        .font(.system(size: 11))
                        .foregroundStyle(Material3Colors.primary)
                        .padding(.top, 4)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
            }
            .buttonStyle(.plain)
        }
        .padding(12)
        .background(Material3Colors.surfaceVariant.opacity(0.5))
        .cornerRadius(8)
    }
}

// MARK: - Error Enum Spec View

struct ErrorEnumSpecView: View {
    let errorEnum: SCSpecUDTErrorEnumV0Xdr
    @Binding var isExpanded: Bool
    
    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            Button(action: { isExpanded.toggle() }) {
                VStack(alignment: .leading, spacing: 6) {
                    Text("Error Enum: \(errorEnum.name)")
                        .font(.system(size: 14, weight: .bold))
                        .foregroundStyle(Material3Colors.onSurfaceVariant)
                    
                    if !errorEnum.doc.isEmpty {
                        Text(errorEnum.doc)
                            .font(.system(size: 12))
                            .foregroundStyle(Material3Colors.onSurfaceVariant.opacity(0.7))
                    }
                    
                    Text("Cases: \(errorEnum.cases.count)")
                        .font(.system(size: 12))
                        .foregroundStyle(Material3Colors.onSurfaceVariant)
                    
                    Text(isExpanded ? "Click to collapse" : "Click to expand")
                        .font(.system(size: 11))
                        .foregroundStyle(Material3Colors.primary)
                        .padding(.top, 4)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
            }
            .buttonStyle(.plain)
        }
        .padding(12)
        .background(Material3Colors.errorContainer.opacity(0.3))
        .cornerRadius(8)
    }
}

// MARK: - Event Spec View

struct EventSpecView: View {
    let event: SCSpecEventV0Xdr
    @Binding var isExpanded: Bool
    
    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            Button(action: { isExpanded.toggle() }) {
                VStack(alignment: .leading, spacing: 6) {
                    headerView
                    if isExpanded {
                        expandedView
                    } else {
                        collapsedView
                    }
                    toggleHintText
                }
                .frame(maxWidth: .infinity, alignment: .leading)
            }
            .buttonStyle(.plain)
        }
        .padding(12)
        .background(Material3Colors.primaryContainer.opacity(0.5))
        .cornerRadius(8)
    }
    
    @ViewBuilder
    private var headerView: some View {
        Text("Event: \(event.name)")
            .font(.system(size: 14, weight: .bold))
            .foregroundStyle(Material3Colors.onSurfaceVariant)
        
        if !event.doc.isEmpty {
            Text(event.doc)
                .font(.system(size: 12))
                .foregroundStyle(Material3Colors.onSurfaceVariant.opacity(0.7))
        }
        
        Text("Lib: \(event.lib)")
            .font(.system(size: 12, design: .monospaced))
            .foregroundStyle(Material3Colors.onSurfaceVariant.opacity(0.8))
    }
    
    @ViewBuilder
    private var expandedView: some View {
        // Prefix Topics
        if !event.prefixTopics.isEmpty {
            Spacer().frame(height: 4)
            Text("Prefix Topics (\(event.prefixTopics.count)):")
                .font(.system(size: 12, weight: .bold))
                .foregroundStyle(Material3Colors.onSurfaceVariant)
            
            ForEach(Array(event.prefixTopics.enumerated()), id: \.offset) { index, topic in
                Text("[\(index)] \(topic)")
                    .font(.system(size: 11, design: .monospaced))
                    .foregroundStyle(Material3Colors.onSurfaceVariant.opacity(0.8))
                    .padding(.leading, 12)
            }
        } else {
            Text("Prefix Topics: None")
                .font(.system(size: 12))
                .foregroundStyle(Material3Colors.onSurfaceVariant)
        }
        
        // Parameters
        if !event.params.isEmpty {
            Spacer().frame(height: 4)
            Text("Params (\(event.params.count)):")
                .font(.system(size: 12, weight: .bold))
                .foregroundStyle(Material3Colors.onSurfaceVariant)
            
            ForEach(Array(event.params.enumerated()), id: \.offset) { index, param in
                parameterView(index: index, param: param)
            }
        } else {
            Text("Params: None")
                .font(.system(size: 12))
                .foregroundStyle(Material3Colors.onSurfaceVariant)
        }
        
        // Data format
        Spacer().frame(height: 4)
        Text("Data Format: \(getDataFormatString())")
            .font(.system(size: 12))
            .foregroundStyle(Material3Colors.onSurfaceVariant)
    }
    
    @ViewBuilder
    private func parameterView(index: Int, param: SCSpecEventParamV0Xdr) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text("[\(index)] \(param.name)")
                .font(.system(size: 12, weight: .medium))
                .foregroundStyle(Material3Colors.onSurfaceVariant)
            Text("Type: \(getSpecTypeInfo(param.type))")
                .font(.system(size: 11, design: .monospaced))
                .foregroundStyle(Material3Colors.onSurfaceVariant.opacity(0.8))
            Text("Location: \(getLocationString(param.location))")
                .font(.system(size: 11))
                .foregroundStyle(Material3Colors.onSurfaceVariant.opacity(0.8))
            
            if !param.doc.isEmpty {
                Text("Doc: \(param.doc)")
                    .font(.system(size: 11))
                    .foregroundStyle(Material3Colors.onSurfaceVariant.opacity(0.7))
            }
        }
        .padding(.leading, 12)
    }
    
    @ViewBuilder
    private var collapsedView: some View {
        HStack(spacing: 12) {
            Text("Prefix Topics: \(event.prefixTopics.count)")
                .font(.system(size: 12))
            Text("Params: \(event.params.count)")
                .font(.system(size: 12))
        }
        .foregroundStyle(Material3Colors.onSurfaceVariant)
    }
    
    private var toggleHintText: some View {
        Text(isExpanded ? "Click to collapse" : "Click to expand")
            .font(.system(size: 11))
            .foregroundStyle(Material3Colors.primary)
            .padding(.top, 4)
    }
    
    private func getLocationString(_ location: SCSpecEventParamLocationV0Xdr) -> String {
        switch location {
        case .scSpecEventParamLocationData:
            return "data"
        case .scSpecEventParamLocationTopicList:
            return "topic list"
        default:
            return "unknown"
        }
    }
    
    private func getDataFormatString() -> String {
        switch event.dataFormat {
        case .scSpecEventDataFormatSingleValue:
            return "single value"
        case .scSpecEventDataFormatMap:
            return "map"
        case .scSpecEventDataFormatVec:
            return "vec"
        default:
            return "unknown"
        }
    }
}




// MARK: - Contract Error View

struct ContractErrorView: View {
    let error: ContractDetailsResult.Error
    
    var body: some View {
        VStack(spacing: 16) {
            // Error card
            VStack(alignment: .leading, spacing: 8) {
                Text("Error")
                    .font(.system(size: 16, weight: .bold))
                    .foregroundStyle(Material3Colors.onErrorContainer)
                
                Text(error.message)
                    .font(.system(size: 14))
                    .foregroundStyle(Material3Colors.onErrorContainer)
                
                if let exception = error.exception {
                    Text("Technical details: \(exception.message ?? "Unknown error")")
                        .font(.system(size: 12, design: .monospaced))
                        .foregroundStyle(Material3Colors.onErrorContainer)
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(16)
            .background(Material3Colors.errorContainer)
            .cornerRadius(12)
            
            // Troubleshooting card
            VStack(alignment: .leading, spacing: 8) {
                Text("Troubleshooting")
                    .font(.system(size: 14, weight: .semibold))
                    .foregroundStyle(Material3Colors.onSecondaryContainer)
                
                VStack(alignment: .leading, spacing: 4) {
                    Text("• Verify the contract ID is valid (starts with 'C' and is 56 characters)")
                        .font(.system(size: 13))
                        .foregroundStyle(Material3Colors.onSecondaryContainer)
                    
                    Text("• Make sure the contract exists on testnet and has been deployed")
                        .font(.system(size: 13))
                        .foregroundStyle(Material3Colors.onSecondaryContainer)
                    
                    Text("• Check your internet connection")
                        .font(.system(size: 13))
                        .foregroundStyle(Material3Colors.onSecondaryContainer)
                    
                    Text("• Try again in a moment if you're being rate-limited")
                        .font(.system(size: 13))
                        .foregroundStyle(Material3Colors.onSecondaryContainer)
                }
                .padding(.leading, 8)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(16)
            .background(Material3Colors.secondaryContainer)
            .cornerRadius(12)
        }
    }
}

// MARK: - Helper Functions

private func getSpecTypeInfo(_ specType: SCSpecTypeDefXdr) -> String {
    switch specType.discriminant {
    case .scSpecTypeVal:
        return "val"
    case .scSpecTypeBool:
        return "bool"
    case .scSpecTypeVoid:
        return "void"
    case .scSpecTypeError:
        return "error"
    case .scSpecTypeU32:
        return "u32"
    case .scSpecTypeI32:
        return "i32"
    case .scSpecTypeU64:
        return "u64"
    case .scSpecTypeI64:
        return "i64"
    case .scSpecTypeTimepoint:
        return "timepoint"
    case .scSpecTypeDuration:
        return "duration"
    case .scSpecTypeU128:
        return "u128"
    case .scSpecTypeI128:
        return "i128"
    case .scSpecTypeU256:
        return "u256"
    case .scSpecTypeI256:
        return "i256"
    case .scSpecTypeBytes:
        return "bytes"
    case .scSpecTypeString:
        return "string"
    case .scSpecTypeSymbol:
        return "symbol"
    case .scSpecTypeAddress:
        return "address"
    case .scSpecTypeMuxedAddress:
        return "muxed address"
    case .scSpecTypeOption:
        if let option = specType as? SCSpecTypeDefXdr.Option {
            let valueType = getSpecTypeInfo(option.value.valueType)
            return "option (value type: \(valueType))"
        }
        return "option"
    case .scSpecTypeResult:
        if let result = specType as? SCSpecTypeDefXdr.Result {
            let okType = getSpecTypeInfo(result.value.okType)
            let errorType = getSpecTypeInfo(result.value.errorType)
            return "result (ok type: \(okType), error type: \(errorType))"
        }
        return "result"
    case .scSpecTypeVec:
        if let vec = specType as? SCSpecTypeDefXdr.Vec {
            let elementType = getSpecTypeInfo(vec.value.elementType)
            return "vec (element type: \(elementType))"
        }
        return "vec"
    case .scSpecTypeMap:
        if let map = specType as? SCSpecTypeDefXdr.Map {
            let keyType = getSpecTypeInfo(map.value.keyType)
            let valueType = getSpecTypeInfo(map.value.valueType)
            return "map (key type: \(keyType), value type: \(valueType))"
        }
        return "map"
    case .scSpecTypeTuple:
        if let tuple = specType as? SCSpecTypeDefXdr.Tuple {
            let valueTypesStr = tuple.value.valueTypes.map { getSpecTypeInfo($0) }.joined(separator: ", ")
            return "tuple (value types: [\(valueTypesStr)])"
        }
        return "tuple"
    case .scSpecTypeBytesN:
        if let bytesN = specType as? SCSpecTypeDefXdr.BytesN {
            return "bytesN (n: \(bytesN.value.n))"
        }
        return "bytesN"
    case .scSpecTypeUdt:
        if let udt = specType as? SCSpecTypeDefXdr.Udt {
            return "udt (name: \(udt.value.name))"
        }
        return "udt"
    default:
        return "unknown"
    }
}


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

#Preview("Fund Account Screen") {
    NavigationStack {
        FundAccountScreen(toastManager: ToastManager())
    }
}

#Preview("Account Details Screen") {
    NavigationStack {
        AccountDetailsScreen(toastManager: ToastManager())
    }
}

#Preview("Trust Asset Screen") {
    NavigationStack {
        TrustAssetScreen(toastManager: ToastManager())
    }
}

#Preview("Send Payment Screen") {
    NavigationStack {
        SendPaymentScreen(toastManager: ToastManager())
    }
}



#Preview("Contract Details Screen") {
    NavigationStack {
        ContractDetailsScreen(toastManager: ToastManager())
    }
}
