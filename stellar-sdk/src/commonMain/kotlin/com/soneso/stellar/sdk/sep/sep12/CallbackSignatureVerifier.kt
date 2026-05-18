// Copyright 2025 Soneso. All rights reserved.
// Use of this source code is governed by a license that can be
// found in the LICENSE file.

package com.soneso.stellar.sdk.sep.sep12

import com.soneso.stellar.sdk.sep.common.CallbackSignatureVerifier as SharedVerifier
import kotlin.time.ExperimentalTime

/**
 * Deprecated callback-signature helper. Kept as a thin shim over
 * [com.soneso.stellar.sdk.sep.common.CallbackSignatureVerifier] so existing
 * call sites continue to compile and produce bit-for-bit identical results.
 *
 * Migrate to [com.soneso.stellar.sdk.sep.common.CallbackSignatureVerifier].
 * The new class exposes a sealed [SharedVerifier.Result] so callers can
 * distinguish replay (`Stale`) from forgery (`SignatureMismatch`) in logs,
 * pins host from a registered URL with port stripped, enforces HTTPS (with
 * a loopback carve-out for local development), and applies a two-sided
 * freshness check that additionally rejects future-dated forgery attempts.
 *
 * ### Shim semantics
 *
 * Internally this object routes through [SharedVerifier.forShim] so every
 * v0.6.0 observable behaviour is preserved:
 *
 * - [verify] passes [expectedHost] verbatim into the canonical payload —
 *   no URL parsing, no port stripping, no HTTPS scheme check. Hosts like
 *   `"localhost:8080"`, `"myapp.com:8443"`, or the empty string all flow
 *   through unchanged.
 * - Freshness is one-sided: future-dated timestamps are accepted as long
 *   as `currentTime - signedTimestamp <= maxAgeSeconds`.
 * - [parseSignatureHeader] keeps its comma-split parser and
 *   `IllegalArgumentException`-on-malformed contract verbatim. It is NOT
 *   routed through the new class's tightened regex, so a header that the
 *   new class would reject as [SharedVerifier.Result.MalformedHeader] may
 *   still parse successfully through [parseSignatureHeader]. This is
 *   acceptable deprecation cost.
 * - [verify] collapses all non-`Valid` outcomes (`Stale`, `MalformedHeader`,
 *   `MissingHeader`, `SignatureMismatch`) to `false`, matching the original
 *   `Boolean` return type. Consumers who want fine-grained diagnostics
 *   migrate to the new class.
 *
 * Removal schedule is in the project `CHANGELOG.md` under the entry that
 * introduced this deprecation.
 *
 * @see com.soneso.stellar.sdk.sep.common.CallbackSignatureVerifier
 * @see PutCustomerCallbackRequest
 */
@Deprecated(
    message = "Moved to com.soneso.stellar.sdk.sep.common.CallbackSignatureVerifier. " +
        "This shim is functionally equivalent and is scheduled for removal " +
        "per the CHANGELOG.",
    replaceWith = ReplaceWith(
        "SharedVerifier(signingKey = anchorSigningKey, " +
            "registeredCallbackUrl = \"https://\$expectedHost/your-callback-path\", " +
            "freshnessSeconds = maxAgeSeconds)",
        "com.soneso.stellar.sdk.sep.common.CallbackSignatureVerifier as SharedVerifier",
    ),
    level = DeprecationLevel.WARNING,
)
@OptIn(ExperimentalTime::class)
object CallbackSignatureVerifier {

