import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import com.soneso.smartdemo.App
import com.soneso.smartdemo.config.DemoConfig
import com.soneso.smartdemo.state.ActivityLogState
import com.soneso.smartdemo.state.DemoState
import com.soneso.stellar.sdk.smartaccount.IndexedDBStorageAdapter
import com.soneso.stellar.sdk.smartaccount.JsWebAuthnProvider
import com.soneso.stellar.sdk.smartaccount.oz.OZSmartAccountConfig
import com.soneso.stellar.sdk.smartaccount.oz.OZSmartAccountKit
import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    // Initialize Smart Account Kit on launch
    MainScope().launch {
        try {
            // Use current domain for development, production should configure explicitly
            val currentDomain = window.location.hostname

            // Create IndexedDB storage for persistent credential storage
            val storage = IndexedDBStorageAdapter(dbName = "smart-account-demo")

            // Create WebAuthn provider with current domain as RP ID
            val webauthnProvider = JsWebAuthnProvider(
                rpId = currentDomain,
                rpName = DemoConfig.RP_NAME
            )

            // Build configuration with WebAuthn provider
            val config = OZSmartAccountConfig(
                rpcUrl = DemoConfig.RPC_URL,
                networkPassphrase = DemoConfig.NETWORK_PASSPHRASE,
                accountWasmHash = DemoConfig.ACCOUNT_WASM_HASH,
                webauthnVerifierAddress = DemoConfig.WEBAUTHN_VERIFIER_ADDRESS,
                rpName = DemoConfig.RP_NAME,
                webauthnProvider = webauthnProvider
            )

            // Create the kit instance
            val kit = OZSmartAccountKit.create(
                config = config,
                storage = storage
            )

            DemoState.setKitInstance(kit)
            ActivityLogState.info("Smart Account Kit initialized (Web platform)")
            ActivityLogState.info("WebAuthn RP ID: $currentDomain")
        } catch (e: Exception) {
            ActivityLogState.error("Failed to initialize Smart Account Kit: ${e.message}")
            console.error("Kit initialization error", e)
        }
    }

    // Launch Compose UI (does not wait for kit initialization)
    ComposeViewport(document.getElementById("ComposeTarget")!!) {
        App()
    }
}
