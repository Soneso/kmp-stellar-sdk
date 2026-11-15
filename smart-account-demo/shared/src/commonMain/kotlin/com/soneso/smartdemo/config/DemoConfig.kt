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

    /** WASM hash of the multisig smart account contract (OZ stellar-contracts v0.8.0).
     *  Passed to OZSmartAccountConfig.accountWasmHash for wallet deployment. The SDK signs
     *  for this build with the v0.8.0 digest and for wallets on other builds, such as the
     *  v0.7.0 build 86b49fe0, with the v0.7 digest.
     *  This hash can change when the contract is upgraded or testnet is reset.
     *  See docs/smart-accounts/README.md#testnet-contract-addresses for upload instructions. */
    const val ACCOUNT_WASM_HASH = "c514d35ea67eaea9bfc1fb3327854c0c06111bc1ddebd4fe217c504c1004b9e1"

    // -- Verifier Contracts --

    /** WebAuthn (secp256r1) signature verifier contract. Validates passkey signatures on-chain. */
    const val WEBAUTHN_VERIFIER_ADDRESS = "CB26VN37RCVNTHJZDEPK6IRO2MMTS3Z2IEO5JD5BINY2OOJ5KKJG7NKY"

    /** Ed25519 signature verifier contract. Validates Ed25519 signer signatures on-chain. */
    const val ED25519_VERIFIER_ADDRESS = "CAW2Z46INPO5VIJEILMYSSEOLBVJIIII5GOE3TN5EUURSRM2FJCF7AJ6"

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
    const val DEFAULT_INDEXER_URL = "https://testnet.mercurydata.app/rest/smart-account-indexer"

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

    // -- Coordination Server (agent-signer flow) --

    /** Base URL of the coordination server that brokers policy-rejected agent calls.
     *  The reference agent posts escalations here; the approval inbox reads them.
     *  The default points at a locally run server, reachable as `localhost` from the iOS
     *  simulator, macOS, and the web build. On the Android emulator the host machine is NOT
     *  `localhost` (that resolves to the emulator itself); use `http://10.0.2.2:8787` to reach
     *  a coordination server running on the developer machine. Override per environment.
     *
     *  The bearer-token-over-HTTP default here is for local dev / testnet only. Any non-localhost
     *  deployment MUST serve the coordination API over HTTPS so the token below is never sent in
     *  clear text. */
    const val COORDINATION_URL = "http://localhost:8787"

    /** Bearer token sent on every coordination-server request. Must match the
     *  token the coordination server was started with (`--token`). Dev/testnet-only:
     *  see [COORDINATION_URL] for the HTTPS-off-localhost requirement. */
    const val COORDINATION_TOKEN = "dev-token-change-me"

    // -- Block Explorer --

    /** Base URL for a Stellar testnet transaction on the stellar.expert block explorer.
     *  The approval inbox appends a confirmed transaction hash to build a viewable link. */
    const val STELLAR_EXPERT_TESTNET_TX_BASE = "https://stellar.expert/explorer/testnet/tx/"

    // -- Reown (WalletConnect) --

    /**
     * Reown (WalletConnect) project ID for external wallet connection via Freighter Mobile.
     * Required for wallet connection on Android and iOS real devices. Not needed for
     * simulators, emulators, or web (which uses the Freighter browser extension directly).
     * Register for a free project ID at https://cloud.reown.com/.
     */
    const val REOWN_PROJECT_ID = ""
}

/**
 * A policy contract deployed on testnet.
 */
data class PolicyInfo(
    val type: String,
    val name: String,
    val description: String,
    val address: String
)

/**
 * The OZ stellar-contracts v0.8.0 policy contracts, offered for new rules.
 */
val KNOWN_POLICIES = listOf(
    PolicyInfo(
        type = "threshold",
        name = "Threshold (M-of-N)",
        description = "Requires M signatures out of N total signers",
        address = "CAZ2WFUDXGX265LOEARFEOYYUL6F4BYWXZLGUAWZNJY3MRFPNLSZVS4S"
    ),
    PolicyInfo(
        type = "spending_limit",
        name = "Spending Limit",
        description = "Limits spending to a maximum amount per time period",
        address = "CC3CEYQAP6NPNFIYSWL5MVZRCPJZGWX4XO7WDEJOUVGO2FZX5OONYPA5"
    ),
    PolicyInfo(
        type = "weighted_threshold",
        name = "Weighted Threshold",
        description = "Requires minimum total weight from signers with different voting weights",
        address = "CDBYLFLUNPAYGE3AHZSMB4TFWECFCCHX6CJFMFO6HRPKK2W5PFU2LCKE"
    )
)

/**
 * The OZ stellar-contracts v0.7.0 policy contracts that rules on v0.7.0 wallets use. The demo
 * reads and labels them but does not offer them for new rules.
 */
private val LEGACY_POLICIES = listOf(
    "threshold" to "CAZJ3UVRY3R3S5C5BH32GMYBRSN23N75ZEEXEOLXOUUAHDFIMVP4AXUC",
    "spending_limit" to "CBQE7L3UNP5IR4I7IBKLS7NV256WHR5TTH26HTMUIK7WXJC6J64RSE2L",
    "weighted_threshold" to "CAF4OCRIB73T5777UWAQS7KGOG6WVIZ3EFXNNUYSPFSBKW2Q5XEIOSPW"
).map { (type, address) ->
    val current = KNOWN_POLICIES.first { it.type == type }
    current.copy(name = "${current.name} (v0.7.0)", address = address)
}

/**
 * Every policy contract the demo can read on an existing rule: the offered v0.8.0 policies
 * first, then the v0.7.0 policies.
 */
val RECOGNIZED_POLICIES: List<PolicyInfo> = KNOWN_POLICIES + LEGACY_POLICIES.filter { legacy ->
    KNOWN_POLICIES.none { it.address == legacy.address }
}
