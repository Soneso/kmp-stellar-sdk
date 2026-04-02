package com.soneso.smartdemo.config

/**
 * Demo configuration with Stellar testnet defaults.
 *
 * These values point to shared testnet contracts deployed by the OpenZeppelin/Stellar team.
 * Using the same contracts and default deployer allows the demo to verify deterministic
 * behavior: identical inputs produce identical results across SDK implementations.
 */
object DemoConfig {

    // -- Network --

    /** Soroban RPC endpoint for testnet. Used by all SDK operations. */
    const val RPC_URL = "https://soroban-testnet.stellar.org"

    /** Stellar testnet network passphrase. Used for transaction signing and contract address derivation. */
    const val NETWORK_PASSPHRASE = "Test SDF Network ; September 2015"

    // -- Smart Account Contract --

    /** WASM hash of the multisig smart account contract (OZ stellar-contracts v0.7.0).
     *  Passed to OZSmartAccountConfig.accountWasmHash for wallet deployment.
     *  This hash can change when the contract is upgraded or testnet is reset.
     *  See docs/smart-accounts/README.md#testnet-contract-addresses for upload instructions. */
    const val ACCOUNT_WASM_HASH = "3e51f5b222dec74650f0b33367acb42a41ce497f72639230463070e666abba2c"

    // -- Verifier Contracts --

    /** WebAuthn (secp256r1) signature verifier contract. Validates passkey signatures on-chain. */
    const val WEBAUTHN_VERIFIER_ADDRESS = "CATPTBRWVMH5ZCIKO5HN2F4FMPXVZEXC56RKGHRXCM7EEZGGXK7PICEH"

    /** Ed25519 signature verifier contract. Validates Ed25519 signer signatures on-chain. */
    const val ED25519_VERIFIER_ADDRESS = "CAIKK32K3BZJYTWVTXHZFPIEEDBR6YCVTGPABH4UQUQ4XFA3OLYXG27G"

    // -- Token Contracts --

    /** XLM native token Stellar Asset Contract (SAC) address on testnet.
     *  Used for XLM transfers via the SAC token interface. */
    const val NATIVE_TOKEN_CONTRACT = "CDLZFC3SYJYDZT7K67VZ75HPJVIEUVNIXF47ZG2FB2RMQQVU2HHGCYSC"

    // -- Demo Token --
    // DemoTokenService deploys a custom Soroban token for testing transfers.
    // All values below are used by DemoTokenService to deterministically deploy
    // and mint the token. The contract address is derived from the admin seed,
    // salt seed, and network passphrase — same address on every platform.

    /** Display name passed to the token contract constructor. */
    const val DEMO_TOKEN_NAME = "Demo Token"

    /** Ticker symbol passed to the token contract constructor. */
    const val DEMO_TOKEN_SYMBOL = "DEMO"

    /** Decimal places for the demo token (7 = same as XLM, so 10_000_000 stroops = 1 token). */
    const val DEMO_TOKEN_DECIMALS = 7

    /** Amount minted per wallet creation: 10,000 DEMO in stroops (10,000 * 10^7). */
    const val DEMO_TOKEN_MINT_AMOUNT = 100_000_000_000L

    /** Seed string for deriving the token admin keypair via SHA256.
     *  The admin deploys the token contract and has mint authority. */
    const val DEMO_TOKEN_ADMIN_SEED = "KMP smart account demo token admin"

    /** Seed string for deriving the deployment salt via SHA256.
     *  Combined with the admin public key and network passphrase,
     *  this produces a deterministic token contract address. */
    const val DEMO_TOKEN_SALT_SEED = "KMP smart account demo token"

    // -- Relayer --

    /** Relayer proxy for fee-sponsored transaction submission. The relayer wraps
     *  transactions in a fee-bump so users don't need XLM to pay fees. */
    const val DEFAULT_RELAYER_URL = "https://smart-account-relayer-proxy.soneso.workers.dev"

    // -- Indexer --

    /** Indexer for credential-to-contract address lookup. Maps a passkey credential ID
     *  to its deployed smart account contract address. */
    const val DEFAULT_INDEXER_URL = "https://smart-account-indexer.sdf-ecosystem.workers.dev"

    // -- WebAuthn --

    /** Relying Party ID for passkey registration and authentication. Must match the domain
     *  configured in Digital Asset Links (Android) or apple-app-site-association (iOS/macOS). */
    const val DEFAULT_RP_ID = "soneso.com"

    /** Display name shown to users during passkey registration prompts. */
    const val RP_NAME = "Smart Account Kit Demo"

    // -- Context Rule Discovery --

    /** Maximum context rule ID to scan when iterating over rules by ID.
     *  Acts as a safety cap to prevent unbounded iteration if the active count is stale.
     *  The contract uses monotonically increasing IDs with gaps from removed rules. */
    val MAX_CONTEXT_RULE_SCAN_ID = 25u
}

/**
 * Known policy contracts deployed on testnet.
 */
data class PolicyInfo(
    val type: String,
    val name: String,
    val description: String,
    val address: String
)

val KNOWN_POLICIES = listOf(
    PolicyInfo(
        type = "threshold",
        name = "Threshold (M-of-N)",
        description = "Requires M signatures out of N total signers",
        address = "CDDQLFG7CV74QHWPSP6NZIPNBR2PPCMTUVYCJF4P3ONDYHODRFGR7LWC"
    ),
    PolicyInfo(
        type = "spending_limit",
        name = "Spending Limit",
        description = "Limits spending to a maximum amount per time period",
        address = "CBYLPYZGLQ6JVY2IQ5P23QLQPR3KAMMKMZLNWG6RUUKJDNYGPLVHK7U4"
    ),
    PolicyInfo(
        type = "weighted_threshold",
        name = "Weighted Threshold",
        description = "Requires minimum total weight from signers with different voting weights",
        address = "CBYDQ5XUBP7G24FI3LLGLW56QZCIEUSVRPX7FVOUCKHJQQ6DTF6BQGBZ"
    )
)
