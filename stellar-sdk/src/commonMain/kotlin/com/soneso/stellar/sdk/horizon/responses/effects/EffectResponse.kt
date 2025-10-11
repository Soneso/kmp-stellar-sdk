package com.soneso.stellar.sdk.horizon.responses.effects

import com.soneso.stellar.sdk.horizon.responses.Link
import com.soneso.stellar.sdk.horizon.responses.Pageable
import com.soneso.stellar.sdk.horizon.responses.Response
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Sealed class for effect responses.
 *
 * Effects represent specific changes that occur in the ledger as a result of successful operations,
 * but are not operations themselves.
 *
 * All effect types must be subclasses of this sealed class to enable polymorphic serialization.
 * Polymorphic deserialization is handled via a custom JsonContentPolymorphicSerializer that
 * preserves the "type" field as both a discriminator and a property.
 *
 * @see [Effect Documentation](https://developers.stellar.org/docs/data/horizon/api-reference/resources/effects)
 * @see EffectResponseSerializer
 */
@Serializable(with = EffectResponseSerializer::class)
sealed class EffectResponse : Response(), Pageable {
    /**
     * A unique identifier for this effect
     */
    abstract val id: String

    /**
     * The account address that is associated with this effect
     */
    abstract val account: String?

    /**
     * The muxed account address that is associated with this effect
     */
    @SerialName("account_muxed")
    abstract val accountMuxed: String?

    /**
     * The muxed account ID that is associated with this effect
     */
    @SerialName("account_muxed_id")
    abstract val accountMuxedId: String?

    /**
     * Type of effect
     * @see [Effect Types](https://developers.stellar.org/api/horizon/resources/effects/types)
     */
    abstract val type: String

    /**
     * ISO 8601 timestamp of when this effect occurred
     */
    @SerialName("created_at")
    abstract val createdAt: String

    /**
     * A cursor value for use in pagination
     */
    @SerialName("paging_token")
    abstract override val pagingToken: String

    /**
     * Links related to this effect
     */
    @SerialName("_links")
    abstract val links: EffectLinks

    /**
     * Represents effect links
     */
    @Serializable
    data class EffectLinks(
        /**
         * Link to the operation that created this effect
         */
        val operation: Link,

        /**
         * Link to the effect that preceded this one
         */
        val precedes: Link,

        /**
         * Link to the effect that succeeded this one
         */
        val succeeds: Link
    )
}
