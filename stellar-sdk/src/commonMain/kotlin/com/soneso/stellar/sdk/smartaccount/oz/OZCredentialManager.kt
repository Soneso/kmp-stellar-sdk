//
//  OZCredentialManager.kt
//  Stellar SDK Kotlin Multiplatform
//
//  Copyright (c) 2026 Soneso. All rights reserved.
//

package com.soneso.stellar.sdk.smartaccount.oz
import com.soneso.stellar.sdk.smartaccount.core.*
import com.soneso.stellar.sdk.currentTimeMillis
import com.soneso.stellar.sdk.rpc.SorobanServer
import com.soneso.stellar.sdk.scval.Scv

/**
 * Manages the lifecycle of smart account credentials.
 *
 * OZCredentialManager provides operations for creating, querying, updating, and deleting
 * stored credentials. It handles credential deployment state transitions and ensures
 * data integrity through validation and error handling.
 *
 * Credential State Machine:
 * ```
 * pending --[deploy success]--> credential DELETED from storage
 * pending --[deploy failure]--> failed (deploymentError set)
 * pending --[sync discovers contract on-chain]--> credential DELETED from storage
 * failed  --[deleteCredential]--> credential DELETED from storage
 * ```
 *
 * After successful deployment (or sync discovery), credentials are deleted from storage.
 * Reconnection works via sessions or the indexer. Failed deployments can be retried by
 * deleting the credential and creating a new one.
 *
 * Thread Safety:
 * All operations delegate to the StorageAdapter, which is responsible for thread-safety.
 *
 * Example usage:
 * ```kotlin
 * val manager = kit.credentialManager
 *
 * // Get all credentials
 * val all = manager.getAllCredentials()
 *
 * // Get pending and failed credentials
 * val pending = manager.getPendingCredentials()
 *
 * // Sync a credential with on-chain state (deletes if deployed)
 * val isDeployed = manager.sync(credentialId = "base64url-id")
 *
 * // Delete a pending credential
 * manager.deleteCredential(credentialId = "base64url-id")
 * ```
 */
