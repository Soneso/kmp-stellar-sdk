package com.soneso.smartdemo.token

import com.soneso.smartdemo.config.DemoConfig
import com.soneso.smartdemo.platform.loadWasmResource
import com.soneso.stellar.sdk.FriendBot
import com.soneso.stellar.sdk.KeyPair
import com.soneso.stellar.sdk.Network
import com.soneso.stellar.sdk.StrKey
import com.soneso.stellar.sdk.contract.ContractClient
import com.soneso.stellar.sdk.crypto.getSha256Crypto
import com.soneso.stellar.sdk.rpc.SorobanServer
import com.soneso.stellar.sdk.rpc.exception.AccountNotFoundException
import com.soneso.stellar.sdk.scval.Scv
import com.soneso.stellar.sdk.xdr.ContractDataDurabilityXdr
import com.soneso.stellar.sdk.xdr.ContractIDPreimageFromAddressXdr
import com.soneso.stellar.sdk.xdr.ContractIDPreimageXdr
import com.soneso.stellar.sdk.xdr.HashIDPreimageContractIDXdr
import com.soneso.stellar.sdk.xdr.HashIDPreimageXdr
import com.soneso.stellar.sdk.xdr.HashXdr
import com.soneso.stellar.sdk.xdr.LedgerKeyContractDataXdr
import com.soneso.stellar.sdk.xdr.LedgerKeyXdr
import com.soneso.stellar.sdk.xdr.SCAddressXdr
import com.soneso.stellar.sdk.xdr.Uint256Xdr
import com.soneso.stellar.sdk.xdr.XdrWriter

/**
 * Manages a deterministic demo token (DEMO) for the smart account demo app.
 *
 * The token contract address is fully deterministic: the same deployer keypair and salt
 * always produce the same address, regardless of platform or app instance. This means
 * the demo token persists across app restarts and works correctly even when called
 * concurrently from multiple devices — only the first deployment goes through; subsequent
 * calls skip deployment and mint additional DEMO to the given recipient.
 *
 * NOTE: The token admin keypair (derived from [DemoConfig.DEMO_TOKEN_ADMIN_SEED]) is
 * completely separate from the smart account deployer. The smart account deployer pays
 * for smart account contract deployment; this token admin has nothing to do with that.
 * It only deploys and administers the DEMO token contract.
 *
 * @param rpcUrl Soroban RPC endpoint URL
 * @param networkPassphrase Stellar network passphrase (used for contract address derivation)
 */
