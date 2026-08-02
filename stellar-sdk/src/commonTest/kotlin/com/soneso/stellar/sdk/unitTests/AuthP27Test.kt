package com.soneso.stellar.sdk.unitTests

import com.soneso.stellar.sdk.*
import com.soneso.stellar.sdk.scval.Scv
import com.soneso.stellar.sdk.xdr.*
import kotlinx.coroutines.test.runTest
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.*

/**
 * Unit tests for Protocol 27 authorization primitives in Auth.kt and DelegateTree.kt.
 *
 * Test constants use the cross-SDK golden vectors defined in the p27-plan section 8.1.
 * All vectors were independently re-derived and byte-verified.
 */
@OptIn(ExperimentalEncodingApi::class)
class AuthP27Test {

    // ========================================================================
    // Cross-SDK golden vector constants
    // ========================================================================

    companion object {
        // Network
        val NETWORK = Network.TESTNET

        // Fixed signer
        const val SIGNER_SEED = "SDJHRQF4GCMIIKAAAQ6IHY42X73FQFLHUULAPSKKD4DFDM7UXWWCRHBE"
        const val SIGNER_ACCOUNT = "GCZHXL5HXQX5ABDM26LHYRCQZ5OJFHLOPLZX47WEBP3V2PF5AVFK2A5D"

        // Contract and invocation parameters
        const val CONTRACT_ID = "CA3D5KRYM6CB7OWQ6TWYRR3Z4T7GNZLKERYNZGGA5SOAOPIFY6YQGAXE"
        const val NONCE = 123456789101112L
        const val EXPIRATION = 4242L

        // Golden vectors
        const val LEGACY_PREIMAGE_B64 =
            "AAAACc7gMC1ZhE0yvcqRXIID3USzP7t+3BkFHqN6vt8o7NRyAABwSIYPOjgAABCSAAAAAAAAAAE2Pqo4Z4QfutD07YjHeeT+ZuVqJHDcmMDsnAc9BcexAwAAAAVoZWxsbwAAAAAAAAEAAAAFAAAAAAAABNIAAAAA"
        const val LEGACY_PAYLOAD_SHA256 =
            "120c429d4333e12e0ca2c5ac10630e728fdd33240bf7066f4c62f6a2d6fa3cbe"
        const val LEGACY_SIGNATURE_HEX =
            "3c69ceefc532f97e1d0e0eb9f204c9aa85cb2b68cf293bce832590b01455e060e89900ea3ba2c45257908769a1a71f25b6d3befbadffd220f896dc0058699008"

        const val V2_PREIMAGE_B64 =
            "AAAACs7gMC1ZhE0yvcqRXIID3USzP7t+3BkFHqN6vt8o7NRyAABwSIYPOjgAABCSAAAAAAAAAACye6+nvC/QBGzXlnxEUM9ckp1uevN+fsQL9108vQVKrQAAAAAAAAABNj6qOGeEH7rQ9O2Ix3nk/mblaiRw3JjA7JwHPQXHsQMAAAAFaGVsbG8AAAAAAAABAAAABQAAAAAAAATSAAAAAA=="
        const val V2_PAYLOAD_SHA256 =
            "252a0d6117840dff37b765839810fb6ecc446198e73062e01bc961e49355b7b9"

        // A second account address for delegate/forAddress tests (distinct from SIGNER_ACCOUNT)
        // This is a deterministic test address, not a real key
        const val DELEGATE_ACCOUNT = "GADBBY4WFXKKFJ7CMTG3J5YAUXMQDBILRQ6W3U5IWN5TQFZU4MWZ5T4K"

        // A second signer with a real key, distinct from SIGNER_*, used as a delegate node
        // whose KeyPair can actually sign. The account is the public key of DELEGATE_B_SEED.
        const val DELEGATE_B_SEED = "SAEZSI6DY7AXJFIYA4PM6SIBNEYYXIEM2MSOTHFGKHDW32MBQ7KVO6EN"
        const val DELEGATE_B_ACCOUNT = "GBMLPRFCZDZJPKUPHUSHCKA737GOZL7ERZLGGMJ6YGHBFJZ6ZKMKCZTM"

        // A valid muxed (M...) address derived deterministically from SIGNER_ACCOUNT
        // with a fixed multiplexing id.
        val MUXED_ADDRESS: String = MuxedAccount(SIGNER_ACCOUNT, 42UL).address
    }

    // ========================================================================
    // Helpers
    // ========================================================================

    private fun hexToBytes(hex: String): ByteArray {
        return ByteArray(hex.length / 2) { i ->
            hex.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
    }

    private fun bytesToHex(bytes: ByteArray): String =
        bytes.joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }

    /** Builds the canonical invocation for all golden-vector tests. */
    private fun goldenInvocation(): SorobanAuthorizedInvocationXdr {
        return SorobanAuthorizedInvocationXdr(
            function = SorobanAuthorizedFunctionXdr.ContractFn(
                InvokeContractArgsXdr(
                    contractAddress = Address(CONTRACT_ID).toSCAddress(),
                    functionName = SCSymbolXdr("hello"),
                    args = listOf(Scv.toUint64(1234UL))
                )
            ),
            subInvocations = emptyList()
        )
    }

    /** Builds the address credentials shared by the golden-vector entries. */
    private fun baseCredentials(): SorobanAddressCredentialsXdr {
        return SorobanAddressCredentialsXdr(
            address = Address(SIGNER_ACCOUNT).toSCAddress(),
            nonce = Int64Xdr(NONCE),
            signatureExpirationLedger = Uint32Xdr(EXPIRATION.toUInt()),
            signature = Scv.toVoid()
        )
    }

    /** Builds a legacy ADDRESS entry with the golden-vector fixed fields. */
    private fun goldenLegacyEntry(): SorobanAuthorizationEntryXdr {
        return SorobanAuthorizationEntryXdr(
            credentials = SorobanCredentialsXdr.Address(baseCredentials()),
            rootInvocation = goldenInvocation()
        )
    }

    /** Builds an ADDRESS_V2 entry with the golden-vector fixed fields. */
    private fun goldenV2Entry(): SorobanAuthorizationEntryXdr {
        return SorobanAuthorizationEntryXdr(
            credentials = SorobanCredentialsXdr.AddressV2(baseCredentials()),
            rootInvocation = goldenInvocation()
        )
    }

    private fun xdrBytes(preimage: HashIDPreimageXdr): ByteArray {
        val writer = XdrWriter()
        preimage.encode(writer)
        return writer.toByteArray()
    }

    private fun nodeBytes(node: SorobanDelegateSignatureXdr): ByteArray {
        val writer = XdrWriter()
        node.encode(writer)
        return writer.toByteArray()
    }

    // ========================================================================
    // Credential extraction helpers
    // ========================================================================

    @Test
    fun testAddressCredentialsReturnsValueForAddressArm() {
        val expectedAddress = Address(SIGNER_ACCOUNT).toSCAddress()
        val creds = SorobanCredentialsXdr.Address(
            SorobanAddressCredentialsXdr(
                address = expectedAddress,
                nonce = Int64Xdr(1L),
                signatureExpirationLedger = Uint32Xdr(100u),
                signature = Scv.toVoid()
            )
        )
        val inner = creds.addressCredentials()
        assertNotNull(inner)
        assertContentEquals(expectedAddress.toXdrBytes(), inner.address.toXdrBytes())
        assertEquals(1L, inner.nonce.value)
    }

