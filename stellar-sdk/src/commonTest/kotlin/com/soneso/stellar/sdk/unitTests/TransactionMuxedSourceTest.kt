package com.soneso.stellar.sdk.unitTests

import com.soneso.stellar.sdk.*
import com.soneso.stellar.sdk.sep.sep10.WebAuth
import com.soneso.stellar.sdk.sep.sep10.exceptions.InvalidTransactionSourceAccountException
import kotlinx.coroutines.test.runTest
import kotlin.test.*
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * A [TransactionBuilderAccount] whose [accountId] is a muxed (`M...`) address, which [Account] refuses,
 * so that [TransactionBuilder] builds a transaction with a muxed source.
 */
internal class MuxedSourceAccount(override val accountId: String, sequenceNumber: Long) : TransactionBuilderAccount {
    private var current = sequenceNumber

    override val keypair: KeyPair = KeyPair.fromAccountId(MuxedAccount(accountId).accountId)

    override val sequenceNumber: Long
        get() = current

    override fun setSequenceNumber(seqNum: Long) {
        current = seqNum
    }

    override fun getIncrementedSequenceNumber(): Long = current + 1

    override fun incrementSequenceNumber() {
        current++
    }
}

class TransactionMuxedSourceTest {

    companion object {
        // Mainnet transaction 17fee99b... of ledger 36196205: a V1 envelope whose source is
        // KEY_TYPE_MUXED_ED25519 with muxed id 0. The address is the source as stellar-xdr decodes it.
        private const val MAINNET_MUXED_ENVELOPE =
            "AAAAAgAAAQAAAAAAAAAAAINI8pDy470sjNO8XONO7kiOq2PYWIOB04Vb7ut9bMtBAAAAZAIoT1sAAAABAAAAAQAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAQAAAAAAAAAGAAAAAkJBQllTSElCAAAAAAAAAACC7/6AUeZNAfs6vz2zTUhqU25f1agTVng8z7hGKOGhAwx9cTtJ2gAAAAAAAAAAAAF9bMtBAAAAQJhoGRiBO2Na1Np9OK0JyWd4Dd6KMivdh5NuVYw8U8/QcXAZ7q/46M/fXC7UaoF2bair7Wbc0LpD3Lkx6XSyvgE="
        private const val MAINNET_MUXED_HASH = "17fee99b64f9c34ec9f8b52ffd4a72c51186bdf36f79a92bbbc138603916039c"
        private const val MAINNET_MUXED_SOURCE = "MCBUR4UQ6LR32LEM2O6FZY2O5ZEI5K3D3BMIHAOTQVN65235NTFUCAAAAAAAAAAAAA4U2"

        // Testnet envelopes written as raw XDR: source key bytes 1 to 32, fee 100, sequence number
        // 4294967297, PRECOND_NONE, MEMO_NONE, one BUMP_SEQUENCE operation and one placeholder signature.
        // Fee bumps use fee source key bytes 65 to 96 and fee 400. Each hash is sha256(network id ||
        // envelope type || transaction), computed outside the SDK; addresses are encoded outside the SDK
        // and match the stellar-xdr decode.
        private const val SOURCE_G = "GAAQEAYEAUDAOCAJBIFQYDIOB4IBCEQTCQKRMFYYDENBWHA5DYPSABOV"

        private const val ID_1_ENVELOPE =
            "AAAAAgAAAQAAAAAAAAAAAQECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8gAAAAZAAAAAEAAAABAAAAAAAAAAAAAAABAAAAAAAAAAsAAAAAAAAABAAAAAAAAAABHR4fIAAAAECrq6urq6urq6urq6urq6urq6urq6urq6urq6urq6urq6urq6urq6urq6urq6urq6urq6urq6urq6urq6urq6ur"
        private const val ID_1_HASH = "5930f2029260bca767011c64fc89c81dd6c4604d9c61d0788614b5d0b6c2597a"
        private const val ID_1_SOURCE = "MAAQEAYEAUDAOCAJBIFQYDIOB4IBCEQTCQKRMFYYDENBWHA5DYPSAAAAAAAAAAAAAENNK"

        // Muxed id 2^64-1.
        private const val ID_MAX_ENVELOPE =
            "AAAAAgAAAQD//////////wECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8gAAAAZAAAAAEAAAABAAAAAAAAAAAAAAABAAAAAAAAAAsAAAAAAAAABAAAAAAAAAABHR4fIAAAAECrq6urq6urq6urq6urq6urq6urq6urq6urq6urq6urq6urq6urq6urq6urq6urq6urq6urq6urq6urq6urq6ur"
        private const val ID_MAX_HASH = "cd68d869b3702fc990235fb165d61a8c0aad7396f7d6f696094535650ada5908"
        private const val ID_MAX_SOURCE = "MAAQEAYEAUDAOCAJBIFQYDIOB4IBCEQTCQKRMFYYDENBWHA5DYPSB7777777777777NGG"

        // Fee bump with a G fee source around the transaction above with muxed id 1234567890123456789.
        private const val MUXED_INNER_FEE_BUMP_ENVELOPE =
            "AAAABQAAAABBQkNERUZHSElKS0xNTk9QUVJTVFVWV1hZWltcXV5fYAAAAAAAAAGQAAAAAgAAAQARIhD0femBFQECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8gAAAAZAAAAAEAAAABAAAAAAAAAAAAAAABAAAAAAAAAAsAAAAAAAAABAAAAAAAAAABHR4fIAAAAECrq6urq6urq6urq6urq6urq6urq6urq6urq6urq6urq6urq6urq6urq6urq6urq6urq6urq6urq6urq6urq6urAAAAAAAAAAFdXl9gAAAAQM3Nzc3Nzc3Nzc3Nzc3Nzc3Nzc3Nzc3Nzc3Nzc3Nzc3Nzc3Nzc3Nzc3Nzc3Nzc3Nzc3Nzc3Nzc3Nzc3Nzc3Nzc0="
        private const val MUXED_INNER_HASH = "e306391b0064607ee3f1c5470bc09f476c6a11bc14775f45f295af6d923bcc09"
        private const val MUXED_INNER_FEE_BUMP_HASH = "e05a3d3f681196e13a8d29222da2403ef1b2567801a7947784719f424ea5f998"
        private const val MUXED_INNER_SOURCE = "MAAQEAYEAUDAOCAJBIFQYDIOB4IBCEQTCQKRMFYYDENBWHA5DYPSAEJCCD2H32MBCXZTE"

        // The transaction above with the G source, as a V1 and as a V0 envelope. A V0 transaction is
        // hashed in its V1 form, so both have the same hash.
        private const val G_SOURCE_ENVELOPE =
            "AAAAAgAAAAABAgMEBQYHCAkKCwwNDg8QERITFBUWFxgZGhscHR4fIAAAAGQAAAABAAAAAQAAAAAAAAAAAAAAAQAAAAAAAAALAAAAAAAAAAQAAAAAAAAAAR0eHyAAAABAq6urq6urq6urq6urq6urq6urq6urq6urq6urq6urq6urq6urq6urq6urq6urq6urq6urq6urq6urq6urq6urqw=="
        private const val V0_ENVELOPE =
            "AAAAAAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8gAAAAZAAAAAEAAAABAAAAAAAAAAAAAAABAAAAAAAAAAsAAAAAAAAABAAAAAAAAAABHR4fIAAAAECrq6urq6urq6urq6urq6urq6urq6urq6urq6urq6urq6urq6urq6urq6urq6urq6urq6urq6urq6urq6urq6ur"
        private const val G_SOURCE_HASH = "ef56104d64995a838c55ca9733c9f783ef0a0199223e4a6079a343cff3a91c70"

        // The G source transaction whose operation has the source key bytes 65 to 96 with muxed id 42.
        private const val MUXED_OPERATION_ENVELOPE =
            "AAAAAgAAAAABAgMEBQYHCAkKCwwNDg8QERITFBUWFxgZGhscHR4fIAAAAGQAAAABAAAAAQAAAAAAAAAAAAAAAQAAAAEAAAEAAAAAAAAAACpBQkNERUZHSElKS0xNTk9QUVJTVFVWV1hZWltcXV5fYAAAAAsAAAAAAAAABAAAAAAAAAABHR4fIAAAAECrq6urq6urq6urq6urq6urq6urq6urq6urq6urq6urq6urq6urq6urq6urq6urq6urq6urq6urq6urq6urq6ur"
        private const val MUXED_OPERATION_HASH = "06cdb8d812b6c0e44305e91a7803c9bab87a84f6f5806ba0f6b0d3dd5ecb2ce7"
        private const val MUXED_OPERATION_SOURCE = "MBAUEQ2EIVDEOSCJJJFUYTKOJ5IFCUSTKRKVMV2YLFNFWXC5LZPWAAAAAAAAAAAAFIWWS"

        // Fee bump with the fee source key bytes 65 to 96 and muxed id 7 around the G source transaction.
        private const val MUXED_FEE_SOURCE_ENVELOPE =
            "AAAABQAAAQAAAAAAAAAAB0FCQ0RFRkdISUpLTE1OT1BRUlNUVVZXWFlaW1xdXl9gAAAAAAAAAZAAAAACAAAAAAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8gAAAAZAAAAAEAAAABAAAAAAAAAAAAAAABAAAAAAAAAAsAAAAAAAAABAAAAAAAAAABHR4fIAAAAECrq6urq6urq6urq6urq6urq6urq6urq6urq6urq6urq6urq6urq6urq6urq6urq6urq6urq6urq6urq6urq6urAAAAAAAAAAFdXl9gAAAAQM3Nzc3Nzc3Nzc3Nzc3Nzc3Nzc3Nzc3Nzc3Nzc3Nzc3Nzc3Nzc3Nzc3Nzc3Nzc3Nzc3Nzc3Nzc3Nzc3Nzc3Nzc0="
        private const val MUXED_FEE_SOURCE_HASH = "d53e48044f0ed9e2641865c2616c5a60a0772b69b5d062edd8e36ca000003261"
        private const val MUXED_FEE_SOURCE = "MBAUEQ2EIVDEOSCJJJFUYTKOJ5IFCUSTKRKVMV2YLFNFWXC5LZPWAAAAAAAAAAAAA7RJY"

        private const val HOME_DOMAIN = "testanchor.stellar.org"
    }

