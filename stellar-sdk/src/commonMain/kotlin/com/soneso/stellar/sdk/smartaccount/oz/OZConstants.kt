//
//  OZConstants.kt
//  Stellar SDK Kotlin Multiplatform
//
//  Copyright (c) 2026 Soneso. All rights reserved.
//

package com.soneso.stellar.sdk.smartaccount.oz

/**
 * Configuration defaults and contract limits for OpenZeppelin smart account operations.
 */
object OZConstants {

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
     * XLM amount reserved by Friendbot when funding test accounts.
     */
    const val FRIENDBOT_RESERVE_XLM = 5

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
     * Maximum context rules per smart account (OZ contract limit).
     */
    const val MAX_CONTEXT_RULES = 15

}