    @Test
    fun testAddressCredentialsReturnsValueForAddressV2Arm() {
        val expectedAddress = Address(SIGNER_ACCOUNT).toSCAddress()
        val creds = SorobanCredentialsXdr.AddressV2(
            SorobanAddressCredentialsXdr(
                address = expectedAddress,
                nonce = Int64Xdr(1L),
                signatureExpirationLedger = Uint32Xdr(100u),
                signature = Scv.toVoid()
            )
        )
        val inner = creds.addressCredentials()
        assertNotNull(inner)
        assertContentEquals(expectedAddress.toXdrBytes(), inner.address.toXdrBytes())
        assertEquals(1L, inner.nonce.value)
    }

    @Test
    fun testAddressCredentialsReturnsValueForAddressWithDelegatesArm() {
        val expectedAddress = Address(SIGNER_ACCOUNT).toSCAddress()
        val base = SorobanAddressCredentialsXdr(
            address = expectedAddress,
            nonce = Int64Xdr(1L),
            signatureExpirationLedger = Uint32Xdr(100u),
            signature = Scv.toVoid()
        )
        val creds = SorobanCredentialsXdr.AddressWithDelegates(
            SorobanAddressCredentialsWithDelegatesXdr(base, emptyList())
        )
        val inner = creds.addressCredentials()
        assertNotNull(inner)
        // The WITH_DELEGATES arm exposes the top-level credential's address and nonce.
        assertContentEquals(expectedAddress.toXdrBytes(), inner.address.toXdrBytes())
        assertEquals(1L, inner.nonce.value)
    }

    @Test
    fun testAddressCredentialsReturnsNullForVoidArm() {
        assertNull(SorobanCredentialsXdr.Void.addressCredentials())
    }

    @Test
    fun testRequireAddressCredentialsThrowsForVoid() {
        assertFailsWith<IllegalArgumentException> {
            SorobanCredentialsXdr.Void.requireAddressCredentials()
        }
    }

    @Test
    fun testWithUpdatedAddressCredentialsPreservesArm() {
        val newCreds = SorobanAddressCredentialsXdr(
            address = Address(SIGNER_ACCOUNT).toSCAddress(),
            nonce = Int64Xdr(99L),
            signatureExpirationLedger = Uint32Xdr(999u),
            signature = Scv.toVoid()
        )

        val addressArm = SorobanCredentialsXdr.Address(
            SorobanAddressCredentialsXdr(
                Address(SIGNER_ACCOUNT).toSCAddress(), Int64Xdr(1L), Uint32Xdr(1u), Scv.toVoid()
            )
        )
        val updated = addressArm.withUpdatedAddressCredentials(newCreds)
        assertTrue(updated is SorobanCredentialsXdr.Address)
        assertEquals(99L, updated.value.nonce.value)

        val v2Arm = SorobanCredentialsXdr.AddressV2(
            SorobanAddressCredentialsXdr(
                Address(SIGNER_ACCOUNT).toSCAddress(), Int64Xdr(1L), Uint32Xdr(1u), Scv.toVoid()
            )
        )
        val updatedV2 = v2Arm.withUpdatedAddressCredentials(newCreds)
        assertTrue(updatedV2 is SorobanCredentialsXdr.AddressV2)
        assertEquals(99L, updatedV2.value.nonce.value)

        val delegate = SorobanDelegateSignatureXdr(
            address = Address(DELEGATE_ACCOUNT).toSCAddress(),
            signature = Scv.toVoid(),
            nestedDelegates = emptyList()
        )
        val withDel = SorobanCredentialsXdr.AddressWithDelegates(
            SorobanAddressCredentialsWithDelegatesXdr(
                SorobanAddressCredentialsXdr(
                    Address(SIGNER_ACCOUNT).toSCAddress(), Int64Xdr(1L), Uint32Xdr(1u), Scv.toVoid()
                ),
                listOf(delegate)
            )
        )
        val updatedWithDel = withDel.withUpdatedAddressCredentials(newCreds)
        assertTrue(updatedWithDel is SorobanCredentialsXdr.AddressWithDelegates)
        assertEquals(99L, updatedWithDel.value.addressCredentials.nonce.value)
        // Delegates are preserved
        assertEquals(1, updatedWithDel.value.delegates.size)
    }

    @Test
    fun testWithUpdatedAddressCredentialsRejectsVoid() {
        val void: SorobanCredentialsXdr = SorobanCredentialsXdr.Void
        val ex = assertFailsWith<IllegalArgumentException> {
            void.withUpdatedAddressCredentials(baseCredentials())
        }
        assertTrue(ex.message!!.contains("Void"), "message must name Void; got: ${ex.message}")
    }

    // ========================================================================
    // Preimage builder
    // ========================================================================

    @Test
    fun testBuildHashIDPreimageLegacyGoldenVector() = runTest {
        val entry = goldenLegacyEntry()
        val networkId = NETWORK.networkId()
        val creds = entry.credentials
        val addressCreds = creds.requireAddressCredentials()

        val preimage = Auth.buildHashIDPreimage(
            credentials = creds,
            networkId = networkId,
            invocation = entry.rootInvocation
        )

        // Must be the legacy arm
        assertTrue(preimage is HashIDPreimageXdr.SorobanAuthorization)

        // Byte-identity check against the golden vector
        val encoded = Base64.encode(xdrBytes(preimage))
        assertEquals(LEGACY_PREIMAGE_B64, encoded)
    }

    @Test
    fun testBuildHashIDPreimageV2GoldenVector() = runTest {
        val entry = goldenV2Entry()
        val networkId = NETWORK.networkId()

        val preimage = Auth.buildHashIDPreimage(
            credentials = entry.credentials,
            networkId = networkId,
            invocation = entry.rootInvocation
        )

        // Must be the V2 arm
        assertTrue(preimage is HashIDPreimageXdr.SorobanAuthorizationWithAddress)

        // Byte-identity check against the golden vector
        val encoded = Base64.encode(xdrBytes(preimage))
        assertEquals(V2_PREIMAGE_B64, encoded)
    }

