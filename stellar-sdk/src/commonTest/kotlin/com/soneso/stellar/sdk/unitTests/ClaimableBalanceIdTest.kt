package com.soneso.stellar.sdk.unitTests

import com.soneso.stellar.sdk.ClaimableBalanceId
import com.soneso.stellar.sdk.xdr.ClaimableBalanceIDXdr
import com.soneso.stellar.sdk.xdr.HashXdr
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * The spellings a claimable balance id is written in, and the one balance they name.
 */
class ClaimableBalanceIdTest {

    /** The 32-byte balance hash the vectors are built around. */
    private val hashHex = "3f0c34bf93ad0d9971d04ccc90f705511c838aad9734a4a2fb0d7a03fc7fe89a"

    private val strKey = "BAAD6DBUX6J22DMZOHIEZTEQ64CVCHEDRKWZONFEUL5Q26QD7R76RGR4TU"

    /** The hash behind the single discriminant byte the strkey body leads with. */
    private val bodyHex = "00$hashHex"

    /** The hash behind the four-byte union discriminant, the spelling Horizon reports. */
    private val paddedHex = "00000000$hashHex"

    /** Every spelling of the one balance, the strkey and a case variant among them. */
    private val spellings = listOf(strKey, hashHex, bodyHex, paddedHex, hashHex.uppercase())

    private val spellingMessage =
        "claimable balance id must be a 58 character strkey (B...), or hex of the bare id " +
            "(64 characters), which a discriminant may prefix to 66 or 72 characters; "

    @Test
    fun testEverySpellingNamesOneBalance() {
        for (spelling in spellings) {
            val resolved = ClaimableBalanceId.forId(spelling)
            assertEquals(hashHex, resolved.hashHex, "spelling: $spelling")
            assertEquals(paddedHex, resolved.toPaddedHex(), "spelling: $spelling")
            assertEquals(strKey, resolved.toStrKey(), "spelling: $spelling")
            assertEquals(ClaimableBalanceId.forId(strKey), resolved, "spelling: $spelling")
            assertEquals(
                ClaimableBalanceId.forId(strKey).hashCode(), resolved.hashCode(),
                "spelling: $spelling"
            )
        }
    }

    @Test
    fun testHexIsReadInEitherCaseAndReportedInLowerCase() {
        for (spelling in listOf(bodyHex.uppercase(), paddedHex.uppercase())) {
            val resolved = ClaimableBalanceId.forId(spelling)
            assertEquals(hashHex, resolved.hashHex, "spelling: $spelling")
            assertEquals(paddedHex, resolved.toPaddedHex(), "spelling: $spelling")
        }
    }

    @Test
    fun testAStrKeyIsNotReadInLowerCase() {
        // Hex reads in either case; a strkey does not. The lowercase form keeps its 58
        // characters, so it is read as a strkey and rejected for its leading character.
        val failure = assertFailsWith<IllegalArgumentException> {
            ClaimableBalanceId.forId(strKey.lowercase())
        }
        assertEquals(
            "a 58 character claimable balance id must be a strkey beginning with \"B\"",
            failure.message
        )
    }

    @Test
    fun testTheIdRoundTripsThroughTheXdrUnion() {
        val resolved = ClaimableBalanceId.forId(strKey)
        val xdr = resolved.toXdr()
        assertTrue(xdr is ClaimableBalanceIDXdr.V0, "the union declares one case")
        assertEquals(
            hashHex,
            xdr.value.value.joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') },
            "the union must carry the balance hash"
        )
        assertEquals(resolved, ClaimableBalanceId.fromXdr(xdr))
        assertEquals(strKey, ClaimableBalanceId.fromXdr(xdr).toStrKey())
    }

    @Test
    fun testTheXdrUnionIsReadWhicheverSpellingBuiltIt() {
        for (spelling in spellings) {
            assertEquals(
                paddedHex,
                ClaimableBalanceId.fromXdr(ClaimableBalanceId.forId(spelling).toXdr()).toPaddedHex(),
                "spelling: $spelling"
            )
        }
    }

    @Test
    fun testAnIdAsLongAsAStrKeyMustBeOne() {
        // 58 characters is a length no hexadecimal spelling has, so a string of that length is
        // read as a strkey and as nothing else, whatever alphabet its characters belong to.
        val notAStrKey = "A" + strKey.drop(1)
        val failure = assertFailsWith<IllegalArgumentException> {
            ClaimableBalanceId.forId(notAStrKey)
        }
        assertEquals(
            "a 58 character claimable balance id must be a strkey beginning with \"B\"",
            failure.message
        )
    }

    @Test
    fun testAMalformedStrKeyIsRejected() {
        // The last character carries part of the checksum. "Q" leaves the unused trailing bits
        // zero as "U" does, so the checksum is the check that rejects the result.
        val broken = strKey.dropLast(1) + "Q"
        val failure = assertFailsWith<IllegalArgumentException> {
            ClaimableBalanceId.forId(broken)
        }
        assertEquals("Checksum invalid", failure.message)
    }

