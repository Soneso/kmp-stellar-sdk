package com.soneso.smartdemo.agent

import kotlinx.coroutines.runBlocking
import kotlin.system.exitProcess

/**
 * Command-line entry point. Three modes, selected by environment gates that
 * mirror the Flutter and iOS reference agents:
 *
 *   - AGENT_PRINT_KEY=true (or --print-key): bootstrap keygen. Derives or
 *     generates the agent's Ed25519 identity and prints the `[agent] [KEY]`
 *     lines. Needs no other configuration.
 *
 *   - AGENT_RUN_LIVE=true with a complete live config: runs one full agent cycle
 *     against testnet and a running coordination server.
 *
 *   - Otherwise: prints usage and exits without touching the network.
 */
fun main(args: Array<String>) = runBlocking {
    val argv = args.toList()
    val env = System.getenv()
    val logger = StdoutAgentLogger()

    when {
        shouldPrintAgentKey(env, argv) -> {
            try {
                val result = resolveAgentKey(env["AGENT_SECRET_SEED"])
                for (line in formatAgentKeyOutput(result)) {
                    println("[agent] [KEY] $line")
                }
            } catch (e: Exception) {
                logger.error("Failed to resolve agent key: ${e.message}")
                exitProcess(1)
            }
        }

        runLiveRequested(env) -> {
            try {
                val config = AgentConfig.resolve(argv, env)
                config.validateForLiveRun()
                logger.info(config.toString())
                val agent = Agent.fromConfig(config, logger)
                try {
                    val result = agent.run()
                    logger.info("Agent result: $result")
                } finally {
                    agent.dispose()
                }
            } catch (e: Exception) {
                logger.error("Live run failed: ${e.message}")
                exitProcess(1)
            }
        }

        else -> printUsage()
    }
}

private fun runLiveRequested(env: Map<String, String>): Boolean =
    (env["AGENT_RUN_LIVE"] ?: "").lowercase() == "true"

private fun printUsage() {
    println(
        """
        reference-agent - autonomous OZ smart-account agent.

        Modes (selected by environment gates):
          AGENT_PRINT_KEY=true   Print the agent Ed25519 identity (keygen bootstrap).
                                 Optionally set AGENT_SECRET_SEED=<64-hex> to derive
                                 the public key for a seed you already hold.
          AGENT_RUN_LIVE=true    Run one full agent cycle. Requires a complete live
                                 config: AGENT_SMART_ACCOUNT, AGENT_SECRET_SEED,
                                 AGENT_DESTINATION, AGENT_COORDINATION_URL,
                                 AGENT_COORDINATION_TOKEN.

        Without a gate this usage is printed and nothing else happens.
        """.trimIndent()
    )
}
