package com.soneso.stellar.sdk

import com.soneso.stellar.sdk.scval.Scv
import com.soneso.stellar.sdk.xdr.*

/**
 * Descriptor for a single delegate in a WITH_DELEGATES credential tree.
 *
 * Describes one node in the delegate tree: the node's address, its optional
 * pre-populated signature (defaults to void), and any nested delegates it
 * authorizes. The descriptor is immutable.
 *
 * Duplicate detection and XDR-byte ordering are enforced at tree-construction
 * time by [Auth.attachDelegates]; this type is a plain data carrier.
 *
 * @property address The StrKey-encoded address of this delegate (G... or C...;
 *   muxed M... addresses are rejected by the host and are not accepted here).
 * @property signature The initial signature SCVal for this node. Defaults to
 *   void; callers may use [Auth.authorizeEntry] with [Auth.AuthOptions.forAddress]
 *   to append a real signature after tree construction.
 * @property nestedDelegates Delegates that this node further authorizes. Empty
 *   by default; nested arrays are also XDR-byte-sorted on construction.
 */
data class DelegateDescriptor(
    val address: String,
    val signature: SCValXdr = Scv.toVoid(),
    val nestedDelegates: List<DelegateDescriptor> = emptyList()
)

// ============================================================================
// Internal credential helpers — used by Auth and the signing pipeline
// ============================================================================

/**
 * Returns the inner [SorobanAddressCredentialsXdr] for address-bearing credential
 * arms, or null for the source-account (Void) arm.
 *
 * Exhaustive over all known arms; unknown future arms (if any) are treated as
 * non-signable and return null.
 */
internal fun SorobanCredentialsXdr.addressCredentials(): SorobanAddressCredentialsXdr? =
    when (this) {
        is SorobanCredentialsXdr.Void -> null
        is SorobanCredentialsXdr.Address -> value
        is SorobanCredentialsXdr.AddressV2 -> value
        is SorobanCredentialsXdr.AddressWithDelegates -> value.addressCredentials
    }

/**
 * Returns the inner [SorobanAddressCredentialsXdr] or throws with a descriptive
 * error if the credentials are not address-based (i.e., Void / source-account).
 */
internal fun SorobanCredentialsXdr.requireAddressCredentials(): SorobanAddressCredentialsXdr =
    addressCredentials()
        ?: throw IllegalArgumentException(
            "Credentials are source-account (Void) and cannot be signed; " +
                "only Address, AddressV2, or AddressWithDelegates credentials are signable"
        )

/**
 * Returns a copy of these credentials with the inner [SorobanAddressCredentialsXdr]
 * replaced by [updated], preserving the credential arm.
 *
 * For [SorobanCredentialsXdr.AddressWithDelegates] the delegates array is
 * preserved unchanged.
 *
 * Exhaustive over all arms.
 */
internal fun SorobanCredentialsXdr.withUpdatedAddressCredentials(
    updated: SorobanAddressCredentialsXdr
): SorobanCredentialsXdr = when (this) {
    is SorobanCredentialsXdr.Void ->
        throw IllegalArgumentException(
            "Cannot update address credentials on Void (source-account) credentials"
        )
    is SorobanCredentialsXdr.Address ->
        SorobanCredentialsXdr.Address(updated)
    is SorobanCredentialsXdr.AddressV2 ->
        SorobanCredentialsXdr.AddressV2(updated)
    is SorobanCredentialsXdr.AddressWithDelegates ->
        SorobanCredentialsXdr.AddressWithDelegates(
            value.copy(addressCredentials = updated)
        )
}

// ============================================================================
// XDR byte-comparison utilities for delegate ordering
// ============================================================================

/**
 * Encodes this [SCAddressXdr] to its canonical XDR byte representation.
 *
 * Used for lexicographic ordering of delegate arrays. XDR byte order
 * differs from strkey string order: account addresses (SC_ADDRESS_TYPE_ACCOUNT,
 * discriminant 0) sort before contract addresses (SC_ADDRESS_TYPE_CONTRACT,
 * discriminant 1), which is the inverse of lexicographic strkey order
 * (G... < C... as strings but ACCOUNT < CONTRACT by XDR discriminant).
 */
internal fun SCAddressXdr.toXdrBytes(): ByteArray {
    val writer = XdrWriter()
    encode(writer)
    return writer.toByteArray()
}

/**
 * Compares two [SCAddressXdr] values by their XDR byte encoding, lexicographically.
 *
 * This implements the host-enforced ordering for delegate arrays.
 */
internal fun compareAddressByXdrBytes(a: SCAddressXdr, b: SCAddressXdr): Int {
    val aBytes = a.toXdrBytes()
    val bBytes = b.toXdrBytes()
    val minLen = minOf(aBytes.size, bBytes.size)
    for (i in 0 until minLen) {
        val diff = (aBytes[i].toInt() and 0xFF) - (bBytes[i].toInt() and 0xFF)
        if (diff != 0) return diff
    }
    return aBytes.size - bBytes.size
}

/**
 * Sorts a list of [SorobanDelegateSignatureXdr] ascending by the XDR bytes of
 * each node's address.
 *
 * @throws IllegalArgumentException if the list contains two nodes with the same
 *   XDR-encoded address (within-array duplicates are rejected by the host).
 */
