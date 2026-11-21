//
//  TransactionStringBytesTest.kt
//  Stellar SDK Kotlin Multiplatform
//
//  Copyright (c) 2026 Soneso. All rights reserved.
//

package com.soneso.stellar.sdk.unitTests

import com.soneso.stellar.sdk.AbstractTransaction
import com.soneso.stellar.sdk.FeeBumpTransaction
import com.soneso.stellar.sdk.KeyPair
import com.soneso.stellar.sdk.MemoText
import com.soneso.stellar.sdk.Network
import com.soneso.stellar.sdk.Transaction
import com.soneso.stellar.sdk.Util
import com.soneso.stellar.sdk.xdr.FeeBumpTransactionInnerTxXdr
import com.soneso.stellar.sdk.xdr.TransactionEnvelopeXdr
import com.soneso.stellar.sdk.xdr.XdrReader
import com.soneso.stellar.sdk.xdr.XdrWriter
import kotlinx.coroutines.test.runTest
import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Transaction envelopes whose memo text, `SCVal` string or symbol argument, or contract function
 * name holds bytes that are not valid UTF-8 or that start with byte order marks. Each envelope
 * decodes and encodes byte for byte, at the XDR layer and through the transaction import, and its
 * hash is the testnet hash of exactly those bytes.
 *
 * The envelopes use source bytes 1 to 32, destination bytes 33 to 64, contract bytes 65 to 96 and
 * fee source bytes 97 to 128, one payment of 1 XLM or one contract call, sequence number
 * 4294967297 and an all-zero signature. The last V1 vector is a transaction taken from testnet.
 */
class TransactionStringBytesTest {

    /** A base64 envelope, the testnet hash of its transaction and, for a fee bump, of the inner one. */
    private class Vector(val hash: String, val envelope: String, val innerHash: String? = null)

    private val network = Network.TESTNET

