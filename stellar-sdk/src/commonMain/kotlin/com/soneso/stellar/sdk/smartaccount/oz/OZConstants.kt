//
//  OZConstants.kt
//  Stellar SDK Kotlin Multiplatform
//
//  Copyright (c) 2026 Soneso. All rights reserved.
//

package com.soneso.stellar.sdk.smartaccount.oz

import com.soneso.stellar.sdk.smartaccount.core.AuthDigestScheme

/**
 * Configuration defaults and contract limits for OpenZeppelin smart account operations.
 */
object OZConstants {

    /**
     * Account WASM hashes (lowercase hex) whose digest scheme needs no configuration:
     * the stellar-accounts 0.8.0 build of `multisig-account-example` (Stellar CLI 28.1.0).
     * [OZSmartAccountConfig.authDigestSchemes] entries take precedence; every other hash
     * signs with [AuthDigestScheme.LEGACY_CONCAT].
     */
    internal val KNOWN_AUTH_DIGEST_SCHEMES: Map<String, AuthDigestScheme> = mapOf(
        "c514d35ea67eaea9bfc1fb3327854c0c06111bc1ddebd4fe217c504c1004b9e1" to AuthDigestScheme.PREIMAGE_V1
    )

    /**
     * Default session expiry in milliseconds (7 days).
     */
    const val DEFAULT_SESSION_EXPIRY_MS = 604_800_000L

    /**
     * Default HTTP timeout for indexer requests in milliseconds (10 seconds).
     */
    const val DEFAULT_INDEXER_TIMEOUT_MS = 10_000L

    /**
     * Default HTTP timeout for relayer requests in milliseconds (6 minutes).
     */
    const val DEFAULT_RELAYER_TIMEOUT_MS = 360_000L

    /**
     * Timeout for WebAuthn credential operations in milliseconds (60 seconds).
     */
    const val WEBAUTHN_TIMEOUT_MS = 60_000L

    /**
     * XLM amount retained in the temporary account as minimum balance reserve
     * when transferring Friendbot funds to a smart account wallet.
     */
    const val FRIENDBOT_RESERVE_XLM = 5

    /**
     * Interval in milliseconds between polls while waiting for a ledger entry to become
     * visible to the Soroban RPC. Shared by the funding-account wait (a Friendbot-funded
     * temporary account) and the deploy wait (a freshly deployed contract instance).
     */
    const val RPC_VISIBILITY_POLL_INTERVAL_MS = 1_500L

    /**
     * Overall timeout in seconds to observe a ledger entry on the Soroban RPC before
     * failing. A transaction confirmed on Horizon may not be reflected in the RPC
     * simulation state until a later ledger close; the RPC state can lag by several
     * ledger closes when testnet propagation is slow. Shared by the funding-account wait
     * and the deploy wait.
     */
    const val RPC_VISIBILITY_TIMEOUT_SECONDS = 45

    /**
     * Default timeout for transaction submission and polling in seconds.
     */
    const val DEFAULT_TIMEOUT_SECONDS = 30

    /**
     * Maximum signers per context rule (OZ contract limit).
     */
    const val MAX_SIGNERS = 15

    /**
     * Maximum policies per context rule (OZ contract limit).
     */
    const val MAX_POLICIES = 5

    /**
     * Maximum context rule name length in UTF-8 bytes (OZ contract limit).
     */
    const val MAX_NAME_SIZE = 20

    /**
     * Maximum external signer key data length in bytes (OZ contract limit).
     */
    const val MAX_EXTERNAL_KEY_SIZE = 256

    /**
     * HTTP header identifying the SDK name sent with relayer requests. Not sent to the
     * indexer: custom headers force a CORS preflight in browsers, and indexer providers
     * only allowlist standard headers.
     */
    const val CLIENT_NAME_HEADER = "X-Client-Name"

    /**
     * HTTP header identifying the SDK version sent with relayer requests.
     */
    const val CLIENT_VERSION_HEADER = "X-Client-Version"

    /**
     * SDK name sent in client identification headers.
     */
    const val CLIENT_NAME = com.soneso.stellar.sdk.Util.CLIENT_NAME
}