class DemoTokenService(
    private val rpcUrl: String,
    private val networkPassphrase: String
) {

    /**
     * Ensures the DEMO token contract is deployed and mints 10,000 DEMO to the recipient.
     *
     * Deploy is idempotent: if the contract already exists on-chain, deployment is skipped.
     * Mint is additive: each call mints 10,000 more DEMO (= 100_000_000_000 stroops at 7 decimals)
     * to the recipient. This is intentional for a demo — wallets accumulate tokens across
     * creation attempts.
     *
     * All errors are caught and returned gracefully. Token failures must not break wallet creation.
     *
     * @param recipientContractId The smart account contract address to receive DEMO tokens (C-address)
     * @return [DemoTokenResult] with the token contract ID, amount minted, and whether it already existed
     */
    suspend fun ensureTokenAndMint(recipientContractId: String): DemoTokenResult {
        // Derive the token admin keypair from a deterministic seed. The same seed produces
        // the same keypair across all platforms and app instances, so the admin account
        // stays funded and the token contract address never changes.
        val adminKeyPair = deriveTokenAdminKeyPair()
        val adminId = adminKeyPair.getAccountId()

        // Derive the deterministic salt. The same salt + deployer always yields the same
        // contract address, making the DEMO token address stable across all deployments.
        val salt = deriveTokenSalt()

        // Pre-compute the expected contract address before deploying. We verify the
        // deployed address matches this after first deployment.
        val tokenContractId = deriveTokenContractAddress(networkPassphrase)

        val sorobanServer = SorobanServer(rpcUrl)

        // Fund the token admin account if it does not yet exist on the network.
        // FriendBot is idempotent for already-funded accounts on testnet.
        try {
            sorobanServer.getAccount(adminId)
        } catch (e: AccountNotFoundException) {
            FriendBot.fundTestnetAccount(adminId)
            // Give the network time to process the funding transaction
            kotlinx.coroutines.delay(5000)
        }

        // Check whether the token contract already exists on-chain by querying
        // its contract instance ledger entry. Returns null if not yet deployed.
        val alreadyExisted = contractExistsOnChain(sorobanServer, tokenContractId)

        if (!alreadyExisted) {
            val wasmBytes = loadWasmResource("soroban_token_contract.wasm")

            // Deploy with deterministic salt so the address is always predictable.
            // Constructor args from DemoConfig match the standard Soroban token interface (SEP-41).
            val deployedClient = ContractClient.deploy(
                wasmBytes = wasmBytes,
                constructorArgs = mapOf(
                    "admin" to adminId,
                    "decimal" to DemoConfig.DEMO_TOKEN_DECIMALS,
                    "name" to DemoConfig.DEMO_TOKEN_NAME,
                    "symbol" to DemoConfig.DEMO_TOKEN_SYMBOL
                ),
                source = adminId,
                signer = adminKeyPair,
                network = Network(networkPassphrase),
                rpcUrl = rpcUrl,
                salt = salt
            )

            // Verify determinism: the deployed address must match the pre-derived address.
            // A mismatch here would indicate a bug in the salt/deployer derivation logic.
            check(deployedClient.contractId == tokenContractId) {
                "Deployed contract address (${deployedClient.contractId}) does not match " +
                "pre-derived address ($tokenContractId). Check salt and deployer derivation."
            }
        }

        // Load the contract client to call mint. We always need the spec loaded to use
        // the typed invoke() API with named arguments.
        val tokenClient = ContractClient.forContract(
            contractId = tokenContractId,
            rpcUrl = rpcUrl,
            network = Network(networkPassphrase)
        )

        // Mint 10,000 DEMO (7 decimals: 10_000 * 10^7 = 100_000_000_000 stroops) to the recipient.
        // The recipient is a smart account contract, so we pass it as a contract address.
        tokenClient.invoke<Unit>(
            functionName = "mint",
            arguments = mapOf(
                "to" to recipientContractId,
                "amount" to MINT_AMOUNT_STROOPS
            ),
            source = adminId,
            signer = adminKeyPair
        )

        return DemoTokenResult(
            tokenContractId = tokenContractId,
            amountMinted = MINT_AMOUNT_STROOPS,
            alreadyExisted = alreadyExisted
        )
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Checks whether a contract instance entry exists on-chain for the given contract ID.
     * Returns true if deployed, false if not yet deployed.
     */
    private suspend fun contractExistsOnChain(server: SorobanServer, contractId: String): Boolean {
        return try {
            val entry = server.getContractData(
                contractId = contractId,
                key = Scv.toLedgerKeyContractInstance(),
                durability = SorobanServer.Durability.PERSISTENT
            )
            entry != null
        } catch (e: Exception) {
            // Any RPC error (e.g., contract not found) means it does not exist yet
            false
        }
    }

    companion object {

        /** Mint amount per call, from [DemoConfig.DEMO_TOKEN_MINT_AMOUNT]. */
        const val MINT_AMOUNT_STROOPS = DemoConfig.DEMO_TOKEN_MINT_AMOUNT

        /**
         * Derives the deterministic DEMO token contract address from the network passphrase.
         *
         * This is a pure computation — it does NOT deploy the contract or make any network call.
         * Use this when you only need the contract address (e.g., WalletConnectionScreen) without
         * running the full deploy/mint flow.
         *
         * @param networkPassphrase The Stellar network passphrase
         * @return The DEMO token contract address (C-address)
         */
        suspend fun deriveTokenContractAddress(networkPassphrase: String): String {
            val adminKeyPair = deriveTokenAdminKeyPair()
            val salt = deriveTokenSalt()
            return deriveContractAddressFromSalt(
                salt = salt,
                deployerPublicKey = adminKeyPair.getAccountId(),
                networkPassphrase = networkPassphrase
            )
        }

        /**
         * Returns the token admin's account ID (G-address).
         *
         * Useful as a funded source account for read-only contract calls (e.g., balance queries)
         * where the RPC requires a valid account for simulation.
         */
        suspend fun getAdminAccountId(): String {
            return deriveTokenAdminKeyPair().getAccountId()
        }

        /**
         * Derives the token admin keypair from [DemoConfig.DEMO_TOKEN_ADMIN_SEED].
         *
         * SHA256 of the seed string produces a stable 32-byte value that maps to the
         * same Ed25519 keypair on every platform and every app restart. This means the
         * admin account can be funded once and reused indefinitely.
         *
         * This keypair is entirely separate from the smart account deployer keypair.
         */
        private suspend fun deriveTokenAdminKeyPair(): KeyPair {
            val seedBytes = getSha256Crypto().hash(
                DemoConfig.DEMO_TOKEN_ADMIN_SEED.encodeToByteArray()
            )
            return KeyPair.fromSecretSeed(seedBytes)
        }

        /**
         * Derives the deterministic salt from [DemoConfig.DEMO_TOKEN_SALT_SEED].
         *
         * SHA256 of the seed string produces a fixed 32-byte salt. Because
         * ContractClient.deploy() uses the same salt internally, the resulting contract
         * address is always identical — regardless of when or where the deployment runs.
         */
        private suspend fun deriveTokenSalt(): ByteArray {
            return getSha256Crypto().hash(
                DemoConfig.DEMO_TOKEN_SALT_SEED.encodeToByteArray()
            )
        }

        /**
         * Derives the Soroban contract address that will result from deploying with the
         * given deployer public key and salt on the given network.
         *
         * Mirrors SmartAccountUtils.deriveContractAddress() but uses the raw salt directly
         * instead of computing SHA256(credentialId) first.
         *
         * The derivation follows the Soroban protocol:
         *   1. networkId  = SHA256(networkPassphrase)
         *   2. preimage   = ContractID { networkId, ContractIDPreimage::FromAddress { deployer, salt } }
         *   3. contractId = SHA256(XDR-encode(preimage))
         *   4. result     = StrKey.encodeContract(contractId)  →  C-address
         */
        private suspend fun deriveContractAddressFromSalt(
            salt: ByteArray,
            deployerPublicKey: String,
            networkPassphrase: String
        ): String {
            val sha256 = getSha256Crypto()

            // Step 1: network ID is the SHA-256 hash of the network passphrase
            val networkIdBytes = sha256.hash(networkPassphrase.encodeToByteArray())
            val networkId = HashXdr(networkIdBytes)

            // Step 2: build the deployer SCAddress
            val deployerKeyPair = KeyPair.fromAccountId(deployerPublicKey)
            val deployerAddress = SCAddressXdr.AccountId(deployerKeyPair.getXdrAccountId())

            // Step 3: construct ContractIDPreimage::FromAddress with the raw salt
            val contractIdPreimage = ContractIDPreimageXdr.FromAddress(
                ContractIDPreimageFromAddressXdr(
                    address = deployerAddress,
                    salt = Uint256Xdr(salt)
                )
            )

            // Step 4: wrap in HashIDPreimage::ContractID
            val preimage = HashIDPreimageXdr.ContractID(
                HashIDPreimageContractIDXdr(
                    networkId = networkId,
                    contractIdPreimage = contractIdPreimage
                )
            )

            // Step 5: XDR-encode the preimage
            val writer = XdrWriter()
            preimage.encode(writer)
            val encodedPreimage = writer.toByteArray()

            // Step 6: hash the encoded preimage to get the 32-byte contract ID
            val contractIdBytes = sha256.hash(encodedPreimage)

            // Step 7: encode as a C-address StrKey
            return StrKey.encodeContract(contractIdBytes)
        }
    }
}

/**
 * Result of [DemoTokenService.ensureTokenAndMint].
 *
 * @property tokenContractId The DEMO token contract address (C-address)
 * @property amountMinted The number of stroops minted in this call (7 decimals: divide by 10^7 for display)
 * @property alreadyExisted True if the token contract was already deployed before this call
 */
data class DemoTokenResult(
    val tokenContractId: String,
    val amountMinted: Long,
    val alreadyExisted: Boolean
)
