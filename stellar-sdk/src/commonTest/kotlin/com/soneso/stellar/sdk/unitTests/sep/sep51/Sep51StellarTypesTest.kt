package com.soneso.stellar.sdk.unitTests.sep.sep51

import com.soneso.stellar.sdk.StrKey
import com.soneso.stellar.sdk.xdr.AccountIDXdr
import com.soneso.stellar.sdk.xdr.AssetCode12Xdr
import com.soneso.stellar.sdk.xdr.AssetCode4Xdr
import com.soneso.stellar.sdk.xdr.AssetCodeXdr
import com.soneso.stellar.sdk.xdr.ClaimableBalanceIDXdr
import com.soneso.stellar.sdk.xdr.ContractIDXdr
import com.soneso.stellar.sdk.xdr.HashXdr
import com.soneso.stellar.sdk.xdr.Int128PartsXdr
import com.soneso.stellar.sdk.xdr.Int256PartsXdr
import com.soneso.stellar.sdk.xdr.Int64Xdr
import com.soneso.stellar.sdk.xdr.MuxedAccountMed25519Xdr
import com.soneso.stellar.sdk.xdr.MuxedAccountXdr
import com.soneso.stellar.sdk.xdr.MuxedEd25519AccountXdr
import com.soneso.stellar.sdk.xdr.NodeIDXdr
import com.soneso.stellar.sdk.xdr.PoolIDXdr
import com.soneso.stellar.sdk.xdr.PublicKeyXdr
import com.soneso.stellar.sdk.xdr.SCAddressXdr
import com.soneso.stellar.sdk.xdr.SignerKeyEd25519SignedPayloadXdr
import com.soneso.stellar.sdk.xdr.SignerKeyXdr
import com.soneso.stellar.sdk.xdr.UInt128PartsXdr
import com.soneso.stellar.sdk.xdr.UInt256PartsXdr
import com.soneso.stellar.sdk.xdr.Uint256Xdr
import com.soneso.stellar.sdk.xdr.Uint64Xdr
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Pins the XDR types SEP-0051 renders in a Stellar-specific form rather than by the generic
 * struct, union and typedef rules: addresses, signers and keys as SEP-23 strkeys, asset codes as
 * trimmed and escaped strings, and the 128-bit and 256-bit integer parts as a single base-10
 * string.
 *
 * Every arm of every one of those types is asserted in both directions, alongside each branch
 * that refuses input: a strkey whose checksum does not hold, a strkey of a kind the member does
 * not accept, a prefix no arm claims, an asset code wider than its type, and a wide integer
 * outside the range its bit size allows.
 *
 * The strkeys and decimal strings below are the XDR-JSON forms of the values they stand beside.
 */
class Sep51StellarTypesTest {

    private fun text(element: JsonElement): String = (element as JsonPrimitive).content

    private fun rejects(block: () -> Unit): IllegalArgumentException =
        assertFailsWith<IllegalArgumentException>(block = block)

    private fun filled(byte: Int, size: Int = 32): ByteArray = ByteArray(size) { byte.toByte() }

    /** The 32-byte key an [PublicKeyXdr] carries, for a content comparison of the decoded value. */
    private fun ed25519Of(value: PublicKeyXdr): ByteArray =
        assertIs<PublicKeyXdr.Ed25519>(value).value.value

    // -------------------------------------------------------------------------------------
    // Account keys: AccountID, PublicKey and NodeID as G strkeys
    // -------------------------------------------------------------------------------------

    @Test
    fun publicKeyRendersItsEd25519ArmAsAGStrkey() {
        val value = PublicKeyXdr.Ed25519(Uint256Xdr(filled(0x11)))
        assertEquals(ACCOUNT_STRKEY, text(value.toXdrJsonElement()))
        assertEquals("\"$ACCOUNT_STRKEY\"", value.toXdrJson())
    }

    @Test
    fun publicKeyReadsAGStrkeyBackIntoItsEd25519Arm() {
        val decoded = PublicKeyXdr.fromXdrJsonElement(JsonPrimitive(ACCOUNT_STRKEY))
        assertContentEquals(filled(0x11), ed25519Of(decoded))
    }

    @Test
    fun accountIdRendersAsAGStrkey() {
        val value = AccountIDXdr(PublicKeyXdr.Ed25519(Uint256Xdr(filled(0x11))))
        assertEquals(ACCOUNT_STRKEY, text(value.toXdrJsonElement()))
    }

    @Test
    fun accountIdReadsAGStrkeyBack() {
        val decoded = AccountIDXdr.fromXdrJsonElement(JsonPrimitive(ACCOUNT_STRKEY))
        assertContentEquals(filled(0x11), ed25519Of(decoded.value))
    }

    @Test
    fun nodeIdRendersAsAGStrkey() {
        val value = NodeIDXdr(PublicKeyXdr.Ed25519(Uint256Xdr(filled(0x11))))
        assertEquals(ACCOUNT_STRKEY, text(value.toXdrJsonElement()))
    }

    @Test
    fun nodeIdReadsAGStrkeyBack() {
        val decoded = NodeIDXdr.fromXdrJsonElement(JsonPrimitive(ACCOUNT_STRKEY))
        assertContentEquals(filled(0x11), ed25519Of(decoded.value))
    }

    // -------------------------------------------------------------------------------------
    // MuxedAccount: G on the ed25519 arm, M on the med25519 arm
    // -------------------------------------------------------------------------------------

    @Test
    fun muxedAccountRendersItsEd25519ArmAsAGStrkey() {
        val value = MuxedAccountXdr.Ed25519(Uint256Xdr(filled(0x11)))
        assertEquals(ACCOUNT_STRKEY, text(value.toXdrJsonElement()))
    }

    @Test
    fun muxedAccountReadsAGStrkeyBackIntoItsEd25519Arm() {
        val decoded = MuxedAccountXdr.fromXdrJsonElement(JsonPrimitive(ACCOUNT_STRKEY))
        val ed25519 = assertIs<MuxedAccountXdr.Ed25519>(decoded)
        assertContentEquals(filled(0x11), ed25519.value.value)
    }

