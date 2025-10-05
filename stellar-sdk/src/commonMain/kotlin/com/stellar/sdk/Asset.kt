package com.stellar.sdk

import com.stellar.sdk.xdr.*

/**
 * Base Asset class representing assets on the Stellar network.
 *
 * Assets are the units that are traded on the Stellar network. An asset consists of a type,
 * code, and issuer (except for the native asset, Lumens).
 *
 * There are three types of assets:
 * - **Native**: The native asset of the Stellar network (Lumens/XLM)
 * - **AlphaNum4**: Issued assets with codes 1-4 characters long
 * - **AlphaNum12**: Issued assets with codes 5-12 characters long
 *
 * ## Usage Examples
 *
 * ### Native Asset (Lumens)
 * ```kotlin
 * val xlm = AssetTypeNative
 * ```
 *
 * ### Create Issued Asset
 * ```kotlin
 * // Automatically detects AlphaNum4 vs AlphaNum12
 * val usd = Asset.createNonNativeAsset("USD", "GDUKMGUGDZQK6YHYA5Z6AY2G4XDSZPSZ3SW5UN3ARVMO6QSRDWP5YLEX")
 *
 * // Explicit types
 * val usdc = AssetTypeCreditAlphaNum4("USDC", "GA5ZSEJYB37JRC5AVCIA5MOP4RHTM335X2KGX3IHOJAPP5RE34K4KZVN")
 * val longName = AssetTypeCreditAlphaNum12("LONGERNAME", "GDUKMGUGDZQK6YHYA5Z6AY2G4XDSZPSZ3SW5UN3ARVMO6QSRDWP5YLEX")
 * ```
 *
 * ### Parse from String
 * ```kotlin
 * val native = Asset.create("native")
 * val usd = Asset.create("USD:GDUKMGUGDZQK6YHYA5Z6AY2G4XDSZPSZ3SW5UN3ARVMO6QSRDWP5YLEX")
 * ```
 *
 * ### XDR Conversion
 * ```kotlin
 * val asset = Asset.createNonNativeAsset("USD", issuer)
 * val xdr = asset.toXdr()
 * val restored = Asset.fromXdr(xdr)
 * ```
 *
 * ## Comparison and Sorting
 *
 * Assets implement [Comparable] and are ordered by:
 * 1. Type (native < alphanum4 < alphanum12)
 * 2. Code (alphabetically)
 * 3. Issuer (alphabetically)
 *
 * @see <a href="https://developers.stellar.org/docs/learn/fundamentals/stellar-data-structures/assets">Stellar Assets</a>
 */
sealed class Asset : Comparable<Asset> {

    /**
     * Returns the asset type.
     */
    abstract val type: AssetTypeXdr

    /**
     * Converts this asset to its XDR representation.
     *
     * @return The XDR Asset union representing this asset
     */
    abstract fun toXdr(): AssetXdr
    /**
     * Returns the contract ID for this asset on the given network.
     *
     * For native assets (XLM), returns the native contract ID.
     * For issued assets, derives the contract ID from the asset and network.
     *
     * This contract ID can be used to interact with the Stellar Asset Contract (SAC)
     * for this asset using Soroban smart contracts.
     *
     * @param network The network to get the contract ID for
     * @return The contract address (C...) for this asset's contract
     *
     * @see <a href="https://developers.stellar.org/docs/tokens/stellar-asset-contract">Stellar Asset Contract</a>
     */
    fun getContractId(network: Network): String {
        // Build the HashIDPreimage for CONTRACT_ID
        val preimage = HashIDPreimageXdr.ContractID(
            HashIDPreimageContractIDXdr(
                networkId = HashXdr(network.networkId()),
                contractIdPreimage = ContractIDPreimageXdr.FromAsset(this.toXdr())
            )
        )

        // Serialize to XDR bytes
        val writer = XdrWriter()
        preimage.encode(writer)
        val xdrBytes = writer.toByteArray()

        // Hash the preimage
        val rawContractId = Util.hash(xdrBytes)

        // Encode as contract address (C...)
        return StrKey.encodeContract(rawContractId)
    }

    /**
     * Returns a canonical string representation of this asset.
     * - Native asset: "native"
     * - Issued assets: "CODE:ISSUER"
     */
    abstract override fun toString(): String

    abstract override fun equals(other: Any?): Boolean
    abstract override fun hashCode(): Int

