package com.soneso.stellar.sdk.contract

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
        assertEquals(true, (trueVal as SCValXdr.B).value)

        val falseVal = spec.nativeToXdrSCVal(false, createTypeDef(SCSpecTypeXdr.SC_SPEC_TYPE_BOOL))
        assertTrue(falseVal is SCValXdr.B)
        assertEquals(false, (falseVal as SCValXdr.B).value)

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
        assertEquals(42u, (result as SCValXdr.U32).value.value)

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
        assertEquals(42, (positive as SCValXdr.I32).value.value)

        val negative = spec.nativeToXdrSCVal(-42, typeDef)
        assertTrue(negative is SCValXdr.I32)
        assertEquals(-42, (negative as SCValXdr.I32).value.value)
    }

    @Test
    fun testU64Conversion() {
        val spec = ContractSpec(emptyList())
        val typeDef = createTypeDef(SCSpecTypeXdr.SC_SPEC_TYPE_U64)

        val result = spec.nativeToXdrSCVal(1234567890L, typeDef)
        assertTrue(result is SCValXdr.U64)
        assertEquals(1234567890UL, (result as SCValXdr.U64).value.value)

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
        assertEquals(1234567890L, (positive as SCValXdr.I64).value.value)

        val negative = spec.nativeToXdrSCVal(-1234567890L, typeDef)
        assertTrue(negative is SCValXdr.I64)
        assertEquals(-1234567890L, (negative as SCValXdr.I64).value.value)
    }

    @Test
    fun testU128Conversion() {
        val spec = ContractSpec(emptyList())
        val typeDef = createTypeDef(SCSpecTypeXdr.SC_SPEC_TYPE_U128)

        val result = spec.nativeToXdrSCVal(1000, typeDef)
        assertTrue(result is SCValXdr.U128)
        val parts = (result as SCValXdr.U128).value
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
        var parts = (positive as SCValXdr.I128).value
        assertEquals(0L, parts.hi.value)
        assertEquals(1000UL, parts.lo.value)

        val negative = spec.nativeToXdrSCVal(-1000, typeDef)
        assertTrue(negative is SCValXdr.I128)
        parts = (negative as SCValXdr.I128).value
        assertEquals(-1L, parts.hi.value) // Sign extension
    }

    @Test
    fun testU256Conversion() {
        val spec = ContractSpec(emptyList())
        val typeDef = createTypeDef(SCSpecTypeXdr.SC_SPEC_TYPE_U256)

        val result = spec.nativeToXdrSCVal(1000, typeDef)
        assertTrue(result is SCValXdr.U256)
        val parts = (result as SCValXdr.U256).value
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
        var parts = (positive as SCValXdr.I256).value
        assertEquals(0L, parts.hiHi.value)
        assertEquals(1000UL, parts.loLo.value)

        val negative = spec.nativeToXdrSCVal(-1000, typeDef)
        assertTrue(negative is SCValXdr.I256)
        parts = (negative as SCValXdr.I256).value
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
        assertEquals("Hello, World!", (result as SCValXdr.Str).value.value)

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
        assertEquals("my_symbol", (result as SCValXdr.Sym).value.value)

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
        assertContentEquals(bytes, (result1 as SCValXdr.Bytes).value.value)

        // From hex string
        val result2 = spec.nativeToXdrSCVal("0x0102030405", typeDef)
        assertTrue(result2 is SCValXdr.Bytes)
        assertContentEquals(bytes, (result2 as SCValXdr.Bytes).value.value)

        // From List<Byte>
        val byteList = listOf<Byte>(1, 2, 3, 4, 5)
        val result3 = spec.nativeToXdrSCVal(byteList, typeDef)
        assertTrue(result3 is SCValXdr.Bytes)
        assertContentEquals(bytes, (result3 as SCValXdr.Bytes).value.value)
    }

    @Test
    fun testTimepointConversion() {
        val spec = ContractSpec(emptyList())
        val typeDef = createTypeDef(SCSpecTypeXdr.SC_SPEC_TYPE_TIMEPOINT)

        val result = spec.nativeToXdrSCVal(1234567890L, typeDef)
        assertTrue(result is SCValXdr.Timepoint)
        assertEquals(1234567890UL, (result as SCValXdr.Timepoint).value.value.value)

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
        assertEquals(3600UL, (result as SCValXdr.Duration).value.value.value)

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
        assertEquals(42u, (result as SCValXdr.U32).value.value)
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
        val vec = (result as SCValXdr.Vec).value
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
        val scMap = (result as SCValXdr.Map).value
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
        val vec = (result as SCValXdr.Vec).value
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
        assertEquals(32, (result as SCValXdr.Bytes).value.value.size)
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
        assertEquals(1u, (result as SCValXdr.U32).value.value)
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
        assertEquals(2u, (result as SCValXdr.U32).value.value)
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
        val map = (result as SCValXdr.Map).value
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
        val vec = (result as SCValXdr.Vec).value
        assertNotNull(vec)
        assertEquals(1, vec.value.size) // Just the tag
        assertTrue(vec.value[0] is SCValXdr.Sym)
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
