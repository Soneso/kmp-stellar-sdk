import SwiftUI
import shared

struct FetchTransactionScreen: View {
    @Environment(\.dismiss) var dismiss
    @ObservedObject var toastManager: ToastManager
    @State private var transactionHash = "639cce30b6c166224010e31abf4ae60468d3480bfc5140eb2abf0eea3e4edf99"
    @State private var selectedAPI: APISelection = .horizon
    @State private var isFetching = false
    @State private var fetchResult: FetchTransactionResult?
    @State private var validationError: String?

    enum APISelection: String, CaseIterable {
        case horizon = "Horizon API"
        case rpc = "Soroban RPC"

        var description: String {
            switch self {
            case .horizon:
                return "REST API for all transactions"
            case .rpc:
                return "RPC API for smart contracts"
            }
        }

        var iconName: String {
            switch self {
            case .horizon:
                return "globe"
            case .rpc:
                return "bolt.fill"
            }
        }
    }

    var body: some View {
        ScrollView {
            VStack(spacing: 16) {
                infoCard
                apiSelectionCard
                inputField
                apiPicker
                fetchButton
                resultView
                placeholderView
            }
            .padding(16)
        }
        .background(Material3Colors.surface)
        .navigationTitle("Fetch Transaction Details")
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
            Text("Fetch transaction details from Horizon or Soroban RPC")
                .font(.system(size: 16, weight: .semibold))
                .foregroundStyle(Material3Colors.onSecondaryContainer)

            Text("Enter a transaction hash to retrieve comprehensive transaction information. Choose between Horizon API (for general transactions) or Soroban RPC (for smart contract transactions).")
                .font(.system(size: 13))
                .foregroundStyle(Material3Colors.onSecondaryContainer)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(16)
        .background(Material3Colors.secondaryContainer)
        .cornerRadius(12)
    }

    private var apiSelectionCard: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("API Selection")
                .font(.system(size: 14, weight: .bold))
                .foregroundStyle(Material3Colors.onPrimaryContainer)

            VStack(alignment: .leading, spacing: 4) {
                Text("Horizon: Complete transaction history with human-readable data")
                    .font(.system(size: 13))
                    .foregroundStyle(Material3Colors.onPrimaryContainer)

                Text("Soroban RPC: Smart contract transactions with return values and events")
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
        VStack(alignment: .leading, spacing: 8) {
            Text("This is a demo transaction hash. You can replace it with your own transaction hash to fetch different transaction details.")
                .font(.system(size: 12, weight: .medium))
                .foregroundStyle(Material3Colors.onSurfaceVariant.opacity(0.7))
                .fixedSize(horizontal: false, vertical: true)

            VStack(alignment: .leading, spacing: 4) {
                Text("Transaction Hash")
                    .font(.system(size: 12, weight: .medium))
                    .foregroundStyle(Material3Colors.onSurfaceVariant)

                TextField("64-character hex string", text: $transactionHash)
                    .textFieldStyle(.plain)
                    .font(.system(.body, design: .monospaced))
                    .padding(12)
                    .background(Color.white)
                    .overlay(
                        RoundedRectangle(cornerRadius: 4)
                            .stroke(validationError != nil ? Material3Colors.onErrorContainer : Material3Colors.onSurfaceVariant.opacity(0.3), lineWidth: 1)
                    )
                    .onChange(of: transactionHash) { newValue in
                        transactionHash = newValue.trimmingCharacters(in: .whitespaces).lowercased()
                        validationError = nil
                        fetchResult = nil
                    }

                if let error = validationError {
                    Text(error)
                        .font(.system(size: 12))
                        .foregroundStyle(Material3Colors.onErrorContainer)
                }
            }
        }
    }

    private var apiPicker: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Select API Endpoint")
                .font(.system(size: 14, weight: .semibold))
                .foregroundStyle(Material3Colors.onSurface)

            Picker("API Selection", selection: $selectedAPI) {
                ForEach(APISelection.allCases, id: \.self) { api in
                    Text(api.rawValue)
                        .tag(api)
                }
            }
            .pickerStyle(.segmented)
            .onChange(of: selectedAPI) { _ in
                fetchResult = nil
            }

