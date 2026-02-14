package com.soneso.stellar.sdk.util

// Detect Node.js vs browser environment
private fun isNodeJs(): Boolean = js(
    "typeof process !== 'undefined' && typeof process.versions !== 'undefined' && typeof process.versions.node !== 'undefined'"
) as Boolean

/**
 * JavaScript implementation for reading test resources.
 *
 * Supports both Node.js (using fs module) and browser (using synchronous XHR from Karma server).
 */
actual object TestResourceUtil {
    actual fun readWasmFile(filename: String): ByteArray {
        return if (isNodeJs()) {
            readWasmFileNode(filename)
        } else {
            readWasmFileBrowser(filename)
        }
    }

    private fun readWasmFileNode(filename: String): ByteArray {
        return try {
            // Use eval("require") to prevent webpack from trying to bundle Node.js modules
            val fs = js("eval('require')('fs')")
            val path = js("eval('require')('path')")

            val dirname = js("__dirname") as String
            val paths = arrayOf(
                path.resolve(dirname, "wasm", filename) as String,
                "kotlin/wasm/$filename",
                "src/commonTest/resources/wasm/$filename",
                "stellar-sdk/src/commonTest/resources/wasm/$filename"
            )

            for (p in paths) {
                try {
                    val buffer = fs.readFileSync(p)
                    val uint8Array = js("new Uint8Array(buffer)")
                    return ByteArray(uint8Array.length as Int) { i ->
                        uint8Array[i].unsafeCast<Byte>()
                    }
                } catch (e: dynamic) {
                    // Try next path
                }
            }

            throw IllegalArgumentException(
                "WASM file not found in any expected location: '$filename' " +
                "(searched from __dirname=$dirname)"
            )
        } catch (e: Throwable) {
            throw IllegalArgumentException("Failed to read WASM file '$filename': ${e.message}", e)
        }
    }

    private fun readWasmFileBrowser(filename: String): ByteArray {
        // In Karma browser tests, WASM files are served by a custom middleware
        // registered in karma.conf.js that maps /wasm/ to the test resources directory.
        val url = "/wasm/$filename"

        return try {
            val xhr = js("new XMLHttpRequest()")
            xhr.open("GET", url, false) // synchronous
            xhr.overrideMimeType("text/plain; charset=x-user-defined")
            xhr.send()

            val status = xhr.status as Int
            if (status != 200) {
                throw IllegalArgumentException(
                    "Failed to fetch WASM file '$filename' from Karma server: HTTP $status (url: $url)"
                )
            }

            val responseText = xhr.responseText as String
            ByteArray(responseText.length) { i ->
                (responseText[i].code and 0xFF).toByte()
            }
        } catch (e: Throwable) {
            throw IllegalArgumentException(
                "Failed to read WASM file '$filename' in browser: ${e.message}", e
            )
        }
    }
}