    @Test
    fun testPreimageDiscriminantPerArm() = runTest {
        val networkId = NETWORK.networkId()
        val invocation = goldenInvocation()
        val baseCreds = SorobanAddressCredentialsXdr(
            address = Address(SIGNER_ACCOUNT).toSCAddress(),
            nonce = Int64Xdr(NONCE),
            signatureExpirationLedger = Uint32Xdr(EXPIRATION.toUInt()),
            signature = Scv.toVoid()
        )

        // ADDRESS -> legacy
        val legacyPreimage = Auth.buildHashIDPreimage(
            SorobanCredentialsXdr.Address(baseCreds), networkId, invocation
        )
        assertEquals(
            EnvelopeTypeXdr.ENVELOPE_TYPE_SOROBAN_AUTHORIZATION,
            legacyPreimage.discriminant
        )

        // ADDRESS_V2 -> with-address
        val v2Preimage = Auth.buildHashIDPreimage(
            SorobanCredentialsXdr.AddressV2(baseCreds), networkId, invocation
        )
        assertEquals(
            EnvelopeTypeXdr.ENVELOPE_TYPE_SOROBAN_AUTHORIZATION_WITH_ADDRESS,
            v2Preimage.discriminant
        )

        // ADDRESS_WITH_DELEGATES -> with-address
        val withDelegatesCreds = SorobanCredentialsXdr.AddressWithDelegates(
            SorobanAddressCredentialsWithDelegatesXdr(baseCreds, emptyList())
        )
        val delegatesPreimage = Auth.buildHashIDPreimage(withDelegatesCreds, networkId, invocation)
        assertEquals(
            EnvelopeTypeXdr.ENVELOPE_TYPE_SOROBAN_AUTHORIZATION_WITH_ADDRESS,
            delegatesPreimage.discriminant
        )
    }

    @Test
    fun testAddressVsAddressV2PreimageDifferForIdenticalFields() = runTest {
        val networkId = NETWORK.networkId()
        val invocation = goldenInvocation()
        val baseCreds = SorobanAddressCredentialsXdr(
            address = Address(SIGNER_ACCOUNT).toSCAddress(),
            nonce = Int64Xdr(NONCE),
            signatureExpirationLedger = Uint32Xdr(EXPIRATION.toUInt()),
            signature = Scv.toVoid()
        )

        val legacyBytes = xdrBytes(
            Auth.buildHashIDPreimage(SorobanCredentialsXdr.Address(baseCreds), networkId, invocation)
        )
        val v2Bytes = xdrBytes(
            Auth.buildHashIDPreimage(SorobanCredentialsXdr.AddressV2(baseCreds), networkId, invocation)
        )
        assertFalse(legacyBytes.contentEquals(v2Bytes))
    }

    @Test
    fun testPreimageForWithDelegatesUsesTopLevelCredentialAddress() = runTest {
        val networkId = NETWORK.networkId()
        val invocation = goldenInvocation()
        val topLevelCreds = SorobanAddressCredentialsXdr(
            address = Address(SIGNER_ACCOUNT).toSCAddress(),
            nonce = Int64Xdr(NONCE),
            signatureExpirationLedger = Uint32Xdr(EXPIRATION.toUInt()),
            signature = Scv.toVoid()
        )
        val delegateNode = SorobanDelegateSignatureXdr(
            address = Address(DELEGATE_ACCOUNT).toSCAddress(),
            signature = Scv.toVoid(),
            nestedDelegates = emptyList()
        )
        val withDelegatesCreds = SorobanCredentialsXdr.AddressWithDelegates(
            SorobanAddressCredentialsWithDelegatesXdr(topLevelCreds, listOf(delegateNode))
        )

        val preimage = Auth.buildHashIDPreimage(withDelegatesCreds, networkId, invocation)
        assertTrue(preimage is HashIDPreimageXdr.SorobanAuthorizationWithAddress)

        // The address in the preimage must be the TOP-LEVEL credential address, not the delegate's
        val preimageAddressBytes = preimage.value.address.toXdrBytes()
        val expectedAddressBytes = Address(SIGNER_ACCOUNT).toSCAddress().toXdrBytes()
        val delegateAddressBytes = Address(DELEGATE_ACCOUNT).toSCAddress().toXdrBytes()

        assertContentEquals(expectedAddressBytes, preimageAddressBytes)
        assertFalse(preimageAddressBytes.contentEquals(delegateAddressBytes))
    }

    @Test
    fun testBuildHashIDPreimageThrowsForVoidCredentials() = runTest {
        val networkId = NETWORK.networkId()
        assertFailsWith<IllegalArgumentException> {
            Auth.buildHashIDPreimage(SorobanCredentialsXdr.Void, networkId, goldenInvocation())
        }
    }

    @Test
    fun testWithAddressPreimageEncodeDecodeRoundTrip() = runTest {
        // The encode path is covered by the golden vectors above; this asserts the
        // decode side reconstructs every field and selects the WITH_ADDRESS arm.
        val preimage = Auth.buildHashIDPreimage(
            credentials = SorobanCredentialsXdr.AddressV2(baseCredentials()),
            networkId = NETWORK.networkId(),
            invocation = goldenInvocation()
        )
        assertIs<HashIDPreimageXdr.SorobanAuthorizationWithAddress>(preimage)

        val decoded = HashIDPreimageXdr.decode(XdrReader(xdrBytes(preimage)))
        assertIs<HashIDPreimageXdr.SorobanAuthorizationWithAddress>(decoded)

        val original = preimage.value
        val back = decoded.value
        assertContentEquals(original.networkId.value, back.networkId.value)
        assertEquals(original.nonce.value, back.nonce.value)
        assertEquals(original.signatureExpirationLedger.value, back.signatureExpirationLedger.value)
        assertContentEquals(original.address.toXdrBytes(), back.address.toXdrBytes())
        assertEquals(NONCE, back.nonce.value)
        assertEquals(EXPIRATION.toUInt(), back.signatureExpirationLedger.value)
    }

    @Test
    fun testDecodedWithAddressPreimageCarriesTopLevelAddress() = runTest {
        // The WITH_ADDRESS preimage binds the top-level credential address; a decoded
        // preimage must carry SIGNER_ACCOUNT, never a delegate address.
        val withDelegates = Auth.attachDelegates(
            goldenLegacyEntry(),
            EXPIRATION,
            listOf(DelegateDescriptor(address = DELEGATE_ACCOUNT))
        )
        val preimage = Auth.buildHashIDPreimage(
            credentials = withDelegates.credentials,
            networkId = NETWORK.networkId(),
            invocation = withDelegates.rootInvocation
        )
        val decoded = HashIDPreimageXdr.decode(XdrReader(xdrBytes(preimage)))
        val back = (decoded as HashIDPreimageXdr.SorobanAuthorizationWithAddress).value
        assertContentEquals(
            Address(SIGNER_ACCOUNT).toSCAddress().toXdrBytes(),
            back.address.toXdrBytes()
        )
    }

    // ========================================================================
    // Payload hash golden vectors
    // ========================================================================

    @Test
    fun testLegacyPayloadHashGoldenVector() = runTest {
        val entry = goldenLegacyEntry()
        val networkId = NETWORK.networkId()
        val preimage = Auth.buildHashIDPreimage(entry.credentials, networkId, entry.rootInvocation)
        val hash = Util.hash(xdrBytes(preimage))
        assertEquals(LEGACY_PAYLOAD_SHA256, bytesToHex(hash))
    }

    @Test
    fun testV2PayloadHashGoldenVector() = runTest {
        val entry = goldenV2Entry()
        val networkId = NETWORK.networkId()
        val preimage = Auth.buildHashIDPreimage(entry.credentials, networkId, entry.rootInvocation)
        val hash = Util.hash(xdrBytes(preimage))
        assertEquals(V2_PAYLOAD_SHA256, bytesToHex(hash))
    }

    // ========================================================================
    // authorizeEntry: expiration set before hash
    // ========================================================================

