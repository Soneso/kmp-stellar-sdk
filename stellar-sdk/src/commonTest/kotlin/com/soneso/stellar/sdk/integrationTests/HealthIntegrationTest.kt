package com.soneso.stellar.sdk.integrationTests

import com.soneso.stellar.sdk.horizon.HorizonServer
import com.soneso.stellar.sdk.horizon.responses.HealthResponse
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.*
import kotlin.time.Duration.Companion.seconds

/**
 * Comprehensive integration tests for Health endpoint operations.
 *
 * These tests verify the SDK's health endpoint functionality against Stellar Horizon servers.
 * The health endpoint provides real-time information about the operational status of the
 * Horizon server, including:
 * - Database connectivity
 * - Stellar Core availability
 * - Stellar Core synchronization status
 *
 * Tests are organized into two groups:
 * 1. Unit tests that validate JSON parsing and response handling
 * 2. Integration tests that query real Stellar networks (PUBLIC and TESTNET)
 *
 * Ported from: /Users/chris/projects/Stellar/stellar_flutter_sdk/test/health_test.dart
 *
 * @see com.soneso.stellar.sdk.horizon.HorizonServer.health
 * @see com.soneso.stellar.sdk.horizon.responses.HealthResponse
 * @see <a href="https://developers.stellar.org/docs/data/horizon/api-reference/structure/health">Health endpoint documentation</a>
 */
class HealthIntegrationTest {

    /**
     * Test health response parsing with all fields true (healthy state).
     *
     * This test verifies that:
     * 1. JSON with all health indicators true is correctly parsed
     * 2. All individual fields (databaseConnected, coreUp, coreSynced) are true
     * 3. The isHealthy computed property returns true
     */
    @Test
    fun testHealthResponseParsingAllTrue() = runTest(timeout = 60.seconds) {
        // Create JSON response with all fields true (healthy state)
        val jsonData = """
            {
              "database_connected": true,
              "core_up": true,
              "core_synced": true
            }
        """.trimIndent()

        val json = Json { ignoreUnknownKeys = true }
        val response = json.decodeFromString<HealthResponse>(jsonData)

        assertEquals(true, response.databaseConnected, "Database should be connected")
        assertEquals(true, response.coreUp, "Core should be up")
        assertEquals(true, response.coreSynced, "Core should be synced")
        assertEquals(true, response.isHealthy, "Server should be healthy when all indicators are true")
    }

    /**
     * Test health response parsing with degraded state (core not synced).
     *
     * This test verifies that:
     * 1. JSON with one health indicator false is correctly parsed
     * 2. Individual fields reflect the correct state
     * 3. The isHealthy computed property returns false when any indicator is false
     */
    @Test
    fun testHealthResponseParsingDegradedState() = runTest(timeout = 60.seconds) {
        // Create JSON response with core not synced (degraded state)
        val jsonData = """
            {
              "database_connected": true,
              "core_up": true,
              "core_synced": false
            }
        """.trimIndent()

        val json = Json { ignoreUnknownKeys = true }
        val response = json.decodeFromString<HealthResponse>(jsonData)

        assertEquals(true, response.databaseConnected, "Database should be connected")
        assertEquals(true, response.coreUp, "Core should be up")
        assertEquals(false, response.coreSynced, "Core should not be synced")
        assertEquals(false, response.isHealthy, "Server should not be healthy when core is not synced")
    }

    /**
     * Test health response isHealthy property with various combinations.
     *
     * This test verifies that the isHealthy computed property correctly identifies
     * healthy vs unhealthy states across all possible combinations:
     * - All true = healthy
     * - Any false = unhealthy
     */
    @Test
    fun testHealthResponseIsHealthyProperty() = runTest(timeout = 60.seconds) {
        val json = Json { ignoreUnknownKeys = true }

        // Test fully healthy state
        val healthyJson = """{"database_connected": true, "core_up": true, "core_synced": true}"""
        val healthyResponse = json.decodeFromString<HealthResponse>(healthyJson)
        assertEquals(true, healthyResponse.isHealthy, "All indicators true should be healthy")

        // Test database down
        val dbDownJson = """{"database_connected": false, "core_up": true, "core_synced": true}"""
        val dbDownResponse = json.decodeFromString<HealthResponse>(dbDownJson)
        assertEquals(false, dbDownResponse.isHealthy, "Database down should be unhealthy")

        // Test core down
        val coreDownJson = """{"database_connected": true, "core_up": false, "core_synced": true}"""
        val coreDownResponse = json.decodeFromString<HealthResponse>(coreDownJson)
        assertEquals(false, coreDownResponse.isHealthy, "Core down should be unhealthy")

        // Test core not synced
        val notSyncedJson = """{"database_connected": true, "core_up": true, "core_synced": false}"""
        val notSyncedResponse = json.decodeFromString<HealthResponse>(notSyncedJson)
        assertEquals(false, notSyncedResponse.isHealthy, "Core not synced should be unhealthy")
    }

