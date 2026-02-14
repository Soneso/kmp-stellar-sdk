package com.soneso.stellar.sdk.unitTests.horizon.responses

import com.soneso.stellar.sdk.horizon.responses.LedgerResponse
import com.soneso.stellar.sdk.horizon.responses.Page
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PageResponseTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun testPageWithRecords() {
        val pageJson = """
        {
            "_embedded": {
                "records": [
                    {
                        "id": "ledger1",
                        "paging_token": "1",
                        "hash": "aaa",
                        "sequence": 1,
                        "closed_at": "2021-01-01T00:00:00Z",
                        "total_coins": "100.0",
                        "fee_pool": "0.0",
                        "base_fee_in_stroops": "100",
                        "base_reserve_in_stroops": "5000000",
                        "_links": {
                            "self": {"href": "https://example.com/self"},
                            "transactions": {"href": "https://example.com/tx"},
                            "operations": {"href": "https://example.com/ops"},
                            "payments": {"href": "https://example.com/pay"},
                            "effects": {"href": "https://example.com/fx"}
                        }
                    }
                ]
            },
            "_links": {
                "self": {"href": "https://horizon.stellar.org/ledgers?cursor=&limit=10&order=asc"},
                "next": {"href": "https://horizon.stellar.org/ledgers?cursor=1&limit=10&order=asc"},
                "prev": {"href": "https://horizon.stellar.org/ledgers?cursor=1&limit=10&order=desc"}
            }
        }
        """.trimIndent()
        val page = json.decodeFromString<Page<LedgerResponse>>(pageJson)
        assertEquals(1, page.records.size)
        assertEquals("ledger1", page.records[0].id)
        assertEquals(1L, page.records[0].sequence)
    }

    @Test
    fun testPageLinks() {
        val pageJson = """
        {
            "_embedded": {"records": []},
            "_links": {
                "self": {"href": "https://example.com/self"},
                "next": {"href": "https://example.com/next"},
                "prev": {"href": "https://example.com/prev"}
            }
        }
        """.trimIndent()
        val page = json.decodeFromString<Page<LedgerResponse>>(pageJson)
        assertEquals("https://example.com/self", page.links?.self?.href)
        assertEquals("https://example.com/next", page.links?.next?.href)
        assertEquals("https://example.com/prev", page.links?.prev?.href)
    }

    @Test
    fun testEmptyPage() {
        val pageJson = """
        {
            "_embedded": {"records": []},
            "_links": {
                "self": {"href": "https://example.com/self"}
            }
        }
        """.trimIndent()
        val page = json.decodeFromString<Page<LedgerResponse>>(pageJson)
        assertTrue(page.records.isEmpty())
        assertNull(page.links?.next)
        assertNull(page.links?.prev)
    }

    @Test
    fun testPageWithNullEmbedded() {
        val pageJson = """
        {
            "_links": {
                "self": {"href": "https://example.com/self"}
            }
        }
        """.trimIndent()
        val page = json.decodeFromString<Page<LedgerResponse>>(pageJson)
        assertTrue(page.records.isEmpty())
        assertNull(page.embedded)
    }

    @Test
    fun testPageLinksEquality() {
        val l1 = Page.Links(
            next = com.soneso.stellar.sdk.horizon.responses.Link("next"),
            prev = com.soneso.stellar.sdk.horizon.responses.Link("prev"),
            self = com.soneso.stellar.sdk.horizon.responses.Link("self")
        )
        val l2 = Page.Links(
            next = com.soneso.stellar.sdk.horizon.responses.Link("next"),
            prev = com.soneso.stellar.sdk.horizon.responses.Link("prev"),
            self = com.soneso.stellar.sdk.horizon.responses.Link("self")
        )
        assertEquals(l1, l2)
    }
}
