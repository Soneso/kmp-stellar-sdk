package com.soneso.demo.stellar

import kotlinx.browser.window
import kotlinx.coroutines.await
import org.khronos.webgl.Uint8Array
import org.khronos.webgl.get

/**
 * JavaScript implementation for loading WASM resources.
 *
 * For browser environments, WASM files must be served via HTTP and loaded
 * using the fetch() API. This implementation uses Kotlin/JS coroutines to
 * provide a clean async/await pattern.
 *
 * The WASM files are expected to be served from the `/wasm/` directory,
 * which is configured via webpack to copy files from the resources directory.
 */
actual suspend fun loadWasmResource(wasmFilename: String): ByteArray {
    return try {
        // Construct the URL to fetch the WASM file from
        val url = "/wasm/$wasmFilename"

        // Use fetch() API to load the WASM file
        val response = window.fetch(url).await()

        if (!response.ok) {
            throw IllegalArgumentException(
                "Failed to fetch WASM file '$wasmFilename': HTTP ${response.status} ${response.statusText}"
            )
        }

        // Get the response as an ArrayBuffer
        val arrayBuffer = response.arrayBuffer().await()

        // Convert ArrayBuffer to Uint8Array
        val uint8Array = Uint8Array(arrayBuffer)

        // Convert Uint8Array to ByteArray
        ByteArray(uint8Array.length) { i ->
            uint8Array[i]
        }
    } catch (e: IllegalArgumentException) {
        // Re-throw IllegalArgumentException with original message
        throw e
    } catch (e: dynamic) {
        // Catch any JavaScript errors and wrap them with helpful context
        throw IllegalArgumentException(
            "Failed to load WASM file '$wasmFilename' from /wasm/ directory. " +
            "Error: ${e.message ?: e.toString()}. " +
            "Ensure WASM files are properly copied to the webpack output directory.",
            e as? Throwable
        )
    }
}
