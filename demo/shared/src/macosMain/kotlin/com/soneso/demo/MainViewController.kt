package com.soneso.demo

import com.soneso.demo.stellar.AccountDetailsResult
import com.soneso.demo.stellar.AccountFundingResult
import com.soneso.demo.stellar.KeyPairGenerationResult
import com.soneso.demo.stellar.SendPaymentResult
import com.soneso.demo.stellar.TrustAssetResult
import com.soneso.demo.stellar.fetchAccountDetails
import com.soneso.demo.stellar.fundTestnetAccount
import com.soneso.demo.stellar.generateRandomKeyPair
import com.soneso.demo.stellar.sendPayment
import com.soneso.demo.stellar.trustAsset
import com.soneso.stellar.sdk.KeyPair

/**
 * Bridge between Swift UI and Kotlin business logic for native macOS app.
 *
 * Note: Native macOS Compose Multiplatform does not have window management APIs
 * like iOS (ComposeUIViewController) or JVM Desktop (Window/application).
 *
 * For a native macOS app, we provide business logic functions that SwiftUI can call.
 * For a full Compose UI experience on macOS, use the JVM desktop target instead
 * (see demo/desktopApp/).
 *
 * This approach allows:
 * - Native macOS app with SwiftUI
 * - Shared business logic from Kotlin
 * - Access to the Stellar SDK
 */
class MacOSBridge {

    /**
     * Generate a random Stellar keypair asynchronously.
     * Call this from Swift using async/await.
     *
     * Uses the centralized KeyPairGeneration business logic to maintain consistency
     * across all platform UIs (Compose, SwiftUI, Web).
     *
     * @return KeyPair instance from the Stellar SDK
     * @throws Exception if keypair generation fails
     */
    suspend fun generateKeypair(): KeyPair {
        return when (val result = generateRandomKeyPair()) {
            is KeyPairGenerationResult.Success -> result.keyPair
            is KeyPairGenerationResult.Error -> {
                throw Exception(result.message, result.exception)
            }
        }
    }

    /**
     * Fund a Stellar testnet account using the SDK's built-in FriendBot service.
     * Call this from Swift using async/await.
     *
     * Uses the centralized AccountFunding business logic to maintain consistency
     * across all platform UIs (Compose, SwiftUI, Web).
     *
     * @param accountId The Stellar account ID to fund (must start with 'G')
     * @return AccountFundingResult indicating success or failure
     */
    suspend fun fundAccount(accountId: String): AccountFundingResult {
        return fundTestnetAccount(accountId)
    }

    /**
     * Fetch detailed account information from the Stellar network.
     * Call this from Swift using async/await.
     *
     * Uses the centralized AccountDetails business logic to maintain consistency
     * across all platform UIs (Compose, SwiftUI, Web).
     *
     * @param accountId The Stellar account ID to fetch (must start with 'G')
     * @param useTestnet If true, connects to testnet; otherwise connects to public network
     * @return AccountDetailsResult with full account data or error details
     */
    suspend fun fetchAccountDetails(accountId: String, useTestnet: Boolean = true): AccountDetailsResult {
        return com.soneso.demo.stellar.fetchAccountDetails(accountId, useTestnet)
    }

    /**
     * Establish a trustline to a Stellar asset.
     * Call this from Swift using async/await.
     *
     * Uses the centralized TrustAsset business logic to maintain consistency
     * across all platform UIs (Compose, SwiftUI, Web).
     *
     * @param accountId The account ID that will trust the asset (must start with 'G')
     * @param assetCode The asset code to trust (1-12 alphanumeric characters)
     * @param assetIssuer The issuer of the asset (must start with 'G')
     * @param secretSeed The secret seed for signing the transaction (must start with 'S')
     * @param limit The maximum amount of the asset to trust (defaults to maximum limit)
     * @param useTestnet If true, connects to testnet; otherwise connects to public network
     * @return TrustAssetResult indicating success with transaction hash or failure with error details
     */
    suspend fun trustAsset(
        accountId: String,
        assetCode: String,
        assetIssuer: String,
        secretSeed: String,
        limit: String = com.soneso.stellar.sdk.ChangeTrustOperation.MAX_LIMIT,
        useTestnet: Boolean = true
    ): TrustAssetResult {
        return com.soneso.demo.stellar.trustAsset(
            accountId = accountId,
            assetCode = assetCode,
            assetIssuer = assetIssuer,
            secretSeed = secretSeed,
            limit = limit,
            useTestnet = useTestnet
        )
    }

    /**
     * Send a payment on the Stellar network.
     * Call this from Swift using async/await.
     *
     * Uses the centralized SendPayment business logic to maintain consistency
     * across all platform UIs (Compose, SwiftUI, Web).
     *
     * @param sourceAccountId The source account ID that sends the payment (must start with 'G')
     * @param destinationAccountId The destination account ID that receives the payment (must start with 'G')
     * @param assetCode The asset code to send ("native" for XLM, or 1-12 alphanumeric characters for issued assets)
     * @param assetIssuer The issuer of the asset (required for issued assets, null for native XLM)
     * @param amount The amount to send (decimal string with up to 7 decimal places)
     * @param secretSeed The secret seed of the source account for signing (must start with 'S')
     * @param useTestnet If true, connects to testnet; otherwise connects to public network
     * @return SendPaymentResult indicating success with transaction details or failure with error details
     */
    suspend fun sendPayment(
        sourceAccountId: String,
        destinationAccountId: String,
        assetCode: String,
        assetIssuer: String?,
        amount: String,
        secretSeed: String,
        useTestnet: Boolean = true
    ): SendPaymentResult {
        return com.soneso.demo.stellar.sendPayment(
            sourceAccountId = sourceAccountId,
            destinationAccountId = destinationAccountId,
            assetCode = assetCode,
            assetIssuer = assetIssuer,
            amount = amount,
            secretSeed = secretSeed,
            useTestnet = useTestnet
        )
    }
}
