package com.soneso.stellar.sdk.contract

import com.soneso.stellar.sdk.rpc.SorobanServer
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext

/**
 * Coroutine context element that supplies the servers [ContractClient] creates for its
 * deployment and loading calls. Without it, each call creates `SorobanServer(rpcUrl)`.
 */
internal class SorobanServerFactory(
    val create: (rpcUrl: String) -> SorobanServer
) : AbstractCoroutineContextElement(Key) {
    companion object Key : CoroutineContext.Key<SorobanServerFactory>
}

/**
 * Returns a new server for [rpcUrl], from the [SorobanServerFactory] of the current coroutine
 * context when one is present.
 */
internal suspend fun sorobanServerFor(rpcUrl: String): SorobanServer {
    val factory = coroutineContext[SorobanServerFactory] ?: return SorobanServer(rpcUrl)
    return factory.create(rpcUrl)
}
