package com.soneso.stellar.sdk.unitTests

/**
 * The one claimable balance the spelling tests are written around, in every spelling that names
 * it and in spellings that name none.
 */
internal object ClaimableBalanceVectors {

    /** The 32-byte hash SEP-0023 builds its strkey vectors on, and the balance these name. */
    const val hashHex = "3f0c34bf93ad0d9971d04ccc90f705511c838aad9734a4a2fb0d7a03fc7fe89a"

    /** That balance as a claimable balance id: the SEP-0023 valid vector for the type. */
    const val strKey = "BAAD6DBUX6J22DMZOHIEZTEQ64CVCHEDRKWZONFEUL5Q26QD7R76RGR4TU"

    /** The hash behind the single discriminant byte the strkey body leads with. */
    const val bodyHex = "00$hashHex"

    /** The hash behind the four-byte union discriminant, the spelling Horizon reports. */
    const val paddedHex = "00000000$hashHex"

    /** Every spelling of the one balance, the strkey and a case variant among them. */
    val spellings = listOf(strKey, hashHex, bodyHex, paddedHex, hashHex.uppercase())

    /**
     * Ids naming no balance: a discriminant the union does not declare, in the strkey body width
     * and in the XDR width where it sits above the last byte, and hexadecimal the alphabet does
     * not contain.
     */
    val rejections = listOf("01$hashHex", "01000000$hashHex", "z".repeat(72))
}
