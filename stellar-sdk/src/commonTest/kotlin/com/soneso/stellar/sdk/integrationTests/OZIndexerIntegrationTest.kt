//
//  OZIndexerIntegrationTest.kt
//  Stellar SDK Kotlin Multiplatform
//
//  Copyright (c) 2026 Soneso. All rights reserved.
//

package com.soneso.stellar.sdk.integrationTests

import com.soneso.stellar.sdk.KeyPair
import com.soneso.stellar.sdk.Network
import com.soneso.stellar.sdk.StrKey
import com.soneso.stellar.sdk.Util
import com.soneso.stellar.sdk.smartaccount.core.IndexerException
import com.soneso.stellar.sdk.smartaccount.oz.OZIndexerClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/**
 * Live tests against the default testnet indexer (Mercury). They verify the endpoint is up
 * and speaks the wire format the SDK expects: the health probe, the stats route, and the
 * lookup routes with their response DTO shapes.
 *
 * Lookups use freshly generated identifiers (a random credential ID, a random keypair's
 * G-address, a random C-address), which are valid by construction and cannot collide with
 * indexed data, so the expected results are deterministic: well-formed empty responses for
 * the lookup routes and the typed failure for an unknown contract.
 */
class OZIndexerIntegrationTest {

    private fun client(): OZIndexerClient {
        val client = OZIndexerClient.forNetwork(Network.TESTNET.networkPassphrase)
        assertNotNull(client, "testnet must have a default indexer")
        return client
    }

    private fun ByteArray.toHex(): String =
        joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }

    @Test
    fun testDefaultIndexer_isHealthy() = runTest(timeout = 60.seconds) {
        withContext(Dispatchers.Default) {
            val client = client()
            try {
                assertTrue(client.isHealthy(), "the default testnet indexer must report healthy")
            } finally {
                client.close()
            }
        }
    }

    @Test
    fun testStats_returnsLiveData() = runTest(timeout = 60.seconds) {
        withContext(Dispatchers.Default) {
            val client = client()
            try {
                val stats = client.getStats().stats
                assertTrue(stats.totalEvents > 0, "the indexer must have processed events, got: ${stats.totalEvents}")
                assertTrue(stats.uniqueContracts > 0, "the indexer must have indexed contracts, got: ${stats.uniqueContracts}")
            } finally {
                client.close()
            }
        }
    }

    @Test
    fun testLookupByUnknownCredentialId_returnsWellFormedEmptyResult() = runTest(timeout = 60.seconds) {
        withContext(Dispatchers.Default) {
            val client = client()
            try {
                // The client takes a base64url credential ID and queries the API by its hex form,
                // which the response echoes.
                val credentialBytes = Random.nextBytes(16)
                val response = client.lookupByCredentialId(Util.base64urlEncode(credentialBytes))
                assertEquals(credentialBytes.toHex(), response.credentialId, "response must echo the queried credential ID as hex")
                assertEquals(0, response.count)
                assertTrue(response.contracts.isEmpty())
            } finally {
                client.close()
            }
        }
    }

    @Test
    fun testLookupByUnknownAddress_returnsWellFormedEmptyResult() = runTest(timeout = 60.seconds) {
        withContext(Dispatchers.Default) {
            val client = client()
            try {
                val address = KeyPair.random().getAccountId()
                val response = client.lookupByAddress(address)
                assertEquals(address, response.signerAddress, "response must echo the queried address")
                assertEquals(0, response.count)
                assertTrue(response.contracts.isEmpty())
            } finally {
                client.close()
            }
        }
    }

    @Test
    fun testGetContract_unknownContract_throwsTyped() = runTest(timeout = 60.seconds) {
        withContext(Dispatchers.Default) {
            val client = client()
            try {
                val contractId = StrKey.encodeContract(Random.nextBytes(32))
                assertFailsWith<IndexerException> {
                    client.getContract(contractId)
                }
            } finally {
                client.close()
            }
        }
    }
}
