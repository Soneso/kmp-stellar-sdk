//
//  ReownWalletHandler.swift
//  StellarSmartDemo
//
//  Copyright (c) 2026 Soneso. All rights reserved.
//

import Foundation
import Combine
import UIKit
import WalletConnectSign
import WalletConnectNetworking
import WalletConnectPairing
import shared

// MARK: - SocketFactory

/// URLSessionWebSocket-based factory for the Reown networking layer.
///
/// This avoids a Starscream dependency by using Apple's built-in WebSocket support,
/// which is available on iOS 13+. The Reown SDK requires a `WebSocketFactory` that
/// produces `WebSocketConnecting`-conforming objects; this implementation wraps
/// `URLSessionWebSocketTask`.
private final class SocketFactory: WebSocketFactory {
    func create(with url: URL) -> WebSocketConnecting {
        return NativeWebSocket(url: url)
    }
}

/// `URLSessionWebSocketTask`-backed `WebSocketConnecting` implementation.
///
/// Bridges Apple's native WebSocket API to the `WebSocketConnecting` protocol
/// required by the Reown networking layer.
private final class NativeWebSocket: NSObject, WebSocketConnecting, URLSessionWebSocketDelegate {
    var isConnected: Bool = false
    var onConnect: (() -> Void)?
    var onDisconnect: ((Error?) -> Void)?
    var onText: ((String) -> Void)?
    var request: URLRequest

    private var task: URLSessionWebSocketTask?
    private lazy var session: URLSession = {
        URLSession(configuration: .default, delegate: self, delegateQueue: nil)
    }()

    init(url: URL) {
        self.request = URLRequest(url: url)
        super.init()
    }

    func connect() {
        task = session.webSocketTask(with: request)
        task?.resume()
        listen()
    }

    func disconnect() {
        task?.cancel(with: .normalClosure, reason: nil)
    }

    func write(string: String, completion: (() -> Void)?) {
        task?.send(.string(string)) { _ in
            completion?()
        }
    }

    private func listen() {
        task?.receive { [weak self] result in
            guard let self else { return }
            switch result {
            case .success(let message):
                switch message {
                case .string(let text):
                    self.onText?(text)
                case .data(let data):
                    print("[NativeWebSocket] Received unexpected binary message (\(data.count) bytes)")
                @unknown default:
                    break
                }
                self.listen()
            case .failure:
                break
            }
        }
    }

    // MARK: URLSessionWebSocketDelegate

    func urlSession(
        _ session: URLSession,
        webSocketTask: URLSessionWebSocketTask,
        didOpenWithProtocol protocol: String?
    ) {
        isConnected = true
        onConnect?()
    }

    func urlSession(
        _ session: URLSession,
        webSocketTask: URLSessionWebSocketTask,
        didCloseWith closeCode: URLSessionWebSocketTask.CloseCode,
        reason: Data?
    ) {
        isConnected = false
        onDisconnect?(nil)
    }

    func urlSession(_ session: URLSession, task: URLSessionTask, didCompleteWithError error: Error?) {
        if isConnected {
            isConnected = false
            onDisconnect?(error)
        }
    }
}

// MARK: - ReownWalletHandlerImpl

/// Reown WalletConnectSign-based implementation of the Kotlin `ReownHandler` protocol.
///
/// This class acts as the dApp-side WalletConnect peer. It:
/// 1. Configures `Networking`, `Pair`, and `Sign` during `configure(projectId:)`.
/// 2. Exposes the callback-based interface that `ReownConnectorBridge` calls from Kotlin.
/// 3. Manages one active session at a time and stores the connected Stellar address.
///
/// Stellar namespace:
/// - Chain IDs: `stellar:testnet` (Testnet) and `stellar:pubnet` (Mainnet)
/// - Methods: `stellar_signAuthEntry`
/// - Network passphrases: mapped from the chain ID in the settled namespace.
///
/// Thread safety:
/// All publisher subscriptions and completions are dispatched to the main queue.
/// The stored session and address are accessed only from the main queue.
final class ReownWalletHandlerImpl: NSObject, ReownHandler {

    // MARK: - Constants

    private static let stellarNamespaceKey = "stellar"
    private static let signMethod = "stellar_signAuthEntry"
    private static let testnetChain = "stellar:testnet"
    private static let pubnetChain = "stellar:pubnet"
    private static let testnetPassphrase = "Test SDF Network ; September 2015"
    private static let pubnetPassphrase = "Public Global Stellar Network ; September 2015"
    private static let connectionTimeout: TimeInterval = 120.0
    /// Timeout for waiting for a sign response (app-level, in seconds).
    /// Aligned with Android (SIGNING_TIMEOUT_MS = 120_000).
    private static let signingTimeout: TimeInterval = 120.0

