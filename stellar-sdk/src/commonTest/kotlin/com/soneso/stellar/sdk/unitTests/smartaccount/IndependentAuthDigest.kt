//
//  IndependentAuthDigest.kt
//  Stellar SDK Kotlin Multiplatform
//
//  Copyright (c) 2026 Soneso. All rights reserved.
//

package com.soneso.stellar.sdk.unitTests.smartaccount

import com.soneso.stellar.sdk.Address
import com.soneso.stellar.sdk.Util
import com.soneso.stellar.sdk.xdr.SCBytesXdr
import com.soneso.stellar.sdk.xdr.SCMapEntryXdr
import com.soneso.stellar.sdk.xdr.SCMapXdr
import com.soneso.stellar.sdk.xdr.SCSymbolXdr
import com.soneso.stellar.sdk.xdr.SCValXdr
import com.soneso.stellar.sdk.xdr.SCVecXdr
import com.soneso.stellar.sdk.xdr.Uint32Xdr
import com.soneso.stellar.sdk.xdr.XdrWriter

/**
 * The two stellar-accounts auth digests built from their definitions with raw XDR types, so a
 * pipeline test compares the digest a signer receives with a value the SDK's digest code did
 * not produce. The signature payload comes from the caller.
 */
internal object IndependentAuthDigest {

    /** `AuthDigestPreimage` of stellar-accounts 0.8.0: a Map with the Symbol keys in name order. */
    fun preimage(account: String, signaturePayload: ByteArray, contextRuleIds: List<UInt>): SCValXdr =
        SCValXdr.Map(
            SCMapXdr(
                listOf(
                    SCMapEntryXdr(SCValXdr.Sym(SCSymbolXdr("account")), SCValXdr.Address(Address(account).toSCAddress())),
                    SCMapEntryXdr(SCValXdr.Sym(SCSymbolXdr("context_rule_ids")), ruleIdsVec(contextRuleIds)),
                    SCMapEntryXdr(SCValXdr.Sym(SCSymbolXdr("signature_payload")), SCValXdr.Bytes(SCBytesXdr(signaturePayload)))
                )
            )
        )

    /** `SHA-256(XDR(AuthDigestPreimage))`, the stellar-accounts 0.8.0 digest. */
    suspend fun preimageDigest(account: String, signaturePayload: ByteArray, contextRuleIds: List<UInt>): ByteArray =
        Util.hash(xdr(preimage(account, signaturePayload, contextRuleIds)))

    /** `SHA-256(signature_payload ++ XDR(Vec<U32>))`, the stellar-accounts 0.7 digest. */
    suspend fun legacyDigest(signaturePayload: ByteArray, contextRuleIds: List<UInt>): ByteArray =
        Util.hash(signaturePayload + xdr(ruleIdsVec(contextRuleIds)))

    /** The XDR encoding of [value]. */
    fun xdr(value: SCValXdr): ByteArray {
        val writer = XdrWriter()
        value.encode(writer)
        return writer.toByteArray()
    }

    private fun ruleIdsVec(contextRuleIds: List<UInt>): SCValXdr =
        SCValXdr.Vec(SCVecXdr(contextRuleIds.map { SCValXdr.U32(Uint32Xdr(it)) }))
}