    private val v1Vectors = listOf(
        // memo text ff fe 20 61 62 63
        Vector(
            "afd8d9d1020027a501b4e3548523f60fdfc093b163a1a77a801514342bf9d1d9",
            "AAAAAgAAAAABAgMEBQYHCAkKCwwNDg8QERITFBUWFxgZGhscHR4fIAAAAGQAAAABAAAAAQAAAAAAAAABAAAABv/+IGFiYwAA" +
                "AAAAAQAAAAAAAAABAAAAACEiIyQlJicoKSorLC0uLzAxMjM0NTY3ODk6Ozw9Pj9AAAAAAAAAAAAAmJaAAAAAAAAAAAEAAAAA" +
                "AAAAQAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="
        ),
        // memo text ef bb bf 61
        Vector(
            "d7cce4ebcf4c7aea1a94d145eb72eb1f6c31dcecf51e390aa43de916cdb00cfd",
            "AAAAAgAAAAABAgMEBQYHCAkKCwwNDg8QERITFBUWFxgZGhscHR4fIAAAAGQAAAABAAAAAQAAAAAAAAABAAAABO+7v2EAAAAB" +
                "AAAAAAAAAAEAAAAAISIjJCUmJygpKissLS4vMDEyMzQ1Njc4OTo7PD0+P0AAAAAAAAAAAACYloAAAAAAAAAAAQAAAAAAAABA" +
                "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=="
        ),
        // memo text ef bb bf ef bb bf 61
        Vector(
            "2a31d6472d0ee51764dd708a52fe149a83d11bc10e5ce8b9bfaa24158a90be22",
            "AAAAAgAAAAABAgMEBQYHCAkKCwwNDg8QERITFBUWFxgZGhscHR4fIAAAAGQAAAABAAAAAQAAAAAAAAABAAAAB++7v++7v2EA" +
                "AAAAAQAAAAAAAAABAAAAACEiIyQlJicoKSorLC0uLzAxMjM0NTY3ODk6Ozw9Pj9AAAAAAAAAAAAAmJaAAAAAAAAAAAEAAAAA" +
                "AAAAQAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="
        ),
        // memo text of 28 bytes: 27 x 61, then c3 (a two-byte sequence cut after its first byte)
        Vector(
            "d99b04f76b7b09b523d59db35089e6256956d8fa89d4c2fdf00005a72ac182ea",
            "AAAAAgAAAAABAgMEBQYHCAkKCwwNDg8QERITFBUWFxgZGhscHR4fIAAAAGQAAAABAAAAAQAAAAAAAAABAAAAHGFhYWFhYWFh" +
                "YWFhYWFhYWFhYWFhYWFhYWFhYcMAAAABAAAAAAAAAAEAAAAAISIjJCUmJygpKissLS4vMDEyMzQ1Njc4OTo7PD0+P0AAAAAA" +
                "AAAAAACYloAAAAAAAAAAAQAAAAAAAABAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA" +
                "AAAAAAAAAAAAAAAAAAAAAA=="
        ),
        // memo text of 28 bytes: 26 x 61, then e2 82 (a three-byte sequence cut after two bytes)
        Vector(
            "f2b1f6b938c090f68c163a71a0db74cb116ed2c7e28ef7a2e348077986224c9c",
            "AAAAAgAAAAABAgMEBQYHCAkKCwwNDg8QERITFBUWFxgZGhscHR4fIAAAAGQAAAABAAAAAQAAAAAAAAABAAAAHGFhYWFhYWFh" +
                "YWFhYWFhYWFhYWFhYWFhYWFh4oIAAAABAAAAAAAAAAEAAAAAISIjJCUmJygpKissLS4vMDEyMzQ1Njc4OTo7PD0+P0AAAAAA" +
                "AAAAAACYloAAAAAAAAAAAQAAAAAAAABAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA" +
                "AAAAAAAAAAAAAAAAAAAAAA=="
        ),
        // contract call f with an SCV_STRING argument ff fe 20 61 62 63
        Vector(
            "d2271e8683ce85e52efbd45c6a2dbb1dac3ca61b16f4f36d8823b56486621b8c",
            "AAAAAgAAAAABAgMEBQYHCAkKCwwNDg8QERITFBUWFxgZGhscHR4fIAAAAGQAAAABAAAAAQAAAAAAAAAAAAAAAQAAAAAAAAAY" +
                "AAAAAAAAAAFBQkNERUZHSElKS0xNTk9QUVJTVFVWV1hZWltcXV5fYAAAAAFmAAAAAAAAAQAAAA4AAAAG//4gYWJjAAAAAAAA" +
                "AAAAAAAAAAEAAAAAAAAAQAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA" +
                "AAAAAAAAAAA="
        ),
        // contract call f with an SCV_STRING argument ef bb bf 61
        Vector(
            "600608e5230c782b6385972888e34013ee0519031d5c0798cd32a55b9c164284",
            "AAAAAgAAAAABAgMEBQYHCAkKCwwNDg8QERITFBUWFxgZGhscHR4fIAAAAGQAAAABAAAAAQAAAAAAAAAAAAAAAQAAAAAAAAAY" +
                "AAAAAAAAAAFBQkNERUZHSElKS0xNTk9QUVJTVFVWV1hZWltcXV5fYAAAAAFmAAAAAAAAAQAAAA4AAAAE77u/YQAAAAAAAAAA" +
                "AAAAAQAAAAAAAABAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA" +
                "AAAAAA=="
        ),
        // contract call f with an SCV_SYMBOL argument ff fe 20 61 62 63
        Vector(
            "fe876329b5ab7aac3de3d7fbfee88cfd7bbd148fdb4c26bafd2e0bd483ea4983",
            "AAAAAgAAAAABAgMEBQYHCAkKCwwNDg8QERITFBUWFxgZGhscHR4fIAAAAGQAAAABAAAAAQAAAAAAAAAAAAAAAQAAAAAAAAAY" +
                "AAAAAAAAAAFBQkNERUZHSElKS0xNTk9QUVJTVFVWV1hZWltcXV5fYAAAAAFmAAAAAAAAAQAAAA8AAAAG//4gYWJjAAAAAAAA" +
                "AAAAAAAAAAEAAAAAAAAAQAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA" +
                "AAAAAAAAAAA="
        ),
        // contract call f with an SCV_SYMBOL argument ef bb bf 61
        Vector(
            "9db200d7e94f46e9b46a3b09b8e99363939ea8fff5d8f6fd988ce8f949f0eb0c",
            "AAAAAgAAAAABAgMEBQYHCAkKCwwNDg8QERITFBUWFxgZGhscHR4fIAAAAGQAAAABAAAAAQAAAAAAAAAAAAAAAQAAAAAAAAAY" +
                "AAAAAAAAAAFBQkNERUZHSElKS0xNTk9QUVJTVFVWV1hZWltcXV5fYAAAAAFmAAAAAAAAAQAAAA8AAAAE77u/YQAAAAAAAAAA" +
                "AAAAAQAAAAAAAABAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA" +
                "AAAAAA=="
        ),
        // contract call with the function name ff fe 20 61 62 63
        Vector(
            "649e93a7e8a22e665be43f4850951f53ea8104949f60f121889d77a206976a4b",
            "AAAAAgAAAAABAgMEBQYHCAkKCwwNDg8QERITFBUWFxgZGhscHR4fIAAAAGQAAAABAAAAAQAAAAAAAAAAAAAAAQAAAAAAAAAY" +
                "AAAAAAAAAAFBQkNERUZHSElKS0xNTk9QUVJTVFVWV1hZWltcXV5fYAAAAAb//iBhYmMAAAAAAAAAAAAAAAAAAAAAAAEAAAAA" +
                "AAAAQAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="
        ),
        // contract call with the function name ef bb bf 61
        Vector(
            "785e5a36182816ff9a2131446efd3b85e16cce555918d53a1d009ad00735d778",
            "AAAAAgAAAAABAgMEBQYHCAkKCwwNDg8QERITFBUWFxgZGhscHR4fIAAAAGQAAAABAAAAAQAAAAAAAAAAAAAAAQAAAAAAAAAY" +
                "AAAAAAAAAAFBQkNERUZHSElKS0xNTk9QUVJTVFVWV1hZWltcXV5fYAAAAATvu79hAAAAAAAAAAAAAAAAAAAAAQAAAAAAAABA" +
                "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=="
        ),
        // a testnet payment with the memo text ff fe 20 61 62 63
        Vector(
            "b7fb286bcb380d0f3b9ace77c8b513cc348e89805ddf16a076f2085ee8dc0816",
            "AAAAAgAAAAB9dKGOpM7ReVg9puIJUE41Q4ixcNzQmY1RuqqEPX5PMAAAA+gAWPV9AAAAAQAAAAEAAAAAAAAAAAAAAAAAAAAA" +
                "AAAAAQAAAAb//iBhYmMAAAAAAAEAAAAAAAAAAQAAAACkQeBd9iE/juL6rqj0VVFw2v8aYlIFPrDcDVEpm7RX1AAAAAAAAAAA" +
                "AJiWgAAAAAAAAAABPX5PMAAAAEAqERgvj65qzoHGjnHgWnfheLjxKwOqWTLzvu/v3OwwOi2Qkejhk2V7ODCcq9/yVZ0M6rvD" +
                "cOIIUrlecenAIGYP"
        )
    )

