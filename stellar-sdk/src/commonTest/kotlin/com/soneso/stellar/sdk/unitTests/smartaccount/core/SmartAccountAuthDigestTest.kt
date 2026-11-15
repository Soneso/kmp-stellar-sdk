//
//  SmartAccountAuthDigestTest.kt
//  Stellar SDK Kotlin Multiplatform
//
//  Copyright (c) 2026 Soneso. All rights reserved.
//

package com.soneso.stellar.sdk.unitTests.smartaccount.core

import com.soneso.stellar.sdk.Address
import com.soneso.stellar.sdk.KeyPair
import com.soneso.stellar.sdk.Util
import com.soneso.stellar.sdk.crypto.getSha256Crypto
import com.soneso.stellar.sdk.scval.Scv
import com.soneso.stellar.sdk.smartaccount.core.AuthDigestScheme
import com.soneso.stellar.sdk.smartaccount.core.EntryAuthDigest
import com.soneso.stellar.sdk.smartaccount.core.SmartAccountAuth
import com.soneso.stellar.sdk.smartaccount.core.TransactionException
import com.soneso.stellar.sdk.unitTests.smartaccount.IndependentAuthDigest
import com.soneso.stellar.sdk.util.TestResourceUtil
import com.soneso.stellar.sdk.xdr.SCValXdr
import com.soneso.stellar.sdk.xdr.SorobanAuthorizationEntryXdr
import com.soneso.stellar.sdk.xdr.SorobanCredentialsXdr
import com.soneso.stellar.sdk.xdr.XdrWriter
import com.soneso.stellar.sdk.xdr.fromXdrBase64
import com.soneso.stellar.sdk.xdr.toXdrBase64
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long

/**
 * The two auth digest schemes of OpenZeppelin smart accounts: the stellar-accounts 0.8.0
 * `AuthDigestPreimage` and the 0.7 concatenation, against `auth_digest_cross_sdk_fixture.json`.
 *
 * The fixture's preimage vectors are the smart-account-kit vectors, generated with
 * `AuthDigestPreimage { .. }.to_xdr()` and `.digest()` from OpenZeppelin/stellar-contracts@6eb047d5,
 * and the digest the `auth_digest` view of a 0.8.0 wallet on testnet returned. Its entry values
 * were computed outside the SDK with the Stellar CLI 28.1.0 (stellar-xdr 28.0.0)
 * `stellar xdr encode`, SHA-256 and an Ed25519 library: the signature payload from the
 * `HashIdPreimage::SorobanAuthorizationWithAddress` of the entry, the preimage digest from the
 * `ScVal` map of the three fields, the legacy digest from the payload followed by the XDR of the
 * rule id `Vec`, and one signature over each digest by the fixture key of
 * `ed25519_cross_sdk_fixture.json`.
 */
class SmartAccountAuthDigestTest {

    private val kitAccount = "CC7EKIHQP3TN4CARQDND6CEOY2UXLWWC2X5GHTD5NLAT7BG5GPZIOM3F"

    private val fixture: JsonObject = Json.parseToJsonElement(
        TestResourceUtil.readFixtureBytes("auth_digest_cross_sdk_fixture.json").decodeToString()
    ).jsonObject
    private val entryFixture: JsonObject = fixture.getValue("entry").jsonObject
    private val preimageV1: JsonObject = entryFixture.getValue("preimageV1").jsonObject
    private val legacyConcat: JsonObject = entryFixture.getValue("legacyConcat").jsonObject

    private val entryExpiration = entryFixture.getValue("signatureExpirationLedger").jsonPrimitive.long.toUInt()
    private val entryRuleIds = entryFixture.getValue("contextRuleIds").ruleIds()
    private val entryPassphrase = entryFixture.string("networkPassphrase")
    private val expectedEntryPayloadHex = entryFixture.string("signaturePayloadHex")
    private val expectedEntryPreimageDigestHex = preimageV1.string("authDigestHex")
    private val expectedEntryLegacyDigestHex = legacyConcat.string("authDigestHex")

    /** The fixture entry: a native asset transfer authorized with ADDRESS_V2 credentials. */
    private fun entryFixture(): SorobanAuthorizationEntryXdr =
        SorobanAuthorizationEntryXdr.fromXdrBase64(entryFixture.string("entryXdrBase64"))

