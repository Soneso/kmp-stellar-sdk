//
//  ClipboardHelper.swift
//  StellarSmartDemo
//
//  Copyright (c) 2026 Soneso. All rights reserved.
//

import AppKit

/// Centralized clipboard functionality for the Smart Account Demo app.
///
/// Provides a simple, consistent interface for copying text to the system clipboard
/// across all screens. Eliminates duplicated `NSPasteboard` code in view files.
///
/// Usage:
/// ```swift
/// ClipboardHelper.copy("Text to copy")
/// ```
enum ClipboardHelper {

    /// Copies the provided text to the system clipboard.
    ///
    /// Clears any existing clipboard contents and sets the new string value.
    /// The operation is synchronous and completes immediately.
    ///
    /// - Parameter text: The text string to copy to the clipboard.
    static func copy(_ text: String) {
        let pasteboard = NSPasteboard.general
        pasteboard.clearContents()
        pasteboard.setString(text, forType: .string)
    }
}
