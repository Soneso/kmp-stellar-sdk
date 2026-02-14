package com.soneso.stellar.sdk.unitTests.xdr

import com.soneso.stellar.sdk.xdr.*
import com.soneso.stellar.sdk.unitTests.xdr.XdrTestHelpers.assertXdrRoundTrip
import com.soneso.stellar.sdk.unitTests.xdr.XdrTestHelpers.hashXdr
import com.soneso.stellar.sdk.unitTests.xdr.XdrTestHelpers.uint32
import com.soneso.stellar.sdk.unitTests.xdr.XdrTestHelpers.uint256Xdr
import com.soneso.stellar.sdk.unitTests.xdr.XdrTestHelpers.int64
import com.soneso.stellar.sdk.unitTests.xdr.XdrTestHelpers.assetNative
import com.soneso.stellar.sdk.unitTests.xdr.XdrTestHelpers.assetAlphaNum4
import com.soneso.stellar.sdk.unitTests.xdr.XdrTestHelpers.accountId
import com.soneso.stellar.sdk.unitTests.xdr.XdrTestHelpers.sequenceNumber
import com.soneso.stellar.sdk.unitTests.xdr.XdrTestHelpers.poolId
import com.soneso.stellar.sdk.unitTests.xdr.XdrTestHelpers.contractId
import kotlin.test.Test

class XdrSorobanAuthTest {

    // ========== Enum types ==========

    @Test fun testHostFunctionTypeEnum() { for (e in HostFunctionTypeXdr.entries) assertXdrRoundTrip(e, { v, w -> v.encode(w) }, { r -> HostFunctionTypeXdr.decode(r) }) }
    @Test fun testContractExecutableTypeEnum() { for (e in ContractExecutableTypeXdr.entries) assertXdrRoundTrip(e, { v, w -> v.encode(w) }, { r -> ContractExecutableTypeXdr.decode(r) }) }
    @Test fun testContractIDPreimageTypeEnum() { for (e in ContractIDPreimageTypeXdr.entries) assertXdrRoundTrip(e, { v, w -> v.encode(w) }, { r -> ContractIDPreimageTypeXdr.decode(r) }) }
    @Test fun testSorobanAuthorizedFunctionTypeEnum() { for (e in SorobanAuthorizedFunctionTypeXdr.entries) assertXdrRoundTrip(e, { v, w -> v.encode(w) }, { r -> SorobanAuthorizedFunctionTypeXdr.decode(r) }) }
    @Test fun testSorobanCredentialsTypeEnum() { for (e in SorobanCredentialsTypeXdr.entries) assertXdrRoundTrip(e, { v, w -> v.encode(w) }, { r -> SorobanCredentialsTypeXdr.decode(r) }) }
    @Test fun testSCAddressTypeEnum() { for (e in SCAddressTypeXdr.entries) assertXdrRoundTrip(e, { v, w -> v.encode(w) }, { r -> SCAddressTypeXdr.decode(r) }) }
    @Test fun testSCErrorTypeEnum() { for (e in SCErrorTypeXdr.entries) assertXdrRoundTrip(e, { v, w -> v.encode(w) }, { r -> SCErrorTypeXdr.decode(r) }) }

    // ========== InvokeContractArgs ==========

    private fun scAddressContract() = SCAddressXdr.ContractId(contractId())

    @Test
    fun testInvokeContractArgs() {
        val args = InvokeContractArgsXdr(
            contractAddress = scAddressContract(),
            functionName = SCSymbolXdr("transfer"),
            args = listOf(SCValXdr.B(true), SCValXdr.U32(uint32(100u)))
        )
        assertXdrRoundTrip(args, { v, w -> v.encode(w) }, { r -> InvokeContractArgsXdr.decode(r) })
    }

    @Test
    fun testInvokeContractArgsEmpty() {
        val args = InvokeContractArgsXdr(
            contractAddress = scAddressContract(),
            functionName = SCSymbolXdr("init"),
            args = emptyList()
        )
        assertXdrRoundTrip(args, { v, w -> v.encode(w) }, { r -> InvokeContractArgsXdr.decode(r) })
    }

    // ========== ContractIDPreimage ==========

    @Test
    fun testContractIDPreimageFromAddress() {
        val preimage = ContractIDPreimageXdr.FromAddress(
            ContractIDPreimageFromAddressXdr(address = scAddressContract(), salt = uint256Xdr())
        )
        assertXdrRoundTrip(preimage, { v, w -> v.encode(w) }, { r -> ContractIDPreimageXdr.decode(r) })
    }

    @Test
    fun testContractIDPreimageFromAsset() {
        assertXdrRoundTrip(ContractIDPreimageXdr.FromAsset(assetNative()), { v, w -> v.encode(w) }, { r -> ContractIDPreimageXdr.decode(r) })
    }

