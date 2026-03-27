//
//  EventSystemTest.kt
//  Stellar SDK Kotlin Multiplatform
//
//  Copyright (c) 2026 Soneso. All rights reserved.
//

package com.soneso.stellar.sdk.unitTests.smartaccount

import com.soneso.stellar.sdk.smartaccount.core.*
import com.soneso.stellar.sdk.smartaccount.oz.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Unit tests for [SmartAccountEventEmitter] and the [SmartAccountEvent] hierarchy.
 *
 * Tests cover event system functionality not already covered in SmartAccountKitTest:
 * - addListener (non-reified global listener) receives all event types
 * - Error handler: failing listener does not affect other listeners
 * - Error handler: setErrorHandler captures event and error
 * - Listener count tracking
 * - Unsubscribe via returned function
 * - removeAllListeners for specific event type
 * - Emit isolation: type-specific listeners only receive matching events
 * - SmartAccountEvent data class instantiation
 *
 * SmartAccountKitTest already covers: on<T>(), once<T>(), removeAllListeners()
 */
class EventSystemTest {

    // MARK: - addListener (Global Listener) Tests

    @Test
    fun testAddListener_receivesAllEventTypes() {
        val emitter = SmartAccountEventEmitter()
        val receivedEvents = mutableListOf<SmartAccountEvent>()

        emitter.addListener { event ->
            receivedEvents.add(event)
        }

        emitter.emit(SmartAccountEvent.WalletConnected(
            contractId = "CABC1234" + "A".repeat(48),
            credentialId = "cred-1"
        ))
        emitter.emit(SmartAccountEvent.TransactionSubmitted(
            hash = "tx-hash-123",
            success = true
        ))
        emitter.emit(SmartAccountEvent.WalletDisconnected(
            contractId = "CABC1234" + "A".repeat(48)
        ))

        assertEquals(3, receivedEvents.size)
        assertTrue(receivedEvents[0] is SmartAccountEvent.WalletConnected)
        assertTrue(receivedEvents[1] is SmartAccountEvent.TransactionSubmitted)
        assertTrue(receivedEvents[2] is SmartAccountEvent.WalletDisconnected)
    }

    @Test
    fun testAddListener_unsubscribeStopsReceiving() {
        val emitter = SmartAccountEventEmitter()
        var callCount = 0

        val unsubscribe = emitter.addListener { _ ->
            callCount++
        }

        emitter.emit(SmartAccountEvent.WalletDisconnected(contractId = "C1234"))
        assertEquals(1, callCount)

        unsubscribe()

        emitter.emit(SmartAccountEvent.WalletDisconnected(contractId = "C5678"))
        assertEquals(1, callCount, "Should not receive events after unsubscribe")
    }

    @Test
    fun testAddListener_multipleGlobalListenersAllReceive() {
        val emitter = SmartAccountEventEmitter()
        var count1 = 0
        var count2 = 0
        var count3 = 0

        emitter.addListener { _ -> count1++ }
        emitter.addListener { _ -> count2++ }
        emitter.addListener { _ -> count3++ }

        emitter.emit(SmartAccountEvent.WalletConnected(
            contractId = "CONTRACT",
            credentialId = "cred"
        ))

        assertEquals(1, count1)
        assertEquals(1, count2)
        assertEquals(1, count3)
    }

    // MARK: - Error Handler Tests

    @Test
    fun testErrorHandler_failingListenerDoesNotAffectOthers() {
        val emitter = SmartAccountEventEmitter()
        var listener1Called = false
        var listener3Called = false

        // Listener 1: normal
        emitter.addListener { _ -> listener1Called = true }

        // Listener 2: throws
        emitter.addListener { _ -> throw RuntimeException("Intentional failure") }

        // Listener 3: normal
        emitter.addListener { _ -> listener3Called = true }

        // Suppress error handler noise
        emitter.setErrorHandler { _, _ -> /* silently ignore */ }

        emitter.emit(SmartAccountEvent.WalletDisconnected(contractId = "CONTRACT"))

        assertTrue(listener1Called, "First listener should have been called")
        assertTrue(listener3Called, "Third listener should still be called despite second failing")
    }

