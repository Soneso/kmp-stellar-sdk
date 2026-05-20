# Skill Generator

Python script that generates the agent-skill API reference file
(`skills/kmp-stellar-sdk/references/api_reference.md`) from the SDK's Kotlin
source.

## What it does

Walks `stellar-sdk/src/commonMain/` (plus the smart-account subpackage of
platform sourcesets) and extracts every public class, interface, object, and
enum into a compact signature-only markdown reference. The output is consumed
by the `kmp-stellar-sdk` agent skill and lets AI coding agents look up
method/property signatures without reading raw Kotlin source.

## Prerequisites

- Python 3.8+ (standard library only, no external dependencies)

## Usage

Run from the repository root:

```bash
python3 tools/skill-generator/generate_api_reference.py
```

Output is written to
`skills/kmp-stellar-sdk/references/api_reference.md` (overwriting the previous
generation).

After regenerating, rebuild the skill zip so the bundled archive matches the
new reference content:

```bash
cd skills
rm -f kmp-stellar-sdk.zip
cd kmp-stellar-sdk && zip -r ../kmp-stellar-sdk.zip . -x "*.DS_Store"
```

## When to regenerate

Regenerate whenever the SDK's public API surface changes:

- New SEP implementation added
- New public class, method, or property in any non-XDR / non-crypto package
- Class moved between packages
- Field renamed, type changed, or signature otherwise modified
- Class deprecated or removed

Stale generation will not break the SDK build, but the agent skill will offer
out-of-date guidance to consumers.

## What gets included

- **Scanned source sets**: `commonMain` (everything), plus the `smartaccount`
  subpackage of `androidMain` / `nativeMain` / `jsMain` for platform-specific
  smart-account adapters.
- **Excluded directories**: `xdr/` and `crypto/` (internal generated /
  platform-impl code).
- **Excluded declarations**: `private`, `internal`, and `protected` classes,
  methods, and properties.
- **KMP `expect`/`actual`**: when both a commonMain `expect` declaration and
  one or more platform `actual` declarations exist for the same name, the
  `expect` is dropped from the output. The `actual` is the authoritative
  signature consumers interact with.

## Output format

Each class produces a section like:

```
## data ClassName : ParentClass, Interface
constructor(val foo: String, val bar: Int = 0)
Companion:
fun create(input: String): ClassName
val publicProperty: SomeType
suspend fun publicMethod(arg: Type): ReturnType
```

Classes are grouped into 11 buckets driven by the source-file path
(core, contract, smartaccount, scval, horizon, requests, responses,
horizon_exceptions, rpc, rpc_exceptions, sep).

## Limitations

This is a regex-based parser, not a Kotlin compiler frontend. It correctly
handles every pattern present in the current SDK source but is not a general
Kotlin parser. If a future SDK change uses a declaration shape that the script
doesn't yet recognize, the affected classes silently drop from the output.
After any large API change, sanity-check the regenerated `api_reference.md`
against the actual source (counts and a few spot-checked sections).
