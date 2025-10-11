# ContractClient Integration Tests

## Overview

This directory contains comprehensive integration tests for **ContractClient** and **AssembledTransaction** that validate the complete contract invocation workflow against a live Stellar Testnet.

**Note:** Integration tests always require network access and automatically fund test accounts via Friendbot. They are not marked with `@Ignore` and will run whenever tests are executed.

## Test Coverage

### Scenarios Tested

1. **Read-Only Function Invocation** (`testReadOnlyFunction`)
   - Invokes contract without signing
   - Validates read-call detection
   - Verifies result parsing from simulation

2. **Write Function Invocation** (`testWriteFunction`)
   - Invokes contract with state modification
   - Signs and submits transaction
   - Verifies state changes on-chain

3. **Multi-Signature Authorization** (`testMultiSignatureAuthorization`)
   - Tests authorization entry signing
   - Validates `needsNonInvokerSigningBy()` API
   - Demonstrates multi-party authorization flow

4. **Automatic State Restoration** (`testAutomaticStateRestoration`)
   - Tests automatic footprint restoration
   - Validates re-simulation after restore
   - Ensures seamless user experience

5. **Error Handling - Invalid Parameters** (`testErrorHandlingInvalidParameters`)
   - Tests parameter validation
   - Verifies error messages are helpful
   - Validates simulation failure handling

6. **Error Handling - Wrong Signer** (`testErrorHandlingWrongSigner`)
   - Tests authorization failures
   - Validates signature verification
   - Ensures security invariants

7. **Result Parsing - Custom Types** (`testResultParsingCustomTypes`)
   - Demonstrates custom result parsers
   - Tests complex type handling (maps, vectors, structs)
   - Validates type conversion correctness

8. **Poll Timeout Behavior** (`testPollTimeoutBehavior`)
   - Tests transaction polling with short timeout
   - Validates exponential backoff
   - Verifies `TransactionStillPendingException` behavior

9. **Read vs Write Detection** (`testReadVsWriteDetection`)
   - Tests `isReadCall()` accuracy
   - Validates automatic call type detection
   - Ensures correct workflow routing

10. **Separate Sign and Submit** (`testSeparateSignAndSubmit`)
    - Demonstrates step-by-step workflow
    - Tests fine-grained control
    - Validates intermediate state access

## Prerequisites

### 1. Network Access

These tests require connectivity to Stellar Testnet RPC server:
```
https://soroban-testnet.stellar.org:443
```

Ensure your environment can reach this endpoint.

### 2. Test Account Funding

The tests automatically generate random keypairs and fund them via Friendbot. No manual funding is required.

### 3. Contract Deployment

The tests use a pre-deployed auth contract on testnet:
```
CDCYWK73YTYFJZZSJ5V7EDFNHYBG4QN3VUNG2IGD27KJDDPNCZKBCBXK
```

To deploy your own contract:

1. **Install Soroban CLI**:
   ```bash
   cargo install --locked soroban-cli
   ```

2. **Deploy Contract**:
   ```bash
   # Upload WASM
   soroban contract deploy \
     --wasm /path/to/soroban_auth_contract.wasm \
     --source YOUR_SECRET_KEY \
     --rpc-url https://soroban-testnet.stellar.org:443 \
     --network-passphrase "Test SDF Network ; September 2015"
   ```

3. **Update Test**:
   Replace `PRE_DEPLOYED_CONTRACT_ID` in `ContractClientIntegrationTest.kt`

## Running the Tests

Integration tests require live network access and use Friendbot to automatically fund test accounts.

### Run Tests

```bash
# Run all integration tests
./gradlew :stellar-sdk:jvmTest --tests "ContractClientIntegrationTest"

# Run a specific test
./gradlew :stellar-sdk:jvmTest --tests "ContractClientIntegrationTest.testReadOnlyFunction"

# Run with verbose output
./gradlew :stellar-sdk:jvmTest --tests "ContractClientIntegrationTest" --info
```

### Review Results

Tests will automatically fund accounts via Friendbot and print detailed output:
```
=== ContractClient Integration Test Setup ===
Test account: GABC123...
Fund this account via: https://friendbot.stellar.org?addr=GABC123...

Using contract: CDCYWK73YTYFJZZSJ5V7EDFNHYBG4QN3VUNG2IGD27KJDDPNCZKBCBXK

Read-only call successful. Count: 0
Count before: 0
Write call successful. New count: 5
...
```

