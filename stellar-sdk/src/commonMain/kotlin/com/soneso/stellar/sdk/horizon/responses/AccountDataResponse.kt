package com.soneso.stellar.sdk.horizon.responses

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * Represents an account data response from the Horizon API.
 *
 * This response contains a single data entry (key-value pair) stored on an account.
 * The value is base64-encoded and can be decoded to bytes or (if valid UTF-8) to a string.
 *
 * ## Usage
 *
 * ```kotlin
 * val horizonServer = HorizonServer("https://horizon-testnet.stellar.org")
 * val dataResponse = horizonServer.accounts().accountData(accountId, "config")
 *
 * // Get base64 value
 * println(dataResponse.value)
 *
 * // Get decoded bytes
 * val bytes = dataResponse.decodedValue
 *
 * // Get decoded string (throws if not valid UTF-8)
 * val string = dataResponse.decodedString
 *
 * // Get decoded string or null (safe for binary data)
 * val stringOrNull = dataResponse.decodedStringOrNull
 * ```
 *
 * @property value The base64-encoded data value
 *
 * @see <a href="https://developers.stellar.org/api/resources/accounts/retrieve-account-data">Retrieve Account Data</a>
 */
@Serializable
data class AccountDataResponse(
    @SerialName("value")
    val value: String
) : Response() {

    /**
     * Decodes the base64-encoded value to a ByteArray.
     *
     * @return The decoded bytes
     * @throws IllegalArgumentException if the value is not valid base64
     */
    @OptIn(ExperimentalEncodingApi::class)
    val decodedValue: ByteArray
        get() = Base64.decode(value)

    /**
     * Decodes the base64-encoded value to a UTF-8 string.
     *
     * **Warning**: This property throws an exception if the decoded bytes are not valid UTF-8.
     * Use [decodedStringOrNull] if you're unsure whether the data is text or binary.
     *
     * @return The decoded UTF-8 string
     * @throws IllegalArgumentException if the decoded bytes are not valid UTF-8
     */
    val decodedString: String
        get() = decodedValue.decodeToString()

    /**
     * Decodes the base64-encoded value to a UTF-8 string, or returns null if not valid UTF-8.
     *
     * This is the safe version of [decodedString] that handles binary data gracefully.
     * Use this when the data entry might contain binary (non-text) data.
     *
     * @return The decoded UTF-8 string, or null if the bytes are not valid UTF-8
     */
    val decodedStringOrNull: String?
        get() = try {
            decodedValue.decodeToString()
        } catch (e: Exception) {
            null
        }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as AccountDataResponse

        return value == other.value
    }

    override fun hashCode(): Int {
        return value.hashCode()
    }

    override fun toString(): String {
        return "AccountDataResponse(value='$value', decodedStringOrNull=${decodedStringOrNull?.let { "'$it'" } ?: "null"})"
    }
}
