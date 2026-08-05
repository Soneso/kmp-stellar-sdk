# SEP Implementations

The SDK provides support for Stellar Ecosystem Proposals (SEPs) to enable interoperability with the Stellar ecosystem.

## What are SEPs?

Stellar Ecosystem Proposals (SEPs) are standards that define how services, applications, and organizations interact with the Stellar network. They ensure consistent implementation of common patterns like domain verification, authentication, and asset anchoring.

## Implemented SEPs

| SEP | Title | Documentation |
|-----|-------|---------------|
| SEP-1 | Stellar TOML | [sep-01.md](sep-01.md) |
| SEP-2 | Federation Protocol | [sep-02.md](sep-02.md) |
| SEP-5 | Key Derivation Methods for Stellar Keys | [sep-05.md](sep-05.md) |
| SEP-6 | Programmatic Deposit and Withdrawal | [sep-06.md](sep-06.md) |
| SEP-8 | Regulated Assets | [sep-08.md](sep-08.md) |
| SEP-9 | Standard KYC Fields | [sep-09.md](sep-09.md) |
| SEP-10 | Web Authentication | [sep-10.md](sep-10.md) |
| SEP-12 | KYC API | [sep-12.md](sep-12.md) |
| SEP-24 | Interactive Deposit and Withdrawal | [sep-24.md](sep-24.md) |
| SEP-30 | Account Recovery | [sep-30.md](sep-30.md) |
| SEP-31 | Cross-Border Payments | [sep-31.md](sep-31.md) |
| SEP-38 | Anchor RFQ API | [sep-38.md](sep-38.md) |
| SEP-45 | Web Authentication for Contract Accounts | [sep-45.md](sep-45.md) |
| SEP-46 | Contract Meta | [Contract Parser](../advanced.md#contract-parser) |
| SEP-47 | Contract Interface Discovery | [Contract Parser](../advanced.md#contract-parser) |
| SEP-48 | Contract Interface Specification | [Contract Parser](../advanced.md#contract-parser) |
| SEP-51 | XDR-JSON | [sep-51.md](sep-51.md) |
| SEP-53 | Sign and Verify Messages | [sep-53.md](sep-53.md) |

## Compatibility Matrices

Detailed field-by-field coverage reports are generated automatically. See individual matrices for current numbers:

- [SEP-0001 Compatibility Matrix](../../compatibility/sep/SEP-0001_COMPATIBILITY_MATRIX.md)
- [SEP-0002 Compatibility Matrix](../../compatibility/sep/SEP-0002_COMPATIBILITY_MATRIX.md)
- [SEP-0005 Compatibility Matrix](../../compatibility/sep/SEP-0005_COMPATIBILITY_MATRIX.md)
- [SEP-0006 Compatibility Matrix](../../compatibility/sep/SEP-0006_COMPATIBILITY_MATRIX.md)
- [SEP-0008 Compatibility Matrix](../../compatibility/sep/SEP-0008_COMPATIBILITY_MATRIX.md)
- [SEP-0009 Compatibility Matrix](../../compatibility/sep/SEP-0009_COMPATIBILITY_MATRIX.md)
- [SEP-0010 Compatibility Matrix](../../compatibility/sep/SEP-0010_COMPATIBILITY_MATRIX.md)
- [SEP-0012 Compatibility Matrix](../../compatibility/sep/SEP-0012_COMPATIBILITY_MATRIX.md)
- [SEP-0024 Compatibility Matrix](../../compatibility/sep/SEP-0024_COMPATIBILITY_MATRIX.md)
- [SEP-0030 Compatibility Matrix](../../compatibility/sep/SEP-0030_COMPATIBILITY_MATRIX.md)
- [SEP-0031 Compatibility Matrix](../../compatibility/sep/SEP-0031_COMPATIBILITY_MATRIX.md)
- [SEP-0038 Compatibility Matrix](../../compatibility/sep/SEP-0038_COMPATIBILITY_MATRIX.md)
- [SEP-0045 Compatibility Matrix](../../compatibility/sep/SEP-0045_COMPATIBILITY_MATRIX.md)
- [SEP-0046 Compatibility Matrix](../../compatibility/sep/SEP-0046_COMPATIBILITY_MATRIX.md)
- [SEP-0047 Compatibility Matrix](../../compatibility/sep/SEP-0047_COMPATIBILITY_MATRIX.md)
- [SEP-0048 Compatibility Matrix](../../compatibility/sep/SEP-0048_COMPATIBILITY_MATRIX.md)
- [SEP-0051 Compatibility Matrix](../../compatibility/sep/SEP-0051_COMPATIBILITY_MATRIX.md)
- [SEP-0053 Compatibility Matrix](../../compatibility/sep/SEP-0053_COMPATIBILITY_MATRIX.md)

To regenerate all matrices: `python3 tools/matrix-generator/run_analysis.py`

---

**Last Updated**: 2026-08-05
