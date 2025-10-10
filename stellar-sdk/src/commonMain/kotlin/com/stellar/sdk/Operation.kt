package com.stellar.sdk

import com.stellar.sdk.xdr.*

/**
 * Abstract base class for all Stellar operations.
 *
 * Operations are the commands that modify the ledger. They represent actions like sending payments,
 * creating accounts, managing offers, and more. Operations are grouped into transactions and
 * submitted to the network.
 *
 * ## Operation Types
 *
 * This sealed class hierarchy includes all 27 operation types supported by the Stellar protocol.
 *
 * ## Source Account
 *
 * Each operation can optionally have a source account that differs from the transaction's
 * source account. This allows operations within a single transaction to be performed by
 * different accounts.
 *
 * ## Usage
 *
 * ```kotlin
 * // Create a payment operation
 * val payment = PaymentOperation(
 *     destination = "GBRPYHIL2CI3FNQ4BXLFMNDLFJUNPU2HY3ZMFSHONUCEOASW7QC7OX2H",
 *     asset = AssetTypeNative,
 *     amount = "10.0000000"
 * )
 *
 * // Set a custom source account
 * payment.sourceAccount = "GABC..."
 *
 * // Convert to XDR for submission
 * val xdr = payment.toXdr()
 * ```
 *
 * @property sourceAccount Optional source account for this operation. If null, the transaction's
 *                         source account is used. Can be a regular account ID (G...) or a muxed
 *                         account address (M...).
 *
 * @see <a href="https://developers.stellar.org/docs/learn/fundamentals/transactions/list-of-operations">List of Operations</a>
 */
sealed class Operation {
    /**
     * Optional source account for this operation.
     *
     * If null, the transaction's source account is used. Can be a regular account ID (G...)
     * or a muxed account address (M...).
     */
    var sourceAccount: String? = null

    /**
     * Converts this operation to its XDR operation body representation.
     *
     * @return The XDR OperationBody object
     */
    abstract fun toOperationBody(): OperationBodyXdr

    /**
     * Converts this operation to its full XDR representation, including the source account.
     *
     * @return The XDR Operation object
     */
    fun toXdr(): OperationXdr {
        val sourceAccountXdr = sourceAccount?.let { MuxedAccount(it).toXdr() }
        return OperationXdr(
            sourceAccount = sourceAccountXdr,
            body = toOperationBody()
        )
    }

    companion object {
        /**
         * Converts an XDR Amount (Int64) to a decimal amount string.
         *
         * @param value The amount in stroops (10^-7 of the base unit)
         * @return The decimal amount string with 7 decimal places
         */
        internal fun fromXdrAmount(value: Long): String {
            return Util.toAmountString(value)
        }

        /**
         * Converts a decimal amount string to XDR Amount (Int64 stroops).
         *
         * @param value The decimal amount string
         * @return The amount in stroops (10^-7 of the base unit)
         * @throws IllegalArgumentException if the amount has more than 7 decimal places
         */
        internal fun toXdrAmount(value: String): Long {
            return Util.toStroops(value)
        }

        /**
         * Formats an amount string to have exactly 7 decimal places.
         *
         * @param value The amount string
         * @return The formatted amount string
         * @throws IllegalArgumentException if the amount has more than 7 decimal places
         */
        internal fun formatAmountScale(value: String): String {
            return Util.formatAmountScale(value)
        }

        /**
         * Decodes an Operation from its XDR representation.
         *
         * This is a factory method that creates the appropriate Operation subclass based on
         * the XDR operation type.
         *
         * @param xdr The XDR Operation object
         * @return The decoded Operation instance
         * @throws IllegalArgumentException if the operation type is unknown
         */
        fun fromXdr(xdr: OperationXdr): Operation {
            val operation = when (val body = xdr.body) {
                is OperationBodyXdr.CreateAccountOp -> CreateAccountOperation.fromXdr(body.value)
                is OperationBodyXdr.PaymentOp -> PaymentOperation.fromXdr(body.value)
                is OperationBodyXdr.PathPaymentStrictReceiveOp -> PathPaymentStrictReceiveOperation.fromXdr(body.value)
                is OperationBodyXdr.PathPaymentStrictSendOp -> PathPaymentStrictSendOperation.fromXdr(body.value)
                is OperationBodyXdr.ManageSellOfferOp -> ManageSellOfferOperation.fromXdr(body.value)
                is OperationBodyXdr.CreatePassiveSellOfferOp -> CreatePassiveSellOfferOperation.fromXdr(body.value)
                is OperationBodyXdr.SetOptionsOp -> SetOptionsOperation.fromXdr(body.value)
                is OperationBodyXdr.ChangeTrustOp -> ChangeTrustOperation.fromXdr(body.value)
                is OperationBodyXdr.AllowTrustOp -> AllowTrustOperation.fromXdr(body.value)
                is OperationBodyXdr.Destination -> AccountMergeOperation.fromXdr(body.value)
                is OperationBodyXdr.Void -> {
                    // Handle void operations based on discriminant
                    when (body.discriminant) {
                        OperationTypeXdr.INFLATION -> InflationOperation()
                        OperationTypeXdr.END_SPONSORING_FUTURE_RESERVES -> EndSponsoringFutureReservesOperation()
                        else -> throw IllegalArgumentException("Unknown void operation type: ${body.discriminant}")
                    }
                }
                is OperationBodyXdr.ManageDataOp -> ManageDataOperation.fromXdr(body.value)
                is OperationBodyXdr.BumpSequenceOp -> BumpSequenceOperation.fromXdr(body.value)
                is OperationBodyXdr.ManageBuyOfferOp -> ManageBuyOfferOperation.fromXdr(body.value)
                is OperationBodyXdr.CreateClaimableBalanceOp -> CreateClaimableBalanceOperation.fromXdr(body.value)
                is OperationBodyXdr.ClaimClaimableBalanceOp -> ClaimClaimableBalanceOperation.fromXdr(body.value)
                is OperationBodyXdr.BeginSponsoringFutureReservesOp -> BeginSponsoringFutureReservesOperation.fromXdr(body.value)
                is OperationBodyXdr.RevokeSponsorshipOp -> RevokeSponsorshipOperation.fromXdr(body.value)
                is OperationBodyXdr.ClawbackOp -> ClawbackOperation.fromXdr(body.value)
                is OperationBodyXdr.ClawbackClaimableBalanceOp -> ClawbackClaimableBalanceOperation.fromXdr(body.value)
                is OperationBodyXdr.SetTrustLineFlagsOp -> SetTrustLineFlagsOperation.fromXdr(body.value)
                is OperationBodyXdr.LiquidityPoolDepositOp -> LiquidityPoolDepositOperation.fromXdr(body.value)
                is OperationBodyXdr.LiquidityPoolWithdrawOp -> LiquidityPoolWithdrawOperation.fromXdr(body.value)
                is OperationBodyXdr.InvokeHostFunctionOp -> InvokeHostFunctionOperation.fromXdr(body.value)
                is OperationBodyXdr.ExtendFootprintTTLOp -> ExtendFootprintTTLOperation.fromXdr(body.value)
                is OperationBodyXdr.RestoreFootprintOp -> RestoreFootprintOperation.fromXdr(body.value)
            }

            // Set the source account if present
            xdr.sourceAccount?.let {
                operation.sourceAccount = MuxedAccount.fromXdr(it).address
            }

            return operation
        }
    }
}

/**
 * Represents a [CreateAccount](https://developers.stellar.org/docs/learn/fundamentals/transactions/list-of-operations#create-account) operation.
 *
 * @property destination Account address that will be created
 * @property startingBalance Amount of XLM to send to the newly created account
 */
