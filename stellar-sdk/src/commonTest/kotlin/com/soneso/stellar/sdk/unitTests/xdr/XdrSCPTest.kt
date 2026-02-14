package com.soneso.stellar.sdk.unitTests.xdr

import com.soneso.stellar.sdk.xdr.*
import kotlin.test.Test

/**
 * Tests for SCP-related XDR types: SCPBallot, SCPNomination, SCPQuorumSet,
 * SCPStatement, SCPEnvelope, SCPHistoryEntry, LedgerSCPMessages.
 */
class XdrSCPTest {

    private fun ballot(counter: UInt = 1u) = SCPBallotXdr(
        counter = Uint32Xdr(counter),
        value = ValueXdr(byteArrayOf(10, 20, 30))
    )

    private fun nodeId() = NodeIDXdr(XdrTestHelpers.publicKeyEd25519())

    // ---- SCPStatementType enum ----

    @Test fun testSCPStatementTypeEnum() {
        for (e in SCPStatementTypeXdr.entries) {
            XdrTestHelpers.assertXdrRoundTrip(e, { a, w -> a.encode(w) }, { r -> SCPStatementTypeXdr.decode(r) })
        }
    }

    // ---- SCPBallot ----

    @Test fun testSCPBallot() {
        val v = ballot()
        XdrTestHelpers.assertXdrRoundTrip(v, { a, w -> a.encode(w) }, { r -> SCPBallotXdr.decode(r) })
    }

    // ---- SCPNomination ----

    @Test fun testSCPNomination() {
        val v = SCPNominationXdr(
            quorumSetHash = XdrTestHelpers.hashXdr(),
            votes = listOf(ValueXdr(byteArrayOf(1, 2)), ValueXdr(byteArrayOf(3, 4))),
            accepted = listOf(ValueXdr(byteArrayOf(5, 6)))
        )
        XdrTestHelpers.assertXdrRoundTrip(v, { a, w -> a.encode(w) }, { r -> SCPNominationXdr.decode(r) })
    }

    @Test fun testSCPNominationEmpty() {
        val v = SCPNominationXdr(
            quorumSetHash = XdrTestHelpers.hashXdr(),
            votes = emptyList(),
            accepted = emptyList()
        )
        XdrTestHelpers.assertXdrRoundTrip(v, { a, w -> a.encode(w) }, { r -> SCPNominationXdr.decode(r) })
    }

    // ---- SCPQuorumSet ----

    @Test fun testSCPQuorumSetSimple() {
        val v = SCPQuorumSetXdr(
            threshold = Uint32Xdr(2u),
            validators = listOf(nodeId(), NodeIDXdr(XdrTestHelpers.publicKeyEd25519Alt())),
            innerSets = emptyList()
        )
        XdrTestHelpers.assertXdrRoundTrip(v, { a, w -> a.encode(w) }, { r -> SCPQuorumSetXdr.decode(r) })
    }

    @Test fun testSCPQuorumSetNested() {
        val inner = SCPQuorumSetXdr(
            threshold = Uint32Xdr(1u),
            validators = listOf(nodeId()),
            innerSets = emptyList()
        )
        val v = SCPQuorumSetXdr(
            threshold = Uint32Xdr(2u),
            validators = listOf(nodeId()),
            innerSets = listOf(inner)
        )
        XdrTestHelpers.assertXdrRoundTrip(v, { a, w -> a.encode(w) }, { r -> SCPQuorumSetXdr.decode(r) })
    }

    // ---- SCPStatementPrepare ----

    @Test fun testSCPStatementPrepareNoPrepared() {
        val v = SCPStatementPrepareXdr(
            quorumSetHash = XdrTestHelpers.hashXdr(),
            ballot = ballot(),
            prepared = null,
            preparedPrime = null,
            nC = Uint32Xdr(0u),
            nH = Uint32Xdr(0u)
        )
        XdrTestHelpers.assertXdrRoundTrip(v, { a, w -> a.encode(w) }, { r -> SCPStatementPrepareXdr.decode(r) })
    }

    @Test fun testSCPStatementPrepareWithPrepared() {
        val v = SCPStatementPrepareXdr(
            quorumSetHash = XdrTestHelpers.hashXdr(),
            ballot = ballot(2u),
            prepared = ballot(1u),
            preparedPrime = ballot(1u),
            nC = Uint32Xdr(1u),
            nH = Uint32Xdr(2u)
        )
        XdrTestHelpers.assertXdrRoundTrip(v, { a, w -> a.encode(w) }, { r -> SCPStatementPrepareXdr.decode(r) })
    }

    // ---- SCPStatementConfirm ----

    @Test fun testSCPStatementConfirm() {
        val v = SCPStatementConfirmXdr(
            ballot = ballot(3u),
            nPrepared = Uint32Xdr(2u),
            nCommit = Uint32Xdr(1u),
            nH = Uint32Xdr(3u),
            quorumSetHash = XdrTestHelpers.hashXdr()
        )
        XdrTestHelpers.assertXdrRoundTrip(v, { a, w -> a.encode(w) }, { r -> SCPStatementConfirmXdr.decode(r) })
    }

    // ---- SCPStatementExternalize ----

    @Test fun testSCPStatementExternalize() {
        val v = SCPStatementExternalizeXdr(
            commit = ballot(5u),
            nH = Uint32Xdr(5u),
            commitQuorumSetHash = XdrTestHelpers.hashXdr()
        )
        XdrTestHelpers.assertXdrRoundTrip(v, { a, w -> a.encode(w) }, { r -> SCPStatementExternalizeXdr.decode(r) })
    }