## Platform-Specific Notes

### JVM (Recommended)

Full support, no special configuration needed:

```bash
./gradlew :stellar-sdk:jvmTest --tests "ContractClientIntegrationTest"
```

### JavaScript (Node.js)

Requires Node.js with network access:

```bash
# Run specific test on Node.js
./gradlew :stellar-sdk:jsNodeTest --tests "ContractClientIntegrationTest.testReadOnlyFunction"
```

**Note:** May require CORS proxy or different RPC endpoint for browser tests.

### Native (iOS/macOS)

Requires platform-specific HTTP client configuration:

```bash
# macOS
./gradlew :stellar-sdk:macosArm64Test --tests "ContractClientIntegrationTest"

# iOS Simulator
./gradlew :stellar-sdk:iosSimulatorArm64Test --tests "ContractClientIntegrationTest"
```

**Note:** Native platforms may have different network stack behavior.

## Test Duration

Integration tests are slower than unit tests due to network latency:

- **Single test**: 10-30 seconds
- **Full suite**: 3-5 minutes
- **With retries**: Up to 10 minutes

**Recommendation:** Run integration tests separately from unit tests in CI/CD.

## CI/CD Integration

### GitHub Actions Example

```yaml
name: Integration Tests

on:
  schedule:
    # Run nightly
    - cron: '0 2 * * *'
  workflow_dispatch:
    # Manual trigger

jobs:
  integration-tests:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3

      - name: Setup JDK
        uses: actions/setup-java@v3
        with:
          distribution: 'temurin'
          java-version: '17'

      - name: Run Integration Tests
        run: |
          ./gradlew :stellar-sdk:jvmTest \
            --tests "ContractClientIntegrationTest"
```

### GitLab CI Example

```yaml
integration-tests:
  stage: test
  only:
    - schedules
    - web
  script:
    - ./gradlew :stellar-sdk:jvmTest --tests "ContractClientIntegrationTest"
  timeout: 15m
  retry: 2
```

## Troubleshooting

### Test Hangs or Timeouts

**Symptom:** Test hangs indefinitely or times out.

**Solutions:**
1. Check network connectivity to testnet RPC
2. Verify test account is funded
3. Increase `submitTimeout` in test configuration
4. Check testnet status: https://status.stellar.org/

### Simulation Failures

**Symptom:** `SimulationFailedException` thrown.

**Solutions:**
1. Verify contract is deployed correctly
2. Check contract function exists
3. Validate parameter types match contract expectations
4. Review testnet logs for detailed errors

### Transaction Failures

**Symptom:** `TransactionFailedException` after submission.

**Solutions:**
1. Ensure account has sufficient XLM balance
2. Check authorization requirements
3. Verify signatures are correct
4. Review transaction result XDR for error details

### Account Not Found

**Symptom:** `AccountNotFoundException` during setup.

**Solutions:**
1. Fund the test account via Friendbot
2. Wait a few seconds for account creation
3. Verify account ID is correct

### State Restoration Issues

**Symptom:** State restoration tests fail.

**Solutions:**
1. Contract state may not be expired yet (expected)
2. Wait for state to expire naturally (can take days)
3. Manually trigger state expiration via Soroban CLI
4. Skip this test if contract state is fresh

## Test Contract Details

### Auth Contract Functions

The test contract (`soroban_auth_contract.wasm`) provides:

#### `increment(user: Address, value: u32) -> u32`

Increments a counter for the given user.

**Parameters:**
- `user`: Account or contract address
- `value`: Amount to increment

**Returns:** New counter value

**Authorization:** Requires signature from `user`

**State:** Modifies persistent storage

#### `get_count(user: Address) -> u32`

Gets the current counter for a user.

**Parameters:**
- `user`: Account or contract address

**Returns:** Current counter value

**Authorization:** None required

**State:** Read-only

### Contract Storage

The contract uses persistent storage:

```rust
key: LedgerKey::ContractData {
    contract: Address,
    key: Symbol("count_{user}"),
    durability: Persistent
}
```

Storage can expire and require restoration after ~100,000 ledgers (~5.5 days on testnet).

## Performance Benchmarks

