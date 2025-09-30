package org.stellar.sdk.horizon.responses

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Represents a claim predicate for claimable balances.
 * This is a simplified representation of the predicate structure.
 *
 * @property unconditional Whether the predicate is unconditional (always claimable)
 * @property absBefore Absolute time before which the balance can be claimed
 * @property relBefore Relative time before which the balance can be claimed
 * @property and AND logical combination of predicates
 * @property or OR logical combination of predicates
 * @property not NOT logical negation of a predicate
 */
@Serializable
data class Predicate(
    @SerialName("unconditional")
    val unconditional: Boolean? = null,

    @SerialName("abs_before")
    val absBefore: String? = null,

    @SerialName("rel_before")
    val relBefore: String? = null,

    @SerialName("and")
    val and: List<Predicate>? = null,

    @SerialName("or")
    val or: List<Predicate>? = null,

    @SerialName("not")
    val not: Predicate? = null
)
