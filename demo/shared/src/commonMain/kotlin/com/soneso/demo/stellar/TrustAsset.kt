package com.soneso.demo.stellar

import com.soneso.stellar.sdk.*
import com.soneso.stellar.sdk.horizon.HorizonServer
import com.soneso.demo.util.StellarValidation

/**
 * Result type for trust asset operations.
 */
sealed class TrustAssetResult {
    /**
     * Successful trust asset operation.
     *
     * @property transactionHash The hash of the submitted transaction
     * @property assetCode The asset code that was trusted
     * @property assetIssuer The issuer of the trusted asset
     * @property limit The trust limit that was set
     * @property message Success message
     */
    data class Success(
        val transactionHash: String,
        val assetCode: String,
        val assetIssuer: String,
        val limit: String,
        val message: String = "Trustline established successfully"
    ) : TrustAssetResult()

    /**
     * Failed trust asset operation with error details.
     *
     * @property message Human-readable error message
     * @property exception The underlying exception, if any
     */
    data class Error(
        val message: String,
        val exception: Throwable? = null
    ) : TrustAssetResult()
}

/**
 * Establishes a trustline to an asset on the Stellar testnet.
 *
 * A trustline is required before an account can hold non-native assets (assets other than XLM).
 * This function creates a ChangeTrust operation that allows the account to hold up to a specified
 * limit of the asset.
 *
 * ## What is a Trustline?
 *
 * In Stellar, accounts must explicitly trust an asset issuer before they can receive that asset.
 * This is a security feature that prevents accounts from receiving unwanted assets. The trustline
 * includes a limit that defines the maximum amount of the asset the account is willing to hold.
 *
 * ## Usage
 *
 * ```kotlin
 * val result = trustAsset(
 *     accountId = "GABC...",
 *     assetCode = "USD",
 *     assetIssuer = "GDEF...",
 *     secretSeed = "SABC...",
 *     limit = "1000.0" // Optional, defaults to maximum
 * )
 *
 * when (result) {
 *     is TrustAssetResult.Success -> {
 *         println("Trustline created: ${result.transactionHash}")
 *         println("Can now receive up to ${result.limit} ${result.assetCode}")
 *     }
 *     is TrustAssetResult.Error -> {
 *         println("Failed to create trustline: ${result.message}")
 *     }
 * }
 * ```
 *
 * ## Important Notes
 *
 * - The account must have enough XLM to meet the base reserve requirement (0.5 XLM per trustline)
 * - Setting the limit to "0" removes the trustline (account must have zero balance of that asset)
 * - The maximum limit is approximately 922 trillion (922337203685.4775807)
 * - Asset codes must be 1-12 alphanumeric characters (uppercase letters and digits only)
 * - Both the account and issuer must be valid Stellar account IDs (G... addresses)
 *
 * @param accountId The account ID that will trust the asset (must start with 'G')
 * @param assetCode The asset code to trust (1-12 alphanumeric characters, e.g., "USD", "USDC")
 * @param assetIssuer The issuer of the asset (must start with 'G')
 * @param secretSeed The secret seed for signing the transaction (must start with 'S')
 * @param limit The maximum amount of the asset to trust (defaults to maximum limit)
 * @return TrustAssetResult.Success if the trustline was established, TrustAssetResult.Error if it failed
 *
 * @see <a href="https://developers.stellar.org/docs/learn/fundamentals/stellar-data-structures/accounts#trustlines">Trustlines Documentation</a>
 * @see <a href="https://developers.stellar.org/docs/learn/fundamentals/transactions/list-of-operations#change-trust">ChangeTrust Operation</a>
 */
