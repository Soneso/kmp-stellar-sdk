package com.soneso.stellar.sdk.unitTests

import com.soneso.stellar.sdk.*
import com.soneso.stellar.sdk.xdr.*
import kotlinx.coroutines.test.runTest
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.*

/**
 * Unit tests for Transaction class covering:
 * - Construction, validation, and properties
 * - XDR envelope conversion (V0/V1)
 * - Soroban transaction detection
 * - Roundtrip from/to envelope XDR
 * - Equality and hashCode
 */
class TransactionTest {

    companion object {
        const val ACCOUNT_ID = "GADBBY4WFXKKFJ7CMTG3J5YAUXMQDBILRQ6W3U5IWN5TQFZU4MWZ5T4K"
        const val ACCOUNT_B = "GA7QYNF7SOWQ3GLR2BGMZEHXAVIRZA4KVWLTJJFC7MGXUA74P7UJVSGZ"
        const val SECRET = "SAEZSI6DY7AXJFIYA4PM6SIBNEYYXIEM2MSOTHFGKHDW32MBQ7KVO6EN"
        val NETWORK = Network.TESTNET
    }

    private fun buildSimpleTransaction(
        fee: Long = 100,
        seqNum: Long = 100,
        operations: List<Operation> = listOf(PaymentOperation(ACCOUNT_B, AssetTypeNative, "10.0000000")),
        memo: Memo = MemoNone,
        preconditions: TransactionPreconditions = TransactionPreconditions(timeBounds = TimeBounds(0, 0)),
        sorobanData: SorobanTransactionDataXdr? = null
    ): Transaction {
        val account = Account(ACCOUNT_ID, seqNum)
        val builder = TransactionBuilder(account, NETWORK)
            .setBaseFee(fee)
            .addPreconditions(preconditions)
        operations.forEach { builder.addOperation(it) }
        if (memo !== MemoNone) {
            builder.addMemo(memo)
        }
        if (sorobanData != null) {
            builder.setSorobanData(sorobanData)
        }
        return builder.build()
    }

    // ========== Basic Construction ==========
    @Test
    fun testTransactionProperties() {
        val tx = buildSimpleTransaction()
        assertEquals(ACCOUNT_ID, tx.sourceAccount)
        assertEquals(100L, tx.fee)
        assertTrue(tx.operations.isNotEmpty())
        assertTrue(tx.signatures.isEmpty())
    }

    @Test
    fun testTransactionMaxOperations() {
        val ops = (1..100).map {
            PaymentOperation(ACCOUNT_B, AssetTypeNative, "1.0000000")
        }
        val tx = buildSimpleTransaction(operations = ops)
        assertEquals(100, tx.operations.size)
    }

    @Test
    fun testTransactionEmptyOperationsThrows() {
        assertFailsWith<IllegalArgumentException> {
            buildSimpleTransaction(operations = emptyList())
        }
    }

    @Test
    fun testTransactionTooManyOperationsThrows() {
        assertFailsWith<IllegalArgumentException> {
            buildSimpleTransaction(
                operations = (1..101).map {
                    PaymentOperation(ACCOUNT_B, AssetTypeNative, "1.0000000")
                }
            )
        }
    }

    @Test
    fun testTransactionWithMemo() {
        val tx = buildSimpleTransaction(memo = MemoText("Hello"))
        assertEquals(MemoText("Hello"), tx.memo)
    }

    @Test
    fun testGetTimeBounds() {
        val tb = TimeBounds(100, 200)
        val tx = buildSimpleTransaction(preconditions = TransactionPreconditions(timeBounds = tb))
        val retrieved = tx.getTimeBounds()
        assertNotNull(retrieved)
        assertEquals(100L, retrieved.minTime)
        assertEquals(200L, retrieved.maxTime)
    }

    @Test
    fun testGetTimeBoundsNull() {
        val tx = buildSimpleTransaction(preconditions = TransactionPreconditions())
        assertNull(tx.getTimeBounds())
    }

