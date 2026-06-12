//
//  ExtractPasskeySignersTest.kt
//  KMP Stellar SDK - Smart Account Demo
//
//  Copyright (c) 2026 Soneso. All rights reserved.
//

package com.soneso.smartdemo.flows

import com.soneso.smartdemo.config.DemoConfig
import com.soneso.stellar.sdk.smartaccount.core.DelegatedSigner
import com.soneso.stellar.sdk.smartaccount.core.ExternalSigner
import com.soneso.stellar.sdk.smartaccount.core.SmartAccountBuilders
import com.soneso.stellar.sdk.smartaccount.core.SmartAccountSigner
import com.soneso.stellar.sdk.smartaccount.oz.ContextRuleType
import com.soneso.stellar.sdk.smartaccount.oz.ParsedContextRule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Unit tests for the pure [extractPasskeySigners] helper.
 *
 * Covers:
 * - Filtering to passkey signers only (ExternalSigner on the WebAuthn verifier)
 * - Exclusion of external signers with a non-WebAuthn verifier address
 * - Deduplication by signer key across rules
 * - Optional exclusion of a passkey by credential ID
 */
class ExtractPasskeySignersTest {

    // MARK: - Fixtures

    companion object {
        /** A valid Stellar G-address used for delegated signer fixtures
         *  (the Stellar testnet Friendbot account). */
        private const val DELEGATED_ADDRESS = "GAIH3ULLFQ4DGSECF2AR555KZ4KNDGEKN4AFI4SU2M7B43MGK3QJZNSR"

        /** The secp256r1 generator point as a 65-byte uncompressed public key (0x04 || X || Y). */
        private val GENERATOR_PUBLIC_KEY: ByteArray =
            byteArrayOf(0x04) +
                hexToBytes("6B17D1F2E12C4247F8BCE6E563A440F277037D812DEB33A0F4A13945D898C296") +
                hexToBytes("4FE342E2FE1A7F9B8EE7EB4A7C0F9E162BCE33576B315ECECBB6406837BF51F5")

        private fun hexToBytes(hex: String): ByteArray {
            return ByteArray(hex.length / 2) { i ->
                hex.substring(i * 2, i * 2 + 2).toInt(16).toByte()
            }
        }
    }

    /** Builds a passkey signer on the configured WebAuthn verifier with the given credential ID. */
    private fun passkeySigner(credentialId: ByteArray): ExternalSigner {
        return ExternalSigner.webAuthn(
            verifierAddress = DemoConfig.WEBAUTHN_VERIFIER_ADDRESS,
            publicKey = GENERATOR_PUBLIC_KEY,
            credentialId = credentialId
        )
    }

    /** Builds a minimal [ParsedContextRule] wrapping the given signers. */
    private fun rule(id: UInt, signers: List<SmartAccountSigner>): ParsedContextRule {
        return ParsedContextRule(
            id = id,
            contextType = ContextRuleType.Default,
            name = "rule-$id",
            signers = signers,
            signerIds = signers.indices.map { it.toUInt() },
            policies = emptyList(),
            policyIds = emptyList(),
            validUntil = null
        )
    }

    // MARK: - Tests

    @Test
    fun returnsOnlyExternalSignersAndDropsDelegatedSigners() {
        val passkey = passkeySigner(credentialId = byteArrayOf(1, 2, 3, 4))
        val delegated = DelegatedSigner(DELEGATED_ADDRESS)
        val rules = listOf(rule(1u, listOf(delegated, passkey)))

        val result = extractPasskeySigners(rules)

        assertEquals(listOf(passkey), result)
    }

    @Test
    fun dropsExternalSignersWithNonWebauthnVerifier() {
        val passkey = passkeySigner(credentialId = byteArrayOf(1, 2, 3, 4))
        val ed25519 = ExternalSigner.ed25519(
            verifierAddress = DemoConfig.ED25519_VERIFIER_ADDRESS,
            publicKey = ByteArray(32) { it.toByte() }
        )
        val rules = listOf(rule(1u, listOf(ed25519, passkey)))

        val result = extractPasskeySigners(rules)

        assertEquals(listOf(passkey), result)
    }

    @Test
    fun deduplicatesSameSignerAcrossRulesBySignerKey() {
        val passkeyA = passkeySigner(credentialId = byteArrayOf(1, 2, 3, 4))
        val passkeyADuplicate = passkeySigner(credentialId = byteArrayOf(1, 2, 3, 4))
        val passkeyB = passkeySigner(credentialId = byteArrayOf(5, 6, 7, 8))
        val rules = listOf(
            rule(1u, listOf(passkeyA, passkeyB)),
            rule(2u, listOf(passkeyADuplicate))
        )

        val result = extractPasskeySigners(rules)

        assertEquals(2, result.size)
        assertEquals(
            listOf(
                SmartAccountBuilders.getSignerKey(passkeyA),
                SmartAccountBuilders.getSignerKey(passkeyB)
            ),
            result.map { SmartAccountBuilders.getSignerKey(it) }
        )
    }

    @Test
    fun excludesPasskeyMatchingExcludeCredentialId() {
        val ownPasskey = passkeySigner(credentialId = byteArrayOf(1, 2, 3, 4))
        val otherPasskey = passkeySigner(credentialId = byteArrayOf(5, 6, 7, 8))
        val ownCredentialId = SmartAccountBuilders.getCredentialIdStringFromSigner(ownPasskey)!!
        val rules = listOf(rule(1u, listOf(ownPasskey, otherPasskey)))

        val result = extractPasskeySigners(rules, excludeCredentialId = ownCredentialId)

        assertEquals(listOf(otherPasskey), result)
    }

    @Test
    fun nullExcludeCredentialIdIncludesAllPasskeys() {
        val passkeyA = passkeySigner(credentialId = byteArrayOf(1, 2, 3, 4))
        val passkeyB = passkeySigner(credentialId = byteArrayOf(5, 6, 7, 8))
        val rules = listOf(rule(1u, listOf(passkeyA, passkeyB)))

        val result = extractPasskeySigners(rules, excludeCredentialId = null)

        assertEquals(2, result.size)
        assertTrue(result.contains(passkeyA))
        assertTrue(result.contains(passkeyB))
    }

    @Test
    fun returnsEmptyListForRulesWithoutPasskeySigners() {
        val delegated = DelegatedSigner(DELEGATED_ADDRESS)
        val rules = listOf(rule(1u, listOf(delegated)), rule(2u, emptyList()))

        val result = extractPasskeySigners(rules)

        assertTrue(result.isEmpty())
    }
}
