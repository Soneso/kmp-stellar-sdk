// Copyright 2026 Soneso. All rights reserved.
// Use of this source code is governed by a license that can be
// found in the LICENSE file.

package com.soneso.stellar.sdk.sep.sep31

import com.soneso.stellar.sdk.sep.common.jsonElementToAny
import com.soneso.stellar.sdk.sep.common.mapToJsonString
import com.soneso.stellar.sdk.sep.common.sanitizeAnchorString
import com.soneso.stellar.sdk.sep.sep01.StellarToml
import com.soneso.stellar.sdk.sep.sep31.exceptions.Sep31BadRequestException
import com.soneso.stellar.sdk.sep.sep31.exceptions.Sep31ConfigurationException
import com.soneso.stellar.sdk.sep.sep31.exceptions.Sep31CustomerInfoNeededException
import com.soneso.stellar.sdk.sep.sep31.exceptions.Sep31ForbiddenException
import com.soneso.stellar.sdk.sep.sep31.exceptions.Sep31InvalidResponseException
import com.soneso.stellar.sdk.sep.sep31.exceptions.Sep31TransactionCallbackNotSupportedException
import com.soneso.stellar.sdk.sep.sep31.exceptions.Sep31TransactionInfoNeededException
import com.soneso.stellar.sdk.sep.sep31.exceptions.Sep31TransactionNotFoundException
import com.soneso.stellar.sdk.sep.sep31.exceptions.Sep31UnauthorizedException
import com.soneso.stellar.sdk.sep.sep31.exceptions.Sep31UnknownResponseException
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.URLBuilder
import io.ktor.http.content.TextContent
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.readRemaining
import kotlinx.io.readByteArray
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * Maximum length (after percent-decoding) of a transaction id used as a URL
 * path segment. Bounded so anchor-supplied identifiers stay log-friendly and
 * cannot be used to inflate URLs beyond reasonable limits.
 */
private const val PATH_SEGMENT_MAX_LENGTH: Int = 256

/**
 * Path-segment allow-list. RFC 3986 `pchar` minus `/`, `?`, and `#`, plus `-`.
 * Applied after a single percent-decoding pass — see [validatePathSegment].
 */
private val PATH_SEGMENT_REGEX: Regex =
    Regex("^[A-Za-z0-9._~!\$&'()*+,;=:@-]{1,$PATH_SEGMENT_MAX_LENGTH}$")

/**
 * Domain validation reject set per [validateDomain]: rejects empty strings,
 * whitespace, control characters, and the URL-meaningful characters `/`, `?`,
 * `#`. Accepts any remaining printable ASCII or non-ASCII domain content so
 * internationalized domain names (IDN) are not blocked at this layer.
 */
private val DOMAIN_REJECT_CHARS: Set<Char> = setOf('/', '?', '#')

/**
 * Discriminator used by [validateHttpsRequired] to pick the right exception
 * type without echoing user input back into the message.
 */
internal sealed class ErrorTarget {
    object ServiceUrl : ErrorTarget()
    object CallbackUrl : ErrorTarget()
    class DirectPaymentServerForDomain(val domain: String) : ErrorTarget()
}

/**
 * Validates that [url] begins with the lowercase `https://` scheme, OR is an
 * `http://` URL whose host is a loopback address (`localhost`, `127.0.0.1`,
 * or `[::1]`). The loopback carve-out exists so that callers can integrate
 * against a local Anchor Platform instance (commonly `http://localhost:8080`)
 * without standing up a TLS-terminating proxy. Defensive against
 * attacker-supplied or misconfigured anchor URLs: the exception message
 * deliberately omits the offending value to avoid log injection.
 */
private fun validateHttpsRequired(url: String, target: ErrorTarget) {
    if (url.startsWith("https://", ignoreCase = false)) return
    if (isLoopbackHttpUrl(url)) return
    when (target) {
        ErrorTarget.ServiceUrl ->
            throw IllegalArgumentException(
                "SEP-31 service URL must use HTTPS (HTTP allowed only for localhost)",
            )
        is ErrorTarget.DirectPaymentServerForDomain ->
            throw Sep31ConfigurationException(
                "DIRECT_PAYMENT_SERVER for domain ${target.domain} is not HTTPS (HTTP allowed only for localhost)",
            )
        ErrorTarget.CallbackUrl ->
            throw IllegalArgumentException(
                "SEP-31 callback URL must use HTTPS (HTTP allowed only for localhost)",
            )
    }
}

/**
 * Returns `true` if [url] begins with `http://` and has authority equal to
 * one of the three IETF loopback designations: `localhost`, `127.0.0.1`,
 * `[::1]` (each optionally followed by `:port`). Comparison is
 * case-insensitive on the host. Userinfo (`user@host`) and any other host
 * value cause the check to return `false`.
 *
 * The implementation deliberately does string-level extraction instead of
 * delegating to a URL parser. Parsers have implementation-defined
 * behavior on malformed inputs and a permissive parser would accept
 * hosts like `attacker.com@localhost`; the explicit substring check
 * rejects every authority that does not match one of the three exact
 * tokens.
 */
private fun isLoopbackHttpUrl(url: String): Boolean {
    val prefix = "http://"
    if (!url.startsWith(prefix, ignoreCase = false)) return false
    val afterScheme = url.substring(prefix.length)
    val end = afterScheme.indexOfAny(charArrayOf('/', '?', '#'))
    val authority = if (end < 0) afterScheme else afterScheme.substring(0, end)
    return com.soneso.stellar.sdk.sep.common.isLoopbackHost(authority)
}

/**
 * Validates the [domain] string. Rejects empty values, whitespace, control
 * characters, and the URL-meaningful characters `/`, `?`, `#`.
 */
private fun validateDomain(domain: String) {
    if (domain.isEmpty()) {
        throw Sep31ConfigurationException("SEP-31 domain must not be empty")
    }
    for (ch in domain) {
        if (ch.isWhitespace() || ch.isISOControl() || ch in DOMAIN_REJECT_CHARS) {
            throw Sep31ConfigurationException("SEP-31 domain contains an invalid character")
        }
    }
}

/**
 * Validates a transaction id used as a URL path segment. Performs a single
 * percent-decoding pass, then enforces the RFC 3986 `pchar` allow-list (minus
 * `/`, `?`, `#`) and rejects the substring `..`. Validation errors do not echo
 * the offending input.
 */
