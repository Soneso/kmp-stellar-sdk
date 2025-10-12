# Integration Tests README

This directory contains integration tests for the KMP Stellar SDK that verify functionality against live Stellar networks (testnet/futurenet).

## Overview

Integration tests are different from unit tests:
- **Unit tests**: Fast, isolated, test individual components with mocked dependencies
- **Integration tests**: Slower, require network access, test real interactions with Stellar networks

## Test Files

### AccountIntegrationTest.kt

Comprehensive tests for account-related operations:

| Test | Description | Operations Tested |
|------|-------------|-------------------|
| `testSetAccountOptions` | Set account thresholds, signers, flags, home domain | SetOptions |
| `testFindAccountsForAsset` | Create accounts and trustlines, query by asset | CreateAccount, ChangeTrust |
| `testAccountMerge` | Merge two accounts | AccountMerge |
| `testAccountMergeMuxedAccounts` | Merge accounts using muxed account IDs | AccountMerge (with M... addresses) |
| `testBumpSequence` | Bump account sequence number | BumpSequence |
| `testManageData` | Set and delete account data entries | ManageData |
| `testMuxedAccountIdParsing` | Parse M... addresses | N/A (unit test) |
| `testAccountDataEndpoint` | Retrieve account data via endpoint | AccountData API |
| `testStreamTransactionsForAccount` | Stream transactions for an account | Transaction streaming |

### ClaimableBalanceIntegrationTest.kt

Comprehensive tests for claimable balance operations:

| Test | Description | Operations Tested |
|------|-------------|-------------------|
| `testClaimableBalance` | Complete claimable balance workflow with complex predicates | CreateClaimableBalance, ClaimClaimableBalance |

**Test Details**: `testClaimableBalance`
- Creates claimable balance with two claimants
- First claimant: Unconditional predicate
- Second claimant: Complex nested predicate (And, Or, Not, BeforeAbsoluteTime, BeforeRelativeTime)
- Tests StrKey encoding/decoding for claimable balance IDs (hex ↔ B... format)
- Queries claimable balances by claimant
- Claims the balance
- Verifies all operations and effects

**Predicate Structure**:
```kotlin
predicateA = BeforeRelativeTime(100)
predicateB = BeforeAbsoluteTime(1634000400)
predicateC = Not(predicateA)
predicateD = And(predicateC, predicateB)
predicateE = BeforeAbsoluteTime(1601671345)
predicateF = Or(predicateD, predicateE)
```

### ContractClientIntegrationTest.kt

Tests for Soroban smart contract interactions:
- Contract deployment
- Contract invocation
- Authorization handling
- Transaction simulation and submission

See `contract/INTEGRATION_TESTS_README.md` for details.

### FriendBot.kt

Helper utility for funding test accounts:
```kotlin
// Fund a testnet account
FriendBot.fundTestnetAccount(keypair.getAccountId())

// Fund a futurenet account
FriendBot.fundFuturenetAccount(keypair.getAccountId())
```

### SorobanIntegrationTest.kt

Comprehensive tests for Soroban RPC server operations against live testnet:

| Test | Description | RPC Methods Tested |
|------|-------------|-------------------|
| `testServerHealth` | Server health check and ledger retention | getHealth |
| `testServerVersionInfo` | RPC server version information | getVersionInfo |
| `testServerFeeStats` | Fee statistics for Soroban operations | getFeeStats |
| `testNetworkRequest` | Network configuration and passphrase | getNetwork |
| `testGetLatestLedger` | Latest ledger information | getLatestLedger |
| `testServerGetTransactions` | Transaction history queries with pagination | getTransactions |
| `testBasicGetLedgersWithLimit` | Basic getLedgers request with pagination limit | getLedgers |
| `testGetLedgersPaginationWithCursor` | Cursor-based pagination for ledgers | getLedgers |
| `testGetLedgersSequenceOrdering` | Verifies ledgers are returned in ascending order | getLedgers |
| `testGetLedgersWithoutPagination` | getLedgers with default pagination | getLedgers |
| `testGetLedgersWithDifferentLimits` | Tests various pagination limits | getLedgers |
| `testGetLedgersValidatesAllFields` | Comprehensive response field validation | getLedgers |
| `testGetLedgersErrorHandlingInvalidStartLedger` | Error handling for invalid parameters | getLedgers |
| `testGetLedgersWithRecentLedger` | Retrieves very recent ledger data | getLedgers |
| `testUploadContract` | Upload Soroban contract WASM | simulateTransaction, sendTransaction |
| `testCreateContract` | Deploy Soroban contract instance | simulateTransaction, sendTransaction |
| `testInvokeContract` | Invoke Soroban contract function | simulateTransaction, sendTransaction |
| `testGetLedgerEntries` | Retrieve ledger entries by key | getLedgerEntries |
| `testEvents` | Contract events emission and querying | getEvents |
| `testDeploySACWithSourceAccount` | Deploy Stellar Asset Contract (native) | simulateTransaction, sendTransaction |
| `testSACWithAsset` | Deploy Stellar Asset Contract (custom asset) | simulateTransaction, sendTransaction |