            HStack(spacing: 12) {
                Image(systemName: selectedAPI.iconName)
                    .font(.system(size: 16))
                    .foregroundStyle(Material3Colors.primary)
                    .frame(width: 24)

                VStack(alignment: .leading, spacing: 4) {
                    Text(selectedAPI.rawValue)
                        .font(.system(size: 14, weight: .semibold))
                        .foregroundStyle(Material3Colors.onSurface)

                    Text(selectedAPI.description)
                        .font(.system(size: 12))
                        .foregroundStyle(Material3Colors.onSurfaceVariant)
                }

                Spacer()
            }
            .padding(12)
            .background(Material3Colors.primaryContainer.opacity(0.3))
            .cornerRadius(8)
        }
        .padding(16)
        .background(Color.white)
        .cornerRadius(12)
    }

    private var fetchButton: some View {
        Button(action: fetchTransaction) {
            HStack(spacing: 8) {
                if isFetching {
                    ProgressView()
                        .controlSize(.small)
                        .tint(.white)
                    Text("Fetching...")
                } else {
                    Image(systemName: "magnifyingglass")
                    Text("Fetch Transaction")
                }
            }
            .frame(maxWidth: .infinity)
            .frame(height: 56)
            .background((isFetching || transactionHash.isEmpty) ? Material3Colors.primary.opacity(0.6) : Material3Colors.primary)
            .foregroundColor(.white)
            .cornerRadius(12)
        }
        .disabled(isFetching || transactionHash.isEmpty)
        .buttonStyle(.plain)
    }

    @ViewBuilder
    private var resultView: some View {
        if let result = fetchResult {
            switch result {
            case let success as FetchTransactionResult.HorizonSuccess:
                HorizonTransactionView(transaction: success.transaction, operations: success.operations)
            case let success as FetchTransactionResult.RpcSuccess:
                RpcTransactionView(transaction: success.transaction)
            case let error as FetchTransactionResult.Error:
                errorCard(error)
                troubleshootingCard
            default:
                EmptyView()
            }
        }
    }

    private func errorCard(_ error: FetchTransactionResult.Error) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Error")
                .font(.system(size: 16, weight: .semibold))
                .foregroundStyle(Material3Colors.onErrorContainer)

            Text(error.message)
                .font(.system(size: 14))
                .foregroundStyle(Material3Colors.onErrorContainer)
                .textSelection(.enabled)

            if let exception = error.exception {
                Text("Technical details: \(exception.message ?? "Unknown error")")
                    .font(.system(size: 13, design: .monospaced))
                    .foregroundStyle(Material3Colors.onErrorContainer)
                    .textSelection(.enabled)
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
                Text("• Verify the transaction hash is correct (64 hex characters)")
                    .font(.system(size: 13))
                    .foregroundStyle(Material3Colors.onSecondaryContainer)

                Text("• Make sure you're using the correct API (Horizon vs Soroban RPC)")
                    .font(.system(size: 13))
                    .foregroundStyle(Material3Colors.onSecondaryContainer)

                Text("• Confirm the transaction exists on testnet")
                    .font(.system(size: 13))
                    .foregroundStyle(Material3Colors.onSecondaryContainer)

                Text("• Check if the transaction is outside the retention window (RPC only)")
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
        if fetchResult == nil && !isFetching && transactionHash.isEmpty {
            Spacer()
                .frame(height: 16)

            Image(systemName: "doc.text.magnifyingglass")
                .font(.system(size: 64))
                .foregroundStyle(Material3Colors.onSurfaceVariant.opacity(0.3))

            Text("Enter a transaction hash to view its details")
                .font(.system(size: 14))
                .foregroundStyle(Material3Colors.onSurfaceVariant)
                .multilineTextAlignment(.center)
        }
    }

    // MARK: - Actions

    private func validateTransactionHash() -> String? {
        if transactionHash.isEmpty {
            return "Transaction hash is required"
        }
        if transactionHash.count != 64 {
            return "Transaction hash must be 64 characters long"
        }
        if !transactionHash.matches(of: /^[0-9a-fA-F]{64}$/).isEmpty == false {
            return "Transaction hash must be a valid hexadecimal string"
        }
        return nil
    }

    private func fetchTransaction() {
        let error = validateTransactionHash()
        if let error = error {
            validationError = error
            return
        }

        isFetching = true
        fetchResult = nil
        validationError = nil

        Task {
            do {
                let result: FetchTransactionResult
                let useHorizon = selectedAPI == .horizon

                if useHorizon {
                    result = try await FetchTransactionKt.fetchTransactionFromHorizon(transactionHash: transactionHash, useTestnet: true)
                } else {
                    result = try await FetchTransactionKt.fetchTransactionFromRpc(transactionHash: transactionHash, useTestnet: true)
                }
                await MainActor.run {
                    fetchResult = result
                    isFetching = false
                }
            } catch {
                await MainActor.run {
                    fetchResult = FetchTransactionResult.Error(
                        message: "Failed to fetch transaction: \(error.localizedDescription)",
                        exception: error as? KotlinThrowable
                    )
                    isFetching = false
                }
            }
        }
    }
}

// MARK: - Horizon Transaction Display

struct HorizonTransactionView: View {
    let transaction: TransactionResponse
    let operations: [OperationResponse]