    // ---- SCPStatementPledges ----

    @Test fun testSCPStatementPledgesPrepare() {
        val prepare = SCPStatementPrepareXdr(
            XdrTestHelpers.hashXdr(), ballot(), null, null, Uint32Xdr(0u), Uint32Xdr(0u)
        )
        val v = SCPStatementPledgesXdr.Prepare(prepare)
        XdrTestHelpers.assertXdrRoundTrip(v, { a, w -> a.encode(w) }, { r -> SCPStatementPledgesXdr.decode(r) })
    }

    @Test fun testSCPStatementPledgesConfirm() {
        val confirm = SCPStatementConfirmXdr(
            ballot(2u), Uint32Xdr(1u), Uint32Xdr(1u), Uint32Xdr(2u), XdrTestHelpers.hashXdr()
        )
        val v = SCPStatementPledgesXdr.Confirm(confirm)
        XdrTestHelpers.assertXdrRoundTrip(v, { a, w -> a.encode(w) }, { r -> SCPStatementPledgesXdr.decode(r) })
    }

    @Test fun testSCPStatementPledgesExternalize() {
        val externalize = SCPStatementExternalizeXdr(ballot(3u), Uint32Xdr(3u), XdrTestHelpers.hashXdr())
        val v = SCPStatementPledgesXdr.Externalize(externalize)
        XdrTestHelpers.assertXdrRoundTrip(v, { a, w -> a.encode(w) }, { r -> SCPStatementPledgesXdr.decode(r) })
    }

    @Test fun testSCPStatementPledgesNominate() {
        val nomination = SCPNominationXdr(
            XdrTestHelpers.hashXdr(),
            listOf(ValueXdr(byteArrayOf(1, 2, 3))),
            emptyList()
        )
        val v = SCPStatementPledgesXdr.Nominate(nomination)
        XdrTestHelpers.assertXdrRoundTrip(v, { a, w -> a.encode(w) }, { r -> SCPStatementPledgesXdr.decode(r) })
    }

    // ---- SCPStatement ----

    @Test fun testSCPStatement() {
        val pledges = SCPStatementPledgesXdr.Prepare(
            SCPStatementPrepareXdr(
                XdrTestHelpers.hashXdr(), ballot(), null, null, Uint32Xdr(0u), Uint32Xdr(0u)
            )
        )
        val v = SCPStatementXdr(
            nodeId = nodeId(),
            slotIndex = Uint64Xdr(100UL),
            pledges = pledges
        )
        XdrTestHelpers.assertXdrRoundTrip(v, { a, w -> a.encode(w) }, { r -> SCPStatementXdr.decode(r) })
    }

    // ---- SCPEnvelope ----

    @Test fun testSCPEnvelope() {
        val statement = SCPStatementXdr(
            nodeId = nodeId(),
            slotIndex = Uint64Xdr(200UL),
            pledges = SCPStatementPledgesXdr.Nominate(
                SCPNominationXdr(XdrTestHelpers.hashXdr(), emptyList(), emptyList())
            )
        )
        val v = SCPEnvelopeXdr(
            statement = statement,
            signature = SignatureXdr(byteArrayOf(1, 2, 3, 4))
        )
        XdrTestHelpers.assertXdrRoundTrip(v, { a, w -> a.encode(w) }, { r -> SCPEnvelopeXdr.decode(r) })
    }

    // ---- LedgerSCPMessages ----

    @Test fun testLedgerSCPMessages() {
        val statement = SCPStatementXdr(
            nodeId = nodeId(),
            slotIndex = Uint64Xdr(50UL),
            pledges = SCPStatementPledgesXdr.Prepare(
                SCPStatementPrepareXdr(XdrTestHelpers.hashXdr(), ballot(), null, null, Uint32Xdr(0u), Uint32Xdr(0u))
            )
        )
        val envelope = SCPEnvelopeXdr(statement, SignatureXdr(byteArrayOf(5, 6, 7)))
        val v = LedgerSCPMessagesXdr(
            ledgerSeq = Uint32Xdr(100u),
            messages = listOf(envelope)
        )
        XdrTestHelpers.assertXdrRoundTrip(v, { a, w -> a.encode(w) }, { r -> LedgerSCPMessagesXdr.decode(r) })
    }

    // ---- SCPHistoryEntry ----

    @Test fun testSCPHistoryEntryV0() {
        val messages = LedgerSCPMessagesXdr(Uint32Xdr(1u), emptyList())
        val v0 = SCPHistoryEntryV0Xdr(
            quorumSets = emptyList(),
            ledgerMessages = messages
        )
        val v = SCPHistoryEntryXdr.V0(v0)
        XdrTestHelpers.assertXdrRoundTrip(v, { a, w -> a.encode(w) }, { r -> SCPHistoryEntryXdr.decode(r) })
    }

    @Test fun testSCPHistoryEntryV0WithQuorumSets() {
        val qs = SCPQuorumSetXdr(Uint32Xdr(1u), listOf(nodeId()), emptyList())
        val messages = LedgerSCPMessagesXdr(Uint32Xdr(2u), emptyList())
        val v0 = SCPHistoryEntryV0Xdr(
            quorumSets = listOf(qs),
            ledgerMessages = messages
        )
        XdrTestHelpers.assertXdrRoundTrip(v0, { a, w -> a.encode(w) }, { r -> SCPHistoryEntryV0Xdr.decode(r) })
    }
}
