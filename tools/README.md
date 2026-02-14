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

### sdk-analysis - Compatibility Matrix Generator

**Location:** `tools/sdk-analysis/`

**Description:** Python-based tools for generating compatibility matrices that compare the KMP Stellar SDK against official Stellar API specifications. Covers Horizon REST API, Soroban RPC, and SEP (Stellar Ecosystem Proposals).

**Prerequisites:**
- Python 3.8+ (standard library only, no external dependencies)
- `stellar-go` repo (for Horizon analysis)
- `stellar-rpc` repo (for RPC analysis, or fetched from GitHub automatically)

**Usage:**
```bash
# Generate Horizon + RPC matrices
python3 tools/sdk-analysis/run_analysis.py

# Generate all SEP matrices
python3 tools/sdk-analysis/sep/run_sep_analysis.py
```

**Output:** Generates compatibility matrices in `compatibility/horizon/`, `compatibility/rpc/`, and `compatibility/sep/`.

For detailed documentation, see `tools/sdk-analysis/README.md` and `tools/sdk-analysis/sep/README.md`.

## Directory Structure

```
tools/
├── README.md              # This file
├── xdrgen-kt/             # XDR code generation tool
│   ├── generate.rb        # Main generator script
│   ├── Gemfile            # Ruby dependencies
│   └── lib/               # Custom Kotlin generator
└── sdk-analysis/          # Compatibility matrix generator
    ├── run_analysis.py    # Horizon + RPC orchestrator
    ├── horizon_parser.py  # Parse Horizon endpoints from stellar-go
    ├── kmp_sdk_analyzer.py # Analyze SDK Kotlin source
    ├── generate_horizon_comparison.py  # Generate Horizon matrix
    ├── generate_rpc_comparison.py      # Generate RPC matrix
    ├── go_protocol_parser.py           # Parse Go protocol files
    └── sep/               # SEP compatibility pipeline
        ├── run_sep_analysis.py         # SEP orchestrator
        ├── sep_parser.py              # Parse SEP specs from GitHub
        ├── sep_analyzer.py            # Analyze SDK SEP implementations
        └── generate_sep_comparison.py # Generate SEP matrices
```

## Contributing

When adding new tools to this directory:

1. Create a subdirectory for the tool (e.g., `tools/my-tool/`)
2. Include a README.md in the tool's directory with detailed usage instructions
3. Update this file to list the new tool
4. Follow existing patterns for script organization and documentation

## License

Apache-2.0
