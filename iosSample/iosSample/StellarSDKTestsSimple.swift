//
//  StellarSDKTestsSimple.swift
//  Simplified test suite that actually builds
//

import Foundation
import stellar_sdk

struct SimpleTestResult: Identifiable {
    let id = UUID()
    let name: String
    let passed: Bool
    let message: String
}

class SimpleStellarTests: ObservableObject {
    @Published var testResults: [SimpleTestResult] = []
    @Published var isRunning = false

    func runTests() {
        isRunning = true
        testResults.removeAll()

        DispatchQueue.global(qos: .userInitiated).async { [weak self] in
            var results: [SimpleTestResult] = []

            results.append(self?.testRandomKeypair() ?? SimpleTestResult(name: "Random Keypair", passed: false, message: "Test error"))
            results.append(self?.testFromSeed() ?? SimpleTestResult(name: "From Seed", passed: false, message: "Test error"))
            results.append(self?.testFromAccountId() ?? SimpleTestResult(name: "From Account ID", passed: false, message: "Test error"))
            results.append(self?.testSignVerify() ?? SimpleTestResult(name: "Sign/Verify", passed: false, message: "Test error"))

            DispatchQueue.main.async {
                self?.testResults = results
                self?.isRunning = false
            }
        }
    }

    private func testRandomKeypair() -> SimpleTestResult {
        do {
            let kp1 = KeyPair.Companion().random()
            let kp2 = KeyPair.Companion().random()

            let id1 = kp1.getAccountId()
            let id2 = kp2.getAccountId()

            if kp1.canSign() && kp2.canSign() && id1 != id2 {
                return SimpleTestResult(
                    name: "Random Keypair",
                    passed: true,
                    message: "Generated 2 unique keypairs: \(id1.prefix(12))... and \(id2.prefix(12))..."
                )
            } else {
                return SimpleTestResult(name: "Random Keypair", passed: false, message: "Failed validation")
            }
        } catch {
            return SimpleTestResult(name: "Random Keypair", passed: false, message: error.localizedDescription)
        }
    }

    private func testFromSeed() -> SimpleTestResult {
        do {
            let seed = "SDJHRQF4GCMIIKAAAQ6IHY42X73FQFLHUULAPSKKD4DFDM7UXWWCRHBE"
            let expected = "GCZHXL5HXQX5ABDM26LHYRCQZ5OJFHLOPLZX47WEBP3V2PF5AVFK2A5D"

            let kp = try KeyPair.Companion().fromSecretSeed(seed: seed)
            let accountId = kp.getAccountId()

            if accountId == expected && kp.canSign() {
                return SimpleTestResult(
                    name: "From Seed",
                    passed: true,
                    message: "Correctly derived: \(accountId.prefix(20))..."
                )
            } else {
                return SimpleTestResult(name: "From Seed", passed: false, message: "Account ID mismatch")
            }
        } catch {
            return SimpleTestResult(name: "From Seed", passed: false, message: error.localizedDescription)
        }
    }

    private func testFromAccountId() -> SimpleTestResult {
        do {
            let accountId = "GCZHXL5HXQX5ABDM26LHYRCQZ5OJFHLOPLZX47WEBP3V2PF5AVFK2A5D"
            let kp = try KeyPair.Companion().fromAccountId(accountId: accountId)

            if !kp.canSign() && kp.getAccountId() == accountId {
                return SimpleTestResult(
                    name: "From Account ID",
                    passed: true,
                    message: "Created public-only keypair: \(accountId.prefix(20))..."
                )
            } else {
                return SimpleTestResult(name: "From Account ID", passed: false, message: "Invalid public keypair")
            }
        } catch {
            return SimpleTestResult(name: "From Account ID", passed: false, message: error.localizedDescription)
        }
    }

    private func testSignVerify() -> SimpleTestResult {
        do {
            let kp = KeyPair.Companion().random()
            let message = "Hello Stellar from iOS!".data(using: .utf8)!
            let messageBytes = KotlinByteArray(size: Int32(message.count))

            message.withUnsafeBytes { (buffer: UnsafeRawBufferPointer) in
                for i in 0..<message.count {
                    messageBytes.set(index: Int32(i), value: Int8(bitPattern: buffer[i]))
                }
            }

            let signature = try kp.sign(data: messageBytes)
            let isValid = kp.verify(data: messageBytes, signature: signature)

            if isValid {
                return SimpleTestResult(
                    name: "Sign/Verify",
                    passed: true,
                    message: "Successfully signed and verified message"
                )
            } else {
                return SimpleTestResult(name: "Sign/Verify", passed: false, message: "Verification failed")
            }
        } catch {
            return SimpleTestResult(name: "Sign/Verify", passed: false, message: error.localizedDescription)
        }
    }
}