//
//  ContentView.swift
//  Stellar KMP SDK iOS Sample
//
//  Main UI for testing the Stellar SDK
//

import SwiftUI
import stellar_sdk

struct ContentView: View {
    @StateObject private var testRunner = SimpleStellarTests()
    @State private var generatedKeypair: KeyPair?
    @State private var generatedAccountId = ""
    @State private var showingTests = false

    var body: some View {
        NavigationView {
            ScrollView {
                VStack(spacing: 20) {
                    // Header
                    VStack(spacing: 8) {
                        Image(systemName: "star.circle.fill")
                            .font(.system(size: 60))
                            .foregroundColor(.blue)

                        Text("Stellar KMP SDK")
                            .font(.title)
                            .fontWeight(.bold)

                        Text("iOS Integration Test")
                            .font(.subheadline)
                            .foregroundColor(.secondary)
                    }
                    .padding(.top, 20)

                    Divider()
                        .padding(.vertical, 10)

                    // Quick Actions
                    VStack(alignment: .leading, spacing: 15) {
                        Text("Quick Actions")
                            .font(.headline)

                        // Generate Keypair Button
                        Button(action: generateKeypair) {
                            HStack {
                                Image(systemName: "key.fill")
                                Text("Generate New Keypair")
                                Spacer()
                                Image(systemName: "chevron.right")
                            }
                            .padding()
                            .background(Color.blue.opacity(0.1))
                            .cornerRadius(10)
                        }

                        if !generatedAccountId.isEmpty {
                            VStack(alignment: .leading, spacing: 8) {
                                Text("Generated Account ID:")
                                    .font(.caption)
                                    .foregroundColor(.secondary)

                                Text(generatedAccountId)
                                    .font(.system(.caption, design: .monospaced))
                                    .padding(8)
                                    .background(Color.gray.opacity(0.1))
                                    .cornerRadius(5)

                                Button(action: {
                                    UIPasteboard.general.string = generatedAccountId
                                }) {
                                    HStack {
                                        Image(systemName: "doc.on.doc")
                                        Text("Copy")
                                    }
                                    .font(.caption)
                                }
                            }
                        }

                        // Run Tests Button
                        Button(action: {
                            showingTests = true
                            testRunner.runTests()
                        }) {
                            HStack {
                                Image(systemName: "checkmark.circle.fill")
                                Text("Run Comprehensive Tests")
                                Spacer()
                                if testRunner.isRunning {
                                    ProgressView()
                                } else {
                                    Image(systemName: "chevron.right")
                                }
                            }
                            .padding()
                            .background(Color.green.opacity(0.1))
                            .cornerRadius(10)
                        }
                        .disabled(testRunner.isRunning)
                    }
                    .padding(.horizontal)

                    Divider()
                        .padding(.vertical, 10)

                    // Test Results Summary
                    if showingTests && !testRunner.testResults.isEmpty {
                        VStack(alignment: .leading, spacing: 15) {
                            Text("Test Results")
                                .font(.headline)

                            // Individual Test Results
                            ForEach(testRunner.testResults) { result in
                                SimpleTestResultRow(result: result)
                            }
                        }
                        .padding(.horizontal)
                    }

                    // Info Section
                    VStack(alignment: .leading, spacing: 12) {
                        Text("About")
                            .font(.headline)

                        InfoRow(
                            icon: "cpu",
                            title: "Platform",
                            value: "iOS Native"
                        )

                        InfoRow(
                            icon: "lock.shield",
                            title: "Crypto Library",
                            value: KeyPair.Companion().getCryptoLibraryName()
                        )

                        InfoRow(
                            icon: "checkmark.seal",
                            title: "Algorithm",
                            value: "Ed25519 (RFC 8032)"
                        )

                        InfoRow(
                            icon: "doc.text",
                            title: "Implementation",
                            value: "Shared nativeMain code"
                        )
                    }
                    .padding(.horizontal)
                    .padding(.bottom, 30)
                }
            }
            .navigationTitle("Stellar SDK")
        }
    }

    private func generateKeypair() {
        do {
            let keypair = KeyPair.Companion().random()
            generatedKeypair = keypair
            generatedAccountId = keypair.getAccountId()
        } catch {
            generatedAccountId = "Error: \(error.localizedDescription)"
        }
    }
}

struct SimpleTestResultRow: View {
    let result: SimpleTestResult

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                Image(systemName: result.passed ? "checkmark.circle.fill" : "xmark.circle.fill")
                    .foregroundColor(result.passed ? .green : .red)

                Text(result.name)
                    .font(.subheadline)
                    .fontWeight(.medium)

                Spacer()
            }

            Text(result.message)
                .font(.caption)
                .foregroundColor(.secondary)
                .lineLimit(3)
        }
        .padding()
        .background(result.passed ? Color.green.opacity(0.05) : Color.red.opacity(0.05))
        .cornerRadius(10)
    }
}

struct InfoRow: View {
    let icon: String
    let title: String
    let value: String

    var body: some View {
        HStack {
            Image(systemName: icon)
                .foregroundColor(.blue)
                .frame(width: 30)

            VStack(alignment: .leading, spacing: 2) {
                Text(title)
                    .font(.caption)
                    .foregroundColor(.secondary)

                Text(value)
                    .font(.subheadline)
            }

            Spacer()
        }
        .padding(.vertical, 4)
    }
}

#Preview {
    ContentView()
}