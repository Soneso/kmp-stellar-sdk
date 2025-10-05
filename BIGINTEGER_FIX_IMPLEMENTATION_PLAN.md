# BigInteger Fix Implementation Plan - expect/actual Solution

**Date:** October 5, 2025  
**Goal:** Fix 2 failing BigInteger tests using platform-specific implementations
**Approach:** Use Kotlin expect/actual to leverage Java's BigInteger on JVM while keeping cross-platform support

---

## Problem Summary

**Current Issue:**
- `ScvTest.testInt128()` and `ScvTest.testInt256()` fail on JVM and Native
- Kotlin bignum library's `BigInteger.fromByteArray()` treats bytes as magnitude+sign
- Java's `BigInteger(byte[])` treats bytes as signed two's complement
- Conversion between these representations fails for extreme values

**Impact:**
- 2/473 tests failing (99.6% pass rate)
- Affects only edge case values: -2^127, 2^127-1, -2^255, 2^255-1
- Core functionality works for all common use cases

---

## Solution: expect/actual Pattern

### Strategy

Create platform-specific BigInteger conversion functions:
- **JVM:** Use Java's `BigInteger(byte[])` directly (perfect compatibility)
- **JS/Native:** Keep current implementation or improve it

This gives us:
- ✅ 100% Java compatibility on JVM (most important platform)
- ✅ Works correctly on JS (already passing)
- ✅ Native can use same approach as JS or implement separately

---

## Implementation Steps

### Step 1: Create Common Expected Functions

**File:** `stellar-sdk/src/commonMain/kotlin/com/stellar/sdk/scval/BigIntegerConversion.kt`

```kotlin
package com.stellar.sdk.scval

import com.ionspin.kotlin.bignum.integer.BigInteger

/**
 * Convert bytes in two's complement format to BigInteger.
 * 
 * This is platform-specific because different BigInteger implementations
 * handle byte array interpretation differently.
 */
internal expect fun bytesToBigIntegerSigned(bytes: ByteArray): BigInteger

/**
 * Convert BigInteger to bytes in two's complement format.
 * 
 * This is platform-specific to ensure consistent behavior across platforms.
 */
internal expect fun bigIntegerToBytesSigned(value: BigInteger, byteCount: Int): ByteArray
```

**Why internal:**
- These are implementation details
- Only used by Scv utility
- Not part of public API

---

### Step 2: Implement JVM-Specific Version

**File:** `stellar-sdk/src/jvmMain/kotlin/com/stellar/sdk/scval/BigIntegerConversion.kt`

```kotlin
package com.stellar.sdk.scval

import com.ionspin.kotlin.bignum.integer.BigInteger as KotlinBigInteger
import java.math.BigInteger as JavaBigInteger

/**
 * JVM implementation using Java's BigInteger which natively supports
 * two's complement byte array interpretation.
 */
internal actual fun bytesToBigIntegerSigned(bytes: ByteArray): KotlinBigInteger {
    // Use Java BigInteger constructor that interprets bytes as two's complement
    val javaBigInt = JavaBigInteger(bytes)
    
    // Convert to Kotlin BigInteger
    return KotlinBigInteger.parseString(javaBigInt.toString())
}

/**
 * JVM implementation using Java's BigInteger for two's complement conversion.
 */
internal actual fun bigIntegerToBytesSigned(
    value: KotlinBigInteger,
    byteCount: Int
): ByteArray {
    // Convert Kotlin BigInteger to Java BigInteger
    val javaBigInt = JavaBigInteger(value.toString())
    
    // Get two's complement byte array
    val bytes = javaBigInt.toByteArray()
    
    // Pad or trim to exact size
    return when {
        bytes.size == byteCount -> bytes
        bytes.size < byteCount -> {
            // Pad with sign extension
            val padded = ByteArray(byteCount)
            val fillByte: Byte = if (javaBigInt.signum() < 0) 0xFF.toByte() else 0x00
            padded.fill(fillByte, 0, byteCount - bytes.size)
            bytes.copyInto(padded, byteCount - bytes.size)
            padded
        }
        else -> {
            // Trim excess sign bytes
            bytes.copyOfRange(bytes.size - byteCount, bytes.size)
        }
    }
}
```

**Key Points:**
- Uses Java's BigInteger for perfect compatibility
- Converts between Java BigInteger and Kotlin BigInteger via string
- String conversion is reliable and handles all edge cases
- Handles padding/trimming for fixed-size byte arrays

---

### Step 3: Implement JS-Specific Version

**File:** `stellar-sdk/src/jsMain/kotlin/com/stellar/sdk/scval/BigIntegerConversion.kt`