data class CreateAccountOperation(
    val destination: String,
    val startingBalance: String
) : Operation() {

    init {
        require(StrKey.isValidEd25519PublicKey(destination)) {
            "Invalid destination account: $destination"
        }
        toXdrAmount(startingBalance) // Validate
    }

    override fun toOperationBody(): OperationBodyXdr {
        val op = CreateAccountOpXdr(
            destination = KeyPair.fromAccountId(destination).getXdrAccountId(),
            startingBalance = Int64Xdr(toXdrAmount(startingBalance))
        )
        return OperationBodyXdr.CreateAccountOp(op)
    }

    companion object {
        fun fromXdr(op: CreateAccountOpXdr): CreateAccountOperation {
            val destination = when (val accountId = op.destination.value) {
                is PublicKeyXdr.Ed25519 -> StrKey.encodeEd25519PublicKey(accountId.value.value)
            }
            val startingBalance = fromXdrAmount(op.startingBalance.value)
            return CreateAccountOperation(destination, startingBalance)
        }
    }
}

/**
 * Represents a [Payment](https://developers.stellar.org/docs/learn/fundamentals/transactions/list-of-operations#payment) operation.
 *
 * @property destination Account address that receives the payment
 * @property asset Asset to send to the destination account
 * @property amount Amount of the asset to send
 */
data class PaymentOperation(
    val destination: String,
    val asset: Asset,
    val amount: String
) : Operation() {

    init {
        require(
            StrKey.isValidEd25519PublicKey(destination) ||
            StrKey.isValidMed25519PublicKey(destination)
        ) {
            "Invalid destination account: $destination"
        }
        toXdrAmount(amount) // Validate
    }

    override fun toOperationBody(): OperationBodyXdr {
        val op = PaymentOpXdr(
            destination = MuxedAccount(destination).toXdr(),
            asset = asset.toXdr(),
            amount = Int64Xdr(toXdrAmount(amount))
        )
        return OperationBodyXdr.PaymentOp(op)
    }

    companion object {
        fun fromXdr(op: PaymentOpXdr): PaymentOperation {
            val destination = MuxedAccount.fromXdr(op.destination).address
            val asset = Asset.fromXdr(op.asset)
            val amount = fromXdrAmount(op.amount.value)
            return PaymentOperation(destination, asset, amount)
        }
    }
}

/**
 * Represents a [PathPaymentStrictReceive](https://developers.stellar.org/docs/learn/fundamentals/transactions/list-of-operations#path-payment-strict-receive) operation.
 *
 * @property sendAsset The asset deducted from the sender's account
 * @property sendMax Maximum amount of send asset to deduct
 * @property destination Account that receives the payment
 * @property destAsset The asset the destination account receives
 * @property destAmount The exact amount of destination asset to receive
 * @property path Assets involved in the offers the path takes (max 5 assets)
 */
data class PathPaymentStrictReceiveOperation(
    val sendAsset: Asset,
    val sendMax: String,
    val destination: String,
    val destAsset: Asset,
    val destAmount: String,
    val path: List<Asset> = emptyList()
) : Operation() {

    init {
        require(
            StrKey.isValidEd25519PublicKey(destination) ||
            StrKey.isValidMed25519PublicKey(destination)
        ) {
            "Invalid destination account: $destination"
        }
        toXdrAmount(sendMax) // Validate
        toXdrAmount(destAmount) // Validate
        require(path.size <= 5) {
            "The maximum number of assets in the path is 5, got ${path.size}"
        }
    }

    override fun toOperationBody(): OperationBodyXdr {
        val op = PathPaymentStrictReceiveOpXdr(
            sendAsset = sendAsset.toXdr(),
            sendMax = Int64Xdr(toXdrAmount(sendMax)),
            destination = MuxedAccount(destination).toXdr(),
            destAsset = destAsset.toXdr(),
            destAmount = Int64Xdr(toXdrAmount(destAmount)),
            path = path.map { it.toXdr() }
        )
        return OperationBodyXdr.PathPaymentStrictReceiveOp(op)
    }

    companion object {
        fun fromXdr(op: PathPaymentStrictReceiveOpXdr): PathPaymentStrictReceiveOperation {
            val sendAsset = Asset.fromXdr(op.sendAsset)
            val sendMax = fromXdrAmount(op.sendMax.value)
            val destination = MuxedAccount.fromXdr(op.destination).address
            val destAsset = Asset.fromXdr(op.destAsset)
            val destAmount = fromXdrAmount(op.destAmount.value)
            val path = op.path.map { Asset.fromXdr(it) }

            return PathPaymentStrictReceiveOperation(
                sendAsset = sendAsset,
                sendMax = sendMax,
                destination = destination,
                destAsset = destAsset,
                destAmount = destAmount,
                path = path
            )
        }
    }
}

/**
 * Represents a [PathPaymentStrictSend](https://developers.stellar.org/docs/learn/fundamentals/transactions/list-of-operations#path-payment-strict-send) operation.
 *
 * @property sendAsset The asset deducted from the sender's account
 * @property sendAmount The exact amount of send asset to deduct
 * @property destination Account that receives the payment
 * @property destAsset The asset the destination account receives
 * @property destMin Minimum amount of destination asset to receive
 * @property path Assets involved in the offers the path takes (max 5 assets)
 */
data class PathPaymentStrictSendOperation(
    val sendAsset: Asset,
    val sendAmount: String,
    val destination: String,
    val destAsset: Asset,
    val destMin: String,
    val path: List<Asset> = emptyList()
) : Operation() {

    init {
        require(
            StrKey.isValidEd25519PublicKey(destination) ||
            StrKey.isValidMed25519PublicKey(destination)
        ) {
            "Invalid destination account: $destination"
        }
        toXdrAmount(sendAmount) // Validate
        toXdrAmount(destMin) // Validate
        require(path.size <= 5) {
            "The maximum number of assets in the path is 5, got ${path.size}"
        }
    }

    override fun toOperationBody(): OperationBodyXdr {
        val op = PathPaymentStrictSendOpXdr(
            sendAsset = sendAsset.toXdr(),
            sendAmount = Int64Xdr(toXdrAmount(sendAmount)),
            destination = MuxedAccount(destination).toXdr(),
            destAsset = destAsset.toXdr(),
            destMin = Int64Xdr(toXdrAmount(destMin)),
            path = path.map { it.toXdr() }
        )
        return OperationBodyXdr.PathPaymentStrictSendOp(op)
    }

    companion object {
        fun fromXdr(op: PathPaymentStrictSendOpXdr): PathPaymentStrictSendOperation {
            val sendAsset = Asset.fromXdr(op.sendAsset)
            val sendAmount = fromXdrAmount(op.sendAmount.value)
            val destination = MuxedAccount.fromXdr(op.destination).address
            val destAsset = Asset.fromXdr(op.destAsset)
            val destMin = fromXdrAmount(op.destMin.value)
            val path = op.path.map { Asset.fromXdr(it) }

            return PathPaymentStrictSendOperation(
                sendAsset = sendAsset,
                sendAmount = sendAmount,
                destination = destination,
                destAsset = destAsset,
                destMin = destMin,
                path = path
            )
        }
    }
}

/**
 * Represents a [ManageSellOffer](https://developers.stellar.org/docs/learn/fundamentals/transactions/list-of-operations#manage-sell-offer) operation.
 *
 * @property selling The asset being sold in this operation
 * @property buying The asset being bought in this operation
 * @property amount Amount of selling being sold
 * @property price Price of 1 unit of selling in terms of buying
 * @property offerId The ID of the offer (0 for new, existing ID to update/delete)
 */
data class ManageSellOfferOperation(
    val selling: Asset,
    val buying: Asset,
    val amount: String,
    val price: Price,
    val offerId: Long = 0
) : Operation() {

    init {
        require(selling != buying) {
            "Selling and buying assets must be different"
        }
        toXdrAmount(amount) // Validate
        require(offerId >= 0) { "Offer ID must be non-negative, got $offerId" }
    }

    override fun toOperationBody(): OperationBodyXdr {
        val op = ManageSellOfferOpXdr(
            selling = selling.toXdr(),
            buying = buying.toXdr(),
            amount = Int64Xdr(toXdrAmount(amount)),
            price = price.toXdr(),
            offerId = Int64Xdr(offerId)
        )
        return OperationBodyXdr.ManageSellOfferOp(op)
    }

    companion object {
        fun fromXdr(op: ManageSellOfferOpXdr): ManageSellOfferOperation {
            val selling = Asset.fromXdr(op.selling)
            val buying = Asset.fromXdr(op.buying)
            val amount = fromXdrAmount(op.amount.value)
            val price = Price.fromXdr(op.price)
            val offerId = op.offerId.value

            return ManageSellOfferOperation(
                selling = selling,
                buying = buying,
                amount = amount,
                price = price,
                offerId = offerId
            )
        }
    }
}

