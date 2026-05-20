// Copyright 2025 Soneso. All rights reserved.
// Use of this source code is governed by a license that can be
// found in the LICENSE file.

package com.soneso.stellar.sdk.sep.sep01

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*

/**
 * Parses and provides access to stellar.toml files as defined in SEP-0001.
 *
 * The stellar.toml file is a standardized configuration file that organizations
 * publish at `https://DOMAIN/.well-known/stellar.toml` to provide information
 * about their Stellar integration, including service endpoints, validators,
 * currencies, and organizational details.
 *
 * This class supports parsing stellar.toml from either a raw TOML string or
 * by automatically fetching from a domain's well-known location.
 *
 * The stellar.toml file serves several critical purposes:
 * - Declares service endpoints for SEP implementations (WebAuth, Transfer, KYC, etc.)
 * - Publishes organization information and contact details for transparency
 * - Lists supported currencies/assets with their properties
 * - Declares validator nodes and their configuration
 * - Links Stellar accounts to a domain for identity verification
 *
 * Example - Fetch from domain:
 * ```kotlin
 * // Fetch and parse stellar.toml from a domain
 * val stellarToml = StellarToml.fromDomain("example.com")
 *
 * // Access general information
 * println("WebAuth endpoint: ${stellarToml.generalInformation.webAuthEndpoint}")
 * println("Transfer server: ${stellarToml.generalInformation.transferServer}")
 *
 * // Access organization documentation
 * stellarToml.documentation?.let { doc ->
 *     println("Organization: ${doc.orgName}")
 *     println("Support email: ${doc.orgSupportEmail}")
 * }
 *
 * // Iterate through supported currencies
 * stellarToml.currencies?.forEach { currency ->
 *     println("Currency: ${currency.code} issued by ${currency.issuer}")
 *     println("Description: ${currency.desc}")
 * }
 * ```
 *
 * Example - Parse from string:
 * ```kotlin
 * val tomlContent = """
 *     NETWORK_PASSPHRASE = "Public Global Stellar Network ; September 2015"
 *     WEB_AUTH_ENDPOINT = "https://example.com/auth"
 *     SIGNING_KEY = "GBWMCCC3NHSKLAOJDBKKYW7SSH2PFTTNVFKWSGLWGDLEBKLOVP5JLBBP"
 *
 *     [DOCUMENTATION]
 *     ORG_NAME = "Example Organization"
 *     ORG_URL = "https://example.com"
 * """.trimIndent()
 *
 * val stellarToml = StellarToml.parse(tomlContent)
 * println(stellarToml.generalInformation.webAuthEndpoint)
 * ```
 *
 * Example - Load currency from external TOML:
 * ```kotlin
 * // Some currencies may be defined in separate files
 * val currency = stellarToml.currencies?.firstOrNull()
 * currency?.toml?.let { url ->
 *     val fullCurrency = StellarToml.currencyFromUrl(url)
 *     println("Full currency details: ${fullCurrency.desc}")
 * }
 * ```
 *
 * Security considerations:
 * - Always verify HTTPS is used when fetching stellar.toml files
 * - Validate signing keys match expected values for critical operations
 * - Cross-reference account information with on-chain data
 * - Be aware that stellar.toml content can change; cache appropriately
 *
 * See also:
 * - [SEP-0001 Specification](https://github.com/stellar/stellar-protocol/blob/master/ecosystem/sep-0001.md)
 *
 * Supported Version: 2.7.0
 *
 * @property generalInformation General information including service endpoints and network configuration
 * @property documentation Organization documentation and contact information
 * @property pointsOfContact List of principals/points of contact for the organization
 * @property currencies List of currencies/assets supported by the organization
 * @property validators List of validator nodes operated by the organization
 */