    @Test
    fun testMainnetMuxedSourceKeepsSourceHashAndBytes() = runTest {
        val tx = AbstractTransaction.fromEnvelopeXdr(MAINNET_MUXED_ENVELOPE, Network.PUBLIC) as Transaction

        assertEquals(MAINNET_MUXED_SOURCE, tx.sourceAccount)
        assertEquals(MAINNET_MUXED_HASH, tx.hashHex())
        assertEquals(MAINNET_MUXED_ENVELOPE, tx.toEnvelopeXdrBase64())
    }

    @Test
    fun testMuxedSourceWithIdOneAndMaximumKeepsSourceHashAndBytes() = runTest {
        val vectors = listOf(
            Triple(ID_1_ENVELOPE, ID_1_HASH, ID_1_SOURCE),
            Triple(ID_MAX_ENVELOPE, ID_MAX_HASH, ID_MAX_SOURCE)
        )
        for ((envelope, hash, source) in vectors) {
            val tx = AbstractTransaction.fromEnvelopeXdr(envelope, Network.TESTNET) as Transaction

            assertEquals(source, tx.sourceAccount)
            assertEquals(hash, tx.hashHex())
            assertEquals(envelope, tx.toEnvelopeXdrBase64())
        }
    }

    @Test
    fun testFeeBumpKeepsMuxedInnerSourceHashesAndBytes() = runTest {
        val feeBump =
            AbstractTransaction.fromEnvelopeXdr(MUXED_INNER_FEE_BUMP_ENVELOPE, Network.TESTNET) as FeeBumpTransaction

        assertEquals(MUXED_INNER_SOURCE, feeBump.innerTransaction.sourceAccount)
        assertEquals(MUXED_INNER_HASH, feeBump.innerTransaction.hashHex())
        assertEquals(MUXED_INNER_FEE_BUMP_HASH, feeBump.hashHex())
        assertEquals(MUXED_INNER_FEE_BUMP_ENVELOPE, feeBump.toEnvelopeXdrBase64())
    }

