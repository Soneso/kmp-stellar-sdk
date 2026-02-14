package com.soneso.stellar.sdk.unitTests

import com.soneso.stellar.sdk.*
import com.soneso.stellar.sdk.scval.Scv
import com.soneso.stellar.sdk.xdr.*
import kotlinx.coroutines.test.runTest
import kotlin.test.*

/**
 * Tests for the Auth helper class for Soroban authorization signing.
 */
class AuthTest {

    companion object {
        // Test data - same values as Java SDK for cross-compatibility
        const val CONTRACT_ID = "CDCYWK73YTYFJZZSJ5V7EDFNHYBG4QN3VUNG2IGD27KJDDPNCZKBCBXK"
        const val SECRET_SEED = "SAEZSI6DY7AXJFIYA4PM6SIBNEYYXIEM2MSOTHFGKHDW32MBQ7KVO6EN"
        const val VALID_UNTIL_LEDGER_SEQ = 654656L
        const val NONCE = 123456789L
        val NETWORK = Network.TESTNET
        const val CREDENTIAL_ADDRESS = "GADBBY4WFXKKFJ7CMTG3J5YAUXMQDBILRQ6W3U5IWN5TQFZU4MWZ5T4K"
    }

    private fun createTestInvocation(): SorobanAuthorizedInvocationXdr {
        return SorobanAuthorizedInvocationXdr(
            function = SorobanAuthorizedFunctionXdr.ContractFn(
                InvokeContractArgsXdr(
                    contractAddress = Address(CONTRACT_ID).toSCAddress(),
                    functionName = SCSymbolXdr("increment"),
                    args = emptyList()
                )
            ),
            subInvocations = emptyList()
        )
    }

    private fun createUnsignedEntry(signerAccountId: String, nonce: Long = NONCE): SorobanAuthorizationEntryXdr {
        return SorobanAuthorizationEntryXdr(
            credentials = SorobanCredentialsXdr.Address(
                SorobanAddressCredentialsXdr(
                    address = Address(signerAccountId).toSCAddress(),
                    nonce = Int64Xdr(nonce),
                    signatureExpirationLedger = Uint32Xdr(0u),
                    signature = Scv.toVoid()
                )
            ),
            rootInvocation = createTestInvocation()
        )
    }

    private fun createSourceAccountEntry(): SorobanAuthorizationEntryXdr {
        return SorobanAuthorizationEntryXdr(
            credentials = SorobanCredentialsXdr.Void,
            rootInvocation = createTestInvocation()
        )
    }

    private suspend fun createExpectedPreimage(
        network: Network,
        nonce: Long,
        invocation: SorobanAuthorizedInvocationXdr,
        validUntilLedgerSeq: Long
    ): HashIDPreimageXdr {
        return HashIDPreimageXdr.SorobanAuthorization(
            HashIDPreimageSorobanAuthorizationXdr(
                networkId = HashXdr(network.networkId()),
                nonce = Int64Xdr(nonce),
                signatureExpirationLedger = Uint32Xdr(validUntilLedgerSeq.toUInt()),
                invocation = invocation
            )
        )
    }

