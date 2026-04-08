# XDR Code Generator

Generates Kotlin XDR types from Stellar's `.x` definition files using the [xdrgen](https://github.com/stellar/xdrgen) Ruby gem.

## Prerequisites

- Ruby 3.x with Bundler
- curl (for downloading .x files)

## Usage

### Generate XDR files

```bash
cd tools/xdrgen-kt

make generate     # full pipeline: install gems, download .x files, generate Kotlin
make update       # re-download .x files and regenerate (after changing xdr-source.cfg)
make clean        # remove only generated Kotlin files
make clean-all    # remove generated Kotlin files and .x definitions
```

Or run the generator directly:

```bash
cd tools/xdrgen-kt
bundle config set --local path vendor/bundle
bundle install
bundle exec ruby generate.rb
```

Output goes to `stellar-sdk/src/commonMain/kotlin/com/soneso/stellar/sdk/xdr/`.

### Update to a new XDR spec version

1. Update `XDR_COMMIT` in `xdr-source.cfg` to the new [stellar/stellar-xdr](https://github.com/stellar/stellar-xdr) commit (use the `curr` branch)
2. If new `.x` files were added upstream, add them to `XDR_FILES` in `xdr-source.cfg`
3. Run `make update`
4. Run `make test` to verify snapshot tests pass (update snapshots if needed)
5. Build and run unit tests: `./gradlew :stellar-sdk:jvmTest -PexcludeIntegrationTests`
6. If new types were added or existing ones modified, evaluate whether the SDK needs updated high-level APIs

### Run tests

```bash
make test               # run snapshot tests
make update-snapshots   # update snapshots after intentional generator changes
```

Or directly (requires `bundle install` first):

```bash
cd tools/xdrgen-kt
bundle exec ruby test/generator_snapshot_test.rb
bundle exec ruby test/update_snapshots.rb
```

## Generator architecture

| File | Purpose |
|------|---------|
| `xdr-source.cfg` | Pinned stellar-xdr commit hash and list of .x files to download |
| `download-xdr.sh` | Downloads .x files from stellar/stellar-xdr at the pinned commit |
| `generate.rb` | Entry point: parses .x files via xdrgen, runs Kotlin generator |
| `lib/xdrgen/generators/kotlin.rb` | Kotlin code renderer (structs, enums, unions, typedefs) |
| `Makefile` | Orchestrates the full pipeline: gem setup, download, generation |
| `test/generator_snapshot_test.rb` | Snapshot tests comparing generated output to expected files |
| `test/update_snapshots.rb` | Regenerates snapshot files after intentional generator changes |
| `test/fixtures/` | Input .x fixture files for snapshot tests |
| `test/snapshots/` | Expected Kotlin output for snapshot comparison |

## Configuration

`xdr-source.cfg` contains two settings:

- `XDR_COMMIT` — The pinned commit hash from the [stellar/stellar-xdr](https://github.com/stellar/stellar-xdr) `curr` branch. The `main` branch contains preprocessor directives that xdrgen cannot parse.
- `XDR_FILES` — List of `.x` files to download from that commit.

## Excluded files

The generator excludes these files (internal types not needed for SDK usage):

- `Stellar-exporter.x` — Batch export format (LedgerCloseMetaBatch)
- `Stellar-internal.x` — Core internal storage (StoredTransactionSet, PersistedSCPState)
- `Stellar-overlay.x` — Network protocol messages (Hello, Auth, PeerAddress)

`Stellar-SCP.x` is included because `Stellar-ledger.x` references its types.
