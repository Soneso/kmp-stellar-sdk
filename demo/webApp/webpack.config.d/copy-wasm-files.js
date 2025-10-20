// Webpack configuration to copy WASM files from shared resources to the output directory
// This uses webpack's CopyWebpackPlugin for robust file copying

const path = require('path');
const fs = require('fs');

// Try multiple possible paths to find the WASM files
// Webpack runs from different directories depending on the build
const possibleRoots = [
    path.resolve(__dirname, '../../../../..'),  // From build/js/packages/kmp-stellar-sdk-demo-webApp/webpack.config.d
    path.resolve(__dirname, '../../../..'),     // One level up
    path.resolve(__dirname, '../../..'),        // Two levels up
    path.resolve(__dirname, '../..')            // From demo/webApp/webpack.config.d
];

let wasmSourceDir = null;

for (const root of possibleRoots) {
    const testPath = path.join(root, 'demo/shared/src/commonMain/resources/wasm');
    if (fs.existsSync(testPath)) {
        wasmSourceDir = testPath;
        console.log('Found WASM source directory at:', wasmSourceDir);
        break;
    }
}

if (!wasmSourceDir) {
    console.warn('Warning: Could not find WASM source directory. Tried:');
    possibleRoots.forEach(root => {
        console.warn('  -', path.join(root, 'demo/shared/src/commonMain/resources/wasm'));
    });
    console.warn('__dirname:', __dirname);
    // Don't fail the build, just skip copying
} else {
    // Use beforeRun hook to copy files BEFORE webpack dev server starts
    // This ensures files are available when the dev server starts serving
    config.plugins = config.plugins || [];
    config.plugins.push({
        apply: (compiler) => {
            // Copy files at the very start
            compiler.hooks.beforeRun.tap('CopyWasmFilesBeforeRun', () => {
                const outputPath = compiler.options.output.path;
                const wasmOutputDir = path.join(outputPath, 'wasm');

                console.log('Copying WASM files (beforeRun) from:', wasmSourceDir);
                console.log('Copying WASM files (beforeRun) to:', wasmOutputDir);

                // Create output directory if it doesn't exist
                if (!fs.existsSync(wasmOutputDir)) {
                    fs.mkdirSync(wasmOutputDir, { recursive: true });
                }

                // Copy each WASM file
                const files = fs.readdirSync(wasmSourceDir);
                let copiedCount = 0;
                files.forEach(file => {
                    if (file.endsWith('.wasm')) {
                        const source = path.join(wasmSourceDir, file);
                        const dest = path.join(wasmOutputDir, file);
                        fs.copyFileSync(source, dest);
                        console.log('  ✓ Copied:', file);
                        copiedCount++;
                    }
                });

                console.log(`Successfully copied ${copiedCount} WASM files (beforeRun)`);
            });

            // Also copy on watchRun for development mode
            compiler.hooks.watchRun.tap('CopyWasmFilesWatchRun', () => {
                const outputPath = compiler.options.output.path;
                const wasmOutputDir = path.join(outputPath, 'wasm');

                // Only copy if directory doesn't exist or is empty
                if (!fs.existsSync(wasmOutputDir) || fs.readdirSync(wasmOutputDir).filter(f => f.endsWith('.wasm')).length === 0) {
                    console.log('Copying WASM files (watchRun) from:', wasmSourceDir);
                    console.log('Copying WASM files (watchRun) to:', wasmOutputDir);

                    // Create output directory if it doesn't exist
                    if (!fs.existsSync(wasmOutputDir)) {
                        fs.mkdirSync(wasmOutputDir, { recursive: true });
                    }

                    // Copy each WASM file
                    const files = fs.readdirSync(wasmSourceDir);
                    let copiedCount = 0;
                    files.forEach(file => {
                        if (file.endsWith('.wasm')) {
                            const source = path.join(wasmSourceDir, file);
                            const dest = path.join(wasmOutputDir, file);
                            fs.copyFileSync(source, dest);
                            console.log('  ✓ Copied:', file);
                            copiedCount++;
                        }
                    });

                    console.log(`Successfully copied ${copiedCount} WASM files (watchRun)`);
                }
            });

            // Keep afterEmit for production builds
            compiler.hooks.afterEmit.tap('CopyWasmFilesAfterEmit', () => {
                const outputPath = compiler.options.output.path;
                const wasmOutputDir = path.join(outputPath, 'wasm');

                console.log('Copying WASM files (afterEmit) from:', wasmSourceDir);
                console.log('Copying WASM files (afterEmit) to:', wasmOutputDir);

                // Create output directory if it doesn't exist
                if (!fs.existsSync(wasmOutputDir)) {
                    fs.mkdirSync(wasmOutputDir, { recursive: true });
                }

                // Copy each WASM file
                const files = fs.readdirSync(wasmSourceDir);
                let copiedCount = 0;
                files.forEach(file => {
                    if (file.endsWith('.wasm')) {
                        const source = path.join(wasmSourceDir, file);
                        const dest = path.join(wasmOutputDir, file);
                        fs.copyFileSync(source, dest);
                        console.log('  ✓ Copied:', file);
                        copiedCount++;
                    }
                });

                console.log(`Successfully copied ${copiedCount} WASM files (afterEmit)`);
            });
        }
    });

    console.log('Webpack: WASM copy plugin configured (beforeRun + watchRun + afterEmit)');
    console.log('  Source:', wasmSourceDir);
}