    /// TTL for the WalletConnect Request object. Must be between 300 and 604800 seconds.
    /// Using the protocol minimum of 300 seconds.
    private static let requestTtl: TimeInterval = 300.0

    // MARK: - State

    /// Lock protecting `activeSession` and `connectedAddress` which may be read from
    /// background threads via the synchronous `isConnected` / `getConnectedAddress` methods.
    private let stateLock = NSLock()

    /// Active WalletConnect session, set when `sessionSettlePublisher` fires.
    /// Access under `stateLock`.
    private var activeSession: Session?

    /// Stellar G-address extracted from the settled session namespace.
    /// Access under `stateLock`.
    private var connectedAddress: String?

    /// Combine subscriptions.
    private var cancellables = Set<AnyCancellable>()

    /// Pending connection completion. Set in `connect`, resolved in `sessionSettlePublisher`.
    private var pendingConnectCompletion: ((WalletConnectionBridge?, String?) -> Void)?

    /// Pending sign completions keyed by request ID string.
    private var pendingSignCompletions: [String: (String?, String?) -> Void] = [:]

    // MARK: - Configuration

    /// Configures the Reown networking stack.
    ///
    /// Must be called once before any other method, typically in the app delegate.
    /// Subsequent calls are no-ops (the singletons guard against re-configuration).
    ///
    /// - Parameter projectId: Reown cloud project ID from https://cloud.reown.com.
    static func configure(projectId: String) {
        Networking.configure(
            groupIdentifier: "group.com.soneso.stellar.smartdemo",
            projectId: projectId,
            socketFactory: SocketFactory()
        )

        let metadata = AppMetadata(
            name: "Stellar Smart Account Demo",
            description: "Stellar Smart Account Demo powered by the KMP Stellar SDK.",
            url: "https://soneso.com",
            icons: ["https://soneso.com/icon.png"],
            redirect: try! AppMetadata.Redirect(
                native: "stellar-smartdemo://",
                universal: nil
            )
        )
        Pair.configure(metadata: metadata)
        Sign.configure(crypto: DefaultCryptoProvider())
    }

    // MARK: - Lifecycle

    override init() {
        super.init()
        subscribeToPublishers()
    }

    // MARK: - ReownHandler (connect)

    func connect(completion: @escaping (WalletConnectionBridge?, String?) -> Void) {
        DispatchQueue.main.async { [weak self] in
            guard let self else { return }

            // If a session is already active, return it immediately.
            if let session = self.activeSession, let address = self.connectedAddress {
                let walletName = session.peer.name
                completion(WalletConnectionBridge(address: address, walletName: walletName), nil)
                return
            }

            self.pendingConnectCompletion = completion

            Task {
                do {
                    let uri = try await self.startSession()
                    await self.openWalletUri(uri.absoluteString)
                } catch {
                    DispatchQueue.main.async {
                        self.pendingConnectCompletion = nil
                        completion(nil, error.localizedDescription)
                    }
                }
            }

            // Connection timeout watchdog.
            DispatchQueue.main.asyncAfter(deadline: .now() + Self.connectionTimeout) { [weak self] in
                guard let self else { return }
                if let pending = self.pendingConnectCompletion {
                    self.pendingConnectCompletion = nil
                    pending(nil, "Wallet connection timed out after \(Int(Self.connectionTimeout)) seconds.")
                }
            }
        }
    }

    // MARK: - ReownHandler (disconnect)

    func disconnect(address: String, completion: @escaping (String?) -> Void) {
        DispatchQueue.main.async { [weak self] in
            guard let self else {
                completion(nil)
                return
            }

            guard let session = self.activeSession else {
                // No active session to disconnect — treat as success.
                completion(nil)
                return
            }

            Task {
                do {
                    try await Sign.instance.disconnect(topic: session.topic)
                    DispatchQueue.main.async {
                        self.stateLock.lock()
                        self.activeSession = nil
                        self.connectedAddress = nil
                        self.stateLock.unlock()
                        completion(nil)
                    }
                } catch {
                    DispatchQueue.main.async {
                        completion(error.localizedDescription)
                    }
                }
            }
        }
    }

    // MARK: - ReownHandler (signAuthEntry)

