//
//  CopyableField.swift
//  Smart Account Demo
//
//  Copyright (c) 2026 Soneso. All rights reserved.
//

import SwiftUI

/// A read-only display field whose value can be copied to the clipboard by clicking.
///
/// Shows a label, the value, and a "Tap to copy" affordance hint. Clicking anywhere
/// on the view copies `value` to the system clipboard. An optional `onCopy` closure
/// is called after the copy so callers can show a confirmation toast.
///
/// ## Usage
///
/// ```swift
/// CopyableField(
///     label: "Contract ID",
///     value: contractId,
///     textColor: Material3Colors.onSurface,
///     labelColor: Material3Colors.onSurfaceVariant,
///     monospace: true
/// )
/// ```
struct CopyableField: View {
    let label: String
    let value: String
    let textColor: Color
    let labelColor: Color
    let monospace: Bool
    let onCopy: (() -> Void)?

    @State private var justCopied = false

    init(
        label: String,
        value: String,
        textColor: Color = Material3Colors.onSurface,
        labelColor: Color = Material3Colors.onSurfaceVariant,
        monospace: Bool = false,
        onCopy: (() -> Void)? = nil
    ) {
        self.label = label
        self.value = value
        self.textColor = textColor
        self.labelColor = labelColor
        self.monospace = monospace
        self.onCopy = onCopy
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(label)
                .font(.caption)
                .fontWeight(.bold)
                .foregroundColor(labelColor)

            Text(value)
                .font(monospace
                    ? .system(.callout, design: .monospaced)
                    : .callout)
                .foregroundColor(textColor)
                .textSelection(.disabled)
                .fixedSize(horizontal: false, vertical: true)

            Text(justCopied ? "Copied!" : "Tap to copy")
                .font(.caption2)
                .foregroundColor(labelColor.opacity(0.6))
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .contentShape(Rectangle())
        .onTapGesture {
            ClipboardHelper.copy(value)
            justCopied = true
            onCopy?()
            DispatchQueue.main.asyncAfter(deadline: .now() + 1.5) {
                justCopied = false
            }
        }
    }
}
