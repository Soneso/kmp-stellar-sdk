import SwiftUI
import shared

// MARK: - Key Pair Display Components

struct KeyDisplayCard: View {
    let title: String
    let value: String
    let description: String
    let backgroundColor: Color
    let textColor: Color
    let descriptionColor: Color
    let iconColor: Color
    let onCopy: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                Text(title)
                    .font(.system(size: 16, weight: .semibold))
                    .foregroundStyle(textColor)

                Spacer()

                Button(action: onCopy) {
                    Image(systemName: "doc.on.doc")
                        .font(.system(size: 16))
                        .foregroundStyle(iconColor)
                }
                .buttonStyle(.plain)
                .help("Copy to clipboard")
            }

            Text(value)
                .font(.system(.body, design: .monospaced))
                .foregroundStyle(descriptionColor)
                .textSelection(.enabled)
                .lineLimit(nil)
                .fixedSize(horizontal: false, vertical: true)

            Text(description)
                .font(.system(size: 13))
                .foregroundStyle(descriptionColor)
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(backgroundColor)
        .cornerRadius(12)
        .shadow(color: Material3Colors.cardShadow, radius: 2, x: 0, y: 1)
    }
}

struct SecretKeyDisplayCard: View {
    let title: String
    let keypair: KeyPair
    let description: String
    @Binding var isVisible: Bool
    let onCopy: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                Text(title)
                    .font(.system(size: 16, weight: .semibold))
                    .foregroundStyle(Material3Colors.onTertiaryContainer)

                Spacer()

                Button(action: { isVisible.toggle() }) {
                    Image(systemName: isVisible ? "eye.slash.fill" : "eye.fill")
                        .font(.system(size: 16))
                        .foregroundStyle(Material3Colors.onTertiaryContainer)
                }
                .buttonStyle(.plain)
                .help(isVisible ? "Hide secret" : "Show secret")

                Button(action: onCopy) {
                    Image(systemName: "doc.on.doc")
                        .font(.system(size: 16))
                        .foregroundStyle(Material3Colors.onTertiaryContainer)
                }
                .buttonStyle(.plain)
                .help("Copy to clipboard")
            }

            Text(isVisible ? (keypair.getSecretSeedAsString() ?? "") : String(repeating: "•", count: 56))
                .font(.system(.body, design: .monospaced))
                .foregroundStyle(Material3Colors.onTertiaryContainer)
                .textSelection(.enabled)
                .lineLimit(nil)
                .fixedSize(horizontal: false, vertical: true)

            Text(description)
                .font(.system(size: 13))
                .foregroundStyle(Material3Colors.onTertiaryContainer)
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Material3Colors.tertiaryContainer)
        .cornerRadius(12)
        .shadow(color: Material3Colors.cardShadow, radius: 2, x: 0, y: 1)
    }
}
