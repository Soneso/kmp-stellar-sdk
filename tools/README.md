# KMP Stellar SDK - Development Tools

This directory contains development tools for the KMP Stellar SDK.

## Available Tools

### xdrgen-kt - XDR Code Generation

**Location:** `tools/xdrgen-kt/`

**Description:** Ruby-based tool for generating Kotlin XDR types from Stellar XDR specification files.

**Prerequisites:**
- Ruby (bundled dependencies included)

**Usage:**
```bash
cd tools/xdrgen-kt
./generate.rb /path/to/stellar-xdr/Stellar-*.x
```

**Output:** Generates Kotlin XDR types in `stellar-sdk/src/commonMain/kotlin/com/soneso/stellar/sdk/xdr/`

**Features:**
- Generates type-safe Kotlin classes from XDR specifications
- Automatically excludes internal/overlay protocol files
- Maintains compatibility with Stellar protocol updates

For detailed documentation, see the README.md in each tool's subdirectory.

### matrix-generator - Compatibility Matrix Generator

**Location:** `tools/matrix-generator/`

**Description:** Python-based tools for generating compatibility matrices that compare the KMP Stellar SDK against official Stellar API specifications. Covers Horizon REST API, Soroban RPC, and SEP (Stellar Ecosystem Proposals).

**Prerequisites:**
- Python 3.8+ (standard library only, no external dependencies)
- Optional: `GITHUB_TOKEN` for higher API rate limits

**Usage:**
```bash
# Generate all matrices (Horizon + RPC + SEPs)
python3 tools/matrix-generator/run_analysis.py
```

**Output:** Generates compatibility matrices in `compatibility/horizon/`, `compatibility/rpc/`, and `compatibility/sep/`.

For detailed documentation, see `tools/matrix-generator/README.md`.

## Directory Structure

```
tools/
├── README.md              # This file
├── xdrgen-kt/             # XDR code generation tool
│   ├── generate.rb        # Main generator script
│   ├── Gemfile            # Ruby dependencies
│   └── lib/               # Custom Kotlin generator
└── matrix-generator/      # Compatibility matrix generator
    ├── run_analysis.py    # Master orchestrator (all pipelines)
    ├── common.py          # Shared utilities
    ├── github_fetcher.py  # GitHub API client
    ├── sdk_analyzer.py    # Kotlin source analyzer
    ├── horizon/           # Horizon API pipeline
    ├── rpc/               # Soroban RPC pipeline
    └── sep/               # SEP pipeline
```

## Contributing

When adding new tools to this directory:

1. Create a subdirectory for the tool (e.g., `tools/my-tool/`)
2. Include a README.md in the tool's directory with detailed usage instructions
3. Update this file to list the new tool
4. Follow existing patterns for script organization and documentation

## License

Apache-2.0
