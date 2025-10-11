package com.soneso.stellar.sdk.horizon.responses

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Represents an entity who is eligible to claim a claimable balance.
 *
 * @property destination The destination account ID
 * @property predicate The predicate for this claimable balance
 */
@Serializable
data class Claimant(
    @SerialName("destination")
    val destination: String,

    @SerialName("predicate")
    val predicate: Predicate
)
