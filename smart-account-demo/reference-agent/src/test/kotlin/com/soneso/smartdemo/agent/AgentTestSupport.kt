package com.soneso.smartdemo.agent

import com.soneso.stellar.sdk.KeyPair
import kotlinx.coroutines.runBlocking

/**
 * Shared fixtures for the reference-agent test suite.
 *
 * The keypair helpers run in a blocking scope so the synchronous JUnit tests can
 * build deterministic identities without each test file restating the same
 * derivation logic.
 */

/** Returns a fresh random Stellar account id (G-address) for test fixtures. */
internal fun randomGAddress(): String = runBlocking { KeyPair.random().getAccountId() }

/** Derives the raw 32-byte Ed25519 public key for [seed] (a raw 32-byte seed). */
internal fun publicKeyFor(seed: ByteArray): ByteArray =
    runBlocking { KeyPair.fromSecretSeed(seed).getPublicKey() }
