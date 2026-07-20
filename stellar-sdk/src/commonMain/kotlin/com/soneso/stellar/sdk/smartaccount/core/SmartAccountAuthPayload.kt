//
//  SmartAccountAuthPayload.kt
//  Stellar SDK Kotlin Multiplatform
//
//  Copyright © 2026 Soneso. All rights reserved.
//

package com.soneso.stellar.sdk.smartaccount.core

import com.soneso.stellar.sdk.Address
import com.soneso.stellar.sdk.scval.Scv
import com.soneso.stellar.sdk.xdr.SCMapEntryXdr
import com.soneso.stellar.sdk.xdr.SCValXdr

/**
 * Represents the v0.7.0 AuthPayload format used by the OZ smart account contract.
 *
 * In v0.7.0, the signature payload changed from a Vec-based tuple struct to a Map-based
 * named struct with two fields: `context_rule_ids` and `signers`.
 *
 * Contract representation:
 * ```
 * ScVal::Map([
 *   { key: Symbol("context_rule_ids"), val: Vec([U32(id), ...]) },
 *   { key: Symbol("signers"),          val: Map([{ key: signer.toScVal(), val: Bytes(sig) }, ...]) }
 * ])
 * ```
 *
 * @property signers Mutable map from signer to their double-XDR-encoded signature bytes.
 * @property contextRuleIds The context rule IDs bound into the signing digest.
 */
data class SmartAccountAuthPayload(
    val signers: MutableMap<SmartAccountSigner, ByteArray>,
    val contextRuleIds: List<UInt>
)

/**
 * Codec for reading and writing [SmartAccountAuthPayload] to/from [SCValXdr].
 *
 * Handles the v0.7.0 OZ smart account contract AuthPayload format, which is a
 * named struct (Map-based) with fields `context_rule_ids` and `signers`.
 */
object SmartAccountAuthPayloadCodec {

    /**
     * Reads an [SmartAccountAuthPayload] from its [SCValXdr] representation.
     *
     * Accepts `SCValXdr.Void` (returns empty payload) or `SCValXdr.Map` (full payload).
     *
     * @param signatureScVal The ScVal stored in the authorization entry credentials signature field.
     * @return The decoded [SmartAccountAuthPayload].
     * @throws TransactionException.SigningFailed if the ScVal is not Void or Map.
     */
    fun read(signatureScVal: SCValXdr): SmartAccountAuthPayload {
        if (signatureScVal is SCValXdr.Void) {
            return SmartAccountAuthPayload(
                signers = mutableMapOf(),
                contextRuleIds = emptyList()
            )
        }

        if (signatureScVal !is SCValXdr.Map) {
            throw TransactionException.signingFailed(
                "Smart account auth signature is not encoded as AuthPayload"
            )
        }

        val mapEntries = signatureScVal.value?.value ?: emptyList()

        var contextRuleIds: List<UInt> = emptyList()
        val signers: MutableMap<SmartAccountSigner, ByteArray> = mutableMapOf()

        for (entry in mapEntries) {
            val key = entry.key
            if (key !is SCValXdr.Sym) continue

            when (key.value.value) {
                "context_rule_ids" -> {
                    val vec = entry.`val`
                    if (vec is SCValXdr.Vec) {
                        contextRuleIds = vec.value?.value?.mapNotNull { element ->
                            if (element is SCValXdr.U32) element.value.value else null
                        } ?: emptyList()
                    }
                }
                "signers" -> {
                    val signersMap = entry.`val`
                    if (signersMap is SCValXdr.Map) {
                        for (signerEntry in signersMap.value?.value ?: emptyList()) {
                            val signer = signerFromScVal(signerEntry.key)
                            val sigBytes = when (val sigVal = signerEntry.`val`) {
                                is SCValXdr.Bytes -> sigVal.value.value
                                else -> throw TransactionException.signingFailed(
                                    "Signer signature value is not encoded as Bytes in AuthPayload"
                                )
                            }
                            signers[signer] = sigBytes
                        }
                    }
                }
            }
        }

        return SmartAccountAuthPayload(
            signers = signers,
            contextRuleIds = contextRuleIds
        )
    }

