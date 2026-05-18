// Copyright 2026 Soneso. All rights reserved.
// Use of this source code is governed by a license that can be
// found in the LICENSE file.

/**
 * Internal JSON helpers shared between [com.soneso.stellar.sdk.sep.sep31.Sep31Service]
 * and the SEP-31 data-class `fromJson` factories.
 *
 * These helpers convert between heterogeneous Kotlin maps/lists/primitives and
 * `kotlinx.serialization` [kotlinx.serialization.json.JsonElement] trees, which lets
 * the SEP-31 layer accept and emit arbitrary `Map<String, Any?>` payloads without
 * declaring an exhaustive serializer for every shape.
 *
 * Out of scope for this file: the visually identical helpers duplicated in
 * `Sep30Service.kt` lines 371-396 are intentionally NOT removed here. The SEP-30
 * dedup is tracked as deferred decision #3 in the SEP-31 implementation plan §8
 * and will be handled by a follow-up PR. Future maintainers MUST NOT widen the
 * visibility of these functions beyond `internal` without first coordinating with
 * that follow-up — a public API surface would lock in a contract that the SEP-30
 * consolidation is expected to refine.
 */

package com.soneso.stellar.sdk.sep.common

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull

/**
 * Encodes a heterogeneous map as a JSON string for use as an HTTP request body.
 *
 * Delegates structural conversion to [mapToJsonObject] and then encodes the
 * resulting [JsonObject] via the supplied [json] instance. This avoids relying on
 * Ktor's ContentNegotiation `guessSerializer`, which cannot serialize
 * heterogeneous `Map<String, Any?>` payloads.
 *
 * Example:
 * ```kotlin
 * val body: String = mapToJsonString(
 *     mapOf(
 *         "amount" to "100.50",
 *         "asset_code" to "USDC",
 *         "fields" to mapOf("transaction" to mapOf("sender_first_name" to "Alice"))
 *     ),
 *     Json { encodeDefaults = false }
 * )
 * ```
 *
 * @param map the heterogeneous payload to encode. Values may be `null`,
 *   [String], [Number], [Boolean], [Map] of `String -> Any?`, or [List] of `Any?`.
 *   Other types cause [anyToJsonElement] to throw [IllegalArgumentException].
 * @param json the kotlinx.serialization [Json] instance used to encode the
 *   resulting tree. Callers typically pass the project-wide configured instance.
 * @return a JSON string representation of [map].
 * @throws IllegalArgumentException if any value (at any depth) is not one of the
 *   supported types listed above.
 */
internal fun mapToJsonString(map: Map<String, Any?>, json: Json): String {
    val jsonObject = mapToJsonObject(map)
    return json.encodeToString(JsonElement.serializer(), jsonObject)
}

/**
 * Converts an arbitrary Kotlin value into a [JsonElement].
 *
 * Recursive for [Map] and [List] inputs. Unlike the legacy `Sep30Service`
 * helper, this function deliberately does **not** fall back to
 * `JsonPrimitive(value.toString())` for unknown types — silent stringification
 * masks bugs in caller code (for example, accidentally passing an instant or
 * URL). Callers must convert non-primitive types to a supported representation
 * before invocation.
 *
 * Conversion rules:
 * - `null` -> [JsonNull]
 * - [String] -> [JsonPrimitive] (string)
 * - [Number] -> [JsonPrimitive] (number; supports `Int`, `Long`, `Double`, etc.)
 * - [Boolean] -> [JsonPrimitive] (boolean)
 * - [Map] of any key type -> [JsonObject] with `key.toString()` keys
 * - [List] of any element type -> [JsonArray]
 * - anything else -> [IllegalArgumentException]
 *
 * @param value the Kotlin value to convert. May be `null`.
 * @return the [JsonElement] representation of [value].
 * @throws IllegalArgumentException if [value] is not `null` and is none of
 *   [String], [Number], [Boolean], [Map], or [List].
 */
internal fun anyToJsonElement(value: Any?): JsonElement = when (value) {
    null -> JsonNull
    is String -> JsonPrimitive(value)
    is Number -> JsonPrimitive(value)
    is Boolean -> JsonPrimitive(value)
    is Map<*, *> -> JsonObject(
        value.entries.associate { (k, v) -> k.toString() to anyToJsonElement(v) },
    )
    is List<*> -> JsonArray(value.map { anyToJsonElement(it) })
    else -> throw IllegalArgumentException(
        "Cannot convert value of type ${value::class.simpleName} to JsonElement",
    )
}

/**
 * Converts a `Map<String, Any?>` directly into a [JsonObject].
 *
 * Each value is mapped through [anyToJsonElement]. Use this when callers need
 * the [JsonObject] itself (for example, to embed in another JSON tree); use
 * [mapToJsonString] when the goal is an encoded HTTP body string.
 *
 * @param map the heterogeneous map to convert. Values must satisfy the same
 *   constraints as [anyToJsonElement].
 * @return a [JsonObject] containing one entry per [map] entry.
 * @throws IllegalArgumentException if any value (at any depth) is unsupported by
 *   [anyToJsonElement].
 */
internal fun mapToJsonObject(map: Map<String, Any?>): JsonObject {
    return JsonObject(map.entries.associate { (k, v) -> k to anyToJsonElement(v) })
}

