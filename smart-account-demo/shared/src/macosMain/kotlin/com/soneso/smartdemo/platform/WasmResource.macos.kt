package com.soneso.smartdemo.platform

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSBundle
import platform.Foundation.NSData
import platform.posix.memcpy

/**
 * macOS implementation for loading WASM resources.
 *
 * macOS bundles resources from commonMain/resources/wasm/ into the app's main bundle.
 * NSBundle.mainBundle provides access to these files at runtime. The resource name
 * must be split from its extension because pathForResource separates the two.
 *
 * This function is marked suspend for API consistency with the JS implementation, but does
 * not actually suspend on macOS — it executes synchronously with zero overhead.
 */
@OptIn(ExperimentalForeignApi::class)
actual suspend fun loadWasmResource(wasmFilename: String): ByteArray {
    return try {
        val bundle = NSBundle.mainBundle

        // NSBundle's pathForResource requires the base name and extension separately.
        // The subdirectory parameter matches the wasm/ folder inside the bundle's Resources.
        val resourceName = wasmFilename.removeSuffix(".wasm")
        val resourcePath = bundle.pathForResource("wasm/$resourceName", ofType = "wasm")
            ?: throw IllegalArgumentException(
                "WASM file not found in macOS app bundle: 'wasm/$wasmFilename'. " +
                "Ensure the file is present in commonMain/resources/wasm/ and is " +
                "included in the macOS app bundle resources."
            )

        val data = NSData.dataWithContentsOfFile(resourcePath)
            ?: throw IllegalArgumentException(
                "Failed to read WASM file from bundle path: $resourcePath"
            )

        // Copy NSData bytes into a Kotlin ByteArray via memcpy, pinning the array to
        // prevent the GC from moving it while the native copy is in progress.
        ByteArray(data.length.toInt()).apply {
            usePinned { pinned ->
                memcpy(pinned.addressOf(0), data.bytes, data.length)
            }
        }
    } catch (e: IllegalArgumentException) {
        throw e
    } catch (e: Exception) {
        throw IllegalArgumentException("Failed to load WASM file '$wasmFilename': ${e.message}", e)
    }
}