    private suspend fun verifySignedEntry(
        signedEntry: SorobanAuthorizationEntryXdr,
        signer: KeyPair,
        expectedNonce: Long,
        expectedValidUntil: Long
    ) {
        assertTrue(signedEntry.credentials is SorobanCredentialsXdr.Address)
        val addressCreds = (signedEntry.credentials as SorobanCredentialsXdr.Address).value

        assertEquals(expectedNonce, addressCreds.nonce.value)
        assertEquals(expectedValidUntil.toUInt(), addressCreds.signatureExpirationLedger.value)

        assertTrue(addressCreds.signature is SCValXdr.Vec)
        val sigVec = (addressCreds.signature as SCValXdr.Vec).value
        assertNotNull(sigVec)
        assertEquals(1, sigVec.value.size)

        val firstElement = sigVec.value[0]
        assertTrue(firstElement is SCValXdr.Map)
        val sigMap = firstElement.value
        assertNotNull(sigMap)
        assertEquals(2, sigMap.value.size)

        val publicKeyEntry = sigMap.value.find {
            it.key is SCValXdr.Sym && (it.key as SCValXdr.Sym).value.value == "public_key"
        }
        assertNotNull(publicKeyEntry)
        assertTrue(publicKeyEntry.`val` is SCValXdr.Bytes)

        val signatureEntry = sigMap.value.find {
            it.key is SCValXdr.Sym && (it.key as SCValXdr.Sym).value.value == "signature"
        }
        assertNotNull(signatureEntry)
        assertTrue(signatureEntry.`val` is SCValXdr.Bytes)

        val publicKeyBytes = (publicKeyEntry.`val` as SCValXdr.Bytes).value.value
        assertContentEquals(signer.getPublicKey(), publicKeyBytes)

        val signatureBytes = (signatureEntry.`val` as SCValXdr.Bytes).value.value
        assertEquals(64, signatureBytes.size)
    }

    @Test
    fun testAuthorizeEntryWithXdrAndKeypair() = runTest {
        val signer = KeyPair.fromSecretSeed(SECRET_SEED)
        val entry = createUnsignedEntry(signer.getAccountId())

        val signedEntry = Auth.authorizeEntry(
            entry = entry,
            signer = signer,
            validUntilLedgerSeq = VALID_UNTIL_LEDGER_SEQ,
            network = NETWORK
        )

        verifySignedEntry(signedEntry, signer, NONCE, VALID_UNTIL_LEDGER_SEQ)
        assertNotSame(entry, signedEntry)
        assertTrue((entry.credentials as SorobanCredentialsXdr.Address).value.signature is SCValXdr.Void)
    }

    @Test
    fun testAuthorizeEntryWithBase64() = runTest {
        val signer = KeyPair.fromSecretSeed(SECRET_SEED)
        val entry = createUnsignedEntry(signer.getAccountId())
        val base64Entry = entry.toXdrBase64()

        val signedEntry = Auth.authorizeEntry(
            entry = base64Entry,
            signer = signer,
            validUntilLedgerSeq = VALID_UNTIL_LEDGER_SEQ,
            network = NETWORK
        )

        verifySignedEntry(signedEntry, signer, NONCE, VALID_UNTIL_LEDGER_SEQ)
    }

    @Test
    fun testAuthorizeEntryWithCustomSigner() = runTest {
        val signer = KeyPair.fromSecretSeed(SECRET_SEED)
        val entry = createUnsignedEntry(signer.getAccountId())

        val customSigner = Auth.Signer { preimage ->
            val writer = XdrWriter()
            preimage.encode(writer)
            val data = writer.toByteArray()
            val payload = Util.hash(data)
            val signature = signer.sign(payload)
            Auth.Signature(signer.getAccountId(), signature)
        }

        val signedEntry = Auth.authorizeEntry(
            entry = entry,
            signer = customSigner,
            validUntilLedgerSeq = VALID_UNTIL_LEDGER_SEQ,
            network = NETWORK
        )

        verifySignedEntry(signedEntry, signer, NONCE, VALID_UNTIL_LEDGER_SEQ)
    }

    @Test
    fun testAuthorizeInvocationWithKeypair() = runTest {
        val signer = KeyPair.fromSecretSeed(SECRET_SEED)
        val invocation = createTestInvocation()

        val signedEntry = Auth.authorizeInvocation(
            signer = signer,
            validUntilLedgerSeq = VALID_UNTIL_LEDGER_SEQ,
            invocation = invocation,
            network = NETWORK
        )

        assertTrue(signedEntry.credentials is SorobanCredentialsXdr.Address)
        val addressCreds = (signedEntry.credentials as SorobanCredentialsXdr.Address).value

        assertEquals(VALID_UNTIL_LEDGER_SEQ.toUInt(), addressCreds.signatureExpirationLedger.value)

        val address = Address.fromSCAddress(addressCreds.address)
        assertEquals(signer.getAccountId(), address.toString())

        assertTrue(addressCreds.signature is SCValXdr.Vec)
        val sigVec = (addressCreds.signature as SCValXdr.Vec).value
        assertNotNull(sigVec)
        assertEquals(1, sigVec.value.size)
    }

