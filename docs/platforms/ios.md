# iOS Platform Guide

This guide covers iOS-specific setup and usage for the Stellar KMP SDK.

## Table of Contents

- [Platform Overview](#platform-overview)
- [Installation](#installation)
- [Project Setup](#project-setup)
- [Swift Integration](#swift-integration)
- [SwiftUI Examples](#swiftui-examples)
- [UIKit Examples](#uikit-examples)
- [Cryptography Details](#cryptography-details)
- [Secure Storage](#secure-storage)
- [Networking](#networking)
- [Performance Optimization](#performance-optimization)
- [Troubleshooting](#troubleshooting)

## Platform Overview

The iOS implementation supports:
- **iOS 15.0+**
- **iPadOS 15.0+**
- **Mac Catalyst** apps

Key characteristics:
- Uses libsodium (native C) for cryptography
- Zero-overhead interop with Swift
- Full async/await support in Swift
- Thread-safe operations

## Installation

### Two Approaches Based on Your App Type

The setup differs depending on whether you're building a **Compose Multiplatform** app (Kotlin UI) or a **Native SwiftUI/UIKit** app:

#### Option A: Compose Multiplatform iOS (Recommended - 95% of cases)

**Use Maven artifact - works perfectly for Kotlin-based UI:**

```kotlin
// shared/build.gradle.kts
kotlin {
    iosX64()
    iosArm64()
    iosSimulatorArm64()

    listOf(iosX64(), iosArm64(), iosSimulatorArm64()).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "shared"
            isStatic = true
            // DON'T export if UI is in Kotlin (Compose)
            // Swift only launches the app, doesn't use SDK types
        }
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                // Maven artifact works perfectly
                implementation("com.soneso.stellar:stellar-sdk:0.2.1")
                implementation(compose.runtime)
                implementation(compose.material3)
            }
        }
    }
}
```

**Why this works:** All SDK calls happen in Kotlin. Swift just launches the Compose UI. No need to export SDK types to Swift.

#### Option B: Native SwiftUI/UIKit Apps

**Requires SDK as Git submodule + libsodium:**

##### 1. Add SDK as Submodule

```bash
# Add SDK as submodule
git submodule add https://github.com/Soneso/stellar-kmp-sdk.git stellar-sdk
git submodule update --init --recursive
```

##### 2: Swift Package Manager for libsodium

Add libsodium dependency in Xcode:

1. File → Add Package Dependencies
2. Search for: `https://github.com/jedisct1/swift-sodium`
3. Add the **swift-sodium** package
4. When prompted, select the **Clibsodium** product (not Sodium)
5. Add to your app target

Or in `Package.swift`:

```swift
// Package.swift
let package = Package(
    name: "YourApp",
    dependencies: [
        .package(
            url: "https://github.com/jedisct1/swift-sodium",
            from: "0.9.1"
        )
    ],
    targets: [
        .target(
            name: "YourApp",
            dependencies: [
                .product(name: "Clibsodium", package: "swift-sodium")
            ]
        )
    ]
)
```

### Option 2: CocoaPods

```ruby
# Podfile
platform :ios, '15.0'
use_frameworks!

target 'YourApp' do
  pod 'StellarSDK', :path => '../stellar-sdk'
  pod 'Sodium', '~> 0.9.1'
end
```

### Option 3: Direct Framework

```bash
# Build the framework
./gradlew :stellar-sdk:linkDebugFrameworkIosSimulatorArm64

# Or for device
./gradlew :stellar-sdk:linkDebugFrameworkIosArm64

# Build XCFramework for distribution
./build-xcframework.sh
```

Then add the framework to your Xcode project:
1. Drag the `.framework` or `.xcframework` to your project
2. Embed & Sign in your app target
3. Add libsodium via SPM as described above

## Project Setup

### Xcode Project Configuration

```xml
<!-- Info.plist -->
<key>NSAppTransportSecurity</key>
<dict>
    <!-- Only for development/testing -->
    <key>NSAllowsArbitraryLoads</key>
    <false/>
    <key>NSExceptionDomains</key>
    <dict>
        <key>horizon-testnet.stellar.org</key>
        <dict>
            <key>NSExceptionAllowsInsecureHTTPLoads</key>
            <false/>
            <key>NSIncludesSubdomains</key>
            <true/>
        </dict>
    </dict>
</dict>
```

### Build Settings

```
// In Xcode Build Settings
ENABLE_BITCODE = NO
VALID_ARCHS = arm64
SWIFT_VERSION = 5.9
IPHONEOS_DEPLOYMENT_TARGET = 15.0
```

## Swift Integration

### Basic Usage

```swift
import StellarSDK

class StellarManager {

    func generateKeypair() async throws -> KeyPair {
        return try await KeyPair.companion.random()
    }

    func importFromSeed(_ seed: String) async throws -> KeyPair {
        return try await KeyPair.companion.fromSecretSeed(seed: seed)
    }

    func createPublicOnlyKeypair(_ accountId: String) -> KeyPair {
        return KeyPair.companion.fromAccountId(accountId: accountId)
    }

    func signMessage(_ message: String, with keypair: KeyPair) async throws -> KotlinByteArray {
        let data = message.data(using: .utf8)!
        let kotlinData = IOSPlatformKt.toKotlinByteArray(data)
        return try await keypair.sign(data: kotlinData)
    }

    func verifySignature(
        message: String,
        signature: KotlinByteArray,
        publicKey: KeyPair
    ) async throws -> Bool {
        let data = message.data(using: .utf8)!
        let kotlinData = IOSPlatformKt.toKotlinByteArray(data)
        return try await publicKey.verify(data: kotlinData, signature: signature)
    }
}
```

### Extension Helpers

```swift
// Swift+Stellar.swift
import StellarSDK
import Foundation

extension KeyPair {
    var swiftAccountId: String {
        getAccountId()
    }

    var swiftSecretSeed: String? {
        getSecretSeed()?.toString()
    }

    var swiftCanSign: Bool {
        canSign()
    }
}

extension KotlinByteArray {
    var data: Data {
        return IOSPlatformKt.toData(self)
    }

    var hexString: String {
        data.map { String(format: "%02x", $0) }.joined()
    }
}

extension Data {
    var kotlinByteArray: KotlinByteArray {
        IOSPlatformKt.toKotlinByteArray(self)
    }
}
```

## SwiftUI Examples

### Wallet View

```swift
import SwiftUI
import StellarSDK

struct WalletView: View {
    @StateObject private var viewModel = WalletViewModel()

    var body: some View {
        NavigationView {
            VStack(spacing: 20) {
                if let keypair = viewModel.keypair {
                    AccountInfoView(keypair: keypair, balance: viewModel.balance)

                    HStack(spacing: 15) {
                        Button("Check Balance") {
                            Task {
                                await viewModel.checkBalance()
                            }
                        }
                        .buttonStyle(.borderedProminent)

                        Button("Send Payment") {
                            viewModel.showingPayment = true
                        }
                        .buttonStyle(.bordered)
                    }
                } else {
                    GenerateKeypairView { keypair in
                        viewModel.keypair = keypair
                    }
                }

                Spacer()
            }
            .padding()
            .navigationTitle("Stellar Wallet")
            .alert("Error", isPresented: $viewModel.showingError) {
                Button("OK", role: .cancel) { }
            } message: {
                Text(viewModel.errorMessage)
            }
            .sheet(isPresented: $viewModel.showingPayment) {
                PaymentView(sourceKeypair: viewModel.keypair!)
            }
        }
    }
}

struct AccountInfoView: View {
    let keypair: KeyPair
    let balance: String

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            Label("Account", systemImage: "person.circle")
                .font(.headline)

            Text(keypair.getAccountId())
                .font(.system(.caption, design: .monospaced))
                .textSelection(.enabled)
                .padding()
                .background(Color.gray.opacity(0.1))
                .cornerRadius(8)

            HStack {
                Label("Balance", systemImage: "bitcoinsign.circle")
                Text("\(balance) XLM")
                    .fontWeight(.semibold)
            }
        }
    }
}

struct GenerateKeypairView: View {
    let onGenerate: (KeyPair) -> Void
    @State private var isGenerating = false

    var body: some View {
        VStack(spacing: 20) {
            Image(systemName: "key.fill")
                .font(.system(size: 60))
                .foregroundColor(.blue)

            Text("No wallet yet")
                .font(.title2)

            Button(action: generateKeypair) {
                if isGenerating {
                    ProgressView()
                        .progressViewStyle(CircularProgressViewStyle())
                } else {
                    Text("Generate Wallet")
                }
            }
            .buttonStyle(.borderedProminent)
            .disabled(isGenerating)
        }
    }

    private func generateKeypair() {
        isGenerating = true
        Task {
            do {
                let keypair = try await KeyPair.companion.random()
                await MainActor.run {
                    onGenerate(keypair)
                    isGenerating = false
                }
            } catch {
                await MainActor.run {
                    isGenerating = false
                    // Handle error
                }
            }
        }
    }
}
```

### View Model

```swift
import SwiftUI
import StellarSDK
import Combine

@MainActor
class WalletViewModel: ObservableObject {
    @Published var keypair: KeyPair?
    @Published var balance: String = "0"
    @Published var transactions: [TransactionResponse] = []
    @Published var showingError = false
    @Published var showingPayment = false
    @Published var errorMessage = ""

    private var horizonServer: HorizonServer?

    init() {
        setupHorizon()
    }

    private func setupHorizon() {
        horizonServer = HorizonServer(
            serverUrl: "https://horizon-testnet.stellar.org"
        )
    }

    func checkBalance() async {
        guard let keypair = keypair else { return }

        do {
            let account = try await horizonServer?.loadAccount(
                accountId: keypair.getAccountId()
            )

            if let xlmBalance = account?.balances.first(where: {
                $0.assetType == "native"
            }) {
                self.balance = xlmBalance.balance
            }
        } catch {
            self.errorMessage = "Account not found. Fund it first!"
            self.showingError = true
            self.balance = "0"
        }
    }

    func fundTestAccount() async {
        guard let keypair = keypair else { return }

        do {
            let success = try await FriendBot.companion.fundAccount(
                accountId: keypair.getAccountId(),
                network: Network.testnet
            )

            if success {
                await checkBalance()
            }
        } catch {
            self.errorMessage = "Failed to fund account: \(error)"
            self.showingError = true
        }
    }

    deinit {
        horizonServer?.close()
    }
}
```

## UIKit Examples

### View Controller

```swift
import UIKit
import StellarSDK

class WalletViewController: UIViewController {

    @IBOutlet weak var accountLabel: UILabel!
    @IBOutlet weak var balanceLabel: UILabel!
    @IBOutlet weak var generateButton: UIButton!
    @IBOutlet weak var activityIndicator: UIActivityIndicatorView!

    private var keypair: KeyPair?
    private var horizonServer: HorizonServer!

    override func viewDidLoad() {
        super.viewDidLoad()
        setupUI()
        setupHorizon()
    }

    private func setupUI() {
        accountLabel.text = "No account yet"
        balanceLabel.text = "0 XLM"
        activityIndicator.hidesWhenStopped = true
    }

    private func setupHorizon() {
        horizonServer = HorizonServer(
            serverUrl: "https://horizon-testnet.stellar.org"
        )
    }

    @IBAction func generateKeypairTapped(_ sender: UIButton) {
        generateButton.isEnabled = false
        activityIndicator.startAnimating()

        Task {
            do {
                let keypair = try await KeyPair.companion.random()

                await MainActor.run {
                    self.keypair = keypair
                    self.updateUI()
                }

                // Fund on testnet
                _ = try await FriendBot.companion.fundAccount(
                    accountId: keypair.getAccountId(),
                    network: Network.testnet
                )

                await checkBalance()

            } catch {
                await MainActor.run {
                    self.showError(error)
                }
            }

            await MainActor.run {
                self.generateButton.isEnabled = true
                self.activityIndicator.stopAnimating()
            }
        }
    }

    private func updateUI() {
        guard let keypair = keypair else { return }

        accountLabel.text = keypair.getAccountId()
        generateButton.setTitle("Refresh Balance", for: .normal)
    }

    private func checkBalance() async {
        guard let keypair = keypair else { return }

        do {
            let account = try await horizonServer.loadAccount(
                accountId: keypair.getAccountId()
            )

            await MainActor.run {
                if let balance = account.balances.first {
                    self.balanceLabel.text = "\(balance.balance) XLM"
                }
            }
        } catch {
            print("Failed to load balance: \(error)")
        }
    }

    private func showError(_ error: Error) {
        let alert = UIAlertController(
            title: "Error",
            message: error.localizedDescription,
            preferredStyle: .alert
        )
        alert.addAction(UIAlertAction(title: "OK", style: .default))
        present(alert, animated: true)
    }

    deinit {
        horizonServer?.close()
    }
}
```

## Cryptography Details

### libsodium Integration

The iOS platform uses native libsodium via C interop:

```swift
// How the SDK uses libsodium internally
// (This is handled by the SDK, shown for reference)

// In nativeMain/kotlin
actual object Ed25519 {
    actual suspend fun generatePrivateKey(): ByteArray {
        val seed = ByteArray(32)
        randombytes_buf(seed.refTo(0), 32)
        return seed
    }

    actual suspend fun sign(
        message: ByteArray,
        privateKey: ByteArray
    ): ByteArray {
        val signature = ByteArray(64)
        crypto_sign_detached(
            signature.refTo(0),
            null,
            message.refTo(0),
            message.size.toULong(),
            privateKey.refTo(0)
        )
        return signature
    }
}
```

## Secure Storage

### Keychain Integration

```swift
import Security
import StellarSDK

class KeychainManager {

    private let service = "com.yourapp.stellar"

    func saveKeypair(_ keypair: KeyPair) throws {
        guard let seed = keypair.getSecretSeed() else {
            throw KeychainError.noSecretSeed
        }

        let seedString = seed.toString()
        let data = seedString.data(using: .utf8)!

        let query: [CFString: Any] = [
            kSecClass: kSecClassGenericPassword,
            kSecAttrService: service,
            kSecAttrAccount: keypair.getAccountId(),
            kSecValueData: data,
            kSecAttrAccessible: kSecAttrAccessibleAfterFirstUnlock
        ]

        // Delete any existing item
        SecItemDelete(query as CFDictionary)

        // Add new item
        let status = SecItemAdd(query as CFDictionary, nil)
        guard status == errSecSuccess else {
            throw KeychainError.saveFailed(status)
        }
    }

    func loadKeypair(accountId: String) async throws -> KeyPair {
        let query: [CFString: Any] = [
            kSecClass: kSecClassGenericPassword,
            kSecAttrService: service,
            kSecAttrAccount: accountId,
            kSecReturnData: true
        ]

        var result: AnyObject?
        let status = SecItemCopyMatching(query as CFDictionary, &result)

        guard status == errSecSuccess,
              let data = result as? Data,
              let seedString = String(data: data, encoding: .utf8) else {
            throw KeychainError.loadFailed(status)
        }

        return try await KeyPair.companion.fromSecretSeed(seed: seedString)
    }

    func deleteKeypair(accountId: String) throws {
        let query: [CFString: Any] = [
            kSecClass: kSecClassGenericPassword,
            kSecAttrService: service,
            kSecAttrAccount: accountId
        ]

        let status = SecItemDelete(query as CFDictionary)
        guard status == errSecSuccess || status == errSecItemNotFound else {
            throw KeychainError.deleteFailed(status)
        }
    }

    func listAccounts() -> [String] {
        let query: [CFString: Any] = [
            kSecClass: kSecClassGenericPassword,
            kSecAttrService: service,
            kSecMatchLimit: kSecMatchLimitAll,
            kSecReturnAttributes: true
        ]

        var result: AnyObject?
        let status = SecItemCopyMatching(query as CFDictionary, &result)

        guard status == errSecSuccess,
              let items = result as? [[CFString: Any]] else {
            return []
        }

        return items.compactMap { item in
            item[kSecAttrAccount] as? String
        }
    }
}

enum KeychainError: Error {
    case noSecretSeed
    case saveFailed(OSStatus)
    case loadFailed(OSStatus)
    case deleteFailed(OSStatus)
}
```

### Biometric Authentication

```swift
import LocalAuthentication

class BiometricAuth {

    func authenticateUser() async -> Bool {
        let context = LAContext()
        var error: NSError?

        guard context.canEvaluatePolicy(
            .deviceOwnerAuthenticationWithBiometrics,
            error: &error
        ) else {
            return false
        }

        do {
            return try await context.evaluatePolicy(
                .deviceOwnerAuthenticationWithBiometrics,
                localizedReason: "Authenticate to access your wallet"
            )
        } catch {
            return false
        }
    }

    func protectedKeypairAccess(
        accountId: String
    ) async throws -> KeyPair? {
        guard await authenticateUser() else {
            throw AuthError.biometricFailed
        }

        let keychain = KeychainManager()
        return try await keychain.loadKeypair(accountId: accountId)
    }
}

enum AuthError: Error {
    case biometricFailed
}
```

## Networking

### URLSession Integration

```swift
// The SDK uses Ktor internally, but you can also use URLSession
import Foundation

class StellarNetworkManager {

    func submitTransactionRaw(_ xdr: String) async throws -> Data {
        let url = URL(string: "https://horizon-testnet.stellar.org/transactions")!
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("application/x-www-form-urlencoded", forHTTPHeaderField: "Content-Type")

        let body = "tx=\(xdr.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed)!)"
        request.httpBody = body.data(using: .utf8)

        let (data, response) = try await URLSession.shared.data(for: request)

        guard let httpResponse = response as? HTTPURLResponse,
              (200...299).contains(httpResponse.statusCode) else {
            throw NetworkError.requestFailed
        }

        return data
    }
}

enum NetworkError: Error {
    case requestFailed
}
```

## Performance Optimization

### Concurrent Operations

```swift
import StellarSDK

class BatchOperations {

    func loadMultipleAccounts(_ accountIds: [String]) async -> [String: AccountResponse] {
        let server = HorizonServer(serverUrl: "https://horizon-testnet.stellar.org")
        defer { server.close() }

        return await withTaskGroup(of: (String, AccountResponse?).self) { group in
            for accountId in accountIds {
                group.addTask {
                    do {
                        let account = try await server.loadAccount(accountId: accountId)
                        return (accountId, account)
                    } catch {
                        return (accountId, nil)
                    }
                }
            }

            var results: [String: AccountResponse] = [:]
            for await (id, account) in group {
                if let account = account {
                    results[id] = account
                }
            }
            return results
        }
    }
}
```

### Caching

```swift
import Foundation

actor AccountCache {
    private var cache: [String: (account: AccountResponse, timestamp: Date)] = [:]
    private let expirationInterval: TimeInterval = 60 // 1 minute

    func get(_ accountId: String) -> AccountResponse? {
        guard let cached = cache[accountId] else { return nil }

        if Date().timeIntervalSince(cached.timestamp) > expirationInterval {
            cache.removeValue(forKey: accountId)
            return nil
        }

        return cached.account
    }

    func set(_ accountId: String, account: AccountResponse) {
        cache[accountId] = (account, Date())
    }

    func clear() {
        cache.removeAll()
    }
}
```

## Troubleshooting

### Common Issues

#### Framework Not Found

```bash
# Ensure framework is built for correct architecture
lipo -info stellar_sdk.framework/stellar_sdk
# Should show: arm64 (for device) or x86_64 (for simulator)

# Build for specific architecture
./gradlew :stellar-sdk:linkDebugFrameworkIosArm64
```

#### libsodium Symbols Not Found

```swift
// Ensure Clibsodium is added via SPM
// In Package.swift or Xcode:
dependencies: [
    .product(name: "Clibsodium", package: "swift-sodium")
]
```

#### Async/Await Issues

```swift
// Ensure minimum iOS 15.0
// For older iOS versions, use completion handlers:

extension KeyPair {
    static func randomWithCompletion(
        completion: @escaping (Result<KeyPair, Error>) -> Void
    ) {
        Task {
            do {
                let keypair = try await KeyPair.companion.random()
                completion(.success(keypair))
            } catch {
                completion(.failure(error))
            }
        }
    }
}
```

### Debug Helpers

```swift
extension KeyPair {
    func debugDescription() -> String {
        """
        KeyPair Debug Info:
        - Account ID: \(getAccountId())
        - Can Sign: \(canSign())
        - Public Key: \(getPublicKey().hexString)
        - Crypto Library: \(KeyPair.companion.getCryptoLibraryName())
        """
    }
}

// Usage
print(keypair.debugDescription())
```

---

**Navigation**: [← JavaScript Platform](javascript.md) | [macOS Platform →](macos.md)