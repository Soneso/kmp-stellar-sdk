package com.soneso.demo.stellar

import java.io.IOException

/**
 * Android implementation for loading WASM resources.
 *
 * Uses the Thread context ClassLoader to access WASM files packaged in the APK.
 * The WASM files from commonMain/resources/wasm/ are included in the Android build
 * via the sourceSets configuration in build.gradle.kts.
 *
 * Android packages these resources into the APK, making them accessible via
 * ClassLoader.getResourceAsStream() at runtime. The Thread context ClassLoader
 * is used because it has access to the merged resources from all modules in
 * the Android application.
 *
 * Note: This is marked as suspend for consistency with the JavaScript implementation,
 * but it doesn't actually suspend on Android (zero overhead).
 */
actual suspend fun loadWasmResource(wasmFilename: String): ByteArray {
    return try {
        // Use Thread context ClassLoader to access resources from the merged APK
        val classLoader = Thread.currentThread().contextClassLoader
            ?: object {}.javaClass.classLoader
            ?: ClassLoader.getSystemClassLoader()

        // Try to load the WASM file from the wasm/ directory
        val resourceStream = classLoader.getResourceAsStream("wasm/$wasmFilename")

        if (resourceStream != null) {
            return resourceStream.use { it.readBytes() }
        }

        // If not found, throw clear error
        throw IllegalArgumentException(
            "WASM file not found in Android resources: '$wasmFilename'. " +
            "Expected location: wasm/$wasmFilename. " +
            "Ensure the WASM files are properly packaged in the Android APK " +
            "(check build.gradle.kts sourceSets configuration)."
        )
    } catch (e: IOException) {
        throw IllegalArgumentException("Failed to read WASM file '$wasmFilename': ${e.message}", e)
    } catch (e: IllegalArgumentException) {
        // Re-throw our own exception
        throw e
    } catch (e: Exception) {
        throw IllegalArgumentException(
            "Unexpected error loading WASM file '$wasmFilename': ${e.message}",
            e
        )
    }
}
