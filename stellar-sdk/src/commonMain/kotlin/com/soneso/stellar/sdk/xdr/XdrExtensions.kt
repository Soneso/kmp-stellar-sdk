package com.soneso.stellar.sdk.xdr

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * Extension functions for converting XDR types to/from base64 encoded strings.
 *
 * These functions provide a convenient way to serialize and deserialize XDR types
 * for transmission over JSON-RPC and HTTP APIs.
 */

/**
 * Encodes this XDR object to a base64 string.
 *
 * @return Base64-encoded XDR representation
 */
@OptIn(ExperimentalEncodingApi::class)
fun LedgerKeyXdr.toXdrBase64(): String {
    val writer = XdrWriter()
    encode(writer)
    return Base64.encode(writer.toByteArray())
}

/**
 * Decodes a LedgerKeyXdr from a base64 string.
 *
 * @param base64 Base64-encoded XDR string
 * @return Decoded LedgerKeyXdr object
 */
@OptIn(ExperimentalEncodingApi::class)
fun LedgerKeyXdr.Companion.fromXdrBase64(base64: String): LedgerKeyXdr {
    val bytes = Base64.decode(base64)
    val reader = XdrReader(bytes)
    return decode(reader)
}

/**
 * Encodes this XDR object to a base64 string.
 *
 * @return Base64-encoded XDR representation
 */
@OptIn(ExperimentalEncodingApi::class)
fun TransactionEnvelopeXdr.toXdrBase64(): String {
    val writer = XdrWriter()
    encode(writer)
    return Base64.encode(writer.toByteArray())
}

/**
 * Decodes a TransactionEnvelopeXdr from a base64 string.
 *
 * @param base64 Base64-encoded XDR string
 * @return Decoded TransactionEnvelopeXdr object
 */
@OptIn(ExperimentalEncodingApi::class)
fun TransactionEnvelopeXdr.Companion.fromXdrBase64(base64: String): TransactionEnvelopeXdr {
    val bytes = Base64.decode(base64)
    val reader = XdrReader(bytes)
    return decode(reader)
}

/**
 * Encodes this XDR object to a base64 string.
 *
 * @return Base64-encoded XDR representation
 */
@OptIn(ExperimentalEncodingApi::class)
fun LedgerEntryDataXdr.toXdrBase64(): String {
    val writer = XdrWriter()
    encode(writer)
    return Base64.encode(writer.toByteArray())
}

/**
 * Decodes a LedgerEntryDataXdr from a base64 string.
 *
 * @param base64 Base64-encoded XDR string
 * @return Decoded LedgerEntryDataXdr object
 */
@OptIn(ExperimentalEncodingApi::class)
fun LedgerEntryDataXdr.Companion.fromXdrBase64(base64: String): LedgerEntryDataXdr {
    val bytes = Base64.decode(base64)
    val reader = XdrReader(bytes)
    return decode(reader)
}

/**
 * Encodes this XDR object to a base64 string.
 *
 * @return Base64-encoded XDR representation
 */
@OptIn(ExperimentalEncodingApi::class)
fun SorobanTransactionDataXdr.toXdrBase64(): String {
    val writer = XdrWriter()
    encode(writer)
    return Base64.encode(writer.toByteArray())
}

/**
 * Decodes a SorobanTransactionDataXdr from a base64 string.
 *
 * @param base64 Base64-encoded XDR string
 * @return Decoded SorobanTransactionDataXdr object
 */
@OptIn(ExperimentalEncodingApi::class)
fun SorobanTransactionDataXdr.Companion.fromXdrBase64(base64: String): SorobanTransactionDataXdr {
    val bytes = Base64.decode(base64)
    val reader = XdrReader(bytes)
    return decode(reader)
}

/**
 * Encodes this XDR object to a base64 string.
 *
 * @return Base64-encoded XDR representation
 */
@OptIn(ExperimentalEncodingApi::class)
fun SorobanAuthorizationEntryXdr.toXdrBase64(): String {
    val writer = XdrWriter()
    encode(writer)
    return Base64.encode(writer.toByteArray())
}

/**
 * Decodes a SorobanAuthorizationEntryXdr from a base64 string.
 *
 * @param base64 Base64-encoded XDR string
 * @return Decoded SorobanAuthorizationEntryXdr object
 */
