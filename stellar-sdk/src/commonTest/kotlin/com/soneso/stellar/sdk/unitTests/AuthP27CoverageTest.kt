package com.soneso.stellar.sdk.unitTests

import com.soneso.stellar.sdk.*
import com.soneso.stellar.sdk.scval.Scv
import com.soneso.stellar.sdk.xdr.*
import kotlinx.coroutines.test.runTest
import kotlin.test.*

/**
 * Protocol 27 authorization tests covering the branches of Auth.kt, DelegateTree.kt and
 * the generated WITH_ADDRESS / delegate XDR types that the golden-vector suite does not
 * reach: the base64 + custom-Signer overload, the forAddress top-level routing on the
 * legacy ADDRESS arm, the muxed-address rejection paths, the WITH_DELEGATES credential
 * mutation and traversal-cap guards, and the decode side of the new preimage type.
 *
 * Fixed keys/nonces and golden-vector constants keep the assertions deterministic and
 * cross-SDK consistent.
 */
class AuthP27CoverageTest {

    companion object {
        private val NETWORK = Network.TESTNET

        private const val SIGNER_SEED = "SDJHRQF4GCMIIKAAAQ6IHY42X73FQFLHUULAPSKKD4DFDM7UXWWCRHBE"
        private const val SIGNER_ACCOUNT = "GCZHXL5HXQX5ABDM26LHYRCQZ5OJFHLOPLZX47WEBP3V2PF5AVFK2A5D"

        private const val CONTRACT_ID = "CA3D5KRYM6CB7OWQ6TWYRR3Z4T7GNZLKERYNZGGA5SOAOPIFY6YQGAXE"
        private const val DELEGATE_ACCOUNT = "GADBBY4WFXKKFJ7CMTG3J5YAUXMQDBILRQ6W3U5IWN5TQFZU4MWZ5T4K"

        private const val NONCE = 123456789101112L
        private const val EXPIRATION = 4242L

        // A valid muxed (M...) address derived deterministically from SIGNER_ACCOUNT
        // with a fixed multiplexing id.
        val MUXED_ADDRESS: String = MuxedAccount(SIGNER_ACCOUNT, 42UL).address
    }

    private fun goldenInvocation(): SorobanAuthorizedInvocationXdr =
        SorobanAuthorizedInvocationXdr(
            function = SorobanAuthorizedFunctionXdr.ContractFn(
                InvokeContractArgsXdr(
                    contractAddress = Address(CONTRACT_ID).toSCAddress(),
                    functionName = SCSymbolXdr("hello"),
                    args = listOf(Scv.toUint64(1234UL))
                )
            ),
            subInvocations = emptyList()
        )

    private fun baseCredentials(): SorobanAddressCredentialsXdr =
        SorobanAddressCredentialsXdr(
            address = Address(SIGNER_ACCOUNT).toSCAddress(),
            nonce = Int64Xdr(NONCE),
            signatureExpirationLedger = Uint32Xdr(EXPIRATION.toUInt()),
            signature = Scv.toVoid()
        )

    private fun legacyEntry(): SorobanAuthorizationEntryXdr =
        SorobanAuthorizationEntryXdr(
            credentials = SorobanCredentialsXdr.Address(baseCredentials()),
            rootInvocation = goldenInvocation()
        )

    private fun v2Entry(): SorobanAuthorizationEntryXdr =
        SorobanAuthorizationEntryXdr(
            credentials = SorobanCredentialsXdr.AddressV2(baseCredentials()),
            rootInvocation = goldenInvocation()
        )

    private fun xdrBytes(preimage: HashIDPreimageXdr): ByteArray =
        XdrWriter().also { preimage.encode(it) }.toByteArray()

