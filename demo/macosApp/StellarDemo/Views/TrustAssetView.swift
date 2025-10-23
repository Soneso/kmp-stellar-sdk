import SwiftUI
import shared

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

