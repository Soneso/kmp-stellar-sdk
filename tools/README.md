# KMP Stellar SDK development tools

This directory holds the development tools of the KMP Stellar SDK, one tool per subdirectory. Every tool except `coverage/` has a README with its details. Run every command in this file from the repository root.

| Tool | Purpose | Requirements |
|---|---|---|
| `xdrgen-kt/` | Generates the Kotlin XDR types and the XDR-JSON (SEP-51) test suite from the pinned `.x` definition files. | Ruby 3 with Bundler, curl |
| `sep-51-oracle/` | Derives the XDR-JSON name of every enum member, struct field and union arm and checks the names against the pinned reference CLI. | Ruby; the pinned `stellar-xdr` CLI for the reference checks |
| `sep-51-corpus/` | Generates the SEP-51 conformance corpus from hand-written seeds through the reference CLI. | Python 3, the pinned `stellar-xdr` CLI |
| `matrix-generator/` | Generates the compatibility matrices under `compatibility/`. | Python 3, network access |
| `skill-generator/` | Generates the API reference of the agent skill. | Python 3 |
| `coverage/` | Computes the line coverage metric from the Kover report and enforces a minimum. | Python 3 |
| `p28-owner-fixture/` | Source of the protocol 28 owner contract whose wasm the integration tests deploy. | Rust with the `wasm32v1-none` target, stellar-cli |

## xdrgen-kt

```bash
make -C tools/xdrgen-kt generate          # install the gems, download the pinned .x files, generate the XDR types
make -C tools/xdrgen-kt emit-json-tests   # emit the XDR-JSON test suite into the SDK test sources
make -C tools/xdrgen-kt test              # run the generator tests
make -C tools/xdrgen-kt update            # after a pin change in xdr-source.cfg: download again and regenerate everything
make -C tools/xdrgen-kt                   # list all targets
```

The Makefile runs `generate.rb` through Bundler. Without arguments, `generate.rb` reads the `.x` files that `download-xdr.sh` places under `tools/xdrgen-kt/xdr/`. The types land in `stellar-sdk/src/commonMain/kotlin/com/soneso/stellar/sdk/xdr/`, the emitted tests in `stellar-sdk/src/commonTest/kotlin/com/soneso/stellar/sdk/unitTests/xdr/json/`. Details: `tools/xdrgen-kt/README.md`.

## sep-51-oracle

```bash
ruby tools/sep-51-oracle/emitted_names.rb     # check the names the generated Kotlin emits against name-map.json
ruby tools/sep-51-oracle/name_map.rb --diff   # rebuild the name table and check it against the reference CLI
tools/sep-51-oracle/probe.sh --check          # verify that the installed reference CLI matches the pin
```

Details, including the pins and the exit codes: `tools/sep-51-oracle/README.md`.

## sep-51-corpus

```bash
python3 tools/sep-51-corpus/generate_corpus.py   # regenerate corpus.json from seeds.py
bash tools/sep-51-corpus/refresh_corpus.sh       # regenerate into a scratch directory and diff against the committed corpus
```

`make -C tools/xdrgen-kt emit-json-tests` embeds the corpus into the generated tests. Details: `tools/sep-51-corpus/README.md`.

## matrix-generator

```bash
GITHUB_TOKEN="$(gh auth token)" python3 tools/matrix-generator/run_analysis.py
```

The run writes the matrices under `compatibility/horizon/`, `compatibility/rpc/` and `compatibility/sep/`. `GITHUB_TOKEN` raises the GitHub API rate limit from 60 to 5,000 requests per hour. `AGENTS.md` lists the cases in which regenerated matrices go into a commit. Details: `tools/matrix-generator/README.md`.

## skill-generator

```bash
python3 tools/skill-generator/generate_api_reference.py
```

The script writes `skills/kmp-stellar-sdk/references/api_reference.md`. The maintainer regenerates the reference and the skill zip at release preparation. Details: `tools/skill-generator/README.md`.

## coverage

```bash
python3 tools/coverage/coverage_summary.py --min 90
```

The script reads `stellar-sdk/build/reports/kover/reportJvmOnly.xml`, which `./gradlew :stellar-sdk:koverXmlReportJvmOnly -PexcludeIntegrationTests` writes. It counts a line as covered only when all of its branches are covered, the metric Codecov reports. `--packages` prints the metric per package, and `--min` exits with status 1 when the overall metric is below the given percentage.

## p28-owner-fixture

```bash
cd tools/p28-owner-fixture && stellar contract build
```

The build writes `target/wasm32v1-none/release/p28_owner_fixture.wasm`, which is committed as `stellar-sdk/src/commonTest/resources/wasm/p28_owner_fixture.wasm`. Each of the four Soneso Stellar SDK repositories holds a copy of the directory and the wasm, and a rebuild updates all four. Details: `tools/p28-owner-fixture/README.md`.

## Adding a tool

A new tool gets its own subdirectory with a README that states its requirements and usage, and a row and a section in this file.

## License

Apache-2.0
