package com.soneso.stellar.sdk.unitTests.smartaccount.oz

import com.soneso.stellar.sdk.KeyPair
import com.soneso.stellar.sdk.Network
import com.soneso.stellar.sdk.StrKey
import com.soneso.stellar.sdk.crypto.getSha256Crypto
import com.soneso.stellar.sdk.smartaccount.core.SmartAccountUtils
import com.soneso.stellar.sdk.smartaccount.oz.OZSmartAccountConfig
import com.soneso.stellar.sdk.xdr.ContractIDPreimageFromAddressXdr
import com.soneso.stellar.sdk.xdr.ContractIDPreimageXdr
import com.soneso.stellar.sdk.xdr.HashIDPreimageContractIDXdr
import com.soneso.stellar.sdk.xdr.HashIDPreimageXdr
import com.soneso.stellar.sdk.xdr.HashXdr
import com.soneso.stellar.sdk.xdr.SCAddressXdr
import com.soneso.stellar.sdk.xdr.Uint256Xdr
import com.soneso.stellar.sdk.xdr.XdrWriter
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Deterministic derivation tests for the Smart Account Kit.
 *
 * Verifies deployer keypair and contract address derivation using the algorithm:
 *
 * 1. Deployer keypair derivation from the shared seed string
 *    ("openzeppelin-smart-account-kit")
 *
 * 2. Contract address derivation from credential IDs, deployer keys, and network
 *    passphrases, using the algorithm:
 *    a. salt = SHA-256(credentialId)
 *    b. networkId = SHA-256(networkPassphrase)
 *    c. deployer SCAddress = SC_ADDRESS_TYPE_ACCOUNT(ed25519PublicKey)
 *    d. preimage = HashIdPreimage::ContractId {
 *           networkId,
 *           ContractIdPreimage::FromAddress { address: deployer, salt }
 *       }
 *    e. contractId = StrKey.encodeContract(SHA-256(XDR(preimage)))
 */
class DeterministicDerivationTest {

    // MARK: - Deployer Keypair Constants

    /**
     * The fixed seed string used to derive the deterministic deployer keypair.
     *
     * OZSmartAccountConfig.createDefaultDeployer():
     *   val seedHash = getSha256Crypto().hash("openzeppelin-smart-account-kit".encodeToByteArray())
     *   KeyPair.fromSecretSeed(seedHash)
     */
    private val deployerSeedString = "openzeppelin-smart-account-kit"

    /**
     * Expected SHA-256 hash of the seed string (hex-encoded).
     * This 32-byte value is the raw Ed25519 secret seed used for deployer derivation.
     *
     * Computed as: SHA-256(UTF-8("openzeppelin-smart-account-kit"))
     */
    private val expectedSeedHashHex =
        "4ff058c7843b35e5cbcb54b5f7e8f3be0ffb46a7c9aa1d3339cb1c473349566d"

    /**
     * Expected deployer G-address derived from the seed hash.
     * This value is used to verify deterministic derivation from the seed string.
     *
     * Derived by: SHA-256("openzeppelin-smart-account-kit") -> fromSecretSeed -> publicKey
     */
    private val expectedDeployerAddress =
        "GAAH4OT36RRCCAGKARGPN2HLHT2NOBVFHO4GUHA6CF7UKQ4MMV24WQ4N"

    // MARK: - Contract Address Derivation Constants

    /**
     * Deployer public key used in contract address test vectors.
     * This is a well-known test key (all zeros) for reproducible results.
     */
    private val testDeployer1 = "GAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAWHF"

    /**
     * Second deployer key for cross-deployer tests.
     */
    private val testDeployer2 = "GA7QYNF7SOWQ3GLR2BGMZEHXAVIRZA4KVWLTJJFC7MGXUA74P7UJVSGZ"

    /**
     * Third deployer key.
     */
    private val testDeployer3 = "GDUKMGUGDZQK6YHYA5Z6AY2G4XDSZPSZ3SW5UN3ARVMO6QSRDWP5YLEX"

