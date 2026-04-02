//
//  ToastManager.swift
//  StellarSmartDemo
//
//  Copyright (c) 2026 Soneso. All rights reserved.
//

import SwiftUI

// MARK: - Toast Manager

/// Shared observable state for presenting ephemeral toast messages.
///
/// Inject a single instance via `@StateObject` at the root of the view hierarchy and consume
/// it with the `.toast(_:)` view modifier. Toasts auto-dismiss after the configured duration.
///
/// Usage:
/// ```swift
/// // Root view
/// @StateObject private var toastManager = ToastManager()
///
/// var body: some View {
///     ContentView()
///         .toast(toastManager)
/// }
///
/// // Any child view
/// toastManager.show("Copied to clipboard")
/// toastManager.show("Transaction failed", isError: true)
/// ```
@MainActor
final class ToastManager: ObservableObject {

    /// The currently displayed toast, or nil when no toast is visible.
    @Published var currentToast: ToastData?

    /// Presents a toast message.
    ///
    /// Any existing toast is dismissed before the new one appears, ensuring the entry
    /// animation always triggers.
    ///
    /// - Parameters:
    ///   - message: The text to display.
    ///   - isError: When `true`, the toast uses the error colour scheme.
    ///   - duration: How long the toast remains visible. Defaults to 2.5 seconds.
    func show(_ message: String, isError: Bool = false, duration: TimeInterval = 2.5) {
        currentToast = nil
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.05) { [weak self] in
            guard let self else { return }
            self.currentToast = ToastData(message: message, isError: isError)
            DispatchQueue.main.asyncAfter(deadline: .now() + duration) { [weak self] in
                self?.currentToast = nil
            }
        }
    }
}

// MARK: - Toast Data

/// Value type describing a single toast notification.
struct ToastData: Identifiable, Equatable {
    let id = UUID()
    let message: String
    let isError: Bool

    static func == (lhs: ToastData, rhs: ToastData) -> Bool {
        lhs.id == rhs.id
    }
}

// MARK: - Toast View

/// Compact notification banner rendered at the bottom of the screen.
struct ToastView: View {
    let toast: ToastData

    var body: some View {
        HStack(spacing: 10) {
            Image(systemName: toast.isError ? "xmark.circle.fill" : "checkmark.circle.fill")
                .font(.system(size: 15, weight: .semibold))
                .foregroundStyle(Material3Colors.onToast)

            Text(toast.message)
                .font(.system(size: 13, weight: .medium))
                .foregroundStyle(Material3Colors.onToast)
                .lineLimit(3)
                .multilineTextAlignment(.leading)
        }
        .padding(.horizontal, 18)
        .padding(.vertical, 12)
        .background(
            toast.isError
                ? Material3Colors.error.opacity(0.92)
                : Material3Colors.toastBackground
        )
        .clipShape(RoundedRectangle(cornerRadius: 20, style: .continuous))
        .shadow(color: Color.black.opacity(0.18), radius: 8, x: 0, y: 4)
    }
}

// MARK: - Toast Modifier

/// View modifier that overlays a `ToastView` at the bottom of any view.
struct ToastModifier: ViewModifier {
    @ObservedObject var toastManager: ToastManager

    func body(content: Content) -> some View {
        ZStack {
            content

            if let toast = toastManager.currentToast {
                VStack {
                    Spacer()
                    ToastView(toast: toast)
                        .transition(.move(edge: .bottom).combined(with: .opacity))
                        .padding(.bottom, 28)
                }
                .animation(.spring(response: 0.38, dampingFraction: 0.78), value: toastManager.currentToast)
            }
        }
    }
}

// MARK: - View Extension

extension View {

    /// Attaches toast presentation behaviour driven by `toastManager`.
    ///
    /// - Parameter toastManager: The shared `ToastManager` instance.
    func toast(_ toastManager: ToastManager) -> some View {
        modifier(ToastModifier(toastManager: toastManager))
    }
}
