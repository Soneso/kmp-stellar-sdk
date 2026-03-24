package com.soneso.smartdemo.platform

import java.io.IOException

/**
 * Android implementation for loading WASM resources.
 *
 * Android packages commonMain/resources/wasm/ files into the APK. At runtime, the Thread
 * context ClassLoader has access to the merged resources from all modules in the application,
 * making ClassLoader.getResourceAsStream() the correct approach for cross-module resource access.
 *
 * This function is marked suspend for API consistency with the JS implementation, but does
 * not actually suspend on Android — it executes synchronously with zero overhead.
 */
actual suspend fun loadWasmResource(wasmFilename: String): ByteArray {
    return try {
        // Thread context ClassLoader has access to the merged APK resources, which includes
        // files from all modules. Using it instead of the class's own classloader ensures
        // resources from dependency modules (like the shared KMP module) are found correctly.
        val classLoader = Thread.currentThread().contextClassLoader
            ?: object {}.javaClass.classLoader
            ?: ClassLoader.getSystemClassLoader()

        val resourceStream = classLoader.getResourceAsStream("wasm/$wasmFilename")
            ?: throw IllegalArgumentException(
                "WASM file not found in Android resources: '$wasmFilename'. " +
                "Expected APK path: wasm/$wasmFilename. " +
                "Ensure the file is present in commonMain/resources/wasm/ and that " +
                "the build.gradle.kts sourceSets configuration includes it in the Android build."
            )

        resourceStream.use { it.readBytes() }
    } catch (e: IOException) {
        throw IllegalArgumentException("Failed to read WASM file '$wasmFilename': ${e.message}", e)
    } catch (e: IllegalArgumentException) {
        throw e
    } catch (e: Exception) {
        throw IllegalArgumentException(
            "Unexpected error loading WASM file '$wasmFilename': ${e.message}", e
        )
    }
}
