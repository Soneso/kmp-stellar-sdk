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
 * This sealed class hierarchy includes all operation types supported by the Stellar protocol:
 * - [CreateAccountOperation] - Create a new account
 * - [PaymentOperation] - Send payment
 * - [PathPaymentStrictReceiveOperation] - Path payment with strict receive amount
 * - [PathPaymentStrictSendOperation] - Path payment with strict send amount
 * - [ManageSellOfferOperation] - Create or update a sell offer
 * - [ChangeTrustOperation] - Create or modify a trustline
 * - [SetOptionsOperation] - Set account options
 * - [ManageDataOperation] - Store or remove account data
 * - [BumpSequenceOperation] - Bump account sequence number
 * - [AccountMergeOperation] - Merge account into another
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
                is OperationBodyXdr.SetOptionsOp -> SetOptionsOperation.fromXdr(body.value)
                is OperationBodyXdr.ChangeTrustOp -> ChangeTrustOperation.fromXdr(body.value)
                is OperationBodyXdr.ManageDataOp -> ManageDataOperation.fromXdr(body.value)
                is OperationBodyXdr.BumpSequenceOp -> BumpSequenceOperation.fromXdr(body.value)
                is OperationBodyXdr.Destination -> AccountMergeOperation.fromXdr(body.value)
                // TODO: Implement remaining operation types
                else -> throw IllegalArgumentException("Unknown operation type: ${body::class.simpleName}")
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
