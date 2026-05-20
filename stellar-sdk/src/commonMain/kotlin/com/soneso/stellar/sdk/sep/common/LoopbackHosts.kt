// Copyright 2026 Soneso. All rights reserved.
// Use of this source code is governed by a license that can be
// found in the LICENSE file.

package com.soneso.stellar.sdk.sep.common

/**
 * Returns `true` if [host] is one of the three IETF loopback authorities
 * (`localhost`, `127.0.0.1`, `[::1]`, each optionally followed by `:port`).
 *
 * Comparison is case-insensitive. The check is a literal string comparison
 * against the three exact host tokens. Authorities like `localhost.evil.com`,
 * `127.0.0.1.evil.com`, or `user@localhost` do not match — only the three
 * reserved loopback names pass, eliminating DNS-rebinding and look-alike-host
 * risks at this layer.
 *
 * Used by SEP-layer code that needs to relax HTTPS-only constraints for
 * local-development integration (e.g., running an Anchor Platform on
 * `http://localhost:8080`).
 */
internal fun isLoopbackHost(host: String): Boolean {
    val lower = host.lowercase()
    return lower == "localhost" ||
        lower.startsWith("localhost:") ||
        lower == "127.0.0.1" ||
        lower.startsWith("127.0.0.1:") ||
        lower == "[::1]" ||
        lower.startsWith("[::1]:")
}
