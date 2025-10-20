package com.soneso.demo.stellar

import kotlinx.cinterop.*
import platform.Foundation.*
import platform.posix.memcpy

/**
 * iOS implementation for loading WASM resources.
 *
 * Uses Foundation Bundle APIs to read WASM files from the app bundle's resources.
 *
 * Note: This is marked as suspend for consistency with the JavaScript implementation,
 * but it doesn't actually suspend on iOS (zero overhead).
 */
@OptIn(ExperimentalForeignApi::class)
actual suspend fun loadWasmResource(wasmFilename: String): ByteArray {
    return try {
        // Get the main bundle
        val bundle = NSBundle.mainBundle

        // Remove .wasm extension to get resource name
        val resourceName = wasmFilename.removeSuffix(".wasm")

        // Try to find the resource in the wasm subdirectory of the bundle
        // Note: pathForResource looks in the bundle's Resources directory,
        // and we need to specify the subdirectory explicitly
        val resourcePath = bundle.pathForResource("wasm/$resourceName", ofType = "wasm")

        if (resourcePath == null) {
            throw IllegalArgumentException(
                "WASM file not found in app bundle: 'wasm/$wasmFilename'. " +
                "Ensure the file is included in the iOS app bundle resources under the wasm/ subdirectory."
            )
        }

        // Read the file data
        val data = NSData.dataWithContentsOfFile(resourcePath)

        if (data == null) {
            throw IllegalArgumentException("Failed to read WASM file from path: $resourcePath")
        }

        // Convert NSData to ByteArray
        ByteArray(data.length.toInt()).apply {
            usePinned { pinned ->
                memcpy(pinned.addressOf(0), data.bytes, data.length)
            }
        }
    } catch (e: Exception) {
        throw IllegalArgumentException("Failed to read WASM file '$wasmFilename': ${e.message}", e)
    }
}