    func signAuthEntry(preimageXdr: String, address: String, completion: @escaping (String?, String?) -> Void) {
        DispatchQueue.main.async { [weak self] in
            guard let self else {
                completion(nil, "Handler deallocated.")
                return
            }

            guard let session = self.activeSession else {
                completion(nil, "No active wallet session. Connect a wallet before signing.")
                return
            }

            guard let chain = self.primaryChain(for: session) else {
                completion(nil, "Wallet session has no Stellar namespace.")
                return
            }

            Task {
                do {
                    let params = AnyCodable(["entryXdr": preimageXdr])
                    let request = try Request(
                        topic: session.topic,
                        method: Self.signMethod,
                        params: params,
                        chainId: chain,
                        ttl: Self.requestTtl
                    )

                    let requestIdKey = request.id.string

                    DispatchQueue.main.async {
                        self.pendingSignCompletions[requestIdKey] = completion
                    }

                    try await Sign.instance.request(params: request)
                    await self.openWalletForSigning(session: session, requestId: request.id.string)

                    // Signing timeout watchdog.
                    DispatchQueue.main.asyncAfter(deadline: .now() + Self.requestTtl) { [weak self] in
                        guard let self else { return }
                        if let pending = self.pendingSignCompletions.removeValue(forKey: requestIdKey) {
                            pending(nil, "Signing request timed out after \(Int(Self.requestTtl)) seconds.")
                        }
                    }
                } catch {
                    DispatchQueue.main.async {
                        completion(nil, error.localizedDescription)
                    }
                }
            }
        }
    }

    // MARK: - ReownHandler (synchronous)

    func isConnected(address: String) -> Bool {
        stateLock.lock()
        defer { stateLock.unlock() }
        return connectedAddress == address && activeSession != nil
    }

    func getConnectedAddress() -> String? {
        stateLock.lock()
        defer { stateLock.unlock() }
        return connectedAddress
    }

    func getNetworkPassphrase(completion: @escaping (String?) -> Void) {
        DispatchQueue.main.async { [weak self] in
            guard let self, let session = self.activeSession else {
                completion(nil)
                return
            }
            let passphrase = self.networkPassphrase(for: session)
            completion(passphrase)
        }
    }

    // MARK: - Publisher subscriptions

    private func subscribeToPublishers() {
        Sign.instance.sessionSettlePublisher
            .receive(on: DispatchQueue.main)
            .sink { [weak self] (session, _) in
                guard let self else { return }
                self.handleSessionSettled(session)
            }
            .store(in: &cancellables)

        Sign.instance.sessionResponsePublisher
            .receive(on: DispatchQueue.main)
            .sink { [weak self] response in
                guard let self else { return }
                self.handleSessionResponse(response)
            }
            .store(in: &cancellables)

        Sign.instance.sessionDeletePublisher
            .receive(on: DispatchQueue.main)
            .sink { [weak self] (_, _) in
                guard let self else { return }
                self.stateLock.lock()
                self.activeSession = nil
                self.connectedAddress = nil
                self.stateLock.unlock()
            }
            .store(in: &cancellables)

        // Restore any previously settled sessions on startup.
        let sessions = Sign.instance.getSessions()
        if let existing = sessions.first,
           let address = stellarAddress(from: existing) {
            stateLock.lock()
            activeSession = existing
            connectedAddress = address
            stateLock.unlock()
        }
    }

    // MARK: - Session helpers

    private func startSession() async throws -> URL {
        let methods: Set<String> = [Self.signMethod]
        let chains: [Blockchain] = [
            Blockchain(Self.testnetChain)!,
            Blockchain(Self.pubnetChain)!
        ]

        let stellarNamespace = ProposalNamespace(
            chains: chains,
            methods: methods,
            events: ["accountsChanged"]
        )
        let namespaces: [String: ProposalNamespace] = [Self.stellarNamespaceKey: stellarNamespace]

        let uri = try await Sign.instance.connect(namespaces: namespaces)
        guard let url = URL(string: uri.absoluteString) else {
            throw ReownError.invalidUri
        }
        return url
    }

    private func handleSessionSettled(_ session: Session) {
        guard let address = stellarAddress(from: session) else {
            pendingConnectCompletion?(nil, "Wallet session has no Stellar account address.")
            pendingConnectCompletion = nil
            return
        }

        stateLock.lock()
        activeSession = session
        connectedAddress = address
        stateLock.unlock()

        let bridge = WalletConnectionBridge(
            address: address,
            walletName: session.peer.name
        )
        pendingConnectCompletion?(bridge, nil)
        pendingConnectCompletion = nil
    }

