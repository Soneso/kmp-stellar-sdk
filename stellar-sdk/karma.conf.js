// Custom Karma configuration — appended INSIDE the Kotlin-generated
// module.exports function by useConfigDirectory(project.projectDir).
// `config` is already in scope from the outer function, so we operate on it directly.
// Do NOT use `module.exports = function(config) { ... }` — that would just reassign
// the export without ever being called.

;(function() {
    const path = require('path');
    const fs = require('fs');
    // __dirname inside the generated config points to the build packages dir
    // (build/js/packages/kmp-stellar-sdk-stellar-sdk-test/).
    // Navigate up to the project root to find the original source files.
    const projectDir = path.resolve(__dirname, '..', '..', '..', '..', 'stellar-sdk');

    // ── Setup file (libsodium init) ──
    const existingFiles = config.files || [];
    const setupFile = path.resolve(projectDir, 'src/jsTest/resources/karma-setup.js');

    // Remove setup file if it's already in the list (to avoid duplicates)
    const filteredFiles = existingFiles.filter(f =>
        typeof f === 'string' ? f !== setupFile : (f.pattern !== setupFile)
    );
    filteredFiles.unshift(setupFile);

    config.set({
        files: filteredFiles,
        client: {
            mocha: {
                timeout: 10000 // 10 seconds for async libsodium initialization
            }
        }
    });

    // ── WASM file serving middleware ──
    // karma-webpack intercepts normal file serving, so we add a custom middleware
    // to serve WASM test resources directly from the filesystem.
    const wasmDir = path.resolve(projectDir, 'src/commonTest/resources/wasm');

    const middlewareFactory = function() {
        return function(req, res, next) {
            if (req.url && req.url.startsWith('/wasm/')) {
                const filename = req.url.replace('/wasm/', '').split('?')[0];
                const filePath = path.join(wasmDir, filename);
                if (fs.existsSync(filePath)) {
                    res.setHeader('Content-Type', 'application/wasm');
                    res.setHeader('Access-Control-Allow-Origin', '*');
                    const content = fs.readFileSync(filePath);
                    res.end(content);
                    return;
                }
            }
            next();
        };
    };
    middlewareFactory.$inject = [];

    config.plugins = config.plugins || [];
    config.plugins.push({ 'middleware:wasmServer': ['factory', middlewareFactory] });

    config.beforeMiddleware = config.beforeMiddleware || [];
    config.beforeMiddleware.push('wasmServer');

    console.log('✓ Karma configured with libsodium setup and WASM middleware (wasmDir: ' + wasmDir + ')');
})();
