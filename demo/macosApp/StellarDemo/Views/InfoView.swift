import SwiftUI

struct InfoView: View {
    @ObservedObject var toastManager: ToastManager

    // Version from demo.version property in gradle.properties
    private let appVersion = "1.3.1"
    private let githubUrl = "https://github.com/Soneso/kmp-stellar-sdk"
    private let emailAddress = "info@soneso.com"

    var body: some View {
        ScrollView {
            VStack(spacing: 16) {
                // App version and title
                InfoCard(title: "KMP Stellar SDK Demo", color: .default) {
                    Text("Version \(appVersion)")
                        .font(.system(size: 13))
                        .foregroundStyle(Material3Colors.onSurface)
                }

                // About the SDK
                InfoCard(title: nil, color: .primary) {
                    HStack(alignment: .top, spacing: 12) {
                        Image(systemName: "curlybraces.square.fill")
                            .font(.system(size: 28))
                            .foregroundStyle(Material3Colors.primary)

                        VStack(alignment: .leading, spacing: 8) {
                            Text("About This App")
                                .font(.system(size: 18, weight: .bold))
                                .foregroundStyle(Material3Colors.onPrimaryContainer)

                            Divider()
                                .background(Material3Colors.primary.opacity(0.2))

                            Text("This application was built with the Kotlin Multiplatform Stellar SDK (kmp-stellar-sdk). It demonstrates comprehensive SDK functionality across all supported platforms: Android, iOS, macOS, Desktop (JVM), and Web.")
                                .font(.system(size: 13))
                                .foregroundStyle(Material3Colors.onPrimaryContainer)
                                .fixedSize(horizontal: false, vertical: true)

                            Text("All features interact with the Stellar live testnet, providing real-world examples of blockchain operations without using real funds.")
                                .font(.system(size: 13))
                                .foregroundStyle(Material3Colors.onPrimaryContainer)
                                .fixedSize(horizontal: false, vertical: true)
                        }
                    }
                }

                // GitHub repository
                InfoCard(title: nil, color: .tertiary) {
                    VStack(alignment: .leading, spacing: 8) {
                        HStack(alignment: .top, spacing: 12) {
                            Image(systemName: "link.circle.fill")
                                .font(.system(size: 28))
                                .foregroundStyle(Color(red: 0.85, green: 0.47, blue: 0.03)) // Gold color

                            Text("GitHub Repository")
                                .font(.system(size: 18, weight: .bold))
                                .foregroundStyle(Material3Colors.onTertiaryContainer)
                        }

                        Divider()
                            .background(Color(red: 0.85, green: 0.47, blue: 0.03).opacity(0.3))

                        Text("SDK Repository")
                            .font(.system(size: 11, weight: .semibold))
                            .foregroundStyle(Material3Colors.onTertiaryContainer.opacity(0.7))
                            .textCase(.uppercase)

                        Button(action: {
                            openURL(githubUrl)
                        }) {
                            Text(githubUrl)
                                .font(.system(size: 13, design: .monospaced))
                                .underline()
                                .foregroundStyle(Material3Colors.primary)
                                .frame(maxWidth: .infinity, alignment: .leading)
                        }
                        .buttonStyle(.plain)
                        .onHover { hovering in
                            if hovering {
                                NSCursor.pointingHand.push()
                            } else {
                                NSCursor.pop()
                            }
                        }

                        Text("This demo app is part of the SDK repository.")
                            .font(.system(size: 12))
                            .foregroundStyle(Material3Colors.onTertiaryContainer.opacity(0.7))
                    }
                }

                // Star on GitHub
                InfoCard(title: nil, color: .success) {
                    VStack(alignment: .leading, spacing: 8) {
                        HStack(alignment: .top, spacing: 12) {
                            Image(systemName: "star.fill")
                                .font(.system(size: 28))
                                .foregroundStyle(Color(red: 0.06, green: 0.46, blue: 0.43)) // Teal color

                            Text("Support the Project")
                                .font(.system(size: 18, weight: .bold))
                                .foregroundStyle(Material3Colors.onSuccessContainer)
                        }

                        Divider()
                            .background(Color(red: 0.06, green: 0.46, blue: 0.43).opacity(0.3))

                        Text("If you find the Kotlin Multiplatform Stellar SDK useful, please consider starring the repository on GitHub. Your support helps with maintenance and development, and helps other developers discover this SDK.")
                            .font(.system(size: 13))
                            .foregroundStyle(Material3Colors.onSuccessContainer)
                            .fixedSize(horizontal: false, vertical: true)

                        Text("Every star counts and motivates continued improvement of the SDK.")
                            .font(.system(size: 12, weight: .medium))
                            .foregroundStyle(Material3Colors.onSuccessContainer.opacity(0.8))
                    }
                }

                // Feedback
                InfoCard(title: nil, color: .primary) {
                    VStack(alignment: .leading, spacing: 8) {
                        HStack(alignment: .top, spacing: 12) {
                            Image(systemName: "envelope.fill")
                                .font(.system(size: 28))
                                .foregroundStyle(Material3Colors.primary)

                            Text("Feedback")
                                .font(.system(size: 18, weight: .bold))
                                .foregroundStyle(Material3Colors.onPrimaryContainer)
                        }

                        Divider()
                            .background(Material3Colors.primary.opacity(0.2))

                        Text("We value your feedback and suggestions. If you have questions, feature requests, or encounter any issues, please reach out to us.")
                            .font(.system(size: 13))
                            .foregroundStyle(Material3Colors.onPrimaryContainer)
                            .fixedSize(horizontal: false, vertical: true)

                        HStack(alignment: .center, spacing: 8) {
                            Text("Contact:")
                                .font(.system(size: 11, weight: .semibold))
                                .foregroundStyle(Material3Colors.onPrimaryContainer.opacity(0.7))

                            Button(action: {
                                openURL("mailto:\(emailAddress)")
                            }) {
                                Text(emailAddress)
                                    .font(.system(size: 13, design: .monospaced))
                                    .underline()
                                    .foregroundStyle(Material3Colors.primary)
                            }
                            .buttonStyle(.plain)
                            .onHover { hovering in
                                if hovering {
                                    NSCursor.pointingHand.push()
                                } else {
                                    NSCursor.pop()
                                }
                            }
                        }
                    }
                }
            }
            .padding(16)
        }
        .background(Material3Colors.surface)
        .navigationToolbar(title: "Info")
    }

    private func openURL(_ urlString: String) {
        guard let url = URL(string: urlString) else {
            toastManager.show("Invalid URL")
            return
        }

        if NSWorkspace.shared.open(url) {
            toastManager.show("Opened in default application")
        } else {
            toastManager.show("Failed to open URL")
        }
    }
}
