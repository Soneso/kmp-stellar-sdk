# Agent Skill for KMP Stellar SDK

An [Agent Skill](https://agentskills.io) that teaches AI coding agents how to build Stellar applications using `kmp-stellar-sdk`. Compatible with any agent that supports the Agent Skills open standard (Claude Code, Codex CLI, Cursor, Gemini CLI, and others).

## What it does

When installed, the skill gives your AI agent working knowledge of the SDK's API: transactions, operations, Horizon queries, Soroban smart contracts, XDR encoding, and SEP protocols. It includes correct method signatures, parameter types, and common patterns so the agent doesn't have to guess.

## Installation

### Manual

Download [kmp-stellar-sdk.zip](kmp-stellar-sdk.zip) and extract it into your agent's skill directory. Refer to your agent's documentation for the exact path.

```bash
# Claude Code
unzip kmp-stellar-sdk.zip -d .claude/skills/

# Codex CLI
unzip kmp-stellar-sdk.zip -d .codex/skills/
```

### Claude Code (via marketplace)

```bash
/plugin marketplace add Soneso/kmp-stellar-sdk
/plugin install kmp-stellar-sdk@soneso-kmp-stellar-sdk
```

## Skill structure

```text
kmp-stellar-sdk/
  SKILL.md                       # Core patterns and API overview (loaded when skill activates)
  references/                    # Detailed docs (loaded on demand by the agent)
    operations.md                # All Stellar operations
    horizon_api.md               # Horizon endpoint coverage
    horizon_streaming.md         # SSE streaming patterns
    rpc.md                       # Soroban RPC methods
    soroban_contracts.md         # Contract deploy/invoke/auth/bindings
    smart_accounts.md            # OZ smart account kit: config, wallet, signers, events
    smart_accounts_policies.md   # Context rules, policies, multi-signer flows
    smart_accounts_webauthn.md   # Platform WebAuthn adapters (Android/iOS/macOS/Web)
    xdr.md                       # XDR encoding/decoding
    troubleshooting.md           # Error codes and solutions
    advanced.md                  # Multi-sig, sponsorship, pools
    security.md                  # Security best practices
    api_reference.md             # Full API surface reference
    sep.md                       # SEP overview
    sep-XX.md                    # Per-SEP reference files
```

The skill uses progressive disclosure: only `SKILL.md` is loaded into context initially. Reference files are loaded by the agent only when needed, keeping token usage efficient.
