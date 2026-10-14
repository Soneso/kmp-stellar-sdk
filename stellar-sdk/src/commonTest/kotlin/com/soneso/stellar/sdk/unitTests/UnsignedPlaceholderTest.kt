package com.soneso.stellar.sdk.unitTests

import com.soneso.stellar.sdk.Auth
import com.soneso.stellar.sdk.DelegateDescriptor
import com.soneso.stellar.sdk.KeyPair
import com.soneso.stellar.sdk.Network
import com.soneso.stellar.sdk.committedExpiration
import com.soneso.stellar.sdk.requireAddressCredentials
import com.soneso.stellar.sdk.scval.Scv
import com.soneso.stellar.sdk.xdr.SCValTypeXdr
import com.soneso.stellar.sdk.xdr.SCValXdr
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class UnsignedPlaceholderTest {
    private suspend fun assertEmptyVectorSigns(authV2: Boolean) {
        val signer = KeyPair.fromSecretSeed(AuthTest.SECRET_SEED)
        val entry = placeholderEntry(signer.getAccountId(), authV2)
        val signed = Auth.authorizeEntry(entry, signer, 500, Network.TESTNET)

        assertEquals(entry.credentials.discriminant, signed.credentials.discriminant)
        assertEquals(500u, signed.credentials.requireAddressCredentials().signatureExpirationLedger.value)
        assertSingleSignatureVerifies(signed, signer)
    }

    @Test
    fun testAddressEmptyVectorSigns() = runTest { assertEmptyVectorSigns(authV2 = false) }

    @Test
    fun testAddressV2EmptyVectorSigns() = runTest { assertEmptyVectorSigns(authV2 = true) }

    @Test
    fun testEmptyVectorDelegateDoesNotCommitExpiration() = runTest {
        val signer = KeyPair.fromSecretSeed(AuthTest.SECRET_SEED)
        val entry = Auth.attachDelegates(
            placeholderEntry(signer.getAccountId(), signature = Scv.toVoid()),
            0,
            listOf(DelegateDescriptor(AuthTest.CREDENTIAL_ADDRESS, Scv.toVec(emptyList())))
        )
        assertNull(entry.credentials.committedExpiration())
    }

    private suspend fun assertPayloadCommitsExpiration(signature: SCValXdr) {
        val signer = KeyPair.fromSecretSeed(AuthTest.SECRET_SEED)
        for (authV2 in listOf(false, true)) {
            val entry = placeholderEntry(signer.getAccountId(), authV2, signature)
            val error = assertFailsWith<IllegalArgumentException> {
                Auth.authorizeEntry(entry, signer, 500, Network.TESTNET)
            }
            assertTrue(error.message.orEmpty().startsWith("Signature expiration ledger"))
        }
    }

    @Test
    fun testNonemptyVectorCommitsExpiration() = runTest {
        assertPayloadCommitsExpiration(Scv.toVec(listOf(Scv.toVoid())))
    }

    @Test
    fun testBytesCommitExpiration() = runTest {
        assertPayloadCommitsExpiration(Scv.toBytes(byteArrayOf(1)))
    }

    @Test
    fun testContractInstanceKeyCommitsExpiration() = runTest {
        assertPayloadCommitsExpiration(SCValXdr.Void(SCValTypeXdr.SCV_LEDGER_KEY_CONTRACT_INSTANCE))
    }
}
