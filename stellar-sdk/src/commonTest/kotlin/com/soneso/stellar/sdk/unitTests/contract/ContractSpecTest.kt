package com.soneso.stellar.sdk.unitTests.contract

import com.soneso.stellar.sdk.contract.*
import com.ionspin.kotlin.bignum.integer.BigInteger
import com.soneso.stellar.sdk.Address
import com.soneso.stellar.sdk.contract.exception.ContractSpecException
import com.soneso.stellar.sdk.xdr.*
import kotlin.test.*

class ContractSpecTest {

    // ========== Introspection Tests ==========

    @Test
    fun testFuncsReturnsAllFunctions() {
        val entries = listOf(
            createFunctionEntry("hello", listOf("to")),
            createFunctionEntry("swap", listOf("a", "b")),
            createStructEntry("MyStruct", listOf("field1" to SCSpecTypeXdr.SC_SPEC_TYPE_SYMBOL, "field2" to SCSpecTypeXdr.SC_SPEC_TYPE_SYMBOL))
        )
        val spec = ContractSpec(entries)

        val functions = spec.funcs()
        assertEquals(2, functions.size)
        assertEquals("hello", functions[0].name.value)
        assertEquals("swap", functions[1].name.value)
    }

    @Test
    fun testGetFuncFindsFunction() {
        val entries = listOf(
            createFunctionEntry("hello", listOf("to")),
            createFunctionEntry("swap", listOf("a", "b"))
        )
        val spec = ContractSpec(entries)

        val helloFunc = spec.getFunc("hello")
        assertNotNull(helloFunc)
        assertEquals("hello", helloFunc.name.value)
        assertEquals(1, helloFunc.inputs.size)

        val swapFunc = spec.getFunc("swap")
        assertNotNull(swapFunc)
        assertEquals("swap", swapFunc.name.value)
        assertEquals(2, swapFunc.inputs.size)

        val notFound = spec.getFunc("notFound")
        assertNull(notFound)
    }

    @Test
    fun testFindEntryFindsAllTypes() {
        val entries = listOf(
            createFunctionEntry("hello", listOf("to")),
            createStructEntry("MyStruct", listOf("field1" to SCSpecTypeXdr.SC_SPEC_TYPE_SYMBOL)),
            createEnumEntry("MyEnum", listOf("Case1", "Case2"))
        )
        val spec = ContractSpec(entries)

        val funcEntry = spec.findEntry("hello")
        assertNotNull(funcEntry)
        assertTrue(funcEntry is SCSpecEntryXdr.FunctionV0)

        val structEntry = spec.findEntry("MyStruct")
        assertNotNull(structEntry)
        assertTrue(structEntry is SCSpecEntryXdr.UdtStructV0)

        val enumEntry = spec.findEntry("MyEnum")
        assertNotNull(enumEntry)
        assertTrue(enumEntry is SCSpecEntryXdr.UdtEnumV0)

        val notFound = spec.findEntry("NotFound")
        assertNull(notFound)
    }

    // ========== funcArgsToXdrSCValues Tests ==========

    @Test
    fun testFuncArgsToXdrSCValuesBasicTypes() {
        val entries = listOf(
            createFunctionEntryWithTypes("test", listOf(
                "name" to SCSpecTypeXdr.SC_SPEC_TYPE_SYMBOL,
                "age" to SCSpecTypeXdr.SC_SPEC_TYPE_U32,
                "active" to SCSpecTypeXdr.SC_SPEC_TYPE_BOOL
            ))
        )
        val spec = ContractSpec(entries)

        val args = spec.funcArgsToXdrSCValues("test", mapOf(
            "name" to "Alice",
            "age" to 30,
            "active" to true
        ))

        assertEquals(3, args.size)
        assertTrue(args[0] is SCValXdr.Sym)
        assertTrue(args[1] is SCValXdr.U32)
        assertTrue(args[2] is SCValXdr.B)
    }

    @Test
    fun testFuncArgsToXdrSCValuesThrowsWhenFunctionNotFound() {
        val spec = ContractSpec(emptyList())

        val exception = assertFailsWith<ContractSpecException> {
            spec.funcArgsToXdrSCValues("notFound", emptyMap())
        }
        assertTrue(exception.message!!.contains("Function not found"))
        assertEquals("notFound", exception.functionName)
    }

    @Test
    fun testFuncArgsToXdrSCValuesThrowsWhenArgumentMissing() {
        val entries = listOf(
            createFunctionEntry("test", listOf("arg1", "arg2"))
        )
        val spec = ContractSpec(entries)

        val exception = assertFailsWith<ContractSpecException> {
            spec.funcArgsToXdrSCValues("test", mapOf("arg1" to "value"))
        }
        assertTrue(exception.message!!.contains("Required argument not found"))
        assertEquals("arg2", exception.argumentName)
        assertEquals("test", exception.functionName)
    }

    // ========== Integration-Style Tests (Demonstrating Real-World Usage) ==========

    @Test
    fun testHelloContractWithContractSpec() {
        // Simulates the Flutter SDK test "test hello contract with ContractSpec"
        // This demonstrates the key value of ContractSpec: simplifying argument conversion

        val entries = listOf(
            createFunctionEntry("hello", listOf("to"), SCSpecTypeXdr.SC_SPEC_TYPE_SYMBOL)
        )
        val spec = ContractSpec(entries)

        // Demonstrate ContractSpec capabilities
        val functions = spec.funcs()
        assertEquals(1, functions.size)
        assertEquals("hello", functions[0].name.value)

        val helloFunc = spec.getFunc("hello")
        assertNotNull(helloFunc)
        assertEquals("hello", helloFunc.name.value)
        assertEquals(1, helloFunc.inputs.size)

        // Key improvement: Convert arguments using ContractSpec
        // Instead of manually creating: SCValXdr.Sym(SCSymbolXdr("Maria"))
        // We can use: mapOf("to" to "Maria")
        val args = spec.funcArgsToXdrSCValues("hello", mapOf("to" to "Maria"))

        assertEquals(1, args.size)
        assertTrue(args[0] is SCValXdr.Sym)
        assertEquals("Maria", (args[0] as SCValXdr.Sym).value.value)
    }

    @Test
    fun testAuthContractWithContractSpec() {
        // Simulates the Flutter SDK test "test auth with ContractSpec"
        // Demonstrates automatic type conversion for Address and u32

        val entries = listOf(
            createAuthIncrementFunction()
        )
        val spec = ContractSpec(entries)

        val accountId = "GBRPYHIL2CI3FNQ4BXLFMNDLFJUNPU2HY3ZMFSHONUCEOASW7QC7OX2H"

        // Manual approach (what developers had to do before ContractSpec)
        val manualAddress = Address(accountId)
        val manualArgs = listOf(
            manualAddress.toSCVal(),
            SCValXdr.U32(Uint32Xdr(5u))
        )
        assertEquals(2, manualArgs.size)
        assertTrue(manualArgs[0] is SCValXdr.Address)
        assertTrue(manualArgs[1] is SCValXdr.U32)
        assertEquals(5u, (manualArgs[1] as SCValXdr.U32).value.value)

        // ContractSpec approach (new, much simpler!)
        val specArgs = spec.funcArgsToXdrSCValues("increment", mapOf(
            "user" to accountId,  // String account ID -> automatically converts to Address
            "value" to 7          // Int -> automatically converts to u32
        ))

        assertEquals(2, specArgs.size)
        assertTrue(specArgs[0] is SCValXdr.Address)
        assertTrue(specArgs[1] is SCValXdr.U32)
        assertEquals(7u, (specArgs[1] as SCValXdr.U32).value.value)

        // Verify the address was converted correctly
        val convertedAddress = Address.fromSCVal(specArgs[0])
        assertEquals(accountId, convertedAddress.toString())
    }

    @Test
    fun testAtomicSwapWithContractSpec() {
        // Simulates the Flutter SDK test "test atomic swap with ContractSpec"
        // Demonstrates complex multi-parameter function with automatic type conversion

        val entries = listOf(
            createAtomicSwapFunction()
        )
        val spec = ContractSpec(entries)

        val aliceId = "GBRPYHIL2CI3FNQ4BXLFMNDLFJUNPU2HY3ZMFSHONUCEOASW7QC7OX2H"
        val bobId = "GBVFLWXYCIGPO3455XVFIKHS66FCT5AI64ZARKS7QJN4NF7K5FOXTJNL"
        val tokenAContractId = "CA3D5KRYM6CB7OWQ6TWYRR3Z4T7GNZLKERYNZGGA5SOAOPIFY6YQGAXE"
        val tokenBContractId = "CA7QYNF7SOWQ3GLR2BGMZEHXAVIRZA4KVWLTJJFC7MGXUA74P7UJUWDA"

        // Manual approach (original, verbose)
        val manualArgs = listOf(
            Address(aliceId).toSCVal(),
            Address(bobId).toSCVal(),
            Address(tokenAContractId).toSCVal(),
            Address(tokenBContractId).toSCVal(),
            SCValXdr.I128(Int128PartsXdr(Int64Xdr(0L), Uint64Xdr(1000UL))),
            SCValXdr.I128(Int128PartsXdr(Int64Xdr(0L), Uint64Xdr(4500UL))),
            SCValXdr.I128(Int128PartsXdr(Int64Xdr(0L), Uint64Xdr(5000UL))),
            SCValXdr.I128(Int128PartsXdr(Int64Xdr(0L), Uint64Xdr(950UL)))
        )
        assertEquals(8, manualArgs.size)

        // ContractSpec approach (new, MUCH cleaner!)
        val specArgs = spec.funcArgsToXdrSCValues("swap", mapOf(
            "a" to aliceId,                    // String -> Address (automatic)
            "b" to bobId,                      // String -> Address (automatic)
            "token_a" to tokenAContractId,     // String -> Address (automatic)
            "token_b" to tokenBContractId,     // String -> Address (automatic)
            "amount_a" to 1000,                // Int -> i128 (automatic)
            "min_b_for_a" to 4500,            // Int -> i128 (automatic)
            "amount_b" to 5000,                // Int -> i128 (automatic)
            "min_a_for_b" to 950               // Int -> i128 (automatic)
        ))

        assertEquals(8, specArgs.size)

        // Verify all 4 addresses were converted correctly
        assertTrue(specArgs[0] is SCValXdr.Address)
        assertTrue(specArgs[1] is SCValXdr.Address)
        assertTrue(specArgs[2] is SCValXdr.Address)
        assertTrue(specArgs[3] is SCValXdr.Address)

        val address1 = Address.fromSCVal(specArgs[0])
        val address2 = Address.fromSCVal(specArgs[1])
        val address3 = Address.fromSCVal(specArgs[2])
        val address4 = Address.fromSCVal(specArgs[3])

        assertEquals(aliceId, address1.toString())
        assertEquals(bobId, address2.toString())
        assertEquals(tokenAContractId, address3.toString())
        assertEquals(tokenBContractId, address4.toString())

        // Verify all 4 i128 values were converted correctly
        assertTrue(specArgs[4] is SCValXdr.I128)
        assertTrue(specArgs[5] is SCValXdr.I128)
        assertTrue(specArgs[6] is SCValXdr.I128)
        assertTrue(specArgs[7] is SCValXdr.I128)

        assertEquals(1000UL, (specArgs[4] as SCValXdr.I128).value.lo.value)
        assertEquals(4500UL, (specArgs[5] as SCValXdr.I128).value.lo.value)
        assertEquals(5000UL, (specArgs[6] as SCValXdr.I128).value.lo.value)
        assertEquals(950UL, (specArgs[7] as SCValXdr.I128).value.lo.value)
    }