    @Test
    fun muxedAccountRendersItsMed25519ArmAsAnMStrkey() {
        val value = MuxedAccountXdr.Med25519(
            MuxedAccountMed25519Xdr(Uint64Xdr(1uL), Uint256Xdr(filled(0x11)))
        )
        assertEquals(MUXED_STRKEY_ID_ONE, text(value.toXdrJsonElement()))
    }

    @Test
    fun muxedAccountReadsAnMStrkeyBackIntoItsMed25519Arm() {
        val decoded = MuxedAccountXdr.fromXdrJsonElement(JsonPrimitive(MUXED_STRKEY_ID_ONE))
        val med25519 = assertIs<MuxedAccountXdr.Med25519>(decoded).value
        assertEquals(1uL, med25519.id.value)
        assertContentEquals(filled(0x11), med25519.ed25519.value)
    }

    @Test
    fun muxedAccountMed25519RendersAsAnMStrkey() {
        val value = MuxedAccountMed25519Xdr(Uint64Xdr(1uL), Uint256Xdr(filled(0x11)))
        assertEquals(MUXED_STRKEY_ID_ONE, text(value.toXdrJsonElement()))
        assertEquals("\"$MUXED_STRKEY_ID_ONE\"", value.toXdrJson())
    }

    @Test
    fun muxedAccountMed25519ReadsAnMStrkeyBack() {
        val decoded = MuxedAccountMed25519Xdr.fromXdrJsonElement(JsonPrimitive(MUXED_STRKEY_ID_ONE))
        assertEquals(1uL, decoded.id.value)
        assertContentEquals(filled(0x11), decoded.ed25519.value)
    }

    @Test
    fun muxedEd25519AccountRendersAsAnMStrkey() {
        val value = MuxedEd25519AccountXdr(Uint64Xdr(1uL), Uint256Xdr(filled(0x11)))
        assertEquals(MUXED_STRKEY_ID_ONE, text(value.toXdrJsonElement()))
    }

    @Test
    fun muxedEd25519AccountReadsAnMStrkeyBack() {
        val decoded = MuxedEd25519AccountXdr.fromXdrJsonElement(JsonPrimitive(MUXED_STRKEY_ID_ONE))
        assertEquals(1uL, decoded.id.value)
        assertContentEquals(filled(0x11), decoded.ed25519.value)
    }

    @Test
    fun muxedAccountRendersTheMultiplexingIdAtBothExtremes() {
        assertEquals(
            MUXED_STRKEY_ID_ZERO,
            text(MuxedAccountMed25519Xdr(Uint64Xdr(0uL), Uint256Xdr(filled(0x11))).toXdrJsonElement())
        )
        assertEquals(
            MUXED_STRKEY_ID_MAXIMUM,
            text(
                MuxedAccountMed25519Xdr(
                    Uint64Xdr(ULong.MAX_VALUE),
                    Uint256Xdr(filled(0x11))
                ).toXdrJsonElement()
            )
        )
    }

    @Test
    fun muxedAccountReadsTheMultiplexingIdBackAtBothExtremes() {
        assertEquals(
            0uL,
            MuxedAccountMed25519Xdr.fromXdrJsonElement(JsonPrimitive(MUXED_STRKEY_ID_ZERO)).id.value
        )
        assertEquals(
            ULong.MAX_VALUE,
            MuxedAccountMed25519Xdr.fromXdrJsonElement(
                JsonPrimitive(MUXED_STRKEY_ID_MAXIMUM)
            ).id.value
        )
    }

    // -------------------------------------------------------------------------------------
    // The byte order an M strkey packs, which is not the order the XDR structure declares
    // -------------------------------------------------------------------------------------

    /**
     * The M strkey carries the 32-byte account key first and the multiplexing id after it, most
     * significant byte first, while the XDR structure declares the id ahead of the key.
     */
    @Test
    fun theMuxedStrkeyCarriesTheAccountKeyBeforeTheMultiplexingId() {
        val value = MuxedAccountMed25519Xdr(Uint64Xdr(0x0102030405060708uL), Uint256Xdr(filled(0x11)))
        val payload = StrKey.decodeMed25519PublicKey(text(value.toXdrJsonElement()))

        assertEquals(40, payload.size)
        assertContentEquals(filled(0x11), payload.copyOfRange(0, 32))
        assertContentEquals(
            byteArrayOf(0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08),
            payload.copyOfRange(32, 40)
        )
    }

    /** The reverse reading: the same layout is what the decoder takes the id and key out of. */
    @Test
    fun theMuxedStrkeyIsReadAsTheAccountKeyFollowedByTheMultiplexingId() {
        val payload = filled(0x11) + byteArrayOf(0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08)
        val strkey = StrKey.encodeMed25519PublicKey(payload)

        val decoded = MuxedAccountMed25519Xdr.fromXdrJsonElement(JsonPrimitive(strkey))
        assertEquals(0x0102030405060708uL, decoded.id.value)
        assertContentEquals(filled(0x11), decoded.ed25519.value)
        assertEquals(MUXED_STRKEY_ORDERED_ID, strkey)
    }

    // -------------------------------------------------------------------------------------
    // ContractID, PoolID and ClaimableBalanceID
    // -------------------------------------------------------------------------------------

    @Test
    fun contractIdRendersAsACStrkey() {
        val value = ContractIDXdr(HashXdr(filled(0x33)))
        assertEquals(CONTRACT_STRKEY, text(value.toXdrJsonElement()))
    }

    @Test
    fun contractIdReadsACStrkeyBack() {
        val decoded = ContractIDXdr.fromXdrJsonElement(JsonPrimitive(CONTRACT_STRKEY))
        assertContentEquals(filled(0x33), decoded.value.value)
    }

    @Test
    fun poolIdRendersAsAnLStrkey() {
        val value = PoolIDXdr(HashXdr(filled(0x44)))
        assertEquals(POOL_STRKEY, text(value.toXdrJsonElement()))
    }

    @Test
    fun poolIdReadsAnLStrkeyBack() {
        val decoded = PoolIDXdr.fromXdrJsonElement(JsonPrimitive(POOL_STRKEY))
        assertContentEquals(filled(0x44), decoded.value.value)
    }

    @Test
    fun claimableBalanceIdRendersAsABStrkey() {
        val value = ClaimableBalanceIDXdr.V0(HashXdr(filled(0x55)))
        assertEquals(BALANCE_STRKEY, text(value.toXdrJsonElement()))
    }

