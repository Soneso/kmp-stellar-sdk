# Test Environment Setup Implementation Plan

## Goal
Enable JS tests (Node.js and Browser) to run successfully without modifying the SDK code, by pre-loading libsodium in the test environment.

## Current State

### What Works
- ✅ Web app (browser with webpack) - libsodium loaded via `require('libsodium-wrappers')`
- ✅ JVM tests - using BouncyCastle
- ✅ Native tests (macOS, iOS) - using libsodium C interop

### What Doesn't Work
- ❌ Node.js tests (Mocha) - libsodium not initialized, tests hang
- ❌ Browser tests (Karma) - ChromeHeadless connection issues

## Root Cause

The SDK's `LibsodiumInit.kt` uses `js("require('libsodium-wrappers')")` which works in webpack-bundled apps but fails in test environments because:
1. **Mocha (Node.js)**: The `require()` call succeeds but libsodium's async initialization isn't awaited properly
2. **Karma (Browser)**: Webpack bundling for tests doesn't include libsodium the same way as the production build

## Solution Overview

Create test-specific setup files that pre-load and initialize libsodium **before** any test code runs. This makes libsodium available globally, allowing the SDK's `require()` call to work.

## Implementation Steps

### 1. Create Node.js Test Setup File

**File**: `stellar-sdk/src/jsTest/resources/mocha-setup.js`

**Purpose**:
- Load libsodium-wrappers module
- Wait for `sodium.ready` promise to complete
- Set global `_sodium` variable for backward compatibility
- Exit with error if initialization fails

**Content**:
```javascript
// Mocha test setup for libsodium initialization
// This file runs before all tests to ensure libsodium is ready

const sodium = require('libsodium-wrappers');

// Wait for libsodium to initialize before running tests
before(async function() {
    this.timeout(10000); // Allow up to 10s for initialization

    try {
        await sodium.ready;

        // Set global for SDK compatibility
        global._sodium = sodium;

        console.log('✓ libsodium initialized successfully');
    } catch (error) {
        console.error('✗ Failed to initialize libsodium:', error);
        throw error;
    }
});
```

**Key features**:
- Uses Mocha's `before()` hook to run before all tests
- Sets explicit timeout for async initialization
- Provides clear console output for debugging
- Throws error on failure to prevent tests from running with broken crypto

### 2. Create Karma Test Setup File

**File**: `stellar-sdk/src/jsTest/resources/karma-setup.js`

**Purpose**:
- Pre-load libsodium in browser environment
- Initialize before test framework loads
- Handle promise-based async initialization

**Content**:
```javascript
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
```

**Key features**:
- Loads libsodium from node_modules (Karma serves this via `/base/`)
- Uses browser-compatible distribution of libsodium
- Sets global flag for test framework integration
- Synchronous script loading to ensure order

### 3. Configure Gradle Build for Node.js Tests

**File**: `stellar-sdk/build.gradle.kts`

**Changes**: Update the `nodejs` test configuration

**Before**:
```kotlin
nodejs {
    testTask {
        useMocha {
            timeout = "30s"
        }
    }
}
```

**After**:
```kotlin
nodejs {
    testTask {
        useMocha {
            timeout = "30s"
        }
    }

    // Configure test resources
    compilations["test"].defaultSourceSet {
        resources.srcDir("src/jsTest/resources")
    }
}

// Configure Mocha to use setup file
tasks.named("jsNodeTest") {
    doFirst {
        // Add --require flag to load setup file before tests
        systemProperty("mocha.require", "src/jsTest/resources/mocha-setup.js")
    }
}
```

**Purpose**: Tell Mocha to load the setup file before running any tests

### 4. Configure Gradle Build for Karma Tests

**File**: `stellar-sdk/build.gradle.kts`

**Changes**: Create custom Karma configuration

**Add after nodejs configuration**:
```kotlin
browser {
    testTask {
        useKarma {
            useChromeHeadless()

            // Custom Karma configuration
            useConfigDirectory(file("src/jsTest/resources"))
        }
    }
}
```

**Create**: `stellar-sdk/src/jsTest/resources/karma.conf.d/setup.js`

**Content**:
```javascript
// Karma configuration fragment for libsodium setup

config.set({
    // Load libsodium setup before test files
    files: [
        'karma-setup.js',
        // Test files will be added automatically by Kotlin/JS plugin
    ],

    // Serve node_modules so libsodium can be loaded
    files: [
        { pattern: 'node_modules/libsodium-wrappers/dist/browsers/sodium.js', included: false }
    ],

    // Wait for libsodium to initialize before starting tests
    client: {
        mocha: {
            timeout: 10000
        }
    }
});
```

**Purpose**: Configure Karma to pre-load libsodium setup script

### 5. Alternative: Simpler Karma Approach

If custom Karma config fragments don't work, use a simpler approach:

**Create**: `stellar-sdk/karma.conf.js` (root of stellar-sdk module)

**Content**:
```javascript
module.exports = function(config) {
    // Load base config from Kotlin/JS
    const kotlinConfig = require('./build/js/packages/kmp-stellar-sdk-stellar-sdk-test/karma.conf.js');

    // Apply Kotlin config
    kotlinConfig(config);

    // Add our setup file to the beginning
    const files = config.files || [];
    files.unshift('src/jsTest/resources/karma-setup.js');
    config.set({ files });

    console.log('✓ Karma configured with libsodium setup');
};
```

**Update build.gradle.kts**:
```kotlin
browser {
    testTask {
        useKarma {
            useChromeHeadless()
            useConfigDirectory(project.projectDir)
        }
    }
}
```

**Purpose**: Override Kotlin/JS generated Karma config to add setup file

## Testing the Solution

### Step 1: Test Node.js Tests
```bash
./gradlew :stellar-sdk:jsNodeTest
```

**Expected result**: Tests should initialize libsodium and run successfully without hanging

### Step 2: Test Browser Tests
```bash
./gradlew :stellar-sdk:jsBrowserTest
```

**Expected result**: ChromeHeadless should connect and tests should run

### Step 3: Verify Web App Still Works
```bash
./gradlew :stellarSample:webApp:jsBrowserDevelopmentRun
```

Visit http://localhost:8081/ and test keypair generation

**Expected result**: No changes, should work as before

## Rollback Plan

If the implementation doesn't work:
1. Delete the test setup files
2. Revert build.gradle.kts changes
3. SDK code remains unchanged, so no rollback needed there
4. Web app continues to work

## Success Criteria

- ✅ Node.js tests pass (Mocha)
- ✅ Browser tests pass (Karma)
- ✅ Web app still works
- ✅ No SDK code changes
- ✅ All other platform tests still pass (JVM, Native, macOS, iOS)

## Future Improvements

If this approach works, consider:
1. Adding timeout configuration for slower environments
2. Creating similar setup for the sample app tests
3. Documenting the test setup approach in CLAUDE.md
4. Adding retry logic for flaky test environments

## Notes

- This approach follows the principle of "environment-specific configuration"
- SDK remains clean and production-focused
- Test infrastructure handles test-specific requirements
- Similar to how test frameworks use setup/teardown hooks
- Aligns with how webpack handles the web app (pre-loading dependencies)
