import SwiftUI
import shared

// MARK: - Account Details Display Components

struct AccountDetailsCard: View {
    let account: AccountResponse

    var body: some View {
        VStack(spacing: 12) {
            // Success header
            VStack(alignment: .leading, spacing: 8) {
                Text("Account Found")
                    .font(.system(size: 16, weight: .bold))
                    .foregroundStyle(Material3Colors.onSuccessContainer)

                Text("Successfully fetched details for account \(shortenAccountId(account.accountId))")
                    .font(.system(size: 14))
                    .foregroundStyle(Material3Colors.onSuccessContainer)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(16)
            .background(Material3Colors.successContainer)
            .cornerRadius(12)

            // Basic Information
            DetailsSectionCard(title: "Basic Information") {
                DetailRow(label: "Account ID", value: account.accountId, monospace: true)
                DetailRow(label: "Sequence Number", value: account.sequenceNumber.description)
                DetailRow(label: "Subentry Count", value: account.subentryCount.description)
                if let homeDomain = account.homeDomain {
                    DetailRow(label: "Home Domain", value: homeDomain)
                }
                DetailRow(label: "Last Modified Ledger", value: account.lastModifiedLedger.description)
                DetailRow(label: "Last Modified Time", value: account.lastModifiedTime)
            }

            // Balances
            DetailsSectionCard(title: "Balances (\(account.balances.count))") {
                ForEach(Array(account.balances.enumerated()), id: \.offset) { index, balance in
                    if index > 0 {
                        Divider()
                            .padding(.vertical, 8)
                    }
                    BalanceItem(balance: balance)
                }
            }

            // Thresholds
            DetailsSectionCard(title: "Thresholds") {
                DetailRow(label: "Low Threshold", value: account.thresholds.lowThreshold.description)
                DetailRow(label: "Medium Threshold", value: account.thresholds.medThreshold.description)
                DetailRow(label: "High Threshold", value: account.thresholds.highThreshold.description)
            }

            // Flags
            DetailsSectionCard(title: "Authorization Flags") {
                FlagRow(label: "Auth Required", enabled: account.flags.authRequired)
                FlagRow(label: "Auth Revocable", enabled: account.flags.authRevocable)
                FlagRow(label: "Auth Immutable", enabled: account.flags.authImmutable)
                FlagRow(label: "Auth Clawback Enabled", enabled: account.flags.authClawbackEnabled)
            }

            // Signers
            DetailsSectionCard(title: "Signers (\(account.signers.count))") {
                ForEach(Array(account.signers.enumerated()), id: \.offset) { index, signer in
                    if index > 0 {
                        Divider()
                            .padding(.vertical, 8)
                    }
                    SignerItem(signer: signer)
                }
            }

            // Data Entries
            if !account.data.isEmpty {
                DetailsSectionCard(title: "Data Entries (\(account.data.count))") {
                    ForEach(Array(account.data.keys.sorted()), id: \.self) { key in
                        if let value = account.data[key] {
                            DetailRow(label: key, value: value, monospace: true)
                        }
                    }
                }
            }

            // Sponsorship Information
            if account.sponsor != nil || (account.numSponsoring != nil && account.numSponsoring!.int32Value > 0) || (account.numSponsored != nil && account.numSponsored!.int32Value > 0) {
                DetailsSectionCard(title: "Sponsorship") {
                    if let sponsor = account.sponsor {
                        DetailRow(label: "Sponsor", value: sponsor, monospace: true)
                    }
                    if let numSponsoring = account.numSponsoring {
                        DetailRow(label: "Number Sponsoring", value: numSponsoring.description)
                    }
                    if let numSponsored = account.numSponsored {
                        DetailRow(label: "Number Sponsored", value: numSponsored.description)
                    }
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

struct DetailsSectionCard<Content: View>: View {
    let title: String
    @ViewBuilder let content: () -> Content

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(title)
                .font(.system(size: 14, weight: .bold))
                .foregroundStyle(Material3Colors.onSurfaceVariant)

            Divider()

            VStack(alignment: .leading, spacing: 8) {
                content()
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(16)
        .background(Material3Colors.surfaceVariant)
        .cornerRadius(12)
    }
}

struct DetailRow: View {
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

struct FlagRow: View {
    let label: String
    let enabled: Bool

    var body: some View {
        HStack {
            Text(label)
                .font(.system(size: 14))
                .foregroundStyle(Material3Colors.onSurfaceVariant)

            Spacer()

            Text(enabled ? "Enabled" : "Disabled")
                .font(.system(size: 12, weight: .medium))
                .foregroundStyle(enabled ? Material3Colors.onSuccessContainer : Material3Colors.onSurfaceVariant.opacity(0.7))
                .padding(.horizontal, 8)
                .padding(.vertical, 4)
                .background(enabled ? Material3Colors.successContainer : Material3Colors.surfaceVariant)
                .cornerRadius(4)
        }
    }
}

struct BalanceItem: View {
    let balance: AccountResponse.Balance

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            // Asset type
            Text(assetTitle)
                .font(.system(size: 14, weight: .bold))
                .foregroundStyle(Material3Colors.onSurfaceVariant)

            // Balance
            DetailRow(label: "Balance", value: balance.balance)

            // Asset issuer (if not native)
            if let issuer = balance.assetIssuer {
                DetailRow(label: "Issuer", value: issuer, monospace: true)
            }

            // Liquidity pool ID (if applicable)
            if let poolId = balance.liquidityPoolId {
                DetailRow(label: "Pool ID", value: poolId, monospace: true)
            }

            // Additional details
            if let limit = balance.limit {
                DetailRow(label: "Limit", value: limit)
            }
            if let buyingLiabilities = balance.buyingLiabilities {
                DetailRow(label: "Buying Liabilities", value: buyingLiabilities)
            }
            if let sellingLiabilities = balance.sellingLiabilities {
                DetailRow(label: "Selling Liabilities", value: sellingLiabilities)
            }

            // Authorization flags
            if let isAuthorized = balance.isAuthorized {
                FlagRow(label: "Authorized", enabled: isAuthorized.boolValue)
            }
            if let isAuthorizedToMaintainLiabilities = balance.isAuthorizedToMaintainLiabilities {
                FlagRow(label: "Authorized to Maintain Liabilities", enabled: isAuthorizedToMaintainLiabilities.boolValue)
            }
            if let isClawbackEnabled = balance.isClawbackEnabled {
                FlagRow(label: "Clawback Enabled", enabled: isClawbackEnabled.boolValue)
            }
        }
    }

    private var assetTitle: String {
        if balance.assetType == "native" {
            return "Native (XLM)"
        } else if let assetCode = balance.assetCode {
            return "\(assetCode) (\(balance.assetType))"
        } else if balance.liquidityPoolId != nil {
            return "Liquidity Pool"
        } else {
            return balance.assetType
        }
    }
}

struct SignerItem: View {
    let signer: AccountResponse.Signer

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            DetailRow(label: "Key", value: signer.key, monospace: true)

            HStack {
                Text("Type: \(signer.type)")
                    .font(.system(size: 14))
                    .foregroundStyle(Material3Colors.onSurfaceVariant)

                Spacer()

                Text("Weight: \(signer.weight.description)")
                    .font(.system(size: 14, weight: .bold))
                    .foregroundStyle(Material3Colors.onSurfaceVariant)
            }

            if let sponsor = signer.sponsor {
                DetailRow(label: "Sponsor", value: sponsor, monospace: true)
            }
        }
    }
}