    @Test
    fun testTokenOperationsWithContractSpec() {
        // Demonstrates token operations using ContractSpec (initialize, mint, balance)
        // Similar to Flutter SDK's createTokenWithSpec, mintWithSpec, readBalanceWithSpec

        val entries = listOf(
            createTokenInitializeFunction(),
            createTokenMintFunction(),
            createTokenBalanceFunction()
        )
        val spec = ContractSpec(entries)

        val adminId = "GBRPYHIL2CI3FNQ4BXLFMNDLFJUNPU2HY3ZMFSHONUCEOASW7QC7OX2H"
        val recipientId = "GBVFLWXYCIGPO3455XVFIKHS66FCT5AI64ZARKS7QJN4NF7K5FOXTJNL"

        // Test initialize function
        val initArgs = spec.funcArgsToXdrSCValues("initialize", mapOf(
            "admin" to adminId,      // String -> Address (automatic)
            "decimal" to 7,          // Int -> u32 (automatic)
            "name" to "MyToken",     // String -> String (direct)
            "symbol" to "MTK"        // String -> String (direct)
        ))

        assertEquals(4, initArgs.size)
        assertTrue(initArgs[0] is SCValXdr.Address)
        assertTrue(initArgs[1] is SCValXdr.U32)
        assertTrue(initArgs[2] is SCValXdr.Str)
        assertTrue(initArgs[3] is SCValXdr.Str)
        assertEquals(7u, (initArgs[1] as SCValXdr.U32).value.value)
        assertEquals("MyToken", (initArgs[2] as SCValXdr.Str).value.value)
        assertEquals("MTK", (initArgs[3] as SCValXdr.Str).value.value)

        // Test mint function
        val mintArgs = spec.funcArgsToXdrSCValues("mint", mapOf(
            "to" to recipientId,     // String -> Address (automatic)
            "amount" to 1000000      // Int -> i128 (automatic)
        ))

        assertEquals(2, mintArgs.size)
        assertTrue(mintArgs[0] is SCValXdr.Address)
        assertTrue(mintArgs[1] is SCValXdr.I128)
        assertEquals(1000000UL, (mintArgs[1] as SCValXdr.I128).value.lo.value)

        // Test balance function
        val balanceArgs = spec.funcArgsToXdrSCValues("balance", mapOf(
            "id" to recipientId      // String -> Address (automatic)
        ))

        assertEquals(1, balanceArgs.size)
        assertTrue(balanceArgs[0] is SCValXdr.Address)
        val balanceAddress = Address.fromSCVal(balanceArgs[0])
        assertEquals(recipientId, balanceAddress.toString())
    }

    @Test
    fun testMixedComplexTypes() {
        // Test a function with a mix of complex types to ensure comprehensive coverage
        val entries = listOf(
            createComplexMixedFunction()
        )
        val spec = ContractSpec(entries)

        val accountId = "GBRPYHIL2CI3FNQ4BXLFMNDLFJUNPU2HY3ZMFSHONUCEOASW7QC7OX2H"
        val contractId = "CA3D5KRYM6CB7OWQ6TWYRR3Z4T7GNZLKERYNZGGA5SOAOPIFY6YQGAXE"

        val args = spec.funcArgsToXdrSCValues("complex_function", mapOf(
            "address" to accountId,
            "amount" to 5000,
            "metadata" to mapOf("key1" to "value1", "key2" to "value2"),
            "tags" to listOf("tag1", "tag2", "tag3"),
            "contract_id" to contractId,
            "timestamp" to 1234567890L,
            "enabled" to true
        ))

        assertEquals(7, args.size)
        assertTrue(args[0] is SCValXdr.Address) // address
        assertTrue(args[1] is SCValXdr.I128)    // amount
        assertTrue(args[2] is SCValXdr.Map)     // metadata
        assertTrue(args[3] is SCValXdr.Vec)     // tags
        assertTrue(args[4] is SCValXdr.Address) // contract_id
        assertTrue(args[5] is SCValXdr.Timepoint) // timestamp
        assertTrue(args[6] is SCValXdr.B)       // enabled
    }

    // ========== Primitive Type Tests ==========

    @Test
    fun testBooleanConversion() {
        val spec = ContractSpec(emptyList())

        val trueVal = spec.nativeToXdrSCVal(true, createTypeDef(SCSpecTypeXdr.SC_SPEC_TYPE_BOOL))
        assertTrue(trueVal is SCValXdr.B)
        assertEquals(true, trueVal.value)

        val falseVal = spec.nativeToXdrSCVal(false, createTypeDef(SCSpecTypeXdr.SC_SPEC_TYPE_BOOL))
        assertTrue(falseVal is SCValXdr.B)
        assertEquals(false, falseVal.value)

        assertFailsWith<ContractSpecException> {
            spec.nativeToXdrSCVal("not a boolean", createTypeDef(SCSpecTypeXdr.SC_SPEC_TYPE_BOOL))
        }
    }

    @Test
    fun testVoidConversion() {
        val spec = ContractSpec(emptyList())

        val voidVal = spec.nativeToXdrSCVal(null, createTypeDef(SCSpecTypeXdr.SC_SPEC_TYPE_VOID))
        assertTrue(voidVal is SCValXdr.Void)
    }

    @Test
    fun testU32Conversion() {
        val spec = ContractSpec(emptyList())
        val typeDef = createTypeDef(SCSpecTypeXdr.SC_SPEC_TYPE_U32)

        val result = spec.nativeToXdrSCVal(42, typeDef)
        assertTrue(result is SCValXdr.U32)
        assertEquals(42u, result.value.value)

        // Test range validation
        assertFailsWith<ContractSpecException> {
            spec.nativeToXdrSCVal(-1, typeDef)
        }

        assertFailsWith<ContractSpecException> {
            spec.nativeToXdrSCVal(0x100000000L, typeDef)
        }
    }

    @Test
    fun testI32Conversion() {
        val spec = ContractSpec(emptyList())
        val typeDef = createTypeDef(SCSpecTypeXdr.SC_SPEC_TYPE_I32)

        val positive = spec.nativeToXdrSCVal(42, typeDef)
        assertTrue(positive is SCValXdr.I32)
        assertEquals(42, positive.value.value)

        val negative = spec.nativeToXdrSCVal(-42, typeDef)
        assertTrue(negative is SCValXdr.I32)
        assertEquals(-42, negative.value.value)
    }

    @Test
    fun testU64Conversion() {
        val spec = ContractSpec(emptyList())
        val typeDef = createTypeDef(SCSpecTypeXdr.SC_SPEC_TYPE_U64)

        val result = spec.nativeToXdrSCVal(1234567890L, typeDef)
        assertTrue(result is SCValXdr.U64)
        assertEquals(1234567890UL, result.value.value)

        assertFailsWith<ContractSpecException> {
            spec.nativeToXdrSCVal(-1, typeDef)
        }
    }

    @Test
    fun testI64Conversion() {
        val spec = ContractSpec(emptyList())
        val typeDef = createTypeDef(SCSpecTypeXdr.SC_SPEC_TYPE_I64)

        val positive = spec.nativeToXdrSCVal(1234567890L, typeDef)
        assertTrue(positive is SCValXdr.I64)
        assertEquals(1234567890L, positive.value.value)

        val negative = spec.nativeToXdrSCVal(-1234567890L, typeDef)
        assertTrue(negative is SCValXdr.I64)
        assertEquals(-1234567890L, negative.value.value)
    }

    @Test
    fun testU128Conversion() {
        val spec = ContractSpec(emptyList())
        val typeDef = createTypeDef(SCSpecTypeXdr.SC_SPEC_TYPE_U128)

        val result = spec.nativeToXdrSCVal(1000, typeDef)
        assertTrue(result is SCValXdr.U128)
        val parts = result.value
        assertEquals(0UL, parts.hi.value)
        assertEquals(1000UL, parts.lo.value)

        assertFailsWith<ContractSpecException> {
            spec.nativeToXdrSCVal(-1, typeDef)
        }
    }

    @Test
    fun testI128Conversion() {
        val spec = ContractSpec(emptyList())
        val typeDef = createTypeDef(SCSpecTypeXdr.SC_SPEC_TYPE_I128)

        val positive = spec.nativeToXdrSCVal(1000, typeDef)
        assertTrue(positive is SCValXdr.I128)
        var parts = positive.value
        assertEquals(0L, parts.hi.value)
        assertEquals(1000UL, parts.lo.value)

        val negative = spec.nativeToXdrSCVal(-1000, typeDef)
        assertTrue(negative is SCValXdr.I128)
        parts = negative.value
        assertEquals(-1L, parts.hi.value) // Sign extension
    }

    @Test
    fun testU256Conversion() {
        val spec = ContractSpec(emptyList())
        val typeDef = createTypeDef(SCSpecTypeXdr.SC_SPEC_TYPE_U256)

        val result = spec.nativeToXdrSCVal(1000, typeDef)
        assertTrue(result is SCValXdr.U256)
        val parts = result.value
        assertEquals(0UL, parts.hiHi.value)
        assertEquals(0UL, parts.hiLo.value)
        assertEquals(0UL, parts.loHi.value)
        assertEquals(1000UL, parts.loLo.value)

        assertFailsWith<ContractSpecException> {
            spec.nativeToXdrSCVal(-1, typeDef)
        }
    }

    @Test
    fun testI256Conversion() {
        val spec = ContractSpec(emptyList())
        val typeDef = createTypeDef(SCSpecTypeXdr.SC_SPEC_TYPE_I256)

        val positive = spec.nativeToXdrSCVal(1000, typeDef)
        assertTrue(positive is SCValXdr.I256)
        var parts = positive.value
        assertEquals(0L, parts.hiHi.value)
        assertEquals(1000UL, parts.loLo.value)

        val negative = spec.nativeToXdrSCVal(-1000, typeDef)
        assertTrue(negative is SCValXdr.I256)
        parts = negative.value
        assertEquals(-1L, parts.hiHi.value) // Sign extension
        assertEquals(-1L, parts.hiLo.value.toLong())
        assertEquals(-1L, parts.loHi.value.toLong())
    }

    @Test
    fun testStringConversion() {
        val spec = ContractSpec(emptyList())
        val typeDef = createTypeDef(SCSpecTypeXdr.SC_SPEC_TYPE_STRING)

        val result = spec.nativeToXdrSCVal("Hello, World!", typeDef)
        assertTrue(result is SCValXdr.Str)
        assertEquals("Hello, World!", result.value.value)

        assertFailsWith<ContractSpecException> {
            spec.nativeToXdrSCVal(123, typeDef)
        }
    }

    @Test
    fun testSymbolConversion() {
        val spec = ContractSpec(emptyList())
        val typeDef = createTypeDef(SCSpecTypeXdr.SC_SPEC_TYPE_SYMBOL)

        val result = spec.nativeToXdrSCVal("my_symbol", typeDef)
        assertTrue(result is SCValXdr.Sym)
        assertEquals("my_symbol", result.value.value)

        assertFailsWith<ContractSpecException> {
            spec.nativeToXdrSCVal(123, typeDef)
        }
    }