    @Test
    fun testExpirationIsSetBeforeHash() = runTest {
        // If expiration were set AFTER hashing, the network would reconstruct a
        // different preimage and reject the signature. This test verifies that
        // the expiration in the returned credentials matches what was signed.
        val signer = KeyPair.fromSecretSeed(SIGNER_SEED)
        val entry = SorobanAuthorizationEntryXdr(
            credentials = SorobanCredentialsXdr.Address(
                SorobanAddressCredentialsXdr(
                    address = Address(SIGNER_ACCOUNT).toSCAddress(),
                    nonce = Int64Xdr(NONCE),
                    signatureExpirationLedger = Uint32Xdr(0u), // starts at 0
                    signature = Scv.toVoid()
                )
            ),
            rootInvocation = goldenInvocation()
        )

        val signed = Auth.authorizeEntry(entry, signer, EXPIRATION, NETWORK)
        val signedCreds = (signed.credentials as SorobanCredentialsXdr.Address).value

        // The expiration must be the target value
        assertEquals(EXPIRATION.toUInt(), signedCreds.signatureExpirationLedger.value)

        // And the signature must verify against the preimage built with that expiration
        val networkId = NETWORK.networkId()
        val preimage = Auth.buildHashIDPreimage(signed.credentials, networkId, signed.rootInvocation)
        val payload = Util.hash(xdrBytes(preimage))

        val sigVec = (signedCreds.signature as SCValXdr.Vec).value!!.value
        val sigMap = (sigVec[0] as SCValXdr.Map).value!!.value
        val sigEntry = sigMap.find { it.key is SCValXdr.Sym && (it.key as SCValXdr.Sym).value.value == "signature" }
        val sigBytes = (sigEntry!!.`val` as SCValXdr.Bytes).value.value
        assertTrue(signer.verify(payload, sigBytes))
    }

    @Test
    fun testExpirationBeforeHashForV2Arm() = runTest {
        val signer = KeyPair.fromSecretSeed(SIGNER_SEED)
        val entry = SorobanAuthorizationEntryXdr(
            credentials = SorobanCredentialsXdr.AddressV2(
                SorobanAddressCredentialsXdr(
                    address = Address(SIGNER_ACCOUNT).toSCAddress(),
                    nonce = Int64Xdr(NONCE),
                    signatureExpirationLedger = Uint32Xdr(0u),
                    signature = Scv.toVoid()
                )
            ),
            rootInvocation = goldenInvocation()
        )

        val signed = Auth.authorizeEntry(entry, signer, EXPIRATION, NETWORK)
        val signedCreds = (signed.credentials as SorobanCredentialsXdr.AddressV2).value
        assertEquals(EXPIRATION.toUInt(), signedCreds.signatureExpirationLedger.value)

        // The signature must verify against the V2 (WITH_ADDRESS) preimage rebuilt from
        // the signed credentials. This proves the signature was made over the stamped
        // expiration, not merely that the field was written into the returned entry.
        val networkId = NETWORK.networkId()
        val preimage = Auth.buildHashIDPreimage(signed.credentials, networkId, signed.rootInvocation)
        assertTrue(preimage is HashIDPreimageXdr.SorobanAuthorizationWithAddress)
        val payload = Util.hash(xdrBytes(preimage))

        val sigVec = (signedCreds.signature as SCValXdr.Vec).value!!.value
        val sigMap = (sigVec[0] as SCValXdr.Map).value!!.value
        val sigEntry = sigMap.find { it.key is SCValXdr.Sym && (it.key as SCValXdr.Sym).value.value == "signature" }
        val sigBytes = (sigEntry!!.`val` as SCValXdr.Bytes).value.value
        assertTrue(signer.verify(payload, sigBytes), "V2 signature must verify over the stamped expiration")
    }

    // ========================================================================
    // authorizeEntry: legacy golden signature
    // ========================================================================

    @Test
    fun testLegacySignatureGoldenVector() = runTest {
        val signer = KeyPair.fromSecretSeed(SIGNER_SEED)
        // Use goldenLegacyEntry() which already has expiration set to EXPIRATION.
        val entry = goldenLegacyEntry()

        val signed = Auth.authorizeEntry(entry, signer, EXPIRATION, NETWORK)
        val signedCreds = (signed.credentials as SorobanCredentialsXdr.Address).value
        val sigVec = (signedCreds.signature as SCValXdr.Vec).value!!.value
        val sigMap = (sigVec[0] as SCValXdr.Map).value!!.value
        val sigEntry = sigMap.find { it.key is SCValXdr.Sym && (it.key as SCValXdr.Sym).value.value == "signature" }
        val sigBytes = (sigEntry!!.`val` as SCValXdr.Bytes).value.value

        assertEquals(LEGACY_SIGNATURE_HEX, bytesToHex(sigBytes))
    }

    // ========================================================================
    // authorizeEntry: arm preservation
    // ========================================================================

    @Test
    fun testAuthorizeEntryPreservesAddressArm() = runTest {
        val signer = KeyPair.fromSecretSeed(SIGNER_SEED)
        val entry = goldenLegacyEntry()
        val signed = Auth.authorizeEntry(entry, signer, EXPIRATION, NETWORK)
        assertTrue(signed.credentials is SorobanCredentialsXdr.Address)
    }

    @Test
    fun testAuthorizeEntryPreservesAddressV2Arm() = runTest {
        val signer = KeyPair.fromSecretSeed(SIGNER_SEED)
        val entry = goldenV2Entry()
        val signed = Auth.authorizeEntry(entry, signer, EXPIRATION, NETWORK)
        assertTrue(signed.credentials is SorobanCredentialsXdr.AddressV2)
    }

    @Test
    fun testSourceAccountEntryReturnedUnchanged() = runTest {
        val signer = KeyPair.fromSecretSeed(SIGNER_SEED)
        val entry = SorobanAuthorizationEntryXdr(
            credentials = SorobanCredentialsXdr.Void,
            rootInvocation = goldenInvocation()
        )
        val result = Auth.authorizeEntry(entry, signer, EXPIRATION, NETWORK)
        assertTrue(result.credentials is SorobanCredentialsXdr.Void)
    }

    // ========================================================================
    // authorizeEntry: base64 overload with a custom Signer
    // ========================================================================

    @Test
    fun testAuthorizeEntryBase64WithCustomSignerSignsTopLevel() = runTest {
        val keyPair = KeyPair.fromSecretSeed(SIGNER_SEED)
        val entryB64 = goldenLegacyEntry().toXdrBase64()

        val customSigner = Auth.Signer { preimage ->
            val payload = Util.hash(xdrBytes(preimage))
            Auth.Signature(keyPair.getAccountId(), keyPair.sign(payload))
        }

        val signed = Auth.authorizeEntry(entryB64, customSigner, EXPIRATION, NETWORK)

        val creds = (signed.credentials as SorobanCredentialsXdr.Address).value
        assertContentEquals(keyPair.getPublicKey(), singleSignaturePublicKey(creds.signature))
    }

