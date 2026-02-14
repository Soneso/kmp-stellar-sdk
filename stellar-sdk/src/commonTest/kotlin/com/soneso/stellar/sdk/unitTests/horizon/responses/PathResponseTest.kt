package com.soneso.stellar.sdk.unitTests.horizon.responses

import com.soneso.stellar.sdk.horizon.responses.PathResponse
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PathResponseTest {

    private val json = Json { ignoreUnknownKeys = true }

    private val pathJson = """
    {
        "destination_amount": "20.0000000",
        "destination_asset_type": "credit_alphanum4",
        "destination_asset_code": "EUR",
        "destination_asset_issuer": "GDSBCQO34HWPGUGQSP3QBFEXVTSR2PW46UIGTHVWGWJGQKH3AFNHXHXN",
        "source_amount": "30.0000000",
        "source_asset_type": "credit_alphanum4",
        "source_asset_code": "USD",
        "source_asset_issuer": "GCDNJUBQSX7AJWLJACMJ7I4BC3Z47BQUTMHEICZLE6MU4KQBRYG5JY6B",
        "path": [
            {"asset_type": "native"},
            {"asset_type": "credit_alphanum4", "asset_code": "BTC", "asset_issuer": "GBTEST123"}
        ]
    }
    """.trimIndent()

    @Test
    fun testDeserialization() {
        val path = json.decodeFromString<PathResponse>(pathJson)
        assertEquals("20.0000000", path.destinationAmount)
        assertEquals("credit_alphanum4", path.destinationAssetType)
        assertEquals("EUR", path.destinationAssetCode)
        assertEquals("GDSBCQO34HWPGUGQSP3QBFEXVTSR2PW46UIGTHVWGWJGQKH3AFNHXHXN", path.destinationAssetIssuer)
        assertEquals("30.0000000", path.sourceAmount)
        assertEquals("credit_alphanum4", path.sourceAssetType)
        assertEquals("USD", path.sourceAssetCode)
        assertEquals("GCDNJUBQSX7AJWLJACMJ7I4BC3Z47BQUTMHEICZLE6MU4KQBRYG5JY6B", path.sourceAssetIssuer)
    }

    @Test
    fun testPath() {
        val path = json.decodeFromString<PathResponse>(pathJson)
        assertEquals(2, path.path.size)
        assertTrue(path.path[0].isNative())
        assertEquals("BTC", path.path[1].assetCode)
    }

    @Test
    fun testGetDestinationAsset() {
        val path = json.decodeFromString<PathResponse>(pathJson)
        val destAsset = path.getDestinationAsset()
        assertEquals("credit_alphanum4", destAsset.assetType)
        assertEquals("EUR", destAsset.assetCode)
        assertEquals("GDSBCQO34HWPGUGQSP3QBFEXVTSR2PW46UIGTHVWGWJGQKH3AFNHXHXN", destAsset.assetIssuer)
    }

    @Test
    fun testGetSourceAsset() {
        val path = json.decodeFromString<PathResponse>(pathJson)
        val sourceAsset = path.getSourceAsset()
        assertEquals("credit_alphanum4", sourceAsset.assetType)
        assertEquals("USD", sourceAsset.assetCode)
        assertEquals("GCDNJUBQSX7AJWLJACMJ7I4BC3Z47BQUTMHEICZLE6MU4KQBRYG5JY6B", sourceAsset.assetIssuer)
    }

    @Test
    fun testNativePathDeserialization() {
        val nativePathJson = """
        {
            "destination_amount": "10.0",
            "destination_asset_type": "native",
            "source_amount": "10.0",
            "source_asset_type": "native",
            "path": []
        }
        """.trimIndent()
        val path = json.decodeFromString<PathResponse>(nativePathJson)
        assertEquals("native", path.destinationAssetType)
        assertNull(path.destinationAssetCode)
        assertNull(path.destinationAssetIssuer)
        assertEquals("native", path.sourceAssetType)
        assertNull(path.sourceAssetCode)
        assertNull(path.sourceAssetIssuer)
        assertTrue(path.path.isEmpty())

        val destAsset = path.getDestinationAsset()
        assertTrue(destAsset.isNative())
        val srcAsset = path.getSourceAsset()
        assertTrue(srcAsset.isNative())
    }

    @Test
    fun testComprehensivePathResponseDeserialization() {
        val comprehensiveJson = """
        {
            "destination_amount": "1000.0000000",
            "destination_asset_type": "credit_alphanum4",
            "destination_asset_code": "USD",
            "destination_asset_issuer": "GCDNJUBQSX7AJWLJACMJ7I4BC3Z47BQUTMHEICZLE6MU4KQBRYG5JY6B",
            "source_amount": "500.0000000",
            "source_asset_type": "native",
            "source_asset_code": null,
            "source_asset_issuer": null,
            "path": [
                {
                    "asset_type": "credit_alphanum4",
                    "asset_code": "EUR",
                    "asset_issuer": "GBVFTZL5HIPT4PFQVTZVIWR77V7LWYCXU4CLYWWHHOEXB64XPG5LDMTU"
                },
                {
                    "asset_type": "credit_alphanum12",
                    "asset_code": "LONGASSETCODE",
                    "asset_issuer": "GAAZI4TCR3TY5OJHCTJC2A4QSY6CJWJH5IAJTGKIN2ER7LBNVKOCCWN7"
                }
            ]
        }
        """.trimIndent()

        val pathResponse = json.decodeFromString<PathResponse>(comprehensiveJson)

        assertEquals("1000.0000000", pathResponse.destinationAmount)
        assertEquals("credit_alphanum4", pathResponse.destinationAssetType)
        assertEquals("USD", pathResponse.destinationAssetCode)
        assertEquals("GCDNJUBQSX7AJWLJACMJ7I4BC3Z47BQUTMHEICZLE6MU4KQBRYG5JY6B", pathResponse.destinationAssetIssuer)
        assertEquals("500.0000000", pathResponse.sourceAmount)
        assertEquals("native", pathResponse.sourceAssetType)
        assertNull(pathResponse.sourceAssetCode)
        assertNull(pathResponse.sourceAssetIssuer)

        assertEquals(2, pathResponse.path.size)
        assertEquals("credit_alphanum4", pathResponse.path[0].assetType)
        assertEquals("EUR", pathResponse.path[0].assetCode)
        assertEquals("GBVFTZL5HIPT4PFQVTZVIWR77V7LWYCXU4CLYWWHHOEXB64XPG5LDMTU", pathResponse.path[0].assetIssuer)
        assertEquals("credit_alphanum12", pathResponse.path[1].assetType)
        assertEquals("LONGASSETCODE", pathResponse.path[1].assetCode)
        assertEquals("GAAZI4TCR3TY5OJHCTJC2A4QSY6CJWJH5IAJTGKIN2ER7LBNVKOCCWN7", pathResponse.path[1].assetIssuer)

        val destinationAsset = pathResponse.getDestinationAsset()
        assertEquals("credit_alphanum4", destinationAsset.assetType)
        assertEquals("USD", destinationAsset.assetCode)

        val sourceAsset = pathResponse.getSourceAsset()
        assertEquals("native", sourceAsset.assetType)
        assertNull(sourceAsset.assetCode)
    }

    @Test
    fun testPathResponseWithNativeToCredit12() {
        val nativeToCredit12Json = """
        {
            "destination_amount": "2500.0000000",
            "destination_asset_type": "credit_alphanum12",
            "destination_asset_code": "VERYLONGCODE",
            "destination_asset_issuer": "GBVFTZL5HIPT4PFQVTZVIWR77V7LWYCXU4CLYWWHHOEXB64XPG5LDMTU",
            "source_amount": "750.0000000",
            "source_asset_type": "native",
            "source_asset_code": null,
            "source_asset_issuer": null,
            "path": [
                {
                    "asset_type": "native"
                }
            ]
        }
        """.trimIndent()

        val pathResponse = json.decodeFromString<PathResponse>(nativeToCredit12Json)

        assertEquals("2500.0000000", pathResponse.destinationAmount)
        assertEquals("credit_alphanum12", pathResponse.destinationAssetType)
        assertEquals("VERYLONGCODE", pathResponse.destinationAssetCode)

        assertEquals(1, pathResponse.path.size)
        assertEquals("native", pathResponse.path[0].assetType)
        assertNull(pathResponse.path[0].assetCode)
        assertNull(pathResponse.path[0].assetIssuer)

        val destinationAsset = pathResponse.getDestinationAsset()
        assertEquals("credit_alphanum12", destinationAsset.assetType)
        assertEquals("VERYLONGCODE", destinationAsset.assetCode)
    }

    @Test
    fun testPathResponseWithCreditToCredit() {
        val creditToCreditJson = """
        {
            "destination_amount": "100.0000000",
            "destination_asset_type": "credit_alphanum4",
            "destination_asset_code": "EUR",
            "destination_asset_issuer": "GCDNJUBQSX7AJWLJACMJ7I4BC3Z47BQUTMHEICZLE6MU4KQBRYG5JY6B",
            "source_amount": "110.0000000",
            "source_asset_type": "credit_alphanum4",
            "source_asset_code": "USD",
            "source_asset_issuer": "GBVFTZL5HIPT4PFQVTZVIWR77V7LWYCXU4CLYWWHHOEXB64XPG5LDMTU",
            "path": []
        }
        """.trimIndent()

        val pathResponse = json.decodeFromString<PathResponse>(creditToCreditJson)

        assertEquals("100.0000000", pathResponse.destinationAmount)
        assertEquals("EUR", pathResponse.destinationAssetCode)
        assertEquals("110.0000000", pathResponse.sourceAmount)
        assertEquals("USD", pathResponse.sourceAssetCode)
        assertEquals("GBVFTZL5HIPT4PFQVTZVIWR77V7LWYCXU4CLYWWHHOEXB64XPG5LDMTU", pathResponse.sourceAssetIssuer)
        assertTrue(pathResponse.path.isEmpty())

        val sourceAsset = pathResponse.getSourceAsset()
        assertEquals("credit_alphanum4", sourceAsset.assetType)
        assertEquals("USD", sourceAsset.assetCode)
    }

    @Test
    fun testPathResponseWithComplexPath() {
        val complexPathJson = """
        {
            "destination_amount": "5000.0000000",
            "destination_asset_type": "credit_alphanum4",
            "destination_asset_code": "BTC",
            "destination_asset_issuer": "GAAZI4TCR3TY5OJHCTJC2A4QSY6CJWJH5IAJTGKIN2ER7LBNVKOCCWN7",
            "source_amount": "150000.0000000",
            "source_asset_type": "native",
            "source_asset_code": null,
            "source_asset_issuer": null,
            "path": [
                {
                    "asset_type": "credit_alphanum4",
                    "asset_code": "USD",
                    "asset_issuer": "GCDNJUBQSX7AJWLJACMJ7I4BC3Z47BQUTMHEICZLE6MU4KQBRYG5JY6B"
                },
                {
                    "asset_type": "credit_alphanum4",
                    "asset_code": "EUR",
                    "asset_issuer": "GBVFTZL5HIPT4PFQVTZVIWR77V7LWYCXU4CLYWWHHOEXB64XPG5LDMTU"
                },
                {
                    "asset_type": "native"
                },
                {
                    "asset_type": "credit_alphanum12",
                    "asset_code": "STABLECOIN12",
                    "asset_issuer": "GCDNJUBQSX7AJWLJACMJ7I4BC3Z47BQUTMHEICZLE6MU4KQBRYG5JY6B"
                }
            ]
        }
        """.trimIndent()

        val pathResponse = json.decodeFromString<PathResponse>(complexPathJson)

        assertEquals(4, pathResponse.path.size)
        assertEquals("USD", pathResponse.path[0].assetCode)
        assertEquals("EUR", pathResponse.path[1].assetCode)
        assertEquals("native", pathResponse.path[2].assetType)
        assertNull(pathResponse.path[2].assetCode)
        assertEquals("STABLECOIN12", pathResponse.path[3].assetCode)
        assertEquals("credit_alphanum12", pathResponse.path[3].assetType)
    }

    @Test
    fun testPathResponseMethodEdgeCases() {
        val edgeCaseJson = """
        {
            "destination_amount": "1.0000000",
            "destination_asset_type": "native",
            "destination_asset_code": null,
            "destination_asset_issuer": null,
            "source_amount": "1.0000000",
            "source_asset_type": "credit_alphanum12",
            "source_asset_code": "MAXLENGTHCODE",
            "source_asset_issuer": "GAAZI4TCR3TY5OJHCTJC2A4QSY6CJWJH5IAJTGKIN2ER7LBNVKOCCWN7",
            "path": []
        }
        """.trimIndent()

        val pathResponse = json.decodeFromString<PathResponse>(edgeCaseJson)

        assertEquals("native", pathResponse.destinationAssetType)
        assertNull(pathResponse.destinationAssetCode)
        assertNull(pathResponse.destinationAssetIssuer)
        assertEquals("credit_alphanum12", pathResponse.sourceAssetType)
        assertEquals("MAXLENGTHCODE", pathResponse.sourceAssetCode)

        val destinationAsset = pathResponse.getDestinationAsset()
        assertEquals("native", destinationAsset.assetType)
        assertNull(destinationAsset.assetCode)

        val sourceAsset = pathResponse.getSourceAsset()
        assertEquals("credit_alphanum12", sourceAsset.assetType)
        assertEquals("MAXLENGTHCODE", sourceAsset.assetCode)
    }
}
