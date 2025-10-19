package com.soneso.demo.platform

/**
 * WebAssembly implementation of clipboard using the browser Clipboard API.
 * Note: This is similar to the JS implementation but uses WASM-compatible APIs.
 * Requires HTTPS or localhost for security reasons (browser restriction).
 */
private class WasmJSClipboard : Clipboard {
    override suspend fun copyToClipboard(text: String): Boolean {
        return try {
            // Use external JavaScript function for clipboard access
            // This will be called through JS interop
            js("navigator.clipboard.writeText(text)")
            true
        } catch (e: Exception) {
            console.log("Clipboard error: $e")
            // Try fallback using execCommand
            try {
                copyToClipboardFallback(text)
            } catch (e2: Exception) {
                console.log("Fallback clipboard error: $e2")
                false
            }
        }
    }

    private fun copyToClipboardFallback(text: String): Boolean {
        return try {
            // Create a temporary textarea element
            js("""
                const textarea = document.createElement('textarea');
                textarea.value = text;
                textarea.style.position = 'fixed';
                textarea.style.left = '-9999px';
                document.body.appendChild(textarea);
                textarea.select();
                const success = document.execCommand('copy');
                document.body.removeChild(textarea);
                return success;
            """)
            true
        } catch (e: Exception) {
            console.log("Fallback clipboard error: $e")
            false
        }
    }
}

actual fun getClipboard(): Clipboard = WasmJSClipboard()
