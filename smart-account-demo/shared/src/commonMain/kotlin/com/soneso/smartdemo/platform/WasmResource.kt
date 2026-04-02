package com.soneso.smartdemo.platform

/**
 * Loads WASM bytecode from the app's bundled resources.
 *
 * Each platform has a different mechanism for accessing bundled files:
 * - Android: ClassLoader reads from the merged APK resource stream
 * - iOS/macOS: NSBundle reads from the app bundle's Resources directory
 * - JS/Web: fetch() API loads from the HTTP server (requires vite/webpack config to serve wasm/)
 *
 * The function is suspend because the JavaScript implementation requires async I/O
 * via the fetch() API. On JVM and Native platforms, this compiles to a regular
 * function with zero overhead — no actual suspension occurs.
 *
 * @param wasmFilename The WASM file name (e.g., "soroban_token_contract.wasm").
 *                     The file must exist in commonMain/resources/wasm/.
 * @return The WASM bytecode as a [ByteArray].
 * @throws IllegalArgumentException if the file cannot be found or read.
 */
expect suspend fun loadWasmResource(wasmFilename: String): ByteArray
