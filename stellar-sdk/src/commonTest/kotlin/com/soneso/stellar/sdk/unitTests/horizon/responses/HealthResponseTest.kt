package com.soneso.stellar.sdk.unitTests.horizon.responses

import com.soneso.stellar.sdk.horizon.responses.HealthResponse
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HealthResponseTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun testHealthyDeserialization() {
        val healthJson = """
        {
            "database_connected": true,
            "core_up": true,
            "core_synced": true
        }
        """.trimIndent()
        val health = json.decodeFromString<HealthResponse>(healthJson)
        assertTrue(health.databaseConnected)
        assertTrue(health.coreUp)
        assertTrue(health.coreSynced)
        assertTrue(health.isHealthy)
    }

    @Test
    fun testUnhealthyDatabase() {
        val healthJson = """
        {
            "database_connected": false,
            "core_up": true,
            "core_synced": true
        }
        """.trimIndent()
        val health = json.decodeFromString<HealthResponse>(healthJson)
        assertFalse(health.databaseConnected)
        assertTrue(health.coreUp)
        assertTrue(health.coreSynced)
        assertFalse(health.isHealthy)
    }

    @Test
    fun testUnhealthyCoreDown() {
        val healthJson = """
        {
            "database_connected": true,
            "core_up": false,
            "core_synced": true
        }
        """.trimIndent()
        val health = json.decodeFromString<HealthResponse>(healthJson)
        assertTrue(health.databaseConnected)
        assertFalse(health.coreUp)
        assertFalse(health.isHealthy)
    }

    @Test
    fun testUnhealthyCoreNotSynced() {
        val healthJson = """
        {
            "database_connected": true,
            "core_up": true,
            "core_synced": false
        }
        """.trimIndent()
        val health = json.decodeFromString<HealthResponse>(healthJson)
        assertTrue(health.coreUp)
        assertFalse(health.coreSynced)
        assertFalse(health.isHealthy)
    }

    @Test
    fun testAllUnhealthy() {
        val healthJson = """
        {
            "database_connected": false,
            "core_up": false,
            "core_synced": false
        }
        """.trimIndent()
        val health = json.decodeFromString<HealthResponse>(healthJson)
        assertFalse(health.databaseConnected)
        assertFalse(health.coreUp)
        assertFalse(health.coreSynced)
        assertFalse(health.isHealthy)
    }

    @Test
    fun testDataClassEquality() {
        val h1 = HealthResponse(true, true, true)
        val h2 = HealthResponse(true, true, true)
        assertEquals(h1, h2)
        assertEquals(h1.hashCode(), h2.hashCode())
    }
}