    @Test
    fun testSignatureVerificationPassesForValidSignatures() = runTest {
        val signer = KeyPair.fromSecretSeed(SECRET_SEED)
        val entry = createUnsignedEntry(signer.getAccountId())

        val signedEntry = Auth.authorizeEntry(
            entry = entry,
            signer = signer,
            validUntilLedgerSeq = VALID_UNTIL_LEDGER_SEQ,
            network = NETWORK
        )

        val addressCreds = (signedEntry.credentials as SorobanCredentialsXdr.Address).value
        val sigVec = (addressCreds.signature as SCValXdr.Vec).value!!.value
        val sigMap = (sigVec[0] as SCValXdr.Map).value!!.value

        val signatureEntry = sigMap.find {
            it.key is SCValXdr.Sym && (it.key as SCValXdr.Sym).value.value == "signature"
        }
        val signatureBytes = (signatureEntry!!.`val` as SCValXdr.Bytes).value.value

        val preimage = createExpectedPreimage(NETWORK, NONCE, createTestInvocation(), VALID_UNTIL_LEDGER_SEQ)
        val writer = XdrWriter()
        preimage.encode(writer)
        val payload = Util.hash(writer.toByteArray())

        assertTrue(signer.verify(payload, signatureBytes))
    }

    @Test
    fun testSignatureVerificationFailsForInvalidSignatures() = runTest {
        val signer = KeyPair.fromSecretSeed(SECRET_SEED)
        val entry = createUnsignedEntry(signer.getAccountId())

        val invalidSigner = Auth.Signer { _ ->
            val invalidData = ByteArray(20) { 0x42 }
            val signature = signer.sign(invalidData)
            Auth.Signature(signer.getAccountId(), signature)
        }

        assertFailsWith<IllegalArgumentException> {
            Auth.authorizeEntry(
                entry = entry,
                signer = invalidSigner,
                validUntilLedgerSeq = VALID_UNTIL_LEDGER_SEQ,
                network = NETWORK
            )
        }
    }

    @Test
    fun testSignatureStructureMatchesStellarSpec() = runTest {
        val signer = KeyPair.fromSecretSeed(SECRET_SEED)
        val entry = createUnsignedEntry(signer.getAccountId())

        val signedEntry = Auth.authorizeEntry(
            entry = entry,
            signer = signer,
            validUntilLedgerSeq = VALID_UNTIL_LEDGER_SEQ,
            network = NETWORK
        )

        val addressCreds = (signedEntry.credentials as SorobanCredentialsXdr.Address).value

        assertTrue(addressCreds.signature is SCValXdr.Vec)
        val sigVec = (addressCreds.signature as SCValXdr.Vec).value
        assertNotNull(sigVec)
        assertEquals(1, sigVec.value.size)

        val firstElement = sigVec.value[0]
        assertTrue(firstElement is SCValXdr.Map)
        val sigMap = firstElement.value
        assertNotNull(sigMap)
        assertEquals(2, sigMap.value.size)

        val publicKeyEntry = sigMap.value.find {
            it.key is SCValXdr.Sym && (it.key as SCValXdr.Sym).value.value == "public_key"
        }
        assertNotNull(publicKeyEntry, "Missing 'public_key' in signature map")
        assertTrue(publicKeyEntry.`val` is SCValXdr.Bytes)
        assertEquals(32, (publicKeyEntry.`val` as SCValXdr.Bytes).value.value.size)

        val signatureEntry = sigMap.value.find {
            it.key is SCValXdr.Sym && (it.key as SCValXdr.Sym).value.value == "signature"
        }
        assertNotNull(signatureEntry, "Missing 'signature' in signature map")
        assertTrue(signatureEntry.`val` is SCValXdr.Bytes)
        assertEquals(64, (signatureEntry.`val` as SCValXdr.Bytes).value.value.size)
    }