    // MARK: - Deployer Keypair Determinism Tests

    /**
     * Verifies that SHA-256 of the seed string produces the expected hash.
     *
     * This is the foundation of deployer derivation: both SDKs hash the same
     * seed string with SHA-256 to produce a 32-byte Ed25519 secret seed.
     */
    @Test
    fun testDeployerSeedHash_matchesExpected() = runTest {
        val seedBytes = deployerSeedString.encodeToByteArray()
        val hash = getSha256Crypto().hash(seedBytes)

        assertEquals(32, hash.size, "SHA-256 hash must be 32 bytes")
        assertEquals(
            expectedSeedHashHex,
            hash.toHex(),
            "SHA-256 hash of '$deployerSeedString' must match expected value"
        )
    }

    /**
     * Verifies that the deployer keypair derived from the seed hash produces
     * the expected G-address.
     *
     * This is a correctness test: the deployer G-address is a fixed point derived
     * deterministically from the seed string. Contract address derivation depends
     * on this value being stable.
     */
    @Test
    fun testDeployerAddress_matchesTypeScriptSdk() = runTest {
        val seedBytes = deployerSeedString.encodeToByteArray()
        val seedHash = getSha256Crypto().hash(seedBytes)
        val deployer = KeyPair.fromSecretSeed(seedHash)

        assertEquals(
            expectedDeployerAddress,
            deployer.getAccountId(),
            "Deployer G-address must match expected derivation"
        )
    }

    /**
     * Verifies that OZSmartAccountConfig.createDefaultDeployer() produces the
     * same deployer as manual derivation, and matches the expected G-address.
     */
    @Test
    fun testCreateDefaultDeployer_matchesExpectedAddress() = runTest {
        val deployer = OZSmartAccountConfig.createDefaultDeployer()

        assertNotNull(deployer, "Default deployer must not be null")
        assertTrue(deployer.canSign(), "Default deployer must have signing capability")
        assertEquals(
            expectedDeployerAddress,
            deployer.getAccountId(),
            "createDefaultDeployer() G-address must match expected deployer address"
        )
    }

    /**
     * Verifies that createDefaultDeployer() is deterministic -- calling it
     * multiple times always produces the same keypair.
     */
    @Test
    fun testCreateDefaultDeployer_isDeterministic() = runTest {
        val deployer1 = OZSmartAccountConfig.createDefaultDeployer()
        val deployer2 = OZSmartAccountConfig.createDefaultDeployer()

        assertEquals(
            deployer1.getAccountId(),
            deployer2.getAccountId(),
            "createDefaultDeployer() must be deterministic"
        )
    }

    /**
     * Verifies that the deployer derived via createDefaultDeployer() matches
     * the deployer derived manually from the raw seed hash. This ensures the
     * high-level API and the low-level derivation are consistent.
     */
    @Test
    fun testCreateDefaultDeployer_matchesManualDerivation() = runTest {
        val defaultDeployer = OZSmartAccountConfig.createDefaultDeployer()

        val seedHash = getSha256Crypto().hash(deployerSeedString.encodeToByteArray())
        val manualDeployer = KeyPair.fromSecretSeed(seedHash)

        assertEquals(
            manualDeployer.getAccountId(),
            defaultDeployer.getAccountId(),
            "createDefaultDeployer() must match manual SHA-256 + fromSecretSeed derivation"
        )
    }

    // MARK: - Contract Address Derivation Test Vector 1: Short Credential ID (16 bytes)