    // ========== Soroban Detection ==========
    @Test
    fun testIsSorobanTransactionInvokeHostFunction() {
        val op = InvokeHostFunctionOperation.invokeContractFunction(
            contractAddress = "CDCYWK73YTYFJZZSJ5V7EDFNHYBG4QN3VUNG2IGD27KJDDPNCZKBCBXK",
            functionName = "test",
            parameters = emptyList()
        )
        val tx = buildSimpleTransaction(operations = listOf(op))
        assertTrue(tx.isSorobanTransaction())
    }

    @Test
    fun testIsSorobanTransactionExtendTTL() {
        val op = ExtendFootprintTTLOperation(10000)
        val tx = buildSimpleTransaction(operations = listOf(op))
        assertTrue(tx.isSorobanTransaction())
    }

    @Test
    fun testIsSorobanTransactionRestoreFootprint() {
        val op = RestoreFootprintOperation()
        val tx = buildSimpleTransaction(operations = listOf(op))
        assertTrue(tx.isSorobanTransaction())
    }

    @Test
    fun testIsNotSorobanTransaction() {
        val tx = buildSimpleTransaction()
        assertFalse(tx.isSorobanTransaction())
    }

    @Test
    fun testIsNotSorobanTransactionMultipleOps() {
        val op1 = InvokeHostFunctionOperation.invokeContractFunction(
            contractAddress = "CDCYWK73YTYFJZZSJ5V7EDFNHYBG4QN3VUNG2IGD27KJDDPNCZKBCBXK",
            functionName = "test",
            parameters = emptyList()
        )
        val op2 = PaymentOperation(ACCOUNT_B, AssetTypeNative, "10.0000000")
        val tx = buildSimpleTransaction(operations = listOf(op1, op2))
        assertFalse(tx.isSorobanTransaction())
    }

    @Test
    fun testIsSorobanTransactionViaSorobanDataOnNonSorobanOperation() {
        // A single non-Soroban operation is still treated as a Soroban transaction when
        // Soroban transaction data is attached (e.g. after simulation attaches resource data).
        val sorobanData = SorobanTransactionDataXdr(
            ext = SorobanTransactionDataExtXdr.Void,
            resources = SorobanResourcesXdr(
                footprint = LedgerFootprintXdr(readOnly = emptyList(), readWrite = emptyList()),
                instructions = Uint32Xdr(1000u),
                diskReadBytes = Uint32Xdr(0u),
                writeBytes = Uint32Xdr(0u)
            ),
            resourceFee = Int64Xdr(100L)
        )
        val tx = buildSimpleTransaction(sorobanData = sorobanData)
        assertTrue(tx.isSorobanTransaction())
    }

    @Test
    fun testIsNotSorobanTransactionWithoutSorobanData() {
        val tx = buildSimpleTransaction(sorobanData = null)
        assertFalse(tx.isSorobanTransaction())
    }

    // ========== XDR Envelope Roundtrip ==========
    @Test
    fun testEnvelopeXdrBase64Roundtrip() = runTest {
        val keypair = KeyPair.fromSecretSeed(SECRET)
        val account = Account(keypair.getAccountId(), 100L)
        val tx = TransactionBuilder(account, NETWORK)
            .setBaseFee(100)
            .addOperation(PaymentOperation(ACCOUNT_B, AssetTypeNative, "10.0000000"))
            .addPreconditions(TransactionPreconditions(timeBounds = TimeBounds(0, 0)))
            .build()
        tx.sign(keypair)

        val base64 = tx.toEnvelopeXdrBase64()
        assertNotNull(base64)
        assertTrue(base64.isNotBlank())

        val restored = Transaction.fromEnvelopeXdr(base64, NETWORK)
        assertEquals(tx.sourceAccount, restored.sourceAccount)
        assertEquals(tx.fee, restored.fee)
        assertEquals(tx.sequenceNumber, restored.sequenceNumber)
        assertEquals(tx.operations.size, restored.operations.size)
        assertEquals(1, restored.signatures.size)
    }