```kotlin
package com.stellar.sdk.scval

import com.ionspin.kotlin.bignum.integer.BigInteger
import com.ionspin.kotlin.bignum.integer.Sign

/**
 * JavaScript implementation using manual two's complement conversion.
 * 
 * JS doesn't have Java's BigInteger, so we implement the conversion manually.
 */
internal actual fun bytesToBigIntegerSigned(bytes: ByteArray): BigInteger {
    // Check if negative (high bit set)
    val isNegative = (bytes[0].toInt() and 0x80) != 0
    
    return if (isNegative) {
        // Two's complement: -(~bytes + 1)
        val inverted = bytes.map { (it.toInt().inv() and 0xFF).toByte() }.toByteArray()
        val magnitude = BigInteger.fromByteArray(inverted, Sign.POSITIVE) + BigInteger.ONE
        -magnitude
    } else {
        // Positive: direct conversion
        BigInteger.fromByteArray(bytes, Sign.POSITIVE)
    }
}

/**
 * JavaScript implementation of BigInteger to two's complement bytes.
 */
internal actual fun bigIntegerToBytesSigned(
    value: BigInteger,
    byteCount: Int
): ByteArray {
    val bytes = value.toByteArray()
    val paddedBytes = ByteArray(byteCount)
    
    if (value.signum() >= 0) {
        // Positive: pad with zeros on the left
        val numBytesToCopy = minOf(bytes.size, byteCount)
        val copyStartIndex = bytes.size - numBytesToCopy
        bytes.copyInto(paddedBytes, byteCount - numBytesToCopy, copyStartIndex, bytes.size)
    } else {
        // Negative: pad with 0xFF on the left
        paddedBytes.fill(0xFF.toByte(), 0, byteCount - bytes.size)
        bytes.copyInto(paddedBytes, byteCount - bytes.size, 0, bytes.size)
    }
    
    return paddedBytes
}
```

**Key Points:**
- Uses manual two's complement conversion
- Same logic as current implementation
- Works for most cases (JS tests already pass)

---

### Step 4: Implement Native-Specific Version

**File:** `stellar-sdk/src/nativeMain/kotlin/com/stellar/sdk/scval/BigIntegerConversion.kt`

```kotlin
package com.stellar.sdk.scval

import com.ionspin.kotlin.bignum.integer.BigInteger
import com.ionspin.kotlin.bignum.integer.Sign

/**
 * Native (iOS/macOS) implementation using manual two's complement conversion.
 * 
 * Same approach as JS since native platforms don't have Java's BigInteger.
 */
internal actual fun bytesToBigIntegerSigned(bytes: ByteArray): BigInteger {
    // Same implementation as JS
    val isNegative = (bytes[0].toInt() and 0x80) != 0
    
    return if (isNegative) {
        val inverted = bytes.map { (it.toInt().inv() and 0xFF).toByte() }.toByteArray()
        val magnitude = BigInteger.fromByteArray(inverted, Sign.POSITIVE) + BigInteger.ONE
        -magnitude
    } else {
        BigInteger.fromByteArray(bytes, Sign.POSITIVE)
    }
}

/**
 * Native implementation of BigInteger to two's complement bytes.
 */
internal actual fun bigIntegerToBytesSigned(
    value: BigInteger,
    byteCount: Int
): ByteArray {
    // Same implementation as JS
    val bytes = value.toByteArray()
    val paddedBytes = ByteArray(byteCount)
    
    if (value.signum() >= 0) {
        val numBytesToCopy = minOf(bytes.size, byteCount)
        val copyStartIndex = bytes.size - numBytesToCopy
        bytes.copyInto(paddedBytes, byteCount - numBytesToCopy, copyStartIndex, bytes.size)
    } else {
        paddedBytes.fill(0xFF.toByte(), 0, byteCount - bytes.size)
        bytes.copyInto(paddedBytes, byteCount - bytes.size, 0, bytes.size)
    }
    
    return paddedBytes
}
```

---

### Step 5: Update Scv.kt to Use New Functions

**File:** `stellar-sdk/src/commonMain/kotlin/com/stellar/sdk/scval/Scv.kt`

**Change in `fromInt128()`:**
```kotlin
fun fromInt128(scVal: SCValXdr): BigInteger {
    require(scVal is SCValXdr.I128) {
        "invalid scVal type, expected SCV_I128, but got ${scVal.discriminant}"
    }

    val hiBytes = longToBytes(scVal.value.hi.value)
    val loBytes = ulongToBytes(scVal.value.lo.value)

    val fullBytes = ByteArray(16)
    hiBytes.copyInto(fullBytes, 0, 0, 8)
    loBytes.copyInto(fullBytes, 8, 0, 8)

    // Use platform-specific conversion
    return bytesToBigIntegerSigned(fullBytes)
}
```

**Change in `toInt128()`:**
```kotlin
fun toInt128(value: BigInteger): SCValXdr {
    require(value >= INT128_MIN_VALUE && value <= INT128_MAX_VALUE) {
        "invalid value, expected between $INT128_MIN_VALUE and $INT128_MAX_VALUE, but got $value"
    }

    // Use platform-specific conversion
    val paddedBytes = bigIntegerToBytesSigned(value, 16)

    val hi = bytesToLong(paddedBytes.copyOfRange(0, 8))
    val lo = bytesToULong(paddedBytes.copyOfRange(8, 16))

    return SCValXdr.I128(
        Int128PartsXdr(
            hi = Int64Xdr(hi),
            lo = Uint64Xdr(lo)
        )
    )
}
```

