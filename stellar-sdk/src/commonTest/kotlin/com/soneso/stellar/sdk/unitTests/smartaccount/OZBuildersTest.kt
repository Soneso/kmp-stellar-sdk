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
        val result = OZBuilders.createDefaultContextType()
        assertIs<ContextRuleType.Default>(result)
    }

    // MARK: - createCallContractContext

    @Test
    fun createCallContractContext_validAddress() {
        val address = "CAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAD2KM"
        val result = OZBuilders.createCallContractContextType(address)
        assertIs<ContextRuleType.CallContract>(result)
        assertEquals(address, result.contractAddress)
    }

    @Test
    fun createCallContractContext_invalidAddress_throws() {
        assertFailsWith<ValidationException> {
            OZBuilders.createCallContractContextType("GABC...")
        }
    }

    @Test
    fun createCallContractContext_emptyAddress_throws() {
        assertFailsWith<ValidationException> {
            OZBuilders.createCallContractContextType("")
        }
    }

    // MARK: - createCreateContractContext (hex)

    @Test
    fun createCreateContractContext_validHex() {
        val hex = "a".repeat(64)
        val result = OZBuilders.createCreateContractContextType(hex)
        assertIs<ContextRuleType.CreateContract>(result)
        assertEquals(32, result.wasmHash.size)
    }

    @Test
    fun createCreateContractContext_validHexWith0xPrefix() {
        val hex = "0x" + "b".repeat(64)
        val result = OZBuilders.createCreateContractContextType(hex)
        assertIs<ContextRuleType.CreateContract>(result)
        assertEquals(32, result.wasmHash.size)
    }

    @Test
    fun createCreateContractContext_shortHex_throws() {
        assertFailsWith<ValidationException> {
            OZBuilders.createCreateContractContextType("abc123")
        }
    }

    @Test
    fun createCreateContractContext_longHex_throws() {
        assertFailsWith<ValidationException> {
            OZBuilders.createCreateContractContextType("a".repeat(66))
        }
    }

    // MARK: - createCreateContractContext (bytes)

    @Test
    fun createCreateContractContext_validBytes() {
        val bytes = ByteArray(32) { it.toByte() }
        val result = OZBuilders.createCreateContractContextType(bytes)
        assertIs<ContextRuleType.CreateContract>(result)
        assertTrue(bytes.contentEquals(result.wasmHash))
    }

    @Test
    fun createCreateContractContext_wrongSizeBytes_throws() {
        assertFailsWith<ValidationException> {
            OZBuilders.createCreateContractContextType(ByteArray(16))
        }
    }

    // MARK: - collectUniqueSignersFromRules

    @Test
    fun collectUniqueSignersFromRules_emptyRules() {
        val result = OZBuilders.collectUniqueSignersFromRules(emptyList())
        assertTrue(result.isEmpty())
    }

    // MARK: - Deprecated context-type builder aliases

    @Suppress("DEPRECATION")
    @Test
    fun deprecatedContextBuilders_delegateToRenamedBuilders() {
        assertEquals(OZBuilders.createDefaultContextType(), OZBuilders.createDefaultContext())

        val address = "CAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAD2KM"
        assertEquals(
            OZBuilders.createCallContractContextType(address),
            OZBuilders.createCallContractContext(address)
        )

        val hex = "a".repeat(64)
        val fromHexNew = OZBuilders.createCreateContractContextType(hex)
        val fromHexOld = OZBuilders.createCreateContractContext(hex)
        assertIs<ContextRuleType.CreateContract>(fromHexNew)
        assertIs<ContextRuleType.CreateContract>(fromHexOld)
        assertTrue(fromHexNew.wasmHash.contentEquals(fromHexOld.wasmHash))

        val bytes = ByteArray(32) { it.toByte() }
        val fromBytesNew = OZBuilders.createCreateContractContextType(bytes)
        val fromBytesOld = OZBuilders.createCreateContractContext(bytes)
        assertIs<ContextRuleType.CreateContract>(fromBytesNew)
        assertIs<ContextRuleType.CreateContract>(fromBytesOld)
        assertTrue(fromBytesNew.wasmHash.contentEquals(fromBytesOld.wasmHash))
    }
}