    companion object {
        /**
         * Parses an asset string and returns the equivalent Asset instance.
         *
         * The asset string is expected to be either:
         * - "native" for the native asset (Lumens)
         * - "CODE:ISSUER" for issued assets (AlphaNum4 or AlphaNum12)
         *
         * @param canonicalForm Canonical string representation of an asset
         * @return Asset instance (AssetTypeNative, AssetTypeCreditAlphaNum4, or AssetTypeCreditAlphaNum12)
         * @throws IllegalArgumentException if the asset string is invalid
         *
         * @see createNonNativeAsset
         */
        fun create(canonicalForm: String): Asset {
            require(canonicalForm.isNotBlank()) { "Asset canonical form cannot be blank" }

            if (canonicalForm.equals("native", ignoreCase = true)) {
                return AssetTypeNative
            }

            val parts = canonicalForm.split(":")
            if (parts.size != 2) {
                throw IllegalArgumentException(
                    "Invalid asset format: '$canonicalForm'. Expected 'CODE:ISSUER' or 'native'"
                )
            }

            return createNonNativeAsset(parts[0], parts[1])
        }

        /**
         * Creates an asset from individual components.
         *
         * @param type The asset type ("native" or null for auto-detection)
         * @param code The asset code (null for native)
         * @param issuer The asset issuer (null for native)
         * @return Asset instance
         * @throws IllegalArgumentException if the parameters are invalid
         */
        fun create(type: String?, code: String?, issuer: String?): Asset {
            if (type != null && type.equals("native", ignoreCase = true)) {
                return AssetTypeNative
            }

            if (code == null || issuer == null) {
                throw IllegalArgumentException(
                    "Code and issuer must be provided for non-native assets"
                )
            }

            return createNonNativeAsset(code, issuer)
        }

        /**
         * Creates a non-native asset, automatically selecting AlphaNum4 or AlphaNum12
         * based on the asset code length.
         *
         * @param code The asset code (1-12 characters)
         * @param issuer The issuer's account ID (G... address)
         * @return AssetTypeCreditAlphaNum4 or AssetTypeCreditAlphaNum12
         * @throws IllegalArgumentException if the code length is invalid or issuer is invalid
         */
        fun createNonNativeAsset(code: String, issuer: String): Asset {
            require(code.isNotBlank()) { "Asset code cannot be blank" }
            require(issuer.isNotBlank()) { "Asset issuer cannot be blank" }

            return when (code.length) {
                in 1..4 -> AssetTypeCreditAlphaNum4(code, issuer)
                in 5..12 -> AssetTypeCreditAlphaNum12(code, issuer)
                else -> throw IllegalArgumentException(
                    "Asset code length must be between 1 and 12 characters, got ${code.length}"
                )
            }
        }

        /**
         * Creates a native asset (Lumens/XLM).
         *
         * @return AssetTypeNative singleton instance
         */
        fun createNativeAsset(): Asset = AssetTypeNative

        /**
         * Decodes an Asset from its XDR representation.
         *
         * @param xdr The XDR Asset union
         * @return Asset instance (AssetTypeNative, AssetTypeCreditAlphaNum4, or AssetTypeCreditAlphaNum12)
         * @throws IllegalArgumentException if the XDR discriminant is unknown
         */
        fun fromXdr(xdr: AssetXdr): Asset {
            return when (xdr) {
                is AssetXdr.Void -> AssetTypeNative
                is AssetXdr.AlphaNum4 -> AssetTypeCreditAlphaNum4.fromXdr(xdr.value)
                is AssetXdr.AlphaNum12 -> AssetTypeCreditAlphaNum12.fromXdr(xdr.value)
            }
        }
    }
}

/**
 * Represents the native asset of the Stellar network (Lumens/XLM).
 *
 * This is a singleton object representing XLM, the native cryptocurrency of Stellar.
 * Unlike issued assets, the native asset doesn't have a code or issuer.
 *
 * ## Usage
 * ```kotlin
 * val xlm = AssetTypeNative
 * println(xlm) // "native"
 * ```
 *
 * @see <a href="https://developers.stellar.org/docs/learn/fundamentals/lumens">Lumens (XLM)</a>
 */
data object AssetTypeNative : Asset() {
    override val type: AssetTypeXdr = AssetTypeXdr.ASSET_TYPE_NATIVE

    override fun toXdr(): AssetXdr = AssetXdr.Void

    override fun toString(): String = "native"

    override fun compareTo(other: Asset): Int {
        // Native is always first
        return when (other) {
            is AssetTypeNative -> 0
            else -> -1
        }
    }
}