    @Test
    fun claimableBalanceIdReadsABStrkeyBack() {
        val decoded = ClaimableBalanceIDXdr.fromXdrJsonElement(JsonPrimitive(BALANCE_STRKEY))
        assertContentEquals(filled(0x55), assertIs<ClaimableBalanceIDXdr.V0>(decoded).value.value)
    }

    // -------------------------------------------------------------------------------------
    // SCAddress: one strkey form per arm
    // -------------------------------------------------------------------------------------

    @Test
    fun addressRendersItsAccountArmAsAGStrkey() {
        val value = SCAddressXdr.AccountId(
            AccountIDXdr(PublicKeyXdr.Ed25519(Uint256Xdr(filled(0x11))))
        )
        assertEquals(ACCOUNT_STRKEY, text(value.toXdrJsonElement()))
    }

    @Test
    fun addressReadsAGStrkeyBackIntoItsAccountArm() {
        val decoded = SCAddressXdr.fromXdrJsonElement(JsonPrimitive(ACCOUNT_STRKEY))
        val account = assertIs<SCAddressXdr.AccountId>(decoded)
        assertContentEquals(filled(0x11), ed25519Of(account.value.value))
    }

    @Test
    fun addressRendersItsContractArmAsACStrkey() {
        val value = SCAddressXdr.ContractId(ContractIDXdr(HashXdr(filled(0x33))))
        assertEquals(CONTRACT_STRKEY, text(value.toXdrJsonElement()))
    }

    @Test
    fun addressReadsACStrkeyBackIntoItsContractArm() {
        val decoded = SCAddressXdr.fromXdrJsonElement(JsonPrimitive(CONTRACT_STRKEY))
        val contract = assertIs<SCAddressXdr.ContractId>(decoded)
        assertContentEquals(filled(0x33), contract.value.value.value)
    }

    @Test
    fun addressRendersItsMuxedAccountArmAsAnMStrkey() {
        val value = SCAddressXdr.MuxedAccount(
            MuxedEd25519AccountXdr(Uint64Xdr(1uL), Uint256Xdr(filled(0x11)))
        )
        assertEquals(MUXED_STRKEY_ID_ONE, text(value.toXdrJsonElement()))
    }

    @Test
    fun addressReadsAnMStrkeyBackIntoItsMuxedAccountArm() {
        val decoded = SCAddressXdr.fromXdrJsonElement(JsonPrimitive(MUXED_STRKEY_ID_ONE))
        val muxed = assertIs<SCAddressXdr.MuxedAccount>(decoded).value
        assertEquals(1uL, muxed.id.value)
        assertContentEquals(filled(0x11), muxed.ed25519.value)
    }

    @Test
    fun addressRendersItsClaimableBalanceArmAsABStrkey() {
        val value = SCAddressXdr.ClaimableBalanceId(ClaimableBalanceIDXdr.V0(HashXdr(filled(0x55))))
        assertEquals(BALANCE_STRKEY, text(value.toXdrJsonElement()))
    }

    @Test
    fun addressReadsABStrkeyBackIntoItsClaimableBalanceArm() {
        val decoded = SCAddressXdr.fromXdrJsonElement(JsonPrimitive(BALANCE_STRKEY))
        val balance = assertIs<SCAddressXdr.ClaimableBalanceId>(decoded).value
        assertContentEquals(filled(0x55), assertIs<ClaimableBalanceIDXdr.V0>(balance).value.value)
    }

    @Test
    fun addressRendersItsLiquidityPoolArmAsAnLStrkey() {
        val value = SCAddressXdr.LiquidityPoolId(PoolIDXdr(HashXdr(filled(0x44))))
        assertEquals(POOL_STRKEY, text(value.toXdrJsonElement()))
    }

    @Test
    fun addressReadsAnLStrkeyBackIntoItsLiquidityPoolArm() {
        val decoded = SCAddressXdr.fromXdrJsonElement(JsonPrimitive(POOL_STRKEY))
        val pool = assertIs<SCAddressXdr.LiquidityPoolId>(decoded)
        assertContentEquals(filled(0x44), pool.value.value.value)
    }

    // -------------------------------------------------------------------------------------
    // SignerKey: one strkey form per arm
    // -------------------------------------------------------------------------------------

    @Test
    fun signerKeyRendersItsEd25519ArmAsAGStrkey() {
        val value = SignerKeyXdr.Ed25519(Uint256Xdr(filled(0x11)))
        assertEquals(ACCOUNT_STRKEY, text(value.toXdrJsonElement()))
    }

    @Test
    fun signerKeyReadsAGStrkeyBackIntoItsEd25519Arm() {
        val decoded = SignerKeyXdr.fromXdrJsonElement(JsonPrimitive(ACCOUNT_STRKEY))
        assertContentEquals(filled(0x11), assertIs<SignerKeyXdr.Ed25519>(decoded).value.value)
    }

    @Test
    fun signerKeyRendersItsPreAuthTxArmAsATStrkey() {
        val value = SignerKeyXdr.PreAuthTx(Uint256Xdr(filled(0x66)))
        assertEquals(PRE_AUTH_TX_STRKEY, text(value.toXdrJsonElement()))
    }

    @Test
    fun signerKeyReadsATStrkeyBackIntoItsPreAuthTxArm() {
        val decoded = SignerKeyXdr.fromXdrJsonElement(JsonPrimitive(PRE_AUTH_TX_STRKEY))
        assertContentEquals(filled(0x66), assertIs<SignerKeyXdr.PreAuthTx>(decoded).value.value)
    }

    @Test
    fun signerKeyRendersItsHashXArmAsAnXStrkey() {
        val value = SignerKeyXdr.HashX(Uint256Xdr(filled(0x77)))
        assertEquals(HASH_X_STRKEY, text(value.toXdrJsonElement()))
    }

    @Test
    fun signerKeyReadsAnXStrkeyBackIntoItsHashXArm() {
        val decoded = SignerKeyXdr.fromXdrJsonElement(JsonPrimitive(HASH_X_STRKEY))
        assertContentEquals(filled(0x77), assertIs<SignerKeyXdr.HashX>(decoded).value.value)
    }

