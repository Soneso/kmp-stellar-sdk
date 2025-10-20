// Webpack dev server configuration to serve WASM files
// This ensures the dev server can serve files from the output directory
//
// Note: 'path' and 'fs' are already required in copy-wasm-files.js

// Configure dev server
if (!config.devServer) {
    config.devServer = {};
}

// Set up static file serving
if (!config.devServer.static) {
    config.devServer.static = [];
} else if (!Array.isArray(config.devServer.static)) {
    config.devServer.static = [config.devServer.static];
}

// The WASM files will be copied to output.path/wasm by copy-wasm-files.js
// Webpack dev server needs to serve files from the dist directory
// Note: config.output.path will be set to the dist directory by webpack

// Add the dist directory to static serving
// We use a function to get the path at runtime after webpack has configured it
const getDistPath = () => {
    // The dist directory is at the same level as the webpack config
    return path.join(__dirname, 'dist');
};

// Add dist directory to static paths
config.devServer.static.push({
    directory: getDistPath(),
    publicPath: '/',
    watch: true,
    serveIndex: false
});

// Enable watching of static files
config.devServer.watchFiles = config.devServer.watchFiles || [];
if (!Array.isArray(config.devServer.watchFiles)) {
    config.devServer.watchFiles = [config.devServer.watchFiles];
}

// Add headers for WASM files to ensure proper MIME type
config.devServer.headers = config.devServer.headers || {};
config.devServer.headers['Access-Control-Allow-Origin'] = '*';

// Set up before middleware to log WASM requests for debugging
config.devServer.setupMiddlewares = (middlewares, devServer) => {
    if (!devServer) {
        throw new Error('webpack-dev-server is not defined');
    }

    // Add custom middleware to serve WASM files with correct MIME type
    devServer.app.use((req, res, next) => {
        if (req.url.endsWith('.wasm')) {
            console.log('WASM file requested:', req.url);
            res.setHeader('Content-Type', 'application/wasm');
            res.setHeader('Access-Control-Allow-Origin', '*');
        }
        next();
    });

    return middlewares;
};

console.log('Webpack dev server: Configured to serve WASM files from output directory');
console.log('  WASM files will be available at: http://localhost:8081/wasm/*.wasm');
console.log('  Static directory:', getDistPath());
