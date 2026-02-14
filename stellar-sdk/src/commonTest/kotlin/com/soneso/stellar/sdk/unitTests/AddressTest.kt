package com.soneso.stellar.sdk.unitTests

import com.soneso.stellar.sdk.*
import com.soneso.stellar.sdk.xdr.*
import kotlin.test.*

class AddressTest {
    private val accountId = "GA7QYNF7SOWQ3GLR2BGMZEHXAVIRZA4KVWLTJJFC7MGXUA74P7UJVSGZ"
    private val contractId = "CA7QYNF7SOWQ3GLR2BGMZEHXAVIRZA4KVWLTJJFC7MGXUA74P7UJUWDA"
    private val muxedAccountId = "MAQAA5L65LSYH7CQ3VTJ7F3HHLGCL3DSLAR2Y47263D56MNNGHSQSAAAAAAAAAAE2LP26"
    private val claimableBalanceId = "BAAD6DBUX6J22DMZOHIEZTEQ64CVCHEDRKWZONFEUL5Q26QD7R76RGR4TU"
    private val liquidityPoolId = "LA7QYNF7SOWQ3GLR2BGMZEHXAVIRZA4KVWLTJJFC7MGXUA74P7UJUPJN"
    private val secretKey = "SB65MHFA2Z342DX4FNKHH2KCNR5JRM7GIVTWQLKG5Z6L3AAH4UZLZV4E"
    private val invalidAddress = "GINVALID"

    @Test
    fun testAddressConstructorWithAccountId() {
        val address = Address(accountId)
        assertEquals(accountId, address.toString())
        assertEquals(Address.AddressType.ACCOUNT, address.addressType)
    }

    @Test
    fun testAddressConstructorWithContractId() {
        val address = Address(contractId)
        assertEquals(contractId, address.toString())
        assertEquals(Address.AddressType.CONTRACT, address.addressType)
    }

    @Test
    fun testAddressConstructorWithMuxedAccountId() {
        val address = Address(muxedAccountId)
        assertEquals(muxedAccountId, address.toString())
        assertEquals(Address.AddressType.MUXED_ACCOUNT, address.addressType)
    }

    @Test
    fun testAddressConstructorWithClaimableBalanceId() {
        val address = Address(claimableBalanceId)
        assertEquals(claimableBalanceId, address.toString())
        assertEquals(Address.AddressType.CLAIMABLE_BALANCE, address.addressType)
    }

    @Test
    fun testAddressConstructorWithLiquidityPoolId() {
        val address = Address(liquidityPoolId)
        assertEquals(liquidityPoolId, address.toString())
        assertEquals(Address.AddressType.LIQUIDITY_POOL, address.addressType)
    }

    @Test
    fun testAddressConstructorWithInvalidAddress() {
        val exception = assertFailsWith<IllegalArgumentException> {
            Address(invalidAddress)
        }
        assertEquals("Unsupported address type", exception.message)
    }

    @Test
    fun testAddressConstructorWithSecretKey() {
        val exception = assertFailsWith<IllegalArgumentException> {
            Address(secretKey)
        }
        assertEquals("Unsupported address type", exception.message)
    }

    @Test
    fun testFromAccountBytes() {
        val accountIdBytes = StrKey.decodeEd25519PublicKey(accountId)
        val address = Address.fromAccount(accountIdBytes)
        assertEquals(accountId, address.toString())
        assertEquals(Address.AddressType.ACCOUNT, address.addressType)
    }

    @Test
    fun testFromContractBytes() {
        val contractIdBytes = StrKey.decodeContract(contractId)
        val address = Address.fromContract(contractIdBytes)
        assertEquals(contractId, address.toString())
        assertEquals(Address.AddressType.CONTRACT, address.addressType)
    }

    @Test
    fun testFromMuxedAccountBytes() {
        val muxedAccountIdBytes = StrKey.decodeMed25519PublicKey(muxedAccountId)
        val address = Address.fromMuxedAccount(muxedAccountIdBytes)
        assertEquals(muxedAccountId, address.toString())
        assertEquals(Address.AddressType.MUXED_ACCOUNT, address.addressType)
    }

