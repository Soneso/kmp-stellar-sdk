package com.soneso.smartdemo.util

import com.soneso.smartdemo.state.ActivityLogState
import com.soneso.smartdemo.state.DemoState
import com.soneso.stellar.sdk.Util
import com.soneso.stellar.sdk.smartaccount.core.DelegatedSigner
import com.soneso.stellar.sdk.smartaccount.core.ExternalSigner
import com.soneso.stellar.sdk.smartaccount.core.SmartAccountBuilders
import com.soneso.stellar.sdk.smartaccount.core.SmartAccountConstants
import com.soneso.stellar.sdk.smartaccount.core.SmartAccountSigner
import com.soneso.stellar.sdk.smartaccount.oz.OZExternalSignerManager
import com.soneso.stellar.sdk.smartaccount.oz.OZSmartAccountKit
import com.soneso.stellar.sdk.smartaccount.oz.ParsedContextRule

/**
 * Represents a signer extracted from context rules with its signing capability.
 *
 * @property signer The smart account signer (ExternalSigner or DelegatedSigner)
 * @property canSign Whether this signer can currently sign transactions
 */
data class SignerInfo(
    val signer: SmartAccountSigner,
    val canSign: Boolean
)

/**
 * Fetches all context rules from the connected smart account.
 *
 * Delegates to [OZContextRuleManager.listContextRules] which handles the count-then-fetch
 * iteration and full SDK-level parsing including [ParsedContextRule.signerIds] and
 * [ParsedContextRule.policyIds].
 *
 * @param kit The initialized [OZSmartAccountKit] instance.
 * @throws IllegalStateException if kit is not initialized.
 */
suspend fun fetchAllContextRules(kit: OZSmartAccountKit): List<ParsedContextRule> {
    return try {
        kit.contextRuleManager.listContextRules()
    } catch (e: Exception) {
        ActivityLogState.error("Failed to fetch context rules: ${e.message}")
        emptyList()
    }
}

/**
 * Convenience overload that reads the kit from [DemoState].
 *
 * @throws IllegalStateException if DemoState.kit is null.
 */
suspend fun fetchAllContextRules(): List<ParsedContextRule> {
    val kit = DemoState.kit ?: throw IllegalStateException("Kit not initialized")
    return fetchAllContextRules(kit)
}

/**
 * Extracts unique signers from a list of parsed context rules and determines
 * whether each signer can currently sign transactions.
 *
 * For [ExternalSigner] with WebAuthn key data (keyData > 32 bytes): canSign is true
 * when the signer's credential ID matches [connectedCredentialId].
 * For [ExternalSigner] with exactly 32 bytes of key data (Ed25519): canSign is true
 * when [externalSigners].canSignEd25519For(verifierAddress, publicKey) returns true.
 * For [DelegatedSigner]: canSign is true when [externalSigners].canSignFor(address) returns true.
 *
 * Both capability checks consult the kit-owned [OZExternalSignerManager], which covers the
 * in-memory keypair custody model and the config-injected adapter custody model for each kind.
 *
 * Signers are deduplicated across rules using [SmartAccountBuilders.collectUniqueSigners].
 *
 * @param rules Parsed context rules to extract signers from.
 * @param connectedCredentialId Base64URL-encoded credential ID of the connected passkey.
 * @param externalSigners The kit-owned external-signer manager for capability checks.
 * @return List of [SignerInfo] with canSign status for each unique signer.
 */
suspend fun extractSignersFromRules(
    rules: List<ParsedContextRule>,
    connectedCredentialId: String?,
    externalSigners: OZExternalSignerManager
): List<SignerInfo> {
    val allSigners = rules.flatMap { it.signers }
    val unique = SmartAccountBuilders.collectUniqueSigners(allSigners)

    return unique.map { signer ->
        val canSign = when (signer) {
            is ExternalSigner -> {
                val keyData = signer.keyData
                when {
                    keyData.size > SmartAccountConstants.SECP256R1_PUBLIC_KEY_SIZE -> {
                        // WebAuthn signer: compare credential ID suffix against connected credential
                        val credIdBytes = keyData.copyOfRange(
                            SmartAccountConstants.SECP256R1_PUBLIC_KEY_SIZE,
                            keyData.size
                        )
                        val credIdEncoded = Util.base64urlEncode(credIdBytes)
                        credIdEncoded == connectedCredentialId
                    }
                    keyData.size == SmartAccountConstants.ED25519_PUBLIC_KEY_SIZE -> {
                        // Ed25519 signer: check if the manager has a signing source registered
                        externalSigners.canSignEd25519For(signer.verifierAddress, keyData)
                    }
                    else -> false
                }
            }
            is DelegatedSigner -> {
                try {
                    externalSigners.canSignFor(signer.address)
                } catch (_: Exception) {
                    false
                }
            }
        }
        SignerInfo(signer = signer, canSign = canSign)
    }
}
