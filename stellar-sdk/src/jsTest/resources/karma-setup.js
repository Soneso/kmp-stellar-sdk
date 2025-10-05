// Karma test setup for libsodium initialization
// This file is loaded before test files to ensure libsodium is ready

(function() {
    // Flag to track initialization
    window.__libsodiumReady__ = false;

    // Load libsodium
    const script = document.createElement('script');
    script.src = '/base/node_modules/libsodium-wrappers/dist/browsers/sodium.js';
    script.async = false;

    script.onload = function() {
        window.sodium.ready.then(function() {
            window._sodium = window.sodium;
            window.__libsodiumReady__ = true;
            console.log('✓ libsodium initialized for Karma tests');
        }).catch(function(error) {
            console.error('✗ Failed to initialize libsodium:', error);
        });
    };

    script.onerror = function() {
        console.error('✗ Failed to load libsodium script');
    };

    document.head.appendChild(script);
})();
