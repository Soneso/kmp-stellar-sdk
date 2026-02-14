package com.soneso.stellar.sdk.unitTests

import com.soneso.stellar.sdk.*
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class FeeBumpTransactionTest {

    private suspend fun createInnerTransaction(
        baseFee: Long = AbstractTransaction.MIN_BASE_FEE,
        network: Network = Network.TESTNET
    ): Transaction {
        val source = KeyPair.fromSecretSeed("SCH27VUZZ6UAKB67BDNF6FA42YMBMQCBKXWGMFD5TZ6S5ZZCZFLRXKHS")
        val account = Account(source.getAccountId(), 2908908335136768L)

        val transaction = TransactionBuilder(account, network)
            .addOperation(
                PaymentOperation(
                    destination = "GA7QYNF7SOWQ3GLR2BGMZEHXAVIRZA4KVWLTJJFC7MGXUA74P7UJVSGZ",
                    asset = AssetTypeNative,
                    amount = "200.0000000"
                )
            )
            .setBaseFee(baseFee)
            .addPreconditions(
                TransactionPreconditions(
                    timeBounds = TimeBounds(10, 11)
                )
            )
            .build()

        transaction.sign(source)
        return transaction
    }

    @Test
    fun testCreateWithBaseFee() = runTest {
        val inner = createInnerTransaction()
        val feeSource = "GDQNY3PBOJOKYZSRMK2S7LHHGWZIUISD4QORETLMXEWXBI7KFZZMKTL3"

        val feeBump = FeeBumpTransaction.createWithBaseFee(
            feeSource = feeSource,
            baseFee = AbstractTransaction.MIN_BASE_FEE * 2,
            innerTransaction = inner
        )

        // Fee should be: baseFee * (operations + 1) = 200 * (1 + 1) = 400
        assertEquals(AbstractTransaction.MIN_BASE_FEE * 4, feeBump.fee)
        assertEquals(feeSource, feeBump.feeSource)
        assertEquals(inner, feeBump.innerTransaction)
        assertEquals(Network.TESTNET, feeBump.network)
    }

    @Test
    fun testCreateWithFee() = runTest {
        val inner = createInnerTransaction()
        val feeSource = "GDQNY3PBOJOKYZSRMK2S7LHHGWZIUISD4QORETLMXEWXBI7KFZZMKTL3"
        val fee = 10000L

        val feeBump = FeeBumpTransaction.createWithFee(
            feeSource = feeSource,
            fee = fee,
            innerTransaction = inner
        )

        assertEquals(fee, feeBump.fee)
        assertEquals(feeSource, feeBump.feeSource)
        assertEquals(inner, feeBump.innerTransaction)
    }

    @Test
    fun testSetBaseFeeBelowNetworkMinimum() = runTest {
        val inner = createInnerTransaction()

        val exception = assertFailsWith<IllegalArgumentException> {
            FeeBumpTransaction.createWithBaseFee(
                feeSource = "GDQNY3PBOJOKYZSRMK2S7LHHGWZIUISD4QORETLMXEWXBI7KFZZMKTL3",
                baseFee = AbstractTransaction.MIN_BASE_FEE - 1,
                innerTransaction = inner
            )
        }

        assertTrue(exception.message!!.contains("MIN_BASE_FEE"))
    }

    @Test
    fun testSetBaseFeeBelowInner() = runTest {
        val inner = createInnerTransaction(baseFee = AbstractTransaction.MIN_BASE_FEE + 1)

        val exception = assertFailsWith<IllegalArgumentException> {
            FeeBumpTransaction.createWithBaseFee(
                feeSource = "GDQNY3PBOJOKYZSRMK2S7LHHGWZIUISD4QORETLMXEWXBI7KFZZMKTL3",
                baseFee = AbstractTransaction.MIN_BASE_FEE,
                innerTransaction = inner
            )
        }

        assertTrue(exception.message!!.contains("lower than inner transaction base fee"))
    }

    @Test
    fun testSetBaseFeeOverflowsLong() = runTest {
        val inner = createInnerTransaction(baseFee = AbstractTransaction.MIN_BASE_FEE + 1)

        val exception = assertFailsWith<IllegalArgumentException> {
            FeeBumpTransaction.createWithBaseFee(
                feeSource = "GDQNY3PBOJOKYZSRMK2S7LHHGWZIUISD4QORETLMXEWXBI7KFZZMKTL3",
                baseFee = Long.MAX_VALUE,
                innerTransaction = inner
            )
        }

        assertTrue(exception.message!!.contains("overflow"))
    }

    @Test
    fun testSetBaseFeeEqualToInner() = runTest {
        val inner = createInnerTransaction()

        val feeBump = FeeBumpTransaction.createWithBaseFee(
            feeSource = "GDQNY3PBOJOKYZSRMK2S7LHHGWZIUISD4QORETLMXEWXBI7KFZZMKTL3",
            baseFee = AbstractTransaction.MIN_BASE_FEE,
            innerTransaction = inner
        )

        // Fee should be: MIN_BASE_FEE * (1 operation + 1) = 100 * 2 = 200
        assertEquals(AbstractTransaction.MIN_BASE_FEE * 2, feeBump.fee)
    }

    @Test
    fun testHash() = runTest {
        val inner = createInnerTransaction()
        assertEquals(
            "2a8ead3351faa7797b284f59027355ddd69c21adb8e4da0b9bb95531f7f32681",
            inner.hashHex()
        )

        val feeBump = FeeBumpTransaction.createWithBaseFee(
            feeSource = "GDQNY3PBOJOKYZSRMK2S7LHHGWZIUISD4QORETLMXEWXBI7KFZZMKTL3",
            baseFee = AbstractTransaction.MIN_BASE_FEE * 2,
            innerTransaction = inner
        )

        assertEquals(
            "58266712c0c1d1cd98faa0e0159605a361cf2a5ca44ad69650eeb1d27ee62334",
            feeBump.hashHex()
        )
    }

    @Test
    fun testRoundTripXdr() = runTest {
        val inner = createInnerTransaction()

        val feeBump = FeeBumpTransaction.createWithBaseFee(
            feeSource = "GDQNY3PBOJOKYZSRMK2S7LHHGWZIUISD4QORETLMXEWXBI7KFZZMKTL3",
            baseFee = AbstractTransaction.MIN_BASE_FEE * 2,
            innerTransaction = inner
        )

        assertEquals(AbstractTransaction.MIN_BASE_FEE * 4, feeBump.fee)
        assertEquals(
            "GDQNY3PBOJOKYZSRMK2S7LHHGWZIUISD4QORETLMXEWXBI7KFZZMKTL3",
            feeBump.feeSource
        )

        // Convert to XDR and back
        val xdr = feeBump.toEnvelopeXdrBase64()
        val parsed = AbstractTransaction.fromEnvelopeXdr(xdr, Network.TESTNET) as FeeBumpTransaction

        assertEquals(feeBump.fee, parsed.fee)
        assertEquals(feeBump.feeSource, parsed.feeSource)
        assertTrue(feeBump.innerTransaction.hash().contentEquals(parsed.innerTransaction.hash()))
    }

    @Test
    fun testSign() = runTest {
        val inner = createInnerTransaction()
        val feeSourceKeypair = KeyPair.fromSecretSeed("SB7ZMPZB3YMMK5CUWENXVLZWBK4KYX4YU5JBXQNZSK2DP2Q7V3LVTO5V")

        val feeBump = FeeBumpTransaction.createWithBaseFee(
            feeSource = feeSourceKeypair.getAccountId(),
            baseFee = AbstractTransaction.MIN_BASE_FEE * 2,
            innerTransaction = inner
        )

        feeBump.sign(feeSourceKeypair)

        assertEquals(1, feeBump.signatures.size)

        // Verify the signature hint matches the fee source keypair
        val signature = feeBump.signatures[0]
        val publicKey = feeSourceKeypair.getPublicKey()
        val expectedHint = publicKey.copyOfRange(publicKey.size - 4, publicKey.size)
        assertTrue(signature.hint.contentEquals(expectedHint))
    }

    @Test
    fun testSignHashX() = runTest {
        val inner = createInnerTransaction()

        val feeBump = FeeBumpTransaction.createWithBaseFee(
            feeSource = "GDQNY3PBOJOKYZSRMK2S7LHHGWZIUISD4QORETLMXEWXBI7KFZZMKTL3",
            baseFee = AbstractTransaction.MIN_BASE_FEE * 2,
            innerTransaction = inner
        )

        val preimage = "hello world".encodeToByteArray()
        feeBump.signHashX(preimage)

        assertEquals(1, feeBump.signatures.size)
    }

    @Test
    fun testBuilder() = runTest {
        val inner = createInnerTransaction()
        val feeSource = "GDQNY3PBOJOKYZSRMK2S7LHHGWZIUISD4QORETLMXEWXBI7KFZZMKTL3"

        val feeBump = FeeBumpTransactionBuilder(inner)
            .setFeeSource(feeSource)
            .setBaseFee(AbstractTransaction.MIN_BASE_FEE * 2)
            .build()

        assertEquals(AbstractTransaction.MIN_BASE_FEE * 4, feeBump.fee)
        assertEquals(feeSource, feeBump.feeSource)
    }

    @Test
    fun testBuilderWithFee() = runTest {
        val inner = createInnerTransaction()
        val feeSource = "GDQNY3PBOJOKYZSRMK2S7LHHGWZIUISD4QORETLMXEWXBI7KFZZMKTL3"
        val fee = 10000L

        val feeBump = FeeBumpTransactionBuilder(inner)
            .setFeeSource(feeSource)
            .setFee(fee)
            .build()

        assertEquals(fee, feeBump.fee)
        assertEquals(feeSource, feeBump.feeSource)
    }

    @Test
    fun testBuilderMissingFeeSource() = runTest {
        val inner = createInnerTransaction()

        val exception = assertFailsWith<IllegalStateException> {
            FeeBumpTransactionBuilder(inner)
                .setBaseFee(AbstractTransaction.MIN_BASE_FEE * 2)
                .build()
        }

        assertTrue(exception.message!!.contains("Fee source"))
    }

    @Test
    fun testBuilderMissingFee() = runTest {
        val inner = createInnerTransaction()

        val exception = assertFailsWith<IllegalStateException> {
            FeeBumpTransactionBuilder(inner)
                .setFeeSource("GDQNY3PBOJOKYZSRMK2S7LHHGWZIUISD4QORETLMXEWXBI7KFZZMKTL3")
                .build()
        }

        assertTrue(exception.message!!.contains("Must set either"))
    }

    @Test
    fun testBuilderBothBaseFeeAndFee() = runTest {
        val inner = createInnerTransaction()

        // Setting baseFee then fee should fail
        val exception1 = assertFailsWith<IllegalStateException> {
            FeeBumpTransactionBuilder(inner)
                .setFeeSource("GDQNY3PBOJOKYZSRMK2S7LHHGWZIUISD4QORETLMXEWXBI7KFZZMKTL3")
                .setBaseFee(AbstractTransaction.MIN_BASE_FEE * 2)
                .setFee(10000)
                .build()
        }
        assertTrue(exception1.message!!.contains("baseFee"))

        // Setting fee then baseFee should fail
        val exception2 = assertFailsWith<IllegalStateException> {
            FeeBumpTransactionBuilder(inner)
                .setFeeSource("GDQNY3PBOJOKYZSRMK2S7LHHGWZIUISD4QORETLMXEWXBI7KFZZMKTL3")
                .setFee(10000)
                .setBaseFee(AbstractTransaction.MIN_BASE_FEE * 2)
                .build()
        }
        assertTrue(exception2.message!!.contains("fee"))
    }

    @Test
    fun testEquals() = runTest {
        val inner = createInnerTransaction()

        val feeBump1 = FeeBumpTransaction.createWithBaseFee(
            feeSource = "GDQNY3PBOJOKYZSRMK2S7LHHGWZIUISD4QORETLMXEWXBI7KFZZMKTL3",
            baseFee = AbstractTransaction.MIN_BASE_FEE * 2,
            innerTransaction = inner
        )

        val feeBump2 = FeeBumpTransaction.createWithBaseFee(
            feeSource = "GDQNY3PBOJOKYZSRMK2S7LHHGWZIUISD4QORETLMXEWXBI7KFZZMKTL3",
            baseFee = AbstractTransaction.MIN_BASE_FEE * 2,
            innerTransaction = inner
        )

        assertEquals(feeBump1, feeBump2)
        assertEquals(feeBump1.hashCode(), feeBump2.hashCode())
    }

    @Test
    fun testNotEquals() = runTest {
        val inner = createInnerTransaction()

        val feeBump1 = FeeBumpTransaction.createWithBaseFee(
            feeSource = "GDQNY3PBOJOKYZSRMK2S7LHHGWZIUISD4QORETLMXEWXBI7KFZZMKTL3",
            baseFee = AbstractTransaction.MIN_BASE_FEE * 2,
            innerTransaction = inner
        )

        val feeBump2 = FeeBumpTransaction.createWithBaseFee(
            feeSource = "GDQNY3PBOJOKYZSRMK2S7LHHGWZIUISD4QORETLMXEWXBI7KFZZMKTL3",
            baseFee = AbstractTransaction.MIN_BASE_FEE * 3,
            innerTransaction = inner
        )

        assertNotEquals(feeBump1, feeBump2)
    }

    @Test
    fun testInvalidFeeSource() = runTest {
        val inner = createInnerTransaction()

        val exception = assertFailsWith<IllegalArgumentException> {
            FeeBumpTransaction.createWithFee(
                feeSource = "INVALID",
                fee = 10000,
                innerTransaction = inner
            )
        }

        assertTrue(exception.message!!.contains("Invalid fee source"))
    }

    @Test
    fun testNegativeFee() = runTest {
        val inner = createInnerTransaction()

        val exception = assertFailsWith<IllegalArgumentException> {
            FeeBumpTransaction.createWithFee(
                feeSource = "GDQNY3PBOJOKYZSRMK2S7LHHGWZIUISD4QORETLMXEWXBI7KFZZMKTL3",
                fee = -100,
                innerTransaction = inner
            )
        }

        assertTrue(exception.message!!.contains("non-negative"))
    }

    @Test
    fun testFeeLowerThanInner() = runTest {
        val inner = createInnerTransaction(baseFee = 1000)

        val exception = assertFailsWith<IllegalArgumentException> {
            FeeBumpTransaction.createWithFee(
                feeSource = "GDQNY3PBOJOKYZSRMK2S7LHHGWZIUISD4QORETLMXEWXBI7KFZZMKTL3",
                fee = 500,
                innerTransaction = inner
            )
        }

        assertTrue(exception.message!!.contains("greater than or equal"))
    }

    @Test
    fun testMuxedFeeSource() = runTest {
        val inner = createInnerTransaction()
        // Create a muxed account using the MuxedAccount class
        val baseAccount = "GDQNY3PBOJOKYZSRMK2S7LHHGWZIUISD4QORETLMXEWXBI7KFZZMKTL3"
        val muxed = MuxedAccount(baseAccount, 123456789UL)
        val muxedAddress = muxed.address

        val feeBump = FeeBumpTransaction.createWithBaseFee(
            feeSource = muxedAddress,
            baseFee = AbstractTransaction.MIN_BASE_FEE * 2,
            innerTransaction = inner
        )

        assertEquals(muxedAddress, feeBump.feeSource)
    }

    @Test
    fun testToEnvelopeXdr() = runTest {
        val inner = createInnerTransaction()

        val feeBump = FeeBumpTransaction.createWithBaseFee(
            feeSource = "GDQNY3PBOJOKYZSRMK2S7LHHGWZIUISD4QORETLMXEWXBI7KFZZMKTL3",
            baseFee = AbstractTransaction.MIN_BASE_FEE * 2,
            innerTransaction = inner
        )

        val envelope = feeBump.toEnvelopeXdr()
        assertTrue(envelope is com.soneso.stellar.sdk.xdr.TransactionEnvelopeXdr.FeeBump)
    }

    @Test
    fun testFromEnvelopeXdr() = runTest {
        val inner = createInnerTransaction()
        val feeSourceKeypair = KeyPair.fromSecretSeed("SB7ZMPZB3YMMK5CUWENXVLZWBK4KYX4YU5JBXQNZSK2DP2Q7V3LVTO5V")

        val feeBump = FeeBumpTransaction.createWithBaseFee(
            feeSource = feeSourceKeypair.getAccountId(),
            baseFee = AbstractTransaction.MIN_BASE_FEE * 2,
            innerTransaction = inner
        )
        feeBump.sign(feeSourceKeypair)

        val xdrBase64 = feeBump.toEnvelopeXdrBase64()

        val decoded = FeeBumpTransaction.fromEnvelopeXdrBase64(xdrBase64, Network.TESTNET)

        assertEquals(feeBump.fee, decoded.fee)
        assertEquals(feeBump.feeSource, decoded.feeSource)
        assertEquals(feeBump.signatures.size, decoded.signatures.size)
        assertTrue(feeBump.innerTransaction.hash().contentEquals(decoded.innerTransaction.hash()))
    }

    @Test
    fun testFromEnvelopeXdrWrongType() = runTest {
        val inner = createInnerTransaction()
        val xdrBase64 = inner.toEnvelopeXdrBase64()

        val exception = assertFailsWith<IllegalArgumentException> {
            FeeBumpTransaction.fromEnvelopeXdrBase64(xdrBase64, Network.TESTNET)
        }

        assertTrue(exception.message!!.contains("not a fee bump"))
    }
}
