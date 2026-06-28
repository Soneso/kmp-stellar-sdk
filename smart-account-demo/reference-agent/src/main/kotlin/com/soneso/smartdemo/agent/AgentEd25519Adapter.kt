package com.soneso.smartdemo.agent

import com.soneso.stellar.sdk.KeyPair
import com.soneso.stellar.sdk.smartaccount.oz.OZExternalEd25519SignerAdapter
import java.util.concurrent.ConcurrentHashMap

/**
 * [OZExternalEd25519SignerAdapter] that signs with in-process Ed25519 seeds,
 * keyed by the on-chain `(verifierAddress, publicKey)` signer slot.
 *
 * Mirrors the demo app's Ed25519 adapter: the signing seed lives inside the
 * adapter, never in the SDK manager's in-process keypair registry. The kit's
 * multi-signer pipeline calls [canSignFor] first; when it returns `true` the
 * pipeline calls [signAuthDigest] on this adapter (adapter-first precedence)
 * rather than its own keypair registry.
 *
 * Supply one instance to `OZSmartAccountConfig.externalEd25519Adapter` at kit
 * construction. Register the agent's seed via [add] before submitting a
 * multi-signer call, and [clearAll] afterwards so the adapter does not retain
 * the seed beyond its needed lifetime.
 */
class AgentEd25519Adapter : OZExternalEd25519SignerAdapter {

    /**
     * Seeds keyed by `(verifierAddress, publicKeyHex)`, mirroring the on-chain slot identity.
     *
     * A [ConcurrentHashMap] so every access is consistently thread-safe with a happens-before
     * relationship: the kit's multi-signer pipeline probes [canSignFor] synchronously, possibly
     * from a different thread than the coroutine that called [add] / [clearAll], and must observe
     * the registered seed (or its removal) rather than a stale view.
     */
    private val registry = ConcurrentHashMap<SignerSlot, ByteArray>()

    /**
     * Registers the 32-byte Ed25519 [seedBytes] for the on-chain signer slot
     * identified by [verifierAddress] and [publicKey]. Registering a second seed
     * for the same slot overwrites the previous entry.
     */
    fun add(verifierAddress: String, publicKey: ByteArray, seedBytes: ByteArray) {
        val slot = SignerSlot(verifierAddress, Hex.encode(publicKey))
        registry[slot] = seedBytes.copyOf()
    }

    /** Removes every registered seed. */
    fun clearAll() {
        registry.clear()
    }

    /**
     * Synchronous capability check. The pipeline calls this before [signAuthDigest]; the
     * concurrent registry makes the containment check observe writes from [add] / [clearAll].
     */
    override fun canSignFor(verifierAddress: String, publicKey: ByteArray): Boolean {
        val slot = SignerSlot(verifierAddress, Hex.encode(publicKey))
        return registry.containsKey(slot)
    }

    override suspend fun signAuthDigest(authDigest: ByteArray, publicKey: ByteArray): ByteArray {
        // signAuthDigest receives only the public key (not the verifier address),
        // so locate by public key. A single agent registers one slot, so the
        // first match is unambiguous; canSignFor has already confirmed the slot.
        val publicKeyHex = Hex.encode(publicKey)
        val seed = registry.entries.firstOrNull { it.key.publicKeyHex == publicKeyHex }?.value
            ?: throw IllegalStateException(
            "No Ed25519 seed registered for the given public key. " +
                "Call add() with the agent seed before invoking the signing pipeline."
        )
        return KeyPair.fromSecretSeed(seed).sign(authDigest)
    }

    /**
     * Composite key mirroring the on-chain `External(verifierAddress, publicKey)`
     * signer identity. The public key is stored as hex so map equality is
     * content-based.
     */
    private data class SignerSlot(val verifierAddress: String, val publicKeyHex: String)
}
