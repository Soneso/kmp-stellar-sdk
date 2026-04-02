package com.soneso.smartdemo.platform

/**
 * Platform-agnostic URL opener interface for opening URLs in the default browser.
 */
interface UrlOpener {
    /**
     * Open a URL in the default browser or application.
     *
     * @param url The URL to open
     * @return true if the operation succeeded, false otherwise
     */
    suspend fun openUrl(url: String): Boolean
}

/**
 * Get the platform-specific URL opener implementation.
 */
expect fun getUrlOpener(): UrlOpener