    @Test
    fun testReturnsUnchangedEntryForSourceAccountCredentials() = runTest {
        val signer = KeyPair.fromSecretSeed(SECRET_SEED)
        val entry = createSourceAccountEntry()

        val signedEntry = Auth.authorizeEntry(
            entry = entry,
            signer = signer,
            validUntilLedgerSeq = VALID_UNTIL_LEDGER_SEQ,
            network = NETWORK
        )

        assertNotSame(entry, signedEntry)
        assertTrue(signedEntry.credentials is SorobanCredentialsXdr.Void)
    }

    @Test
    fun testHandlesEntryCloningCorrectly() = runTest {
        val signer = KeyPair.fromSecretSeed(SECRET_SEED)
        val entry = createUnsignedEntry(signer.getAccountId())

        val originalCreds = (entry.credentials as SorobanCredentialsXdr.Address).value
        val originalExpiration = originalCreds.signatureExpirationLedger.value

        val signedEntry = Auth.authorizeEntry(
            entry = entry,
            signer = signer,
            validUntilLedgerSeq = VALID_UNTIL_LEDGER_SEQ,
            network = NETWORK
        )

        val currentCreds = (entry.credentials as SorobanCredentialsXdr.Address).value
        assertTrue(currentCreds.signature is SCValXdr.Void)
        assertEquals(originalExpiration, currentCreds.signatureExpirationLedger.value)

        assertNotSame(entry, signedEntry)
        val signedCreds = (signedEntry.credentials as SorobanCredentialsXdr.Address).value
        assertFalse(signedCreds.signature is SCValXdr.Void)
        assertEquals(VALID_UNTIL_LEDGER_SEQ.toUInt(), signedCreds.signatureExpirationLedger.value)
    }

    @Test
    fun testThrowsOnInvalidBase64Entry() = runTest {
        val signer = KeyPair.fromSecretSeed(SECRET_SEED)
        val invalidBase64 = "this is not valid base64!!!"

        assertFailsWith<IllegalArgumentException> {
            Auth.authorizeEntry(
                entry = invalidBase64,
                signer = signer,
                validUntilLedgerSeq = VALID_UNTIL_LEDGER_SEQ,
                network = NETWORK
            )
        }
    }

    @Test
    fun testGeneratesUniqueNoncesForMultipleInvocations() = runTest {
        val signer = KeyPair.fromSecretSeed(SECRET_SEED)
        val invocation = createTestInvocation()

        val entry1 = Auth.authorizeInvocation(signer, VALID_UNTIL_LEDGER_SEQ, invocation, NETWORK)
        val entry2 = Auth.authorizeInvocation(signer, VALID_UNTIL_LEDGER_SEQ, invocation, NETWORK)
        val entry3 = Auth.authorizeInvocation(signer, VALID_UNTIL_LEDGER_SEQ, invocation, NETWORK)

        val nonce1 = ((entry1.credentials as SorobanCredentialsXdr.Address).value.nonce.value)
        val nonce2 = ((entry2.credentials as SorobanCredentialsXdr.Address).value.nonce.value)
        val nonce3 = ((entry3.credentials as SorobanCredentialsXdr.Address).value.nonce.value)

        assertNotEquals(nonce1, nonce2)
        assertNotEquals(nonce1, nonce3)
        assertNotEquals(nonce2, nonce3)
    }

