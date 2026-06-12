//
//  Material3Colors.swift
//  StellarSmartDemo
//
//  Copyright (c) 2026 Soneso. All rights reserved.
//

import SwiftUI

// MARK: - Material 3 Color Scheme (Smart Account Palette)

/// Static color constants for the Smart Account Demo.
///
/// All colors are defined for light mode only. The app forces light mode throughout;
/// no dark mode variants are provided.
///
/// Color roles follow the Material 3 naming convention. Where a role deviates from the
/// default Material 3 mapping (e.g. `surfaceVariant = SmartBlueContainer`), the reason
/// is documented inline to match the Compose `Theme.kt` configuration.
struct Material3Colors {

    // MARK: - Primary (SmartBlue)

    /// SmartBlue — #0A4FD6. Used as the primary action colour.
    static let primary = Color(red: 0.039, green: 0.310, blue: 0.839)

    /// SmartBlueContainer — #E8F1FF. Background for primary-tinted containers.
    static let primaryContainer = Color(red: 0.910, green: 0.945, blue: 1.0)

    /// SmartBlueDark — #0639A3. Text and icons on top of primaryContainer.
    static let onPrimaryContainer = Color(red: 0.024, green: 0.224, blue: 0.639)

    // MARK: - Secondary (SmartPurple)

    /// SmartPurple — #5E3FBE. Used as the secondary accent colour.
    static let secondary = Color(red: 0.369, green: 0.247, blue: 0.745)

    /// SmartPurpleContainer — #F3EFFF. Background for secondary-tinted containers.
    static let secondaryContainer = Color(red: 0.953, green: 0.937, blue: 1.0)

    /// SmartPurpleDark — #3D2373. Text and icons on top of secondaryContainer.
    static let onSecondaryContainer = Color(red: 0.239, green: 0.137, blue: 0.451)

    // MARK: - Error (SmartRed)

    /// SmartRed — #DC2626. Destructive actions and error states.
    static let error = Color(red: 0.863, green: 0.149, blue: 0.149)

    /// SmartRedContainer — #FEF2F2. Background for error containers.
    static let errorContainer = Color(red: 0.996, green: 0.949, blue: 0.949)

    /// SmartRedDark — #991B1B. Text and icons on top of errorContainer.
    static let onErrorContainer = Color(red: 0.600, green: 0.106, blue: 0.106)

    // MARK: - Surface & Background

    /// SurfaceLight — #FFFFFF. Card and sheet backgrounds.
    static let surface = Color.white

    /// SurfaceVariantLight — #F9FAFB. Window background colour.
    ///
    /// Note: this maps to Material 3 `background`, NOT to `surfaceVariant`.
    /// `surfaceVariant` maps to `primaryContainer` (SmartBlueContainer) per `Theme.kt`.
    static let background = Color(red: 0.976, green: 0.980, blue: 0.984)

    /// SmartBlueContainer — #E8F1FF. Material 3 `surfaceVariant` per `Theme.kt`.
    ///
    /// Intentionally the same value as `primaryContainer`; assigned to `surfaceVariant`
    /// in the Compose theme so SwiftUI must mirror that mapping.
    static let surfaceVariant = Color(red: 0.910, green: 0.945, blue: 1.0)

    /// VoidBlack — #0A0E1A. Primary text on surfaces and backgrounds.
    static let onSurface = Color(red: 0.039, green: 0.055, blue: 0.102)

    /// SpaceMedium — #2D3548. Secondary text and icons on surfaces.
    static let onSurfaceVariant = Color(red: 0.176, green: 0.208, blue: 0.282)

    /// OutlineLight — #E5E7EB. Dividers, borders, and separator lines.
    static let outline = Color(red: 0.898, green: 0.906, blue: 0.922)

    // MARK: - Card Shadow

    /// Subtle card elevation shadow.
    static let cardShadow = Color.black.opacity(0.07)

    // MARK: - Toast

    /// Dark overlay used as the toast background.
    static let toastBackground = Color(red: 0.133, green: 0.161, blue: 0.220)

    /// Text placed on the toast background.
    static let onToast = Color.white

    // MARK: - Signer Type Colours

    /// Passkey signer badge — purple #9C27B0.
    static let signerPasskey = Color(red: 0.612, green: 0.153, blue: 0.690)

    /// Delegated signer badge — blue #2196F3.
    static let signerDelegated = Color(red: 0.129, green: 0.588, blue: 0.953)

    /// Ed25519 signer badge — teal #009688.
    static let signerEd25519 = Color(red: 0.000, green: 0.588, blue: 0.533)

    /// Default / unknown signer badge — grey #607D8B.
    static let signerDefault = Color(red: 0.376, green: 0.490, blue: 0.545)

    // MARK: - Activity Log Level Colours

    /// Info-level log entries — #2196F3.
    static let logInfo = Color(red: 0.129, green: 0.588, blue: 0.953)

    /// Success-level log entries — #4CAF50.
    static let logSuccess = Color(red: 0.298, green: 0.686, blue: 0.314)

    /// Error-level log entries — #F44336.
    static let logError = Color(red: 0.957, green: 0.263, blue: 0.212)

    // MARK: - Policy Type Colours

    /// Threshold policy accent — #E65100.
    static let policyThreshold = Color(red: 0.902, green: 0.318, blue: 0.000)

    /// Spending-limit policy accent — #1565C0.
    static let policySpendingLimit = Color(red: 0.082, green: 0.396, blue: 0.753)

    /// Weighted-threshold policy accent — #6A1B9A.
    static let policyWeightedThreshold = Color(red: 0.416, green: 0.106, blue: 0.604)

    // MARK: - Context Rule Badge Colours

    /// Signers count badge background — #2196F3 at 15% alpha.
    static let badgeSignersBackground = Color(red: 0.129, green: 0.588, blue: 0.953).opacity(0.15)

    /// Signers count badge foreground — #1565C0.
    static let badgeSignersText = Color(red: 0.082, green: 0.396, blue: 0.753)

    /// Policies count badge background — #4CAF50 at 15% alpha.
    static let badgePoliciesBackground = Color(red: 0.298, green: 0.686, blue: 0.314).opacity(0.15)

    /// Policies count badge foreground — #2E7D32.
    static let badgePoliciesText = Color(red: 0.180, green: 0.490, blue: 0.196)

    /// Expiry badge background — #FF9800 at 15% alpha.
    static let badgeExpiryBackground = Color(red: 1.000, green: 0.596, blue: 0.000).opacity(0.15)

    /// Expiry badge foreground — #E65100.
    static let badgeExpiryText = Color(red: 0.902, green: 0.318, blue: 0.000)

    // MARK: - Warning Colours

    /// Warning text and accents — Orange 900 #E65100. Used by the
    /// wallet-not-deployed cards.
    static let warningText = Color(red: 0.902, green: 0.318, blue: 0.000)

    /// Warning container background — Orange 50 #FFF3E0.
    static let warningBackground = Color(red: 1.0, green: 0.953, blue: 0.878)
}
