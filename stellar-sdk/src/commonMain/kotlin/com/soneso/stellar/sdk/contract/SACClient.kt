package com.soneso.stellar.sdk.contract

import com.ionspin.kotlin.bignum.integer.BigInteger
import com.soneso.stellar.sdk.Address
import com.soneso.stellar.sdk.Asset
import com.soneso.stellar.sdk.KeyPair
import com.soneso.stellar.sdk.MuxedAccount
import com.soneso.stellar.sdk.MuxedContract
import com.soneso.stellar.sdk.Network
import com.soneso.stellar.sdk.StrKey
import com.soneso.stellar.sdk.Util
import com.soneso.stellar.sdk.rpc.SorobanServer
import com.soneso.stellar.sdk.rpc.responses.GetTransactionResponse
import com.soneso.stellar.sdk.scval.Scv
import com.soneso.stellar.sdk.xdr.SCSpecEntryXdr
import com.soneso.stellar.sdk.xdr.SCValTypeXdr
import com.soneso.stellar.sdk.xdr.SCValXdr
import com.soneso.stellar.sdk.xdr.XdrJson
import com.soneso.stellar.sdk.xdr.XdrReader
import com.soneso.stellar.sdk.xdr.XdrWriter
import kotlin.io.encoding.Base64
import kotlinx.serialization.json.JsonPrimitive

/**
 * A typed client for a Stellar Asset Contract (SAC), the built-in token contract of a classic
 * asset, the native asset (XLM) included.
 *
 * Every asset has one SAC per network, whose contract id derives from the asset and the network
 * ([Asset.getContractId]). A SAC runs no wasm, so the network holds no spec for it; this client
 * uses the spec embedded in [SACSpec]. Opening a client therefore sends no RPC request:
 * [forAsset] and [forContractId] open it with the keypair of the source account, and
 * [readOnlyForAsset] and [readOnlyForContractId] open it without a keypair.
 *
 * ## Base units
 *
 * Amounts are i128 values in base units. A SAC has 7 decimals, so one unit of the asset is
 * 10,000,000 base units, and one base unit is one stroop. [toBaseUnits] converts a decimal
 * amount such as `"12.5"`.
 *
 * ## Reads
 *
 * [balance], [decimals], [name], and [symbol] simulate the contract call and never sign, send,
 * or restore. A failed simulation throws
 * [com.soneso.stellar.sdk.contract.exception.SimulationFailedException] with the contract
 * error: `Error(Contract, #6)` for the balance of an account that does not exist and
 * `Error(Contract, #13)` for that of an account without a trustline to the asset. No read turns
 * an error into 0. [SorobanServer.getSACBalance] reads the balance entry of a contract (C...) holder with its
 * `authorized` and `clawback` flags straight from the ledger, without a simulation; [balance]
 * runs the contract's own `balance` function for an account (G...) or contract (C...) holder.
 *
 * ## Writes
 *
 * [transfer] builds a transfer, simulates it with the default options, and returns it unsigned;
 * with [ClientOptions.simulate] false, call [AssembledTransaction.simulate] before signing.
 * [transferAndSubmit] sends a transfer from the source account and waits for its result. Both take
 * [ClientOptions] for `baseFee`, `transactionTimeout`, `submitTimeout`, `useUpgradedAuth`,
 * `simulate`, and `restore`; the options keep this client's `sourceAccountKeyPair`, `contractId`,
 * `network`, and `rpcUrl`. A transfer whose `from` is an account other than the source needs that
 * account's authorization: it appears in [AssembledTransaction.needsNonInvokerSigningBy], and
 * [AssembledTransaction.signAuthEntries] signs it with that account's keypair.
 *
 * ## Read-only mode
 *
 * A read-only client uses [READ_ONLY_SOURCE_ACCOUNT_ID], the all-zero ed25519 public key that
 * no secret key produces, as its source. Its reads need no account on the network. It builds
 * and simulates a [transfer], but the transaction refuses to sign, submit, or restore, and
 * [transferAndSubmit] throws.
 *
 * The other functions of the contract, such as `approve` or `mint`, run through
 * `getContractClient().buildInvoke` ([ContractClient.buildInvoke]).
 *
 * ## Example
 * ```kotlin
 * val asset = Asset.createNonNativeAsset("USDC", issuerAccountId)
 *
 * val reader = SACClient.readOnlyForAsset(asset, Network.TESTNET, rpcUrl)
 * val balance = reader.balance(Address(holderAccountId))
 *
 * val sender = SACClient.forAsset(asset, senderKeyPair, Network.TESTNET, rpcUrl)
 * val response = sender.transferAndSubmit(Address(recipientAccountId), SACClient.toBaseUnits("12.5"))
 * ```
 *
 * @see ContractClient
 */