    private fun JsonObject.string(key: String): String = getValue(key).jsonPrimitive.content

    private fun JsonElement.ruleIds(): List<UInt> = jsonArray.map { it.jsonPrimitive.long.toUInt() }

    private fun JsonObject.delegatedArgs(): List<String> =
        getValue("delegatedAuthArgsXdrBase64").jsonArray.map { it.jsonPrimitive.content }

    private fun xdrHex(value: SCValXdr): String {
        val writer = XdrWriter()
        value.encode(writer)
        return Util.bytesToHex(writer.toByteArray())
    }

    /** The fixture key's signature over [digest] verifies, so [digest] is the digest the fixture signed. */
    private suspend fun assertFixtureSignatureVerifies(scheme: JsonObject, digest: ByteArray) {
        val publicKey = KeyPair.fromPublicKey(Util.hexToBytes(entryFixture.string("ed25519PublicKeyHex")))
        assertTrue(
            publicKey.verify(digest, Util.hexToBytes(scheme.string("ed25519SignatureHex"))),
            "The fixture signature must verify over ${Util.bytesToHex(digest)}"
        )
    }

    // MARK: - Preimage builder against the kit vectors and the live view

    @Test
    fun buildAuthDigestPreimage_fixtureVectors_matchContractEncodingAndDigest() = runTest {
        val vectors = fixture.getValue("preimageVectors").jsonArray.map { it.jsonObject }
        assertEquals(4, vectors.size, "Three kit vectors and the live auth_digest reading")
        for (vector in vectors) {
            val name = vector.string("name")
            val preimage = SmartAccountAuth.buildAuthDigestPreimage(
                Address(vector.string("account")).toSCAddress(),
                Util.hexToBytes(vector.string("signaturePayloadHex")),
                vector.getValue("contextRuleIds").ruleIds()
            )
            assertEquals(vector.string("preimageXdrHex"), xdrHex(preimage), "Preimage XDR for $name")
            assertEquals(
                vector.string("authDigestHex"),
                Util.bytesToHex(getSha256Crypto().hash(Util.hexToBytes(xdrHex(preimage)))),
                "Preimage digest for $name"
            )
        }
    }

    @Test
    fun buildAuthDigestPreimage_payloadNot32Bytes_throws() {
        for (size in listOf(0, 31, 33)) {
            val ex = assertFailsWith<IllegalArgumentException> {
                SmartAccountAuth.buildAuthDigestPreimage(Address(kitAccount).toSCAddress(), ByteArray(size), listOf(0u))
            }
            assertEquals("Signature payload must be 32 bytes, got $size", ex.message)
        }
    }

    // MARK: - computeEntryAuthDigest against the entry fixture

    @Test
    fun computeEntryAuthDigest_preimageScheme_matchesIndependentlyComputedValues() = runTest {
        val digest = SmartAccountAuth.computeEntryAuthDigest(
            entry = entryFixture(),
            expirationLedger = entryExpiration,
            networkPassphrase = entryPassphrase,
            contextRuleIds = entryRuleIds,
            scheme = AuthDigestScheme.PREIMAGE_V1
        )

        assertEquals(expectedEntryPayloadHex, Util.bytesToHex(digest.signaturePayload))
        assertEquals(expectedEntryPreimageDigestHex, Util.bytesToHex(digest.authDigest))
        assertEquals(
            preimageV1.delegatedArgs(),
            digest.delegatedAuthArgs.map { it.toXdrBase64() },
            "The delegated __check_auth argument is the preimage"
        )
        assertFixtureSignatureVerifies(preimageV1, digest.authDigest)
    }

    @Test
    fun computeEntryAuthDigest_legacyScheme_matchesIndependentlyComputedValues() = runTest {
        val digest = SmartAccountAuth.computeEntryAuthDigest(
            entry = entryFixture(),
            expirationLedger = entryExpiration,
            networkPassphrase = entryPassphrase,
            contextRuleIds = entryRuleIds,
            scheme = AuthDigestScheme.LEGACY_CONCAT
        )

        assertEquals(expectedEntryPayloadHex, Util.bytesToHex(digest.signaturePayload))
        assertEquals(expectedEntryLegacyDigestHex, Util.bytesToHex(digest.authDigest))
        assertEquals(
            legacyConcat.delegatedArgs(),
            digest.delegatedAuthArgs.map { it.toXdrBase64() },
            "The legacy delegated __check_auth argument is Bytes(auth_digest)"
        )
        assertFixtureSignatureVerifies(legacyConcat, digest.authDigest)
    }