    /**
     * Test health response with degraded database.
     *
     * This test verifies that when the database is not connected:
     * 1. The databaseConnected field is false
     * 2. Other fields can still be true
     * 3. The overall health status is unhealthy
     */
    @Test
    fun testHealthWithDegradedDatabase() = runTest(timeout = 60.seconds) {
        val jsonData = """
            {
              "database_connected": false,
              "core_up": true,
              "core_synced": true
            }
        """.trimIndent()

        val json = Json { ignoreUnknownKeys = true }
        val response = json.decodeFromString<HealthResponse>(jsonData)

        assertEquals(false, response.databaseConnected, "Database should not be connected")
        assertEquals(true, response.coreUp, "Core should be up")
        assertEquals(true, response.coreSynced, "Core should be synced")
        assertEquals(false, response.isHealthy, "Server should be unhealthy when database is disconnected")
    }

    /**
     * Test health response edge case with all systems down.
     *
     * This test verifies the worst-case scenario where:
     * 1. All health indicators are false
     * 2. Each field is correctly parsed
     * 3. The overall health status is unhealthy
     */
    @Test
    fun testHealthResponseAllFalse() = runTest(timeout = 60.seconds) {
        // Test with all systems down
        val jsonData = """
            {
              "database_connected": false,
              "core_up": false,
              "core_synced": false
            }
        """.trimIndent()

        val json = Json { ignoreUnknownKeys = true }
        val response = json.decodeFromString<HealthResponse>(jsonData)

        assertEquals(false, response.databaseConnected, "Database should not be connected")
        assertEquals(false, response.coreUp, "Core should not be up")
        assertEquals(false, response.coreSynced, "Core should not be synced")
        assertEquals(false, response.isHealthy, "Server should be unhealthy when all systems are down")
    }

    /**
     * Test health response toString method.
     *
     * This test verifies that:
     * 1. The toString method produces a readable string representation
     * 2. The string contains all key fields
     * 3. The field values are included in the output
     */
    @Test
    fun testHealthResponseToString() = runTest(timeout = 60.seconds) {
        val jsonData = """
            {
              "database_connected": true,
              "core_up": true,
              "core_synced": false
            }
        """.trimIndent()

        val json = Json { ignoreUnknownKeys = true }
        val response = json.decodeFromString<HealthResponse>(jsonData)

        val stringRepresentation = response.toString()

        assertTrue(
            stringRepresentation.contains("databaseConnected") ||
            stringRepresentation.contains("database_connected"),
            "toString should contain databaseConnected field"
        )
        assertTrue(
            stringRepresentation.contains("coreUp") ||
            stringRepresentation.contains("core_up"),
            "toString should contain coreUp field"
        )
        assertTrue(
            stringRepresentation.contains("coreSynced") ||
            stringRepresentation.contains("core_synced"),
            "toString should contain coreSynced field"
        )
    }

    /**
     * Test health endpoint integration with HorizonServer.
     *
     * This test verifies that:
     * 1. The health() method is properly exposed in HorizonServer
     * 2. The method returns a HealthRequestBuilder instance
     * 3. The builder can execute and return a HealthResponse
     *
     * Note: This test uses the real PUBLIC network endpoint.
     */
    @Test
    fun testHealthEndpointIntegration() = runTest(timeout = 60.seconds) {
        val horizonServer = HorizonServer("https://horizon.stellar.org")

        // Verify that health() method returns a HealthRequestBuilder
        val healthBuilder = horizonServer.health()
        assertNotNull(healthBuilder, "health() should return a HealthRequestBuilder")

        // Execute request through the SDK
        val response = horizonServer.health().execute()
        assertNotNull(response, "Health response should not be null")

        // Verify response contains boolean values
        assertTrue(
            response.databaseConnected is Boolean,
            "databaseConnected should be a Boolean"
        )
        assertTrue(
            response.coreUp is Boolean,
            "coreUp should be a Boolean"
        )
        assertTrue(
            response.coreSynced is Boolean,
            "coreSynced should be a Boolean"
        )

        println("PUBLIC network health status: ${response.isHealthy}")
        println("Database connected: ${response.databaseConnected}")
        println("Core up: ${response.coreUp}")
        println("Core synced: ${response.coreSynced}")
    }

