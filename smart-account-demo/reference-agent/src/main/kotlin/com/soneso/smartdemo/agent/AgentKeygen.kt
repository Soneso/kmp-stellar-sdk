package com.soneso.smartdemo.agent

import com.soneso.stellar.sdk.KeyPair
import com.soneso.stellar.sdk.secureRandomBytes

/** Number of hex characters in a raw 32-byte Ed25519 value (public key or seed). */
const val AGENT_HEX_KEY_LENGTH = 64

/** Outcome of resolving the agent's signing identity for the print-key mode. */
data class AgentKeyResult(
    /** The agent's raw 32-byte Ed25519 public key as 64-character lowercase hex. */
    val publicKeyHex: String,
    /**
     * Whether the key was newly generated (`true`) or derived from a supplied
     * seed (`false`).
     */
    val generated: Boolean,
    /**
     * The raw 32-byte secret seed as 64-character lowercase hex, to copy into the
     * agent config (`AGENT_SECRET_SEED`). Non-null only when [generated] is
     * `true`: a seed supplied by the operator is never echoed back.
     */
    val secretSeedHex: String? = null,
)

/**
 * Resolves the agent's identity for the print-key bootstrap mode.
 *
 * When [seed] is a non-empty, valid 64-character hex seed, derives and returns
 * its public key hex; [AgentKeyResult.generated] is `false` and
 * [AgentKeyResult.secretSeedHex] is `null`. Otherwise generates a fresh Ed25519
 * keypair from a cryptographically secure 32-byte seed and returns both the new
 * seed hex and its public key hex.
 *
 * Throws [AgentConfigException] when [seed] is non-empty but malformed.
 */
suspend fun resolveAgentKey(seed: String? = null): AgentKeyResult {
    if (!seed.isNullOrEmpty()) {
        val normalized = seed.trim()
        val bytes = if (isValidHexSeed(normalized)) Hex.decode(normalized.lowercase()) else null
        if (bytes == null) {
            throw AgentConfigException(
                "AGENT_SECRET_SEED is set but is not a valid 64-character hex Ed25519 seed."
            )
        }
        val keypair = KeyPair.fromSecretSeed(bytes)
        return AgentKeyResult(publicKeyHex = Hex.encode(keypair.getPublicKey()), generated = false)
    }
    val seedBytes = secureRandomBytes(32)
    val keypair = KeyPair.fromSecretSeed(seedBytes)
    return AgentKeyResult(
        publicKeyHex = Hex.encode(keypair.getPublicKey()),
        generated = true,
        secretSeedHex = Hex.encode(seedBytes),
    )
}

/**
 * Formats [result] into operator-facing console lines.
 *
 * For a generated key both the seed (to copy into `AGENT_SECRET_SEED`) and the
 * public key hex (to paste into the demo's Delegate-to-agent screen) are shown.
 * For a supplied seed only the derived public key hex is shown.
 */
fun formatAgentKeyOutput(result: AgentKeyResult): List<String> =
    if (result.generated) {
        listOf(
            "Generated a new agent Ed25519 keypair.",
            "AGENT_SECRET_SEED (copy into the agent config, keep secret): ${result.secretSeedHex ?: ""}",
            "Agent public key (paste into Delegate-to-agent): ${result.publicKeyHex}",
        )
    } else {
        listOf(
            "Derived the agent public key from AGENT_SECRET_SEED.",
            "Agent public key (paste into Delegate-to-agent): ${result.publicKeyHex}",
        )
    }

/**
 * Whether the print-key bootstrap mode is requested, via [env]
 * (`AGENT_PRINT_KEY=true`, case-insensitive) or [args] (`--print-key`).
 */
fun shouldPrintAgentKey(env: Map<String, String> = emptyMap(), args: List<String> = emptyList()): Boolean {
    val fromEnv = (env["AGENT_PRINT_KEY"] ?: "").lowercase() == "true"
    val fromArgs = args.contains("--print-key")
    return fromEnv || fromArgs
}

/** Whether [value] is exactly 64 hex characters (a raw 32-byte seed). */
private fun isValidHexSeed(value: String): Boolean =
    value.length == AGENT_HEX_KEY_LENGTH && Hex.isHexString(value)
