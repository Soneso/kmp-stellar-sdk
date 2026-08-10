package com.soneso.stellar.sdk.unitTests.sep.sep51

import com.soneso.stellar.sdk.unitTests.xdr.json.SEP51_CORPUS
import com.soneso.stellar.sdk.unitTests.xdr.json.SEP51_UNRESOLVABLE_ENUM_MEMBERS
import com.soneso.stellar.sdk.unitTests.xdr.json.SEP51_UNRESOLVABLE_STRUCT_TYPES
import com.soneso.stellar.sdk.unitTests.xdr.json.Sep51CorpusEntry
import com.soneso.stellar.sdk.xdr.*
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Holds the SDK to the XDR-JSON (SEP-0051) rendering recorded for every corpus entry, in both
 * directions: the recorded JSON must encode to the recorded XDR, and that XDR must render back
 * as exactly the recorded JSON text, key order included.
 *
 * The corpus is embedded as Kotlin source rather than read from disk because `commonTest` has
 * no filesystem API on the JavaScript and Apple targets. It pins the renderings that are not
 * mechanical: strkey-valued union arms, integers that become strings, the escape ladder over
 * bytes-typed and string-typed fields, empty and populated variable-length data, optionals in
 * both states, and the name rules a change of XDR definitions could disturb.
 */
class Sep51CorpusTest {

    /**
     * The four operations one corpus entry needs. Binding them per type erases the type, so a
     * single loop can drive every entry.
     *
     * Base64 is produced from the type's own binary encoder rather than a shared helper: the
     * corpus spans types for which no base64 convenience function is declared.
     */
    private class Codec<T>(
        private val fromJson: (String) -> T,
        private val toJson: (T) -> String,
        private val decode: (XdrReader) -> T,
        private val encode: (T, XdrWriter) -> Unit
    ) {
        @OptIn(ExperimentalEncodingApi::class)
        fun jsonToXdr(json: String): String {
            val writer = XdrWriter()
            encode(fromJson(json), writer)
            return Base64.encode(writer.toByteArray())
        }

        @OptIn(ExperimentalEncodingApi::class)
        fun xdrToJson(xdr: String): String = toJson(decode(XdrReader(Base64.decode(xdr))))
    }

    /** Identifies an entry in a failure message by its type and the start of its base64. */
    private fun describe(entry: Sep51CorpusEntry): String =
        "${entry.type} [${entry.xdr.take(24)}]"

    private fun entryFor(kmpType: String): Sep51CorpusEntry =
        SEP51_CORPUS.firstOrNull { it.kmpType == kmpType }
            ?: fail("the corpus holds no entry for $kmpType; add a seed for it")