/**
 * Represents a [ManageBuyOffer](https://developers.stellar.org/docs/learn/fundamentals/transactions/list-of-operations#manage-buy-offer) operation.
 *
 * @property selling The asset being sold in this operation
 * @property buying The asset being bought in this operation
 * @property buyAmount Amount of buying being bought
 * @property price Price of 1 unit of buying in terms of selling
 * @property offerId The ID of the offer (0 for new, existing ID to update/delete)
 */
data class ManageBuyOfferOperation(
    val selling: Asset,
    val buying: Asset,
    val buyAmount: String,
    val price: Price,
    val offerId: Long = 0
) : Operation() {

    init {
        require(selling != buying) {
            "Selling and buying assets must be different"
        }
        toXdrAmount(buyAmount) // Validate
        require(offerId >= 0) { "Offer ID must be non-negative, got $offerId" }
    }

    override fun toOperationBody(): OperationBodyXdr {
        val op = ManageBuyOfferOpXdr(
            selling = selling.toXdr(),
            buying = buying.toXdr(),
            buyAmount = Int64Xdr(toXdrAmount(buyAmount)),
            price = price.toXdr(),
            offerId = Int64Xdr(offerId)
        )
        return OperationBodyXdr.ManageBuyOfferOp(op)
    }

    companion object {
        fun fromXdr(op: ManageBuyOfferOpXdr): ManageBuyOfferOperation {
            val selling = Asset.fromXdr(op.selling)
            val buying = Asset.fromXdr(op.buying)
            val buyAmount = fromXdrAmount(op.buyAmount.value)
            val price = Price.fromXdr(op.price)
            val offerId = op.offerId.value

            return ManageBuyOfferOperation(
                selling = selling,
                buying = buying,
                buyAmount = buyAmount,
                price = price,
                offerId = offerId
            )
        }
    }
}

/**
 * Represents a [CreatePassiveSellOffer](https://developers.stellar.org/docs/learn/fundamentals/transactions/list-of-operations#create-passive-sell-offer) operation.
 *
 * Creates a passive sell offer that won't immediately match existing offers.
 *
 * @property selling The asset being sold in this operation
 * @property buying The asset being bought in this operation
 * @property amount Amount of selling being sold
 * @property price Price of 1 unit of selling in terms of buying
 */
data class CreatePassiveSellOfferOperation(
    val selling: Asset,
    val buying: Asset,
    val amount: String,
    val price: Price
) : Operation() {

    init {
        require(selling != buying) {
            "Selling and buying assets must be different"
        }
        toXdrAmount(amount) // Validate
    }

    override fun toOperationBody(): OperationBodyXdr {
        val op = CreatePassiveSellOfferOpXdr(
            selling = selling.toXdr(),
            buying = buying.toXdr(),
            amount = Int64Xdr(toXdrAmount(amount)),
            price = price.toXdr()
        )
        return OperationBodyXdr.CreatePassiveSellOfferOp(op)
    }

    companion object {
        fun fromXdr(op: CreatePassiveSellOfferOpXdr): CreatePassiveSellOfferOperation {
            val selling = Asset.fromXdr(op.selling)
            val buying = Asset.fromXdr(op.buying)
            val amount = fromXdrAmount(op.amount.value)
            val price = Price.fromXdr(op.price)

            return CreatePassiveSellOfferOperation(
                selling = selling,
                buying = buying,
                amount = amount,
                price = price
            )
        }
    }
}

/**
 * Represents a [ChangeTrust](https://developers.stellar.org/docs/learn/fundamentals/transactions/list-of-operations#change-trust) operation.
 *
 * @property asset The asset of the trustline
 * @property limit The limit of the trustline
 */
data class ChangeTrustOperation(
    val asset: Asset,
    val limit: String = MAX_LIMIT
) : Operation() {

    init {
        require(asset !is AssetTypeNative) {
            "ChangeTrust cannot be used with the native asset"
        }
        toXdrAmount(limit) // Validate
    }

    override fun toOperationBody(): OperationBodyXdr {
        val changeTrustAsset = when (asset) {
            is AssetTypeNative -> ChangeTrustAssetXdr.Void
            is AssetTypeCreditAlphaNum4 -> {
                val assetXdr = asset.toXdr() as AssetXdr.AlphaNum4
                ChangeTrustAssetXdr.AlphaNum4(assetXdr.value)
            }
            is AssetTypeCreditAlphaNum12 -> {
                val assetXdr = asset.toXdr() as AssetXdr.AlphaNum12
                ChangeTrustAssetXdr.AlphaNum12(assetXdr.value)
            }
        }

        val op = ChangeTrustOpXdr(
            line = changeTrustAsset,
            limit = Int64Xdr(toXdrAmount(limit))
        )
        return OperationBodyXdr.ChangeTrustOp(op)
    }

    companion object {
        const val MAX_LIMIT = "922337203685.4775807"

        fun fromXdr(op: ChangeTrustOpXdr): ChangeTrustOperation {
            val asset = when (val line = op.line) {
                is ChangeTrustAssetXdr.Void -> AssetTypeNative
                is ChangeTrustAssetXdr.AlphaNum4 -> AssetTypeCreditAlphaNum4.fromXdr(line.value)
                is ChangeTrustAssetXdr.AlphaNum12 -> AssetTypeCreditAlphaNum12.fromXdr(line.value)
                is ChangeTrustAssetXdr.LiquidityPool -> {
                    throw UnsupportedOperationException("Liquidity pool trustlines are not yet supported")
                }
            }

            val limit = fromXdrAmount(op.limit.value)
            return ChangeTrustOperation(asset, limit)
        }
    }
}

/**
 * Represents an [AllowTrust](https://developers.stellar.org/docs/learn/fundamentals/transactions/list-of-operations#allow-trust) operation.
 *
 * **Deprecated**: Use [SetTrustLineFlagsOperation] instead (Protocol 17+).
 *
 * @property trustor The account of the recipient of the trustline
 * @property assetCode The asset code of the trustline
 * @property authorize Authorization level (0=unauthorized, 1=authorized, 2=authorized_to_maintain_liabilities)
 */
data class AllowTrustOperation(
    val trustor: String,
    val assetCode: String,
    val authorize: Int
) : Operation() {

    init {
        require(StrKey.isValidEd25519PublicKey(trustor)) {
            "Invalid trustor account: $trustor"
        }
        require(assetCode.isNotBlank()) {
            "Asset code cannot be blank"
        }
        require(assetCode.length <= 12) {
            "Asset code cannot exceed 12 characters"
        }
        require(authorize in 0..2) {
            "Authorize must be 0 (unauthorized), 1 (authorized), or 2 (authorized_to_maintain_liabilities), got $authorize"
        }
    }

    override fun toOperationBody(): OperationBodyXdr {
        val assetCodeXdr = if (assetCode.length <= 4) {
            AssetCodeXdr.AssetCode4(
                AssetCode4Xdr(Util.paddedByteArray(assetCode, 4))
            )
        } else {
            AssetCodeXdr.AssetCode12(
                AssetCode12Xdr(Util.paddedByteArray(assetCode, 12))
            )
        }

        val op = AllowTrustOpXdr(
            trustor = KeyPair.fromAccountId(trustor).getXdrAccountId(),
            asset = assetCodeXdr,
            authorize = Uint32Xdr(authorize.toUInt())
        )
        return OperationBodyXdr.AllowTrustOp(op)
    }

    companion object {
        fun fromXdr(op: AllowTrustOpXdr): AllowTrustOperation {
            val trustor = when (val accountId = op.trustor.value) {
                is PublicKeyXdr.Ed25519 -> StrKey.encodeEd25519PublicKey(accountId.value.value)
            }
            val assetCode = when (val asset = op.asset) {
                is AssetCodeXdr.AssetCode4 -> Util.paddedByteArrayToString(asset.value.value)
                is AssetCodeXdr.AssetCode12 -> Util.paddedByteArrayToString(asset.value.value)
            }
            val authorize = op.authorize.value.toInt()

            return AllowTrustOperation(trustor, assetCode, authorize)
        }
    }
}