@OptIn(ExperimentalEncodingApi::class)
fun SorobanAuthorizationEntryXdr.Companion.fromXdrBase64(base64: String): SorobanAuthorizationEntryXdr {
    val bytes = Base64.decode(base64)
    val reader = XdrReader(bytes)
    return decode(reader)
}

/**
 * Encodes this XDR object to a base64 string.
 *
 * @return Base64-encoded XDR representation
 */
@OptIn(ExperimentalEncodingApi::class)
fun TransactionResultXdr.toXdrBase64(): String {
    val writer = XdrWriter()
    encode(writer)
    return Base64.encode(writer.toByteArray())
}

/**
 * Decodes a TransactionResultXdr from a base64 string.
 *
 * @param base64 Base64-encoded XDR string
 * @return Decoded TransactionResultXdr object
 */
@OptIn(ExperimentalEncodingApi::class)
fun TransactionResultXdr.Companion.fromXdrBase64(base64: String): TransactionResultXdr {
    val bytes = Base64.decode(base64)
    val reader = XdrReader(bytes)
    return decode(reader)
}

/**
 * Encodes this XDR object to a base64 string.
 *
 * @return Base64-encoded XDR representation
 */
@OptIn(ExperimentalEncodingApi::class)
fun TransactionMetaXdr.toXdrBase64(): String {
    val writer = XdrWriter()
    encode(writer)
    return Base64.encode(writer.toByteArray())
}

/**
 * Decodes a TransactionMetaXdr from a base64 string.
 *
 * @param base64 Base64-encoded XDR string
 * @return Decoded TransactionMetaXdr object
 */
@OptIn(ExperimentalEncodingApi::class)
fun TransactionMetaXdr.Companion.fromXdrBase64(base64: String): TransactionMetaXdr {
    val bytes = Base64.decode(base64)
    val reader = XdrReader(bytes)
    return decode(reader)
}

/**
 * Encodes this XDR object to a base64 string.
 *
 * @return Base64-encoded XDR representation
 */
@OptIn(ExperimentalEncodingApi::class)
fun DiagnosticEventXdr.toXdrBase64(): String {
    val writer = XdrWriter()
    encode(writer)
    return Base64.encode(writer.toByteArray())
}

/**
 * Decodes a DiagnosticEventXdr from a base64 string.
 *
 * @param base64 Base64-encoded XDR string
 * @return Decoded DiagnosticEventXdr object
 */
@OptIn(ExperimentalEncodingApi::class)
fun DiagnosticEventXdr.Companion.fromXdrBase64(base64: String): DiagnosticEventXdr {
    val bytes = Base64.decode(base64)
    val reader = XdrReader(bytes)
    return decode(reader)
}

/**
 * Encodes this XDR object to a base64 string.
 *
 * @return Base64-encoded XDR representation
 */
@OptIn(ExperimentalEncodingApi::class)
fun ContractEventXdr.toXdrBase64(): String {
    val writer = XdrWriter()
    encode(writer)
    return Base64.encode(writer.toByteArray())
}

/**
 * Decodes a ContractEventXdr from a base64 string.
 *
 * @param base64 Base64-encoded XDR string
 * @return Decoded ContractEventXdr object
 */
@OptIn(ExperimentalEncodingApi::class)
fun ContractEventXdr.Companion.fromXdrBase64(base64: String): ContractEventXdr {
    val bytes = Base64.decode(base64)
    val reader = XdrReader(bytes)
    return decode(reader)
}

/**
 * Encodes this XDR object to a base64 string.
 *
 * @return Base64-encoded XDR representation
 */
@OptIn(ExperimentalEncodingApi::class)
fun LedgerEntryXdr.toXdrBase64(): String {
    val writer = XdrWriter()
    encode(writer)
    return Base64.encode(writer.toByteArray())
}

/**
 * Decodes a LedgerEntryXdr from a base64 string.
 *
 * @param base64 Base64-encoded XDR string
 * @return Decoded LedgerEntryXdr object
 */