    @Test
    fun signerKeyRendersItsSignedPayloadArmAsAPStrkey() {
        val value = SignerKeyXdr.Ed25519SignedPayload(
            SignerKeyEd25519SignedPayloadXdr(
                Uint256Xdr(filled(0x11)),
                byteArrayOf(0x01, 0x02, 0x03, 0x04)
            )
        )
        assertEquals(SIGNED_PAYLOAD_STRKEY, text(value.toXdrJsonElement()))
    }

    @Test
    fun signerKeyReadsAPStrkeyBackIntoItsSignedPayloadArm() {
        val decoded = SignerKeyXdr.fromXdrJsonElement(JsonPrimitive(SIGNED_PAYLOAD_STRKEY))
        val signed = assertIs<SignerKeyXdr.Ed25519SignedPayload>(decoded).value
        assertContentEquals(filled(0x11), signed.ed25519.value)
        assertContentEquals(byteArrayOf(0x01, 0x02, 0x03, 0x04), signed.payload)
    }

    @Test
    fun signedPayloadRendersAsAPStrkey() {
        val value = SignerKeyEd25519SignedPayloadXdr(
            Uint256Xdr(filled(0x11)),
            byteArrayOf(0x01, 0x02, 0x03, 0x04)
        )
        assertEquals(SIGNED_PAYLOAD_STRKEY, text(value.toXdrJsonElement()))
        assertEquals("\"$SIGNED_PAYLOAD_STRKEY\"", value.toXdrJson())
    }

    @Test
    fun signedPayloadReadsAPStrkeyBack() {
        val decoded = SignerKeyEd25519SignedPayloadXdr.fromXdrJsonElement(
            JsonPrimitive(SIGNED_PAYLOAD_STRKEY)
        )
        assertContentEquals(filled(0x11), decoded.ed25519.value)
        assertContentEquals(byteArrayOf(0x01, 0x02, 0x03, 0x04), decoded.payload)
    }

    @Test
    fun signedPayloadRendersTheWidestPayloadAStrkeyCarries() {
        val value = SignerKeyEd25519SignedPayloadXdr(Uint256Xdr(filled(0x11)), filled(0xAB, 64))
        assertEquals(SIGNED_PAYLOAD_STRKEY_SIXTY_FOUR, text(value.toXdrJsonElement()))
    }

    /**
     * Every payload length XDR admits renders, down to a single byte: padding to a four-byte
     * boundary carries a 1-to-3-byte payload up to the 40-byte floor the strkey codec requires
     * of the packed key. Only the empty payload falls short of it.
     */
    @Test
    fun signedPayloadCarriesEveryPayloadLengthThroughTheStrkey() {
        for (length in 1..64) {
            val payload = ByteArray(length) { (it and 0xFF).toByte() }
            val rendered = SignerKeyEd25519SignedPayloadXdr(
                Uint256Xdr(filled(0x11)),
                payload
            ).toXdrJsonElement()
            val decoded = SignerKeyEd25519SignedPayloadXdr.fromXdrJsonElement(rendered)
            assertContentEquals(payload, decoded.payload, "payload of $length bytes")
            assertContentEquals(filled(0x11), decoded.ed25519.value)
        }
    }

    /**
     * The narrowest payload that renders. Three bytes of padding carry it to the 40-byte floor,
     * and the rendering below is the reference XDR-JSON form for these bytes.
     */
    @Test
    fun signedPayloadRendersTheNarrowestPayloadAStrkeyCarries() {
        val value = SignerKeyEd25519SignedPayloadXdr(Uint256Xdr(filled(0xAB)), byteArrayOf(0x07))
        assertEquals(SIGNED_PAYLOAD_STRKEY_ONE, text(value.toXdrJsonElement()))
        assertContentEquals(
            byteArrayOf(0x07),
            SignerKeyEd25519SignedPayloadXdr.fromXdrJson("\"$SIGNED_PAYLOAD_STRKEY_ONE\"").payload
        )
    }

    /**
     * XDR admits an empty signed payload, and it is the one length that has no strkey: padding
     * adds nothing, leaving a packed key of 36 bytes where the codec requires at least 40. Such
     * a signer therefore has no XDR-JSON form, and encoding it raises by name rather than
     * emitting a strkey no decoder would read back.
     */
    @Test
    fun signedPayloadWithNoPayloadHasNoRendering() {
        val value = SignerKeyEd25519SignedPayloadXdr(Uint256Xdr(filled(0x11)), ByteArray(0))
        val error = rejects { value.toXdrJsonElement() }
        assertEquals(
            "SignerKeyEd25519SignedPayloadXdr: has an empty signed payload, " +
                "which has no strkey form",
            error.message
        )
        rejects { SignerKeyXdr.Ed25519SignedPayload(value).toXdrJsonElement() }
    }

    // -------------------------------------------------------------------------------------
    // Asset codes
    // -------------------------------------------------------------------------------------

    @Test
    fun assetCodeOfFourDropsEveryTrailingNulByte() {
        assertEquals(
            "AB",
            text(AssetCode4Xdr(byteArrayOf(0x41, 0x42, 0x00, 0x00)).toXdrJsonElement())
        )
        assertEquals(
            "ABCD",
            text(AssetCode4Xdr(byteArrayOf(0x41, 0x42, 0x43, 0x44)).toXdrJsonElement())
        )
    }

    @Test
    fun assetCodeOfFourRendersAnAllNulCodeAsAnEmptyString() {
        assertEquals("", text(AssetCode4Xdr(ByteArray(4)).toXdrJsonElement()))
    }

    @Test
    fun assetCodeOfFourKeepsANulByteThatIsNotPadding() {
        val value = AssetCode4Xdr(byteArrayOf(0x41, 0x00, 0x43, 0x00))
        assertEquals("A\\0C", text(value.toXdrJsonElement()))
        assertContentEquals(
            byteArrayOf(0x41, 0x00, 0x43, 0x00),
            AssetCode4Xdr.fromXdrJsonElement(JsonPrimitive("A\\0C")).value
        )
    }

    @Test
    fun assetCodeOfFourReadsAShortCodeBackAtItsFullWidth() {
        assertContentEquals(
            byteArrayOf(0x41, 0x42, 0x00, 0x00),
            AssetCode4Xdr.fromXdrJsonElement(JsonPrimitive("AB")).value
        )
        assertContentEquals(ByteArray(4), AssetCode4Xdr.fromXdrJsonElement(JsonPrimitive("")).value)
    }

