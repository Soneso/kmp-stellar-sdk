//
//  LoadingButton.swift
//  Smart Account Demo
//
//  Copyright (c) 2026 Soneso. All rights reserved.
//

import SwiftUI

/// Full-width button with an integrated loading state.
///
/// When `isLoading` is `true` the button shows a `ProgressView` spinner alongside
/// `loadingText` and is automatically disabled. An optional SF Symbol icon is shown
/// to the left of the label in the idle state.
///
/// ## Usage
///
/// ```swift
/// LoadingButton(
///     action: { performAction() },
///     isLoading: isLoading,
///     isEnabled: canProceed && !isLoading,
///     icon: "paperplane",
///     text: "Send Transfer",
///     loadingText: "Sending...",
///     style: .filled
/// )
/// ```
struct LoadingButton: View {
    let action: () -> Void
    let isLoading: Bool
    let isEnabled: Bool
    let icon: String?
    let text: String
    let loadingText: String
    let style: ButtonStyleType

    enum ButtonStyleType {
        /// Solid background with white text. Background color is controlled by the style.
        case filled
        /// Transparent background with a colored border and text.
        case outlined
        /// Solid red background with white text for destructive actions.
        case error
    }

    var body: some View {
        Button(action: action) {
            HStack(spacing: 8) {
                if isLoading {
                    ProgressView()
                        .controlSize(.small)
                        .tint(style == .outlined ? Material3Colors.primary : .white)
                    Text(loadingText)
                        .font(.system(size: 15, weight: .medium))
                } else {
                    if let icon = icon {
                        Image(systemName: icon)
                            .font(.system(size: 15))
                    }
                    Text(text)
                        .font(.system(size: 15, weight: .medium))
                }
            }
            .frame(maxWidth: .infinity)
            .frame(height: 44)
            .foregroundColor(resolvedForegroundColor)
            .background(resolvedBackground)
            .cornerRadius(8)
            .overlay(resolvedOverlay)
        }
        .disabled(!isEnabled || isLoading)
        .buttonStyle(.plain)
        .opacity((isEnabled && !isLoading) ? 1.0 : 0.5)
    }

    // MARK: - Style resolution

    private var resolvedForegroundColor: Color {
        switch style {
        case .filled:  return .white
        case .outlined: return Material3Colors.primary
        case .error:   return .white
        }
    }

    private var resolvedBackground: Color {
        switch style {
        case .filled:  return Material3Colors.primary
        case .outlined: return .clear
        case .error:   return Material3Colors.error
        }
    }

    @ViewBuilder
    private var resolvedOverlay: some View {
        if style == .outlined {
            RoundedRectangle(cornerRadius: 8)
                .stroke(Material3Colors.primary, lineWidth: 1.5)
        }
    }
}

// MARK: - Convenience initializer (icon optional)

extension LoadingButton {
    /// Creates a `LoadingButton` without an icon.
    init(
        action: @escaping () -> Void,
        isLoading: Bool,
        isEnabled: Bool,
        text: String,
        loadingText: String,
        style: ButtonStyleType = .filled
    ) {
        self.init(
            action: action,
            isLoading: isLoading,
            isEnabled: isEnabled,
            icon: nil,
            text: text,
            loadingText: loadingText,
            style: style
        )
    }
}
