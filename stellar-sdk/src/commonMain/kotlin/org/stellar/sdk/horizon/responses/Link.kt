package org.stellar.sdk.horizon.responses

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Represents a HAL (Hypertext Application Language) link in Horizon API responses.
 *
 * @property href The URL of the link
 * @property templated Whether the href is a URI template
 */
@Serializable
data class Link(
    @SerialName("href")
    val href: String,

    @SerialName("templated")
    val templated: Boolean? = null
)
