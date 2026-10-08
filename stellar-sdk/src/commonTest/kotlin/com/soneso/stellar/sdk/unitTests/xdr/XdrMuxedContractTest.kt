package com.soneso.stellar.sdk.unitTests.xdr

import com.soneso.stellar.sdk.Address
import com.soneso.stellar.sdk.Util
import com.soneso.stellar.sdk.xdr.*
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * Tests for MuxedContract, the SC_ADDRESS_TYPE_MUXED_CONTRACT arm of SCAddress, and the ML-DSA
 * contract cost types.
 */
@OptIn(ExperimentalEncodingApi::class)
class XdrMuxedContractTest {

    private val muxedContractId = "WA3D5KRYM6CB7OWQ6TWYRR3Z4T7GNZLKERYNZGGA5SOAOPIFY6YQGAAAAAAAAAPCIA6IG"
    private val contractHash = Util.hexToBytes("363eaa3867841fbad0f4ed88c779e4fe66e56a2470dc98c0ec9c073d05c7b103")

    // MuxedContract lists the id 123456 ahead of the contract id; the SCAddress arm leads with
    // the discriminant 5.
    private val muxedContractBase64 = "AAAAAAAB4kA2Pqo4Z4QfutD07YjHeeT+ZuVqJHDcmMDsnAc9BcexAw=="
    private val scAddressBase64 = "AAAABQAAAAAAAeJANj6qOGeEH7rQ9O2Ix3nk/mblaiRw3JjA7JwHPQXHsQM="

    private fun muxedContract() = MuxedContractXdr(Uint64Xdr(123456UL), ContractIDXdr(HashXdr(contractHash)))

    private fun encoded(encode: (XdrWriter) -> Unit): String = Base64.encode(XdrWriter().also(encode).toByteArray())

    @Test fun testMuxedContractBinaryForm() {
        assertEquals(muxedContractBase64, encoded { muxedContract().encode(it) })

        val decoded = MuxedContractXdr.decode(XdrReader(Base64.decode(muxedContractBase64)))
        assertEquals(123456UL, decoded.id.value)
        assertContentEquals(contractHash, decoded.contractId.value.value)
    }

    @Test fun testMuxedContractXdrJsonIsTheWStrkey() {
        assertEquals("\"$muxedContractId\"", muxedContract().toXdrJson())

        val decoded = MuxedContractXdr.fromXdrJson("\"$muxedContractId\"")
        assertEquals(123456UL, decoded.id.value)
        assertContentEquals(contractHash, decoded.contractId.value.value)
    }

    @Test fun testSCAddressMuxedContractArm() {
        val address = Address(muxedContractId).toSCAddress()
        assertEquals(scAddressBase64, encoded { address.encode(it) })
        assertEquals("\"$muxedContractId\"", address.toXdrJson())

        val fromBytes = SCAddressXdr.decode(XdrReader(Base64.decode(scAddressBase64)))
        val fromJson = SCAddressXdr.fromXdrJson("\"$muxedContractId\"")
        for (decoded in listOf(fromBytes, fromJson)) {
            val arm = assertIs<SCAddressXdr.MuxedContract>(decoded).value
            assertEquals(123456UL, arm.id.value)
            assertContentEquals(contractHash, arm.contractId.value.value)
        }
    }

    @Test fun testMlDsaCostTypes() {
        assertEquals(86, ContractCostTypeXdr.MlDsa44DecodeVerifyingKey.value)
        assertEquals("\"ml_dsa44_decode_verifying_key\"", ContractCostTypeXdr.MlDsa44DecodeVerifyingKey.toXdrJson())
        assertEquals(94, ContractCostTypeXdr.VerifyMlDsa87Sig.value)
        assertEquals(ContractCostTypeXdr.VerifyMlDsa87Sig, ContractCostTypeXdr.fromXdrJson("\"verify_ml_dsa87_sig\""))
    }
}
