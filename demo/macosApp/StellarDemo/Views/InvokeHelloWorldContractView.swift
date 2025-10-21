import SwiftUI
import shared

// MARK: - Invoke Hello World Contract Screen (matches Compose InvokeHelloWorldContractScreen)

struct InvokeHelloWorldContractScreen: View {
    @Environment(\.dismiss) var dismiss
    @ObservedObject var toastManager: ToastManager
    @State private var contractId = ""
    @State private var toParameter = ""
    @State private var submitterAccountId = ""
    @State private var secretKey = ""
    @State private var showSecret = false
    @State private var isInvoking = false
    @State private var invocationResult: InvokeHelloWorldResult?
    @State private var validationErrors: [String: String] = [:]

    var body: some View {
        ScrollView {
            VStack(spacing: 16) {
                infoCard
                contractDetailsCard
                submitterAccountCard
                invokeButton
                resultView
                placeholderView
            }
            .padding(16)
        }
        .background(Material3Colors.surface)
        .navigationTitle("Invoke Hello World Contract")
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
            Text("ContractClient.invoke(): Beginner-friendly contract invocation")
                .font(.system(size: 16, weight: .semibold))
                .foregroundStyle(Material3Colors.onSecondaryContainer)

            Text("This demo showcases the SDK's high-level contract invocation API with automatic type conversion. The invoke() method accepts Map-based arguments and handles XDR conversion, transaction building, signing, submission, and result parsing automatically.")
                .font(.system(size: 13))
                .foregroundStyle(Material3Colors.onSecondaryContainer)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(16)
        .background(Material3Colors.secondaryContainer)
        .cornerRadius(12)
    }

    private var contractDetailsCard: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Contract Details")
                .font(.system(size: 14, weight: .bold))
                .foregroundStyle(Material3Colors.onSurface)