    private func handleSessionResponse(_ response: WalletConnectSign.Response) {
        let idKey = response.id.string
        guard let completion = pendingSignCompletions.removeValue(forKey: idKey) else { return }

        switch response.result {
        case .response(let anyCodable):
            // Freighter returns { signedAuthEntry: "base64...", signerAddress: "G..." }
            // via WalletConnect. Extract the signedAuthEntry from the dictionary.
            if let dict = anyCodable.value as? [String: Any],
               let signedAuthEntry = dict["signedAuthEntry"] as? String {
                completion(signedAuthEntry, nil)
            } else if let signature = anyCodable.value as? String {
                // Plain string fallback (some wallets may return the signature directly)
                completion(signature, nil)
            } else {
                completion(nil, "Wallet returned an unexpected response format.")
            }
        case .error(let rpcError):
            completion(nil, rpcError.message)
        }
    }

    // MARK: - Address / namespace extraction

    private func stellarAddress(from session: Session) -> String? {
        guard let namespace = session.namespaces[Self.stellarNamespaceKey] else { return nil }
        // Accounts are formatted as "stellar:testnet:GADDR..." or "stellar:pubnet:GADDR...".
        return namespace.accounts.first.map { account in
            // The address is the portion after the last colon.
            String(account.address)
        }
    }

    private func primaryChain(for session: Session) -> Blockchain? {
        guard let namespace = session.namespaces[Self.stellarNamespaceKey],
              let firstAccount = namespace.accounts.first else { return nil }
        // Blockchain is "namespace:chain", e.g. "stellar:testnet"
        return Blockchain("\(firstAccount.namespace):\(firstAccount.reference)")
    }

    private func networkPassphrase(for session: Session) -> String? {
        guard let chain = primaryChain(for: session) else { return nil }
        switch chain.absoluteString {
        case Self.testnetChain: return Self.testnetPassphrase
        case Self.pubnetChain: return Self.pubnetPassphrase
        default: return nil
        }
    }

    // MARK: - Deep link helpers

    @MainActor
    private func openWalletUri(_ uriString: String) {
        // Freighter Mobile's deep link format (from the Reown wallet registry):
        //   freighterwallet://wc-redirect?uri={RFC3986 percent-encoded WC URI}
        //
        // The WC URI contains ?, &, = characters that must be fully encoded.
        // Using .alphanumerics ensures all special characters are encoded.
        guard let encoded = uriString.addingPercentEncoding(withAllowedCharacters: .alphanumerics),
              let url = URL(string: "freighterwallet://wc-redirect?uri=\(encoded)") else {
            print("[ReownWalletHandler] Failed to build Freighter deep link")
            return
        }
        print("[ReownWalletHandler] Opening Freighter via wc-redirect deep link")
        UIApplication.shared.open(url) { success in
            if !success {
                print("[ReownWalletHandler] Could not open Freighter — is it installed?")
            }
        }
    }

    @MainActor
    private func openWalletForSigning(session: Session, requestId: String) {
        // Redirect the user back to the wallet to approve the signing request.
        let redirectUrl = session.peer.redirect?.native ?? session.peer.redirect?.universal
        if let redirectString = redirectUrl, let url = URL(string: redirectString) {
            UIApplication.shared.open(url)
        }
    }
}

// MARK: - DefaultCryptoProvider

/// Minimal `CryptoProvider` stub for the Reown SDK.
///
/// WalletConnectSign requires a `CryptoProvider` conformance. Both methods
/// (`recoverPubKey` and `keccak256`) are only used by Ethereum-specific
/// components (ENS resolver, EIP-191/1271 verification, EIP-55 encoding)
/// for SIWE authentication flows. The WalletConnectSign module never calls
/// them during standard session management, pairing, or request/response.
/// They are dead code paths for Stellar — return empty Data to satisfy the
/// protocol requirement.
final class DefaultCryptoProvider: CryptoProvider {

    func recoverPubKey(signature: EthereumSignature, message: Data) throws -> Data {
        return Data()
    }

    func keccak256(_ data: Data) -> Data {
        return Data()
    }
}

// MARK: - ReownError

private enum ReownError: LocalizedError {
    case invalidUri

    var errorDescription: String? {
        switch self {
        case .invalidUri:
            return "WalletConnect produced an invalid pairing URI."
        }
    }
}
