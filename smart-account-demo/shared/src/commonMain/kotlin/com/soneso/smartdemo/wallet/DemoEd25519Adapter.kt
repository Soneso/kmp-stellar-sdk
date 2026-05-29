package com.soneso.smartdemo.wallet

import com.soneso.smartdemo.flows.Ed25519SignerIdentity
import com.soneso.stellar.sdk.KeyPair
import com.soneso.stellar.sdk.smartaccount.oz.OZExternalEd25519SignerAdapter
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Reference implementation of [OZExternalEd25519SignerAdapter] for demonstration purposes.
 *
 * Stores verified 32-byte Ed25519 seeds in a thread-safe in-memory registry keyed by
 * `(verifierAddress, publicKey)`. Intended for demonstration and testing of the adapter
 * callback path — real production adapters delegate to hardware wallets, HSMs, or remote
 * signing services and never handle raw seed material.
 *
 * The adapter is injected at kit construction via OZSmartAccountConfig.externalEd25519Adapter
 * and consulted by kit.externalSigners ahead of its in-memory keypair registry. [canSignFor]
 * returns `true` only for seeds registered via [add]; keys registered in-process through
 * kit.externalSigners.addEd25519FromRawKey are deliberately absent here so they route through
 * the in-memory path rather than this adapter.
 *
 * Usage pattern:
 * 1. After the user verifies secrets in the signer picker, call [add] for each secret.
 * 2. Submit the multi-signer operation; the manager invokes [signAuthDigest] for covered keys.
 * 3. After submission (success or failure), call [clearAll] to drop all seed material.
 */
class DemoEd25519Adapter : OZExternalEd25519SignerAdapter {

    private val mutex = Mutex()

    // Key: composite (verifierAddress, publicKeyHex) — same tuple semantics as the SDK's
    // internal Ed25519SignerKey. PublicKey stored as hex for content-comparable map equality.
    private val registry = mutableMapOf<Ed25519SignerIdentity, ByteArray>()

    /**
     * Registers an Ed25519 signing secret for the given signer identity.
     *
     * Overwrites any existing entry for the same `(verifierAddress, publicKey)` pair.
     *
     * @param identity The `(verifierAddress, publicKey)` pair identifying the on-chain signer slot.
     * @param seedBytes The 32-byte raw Ed25519 seed for this signer.
     */
    suspend fun add(identity: Ed25519SignerIdentity, seedBytes: ByteArray) {
        mutex.withLock {
            registry[identity] = seedBytes.copyOf()
        }
    }

    /**
     * Clears all registered secrets. Must be called after submission succeeds or fails
     * to prevent raw seed material from persisting beyond its needed lifetime.
     */
    suspend fun clearAll() {
        mutex.withLock {
            registry.clear()
        }
    }

    // MARK: - OZExternalEd25519SignerAdapter

    /**
     * Returns whether this adapter holds a secret for the given `(verifierAddress, publicKey)` pair.
     *
     * @param verifierAddress C-strkey of the Ed25519 verifier contract.
     * @param publicKey 32-byte Ed25519 public key of the signer slot.
     * @return `true` when a seed is registered for this pair.
     */
    override fun canSignFor(verifierAddress: String, publicKey: ByteArray): Boolean {
        val identity = Ed25519SignerIdentity(verifierAddress, publicKey)
        // Read-only snapshot — safe without mutex on immutable key lookup
        return registry.containsKey(identity)
    }

    /**
     * Signs [authDigest] using the Ed25519 keypair derived from the registered seed.
     *
     * Derives the keypair from the seed at signing time rather than caching the full
     * [KeyPair] so that secret material is not held beyond what each sign call requires.
     *
     * @param authDigest 32-byte digest to sign, computed by the SDK's auth pipeline.
     * @param publicKey 32-byte Ed25519 public key identifying which registered seed to use.
     * @return 64-byte raw Ed25519 signature over [authDigest].
     * @throws IllegalStateException if no seed is registered for the given [publicKey] under
     *   any verifier address accessible to [canSignFor].
     */
    override suspend fun signAuthDigest(authDigest: ByteArray, publicKey: ByteArray): ByteArray {
        // Locate the seed by scanning for any identity whose publicKey matches.
        // The SDK calls canSignFor(verifierAddress, publicKey) before calling signAuthDigest,
        // so we can locate the seed by publicKey alone here — the verifierAddress is
        // already confirmed by the canSignFor check. In the unlikely event two entries share
        // the same publicKey under different verifier addresses, the first match is used;
        // the SDK resolves tuple identity upstream.
        val seedBytes = mutex.withLock {
            registry.entries.firstOrNull { it.key.publicKey.contentEquals(publicKey) }?.value
        } ?: throw IllegalStateException(
            "No signing secret found for the given public key. " +
                "Call add() with the verified seed before invoking the signing pipeline."
        )

        val keypair = KeyPair.fromSecretSeed(seedBytes)
        return keypair.sign(authDigest)
    }
}