    @Test
    fun assetCodeOfTwelveDropsTrailingNulBytesDownToFive() {
        val value = AssetCode12Xdr("ABCDEF".encodeToByteArray() + ByteArray(6))
        assertEquals("ABCDEF", text(value.toXdrJsonElement()))
    }

    @Test
    fun assetCodeOfTwelveNeverTrimsBelowFiveBytes() {
        val value = AssetCode12Xdr(byteArrayOf(0x41) + ByteArray(11))
        assertEquals("A\\0\\0\\0\\0", text(value.toXdrJsonElement()))
        assertContentEquals(
            byteArrayOf(0x41) + ByteArray(11),
            AssetCode12Xdr.fromXdrJsonElement(JsonPrimitive("A\\0\\0\\0\\0")).value
        )
    }

    /**
     * Five bytes is the floor even when every byte is padding, which is what keeps a twelve-byte
     * code distinguishable from anything a four-byte code can render.
     */
    @Test
    fun assetCodeOfTwelveRendersAnAllNulCodeAsFiveEscapedNulBytes() {
        val value = AssetCode12Xdr(ByteArray(12))
        assertEquals("\\0\\0\\0\\0\\0", text(value.toXdrJsonElement()))
        assertEquals("\"\\\\0\\\\0\\\\0\\\\0\\\\0\"", value.toXdrJson())
    }

    @Test
    fun assetCodeOfTwelveReadsFiveEscapedNulBytesBackAsTwelveNulBytes() {
        val decoded = AssetCode12Xdr.fromXdrJsonElement(JsonPrimitive("\\0\\0\\0\\0\\0"))
        assertContentEquals(ByteArray(12), decoded.value)
    }

    @Test
    fun assetCodeOfTwelveKeepsItsFullWidth() {
        val value = AssetCode12Xdr("ABCDEFGHIJKL".encodeToByteArray())
        assertEquals("ABCDEFGHIJKL", text(value.toXdrJsonElement()))
        assertContentEquals(
            "ABCDEFGHIJKL".encodeToByteArray(),
            AssetCode12Xdr.fromXdrJsonElement(JsonPrimitive("ABCDEFGHIJKL")).value
        )
    }

    @Test
    fun assetCodeUnionDelegatesEachArmToItsWidth() {
        val four = AssetCodeXdr.AssetCode4(AssetCode4Xdr(byteArrayOf(0x41, 0x42, 0x43, 0x00)))
        assertEquals("ABC", text(four.toXdrJsonElement()))

        val twelve = AssetCodeXdr.AssetCode12(
            AssetCode12Xdr("ABCDEF".encodeToByteArray() + ByteArray(6))
        )
        assertEquals("ABCDEF", text(twelve.toXdrJsonElement()))
    }

    @Test
    fun assetCodeUnionTakesTheFourByteArmForACodeOfAtMostFourBytes() {
        val decoded = AssetCodeXdr.fromXdrJsonElement(JsonPrimitive("ABCD"))
        val four = assertIs<AssetCodeXdr.AssetCode4>(decoded)
        assertContentEquals("ABCD".encodeToByteArray(), four.value.value)
    }

    @Test
    fun assetCodeUnionTakesTheTwelveByteArmForALongerCode() {
        val decoded = AssetCodeXdr.fromXdrJsonElement(JsonPrimitive("ABCDE"))
        val twelve = assertIs<AssetCodeXdr.AssetCode12>(decoded)
        assertContentEquals("ABCDE".encodeToByteArray() + ByteArray(7), twelve.value.value)
    }

    @Test
    fun assetCodeUnionTakesTheTwelveByteArmForFiveEscapedNulBytes() {
        val decoded = AssetCodeXdr.fromXdrJsonElement(JsonPrimitive("\\0\\0\\0\\0\\0"))
        val twelve = assertIs<AssetCodeXdr.AssetCode12>(decoded)
        assertContentEquals(ByteArray(12), twelve.value.value)
    }

    // -------------------------------------------------------------------------------------
    // 128-bit and 256-bit integers as a single base-10 string
    // -------------------------------------------------------------------------------------

    @Test
    fun int128RendersAsASingleBaseTenString() {
        assertEquals("0", text(Int128PartsXdr(Int64Xdr(0), Uint64Xdr(0uL)).toXdrJsonElement()))
        assertEquals("1", text(Int128PartsXdr(Int64Xdr(0), Uint64Xdr(1uL)).toXdrJsonElement()))
        assertEquals(
            "-1",
            text(Int128PartsXdr(Int64Xdr(-1), Uint64Xdr(ULong.MAX_VALUE)).toXdrJsonElement())
        )
    }

    @Test
    fun int128RendersBothExtremes() {
        assertEquals(
            "170141183460469231731687303715884105727",
            text(
                Int128PartsXdr(Int64Xdr(Long.MAX_VALUE), Uint64Xdr(ULong.MAX_VALUE))
                    .toXdrJsonElement()
            )
        )
        assertEquals(
            "-170141183460469231731687303715884105728",
            text(Int128PartsXdr(Int64Xdr(Long.MIN_VALUE), Uint64Xdr(0uL)).toXdrJsonElement())
        )
    }

    @Test
    fun int128ReadsItsLimbsBackFromTheDecimalString() {
        assertEquals(
            Int128PartsXdr(Int64Xdr(0), Uint64Xdr(0uL)),
            Int128PartsXdr.fromXdrJsonElement(JsonPrimitive("0"))
        )
        assertEquals(
            Int128PartsXdr(Int64Xdr(0), Uint64Xdr(1uL)),
            Int128PartsXdr.fromXdrJsonElement(JsonPrimitive("1"))
        )
        assertEquals(
            Int128PartsXdr(Int64Xdr(-1), Uint64Xdr(ULong.MAX_VALUE)),
            Int128PartsXdr.fromXdrJsonElement(JsonPrimitive("-1"))
        )
        assertEquals(
            Int128PartsXdr(Int64Xdr(Long.MAX_VALUE), Uint64Xdr(ULong.MAX_VALUE)),
            Int128PartsXdr.fromXdrJsonElement(
                JsonPrimitive("170141183460469231731687303715884105727")
            )
        )
        assertEquals(
            Int128PartsXdr(Int64Xdr(Long.MIN_VALUE), Uint64Xdr(0uL)),
            Int128PartsXdr.fromXdrJsonElement(
                JsonPrimitive("-170141183460469231731687303715884105728")
            )
        )
    }

