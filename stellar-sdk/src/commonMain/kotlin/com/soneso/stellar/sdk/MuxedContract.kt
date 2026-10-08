package com.soneso.stellar.sdk

import com.soneso.stellar.sdk.xdr.*
import kotlin.jvm.JvmStatic

/**
 * A contract with an optional 64-bit multiplexing id (CAP-0084, protocol 30 and higher).
 *
 * A muxed contract address pairs a contract with an id the way a muxed account address pairs an
 * account with one. It consists of two parts:
 * - The contract id, which starts with the letter "C"
 * - An optional multiplexing id, a 64-bit unsigned integer
 *
 * When the multiplexing id is set, the address starts with "W" instead of "C". The Stellar
 * Asset Contract accepts a muxed contract address as the recipient of a transfer; it is never
 * an authorization target.
 *
 * ## Usage
 *
 * ```kotlin
 * val muxed = MuxedContract("CA3D5KRYM6CB7OWQ6TWYRR3Z4T7GNZLKERYNZGGA5SOAOPIFY6YQGAXE", 123456UL)
 * println(muxed.address) // WA3D5KRYM6CB7OWQ6TWYRR3Z4T7GNZLKERYNZGGA5SOAOPIFY6YQGAAAAAAAAAPCIA6IG
 *
 * val parsed = MuxedContract("WA3D5KRYM6CB7OWQ6TWYRR3Z4T7GNZLKERYNZGGA5SOAOPIFY6YQGAAAAAAAAAPCIA6IG")
 * println(parsed.contractId) // CA3D5KRY...
 * println(parsed.id)         // 123456
 * ```
 *
 * @see <a href="https://github.com/stellar/stellar-protocol/blob/master/core/cap-0084.md">CAP-0084</a>
 */
class MuxedContract private constructor(scAddress: SCAddressXdr) {
    /**
     * The contract id. It starts with the letter "C".
     */
    val contractId: String

    /**
     * The optional multiplexing id, a 64-bit unsigned integer. Null for a plain contract
     * address.
     */
    val id: ULong?

    init {
        when (scAddress) {
            is SCAddressXdr.ContractId -> {
                contractId = StrKey.encodeContract(scAddress.value.value.value)
                id = null
            }
            is SCAddressXdr.MuxedContract -> {
                contractId = StrKey.encodeContract(scAddress.value.contractId.value.value)
                id = scAddress.value.id.value
            }
            else -> throw IllegalArgumentException(
                "Expected a contract or muxed contract address, got ${scAddress.discriminant}"
            )
        }
    }

    /**
     * Creates a muxed contract from a contract id and a multiplexing id.
     *
     * @param contractId The contract id. It must be a valid contract id starting with "C"
     * @param id The multiplexing id
     * @throws IllegalArgumentException if [contractId] is not a valid contract id
     */
    constructor(contractId: String, id: ULong) : this(
        SCAddressXdr.MuxedContract(MuxedContractXdr(Uint64Xdr(id), contractIdXdr(contractId)))
    )

    /**
     * Creates a muxed contract from a contract address (C...), which leaves [id] null, or a
     * muxed contract address (W...).
     *
     * @param address The contract or muxed contract address
     * @throws IllegalArgumentException if [address] is neither
     */
    constructor(address: String) : this(scAddressOf(address))

    /**
     * The address of this muxed contract: the muxed contract address (W...) if the
     * multiplexing id is set, or the contract address (C...) if it is not.
     */
    val address: String
        get() = toAddress().toString()

    /**
     * Converts this muxed contract to its XDR address: the muxed contract arm if the
     * multiplexing id is set, or the contract arm if it is not.
     *
     * @return The [SCAddressXdr] of this muxed contract
     */
    fun toSCAddress(): SCAddressXdr {
        val contract = contractIdXdr(contractId)
        val muxedId = id ?: return SCAddressXdr.ContractId(contract)
        return SCAddressXdr.MuxedContract(MuxedContractXdr(Uint64Xdr(muxedId), contract))
    }

    /**
     * Converts this muxed contract to an [Address] of type
     * [Address.AddressType.MUXED_CONTRACT] if the multiplexing id is set, or
     * [Address.AddressType.CONTRACT] if it is not.
     *
     * @return The [Address] of this muxed contract
     */
    fun toAddress(): Address = Address.fromSCAddress(toSCAddress())

    companion object {
        /**
         * Creates a muxed contract from its XDR address.
         *
         * @param scAddress The contract arm, which leaves [id] null, or the muxed contract arm
         * @return A new MuxedContract instance
         * @throws IllegalArgumentException if [scAddress] is any other arm
         */
        @JvmStatic
        fun fromSCAddress(scAddress: SCAddressXdr): MuxedContract = MuxedContract(scAddress)

        private fun contractIdXdr(contractId: String): ContractIDXdr {
            require(StrKey.isValidContract(contractId)) { "Invalid contract ID: $contractId" }
            return ContractIDXdr(HashXdr(StrKey.decodeContract(contractId)))
        }

        private fun scAddressOf(address: String): SCAddressXdr {
            require(StrKey.isValidContract(address) || StrKey.isValidMuxedContract(address)) {
                "Invalid contract or muxed contract address: $address"
            }
            return Address(address).toSCAddress()
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as MuxedContract

        if (contractId != other.contractId) return false
        if (id != other.id) return false

        return true
    }

    override fun hashCode(): Int {
        var result = contractId.hashCode()
        result = 31 * result + (id?.hashCode() ?: 0)
        return result
    }

    override fun toString(): String {
        return if (id != null) {
            "MuxedContract(address=$address, contractId=$contractId, id=$id)"
        } else {
            "MuxedContract(address=$address)"
        }
    }
}