    @Test
    fun testBytesConversion() {
        val spec = ContractSpec(emptyList())
        val typeDef = createTypeDef(SCSpecTypeXdr.SC_SPEC_TYPE_BYTES)

        // From ByteArray
        val bytes = byteArrayOf(1, 2, 3, 4, 5)
        val result1 = spec.nativeToXdrSCVal(bytes, typeDef)
        assertTrue(result1 is SCValXdr.Bytes)
        assertContentEquals(bytes, result1.value.value)

        // From hex string
        val result2 = spec.nativeToXdrSCVal("0x0102030405", typeDef)
        assertTrue(result2 is SCValXdr.Bytes)
        assertContentEquals(bytes, result2.value.value)

        // From List<Byte>
        val byteList = listOf<Byte>(1, 2, 3, 4, 5)
        val result3 = spec.nativeToXdrSCVal(byteList, typeDef)
        assertTrue(result3 is SCValXdr.Bytes)
        assertContentEquals(bytes, result3.value.value)
    }

    @Test
    fun testTimepointConversion() {
        val spec = ContractSpec(emptyList())
        val typeDef = createTypeDef(SCSpecTypeXdr.SC_SPEC_TYPE_TIMEPOINT)

        val result = spec.nativeToXdrSCVal(1234567890L, typeDef)
        assertTrue(result is SCValXdr.Timepoint)
        assertEquals(1234567890UL, result.value.value.value)

        assertFailsWith<ContractSpecException> {
            spec.nativeToXdrSCVal(-1, typeDef)
        }
    }

    @Test
    fun testDurationConversion() {
        val spec = ContractSpec(emptyList())
        val typeDef = createTypeDef(SCSpecTypeXdr.SC_SPEC_TYPE_DURATION)

        val result = spec.nativeToXdrSCVal(3600L, typeDef)
        assertTrue(result is SCValXdr.Duration)
        assertEquals(3600UL, result.value.value.value)

        assertFailsWith<ContractSpecException> {
            spec.nativeToXdrSCVal(-1, typeDef)
        }
    }

    // ========== Address Auto-Detection Tests ==========

    @Test
    fun testAddressAutoDetectionAccountAddress() {
        val spec = ContractSpec(emptyList())
        val typeDef = createTypeDef(SCSpecTypeXdr.SC_SPEC_TYPE_ADDRESS)

        val accountId = "GBRPYHIL2CI3FNQ4BXLFMNDLFJUNPU2HY3ZMFSHONUCEOASW7QC7OX2H"
        val result = spec.nativeToXdrSCVal(accountId, typeDef)

        assertTrue(result is SCValXdr.Address)
        val address = Address.fromSCVal(result)
        assertEquals(accountId, address.toString())
    }

    @Test
    fun testAddressAutoDetectionContractAddress() {
        val spec = ContractSpec(emptyList())
        val typeDef = createTypeDef(SCSpecTypeXdr.SC_SPEC_TYPE_ADDRESS)

        // Create a contract address (starts with C)
        val contractBytes = ByteArray(32) { it.toByte() }
        val contractId = Address.fromContract(contractBytes).toString()

        val result = spec.nativeToXdrSCVal(contractId, typeDef)

        assertTrue(result is SCValXdr.Address)
        val address = Address.fromSCVal(result)
        assertEquals(contractId, address.toString())
    }

    @Test
    fun testAddressAutoDetectionInvalidAddress() {
        val spec = ContractSpec(emptyList())
        val typeDef = createTypeDef(SCSpecTypeXdr.SC_SPEC_TYPE_ADDRESS)

        assertFailsWith<ContractSpecException> {
            spec.nativeToXdrSCVal("INVALID", typeDef)
        }

        assertFailsWith<ContractSpecException> {
            spec.nativeToXdrSCVal(123, typeDef)
        }
    }

    // ========== Complex Type Tests ==========

    @Test
    fun testOptionTypeWithValue() {
        val spec = ContractSpec(emptyList())
        val optionTypeDef = SCSpecTypeDefXdr.Option(
            SCSpecTypeOptionXdr(createTypeDef(SCSpecTypeXdr.SC_SPEC_TYPE_U32))
        )

        val result = spec.nativeToXdrSCVal(42, optionTypeDef)
        assertTrue(result is SCValXdr.U32)
        assertEquals(42u, result.value.value)
    }

    @Test
    fun testOptionTypeWithNull() {
        val spec = ContractSpec(emptyList())
        val optionTypeDef = SCSpecTypeDefXdr.Option(
            SCSpecTypeOptionXdr(createTypeDef(SCSpecTypeXdr.SC_SPEC_TYPE_U32))
        )

        val result = spec.nativeToXdrSCVal(null, optionTypeDef)
        assertTrue(result is SCValXdr.Void)
    }

    @Test
    fun testVecType() {
        val spec = ContractSpec(emptyList())
        val vecTypeDef = SCSpecTypeDefXdr.Vec(
            SCSpecTypeVecXdr(createTypeDef(SCSpecTypeXdr.SC_SPEC_TYPE_U32))
        )

        val list = listOf(1, 2, 3, 4, 5)
        val result = spec.nativeToXdrSCVal(list, vecTypeDef)

        assertTrue(result is SCValXdr.Vec)
        val vec = result.value
        assertNotNull(vec)
        assertEquals(5, vec.value.size)
        assertTrue(vec.value.all { it is SCValXdr.U32 })
    }

    @Test
    fun testMapType() {
        val spec = ContractSpec(emptyList())
        val mapTypeDef = SCSpecTypeDefXdr.Map(
            SCSpecTypeMapXdr(
                createTypeDef(SCSpecTypeXdr.SC_SPEC_TYPE_SYMBOL),
                createTypeDef(SCSpecTypeXdr.SC_SPEC_TYPE_U32)
            )
        )

        val map = mapOf("key1" to 100, "key2" to 200)
        val result = spec.nativeToXdrSCVal(map, mapTypeDef)

        assertTrue(result is SCValXdr.Map)
        val scMap = result.value
        assertNotNull(scMap)
        assertEquals(2, scMap.value.size)
    }

    @Test
    fun testTupleType() {
        val spec = ContractSpec(emptyList())
        val tupleTypeDef = SCSpecTypeDefXdr.Tuple(
            SCSpecTypeTupleXdr(
                listOf(
                    createTypeDef(SCSpecTypeXdr.SC_SPEC_TYPE_U32),
                    createTypeDef(SCSpecTypeXdr.SC_SPEC_TYPE_STRING)
                )
            )
        )

        val tuple = listOf(42, "hello")
        val result = spec.nativeToXdrSCVal(tuple, tupleTypeDef)

        assertTrue(result is SCValXdr.Vec)
        val vec = result.value
        assertNotNull(vec)
        assertEquals(2, vec.value.size)
        assertTrue(vec.value[0] is SCValXdr.U32)
        assertTrue(vec.value[1] is SCValXdr.Str)
    }

    @Test
    fun testTupleTypeLengthMismatch() {
        val spec = ContractSpec(emptyList())
        val tupleTypeDef = SCSpecTypeDefXdr.Tuple(
            SCSpecTypeTupleXdr(
                listOf(
                    createTypeDef(SCSpecTypeXdr.SC_SPEC_TYPE_U32),
                    createTypeDef(SCSpecTypeXdr.SC_SPEC_TYPE_STRING)
                )
            )
        )

        assertFailsWith<ContractSpecException> {
            spec.nativeToXdrSCVal(listOf(42), tupleTypeDef)
        }
    }

    @Test
    fun testBytesNType() {
        val spec = ContractSpec(emptyList())
        val bytesNTypeDef = SCSpecTypeDefXdr.BytesN(
            SCSpecTypeBytesNXdr(Uint32Xdr(32u))
        )

        val bytes = ByteArray(32) { it.toByte() }
        val result = spec.nativeToXdrSCVal(bytes, bytesNTypeDef)

        assertTrue(result is SCValXdr.Bytes)
        assertEquals(32, result.value.value.size)
    }

    @Test
    fun testBytesNTypeLengthMismatch() {
        val spec = ContractSpec(emptyList())
        val bytesNTypeDef = SCSpecTypeDefXdr.BytesN(
            SCSpecTypeBytesNXdr(Uint32Xdr(32u))
        )

        assertFailsWith<ContractSpecException> {
            spec.nativeToXdrSCVal(ByteArray(16), bytesNTypeDef)
        }
    }

    // ========== UDT Tests (Enum, Struct, Union) ==========

    @Test
    fun testEnumConversionByName() {
        val entries = listOf(
            createEnumEntry("Status", listOf("Pending", "Active", "Completed"))
        )
        val spec = ContractSpec(entries)
        val enumTypeDef = createUdtTypeDef("Status")

        val result = spec.nativeToXdrSCVal("Active", enumTypeDef)
        assertTrue(result is SCValXdr.U32)
        assertEquals(1u, result.value.value)
    }

    @Test
    fun testEnumConversionByValue() {
        val entries = listOf(
            createEnumEntry("Status", listOf("Pending", "Active", "Completed"))
        )
        val spec = ContractSpec(entries)
        val enumTypeDef = createUdtTypeDef("Status")

        val result = spec.nativeToXdrSCVal(2, enumTypeDef)
        assertTrue(result is SCValXdr.U32)
        assertEquals(2u, result.value.value)
    }

    @Test
    fun testEnumConversionInvalidCase() {
        val entries = listOf(
            createEnumEntry("Status", listOf("Pending", "Active"))
        )
        val spec = ContractSpec(entries)
        val enumTypeDef = createUdtTypeDef("Status")

        assertFailsWith<ContractSpecException> {
            spec.nativeToXdrSCVal("InvalidCase", enumTypeDef)
        }
    }

    @Test
    fun testStructConversionAsMap() {
        val entries = listOf(
            createStructEntry("Person", listOf(
                "name" to SCSpecTypeXdr.SC_SPEC_TYPE_SYMBOL,
                "age" to SCSpecTypeXdr.SC_SPEC_TYPE_U32
            ))
        )
        val spec = ContractSpec(entries)
        val structTypeDef = createUdtTypeDef("Person")

        val person = mapOf("name" to "Alice", "age" to 30)
        val result = spec.nativeToXdrSCVal(person, structTypeDef)

        assertTrue(result is SCValXdr.Map)
        val map = result.value
        assertNotNull(map)
        assertEquals(2, map.value.size)
    }

    @Test
    fun testStructConversionMissingField() {
        val entries = listOf(
            createStructEntry("Person", listOf(
                "name" to SCSpecTypeXdr.SC_SPEC_TYPE_SYMBOL,
                "age" to SCSpecTypeXdr.SC_SPEC_TYPE_SYMBOL
            ))
        )
        val spec = ContractSpec(entries)
        val structTypeDef = createUdtTypeDef("Person")

        assertFailsWith<ContractSpecException> {
            spec.nativeToXdrSCVal(mapOf("name" to "Alice"), structTypeDef)
        }
    }

    @Test
    fun testUnionConversionVoidCase() {
        val entries = listOf(
            createUnionEntry("Result", listOf("Success" to 0))
        )
        val spec = ContractSpec(entries)
        val unionTypeDef = createUdtTypeDef("Result")

        val unionVal = NativeUnionVal.VoidCase("Success")
        val result = spec.nativeToXdrSCVal(unionVal, unionTypeDef)

        assertTrue(result is SCValXdr.Vec)
        val vec = result.value
        assertNotNull(vec)
        assertEquals(1, vec.value.size) // Just the tag
        assertTrue(vec.value[0] is SCValXdr.Sym)
    }