    @Test
    fun testContractIDPreimageFromAddressStruct() {
        val fromAddr = ContractIDPreimageFromAddressXdr(
            address = SCAddressXdr.AccountId(accountId()),
            salt = uint256Xdr()
        )
        assertXdrRoundTrip(fromAddr, { v, w -> v.encode(w) }, { r -> ContractIDPreimageFromAddressXdr.decode(r) })
    }

    // ========== CreateContractArgs ==========

    @Test
    fun testCreateContractArgs() {
        val args = CreateContractArgsXdr(
            contractIdPreimage = ContractIDPreimageXdr.FromAsset(assetAlphaNum4()),
            executable = ContractExecutableXdr.Void
        )
        assertXdrRoundTrip(args, { v, w -> v.encode(w) }, { r -> CreateContractArgsXdr.decode(r) })
    }

    @Test
    fun testCreateContractArgsV2() {
        val args = CreateContractArgsV2Xdr(
            contractIdPreimage = ContractIDPreimageXdr.FromAddress(
                ContractIDPreimageFromAddressXdr(scAddressContract(), uint256Xdr())
            ),
            executable = ContractExecutableXdr.WasmHash(hashXdr()),
            constructorArgs = listOf(SCValXdr.U32(uint32(1u)), SCValXdr.B(false))
        )
        assertXdrRoundTrip(args, { v, w -> v.encode(w) }, { r -> CreateContractArgsV2Xdr.decode(r) })
    }

    @Test
    fun testCreateContractArgsV2EmptyConstructor() {
        val args = CreateContractArgsV2Xdr(
            contractIdPreimage = ContractIDPreimageXdr.FromAsset(assetNative()),
            executable = ContractExecutableXdr.Void,
            constructorArgs = emptyList()
        )
        assertXdrRoundTrip(args, { v, w -> v.encode(w) }, { r -> CreateContractArgsV2Xdr.decode(r) })
    }

    // ========== SorobanAuthorizedFunction ==========

    private fun invokeContractArgs() = InvokeContractArgsXdr(
        contractAddress = scAddressContract(),
        functionName = SCSymbolXdr("mint"),
        args = listOf(SCValXdr.Void(SCValTypeXdr.SCV_VOID))
    )

    @Test
    fun testSorobanAuthorizedFunctionContractFn() {
        assertXdrRoundTrip(SorobanAuthorizedFunctionXdr.ContractFn(invokeContractArgs()), { v, w -> v.encode(w) }, { r -> SorobanAuthorizedFunctionXdr.decode(r) })
    }

    @Test
    fun testSorobanAuthorizedFunctionCreateContract() {
        val fn = SorobanAuthorizedFunctionXdr.CreateContractHostFn(
            CreateContractArgsXdr(
                contractIdPreimage = ContractIDPreimageXdr.FromAsset(assetNative()),
                executable = ContractExecutableXdr.Void
            )
        )
        assertXdrRoundTrip(fn, { v, w -> v.encode(w) }, { r -> SorobanAuthorizedFunctionXdr.decode(r) })
    }

    @Test
    fun testSorobanAuthorizedFunctionCreateContractV2() {
        val fn = SorobanAuthorizedFunctionXdr.CreateContractV2HostFn(
            CreateContractArgsV2Xdr(
                contractIdPreimage = ContractIDPreimageXdr.FromAsset(assetNative()),
                executable = ContractExecutableXdr.Void,
                constructorArgs = emptyList()
            )
        )
        assertXdrRoundTrip(fn, { v, w -> v.encode(w) }, { r -> SorobanAuthorizedFunctionXdr.decode(r) })
    }

    // ========== SorobanAuthorizedInvocation ==========

    @Test
    fun testSorobanAuthorizedInvocationNoSubs() {
        val inv = SorobanAuthorizedInvocationXdr(
            function = SorobanAuthorizedFunctionXdr.ContractFn(invokeContractArgs()),
            subInvocations = emptyList()
        )
        assertXdrRoundTrip(inv, { v, w -> v.encode(w) }, { r -> SorobanAuthorizedInvocationXdr.decode(r) })
    }

    @Test
    fun testSorobanAuthorizedInvocationWithSubs() {
        val sub = SorobanAuthorizedInvocationXdr(
            function = SorobanAuthorizedFunctionXdr.ContractFn(invokeContractArgs()),
            subInvocations = emptyList()
        )
        val inv = SorobanAuthorizedInvocationXdr(
            function = SorobanAuthorizedFunctionXdr.ContractFn(invokeContractArgs()),
            subInvocations = listOf(sub)
        )
        assertXdrRoundTrip(inv, { v, w -> v.encode(w) }, { r -> SorobanAuthorizedInvocationXdr.decode(r) })
    }

    // ========== SorobanCredentials ==========

    @Test
    fun testSorobanCredentialsSourceAccount() {
        assertXdrRoundTrip(SorobanCredentialsXdr.Void, { v, w -> v.encode(w) }, { r -> SorobanCredentialsXdr.decode(r) })
    }

