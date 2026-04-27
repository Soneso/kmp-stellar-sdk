package com.soneso.stellar.sdk.unitTests.xdr

import com.soneso.stellar.sdk.xdr.*
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Verifies that XDR unions where multiple discriminant cases share the same payload
 * type preserve the original discriminant across decode and encode. A payload
 * received as e.g. `txFAILED` must not silently re-encode as `txSUCCESS`.
 *
 * Each fixture is a base64-encoded XDR payload produced by the Stellar reference
 * Rust XDR codec (github.com/stellar/stellar-xdr), exercising the second (or later)
 * discriminant case of a shared arm. The JSON shown above each fixture is the
 * verbatim input passed to the Rust encoder, so any maintainer can regenerate the
 * bytes via the stellar-xdr CLI:
 *
 *     stellar-xdr encode --type <Type> '<json>'
 *
 * The test decodes, asserts the discriminant matches what the wire carries, and
 * re-encodes to assert the bytes round-trip exactly.
 */
@OptIn(ExperimentalEncodingApi::class)
class XdrDiscriminantPreservationTest {

    private fun decodeBytes(base64: String): ByteArray = Base64.decode(base64)

    private inline fun <reified T> assertRoundTripBytes(
        base64: String,
        decode: (XdrReader) -> T,
        encode: (T, XdrWriter) -> Unit
    ): T {
        val bytes = decodeBytes(base64)
        val decoded = decode(XdrReader(bytes))
        val writer = XdrWriter()
        encode(decoded, writer)
        assertTrue(
            bytes.contentEquals(writer.toByteArray()),
            "Re-encoded bytes differ from input — discriminant likely lost on decode/encode"
        )
        return decoded
    }

    // ===== TransactionResultResult =====

    @Test
    fun txResultResult_txFailed_preservesDiscriminant() {
        // stellar-xdr encode --type TransactionResultResult '{"tx_failed":[{"op_inner":{"payment":"underfunded"}}]}'
        val decoded = assertRoundTripBytes(
            base64 = "/////wAAAAEAAAAAAAAAAf////4=",
            decode = { TransactionResultResultXdr.decode(it) },
            encode = { v, w -> v.encode(w) }
        )
        assertEquals(TransactionResultCodeXdr.txFAILED, decoded.discriminant)
        assertTrue(decoded is TransactionResultResultXdr.Results)
        assertEquals(1, decoded.value.size)
    }

    @Test
    fun txResultResult_txSuccess_preservesDiscriminant() {
        // stellar-xdr encode --type TransactionResultResult '{"tx_success":[{"op_inner":{"payment":"success"}}]}'
        val decoded = assertRoundTripBytes(
            base64 = "AAAAAAAAAAEAAAAAAAAAAQAAAAA=",
            decode = { TransactionResultResultXdr.decode(it) },
            encode = { v, w -> v.encode(w) }
        )
        assertEquals(TransactionResultCodeXdr.txSUCCESS, decoded.discriminant)
        assertTrue(decoded is TransactionResultResultXdr.Results)
        assertEquals(1, decoded.value.size)
    }

    @Test
    fun txResultResult_feeBumpInnerFailed_preservesDiscriminant() {
        // stellar-xdr encode --type TransactionResultResult \
        //   '{"tx_fee_bump_inner_failed":{"transaction_hash":"0000000000000000000000000000000000000000000000000000000000000000","result":{"fee_charged":"100","result":{"tx_failed":[{"op_inner":{"payment":"underfunded"}}]},"ext":"v0"}}}'
        val decoded = assertRoundTripBytes(
            base64 = "////8wAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAGT/////AAAAAQAAAAAAAAAB/////gAAAAA=",
            decode = { TransactionResultResultXdr.decode(it) },
            encode = { v, w -> v.encode(w) }
        )
        assertEquals(TransactionResultCodeXdr.txFEE_BUMP_INNER_FAILED, decoded.discriminant)
        assertTrue(decoded is TransactionResultResultXdr.InnerResultPair)
    }

    // ===== InnerTransactionResultResult =====

    @Test
    fun innerTxResultResult_txFailed_preservesDiscriminant() {
        // stellar-xdr encode --type InnerTransactionResultResult '{"tx_failed":[{"op_inner":{"change_trust":"malformed"}}]}'
        val decoded = assertRoundTripBytes(
            base64 = "/////wAAAAEAAAAAAAAABv////8=",
            decode = { InnerTransactionResultResultXdr.decode(it) },
            encode = { v, w -> v.encode(w) }
        )
        assertEquals(TransactionResultCodeXdr.txFAILED, decoded.discriminant)
        assertTrue(decoded is InnerTransactionResultResultXdr.Results)
        assertEquals(1, decoded.value.size)
    }

    @Test
    fun innerTxResultResult_txSuccess_emptyOps_preservesDiscriminant() {
        // stellar-xdr encode --type InnerTransactionResultResult '{"tx_success":[]}'
        val decoded = assertRoundTripBytes(
            base64 = "AAAAAAAAAAA=",
            decode = { InnerTransactionResultResultXdr.decode(it) },
            encode = { v, w -> v.encode(w) }
        )
        assertEquals(TransactionResultCodeXdr.txSUCCESS, decoded.discriminant)
        assertTrue(decoded is InnerTransactionResultResultXdr.Results)
        assertEquals(0, decoded.value.size)
    }

    // ===== SCError =====
    // The pre-fix `Code` data class collapsed nine SCErrorType discriminants
    // (SCE_WASM_VM through SCE_AUTH) into one hardcoded SCE_WASM_VM. Three
    // representative fixtures are sufficient to lock the contract.

    @Test
    fun scError_context_preservesDiscriminant() {
        // stellar-xdr encode --type ScError '{"context":"arith_domain"}'
        val decoded = assertRoundTripBytes(
            base64 = "AAAAAgAAAAA=",
            decode = { SCErrorXdr.decode(it) },
            encode = { v, w -> v.encode(w) }
        )
        assertEquals(SCErrorTypeXdr.SCE_CONTEXT, decoded.discriminant)
        assertTrue(decoded is SCErrorXdr.Code)
        assertEquals(SCErrorCodeXdr.SCEC_ARITH_DOMAIN, decoded.value)
    }

    @Test
    fun scError_storage_preservesDiscriminant() {
        // stellar-xdr encode --type ScError '{"storage":"missing_value"}'
        val decoded = assertRoundTripBytes(
            base64 = "AAAAAwAAAAM=",
            decode = { SCErrorXdr.decode(it) },
            encode = { v, w -> v.encode(w) }
        )
        assertEquals(SCErrorTypeXdr.SCE_STORAGE, decoded.discriminant)
        assertTrue(decoded is SCErrorXdr.Code)
        assertEquals(SCErrorCodeXdr.SCEC_MISSING_VALUE, decoded.value)
    }

    @Test
    fun scError_object_preservesDiscriminant() {
        // stellar-xdr encode --type ScError '{"object":"unexpected_type"}'
        val decoded = assertRoundTripBytes(
            base64 = "AAAABAAAAAg=",
            decode = { SCErrorXdr.decode(it) },
            encode = { v, w -> v.encode(w) }
        )
        assertEquals(SCErrorTypeXdr.SCE_OBJECT, decoded.discriminant)
        assertTrue(decoded is SCErrorXdr.Code)
        assertEquals(SCErrorCodeXdr.SCEC_UNEXPECTED_TYPE, decoded.value)
    }
}