    @Test
    fun testAuthorizeEntryBase64InvalidThrows() = runTest {
        val keyPair = KeyPair.fromSecretSeed(SIGNER_SEED)
        val customSigner = Auth.Signer { preimage ->
            Auth.Signature(keyPair.getAccountId(), keyPair.sign(Util.hash(xdrBytes(preimage))))
        }
        assertFailsWith<IllegalArgumentException> {
            Auth.authorizeEntry("not-valid-base64-xdr!!!", customSigner, EXPIRATION, NETWORK)
        }
    }

    // ========================================================================
    // authorizeEntry: signature append semantics
    // ========================================================================

    @Test
    fun testVoidSignatureBecomesOneElementVec() = runTest {
        val signer = KeyPair.fromSecretSeed(SIGNER_SEED)
        val entry = goldenLegacyEntry()
        val signed = Auth.authorizeEntry(entry, signer, EXPIRATION, NETWORK)
        val creds = (signed.credentials as SorobanCredentialsXdr.Address).value
        assertTrue(creds.signature is SCValXdr.Vec)
        val vec = (creds.signature as SCValXdr.Vec).value!!.value
        assertEquals(1, vec.size)
    }

    @Test
    fun testAppendToExistingVecGrowsWithoutReordering() = runTest {
        // Two distinct signers: the second append must not remove or reorder the first.
        val signer1 = KeyPair.fromSecretSeed(SIGNER_SEED)
        val signer2 = KeyPair.random()
        val entry = goldenLegacyEntry()

        val signed1 = Auth.authorizeEntry(entry, signer1, EXPIRATION, NETWORK)
        val signed2 = Auth.authorizeEntry(signed1, signer2, EXPIRATION, NETWORK)

        val creds = (signed2.credentials as SorobanCredentialsXdr.Address).value
        val vec = (creds.signature as SCValXdr.Vec).value!!.value
        assertEquals(2, vec.size)
        // Order preserved: the first signer stays at index 0, the second is appended at index 1.
        assertContentEquals(signer1.getPublicKey(), signatureElementPublicKey(vec[0]))
        assertContentEquals(signer2.getPublicKey(), signatureElementPublicKey(vec[1]))
    }

    /** Reads the `public_key` bytes from one element of an Ed25519 signature vec. */
    private fun signatureElementPublicKey(element: SCValXdr): ByteArray {
        val map = (element as SCValXdr.Map).value!!.value
        val entry = map.find { it.key is SCValXdr.Sym && (it.key as SCValXdr.Sym).value.value == "public_key" }
        return (entry!!.`val` as SCValXdr.Bytes).value.value
    }

    /** Returns the raw public-key bytes from the single signature element of [signature]. */
    private fun singleSignaturePublicKey(signature: SCValXdr): ByteArray {
        val vec = (signature as SCValXdr.Vec).value!!.value
        assertEquals(1, vec.size, "expected exactly one signature element")
        return signatureElementPublicKey(vec[0])
    }

    @Test
    fun testVoidTopLevelSignatureIsPreserved() {
        // attachDelegates sets top-level signature to void; it must not be changed
        // by delegate-signing operations.
        val entry = goldenLegacyEntry()
        val withDelegates = Auth.attachDelegates(
            entry,
            EXPIRATION,
            listOf(DelegateDescriptor(address = DELEGATE_ACCOUNT))
        )
        val creds = (withDelegates.credentials as SorobanCredentialsXdr.AddressWithDelegates).value
        assertTrue(creds.addressCredentials.signature is SCValXdr.Void)
    }

    // ========================================================================
    // authorizeEntry: forAddress routing
    // ========================================================================

    @Test
    fun testForAddressSignsDelegateNodeAndLeavesTopLevelVoid() = runTest {
        // DISTINCT addresses: top-level = SIGNER_ACCOUNT, delegate = DELEGATE_ACCOUNT
        val signer = KeyPair.fromSecretSeed(SIGNER_SEED)
        val entry = goldenLegacyEntry()
        val withDelegates = Auth.attachDelegates(
            entry,
            EXPIRATION,
            listOf(DelegateDescriptor(address = DELEGATE_ACCOUNT))
        )

        // Sign only the delegate node
        val signed = Auth.authorizeEntry(
            withDelegates,
            signer,
            EXPIRATION,
            NETWORK,
            Auth.AuthOptions(forAddress = DELEGATE_ACCOUNT)
        )

        val outerCreds = (signed.credentials as SorobanCredentialsXdr.AddressWithDelegates).value

        // Top-level remains void
        assertTrue(outerCreds.addressCredentials.signature is SCValXdr.Void)

        // Delegate has a signature
        val delegateNode = outerCreds.delegates.first()
        assertFalse(delegateNode.signature is SCValXdr.Void)
        assertTrue(delegateNode.signature is SCValXdr.Vec)
    }

    @Test
    fun testTopLevelAndDelegateSignSameHash() = runTest {
        // Both the top-level signer and a delegate signer verify against the same payload hash.
        val signer = KeyPair.fromSecretSeed(SIGNER_SEED)
        val entry = goldenLegacyEntry()
        val withDelegates = Auth.attachDelegates(
            entry,
            EXPIRATION,
            listOf(DelegateDescriptor(address = DELEGATE_ACCOUNT))
        )

        // Sign the delegate node
        val signedDelegate = Auth.authorizeEntry(
            withDelegates,
            signer,
            EXPIRATION,
            NETWORK,
            Auth.AuthOptions(forAddress = DELEGATE_ACCOUNT)
        )
        // Sign the top-level
        val signedBoth = Auth.authorizeEntry(
            signedDelegate,
            signer,
            EXPIRATION,
            NETWORK
            // forAddress = null -> top-level
        )

        val outerCreds = (signedBoth.credentials as SorobanCredentialsXdr.AddressWithDelegates).value

        // Both nodes carry a signature; both verify against the same payload hash
        val networkId = NETWORK.networkId()
        val preimage = Auth.buildHashIDPreimage(signedBoth.credentials, networkId, signedBoth.rootInvocation)
        val payload = Util.hash(xdrBytes(preimage))

        fun extractSig(sigVal: SCValXdr): ByteArray {
            val vec = (sigVal as SCValXdr.Vec).value!!.value
            val map = (vec[0] as SCValXdr.Map).value!!.value
            val entry = map.find { it.key is SCValXdr.Sym && (it.key as SCValXdr.Sym).value.value == "signature" }
            return (entry!!.`val` as SCValXdr.Bytes).value.value
        }

        val topSig = extractSig(outerCreds.addressCredentials.signature)
        val delSig = extractSig(outerCreds.delegates.first().signature)

        assertTrue(signer.verify(payload, topSig))
        assertTrue(signer.verify(payload, delSig))
    }

