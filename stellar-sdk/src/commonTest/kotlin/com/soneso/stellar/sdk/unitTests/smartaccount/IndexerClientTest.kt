//
//  IndexerClientTest.kt
//  Stellar SDK Kotlin Multiplatform
//
//  Copyright (c) 2026 Soneso. All rights reserved.
//

package com.soneso.stellar.sdk.unitTests.smartaccount

import com.soneso.stellar.sdk.Network
import com.soneso.stellar.sdk.smartaccount.core.*
import com.soneso.stellar.sdk.smartaccount.oz.*
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class IndexerClientTest {

    // Valid Stellar test addresses (56 characters with valid checksum)
    private val testAccountId = "GCZHXL5HXQX5ABDM26LHYRCQZ5OJFHLOPLZX47WEBP3V2PF5AVFK2A5D"
    private val testContractId = "CA2LVQXQLGPWHV2QO5ENVAGWM2TYICRMWXW4UXBPVKV26WLKU2V3UTH5"

    companion object {
        private val contractSummaryJson = """
            {
                "contract_id": "CA2LVQXQLGPWHV2QO5ENVAGWM2TYICRMWXW4UXBPVKV26WLKU2V3UTH5",
                "context_rule_count": 2,
                "external_signer_count": 1,
                "delegated_signer_count": 1,
                "native_signer_count": 0,
                "first_seen_ledger": 100000,
                "last_seen_ledger": 200000,
                "context_rule_ids": [0, 1]
            }
        """.trimIndent()

        private fun createMockClient(
            responseBody: String,
            statusCode: HttpStatusCode = HttpStatusCode.OK,
            contentType: String = "application/json"
        ): HttpClient {
            return HttpClient(MockEngine) {
                install(ContentNegotiation) {
                    json(Json { ignoreUnknownKeys = true; isLenient = true })
                }
                engine {
                    addHandler {
                        respond(
                            content = responseBody,
                            status = statusCode,
                            headers = headersOf(HttpHeaders.ContentType, contentType)
                        )
                    }
                }
            }
        }

        private fun createCapturingMockClient(
            responseBody: String,
            statusCode: HttpStatusCode = HttpStatusCode.OK,
            onRequestUrl: (String) -> Unit
        ): HttpClient {
            return HttpClient(MockEngine) {
                install(ContentNegotiation) {
                    json(Json { ignoreUnknownKeys = true; isLenient = true })
                }
                engine {
                    addHandler { request ->
                        onRequestUrl(request.url.toString())
                        respond(
                            content = responseBody,
                            status = statusCode,
                            headers = headersOf(HttpHeaders.ContentType, "application/json")
                        )
                    }
                }
            }
        }

        private fun createThrowingMockClient(exception: Throwable): HttpClient {
            return HttpClient(MockEngine) {
                install(ContentNegotiation) {
                    json(Json { ignoreUnknownKeys = true; isLenient = true })
                }
                engine {
                    addHandler {
                        throw exception
                    }
                }
            }
        }
    }

    // MARK: - Constructor validation

    @Test
    fun testConstructor_blankUrl_throwsConfigurationException() {
        assertFailsWith<ConfigurationException.InvalidConfig> {
            OZIndexerClient(indexerUrl = "")
        }
    }

    @Test
    fun testConstructor_whitespaceUrl_throwsConfigurationException() {
        assertFailsWith<ConfigurationException.InvalidConfig> {
            OZIndexerClient(indexerUrl = "   ")
        }
    }

    @Test
    fun testConstructor_httpUrl_throwsConfigurationException() {
        assertFailsWith<ConfigurationException.InvalidConfig> {
            OZIndexerClient(indexerUrl = "http://indexer.example.com")
        }
    }

    @Test
    fun testConstructor_httpsUrlSucceeds() {
        val client = OZIndexerClient(indexerUrl = "https://indexer.example.com")
        client.close()
    }

    @Test
    fun testConstructor_localhostHttpUrlSucceeds() {
        val client = OZIndexerClient(indexerUrl = "http://localhost:8080")
        client.close()
    }

    @Test
    fun testConstructor_ftpSchemeThrows() {
        assertFailsWith<ConfigurationException.InvalidConfig> {
            OZIndexerClient(indexerUrl = "ftp://indexer.example.com")
        }
    }

    @Test
    fun testConstructor_noSchemeThrows() {
        assertFailsWith<ConfigurationException.InvalidConfig> {
            OZIndexerClient(indexerUrl = "indexer.example.com")
        }
    }

    // MARK: - DEFAULT_INDEXER_URLS

    @Test
    fun testDefaultIndexerUrls_testnetHasUrl() {
        val url = OZIndexerClient.DEFAULT_INDEXER_URLS[Network.TESTNET.networkPassphrase]
        assertNotNull(url, "Testnet should have a default indexer URL configured")
        assertTrue(url.startsWith("https://"), "Default indexer URL must use HTTPS")
    }

    @Test
    fun testDefaultIndexerUrls_unknownNetworkReturnsNull() {
        val url = OZIndexerClient.DEFAULT_INDEXER_URLS["Custom Network ; 2026"]
        assertNull(url, "Unknown network must not have a default indexer URL")
    }

    // MARK: - getDefaultUrl

    @Test
    fun testGetDefaultUrl_testnetReturnsUrl() {
        val url = OZIndexerClient.getDefaultUrl(Network.TESTNET.networkPassphrase)
        assertNotNull(url)
        assertTrue(url.startsWith("https://"))
    }

    @Test
    fun testGetDefaultUrl_unknownNetworkReturnsNull() {
        val url = OZIndexerClient.getDefaultUrl("Unknown Network ; January 2099")
        assertNull(url)
    }

    @Test
    fun testGetDefaultUrl_mainnetReturnsNullOrUrl() {
        // Mainnet may or may not have a default URL depending on configuration
        val url = OZIndexerClient.getDefaultUrl(Network.PUBLIC.networkPassphrase)
        if (url != null) {
            assertTrue(url.startsWith("https://"), "If mainnet URL is set, it must use HTTPS")
        }
    }

    // MARK: - forNetwork

    @Test
    fun testForNetwork_testnetReturnsClient() {
        val client = OZIndexerClient.forNetwork(Network.TESTNET.networkPassphrase)
        assertNotNull(client, "forNetwork must return a client for testnet")
        client.close()
    }

    @Test
    fun testForNetwork_unknownNetworkReturnsNull() {
        val client = OZIndexerClient.forNetwork("Unknown Network ; 2099")
        assertNull(client, "forNetwork must return null for unknown networks")
    }

    @Test
    fun testForNetwork_mainnetReturnsNullCurrently() {
        // Mainnet URL is not yet configured, so forNetwork should return null
        val url = OZIndexerClient.getDefaultUrl(Network.PUBLIC.networkPassphrase)
        val client = OZIndexerClient.forNetwork(Network.PUBLIC.networkPassphrase)
        if (url == null) {
            assertNull(client, "forNetwork must return null when no default URL exists")
        } else {
            assertNotNull(client)
            client.close()
        }
    }

    // MARK: - lookupByCredentialId

    @Test
    fun testLookupByCredentialId_success() = runTest {
        val responseJson = """
            {
                "credentialId": "aabbccdd",
                "contracts": [$contractSummaryJson],
                "count": 1
            }
        """.trimIndent()

        val mockClient = createMockClient(responseJson)
        try {
            val indexer = OZIndexerClient(
                indexerUrl = "https://indexer.example.com",
                injectedClient = mockClient
            )

            // "qrvM3Q" is the base64url encoding of bytes [aa bb cc dd]
            val result = indexer.lookupByCredentialId("qrvM3Q")

            assertEquals("aabbccdd", result.credentialId)
            assertEquals(1, result.count)
            assertEquals(1, result.contracts.size)
            assertEquals(testContractId, result.contracts[0].contractId)
            assertEquals(2, result.contracts[0].contextRuleCount)

            indexer.close()
        } finally {
            mockClient.close()
        }
    }

    @Test
    fun testLookupByCredentialId_verifiesUrlPath() = runTest {
        val responseJson = """
            {
                "credentialId": "aabbccdd",
                "contracts": [],
                "count": 0
            }
        """.trimIndent()

        var capturedUrl: String? = null
        val mockClient = createCapturingMockClient(responseJson) { url ->
            capturedUrl = url
        }

        try {
            val indexer = OZIndexerClient(
                indexerUrl = "https://indexer.example.com",
                injectedClient = mockClient
            )

            indexer.lookupByCredentialId("qrvM3Q")

            assertNotNull(capturedUrl)
            assertTrue(
                capturedUrl!!.contains("/api/lookup/"),
                "Request URL must contain /api/lookup/ path"
            )
            // "qrvM3Q" decodes to bytes [aa bb cc dd] -> hex "aabbccdd"
            assertTrue(
                capturedUrl!!.endsWith("aabbccdd"),
                "Request URL must end with hex-encoded credential ID"
            )

            indexer.close()
        } finally {
            mockClient.close()
        }
    }

    @Test
    fun testLookupByCredentialId_invalidBase64url_throwsValidationException() = runTest {
        val mockClient = createMockClient("{}")
        try {
            val indexer = OZIndexerClient(
                indexerUrl = "https://indexer.example.com",
                injectedClient = mockClient
            )

            assertFailsWith<ValidationException.InvalidInput> {
                indexer.lookupByCredentialId("!!!invalid-base64url!!!")
            }

            indexer.close()
        } finally {
            mockClient.close()
        }
    }

    @Test
    fun testLookupByCredentialId_http404_throwsIndexerException() = runTest {
        val mockClient = createMockClient(
            responseBody = """{"error": "not found"}""",
            statusCode = HttpStatusCode.NotFound
        )
        try {
            val indexer = OZIndexerClient(
                indexerUrl = "https://indexer.example.com",
                injectedClient = mockClient
            )

            val exception = assertFailsWith<IndexerException.RequestFailed> {
                indexer.lookupByCredentialId("qrvM3Q")
            }
            assertTrue(exception.message!!.contains("404"))

            indexer.close()
        } finally {
            mockClient.close()
        }
    }

    @Test
    fun testLookupByCredentialId_http500_throwsIndexerException() = runTest {
        val mockClient = createMockClient(
            responseBody = """{"error": "internal server error"}""",
            statusCode = HttpStatusCode.InternalServerError
        )
        try {
            val indexer = OZIndexerClient(
                indexerUrl = "https://indexer.example.com",
                injectedClient = mockClient
            )

            val exception = assertFailsWith<IndexerException.RequestFailed> {
                indexer.lookupByCredentialId("qrvM3Q")
            }
            assertTrue(exception.message!!.contains("500"))

            indexer.close()
        } finally {
            mockClient.close()
        }
    }

    @Test
    fun testLookupByCredentialId_nonJsonResponse_throwsIndexerException() = runTest {
        val mockClient = createMockClient(
            responseBody = "<html>Not JSON</html>",
            contentType = "text/html"
        )
        try {
            val indexer = OZIndexerClient(
                indexerUrl = "https://indexer.example.com",
                injectedClient = mockClient
            )

            assertFailsWith<IndexerException.RequestFailed> {
                indexer.lookupByCredentialId("qrvM3Q")
            }

            indexer.close()
        } finally {
            mockClient.close()
        }
    }

    // MARK: - lookupByAddress

    @Test
    fun testLookupByAddress_successWithGAddress() = runTest {
        val responseJson = """
            {
                "signerAddress": "$testAccountId",
                "contracts": [$contractSummaryJson],
                "count": 1
            }
        """.trimIndent()

        val mockClient = createMockClient(responseJson)
        try {
            val indexer = OZIndexerClient(
                indexerUrl = "https://indexer.example.com",
                injectedClient = mockClient
            )

            val result = indexer.lookupByAddress(testAccountId)

            assertEquals(testAccountId, result.signerAddress)
            assertEquals(1, result.count)
            assertEquals(1, result.contracts.size)

            indexer.close()
        } finally {
            mockClient.close()
        }
    }

    @Test
    fun testLookupByAddress_successWithCAddress() = runTest {
        val responseJson = """
            {
                "signerAddress": "$testContractId",
                "contracts": [$contractSummaryJson],
                "count": 1
            }
        """.trimIndent()

        val mockClient = createMockClient(responseJson)
        try {
            val indexer = OZIndexerClient(
                indexerUrl = "https://indexer.example.com",
                injectedClient = mockClient
            )

            val result = indexer.lookupByAddress(testContractId)

            assertEquals(testContractId, result.signerAddress)
            assertEquals(1, result.count)

            indexer.close()
        } finally {
            mockClient.close()
        }
    }

    @Test
    fun testLookupByAddress_verifiesUrlPath() = runTest {
        val responseJson = """
            {
                "signerAddress": "$testAccountId",
                "contracts": [],
                "count": 0
            }
        """.trimIndent()

        var capturedUrl: String? = null
        val mockClient = createCapturingMockClient(responseJson) { url ->
            capturedUrl = url
        }

        try {
            val indexer = OZIndexerClient(
                indexerUrl = "https://indexer.example.com",
                injectedClient = mockClient
            )

            indexer.lookupByAddress(testAccountId)

            assertNotNull(capturedUrl)
            assertTrue(
                capturedUrl!!.contains("/api/lookup/address/"),
                "Request URL must contain /api/lookup/address/ path"
            )
            assertTrue(
                capturedUrl!!.endsWith(testAccountId),
                "Request URL must end with the address"
            )

            indexer.close()
        } finally {
            mockClient.close()
        }
    }

    @Test
    fun testLookupByAddress_invalidAddress_throwsValidationException() = runTest {
        val mockClient = createMockClient("{}")
        try {
            val indexer = OZIndexerClient(
                indexerUrl = "https://indexer.example.com",
                injectedClient = mockClient
            )

            assertFailsWith<ValidationException.InvalidAddress> {
                indexer.lookupByAddress("INVALID_ADDRESS")
            }

            indexer.close()
        } finally {
            mockClient.close()
        }
    }

    @Test
    fun testLookupByAddress_httpError_throwsIndexerException() = runTest {
        val mockClient = createMockClient(
            responseBody = """{"error": "service unavailable"}""",
            statusCode = HttpStatusCode.ServiceUnavailable
        )
        try {
            val indexer = OZIndexerClient(
                indexerUrl = "https://indexer.example.com",
                injectedClient = mockClient
            )

            assertFailsWith<IndexerException.RequestFailed> {
                indexer.lookupByAddress(testAccountId)
            }

            indexer.close()
        } finally {
            mockClient.close()
        }
    }

    // MARK: - getContract

    @Test
    fun testGetContract_success() = runTest {
        val responseJson = """
            {
                "contractId": "$testContractId",
                "summary": $contractSummaryJson,
                "contextRules": [
                    {
                        "context_rule_id": 0,
                        "signers": [
                            {
                                "signer_type": "External",
                                "credential_id": "aabbccdd"
                            },
                            {
                                "signer_type": "Delegated",
                                "signer_address": "$testAccountId"
                            }
                        ],
                        "policies": [
                            {
                                "policy_address": "$testContractId",
                                "install_params": {"limit": "1000000000"}
                            }
                        ]
                    },
                    {
                        "context_rule_id": 1,
                        "signers": [
                            {
                                "signer_type": "Native"
                            }
                        ],
                        "policies": []
                    }
                ]
            }
        """.trimIndent()

        val mockClient = createMockClient(responseJson)
        try {
            val indexer = OZIndexerClient(
                indexerUrl = "https://indexer.example.com",
                injectedClient = mockClient
            )

            val result = indexer.getContract(testContractId)

            assertEquals(testContractId, result.contractId)
            assertEquals(2, result.summary.contextRuleCount)
            assertEquals(1, result.summary.externalSignerCount)
            assertEquals(1, result.summary.delegatedSignerCount)
            assertEquals(0, result.summary.nativeSignerCount)
            assertEquals(100000, result.summary.firstSeenLedger)
            assertEquals(200000, result.summary.lastSeenLedger)
            assertEquals(listOf(0, 1), result.summary.contextRuleIds)

            // Context rule 0: External + Delegated signers, 1 policy
            val rule0 = result.contextRules[0]
            assertEquals(0, rule0.contextRuleId)
            assertEquals(2, rule0.signers.size)
            assertEquals("External", rule0.signers[0].signerType)
            assertEquals("aabbccdd", rule0.signers[0].credentialId)
            assertEquals("Delegated", rule0.signers[1].signerType)
            assertEquals(testAccountId, rule0.signers[1].signerAddress)
            assertEquals(1, rule0.policies.size)
            assertEquals(testContractId, rule0.policies[0].policyAddress)
            assertNotNull(rule0.policies[0].installParams)

            // Context rule 1: Native signer, no policies
            val rule1 = result.contextRules[1]
            assertEquals(1, rule1.contextRuleId)
            assertEquals(1, rule1.signers.size)
            assertEquals("Native", rule1.signers[0].signerType)
            assertEquals(0, rule1.policies.size)

            indexer.close()
        } finally {
            mockClient.close()
        }
    }

    @Test
    fun testGetContract_verifiesUrlPath() = runTest {
        val responseJson = """
            {
                "contractId": "$testContractId",
                "summary": $contractSummaryJson,
                "contextRules": []
            }
        """.trimIndent()

        var capturedUrl: String? = null
        val mockClient = createCapturingMockClient(responseJson) { url ->
            capturedUrl = url
        }

        try {
            val indexer = OZIndexerClient(
                indexerUrl = "https://indexer.example.com",
                injectedClient = mockClient
            )

            indexer.getContract(testContractId)

            assertNotNull(capturedUrl)
            assertTrue(
                capturedUrl!!.contains("/api/contract/"),
                "Request URL must contain /api/contract/ path"
            )
            assertTrue(
                capturedUrl!!.endsWith(testContractId),
                "Request URL must end with the contract ID"
            )

            indexer.close()
        } finally {
            mockClient.close()
        }
    }

    @Test
    fun testGetContract_invalidContractId_throwsValidationException() = runTest {
        val mockClient = createMockClient("{}")
        try {
            val indexer = OZIndexerClient(
                indexerUrl = "https://indexer.example.com",
                injectedClient = mockClient
            )

            assertFailsWith<ValidationException.InvalidAddress> {
                indexer.getContract(testAccountId) // G... address, not C...
            }

            indexer.close()
        } finally {
            mockClient.close()
        }
    }

    @Test
    fun testGetContract_httpError_throwsIndexerException() = runTest {
        val mockClient = createMockClient(
            responseBody = """{"error": "not found"}""",
            statusCode = HttpStatusCode.NotFound
        )
        try {
            val indexer = OZIndexerClient(
                indexerUrl = "https://indexer.example.com",
                injectedClient = mockClient
            )

            assertFailsWith<IndexerException.RequestFailed> {
                indexer.getContract(testContractId)
            }

            indexer.close()
        } finally {
            mockClient.close()
        }
    }

    // MARK: - getStats

    @Test
    fun testGetStats_success() = runTest {
        val responseJson = """
            {
                "stats": {
                    "total_events": 15234,
                    "unique_contracts": 842,
                    "unique_credentials": 1203,
                    "first_ledger": 50000,
                    "last_ledger": 250000,
                    "eventTypes": [
                        {"event_type": "signer_added", "count": 5000},
                        {"event_type": "signer_removed", "count": 1200},
                        {"event_type": "policy_added", "count": 3500}
                    ]
                }
            }
        """.trimIndent()

        val mockClient = createMockClient(responseJson)
        try {
            val indexer = OZIndexerClient(
                indexerUrl = "https://indexer.example.com",
                injectedClient = mockClient
            )

            val result = indexer.getStats()

            assertEquals(15234L, result.stats.totalEvents)
            assertEquals(842L, result.stats.uniqueContracts)
            assertEquals(1203L, result.stats.uniqueCredentials)
            assertEquals(50000L, result.stats.firstLedger)
            assertEquals(250000L, result.stats.lastLedger)
            assertEquals(3, result.stats.eventTypes.size)
            assertEquals("signer_added", result.stats.eventTypes[0].eventType)
            assertEquals(5000L, result.stats.eventTypes[0].count)
            assertEquals("signer_removed", result.stats.eventTypes[1].eventType)
            assertEquals(1200L, result.stats.eventTypes[1].count)
            assertEquals("policy_added", result.stats.eventTypes[2].eventType)
            assertEquals(3500L, result.stats.eventTypes[2].count)

            indexer.close()
        } finally {
            mockClient.close()
        }
    }

    @Test
    fun testGetStats_verifiesUrlPath() = runTest {
        val responseJson = """
            {
                "stats": {
                    "total_events": 0,
                    "unique_contracts": 0,
                    "unique_credentials": 0,
                    "first_ledger": 0,
                    "last_ledger": 0,
                    "eventTypes": []
                }
            }
        """.trimIndent()

        var capturedUrl: String? = null
        val mockClient = createCapturingMockClient(responseJson) { url ->
            capturedUrl = url
        }

        try {
            val indexer = OZIndexerClient(
                indexerUrl = "https://indexer.example.com",
                injectedClient = mockClient
            )

            indexer.getStats()

            assertNotNull(capturedUrl)
            assertTrue(
                capturedUrl!!.endsWith("/api/stats"),
                "Request URL must end with /api/stats"
            )

            indexer.close()
        } finally {
            mockClient.close()
        }
    }

    @Test
    fun testGetStats_httpError_throwsIndexerException() = runTest {
        val mockClient = createMockClient(
            responseBody = """{"error": "internal server error"}""",
            statusCode = HttpStatusCode.InternalServerError
        )
        try {
            val indexer = OZIndexerClient(
                indexerUrl = "https://indexer.example.com",
                injectedClient = mockClient
            )

            val exception = assertFailsWith<IndexerException.RequestFailed> {
                indexer.getStats()
            }
            assertTrue(exception.message!!.contains("500"))

            indexer.close()
        } finally {
            mockClient.close()
        }
    }

    // MARK: - performRequest: error body truncation

    @Test
    fun testPerformRequest_truncatesLongErrorBody() = runTest {
        val longBody = "x".repeat(300)
        val mockClient = createMockClient(
            responseBody = longBody,
            statusCode = HttpStatusCode.BadRequest
        )
        try {
            val indexer = OZIndexerClient(
                indexerUrl = "https://indexer.example.com",
                injectedClient = mockClient
            )

            val exception = assertFailsWith<IndexerException.RequestFailed> {
                indexer.lookupByCredentialId("qrvM3Q")
            }
            // Error body is 300 chars, should be truncated to 200 + "..."
            assertTrue(
                exception.message!!.contains("..."),
                "Long error body must be truncated with ellipsis"
            )
            // The truncated body should be at most 200 chars of the original
            assertFalse(
                exception.message!!.contains("x".repeat(201)),
                "Error body must be truncated to at most 200 characters"
            )

            indexer.close()
        } finally {
            mockClient.close()
        }
    }

    @Test
    fun testPerformRequest_shortErrorBodyNotTruncated() = runTest {
        val shortBody = "short error"
        val mockClient = createMockClient(
            responseBody = shortBody,
            statusCode = HttpStatusCode.BadRequest
        )
        try {
            val indexer = OZIndexerClient(
                indexerUrl = "https://indexer.example.com",
                injectedClient = mockClient
            )

            val exception = assertFailsWith<IndexerException.RequestFailed> {
                indexer.lookupByCredentialId("qrvM3Q")
            }
            assertTrue(exception.message!!.contains("short error"))
            assertFalse(
                exception.message!!.endsWith("..."),
                "Short error body must not be truncated"
            )

            indexer.close()
        } finally {
            mockClient.close()
        }
    }

    // MARK: - performRequest: timeout

    @Test
    fun testPerformRequest_timeout_throwsIndexerTimeoutException() = runTest {
        val mockClient = createThrowingMockClient(
            HttpRequestTimeoutException("https://indexer.example.com/api/stats", 10_000L)
        )
        try {
            val indexer = OZIndexerClient(
                indexerUrl = "https://indexer.example.com",
                injectedClient = mockClient
            )

            val exception = assertFailsWith<IndexerException.Timeout> {
                indexer.getStats()
            }
            assertTrue(
                exception.message!!.contains("timed out"),
                "Timeout exception message must indicate timeout"
            )

            indexer.close()
        } finally {
            mockClient.close()
        }
    }

    // MARK: - performRequest: generic exception wrapping

    @Test
    fun testPerformRequest_genericException_wrappedAsRequestFailed() = runTest {
        val mockClient = createThrowingMockClient(
            RuntimeException("unexpected network failure")
        )
        try {
            val indexer = OZIndexerClient(
                indexerUrl = "https://indexer.example.com",
                injectedClient = mockClient
            )

            val exception = assertFailsWith<IndexerException.RequestFailed> {
                indexer.lookupByCredentialId("qrvM3Q")
            }
            assertTrue(exception.message!!.contains("unexpected network failure"))

            indexer.close()
        } finally {
            mockClient.close()
        }
    }

    // MARK: - isHealthy

    @Test
    fun testIsHealthy_returnsTrue_whenStatusOk() = runTest {
        val mockClient = createMockClient("""{"status": "ok"}""")
        try {
            val indexer = OZIndexerClient(
                indexerUrl = "https://indexer.example.com",
                injectedClient = mockClient
            )

            assertTrue(indexer.isHealthy())

            indexer.close()
        } finally {
            mockClient.close()
        }
    }

    @Test
    fun testIsHealthy_returnsFalse_whenStatusError() = runTest {
        val mockClient = createMockClient("""{"status": "error"}""")
        try {
            val indexer = OZIndexerClient(
                indexerUrl = "https://indexer.example.com",
                injectedClient = mockClient
            )

            assertFalse(indexer.isHealthy())

            indexer.close()
        } finally {
            mockClient.close()
        }
    }

    @Test
    fun testIsHealthy_returnsFalse_whenNetworkError() = runTest {
        val mockClient = createThrowingMockClient(Exception("Connection refused"))
        try {
            val indexer = OZIndexerClient(
                indexerUrl = "https://indexer.example.com",
                injectedClient = mockClient
            )

            assertFalse(indexer.isHealthy())

            indexer.close()
        } finally {
            mockClient.close()
        }
    }

    @Test
    fun testIsHealthy_returnsFalse_whenNon2xxStatus() = runTest {
        val mockClient = createMockClient(
            responseBody = """{"status": "ok"}""",
            statusCode = HttpStatusCode.InternalServerError
        )
        try {
            val indexer = OZIndexerClient(
                indexerUrl = "https://indexer.example.com",
                injectedClient = mockClient
            )

            assertFalse(indexer.isHealthy())

            indexer.close()
        } finally {
            mockClient.close()
        }
    }

    @Test
    fun testIsHealthy_verifiesUrlPath() = runTest {
        var capturedUrl: String? = null
        val mockClient = createCapturingMockClient("""{"status": "ok"}""") { url ->
            capturedUrl = url
        }

        try {
            val indexer = OZIndexerClient(
                indexerUrl = "https://indexer.example.com",
                injectedClient = mockClient
            )

            indexer.isHealthy()

            assertNotNull(capturedUrl)
            assertTrue(
                capturedUrl!!.endsWith("/"),
                "Health check URL must be the root endpoint"
            )

            indexer.close()
        } finally {
            mockClient.close()
        }
    }

    // MARK: - close

    @Test
    fun testClose_clientIsAutoCloseable() {
        val indexer = OZIndexerClient(
            indexerUrl = "https://indexer.example.com"
        )
        // AutoCloseable contract: close() should not throw
        indexer.close()
    }

    @Test
    fun testClose_doubleCloseDoesNotThrow() {
        val indexer = OZIndexerClient(
            indexerUrl = "https://indexer.example.com"
        )
        indexer.close()
        // Second close should be safe
        indexer.close()
    }
}