    @Test
    fun testSorobanCredentialsAddress() {
        val cred = SorobanCredentialsXdr.Address(
            SorobanAddressCredentialsXdr(
                address = scAddressContract(),
                nonce = int64(42L),
                signatureExpirationLedger = uint32(100u),
                signature = SCValXdr.Void(SCValTypeXdr.SCV_VOID)
            )
        )
        assertXdrRoundTrip(cred, { v, w -> v.encode(w) }, { r -> SorobanCredentialsXdr.decode(r) })
    }

    @Test
    fun testSorobanAddressCredentials() {
        val cred = SorobanAddressCredentialsXdr(
            address = SCAddressXdr.AccountId(accountId()),
            nonce = int64(123L),
            signatureExpirationLedger = uint32(500u),
            signature = SCValXdr.Map(
                SCMapXdr(listOf(SCMapEntryXdr(SCValXdr.Sym(SCSymbolXdr("key")), SCValXdr.U32(uint32(1u)))))
            )
        )
        assertXdrRoundTrip(cred, { v, w -> v.encode(w) }, { r -> SorobanAddressCredentialsXdr.decode(r) })
    }

    // ========== SorobanAuthorizationEntry ==========

    @Test
    fun testSorobanAuthorizationEntry() {
        val entry = SorobanAuthorizationEntryXdr(
            credentials = SorobanCredentialsXdr.Void,
            rootInvocation = SorobanAuthorizedInvocationXdr(
                function = SorobanAuthorizedFunctionXdr.ContractFn(invokeContractArgs()),
                subInvocations = emptyList()
            )
        )
        assertXdrRoundTrip(entry, { v, w -> v.encode(w) }, { r -> SorobanAuthorizationEntryXdr.decode(r) })
    }

    // ========== SorobanAuthorizationEntries ==========

    @Test
    fun testSorobanAuthorizationEntries() {
        val entries = SorobanAuthorizationEntriesXdr(listOf(
            SorobanAuthorizationEntryXdr(
                credentials = SorobanCredentialsXdr.Void,
                rootInvocation = SorobanAuthorizedInvocationXdr(
                    function = SorobanAuthorizedFunctionXdr.ContractFn(invokeContractArgs()),
                    subInvocations = emptyList()
                )
            )
        ))
        assertXdrRoundTrip(entries, { v, w -> v.encode(w) }, { r -> SorobanAuthorizationEntriesXdr.decode(r) })
    }

    @Test
    fun testSorobanAuthorizationEntriesEmpty() {
        assertXdrRoundTrip(SorobanAuthorizationEntriesXdr(emptyList()), { v, w -> v.encode(w) }, { r -> SorobanAuthorizationEntriesXdr.decode(r) })
    }

    // ========== HashIDPreimage ==========

    @Test
    fun testHashIDPreimageOperationID() {
        val preimage = HashIDPreimageXdr.OperationID(
            HashIDPreimageOperationIDXdr(sourceAccount = accountId(), seqNum = sequenceNumber(100L), opNum = uint32(0u))
        )
        assertXdrRoundTrip(preimage, { v, w -> v.encode(w) }, { r -> HashIDPreimageXdr.decode(r) })
    }

    @Test
    fun testHashIDPreimageRevokeID() {
        val preimage = HashIDPreimageXdr.RevokeID(
            HashIDPreimageRevokeIDXdr(
                sourceAccount = accountId(),
                seqNum = sequenceNumber(200L),
                opNum = uint32(1u),
                liquidityPoolId = poolId(),
                asset = assetNative()
            )
        )
        assertXdrRoundTrip(preimage, { v, w -> v.encode(w) }, { r -> HashIDPreimageXdr.decode(r) })
    }

    @Test
    fun testHashIDPreimageContractID() {
        val preimage = HashIDPreimageXdr.ContractID(
            HashIDPreimageContractIDXdr(
                networkId = hashXdr(),
                contractIdPreimage = ContractIDPreimageXdr.FromAsset(assetNative())
            )
        )
        assertXdrRoundTrip(preimage, { v, w -> v.encode(w) }, { r -> HashIDPreimageXdr.decode(r) })
    }

    @Test
    fun testHashIDPreimageSorobanAuthorization() {
        val preimage = HashIDPreimageXdr.SorobanAuthorization(
            HashIDPreimageSorobanAuthorizationXdr(
                networkId = hashXdr(),
                nonce = int64(99L),
                signatureExpirationLedger = uint32(1000u),
                invocation = SorobanAuthorizedInvocationXdr(
                    function = SorobanAuthorizedFunctionXdr.ContractFn(invokeContractArgs()),
                    subInvocations = emptyList()
                )
            )
        )
        assertXdrRoundTrip(preimage, { v, w -> v.encode(w) }, { r -> HashIDPreimageXdr.decode(r) })
    }
}
