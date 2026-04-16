// Smart Account Demo - root build file
// Subprojects define their own plugins and dependencies.

subprojects {
    repositories {
        // Required by Reown (WalletConnect v2) transitive dependencies:
        // com.github.komputing.kethereum, com.github.multiformats, com.walletconnect.Scarlet
        maven("https://jitpack.io")
    }
}
