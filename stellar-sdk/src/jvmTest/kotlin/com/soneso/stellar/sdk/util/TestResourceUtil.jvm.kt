package com.soneso.stellar.sdk.util

import java.io.IOException
import java.nio.file.Files
import java.nio.file.Paths

/**
 * JVM implementation for reading test resources.
 *
 * Uses java.nio.file APIs to read WASM files from the test resources directory.
 */
actual object TestResourceUtil {
    /**
     * Reads a WASM file from the test resources directory.
     *
     * On JVM, test resources are available in the classpath. This implementation
     * reads from the resources/wasm directory using the ClassLoader.
     *
     * @param filename The name of the WASM file (e.g., "soroban_hello_world_contract.wasm")
     * @return The file contents as a ByteArray
     * @throws IllegalArgumentException if the file cannot be found or read
     */
    actual fun readWasmFile(filename: String): ByteArray {
        return try {
            // Try reading from classpath using ClassLoader
            val resourceStream = TestResourceUtil::class.java.classLoader.getResourceAsStream("wasm/$filename")
            if (resourceStream != null) {
                return resourceStream.readBytes()
            }

            // Fallback: Try reading from filesystem (useful during development)
            val filePath = "src/commonTest/resources/wasm/$filename"
            Files.readAllBytes(Paths.get(filePath))
        } catch (e: IOException) {
            throw IllegalArgumentException("Failed to read WASM file '$filename': ${e.message}", e)
        } catch (e: NullPointerException) {
            throw IllegalArgumentException("WASM file not found: '$filename'", e)
        }
    }
}
