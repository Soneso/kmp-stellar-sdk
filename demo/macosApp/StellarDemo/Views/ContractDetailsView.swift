import SwiftUI
import shared

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
                let result = try await bridge.fetchContractDetails(contractId: contractId)
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