internal fun validatePathSegment(id: String) {
    if (id.isEmpty()) {
        throw IllegalArgumentException("invalid transaction id")
    }
    val decoded = decodePercentOnce(id)
        ?: throw IllegalArgumentException("invalid transaction id")
    if (!PATH_SEGMENT_REGEX.matches(decoded)) {
        throw IllegalArgumentException("invalid transaction id")
    }
    if (decoded.contains("..")) {
        throw IllegalArgumentException("invalid transaction id")
    }
}

/**
 * Single-pass percent-decoder. Returns `null` if [value] contains a malformed
 * percent escape (`%` not followed by exactly two hex digits) or decodes to a
 * byte sequence that is not valid UTF-8.
 */
private fun decodePercentOnce(value: String): String? {
    if (!value.contains('%')) return value
    val bytes = mutableListOf<Byte>()
    var i = 0
    while (i < value.length) {
        val ch = value[i]
        if (ch == '%') {
            if (i + 2 >= value.length) return null
            val hi = hexDigitValue(value[i + 1]) ?: return null
            val lo = hexDigitValue(value[i + 2]) ?: return null
            bytes.add(((hi shl 4) or lo).toByte())
            i += 3
        } else {
            // Encode the character as UTF-8 bytes.
            val codePoint = ch.code
            when {
                codePoint < 0x80 -> bytes.add(codePoint.toByte())
                codePoint < 0x800 -> {
                    bytes.add((0xC0 or (codePoint shr 6)).toByte())
                    bytes.add((0x80 or (codePoint and 0x3F)).toByte())
                }
                else -> {
                    // Re-encode the UTF-16 code unit naively as a 3-byte UTF-8 sequence. This is
                    // intentionally lax: surrogate halves (0xD800..0xDFFF) and split surrogate
                    // pairs synthesise invalid UTF-8 byte sequences, which the strict-mode
                    // decoder downstream then rejects. The end result is rejection of any
                    // non-ASCII path segment, which is what the RFC 3986 pchar allow-list
                    // requires anyway. Production transaction IDs are ASCII only.
                    bytes.add((0xE0 or (codePoint shr 12)).toByte())
                    bytes.add((0x80 or ((codePoint shr 6) and 0x3F)).toByte())
                    bytes.add((0x80 or (codePoint and 0x3F)).toByte())
                }
            }
            i += 1
        }
    }
    return decodeUtf8OrNull(bytes.toByteArray())
}

/**
 * Returns the integer value of an ASCII hex digit, or `null` if [ch] is not a
 * hex digit. Accepts both upper-case and lower-case.
 */
private fun hexDigitValue(ch: Char): Int? = when (ch) {
    in '0'..'9' -> ch.code - '0'.code
    in 'a'..'f' -> ch.code - 'a'.code + 10
    in 'A'..'F' -> ch.code - 'A'.code + 10
    else -> null
}

/**
 * Decodes [bytes] as UTF-8 in strict mode, returning `null` on malformed input.
 * Delegates to the stdlib's `decodeToString(throwOnInvalidSequence = true)`,
 * which has identical strict-mode semantics across all KMP targets.
 */
private fun decodeUtf8OrNull(bytes: ByteArray): String? =
    runCatching { bytes.decodeToString(throwOnInvalidSequence = true) }.getOrNull()

/**
 * SEP-31 Cross-Border Payments service client.
 *
 * Provides the Sending Anchor side of SEP-31 by talking to a Receiving Anchor's
 * `DIRECT_PAYMENT_SERVER`. Wraps the five HTTP endpoints defined by SEP-0031:
 *
 * - `GET /info` — discover supported assets, limits, and KYC requirements.
 * - `POST /transactions` — initiate a cross-border payment and receive on-chain instructions.
 * - `GET /transactions/:id` — fetch the current state of a transaction.
 * - `PUT /transactions/:id/callback` — register a callback URL for status notifications.
 * - `PATCH /transactions/:id` — update legacy per-transaction fields (deprecated; use SEP-12 instead).
 *
 * ## Security posture
 *
 * - **HTTPS enforcement.** The constructor rejects non-HTTPS [serviceUrl] values, the
 *   companion [Companion.fromDomain] re-validates the resolved `DIRECT_PAYMENT_SERVER`,
 *   and [putTransactionCallback] re-validates the supplied callback URL before any
 *   network call. `http://` is allowed only for the three loopback authorities
 *   (`localhost`, `127.0.0.1`, `[::1]`, each optionally with a port) so callers can
 *   integrate against a local Anchor Platform without standing up a TLS proxy;
 *   every other host must use HTTPS. Validation errors do not echo the offending
 *   input to avoid log-injection.
 * - **Path-segment validation.** Every transaction id interpolated into a URL is
 *   percent-decoded once and matched against the RFC 3986 `pchar` allow-list
 *   (`A-Z`, `a-z`, `0-9`, `-`, `.`, `_`, `~`, `!`, `$`, `&`, `'`, `(`, `)`, `*`, `+`,
 *   `,`, `;`, `=`, `:`, `@`). The substring `..` is additionally rejected to prevent
 *   path-traversal attempts, which is stricter than what RFC 3986 alone requires.
 * - **Redirect handling.** The default HTTP client used by this service sets
 *   `followRedirects = false`. The anchor URL is authoritative; a redirect indicates
 *   misconfiguration or an attack. A caller-supplied [httpClient] is used as-is —
 *   the caller is responsible for redirect policy on the client they pass in.
 * - **Response body caps.** Bodies are size-capped before decoding (2 MB for `/info`,
 *   256 KB for transaction responses). If the response advertises a `Content-Length`
 *   larger than the cap, or streams more bytes than the cap, [Sep31InvalidResponseException]
 *   is raised before any body content is materialized.
 * - **Content-Type allow-list.** Responses must declare `application/json` or
 *   `application/problem+json` (RFC 7807). Other types raise [Sep31InvalidResponseException]
 *   before body read; this defends against anchors returning HTML error pages that
 *   would otherwise reach the error-message extractor.
 * - **No logging plugin by default.** This service does not install Ktor's `Logging`
 *   plugin. If a caller supplies a logging-enabled [httpClient], the caller is
 *   responsible for redaction. JWT bearer tokens, PII (KYC fields, customer ids,
 *   refund memos, names), and full request bodies must be filtered upstream.
 * - **TLS baseline.** TLS minimum version and certificate pinning follow the platform
 *   Ktor client defaults. Production deployments that require pinning or a higher TLS
 *   floor should construct an `HttpClient` with their engine's pinning policy and pass
 *   it via the [httpClient] constructor parameter.
 *
 * ## Typical workflow
 *
 * ```kotlin
 * // 1. Initialize from the receiving anchor's domain.
 * val sep31 = Sep31Service.fromDomain("anchor.example.org")
 *
 * // 2. Discover available assets.
 * val info = sep31.info(jwt = jwtToken)
 * val usdc = info.receiveAssets["USDC"] ?: error("USDC not supported")
 *
 * // 3. Initiate a transaction.
 * val request = Sep31PostTransactionsRequest(
 *     amount = 100.0,
 *     assetCode = "USDC",
 *     fundingMethod = "SWIFT",
 *     senderId = "11111111-1111-1111-1111-111111111111",
 *     receiverId = "22222222-2222-2222-2222-222222222222"
 * )
 * val post = sep31.postTransactions(request, jwtToken)
 *
 * // 4. Poll for status.
 * val transaction = sep31.getTransaction(post.id, jwtToken)
 * println("Status: ${transaction.status}")
 * ```
 *
 * See also:
 * - [Companion.fromDomain] for automatic configuration via stellar.toml
 * - [Sep31PostTransactionsRequest] for request construction
 * - [Sep31TransactionResponse] for transaction state
 * - [SEP-0031 Specification](https://github.com/stellar/stellar-protocol/blob/master/ecosystem/sep-0031.md)
 *
 * @property serviceUrl Base URL of the Receiving Anchor's `DIRECT_PAYMENT_SERVER`.
 *   Must be HTTPS, or `http://` against the loopback authorities `localhost`,
 *   `127.0.0.1`, or `[::1]` for local-development integration.
 * @param httpClient Optional caller-supplied HTTP client. When `null`, the service
 *   constructs a Ktor client with `followRedirects = false`, content negotiation, and a
 *   30 s request timeout. A caller-supplied client is used verbatim and never closed
 *   by this service.
 * @param httpRequestHeaders Optional headers attached to every outbound request in
 *   addition to `Authorization` and `Content-Type`.
 * @throws IllegalArgumentException if [serviceUrl] is neither HTTPS nor HTTP
 *   targeting a loopback authority.
 */
