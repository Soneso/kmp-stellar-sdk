# frozen_string_literal: true

module Xdrgen
  module Generators
    # The types SEP-0051 renders in a Stellar-specific form rather than by the generic
    # struct, union and typedef rules: strkeys, asset codes, and the wide integers.
    #
    # Each entry replaces the default emission entirely, supplying the body of
    # toXdrJsonElement and of the internal fromXdrJsonTree that the generic entry points
    # delegate to. Bodies are written against the generated Kotlin shape of the type, so a
    # renamed arm or field surfaces as a compile error rather than as wrong output.
    #
    # AccountID and NodeID are absent deliberately. Both are typedefs of PublicKey, and the
    # generated value class delegates its JSON form to the type it wraps, so both already
    # render as the G strkey PublicKey renders as.
    module KotlinJsonOverrides
      STRKEY_IMPORT = 'com.soneso.stellar.sdk.StrKey'

      OVERRIDES = {
        'PublicKeyXdr' => {
          imports: [STRKEY_IMPORT],
          to: <<~KOTLIN,
            fun toXdrJsonElement(): JsonElement = when (this) {
              is Ed25519 -> XdrJson.name(StrKey.encodeEd25519PublicKey(value.value))
            }
          KOTLIN
          from: <<~KOTLIN
            internal fun fromXdrJsonTree(element: JsonElement): PublicKeyXdr =
              Ed25519(Uint256Xdr(XdrJson.strkey(XdrJson.name(element, XDR_JSON_TYPE), XDR_JSON_TYPE, "a G strkey") { StrKey.decodeEd25519PublicKey(it) }))
          KOTLIN
        },

        'ContractIDXdr' => {
          imports: [STRKEY_IMPORT],
          to: <<~KOTLIN,
            fun toXdrJsonElement(): JsonElement = XdrJson.name(StrKey.encodeContract(value.value))
          KOTLIN
          from: <<~KOTLIN
            internal fun fromXdrJsonTree(element: JsonElement): ContractIDXdr =
              ContractIDXdr(HashXdr(XdrJson.strkey(XdrJson.name(element, XDR_JSON_TYPE), XDR_JSON_TYPE, "a C strkey") { StrKey.decodeContract(it) }))
          KOTLIN
        },

        'PoolIDXdr' => {
          imports: [STRKEY_IMPORT],
          to: <<~KOTLIN,
            fun toXdrJsonElement(): JsonElement = XdrJson.name(StrKey.encodeLiquidityPool(value.value))
          KOTLIN
          from: <<~KOTLIN
            internal fun fromXdrJsonTree(element: JsonElement): PoolIDXdr =
              PoolIDXdr(HashXdr(XdrJson.strkey(XdrJson.name(element, XDR_JSON_TYPE), XDR_JSON_TYPE, "an L strkey") { StrKey.decodeLiquidityPool(it) }))
          KOTLIN
        },

        # The strkey lists the account key before the multiplexing id; the XDR structure
        # lists the id first. XdrJson.muxedPayload and XdrJson.muxedId hold that reordering.
        'MuxedAccountMed25519Xdr' => {
          imports: [STRKEY_IMPORT],
          to: <<~KOTLIN,
            fun toXdrJsonElement(): JsonElement = XdrJson.name(StrKey.encodeMed25519PublicKey(XdrJson.muxedPayload(ed25519.value, id.value)))
          KOTLIN
          from: <<~KOTLIN
            internal fun fromXdrJsonTree(element: JsonElement): MuxedAccountMed25519Xdr {
              val payload = XdrJson.strkey(XdrJson.name(element, XDR_JSON_TYPE), XDR_JSON_TYPE, "an M strkey") { StrKey.decodeMed25519PublicKey(it) }
              return MuxedAccountMed25519Xdr(Uint64Xdr(XdrJson.muxedId(payload)), Uint256Xdr(payload.copyOfRange(0, 32)))
            }
          KOTLIN
        },

        'MuxedEd25519AccountXdr' => {
          imports: [STRKEY_IMPORT],
          to: <<~KOTLIN,
            fun toXdrJsonElement(): JsonElement = XdrJson.name(StrKey.encodeMed25519PublicKey(XdrJson.muxedPayload(ed25519.value, id.value)))
          KOTLIN
          from: <<~KOTLIN
            internal fun fromXdrJsonTree(element: JsonElement): MuxedEd25519AccountXdr {
              val payload = XdrJson.strkey(XdrJson.name(element, XDR_JSON_TYPE), XDR_JSON_TYPE, "an M strkey") { StrKey.decodeMed25519PublicKey(it) }
              return MuxedEd25519AccountXdr(Uint64Xdr(XdrJson.muxedId(payload)), Uint256Xdr(payload.copyOfRange(0, 32)))
            }
          KOTLIN
        },

        'MuxedAccountXdr' => {
          imports: [STRKEY_IMPORT],
          to: <<~KOTLIN,
            fun toXdrJsonElement(): JsonElement = when (this) {
              is Ed25519 -> XdrJson.name(StrKey.encodeEd25519PublicKey(value.value))
              is Med25519 -> value.toXdrJsonElement()
            }
          KOTLIN
          from: <<~KOTLIN
            internal fun fromXdrJsonTree(element: JsonElement): MuxedAccountXdr {
              val text = XdrJson.name(element, XDR_JSON_TYPE)
              return when (text.firstOrNull()) {
                'G' -> Ed25519(Uint256Xdr(XdrJson.strkey(text, XDR_JSON_TYPE, "a G strkey") { StrKey.decodeEd25519PublicKey(it) }))
                'M' -> Med25519(MuxedAccountMed25519Xdr.fromXdrJsonTree(element))
                else -> XdrJson.fail(XDR_JSON_TYPE, "expects a G or M strkey, got ${XdrJson.preview(element)}")
              }
            }
          KOTLIN
        },

        'SCAddressXdr' => {
          imports: [],
          to: <<~KOTLIN,
            fun toXdrJsonElement(): JsonElement = when (this) {
              is AccountId -> value.toXdrJsonElement()
              is ContractId -> value.toXdrJsonElement()
              is MuxedAccount -> value.toXdrJsonElement()
              is ClaimableBalanceId -> value.toXdrJsonElement()
              is LiquidityPoolId -> value.toXdrJsonElement()
            }
          KOTLIN
          from: <<~KOTLIN
            internal fun fromXdrJsonTree(element: JsonElement): SCAddressXdr =
              when (XdrJson.name(element, XDR_JSON_TYPE).firstOrNull()) {
                'G' -> AccountId(AccountIDXdr.fromXdrJsonTree(element))
                'C' -> ContractId(ContractIDXdr.fromXdrJsonTree(element))
                'M' -> MuxedAccount(MuxedEd25519AccountXdr.fromXdrJsonTree(element))
                'B' -> ClaimableBalanceId(ClaimableBalanceIDXdr.fromXdrJsonTree(element))
                'L' -> LiquidityPoolId(PoolIDXdr.fromXdrJsonTree(element))
                else -> XdrJson.fail(XDR_JSON_TYPE, "expects a G, C, M, B or L strkey, got ${XdrJson.preview(element)}")
              }
          KOTLIN
        },

        'SignerKeyEd25519SignedPayloadXdr' => {
          imports: [STRKEY_IMPORT],
          to: <<~KOTLIN,
            /**
             * @throws IllegalArgumentException if [payload] is empty. SEP-0051 renders this type as
             *   a P strkey, and a strkey has no form for an empty payload, so such a signer has no
             *   XDR-JSON form even though it is valid XDR. Every other length XDR admits, 1 to 64
             *   bytes, renders.
             */
            fun toXdrJsonElement(): JsonElement = XdrJson.name(StrKey.encodeSignedPayload(XdrJson.signedPayloadKey(ed25519.value, payload, XDR_JSON_TYPE)))
          KOTLIN
          from: <<~KOTLIN
            internal fun fromXdrJsonTree(element: JsonElement): SignerKeyEd25519SignedPayloadXdr {
              val key = XdrJson.strkey(XdrJson.name(element, XDR_JSON_TYPE), XDR_JSON_TYPE, "a P strkey") { StrKey.decodeSignedPayload(it) }
              return SignerKeyEd25519SignedPayloadXdr(Uint256Xdr(key.copyOfRange(0, 32)), XdrJson.signedPayload(key, XDR_JSON_TYPE))
            }
          KOTLIN
        },

        'SignerKeyXdr' => {
          imports: [STRKEY_IMPORT],
          to: <<~KOTLIN,
            fun toXdrJsonElement(): JsonElement = when (this) {
              is Ed25519 -> XdrJson.name(StrKey.encodeEd25519PublicKey(value.value))
              is PreAuthTx -> XdrJson.name(StrKey.encodePreAuthTx(value.value))
              is HashX -> XdrJson.name(StrKey.encodeSha256Hash(value.value))
              is Ed25519SignedPayload -> value.toXdrJsonElement()
            }
          KOTLIN
          from: <<~KOTLIN
            internal fun fromXdrJsonTree(element: JsonElement): SignerKeyXdr {
              val text = XdrJson.name(element, XDR_JSON_TYPE)
              return when (text.firstOrNull()) {
                'G' -> Ed25519(Uint256Xdr(XdrJson.strkey(text, XDR_JSON_TYPE, "a G strkey") { StrKey.decodeEd25519PublicKey(it) }))
                'T' -> PreAuthTx(Uint256Xdr(XdrJson.strkey(text, XDR_JSON_TYPE, "a T strkey") { StrKey.decodePreAuthTx(it) }))
                'X' -> HashX(Uint256Xdr(XdrJson.strkey(text, XDR_JSON_TYPE, "an X strkey") { StrKey.decodeSha256Hash(it) }))
                'P' -> Ed25519SignedPayload(SignerKeyEd25519SignedPayloadXdr.fromXdrJsonTree(element))
                else -> XdrJson.fail(XDR_JSON_TYPE, "expects a G, T, X or P strkey, got ${XdrJson.preview(element)}")
              }
            }
          KOTLIN
        },

        # The B strkey carries a leading type byte ahead of the 32-byte hash.
        'ClaimableBalanceIDXdr' => {
          imports: [STRKEY_IMPORT],
          to: <<~KOTLIN,
            fun toXdrJsonElement(): JsonElement = when (this) {
              is V0 -> XdrJson.name(StrKey.encodeClaimableBalance(byteArrayOf(discriminant.value.toByte()) + value.value))
            }
          KOTLIN
          from: <<~KOTLIN
            internal fun fromXdrJsonTree(element: JsonElement): ClaimableBalanceIDXdr {
              val payload = XdrJson.strkey(XdrJson.name(element, XDR_JSON_TYPE), XDR_JSON_TYPE, "a B strkey") { StrKey.decodeClaimableBalance(it) }
              return when (payload[0].toInt()) {
                ClaimableBalanceIDTypeXdr.CLAIMABLE_BALANCE_ID_TYPE_V0.value -> V0(HashXdr(payload.copyOfRange(1, payload.size)))
                else -> XdrJson.fail(XDR_JSON_TYPE, "has no type numbered ${payload[0].toInt()}")
              }
            }
          KOTLIN
        },

        # A four-character code drops all its NUL padding; a twelve-character code keeps
        # five bytes, which is what tells the two forms apart once trimmed.
        'AssetCode4Xdr' => {
          imports: [],
          to: <<~KOTLIN,
            fun toXdrJsonElement(): JsonElement = XdrJson.escapedString(XdrJson.trimAssetCode(value, 0))
          KOTLIN
          from: <<~KOTLIN
            internal fun fromXdrJsonTree(element: JsonElement): AssetCode4Xdr =
              AssetCode4Xdr(XdrJson.padAssetCode(XdrJson.unescapeStringBytes(element, XDR_JSON_TYPE, "value"), XDR_JSON_TYPE, 4))
          KOTLIN
        },

        'AssetCode12Xdr' => {
          imports: [],
          to: <<~KOTLIN,
            fun toXdrJsonElement(): JsonElement = XdrJson.escapedString(XdrJson.trimAssetCode(value, 5))
          KOTLIN
          from: <<~KOTLIN
            internal fun fromXdrJsonTree(element: JsonElement): AssetCode12Xdr =
              AssetCode12Xdr(XdrJson.padAssetCode(XdrJson.unescapeStringBytes(element, XDR_JSON_TYPE, "value"), XDR_JSON_TYPE, 12))
          KOTLIN
        },

        'AssetCodeXdr' => {
          imports: [],
          to: <<~KOTLIN,
            fun toXdrJsonElement(): JsonElement = when (this) {
              is AssetCode4 -> value.toXdrJsonElement()
              is AssetCode12 -> value.toXdrJsonElement()
            }
          KOTLIN
          from: <<~KOTLIN
            internal fun fromXdrJsonTree(element: JsonElement): AssetCodeXdr {
              val code = XdrJson.unescapeStringBytes(element, XDR_JSON_TYPE, "value")
              return if (code.size <= 4) {
                AssetCode4(AssetCode4Xdr(XdrJson.padAssetCode(code, XDR_JSON_TYPE, 4)))
              } else {
                AssetCode12(AssetCode12Xdr(XdrJson.padAssetCode(code, XDR_JSON_TYPE, 12)))
              }
            }
          KOTLIN
        },

        'Int128PartsXdr' => {
          imports: [],
          to: <<~KOTLIN,
            fun toXdrJsonElement(): JsonElement = XdrJson.name(XdrJson.int128ToDecimalString(hi.value, lo.value))
          KOTLIN
          from: <<~KOTLIN
            internal fun fromXdrJsonTree(element: JsonElement): Int128PartsXdr {
              val limbs = XdrJson.decimalStringToInt128(element, XDR_JSON_TYPE, "value")
              return Int128PartsXdr(Int64Xdr(limbs.hi), Uint64Xdr(limbs.lo))
            }
          KOTLIN
        },

        'UInt128PartsXdr' => {
          imports: [],
          to: <<~KOTLIN,
            fun toXdrJsonElement(): JsonElement = XdrJson.name(XdrJson.uint128ToDecimalString(hi.value, lo.value))
          KOTLIN
          from: <<~KOTLIN
            internal fun fromXdrJsonTree(element: JsonElement): UInt128PartsXdr {
              val limbs = XdrJson.decimalStringToUInt128(element, XDR_JSON_TYPE, "value")
              return UInt128PartsXdr(Uint64Xdr(limbs.hi), Uint64Xdr(limbs.lo))
            }
          KOTLIN
        },

        'Int256PartsXdr' => {
          imports: [],
          to: <<~KOTLIN,
            fun toXdrJsonElement(): JsonElement = XdrJson.name(XdrJson.int256ToDecimalString(hiHi.value, hiLo.value, loHi.value, loLo.value))
          KOTLIN
          from: <<~KOTLIN
            internal fun fromXdrJsonTree(element: JsonElement): Int256PartsXdr {
              val limbs = XdrJson.decimalStringToInt256(element, XDR_JSON_TYPE, "value")
              return Int256PartsXdr(Int64Xdr(limbs.hiHi), Uint64Xdr(limbs.hiLo), Uint64Xdr(limbs.loHi), Uint64Xdr(limbs.loLo))
            }
          KOTLIN
        },

        'UInt256PartsXdr' => {
          imports: [],
          to: <<~KOTLIN,
            fun toXdrJsonElement(): JsonElement = XdrJson.name(XdrJson.uint256ToDecimalString(hiHi.value, hiLo.value, loHi.value, loLo.value))
          KOTLIN
          from: <<~KOTLIN
            internal fun fromXdrJsonTree(element: JsonElement): UInt256PartsXdr {
              val limbs = XdrJson.decimalStringToUInt256(element, XDR_JSON_TYPE, "value")
              return UInt256PartsXdr(Uint64Xdr(limbs.hiHi), Uint64Xdr(limbs.hiLo), Uint64Xdr(limbs.loHi), Uint64Xdr(limbs.loLo))
            }
          KOTLIN
        }
      }.freeze

      module_function

      def for_type(type_name) = OVERRIDES[type_name]

      def type_names = OVERRIDES.keys
    end
  end
end
