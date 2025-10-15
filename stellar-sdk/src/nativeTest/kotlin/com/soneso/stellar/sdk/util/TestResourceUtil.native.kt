package com.soneso.stellar.sdk.util

import kotlinx.cinterop.*
import platform.posix.*

/**
 * Native (iOS/macOS) implementation for reading test resources.
 *
 * Uses POSIX file I/O APIs to read WASM files from the test resources directory.
 */
@OptIn(ExperimentalForeignApi::class)
actual object TestResourceUtil {
    /**
     * Reads a WASM file from the test resources directory.
     *
     * On Native platforms, this implementation uses POSIX file I/O to read files
     * from the resources/wasm directory. It tries multiple possible paths to account
     * for different build configurations and execution contexts.
     *
     * @param filename The name of the WASM file (e.g., "soroban_hello_world_contract.wasm")
     * @return The file contents as a ByteArray
     * @throws IllegalArgumentException if the file cannot be found or read
     */
    actual fun readWasmFile(filename: String): ByteArray {
        // Try multiple possible paths (relative to different build/execution contexts)
        val paths = listOf(
            "src/commonTest/resources/wasm/$filename",
            "../src/commonTest/resources/wasm/$filename",
            "../../src/commonTest/resources/wasm/$filename",
            "../../../src/commonTest/resources/wasm/$filename",
            "stellar-sdk/src/commonTest/resources/wasm/$filename"
        )

        for (path in paths) {
            try {
                val file = fopen(path, "rb") ?: continue

                try {
                    // Get file size
                    fseek(file, 0, SEEK_END)
                    val size = ftell(file).toInt()
                    fseek(file, 0, SEEK_SET)

                    if (size <= 0) {
                        continue
                    }

                    // Read file contents
                    return memScoped {
                        val buffer = allocArray<ByteVar>(size)
                        val bytesRead = fread(buffer, 1u, size.toULong(), file).toInt()

                        if (bytesRead != size) {
                            throw IllegalArgumentException("Failed to read complete file: expected $size bytes, got $bytesRead")
                        }

                        // Convert to ByteArray
                        ByteArray(size) { i -> buffer[i] }
                    }
                } finally {
                    fclose(file)
                }
            } catch (e: Exception) {
                // Try next path
                continue
            }
        }

        throw IllegalArgumentException(
            "WASM file not found in any expected location: '$filename'. " +
            "Searched paths: ${paths.joinToString(", ")}"
        )
    }
}
