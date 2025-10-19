import SwiftUI
import shared

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

