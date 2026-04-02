//
//  ValidationTextField.swift
//  Smart Account Demo
//
//  Copyright (c) 2026 Soneso. All rights reserved.
//

import SwiftUI

/// A labeled text field with inline validation error display.
///
/// Renders a label above the field and an error message below when `error` is non-nil.
/// Supports secure input (with show/hide toggle), monospace font for Stellar addresses,
/// and a disabled state with reduced opacity.
///
/// ## Usage
///
/// ```swift
/// ValidationTextField(
///     label: "Account ID",
///     text: $accountId,
///     error: validationErrors["accountId"],
///     placeholder: "G...",
///     isMonospace: true,
///     isEnabled: true,
///     isSecure: false
/// )
/// ```
struct ValidationTextField: View {
    let label: String
    @Binding var text: String
    let error: String?
    let placeholder: String
    let isMonospace: Bool
    let isEnabled: Bool
    let isSecure: Bool

    @State private var showSecret: Bool = false

    init(
        label: String,
        text: Binding<String>,
        error: String? = nil,
        placeholder: String = "",
        isMonospace: Bool = false,
        isEnabled: Bool = true,
        isSecure: Bool = false
    ) {
        self.label = label
        self._text = text
        self.error = error
        self.placeholder = placeholder
        self.isMonospace = isMonospace
        self.isEnabled = isEnabled
        self.isSecure = isSecure
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            // Label
            Text(label)
                .font(.system(size: 12, weight: .medium))
                .foregroundColor(Material3Colors.onSurfaceVariant)

            // Input container
            HStack(spacing: 0) {
                Group {
                    if isSecure && !showSecret {
                        SecureField(placeholder, text: $text)
                    } else {
                        TextField(placeholder, text: $text)
                    }
                }
                .textFieldStyle(.plain)
                .font(isMonospace
                    ? .system(.callout, design: .monospaced)
                    : .callout)
                .padding(10)
                .disabled(!isEnabled)

                // Visibility toggle for secure fields
                if isSecure {
                    Button(action: { showSecret.toggle() }) {
                        Image(systemName: showSecret ? "eye.slash.fill" : "eye.fill")
                            .font(.system(size: 14))
                            .foregroundColor(Material3Colors.onSurfaceVariant)
                            .frame(width: 36, height: 36)
                    }
                    .buttonStyle(.plain)
                    .help(showSecret ? "Hide" : "Show")
                }
            }
            .background(Material3Colors.surface)
            .cornerRadius(6)
            .overlay(
                RoundedRectangle(cornerRadius: 6)
                    .stroke(
                        error != nil
                            ? Material3Colors.error
                            : Material3Colors.outline,
                        lineWidth: 1
                    )
            )
            .opacity(isEnabled ? 1.0 : 0.5)

            // Error message
            if let error = error {
                Text(error)
                    .font(.footnote)
                    .foregroundColor(Material3Colors.error)
            }
        }
    }
}
