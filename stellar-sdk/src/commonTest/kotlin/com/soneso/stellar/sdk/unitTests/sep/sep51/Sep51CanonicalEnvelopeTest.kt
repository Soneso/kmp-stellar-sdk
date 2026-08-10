package com.soneso.stellar.sdk.unitTests.sep.sep51

import com.soneso.stellar.sdk.StrKey
import com.soneso.stellar.sdk.xdr.AssetXdr
import com.soneso.stellar.sdk.xdr.ContractDataDurabilityXdr
import com.soneso.stellar.sdk.xdr.ContractExecutableXdr
import com.soneso.stellar.sdk.xdr.ContractIDPreimageXdr
import com.soneso.stellar.sdk.xdr.HostFunctionXdr
import com.soneso.stellar.sdk.xdr.LedgerKeyXdr
import com.soneso.stellar.sdk.xdr.MemoXdr
import com.soneso.stellar.sdk.xdr.MuxedAccountXdr
import com.soneso.stellar.sdk.xdr.OperationBodyXdr
import com.soneso.stellar.sdk.xdr.PreconditionsXdr
import com.soneso.stellar.sdk.xdr.SCAddressXdr
import com.soneso.stellar.sdk.xdr.SCValTypeXdr
import com.soneso.stellar.sdk.xdr.SCValXdr
import com.soneso.stellar.sdk.xdr.SorobanTransactionDataExtXdr
import com.soneso.stellar.sdk.xdr.TransactionEnvelopeXdr
import com.soneso.stellar.sdk.xdr.TransactionExtXdr
import com.soneso.stellar.sdk.xdr.toXdrBase64
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Pins the worked `TransactionEnvelope` example of SEP-0051 end to end: the JSON the
 * specification prints for that envelope must decode to the value the envelope holds, and it
 * must encode back to the base64 XDR the specification prints alongside it.
 *
 * The example is the specification's only whole-document sample, so it is where the individual
 * mapping rules are seen composed: strkey-valued addresses, a hyper as a base-10 string, void
 * arms as bare strings, an unset optional as null, empty variable-length arrays, and
 * hexadecimal for binary members.
 */
class Sep51CanonicalEnvelopeTest {

    private fun hexBytes(hex: String): ByteArray =
        ByteArray(hex.length / 2) { hex.substring(it * 2, it * 2 + 2).toInt(16).toByte() }

    @Test
    fun theEnvelopeExampleDecodesToTheValueItDescribes() {
        val envelope = TransactionEnvelopeXdr.fromXdrJson(ENVELOPE_JSON)

        val v1 = assertIs<TransactionEnvelopeXdr.V1>(envelope).value
        val tx = v1.tx
        val source = assertIs<MuxedAccountXdr.Ed25519>(tx.sourceAccount)
        assertEquals(
            "GDTJSJTEYGG7L23UZSROA5SNR4GJMOUXYNRDCVMEY3FPB22HUYWQBZIA",
            StrKey.encodeEd25519PublicKey(source.value.value)
        )
        assertEquals(2792036u, tx.fee.value)
        assertEquals(29059748724737L, tx.seqNum.value.value)
        assertEquals(PreconditionsXdr.Void, tx.cond)
        assertEquals(MemoXdr.Void, tx.memo)

        assertEquals(1, tx.operations.size)
        val operation = tx.operations.single()
        assertNull(operation.sourceAccount)
        val invoke = assertIs<OperationBodyXdr.InvokeHostFunctionOp>(operation.body).value
        assertTrue(invoke.auth.isEmpty())
        val create = assertIs<HostFunctionXdr.CreateContract>(invoke.hostFunction).value
        val preimage = assertIs<ContractIDPreimageXdr.FromAsset>(create.contractIdPreimage)
        assertEquals(AssetXdr.Void, preimage.value)
        assertEquals(ContractExecutableXdr.Void, create.executable)

        val soroban = assertIs<TransactionExtXdr.SorobanData>(tx.ext).value
        assertEquals(SorobanTransactionDataExtXdr.Void, soroban.ext)
        assertEquals(146813u, soroban.resources.instructions.value)
        assertEquals(0u, soroban.resources.diskReadBytes.value)
        assertEquals(248u, soroban.resources.writeBytes.value)
        assertEquals(2791936L, soroban.resourceFee.value)
        assertTrue(soroban.resources.footprint.readOnly.isEmpty())
        assertEquals(1, soroban.resources.footprint.readWrite.size)
        val entry = assertIs<LedgerKeyXdr.ContractData>(
            soroban.resources.footprint.readWrite.single()
        ).value
        val contract = assertIs<SCAddressXdr.ContractId>(entry.contract)
        assertEquals(
            "CDLZFC3SYJYDZT7K67VZ75HPJVIEUVNIXF47ZG2FB2RMQQVU2HHGCYSC",
            StrKey.encodeContract(contract.value.value.value)
        )
        assertEquals(
            SCValXdr.Void(SCValTypeXdr.SCV_LEDGER_KEY_CONTRACT_INSTANCE),
            entry.key
        )
        assertEquals(ContractDataDurabilityXdr.PERSISTENT, entry.durability)

        assertEquals(1, v1.signatures.size)
        val signature = v1.signatures.single()
        assertContentEquals(hexBytes("47a62d00"), signature.hint.value)
        assertContentEquals(
            hexBytes(
                "2b0edc5ba9423e0ac764466574945855b74c3207b7f2ae69a433a16bdf9c293dc2bc58a7" +
                    "1778a4e5e0143e6a4135e0c66da5a79af4b31d857a29696de9240d04"
            ),
            signature.signature.value
        )
    }