    @Test
    fun testEnvelopeXdrBytesRoundtrip() = runTest {
        val keypair = KeyPair.fromSecretSeed(SECRET)
        val account = Account(keypair.getAccountId(), 100L)
        val tx = TransactionBuilder(account, NETWORK)
            .setBaseFee(100)
            .addOperation(PaymentOperation(ACCOUNT_B, AssetTypeNative, "10.0000000"))
            .addPreconditions(TransactionPreconditions(timeBounds = TimeBounds(0, 0)))
            .build()

        val envelopeXdr = tx.toEnvelopeXdr()
        val writer = XdrWriter()
        envelopeXdr.encode(writer)
        val bytes = writer.toByteArray()

        val restored = Transaction.fromEnvelopeXdr(bytes, NETWORK)
        assertEquals(tx.sourceAccount, restored.sourceAccount)
    }

    @Test
    fun testFromEnvelopeXdrInvalidString() {
        assertFailsWith<IllegalArgumentException> {
            Transaction.fromEnvelopeXdr("not-valid-base64!!!", NETWORK)
        }
    }

    // ========== Signing ==========
    @Test
    fun testTransactionSign() = runTest {
        val keypair = KeyPair.fromSecretSeed(SECRET)
        val account = Account(keypair.getAccountId(), 100L)
        val tx = TransactionBuilder(account, NETWORK)
            .setBaseFee(100)
            .addOperation(PaymentOperation(ACCOUNT_B, AssetTypeNative, "10.0000000"))
            .addPreconditions(TransactionPreconditions(timeBounds = TimeBounds(0, 0)))
            .build()

        assertTrue(tx.signatures.isEmpty())
        tx.sign(keypair)
        assertEquals(1, tx.signatures.size)
    }

    @Test
    fun testTransactionMultipleSignatures() = runTest {
        val keypair1 = KeyPair.fromSecretSeed(SECRET)
        val keypair2 = KeyPair.fromSecretSeed("SCH27VUZZ6UAKB67BDNF6FA42YMBMQCBKXWGMFD5TZ6S5ZZCZFLRXKHS")
        val account = Account(keypair1.getAccountId(), 100L)
        val tx = TransactionBuilder(account, NETWORK)
            .setBaseFee(100)
            .addOperation(PaymentOperation(ACCOUNT_B, AssetTypeNative, "10.0000000"))
            .addPreconditions(TransactionPreconditions(timeBounds = TimeBounds(0, 0)))
            .build()

        tx.sign(keypair1)
        tx.sign(keypair2)
        assertEquals(2, tx.signatures.size)
    }

    @Test
    fun testTransactionHashHex() = runTest {
        val keypair = KeyPair.fromSecretSeed(SECRET)
        val account = Account(keypair.getAccountId(), 100L)
        val tx = TransactionBuilder(account, NETWORK)
            .setBaseFee(100)
            .addOperation(PaymentOperation(ACCOUNT_B, AssetTypeNative, "10.0000000"))
            .addPreconditions(TransactionPreconditions(timeBounds = TimeBounds(0, 0)))
            .build()

        val hash = tx.hashHex()
        assertEquals(64, hash.length)
        assertTrue(hash.all { it in '0'..'9' || it in 'a'..'f' })
    }

    @Test
    fun testSignHashX() = runTest {
        val keypair = KeyPair.fromSecretSeed(SECRET)
        val account = Account(keypair.getAccountId(), 100L)
        val tx = TransactionBuilder(account, NETWORK)
            .setBaseFee(100)
            .addOperation(PaymentOperation(ACCOUNT_B, AssetTypeNative, "10.0000000"))
            .addPreconditions(TransactionPreconditions(timeBounds = TimeBounds(0, 0)))
            .build()

        val preimage = "test preimage".encodeToByteArray()
        tx.signHashX(preimage)
        assertEquals(1, tx.signatures.size)
    }

    // ========== Equality ==========
    @Test
    fun testTransactionEquality() {
        val tx1 = buildSimpleTransaction()
        val tx2 = buildSimpleTransaction()
        assertEquals(tx1, tx2)
        assertEquals(tx1.hashCode(), tx2.hashCode())
    }

    @Test
    fun testTransactionInequality() {
        val tx1 = buildSimpleTransaction(fee = 100)
        val tx2 = buildSimpleTransaction(fee = 200)
        assertNotEquals(tx1, tx2)
    }

