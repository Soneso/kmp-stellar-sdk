package com.stellar.sdk.horizon.responses

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Represents an asset in the Stellar network.
 *
 * Assets can be either native (XLM) or credit assets issued by an account.
 * For credit assets, the asset type, code, and issuer are all required.
 *
 * This class is used in various Horizon API responses including paths, order books, and trades.
 *
 * @property assetType The type of asset (native, credit_alphanum4, credit_alphanum12)
 * @property assetCode The asset code (null for native)
 * @property assetIssuer The issuer account ID (null for native)
 *
 * @see <a href="https://developers.stellar.org/docs/learn/fundamentals/stellar-data-structures/assets">Assets documentation</a>
 */
@Serializable
data class Asset(
    @SerialName("asset_type")
    val assetType: String,

    @SerialName("asset_code")
    val assetCode: String? = null,

    @SerialName("asset_issuer")
    val assetIssuer: String? = null
) {
    /**
     * Returns true if this asset is the native asset (XLM).
     */
    fun isNative(): Boolean = assetType == "native"

    /**
     * Returns a canonical string representation of the asset.
     * For native assets, returns "native".
     * For credit assets, returns "CODE:ISSUER".
     */
    fun toCanonicalForm(): String {
        return when {
            isNative() -> "native"
            assetCode != null && assetIssuer != null -> "$assetCode:$assetIssuer"
            else -> throw IllegalStateException("Invalid asset: missing code or issuer for credit asset")
        }
    }

    companion object {
        /**
         * Creates an Asset instance from a canonical string representation.
         *
         * @param canonicalForm Either "native" or "CODE:ISSUER"
         * @return Asset instance
         * @throws IllegalArgumentException if the canonical form is invalid
         */
        fun fromCanonicalForm(canonicalForm: String): Asset {
            return when {
                canonicalForm == "native" -> Asset("native")
                canonicalForm.contains(":") -> {
                    val parts = canonicalForm.split(":")
                    if (parts.size != 2) {
                        throw IllegalArgumentException("Invalid asset canonical form: $canonicalForm")
                    }
                    val code = parts[0]
                    val issuer = parts[1]
                    val type = when (code.length) {
                        in 1..4 -> "credit_alphanum4"
                        in 5..12 -> "credit_alphanum12"
                        else -> throw IllegalArgumentException("Invalid asset code length: ${code.length}")
                    }
                    Asset(type, code, issuer)
                }
                else -> throw IllegalArgumentException("Invalid asset canonical form: $canonicalForm")
            }
        }

        /**
         * Creates a native asset (XLM).
         */
        fun native(): Asset = Asset("native")

        /**
         * Creates a credit asset.
         *
         * @param code The asset code (1-12 characters)
         * @param issuer The issuer account ID
         * @return Asset instance
         * @throws IllegalArgumentException if code length is invalid
         */
        fun create(code: String, issuer: String): Asset {
            val type = when (code.length) {
                in 1..4 -> "credit_alphanum4"
                in 5..12 -> "credit_alphanum12"
                else -> throw IllegalArgumentException("Invalid asset code length: ${code.length}. Must be 1-12 characters.")
            }
            return Asset(type, code, issuer)
        }
    }
}
