// Copyright 2026 Soneso. All rights reserved.
// Use of this source code is governed by a license that can be
// found in the LICENSE file.

package com.soneso.stellar.sdk.sep.sep02.exceptions

/**
 * Exception thrown when the federation server URL cannot be found in stellar.toml.
 *
 * Indicates that the stellar.toml file at the domain does not contain the required
 * FEDERATION_SERVER field. This typically occurs when:
 * - The stellar.toml file does not contain a FEDERATION_SERVER entry
 * - The stellar.toml file cannot be resolved from the domain
 * - The domain's web server is not properly configured to serve stellar.toml
 * - The federation service is not configured for this domain
 *
 * The FEDERATION_SERVER field in stellar.toml should contain the base URL of the
 * federation server endpoint that handles federation protocol requests.
 *
 * Recovery actions:
 * - Verify the stellar.toml file exists at https://domain/.well-known/stellar.toml
 * - Check that the FEDERATION_SERVER field is present and contains a valid URL
 * - Ensure the domain's web server is accessible and serving stellar.toml correctly
 * - Contact the domain administrator if the problem persists
 *
 * Example - Handle missing federation server:
 * ```kotlin
 * try {
 *     val service = FederationService.fromDomain("example.com")
 * } catch (e: Sep02FederationNotFoundException) {
 *     println("Federation server not found: ${e.message}")
 *     // Fall back to direct account ID lookup
 * }
 * ```
 *
 * See also:
 * - [Sep02Exception] base class
 * - [SEP-0002 Specification](https://github.com/stellar/stellar-protocol/blob/master/ecosystem/sep-0002.md)
 *
 * @param message Detailed error message describing the federation server lookup failure
 */
class Sep02FederationNotFoundException(
    message: String
) : Sep02Exception(message) {
    override fun toString(): String {
        return "SEP-02 federation server not found: $message"
    }
}