    // ========== funcResToNative Tests ==========

    @Test
    fun testFuncResToNativeVoidReturn() {
        val entries = listOf(
            createFunctionEntryWithOutputs("test_void", emptyList(), emptyList())
        )
        val spec = ContractSpec(entries)

        val voidVal = SCValXdr.Void(SCValTypeXdr.SCV_VOID)
        val result = spec.funcResToNative("test_void", voidVal)
        assertNull(result)
    }

    @Test
    fun testFuncResToNativeVoidReturnThrowsOnNonVoid() {
        val entries = listOf(
            createFunctionEntryWithOutputs("test_void", emptyList(), emptyList())
        )
        val spec = ContractSpec(entries)

        val nonVoidVal = SCValXdr.U32(Uint32Xdr(42u))
        assertFailsWith<ContractSpecException> {
            spec.funcResToNative("test_void", nonVoidVal)
        }
    }

    @Test
    fun testFuncResToNativeSingleOutput() {
        val entries = listOf(
            createFunctionEntryWithOutputs(
                "get_balance",
                emptyList(),
                listOf(SCSpecTypeXdr.SC_SPEC_TYPE_I128)
            )
        )
        val spec = ContractSpec(entries)

        val balanceVal = SCValXdr.I128(Int128PartsXdr(Int64Xdr(0L), Uint64Xdr(1000000UL)))
        val result = spec.funcResToNative("get_balance", balanceVal)

        assertNotNull(result)
        assertTrue(result is BigInteger)
        assertEquals(BigInteger.fromLong(1000000L), result)
    }

    @Test
    fun testFuncResToNativeMultipleOutputsNotSupported() {
        val entries = listOf(
            createFunctionEntryWithOutputs(
                "multi_return",
                emptyList(),
                listOf(SCSpecTypeXdr.SC_SPEC_TYPE_U32, SCSpecTypeXdr.SC_SPEC_TYPE_U32)
            )
        )
        val spec = ContractSpec(entries)

        val result = SCValXdr.U32(Uint32Xdr(42u))
        val exception = assertFailsWith<ContractSpecException> {
            spec.funcResToNative("multi_return", result)
        }
        assertTrue(exception.message!!.contains("Multiple outputs not supported"))
    }

    @Test
    fun testFuncResToNativeWithBase64() {
        val entries = listOf(
            createFunctionEntryWithOutputs(
                "get_value",
                emptyList(),
                listOf(SCSpecTypeXdr.SC_SPEC_TYPE_U32)
            )
        )
        val spec = ContractSpec(entries)

        // Create a U32 value and encode to base64
        val u32Val = SCValXdr.U32(Uint32Xdr(42u))
        val base64 = u32Val.toXdrBase64()

        val result = spec.funcResToNative("get_value", base64)
        assertNotNull(result)
        assertEquals(42u, result)
    }

    // ========== scValToNative Tests ==========

    @Test
    fun testScValToNativePrimitives() {
        val spec = ContractSpec(emptyList())

        // Bool
        val boolVal = SCValXdr.B(true)
        val boolResult = spec.scValToNative(boolVal, createTypeDef(SCSpecTypeXdr.SC_SPEC_TYPE_BOOL))
        assertEquals(true, boolResult)

        // U32
        val u32Val = SCValXdr.U32(Uint32Xdr(42u))
        val u32Result = spec.scValToNative(u32Val, createTypeDef(SCSpecTypeXdr.SC_SPEC_TYPE_U32))
        assertEquals(42u, u32Result)

        // I32
        val i32Val = SCValXdr.I32(Int32Xdr(-42))
        val i32Result = spec.scValToNative(i32Val, createTypeDef(SCSpecTypeXdr.SC_SPEC_TYPE_I32))
        assertEquals(-42, i32Result)

        // String
        val stringVal = SCValXdr.Str(SCStringXdr("hello"))
        val stringResult = spec.scValToNative(stringVal, createTypeDef(SCSpecTypeXdr.SC_SPEC_TYPE_STRING))
        assertEquals("hello", stringResult)

        // Symbol
        val symbolVal = SCValXdr.Sym(SCSymbolXdr("my_symbol"))
        val symbolResult = spec.scValToNative(symbolVal, createTypeDef(SCSpecTypeXdr.SC_SPEC_TYPE_SYMBOL))
        assertEquals("my_symbol", symbolResult)

        // Bytes
        val bytesVal = SCValXdr.Bytes(SCBytesXdr(byteArrayOf(1, 2, 3)))
        val bytesResult = spec.scValToNative(bytesVal, createTypeDef(SCSpecTypeXdr.SC_SPEC_TYPE_BYTES))
        assertTrue(bytesResult is ByteArray)
        assertContentEquals(byteArrayOf(1, 2, 3), bytesResult as ByteArray)
    }

    @Test
    fun testScValToNativeBigIntegers() {
        val spec = ContractSpec(emptyList())

        // I128
        val i128Val = SCValXdr.I128(Int128PartsXdr(Int64Xdr(0L), Uint64Xdr(1000UL)))
        val i128Result = spec.scValToNative(i128Val, createTypeDef(SCSpecTypeXdr.SC_SPEC_TYPE_I128))
        assertTrue(i128Result is BigInteger)
        assertEquals(BigInteger.fromLong(1000L), i128Result)

        // U128
        val u128Val = SCValXdr.U128(UInt128PartsXdr(Uint64Xdr(0UL), Uint64Xdr(2000UL)))
        val u128Result = spec.scValToNative(u128Val, createTypeDef(SCSpecTypeXdr.SC_SPEC_TYPE_U128))
        assertTrue(u128Result is BigInteger)
        assertEquals(BigInteger.fromLong(2000L), u128Result)
    }

    @Test
    fun testScValToNativeAddress() {
        val spec = ContractSpec(emptyList())
        val accountId = "GBRPYHIL2CI3FNQ4BXLFMNDLFJUNPU2HY3ZMFSHONUCEOASW7QC7OX2H"

        val address = Address(accountId)
        val addressVal = address.toSCVal()
        val result = spec.scValToNative(addressVal, createTypeDef(SCSpecTypeXdr.SC_SPEC_TYPE_ADDRESS))

        assertTrue(result is String)
        assertEquals(accountId, result)
    }

    @Test
    fun testScValToNativeVec() {
        val spec = ContractSpec(emptyList())
        val vecTypeDef = SCSpecTypeDefXdr.Vec(
            SCSpecTypeVecXdr(createTypeDef(SCSpecTypeXdr.SC_SPEC_TYPE_U32))
        )

        val vecVal = SCValXdr.Vec(SCVecXdr(listOf(
            SCValXdr.U32(Uint32Xdr(1u)),
            SCValXdr.U32(Uint32Xdr(2u)),
            SCValXdr.U32(Uint32Xdr(3u))
        )))

        val result = spec.scValToNative(vecVal, vecTypeDef)
        assertTrue(result is List<*>)
        val list = result as List<*>
        assertEquals(3, list.size)
        assertEquals(1u, list[0])
        assertEquals(2u, list[1])
        assertEquals(3u, list[2])
    }

    @Test
    fun testScValToNativeMap() {
        val spec = ContractSpec(emptyList())
        val mapTypeDef = SCSpecTypeDefXdr.Map(
            SCSpecTypeMapXdr(
                createTypeDef(SCSpecTypeXdr.SC_SPEC_TYPE_SYMBOL),
                createTypeDef(SCSpecTypeXdr.SC_SPEC_TYPE_U32)
            )
        )

        val mapVal = SCValXdr.Map(SCMapXdr(listOf(
            SCMapEntryXdr(
                SCValXdr.Sym(SCSymbolXdr("key1")),
                SCValXdr.U32(Uint32Xdr(100u))
            ),
            SCMapEntryXdr(
                SCValXdr.Sym(SCSymbolXdr("key2")),
                SCValXdr.U32(Uint32Xdr(200u))
            )
        )))

        val result = spec.scValToNative(mapVal, mapTypeDef)
        assertTrue(result is List<*>)
        val list = result as List<*>
        assertEquals(2, list.size)

        val pair1 = list[0] as Pair<*, *>
        assertEquals("key1", pair1.first)
        assertEquals(100u, pair1.second)

        val pair2 = list[1] as Pair<*, *>
        assertEquals("key2", pair2.first)
        assertEquals(200u, pair2.second)
    }

    @Test
    fun testScValToNativeTuple() {
        val spec = ContractSpec(emptyList())
        val tupleTypeDef = SCSpecTypeDefXdr.Tuple(
            SCSpecTypeTupleXdr(listOf(
                createTypeDef(SCSpecTypeXdr.SC_SPEC_TYPE_U32),
                createTypeDef(SCSpecTypeXdr.SC_SPEC_TYPE_STRING)
            ))
        )

        val tupleVal = SCValXdr.Vec(SCVecXdr(listOf(
            SCValXdr.U32(Uint32Xdr(42u)),
            SCValXdr.Str(SCStringXdr("hello"))
        )))

        val result = spec.scValToNative(tupleVal, tupleTypeDef)
        assertTrue(result is List<*>)
        val list = result as List<*>
        assertEquals(2, list.size)
        assertEquals(42u, list[0])
        assertEquals("hello", list[1])
    }

    @Test
    fun testScValToNativeEnum() {
        val entries = listOf(
            createEnumEntry("Status", listOf("Pending", "Active", "Completed"))
        )
        val spec = ContractSpec(entries)
        val enumTypeDef = createUdtTypeDef("Status")

        val enumVal = SCValXdr.U32(Uint32Xdr(1u)) // Active
        val result = spec.scValToNative(enumVal, enumTypeDef)

        assertTrue(result is UInt)
        assertEquals(1u, result)
    }

    @Test
    fun testScValToNativeStruct() {
        val entries = listOf(
            createStructEntry("Person", listOf(
                "name" to SCSpecTypeXdr.SC_SPEC_TYPE_SYMBOL,
                "age" to SCSpecTypeXdr.SC_SPEC_TYPE_U32
            ))
        )
        val spec = ContractSpec(entries)
        val structTypeDef = createUdtTypeDef("Person")

        val structVal = SCValXdr.Map(SCMapXdr(listOf(
            SCMapEntryXdr(
                SCValXdr.Sym(SCSymbolXdr("name")),
                SCValXdr.Sym(SCSymbolXdr("Alice"))
            ),
            SCMapEntryXdr(
                SCValXdr.Sym(SCSymbolXdr("age")),
                SCValXdr.U32(Uint32Xdr(30u))
            )
        )))

        val result = spec.scValToNative(structVal, structTypeDef)
        assertTrue(result is Map<*, *>)
        val map = result as Map<*, *>
        assertEquals("Alice", map["name"])
        assertEquals(30u, map["age"])
    }

    @Test
    fun testScValToNativeUnionVoidCase() {
        val entries = listOf(
            createUnionEntry("Result", listOf("Success" to 0))
        )
        val spec = ContractSpec(entries)
        val unionTypeDef = createUdtTypeDef("Result")

        val unionVal = SCValXdr.Vec(SCVecXdr(listOf(
            SCValXdr.Sym(SCSymbolXdr("Success"))
        )))

        val result = spec.scValToNative(unionVal, unionTypeDef)
        assertTrue(result is NativeUnionVal.VoidCase)
        assertEquals("Success", result.tag)
    }