    @Test
    fun computeEntryAuthDigest_preimageScheme_bindsTheCredentialAddress() = runTest {
        val entry = entryFixture()
        val otherAddress = "CDLZFC3SYJYDZT7K67VZ75HPJVIEUVNIXF47ZG2FB2RMQQVU2HHGCYSC"
        val otherAccount = Address(otherAddress).toSCAddress()
        val credentials = (entry.credentials as SorobanCredentialsXdr.AddressV2).value
        val otherEntry = entry.copy(
            credentials = SorobanCredentialsXdr.Address(credentials.copy(address = otherAccount))
        )

        val digest = SmartAccountAuth.computeEntryAuthDigest(
            otherEntry, entryExpiration, entryPassphrase, entryRuleIds, AuthDigestScheme.PREIMAGE_V1
        )

        val expectedPreimage = IndependentAuthDigest.preimage(otherAddress, digest.signaturePayload, entryRuleIds)
        assertEquals(listOf(xdrHex(expectedPreimage)), digest.delegatedAuthArgs.map { xdrHex(it) })
        assertContentEquals(
            IndependentAuthDigest.preimageDigest(otherAddress, digest.signaturePayload, entryRuleIds),
            digest.authDigest
        )
        assertNotEquals(expectedEntryPreimageDigestHex, Util.bytesToHex(digest.authDigest))
    }

    @Test
    fun computeEntryAuthDigest_sourceAccountCredentials_throwsSigningFailed() = runTest {
        val entry = entryFixture().copy(credentials = SorobanCredentialsXdr.Void)
        for (scheme in AuthDigestScheme.entries) {
            assertFailsWith<TransactionException.SigningFailed> {
                SmartAccountAuth.computeEntryAuthDigest(
                    entry, entryExpiration, entryPassphrase, entryRuleIds, scheme
                )
            }
        }
    }

    @Suppress("DEPRECATION")
    @Test
    fun buildAuthDigest_equalsTheLegacySchemeDigest() = runTest {
        val payload = Util.hexToBytes(expectedEntryPayloadHex)
        assertEquals(
            expectedEntryLegacyDigestHex,
            Util.bytesToHex(SmartAccountAuth.buildAuthDigest(payload, entryRuleIds))
        )
    }

    // MARK: - EntryAuthDigest value semantics

    @Test
    fun entryAuthDigest_equalsAndHashCode_compareByContent() {
        val first = EntryAuthDigest(ByteArray(32) { 1 }, ByteArray(32) { 2 }, listOf(Scv.toUint32(3u)))
        val same = EntryAuthDigest(ByteArray(32) { 1 }, ByteArray(32) { 2 }, listOf(Scv.toUint32(3u)))

        assertEquals(first, same)
        assertEquals(first.hashCode(), same.hashCode())
        assertTrue(first.equals(first))
        assertNotEquals<Any?>(first, null)
        assertNotEquals<Any>(first, "not a digest")
        assertNotEquals(first, same.copy(signaturePayload = ByteArray(32) { 9 }))
        assertNotEquals(first, same.copy(authDigest = ByteArray(32) { 9 }))
        assertNotEquals(first, same.copy(delegatedAuthArgs = emptyList()))

        // Byte-carrying arguments built apart compare by content.
        val withBytes = first.copy(delegatedAuthArgs = listOf(Scv.toBytes(ByteArray(32) { 4 })))
        val withSameBytes = first.copy(delegatedAuthArgs = listOf(Scv.toBytes(ByteArray(32) { 4 })))
        assertEquals(withBytes, withSameBytes)
        assertEquals(withBytes.hashCode(), withSameBytes.hashCode())
        assertNotEquals(withBytes, first.copy(delegatedAuthArgs = listOf(Scv.toBytes(ByteArray(32) { 5 }))))
        assertContentEquals(ByteArray(32) { 2 }, first.authDigest)
    }
}
