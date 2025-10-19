import SwiftUI

// MARK: - Toast Manager (Shared State)

@MainActor
class ToastManager: ObservableObject {
    @Published var currentToast: ToastData?

    func show(_ message: String, duration: TimeInterval = 2.5) {
        // Dismiss any existing toast
        currentToast = nil

        // Show new toast after a brief delay to ensure animation triggers
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.1) {
            self.currentToast = ToastData(message: message)

            // Auto-dismiss after duration
            DispatchQueue.main.asyncAfter(deadline: .now() + duration) {
                self.currentToast = nil
            }
        }
    }
}

struct ToastData: Identifiable, Equatable {
    let id = UUID()
    let message: String

    static func == (lhs: ToastData, rhs: ToastData) -> Bool {
        lhs.id == rhs.id
    }
}

// MARK: - Toast View

struct ToastView: View {
    let message: String

    var body: some View {
        HStack(spacing: 12) {
            Image(systemName: "checkmark.circle.fill")
                .font(.system(size: 16))
                .foregroundStyle(Material3Colors.onToast)

            Text(message)
                .font(.system(size: 14, weight: .medium))
                .foregroundStyle(Material3Colors.onToast)
        }
        .padding(.horizontal, 20)
        .padding(.vertical, 14)
        .background(Material3Colors.toastBackground)
        .cornerRadius(24)
        .shadow(color: Color.black.opacity(0.2), radius: 8, x: 0, y: 4)
    }
}

// MARK: - Toast Modifier

struct ToastModifier: ViewModifier {
    @ObservedObject var toastManager: ToastManager

    func body(content: Content) -> some View {
        ZStack {
            content

            // Toast overlay
            if let toast = toastManager.currentToast {
                VStack {
                    Spacer()

                    ToastView(message: toast.message)
                        .transition(.move(edge: .bottom).combined(with: .opacity))
                        .padding(.bottom, 32)
                }
                .animation(.spring(response: 0.4, dampingFraction: 0.8), value: toastManager.currentToast)
            }
        }
    }
}

extension View {
    func toast(_ toastManager: ToastManager) -> some View {
        modifier(ToastModifier(toastManager: toastManager))
    }
}
