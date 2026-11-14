# Release Notes - Version 1.17.0

## Overview

Version 1.17.0 supports stellar-rpc 30.0.1. That version reports a contract error raised in a constructor or in `__check_auth` as `Error(Context, InvalidAction)` or `Error(Auth, InvalidAction)` and leaves the contract's code only in the diagnostic events. The SDK reads the code from those events, so a failed simulation names the contract error in its exception message, in `reportedContractError()` and in the new `SimulationFailureDetails` interface. `ContractErrorCodes.decodeFromSimulationFailure` maps it to the OpenZeppelin error (#150). Existing calls and catch blocks keep working.

## Also in this release

- Strkeys work on Android 7.0 to 8.1, and the JVM and Android artifacts drop their Apache Commons Codec dependency (#148).
- The Android artifact is built with Android Gradle plugin 9. Apps keep minimum SDK 24 and compile SDK 34 or later (#140).
- Fixes for deeply nested XDR decoding (#136), `ContractSpec` enum ranges and type lookup (#138, #154), `assembleTransaction` reporting a failed simulation with its error and events (#151), and the SEP-29 check on envelope strings with line breaks (#156).

## Compatibility

- Kotlin 2.2+
- Maven: `com.soneso.stellar:stellar-sdk:1.17.0`
- Android: minimum SDK 24 (Android 7.0), compile SDK 34 or later
- Horizon v30.0.1 and stellar-rpc v30.0.1, the targets of the compatibility matrices

## Details

The [CHANGELOG](https://github.com/Soneso/kmp-stellar-sdk/blob/v1.17.0/CHANGELOG.md) lists every change of this release.
