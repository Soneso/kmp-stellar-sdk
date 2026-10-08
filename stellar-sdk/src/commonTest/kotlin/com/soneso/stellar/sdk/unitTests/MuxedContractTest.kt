package com.soneso.stellar.sdk.unitTests

import com.soneso.stellar.sdk.*
import com.soneso.stellar.sdk.xdr.*
import kotlin.test.*

class MuxedContractTest {

    private val contractId = "CA7QYNF7SOWQ3GLR2BGMZEHXAVIRZA4KVWLTJJFC7MGXUA74P7UJUWDA"
    private val idZero = "WA7QYNF7SOWQ3GLR2BGMZEHXAVIRZA4KVWLTJJFC7MGXUA74P7UJUAAAAAAAAAAAAAWWC"
    private val idTwoToThe63 = "WA7QYNF7SOWQ3GLR2BGMZEHXAVIRZA4KVWLTJJFC7MGXUA74P7UJVAAAAAAAAAAAACWJY"
    private val otherContractId = "CA3D5KRYM6CB7OWQ6TWYRR3Z4T7GNZLKERYNZGGA5SOAOPIFY6YQGAXE"
    private val id123456 = "WA3D5KRYM6CB7OWQ6TWYRR3Z4T7GNZLKERYNZGGA5SOAOPIFY6YQGAAAAAAAAAPCIA6IG"

    @Test
    fun testContractIdAndIdSpellTheMuxedContractAddress() {
        assertEquals(idZero, MuxedContract(contractId, 0UL).address)
        assertEquals(idTwoToThe63, MuxedContract(contractId, 9223372036854775808UL).address)
        assertEquals(id123456, MuxedContract(otherContractId, 123456UL).address)
    }

    @Test
    fun testMuxedContractAddressSplitsIntoContractIdAndId() {
        val muxed = MuxedContract(id123456)
        assertEquals(otherContractId, muxed.contractId)
        assertEquals(123456UL, muxed.id)
        assertEquals(id123456, muxed.address)

        assertEquals(0UL, MuxedContract(idZero).id)
        assertEquals(9223372036854775808UL, MuxedContract(idTwoToThe63).id)
    }

    @Test
    fun testLargestIdRoundTripsThroughTheAddress() {
        val muxed = MuxedContract(contractId, ULong.MAX_VALUE)
        val parsed = MuxedContract(muxed.address)
        assertEquals(ULong.MAX_VALUE, parsed.id)
        assertEquals(contractId, parsed.contractId)
    }

    @Test
    fun testContractAddressLeavesTheIdUnset() {
        val plain = MuxedContract(contractId)
        assertEquals(contractId, plain.contractId)
        assertNull(plain.id)
        assertEquals(contractId, plain.address)
    }

    @Test
    fun testToSCAddressPicksTheArmByTheId() {
        val hash = StrKey.decodeContract(otherContractId)

        val muxedArm = assertIs<SCAddressXdr.MuxedContract>(MuxedContract(id123456).toSCAddress()).value
        assertEquals(123456UL, muxedArm.id.value)
        assertContentEquals(hash, muxedArm.contractId.value.value)

        val plainArm = assertIs<SCAddressXdr.ContractId>(MuxedContract(otherContractId).toSCAddress())
        assertContentEquals(hash, plainArm.value.value.value)
    }

    @Test
    fun testFromSCAddressReadsBothContractArms() {
        val hash = HashXdr(StrKey.decodeContract(otherContractId))
        val muxed = MuxedContract.fromSCAddress(
            SCAddressXdr.MuxedContract(MuxedContractXdr(Uint64Xdr(123456UL), ContractIDXdr(hash)))
        )
        assertEquals(MuxedContract(otherContractId, 123456UL), muxed)

        val plain = MuxedContract.fromSCAddress(SCAddressXdr.ContractId(ContractIDXdr(hash)))
        assertEquals(otherContractId, plain.contractId)
        assertNull(plain.id)
    }

    @Test
    fun testFromSCAddressRejectsTheOtherArms() {
        val account = Address("GA7QYNF7SOWQ3GLR2BGMZEHXAVIRZA4KVWLTJJFC7MGXUA74P7UJVSGZ").toSCAddress()
        val failure = assertFailsWith<IllegalArgumentException> { MuxedContract.fromSCAddress(account) }
        assertEquals(
            "Expected a contract or muxed contract address, got SC_ADDRESS_TYPE_ACCOUNT",
            failure.message
        )
    }

    @Test
    fun testToAddressKeepsTheAddressType() {
        val muxed = MuxedContract(id123456).toAddress()
        assertEquals(Address.AddressType.MUXED_CONTRACT, muxed.addressType)
        assertEquals(id123456, muxed.toString())

        assertEquals(Address.AddressType.CONTRACT, MuxedContract(contractId).toAddress().addressType)
    }

    @Test
    fun testRejectsStringsThatAreNotAContractOrMuxedContractAddress() {
        val muxedAccount = MuxedAccount("GA7QYNF7SOWQ3GLR2BGMZEHXAVIRZA4KVWLTJJFC7MGXUA74P7UJVSGZ", 1UL).address
        for (input in listOf("WINVALID", idZero.dropLast(1) + "D", muxedAccount)) {
            val failure = assertFailsWith<IllegalArgumentException>(input) { MuxedContract(input) }
            assertEquals("Invalid contract or muxed contract address: $input", failure.message)
        }

        val failure = assertFailsWith<IllegalArgumentException> { MuxedContract(idZero, 1UL) }
        assertEquals("Invalid contract ID: $idZero", failure.message)
    }

    @Test
    fun testEqualsHashCodeAndToString() {
        val muxed = MuxedContract(otherContractId, 123456UL)
        assertEquals(MuxedContract(id123456), muxed)
        assertEquals(MuxedContract(id123456).hashCode(), muxed.hashCode())
        assertNotEquals(MuxedContract(otherContractId, 123457UL), muxed)
        assertNotEquals(MuxedContract(contractId, 123456UL), muxed)
        assertNotEquals(MuxedContract(otherContractId), muxed)

        assertEquals(
            "MuxedContract(address=$id123456, contractId=$otherContractId, id=123456)",
            muxed.toString()
        )
        assertEquals("MuxedContract(address=$contractId)", MuxedContract(contractId).toString())
    }
}
