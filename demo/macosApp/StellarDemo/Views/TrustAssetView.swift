import SwiftUI
import shared

struct TrustAssetScreen: View {
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

    @EnvironmentObject var bridgeWrapper: MacOSBridgeWrapper

    var body: some View {
        ScrollViewReader { proxy in
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
            .navigationToolbar(title: "Trust Asset")
            .onChange(of: trustResult) { newValue in
                if newValue != nil {
                    withAnimation {
                        // Smart auto-scroll: reveal result without scrolling too far
                        proxy.scrollTo("resultCard", anchor: .top)
                    }
                }
            }
        }
    }

    // MARK: - View Components

    private var infoCard: some View {
        VStack(spacing: 12) {
            InfoCard(title: "Establish a Trustline", color: .secondary) {
                Text("A trustline is required before an account can hold non-native assets (assets other than XLM). This creates a ChangeTrust operation that allows your account to hold up to a specified limit of the asset.")
                    .font(.system(size: 13))
                    .foregroundStyle(Material3Colors.onSecondaryContainer)

                Divider()
                    .padding(.vertical, 4)

                Text("Important: Your account must have at least 0.5 XLM base reserve for each trustline.")
                    .font(.system(size: 12, weight: .medium))
                    .foregroundStyle(Material3Colors.onSecondaryContainer)
            }

            InfoCard(title: "Example Testnet Asset", color: .primary) {
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

            InfoCard(title: "Trust Limit", color: .tertiary) {
                VStack(alignment: .leading, spacing: 4) {
                    Text("The trust limit determines the maximum amount of the asset your account can hold.")
                        .font(.system(size: 13))
                        .foregroundStyle(Material3Colors.onTertiaryContainer)

                    Text("Leave empty for maximum (~922 trillion)")
                        .font(.system(size: 12, weight: .medium))
                        .foregroundStyle(Material3Colors.onTertiaryContainer)

                    Text("Enter 0 to remove an existing trustline (requires zero balance)")
                        .font(.system(size: 12, weight: .medium))
                        .foregroundStyle(Material3Colors.onTertiaryContainer)
                }
                .padding(.leading, 8)
            }
        }
    }

    private var inputFields: some View {
        VStack(spacing: 12) {
            StellarTextField(
                label: "Account ID",
                placeholder: "G...",
                text: $accountId,
                error: validationError
            )
            .onChange(of: accountId) { _ in
                validationError = nil
                trustResult = nil
            }

            StellarTextField(
                label: "Asset Code",
                placeholder: "USD, EUR, etc.",
                text: $assetCode,
                helpText: "1-12 alphanumeric characters"
            )
            .onChange(of: assetCode) { _ in
                validationError = nil
                trustResult = nil
            }

            StellarTextField(
                label: "Asset Issuer",
                placeholder: "G...",
                text: $assetIssuer
            )
            .onChange(of: assetIssuer) { _ in
                validationError = nil
                trustResult = nil
            }

            StellarTextField(
                label: "Trust Limit (Optional)",
                placeholder: "Leave empty for maximum",
                text: $trustLimit,
                helpText: "Maximum amount to hold (empty = max, 0 = remove trustline)"
            )
            .onChange(of: trustLimit) { _ in
                validationError = nil
                trustResult = nil
            }

            StellarTextField(
                label: "Secret Seed",
                placeholder: "S...",
                text: $secretSeed,
                helpText: "Required for signing the transaction",
                isSecure: true,
                showVisibilityToggle: true,
                isVisible: $showSecret
            )
            .onChange(of: secretSeed) { _ in
                validationError = nil
                trustResult = nil
            }
        }
    }

    private var submitButton: some View {
        LoadingButton(
            action: submitTrustline,
            isLoading: isSubmitting,
            isEnabled: isFormValid,
            icon: "link.badge.plus",
            text: "Establish Trustline",
            loadingText: "Submitting..."
        )
    }

    @ViewBuilder
    private var resultView: some View {
        if let result = trustResult {
            switch result {
            case let success as TrustAssetResult.Success:
                TrustAssetSuccessCards(success: success)
                    .id("resultCard")
            case let error as TrustAssetResult.Error:
                errorCard(error)
                    .id("resultCard")
                troubleshootingCard
            default:
                EmptyView()
            }
        }
    }

    private func errorCard(_ error: TrustAssetResult.Error) -> some View {
        InfoCard(title: "Error", color: .error) {
            Text(error.message)
                .font(.system(size: 14))
                .foregroundStyle(Material3Colors.onErrorContainer)

            if let exception = error.exception {
                Text("Technical details: \(exception.message ?? "Unknown error")")
                    .font(.system(size: 13, design: .monospaced))
                    .foregroundStyle(Material3Colors.onErrorContainer)
            }
        }
    }

    private var troubleshootingCard: some View {
        InfoCard(title: "Troubleshooting", color: .secondary) {
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

                Text("• Asset code must be 1-12 alphanumeric characters (a-z, A-Z, 0-9)")
                    .font(.system(size: 13))
                    .foregroundStyle(Material3Colors.onSecondaryContainer)

                Text("• To remove a trustline, enter 0 as the limit (requires zero balance)")
                    .font(.system(size: 13))
                    .foregroundStyle(Material3Colors.onSecondaryContainer)
            }
            .padding(.leading, 8)
        }
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
        if let error = FormValidation.validateAccountIdField(accountId) {
            toastManager.show(error)
            return
        }

        if let error = FormValidation.validateAssetCodeField(assetCode) {
            toastManager.show(error)
            return
        }

        if let error = FormValidation.validateAccountIdField(assetIssuer) {
            toastManager.show(error)
            return
        }

        if !trustLimit.isEmpty {
            if Double(trustLimit) == nil {
                toastManager.show("Trust limit must be a valid decimal number")
                return
            }
            if let limitValue = Double(trustLimit), limitValue < 0 {
                toastManager.show("Trust limit must be 0 or greater (0 removes the trustline)")
                return
            }
        }

        if let error = FormValidation.validateSecretSeedField(secretSeed) {
            toastManager.show(error)
            return
        }

        isSubmitting = true
        trustResult = nil
        validationError = nil

        Task {
            do {
                let limit = trustLimit.isEmpty ? "922337203685.4775807" : trustLimit
                let result = try await bridgeWrapper.bridge.trustAsset(
                    accountId: accountId,
                    assetCode: assetCode,
                    assetIssuer: assetIssuer,
                    secretSeed: secretSeed,
                    limit: limit
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