            // Contract ID field
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
                            .stroke(
                                validationErrors["contractId"] != nil ?
                                Material3Colors.onErrorContainer :
                                Material3Colors.onSurfaceVariant.opacity(0.3),
                                lineWidth: 1
                            )
                    )
                    .onChange(of: contractId) { _ in
                        validationErrors.removeValue(forKey: "contractId")
                        invocationResult = nil
                    }

                if let error = validationErrors["contractId"] {
                    Text(error)
                        .font(.system(size: 12))
                        .foregroundStyle(Material3Colors.onErrorContainer)
                } else {
                    Text("Deploy hello world contract first using 'Deploy a Smart Contract'")
                        .font(.system(size: 12))
                        .foregroundStyle(Material3Colors.onSurfaceVariant)
                }
            }

            // To parameter field
            VStack(alignment: .leading, spacing: 4) {
                Text("Name (to parameter)")
                    .font(.system(size: 12, weight: .medium))
                    .foregroundStyle(Material3Colors.onSurfaceVariant)

                TextField("Alice", text: $toParameter)
                    .textFieldStyle(.plain)
                    .font(.system(.body, design: .default))
                    .padding(12)
                    .background(Color.white)
                    .overlay(
                        RoundedRectangle(cornerRadius: 4)
                            .stroke(
                                validationErrors["toParameter"] != nil ?
                                Material3Colors.onErrorContainer :
                                Material3Colors.onSurfaceVariant.opacity(0.3),
                                lineWidth: 1
                            )
                    )
                    .onChange(of: toParameter) { _ in
                        validationErrors.removeValue(forKey: "toParameter")
                        invocationResult = nil
                    }

                if let error = validationErrors["toParameter"] {
                    Text(error)
                        .font(.system(size: 12))
                        .foregroundStyle(Material3Colors.onErrorContainer)
                } else {
                    Text("The name to greet in the hello function")
                        .font(.system(size: 12))
                        .foregroundStyle(Material3Colors.onSurfaceVariant)
                }
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(16)
        .background(Color.white)
        .cornerRadius(12)
        .shadow(color: Material3Colors.cardShadow, radius: 2, x: 0, y: 1)
    }

    private var submitterAccountCard: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Submitter Account")
                .font(.system(size: 14, weight: .bold))
                .foregroundStyle(Material3Colors.onSurface)

            // Submitter Account ID field
            VStack(alignment: .leading, spacing: 4) {
                Text("Submitter Account ID")
                    .font(.system(size: 12, weight: .medium))
                    .foregroundStyle(Material3Colors.onSurfaceVariant)

                TextField("G...", text: $submitterAccountId)
                    .textFieldStyle(.plain)
                    .font(.system(.body, design: .monospaced))
                    .padding(12)
                    .background(Color.white)
                    .overlay(
                        RoundedRectangle(cornerRadius: 4)
                            .stroke(
                                validationErrors["submitterAccount"] != nil ?
                                Material3Colors.onErrorContainer :
                                Material3Colors.onSurfaceVariant.opacity(0.3),
                                lineWidth: 1
                            )
                    )
                    .onChange(of: submitterAccountId) { _ in
                        validationErrors.removeValue(forKey: "submitterAccount")
                        invocationResult = nil
                    }

                if let error = validationErrors["submitterAccount"] {
                    Text(error)
                        .font(.system(size: 12))
                        .foregroundStyle(Material3Colors.onErrorContainer)
                } else {
                    Text("Account that will sign and submit the transaction")
                        .font(.system(size: 12))
                        .foregroundStyle(Material3Colors.onSurfaceVariant)
                }
            }

            // Secret Key field
            VStack(alignment: .leading, spacing: 4) {
                Text("Secret Key")
                    .font(.system(size: 12, weight: .medium))
                    .foregroundStyle(Material3Colors.onSurfaceVariant)

                HStack(spacing: 0) {
                    if showSecret {
                        TextField("S...", text: $secretKey)
                            .textFieldStyle(.plain)
                            .font(.system(.body, design: .monospaced))
                    } else {
                        SecureField("S...", text: $secretKey)
                            .textFieldStyle(.plain)
                            .font(.system(.body, design: .monospaced))
                    }

                    Button(action: { showSecret.toggle() }) {
                        Image(systemName: showSecret ? "eye.slash.fill" : "eye.fill")
                            .foregroundColor(Material3Colors.onSurfaceVariant)
                            .padding(.horizontal, 8)
                    }
                    .buttonStyle(.plain)
                }
                .padding(12)
                .background(Color.white)
                .overlay(
                    RoundedRectangle(cornerRadius: 4)
                        .stroke(
                            validationErrors["secretKey"] != nil ?
                            Material3Colors.onErrorContainer :
                            Material3Colors.onSurfaceVariant.opacity(0.3),
                            lineWidth: 1
                        )
                )
                .onChange(of: secretKey) { _ in
                    validationErrors.removeValue(forKey: "secretKey")
                    invocationResult = nil
                }

                if let error = validationErrors["secretKey"] {
                    Text(error)
                        .font(.system(size: 12))
                        .foregroundStyle(Material3Colors.onErrorContainer)
                }
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(16)
        .background(Color.white)
        .cornerRadius(12)
        .shadow(color: Material3Colors.cardShadow, radius: 2, x: 0, y: 1)
    }

    private var invokeButton: some View {
        Button(action: invokeContract) {
            HStack(spacing: 8) {
                if isInvoking {
                    ProgressView()
                        .controlSize(.small)
                        .tint(.white)
                    Text("Invoking...")
                } else {
                    Image(systemName: "play.fill")
                    Text("Invoke Contract")
                }
            }
            .frame(maxWidth: .infinity)
            .frame(height: 56)
            .background(
                (isInvoking || !isFormValid) ?
                Material3Colors.primary.opacity(0.6) :
                Material3Colors.primary
            )
            .foregroundColor(.white)
            .cornerRadius(12)
        }
        .disabled(isInvoking || !isFormValid)
        .buttonStyle(.plain)
    }

    private var isFormValid: Bool {
        !contractId.isEmpty &&
        !toParameter.isEmpty &&
        !submitterAccountId.isEmpty &&
        !secretKey.isEmpty
    }

    @ViewBuilder
    private var resultView: some View {
        if let result = invocationResult {
            switch result {
            case let success as InvokeHelloWorldResult.Success:
                successCard(success)
            case let error as InvokeHelloWorldResult.Error:
                errorCard(error)
                troubleshootingCard
            default:
                EmptyView()
            }
        }
    }

    private func successCard(_ success: InvokeHelloWorldResult.Success) -> some View {
        VStack(alignment: .leading, spacing: 12) {
            // Header
            HStack(spacing: 8) {
                Image(systemName: "checkmark.circle.fill")
                    .foregroundStyle(Material3Colors.onSuccessContainer)
                Text("Contract Invocation Successful")
                    .font(.system(size: 16, weight: .bold))
                    .foregroundStyle(Material3Colors.onSuccessContainer)
            }

            Divider()

            // Greeting response
            VStack(alignment: .leading, spacing: 8) {
                Text("Greeting Response")
                    .font(.system(size: 12, weight: .medium))
                    .foregroundStyle(Material3Colors.onSuccessContainer.opacity(0.7))

                Text(success.greeting)
                    .font(.system(size: 20, weight: .medium, design: .default))
                    .foregroundStyle(Material3Colors.onSuccessContainer)
                    .padding(16)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(Material3Colors.onSuccessContainer.opacity(0.1))
                    .cornerRadius(8)
            }

            Text("The contract function was successfully invoked using ContractClient.invoke() with automatic type conversion from Map arguments to Soroban XDR types.")
                .font(.system(size: 13))
                .foregroundStyle(Material3Colors.onSuccessContainer)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(16)
        .background(Material3Colors.successContainer)
        .cornerRadius(12)
    }

    private func errorCard(_ error: InvokeHelloWorldResult.Error) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack(spacing: 8) {
                Image(systemName: "exclamationmark.triangle.fill")
                    .foregroundStyle(Material3Colors.onErrorContainer)
                Text("Invocation Failed")
                    .font(.system(size: 16, weight: .bold))
                    .foregroundStyle(Material3Colors.onErrorContainer)
            }

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
    }

    private var troubleshootingCard: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Troubleshooting")
                .font(.system(size: 14, weight: .semibold))
                .foregroundStyle(Material3Colors.onSecondaryContainer)

            VStack(alignment: .leading, spacing: 4) {
                Text("• Ensure the contract ID is correct and the contract is deployed on testnet")
                    .font(.system(size: 13))
                    .foregroundStyle(Material3Colors.onSecondaryContainer)

                Text("• Verify the submitter account has sufficient XLM balance for fees")
                    .font(.system(size: 13))
                    .foregroundStyle(Material3Colors.onSecondaryContainer)

                Text("• Check that the secret key matches the submitter account ID")
                    .font(.system(size: 13))
                    .foregroundStyle(Material3Colors.onSecondaryContainer)

                Text("• Make sure you deployed the Hello World contract first (not another contract)")
                    .font(.system(size: 13))
                    .foregroundStyle(Material3Colors.onSecondaryContainer)

                Text("• Check your internet connection and Soroban RPC availability")
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
        if invocationResult == nil && !isInvoking && contractId.isEmpty {
            VStack(spacing: 16) {
                Image(systemName: "play.circle")
                    .font(.system(size: 64))
                    .foregroundStyle(Material3Colors.onSurfaceVariant.opacity(0.3))

                Text("Enter contract details to invoke the hello function")
                    .font(.system(size: 14))
                    .foregroundStyle(Material3Colors.onSurfaceVariant)
                    .multilineTextAlignment(.center)
            }
            .padding(.vertical, 32)
        }
    }

    // MARK: - Actions

    private func validateInputs() -> [String: String] {
        var errors: [String: String] = [:]

        // Validate contract ID
        if contractId.isEmpty {
            errors["contractId"] = "Contract ID is required"
        } else if !contractId.hasPrefix("C") {
            errors["contractId"] = "Contract ID must start with 'C'"
        } else if contractId.count != 56 {
            errors["contractId"] = "Contract ID must be 56 characters"
        }

        // Validate "to" parameter
        if toParameter.isEmpty {
            errors["toParameter"] = "Name parameter is required"
        }

        // Validate submitter account ID
        if submitterAccountId.isEmpty {
            errors["submitterAccount"] = "Submitter account ID is required"
        } else if !submitterAccountId.hasPrefix("G") {
            errors["submitterAccount"] = "Account ID must start with 'G'"
        } else if submitterAccountId.count != 56 {
            errors["submitterAccount"] = "Account ID must be 56 characters"
        }

        // Validate secret key
        if secretKey.isEmpty {
            errors["secretKey"] = "Secret key is required"
        } else if !secretKey.hasPrefix("S") {
            errors["secretKey"] = "Secret key must start with 'S'"
        } else if secretKey.count != 56 {
            errors["secretKey"] = "Secret key must be 56 characters"
        }

        return errors
    }

    private func invokeContract() {
        let errors = validateInputs()
        if !errors.isEmpty {
            validationErrors = errors
            return
        }

        isInvoking = true
        invocationResult = nil
        validationErrors = [:]

        Task {
            do {
                // Call the Kotlin function from the shared module
                let result = try await InvokeHelloWorldContractKt.invokeHelloWorldContract(
                    contractId: contractId,
                    to: toParameter,
                    submitterAccountId: submitterAccountId,
                    secretKey: secretKey,
                    useTestnet: true
                )

                await MainActor.run {
                    invocationResult = result
                    isInvoking = false
                }
            } catch {
                await MainActor.run {
                    invocationResult = InvokeHelloWorldResult.Error(
                        message: "Failed to invoke contract: \(error.localizedDescription)",
                        exception: error as? KotlinThrowable
                    )
                    isInvoking = false
                }
            }
        }
    }
}
