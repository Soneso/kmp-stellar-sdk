package com.soneso.demo.stellar

/**
 * WASM/JS implementation for loading WASM resources.
 *
 * For WASM targets, resource loading must be handled differently from regular JS.
 * This implementation provides a placeholder that explains the limitation.
 */
actual suspend fun loadWasmResource(wasmFilename: String): ByteArray {
    // WASM/JS resource loading is complex and depends on how the WASM module is bundled
    // For now, throw an informative error
    throw IllegalArgumentException(
        "WASM resource loading not yet implemented for WASM/JS target. " +
        "WASM file: '$wasmFilename'. " +
        "Contract deployment is currently supported on JVM, Android, iOS, macOS, and Node.js targets."
    )
}