class SACClient private constructor(
    private val client: ContractClient,
    private val sourceAccountKeyPair: KeyPair,
    private val readOnly: Boolean
) {
    /**
     * The contract id (C...) of the Stellar Asset Contract.
     */
    val contractId: String = client.contractId

    /**
     * Returns the [ContractClient] this client calls the contract through. It carries the
     * embedded spec, so its Map-based [ContractClient.buildInvoke] and [ContractClient.invoke]
     * reach the functions this class has no method for.
     *
     * @return The wrapped [ContractClient]
     */
    fun getContractClient(): ContractClient = client

    /**
     * Returns the balance of [holder] in base units, from a simulation of the contract's
     * `balance` function.
     *
     * @param holder An account (G...) or contract (C...) address
     * @return The balance in base units
     * @throws IllegalArgumentException before any request if [holder] is a muxed account (M...)
     *   or muxed contract (W...) address, whose base address the message names, or a claimable
     *   balance or liquidity pool address
     * @throws com.soneso.stellar.sdk.contract.exception.SimulationFailedException if the
     *   simulation fails, such as `Error(Contract, #6)` for an account that does not exist and
     *   `Error(Contract, #13)` for an account without a trustline to the asset
     * @throws IllegalStateException if ledger entries the call needs are archived, or if the
     *   contract returns a value that is not an i128
     */
    suspend fun balance(holder: Address): BigInteger {
        if (holder.addressType == Address.AddressType.MUXED_ACCOUNT) {
            throw IllegalArgumentException(
                "The balance holder must be an account (G...) or contract (C...) address, got a muxed " +
                    "account (M...) address: $holder; query its base account ${MuxedAccount(holder.toString()).accountId}"
            )
        }
        if (holder.addressType == Address.AddressType.MUXED_CONTRACT) {
            throw IllegalArgumentException(
                "The balance holder must be an account (G...) or contract (C...) address, got a muxed " +
                    "contract (W...) address: $holder; query its base contract ${MuxedContract(holder.toString()).contractId}"
            )
        }
        return Scv.fromInt128(read("balance", mapOf("id" to holder), SCValTypeXdr.SCV_I128))
    }

    /**
     * Returns the number of decimals of the asset, from a simulation of the contract's
     * `decimals` function. A Stellar Asset Contract returns 7.
     *
     * @return The number of decimals
     * @throws com.soneso.stellar.sdk.contract.exception.SimulationFailedException if the simulation fails
     * @throws IllegalStateException if ledger entries the call needs are archived, or if the
     *   contract returns a value that is not a u32
     */
    suspend fun decimals(): UInt = Scv.fromUint32(read("decimals", emptyMap(), SCValTypeXdr.SCV_U32))

    /**
     * Returns the name of the asset, from a simulation of the contract's `name` function:
     * `CODE:ISSUER` for an issued asset and `native` for XLM.
     *
     * @return The name of the asset
     * @throws com.soneso.stellar.sdk.contract.exception.SimulationFailedException if the simulation fails
     * @throws IllegalStateException if ledger entries the call needs are archived, or if the
     *   contract returns a value that is not a string
     */
    suspend fun name(): String = Scv.fromString(read("name", emptyMap(), SCValTypeXdr.SCV_STRING))

    /**
     * Returns the symbol of the asset, from a simulation of the contract's `symbol` function:
     * the asset code for an issued asset and `native` for XLM.
     *
     * @return The symbol of the asset
     * @throws com.soneso.stellar.sdk.contract.exception.SimulationFailedException if the simulation fails
     * @throws IllegalStateException if ledger entries the call needs are archived, or if the
     *   contract returns a value that is not a string
     */
    suspend fun symbol(): String = Scv.fromString(read("symbol", emptyMap(), SCValTypeXdr.SCV_STRING))

    /**
     * Builds a transfer of [amount] base units from [from] to [to] and, unless
     * [ClientOptions.simulate] is false, simulates it. The transaction is returned unsigned and
     * unsent.
     *
     * With [from] as the source account, [AssembledTransaction.signAndSubmit] submits it. An
     * account [from] other than the source appears in
     * [AssembledTransaction.needsNonInvokerSigningBy]; that account signs its entry with
     * [AssembledTransaction.signAuthEntries] first. A contract [from] authorizes through its own
     * `__check_auth`, which [AssembledTransaction.signAuthEntries] does not sign for. On a
     * client with a keypair, a simulation that needs archived entries restored restores them
     * first when [ClientOptions.restore] is true; on a read-only client the transaction refuses
     * to restore, sign, or submit.
     *
     * @param from The sender: an account (G...) or contract (C...) address
     * @param to The recipient: an account (G...), muxed account (M...), contract (C...), or muxed
     *   contract (W...) address
     * @param amount The amount in base units, from 0 to 2^127 - 1
     * @param options The invocation options; they keep this client's `sourceAccountKeyPair`
     *   (compared by account id), `contractId`, `network`, and `rpcUrl`
     * @return The transaction, simulated unless [ClientOptions.simulate] is false
     * @throws IllegalArgumentException before any request if [amount] is out of range, if
     *   [options] change one of the four fields above (the message names it), or if [from] or
     *   [to] is an address of a kind the parameter does not take
     * @throws com.soneso.stellar.sdk.contract.exception.SimulationFailedException if the simulation fails
     * @throws com.soneso.stellar.sdk.contract.exception.RestorationFailureException if the
     *   restore of archived entries fails
     * @throws IllegalStateException if a read-only client's simulation needs archived entries
     *   restored and [ClientOptions.restore] is true
     */
    suspend fun transfer(
        from: Address,
        to: Address,
        amount: BigInteger,
        options: ClientOptions = defaultOptions()
    ): AssembledTransaction<SCValXdr> {
        require(amount >= BigInteger.ZERO && amount <= I128_MAX) {
            "The transfer amount must be from 0 to $I128_MAX base units, got $amount"
        }
        requireOwnOptions(options)
        return client.buildInvoke(
            functionName = "transfer",
            arguments = mapOf("from" to from, "to" to to, "amount" to amount),
            source = sourceAccountKeyPair.getAccountId(),
            signer = if (readOnly) null else sourceAccountKeyPair,
            parseResultXdrFn = null,
            options = options
        )
    }

    /**
     * Transfers [amount] base units from the source account to [to]: builds and simulates the
     * transfer as [transfer] does with the source account as `from`, signs it with the source
     * account's keypair, submits it, and waits for its result.
     *
     * @param to The recipient: an account (G...), muxed account (M...), contract (C...), or muxed
     *   contract (W...) address
     * @param amount The amount in base units, from 0 to 2^127 - 1
     * @param options The invocation options; they keep this client's `sourceAccountKeyPair`,
     *   `contractId`, `network`, and `rpcUrl`, and [ClientOptions.submitTimeout] bounds the wait
     * @return The response of the successful transaction
     * @throws IllegalStateException before any request if this client is read-only, and after
     *   the transaction succeeded if its transaction meta carries no return value
     * @throws IllegalArgumentException before any request for the inputs [transfer] rejects
     * @throws com.soneso.stellar.sdk.contract.exception.SimulationFailedException if the simulation fails
     * @throws com.soneso.stellar.sdk.contract.exception.RestorationFailureException if the
     *   restore of archived entries fails
     * @throws com.soneso.stellar.sdk.contract.exception.NotYetSimulatedException if
     *   [ClientOptions.simulate] is false
     * @throws com.soneso.stellar.sdk.contract.exception.ExpiredStateException if archived entries
     *   need a restore and [ClientOptions.restore] is false
     * @throws com.soneso.stellar.sdk.contract.exception.SendTransactionFailedException if the
     *   network does not accept the transaction
     * @throws com.soneso.stellar.sdk.contract.exception.TransactionStillPendingException if the
     *   transaction has no result when [ClientOptions.submitTimeout] ends
     * @throws com.soneso.stellar.sdk.contract.exception.TransactionFailedException if the
     *   transaction failed
     * @throws Exception the last `getTransaction` failure when every status request failed
     *   until [ClientOptions.submitTimeout] ended
     */
    suspend fun transferAndSubmit(
        to: Address,
        amount: BigInteger,
        options: ClientOptions = defaultOptions()
    ): GetTransactionResponse {
        check(!readOnly) {
            "transferAndSubmit needs the sending account's keypair, and this SACClient is read-only; " +
                "open the client with SACClient.forAsset() or SACClient.forContractId()"
        }
        val transaction = transfer(Address(sourceAccountKeyPair.getAccountId()), to, amount, options)
        transaction.signAndSubmit()
        return transaction.getTransactionResponse!!
    }

    /**
     * Closes the wrapped [ContractClient] and its server connection.
     */
    fun close() {
        client.close()
    }

    /**
     * Simulates [function] with [arguments] and returns its result, which must have the arm
     * [expected]. The simulation never restores, so archived entries throw.
     */
    private suspend fun read(function: String, arguments: Map<String, Any?>, expected: SCValTypeXdr): SCValXdr {
        val transaction = client.buildInvoke<SCValXdr>(
            functionName = function,
            arguments = arguments,
            source = sourceAccountKeyPair.getAccountId(),
            signer = null,
            parseResultXdrFn = null,
            options = ClientOptions(sourceAccountKeyPair, contractId, client.network, client.rpcUrl, restore = false)
        )
        check(transaction.simulation!!.restorePreamble == null) {
            "The Stellar Asset Contract $contractId cannot run $function: ledger entries it needs are " +
                "archived; restore them from a client opened with a funded account's keypair"
        }
        val result = transaction.result()
        check(result.discriminant == expected) {
            "The Stellar Asset Contract $contractId returned ${result.discriminant.name} from $function, " +
                "expected ${expected.name}"
        }
        return result
    }

    /** The options the writes default to: this client's source, contract, network, and RPC URL. */
    private fun defaultOptions(): ClientOptions =
        ClientOptions(sourceAccountKeyPair, contractId, client.network, client.rpcUrl)

    /**
     * Requires [options] to keep the four fields [ContractClient.buildInvoke] does not read, so
     * that options never name another account, contract, network, or server than the ones the
     * transaction uses. The message names the field and not its value, since an RPC URL can
     * carry an access key.
     */
    private fun requireOwnOptions(options: ClientOptions) {
        val field = when {
            // KeyPair.equals also compares whether a secret key is present.
            options.sourceAccountKeyPair.getAccountId() != sourceAccountKeyPair.getAccountId() -> "sourceAccountKeyPair"
            options.contractId != contractId -> "contractId"
            options.network != client.network -> "network"
            options.rpcUrl != client.rpcUrl -> "rpcUrl"
            else -> return
        }
        throw IllegalArgumentException(
            "The options must keep this SACClient's sourceAccountKeyPair, contractId, network, and rpcUrl; " +
                "got a different $field"
        )
    }

    companion object {
        /**
         * The source account of the read-only clients: the all-zero ed25519 public key. No
         * secret key produces it, and a simulation needs no account entry for its source.
         */
        const val READ_ONLY_SOURCE_ACCOUNT_ID: String = "GAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAWHF"

        private const val SAC_DECIMALS = 7

        private val I128_MAX: BigInteger = BigInteger.TWO.pow(127) - BigInteger.ONE

        private val AMOUNT_PATTERN = Regex("[0-9]+(\\.[0-9]{1,$SAC_DECIMALS})?")

        /**
         * Opens the Stellar Asset Contract of [asset] on [network] without an RPC request.
         *
         * @param asset The asset, the native asset included
         * @param sourceAccountKeyPair The keypair of the source account of every call; the writes
         *   sign with it
         * @param network The network
         * @param rpcUrl The Soroban RPC URL
         * @return The client
         * @throws IllegalStateException if the embedded spec does not decode
         */
        suspend fun forAsset(
            asset: Asset,
            sourceAccountKeyPair: KeyPair,
            network: Network,
            rpcUrl: String
        ): SACClient = forServer(asset.getContractId(network), sourceAccountKeyPair, network, rpcUrl, SorobanServer(rpcUrl))

        /**
         * Opens the Stellar Asset Contract [contractId] without an RPC request. The id is not
         * checked against the network, so the client does not confirm that the contract is a
         * Stellar Asset Contract; it calls the contract with this id using the Stellar Asset
         * Contract spec.
         *
         * @param contractId The contract id (C...)
         * @param sourceAccountKeyPair The keypair of the source account of every call; the writes
         *   sign with it
         * @param network The network
         * @param rpcUrl The Soroban RPC URL
         * @return The client
         * @throws IllegalArgumentException if [contractId] is not a contract (C...) address; the
         *   message names the kind given, and a secret seed by its kind only
         * @throws IllegalStateException if the embedded spec does not decode
         */
        suspend fun forContractId(
            contractId: String,
            sourceAccountKeyPair: KeyPair,
            network: Network,
            rpcUrl: String
        ): SACClient = forServer(contractId, sourceAccountKeyPair, network, rpcUrl, SorobanServer(rpcUrl))

        /**
         * Opens the Stellar Asset Contract of [asset] on [network] as a read-only client, with
         * [READ_ONLY_SOURCE_ACCOUNT_ID] as the source, without an RPC request.
         *
         * @param asset The asset, the native asset included
         * @param network The network
         * @param rpcUrl The Soroban RPC URL
         * @return The read-only client
         * @throws IllegalStateException if the embedded spec does not decode
         */
        suspend fun readOnlyForAsset(asset: Asset, network: Network, rpcUrl: String): SACClient =
            forServer(asset.getContractId(network), null, network, rpcUrl, SorobanServer(rpcUrl))

        /**
         * Opens the Stellar Asset Contract [contractId] as a read-only client, with
         * [READ_ONLY_SOURCE_ACCOUNT_ID] as the source, without an RPC request.
         *
         * @param contractId The contract id (C...)
         * @param network The network
         * @param rpcUrl The Soroban RPC URL
         * @return The read-only client
         * @throws IllegalArgumentException if [contractId] is not a contract (C...) address; the
         *   message names the kind given, and a secret seed by its kind only
         * @throws IllegalStateException if the embedded spec does not decode
         */
        suspend fun readOnlyForContractId(contractId: String, network: Network, rpcUrl: String): SACClient =
            forServer(contractId, null, network, rpcUrl, SorobanServer(rpcUrl))

        /**
         * Decodes the embedded spec of the Stellar Asset Contract, [SACSpec.XDR_BASE64], after
         * checking its digest against [SACSpec.SHA256]. Each call returns a new list.
         *
         * @return The function and event entries of the spec, in their upstream order
         * @throws IllegalStateException if the constant is not valid base64, its digest differs,
         *   or an entry does not decode to the exact bytes it was read from
         */
        suspend fun specEntries(): List<SCSpecEntryXdr> = decodeSpec(SACSpec.XDR_BASE64, SACSpec.SHA256)

        /**
         * Converts a decimal amount of the asset to base units: `"12.5"` gives 125000000.
         *
         * The amount is decimal digits, optionally followed by a point and 1 to 7 fractional
         * digits: no sign, exponent, grouping, or whitespace. Zero is accepted, nothing is
         * rounded, and the largest amount is 17014118346046923173168730371588.4105727, which is
         * 2^127 - 1 base units, the largest i128. The exception for a secret seed, also one with
         * whitespace around it, names its kind only.
         *
         * @param amount The decimal amount
         * @return The amount in base units
         * @throws IllegalArgumentException if [amount] is a secret seed (named by its kind
         *   only), does not match the format above (the message quotes it, escaped and
         *   truncated), or exceeds the largest amount
         */
        fun toBaseUnits(amount: String): BigInteger {
            // The trim keeps a seed with surrounding whitespace, such as a pasted line break, out
            // of the message that quotes a malformed amount.
            if (StrKey.isValidEd25519SecretSeed(amount.trim().toCharArray())) {
                throw IllegalArgumentException("Invalid amount: got a secret seed (S...) instead of a decimal amount")
            }
            require(AMOUNT_PATTERN.matches(amount)) {
                "Invalid amount ${XdrJson.preview(JsonPrimitive(amount))}: expected decimal digits with at " +
                    "most $SAC_DECIMALS fractional digits, such as 12.5"
            }
            val fraction = amount.substringAfter('.', "").padEnd(SAC_DECIMALS, '0')
            val baseUnits = BigInteger.parseString(amount.substringBefore('.') + fraction, 10)
            require(baseUnits <= I128_MAX) {
                "Amount $amount exceeds the largest i128 amount, 17014118346046923173168730371588.4105727"
            }
            return baseUnits
        }

        /**
         * Opens [contractId] with the embedded spec on [server]; a null [sourceAccountKeyPair]
         * gives the read-only client. Every factory goes through it, and unit tests inject a
         * mocked server here. The server is closed when the client cannot be opened.
         */
        internal suspend fun forServer(
            contractId: String,
            sourceAccountKeyPair: KeyPair?,
            network: Network,
            rpcUrl: String,
            server: SorobanServer
        ): SACClient {
            val readOnly = sourceAccountKeyPair == null
            val client = try {
                ContractClient.forSpecEntries(contractId, rpcUrl, network, specEntries(), readOnly, server)
            } catch (failure: Throwable) {
                server.close()
                throw failure
            }
            return SACClient(client, sourceAccountKeyPair ?: KeyPair.fromAccountId(READ_ONLY_SOURCE_ACCOUNT_ID), readOnly)
        }

        /**
         * Decodes [base64] into spec entries after checking that the bytes have the lowercase
         * hex SHA-256 digest [sha256Hex]. Every entry must re-encode to the bytes it was read
         * from, so trailing or altered bytes fail instead of ending the list early.
         *
         * @throws IllegalStateException if [base64] is not valid base64, the digest differs, or
         *   an entry does not decode or re-encodes to different bytes; the message names the
         *   byte offset of the entry
         */
        internal suspend fun decodeSpec(base64: String, sha256Hex: String): List<SCSpecEntryXdr> {
            val bytes = try {
                Base64.Default.decode(base64)
            } catch (e: IllegalArgumentException) {
                throw IllegalStateException("The embedded SAC spec is not valid base64", e)
            }
            val actual = Util.bytesToHex(Util.hash(bytes))
            check(actual == sha256Hex) { "The embedded SAC spec has sha256 $actual, expected $sha256Hex" }

            val entries = mutableListOf<SCSpecEntryXdr>()
            var offset = 0
            while (offset < bytes.size) {
                val entry = try {
                    // XdrReader has no position accessor, so each entry is read from a copy of the rest.
                    SCSpecEntryXdr.decode(XdrReader(bytes.copyOfRange(offset, bytes.size)))
                } catch (e: Exception) {
                    throw IllegalStateException(
                        "The embedded SAC spec does not decode at byte offset $offset: ${e.message}",
                        e
                    )
                }
                val writer = XdrWriter()
                entry.encode(writer)
                val encoded = writer.toByteArray()
                // The readers skip string padding without checking it, so the re-encoding is the
                // check that the entry is the exact bytes it was read from.
                val read = bytes.copyOfRange(offset, minOf(offset + encoded.size, bytes.size))
                check(read.contentEquals(encoded)) {
                    "The embedded SAC spec does not decode at byte offset $offset: the entry re-encodes to different bytes"
                }
                entries.add(entry)
                offset += encoded.size
            }
            return entries
        }
    }
}
