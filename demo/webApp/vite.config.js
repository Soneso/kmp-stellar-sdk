import { defineConfig } from 'vite';
import { resolve } from 'path';
import { copyFileSync, existsSync, mkdirSync, readdirSync, cpSync, readFileSync, writeFileSync } from 'fs';

// Vite plugin to prepare the Kotlin/JS webpack output for serving
function prepareKotlinJSPlugin() {
  return {
    name: 'prepare-kotlinjs',

    configResolved(config) {
      // 'serve' = dev server, 'build' = production build, 'preview' = preview production
      const isDev = config.command === 'serve';
      const isPreview = config.command === 'preview' || config.command === 'build';
      const mode = (isDev || !isPreview) ? 'developmentExecutable' : 'productionExecutable';
      const webpackDir = resolve(__dirname, `build/kotlin-webpack/js/${mode}`);
      const distDir = resolve(__dirname, `build/dist/js/${mode}`);
      const publicDir = resolve(__dirname, 'public');

      console.log(`Vite: Preparing ${mode} build (command: ${config.command})...`);

      // 1. Copy contract WASM files to public directory
      const wasmSourceDir = resolve(__dirname, '../shared/src/commonMain/resources/wasm');
      const wasmDestDir = resolve(publicDir, 'wasm');

      if (existsSync(wasmSourceDir)) {
        if (!existsSync(publicDir)) {
          mkdirSync(publicDir, { recursive: true });
        }
        if (!existsSync(wasmDestDir)) {
          mkdirSync(wasmDestDir, { recursive: true });
        }

        const files = readdirSync(wasmSourceDir);
        let copiedCount = 0;
        files.forEach(file => {
          if (file.endsWith('.wasm')) {
            copyFileSync(resolve(wasmSourceDir, file), resolve(wasmDestDir, file));
            copiedCount++;
          }
        });
        console.log(`Vite: Copied ${copiedCount} contract WASM files`);
      }

      // 2. Copy webpack output to dist directory for Vite to serve
      if (existsSync(webpackDir)) {
        console.log('Vite: Copying webpack output to dist...');

        if (!existsSync(distDir)) {
          mkdirSync(distDir, { recursive: true });
        }

        // Copy all JS files (app.js and chunks)
        const jsFiles = readdirSync(webpackDir).filter(f => f.endsWith('.js'));
        jsFiles.forEach(file => {
          copyFileSync(resolve(webpackDir, file), resolve(distDir, file));
        });
        console.log(`Vite: Copied ${jsFiles.length} JS files`);

        // Copy all JS map files
        const mapFiles = readdirSync(webpackDir).filter(f => f.endsWith('.js.map'));
        mapFiles.forEach(file => {
          copyFileSync(resolve(webpackDir, file), resolve(distDir, file));
        });

        // Copy Skiko WASM
        const wasmFiles = readdirSync(webpackDir).filter(f => f.endsWith('.wasm'));
        wasmFiles.forEach(file => {
          copyFileSync(resolve(webpackDir, file), resolve(distDir, file));
        });
        console.log(`Vite: Copied ${wasmFiles.length} WASM files`);

        // Copy webpack WASM directory
        const webpackWasmDir = resolve(webpackDir, 'wasm');
        if (existsSync(webpackWasmDir)) {
          const distWasmDir = resolve(distDir, 'wasm');
          if (!existsSync(distWasmDir)) {
            mkdirSync(distWasmDir, { recursive: true });
          }
          cpSync(webpackWasmDir, distWasmDir, { recursive: true });
        }

        // 3. Create/update index.html
        const indexPath = resolve(distDir, 'index.html');
        const processedHtml = resolve(__dirname, 'build/processedResources/js/main/index.html');

        if (existsSync(processedHtml)) {
          let htmlContent = readFileSync(processedHtml, 'utf-8');

          // For production, add script tags for all chunks
          if (!isDev && jsFiles.length > 1) {
            // Production has multiple chunks - need to load them in order
            const chunks = jsFiles.sort(); // Sort to ensure correct order
            const scriptTags = chunks.map(file => `<script src="${file}"></script>`).join('\n    ');
            htmlContent = htmlContent.replace(
              /<script src="app\.js"><\/script>/,
              scriptTags
            );
          }

          writeFileSync(indexPath, htmlContent);
          console.log('Vite: Created index.html');
        }
      }

      console.log('Vite: Preparation complete');
    }
  };
}

export default defineConfig(({ command }) => ({
  // Serve from the appropriate Kotlin/JS dist directory based on command
  root: command === 'serve'
    ? resolve(__dirname, 'build/dist/js/developmentExecutable')
    : resolve(__dirname, 'build/dist/js/productionExecutable'),

  // Public directory for static assets
  publicDir: resolve(__dirname, 'public'),

  build: {
    // For production preview, just copy webpack output to dist
    outDir: resolve(__dirname, 'dist'),
    emptyOutDir: true,

    // Copy HTML without processing
    rollupOptions: {
      input: resolve(__dirname, 'build/dist/js/productionExecutable/index.html'),
      output: {
        // Keep the original file structure
        entryFileNames: '[name].js',
        chunkFileNames: '[name].js',
        assetFileNames: '[name].[ext]'
      }
    },

    // Don't process anything - webpack already did it all
    minify: false,
    sourcemap: false,
    target: 'esnext',
    chunkSizeWarningLimit: 100000,

    // Don't try to bundle - just copy
    copyPublicDir: true
  },

  server: {
    port: 8081,
    open: false,
    headers: {
      'Access-Control-Allow-Origin': '*'
    }
  },

  preview: {
    port: 8082,
    open: false
  },

  // Don't optimize dependencies - webpack already bundled everything
  optimizeDeps: {
    noDiscovery: true,
    include: []
  },

  plugins: [
    prepareKotlinJSPlugin()
  ]
}));