Typical execution times on testnet (as of October 2025):

| Operation | Duration | Notes |
|-----------|----------|-------|
| Simulation | 2-5 seconds | Network latency dependent |
| Transaction submission | 5-10 seconds | Depends on network congestion |
| State restoration | 10-15 seconds | Includes re-simulation |
| Full test suite | 3-5 minutes | 10 tests |

**Note:** Times vary based on network conditions and geographic location.

## Account Integration Tests

The SDK also includes comprehensive account integration tests in `AccountIntegrationTest.kt`:

| Test | Description | Features Tested |
|------|-------------|-----------------|
| `testSetAccountOptions` | Tests SetOptions operation | Thresholds, signers, flags, home domain |
| `testFindAccountsForAsset` | Tests finding accounts holding an asset | CreateAccount, ChangeTrust operations |
| `testAccountMerge` | Tests merging accounts | AccountMerge operation |
| `testAccountMergeMuxedAccounts` | Tests account merge with muxed accounts | Muxed account IDs |
| `testBumpSequence` | Tests sequence number bumping | BumpSequence operation |
| `testManageData` | Tests data storage | ManageData operation (set/delete) |
| `testMuxedAccountIdParsing` | Tests muxed account ID parsing | M... address parsing |
| `testAccountDataEndpoint` | Tests account data retrieval | AccountData endpoint |
| `testStreamTransactionsForAccount` | Tests transaction streaming | SSE streaming, EventListener, cursor("now") |

## Query Integration Tests

The SDK includes comprehensive Horizon query integration tests in `QueryIntegrationTest.kt`:

| Test | Description | Features Tested |
|------|-------------|-----------------|
| `testQueryAccounts` | Tests account queries | forSigner, forAsset, pagination, ordering |
| `testQueryAssets` | Tests asset queries | assetCode, assetIssuer filters, asset statistics |
| `testQueryEffects` | Tests effects queries | forAccount, forLedger, forTransaction, forOperation |
| `testQueryOperationsForClaimableBalance` | Tests operations for claimable balance | Hex and base58 claimable balance IDs |
| `testQueryTransactionsForClaimableBalance` | Tests transactions for claimable balance | Claimable balance transaction queries |
| `testQueryLedgers` | Tests ledger queries | Latest ledger, specific ledger by sequence |
| `testQueryFeeStats` | Tests fee statistics queries | Fee charged, max fee, percentiles |
| `testQueryOffersAndOrderBook` | Tests offers and order book | forAccount, forBuyingAsset, order book bids/asks |
| `testQueryStrictSendReceivePathsAndTrades` | Tests path payments and trades | Strict send paths, strict receive paths, trade execution, trade streaming |
| `testQueryRoot` | Tests root endpoint | Server version, protocol version |

### Query Test Details

The query integration tests validate the SDK's ability to query various Horizon endpoints:

1. **Account Queries** (`testQueryAccounts`):
   - Creates multiple accounts with shared signers
   - Tests `forSigner()` query with pagination
   - Creates custom assets with trustlines
   - Tests `forAsset()` query with pagination
   - Validates ordering (ASC/DESC) and limits

2. **Asset Queries** (`testQueryAssets`):
   - Queries assets by code and issuer
   - Validates asset statistics (accounts, balances, claimable balances)
   - Tests pagination and ordering

3. **Effects Queries** (`testQueryEffects`):
   - Queries effects for accounts, ledgers, transactions, operations
   - Validates effect types (AccountCreatedEffectResponse, etc.)
   - Tests pagination across different query types

4. **Claimable Balance Queries**:
   - Queries operations and transactions for claimable balances
   - Tests both hex and base58 encoded balance IDs

5. **Ledger Queries** (`testQueryLedgers`):
   - Queries latest ledger with ordering
   - Queries specific ledger by sequence number

6. **Fee Stats Queries** (`testQueryFeeStats`):
   - Validates fee statistics fields (min, max, mode, percentiles)
   - Tests both `feeCharged` and `maxFee` statistics

7. **Offers and Order Book** (`testQueryOffersAndOrderBook`):
   - Creates offers using ManageBuyOffer
   - Queries offers by account and buying asset
   - Queries order book with both asset orderings
   - Validates bids, asks, base, and counter assets

