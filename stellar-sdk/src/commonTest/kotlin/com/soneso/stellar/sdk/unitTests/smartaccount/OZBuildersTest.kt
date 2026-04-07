package com.soneso.stellar.sdk.unitTests.smartaccount

import com.soneso.stellar.sdk.smartaccount.core.ValidationException
import com.soneso.stellar.sdk.smartaccount.oz.ContextRuleType
import com.soneso.stellar.sdk.smartaccount.oz.OZBuilders
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Unit tests for [OZBuilders] context rule type builders and signer utilities.
 */
class OZBuildersTest {

    // MARK: - createDefaultContext

    @Test
    fun createDefaultContext_returnsDefault() {
        val result = OZBuilders.createDefaultContext()
        assertIs<ContextRuleType.Default>(result)
    }

    // MARK: - createCallContractContext

    @Test
    fun createCallContractContext_validAddress() {
        val address = "CAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAD2KM"
        val result = OZBuilders.createCallContractContext(address)
        assertIs<ContextRuleType.CallContract>(result)
        assertEquals(address, result.contractAddress)
    }

    @Test
    fun createCallContractContext_invalidAddress_throws() {
        assertFailsWith<ValidationException> {
            OZBuilders.createCallContractContext("GABC...")
        }
    }

    @Test
    fun createCallContractContext_emptyAddress_throws() {
        assertFailsWith<ValidationException> {
            OZBuilders.createCallContractContext("")
        }
    }

    // MARK: - createCreateContractContext (hex)

    @Test
    fun createCreateContractContext_validHex() {
        val hex = "a".repeat(64)
        val result = OZBuilders.createCreateContractContext(hex)
        assertIs<ContextRuleType.CreateContract>(result)
        assertEquals(32, result.wasmHash.size)
    }

    @Test
    fun createCreateContractContext_validHexWith0xPrefix() {
        val hex = "0x" + "b".repeat(64)
        val result = OZBuilders.createCreateContractContext(hex)
        assertIs<ContextRuleType.CreateContract>(result)
        assertEquals(32, result.wasmHash.size)
    }

    @Test
    fun createCreateContractContext_shortHex_throws() {
        assertFailsWith<ValidationException> {
            OZBuilders.createCreateContractContext("abc123")
        }
    }

    @Test
    fun createCreateContractContext_longHex_throws() {
        assertFailsWith<ValidationException> {
            OZBuilders.createCreateContractContext("a".repeat(66))
        }
    }

    // MARK: - createCreateContractContext (bytes)

    @Test
    fun createCreateContractContext_validBytes() {
        val bytes = ByteArray(32) { it.toByte() }
        val result = OZBuilders.createCreateContractContext(bytes)
        assertIs<ContextRuleType.CreateContract>(result)
        assertTrue(bytes.contentEquals(result.wasmHash))
    }

    @Test
    fun createCreateContractContext_wrongSizeBytes_throws() {
        assertFailsWith<ValidationException> {
            OZBuilders.createCreateContractContext(ByteArray(16))
        }
    }

    // MARK: - collectUniqueSignersFromRules

    @Test
    fun collectUniqueSignersFromRules_emptyRules() {
        val result = OZBuilders.collectUniqueSignersFromRules(emptyList())
        assertTrue(result.isEmpty())
    }
}