    @Test
    fun testSigningOneDelegateLeavesOtherDelegateAndTopLevelUntouched() = runTest {
        // Two distinct delegate nodes A and B. Signing A must touch only A: B's node and
        // the top-level credential must be byte-identical before/after. This proves the
        // signature routing maps exactly the matching delegate node, not every node.
        val signerA = KeyPair.fromSecretSeed(SIGNER_SEED)             // delegate A
        val signerB = KeyPair.fromSecretSeed(DELEGATE_B_SEED)         // delegate B
        val addrA = signerA.getAccountId()
        val addrB = signerB.getAccountId()

        // Build a WITH_DELEGATES entry over a top-level credential that is NOT either
        // delegate, so the top-level node stays void throughout.
        val topLevel = SorobanAuthorizationEntryXdr(
            credentials = SorobanCredentialsXdr.Address(
                SorobanAddressCredentialsXdr(
                    address = Address(DELEGATE_ACCOUNT).toSCAddress(),
                    nonce = Int64Xdr(NONCE),
                    signatureExpirationLedger = Uint32Xdr(EXPIRATION.toUInt()),
                    signature = Scv.toVoid()
                )
            ),
            rootInvocation = goldenInvocation()
        )
        val withDelegates = Auth.attachDelegates(
            topLevel,
            EXPIRATION,
            listOf(DelegateDescriptor(address = addrA), DelegateDescriptor(address = addrB))
        )

        val addrABytes = Address(addrA).toSCAddress().toXdrBytes()

        // Sign delegate A.
        val afterA = Auth.authorizeEntry(
            withDelegates, signerA, EXPIRATION, NETWORK, Auth.AuthOptions(forAddress = addrA)
        )
        val credsAfterA = (afterA.credentials as SorobanCredentialsXdr.AddressWithDelegates).value
        val nodeAAfterAOnly = credsAfterA.delegates.first { it.address.toXdrBytes().contentEquals(addrABytes) }
        // Capture A's signed node bytes after signing A but before signing B.
        val nodeABytesBeforeB = nodeBytes(nodeAAfterAOnly)
        assertTrue(nodeAAfterAOnly.signature is SCValXdr.Vec, "delegate A must be signed after signing A")

        // Now sign delegate B on top of the A-signed entry.
        val afterB = Auth.authorizeEntry(
            afterA, signerB, EXPIRATION, NETWORK, Auth.AuthOptions(forAddress = addrB)
        )
        val credsAfterB = (afterB.credentials as SorobanCredentialsXdr.AddressWithDelegates).value

        // Delegate A's node must be byte-identical: signing B did not touch A.
        val nodeAAfterB = credsAfterB.delegates.first { it.address.toXdrBytes().contentEquals(addrABytes) }
        assertContentEquals(
            nodeABytesBeforeB,
            nodeBytes(nodeAAfterB),
            "signing delegate B must leave delegate A's node byte-identical"
        )

        // Delegate B is now signed.
        val addrBBytes = Address(addrB).toSCAddress().toXdrBytes()
        val nodeBAfterB = credsAfterB.delegates.first { it.address.toXdrBytes().contentEquals(addrBBytes) }
        assertTrue(nodeBAfterB.signature is SCValXdr.Vec, "delegate B must be signed after signing B")

        // The top-level credential stays void: neither delegate signing touched it.
        assertTrue(
            credsAfterB.addressCredentials.signature is SCValXdr.Void,
            "top-level signature must remain void"
        )
    }

    @Test
    fun testForAddressNoMatchThrows() = runTest {
        val signer = KeyPair.fromSecretSeed(SIGNER_SEED)
        val entry = goldenLegacyEntry()
        // entry has SIGNER_ACCOUNT; forAddress = DELEGATE_ACCOUNT (not present)
        assertFailsWith<IllegalArgumentException> {
            Auth.authorizeEntry(
                entry,
                signer,
                EXPIRATION,
                NETWORK,
                Auth.AuthOptions(forAddress = DELEGATE_ACCOUNT)
            )
        }
    }

    @Test
    fun testForAddressMuxedThrows() = runTest {
        // A structurally valid M... address must be rejected by signForAddress, because
        // muxed addresses are not valid Soroban auth addresses. Asserting the address type
        // up front keeps the test from passing on a plain strkey-decoding failure.
        assertEquals(Address.AddressType.MUXED_ACCOUNT, Address(MUXED_ADDRESS).addressType)
        val signer = KeyPair.fromSecretSeed(SIGNER_SEED)
        val ex = assertFailsWith<IllegalArgumentException> {
            Auth.authorizeEntry(
                goldenLegacyEntry(),
                signer,
                EXPIRATION,
                NETWORK,
                Auth.AuthOptions(forAddress = MUXED_ADDRESS)
            )
        }
        assertTrue(ex.message!!.contains("Muxed"), "message must name the muxed rejection; got: ${ex.message}")
    }

    @Test
    fun testForAddressMatchingTopLevelLegacyAddressArm() = runTest {
        // forAddress == the top-level credential address on a legacy ADDRESS entry.
        // The signature must land on the top-level credentials and the arm is preserved.
        val keyPair = KeyPair.fromSecretSeed(SIGNER_SEED)
        val signed = Auth.authorizeEntry(
            goldenLegacyEntry(),
            keyPair,
            EXPIRATION,
            NETWORK,
            Auth.AuthOptions(forAddress = SIGNER_ACCOUNT)
        )

        assertIs<SorobanCredentialsXdr.Address>(signed.credentials)
        assertContentEquals(
            keyPair.getPublicKey(),
            singleSignaturePublicKey(signed.credentials.value.signature)
        )
    }

    @Test
    fun testForAddressMatchingTopLevelV2Arm() = runTest {
        // forAddress == the top-level credential address on an ADDRESS_V2 entry.
        val keyPair = KeyPair.fromSecretSeed(SIGNER_SEED)
        val signed = Auth.authorizeEntry(
            goldenV2Entry(),
            keyPair,
            EXPIRATION,
            NETWORK,
            Auth.AuthOptions(forAddress = SIGNER_ACCOUNT)
        )

        assertIs<SorobanCredentialsXdr.AddressV2>(signed.credentials)
        assertContentEquals(
            keyPair.getPublicKey(),
            singleSignaturePublicKey(signed.credentials.value.signature)
        )
    }

    // ========================================================================
    // authorizeInvocation authV2 flag
    // ========================================================================

    @Test
    fun testAuthorizeInvocationWithAuthV2FalseCreatesAddressArm() = runTest {
        val signer = KeyPair.fromSecretSeed(SIGNER_SEED)
        val result = Auth.authorizeInvocation(
            signer, EXPIRATION, goldenInvocation(), NETWORK, authV2 = false
        )
        assertTrue(result.credentials is SorobanCredentialsXdr.Address)
    }

    @Test
    fun testAuthorizeInvocationWithAuthV2TrueCreatesAddressV2Arm() = runTest {
        val signer = KeyPair.fromSecretSeed(SIGNER_SEED)
        val result = Auth.authorizeInvocation(
            signer, EXPIRATION, goldenInvocation(), NETWORK, authV2 = true
        )
        assertTrue(result.credentials is SorobanCredentialsXdr.AddressV2)
    }

    @Test
    fun testAuthorizeInvocationV2UsesWithAddressPreimage() = runTest {
        val signer = KeyPair.fromSecretSeed(SIGNER_SEED)
        val result = Auth.authorizeInvocation(
            signer, EXPIRATION, goldenInvocation(), NETWORK, authV2 = true
        )

        val networkId = NETWORK.networkId()
        val preimage = Auth.buildHashIDPreimage(result.credentials, networkId, result.rootInvocation)
        assertTrue(preimage is HashIDPreimageXdr.SorobanAuthorizationWithAddress)
    }

