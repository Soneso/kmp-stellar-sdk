import SwiftUI

/// A reusable text field component for Stellar demo forms.
///
/// This component provides consistent styling, validation error display, and optional features
/// like secure text entry and copy buttons. It's designed to be used across all input forms
/// in the Stellar macOS demo app.
///
/// ## Features
///
/// - Consistent Material3 styling across all screens
/// - Built-in validation error display with red border
/// - Optional secure text entry for secret seeds
/// - Optional copy button for generated values
/// - Optional visibility toggle for secure fields
/// - Monospaced font for Stellar addresses (G..., S..., C...)
/// - Support for help text/descriptions
///
/// ## Usage
///
/// Basic text field:
/// ```swift
/// StellarTextField(
///     label: "Account ID",
///     placeholder: "G...",
///     text: $accountId,
///     error: validationErrors["accountId"]
/// )
/// ```
///
/// Secure text field with visibility toggle:
/// ```swift
/// StellarTextField(
///     label: "Secret Seed",
///     placeholder: "S...",
///     text: $secretSeed,
///     error: validationErrors["secretSeed"],
///     isSecure: true,
///     showVisibilityToggle: true,
///     isVisible: $showSecret
/// )
/// ```
///
/// Field with copy button:
/// ```swift
/// StellarTextField(
///     label: "Generated Account ID",
///     placeholder: "",
///     text: $accountId,
///     showCopyButton: true,
///     onCopy: {
///         ClipboardHelper.copyToClipboard(accountId)
///         toastManager.show("Copied to clipboard")
///     }
/// )
/// ```
///
/// Field with help text:
/// ```swift
/// StellarTextField(
///     label: "Asset Code",
///     placeholder: "USD, EUR, etc.",
///     text: $assetCode,
///     error: validationErrors["assetCode"],
///     helpText: "1-12 alphanumeric characters"
/// )
/// ```
struct StellarTextField: View {
    // MARK: - Properties

    /// Optional label text displayed above the field
    let label: String?

    /// Placeholder text shown when field is empty
    let placeholder: String

    /// Binding to the text value
    @Binding var text: String

    /// Optional error message to display below the field
    let error: String?

    /// Optional help text displayed below the field when no error is present
    let helpText: String?

    /// Whether this is a secure text entry field (for passwords/seeds)
    let isSecure: Bool

    /// Whether to show the visibility toggle button (for secure fields)
    let showVisibilityToggle: Bool

    /// Binding to control visibility state (for secure fields with toggle)
    @Binding var isVisible: Bool

    /// Whether to show a copy button
    let showCopyButton: Bool

    /// Action to perform when copy button is tapped
    let onCopy: (() -> Void)?

    /// Whether to use monospaced font (default: true for addresses)
    let useMonospacedFont: Bool

    /// Background color for the text field
    let backgroundColor: Color

    /// Whether the field is disabled
    let isDisabled: Bool

    // MARK: - Initializers

    /// Creates a StellarTextField with full customization.
    ///
    /// - Parameters:
    ///   - label: Optional label text displayed above the field
    ///   - placeholder: Placeholder text shown when field is empty
    ///   - text: Binding to the text value
    ///   - error: Optional error message to display below the field
    ///   - helpText: Optional help text displayed below the field when no error
    ///   - isSecure: Whether this is a secure text entry field
    ///   - showVisibilityToggle: Whether to show visibility toggle (requires isSecure: true)
    ///   - isVisible: Binding to control visibility state (used with showVisibilityToggle)
    ///   - showCopyButton: Whether to show a copy button
    ///   - onCopy: Action to perform when copy button is tapped
    ///   - useMonospacedFont: Whether to use monospaced font (default: true)
    ///   - backgroundColor: Background color for the text field
    ///   - isDisabled: Whether the field is disabled
    init(
        label: String? = nil,
        placeholder: String,
        text: Binding<String>,
        error: String? = nil,
        helpText: String? = nil,
        isSecure: Bool = false,
        showVisibilityToggle: Bool = false,
        isVisible: Binding<Bool> = .constant(false),
        showCopyButton: Bool = false,
        onCopy: (() -> Void)? = nil,
        useMonospacedFont: Bool = true,
        backgroundColor: Color = Material3Colors.surface,
        isDisabled: Bool = false
    ) {
        self.label = label
        self.placeholder = placeholder
        self._text = text
        self.error = error
        self.helpText = helpText
        self.isSecure = isSecure
        self.showVisibilityToggle = showVisibilityToggle
        self._isVisible = isVisible
        self.showCopyButton = showCopyButton
        self.onCopy = onCopy
        self.useMonospacedFont = useMonospacedFont
        self.backgroundColor = backgroundColor
        self.isDisabled = isDisabled
    }

