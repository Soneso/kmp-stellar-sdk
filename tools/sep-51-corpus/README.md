# SEP-0051 conformance corpus

`corpus.json` pins, for a set of hand-chosen XDR values, the exact JSON text the
SDK must emit and the base64 XDR that value encodes to. The SDK's unit test
`Sep51CorpusTest` walks it in both directions: JSON to XDR and XDR to JSON.

The corpus targets the renderings that are not mechanical — strkey-valued union
arms, integers that become strings, the escape ladder over bytes-typed and
string-typed fields, empty and populated variable-length data, optionals in both
states, and the name rules a pin bump could disturb. It is not an enumeration of
every type; the generated per-type suites cover that.

## Files

| File | Role |
|---|---|
| `seeds.py` | Hand-authored seed values, each written in XDR-JSON form. |
| `generate_corpus.py` | Encodes and decodes every seed with the reference CLI and writes `corpus.json`. |
| `corpus.json` | Generated and committed. Consumed by the emitter, never edited by hand. |
| `refresh_corpus.sh` | Regenerates into `.tmp/` and diffs against the committed file. |

## Ground truth

The reference is the XDR-JSON CLI pinned in `../sep-51-oracle/oracle-pin.json`.
`generate_corpus.py` verifies that pin before it does anything else and refuses
to run against a different build, because key spellings differ between reference
releases.

Every seed is encoded to XDR and decoded straight back through the reference, so
a seed that is not valid XDR-JSON for its type fails the run rather than
reaching the corpus. The decoded document — not the authored one — is what the
corpus records, which is why authoring a value in a non-canonical form is
harmless.

## Comparable and incomparable entries

Most entries are **comparable**: the reference's own output is exactly what the
SDK must emit, and the entry records it verbatim under `oracle: "reference"`.

Some entries are **incomparable**: SEP-0051 specifies one form and the reference
emits another. Those entries carry `oracle: "incomparable"`, the specified text
under `json`, the reference's text under `oracle_json`, and a `reason`. At the
current pins there are 17 such entries across 8 types, under two
transformations that derive the specified form from the reference's:

- `integer_string` — SEP-0051 §Hyper Integer requires a 64-bit integer to be a
  base-10 string. The reference emits a bare JSON number for a standalone
  `Int64` or `Uint64`.
- `opaque_hex` — SEP-0051 §Fixed-Length Opaque Data requires fixed-length opaque
  data to be a lowercase hex string. Where the `.x` declares such a field
  inline, the reference emits an array of byte numbers. The transformation walks
  the whole document, so nested occurrences are rewritten too.

The generator asserts that each derived value actually differs from the
reference's. A divergence that quietly disappears in a future reference release
fails the run instead of passing unnoticed.

## Refreshing

```bash
# Check the committed corpus against a fresh generation
bash refresh_corpus.sh

# Ask whether a newer reference build would render the corpus differently.
# Reports only; the committed corpus is never written.
STELLAR_XDR=/path/to/newer/stellar-xdr bash refresh_corpus.sh --advisory

# Rewrite it
python3 generate_corpus.py

# Re-emit the copy embedded in the SDK test sources
make -C ../xdrgen-kt emit-json-tests
```

`corpus.json` has no timestamp and a fixed entry and key order, so an unchanged
input produces a byte-identical file and any diff is real drift.

Re-run after either pin moves: the SDK's XDR pin in
`../xdrgen-kt/xdr-source.cfg`, or the reference pin in
`../sep-51-oracle/oracle-pin.json`.

## Exit codes

`generate_corpus.py`

| Code | Meaning |
|---|---|
| 0 | Corpus written. |
| 1 | A seed failed, or a declared divergence no longer holds. |
| 2 | The reference CLI is missing or does not match the pin. |

`refresh_corpus.sh`

| Code | Meaning |
|---|---|
| 0 | No drift. |
| 1 | Drift; the diff is printed. |
| 2 | A prerequisite is missing. |

In `--advisory` mode the same codes mean the same things, measured against whatever build
`STELLAR_XDR` names rather than the pinned one: 0 that the build renders the corpus
identically, 1 that it does not, with the diff and any per-seed findings both printed. A seed
the build cannot process is recorded as a finding and its entry omitted, so one run
enumerates every affected seed instead of stopping at the first, and the omission shows up in
the diff as a smaller `entry_count`.

The advisory comparison ignores `reference_version` and `reference_xdr_commit`. The generated
document records the build that actually produced it, but those two fields differ on every
real release and would otherwise mask the only question being asked: does anything the SDK
emits change? `entry_count` stays in the comparison, because a dropped seed is a real
difference.
