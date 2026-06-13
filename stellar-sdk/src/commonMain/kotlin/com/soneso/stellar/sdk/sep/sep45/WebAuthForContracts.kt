// Copyright 2025 Soneso. All rights reserved.
// Use of this source code is governed by a license that can be
// found in the LICENSE file.

package com.soneso.stellar.sdk.sep.sep45

import com.soneso.stellar.sdk.Auth
import com.soneso.stellar.sdk.KeyPair
import com.soneso.stellar.sdk.Network
import com.soneso.stellar.sdk.StrKey
import com.soneso.stellar.sdk.Util
import com.soneso.stellar.sdk.addressCredentials
import com.soneso.stellar.sdk.withUpdatedAddressCredentials
import com.soneso.stellar.sdk.rpc.SorobanServer
import com.soneso.stellar.sdk.sep.sep01.StellarToml
import com.soneso.stellar.sdk.sep.sep45.exceptions.*
import com.soneso.stellar.sdk.xdr.*
import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * SEP-45 Web Authentication client for contract accounts.
 *
 * Provides secure authentication for Stellar smart contract accounts (C... addresses)
 * using a challenge-response protocol with Soroban authorization entry signing. This
 * implements the client side of the SEP-45 specification for authenticating contract
 * accounts with Stellar services.
 *
 * SEP-45 complements SEP-10, which handles traditional Stellar accounts (G... and M... addresses).
 * Use this class when authenticating contract accounts; use SEP-10 WebAuth for regular accounts.
 *
 * ## SEP-45 Authentication Flow
 *
 * 1. Client requests authorization entries from the authentication server
 * 2. Server generates entries with specific security requirements and signs them
 * 3. Client validates the authorization entries (13 security checks)
 * 4. Client signs the validated entries with their keypair(s)
 * 5. Client submits the signed entries back to the server
 * 6. Server verifies the signatures via contract simulation and returns a JWT token
 * 7. Client uses the JWT token to authenticate subsequent API requests
 *
 * The JWT token can be used to authenticate requests to SEP-6 (Deposit/Withdrawal),
 * SEP-12 (KYC), SEP-24 (Hosted Deposit/Withdrawal), SEP-31 (Cross-Border Payments),
 * and other Stellar services that require authentication.
 *
 * ## Usage Patterns
 *
 * **High-Level API (Recommended for most use cases):**
 * ```kotlin
 * // Initialize from domain's stellar.toml
 * val webAuth = WebAuthForContracts.fromDomain("example.com", Network.PUBLIC)
 *
 * // One-line authentication
 * val authToken = webAuth.jwtToken(
 *     clientAccountId = contractId,
 *     signers = listOf(signerKeyPair)
 * )
 *
 * // Use token in API requests
 * httpClient.get("https://example.com/api/account") {
 *     header("Authorization", "Bearer ${authToken.token}")
 * }
 * ```
 *
 * **Contracts Without Signature Requirements:**
 * ```kotlin
 * // Some contracts implement __check_auth without requiring signatures
 * val authToken = webAuth.jwtToken(
 *     clientAccountId = contractId,
 *     signers = emptyList()  // Valid for such contracts
 * )
 * ```
 *
 * **Client Domain Verification (Local Signing):**
 * ```kotlin
 * val authToken = webAuth.jwtToken(
 *     clientAccountId = contractId,
 *     signers = listOf(signerKeyPair),
 *     clientDomain = "wallet.mycompany.com",
 *     clientDomainAccountKeyPair = clientDomainSigningKey
 * )
 * ```
 *
 * **Client Domain Verification (Remote Signing):**
 * ```kotlin
 * val signingDelegate = Sep45ClientDomainSigningDelegate { entryXdr ->
 *     // Send to wallet backend for domain signing
 *     val response = httpClient.post("https://backend.wallet.com/sign") {
 *         setBody(SignRequest(entryXdr))
 *     }
 *     response.body<SignResponse>().signedEntry
 * }
 *
 * val authToken = webAuth.jwtToken(
 *     clientAccountId = contractId,
 *     signers = listOf(signerKeyPair),
 *     clientDomain = "wallet.mycompany.com",
 *     clientDomainSigningDelegate = signingDelegate
 * )
 * ```
 *
 * ## Security Considerations
 *
 * - Always validate the challenge before signing (done automatically in jwtToken())
 * - Verify HTTPS is used for all communication with the authentication endpoint
 * - Verify the server's signing key matches the stellar.toml SIGNING_KEY
 * - Never skip validation checks (all checks are required)
 * - Store JWT tokens securely (encrypted storage, not in logs)
 * - Check token expiration before use
 * - Use [fromDomain] to automatically discover and verify server configuration
 *
 * See also:
 * - [SEP-45 Specification](https://github.com/stellar/stellar-protocol/blob/master/ecosystem/sep-0045.md)
 * - [fromDomain] for automatic configuration from stellar.toml
 * - [jwtToken] for the complete high-level authentication flow
 * - [validateChallenge] for the security validation checks
 *
 * @property authEndpoint The authentication endpoint URL (from stellar.toml WEB_AUTH_FOR_CONTRACTS_ENDPOINT)
 * @property webAuthContractId The web auth contract ID (from stellar.toml WEB_AUTH_CONTRACT_ID, C... address)
 * @property serverSigningKey The server's public signing key (from stellar.toml SIGNING_KEY, G... address)
 * @property serverHomeDomain The home domain of the server (used in challenge validation)
 * @property network The Stellar network (Network.PUBLIC or Network.TESTNET)
 * @property sorobanRpcUrl Optional Soroban RPC URL for ledger queries (defaults based on network)
 */
class WebAuthForContracts(
    val authEndpoint: String,
    val webAuthContractId: String,
    val serverSigningKey: String,
    val serverHomeDomain: String,
    val network: Network,
    private val httpClient: HttpClient? = null,
    private val httpRequestHeaders: Map<String, String>? = null,
    val sorobanRpcUrl: String? = null
) {
    /**
     * Whether to use application/x-www-form-urlencoded content type for token submission.
     * If false, uses application/json.
     */
    var useFormUrlEncoded: Boolean = true

    /**
     * JSON configuration for parsing server responses.
     */
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    /**
     * Lazy-initialized HTTP client for WebAuth requests.
     */
    private val client: HttpClient by lazy {
        httpClient ?: HttpClient {
            install(ContentNegotiation) {
                json(this@WebAuthForContracts.json)
            }
            install(HttpTimeout) {
                connectTimeoutMillis = 10_000
                requestTimeoutMillis = 30_000
                socketTimeoutMillis = 30_000
            }
            install(HttpRequestRetry) {
                retryOnServerErrors(maxRetries = 3)
                exponentialDelay()
            }
            install(DefaultRequest) {
                header("X-Client-Name", "kmp-stellar-sdk")
                header("X-Client-Version", Util.getSdkVersion())
            }
        }
    }

    /**
     * The effective Soroban RPC URL, defaulting based on network if not provided.
     */
    private val effectiveSorobanRpcUrl: String by lazy {
        sorobanRpcUrl ?: when (network.networkPassphrase) {
            Network.TESTNET.networkPassphrase -> "https://soroban-testnet.stellar.org"
            Network.PUBLIC.networkPassphrase -> "https://soroban.stellar.org"
            else -> "https://soroban-testnet.stellar.org"
        }
    }

    init {
        require(webAuthContractId.startsWith("C")) {
            "webAuthContractId must be a contract address starting with 'C', got: ${webAuthContractId.take(10)}..."
        }
        require(serverSigningKey.startsWith("G")) {
            "serverSigningKey must be an account address starting with 'G', got: ${serverSigningKey.take(10)}..."
        }
        require(serverHomeDomain.isNotBlank()) {
            "serverHomeDomain must not be empty"
        }
        val uri = try {
            Url(authEndpoint)
        } catch (e: Exception) {
            throw IllegalArgumentException("authEndpoint must be a valid URL: $authEndpoint", e)
        }
        require(uri.host.isNotBlank()) {
            "authEndpoint must have a valid host: $authEndpoint"
        }
    }

    companion object {
        private const val WEB_AUTH_VERIFY_FUNCTION = "web_auth_verify"

        /**
         * Creates a WebAuthForContracts instance by discovering configuration from a domain's stellar.toml.
         *
         * This is the recommended way to initialize WebAuthForContracts. It automatically fetches the
         * stellar.toml file from the specified domain and extracts the required configuration:
         * - WEB_AUTH_FOR_CONTRACTS_ENDPOINT: The authentication endpoint URL
         * - WEB_AUTH_CONTRACT_ID: The web auth contract ID (C... address)
         * - SIGNING_KEY: The server's public signing key for validating challenges
         *
         * The stellar.toml file is fetched from: https://{domain}/.well-known/stellar.toml
         *
         * @param domain The domain name (without protocol). E.g., "example.com"
         * @param network The Stellar network (Network.PUBLIC or Network.TESTNET)
         * @param httpClient Optional custom HTTP client for testing or proxy configuration
         * @param httpRequestHeaders Optional custom HTTP headers to include in requests
         * @return WebAuthForContracts instance configured from the domain's stellar.toml
         * @throws Sep45NoEndpointException If WEB_AUTH_FOR_CONTRACTS_ENDPOINT is missing from stellar.toml
         * @throws Sep45NoContractIdException If WEB_AUTH_CONTRACT_ID is missing from stellar.toml
         * @throws Sep45NoSigningKeyException If SIGNING_KEY is missing from stellar.toml
         */
        suspend fun fromDomain(
            domain: String,
            network: Network,
            httpClient: HttpClient? = null,
            httpRequestHeaders: Map<String, String>? = null
        ): WebAuthForContracts {
            val stellarToml = try {
                StellarToml.fromDomain(
                    domain = domain,
                    httpClient = httpClient,
                    httpRequestHeaders = httpRequestHeaders
                )
            } catch (e: Exception) {
                throw Sep45ChallengeRequestException(
                    "Failed to fetch stellar.toml from domain $domain",
                    errorMessage = e.message
                )
            }

            val webAuthEndpoint = stellarToml.generalInformation.webAuthForContractsEndpoint
                ?: throw Sep45NoEndpointException(domain)

            val contractId = stellarToml.generalInformation.webAuthContractId
                ?: throw Sep45NoContractIdException(domain)

            val signingKey = stellarToml.generalInformation.signingKey
                ?: throw Sep45NoSigningKeyException(domain)

            return WebAuthForContracts(
                authEndpoint = webAuthEndpoint,
                webAuthContractId = contractId,
                serverSigningKey = signingKey,
                serverHomeDomain = domain,
                network = network,
                httpClient = httpClient,
                httpRequestHeaders = httpRequestHeaders
            )
        }
    }

    /**
     * Performs complete SEP-45 authentication flow.
     *
     * This is the high-level API that handles the entire challenge-response flow:
     * 1. Validates input parameters
     * 2. Requests challenge from server
     * 3. Validates network passphrase (if provided in response)
     * 4. Decodes and validates authorization entries
     * 5. Signs entries with provided keypairs
     * 6. Submits signed entries to server
     * 7. Returns parsed JWT authentication token
     *
     * @param clientAccountId Contract account (C...) to authenticate
     * @param signers List of keypairs to sign the client authorization entry. For contracts
     *                that implement __check_auth with signature verification, provide the keypairs
     *                with sufficient weight to meet the contract's authentication requirements.
     *                Can be empty for contracts whose __check_auth does not require signatures.
     * @param homeDomain Optional home domain for the challenge request. If not provided,
     *                   defaults to the server home domain from stellar.toml.
     * @param clientDomain Optional client domain for verification
     * @param clientDomainAccountKeyPair Optional keypair for local client domain signing
     * @param clientDomainSigningDelegate Optional delegate for remote client domain signing
     * @param signatureExpirationLedger Optional expiration ledger for signatures. If null and
     *                                   signers are provided, automatically set to current ledger + 10
     *                                   (approximately 50-60 seconds).
     * @return Sep45AuthToken containing JWT token and parsed claims
     * @throws Sep45ChallengeRequestException If challenge request fails
     * @throws Sep45ChallengeValidationException If challenge validation fails
     * @throws Sep45TokenSubmissionException If token submission fails
     * @throws Sep45MissingClientDomainException If client domain signing parameters are misconfigured
     * @throws IllegalArgumentException If clientAccountId is not a contract address
     */
    suspend fun jwtToken(
        clientAccountId: String,
        signers: List<KeyPair> = emptyList(),
        homeDomain: String? = null,
        clientDomain: String? = null,
        clientDomainAccountKeyPair: KeyPair? = null,
        clientDomainSigningDelegate: Sep45ClientDomainSigningDelegate? = null,
        signatureExpirationLedger: Long? = null
    ): Sep45AuthToken {
        // Validate client account ID is a contract address
        require(clientAccountId.startsWith("C")) {
            "clientAccountId must be a contract address (C...), got: ${clientAccountId.take(10)}..."
        }

        // Validate client domain signing parameters
        if (clientDomainAccountKeyPair != null && clientDomainSigningDelegate != null) {
            throw Sep45MissingClientDomainException(
                "Cannot specify both clientDomainAccountKeyPair and clientDomainSigningDelegate. " +
                        "Use one or the other for client domain signing."
            )
        }

        if (clientDomain != null && clientDomainAccountKeyPair == null && clientDomainSigningDelegate == null) {
            throw Sep45MissingClientDomainException(
                "When clientDomain is specified, either clientDomainAccountKeyPair or " +
                        "clientDomainSigningDelegate must be provided for signing."
            )
        }

        val effectiveHomeDomain = homeDomain ?: serverHomeDomain

        // Step 1: Get challenge from server
        val challengeResponse = getChallenge(
            clientAccountId = clientAccountId,
            homeDomain = effectiveHomeDomain,
            clientDomain = clientDomain
        )

        // Step 2: Validate network passphrase BEFORE decoding (if provided)
        challengeResponse.networkPassphrase?.let { responsePassphrase ->
            if (responsePassphrase != network.networkPassphrase) {
                throw Sep45InvalidNetworkPassphraseException(
                    expected = network.networkPassphrase,
                    actual = responsePassphrase
                )
            }
        }

        // Step 3: Decode authorization entries
        val authorizationEntries = challengeResponse.authorizationEntries
            ?: throw Sep45ChallengeRequestException(
                "Challenge response missing authorization_entries field"
            )
        val authEntries = decodeAuthorizationEntries(authorizationEntries)

        // Step 4: Resolve client domain account ID if needed
        val clientDomainAccountId = if (clientDomain != null) {
            if (clientDomainAccountKeyPair != null) {
                clientDomainAccountKeyPair.getAccountId()
            } else if (clientDomainSigningDelegate != null) {
                // Fetch SIGNING_KEY from client domain's stellar.toml
                val clientToml = try {
                    StellarToml.fromDomain(
                        domain = clientDomain,
                        httpClient = httpClient,
                        httpRequestHeaders = httpRequestHeaders
                    )
                } catch (e: Exception) {
                    throw Sep45ChallengeRequestException(
                        "Failed to fetch stellar.toml for client domain $clientDomain",
                        errorMessage = e.message
                    )
                }
                clientToml.generalInformation.signingKey
                    ?: throw Sep45NoSigningKeyException(clientDomain)
            } else {
                null
            }
        } else {
            null
        }

        // Step 5: Validate challenge entries
        validateChallenge(
            authEntries = authEntries,
            clientAccountId = clientAccountId,
            homeDomain = effectiveHomeDomain,
            clientDomainAccountId = clientDomainAccountId
        )

        // Step 6: Get signature expiration ledger if needed
        val effectiveExpirationLedger = if (signers.isNotEmpty() && signatureExpirationLedger == null) {
            val sorobanServer = SorobanServer(effectiveSorobanRpcUrl)
            try {
                val latestLedger = sorobanServer.getLatestLedger()
                latestLedger.sequence + 10
            } finally {
                sorobanServer.close()
            }
        } else {
            signatureExpirationLedger
        }

        // Step 7: Sign authorization entries
        val signedEntries = signAuthorizationEntries(
            authEntries = authEntries,
            clientAccountId = clientAccountId,
            signers = signers,
            signatureExpirationLedger = effectiveExpirationLedger,
            clientDomainKeyPair = clientDomainAccountKeyPair,
            clientDomainAccountId = clientDomainAccountId,
            clientDomainSigningDelegate = clientDomainSigningDelegate
        )

        // Step 8: Submit signed challenge and get JWT token
        return sendSignedChallenge(signedEntries)
    }

    /**
     * Requests a challenge from the authentication server.
     *
     * This is the first step of the SEP-45 authentication flow. The server generates
     * authorization entries specifically for the client contract account and returns them
     * as base64-encoded XDR.
     *
     * @param clientAccountId Contract account (C...) to authenticate
     * @param homeDomain Optional home domain for the request. Defaults to server home domain.
     * @param clientDomain Optional client domain for verification
     * @return Sep45ChallengeResponse containing authorization entries XDR
     * @throws Sep45ChallengeRequestException If the request fails or returns an error
     */
    suspend fun getChallenge(
        clientAccountId: String,
        homeDomain: String? = null,
        clientDomain: String? = null
    ): Sep45ChallengeResponse {
        val effectiveHomeDomain = homeDomain ?: serverHomeDomain

        val url = URLBuilder(authEndpoint).apply {
            parameters.append("account", clientAccountId)
            parameters.append("home_domain", effectiveHomeDomain)
            clientDomain?.let { parameters.append("client_domain", it) }
        }.buildString()

        val response: HttpResponse = try {
            client.get(url) {
                httpRequestHeaders?.forEach { (key, headerValue) ->
                    header(key, headerValue)
                }
            }
        } catch (e: Exception) {
            throw Sep45ChallengeRequestException(
                "Network error during challenge request",
                errorMessage = e.message
            )
        }

        return when (response.status.value) {
            200 -> {
                val bodyText = try {
                    response.bodyAsText()
                } catch (e: Exception) {
                    throw Sep45ChallengeRequestException(
                        "Failed to read response body",
                        statusCode = 200,
                        errorMessage = e.message
                    )
                }

                try {
                    Sep45ChallengeResponse.fromJson(bodyText)
                } catch (e: Exception) {
                    throw Sep45ChallengeRequestException(
                        "Failed to parse challenge response",
                        statusCode = 200,
                        errorMessage = e.message
                    )
                }
            }
            400 -> {
                val errorBody = try { response.bodyAsText() } catch (e: Exception) { "Bad Request" }
                throw Sep45ChallengeRequestException(
                    "Bad request",
                    statusCode = 400,
                    errorMessage = errorBody
                )
            }
            403 -> {
                val errorBody = try { response.bodyAsText() } catch (e: Exception) { "Forbidden" }
                throw Sep45ChallengeRequestException(
                    "Forbidden",
                    statusCode = 403,
                    errorMessage = errorBody
                )
            }
            404 -> {
                throw Sep45ChallengeRequestException(
                    "WebAuth for contracts endpoint not found",
                    statusCode = 404,
                    errorMessage = "Endpoint not available at: $authEndpoint"
                )
            }
            504 -> {
                throw Sep45TimeoutException("Challenge request timed out (HTTP 504)")
            }
            else -> {
                val errorBody = try { response.bodyAsText() } catch (e: Exception) { "" }
                throw Sep45UnknownResponseException(
                    message = "Unexpected response during challenge request",
                    code = response.status.value,
                    body = errorBody
                )
            }
        }
    }

    /**
     * Validates the authorization entries from the challenge response.
     *
     * This performs the security validation checks required by SEP-45:
     * 1. No sub-invocations in any entry
     * 2. Contract address matches WEB_AUTH_CONTRACT_ID
     * 3. Function name is "web_auth_verify"
     * 4. Arguments validation (account, home_domain, web_auth_domain, nonce, etc.)
     * 5. Server entry exists with valid signature
     * 6. Client entry exists
     * 7. Client domain entry exists (if client domain provided)
     *
     * @param authEntries Entries to validate
     * @param clientAccountId Expected client contract account
     * @param homeDomain Expected home domain. Defaults to server home domain.
     * @param clientDomainAccountId Expected client domain account (if using client domain)
     * @throws Sep45ChallengeValidationException If any validation check fails
     */
    suspend fun validateChallenge(
        authEntries: List<SorobanAuthorizationEntryXdr>,
        clientAccountId: String,
        homeDomain: String? = null,
        clientDomainAccountId: String? = null
    ) {
        if (authEntries.isEmpty()) {
            throw Sep45InvalidArgsException("No authorization entries found in challenge")
        }

        val effectiveHomeDomain = homeDomain ?: serverHomeDomain

        // Extract web_auth_domain from auth endpoint URL (include port if non-standard)
        val endpointUrl = Url(authEndpoint)
        val webAuthDomain = buildString {
            append(endpointUrl.host)
            if (endpointUrl.port != 80 && endpointUrl.port != 443 && endpointUrl.specifiedPort > 0) {
                append(":${endpointUrl.port}")
            }
        }

        var nonce: String? = null
        var serverEntryFound = false
        var clientEntryFound = false
        var clientDomainEntryFound = false

        for (entry in authEntries) {
            val rootInvocation = entry.rootInvocation

            // Check 1: No sub-invocations
            if (rootInvocation.subInvocations.isNotEmpty()) {
                throw Sep45SubInvocationsFoundException(
                    "Entry contains ${rootInvocation.subInvocations.size} sub-invocations"
                )
            }

            // Check 2: Function must be contract function
            val function = rootInvocation.function
            if (function !is SorobanAuthorizedFunctionXdr.ContractFn) {
                throw Sep45InvalidFunctionNameException(
                    expected = WEB_AUTH_VERIFY_FUNCTION,
                    actual = "non-contract function"
                )
            }

            val contractFn = function.value

            // Check 3: Contract address matches WEB_AUTH_CONTRACT_ID
            val contractAddress = contractFn.contractAddress
            val actualContractId = scAddressToString(contractAddress)
            if (actualContractId != webAuthContractId) {
                throw Sep45InvalidContractAddressException(
                    expected = webAuthContractId,
                    actual = actualContractId
                )
            }

            // Check 4: Function name is "web_auth_verify"
            val functionName = contractFn.functionName.value
            if (functionName != WEB_AUTH_VERIFY_FUNCTION) {
                throw Sep45InvalidFunctionNameException(
                    expected = WEB_AUTH_VERIFY_FUNCTION,
                    actual = functionName
                )
            }

            // Check 5: Extract and validate args
            val args = extractArgsFromEntry(contractFn.args)

            // Validate account
            val entryAccount = args["account"]
            if (entryAccount != clientAccountId) {
                throw Sep45InvalidAccountException(
                    expected = clientAccountId,
                    actual = entryAccount ?: "null"
                )
            }

            // Validate home_domain
            val entryHomeDomain = args["home_domain"]
            if (entryHomeDomain != effectiveHomeDomain) {
                throw Sep45InvalidHomeDomainException(
                    expected = effectiveHomeDomain,
                    actual = entryHomeDomain ?: "null"
                )
            }

            // Validate web_auth_domain
            val entryWebAuthDomain = args["web_auth_domain"]
            if (entryWebAuthDomain != webAuthDomain) {
                throw Sep45InvalidWebAuthDomainException(
                    expected = webAuthDomain,
                    actual = entryWebAuthDomain ?: "null"
                )
            }

            // Validate web_auth_domain_account
            val entryWebAuthDomainAccount = args["web_auth_domain_account"]
            if (entryWebAuthDomainAccount != serverSigningKey) {
                throw Sep45InvalidArgsException(
                    "web_auth_domain_account mismatch. Expected: $serverSigningKey, got: $entryWebAuthDomainAccount"
                )
            }

            // Validate nonce consistency
            val entryNonce = args["nonce"]
            if (entryNonce == null) {
                throw Sep45InvalidNonceException("Nonce argument is missing")
            }
            if (nonce == null) {
                nonce = entryNonce
            } else if (nonce != entryNonce) {
                throw Sep45InvalidNonceException(
                    "Nonce is not consistent across entries. Expected: $nonce, got: $entryNonce"
                )
            }

            // Validate client domain account if provided
            if (clientDomainAccountId != null) {
                val entryClientDomainAccount = args["client_domain_account"]
                if (entryClientDomainAccount != null && entryClientDomainAccount != clientDomainAccountId) {
                    throw Sep45InvalidArgsException(
                        "client_domain_account mismatch. Expected: $clientDomainAccountId, got: $entryClientDomainAccount"
                    )
                }
            }

            // Identify which entry this is (server, client, or client domain).
            // Address-bearing credentials cover all three arms (ADDRESS, ADDRESS_V2,
            // ADDRESS_WITH_DELEGATES); source-account (Void) credentials carry no
            // address and are skipped here.
            val addressCredentials = entry.credentials.addressCredentials()
            if (addressCredentials != null) {
                val credentialsAddressStr = scAddressToString(addressCredentials.address)

                when (credentialsAddressStr) {
                    serverSigningKey -> {
                        serverEntryFound = true
                        // Verify server signature
                        if (!verifyServerSignature(entry)) {
                            throw Sep45InvalidServerSignatureException(
                                "Signature verification failed for server entry."
                            )
                        }
                    }
                    clientAccountId -> {
                        clientEntryFound = true
                    }
                    clientDomainAccountId -> {
                        clientDomainEntryFound = true
                    }
                }
            }
        }

        // Check 6: Server entry must exist
        if (!serverEntryFound) {
            throw Sep45MissingServerEntryException(
                "Expected entry with credentials.address matching server signing key: $serverSigningKey"
            )
        }

        // Check 7: Client entry must exist
        if (!clientEntryFound) {
            throw Sep45MissingClientEntryException(
                "Expected entry with credentials.address matching client account: $clientAccountId"
            )
        }

        // Check 8: Client domain entry must exist if client domain account is provided
        if (clientDomainAccountId != null && !clientDomainEntryFound) {
            throw Sep45MissingClientEntryException(
                "Expected entry with credentials.address matching client domain account: $clientDomainAccountId"
            )
        }
    }

    /**
     * Signs the authorization entries for the client account.
     *
     * @param authEntries Entries to sign
     * @param clientAccountId Client contract account
     * @param signers Keypairs to sign with
     * @param signatureExpirationLedger Expiration ledger for signatures
     * @param clientDomainKeyPair Optional client domain keypair for local signing
     * @param clientDomainAccountId Optional client domain account ID (for delegate signing)
     * @param clientDomainSigningDelegate Optional delegate for remote signing
     * @return Signed authorization entries
     */
    @OptIn(ExperimentalEncodingApi::class)
    suspend fun signAuthorizationEntries(
        authEntries: List<SorobanAuthorizationEntryXdr>,
        clientAccountId: String,
        signers: List<KeyPair>,
        signatureExpirationLedger: Long?,
        clientDomainKeyPair: KeyPair? = null,
        clientDomainAccountId: String? = null,
        clientDomainSigningDelegate: Sep45ClientDomainSigningDelegate? = null
    ): List<SorobanAuthorizationEntryXdr> {
        val signedEntries = mutableListOf<SorobanAuthorizationEntryXdr>()

        for (entry in authEntries) {
            // Address-bearing credentials cover all three arms (ADDRESS, ADDRESS_V2,
            // ADDRESS_WITH_DELEGATES); source-account (Void) entries carry no address
            // and are passed through unchanged below.
            val addressCredentials = entry.credentials.addressCredentials()
            if (addressCredentials != null) {
                val credentialsAddressStr = scAddressToString(addressCredentials.address)

                // Sign client entry
                if (credentialsAddressStr == clientAccountId) {
                    var signedEntry = entry
                    // Sign with all provided signers
                    for (signer in signers) {
                        signedEntry = Auth.authorizeEntry(
                            entry = signedEntry,
                            signer = signer,
                            validUntilLedgerSeq = signatureExpirationLedger ?: 0L,
                            network = network
                        )
                    }
                    signedEntries.add(signedEntry)
                    continue
                }

                // Sign client domain entry with local keypair
                if (clientDomainKeyPair != null && credentialsAddressStr == clientDomainKeyPair.getAccountId()) {
                    val signedEntry = Auth.authorizeEntry(
                        entry = entry,
                        signer = clientDomainKeyPair,
                        validUntilLedgerSeq = signatureExpirationLedger ?: 0L,
                        network = network
                    )
                    signedEntries.add(signedEntry)
                    continue
                }

                // Sign client domain entry via delegate (remote signing)
                if (clientDomainSigningDelegate != null &&
                    clientDomainAccountId != null &&
                    credentialsAddressStr == clientDomainAccountId
                ) {
                    // Update expiration ledger before sending to delegate, preserving
                    // the credential arm (ADDRESS, ADDRESS_V2, or ADDRESS_WITH_DELEGATES).
                    val entryWithExpiration = if (signatureExpirationLedger != null) {
                        val updatedCredentials = addressCredentials.copy(
                            signatureExpirationLedger = Uint32Xdr(signatureExpirationLedger.toUInt())
                        )
                        entry.copy(
                            credentials = entry.credentials.withUpdatedAddressCredentials(updatedCredentials)
                        )
                    } else {
                        entry
                    }

                    // Convert to base64 XDR
                    val entryXdr = entryToBase64(entryWithExpiration)

                    // Call delegate
                    val signedEntryXdr = clientDomainSigningDelegate.signEntry(entryXdr)

                    // Parse signed entry
                    val signedEntry = base64ToEntry(signedEntryXdr)
                    signedEntries.add(signedEntry)
                    continue
                }
            }

            // Add entry as-is (e.g., server entry which is already signed)
            signedEntries.add(entry)
        }

        return signedEntries
    }

    /**
     * Submits signed authorization entries to obtain a JWT token.
     *
     * @param signedEntries Signed authorization entries
     * @return Sep45AuthToken containing JWT token and parsed claims
     * @throws Sep45TokenSubmissionException If submission fails
     * @throws Sep45TimeoutException If the request times out (HTTP 504)
     */
    suspend fun sendSignedChallenge(
        signedEntries: List<SorobanAuthorizationEntryXdr>
    ): Sep45AuthToken {
        val base64Xdr = encodeAuthorizationEntries(signedEntries)

        val response: HttpResponse = try {
            if (useFormUrlEncoded) {
                client.submitForm(
                    url = authEndpoint,
                    formParameters = parameters {
                        append("authorization_entries", base64Xdr)
                    }
                ) {
                    httpRequestHeaders?.forEach { (key, headerValue) ->
                        header(key, headerValue)
                    }
                }
            } else {
                client.post(authEndpoint) {
                    contentType(ContentType.Application.Json)
                    httpRequestHeaders?.forEach { (key, headerValue) ->
                        header(key, headerValue)
                    }
                    setBody("""{"authorization_entries":"$base64Xdr"}""")
                }
            }
        } catch (e: Exception) {
            throw Sep45TokenSubmissionException(
                "Network error during token submission",
                errorMessage = e.message
            )
        }

        return when (response.status.value) {
            200, 400 -> {
                val bodyText = try {
                    response.bodyAsText()
                } catch (e: Exception) {
                    throw Sep45TokenSubmissionException(
                        "Failed to read response body",
                        statusCode = response.status.value,
                        errorMessage = e.message
                    )
                }

                val tokenResponse = try {
                    json.decodeFromString<Sep45TokenResponse>(bodyText)
                } catch (e: Exception) {
                    throw Sep45TokenSubmissionException(
                        "Failed to parse token response",
                        statusCode = response.status.value,
                        errorMessage = e.message
                    )
                }

                if (tokenResponse.error != null) {
                    throw Sep45TokenSubmissionException(
                        "Token request failed",
                        statusCode = response.status.value,
                        errorMessage = tokenResponse.error
                    )
                }

                val token = tokenResponse.token
                    ?: throw Sep45TokenSubmissionException(
                        "Response missing token field",
                        statusCode = response.status.value
                    )

                Sep45AuthToken.parse(token)
            }
            401 -> {
                val errorBody = try { response.bodyAsText() } catch (e: Exception) { "Unauthorized" }
                throw Sep45TokenSubmissionException(
                    "Signature verification failed",
                    statusCode = 401,
                    errorMessage = errorBody
                )
            }
            403 -> {
                val errorBody = try { response.bodyAsText() } catch (e: Exception) { "Forbidden" }
                throw Sep45TokenSubmissionException(
                    "Forbidden",
                    statusCode = 403,
                    errorMessage = errorBody
                )
            }
            504 -> {
                throw Sep45TimeoutException("Token submission timed out (HTTP 504)")
            }
            else -> {
                val errorBody = try { response.bodyAsText() } catch (e: Exception) { "" }
                throw Sep45UnknownResponseException(
                    message = "Unexpected response during token submission",
                    code = response.status.value,
                    body = errorBody
                )
            }
        }
    }

    /**
     * Decodes authorization entries from base64 XDR.
     *
     * The XDR format is length-prefixed: 4-byte int count, then entries.
     *
     * @param base64Xdr Base64-encoded XDR array of authorization entries
     * @return List of decoded authorization entries
     * @throws Sep45InvalidArgsException If decoding fails
     */
    @OptIn(ExperimentalEncodingApi::class)
    fun decodeAuthorizationEntries(base64Xdr: String): List<SorobanAuthorizationEntryXdr> {
        return try {
            val bytes = Base64.decode(base64Xdr)
            val reader = XdrReader(bytes)
            val count = reader.readInt()
            List(count) { SorobanAuthorizationEntryXdr.decode(reader) }
        } catch (e: Exception) {
            throw Sep45InvalidArgsException("Failed to decode authorization entries: ${e.message}")
        }
    }

    /**
     * Encodes authorization entries to base64 XDR.
     *
     * The XDR format is length-prefixed: 4-byte int count, then entries.
     *
     * @param entries List of authorization entries to encode
     * @return Base64-encoded XDR string
     */
    @OptIn(ExperimentalEncodingApi::class)
    fun encodeAuthorizationEntries(entries: List<SorobanAuthorizationEntryXdr>): String {
        val writer = XdrWriter()
        writer.writeInt(entries.size)
        entries.forEach { it.encode(writer) }
        return Base64.encode(writer.toByteArray())
    }

    // ============================================================================
    // Private Helper Methods
    // ============================================================================

    /**
     * Extracts the arguments map from contract function args.
     * The first arg should be a Map<Symbol, String>.
     */
    private fun extractArgsFromEntry(args: List<SCValXdr>): Map<String, String> {
        if (args.isEmpty()) {
            throw Sep45InvalidArgsException("No arguments found in authorization entry")
        }

        val argsVal = args[0]
        if (argsVal !is SCValXdr.Map || argsVal.value == null) {
            throw Sep45InvalidArgsException("First argument is not a map")
        }

        val result = mutableMapOf<String, String>()
        for (mapEntry in argsVal.value.value) {
            // Key should be a symbol
            val mapKey = mapEntry.key
            if (mapKey !is SCValXdr.Sym) continue
            val keyStr = mapKey.value.value

            // Value should be a string (note: property is `val` not `value`)
            val mapVal = mapEntry.`val`
            if (mapVal !is SCValXdr.Str) continue
            val valueStr = mapVal.value.value

            result[keyStr] = valueStr
        }

        return result
    }

    /**
     * Verifies the server's signature on an authorization entry.
     *
     * Uses the entry's existing nonce and signatureExpirationLedger values.
     */
    private suspend fun verifyServerSignature(entry: SorobanAuthorizationEntryXdr): Boolean {
        return try {
            // Address-bearing credentials cover all three arms; a source-account
            // (Void) entry carries no signature to verify.
            val addressCredentials = entry.credentials.addressCredentials()
                ?: return false

            // Build the authorization preimage from the entry's credentials so the
            // preimage arm matches the credential arm: ADDRESS yields the legacy
            // ENVELOPE_TYPE_SOROBAN_AUTHORIZATION; ADDRESS_V2 and ADDRESS_WITH_DELEGATES
            // yield the address-bound ENVELOPE_TYPE_SOROBAN_AUTHORIZATION_WITH_ADDRESS.
            // A V2 entry verified against a legacy preimage hashes differently and the
            // signature would never match.
            val preimage = Auth.buildHashIDPreimage(
                credentials = entry.credentials,
                networkId = network.networkId(),
                invocation = entry.rootInvocation
            )

            // Hash the preimage
            val writer = XdrWriter()
            preimage.encode(writer)
            val payload = Util.hash(writer.toByteArray())

            // Extract signature from credentials
            val signatureVal = addressCredentials.signature
            if (signatureVal !is SCValXdr.Vec || signatureVal.value == null || signatureVal.value.value.isEmpty()) {
                return false
            }

            // Get first signature entry (should be a map with public_key and signature)
            val firstSig = signatureVal.value.value[0]
            if (firstSig !is SCValXdr.Map || firstSig.value == null) {
                return false
            }

            var publicKeyBytes: ByteArray? = null
            var signatureBytes: ByteArray? = null

            for (mapEntry in firstSig.value.value) {
                val mapKey = mapEntry.key
                if (mapKey is SCValXdr.Sym) {
                    when (mapKey.value.value) {
                        "public_key" -> {
                            val mapVal = mapEntry.`val`
                            if (mapVal is SCValXdr.Bytes) {
                                publicKeyBytes = mapVal.value.value
                            }
                        }
                        "signature" -> {
                            val mapVal = mapEntry.`val`
                            if (mapVal is SCValXdr.Bytes) {
                                signatureBytes = mapVal.value.value
                            }
                        }
                    }
                }
            }

            if (publicKeyBytes == null || signatureBytes == null) {
                return false
            }

            // Verify public key matches server signing key
            val expectedPublicKey = KeyPair.fromAccountId(serverSigningKey).getPublicKey()
            if (!publicKeyBytes.contentEquals(expectedPublicKey)) {
                return false
            }

            // Verify signature
            val serverKeyPair = KeyPair.fromAccountId(serverSigningKey)
            serverKeyPair.verify(payload, signatureBytes)
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Converts an SCAddress to its string representation.
     */
    private fun scAddressToString(address: SCAddressXdr): String {
        return when (address) {
            is SCAddressXdr.AccountId -> {
                val publicKey = when (val pk = address.value.value) {
                    is PublicKeyXdr.Ed25519 -> pk.value.value
                    else -> throw IllegalArgumentException("Unsupported public key type")
                }
                StrKey.encodeEd25519PublicKey(publicKey)
            }
            is SCAddressXdr.ContractId -> {
                StrKey.encodeContract(address.value.value.value)
            }
            else -> throw IllegalArgumentException("Unsupported address type: ${address::class.simpleName}")
        }
    }

    /**
     * Converts an authorization entry to base64 XDR string.
     */
    @OptIn(ExperimentalEncodingApi::class)
    private fun entryToBase64(entry: SorobanAuthorizationEntryXdr): String {
        val writer = XdrWriter()
        entry.encode(writer)
        return Base64.encode(writer.toByteArray())
    }

    /**
     * Converts a base64 XDR string to an authorization entry.
     */
    @OptIn(ExperimentalEncodingApi::class)
    private fun base64ToEntry(base64Xdr: String): SorobanAuthorizationEntryXdr {
        val bytes = Base64.decode(base64Xdr)
        val reader = XdrReader(bytes)
        return SorobanAuthorizationEntryXdr.decode(reader)
    }
}
