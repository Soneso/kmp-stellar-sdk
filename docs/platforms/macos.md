# macOS Platform Guide

This guide covers macOS-specific setup and usage for the Stellar KMP SDK.

## Table of Contents

- [Platform Overview](#platform-overview)
- [Installation](#installation)
- [Project Setup](#project-setup)
- [SwiftUI Development](#swiftui-development)
- [AppKit Development](#appkit-development)
- [Command Line Tools](#command-line-tools)
- [Menu Bar Applications](#menu-bar-applications)
- [Security & Sandboxing](#security--sandboxing)
- [Performance Optimization](#performance-optimization)
- [Troubleshooting](#troubleshooting)

## Platform Overview

The macOS implementation supports:
- **macOS 12.0+** (Monterey and later)
- **Mac Catalyst** apps from iOS
- **Command-line tools**
- Both **Apple Silicon (M1/M2/M3)** and **Intel** Macs

Key characteristics:
- Uses native libsodium for cryptography
- Full SwiftUI and AppKit support
- Notarization ready
- Sandboxing compatible

## Installation

### Two Approaches Based on Your App Type

The setup differs depending on whether you're building a **Compose Desktop** app (JVM) or a **Native SwiftUI/AppKit** app:

#### Option 1: Compose Desktop on macOS (Recommended - 95% of cases)

**Use Maven artifact - no additional setup required:**

```kotlin
// build.gradle.kts
kotlin {
    jvm("desktop") {
        compilations.all {
            kotlinOptions.jvmTarget = "11"
        }
    }

    sourceSets {
        val desktopMain by getting {
            dependencies {
                // Maven artifact works perfectly for JVM/Compose Desktop
                implementation("com.soneso.stellar:stellar-sdk:0.2.0")
                implementation(compose.desktop.currentOs)
            }
        }
    }
}
```

**Why this works:** Compose Desktop runs on the JVM and uses BouncyCastle for cryptography - no native dependencies needed.

#### Option 2: Native SwiftUI/AppKit Apps

**Requires SDK as Git submodule:**

```bash
# Add SDK as submodule
git submodule add https://github.com/Soneso/stellar-kmp-sdk.git stellar-sdk
git submodule update --init --recursive

# Install libsodium via Homebrew
brew install libsodium
```

**In settings.gradle.kts:**
```kotlin
include(":stellar-sdk")
project(":stellar-sdk").projectDir = file("stellar-sdk/stellar-sdk")
```

**In your shared module build.gradle.kts:**
```kotlin
kotlin {
    macosArm64().binaries.framework {
        baseName = "shared"
        isStatic = true
        // Export SDK for Swift access
        export(project(":stellar-sdk"))
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                // Use project dependency for native Swift interop
                api(project(":stellar-sdk"))
            }
        }
    }
}
```

**Why project dependency is needed:** When Swift code directly uses SDK types (like `KeyPair`, `Transaction`), the framework must export the SDK. Maven artifacts cannot be exported due to Kotlin/Native cinterop limitations with libsodium.

### Framework Building (Native Apps Only)

```bash
# For Apple Silicon
./gradlew :stellar-sdk:linkDebugFrameworkMacosArm64

# For Intel
./gradlew :stellar-sdk:linkDebugFrameworkMacosX64

# Build XCFramework (universal)
./build-xcframework.sh
```

## Project Setup

### Xcode Configuration

```xml
<!-- Info.plist -->
<key>LSMinimumSystemVersion</key>
<string>12.0</string>

<!-- Network access for sandboxed apps -->
<key>NSAppTransportSecurity</key>
<dict>
    <key>NSAllowsArbitraryLoads</key>
    <false/>
</dict>

<!-- For Menu Bar apps -->
<key>LSUIElement</key>
<true/>
```

### Entitlements

```xml
<!-- YourApp.entitlements -->
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
    <!-- Network access -->
    <key>com.apple.security.network.client</key>
    <true/>

    <!-- Keychain access -->
    <key>com.apple.security.keychain-access-groups</key>
    <array>
        <string>$(AppIdentifierPrefix)com.yourcompany.stellar</string>
    </array>

    <!-- For non-sandboxed apps (development) -->
    <key>com.apple.security.app-sandbox</key>
    <false/>
</dict>
</plist>
```

## SwiftUI Development

### Main Application

```swift
import SwiftUI
import StellarSDK

@main
struct StellarMacApp: App {
    @StateObject private var walletManager = WalletManager()

    var body: some Scene {
        WindowGroup {
            ContentView()
                .environmentObject(walletManager)
                .frame(minWidth: 800, minHeight: 600)
        }
        .windowStyle(.titleBar)
        .windowToolbarStyle(.unified)

        #if os(macOS)
        Settings {
            SettingsView()
                .environmentObject(walletManager)
        }
        #endif
    }
}
```

### Main Window View

```swift
struct ContentView: View {
    @EnvironmentObject var walletManager: WalletManager
    @State private var selectedTab = 0

    var body: some View {
        NavigationSplitView {
            SidebarView(selection: $selectedTab)
        } detail: {
            switch selectedTab {
            case 0:
                WalletView()
            case 1:
                TransactionsView()
            case 2:
                TradingView()
            default:
                EmptyView()
            }
        }
        .toolbar {
            ToolbarItemGroup {
                Button(action: refresh) {
                    Label("Refresh", systemImage: "arrow.clockwise")
                }
                .keyboardShortcut("r", modifiers: .command)

                Spacer()

                ConnectionStatusView()
            }
        }
    }

    private func refresh() {
        Task {
            await walletManager.refreshAll()
        }
    }
}
```

### Wallet Manager

```swift
import Foundation
import StellarSDK
import Combine

@MainActor
class WalletManager: ObservableObject {
    @Published var accounts: [WalletAccount] = []
    @Published var selectedAccount: WalletAccount?
    @Published var isConnected = false
    @Published var network: Network = .testnet

    private var horizonServer: HorizonServer?
    private var cancellables = Set<AnyCancellable>()

    init() {
        loadAccounts()
        setupNetworkMonitoring()
    }

    private func setupNetworkMonitoring() {
        Timer.publish(every: 30, on: .main, in: .common)
            .autoconnect()
            .sink { _ in
                Task {
                    await self.checkConnection()
                }
            }
            .store(in: &cancellables)
    }

    func createNewAccount() async throws {
        let keypair = try await KeyPair.companion.random()

        let account = WalletAccount(
            id: UUID(),
            name: "Account \(accounts.count + 1)",
            keypair: keypair,
            balance: "0"
        )

        accounts.append(account)
        selectedAccount = account

        // Save to keychain
        try KeychainHelper.save(account)

        // Fund on testnet
        if network == .testnet {
            _ = try await FriendBot.companion.fundAccount(
                accountId: keypair.getAccountId(),
                network: network
            )
            await refreshBalance(for: account)
        }
    }

    func importAccount(secretSeed: String) async throws {
        let keypair = try await KeyPair.companion.fromSecretSeed(seed: secretSeed)

        let account = WalletAccount(
            id: UUID(),
            name: "Imported Account",
            keypair: keypair,
            balance: "0"
        )

        accounts.append(account)
        selectedAccount = account

        try KeychainHelper.save(account)
        await refreshBalance(for: account)
    }

    func refreshBalance(for account: WalletAccount) async {
        guard let server = getHorizonServer() else { return }

        do {
            let response = try await server.loadAccount(
                accountId: account.keypair.getAccountId()
            )

            if let xlmBalance = response.balances.first(where: { $0.assetType == "native" }) {
                if let index = accounts.firstIndex(where: { $0.id == account.id }) {
                    accounts[index].balance = xlmBalance.balance
                }
            }
        } catch {
            print("Failed to load balance: \(error)")
        }
    }

    func refreshAll() async {
        for account in accounts {
            await refreshBalance(for: account)
        }
    }

    private func checkConnection() async {
        guard let server = getHorizonServer() else {
            isConnected = false
            return
        }

        do {
            _ = try await server.getRoot()
            isConnected = true
        } catch {
            isConnected = false
        }
    }

    private func getHorizonServer() -> HorizonServer? {
        if horizonServer == nil {
            let url = network == .public ?
                "https://horizon.stellar.org" :
                "https://horizon-testnet.stellar.org"
            horizonServer = HorizonServer(serverUrl: url)
        }
        return horizonServer
    }

    private func loadAccounts() {
        accounts = KeychainHelper.loadAllAccounts()
        selectedAccount = accounts.first
    }

    deinit {
        horizonServer?.close()
    }
}

struct WalletAccount: Identifiable, Codable {
    let id: UUID
    var name: String
    let keypair: KeyPair  // Note: KeyPair needs Codable conformance
    var balance: String
}
```

### Transaction Builder View

```swift
struct TransactionBuilderView: View {
    @EnvironmentObject var walletManager: WalletManager
    @State private var recipient = ""
    @State private var amount = ""
    @State private var memo = ""
    @State private var isProcessing = false
    @State private var transactionHash = ""
    @State private var error: Error?

    var body: some View {
        Form {
            Section("Payment Details") {
                TextField("Recipient Account (G...)", text: $recipient)
                    .textFieldStyle(.roundedBorder)

                HStack {
                    TextField("Amount", text: $amount)
                        .textFieldStyle(.roundedBorder)
                    Text("XLM")
                }

                TextField("Memo (optional)", text: $memo)
                    .textFieldStyle(.roundedBorder)
            }

            Section {
                HStack {
                    Button("Send Payment") {
                        Task {
                            await sendPayment()
                        }
                    }
                    .buttonStyle(.borderedProminent)
                    .disabled(isProcessing || recipient.isEmpty || amount.isEmpty)

                    if isProcessing {
                        ProgressView()
                            .progressViewStyle(CircularProgressViewStyle())
                            .scaleEffect(0.8)
                    }
                }
            }

            if !transactionHash.isEmpty {
                Section("Transaction Result") {
                    HStack {
                        Text("Hash:")
                        Text(transactionHash)
                            .font(.system(.caption, design: .monospaced))
                            .textSelection(.enabled)
                    }

                    Button("View on Explorer") {
                        openExplorer()
                    }
                }
            }
        }
        .padding()
        .alert("Error", isPresented: .constant(error != nil)) {
            Button("OK") {
                error = nil
            }
        } message: {
            Text(error?.localizedDescription ?? "")
        }
    }

    private func sendPayment() async {
        guard let account = walletManager.selectedAccount else { return }

        isProcessing = true
        defer { isProcessing = false }

        do {
            let server = HorizonServer(
                serverUrl: walletManager.network == .public ?
                    "https://horizon.stellar.org" :
                    "https://horizon-testnet.stellar.org"
            )
            defer { server.close() }

            // Load source account
            let sourceAccount = try await server.loadAccount(
                accountId: account.keypair.getAccountId()
            )

            // Build transaction
            var builder = TransactionBuilder(
                sourceAccount: sourceAccount,
                network: walletManager.network
            )
            .addOperation(
                PaymentOperation(
                    destination: recipient,
                    amount: amount,
                    asset: Asset.native
                )
            )
            .setBaseFee(100)
            .setTimeout(180)

            if !memo.isEmpty {
                builder = builder.addMemo(Memo.text(memo))
            }

            let transaction = builder.build()

            // Sign transaction
            try await transaction.sign(signer: account.keypair)

            // Submit transaction
            let response = try await server.submitTransaction(transaction: transaction)

            if response.isSuccess {
                transactionHash = response.hash ?? ""
                // Refresh balance
                await walletManager.refreshBalance(for: account)
            } else {
                throw TransactionError.failed(response.extras?.resultCodes?.transactionResultCode ?? "Unknown")
            }

        } catch {
            self.error = error
        }
    }

    private func openExplorer() {
        let baseUrl = walletManager.network == .public ?
            "https://stellar.expert/explorer/public" :
            "https://stellar.expert/explorer/testnet"
        let url = URL(string: "\(baseUrl)/tx/\(transactionHash)")!
        NSWorkspace.shared.open(url)
    }
}

enum TransactionError: LocalizedError {
    case failed(String)

    var errorDescription: String? {
        switch self {
        case .failed(let reason):
            return "Transaction failed: \(reason)"
        }
    }
}
```

## AppKit Development

### Window Controller

```swift
import AppKit
import StellarSDK

class MainWindowController: NSWindowController {

    override func windowDidLoad() {
        super.windowDidLoad()

        window?.titlebarAppearsTransparent = true
        window?.titleVisibility = .visible
        window?.styleMask.insert(.fullSizeContentView)

        // Set minimum size
        window?.minSize = NSSize(width: 800, height: 600)

        // Center on screen
        window?.center()
    }
}
```

### View Controller

```swift
import AppKit
import StellarSDK

class WalletViewController: NSViewController {

    @IBOutlet weak var accountField: NSTextField!
    @IBOutlet weak var balanceField: NSTextField!
    @IBOutlet weak var progressIndicator: NSProgressIndicator!
    @IBOutlet weak var tableView: NSTableView!

    private var keypair: KeyPair?
    private var transactions: [TransactionResponse] = []

    override func viewDidLoad() {
        super.viewDidLoad()
        setupUI()
    }

    private func setupUI() {
        accountField.stringValue = "No account"
        balanceField.stringValue = "0 XLM"
        tableView.delegate = self
        tableView.dataSource = self
    }

    @IBAction func generateAccount(_ sender: NSButton) {
        progressIndicator.startAnimation(nil)

        Task {
            do {
                let keypair = try await KeyPair.companion.random()

                await MainActor.run {
                    self.keypair = keypair
                    self.accountField.stringValue = keypair.getAccountId()
                    self.progressIndicator.stopAnimation(nil)
                }

                // Fund on testnet
                await fundAccount()

            } catch {
                await MainActor.run {
                    self.showError(error)
                    self.progressIndicator.stopAnimation(nil)
                }
            }
        }
    }

    private func fundAccount() async {
        guard let keypair = keypair else { return }

        do {
            let success = try await FriendBot.companion.fundAccount(
                accountId: keypair.getAccountId(),
                network: Network.testnet
            )

            if success {
                await loadBalance()
            }
        } catch {
            print("Failed to fund: \(error)")
        }
    }

    private func loadBalance() async {
        guard let keypair = keypair else { return }

        let server = HorizonServer(serverUrl: "https://horizon-testnet.stellar.org")
        defer { server.close() }

        do {
            let account = try await server.loadAccount(accountId: keypair.getAccountId())

            await MainActor.run {
                if let balance = account.balances.first {
                    self.balanceField.stringValue = "\(balance.balance) XLM"
                }
            }

            // Load transactions
            await loadTransactions()

        } catch {
            print("Failed to load account: \(error)")
        }
    }

    private func loadTransactions() async {
        // Implementation...
    }

    private func showError(_ error: Error) {
        let alert = NSAlert()
        alert.messageText = "Error"
        alert.informativeText = error.localizedDescription
        alert.alertStyle = .warning
        alert.addButton(withTitle: "OK")
        alert.runModal()
    }
}

extension WalletViewController: NSTableViewDataSource, NSTableViewDelegate {
    // Table view implementation...
}
```

## Command Line Tools

### CLI Application

```swift
// Sources/StellarCLI/main.swift
import Foundation
import StellarSDK
import ArgumentParser

@main
struct StellarCLI: AsyncParsableCommand {
    static let configuration = CommandConfiguration(
        commandName: "stellar",
        abstract: "Stellar blockchain CLI tool",
        version: "1.0.0",
        subcommands: [
            Generate.self,
            Balance.self,
            Pay.self,
            Sign.self
        ]
    )
}

struct Generate: AsyncParsableCommand {
    static let configuration = CommandConfiguration(
        abstract: "Generate a new Stellar keypair"
    )

    @Flag(help: "Output as JSON")
    var json = false

    @Flag(help: "Fund on testnet")
    var fund = false

    func run() async throws {
        let keypair = try await KeyPair.companion.random()

        if json {
            let output = [
                "accountId": keypair.getAccountId(),
                "secretSeed": keypair.getSecretSeed()?.toString() ?? ""
            ]
            let jsonData = try JSONSerialization.data(withJSONObject: output)
            print(String(data: jsonData, encoding: .utf8)!)
        } else {
            print("Account ID: \(keypair.getAccountId())")
            print("Secret Seed: \(keypair.getSecretSeed()?.toString() ?? "")")
        }

        if fund {
            print("Funding account on testnet...")
            let success = try await FriendBot.companion.fundAccount(
                accountId: keypair.getAccountId(),
                network: Network.testnet
            )
            print(success ? "✅ Funded!" : "❌ Failed to fund")
        }
    }
}

struct Balance: AsyncParsableCommand {
    static let configuration = CommandConfiguration(
        abstract: "Check account balance"
    )

    @Argument(help: "Account ID to check")
    var accountId: String

    @Option(help: "Network (testnet/mainnet)")
    var network: String = "testnet"

    func run() async throws {
        let serverUrl = network == "mainnet" ?
            "https://horizon.stellar.org" :
            "https://horizon-testnet.stellar.org"

        let server = HorizonServer(serverUrl: serverUrl)
        defer { server.close() }

        do {
            let account = try await server.loadAccount(accountId: accountId)

            print("Account: \(accountId)")
            print("Sequence: \(account.sequence)")
            print("\nBalances:")

            for balance in account.balances {
                if balance.assetType == "native" {
                    print("  XLM: \(balance.balance)")
                } else {
                    print("  \(balance.assetCode ?? ""): \(balance.balance)")
                }
            }
        } catch {
            print("Error: Account not found or network error")
            throw error
        }
    }
}

struct Pay: AsyncParsableCommand {
    static let configuration = CommandConfiguration(
        abstract: "Send a payment"
    )

    @Argument(help: "Recipient account ID")
    var recipient: String

    @Argument(help: "Amount to send")
    var amount: String

    @Option(help: "Secret seed of sender")
    var seed: String

    @Option(help: "Memo text")
    var memo: String?

    @Option(help: "Network (testnet/mainnet)")
    var network: String = "testnet"

    func run() async throws {
        let keypair = try await KeyPair.companion.fromSecretSeed(seed: seed)
        let networkObj = network == "mainnet" ? Network.public : Network.testnet
        let serverUrl = network == "mainnet" ?
            "https://horizon.stellar.org" :
            "https://horizon-testnet.stellar.org"

        let server = HorizonServer(serverUrl: serverUrl)
        defer { server.close() }

        print("Sending \(amount) XLM to \(recipient)...")

        let sourceAccount = try await server.loadAccount(
            accountId: keypair.getAccountId()
        )

        var builder = TransactionBuilder(
            sourceAccount: sourceAccount,
            network: networkObj
        )
        .addOperation(
            PaymentOperation(
                destination: recipient,
                amount: amount,
                asset: Asset.native
            )
        )
        .setBaseFee(100)
        .setTimeout(180)

        if let memoText = memo {
            builder = builder.addMemo(Memo.text(memoText))
        }

        let transaction = builder.build()
        try await transaction.sign(signer: keypair)

        let response = try await server.submitTransaction(transaction: transaction)

        if response.isSuccess {
            print("✅ Success! TX: \(response.hash ?? "")")
        } else {
            print("❌ Failed: \(response.extras?.resultCodes?.transactionResultCode ?? "Unknown")")
        }
    }
}
```

### Package.swift for CLI

```swift
// Package.swift
// swift-tools-version: 5.9
import PackageDescription

let package = Package(
    name: "StellarCLI",
    platforms: [
        .macOS(.v12)
    ],
    dependencies: [
        .package(path: "../stellar-sdk"),
        .package(url: "https://github.com/apple/swift-argument-parser", from: "1.2.0"),
        .package(url: "https://github.com/jedisct1/swift-sodium", from: "0.9.1")
    ],
    targets: [
        .executableTarget(
            name: "StellarCLI",
            dependencies: [
                .product(name: "StellarSDK", package: "stellar-sdk"),
                .product(name: "ArgumentParser", package: "swift-argument-parser"),
                .product(name: "Clibsodium", package: "swift-sodium")
            ]
        )
    ]
)
```

## Menu Bar Applications

### Menu Bar App

```swift
import SwiftUI
import StellarSDK

@main
struct StellarMenuBarApp: App {
    @NSApplicationDelegateAdaptor(AppDelegate.self) var appDelegate

    var body: some Scene {
        Settings {
            EmptyView()
        }
    }
}

class AppDelegate: NSObject, NSApplicationDelegate {
    var statusItem: NSStatusItem!
    var popover = NSPopover()
    @ObservedObject var priceMonitor = PriceMonitor()

    func applicationDidFinishLaunching(_ notification: Notification) {
        setupMenuBar()
        setupPopover()
        priceMonitor.start()
    }

    private func setupMenuBar() {
        statusItem = NSStatusBar.system.statusItem(withLength: NSStatusItem.variableLength)

        if let button = statusItem.button {
            button.image = NSImage(systemSymbolName: "bitcoinsign.circle", accessibilityDescription: "Stellar")
            button.action = #selector(togglePopover)
            button.target = self
        }

        // Update price in menu bar
        priceMonitor.$currentPrice
            .sink { [weak self] price in
                self?.statusItem.button?.title = price
            }
            .store(in: &cancellables)
    }

    private func setupPopover() {
        popover.contentSize = NSSize(width: 400, height: 300)
        popover.behavior = .transient
        popover.contentViewController = NSHostingController(
            rootView: MenuBarContentView()
                .environmentObject(priceMonitor)
        )
    }

    @objc private func togglePopover() {
        guard let button = statusItem.button else { return }

        if popover.isShown {
            popover.performClose(nil)
        } else {
            popover.show(relativeTo: button.bounds, of: button, preferredEdge: .minY)
        }
    }
}

struct MenuBarContentView: View {
    @EnvironmentObject var priceMonitor: PriceMonitor
    @State private var selectedAccount: String?

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack {
                Image(systemName: "bitcoinsign.circle.fill")
                    .font(.title2)
                Text("Stellar Monitor")
                    .font(.headline)
                Spacer()
                Button("Quit") {
                    NSApplication.shared.terminate(nil)
                }
                .buttonStyle(.borderless)
            }

            Divider()

            VStack(alignment: .leading) {
                Text("XLM Price")
                    .font(.caption)
                    .foregroundColor(.secondary)
                Text(priceMonitor.currentPrice)
                    .font(.title2)
                    .monospacedDigit()
            }

            if let account = selectedAccount {
                AccountQuickView(accountId: account)
            }

            Spacer()

            Button("Open Full App") {
                NSWorkspace.shared.launchApplication("StellarWallet")
            }
        }
        .padding()
    }
}

@MainActor
class PriceMonitor: ObservableObject {
    @Published var currentPrice = "$0.00"
    private var timer: Timer?

    func start() {
        timer = Timer.scheduledTimer(withTimeInterval: 60, repeats: true) { _ in
            Task {
                await self.fetchPrice()
            }
        }
        Task {
            await fetchPrice()
        }
    }

    private func fetchPrice() async {
        // Fetch XLM price from API
        // Update currentPrice
    }
}
```

## Security & Sandboxing

### Keychain Helper

```swift
import Security
import StellarSDK

class KeychainHelper {

    private static let service = "com.yourapp.stellar"
    private static let accessGroup = "YOUR_TEAM_ID.com.yourapp.stellar"

    static func save(_ account: WalletAccount) throws {
        guard let seed = account.keypair.getSecretSeed() else {
            throw KeychainError.noSecret
        }

        let encoder = JSONEncoder()
        let data = try encoder.encode(account)

        let query: [CFString: Any] = [
            kSecClass: kSecClassGenericPassword,
            kSecAttrService: service,
            kSecAttrAccount: account.keypair.getAccountId(),
            kSecValueData: data,
            kSecAttrAccessible: kSecAttrAccessibleAfterFirstUnlock,
            kSecAttrAccessGroup: accessGroup
        ]

        SecItemDelete(query as CFDictionary)

        let status = SecItemAdd(query as CFDictionary, nil)
        guard status == errSecSuccess else {
            throw KeychainError.saveFailed
        }
    }

    static func loadAllAccounts() -> [WalletAccount] {
        let query: [CFString: Any] = [
            kSecClass: kSecClassGenericPassword,
            kSecAttrService: service,
            kSecMatchLimit: kSecMatchLimitAll,
            kSecReturnData: true,
            kSecAttrAccessGroup: accessGroup
        ]

        var result: AnyObject?
        let status = SecItemCopyMatching(query as CFDictionary, &result)

        guard status == errSecSuccess,
              let items = result as? [Data] else {
            return []
        }

        let decoder = JSONDecoder()
        return items.compactMap { data in
            try? decoder.decode(WalletAccount.self, from: data)
        }
    }

    static func delete(_ accountId: String) throws {
        let query: [CFString: Any] = [
            kSecClass: kSecClassGenericPassword,
            kSecAttrService: service,
            kSecAttrAccount: accountId,
            kSecAttrAccessGroup: accessGroup
        ]

        let status = SecItemDelete(query as CFDictionary)
        guard status == errSecSuccess || status == errSecItemNotFound else {
            throw KeychainError.deleteFailed
        }
    }
}

enum KeychainError: Error {
    case noSecret
    case saveFailed
    case deleteFailed
}
```

## Performance Optimization

### Concurrent Processing

```swift
import StellarSDK

actor TransactionProcessor {
    private let maxConcurrent = 10
    private var activeTasks = 0

    func processBatch(_ transactions: [TransactionData]) async throws -> [TransactionResult] {
        return try await withThrowingTaskGroup(of: TransactionResult.self) { group in
            for transaction in transactions {
                await waitForSlot()

                group.addTask {
                    defer {
                        Task {
                            await self.releaseSlot()
                        }
                    }

                    return try await self.process(transaction)
                }
            }

            var results: [TransactionResult] = []
            for try await result in group {
                results.append(result)
            }
            return results
        }
    }

    private func waitForSlot() async {
        while activeTasks >= maxConcurrent {
            await Task.yield()
        }
        activeTasks += 1
    }

    private func releaseSlot() {
        activeTasks -= 1
    }

    private func process(_ transaction: TransactionData) async throws -> TransactionResult {
        // Process transaction
    }
}
```

## Troubleshooting

### Framework Architecture Issues

```bash
# Check framework architectures
lipo -info stellar_sdk.framework/stellar_sdk

# Build for specific architecture
# Apple Silicon
./gradlew :stellar-sdk:linkDebugFrameworkMacosArm64

# Intel
./gradlew :stellar-sdk:linkDebugFrameworkMacosX64

# Universal XCFramework
./build-xcframework.sh
```

### Code Signing

```bash
# Sign the framework
codesign --force --deep --sign "Developer ID Application: Your Name" stellar_sdk.framework

# Verify signing
codesign -dv stellar_sdk.framework
```

### Notarization

```bash
# Create DMG or ZIP for notarization
ditto -c -k --keepParent YourApp.app YourApp.zip

# Submit for notarization
xcrun notarytool submit YourApp.zip \
    --apple-id "your-apple-id@example.com" \
    --password "app-specific-password" \
    --team-id "YOUR_TEAM_ID" \
    --wait

# Staple the notarization
xcrun stapler staple YourApp.app
```

---

**Navigation**: [← iOS Platform](ios.md) | [Sample Apps Guide →](../sample-apps.md)