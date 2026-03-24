package com.soneso.smartdemo.platform

import kotlinx.browser.window
import kotlinx.coroutines.await
import org.khronos.webgl.Uint8Array
import org.khronos.webgl.get

/**
 * JavaScript/Web implementation for loading WASM resources.
 *
 * In browser environments, files cannot be read from disk directly — they must be served
 * over HTTP and fetched via the fetch() API. The vite.config.js for the web app copies
 * WASM files from commonMain/resources/wasm/ into the public directory so they are
 * served at /wasm/<filename> by the dev server and production build.
 *
 * Multiple URL paths are tried in order to accommodate different deployment configurations
 * (subdirectory deployments, root deployments, development servers).
 *
 * This implementation genuinely suspends while awaiting the network response, which is
 * why the entire loadWasmResource expect/actual API is suspend.
 */
actual suspend fun loadWasmResource(wasmFilename: String): ByteArray {
    // Try multiple paths to accommodate root deployments, subdirectory deployments,
    // and Vite dev server configurations.
    val candidatePaths = listOf(
        "./wasm/$wasmFilename",   // Relative to current page (most common for Vite)
        "wasm/$wasmFilename",     // Relative without leading dot
        "/wasm/$wasmFilename"     // Absolute from server root
    )

    var lastError: String = "unknown error"

    for (url in candidatePaths) {
        try {
            val response = window.fetch(url).await()

            if (response.ok) {
                val arrayBuffer = response.arrayBuffer().await()
                val uint8Array = Uint8Array(arrayBuffer)
                return ByteArray(uint8Array.length) { i -> uint8Array[i] }
            }

            lastError = "HTTP ${response.status} for $url"
        } catch (e: dynamic) {
            lastError = "${e.message ?: e.toString()} for $url"
        }
    }

    throw IllegalArgumentException(
        "Failed to load WASM file '$wasmFilename' from the web server. " +
        "Tried paths: ${candidatePaths.joinToString(", ")}. Last error: $lastError. " +
        "Ensure the vite.config.js copies WASM files from commonMain/resources/wasm/ " +
        "to the public/wasm/ directory so they are served alongside the application."
    )
}
