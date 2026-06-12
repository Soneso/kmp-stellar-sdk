//
//  SignerBadge.swift
//  Smart Account Demo
//
//  Copyright (c) 2026 Soneso. All rights reserved.
//

import SwiftUI

/// Color-coded pill badge identifying a signer type.
///
/// Each signer type receives a distinctive background color to make rows visually
/// scannable. The label defaults to the canonical capitalized type name but can be
/// overridden via `text`.
///
/// ## Usage
///
/// ```swift
/// SignerBadge(signerType: "delegated")
/// SignerBadge(signerType: "passkey", text: "WebAuthn")
/// ```
struct SignerBadge: View {
    let signerType: String
    let text: String?

    init(signerType: String, text: String? = nil) {
        self.signerType = signerType
        self.text = text
    }

    var body: some View {
        TagPill(text: displayText, background: badgeColor)
    }

    // MARK: - Helpers

    private var displayText: String {
        if let text = text { return text }
        switch signerType.lowercased() {
        case "passkey":   return "Passkey"
        case "delegated": return "G-Address"
        case "ed25519":   return "Ed25519"
        default:          return signerType.capitalized
        }
    }

    private var badgeColor: Color {
        switch signerType.lowercased() {
        case "passkey":   return Material3Colors.signerPasskey
        case "delegated": return Material3Colors.signerDelegated
        case "ed25519":   return Material3Colors.signerEd25519
        default:          return Material3Colors.signerDefault
        }
    }
}
