//
//  PrimaryButtonLabel.swift
//  Smart Account Demo
//
//  Copyright (c) 2026 Soneso. All rights reserved.
//

import SwiftUI

/// Static full-width label that reproduces the `LoadingButton` visual style
/// (15pt medium text, 44pt height, corner radius 8, 1.5pt outline stroke) for
/// controls that cannot use `LoadingButton` directly: `NavigationLink` labels
/// and plain `Button`s.
///
/// The call site supplies the resolved colors, including any disabled-state
/// dimming, since `NavigationLink` and `Button` manage their own enabled state.
struct PrimaryButtonLabel: View {

    enum Style {
        /// Solid background with the given text color.
        case filled(foreground: Color, background: Color)
        /// Transparent background with a colored border and matching text.
        case outlined(color: Color)
    }

    let text: String
    let style: Style

    var body: some View {
        switch style {
        case .filled(let foreground, let background):
            label
                .foregroundColor(foreground)
                .background(background)
                .cornerRadius(8)
        case .outlined(let color):
            label
                .foregroundColor(color)
                .overlay(
                    RoundedRectangle(cornerRadius: 8)
                        .stroke(color, lineWidth: 1.5)
                )
        }
    }

    private var label: some View {
        Text(text)
            .font(.system(size: 15, weight: .medium))
            .frame(maxWidth: .infinity)
            .frame(height: 44)
    }
}
