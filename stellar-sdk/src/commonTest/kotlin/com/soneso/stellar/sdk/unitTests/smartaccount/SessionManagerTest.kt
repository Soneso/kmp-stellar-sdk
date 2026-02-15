//
//  SessionManagerTest.kt
//  Stellar SDK Kotlin Multiplatform
//
//  Copyright (c) 2026 Soneso. All rights reserved.
//

package com.soneso.stellar.sdk.unitTests.smartaccount

import com.soneso.stellar.sdk.Network
import com.soneso.stellar.sdk.smartaccount.core.*
import com.soneso.stellar.sdk.smartaccount.oz.*
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Unit tests for session management in the Smart Account Kit.
 *
 * Session operations are handled by the [StorageAdapter] and exposed through [OZSmartAccountKit].
 * The low-level StorageAdapter session CRUD is already tested in StorageAdapterTest.
 * SmartAccountKitTest covers connectWallet with valid session restoration.
 *
 * These tests cover session lifecycle scenarios not covered elsewhere:
 * - StoredSession.isExpired with edge cases
 * - Session save/restore/clear via kit storage
 * - Session independence from credentials
 * - Session overwrite behavior
 * - Session with custom expiry configuration
 * - Kit disconnect clears session
 */
class SessionManagerTest {

    // MARK: - Test Fixtures

    private suspend fun createKit(
        sessionExpiryMs: Long = SmartAccountConstants.DEFAULT_SESSION_EXPIRY_MS
    ): Pair<OZSmartAccountKit, InMemoryStorageAdapter> {
        val storage = InMemoryStorageAdapter()
        val config = OZSmartAccountConfig(
            rpcUrl = "https://soroban-testnet.stellar.org",
            networkPassphrase = Network.TESTNET.networkPassphrase,
            accountWasmHash = "a" + "0".repeat(63),
            webauthnVerifierAddress = "CAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAD2KM",
            sessionExpiryMs = sessionExpiryMs
        )
        val kit = OZSmartAccountKit.create(config, storage = storage)
        return Pair(kit, storage)
    }

    private val testContractId = "CBCD1234" + "A".repeat(48)

    // MARK: - StoredSession.isExpired Edge Cases

    @Test
    fun testStoredSession_expiresAtZero_isExpired() {
        val session = StoredSession(
            credentialId = "cred",
            contractId = testContractId,
            connectedAt = 0L,
            expiresAt = 0L
        )
        assertTrue(session.isExpired, "Session with expiresAt=0 should be expired")
    }

    @Test
    fun testStoredSession_expiresAtMaxValue_notExpired() {
        val session = StoredSession(
            credentialId = "cred",
            contractId = testContractId,
            connectedAt = 1700000000000L,
            expiresAt = Long.MAX_VALUE
        )
        assertFalse(session.isExpired, "Session with expiresAt=Long.MAX_VALUE should not be expired")
    }

    @Test
    fun testStoredSession_expiresAtInPast_isExpired() {
        val session = StoredSession(
            credentialId = "cred",
            contractId = testContractId,
            connectedAt = 1000L,
            expiresAt = 5000L // way in the past
        )
        assertTrue(session.isExpired)
    }

    // MARK: - StoredSession Data Class Properties

    @Test
    fun testStoredSession_allFieldsAccessible() {
        val session = StoredSession(
            credentialId = "cred-abc",
            contractId = "CONTRACT-XYZ",
            connectedAt = 1700000000000L,
            expiresAt = 1700604800000L
        )

        assertEquals("cred-abc", session.credentialId)
        assertEquals("CONTRACT-XYZ", session.contractId)
        assertEquals(1700000000000L, session.connectedAt)
        assertEquals(1700604800000L, session.expiresAt)
    }

    @Test
    fun testStoredSession_equalityCheck() {
        val session1 = StoredSession(
            credentialId = "cred",
            contractId = "CONTRACT",
            connectedAt = 1000L,
            expiresAt = 2000L
        )
        val session2 = StoredSession(
            credentialId = "cred",
            contractId = "CONTRACT",
            connectedAt = 1000L,
            expiresAt = 2000L
        )

        assertEquals(session1, session2)
        assertEquals(session1.hashCode(), session2.hashCode())
    }

    // MARK: - Session Save and Retrieve via Storage

    @Test
    fun testSaveSession_thenRetrieve() = runTest {
        val (_, storage) = createKit()

        val session = StoredSession(
            credentialId = "cred-session",
            contractId = testContractId,
            connectedAt = 1700000000000L,
            expiresAt = Long.MAX_VALUE
        )
        storage.saveSession(session)

        val retrieved = storage.getSession()
        assertNotNull(retrieved)
        assertEquals("cred-session", retrieved.credentialId)
        assertEquals(testContractId, retrieved.contractId)
    }

    @Test
    fun testGetSession_noneExists_returnsNull() = runTest {
        val (_, storage) = createKit()

        val result = storage.getSession()
        assertNull(result)
    }

    // MARK: - Session Overwrite Behavior

    @Test
    fun testSaveSession_overwritesPreviousSession() = runTest {
        val (_, storage) = createKit()

        val session1 = StoredSession(
            credentialId = "cred-1",
            contractId = "CONTRACT_1",
            connectedAt = 1700000000000L,
            expiresAt = Long.MAX_VALUE
        )
        storage.saveSession(session1)

        val session2 = StoredSession(
            credentialId = "cred-2",
            contractId = "CONTRACT_2",
            connectedAt = 1700001000000L,
            expiresAt = Long.MAX_VALUE
        )
        storage.saveSession(session2)

        val retrieved = storage.getSession()
        assertNotNull(retrieved)
        assertEquals("cred-2", retrieved.credentialId)
        assertEquals("CONTRACT_2", retrieved.contractId)
    }