    @Test
    fun testSignatureIncludesCorrectNetworkIdForTestnet() = runTest {
        val signer = KeyPair.fromSecretSeed(SECRET_SEED)
        val entry = createUnsignedEntry(signer.getAccountId())

        val signedEntry = Auth.authorizeEntry(
            entry = entry,
            signer = signer,
            validUntilLedgerSeq = VALID_UNTIL_LEDGER_SEQ,
            network = Network.TESTNET
        )

        val preimage = createExpectedPreimage(Network.TESTNET, NONCE, createTestInvocation(), VALID_UNTIL_LEDGER_SEQ)
        val writer = XdrWriter()
        preimage.encode(writer)
        val payload = Util.hash(writer.toByteArray())

        val addressCreds = (signedEntry.credentials as SorobanCredentialsXdr.Address).value
        val sigVec = (addressCreds.signature as SCValXdr.Vec).value!!.value
        val sigMap = (sigVec[0] as SCValXdr.Map).value!!.value
        val signatureEntry = sigMap.find {
            it.key is SCValXdr.Sym && (it.key as SCValXdr.Sym).value.value == "signature"
        }
        val signatureBytes = (signatureEntry!!.`val` as SCValXdr.Bytes).value.value

        assertTrue(signer.verify(payload, signatureBytes))
    }

    @Test
    fun testSignaturesDifferAcrossNetworks() = runTest {
        val signer = KeyPair.fromSecretSeed(SECRET_SEED)
        val entry = createUnsignedEntry(signer.getAccountId())

        val testnetEntry = Auth.authorizeEntry(entry, signer, VALID_UNTIL_LEDGER_SEQ, Network.TESTNET)
        val publicEntry = Auth.authorizeEntry(entry, signer, VALID_UNTIL_LEDGER_SEQ, Network.PUBLIC)

        fun extractSignature(entry: SorobanAuthorizationEntryXdr): ByteArray {
            val addressCreds = (entry.credentials as SorobanCredentialsXdr.Address).value
            val sigVec = (addressCreds.signature as SCValXdr.Vec).value!!.value
            val sigMap = (sigVec[0] as SCValXdr.Map).value!!.value
            val signatureEntry = sigMap.find {
                it.key is SCValXdr.Sym && (it.key as SCValXdr.Sym).value.value == "signature"
            }
            return (signatureEntry!!.`val` as SCValXdr.Bytes).value.value
        }

        val testnetSig = extractSignature(testnetEntry)
        val publicSig = extractSignature(publicEntry)

        assertFalse(testnetSig.contentEquals(publicSig))
    }

    @Test
    fun testSignerNotEqualToCredentialAddressIsAllowed() = runTest {
        val signer = KeyPair.fromSecretSeed(SECRET_SEED)
        val entry = createUnsignedEntry(CREDENTIAL_ADDRESS, NONCE)

        val signedEntry = Auth.authorizeEntry(
            entry = entry,
            signer = signer,
            validUntilLedgerSeq = VALID_UNTIL_LEDGER_SEQ,
            network = NETWORK
        )

        assertTrue(signedEntry.credentials is SorobanCredentialsXdr.Address)
        val addressCreds = (signedEntry.credentials as SorobanCredentialsXdr.Address).value

        val credAddress = Address.fromSCAddress(addressCreds.address)
        assertEquals(CREDENTIAL_ADDRESS, credAddress.toString())

        val sigVec = (addressCreds.signature as SCValXdr.Vec).value!!.value
        val sigMap = (sigVec[0] as SCValXdr.Map).value!!.value
        val publicKeyEntry = sigMap.find {
            it.key is SCValXdr.Sym && (it.key as SCValXdr.Sym).value.value == "public_key"
        }
        val publicKeyBytes = (publicKeyEntry!!.`val` as SCValXdr.Bytes).value.value
        assertContentEquals(signer.getPublicKey(), publicKeyBytes)
    }
}