    var body: some View {
        VStack(spacing: 16) {
            // Success header
            VStack(alignment: .leading, spacing: 8) {
                Text(transaction.successful ? "Transaction Successful" : "Transaction Failed")
                    .font(.system(size: 16, weight: .bold))
                    .foregroundStyle(transaction.successful ? Material3Colors.onSuccessContainer : Material3Colors.onErrorContainer)

                Text("Fetched from Horizon API")
                    .font(.system(size: 14))
                    .foregroundStyle(transaction.successful ? Material3Colors.onSuccessContainer : Material3Colors.onErrorContainer)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(16)
            .background(transaction.successful ? Material3Colors.successContainer : Material3Colors.errorContainer)
            .cornerRadius(12)

            // Basic Information
            let basicItems = [
                ("Hash", transaction.hash),
                ("ID", transaction.id),
                ("Successful", transaction.successful ? "Yes" : "No"),
                ("Ledger", "\(transaction.ledger)"),
                ("Created At", transaction.createdAt),
                ("Paging Token", transaction.pagingToken)
            ]
            TransactionInfoCard(title: "Basic Information", items: basicItems)

            // Account and Fees
            let accountItems = buildAccountItems()
            TransactionInfoCard(title: "Account and Fees", items: accountItems)

            // Operations
            if !operations.isEmpty {
                OperationsCard(operations: operations)
            }

            // Memo
            let memoItems = buildMemoItems()
            if !memoItems.isEmpty {
                TransactionInfoCard(title: "Memo", items: memoItems)
            }

            // Signatures
            let signatureItems = buildSignatureItems()
            TransactionInfoCard(title: "Signatures (\(transaction.signatures.count))", items: signatureItems)

            // XDR Data
            let xdrItems = buildXdrItems()
            if !xdrItems.isEmpty {
                TransactionInfoCard(title: "XDR Data", items: xdrItems)
            }
        }
    }

    private func buildAccountItems() -> [(String, String)] {
        var items = [("Source Account", transaction.sourceAccount)]

        if let muxed = transaction.accountMuxed {
            items.append(("Account Muxed", muxed))
        }
        if let muxedId = transaction.accountMuxedId {
            items.append(("Account Muxed ID", muxedId))
        }

        items.append(contentsOf: [
            ("Source Sequence", "\(transaction.sourceAccountSequence)"),
            ("Fee Account", transaction.feeAccount),
            ("Fee Charged", "\(transaction.feeCharged) stroops (\(Double(transaction.feeCharged) / 10_000_000.0) XLM)"),
            ("Max Fee", "\(transaction.maxFee) stroops (\(Double(transaction.maxFee) / 10_000_000.0) XLM)")
        ])

        return items
    }

    private func buildMemoItems() -> [(String, String)] {
        var items = [("Memo Type", transaction.memoType)]

        let memoValue = transaction.memoValue ?? transaction.memoBytes ?? ""
        if !memoValue.isEmpty {
            items.append(("Memo Value", memoValue))
        }

        return items.filter { !$0.1.isEmpty }
    }

    private func buildSignatureItems() -> [(String, String)] {
        if transaction.signatures.isEmpty {
            return [("Status", "No signatures")]
        }

        return transaction.signatures.enumerated().map { index, sig in
            ("Signature \(index + 1)", sig)
        }
    }

    private func buildXdrItems() -> [(String, String)] {
        var items: [(String, String)] = []

        if let xdr = transaction.envelopeXdr {
            items.append(("Envelope XDR", xdr))
        }
        if let xdr = transaction.resultXdr {
            items.append(("Result XDR", xdr))
        }
        if let xdr = transaction.resultMetaXdr {
            items.append(("Result Meta XDR", xdr))
        }
        if let xdr = transaction.feeMetaXdr {
            items.append(("Fee Meta XDR", xdr))
        }

        return items
    }
}

// MARK: - RPC Transaction Display

struct RpcTransactionView: View {
    let transaction: GetTransactionResponse

    var body: some View {
        VStack(spacing: 16) {
            // Success header
            let isSuccess = transaction.status == GetTransactionStatus.success
            VStack(alignment: .leading, spacing: 8) {
                Text(statusText)
                    .font(.system(size: 16, weight: .bold))
                    .foregroundStyle(isSuccess ? Material3Colors.onSuccessContainer : Material3Colors.onErrorContainer)

                Text("Fetched from Soroban RPC")
                    .font(.system(size: 14))
                    .foregroundStyle(isSuccess ? Material3Colors.onSuccessContainer : Material3Colors.onErrorContainer)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(16)
            .background(isSuccess ? Material3Colors.successContainer : Material3Colors.errorContainer)
            .cornerRadius(12)

            // Transaction Information
            let txItems = buildTransactionItems()
            TransactionInfoCard(title: "Transaction Information", items: txItems)

            // Ledger Window
            let ledgerItems = buildLedgerItems()
            if !ledgerItems.isEmpty {
                TransactionInfoCard(title: "Ledger Window", items: ledgerItems)
            }

            // XDR Data
            let xdrItems = buildXdrItems()
            if !xdrItems.isEmpty {
                TransactionInfoCard(title: "XDR Data", items: xdrItems)
            }

            // Events
            if let events = transaction.events {
                EventsCard(events: events, ledger: transaction.ledger?.int64Value)
            }

            // Return Value
            if let returnValue = transaction.getResultValue() {
                ReturnValueCard(scVal: returnValue)
            }
        }
    }

    private var statusText: String {
        switch transaction.status {
        case .success:
            return "Transaction Successful"
        case .failed:
            return "Transaction Failed"
        case .notFound:
            return "Transaction Not Found"
        default:
            return "Unknown Status"
        }
    }

    private func buildTransactionItems() -> [(String, String)] {
        var items = [("Status", transaction.status.name)]

        if let hash = transaction.txHash {
            items.append(("Transaction Hash", hash))
        }
        if let ledger = transaction.ledger {
            items.append(("Ledger", "\(ledger.int64Value)"))
        }
        if let createdAt = transaction.createdAt {
            items.append(("Created At", "\(createdAt.int64Value)"))
        }
        if let order = transaction.applicationOrder {
            items.append(("Application Order", "\(order.int32Value)"))
        }
        if let feeBump = transaction.feeBump {
            items.append(("Fee Bump", feeBump.boolValue ? "Yes" : "No"))
        }

        return items
    }

    private func buildLedgerItems() -> [(String, String)] {
        var items: [(String, String)] = []

        if let latest = transaction.latestLedger {
            items.append(("Latest Ledger", "\(latest.int64Value)"))
        }
        if let time = transaction.latestLedgerCloseTime {
            items.append(("Latest Ledger Close", "\(time.int64Value)"))
        }
        if let oldest = transaction.oldestLedger {
            items.append(("Oldest Ledger", "\(oldest.int64Value)"))
        }
        if let time = transaction.oldestLedgerCloseTime {
            items.append(("Oldest Ledger Close", "\(time.int64Value)"))
        }

        return items
    }

    private func buildXdrItems() -> [(String, String)] {
        var items: [(String, String)] = []

        if let xdr = transaction.envelopeXdr {
            items.append(("Envelope XDR", xdr))
        }
        if let xdr = transaction.resultXdr {
            items.append(("Result XDR", xdr))
        }
        if let xdr = transaction.resultMetaXdr {
            items.append(("Result Meta XDR", xdr))
        }

        return items
    }
}

// MARK: - Helper Components

struct TransactionInfoCard: View {
    let title: String
    let items: [(String, String)]

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text(title)
                .font(.system(size: 14, weight: .bold))
                .foregroundStyle(Material3Colors.onSurfaceVariant)