    // MARK: - Session Clear

    @Test
    fun testClearSession_removesSession() = runTest {
        val (_, storage) = createKit()

        storage.saveSession(StoredSession(
            credentialId = "cred",
            contractId = testContractId,
            connectedAt = 1700000000000L,
            expiresAt = Long.MAX_VALUE
        ))

        storage.clearSession()

        assertNull(storage.getSession())
    }

    @Test
    fun testClearSession_whenNoneExists_noOp() = runTest {
        val (_, storage) = createKit()

        // Should not throw
        storage.clearSession()
        assertNull(storage.getSession())
    }

    // MARK: - Session Expiry Auto-Clear via Storage

    @Test
    fun testExpiredSession_autoClearedOnGet() = runTest {
        val (_, storage) = createKit()

        // Save a session that is already expired
        val expiredSession = StoredSession(
            credentialId = "expired-cred",
            contractId = testContractId,
            connectedAt = 1000L,
            expiresAt = 2000L // well in the past
        )
        storage.saveSession(expiredSession)

        // getSession should detect expiry and return null
        val result = storage.getSession()
        assertNull(result, "Expired session should return null")

        // Verify it was actually removed
        val secondResult = storage.getSession()
        assertNull(secondResult, "Expired session should remain cleared")
    }

    // MARK: - Kit Disconnect Clears Session

    @Test
    fun testKitDisconnect_clearsSession() = runTest {
        val (kit, storage) = createKit()

        // Manually save a session
        storage.saveSession(StoredSession(
            credentialId = "cred-1",
            contractId = testContractId,
            connectedAt = 1700000000000L,
            expiresAt = Long.MAX_VALUE
        ))

        // Connect the kit state
        kit.setConnectedState("cred-1", testContractId)
        assertTrue(kit.isConnected)

        // Disconnect should clear session
        kit.disconnect()
        assertFalse(kit.isConnected)
        assertNull(storage.getSession(), "Session should be cleared after disconnect")
    }

    // MARK: - Session Independence from Credentials

    @Test
    fun testSessionIndependentFromCredentials() = runTest {
        val (kit, storage) = createKit()

        // Save a credential and a session
        kit.credentialManager.createPendingCredential(
            credentialId = "cred-1",
            publicKey = ByteArray(65) { if (it == 0) 0x04.toByte() else it.toByte() },
            contractId = testContractId
        )

        storage.saveSession(StoredSession(
            credentialId = "cred-1",
            contractId = testContractId,
            connectedAt = 1700000000000L,
            expiresAt = Long.MAX_VALUE
        ))

        // Clear all credentials
        kit.credentialManager.clearAll()

        // Session should still exist
        val session = storage.getSession()
        assertNotNull(session, "Session should not be affected by clearing credentials")
        assertEquals("cred-1", session.credentialId)
    }

    @Test
    fun testClearSessionDoesNotAffectCredentials() = runTest {
        val (kit, storage) = createKit()

        kit.credentialManager.createPendingCredential(
            credentialId = "cred-1",
            publicKey = ByteArray(65) { if (it == 0) 0x04.toByte() else it.toByte() },
            contractId = testContractId
        )

        storage.saveSession(StoredSession(
            credentialId = "cred-1",
            contractId = testContractId,
            connectedAt = 1700000000000L,
            expiresAt = Long.MAX_VALUE
        ))

        storage.clearSession()

        // Credentials should remain
        val cred = kit.credentialManager.getCredential("cred-1")
        assertNotNull(cred)
        assertEquals("cred-1", cred.credentialId)
    }

    // MARK: - Config SessionExpiryMs

    @Test
    fun testConfigSessionExpiryMs_default() {
        val config = OZSmartAccountConfig(
            rpcUrl = "https://soroban-testnet.stellar.org",
            networkPassphrase = Network.TESTNET.networkPassphrase,
            accountWasmHash = "a" + "0".repeat(63),
            webauthnVerifierAddress = "CAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAD2KM"
        )

        assertEquals(SmartAccountConstants.DEFAULT_SESSION_EXPIRY_MS, config.sessionExpiryMs)
    }

    @Test
    fun testConfigSessionExpiryMs_custom() {
        val oneDayMs = 86400000L
        val config = OZSmartAccountConfig(
            rpcUrl = "https://soroban-testnet.stellar.org",
            networkPassphrase = Network.TESTNET.networkPassphrase,
            accountWasmHash = "a" + "0".repeat(63),
            webauthnVerifierAddress = "CAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAD2KM",
            sessionExpiryMs = oneDayMs
        )

        assertEquals(oneDayMs, config.sessionExpiryMs)
    }

    // MARK: - Kit Connected State

    @Test
    fun testKitIsConnected_initiallyFalse() = runTest {
        val (kit, _) = createKit()
        assertFalse(kit.isConnected)
    }

    @Test
    fun testKitIsConnected_afterSetConnectedState() = runTest {
        val (kit, _) = createKit()
        kit.setConnectedState("cred-1", testContractId)
        assertTrue(kit.isConnected)
    }

    @Test
    fun testKitIsConnected_afterDisconnect() = runTest {
        val (kit, _) = createKit()
        kit.setConnectedState("cred-1", testContractId)
        assertTrue(kit.isConnected)

        kit.disconnect()
        assertFalse(kit.isConnected)
    }

    @Test
    fun testKitRequireConnected_throwsWhenNotConnected() = runTest {
        val (kit, _) = createKit()

        assertFalse(kit.isConnected)

        // requireConnected is tested in SmartAccountKitTest already, but verify the exception type
        kotlin.test.assertFailsWith<WalletException.NotConnected> {
            kit.requireConnected()
        }
    }
}
