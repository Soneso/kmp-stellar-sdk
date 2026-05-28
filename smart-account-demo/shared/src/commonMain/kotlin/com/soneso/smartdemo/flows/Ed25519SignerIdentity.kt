package com.soneso.smartdemo.flows

/**
 * Composite identity for an Ed25519 signer as used by the signer picker.
 *
 * Serves as the map key linking a verified hex secret from the picker to
 * the on-chain `External(verifierAddress, publicKey)` signer slot in the
 * submission flow. Content-based equality and hash are required because
 * [publicKey] is a [ByteArray].
 *
 * @property verifierAddress C-strkey of the Ed25519 verifier contract that validates
 *   this signer's signatures on-chain.
 * @property publicKey 32-byte Ed25519 public key identifying the signer slot.
 */
data class Ed25519SignerIdentity(
    val verifierAddress: String,
    val publicKey: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false
        other as Ed25519SignerIdentity
        if (verifierAddress != other.verifierAddress) return false
        return publicKey.contentEquals(other.publicKey)
    }

    override fun hashCode(): Int {
        var result = verifierAddress.hashCode()
        result = 31 * result + publicKey.contentHashCode()
        return result
    }
}