    /** Returns the raw public-key bytes from the single signature element of [signature]. */
    private fun singleSignaturePublicKey(signature: SCValXdr): ByteArray {
        val vec = (signature as SCValXdr.Vec).value!!.value
        assertEquals(1, vec.size, "expected exactly one signature element")
        val map = (vec[0] as SCValXdr.Map).value!!.value
        val entry = map.find { it.key is SCValXdr.Sym && (it.key as SCValXdr.Sym).value.value == "public_key" }
        return (entry!!.`val` as SCValXdr.Bytes).value.value
    }

    // ========================================================================
    // authorizeEntry base64 + custom Signer overload
    // ========================================================================

    @Test
    fun testAuthorizeEntryBase64WithCustomSignerSignsTopLevel() = runTest {
        val keyPair = KeyPair.fromSecretSeed(SIGNER_SEED)
        val entryB64 = legacyEntry().toXdrBase64()

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
    // forAddress routing on the non-delegate arms (top-level match)
    // ========================================================================

    @Test
    fun testForAddressMatchingTopLevelLegacyAddressArm() = runTest {
        // forAddress == the top-level credential address on a legacy ADDRESS entry.
        // The signature must land on the top-level credentials and the arm is preserved.
        val keyPair = KeyPair.fromSecretSeed(SIGNER_SEED)
        val signed = Auth.authorizeEntry(
            legacyEntry(),
            keyPair,
            EXPIRATION,
            NETWORK,
            Auth.AuthOptions(forAddress = SIGNER_ACCOUNT)
        )

        assertIs<SorobanCredentialsXdr.Address>(signed.credentials)
        val creds = (signed.credentials as SorobanCredentialsXdr.Address).value
        assertContentEquals(keyPair.getPublicKey(), singleSignaturePublicKey(creds.signature))
    }

    @Test
    fun testForAddressMatchingTopLevelV2Arm() = runTest {
        // forAddress == the top-level credential address on an ADDRESS_V2 entry.
        val keyPair = KeyPair.fromSecretSeed(SIGNER_SEED)
        val signed = Auth.authorizeEntry(
            v2Entry(),
            keyPair,
            EXPIRATION,
            NETWORK,
            Auth.AuthOptions(forAddress = SIGNER_ACCOUNT)
        )

        assertIs<SorobanCredentialsXdr.AddressV2>(signed.credentials)
        val creds = (signed.credentials as SorobanCredentialsXdr.AddressV2).value
        assertContentEquals(keyPair.getPublicKey(), singleSignaturePublicKey(creds.signature))
    }

    @Test
    fun testForAddressMuxedThrowsWithValidMuxedAddress() = runTest {
        // A structurally valid M... address must be rejected by signForAddress because
        // muxed addresses are not valid Soroban auth addresses.
        require(Address(MUXED_ADDRESS).addressType == Address.AddressType.MUXED_ACCOUNT)
        val keyPair = KeyPair.fromSecretSeed(SIGNER_SEED)
        val ex = assertFailsWith<IllegalArgumentException> {
            Auth.authorizeEntry(
                legacyEntry(),
                keyPair,
                EXPIRATION,
                NETWORK,
                Auth.AuthOptions(forAddress = MUXED_ADDRESS)
            )
        }
        assertTrue(ex.message!!.contains("Muxed"), "message must name the muxed rejection; got: ${ex.message}")
    }

    // ========================================================================
    // withUpdatedAddressCredentials — Void arm rejection
    // ========================================================================

    @Test
    fun testWithUpdatedAddressCredentialsRejectsVoid() {
        val void: SorobanCredentialsXdr = SorobanCredentialsXdr.Void
        val ex = assertFailsWith<IllegalArgumentException> {
            void.withUpdatedAddressCredentials(baseCredentials())
        }
        assertTrue(ex.message!!.contains("Void"), "message must name Void; got: ${ex.message}")
    }

    // ========================================================================
    // sortAndValidateDelegates — duplicate rejection (decodable-address message)
    // ========================================================================

    @Test
    fun testDuplicateDelegateAddressInArrayThrows() {
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

    // ========================================================================
    // Traversal-cap guards on mapMatchingDelegates and DelegateDescriptor.toXdr
    // ========================================================================

    @Test
    fun testMapMatchingDelegatesRejectsTreeDeeperThanCap() {
        val leafAddress = Address(DELEGATE_ACCOUNT).toSCAddress()
        var deepNode = SorobanDelegateSignatureXdr(leafAddress, Scv.toVoid(), emptyList())
        repeat(130) {
            deepNode = SorobanDelegateSignatureXdr(leafAddress, Scv.toVoid(), listOf(deepNode))
        }
        val targetBytes = leafAddress.toXdrBytes()
        assertFailsWith<IllegalArgumentException> {
            listOf(deepNode).mapMatchingDelegates(targetBytes) { it }
        }
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
        assertFailsWith<IllegalArgumentException> {
            descriptor.toXdr()
        }
    }

    @Test
    fun testDelegateDescriptorToXdrRejectsMuxedAddress() {
        val ex = assertFailsWith<IllegalArgumentException> {
            DelegateDescriptor(address = MUXED_ADDRESS).toXdr()
        }
        assertTrue(ex.message!!.contains("Muxed"), "message must name muxed rejection; got: ${ex.message}")
    }

    // ========================================================================
    // WITH_ADDRESS preimage decode round-trip
    // ========================================================================

    @Test
    fun testWithAddressPreimageEncodeDecodeRoundTrip() = runTest {
        // The encode path is exercised by the golden-vector suite; this asserts the
        // decode side reconstructs every field and selects the WITH_ADDRESS arm.
        val preimage = Auth.buildHashIDPreimage(
            credentials = SorobanCredentialsXdr.AddressV2(baseCredentials()),
            networkId = NETWORK.networkId(),
            invocation = goldenInvocation()
        )
        assertIs<HashIDPreimageXdr.SorobanAuthorizationWithAddress>(preimage)

        val bytes = XdrWriter().also { preimage.encode(it) }.toByteArray()
        val decoded = HashIDPreimageXdr.decode(XdrReader(bytes))

        assertIs<HashIDPreimageXdr.SorobanAuthorizationWithAddress>(decoded)
        val original = (preimage as HashIDPreimageXdr.SorobanAuthorizationWithAddress).value
        val back = (decoded as HashIDPreimageXdr.SorobanAuthorizationWithAddress).value
        assertContentEquals(original.networkId.value, back.networkId.value)
        assertEquals(original.nonce.value, back.nonce.value)
        assertEquals(original.signatureExpirationLedger.value, back.signatureExpirationLedger.value)
        assertContentEquals(original.address.toXdrBytes(), back.address.toXdrBytes())
        assertEquals(NONCE, back.nonce.value)
        assertEquals(EXPIRATION.toUInt(), back.signatureExpirationLedger.value)
    }

    @Test
    fun testWithAddressPreimageDecodedFieldsMatchTopLevelAddress() = runTest {
        // The WITH_ADDRESS preimage binds the top-level credential address; a decoded
        // preimage must carry the SIGNER_ACCOUNT address, never a delegate address.
        val withDelegates = Auth.attachDelegates(
            legacyEntry(),
            EXPIRATION,
            listOf(DelegateDescriptor(address = DELEGATE_ACCOUNT))
        )
        val preimage = Auth.buildHashIDPreimage(
            credentials = withDelegates.credentials,
            networkId = NETWORK.networkId(),
            invocation = withDelegates.rootInvocation
        )
        val bytes = XdrWriter().also { preimage.encode(it) }.toByteArray()
        val decoded = HashIDPreimageXdr.decode(XdrReader(bytes))
        val back = (decoded as HashIDPreimageXdr.SorobanAuthorizationWithAddress).value
        assertContentEquals(
            Address(SIGNER_ACCOUNT).toSCAddress().toXdrBytes(),
            back.address.toXdrBytes()
        )
    }
}
