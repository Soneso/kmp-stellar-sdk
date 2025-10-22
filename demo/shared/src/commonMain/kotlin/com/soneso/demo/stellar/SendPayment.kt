package com.soneso.demo.stellar

import com.soneso.stellar.sdk.*
import com.soneso.stellar.sdk.horizon.HorizonServer

/**
 * Result type for payment operations.
 */
sealed class SendPaymentResult {
    /**
     * Successful payment operation.
     *
     * @property transactionHash The hash of the submitted transaction
     * @property source The source account ID
     * @property destination The destination account ID
     * @property assetCode The asset code (or "XLM" for native)
     * @property assetIssuer The issuer of the asset (null for native assets)
     * @property amount The amount sent
     * @property message Success message
     */
    data class Success(
        val transactionHash: String,
        val source: String,
        val destination: String,
        val assetCode: String,
        val assetIssuer: String?,
        val amount: String,
        val message: String = "Payment sent successfully"
    ) : SendPaymentResult()

    /**
     * Failed payment operation with error details.
     *
     * @property message Human-readable error message
     * @property exception The underlying exception, if any
     */
    data class Error(
        val message: String,
        val exception: Throwable? = null
    ) : SendPaymentResult()
}

/**
 * Sends a payment on the Stellar network.
 *
 * A payment operation transfers a specified amount of an asset from the source account
 * to the destination account. The destination account must exist and, for non-native assets,
 * must have an established trustline to the asset.
 *
 * ## What is a Payment?
 *
 * Payments are the fundamental way to transfer value on the Stellar network. They can transfer:
 * - Native assets (XLM/Lumens) - no trustline required
 * - Issued assets (tokens) - destination must trust the asset first
 *
 * ## Usage
 *
 * ```kotlin
 * // Send native XLM
 * val result = sendPayment(
 *     sourceAccountId = "GABC...",
 *     destinationAccountId = "GDEF...",
 *     assetCode = "native",
 *     assetIssuer = null,
 *     amount = "10.0",
 *     secretSeed = "SABC..."
 * )
 *
 * // Send issued asset (e.g., USD)
 * val result = sendPayment(
 *     sourceAccountId = "GABC...",
 *     destinationAccountId = "GDEF...",
 *     assetCode = "USD",
 *     assetIssuer = "GISSUER...",
 *     amount = "100.0",
 *     secretSeed = "SABC..."
 * )
 *
 * when (result) {
 *     is SendPaymentResult.Success -> {
 *         println("Payment sent: ${result.transactionHash}")
 *         println("Sent ${result.amount} ${result.assetCode} to ${result.destination}")
 *         result.assetIssuer?.let { issuer ->
 *             println("Asset issued by: $issuer")
 *         }
 *     }
 *     is SendPaymentResult.Error -> {
 *         println("Payment failed: ${result.message}")
 *     }
 * }
 * ```
 *
 * ## Important Notes
 *
 * - Source account must have sufficient balance for the payment amount plus fees
 * - Destination account must exist (use CreateAccount operation for new accounts)
 * - For non-native assets, destination must have a trustline to the asset
 * - Minimum payment amount is 0.0000001 (1 stroop)
 * - Transaction fees are in addition to the payment amount (typically 100 stroops = 0.00001 XLM)
 *
 * @param sourceAccountId The source account ID that sends the payment (must start with 'G')
 * @param destinationAccountId The destination account ID that receives the payment (must start with 'G')
 * @param assetCode The asset code to send ("native" for XLM, or 1-12 alphanumeric characters for issued assets)
 * @param assetIssuer The issuer of the asset (required for issued assets, null for native XLM)
 * @param amount The amount to send (decimal string with up to 7 decimal places)
 * @param secretSeed The secret seed of the source account for signing (must start with 'S')
 * @param useTestnet If true, connects to testnet; otherwise connects to public network (default: true)
 * @return SendPaymentResult.Success if the payment was sent, SendPaymentResult.Error if it failed
 *
 * @see <a href="https://developers.stellar.org/docs/learn/fundamentals/transactions/list-of-operations#payment">Payment Operation</a>
 * @see <a href="https://developers.stellar.org/docs/learn/fundamentals/stellar-data-structures/assets">Assets</a>
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
    return try {
        // Validate source account ID
        if (sourceAccountId.isBlank()) {
            return SendPaymentResult.Error(
                message = "Source account ID cannot be empty"
            )
        }

        if (!sourceAccountId.startsWith('G')) {
            return SendPaymentResult.Error(
                message = "Source account ID must start with 'G' (got: ${sourceAccountId.take(1)})"
            )
        }

        if (sourceAccountId.length != 56) {
            return SendPaymentResult.Error(
                message = "Source account ID must be exactly 56 characters long (got: ${sourceAccountId.length})"
            )
        }

        // Validate destination account ID
        if (destinationAccountId.isBlank()) {
            return SendPaymentResult.Error(
                message = "Destination account ID cannot be empty"
            )
        }

        if (!destinationAccountId.startsWith('G')) {
            return SendPaymentResult.Error(
                message = "Destination account ID must start with 'G' (got: ${destinationAccountId.take(1)})"
            )
        }

        if (destinationAccountId.length != 56) {
            return SendPaymentResult.Error(
                message = "Destination account ID must be exactly 56 characters long (got: ${destinationAccountId.length})"
            )
        }

        // Validate asset code
        if (assetCode.isBlank()) {
            return SendPaymentResult.Error(
                message = "Asset code cannot be empty"
            )
        }

        val isNative = assetCode.equals("native", ignoreCase = true) ||
                       assetCode.equals("XLM", ignoreCase = true)

        if (!isNative) {
            if (assetCode.length > 12) {
                return SendPaymentResult.Error(
                    message = "Asset code cannot exceed 12 characters (got: ${assetCode.length})"
                )
            }

            // Validate asset code contains only alphanumeric characters
            val invalidChars = assetCode.filter { char ->
                char !in 'A'..'Z' && char !in '0'..'9'
            }
            if (invalidChars.isNotEmpty()) {
                return SendPaymentResult.Error(
                    message = "Asset code must contain only uppercase letters and digits. Invalid characters: '$invalidChars'"
                )
            }

            // Validate asset issuer for non-native assets
            if (assetIssuer.isNullOrBlank()) {
                return SendPaymentResult.Error(
                    message = "Asset issuer is required for non-native assets"
                )
            }

            if (!assetIssuer.startsWith('G')) {
                return SendPaymentResult.Error(
                    message = "Asset issuer must start with 'G' (got: ${assetIssuer.take(1)})"
                )
            }

            if (assetIssuer.length != 56) {
                return SendPaymentResult.Error(
                    message = "Asset issuer must be exactly 56 characters long (got: ${assetIssuer.length})"
                )
            }
        }

        // Validate amount
        if (amount.isBlank()) {
            return SendPaymentResult.Error(
                message = "Amount cannot be empty"
            )
        }

        try {
            val amountValue = amount.toDouble()
            if (amountValue <= 0) {
                return SendPaymentResult.Error(
                    message = "Amount must be greater than 0 (got: $amount)"
                )
            }
        } catch (e: NumberFormatException) {
            return SendPaymentResult.Error(
                message = "Invalid amount format: $amount",
                exception = e
            )
        }

        // Validate secret seed
        if (secretSeed.isBlank()) {
            return SendPaymentResult.Error(
                message = "Secret seed cannot be empty"
            )
        }

        if (!secretSeed.startsWith('S')) {
            return SendPaymentResult.Error(
                message = "Secret seed must start with 'S'"
            )
        }

        if (secretSeed.length != 56) {
            return SendPaymentResult.Error(
                message = "Secret seed must be exactly 56 characters long (got: ${secretSeed.length})"
            )
        }

        // Connect to Horizon server
        val horizonUrl = if (useTestnet) {
            "https://horizon-testnet.stellar.org"
        } else {
            "https://horizon.stellar.org"
        }

        val server = HorizonServer(horizonUrl)
        val network = if (useTestnet) Network.TESTNET else Network.PUBLIC

        try {
            // Create keypair from secret seed
            val signerKeyPair = try {
                KeyPair.fromSecretSeed(secretSeed)
            } catch (e: Exception) {
                return SendPaymentResult.Error(
                    message = "Invalid secret seed: ${e.message}",
                    exception = e
                )
            }

            // Verify the source account ID matches the keypair
            val expectedSourceAccountId = signerKeyPair.getAccountId()
            if (expectedSourceAccountId != sourceAccountId) {
                return SendPaymentResult.Error(
                    message = "Secret seed does not match source account ID. Expected: $sourceAccountId, Got: $expectedSourceAccountId"
                )
            }

            // Load the source account from Horizon
            val sourceAccount = try {
                server.loadAccount(sourceAccountId)
            } catch (e: com.soneso.stellar.sdk.horizon.exceptions.BadRequestException) {
                if (e.code == 404) {
                    return SendPaymentResult.Error(
                        message = "Source account not found. The account may not exist or hasn't been funded yet. Fund the account first using FriendBot (testnet) or by receiving XLM.",
                        exception = e
                    )
                } else {
                    return SendPaymentResult.Error(
                        message = "Failed to load source account: ${e.message}",
                        exception = e
                    )
                }
            } catch (e: Exception) {
                return SendPaymentResult.Error(
                    message = "Failed to load source account: ${e.message}",
                    exception = e
                )
            }

            // Create the asset
            val asset = try {
                if (isNative) {
                    AssetTypeNative
                } else {
                    Asset.createNonNativeAsset(assetCode, assetIssuer!!)
                }
            } catch (e: Exception) {
                return SendPaymentResult.Error(
                    message = "Invalid asset parameters: ${e.message}",
                    exception = e
                )
            }

            // Create the Payment operation
            val paymentOperation = try {
                PaymentOperation(
                    destination = destinationAccountId,
                    asset = asset,
                    amount = amount
                )
            } catch (e: Exception) {
                return SendPaymentResult.Error(
                    message = "Failed to create Payment operation: ${e.message}",
                    exception = e
                )
            }

            // Build the transaction
            val transaction = try {
                TransactionBuilder(sourceAccount, network)
                    .setBaseFee(AbstractTransaction.MIN_BASE_FEE) // 100 stroops
                    .addOperation(paymentOperation)
                    // Use infinite timeout (no time bounds)
                    .build()
            } catch (e: Exception) {
                return SendPaymentResult.Error(
                    message = "Failed to build transaction: ${e.message}",
                    exception = e
                )
            }

            // Sign the transaction
            try {
                transaction.sign(signerKeyPair)
            } catch (e: Exception) {
                return SendPaymentResult.Error(
                    message = "Failed to sign transaction: ${e.message}",
                    exception = e
                )
            }

            // Submit the transaction
            val response = try {
                server.submitTransaction(transaction.toEnvelopeXdrBase64())
            } catch (e: com.soneso.stellar.sdk.horizon.exceptions.BadRequestException) {
                // Parse error details from response
                val errorMessage = when {
                    e.code == 400 && e.body?.contains("op_no_destination") == true -> {
                        "Destination account does not exist. Create the account first by sending it at least 1 XLM."
                    }
                    e.code == 400 && e.body?.contains("op_no_trust") == true -> {
                        "Destination account does not trust the asset. The destination must establish a trustline first."
                    }
                    e.code == 400 && e.body?.contains("op_underfunded") == true -> {
                        "Insufficient balance in source account for this payment (including transaction fees)."
                    }
                    e.code == 400 -> {
                        val bodyMsg = e.body ?: "Unknown error"
                        "Transaction failed: $bodyMsg. This could be due to insufficient balance, destination account issues, or invalid operation parameters."
                    }
                    else -> {
                        "Transaction rejected: ${e.message}"
                    }
                }
                return SendPaymentResult.Error(
                    message = errorMessage,
                    exception = e
                )
            } catch (e: com.soneso.stellar.sdk.horizon.exceptions.NetworkException) {
                return SendPaymentResult.Error(
                    message = "Network error while submitting transaction: ${e.message ?: "Failed to connect to Horizon"}",
                    exception = e
                )
            } catch (e: Exception) {
                return SendPaymentResult.Error(
                    message = "Failed to submit transaction: ${e.message}",
                    exception = e
                )
            }

            // Determine the display asset code and issuer
            val displayAssetCode = if (isNative) "XLM" else assetCode
            val displayAssetIssuer = if (isNative) null else assetIssuer

            // Return success with transaction details
            SendPaymentResult.Success(
                transactionHash = response.hash,
                source = sourceAccountId,
                destination = destinationAccountId,
                assetCode = displayAssetCode,
                assetIssuer = displayAssetIssuer,
                amount = amount,
                message = "Successfully sent $amount $displayAssetCode to ${shortenAccountId(destinationAccountId)}"
            )
        } finally {
            // Clean up HTTP client resources
            server.close()
        }
    } catch (e: Exception) {
        // Catch any unexpected errors
        SendPaymentResult.Error(
            message = "Unexpected error: ${e.message ?: "Unknown error occurred"}",
            exception = e
        )
    }
}

/**
 * Shortens an account ID for display purposes.
 * Shows first 4 and last 4 characters with "..." in between.
 *
 * @param accountId The full account ID
 * @return Shortened account ID (e.g., "GABC...XYZ1")
 */
private fun shortenAccountId(accountId: String): String {
    return if (accountId.length > 12) {
        "${accountId.take(4)}...${accountId.takeLast(4)}"
    } else {
        accountId
    }
}
