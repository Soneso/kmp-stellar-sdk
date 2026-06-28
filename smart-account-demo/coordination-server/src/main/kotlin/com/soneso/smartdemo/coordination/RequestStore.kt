package com.soneso.smartdemo.coordination

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.UUID
import kotlin.io.path.exists
import kotlin.io.path.readText

/**
 * In-memory store of [SmartAccountRequest]s with optional JSON-file persistence.
 *
 * Every mutation and read is serialized through a [Mutex], so in-memory state
 * never races. When a store path is configured, each mutation writes the full
 * snapshot to the backing file before returning. The write goes to a temporary
 * file that is atomically renamed over the target, so the store file is never
 * observed in a partially written state.
 */
class RequestStore(
    private val storePath: String? = null,
    private val idGenerator: () -> String = { UUID.randomUUID().toString().lowercase() },
    private val clock: () -> Long = { System.currentTimeMillis() },
) {
    private val mutex = Mutex()

    /** Records keyed by id for O(1) lookup. */
    private val byId = mutableMapOf<String, SmartAccountRequest>()

    /** Insertion order of ids. Reversed when listing so newest appears first. */
    private val order = mutableListOf<String>()

    /** Path of the backing JSON file, or `null` when persistence is disabled. */
    val backingStorePath: String?
        get() = storePath

    /**
     * Loads persisted records when a store path is configured and the file
     * exists. Safe to call once during startup.
     *
     * Throws [StoreFormatException] when the file is not a JSON array of request
     * objects, so a corrupt store fails loudly instead of dropping data.
     */
    suspend fun load() {
        val path = storePath ?: return
        val file = Path.of(path)
        if (!file.exists()) return
        // Read the backing file off the event-loop threads; disk I/O must not
        // block a server dispatcher worker.
        val text = withContext(Dispatchers.IO) { file.readText() }
        if (text.isBlank()) return
        val entries = try {
            json.decodeFromString(ListSerializer(SmartAccountRequest.serializer()), text)
        } catch (e: Exception) {
            throw StoreFormatException("store file is not a JSON array of request objects: ${e.message}")
        }
        mutex.withLock {
            byId.clear()
            order.clear()
            for (request in entries) {
                byId[request.id] = request
                order.add(request.id)
            }
        }
    }

    /**
     * Creates a new pending request from validated [input], assigning the id,
     * `createdAt`, and `pending` status. Persists before returning.
     */
    suspend fun create(input: CreateRequestInput): SmartAccountRequest = mutex.withLock {
        val request = SmartAccountRequest(
            id = idGenerator(),
            smartAccount = input.smartAccount,
            target = input.target,
            targetFn = input.targetFn,
            args = input.args,
            amount = input.amount,
            reason = input.reason,
            status = RequestStatus.PENDING,
            createdAt = clock(),
        )
        byId[request.id] = request
        order.add(request.id)
        flush()
        request
    }

    /** Returns the request with [id], or `null` when absent. */
    suspend fun getById(id: String): SmartAccountRequest? = mutex.withLock {
        byId[id]
    }

    /** Returns stored requests newest-first, optionally filtered by [status]. */
    suspend fun list(status: RequestStatus? = null): List<SmartAccountRequest> = mutex.withLock {
        val result = mutableListOf<SmartAccountRequest>()
        for (i in order.indices.reversed()) {
            val request = byId[order[i]] ?: continue
            if (status == null || request.status == status) {
                result.add(request)
            }
        }
        result
    }

    /**
     * Transitions a pending request to approved, recording [resultHash] and the
     * resolution time. Persists before returning.
     *
     * Throws [NotFoundException] when [id] is unknown and [ConflictException]
     * when the request is already resolved.
     */
    suspend fun approve(id: String, resultHash: String): SmartAccountRequest =
        resolve(id, RequestStatus.APPROVED, resultHash = resultHash, note = null)

    /**
     * Transitions a pending request to rejected, recording the optional [note]
     * and the resolution time. Persists before returning.
     *
     * Throws [NotFoundException] when [id] is unknown and [ConflictException]
     * when the request is already resolved.
     */
    suspend fun reject(id: String, note: String?): SmartAccountRequest =
        resolve(id, RequestStatus.REJECTED, resultHash = null, note = note)

    private suspend fun resolve(
        id: String,
        status: RequestStatus,
        resultHash: String?,
        note: String?,
    ): SmartAccountRequest = mutex.withLock {
        val existing = byId[id] ?: throw NotFoundException("request '$id' not found")
        if (existing.isResolved) {
            throw ConflictException("request '$id' is already ${existing.status.wireName}")
        }
        val updated = existing.resolving(
            status = status,
            resolvedAt = clock(),
            resultHash = resultHash,
            note = note,
        )
        byId[id] = updated
        flush()
        updated
    }

    /**
     * Writes the current snapshot to the backing file atomically.
     *
     * No-op when persistence is disabled. The snapshot preserves insertion order
     * so newest-first listing survives a reload. Called under [mutex].
     *
     * The snapshot is serialized to a string while the caller still holds the
     * lock, but the disk write runs on [Dispatchers.IO] so it never blocks a
     * server event-loop worker thread.
     */
    private suspend fun flush() {
        val path = storePath ?: return
        val snapshot = order.mapNotNull { byId[it] }
        val data = json.encodeToString(ListSerializer(SmartAccountRequest.serializer()), snapshot)
        val target = Path.of(path)
        withContext(Dispatchers.IO) {
            val directory = target.toAbsolutePath().parent
            if (directory != null && !directory.exists()) {
                Files.createDirectories(directory)
            }
            val temp = Files.createTempFile(directory, "coordination-store", ".tmp")
            Files.writeString(temp, data)
            try {
                Files.move(
                    temp,
                    target,
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE,
                )
            } catch (_: Exception) {
                // ATOMIC_MOVE is unsupported on some filesystems; fall back to a
                // replace move so persistence still works for those targets.
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING)
            }
        }
    }

    private companion object {
        val json = Json {
            encodeDefaults = true
            explicitNulls = true
            ignoreUnknownKeys = true
        }
    }
}
