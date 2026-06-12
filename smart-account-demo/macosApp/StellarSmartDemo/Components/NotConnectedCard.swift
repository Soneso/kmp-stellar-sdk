//
//  NotConnectedCard.swift
//  Smart Account Demo
//
//  Copyright (c) 2026 Soneso. All rights reserved.
//

import SwiftUI

/// Error-styled card shown by the operation screens (Transfer, Approve) when
/// no wallet is connected, followed by a filled "Go Back" button.
///
/// Rendered as a `Group` so the card and the button participate directly in
/// the parent stack's spacing.
struct NotConnectedCard: View {
    /// Message explaining that a wallet connection is required.
    let message: String
    /// Invoked by the "Go Back" button (typically dismisses the screen).
    let onGoBack: () -> Void

    var body: some View {
        Group {
            VStack(alignment: .leading, spacing: 0) {
                Text(message)
                    .font(.system(size: 13))
                    .foregroundStyle(Material3Colors.onErrorContainer)
                    .frame(maxWidth: .infinity, alignment: .leading)
            }
            .padding(16)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(Material3Colors.errorContainer)
            .cornerRadius(8)

            LoadingButton(
                action: onGoBack,
                isLoading: false,
                isEnabled: true,
                text: "Go Back",
                loadingText: "",
                style: .filled
            )
        }
    }
}
