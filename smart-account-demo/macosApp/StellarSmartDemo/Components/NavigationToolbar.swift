//
//  NavigationToolbar.swift
//  Smart Account Demo
//
//  Copyright (c) 2026 Soneso. All rights reserved.
//

import SwiftUI

/// View modifier that applies a consistent navigation bar with a blue gradient header.
///
/// Hides the default back button and renders a custom back button so that the visual
/// style matches the rest of the smart account demo UI.
///
/// ## Usage
///
/// ```swift
/// MyView()
///     .navigationToolbar(title: "Create Account", subtitle: "Testnet")
/// ```
struct NavigationToolbar: ViewModifier {
    let title: String
    let subtitle: String?
    let showBackButton: Bool
    let onBack: (() -> Void)?

    @Environment(\.dismiss) private var dismiss

    init(
        title: String,
        subtitle: String? = nil,
        showBackButton: Bool = true,
        onBack: (() -> Void)? = nil
    ) {
        self.title = title
        self.subtitle = subtitle
        self.showBackButton = showBackButton
        self.onBack = onBack
    }

    func body(content: Content) -> some View {
        VStack(spacing: 0) {
            navigationBar
            content
        }
        .navigationBarBackButtonHidden(true)
    }

    // MARK: - Navigation bar

    private var navigationBar: some View {
        VStack(spacing: 0) {
            ZStack(alignment: .center) {
                // Back button — pinned left, does not affect title centering
                HStack {
                    if showBackButton {
                        Button(action: { if let onBack { onBack() } else { dismiss() } }) {
                            HStack(spacing: 4) {
                                Image(systemName: "chevron.left")
                                    .font(.system(size: 12, weight: .semibold))
                                Text("Back")
                                    .font(.system(size: 12))
                            }
                            .foregroundColor(.white)
                        }
                        .buttonStyle(.plain)
                    }
                    Spacer()
                }
                .padding(.horizontal, 12)

                // Centered title block
                VStack(spacing: 2) {
                    Text(title)
                        .font(.system(size: 17, weight: .semibold))
                        .foregroundColor(.white)

                    if let subtitle = subtitle, !subtitle.isEmpty {
                        Text(subtitle)
                            .font(.system(size: 11))
                            .foregroundColor(Material3Colors.secondaryContainer)
                    }
                }
                .frame(maxHeight: .infinity)
            }
            .frame(height: 52)
            .background(
                LinearGradient(
                    gradient: Gradient(colors: [
                        Material3Colors.primary,
                        Material3Colors.onPrimaryContainer
                    ]),
                    startPoint: .leading,
                    endPoint: .trailing
                )
            )
            .shadow(color: Material3Colors.primary.opacity(0.3), radius: 4, x: 0, y: 2)

            Divider()
                .background(Color.white.opacity(0.2))
        }
    }
}

// MARK: - View extension

extension View {
    /// Applies the smart account demo navigation bar to this view.
    ///
    /// - Parameters:
    ///   - title: Screen title displayed in the center of the bar.
    ///   - subtitle: Optional subtitle shown below the title in a lighter color.
    ///   - showBackButton: Whether to render the back chevron. Defaults to `true`.
    ///   - onBack: Custom back action. Falls back to `dismiss()` when `nil`.
    func navigationToolbar(
        title: String,
        subtitle: String? = nil,
        showBackButton: Bool = true,
        onBack: (() -> Void)? = nil
    ) -> some View {
        modifier(NavigationToolbar(
            title: title,
            subtitle: subtitle,
            showBackButton: showBackButton,
            onBack: onBack
        ))
    }
}
