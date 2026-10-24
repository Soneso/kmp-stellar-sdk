// Copyright 2026 Soneso. All rights reserved.
// Use of this source code is governed by a license that can be
// found in the LICENSE file.

package com.soneso.stellar.sdk.sep.common

import com.soneso.stellar.sdk.HttpTimeouts
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpTimeout

// Shared by stellar.toml discovery and federation queries.
internal fun createSepDiscoveryClient(
    engine: HttpClientEngine? = null,
    configure: HttpClientConfig<*>.() -> Unit = {}
): HttpClient {
    val config: HttpClientConfig<*>.() -> Unit = {
        install(HttpTimeout) {
            connectTimeoutMillis = HttpTimeouts.CONNECT_TIMEOUT_MS
            requestTimeoutMillis = HttpTimeouts.REQUEST_TIMEOUT_MS
        }
        configure()
    }
    return if (engine == null) HttpClient(config) else HttpClient(engine, config)
}
