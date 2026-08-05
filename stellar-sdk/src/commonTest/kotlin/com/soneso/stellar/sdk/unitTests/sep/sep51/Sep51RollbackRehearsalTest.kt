package com.soneso.stellar.sdk.unitTests.sep.sep51

import com.soneso.stellar.sdk.xdr.TransactionEnvelopeXdr
import com.soneso.stellar.sdk.xdr.fromXdrBase64
import com.soneso.stellar.sdk.xdr.toXdrBase64
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Freezes one transaction envelope in both wire forms and holds the SDK to them byte for byte,
 * so that any change to the XDR-JSON (SEP-0051) rendering surfaces as a failure here rather
 * than in a consumer reading or writing documents this SDK produced.
 *
 * The envelope is chosen to exercise the renderings a change is most likely to disturb: strkeys
 * for a plain and a muxed account, a hyper as a base-10 string, an asset code trimmed of its
 * padding, a set optional, variable-length opaque as hexadecimal, a text memo, a value union
 * arm and a void union arm.
 */
class Sep51RollbackRehearsalTest {

    @Test
    fun theFrozenJsonEncodesToTheFrozenXdr() {
        assertEquals(
            FROZEN_XDR,
            TransactionEnvelopeXdr.fromXdrJson(FROZEN_JSON).toXdrBase64(),
            REVIEW_NOTE
        )
    }

    @Test
    fun theFrozenXdrRendersAsTheFrozenJson() {
        assertEquals(
            FROZEN_JSON,
            TransactionEnvelopeXdr.fromXdrBase64(FROZEN_XDR).toXdrJson(),
            REVIEW_NOTE
        )
    }

    private companion object {

        const val REVIEW_NOTE: String =
            "The XDR-JSON wire format of this SDK has changed. Documents already written by " +
                "consumers will no longer read back the same way, so the change has to be a " +
                "deliberate one: confirm it against SEP-0051, then update both the frozen " +
                "constant in this test and the CHANGELOG entry describing the break."

        /** A transaction envelope frozen in its binary form. */
        const val FROZEN_XDR: String =
            "AAAAAgAAAAAREREREREREREREREREREREREREREREREREREREREREQAAAMgAAAADAAAAAQAAAAEA" +
                "AAAAAAAAAAAAAABkAAAAAAAAAQAAAAVoZWxsbwAAAAAAAAIAAAAAAAAAAQAAAAAiIiIiIiIiIiIi" +
                "IiIiIiIiIiIiIiIiIiIiIiIiIiIiIgAAAAFVU0QAAAAAADMzMzMzMzMzMzMzMzMzMzMzMzMzMzMz" +
                "MzMzMzMzMzMzAAAAAACYln8AAAABAAABAAAAAAAAAAAHRERERERERERERERERERERERERERERERE" +
                "REREREREREQAAAAKAAAABG5hbWUAAAABAAAAAwECAwAAAAAAAAAAAaq7zN0AAAAEAQIDBA=="

        /** The same envelope frozen in the XDR-JSON form this SDK emits for it. */
        const val FROZEN_JSON: String =
            "{\"tx\":{\"tx\":{\"source_account\":\"GAIRCEIRCEIRCEIRCEIRCEIRCEIRCEIRCEIR" +
                "CEIRCEIRCEIRCEIRCF6M\",\"fee\":200,\"seq_num\":\"12884901889\",\"cond\":{" +
                "\"time\":{\"min_time\":\"0\",\"max_time\":\"1677721600\"}},\"memo\":{\"tex" +
                "t\":\"hello\"},\"operations\":[{\"source_account\":null,\"body\":{\"paymen" +
                "t\":{\"destination\":\"GARCEIRCEIRCEIRCEIRCEIRCEIRCEIRCEIRCEIRCEIRCEIRCEIR" +
                "CFRVX\",\"asset\":{\"credit_alphanum4\":{\"asset_code\":\"USD\",\"issuer\"" +
                ":\"GAZTGMZTGMZTGMZTGMZTGMZTGMZTGMZTGMZTGMZTGMZTGMZTGMZTHCM6\"}},\"amount\"" +
                ":\"9999999\"}}},{\"source_account\":\"MBCEIRCEIRCEIRCEIRCEIRCEIRCEIRCEIRCE" +
                "IRCEIRCEIRCEIRCEIAAAAAAAAAAAA4WDM\",\"body\":{\"manage_data\":{\"data_name" +
                "\":\"name\",\"data_value\":\"010203\"}}}],\"ext\":\"v0\"},\"signatures\":[" +
                "{\"hint\":\"aabbccdd\",\"signature\":\"01020304\"}]}}"
    }
}
