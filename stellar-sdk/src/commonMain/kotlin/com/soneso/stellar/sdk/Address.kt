package com.soneso.stellar.sdk

import com.soneso.stellar.sdk.xdr.*

/**
 * Represents a single address in the Stellar network. An address can represent an account,
 * contract, muxed account, claimable balance, or liquidity pool.
 *
 * ## Usage
 *
 * ```kotlin
 * // Parse from string
 * val accountAddress = Address("GABC...")
 * val contractAddress = Address("CCJZ5D...")
 * val muxedAddress = Address("MABC...")
 *
 * // Create from bytes
 * val address = Address.fromAccount(publicKeyBytes)
 * val contract = Address.fromContract(contractIdBytes)
 *
 * // Convert to XDR
 * val scAddress = address.toSCAddress()
 * val scVal = address.toSCVal()
 *
 * // Get encoded string
 * val encoded = address.toString() // G..., C..., M..., B..., or L...
 * ```
 *
 * @property addressType The type of this address
 * @constructor Creates a new [Address] from a Stellar public key (G...), contract ID (C...),
 *              muxed account ID (M...), liquidity pool ID (L...), or claimable balance ID (B...).
 * @param address the StrKey encoded format of Stellar address
 * @throws IllegalArgumentException if the address is invalid or unsupported
 */
class Address(address: String) {

    val addressType: AddressType
    private val key: ByteArray

    init {
        when {
            StrKey.isValidEd25519PublicKey(address) -> {
                addressType = AddressType.ACCOUNT
                key = StrKey.decodeEd25519PublicKey(address)
            }
            StrKey.isValidContract(address) -> {
                addressType = AddressType.CONTRACT
                key = StrKey.decodeContract(address)
            }
            StrKey.isValidMed25519PublicKey(address) -> {
                addressType = AddressType.MUXED_ACCOUNT
                key = StrKey.decodeMed25519PublicKey(address)
            }
            StrKey.isValidClaimableBalance(address) -> {
                addressType = AddressType.CLAIMABLE_BALANCE
                key = StrKey.decodeClaimableBalance(address)
            }
            StrKey.isValidLiquidityPool(address) -> {
                addressType = AddressType.LIQUIDITY_POOL
                key = StrKey.decodeLiquidityPool(address)
            }
            else -> {
                throw IllegalArgumentException("Unsupported address type")
            }
        }
    }

    /**
     * Returns the byte array of the Stellar public key or contract ID.
     *
     * @return the byte array of the Stellar public key or contract ID
     */
    fun getBytes(): ByteArray = key.copyOf()

    /**
     * Gets the encoded string representation of this address.
     *
     * @return The StrKey-encoded representation of this address
     * @throws IllegalArgumentException if the address type is unknown
     */
    fun getEncodedAddress(): String {
        return when (addressType) {
            AddressType.ACCOUNT -> StrKey.encodeEd25519PublicKey(key)
            AddressType.CONTRACT -> StrKey.encodeContract(key)
            AddressType.MUXED_ACCOUNT -> StrKey.encodeMed25519PublicKey(key)
            AddressType.CLAIMABLE_BALANCE -> StrKey.encodeClaimableBalance(key)
            AddressType.LIQUIDITY_POOL -> StrKey.encodeLiquidityPool(key)
        }
    }

    /**
     * Converts this object to its [SCAddressXdr] XDR object representation.
     *
     * @return a new [SCAddressXdr] object from this object
     * @throws IllegalArgumentException if the address type is unsupported or data is invalid
     */
    fun toSCAddress(): SCAddressXdr {
        return when (addressType) {
            AddressType.ACCOUNT -> {
                val accountId = KeyPair.fromPublicKey(key).getXdrAccountId()
                SCAddressXdr.AccountId(accountId)
            }
            AddressType.CONTRACT -> {
                val hash = HashXdr(key.copyOf())
                SCAddressXdr.ContractId(ContractIDXdr(hash))
            }
            AddressType.MUXED_ACCOUNT -> {
                val parameter = fromRawMuxedAccountStrKey(key)
                val muxedAccount = MuxedEd25519AccountXdr(
                    id = parameter.id,
                    ed25519 = parameter.ed25519
                )
                SCAddressXdr.MuxedAccount(muxedAccount)
            }
            AddressType.CLAIMABLE_BALANCE -> {
                require(key[0] == 0.toByte()) {
                    "The claimable balance ID type is not supported, it must be `CLAIMABLE_BALANCE_ID_TYPE_V0`."
                }
                val hashBytes = key.copyOfRange(1, key.size)
                val hash = HashXdr(hashBytes)
                val claimableBalanceId = ClaimableBalanceIDXdr.V0(hash)
                SCAddressXdr.ClaimableBalanceId(claimableBalanceId)
            }
            AddressType.LIQUIDITY_POOL -> {
                val hash = HashXdr(key.copyOf())
                SCAddressXdr.LiquidityPoolId(PoolIDXdr(hash))
            }
        }
    }