    /** V0 envelopes; the network hashes a V0 transaction as `sha256(id || 2 || 0 || v0.tx)`. */
    private val v0Vectors = listOf(
        // memo text ff fe 20 61 62 63
        Vector(
            "afd8d9d1020027a501b4e3548523f60fdfc093b163a1a77a801514342bf9d1d9",
            "AAAAAAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8gAAAAZAAAAAEAAAABAAAAAAAAAAEAAAAG//4gYWJjAAAAAAAB" +
                "AAAAAAAAAAEAAAAAISIjJCUmJygpKissLS4vMDEyMzQ1Njc4OTo7PD0+P0AAAAAAAAAAAACYloAAAAAAAAAAAQAAAAAAAABA" +
                "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=="
        ),
        // memo text ef bb bf 61
        Vector(
            "d7cce4ebcf4c7aea1a94d145eb72eb1f6c31dcecf51e390aa43de916cdb00cfd",
            "AAAAAAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8gAAAAZAAAAAEAAAABAAAAAAAAAAEAAAAE77u/YQAAAAEAAAAA" +
                "AAAAAQAAAAAhIiMkJSYnKCkqKywtLi8wMTIzNDU2Nzg5Ojs8PT4/QAAAAAAAAAAAAJiWgAAAAAAAAAABAAAAAAAAAEAAAAAA" +
                "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
        ),
        // memo text ef bb bf ef bb bf 61
        Vector(
            "2a31d6472d0ee51764dd708a52fe149a83d11bc10e5ce8b9bfaa24158a90be22",
            "AAAAAAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8gAAAAZAAAAAEAAAABAAAAAAAAAAEAAAAH77u/77u/YQAAAAAB" +
                "AAAAAAAAAAEAAAAAISIjJCUmJygpKissLS4vMDEyMzQ1Njc4OTo7PD0+P0AAAAAAAAAAAACYloAAAAAAAAAAAQAAAAAAAABA" +
                "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=="
        ),
        // two payments, fee 200, no memo
        Vector(
            "a8da8515801f02678a926092014c2cdc5210aa5907da2dfcda6c0e69bb4542c9",
            "AAAAAAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8gAAAAyAAAAAEAAAABAAAAAAAAAAAAAAACAAAAAAAAAAEAAAAA" +
                "ISIjJCUmJygpKissLS4vMDEyMzQ1Njc4OTo7PD0+P0AAAAAAAAAAAACYloAAAAAAAAAAAQAAAAAhIiMkJSYnKCkqKywtLi8w" +
                "MTIzNDU2Nzg5Ojs8PT4/QAAAAAAAAAAAATEtAAAAAAAAAAABAAAAAAAAAEAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA" +
                "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
        ),
        // two payments, fee 200, memo text ff fe 20 61 62 63
        Vector(
            "f5bb99c62173175876a0f612a712addc62dc5193fafa55553fb73cb364637abd",
            "AAAAAAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8gAAAAyAAAAAEAAAABAAAAAAAAAAEAAAAG//4gYWJjAAAAAAAC" +
                "AAAAAAAAAAEAAAAAISIjJCUmJygpKissLS4vMDEyMzQ1Njc4OTo7PD0+P0AAAAAAAAAAAACYloAAAAAAAAAAAQAAAAAhIiMk" +
                "JSYnKCkqKywtLi8wMTIzNDU2Nzg5Ojs8PT4/QAAAAAAAAAAAATEtAAAAAAAAAAABAAAAAAAAAEAAAAAAAAAAAAAAAAAAAAAA" +
                "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
        ),
        // time bounds 0 to 1767225600, memo text ff fe 20 61 62 63
        Vector(
            "2da74931f32d2c36c3420a401105d1ac797a81a103d75fefba1155b6fef1b4b7",
            "AAAAAAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8gAAAAZAAAAAEAAAABAAAAAQAAAAAAAAAAAAAAAGlVuQAAAAAB" +
                "AAAABv/+IGFiYwAAAAAAAQAAAAAAAAABAAAAACEiIyQlJicoKSorLC0uLzAxMjM0NTY3ODk6Ozw9Pj9AAAAAAAAAAAAAmJaA" +
                "AAAAAAAAAAEAAAAAAAAAQAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA" +
                "AAAAAAAAAAA="
        ),
        // two payments, fee 200, time bounds 0 to 1767225600, memo text c3 a9 31
        Vector(
            "6e3638fb3c7cb6581e1d8cf6169ce03f4c238d535d0b9cea44984e43fe5a1201",
            "AAAAAAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8gAAAAyAAAAAEAAAABAAAAAQAAAAAAAAAAAAAAAGlVuQAAAAAB" +
                "AAAAA8OpMQAAAAACAAAAAAAAAAEAAAAAISIjJCUmJygpKissLS4vMDEyMzQ1Njc4OTo7PD0+P0AAAAAAAAAAAACYloAAAAAA" +
                "AAAAAQAAAAAhIiMkJSYnKCkqKywtLi8wMTIzNDU2Nzg5Ojs8PT4/QAAAAAAAAAAAATEtAAAAAAAAAAABAAAAAAAAAEAAAAAA" +
                "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
        )
    )

