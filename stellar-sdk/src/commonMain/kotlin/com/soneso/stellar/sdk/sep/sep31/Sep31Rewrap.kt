// Copyright 2026 Soneso. All rights reserved.
// Use of this source code is governed by a license that can be
// found in the LICENSE file.

package com.soneso.stellar.sdk.sep.sep31

import com.soneso.stellar.sdk.sep.common.sanitizeAnchorString
import com.soneso.stellar.sdk.sep.sep31.exceptions.Sep31InvalidResponseException

/**
 * Runs [block] and rewraps any [IllegalArgumentException] (including
 * `kotlinx.serialization.SerializationException`, which extends it) into
 * [Sep31InvalidResponseException] with a message of the form `"<label>: <sanitized cause>"`.
 *
 * [Sep31InvalidResponseException] thrown by nested factories is rethrown unchanged so
 * inner parse-failure details propagate to the caller without losing the original
 * context. Callers MUST pass a label identical to the `"Malformed ..."` prefix used
 * by the data-class `fromJson` factories — the exact wording is locked in by tests
 * and is part of the SDK's stable error surface.
 */
internal inline fun <T> sep31Rewrap(label: String, block: () -> T): T {
    return try {
        block()
    } catch (e: Sep31InvalidResponseException) {
        throw e
    } catch (e: IllegalArgumentException) {
        throw Sep31InvalidResponseException("$label: ${sanitizeAnchorString(e.message)}")
    }
}