/**
 * Represents an [Inflation](https://developers.stellar.org/docs/learn/fundamentals/transactions/list-of-operations#inflation) operation.
 *
 * **Deprecated**: This operation is no longer functional on the network (disabled in Protocol 12).
 * It is kept for historical transaction parsing.
 */
class InflationOperation : Operation() {

    override fun toOperationBody(): OperationBodyXdr {
        return OperationBodyXdr.Void(OperationTypeXdr.INFLATION)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false
        return true
    }

    override fun hashCode(): Int = this::class.hashCode()
}

/**
 * Represents a [SetOptions](https://developers.stellar.org/docs/learn/fundamentals/transactions/list-of-operations#set-options) operation.
 *
 * @property inflationDestination Account of the inflation destination
 * @property clearFlags Indicates which flags to clear
 * @property setFlags Indicates which flags to set
 * @property masterKeyWeight Weight of the master key
 * @property lowThreshold Low threshold for operations
 * @property mediumThreshold Medium threshold for operations
 * @property highThreshold High threshold for operations
 * @property homeDomain The home domain of the account
 * @property signer Additional signer to add/remove
 * @property signerWeight Weight of the signer (0 to remove)
 */
data class SetOptionsOperation(
    val inflationDestination: String? = null,
    val clearFlags: Int? = null,
    val setFlags: Int? = null,
    val masterKeyWeight: Int? = null,
    val lowThreshold: Int? = null,
    val mediumThreshold: Int? = null,
    val highThreshold: Int? = null,
    val homeDomain: String? = null,
    val signer: SignerKey? = null,
    val signerWeight: Int? = null
) : Operation() {

    init {
        masterKeyWeight?.let {
            require(it in 0..255) { "Master key weight must be between 0 and 255, got $it" }
        }
        lowThreshold?.let {
            require(it in 0..255) { "Low threshold must be between 0 and 255, got $it" }
        }
        mediumThreshold?.let {
            require(it in 0..255) { "Medium threshold must be between 0 and 255, got $it" }
        }
        highThreshold?.let {
            require(it in 0..255) { "High threshold must be between 0 and 255, got $it" }
        }
        signerWeight?.let {
            require(it in 0..255) { "Signer weight must be between 0 and 255, got $it" }
        }
        clearFlags?.let {
            require(it in 0..7) { "Clear flags must be between 0 and 7, got $it" }
        }
        setFlags?.let {
            require(it in 0..7) { "Set flags must be between 0 and 7, got $it" }
        }
        homeDomain?.let {
            require(it.encodeToByteArray().size <= 32) {
                "Home domain cannot exceed 32 bytes"
            }
        }
        if (signer != null && signerWeight == null) {
            throw IllegalArgumentException("Signer weight cannot be null if signer is not null")
        }
        if (signer == null && signerWeight != null) {
            throw IllegalArgumentException("Signer cannot be null if signer weight is not null")
        }
        inflationDestination?.let {
            require(StrKey.isValidEd25519PublicKey(it)) {
                "Invalid inflation destination: $it"
            }
        }
    }

    override fun toOperationBody(): OperationBodyXdr {
        val op = SetOptionsOpXdr(
            inflationDest = inflationDestination?.let {
                KeyPair.fromAccountId(it).getXdrAccountId()
            },
            clearFlags = clearFlags?.let { Uint32Xdr(it.toUInt()) },
            setFlags = setFlags?.let { Uint32Xdr(it.toUInt()) },
            masterWeight = masterKeyWeight?.let { Uint32Xdr(it.toUInt()) },
            lowThreshold = lowThreshold?.let { Uint32Xdr(it.toUInt()) },
            medThreshold = mediumThreshold?.let { Uint32Xdr(it.toUInt()) },
            highThreshold = highThreshold?.let { Uint32Xdr(it.toUInt()) },
            homeDomain = homeDomain?.let { String32Xdr(it) },
            signer = if (signer != null && signerWeight != null) {
                SignerXdr(
                    key = signer.toXdr(),
                    weight = Uint32Xdr(signerWeight.toUInt())
                )
            } else {
                null
            }
        )
        return OperationBodyXdr.SetOptionsOp(op)
    }

    companion object {
        fun fromXdr(op: SetOptionsOpXdr): SetOptionsOperation {
            val inflationDest = op.inflationDest?.let {
                when (val publicKey = it.value) {
                    is PublicKeyXdr.Ed25519 -> StrKey.encodeEd25519PublicKey(publicKey.value.value)
                }
            }

            val clearFlags = op.clearFlags?.value?.toInt()
            val setFlags = op.setFlags?.value?.toInt()
            val masterWeight = op.masterWeight?.value?.toInt()
            val lowThreshold = op.lowThreshold?.value?.toInt()
            val medThreshold = op.medThreshold?.value?.toInt()
            val highThreshold = op.highThreshold?.value?.toInt()
            val homeDomain = op.homeDomain?.value

            val signer = op.signer?.let { SignerKey.fromXdr(it.key) }
            val signerWeight = op.signer?.weight?.value?.toInt()

            return SetOptionsOperation(
                inflationDestination = inflationDest,
                clearFlags = clearFlags,
                setFlags = setFlags,
                masterKeyWeight = masterWeight,
                lowThreshold = lowThreshold,
                mediumThreshold = medThreshold,
                highThreshold = highThreshold,
                homeDomain = homeDomain,
                signer = signer,
                signerWeight = signerWeight
            )
        }
    }
}

/**
 * Represents a [ManageData](https://developers.stellar.org/docs/learn/fundamentals/transactions/list-of-operations#manage-data) operation.
 *
 * @property name The name of the data entry
 * @property value The value of the data entry, or null to delete
 */
data class ManageDataOperation(
    val name: String,
    val value: ByteArray? = null
) : Operation() {

    init {
        require(name.isNotBlank()) { "Data entry name cannot be blank" }
        require(name.encodeToByteArray().size <= 64) {
            "Data entry name cannot exceed 64 bytes"
        }
        value?.let {
            require(it.size <= 64) {
                "Data entry value cannot exceed 64 bytes, got ${it.size}"
            }
        }
    }

    override fun toOperationBody(): OperationBodyXdr {
        val op = ManageDataOpXdr(
            dataName = String64Xdr(name),
            dataValue = value?.let { DataValueXdr(it) }
        )
        return OperationBodyXdr.ManageDataOp(op)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as ManageDataOperation

        if (name != other.name) return false
        if (value != null) {
            if (other.value == null) return false
            if (!value.contentEquals(other.value)) return false
        } else if (other.value != null) {
            return false
        }

        return true
    }

    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + (value?.contentHashCode() ?: 0)
        return result
    }

    companion object {
        fun forString(name: String, value: String?): ManageDataOperation {
            return ManageDataOperation(name, value?.encodeToByteArray())
        }

        fun fromXdr(op: ManageDataOpXdr): ManageDataOperation {
            val name = op.dataName.value
            val value = op.dataValue?.value
            return ManageDataOperation(name, value)
        }
    }
}

/**
 * Represents a [BumpSequence](https://developers.stellar.org/docs/learn/fundamentals/transactions/list-of-operations#bump-sequence) operation.
 *
 * @property bumpTo Desired value for the operation's source account sequence number
 */
data class BumpSequenceOperation(
    val bumpTo: Long
) : Operation() {

    init {
        require(bumpTo >= 0) { "Bump to value must be non-negative, got $bumpTo" }
    }

    override fun toOperationBody(): OperationBodyXdr {
        val op = BumpSequenceOpXdr(
            bumpTo = SequenceNumberXdr(Int64Xdr(bumpTo))
        )
        return OperationBodyXdr.BumpSequenceOp(op)
    }

    companion object {
        fun fromXdr(op: BumpSequenceOpXdr): BumpSequenceOperation {
            val bumpTo = op.bumpTo.value.value
            return BumpSequenceOperation(bumpTo)
        }
    }
}