@OptIn(ExperimentalEncodingApi::class)
fun LedgerEntryXdr.Companion.fromXdrBase64(base64: String): LedgerEntryXdr {
    val bytes = Base64.decode(base64)
    val reader = XdrReader(bytes)
    return decode(reader)
}

/**
 * Encodes this XDR object to a base64 string.
 *
 * @return Base64-encoded XDR representation
 */
@OptIn(ExperimentalEncodingApi::class)
fun SCValXdr.toXdrBase64(): String {
    val writer = XdrWriter()
    encode(writer)
    return Base64.encode(writer.toByteArray())
}

/**
 * Decodes a SCValXdr from a base64 string.
 *
 * @param base64 Base64-encoded XDR string
 * @return Decoded SCValXdr object
 */
@OptIn(ExperimentalEncodingApi::class)
fun SCValXdr.Companion.fromXdrBase64(base64: String): SCValXdr {
    val bytes = Base64.decode(base64)
    val reader = XdrReader(bytes)
    return decode(reader)
}

/**
 * Encodes this XDR object to a base64 string.
 *
 * @return Base64-encoded XDR representation
 */
@OptIn(ExperimentalEncodingApi::class)
fun LedgerHeaderHistoryEntryXdr.toXdrBase64(): String {
    val writer = XdrWriter()
    encode(writer)
    return Base64.encode(writer.toByteArray())
}

/**
 * Decodes a LedgerHeaderHistoryEntryXdr from a base64 string.
 *
 * @param base64 Base64-encoded XDR string
 * @return Decoded LedgerHeaderHistoryEntryXdr object
 */
@OptIn(ExperimentalEncodingApi::class)
fun LedgerHeaderHistoryEntryXdr.Companion.fromXdrBase64(base64: String): LedgerHeaderHistoryEntryXdr {
    val bytes = Base64.decode(base64)
    val reader = XdrReader(bytes)
    return decode(reader)
}

/**
 * Encodes this XDR object to a base64 string.
 *
 * @return Base64-encoded XDR representation
 */
@OptIn(ExperimentalEncodingApi::class)
fun LedgerCloseMetaXdr.toXdrBase64(): String {
    val writer = XdrWriter()
    encode(writer)
    return Base64.encode(writer.toByteArray())
}

/**
 * Decodes a LedgerCloseMetaXdr from a base64 string.
 *
 * @param base64 Base64-encoded XDR string
 * @return Decoded LedgerCloseMetaXdr object
 */
@OptIn(ExperimentalEncodingApi::class)
fun LedgerCloseMetaXdr.Companion.fromXdrBase64(base64: String): LedgerCloseMetaXdr {
    val bytes = Base64.decode(base64)
    val reader = XdrReader(bytes)
    return decode(reader)
}

/**
 * Encodes this XDR object to a base64 string.
 *
 * @return Base64-encoded XDR representation
 */
@OptIn(ExperimentalEncodingApi::class)
fun TransactionEventXdr.toXdrBase64(): String {
    val writer = XdrWriter()
    encode(writer)
    return Base64.encode(writer.toByteArray())
}

/**
 * Decodes a TransactionEventXdr from a base64 string.
 *
 * @param base64 Base64-encoded XDR string
 * @return Decoded TransactionEventXdr object
 */
@OptIn(ExperimentalEncodingApi::class)
fun TransactionEventXdr.Companion.fromXdrBase64(base64: String): TransactionEventXdr {
    val bytes = Base64.decode(base64)
    val reader = XdrReader(bytes)
    return decode(reader)
}

/**
 * Encodes this XDR object to a base64 string.
 *
 * @return Base64-encoded XDR representation
 */
@OptIn(ExperimentalEncodingApi::class)
fun HostFunctionXdr.toXdrBase64(): String {
    val writer = XdrWriter()
    encode(writer)
    return Base64.encode(writer.toByteArray())
}

/**
 * Decodes a HostFunctionXdr from a base64 string.
 *
 * @param base64 Base64-encoded XDR string
 * @return Decoded HostFunctionXdr object
 */
@OptIn(ExperimentalEncodingApi::class)
fun HostFunctionXdr.Companion.fromXdrBase64(base64: String): HostFunctionXdr {
    val bytes = Base64.decode(base64)
    val reader = XdrReader(bytes)
    return decode(reader)
}
