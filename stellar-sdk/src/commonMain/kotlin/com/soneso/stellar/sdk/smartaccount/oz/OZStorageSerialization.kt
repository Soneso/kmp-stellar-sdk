//
//  OZStorageSerialization.kt
//  Stellar SDK Kotlin Multiplatform
//
//  Copyright (c) 2026 Soneso. All rights reserved.
//

package com.soneso.stellar.sdk.smartaccount.oz

import com.soneso.stellar.sdk.Util
import kotlinx.serialization.Serializable

// MARK: - Internal Serializable Data Transfer Objects

/**
 * JSON-serializable representation of a [StoredCredential].
 *
 * [StoredCredential] contains a [ByteArray] field (publicKey) which cannot
 * be serialized directly by kotlinx.serialization. This DTO converts the
 * ByteArray to a hex string for storage.
 */
@Serializable
internal data class SerializableCredential(
    val credentialId: String,
    val publicKeyHex: String,
    val contractId: String? = null,
    val deploymentStatus: String = CredentialDeploymentStatus.PENDING.name,
    val deploymentError: String? = null,
    val createdAt: Long,
    val lastUsedAt: Long? = null,
    val nickname: String? = null,
    val isPrimary: Boolean = false,
    val transports: List<String>? = null,
    val deviceType: String? = null,
    val backedUp: Boolean? = null
)

/**
 * JSON-serializable representation of a [StoredSession].
 */
@Serializable
internal data class SerializableSession(
    val credentialId: String,
    val contractId: String,
    val connectedAt: Long,
    val expiresAt: Long
)

/**
 * JSON-serializable index of credential IDs for enumeration.
 */
@Serializable
internal data class CredentialIndex(
    val ids: List<String>
)

// MARK: - Conversion Helpers

/**
 * Converts a [StoredCredential] to its JSON-serializable form.
 */
internal fun StoredCredential.toSerializable(): SerializableCredential = SerializableCredential(
    credentialId = credentialId,
    publicKeyHex = Util.bytesToHex(publicKey),
    contractId = contractId,
    deploymentStatus = deploymentStatus.name,
    deploymentError = deploymentError,
    createdAt = createdAt,
    lastUsedAt = lastUsedAt,
    nickname = nickname,
    isPrimary = isPrimary,
    transports = transports,
    deviceType = deviceType,
    backedUp = backedUp
)

/**
 * Converts a [SerializableCredential] back to a [StoredCredential].
 *
 * @throws IllegalArgumentException if the deployment status name is invalid or hex is malformed.
 */
internal fun SerializableCredential.toStoredCredential(): StoredCredential = StoredCredential(
    credentialId = credentialId,
    publicKey = Util.hexToBytes(publicKeyHex),
    contractId = contractId,
    deploymentStatus = CredentialDeploymentStatus.valueOf(deploymentStatus),
    deploymentError = deploymentError,
    createdAt = createdAt,
    lastUsedAt = lastUsedAt,
    nickname = nickname,
    isPrimary = isPrimary,
    transports = transports,
    deviceType = deviceType,
    backedUp = backedUp
)

/**
 * Converts a [StoredSession] to its JSON-serializable form.
 */
internal fun StoredSession.toSerializable(): SerializableSession = SerializableSession(
    credentialId = credentialId,
    contractId = contractId,
    connectedAt = connectedAt,
    expiresAt = expiresAt
)

/**
 * Converts a [SerializableSession] back to a [StoredSession].
 */
internal fun SerializableSession.toStoredSession(): StoredSession = StoredSession(
    credentialId = credentialId,
    contractId = contractId,
    connectedAt = connectedAt,
    expiresAt = expiresAt
)