**GetLedgers Test Suite Details**:

All `getLedgers` tests were ported from the Flutter Stellar SDK to ensure feature parity and validate the getLedgers RPC method:

1. **testBasicGetLedgersWithLimit**: Tests basic ledger retrieval with pagination, validates response structure (cursor, timestamps, ledger info with hash/sequence/XDR)
2. **testGetLedgersPaginationWithCursor**: Validates cursor-based pagination where startLedger is omitted when using cursor
3. **testGetLedgersSequenceOrdering**: Ensures ledgers are returned in ascending sequence order for blockchain continuity
4. **testGetLedgersWithoutPagination**: Tests that pagination is optional and server provides sensible defaults
5. **testGetLedgersWithDifferentLimits**: Verifies pagination limit parameter works for various values (1, 10)
6. **testGetLedgersValidatesAllFields**: Comprehensive validation of all response fields (top-level and ledger info)
7. **testGetLedgersErrorHandlingInvalidStartLedger**: Tests graceful error handling for invalid start ledger (future ledger)
8. **testGetLedgersWithRecentLedger**: Validates retrieval of very recent ledger data for real-time applications

**Reference**: All getLedgers tests ported from Flutter SDK's `soroban_test.dart` (lines 1047-1345)

**Soroban Contract Workflow Tests**:

The contract tests demonstrate the complete Soroban smart contract lifecycle:

- **Upload**: Load WASM from resources → simulate → sign → submit → poll → extract WASM ID
- **Deploy**: Create contract from WASM ID → handle authorization → extract contract ID
- **Invoke**: Call contract function → pass parameters → parse return values
- **Events**: Query contract events by ID → validate topics and values
- **Ledger Entries**: Retrieve contract code and data using ledger keys from footprint

**Stellar Asset Contract (SAC) Tests**:

- **Native Asset**: Deploy SAC for XLM using CONTRACT_ID_PREIMAGE_FROM_ADDRESS
- **Custom Asset**: Create trustline → establish holdings → deploy SAC using CONTRACT_ID_PREIMAGE_FROM_ASSET

**Test Duration**: ~90-120 seconds for full contract tests (includes network delays and polling)

**Prerequisites**: Network connectivity to Stellar testnet Soroban RPC server


## Running Integration Tests

### Prerequisites

1. **Network Access**: Stable internet connection required
2. **FriendBot**: Testnet/futurenet must be accessible
3. **Time**: Tests take longer than unit tests (3-10 seconds each)

### Running Tests

Integration tests automatically fund accounts via Friendbot and run without manual intervention. To run:

```bash
# Run specific test class
./gradlew :stellar-sdk:jvmTest --tests "AccountIntegrationTest"

# Run specific test method
./gradlew :stellar-sdk:jvmTest --tests "AccountIntegrationTest.testBumpSequence"

# Run all integration tests
./gradlew :stellar-sdk:jvmTest --tests "com.stellar.sdk.integrationTests.*"

# Run on macOS
./gradlew :stellar-sdk:macosArm64Test --tests "AccountIntegrationTest"

# Run on iOS Simulator
./gradlew :stellar-sdk:iosSimulatorArm64Test --tests "AccountIntegrationTest"

# Note: JS tests may need to be run individually (see CLAUDE.md for JS testing notes)
```