    // ========================================================================
    // attachDelegates
    // ========================================================================

    @Test
    fun testAttachDelegatesProducesWithDelegatesArm() {
        val entry = goldenLegacyEntry()
        val result = Auth.attachDelegates(
            entry,
            EXPIRATION,
            listOf(DelegateDescriptor(address = DELEGATE_ACCOUNT))
        )
        assertTrue(result.credentials is SorobanCredentialsXdr.AddressWithDelegates)
    }

    @Test
    fun testAttachDelegatesRejectsAlreadyWithDelegatesEntry() {
        val entry = goldenLegacyEntry()
        val withDelegates = Auth.attachDelegates(
            entry, EXPIRATION, listOf(DelegateDescriptor(address = DELEGATE_ACCOUNT))
        )
        assertFailsWith<IllegalArgumentException> {
            Auth.attachDelegates(withDelegates, EXPIRATION, emptyList())
        }
    }

    @Test
    fun testAttachDelegatesRejectsVoidCredentials() {
        val entry = SorobanAuthorizationEntryXdr(
            credentials = SorobanCredentialsXdr.Void,
            rootInvocation = goldenInvocation()
        )
        assertFailsWith<IllegalArgumentException> {
            Auth.attachDelegates(entry, EXPIRATION, listOf(DelegateDescriptor(address = DELEGATE_ACCOUNT)))
        }
    }

    @Test
    fun testAttachDelegatesSetsTopLevelSignatureToVoid() {
        val entry = goldenLegacyEntry()
        val result = Auth.attachDelegates(
            entry, EXPIRATION, listOf(DelegateDescriptor(address = DELEGATE_ACCOUNT))
        )
        val creds = (result.credentials as SorobanCredentialsXdr.AddressWithDelegates).value
        assertTrue(creds.addressCredentials.signature is SCValXdr.Void)
    }

    @Test
    fun testAttachDelegatesCopiesAddressAndNonce() {
        val entry = goldenLegacyEntry()
        val result = Auth.attachDelegates(
            entry, EXPIRATION, listOf(DelegateDescriptor(address = DELEGATE_ACCOUNT))
        )
        val creds = (result.credentials as SorobanCredentialsXdr.AddressWithDelegates).value
        assertEquals(NONCE, creds.addressCredentials.nonce.value)

        val srcCreds = (entry.credentials as SorobanCredentialsXdr.Address).value
        val srcAddressBytes = srcCreds.address.toXdrBytes()
        val dstAddressBytes = creds.addressCredentials.address.toXdrBytes()
        assertContentEquals(srcAddressBytes, dstAddressBytes)
    }

    @Test
    fun testAttachDelegatesSetsExpiration() {
        val entry = goldenLegacyEntry()
        val result = Auth.attachDelegates(
            entry, EXPIRATION, listOf(DelegateDescriptor(address = DELEGATE_ACCOUNT))
        )
        val creds = (result.credentials as SorobanCredentialsXdr.AddressWithDelegates).value
        assertEquals(EXPIRATION.toUInt(), creds.addressCredentials.signatureExpirationLedger.value)
    }

    // ========================================================================
    // Delegate sorting: XDR bytes (account sorts before contract)
    // ========================================================================

    @Test
    fun testDelegateSortingAccountSortsBeforeContract() {
        // Per XDR encoding: SC_ADDRESS_TYPE_ACCOUNT discriminant = 0,
        // SC_ADDRESS_TYPE_CONTRACT discriminant = 1.
        // Account sorts BEFORE contract in XDR byte order.
        // Strkey string order is "C..." < "G..." so strkey would invert this.
        val accountAddress = SIGNER_ACCOUNT   // G... address
        val contractAddress = CONTRACT_ID     // C... address

        val entry = goldenLegacyEntry()
        val result = Auth.attachDelegates(
            entry,
            EXPIRATION,
            // Deliberately provide contract first, account second; expect sort to fix order
            listOf(
                DelegateDescriptor(address = contractAddress),
                DelegateDescriptor(address = accountAddress)
            )
        )

        val creds = (result.credentials as SorobanCredentialsXdr.AddressWithDelegates).value
        assertEquals(2, creds.delegates.size)

        // After sorting by XDR bytes, account address must come first
        val firstIsAccount = creds.delegates[0].address is SCAddressXdr.AccountId
        assertTrue(firstIsAccount, "Account address (G...) must sort before contract address (C...) by XDR bytes")
    }

    @Test
    fun testDelegateSortingByXdrBytesNotStrkey() {
        // Verify that strkey order would produce the wrong result but XDR order is correct.
        // strkey: "C..." < "G..." alphabetically, so strkey sort puts CONTRACT first.
        // XDR byte sort puts ACCOUNT first (discriminant 0 < 1).
        val accountAddrStr = SIGNER_ACCOUNT
        val contractAddrStr = CONTRACT_ID

        // Confirm strkey lexicographic order puts contract first (to prove we're not using strkey)
        assertTrue(contractAddrStr < accountAddrStr, "Precondition: C... < G... as strings")

        // Use compareAddressByXdrBytes directly
        val accountXdr = Address(accountAddrStr).toSCAddress()
        val contractXdr = Address(contractAddrStr).toSCAddress()
        val cmp = compareAddressByXdrBytes(accountXdr, contractXdr)
        assertTrue(cmp < 0, "Account address must sort before contract address by XDR bytes")
    }

    @Test
    fun testDuplicateDelegateAddressInSameArrayThrows() {
        val entry = goldenLegacyEntry()
        assertFailsWith<IllegalArgumentException> {
            Auth.attachDelegates(
                entry,
                EXPIRATION,
                listOf(
                    DelegateDescriptor(address = DELEGATE_ACCOUNT),
                    DelegateDescriptor(address = DELEGATE_ACCOUNT) // duplicate
                )
            )
        }
    }

    @Test
    fun testSortAndValidateDelegatesRejectsDuplicateAddress() {
        // Two nodes carrying byte-identical addresses in one array must be rejected.
        // The address decodes cleanly, so the message names the StrKey form.
        val addr = Address(DELEGATE_ACCOUNT).toSCAddress()
        val a = SorobanDelegateSignatureXdr(addr, Scv.toVoid(), emptyList())
        val b = SorobanDelegateSignatureXdr(addr, Scv.toVoid(), emptyList())
        val ex = assertFailsWith<IllegalArgumentException> {
            sortAndValidateDelegates(listOf(a, b))
        }
        assertTrue(ex.message!!.contains("Duplicate delegate address"), "got: ${ex.message}")
        assertTrue(ex.message!!.contains(DELEGATE_ACCOUNT), "message must name the duplicate address; got: ${ex.message}")
    }

    @Test
    fun testDelegateDescriptorToXdrRejectsMuxedAddress() {
        val ex = assertFailsWith<IllegalArgumentException> {
            DelegateDescriptor(address = MUXED_ADDRESS).toXdr()
        }
        assertTrue(ex.message!!.contains("Muxed"), "message must name muxed rejection; got: ${ex.message}")
    }

