# sac-spec

A Stellar Asset Contract (SAC) runs no wasm, so the network holds no contract spec for it. The SDK embeds the spec that `stellar/stellar-asset-contract-spec` publishes as `stellar-asset-spec.xdr`, the XDR `SCSpecEntry` values of the contract one after another. `generate_sac_spec.py` writes that file into `stellar-sdk/src/commonMain/kotlin/com/soneso/stellar/sdk/contract/SACSpec.kt` as one base64 string constant, together with the upstream commit and the file's sha256. `SACClient.specEntries()` decodes the constant and checks it against the digest.

The generated file is never edited by hand.

## Files

| File | Role |
|---|---|
| `pin.json` | The upstream `repository`, `commit` and `file`, the file's `sha256`, and the `template` and `output` paths, relative to the repository root. |
| `SACSpec.kt.template` | The Kotlin source with the placeholders `{{REPOSITORY}}`, `{{FILE}}`, `{{COMMIT}}`, `{{SHA256}}`, `{{SIZE}}` and `{{BASE64}}`. |
| `generate_sac_spec.py` | Fills the template from the pinned file and writes the output. |
| `tests/` | The script tests; they run offline. |

The script holds nothing specific to this repository: the pin and the template do. It needs Python 3.9 or later and the standard library only.

## Usage

Run from the repository root:

```bash
python3 tools/sac-spec/generate_sac_spec.py                       # download the pinned file and regenerate SACSpec.kt
python3 tools/sac-spec/generate_sac_spec.py --source FILE         # regenerate from a local copy of the file
python3 -m unittest discover -s tools/sac-spec/tests              # run the script tests
```

`--pin PATH` selects another pin (default: `pin.json` beside the script), and `--root DIR` another directory the pin's paths resolve against (default: the repository root). Without `--source` the script downloads `https://raw.githubusercontent.com/<repository>/<commit>/<file>` with a 60-second timeout.

The script checks that `commit` has 40 and `sha256` 64 lowercase hex characters, and that the file's sha256 equals the pinned one. It writes the output as UTF-8 with LF line endings through a temporary file, so a failed run leaves the committed file as it was. The output carries no timestamp: a run at the same pin reproduces the committed file byte for byte.

| Exit code | Meaning |
|---|---|
| 0 | The output was written. |
| 1 | Nothing was written: the pin is invalid, the template or source file cannot be read, the download failed, the digest differs from the pin (both digests are printed), the template leaves a placeholder unfilled, or the output cannot be written. A read, download or write failure names the path or URL and the cause. |

## Moving the pin

1. Pick the upstream commit and download `stellar-asset-spec.xdr` at that commit.
2. Set `commit` in `pin.json` to the commit and `sha256` to the output of `shasum -a 256 stellar-asset-spec.xdr`.
3. Run `python3 tools/sac-spec/generate_sac_spec.py` and read the diff of `SACSpec.kt`.
4. Run the script tests and the JVM unit suite (`AGENTS.md` names the command).

`SACSpecTest.kt` states the pinned commit, the digest, the size, and the function and event names of the spec as literals, so a new pin or a changed function or event set fails `SACSpecTest` until the same commit updates its expectations there. `SACClientTest`, `ContractClientSacDetectionTest` and the SAC integration test take the function names from `SAC_FUNCTION_NAMES` in that file and need no change of their own. A changed signature of `balance`, `decimals`, `name`, `symbol` or `transfer` also needs a look at `SACClient`, which calls them.
