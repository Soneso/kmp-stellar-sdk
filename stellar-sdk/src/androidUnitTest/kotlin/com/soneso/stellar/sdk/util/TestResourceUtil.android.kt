package com.soneso.stellar.sdk.util

import java.io.IOException
import java.nio.file.Files
import java.nio.file.Paths

/**
 * Android implementation for reading test resources.
 *
 * Uses the same java.nio.file APIs as the JVM implementation since Android
 * unit tests run on the host JVM (not on a device).
 */
actual object TestResourceUtil {
    /**
     * Reads a WASM file from the test resources directory.
     *
     * @param filename The name of the WASM file (e.g., "soroban_hello_world_contract.wasm")
     * @return The file contents as a ByteArray
     * @throws IllegalArgumentException if the file cannot be found or read
     */
    actual fun readWasmFile(filename: String): ByteArray {
        return try {
            val resourceStream = TestResourceUtil::class.java.classLoader.getResourceAsStream("wasm/$filename")
            if (resourceStream != null) {
                return resourceStream.readBytes()
            }

            val filePath = "src/commonTest/resources/wasm/$filename"
            Files.readAllBytes(Paths.get(filePath))
        } catch (e: IOException) {
            throw IllegalArgumentException("Failed to read WASM file '$filename': ${e.message}", e)
        } catch (e: NullPointerException) {
            throw IllegalArgumentException("WASM file not found: '$filename'", e)
        }
    }
}