/**
 * Represents an [AccountMerge](https://developers.stellar.org/docs/learn/fundamentals/transactions/list-of-operations#account-merge) operation.
 *
 * @property destination Account that receives the remaining XLM balance
 */
data class AccountMergeOperation(
    val destination: String
) : Operation() {

    init {
        require(
            StrKey.isValidEd25519PublicKey(destination) ||
            StrKey.isValidMed25519PublicKey(destination)
        ) {
            "Invalid destination account: $destination"
        }
    }

    override fun toOperationBody(): OperationBodyXdr {
        return OperationBodyXdr.Destination(MuxedAccount(destination).toXdr())
    }

    companion object {
        fun fromXdr(destination: MuxedAccountXdr): AccountMergeOperation {
            val destAddress = MuxedAccount.fromXdr(destination).address
            return AccountMergeOperation(destAddress)
        }
    }
}

/**
 * Represents a [CreateClaimableBalance](https://developers.stellar.org/docs/learn/fundamentals/transactions/list-of-operations#create-claimable-balance) operation.
 *
 * @property asset The asset for the claimable balance
 * @property amount The amount of the asset
 * @property claimants The list of claimants who can claim this balance
 */
data class CreateClaimableBalanceOperation(
    val asset: Asset,
    val amount: String,
    val claimants: List<Claimant>
) : Operation() {

    init {
        toXdrAmount(amount) // Validate
        require(claimants.isNotEmpty()) {
            "Claimants list cannot be empty"
        }
    }

    override fun toOperationBody(): OperationBodyXdr {
        val xdrClaimants = claimants.map { claimant ->
            val v0 = ClaimantV0Xdr(
                destination = KeyPair.fromAccountId(claimant.destination).getXdrAccountId(),
                predicate = claimant.predicate.toXdr()
            )
            ClaimantXdr.V0(v0)
        }

        val op = CreateClaimableBalanceOpXdr(
            asset = asset.toXdr(),
            amount = Int64Xdr(toXdrAmount(amount)),
            claimants = xdrClaimants
        )
        return OperationBodyXdr.CreateClaimableBalanceOp(op)
    }

    companion object {
        fun fromXdr(op: CreateClaimableBalanceOpXdr): CreateClaimableBalanceOperation {
            val asset = Asset.fromXdr(op.asset)
            val amount = fromXdrAmount(op.amount.value)
            val claimants = op.claimants.map { xdrClaimant ->
                when (xdrClaimant) {
                    is ClaimantXdr.V0 -> {
                        val destination = when (val accountId = xdrClaimant.value.destination.value) {
                            is PublicKeyXdr.Ed25519 -> StrKey.encodeEd25519PublicKey(accountId.value.value)
                        }
                        Claimant(destination, ClaimPredicate.fromXdr(xdrClaimant.value.predicate))
                    }
                }
            }

            return CreateClaimableBalanceOperation(asset, amount, claimants)
        }
    }
}

/**
 * Represents a [ClaimClaimableBalance](https://developers.stellar.org/docs/learn/fundamentals/transactions/list-of-operations#claim-claimable-balance) operation.
 *
 * @property balanceId The hex-encoded claimable balance ID to claim
 */
data class ClaimClaimableBalanceOperation(
    val balanceId: String
) : Operation() {

    init {
        require(balanceId.length == 72) {
            "Invalid balance ID length: expected 72 characters, got ${balanceId.length}"
        }
        // Validate hex string
        try {
            Util.hexToBytes(balanceId)
        } catch (e: Exception) {
            throw IllegalArgumentException("Balance ID must be valid hex string", e)
        }
    }

    override fun toOperationBody(): OperationBodyXdr {
        val balanceIdBytes = Util.hexToBytes(balanceId)
        // Deserialize the full XDR bytes to get the ClaimableBalanceID
        val reader = XdrReader(balanceIdBytes)
        val claimableBalanceId = ClaimableBalanceIDXdr.decode(reader)

        val op = ClaimClaimableBalanceOpXdr(
            balanceId = claimableBalanceId
        )
        return OperationBodyXdr.ClaimClaimableBalanceOp(op)
    }

    companion object {
        fun fromXdr(op: ClaimClaimableBalanceOpXdr): ClaimClaimableBalanceOperation {
            // Serialize the entire ClaimableBalanceID XDR to bytes
            val writer = XdrWriter()
            op.balanceId.encode(writer)
            val balanceIdBytes = writer.toByteArray()
            val balanceId = Util.bytesToHex(balanceIdBytes).lowercase()
            return ClaimClaimableBalanceOperation(balanceId)
        }
    }
}

/**
 * Represents a [ClawbackClaimableBalance](https://developers.stellar.org/docs/learn/fundamentals/transactions/list-of-operations#clawback-claimable-balance) operation.
 *
 * @property balanceId The hex-encoded claimable balance ID to claw back
 */
data class ClawbackClaimableBalanceOperation(
    val balanceId: String
) : Operation() {

    init {
        require(balanceId.length == 72) {
            "Invalid balance ID length: expected 72 characters, got ${balanceId.length}"
        }
        // Validate hex string
        try {
            Util.hexToBytes(balanceId)
        } catch (e: Exception) {
            throw IllegalArgumentException("Balance ID must be valid hex string", e)
        }
    }

    override fun toOperationBody(): OperationBodyXdr {
        val balanceIdBytes = Util.hexToBytes(balanceId)
        // Deserialize the full XDR bytes to get the ClaimableBalanceID
        val reader = XdrReader(balanceIdBytes)
        val claimableBalanceId = ClaimableBalanceIDXdr.decode(reader)

        val op = ClawbackClaimableBalanceOpXdr(
            balanceId = claimableBalanceId
        )
        return OperationBodyXdr.ClawbackClaimableBalanceOp(op)
    }

    companion object {
        fun fromXdr(op: ClawbackClaimableBalanceOpXdr): ClawbackClaimableBalanceOperation {
            // Serialize the entire ClaimableBalanceID XDR to bytes
            val writer = XdrWriter()
            op.balanceId.encode(writer)
            val balanceIdBytes = writer.toByteArray()
            val balanceId = Util.bytesToHex(balanceIdBytes).lowercase()
            return ClawbackClaimableBalanceOperation(balanceId)
        }
    }
}

/**
 * Represents a [Clawback](https://developers.stellar.org/docs/learn/fundamentals/transactions/list-of-operations#clawback) operation.
 *
 * @property from The account holding the asset to claw back
 * @property asset The asset to claw back (must not be native)
 * @property amount The amount to claw back
 */
data class ClawbackOperation(
    val from: String,
    val asset: Asset,
    val amount: String
) : Operation() {

    init {
        require(
            StrKey.isValidEd25519PublicKey(from) ||
            StrKey.isValidMed25519PublicKey(from)
        ) {
            "Invalid from account: $from"
        }
        require(asset !is AssetTypeNative) {
            "Clawback cannot be used with the native asset"
        }
        toXdrAmount(amount) // Validate
    }

    override fun toOperationBody(): OperationBodyXdr {
        val op = ClawbackOpXdr(
            asset = asset.toXdr(),
            from = MuxedAccount(from).toXdr(),
            amount = Int64Xdr(toXdrAmount(amount))
        )
        return OperationBodyXdr.ClawbackOp(op)
    }

    companion object {
        fun fromXdr(op: ClawbackOpXdr): ClawbackOperation {
            val asset = Asset.fromXdr(op.asset)
            val from = MuxedAccount.fromXdr(op.from).address
            val amount = fromXdrAmount(op.amount.value)

            return ClawbackOperation(from, asset, amount)
        }
    }
}

/**
 * Represents a [BeginSponsoringFutureReserves](https://developers.stellar.org/docs/learn/fundamentals/transactions/list-of-operations#begin-sponsoring-future-reserves) operation.
 *
 * @property sponsoredId The account ID being sponsored
 */
