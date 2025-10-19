import SwiftUI
import shared

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