    @Test
    fun testFromClaimableBalanceBytes() {
        val claimableBalanceIdBytes = StrKey.decodeClaimableBalance(claimableBalanceId)
        val address = Address.fromClaimableBalance(claimableBalanceIdBytes)
        assertEquals(claimableBalanceId, address.toString())
        assertEquals(Address.AddressType.CLAIMABLE_BALANCE, address.addressType)
    }

    @Test
    fun testFromLiquidityPoolBytes() {
        val liquidityPoolIdBytes = StrKey.decodeLiquidityPool(liquidityPoolId)
        val address = Address.fromLiquidityPool(liquidityPoolIdBytes)
        assertEquals(liquidityPoolId, address.toString())
        assertEquals(Address.AddressType.LIQUIDITY_POOL, address.addressType)
    }

    @Test
    fun testAccountToSCAddress() {
        val address = Address(accountId)
        val scAddress = address.toSCAddress()

        assertTrue(scAddress is SCAddressXdr.AccountId)
        val accountIdXdr = scAddress as SCAddressXdr.AccountId
        assertTrue(accountIdXdr.value.value is PublicKeyXdr.Ed25519)
    }

    @Test
    fun testContractToSCAddress() {
        val address = Address(contractId)
        val scAddress = address.toSCAddress()

        assertTrue(scAddress is SCAddressXdr.ContractId)
        val contractIdXdr = scAddress as SCAddressXdr.ContractId
        assertEquals(32, contractIdXdr.value.value.value.size)
    }

    @Test
    fun testMuxedAccountToSCAddress() {
        val address = Address(muxedAccountId)
        val scAddress = address.toSCAddress()

        assertTrue(scAddress is SCAddressXdr.MuxedAccount)
        val muxedAccountXdr = scAddress as SCAddressXdr.MuxedAccount
        assertEquals(32, muxedAccountXdr.value.ed25519.value.size)
    }

    @Test
    fun testClaimableBalanceToSCAddress() {
        val address = Address(claimableBalanceId)
        val scAddress = address.toSCAddress()

        assertTrue(scAddress is SCAddressXdr.ClaimableBalanceId)
        val claimableBalanceIdXdr = scAddress as SCAddressXdr.ClaimableBalanceId
        assertTrue(claimableBalanceIdXdr.value is ClaimableBalanceIDXdr.V0)
    }

    @Test
    fun testLiquidityPoolToSCAddress() {
        val address = Address(liquidityPoolId)
        val scAddress = address.toSCAddress()

        assertTrue(scAddress is SCAddressXdr.LiquidityPoolId)
        val liquidityPoolIdXdr = scAddress as SCAddressXdr.LiquidityPoolId
        assertEquals(32, liquidityPoolIdXdr.value.value.value.size)
    }

    @Test
    fun testFromSCAddressAccount() {
        val address = Address(accountId)
        val scAddress = address.toSCAddress()
        val reconstructed = Address.fromSCAddress(scAddress)

        assertEquals(accountId, reconstructed.toString())
        assertEquals(Address.AddressType.ACCOUNT, reconstructed.addressType)
    }

    @Test
    fun testFromSCAddressContract() {
        val address = Address(contractId)
        val scAddress = address.toSCAddress()
        val reconstructed = Address.fromSCAddress(scAddress)

        assertEquals(contractId, reconstructed.toString())
        assertEquals(Address.AddressType.CONTRACT, reconstructed.addressType)
    }

    @Test
    fun testFromSCAddressMuxedAccount() {
        val address = Address(muxedAccountId)
        val scAddress = address.toSCAddress()
        val reconstructed = Address.fromSCAddress(scAddress)

        assertEquals(muxedAccountId, reconstructed.toString())
        assertEquals(Address.AddressType.MUXED_ACCOUNT, reconstructed.addressType)
    }

    @Test
    fun testFromSCAddressClaimableBalance() {
        val address = Address(claimableBalanceId)
        val scAddress = address.toSCAddress()
        val reconstructed = Address.fromSCAddress(scAddress)

        assertEquals(claimableBalanceId, reconstructed.toString())
        assertEquals(Address.AddressType.CLAIMABLE_BALANCE, reconstructed.addressType)
    }

    @Test
    fun testFromSCAddressLiquidityPool() {
        val address = Address(liquidityPoolId)
        val scAddress = address.toSCAddress()
        val reconstructed = Address.fromSCAddress(scAddress)

        assertEquals(liquidityPoolId, reconstructed.toString())
        assertEquals(Address.AddressType.LIQUIDITY_POOL, reconstructed.addressType)
    }

