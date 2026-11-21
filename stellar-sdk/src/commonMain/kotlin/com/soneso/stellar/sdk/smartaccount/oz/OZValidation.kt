//
//  OZValidation.kt
//  Stellar SDK Kotlin Multiplatform
//
//  Copyright (c) 2026 Soneso. All rights reserved.
//

package com.soneso.stellar.sdk.smartaccount.oz

import com.soneso.stellar.sdk.Address
import com.soneso.stellar.sdk.StrKey
import com.soneso.stellar.sdk.smartaccount.core.DelegatedSigner
import com.soneso.stellar.sdk.smartaccount.core.ExternalSigner
import com.soneso.stellar.sdk.smartaccount.core.SignerScValFields
import com.soneso.stellar.sdk.smartaccount.core.SmartAccountAuthPayloadCodec
import com.soneso.stellar.sdk.smartaccount.core.SmartAccountSigner
import com.soneso.stellar.sdk.smartaccount.core.ValidationException
import com.soneso.stellar.sdk.xdr.SCAddressTypeXdr
import com.soneso.stellar.sdk.xdr.SCSymbolXdr
import com.soneso.stellar.sdk.xdr.SCValXdr
import com.soneso.stellar.sdk.xdr.SorobanAuthorizationEntryXdr
import com.soneso.stellar.sdk.xdr.SorobanAuthorizedFunctionXdr
import com.soneso.stellar.sdk.xdr.SorobanAuthorizedInvocationXdr

/**
 * Validates that the given string is a valid Stellar contract address (C...).
 * Uses full StrKey validation including CRC16 checksum.
 *
 * @param address The address string to validate.
 * @param fieldName The field name used in the error message.
 * @throws ValidationException.InvalidAddress if the address is not a valid contract address.
 */
internal fun requireContractAddress(address: String, fieldName: String) {
    if (!StrKey.isValidContract(address)) {
        throw ValidationException.invalidAddress(
            "$fieldName must be a valid contract address (C...), got: $address"
        )
    }
}

/**
 * Validates that the given string is a valid Stellar address (G... account or C... contract).
 * Uses full StrKey validation including CRC16 checksum.
 *
 * @param address The address string to validate.
 * @param fieldName The field name used in the error message.
 * @throws ValidationException.InvalidAddress if the address is not a valid Stellar address.
 */
/**
 * Checks if a URL is a valid localhost URL for development.
 *
 * Matches `http://localhost` exactly, or followed by `:` (port) or `/` (path).
 * Rejects URLs like `http://localhost.evil.com`.
 */
internal fun isLocalhostUrl(url: String): Boolean {
    if (!url.startsWith("http://localhost")) return false
    val suffix = url.removePrefix("http://localhost")
    return suffix.isEmpty() || suffix[0] == ':' || suffix[0] == '/'
}

internal fun requireStellarAddress(address: String, fieldName: String) {
    if (!StrKey.isValidEd25519PublicKey(address) && !StrKey.isValidContract(address)) {
        throw ValidationException.invalidAddress(
            "$fieldName must be a valid Stellar address (G... or C...), got: $address"
        )
    }
}

/**
 * Validates a context rule name: non-empty and within the OZ contract's
 * [OZConstants.MAX_NAME_SIZE]-byte (UTF-8) limit. Rejecting oversized names client-side
 * turns an opaque on-chain failure into a clear error before submission.
 *
 * @param name The context rule name to validate.
 * @throws ValidationException.InvalidInput if the name is empty or exceeds the byte limit.
 */
internal fun requireValidContextRuleName(name: String) {
    if (name.isEmpty()) {
        throw ValidationException.invalidInput("name", "Context rule name cannot be empty")
    }
    val byteLength = name.encodeToByteArray().size
    if (byteLength > OZConstants.MAX_NAME_SIZE) {
        throw ValidationException.invalidInput(
            "name",
            "Context rule name cannot exceed ${OZConstants.MAX_NAME_SIZE} bytes, got: $byteLength"
        )
    }
}

