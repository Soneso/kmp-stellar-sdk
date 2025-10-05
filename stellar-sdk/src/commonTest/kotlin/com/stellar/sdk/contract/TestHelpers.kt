package com.stellar.sdk.contract

import com.stellar.sdk.Address
import com.stellar.sdk.scval.Scv
import com.stellar.sdk.rpc.responses.*
import com.stellar.sdk.xdr.*
import com.ionspin.kotlin.bignum.integer.BigInteger

/**
 * Test helpers for creating test data for contract tests.
 */
object TestHelpers {

    fun createDefaultSorobanData(): SorobanTransactionDataXdr {
        return SorobanTransactionDataXdr(
            ext = SorobanTransactionDataExtXdr.Void,
            resources = SorobanResourcesXdr(
                footprint = LedgerFootprintXdr(
                    readOnly = emptyList(),
                    readWrite = emptyList()
                ),
                instructions = Uint32Xdr(0u),
                diskReadBytes = Uint32Xdr(0u),
                writeBytes = Uint32Xdr(0u)
            ),
            resourceFee = Int64Xdr(0L)
        )
    }

    fun createDefaultSimulateResult(resultValue: SCValXdr = Scv.toSymbol("ok")): SimulateTransactionResponse.SimulateHostFunctionResult {
        return SimulateTransactionResponse.SimulateHostFunctionResult(
            auth = emptyList(),
            xdr = resultValue.toXdrBase64()
        )
    }

    fun createReadOnlySimulateResponse(resultValue: SCValXdr = Scv.toSymbol("ok")): SimulateTransactionResponse {
        val result = SimulateTransactionResponse.SimulateHostFunctionResult(
            auth = emptyList(),
            xdr = resultValue.toXdrBase64()
        )

        val sorobanData = SorobanTransactionDataXdr(
            ext = SorobanTransactionDataExtXdr.Void,
            resources = SorobanResourcesXdr(
                footprint = LedgerFootprintXdr(
                    readOnly = emptyList(),
                    readWrite = emptyList() // No writes = read-only
                ),
                instructions = Uint32Xdr(0u),
                diskReadBytes = Uint32Xdr(0u),
                writeBytes = Uint32Xdr(0u)
            ),
            resourceFee = Int64Xdr(0L)
        )

        return SimulateTransactionResponse(
            error = null,
            transactionData = sorobanData.toXdrBase64(),
            minResourceFee = 100L,
            events = null,
            results = listOf(result),
            restorePreamble = null,
            stateChanges = null,
            latestLedger = 1000L
        )
    }

    fun createWriteCallSimulateResponse(contractId: String): SimulateTransactionResponse {
        val result = SimulateTransactionResponse.SimulateHostFunctionResult(
            auth = emptyList(),
            xdr = Scv.toSymbol("ok").toXdrBase64()
        )

        val sorobanData = SorobanTransactionDataXdr(
            ext = SorobanTransactionDataExtXdr.Void,
            resources = SorobanResourcesXdr(
                footprint = LedgerFootprintXdr(
                    readOnly = emptyList(),
                    readWrite = listOf( // Has writes = write call
                        LedgerKeyXdr.ContractData(
                            LedgerKeyContractDataXdr(
                                contract = Address(contractId).toSCAddress(),
                                key = Scv.toSymbol("state"),
                                durability = ContractDataDurabilityXdr.PERSISTENT
                            )
                        )
                    )
                ),
                instructions = Uint32Xdr(1000u),
                diskReadBytes = Uint32Xdr(100u),
                writeBytes = Uint32Xdr(50u)
            ),
            resourceFee = Int64Xdr(1000L)
        )

        return SimulateTransactionResponse(
            error = null,
            transactionData = sorobanData.toXdrBase64(),
            minResourceFee = 1000L,
            events = null,
            results = listOf(result),
            restorePreamble = null,
            stateChanges = null,
            latestLedger = 1000L
        )
    }

    fun createAuthSimulateResponse(contractId: String, authAccountId: String): SimulateTransactionResponse {
        val authEntry = SorobanAuthorizationEntryXdr(
            credentials = SorobanCredentialsXdr.Address(
                SorobanAddressCredentialsXdr(
                    address = Address(authAccountId).toSCAddress(),
                    nonce = Int64Xdr(123456L),
                    signatureExpirationLedger = Uint32Xdr(0u),
                    signature = Scv.toVoid() // Unsigned
                )
            ),
            rootInvocation = SorobanAuthorizedInvocationXdr(
                function = SorobanAuthorizedFunctionXdr.ContractFn(
                    InvokeContractArgsXdr(
                        contractAddress = Address(contractId).toSCAddress(),
                        functionName = SCSymbolXdr("test"),
                        args = emptyList()
                    )
                ),
                subInvocations = emptyList()
            )
        )

        val result = SimulateTransactionResponse.SimulateHostFunctionResult(
            auth = listOf(authEntry.toXdrBase64()),
            xdr = Scv.toSymbol("ok").toXdrBase64()
        )

        val sorobanData = SorobanTransactionDataXdr(
            ext = SorobanTransactionDataExtXdr.Void,
            resources = SorobanResourcesXdr(
                footprint = LedgerFootprintXdr(
                    readOnly = emptyList(),
                    readWrite = listOf(
                        LedgerKeyXdr.ContractData(
                            LedgerKeyContractDataXdr(
                                contract = Address(contractId).toSCAddress(),
                                key = Scv.toSymbol("state"),
                                durability = ContractDataDurabilityXdr.PERSISTENT
                            )
                        )
                    )
                ),
                instructions = Uint32Xdr(1000u),
                diskReadBytes = Uint32Xdr(100u),
                writeBytes = Uint32Xdr(50u)
            ),
            resourceFee = Int64Xdr(1000L)
        )

        return SimulateTransactionResponse(
            error = null,
            transactionData = sorobanData.toXdrBase64(),
            minResourceFee = 1000L,
            events = null,
            results = listOf(result),
            restorePreamble = null,
            stateChanges = null,
            latestLedger = 1000L
        )
    }

    fun createSuccessGetResponse(returnValue: SCValXdr = Scv.toSymbol("success")): GetTransactionResponse {
        val sorobanMeta = SorobanTransactionMetaXdr(
            ext = SorobanTransactionMetaExtXdr.Void,
            events = emptyList(),
            returnValue = returnValue,
            diagnosticEvents = emptyList()
        )

        val meta = TransactionMetaXdr.V3(
            TransactionMetaV3Xdr(
                ext = ExtensionPointXdr.Void,
                txChangesBefore = LedgerEntryChangesXdr(emptyList()),
                operations = emptyList(),
                txChangesAfter = LedgerEntryChangesXdr(emptyList()),
                sorobanMeta = sorobanMeta
            )
        )

        return GetTransactionResponse(
            status = GetTransactionStatus.SUCCESS,
            latestLedger = 1002L,
            latestLedgerCloseTime = 1234567900L,
            oldestLedger = 900L,
            oldestLedgerCloseTime = 1234567000L,
            applicationOrder = 1,
            feeBump = false,
            envelopeXdr = null,
            resultXdr = null,
            resultMetaXdr = meta.toXdrBase64(),
            ledger = 1001L,
            createdAt = 1234567890L
        )
    }

    fun scAddressFrom(accountId: String): SCAddressXdr {
        return Address(accountId).toSCAddress()
    }

    fun scInt128From(value: Long): SCValXdr {
        return Scv.toInt128(BigInteger(value))
    }
}