    private fun codecFor(entry: Sep51CorpusEntry): Codec<*> = when (entry.kmpType) {
        "AccountEntryExtXdr" -> Codec(
            AccountEntryExtXdr.Companion::fromXdrJson,
            AccountEntryExtXdr::toXdrJson,
            AccountEntryExtXdr.Companion::decode,
            AccountEntryExtXdr::encode
        )
        "AccountEntryExtensionV1ExtXdr" -> Codec(
            AccountEntryExtensionV1ExtXdr.Companion::fromXdrJson,
            AccountEntryExtensionV1ExtXdr::toXdrJson,
            AccountEntryExtensionV1ExtXdr.Companion::decode,
            AccountEntryExtensionV1ExtXdr::encode
        )
        "AccountIDXdr" -> Codec(
            AccountIDXdr.Companion::fromXdrJson,
            AccountIDXdr::toXdrJson,
            AccountIDXdr.Companion::decode,
            AccountIDXdr::encode
        )
        "AssetCode12Xdr" -> Codec(
            AssetCode12Xdr.Companion::fromXdrJson,
            AssetCode12Xdr::toXdrJson,
            AssetCode12Xdr.Companion::decode,
            AssetCode12Xdr::encode
        )
        "AssetCode4Xdr" -> Codec(
            AssetCode4Xdr.Companion::fromXdrJson,
            AssetCode4Xdr::toXdrJson,
            AssetCode4Xdr.Companion::decode,
            AssetCode4Xdr::encode
        )
        "AssetCodeXdr" -> Codec(
            AssetCodeXdr.Companion::fromXdrJson,
            AssetCodeXdr::toXdrJson,
            AssetCodeXdr.Companion::decode,
            AssetCodeXdr::encode
        )
        "AssetXdr" -> Codec(
            AssetXdr.Companion::fromXdrJson,
            AssetXdr::toXdrJson,
            AssetXdr.Companion::decode,
            AssetXdr::encode
        )
        "BinaryFuseFilterTypeXdr" -> Codec(
            BinaryFuseFilterTypeXdr.Companion::fromXdrJson,
            BinaryFuseFilterTypeXdr::toXdrJson,
            BinaryFuseFilterTypeXdr.Companion::decode,
            BinaryFuseFilterTypeXdr::encode
        )
        "BucketEntryXdr" -> Codec(
            BucketEntryXdr.Companion::fromXdrJson,
            BucketEntryXdr::toXdrJson,
            BucketEntryXdr.Companion::decode,
            BucketEntryXdr::encode
        )
        "ChangeTrustAssetXdr" -> Codec(
            ChangeTrustAssetXdr.Companion::fromXdrJson,
            ChangeTrustAssetXdr::toXdrJson,
            ChangeTrustAssetXdr.Companion::decode,
            ChangeTrustAssetXdr::encode
        )
        "ClaimAtomXdr" -> Codec(
            ClaimAtomXdr.Companion::fromXdrJson,
            ClaimAtomXdr::toXdrJson,
            ClaimAtomXdr.Companion::decode,
            ClaimAtomXdr::encode
        )
        "ClaimPredicateXdr" -> Codec(
            ClaimPredicateXdr.Companion::fromXdrJson,
            ClaimPredicateXdr::toXdrJson,
            ClaimPredicateXdr.Companion::decode,
            ClaimPredicateXdr::encode
        )
        "ClaimableBalanceIDXdr" -> Codec(
            ClaimableBalanceIDXdr.Companion::fromXdrJson,
            ClaimableBalanceIDXdr::toXdrJson,
            ClaimableBalanceIDXdr.Companion::decode,
            ClaimableBalanceIDXdr::encode
        )
        "ClaimantXdr" -> Codec(
            ClaimantXdr.Companion::fromXdrJson,
            ClaimantXdr::toXdrJson,
            ClaimantXdr.Companion::decode,
            ClaimantXdr::encode
        )
        "ConfigSettingEntryXdr" -> Codec(
            ConfigSettingEntryXdr.Companion::fromXdrJson,
            ConfigSettingEntryXdr::toXdrJson,
            ConfigSettingEntryXdr.Companion::decode,
            ConfigSettingEntryXdr::encode
        )
        "ContractCostParamsXdr" -> Codec(
            ContractCostParamsXdr.Companion::fromXdrJson,
            ContractCostParamsXdr::toXdrJson,
            ContractCostParamsXdr.Companion::decode,
            ContractCostParamsXdr::encode
        )
        "ContractCostTypeXdr" -> Codec(
            ContractCostTypeXdr.Companion::fromXdrJson,
            ContractCostTypeXdr::toXdrJson,
            ContractCostTypeXdr.Companion::decode,
            ContractCostTypeXdr::encode
        )
        "ContractDataEntryXdr" -> Codec(
            ContractDataEntryXdr.Companion::fromXdrJson,
            ContractDataEntryXdr::toXdrJson,
            ContractDataEntryXdr.Companion::decode,
            ContractDataEntryXdr::encode
        )
        "ContractEventXdr" -> Codec(
            ContractEventXdr.Companion::fromXdrJson,
            ContractEventXdr::toXdrJson,
            ContractEventXdr.Companion::decode,
            ContractEventXdr::encode
        )
        "ContractExecutableXdr" -> Codec(
            ContractExecutableXdr.Companion::fromXdrJson,
            ContractExecutableXdr::toXdrJson,
            ContractExecutableXdr.Companion::decode,
            ContractExecutableXdr::encode
        )
        "ContractIDPreimageXdr" -> Codec(
            ContractIDPreimageXdr.Companion::fromXdrJson,
            ContractIDPreimageXdr::toXdrJson,
            ContractIDPreimageXdr.Companion::decode,
            ContractIDPreimageXdr::encode
        )
        "Curve25519PublicXdr" -> Codec(
            Curve25519PublicXdr.Companion::fromXdrJson,
            Curve25519PublicXdr::toXdrJson,
            Curve25519PublicXdr.Companion::decode,
            Curve25519PublicXdr::encode
        )
        "Curve25519SecretXdr" -> Codec(
            Curve25519SecretXdr.Companion::fromXdrJson,
            Curve25519SecretXdr::toXdrJson,
            Curve25519SecretXdr.Companion::decode,
            Curve25519SecretXdr::encode
        )
        "DataValueXdr" -> Codec(
            DataValueXdr.Companion::fromXdrJson,
            DataValueXdr::toXdrJson,
            DataValueXdr.Companion::decode,
            DataValueXdr::encode
        )
        "DecoratedSignatureXdr" -> Codec(
            DecoratedSignatureXdr.Companion::fromXdrJson,
            DecoratedSignatureXdr::toXdrJson,
            DecoratedSignatureXdr.Companion::decode,
            DecoratedSignatureXdr::encode
        )
        "DiagnosticEventXdr" -> Codec(
            DiagnosticEventXdr.Companion::fromXdrJson,
            DiagnosticEventXdr::toXdrJson,
            DiagnosticEventXdr.Companion::decode,
            DiagnosticEventXdr::encode
        )
        "DurationXdr" -> Codec(
            DurationXdr.Companion::fromXdrJson,
            DurationXdr::toXdrJson,
            DurationXdr.Companion::decode,
            DurationXdr::encode
        )
        "EnvelopeTypeXdr" -> Codec(
            EnvelopeTypeXdr.Companion::fromXdrJson,
            EnvelopeTypeXdr::toXdrJson,
            EnvelopeTypeXdr.Companion::decode,
            EnvelopeTypeXdr::encode
        )
        "ExtensionPointXdr" -> Codec(
            ExtensionPointXdr.Companion::fromXdrJson,
            ExtensionPointXdr::toXdrJson,
            ExtensionPointXdr.Companion::decode,
            ExtensionPointXdr::encode
        )
        "HashXdr" -> Codec(
            HashXdr.Companion::fromXdrJson,
            HashXdr::toXdrJson,
            HashXdr.Companion::decode,
            HashXdr::encode
        )
        "HmacSha256KeyXdr" -> Codec(
            HmacSha256KeyXdr.Companion::fromXdrJson,
            HmacSha256KeyXdr::toXdrJson,
            HmacSha256KeyXdr.Companion::decode,
            HmacSha256KeyXdr::encode
        )
        "HmacSha256MacXdr" -> Codec(
            HmacSha256MacXdr.Companion::fromXdrJson,
            HmacSha256MacXdr::toXdrJson,
            HmacSha256MacXdr.Companion::decode,
            HmacSha256MacXdr::encode
        )
        "HostFunctionXdr" -> Codec(
            HostFunctionXdr.Companion::fromXdrJson,
            HostFunctionXdr::toXdrJson,
            HostFunctionXdr.Companion::decode,
            HostFunctionXdr::encode
        )
        "HotArchiveBucketEntryXdr" -> Codec(
            HotArchiveBucketEntryXdr.Companion::fromXdrJson,
            HotArchiveBucketEntryXdr::toXdrJson,
            HotArchiveBucketEntryXdr.Companion::decode,
            HotArchiveBucketEntryXdr::encode
        )
        "Int128PartsXdr" -> Codec(
            Int128PartsXdr.Companion::fromXdrJson,
            Int128PartsXdr::toXdrJson,
            Int128PartsXdr.Companion::decode,
            Int128PartsXdr::encode
        )
        "Int256PartsXdr" -> Codec(
            Int256PartsXdr.Companion::fromXdrJson,
            Int256PartsXdr::toXdrJson,
            Int256PartsXdr.Companion::decode,
            Int256PartsXdr::encode
        )
        "Int32Xdr" -> Codec(
            Int32Xdr.Companion::fromXdrJson,
            Int32Xdr::toXdrJson,
            Int32Xdr.Companion::decode,
            Int32Xdr::encode
        )
        "Int64Xdr" -> Codec(
            Int64Xdr.Companion::fromXdrJson,
            Int64Xdr::toXdrJson,
            Int64Xdr.Companion::decode,
            Int64Xdr::encode
        )
        "InvokeContractArgsXdr" -> Codec(
            InvokeContractArgsXdr.Companion::fromXdrJson,
            InvokeContractArgsXdr::toXdrJson,
            InvokeContractArgsXdr.Companion::decode,
            InvokeContractArgsXdr::encode
        )
        "LedgerBoundsXdr" -> Codec(
            LedgerBoundsXdr.Companion::fromXdrJson,
            LedgerBoundsXdr::toXdrJson,
            LedgerBoundsXdr.Companion::decode,
            LedgerBoundsXdr::encode
        )
        "LedgerCloseMetaExtXdr" -> Codec(
            LedgerCloseMetaExtXdr.Companion::fromXdrJson,
            LedgerCloseMetaExtXdr::toXdrJson,
            LedgerCloseMetaExtXdr.Companion::decode,
            LedgerCloseMetaExtXdr::encode
        )
        "LedgerEntryChangeXdr" -> Codec(
            LedgerEntryChangeXdr.Companion::fromXdrJson,
            LedgerEntryChangeXdr::toXdrJson,
            LedgerEntryChangeXdr.Companion::decode,
            LedgerEntryChangeXdr::encode
        )
        "LedgerEntryDataXdr" -> Codec(
            LedgerEntryDataXdr.Companion::fromXdrJson,
            LedgerEntryDataXdr::toXdrJson,
            LedgerEntryDataXdr.Companion::decode,
            LedgerEntryDataXdr::encode
        )
        "LedgerEntryExtXdr" -> Codec(
            LedgerEntryExtXdr.Companion::fromXdrJson,
            LedgerEntryExtXdr::toXdrJson,
            LedgerEntryExtXdr.Companion::decode,
            LedgerEntryExtXdr::encode
        )
        "LedgerEntryXdr" -> Codec(
            LedgerEntryXdr.Companion::fromXdrJson,
            LedgerEntryXdr::toXdrJson,
            LedgerEntryXdr.Companion::decode,
            LedgerEntryXdr::encode
        )
        "LedgerFootprintXdr" -> Codec(
            LedgerFootprintXdr.Companion::fromXdrJson,
            LedgerFootprintXdr::toXdrJson,
            LedgerFootprintXdr.Companion::decode,
            LedgerFootprintXdr::encode
        )
        "LedgerHeaderXdr" -> Codec(
            LedgerHeaderXdr.Companion::fromXdrJson,
            LedgerHeaderXdr::toXdrJson,
            LedgerHeaderXdr.Companion::decode,
            LedgerHeaderXdr::encode
        )
        "LedgerKeyXdr" -> Codec(
            LedgerKeyXdr.Companion::fromXdrJson,
            LedgerKeyXdr::toXdrJson,
            LedgerKeyXdr.Companion::decode,
            LedgerKeyXdr::encode
        )
        "LiabilitiesXdr" -> Codec(
            LiabilitiesXdr.Companion::fromXdrJson,
            LiabilitiesXdr::toXdrJson,
            LiabilitiesXdr.Companion::decode,
            LiabilitiesXdr::encode
        )
        "LiquidityPoolParametersXdr" -> Codec(
            LiquidityPoolParametersXdr.Companion::fromXdrJson,
            LiquidityPoolParametersXdr::toXdrJson,
            LiquidityPoolParametersXdr.Companion::decode,
            LiquidityPoolParametersXdr::encode
        )
        "ManageDataOpXdr" -> Codec(
            ManageDataOpXdr.Companion::fromXdrJson,
            ManageDataOpXdr::toXdrJson,
            ManageDataOpXdr.Companion::decode,
            ManageDataOpXdr::encode
        )
        "MemoXdr" -> Codec(
            MemoXdr.Companion::fromXdrJson,
            MemoXdr::toXdrJson,
            MemoXdr.Companion::decode,
            MemoXdr::encode
        )
        "MuxedAccountXdr" -> Codec(
            MuxedAccountXdr.Companion::fromXdrJson,
            MuxedAccountXdr::toXdrJson,
            MuxedAccountXdr.Companion::decode,
            MuxedAccountXdr::encode
        )
        "NodeIDXdr" -> Codec(
            NodeIDXdr.Companion::fromXdrJson,
            NodeIDXdr::toXdrJson,
            NodeIDXdr.Companion::decode,
            NodeIDXdr::encode
        )
        "OperationBodyXdr" -> Codec(
            OperationBodyXdr.Companion::fromXdrJson,
            OperationBodyXdr::toXdrJson,
            OperationBodyXdr.Companion::decode,
            OperationBodyXdr::encode
        )
        "OperationResultXdr" -> Codec(
            OperationResultXdr.Companion::fromXdrJson,
            OperationResultXdr::toXdrJson,
            OperationResultXdr.Companion::decode,
            OperationResultXdr::encode
        )
        "OperationXdr" -> Codec(
            OperationXdr.Companion::fromXdrJson,
            OperationXdr::toXdrJson,
            OperationXdr.Companion::decode,
            OperationXdr::encode
        )
        "PoolIDXdr" -> Codec(
            PoolIDXdr.Companion::fromXdrJson,
            PoolIDXdr::toXdrJson,
            PoolIDXdr.Companion::decode,
            PoolIDXdr::encode
        )
        "PreconditionsV2Xdr" -> Codec(
            PreconditionsV2Xdr.Companion::fromXdrJson,
            PreconditionsV2Xdr::toXdrJson,
            PreconditionsV2Xdr.Companion::decode,
            PreconditionsV2Xdr::encode
        )
        "PreconditionsXdr" -> Codec(
            PreconditionsXdr.Companion::fromXdrJson,
            PreconditionsXdr::toXdrJson,
            PreconditionsXdr.Companion::decode,
            PreconditionsXdr::encode
        )
        "PriceXdr" -> Codec(
            PriceXdr.Companion::fromXdrJson,
            PriceXdr::toXdrJson,
            PriceXdr.Companion::decode,
            PriceXdr::encode
        )
        "PublicKeyXdr" -> Codec(
            PublicKeyXdr.Companion::fromXdrJson,
            PublicKeyXdr::toXdrJson,
            PublicKeyXdr.Companion::decode,
            PublicKeyXdr::encode
        )
        "RevokeSponsorshipOpXdr" -> Codec(
            RevokeSponsorshipOpXdr.Companion::fromXdrJson,
            RevokeSponsorshipOpXdr::toXdrJson,
            RevokeSponsorshipOpXdr.Companion::decode,
            RevokeSponsorshipOpXdr::encode
        )
        "SCAddressXdr" -> Codec(
            SCAddressXdr.Companion::fromXdrJson,
            SCAddressXdr::toXdrJson,
            SCAddressXdr.Companion::decode,
            SCAddressXdr::encode
        )
        "SCBytesXdr" -> Codec(
            SCBytesXdr.Companion::fromXdrJson,
            SCBytesXdr::toXdrJson,
            SCBytesXdr.Companion::decode,
            SCBytesXdr::encode
        )
        "SCContractInstanceXdr" -> Codec(
            SCContractInstanceXdr.Companion::fromXdrJson,
            SCContractInstanceXdr::toXdrJson,
            SCContractInstanceXdr.Companion::decode,
            SCContractInstanceXdr::encode
        )
        "SCEnvMetaEntryXdr" -> Codec(
            SCEnvMetaEntryXdr.Companion::fromXdrJson,
            SCEnvMetaEntryXdr::toXdrJson,
            SCEnvMetaEntryXdr.Companion::decode,
            SCEnvMetaEntryXdr::encode
        )
        "SCErrorXdr" -> Codec(
            SCErrorXdr.Companion::fromXdrJson,
            SCErrorXdr::toXdrJson,
            SCErrorXdr.Companion::decode,
            SCErrorXdr::encode
        )
        "SCMapEntryXdr" -> Codec(
            SCMapEntryXdr.Companion::fromXdrJson,
            SCMapEntryXdr::toXdrJson,
            SCMapEntryXdr.Companion::decode,
            SCMapEntryXdr::encode
        )
        "SCMapXdr" -> Codec(
            SCMapXdr.Companion::fromXdrJson,
            SCMapXdr::toXdrJson,
            SCMapXdr.Companion::decode,
            SCMapXdr::encode
        )
        "SCMetaEntryXdr" -> Codec(
            SCMetaEntryXdr.Companion::fromXdrJson,
            SCMetaEntryXdr::toXdrJson,
            SCMetaEntryXdr.Companion::decode,
            SCMetaEntryXdr::encode
        )
        "SCNonceKeyXdr" -> Codec(
            SCNonceKeyXdr.Companion::fromXdrJson,
            SCNonceKeyXdr::toXdrJson,
            SCNonceKeyXdr.Companion::decode,
            SCNonceKeyXdr::encode
        )
        "SCSpecEntryXdr" -> Codec(
            SCSpecEntryXdr.Companion::fromXdrJson,
            SCSpecEntryXdr::toXdrJson,
            SCSpecEntryXdr.Companion::decode,
            SCSpecEntryXdr::encode
        )
        "SCSpecEventParamV0Xdr" -> Codec(
            SCSpecEventParamV0Xdr.Companion::fromXdrJson,
            SCSpecEventParamV0Xdr::toXdrJson,
            SCSpecEventParamV0Xdr.Companion::decode,
            SCSpecEventParamV0Xdr::encode
        )
        "SCSpecFunctionInputV0Xdr" -> Codec(
            SCSpecFunctionInputV0Xdr.Companion::fromXdrJson,
            SCSpecFunctionInputV0Xdr::toXdrJson,
            SCSpecFunctionInputV0Xdr.Companion::decode,
            SCSpecFunctionInputV0Xdr::encode
        )
        "SCSpecFunctionV0Xdr" -> Codec(
            SCSpecFunctionV0Xdr.Companion::fromXdrJson,
            SCSpecFunctionV0Xdr::toXdrJson,
            SCSpecFunctionV0Xdr.Companion::decode,
            SCSpecFunctionV0Xdr::encode
        )
        "SCSpecTypeDefXdr" -> Codec(
            SCSpecTypeDefXdr.Companion::fromXdrJson,
            SCSpecTypeDefXdr::toXdrJson,
            SCSpecTypeDefXdr.Companion::decode,
            SCSpecTypeDefXdr::encode
        )
        "SCSpecUDTStructFieldV0Xdr" -> Codec(
            SCSpecUDTStructFieldV0Xdr.Companion::fromXdrJson,
            SCSpecUDTStructFieldV0Xdr::toXdrJson,
            SCSpecUDTStructFieldV0Xdr.Companion::decode,
            SCSpecUDTStructFieldV0Xdr::encode
        )
        "SCSpecUDTUnionCaseTupleV0Xdr" -> Codec(
            SCSpecUDTUnionCaseTupleV0Xdr.Companion::fromXdrJson,
            SCSpecUDTUnionCaseTupleV0Xdr::toXdrJson,
            SCSpecUDTUnionCaseTupleV0Xdr.Companion::decode,
            SCSpecUDTUnionCaseTupleV0Xdr::encode
        )
        "SCSpecUDTUnionCaseV0Xdr" -> Codec(
            SCSpecUDTUnionCaseV0Xdr.Companion::fromXdrJson,
            SCSpecUDTUnionCaseV0Xdr::toXdrJson,
            SCSpecUDTUnionCaseV0Xdr.Companion::decode,
            SCSpecUDTUnionCaseV0Xdr::encode
        )
        "SCStringXdr" -> Codec(
            SCStringXdr.Companion::fromXdrJson,
            SCStringXdr::toXdrJson,
            SCStringXdr.Companion::decode,
            SCStringXdr::encode
        )
        "SCSymbolXdr" -> Codec(
            SCSymbolXdr.Companion::fromXdrJson,
            SCSymbolXdr::toXdrJson,
            SCSymbolXdr.Companion::decode,
            SCSymbolXdr::encode
        )
        "SCValXdr" -> Codec(
            SCValXdr.Companion::fromXdrJson,
            SCValXdr::toXdrJson,
            SCValXdr.Companion::decode,
            SCValXdr::encode
        )
        "SCVecXdr" -> Codec(
            SCVecXdr.Companion::fromXdrJson,
            SCVecXdr::toXdrJson,
            SCVecXdr.Companion::decode,
            SCVecXdr::encode
        )
        "SequenceNumberXdr" -> Codec(
            SequenceNumberXdr.Companion::fromXdrJson,
            SequenceNumberXdr::toXdrJson,
            SequenceNumberXdr.Companion::decode,
            SequenceNumberXdr::encode
        )
        "SerializedBinaryFuseFilterXdr" -> Codec(
            SerializedBinaryFuseFilterXdr.Companion::fromXdrJson,
            SerializedBinaryFuseFilterXdr::toXdrJson,
            SerializedBinaryFuseFilterXdr.Companion::decode,
            SerializedBinaryFuseFilterXdr::encode
        )
        "SetOptionsOpXdr" -> Codec(
            SetOptionsOpXdr.Companion::fromXdrJson,
            SetOptionsOpXdr::toXdrJson,
            SetOptionsOpXdr.Companion::decode,
            SetOptionsOpXdr::encode
        )
        "ShortHashSeedXdr" -> Codec(
            ShortHashSeedXdr.Companion::fromXdrJson,
            ShortHashSeedXdr::toXdrJson,
            ShortHashSeedXdr.Companion::decode,
            ShortHashSeedXdr::encode
        )
        "SignerKeyXdr" -> Codec(
            SignerKeyXdr.Companion::fromXdrJson,
            SignerKeyXdr::toXdrJson,
            SignerKeyXdr.Companion::decode,
            SignerKeyXdr::encode
        )
        "SorobanAuthorizationEntryXdr" -> Codec(
            SorobanAuthorizationEntryXdr.Companion::fromXdrJson,
            SorobanAuthorizationEntryXdr::toXdrJson,
            SorobanAuthorizationEntryXdr.Companion::decode,
            SorobanAuthorizationEntryXdr::encode
        )
        "SorobanAuthorizedInvocationXdr" -> Codec(
            SorobanAuthorizedInvocationXdr.Companion::fromXdrJson,
            SorobanAuthorizedInvocationXdr::toXdrJson,
            SorobanAuthorizedInvocationXdr.Companion::decode,
            SorobanAuthorizedInvocationXdr::encode
        )
        "SorobanCredentialsXdr" -> Codec(
            SorobanCredentialsXdr.Companion::fromXdrJson,
            SorobanCredentialsXdr::toXdrJson,
            SorobanCredentialsXdr.Companion::decode,
            SorobanCredentialsXdr::encode
        )
        "SorobanTransactionMetaExtXdr" -> Codec(
            SorobanTransactionMetaExtXdr.Companion::fromXdrJson,
            SorobanTransactionMetaExtXdr::toXdrJson,
            SorobanTransactionMetaExtXdr.Companion::decode,
            SorobanTransactionMetaExtXdr::encode
        )
        "TTLEntryXdr" -> Codec(
            TTLEntryXdr.Companion::fromXdrJson,
            TTLEntryXdr::toXdrJson,
            TTLEntryXdr.Companion::decode,
            TTLEntryXdr::encode
        )
        "ThresholdIndexesXdr" -> Codec(
            ThresholdIndexesXdr.Companion::fromXdrJson,
            ThresholdIndexesXdr::toXdrJson,
            ThresholdIndexesXdr.Companion::decode,
            ThresholdIndexesXdr::encode
        )
        "TimeBoundsXdr" -> Codec(
            TimeBoundsXdr.Companion::fromXdrJson,
            TimeBoundsXdr::toXdrJson,
            TimeBoundsXdr.Companion::decode,
            TimeBoundsXdr::encode
        )
        "TimePointXdr" -> Codec(
            TimePointXdr.Companion::fromXdrJson,
            TimePointXdr::toXdrJson,
            TimePointXdr.Companion::decode,
            TimePointXdr::encode
        )
        "TransactionEnvelopeXdr" -> Codec(
            TransactionEnvelopeXdr.Companion::fromXdrJson,
            TransactionEnvelopeXdr::toXdrJson,
            TransactionEnvelopeXdr.Companion::decode,
            TransactionEnvelopeXdr::encode
        )
        "TransactionMetaXdr" -> Codec(
            TransactionMetaXdr.Companion::fromXdrJson,
            TransactionMetaXdr::toXdrJson,
            TransactionMetaXdr.Companion::decode,
            TransactionMetaXdr::encode
        )
        "TransactionPhaseXdr" -> Codec(
            TransactionPhaseXdr.Companion::fromXdrJson,
            TransactionPhaseXdr::toXdrJson,
            TransactionPhaseXdr.Companion::decode,
            TransactionPhaseXdr::encode
        )
        "TransactionResultXdr" -> Codec(
            TransactionResultXdr.Companion::fromXdrJson,
            TransactionResultXdr::toXdrJson,
            TransactionResultXdr.Companion::decode,
            TransactionResultXdr::encode
        )
        "TrustLineAssetXdr" -> Codec(
            TrustLineAssetXdr.Companion::fromXdrJson,
            TrustLineAssetXdr::toXdrJson,
            TrustLineAssetXdr.Companion::decode,
            TrustLineAssetXdr::encode
        )
        "TrustLineEntryExtXdr" -> Codec(
            TrustLineEntryExtXdr.Companion::fromXdrJson,
            TrustLineEntryExtXdr::toXdrJson,
            TrustLineEntryExtXdr.Companion::decode,
            TrustLineEntryExtXdr::encode
        )
        "UInt128PartsXdr" -> Codec(
            UInt128PartsXdr.Companion::fromXdrJson,
            UInt128PartsXdr::toXdrJson,
            UInt128PartsXdr.Companion::decode,
            UInt128PartsXdr::encode
        )
        "UInt256PartsXdr" -> Codec(
            UInt256PartsXdr.Companion::fromXdrJson,
            UInt256PartsXdr::toXdrJson,
            UInt256PartsXdr.Companion::decode,
            UInt256PartsXdr::encode
        )
        "Uint256Xdr" -> Codec(
            Uint256Xdr.Companion::fromXdrJson,
            Uint256Xdr::toXdrJson,
            Uint256Xdr.Companion::decode,
            Uint256Xdr::encode
        )
        "Uint32Xdr" -> Codec(
            Uint32Xdr.Companion::fromXdrJson,
            Uint32Xdr::toXdrJson,
            Uint32Xdr.Companion::decode,
            Uint32Xdr::encode
        )
        "Uint64Xdr" -> Codec(
            Uint64Xdr.Companion::fromXdrJson,
            Uint64Xdr::toXdrJson,
            Uint64Xdr.Companion::decode,
            Uint64Xdr::encode
        )
        else -> throw IllegalStateException(
            "the corpus names an SDK type this test does not dispatch: ${entry.kmpType} " +
                "(entry ${describe(entry)})"
        )
    }

