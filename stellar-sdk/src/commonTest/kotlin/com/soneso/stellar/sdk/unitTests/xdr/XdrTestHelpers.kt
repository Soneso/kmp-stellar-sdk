package com.soneso.stellar.sdk.unitTests.xdr

import com.soneso.stellar.sdk.xdr.*

object XdrTestHelpers {

    fun uint32(v: UInt = 42u) = Uint32Xdr(v)
    fun int32(v: Int = 42) = Int32Xdr(v)
    fun uint64(v: ULong = 100UL) = Uint64Xdr(v)
    fun int64(v: Long = 100L) = Int64Xdr(v)

    fun hash32(): ByteArray = ByteArray(32) { it.toByte() }
    fun hash32Alt(): ByteArray = ByteArray(32) { (it + 100).toByte() }
    fun bytes4(): ByteArray = byteArrayOf(1, 2, 3, 4)
    fun bytes12(): ByteArray = ByteArray(12) { (it + 10).toByte() }

    fun hashXdr() = HashXdr(hash32())
    fun hashXdrAlt() = HashXdr(hash32Alt())
    fun uint256Xdr() = Uint256Xdr(hash32())
    fun uint256XdrAlt() = Uint256Xdr(hash32Alt())

    fun publicKeyEd25519() = PublicKeyXdr.Ed25519(uint256Xdr())
    fun publicKeyEd25519Alt() = PublicKeyXdr.Ed25519(uint256XdrAlt())

    fun accountId() = AccountIDXdr(publicKeyEd25519())
    fun accountIdAlt() = AccountIDXdr(publicKeyEd25519Alt())

    fun muxedAccountEd25519() = MuxedAccountXdr.Ed25519(uint256Xdr())
    fun muxedAccountMed25519() = MuxedAccountXdr.Med25519(
        MuxedAccountMed25519Xdr(uint64(12345UL), uint256Xdr())
    )

    fun assetCode4Xdr() = AssetCode4Xdr(bytes4())
    fun assetCode12Xdr() = AssetCode12Xdr(bytes12())

    fun alphaNum4() = AlphaNum4Xdr(assetCode4Xdr(), accountId())
    fun alphaNum12() = AlphaNum12Xdr(assetCode12Xdr(), accountId())

    fun assetNative(): AssetXdr = AssetXdr.Void
    fun assetAlphaNum4(): AssetXdr = AssetXdr.AlphaNum4(alphaNum4())
    fun assetAlphaNum12(): AssetXdr = AssetXdr.AlphaNum12(alphaNum12())

    fun sequenceNumber(v: Long = 12345L) = SequenceNumberXdr(int64(v))

    fun price(n: Int = 1, d: Int = 2) = PriceXdr(int32(n), int32(d))

    fun contractId() = ContractIDXdr(hashXdr())
    fun poolId() = PoolIDXdr(hashXdr())

    fun string32(v: String = "hello") = String32Xdr(v)
    fun string64(v: String = "test-data") = String64Xdr(v)

    fun thresholds() = ThresholdsXdr(bytes4())

    fun assertBytesEqual(expected: ByteArray, actual: ByteArray, message: String = "") {
        val prefix = if (message.isNotEmpty()) "$message: " else ""
        kotlin.test.assertEquals(expected.size, actual.size, "${prefix}sizes differ")
        for (i in expected.indices) {
            kotlin.test.assertEquals(expected[i], actual[i], "${prefix}byte at index $i differs")
        }
    }

    inline fun <T> assertXdrRoundTrip(
        value: T,
        crossinline encode: (T, XdrWriter) -> Unit,
        crossinline decode: (XdrReader) -> T
    ) {
        val writer1 = XdrWriter()
        encode(value, writer1)
        val bytes1 = writer1.toByteArray()
        val decoded = decode(XdrReader(bytes1))
        val writer2 = XdrWriter()
        encode(decoded, writer2)
        val bytes2 = writer2.toByteArray()
        kotlin.test.assertTrue(bytes1.contentEquals(bytes2), "XDR round-trip bytes differ")
    }
}
