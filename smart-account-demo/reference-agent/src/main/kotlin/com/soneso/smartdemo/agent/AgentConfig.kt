package com.soneso.smartdemo.agent

import com.soneso.stellar.sdk.StrKey
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.File

/**
 * Static testnet defaults shared by every reference-agent run.
 *
 * Every value mirrors a constant already published in the demo app's
 * configuration. They are testnet-only, public by design, and safe to ship as
 * defaults. Per-run identity values (smart account, agent seed, destination)
 * have no static default and must be supplied explicitly.
 */
object AgentDefaults {
    /** Soroban RPC endpoint for testnet. */
    const val RPC_URL = "https://soroban-testnet.stellar.org"

    /** Stellar testnet network passphrase. */
    const val NETWORK_PASSPHRASE = "Test SDF Network ; September 2015"

    /** WASM hash of the multisig smart-account contract deployed on testnet. */
    const val ACCOUNT_WASM_HASH = "86b49fe03f7df0ad1c2a28bd8361b923ab57096e09f397f92f0c00ae3bd06d28"

    /**
     * WebAuthn (secp256r1) signature verifier contract address. Required by
     * `OZSmartAccountConfig` even though the headless agent never signs with a
     * passkey.
     */
    const val WEBAUTHN_VERIFIER_ADDRESS = "CB26VN37RCVNTHJZDEPK6IRO2MMTS3Z2IEO5JD5BINY2OOJ5KKJG7NKY"

    /**
     * Ed25519 signature verifier contract address. The agent registers as an
     * `External(ed25519VerifierAddress, publicKey)` signer under this verifier.
     */
    const val ED25519_VERIFIER_ADDRESS = "CAW2Z46INPO5VIJEILMYSSEOLBVJIIII5GOE3TN5EUURSRM2FJCF7AJ6"

    /**
     * Relayer proxy for fee-sponsored (gasless) submission. The empty string
     * disables the relayer and submits directly via the RPC endpoint.
     */
    const val RELAYER_URL = "https://smart-account-relayer-proxy.soneso.workers.dev"

    /**
     * XLM native token Stellar Asset Contract (SAC) on testnet. Used as the
     * default scoped-call target token.
     */
    const val NATIVE_TOKEN_CONTRACT = "CDLZFC3SYJYDZT7K67VZ75HPJVIEUVNIXF47ZG2FB2RMQQVU2HHGCYSC"

    /**
     * Decimal scale used when converting the human-readable [AgentConfig.amount]
     * to base units (7 = same scale as XLM and the demo token).
     */
    const val TOKEN_DECIMALS = 7

    /** Default human-readable transfer amount. */
    const val AMOUNT = "1"

    /** Coordination server base URL. Matches the server's default bind port. */
    const val COORDINATION_BASE_URL = "http://localhost:8787"

    /** Coordination server bearer token. Matches the server's documented dev token. */
    const val COORDINATION_TOKEN = "dev-token-change-me"

    /** Seconds between successive escalation polls. */
    const val POLL_INTERVAL_SECONDS = 3

    /** Maximum number of escalation polls before the agent gives up waiting. */
    const val POLL_MAX_ATTEMPTS = 40

    /**
     * Known testnet policy contracts, by policy type. Informational reference for operators
     * wiring up the step-2 delegation flow; the agent does not install policies itself. Keyed
     * to match the Flutter and iOS reference agents' `knownPolicies` maps.
     */
    val knownPolicies: Map<String, String> = mapOf(
        "threshold" to "CAZJ3UVRY3R3S5C5BH32GMYBRSN23N75ZEEXEOLXOUUAHDFIMVP4AXUC",
        "spendingLimit" to "CBQE7L3UNP5IR4I7IBKLS7NV256WHR5TTH26HTMUIK7WXJC6J64RSE2L",
        "weightedThreshold" to "CAF4OCRIB73T5777UWAQS7KGOG6WVIZ3EFXNNUYSPFSBKW2Q5XEIOSPW",
    )
}

/** Thrown when an [AgentConfig] cannot satisfy the requirements of a live run. */
class AgentConfigException(override val message: String) : Exception(message)

/**
 * Immutable configuration for a single reference-agent run.
 *
 * Construct directly for tests, or via [AgentConfig.resolve] to layer
 * command-line arguments over environment variables over an optional JSON config
 * file over the [AgentDefaults]. Precedence, highest first: CLI args >
 * environment > JSON file > defaults.
 */