    @Test
    fun uint128RendersAsASingleBaseTenString() {
        assertEquals("0", text(UInt128PartsXdr(Uint64Xdr(0uL), Uint64Xdr(0uL)).toXdrJsonElement()))
        assertEquals("1", text(UInt128PartsXdr(Uint64Xdr(0uL), Uint64Xdr(1uL)).toXdrJsonElement()))
        assertEquals(
            "340282366920938463463374607431768211455",
            text(
                UInt128PartsXdr(Uint64Xdr(ULong.MAX_VALUE), Uint64Xdr(ULong.MAX_VALUE))
                    .toXdrJsonElement()
            )
        )
    }

    @Test
    fun uint128ReadsItsLimbsBackFromTheDecimalString() {
        assertEquals(
            UInt128PartsXdr(Uint64Xdr(0uL), Uint64Xdr(0uL)),
            UInt128PartsXdr.fromXdrJsonElement(JsonPrimitive("0"))
        )
        assertEquals(
            UInt128PartsXdr(Uint64Xdr(0uL), Uint64Xdr(1uL)),
            UInt128PartsXdr.fromXdrJsonElement(JsonPrimitive("1"))
        )
        assertEquals(
            UInt128PartsXdr(Uint64Xdr(ULong.MAX_VALUE), Uint64Xdr(ULong.MAX_VALUE)),
            UInt128PartsXdr.fromXdrJsonElement(
                JsonPrimitive("340282366920938463463374607431768211455")
            )
        )
    }

    @Test
    fun int256RendersAsASingleBaseTenString() {
        assertEquals("0", text(int256(0, 0uL, 0uL, 0uL).toXdrJsonElement()))
        assertEquals("1", text(int256(0, 0uL, 0uL, 1uL).toXdrJsonElement()))
        assertEquals(
            "-1",
            text(int256(-1, ULong.MAX_VALUE, ULong.MAX_VALUE, ULong.MAX_VALUE).toXdrJsonElement())
        )
    }

    @Test
    fun int256RendersBothExtremes() {
        assertEquals(
            INT256_MAXIMUM,
            text(
                int256(Long.MAX_VALUE, ULong.MAX_VALUE, ULong.MAX_VALUE, ULong.MAX_VALUE)
                    .toXdrJsonElement()
            )
        )
        assertEquals(
            INT256_MINIMUM,
            text(int256(Long.MIN_VALUE, 0uL, 0uL, 0uL).toXdrJsonElement())
        )
    }

    @Test
    fun int256ReadsItsLimbsBackFromTheDecimalString() {
        assertEquals(int256(0, 0uL, 0uL, 0uL), Int256PartsXdr.fromXdrJsonElement(JsonPrimitive("0")))
        assertEquals(int256(0, 0uL, 0uL, 1uL), Int256PartsXdr.fromXdrJsonElement(JsonPrimitive("1")))
        assertEquals(
            int256(-1, ULong.MAX_VALUE, ULong.MAX_VALUE, ULong.MAX_VALUE),
            Int256PartsXdr.fromXdrJsonElement(JsonPrimitive("-1"))
        )
        assertEquals(
            int256(Long.MAX_VALUE, ULong.MAX_VALUE, ULong.MAX_VALUE, ULong.MAX_VALUE),
            Int256PartsXdr.fromXdrJsonElement(JsonPrimitive(INT256_MAXIMUM))
        )
        assertEquals(
            int256(Long.MIN_VALUE, 0uL, 0uL, 0uL),
            Int256PartsXdr.fromXdrJsonElement(JsonPrimitive(INT256_MINIMUM))
        )
    }

    @Test
    fun uint256RendersAsASingleBaseTenString() {
        assertEquals("0", text(uint256(0uL, 0uL, 0uL, 0uL).toXdrJsonElement()))
        assertEquals("1", text(uint256(0uL, 0uL, 0uL, 1uL).toXdrJsonElement()))
        assertEquals(
            UINT256_MAXIMUM,
            text(
                uint256(ULong.MAX_VALUE, ULong.MAX_VALUE, ULong.MAX_VALUE, ULong.MAX_VALUE)
                    .toXdrJsonElement()
            )
        )
    }

    @Test
    fun uint256ReadsItsLimbsBackFromTheDecimalString() {
        assertEquals(
            uint256(0uL, 0uL, 0uL, 0uL),
            UInt256PartsXdr.fromXdrJsonElement(JsonPrimitive("0"))
        )
        assertEquals(
            uint256(0uL, 0uL, 0uL, 1uL),
            UInt256PartsXdr.fromXdrJsonElement(JsonPrimitive("1"))
        )
        assertEquals(
            uint256(ULong.MAX_VALUE, ULong.MAX_VALUE, ULong.MAX_VALUE, ULong.MAX_VALUE),
            UInt256PartsXdr.fromXdrJsonElement(JsonPrimitive(UINT256_MAXIMUM))
        )
    }

    // -------------------------------------------------------------------------------------
    // Refusals: broken strkeys, strkeys of the wrong kind, prefixes no arm claims
    // -------------------------------------------------------------------------------------

    @Test
    fun anAccountKeyRejectsAStrkeyWhoseChecksumDoesNotHold() {
        val broken = ACCOUNT_STRKEY.dropLast(1) + "A"
        val error = rejects { PublicKeyXdr.fromXdrJsonElement(JsonPrimitive(broken)) }
        assertTrue(error.message!!.startsWith("PublicKeyXdr: "), error.message!!)
        assertTrue(error.message!!.contains("a G strkey"), error.message!!)
    }

    @Test
    fun anAddressRejectsAStrkeyWhoseChecksumDoesNotHold() {
        val broken = CONTRACT_STRKEY.dropLast(1) + "A"
        val error = rejects { SCAddressXdr.fromXdrJsonElement(JsonPrimitive(broken)) }
        assertTrue(error.message!!.contains("ContractIDXdr"), error.message!!)
    }

