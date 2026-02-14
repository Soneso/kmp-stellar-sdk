# Testing Guide

The SDK includes comprehensive test coverage across all supported platforms: JVM, JavaScript (Node.js and Browser), macOS native, and iOS simulator.

## Test Organization

Tests are organized into two categories under `commonTest`:

- **`unitTests/`** — Pure unit tests with no network dependencies.
- **`integrationTests/`** — Tests that validate against the live Stellar Testnet. These require network access and take longer to run.

Both categories run by default in local and IDE environments. In CI, integration tests are excluded via the `-PexcludeIntegrationTests` Gradle property.

## Running Tests

### JVM

```bash
# All tests (unit + integration)
./gradlew :stellar-sdk:jvmTest

# Unit tests only (excludes integration)
./gradlew :stellar-sdk:jvmTest -PexcludeIntegrationTests

# Specific test class
./gradlew :stellar-sdk:jvmTest --tests "KeyPairTest"

# Pattern matching
./gradlew :stellar-sdk:jvmTest --tests "*Key*"
```

### JavaScript (Node.js)

```bash
# All tests (unit + integration)
./gradlew :stellar-sdk:jsNodeTest

# Unit tests only
./gradlew :stellar-sdk:jsNodeTest -PexcludeIntegrationTests

# Specific test class
./gradlew :stellar-sdk:jsNodeTest --tests "KeyPairTest"
```

### JavaScript (Browser)

Requires Chrome installed. The Gradle build handles Karma configuration and WASM file serving automatically.

```bash
# All tests (unit + integration)
./gradlew :stellar-sdk:jsBrowserTest

# Unit tests only
./gradlew :stellar-sdk:jsBrowserTest -PexcludeIntegrationTests

# Specific test class
./gradlew :stellar-sdk:jsBrowserTest --tests "KeyPairTest"
```

### Native (macOS)

```bash
# macOS ARM (Apple Silicon)
./gradlew :stellar-sdk:macosArm64Test

# macOS x86_64
./gradlew :stellar-sdk:macosX64Test

# Unit tests only
./gradlew :stellar-sdk:macosArm64Test -PexcludeIntegrationTests
```

### Native (iOS Simulator)

```bash
# Requires Xcode and an iOS simulator runtime
./gradlew :stellar-sdk:iosSimulatorArm64Test
```

**Note:** iOS simulator integration tests are currently skipped. The simulator does not trust the Sectigo root CA used by Stellar's servers (`NSURLErrorDomain Code=-1202`), and `xcrun simctl keychain add-root-cert` does not reliably resolve this. Unit tests run fine on iOS simulator. Integration tests are validated on macOS native, JVM, and JS Node instead.

## Integration Tests

Integration tests run against the live Stellar Testnet. Test accounts are automatically funded via Friendbot. Requires connectivity to `https://soroban-testnet.stellar.org`.

```bash
# Run a specific integration test class
./gradlew :stellar-sdk:jvmTest --tests "*SorobanIntegrationTest"

# Exclude integration tests (used in CI)
./gradlew :stellar-sdk:jvmTest -PexcludeIntegrationTests
```

### Timing and Delays

Integration tests use `realDelay()` instead of `delay()` for wait operations. This is necessary because `runTest` uses a virtual time scheduler that skips `delay()` calls. On platforms with fully async I/O (JS Node, JS Browser), this would cause polling loops to fire too fast without real wall-clock delays.

The `realDelay()` function (in `integrationTests/TestUtils.kt`) delegates to `platformDelay()`, which uses platform-native timing: `Thread.sleep` on JVM, `setTimeout` on JS, and `kotlinx.coroutines.delay` with `Dispatchers.Default` on Native.

## Code Coverage

Coverage is collected via [Kover](https://github.com/Kotlin/kotlinx-kover) on JVM tests:

```bash
# Generate HTML and XML coverage reports
./gradlew :stellar-sdk:koverHtmlReport :stellar-sdk:koverXmlReport
```

Reports are generated at:
- HTML: `stellar-sdk/build/reports/kover/html/index.html`
- XML: `stellar-sdk/build/reports/kover/report.xml`

Coverage is uploaded to [Codecov](https://codecov.io) automatically in CI (requires `CODECOV_TOKEN` secret in GitHub repository settings).

## CI Workflow

The GitHub Actions workflow (`.github/workflows/tests.yml`) runs JVM unit tests across JDK 17, 21, and 25 (integration tests excluded via `-PexcludeIntegrationTests`). Coverage is collected on JDK 25 and uploaded to Codecov.

JS, macOS native, and iOS simulator tests are run locally. They share the same `commonTest` sources and the same exclusion flag works across all platforms.
