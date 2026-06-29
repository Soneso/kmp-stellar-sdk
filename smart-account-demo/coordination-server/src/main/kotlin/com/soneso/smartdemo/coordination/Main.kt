package com.soneso.smartdemo.coordination

import kotlinx.coroutines.runBlocking
import kotlin.system.exitProcess

/**
 * Entry point for the coordination server.
 *
 * Resolves configuration, loads any persisted store, binds `0.0.0.0:<port>`, and
 * serves until interrupted. Exits with code 64 on a configuration error and 70
 * on a store-load failure, so a supervisor can distinguish a misconfigured
 * launch from a runtime crash.
 */
fun main(args: Array<String>) {
    val config = try {
        ServerConfig.resolve(args.toList(), System.getenv())
    } catch (e: ConfigException) {
        System.err.println("Configuration error: ${e.message}")
        exitProcess(64)
    }

    val store = RequestStore(storePath = config.storePath)
    try {
        runBlocking { store.load() }
    } catch (e: Exception) {
        System.err.println("Failed to load store \"${config.storePath ?: ""}\": ${e.message}")
        exitProcess(70)
    }

    println("coordination-server listening on http://0.0.0.0:${config.port}")
    if (config.storePath != null) {
        println("Persisting requests to ${config.storePath}")
    } else {
        println("Running in-memory only (no --store configured)")
    }

    buildServer(store, config.token, config.port).start(wait = true)
}