    /**
     * Writes an [SmartAccountAuthPayload] to its [SCValXdr] representation.
     *
     * Builds the Map with exactly two entries (order: `context_rule_ids`, then `signers`).
     * Signer entries within the signers map are sorted in the Soroban host's ScMap key
     * order (semantic content order), matching how the contract materializes the map.
     *
     * @param payload The payload to encode.
     * @return The [SCValXdr] representation of the payload.
     */
    fun write(payload: SmartAccountAuthPayload): SCValXdr {
        // Build signer map entries, wrapping raw bytes in Scv.toBytes()
        val signerEntries = payload.signers.map { (signer, sigBytes) ->
            SCMapEntryXdr(key = signer.toScVal(), `val` = Scv.toBytes(sigBytes))
        }

        // Sort signer entries in the Soroban host's ScMap key order (semantic content
        // order, not the length-major XDR-byte order), matching how the contract
        // materializes the signers map.
        val sortedSignerEntries = signerEntries.sortedWith { x, y ->
            compareScValHostOrder(x.key, y.key)
        }

        val signersMapScVal = Scv.toMap(linkedMapOf<SCValXdr, SCValXdr>().apply {
            sortedSignerEntries.forEach { e -> put(e.key, e.`val`) }
        })

        // context_rule_ids sorts before signers lexicographically, so order is correct
        val contextRuleIdsScVal = Scv.toVec(payload.contextRuleIds.map { id ->
            Scv.toUint32(id)
        })

        val outerMap = linkedMapOf<SCValXdr, SCValXdr>(
            Scv.toSymbol("context_rule_ids") to contextRuleIdsScVal,
            Scv.toSymbol("signers") to signersMapScVal
        )

        return Scv.toMap(outerMap)
    }

    /**
     * Upserts a signer entry in the payload.
     *
     * If a signer matching [signer] already exists (using [SmartAccountBuilders.signersEqual]),
     * the old entry is removed before adding the new one.
     *
     * @param payload The payload to update (mutated in place).
     * @param signer The signer to add or replace.
     * @param signatureBytes The double-XDR-encoded signature bytes to associate with the signer.
     */
    fun upsertSigner(
        payload: SmartAccountAuthPayload,
        signer: SmartAccountSigner,
        signatureBytes: ByteArray
    ) {
        // Remove existing entry for this signer if present
        val existingKey = payload.signers.keys.find { existing ->
            SmartAccountBuilders.signersEqual(existing, signer)
        }
        if (existingKey != null) {
            payload.signers.remove(existingKey)
        }
        payload.signers[signer] = signatureBytes
    }

    /**
     * Parses a [SmartAccountSigner] from its [SCValXdr] representation.
     *
     * Supported formats:
     * - `Vec([Symbol("Delegated"), Address(...)])` -> [DelegatedSigner]
     * - `Vec([Symbol("External"), Address(...), Bytes(...)])` -> [ExternalSigner]
     *
     * @param scVal The ScVal to parse.
     * @return The parsed [SmartAccountSigner].
     * @throws TransactionException.SigningFailed if the ScVal format is unrecognized.
     */
    fun signerFromScVal(scVal: SCValXdr): SmartAccountSigner {
        if (scVal !is SCValXdr.Vec) {
            throw TransactionException.signingFailed(
                "Signer ScVal is not a Vec"
            )
        }

        val elements = scVal.value?.value
            ?: throw TransactionException.signingFailed("Signer ScVal Vec is null or empty")

        if (elements.isEmpty()) {
            throw TransactionException.signingFailed("Signer ScVal Vec is empty")
        }

        val typeTag = elements[0]
        if (typeTag !is SCValXdr.Sym) {
            throw TransactionException.signingFailed(
                "First element of signer Vec is not a Symbol"
            )
        }

        return when (typeTag.value.value) {
            "Delegated" -> {
                if (elements.size < 2) {
                    throw TransactionException.signingFailed(
                        "Delegated signer Vec must have at least 2 elements"
                    )
                }
                val addressScVal = elements[1]
                if (addressScVal !is SCValXdr.Address) {
                    throw TransactionException.signingFailed(
                        "Delegated signer second element is not an Address"
                    )
                }
                val addressStr = Address.fromSCAddress(addressScVal.value).getEncodedAddress()
                DelegatedSigner(addressStr)
            }
            "External" -> {
                if (elements.size < 3) {
                    throw TransactionException.signingFailed(
                        "External signer Vec must have at least 3 elements"
                    )
                }
                val addressScVal = elements[1]
                if (addressScVal !is SCValXdr.Address) {
                    throw TransactionException.signingFailed(
                        "External signer second element is not an Address"
                    )
                }
                val verifierAddressStr = Address.fromSCAddress(addressScVal.value).getEncodedAddress()

                val keyDataScVal = elements[2]
                if (keyDataScVal !is SCValXdr.Bytes) {
                    throw TransactionException.signingFailed(
                        "External signer third element is not Bytes"
                    )
                }
                val keyData = keyDataScVal.value.value
                ExternalSigner(verifierAddressStr, keyData)
            }
            else -> throw TransactionException.signingFailed(
                "Unknown signer type tag: '${typeTag.value.value}'"
            )
        }
    }
}