    @Test
    fun theEnvelopeExampleEncodesBackToItsBase64() {
        assertEquals(ENVELOPE_XDR, TransactionEnvelopeXdr.fromXdrJson(ENVELOPE_JSON).toXdrBase64())
    }

    private companion object {

        /** The base64 XDR SEP-0051 prints for its `TransactionEnvelope` example. */
        const val ENVELOPE_XDR: String =
            "AAAAAgAAAADmmSZkwY3163TMouB2TY8MljqXw2IxVYTGyvDrR6YtAAAqmmQAABpuAAAAAQAAAAAA" +
                "AAAAAAAAAQAAAAAAAAAYAAAAAQAAAAEAAAAAAAAAAQAAAAAAAAABAAAAAAAAAAAAAAABAAAABgAA" +
                "AAHXkotywnA8z+r365/0701QSlWouXn8m0UOoshCtNHOYQAAABQAAAABAAI9fQAAAAAAAAD4AAAA" +
                "AAAqmgAAAAABR6YtAAAAAEArDtxbqUI+CsdkRmV0lFhVt0wyB7fyrmmkM6Fr35wpPcK8WKcXeKTl" +
                "4BQ+akE14MZtpaea9LMdhXopaW3pJA0E"

        /**
         * The JSON SEP-0051 prints for the same envelope, without the insignificant whitespace
         * of the pretty-printed form the document shows it in.
         */
        const val ENVELOPE_JSON: String =
            "{\"tx\":{\"tx\":{\"source_account\":\"GDTJSJTEYGG7L23UZSROA5SNR4GJMOUXYNRD" +
                "CVMEY3FPB22HUYWQBZIA\",\"fee\":2792036,\"seq_num\":\"29059748724737\",\"co" +
                "nd\":\"none\",\"memo\":\"none\",\"operations\":[{\"source_account\":null," +
                "\"body\":{\"invoke_host_function\":{\"host_function\":{\"create_contract\"" +
                ":{\"contract_id_preimage\":{\"asset\":\"native\"},\"executable\":\"stellar" +
                "_asset\"}},\"auth\":[]}}}],\"ext\":{\"v1\":{\"ext\":\"v0\",\"resources\":{" +
                "\"footprint\":{\"read_only\":[],\"read_write\":[{\"contract_data\":{\"cont" +
                "ract\":\"CDLZFC3SYJYDZT7K67VZ75HPJVIEUVNIXF47ZG2FB2RMQQVU2HHGCYSC\",\"key" +
                "\":\"ledger_key_contract_instance\",\"durability\":\"persistent\"}}]},\"in" +
                "structions\":146813,\"disk_read_bytes\":0,\"write_bytes\":248},\"resource_" +
                "fee\":\"2791936\"}}},\"signatures\":[{\"hint\":\"47a62d00\",\"signature\":" +
                "\"2b0edc5ba9423e0ac764466574945855b74c3207b7f2ae69a433a16bdf9c293dc2bc58a7" +
                "1778a4e5e0143e6a4135e0c66da5a79af4b31d857a29696de9240d04\"}]}}"
    }
}
