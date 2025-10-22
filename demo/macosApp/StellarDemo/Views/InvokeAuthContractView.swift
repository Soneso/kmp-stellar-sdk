import SwiftUI
import shared

// MARK: - Invoke Auth Contract Screen (matches Compose InvokeAuthContractScreen)

struct InvokeAuthContractScreen: View {
    @Environment(\.dismiss) var dismiss
    @ObservedObject var toastManager: ToastManager
    @State private var contractId = ""
    @State private var userAccountId = ""
    @State private var userSecretKey = ""
    @State private var showUserSecret = false
    @State private var useSameAccount = true
    @State private var sourceAccountId = ""
    @State private var sourceSecretKey = ""
    @State private var showSourceSecret = false
    @State private var value = "1"
    @State private var isInvoking = false
    @State private var invocationResult: InvokeAuthContractResult?
    @State private var errorMessage: String?
    @State private var validationErrors: [String: String] = [:]

    var body: some View {
        ScrollView {
            VStack(spacing: 16) {
                infoCard
                contractDetailsCard
                userAccountCard
                sameAccountToggle

                // Source account card (conditional, shown when toggle is OFF)
                if !useSameAccount {
                    sourceAccountCard
                }

                valueInputCard
                invokeButton
                resultView
                placeholderView
            }
            .padding(16)
        }
        .background(Material3Colors.surface)
        .navigationTitle("Invoke Auth Contract")
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
            Text("Deploy the auth contract first!")
                .font(.system(size: 16, weight: .semibold))
                .foregroundStyle(Material3Colors.onSecondaryContainer)

            Text("This demo showcases dynamic authorization handling with needsNonInvokerSigningBy(). The SDK automatically detects whether same-invoker (automatic authorization) or different-invoker (manual authorization) pattern applies, and conditionally calls signAuthEntries() only when needed.")
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
                        errorMessage = nil
                    }