    @Test
    fun testScValToNativeUnionTupleCase() {
        val entries = listOf(
            createUnionEntryWithTupleCase(
                "Result",
                "Error" to listOf(SCSpecTypeXdr.SC_SPEC_TYPE_U32, SCSpecTypeXdr.SC_SPEC_TYPE_STRING)
            )
        )
        val spec = ContractSpec(entries)
        val unionTypeDef = createUdtTypeDef("Result")

        val unionVal = SCValXdr.Vec(SCVecXdr(listOf(
            SCValXdr.Sym(SCSymbolXdr("Error")),
            SCValXdr.U32(Uint32Xdr(404u)),
            SCValXdr.Str(SCStringXdr("Not Found"))
        )))

        val result = spec.scValToNative(unionVal, unionTypeDef)
        assertTrue(result is NativeUnionVal.TupleCase)
        assertEquals("Error", result.tag)
        assertEquals(2, result.values.size)
        assertEquals(404u, result.values[0])
        assertEquals("Not Found", result.values[1])
    }

    // ========== Round-Trip Conversion Tests ==========

    @Test
    fun testRoundTripPrimitives() {
        val spec = ContractSpec(emptyList())

        // Boolean
        val boolTypeDef = createTypeDef(SCSpecTypeXdr.SC_SPEC_TYPE_BOOL)
        val boolScVal = spec.nativeToXdrSCVal(true, boolTypeDef)
        val boolResult = spec.scValToNative(boolScVal, boolTypeDef)
        assertEquals(true, boolResult)

        // U32
        val u32TypeDef = createTypeDef(SCSpecTypeXdr.SC_SPEC_TYPE_U32)
        val u32ScVal = spec.nativeToXdrSCVal(42, u32TypeDef)
        val u32Result = spec.scValToNative(u32ScVal, u32TypeDef)
        assertEquals(42u, u32Result)

        // String
        val stringTypeDef = createTypeDef(SCSpecTypeXdr.SC_SPEC_TYPE_STRING)
        val stringScVal = spec.nativeToXdrSCVal("hello", stringTypeDef)
        val stringResult = spec.scValToNative(stringScVal, stringTypeDef)
        assertEquals("hello", stringResult)
    }

    @Test
    fun testRoundTripVec() {
        val spec = ContractSpec(emptyList())
        val vecTypeDef = SCSpecTypeDefXdr.Vec(
            SCSpecTypeVecXdr(createTypeDef(SCSpecTypeXdr.SC_SPEC_TYPE_U32))
        )

        val original = listOf(1, 2, 3, 4, 5)
        val scVal = spec.nativeToXdrSCVal(original, vecTypeDef)
        val result = spec.scValToNative(scVal, vecTypeDef)

        assertTrue(result is List<*>)
        val list = result as List<*>
        assertEquals(5, list.size)
        assertEquals(listOf(1u, 2u, 3u, 4u, 5u), list)
    }

    @Test
    fun testRoundTripStruct() {
        val entries = listOf(
            createStructEntry("Person", listOf(
                "name" to SCSpecTypeXdr.SC_SPEC_TYPE_SYMBOL,
                "age" to SCSpecTypeXdr.SC_SPEC_TYPE_U32
            ))
        )
        val spec = ContractSpec(entries)
        val structTypeDef = createUdtTypeDef("Person")

        val original = mapOf("name" to "Alice", "age" to 30)
        val scVal = spec.nativeToXdrSCVal(original, structTypeDef)
        val result = spec.scValToNative(scVal, structTypeDef)

        assertTrue(result is Map<*, *>)
        val map = result as Map<*, *>
        assertEquals("Alice", map["name"])
        assertEquals(30u, map["age"])
    }

    @Test
    fun testRoundTripAddress() {
        val spec = ContractSpec(emptyList())
        val addressTypeDef = createTypeDef(SCSpecTypeXdr.SC_SPEC_TYPE_ADDRESS)
        val accountId = "GBRPYHIL2CI3FNQ4BXLFMNDLFJUNPU2HY3ZMFSHONUCEOASW7QC7OX2H"

        val scVal = spec.nativeToXdrSCVal(accountId, addressTypeDef)
        val result = spec.scValToNative(scVal, addressTypeDef)

        assertEquals(accountId, result)
    }

    // ========== Missing Type Tests (Muxed Address, Error, Val, Result) ==========

    @Test
    fun testMuxedAddressConversion() {
        val spec = ContractSpec(emptyList())
        val typeDef = createTypeDef(SCSpecTypeXdr.SC_SPEC_TYPE_MUXED_ADDRESS)

        // Test Case 1: G... address (regular account)
        val regularAddress = "GBLGJA4TUN5XOGTV6WO2BWYUI2OZR5GYQ5PDPCRMQ5XEPJOYWB2X4CJO"
        val result1 = spec.nativeToXdrSCVal(regularAddress, typeDef)

        assertTrue(result1 is SCValXdr.Address)
        val address1 = (result1 as SCValXdr.Address).value
        assertTrue(address1 is SCAddressXdr.AccountId)

        // Verify round-trip conversion
        val convertedAddress1 = Address.fromSCVal(result1)
        assertEquals(regularAddress, convertedAddress1.toString())

        // Test Case 2: M... address (muxed account) - THE MAIN TEST
        val muxedAddress = "MAQAA5L65LSYH7CQ3VTJ7F3HHLGCL3DSLAR2Y47263D56MNNGHSQSAAAAAAAAAAE2LP26"
        val result2 = spec.nativeToXdrSCVal(muxedAddress, typeDef)

        assertTrue(result2 is SCValXdr.Address)
        val address2 = (result2 as SCValXdr.Address).value
        assertTrue(address2 is SCAddressXdr.MuxedAccount, "Expected MuxedAccount address type, but got ${address2::class.simpleName}")

        // Verify the muxed account structure
        val muxedAccountXdr = (address2 as SCAddressXdr.MuxedAccount).value
        assertNotNull(muxedAccountXdr.ed25519, "Muxed account should have ed25519 public key")
        assertNotNull(muxedAccountXdr.id, "Muxed account should have ID")

        // Verify round-trip conversion preserves the muxed address
        val convertedAddress2 = Address.fromSCVal(result2)
        assertEquals(muxedAddress, convertedAddress2.toString(), "Muxed address round-trip failed")
        assertEquals(Address.AddressType.MUXED_ACCOUNT, convertedAddress2.addressType, "Address type should be MUXED_ACCOUNT")

        // Test Case 3: C... address (contract)
        val contractAddress = "CA3D5KRYM6CB7OWQ6TWYRR3Z4T7GNZLKERYNZGGA5SOAOPIFY6YQGAXE"
        val result3 = spec.nativeToXdrSCVal(contractAddress, typeDef)

        assertTrue(result3 is SCValXdr.Address)
        val address3 = (result3 as SCValXdr.Address).value
        assertTrue(address3 is SCAddressXdr.ContractId)

        // Verify round-trip conversion
        val convertedAddress3 = Address.fromSCVal(result3)
        assertEquals(contractAddress, convertedAddress3.toString())
    }

    @Test
    fun testErrorConversion() {
        val spec = ContractSpec(emptyList())
        val typeDef = createTypeDef(SCSpecTypeXdr.SC_SPEC_TYPE_ERROR)

        // Test numeric error code
        val result = spec.nativeToXdrSCVal(42, typeDef)
        assertTrue(result is SCValXdr.Error)

        // Test SCErrorXdr
        val errorXdr = SCErrorXdr.ContractCode(Uint32Xdr(100u))
        val result2 = spec.nativeToXdrSCVal(errorXdr, typeDef)
        assertTrue(result2 is SCValXdr.Error)
    }

    @Test
    fun testValConversion() {
        val spec = ContractSpec(emptyList())
        val typeDef = createTypeDef(SCSpecTypeXdr.SC_SPEC_TYPE_VAL)

        // Test with SCValXdr (pass through)
        val scVal = SCValXdr.U32(Uint32Xdr(42u))
        val result = spec.nativeToXdrSCVal(scVal, typeDef)
        assertEquals(scVal, result)

        // Test with native value (auto-convert)
        val result2 = spec.nativeToXdrSCVal("test", typeDef)
        assertTrue(result2 is SCValXdr.Str)

        val result3 = spec.nativeToXdrSCVal(true, typeDef)
        assertTrue(result3 is SCValXdr.B)

        val result4 = spec.nativeToXdrSCVal(42, typeDef)
        assertTrue(result4 is SCValXdr.I32)
    }

    @Test
    fun testResultConversion() {
        val spec = ContractSpec(emptyList())

        // Create Result type def with U32 ok type and Error error type
        val okType = createTypeDef(SCSpecTypeXdr.SC_SPEC_TYPE_U32)
        val errorType = createTypeDef(SCSpecTypeXdr.SC_SPEC_TYPE_ERROR)
        val resultTypeDef = SCSpecTypeDefXdr.Result(
            SCSpecTypeResultXdr(okType = okType, errorType = errorType)
        )

        // Test Kotlin Result success
        val successResult = kotlin.Result.success(42u)
        val scVal1 = spec.nativeToXdrSCVal(successResult, resultTypeDef)
        assertTrue(scVal1 is SCValXdr.U32)
        assertEquals(42u, (scVal1 as SCValXdr.U32).value.value)

        // Test Kotlin Result failure
        val failureResult = kotlin.Result.failure<UInt>(Exception("Test error"))
        val scVal2 = spec.nativeToXdrSCVal(failureResult, resultTypeDef)
        assertTrue(scVal2 is SCValXdr.Error)

        // Test Map with "ok" key
        val okMap = mapOf("ok" to 42u)
        val scVal3 = spec.nativeToXdrSCVal(okMap, resultTypeDef)
        assertTrue(scVal3 is SCValXdr.U32)
        assertEquals(42u, (scVal3 as SCValXdr.U32).value.value)

        // Test Map with "error" key
        val errorMap = mapOf("error" to SCErrorXdr.ContractCode(Uint32Xdr(1u)))
        val scVal4 = spec.nativeToXdrSCVal(errorMap, resultTypeDef)
        assertTrue(scVal4 is SCValXdr.Error)
    }

    @Test
    fun testResultConversionWithVoidOk() {
        val spec = ContractSpec(emptyList())

        // Create Result type def with VOID ok type
        val okType = createTypeDef(SCSpecTypeXdr.SC_SPEC_TYPE_VOID)
        val errorType = createTypeDef(SCSpecTypeXdr.SC_SPEC_TYPE_ERROR)
        val resultTypeDef = SCSpecTypeDefXdr.Result(
            SCSpecTypeResultXdr(okType = okType, errorType = errorType)
        )

        // Test success with null value
        val successResult = kotlin.Result.success(null as Unit?)
        val scVal = spec.nativeToXdrSCVal(successResult, resultTypeDef)
        assertTrue(scVal is SCValXdr.Void)

        // Test Map with ok: null
        val okMap = mapOf("ok" to null)
        val scVal2 = spec.nativeToXdrSCVal(okMap, resultTypeDef)
        assertTrue(scVal2 is SCValXdr.Void)
    }

    // ========== New Tests for SCV_ERROR and SCV_CONTRACT_INSTANCE ==========

