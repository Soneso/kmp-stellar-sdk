//
//  TagPill.swift
//  Smart Account Demo
//
//  Copyright (c) 2026 Soneso. All rights reserved.
//

import SwiftUI

/// Small rounded text badge ("pill") used for rule ID badges, count badges,
/// status chips, and log level tags.
///
/// The defaults match the most common badge shape (caption2 text on white,
/// 8x4 padding, corner radius 4); the compact variants override padding,
/// radius, and font at the call site.
struct TagPill: View {
    let text: String
    var font: Font = .caption2
    var foreground: Color = .white
    let background: Color
    var horizontalPadding: CGFloat = 8
    var verticalPadding: CGFloat = 4
    var cornerRadius: CGFloat = 4

    var body: some View {
        Text(text)
            .font(font)
            .foregroundColor(foreground)
            .padding(.horizontal, horizontalPadding)
            .padding(.vertical, verticalPadding)
            .background(background)
            .cornerRadius(cornerRadius)
    }
}
