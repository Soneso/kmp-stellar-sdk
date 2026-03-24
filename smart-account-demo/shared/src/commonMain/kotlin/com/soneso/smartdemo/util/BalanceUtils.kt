package com.soneso.smartdemo.util

import com.soneso.smartdemo.config.DemoConfig
import com.soneso.smartdemo.state.DemoState
import com.soneso.smartdemo.token.DemoTokenService
import com.soneso.stellar.sdk.AssetTypeNative
import com.soneso.stellar.sdk.Network
import com.soneso.stellar.sdk.contract.ContractClient
import com.soneso.stellar.sdk.rpc.SorobanServer
import com.soneso.stellar.sdk.xdr.SCValXdr

/**
 * Parses an i128 SCVal result from a token balance() call to a display string.
 *
 * DEMO uses 7 decimals (same as XLM), so [formatStroopsAsXlm] applies directly.
 * Balances that overflow Long (hi != 0) are returned as "0.0" — not expected in the demo.
 */
fun parseDemoBalance(scVal: SCValXdr): String {
    return when (scVal) {
        is SCValXdr.I128 -> {
            val hi = scVal.value.hi.value
            if (hi != 0L) return "0.0"
            formatStroopsAsXlm(scVal.value.lo.value.toLong())
        }
        else -> "0.0"
    }
}

/**
 * Fetches the XLM balance for [contractId] via the Stellar Asset Contract (SAC).
 *
 * Creates a [SorobanServer] connection, calls getSACBalance for the native asset,
 * and returns the formatted balance string (e.g. "9995.0"). Returns "0.0" if the
 * account has no balance entry.
 */
suspend fun fetchXlmBalance(contractId: String): String {
    val server = SorobanServer(DemoConfig.RPC_URL)
    val result = server.getSACBalance(
        contractId,
        AssetTypeNative,
        Network(DemoConfig.NETWORK_PASSPHRASE)
    )
    return if (result.balanceEntry != null) {
        formatStroopsAsXlm(result.balanceEntry!!.amount)
    } else {
        "0.0"
    }
}

/**
 * Fetches the DEMO token balance for [walletContractId].
 *
 * Creates a [ContractClient] for the DEMO token, invokes the balance() function
 * using the token admin account as the simulation source (always funded), and
 * returns the formatted balance string. Returns "0.0" if the DEMO contract is not
 * yet deployed or any other error occurs.
 */
suspend fun fetchDemoBalance(walletContractId: String): String {
    val demoTokenContractId = DemoState.demoTokenContractId ?: return "0.0"
    return try {
        val tokenClient = ContractClient.forContract(
            contractId = demoTokenContractId,
            rpcUrl = DemoConfig.RPC_URL,
            network = Network(DemoConfig.NETWORK_PASSPHRASE)
        )
        val sourceAccountId = DemoTokenService.getAdminAccountId()
        val balanceResult = tokenClient.invoke<SCValXdr>(
            functionName = "balance",
            arguments = mapOf("id" to walletContractId),
            source = sourceAccountId,
            signer = null
        )
        parseDemoBalance(balanceResult)
    } catch (e: Exception) {
        "0.0"
    }
}

/**
 * Refreshes both XLM and DEMO balances for [walletContractId] and updates [DemoState].
 *
 * Fetches XLM via SAC and DEMO via ContractClient concurrently. XLM errors propagate;
 * DEMO errors silently fall back to "0.0" (contract may not be deployed yet).
 */
suspend fun refreshAllBalances(walletContractId: String) {
    val xlmBalance = fetchXlmBalance(walletContractId)
    DemoState.updateBalance(xlmBalance)
    val demoBalance = fetchDemoBalance(walletContractId)
    DemoState.updateDemoTokenBalance(demoBalance)
}
