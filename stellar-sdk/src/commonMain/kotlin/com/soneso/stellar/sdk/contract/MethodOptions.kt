package com.soneso.stellar.sdk.contract

/**
 * Configuration options for contract method invocation.
 *
 * @property fee The fee to pay for the transaction in stroops (default: 100)
 * @property timeoutInSeconds Transaction validity timeout in seconds (default: 300)
 * @property simulate Whether to automatically simulate the transaction (default: true)
 * @property restore Whether to automatically restore contract state if needed (default: false)
 */
data class MethodOptions(
    val fee: Long = 100,
    val timeoutInSeconds: Long = 300,
    val simulate: Boolean = true,
    val restore: Boolean = false
)