    @Test
    fun testALengthNoSpellingHasIsRejected() {
        // The 63-character id below has a length no spelling has. Around it sit the lengths
        // one character either side of each accepted shape, which a resolver that padded or
        // truncated would take.
        val candidates = listOf(
            "",
            "BAAAAAAAJXMXZMNAXINW4GQYRNE55L523HRUZAHCO7BXEX4BLR2XYLIF3X7IL2Y",
            hashHex.dropLast(1),
            hashHex + "0",
            bodyHex + "0",
            paddedHex.dropLast(1),
            paddedHex + "0"
        )
        for (candidate in candidates) {
            val failure = assertFailsWith<IllegalArgumentException>(
                "${candidate.length} characters must be rejected"
            ) {
                ClaimableBalanceId.forId(candidate)
            }
            assertEquals(
                spellingMessage + "${candidate.length} characters given",
                failure.message,
                "${candidate.length} characters: the rejection must name the accepted shapes"
            )
        }
    }

    @Test
    fun testAnIdThatIsNotHexadecimalIsRejected() {
        for (candidate in listOf("z".repeat(64), "g".repeat(66), " ".repeat(72))) {
            val failure = assertFailsWith<IllegalArgumentException>(
                "${candidate.length} non-hexadecimal characters must be rejected"
            ) {
                ClaimableBalanceId.forId(candidate)
            }
            assertEquals(
                "claimable balance id \"$candidate\" is not hexadecimal",
                failure.message,
                "${candidate.length} characters: the rejection must name the rule broken"
            )
        }
    }

    @Test
    fun testAStrKeyBodyDiscriminantTheUnionDoesNotDeclareIsRejected() {
        for ((prefix, carried) in mapOf("01" to "0x1", "ff" to "0xff")) {
            val failure = assertFailsWith<IllegalArgumentException>(
                "the body prefixed $prefix must be rejected"
            ) {
                ClaimableBalanceId.forId(prefix + hashHex)
            }
            assertEquals(
                "claimable balance id carries the discriminant $carried, " +
                    "which names no claimable balance id type",
                failure.message,
                "the body prefixed $prefix: the rejection must name the rule broken"
            )
        }
    }

    @Test
    fun testAnXdrDiscriminantTheUnionDoesNotDeclareIsRejected() {
        // The first three carry a value only in the bytes above the last one, which a resolver
        // that judged the last byte alone would let through as the balance the low byte spells.
        val carriedByPrefix = mapOf(
            "01000000" to "0x1000000",
            "00010000" to "0x10000",
            "00000100" to "0x100",
            "00000001" to "0x1",
            "ffffffff" to "0xffffffff"
        )
        for ((prefix, carried) in carriedByPrefix) {
            val failure = assertFailsWith<IllegalArgumentException>(
                "the XDR form prefixed $prefix must be rejected"
            ) {
                ClaimableBalanceId.forId(prefix + hashHex)
            }
            assertEquals(
                "claimable balance id carries the discriminant $carried, " +
                    "which names no claimable balance id type",
                failure.message,
                "the XDR form prefixed $prefix: the rejection must name the rule broken"
            )
        }
    }

    @Test
    fun testAnIdOfAllZerosResolves() {
        // A hash of zeros is indistinguishable from padding, so it is the case that shows the
        // discriminant width, not the leading zeros, decides where the hash begins.
        val zeroHash = "0".repeat(64)
        val fromHash = ClaimableBalanceId.forId(zeroHash)
        assertEquals(zeroHash, fromHash.hashHex)
        assertEquals("0".repeat(72), fromHash.toPaddedHex())
        assertEquals(fromHash, ClaimableBalanceId.forId("0".repeat(72)))
        assertEquals(fromHash, ClaimableBalanceId.forId("0".repeat(66)))
        assertEquals(fromHash, ClaimableBalanceId.forId(fromHash.toStrKey()))
    }

    @Test
    fun testDifferentBalancesAreNotEqual() {
        val other = ClaimableBalanceId.forId(
            "f5ea7fb3de18dae9f12af96cf0750749016fbcba6bf09e902a69e54568771d82"
        )
        assertTrue(ClaimableBalanceId.forId(strKey) != other)
        assertEquals(
            "ClaimableBalanceId($hashHex)",
            ClaimableBalanceId.forId(strKey).toString()
        )
    }

    @Test
    fun testTheUnionIsReadWhateverBytesItCarries() {
        val zeros = ClaimableBalanceId.fromXdr(ClaimableBalanceIDXdr.V0(HashXdr(ByteArray(32))))
        assertEquals("0".repeat(64), zeros.hashHex)
    }
}
