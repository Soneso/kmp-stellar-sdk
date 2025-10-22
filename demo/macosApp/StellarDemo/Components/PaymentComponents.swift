import SwiftUI
import shared

// MARK: - Payment Success Cards

struct PaymentSuccessCards: View {
    let success: SendPaymentResult.Success

    var body: some View {
        VStack(spacing: 16) {
            // Success header card (green)
            VStack(alignment: .leading, spacing: 8) {
                HStack(spacing: 8) {
                    Image(systemName: "checkmark.circle.fill")
                        .font(.system(size: 24))
                        .foregroundStyle(Material3Colors.onSuccessContainer)

                    Text("Payment Sent Successfully")
                        .font(.system(size: 16, weight: .bold))
                        .foregroundStyle(Material3Colors.onSuccessContainer)
                }

                Text("Sent \(success.amount) \(success.assetCode) to destination account")
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

                CopyableDetailRow(label: "Hash", value: success.transactionHash)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(16)
            .background(Material3Colors.primaryContainer)
            .cornerRadius(12)

            // Payment Details Card
            VStack(alignment: .leading, spacing: 12) {
                Text("Payment Details")
                    .font(.system(size: 14, weight: .bold))
                    .foregroundStyle(Material3Colors.onSurfaceVariant)

                Divider()

                PaymentCopyableRow(label: "From", value: success.source)
                PaymentCopyableRow(label: "To", value: success.destination)
                PaymentDetailRow(label: "Amount", value: "\(success.amount) \(success.assetCode)")

                if let issuer = success.assetIssuer {
                    PaymentCopyableRow(label: "Asset Issuer", value: issuer)
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
                    Text("• The payment has been successfully recorded on the Stellar ledger")
                        .font(.system(size: 13))
                        .foregroundStyle(Material3Colors.onSecondaryContainer)

                    Text("• The destination account now has the funds available")
                        .font(.system(size: 13))
                        .foregroundStyle(Material3Colors.onSecondaryContainer)

                    Text("• Use 'Fetch Account Details' to verify the updated balances")
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
}

// MARK: - Payment Detail Row (non-copyable)

struct PaymentDetailRow: View {
    let label: String
    let value: String

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(label)
                .font(.system(size: 12, weight: .medium))
                .foregroundStyle(Material3Colors.onSurfaceVariant.opacity(0.7))

            Text(value)
                .font(.system(.body, design: .monospaced))
                .foregroundStyle(Material3Colors.onSurfaceVariant)
                .textSelection(.enabled)
        }
    }
}

// MARK: - Payment Copyable Row

struct PaymentCopyableRow: View {
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