public class Sep31Service(
    public val serviceUrl: String,
    private val httpClient: HttpClient? = null,
    private val httpRequestHeaders: Map<String, String>? = null,
) {

    init {
        validateHttpsRequired(serviceUrl, ErrorTarget.ServiceUrl)
    }

    /**
     * JSON configuration shared across request encoding and response decoding.
     *
     * `ignoreUnknownKeys` accommodates forward-compatible anchor additions.
     * `isLenient` allows numeric-looking strings (some anchors emit `"100"` for
     * `min_amount`) and unquoted-key tolerance during decode.
     */
    private val json: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    /**
     * Base URL with a single trailing slash removed so [buildServiceUrl] can append a
     * relative segment without producing `//` paths.
     */
    private val baseUrl: String = serviceUrl.trimEnd('/')

    public companion object {

        /** Maximum body size for `GET /info` responses. */
        private const val INFO_RESPONSE_CAP_BYTES: Long = 2L * 1024 * 1024

        /** Maximum body size for transaction-shaped responses. */
        private const val TRANSACTION_RESPONSE_CAP_BYTES: Long = 256L * 1024

        /** Connection timeout for SDK-built clients (milliseconds). */
        private const val CONNECT_TIMEOUT_MS: Long = 10_000

        /** Request timeout for SDK-built clients (milliseconds). */
        private const val REQUEST_TIMEOUT_MS: Long = 30_000

        /**
         * Default message used when an authentication-failure response carries no JSON
         * body the SDK can sanitize. Avoids fabricating anchor-side content while still
         * providing a typed exception to the caller.
         */
        private const val DEFAULT_AUTH_FAILURE_MESSAGE: String = "authentication failed"

        /**
         * Creates a [Sep31Service] by discovering the endpoint from stellar.toml.
         *
         * Fetches the stellar.toml file for [domain] via [StellarToml.fromDomain] and
         * extracts `DIRECT_PAYMENT_SERVER`. The TOML fetch follows its own HTTP
         * configuration — including default redirect behavior — so legitimate `www.`
         * canonicalization redirects work as expected. The resolved URL is then
         * re-validated against the HTTPS requirement before the service is constructed.
         *
         * This SDK does not enforce a relationship between the requested [domain] and
         * the host component of the resolved `DIRECT_PAYMENT_SERVER`. A bilateral
         * partner can legitimately advertise a different host (for example,
         * `cdn.payments.example.com` for `example.com`); a hostile partner could too.
         * HTTPS validation is the only transport guarantee.
         *
         * Example:
         * ```kotlin
         * val service = Sep31Service.fromDomain("anchor.example.org")
         * ```
         *
         * @param domain Stellar address domain hosting the stellar.toml file.
         * @param httpClient Optional HTTP client used both for the TOML fetch and for
         *   subsequent SEP-31 calls.
         * @param httpRequestHeaders Optional headers attached to every outbound request.
         * @return A configured [Sep31Service].
         * @throws Sep31ConfigurationException if the domain string is invalid, the TOML
         *   does not advertise `DIRECT_PAYMENT_SERVER`, or the advertised URL is neither
         *   HTTPS nor HTTP against the loopback authorities `localhost`, `127.0.0.1`, `[::1]`.
         */
        public suspend fun fromDomain(
            domain: String,
            httpClient: HttpClient? = null,
            httpRequestHeaders: Map<String, String>? = null,
        ): Sep31Service {
            validateDomain(domain)

            // [StellarToml.fromDomain] does not declare or throw [Sep31ConfigurationException],
            // so the catch block only needs to wrap unexpected exceptions in the configuration
            // error type.
            val toml = try {
                StellarToml.fromDomain(
                    domain = domain,
                    httpClient = httpClient,
                    httpRequestHeaders = httpRequestHeaders,
                )
            } catch (e: Exception) {
                throw Sep31ConfigurationException(
                    "Failed to load stellar.toml for SEP-31 domain configuration",
                    cause = e,
                )
            }

            val directPaymentServer = toml.generalInformation.directPaymentServer
            if (directPaymentServer.isNullOrBlank()) {
                throw Sep31ConfigurationException(
                    "No DIRECT_PAYMENT_SERVER found in stellar.toml for domain $domain",
                )
            }

            validateHttpsRequired(directPaymentServer, ErrorTarget.DirectPaymentServerForDomain(domain))

            return Sep31Service(
                serviceUrl = directPaymentServer,
                httpClient = httpClient,
                httpRequestHeaders = httpRequestHeaders,
            )
        }

    }

    /**
     * Fetches the Receiving Anchor's supported assets, limits, and KYC requirements.
     *
     * Per SEP-31 §"Authentication", every endpoint — including `GET /info` — requires
     * a SEP-10 JWT in the `Authorization: Bearer <jwt>` header. The call goes over the
     * same transport the constructor validated: HTTPS for production hosts, or HTTP
     * only against the loopback authorities accepted at construction time.
     *
     * Pass [lang] to request a non-English variant of human-readable strings; the
     * anchor advertises supported languages out-of-band.
     *
     * Example:
     * ```kotlin
     * val info = sep31Service.info(jwt = jwtToken)
     * val usdc = info.receiveAssets["USDC"] ?: error("USDC not supported")
     * println("Min: ${usdc.minAmount}  Max: ${usdc.maxAmount}")
     * ```
     *
     * @param jwt SEP-10 authentication token.
     * @param lang Optional ISO 639-1 language code passed via the `lang` query parameter.
     * @return The parsed [Sep31InfoResponse].
     * @throws Sep31BadRequestException on HTTP 400.
     * @throws Sep31UnauthorizedException on HTTP 401.
     * @throws Sep31ForbiddenException on HTTP 403 (spec-mandated auth failure path).
     * @throws Sep31InvalidResponseException on HTTP 200 with a malformed body, unexpected
     *   Content-Type, or a response that exceeds 2 MB.
     * @throws Sep31UnknownResponseException on any other HTTP status code (including 3xx,
     *   since `followRedirects = false`).
     */
    public suspend fun info(jwt: String, lang: String? = null): Sep31InfoResponse =
        withHttpClient { client ->
            val url = URLBuilder(buildServiceUrl("info")).apply {
                if (lang != null) parameters.append("lang", lang)
            }.buildString()

            val response = client.get(url) {
                applyHeaders(jwt)
            }
            handleInfoResponse(response)
        }

    /**
     * Initiates a SEP-31 cross-border payment.
     *
     * Sends the supplied [request] to the Receiving Anchor and returns the persistent
     * transaction id plus any on-chain payment instructions the anchor has already
     * determined. The SDK accepts both HTTP 200 and HTTP 201 as success per spec.
     *
     * This method does not retry on network failure. Retrying without out-of-band
     * reconciliation may create duplicate transactions at the anchor.
     *
     * If the anchor responds with HTTP 400 and `error == "customer_info_needed"`, the
     * SDK raises [Sep31CustomerInfoNeededException] with the requested SEP-12 customer
     * type. If `error == "transaction_info_needed"`, the SDK raises
     * [Sep31TransactionInfoNeededException] with a primitive-leafed `fields` map.
     * Other 400 responses surface as generic [Sep31BadRequestException].
     *
     * Example:
     * ```kotlin
     * val request = Sep31PostTransactionsRequest(
     *     amount = 100.0,
     *     assetCode = "USDC",
     *     fundingMethod = "SWIFT",
     *     senderId = "11111111-1111-1111-1111-111111111111",
     *     receiverId = "22222222-2222-2222-2222-222222222222"
     * )
     * val response = sep31Service.postTransactions(request, jwtToken)
     * println("Pay to ${response.stellarAccountId} with memo ${response.stellarMemo}")
     * ```
     *
     * @param request The request body.
     * @param jwt SEP-10 authentication token.
     * @return The parsed [Sep31PostTransactionsResponse].
     * @throws Sep31BadRequestException on a generic HTTP 400.
     * @throws Sep31CustomerInfoNeededException on HTTP 400 with `customer_info_needed`.
     * @throws Sep31TransactionInfoNeededException on HTTP 400 with `transaction_info_needed`.
     * @throws Sep31UnauthorizedException on HTTP 401.
     * @throws Sep31ForbiddenException on HTTP 403.
     * @throws Sep31InvalidResponseException on a 200/201 with a malformed body, unexpected
     *   Content-Type, or a response that exceeds 256 KB.
     * @throws Sep31UnknownResponseException on any other HTTP status code.
     */
    public suspend fun postTransactions(
        request: Sep31PostTransactionsRequest,
        jwt: String,
    ): Sep31PostTransactionsResponse = withHttpClient { client ->
        val url = buildServiceUrl("transactions")
        val response = client.post(url) {
            applyHeaders(jwt)
            setBody(
                TextContent(
                    mapToJsonString(request.toJson(), json),
                    ContentType.Application.Json,
                ),
            )
        }
        handlePostTransactionsResponse(response)
    }

    /**
     * Fetches the current state of a previously-initiated transaction.
     *
     * The [id] must be the value returned by [postTransactions]; it is percent-decoded
     * and validated against the RFC 3986 `pchar` allow-list before being interpolated
     * into the URL.
     *
     * Example:
     * ```kotlin
     * val transaction = sep31Service.getTransaction(
     *     id = "11111111-1111-1111-1111-111111111111",
     *     jwt = jwtToken
     * )
     * println("Status: ${transaction.status}")
     * ```
     *
     * @param id Transaction identifier returned by [postTransactions].
     * @param jwt SEP-10 authentication token.
     * @return The parsed [Sep31TransactionResponse].
     * @throws IllegalArgumentException if [id] fails path-segment validation.
     * @throws Sep31BadRequestException on HTTP 400.
     * @throws Sep31UnauthorizedException on HTTP 401.
     * @throws Sep31ForbiddenException on HTTP 403.
     * @throws Sep31TransactionNotFoundException on HTTP 404.
     * @throws Sep31InvalidResponseException on a 200 with a malformed body, unexpected
     *   Content-Type, or a response that exceeds 256 KB.
     * @throws Sep31UnknownResponseException on any other HTTP status code.
     */
    public suspend fun getTransaction(id: String, jwt: String): Sep31TransactionResponse =
        withHttpClient { client ->
            validatePathSegment(id)
            val url = buildServiceUrl("transactions/$id")
            val response = client.get(url) {
                applyHeaders(jwt)
            }
            handleTransactionResponse(response, transactionId = id)
        }

    /**
     * Registers a callback URL the Receiving Anchor will invoke when the transaction
     * status changes.
     *
     * The [callbackUrl] is validated against the HTTPS requirement before the JWT
     * is sent on the wire. The anchor may indicate that callback registration is not
     * supported by returning HTTP 404; that path raises
     * [Sep31TransactionCallbackNotSupportedException] (distinct from
     * [Sep31TransactionNotFoundException], which signals an unknown transaction id).
     *
     * Callback signature verification is the Sending Anchor application's
     * responsibility per spec. Use
     * [com.soneso.stellar.sdk.sep.common.CallbackSignatureVerifier] to verify
     * inbound `Signature` (or legacy `X-Stellar-Signature`) headers against the
     * Receiving Anchor's `SIGNING_KEY`; the verifier handles header parsing,
     * canonical payload assembly, timestamp freshness, and Ed25519 verification.
     *
     * Example:
     * ```kotlin
     * sep31Service.putTransactionCallback(
     *     id = "11111111-1111-1111-1111-111111111111",
     *     callbackUrl = "https://sending-anchor.example.org/sep31-callback",
     *     jwt = jwtToken
     * )
     * ```
     *
     * @param id Transaction identifier returned by [postTransactions].
     * @param callbackUrl HTTPS URL the anchor will POST status updates to. HTTP is
     *   accepted only against the loopback authorities `localhost`, `127.0.0.1`,
     *   or `[::1]` (each optionally with a port).
     * @param jwt SEP-10 authentication token.
     * @throws IllegalArgumentException if [id] fails path-segment validation or
     *   [callbackUrl] is neither HTTPS nor HTTP against a loopback authority.
     * @throws Sep31BadRequestException on HTTP 400.
     * @throws Sep31UnauthorizedException on HTTP 401.
     * @throws Sep31ForbiddenException on HTTP 403.
     * @throws Sep31TransactionCallbackNotSupportedException on HTTP 404.
     * @throws Sep31UnknownResponseException on any other HTTP status code.
     */
    public suspend fun putTransactionCallback(
        id: String,
        callbackUrl: String,
        jwt: String,
    ): Unit = withHttpClient { client ->
        validatePathSegment(id)
        validateHttpsRequired(callbackUrl, ErrorTarget.CallbackUrl)
        val url = buildServiceUrl("transactions/$id/callback")
        val response = client.put(url) {
            applyHeaders(jwt)
            setBody(
                TextContent(
                    mapToJsonString(mapOf("url" to callbackUrl), json),
                    ContentType.Application.Json,
                ),
            )
        }
        handlePutCallbackResponse(response)
    }

    /**
     * Updates legacy per-transaction fields on a pending transaction.
     *
     * This endpoint supports the `pending_transaction_info_update` workflow defined by
     * SEP-31 v2.5.0. New integrations should register customer KYC via SEP-12
     * `PUT /customer` and pass `sender_id` / `receiver_id` on the original
     * [postTransactions] request instead.
     *
     * Unlike [postTransactions], a 400 response here always surfaces as
     * [Sep31BadRequestException]; the `customer_info_needed` and
     * `transaction_info_needed` error tags are POST-only per spec.
     *
     * Returns [Sep31TransactionResponse] because the SEP-31 spec specifies the PATCH
     * response body must match `GET /transactions/:id`.
     * Surfacing the parsed response makes it observable without a follow-up
     * [getTransaction] call.
     *
     * Example:
     * ```kotlin
     * @Suppress("DEPRECATION")
     * val updated = sep31Service.patchTransaction(
     *     id = "11111111-1111-1111-1111-111111111111",
     *     fields = mapOf(
     *         "transaction" to mapOf(
     *             "receiver_account_number" to "0987654321"
     *         )
     *     ),
     *     jwt = jwtToken
     * )
     * println("Updated status: ${updated.status}")
     * ```
     *
     * @param id Transaction identifier returned by [postTransactions].
     * @param fields Per-transaction field map; the SDK wraps this in a `{"fields": ...}` envelope.
     * @param jwt SEP-10 authentication token.
     * @return The updated [Sep31TransactionResponse] returned by the anchor. See the
     *   spec-divergence paragraph above for why this method returns a value rather
     *   than `Unit`.
     * @throws IllegalArgumentException if [id] fails path-segment validation.
     * @throws Sep31BadRequestException on HTTP 400.
     * @throws Sep31UnauthorizedException on HTTP 401.
     * @throws Sep31ForbiddenException on HTTP 403.
     * @throws Sep31TransactionNotFoundException on HTTP 404.
     * @throws Sep31InvalidResponseException on a 200 with a malformed body, unexpected
     *   Content-Type, or a response that exceeds 256 KB.
     * @throws Sep31UnknownResponseException on any other HTTP status code.
     */
    @Deprecated(
        message = "Use SEP-12 PUT /customer to update KYC fields instead. SEP-31 v2.5.0 " +
            "deprecated per-transaction fields.",
        level = DeprecationLevel.WARNING,
    )
    public suspend fun patchTransaction(
        id: String,
        fields: Map<String, Any?>,
        jwt: String,
    ): Sep31TransactionResponse = withHttpClient { client ->
        validatePathSegment(id)
        val url = buildServiceUrl("transactions/$id")
        val response = client.patch(url) {
            applyHeaders(jwt)
            setBody(
                TextContent(
                    mapToJsonString(mapOf("fields" to fields), json),
                    ContentType.Application.Json,
                ),
            )
        }
        handlePatchTransactionResponse(response, transactionId = id)
    }

    // ===================================================================================
    // Response handlers — one per endpoint so the dispatch matrix is verifiable row-by-row.
    // ===================================================================================

    /**
     * Handles `GET /info` responses: 200 parses the body; 400/401/403 raise typed
     * exceptions with sanitized error context; any other status raises
     * [Sep31UnknownResponseException].
     */
    private suspend fun handleInfoResponse(response: HttpResponse): Sep31InfoResponse {
        return when (val status = response.status.value) {
            200 -> {
                val body = readJsonBody(response, INFO_RESPONSE_CAP_BYTES)
                parseJsonBody(body) { Sep31InfoResponse.fromJson(it) }
            }
            400 -> throwBadRequest(response, INFO_RESPONSE_CAP_BYTES)
            401, 403 -> throwAuthFailure(response, status, INFO_RESPONSE_CAP_BYTES)
            else -> throwUnknownResponse(response, INFO_RESPONSE_CAP_BYTES)
        }
    }

    /**
     * Handles `POST /transactions` responses. 200 and 201 both succeed and parse the
     * body. 400 routes through [dispatchPostTransactionsBadRequest] for the two
     * domain-specific sub-cases (`customer_info_needed`, `transaction_info_needed`).
     * 401/403 raise typed exceptions; any other status raises
     * [Sep31UnknownResponseException].
     */
    private suspend fun handlePostTransactionsResponse(
        response: HttpResponse,
    ): Sep31PostTransactionsResponse {
        return when (val status = response.status.value) {
            200, 201 -> {
                val body = readJsonBody(response, TRANSACTION_RESPONSE_CAP_BYTES)
                parseJsonBody(body) { Sep31PostTransactionsResponse.fromJson(it) }
            }
            400 -> {
                val body = readJsonBody(response, TRANSACTION_RESPONSE_CAP_BYTES)
                dispatchPostTransactionsBadRequest(body)
            }
            401, 403 -> throwAuthFailure(response, status, TRANSACTION_RESPONSE_CAP_BYTES)
            else -> throwUnknownResponse(response, TRANSACTION_RESPONSE_CAP_BYTES)
        }
    }

    /**
     * Dispatches a `POST /transactions` HTTP 400 body to the appropriate exception.
     * `customer_info_needed` and `transaction_info_needed` are POST-only per spec.
     *
     * The body is parsed once into [parsed]; both the success-path branches and the
     * generic fallback reuse that decoded tree to avoid a redundant second parse pass.
     */
    @Suppress("DEPRECATION")
    private fun dispatchPostTransactionsBadRequest(body: String): Nothing {
        val parsed: JsonObject? = try {
            json.decodeFromString(JsonObject.serializer(), body)
        } catch (_: IllegalArgumentException) {
            null
        }

        val errorTag: String? = (parsed?.get("error") as? JsonPrimitive)?.contentOrNull
        val rawBody = rawResponseBodyFor(body)

        when (errorTag) {
            "customer_info_needed" -> {
                val type: String? = (parsed?.get("type") as? JsonPrimitive)?.contentOrNull
                throw Sep31CustomerInfoNeededException(type = type, rawResponseBody = rawBody)
            }
            "transaction_info_needed" -> {
                val fieldsObject = parsed?.get("fields") as? JsonObject
                @Suppress("UNCHECKED_CAST")
                val fields: Map<String, Any?>? = fieldsObject?.let {
                    jsonElementToAny(it) as? Map<String, Any?>
                }
                throw Sep31TransactionInfoNeededException(fields = fields, rawResponseBody = rawBody)
            }
            else -> throw Sep31BadRequestException(
                message = extractErrorMessageFromParsed(parsed, body),
                statusCode = 400,
                rawResponseBody = rawBody,
            )
        }
    }

    /**
     * Handles `GET /transactions/:id` responses: 200 parses the body; 400/401/403/404
     * raise typed exceptions. The supplied [transactionId] has already passed
     * [validatePathSegment], so interpolating it into the 404 message is safe (no
     * log-injection risk). Any other status raises [Sep31UnknownResponseException].
     */
    private suspend fun handleTransactionResponse(
        response: HttpResponse,
        transactionId: String,
    ): Sep31TransactionResponse {
        return when (val status = response.status.value) {
            200 -> {
                val body = readJsonBody(response, TRANSACTION_RESPONSE_CAP_BYTES)
                parseJsonBody(body) { Sep31TransactionResponse.fromJson(it) }
            }
            400 -> throwBadRequest(response, TRANSACTION_RESPONSE_CAP_BYTES)
            401, 403 -> throwAuthFailure(response, status, TRANSACTION_RESPONSE_CAP_BYTES)
            404 -> throwTransactionNotFound(response, transactionId, TRANSACTION_RESPONSE_CAP_BYTES)
            else -> throwUnknownResponse(response, TRANSACTION_RESPONSE_CAP_BYTES)
        }
    }

    /**
     * Handles `PUT /transactions/:id/callback` responses. 204 No Content is the
     * success path; 400/401/403 raise typed exceptions; 404 maps to the
     * callback-not-supported exception (not [Sep31TransactionNotFoundException]).
     * Any other status raises [Sep31UnknownResponseException].
     */
    private suspend fun handlePutCallbackResponse(response: HttpResponse) {
        when (val status = response.status.value) {
            204 -> return
            400 -> throwBadRequest(response, TRANSACTION_RESPONSE_CAP_BYTES)
            401, 403 -> throwAuthFailure(response, status, TRANSACTION_RESPONSE_CAP_BYTES)
            404 -> throwCallbackNotSupported(response, TRANSACTION_RESPONSE_CAP_BYTES)
            else -> throwUnknownResponse(response, TRANSACTION_RESPONSE_CAP_BYTES)
        }
    }

    /**
     * Handles `PATCH /transactions/:id` responses: 200 parses the body; 400/401/403/404
     * raise typed exceptions; any other status raises [Sep31UnknownResponseException].
     * Unlike [handlePostTransactionsResponse], a 400 here is always generic —
     * `customer_info_needed` and `transaction_info_needed` are POST-only per spec.
     */
    private suspend fun handlePatchTransactionResponse(
        response: HttpResponse,
        transactionId: String,
    ): Sep31TransactionResponse {
        return when (val status = response.status.value) {
            200 -> {
                val body = readJsonBody(response, TRANSACTION_RESPONSE_CAP_BYTES)
                parseJsonBody(body) { Sep31TransactionResponse.fromJson(it) }
            }
            400 -> throwBadRequest(response, TRANSACTION_RESPONSE_CAP_BYTES)
            401, 403 -> throwAuthFailure(response, status, TRANSACTION_RESPONSE_CAP_BYTES)
            404 -> throwTransactionNotFound(response, transactionId, TRANSACTION_RESPONSE_CAP_BYTES)
            else -> throwUnknownResponse(response, TRANSACTION_RESPONSE_CAP_BYTES)
        }
    }

    /**
     * Reads the response body once, then throws the auth-failure exception matching
     * [statusCode] (401 → [Sep31UnauthorizedException], 403 → [Sep31ForbiddenException]).
     */
    private suspend fun throwAuthFailure(
        response: HttpResponse,
        statusCode: Int,
        maxBytes: Long,
    ): Nothing {
        val (message, rawBody) = readBoundedErrorContext(response, maxBytes)
        when (statusCode) {
            401 -> throw Sep31UnauthorizedException(
                message = message,
                statusCode = 401,
                rawResponseBody = rawBody,
            )
            403 -> throw Sep31ForbiddenException(
                message = message,
                statusCode = 403,
                rawResponseBody = rawBody,
            )
            else -> error("throwAuthFailure called with non-auth status $statusCode")
        }
    }

    /**
     * Reads the response body once, extracts a sanitized error message, and throws
     * [Sep31BadRequestException].
     */
    private suspend fun throwBadRequest(
        response: HttpResponse,
        maxBytes: Long,
    ): Nothing {
        val body = readJsonBody(response, maxBytes)
        throw Sep31BadRequestException(
            message = extractErrorMessage(body),
            statusCode = 400,
            rawResponseBody = rawResponseBodyFor(body),
        )
    }

    /**
     * Reads the response body once (for `rawResponseBody` only) and throws
     * [Sep31TransactionNotFoundException]. The message is fully derived from the
     * caller-supplied [transactionId], which has already passed [validatePathSegment],
     * so no anchor-supplied content is interpolated.
     */
    private suspend fun throwTransactionNotFound(
        response: HttpResponse,
        transactionId: String,
        maxBytes: Long,
    ): Nothing {
        val (_, rawBody) = readBoundedErrorContext(response, maxBytes)
        throw Sep31TransactionNotFoundException(
            message = "transaction not found for id $transactionId",
            statusCode = 404,
            rawResponseBody = rawBody,
        )
    }

    /**
     * Reads the response body once (for `rawResponseBody` only) and throws
     * [Sep31TransactionCallbackNotSupportedException]. The message is a fixed string
     * (no anchor content interpolated).
     */
    private suspend fun throwCallbackNotSupported(
        response: HttpResponse,
        maxBytes: Long,
    ): Nothing {
        val (_, rawBody) = readBoundedErrorContext(response, maxBytes)
        throw Sep31TransactionCallbackNotSupportedException(
            message = "transaction callback not supported",
            statusCode = 404,
            rawResponseBody = rawBody,
        )
    }

    // ===================================================================================
    // Body reading, parsing, and sanitization helpers.
    // ===================================================================================

    /**
     * Reads a JSON-bodied response with size and Content-Type checks applied.
     *
     * Steps:
     * 1. Verify the Content-Type is `application/json` or `application/problem+json`,
     *    parameters allowed (case-insensitive).
     * 2. If `Content-Length` is present and exceeds [maxBytes], reject without reading.
     * 3. Otherwise stream up to [maxBytes] + 1 bytes from the response channel and
     *    reject if the channel still has bytes available afterwards.
     */
    private suspend fun readJsonBody(response: HttpResponse, maxBytes: Long): String {
        verifyJsonContentType(response)
        return readBoundedText(response, maxBytes)
    }

    /**
     * Reads an error response body and returns both the sanitized message string and
     * the JWT-preserving raw body for the debug-only `rawResponseBody` exception field.
     * If the Content-Type is not JSON-like, or if the body cannot be read within
     * [maxBytes], returns the default auth-failure message and `null` for the raw body
     * (no body was captured, so nothing meaningful to surface for debugging).
     */
    private suspend fun readBoundedErrorContext(
        response: HttpResponse,
        maxBytes: Long,
    ): Pair<String, String?> {
        val contentType = response.headers[HttpHeaders.ContentType]
        val isJsonLike = contentType != null && isAcceptableContentType(contentType)
        if (!isJsonLike) {
            return DEFAULT_AUTH_FAILURE_MESSAGE to null
        }
        val body = try {
            readBoundedText(response, maxBytes)
        } catch (_: Sep31InvalidResponseException) {
            return DEFAULT_AUTH_FAILURE_MESSAGE to null
        }
        return extractErrorMessage(body) to rawResponseBodyFor(body)
    }

    /**
     * Returns the JWT-preserving sanitized form of [body] suitable for the
     * `rawResponseBody` exception field, or `null` if [body] is empty.
     */
    private fun rawResponseBodyFor(body: String): String? {
        if (body.isEmpty()) return null
        return sanitizeAnchorString(body, redactJwts = false).takeIf { it.isNotEmpty() }
    }

    /**
     * Verifies the response Content-Type matches an accepted JSON variant. 204 No
     * Content responses have no body and no Content-Type — the caller must not invoke
     * this for 204.
     */
    private fun verifyJsonContentType(response: HttpResponse) {
        val contentType = response.headers[HttpHeaders.ContentType]
            ?: throw Sep31InvalidResponseException("missing Content-Type header")
        if (!isAcceptableContentType(contentType)) {
            throw Sep31InvalidResponseException(
                "unexpected Content-Type: ${sanitizeAnchorString(contentType)}",
            )
        }
    }

    /**
     * Returns `true` when [value] parses as `application/json` or
     * `application/problem+json`. Parameters (for example `; charset=utf-8`) are ignored
     * during comparison; matching is case-insensitive.
     */
    private fun isAcceptableContentType(value: String): Boolean {
        val parsed = try {
            ContentType.parse(value)
        } catch (_: Throwable) {
            return false
        }
        if (parsed.contentType.equals("application", ignoreCase = true)) {
            val subtype = parsed.contentSubtype
            if (subtype.equals("json", ignoreCase = true)) return true
            if (subtype.equals("problem+json", ignoreCase = true)) return true
        }
        return false
    }

    /**
     * Reads up to [maxBytes] from the response body. Enforces:
     *
     * - **Content-Length pre-check.** If the response advertises a `Content-Length`
     *   header strictly larger than [maxBytes], throws before touching the body
     *   channel.
     * - **Streaming cap.** Reads at most [maxBytes] + 1 bytes from the response
     *   channel. If the read returns more than [maxBytes] bytes (the channel had at
     *   least one more byte), throws [Sep31InvalidResponseException]. Otherwise the
     *   body fits and is decoded as UTF-8.
     */
    private suspend fun readBoundedText(response: HttpResponse, maxBytes: Long): String {
        val advertised = response.headers[HttpHeaders.ContentLength]?.toLongOrNull()
        if (advertised != null && advertised > maxBytes) {
            throw Sep31InvalidResponseException(
                "response too large: $advertised > $maxBytes",
            )
        }
        val channel = response.bodyAsChannel()
        val source = channel.readRemaining(maxBytes + 1)
        val bytes = source.readByteArray()
        if (bytes.size > maxBytes) {
            throw Sep31InvalidResponseException(
                "response too large: streamed ${bytes.size} bytes > $maxBytes",
            )
        }
        return bytes.decodeToString()
    }

    /**
     * Parses [body] as a [JsonObject] and applies [transform]. Wraps
     * [IllegalArgumentException] (which `kotlinx.serialization.SerializationException`
     * extends) in [Sep31InvalidResponseException] so callers see a typed SEP-31 failure.
     */
    private inline fun <T> parseJsonBody(
        body: String,
        transform: (JsonObject) -> T,
    ): T {
        val rawBody = rawResponseBodyFor(body)
        val parsed = try {
            json.decodeFromString(JsonObject.serializer(), body)
        } catch (e: IllegalArgumentException) {
            throw Sep31InvalidResponseException(
                message = "Failed to parse SEP-31 response: ${sanitizeAnchorString(e.message ?: "malformed JSON")}",
                rawResponseBody = rawBody,
            )
        }
        return try {
            transform(parsed)
        } catch (e: Sep31InvalidResponseException) {
            // The fromJson factories don't have the original body string, so they cannot
            // populate rawResponseBody themselves. Rewrap to attach the raw body captured
            // here, preserving the original message verbatim.
            throw Sep31InvalidResponseException(
                message = e.message ?: "Invalid SEP-31 response",
                rawResponseBody = rawBody,
            )
        } catch (e: IllegalArgumentException) {
            throw Sep31InvalidResponseException(
                message = "Failed to decode SEP-31 response: ${sanitizeAnchorString(e.message ?: "malformed JSON")}",
                rawResponseBody = rawBody,
            )
        }
    }

    /**
     * Reads the body of an unknown-status response (respecting [maxBytes]) and throws
     * [Sep31UnknownResponseException]. Body capture failures fall back to an empty
     * string so the caller still sees the original status code.
     */
    private suspend fun throwUnknownResponse(
        response: HttpResponse,
        maxBytes: Long,
    ): Nothing {
        val statusCode = response.status.value
        val originalBody = try {
            readBoundedText(response, maxBytes)
        } catch (_: Sep31InvalidResponseException) {
            ""
        }
        val sanitized = sanitizeAnchorString(originalBody)
        throw Sep31UnknownResponseException(
            message = "Unexpected HTTP status: $statusCode",
            statusCode = statusCode,
            responseBody = sanitized,
            rawResponseBody = rawResponseBodyFor(originalBody),
        )
    }

    /**
     * Extracts a sanitized, truncated error message from a JSON-bodied error response.
     * Returns the literal `"error"` field when present; otherwise returns the body
     * itself sanitized and truncated.
     */
    private fun extractErrorMessage(body: String): String {
        val parsed: JsonObject? = try {
            json.decodeFromString(JsonObject.serializer(), body)
        } catch (_: IllegalArgumentException) {
            null
        }
        return extractErrorMessageFromParsed(parsed, body)
    }

    /**
     * Shared tail of [extractErrorMessage] and the generic fallback path in
     * [dispatchPostTransactionsBadRequest]. Reads the `error` field from an already
     * decoded [parsed] tree; falls back to the raw [body] when the field is missing
     * or the tree is `null` (parse failure). The returned string is always sanitized
     * via [sanitizeAnchorString].
     */
    private fun extractErrorMessageFromParsed(parsed: JsonObject?, body: String): String {
        val raw: String? = try {
            parsed?.get("error")?.jsonPrimitive?.contentOrNull
        } catch (_: IllegalArgumentException) {
            null
        }
        val source = raw ?: body
        return sanitizeAnchorString(source)
    }

    // ===================================================================================
    // HTTP plumbing helpers.
    // ===================================================================================

    /**
     * Returns `"$baseUrl/$segment"`. [segment] must not begin with `/`; mutating
     * methods pass already-validated path segments so this helper performs no escaping.
     */
    private fun buildServiceUrl(segment: String): String = "$baseUrl/$segment"

    /**
     * Adds the `Authorization: Bearer $jwt` header plus every entry from
     * [httpRequestHeaders]. Generic helper used by every public method to apply the
     * Authorization header alongside caller-supplied headers uniformly.
     */
    private fun HttpRequestBuilder.applyHeaders(jwt: String) {
        header(HttpHeaders.Authorization, "Bearer $jwt")
        httpRequestHeaders?.forEach { (key, value) -> header(key, value) }
    }

    /**
     * Runs [block] with a Ktor [HttpClient].
     *
     * If [httpClient] was supplied at construction time, that client is used as-is and
     * is not closed afterwards (it is owned by the caller). Otherwise a temporary
     * client is created with `followRedirects = false`, `ContentNegotiation`, and a
     * 30-second request timeout, and is closed after [block] returns.
     */
    private suspend fun <T> withHttpClient(block: suspend (HttpClient) -> T): T {
        val client = httpClient ?: HttpClient {
            followRedirects = false
            install(ContentNegotiation) {
                json(json)
            }
            install(HttpTimeout) {
                connectTimeoutMillis = CONNECT_TIMEOUT_MS
                requestTimeoutMillis = REQUEST_TIMEOUT_MS
            }
        }
        return try {
            block(client)
        } finally {
            if (httpClient == null) {
                client.close()
            }
        }
    }

}
