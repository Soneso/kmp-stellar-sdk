//
//  BalanceRows.swift
//  Smart Account Demo
//
//  Copyright (c) 2026 Soneso. All rights reserved.
//

import SwiftUI

/// Labeled balance display: a caption-style label above one or more
/// pre-formatted balance value lines (e.g. "10.0 XLM", "5.0 DEMO").
///
/// The label and value fonts and colors are supplied by the call site so the
/// component matches each screen's card styling exactly. Values render
/// vertically by default; pass `horizontal: true` to lay them out in a single
/// row (used by the transfer cards).
struct BalanceRows: View {
    let label: String
    var labelFont: Font = .caption.bold()
    let labelColor: Color
    /// Pre-formatted balance strings; optional balances are filtered out at
    /// the call site before construction.
    let values: [String]
    let valueFont: Font
    let valueColor: Color
    var horizontal: Bool = false

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(label)
                .font(labelFont)
                .foregroundColor(labelColor)

            if horizontal {
                HStack(spacing: 16) {
                    valueTexts
                }
            } else {
                valueTexts
            }
        }
    }

    private var valueTexts: some View {
        ForEach(Array(values.enumerated()), id: \.offset) { _, value in
            Text(value)
                .font(valueFont)
                .foregroundColor(valueColor)
        }
    }
}