## Test Networks

### Testnet (Default)

- **Horizon**: `https://horizon-testnet.stellar.org`
- **Network Passphrase**: `Test SDF Network ; September 2015`
- **FriendBot**: `https://friendbot.stellar.org`
- **Best for**: Most testing scenarios

### Futurenet

- **Horizon**: `https://horizon-futurenet.stellar.org`
- **Network Passphrase**: `Test SDF Future Network ; October 2022`
- **FriendBot**: `https://friendbot-futurenet.stellar.org`
- **Best for**: Testing upcoming protocol features

To switch networks, change the `testOn` variable in test files:

```kotlin
private val testOn = "futurenet"  // or "testnet"
```

## Troubleshooting

### Tests Timeout

**Symptom**: Tests fail with timeout error

**Solutions**:
1. Increase timeout in `runTest`:
   ```kotlin
   @Test
   fun testSomething() = runTest(timeout = 120_000) { // 2 minutes
       // ...
   }
   ```

2. Add more delays after transactions:
   ```kotlin
   horizonServer.submitTransaction(tx)
   delay(5000)  // Wait 5 seconds instead of 3
   ```

3. Check network status: https://status.stellar.org

### FriendBot Not Available

**Symptom**: `Failed to fund account` error

**Solutions**:
1. Check FriendBot status: https://status.stellar.org
2. Try again in a few minutes
3. Fund manually using Stellar Laboratory: https://laboratory.stellar.org
4. Switch to a different network (testnet ↔ futurenet)

### 404 Account Not Found

**Symptom**: `Account not found` or `404` error immediately after funding

**Solutions**:
1. Increase delay after funding:
   ```kotlin
   FriendBot.fundTestnetAccount(accountId)
   delay(5000)  // Wait longer
   ```

2. Verify account was actually funded:
   ```kotlin
   println("Funded: $accountId")
   println("Check: https://horizon-testnet.stellar.org/accounts/$accountId")
   ```

### Transaction Failed

**Symptom**: `Transaction failed` or `tx_failed` error

**Solutions**:
1. Check error details in response:
   ```kotlin
   val response = horizonServer.submitTransaction(tx)
   if (!response.successful) {
       println("Error: ${response.extras?.resultCodes}")
       println("XDR: ${response.extras?.resultXdr}")
   }
   ```

2. Common issues:
   - Insufficient balance (fee + reserve requirements)
   - Invalid sequence number (retry after loading account)
   - Invalid operation parameters
   - Account doesn't exist yet (wait longer after funding)

### Tests Pass on JVM but Fail on JS/Native

**Symptom**: Platform-specific failures

**Solutions**:
1. Check platform-specific crypto initialization (especially JS)
2. Verify HTTP client configuration for platform
3. Check for platform-specific timing issues (add longer delays)
4. See `CLAUDE.md` for known JS testing limitations

### Network Connectivity Issues

**Symptom**: `Connection refused`, `Timeout`, or `Network unreachable`

**Solutions**:
1. Check internet connection
2. Verify Horizon URLs are accessible:
   ```bash
   curl https://horizon-testnet.stellar.org/
   ```

3. Check firewall/proxy settings
4. Try using a different network or VPN

## Best Practices

### 1. Test Isolation

Each test should be independent:
```kotlin
@Test
fun testSomething() = runTest {
    // Create fresh keypairs for each test
    val keyPair = KeyPair.random()

    // Don't reuse accounts between tests
    FriendBot.fundTestnetAccount(keyPair.getAccountId())

    // ...
}
```

### 2. Proper Delays

Always add delays after network operations:
```kotlin
// After funding
FriendBot.fundTestnetAccount(accountId)
delay(3000)  // Wait for account creation

// After submitting transaction
horizonServer.submitTransaction(tx)
delay(3000)  // Wait for transaction to be processed

// Before querying results
delay(3000)  // Ensure data is available
```

### 3. Error Handling