    @Test
    fun testToSCVal() {
        val address = Address(contractId)
        val scVal = address.toSCVal()

        assertTrue(scVal is SCValXdr.Address)
        val addressVal = scVal as SCValXdr.Address
        assertTrue(addressVal.value is SCAddressXdr.ContractId)
    }

    @Test
    fun testFromSCVal() {
        val address = Address(contractId)
        val scVal = address.toSCVal()
        val reconstructed = Address.fromSCVal(scVal)

        assertEquals(contractId, reconstructed.toString())
        assertEquals(Address.AddressType.CONTRACT, reconstructed.addressType)
    }

    @Test
    fun testFromSCValInvalidType() {
        val scVal = SCValXdr.U32(Uint32Xdr(42u))

        val exception = assertFailsWith<IllegalArgumentException> {
            Address.fromSCVal(scVal)
        }
        assertTrue(exception.message!!.contains("invalid scVal type"))
    }

    @Test
    fun testGetBytes() {
        val accountIdBytes = StrKey.decodeEd25519PublicKey(accountId)
        val address = Address.fromAccount(accountIdBytes)
        val retrievedBytes = address.getBytes()

        assertContentEquals(accountIdBytes, retrievedBytes)
        // Verify it's a copy
        assertNotSame(accountIdBytes, retrievedBytes)
    }

    @Test
    fun testGetEncodedAddress() {
        val address = Address(accountId)
        assertEquals(accountId, address.getEncodedAddress())
    }

    @Test
    fun testToString() {
        val address = Address(contractId)
        assertEquals(contractId, address.toString())
    }

    @Test
    fun testEqualsAndHashCode() {
        val address1 = Address(accountId)
        val address2 = Address(accountId)
        val address3 = Address(contractId)

        assertEquals(address1, address2)
        assertEquals(address1.hashCode(), address2.hashCode())
        assertNotEquals(address1, address3)
        assertNotEquals(address1.hashCode(), address3.hashCode())
    }

    @Test
    fun testRoundTripAccount() {
        val original = Address(accountId)
        val scAddress = original.toSCAddress()
        val reconstructed = Address.fromSCAddress(scAddress)

        assertEquals(original, reconstructed)
        assertEquals(original.addressType, reconstructed.addressType)
        assertContentEquals(original.getBytes(), reconstructed.getBytes())
    }

    @Test
    fun testRoundTripContract() {
        val original = Address(contractId)
        val scAddress = original.toSCAddress()
        val reconstructed = Address.fromSCAddress(scAddress)

        assertEquals(original, reconstructed)
        assertEquals(original.addressType, reconstructed.addressType)
        assertContentEquals(original.getBytes(), reconstructed.getBytes())
    }

    @Test
    fun testRoundTripMuxedAccount() {
        val original = Address(muxedAccountId)
        val scAddress = original.toSCAddress()
        val reconstructed = Address.fromSCAddress(scAddress)

        assertEquals(original, reconstructed)
        assertEquals(original.addressType, reconstructed.addressType)
        assertContentEquals(original.getBytes(), reconstructed.getBytes())
    }

    @Test
    fun testRoundTripClaimableBalance() {
        val original = Address(claimableBalanceId)
        val scAddress = original.toSCAddress()
        val reconstructed = Address.fromSCAddress(scAddress)

        assertEquals(original, reconstructed)
        assertEquals(original.addressType, reconstructed.addressType)
        assertContentEquals(original.getBytes(), reconstructed.getBytes())
    }

    @Test
    fun testRoundTripLiquidityPool() {
        val original = Address(liquidityPoolId)
        val scAddress = original.toSCAddress()
        val reconstructed = Address.fromSCAddress(scAddress)

        assertEquals(original, reconstructed)
        assertEquals(original.addressType, reconstructed.addressType)
        assertContentEquals(original.getBytes(), reconstructed.getBytes())
    }

    @Test
    fun testRoundTripSCVal() {
        val original = Address(contractId)
        val scVal = original.toSCVal()
        val reconstructed = Address.fromSCVal(scVal)

        assertEquals(original, reconstructed)
        assertEquals(original.addressType, reconstructed.addressType)
        assertContentEquals(original.getBytes(), reconstructed.getBytes())
    }
}
