import SwiftUI
import shared

// MARK: - Deploy Contract Screen (matches Compose DeployContractScreen)

struct DeployContractView: View {
    @Environment(\.dismiss) var dismiss
    @ObservedObject var toastManager: ToastManager
    @State private var selectedContract: ContractMetadata?
    @State private var sourceAccountId = ""
    @State private var secretKey = ""
    @State private var showSecret = false
    @State private var constructorArgValues: [String: String] = [:]
    @State private var isDeploying = false
    @State private var deploymentResult: DeployContractResult?
    @State private var validationErrors: [String: String] = [:]

    private let bridge = MacOSBridge()
    // Access the AVAILABLE_CONTRACTS list from the shared module
    private var availableContracts: [ContractMetadata] {
        DeployContractKt.AVAILABLE_CONTRACTS.compactMap { $0 as? ContractMetadata }
    }

    var body: some View {
        ScrollView {
            VStack(spacing: 16) {
                infoCard
                contractSelectionCard
                sourceAccountCard

                // Constructor parameters card (conditional)
                if let contract = selectedContract, contract.hasConstructor {
                    constructorParamsCard(for: contract)
                }

                deployButton
                resultView
                placeholderView
            }
            .padding(16)
        }
        .background(Material3Colors.surface)
        .navigationTitle("Deploy a Smart Contract")
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
            Text("ContractClient.deploy(): One-step contract deployment")
                .font(.system(size: 16, weight: .semibold))
                .foregroundStyle(Material3Colors.onSecondaryContainer)

            Text("This demo showcases the SDK's high-level deployment API that handles WASM upload, contract deployment, and constructor invocation in a single call.")
                .font(.system(size: 13))
                .foregroundStyle(Material3Colors.onSecondaryContainer)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(16)
        .background(Material3Colors.secondaryContainer)
        .cornerRadius(12)
    }

    private var contractSelectionCard: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("1. Select Contract")
                .font(.system(size: 14, weight: .bold))
                .foregroundStyle(Material3Colors.onSurface)

            // Contract picker
            Menu {
                ForEach(availableContracts.indices, id: \.self) { index in
                    let contract = availableContracts[index]
                    Button(action: {
                        selectContract(contract)
                    }) {
                        VStack(alignment: .leading) {
                            Text(contract.name)
                            Text(contract.description_)
                                .font(.caption)
                        }
                    }
                }
            } label: {
                HStack {
                    Text(selectedContract?.name ?? "Select a contract")
                        .foregroundStyle(selectedContract == nil ? Material3Colors.onSurfaceVariant : Material3Colors.onSurface)
                    Spacer()
                    Image(systemName: "chevron.down")
                        .foregroundStyle(Material3Colors.onSurfaceVariant)
                }
                .padding(12)
                .background(Color.white)
                .cornerRadius(4)
                .overlay(
                    RoundedRectangle(cornerRadius: 4)
                        .stroke(Material3Colors.onSurfaceVariant.opacity(0.3), lineWidth: 1)
                )
            }
            .buttonStyle(.plain)

            // Selected contract description
            if let contract = selectedContract {
                VStack(alignment: .leading, spacing: 4) {
                    Text(contract.description_)
                        .font(.system(size: 14))
                        .foregroundStyle(Material3Colors.onSurface)

                    if contract.hasConstructor {
                        Spacer().frame(height: 4)
                        Text("Constructor required: \(contract.constructorParams.count) parameter(s)")
                            .font(.system(size: 13, weight: .medium))
                            .foregroundStyle(Material3Colors.onSurfaceVariant)
                    }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(12)
                .background(Material3Colors.primaryContainer.opacity(0.3))
                .cornerRadius(8)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(16)
        .background(Material3Colors.surface)
        .cornerRadius(12)
    }

    private var sourceAccountCard: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("2. Source Account")
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
                            .stroke(validationErrors["sourceAccount"] != nil ? Material3Colors.onErrorContainer : Material3Colors.onSurfaceVariant.opacity(0.3), lineWidth: 1)
                    )
                    .onChange(of: sourceAccountId) { _ in
                        validationErrors.removeValue(forKey: "sourceAccount")
                        deploymentResult = nil
                    }