    @Test
    fun testDeriveContractAddress_shortCredentialId_testnet() = runTest {
        val credentialId = ByteArray(16) { (it + 1).toByte() } // 0x01, 0x02, ..., 0x10

        val contractAddress = SmartAccountUtils.deriveContractAddress(
            credentialId = credentialId,
            deployerPublicKey = testDeployer1,
            networkPassphrase = Network.TESTNET.networkPassphrase
        )

        // Verify the output is a valid contract address
        assertTrue(contractAddress.startsWith("C"), "Contract address must start with C")
        assertEquals(56, contractAddress.length, "Contract address must be 56 characters")
        assertTrue(StrKey.isValidContract(contractAddress), "Contract address must be valid StrKey")

        // Verify against manual step-by-step derivation
        val manualAddress = manuallyDeriveContractAddress(
            credentialId = credentialId,
            deployerPublicKey = testDeployer1,
            networkPassphrase = Network.TESTNET.networkPassphrase
        )

        assertEquals(
            manualAddress,
            contractAddress,
            "SmartAccountUtils.deriveContractAddress must match manual XDR derivation for short credential ID"
        )
    }

    // MARK: - Contract Address Derivation Test Vector 2: Medium Credential ID (32 bytes)

    @Test
    fun testDeriveContractAddress_mediumCredentialId_testnet() = runTest {
        val credentialId = ByteArray(32) { (it * 3 + 7).toByte() }

        val contractAddress = SmartAccountUtils.deriveContractAddress(
            credentialId = credentialId,
            deployerPublicKey = testDeployer2,
            networkPassphrase = Network.TESTNET.networkPassphrase
        )

        assertTrue(contractAddress.startsWith("C"))
        assertEquals(56, contractAddress.length)
        assertTrue(StrKey.isValidContract(contractAddress))

        val manualAddress = manuallyDeriveContractAddress(
            credentialId = credentialId,
            deployerPublicKey = testDeployer2,
            networkPassphrase = Network.TESTNET.networkPassphrase
        )

        assertEquals(
            manualAddress,
            contractAddress,
            "SmartAccountUtils.deriveContractAddress must match manual XDR derivation for medium credential ID"
        )
    }

    // MARK: - Contract Address Derivation Test Vector 3: Long Credential ID (128 bytes, typical WebAuthn)

    @Test
    fun testDeriveContractAddress_longCredentialId_testnet() = runTest {
        // WebAuthn credential IDs can be 32-128+ bytes
        val credentialId = ByteArray(128) { (it * 17 + 42).toByte() }

        val contractAddress = SmartAccountUtils.deriveContractAddress(
            credentialId = credentialId,
            deployerPublicKey = testDeployer3,
            networkPassphrase = Network.TESTNET.networkPassphrase
        )

        assertTrue(contractAddress.startsWith("C"))
        assertEquals(56, contractAddress.length)
        assertTrue(StrKey.isValidContract(contractAddress))

        val manualAddress = manuallyDeriveContractAddress(
            credentialId = credentialId,
            deployerPublicKey = testDeployer3,
            networkPassphrase = Network.TESTNET.networkPassphrase
        )

        assertEquals(
            manualAddress,
            contractAddress,
            "SmartAccountUtils.deriveContractAddress must match manual XDR derivation for long credential ID"
        )
    }

    // MARK: - Contract Address Derivation Test Vector 4: Public Network

    @Test
    fun testDeriveContractAddress_publicNetwork() = runTest {
        val credentialId = ByteArray(16) { (it + 1).toByte() }

        val contractAddress = SmartAccountUtils.deriveContractAddress(
            credentialId = credentialId,
            deployerPublicKey = testDeployer1,
            networkPassphrase = Network.PUBLIC.networkPassphrase
        )

        assertTrue(contractAddress.startsWith("C"))
        assertEquals(56, contractAddress.length)
        assertTrue(StrKey.isValidContract(contractAddress))

        val manualAddress = manuallyDeriveContractAddress(
            credentialId = credentialId,
            deployerPublicKey = testDeployer1,
            networkPassphrase = Network.PUBLIC.networkPassphrase
        )

        assertEquals(
            manualAddress,
            contractAddress,
            "SmartAccountUtils.deriveContractAddress must match manual XDR derivation on public network"
        )
    }

    // MARK: - Contract Address Derivation Test Vector 5: Single-byte Credential ID (minimum)