                if let error = validationErrors["contractId"] {
                    Text(error)
                        .font(.system(size: 12))
                        .foregroundStyle(Material3Colors.onErrorContainer)
                } else {
                    Text("Deploy the auth contract first using 'Deploy Smart Contract'")
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

    private var userAccountCard: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("User Account")
                .font(.system(size: 14, weight: .bold))
                .foregroundStyle(Material3Colors.onSurface)

            // User Account ID
            VStack(alignment: .leading, spacing: 4) {
                Text("User Account ID")
                    .font(.system(size: 12, weight: .medium))
                    .foregroundStyle(Material3Colors.onSurfaceVariant)

                TextField("G...", text: $userAccountId)
                    .textFieldStyle(.plain)
                    .font(.system(.body, design: .monospaced))
                    .padding(12)
                    .background(Color.white)
                    .overlay(
                        RoundedRectangle(cornerRadius: 4)
                            .stroke(
                                validationErrors["userAccount"] != nil ?
                                Material3Colors.onErrorContainer :
                                Material3Colors.onSurfaceVariant.opacity(0.3),
                                lineWidth: 1
                            )
                    )
                    .onChange(of: userAccountId) { _ in
                        validationErrors.removeValue(forKey: "userAccount")
                        invocationResult = nil
                        errorMessage = nil
                    }

                if let error = validationErrors["userAccount"] {
                    Text(error)
                        .font(.system(size: 12))
                        .foregroundStyle(Material3Colors.onErrorContainer)
                } else {
                    Text("The account that owns the counter (will be incremented)")
                        .font(.system(size: 12))
                        .foregroundStyle(Material3Colors.onSurfaceVariant)
                }
            }

            // User Secret Key
            VStack(alignment: .leading, spacing: 4) {
                HStack {
                    Text("User Secret Key")
                        .font(.system(size: 12, weight: .medium))
                        .foregroundStyle(Material3Colors.onSurfaceVariant)

                    Spacer()

                    Button(action: { showUserSecret.toggle() }) {
                        Image(systemName: showUserSecret ? "eye.slash.fill" : "eye.fill")
                            .font(.system(size: 14))
                            .foregroundStyle(Material3Colors.onSurfaceVariant)
                    }
                    .buttonStyle(.plain)
                    .help(showUserSecret ? "Hide secret" : "Show secret")
                }

                if showUserSecret {
                    TextField("S...", text: $userSecretKey)
                        .textFieldStyle(.plain)
                        .font(.system(.body, design: .monospaced))
                        .padding(12)
                        .background(Color.white)
                        .overlay(
                            RoundedRectangle(cornerRadius: 4)
                                .stroke(
                                    validationErrors["userSecretKey"] != nil ?
                                    Material3Colors.onErrorContainer :
                                    Material3Colors.onSurfaceVariant.opacity(0.3),
                                    lineWidth: 1
                                )
                        )
                        .onChange(of: userSecretKey) { _ in
                            validationErrors.removeValue(forKey: "userSecretKey")
                            invocationResult = nil
                            errorMessage = nil
                        }
                } else {
                    SecureField("S...", text: $userSecretKey)
                        .textFieldStyle(.plain)
                        .font(.system(.body, design: .monospaced))
                        .padding(12)
                        .background(Color.white)
                        .overlay(
                            RoundedRectangle(cornerRadius: 4)
                                .stroke(
                                    validationErrors["userSecretKey"] != nil ?
                                    Material3Colors.onErrorContainer :
                                    Material3Colors.onSurfaceVariant.opacity(0.3),
                                    lineWidth: 1
                                )
                        )
                        .onChange(of: userSecretKey) { _ in
                            validationErrors.removeValue(forKey: "userSecretKey")
                            invocationResult = nil
                            errorMessage = nil
                        }
                }

                if let error = validationErrors["userSecretKey"] {
                    Text(error)
                        .font(.system(size: 12))
                        .foregroundStyle(Material3Colors.onErrorContainer)
                } else {
                    Text("Used to authorize the increment operation")
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

    private var sameAccountToggle: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                VStack(alignment: .leading, spacing: 4) {
                    Text("Use Same Account")
                        .font(.system(size: 14, weight: .bold))
                        .foregroundStyle(Material3Colors.onSurface)

                    Text(useSameAccount ?
                         "Same-invoker: User submits their own transaction (automatic authorization)" :
                         "Different-invoker: Source account submits on behalf of user (manual authorization required)")
                        .font(.system(size: 13))
                        .foregroundStyle(Material3Colors.onSurfaceVariant)
                }

                Spacer()

                Toggle("", isOn: $useSameAccount)
                    .labelsHidden()
                    .toggleStyle(.switch)
                    .tint(Material3Colors.primary)
                    .onChange(of: useSameAccount) { newValue in
                        if newValue {
                            // When switching to same account, clear source fields
                            sourceAccountId = ""
                            sourceSecretKey = ""
                            validationErrors.removeValue(forKey: "sourceAccount")
                            validationErrors.removeValue(forKey: "sourceSecretKey")
                        }
                        invocationResult = nil
                        errorMessage = nil
                    }
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(16)
        .background(Material3Colors.primaryContainer.opacity(0.3))
        .cornerRadius(12)
    }

    private var sourceAccountCard: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Source Account (Transaction Submitter)")
                .font(.system(size: 14, weight: .bold))
                .foregroundStyle(Material3Colors.onSurface)

            // Source Account ID
            VStack(alignment: .leading, spacing: 4) {
                Text("Source Account ID")
                    .font(.system(size: 12, weight: .medium))
                    .foregroundStyle(Material3Colors.onSurfaceVariant)

                TextField("G...", text: $sourceAccountId)
                    .textFieldStyle(.plain)
                    .font(.system(.body, design: .monospaced))
                    .padding(12)
                    .background(Color.white)
                    .overlay(
                        RoundedRectangle(cornerRadius: 4)
                            .stroke(
                                validationErrors["sourceAccount"] != nil ?
                                Material3Colors.onErrorContainer :
                                Material3Colors.onSurfaceVariant.opacity(0.3),
                                lineWidth: 1
                            )
                    )
                    .onChange(of: sourceAccountId) { _ in
                        validationErrors.removeValue(forKey: "sourceAccount")
                        invocationResult = nil
                        errorMessage = nil
                    }

                if let error = validationErrors["sourceAccount"] {
                    Text(error)
                        .font(.system(size: 12))
                        .foregroundStyle(Material3Colors.onErrorContainer)
                } else {
                    Text("Different account that will submit the transaction")
                        .font(.system(size: 12))
                        .foregroundStyle(Material3Colors.onSurfaceVariant)
                }
            }

            // Source Secret Key
            VStack(alignment: .leading, spacing: 4) {
                HStack {
                    Text("Source Secret Key")
                        .font(.system(size: 12, weight: .medium))
                        .foregroundStyle(Material3Colors.onSurfaceVariant)

                    Spacer()

                    Button(action: { showSourceSecret.toggle() }) {
                        Image(systemName: showSourceSecret ? "eye.slash.fill" : "eye.fill")
                            .font(.system(size: 14))
                            .foregroundStyle(Material3Colors.onSurfaceVariant)
                    }
                    .buttonStyle(.plain)
                    .help(showSourceSecret ? "Hide secret" : "Show secret")
                }

                if showSourceSecret {
                    TextField("S...", text: $sourceSecretKey)
                        .textFieldStyle(.plain)
                        .font(.system(.body, design: .monospaced))
                        .padding(12)
                        .background(Color.white)
                        .overlay(
                            RoundedRectangle(cornerRadius: 4)
                                .stroke(
                                    validationErrors["sourceSecretKey"] != nil ?
                                    Material3Colors.onErrorContainer :
                                    Material3Colors.onSurfaceVariant.opacity(0.3),
                                    lineWidth: 1
                                )
                        )
                        .onChange(of: sourceSecretKey) { _ in
                            validationErrors.removeValue(forKey: "sourceSecretKey")
                            invocationResult = nil
                            errorMessage = nil
                        }
                } else {
                    SecureField("S...", text: $sourceSecretKey)
                        .textFieldStyle(.plain)
                        .font(.system(.body, design: .monospaced))
                        .padding(12)
                        .background(Color.white)
                        .overlay(
                            RoundedRectangle(cornerRadius: 4)
                                .stroke(
                                    validationErrors["sourceSecretKey"] != nil ?
                                    Material3Colors.onErrorContainer :
                                    Material3Colors.onSurfaceVariant.opacity(0.3),
                                    lineWidth: 1
                                )
                        )
                        .onChange(of: sourceSecretKey) { _ in
                            validationErrors.removeValue(forKey: "sourceSecretKey")
                            invocationResult = nil
                            errorMessage = nil
                        }
                }

                if let error = validationErrors["sourceSecretKey"] {
                    Text(error)
                        .font(.system(size: 12))
                        .foregroundStyle(Material3Colors.onErrorContainer)
                } else {
                    Text("Used to sign and submit the transaction")
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

    private var valueInputCard: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Increment Amount")
                .font(.system(size: 14, weight: .bold))
                .foregroundStyle(Material3Colors.onSurface)

            VStack(alignment: .leading, spacing: 4) {
                Text("Value")
                    .font(.system(size: 12, weight: .medium))
                    .foregroundStyle(Material3Colors.onSurfaceVariant)

                TextField("1", text: $value)
                    .textFieldStyle(.plain)
                    .font(.system(.body, design: .monospaced))
                    .padding(12)
                    .background(Color.white)
                    .overlay(
                        RoundedRectangle(cornerRadius: 4)
                            .stroke(
                                validationErrors["value"] != nil ?
                                Material3Colors.onErrorContainer :
                                Material3Colors.onSurfaceVariant.opacity(0.3),
                                lineWidth: 1
                            )
                    )
                    .onChange(of: value) { _ in
                        validationErrors.removeValue(forKey: "value")
                        invocationResult = nil
                        errorMessage = nil
                    }

                if let error = validationErrors["value"] {
                    Text(error)
                        .font(.system(size: 12))
                        .foregroundStyle(Material3Colors.onErrorContainer)
                } else {
                    Text("Amount to increment the counter (positive integer)")
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

    private var invokeButton: some View {
        Button(action: invokeContract) {
            HStack(spacing: 8) {
                if isInvoking {
                    ProgressView()
                        .controlSize(.small)
                        .tint(.white)
                    Text("Invoking...")
                } else {
                    Image(systemName: "checkmark.shield.fill")
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
        if contractId.isEmpty || userAccountId.isEmpty || userSecretKey.isEmpty || value.isEmpty {
            return false
        }

        if !useSameAccount {
            if sourceAccountId.isEmpty || sourceSecretKey.isEmpty {
                return false
            }
        }

        return true
    }

    @ViewBuilder
    private var resultView: some View {
        if let result = invocationResult {
            if let success = result as? InvokeAuthContractResult.Success {
                successCard(success)
            } else if let failure = result as? InvokeAuthContractResult.Failure {
                errorCard(failure.message)
                troubleshootingCard
            }
        } else if let error = errorMessage {
            errorCard(error)
            troubleshootingCard
        }
    }

    private func successCard(_ result: InvokeAuthContractResult.Success) -> some View {
        VStack(spacing: 16) {
            // Success header card
            VStack(alignment: .leading, spacing: 8) {
                HStack(spacing: 8) {
                    Image(systemName: "checkmark.circle.fill")
                        .font(.system(size: 24))
                        .foregroundStyle(Material3Colors.onSuccessContainer)

                    Text("Contract Invocation Successful")
                        .font(.system(size: 16, weight: .bold))
                        .foregroundStyle(Material3Colors.onSuccessContainer)
                }

                Text("The auth contract was successfully invoked")
                    .font(.system(size: 14))
                    .foregroundStyle(Material3Colors.onSuccessContainer)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(16)
            .background(Material3Colors.successContainer)
            .cornerRadius(12)

            // Transaction Hash Card (Prominent)
            VStack(alignment: .leading, spacing: 12) {
                Text("Transaction Hash")
                    .font(.system(size: 14, weight: .bold))
                    .foregroundStyle(Material3Colors.onPrimaryContainer)

                Divider()

                CopyableDetailRow(label: "Hash", value: result.transactionHash)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(16)
            .background(Material3Colors.primaryContainer)
            .cornerRadius(12)

            // Counter Value Card
            VStack(alignment: .leading, spacing: 12) {
                Text("Counter Value")
                    .font(.system(size: 14, weight: .bold))
                    .foregroundStyle(Material3Colors.onSurfaceVariant)

                Divider()

                Text("\(result.counterValue)")
                    .font(.system(size: 48, weight: .bold, design: .default))
                    .foregroundStyle(Material3Colors.onSurfaceVariant)
                    .padding(16)
                    .frame(maxWidth: .infinity, alignment: .center)
                    .background(Material3Colors.onSurfaceVariant.opacity(0.1))
                    .cornerRadius(8)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(16)
            .background(Material3Colors.surfaceVariant)
            .cornerRadius(12)

            // Authorization Details Card
            VStack(alignment: .leading, spacing: 12) {
                Text("Authorization Details")
                    .font(.system(size: 14, weight: .bold))
                    .foregroundStyle(Material3Colors.onSurfaceVariant)

                Divider()

                // Detected scenario
                VStack(alignment: .leading, spacing: 8) {
                    Text("Detected Scenario")
                        .font(.system(size: 12, weight: .medium))
                        .foregroundStyle(Material3Colors.onSurfaceVariant.opacity(0.7))

                    Text(result.scenario)
                        .font(.system(size: 16, weight: .semibold))
                        .foregroundStyle(Material3Colors.onSurfaceVariant)
                        .padding(12)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .background(Material3Colors.onSurfaceVariant.opacity(0.1))
                        .cornerRadius(8)
                }

                // Who needed to sign
                VStack(alignment: .leading, spacing: 8) {
                    Text("Who Needed to Sign")
                        .font(.system(size: 12, weight: .medium))
                        .foregroundStyle(Material3Colors.onSurfaceVariant.opacity(0.7))

                    if result.whoNeedsToSign.isEmpty {
                        Text("None (automatic authorization)")
                            .font(.system(size: 14))
                            .foregroundStyle(Material3Colors.onSurfaceVariant)
                            .padding(12)
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .background(Material3Colors.onSurfaceVariant.opacity(0.1))
                            .cornerRadius(8)
                    } else {
                        VStack(alignment: .leading, spacing: 4) {
                            ForEach(Array(result.whoNeedsToSign), id: \.self) { accountId in
                                Text("• \(accountId)")
                                    .font(.system(size: 13, design: .monospaced))
                                    .foregroundStyle(Material3Colors.onSurfaceVariant)
                            }
                        }
                        .padding(12)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .background(Material3Colors.onSurfaceVariant.opacity(0.1))
                        .cornerRadius(8)
                    }
                }
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
                    Text("• The SDK dynamically detected the authorization pattern")
                        .font(.system(size: 13))
                        .foregroundStyle(Material3Colors.onSecondaryContainer)

                    Text("• Handled signing appropriately using needsNonInvokerSigningBy()")
                        .font(.system(size: 13))
                        .foregroundStyle(Material3Colors.onSecondaryContainer)

                    Text("• Conditional signAuthEntries() was applied only when needed")
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

    private func errorCard(_ error: String) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack(spacing: 8) {
                Image(systemName: "exclamationmark.triangle.fill")
                    .foregroundStyle(Material3Colors.onErrorContainer)
                Text("Invocation Failed")
                    .font(.system(size: 16, weight: .bold))
                    .foregroundStyle(Material3Colors.onErrorContainer)
            }

            Text(error)
                .font(.system(size: 14))
                .foregroundStyle(Material3Colors.onErrorContainer)
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
                Text("• Ensure the contract ID is correct and the auth contract is deployed on testnet")
                    .font(.system(size: 13))
                    .foregroundStyle(Material3Colors.onSecondaryContainer)

                Text("• Verify all accounts have sufficient XLM balance for fees")
                    .font(.system(size: 13))
                    .foregroundStyle(Material3Colors.onSecondaryContainer)

                Text("• Check that secret keys match their corresponding account IDs")
                    .font(.system(size: 13))
                    .foregroundStyle(Material3Colors.onSecondaryContainer)

                Text("• Make sure you deployed the auth contract (not another contract)")
                    .font(.system(size: 13))
                    .foregroundStyle(Material3Colors.onSecondaryContainer)

                Text("• For different-invoker scenario, both user and source accounts must be funded")
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
        if invocationResult == nil && errorMessage == nil && !isInvoking && contractId.isEmpty {
            VStack(spacing: 16) {
                Image(systemName: "checkmark.shield")
                    .font(.system(size: 64))
                    .foregroundStyle(Material3Colors.onSurfaceVariant.opacity(0.3))

                Text("Enter contract details to invoke with dynamic authorization")
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

        // Validate user account ID
        if userAccountId.isEmpty {
            errors["userAccount"] = "User account ID is required"
        } else if !userAccountId.hasPrefix("G") {
            errors["userAccount"] = "Account ID must start with 'G'"
        } else if userAccountId.count != 56 {
            errors["userAccount"] = "Account ID must be 56 characters"
        }

        // Validate user secret key
        if userSecretKey.isEmpty {
            errors["userSecretKey"] = "User secret key is required"
        } else if !userSecretKey.hasPrefix("S") {
            errors["userSecretKey"] = "Secret key must start with 'S'"
        } else if userSecretKey.count != 56 {
            errors["userSecretKey"] = "Secret key must be 56 characters"
        }

        // Validate source account (if different-invoker scenario)
        if !useSameAccount {
            if sourceAccountId.isEmpty {
                errors["sourceAccount"] = "Source account ID is required"
            } else if !sourceAccountId.hasPrefix("G") {
                errors["sourceAccount"] = "Account ID must start with 'G'"
            } else if sourceAccountId.count != 56 {
                errors["sourceAccount"] = "Account ID must be 56 characters"
            }

            if sourceSecretKey.isEmpty {
                errors["sourceSecretKey"] = "Source secret key is required"
            } else if !sourceSecretKey.hasPrefix("S") {
                errors["sourceSecretKey"] = "Secret key must start with 'S'"
            } else if sourceSecretKey.count != 56 {
                errors["sourceSecretKey"] = "Secret key must be 56 characters"
            }
        }

        // Validate value
        if value.isEmpty {
            errors["value"] = "Value is required"
        } else if let intValue = Int(value), intValue < 0 {
            errors["value"] = "Value must be non-negative"
        } else if Int(value) == nil {
            errors["value"] = "Value must be a valid integer"
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
        errorMessage = nil
        validationErrors = [:]

        Task {
            do {
                // Determine source account and keypair based on toggle
                let finalSourceAccountId: String
                let finalSourceSecretKey: String

                if useSameAccount {
                    // Same-invoker scenario: user is the source
                    finalSourceAccountId = userAccountId
                    finalSourceSecretKey = userSecretKey
                } else {
                    // Different-invoker scenario: separate source account
                    finalSourceAccountId = sourceAccountId
                    finalSourceSecretKey = sourceSecretKey
                }

                // Create keypairs
                let userKeyPair = try await KeyPair.companion.fromSecretSeed(seed: userSecretKey)
                let sourceKeyPair = try await KeyPair.companion.fromSecretSeed(seed: finalSourceSecretKey)

                // Call the Kotlin function from the shared module
                let result = try await InvokeAuthContractKt.invokeAuthContract(
                    contractId: contractId,
                    userAccountId: userAccountId,
                    userKeyPair: userKeyPair,
                    sourceAccountId: finalSourceAccountId,
                    sourceKeyPair: sourceKeyPair,
                    value: Int32(value) ?? 1
                )

                // Update UI on main thread
                await MainActor.run {
                    if let success = result as? InvokeAuthContractResult.Success {
                        invocationResult = success
                        errorMessage = nil
                    } else if let failure = result as? InvokeAuthContractResult.Failure {
                        errorMessage = failure.message
                        invocationResult = nil
                    }
                    isInvoking = false
                }
            } catch let error as NSError {
                // Handle errors on main thread
                await MainActor.run {
                    invocationResult = nil
                    // Extract error message from Kotlin exception
                    errorMessage = error.localizedDescription
                    isInvoking = false
                }
            }
        }
    }
}
