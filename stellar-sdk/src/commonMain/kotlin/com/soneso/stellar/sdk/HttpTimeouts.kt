package com.soneso.stellar.sdk

/** Default timeouts of the HTTP clients the SDK creates. */
internal object HttpTimeouts {
    /** Default connection timeout for SDK HTTP clients in milliseconds. */
    const val CONNECT_TIMEOUT_MS = 10_000L
    /** Default request timeout for SDK HTTP clients in milliseconds. */
    const val REQUEST_TIMEOUT_MS = 30_000L
    /** Default socket timeout for SDK HTTP clients in milliseconds. */
    const val SOCKET_TIMEOUT_MS = 30_000L
    /** Connection timeout for server-sent event streams in milliseconds. */
    const val SSE_CONNECT_TIMEOUT_MS = 30_000L
    /** Maximum connection timeout for relayer requests in milliseconds. */
    const val RELAYER_CONNECT_TIMEOUT_MS = 30_000L
}
