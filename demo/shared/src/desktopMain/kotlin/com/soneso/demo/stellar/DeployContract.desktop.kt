package com.soneso.demo.stellar

import java.io.IOException

/**
 * Desktop (JVM) implementation for loading WASM resources.
 *
 * Uses Java ClassLoader to read WASM files from the resources/wasm directory.
 * This works in both development and production JAR distributions.
 *
 * Note: This is marked as suspend for consistency with the JavaScript implementation,
 * but it doesn't actually suspend on Desktop JVM (zero overhead).
 */
actual suspend fun loadWasmResource(wasmFilename: String): ByteArray {
    return try {
        // Try reading from classpath using ClassLoader
        val resourceStream = object {}.javaClass.classLoader.getResourceAsStream("wasm/$wasmFilename")
        if (resourceStream != null) {
            return resourceStream.readBytes()
        }

        // If not found in classpath, throw clear error
        throw IllegalArgumentException(
            "WASM file not found in resources: '$wasmFilename'. " +
            "Expected location: resources/wasm/$wasmFilename"
        )
    } catch (e: IOException) {
        throw IllegalArgumentException("Failed to read WASM file '$wasmFilename': ${e.message}", e)
    }
}
