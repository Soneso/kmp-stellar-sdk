package com.soneso.stellar.sdk

import com.soneso.stellar.sdk.xdr.ClaimableBalanceIDXdr
import com.soneso.stellar.sdk.xdr.HashXdr

/**
 * A claimable balance id, resolved from any of the spellings it is written in.
 *
 * The same balance reaches an application under four names: the `B...` strkey, the bare hash as
 * hexadecimal, and that hash behind a type discriminant carried either in the single byte the
 * strkey body leads with or in the four big-endian bytes the XDR union writes (the spelling
 * Horizon reports). [forId] reads all of them and this type reports the one balance they name, so a
 * caller may pass on whichever spelling it received.
 *
 * @property hashHex the 32-byte balance hash as 64 lower case hexadecimal characters, the
 * canonical spelling of the balance this id names
 */
class ClaimableBalanceId private constructor(val hashHex: String) {

    /**
     * The id in the spelling Horizon reports: the hexadecimal of the XDR wire form, the type
     * discriminant as four big-endian bytes ahead of the hash, in lower case.
     */
    fun toPaddedHex(): String = XDR_DISCRIMINANT_HEX + hashHex

    /**
     * The id as the `B...` strkey, 58 characters long.
     */
    fun toStrKey(): String = StrKey.encodeClaimableBalance(Util.hexToBytes(hashHex))

    /**
     * The id as the XDR union the wire form and the operations carry.
     */
    fun toXdr(): ClaimableBalanceIDXdr =
        ClaimableBalanceIDXdr.V0(HashXdr(Util.hexToBytes(hashHex)))

    override fun equals(other: Any?): Boolean =
        this === other || (other is ClaimableBalanceId && other.hashHex == hashHex)

    override fun hashCode(): Int = hashHex.hashCode()

    override fun toString(): String = "ClaimableBalanceId($hashHex)"

    companion object {

        /** The character a claimable balance strkey begins with. */
        private const val STRKEY_PREFIX = "B"

        /**
         * The one type discriminant the XDR union declares, as the hexadecimal here is read:
         * a value across as many bytes as the spelling carries, rather than the single byte
         * the strkey body holds it in.
         */
        private val V0_DISCRIMINANT: Long = StrKey.CLAIMABLE_BALANCE_V0_DISCRIMINANT.toLong()

        /** Characters a claimable balance strkey (B...) has. */
        private val STRKEY_LENGTH: Int = StrKey.CLAIMABLE_BALANCE_STRKEY_LENGTH

        /** Characters the bare balance hash has in hexadecimal. */
        private val HASH_HEX_LENGTH: Int = StrKey.CLAIMABLE_BALANCE_HASH_SIZE * 2

        /** Characters the type discriminant of the strkey body has in hexadecimal. */
        private val STRKEY_DISCRIMINANT_HEX_LENGTH: Int =
            StrKey.CLAIMABLE_BALANCE_DISCRIMINANT_SIZE * 2

        /** Characters the type discriminant of the XDR wire form has in hexadecimal. */
        private val XDR_DISCRIMINANT_HEX_LENGTH: Int = StrKey.XDR_UNION_DISCRIMINANT_SIZE * 2

        /** Characters the strkey body has in hexadecimal: the discriminant and the hash. */
        private val BODY_HEX_LENGTH: Int = StrKey.CLAIMABLE_BALANCE_BODY_SIZE * 2

        /** Characters the XDR wire form has in hexadecimal: the discriminant and the hash. */
        private val XDR_HEX_LENGTH: Int = StrKey.CLAIMABLE_BALANCE_XDR_SIZE * 2

        /** The type discriminant of the XDR wire form, in the hexadecimal a padded id opens with. */
        private val XDR_DISCRIMINANT_HEX: String = "0".repeat(XDR_DISCRIMINANT_HEX_LENGTH)

        /**
         * Reads [claimableBalanceId] in whichever spelling it holds and returns the balance it
         * names.
         *
         * A string as long as a strkey is read as one: the base32 and the hexadecimal alphabets
         * overlap, but no hexadecimal spelling has that length, so the strkey reading is the
         * only one that can succeed. A hexadecimal spelling is read in either case, and every
         * byte of a discriminant it carries is judged, so an id that names another type is
         * rejected rather than having its high bytes dropped.
         *
         * @param claimableBalanceId the balance id as a `B...` strkey, as the bare hash in
         * hexadecimal, or as the hash behind a type discriminant of either width in hexadecimal
         * @return the balance the given spelling names
         * @throws IllegalArgumentException if [claimableBalanceId] is as long as a strkey but
         * does not begin with `B`, if it is not a strkey this codec accepts, if its length
         * matches none of the accepted shapes, if it is not hexadecimal, or if it carries a
         * discriminant naming no claimable balance id type
         */
        fun forId(claimableBalanceId: String): ClaimableBalanceId {
            if (claimableBalanceId.length == STRKEY_LENGTH) {
                require(claimableBalanceId.startsWith(STRKEY_PREFIX)) {
                    "a $STRKEY_LENGTH character claimable balance id must be a strkey " +
                        "beginning with \"$STRKEY_PREFIX\""
                }
                // The decode judges the checksum and the type discriminant and hands back the
                // body: the discriminant followed by the hash.
                val body = StrKey.decodeClaimableBalance(claimableBalanceId)
                return ClaimableBalanceId(
                    Util.bytesToHex(
                        body.copyOfRange(StrKey.CLAIMABLE_BALANCE_DISCRIMINANT_SIZE, body.size)
                    )
                )
            }

            val discriminantLength = when (claimableBalanceId.length) {
                HASH_HEX_LENGTH -> 0
                BODY_HEX_LENGTH -> STRKEY_DISCRIMINANT_HEX_LENGTH
                XDR_HEX_LENGTH -> XDR_DISCRIMINANT_HEX_LENGTH
                else -> throw IllegalArgumentException(
                    "claimable balance id must be a $STRKEY_LENGTH character strkey " +
                        "($STRKEY_PREFIX...), or hex of the bare id ($HASH_HEX_LENGTH " +
                        "characters), which a discriminant may prefix to $BODY_HEX_LENGTH or " +
                        "$XDR_HEX_LENGTH characters; ${claimableBalanceId.length} characters given"
                )
            }

            require(claimableBalanceId.all { it.isHexDigit() }) {
                "claimable balance id \"$claimableBalanceId\" is not hexadecimal"
            }

            // Hexadecimal is case insensitive; lower case is the canonical spelling, so one
            // balance is reported under one string whichever case it arrived in.
            val value = claimableBalanceId.lowercase()
            if (discriminantLength > 0) {
                val carried = value.substring(0, discriminantLength).toLong(16)
                require(carried == V0_DISCRIMINANT) {
                    "claimable balance id carries the discriminant 0x${carried.toString(16)}, " +
                        "which names no claimable balance id type"
                }
            }
            return ClaimableBalanceId(value.substring(value.length - HASH_HEX_LENGTH))
        }

        /**
         * Reads the balance id an XDR union carries.
         *
         * @param balanceId the balance id as the XDR union declares it
         * @return the balance the union names
         */
        fun fromXdr(balanceId: ClaimableBalanceIDXdr): ClaimableBalanceId = when (balanceId) {
            is ClaimableBalanceIDXdr.V0 -> ClaimableBalanceId(Util.bytesToHex(balanceId.value.value))
        }

        private fun Char.isHexDigit(): Boolean =
            this in '0'..'9' || this in 'a'..'f' || this in 'A'..'F'
    }
}