    @Test
    fun anAccountKeyRejectsAContractStrkey() {
        val error = rejects { AccountIDXdr.fromXdrJsonElement(JsonPrimitive(CONTRACT_STRKEY)) }
        assertTrue(error.message!!.contains("a G strkey"), error.message!!)
    }

    @Test
    fun aContractIdRejectsAnAccountStrkey() {
        val error = rejects { ContractIDXdr.fromXdrJsonElement(JsonPrimitive(ACCOUNT_STRKEY)) }
        assertTrue(error.message!!.startsWith("ContractIDXdr: "), error.message!!)
        assertTrue(error.message!!.contains("a C strkey"), error.message!!)
    }

    @Test
    fun aPoolIdRejectsAContractStrkey() {
        val error = rejects { PoolIDXdr.fromXdrJsonElement(JsonPrimitive(CONTRACT_STRKEY)) }
        assertTrue(error.message!!.contains("an L strkey"), error.message!!)
    }

    @Test
    fun aClaimableBalanceIdRejectsAnAccountStrkey() {
        val error = rejects {
            ClaimableBalanceIDXdr.fromXdrJsonElement(JsonPrimitive(ACCOUNT_STRKEY))
        }
        assertTrue(error.message!!.contains("a B strkey"), error.message!!)
    }

    @Test
    fun aMuxedAccountStructRejectsAnAccountStrkey() {
        val error = rejects {
            MuxedAccountMed25519Xdr.fromXdrJsonElement(JsonPrimitive(ACCOUNT_STRKEY))
        }
        assertTrue(error.message!!.contains("an M strkey"), error.message!!)
    }

    @Test
    fun aSignedPayloadRejectsAnAccountStrkey() {
        val error = rejects {
            SignerKeyEd25519SignedPayloadXdr.fromXdrJsonElement(JsonPrimitive(ACCOUNT_STRKEY))
        }
        assertTrue(error.message!!.contains("a P strkey"), error.message!!)
    }

    @Test
    fun aMuxedAccountRejectsAPrefixThatIsNeitherGNorM() {
        val error = rejects { MuxedAccountXdr.fromXdrJsonElement(JsonPrimitive(CONTRACT_STRKEY)) }
        assertEquals(
            "MuxedAccountXdr: expects a G or M strkey, got \"$CONTRACT_STRKEY\"",
            error.message
        )
    }

    @Test
    fun aMuxedAccountRejectsAnEmptyString() {
        val error = rejects { MuxedAccountXdr.fromXdrJsonElement(JsonPrimitive("")) }
        assertTrue(error.message!!.contains("expects a G or M strkey"), error.message!!)
    }

    @Test
    fun anAddressRejectsAPrefixOutsideItsFiveForms() {
        val error = rejects { SCAddressXdr.fromXdrJsonElement(JsonPrimitive(PRE_AUTH_TX_STRKEY)) }
        assertEquals(
            "SCAddressXdr: expects a G, C, M, B or L strkey, got \"$PRE_AUTH_TX_STRKEY\"",
            error.message
        )
    }

    @Test
    fun anAddressRejectsAnEmptyString() {
        val error = rejects { SCAddressXdr.fromXdrJsonElement(JsonPrimitive("")) }
        assertTrue(error.message!!.contains("expects a G, C, M, B or L strkey"), error.message!!)
    }

    @Test
    fun aSignerKeyRejectsAPrefixOutsideItsFourForms() {
        val error = rejects { SignerKeyXdr.fromXdrJsonElement(JsonPrimitive(CONTRACT_STRKEY)) }
        assertEquals(
            "SignerKeyXdr: expects a G, T, X or P strkey, got \"$CONTRACT_STRKEY\"",
            error.message
        )
    }

    @Test
    fun aSignerKeyRejectsAnEmptyString() {
        val error = rejects { SignerKeyXdr.fromXdrJsonElement(JsonPrimitive("")) }
        assertTrue(error.message!!.contains("expects a G, T, X or P strkey"), error.message!!)
    }

    /** A B strkey carries a leading type byte; only the one type the XDR declares is readable. */
    @Test
    fun aClaimableBalanceIdRejectsALeadingTypeByteThatNamesNoType() {
        val strkey = StrKey.encodeClaimableBalance(byteArrayOf(0x01) + filled(0x55))
        val error = rejects { ClaimableBalanceIDXdr.fromXdrJsonElement(JsonPrimitive(strkey)) }
        assertEquals("ClaimableBalanceIDXdr: has no type numbered 1", error.message)
    }

    @Test
    fun assetCodeOfFourRejectsACodeWiderThanFourBytes() {
        val error = rejects { AssetCode4Xdr.fromXdrJsonElement(JsonPrimitive("ABCDE")) }
        assertEquals("AssetCode4Xdr: expects an asset code of at most 4 bytes, got 5", error.message)
    }

    @Test
    fun assetCodeOfTwelveRejectsACodeWiderThanTwelveBytes() {
        val error = rejects { AssetCode12Xdr.fromXdrJsonElement(JsonPrimitive("ABCDEFGHIJKLM")) }
        assertEquals(
            "AssetCode12Xdr: expects an asset code of at most 12 bytes, got 13",
            error.message
        )
    }

    @Test
    fun assetCodeUnionRejectsACodeWiderThanTwelveBytes() {
        val error = rejects { AssetCodeXdr.fromXdrJsonElement(JsonPrimitive("ABCDEFGHIJKLM")) }
        assertEquals(
            "AssetCodeXdr: expects an asset code of at most 12 bytes, got 13",
            error.message
        )
    }

    @Test
    fun int128RejectsAValueOutsideItsRange() {
        rejects {
            Int128PartsXdr.fromXdrJsonElement(
                JsonPrimitive("170141183460469231731687303715884105728")
            )
        }
        rejects {
            Int128PartsXdr.fromXdrJsonElement(
                JsonPrimitive("-170141183460469231731687303715884105729")
            )
        }
    }

    @Test
    fun uint128RejectsANegativeValueAndOneAboveItsMaximum() {
        rejects { UInt128PartsXdr.fromXdrJsonElement(JsonPrimitive("-1")) }
        rejects {
            UInt128PartsXdr.fromXdrJsonElement(
                JsonPrimitive("340282366920938463463374607431768211456")
            )
        }
    }

