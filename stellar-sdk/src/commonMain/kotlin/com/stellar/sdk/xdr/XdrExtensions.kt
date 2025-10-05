package com.stellar.sdk.xdr

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
