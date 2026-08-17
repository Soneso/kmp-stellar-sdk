package com.soneso.stellar.sdk.unitTests

import com.soneso.stellar.sdk.*
import com.soneso.stellar.sdk.xdr.*
import kotlin.test.*

/**
 * Tests the hex alphabet gate on the operation builders that take hex-encoded ids.
 *
 * Every id here is the right length, so the length checks pass it through and only the
 * hex alphabet gate stands between the caller's string and a byte array that does not
 * correspond to it.
 */
class OperationHexIdValidationTest {

    companion object {
        const val ACCOUNT_ID = "GADBBY4WFXKKFJ7CMTG3J5YAUXMQDBILRQ6W3U5IWN5TQFZU4MWZ5T4K"

        /** 64 characters of "-1" pairs: a radix parse reads each pair as -1 and truncates to 0xff. */
        const val NEGATIVE_SIGN_ID = "-1-1-1-1-1-1-1-1-1-1-1-1-1-1-1-1-1-1-1-1-1-1-1-1-1-1-1-1-1-1-1-1"

        /** 64 characters of "+1" pairs: a radix parse reads each pair as 1. */
        const val POSITIVE_SIGN_ID = "+1+1+1+1+1+1+1+1+1+1+1+1+1+1+1+1+1+1+1+1+1+1+1+1+1+1+1+1+1+1+1+1"

        /** 64 fullwidth digits: Unicode decimal digits a radix parse accepts, case folding keeps them fullwidth. */
        val FULLWIDTH_ID = "１".repeat(64)

        /** 64 Arabic-Indic digits: Unicode decimal digits a radix parse accepts. */
        val ARABIC_INDIC_ID = "١".repeat(64)
    }

    // ========== LiquidityPoolDeposit ==========

    @Test
    fun testLiquidityPoolDepositRejectsSignedPoolId() {
        val op = LiquidityPoolDepositOperation(
            liquidityPoolId = NEGATIVE_SIGN_ID,
            maxAmountA = "100.0000000",
            maxAmountB = "200.0000000",
            minPrice = Price(1, 2),
            maxPrice = Price(2, 1)
        )
        val exception = assertFailsWith<IllegalArgumentException> { op.toOperationBody() }
        assertTrue(
            exception.message?.contains("0-9 and a-f") ?: false,
            "Unexpected message: ${exception.message}"
        )
    }

    @Test
    fun testLiquidityPoolDepositRejectsUnicodeDigitPoolId() {
        val fullwidth = LiquidityPoolDepositOperation(
            liquidityPoolId = FULLWIDTH_ID,
            maxAmountA = "1.0000000",
            maxAmountB = "1.0000000",
            minPrice = Price(1, 1),
            maxPrice = Price(1, 1)
        )
        assertFailsWith<IllegalArgumentException> { fullwidth.toOperationBody() }

        val arabicIndic = LiquidityPoolDepositOperation(
            liquidityPoolId = ARABIC_INDIC_ID,
            maxAmountA = "1.0000000",
            maxAmountB = "1.0000000",
            minPrice = Price(1, 1),
            maxPrice = Price(1, 1)
        )
        assertFailsWith<IllegalArgumentException> { arabicIndic.toOperationBody() }
    }

    @Test
    fun testLiquidityPoolDepositAcceptsMixedCasePoolId() {
        val poolId = "0A1b2C3d4E5f6789AbCdEf0123456789abcdef0123456789ABCDEF0123456789"
        val op = LiquidityPoolDepositOperation(
            liquidityPoolId = poolId,
            maxAmountA = "100.0000000",
            maxAmountB = "200.0000000",
            minPrice = Price(1, 2),
            maxPrice = Price(2, 1)
        )
        val body = op.toOperationBody()
        assertIs<OperationBodyXdr.LiquidityPoolDepositOp>(body)
        assertContentEquals(
            Util.hexToBytes(poolId.lowercase()),
            body.value.liquidityPoolId.value.value
        )
    }

    // ========== LiquidityPoolWithdraw ==========

    @Test
    fun testLiquidityPoolWithdrawRejectsSignedPoolId() {
        val op = LiquidityPoolWithdrawOperation(
            liquidityPoolId = POSITIVE_SIGN_ID,
            amount = "100.0000000",
            minAmountA = "1.0000000",
            minAmountB = "1.0000000"
        )
        val exception = assertFailsWith<IllegalArgumentException> { op.toOperationBody() }
        assertTrue(
            exception.message?.contains("0-9 and a-f") ?: false,
            "Unexpected message: ${exception.message}"
        )
    }

    @Test
    fun testLiquidityPoolWithdrawAcceptsMixedCasePoolId() {
        val poolId = "0A1b2C3d4E5f6789AbCdEf0123456789abcdef0123456789ABCDEF0123456789"
        val op = LiquidityPoolWithdrawOperation(
            liquidityPoolId = poolId,
            amount = "100.0000000",
            minAmountA = "1.0000000",
            minAmountB = "1.0000000"
        )
        val body = op.toOperationBody()
        assertIs<OperationBodyXdr.LiquidityPoolWithdrawOp>(body)
        assertContentEquals(
            Util.hexToBytes(poolId.lowercase()),
            body.value.liquidityPoolId.value.value
        )
    }

    // ========== InvokeHostFunctionOperation.createContract ==========

    @Test
    fun testCreateContractRejectsSignedWasmId() {
        val exception = assertFailsWith<IllegalArgumentException> {
            InvokeHostFunctionOperation.createContract(
                wasmId = NEGATIVE_SIGN_ID,
                address = Address(ACCOUNT_ID),
                salt = ByteArray(32)
            )
        }
        assertTrue(
            exception.message?.contains("0-9 and a-f") ?: false,
            "Unexpected message: ${exception.message}"
        )
    }

    @Test
    fun testCreateContractRejectsUnicodeDigitWasmId() {
        assertFailsWith<IllegalArgumentException> {
            InvokeHostFunctionOperation.createContract(
                wasmId = FULLWIDTH_ID,
                address = Address(ACCOUNT_ID),
                salt = ByteArray(32)
            )
        }
        assertFailsWith<IllegalArgumentException> {
            InvokeHostFunctionOperation.createContract(
                wasmId = ARABIC_INDIC_ID,
                address = Address(ACCOUNT_ID),
                salt = ByteArray(32)
            )
        }
    }

    @Test
    fun testCreateContractAcceptsMixedCaseWasmId() {
        val wasmId = "0A1b2C3d4E5f6789AbCdEf0123456789abcdef0123456789ABCDEF0123456789"
        val op = InvokeHostFunctionOperation.createContract(
            wasmId = wasmId,
            address = Address(ACCOUNT_ID),
            salt = ByteArray(32)
        )
        val hostFunction = op.hostFunction
        assertIs<HostFunctionXdr.CreateContract>(hostFunction)
        val executable = hostFunction.value.executable
        assertIs<ContractExecutableXdr.WasmHash>(executable)
        assertContentEquals(Util.hexToBytes(wasmId.lowercase()), executable.value.value)
    }
}