/**
 * Base class for issued assets (credit assets with an issuer).
 *
 * This sealed class represents assets that are issued by an account on the Stellar network.
 * All issued assets have:
 * - A code (1-12 alphanumeric characters)
 * - An issuer (the account that created the asset)
 *
 * Asset codes must contain only:
 * - Uppercase letters (A-Z)
 * - Digits (0-9)
 *
 * The issuer must be a valid ed25519 public key (G... address).
 *
 * @property code The asset code (1-12 characters)
 * @property issuer The issuer's account ID (G... strkey format)
 *
 * @see AssetTypeCreditAlphaNum4
 * @see AssetTypeCreditAlphaNum12
 * @see <a href="https://developers.stellar.org/docs/learn/fundamentals/stellar-data-structures/assets">Stellar Assets</a>
 */
sealed class AssetTypeCreditAlphaNum : Asset() {
    abstract val code: String
    abstract val issuer: String

    override fun toString(): String = "$code:$issuer"

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as AssetTypeCreditAlphaNum

        if (code != other.code) return false
        if (issuer != other.issuer) return false

        return true
    }

    override fun hashCode(): Int {
        var result = code.hashCode()
        result = 31 * result + issuer.hashCode()
        return result
    }

    /**
     * Validates that an asset code contains only valid characters.
     *
     * Valid characters are:
     * - Uppercase letters (A-Z)
     * - Digits (0-9)
     *
     * @throws IllegalArgumentException if the code contains invalid characters
     */
    protected fun validateAssetCode(code: String) {
        val invalidChars = code.filter { char ->
            char !in 'A'..'Z' && char !in '0'..'9'
        }

        if (invalidChars.isNotEmpty()) {
            throw IllegalArgumentException(
                "Asset code '$code' contains invalid characters: '${invalidChars}'. " +
                "Asset codes must contain only uppercase letters (A-Z) and digits (0-9)"
            )
        }
    }

    /**
     * Validates that an issuer is a valid ed25519 public key.
     *
     * @throws IllegalArgumentException if the issuer is invalid
     */
    protected fun validateIssuer(issuer: String) {
        require(issuer.isNotBlank()) { "Asset issuer cannot be blank" }
        require(StrKey.isValidEd25519PublicKey(issuer)) {
            "Invalid issuer: '$issuer'. Issuer must be a valid ed25519 public key (G... address)"
        }
    }

    protected fun compareAlphaNum(other: Asset): Int {
        if (other !is AssetTypeCreditAlphaNum) {
            // This shouldn't happen if called correctly, but handle it
            return 1
        }

        // First compare codes
        val codeComparison = this.code.compareTo(other.code)
        if (codeComparison != 0) {
            return codeComparison
        }

        // If codes are equal, compare issuers
        return this.issuer.compareTo(other.issuer)
    }
}

/**
 * Represents issued assets with codes 1-4 characters long.
 *
 * This class is used for assets with short codes like "USD", "EUR", "BTC", etc.
 *
 * ## Validation
 * - Code length must be 1-4 characters
 * - Code must contain only uppercase letters (A-Z) and digits (0-9)
 * - Issuer must be a valid ed25519 public key (G... address)
 *
 * ## Usage
 * ```kotlin
 * val usd = AssetTypeCreditAlphaNum4("USD", "GDUKMGUGDZQK6YHYA5Z6AY2G4XDSZPSZ3SW5UN3ARVMO6QSRDWP5YLEX")
 * val btc = AssetTypeCreditAlphaNum4("BTC", "GA5ZSEJYB37JRC5AVCIA5MOP4RHTM335X2KGX3IHOJAPP5RE34K4KZVN")
 * ```
 *
 * @property code The asset code (1-4 characters)
 * @property issuer The issuer's account ID (G... address)
 * @throws IllegalArgumentException if code length is not 1-4 characters or issuer is invalid
 */
