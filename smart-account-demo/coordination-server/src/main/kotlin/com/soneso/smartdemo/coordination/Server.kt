package com.soneso.smartdemo.coordination

import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.withCharset
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.application.log
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.contentLength
import io.ktor.server.request.header
import io.ktor.server.request.httpMethod
import io.ktor.server.request.path
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json

/** `Bearer ` prefix on the Authorization header. */
private const val BEARER_PREFIX = "Bearer "

/**
 * Largest request body the API decodes, in bytes. Bodies declaring or carrying
 * more than this are rejected with HTTP 413 before any JSON parsing, bounding the
 * memory a single request can pin. Generous for the small JSON call descriptors
 * this relay handles while still capping abuse.
 */
private const val MAX_REQUEST_BODY_BYTES = 256L * 1024

/**
 * JSON codec for the wire contract: nullable fields are always present (as
 * `null`), and unknown keys in request bodies are ignored so server-assigned
 * fields a client echoes back are dropped on decode. A JSON `null` supplied for
 * a non-nullable field that has a default (the optional `amount`) is coerced to
 * that default rather than rejected, matching the iOS and Dart servers which
 * treat a missing or null amount as the empty string.
 */
internal val wireJson = Json {
    encodeDefaults = true
    explicitNulls = true
    ignoreUnknownKeys = true
    coerceInputValues = true
}

/** Response content type: JSON with an explicit UTF-8 charset, per the wire spec. */
private val jsonContentType = ContentType.Application.Json.withCharset(Charsets.UTF_8)

/**
 * Installs the coordination API onto an [Application].
 *
 * Wiring (outermost first): request logging; permissive CORS on every response
 * with an `OPTIONS` preflight short-circuit; bearer auth (constant-time, with
 * `/health` and `OPTIONS` exempt); error mapping to the `{ "error": ... }`
 * shape; then the routed endpoints. The args of a request are opaque base64
 * strings the server echoes verbatim — it never decodes them.
 */
fun Application.coordinationModule(store: RequestStore, token: String) {
    val expectedToken = token.encodeToByteArray()

    install(StatusPages) {
        exception<ValidationException> { call, cause ->
            call.respondError(HttpStatusCode.BadRequest, cause.message)
        }
        exception<NotFoundException> { call, cause ->
            call.respondError(HttpStatusCode.NotFound, cause.message)
        }
        exception<ConflictException> { call, cause ->
            call.respondError(HttpStatusCode.Conflict, cause.message)
        }
        exception<StoreFormatException> { call, cause ->
            call.respondError(HttpStatusCode.BadRequest, cause.message)
        }
        exception<PayloadTooLargeException> { call, cause ->
            call.respondError(HttpStatusCode.PayloadTooLarge, cause.message)
        }
        exception<Throwable> { call, cause ->
            // Cancellation is cooperative coroutine control flow (client disconnects,
            // shutdown), not a server fault: propagate it untouched so it is not
            // logged as an error or masked behind a 500.
            if (cause is CancellationException) throw cause
            call.application.log.error("Unhandled error", cause)
            call.respondError(HttpStatusCode.InternalServerError, "internal server error")
        }
        // Unmatched routes and wrong-method requests surface as bare status
        // responses; map them to the same JSON error shape so they still carry
        // CORS headers and a structured body. These handlers fire only for
        // bodyless framework responses, not for the bodies produced above.
        status(HttpStatusCode.NotFound) { call, status ->
            call.respondError(status, "resource not found")
        }
        status(HttpStatusCode.MethodNotAllowed) { call, status ->
            call.respondError(status, "method not allowed")
        }
    }

    // Request logging: one line per request through the application logger, which
    // adds its own timestamp. CORS `OPTIONS` preflights are short-circuited noise
    // and are not logged.
    intercept(ApplicationCallPipeline.Monitoring) {
        val start = System.nanoTime()
        proceed()
        if (call.request.httpMethod == HttpMethod.Options) return@intercept
        val millis = (System.nanoTime() - start) / 1_000_000
        val status = call.response.status()?.value ?: 0
        call.application.log.info(
            "${call.request.httpMethod.value} ${call.request.path()} $status ${millis}ms"
        )
    }

    // CORS on every response, OPTIONS preflight short-circuit, and bearer auth.
    intercept(ApplicationCallPipeline.Plugins) {
        val headers = call.response.headers
        headers.append(HttpHeaders.AccessControlAllowOrigin, "*")
        headers.append(HttpHeaders.AccessControlAllowMethods, "GET, POST, OPTIONS")
        headers.append(HttpHeaders.AccessControlAllowHeaders, "Authorization, Content-Type")
        headers.append(HttpHeaders.AccessControlMaxAge, "86400")

        if (call.request.httpMethod == HttpMethod.Options) {
            call.respond(HttpStatusCode.NoContent)
            finish()
            return@intercept
        }

        if (call.request.path() != "/health") {
            val authHeader = call.request.header(HttpHeaders.Authorization)
            if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
                call.respondError(HttpStatusCode.Unauthorized, "missing or malformed Authorization header")
                finish()
                return@intercept
            }
            val presented = authHeader.substring(BEARER_PREFIX.length).encodeToByteArray()
            if (!constantTimeEquals(expectedToken, presented)) {
                call.respondError(HttpStatusCode.Unauthorized, "invalid bearer token")
                finish()
                return@intercept
            }
        }
    }

    routing {
        get("/health") {
            call.respondJson(HttpStatusCode.OK, HealthResponse.serializer(), HealthResponse("ok"))
        }

        post("/requests") {
            val input = decodeBody(call.receiveBoundedText(), CreateRequestInput.serializer(), allowEmpty = false)
                .validated()
            val created = store.create(input)
            call.respondJson(HttpStatusCode.Created, SmartAccountRequest.serializer(), created)
        }

        get("/requests") {
            val statusParam = call.request.queryParameters["status"]
            val status = statusParam?.let { RequestStatus.fromWire(it) }
            val requests = store.list(status)
            call.respondJson(
                HttpStatusCode.OK,
                RequestListResponse.serializer(),
                RequestListResponse(requests),
            )
        }

        get("/requests/{id}") {
            val id = call.parameters["id"].orEmpty()
            val found = store.getById(id) ?: throw NotFoundException("request '$id' not found")
            call.respondJson(HttpStatusCode.OK, SmartAccountRequest.serializer(), found)
        }

        post("/requests/{id}/approve") {
            val id = call.parameters["id"].orEmpty()
            val body = decodeBody(call.receiveBoundedText(), ApproveBody.serializer(), allowEmpty = false)
            val resultHash = body.resultHash
            if (resultHash.isNullOrEmpty()) {
                throw ValidationException("field 'resultHash' must be a non-empty string")
            }
            val updated = store.approve(id, resultHash)
            call.respondJson(HttpStatusCode.OK, SmartAccountRequest.serializer(), updated)
        }

        post("/requests/{id}/reject") {
            val id = call.parameters["id"].orEmpty()
            val body = decodeBody(call.receiveBoundedText(), RejectBody.serializer(), allowEmpty = true)
            val updated = store.reject(id, body.note)
            call.respondJson(HttpStatusCode.OK, SmartAccountRequest.serializer(), updated)
        }
    }
}