    @OptIn(ExperimentalTime::class)
    @Test
    fun testSep10ChallengeWithMuxedServerSourceIsRefusedAtTheSourceCheck() = runTest {
        val serverKeyPair = KeyPair.random()
        val clientKeyPair = KeyPair.random()
        val muxedServer = MuxedAccount(serverKeyPair.getAccountId(), 1UL).address
        val now = Clock.System.now().toEpochMilliseconds() / 1000

        // A SEP-10 nonce: the Base64 text of 48 bytes.
        val nonce = "AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8gISIjJCUmJygpKissLS4v"
        val authOperation = ManageDataOperation(name = "$HOME_DOMAIN auth", value = nonce.encodeToByteArray())
        authOperation.sourceAccount = clientKeyPair.getAccountId()
        val challenge = TransactionBuilder(MuxedSourceAccount(muxedServer, -1L), Network.TESTNET)
            .addOperation(authOperation)
            .addTimeBounds(TimeBounds(minTime = now - 300, maxTime = now + 600))
            .setBaseFee(AbstractTransaction.MIN_BASE_FEE)
            .build()
        challenge.sign(serverKeyPair)

        val webAuth = WebAuth(
            authEndpoint = "https://$HOME_DOMAIN/auth",
            network = Network.TESTNET,
            serverSigningKey = serverKeyPair.getAccountId(),
            serverHomeDomain = HOME_DOMAIN
        )
        val exception = assertFailsWith<InvalidTransactionSourceAccountException> {
            webAuth.validateChallenge(
                challengeXdr = challenge.toEnvelopeXdrBase64(),
                clientAccountId = clientKeyPair.getAccountId()
            )
        }
        assertTrue(
            exception.message?.contains(muxedServer) == true,
            "The muxed source is named, got: ${exception.message}"
        )
    }

