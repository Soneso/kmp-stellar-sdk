package com.soneso.smartdemo.agent

import com.soneso.stellar.sdk.KeyPair
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AgentKeygenTest {

    private fun isHex64(value: String): Boolean =
        value.length == 64 && value.all { it in '0'..'9' || it in 'a'..'f' }

    /** Derives the public key hex for a seed independently of the keygen under test. */
    private fun derivePublicKeyHex(seedHex: String): String = runBlocking {
        val bytes = assertNotNull(Hex.decode(seedHex.lowercase()))
        Hex.encode(KeyPair.fromSecretSeed(bytes).getPublicKey())
    }

    // MARK: - resolveAgentKey

    @Test
    fun generatesAFreshKeypairAs64HexPublicKeyPlus64HexSeed() = runBlocking {
        val result = resolveAgentKey()
        assertTrue(result.generated)
        val seedHex = assertNotNull(result.secretSeedHex)
        assertTrue(isHex64(seedHex))
        assertTrue(isHex64(result.publicKeyHex))
        assertEquals(derivePublicKeyHex(seedHex), result.publicKeyHex)
    }

    @Test
    fun derivesTheHexPublicKeyFromASuppliedSeedAndDoesNotEchoIt() = runBlocking {
        val seedHex = assertNotNull(resolveAgentKey().secretSeedHex)
        val result = resolveAgentKey(seedHex)
        assertFalse(result.generated)
        assertNull(result.secretSeedHex)
        assertTrue(isHex64(result.publicKeyHex))
        assertEquals(derivePublicKeyHex(seedHex), result.publicKeyHex)
    }

    @Test
    fun acceptsAnUpperCaseHexSeedAndDerivesTheSamePublicKey() = runBlocking {
        val seedHex = assertNotNull(resolveAgentKey().secretSeedHex)
        val lower = resolveAgentKey(seedHex)
        val upper = resolveAgentKey(seedHex.uppercase())
        assertEquals(lower.publicKeyHex, upper.publicKeyHex)
    }

    @Test
    fun treatsAnEmptySeedAsGenerateAFreshKey() = runBlocking {
        val result = resolveAgentKey("")
        assertTrue(result.generated)
        assertTrue(isHex64(assertNotNull(result.secretSeedHex)))
    }

    @Test
    fun rejectsANonHexSeed(): Unit = runBlocking {
        assertFailsWith<AgentConfigException> { resolveAgentKey("not-a-seed") }
    }

    @Test
    fun rejectsAWrongLengthHexSeed(): Unit = runBlocking {
        assertFailsWith<AgentConfigException> { resolveAgentKey("abcd") }
        assertFailsWith<AgentConfigException> { resolveAgentKey("a".repeat(62)) }
    }

    @Test
    fun twoGeneratedSeedsDiffer() = runBlocking {
        val a = assertNotNull(resolveAgentKey().secretSeedHex)
        val b = assertNotNull(resolveAgentKey().secretSeedHex)
        assertNotEquals(a, b)
    }

    // MARK: - formatAgentKeyOutput

    @Test
    fun aGeneratedKeyPrintsTheHexSeedAndTheHexPublicKey() = runBlocking {
        val result = resolveAgentKey()
        val seedHex = assertNotNull(result.secretSeedHex)
        val out = formatAgentKeyOutput(result).joinToString("\n")
        assertTrue(out.contains(seedHex))
        assertTrue(out.contains(result.publicKeyHex))
        assertTrue(out.contains("Delegate-to-agent"))
    }

    @Test
    fun aSuppliedSeedPrintsOnlyTheHexPublicKeyNeverTheSecret() = runBlocking {
        val seedHex = assertNotNull(resolveAgentKey().secretSeedHex)
        val result = resolveAgentKey(seedHex)
        val out = formatAgentKeyOutput(result).joinToString("\n")
        assertTrue(out.contains(result.publicKeyHex))
        assertFalse(out.contains(seedHex))
    }

    // MARK: - shouldPrintAgentKey

    @Test
    fun honorsAgentPrintKeyTrueCaseInsensitively() {
        assertTrue(shouldPrintAgentKey(env = mapOf("AGENT_PRINT_KEY" to "true")))
        assertTrue(shouldPrintAgentKey(env = mapOf("AGENT_PRINT_KEY" to "TRUE")))
    }

    @Test
    fun honorsThePrintKeyArgument() {
        assertTrue(shouldPrintAgentKey(args = listOf("--print-key")))
    }

    @Test
    fun isFalseWithoutEitherTrigger() {
        assertFalse(shouldPrintAgentKey())
        assertFalse(shouldPrintAgentKey(env = mapOf("AGENT_PRINT_KEY" to "false")))
    }
}
