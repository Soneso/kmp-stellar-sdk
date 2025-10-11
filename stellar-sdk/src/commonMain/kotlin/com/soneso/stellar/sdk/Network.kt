package com.soneso.stellar.sdk

/**
 * Network class is used to specify which Stellar network you want to use.
 * Each network has a [networkPassphrase] which is hashed to every transaction id.
 *
 * @property networkPassphrase The network passphrase
 * @see <a href="https://developers.stellar.org/docs/learn/fundamentals/networks">Stellar Networks</a>
 */
data class Network(val networkPassphrase: String) {

    init {
        require(networkPassphrase.isNotBlank()) { "Network passphrase cannot be blank" }
    }

    /**
     * Returns network id (SHA-256 hashed [networkPassphrase]).
     *
     * @return The 32-byte network ID
     */
    fun networkId(): ByteArray {
        return Util.hash(networkPassphrase.encodeToByteArray())
    }

    override fun toString(): String = networkPassphrase

    companion object {
        /**
         * Stellar public network ("Public Global Stellar Network ; September 2015")
         */
        val PUBLIC = Network("Public Global Stellar Network ; September 2015")

        /**
         * Stellar test network ("Test SDF Network ; September 2015")
         */
        val TESTNET = Network("Test SDF Network ; September 2015")

        /**
         * Stellar future network ("Test SDF Future Network ; October 2022")
         */
        val FUTURENET = Network("Test SDF Future Network ; October 2022")

        /**
         * Standalone network ("Standalone Network ; February 2017")
         */
        val STANDALONE = Network("Standalone Network ; February 2017")

        /**
         * Local sandbox network ("Local Sandbox Stellar Network ; September 2022")
         */
        val SANDBOX = Network("Local Sandbox Stellar Network ; September 2022")
    }
}
