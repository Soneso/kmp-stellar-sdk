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

## Directory Structure

```
tools/
├── README.md              # This file
└── xdrgen-kt/             # XDR code generation tool
    ├── generate.rb        # Main generator script
    ├── Gemfile            # Ruby dependencies
    └── lib/               # Custom Kotlin generator
```

## Contributing

When adding new tools to this directory:

1. Create a subdirectory for the tool (e.g., `tools/my-tool/`)
2. Include a README.md in the tool's directory with detailed usage instructions
3. Update this file to list the new tool
4. Follow existing patterns for script organization and documentation

## License

Apache-2.0