            Divider()

            ForEach(items.indices, id: \.self) { index in
                CopyableDetailRow(label: items[index].0, value: items[index].1)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(16)
        .background(Material3Colors.surfaceVariant)
        .cornerRadius(12)
    }
}

struct OperationsCard: View {
    let operations: [OperationResponse]
    @State private var expandedIndices: Set<Int> = []

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Operations (\(operations.count))")
                .font(.system(size: 14, weight: .bold))
                .foregroundStyle(Material3Colors.onSurfaceVariant)

            Text("Click on an operation to view details")
                .font(.system(size: 12))
                .foregroundStyle(Material3Colors.onSurfaceVariant.opacity(0.7))

            Divider()

            ForEach(operations.indices, id: \.self) { index in
                let operation = operations[index]
                let isExpanded = expandedIndices.contains(index)

                VStack(alignment: .leading, spacing: 8) {
                    Button(action: {
                        if isExpanded {
                            expandedIndices.remove(index)
                        } else {
                            expandedIndices.insert(index)
                        }
                    }) {
                        HStack {
                            VStack(alignment: .leading, spacing: 4) {
                                Text("Operation \(index + 1): \(operation.type.replacingOccurrences(of: "_", with: " ").uppercased())")
                                    .font(.system(size: 13, weight: .bold))
                                    .foregroundStyle(Material3Colors.onSurface)
                            }

                            Spacer()

                            Image(systemName: isExpanded ? "chevron.up" : "chevron.down")
                                .foregroundStyle(Material3Colors.onSurfaceVariant)
                        }
                    }
                    .buttonStyle(.plain)

                    // Operation ID - always visible with full text
                    CopyableDetailRow(label: "Operation ID", value: operation.id)

                    if isExpanded {
                        Divider()
                        OperationDetails(operation: operation)
                    }
                }
                .padding(12)
                .background(Color.white)
                .cornerRadius(8)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(16)
        .background(Material3Colors.primaryContainer.opacity(0.3))
        .cornerRadius(12)
    }
}

struct OperationDetails: View {
    let operation: OperationResponse

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            // Common fields
            CopyableDetailRow(label: "Source Account", value: operation.sourceAccount)
            if let muxed = operation.sourceAccountMuxed {
                CopyableDetailRow(label: "Source Muxed", value: muxed)
            }
            CopyableDetailRow(label: "Transaction Hash", value: operation.transactionHash)
            InfoRow(label: "Created At", value: operation.createdAt)
            InfoRow(label: "Transaction Successful", value: operation.transactionSuccessful ? "Yes" : "No")

            Divider()
            Text("Operation-Specific Data")
                .font(.system(size: 12, weight: .bold))
                .padding(.vertical, 4)

