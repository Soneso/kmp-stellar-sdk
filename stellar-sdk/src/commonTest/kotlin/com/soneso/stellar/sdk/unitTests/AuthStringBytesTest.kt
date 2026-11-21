//
//  AuthStringBytesTest.kt
//  Stellar SDK Kotlin Multiplatform
//
//  Copyright (c) 2026 Soneso. All rights reserved.
//

package com.soneso.stellar.sdk.unitTests

import com.soneso.stellar.sdk.Auth
import com.soneso.stellar.sdk.KeyPair
import com.soneso.stellar.sdk.Network
import com.soneso.stellar.sdk.Util
import com.soneso.stellar.sdk.xdr.HashIDPreimageXdr
import com.soneso.stellar.sdk.xdr.XdrWriter
import kotlinx.coroutines.test.runTest
import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Soroban authorization entries whose function name or `SCVal` argument holds bytes that are not
 * valid UTF-8 or that start with a byte order mark. Signing such an entry hashes the preimage of
 * exactly those bytes. Each entry has address credentials for the account of bytes 129 to 160,
 * nonce 1234567 and expiration ledger 1000, and calls the contract of bytes 65 to 96.
 */
class AuthStringBytesTest {

    /** A base64 entry, the base64 preimage its signature covers on testnet and the preimage hash. */
    private class Vector(val payloadHash: String, val entry: String, val preimage: String)