/**
 * Builds a Ktor CIO server bound to `0.0.0.0:<port>` serving the coordination
 * API.
 */
fun buildServer(store: RequestStore, token: String, port: Int) =
    embeddedServer(CIO, host = "0.0.0.0", port = port) {
        coordinationModule(store, token)
    }

/**
 * Reads the request body as text, rejecting anything larger than
 * [MAX_REQUEST_BODY_BYTES] with a [PayloadTooLargeException] (HTTP 413).
 *
 * A declared `Content-Length` over the limit is rejected before the body is read,
 * so an oversized request never reaches [receiveText]. Bodies without a usable
 * `Content-Length` (e.g. chunked transfer) are checked again after reading, since
 * their true size is only known once buffered.
 */
private suspend fun ApplicationCall.receiveBoundedText(): String {
    val declaredLength = request.contentLength()
    if (declaredLength != null && declaredLength > MAX_REQUEST_BODY_BYTES) {
        throw PayloadTooLargeException("request body exceeds the $MAX_REQUEST_BODY_BYTES byte limit")
    }
    val text = receiveText()
    if (text.toByteArray(Charsets.UTF_8).size.toLong() > MAX_REQUEST_BODY_BYTES) {
        throw PayloadTooLargeException("request body exceeds the $MAX_REQUEST_BODY_BYTES byte limit")
    }
    return text
}

/**
 * Decodes a JSON object request body into [T].
 *
 * When [allowEmpty] is true an empty body yields an all-defaults object (used by
 * reject, whose note is optional). A malformed or wrongly-shaped body becomes a
 * [ValidationException] mapped to HTTP 400.
 */
private fun <T> decodeBody(text: String, serializer: KSerializer<T>, allowEmpty: Boolean): T {
    val trimmed = text.trim()
    if (trimmed.isEmpty()) {
        if (allowEmpty) {
            return wireJson.decodeFromString(serializer, "{}")
        }
        throw ValidationException("request body must be a JSON object")
    }
    return try {
        wireJson.decodeFromString(serializer, trimmed)
    } catch (e: Exception) {
        throw ValidationException("request body is not a valid JSON object: ${e.message}")
    }
}

private suspend fun <T> ApplicationCall.respondJson(
    status: HttpStatusCode,
    serializer: KSerializer<T>,
    value: T,
) {
    respondText(wireJson.encodeToString(serializer, value), jsonContentType, status)
}

private suspend fun ApplicationCall.respondError(status: HttpStatusCode, message: String?) {
    respondText(
        wireJson.encodeToString(ErrorResponse.serializer(), ErrorResponse(message ?: "error")),
        jsonContentType,
        status,
    )
}

/**
 * Length-aware constant-time byte comparison, so the bearer token cannot be
 * recovered through response timing.
 */
internal fun constantTimeEquals(lhs: ByteArray, rhs: ByteArray): Boolean {
    var diff = if (lhs.size == rhs.size) 0 else 1
    val max = maxOf(lhs.size, rhs.size)
    var index = 0
    while (index < max) {
        val a = if (index < lhs.size) lhs[index].toInt() else 0
        val b = if (index < rhs.size) rhs[index].toInt() else 0
        diff = diff or (a xor b)
        index += 1
    }
    return diff == 0
}