class StellarToml(
    val generalInformation: GeneralInformation,
    val documentation: Documentation? = null,
    val pointsOfContact: List<PointOfContact>? = null,
    val currencies: List<Currency>? = null,
    val validators: List<Validator>? = null
) {
    companion object {
        /**
         * Fetches and parses stellar.toml from a domain's well-known location.
         *
         * The standard SEP-1 location is `https://DOMAIN/.well-known/stellar.toml`.
         * As a development convenience, this method also accepts the three IETF
         * loopback authorities — `localhost`, `127.0.0.1`, and `[::1]`, each
         * optionally with a `:port` — and fetches over `http://` against those
         * hosts so callers can integrate against a local Anchor Platform
         * instance without standing up a TLS-terminating proxy. Every non-loopback
         * domain is fetched over HTTPS unconditionally.
         *
         * This is the primary method for discovering a domain's Stellar integration
         * information. Organizations publish their stellar.toml file at this standardized
         * location to allow wallets, anchors, and other services to discover their
         * capabilities and configuration.
         *
         * @param domain The domain name (without protocol). E.g., "example.com",
         *   "localhost:8080", "127.0.0.1", or "[::1]:8080".
         * @param httpClient Optional custom HTTP client for testing or proxy configuration
         * @param httpRequestHeaders Optional custom HTTP headers to include in the request
         * @return StellarToml containing the parsed stellar.toml data
         * @throws IllegalStateException If the stellar.toml file is not found (non-200 status code)
         * @throws Exception If the TOML content is invalid and cannot be parsed
         *
         * Example:
         * ```kotlin
         * // Fetch stellar.toml from a domain
         * try {
         *     val stellarToml = StellarToml.fromDomain("example.com")
         *
         *     // Check if the domain supports WebAuth
         *     stellarToml.generalInformation.webAuthEndpoint?.let {
         *         println("WebAuth supported at: $it")
         *     }
         *
         *     // Check for transfer server
         *     stellarToml.generalInformation.transferServerSep24?.let {
         *         println("SEP-24 transfers available")
         *     }
         * } catch (e: Exception) {
         *     println("Failed to fetch stellar.toml: ${e.message}")
         * }
         * ```
         *
         * Example with custom headers:
         * ```kotlin
         * val stellarToml = StellarToml.fromDomain(
         *     "example.com",
         *     httpRequestHeaders = mapOf("User-Agent" to "MyWallet/1.0")
         * )
         * ```
         *
         * Local development example:
         * ```kotlin
         * // Anchor Platform running on http://localhost:8080
         * val stellarToml = StellarToml.fromDomain("localhost:8080")
         * ```
         */
        suspend fun fromDomain(
            domain: String,
            httpClient: HttpClient? = null,
            httpRequestHeaders: Map<String, String>? = null
        ): StellarToml {
            val client = httpClient ?: HttpClient()
            val scheme = if (isLoopbackDomain(domain)) "http" else "https"
            val url = "$scheme://$domain/.well-known/stellar.toml"

            try {
                val response: HttpResponse = client.get(url) {
                    httpRequestHeaders?.forEach { (key, value) ->
                        header(key, value)
                    }
                }

                if (response.status != HttpStatusCode.OK) {
                    throw IllegalStateException(
                        "Stellar toml not found, response status code ${response.status.value}"
                    )
                }

                val body = response.bodyAsText()
                return parse(body)
            } finally {
                if (httpClient == null) {
                    client.close()
                }
            }
        }

        /**
         * Returns `true` if [domain] is one of the three IETF loopback authorities
         * (`localhost`, `127.0.0.1`, `[::1]`, each optionally followed by `:port`).
         * Comparison is case-insensitive. Used by [fromDomain] to switch the fetch
         * scheme from `https` to `http` for local development.
         *
         * Delegates to [com.soneso.stellar.sdk.sep.common.isLoopbackHost] so the
         * loopback recognition rules are shared with the SEP-31 and callback-signature
         * verifier layers.
         */
        internal fun isLoopbackDomain(domain: String): Boolean =
            com.soneso.stellar.sdk.sep.common.isLoopbackHost(domain)

        /**
         * Loads detailed currency information from an external TOML file.
         *
         * Instead of embedding complete currency information directly in stellar.toml,
         * organizations can link to separate TOML files for each currency. This is useful
         * when currency information is extensive or when the same currency details need
         * to be shared across multiple services.
         *
         * When a currency entry in stellar.toml contains only a `toml` field pointing to
         * an external URL (e.g., `toml="https://DOMAIN/.well-known/USDC.toml"`), use this
         * method to fetch and parse the complete currency information.
         *
         * @param toml The full URL to the currency TOML file
         * @param httpClient Optional custom HTTP client for testing or proxy configuration
         * @param httpRequestHeaders Optional custom HTTP headers to include in the request
         * @return Currency containing the complete currency information
         * @throws IllegalStateException If the currency TOML file is not found (non-200 status code)
         * @throws Exception If the TOML content is invalid and cannot be parsed
         *
         * Example:
         * ```kotlin
         * val stellarToml = StellarToml.fromDomain("example.com")
         *
         * // Check if any currencies link to external TOML files
         * stellarToml.currencies?.forEach { currency ->
         *     currency.toml?.let { url ->
         *         // This currency's details are in an external file
         *         val fullCurrency = StellarToml.currencyFromUrl(url)
         *         println("${fullCurrency.code}: ${fullCurrency.desc}")
         *         println("Issuer: ${fullCurrency.issuer}")
         *         if (fullCurrency.isAssetAnchored == true) {
         *             println("Anchored to: ${fullCurrency.anchorAsset}")
         *         }
         *     }
         * }
         * ```
         */
        suspend fun currencyFromUrl(
            toml: String,
            httpClient: HttpClient? = null,
            httpRequestHeaders: Map<String, String>? = null
        ): Currency {
            val client = httpClient ?: HttpClient()

            try {
                val response: HttpResponse = client.get(toml) {
                    httpRequestHeaders?.forEach { (key, value) ->
                        header(key, value)
                    }
                }

                if (response.status != HttpStatusCode.OK) {
                    throw IllegalStateException(
                        "Currency toml not found, response status code ${response.status.value}"
                    )
                }

                val body = response.bodyAsText()
                val parsedToml = parseTomlTree(body)
                return parseCurrency(parsedToml)
            } finally {
                if (httpClient == null) {
                    client.close()
                }
            }
        }

        /**
         * Parses TOML content string into a StellarToml object.
         *
         * This is the main parsing method that handles TOML content from any source.
         * It automatically applies content safeguards to correct common TOML formatting
         * errors found in real-world stellar.toml files.
         *
         * @param toml Raw TOML content string to parse
         * @return StellarToml object containing all parsed sections
         * @throws Exception If the TOML content is invalid and cannot be parsed
         */
        fun parse(toml: String): StellarToml {
            val safeToml = safeguardTomlContent(toml)
            val parsedToml = parseTomlTree(safeToml)

            return StellarToml(
                generalInformation = parseGeneralInformation(parsedToml),
                documentation = parseDocumentation(parsedToml),
                pointsOfContact = parsePointsOfContact(parsedToml),
                currencies = parseCurrencies(parsedToml),
                validators = parseValidators(parsedToml)
            )
        }

        /**
         * Corrects common formatting errors in stellar.toml content.
         *
         * Some stellar.toml files in the wild contain invalid TOML syntax, particularly
         * with array table declarations. This method automatically corrects these issues
         * to allow successful parsing while logging warnings.
         *
         * Common corrections made:
         * - `[ACCOUNTS]` -> `[[ACCOUNTS]]` (should be array of tables)
         * - `[[DOCUMENTATION]]` -> `[DOCUMENTATION]` (should be single table)
         * - `[PRINCIPALS]` -> `[[PRINCIPALS]]` (should be array of tables)
         * - `[CURRENCIES]` -> `[[CURRENCIES]]` (should be array of tables)
         * - `[VALIDATORS]` -> `[[VALIDATORS]]` (should be array of tables)
         *
         * @param input The raw TOML content string
         * @return Corrected TOML content string
         */
        private fun safeguardTomlContent(input: String): String {
            val lines = input.split('\n').toMutableList()
            for (i in lines.indices) {
                val trimmedLine = lines[i].trimStart()

                if (trimmedLine.startsWith("[ACCOUNTS]")) {
                    println("Warning: Replacing [ACCOUNTS] with [[ACCOUNTS]]. The [ACCOUNTS] value is invalid.")
                    lines[i] = lines[i].replaceFirst("[ACCOUNTS]", "[[ACCOUNTS]]")
                }
                if (trimmedLine.startsWith("[[DOCUMENTATION]]")) {
                    println("Warning: Replacing [[DOCUMENTATION]] with [DOCUMENTATION]. The [[DOCUMENTATION]] value is invalid.")
                    lines[i] = lines[i].replaceFirst("[[DOCUMENTATION]]", "[DOCUMENTATION]")
                }
                if (trimmedLine.startsWith("[PRINCIPALS]")) {
                    println("Warning: Replacing [PRINCIPALS] with [[PRINCIPALS]]. The [PRINCIPALS] value is invalid.")
                    lines[i] = lines[i].replaceFirst("[PRINCIPALS]", "[[PRINCIPALS]]")
                }
                if (trimmedLine.startsWith("[CURRENCIES]")) {
                    println("Warning: Replacing [CURRENCIES] with [[CURRENCIES]]. The [CURRENCIES] value is invalid.")
                    lines[i] = lines[i].replaceFirst("[CURRENCIES]", "[[CURRENCIES]]")
                }
                if (trimmedLine.startsWith("[VALIDATORS]")) {
                    println("Warning: Replacing [VALIDATORS] with [[VALIDATORS]]. The [VALIDATORS] value is invalid.")
                    lines[i] = lines[i].replaceFirst("[VALIDATORS]", "[[VALIDATORS]]")
                }
            }
            return lines.joinToString("\n")
        }

        private fun parseTomlTree(toml: String): Map<String, Any?> {
            return TomlParser.parse(toml)
        }

        private fun parseGeneralInformation(data: Map<String, Any?>): GeneralInformation {
            return GeneralInformation(
                version = data["VERSION"] as? String,
                networkPassphrase = data["NETWORK_PASSPHRASE"] as? String,
                federationServer = data["FEDERATION_SERVER"] as? String,
                authServer = data["AUTH_SERVER"] as? String,
                transferServer = data["TRANSFER_SERVER"] as? String,
                transferServerSep24 = data["TRANSFER_SERVER_SEP0024"] as? String,
                kycServer = data["KYC_SERVER"] as? String,
                webAuthEndpoint = data["WEB_AUTH_ENDPOINT"] as? String,
                signingKey = data["SIGNING_KEY"] as? String,
                horizonUrl = data["HORIZON_URL"] as? String,
                accounts = (data["ACCOUNTS"] as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
                uriRequestSigningKey = data["URI_REQUEST_SIGNING_KEY"] as? String,
                directPaymentServer = data["DIRECT_PAYMENT_SERVER"] as? String,
                anchorQuoteServer = data["ANCHOR_QUOTE_SERVER"] as? String,
                webAuthForContractsEndpoint = data["WEB_AUTH_FOR_CONTRACTS_ENDPOINT"] as? String,
                webAuthContractId = data["WEB_AUTH_CONTRACT_ID"] as? String
            )
        }

        private fun parseDocumentation(data: Map<String, Any?>): Documentation? {
            val docTable = data["DOCUMENTATION"] as? Map<*, *> ?: return null

            return Documentation(
                orgName = docTable["ORG_NAME"] as? String,
                orgDba = docTable["ORG_DBA"] as? String,
                orgUrl = docTable["ORG_URL"] as? String,
                orgLogo = docTable["ORG_LOGO"] as? String,
                orgDescription = docTable["ORG_DESCRIPTION"] as? String,
                orgPhysicalAddress = docTable["ORG_PHYSICAL_ADDRESS"] as? String,
                orgPhysicalAddressAttestation = docTable["ORG_PHYSICAL_ADDRESS_ATTESTATION"] as? String,
                orgPhoneNumber = docTable["ORG_PHONE_NUMBER"] as? String,
                orgPhoneNumberAttestation = docTable["ORG_PHONE_NUMBER_ATTESTATION"] as? String,
                orgKeybase = docTable["ORG_KEYBASE"] as? String,
                orgTwitter = docTable["ORG_TWITTER"] as? String,
                orgGithub = docTable["ORG_GITHUB"] as? String,
                orgOfficialEmail = docTable["ORG_OFFICIAL_EMAIL"] as? String,
                orgSupportEmail = docTable["ORG_SUPPORT_EMAIL"] as? String,
                orgLicensingAuthority = docTable["ORG_LICENSING_AUTHORITY"] as? String,
                orgLicenseType = docTable["ORG_LICENSE_TYPE"] as? String,
                orgLicenseNumber = docTable["ORG_LICENSE_NUMBER"] as? String
            )
        }

        private fun parsePointsOfContact(data: Map<String, Any?>): List<PointOfContact>? {
            val principalsList = data["PRINCIPALS"] as? List<*> ?: return null

            return principalsList.mapNotNull { item ->
                val table = item as? Map<*, *> ?: return@mapNotNull null
                PointOfContact(
                    name = table["name"] as? String,
                    email = table["email"] as? String,
                    keybase = table["keybase"] as? String,
                    telegram = table["telegram"] as? String,
                    twitter = table["twitter"] as? String,
                    github = table["github"] as? String,
                    idPhotoHash = table["id_photo_hash"] as? String,
                    verificationPhotoHash = table["verification_photo_hash"] as? String
                )
            }
        }

        private fun parseCurrencies(data: Map<String, Any?>): List<Currency>? {
            val currenciesList = data["CURRENCIES"] as? List<*> ?: return null

            return currenciesList.mapNotNull { item ->
                val table = item as? Map<*, *> ?: return@mapNotNull null
                parseCurrencyFromMap(table)
            }
        }

        private fun parseCurrency(data: Map<String, Any?>): Currency {
            return parseCurrencyFromMap(data)
        }

        private fun parseCurrencyFromMap(table: Map<*, *>): Currency {
            return Currency(
                toml = table["toml"] as? String,
                code = table["code"] as? String,
                codeTemplate = table["code_template"] as? String,
                issuer = table["issuer"] as? String,
                contract = table["contract"] as? String,
                status = table["status"] as? String,
                displayDecimals = (table["display_decimals"] as? Number)?.toInt(),
                name = table["name"] as? String,
                desc = table["desc"] as? String,
                conditions = table["conditions"] as? String,
                image = table["image"] as? String,
                fixedNumber = (table["fixed_number"] as? Number)?.toLong(),
                maxNumber = (table["max_number"] as? Number)?.toLong(),
                isUnlimited = table["is_unlimited"] as? Boolean,
                isAssetAnchored = table["is_asset_anchored"] as? Boolean,
                anchorAssetType = table["anchor_asset_type"] as? String,
                anchorAsset = table["anchor_asset"] as? String,
                attestationOfReserve = table["attestation_of_reserve"] as? String,
                redemptionInstructions = table["redemption_instructions"] as? String,
                collateralAddresses = (table["collateral_addresses"] as? List<*>)?.filterIsInstance<String>(),
                collateralAddressMessages = (table["collateral_address_messages"] as? List<*>)?.filterIsInstance<String>(),
                collateralAddressSignatures = (table["collateral_address_signatures"] as? List<*>)?.filterIsInstance<String>(),
                regulated = table["regulated"] as? Boolean,
                approvalServer = table["approval_server"] as? String,
                approvalCriteria = table["approval_criteria"] as? String
            )
        }

        private fun parseValidators(data: Map<String, Any?>): List<Validator>? {
            val validatorsList = data["VALIDATORS"] as? List<*> ?: return null

            return validatorsList.mapNotNull { item ->
                val table = item as? Map<*, *> ?: return@mapNotNull null
                Validator(
                    alias = table["ALIAS"] as? String,
                    displayName = table["DISPLAY_NAME"] as? String,
                    publicKey = table["PUBLIC_KEY"] as? String,
                    host = table["HOST"] as? String,
                    history = table["HISTORY"] as? String
                )
            }
        }
    }
}
