// Copyright 2026 Soneso. All rights reserved.
// Use of this source code is governed by a license that can be
// found in the LICENSE file.

package com.soneso.stellar.sdk.sep.common

import com.soneso.stellar.sdk.KeyPair
import io.ktor.http.Url
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.math.absoluteValue
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * Verifies callback signatures from SEP-12 and SEP-31 anchor servers.
 *
 * Anchors sign outbound webhook requests with their `SIGNING_KEY` so that the
 * receiving party can prove authenticity of the inbound callback. Both SEP-12
 * (KYC customer status updates) and SEP-31 (cross-border payment status
 * updates) define the same signing format, and this class serves both.
 *
 * ## Signature header
 *
 * The anchor sends one of:
 *
 * - `Signature: t=<unix-seconds>, s=<base64-signature>` (preferred)
 * - `X-Stellar-Signature: t=<unix-seconds>, s=<base64-signature>` (legacy)
 *
 * When both headers are present the verifier always uses `Signature`. A
 * malformed `Signature` returns [Result.MalformedHeader] even if
 * `X-Stellar-Signature` would have verified — the preference for the
 * non-deprecated header is firm and there is no silent fallback.
 *
 * ## Canonical payload
 *
 * The signed bytes are `"$timestamp.$host.$body"` encoded as UTF-8, where:
 *
 * - `timestamp` is the Unix seconds value from the header `t=` parameter.
 * - `host` is derived from the URL passed to the constructor as
 *   [registeredCallbackUrl] (port is stripped).
 * - `body` is the byte-exact inbound request body, decoded as UTF-8.
 *
 * The host is taken from the constructor-supplied URL and never from any
 * inbound HTTP header. This pins the verification to the URL the consumer
 * previously registered with the anchor and prevents man-in-the-middle
 * tampering with a `Host` header.
 *
 * ## Port stripping
 *
 * The verifier strips port from the host (e.g., `myapp.com:8443` becomes
 * `myapp.com`). The SEP specifications do not say whether the port should be
 * included in the signed payload, and the SDK pins to host-only for
 * unambiguity. An anchor that signs with port included will fail with
 * [Result.SignatureMismatch]; file an upstream bug against that anchor.
 *
 * ## Freshness window
 *
 * The default freshness window is 120 seconds, which matches the SEP-31
 * specification's recommendation. The verifier applies a two-sided check:
 * `abs(currentTime - timestamp) > freshnessSeconds` is rejected. The
 * specifications define a one-sided check that lets future-dated timestamps
 * through; the two-sided form rejects them as well and is documented as the
 * behavior of this class. [Result.Stale.ageSeconds] carries a signed value so
 * callers can distinguish replay (positive age) from clock-skew or forgery
 * (negative age) in logs.
 *
 * Setting [freshnessSeconds] above 120 deviates from the spec recommendation
 * and exists only for clock-skew tolerance in test environments. Production
 * should keep the default.
 *
 * ## stellar.toml
 *
 * The verifier does NOT fetch the anchor's `stellar.toml`. The caller looks up
 * the anchor's `SIGNING_KEY` once and caches it for the lifetime of the
 * connection:
 *
 * ```kotlin
 * val signingKey = StellarToml.fromDomain("anchor.example.org")
 *     .generalInformation.signingKey
 *     ?: error("Receiving Anchor stellar.toml has no SIGNING_KEY")
 * ```
 *
 * ## HTTPS requirement
 *
 * The constructor rejects non-HTTPS URLs unless the host is one of the IETF
 * loopback authorities (`localhost`, `127.0.0.1`, `[::1]`, each optionally
 * with a port). The loopback exception exists so that callers can integrate
 * against a local Anchor Platform instance during development without
 * standing up a TLS-terminating proxy.
 *
 * ## Thread safety
 *
 * Instances are immutable and safe to share across threads. Construct one
 * verifier per registered callback URL and reuse it for every inbound
 * callback against that URL.
 *
 * ## Example: SEP-31 callback verification
 *
 * ```kotlin
 * val verifier = CallbackSignatureVerifier(
 *     signingKey = receivingAnchorSigningKey,
 *     registeredCallbackUrl = "https://sending-anchor.example.org/sep31-callback",
 * )
 *
 * when (val result = verifier.verify(
 *     signatureHeader = call.request.headers["Signature"],
 *     xStellarSignatureHeader = call.request.headers["X-Stellar-Signature"],
 *     body = call.receiveText(),
 * )) {
 *     CallbackSignatureVerifier.Result.Valid -> processCallback()
 *     is CallbackSignatureVerifier.Result.Stale ->
 *         log.warn("Callback stale by ${result.ageSeconds}s")
 *     CallbackSignatureVerifier.Result.SignatureMismatch,
 *     CallbackSignatureVerifier.Result.MalformedHeader,
 *     CallbackSignatureVerifier.Result.MissingHeader -> reject()
 * }
 * ```
 *
 * @see <a href="https://github.com/stellar/stellar-protocol/blob/master/ecosystem/sep-0031.md">SEP-0031</a>
 * @see <a href="https://github.com/stellar/stellar-protocol/blob/master/ecosystem/sep-0012.md">SEP-0012</a>
 */