    @Test
    fun testTransactionInequalityPerField() {
        val base = buildSimpleTransaction()

        val differentSeqNum = buildSimpleTransaction(seqNum = 999)
        assertNotEquals(base, differentSeqNum)

        val differentOps = buildSimpleTransaction(
            operations = listOf(PaymentOperation(ACCOUNT_ID, AssetTypeNative, "1.0000000"))
        )
        assertNotEquals(base, differentOps)

        val differentMemo = buildSimpleTransaction(memo = MemoText("different"))
        assertNotEquals(base, differentMemo)

        val differentPreconditions = buildSimpleTransaction(
            preconditions = TransactionPreconditions(timeBounds = TimeBounds(1, 2))
        )
        assertNotEquals(base, differentPreconditions)

        val differentSourceAccount = Account(ACCOUNT_B, 100).let { account ->
            TransactionBuilder(account, NETWORK)
                .setBaseFee(100)
                .addPreconditions(TransactionPreconditions(timeBounds = TimeBounds(0, 0)))
                .addOperation(PaymentOperation(ACCOUNT_B, AssetTypeNative, "10.0000000"))
                .build()
        }
        assertNotEquals(base, differentSourceAccount)

        val differentNetwork = Account(ACCOUNT_ID, 100).let { account ->
            TransactionBuilder(account, Network.PUBLIC)
                .setBaseFee(100)
                .addPreconditions(TransactionPreconditions(timeBounds = TimeBounds(0, 0)))
                .addOperation(PaymentOperation(ACCOUNT_B, AssetTypeNative, "10.0000000"))
                .build()
        }
        assertNotEquals(base, differentNetwork)
    }

    @Test
    fun testTransactionEqualsReflexiveNullAndDifferentType() {
        val tx = buildSimpleTransaction()
        assertEquals(tx, tx)
        assertFalse(tx.equals(null))
        assertFalse(tx.equals("not a transaction"))
    }

    @Test
    fun testTransactionToString() {
        val tx = buildSimpleTransaction()
        val str = tx.toString()
        assertTrue(str.contains("Transaction"))
        assertTrue(str.contains(ACCOUNT_ID))
    }

    // ========== FeeBump round-trip ==========
    @Test
    fun testFeeBumpEnvelopeRoundtrip() = runTest {
        val keypair = KeyPair.fromSecretSeed(SECRET)
        val account = Account(keypair.getAccountId(), 100L)
        val inner = TransactionBuilder(account, NETWORK)
            .setBaseFee(100)
            .addOperation(PaymentOperation(ACCOUNT_B, AssetTypeNative, "10.0000000"))
            .addPreconditions(TransactionPreconditions(timeBounds = TimeBounds(0, 0)))
            .build()
        inner.sign(keypair)

        val feeBump = FeeBumpTransaction.createWithBaseFee(
            feeSource = keypair.getAccountId(),
            baseFee = 200,
            innerTransaction = inner
        )
        feeBump.sign(keypair)

        val base64 = feeBump.toEnvelopeXdrBase64()
        assertNotNull(base64)

        val restored = FeeBumpTransaction.fromEnvelopeXdrBase64(base64, NETWORK)
        assertEquals(feeBump.feeSource, restored.feeSource)
        assertEquals(feeBump.fee, restored.fee)
        assertEquals(inner.sourceAccount, restored.innerTransaction.sourceAccount)
    }

    // ========== AbstractTransaction.fromEnvelopeXdr dispatches correctly ==========
    @Test
    fun testAbstractTransactionFromEnvelopeXdrReturnsTransaction() = runTest {
        val keypair = KeyPair.fromSecretSeed(SECRET)
        val account = Account(keypair.getAccountId(), 100L)
        val tx = TransactionBuilder(account, NETWORK)
            .setBaseFee(100)
            .addOperation(PaymentOperation(ACCOUNT_B, AssetTypeNative, "10.0000000"))
            .addPreconditions(TransactionPreconditions(timeBounds = TimeBounds(0, 0)))
            .build()

        val base64 = tx.toEnvelopeXdrBase64()
        val restored = AbstractTransaction.fromEnvelopeXdr(base64, NETWORK)
        assertTrue(restored is Transaction)
    }

