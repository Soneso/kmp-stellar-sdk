package com.soneso.smartdemo.agent

import com.soneso.stellar.sdk.KeyPair
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AgentEd25519AdapterTest {

    private val verifier = AgentDefaults.ED25519_VERIFIER_ADDRESS
    private val seed = ByteArray(32) { (it + 1).toByte() }

    private fun publicKey(): ByteArray = runBlocking { KeyPair.fromSecretSeed(seed).getPublicKey() }

    @Test
    fun canSignForReportsRegisteredSlotsOnly() = runBlocking {
        val adapter = AgentEd25519Adapter()
        val pub = publicKey()
        assertFalse(adapter.canSignFor(verifier, pub))
        adapter.add(verifier, pub, seed)
        assertTrue(adapter.canSignFor(verifier, pub))
    }

    @Test
    fun signAuthDigestProducesTheKeypairsSignature() = runBlocking {
        val adapter = AgentEd25519Adapter()
        val pub = publicKey()
        adapter.add(verifier, pub, seed)

        val digest = ByteArray(32) { 7 }
        val signature = adapter.signAuthDigest(digest, pub)
        assertEquals(64, signature.size)
        // Ed25519 is deterministic: the adapter must produce the same signature the
        // registered keypair would, so the on-chain verifier accepts it.
        assertContentEquals(KeyPair.fromSecretSeed(seed).sign(digest), signature)
    }

    @Test
    fun clearAllDropsTheRegisteredSeed() = runBlocking {
        val adapter = AgentEd25519Adapter()
        val pub = publicKey()
        adapter.add(verifier, pub, seed)
        adapter.clearAll()
        assertFalse(adapter.canSignFor(verifier, pub))
    }

    @Test
    fun signAuthDigestWithoutARegisteredSeedThrows(): Unit = runBlocking {
        val adapter = AgentEd25519Adapter()
        assertFailsWith<IllegalStateException> {
            adapter.signAuthDigest(ByteArray(32), publicKey())
        }
    }
}
