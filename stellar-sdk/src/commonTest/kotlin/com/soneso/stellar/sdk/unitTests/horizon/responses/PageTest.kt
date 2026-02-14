package com.soneso.stellar.sdk.unitTests.horizon.responses

import com.soneso.stellar.sdk.horizon.responses.Link
import com.soneso.stellar.sdk.horizon.responses.Page
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PageTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    data class TestRecord(
        @SerialName("id")
        val id: String,
        @SerialName("value")
        val value: String
    )

    @Test
    fun testPageDeserialization() {
        val pageJson = """
        {
            "_embedded": {
                "records": [
                    {
                        "id": "1",
                        "value": "test1"
                    },
                    {
                        "id": "2",
                        "value": "test2"
                    }
                ]
            },
            "_links": {
                "self": {
                    "href": "https://horizon.stellar.org/accounts?cursor=&limit=10&order=asc"
                },
                "next": {
                    "href": "https://horizon.stellar.org/accounts?cursor=next_token&limit=10&order=asc"
                },
                "prev": {
                    "href": "https://horizon.stellar.org/accounts?cursor=prev_token&limit=10&order=desc"
                }
            }
        }
        """.trimIndent()

        val page = json.decodeFromString<Page<TestRecord>>(pageJson)
        assertNotNull(page.embedded)
        assertEquals(2, page.embedded!!.records.size)
        assertEquals("1", page.embedded!!.records[0].id)
        assertEquals("test1", page.embedded!!.records[0].value)
        assertEquals("2", page.embedded!!.records[1].id)
        assertEquals("test2", page.embedded!!.records[1].value)

        assertNotNull(page.links)
        assertTrue(page.links!!.self.href.contains("accounts"))
        assertNotNull(page.links!!.next)
        assertTrue(page.links!!.next!!.href.contains("next_token"))
        assertNotNull(page.links!!.prev)
        assertTrue(page.links!!.prev!!.href.contains("prev_token"))
    }

    @Test
    fun testPageWithoutEmbedded() {
        val pageJson = """
        {
            "_links": {
                "self": {
                    "href": "https://horizon.stellar.org/accounts"
                }
            }
        }
        """.trimIndent()

        val page = json.decodeFromString<Page<TestRecord>>(pageJson)
        assertNull(page.embedded)
        assertTrue(page.records.isEmpty())
        assertNotNull(page.links)
        assertTrue(page.links!!.self.href.contains("accounts"))
        assertNull(page.links!!.next)
        assertNull(page.links!!.prev)
    }

    @Test
    fun testPageRecordsConvenience() {
        val pageJson = """
        {
            "_embedded": {
                "records": [
                    {
                        "id": "test",
                        "value": "convenience"
                    }
                ]
            },
            "_links": {
                "self": {
                    "href": "https://example.com"
                }
            }
        }
        """.trimIndent()

        val page = json.decodeFromString<Page<TestRecord>>(pageJson)
        assertEquals(1, page.records.size)
        assertEquals("test", page.records[0].id)
        assertEquals("convenience", page.records[0].value)
    }

    @Test
    fun testEmptyPage() {
        val pageJson = """
        {
            "_embedded": {
                "records": []
            },
            "_links": {
                "self": {
                    "href": "https://horizon.stellar.org/empty"
                }
            }
        }
        """.trimIndent()

        val page = json.decodeFromString<Page<TestRecord>>(pageJson)
        assertTrue(page.records.isEmpty())
        assertNotNull(page.embedded)
        assertTrue(page.embedded!!.records.isEmpty())
    }

    @Test
    fun testPageLinksWithoutNextPrev() {
        val pageJson = """
        {
            "_embedded": {
                "records": [
                    {
                        "id": "single",
                        "value": "record"
                    }
                ]
            },
            "_links": {
                "self": {
                    "href": "https://horizon.stellar.org/single"
                }
            }
        }
        """.trimIndent()

        val page = json.decodeFromString<Page<TestRecord>>(pageJson)
        assertEquals(1, page.records.size)
        assertNotNull(page.links)
        assertNotNull(page.links!!.self)
        assertNull(page.links!!.next)
        assertNull(page.links!!.prev)
    }

    @Test
    fun testPageEmbeddedEquality() {
        val records1 = listOf(TestRecord("1", "test"))
        val records2 = listOf(TestRecord("1", "test"))
        
        val embedded1 = Page.Embedded(records1)
        val embedded2 = Page.Embedded(records2)
        
        assertEquals(embedded1, embedded2)
        assertEquals(embedded1.hashCode(), embedded2.hashCode())
    }

    @Test
    fun testPageLinksEquality() {
        val selfLink = Link(href = "https://example.com/self")
        val nextLink = Link(href = "https://example.com/next")
        val prevLink = Link(href = "https://example.com/prev")
        
        val links1 = Page.Links(self = selfLink, next = nextLink, prev = prevLink)
        val links2 = Page.Links(self = selfLink, next = nextLink, prev = prevLink)
        
        assertEquals(links1, links2)
        assertEquals(links1.hashCode(), links2.hashCode())
    }

    @Serializable
    data class ComplexRecord(
        @SerialName("id")
        val id: String,
        @SerialName("nested")
        val nested: NestedData,
        @SerialName("list")
        val list: List<String>
    )

    @Serializable
    data class NestedData(
        @SerialName("field")
        val field: String
    )

    @Test
    fun testPageWithComplexRecords() {

        val pageJson = """
        {
            "_embedded": {
                "records": [
                    {
                        "id": "complex1",
                        "nested": {
                            "field": "nested_value"
                        },
                        "list": ["item1", "item2"]
                    }
                ]
            },
            "_links": {
                "self": {
                    "href": "https://example.com/complex"
                }
            }
        }
        """.trimIndent()

        val page = json.decodeFromString<Page<ComplexRecord>>(pageJson)
        assertEquals(1, page.records.size)
        assertEquals("complex1", page.records[0].id)
        assertEquals("nested_value", page.records[0].nested.field)
        assertEquals(2, page.records[0].list.size)
        assertEquals("item1", page.records[0].list[0])
        assertEquals("item2", page.records[0].list[1])
    }

    @Test
    fun testMinimalPageStructure() {
        val pageJson = """
        {}
        """.trimIndent()

        val page = json.decodeFromString<Page<TestRecord>>(pageJson)
        assertTrue(page.records.isEmpty())
        assertNull(page.embedded)
        assertNull(page.links)
    }

    @Test
    fun testPageIsResponseSubclass() {
        val pageJson = """
        {
            "_embedded": {
                "records": []
            },
            "_links": {
                "self": {
                    "href": "https://example.com"
                }
            }
        }
        """.trimIndent()

        val page = json.decodeFromString<Page<TestRecord>>(pageJson)
        assertTrue(page is com.soneso.stellar.sdk.horizon.responses.Response)
    }
}