    @Test
    fun testScValToNativeError() {
        val spec = ContractSpec(emptyList())

        // Create SCErrorXdr instances
        val contractError = SCErrorXdr.ContractCode(Uint32Xdr(42u))
        val scValError = SCValXdr.Error(contractError)

        // Convert to native
        val result = spec.scValToNative(scValError, null)

        // Verify result is SCErrorXdr
        assertTrue(result is SCErrorXdr)
        assertTrue((result as SCErrorXdr) is SCErrorXdr.ContractCode)
        assertEquals(42u, (result as SCErrorXdr.ContractCode).value.value)

        // Test with WasmVm error
        val wasmError = SCErrorXdr.Code(SCErrorCodeXdr.SCEC_ARITH_DOMAIN)
        val scValCodeError = SCValXdr.Error(wasmError)
        val codeResult = spec.scValToNative(scValCodeError, null)

        assertTrue(codeResult is SCErrorXdr)
        assertTrue((codeResult as SCErrorXdr) is SCErrorXdr.Code)
    }

    @Test
    fun testScValToNativeContractInstance() {
        val spec = ContractSpec(emptyList())

        // Create a minimal SCContractInstanceXdr
        val executable = ContractExecutableXdr.WasmHash(HashXdr(ByteArray(32)))
        val contractInstance = SCContractInstanceXdr(
            executable = executable,
            storage = null
        )
        val scValInstance = SCValXdr.Instance(contractInstance)

        // Convert to native
        val result = spec.scValToNative(scValInstance, null)

        // Verify result is SCContractInstanceXdr
        assertTrue(result is SCContractInstanceXdr)
        assertEquals(executable, (result as SCContractInstanceXdr).executable)
    }

    @Test
    fun testFuncResToNativeResultError() {
        // Create a contract spec with a function that returns Result<U32, Error>
        val okType = SCSpecTypeDefXdr.Void(SCSpecTypeXdr.SC_SPEC_TYPE_U32)
        val errorType = SCSpecTypeDefXdr.Void(SCSpecTypeXdr.SC_SPEC_TYPE_ERROR)
        val resultType = SCSpecTypeDefXdr.Result(
            SCSpecTypeResultXdr(okType = okType, errorType = errorType)
        )

        val functionSpec = SCSpecFunctionV0Xdr(
            name = SCSymbolXdr("test_func"),
            inputs = emptyList(),
            outputs = listOf(resultType),
            doc = ""
        )

        val specEntry = SCSpecEntryXdr.FunctionV0(functionSpec)
        val spec = ContractSpec(listOf(specEntry))

        // Test 1: Ok value (U32)
        val okValue = SCValXdr.U32(Uint32Xdr(42u))
        val okResult = spec.funcResToNative("test_func", okValue)

        assertEquals(42u, okResult)

        // Test 2: Error value
        val contractError = SCErrorXdr.ContractCode(Uint32Xdr(100u))
        val errorValue = SCValXdr.Error(contractError)
        val errorResult = spec.funcResToNative("test_func", errorValue)

        // Should return the SCErrorXdr
        assertTrue(errorResult is SCErrorXdr)
        assertEquals(100u, ((errorResult as SCErrorXdr.ContractCode).value.value))
    }

    @Test
    fun testFuncResToNativeResultVoidOk() {
        // Create a contract spec with a function that returns Result<Void, Error>
        val okType = SCSpecTypeDefXdr.Void(SCSpecTypeXdr.SC_SPEC_TYPE_VOID)
        val errorType = SCSpecTypeDefXdr.Void(SCSpecTypeXdr.SC_SPEC_TYPE_ERROR)
        val resultType = SCSpecTypeDefXdr.Result(
            SCSpecTypeResultXdr(okType = okType, errorType = errorType)
        )

        val functionSpec = SCSpecFunctionV0Xdr(
            name = SCSymbolXdr("test_void_func"),
            inputs = emptyList(),
            outputs = listOf(resultType),
            doc = ""
        )

        val specEntry = SCSpecEntryXdr.FunctionV0(functionSpec)
        val spec = ContractSpec(listOf(specEntry))

        // Test: Void ok value
        val voidValue = SCValXdr.Void(SCValTypeXdr.SCV_VOID)
        val result = spec.funcResToNative("test_void_func", voidValue)

        // Should return null for void
        assertNull(result)
    }

    // ========== scValToNative with null TypeDef (Type Inference) Tests ==========

    @Test
    fun testScValToNativeWithNullTypeDefVoid() {
        val spec = ContractSpec(emptyList())
        assertNull(spec.scValToNative(SCValXdr.Void(SCValTypeXdr.SCV_VOID), null))
    }

    @Test
    fun testScValToNativeWithNullTypeDefBool() {
        val spec = ContractSpec(emptyList())
        assertEquals(true, spec.scValToNative(SCValXdr.B(true), null))
    }

    @Test
    fun testScValToNativeWithNullTypeDefU32() {
        val spec = ContractSpec(emptyList())
        assertEquals(123u, spec.scValToNative(SCValXdr.U32(Uint32Xdr(123u)), null))
    }

    @Test
    fun testScValToNativeWithNullTypeDefI32() {
        val spec = ContractSpec(emptyList())
        assertEquals(-42, spec.scValToNative(SCValXdr.I32(Int32Xdr(-42)), null))
    }

    @Test
    fun testScValToNativeWithNullTypeDefU64() {
        val spec = ContractSpec(emptyList())
        assertEquals(999UL, spec.scValToNative(SCValXdr.U64(Uint64Xdr(999UL)), null))
    }

    @Test
    fun testScValToNativeWithNullTypeDefI64() {
        val spec = ContractSpec(emptyList())
        assertEquals(-999L, spec.scValToNative(SCValXdr.I64(Int64Xdr(-999L)), null))
    }

    @Test
    fun testScValToNativeWithNullTypeDefString() {
        val spec = ContractSpec(emptyList())
        assertEquals("hello", spec.scValToNative(SCValXdr.Str(SCStringXdr("hello")), null))
    }

    @Test
    fun testScValToNativeWithNullTypeDefSymbol() {
        val spec = ContractSpec(emptyList())
        assertEquals("sym", spec.scValToNative(SCValXdr.Sym(SCSymbolXdr("sym")), null))
    }

    @Test
    fun testScValToNativeWithNullTypeDefBytes() {
        val spec = ContractSpec(emptyList())
        assertContentEquals(
            byteArrayOf(1, 2, 3),
            spec.scValToNative(SCValXdr.Bytes(SCBytesXdr(byteArrayOf(1, 2, 3))), null) as ByteArray
        )
    }

    @Test
    fun testScValToNativeWithNullTypeDefTimepoint() {
        val spec = ContractSpec(emptyList())
        assertEquals(123UL, spec.scValToNative(SCValXdr.Timepoint(TimePointXdr(Uint64Xdr(123UL))), null))
    }

    @Test
    fun testScValToNativeWithNullTypeDefDuration() {
        val spec = ContractSpec(emptyList())
        assertEquals(456UL, spec.scValToNative(SCValXdr.Duration(DurationXdr(Uint64Xdr(456UL))), null))
    }

    // ========== scValToNative Type Mismatch Error Cases ==========

    @Test
    fun testScValToNativeVecWithNonVecTypeDef() {
        val spec = ContractSpec(emptyList())
        assertFailsWith<ContractSpecException> {
            spec.scValToNative(
                SCValXdr.Vec(SCVecXdr(emptyList())),
                createTypeDef(SCSpecTypeXdr.SC_SPEC_TYPE_BOOL)
            )
        }
    }

    @Test
    fun testScValToNativeMapWithNonMapTypeDef() {
        val spec = ContractSpec(emptyList())
        assertFailsWith<ContractSpecException> {
            spec.scValToNative(
                SCValXdr.Map(SCMapXdr(emptyList())),
                createTypeDef(SCSpecTypeXdr.SC_SPEC_TYPE_BOOL)
            )
        }
    }

    // ========== nativeToXdrSCVal Edge Cases ==========

    @Test
    fun testNativeToXdrSCValPassthrough() {
        val spec = ContractSpec(emptyList())
        val original = SCValXdr.U32(Uint32Xdr(42u))
        val result = spec.nativeToXdrSCVal(original, createTypeDef(SCSpecTypeXdr.SC_SPEC_TYPE_VOID))
        assertSame(original, result)
    }

    @Test
    fun testI32ConversionOverflow() {
        val spec = ContractSpec(emptyList())
        assertFailsWith<ContractSpecException> {
            spec.nativeToXdrSCVal(Long.MAX_VALUE, createTypeDef(SCSpecTypeXdr.SC_SPEC_TYPE_I32))
        }
    }

    @Test
    fun testBytesConversionFromHexWithoutPrefix() {
        val spec = ContractSpec(emptyList())
        val result = spec.nativeToXdrSCVal("0102", createTypeDef(SCSpecTypeXdr.SC_SPEC_TYPE_BYTES))
        assertTrue(result is SCValXdr.Bytes)
    }

    @Test
    fun testBytesConversionFromUnsupportedType() {
        val spec = ContractSpec(emptyList())
        assertFailsWith<ContractSpecException> {
            spec.nativeToXdrSCVal(42, createTypeDef(SCSpecTypeXdr.SC_SPEC_TYPE_BYTES))
        }
    }

    @Test
    fun testVecConversionFromNonList() {
        val spec = ContractSpec(emptyList())
        val vecTypeDef = SCSpecTypeDefXdr.Vec(
            SCSpecTypeVecXdr(createTypeDef(SCSpecTypeXdr.SC_SPEC_TYPE_U32))
        )
        assertFailsWith<ContractSpecException> {
            spec.nativeToXdrSCVal("not a list", vecTypeDef)
        }
    }

    @Test
    fun testMapConversionFromNonMap() {
        val spec = ContractSpec(emptyList())
        val mapTypeDef = SCSpecTypeDefXdr.Map(
            SCSpecTypeMapXdr(
                createTypeDef(SCSpecTypeXdr.SC_SPEC_TYPE_U32),
                createTypeDef(SCSpecTypeXdr.SC_SPEC_TYPE_U32)
            )
        )
        assertFailsWith<ContractSpecException> {
            spec.nativeToXdrSCVal("not a map", mapTypeDef)
        }
    }

    @Test
    fun testTupleConversionFromNonList() {
        val spec = ContractSpec(emptyList())
        val tupleTypeDef = SCSpecTypeDefXdr.Tuple(
            SCSpecTypeTupleXdr(listOf(createTypeDef(SCSpecTypeXdr.SC_SPEC_TYPE_U32)))
        )
        assertFailsWith<ContractSpecException> {
            spec.nativeToXdrSCVal("not a list", tupleTypeDef)
        }
    }

    @Test
    fun testBytesNConversionFromNonByteArray() {
        val spec = ContractSpec(emptyList())
        val bytesNTypeDef = SCSpecTypeDefXdr.BytesN(
            SCSpecTypeBytesNXdr(Uint32Xdr(3u))
        )
        assertFailsWith<ContractSpecException> {
            spec.nativeToXdrSCVal(42, bytesNTypeDef)
        }
    }

    @Test
    fun testErrorConversionFromUnsupportedType() {
        val spec = ContractSpec(emptyList())
        assertFailsWith<ContractSpecException> {
            spec.nativeToXdrSCVal("not an error", createTypeDef(SCSpecTypeXdr.SC_SPEC_TYPE_ERROR))
        }
    }

    // ========== Val Type Additional Conversions ==========