    // MARK: - Body

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            // Label
            if let label = label {
                Text(label)
                    .font(.system(size: 12, weight: .medium))
                    .foregroundStyle(Material3Colors.onSurfaceVariant)
            }

            // Input field container
            HStack(spacing: 0) {
                // Text input
                Group {
                    if isSecure && !isVisible {
                        SecureField(placeholder, text: $text)
                            .textFieldStyle(.plain)
                            .font(useMonospacedFont ? .system(.body, design: .monospaced) : .system(.body))
                    } else {
                        TextField(placeholder, text: $text)
                            .textFieldStyle(.plain)
                            .font(useMonospacedFont ? .system(.body, design: .monospaced) : .system(.body))
                    }
                }
                .padding(12)
                .disabled(isDisabled)

                // Visibility toggle button (for secure fields)
                if isSecure && showVisibilityToggle {
                    Button(action: { isVisible.toggle() }) {
                        Image(systemName: isVisible ? "eye.slash.fill" : "eye.fill")
                            .font(.system(size: 14))
                            .foregroundStyle(Material3Colors.onSurfaceVariant)
                            .frame(width: 44, height: 44)
                    }
                    .buttonStyle(.plain)
                    .help(isVisible ? "Hide" : "Show")
                }

                // Copy button
                if showCopyButton && !text.isEmpty {
                    Button(action: { onCopy?() }) {
                        Image(systemName: "doc.on.doc")
                            .font(.system(size: 14))
                            .foregroundStyle(Material3Colors.primary)
                            .frame(width: 44, height: 44)
                    }
                    .buttonStyle(.plain)
                    .help("Copy to clipboard")
                }
            }
            .background(backgroundColor)
            .overlay(
                RoundedRectangle(cornerRadius: 4)
                    .stroke(
                        error != nil
                            ? Material3Colors.onErrorContainer
                            : Material3Colors.onSurfaceVariant.opacity(0.3),
                        lineWidth: 1
                    )
            )

            // Error message or help text
            if let error = error {
                Text(error)
                    .font(.system(size: 12))
                    .foregroundStyle(Material3Colors.onErrorContainer)
            } else if let helpText = helpText {
                Text(helpText)
                    .font(.system(size: 12))
                    .foregroundStyle(Material3Colors.onSurfaceVariant.opacity(0.7))
            }
        }
    }
}

// MARK: - Preview

#if DEBUG
struct StellarTextField_Previews: PreviewProvider {
    static var previews: some View {
        VStack(spacing: 24) {
            // Basic text field
            StellarTextField(
                label: "Account ID",
                placeholder: "G...",
                text: .constant("GABC7IJKLMNOPQRSTUVWXYZ234567ABCDEFGHIJKLMNOPQRSTUVWXYZ234")
            )

            // Text field with error
            StellarTextField(
                label: "Account ID",
                placeholder: "G...",
                text: .constant("INVALID"),
                error: "Account ID must be 56 characters long"
            )

            // Text field with help text
            StellarTextField(
                label: "Asset Code",
                placeholder: "USD, EUR, etc.",
                text: .constant("USDC"),
                helpText: "1-12 alphanumeric characters"
            )

            // Secure text field with visibility toggle
            StellarTextField(
                label: "Secret Seed",
                placeholder: "S...",
                text: .constant("SABC7IJKLMNOPQRSTUVWXYZ234567ABCDEFGHIJKLMNOPQRSTUVWXYZ234"),
                isSecure: true,
                showVisibilityToggle: true,
                isVisible: .constant(false)
            )

            // Text field with copy button
            StellarTextField(
                label: "Generated Account ID",
                placeholder: "",
                text: .constant("GABC7IJKLMNOPQRSTUVWXYZ234567ABCDEFGHIJKLMNOPQRSTUVWXYZ234"),
                showCopyButton: true,
                onCopy: { print("Copy tapped") }
            )

            // Non-monospaced text field
            StellarTextField(
                label: "Name",
                placeholder: "Alice",
                text: .constant("Alice"),
                useMonospacedFont: false
            )
        }
        .padding(16)
    }
}
#endif