            // Operation-specific fields
            if let payment = operation as? PaymentOperationResponse {
                CopyableDetailRow(label: "From", value: payment.from)
                CopyableDetailRow(label: "To", value: payment.to)
                InfoRow(label: "Amount", value: payment.amount)
                InfoRow(label: "Asset Type", value: payment.assetType)
                if let code = payment.assetCode {
                    InfoRow(label: "Asset Code", value: code)
                }
                if let issuer = payment.assetIssuer {
                    CopyableDetailRow(label: "Asset Issuer", value: issuer)
                }
            } else if let createAccount = operation as? CreateAccountOperationResponse {
                CopyableDetailRow(label: "Funder", value: createAccount.funder)
                CopyableDetailRow(label: "Account", value: createAccount.account)
                InfoRow(label: "Starting Balance", value: "\(createAccount.startingBalance) XLM")
            } else if let changeTrust = operation as? ChangeTrustOperationResponse {
                CopyableDetailRow(label: "Trustor", value: changeTrust.trustor)
                if let trustee = changeTrust.trustee {
                    CopyableDetailRow(label: "Trustee", value: trustee)
                }
                InfoRow(label: "Asset Type", value: changeTrust.assetType)
                if let code = changeTrust.assetCode {
                    InfoRow(label: "Asset Code", value: code)
                }
                if let issuer = changeTrust.assetIssuer {
                    CopyableDetailRow(label: "Asset Issuer", value: issuer)
                }
                InfoRow(label: "Limit", value: changeTrust.limit)
                if let poolId = changeTrust.liquidityPoolId {
                    CopyableDetailRow(label: "Liquidity Pool ID", value: poolId)
                }
            } else if let manageSell = operation as? ManageSellOfferOperationResponse {
                InfoRow(label: "Offer ID", value: "\(manageSell.offerId)")
                InfoRow(label: "Amount", value: manageSell.amount)
                InfoRow(label: "Price", value: manageSell.price)
                InfoRow(label: "Selling Asset", value: manageSell.sellingAssetType)
                if let code = manageSell.sellingAssetCode {
                    InfoRow(label: "Selling Code", value: code)
                }
                if let issuer = manageSell.sellingAssetIssuer {
                    CopyableDetailRow(label: "Selling Issuer", value: issuer)
                }
                InfoRow(label: "Buying Asset", value: manageSell.buyingAssetType)
                if let code = manageSell.buyingAssetCode {
                    InfoRow(label: "Buying Code", value: code)
                }
                if let issuer = manageSell.buyingAssetIssuer {
                    CopyableDetailRow(label: "Buying Issuer", value: issuer)
                }
            } else if let invokeHost = operation as? InvokeHostFunctionOperationResponse {
                InfoRow(label: "Function", value: invokeHost.function)
                if let address = invokeHost.address {
                    CopyableDetailRow(label: "Address", value: address)
                }
                if let params = invokeHost.parameters {
                    Text("Parameters (\(params.count)):")
                        .font(.system(size: 11, weight: .bold))
                    ForEach(Array(params.enumerated()), id: \.offset) { idx, param in
                        InfoRow(label: "  Param \(idx + 1) (\(param.type))", value: param.value)
                    }
                }
            } else {
                InfoRow(label: "Operation Type", value: operation.type)
            }
        }
        .padding(.leading, 12)
    }
}

struct EventsCard: View {
    let events: Events
    let ledger: Int64?
    @State private var expandedIndices: Set<Int> = []