    @Test
    fun testSameAddressAtDifferentLevelsIsAccepted() {
        // The same address may appear at different nesting levels — only within-array
        // duplicates are forbidden.
        val entry = goldenLegacyEntry()
        // top delegate = DELEGATE_ACCOUNT with a nested delegate also = DELEGATE_ACCOUNT
        val result = Auth.attachDelegates(
            entry,
            EXPIRATION,
            listOf(
                DelegateDescriptor(
                    address = DELEGATE_ACCOUNT,
                    nestedDelegates = listOf(
                        DelegateDescriptor(address = DELEGATE_ACCOUNT)
                    )
                )
            )
        )
        assertTrue(result.credentials is SorobanCredentialsXdr.AddressWithDelegates)
    }

    @Test
    fun testEmptyDelegateArrayIsValid() {
        val entry = goldenLegacyEntry()
        val result = Auth.attachDelegates(entry, EXPIRATION, emptyList())
        assertTrue(result.credentials is SorobanCredentialsXdr.AddressWithDelegates)
        val creds = (result.credentials as SorobanCredentialsXdr.AddressWithDelegates).value
        assertTrue(creds.delegates.isEmpty())
    }

    // ========================================================================
    // XDR decode depth guard
    // ========================================================================

    @Test
    fun testDepthGuardRejectsTreeDeeperThanCap() {
        // Build a SorobanDelegateSignatureXdr tree 130 levels deep by XDR round-trip.
        // The decode must throw when it encounters the 129th recursion (> 128).
        val leafAddress = Address(DELEGATE_ACCOUNT).toSCAddress()
        var deepNode = SorobanDelegateSignatureXdr(
            address = leafAddress,
            signature = Scv.toVoid(),
            nestedDelegates = emptyList()
        )
        // Build 130 levels deep
        repeat(130) {
            deepNode = SorobanDelegateSignatureXdr(
                address = leafAddress,
                signature = Scv.toVoid(),
                nestedDelegates = listOf(deepNode)
            )
        }

        // Encode to bytes
        val writer = XdrWriter()
        deepNode.encode(writer)
        val bytes = writer.toByteArray()

        // Decode must throw (exceeds cap of 128)
        val reader = XdrReader(bytes)
        assertFailsWith<IllegalArgumentException> {
            SorobanDelegateSignatureXdr.decode(reader)
        }
    }

    @Test
    fun testDepthGuardAcceptsTreeAtOrBelowCap() {
        // A 2-level deep tree must decode without error
        val leafAddress = Address(DELEGATE_ACCOUNT).toSCAddress()
        val level1 = SorobanDelegateSignatureXdr(leafAddress, Scv.toVoid(), emptyList())
        val level0 = SorobanDelegateSignatureXdr(leafAddress, Scv.toVoid(), listOf(level1))

        val writer = XdrWriter()
        level0.encode(writer)
        val bytes = writer.toByteArray()
        val reader = XdrReader(bytes)
        val decoded = SorobanDelegateSignatureXdr.decode(reader)

        assertEquals(1, decoded.nestedDelegates.size)
        assertEquals(0, decoded.nestedDelegates[0].nestedDelegates.size)
    }

    @Test
    fun testDelegateTraversalCapRejection() {
        // A tree at depth > DELEGATE_TRAVERSAL_CAP must cause traversal to throw
        val leafAddress = Address(DELEGATE_ACCOUNT).toSCAddress()
        var deepNode = SorobanDelegateSignatureXdr(leafAddress, Scv.toVoid(), emptyList())
        // Build manually to depth 130 without encoding/decoding (to bypass decode cap)
        // We can't actually build deeper than decode cap via encoding, so we test
        // the traversal cap independently by constructing the tree in-memory.
        // Note: building 130 levels in-memory is fine; the cap only fires on decode.
        repeat(130) {
            deepNode = SorobanDelegateSignatureXdr(leafAddress, Scv.toVoid(), listOf(deepNode))
        }

        val targetBytes = leafAddress.toXdrBytes()
        val error = assertFailsWith<IllegalArgumentException> {
            findDelegateNodes(listOf(deepNode), targetBytes)
        }
        // Traversal and descriptor conversion raise different messages; pinning the exact one
        // keeps this test distinguishable from testDelegateDescriptorToXdrRejectsTreeDeeperThanCap
        assertEquals("Delegate tree traversal depth 129 exceeds cap 128", error.message)
    }

    @Test
    fun testMapMatchingDelegatesRejectsTreeDeeperThanCap() {
        val leafAddress = Address(DELEGATE_ACCOUNT).toSCAddress()
        var deepNode = SorobanDelegateSignatureXdr(leafAddress, Scv.toVoid(), emptyList())
        repeat(130) {
            deepNode = SorobanDelegateSignatureXdr(leafAddress, Scv.toVoid(), listOf(deepNode))
        }
        val targetBytes = leafAddress.toXdrBytes()
        val error = assertFailsWith<IllegalArgumentException> {
            listOf(deepNode).mapMatchingDelegates(targetBytes) { it }
        }
        assertEquals("Delegate tree traversal depth 129 exceeds cap 128", error.message)
    }

    @Test
    fun testDelegateDescriptorToXdrRejectsTreeDeeperThanCap() {
        var descriptor = DelegateDescriptor(address = DELEGATE_ACCOUNT)
        repeat(130) {
            descriptor = DelegateDescriptor(
                address = DELEGATE_ACCOUNT,
                nestedDelegates = listOf(descriptor)
            )
        }
        val error = assertFailsWith<IllegalArgumentException> {
            descriptor.toXdr()
        }
        // The descriptor path reports itself, not the traversal cap
        assertEquals("Delegate descriptor depth 129 exceeds cap 128", error.message)
    }

    // ========================================================================
    // Entry-level XDR round-trip
    // ========================================================================

    @Test
    fun testV2EntryXdrRoundTrip() {
        val entry = goldenV2Entry()
        val bytes = XdrWriter().also { entry.encode(it) }.toByteArray()
        val decoded = SorobanAuthorizationEntryXdr.decode(XdrReader(bytes))
        assertTrue(decoded.credentials is SorobanCredentialsXdr.AddressV2)
        val creds = (decoded.credentials as SorobanCredentialsXdr.AddressV2).value
        assertEquals(NONCE, creds.nonce.value)
    }

    @Test
    fun testWithDelegatesEntryXdrRoundTrip() {
        val entry = goldenLegacyEntry()
        val withDelegates = Auth.attachDelegates(
            entry,
            EXPIRATION,
            listOf(
                DelegateDescriptor(
                    address = DELEGATE_ACCOUNT,
                    nestedDelegates = listOf(DelegateDescriptor(address = CONTRACT_ID))
                )
            )
        )

        val bytes = XdrWriter().also { withDelegates.encode(it) }.toByteArray()
        val decoded = SorobanAuthorizationEntryXdr.decode(XdrReader(bytes))

        assertTrue(decoded.credentials is SorobanCredentialsXdr.AddressWithDelegates)
        val creds = (decoded.credentials as SorobanCredentialsXdr.AddressWithDelegates).value
        assertEquals(1, creds.delegates.size)
        assertEquals(1, creds.delegates[0].nestedDelegates.size)
    }
}
