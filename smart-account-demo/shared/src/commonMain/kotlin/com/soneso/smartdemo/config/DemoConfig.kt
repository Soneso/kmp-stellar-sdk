package com.soneso.smartdemo.config

/**
 * Demo configuration with Stellar testnet defaults.
 *
 * These values point to shared testnet contracts deployed by the OpenZeppelin/Stellar team.
 * Using the same contracts and default deployer allows the demo to verify deterministic
 * behavior: identical inputs produce identical results across SDK implementations.
 */
object DemoConfig {
    // Network
    const val RPC_URL = "https://soroban-testnet.stellar.org"
    const val NETWORK_PASSPHRASE = "Test SDF Network ; September 2015"

    // Smart Account Contract
    const val ACCOUNT_WASM_HASH = "64086253db59176c3bbbcf57fbb68c0a2fbe6fe9e0b05883ff1da44c5978ae4c"

    // Verifier Contracts
    const val WEBAUTHN_VERIFIER_ADDRESS = "CBSHV66WG7UV6FQVUTB67P3DZUEJ2KJ5X6JKQH5MFRAAFNFJUAJVXJYV"
    const val ED25519_VERIFIER_ADDRESS = "CDGMOL3BP6Y6LYOXXTRNXBNJ2SLNTQ47BGG3LOS2OBBE657E3NYCN54B"

    // Token Contracts
    const val NATIVE_TOKEN_CONTRACT = "CDLZFC3SYJYDZT7K67VZ75HPJVIEUVNIXF47ZG2FB2RMQQVU2HHGCYSC"

    // Relayer (fee sponsoring via Cloudflare Workers proxy)
    const val DEFAULT_RELAYER_URL = "https://smart-account-relayer-proxy.soneso.workers.dev"

    // Indexer (credential-to-contract lookup)
    const val DEFAULT_INDEXER_URL = "https://smart-account-indexer.sdf-ecosystem.workers.dev"

    // Relying Party for WebAuthn
    const val DEFAULT_RP_ID = "soneso.com"
    const val RP_NAME = "Smart Account Kit Demo"

    // UI Constants
    const val MAX_LOG_ENTRIES = 50

    // Conversion
    const val STROOPS_PER_XLM = 10_000_000L
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
        address = "CCT4MMN5MJ6O2OU6LXPYTCVORQ2QVTBMDJ7MYBZQ2ULSYQVUIYP4IFYD"
    ),
    PolicyInfo(
        type = "spending_limit",
        name = "Spending Limit",
        description = "Limits spending to a maximum amount per time period",
        address = "CBMMWY54XOV6JJHSWCMKWWPXVRXASR5U26UJMLZDN4SP6CFFTVZARPTY"
    ),
    PolicyInfo(
        type = "weighted_threshold",
        name = "Weighted Threshold",
        description = "Requires minimum total weight from signers with different voting weights",
        address = "CBYDQ5XUBP7G24FI3LLGLW56QZCIEUSVRPX7FVOUCKHJQQ6DTF6BQGBZ"
    )
)