                if let error = validationErrors["sourceAccount"] {
                    Text(error)
                        .font(.system(size: 12))
                        .foregroundStyle(Material3Colors.onErrorContainer)
                }
            }

            // Secret Key
            VStack(alignment: .leading, spacing: 4) {
                HStack {
                    Text("Secret Key")
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
                    TextField("S...", text: $secretKey)
                        .textFieldStyle(.plain)
                        .font(.system(.body, design: .monospaced))
                        .padding(12)
                        .background(Color.white)
                        .overlay(
                            RoundedRectangle(cornerRadius: 4)
                                .stroke(validationErrors["secretKey"] != nil ? Material3Colors.onErrorContainer : Material3Colors.onSurfaceVariant.opacity(0.3), lineWidth: 1)
                        )
                        .onChange(of: secretKey) { _ in
                            validationErrors.removeValue(forKey: "secretKey")
                            deploymentResult = nil
                        }
                } else {
                    SecureField("S...", text: $secretKey)
                        .textFieldStyle(.plain)
                        .font(.system(.body, design: .monospaced))
                        .padding(12)
                        .background(Color.white)
                        .overlay(
                            RoundedRectangle(cornerRadius: 4)
                                .stroke(validationErrors["secretKey"] != nil ? Material3Colors.onErrorContainer : Material3Colors.onSurfaceVariant.opacity(0.3), lineWidth: 1)
                        )
                        .onChange(of: secretKey) { _ in
                            validationErrors.removeValue(forKey: "secretKey")
                            deploymentResult = nil
                        }
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
        .background(Material3Colors.surface)
        .cornerRadius(12)
    }

    private func constructorParamsCard(for contract: ContractMetadata) -> some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("3. Constructor Parameters")
                .font(.system(size: 14, weight: .bold))
                .foregroundStyle(Material3Colors.onSurface)

            ForEach(0..<contract.constructorParams.count, id: \.self) { index in
                if let param = contract.constructorParams[index] as? ConstructorParam {
                    constructorParamField(param: param)
                }
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(16)
        .background(Material3Colors.surface)
        .cornerRadius(12)
    }

    private func constructorParamField(param: ConstructorParam) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(param.name)
                .font(.system(size: 12, weight: .medium))
                .foregroundStyle(Material3Colors.onSurfaceVariant)

            TextField(param.placeholder, text: Binding(
                get: { constructorArgValues[param.name] ?? "" },
                set: { newValue in
                    constructorArgValues[param.name] = newValue
                    validationErrors.removeValue(forKey: "constructor_\(param.name)")
                    deploymentResult = nil
                }
            ))
            .textFieldStyle(.plain)
            .font(.system(.body, design: .monospaced))
            .padding(12)
            .background(Color.white)
            .overlay(
                RoundedRectangle(cornerRadius: 4)
                    .stroke(validationErrors["constructor_\(param.name)"] != nil ? Material3Colors.onErrorContainer : Material3Colors.onSurfaceVariant.opacity(0.3), lineWidth: 1)
            )

            if let error = validationErrors["constructor_\(param.name)"] {
                Text(error)
                    .font(.system(size: 12))
                    .foregroundStyle(Material3Colors.onErrorContainer)
            } else {
                Text(param.description_)
                    .font(.system(size: 12))
                    .foregroundStyle(Material3Colors.onSurfaceVariant)
            }
        }
    }

    private var deployButton: some View {
        Button(action: deployContract) {
            HStack(spacing: 8) {
                if isDeploying {
                    ProgressView()
                        .controlSize(.small)
                        .tint(.white)
                    Text("Deploying...")
                } else {
                    Image(systemName: "arrow.up.doc.fill")
                    Text("Deploy Contract")
                }
            }
            .frame(maxWidth: .infinity)
            .frame(height: 56)
            .background(isFormValid && !isDeploying ? Material3Colors.primary : Material3Colors.primary.opacity(0.6))
            .foregroundColor(.white)
            .cornerRadius(12)
        }
        .disabled(!isFormValid || isDeploying)
        .buttonStyle(.plain)
    }

    @ViewBuilder
    private var resultView: some View {
        if let result = deploymentResult {
            switch result {
            case let success as DeployContractResult.Success:
                successCard(success)
            case let error as DeployContractResult.Error:
                errorCard(error)
                troubleshootingCard
            default:
                EmptyView()
            }
        }
    }

    private func successCard(_ success: DeployContractResult.Success) -> some View {
        VStack(spacing: 16) {
            // Success header card
            VStack(alignment: .leading, spacing: 8) {
                HStack(spacing: 8) {
                    Image(systemName: "checkmark.circle.fill")
                        .font(.system(size: 24))
                        .foregroundStyle(Material3Colors.onSuccessContainer)

                    Text("Deployment Successful")
                        .font(.system(size: 16, weight: .bold))
                        .foregroundStyle(Material3Colors.onSuccessContainer)
                }

                Text("Your smart contract has been deployed to the testnet")
                    .font(.system(size: 14))
                    .foregroundStyle(Material3Colors.onSuccessContainer)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(16)
            .background(Material3Colors.successContainer)
            .cornerRadius(12)
            // Contract Details Card
            VStack(alignment: .leading, spacing: 12) {
                Text("Contract Details")
                    .font(.system(size: 14, weight: .bold))
                    .foregroundStyle(Material3Colors.onSurfaceVariant)

                Divider()

                DeployContractCopyableRow(label: "Contract ID", value: success.contractId)

                if let wasmId = success.wasmId {
                    DeployContractCopyableRow(label: "WASM ID", value: wasmId)
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
                    Text("• You can now use this contract ID to interact with your deployed contract")
                        .font(.system(size: 13))
                        .foregroundStyle(Material3Colors.onSecondaryContainer)

                    Text("• Use ContractClient.fromNetwork() to create a client instance")
                        .font(.system(size: 13))
                        .foregroundStyle(Material3Colors.onSecondaryContainer)

                    Text("• Try the 'Invoke Hello World Contract' or 'Invoke Auth Contract' demos")
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

    private func errorCard(_ error: DeployContractResult.Error) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Deployment Failed")
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
                Text("• Verify the source account has sufficient XLM balance (at least 100 XLM recommended)")
                    .font(.system(size: 13))
                    .foregroundStyle(Material3Colors.onSecondaryContainer)

                Text("• Ensure the source account exists on testnet (use 'Fund Testnet Account' first)")
                    .font(.system(size: 13))
                    .foregroundStyle(Material3Colors.onSecondaryContainer)

                Text("• Check that the secret key matches the source account ID")
                    .font(.system(size: 13))
                    .foregroundStyle(Material3Colors.onSecondaryContainer)

                Text("• Verify constructor arguments match the expected types")
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
        if selectedContract == nil && deploymentResult == nil && !isDeploying {
            Spacer()
                .frame(height: 16)

            Image(systemName: "arrow.up.doc")
                .font(.system(size: 64))
                .foregroundStyle(Material3Colors.onSurfaceVariant.opacity(0.3))

            Text("Select a demo contract to begin deployment")
                .font(.system(size: 14))
                .foregroundStyle(Material3Colors.onSurfaceVariant)
                .multilineTextAlignment(.center)
        }
    }

    // MARK: - Computed Properties

    private var isFormValid: Bool {
        guard let contract = selectedContract else { return false }

        if sourceAccountId.isEmpty || secretKey.isEmpty {
            return false
        }

        if contract.hasConstructor {
            for i in 0..<contract.constructorParams.count {
                if let param = contract.constructorParams[i] as? ConstructorParam {
                    if (constructorArgValues[param.name] ?? "").isEmpty {
                        return false
                    }
                }
            }
        }

        return true
    }

    // MARK: - Actions

    private func selectContract(_ contract: ContractMetadata) {
        selectedContract = contract

        // Reset constructor args when changing contract
        constructorArgValues = [:]
        if contract.hasConstructor {
            for i in 0..<contract.constructorParams.count {
                if let param = contract.constructorParams[i] as? ConstructorParam {
                    constructorArgValues[param.name] = ""
                }
            }
        }
    }

    private func deployContract() {
        let errors = validateInputs()
        if !errors.isEmpty {
            validationErrors = errors
            return
        }

        guard let contract = selectedContract else {
            toastManager.show("Please select a contract to deploy")
            return
        }

        isDeploying = true
        deploymentResult = nil
        validationErrors = [:]

        Task {
            do {
                // Build constructor arguments map with proper types
                var constructorArgs: [String: Any] = [:]
                if contract.hasConstructor {
                    for i in 0..<contract.constructorParams.count {
                        if let param = contract.constructorParams[i] as? ConstructorParam {
                            let value = constructorArgValues[param.name] ?? ""

                            // Convert based on type
                            switch param.type {
                            case .address, .string:
                                constructorArgs[param.name] = value
                            case .u32:
                                if let intValue = Int32(value) {
                                    constructorArgs[param.name] = intValue
                                }
                            default:
                                constructorArgs[param.name] = value
                            }
                        }
                    }
                }

                // Call the shared business logic
                let result = try await bridge.deployContract(
                    contractMetadata: contract,
                    constructorArgs: constructorArgs,
                    sourceAccountId: sourceAccountId,
                    secretKey: secretKey,
                )

                await MainActor.run {
                    deploymentResult = result
                    isDeploying = false
                }
            } catch {
                await MainActor.run {
                    deploymentResult = DeployContractResult.Error(
                        message: "Failed to deploy contract: \(error.localizedDescription)",
                        exception: error as? KotlinThrowable
                    )
                    isDeploying = false
                }
            }
        }
    }

    private func validateInputs() -> [String: String] {
        var errors: [String: String] = [:]

        // Validate source account
        if sourceAccountId.isEmpty {
            errors["sourceAccount"] = "Source account ID is required"
        } else if !sourceAccountId.hasPrefix("G") {
            errors["sourceAccount"] = "Account ID must start with 'G'"
        } else if sourceAccountId.count != 56 {
            errors["sourceAccount"] = "Account ID must be 56 characters"
        }

        // Validate secret key
        if secretKey.isEmpty {
            errors["secretKey"] = "Secret key is required"
        } else if !secretKey.hasPrefix("S") {
            errors["secretKey"] = "Secret key must start with 'S'"
        } else if secretKey.count != 56 {
            errors["secretKey"] = "Secret key must be 56 characters"
        }

        // Validate constructor arguments
        if let contract = selectedContract, contract.hasConstructor {
            for i in 0..<contract.constructorParams.count {
                if let param = contract.constructorParams[i] as? ConstructorParam {
                    let value = constructorArgValues[param.name] ?? ""

                    if value.isEmpty {
                        errors["constructor_\(param.name)"] = "\(param.name) is required"
                    } else {
                        // Type-specific validation
                        switch param.type {
                        case .address:
                            if !value.hasPrefix("G") || value.count != 56 {
                                errors["constructor_\(param.name)"] = "Must be a valid address (G...)"
                            }
                        case .u32:
                            if Int32(value) == nil {
                                errors["constructor_\(param.name)"] = "Must be a valid number"
                            }
                        case .string:
                            break // No additional validation for strings
                        default:
                            break
                        }
                    }
                }
            }
        }

        return errors
    }

    private func copyToClipboard(_ text: String) {
        let pasteboard = NSPasteboard.general
        pasteboard.clearContents()
        pasteboard.setString(text, forType: .string)
    }
}

// MARK: - Deploy Contract Copyable Row

struct DeployContractCopyableRow: View {
    let label: String
    let value: String

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(label)
                .font(.system(size: 12, weight: .medium))
                .foregroundStyle(Material3Colors.onSurfaceVariant.opacity(0.7))

            HStack(alignment: .top) {
                Text(value)
                    .font(.system(.body, design: .monospaced))
                    .foregroundStyle(Material3Colors.onSurfaceVariant)
                    .textSelection(.enabled)
                    .lineLimit(nil)
                    .fixedSize(horizontal: false, vertical: true)

                Spacer()

                Button(action: {
                    NSPasteboard.general.clearContents()
                    NSPasteboard.general.setString(value, forType: .string)
                }) {
                    Image(systemName: "doc.on.doc")
                        .font(.system(size: 10))
                        .foregroundStyle(Material3Colors.primary)
                }
                .buttonStyle(.plain)
                .help("Copy to clipboard")
            }
        }
    }
}
