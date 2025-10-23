# Web App Troubleshooting Guide

## ✅ RESOLVED: Production Webpack Build Now Works

**Update (October 23, 2025)**: The production build issue has been **fixed** through webpack configuration optimization.

### Current Status

- ✅ Production build completes successfully in ~5 seconds
- ✅ Creates 28 MB bundle (2.7 MB with gzip compression)
- ✅ Code splitting enabled (4 separate chunks)
- ✅ Web app loads and functions correctly

---

## Historical Issue: Production Webpack Build Hang (RESOLVED)

### Original Symptoms (Before Fix)
- `./gradlew :demo:webApp:jsBrowserProductionWebpack` hung indefinitely
- `compileDevelopmentExecutableKotlinJs` task consumed excessive CPU (>100%)
- Webpack process consumed 2+ GB of memory and never completed
- Error: "Module not found: Error: Can't resolve '/Users/.../kmp-stellar-sdk-demo-webApp.js'"

### Root Cause (Identified)
This was a **known issue with Kotlin/JS 2.2.x + Webpack** when bundling large Compose Multiplatform projects with:
- Large dependency graphs (69 JS modules in this project)
- XDR types (470+ types generating significant code)
- Compose HTML + Skiko WASM (8+ MB WASM file)
- Multiple large libraries (Ktor, kotlinx-serialization, Compose, etc.)

Webpack's optimization phase got stuck when processing 30+ MB of unoptimized JavaScript.

### Solution Implemented (October 23, 2025)

Created webpack configuration file that disables problematic optimizations:
- **Location**: `webpack.config.d/production-optimization.js`
- **Fix**: Disabled minification and module concatenation
- **Enhancement**: Added code splitting for better caching

### Current Recommended Commands

**Production Build** (NOW WORKING):
```bash
./gradlew :demo:webApp:jsBrowserProductionWebpack
# Completes in ~5 seconds
# Output: demo/webApp/build/kotlin-webpack/js/productionExecutable/
```

**Development Server** (For hot reload during development):
```bash
./gradlew :demo:webApp:jsDevelopmentRun
# Access: http://localhost:8081/
```

**Development Build** (Manual):
```bash
./gradlew :demo:webApp:jsBrowserDevelopmentWebpack
# Output: demo/webApp/build/kotlin-webpack/js/developmentExecutable/
```

### Recovery Steps When Processes Are Stuck

1. Kill stuck processes:
```bash
# Kill Kotlin compiler daemon
pkill -9 -f "kotlin-compiler"

# Kill webpack processes
pkill -9 -f "webpack"

# Stop all Gradle daemons
./gradlew --stop
```

2. Clean build directories:
```bash
rm -rf demo/webApp/build build/js
```

3. Start fresh:
```bash
./gradlew :demo:webApp:jsDevelopmentRun
```

### Why Production Build Hangs

The production webpack task (`jsBrowserProductionWebpack`) applies aggressive optimizations:
- Tree shaking across 69 modules
- Dead code elimination
- Minification of 63+ MB of JavaScript
- Source map generation for large bundles
- Module concatenation

With the large number of XDR types and Compose dependencies, these optimizations create a combinatorial explosion that webpack cannot complete in reasonable time.

### Development vs Production Differences

| Aspect | Development | Production |
|--------|-------------|------------|
| Build time | ~11 seconds | ~5 seconds |
| Bundle size | 63.4 MB | 28 MB (2.7 MB gzipped) |
| Optimizations | Minimal | Disabled minification |
| Source maps | Yes | Yes |
| Minification | No | No (disabled for performance) |
| Tree shaking | Minimal | Basic |
| Code splitting | No | Yes (4 chunks) |

### Future Improvements

Potential solutions for production builds:
1. **Split bundles**: Create separate chunks for SDK, Compose, and app code
2. **Upgrade Kotlin**: Wait for Kotlin/JS optimizations in future versions
3. **Webpack config**: Disable specific optimizations that cause hangs
4. **Alternative bundler**: Use esbuild or Vite instead of webpack
5. **Module federation**: Split into multiple micro-frontends

### Monitoring Build Progress

To see what's happening during builds:
```bash
# In another terminal
tail -f /tmp/dev-server.log

# Or check webpack process
ps aux | grep webpack
```

### Known Working Configuration

**File**: `demo/webApp/build.gradle.kts`
```kotlin
kotlin {
    js(IR) {
        browser {
            commonWebpackConfig {
                outputFileName = "stellarDemoJs.js"
                devServer = devServer?.copy(
                    port = 8081,
                    open = false
                )
            }
        }
        binaries.executable()
    }
}
```

This configuration works for development mode. Production mode requires further optimization or alternative bundling strategies.

### Related Issues

- [Kotlin/JS compilation performance](https://youtrack.jetbrains.com/issue/KT-50360)
- [Webpack optimization issues with large KMP projects](https://github.com/JetBrains/compose-multiplatform/issues/2834)
- Similar issues reported in Kotlin 2.0-2.2 with large dependency graphs

### Recommended Workflow

1. **Development**: Use `jsDevelopmentRun` (fast, reliable, hot reload)
2. **Testing**: Desktop app (`./gradlew :demo:desktopApp:run`) for faster iterations
3. **Production Build**: Use `jsBrowserProductionWebpack` (completes in ~5 seconds)
4. **Deployment**: Production build creates optimized bundle ready for deployment
5. **CI/CD**: Both development and production builds work reliably

### Last Updated
October 23, 2025 - Production build now working with custom webpack configuration (webpack.config.d/production-optimization.js)