/**
 * Validates signers that are about to be added to the smart account [walletContractId].
 *
 * A Delegated signer must be a Stellar account (G-address) other than the shared default
 * deployer ([OZSmartAccountConfig.createDefaultDeployer]). A contract delegate can let anyone
 * authorize the account without a key (GHSA-jpg9-m7w4-c7wc), the wallet itself included, and
 * the default deployer's secret derives from a public string. An External signer's key data
 * must not exceed the OZ contract's [OZConstants.MAX_EXTERNAL_KEY_SIZE]-byte limit.
 *
 * Signers already on a wallet are not checked here; reading them never fails on these rules.
 *
 * @param signers The signers to validate.
 * @param walletContractId The smart account (C-address) the signers are added to.
 * @throws ValidationException.InvalidInput if a Delegated signer is the default deployer or
 *   the wallet itself, or if an external signer's key data is too large.
 * @throws ValidationException.InvalidAddress if a Delegated signer is not a G-address.
 */
internal suspend fun requireValidSigners(signers: List<SmartAccountSigner>, walletContractId: String) {
    val fields = signers.map { signer ->
        when (signer) {
            is DelegatedSigner -> SignerScValFields.Delegated(signer.address)
            is ExternalSigner -> SignerScValFields.External(signer.verifierAddress, signer.keyData)
        }
    }
    requireValidSignerFields(fields, walletContractId)
}

/**
 * The rules of [requireValidSigners] on signer fields, which carry no check of the typed
 * signers, plus the refusal of a muxed contract address (W...) as a Delegated address or an
 * External verifier, which only a raw signer value can carry.
 */
private suspend fun requireValidSignerFields(signers: List<SignerScValFields>, walletContractId: String) {
    var defaultDeployerAddress: String? = null
    for (signer in signers) {
        when (signer) {
            is SignerScValFields.Delegated -> {
                requireConvertibleSignerAddress(signer.address, "Delegated signer address")
                val deployerAddress = defaultDeployerAddress
                    ?: OZSmartAccountConfig.createDefaultDeployer().getAccountId().also { defaultDeployerAddress = it }
                requireValidDelegatedSigner(signer.address, walletContractId, deployerAddress)
            }
            is SignerScValFields.External -> {
                requireConvertibleSignerAddress(signer.verifierAddress, "External signer verifier address")
                if (signer.keyData.size > OZConstants.MAX_EXTERNAL_KEY_SIZE) {
                    throw ValidationException.invalidInput(
                        "keyData",
                        "External signer key data cannot exceed ${OZConstants.MAX_EXTERNAL_KEY_SIZE} bytes, " +
                            "got: ${signer.keyData.size}"
                    )
                }
            }
        }
    }
}

/**
 * Refuses a signer [address] of the muxed contract type (W...), which the smart-account-kit
 * cannot convert to a strkey and refuses; every other address type passes.
 */
private fun requireConvertibleSignerAddress(address: String, field: String) {
    if (Address(address).addressType == Address.AddressType.MUXED_CONTRACT) {
        throw ValidationException.invalidInput(
            "signer",
            "$field has an unsupported address type: ${SCAddressTypeXdr.SC_ADDRESS_TYPE_MUXED_CONTRACT.name}, got: $address"
        )
    }
}

private fun requireValidDelegatedSigner(address: String, walletContractId: String, defaultDeployerAddress: String) {
    if (address == defaultDeployerAddress) {
        throw ValidationException.invalidInput(
            "signer",
            "The shared default deployer $address cannot be a delegated signer: its secret derives " +
                "from a public string, so anyone could authorize the account through it"
        )
    }
    if (address == walletContractId) {
        throw ValidationException.invalidInput(
            "signer",
            "The smart account $walletContractId cannot be its own delegated signer"
        )
    }
    if (!StrKey.isValidEd25519PublicKey(address)) {
        throw ValidationException.invalidAddress(
            "A delegated signer must be a Stellar account (G-address), got: $address. " +
                "Delegating to a contract can let anyone authorize the account."
        )
    }
}

/**
 * Wallet functions that add signers, with the index of the argument that holds them and
 * whether that argument is a `Vec` of signers (stellar-accounts 0.8.0 signatures:
 * `add_signer(context_rule_id, signer)`, `batch_add_signer(context_rule_id, signers)`,
 * `add_context_rule(context_type, name, valid_until, signers, policies)`). The function name
 * of a call is looked up by its bytes, so a name that is not valid UTF-8 matches none.
 */