class AssetTypeCreditAlphaNum4(
    override val code: String,
    override val issuer: String
) : AssetTypeCreditAlphaNum() {

    init {
        require(code.isNotBlank()) { "Asset code cannot be blank" }
        require(code.isNotEmpty() && code.length <= 4) {
            "Asset code length must be between 1 and 4 characters, got ${code.length}"
        }
        validateAssetCode(code)
        validateIssuer(issuer)
    }

    override val type: AssetTypeXdr = AssetTypeXdr.ASSET_TYPE_CREDIT_ALPHANUM4

    override fun toXdr(): AssetXdr {
        val assetCode4 = AssetCode4Xdr(Util.paddedByteArray(code, 4))
        val issuerAccountId = KeyPair.fromAccountId(issuer).getXdrAccountId()
        val alphaNum4 = AlphaNum4Xdr(assetCode4, issuerAccountId)
        return AssetXdr.AlphaNum4(alphaNum4)
    }

    override fun compareTo(other: Asset): Int {
        return when (other) {
            is AssetTypeNative -> 1
            is AssetTypeCreditAlphaNum12 -> -1
            is AssetTypeCreditAlphaNum4 -> compareAlphaNum(other)
            else -> 1
        }
    }

    companion object {
        /**
         * Creates an AssetTypeCreditAlphaNum4 from its XDR representation.
         *
         * @param xdr The XDR AlphaNum4 structure
         * @return AssetTypeCreditAlphaNum4 instance
         */
        fun fromXdr(xdr: AlphaNum4Xdr): AssetTypeCreditAlphaNum4 {
            val assetCode = Util.paddedByteArrayToString(xdr.assetCode.value)
            val issuer = when (val publicKey = xdr.issuer.value) {
                is PublicKeyXdr.Ed25519 -> StrKey.encodeEd25519PublicKey(publicKey.value.value)
            }
            return AssetTypeCreditAlphaNum4(assetCode, issuer)
        }
    }
}

/**
 * Represents issued assets with codes 5-12 characters long.
 *
 * This class is used for assets with longer codes that don't fit in AlphaNum4.
 *
 * ## Validation
 * - Code length must be 5-12 characters
 * - Code must contain only uppercase letters (A-Z) and digits (0-9)
 * - Issuer must be a valid ed25519 public key (G... address)
 *
 * ## Usage
 * ```kotlin
 * val asset = AssetTypeCreditAlphaNum12(
 *     "LONGERNAME",
 *     "GDUKMGUGDZQK6YHYA5Z6AY2G4XDSZPSZ3SW5UN3ARVMO6QSRDWP5YLEX"
 * )
 * ```
 *
 * @property code The asset code (5-12 characters)
 * @property issuer The issuer's account ID (G... address)
 * @throws IllegalArgumentException if code length is not 5-12 characters or issuer is invalid
 */
class AssetTypeCreditAlphaNum12(
    override val code: String,
    override val issuer: String
) : AssetTypeCreditAlphaNum() {

    init {
        require(code.isNotBlank()) { "Asset code cannot be blank" }
        require(code.length >= 5 && code.length <= 12) {
            "Asset code length must be between 5 and 12 characters, got ${code.length}"
        }
        validateAssetCode(code)
        validateIssuer(issuer)
    }

    override val type: AssetTypeXdr = AssetTypeXdr.ASSET_TYPE_CREDIT_ALPHANUM12

    override fun toXdr(): AssetXdr {
        val assetCode12 = AssetCode12Xdr(Util.paddedByteArray(code, 12))
        val issuerAccountId = KeyPair.fromAccountId(issuer).getXdrAccountId()
        val alphaNum12 = AlphaNum12Xdr(assetCode12, issuerAccountId)
        return AssetXdr.AlphaNum12(alphaNum12)
    }

    override fun compareTo(other: Asset): Int {
        return when (other) {
            is AssetTypeNative, is AssetTypeCreditAlphaNum4 -> 1
            is AssetTypeCreditAlphaNum12 -> compareAlphaNum(other)
            else -> 1
        }
    }

    companion object {
        /**
         * Creates an AssetTypeCreditAlphaNum12 from its XDR representation.
         *
         * @param xdr The XDR AlphaNum12 structure
         * @return AssetTypeCreditAlphaNum12 instance
         */
        fun fromXdr(xdr: AlphaNum12Xdr): AssetTypeCreditAlphaNum12 {
            val assetCode = Util.paddedByteArrayToString(xdr.assetCode.value)
            val issuer = when (val publicKey = xdr.issuer.value) {
                is PublicKeyXdr.Ed25519 -> StrKey.encodeEd25519PublicKey(publicKey.value.value)
            }
            return AssetTypeCreditAlphaNum12(assetCode, issuer)
        }
    }
}

/**
 * Extension function to get the XDR AccountID from a KeyPair.
 */
internal fun KeyPair.getXdrAccountId(): AccountIDXdr {
    val publicKeyBytes = this.getPublicKey()
    val uint256 = Uint256Xdr(publicKeyBytes)
    val publicKey = PublicKeyXdr.Ed25519(uint256)
    return AccountIDXdr(publicKey)
}