    @Test
    fun testDeriveContractAddress_singleByteCredentialId() = runTest {
        val credentialId = byteArrayOf(0xFF.toByte())

        val contractAddress = SmartAccountUtils.deriveContractAddress(
            credentialId = credentialId,
            deployerPublicKey = testDeployer1,
            networkPassphrase = Network.TESTNET.networkPassphrase
        )

        assertTrue(contractAddress.startsWith("C"))
        assertEquals(56, contractAddress.length)
        assertTrue(StrKey.isValidContract(contractAddress))

        val manualAddress = manuallyDeriveContractAddress(
            credentialId = credentialId,
            deployerPublicKey = testDeployer1,
            networkPassphrase = Network.TESTNET.networkPassphrase
        )

        assertEquals(
            manualAddress,
            contractAddress,
            "SmartAccountUtils.deriveContractAddress must match manual XDR derivation for single-byte credential ID"
        )
    }

    // MARK: - Intermediate Value Verification

    @Test
    fun testGetContractSalt_matchesSha256OfCredentialId() = runTest {
        val credentialId = ByteArray(16) { (it + 1).toByte() }
        val sha256 = getSha256Crypto()

        val salt = SmartAccountUtils.getContractSalt(credentialId)
        val expectedSalt = sha256.hash(credentialId)

        assertEquals(32, salt.size, "Salt must be 32 bytes (SHA-256 output)")
        assertEquals(
            expectedSalt.toHex(),
            salt.toHex(),
            "getContractSalt must return SHA-256(credentialId)"
        )
    }

    @Test
    fun testGetContractSalt_differentCredentialIds_produceDifferentSalts() = runTest {
        val credentialId1 = ByteArray(16) { 0x01 }
        val credentialId2 = ByteArray(16) { 0x02 }
        val credentialId3 = ByteArray(32) { 0x01 }

        val salt1 = SmartAccountUtils.getContractSalt(credentialId1)
        val salt2 = SmartAccountUtils.getContractSalt(credentialId2)
        val salt3 = SmartAccountUtils.getContractSalt(credentialId3)

        assertNotEquals(salt1.toHex(), salt2.toHex(), "Different credential content must produce different salts")
        assertNotEquals(salt1.toHex(), salt3.toHex(), "Different credential length must produce different salts")
    }

    // MARK: - Cross-Input Variation Tests

    @Test
    fun testDeriveContractAddress_differentCredentialIds_differentAddresses() = runTest {
        val credentialId1 = ByteArray(16) { 0xAA.toByte() }
        val credentialId2 = ByteArray(16) { 0xBB.toByte() }
        val credentialId3 = ByteArray(32) { 0xAA.toByte() }

        val address1 = SmartAccountUtils.deriveContractAddress(
            credentialId1, testDeployer1, Network.TESTNET.networkPassphrase
        )
        val address2 = SmartAccountUtils.deriveContractAddress(
            credentialId2, testDeployer1, Network.TESTNET.networkPassphrase
        )
        val address3 = SmartAccountUtils.deriveContractAddress(
            credentialId3, testDeployer1, Network.TESTNET.networkPassphrase
        )

        assertNotEquals(address1, address2, "Different credential content must produce different addresses")
        assertNotEquals(address1, address3, "Different credential length must produce different addresses")
        assertNotEquals(address2, address3)
    }

    @Test
    fun testDeriveContractAddress_differentDeployers_differentAddresses() = runTest {
        val credentialId = ByteArray(16) { 0x42 }

        val address1 = SmartAccountUtils.deriveContractAddress(
            credentialId, testDeployer1, Network.TESTNET.networkPassphrase
        )
        val address2 = SmartAccountUtils.deriveContractAddress(
            credentialId, testDeployer2, Network.TESTNET.networkPassphrase
        )
        val address3 = SmartAccountUtils.deriveContractAddress(
            credentialId, testDeployer3, Network.TESTNET.networkPassphrase
        )

        assertNotEquals(address1, address2, "Different deployers must produce different addresses")
        assertNotEquals(address1, address3)
        assertNotEquals(address2, address3)
    }

