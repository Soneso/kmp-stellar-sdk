//
//  OZSmartAccountEvents.kt
//  Stellar SDK Kotlin Multiplatform
//
//  Copyright (c) 2026 Soneso. All rights reserved.
//

package com.soneso.stellar.sdk.smartaccount.oz
import com.soneso.stellar.sdk.platformSynchronized
import kotlin.concurrent.Volatile

/**
 * Events emitted by the Smart Account Kit during wallet lifecycle operations.
 *
 * These events provide hooks for monitoring and responding to key operations:
 * - Wallet connection and disconnection
 * - Credential lifecycle (creation, deletion)
 * - Transaction lifecycle (signing, submission)
 * - Session management (expiration)
 *
 * Example:
 * ```kotlin
 * kit.events.addListener { event ->
 *     when (event) {
 *         is SmartAccountEvent.WalletConnected ->
 *             println("Connected to ${event.contractId}")
 *         is SmartAccountEvent.TransactionSubmitted ->
 *             println("Transaction ${event.hash} submitted")
 *         else -> {}
 *     }
 * }
 * ```
 */
sealed class SmartAccountEvent {
    /**
     * Emitted when a wallet is connected.
     *
     * This event is fired when connecting to an existing wallet, either through
     * automatic session restoration or an explicit wallet connection call.
     *
     * @property contractId The smart account contract address (C-address)
     * @property credentialId The Base64URL-encoded credential ID
     */
    data class WalletConnected(
        val contractId: String,
        val credentialId: String
    ) : SmartAccountEvent()

    /**
     * Emitted when a wallet is disconnected.
     *
     * This event is fired when disconnect() is called. The session is cleared,
     * but stored credentials remain for future reconnection.
     *
     * @property contractId The smart account contract address that was disconnected
     */
    data class WalletDisconnected(
        val contractId: String
    ) : SmartAccountEvent()

    /**
     * Emitted when a new credential is created (passkey registered).
     *
     * This event is fired after successful WebAuthn credential creation, whether
     * during initial wallet setup or when adding a new signer to an existing wallet.
     * Note that the wallet may not be deployed yet.
     *
     * @property credential The stored credential data
     */
    data class CredentialCreated(
        val credential: StoredCredential
    ) : SmartAccountEvent()

    /**
     * Emitted when a credential is deleted from storage.
     *
     * This event is fired when a credential is removed via the credential management API. If the credential
     * was connected, the wallet is automatically disconnected first.
     *
     * @property credentialId The Base64URL-encoded credential ID
     */
    data class CredentialDeleted(
        val credentialId: String
    ) : SmartAccountEvent()

    /**
     * Emitted when a session expires during connection attempt.
     *
     * This event is fired when attempting to restore a session that has expired.
     * The application should prompt the user to reconnect.
     *
     * @property contractId The smart account contract address
     * @property credentialId The Base64URL-encoded credential ID
     */
    data class SessionExpired(
        val contractId: String,
        val credentialId: String
    ) : SmartAccountEvent()

    /**
     * Emitted when a transaction is signed.
     *
     * This event is fired after successfully collecting all required signatures
     * for a transaction, before submission to the network.
     *
     * @property contractId The smart account contract address
     * @property credentialId The credential ID used for signing (null if only external signers)
     */
    data class TransactionSigned(
        val contractId: String,
        val credentialId: String?
    ) : SmartAccountEvent()

    /**
     * Emitted when a transaction is submitted to the network.
     *
     * This event is fired after sending the signed transaction to Soroban RPC
     * or the relayer service. The success flag indicates whether the transaction
     * was successfully sent to the network node, not whether it was included in
     * a ledger.
     *
     * @property hash The transaction hash
     * @property success True if submitted successfully, false if submission failed
     */
    data class TransactionSubmitted(
        val hash: String,
        val success: Boolean
    ) : SmartAccountEvent()
}

/**
 * Listener interface for Smart Account events.
 *
 * Implement this functional interface to receive event notifications.
 * The interface uses Kotlin's fun interface syntax, allowing lambda expressions.
 *
 * Example:
 * ```kotlin
 * val listener = SmartAccountEventListener { event ->
 *     println("Received event: $event")
 * }
 * kit.events.addListener(listener)
 * ```
 */
fun interface SmartAccountEventListener {
    /**
     * Called when an event is emitted.
     *
     * @param event The event that occurred
     */
    fun onEvent(event: SmartAccountEvent)
}

