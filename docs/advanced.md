# Advanced Topics: KMP Stellar SDK

This comprehensive guide covers advanced topics for power users of the KMP Stellar SDK, including custom implementations, performance optimization, security hardening, and production deployment patterns.

## Table of Contents

1. [Custom Signer Implementations](#custom-signer-implementations)
2. [Hardware Wallet Integration](#hardware-wallet-integration)
3. [Multi-Signature Workflows](#multi-signature-workflows)
4. [Advanced Transaction Building](#advanced-transaction-building)
5. [Soroban Authorization Flows](#soroban-authorization-flows)
6. [Performance Optimization](#performance-optimization)
7. [Memory Management and Security](#memory-management-and-security)
8. [Advanced Cryptographic Operations](#advanced-cryptographic-operations)
9. [External System Integration](#external-system-integration)
10. [Production Deployment](#production-deployment)
11. [Advanced Error Handling](#advanced-error-handling)
12. [Custom ContractClient Implementations](#custom-contractclient-implementations)
13. [WebAssembly Considerations](#webassembly-considerations)
14. [Native Interop Patterns](#native-interop-patterns)
15. [Advanced Coroutine Patterns](#advanced-coroutine-patterns)

## Custom Signer Implementations

### Overview

The SDK provides a flexible `Signer` interface that allows integration with custom signing mechanisms including hardware wallets, HSMs, remote signing services, and threshold signature schemes.

### Core Signer Interface

```kotlin
// stellar-sdk/src/commonMain/kotlin/com/stellar/sdk/Signer.kt
interface Signer {
    /**
     * Signs the provided transaction hash.
     * @param transactionHash The 32-byte hash to sign
     * @return DecoratedSignature containing hint and signature
     */
    suspend fun signTransaction(transactionHash: ByteArray): DecoratedSignature

    /**
     * Returns the public key associated with this signer.
     */
    fun getPublicKey(): ByteArray

    /**
     * Returns the signature hint (last 4 bytes of public key).
     */
    fun getHint(): ByteArray
}
```

### Implementation Examples

#### 1. Remote Signing Service

```kotlin
/**
 * Signer that delegates signing to a remote service.
 * Useful for custody solutions and enterprise deployments.
 */
class RemoteSigningService(
    private val serviceUrl: String,
    private val apiKey: String,
    private val publicKey: ByteArray,
    private val httpClient: HttpClient = HttpClient()
) : Signer {

    init {
        require(publicKey.size == 32) { "Public key must be 32 bytes" }
    }

    override suspend fun signTransaction(transactionHash: ByteArray): DecoratedSignature {
        require(transactionHash.size == 32) { "Transaction hash must be 32 bytes" }

        // Prepare signing request
        val request = SigningRequest(
            hash = Base64.encode(transactionHash),
            publicKey = StrKey.encodeEd25519PublicKey(publicKey),
            metadata = mapOf(
                "timestamp" to Clock.System.now().toString(),
                "sdk_version" to "1.0.0"
            )
        )

        // Send to remote service
        val response: SigningResponse = httpClient.post(serviceUrl) {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $apiKey")
            setBody(request)
        }

        // Validate response
        if (!response.success) {
            throw SigningException("Remote signing failed: ${response.error}")
        }

        val signatureBytes = Base64.decode(response.signature)
        require(signatureBytes.size == 64) { "Invalid signature length" }

        // Verify signature locally for additional security
        val verificationKey = KeyPair.fromPublicKey(publicKey)
        if (!verificationKey.verify(transactionHash, signatureBytes)) {
            throw SigningException("Signature verification failed")
        }

        return DecoratedSignature(
            hint = getHint(),
            signature = signatureBytes
        )
    }

    override fun getPublicKey(): ByteArray = publicKey.copyOf()

    override fun getHint(): ByteArray =
        publicKey.copyOfRange(publicKey.size - 4, publicKey.size)
}
```

#### 2. Threshold Signature Scheme (TSS)

```kotlin
/**
 * Implements threshold signatures where k-of-n parties must cooperate to sign.
 * Based on Schnorr threshold signatures or similar schemes.
 */
class ThresholdSigner(
    private val localShare: SecretShare,
    private val publicKey: ByteArray,
    private val threshold: Int,
    private val parties: List<PartyInfo>,
    private val coordinator: TSSCoordinator
) : Signer {

    override suspend fun signTransaction(transactionHash: ByteArray): DecoratedSignature {
        // Phase 1: Commitment
        val commitment = generateCommitment(transactionHash)
        val allCommitments = coordinator.gatherCommitments(commitment, parties)

        // Phase 2: Share generation
        val partialSignature = computePartialSignature(
            transactionHash,
            localShare,
            allCommitments
        )

        // Phase 3: Aggregation
        val partialSignatures = coordinator.gatherPartialSignatures(
            partialSignature,
            parties,
            threshold
        )

        // Combine partial signatures
        val finalSignature = combineSignatures(partialSignatures)

        // Verify threshold signature
        if (!verifyThresholdSignature(transactionHash, finalSignature, publicKey)) {
            throw SigningException("Threshold signature verification failed")
        }

        return DecoratedSignature(
            hint = getHint(),
            signature = finalSignature
        )
    }

    private suspend fun generateCommitment(message: ByteArray): Commitment {
        // Generate random nonce
        val nonce = ByteArray(32)
        SecureRandom.nextBytes(nonce)

        // Compute commitment
        return Commitment(
            value = Sha256.hash(nonce + message),
            nonce = nonce,
            partyId = localShare.partyId
        )
    }

    private fun computePartialSignature(
        message: ByteArray,
        share: SecretShare,
        commitments: List<Commitment>
    ): PartialSignature {
        // Implement partial signature generation
        // This is scheme-specific (Schnorr, ECDSA, EdDSA)
        // ...
    }

    private fun combineSignatures(
        partialSignatures: List<PartialSignature>
    ): ByteArray {
        // Implement signature aggregation
        // ...
    }

    override fun getPublicKey(): ByteArray = publicKey.copyOf()
    override fun getHint(): ByteArray = publicKey.copyOfRange(28, 32)
}
```

## Hardware Wallet Integration

### Ledger Integration

```kotlin
/**
 * Ledger hardware wallet integration via WebUSB/WebHID (Browser)
 * or USB/Bluetooth (Native platforms).
 */
class LedgerSigner(
    private val transport: LedgerTransport,
    private val derivationPath: String = "44'/148'/0'"  // Stellar default
) : Signer {

    private var publicKey: ByteArray? = null

    /**
     * Initialize connection and retrieve public key.
     */
    suspend fun connect() {
        transport.open()

        // Get public key from device
        val response = transport.send(
            cla = 0xE0,
            ins = 0x02,  // GET_PUBLIC_KEY
            p1 = 0x00,
            p2 = 0x00,
            data = encodeBIP32Path(derivationPath)
        )

        publicKey = response.copyOfRange(1, 33)  // Skip first byte (key type)
    }

    override suspend fun signTransaction(transactionHash: ByteArray): DecoratedSignature {
        requireNotNull(publicKey) { "Not connected to device" }

        // Request user confirmation on device
        val response = transport.send(
            cla = 0xE0,
            ins = 0x08,  // SIGN_HASH
            p1 = 0x00,
            p2 = 0x00,
            data = transactionHash
        )

        if (response.isEmpty() || response[0] != 0x90.toByte()) {
            throw SigningException("User rejected transaction on device")
        }

        val signature = response.copyOfRange(1, 65)  // 64-byte signature

        return DecoratedSignature(
            hint = getHint(),
            signature = signature
        )
    }

    override fun getPublicKey(): ByteArray =
        publicKey?.copyOf() ?: throw IllegalStateException("Not connected")

    override fun getHint(): ByteArray =
        getPublicKey().copyOfRange(28, 32)

    private fun encodeBIP32Path(path: String): ByteArray {
        // Convert "44'/148'/0'" to byte array
        // Format: [length][44 | 0x80000000][148 | 0x80000000][0]
        val components = path.split("/")
        val buffer = ByteArrayOutputStream()
        buffer.write(components.size)

        components.forEach { component ->
            val hardened = component.endsWith("'")
            val index = component.removeSuffix("'").toInt()
            val value = if (hardened) index or 0x80000000.toInt() else index
            buffer.write(value.toByteArray())
        }

        return buffer.toByteArray()
    }
}
```

### Trezor Integration

```kotlin
/**
 * Trezor hardware wallet integration.
 */
class TrezorSigner(
    private val transport: TrezorTransport,
    private val accountIndex: Int = 0
) : Signer {

    private lateinit var publicKey: ByteArray

    suspend fun initialize() {
        // Initialize Trezor session
        val features = transport.getFeatures()

        // Get Stellar public key
        val keyResponse = transport.stellarGetAddress(
            addressN = intArrayOf(
                44 or HARDENED_BIT,
                148 or HARDENED_BIT,
                accountIndex or HARDENED_BIT
            ),
            showDisplay = false
        )

        publicKey = StrKey.decodeEd25519PublicKey(keyResponse.address)
    }

    override suspend fun signTransaction(transactionHash: ByteArray): DecoratedSignature {
        // Request signature from Trezor
        val signResponse = transport.stellarSignTransaction(
            addressN = intArrayOf(
                44 or HARDENED_BIT,
                148 or HARDENED_BIT,
                accountIndex or HARDENED_BIT
            ),
            transactionHash = transactionHash
        )

        return DecoratedSignature(
            hint = getHint(),
            signature = signResponse.signature
        )
    }

    override fun getPublicKey(): ByteArray = publicKey.copyOf()
    override fun getHint(): ByteArray = publicKey.copyOfRange(28, 32)

    companion object {
        private const val HARDENED_BIT = 0x80000000.toInt()
    }
}
```

## Multi-Signature Workflows

### Advanced Multi-Sig Transaction Coordination

```kotlin
/**
 * Coordinates multi-signature transaction signing across multiple parties.
 * Handles signature collection, validation, and submission.
 */
class MultiSigCoordinator(
    private val horizonServer: HorizonServer,
    private val network: Network
) {

    /**
     * Represents a multi-sig transaction in progress.
     */
    data class SigningSession(
        val id: String = UUID.randomString(),
        val transaction: Transaction,
        val requiredSignatures: Set<String>,  // Public keys
        val collectedSignatures: MutableMap<String, DecoratedSignature> = mutableMapOf(),
        val metadata: Map<String, Any> = emptyMap(),
        val createdAt: Instant = Clock.System.now(),
        val expiresAt: Instant
    )

    private val sessions = ConcurrentHashMap<String, SigningSession>()

    /**
     * Initiates a new multi-signature signing session.
     */
    suspend fun initiateSigningSession(
        sourceAccount: String,
        operations: List<Operation>,
        signers: List<String>,  // Required signer public keys
        timeoutSeconds: Long = 3600
    ): SigningSession {
        // Load account to get sequence number
        val account = horizonServer.accounts().account(sourceAccount)

        // Build transaction
        val transaction = Transaction(
            sourceAccount = account,
            network = network,
            operations = operations,
            timeBounds = TimeBounds.withTimeout(timeoutSeconds),
            baseFee = 100
        )

        // Create session
        val session = SigningSession(
            transaction = transaction,
            requiredSignatures = signers.toSet(),
            expiresAt = Clock.System.now() + timeoutSeconds.seconds
        )

        sessions[session.id] = session

        // Start expiration monitor
        GlobalScope.launch {
            delay(timeoutSeconds * 1000)
            sessions.remove(session.id)
        }

        return session
    }

    /**
     * Adds a signature to the session.
     */
    suspend fun addSignature(
        sessionId: String,
        signer: Signer
    ): SigningProgress {
        val session = sessions[sessionId]
            ?: throw IllegalArgumentException("Session not found or expired")

        // Verify signer is authorized
        val signerKey = StrKey.encodeEd25519PublicKey(signer.getPublicKey())
        if (signerKey !in session.requiredSignatures) {
            throw UnauthorizedException("$signerKey is not an authorized signer")
        }

        // Check if already signed
        if (signerKey in session.collectedSignatures) {
            throw DuplicateSignatureException("$signerKey has already signed")
        }

        // Sign transaction
        val txHash = session.transaction.hash(network)
        val signature = signer.signTransaction(txHash)

        // Store signature
        session.collectedSignatures[signerKey] = signature

        // Check if ready to submit
        return SigningProgress(
            sessionId = sessionId,
            requiredCount = session.requiredSignatures.size,
            collectedCount = session.collectedSignatures.size,
            isComplete = session.collectedSignatures.size >= session.requiredSignatures.size,
            remainingSigners = session.requiredSignatures - session.collectedSignatures.keys
        )
    }

    /**
     * Submits the transaction once all signatures are collected.
     */
    suspend fun submitTransaction(sessionId: String): SubmitTransactionResponse {
        val session = sessions[sessionId]
            ?: throw IllegalArgumentException("Session not found or expired")

        // Verify we have all signatures
        if (session.collectedSignatures.size < session.requiredSignatures.size) {
            val missing = session.requiredSignatures - session.collectedSignatures.keys
            throw InsufficientSignaturesException(
                "Missing signatures from: ${missing.joinToString()}"
            )
        }

        // Apply all signatures to transaction
        session.collectedSignatures.values.forEach { decoratedSig ->
            session.transaction.addSignature(decoratedSig)
        }

        // Submit to network
        val response = horizonServer.submitTransaction(session.transaction)

        // Clean up session
        sessions.remove(sessionId)

        return response
    }

    /**
     * Implements signature aggregation for Schnorr-based multi-sig.
     */
    suspend fun aggregateSchnorrSignatures(
        sessionId: String
    ): ByteArray {
        val session = sessions[sessionId]
            ?: throw IllegalArgumentException("Session not found")

        // MuSig2 aggregation
        val publicKeys = session.requiredSignatures.map {
            StrKey.decodeEd25519PublicKey(it)
        }

        val aggregatedPubKey = SchnorrMultiSig.aggregatePublicKeys(publicKeys)
        val partialSigs = session.collectedSignatures.values.map { it.signature }

        return SchnorrMultiSig.aggregateSignatures(
            partialSignatures = partialSigs,
            publicKeys = publicKeys,
            message = session.transaction.hash(network)
        )
    }
}
```

### Signature Request Distribution

```kotlin
/**
 * Distributes signature requests to multiple signers via various channels.
 */
class SignatureRequestDistributor(
    private val coordinator: MultiSigCoordinator
) {

    /**
     * Sends signature request via email.
     */
    suspend fun requestViaEmail(
        session: MultiSigCoordinator.SigningSession,
        signerEmail: String,
        signerPublicKey: String
    ) {
        val signingUrl = generateSigningUrl(session.id, signerPublicKey)

        val emailContent = """
            You have been requested to sign a Stellar transaction.

            Transaction Details:
            - Session ID: ${session.id}
            - Operations: ${session.transaction.operations.size} operations
            - Expires: ${session.expiresAt}

            Click here to review and sign: $signingUrl

            Security: Verify the transaction details carefully before signing.
        """.trimIndent()

        EmailService.send(
            to = signerEmail,
            subject = "Stellar Transaction Signature Request",
            body = emailContent
        )
    }

    /**
     * Sends signature request via webhook.
     */
    suspend fun requestViaWebhook(
        session: MultiSigCoordinator.SigningSession,
        webhookUrl: String,
        signerPublicKey: String
    ) {
        val payload = WebhookPayload(
            type = "SIGNATURE_REQUEST",
            sessionId = session.id,
            transaction = session.transaction.toEnvelopeXdrBase64(),
            requiredSigner = signerPublicKey,
            expiresAt = session.expiresAt,
            callbackUrl = "https://api.example.com/signatures/callback"
        )

        HttpClient().post(webhookUrl) {
            contentType(ContentType.Application.Json)
            setBody(payload)
        }
    }

    /**
     * QR code for mobile signing.
     */
    fun generateSigningQRCode(
        session: MultiSigCoordinator.SigningSession,
        signerPublicKey: String
    ): ByteArray {
        val data = SigningQRData(
            version = 1,
            network = session.transaction.network.networkPassphrase,
            sessionId = session.id,
            transactionXdr = session.transaction.toEnvelopeXdrBase64(),
            signerHint = StrKey.encodeEd25519PublicKey(
                signerPublicKey.toByteArray()
            ).substring(0, 8)
        )

        return QRCodeGenerator.generate(
            Json.encodeToString(data),
            errorCorrection = QRErrorCorrection.HIGH
        )
    }
}
```

## Advanced Transaction Building

### Complex Transaction Patterns

```kotlin
/**
 * Advanced transaction builder with complex multi-operation patterns.
 */
class AdvancedTransactionBuilder(
    private val horizonServer: HorizonServer,
    private val network: Network
) {

    /**
     * Atomic path payment with fallback options.
     */
    suspend fun buildAtomicPathPayment(
        source: KeyPair,
        destination: String,
        sendAsset: Asset,
        sendAmount: String,
        destAsset: Asset,
        destMinAmount: String,
        paths: List<List<Asset>>
    ): Transaction {
        val account = horizonServer.accounts().account(source.getAccountId())

        val operations = mutableListOf<Operation>()

        // Primary path payment
        operations.add(
            PathPaymentStrictSendOperation(
                sendAsset = sendAsset,
                sendAmount = sendAmount,
                destination = destination,
                destAsset = destAsset,
                destMinAmount = destMinAmount,
                path = paths.firstOrNull() ?: emptyList()
            )
        )

        // Add fallback paths as conditional operations
        paths.drop(1).forEach { path ->
            operations.add(
                PathPaymentStrictSendOperation(
                    sendAsset = sendAsset,
                    sendAmount = sendAmount,
                    destination = destination,
                    destAsset = destAsset,
                    destMinAmount = destMinAmount,
                    path = path,
                    sourceAccount = source.getAccountId()
                )
            )
        }

        return Transaction(
            sourceAccount = account,
            network = network,
            operations = operations,
            timeBounds = TimeBounds.withTimeout(300),
            baseFee = 100 * operations.size  // Higher fee for complex transaction
        )
    }

    /**
     * Creates a claimable balance with complex conditions.
     */
    suspend fun buildConditionalClaimableBalance(
        creator: KeyPair,
        asset: Asset,
        amount: String,
        claimants: List<ClaimantConfig>
    ): Transaction {
        val account = horizonServer.accounts().account(creator.getAccountId())

        // Build complex claimant predicates
        val claimantObjects = claimants.map { config ->
            val predicate = when (config.type) {
                ClaimantType.IMMEDIATE ->
                    Claimant.predicateUnconditional()

                ClaimantType.TIME_BOUND ->
                    Claimant.predicateAnd(
                        Claimant.predicateNot(
                            Claimant.predicateBeforeAbsoluteTime(config.notBefore!!)
                        ),
                        Claimant.predicateBeforeAbsoluteTime(config.notAfter!!)
                    )

                ClaimantType.HASH_LOCKED ->
                    Claimant.predicateAnd(
                        Claimant.predicateUnconditional(),
                        // Custom predicate for hash validation
                        buildHashPredicate(config.hashLock!!)
                    )

                ClaimantType.MULTI_SIG ->
                    buildMultiSigPredicate(config.signers!!, config.threshold!!)
            }

            Claimant(config.destination, predicate)
        }

        return Transaction(
            sourceAccount = account,
            network = network,
            operations = listOf(
                CreateClaimableBalanceOperation(
                    asset = asset,
                    amount = amount,
                    claimants = claimantObjects
                )
            ),
            memo = Memo.text("Conditional payment"),
            timeBounds = TimeBounds.withTimeout(300)
        )
    }

    /**
     * Batch operation builder with automatic chunking.
     */
    suspend fun buildBatchTransaction(
        source: KeyPair,
        operations: List<Operation>,
        maxOpsPerTx: Int = 100
    ): List<Transaction> {
        val account = horizonServer.accounts().account(source.getAccountId())

        return operations.chunked(maxOpsPerTx).mapIndexed { index, ops ->
            Transaction(
                sourceAccount = account.withIncrementedSequence(index),
                network = network,
                operations = ops,
                timeBounds = TimeBounds.withTimeout(300),
                baseFee = 100
            )
        }
    }

    /**
     * Sponsorship transaction pattern.
     */
    suspend fun buildSponsoredAccountCreation(
        sponsor: KeyPair,
        newAccount: String,
        startingBalance: String,
        sponsoredReserves: Int = 2  // Base reserve + 1 additional
    ): Transaction {
        val sponsorAccount = horizonServer.accounts().account(sponsor.getAccountId())

        val operations = listOf(
            // Begin sponsorship
            BeginSponsoringFutureReservesOperation(
                sponsoredAccount = newAccount
            ),

            // Create account
            CreateAccountOperation(
                destination = newAccount,
                startingBalance = startingBalance
            ),

            // Optional: Add trustlines while sponsored
            // ChangeTrustOperation(...),

            // End sponsorship
            EndSponsoringFutureReservesOperation(
                sourceAccount = newAccount
            )
        )

        return Transaction(
            sourceAccount = sponsorAccount,
            network = network,
            operations = operations,
            timeBounds = TimeBounds.withTimeout(300),
            baseFee = 100
        )
    }
}
```

### Transaction Preconditions

```kotlin
/**
 * Advanced transaction preconditions for complex scenarios.
 */
class TransactionPreconditionBuilder {

    /**
     * Time-based preconditions.
     */
    fun buildTimePreconditions(
        validAfter: Instant? = null,
        validBefore: Instant? = null,
        minLedger: Long? = null,
        maxLedger: Long? = null
    ): Preconditions {
        return Preconditions(
            timeBounds = when {
                validAfter != null && validBefore != null ->
                    TimeBounds(validAfter.epochSeconds, validBefore.epochSeconds)
                validAfter != null ->
                    TimeBounds.after(validAfter.epochSeconds)
                validBefore != null ->
                    TimeBounds.before(validBefore.epochSeconds)
                else -> null
            },
            ledgerBounds = when {
                minLedger != null && maxLedger != null ->
                    LedgerBounds(minLedger.toUInt(), maxLedger.toUInt())
                else -> null
            }
        )
    }

    /**
     * Sequence number preconditions for strict ordering.
     */
    fun buildSequencePreconditions(
        minSeqNum: Long? = null,
        maxSeqNum: Long? = null,
        seqAge: Duration? = null,
        seqLedgerGap: Int? = null
    ): Preconditions {
        return Preconditions(
            minSequenceNumber = minSeqNum,
            minSequenceAge = seqAge?.inWholeSeconds,
            minSequenceLedgerGap = seqLedgerGap?.toUInt()
        )
    }

    /**
     * Extra signer preconditions for additional authorization.
     */
    fun buildExtraSignerPreconditions(
        extraSigners: List<SignerKey>
    ): Preconditions {
        return Preconditions(
            extraSigners = extraSigners
        )
    }
}
```

## Soroban Authorization Flows

### Custom Authorization Implementation

```kotlin
/**
 * Advanced Soroban authorization with custom signing logic.
 */
class SorobanAuthorizationManager {

    /**
     * Multi-party authorization for smart contract calls.
     */
    suspend fun authorizeWithMultipleSigners(
        contractClient: ContractClient,
        contractId: String,
        method: String,
        arguments: Map<String, Any?>,
        authorizers: List<Authorizer>
    ): AssembledTransaction<*> {
        // Initial simulation using buildInvoke for manual control
        var assembled = contractClient.buildInvoke<Any>(
            functionName = method,
            arguments = arguments,
            source = authorizers.first().accountId,
            signer = null
        )

        // Simulate to get auth requirements
        assembled.simulate()

        // Get authorization entries
        val authEntries = assembled.simulation?.results?.firstOrNull()
            ?.auth
            ?: emptyList()

        // Sign each entry with appropriate authorizer
        val signedEntries = authEntries.map { entry ->
            val requiredAddress = extractAddress(entry)
            val authorizer = authorizers.find {
                it.accountId == requiredAddress
            } ?: throw AuthorizationException(
                "No authorizer found for $requiredAddress"
            )

            authorizer.sign(entry)
        }

        // Update transaction with signed entries
        assembled = assembled.withAuth(signedEntries)

        return assembled
    }

    /**
     * Conditional authorization based on contract state.
     */
    suspend fun conditionalAuthorization(
        contractClient: ContractClient,
        condition: suspend () -> Boolean,
        authorizedAction: suspend () -> AssembledTransaction<*>,
        fallbackAction: suspend () -> AssembledTransaction<*>
    ): AssembledTransaction<*> {
        return if (condition()) {
            val assembled = authorizedAction()
            // Add authorization with elevated permissions
            assembled.withAuth(
                Auth.signAuthEntries(
                    assembled.simulation?.results?.firstOrNull()?.auth ?: emptyList(),
                    elevatedSigner,
                    network
                )
            )
        } else {
            fallbackAction()
        }
    }

    /**
     * Time-locked authorization.
     */
    class TimeLockedAuthorizer(
        private val signer: KeyPair,
        private val unlockTime: Instant
    ) : Authorizer {

        override suspend fun sign(authEntry: SorobanAuthorizationEntry): SorobanAuthorizationEntry {
            val currentTime = Clock.System.now()

            if (currentTime < unlockTime) {
                throw AuthorizationException(
                    "Authorization locked until $unlockTime"
                )
            }

            return Auth.signAuthEntry(authEntry, signer, network)
        }
    }

    /**
     * Delegated authorization with revocation.
     */
    class DelegatedAuthorizer(
        private val delegator: KeyPair,
        private val delegate: KeyPair,
        private val permissions: Set<Permission>,
        private val revocationList: RevocationList
    ) : Authorizer {

        override suspend fun sign(authEntry: SorobanAuthorizationEntry): SorobanAuthorizationEntry {
            // Check if delegation is revoked
            if (revocationList.isRevoked(delegate.getAccountId())) {
                throw AuthorizationException("Delegation has been revoked")
            }

            // Check permissions
            val requiredPermission = extractPermission(authEntry)
            if (requiredPermission !in permissions) {
                throw AuthorizationException(
                    "Delegate lacks permission: $requiredPermission"
                )
            }

            // Sign with delegate key but include delegator proof
            val delegateSig = Auth.signAuthEntry(authEntry, delegate, network)

            // Add delegation proof as additional data
            return delegateSig.withDelegationProof(
                delegator = delegator.getAccountId(),
                delegate = delegate.getAccountId(),
                permissions = permissions,
                signature = delegator.sign(
                    buildDelegationProof(authEntry, delegate)
                )
            )
        }
    }
}
```

### Authorization Session Management

```kotlin
/**
 * Manages authorization sessions for complex multi-step operations.
 */
class AuthorizationSessionManager {

    data class AuthSession(
        val id: String = UUID.randomString(),
        val contractId: String,
        val authorizer: String,
        val scope: Set<String>,  // Authorized methods
        val validUntil: Instant,
        val usageLimit: Int = Int.MAX_VALUE,
        var usageCount: Int = 0,
        val metadata: Map<String, Any> = emptyMap()
    )

    private val sessions = ConcurrentHashMap<String, AuthSession>()

    /**
     * Creates a new authorization session.
     */
    fun createSession(
        contractId: String,
        authorizer: KeyPair,
        scope: Set<String>,
        duration: Duration
    ): AuthSession {
        val session = AuthSession(
            contractId = contractId,
            authorizer = authorizer.getAccountId(),
            scope = scope,
            validUntil = Clock.System.now() + duration
        )

        sessions[session.id] = session

        // Schedule cleanup
        GlobalScope.launch {
            delay(duration.inWholeMilliseconds)
            sessions.remove(session.id)
        }

        return session
    }

    /**
     * Uses a session for authorization.
     */
    suspend fun authorizeWithSession(
        sessionId: String,
        method: String,
        authEntry: SorobanAuthorizationEntry,
        signer: KeyPair
    ): SorobanAuthorizationEntry {
        val session = sessions[sessionId]
            ?: throw AuthorizationException("Session not found or expired")

        // Validate session
        if (Clock.System.now() > session.validUntil) {
            sessions.remove(sessionId)
            throw AuthorizationException("Session expired")
        }

        if (method !in session.scope) {
            throw AuthorizationException("Method $method not in session scope")
        }

        if (session.usageCount >= session.usageLimit) {
            throw AuthorizationException("Session usage limit exceeded")
        }

        if (signer.getAccountId() != session.authorizer) {
            throw AuthorizationException("Invalid authorizer for session")
        }

        // Increment usage
        session.usageCount++

        // Sign with session context
        return Auth.signAuthEntry(authEntry, signer, network)
    }
}
```

## Performance Optimization

### Connection Pooling

```kotlin
/**
 * Optimized connection pool for Horizon/Soroban servers.
 */
class OptimizedConnectionPool(
    private val config: PoolConfig
) {
    data class PoolConfig(
        val minConnections: Int = 5,
        val maxConnections: Int = 50,
        val connectionTimeout: Duration = 30.seconds,
        val idleTimeout: Duration = 5.minutes,
        val maxLifetime: Duration = 30.minutes,
        val validationQuery: suspend (HorizonServer) -> Boolean = {
            try {
                it.root()
                true
            } catch (e: Exception) {
                false
            }
        }
    )

    private val pool = Channel<PooledConnection>(config.maxConnections)
    private val activeConnections = AtomicInteger(0)

    init {
        // Pre-create minimum connections
        runBlocking {
            repeat(config.minConnections) {
                pool.send(createConnection())
            }
        }
    }

    private suspend fun createConnection(): PooledConnection {
        val client = HttpClient {
            install(ContentNegotiation) {
                json()
            }
            install(HttpTimeout) {
                requestTimeoutMillis = config.connectionTimeout.inWholeMilliseconds
            }
            engine {
                pipelining = true
                threadsCount = 4
            }
        }

        activeConnections.incrementAndGet()

        return PooledConnection(
            client = client,
            createdAt = Clock.System.now(),
            lastUsed = Clock.System.now()
        )
    }

    suspend fun <T> use(block: suspend (HttpClient) -> T): T {
        val connection = borrowConnection()
        return try {
            block(connection.client)
        } finally {
            returnConnection(connection)
        }
    }

    private suspend fun borrowConnection(): PooledConnection {
        return pool.tryReceive().getOrNull()
            ?: if (activeConnections.get() < config.maxConnections) {
                createConnection()
            } else {
                pool.receive()  // Wait for available connection
            }
    }

    private suspend fun returnConnection(connection: PooledConnection) {
        val now = Clock.System.now()
        connection.lastUsed = now

        // Check if connection should be retired
        if (now - connection.createdAt > config.maxLifetime ||
            now - connection.lastUsed > config.idleTimeout) {
            connection.client.close()
            activeConnections.decrementAndGet()

            // Create replacement if below minimum
            if (activeConnections.get() < config.minConnections) {
                pool.send(createConnection())
            }
        } else {
            pool.send(connection)
        }
    }

    data class PooledConnection(
        val client: HttpClient,
        val createdAt: Instant,
        var lastUsed: Instant
    )
}
```

### Batch Operations

```kotlin
/**
 * Optimized batch operation processor.
 */
class BatchOperationProcessor(
    private val horizonServer: HorizonServer,
    private val network: Network
) {

    /**
     * Processes operations in optimized batches.
     */
    suspend fun processBatch(
        operations: List<Operation>,
        signer: KeyPair,
        options: BatchOptions = BatchOptions()
    ): BatchResult {
        val results = mutableListOf<TransactionResult>()
        val failures = mutableListOf<BatchFailure>()

        // Group operations by type for optimization
        val groupedOps = operations.groupBy { it::class }

        // Process each group with optimized strategy
        coroutineScope {
            groupedOps.map { (type, ops) ->
                async {
                    when (type) {
                        PaymentOperation::class ->
                            processPaymentBatch(ops, signer, options)
                        CreateAccountOperation::class ->
                            processAccountCreationBatch(ops, signer, options)
                        else ->
                            processGenericBatch(ops, signer, options)
                    }
                }
            }.awaitAll()
        }

        return BatchResult(
            successful = results,
            failed = failures,
            totalProcessed = operations.size,
            processingTime = measureTime { /* processing */ }
        )
    }

    private suspend fun processPaymentBatch(
        payments: List<Operation>,
        signer: KeyPair,
        options: BatchOptions
    ): List<TransactionResult> = coroutineScope {
        // Use channel for rate limiting
        val semaphore = Semaphore(options.maxConcurrency)

        payments.chunked(options.chunkSize).map { chunk ->
            async {
                semaphore.withPermit {
                    submitChunk(chunk, signer)
                }
            }
        }.awaitAll().flatten()
    }

    data class BatchOptions(
        val chunkSize: Int = 100,  // Max operations per transaction
        val maxConcurrency: Int = 10,  // Parallel submissions
        val retryPolicy: RetryPolicy = RetryPolicy.default(),
        val priorityFeeMultiplier: Double = 1.0
    )
}
```

### Caching Strategy

```kotlin
/**
 * Multi-level caching for SDK operations.
 */
class SdkCacheManager {

    // L1: In-memory cache for hot data
    private val l1Cache = LruCache<String, Any>(
        maxSize = 1000,
        expireAfterAccess = 5.minutes
    )

    // L2: Persistent cache for larger data
    private val l2Cache = DiskCache(
        directory = cacheDir,
        maxSize = 100.MB,
        expireAfterWrite = 1.hours
    )

    /**
     * Cached account loading with automatic refresh.
     */
    suspend fun getCachedAccount(
        accountId: String,
        server: HorizonServer,
        maxAge: Duration = 30.seconds
    ): AccountResponse {
        val cacheKey = "account:$accountId"

        // Check L1 cache
        l1Cache.get(cacheKey)?.let { cached ->
            if (cached is CachedAccount &&
                Clock.System.now() - cached.timestamp < maxAge) {
                return cached.account
            }
        }

        // Check L2 cache
        l2Cache.get(cacheKey)?.let { bytes ->
            val cached = Json.decodeFromString<CachedAccount>(bytes.decodeToString())
            if (Clock.System.now() - cached.timestamp < maxAge) {
                l1Cache.put(cacheKey, cached)  // Promote to L1
                return cached.account
            }
        }

        // Fetch from network
        val account = server.accounts().account(accountId)
        val cached = CachedAccount(account, Clock.System.now())

        // Update caches
        l1Cache.put(cacheKey, cached)
        l2Cache.put(cacheKey, Json.encodeToString(cached).encodeToByteArray())

        return account
    }

    /**
     * Smart cache invalidation.
     */
    suspend fun invalidateAccount(accountId: String, cascade: Boolean = true) {
        val cacheKey = "account:$accountId"

        l1Cache.remove(cacheKey)
        l2Cache.remove(cacheKey)

        if (cascade) {
            // Invalidate related caches
            invalidatePattern("transactions:$accountId:*")
            invalidatePattern("operations:$accountId:*")
            invalidatePattern("effects:$accountId:*")
        }
    }

    @Serializable
    data class CachedAccount(
        val account: AccountResponse,
        val timestamp: Instant
    )
}
```

## Memory Management and Security

### Secure Memory Handling

```kotlin
/**
 * Secure memory management for sensitive data.
 */
class SecureMemoryManager {

    /**
     * Secure buffer for sensitive data with automatic cleanup.
     */
    class SecureBuffer(size: Int) : AutoCloseable {
        private val buffer: ByteArray = ByteArray(size)
        @Volatile private var cleared = false

        init {
            // Lock memory to prevent swapping (platform-specific)
            lockMemory(buffer)
        }

        fun write(data: ByteArray, offset: Int = 0) {
            check(!cleared) { "Buffer has been cleared" }
            require(offset + data.size <= buffer.size) { "Data too large" }
            data.copyInto(buffer, offset)
        }

        fun read(offset: Int = 0, length: Int = buffer.size): ByteArray {
            check(!cleared) { "Buffer has been cleared" }
            return buffer.copyOfRange(offset, offset + length)
        }

        override fun close() {
            if (!cleared) {
                // Overwrite with random data multiple times
                repeat(3) {
                    SecureRandom.nextBytes(buffer)
                }
                buffer.fill(0)
                unlockMemory(buffer)
                cleared = true
            }
        }

        protected fun finalize() {
            close()  // Ensure cleanup even if not explicitly closed
        }
    }

    /**
     * Secure string handling.
     */
    class SecureString(private val chars: CharArray) : AutoCloseable {
        @Volatile private var cleared = false

        constructor(string: String) : this(string.toCharArray())

        fun use(block: (CharArray) -> Unit) {
            check(!cleared) { "SecureString has been cleared" }
            block(chars.copyOf())
        }

        override fun close() {
            if (!cleared) {
                chars.fill('\u0000')
                cleared = true
            }
        }
    }

    /**
     * Memory-safe key derivation.
     */
    suspend fun deriveKey(
        masterSecret: SecureBuffer,
        salt: ByteArray,
        iterations: Int = 100000
    ): SecureBuffer {
        return SecureBuffer(32).apply {
            val derived = Pbkdf2.derive(
                secret = masterSecret.read(),
                salt = salt,
                iterations = iterations,
                keyLength = 32
            )
            write(derived)
            derived.fill(0)  // Clear intermediate
        }
    }
}
```

### Credential Storage

```kotlin
/**
 * Secure credential storage with platform-specific implementations.
 */
expect class SecureCredentialStore() {
    suspend fun store(key: String, value: ByteArray)
    suspend fun retrieve(key: String): ByteArray?
    suspend fun delete(key: String)
    suspend fun exists(key: String): Boolean
}

// JVM implementation using Java KeyStore
actual class SecureCredentialStore {
    private val keyStore: KeyStore = KeyStore.getInstance("PKCS12")
    private val keyAlias = "stellar-sdk-master"

    init {
        val keystoreFile = File(System.getProperty("user.home"), ".stellar-sdk.keystore")
        if (keystoreFile.exists()) {
            keystoreFile.inputStream().use {
                keyStore.load(it, getKeystorePassword())
            }
        } else {
            keyStore.load(null, getKeystorePassword())
        }
    }

    actual suspend fun store(key: String, value: ByteArray) {
        val secretKey = SecretKeySpec(value, "AES")
        val entry = KeyStore.SecretKeyEntry(secretKey)
        keyStore.setEntry(key, entry, getProtectionParameter())
        saveKeystore()
    }

    actual suspend fun retrieve(key: String): ByteArray? {
        val entry = keyStore.getEntry(key, getProtectionParameter()) as? KeyStore.SecretKeyEntry
        return entry?.secretKey?.encoded
    }

    actual suspend fun delete(key: String) {
        keyStore.deleteEntry(key)
        saveKeystore()
    }

    actual suspend fun exists(key: String): Boolean = keyStore.containsAlias(key)
}

// iOS implementation using Keychain
actual class SecureCredentialStore {
    actual suspend fun store(key: String, value: ByteArray) {
        val query = mapOf(
            kSecClass to kSecClassGenericPassword,
            kSecAttrAccount to key,
            kSecValueData to value.toNSData(),
            kSecAttrAccessible to kSecAttrAccessibleWhenUnlockedThisDeviceOnly
        )

        SecItemDelete(query)
        val status = SecItemAdd(query, null)

        if (status != errSecSuccess) {
            throw SecurityException("Failed to store credential: $status")
        }
    }

    actual suspend fun retrieve(key: String): ByteArray? {
        val query = mapOf(
            kSecClass to kSecClassGenericPassword,
            kSecAttrAccount to key,
            kSecReturnData to kCFBooleanTrue,
            kSecMatchLimit to kSecMatchLimitOne
        )

        val result = SecItemCopyMatching(query)
        return (result as? NSData)?.toByteArray()
    }
}
```

## Advanced Cryptographic Operations

### Threshold Signatures

```kotlin
/**
 * Threshold signature implementation using Shamir's Secret Sharing.
 */
class ThresholdSignatureScheme(
    private val threshold: Int,
    private val totalShares: Int
) {
    init {
        require(threshold <= totalShares) { "Threshold must be <= total shares" }
        require(threshold >= 2) { "Threshold must be at least 2" }
    }

    /**
     * Splits a private key into shares.
     */
    suspend fun splitKey(privateKey: ByteArray): List<SecretShare> {
        require(privateKey.size == 32) { "Private key must be 32 bytes" }

        // Generate random coefficients for polynomial
        val coefficients = Array(threshold) { ByteArray(32) }
        coefficients[0] = privateKey  // Secret is the constant term

        for (i in 1 until threshold) {
            SecureRandom.nextBytes(coefficients[i])
        }

        // Generate shares
        return (1..totalShares).map { x ->
            val y = evaluatePolynomial(coefficients, x)
            SecretShare(
                id = x,
                value = y,
                threshold = threshold,
                metadata = mapOf(
                    "created" to Clock.System.now().toString(),
                    "version" to "1.0"
                )
            )
        }
    }

    /**
     * Reconstructs the private key from shares.
     */
    suspend fun reconstructKey(shares: List<SecretShare>): ByteArray {
        require(shares.size >= threshold) {
            "Need at least $threshold shares, got ${shares.size}"
        }

        // Use Lagrange interpolation to recover secret
        val points = shares.take(threshold).map { share ->
            Point(share.id.toBigInteger(), share.value.toBigInteger())
        }

        val secret = lagrangeInterpolation(points, BigInteger.ZERO)
        return secret.toByteArray().takeLast(32).toByteArray()
    }

    /**
     * Creates a threshold signer from shares.
     */
    suspend fun createThresholdSigner(
        shares: List<SecretShare>,
        publicKey: ByteArray
    ): Signer {
        return object : Signer {
            override suspend fun signTransaction(transactionHash: ByteArray): DecoratedSignature {
                // Collect partial signatures
                val partialSignatures = shares.map { share ->
                    computePartialSignature(transactionHash, share)
                }

                // Combine using threshold scheme
                val signature = combinePartialSignatures(
                    partialSignatures,
                    transactionHash
                )

                return DecoratedSignature(
                    hint = publicKey.copyOfRange(28, 32),
                    signature = signature
                )
            }

            override fun getPublicKey(): ByteArray = publicKey
            override fun getHint(): ByteArray = publicKey.copyOfRange(28, 32)
        }
    }

    data class SecretShare(
        val id: Int,
        val value: ByteArray,
        val threshold: Int,
        val metadata: Map<String, String> = emptyMap()
    )
}
```

### Homomorphic Operations

```kotlin
/**
 * Homomorphic operations on encrypted values.
 */
class HomomorphicOperations {

    /**
     * Additive homomorphic encryption for balance privacy.
     */
    class AdditiveHomomorphic {
        private val modulus = BigInteger("2").pow(256)

        fun encryptAmount(amount: Long, publicKey: ByteArray): EncryptedValue {
            val r = BigInteger(256, SecureRandom())
            val g = BigInteger(publicKey)
            val n = modulus

            // E(m) = g^m * r^n mod n^2
            val ciphertext = g.modPow(BigInteger.valueOf(amount), n.pow(2))
                .multiply(r.modPow(n, n.pow(2)))
                .mod(n.pow(2))

            return EncryptedValue(
                ciphertext = ciphertext.toByteArray(),
                publicKey = publicKey
            )
        }

        fun addEncrypted(a: EncryptedValue, b: EncryptedValue): EncryptedValue {
            require(a.publicKey.contentEquals(b.publicKey)) {
                "Values must be encrypted with same key"
            }

            val n = modulus
            val result = BigInteger(a.ciphertext)
                .multiply(BigInteger(b.ciphertext))
                .mod(n.pow(2))

            return EncryptedValue(
                ciphertext = result.toByteArray(),
                publicKey = a.publicKey
            )
        }

        fun multiplyByScalar(encrypted: EncryptedValue, scalar: Long): EncryptedValue {
            val n = modulus
            val result = BigInteger(encrypted.ciphertext)
                .modPow(BigInteger.valueOf(scalar), n.pow(2))

            return EncryptedValue(
                ciphertext = result.toByteArray(),
                publicKey = encrypted.publicKey
            )
        }
    }

    data class EncryptedValue(
        val ciphertext: ByteArray,
        val publicKey: ByteArray
    )
}
```

## External System Integration

### Database Integration

```kotlin
/**
 * Advanced database integration for transaction history and caching.
 */
class StellarDatabaseIntegration(
    private val dataSource: DataSource
) {

    /**
     * Stores transactions with full indexing.
     */
    suspend fun storeTransaction(
        transaction: Transaction,
        result: SubmitTransactionResponse
    ) = withContext(Dispatchers.IO) {
        dataSource.connection.use { conn ->
            conn.prepareStatement("""
                INSERT INTO transactions (
                    hash, source_account, sequence, fee,
                    operation_count, memo_type, memo_value,
                    time_bounds_min, time_bounds_max,
                    envelope_xdr, result_xdr,
                    successful, ledger, created_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """).use { stmt ->
                stmt.setString(1, result.hash)
                stmt.setString(2, transaction.sourceAccount)
                stmt.setLong(3, transaction.sequence)
                stmt.setInt(4, transaction.fee)
                stmt.setInt(5, transaction.operations.size)
                stmt.setString(6, transaction.memo.type)
                stmt.setString(7, transaction.memo.value)
                stmt.setLong(8, transaction.timeBounds?.minTime ?: 0)
                stmt.setLong(9, transaction.timeBounds?.maxTime ?: 0)
                stmt.setString(10, transaction.toEnvelopeXdrBase64())
                stmt.setString(11, result.resultXdr)
                stmt.setBoolean(12, result.successful)
                stmt.setLong(13, result.ledger)
                stmt.setTimestamp(14, Timestamp.from(Instant.now()))

                stmt.executeUpdate()
            }

            // Index operations
            transaction.operations.forEachIndexed { index, op ->
                storeOperation(conn, result.hash, index, op)
            }
        }
    }

    /**
     * Query builder for complex transaction searches.
     */
    class TransactionQueryBuilder {
        private val conditions = mutableListOf<String>()
        private val parameters = mutableListOf<Any>()

        fun withSourceAccount(account: String): TransactionQueryBuilder {
            conditions.add("source_account = ?")
            parameters.add(account)
            return this
        }

        fun withMemo(memo: String): TransactionQueryBuilder {
            conditions.add("memo_value = ?")
            parameters.add(memo)
            return this
        }

        fun inLedgerRange(min: Long, max: Long): TransactionQueryBuilder {
            conditions.add("ledger BETWEEN ? AND ?")
            parameters.add(min)
            parameters.add(max)
            return this
        }

        fun withOperationType(type: String): TransactionQueryBuilder {
            conditions.add("""
                EXISTS (
                    SELECT 1 FROM operations
                    WHERE transaction_hash = transactions.hash
                    AND operation_type = ?
                )
            """)
            parameters.add(type)
            return this
        }

        fun build(): Pair<String, List<Any>> {
            val where = if (conditions.isNotEmpty()) {
                "WHERE " + conditions.joinToString(" AND ")
            } else ""

            val query = """
                SELECT * FROM transactions
                $where
                ORDER BY created_at DESC
            """

            return query to parameters
        }
    }
}
```

### Message Queue Integration

```kotlin
/**
 * Integration with message queues for event-driven architecture.
 */
class StellarEventPublisher(
    private val kafkaProducer: KafkaProducer<String, ByteArray>
) {

    /**
     * Publishes transaction events to Kafka.
     */
    suspend fun publishTransactionEvent(
        transaction: Transaction,
        result: SubmitTransactionResponse
    ) {
        val event = TransactionEvent(
            id = UUID.randomString(),
            timestamp = Clock.System.now(),
            transactionHash = result.hash,
            sourceAccount = transaction.sourceAccount,
            successful = result.successful,
            ledger = result.ledger,
            operations = transaction.operations.map { op ->
                OperationSummary(
                    type = op::class.simpleName ?: "Unknown",
                    sourceAccount = op.sourceAccount
                )
            },
            metadata = mapOf(
                "sdk_version" to "1.0.0",
                "platform" to Platform.current
            )
        )

        val record = ProducerRecord(
            "stellar.transactions",
            result.hash,
            Json.encodeToString(event).encodeToByteArray()
        )

        kafkaProducer.send(record).await()
    }

    /**
     * Subscribes to transaction events.
     */
    fun subscribeToTransactions(
        topics: List<String> = listOf("stellar.transactions"),
        handler: suspend (TransactionEvent) -> Unit
    ): Flow<TransactionEvent> = flow {
        val consumer = KafkaConsumer<String, ByteArray>(kafkaConfig)
        consumer.subscribe(topics)

        while (currentCoroutineContext().isActive) {
            val records = consumer.poll(Duration.ofMillis(100))

            records.forEach { record ->
                val event = Json.decodeFromString<TransactionEvent>(
                    record.value().decodeToString()
                )
                emit(event)
                handler(event)
            }
        }
    }.flowOn(Dispatchers.IO)
}
```

## Production Deployment

### Health Monitoring

```kotlin
/**
 * Comprehensive health monitoring for production deployments.
 */
class StellarHealthMonitor(
    private val horizonServers: List<String>,
    private val sorobanServers: List<String>
) {

    data class HealthStatus(
        val timestamp: Instant = Clock.System.now(),
        val horizonHealth: Map<String, ServerHealth>,
        val sorobanHealth: Map<String, ServerHealth>,
        val overallStatus: Status,
        val metrics: HealthMetrics
    )

    data class ServerHealth(
        val url: String,
        val status: Status,
        val latency: Duration,
        val ledgerSequence: Long?,
        val errorMessage: String?
    )

    enum class Status { HEALTHY, DEGRADED, UNHEALTHY }

    /**
     * Performs comprehensive health check.
     */
    suspend fun checkHealth(): HealthStatus = coroutineScope {
        val horizonChecks = horizonServers.map { server ->
            async { checkHorizonHealth(server) }
        }

        val sorobanChecks = sorobanServers.map { server ->
            async { checkSorobanHealth(server) }
        }

        val horizonResults = horizonChecks.awaitAll().associateBy { it.url }
        val sorobanResults = sorobanChecks.awaitAll().associateBy { it.url }

        val overallStatus = when {
            horizonResults.all { it.value.status == Status.HEALTHY } &&
            sorobanResults.all { it.value.status == Status.HEALTHY } -> Status.HEALTHY

            horizonResults.any { it.value.status == Status.UNHEALTHY } ||
            sorobanResults.any { it.value.status == Status.UNHEALTHY } -> Status.UNHEALTHY

            else -> Status.DEGRADED
        }

        HealthStatus(
            horizonHealth = horizonResults,
            sorobanHealth = sorobanResults,
            overallStatus = overallStatus,
            metrics = calculateMetrics(horizonResults, sorobanResults)
        )
    }

    private suspend fun checkHorizonHealth(url: String): ServerHealth {
        return try {
            val start = Clock.System.now()
            val server = HorizonServer(url)
            val root = server.root()
            val latency = Clock.System.now() - start

            ServerHealth(
                url = url,
                status = if (latency < 1.seconds) Status.HEALTHY else Status.DEGRADED,
                latency = latency,
                ledgerSequence = root.historyLatestLedger,
                errorMessage = null
            )
        } catch (e: Exception) {
            ServerHealth(
                url = url,
                status = Status.UNHEALTHY,
                latency = Duration.ZERO,
                ledgerSequence = null,
                errorMessage = e.message
            )
        }
    }
}
```

### Circuit Breaker

```kotlin
/**
 * Circuit breaker for resilient API calls.
 */
class CircuitBreaker(
    private val config: CircuitBreakerConfig
) {
    data class CircuitBreakerConfig(
        val failureThreshold: Int = 5,
        val successThreshold: Int = 2,
        val timeout: Duration = 30.seconds,
        val resetTimeout: Duration = 60.seconds,
        val halfOpenRequests: Int = 3
    )

    enum class State { CLOSED, OPEN, HALF_OPEN }

    private var state = State.CLOSED
    private var failureCount = 0
    private var successCount = 0
    private var lastFailureTime: Instant? = null

    suspend fun <T> execute(
        fallback: (suspend () -> T)? = null,
        block: suspend () -> T
    ): T {
        return when (state) {
            State.CLOSED -> executeInClosed(block, fallback)
            State.OPEN -> executeInOpen(block, fallback)
            State.HALF_OPEN -> executeInHalfOpen(block, fallback)
        }
    }

    private suspend fun <T> executeInClosed(
        block: suspend () -> T,
        fallback: (suspend () -> T)?
    ): T {
        return try {
            withTimeout(config.timeout) {
                block().also {
                    successCount++
                    failureCount = 0
                }
            }
        } catch (e: Exception) {
            failureCount++
            lastFailureTime = Clock.System.now()

            if (failureCount >= config.failureThreshold) {
                state = State.OPEN
                scheduleReset()
            }

            fallback?.invoke() ?: throw e
        }
    }

    private suspend fun <T> executeInOpen(
        block: suspend () -> T,
        fallback: (suspend () -> T)?
    ): T {
        return fallback?.invoke()
            ?: throw CircuitBreakerOpenException("Circuit breaker is open")
    }

    private suspend fun <T> executeInHalfOpen(
        block: suspend () -> T,
        fallback: (suspend () -> T)?
    ): T {
        return try {
            withTimeout(config.timeout) {
                block().also {
                    successCount++
                    if (successCount >= config.successThreshold) {
                        state = State.CLOSED
                        failureCount = 0
                        successCount = 0
                    }
                }
            }
        } catch (e: Exception) {
            state = State.OPEN
            scheduleReset()
            fallback?.invoke() ?: throw e
        }
    }

    private fun scheduleReset() {
        GlobalScope.launch {
            delay(config.resetTimeout)
            state = State.HALF_OPEN
            successCount = 0
        }
    }
}
```

## Advanced Error Handling

### Comprehensive Error Recovery

```kotlin
/**
 * Advanced error recovery strategies.
 */
class ErrorRecoveryManager {

    /**
     * Retry with exponential backoff and jitter.
     */
    suspend fun <T> retryWithBackoff(
        maxAttempts: Int = 3,
        initialDelay: Duration = 1.seconds,
        maxDelay: Duration = 30.seconds,
        factor: Double = 2.0,
        jitter: Double = 0.1,
        block: suspend (attempt: Int) -> T
    ): T {
        var currentDelay = initialDelay
        var lastException: Exception? = null

        repeat(maxAttempts) { attempt ->
            try {
                return block(attempt + 1)
            } catch (e: Exception) {
                lastException = e

                if (attempt < maxAttempts - 1) {
                    val jitterMillis = (currentDelay.inWholeMilliseconds * jitter *
                        Random.nextDouble()).toLong()

                    delay(currentDelay.inWholeMilliseconds + jitterMillis)

                    currentDelay = minOf(
                        Duration.milliseconds(
                            (currentDelay.inWholeMilliseconds * factor).toLong()
                        ),
                        maxDelay
                    )
                }
            }
        }

        throw RetryExhaustedException(
            "Failed after $maxAttempts attempts",
            lastException
        )
    }

    /**
     * Smart error classification and recovery.
     */
    suspend fun <T> executeWithRecovery(
        block: suspend () -> T
    ): T {
        return try {
            block()
        } catch (e: Exception) {
            when (classifyError(e)) {
                ErrorType.TRANSIENT -> retryWithBackoff { block() }
                ErrorType.RATE_LIMIT -> handleRateLimit(e, block)
                ErrorType.AUTHENTICATION -> refreshAuthAndRetry(block)
                ErrorType.NETWORK -> handleNetworkError(e, block)
                ErrorType.PERMANENT -> throw e
            }
        }
    }

    private fun classifyError(e: Exception): ErrorType {
        return when (e) {
            is IOException -> ErrorType.NETWORK
            is HttpException -> when (e.statusCode) {
                429 -> ErrorType.RATE_LIMIT
                401, 403 -> ErrorType.AUTHENTICATION
                500, 502, 503, 504 -> ErrorType.TRANSIENT
                else -> ErrorType.PERMANENT
            }
            is TimeoutException -> ErrorType.TRANSIENT
            else -> ErrorType.PERMANENT
        }
    }

    enum class ErrorType {
        TRANSIENT,
        RATE_LIMIT,
        AUTHENTICATION,
        NETWORK,
        PERMANENT
    }
}
```

## Custom ContractClient Implementations

### Specialized Contract Clients

```kotlin
/**
 * Custom contract client for specific use cases.
 */
class CustomContractClient(
    private val sorobanServer: SorobanServer,
    private val network: Network
) {

    /**
     * Optimized client for token contracts.
     */
    class TokenContractClient(
        private val contractId: String,
        private val rpcUrl: String,
        private val network: Network
    ) {
        // Cache metadata
        private var cachedMetadata: TokenMetadata? = null
        private var metadataExpiry: Instant? = null
        private val client = ContractClient.forContract(contractId, rpcUrl, network)

        suspend fun getBalance(account: String): BigDecimal {
            val result = client.invoke<Long>(
                functionName = "balance",
                arguments = mapOf("account" to account),
                source = account,
                signer = null,
                parseResultXdrFn = { ScInt.fromScVal(it).value }
            )

            val metadata = getCachedMetadata()
            return BigDecimal(result).movePointLeft(metadata.decimals)
        }

        suspend fun transfer(
            from: KeyPair,
            to: String,
            amount: BigDecimal,
            memo: String? = null
        ): TransferResult {
            val metadata = getCachedMetadata()
            val rawAmount = amount.movePointRight(metadata.decimals).toLong()

            val arguments = if (memo != null) {
                mapOf(
                    "from" to from.getAccountId(),
                    "to" to to,
                    "amount" to rawAmount,
                    "memo" to memo
                )
            } else {
                mapOf(
                    "from" to from.getAccountId(),
                    "to" to to,
                    "amount" to rawAmount
                )
            }

            // Using invoke API with native types (auto-executes)
            client.invoke<Unit>(
                functionName = if (memo != null) "transfer_with_memo" else "transfer",
                arguments = arguments,
                source = from.getAccountId(),
                signer = from
            )

            // For manual control over the transaction lifecycle (multi-sig), use buildInvoke
            val assembled = client.buildInvoke<Unit>(
                functionName = "transfer",
                arguments = mapOf(
                    "from" to from.getAccountId(),
                    "to" to to,
                    "amount" to rawAmount
                )
            )

            return TransferResult(
                transactionHash = assembled.transaction?.hash()?.toHexString() ?: "",
                from = from.getAccountId(),
                to = to,
                amount = amount,
                fee = assembled.transaction?.fee ?: 0,
                ledger = assembled.submitResult?.ledger ?: 0
            )
        }

        private suspend fun getCachedMetadata(): TokenMetadata {
            val now = Clock.System.now()

            if (cachedMetadata != null && metadataExpiry != null && now < metadataExpiry!!) {
                return cachedMetadata!!
            }

            // Fetch metadata
            val metadata = fetchMetadata()
            cachedMetadata = metadata
            metadataExpiry = now + 1.hours

            return metadata
        }

        private suspend fun fetchMetadata(): TokenMetadata {
            // Option 1: Fetch metadata using funcResToNative (cleaner with spec)
            val nameXdr = client.invoke<SCValXdr>(
                functionName = "name",
                arguments = emptyMap(),
                source = contractId,
                signer = null
            )
            val name = client.funcResToNative("name", nameXdr) as String

            val symbolXdr = client.invoke<SCValXdr>(
                functionName = "symbol",
                arguments = emptyMap(),
                source = contractId,
                signer = null
            )
            val symbol = client.funcResToNative("symbol", symbolXdr) as String

            val decimalsXdr = client.invoke<SCValXdr>(
                functionName = "decimals",
                arguments = emptyMap(),
                source = contractId,
                signer = null
            )
            val decimals = client.funcResToNative("decimals", decimalsXdr) as UInt

            // Option 2: Using parseResultXdrFn (when custom parsing needed)
            val nameAlt = client.invoke<String>(
                functionName = "name",
                arguments = emptyMap(),
                source = contractId,
                signer = null,
                parseResultXdrFn = { Scv.fromString(it) }
            )

            return TokenMetadata(
                name = name,
                symbol = symbol,
                decimals = decimals.toInt()
            )
        }
    }

    /**
     * Batch contract operations.
     */
    suspend fun batchInvoke(
        calls: List<ContractCall>
    ): List<Result<Any?>> = coroutineScope {
        calls.map { call ->
            async {
                runCatching {
                    val client = ContractClient.forContract(
                        contractId = call.contractId,
                        rpcUrl = sorobanServer.serverUrl,
                        network = network
                    )

                    client.invoke<Any>(
                        functionName = call.method,
                        arguments = call.arguments,
                        source = call.sourceAccount,
                        signer = null,
                        parseResultXdrFn = call.parseResultFn
                    )
                }
            }
        }.awaitAll()
    }

    data class ContractCall(
        val contractId: String,
        val method: String,
        val arguments: Map<String, Any?>,
        val sourceAccount: String,
        val parseResultFn: ((SCValXdr) -> Any)?
    )
}
```

## WebAssembly Considerations

### JavaScript/WASM Optimization

```kotlin
/**
 * WebAssembly-specific optimizations for JavaScript platform.
 */
@JsExport
class WasmOptimizedClient {

    /**
     * Streaming parser for large responses.
     */
    @OptIn(ExperimentalJsExport::class)
    suspend fun streamingParse(
        data: ArrayBuffer,
        chunkSize: Int = 1024
    ): Flow<ParsedChunk> = flow {
        val uint8Array = Uint8Array(data)
        var offset = 0

        while (offset < uint8Array.length) {
            val end = minOf(offset + chunkSize, uint8Array.length)
            val chunk = uint8Array.subarray(offset, end)

            emit(ParsedChunk(
                data = chunk.toByteArray(),
                offset = offset,
                isLast = end >= uint8Array.length
            ))

            offset = end

            // Yield to event loop
            delay(1)
        }
    }

    /**
     * Memory-efficient XDR encoding.
     */
    @JsExport
    suspend fun encodeXdrStreaming(
        transaction: dynamic
    ): ArrayBuffer {
        // Use streaming encoder to avoid large allocations
        val encoder = XdrStreamEncoder()

        encoder.writeString(transaction.sourceAccount as String)
        encoder.writeInt(transaction.fee as Int)
        encoder.writeSequence(transaction.sequence as Long)

        // Process operations in chunks
        val operations = transaction.operations as Array<dynamic>
        operations.forEach { op ->
            encoder.writeOperation(op)

            // Yield periodically
            if (encoder.size > 10_000) {
                delay(1)
            }
        }

        return encoder.toArrayBuffer()
    }

    /**
     * Worker pool for parallel processing.
     */
    @JsExport
    class WorkerPool(size: Int = 4) {
        private val workers = Array(size) {
            Worker("stellar-worker.js")
        }
        private var nextWorker = 0

        suspend fun process(task: WorkerTask): dynamic {
            val worker = workers[nextWorker]
            nextWorker = (nextWorker + 1) % workers.size

            return suspendCoroutine { continuation ->
                worker.onmessage = { event ->
                    continuation.resume(event.data)
                }
                worker.postMessage(task)
            }
        }
    }
}
```

## Native Interop Patterns

### Platform-Specific Native Integration

```kotlin
/**
 * Native platform integration patterns.
 */
expect class NativeCryptoAccelerator() {
    suspend fun acceleratedSign(data: ByteArray, key: ByteArray): ByteArray
    suspend fun acceleratedVerify(data: ByteArray, signature: ByteArray, key: ByteArray): Boolean
}

// iOS implementation using CryptoKit
actual class NativeCryptoAccelerator {
    actual suspend fun acceleratedSign(data: ByteArray, key: ByteArray): ByteArray {
        memScoped {
            val keyData = key.toNSData()
            val messageData = data.toNSData()

            val privateKey = P256.Signing.PrivateKey(rawRepresentation: keyData)
            val signature = privateKey.signature(for: messageData)

            return signature.toByteArray()
        }
    }
}

// JVM implementation using hardware security module
actual class NativeCryptoAccelerator {
    private val provider = Security.getProvider("SunPKCS11")

    actual suspend fun acceleratedSign(data: ByteArray, key: ByteArray): ByteArray {
        return withContext(Dispatchers.IO) {
            val keySpec = PKCS8EncodedKeySpec(key)
            val privateKey = KeyFactory.getInstance("EC", provider)
                .generatePrivate(keySpec)

            val signature = Signature.getInstance("SHA256withECDSA", provider)
            signature.initSign(privateKey)
            signature.update(data)
            signature.sign()
        }
    }
}
```

## Advanced Coroutine Patterns

### Structured Concurrency

```kotlin
/**
 * Advanced coroutine patterns for complex async operations.
 */
class AdvancedCoroutinePatterns {

    /**
     * Supervised parallel execution with timeout and cancellation.
     */
    suspend fun <T> supervisedParallel(
        tasks: List<suspend () -> T>,
        timeout: Duration = 30.seconds,
        concurrency: Int = 10
    ): List<Result<T>> = supervisorScope {
        withTimeout(timeout) {
            val semaphore = Semaphore(concurrency)

            tasks.map { task ->
                async {
                    semaphore.withPermit {
                        runCatching { task() }
                    }
                }
            }.awaitAll()
        }
    }

    /**
     * Race multiple operations and cancel losers.
     */
    suspend fun <T> race(vararg blocks: suspend CoroutineScope.() -> T): T =
        coroutineScope {
            select {
                blocks.map { block ->
                    async { block() }.onAwait { it }
                }
            }.also {
                coroutineContext.cancelChildren()  // Cancel remaining
            }
        }

    /**
     * Debounced operations.
     */
    class Debouncer<T>(
        private val delay: Duration,
        private val scope: CoroutineScope
    ) {
        private var job: Job? = null

        fun debounce(block: suspend () -> T): Job {
            job?.cancel()
            job = scope.launch {
                delay(delay)
                block()
            }
            return job!!
        }
    }

    /**
     * Flow-based reactive streams.
     */
    fun createTransactionStream(
        server: HorizonServer,
        account: String
    ): Flow<TransactionResponse> = flow {
        var cursor = "now"

        while (currentCoroutineContext().isActive) {
            val transactions = server.transactions()
                .forAccount(account)
                .cursor(cursor)
                .limit(100)
                .execute()

            transactions.records.forEach { tx ->
                emit(tx)
                cursor = tx.pagingToken
            }

            delay(5.seconds)  // Poll interval
        }
    }.flowOn(Dispatchers.IO)
        .retry(3) { e ->
            logger.warn("Stream error, retrying", e)
            delay(10.seconds)
            true
        }
        .shareIn(
            scope = GlobalScope,
            started = SharingStarted.WhileSubscribed(5000),
            replay = 10
        )
}
```

## Conclusion

This advanced guide covers sophisticated patterns and techniques for building production-grade applications with the KMP Stellar SDK. These patterns enable:

- **Enterprise Integration**: Hardware wallets, HSMs, and custody solutions
- **High Performance**: Connection pooling, caching, and batch operations
- **Security**: Memory protection, secure storage, and threshold signatures
- **Reliability**: Circuit breakers, retry strategies, and health monitoring
- **Scalability**: Event-driven architecture, database integration, and streaming

For specific implementation details and additional examples, refer to:
- [API Reference](./api-reference.md) - Detailed API documentation
- [Platform Guides](./platforms/) - Platform-specific implementations
- [Sample Applications](./sample-apps.md) - Complete example applications

---

*Last updated: October 2025*
*KMP SDK Version: 1.0.0*