    @Test
    fun testDeriveContractAddress_differentNetworks_differentAddresses() = runTest {
        val credentialId = ByteArray(16) { 0x42 }

        val testnetAddress = SmartAccountUtils.deriveContractAddress(
            credentialId, testDeployer1, Network.TESTNET.networkPassphrase
        )
        val publicAddress = SmartAccountUtils.deriveContractAddress(
            credentialId, testDeployer1, Network.PUBLIC.networkPassphrase
        )

        assertNotEquals(
            testnetAddress,
            publicAddress,
            "Different networks must produce different addresses"
        )
    }

    // MARK: - XDR Structure Verification

    @Test
    fun testXdrPreimageStructure_envelopeTypeContractId() = runTest {
        // Verify that the XDR preimage starts with the ENVELOPE_TYPE_CONTRACT_ID discriminant (8)
        val credentialId = ByteArray(16) { 0x42 }
        val sha256 = getSha256Crypto()

        val salt = sha256.hash(credentialId)
        val networkId = sha256.hash(Network.TESTNET.networkPassphrase.encodeToByteArray())
        val keyPair = KeyPair.fromAccountId(testDeployer1)
        val deployerAddress = SCAddressXdr.AccountId(keyPair.getXdrAccountId())

        val preimage = HashIDPreimageXdr.ContractID(
            HashIDPreimageContractIDXdr(
                networkId = HashXdr(networkId),
                contractIdPreimage = ContractIDPreimageXdr.FromAddress(
                    ContractIDPreimageFromAddressXdr(
                        address = deployerAddress,
                        salt = Uint256Xdr(salt)
                    )
                )
            )
        )

        val writer = XdrWriter()
        preimage.encode(writer)
        val xdrBytes = writer.toByteArray()

        // First 4 bytes are the EnvelopeType discriminant (big-endian int32)
        // ENVELOPE_TYPE_CONTRACT_ID = 8
        assertEquals(0x00, xdrBytes[0].toInt() and 0xFF, "Byte 0 of envelope type")
        assertEquals(0x00, xdrBytes[1].toInt() and 0xFF, "Byte 1 of envelope type")
        assertEquals(0x00, xdrBytes[2].toInt() and 0xFF, "Byte 2 of envelope type")
        assertEquals(0x08, xdrBytes[3].toInt() and 0xFF, "Byte 3 of envelope type = 8 (CONTRACT_ID)")

        // Bytes 4-35 are the networkId (32 bytes SHA-256 hash)
        val encodedNetworkId = xdrBytes.copyOfRange(4, 36)
        assertEquals(
            networkId.toHex(),
            encodedNetworkId.toHex(),
            "Network ID in XDR must match SHA-256 of network passphrase"
        )

        // Byte 36-39 is the ContractIDPreimageType discriminant
        // CONTRACT_ID_PREIMAGE_FROM_ADDRESS = 0
        assertEquals(0x00, xdrBytes[36].toInt() and 0xFF, "Byte 0 of preimage type")
        assertEquals(0x00, xdrBytes[37].toInt() and 0xFF, "Byte 1 of preimage type")
        assertEquals(0x00, xdrBytes[38].toInt() and 0xFF, "Byte 2 of preimage type")
        assertEquals(0x00, xdrBytes[39].toInt() and 0xFF, "Byte 3 of preimage type = 0 (FROM_ADDRESS)")
    }