class OZCredentialManager internal constructor(
    private val kit: OZSmartAccountKit
) {
    /**
     * Storage adapter for credential persistence.
     */
    private val storage: StorageAdapter
        get() = kit.getStorage()

    // MARK: - Public API

    /**
     * Creates a new pending credential in storage.
     *
     * The credential is created with:
     * - deploymentStatus: PENDING
     * - isPrimary: false (set to true by wallet creation flow)
     * - createdAt: current timestamp
     *
     * Validation:
     * - Public key must be exactly 65 bytes (uncompressed secp256r1 format)
     * - Credential ID must not be empty
     * - Credential ID must be unique (no existing credential with same ID)
     *
     * @param credentialId The Base64URL-encoded credential ID (must be unique and non-empty)
     * @param publicKey The uncompressed secp256r1 public key (must be 65 bytes)
     * @param contractId The smart account contract address (C-address)
     * @param nickname Optional user-friendly display name for the credential
     * @param transports Authenticator transport hints (e.g., "usb", "nfc", "ble", "internal")
     * @param deviceType Authenticator device type ("singleDevice" or "multiDevice")
     * @param backedUp Whether the passkey is backed up or synced
     * @return The newly created credential
     * @throws ValidationException.InvalidInput if validation fails
     * @throws CredentialException.AlreadyExists if a credential with the same ID exists
     * @throws StorageException.WriteFailed if saving fails
     *
     * Example:
     * ```kotlin
     * val credential = manager.createPendingCredential(
     *     credentialId = "abc123",
     *     publicKey = publicKeyData,
     *     contractId = "CBCD1234...",
     *     nickname = "Alice",
     *     transports = listOf("internal"),
     *     deviceType = "multiDevice",
     *     backedUp = true
     * )
     * println("Created credential: ${credential.credentialId}")
     * ```
     */
    suspend fun createPendingCredential(
        credentialId: String,
        publicKey: ByteArray,
        contractId: String,
        nickname: String? = null,
        transports: List<String>? = null,
        deviceType: String? = null,
        backedUp: Boolean? = null
    ): StoredCredential {
        // Validate public key size
        if (publicKey.size != SmartAccountConstants.SECP256R1_PUBLIC_KEY_SIZE) {
            throw ValidationException.invalidInput(
                field = "publicKey",
                reason = "Expected ${SmartAccountConstants.SECP256R1_PUBLIC_KEY_SIZE} bytes, got ${publicKey.size}"
            )
        }

        // Validate credential ID is not empty
        if (credentialId.isEmpty()) {
            throw ValidationException.invalidInput(
                field = "credentialId",
                reason = "Credential ID cannot be empty"
            )
        }

        // Check for existing credential with same ID
        val existing = storage.get(credentialId)
        if (existing != null) {
            throw CredentialException.alreadyExists(credentialId)
        }

        // Create the credential
        val credential = StoredCredential(
            credentialId = credentialId,
            publicKey = publicKey,
            contractId = contractId,
            deploymentStatus = CredentialDeploymentStatus.PENDING,
            isPrimary = false,
            createdAt = currentTimeMillis(),
            nickname = nickname,
            transports = transports,
            deviceType = deviceType,
            backedUp = backedUp
        )

        // Save to storage
        try {
            storage.save(credential)
        } catch (e: CredentialException) {
            throw e
        } catch (e: StorageException) {
            throw e
        } catch (e: Exception) {
            throw StorageException.writeFailed(
                key = credentialId,
                cause = e
            )
        }

        return credential
    }

    /**
     * Saves a credential to storage.
     *
     * Saves a credential directly to storage with PENDING deployment status and
     * isPrimary = false. Unlike [createPendingCredential], this does not set deployment
     * metadata (transports, deviceType, backedUp) and does not check for duplicates —
     * if a credential with the same ID already exists, it is silently overwritten.
     *
     * Validates that credentialId is non-empty and publicKey is exactly 65 bytes.
     * A null [contractId] is stored as an empty string.
     *
     * @param credentialId The Base64URL-encoded credential ID (must not be empty)
     * @param publicKey The uncompressed secp256r1 public key (65 bytes)
     * @param nickname Optional user-friendly name for the credential
     * @param contractId Optional smart account contract address (C-address). Null is stored as empty string.
     * @return The saved credential
     * @throws ValidationException.InvalidInput if credentialId is empty or publicKey is wrong size
     * @throws StorageException.WriteFailed if saving fails
     *
     * Example:
     * ```kotlin
     * val credential = manager.saveCredential(
     *     credentialId = "abc123",
     *     publicKey = publicKeyData,
     *     nickname = "MacBook Pro",
     *     contractId = "CBCD1234..."
     * )
     * ```
     */
    suspend fun saveCredential(
        credentialId: String,
        publicKey: ByteArray,
        nickname: String? = null,
        contractId: String? = null
    ): StoredCredential {
        // Validate credential ID is not empty
        if (credentialId.isEmpty()) {
            throw ValidationException.invalidInput(
                field = "credentialId",
                reason = "Credential ID cannot be empty"
            )
        }

        // Validate public key size
        if (publicKey.size != SmartAccountConstants.SECP256R1_PUBLIC_KEY_SIZE) {
            throw ValidationException.invalidInput(
                field = "publicKey",
                reason = "Expected ${SmartAccountConstants.SECP256R1_PUBLIC_KEY_SIZE} bytes, got ${publicKey.size}"
            )
        }

        val credential = StoredCredential(
            credentialId = credentialId,
            publicKey = publicKey,
            contractId = contractId ?: "",
            nickname = nickname,
            createdAt = currentTimeMillis(),
            deploymentStatus = CredentialDeploymentStatus.PENDING
        )

        try {
            storage.save(credential)
        } catch (e: StorageException) {
            throw e
        } catch (e: Exception) {
            throw StorageException.writeFailed(
                key = credentialId,
                cause = e
            )
        }

        return credential
    }

    /**
     * Marks a credential as failed deployment.
     *
     * Updates the credential's deployment status to FAILED and sets the deployment
     * error message. The credential can be retried by deleting it and creating a new one.
     *
     * @param credentialId The ID of the credential that failed deployment
     * @param error The error message describing why deployment failed
     * @throws CredentialException.NotFound if the credential does not exist
     * @throws StorageException.WriteFailed if the update fails
     *
     * Example:
     * ```kotlin
     * manager.markDeploymentFailed(
     *     credentialId = "abc123",
     *     error = "Transaction failed: insufficient balance"
     * )
     * ```
     */
    internal suspend fun markDeploymentFailed(
        credentialId: String,
        error: String
    ) {
        // Verify credential exists
        storage.get(credentialId) ?: throw CredentialException.notFound(credentialId)

        // Update deployment status
        val update = StoredCredentialUpdate(
            deploymentStatus = CredentialDeploymentStatus.FAILED,
            deploymentError = error
        )

        try {
            storage.update(credentialId, update)
        } catch (e: CredentialException) {
            throw e
        } catch (e: StorageException) {
            throw e
        } catch (e: Exception) {
            throw StorageException.writeFailed(
                key = credentialId,
                cause = e
            )
        }
    }

    /**
     * Syncs a credential with on-chain state.
     *
     * Checks whether the smart account contract for this credential exists on-chain
     * by querying the contract instance via Soroban RPC. If the contract exists, the
     * credential is deleted from storage (deployment is confirmed) and the method
     * returns true. If the contract does not exist, or if the on-chain check fails
     * (e.g., network error, storage deletion failure), the method returns false.
     *
     * This is essential for the pending credentials workflow: when a deployment
     * transaction is submitted but the app closes before confirmation, sync() allows
     * the app to discover on next launch whether the deployment actually succeeded.
     *
     * @param credentialId The ID of the credential to sync
     * @return true if the contract exists on-chain (credential was deployed), false otherwise
     * @throws CredentialException.NotFound if the credential does not exist in storage
     * @throws StorageException.ReadFailed if reading the credential fails
     *
     * Example:
     * ```kotlin
     * val isDeployed = manager.sync(credentialId = "abc123")
     * if (isDeployed) {
     *     println("Contract is deployed on-chain, credential removed from storage")
     * } else {
     *     println("Contract not yet deployed, credential still pending")
     * }
     * ```
     */
    suspend fun sync(credentialId: String): Boolean {
        val credential = try {
            storage.get(credentialId)
        } catch (e: StorageException) {
            throw e
        } catch (e: Exception) {
            throw StorageException.readFailed(
                key = credentialId,
                cause = e
            )
        } ?: throw CredentialException.notFound(credentialId)

        val contractAddress = credential.contractId
        if (contractAddress.isNullOrEmpty()) {
            return false
        }

        // Check on-chain contract existence via getContractData
        return try {
            val result = kit.sorobanServer.getContractData(
                contractId = contractAddress,
                key = Scv.toLedgerKeyContractInstance(),
                durability = SorobanServer.Durability.PERSISTENT
            )
            if (result != null) {
                // Contract exists on-chain -- remove credential from storage
                storage.delete(credentialId)
                true
            } else {
                false
            }
        } catch (e: Exception) {
            // Contract does not exist or RPC error -- treat as not deployed
            false
        }
    }

    /**
     * Syncs all stored credentials with on-chain state.
     *
     * Iterates through all stored credentials and checks each one against on-chain
     * state. Deployed credentials are removed from storage. Returns a summary of
     * deployment statuses.
     *
     * @return A [SyncResult] containing counts of deployed, pending, and failed credentials
     * @throws StorageException.ReadFailed if reading credentials fails
     *
     * Example:
     * ```kotlin
     * val result = manager.syncAll()
     * println("Deployed: ${result.deployed}, Pending: ${result.pending}, Failed: ${result.failed}")
     * ```
     */
    suspend fun syncAll(): SyncResult {
        val all = try {
            storage.getAll()
        } catch (e: StorageException) {
            throw e
        } catch (e: Exception) {
            throw StorageException.readFailed(
                key = "all",
                cause = e
            )
        }

        var deployed = 0
        var pending = 0
        var failed = 0

        for (credential in all) {
            val exists = try {
                sync(credential.credentialId)
            } catch (e: CredentialException) {
                // Credential may have been deleted by a previous sync in this loop
                false
            }

            if (exists) {
                deployed++
            } else if (credential.deploymentStatus == CredentialDeploymentStatus.FAILED) {
                failed++
            } else {
                pending++
            }
        }

        return SyncResult(deployed = deployed, pending = pending, failed = failed)
    }

    /**
     * Deletes a pending credential from storage.
     *
     * Before deleting, checks whether the contract exists on-chain by calling [sync].
     * If the contract is already deployed, the deletion is rejected because the wallet
     * exists on-chain and the credential has already been removed by sync.
     *
     * @param credentialId The ID of the credential to delete
     * @throws CredentialException.NotFound if the credential does not exist
     * @throws CredentialException.Invalid if the credential is already deployed on-chain
     * @throws StorageException.WriteFailed if deletion fails
     *
     * Example:
     * ```kotlin
     * manager.deleteCredential(credentialId = "abc123")
     * ```
     */
    suspend fun deleteCredential(credentialId: String) {
        // Verify credential exists before sync
        val credential = try {
            storage.get(credentialId)
        } catch (e: StorageException) {
            throw e
        } catch (e: Exception) {
            throw StorageException.readFailed(
                key = credentialId,
                cause = e
            )
        } ?: throw CredentialException.notFound(credentialId)

        // Check on-chain status -- if deployed, sync removes it and we throw
        val isDeployed = sync(credentialId)
        if (isDeployed) {
            throw CredentialException.invalid(
                "Cannot delete a deployed credential. The wallet exists on-chain."
            )
        }

        try {
            storage.delete(credentialId)
        } catch (e: StorageException) {
            throw e
        } catch (e: Exception) {
            throw StorageException.writeFailed(
                key = credentialId,
                cause = e
            )
        }

        // Emit credential deleted event
        kit.events.emit(SmartAccountEvent.CredentialDeleted(credentialId = credentialId))
    }

    /**
     * Retrieves a credential by its ID.
     *
     * @param credentialId The credential ID to look up
     * @return The stored credential, or null if not found
     * @throws StorageException.ReadFailed if reading fails
     *
     * Example:
     * ```kotlin
     * val credential = manager.getCredential(credentialId = "abc123")
     * if (credential != null) {
     *     println("Found credential for contract: ${credential.contractId ?: "unknown"}")
     * } else {
     *     println("Credential not found")
     * }
     * ```
     */
    suspend fun getCredential(credentialId: String): StoredCredential? {
        return try {
            storage.get(credentialId)
        } catch (e: StorageException) {
            throw e
        } catch (e: Exception) {
            throw StorageException.readFailed(
                key = credentialId,
                cause = e
            )
        }
    }

    /**
     * Retrieves all credentials associated with a specific contract.
     *
     * Returns credentials where the contractId matches the provided contract address.
     * Useful for finding all credentials (including failed deployments) for a wallet.
     *
     * @param contractId The contract address to filter by
     * @return List of credentials for this contract (empty if none found)
     * @throws StorageException.ReadFailed if reading fails
     *
     * Example:
     * ```kotlin
     * val credentials = manager.getCredentialsByContract(contractId = "CBCD1234...")
     * println("Found ${credentials.size} credential(s) for this contract")
     * ```
     */
    suspend fun getCredentialsByContract(contractId: String): List<StoredCredential> {
        return try {
            storage.getByContract(contractId)
        } catch (e: StorageException) {
            throw e
        } catch (e: Exception) {
            throw StorageException.readFailed(
                key = "contract:$contractId",
                cause = e
            )
        }
    }

    /**
     * Retrieves all stored credentials.
     *
     * Returns all credentials regardless of deployment status or contract address.
     * Useful for displaying all wallets or performing batch operations.
     *
     * @return List of all stored credentials (empty if none exist)
     * @throws StorageException.ReadFailed if reading fails
     *
     * Example:
     * ```kotlin
     * val allCredentials = manager.getAllCredentials()
     * println("Total credentials: ${allCredentials.size}")
     * ```
     */
    suspend fun getAllCredentials(): List<StoredCredential> {
        return try {
            storage.getAll()
        } catch (e: StorageException) {
            throw e
        } catch (e: Exception) {
            throw StorageException.readFailed(
                key = "all",
                cause = e
            )
        }
    }

    /**
     * Retrieves credentials for the currently connected wallet.
     *
     * Returns credentials where the contractId matches the kit's currently connected
     * contract ID. Returns an empty list if no wallet is connected.
     *
     * @return List of credentials for the connected wallet (empty if not connected or none found)
     * @throws StorageException.ReadFailed if reading fails
     *
     * Example:
     * ```kotlin
     * val walletCredentials = manager.getForConnectedWallet()
     * println("Found ${walletCredentials.size} credential(s) for current wallet")
     * ```
     */
    suspend fun getForConnectedWallet(): List<StoredCredential> {
        val contractId = kit.contractId ?: return emptyList()
        return getCredentialsByContract(contractId)
    }

    /**
     * Retrieves credentials that are pending deployment or have failed deployment.
     *
     * Returns all credentials with deployment status PENDING or FAILED. These are
     * credentials that have not been confirmed on-chain and may need attention
     * (retry, sync, or delete).
     *
     * @return List of pending and failed credentials (empty if none exist)
     * @throws StorageException.ReadFailed if reading fails
     *
     * Example:
     * ```kotlin
     * val pendingCredentials = manager.getPendingCredentials()
     * for (cred in pendingCredentials) {
     *     println("${cred.credentialId}: ${cred.deploymentStatus}")
     * }
     * ```
     */
    suspend fun getPendingCredentials(): List<StoredCredential> {
        val all = try {
            storage.getAll()
        } catch (e: StorageException) {
            throw e
        } catch (e: Exception) {
            throw StorageException.readFailed(
                key = "all",
                cause = e
            )
        }

        return all.filter {
            it.deploymentStatus == CredentialDeploymentStatus.PENDING ||
                it.deploymentStatus == CredentialDeploymentStatus.FAILED
        }
    }

    /**
     * Updates a credential with partial changes.
     *
     * Only non-null fields in the update are applied. The credential must exist
     * in storage before updating.
     *
     * @param credentialId The ID of the credential to update
     * @param updates The partial updates to apply
     * @throws CredentialException.NotFound if the credential does not exist
     * @throws StorageException.WriteFailed if the update fails
     *
     * Example:
     * ```kotlin
     * val update = StoredCredentialUpdate(
     *     nickname = "MacBook Pro",
     *     lastUsedAt = currentTimeMillis()
     * )
     * manager.updateCredential(credentialId = "abc123", updates = update)
     * ```
     */
    internal suspend fun updateCredential(credentialId: String, updates: StoredCredentialUpdate) {
        // Verify credential exists
        storage.get(credentialId) ?: throw CredentialException.notFound(credentialId)

        // Apply update
        try {
            storage.update(credentialId, updates)
        } catch (e: CredentialException) {
            throw e
        } catch (e: StorageException) {
            throw e
        } catch (e: Exception) {
            throw StorageException.writeFailed(
                key = credentialId,
                cause = e
            )
        }
    }

    /**
     * Updates the last used timestamp for a credential.
     *
     * @param credentialId The credential ID to update
     * @throws CredentialException.NotFound if the credential does not exist
     * @throws StorageException.WriteFailed if the update fails
     *
     * Example:
     * ```kotlin
     * manager.updateLastUsed(credentialId = "abc123")
     * ```
     */
    internal suspend fun updateLastUsed(credentialId: String) {
        val update = StoredCredentialUpdate(
            lastUsedAt = currentTimeMillis()
        )
        updateCredential(credentialId, update)
    }

    /**
     * Updates the nickname of a credential.
     *
     * @param credentialId The credential ID to update
     * @param nickname The new nickname (null to clear)
     * @throws CredentialException.NotFound if the credential does not exist
     * @throws StorageException.WriteFailed if the update fails
     *
     * Example:
     * ```kotlin
     * manager.updateNickname(credentialId = "abc123", nickname = "MacBook Pro Touch ID")
     * ```
     */
    suspend fun updateNickname(credentialId: String, nickname: String?) {
        val update = StoredCredentialUpdate(nickname = nickname)
        updateCredential(credentialId, update)
    }

    /**
     * Sets a credential as the primary credential.
     *
     * First unsets any existing primary credential for the same contract,
     * then sets this credential as primary.
     *
     * @param credentialId The credential ID to set as primary
     * @throws CredentialException.NotFound if the credential does not exist
     * @throws StorageException.WriteFailed if the update fails
     *
     * Example:
     * ```kotlin
     * manager.setPrimary(credentialId = "abc123")
     * ```
     */
    internal suspend fun setPrimary(credentialId: String) {
        // Verify credential exists
        val credential = storage.get(credentialId)
            ?: throw CredentialException.notFound(credentialId)

        // First, unset any existing primary credentials for the same contract
        val contractId = credential.contractId
        val allCredentials = if (contractId != null) {
            storage.getByContract(contractId)
        } else {
            storage.getAll()
        }
        for (cred in allCredentials) {
            if (cred.isPrimary && cred.credentialId != credentialId) {
                try {
                    storage.update(
                        cred.credentialId,
                        StoredCredentialUpdate(isPrimary = false)
                    )
                } catch (_: Exception) {
                    // Non-fatal: the new primary is set regardless. Having two
                    // credentials briefly marked as primary only affects which
                    // one is picked during auto-connect (first match wins).
                }
            }
        }

        // Set this credential as primary
        val update = StoredCredentialUpdate(isPrimary = true)
        try {
            storage.update(credentialId, update)
        } catch (e: CredentialException) {
            throw e
        } catch (e: StorageException) {
            throw e
        } catch (e: Exception) {
            throw StorageException.writeFailed(
                key = credentialId,
                cause = e
            )
        }
    }

    /**
     * Clears all credentials from storage.
     *
     * This operation is irreversible. Use with caution.
     *
     * @throws StorageException.WriteFailed if clearing fails
     *
     * Example:
     * ```kotlin
     * // Clear all credentials (e.g., on account deletion or reset)
     * manager.clearAll()
     * ```
     */
    suspend fun clearAll() {
        try {
            storage.clear()
        } catch (e: StorageException) {
            throw e
        } catch (e: Exception) {
            throw StorageException.writeFailed(
                key = "all",
                cause = e
            )
        }
    }
}

/**
 * Result of syncing all credentials with on-chain state.
 *
 * Returned by [OZCredentialManager.syncAll] to provide a summary
 * of how many credentials are deployed, pending, or failed.
 *
 * @property deployed Number of credentials confirmed as deployed on-chain (removed from storage)
 * @property pending Number of credentials still pending deployment
 * @property failed Number of credentials with failed deployment status
 */
data class SyncResult(
    val deployed: Int,
    val pending: Int,
    val failed: Int
)
