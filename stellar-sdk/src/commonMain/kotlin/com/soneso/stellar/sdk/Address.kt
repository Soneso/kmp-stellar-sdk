package com.soneso.stellar.sdk

import com.soneso.stellar.sdk.xdr.*

/**
 * Represents a single address in the Stellar network. An address can represent an account,
 * contract, muxed account, claimable balance, liquidity pool, or muxed contract.
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
 * val encoded = address.toString() // G..., C..., M..., B..., L..., or W...
 * ```
 *
 * @property addressType The type of this address
 * @constructor Creates a new [Address] from a Stellar public key (G...), contract ID (C...),
 *              muxed account ID (M...), liquidity pool ID (L...), claimable balance ID (B...),
 *              or muxed contract ID (W...).
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
            StrKey.isValidMuxedContract(address) -> {
                addressType = AddressType.MUXED_CONTRACT
                key = StrKey.decodeMuxedContract(address)
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
            AddressType.MUXED_CONTRACT -> StrKey.encodeMuxedContract(key)
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
                val muxedAccount = MuxedEd25519AccountXdr(
                    id = Uint64Xdr(XdrJson.muxedId(key)),
                    ed25519 = Uint256Xdr(key.copyOfRange(0, 32))
                )
                SCAddressXdr.MuxedAccount(muxedAccount)
            }
            AddressType.CLAIMABLE_BALANCE -> {
                // The strkey decoder only admits the V0 type discriminant, so the bytes
                // after it are the V0 hash.
                val hashBytes = key.copyOfRange(1, key.size)
                val hash = HashXdr(hashBytes)
                val claimableBalanceId = ClaimableBalanceIDXdr.V0(hash)
                SCAddressXdr.ClaimableBalanceId(claimableBalanceId)
            }
            AddressType.LIQUIDITY_POOL -> {
                val hash = HashXdr(key.copyOf())
                SCAddressXdr.LiquidityPoolId(PoolIDXdr(hash))
            }
            AddressType.MUXED_CONTRACT -> {
                val muxedContract = MuxedContractXdr(
                    id = Uint64Xdr(XdrJson.muxedId(key)),
                    contractId = ContractIDXdr(HashXdr(key.copyOfRange(0, 32)))
                )
                SCAddressXdr.MuxedContract(muxedContract)
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
        LIQUIDITY_POOL,
        /** Muxed contract address (W...), protocol 30 and higher */
        MUXED_CONTRACT
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
         * Creates a new [Address] from a Stellar muxed contract ID.
         *
         * @param muxedContractId the 40 bytes of the muxed contract ID (W...): the 32-byte
         * contract id followed by the 8-byte big-endian multiplexing id
         * @return a new [Address] object from the given Stellar muxed contract ID
         * @throws IllegalArgumentException if [muxedContractId] is not 40 bytes
         */
        fun fromMuxedContract(muxedContractId: ByteArray): Address {
            return Address(StrKey.encodeMuxedContract(muxedContractId))
        }

        /**
         * Creates a new [Address] from a Stellar Claimable Balance ID.
         *
         * Accepts the 33-byte strkey body (the type discriminant followed by the 32-byte
         * hash), the 32-byte hash alone, or the 36-byte XDR wire form (the four-byte
         * big-endian union discriminant followed by the hash).
         *
         * @param claimableBalanceId the byte array of the Stellar Claimable Balance ID (B...)
         * @return a new [Address] object from the given Stellar Claimable Balance ID
         * @throws IllegalArgumentException if [claimableBalanceId] has none of those widths,
         * or if the discriminant in the 33- or 36-byte form is not the one the XDR union
         * declares
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
         * Derives the contract id a deployment by the given [deployer] with the
         * given [salt] creates on the given [network].
         *
         * The id depends only on the deployer address, the salt and the network;
         * the executable the contract is created with (WASM hash, CAP-85 external
         * reference or Stellar asset) does not enter the derivation. Use it to know
         * a contract's address before deploying, for example when the address is
         * needed in constructor arguments of another contract.
         *
         * @param deployer the address the deployment is issued from (account or contract)
         * @param salt the 32 byte salt the deployment uses
         * @param network the network the contract is deployed to
         * @return the derived contract id ("C...")
         * @throws IllegalArgumentException if [salt] is not exactly 32 bytes
         */
        suspend fun deriveContractId(deployer: Address, salt: ByteArray, network: Network): String {
            require(salt.size == 32) { "salt must be exactly 32 bytes, got ${salt.size}" }

            val contractIdPreimage = ContractIDPreimageXdr.FromAddress(
                ContractIDPreimageFromAddressXdr(
                    address = deployer.toSCAddress(),
                    salt = Uint256Xdr(salt)
                )
            )
            val preimage = HashIDPreimageXdr.ContractID(
                HashIDPreimageContractIDXdr(
                    networkId = HashXdr(network.networkId()),
                    contractIdPreimage = contractIdPreimage
                )
            )
            val writer = XdrWriter()
            preimage.encode(writer)
            return StrKey.encodeContract(Util.hash(writer.toByteArray()))
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
                    fromMuxedAccount(
                        XdrJson.muxedPayload(scAddress.value.ed25519.value, scAddress.value.id.value)
                    )
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
                is SCAddressXdr.MuxedContract -> {
                    fromMuxedContract(
                        XdrJson.muxedPayload(scAddress.value.contractId.value.value, scAddress.value.id.value)
                    )
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
    }
}
