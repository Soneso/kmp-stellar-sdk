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

    @Test
    fun txResultResult_feeBumpInnerSuccess_preservesDiscriminant() {
        // stellar-xdr encode --type TransactionResultResult \
        //   '{"tx_fee_bump_inner_success":{"transaction_hash":"0000000000000000000000000000000000000000000000000000000000000000","result":{"fee_charged":"100","result":{"tx_success":[{"op_inner":{"payment":"success"}}]},"ext":"v0"}}}'
        val decoded = assertRoundTripBytes(
            base64 = "AAAAAQAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAGQAAAAAAAAAAQAAAAAAAAABAAAAAAAAAAA=",
            decode = { TransactionResultResultXdr.decode(it) },
            encode = { v, w -> v.encode(w) }
        )
        assertEquals(TransactionResultCodeXdr.txFEE_BUMP_INNER_SUCCESS, decoded.discriminant)
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

    // ===== BucketEntry =====
    // The pre-fix `LiveEntry` data class collapsed LIVEENTRY and INITENTRY
    // (both carrying LedgerEntry) into one hardcoded LIVEENTRY.

    @Test
    fun bucketEntry_initEntry_preservesDiscriminant() {
        // stellar-xdr encode --type BucketEntry \
        //   '{"initentry":{"last_modified_ledger_seq":1,"data":{"ttl":{"key_hash":"0000000000000000000000000000000000000000000000000000000000000000","live_until_ledger_seq":1000}},"ext":"v0"}}'
        val decoded = assertRoundTripBytes(
            base64 = "AAAAAgAAAAEAAAAJAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAPoAAAAAA==",
            decode = { BucketEntryXdr.decode(it) },
            encode = { v, w -> v.encode(w) }
        )
        assertEquals(BucketEntryTypeXdr.INITENTRY, decoded.discriminant)
        assertTrue(decoded is BucketEntryXdr.LiveEntry)
    }

    // ===== ManageOfferSuccessResultOffer =====
    // The pre-fix `Offer` data class collapsed MANAGE_OFFER_CREATED and
    // MANAGE_OFFER_UPDATED (both carrying OfferEntry) into one hardcoded
    // MANAGE_OFFER_CREATED.

    @Test
    fun manageOfferSuccess_updated_preservesDiscriminant() {
        // stellar-xdr encode --type ManageOfferSuccessResultOffer \
        //   '{"updated":{"seller_id":"GA7QYNF7SOWQ3GLR2BGMZEHXAVIRZA4KVWLTJJFC7MGXUA74P7UJVSGZ","offer_id":"42","selling":"native","buying":{"credit_alphanum4":{"asset_code":"USD","issuer":"GA7QYNF7SOWQ3GLR2BGMZEHXAVIRZA4KVWLTJJFC7MGXUA74P7UJVSGZ"}},"amount":"1000000000","price":{"n":1,"d":2},"flags":0,"ext":"v0"}}'
        val decoded = assertRoundTripBytes(
            base64 = "AAAAAQAAAAA/DDS/k60NmXHQTMyQ9wVRHIOKrZc0pKL7DXoD/H/omgAAAAAAAAAqAAAAAAAAAAFVU0QAAAAAAD8MNL+TrQ2ZcdBMzJD3BVEcg4qtlzSkovsNegP8f+iaAAAAADuaygAAAAABAAAAAgAAAAAAAAAA",
            decode = { ManageOfferSuccessResultOfferXdr.decode(it) },
            encode = { v, w -> v.encode(w) }
        )
        assertEquals(ManageOfferEffectXdr.MANAGE_OFFER_UPDATED, decoded.discriminant)
        assertTrue(decoded is ManageOfferSuccessResultOfferXdr.Offer)
    }
}