suspend fun trustAsset(
    accountId: String,
    assetCode: String,
    assetIssuer: String,
    secretSeed: String,
    limit: String = ChangeTrustOperation.MAX_LIMIT
): TrustAssetResult {
    return try {
        // Validate input parameters
        StellarValidation.validateAccountId(accountId)?.let { error ->
            return TrustAssetResult.Error(message = error)
        }

        StellarValidation.validateAssetCode(assetCode)?.let { error ->
            return TrustAssetResult.Error(message = error)
        }

        if (assetIssuer.isBlank()) {
            return TrustAssetResult.Error(
                message = "Asset issuer cannot be empty"
            )
        }

        StellarValidation.validateAccountId(assetIssuer)?.let { error ->
            return TrustAssetResult.Error(message = error.replace("Account ID", "Asset issuer"))
        }

        StellarValidation.validateSecretSeed(secretSeed)?.let { error ->
            return TrustAssetResult.Error(message = error)
        }

        // Validate limit (basic validation - SDK will do more thorough validation)
        try {
            val limitValue = limit.toDouble()
            if (limitValue < 0) {
                return TrustAssetResult.Error(
                    message = "Trust limit cannot be negative (got: $limit)"
                )
            }
        } catch (e: NumberFormatException) {
            return TrustAssetResult.Error(
                message = "Invalid trust limit format: $limit",
                exception = e
            )
        }

        // Connect to Horizon testnet server
        val horizonUrl = "https://horizon-testnet.stellar.org"

        val server = HorizonServer(horizonUrl)
        val network = Network.TESTNET

        try {
            // Create keypair from secret seed
            val signerKeyPair = try {
                KeyPair.fromSecretSeed(secretSeed)
            } catch (e: Exception) {
                return TrustAssetResult.Error(
                    message = "Invalid secret seed: ${e.message}",
                    exception = e
                )
            }

            // Verify the account ID matches the keypair
            val expectedAccountId = signerKeyPair.getAccountId()
            if (expectedAccountId != accountId) {
                return TrustAssetResult.Error(
                    message = "Secret seed does not match account ID. Expected: $accountId, Got: $expectedAccountId"
                )
            }

            // Load the source account from Horizon
            val sourceAccount = try {
                server.loadAccount(accountId)
            } catch (e: com.soneso.stellar.sdk.horizon.exceptions.BadRequestException) {
                if (e.code == 404) {
                    return TrustAssetResult.Error(
                        message = "Account not found. The account may not exist or hasn't been funded yet. Fund the account first using FriendBot (testnet) or by receiving XLM.",
                        exception = e
                    )
                } else {
                    return TrustAssetResult.Error(
                        message = "Failed to load account: ${e.message}",
                        exception = e
                    )
                }
            } catch (e: Exception) {
                return TrustAssetResult.Error(
                    message = "Failed to load account: ${e.message}",
                    exception = e
                )
            }

            // Create the asset
            val asset = try {
                Asset.createNonNativeAsset(assetCode, assetIssuer)
            } catch (e: Exception) {
                return TrustAssetResult.Error(
                    message = "Invalid asset parameters: ${e.message}",
                    exception = e
                )
            }

            // Create the ChangeTrust operation
            val changeTrustOperation = try {
                ChangeTrustOperation(asset, limit)
            } catch (e: Exception) {
                return TrustAssetResult.Error(
                    message = "Failed to create ChangeTrust operation: ${e.message}",
                    exception = e
                )
            }

            // Build the transaction
            val transaction = try {
                TransactionBuilder(sourceAccount, network)
                    .setBaseFee(AbstractTransaction.MIN_BASE_FEE) // 100 stroops
                    .addOperation(changeTrustOperation)
                    // Use infinite timeout (no time bounds)
                    .build()
            } catch (e: Exception) {
                return TrustAssetResult.Error(
                    message = "Failed to build transaction: ${e.message}",
                    exception = e
                )
            }

            // Sign the transaction
            try {
                transaction.sign(signerKeyPair)
            } catch (e: Exception) {
                return TrustAssetResult.Error(
                    message = "Failed to sign transaction: ${e.message}",
                    exception = e
                )
            }

            // Submit the transaction
            val response = try {
                server.submitTransaction(transaction.toEnvelopeXdrBase64())
            } catch (e: com.soneso.stellar.sdk.horizon.exceptions.BadRequestException) {
                // Parse error details from response
                val errorMessage = if (e.code == 400) {
                    "Transaction failed: ${e.body}. This could be due to insufficient XLM balance, invalid sequence number, or operation-specific errors."
                } else {
                    "Transaction rejected: ${e.message}"
                }
                return TrustAssetResult.Error(
                    message = errorMessage,
                    exception = e
                )
            } catch (e: com.soneso.stellar.sdk.horizon.exceptions.NetworkException) {
                return TrustAssetResult.Error(
                    message = "Network error while submitting transaction: ${e.message ?: "Failed to connect to Horizon"}",
                    exception = e
                )
            } catch (e: Exception) {
                return TrustAssetResult.Error(
                    message = "Failed to submit transaction: ${e.message}",
                    exception = e
                )
            }

            // Return success with transaction details
            TrustAssetResult.Success(
                transactionHash = response.hash,
                assetCode = assetCode,
                assetIssuer = assetIssuer,
                limit = limit,
                message = "Trustline established successfully"
            )
        } finally {
            // Clean up HTTP client resources
            server.close()
        }
    } catch (e: Exception) {
        // Catch any unexpected errors
        TrustAssetResult.Error(
            message = "Unexpected error: ${e.message ?: "Unknown error occurred"}",
            exception = e
        )
    }
}