    @Test
    fun int256RejectsAValueOutsideItsRange() {
        rejects { Int256PartsXdr.fromXdrJsonElement(JsonPrimitive(INT256_ABOVE_MAXIMUM)) }
        rejects { Int256PartsXdr.fromXdrJsonElement(JsonPrimitive(INT256_BELOW_MINIMUM)) }
    }

    @Test
    fun uint256RejectsANegativeValueAndOneAboveItsMaximum() {
        rejects { UInt256PartsXdr.fromXdrJsonElement(JsonPrimitive("-1")) }
        rejects { UInt256PartsXdr.fromXdrJsonElement(JsonPrimitive(UINT256_ABOVE_MAXIMUM)) }
    }

    private fun int256(hiHi: Long, hiLo: ULong, loHi: ULong, loLo: ULong): Int256PartsXdr =
        Int256PartsXdr(Int64Xdr(hiHi), Uint64Xdr(hiLo), Uint64Xdr(loHi), Uint64Xdr(loLo))

    private fun uint256(hiHi: ULong, hiLo: ULong, loHi: ULong, loLo: ULong): UInt256PartsXdr =
        UInt256PartsXdr(Uint64Xdr(hiHi), Uint64Xdr(hiLo), Uint64Xdr(loHi), Uint64Xdr(loLo))

    private companion object {

        /** The G strkey of an account key of thirty-two 0x11 bytes. */
        const val ACCOUNT_STRKEY: String =
            "GAIRCEIRCEIRCEIRCEIRCEIRCEIRCEIRCEIRCEIRCEIRCEIRCEIRCF6M"

        /** The C strkey of a contract hash of thirty-two 0x33 bytes. */
        const val CONTRACT_STRKEY: String =
            "CAZTGMZTGMZTGMZTGMZTGMZTGMZTGMZTGMZTGMZTGMZTGMZTGMZTGGJH"

        /** The L strkey of a pool hash of thirty-two 0x44 bytes. */
        const val POOL_STRKEY: String =
            "LBCEIRCEIRCEIRCEIRCEIRCEIRCEIRCEIRCEIRCEIRCEIRCEIRCEJENU"

        /** The B strkey of the type-zero claimable balance with a hash of thirty-two 0x55 bytes. */
        const val BALANCE_STRKEY: String =
            "BAAFKVKVKVKVKVKVKVKVKVKVKVKVKVKVKVKVKVKVKVKVKVKVKVKVKVO7FU"

        /** The M strkey of the same account key multiplexed by id 1. */
        const val MUXED_STRKEY_ID_ONE: String =
            "MAIRCEIRCEIRCEIRCEIRCEIRCEIRCEIRCEIRCEIRCEIRCEIRCEIRCAAAAAAAAAAAAE76M"

        const val MUXED_STRKEY_ID_ZERO: String =
            "MAIRCEIRCEIRCEIRCEIRCEIRCEIRCEIRCEIRCEIRCEIRCEIRCEIRCAAAAAAAAAAAAAPPM"

        const val MUXED_STRKEY_ID_MAXIMUM: String =
            "MAIRCEIRCEIRCEIRCEIRCEIRCEIRCEIRCEIRCEIRCEIRCEIRCEIRD77777777777777VA"

        /** The M strkey of the same account key multiplexed by id 0x0102030405060708. */
        const val MUXED_STRKEY_ORDERED_ID: String =
            "MAIRCEIRCEIRCEIRCEIRCEIRCEIRCEIRCEIRCEIRCEIRCEIRCEIRCAICAMCAKBQHBCZIA"

        /** The T strkey of a pre-authorised transaction hash of thirty-two 0x66 bytes. */
        const val PRE_AUTH_TX_STRKEY: String =
            "TBTGMZTGMZTGMZTGMZTGMZTGMZTGMZTGMZTGMZTGMZTGMZTGMZTGMDCD"

        /** The X strkey of a preimage hash of thirty-two 0x77 bytes. */
        const val HASH_X_STRKEY: String =
            "XB3XO53XO53XO53XO53XO53XO53XO53XO53XO53XO53XO53XO53XPU6T"

        /** The P strkey of the same account key signing the payload 01020304. */
        const val SIGNED_PAYLOAD_STRKEY: String =
            "PAIRCEIRCEIRCEIRCEIRCEIRCEIRCEIRCEIRCEIRCEIRCEIRCEIRCAAAAACACAQDATK5U"

        /** The P strkey of the same account key signing sixty-four 0xab bytes. */
        const val SIGNED_PAYLOAD_STRKEY_SIXTY_FOUR: String =
            "PAIRCEIRCEIRCEIRCEIRCEIRCEIRCEIRCEIRCEIRCEIRCEIRCEIRCAAAABAKXK5LVOV2XK5" +
                "LVOV2XK5LVOV2XK5LVOV2XK5LVOV2XK5LVOV2XK5LVOV2XK5LVOV2XK5LVOV2XK5LVOV2XK5L" +
                "VOV2XK5LVOV2XK5LVOQXO"

        /**
         * The P strkey of an all-0xab account key signing the single byte 0x07. A different
         * account key from the constants above, because this value is pinned directly against
         * the reference rendering of that exact input.
         */
        const val SIGNED_PAYLOAD_STRKEY_ONE: String =
            "PCV2XK5LVOV2XK5LVOV2XK5LVOV2XK5LVOV2XK5LVOV2XK5LVOV2WAAAAAAQOAAAAB4FM"

        const val INT256_MAXIMUM: String =
            "57896044618658097711785492504343953926634992332820282019728792003956564819967"

        const val INT256_MINIMUM: String =
            "-57896044618658097711785492504343953926634992332820282019728792003956564819968"

        const val UINT256_MAXIMUM: String =
            "115792089237316195423570985008687907853269984665640564039457584007913129639935"

        const val INT256_ABOVE_MAXIMUM: String =
            "57896044618658097711785492504343953926634992332820282019728792003956564819968"

        const val INT256_BELOW_MINIMUM: String =
            "-57896044618658097711785492504343953926634992332820282019728792003956564819969"

        const val UINT256_ABOVE_MAXIMUM: String =
            "115792089237316195423570985008687907853269984665640564039457584007913129639936"
    }
}