data class BeginSponsoringFutureReservesOperation(
    val sponsoredId: String
) : Operation() {

    init {
        require(StrKey.isValidEd25519PublicKey(sponsoredId)) {
            "Invalid sponsored account ID: $sponsoredId"
        }
    }

    override fun toOperationBody(): OperationBodyXdr {
        val op = BeginSponsoringFutureReservesOpXdr(
            sponsoredId = KeyPair.fromAccountId(sponsoredId).getXdrAccountId()
        )
        return OperationBodyXdr.BeginSponsoringFutureReservesOp(op)
    }

    companion object {
        fun fromXdr(op: BeginSponsoringFutureReservesOpXdr): BeginSponsoringFutureReservesOperation {
            val sponsoredId = when (val accountId = op.sponsoredId.value) {
                is PublicKeyXdr.Ed25519 -> StrKey.encodeEd25519PublicKey(accountId.value.value)
            }
            return BeginSponsoringFutureReservesOperation(sponsoredId)
        }
    }
}

/**
 * Represents an [EndSponsoringFutureReserves](https://developers.stellar.org/docs/learn/fundamentals/transactions/list-of-operations#end-sponsoring-future-reserves) operation.
 *
 * This operation ends the sponsorship block started by BeginSponsoringFutureReserves.
 */
class EndSponsoringFutureReservesOperation : Operation() {

    override fun toOperationBody(): OperationBodyXdr {
        return OperationBodyXdr.Void(OperationTypeXdr.END_SPONSORING_FUTURE_RESERVES)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false
        return true
    }

    override fun hashCode(): Int = this::class.hashCode()
}

/**
 * Represents a [RevokeSponsorship](https://developers.stellar.org/docs/learn/fundamentals/transactions/list-of-operations#revoke-sponsorship) operation.
 *
 * This operation is a union type that can revoke sponsorship of various ledger entries.
 *
 * @property sponsorship The type of sponsorship to revoke
 */
data class RevokeSponsorshipOperation(
    val sponsorship: Sponsorship
) : Operation() {

    override fun toOperationBody(): OperationBodyXdr {
        val revokeSponsorshipOp = when (sponsorship) {
            is Sponsorship.Account -> {
                RevokeSponsorshipOpXdr.LedgerKey(
                    LedgerKeyXdr.Account(
                        LedgerKeyAccountXdr(
                            accountId = KeyPair.fromAccountId(sponsorship.accountId).getXdrAccountId()
                        )
                    )
                )
            }
            is Sponsorship.TrustLine -> {
                val trustLineAsset = when (val asset = sponsorship.asset) {
                    is AssetTypeNative -> TrustLineAssetXdr.Void
                    is AssetTypeCreditAlphaNum4 -> {
                        val assetXdr = asset.toXdr() as AssetXdr.AlphaNum4
                        TrustLineAssetXdr.AlphaNum4(assetXdr.value)
                    }
                    is AssetTypeCreditAlphaNum12 -> {
                        val assetXdr = asset.toXdr() as AssetXdr.AlphaNum12
                        TrustLineAssetXdr.AlphaNum12(assetXdr.value)
                    }
                }
                RevokeSponsorshipOpXdr.LedgerKey(
                    LedgerKeyXdr.TrustLine(
                        LedgerKeyTrustLineXdr(
                            accountId = KeyPair.fromAccountId(sponsorship.accountId).getXdrAccountId(),
                            asset = trustLineAsset
                        )
                    )
                )
            }
            is Sponsorship.Offer -> {
                RevokeSponsorshipOpXdr.LedgerKey(
                    LedgerKeyXdr.Offer(
                        LedgerKeyOfferXdr(
                            sellerId = KeyPair.fromAccountId(sponsorship.sellerId).getXdrAccountId(),
                            offerId = Int64Xdr(sponsorship.offerId)
                        )
                    )
                )
            }
            is Sponsorship.Data -> {
                RevokeSponsorshipOpXdr.LedgerKey(
                    LedgerKeyXdr.Data(
                        LedgerKeyDataXdr(
                            accountId = KeyPair.fromAccountId(sponsorship.accountId).getXdrAccountId(),
                            dataName = String64Xdr(sponsorship.dataName)
                        )
                    )
                )
            }
            is Sponsorship.ClaimableBalance -> {
                val balanceIdBytes = Util.hexToBytes(sponsorship.balanceId)
                val claimableBalanceId = ClaimableBalanceIDXdr.V0(HashXdr(balanceIdBytes))
                RevokeSponsorshipOpXdr.LedgerKey(
                    LedgerKeyXdr.ClaimableBalance(
                        LedgerKeyClaimableBalanceXdr(balanceId = claimableBalanceId)
                    )
                )
            }
            is Sponsorship.Signer -> {
                RevokeSponsorshipOpXdr.Signer(
                    RevokeSponsorshipOpSignerXdr(
                        accountId = KeyPair.fromAccountId(sponsorship.accountId).getXdrAccountId(),
                        signerKey = sponsorship.signerKey.toXdr()
                    )
                )
            }
        }

        return OperationBodyXdr.RevokeSponsorshipOp(revokeSponsorshipOp)
    }

    companion object {
        fun fromXdr(op: RevokeSponsorshipOpXdr): RevokeSponsorshipOperation {
            val sponsorship = when (op) {
                is RevokeSponsorshipOpXdr.LedgerKey -> {
                    when (val ledgerKey = op.value) {
                        is LedgerKeyXdr.Account -> {
                            val accountId = when (val pk = ledgerKey.value.accountId.value) {
                                is PublicKeyXdr.Ed25519 -> StrKey.encodeEd25519PublicKey(pk.value.value)
                            }
                            Sponsorship.Account(accountId)
                        }
                        is LedgerKeyXdr.TrustLine -> {
                            val accountId = when (val pk = ledgerKey.value.accountId.value) {
                                is PublicKeyXdr.Ed25519 -> StrKey.encodeEd25519PublicKey(pk.value.value)
                            }
                            val asset = when (val tla = ledgerKey.value.asset) {
                                is TrustLineAssetXdr.Void -> AssetTypeNative
                                is TrustLineAssetXdr.AlphaNum4 -> AssetTypeCreditAlphaNum4.fromXdr(tla.value)
                                is TrustLineAssetXdr.AlphaNum12 -> AssetTypeCreditAlphaNum12.fromXdr(tla.value)
                                is TrustLineAssetXdr.LiquidityPoolID -> throw UnsupportedOperationException("Pool share trustlines not supported")
                            }
                            Sponsorship.TrustLine(accountId, asset)
                        }
                        is LedgerKeyXdr.Offer -> {
                            val sellerId = when (val pk = ledgerKey.value.sellerId.value) {
                                is PublicKeyXdr.Ed25519 -> StrKey.encodeEd25519PublicKey(pk.value.value)
                            }
                            Sponsorship.Offer(sellerId, ledgerKey.value.offerId.value)
                        }
                        is LedgerKeyXdr.Data -> {
                            val accountId = when (val pk = ledgerKey.value.accountId.value) {
                                is PublicKeyXdr.Ed25519 -> StrKey.encodeEd25519PublicKey(pk.value.value)
                            }
                            Sponsorship.Data(accountId, ledgerKey.value.dataName.value)
                        }
                        is LedgerKeyXdr.ClaimableBalance -> {
                            val balanceId = when (val id = ledgerKey.value.balanceId) {
                                is ClaimableBalanceIDXdr.V0 -> Util.bytesToHex(id.value.value)
                            }
                            Sponsorship.ClaimableBalance(balanceId.lowercase())
                        }
                        else -> throw IllegalArgumentException("Unknown ledger key type for revoke sponsorship")
                    }
                }
                is RevokeSponsorshipOpXdr.Signer -> {
                    val accountId = when (val pk = op.value.accountId.value) {
                        is PublicKeyXdr.Ed25519 -> StrKey.encodeEd25519PublicKey(pk.value.value)
                    }
                    val signerKey = SignerKey.fromXdr(op.value.signerKey)
                    Sponsorship.Signer(accountId, signerKey)
                }
            }

            return RevokeSponsorshipOperation(sponsorship)
        }
    }
}