    @Test
    fun testSetErrorHandler_capturesEventAndError() {
        val emitter = SmartAccountEventEmitter()
        var capturedEvent: SmartAccountEvent? = null
        var capturedError: Throwable? = null

        emitter.setErrorHandler { event, error ->
            capturedEvent = event
            capturedError = error
        }

        val errorMessage = "Test error from listener"
        emitter.addListener { _ ->
            throw RuntimeException(errorMessage)
        }

        val event = SmartAccountEvent.TransactionSubmitted(
            hash = "abc123",
            success = false
        )
        emitter.emit(event)

        assertNotNull(capturedEvent)
        assertNotNull(capturedError)
        assertEquals(event, capturedEvent)
        assertEquals(errorMessage, capturedError!!.message)
    }

    @Test
    fun testSetErrorHandler_nullDisablesHandler() {
        val emitter = SmartAccountEventEmitter()
        var handlerCalled = false

        emitter.setErrorHandler { _, _ -> handlerCalled = true }
        emitter.setErrorHandler(null) // disable

        emitter.addListener { _ -> throw RuntimeException("Boom") }

        // Should not crash and should not call handler
        emitter.emit(SmartAccountEvent.WalletDisconnected(contractId = "C"))
        assertTrue(!handlerCalled)
    }

    // MARK: - listenerCount Tests

    @Test
    fun testListenerCount_noListenersReturnsZero() {
        val emitter = SmartAccountEventEmitter()
        assertEquals(0, emitter.listenerCount("WalletConnected"))
    }

    @Test
    fun testListenerCount_countsTypeSpecificListeners() {
        val emitter = SmartAccountEventEmitter()

        emitter.on<SmartAccountEvent.WalletConnected> { _ -> }
        emitter.on<SmartAccountEvent.WalletConnected> { _ -> }
        emitter.on<SmartAccountEvent.TransactionSubmitted> { _ -> }

        // WalletConnected has 2 type-specific, 0 global = 2
        assertEquals(2, emitter.listenerCount("WalletConnected"))
        // TransactionSubmitted has 1 type-specific, 0 global = 1
        assertEquals(1, emitter.listenerCount("TransactionSubmitted"))
    }

    @Test
    fun testListenerCount_includesGlobalListeners() {
        val emitter = SmartAccountEventEmitter()

        emitter.on<SmartAccountEvent.WalletConnected> { _ -> }
        emitter.addListener { _ -> } // global

        // WalletConnected: 1 type-specific + 1 global = 2
        assertEquals(2, emitter.listenerCount("WalletConnected"))

        // TransactionSubmitted: 0 type-specific + 1 global = 1
        assertEquals(1, emitter.listenerCount("TransactionSubmitted"))
    }

    // MARK: - Emit Isolation Tests

    @Test
    fun testEmitIsolation_typeSpecificOnlyReceivesMatchingEvents() {
        val emitter = SmartAccountEventEmitter()
        var walletConnectedCount = 0
        var txSubmittedCount = 0

        emitter.on<SmartAccountEvent.WalletConnected> { _ -> walletConnectedCount++ }
        emitter.on<SmartAccountEvent.TransactionSubmitted> { _ -> txSubmittedCount++ }

        emitter.emit(SmartAccountEvent.WalletConnected(
            contractId = "CONTRACT",
            credentialId = "cred"
        ))

        assertEquals(1, walletConnectedCount)
        assertEquals(0, txSubmittedCount, "TransactionSubmitted listener should not receive WalletConnected events")
    }

    @Test
    fun testEmitIsolation_globalAndTypedMixed() {
        val emitter = SmartAccountEventEmitter()
        var globalCount = 0
        var typedCount = 0

        emitter.addListener { _ -> globalCount++ }
        emitter.on<SmartAccountEvent.CredentialCreated> { _ -> typedCount++ }

        // Emit an event that does NOT match the typed listener
        emitter.emit(SmartAccountEvent.WalletDisconnected(contractId = "C"))

        assertEquals(1, globalCount, "Global listener should receive all events")
        assertEquals(0, typedCount, "Typed listener should not receive unmatched events")

        // Emit the matching event
        val credential = StoredCredential(
            credentialId = "cred-1",
            publicKey = ByteArray(65) { if (it == 0) 0x04.toByte() else it.toByte() },
            createdAt = 1700000000000L
        )
        emitter.emit(SmartAccountEvent.CredentialCreated(credential = credential))

        assertEquals(2, globalCount, "Global should now have 2 calls")
        assertEquals(1, typedCount, "Typed should now have 1 call")
    }

    // MARK: - removeAllListeners for Specific Event Type