data class AgentConfig(
    val rpcUrl: String = AgentDefaults.RPC_URL,
    val networkPassphrase: String = AgentDefaults.NETWORK_PASSPHRASE,
    val accountWasmHash: String = AgentDefaults.ACCOUNT_WASM_HASH,
    val webauthnVerifierAddress: String = AgentDefaults.WEBAUTHN_VERIFIER_ADDRESS,
    val ed25519VerifierAddress: String = AgentDefaults.ED25519_VERIFIER_ADDRESS,
    val relayerUrl: String = AgentDefaults.RELAYER_URL,
    val tokenContractId: String = AgentDefaults.NATIVE_TOKEN_CONTRACT,
    val tokenDecimals: Int = AgentDefaults.TOKEN_DECIMALS,
    val amount: String = AgentDefaults.AMOUNT,
    val smartAccountContractId: String? = null,
    val agentSecretSeed: String? = null,
    val destinationAddress: String? = null,
    val coordinationBaseUrl: String = AgentDefaults.COORDINATION_BASE_URL,
    val coordinationToken: String = AgentDefaults.COORDINATION_TOKEN,
    val pollIntervalSeconds: Int = AgentDefaults.POLL_INTERVAL_SECONDS,
    val pollMaxAttempts: Int = AgentDefaults.POLL_MAX_ATTEMPTS,
) {
    /** Whether every value required for a live, end-to-end run is present. */
    val isCompleteForLiveRun: Boolean
        get() = try {
            validateForLiveRun()
            true
        } catch (_: AgentConfigException) {
            false
        }

    /**
     * Validates that the per-run identity values are present and well-formed.
     *
     * Throws [AgentConfigException] describing the first problem found.
     */
    fun validateForLiveRun() {
        val smartAccount = smartAccountContractId
        if (smartAccount.isNullOrEmpty()) {
            throw AgentConfigException("smartAccountContractId is required.")
        }
        if (!StrKey.isValidContract(smartAccount)) {
            throw AgentConfigException(
                "smartAccountContractId is not a valid contract address: $smartAccount"
            )
        }

        val seed = agentSecretSeed
        if (seed.isNullOrEmpty()) {
            throw AgentConfigException("agentSecretSeed is required.")
        }
        if (seed.length != 64 || !Hex.isHexString(seed)) {
            throw AgentConfigException(
                "agentSecretSeed is not a valid 64-character hex Ed25519 seed."
            )
        }

        val destination = destinationAddress
        if (destination.isNullOrEmpty()) {
            throw AgentConfigException("destinationAddress is required.")
        }
        if (!StrKey.isValidEd25519PublicKey(destination) && !StrKey.isValidContract(destination)) {
            throw AgentConfigException(
                "destinationAddress is not a valid G- or C-address: $destination"
            )
        }

        if (!StrKey.isValidContract(ed25519VerifierAddress)) {
            throw AgentConfigException(
                "ed25519VerifierAddress is not a valid contract address: $ed25519VerifierAddress"
            )
        }
        if (!StrKey.isValidContract(tokenContractId)) {
            throw AgentConfigException(
                "tokenContractId is not a valid contract address: $tokenContractId"
            )
        }
        if (coordinationBaseUrl.isEmpty()) {
            throw AgentConfigException("coordinationBaseUrl is required.")
        }
        if (coordinationToken.isEmpty()) {
            throw AgentConfigException("coordinationToken is required.")
        }
    }

    /** Redacts the agent seed and bearer token so the config is safe to log. */
    override fun toString(): String =
        "AgentConfig(rpcUrl=$rpcUrl, network=$networkPassphrase, " +
            "smartAccount=${smartAccountContractId ?: "null"}, " +
            "ed25519Verifier=$ed25519VerifierAddress, token=$tokenContractId, amount=$amount, " +
            "destination=${destinationAddress ?: "null"}, " +
            "relayer=${if (relayerUrl.isEmpty()) "(disabled)" else relayerUrl}, " +
            "coordination=$coordinationBaseUrl, " +
            "agentSecretSeed=${if (agentSecretSeed == null) "null" else "***"}, " +
            "coordinationToken=***, " +
            "pollIntervalSeconds=$pollIntervalSeconds, pollMaxAttempts=$pollMaxAttempts)"

    companion object {
        /**
         * Resolves a configuration by layering, highest precedence first:
         * [args] (`--kebab-key=value` or `--kebab-key value`) > [env]
         * (`AGENT_UPPER_SNAKE`) > the JSON file at `--config` / `AGENT_CONFIG_FILE`
         * / [jsonPath] (camelCase keys) > [AgentDefaults].
         *
         * The JSON file, when supplied, must exist and decode to a JSON object
         * whose keys are the camelCase field names; otherwise an
         * [AgentConfigException] is thrown so a misconfigured file fails loudly
         * instead of being silently ignored.
         *
         * Throws [AgentConfigException] on a non-integer numeric value.
         */
        fun resolve(
            args: List<String> = emptyList(),
            env: Map<String, String> = emptyMap(),
            jsonPath: String? = null,
        ): AgentConfig {
            val argMap = parseArgs(args)

            val resolvedJsonPath = argMap["config"] ?: env["AGENT_CONFIG_FILE"] ?: jsonPath
            val json: Map<String, String> =
                if (resolvedJsonPath != null) readJsonFile(resolvedJsonPath) else emptyMap()

            fun pick(argKey: String, envKey: String, jsonKey: String): String? =
                argMap[argKey] ?: env[envKey] ?: json[jsonKey]

            fun pickInt(argKey: String, envKey: String, jsonKey: String, fallback: Int): Int {
                val raw = pick(argKey, envKey, jsonKey) ?: return fallback
                return raw.toIntOrNull()
                    ?: throw AgentConfigException("$jsonKey must be an integer, got: $raw")
            }

            return AgentConfig(
                rpcUrl = pick("rpc-url", "AGENT_RPC_URL", "rpcUrl") ?: AgentDefaults.RPC_URL,
                networkPassphrase = pick("network-passphrase", "AGENT_NETWORK_PASSPHRASE", "networkPassphrase")
                    ?: AgentDefaults.NETWORK_PASSPHRASE,
                accountWasmHash = pick("account-wasm-hash", "AGENT_ACCOUNT_WASM_HASH", "accountWasmHash")
                    ?: AgentDefaults.ACCOUNT_WASM_HASH,
                webauthnVerifierAddress = pick("webauthn-verifier", "AGENT_WEBAUTHN_VERIFIER", "webauthnVerifierAddress")
                    ?: AgentDefaults.WEBAUTHN_VERIFIER_ADDRESS,
                ed25519VerifierAddress = pick("ed25519-verifier", "AGENT_ED25519_VERIFIER", "ed25519VerifierAddress")
                    ?: AgentDefaults.ED25519_VERIFIER_ADDRESS,
                relayerUrl = pick("relayer-url", "AGENT_RELAYER_URL", "relayerUrl") ?: AgentDefaults.RELAYER_URL,
                tokenContractId = pick("token-contract", "AGENT_TOKEN_CONTRACT", "tokenContractId")
                    ?: AgentDefaults.NATIVE_TOKEN_CONTRACT,
                tokenDecimals = pickInt("token-decimals", "AGENT_TOKEN_DECIMALS", "tokenDecimals", AgentDefaults.TOKEN_DECIMALS),
                amount = pick("amount", "AGENT_AMOUNT", "amount") ?: AgentDefaults.AMOUNT,
                smartAccountContractId = pick("smart-account", "AGENT_SMART_ACCOUNT", "smartAccountContractId"),
                agentSecretSeed = pick("secret-seed", "AGENT_SECRET_SEED", "agentSecretSeed"),
                destinationAddress = pick("destination", "AGENT_DESTINATION", "destinationAddress"),
                coordinationBaseUrl = pick("coordination-url", "AGENT_COORDINATION_URL", "coordinationBaseUrl")
                    ?: AgentDefaults.COORDINATION_BASE_URL,
                coordinationToken = pick("coordination-token", "AGENT_COORDINATION_TOKEN", "coordinationToken")
                    ?: AgentDefaults.COORDINATION_TOKEN,
                pollIntervalSeconds = pickInt(
                    "poll-interval-seconds", "AGENT_POLL_INTERVAL_SECONDS", "pollIntervalSeconds",
                    AgentDefaults.POLL_INTERVAL_SECONDS
                ),
                pollMaxAttempts = pickInt(
                    "poll-max-attempts", "AGENT_POLL_MAX_ATTEMPTS", "pollMaxAttempts", AgentDefaults.POLL_MAX_ATTEMPTS
                ),
            )
        }

        /**
         * Reads [path] and flattens its top-level JSON object into a map of
         * camelCase key to its scalar value rendered as a string. Scalars
         * (string, number, boolean) are coerced to their textual form so the
         * layered resolver can treat every source uniformly.
         *
         * Throws [AgentConfigException] when the file is missing, unreadable, not
         * valid JSON, or not a JSON object.
         */
        internal fun readJsonFile(path: String): Map<String, String> {
            val file = File(path)
            if (!file.exists()) {
                throw AgentConfigException("Config file not found: $path")
            }
            val text = try {
                file.readText()
            } catch (e: Exception) {
                throw AgentConfigException("Failed to read JSON config $path: ${e.message}")
            }
            val element = try {
                Json.parseToJsonElement(text)
            } catch (e: Exception) {
                throw AgentConfigException("Failed to parse JSON config $path: ${e.message}")
            }
            if (element !is JsonObject) {
                throw AgentConfigException("JSON config $path must decode to an object")
            }
            val result = mutableMapOf<String, String>()
            for ((key, value) in element) {
                if (value is JsonPrimitive) {
                    // content drops the surrounding quotes for strings and renders
                    // numbers and booleans as their literal text.
                    result[key] = value.content
                } else {
                    // Nested objects and arrays have no scalar form the resolver
                    // can consume; keep the raw JSON so a misuse is visible.
                    result[key] = value.toString()
                }
            }
            return result
        }

        /**
         * Parses `--key=value` and `--key value` argument pairs into a map keyed
         * by the kebab-case option name (without the leading `--`). A bare flag
         * with no following value is recorded as `"true"`.
         */
        internal fun parseArgs(args: List<String>): Map<String, String> {
            val map = mutableMapOf<String, String>()
            var i = 0
            while (i < args.size) {
                val arg = args[i]
                if (!arg.startsWith("--")) {
                    i += 1
                    continue
                }
                val body = arg.substring(2)
                val eq = body.indexOf('=')
                if (eq >= 0) {
                    map[body.substring(0, eq)] = body.substring(eq + 1)
                } else if (i + 1 < args.size && !args[i + 1].startsWith("--")) {
                    map[body] = args[i + 1]
                    i += 1
                } else {
                    map[body] = "true"
                }
                i += 1
            }
            return map
        }
    }
}