    /** Fee bumps with fee 400 around the first three V1 vectors. */
    private val feeBumpVectors = listOf(
        // inner memo text ff fe 20 61 62 63
        Vector(
            "d8db2bb148d0a5b11885818541b9abe4bc44e3ad43de5e64a3b74bea2c748537",
            "AAAABQAAAABhYmNkZWZnaGlqa2xtbm9wcXJzdHV2d3h5ent8fX5/gAAAAAAAAAGQAAAAAgAAAAABAgMEBQYHCAkKCwwNDg8Q" +
                "ERITFBUWFxgZGhscHR4fIAAAAGQAAAABAAAAAQAAAAAAAAABAAAABv/+IGFiYwAAAAAAAQAAAAAAAAABAAAAACEiIyQlJico" +
                "KSorLC0uLzAxMjM0NTY3ODk6Ozw9Pj9AAAAAAAAAAAAAmJaAAAAAAAAAAAEAAAAAAAAAQAAAAAAAAAAAAAAAAAAAAAAAAAAA" +
                "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAQAAAAAAAABAAAAAAAAAAAAAAAAA" +
                "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA==",
            innerHash = "afd8d9d1020027a501b4e3548523f60fdfc093b163a1a77a801514342bf9d1d9"
        ),
        // inner memo text ef bb bf 61
        Vector(
            "d50d964ee0e4ded79a0c8a527438daac87685040c8456c603cc709d5e0877c0e",
            "AAAABQAAAABhYmNkZWZnaGlqa2xtbm9wcXJzdHV2d3h5ent8fX5/gAAAAAAAAAGQAAAAAgAAAAABAgMEBQYHCAkKCwwNDg8Q" +
                "ERITFBUWFxgZGhscHR4fIAAAAGQAAAABAAAAAQAAAAAAAAABAAAABO+7v2EAAAABAAAAAAAAAAEAAAAAISIjJCUmJygpKiss" +
                "LS4vMDEyMzQ1Njc4OTo7PD0+P0AAAAAAAAAAAACYloAAAAAAAAAAAQAAAAAAAABAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA" +
                "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAABAAAAAAAAAEAAAAAAAAAAAAAAAAAAAAAA" +
                "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA",
            innerHash = "d7cce4ebcf4c7aea1a94d145eb72eb1f6c31dcecf51e390aa43de916cdb00cfd"
        ),
        // inner memo text ef bb bf ef bb bf 61
        Vector(
            "34e6f71d40bf353d481cd37a493d78a4153eb7d163490093cb120ae6b56892c7",
            "AAAABQAAAABhYmNkZWZnaGlqa2xtbm9wcXJzdHV2d3h5ent8fX5/gAAAAAAAAAGQAAAAAgAAAAABAgMEBQYHCAkKCwwNDg8Q" +
                "ERITFBUWFxgZGhscHR4fIAAAAGQAAAABAAAAAQAAAAAAAAABAAAAB++7v++7v2EAAAAAAQAAAAAAAAABAAAAACEiIyQlJico" +
                "KSorLC0uLzAxMjM0NTY3ODk6Ozw9Pj9AAAAAAAAAAAAAmJaAAAAAAAAAAAEAAAAAAAAAQAAAAAAAAAAAAAAAAAAAAAAAAAAA" +
                "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAQAAAAAAAABAAAAAAAAAAAAAAAAA" +
                "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA==",
            innerHash = "2a31d6472d0ee51764dd708a52fe149a83d11bc10e5ce8b9bfaa24158a90be22"
        )
    )