@OptIn(ExperimentalEncodingApi::class, ExperimentalTime::class)
public class CallbackSignatureVerifier internal constructor(
    private val signingKey: String,
    registeredCallbackUrl: String?,
    private val freshnessSeconds: Long,
    private val clock: Clock,
    hostOverride: String?,
    private val oneSidedFreshness: Boolean,
) {

    /**
     * Constructs a verifier for callbacks delivered to [registeredCallbackUrl].
     *
     * @param signingKey The anchor's `SIGNING_KEY` from its stellar.toml (G... Ed25519 address).
     * @param registeredCallbackUrl The HTTPS URL that the consumer previously registered with
     *   the anchor as the callback destination. The verifier derives the canonical host from
     *   this URL (port stripped). HTTP is allowed only when the host is a loopback authority.
     * @param freshnessSeconds Maximum tolerated `|now - signedTimestamp|` in seconds. Must be
     *   in `1..600`. Defaults to 120 to match the SEP-31 specification recommendation. Values
     *   above 120 deviate from the spec and should only be used to absorb test-environment
     *   clock skew.
     * @param clock Clock used to read the current time. Defaults to [Clock.System]. Tests can
     *   inject a fixed-time clock to verify freshness boundaries deterministically.
     *
     * @throws IllegalArgumentException if [signingKey] is malformed, [registeredCallbackUrl]
     *   is malformed or uses an unsupported scheme, or [freshnessSeconds] is out of range.
     */
    public constructor(
        signingKey: String,
        registeredCallbackUrl: String,
        freshnessSeconds: Long = DEFAULT_FRESHNESS_SECONDS,
        clock: Clock = Clock.System,
    ) : this(
        signingKey = signingKey,
        registeredCallbackUrl = registeredCallbackUrl,
        freshnessSeconds = freshnessSeconds,
        clock = clock,
        hostOverride = null,
        oneSidedFreshness = false,
    )

    private val keyPair: KeyPair
    private val canonicalHost: String

    init {
        require(freshnessSeconds in 1..MAX_FRESHNESS_SECONDS) {
            "freshnessSeconds must be 1..$MAX_FRESHNESS_SECONDS (spec recommends <=$DEFAULT_FRESHNESS_SECONDS; >$DEFAULT_FRESHNESS_SECONDS deviates and is for clock-skew tolerance only)"
        }
        keyPair = KeyPair.fromAccountId(signingKey)
        canonicalHost = if (hostOverride != null) {
            // Shim path: skip URL parsing and scheme validation entirely; trust the caller's host string.
            // Gate is `hostOverride != null` (NOT `hostOverride.isNotEmpty()`) — an empty string is
            // a deliberate supported case to preserve v0.6.0 behaviour, where callers could pass
            // `expectedHost = ""` and the verifier would assemble `"$t..$body"` and verify against
            // whatever the signer signed against an identical empty-host payload. Also covers
            // host-with-port (e.g. "localhost:8080", "myapp.com:8443") for the same reason.
            hostOverride
        } else {
            // Reached only from the public constructor (which always passes a non-null
            // `registeredCallbackUrl`). `requireNotNull` defends against future factory
            // additions that forget to set `hostOverride`.
            val callbackUrl = requireNotNull(registeredCallbackUrl) {
                "registeredCallbackUrl must be non-null in the public-constructor branch"
            }
            val parsed = try {
                Url(callbackUrl)
            } catch (e: Exception) {
                throw IllegalArgumentException(
                    "registeredCallbackUrl is not a valid URL: $callbackUrl",
                    e,
                )
            }
            require(parsed.protocol.name == "https" || isLoopbackHost(parsed.host)) {
                "registeredCallbackUrl must be https:// (or http:// against loopback for development)"
            }
            parsed.host
        }
    }

    /**
     * Verifies a callback signature against the registered URL's host and the supplied body.
     *
     * The verifier prefers [signatureHeader] over [xStellarSignatureHeader] when both are
     * provided. A malformed `Signature` returns [Result.MalformedHeader] even when the
     * legacy header would have verified — the preference is firm.
     *
     * @param signatureHeader Value of the `Signature` HTTP header, or `null` if absent.
     * @param xStellarSignatureHeader Value of the deprecated `X-Stellar-Signature` HTTP
     *   header, or `null` if absent.
     * @param body The byte-exact inbound request body, decoded as UTF-8.
     * @return [Result.Valid] on success; one of [Result.MissingHeader], [Result.MalformedHeader],
     *   [Result.Stale], or [Result.SignatureMismatch] on failure.
     */
    public suspend fun verify(
        signatureHeader: String?,
        xStellarSignatureHeader: String?,
        body: String,
    ): Result {
        val rawHeader = pickHeader(signatureHeader, xStellarSignatureHeader)
            ?: return Result.MissingHeader

        val match = HEADER_REGEX.matchEntire(rawHeader.trim())
            ?: return Result.MalformedHeader

        val timestamp = match.groupValues[1].toLongOrNull()
            ?: return Result.MalformedHeader
        val signatureBase64 = match.groupValues[2]

        val signatureBytes = try {
            Base64.decode(signatureBase64)
        } catch (_: IllegalArgumentException) {
            try {
                Base64.UrlSafe.decode(signatureBase64)
            } catch (_: IllegalArgumentException) {
                return Result.MalformedHeader
            }
        }

        val ageSeconds = clock.now().epochSeconds - timestamp
        val stale = if (oneSidedFreshness) {
            ageSeconds > freshnessSeconds
        } else {
            ageSeconds.absoluteValue > freshnessSeconds
        }
        if (stale) {
            return Result.Stale(ageSeconds)
        }

        val payload = "$timestamp.$canonicalHost.$body".encodeToByteArray()
        val verified = try {
            keyPair.verify(payload, signatureBytes)
        } catch (_: Throwable) {
            return Result.MalformedHeader
        }
        return if (verified) Result.Valid else Result.SignatureMismatch
    }

    private fun pickHeader(signatureHeader: String?, xStellarSignatureHeader: String?): String? {
        val primary = signatureHeader?.takeIf { it.isNotBlank() }
        if (primary != null) return primary
        val legacy = xStellarSignatureHeader?.takeIf { it.isNotBlank() }
        return legacy
    }

    /**
     * Outcome of a [verify] call.
     *
     * Callers typically branch on this sealed type to distinguish replay from forgery in
     * logs. All non-[Valid] outcomes are equally a refusal to process the callback; the
     * granularity exists for diagnostics.
     */
    public sealed interface Result {
        /** The signature is valid for the supplied body, freshness window, and host. */
        public data object Valid : Result

        /** Both `Signature` and `X-Stellar-Signature` were null or blank. */
        public data object MissingHeader : Result

        /**
         * The selected header did not match the expected `t=<digits>, s=<base64>` shape,
         * the base64 payload failed to decode, or the signature bytes were rejected by the
         * Ed25519 layer (e.g., wrong byte length).
         */
        public data object MalformedHeader : Result

        /**
         * The signed timestamp is outside the configured freshness window.
         *
         * @property ageSeconds Signed value of `currentTime - signedTimestamp`. Positive
         *   values indicate a stale past timestamp (typical replay). Negative values
         *   indicate a future-dated timestamp (clock skew or forgery attempt). The
         *   verifier rejects both equally; the sign exists for logging only.
         */
        public data class Stale(val ageSeconds: Long) : Result

        /** The cryptographic verification of an otherwise well-formed header failed. */
        public data object SignatureMismatch : Result
    }

    public companion object {
        /**
         * Default freshness window in seconds. Matches the SEP-31 specification's
         * recommendation; setting [freshnessSeconds] above this value deviates from
         * the spec and should only be used to absorb test-environment clock skew.
         */
        public const val DEFAULT_FRESHNESS_SECONDS: Long = 120

        /**
         * Maximum permitted [freshnessSeconds] value. Values above this are rejected
         * with [IllegalArgumentException] at construction time; the upper bound exists
         * to keep the freshness window from becoming a security footgun.
         */
        public const val MAX_FRESHNESS_SECONDS: Long = 600

        private val HEADER_REGEX = Regex("^t=(\\d+),\\s*s=([A-Za-z0-9+/_\\-=]+)$")

        /**
         * Internal factory used only by the deprecated
         * `com.soneso.stellar.sdk.sep.sep12.CallbackSignatureVerifier` shim to preserve
         * v0.6.0 observable behaviour:
         *
         * - [expectedHost] is used verbatim. No URL parsing, no port stripping, no HTTPS
         *   scheme check.
         * - Freshness is one-sided: future-dated timestamps are accepted until
         *   `currentTime - signedTimestamp > freshnessSeconds`.
         *
         * Not intended for new callers. Will be removed together with the SEP-12 shim.
         */
        internal fun forShim(
            signingKey: String,
            expectedHost: String,
            freshnessSeconds: Long,
            clock: Clock = Clock.System,
        ): CallbackSignatureVerifier = CallbackSignatureVerifier(
            signingKey = signingKey,
            registeredCallbackUrl = null,
            freshnessSeconds = freshnessSeconds,
            clock = clock,
            hostOverride = expectedHost,
            oneSidedFreshness = true,
        )
    }
}