/**
 * Sealed class representing the different types of sponsorships that can be revoked.
 */
sealed class Sponsorship {
    /**
     * Revoke sponsorship of an account.
     * @property accountId The account whose sponsorship to revoke
     */
    data class Account(val accountId: String) : Sponsorship()

    /**
     * Revoke sponsorship of a trustline.
     * @property accountId The account ID
     * @property asset The asset of the trustline
     */
    data class TrustLine(val accountId: String, val asset: Asset) : Sponsorship()

    /**
     * Revoke sponsorship of an offer.
     * @property sellerId The seller's account ID
     * @property offerId The offer ID
     */
    data class Offer(val sellerId: String, val offerId: Long) : Sponsorship()

    /**
     * Revoke sponsorship of a data entry.
     * @property accountId The account ID
     * @property dataName The data entry name
     */
    data class Data(val accountId: String, val dataName: String) : Sponsorship()

    /**
     * Revoke sponsorship of a claimable balance.
     * @property balanceId The hex-encoded claimable balance ID
     */
    data class ClaimableBalance(val balanceId: String) : Sponsorship()

    /**
     * Revoke sponsorship of a signer.
     * @property accountId The account ID
     * @property signerKey The signer key
     */
    data class Signer(val accountId: String, val signerKey: SignerKey) : Sponsorship()
}

/**
 * Represents a [SetTrustLineFlags](https://developers.stellar.org/docs/learn/fundamentals/transactions/list-of-operations#set-trustline-flags) operation.
 *
 * @property trustor The account owning the trustline
 * @property asset The asset of the trustline
 * @property clearFlags Flags to clear (null means no flags to clear)
 * @property setFlags Flags to set (null means no flags to set)
 */
data class SetTrustLineFlagsOperation(
    val trustor: String,
    val asset: Asset,
    val clearFlags: Int? = null,
    val setFlags: Int? = null
) : Operation() {

    init {
        require(StrKey.isValidEd25519PublicKey(trustor)) {
            "Invalid trustor account: $trustor"
        }
        require(asset !is AssetTypeNative) {
            "SetTrustLineFlags cannot be used with the native asset"
        }
    }

    override fun toOperationBody(): OperationBodyXdr {
        val op = SetTrustLineFlagsOpXdr(
            trustor = KeyPair.fromAccountId(trustor).getXdrAccountId(),
            asset = asset.toXdr(),
            clearFlags = Uint32Xdr((clearFlags ?: 0).toUInt()),
            setFlags = Uint32Xdr((setFlags ?: 0).toUInt())
        )
        return OperationBodyXdr.SetTrustLineFlagsOp(op)
    }

    companion object {
        // Trustline flags constants
        const val AUTHORIZED_FLAG = 1
        const val AUTHORIZED_TO_MAINTAIN_LIABILITIES_FLAG = 2
        const val TRUSTLINE_CLAWBACK_ENABLED_FLAG = 4

        fun fromXdr(op: SetTrustLineFlagsOpXdr): SetTrustLineFlagsOperation {
            val trustor = when (val accountId = op.trustor.value) {
                is PublicKeyXdr.Ed25519 -> StrKey.encodeEd25519PublicKey(accountId.value.value)
            }
            val asset = Asset.fromXdr(op.asset)
            val clearFlags = op.clearFlags.value.toInt().takeIf { it != 0 }
            val setFlags = op.setFlags.value.toInt().takeIf { it != 0 }

            return SetTrustLineFlagsOperation(trustor, asset, clearFlags, setFlags)
        }
    }
}

/**
 * Represents a [LiquidityPoolDeposit](https://developers.stellar.org/docs/learn/fundamentals/transactions/list-of-operations#liquidity-pool-deposit) operation.
 *
 * @property liquidityPoolId The liquidity pool ID (hex-encoded, 64 characters)
 * @property maxAmountA Maximum amount of first asset to deposit
 * @property maxAmountB Maximum amount of second asset to deposit
 * @property minPrice Minimum deposita/depositb price
 * @property maxPrice Maximum deposita/depositb price
 */
data class LiquidityPoolDepositOperation(
    val liquidityPoolId: String,
    val maxAmountA: String,
    val maxAmountB: String,
    val minPrice: Price,
    val maxPrice: Price
) : Operation() {

    init {
        require(liquidityPoolId.length == 64) {
            "Invalid liquidity pool ID length: expected 64 characters, got ${liquidityPoolId.length}"
        }
        toXdrAmount(maxAmountA) // Validate
        toXdrAmount(maxAmountB) // Validate
    }

    override fun toOperationBody(): OperationBodyXdr {
        val poolIdBytes = Util.hexToBytes(liquidityPoolId)
        val op = LiquidityPoolDepositOpXdr(
            liquidityPoolId = PoolIDXdr(HashXdr(poolIdBytes)),
            maxAmountA = Int64Xdr(toXdrAmount(maxAmountA)),
            maxAmountB = Int64Xdr(toXdrAmount(maxAmountB)),
            minPrice = minPrice.toXdr(),
            maxPrice = maxPrice.toXdr()
        )
        return OperationBodyXdr.LiquidityPoolDepositOp(op)
    }

    companion object {
        fun fromXdr(op: LiquidityPoolDepositOpXdr): LiquidityPoolDepositOperation {
            val liquidityPoolId = Util.bytesToHex(op.liquidityPoolId.value.value).lowercase()
            val maxAmountA = fromXdrAmount(op.maxAmountA.value)
            val maxAmountB = fromXdrAmount(op.maxAmountB.value)
            val minPrice = Price.fromXdr(op.minPrice)
            val maxPrice = Price.fromXdr(op.maxPrice)

            return LiquidityPoolDepositOperation(
                liquidityPoolId, maxAmountA, maxAmountB, minPrice, maxPrice
            )
        }
    }
}

/**
 * Represents a [LiquidityPoolWithdraw](https://developers.stellar.org/docs/learn/fundamentals/transactions/list-of-operations#liquidity-pool-withdraw) operation.
 *
 * @property liquidityPoolId The liquidity pool ID (hex-encoded, 64 characters)
 * @property amount Amount of pool shares to withdraw
 * @property minAmountA Minimum amount of first asset to withdraw
 * @property minAmountB Minimum amount of second asset to withdraw
 */
data class LiquidityPoolWithdrawOperation(
    val liquidityPoolId: String,
    val amount: String,
    val minAmountA: String,
    val minAmountB: String
) : Operation() {

    init {
        require(liquidityPoolId.length == 64) {
            "Invalid liquidity pool ID length: expected 64 characters, got ${liquidityPoolId.length}"
        }
        toXdrAmount(amount) // Validate
        toXdrAmount(minAmountA) // Validate
        toXdrAmount(minAmountB) // Validate
    }

    override fun toOperationBody(): OperationBodyXdr {
        val poolIdBytes = Util.hexToBytes(liquidityPoolId)
        val op = LiquidityPoolWithdrawOpXdr(
            liquidityPoolId = PoolIDXdr(HashXdr(poolIdBytes)),
            amount = Int64Xdr(toXdrAmount(amount)),
            minAmountA = Int64Xdr(toXdrAmount(minAmountA)),
            minAmountB = Int64Xdr(toXdrAmount(minAmountB))
        )
        return OperationBodyXdr.LiquidityPoolWithdrawOp(op)
    }

    companion object {
        fun fromXdr(op: LiquidityPoolWithdrawOpXdr): LiquidityPoolWithdrawOperation {
            val liquidityPoolId = Util.bytesToHex(op.liquidityPoolId.value.value).lowercase()
            val amount = fromXdrAmount(op.amount.value)
            val minAmountA = fromXdrAmount(op.minAmountA.value)
            val minAmountB = fromXdrAmount(op.minAmountB.value)

            return LiquidityPoolWithdrawOperation(
                liquidityPoolId, amount, minAmountA, minAmountB
            )
        }
    }
}

