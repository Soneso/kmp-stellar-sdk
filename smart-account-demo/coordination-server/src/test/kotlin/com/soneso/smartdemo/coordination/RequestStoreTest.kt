package com.soneso.smartdemo.coordination

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicInteger
import kotlin.io.path.createTempDirectory
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RequestStoreTest {

    private fun makeInput(
        smartAccount: String = "CSMART",
        target: String = "CTARGET",
        targetFn: String = "transfer",
        args: List<String> = listOf("AAAA", "BBBB"),
        amount: String = "10.5",
        reason: Int = 3016,
    ) = CreateRequestInput(smartAccount, target, targetFn, args, amount, reason)

    private fun counterIds(): () -> String {
        val counter = AtomicInteger(0)
        return { "id-${counter.getAndIncrement()}" }
    }

    @Test
    fun createAssignsIdPendingStatusAndCreatedAt() = runBlocking {
        val store = RequestStore(idGenerator = counterIds(), clock = { 1_700_000_000_000L })
        val created = store.create(makeInput())

        assertEquals("id-0", created.id)
        assertEquals(RequestStatus.PENDING, created.status)
        assertEquals(1_700_000_000_000L, created.createdAt)
        assertNull(created.resolvedAt)
        assertNull(created.resultHash)
        assertNull(created.note)
        assertEquals("10.5", created.amount)
        assertEquals(3016, created.reason)
        assertEquals(listOf("AAAA", "BBBB"), created.args)
    }

    @Test
    fun argsAreStoredVerbatim() = runBlocking {
        val store = RequestStore()
        val created = store.create(makeInput(args = listOf("Zm9v", "YmFy")))
        assertEquals(listOf("Zm9v", "YmFy"), created.args)
    }

    @Test
    fun getByIdReturnsTheRequestOrNull() = runBlocking {
        val store = RequestStore()
        val created = store.create(makeInput())
        assertEquals(created, store.getById(created.id))
        assertNull(store.getById("missing"))
    }

    @Test
    fun listReturnsNewestFirst() = runBlocking {
        val store = RequestStore(idGenerator = counterIds())
        val a = store.create(makeInput())
        val b = store.create(makeInput())
        val c = store.create(makeInput())
        assertEquals(listOf(c.id, b.id, a.id), store.list().map { it.id })
    }

    @Test
    fun listFiltersByStatus() = runBlocking {
        val store = RequestStore(idGenerator = counterIds())
        val a = store.create(makeInput())
        val b = store.create(makeInput())
        store.create(makeInput())

        store.approve(a.id, "hashA")
        store.reject(b.id, "nope")

        assertEquals(1, store.list(RequestStatus.PENDING).size)
        val approved = store.list(RequestStatus.APPROVED)
        assertEquals(1, approved.size)
        assertEquals(a.id, approved[0].id)
        val rejected = store.list(RequestStatus.REJECTED)
        assertEquals(1, rejected.size)
        assertEquals(b.id, rejected[0].id)
    }

    @Test
    fun approveSetsStatusResolvedAtAndResultHash() = runBlocking {
        val store = RequestStore(clock = { 42L })
        val created = store.create(makeInput())
        val approved = store.approve(created.id, "abc123")

        assertEquals(RequestStatus.APPROVED, approved.status)
        assertEquals(42L, approved.resolvedAt)
        assertEquals("abc123", approved.resultHash)
        assertNull(approved.note)
        assertEquals(approved, store.getById(created.id))
    }

    @Test
    fun rejectSetsStatusResolvedAtAndOptionalNote() = runBlocking {
        val store = RequestStore(clock = { 99L })
        val created = store.create(makeInput())
        val rejected = store.reject(created.id, "policy violation")

        assertEquals(RequestStatus.REJECTED, rejected.status)
        assertEquals(99L, rejected.resolvedAt)
        assertEquals("policy violation", rejected.note)
        assertNull(rejected.resultHash)
    }

    @Test
    fun rejectWithoutANoteLeavesNoteNull() = runBlocking {
        val store = RequestStore()
        val created = store.create(makeInput())
        assertNull(store.reject(created.id, null).note)
    }

    @Test
    fun approveOnUnknownIdThrowsNotFound() = runBlocking {
        val store = RequestStore()
        assertFailsWith<NotFoundException> { store.approve("nope", "h") }
        Unit
    }

    @Test
    fun rejectOnUnknownIdThrowsNotFound() = runBlocking {
        val store = RequestStore()
        assertFailsWith<NotFoundException> { store.reject("nope", null) }
        Unit
    }

    @Test
    fun doubleApproveThrowsConflict() = runBlocking {
        val store = RequestStore()
        val created = store.create(makeInput())
        store.approve(created.id, "h1")
        assertFailsWith<ConflictException> { store.approve(created.id, "h2") }
        Unit
    }

    @Test
    fun approvingAnAlreadyRejectedRequestThrowsConflict() = runBlocking {
        val store = RequestStore()
        val created = store.create(makeInput())
        store.reject(created.id, null)
        assertFailsWith<ConflictException> { store.approve(created.id, "h") }
        Unit
    }

    @Test
    fun rejectingAnAlreadyApprovedRequestThrowsConflict() = runBlocking {
        val store = RequestStore()
        val created = store.create(makeInput())
        store.approve(created.id, "h")
        assertFailsWith<ConflictException> { store.reject(created.id, null) }
        Unit
    }

    @Test
    fun roundTripsStateThroughTheStoreFile() = runBlocking {
        val dir = createTempDirectory("coord_store_test")
        try {
            val path = dir.resolve("store.json").toString()
            val writer = RequestStore(storePath = path, idGenerator = counterIds(), clock = { 1000L })
            val a = writer.create(makeInput(amount = "one"))
            val b = writer.create(makeInput(amount = "two"))
            writer.approve(a.id, "tx-hash")
            writer.reject(b.id, "too risky")

            assertTrue(Files.exists(dir.resolve("store.json")))

            val reader = RequestStore(storePath = path)
            reader.load()

            val loaded = reader.list()
            assertEquals(2, loaded.size)
            assertEquals(b.id, loaded[0].id)
            assertEquals(a.id, loaded[1].id)

            val loadedA = assertNotNull(reader.getById(a.id))
            assertEquals(RequestStatus.APPROVED, loadedA.status)
            assertEquals("tx-hash", loadedA.resultHash)
            assertEquals("one", loadedA.amount)

            val loadedB = assertNotNull(reader.getById(b.id))
            assertEquals(RequestStatus.REJECTED, loadedB.status)
            assertEquals("too risky", loadedB.note)
        } finally {
            dir.toFile().deleteRecursively()
        }
    }

    @Test
    fun writesAWellFormedJsonArray() = runBlocking {
        val dir = createTempDirectory("coord_store_test")
        try {
            val path = dir.resolve("store.json").toString()
            val store = RequestStore(storePath = path)
            store.create(makeInput())

            val element = Json.parseToJsonElement(dir.resolve("store.json").readText())
            assertTrue(element is JsonArray)
            assertEquals(1, (element as JsonArray).size)
        } finally {
            dir.toFile().deleteRecursively()
        }
    }

    @Test
    fun loadOnAMissingFileLeavesAnEmptyStore() = runBlocking {
        val dir = createTempDirectory("coord_store_test")
        try {
            val store = RequestStore(storePath = dir.resolve("absent.json").toString())
            store.load()
            assertTrue(store.list().isEmpty())
        } finally {
            dir.toFile().deleteRecursively()
        }
    }

    @Test
    fun loadRejectsANonArrayStoreFile() = runBlocking {
        val dir = createTempDirectory("coord_store_test")
        try {
            val path = dir.resolve("bad.json")
            Files.writeString(path, """{"not":"an array"}""")
            val store = RequestStore(storePath = path.toString())
            assertFailsWith<StoreFormatException> { store.load() }
        } finally {
            dir.toFile().deleteRecursively()
        }
        Unit
    }
}