    @Test
    fun testEd25519SourceAndV0EnvelopesKeepSourceHashAndBytes() = runTest {
        for (envelope in listOf(G_SOURCE_ENVELOPE, V0_ENVELOPE)) {
            val tx = AbstractTransaction.fromEnvelopeXdr(envelope, Network.TESTNET) as Transaction

            assertEquals(SOURCE_G, tx.sourceAccount)
            assertEquals(G_SOURCE_HASH, tx.hashHex())
            assertEquals(envelope, tx.toEnvelopeXdrBase64())
        }
    }

    @Test
    fun testMuxedOperationSourceInEd25519SourceTransactionKeepsHashAndBytes() = runTest {
        val tx = AbstractTransaction.fromEnvelopeXdr(MUXED_OPERATION_ENVELOPE, Network.TESTNET) as Transaction

        assertEquals(SOURCE_G, tx.sourceAccount)
        assertEquals(MUXED_OPERATION_SOURCE, tx.operations.single().sourceAccount)
        assertEquals(MUXED_OPERATION_HASH, tx.hashHex())
        assertEquals(MUXED_OPERATION_ENVELOPE, tx.toEnvelopeXdrBase64())
    }

    @Test
    fun testMuxedFeeSourceAroundEd25519InnerSourceKeepsHashesAndBytes() = runTest {
        val feeBump =
            AbstractTransaction.fromEnvelopeXdr(MUXED_FEE_SOURCE_ENVELOPE, Network.TESTNET) as FeeBumpTransaction

        assertEquals(MUXED_FEE_SOURCE, feeBump.feeSource)
        assertEquals(SOURCE_G, feeBump.innerTransaction.sourceAccount)
        assertEquals(G_SOURCE_HASH, feeBump.innerTransaction.hashHex())
        assertEquals(MUXED_FEE_SOURCE_HASH, feeBump.hashHex())
        assertEquals(MUXED_FEE_SOURCE_ENVELOPE, feeBump.toEnvelopeXdrBase64())
    }
}