    @Test
    fun testValConversionLong() {
        val spec = ContractSpec(emptyList())
        val result = spec.nativeToXdrSCVal(42L, createTypeDef(SCSpecTypeXdr.SC_SPEC_TYPE_VAL))
        assertTrue(result is SCValXdr.I64)
    }

    @Test
    fun testValConversionUInt() {
        val spec = ContractSpec(emptyList())
        val result = spec.nativeToXdrSCVal(42u, createTypeDef(SCSpecTypeXdr.SC_SPEC_TYPE_VAL))
        assertTrue(result is SCValXdr.U32)
    }

    @Test
    fun testValConversionULong() {
        val spec = ContractSpec(emptyList())
        val result = spec.nativeToXdrSCVal(42UL, createTypeDef(SCSpecTypeXdr.SC_SPEC_TYPE_VAL))
        assertTrue(result is SCValXdr.U64)
    }

    @Test
    fun testValConversionUnsupportedType() {
        val spec = ContractSpec(emptyList())
        assertFailsWith<ContractSpecException> {
            spec.nativeToXdrSCVal(listOf(1), createTypeDef(SCSpecTypeXdr.SC_SPEC_TYPE_VAL))
        }
    }

    // ========== Result Type Error Cases ==========

    @Test
    fun testResultConversionMapWithoutOkOrErrorKey() {
        val spec = ContractSpec(emptyList())
        val resultTypeDef = SCSpecTypeDefXdr.Result(
            SCSpecTypeResultXdr(
                createTypeDef(SCSpecTypeXdr.SC_SPEC_TYPE_U32),
                createTypeDef(SCSpecTypeXdr.SC_SPEC_TYPE_U32)
            )
        )
        assertFailsWith<ContractSpecException> {
            spec.nativeToXdrSCVal(mapOf("x" to 1), resultTypeDef)
        }
    }

    @Test
    fun testResultConversionFromUnsupportedType() {
        val spec = ContractSpec(emptyList())
        val resultTypeDef = SCSpecTypeDefXdr.Result(
            SCSpecTypeResultXdr(
                createTypeDef(SCSpecTypeXdr.SC_SPEC_TYPE_U32),
                createTypeDef(SCSpecTypeXdr.SC_SPEC_TYPE_U32)
            )
        )
        assertFailsWith<ContractSpecException> {
            spec.nativeToXdrSCVal("bad", resultTypeDef)
        }
    }

    @Test
    fun testResultConversionErrorWithNull() {
        val spec = ContractSpec(emptyList())
        val resultTypeDef = SCSpecTypeDefXdr.Result(
            SCSpecTypeResultXdr(
                createTypeDef(SCSpecTypeXdr.SC_SPEC_TYPE_U32),
                createTypeDef(SCSpecTypeXdr.SC_SPEC_TYPE_U32)
            )
        )
        assertFailsWith<ContractSpecException> {
            spec.nativeToXdrSCVal(mapOf("error" to null), resultTypeDef)
        }
    }

    // ========== U32 Type Variant Inputs ==========

    @Test
    fun testU32ConversionFromUInt() {
        val spec = ContractSpec(emptyList())
        val result = spec.nativeToXdrSCVal(42u, createTypeDef(SCSpecTypeXdr.SC_SPEC_TYPE_U32))
        assertTrue(result is SCValXdr.U32)
    }

    @Test
    fun testU32ConversionFromULong() {
        val spec = ContractSpec(emptyList())
        val result = spec.nativeToXdrSCVal(42UL, createTypeDef(SCSpecTypeXdr.SC_SPEC_TYPE_U32))
        assertTrue(result is SCValXdr.U32)
    }

    @Test
    fun testU32ConversionFromDouble() {
        val spec = ContractSpec(emptyList())
        val result = spec.nativeToXdrSCVal(42.0, createTypeDef(SCSpecTypeXdr.SC_SPEC_TYPE_U32))
        assertTrue(result is SCValXdr.U32)
    }

    @Test
    fun testU32ConversionFromFloat() {
        val spec = ContractSpec(emptyList())
        val result = spec.nativeToXdrSCVal(42.0f, createTypeDef(SCSpecTypeXdr.SC_SPEC_TYPE_U32))
        assertTrue(result is SCValXdr.U32)
    }

    @Test
    fun testU32ConversionFromString() {
        val spec = ContractSpec(emptyList())
        val result = spec.nativeToXdrSCVal("42", createTypeDef(SCSpecTypeXdr.SC_SPEC_TYPE_U32))
        assertTrue(result is SCValXdr.U32)
    }

    @Test
    fun testU32ConversionFromInvalidString() {
        val spec = ContractSpec(emptyList())
        assertFailsWith<ContractSpecException> {
            spec.nativeToXdrSCVal("xyz", createTypeDef(SCSpecTypeXdr.SC_SPEC_TYPE_U32))
        }
    }

    @Test
    fun testU32ConversionFromUnsupportedType() {
        val spec = ContractSpec(emptyList())
        assertFailsWith<ContractSpecException> {
            spec.nativeToXdrSCVal(listOf(1), createTypeDef(SCSpecTypeXdr.SC_SPEC_TYPE_U32))
        }
    }

    // ========== funcResToNative Error Cases ==========

    @Test
    fun testFuncResToNativeFunctionNotFound() {
        val spec = ContractSpec(emptyList())
        assertFailsWith<ContractSpecException> {
            spec.funcResToNative("nonexistent", SCValXdr.Void(SCValTypeXdr.SCV_VOID))
        }
    }

    // ========== Introspection Method Tests ==========

    @Test
    fun testUdtStructsIntrospection() {
        val entries = listOf(
            createStructEntry("MyStruct", listOf("field1" to SCSpecTypeXdr.SC_SPEC_TYPE_SYMBOL))
        )
        val spec = ContractSpec(entries)
        assertEquals(1, spec.udtStructs().size)
    }

    @Test
    fun testUdtUnionsIntrospection() {
        val entries = listOf(
            createUnionEntry("MyUnion", listOf("Case1" to 0))
        )
        val spec = ContractSpec(entries)
        assertEquals(1, spec.udtUnions().size)
    }

    @Test
    fun testUdtEnumsIntrospection() {
        val entries = listOf(
            createEnumEntry("MyEnum", listOf("Case1", "Case2"))
        )
        val spec = ContractSpec(entries)
        assertEquals(1, spec.udtEnums().size)
    }

    @Test
    fun testUdtErrorEnumsIntrospection() {
        val entries = listOf(
            SCSpecEntryXdr.UdtErrorEnumV0(
                SCSpecUDTErrorEnumV0Xdr("", "", "MyErrorEnum", emptyList())
            )
        )
        val spec = ContractSpec(entries)
        assertEquals(1, spec.udtErrorEnums().size)
    }

    @Test
    fun testEventsIntrospection() {
        val entries = listOf(
            SCSpecEntryXdr.EventV0(
                SCSpecEventV0Xdr(
                    "", "",
                    SCSymbolXdr("MyEvent"),
                    emptyList(), emptyList(),
                    SCSpecEventDataFormatXdr.SC_SPEC_EVENT_DATA_FORMAT_SINGLE_VALUE
                )
            )
        )
        val spec = ContractSpec(entries)
        assertEquals(1, spec.events().size)
    }

    // ========== findEntry Additional Types ==========

    @Test
    fun testFindEntryUnion() {
        val entries = listOf(
            createUnionEntry("MyUnion", listOf("Case1" to 0))
        )
        val spec = ContractSpec(entries)
        val entry = spec.findEntry("MyUnion")
        assertNotNull(entry)
        assertTrue(entry is SCSpecEntryXdr.UdtUnionV0)
    }

    @Test
    fun testFindEntryErrorEnum() {
        val entries = listOf(
            SCSpecEntryXdr.UdtErrorEnumV0(
                SCSpecUDTErrorEnumV0Xdr("", "", "MyErrorEnum", emptyList())
            )
        )
        val spec = ContractSpec(entries)
        val entry = spec.findEntry("MyErrorEnum")
        assertNotNull(entry)
        assertTrue(entry is SCSpecEntryXdr.UdtErrorEnumV0)
    }

    @Test
    fun testFindEntryEvent() {
        val entries = listOf(
            SCSpecEntryXdr.EventV0(
                SCSpecEventV0Xdr(
                    "", "",
                    SCSymbolXdr("MyEvent"),
                    emptyList(), emptyList(),
                    SCSpecEventDataFormatXdr.SC_SPEC_EVENT_DATA_FORMAT_SINGLE_VALUE
                )
            )
        )
        val spec = ContractSpec(entries)
        val entry = spec.findEntry("MyEvent")
        assertNotNull(entry)
        assertTrue(entry is SCSpecEntryXdr.EventV0)
    }

    // ========== UDT Error Cases ==========

    @Test
    fun testEnumConversionInvalidNumericValue() {
        val entries = listOf(
            createEnumEntry("Status", listOf("Pending", "Active"))
        )
        val spec = ContractSpec(entries)
        val enumTypeDef = createUdtTypeDef("Status")

        assertFailsWith<ContractSpecException> {
            spec.nativeToXdrSCVal(99, enumTypeDef)
        }
    }

    @Test
    fun testEnumConversionUnsupportedType() {
        val entries = listOf(
            createEnumEntry("Status", listOf("Pending"))
        )
        val spec = ContractSpec(entries)
        val enumTypeDef = createUdtTypeDef("Status")

        assertFailsWith<ContractSpecException> {
            spec.nativeToXdrSCVal(listOf(1), enumTypeDef)
        }
    }

    @Test
    fun testUnionConversionInvalidCaseName() {
        val entries = listOf(
            createUnionEntry("Result", listOf("Success" to 0))
        )
        val spec = ContractSpec(entries)
        val unionTypeDef = createUdtTypeDef("Result")

        assertFailsWith<ContractSpecException> {
            spec.nativeToXdrSCVal(NativeUnionVal.VoidCase("NonExistent"), unionTypeDef)
        }
    }

    @Test
    fun testUnionConversionUnsupportedType() {
        val entries = listOf(
            createUnionEntry("Result", listOf("Success" to 0))
        )
        val spec = ContractSpec(entries)
        val unionTypeDef = createUdtTypeDef("Result")

        assertFailsWith<ContractSpecException> {
            spec.nativeToXdrSCVal("bad", unionTypeDef)
        }
    }

    @Test
    fun testStructConversionUnsupportedType() {
        val entries = listOf(
            createStructEntry("Person", listOf("name" to SCSpecTypeXdr.SC_SPEC_TYPE_SYMBOL))
        )
        val spec = ContractSpec(entries)
        val structTypeDef = createUdtTypeDef("Person")

        assertFailsWith<ContractSpecException> {
            spec.nativeToXdrSCVal("bad", structTypeDef)
        }
    }

    @Test
    fun testUdtTypeNotFound() {
        val spec = ContractSpec(emptyList())

        assertFailsWith<ContractSpecException> {
            spec.nativeToXdrSCVal(42, createUdtTypeDef("Missing"))
        }
    }

    // ========== Helper Functions ==========

    private fun createFunctionEntry(
        name: String,
        inputNames: List<String>,
        inputType: SCSpecTypeXdr = SCSpecTypeXdr.SC_SPEC_TYPE_SYMBOL
    ): SCSpecEntryXdr {
        val inputs = inputNames.map { inputName ->
            SCSpecFunctionInputV0Xdr(
                doc = "",
                name = inputName,
                type = createTypeDef(inputType)
            )
        }

        return SCSpecEntryXdr.FunctionV0(
            SCSpecFunctionV0Xdr(
                doc = "",
                name = SCSymbolXdr(name),
                inputs = inputs,
                outputs = emptyList()
            )
        )
    }

