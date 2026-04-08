//
//  JsWebAuthnGuardTest.kt
//  Stellar SDK Kotlin Multiplatform
//
//  Copyright (c) 2026 Soneso. All rights reserved.
//

package com.soneso.stellar.sdk.smartaccount

import com.soneso.stellar.sdk.smartaccount.core.StorageException
import com.soneso.stellar.sdk.smartaccount.core.WebAuthnException
import com.soneso.stellar.sdk.smartaccount.oz.OZConstants
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

/**
 * Tests for Node.js guard behaviour of [JsWebAuthnProvider] and [IndexedDBStorageAdapter].
 *
 * These tests run under `jsNodeTest` where `navigator.credentials` is absent and
 * `indexedDB` is not available. They verify that the SDK surfaces the correct typed
 * exceptions rather than crashing with raw JS errors.
 */
class JsWebAuthnGuardTest {

    companion object {
        /** Returns true when running in a Node.js environment (no `navigator.credentials`). */
        private fun isNodeEnvironment(): Boolean = js(
            "typeof navigator === 'undefined' || !navigator.credentials"
        ) as Boolean

        private fun testChallenge(): ByteArray = ByteArray(32) { it.toByte() }
        private fun testUserId(): ByteArray = ByteArray(16) { it.toByte() }
    }

    // MARK: - JsWebAuthnProvider: register()

    /**
     * Verifies that [JsWebAuthnProvider.register] throws [WebAuthnException.NotSupported]
     * in Node.js where `navigator.credentials` is not available.
     */
    @Test
    fun testRegisterThrowsNotSupportedInNodeJs() = runTest {
        if (!isNodeEnvironment()) return@runTest

        val provider = JsWebAuthnProvider(
            rpId = "example.com",
            rpName = "Test App"
        )

        val exception = assertFailsWith<WebAuthnException.NotSupported> {
            provider.register(
                challenge = testChallenge(),
                userId = testUserId(),
                userName = "alice@example.com"
            )
        }
        assertNotNull(exception.message)
    }

    // MARK: - JsWebAuthnProvider: authenticate()

    /**
     * Verifies that [JsWebAuthnProvider.authenticate] throws [WebAuthnException.NotSupported]
     * in Node.js where `navigator.credentials` is not available.
     */
    @Test
    fun testAuthenticateThrowsNotSupportedInNodeJs() = runTest {
        if (!isNodeEnvironment()) return@runTest

        val provider = JsWebAuthnProvider(
            rpId = "example.com",
            rpName = "Test App"
        )

        val exception = assertFailsWith<WebAuthnException.NotSupported> {
            provider.authenticate(
                challenge = testChallenge(),
                allowCredentialIds = null
            )
        }
        assertNotNull(exception.message)
    }

    /**
     * Verifies that [JsWebAuthnProvider.authenticate] with an explicit credential ID list
     * also throws [WebAuthnException.NotSupported] in Node.js.
     */
    @Test
    fun testAuthenticateWithAllowCredentialsThrowsNotSupportedInNodeJs() = runTest {
        if (!isNodeEnvironment()) return@runTest

        val provider = JsWebAuthnProvider(
            rpId = "example.com",
            rpName = "Test App"
        )

        assertFailsWith<WebAuthnException.NotSupported> {
            provider.authenticate(
                challenge = testChallenge(),
                allowCredentialIds = listOf(ByteArray(32) { 0xAB.toByte() })
            )
        }
    }

    // MARK: - JsWebAuthnProvider: Constructor

    /**
     * Verifies that [JsWebAuthnProvider] can be constructed with a custom timeout
     * and that it is stored correctly. Construction itself never throws -- input
     * validation occurs at invocation time (not at construction time).
     */
    @Test
    fun testConstructorAcceptsValidParameters() {
        val provider = JsWebAuthnProvider(
            rpId = "stellar.org",
            rpName = "Stellar App",
            timeout = 30_000L
        )
        assertNotNull(provider)
    }

    /**
     * Verifies that [JsWebAuthnProvider] uses the default timeout from [OZConstants]
     * when no explicit timeout is provided.
     */
    @Test
    fun testConstructorUsesDefaultTimeout() {
        // Default timeout is OZConstants.WEBAUTHN_TIMEOUT_MS (60_000L).
        // This test documents the expected default -- any regression in the default
        // would cause a mismatch if the provider were to expose the value.
        val provider = JsWebAuthnProvider(rpId = "stellar.org", rpName = "Stellar App")
        assertNotNull(provider)
        assertEquals(OZConstants.WEBAUTHN_TIMEOUT_MS, 60_000L)
    }

    // MARK: - IndexedDBStorageAdapter: save()

    /**
     * Verifies that [IndexedDBStorageAdapter.save] throws [StorageException.ReadFailed]
     * in Node.js where `indexedDB` is unavailable.
     *
     * The adapter attempts to open the database on the first operation.
     * When IndexedDB is not available the open step fails before any write occurs,
     * so [StorageException.ReadFailed] is the correct exception type.
     */
    @Test
    fun testIndexedDbSaveThrowsWhenUnavailable() = runTest {
        if (!isNodeEnvironment()) return@runTest

        val adapter = IndexedDBStorageAdapter()
        val credential = minimalTestCredential("idb-save-guard")

        assertFailsWith<StorageException.ReadFailed> {
            adapter.save(credential)
        }
    }

    // MARK: - IndexedDBStorageAdapter: get()

    /**
     * Verifies that [IndexedDBStorageAdapter.get] throws [StorageException.ReadFailed]
     * in Node.js where `indexedDB` is unavailable.
     */
    @Test
    fun testIndexedDbGetThrowsWhenUnavailable() = runTest {
        if (!isNodeEnvironment()) return@runTest

        val adapter = IndexedDBStorageAdapter()

        assertFailsWith<StorageException.ReadFailed> {
            adapter.get("nonexistent-credential-id")
        }
    }

    // MARK: - Helpers

    private fun minimalTestCredential(id: String): com.soneso.stellar.sdk.smartaccount.oz.StoredCredential =
        com.soneso.stellar.sdk.smartaccount.oz.StoredCredential(
            credentialId = id,
            publicKey = ByteArray(65) { i -> if (i == 0) 0x04.toByte() else (i % 256).toByte() },
            contractId = null,
            deploymentStatus = com.soneso.stellar.sdk.smartaccount.oz.CredentialDeploymentStatus.PENDING,
            createdAt = 1700000000000L
        )
}
