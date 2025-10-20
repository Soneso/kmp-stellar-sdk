# Deploy Smart Contract Feature

This document describes the comprehensive "Deploy a Smart Contract" feature implemented for the Stellar SDK demo app.

## Overview

This feature showcases the SDK's high-level contract deployment API (`ContractClient.deploy()`) that handles:
- WASM bytecode upload to the network
- Contract instance deployment from WASM
- Constructor invocation with automatic type conversion
- Transaction building, simulation, and submission

## Implementation Components

### 1. WASM Contract Resources

**Location**: `demo/shared/src/commonMain/resources/wasm/`

Five Soroban contracts copied from SDK test resources:
- `soroban_hello_world_contract.wasm` (539 bytes) - Simple greeting contract
- `soroban_token_contract.wasm` (7.1 KB) - Full-featured SAC-compatible token
- `soroban_events_contract.wasm` (742 bytes) - Event emission demo
- `soroban_auth_contract.wasm` (988 bytes) - Authorization patterns
- `soroban_atomic_swap_contract.wasm` (2.0 KB) - Multi-party swap

### 2. Business Logic Module

**Location**: `demo/shared/src/commonMain/kotlin/com/soneso/demo/stellar/DeployContract.kt`

**Key Components**:

#### ContractMetadata
Data class containing contract information for UI display:
- `id` - Unique identifier
- `name` - Display name
- `description` - What the contract does
- `wasmFilename` - Resource file name
- `hasConstructor` - Whether constructor is required
- `constructorParams` - List of constructor parameter definitions

#### ConstructorParam
Parameter definition with:
- `name` - Parameter name (matches contract spec)
- `type` - ConstructorParamType (ADDRESS, STRING, U32)
- `description` - User-facing description
- `placeholder` - Example value

#### deployContract()
Main deployment function demonstrating SDK usage:

```kotlin
suspend fun deployContract(
    contractMetadata: ContractMetadata,
    constructorArgs: Map<String, Any?>,
    sourceAccountId: String,
    secretKey: String,
    useTestnet: Boolean = true
): DeployContractResult
```

**What it demonstrates**:
1. Input validation (account ID, secret key, constructor args)
2. WASM resource loading (platform-specific)
3. KeyPair creation from secret seed
4. ContractClient.deploy() usage with automatic type conversion
5. Comprehensive error handling with specific exception types

**Type Conversion**:
The SDK automatically converts Kotlin types to Soroban XDR:
- `String` (G...) → `Address` (SCAddressXdr)
- `String` (regular) → `String` (SCValXdr.String)
- `Int`/`UInt` → `u32` (SCValXdr.U32)
- Maps are converted by parameter name matching

### 3. Platform-Specific Resource Loading

**expect/actual pattern** for loading WASM from resources:

#### JVM/Desktop/Android
**Location**: `demo/shared/src/jvmMain/kotlin/com/soneso/demo/stellar/DeployContract.jvm.kt`

Uses Java ClassLoader:
```kotlin
object {}.javaClass.classLoader.getResourceAsStream("wasm/$wasmFilename")
```

#### JavaScript
**Location**: `demo/shared/src/jsMain/kotlin/com/soneso/demo/stellar/DeployContract.js.kt`

Uses Node.js `fs` module with multiple path attempts for webpack compatibility.

#### Native (iOS/macOS)
**Locations**: 
- `demo/shared/src/iosMain/kotlin/com/soneso/demo/stellar/DeployContract.ios.kt`
- `demo/shared/src/macosMain/kotlin/com/soneso/demo/stellar/DeployContract.macos.kt`

Uses Foundation Bundle APIs:
```kotlin
NSBundle.mainBundle.pathForResource(resourceName, "wasm")
```


### 4. UI Screen

**Location**: `demo/shared/src/commonMain/kotlin/com/soneso/demo/ui/screens/DeployContractScreen.kt`

**Features**:
- Contract selection dropdown with descriptions
- Source account ID input (G...)
- Secret key input (S...)
- Dynamic constructor argument inputs (shown/hidden based on contract)
- Type-specific validation (addresses, numbers, strings)
- Deploy button with loading state
- Success display with contract ID
- Error display with troubleshooting tips

**UI Pattern**:
Follows Material 3 design with:
- Cards for logical grouping
- OutlinedTextField for inputs
- ExposedDropdownMenuBox for contract selection
- Comprehensive validation with inline error messages
- Success/Error states with appropriate colors

**Constructor Arguments**:
When a contract with constructor is selected (e.g., token), the UI dynamically shows:
- Admin address field (validated as G... address)
- Decimal field (validated as integer)
- Name field (string)
- Symbol field (string)

