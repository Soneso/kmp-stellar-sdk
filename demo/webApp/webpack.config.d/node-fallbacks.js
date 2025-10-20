// Webpack configuration to handle Node.js polyfills for browser environment
// This fixes warnings from libsodium trying to import Node.js 'crypto' module

// Configure resolve.fallback to tell webpack how to handle Node.js core modules in browser
config.resolve = config.resolve || {};
config.resolve.fallback = config.resolve.fallback || {};

// Set crypto to false - we don't need Node.js crypto in the browser
// libsodium will use its own WebAssembly implementation
config.resolve.fallback.crypto = false;

// Also disable other Node.js modules that libsodium might try to use
config.resolve.fallback.stream = false;
config.resolve.fallback.buffer = false;
config.resolve.fallback.path = false;
config.resolve.fallback.fs = false;

console.log('Webpack: Node.js polyfill fallbacks configured');
console.log('  crypto: false (using libsodium WebAssembly)');
console.log('  stream, buffer, path, fs: false (not needed in browser)');
