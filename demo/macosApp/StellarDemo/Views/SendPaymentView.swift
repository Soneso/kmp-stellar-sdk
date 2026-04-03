import SwiftUI
import shared

struct SendPaymentScreen: View {
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

    @EnvironmentObject var bridgeWrapper: MacOSBridgeWrapper

    var body: some View {
        ScrollViewReader { proxy in
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
            .navigationToolbar(title: "Send a Payment")
            .onChange(of: paymentResult) { newValue in
                // Smart auto-scroll: reveal result without scrolling too far
                if newValue != nil {
                    withAnimation {
                        proxy.scrollTo("resultCard", anchor: .top)
                    }
                }
            }
        }
    }

    // MARK: - View Components

    private var infoCard: some View {
        InfoCard(title: "Send a payment on the Stellar network", color: .secondary) {
            Text("Transfer XLM (native asset) or any issued asset to another Stellar account. The destination account must exist, and for issued assets, must have an established trustline.")
                .font(.system(size: 13))
                .foregroundStyle(Material3Colors.onSecondaryContainer)
        }
    }

    private var importantNotesCard: some View {
        InfoCard(title: "Important Notes", color: .tertiary) {
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
    }

    private var inputFields: some View {
        VStack(spacing: 12) {
            StellarTextField(
                label: "Source Account ID",
                placeholder: "G... (your account)",
                text: $sourceAccountId,
                error: validationErrors["sourceAccountId"]
            )
            .onChange(of: sourceAccountId) { _ in
                validationErrors.removeValue(forKey: "sourceAccountId")
                paymentResult = nil
            }

            StellarTextField(
                label: "Destination Account ID",
                placeholder: "G... (recipient's account)",
                text: $destinationAccountId,
                error: validationErrors["destinationAccountId"]
            )
            .onChange(of: destinationAccountId) { _ in
                validationErrors.removeValue(forKey: "destinationAccountId")
                paymentResult = nil
            }

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
                        .background(Material3Colors.surface)
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
                        .background(Material3Colors.surface)
                        .cornerRadius(8)
                    }
                    .buttonStyle(.plain)
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(16)
            .background(Material3Colors.surface)
            .cornerRadius(12)

            if assetType == .issued {
                StellarTextField(
                    label: "Asset Code",
                    placeholder: "USD, EUR, USDC, etc.",
                    text: Binding(
                        get: { assetCode },
                        set: { assetCode = $0.trimmingCharacters(in: .whitespaces) }
                    ),
                    error: validationErrors["assetCode"]
                )
                .onChange(of: assetCode) { _ in
                    validationErrors.removeValue(forKey: "assetCode")
                    paymentResult = nil
                }
            }

            if assetType == .issued {
                StellarTextField(
                    label: "Asset Issuer",
                    placeholder: "G... (issuer's account)",
                    text: $assetIssuer,
                    error: validationErrors["assetIssuer"]
                )
                .onChange(of: assetIssuer) { _ in
                    validationErrors.removeValue(forKey: "assetIssuer")
                    paymentResult = nil
                }
            }

            StellarTextField(
                label: "Amount",
                placeholder: "10.0",
                text: $amount,
                error: validationErrors["amount"]
            )
            .onChange(of: amount) { _ in
                validationErrors.removeValue(forKey: "amount")
                paymentResult = nil
            }

            StellarTextField(
                label: "Source Secret Seed",
                placeholder: "S... (for signing)",
                text: $secretSeed,
                error: validationErrors["secretSeed"],
                isSecure: true,
                showVisibilityToggle: true,
                isVisible: $showSecret,
                backgroundColor: Material3Colors.tertiaryContainer
            )
            .onChange(of: secretSeed) { _ in
                validationErrors.removeValue(forKey: "secretSeed")
                paymentResult = nil
            }
        }
    }

    private var submitButton: some View {
        LoadingButton(
            action: submitPayment,
            isLoading: isSubmitting,
            isEnabled: isFormValid && !isSubmitting,
            icon: "paperplane.fill",
            text: "Send Payment",
            loadingText: "Sending Payment..."
        )
    }

    @ViewBuilder
    private var resultView: some View {
        if let result = paymentResult {
            switch result {
            case let success as SendPaymentResult.Success:
                PaymentSuccessCards(success: success)
                    .id("resultCard")
            case let error as SendPaymentResult.Error:
                errorCard(error)
                    .id("resultCard")
                troubleshootingCard
            default:
                EmptyView()
            }
        }
    }

    private func errorCard(_ error: SendPaymentResult.Error) -> some View {
        InfoCard(title: "Payment Failed", color: .error) {
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

                let result = try await bridgeWrapper.bridge.sendPayment(
                    sourceAccountId: sourceAccountId,
                    destinationAccountId: destinationAccountId,
                    assetCode: assetCodeValue,
                    assetIssuer: assetIssuerValue,
                    amount: amount,
                    secretSeed: secretSeed,
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

        if let error = FormValidation.validateAccountIdField(sourceAccountId) {
            errors["sourceAccountId"] = error
        }

        if let error = FormValidation.validateAccountIdField(destinationAccountId) {
            errors["destinationAccountId"] = error
        }

        if assetType == .issued {
            if let error = FormValidation.validateAssetCodeField(assetCode) {
                errors["assetCode"] = error
            }

            if let error = FormValidation.validateAccountIdField(assetIssuer) {
                errors["assetIssuer"] = error
            }
        }

        if amount.isEmpty {
            errors["amount"] = "Amount is required"
        } else if let amountValue = Double(amount), amountValue <= 0 {
            errors["amount"] = "Amount must be greater than 0"
        } else if Double(amount) == nil {
            errors["amount"] = "Invalid number format"
        }

        if let error = FormValidation.validateSecretSeedField(secretSeed) {
            errors["secretSeed"] = error
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

enum AssetType {
    case native
    case issued
}
