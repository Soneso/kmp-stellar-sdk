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
     * HTTP header identifying the SDK name sent with indexer and relayer requests.
     */
    const val CLIENT_NAME_HEADER = "X-Client-Name"

    /**
     * HTTP header identifying the SDK version sent with indexer and relayer requests.
     */
    const val CLIENT_VERSION_HEADER = "X-Client-Version"

    /**
     * SDK name sent in client identification headers.
     */
    const val CLIENT_NAME = "kmp-stellar-sdk"

    /**
     * Sentinel credential ID written into the connected state by a headless connect
     * ([OZWalletOperations.connectToContract]). Empty string is the natural "no credential"
     * marker: it is non-null, so it satisfies the non-null credential invariant of
     * [OZSmartAccountKit.setConnectedState] and [OZSmartAccountKit.requireConnected], while a
     * real WebAuthn credential ID is never empty. The single-passkey submit path compares the
     * connected credential against this value to fail loudly when invoked on a headless kit.
     */
    internal const val HEADLESS_CREDENTIAL_ID: String = ""
}