    @Test
    fun testAbstractTransactionFromEnvelopeXdrReturnsFeeBump() = runTest {
        val keypair = KeyPair.fromSecretSeed(SECRET)
        val account = Account(keypair.getAccountId(), 100L)
        val inner = TransactionBuilder(account, NETWORK)
            .setBaseFee(100)
            .addOperation(PaymentOperation(ACCOUNT_B, AssetTypeNative, "10.0000000"))
            .addPreconditions(TransactionPreconditions(timeBounds = TimeBounds(0, 0)))
            .build()
        inner.sign(keypair)

        val feeBump = FeeBumpTransaction.createWithBaseFee(
            feeSource = keypair.getAccountId(),
            baseFee = 200,
            innerTransaction = inner
        )
        feeBump.sign(keypair)

        val base64 = feeBump.toEnvelopeXdrBase64()
        val restored = AbstractTransaction.fromEnvelopeXdr(base64, NETWORK)
        assertTrue(restored is FeeBumpTransaction)
    }

    // ========== TransactionBuilder timeout ==========
    @Test
    fun testTransactionBuilderTimeout() {
        val account = Account(ACCOUNT_ID, 100L)
        val tx = TransactionBuilder(account, NETWORK)
            .setBaseFee(100)
            .addOperation(PaymentOperation(ACCOUNT_B, AssetTypeNative, "10.0000000"))
            .setTimeout(300)
            .build()
        assertNotNull(tx.getTimeBounds())
        assertTrue(tx.getTimeBounds()!!.maxTime > 0)
    }

    @Test
    fun testTransactionBuilderInfiniteTimeout() {
        val account = Account(ACCOUNT_ID, 100L)
        val tx = TransactionBuilder(account, NETWORK)
            .setBaseFee(100)
            .addOperation(PaymentOperation(ACCOUNT_B, AssetTypeNative, "10.0000000"))
            .setTimeout(0)
            .build()
        assertNotNull(tx.getTimeBounds())
        assertEquals(0L, tx.getTimeBounds()!!.maxTime)
    }

    @Test
    fun testTransactionBuilderCannotSetBothTimeoutAndTimeBounds() {
        val account = Account(ACCOUNT_ID, 100L)
        assertFailsWith<IllegalStateException> {
            TransactionBuilder(account, NETWORK)
                .setBaseFee(100)
                .addOperation(PaymentOperation(ACCOUNT_B, AssetTypeNative, "10.0000000"))
                .addPreconditions(TransactionPreconditions(timeBounds = TimeBounds(0, 100)))
                .setTimeout(300)
                .build()
        }
    }

    @Test
    fun testTransactionBuilderFeeRequired() {
        val account = Account(ACCOUNT_ID, 100L)
        assertFailsWith<IllegalStateException> {
            TransactionBuilder(account, NETWORK)
                .addOperation(PaymentOperation(ACCOUNT_B, AssetTypeNative, "10.0000000"))
                .addPreconditions(TransactionPreconditions(timeBounds = TimeBounds(0, 0)))
                .build()
        }
    }

    @Test
    fun testTransactionBuilderMinBaseFee() {
        val account = Account(ACCOUNT_ID, 100L)
        assertFailsWith<IllegalArgumentException> {
            TransactionBuilder(account, NETWORK)
                .setBaseFee(50) // Below MIN_BASE_FEE
        }
    }

    @Test
    fun testTransactionBuilderOperationsCount() {
        val account = Account(ACCOUNT_ID, 100L)
        val builder = TransactionBuilder(account, NETWORK)
            .setBaseFee(100)
        assertEquals(0, builder.getOperationsCount())
        builder.addOperation(PaymentOperation(ACCOUNT_B, AssetTypeNative, "10.0000000"))
        assertEquals(1, builder.getOperationsCount())
    }

    @Test
    fun testTransactionBuilderAddMultipleOperations() {
        val account = Account(ACCOUNT_ID, 100L)
        val ops = listOf(
            PaymentOperation(ACCOUNT_B, AssetTypeNative, "10.0000000"),
            PaymentOperation(ACCOUNT_B, AssetTypeNative, "20.0000000")
        )
        val builder = TransactionBuilder(account, NETWORK)
            .setBaseFee(100)
            .addOperations(ops)
            .addPreconditions(TransactionPreconditions(timeBounds = TimeBounds(0, 0)))
        assertEquals(2, builder.getOperationsCount())
    }

