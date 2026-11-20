package com.soneso.stellar.sdk.unitTests.sep.sep51

import com.soneso.stellar.sdk.xdr.LedgerCloseMetaXdr
import com.soneso.stellar.sdk.xdr.XdrReader
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

/**
 * The writer refuses before it spends the stack. On a 512 KB thread, the deepest ledger close
 * meta the binary decoder accepts, whose XDR-JSON would nest 1026 levels, is refused with
 * [IllegalArgumentException], and a ledger close meta at the 128 levels the limit admits writes
 * and reads back. The binary steps run on the test thread, so the small thread runs only XDR-JSON.
 */
class Sep51WriteDepthStackTest {

    @Test
    fun aSmallThreadRefusesTheDeepestLedgerAndWritesOneAtTheLimit() {
        val witness = LCM.decode(XdrReader(LCM.bytes(authLedger(499))))
        val bytes = LCM.bytes(authLedger(50))
        val atLimit = LCM.decode(XdrReader(bytes))
        var json = ""
        var readBack: LedgerCloseMetaXdr? = null
        var failure: Throwable? = null
        val thread = Thread(null, {
            failure = runCatching {
                refusesWrite("AssetXdr") { LCM.write(witness) }
                json = LCM.write(atLimit)
                readBack = LCM.read(json)
            }.exceptionOrNull()
        }, "xdr-json-small-stack", 512L * 1024)
        thread.start()
        thread.join()
        failure?.let { throw it }
        assertEquals(128, textDepth(json))
        assertContentEquals(bytes, LCM.bytes(readBack!!))
    }
}
