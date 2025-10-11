package com.soneso.stellar.sdk.util

/**
 * Platform-agnostic utility for reading test resources.
 *
 * This expect/actual pattern allows reading test resources (like WASM files)
 * from the appropriate location on each platform (JVM, JS, Native).
 */
expect object TestResourceUtil {
    /**
     * Reads a WASM file from the test resources directory.
     *
     * @param filename The name of the WASM file (e.g., "soroban_hello_world_contract.wasm")
     * @return The file contents as a ByteArray
     * @throws IllegalArgumentException if the file cannot be found or read
     */
    fun readWasmFile(filename: String): ByteArray
}