Always verify transaction success:
```kotlin
val response = horizonServer.submitTransaction(tx)
assertTrue(response.successful, "Transaction should succeed")
assertNotNull(response.hash, "Should have transaction hash")
```

### 4. Resource Cleanup

Tests should clean up when possible:
```kotlin
@Test
fun testSomething() = runTest {
    val keyPair = KeyPair.random()
    try {
        // Test code
    } finally {
        // Cleanup if needed (merge account, etc.)
    }
}
```

### 5. Timeouts

Set appropriate timeouts based on operations:
```kotlin
// Simple tests
@Test
fun simpleTest() = runTest(timeout = 30_000) { ... }

// Complex tests with multiple transactions
@Test
fun complexTest() = runTest(timeout = 120_000) { ... }
```

## Missing Features

The following features from the Flutter SDK tests are not yet implemented in KMP SDK:

### Streaming API

**Status**: Not implemented

**Flutter Test**:
```dart
sdk.transactions
    .forAccount(accountId)
    .cursor("now")
    .stream()
    .listen((response) { ... })
```

**Workaround**: Poll for new transactions instead of streaming:
```kotlin
var lastCursor = "now"
while (keepPolling) {
    val page = horizonServer.transactions()
        .forAccount(accountId)
        .cursor(lastCursor)
        .limit(10)
        .execute()

    for (tx in page.records) {
        // Process transaction
    }

    lastCursor = page.records.lastOrNull()?.pagingToken ?: lastCursor
    delay(5000)  // Poll every 5 seconds
}
```

**Test Coverage**: The `stream transactions for account` test from Flutter SDK is not included.

## CI/CD Integration

### GitHub Actions

Example workflow:

```yaml
name: Integration Tests

on:
  schedule:
    - cron: '0 2 * * *'  # Run daily at 2 AM
  workflow_dispatch:  # Manual trigger

jobs:
  integration-tests:
    runs-on: ubuntu-latest

    steps:
      - uses: actions/checkout@v3

      - name: Set up JDK
        uses: actions/setup-java@v3
        with:
          java-version: '17'

      - name: Run integration tests
        run: ./gradlew :stellar-sdk:jvmTest --tests "com.stellar.sdk.integrationTests.*"
        timeout-minutes: 30

      - name: Report results
        if: failure()
        uses: actions/github-script@v6
        with:
          script: |
            github.rest.issues.create({
              owner: context.repo.owner,
              repo: context.repo.repo,
              title: 'Integration tests failed',
              body: 'Integration tests failed. Check the workflow run for details.'
            })
```

### GitLab CI

Example `.gitlab-ci.yml`:

```yaml
integration-tests:
  stage: test
  only:
    - schedules
    - web  # Manual trigger
  script:
    - ./gradlew :stellar-sdk:jvmTest --tests "com.stellar.sdk.integrationTests.*"
  timeout: 30m
  allow_failure: true  # Don't block pipeline on network issues
```

## Contributing

When adding new integration tests:

1. **Document the test** in this README
2. **Use descriptive names** that explain what's being tested
3. **Add proper delays** after network operations
4. **Handle errors gracefully** with meaningful assertions
5. **Follow existing patterns** for consistency
6. **Use FriendBot for automatic account funding**
7. **Test on multiple platforms** (JVM, JS, Native)

## Resources

- [Stellar Documentation](https://developers.stellar.org/)
- [Horizon API Reference](https://developers.stellar.org/api/horizon)
- [Stellar Laboratory](https://laboratory.stellar.org/) - Manual testing tool
- [Stellar Status](https://status.stellar.org/) - Network status
- [Stellar Discord](https://discord.gg/stellar) - Community support
- [Java Stellar SDK](https://github.com/stellar/java-stellar-sdk) - Reference implementation

## Support

If you encounter issues with integration tests:

1. Check this README's troubleshooting section
2. Verify network status at https://status.stellar.org
3. Review test output and error messages carefully
4. Try running individual tests instead of all at once
5. Ask in [Stellar Discord #sdk channel](https://discord.gg/stellar)
6. File an issue with:
   - Full error message
   - Test code
   - Platform (JVM/JS/Native)
   - Network used (testnet/futurenet)
