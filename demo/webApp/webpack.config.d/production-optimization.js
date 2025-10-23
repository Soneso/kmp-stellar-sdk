// Webpack production build optimization fix for large KMP projects
// See: WEB_APP_BUILD_INVESTIGATION_SUMMARY.md
//
// Problem: Standard webpack optimization phase hangs indefinitely with our 30+ MB bundle
// - 470+ XDR types generating significant code
// - Full Compose Multiplatform stack (Material 3, Foundation, UI, Skiko WASM)
// - 69 JavaScript modules with complex dependency graph
//
// Solution: Disable aggressive optimizations that cause hangs while keeping build functional

console.log('Webpack: Configuring production build optimizations...');

// Check if this is a production build
const isProduction = config.mode === 'production';

if (isProduction) {
    console.log('Webpack: Production mode detected - applying optimization fixes');

    // Configure optimization settings to prevent hangs
    config.optimization = config.optimization || {};

    // PHASE 1: Disable minification (primary cause of hang)
    // TerserPlugin tries to minify 30+ MB of code and gets stuck
    config.optimization.minimize = false;

    console.log('  ✓ Minification: DISABLED (prevents webpack hang)');

    // Keep module concatenation disabled for large projects
    // This reduces webpack's combinatorial optimization space
    config.optimization.concatenateModules = false;

    console.log('  ✓ Module concatenation: DISABLED');

    // Configure chunk splitting for better performance
    // Split large bundle into manageable chunks
    config.optimization.splitChunks = {
        chunks: 'all',
        maxInitialRequests: Infinity,
        minSize: 0,
        cacheGroups: {
            // SDK and XDR types in separate chunk
            stellar: {
                test: /[\\/]stellar-sdk[\\/]/,
                name: 'stellar-sdk',
                priority: 30,
                reuseExistingChunk: true,
            },
            // Compose and Skiko in separate chunk
            compose: {
                test: /[\\/](compose|skiko)[\\/]/,
                name: 'compose',
                priority: 20,
                reuseExistingChunk: true,
            },
            // Kotlin standard library
            kotlin: {
                test: /[\\/]kotlin[\\/]/,
                name: 'kotlin-stdlib',
                priority: 25,
                reuseExistingChunk: true,
            },
            // All other vendor dependencies
            vendors: {
                test: /[\\/]node_modules[\\/]/,
                name: 'vendors',
                priority: 10,
                reuseExistingChunk: true,
            },
            // Default chunk for app code
            default: {
                minChunks: 2,
                priority: -10,
                reuseExistingChunk: true,
            },
        },
    };

    console.log('  ✓ Code splitting: ENABLED (stellar-sdk, compose, kotlin, vendors)');

    // Use source-map for debugging (faster than eval-source-map)
    // Can be disabled entirely if build is still slow
    config.devtool = 'source-map';

    console.log('  ✓ Source maps: source-map (can disable if needed)');

    // Configure performance hints but don't fail build
    config.performance = {
        hints: 'warning',
        maxEntrypointSize: 50000000,  // 50 MB (unminified)
        maxAssetSize: 50000000,       // 50 MB (unminified)
    };

    console.log('  ✓ Performance hints: warnings only (max 50 MB)');

    // Increase Node.js memory for webpack via stats
    // This is informational - actual memory must be set via NODE_OPTIONS environment variable
    console.log('');
    console.log('  Note: If build still has memory issues, set environment variable:');
    console.log('  export NODE_OPTIONS="--max-old-space-size=4096"');
    console.log('  ./gradlew :demo:webApp:jsBrowserProductionWebpack');

} else {
    console.log('Webpack: Development mode - using default optimizations');
}

console.log('Webpack: Production optimization configuration complete');