    @Test
    fun testTransactionBuilderDuplicateMemoThrows() {
        val account = Account(ACCOUNT_ID, 100L)
        assertFailsWith<IllegalStateException> {
            TransactionBuilder(account, NETWORK)
                .addMemo(MemoText("Hello"))
                .addMemo(MemoText("World"))
        }
    }

    @Test
    fun testTransactionBuilderNegativeTimeoutThrows() {
        val account = Account(ACCOUNT_ID, 100L)
        assertFailsWith<IllegalArgumentException> {
            TransactionBuilder(account, NETWORK)
                .setTimeout(-1)
        }
    }

    // ========== Constructor validation (internal constructor, friend access) ==========

    @Test
    fun testNegativeFeeThrows() {
        val exception = assertFailsWith<IllegalArgumentException> {
            Transaction(
                sourceAccount = ACCOUNT_ID,
                fee = -1L,
                sequenceNumber = 100L,
                operations = listOf(PaymentOperation(ACCOUNT_B, AssetTypeNative, "10.0000000")),
                memo = MemoNone,
                preconditions = TransactionPreconditions(),
                sorobanData = null,
                network = NETWORK
            )
        }
        assertTrue(exception.message!!.contains("Fee must be non-negative"))
    }

    @Test
    fun testNegativeSequenceNumberThrows() {
        val exception = assertFailsWith<IllegalArgumentException> {
            Transaction(
                sourceAccount = ACCOUNT_ID,
                fee = 100L,
                sequenceNumber = -1L,
                operations = listOf(PaymentOperation(ACCOUNT_B, AssetTypeNative, "10.0000000")),
                memo = MemoNone,
                preconditions = TransactionPreconditions(),
                sorobanData = null,
                network = NETWORK
            )
        }
        assertTrue(exception.message!!.contains("Sequence number must be non-negative"))
    }

    // ========== V0 envelope round trip ==========

    @OptIn(ExperimentalEncodingApi::class)
    @Test
    fun testFromEnvelopeXdrV0AndBackPreservesV0Envelope() = runTest {
        val keypair = KeyPair.fromSecretSeed(SECRET)

        val v0Tx = TransactionV0Xdr(
            sourceAccountEd25519 = Uint256Xdr(keypair.getPublicKey()),
            fee = Uint32Xdr(100u),
            seqNum = SequenceNumberXdr(Int64Xdr(101L)),
            timeBounds = TimeBoundsXdr(TimePointXdr(Uint64Xdr(0UL)), TimePointXdr(Uint64Xdr(0UL))),
            memo = MemoXdr.Void,
            operations = listOf(PaymentOperation(ACCOUNT_B, AssetTypeNative, "10.0000000").toXdr()),
            ext = TransactionV0ExtXdr.Void
        )
        val v0Envelope = TransactionEnvelopeXdr.V0(
            TransactionV0EnvelopeXdr(tx = v0Tx, signatures = emptyList())
        )
        val writer = XdrWriter()
        v0Envelope.encode(writer)
        val base64 = Base64.encode(writer.toByteArray())

        val restored = Transaction.fromEnvelopeXdr(base64, NETWORK)

        assertEquals(keypair.getAccountId(), restored.sourceAccount)
        assertEquals(100L, restored.fee)
        assertEquals(101L, restored.sequenceNumber)

        // Re-serializing must keep the V0 envelope shape (exercises Transaction.toV0Xdr()).
        val restoredEnvelopeXdr = restored.toEnvelopeXdr()
        assertTrue(restoredEnvelopeXdr is TransactionEnvelopeXdr.V0)
        val roundTrippedV0 = (restoredEnvelopeXdr as TransactionEnvelopeXdr.V0).value.tx
        assertEquals(v0Tx.fee.value, roundTrippedV0.fee.value)
        assertEquals(v0Tx.seqNum.value.value, roundTrippedV0.seqNum.value.value)
    }