    private val vectors = listOf(
        // function name ff fe 20 61 62 63
        Vector(
            "d0a19f113ebb858c5459673806b3ec674fe893f60b0cb0fa731fc497c6d24d99",
            "AAAAAQAAAAAAAAAAgYKDhIWGh4iJiouMjY6PkJGSk5SVlpeYmZqbnJ2en6AAAAAAABLWhwAAA+gAAAABAAAAAAAAAAFBQkNE" +
                "RUZHSElKS0xNTk9QUVJTVFVWV1hZWltcXV5fYAAAAAb//iBhYmMAAAAAAAAAAAAA",
            "AAAACc7gMC1ZhE0yvcqRXIID3USzP7t+3BkFHqN6vt8o7NRyAAAAAAAS1ocAAAPoAAAAAAAAAAFBQkNERUZHSElKS0xNTk9Q" +
                "UVJTVFVWV1hZWltcXV5fYAAAAAb//iBhYmMAAAAAAAAAAAAA"
        ),
        // function name ef bb bf 61
        Vector(
            "b7e14047c0a7289fcd94da522ddaab04acd063db75e3b4a5189e352ba0ec84a6",
            "AAAAAQAAAAAAAAAAgYKDhIWGh4iJiouMjY6PkJGSk5SVlpeYmZqbnJ2en6AAAAAAABLWhwAAA+gAAAABAAAAAAAAAAFBQkNE" +
                "RUZHSElKS0xNTk9QUVJTVFVWV1hZWltcXV5fYAAAAATvu79hAAAAAAAAAAA=",
            "AAAACc7gMC1ZhE0yvcqRXIID3USzP7t+3BkFHqN6vt8o7NRyAAAAAAAS1ocAAAPoAAAAAAAAAAFBQkNERUZHSElKS0xNTk9Q" +
                "UVJTVFVWV1hZWltcXV5fYAAAAATvu79hAAAAAAAAAAA="
        ),
        // transfer with an SCV_STRING argument ff fe 20 61 62 63
        Vector(
            "85a9809802da0bc78328f29b3a6671f7b67732ae79f4e3697b0b56bdc4690c33",
            "AAAAAQAAAAAAAAAAgYKDhIWGh4iJiouMjY6PkJGSk5SVlpeYmZqbnJ2en6AAAAAAABLWhwAAA+gAAAABAAAAAAAAAAFBQkNE" +
                "RUZHSElKS0xNTk9QUVJTVFVWV1hZWltcXV5fYAAAAAh0cmFuc2ZlcgAAAAEAAAAOAAAABv/+IGFiYwAAAAAAAA==",
            "AAAACc7gMC1ZhE0yvcqRXIID3USzP7t+3BkFHqN6vt8o7NRyAAAAAAAS1ocAAAPoAAAAAAAAAAFBQkNERUZHSElKS0xNTk9Q" +
                "UVJTVFVWV1hZWltcXV5fYAAAAAh0cmFuc2ZlcgAAAAEAAAAOAAAABv/+IGFiYwAAAAAAAA=="
        ),
        // transfer with an SCV_SYMBOL argument ff fe 20 61 62 63
        Vector(
            "2bb12e4f019acfc3557c522e30ebe9f8028329f937e63b78eeb85a52d5c4285f",
            "AAAAAQAAAAAAAAAAgYKDhIWGh4iJiouMjY6PkJGSk5SVlpeYmZqbnJ2en6AAAAAAABLWhwAAA+gAAAABAAAAAAAAAAFBQkNE" +
                "RUZHSElKS0xNTk9QUVJTVFVWV1hZWltcXV5fYAAAAAh0cmFuc2ZlcgAAAAEAAAAPAAAABv/+IGFiYwAAAAAAAA==",
            "AAAACc7gMC1ZhE0yvcqRXIID3USzP7t+3BkFHqN6vt8o7NRyAAAAAAAS1ocAAAPoAAAAAAAAAAFBQkNERUZHSElKS0xNTk9Q" +
                "UVJTVFVWV1hZWltcXV5fYAAAAAh0cmFuc2ZlcgAAAAEAAAAPAAAABv/+IGFiYwAAAAAAAA=="
        ),
        // transfer with an SCV_STRING argument ef bb bf 61
        Vector(
            "f2319301b2ced3a8dcf2dc6d044ad8f1949e5ee3233aa5417b8d2e646636c3a5",
            "AAAAAQAAAAAAAAAAgYKDhIWGh4iJiouMjY6PkJGSk5SVlpeYmZqbnJ2en6AAAAAAABLWhwAAA+gAAAABAAAAAAAAAAFBQkNE" +
                "RUZHSElKS0xNTk9QUVJTVFVWV1hZWltcXV5fYAAAAAh0cmFuc2ZlcgAAAAEAAAAOAAAABO+7v2EAAAAA",
            "AAAACc7gMC1ZhE0yvcqRXIID3USzP7t+3BkFHqN6vt8o7NRyAAAAAAAS1ocAAAPoAAAAAAAAAAFBQkNERUZHSElKS0xNTk9Q" +
                "UVJTVFVWV1hZWltcXV5fYAAAAAh0cmFuc2ZlcgAAAAEAAAAOAAAABO+7v2EAAAAA"
        )
    )

    private fun bytesOf(encode: (XdrWriter) -> Unit): ByteArray = XdrWriter().also(encode).toByteArray()

    @Test
    fun signingAnEntryHashesThePreimageOfItsExactBytes() = runTest {
        val keyPair = KeyPair.fromSecretSeed(ByteArray(32) { (it + 3).toByte() })
        for (vector in vectors) {
            var signedPreimage: HashIDPreimageXdr? = null
            val signer = Auth.Signer { preimage ->
                signedPreimage = preimage
                Auth.Signature(keyPair.getAccountId(), keyPair.sign(Util.hash(bytesOf { preimage.encode(it) })))
            }

            val signed = Auth.authorizeEntry(vector.entry, signer, 1000L, Network.TESTNET)

            val preimageBytes = bytesOf { assertNotNull(signedPreimage).encode(it) }
            assertEquals(vector.preimage, Base64.encode(preimageBytes))
            assertEquals(vector.payloadHash, Util.bytesToHex(Util.hash(preimageBytes)))
            // The preimage ends with the invocation after its type, network id, nonce and expiration.
            val invocation = preimageBytes.copyOfRange(4 + 32 + 8 + 4, preimageBytes.size)
            assertContentEquals(invocation, bytesOf { signed.rootInvocation.encode(it) })
        }
    }
}
