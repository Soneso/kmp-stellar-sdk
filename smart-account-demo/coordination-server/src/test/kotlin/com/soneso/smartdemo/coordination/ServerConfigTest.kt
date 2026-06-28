package com.soneso.smartdemo.coordination

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class ServerConfigTest {

    @Test
    fun usesDefaultsWithOnlyATokenInTheEnvironment() {
        val config = ServerConfig.resolve(
            args = emptyList(),
            environment = mapOf("COORDINATION_TOKEN" to "secret"),
        )
        assertEquals(ServerConfig.DEFAULT_PORT, config.port)
        assertEquals("secret", config.token)
        assertNull(config.storePath)
    }

    @Test
    fun readsPortAndStoreFromTheEnvironment() {
        val config = ServerConfig.resolve(
            args = emptyList(),
            environment = mapOf(
                "COORDINATION_TOKEN" to "secret",
                "PORT" to "9000",
                "COORDINATION_STORE" to "/var/data/store.json",
            ),
        )
        assertEquals(9000, config.port)
        assertEquals("/var/data/store.json", config.storePath)
    }

    @Test
    fun cliFlagsOverrideEnvironmentVariables() {
        val config = ServerConfig.resolve(
            args = listOf("--token", "flag-token", "--port", "1234"),
            environment = mapOf("COORDINATION_TOKEN" to "env-token", "PORT" to "8787"),
        )
        assertEquals("flag-token", config.token)
        assertEquals(1234, config.port)
    }

    @Test
    fun acceptsTheFlagEqualsValueForm() {
        val config = ServerConfig.resolve(
            args = listOf("--token=abc", "--port=2020", "--store=/tmp/s.json"),
            environment = emptyMap(),
        )
        assertEquals("abc", config.token)
        assertEquals(2020, config.port)
        assertEquals("/tmp/s.json", config.storePath)
    }

    @Test
    fun throwsWhenNoTokenIsConfigured() {
        assertFailsWith<ConfigException> {
            ServerConfig.resolve(args = emptyList(), environment = emptyMap())
        }
    }

    @Test
    fun throwsOnAnEmptyToken() {
        assertFailsWith<ConfigException> {
            ServerConfig.resolve(args = listOf("--token", ""), environment = emptyMap())
        }
    }

    @Test
    fun throwsOnANonNumericPort() {
        assertFailsWith<ConfigException> {
            ServerConfig.resolve(args = listOf("--token", "t", "--port", "abc"), environment = emptyMap())
        }
    }

    @Test
    fun throwsOnAnOutOfRangePort() {
        assertFailsWith<ConfigException> {
            ServerConfig.resolve(args = listOf("--token", "t", "--port", "70000"), environment = emptyMap())
        }
    }

    @Test
    fun throwsOnAnUnknownFlag() {
        assertFailsWith<ConfigException> {
            ServerConfig.resolve(args = listOf("--token", "t", "--bogus", "x"), environment = emptyMap())
        }
    }

    @Test
    fun throwsOnAMissingFlagValue() {
        assertFailsWith<ConfigException> {
            ServerConfig.resolve(args = listOf("--token"), environment = emptyMap())
        }
    }
}