/**
 * Represents an [InvokeHostFunction](https://developers.stellar.org/docs/learn/fundamentals/transactions/list-of-operations#invoke-host-function) operation.
 *
 * This operation is used for Soroban smart contract invocations.
 *
 * @property hostFunction The host function to invoke
 * @property auth The authorization entries required to execute the function
 */
data class InvokeHostFunctionOperation(
    val hostFunction: HostFunctionXdr,
    val auth: List<SorobanAuthorizationEntryXdr> = emptyList()
) : Operation() {

    override fun toOperationBody(): OperationBodyXdr {
        val op = InvokeHostFunctionOpXdr(
            hostFunction = hostFunction,
            auth = auth
        )
        return OperationBodyXdr.InvokeHostFunctionOp(op)
    }

    companion object {
        fun fromXdr(op: InvokeHostFunctionOpXdr): InvokeHostFunctionOperation {
            return InvokeHostFunctionOperation(
                hostFunction = op.hostFunction,
                auth = op.auth
            )
        }

        /**
         * Creates an InvokeHostFunctionOperation for invoking a specific function on a contract.
         *
         * This is a convenience factory method that builds the necessary XDR structures
         * for contract invocation.
         *
         * @param contractAddress The contract address (C... format)
         * @param functionName The name of the contract function to invoke
         * @param parameters The function parameters as SCVal XDR values
         * @return An InvokeHostFunctionOperation ready to be added to a transaction
         *
         * ## Example
         * ```kotlin
         * val operation = InvokeHostFunctionOperation.invokeContractFunction(
         *     contractAddress = "CABC123...",
         *     functionName = "transfer",
         *     parameters = listOf(
         *         Scv.toAddress(fromAccount),
         *         Scv.toAddress(toAccount),
         *         Scv.toInt128(amount)
         *     )
         * )
         * ```
         */
        fun invokeContractFunction(
            contractAddress: String,
            functionName: String,
            parameters: List<SCValXdr>
        ): InvokeHostFunctionOperation {
            val address = Address(contractAddress)
            val invokeArgs = InvokeContractArgsXdr(
                contractAddress = address.toSCAddress(),
                functionName = SCSymbolXdr(functionName),
                args = parameters
            )
            val hostFunction = HostFunctionXdr.InvokeContract(invokeArgs)
            return InvokeHostFunctionOperation(
                hostFunction = hostFunction,
                auth = emptyList()
            )
        }
    }
}

/**
 * Represents an [ExtendFootprintTTL](https://developers.stellar.org/docs/learn/fundamentals/transactions/list-of-operations#extend-footprint-ttl) operation.
 *
 * This operation extends the expiration of Soroban contract storage.
 *
 * @property extendTo Number of ledgers to extend the TTL
 */
data class ExtendFootprintTTLOperation(
    val extendTo: Int
) : Operation() {

    init {
        require(extendTo > 0) {
            "extendTo must be positive, got $extendTo"
        }
    }

    override fun toOperationBody(): OperationBodyXdr {
        val op = ExtendFootprintTTLOpXdr(
            ext = ExtensionPointXdr.Void,
            extendTo = Uint32Xdr(extendTo.toUInt())
        )
        return OperationBodyXdr.ExtendFootprintTTLOp(op)
    }

    companion object {
        fun fromXdr(op: ExtendFootprintTTLOpXdr): ExtendFootprintTTLOperation {
            return ExtendFootprintTTLOperation(op.extendTo.value.toInt())
        }
    }
}

/**
 * Represents a [RestoreFootprint](https://developers.stellar.org/docs/learn/fundamentals/transactions/list-of-operations#restore-footprint) operation.
 *
 * This operation restores archived Soroban contract data.
 */
class RestoreFootprintOperation : Operation() {

    override fun toOperationBody(): OperationBodyXdr {
        val op = RestoreFootprintOpXdr(
            ext = ExtensionPointXdr.Void
        )
        return OperationBodyXdr.RestoreFootprintOp(op)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false
        return true
    }

    override fun hashCode(): Int = this::class.hashCode()

    companion object {
        fun fromXdr(op: RestoreFootprintOpXdr): RestoreFootprintOperation {
            return RestoreFootprintOperation()
        }
    }
}

/**
 * Represents a claimant for a claimable balance.
 *
 * @property destination The account ID that can claim the balance
 * @property predicate The predicate that must be satisfied to claim
 */
data class Claimant(
    val destination: String,
    val predicate: ClaimPredicate
) {
    init {
        require(StrKey.isValidEd25519PublicKey(destination)) {
            "Invalid destination account: $destination"
        }
    }
}

/**
 * Sealed class representing claim predicates for claimable balances.
 */
sealed class ClaimPredicate {
    /**
     * Converts this predicate to its XDR representation.
     */
    abstract fun toXdr(): ClaimPredicateXdr

    /**
     * A predicate that is always true (unconditional).
     */
    object Unconditional : ClaimPredicate() {
        override fun toXdr(): ClaimPredicateXdr = ClaimPredicateXdr.Void
    }

    /**
     * A predicate that is satisfied if the time is before the specified absolute time.
     * @property timestamp Absolute Unix timestamp in seconds
     */
    data class BeforeAbsoluteTime(val timestamp: Long) : ClaimPredicate() {
        override fun toXdr(): ClaimPredicateXdr {
            return ClaimPredicateXdr.AbsBefore(Int64Xdr(timestamp))
        }
    }

    /**
     * A predicate that is satisfied if the time is before the specified relative time.
     * @property seconds Relative time in seconds from the close time of the ledger in which the claimable balance was created
     */
    data class BeforeRelativeTime(val seconds: Long) : ClaimPredicate() {
        override fun toXdr(): ClaimPredicateXdr {
            return ClaimPredicateXdr.RelBefore(Int64Xdr(seconds))
        }
    }

    /**
     * A predicate that negates another predicate.
     * @property predicate The predicate to negate
     */
    data class Not(val predicate: ClaimPredicate) : ClaimPredicate() {
        init {
            require(predicate !is Not) {
                "Cannot nest NOT predicates"
            }
        }
        override fun toXdr(): ClaimPredicateXdr {
            return ClaimPredicateXdr.NotPredicate(predicate.toXdr())
        }
    }

    /**
     * A predicate that is satisfied if both predicates are satisfied.
     * @property left First predicate
     * @property right Second predicate
     */
    data class And(val left: ClaimPredicate, val right: ClaimPredicate) : ClaimPredicate() {
        override fun toXdr(): ClaimPredicateXdr {
            return ClaimPredicateXdr.AndPredicates(listOf(left.toXdr(), right.toXdr()))
        }
    }

    /**
     * A predicate that is satisfied if either predicate is satisfied.
     * @property left First predicate
     * @property right Second predicate
     */
    data class Or(val left: ClaimPredicate, val right: ClaimPredicate) : ClaimPredicate() {
        override fun toXdr(): ClaimPredicateXdr {
            return ClaimPredicateXdr.OrPredicates(listOf(left.toXdr(), right.toXdr()))
        }
    }

    companion object {
        /**
         * Decodes a ClaimPredicate from its XDR representation.
         */
        fun fromXdr(xdr: ClaimPredicateXdr): ClaimPredicate {
            return when (xdr) {
                is ClaimPredicateXdr.Void -> Unconditional
                is ClaimPredicateXdr.AbsBefore -> BeforeAbsoluteTime(xdr.value.value)
                is ClaimPredicateXdr.RelBefore -> BeforeRelativeTime(xdr.value.value)
                is ClaimPredicateXdr.NotPredicate -> {
                    xdr.value?.let { Not(fromXdr(it)) }
                        ?: throw IllegalArgumentException("NOT predicate cannot have null value")
                }
                is ClaimPredicateXdr.AndPredicates -> {
                    require(xdr.value.size == 2) { "AND predicate must have exactly 2 predicates" }
                    And(fromXdr(xdr.value[0]), fromXdr(xdr.value[1]))
                }
                is ClaimPredicateXdr.OrPredicates -> {
                    require(xdr.value.size == 2) { "OR predicate must have exactly 2 predicates" }
                    Or(fromXdr(xdr.value[0]), fromXdr(xdr.value[1]))
                }
            }
        }
    }
}