    @Test
    fun testXdrPreimageSize() = runTest {
        // The XDR preimage has a deterministic size:
        // 4 (EnvelopeType) + 32 (networkId) +
        // 4 (ContractIdPreimageType) + 4 (SCAddressType) + 4 (PublicKeyType) + 32 (Ed25519 key) +
        // 32 (salt) = 112 bytes
        val credentialId = ByteArray(16) { 0x42 }
        val sha256 = getSha256Crypto()

        val salt = sha256.hash(credentialId)
        val networkId = sha256.hash(Network.TESTNET.networkPassphrase.encodeToByteArray())
        val keyPair = KeyPair.fromAccountId(testDeployer1)
        val deployerAddress = SCAddressXdr.AccountId(keyPair.getXdrAccountId())

        val preimage = HashIDPreimageXdr.ContractID(
            HashIDPreimageContractIDXdr(
                networkId = HashXdr(networkId),
                contractIdPreimage = ContractIDPreimageXdr.FromAddress(
                    ContractIDPreimageFromAddressXdr(
                        address = deployerAddress,
                        salt = Uint256Xdr(salt)
                    )
                )
            )
        )

        val writer = XdrWriter()
        preimage.encode(writer)
        val xdrBytes = writer.toByteArray()

        assertEquals(112, xdrBytes.size, "XDR preimage for address-based contract ID must be 112 bytes")
    }

    // MARK: - Determinism Tests

    @Test
    fun testDeriveContractAddress_deterministic_multipleInvocations() = runTest {
        val credentialId = ByteArray(64) { (it * 5).toByte() }

        val results = mutableListOf<String>()
        for (i in 0 until 5) {
            results.add(
                SmartAccountUtils.deriveContractAddress(
                    credentialId = credentialId,
                    deployerPublicKey = testDeployer2,
                    networkPassphrase = Network.TESTNET.networkPassphrase
                )
            )
        }

        val first = results[0]
        for (i in 1 until results.size) {
            assertEquals(first, results[i], "Derivation must be deterministic (invocation $i)")
        }
    }

    // MARK: - Private Helpers

    /**
     * Manually derives a contract address using raw XDR construction and SHA-256.
     *
     * Algorithm:
     * 1. salt = SHA-256(credentialId)
     * 2. networkId = SHA-256(networkPassphrase)
     * 3. Construct HashIdPreimage::ContractId { networkId, FromAddress { deployer, salt } }
     * 4. contractId = StrKey.encodeContract(SHA-256(XDR(preimage)))
     */
    private suspend fun manuallyDeriveContractAddress(
        credentialId: ByteArray,
        deployerPublicKey: String,
        networkPassphrase: String
    ): String {
        val sha256 = getSha256Crypto()

        // Step 1: salt = SHA-256(credentialId)
        val salt = sha256.hash(credentialId)

        // Step 2: networkId = SHA-256(networkPassphrase as UTF-8)
        val networkId = sha256.hash(networkPassphrase.encodeToByteArray())

        // Step 3: deployer SCAddress = SC_ADDRESS_TYPE_ACCOUNT(ed25519PublicKey)
        val keyPair = KeyPair.fromAccountId(deployerPublicKey)
        val deployerAddress = SCAddressXdr.AccountId(keyPair.getXdrAccountId())

        // Step 4: Construct the XDR preimage
        val preimage = HashIDPreimageXdr.ContractID(
            HashIDPreimageContractIDXdr(
                networkId = HashXdr(networkId),
                contractIdPreimage = ContractIDPreimageXdr.FromAddress(
                    ContractIDPreimageFromAddressXdr(
                        address = deployerAddress,
                        salt = Uint256Xdr(salt)
                    )
                )
            )
        )

        // Step 5: XDR encode the preimage
        val writer = XdrWriter()
        preimage.encode(writer)
        val xdrBytes = writer.toByteArray()

        // Step 6: contractIdBytes = SHA-256(xdrBytes)
        val contractIdBytes = sha256.hash(xdrBytes)

        // Step 7: Encode as C-address
        return StrKey.encodeContract(contractIdBytes)
    }

    /**
     * Converts byte array to hex string for comparison.
     */
    private fun ByteArray.toHex(): String = joinToString("") { byte ->
        (byte.toInt() and 0xFF).toString(16).padStart(2, '0')
    }
}
