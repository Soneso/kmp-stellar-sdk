# Compatibility Matrix Generator

Automated tool that generates compatibility matrices comparing the KMP Stellar SDK against the official Stellar APIs and protocol specifications.

It analyzes three areas:

- **Horizon API** -- all REST endpoints defined in `stellar-go/services/horizon`
- **Soroban RPC** -- all JSON-RPC methods defined in `stellar-rpc`
- **SEPs** -- 13 Stellar Ecosystem Proposals (SEP-01, 02, 05, 06, 08, 09, 10, 12, 24, 30, 38, 45, 53)

## Requirements

- Python 3.8+
- No external dependencies (stdlib only)
- Internet access (fetches specs from GitHub)

Optional: set `GITHUB_TOKEN` for higher API rate limits (5,000 vs 60 requests/hour).

## Quick Start

Run all three pipelines in a single command:

```bash
python3 tools/matrix-generator/run_analysis.py
```

This auto-detects implemented SEPs and runs Horizon, RPC, and SEP analysis sequentially.

This generates Markdown reports in `compatibility/`:

```
compatibility/
  horizon/HORIZON_COMPATIBILITY_MATRIX.md
  rpc/RPC_COMPATIBILITY_MATRIX.md
  sep/SEP-0001_COMPATIBILITY_MATRIX.md
  sep/SEP-0002_COMPATIBILITY_MATRIX.md
  ...
  sep/SEP-0053_COMPATIBILITY_MATRIX.md
```

## Running Individual Pipelines

Each subsystem can be run independently.

### Horizon

```bash
python3 tools/matrix-generator/horizon/run_horizon_analysis.py

# Use a specific Horizon version
python3 tools/matrix-generator/horizon/run_horizon_analysis.py --horizon-version v2.30.0

# Use a local router.go file
python3 tools/matrix-generator/horizon/run_horizon_analysis.py --local /path/to/router.go

# Enable verbose output
python3 tools/matrix-generator/horizon/run_horizon_analysis.py --verbose
```

### Soroban RPC

```bash
python3 tools/matrix-generator/rpc/run_rpc_analysis.py

# Use a specific RPC version
python3 tools/matrix-generator/rpc/run_rpc_analysis.py --rpc-version v22.0.0

# Use a local jsonrpc.go file
python3 tools/matrix-generator/rpc/run_rpc_analysis.py --local /path/to/jsonrpc.go

# Enable verbose output
python3 tools/matrix-generator/rpc/run_rpc_analysis.py --verbose
```

### SEPs

SEP analysis runs as three stages per SEP: parse, analyze, compare.

```bash
# Parse a single SEP specification from GitHub
python3 tools/matrix-generator/sep/sep_parser.py 0010

# Analyze SDK implementation for that SEP
python3 tools/matrix-generator/sep/sep_analyzer.py 0010

# Generate the comparison report
python3 tools/matrix-generator/sep/generate_sep_comparison.py 0010
```

The SEP number argument accepts both short (`10`) and zero-padded (`0010`) forms.

## Project Structure

```
tools/matrix-generator/
├── run_analysis.py              # Master orchestrator (runs all pipelines)
├── common.py                    # Shared utilities (colors, paths, SDK version)
├── github_fetcher.py            # GitHub API client (release + source fetching)
├── sdk_analyzer.py              # Kotlin source analyzer (used by Horizon pipeline)
├── horizon/
│   ├── run_horizon_analysis.py  # Horizon pipeline orchestrator
│   ├── horizon_parser.py        # Parses router.go for endpoint definitions
│   └── generate_horizon_comparison.py
├── rpc/
│   ├── run_rpc_analysis.py      # RPC pipeline orchestrator
│   ├── rpc_parser.py            # Parses jsonrpc.go for RPC method definitions
│   └── generate_rpc_comparison.py
├── sep/
│   ├── sep_parser.py            # Fetches and parses SEP specs from GitHub
│   ├── sep_analyzer.py          # Analyzes SDK source for SEP implementation
│   └── generate_sep_comparison.py
└── data/                        # Intermediate JSON (gitignored)
    ├── horizon/
    ├── rpc/
    └── sep/
```

## How It Works

Each pipeline follows the same pattern:

1. **Parse** the reference source (Go source code or SEP Markdown) to extract the official API surface
2. **Analyze** the KMP SDK Kotlin source to find which parts are implemented
3. **Compare** the two and generate a Markdown compatibility matrix with coverage percentages

SDK version is read from `gradle.properties` (`version=x.y.z`). Intermediate JSON files are written to `data/` for debugging. Only the final Markdown reports in `compatibility/` are committed.

## Adding a New SEP

SEPs are auto-detected from the SDK source directory structure. To add analysis for a new SEP:

1. Add a SEP-specific parser function in `sep/sep_parser.py` (or rely on the generic parser for simple specs)
2. Verify that `sep/sep_analyzer.py` detects the SDK implementation directory (`sep/sepXX/`)
3. Run the three-stage pipeline for the new SEP number:

```bash
python3 tools/matrix-generator/sep/sep_parser.py 0054
python3 tools/matrix-generator/sep/sep_analyzer.py 0054
python3 tools/matrix-generator/sep/generate_sep_comparison.py 0054
```

## GitHub Rate Limits

Without authentication the GitHub API allows 60 requests/hour. Set `GITHUB_TOKEN` to raise this to 5,000 requests/hour:

```bash
export GITHUB_TOKEN=your_personal_access_token
python3 tools/matrix-generator/horizon/run_horizon_analysis.py
```

A read-only token with no additional scopes is sufficient.
