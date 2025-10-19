import SwiftUI
import shared

// MARK: - Trust Asset Success Cards

struct TrustAssetSuccessCards: View {
    let success: TrustAssetResult.Success

    var body: some View {
        VStack(spacing: 16) {
            // Success header card (green)
            VStack(alignment: .leading, spacing: 8) {
                HStack(spacing: 8) {
                    Image(systemName: "checkmark.circle.fill")
                        .font(.system(size: 24))
                        .foregroundStyle(Material3Colors.onSuccessContainer)

                    Text("Trustline Established")
                        .font(.system(size: 16, weight: .bold))
                        .foregroundStyle(Material3Colors.onSuccessContainer)
                }

                Text("Trustline established successfully. Transaction hash: \(success.transactionHash)")
                    .font(.system(size: 14))
                    .foregroundStyle(Material3Colors.onSuccessContainer)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(16)
            .background(Material3Colors.successContainer)
            .cornerRadius(12)

            // Transaction Details Card
            VStack(alignment: .leading, spacing: 12) {
                Text("Transaction Details")
                    .font(.system(size: 14, weight: .bold))
                    .foregroundStyle(Material3Colors.onSurfaceVariant)

                Divider()

                TrustAssetDetailRow(label: "Asset Code", value: success.assetCode)
                TrustAssetDetailRow(label: "Asset Issuer", value: success.assetIssuer, monospace: true)
                TrustAssetDetailRow(
                    label: "Trust Limit",
                    value: success.limit == "922337203685.4775807"
                        ? "Maximum (\(success.limit))"
                        : success.limit
                )
                TrustAssetDetailRow(label: "Transaction Hash", value: success.transactionHash, monospace: true)
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
                    Text("• You can now receive \(success.assetCode) from the asset issuer")
                        .font(.system(size: 13))
                        .foregroundStyle(Material3Colors.onSecondaryContainer)

                    Text("• Check your account balances to see the new trustline")
                        .font(.system(size: 13))
                        .foregroundStyle(Material3Colors.onSecondaryContainer)

                    Text("• Use the 'Fetch Account Details' feature to view your trustlines")
                        .font(.system(size: 13))
                        .foregroundStyle(Material3Colors.onSecondaryContainer)

                    Text("• You can modify the trust limit or remove the trustline later")
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

// MARK: - Trust Asset Detail Row

struct TrustAssetDetailRow: View {
    let label: String
    let value: String
    var monospace: Bool = false

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(label)
                .font(.system(size: 12, weight: .medium))
                .foregroundStyle(Material3Colors.onSurfaceVariant.opacity(0.7))

            Text(value)
                .font(monospace ? .system(.body, design: .monospaced) : .system(.body))
                .foregroundStyle(Material3Colors.onSurfaceVariant)
                .textSelection(.enabled)
        }
    }
}
