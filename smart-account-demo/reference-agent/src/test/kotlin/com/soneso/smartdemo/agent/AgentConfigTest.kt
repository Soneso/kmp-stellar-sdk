package com.soneso.smartdemo.agent

import com.soneso.stellar.sdk.KeyPair
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AgentConfigTest {

    private val validSeed = "01".repeat(32)

    private fun randomGAddress(): String = runBlocking { KeyPair.random().getAccountId() }

    private fun completeConfig(): AgentConfig = AgentConfig(
        smartAccountContractId = AgentDefaults.NATIVE_TOKEN_CONTRACT,
        agentSecretSeed = validSeed,
        destinationAddress = randomGAddress(),
    )

    // MARK: - defaults

    @Test
    fun aBareConfigCarriesTheDemoTestnetDefaults() {
        val config = AgentConfig()
        assertEquals(AgentDefaults.RPC_URL, config.rpcUrl)
        assertEquals(AgentDefaults.NETWORK_PASSPHRASE, config.networkPassphrase)
        assertEquals(AgentDefaults.ACCOUNT_WASM_HASH, config.accountWasmHash)
        assertEquals(AgentDefaults.WEBAUTHN_VERIFIER_ADDRESS, config.webauthnVerifierAddress)
        assertEquals(AgentDefaults.ED25519_VERIFIER_ADDRESS, config.ed25519VerifierAddress)
        assertEquals(AgentDefaults.RELAYER_URL, config.relayerUrl)
        assertEquals(AgentDefaults.NATIVE_TOKEN_CONTRACT, config.tokenContractId)
        assertEquals(7, config.tokenDecimals)
        assertEquals(AgentDefaults.COORDINATION_BASE_URL, config.coordinationBaseUrl)
        assertEquals(AgentDefaults.COORDINATION_TOKEN, config.coordinationToken)
        assertNull(config.smartAccountContractId)
        assertFalse(config.isCompleteForLiveRun)
    }

    @Test
    fun descriptionRedactsTheSeedAndCoordinationToken() {
        val text = AgentConfig(agentSecretSeed = validSeed, coordinationToken = "super-secret").toString()
        assertTrue(text.contains("agentSecretSeed=***"))
        assertTrue(text.contains("coordinationToken=***"))
        assertFalse(text.contains("super-secret"))
    }

    // MARK: - resolve precedence

    @Test
    fun emptyInputsFallBackToDefaults() {
        val config = AgentConfig.resolve(env = emptyMap())
        assertEquals(AgentDefaults.RPC_URL, config.rpcUrl)
        assertNull(config.smartAccountContractId)
    }

    @Test
    fun environmentOverridesDefaults() {
        val config = AgentConfig.resolve(
            env = mapOf(
                "AGENT_RPC_URL" to "https://env.example/rpc",
                "AGENT_SMART_ACCOUNT" to "CENV",
                "AGENT_AMOUNT" to "42",
                "AGENT_POLL_INTERVAL_SECONDS" to "7",
            ),
        )
        assertEquals("https://env.example/rpc", config.rpcUrl)
        assertEquals("CENV", config.smartAccountContractId)
        assertEquals("42", config.amount)
        assertEquals(7, config.pollIntervalSeconds)
    }

    @Test
    fun argsOverrideEnvironment() {
        val config = AgentConfig.resolve(
            args = listOf("--rpc-url=https://arg.example/rpc", "--amount", "9"),
            env = mapOf("AGENT_RPC_URL" to "https://env.example/rpc", "AGENT_AMOUNT" to "42"),
        )
        assertEquals("https://arg.example/rpc", config.rpcUrl)
        assertEquals("9", config.amount)
    }

    @Test
    fun nonIntegerPollIntervalIsRejected() {
        assertFailsWith<AgentConfigException> {
            AgentConfig.resolve(env = mapOf("AGENT_POLL_INTERVAL_SECONDS" to "soon"))
        }
    }

    // MARK: - JSON config file layer

    private fun writeTempJson(contents: String): String {
        val file = File.createTempFile("agent-config", ".json")
        file.deleteOnExit()
        file.writeText(contents)
        return file.absolutePath
    }

    @Test
    fun jsonFileOverridesDefaultsButSitsBelowEnvAndArgs() {
        val path = writeTempJson(
            """
            {
              "rpcUrl": "https://json.example/rpc",
              "amount": "11",
              "smartAccountContractId": "CJSON",
              "pollIntervalSeconds": 9
            }
            """.trimIndent()
        )

        // Only the JSON file present: its values win over defaults.
        val fromJson = AgentConfig.resolve(env = emptyMap(), jsonPath = path)
        assertEquals("https://json.example/rpc", fromJson.rpcUrl)
        assertEquals("11", fromJson.amount)
        assertEquals("CJSON", fromJson.smartAccountContractId)
        assertEquals(9, fromJson.pollIntervalSeconds)
        // Untouched keys still fall back to defaults.
        assertEquals(AgentDefaults.NETWORK_PASSPHRASE, fromJson.networkPassphrase)

        // Env overrides the JSON file; args override env.
        val layered = AgentConfig.resolve(
            args = listOf("--amount=33"),
            env = mapOf("AGENT_RPC_URL" to "https://env.example/rpc"),
            jsonPath = path,
        )
        assertEquals("https://env.example/rpc", layered.rpcUrl) // env beats json
        assertEquals("33", layered.amount) // arg beats json
        assertEquals("CJSON", layered.smartAccountContractId) // json still applies where unset
        assertEquals(9, layered.pollIntervalSeconds)
    }

    @Test
    fun configFileArgSelectsTheJsonFile() {
        val path = writeTempJson("""{ "amount": "77" }""")
        val config = AgentConfig.resolve(args = listOf("--config=$path"), env = emptyMap())
        assertEquals("77", config.amount)
    }

    @Test
    fun agentConfigFileEnvSelectsTheJsonFile() {
        val path = writeTempJson("""{ "amount": "88" }""")
        val config = AgentConfig.resolve(env = mapOf("AGENT_CONFIG_FILE" to path))
        assertEquals("88", config.amount)
    }

    @Test
    fun aMissingConfigFileFailsLoudly() {
        assertFailsWith<AgentConfigException> {
            AgentConfig.resolve(
                env = mapOf("AGENT_CONFIG_FILE" to "/no/such/agent-config.json"),
            )
        }
    }

    @Test
    fun aNonIntegerJsonPollIntervalIsRejected() {
        val path = writeTempJson("""{ "pollIntervalSeconds": "soon" }""")
        assertFailsWith<AgentConfigException> {
            AgentConfig.resolve(jsonPath = path)
        }
    }

    // MARK: - validateForLiveRun

    @Test
    fun passesForACompleteConfiguration() {
        val config = completeConfig()
        assertTrue(config.isCompleteForLiveRun)
        config.validateForLiveRun()
    }

    @Test
    fun requiresTheSmartAccount() {
        assertFailsWith<AgentConfigException> {
            completeConfig().copy(smartAccountContractId = "").validateForLiveRun()
        }
    }

    @Test
    fun rejectsANonHexAgentSeed() {
        assertFailsWith<AgentConfigException> {
            completeConfig().copy(agentSecretSeed = "not-a-seed").validateForLiveRun()
        }
    }

    @Test
    fun rejectsAWrongLengthHexAgentSeed() {
        // Valid hex but 62 characters — one byte short of a 32-byte seed.
        assertFailsWith<AgentConfigException> {
            completeConfig().copy(agentSecretSeed = "a".repeat(62)).validateForLiveRun()
        }
    }

    @Test
    fun rejectsAnInvalidDestinationAddress() {
        assertFailsWith<AgentConfigException> {
            completeConfig().copy(destinationAddress = "nonsense").validateForLiveRun()
        }
    }

    @Test
    fun acceptsAContractDestinationAddress() {
        completeConfig().copy(destinationAddress = AgentDefaults.NATIVE_TOKEN_CONTRACT).validateForLiveRun()
    }
}