    /**
     * Verifies a callback signature from a SEP-12 anchor.
     *
     * This shim delegates to [com.soneso.stellar.sdk.sep.common.CallbackSignatureVerifier]
     * via [SharedVerifier.forShim] so that [expectedHost] is honoured verbatim and the
     * freshness check stays one-sided, matching v0.6.0 observable behaviour.
     *
     * Validates that:
     * 1. Signature header is properly formatted
     * 2. Timestamp is not older than [maxAgeSeconds] (one-sided check; future-dated
     *    timestamps are accepted in this shim)
     * 3. Signature is valid for the payload `<timestamp>.<expectedHost>.<requestBody>`
     *
     * @param signatureHeader The Signature or X-Stellar-Signature header value
     *   (format: `"t=<timestamp>, s=<signature>"`).
     * @param requestBody The raw request body (JSON string).
     * @param expectedHost The expected host string from the callback URL. Used verbatim;
     *   no URL parsing or port stripping is performed.
     * @param anchorSigningKey The anchor's `SIGNING_KEY` from stellar.toml (G... address).
     * @param maxAgeSeconds Maximum age of signature in seconds (default: 300 = 5 minutes).
     * @return `true` if signature is valid and not expired, `false` otherwise.
     *
     * Example:
     * ```kotlin
     * val isValid = CallbackSignatureVerifier.verify(
     *     signatureHeader = "t=1234567890, s=SGVsbG8gV29ybGQh",
     *     requestBody = """{"id":"123","status":"ACCEPTED"}""",
     *     expectedHost = "myapp.com",
     *     anchorSigningKey = "GBWMCCC3NHSKLAOJDBKKYW7SSH2PFTTNVFKWSGLWGDLEBKLOVP5JLBBP"
     * )
     * ```
     */
    @Suppress("LongParameterList")
    suspend fun verify(
        signatureHeader: String,
        requestBody: String,
        expectedHost: String,
        anchorSigningKey: String,
        maxAgeSeconds: Long = 300,
    ): Boolean {
        return try {
            // Use the `internal` shim factory so `expectedHost` is honoured verbatim
            // and freshness stays one-sided — exactly matching v0.6.0 observable behaviour.
            val verifier = SharedVerifier.forShim(
                signingKey = anchorSigningKey,
                expectedHost = expectedHost,
                freshnessSeconds = maxAgeSeconds,
            )
            verifier.verify(
                signatureHeader = signatureHeader,
                xStellarSignatureHeader = null,
                body = requestBody,
            ) is SharedVerifier.Result.Valid
        } catch (_: Throwable) {
            // v0.6.0 wrapped its entire `verify` body in `try { ... } catch (e: Exception) { false }`,
            // so a malformed `anchorSigningKey` (KeyPair.fromAccountId throws), a malformed
            // `signatureHeader` that escapes `parseSignatureHeader`, etc., all collapse to `false`.
            // The new shared class's `init {}` throws on malformed `signingKey`, which would otherwise
            // escape through the shim. Wrap to preserve bit-for-bit behaviour.
            false
        }
    }

    /**
     * Parses the signature header into timestamp and signature components.
     *
     * Header format: `"t=<timestamp>, s=<signature>"`.
     *
     * The parser is intentionally permissive (splits on commas and looks up `t` and `s`
     * keys) and is preserved verbatim from the v0.6.0 implementation. It is NOT routed
     * through the new class's tightened regex, so headers that the new class would reject
     * as `MalformedHeader` (e.g. trailing `; v=1`) may still parse successfully here.
     * Acceptable deprecation cost.
     *
     * @param header The Signature or X-Stellar-Signature header value.
     * @return Pair of `(timestamp, base64Signature)`.
     * @throws IllegalArgumentException if the header format is invalid.
     *
     * Example:
     * ```kotlin
     * val (timestamp, signature) = CallbackSignatureVerifier.parseSignatureHeader(
     *     "t=1234567890, s=SGVsbG8gV29ybGQh"
     * )
     * // timestamp = 1234567890
     * // signature = "SGVsbG8gV29ybGQh"
     * ```
     */
    @Deprecated(
        message = "Use com.soneso.stellar.sdk.sep.common.CallbackSignatureVerifier for callback " +
            "verification. Header parsing is handled internally by the new class; if you need " +
            "to inspect the timestamp separately, parse it from the header value yourself.",
        level = DeprecationLevel.WARNING,
    )
    fun parseSignatureHeader(header: String): Pair<Long, String> {
        // Original v0.6.0 behaviour: split on commas, lookup t / s, throw IAE on malformed.
        // Kept verbatim from the v0.6.0 implementation.
        val parts = header.split(",").associate { part ->
            val trimmed = part.trim()
            val (key, value) = trimmed.split("=", limit = 2)
            key to value
        }

        val timestamp = parts["t"]?.toLongOrNull()
            ?: throw IllegalArgumentException("Invalid or missing timestamp in signature header: $header")

        val signature = parts["s"]
            ?: throw IllegalArgumentException("Missing signature in header: $header")

        return Pair(timestamp, signature)
    }
}