    /**
     * Converts this object to its [SCValXdr] XDR object representation.
     *
     * @return a new [SCValXdr] object from this object
     */
    fun toSCVal(): SCValXdr {
        return SCValXdr.Address(toSCAddress())
    }

    override fun toString(): String = getEncodedAddress()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as Address

        if (addressType != other.addressType) return false
        if (!key.contentEquals(other.key)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = addressType.hashCode()
        result = 31 * result + key.contentHashCode()
        return result
    }

    /**
     * Represents the type of the address.
     */
    enum class AddressType {
        /** Account address (G...) */
        ACCOUNT,
        /** Contract address (C...) */
        CONTRACT,
        /** Muxed account address (M...) */
        MUXED_ACCOUNT,
        /** Claimable balance ID (B...) */
        CLAIMABLE_BALANCE,
        /** Liquidity pool ID (L...) */
        LIQUIDITY_POOL
    }

    companion object {
        /**
         * Creates a new [Address] from a Stellar public key.
         *
         * @param accountId the byte array of the Stellar public key (G...)
         * @return a new [Address] object from the given Stellar public key
         */
        fun fromAccount(accountId: ByteArray): Address {
            return Address(StrKey.encodeEd25519PublicKey(accountId))
        }

        /**
         * Creates a new [Address] from a Stellar Contract ID.
         *
         * @param contractId the byte array of the Stellar Contract ID
         * @return a new [Address] object from the given Stellar Contract ID
         */
        fun fromContract(contractId: ByteArray): Address {
            return Address(StrKey.encodeContract(contractId))
        }

        /**
         * Creates a new [Address] from a Stellar muxed account ID.
         *
         * @param muxedAccountId the byte array of the Stellar muxed account ID (M...)
         * @return a new [Address] object from the given Stellar muxed account ID
         */
        fun fromMuxedAccount(muxedAccountId: ByteArray): Address {
            return Address(StrKey.encodeMed25519PublicKey(muxedAccountId))
        }

        /**
         * Creates a new [Address] from a Stellar Claimable Balance ID.
         *
         * @param claimableBalanceId the byte array of the Stellar Claimable Balance ID (B...)
         * @return a new [Address] object from the given Stellar Claimable Balance ID
         */
        fun fromClaimableBalance(claimableBalanceId: ByteArray): Address {
            return Address(StrKey.encodeClaimableBalance(claimableBalanceId))
        }

        /**
         * Creates a new [Address] from a Stellar Liquidity Pool ID.
         *
         * @param liquidityPoolId the byte array of the Stellar Liquidity Pool ID (L...)
         * @return a new [Address] object from the given Stellar Liquidity Pool ID
         */
        fun fromLiquidityPool(liquidityPoolId: ByteArray): Address {
            return Address(StrKey.encodeLiquidityPool(liquidityPoolId))
        }

        /**
         * Creates a new [Address] from a [SCAddressXdr] XDR object.
         *
         * @param scAddress the [SCAddressXdr] object to convert
         * @return a new [Address] object from the given XDR object
         * @throws IllegalArgumentException if the address type is unsupported
         */
        fun fromSCAddress(scAddress: SCAddressXdr): Address {
            return when (scAddress) {
                is SCAddressXdr.AccountId -> {
                    val publicKey = when (val pk = scAddress.value.value) {
                        is PublicKeyXdr.Ed25519 -> pk.value.value
                        else -> throw IllegalArgumentException("Unsupported public key type")
                    }
                    fromAccount(publicKey)
                }
                is SCAddressXdr.ContractId -> {
                    fromContract(scAddress.value.value.value)
                }
                is SCAddressXdr.MuxedAccount -> {
                    val rawBytes = toRawMuxedAccountStrKey(
                        RawMuxedAccountStrKeyParameter(
                            ed25519 = scAddress.value.ed25519,
                            id = scAddress.value.id
                        )
                    )
                    fromMuxedAccount(rawBytes)
                }
                is SCAddressXdr.ClaimableBalanceId -> {
                    when (val cbId = scAddress.value) {
                        is ClaimableBalanceIDXdr.V0 -> {
                            val v0Bytes = cbId.value.value
                            val withZeroPrefix = ByteArray(v0Bytes.size + 1)
                            withZeroPrefix[0] = 0x00
                            v0Bytes.copyInto(withZeroPrefix, destinationOffset = 1)
                            fromClaimableBalance(withZeroPrefix)
                        }
                    }
                }
                is SCAddressXdr.LiquidityPoolId -> {
                    fromLiquidityPool(scAddress.value.value.value)
                }
            }
        }

        /**
         * Creates a new [Address] from a [SCValXdr] XDR object.
         *
         * @param scVal the [SCValXdr] object to convert
         * @return a new [Address] object from the given XDR object
         * @throws IllegalArgumentException if the scVal type is not SCV_ADDRESS
         */
        fun fromSCVal(scVal: SCValXdr): Address {
            require(scVal is SCValXdr.Address) {
                "invalid scVal type, expected SCV_ADDRESS, but got ${scVal.discriminant}"
            }
            return fromSCAddress(scVal.value)
        }

        /**
         * Helper class to hold muxed account StrKey parameters.
         */
        private data class RawMuxedAccountStrKeyParameter(
            val ed25519: Uint256Xdr,
            val id: Uint64Xdr
        )

        /**
         * Converts muxed account XDR parameters to raw bytes for StrKey encoding.
         *
         * The raw format is 40 bytes: 32 bytes ed25519 public key + 8 bytes big-endian ID.
         *
         * @param parameter the muxed account parameters
         * @return the raw 40-byte representation
         */
        private fun toRawMuxedAccountStrKey(parameter: RawMuxedAccountStrKeyParameter): ByteArray {
            val result = ByteArray(40)
            // Copy ed25519 (32 bytes)
            parameter.ed25519.value.copyInto(result, destinationOffset = 0)
            // Copy ID as big-endian (8 bytes)
            val id = parameter.id.value.toLong()
            result[32] = ((id shr 56) and 0xFF).toByte()
            result[33] = ((id shr 48) and 0xFF).toByte()
            result[34] = ((id shr 40) and 0xFF).toByte()
            result[35] = ((id shr 32) and 0xFF).toByte()
            result[36] = ((id shr 24) and 0xFF).toByte()
            result[37] = ((id shr 16) and 0xFF).toByte()
            result[38] = ((id shr 8) and 0xFF).toByte()
            result[39] = (id and 0xFF).toByte()
            return result
        }

        /**
         * Converts raw muxed account StrKey bytes to XDR parameters.
         *
         * The raw format is 40 bytes: 32 bytes ed25519 public key + 8 bytes big-endian ID.
         *
         * @param data the raw 40-byte muxed account data
         * @return the muxed account parameters
         * @throws IllegalArgumentException if the data is not 40 bytes
         */
        private fun fromRawMuxedAccountStrKey(data: ByteArray): RawMuxedAccountStrKeyParameter {
            require(data.size == 40) {
                "Muxed account bytes must be 40 bytes long, got ${data.size}"
            }
            // Extract ed25519 (first 32 bytes)
            val ed25519Bytes = data.copyOfRange(0, 32)
            // Extract ID (last 8 bytes, big-endian)
            var id = 0L
            for (i in 32..39) {
                id = (id shl 8) or (data[i].toLong() and 0xFF)
            }
            return RawMuxedAccountStrKeyParameter(
                ed25519 = Uint256Xdr(ed25519Bytes),
                id = Uint64Xdr(id.toULong())
            )
        }
    }
}