    private fun decode(envelope: String): TransactionEnvelopeXdr =
        TransactionEnvelopeXdr.decode(XdrReader(Base64.decode(envelope)))

    private fun bytesOf(encode: (XdrWriter) -> Unit): ByteArray = XdrWriter().also(encode).toByteArray()

    /** The testnet hash of [body] behind the envelope type [tag], as the network computes it. */
    private suspend fun networkHash(tag: Int, body: ByteArray): String =
        Util.bytesToHex(Util.hash(network.networkId() + byteArrayOf(0, 0, 0, tag.toByte()) + body))

    @Test
    fun v1EnvelopesKeepTheirBytesAndHashAtTheXdrLayer() = runTest {
        for (vector in v1Vectors) {
            val envelope = assertIs<TransactionEnvelopeXdr.V1>(decode(vector.envelope))
            assertEquals(vector.envelope, Base64.encode(bytesOf { envelope.encode(it) }))
            assertEquals(vector.hash, networkHash(2, bytesOf { envelope.value.tx.encode(it) }))
        }
    }

    @Test
    fun v1EnvelopesImportByteForByteWithTheirHash() = runTest {
        for (vector in v1Vectors) {
            val transaction = assertIs<Transaction>(AbstractTransaction.fromEnvelopeXdr(vector.envelope, network))
            assertEquals(vector.envelope, transaction.toEnvelopeXdrBase64())
            assertEquals(vector.hash, transaction.hashHex())
        }
    }

