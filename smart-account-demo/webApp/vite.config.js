import { defineConfig } from 'vite';
import { resolve } from 'path';
import { copyFileSync, existsSync, mkdirSync, readdirSync, readFileSync, writeFileSync } from 'fs';

function prepareKotlinJSPlugin() {
  return {
    name: 'prepare-kotlinjs',

    configResolved(config) {
      const isDev = config.command === 'serve';
      const isPreview = config.command === 'preview' || config.command === 'build';
      const mode = (isDev || !isPreview) ? 'developmentExecutable' : 'productionExecutable';
      const webpackDir = resolve(__dirname, `build/kotlin-webpack/js/${mode}`);
      const distDir = resolve(__dirname, `build/dist/js/${mode}`);

      console.log(`Vite: Preparing ${mode} build (command: ${config.command})...`);

      if (existsSync(webpackDir)) {
        console.log('Vite: Copying webpack output to dist...');

        if (!existsSync(distDir)) {
          mkdirSync(distDir, { recursive: true });
        }

        const jsFiles = readdirSync(webpackDir).filter(f => f.endsWith('.js'));
        jsFiles.forEach(file => {
          copyFileSync(resolve(webpackDir, file), resolve(distDir, file));
        });
        console.log(`Vite: Copied ${jsFiles.length} JS files`);

        const mapFiles = readdirSync(webpackDir).filter(f => f.endsWith('.js.map'));
        mapFiles.forEach(file => {
          copyFileSync(resolve(webpackDir, file), resolve(distDir, file));
        });

        const wasmFiles = readdirSync(webpackDir).filter(f => f.endsWith('.wasm'));
        wasmFiles.forEach(file => {
          copyFileSync(resolve(webpackDir, file), resolve(distDir, file));
        });
        console.log(`Vite: Copied ${wasmFiles.length} WASM files`);

        const indexPath = resolve(distDir, 'index.html');
        const processedHtml = resolve(__dirname, 'build/processedResources/js/main/index.html');

        if (existsSync(processedHtml)) {
          let htmlContent = readFileSync(processedHtml, 'utf-8');

          if (!isDev && jsFiles.length > 1) {
            const chunks = jsFiles.sort();
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
  root: command === 'serve'
    ? resolve(__dirname, 'build/dist/js/developmentExecutable')
    : resolve(__dirname, 'build/dist/js/productionExecutable'),

  publicDir: resolve(__dirname, 'public'),

  build: {
    outDir: resolve(__dirname, 'dist'),
    emptyOutDir: true,

    rollupOptions: {
      input: resolve(__dirname, 'build/dist/js/productionExecutable/index.html'),
      output: {
        entryFileNames: '[name].js',
        chunkFileNames: '[name].js',
        assetFileNames: '[name].[ext]'
      }
    },

    minify: false,
    sourcemap: false,
    target: 'esnext',
    chunkSizeWarningLimit: 100000,
    copyPublicDir: true
  },

  server: {
    port: parseInt(process.env.VITE_PORT || '9003'),
    open: false,
    headers: {
      'Access-Control-Allow-Origin': '*'
    }
  },

  preview: {
    port: parseInt(process.env.VITE_PORT || '9003'),
    open: false
  },

  optimizeDeps: {
    noDiscovery: true,
    include: []
  },

  plugins: [
    prepareKotlinJSPlugin()
  ]
}));