    var body: some View {
        // Parse all events
        let allEvents = parseAllEvents()

        if allEvents.isEmpty {
            // Debug card if no events
            VStack(alignment: .leading, spacing: 12) {
                Text("Events Debug Information")
                    .font(.system(size: 14, weight: .bold))
                    .foregroundStyle(Material3Colors.onSurfaceVariant)

                Divider()

                Text("Events object: present")
                    .font(.system(size: 12))
                    .foregroundStyle(Material3Colors.onSurfaceVariant)
                    .textSelection(.enabled)

                if let diagnostic = events.parseDiagnosticEventsXdr() {
                    Text("Diagnostic events: \(diagnostic.count)")
                        .font(.system(size: 12))
                        .foregroundStyle(Material3Colors.onSurfaceVariant)
                        .textSelection(.enabled)
                }
                if let transaction = events.parseTransactionEventsXdr() {
                    Text("Transaction events: \(transaction.count)")
                        .font(.system(size: 12))
                        .foregroundStyle(Material3Colors.onSurfaceVariant)
                        .textSelection(.enabled)
                }
                if let contract = events.parseContractEventsXdr() {
                    let total = contract.map { $0.count }.reduce(0, +)
                    Text("Contract events: \(total)")
                        .font(.system(size: 12))
                        .foregroundStyle(Material3Colors.onSurfaceVariant)
                        .textSelection(.enabled)
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(16)
            .background(Material3Colors.errorContainer.opacity(0.3))
            .cornerRadius(12)
        } else {
            VStack(alignment: .leading, spacing: 12) {
                Text("Contract Events (\(allEvents.count))")
                    .font(.system(size: 14, weight: .bold))
                    .foregroundStyle(Material3Colors.onSurfaceVariant)

                Text("Click on an event to view details")
                    .font(.system(size: 12))
                    .foregroundStyle(Material3Colors.onSurfaceVariant.opacity(0.7))

                Divider()

                ForEach(allEvents.indices, id: \.self) { index in
                    let eventItem = allEvents[index]
                    let isExpanded = expandedIndices.contains(index)

                    VStack(alignment: .leading, spacing: 8) {
                        Button(action: {
                            if isExpanded {
                                expandedIndices.remove(index)
                            } else {
                                expandedIndices.insert(index)
                            }
                        }) {
                            HStack {
                                VStack(alignment: .leading, spacing: 4) {
                                    Text(eventItem.title)
                                        .font(.system(size: 13, weight: .bold))
                                        .foregroundStyle(Material3Colors.onSurface)

                                    Text(eventItem.subtitle)
                                        .font(.system(size: 11))
                                        .foregroundStyle(Material3Colors.onSurfaceVariant.opacity(0.7))
                                }

                                Spacer()

                                Image(systemName: isExpanded ? "chevron.up" : "chevron.down")
                                    .foregroundStyle(Material3Colors.onSurfaceVariant)
                            }
                        }
                        .buttonStyle(.plain)

                        if isExpanded {
                            Divider()
                            EventDetails(event: eventItem.event, ledger: ledger)
                        }
                    }
                    .padding(12)
                    .background(Color.white)
                    .cornerRadius(8)
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(16)
            .background(Material3Colors.primaryContainer.opacity(0.3))
            .cornerRadius(12)
        }
    }

    private func parseAllEvents() -> [EventItem] {
        var result: [EventItem] = []

        if let diagnostic = events.parseDiagnosticEventsXdr() {
            for (index, event) in diagnostic.enumerated() {
                result.append(EventItem(
                    title: "Diagnostic Event \(index + 1)",
                    subtitle: "Runtime debugging information",
                    event: event.event
                ))
            }
        }

        if let transaction = events.parseTransactionEventsXdr() {
            for (index, event) in transaction.enumerated() {
                result.append(EventItem(
                    title: "Transaction Event \(index + 1)",
                    subtitle: "Transaction-level event",
                    event: event.event
                ))
            }
        }

        if let contract = events.parseContractEventsXdr() {
            for (opIndex, opEvents) in contract.enumerated() {
                for (eventIndex, event) in opEvents.enumerated() {
                    result.append(EventItem(
                        title: "Contract Event (Op \(opIndex + 1).\(eventIndex + 1))",
                        subtitle: "Emitted by smart contract",
                        event: event
                    ))
                }
            }
        }

        return result
    }

    struct EventItem {
        let title: String
        let subtitle: String
        let event: ContractEventXdr
    }
}

struct EventDetails: View {
    let event: ContractEventXdr
    let ledger: Int64?

    // Compute contract ID string before the view body
    private var contractIdString: String {
        guard let contractId = event.contractId else {
            return "None (system event)"
        }

        // ContractIDXdr value classes may be unwrapped in Swift
        // Try to extract the ByteArray directly using KVC
        do {
            // First try direct cast to KotlinByteArray
            if let byteArray = contractId as? KotlinByteArray {
                return try StrKey.shared.encodeContract(data: byteArray)
            }

            // If that doesn't work, try reflection-based access
            let mirror = Mirror(reflecting: contractId)
            for child in mirror.children {
                if let byteArray = child.value as? KotlinByteArray {
                    return try StrKey.shared.encodeContract(data: byteArray)
                }
                // Try nested value property
                let nestedMirror = Mirror(reflecting: child.value)
                for nestedChild in nestedMirror.children {
                    if let byteArray = nestedChild.value as? KotlinByteArray {
                        return try StrKey.shared.encodeContract(data: byteArray)
                    }
                }
            }

            return "Unable to extract contract ID"
        } catch {
            return "Error encoding contract ID: \(error.localizedDescription)"
        }
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            InfoRow(label: "Event Type", value: event.type.name)

            // Display contract ID
            if event.contractId != nil {
                CopyableDetailRow(label: "Contract ID", value: contractIdString)
            } else {
                InfoRow(label: "Contract ID", value: contractIdString)
            }

            InfoRow(label: "In Successful Contract Call", value: "Yes")

            if let ledger = ledger {
                InfoRow(label: "Ledger", value: "\(ledger)")
            }

            Divider()
            Text("Event Data")
                .font(.system(size: 12, weight: .bold))
                .padding(.vertical, 4)

            if let eventBody = event.body as? ContractEventBodyXdr.V0 {
                // Display topics
                if !eventBody.value.topics.isEmpty {
                    Text("Topics (\(eventBody.value.topics.count)):")
                        .font(.system(size: 11, weight: .bold))
                        .padding(.top, 8)

                    ForEach(Array(eventBody.value.topics.enumerated()), id: \.offset) { index, topic in
                        VStack(alignment: .leading, spacing: 4) {
                            Text("Topic \(index + 1):")
                                .font(.system(size: 11))
                                .foregroundStyle(Material3Colors.onSurfaceVariant.opacity(0.7))

                            Text(formatSCVal(topic, indentLevel: 0))
                                .font(.system(size: 11, design: .monospaced))
                                .foregroundStyle(Material3Colors.onSurfaceVariant)
                                .textSelection(.enabled)
                                .lineLimit(nil)
                                .fixedSize(horizontal: false, vertical: true)
                        }
                        .padding(.leading, 8)
                        .padding(.top, 4)
                    }
                } else {
                    Text("No topics")
                        .font(.system(size: 11))
                        .foregroundStyle(Material3Colors.onSurfaceVariant.opacity(0.7))
                        .padding(.leading, 8)
                }

                // Display event value
                Spacer().frame(height: 8)
                Text("Value:")
                    .font(.system(size: 11, weight: .bold))

                Text(formatSCVal(eventBody.value.data, indentLevel: 0))
                    .font(.system(size: 11, design: .monospaced))
                    .foregroundStyle(Material3Colors.onSurfaceVariant)
                    .textSelection(.enabled)
                    .lineLimit(nil)
                    .fixedSize(horizontal: false, vertical: true)
                    .padding(.leading, 8)
                    .padding(.top, 4)
            }
        }
        .padding(.leading, 12)
    }
}

struct ReturnValueCard: View {
    let scVal: SCValXdr

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Contract Return Value")
                .font(.system(size: 14, weight: .bold))
                .foregroundStyle(Material3Colors.onSurfaceVariant)

            Divider()

            InfoRow(label: "Type", value: String(describing: scVal.discriminant))

            VStack(alignment: .leading, spacing: 4) {
                Text("Value")
                    .font(.system(size: 11, weight: .medium))
                    .foregroundStyle(Material3Colors.onSurfaceVariant.opacity(0.7))

                Text(formatSCVal(scVal, indentLevel: 0))
                    .font(.system(size: 12, design: .monospaced))
                    .foregroundStyle(Material3Colors.onSurfaceVariant)
                    .textSelection(.enabled)
                    .lineLimit(nil)
                    .fixedSize(horizontal: false, vertical: true)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(16)
        .background(Material3Colors.tertiaryContainer.opacity(0.3))
        .cornerRadius(12)
    }
}

// MARK: - SCVal Formatting

/**
 * Formats an SCVal for display, showing both the type and a human-readable representation.
 *
 * This function recursively expands complex types (Maps and Vecs) to show their complete contents,
 * with proper indentation for nested structures. This provides developers with comprehensive insight
 * into event data without requiring manual XDR parsing.
 *
 * Key features:
 * - Map types: Expands to show all key-value pairs with numbering and indentation
 * - Vec types: Expands to show all elements with numbering and indentation
 * - Nested structures: Recursively formats with increased indentation
 * - Simple types: Shows type label and formatted value
 *
 * - Parameters:
 *   - scVal: The SCVal to format
 *   - indentLevel: The current indentation level (used for nested structures)
 * - Returns: A formatted string showing the type and value, with newlines and indentation for complex types
 */
func formatSCVal(_ scVal: SCValXdr, indentLevel: Int = 0) -> String {
    let indent = String(repeating: "  ", count: indentLevel)
    let nextIndent = String(repeating: "  ", count: indentLevel + 1)

    // Use pattern matching based on the type discriminant
    let description = String(describing: scVal)

    if let bVal = scVal as? SCValXdr.B {
        return "Bool: \(bVal.value)"
    }

    if description.starts(with: "Void") {
        return "Void"
    }

    if let errorVal = scVal as? SCValXdr.Error {
        return "Error: \(errorVal.value)"
    }

    if let u32Val = scVal as? SCValXdr.U32 {
        return "U32: \(u32Val.value)"
    }

    if let i32Val = scVal as? SCValXdr.I32 {
        return "I32: \(i32Val.value)"
    }

    if let u64Val = scVal as? SCValXdr.U64 {
        return "U64: \(u64Val.value)"
    }

    if let i64Val = scVal as? SCValXdr.I64 {
        return "I64: \(i64Val.value)"
    }

    if let timepointVal = scVal as? SCValXdr.Timepoint {
        return "Timepoint: \(timepointVal.value)"
    }

    if let durationVal = scVal as? SCValXdr.Duration {
        return "Duration: \(durationVal.value)"
    }

    if let u128Val = scVal as? SCValXdr.U128 {
        return "U128: \(formatU128(u128Val.value))"
    }

    if let i128Val = scVal as? SCValXdr.I128 {
        return "I128: \(formatI128(i128Val.value))"
    }

    if let u256Val = scVal as? SCValXdr.U256 {
        return "U256: \(formatU256(u256Val.value))"
    }

    if let i256Val = scVal as? SCValXdr.I256 {
        return "I256: \(formatI256(i256Val.value))"
    }

    if let bytesVal = scVal as? SCValXdr.Bytes {
        if let byteArray = bytesVal.value as? KotlinByteArray {
            return "Bytes: \(formatBytes(byteArray))"
        }
        return "Bytes: [invalid format]"
    }

    if let strVal = scVal as? SCValXdr.Str {
        return "String: \"\(strVal.value)\""
    }

    if let symVal = scVal as? SCValXdr.Sym {
        return "Symbol: \(symVal.value)\""
    }

    if let vecVal = scVal as? SCValXdr.Vec {
        if let elementsArray = vecVal.value as? NSArray {
            let elements = elementsArray as! [SCValXdr]
            if elements.isEmpty {
                return "Vec: [0 elements]"
            } else {
                let formattedElements = elements.enumerated().map { index, element in
                    let formattedElement = formatSCVal(element, indentLevel: indentLevel + 1)
                    return "\(nextIndent)Element \(index + 1): \(formattedElement)"
                }
                return "Vec: [\(elements.count) elements]\n\(formattedElements.joined(separator: "\n"))"
            }
        }
        return "Vec: [invalid format]"
    }

    if let mapVal = scVal as? SCValXdr.Map {
        if let entriesArray = mapVal.value as? NSArray {
            let entries = entriesArray as! [SCMapEntryXdr]
            if entries.isEmpty {
                return "Map: {0 entries}"
            } else {
                let formattedEntries = entries.enumerated().map { index, entry in
                    let keyStr = formatSCVal(entry.key, indentLevel: indentLevel + 1)
                    let valStr = formatSCVal(entry.val, indentLevel: indentLevel + 1)
                    return "\(nextIndent)Entry \(index + 1):\n\(nextIndent)  Key: \(keyStr)\n\(nextIndent)  Value: \(valStr)"
                }
                return "Map: {\(entries.count) entries}\n\(formattedEntries.joined(separator: "\n"))"
            }
        }
        return "Map: {invalid format}"
    }

    if let addressVal = scVal as? SCValXdr.Address {
        return "Address: \(formatAddress(addressVal.value))"
    }

    if description.starts(with: "Instance") {
        return "ContractInstance"
    }

    if let nonceVal = scVal as? SCValXdr.NonceKey {
        return "LedgerKeyNonce: \(nonceVal.value.nonce)"
    }

    return description
}

/**
 * Formats an SCAddress as a strkey-encoded string.
 */
func formatAddress(_ address: SCAddressXdr) -> String {
    if let accountId = address as? SCAddressXdr.AccountId {
        do {
            let publicKey = accountId.value
            if let ed25519 = publicKey as? PublicKeyXdr.Ed25519 {
                let bytes = ed25519.value
                if let byteArray = bytes as? KotlinByteArray {
                    return try StrKey.shared.encodeEd25519PublicKey(data: byteArray)
                }
            }
        } catch {
            return "Invalid account ID"
        }
    }

    if let contractId = address as? SCAddressXdr.ContractId {
        do {
            let hashBytes = contractId.value
            if let byteArray = hashBytes as? KotlinByteArray {
                return try StrKey.shared.encodeContract(data: byteArray)
            }
        } catch {
            return "Invalid contract ID"
        }
    }

    if let muxed = address as? SCAddressXdr.MuxedAccount {
        return "Muxed: ID=\(muxed.value.id)"
    }

    if let claimable = address as? SCAddressXdr.ClaimableBalanceId {
        do {
            let balanceId = claimable.value
            if let v0 = balanceId as? ClaimableBalanceIDXdr.V0 {
                let bytes = v0.value
                if let byteArray = bytes as? KotlinByteArray {
                    return try StrKey.shared.encodeClaimableBalance(data: byteArray)
                }
            }
        } catch {
            return "Claimable Balance: \(claimable.value)"
        }
    }

    if let liquidityPool = address as? SCAddressXdr.LiquidityPoolId {
        return "Liquidity Pool: \(liquidityPool.value)"
    }

    return String(describing: address)
}

/**
 * Formats a U128 value as a decimal string.
 */
func formatU128(_ value: UInt128PartsXdr) -> String {
    let hi = value.hi
    let lo = value.lo
    if hi == 0 {
        return "\(lo)"
    } else {
        return "\(hi):\(lo)"
    }
}

/**
 * Formats an I128 value as a decimal string.
 */
func formatI128(_ value: Int128PartsXdr) -> String {
    let hi = value.hi
    let lo = value.lo
    if hi == 0 && lo <= Int64.max {
        return "\(Int64(lo))"
    } else {
        return "\(hi):\(lo)"
    }
}

/**
 * Formats a U256 value as a hex string (showing first/last bytes).
 */
func formatU256(_ value: UInt256PartsXdr) -> String {
    return "0x...\(String(value.hiHi, radix: 16))"
}

/**
 * Formats an I256 value as a hex string (showing first/last bytes).
 */
func formatI256(_ value: Int256PartsXdr) -> String {
    return "0x...\(String(value.hiHi, radix: 16))"
}

/**
 * Formats a byte array, showing length and first few bytes.
 */
func formatBytes(_ byteArray: KotlinByteArray) -> String {
    let size = Int(byteArray.size)
    let bytes = (0..<size).map { Int8(byteArray.get(index: Int32($0))) }
    let unsignedBytes = bytes.map { UInt8(bitPattern: $0) }
    if bytes.count <= 32 {
        return unsignedBytes.map { String(format: "%02x", $0) }.joined()
    } else {
        let first = unsignedBytes.prefix(16).map { String(format: "%02x", $0) }.joined()
        let last = unsignedBytes.suffix(8).map { String(format: "%02x", $0) }.joined()
        return "\(first)...\(last) (\(bytes.count) bytes)"
    }
}

// MARK: - Reusable Components

struct CopyableDetailRow: View {
    let label: String
    let value: String

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(label)
                .font(.system(size: 11, weight: .medium))
                .foregroundStyle(Material3Colors.onSurfaceVariant.opacity(0.7))

            HStack(alignment: .top) {
                Text(value)
                    .font(.system(size: 12, design: .monospaced))
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

struct InfoRow: View {
    let label: String
    let value: String

    var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(label)
                .font(.system(size: 11, weight: .medium))
                .foregroundStyle(Material3Colors.onSurfaceVariant.opacity(0.7))

            Text(value)
                .font(.system(size: 12, design: .monospaced))
                .foregroundStyle(Material3Colors.onSurfaceVariant)
                .textSelection(.enabled)
                .lineLimit(nil)
                .fixedSize(horizontal: false, vertical: true)
        }
    }
}
