//
//  ErrorCard.swift
//  Smart Account Demo
//
//  Copyright (c) 2026 Soneso. All rights reserved.
//

import SwiftUI

/// Error message card: error-container styling with selectable message text.
///
/// Wraps `InfoCard`'s error color set. The default font matches the 13pt style
/// used by the form screens; the list screens pass `.callout` to keep their
/// existing rendering.
struct ErrorCard: View {
    let message: String
    var font: Font = .system(size: 13)

    var body: some View {
        InfoCard(color: .error) {
            Text(message)
                .font(font)
                .foregroundColor(Material3Colors.onErrorContainer)
                .textSelection(.enabled)
                .frame(maxWidth: .infinity, alignment: .leading)
        }
    }
}