### 5. Navigation Integration

**Location**: `demo/shared/src/commonMain/kotlin/com/soneso/demo/ui/screens/MainScreen.kt`

Added between "Send a Payment" and "Fetch Smart Contract Details":
```kotlin
DemoTopic(
    title = "Deploy a Smart Contract",
    description = "Upload and deploy Soroban contracts with constructor support",
    icon = Icons.Default.CloudUpload,
    screen = DeployContractScreen()
)
```

## Available Contracts

### 1. Hello World
- **Constructor**: None
- **Purpose**: Simple greeting function
- **Function**: `hello(to: Symbol) -> Vec<Symbol>`
- **Use Case**: Introduction to Soroban contracts

### 2. Token Contract
- **Constructor**: Required (4 parameters)
  - `admin: Address` - Administrator address
  - `decimal: u32` - Number of decimal places (typically 7)
  - `name: String` - Token name
  - `symbol: String` - Token symbol
- **Purpose**: Full-featured SAC-compatible token
- **Functions**: mint, transfer, balance, approve, allowance, etc.
- **Use Case**: Creating custom tokens on Stellar

### 3. Events Contract
- **Constructor**: None
- **Purpose**: Demonstrates event emission
- **Use Case**: Off-chain monitoring and logging

### 4. Auth Contract
- **Constructor**: None
- **Purpose**: Authorization patterns
- **Function**: `increment(user: Address, value: u32) -> u32`
- **Use Case**: Signature verification and access control

### 5. Atomic Swap
- **Constructor**: None
- **Purpose**: Multi-party token exchange
- **Use Case**: Trustless swaps between two parties

## Usage Flow

1. **Select Contract**: Choose from dropdown (5 options)
2. **View Description**: Contract purpose displayed automatically
3. **Enter Source Account**: Account that will pay for deployment
4. **Enter Secret Key**: Private key for signing
5. **Fill Constructor Args** (if required): Dynamic fields appear
6. **Deploy**: Click button to initiate deployment
7. **View Result**: Contract ID displayed on success

## Technical Implementation

### Validation
- **Account ID**: Must start with 'G', be 56 characters
- **Secret Key**: Must start with 'S', be 56 characters
- **Constructor Args**: Type-specific validation (addresses, integers, strings)

### Error Handling
Catches and displays:
- `ContractException` - Soroban contract errors
- `SorobanRpcException` - RPC communication errors
- `IllegalArgumentException` - Validation errors
- Generic `Exception` - Unexpected errors

### Troubleshooting Tips
Displayed on error:
- Verify account has sufficient XLM (100+ recommended)
- Ensure account exists on testnet (use FundBot first)
- Check secret key matches account ID
- Verify constructor arguments match expected types
- Check internet connection and RPC availability

## Platform Support

- **JVM/Desktop**: ✅ Full support
- **Android**: ✅ Full support
- **iOS**: ✅ Full support (requires WASM files in bundle)
- **macOS**: ✅ Full support (requires WASM files in bundle)
- **JavaScript**: ✅ Node.js support

## Testing

### Desktop (Fastest)
```bash
./gradlew :demo:desktopApp:run
```

### Android
```bash
./gradlew :demo:androidApp:installDebug
# Open app on device/emulator
```

### iOS
```bash
./gradlew :demo:shared:linkDebugFrameworkIosSimulatorArm64
cd demo/iosApp && xcodegen generate && open StellarDemo.xcodeproj
# Ensure WASM files are in Xcode project resources
```

## Educational Value

This feature demonstrates:
1. **SDK Usage**: `ContractClient.deploy()` one-step deployment
2. **Type Conversion**: Automatic Map<String, Any?> → XDR conversion
3. **Resource Management**: Cross-platform WASM loading
4. **Error Handling**: Comprehensive exception handling
5. **UI Patterns**: Material 3 design, dynamic forms
6. **Validation**: Multi-level input validation
7. **Real Network**: Live testnet deployment

## References

- **SDK Integration Tests**: `stellar-sdk/src/commonTest/kotlin/com/soneso/stellar/sdk/integrationTests/SorobanClientIntegrationTest.kt`
- **ContractClient API**: `stellar-sdk/src/commonMain/kotlin/com/soneso/stellar/sdk/contract/ContractClient.kt`
- **Soroban Docs**: https://developers.stellar.org/docs/smart-contracts/
- **Example Contracts**: Used in SDK integration tests

## Notes

- All contracts deployed to **Stellar testnet** (not mainnet)
- Source account must have sufficient XLM balance
- Constructor arguments must match contract specification exactly
- WASM files are ~15 KB total (small enough for app bundle)
- Feature works on all Compose-based platforms automatically
