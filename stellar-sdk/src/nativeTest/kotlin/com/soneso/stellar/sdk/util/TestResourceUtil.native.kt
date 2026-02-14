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
        // Use PROJECT_DIR env var (set by Gradle) for absolute path resolution.
        // This is needed because iOS simulator runs from a different CWD
        // inside CoreSimulator, where relative paths don't work.
        val projectDir = getenv("PROJECT_DIR")?.toKString()

        val paths = mutableListOf<String>()

        // Absolute path via PROJECT_DIR (works regardless of CWD)
        if (projectDir != null) {
            paths.add("$projectDir/src/commonTest/resources/wasm/$filename")
        }

        // Relative paths (work when CWD is the project or repo root)
        paths.addAll(listOf(
            "src/commonTest/resources/wasm/$filename",
            "stellar-sdk/src/commonTest/resources/wasm/$filename"
        ))

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
            "PROJECT_DIR: $projectDir. Searched paths: ${paths.joinToString(", ")}"
        )
    }
}
