// Copyright 2026 Soneso. All rights reserved.
// Use of this source code is governed by a license that can be
// found in the LICENSE file.

package com.soneso.stellar.sdk.sep.sep31

/**
 * Enumeration of SEP-31 transaction lifecycle statuses defined by the spec.
 *
 * Each constant carries the lowercase wire form ([value]) used by the Receiving Anchor in
 * `GET /transactions/:id` responses. The enum exists as a downstream helper for consumers
 * that prefer typed status handling. The SDK itself keeps
 * [Sep31TransactionResponse.status] as a raw `String` so a new spec status does not
 * require an SDK release; this enum is intentionally not consulted inside `Sep31Service`.
 *
 * ## Usage
 *
 * ```kotlin
 * val response: Sep31TransactionResponse = sep31Service.getTransaction(id = "tx-id", jwt = jwt)
 * when (Sep31TransactionStatus.fromString(response.status)) {
 *     Sep31TransactionStatus.PENDING_SENDER -> println("Awaiting Sending Anchor payment")
 *     Sep31TransactionStatus.COMPLETED -> println("Delivered to Receiving Client")
 *     null -> println("Unknown status: ${response.status}")
 *     else -> println("Status: ${response.status}")
 * }
 * ```
 *
 * @property value The exact lowercase wire form used in SEP-31 JSON payloads.
 * @see <a href="https://github.com/stellar/stellar-protocol/blob/master/ecosystem/sep-0031.md#transaction-object">SEP-0031 Transaction Object</a>
 */
enum class Sep31TransactionStatus(val value: String) {
    /** Awaiting payment from the Sending Anchor. */
    PENDING_SENDER("pending_sender"),

    /** Stellar payment submitted by the Sending Anchor but not yet confirmed on-network. */
    PENDING_STELLAR("pending_stellar"),

    /** Customer KYC information must be updated via SEP-12 before the transaction can advance. */
    PENDING_CUSTOMER_INFO_UPDATE("pending_customer_info_update"),

    /** Per-transaction fields require an update (legacy; superseded by SEP-12). */
    PENDING_TRANSACTION_INFO_UPDATE("pending_transaction_info_update"),

    /** Payment is being processed by the Receiving Anchor. */
    PENDING_RECEIVER("pending_receiver"),

    /** Payment was submitted to an external (off-chain) network but is not yet confirmed. */
    PENDING_EXTERNAL("pending_external"),

    /** Funds successfully delivered to the Receiving Client. */
    COMPLETED("completed"),

    /** Funds were refunded to the Sending Anchor; inspect [Sep31TransactionResponse.refunds] for details. */
    REFUNDED("refunded"),

    /** Transaction abandoned or the underlying SEP-38 quote expired. */
    EXPIRED("expired"),

    /** Catch-all for unspecified errors; inspect [Sep31TransactionResponse.statusMessage] for context. */
    ERROR("error");

    companion object {
        /**
         * Resolves a wire-form status string to the matching enum constant.
         *
         * Comparison is case-sensitive against [value]. Returns `null` for any unknown
         * value, including values that differ only in casing. The case-sensitive
         * behavior keeps the SDK forward-compatible: a future spec amendment that
         * introduces a new lowercase status will be accepted by
         * [Sep31TransactionResponse.status] (raw `String`) and surface as `null` here
         * rather than as a mis-typed enum.
         *
         * ```kotlin
         * Sep31TransactionStatus.fromString("completed")  // returns Sep31TransactionStatus.COMPLETED
         * Sep31TransactionStatus.fromString("COMPLETED")  // returns null (case-sensitive)
         * Sep31TransactionStatus.fromString("future_new") // returns null (unknown)
         * ```
         *
         * @param value The wire-form status string to resolve.
         * @return The matching enum constant, or `null` if [value] is not a known status.
         */
        fun fromString(value: String): Sep31TransactionStatus? =
            entries.firstOrNull { it.value == value }
    }
}
