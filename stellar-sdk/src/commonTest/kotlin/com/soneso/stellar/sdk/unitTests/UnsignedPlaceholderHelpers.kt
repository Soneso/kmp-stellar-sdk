package com.soneso.stellar.sdk.unitTests

import com.soneso.stellar.sdk.Address
import com.soneso.stellar.sdk.KeyPair
import com.soneso.stellar.sdk.Network
import com.soneso.stellar.sdk.Util
import com.soneso.stellar.sdk.requireAddressCredentials
import com.soneso.stellar.sdk.xdr.*
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Builds authorization entries with configurable signatures and verifies top-level signatures.
 * Shared by authorization, assembled transaction and SEP-45 unit tests.
 */

internal fun placeholderEntry(
    address: String,
    authV2: Boolean = false,
    signature: SCValXdr = SCValXdr.Vec(SCVecXdr(emptyList()))
): SorobanAuthorizationEntryXdr {
    val credentials = SorobanAddressCredentialsXdr(
        address = Address(address).toSCAddress(),
        nonce = Int64Xdr(123456789L),
        signatureExpirationLedger = Uint32Xdr(0u),
        signature = signature
    )
    return SorobanAuthorizationEntryXdr(
        credentials = if (authV2) SorobanCredentialsXdr.AddressV2(credentials)
            else SorobanCredentialsXdr.Address(credentials),
        rootInvocation = SorobanAuthorizedInvocationXdr(
            function = SorobanAuthorizedFunctionXdr.ContractFn(
                InvokeContractArgsXdr(
                    contractAddress = Address(AuthTest.CONTRACT_ID).toSCAddress(),
                    functionName = SCSymbolXdr("increment"),
                    args = emptyList()
                )
            ),
            subInvocations = emptyList()
        )
    )
}

/**
 * Verifies the sole top-level signature on [entry] against its arm-correct preimage for [network].
 */
internal suspend fun assertSingleSignatureVerifies(
    entry: SorobanAuthorizationEntryXdr,
    signer: KeyPair,
    network: Network = Network.TESTNET
) {
    val credentials = entry.credentials.requireAddressCredentials()
    val preimage = when (entry.credentials) {
        is SorobanCredentialsXdr.Address -> HashIDPreimageXdr.SorobanAuthorization(
            HashIDPreimageSorobanAuthorizationXdr(
                HashXdr(network.networkId()), credentials.nonce,
                credentials.signatureExpirationLedger, entry.rootInvocation
            )
        )
        is SorobanCredentialsXdr.AddressV2,
        is SorobanCredentialsXdr.AddressWithDelegates -> HashIDPreimageXdr.SorobanAuthorizationWithAddress(
            HashIDPreimageSorobanAuthorizationWithAddressXdr(
                HashXdr(network.networkId()), credentials.nonce,
                credentials.signatureExpirationLedger, credentials.address, entry.rootInvocation
            )
        )
        is SorobanCredentialsXdr.Void -> error("Source-account credentials cannot carry a signature")
    }
    val signature = assertIs<SCValXdr.Vec>(credentials.signature)
    val elements = assertNotNull(signature.value).value
    assertEquals(1, elements.size)
    val fields = assertNotNull(assertIs<SCValXdr.Map>(elements.single()).value).value.associate {
        assertIs<SCValXdr.Sym>(it.key).value.value to assertIs<SCValXdr.Bytes>(it.`val`).value.value
    }
    assertContentEquals(signer.getPublicKey(), fields.getValue("public_key"))
    val writer = XdrWriter()
    preimage.encode(writer)
    assertTrue(
        signer.verify(Util.hash(writer.toByteArray()), fields.getValue("signature")),
        "Signature must verify against the arm-correct preimage"
    )
}