/**
 * Recursively converts a [JsonElement] tree into primitive Kotlin types.
 *
 * Critical contract: leaves of the returned tree are **always** primitive
 * Kotlin types ([String], [Boolean], [Long], [Double], or `null`) and are
 * **never** [JsonElement] instances. Downstream callers in the SEP-31 layer —
 * notably `Sep31TransactionResponse.requiredInfoUpdates` and
 * `Sep31TransactionInfoNeededException.fields` — perform `as String` and
 * `as Map<String, Any?>` casts on the result, so any [JsonElement] leftover
 * would surface as a `ClassCastException` at runtime.
 *
 * Conversion rules:
 * - [JsonNull] -> `null`
 * - [JsonPrimitive] with `isString == true` -> [String] content (preserved as
 *   `String` even when the content looks numeric; for example, the JSON value
 *   `"100"` decodes to the [String] `"100"`, not to a number)
 * - [JsonPrimitive] with `isString == false`:
 *   1. [Boolean] if [JsonPrimitive.booleanOrNull] is non-null
 *   2. otherwise [Long] if [JsonPrimitive.longOrNull] is non-null
 *   3. otherwise [Double] if [JsonPrimitive.doubleOrNull] is non-null
 *   4. otherwise the raw `content: String` (defensive fallback)
 * - [JsonObject] -> `Map<String, Any?>` with recursive values
 * - [JsonArray] -> `List<Any?>` with recursive elements
 *
 * @param element the JSON tree to walk. Must not be a custom [JsonElement]
 *   subtype outside the four built-in variants.
 * @return the equivalent Kotlin value, with primitive leaves only.
 */
internal fun jsonElementToAny(element: JsonElement): Any? = when (element) {
    is JsonNull -> null
    is JsonPrimitive -> if (element.isString) {
        element.content
    } else {
        element.booleanOrNull
            ?: element.longOrNull
            ?: element.doubleOrNull
            ?: element.content
    }
    is JsonObject -> element.mapValues { (_, value) -> jsonElementToAny(value) }
    is JsonArray -> element.map { jsonElementToAny(it) }
}

/**
 * Regex matching the canonical three-segment JSON Web Token (JWT) shape:
 * `header.payload.signature`, where each segment is one or more base64url
 * characters (`A-Z`, `a-z`, `0-9`, `_`, `-`). The header always begins with
 * `eyJ` because every JWT header decodes to JSON starting with `{`.
 *
 * The regex is conservative on purpose: requiring all three dot-separated
 * base64url segments to be non-empty avoids matching arbitrary base64-looking
 * substrings while still catching anchor responses that embed a real bearer
 * token in their error body.
 */
private val JWT_PATTERN = Regex("eyJ[A-Za-z0-9_\\-]+\\.[A-Za-z0-9_\\-]+\\.[A-Za-z0-9_\\-]+")

/**
 * Truncates anchor-supplied or untrusted strings to 1024 characters, replaces
 * control characters (U+0000..U+001F, except tab) with `?`, and optionally
 * redacts any substring that matches the canonical JWT shape
 * (`eyJ<base64url>.<base64url>.<base64url>`) by replacing it with the literal
 * token `<redacted-jwt>`.
 *
 * Used across SEP-31 to defend against log-injection via anchor-controlled bytes
 * flowing into exception messages and into the public `responseBody` field of
 * [com.soneso.stellar.sdk.sep.sep31.exceptions.Sep31UnknownResponseException].
 *
 * Sanitization steps, applied in order:
 * 1. Empty / null input short-circuits to `""`.
 * 2. The input is truncated to at most 1024 characters.
 * 3. Control characters in the range U+0000..U+001F (except `'\t'`, U+0009) are
 *    replaced with `'?'`.
 * 4. When [redactJwts] is `true` (the default), any match of [JWT_PATTERN] is
 *    replaced with the literal `<redacted-jwt>`. Pass `false` only when
 *    producing a body for the `rawResponseBody` field on SEP-31 exceptions —
 *    that field is documented as debug-only and may contain bearer tokens.
 *
 * Applied at every call site that interpolates an anchor-supplied string (or a
 * `kotlinx.serialization` exception message derived from anchor bytes) into a
 * thrown exception message. Centralizing the policy here prevents the
 * sanitization gap that occurs when individual data-class `fromJson` factories
 * forget to apply it.
 *
 * @param raw the untrusted input. May be `null`; a `null` input returns `""`.
 * @param redactJwts when `true` (default), JWT-shaped substrings are replaced
 *   with `<redacted-jwt>`. When `false`, JWTs are preserved verbatim — use
 *   only for the `rawResponseBody` debug-only exception field.
 * @return the sanitized string. Length at most 1024 characters after the
 *   control-character scrub; if [redactJwts] is `true`, JWT-matching
 *   substrings are subsequently replaced with `<redacted-jwt>` which may
 *   marginally shorten or lengthen the result. The only control character
 *   that survives is `'\t'` (U+0009).
 */
internal fun sanitizeAnchorString(raw: String?, redactJwts: Boolean = true): String {
    if (raw.isNullOrEmpty()) return ""
    val maxChars = 1024
    val truncated = if (raw.length > maxChars) raw.substring(0, maxChars) else raw
    val sb = StringBuilder(truncated.length)
    for (ch in truncated) {
        val needsScrub = ch.code in 0x00..0x1F && ch.code != 0x09
        sb.append(if (needsScrub) '?' else ch)
    }
    val scrubbed = sb.toString()
    return if (redactJwts) JWT_PATTERN.replace(scrubbed, "<redacted-jwt>") else scrubbed
}
