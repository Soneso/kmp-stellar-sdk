// Copyright 2025 Soneso. All rights reserved.
// Use of this source code is governed by a license that can be
// found in the LICENSE file.

package com.soneso.stellar.sdk.sep.sep10.exceptions

/**
 * Exception thrown when the challenge transaction's source account is not the server account.
 *
 * SEP-10 Security Requirement: the challenge transaction returned by the web auth endpoint
 * MUST have its source account set to the Server Account, that is the SIGNING_KEY published
 * in the server's stellar.toml.
 *
 * The source account is distinct from the operation source accounts: the first operation is
 * sourced by the client account, while the transaction itself is sourced by the server. A
 * challenge whose transaction source is some other account is not a challenge this server
 * issued for this flow, so it is rejected before any signature work.
 *
 * Muxed (M...) source accounts are rejected by the same comparison, since the Server Account
 * is an ed25519 account id.
 *
 * @param expected The expected server account (the stellar.toml SIGNING_KEY)
 * @param actual The actual source account found on the challenge transaction
 */
class InvalidTransactionSourceAccountException(expected: String, actual: String?) :
    ChallengeValidationException(
        "Challenge transaction's source account must be the server account. " +
                "Expected: $expected, but found: $actual"
    )