    @Test
    fun anImportedMemoHoldsTheMemoBytes() = runTest {
        val expected = mapOf(
            0 to "fffe20616263",
            1 to "efbbbf61",
            2 to "efbbbfefbbbf61",
            3 to "616161616161616161616161616161616161616161616161616161c3",
            4 to "6161616161616161616161616161616161616161616161616161e282"
        )
        for ((index, memoHex) in expected) {
            val transaction = Transaction.fromEnvelopeXdr(v1Vectors[index].envelope, network)
            assertEquals(memoHex, Util.bytesToHex(assertIs<MemoText>(transaction.memo).bytes))
        }
    }

    @Test
    fun v0EnvelopesKeepTheirBytesAndHashAtTheXdrLayer() = runTest {
        for (vector in v0Vectors) {
            val envelope = assertIs<TransactionEnvelopeXdr.V0>(decode(vector.envelope))
            assertEquals(vector.envelope, Base64.encode(bytesOf { envelope.encode(it) }))
            val v0Tx = bytesOf { envelope.value.tx.encode(it) }
            assertEquals(vector.hash, networkHash(2, byteArrayOf(0, 0, 0, 0) + v0Tx))
        }
    }

    @Test
    fun v0EnvelopesImportByteForByteWithTheirHashAndFee() = runTest {
        val expectedFees = listOf(100L, 100L, 100L, 200L, 200L, 100L, 200L)
        for ((vector, fee) in v0Vectors.zip(expectedFees)) {
            val transaction = assertIs<Transaction>(AbstractTransaction.fromEnvelopeXdr(vector.envelope, network))
            assertEquals(vector.envelope, transaction.toEnvelopeXdrBase64())
            assertEquals(vector.hash, transaction.hashHex())
            assertEquals(fee, transaction.fee)
        }
    }

    @Test
    fun feeBumpEnvelopesKeepTheirBytesAndBothHashesAtTheXdrLayer() = runTest {
        for (vector in feeBumpVectors) {
            val envelope = assertIs<TransactionEnvelopeXdr.FeeBump>(decode(vector.envelope))
            assertEquals(vector.envelope, Base64.encode(bytesOf { envelope.encode(it) }))
            val feeBumpTx = envelope.value.tx
            assertEquals(vector.hash, networkHash(5, bytesOf { feeBumpTx.encode(it) }))
            val inner = assertIs<FeeBumpTransactionInnerTxXdr.V1>(feeBumpTx.innerTx)
            assertEquals(vector.innerHash, networkHash(2, bytesOf { inner.value.tx.encode(it) }))
        }
    }

    @Test
    fun feeBumpEnvelopesImportByteForByteWithBothHashes() = runTest {
        for (vector in feeBumpVectors) {
            val imported = listOf(
                FeeBumpTransaction.fromEnvelopeXdrBase64(vector.envelope, network),
                assertIs<FeeBumpTransaction>(AbstractTransaction.fromEnvelopeXdr(vector.envelope, network))
            )
            for (feeBump in imported) {
                assertEquals(vector.envelope, feeBump.toEnvelopeXdrBase64())
                assertEquals(vector.hash, feeBump.hashHex())
                assertEquals(vector.innerHash, feeBump.innerTransaction.hashHex())
                assertEquals(400L, feeBump.fee)
            }
        }
    }

    @Test
    fun aSignatureMadeAfterTheImportVerifiesAgainstTheNetworkHash() = runTest {
        val signer = KeyPair.fromSecretSeed(ByteArray(32) { (it + 7).toByte() })
        val imported = listOf(v1Vectors[0], v0Vectors[4], feeBumpVectors[0]).map {
            it.hash to AbstractTransaction.fromEnvelopeXdr(it.envelope, network)
        }
        for ((hash, transaction) in imported) {
            transaction.sign(signer)
            assertTrue(signer.verify(Util.hexToBytes(hash), transaction.signatures.last().signature), hash)
        }
    }
}