8. **Path Payments and Trades** (`testQueryStrictSendReceivePathsAndTrades`):
   - Sets up complex trading paths with multiple assets
   - Tests strict send paths (source asset/amount → destination)
   - Tests strict receive paths (source → destination asset/amount)
   - Executes PathPaymentStrictSend and PathPaymentStrictReceive
   - Queries and validates trades
   - Tests trade streaming with SSE

9. **Root Endpoint** (`testQueryRoot`):
   - Queries server version and network information
   - Validates protocol versions

### Running Query Integration Tests

```bash
# Run all query integration tests
./gradlew :stellar-sdk:jvmTest --tests "QueryIntegrationTest"

# Run a specific query test
./gradlew :stellar-sdk:jvmTest --tests "QueryIntegrationTest.testQueryRoot"
./gradlew :stellar-sdk:jvmTest --tests "QueryIntegrationTest.testQueryAccounts"
./gradlew :stellar-sdk:jvmTest --tests "QueryIntegrationTest.testQueryFeeStats"

# Run with verbose output
./gradlew :stellar-sdk:jvmTest --tests "QueryIntegrationTest" --info
```

**Note:** Query tests automatically fund accounts using FriendBot, so no manual setup is required beyond having network connectivity.

### Streaming Test Details

The `testStreamTransactionsForAccount` test demonstrates SSE (Server-Sent Events) streaming:

1. **Setup**: Creates and funds two accounts (A and B)
2. **Stream Start**: Opens stream for account A's transactions with `cursor("now")`
3. **Event Flow**:
   - Submits first payment from B to A
   - Stream receives event and triggers next payment
   - Process repeats until 3 events are received
4. **Verification**: Validates event count and operation details
5. **Cleanup**: Properly closes the stream

**Key Features Demonstrated**:
- SSE streaming with `EventListener<TransactionResponse>`
- Cursor management (`cursor("now")`)
- Thread-safe event handling with `@Volatile`
- Automatic stream reconnection
- Proper resource cleanup

## Known Limitations

### 1. Test Account Funding

Each test run generates a new keypair and automatically funds it via Friendbot. Tests may fail if Friendbot is unavailable or rate-limited.

### 2. Contract Deployment

Tests use a pre-deployed contract. For better isolation:
- Deploy contract per test run
- Clean up contracts after tests
- Use contract deployment automation

### 3. State Expiration Testing

Testing state restoration requires waiting for natural expiration:
- Can take days for state to expire
- Consider manual expiration triggers
- Or mock restoration in unit tests

### 4. Network Dependency

Tests require live network access:
- Can fail if testnet is down
- Affected by network congestion
- Geographic latency impacts duration

### 5. Streaming Tests

The streaming test (`testStreamTransactionsForAccount`) requires:
- Stable network connection for SSE
- Proper event timing (30 second wait)
- Platform-specific SSE implementation (JVM, JS, Native)
- Note: Stream events may arrive at different rates based on network conditions

## Future Enhancements

Potential improvements to integration tests:

1. **Contract Deployment Automation**
   - Deploy fresh contract per test run
   - Clean up after tests complete
   - Use contract deployment fixtures

2. **Parallel Test Execution**
   - Use separate accounts per test
   - Eliminate state conflicts
   - Reduce total test time

3. **Network Mocking Option**
   - Provide mock network mode for CI
   - Record/replay network interactions
   - Faster feedback in development

4. **Enhanced Error Reporting**
   - Capture full transaction XDR
   - Log detailed simulation responses
   - Include network diagnostics

## References

- **Soroban Documentation**: https://soroban.stellar.org/docs
- **Stellar Testnet**: https://stellar.org/developers/guides/get-started/test-network
- **Friendbot**: https://friendbot.stellar.org
- **Testnet Status**: https://status.stellar.org/
- **Java SDK Tests**: `/Users/chris/projects/Stellar/java-stellar-sdk/src/test/java/org/stellar/sdk/SorobanServerTest.java`

## Support

For issues with integration tests:

1. Check this README for troubleshooting steps
2. Review test output for error details
3. Verify testnet connectivity and status
4. Check contract deployment and funding
5. Open an issue with detailed logs

---

**Last Updated:** October 5, 2025
**Test Framework Version:** Kotlin 2.0.20
**KMP Stellar SDK Version:** 0.1.0-SNAPSHOT