/**
 * Event emitter for Smart Account lifecycle events.
 *
 * This class manages event subscriptions and dispatches events to all registered
 * listeners. It provides thread-safe subscription management and error handling.
 *
 * All synchronization uses [platformSynchronized] on the internal listeners map as the
 * single lock. Event emission snapshots the listener list under the lock, then invokes
 * listeners outside the lock to avoid holding the lock during callbacks.
 *
 * Features:
 * - Thread-safe listener management with platformSynchronized
 * - Multiple listeners per event type
 * - Error isolation (one failing listener does not affect others)
 * - Optional error handler for debugging listener failures
 * - Public addListener() for Java/Swift/non-Kotlin callers
 *
 * Example:
 * ```kotlin
 * val emitter = SmartAccountEventEmitter()
 *
 * // Type-safe subscription (Kotlin only, uses reified generics)
 * val unsubscribe = emitter.on<SmartAccountEvent.WalletConnected> { event ->
 *     println("Connected to ${event.contractId}")
 * }
 *
 * // Non-generic subscription (works from Java, Swift, etc.)
 * val unsub = emitter.addListener { event ->
 *     when (event) {
 *         is SmartAccountEvent.WalletConnected ->
 *             println("Connected to ${event.contractId}")
 *         else -> {}
 *     }
 * }
 *
 * // One-time listener
 * emitter.once<SmartAccountEvent.TransactionSubmitted> { event ->
 *     println("First transaction: ${event.hash}")
 * }
 *
 * // Remove listener
 * unsubscribe()
 * ```
 */
class SmartAccountEventEmitter {
    /**
     * Map of event types to their registered listeners.
     * All access must be synchronized on this map.
     */
    private val listeners = mutableMapOf<String, MutableSet<SmartAccountEventListener>>()

    /**
     * Global listeners that receive all event types.
     * All access must be synchronized on [listeners].
     */
    private val globalListeners = mutableSetOf<SmartAccountEventListener>()

    /**
     * Optional error handler for listener errors.
     *
     * By default, listener errors are silently caught to prevent one failing
     * listener from affecting others. Set this handler to receive error notifications.
     *
     * Example:
     * ```kotlin
     * emitter.setErrorHandler { event, error ->
     *     println("Listener error for ${event::class.simpleName}: $error")
     * }
     * ```
     */
    @Volatile
    private var errorHandler: ((event: SmartAccountEvent, error: Throwable) -> Unit)? = null

    /**
     * Sets the error handler for listener errors.
     *
     * The error handler receives the event that was being dispatched and the
     * exception thrown by the failing listener.
     *
     * @param handler Error handler function, or null to disable
     */
    fun setErrorHandler(handler: ((event: SmartAccountEvent, error: Throwable) -> Unit)?) {
        errorHandler = handler
    }

    /**
     * Subscribes a listener to receive all Smart Account events.
     *
     * Unlike [on], this method does not use reified type parameters, making it
     * callable from Java, Swift, and other non-Kotlin languages. The listener
     * receives all event types and must dispatch internally using when/instanceof.
     *
     * @param listener The event listener to register
     * @return A function that unsubscribes the listener when called
     *
     * Example:
     * ```kotlin
     * val unsubscribe = emitter.addListener { event ->
     *     when (event) {
     *         is SmartAccountEvent.WalletConnected ->
     *             println("Wallet ${event.contractId} connected")
     *         is SmartAccountEvent.TransactionSubmitted ->
     *             println("Transaction ${event.hash}: success=${event.success}")
     *         else -> {}
     *     }
     * }
     * // Later: unsubscribe()
     * ```
     */
    fun addListener(listener: SmartAccountEventListener): () -> Unit {
        platformSynchronized(listeners) {
            globalListeners.add(listener)
        }
        return {
            platformSynchronized(listeners) {
                globalListeners.remove(listener)
            }
        }
    }

    /**
     * Subscribes to events of a specific type.
     *
     * The listener will be called whenever an event of the specified type is emitted.
     * The returned unsubscribe function can be called to remove the listener.
     *
     * This method is type-safe: the listener parameter is constrained to match
     * the event type through Kotlin's reified type parameter.
     *
     * Note: This method uses reified generics and cannot be called from Java or Swift.
     * Use [addListener] for cross-language compatibility.
     *
     * @param T The event type to subscribe to
     * @param listener The callback function to invoke on events
     * @return A function that unsubscribes the listener when called
     *
     * Example:
     * ```kotlin
     * val unsubscribe = emitter.on<SmartAccountEvent.WalletConnected> { event ->
     *     println("Wallet ${event.contractId} connected")
     * }
     * // Later: unsubscribe()
     * ```
     */
    inline fun <reified T : SmartAccountEvent> on(
        crossinline listener: (T) -> Unit
    ): () -> Unit {
        val eventType = T::class.simpleName ?: "Unknown"
        val wrapper = SmartAccountEventListener { event ->
            if (event is T) {
                listener(event)
            }
        }

        addListenerInternal(eventType, wrapper)

        return {
            removeListenerInternal(eventType, wrapper)
        }
    }