private val SIGNER_ADDING_FUNCTIONS: Map<SCSymbolXdr, Pair<Int, Boolean>> = mapOf(
    SCSymbolXdr("add_signer") to (1 to false),
    SCSymbolXdr("batch_add_signer") to (1 to true),
    SCSymbolXdr("add_context_rule") to (3 to true)
)

/**
 * Validates, with the rules of [requireValidSigners], every signer that an `add_signer`,
 * `batch_add_signer` or `add_context_rule` call on the smart account [walletContractId] adds,
 * anywhere in the invocation tree of [entry].
 *
 * The signing pipelines run this over each smart account entry before they ask any signer,
 * so a transaction built from raw contract calls cannot add a Delegated signer that
 * `OZSignerManager.addDelegated` or `OZContextRuleManager.addContextRule` refuses. The rules
 * apply to the fields of each signer value, without the checks of [DelegatedSigner] and
 * [ExternalSigner]: an External signer with empty key data or with a verifier that is no
 * contract passes, as the contract and the smart-account-kit accept it, while a muxed contract
 * address (W...) fails as the kit refuses it. A call without the signer argument is left to the
 * contract, which rejects it.
 *
 * @param entry The smart account authorization entry about to be signed.
 * @param walletContractId The smart account (C-address) whose entries are signed.
 * @throws ValidationException if an added signer fails [requireValidSigners], or if the
 *   signer list argument of `batch_add_signer` or `add_context_rule` is not a `Vec`.
 * @throws com.soneso.stellar.sdk.smartaccount.core.TransactionException.SigningFailed if an
 *   added signer is not a valid signer ScVal.
 */
internal suspend fun requireValidAddedSigners(entry: SorobanAuthorizationEntryXdr, walletContractId: String) {
    val pending = ArrayDeque<SorobanAuthorizedInvocationXdr>()
    pending.addLast(entry.rootInvocation)
    while (pending.isNotEmpty()) {
        val invocation = pending.removeFirst()
        pending.addAll(invocation.subInvocations)
        val contractFn = invocation.function as? SorobanAuthorizedFunctionXdr.ContractFn ?: continue
        val call = contractFn.value
        val (argumentIndex, isList) = SIGNER_ADDING_FUNCTIONS[call.functionName] ?: continue
        val functionName = call.functionName.value
        val target = try { Address.fromSCAddress(call.contractAddress).toString() } catch (_: Exception) { null }
        if (target != walletContractId) continue
        val argument = call.args.getOrNull(argumentIndex) ?: continue
        val signerValues = if (isList) {
            val vector = argument as? SCValXdr.Vec
                ?: throw ValidationException.invalidInput(
                    "signers",
                    "$functionName on $walletContractId carries ${argument.discriminant} at argument " +
                        "$argumentIndex, where a Vec of signers is expected"
                )
            vector.value?.value.orEmpty()
        } else {
            listOf(argument)
        }
        requireValidSignerFields(signerValues.map { SmartAccountAuthPayloadCodec.signerFieldsFromScVal(it) }, walletContractId)
    }
}

/**
 * Validates constructor / context-rule policies: at most [OZConstants.MAX_POLICIES] entries,
 * each keyed by a valid contract address (C...). The values are pre-encoded install-param
 * ScVals and are not inspected here.
 *
 * @param policies Policy install params keyed by policy contract address.
 * @throws ValidationException.InvalidInput if too many policies are supplied.
 * @throws ValidationException.InvalidAddress if a policy key is not a valid contract address.
 */
internal fun requireValidPolicies(policies: Map<String, SCValXdr>) {
    if (policies.size > OZConstants.MAX_POLICIES) {
        throw ValidationException.invalidInput(
            "policies",
            "Cannot install more than ${OZConstants.MAX_POLICIES} policies, got: ${policies.size}"
        )
    }
    for ((address, _) in policies) {
        requireContractAddress(address, "policyAddress")
    }
}