**Similar changes for Int256:**
- Use `bytesToBigIntegerSigned()` in `fromInt256()`
- Use `bigIntegerToBytesSigned(value, 32)` in `toInt256()`

---

## File Structure

```
stellar-sdk/src/
├── commonMain/kotlin/com/stellar/sdk/scval/
│   ├── Scv.kt                           (MODIFIED - use expect functions)
│   └── BigIntegerConversion.kt          (NEW - expect declarations)
├── jvmMain/kotlin/com/stellar/sdk/scval/
│   └── BigIntegerConversion.kt          (NEW - JVM actual)
├── jsMain/kotlin/com/stellar/sdk/scval/
│   └── BigIntegerConversion.kt          (NEW - JS actual)
└── nativeMain/kotlin/com/stellar/sdk/scval/
    └── BigIntegerConversion.kt          (NEW - Native actual)
```

**Total Files:** 4 new files + 1 modified

---

## Expected Results

### Before (Current State):
- JVM: 471/473 tests passing (99.6%)
- JS: All tests passing (100%)
- Native: 470/472 tests passing (99.6%)

### After (Expected):
- JVM: 473/473 tests passing (100%) ✅
- JS: All tests passing (100%) ✅
- Native: 472/472 tests passing (100%) ✅

**Overall: 100% test pass rate across all platforms**

---

## Advantages of This Approach

1. **Platform-Specific Optimization**
   - JVM uses native Java BigInteger (most efficient)
   - JS/Native use manual conversion (lightweight)

2. **Maintains Cross-Platform Support**
   - All platforms continue to work
   - No platform-specific dependencies in common code

3. **Clean Separation**
   - Internal functions keep implementation details hidden
   - Public API (Scv) remains unchanged
   - Easy to test each platform independently

4. **Future-Proof**
   - If better BigInteger library appears, easy to swap
   - Can optimize each platform independently
   - No coupling between platforms

---

## Testing Strategy

### Step 1: Test JVM
```bash
./gradlew :stellar-sdk:jvmTest --tests "ScvTest.testInt128" --tests "ScvTest.testInt256"
```
**Expected:** Both tests pass

### Step 2: Test JS (Node.js)
```bash
./gradlew :stellar-sdk:jsNodeTest --tests "ScvTest"
```
**Expected:** All tests pass (already do)

### Step 3: Test Native (macOS)
```bash
./gradlew :stellar-sdk:macosArm64Test --tests "ScvTest"
```
**Expected:** All tests pass

### Step 4: Full Test Suite
```bash
./gradlew :stellar-sdk:jvmTest
./gradlew :stellar-sdk:jsNodeTest --tests "*"  # Individual classes
./gradlew :stellar-sdk:macosArm64Test
```
**Expected:** 100% pass rate on all platforms

---

## Risks and Mitigation

### Risk 1: String Conversion Performance
**Risk:** Converting BigInteger via string might be slow  
**Mitigation:** 
- Only used for Int128/Int256 conversions
- These are relatively rare operations
- String conversion is reliable and correct
- Performance impact negligible

### Risk 2: Platform-Specific Bugs
**Risk:** Different implementations might have subtle bugs  
**Mitigation:**
- Comprehensive test coverage
- Use same test vectors on all platforms
- JVM implementation uses battle-tested Java BigInteger
- JS/Native implementations already work for most cases

### Risk 3: Build Complexity
**Risk:** More files to maintain  
**Mitigation:**
- Well-documented expect/actual pattern
- Each platform is self-contained
- Internal functions minimize API surface

---

## Success Criteria

✅ All 473 JVM tests pass  
✅ All JS tests pass  
✅ All Native tests pass  
✅ No breaking changes to public API  
✅ Code compiles on all platforms  
✅ Documentation updated  

---

## Estimated Time

- Step 1 (Common expect): 5 minutes
- Step 2 (JVM actual): 10 minutes
- Step 3 (JS actual): 5 minutes
- Step 4 (Native actual): 5 minutes
- Step 5 (Update Scv): 15 minutes
- Testing & Verification: 10 minutes

**Total: ~50 minutes**

---

## Alternative Considered

**Alternative 1:** Fix manual two's complement conversion  
- ❌ Already tried, subtle edge cases remain
- ❌ Difficult to debug
- ❌ No guarantee of full compatibility

**Alternative 2:** Use different BigInteger library  
- ❌ May have same issues
- ❌ Additional dependency
- ❌ Unknown quality

**Alternative 3:** Accept 99.6% pass rate  
- ✅ Already production-ready
- ❌ Not perfect
- ❌ Could cause issues with extreme values

**Chosen: expect/actual with Java BigInteger**
- ✅ Leverages proven Java implementation
- ✅ Platform-specific optimization
- ✅ Maintainable and clear
- ✅ 100% compatibility on JVM (primary platform)

---

*Plan created: October 5, 2025*  
*Status: Ready for review and implementation*
