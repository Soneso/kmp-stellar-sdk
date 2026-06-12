//
//  ActivityLogView.swift
//  Smart Account Demo
//
//  Copyright (c) 2026 Soneso. All rights reserved.
//

import SwiftUI

/// Scrollable activity log section that displays timestamped, level-coded entries.
///
/// Each entry row shows a fixed-width timestamp, a level badge (INFO / OK / ERR),
/// and the message text. Clicking a row copies its message to the clipboard.
/// The header displays an entry count and a "Clear" button, disabled while the log is empty.
///
/// ## Usage
///
/// ```swift
/// ActivityLogView(
///     entries: appState.activityLog,
///     onClear: { appState.clearActivityLog(bridge: bridge) },
///     onCopyEntry: { message in toastManager.show("Copied") }
/// )
/// ```
struct ActivityLogView: View {
    let entries: [AppState.ActivityLogEntry]
    let onClear: () -> Void
    let onCopyEntry: (String) -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            // Header
            HStack {
                Text("Activity Log (\(entries.count))")
                    .font(.headline)
                    .fontWeight(.bold)
                    .foregroundColor(Material3Colors.onSurface)

                Spacer()

                Button(action: onClear) {
                    Text("Clear")
                        .font(.caption)
                        .foregroundColor(Material3Colors.primary)
                        .padding(.horizontal, 10)
                        .padding(.vertical, 4)
                        .overlay(
                            RoundedRectangle(cornerRadius: 4)
                                .stroke(Material3Colors.primary, lineWidth: 1)
                        )
                }
                .buttonStyle(.plain)
                .disabled(entries.isEmpty)
                .opacity(entries.isEmpty ? 0.5 : 1.0)
            }

            if entries.isEmpty {
                Text("No activity yet")
                    .font(.footnote)
                    .foregroundColor(Material3Colors.onSurfaceVariant.opacity(0.6))
                    .frame(maxWidth: .infinity, alignment: .center)
                    .padding(.vertical, 16)
            } else {
                ScrollView {
                    LazyVStack(spacing: 4) {
                        ForEach(entries) { entry in
                            LogEntryRow(
                                entry: entry,
                                onCopy: {
                                    ClipboardHelper.copy(entry.message)
                                    onCopyEntry(entry.message)
                                }
                            )
                        }
                    }
                }
                .frame(maxHeight: 480)
            }
        }
    }
}

// MARK: - Log entry row

private struct LogEntryRow: View {
    let entry: AppState.ActivityLogEntry
    let onCopy: () -> Void

    @State private var isHovered = false

    var body: some View {
        HStack(alignment: .top, spacing: 8) {
            // Timestamp — fixed width for visual alignment
            Text(formattedTimestamp)
                .font(.system(.footnote, design: .monospaced))
                .foregroundColor(Material3Colors.onSurfaceVariant)
                .frame(width: 70, alignment: .leading)
                .lineLimit(1)

            // Level badge
            levelBadge

            // Message
            Text(entry.message)
                .font(.footnote)
                .foregroundColor(Material3Colors.onSurface)
                .frame(maxWidth: .infinity, alignment: .leading)
                .fixedSize(horizontal: false, vertical: true)
        }
        .padding(.horizontal, 8)
        .padding(.vertical, 4)
        .background(isHovered ? Material3Colors.surfaceVariant.opacity(0.5) : Color.clear)
        .cornerRadius(4)
        .contentShape(Rectangle())
        .onHover { isHovered = $0 }
        .onTapGesture { onCopy() }
    }

    private var formattedTimestamp: String {
        let calendar = Calendar.current
        let hour = calendar.component(.hour, from: entry.timestamp)
        let minute = calendar.component(.minute, from: entry.timestamp)
        let second = calendar.component(.second, from: entry.timestamp)
        return String(format: "%02d:%02d:%02d", hour, minute, second)
    }

    private var levelBadge: some View {
        let (label, color) = levelStyle
        return TagPill(
            text: label,
            background: color,
            horizontalPadding: 6,
            verticalPadding: 2,
            cornerRadius: 3
        )
    }

    private var levelStyle: (String, Color) {
        switch entry.level.uppercased() {
        case "SUCCESS": return ("OK",   Material3Colors.logSuccess)
        case "ERROR":   return ("ERR",  Material3Colors.logError)
        default:        return ("INFO", Material3Colors.logInfo)
        }
    }
}
