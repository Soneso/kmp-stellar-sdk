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
