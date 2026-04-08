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
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
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
 * - once<T>() auto-unsubscribe and pre-fire cancellation
 * - Concurrent/rapid emission safety
 * - removeAllListeners(eventType) does not affect global listeners
 * - Edge cases: emit with no listeners, removeAllListeners when empty
 * - Data class equality, hashCode, copy
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

    // MARK: - once<T>() Tests

    @Test
    fun testOnce_firesOnFirstEventOnly() {
        val emitter = SmartAccountEventEmitter()
        val receivedEvents = mutableListOf<SmartAccountEvent.WalletConnected>()

        emitter.once<SmartAccountEvent.WalletConnected> { event ->
            receivedEvents.add(event)
        }

        val event1 = SmartAccountEvent.WalletConnected(contractId = "C1", credentialId = "cr1")
        val event2 = SmartAccountEvent.WalletConnected(contractId = "C2", credentialId = "cr2")

        emitter.emit(event1)
        emitter.emit(event2)

        assertEquals(1, receivedEvents.size, "once listener should fire exactly once")
        assertEquals("C1", receivedEvents[0].contractId, "once listener should receive the first event")
    }

    @Test
    fun testOnce_unsubscribeBeforeEventFiresCancels() {
        val emitter = SmartAccountEventEmitter()
        var callCount = 0

        val unsubscribe = emitter.once<SmartAccountEvent.WalletDisconnected> { _ ->
            callCount++
        }

        // Cancel before any event fires
        unsubscribe()

        emitter.emit(SmartAccountEvent.WalletDisconnected(contractId = "C"))

        assertEquals(0, callCount, "once listener cancelled before firing should never fire")
    }

    @Test
    fun testOnce_listenerCountDecrementsAfterFiring() {
        val emitter = SmartAccountEventEmitter()

        emitter.once<SmartAccountEvent.TransactionSubmitted> { _ -> }

        assertEquals(1, emitter.listenerCount("TransactionSubmitted"))

        emitter.emit(SmartAccountEvent.TransactionSubmitted(hash = "h", success = true))

        assertEquals(0, emitter.listenerCount("TransactionSubmitted"),
            "Listener count should decrement after once listener auto-unsubscribes")
    }

    @Test
    fun testOnce_multipleOnceListenersForSameType() {
        val emitter = SmartAccountEventEmitter()
        var count1 = 0
        var count2 = 0

        emitter.once<SmartAccountEvent.SessionExpired> { _ -> count1++ }
        emitter.once<SmartAccountEvent.SessionExpired> { _ -> count2++ }

        assertEquals(2, emitter.listenerCount("SessionExpired"))

        emitter.emit(SmartAccountEvent.SessionExpired(contractId = "C", credentialId = "cr"))

        assertEquals(1, count1, "First once listener should fire once")
        assertEquals(1, count2, "Second once listener should fire once")

        emitter.emit(SmartAccountEvent.SessionExpired(contractId = "C2", credentialId = "cr2"))

        assertEquals(1, count1, "First once listener should not fire again")
        assertEquals(1, count2, "Second once listener should not fire again")
        assertEquals(0, emitter.listenerCount("SessionExpired"),
            "All once listeners should be removed after firing")
    }

    @Test
    fun testOnce_doesNotAffectOtherEventTypes() {
        val emitter = SmartAccountEventEmitter()
        var onceCount = 0
        var permanentCount = 0

        emitter.once<SmartAccountEvent.WalletConnected> { _ -> onceCount++ }
        emitter.on<SmartAccountEvent.WalletDisconnected> { _ -> permanentCount++ }

        emitter.emit(SmartAccountEvent.WalletConnected(contractId = "C", credentialId = "cr"))
        emitter.emit(SmartAccountEvent.WalletDisconnected(contractId = "C"))
        emitter.emit(SmartAccountEvent.WalletDisconnected(contractId = "C2"))

        assertEquals(1, onceCount)
        assertEquals(2, permanentCount, "Permanent listener should still receive all events")
    }

    // MARK: - Error Handler with once Tests

    @Test
    fun testOnce_listenerThrowsOnFirstEvent_errorHandlerCalled() {
        val emitter = SmartAccountEventEmitter()
        var errorHandlerCalled = false
        var capturedError: Throwable? = null

        emitter.setErrorHandler { _, error ->
            errorHandlerCalled = true
            capturedError = error
        }

        emitter.once<SmartAccountEvent.TransactionSubmitted> { _ ->
            throw IllegalStateException("Listener failure on first event")
        }

        emitter.emit(SmartAccountEvent.TransactionSubmitted(hash = "h1", success = true))

        assertTrue(errorHandlerCalled, "Error handler should be called when once listener throws")
        assertEquals("Listener failure on first event", capturedError?.message)
    }

    @Test
    fun testOnce_listenerThrowsOnFirstEvent_stillAutoUnsubscribes() {
        val emitter = SmartAccountEventEmitter()
        var callCount = 0

        emitter.setErrorHandler { _, _ -> /* suppress */ }

        emitter.once<SmartAccountEvent.WalletDisconnected> { _ ->
            callCount++
            throw RuntimeException("Boom")
        }

        emitter.emit(SmartAccountEvent.WalletDisconnected(contractId = "C1"))
        emitter.emit(SmartAccountEvent.WalletDisconnected(contractId = "C2"))

        // The once implementation calls unsubscribe() before the listener.
        // So even though the listener throws, it should still auto-unsubscribe.
        assertEquals(1, callCount,
            "once listener should fire exactly once even when it throws")
        assertEquals(0, emitter.listenerCount("WalletDisconnected"),
            "once listener should be removed even when it throws")
    }

    @Test
    fun testErrorHandler_failingTypedListenerDoesNotAffectGlobalListener() {
        val emitter = SmartAccountEventEmitter()
        var globalCalled = false

        emitter.setErrorHandler { _, _ -> /* suppress */ }

        emitter.on<SmartAccountEvent.WalletConnected> { _ ->
            throw RuntimeException("Typed listener failure")
        }
        emitter.addListener { _ -> globalCalled = true }

        emitter.emit(SmartAccountEvent.WalletConnected(contractId = "C", credentialId = "cr"))

        assertTrue(globalCalled,
            "Global listener should still be called when typed listener throws")
    }

    // MARK: - removeAllListeners(eventType) Does Not Remove Global Listeners

    @Test
    fun testRemoveAllListeners_specificType_doesNotRemoveGlobalListeners() {
        val emitter = SmartAccountEventEmitter()
        var globalCount = 0
        var typedCount = 0

        emitter.addListener { _ -> globalCount++ }
        emitter.on<SmartAccountEvent.WalletConnected> { _ -> typedCount++ }

        // Remove only WalletConnected type-specific listeners
        emitter.removeAllListeners("WalletConnected")

        emitter.emit(SmartAccountEvent.WalletConnected(contractId = "C", credentialId = "cr"))

        assertEquals(0, typedCount, "Typed listener should have been removed")
        assertEquals(1, globalCount,
            "Global listener should NOT be removed by removeAllListeners(eventType)")
    }

    @Test
    fun testRemoveAllListeners_specificType_globalListenerCountUnchanged() {
        val emitter = SmartAccountEventEmitter()

        emitter.addListener { _ -> }
        emitter.on<SmartAccountEvent.WalletConnected> { _ -> }

        // Before removal: 1 type-specific + 1 global = 2
        assertEquals(2, emitter.listenerCount("WalletConnected"))

        emitter.removeAllListeners("WalletConnected")

        // After removal: 0 type-specific + 1 global = 1
        assertEquals(1, emitter.listenerCount("WalletConnected"),
            "Global listener should still be counted after removeAllListeners(eventType)")
    }

    // MARK: - Edge Cases

    @Test
    fun testEmit_withNoListeners_doesNotThrow() {
        val emitter = SmartAccountEventEmitter()

        // Should not throw
        emitter.emit(SmartAccountEvent.WalletConnected(contractId = "C", credentialId = "cr"))
        emitter.emit(SmartAccountEvent.TransactionSubmitted(hash = "h", success = true))
        emitter.emit(SmartAccountEvent.CredentialDeleted(credentialId = "cr"))
        emitter.emit(SmartAccountEvent.SessionExpired(contractId = "C", credentialId = "cr"))
        emitter.emit(SmartAccountEvent.TransactionSigned(contractId = "C", credentialId = null))
        emitter.emit(SmartAccountEvent.WalletDisconnected(contractId = "C"))

        val credential = StoredCredential(
            credentialId = "cr",
            publicKey = ByteArray(65) { if (it == 0) 0x04.toByte() else it.toByte() },
            createdAt = 1700000000000L
        )
        emitter.emit(SmartAccountEvent.CredentialCreated(credential = credential))

        // Reaching this point without exception confirms success
        assertTrue(true)
    }

    @Test
    fun testRemoveAllListeners_whenAlreadyEmpty_doesNotThrow() {
        val emitter = SmartAccountEventEmitter()

        // No listeners registered, should not throw
        emitter.removeAllListeners()
        emitter.removeAllListeners("WalletConnected")
        emitter.removeAllListeners("NonExistentType")

        assertTrue(true)
    }

    @Test
    fun testUnsubscribe_calledMultipleTimes_doesNotThrow() {
        val emitter = SmartAccountEventEmitter()

        val unsubscribe = emitter.on<SmartAccountEvent.WalletConnected> { _ -> }

        unsubscribe()
        // Calling unsubscribe again should be safe (idempotent)
        unsubscribe()

        assertEquals(0, emitter.listenerCount("WalletConnected"))
    }

    @Test
    fun testAddListenerUnsubscribe_calledMultipleTimes_doesNotThrow() {
        val emitter = SmartAccountEventEmitter()

        val unsubscribe = emitter.addListener { _ -> }

        unsubscribe()
        unsubscribe()

        // Verify no global listeners remain
        assertEquals(0, emitter.listenerCount("AnyType"))
    }

    // MARK: - Rapid/Sequential Emission Tests

    @Test
    fun testRapidEmission_allEventsDeliveredInOrder() {
        val emitter = SmartAccountEventEmitter()
        val receivedHashes = mutableListOf<String>()

        emitter.on<SmartAccountEvent.TransactionSubmitted> { event ->
            receivedHashes.add(event.hash)
        }

        val count = 100
        for (i in 0 until count) {
            emitter.emit(SmartAccountEvent.TransactionSubmitted(hash = "tx-$i", success = true))
        }

        assertEquals(count, receivedHashes.size, "All $count events should be delivered")
        for (i in 0 until count) {
            assertEquals("tx-$i", receivedHashes[i], "Events should arrive in emission order")
        }
    }

    @Test
    fun testRapidEmission_mixedEventTypes() {
        val emitter = SmartAccountEventEmitter()
        val allEvents = mutableListOf<SmartAccountEvent>()

        emitter.addListener { event -> allEvents.add(event) }

        for (i in 0 until 50) {
            emitter.emit(SmartAccountEvent.WalletConnected(contractId = "C$i", credentialId = "cr$i"))
            emitter.emit(SmartAccountEvent.TransactionSubmitted(hash = "tx-$i", success = i % 2 == 0))
        }

        assertEquals(100, allEvents.size, "All 100 mixed events should be delivered")
        // Verify alternating pattern
        for (i in 0 until 50) {
            assertTrue(allEvents[i * 2] is SmartAccountEvent.WalletConnected)
            assertTrue(allEvents[i * 2 + 1] is SmartAccountEvent.TransactionSubmitted)
        }
    }

    // MARK: - Data Class Equality and Properties Tests

    @Test
    fun testWalletConnected_equalityAndCopy() {
        val event1 = SmartAccountEvent.WalletConnected(contractId = "C1", credentialId = "cr1")
        val event2 = SmartAccountEvent.WalletConnected(contractId = "C1", credentialId = "cr1")
        val event3 = SmartAccountEvent.WalletConnected(contractId = "C2", credentialId = "cr1")

        assertEquals(event1, event2, "Same properties should be equal")
        assertEquals(event1.hashCode(), event2.hashCode(), "Equal objects should have equal hashCode")
        assertNotEquals(event1, event3, "Different contractId should not be equal")

        val copied = event1.copy(credentialId = "cr-new")
        assertEquals("C1", copied.contractId, "Copy should preserve unchanged fields")
        assertEquals("cr-new", copied.credentialId, "Copy should update specified field")
    }

    @Test
    fun testWalletDisconnected_equalityAndCopy() {
        val event1 = SmartAccountEvent.WalletDisconnected(contractId = "C1")
        val event2 = SmartAccountEvent.WalletDisconnected(contractId = "C1")
        val event3 = SmartAccountEvent.WalletDisconnected(contractId = "C2")

        assertEquals(event1, event2)
        assertEquals(event1.hashCode(), event2.hashCode())
        assertNotEquals(event1, event3)

        val copied = event1.copy(contractId = "C-new")
        assertEquals("C-new", copied.contractId)
    }

    @Test
    fun testTransactionSubmitted_equalityAndCopy() {
        val event1 = SmartAccountEvent.TransactionSubmitted(hash = "h1", success = true)
        val event2 = SmartAccountEvent.TransactionSubmitted(hash = "h1", success = true)
        val event3 = SmartAccountEvent.TransactionSubmitted(hash = "h1", success = false)

        assertEquals(event1, event2)
        assertEquals(event1.hashCode(), event2.hashCode())
        assertNotEquals(event1, event3, "Different success value should not be equal")

        val copied = event1.copy(success = false)
        assertEquals("h1", copied.hash)
        assertFalse(copied.success)
    }

    @Test
    fun testTransactionSigned_equalityWithNullCredential() {
        val event1 = SmartAccountEvent.TransactionSigned(contractId = "C1", credentialId = null)
        val event2 = SmartAccountEvent.TransactionSigned(contractId = "C1", credentialId = null)
        val event3 = SmartAccountEvent.TransactionSigned(contractId = "C1", credentialId = "cr")

        assertEquals(event1, event2, "Both with null credentialId should be equal")
        assertNotEquals(event1, event3, "Null vs non-null credentialId should not be equal")
    }

    @Test
    fun testSessionExpired_equalityAndCopy() {
        val event1 = SmartAccountEvent.SessionExpired(contractId = "C1", credentialId = "cr1")
        val event2 = SmartAccountEvent.SessionExpired(contractId = "C1", credentialId = "cr1")

        assertEquals(event1, event2)
        assertEquals(event1.hashCode(), event2.hashCode())

        val copied = event1.copy(contractId = "C-new")
        assertEquals("C-new", copied.contractId)
        assertEquals("cr1", copied.credentialId)
    }

    @Test
    fun testCredentialDeleted_equalityAndCopy() {
        val event1 = SmartAccountEvent.CredentialDeleted(credentialId = "cr1")
        val event2 = SmartAccountEvent.CredentialDeleted(credentialId = "cr1")
        val event3 = SmartAccountEvent.CredentialDeleted(credentialId = "cr2")

        assertEquals(event1, event2)
        assertNotEquals(event1, event3)

        val copied = event1.copy(credentialId = "cr-new")
        assertEquals("cr-new", copied.credentialId)
    }

    @Test
    fun testDifferentEventTypes_areNeverEqual() {
        val connected = SmartAccountEvent.WalletConnected(contractId = "C", credentialId = "cr")
        val disconnected = SmartAccountEvent.WalletDisconnected(contractId = "C")
        val expired = SmartAccountEvent.SessionExpired(contractId = "C", credentialId = "cr")

        assertNotEquals<SmartAccountEvent>(connected, disconnected,
            "Different event types should never be equal")
        assertNotEquals<SmartAccountEvent>(connected, expired,
            "Different event types should never be equal even with same properties")
    }

    // MARK: - Listener Interaction During Emission

    @Test
    fun testListener_canUnsubscribeItselfDuringEmission() {
        val emitter = SmartAccountEventEmitter()
        var callCount = 0

        lateinit var unsub: () -> Unit
        unsub = emitter.on<SmartAccountEvent.WalletDisconnected> { _ ->
            callCount++
            unsub()
        }

        emitter.emit(SmartAccountEvent.WalletDisconnected(contractId = "C1"))
        emitter.emit(SmartAccountEvent.WalletDisconnected(contractId = "C2"))

        assertEquals(1, callCount,
            "Listener that unsubscribes itself during emission should fire once")
    }

    @Test
    fun testOnce_combinedWithPermanentListener() {
        val emitter = SmartAccountEventEmitter()
        var onceCount = 0
        var permanentCount = 0

        emitter.once<SmartAccountEvent.TransactionSubmitted> { _ -> onceCount++ }
        emitter.on<SmartAccountEvent.TransactionSubmitted> { _ -> permanentCount++ }

        emitter.emit(SmartAccountEvent.TransactionSubmitted(hash = "tx1", success = true))
        emitter.emit(SmartAccountEvent.TransactionSubmitted(hash = "tx2", success = true))
        emitter.emit(SmartAccountEvent.TransactionSubmitted(hash = "tx3", success = true))

        assertEquals(1, onceCount, "once listener should fire exactly once")
        assertEquals(3, permanentCount, "Permanent listener should fire for all events")
    }
}
