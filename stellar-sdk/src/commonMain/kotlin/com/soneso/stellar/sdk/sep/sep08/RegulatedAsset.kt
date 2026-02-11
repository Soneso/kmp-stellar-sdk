// Copyright 2026 Soneso. All rights reserved.
// Use of this source code is governed by a license that can be
// found in the LICENSE file.

package com.soneso.stellar.sdk.sep.sep08

import com.soneso.stellar.sdk.Asset
import com.soneso.stellar.sdk.xdr.AssetTypeXdr
import com.soneso.stellar.sdk.xdr.AssetXdr

/**
 * Represents a regulated asset on the Stellar network that requires approval before transactions
 * can be submitted.
 *
 * Regulated assets (SEP-8) are issued assets that have the `auth_required` flag set and specify
 * an `approval_server` in the issuer's stellar.toml file. Transactions involving these assets
 * must be submitted to the approval server for authorization before they can be sent to
 * the Stellar network.
 *
 * This class wraps a standard Stellar [Asset] (AlphaNum4 or AlphaNum12) and adds the
 * approval server URL and optional approval criteria from the stellar.toml configuration.
 *
 * ## Usage
 *
 * ### Create a regulated asset
 * ```kotlin
 * val regulatedAsset = RegulatedAsset(
 *     code = "GOAT",
 *     issuer = "GDUKMGUGDZQK6YHYA5Z6AY2G4XDSZPSZ3SW5UN3ARVMO6QSRDWP5YLEX",
 *     approvalServer = "https://example.com/tx_approve",
 *     approvalCriteria = "Transactions must be below 500 GOAT per account per day"
 * )
 * ```
 *
 * ### Access the underlying asset
 * ```kotlin
 * val asset = regulatedAsset.toAsset()
 * val xdr = regulatedAsset.toXdr()
 * ```
 *
 * @property code The asset code (1-12 characters)
 * @property issuer The issuer's account ID (G... address)
 * @property approvalServer The URL of the approval server for this asset
 * @property approvalCriteria Optional human-readable criteria describing the approval requirements
 *
 * @see <a href="https://github.com/stellar/stellar-protocol/blob/master/ecosystem/sep-0008.md">SEP-0008 Specification</a>
 */
class RegulatedAsset(
    val code: String,
    val issuer: String,
    val approvalServer: String,
    val approvalCriteria: String? = null
) : Comparable<RegulatedAsset> {

    /**
     * The underlying Stellar asset (AlphaNum4 or AlphaNum12).
     */
    private val asset: Asset = Asset.createNonNativeAsset(code, issuer)

    /**
     * Returns the asset type of the underlying asset.
     */
    val type: AssetTypeXdr
        get() = asset.type

    /**
     * Converts the underlying asset to its XDR representation.
     *
     * @return The XDR Asset union representing this asset
     */
    fun toXdr(): AssetXdr = asset.toXdr()

    /**
     * Returns the underlying [Asset] instance.
     *
     * Use this method when you need to pass the asset to SDK methods that accept [Asset]
     * parameters, such as transaction building or Horizon queries.
     *
     * @return The underlying Asset (AssetTypeCreditAlphaNum4 or AssetTypeCreditAlphaNum12)
     */
    fun toAsset(): Asset = asset

    /**
     * Returns the canonical string representation of this asset in "CODE:ISSUER" format.
     */
    override fun toString(): String = "$code:$issuer"

    override fun compareTo(other: RegulatedAsset): Int {
        return asset.compareTo(other.asset)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as RegulatedAsset

        if (code != other.code) return false
        if (issuer != other.issuer) return false
        if (approvalServer != other.approvalServer) return false
        if (approvalCriteria != other.approvalCriteria) return false

        return true
    }

    override fun hashCode(): Int {
        var result = code.hashCode()
        result = 31 * result + issuer.hashCode()
        result = 31 * result + approvalServer.hashCode()
        result = 31 * result + (approvalCriteria?.hashCode() ?: 0)
        return result
    }
}