    @Test
    fun testRemoveAllListeners_specificTypeOnly() {
        val emitter = SmartAccountEventEmitter()
        var walletCount = 0
        var txCount = 0

        emitter.on<SmartAccountEvent.WalletConnected> { _ -> walletCount++ }
        emitter.on<SmartAccountEvent.TransactionSubmitted> { _ -> txCount++ }

        // Remove only WalletConnected listeners
        emitter.removeAllListeners("WalletConnected")

        emitter.emit(SmartAccountEvent.WalletConnected(
            contractId = "C", credentialId = "c"
        ))
        emitter.emit(SmartAccountEvent.TransactionSubmitted(
            hash = "h", success = true
        ))

        assertEquals(0, walletCount, "WalletConnected listener should have been removed")
        assertEquals(1, txCount, "TransactionSubmitted listener should still work")
    }

    @Test
    fun testRemoveAllListeners_allTypesAndGlobal() {
        val emitter = SmartAccountEventEmitter()
        var count = 0

        emitter.on<SmartAccountEvent.WalletConnected> { _ -> count++ }
        emitter.on<SmartAccountEvent.TransactionSubmitted> { _ -> count++ }
        emitter.addListener { _ -> count++ }

        // Remove everything
        emitter.removeAllListeners()

        emitter.emit(SmartAccountEvent.WalletConnected(
            contractId = "C", credentialId = "c"
        ))
        emitter.emit(SmartAccountEvent.TransactionSubmitted(
            hash = "h", success = true
        ))

        assertEquals(0, count, "No listeners should remain after removeAllListeners()")
    }

    // MARK: - on<T>() Unsubscribe Tests

    @Test
    fun testOnUnsubscribe_stopsReceivingTypedEvents() {
        val emitter = SmartAccountEventEmitter()
        var count = 0

        val unsubscribe = emitter.on<SmartAccountEvent.SessionExpired> { _ -> count++ }

        emitter.emit(SmartAccountEvent.SessionExpired(
            contractId = "C", credentialId = "c"
        ))
        assertEquals(1, count)

        unsubscribe()

        emitter.emit(SmartAccountEvent.SessionExpired(
            contractId = "C2", credentialId = "c2"
        ))
        assertEquals(1, count, "Should not receive after unsubscribe")
    }

    // MARK: - SmartAccountEvent Data Class Tests

    @Test
    fun testWalletConnectedEvent() {
        val event = SmartAccountEvent.WalletConnected(
            contractId = "CABC",
            credentialId = "cred-id"
        )
        assertEquals("CABC", event.contractId)
        assertEquals("cred-id", event.credentialId)
    }

    @Test
    fun testWalletDisconnectedEvent() {
        val event = SmartAccountEvent.WalletDisconnected(contractId = "CXYZ")
        assertEquals("CXYZ", event.contractId)
    }

    @Test
    fun testCredentialDeletedEvent() {
        val event = SmartAccountEvent.CredentialDeleted(credentialId = "del-cred")
        assertEquals("del-cred", event.credentialId)
    }

    @Test
    fun testSessionExpiredEvent() {
        val event = SmartAccountEvent.SessionExpired(
            contractId = "CSESS",
            credentialId = "cred-sess"
        )
        assertEquals("CSESS", event.contractId)
        assertEquals("cred-sess", event.credentialId)
    }

    @Test
    fun testTransactionSignedEvent() {
        val event = SmartAccountEvent.TransactionSigned(
            contractId = "CTX",
            credentialId = "cred-tx"
        )
        assertEquals("CTX", event.contractId)
        assertEquals("cred-tx", event.credentialId)

        val eventWithNull = SmartAccountEvent.TransactionSigned(
            contractId = "CTX",
            credentialId = null
        )
        assertEquals(null, eventWithNull.credentialId)
    }

    @Test
    fun testTransactionSubmittedEvent() {
        val successEvent = SmartAccountEvent.TransactionSubmitted(
            hash = "tx-hash",
            success = true
        )
        assertEquals("tx-hash", successEvent.hash)
        assertTrue(successEvent.success)

        val failEvent = SmartAccountEvent.TransactionSubmitted(
            hash = "fail-hash",
            success = false
        )
        assertTrue(!failEvent.success)
    }

    @Test
    fun testCredentialCreatedEvent() {
        val credential = StoredCredential(
            credentialId = "new-cred",
            publicKey = ByteArray(65) { if (it == 0) 0x04.toByte() else it.toByte() },
            createdAt = 1700000000000L,
            nickname = "Test Key"
        )
        val event = SmartAccountEvent.CredentialCreated(credential = credential)
        assertEquals("new-cred", event.credential.credentialId)
        assertEquals("Test Key", event.credential.nickname)
    }
}
