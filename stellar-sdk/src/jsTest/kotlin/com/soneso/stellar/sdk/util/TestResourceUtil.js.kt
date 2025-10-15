package com.soneso.stellar.sdk.util

/**
 * JavaScript implementation for reading test resources.
 *
 * Uses Node.js fs module to read files from the resources directory.
 */
actual object TestResourceUtil {
    /**
     * Reads a WASM file from the test resources directory.
     *
     * On JS, this implementation uses Node.js fs module to read files
     * from the resources/wasm directory.
     *
     * @param filename The name of the WASM file (e.g., "soroban_hello_world_contract.wasm")
     * @return The file contents as a ByteArray
     * @throws IllegalArgumentException if the file cannot be found or read
     */
    actual fun readWasmFile(filename: String): ByteArray {
        return try {
            // Try reading from Node.js filesystem
            val fs = js("require('fs')")

            // Try multiple possible paths
            val paths = arrayOf(
                "src/commonTest/resources/wasm/$filename",
                "../src/commonTest/resources/wasm/$filename",
                "../../src/commonTest/resources/wasm/$filename",
                "stellar-sdk/src/commonTest/resources/wasm/$filename"
            )

            for (path in paths) {
                try {
                    val buffer = fs.readFileSync(path)
                    // Convert Node.js Buffer to ByteArray
                    val uint8Array = js("new Uint8Array(buffer)")
                    return ByteArray(uint8Array.length as Int) { i ->
                        uint8Array[i].unsafeCast<Byte>()
                    }
                } catch (e: dynamic) {
                    // Try next path
                }
            }

            throw IllegalArgumentException("WASM file not found in any expected location: '$filename'")
        } catch (e: Throwable) {
            throw IllegalArgumentException("Failed to read WASM file '$filename': ${e.message}", e)
        }
    }
}
