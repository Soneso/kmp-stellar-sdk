import SwiftUI
import shared

struct ContentView: View {
    @StateObject private var viewModel = StellarViewModel()

    var body: some View {
        NavigationView {
            ScrollView {
                VStack(spacing: 16) {
                    // SDK Info Card
                    SDKInfoCard()

                    // KeyPair Generation Card
                    KeyPairCard(viewModel: viewModel)

                    // Test Suite Card
                    TestSuiteCard(viewModel: viewModel)
                }
                .padding()
            }
            .navigationTitle("Stellar KMP Sample")
        }
    }
}

struct SDKInfoCard: View {
    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Stellar SDK for Kotlin Multiplatform")
                .font(.headline)

            Text("This sample demonstrates shared business logic across Android, iOS, and Web platforms.")
                .font(.subheadline)
                .foregroundColor(.secondary)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding()
        .background(Color(UIColor.secondarySystemGroupedBackground))
        .cornerRadius(12)
    }
}

struct KeyPairCard: View {
    @ObservedObject var viewModel: StellarViewModel

    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            Text("KeyPair Operations")
                .font(.headline)

            HStack(spacing: 12) {
                Button("Random") {
                    viewModel.generateRandom()
                }
                .buttonStyle(.borderedProminent)
                .frame(maxWidth: .infinity)

                Button("From Seed") {
                    viewModel.generateFromSeed()
                }
                .buttonStyle(.borderedProminent)
                .frame(maxWidth: .infinity)
            }

            if let keypair = viewModel.keypair {
                Divider()
                KeyPairInfoDisplay(keypair: keypair)
            }
        }
        .padding()
        .background(Color(UIColor.secondarySystemGroupedBackground))
        .cornerRadius(12)
    }
}

struct KeyPairInfoDisplay: View {
    let keypair: KeyPairInfo

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            InfoRow(label: "Account ID:", value: keypair.accountId)

            if let secretSeed = keypair.secretSeed {
                InfoRow(label: "Secret Seed:", value: "S" + String(repeating: "*", count: 55), isSecret: true)
            }

            InfoRow(label: "Can Sign:", value: keypair.canSign ? "Yes" : "No")
            InfoRow(label: "Crypto Library:", value: keypair.cryptoLibrary)
        }
    }
}

struct InfoRow: View {
    let label: String
    let value: String
    var isSecret: Bool = false

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(label)
                .font(.caption)
                .foregroundColor(.secondary)

            Text(value)
                .font(isSecret ? .body : .system(.body, design: .monospaced))
        }
    }
}

struct TestSuiteCard: View {
    @ObservedObject var viewModel: StellarViewModel

    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            HStack {
                Text("Test Suite")
                    .font(.headline)

                Spacer()

                Button(viewModel.isRunningTests ? "Running..." : "Run Tests") {
                    viewModel.runTests()
                }
                .buttonStyle(.borderedProminent)
                .disabled(viewModel.isRunningTests)
            }

            if !viewModel.testResults.isEmpty {
                let passedCount = viewModel.testResults.filter { $0.passed }.count
                let totalCount = viewModel.testResults.count

                Text("Results: \(passedCount)/\(totalCount) tests passed")
                    .font(.subheadline)
                    .foregroundColor(passedCount == totalCount ? .green : .red)

                ForEach(viewModel.testResults, id: \.name) { result in
                    TestResultItem(result: result)
                }
            }
        }
        .padding()
        .background(Color(UIColor.secondarySystemGroupedBackground))
        .cornerRadius(12)
    }
}

struct TestResultItem: View {
    let result: TestResult

    var body: some View {
        HStack(spacing: 12) {
            Text(result.passed ? "✓" : "✗")
                .font(.title3)
                .foregroundColor(result.passed ? .green : .red)

            VStack(alignment: .leading, spacing: 4) {
                Text(result.name)
                    .font(.body)

                Text(result.message)
                    .font(.caption)
                    .foregroundColor(.secondary)
            }

            Spacer()

            Text("\(result.duration)ms")
                .font(.caption)
                .foregroundColor(.secondary)
        }
    }
}

@MainActor
class StellarViewModel: ObservableObject {
    private let demo = StellarDemo()

    @Published var keypair: KeyPairInfo?
    @Published var testResults: [TestResult] = []
    @Published var isRunningTests = false

    func generateRandom() {
        Task {
            do {
                let kp = try await demo.generateRandomKeyPair()
                self.keypair = kp
            } catch {
                print("Error generating random keypair: \(error)")
            }
        }
    }

    func generateFromSeed() {
        Task {
            do {
                let testSeed = "SDJHRQF4GCMIIKAAAQ6IHY42X73FQFLHUULAPSKKD4DFDM7UXWWCRHBE"
                // Use generateRandom for now since Result type doesn't bridge well to Swift
                let kp = try await demo.generateRandomKeyPair()
                self.keypair = kp
            } catch {
                print("Error generating keypair from seed: \(error)")
            }
        }
    }

    func runTests() {
        Task {
            self.isRunningTests = true
            do {
                let results = try await demo.runTestSuite()
                self.testResults = results
            } catch {
                print("Error running tests: \(error)")
            }
            self.isRunningTests = false
        }
    }
}

struct ContentView_Previews: PreviewProvider {
    static var previews: some View {
        ContentView()
    }
}
