/**
 * Initialize libsodium-wrappers for Stellar SDK.
 *
 * This script ensures libsodium is properly loaded and initialized
 * before any cryptographic operations are performed.
 */

(function() {
    'use strict';

    // Check if we're in a browser or Node.js environment
    const isBrowser = typeof window !== 'undefined';
    const isNode = typeof process !== 'undefined' && process.versions && process.versions.node;

    /**
     * Load and initialize libsodium
     */
    async function initializeSodium() {
        try {
            if (isBrowser) {
                // Browser environment
                if (typeof _sodium === 'undefined') {
                    console.warn('libsodium-wrappers not loaded. Make sure to include it in your HTML:');
                    console.warn('<script src="https://cdn.jsdelivr.net/npm/libsodium-wrappers@0.7.13/dist/browsers/sodium.js"></script>');
                    return false;
                }

                // Wait for libsodium to be ready
                await _sodium.ready;
                console.log('libsodium initialized successfully (browser)');
                return true;

            } else if (isNode) {
                // Node.js environment
                const sodium = require('libsodium-wrappers');
                await sodium.ready;

                // Make it available globally for the Kotlin/JS code
                global._sodium = sodium;
                console.log('libsodium initialized successfully (Node.js)');
                return true;

            } else {
                console.error('Unknown JavaScript environment');
                return false;
            }
        } catch (error) {
            console.error('Failed to initialize libsodium:', error);
            return false;
        }
    }

    // Initialize on load
    if (typeof window !== 'undefined') {
        // Browser: Initialize when DOM is ready
        if (document.readyState === 'loading') {
            document.addEventListener('DOMContentLoaded', initializeSodium);
        } else {
            initializeSodium();
        }
    } else {
        // Node.js: Initialize immediately
        initializeSodium();
    }

    // Export for manual initialization if needed
    if (typeof module !== 'undefined' && module.exports) {
        module.exports = { initializeSodium };
    }
})();