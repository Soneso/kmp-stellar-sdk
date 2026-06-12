//
//  ValidationTextField.swift
//  Smart Account Demo
//
//  Copyright (c) 2026 Soneso. All rights reserved.
//

import SwiftUI

/// A labeled text field with inline validation error display.
///
/// Renders a label above the field and an error message below when `error` is non-nil;
/// while there is no error, an optional helper text is shown in its place.
/// Supports monospace font for Stellar addresses and a disabled state with reduced opacity.
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
///     isEnabled: true
/// )
/// ```
struct ValidationTextField: View {
    let label: String
    @Binding var text: String
    let error: String?
    let placeholder: String
    let helperText: String?
    let isMonospace: Bool
    let isEnabled: Bool

    init(
        label: String,
        text: Binding<String>,
        error: String? = nil,
        placeholder: String = "",
        helperText: String? = nil,
        isMonospace: Bool = false,
        isEnabled: Bool = true
    ) {
        self.label = label
        self._text = text
        self.error = error
        self.placeholder = placeholder
        self.helperText = helperText
        self.isMonospace = isMonospace
        self.isEnabled = isEnabled
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            // Label
            Text(label)
                .font(.system(size: 12, weight: .medium))
                .foregroundColor(Material3Colors.onSurfaceVariant)

            // Input container
            HStack(spacing: 0) {
                TextField(placeholder, text: $text)
                    .textFieldStyle(.plain)
                    .font(isMonospace
                        ? .system(.callout, design: .monospaced)
                        : .callout)
                    .padding(10)
                    .disabled(!isEnabled)
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

            // Error message, or helper text while the input is valid
            if let error = error {
                Text(error)
                    .font(.footnote)
                    .foregroundColor(Material3Colors.error)
            } else if let helperText = helperText {
                Text(helperText)
                    .font(.footnote)
                    .foregroundColor(Material3Colors.onSurfaceVariant)
            }
        }
    }
}