    private fun createFunctionEntryWithTypes(
        name: String,
        inputs: List<Pair<String, SCSpecTypeXdr>>
    ): SCSpecEntryXdr {
        val inputList = inputs.map { (inputName, inputType) ->
            SCSpecFunctionInputV0Xdr(
                doc = "",
                name = inputName,
                type = createTypeDef(inputType)
            )
        }

        return SCSpecEntryXdr.FunctionV0(
            SCSpecFunctionV0Xdr(
                doc = "",
                name = SCSymbolXdr(name),
                inputs = inputList,
                outputs = emptyList()
            )
        )
    }

    private fun createFunctionEntryWithOutputs(
        name: String,
        inputs: List<Pair<String, SCSpecTypeXdr>>,
        outputs: List<SCSpecTypeXdr>
    ): SCSpecEntryXdr {
        val inputList = inputs.map { (inputName, inputType) ->
            SCSpecFunctionInputV0Xdr(
                doc = "",
                name = inputName,
                type = createTypeDef(inputType)
            )
        }

        val outputList = outputs.map { outputType ->
            createTypeDef(outputType)
        }

        return SCSpecEntryXdr.FunctionV0(
            SCSpecFunctionV0Xdr(
                doc = "",
                name = SCSymbolXdr(name),
                inputs = inputList,
                outputs = outputList
            )
        )
    }

    private fun createStructEntry(name: String, fields: List<Pair<String, SCSpecTypeXdr>>): SCSpecEntryXdr {
        val fieldList = fields.map { (fieldName, fieldType) ->
            SCSpecUDTStructFieldV0Xdr(
                doc = "",
                name = fieldName,
                type = createTypeDef(fieldType)
            )
        }

        return SCSpecEntryXdr.UdtStructV0(
            SCSpecUDTStructV0Xdr(
                doc = "",
                lib = "",
                name = name,
                fields = fieldList
            )
        )
    }

    private fun createEnumEntry(name: String, caseNames: List<String>): SCSpecEntryXdr {
        val cases = caseNames.mapIndexed { index, caseName ->
            SCSpecUDTEnumCaseV0Xdr(
                doc = "",
                name = caseName,
                value = Uint32Xdr(index.toUInt())
            )
        }

        return SCSpecEntryXdr.UdtEnumV0(
            SCSpecUDTEnumV0Xdr(
                doc = "",
                lib = "",
                name = name,
                cases = cases
            )
        )
    }

    private fun createUnionEntry(name: String, cases: List<Pair<String, Int>>): SCSpecEntryXdr {
        val unionCases = cases.map { (caseName, _) ->
            SCSpecUDTUnionCaseV0Xdr.VoidCase(
                SCSpecUDTUnionCaseVoidV0Xdr(
                    doc = "",
                    name = caseName
                )
            )
        }

        return SCSpecEntryXdr.UdtUnionV0(
            SCSpecUDTUnionV0Xdr(
                doc = "",
                lib = "",
                name = name,
                cases = unionCases
            )
        )
    }

    private fun createUnionEntryWithTupleCase(
        name: String,
        tupleCase: Pair<String, List<SCSpecTypeXdr>>
    ): SCSpecEntryXdr {
        val (caseName, types) = tupleCase
        val unionCase = SCSpecUDTUnionCaseV0Xdr.TupleCase(
            SCSpecUDTUnionCaseTupleV0Xdr(
                doc = "",
                name = caseName,
                type = types.map { createTypeDef(it) }
            )
        )

        return SCSpecEntryXdr.UdtUnionV0(
            SCSpecUDTUnionV0Xdr(
                doc = "",
                lib = "",
                name = name,
                cases = listOf(unionCase)
            )
        )
    }

    private fun createAuthIncrementFunction(): SCSpecEntryXdr {
        val inputs = listOf(
            SCSpecFunctionInputV0Xdr(
                doc = "",
                name = "user",
                type = createTypeDef(SCSpecTypeXdr.SC_SPEC_TYPE_ADDRESS)
            ),
            SCSpecFunctionInputV0Xdr(
                doc = "",
                name = "value",
                type = createTypeDef(SCSpecTypeXdr.SC_SPEC_TYPE_U32)
            )
        )

        return SCSpecEntryXdr.FunctionV0(
            SCSpecFunctionV0Xdr(
                doc = "",
                name = SCSymbolXdr("increment"),
                inputs = inputs,
                outputs = listOf(createTypeDef(SCSpecTypeXdr.SC_SPEC_TYPE_U32))
            )
        )
    }

    private fun createAtomicSwapFunction(): SCSpecEntryXdr {
        val inputs = listOf(
            SCSpecFunctionInputV0Xdr(doc = "", name = "a", type = createTypeDef(SCSpecTypeXdr.SC_SPEC_TYPE_ADDRESS)),
            SCSpecFunctionInputV0Xdr(doc = "", name = "b", type = createTypeDef(SCSpecTypeXdr.SC_SPEC_TYPE_ADDRESS)),
            SCSpecFunctionInputV0Xdr(doc = "", name = "token_a", type = createTypeDef(SCSpecTypeXdr.SC_SPEC_TYPE_ADDRESS)),
            SCSpecFunctionInputV0Xdr(doc = "", name = "token_b", type = createTypeDef(SCSpecTypeXdr.SC_SPEC_TYPE_ADDRESS)),
            SCSpecFunctionInputV0Xdr(doc = "", name = "amount_a", type = createTypeDef(SCSpecTypeXdr.SC_SPEC_TYPE_I128)),
            SCSpecFunctionInputV0Xdr(doc = "", name = "min_b_for_a", type = createTypeDef(SCSpecTypeXdr.SC_SPEC_TYPE_I128)),
            SCSpecFunctionInputV0Xdr(doc = "", name = "amount_b", type = createTypeDef(SCSpecTypeXdr.SC_SPEC_TYPE_I128)),
            SCSpecFunctionInputV0Xdr(doc = "", name = "min_a_for_b", type = createTypeDef(SCSpecTypeXdr.SC_SPEC_TYPE_I128))
        )

        return SCSpecEntryXdr.FunctionV0(
            SCSpecFunctionV0Xdr(
                doc = "",
                name = SCSymbolXdr("swap"),
                inputs = inputs,
                outputs = emptyList()
            )
        )
    }

    private fun createTokenInitializeFunction(): SCSpecEntryXdr {
        val inputs = listOf(
            SCSpecFunctionInputV0Xdr(doc = "", name = "admin", type = createTypeDef(SCSpecTypeXdr.SC_SPEC_TYPE_ADDRESS)),
            SCSpecFunctionInputV0Xdr(doc = "", name = "decimal", type = createTypeDef(SCSpecTypeXdr.SC_SPEC_TYPE_U32)),
            SCSpecFunctionInputV0Xdr(doc = "", name = "name", type = createTypeDef(SCSpecTypeXdr.SC_SPEC_TYPE_STRING)),
            SCSpecFunctionInputV0Xdr(doc = "", name = "symbol", type = createTypeDef(SCSpecTypeXdr.SC_SPEC_TYPE_STRING))
        )

        return SCSpecEntryXdr.FunctionV0(
            SCSpecFunctionV0Xdr(
                doc = "",
                name = SCSymbolXdr("initialize"),
                inputs = inputs,
                outputs = emptyList()
            )
        )
    }

    private fun createTokenMintFunction(): SCSpecEntryXdr {
        val inputs = listOf(
            SCSpecFunctionInputV0Xdr(doc = "", name = "to", type = createTypeDef(SCSpecTypeXdr.SC_SPEC_TYPE_ADDRESS)),
            SCSpecFunctionInputV0Xdr(doc = "", name = "amount", type = createTypeDef(SCSpecTypeXdr.SC_SPEC_TYPE_I128))
        )

        return SCSpecEntryXdr.FunctionV0(
            SCSpecFunctionV0Xdr(
                doc = "",
                name = SCSymbolXdr("mint"),
                inputs = inputs,
                outputs = emptyList()
            )
        )
    }

    private fun createTokenBalanceFunction(): SCSpecEntryXdr {
        val inputs = listOf(
            SCSpecFunctionInputV0Xdr(doc = "", name = "id", type = createTypeDef(SCSpecTypeXdr.SC_SPEC_TYPE_ADDRESS))
        )

        return SCSpecEntryXdr.FunctionV0(
            SCSpecFunctionV0Xdr(
                doc = "",
                name = SCSymbolXdr("balance"),
                inputs = inputs,
                outputs = listOf(createTypeDef(SCSpecTypeXdr.SC_SPEC_TYPE_I128))
            )
        )
    }

    private fun createComplexMixedFunction(): SCSpecEntryXdr {
        val inputs = listOf(
            SCSpecFunctionInputV0Xdr(doc = "", name = "address", type = createTypeDef(SCSpecTypeXdr.SC_SPEC_TYPE_ADDRESS)),
            SCSpecFunctionInputV0Xdr(doc = "", name = "amount", type = createTypeDef(SCSpecTypeXdr.SC_SPEC_TYPE_I128)),
            SCSpecFunctionInputV0Xdr(
                doc = "",
                name = "metadata",
                type = SCSpecTypeDefXdr.Map(
                    SCSpecTypeMapXdr(
                        createTypeDef(SCSpecTypeXdr.SC_SPEC_TYPE_SYMBOL),
                        createTypeDef(SCSpecTypeXdr.SC_SPEC_TYPE_STRING)
                    )
                )
            ),
            SCSpecFunctionInputV0Xdr(
                doc = "",
                name = "tags",
                type = SCSpecTypeDefXdr.Vec(
                    SCSpecTypeVecXdr(createTypeDef(SCSpecTypeXdr.SC_SPEC_TYPE_STRING))
                )
            ),
            SCSpecFunctionInputV0Xdr(doc = "", name = "contract_id", type = createTypeDef(SCSpecTypeXdr.SC_SPEC_TYPE_ADDRESS)),
            SCSpecFunctionInputV0Xdr(doc = "", name = "timestamp", type = createTypeDef(SCSpecTypeXdr.SC_SPEC_TYPE_TIMEPOINT)),
            SCSpecFunctionInputV0Xdr(doc = "", name = "enabled", type = createTypeDef(SCSpecTypeXdr.SC_SPEC_TYPE_BOOL))
        )

        return SCSpecEntryXdr.FunctionV0(
            SCSpecFunctionV0Xdr(
                doc = "",
                name = SCSymbolXdr("complex_function"),
                inputs = inputs,
                outputs = emptyList()
            )
        )
    }

    private fun createTypeDef(type: SCSpecTypeXdr): SCSpecTypeDefXdr {
        // Create type def by encoding the discriminant and decoding it back
        // This is necessary because primitive types don't have public constructors
        val writer = XdrWriter()
        type.encode(writer)
        val bytes = writer.toByteArray()
        val reader = XdrReader(bytes)
        return SCSpecTypeDefXdr.decode(reader)
    }

    private fun createUdtTypeDef(name: String): SCSpecTypeDefXdr {
        return SCSpecTypeDefXdr.Udt(SCSpecTypeUDTXdr(name))
    }

}
