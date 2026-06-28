package com.soneso.smartdemo.agent

import com.soneso.stellar.sdk.KeyPair
import com.soneso.stellar.sdk.smartaccount.oz.InMemoryStorageAdapter
import com.soneso.stellar.sdk.smartaccount.oz.OZMultiSignerManager
import com.soneso.stellar.sdk.smartaccount.oz.OZSmartAccountConfig
import com.soneso.stellar.sdk.smartaccount.oz.OZSmartAccountKit
import com.soneso.stellar.sdk.smartaccount.oz.SelectedSigner
import com.soneso.stellar.sdk.smartaccount.oz.TransactionResult
import com.soneso.stellar.sdk.xdr.SCValXdr
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO

/**
 * Production [WalletSession] that connects an [OZSmartAccountKit] headlessly.
 *
 * Uses the contract-address-only `OZWalletOperations.connectToContract` path: no
 * passkey credential, no WebAuthn ceremony, no session restore. The agent
 * operates the account through the multi-signer / external-signer pipeline.
 */
class KitWalletSession(
    private val kit: OZSmartAccountKit,
    private val contractId: String,
) : WalletSession {
    override suspend fun connect(): String =
        kit.walletOperations.connectToContract(contractId)
}

/** Production [MultiSignerContractCall] backed by [OZMultiSignerManager]. */
class MultiSignerContractCallAdapter(
    private val manager: OZMultiSignerManager,
) : MultiSignerContractCall {
    override suspend fun multiSignerContractCall(
        target: String,
        targetFn: String,
        targetArgs: List<SCValXdr>,
        selectedSigners: List<SelectedSigner>,
    ): TransactionResult = manager.multiSignerContractCall(
        target = target,
        targetFn = targetFn,
        targetArgs = targetArgs,
        selectedSigners = selectedSigners,
    )
}

/**
 * Production assembly of the reference agent.
 *
 * Wires an [OZSmartAccountKit] (in-memory storage, no WebAuthn provider, the
 * agent's [AgentEd25519Adapter] supplied as the Ed25519 adapter), an
 * [HttpCoordinationClient], and an [AgentRunner]. Owns the kit and the HTTP
 * client; call [dispose] when finished.
 */
class Agent private constructor(
    val runner: AgentRunner,
    private val kit: OZSmartAccountKit,
    private val httpClient: HttpClient,
) {
    /** Runs one agent cycle. */
    suspend fun run(): AgentResult = runner.run()

    /** Releases the HTTP client and the kit's held resources. */
    fun dispose() {
        httpClient.close()
        kit.close()
    }

    companion object {
        /**
         * Builds a fully wired agent from [config].
         *
         * Throws [AgentConfigException] when [config] is missing a value required
         * for a live run, or a `SmartAccountException` when the kit configuration
         * is rejected.
         */
        suspend fun fromConfig(
            config: AgentConfig,
            logger: AgentLogger = StdoutAgentLogger(),
        ): Agent {
            config.validateForLiveRun()

            // validateForLiveRun guarantees these are present and well-formed.
            val seedHex = config.agentSecretSeed!!
            val seedBytes = Hex.decode(seedHex.lowercase())
                ?: throw AgentConfigException("agentSecretSeed is not valid hex.")
            val agentKeypair = KeyPair.fromSecretSeed(seedBytes)
            val publicKey = agentKeypair.getPublicKey()
            val signerAdapter = AgentEd25519Adapter()

            val ozConfig = OZSmartAccountConfig(
                rpcUrl = config.rpcUrl,
                networkPassphrase = config.networkPassphrase,
                accountWasmHash = config.accountWasmHash,
                webauthnVerifierAddress = config.webauthnVerifierAddress,
                relayerUrl = config.relayerUrl.ifEmpty { null },
                storage = InMemoryStorageAdapter(),
                externalEd25519Adapter = signerAdapter,
            )
            val kit = OZSmartAccountKit.create(ozConfig)

            val httpClient = HttpClient(CIO)
            val coordination = HttpCoordinationClient(
                baseUrl = config.coordinationBaseUrl,
                token = config.coordinationToken,
                client = httpClient,
            )

            val runner = AgentRunner(
                config = config,
                session = KitWalletSession(kit, config.smartAccountContractId!!),
                contractCall = MultiSignerContractCallAdapter(kit.multiSignerManager),
                coordination = coordination,
                signerAdapter = signerAdapter,
                agentPublicKey = publicKey,
                agentSeed = seedBytes,
                logger = logger,
            )

            return Agent(runner, kit, httpClient)
        }
    }
}
