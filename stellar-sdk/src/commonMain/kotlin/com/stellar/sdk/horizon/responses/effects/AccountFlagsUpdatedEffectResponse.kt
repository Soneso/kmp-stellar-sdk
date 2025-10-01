package com.stellar.sdk.horizon.responses.effects

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Represents account_flags_updated effect response.
 *
 * This effect occurs when an account's flags are updated.
 *
 * @see [Effect Documentation](https://developers.stellar.org/docs/data/horizon/api-reference/resources/effects)
 */
@Serializable
@SerialName("account_flags_updated")
data class AccountFlagsUpdatedEffectResponse(
    override val id: String,
    override val account: String?,
    @SerialName("account_muxed")
    override val accountMuxed: String? = null,
    @SerialName("account_muxed_id")
    override val accountMuxedId: String? = null,
    override val type: String,
    @SerialName("created_at")
    override val createdAt: String,
    @SerialName("paging_token")
    override val pagingToken: String,
    @SerialName("_links")
    override val links: EffectLinks,

    /**
     * Whether authorization is required for this account
     */
    @SerialName("auth_required_flag")
    val authRequiredFlag: Boolean? = null,

    /**
     * Whether authorization is revocable for this account
     */
    @SerialName("auth_revokable_flag")
    val authRevokableFlag: Boolean? = null
) : EffectResponse()