    @OptIn(ExperimentalEncodingApi::class)
    @Test
    fun testFromEnvelopeXdrV0WithoutTimeBoundsCarriesExistingSignatures() = runTest {
        val keypair = KeyPair.fromSecretSeed(SECRET)
        val decoratedSig = keypair.signDecorated("arbitrary payload".encodeToByteArray())

        val v0Tx = TransactionV0Xdr(
            sourceAccountEd25519 = Uint256Xdr(keypair.getPublicKey()),
            fee = Uint32Xdr(100u),
            seqNum = SequenceNumberXdr(Int64Xdr(101L)),
            timeBounds = null,
            memo = MemoXdr.Void,
            operations = listOf(PaymentOperation(ACCOUNT_B, AssetTypeNative, "10.0000000").toXdr()),
            ext = TransactionV0ExtXdr.Void
        )
        val v0Envelope = TransactionEnvelopeXdr.V0(
            TransactionV0EnvelopeXdr(tx = v0Tx, signatures = listOf(decoratedSig.toXdr()))
        )
        val writer = XdrWriter()
        v0Envelope.encode(writer)
        val base64 = Base64.encode(writer.toByteArray())

        val restored = Transaction.fromEnvelopeXdr(base64, NETWORK)

        assertNull(restored.getTimeBounds())
        assertEquals(1, restored.signatures.size)
        assertEquals(decoratedSig, restored.signatures[0])
    }

    @Test
    fun testToEnvelopeXdrInvalidEnvelopeTypeThrows() {
        val tx = buildSimpleTransaction()
        tx.envelopeType = EnvelopeTypeXdr.ENVELOPE_TYPE_TX_FEE_BUMP

        val exception = assertFailsWith<IllegalStateException> {
            tx.toEnvelopeXdr()
        }
        assertTrue(exception.message!!.contains("Invalid envelope type"))
    }

    // ========== fromEnvelopeXdr type mismatch ==========

    @Test
    fun testFromEnvelopeXdrStringNotATransactionThrows() = runTest {
        val keypair = KeyPair.fromSecretSeed(SECRET)
        val account = Account(keypair.getAccountId(), 100L)
        val inner = TransactionBuilder(account, NETWORK)
            .setBaseFee(100)
            .addOperation(PaymentOperation(ACCOUNT_B, AssetTypeNative, "10.0000000"))
            .addPreconditions(TransactionPreconditions(timeBounds = TimeBounds(0, 0)))
            .build()
        inner.sign(keypair)

        val feeBump = FeeBumpTransaction.createWithBaseFee(
            feeSource = keypair.getAccountId(),
            baseFee = 200,
            innerTransaction = inner
        )
        val base64 = feeBump.toEnvelopeXdrBase64()

        val exception = assertFailsWith<IllegalArgumentException> {
            Transaction.fromEnvelopeXdr(base64, NETWORK)
        }
        assertTrue(exception.message!!.contains("does not contain a Transaction"))
    }

    @Test
    fun testFromEnvelopeXdrBytesNotATransactionThrows() = runTest {
        val keypair = KeyPair.fromSecretSeed(SECRET)
        val account = Account(keypair.getAccountId(), 100L)
        val inner = TransactionBuilder(account, NETWORK)
            .setBaseFee(100)
            .addOperation(PaymentOperation(ACCOUNT_B, AssetTypeNative, "10.0000000"))
            .addPreconditions(TransactionPreconditions(timeBounds = TimeBounds(0, 0)))
            .build()
        inner.sign(keypair)

        val feeBump = FeeBumpTransaction.createWithBaseFee(
            feeSource = keypair.getAccountId(),
            baseFee = 200,
            innerTransaction = inner
        )
        val envelopeXdr = feeBump.toEnvelopeXdr()
        val writer = XdrWriter()
        envelopeXdr.encode(writer)
        val bytes = writer.toByteArray()

        val exception = assertFailsWith<IllegalArgumentException> {
            Transaction.fromEnvelopeXdr(bytes, NETWORK)
        }
        assertTrue(exception.message!!.contains("does not contain a Transaction"))
    }
}