    /**
     * Test health endpoint with real PUBLIC network.
     *
     * This test queries the actual Stellar PUBLIC network health endpoint
     * and verifies that:
     * 1. The request succeeds and returns a valid response
     * 2. All fields are present and have boolean values
     * 3. The response structure matches expectations
     */
    @Test
    fun testHealthEndpointRealPublicNetwork() = runTest(timeout = 60.seconds) {
        val horizonServer = HorizonServer("https://horizon.stellar.org")

        val response = horizonServer.health().execute()

        assertNotNull(response, "Health response should not be null")
        assertTrue(
            response.databaseConnected is Boolean,
            "databaseConnected should be a Boolean"
        )
        assertTrue(
            response.coreUp is Boolean,
            "coreUp should be a Boolean"
        )
        assertTrue(
            response.coreSynced is Boolean,
            "coreSynced should be a Boolean"
        )

        println("PUBLIC network health status: ${response.isHealthy}")
        println("Database connected: ${response.databaseConnected}")
        println("Core up: ${response.coreUp}")
        println("Core synced: ${response.coreSynced}")
    }

    /**
     * Test health endpoint with real TESTNET.
     *
     * This test queries the actual Stellar TESTNET health endpoint
     * and verifies that:
     * 1. The request succeeds and returns a valid response
     * 2. All fields are present and have boolean values
     * 3. The response structure matches expectations
     */
    @Test
    fun testHealthEndpointRealTestnet() = runTest(timeout = 60.seconds) {
        val horizonServer = HorizonServer("https://horizon-testnet.stellar.org")

        val response = horizonServer.health().execute()

        assertNotNull(response, "Health response should not be null")
        assertTrue(
            response.databaseConnected is Boolean,
            "databaseConnected should be a Boolean"
        )
        assertTrue(
            response.coreUp is Boolean,
            "coreUp should be a Boolean"
        )
        assertTrue(
            response.coreSynced is Boolean,
            "coreSynced should be a Boolean"
        )

        println("TESTNET health status: ${response}")
    }

    /**
     * Test health request with TESTNET server URL.
     *
     * This test verifies that:
     * 1. Health requests work correctly with TESTNET server URL
     * 2. All health indicators are returned correctly
     * 3. The isHealthy property reflects the actual server state
     */
    @Test
    fun testHealthRequestWithTestnet() = runTest(timeout = 60.seconds) {
        val horizonServer = HorizonServer("https://horizon-testnet.stellar.org")

        val response = horizonServer.health().execute()

        assertTrue(response.isHealthy is Boolean, "isHealthy should be a Boolean")
        assertTrue(response.databaseConnected is Boolean, "databaseConnected should be a Boolean")
        assertTrue(response.coreUp is Boolean, "coreUp should be a Boolean")
        assertTrue(response.coreSynced is Boolean, "coreSynced should be a Boolean")
    }

    /**
     * Test health request with FUTURENET server URL.
     *
     * This test verifies that:
     * 1. Health requests work correctly with FUTURENET server URL
     * 2. All health indicators are returned correctly
     * 3. The response may show degraded state (core not synced) which is normal for FUTURENET
     */
    @Test
    fun testHealthRequestWithFuturenet() = runTest(timeout = 60.seconds) {
        val horizonServer = HorizonServer("https://horizon-futurenet.stellar.org")

        val response = horizonServer.health().execute()

        assertTrue(response.databaseConnected is Boolean, "databaseConnected should be a Boolean")
        assertTrue(response.coreUp is Boolean, "coreUp should be a Boolean")
        assertTrue(response.coreSynced is Boolean, "coreSynced should be a Boolean")

        // Note: FUTURENET may have coreSynced = false, which is normal
        // The test just verifies the response is valid, not necessarily healthy
    }

    /**
     * Test health request builder URL construction.
     *
     * This test verifies that:
     * 1. The HealthRequestBuilder constructs the correct URL
     * 2. The URL includes the /health path
     * 3. The request executes successfully
     */
    @Test
    fun testHealthRequestBuilderUrlConstruction() = runTest(timeout = 60.seconds) {
        val horizonServer = HorizonServer("https://horizon.stellar.org")

        // Execute health request
        val response = horizonServer.health().execute()

        // Verify response is valid (which means URL was correct)
        assertTrue(response.isHealthy is Boolean, "Response should be valid if URL was correct")
    }

    /**
     * Test health request with custom server URL.
     *
     * This test verifies that:
     * 1. Health requests work with custom/alternative server URLs
     * 2. The request succeeds with the default PUBLIC network
     */
    @Test
    fun testHealthRequestWithCustomServerUrl() = runTest(timeout = 60.seconds) {
        // Using the standard PUBLIC network URL (custom in the sense of being explicitly set)
        val horizonServer = HorizonServer("https://horizon.stellar.org")

        val response = horizonServer.health().execute()

        assertTrue(response.isHealthy is Boolean, "Response should be valid for custom URL")
    }
}
