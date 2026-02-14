# Quick Integration Guide

This guide shows you how to integrate the Stellar SDK XCFramework into your iOS project in under 5 minutes.

## Prerequisites

- Xcode 14.0 or later
- iOS 13.0+ deployment target
- Basic familiarity with Xcode

## Step-by-Step Integration

### 1. Add the Framework to Your Project

1. **Open your Xcode project**
2. **Drag** `stellar_sdk.xcframework` from Finder into your Xcode project navigator
3. **Check** "Copy items if needed" and select your target
4. Click **"Finish"**

### 2. Configure Embedding

1. Select your **project** in the navigator
2. Select your **target**
3. Go to the **"General"** tab
4. Under **"Frameworks, Libraries, and Embedded Content"**
5. Find `stellar_sdk.xcframework`
6. Change the dropdown to **"Embed & Sign"**

### 3. Add libsodium Dependency

**Option A: Swift Package Manager (Easiest)**

1. In Xcode: **File → Add Package Dependencies**
2. Enter URL: `https://github.com/jedisct1/swift-libsodium`
3. Version: **"Up to Next Major Version"** starting at **1.2.0**
4. Click **"Add Package"**
5. Select your target and click **"Add Package"** again

**Option B: Manual (Alternative)**

If SPM doesn't work, you can manually add libsodium:

1. Download prebuilt: https://download.libsodium.org/libsodium/releases/
2. Extract and find `libsodium.a` for iOS
3. Drag to your project
4. Add to "Link Binary With Libraries"

### 4. Test the Integration

Add this code to a ViewController or SwiftUI view:

```swift
import stellar_sdk

func testStellarSDK() {
    do {
        // Generate a random keypair
        let keypair = KeyPair.Companion().random()
        let accountId = keypair.getAccountId()

        print("✅ Stellar SDK working!")
        print("Account ID: \(accountId)")

        // Test signing
        let data = "Hello, Stellar!".data(using: .utf8)!
        let signature = keypair.sign(data: Array(data))

        // Verify
        let isValid = keypair.verify(
            data: Array(data),
            signature: signature
        )

        print("✅ Signature valid: \(isValid)")

    } catch {
        print("❌ Error: \(error)")
    }
}
```

### 5. Build and Run

1. **Clean** build folder: ⌘ + Shift + K
2. **Build**: ⌘ + B
3. **Run**: ⌘ + R

If everything works, you'll see:
```
✅ Stellar SDK working!
Account ID: GXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX
✅ Signature valid: true
```

## Common Issues & Fixes

### "No such module 'stellar_sdk'"

**Fix:**
- Ensure framework is added to **"Frameworks, Libraries, and Embedded Content"**
- Set to **"Embed & Sign"**, not "Do Not Embed"
- Clean build folder (⌘ + Shift + K)

### "Undefined symbols for architecture arm64"

**Fix:**
- Ensure libsodium is installed via SPM or manually linked
- Check "Link Binary With Libraries" in Build Phases

### "Command PhaseScriptExecution failed"

**Fix:**
- Check Build Settings → Framework Search Paths
- Add: `$(PROJECT_DIR)` if needed

### "dyld: Library not loaded"

**Fix:**
- Framework must be **"Embed & Sign"**, not just linked
- Check that framework appears in app bundle

## Example Project Structure

```
YourApp/
├── YourApp.xcodeproj
├── YourApp/
│   ├── ContentView.swift          # Your UI
│   ├── StellarService.swift       # Wrap SDK calls here
│   └── ...
└── Frameworks/
    └── stellar_sdk.xcframework    # Drag here
```

## Recommended Project Setup

Create a service class to encapsulate Stellar operations:

```swift
import stellar_sdk
import Foundation

class StellarService {

    // Generate new keypair
    func generateKeypair() throws -> KeyPair {
        return KeyPair.Companion().random()
    }

    // Create from secret seed
    func keypair(fromSeed seed: String) throws -> KeyPair {
        return KeyPair.Companion().fromSecretSeed(seed: seed)
    }

    // Sign transaction data
    func sign(_ data: Data, with keypair: KeyPair) throws -> [UInt8] {
        return keypair.sign(data: Array(data))
    }

    // Verify signature
    func verify(data: Data, signature: [UInt8], publicKey: KeyPair) -> Bool {
        return publicKey.verify(data: Array(data), signature: signature)
    }
}
```

## SwiftUI Example

```swift
import SwiftUI
import stellar_sdk

struct ContentView: View {
    @State private var accountId = ""
    @State private var status = "Tap to generate"

    var body: some View {
        VStack(spacing: 20) {
            Text("Stellar SDK Demo")
                .font(.title)

            Button("Generate Keypair") {
                generateKeypair()
            }
            .buttonStyle(.borderedProminent)

            Text(status)
                .foregroundColor(.secondary)

            if !accountId.isEmpty {
                Text(accountId)
                    .font(.caption)
                    .textSelection(.enabled)
                    .padding()
                    .background(Color.gray.opacity(0.1))
                    .cornerRadius(8)
            }
        }
        .padding()
    }

    func generateKeypair() {
        do {
            let keypair = KeyPair.Companion().random()
            accountId = keypair.getAccountId()
            status = "✅ Generated successfully"
        } catch {
            status = "❌ Error: \(error.localizedDescription)"
        }
    }
}
```

## UIKit Example

```swift
import UIKit
import stellar_sdk

class ViewController: UIViewController {

    @IBOutlet weak var accountLabel: UILabel!
    @IBOutlet weak var statusLabel: UILabel!

    @IBAction func generateTapped(_ sender: UIButton) {
        do {
            let keypair = KeyPair.Companion().random()
            accountLabel.text = keypair.getAccountId()
            statusLabel.text = "✅ Generated successfully"
            statusLabel.textColor = .systemGreen
        } catch {
            statusLabel.text = "❌ \(error.localizedDescription)"
            statusLabel.textColor = .systemRed
        }
    }
}
```

## Next Steps

1. **Explore the API**: Check out all available methods in the README
2. **Secure Storage**: Use Keychain to store secret seeds securely
3. **Network Operations**: Integrate with Horizon API for transactions
4. **Error Handling**: Add proper error handling for production use

## Need Help?

- 📚 **Full Documentation**: See `README.md` in this directory
- 🐛 **Issues**: https://github.com/stellar/kmp-stellar-sdk/issues
- 💬 **Community**: https://discord.gg/stellar
- 📧 **Security**: security@stellar.org

## Tips for Production

1. **Never hardcode secret seeds** - Use Keychain Services
2. **Validate user input** before creating keypairs from seeds
3. **Handle errors gracefully** - Network issues, invalid keys, etc.
4. **Test on real devices** - Simulators may have different behavior
5. **Keep libsodium updated** - Check for security updates regularly

---

**You're all set!** 🎉 Start building amazing Stellar applications on iOS.
