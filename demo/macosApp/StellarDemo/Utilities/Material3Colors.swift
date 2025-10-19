import SwiftUI

// MARK: - Material 3 Color Scheme

struct Material3Colors {
    // Primary colors
    static let primaryContainer = Color(red: 0.85, green: 0.90, blue: 1.0)
    static let onPrimaryContainer = Color(red: 0.0, green: 0.11, blue: 0.36)
    static let primary = Color(red: 0.13, green: 0.35, blue: 0.78)

    // Secondary colors
    static let secondaryContainer = Color(red: 0.85, green: 0.92, blue: 0.96)
    static let onSecondaryContainer = Color(red: 0.05, green: 0.20, blue: 0.30)

    // Tertiary colors (for secret seed)
    static let tertiaryContainer = Color(red: 0.98, green: 0.92, blue: 0.85)
    static let onTertiaryContainer = Color(red: 0.35, green: 0.18, blue: 0.03)

    // Error colors
    static let errorContainer = Color(red: 1.0, green: 0.85, blue: 0.85)
    static let onErrorContainer = Color(red: 0.4, green: 0.0, blue: 0.0)

    // Success colors (matching primaryContainer for success states)
    static let successContainer = Color(red: 0.85, green: 0.95, blue: 0.87)
    static let onSuccessContainer = Color(red: 0.0, green: 0.3, blue: 0.1)

    // Surface colors
    static let surface = Color(white: 0.98)
    static let surfaceVariant = Color(white: 0.94)
    static let onSurface = Color(white: 0.1)
    static let onSurfaceVariant = Color(white: 0.4)

    // Card elevation shadow
    static let cardShadow = Color.black.opacity(0.08)

    // Toast colors
    static let toastBackground = Color(red: 0.2, green: 0.2, blue: 0.22)
    static let onToast = Color.white
}
