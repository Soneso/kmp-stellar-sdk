//
//  SmartAccountVersion.kt
//  Stellar SDK Kotlin Multiplatform
//
//  Copyright (c) 2026 Soneso. All rights reserved.
//

package com.soneso.stellar.sdk.smartaccount.core

/**
 * Version information for the KMP Smart Account Kit.
 *
 * Used to identify the SDK in HTTP request headers when communicating
 * with relayer and indexer services. The relayer receives these values
 * as `X-Client-Name` and `X-Client-Version` headers.
 */
object SmartAccountVersion {
    /** SDK name sent in the X-Client-Name header. */
    const val NAME = "kmp-smart-account-kit"

    /** SDK version sent in the X-Client-Version header. */
    const val VERSION = "1.0.0"
}