internal fun sortAndValidateDelegates(
    delegates: List<SorobanDelegateSignatureXdr>
): List<SorobanDelegateSignatureXdr> {
    val sorted = delegates.sortedWith { a, b -> compareAddressByXdrBytes(a.address, b.address) }
    for (i in 1 until sorted.size) {
        val prev = sorted[i - 1].address.toXdrBytes()
        val curr = sorted[i].address.toXdrBytes()
        if (prev.contentEquals(curr)) {
            val addrStr = try {
                Address.fromSCAddress(sorted[i].address).toString()
            } catch (_: Exception) {
                "(undecodable address)"
            }
            throw IllegalArgumentException(
                "Duplicate delegate address within one array: $addrStr. " +
                    "The same address may appear at different nesting levels but not twice in one array."
            )
        }
    }
    return sorted
}

// ============================================================================
// Delegate tree traversal
// ============================================================================

/**
 * Maximum delegate tree traversal depth.
 *
 * Matches the XDR decode depth cap so that a tree the decoder accepts can
 * always be traversed. A traversal that would exceed this depth throws rather
 * than overflowing the stack.
 */
internal const val DELEGATE_TRAVERSAL_CAP = 128

/**
 * Traverses the delegate tree depth-first and returns all nodes whose XDR
 * address bytes equal [targetBytes].
 *
 * Used to check whether a [forAddress] target exists anywhere in the tree
 * before committing to write-back via [mapMatchingDelegates].
 *
 * @param depth current recursion depth (caller starts at 0)
 * @throws IllegalArgumentException if depth exceeds [DELEGATE_TRAVERSAL_CAP]
 */
internal fun findDelegateNodes(
    delegates: List<SorobanDelegateSignatureXdr>,
    targetBytes: ByteArray,
    depth: Int = 0
): List<SorobanDelegateSignatureXdr> {
    if (depth > DELEGATE_TRAVERSAL_CAP) {
        throw IllegalArgumentException(
            "Delegate tree traversal depth $depth exceeds cap $DELEGATE_TRAVERSAL_CAP"
        )
    }
    val result = mutableListOf<SorobanDelegateSignatureXdr>()
    for (node in delegates) {
        if (node.address.toXdrBytes().contentEquals(targetBytes)) {
            result.add(node)
        }
        result.addAll(findDelegateNodes(node.nestedDelegates, targetBytes, depth + 1))
    }
    return result
}

/**
 * Applies [transform] to every [SorobanDelegateSignatureXdr] in this list whose
 * XDR-encoded address matches [targetBytes], recursing into nestedDelegates.
 *
 * Returns a new list with the matching nodes replaced; nodes that do not match
 * are returned unchanged. The list structure (ordering, non-matching nodes) is
 * fully preserved.
 *
 * @param depth current recursion depth (caller starts at 0)
 * @throws IllegalArgumentException if depth exceeds [DELEGATE_TRAVERSAL_CAP]
 */
internal fun List<SorobanDelegateSignatureXdr>.mapMatchingDelegates(
    targetBytes: ByteArray,
    depth: Int = 0,
    transform: (SorobanDelegateSignatureXdr) -> SorobanDelegateSignatureXdr
): List<SorobanDelegateSignatureXdr> {
    if (depth > DELEGATE_TRAVERSAL_CAP) {
        throw IllegalArgumentException(
            "Delegate tree traversal depth $depth exceeds cap $DELEGATE_TRAVERSAL_CAP"
        )
    }
    return map { node ->
        val updatedNested = node.nestedDelegates.mapMatchingDelegates(targetBytes, depth + 1, transform)
        val nodeAddressBytes = node.address.toXdrBytes()
        val updated = if (nodeAddressBytes.contentEquals(targetBytes)) {
            transform(node.copy(nestedDelegates = updatedNested))
        } else {
            node.copy(nestedDelegates = updatedNested)
        }
        updated
    }
}

/**
 * Converts a [DelegateDescriptor] to a [SorobanDelegateSignatureXdr] with
 * sorted, duplicate-checked nested delegate arrays, at [depth] in the tree.
 *
 * @throws IllegalArgumentException if depth exceeds [DELEGATE_TRAVERSAL_CAP],
 *   if the address is invalid or muxed, or if duplicates are detected.
 */
internal fun DelegateDescriptor.toXdr(depth: Int = 0): SorobanDelegateSignatureXdr {
    if (depth > DELEGATE_TRAVERSAL_CAP) {
        throw IllegalArgumentException(
            "Delegate descriptor depth $depth exceeds cap $DELEGATE_TRAVERSAL_CAP"
        )
    }
    val addr = Address(address)
    require(addr.addressType != Address.AddressType.MUXED_ACCOUNT) {
        "Muxed (M...) addresses are not valid Soroban delegate addresses: $address"
    }
    val xdrAddress = addr.toSCAddress()
    val nestedXdr = sortAndValidateDelegates(
        nestedDelegates.map { it.toXdr(depth + 1) }
    )
    return SorobanDelegateSignatureXdr(
        address = xdrAddress,
        signature = signature,
        nestedDelegates = nestedXdr
    )
}