    @Test
    fun corpusJsonEncodesToTheRecordedXdr() {
        for (entry in SEP51_CORPUS) {
            assertEquals(
                entry.xdr,
                codecFor(entry).jsonToXdr(entry.json),
                "encoding the recorded JSON of ${describe(entry)}"
            )
        }
    }

    @Test
    fun corpusXdrRendersAsTheRecordedJson() {
        for (entry in SEP51_CORPUS) {
            assertEquals(
                entry.json,
                codecFor(entry).xdrToJson(entry.xdr),
                "rendering ${describe(entry)}"
            )
        }
    }

    @Test
    fun everyCorpusTypeResolvesToACodec() {
        assertTrue(SEP51_CORPUS.isNotEmpty(), "the embedded corpus is empty")
        val types = SEP51_CORPUS.map { it.kmpType }.toSet()
        assertTrue(
            types.size >= 40,
            "the corpus spans only ${types.size} types; it is meant to span at least 40"
        )
        for (entry in SEP51_CORPUS.distinctBy { it.kmpType }) {
            codecFor(entry)
        }
    }

    @Test
    fun corpusPinsTheNameRulesThatDependOnTheXdrDefinitions() {
        val filterMembers = SEP51_CORPUS
            .filter { it.kmpType == "BinaryFuseFilterTypeXdr" }
            .map { it.json }
            .toSet()
        assertEquals(
            setOf("\"b8_bit\"", "\"b16_bit\"", "\"b32_bit\""),
            filterMembers,
            "the corpus must pin every member of the one enum whose members start with a digit"
        )

        for (kmpType in TYPE_FIELD_TYPES) {
            val entry = entryFor(kmpType)
            val codec = codecFor(entry)
            val rendered = codec.xdrToJson(entry.xdr)
            assertTrue(
                rendered.contains("\"type\":"),
                "$kmpType must render its type field under the key \"type\": $rendered"
            )
            assertFalse(
                rendered.contains("\"type_\":"),
                "$kmpType must never emit the escaped key \"type_\": $rendered"
            )

            val aliased = entry.json.replaceFirst("\"type\":", "\"type_\":")
            assertNotEquals(
                entry.json,
                aliased,
                "the recorded JSON of $kmpType has no \"type\" key to spell as \"type_\""
            )
            assertEquals(
                entry.xdr,
                codec.jsonToXdr(aliased),
                "$kmpType must accept \"type_\" as an input alias for \"type\""
            )
        }

        for (label in SEP51_UNRESOLVABLE_ENUM_MEMBERS) {
            val enumType = label.substringBefore('.') + "Xdr"
            assertTrue(
                SEP51_CORPUS.any { it.kmpType == enumType },
                "the name table could not resolve $label against the reference, so the corpus " +
                    "must carry an entry for $enumType; add a seed for it"
            )
        }
        for (name in SEP51_UNRESOLVABLE_STRUCT_TYPES) {
            val structType = name + "Xdr"
            assertTrue(
                SEP51_CORPUS.any { it.kmpType == structType },
                "the name table could not resolve $name against the reference, so the corpus " +
                    "must carry an entry for $structType; add a seed for it"
            )
        }
    }

    private companion object {
        /** Every type declaring an XDR field named `type`, which SEP-0051 keys as `type`. */
        val TYPE_FIELD_TYPES = listOf(
            "ContractEventXdr",
            "SCSpecEventParamV0Xdr",
            "SCSpecFunctionInputV0Xdr",
            "SCSpecUDTStructFieldV0Xdr",
            "SCSpecUDTUnionCaseTupleV0Xdr",
            "SerializedBinaryFuseFilterXdr"
        )
    }
}
