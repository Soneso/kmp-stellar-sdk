package com.stellar.sdk.horizon.responses.operations

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator
import com.stellar.sdk.horizon.responses.Link
import com.stellar.sdk.horizon.responses.Pageable
import com.stellar.sdk.horizon.responses.Response
import com.stellar.sdk.horizon.responses.TransactionResponse

/**
 * Base class for all operation responses from the Horizon API.
 * Uses polymorphic serialization based on the "type" field.
 *
 * @see <a href="https://developers.stellar.org/api/resources/operations/">Operation documentation</a>
 */
@Serializable
@JsonClassDiscriminator("type")
sealed class OperationResponse : Response(), Pageable {
    abstract val id: String
    abstract val sourceAccount: String
    abstract val sourceAccountMuxed: String?
    abstract val sourceAccountMuxedId: String?
    abstract override val pagingToken: String
    abstract val createdAt: String
    abstract val transactionHash: String
    abstract val transactionSuccessful: Boolean
    abstract val type: String
    abstract val links: Links
    abstract val transaction: TransactionResponse?

    /**
     * HAL links connected to the operation.
     */
    @Serializable
    data class Links(
        @SerialName("effects")
        val effects: Link,

        @SerialName("precedes")
        val precedes: Link,

        @SerialName("self")
        val self: Link,

        @SerialName("succeeds")
        val succeeds: Link,

        @SerialName("transaction")
        val transaction: Link
    )
}