    /**
     * Subscribes to an event, but only triggers once.
     *
     * The listener will be automatically unsubscribed after the first event.
     * Useful for waiting for a single occurrence of an event.
     *
     * @param T The event type to subscribe to
     * @param listener The callback function to invoke once
     * @return A function that unsubscribes the listener if called before the event fires
     *
     * Example:
     * ```kotlin
     * emitter.once<SmartAccountEvent.TransactionSubmitted> { event ->
     *     println("First transaction submitted: ${event.hash}")
     * }
     * ```
     */
    inline fun <reified T : SmartAccountEvent> once(
        crossinline listener: (T) -> Unit
    ): () -> Unit {
        lateinit var unsubscribe: () -> Unit
        unsubscribe = on<T> { event ->
            unsubscribe()
            listener(event)
        }
        return unsubscribe
    }

    /**
     * Removes all listeners for a specific event type, or all listeners if no type is specified.
     *
     * When an eventType is specified, only type-specific listeners registered via [on] are
     * removed. Global listeners registered via [addListener] are not affected. When null,
     * both type-specific and global listeners are removed.
     *
     * @param eventType The event class name, or null to remove all listeners
     *
     * Example:
     * ```kotlin
     * // Remove all WalletConnected listeners
     * emitter.removeAllListeners("WalletConnected")
     *
     * // Remove all listeners for all event types
     * emitter.removeAllListeners()
     * ```
     */
    fun removeAllListeners(eventType: String? = null) {
        platformSynchronized(listeners) {
            if (eventType != null) {
                listeners.remove(eventType)
            } else {
                listeners.clear()
                globalListeners.clear()
            }
        }
    }

    /**
     * Returns the number of listeners for a specific event type.
     *
     * This count includes both type-specific listeners registered via [on] and
     * global listeners registered via [addListener].
     *
     * @param eventType The event class name
     * @return The number of registered listeners (type-specific + global)
     *
     * Example:
     * ```kotlin
     * val count = emitter.listenerCount("WalletConnected")
     * println("$count listeners for WalletConnected")
     * ```
     */
    fun listenerCount(eventType: String): Int {
        return platformSynchronized(listeners) {
            (listeners[eventType]?.size ?: 0) + globalListeners.size
        }
    }

    /**
     * Emits an event to all registered listeners.
     *
     * This method dispatches the event to all type-specific listeners registered for the
     * event's type, plus all global listeners registered via [addListener]. The listener
     * list is snapshotted under the lock, then listeners are invoked outside the lock.
     *
     * Listener errors are caught and optionally passed to the error handler to prevent
     * one failing listener from affecting others.
     *
     * This is an internal method called by the kit's operation modules.
     *
     * @param event The event to emit
     */
    internal fun emit(event: SmartAccountEvent) {
        val eventType = event::class.simpleName ?: return
        val currentListeners: List<SmartAccountEventListener>
        currentListeners = platformSynchronized(listeners) {
            val typeSpecific = listeners[eventType]?.toList() ?: emptyList()
            val global = globalListeners.toList()
            typeSpecific + global
        }

        currentListeners.forEach { listener ->
            try {
                listener.onEvent(event)
            } catch (err: Throwable) {
                // Call error handler if provided, otherwise silently catch
                // to prevent one failing listener from affecting others
                errorHandler?.invoke(event, err)
            }
        }
    }

    /**
     * Internal helper to add a listener for a specific event type.
     *
     * @param eventType The event class name (e.g., "WalletConnected")
     * @param listener The listener to register
     */
    @PublishedApi
    internal fun addListenerInternal(eventType: String, listener: SmartAccountEventListener) {
        platformSynchronized(listeners) {
            listeners.getOrPut(eventType) { mutableSetOf() }.add(listener)
        }
    }

    /**
     * Internal helper to remove a listener for a specific event type.
     *
     * @param eventType The event class name (e.g., "WalletConnected")
     * @param listener The listener to unregister
     */
    @PublishedApi
    internal fun removeListenerInternal(eventType: String, listener: SmartAccountEventListener) {
        platformSynchronized(listeners) {
            listeners[eventType]?.remove(listener)
        }
    }
}
