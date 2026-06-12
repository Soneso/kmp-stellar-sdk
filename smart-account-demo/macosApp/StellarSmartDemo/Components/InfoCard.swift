//
//  InfoCard.swift
//  Smart Account Demo
//
//  Copyright (c) 2026 Soneso. All rights reserved.
//

import SwiftUI

/// Styled container card for descriptions and info sections.
///
/// Provides a rounded, colored background container with an optional title.
/// Use different `InfoCardColor` variants to communicate semantic meaning
/// (primary information, warnings, errors, etc.).
struct InfoCard<Content: View>: View {
    let title: String?
    let color: InfoCardColor
    @ViewBuilder let content: () -> Content

    enum InfoCardColor {
        /// Primary container background with on-primary-container text.
        case primary
        /// Surface variant background with on-surface-variant text.
        case variant
        /// Error container background with on-error-container text.
        case error
        /// Secondary container background with on-secondary-container text.
        case secondary
        /// Plain surface background with on-surface text.
        case surface

        var backgroundColor: Color {
            switch self {
            case .primary:   return Material3Colors.primaryContainer
            case .variant:   return Material3Colors.surfaceVariant
            case .error:     return Material3Colors.errorContainer
            case .secondary: return Material3Colors.secondaryContainer
            case .surface:   return Material3Colors.surface
            }
        }

        var textColor: Color {
            switch self {
            case .primary:   return Material3Colors.onPrimaryContainer
            case .variant:   return Material3Colors.onSurfaceVariant
            case .error:     return Material3Colors.onErrorContainer
            case .secondary: return Material3Colors.onSecondaryContainer
            case .surface:   return Material3Colors.onSurface
            }
        }
    }

    init(
        title: String? = nil,
        color: InfoCardColor = .surface,
        @ViewBuilder content: @escaping () -> Content
    ) {
        self.title = title
        self.color = color
        self.content = content
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            if let title = title {
                Text(title)
                    .font(.headline)
                    .fontWeight(.bold)
                    .foregroundColor(color.textColor)
            }
            content()
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(color.backgroundColor)
        .cornerRadius(8)
    }
}
