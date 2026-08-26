# SEP-1: Stellar TOML

**[SEP-0001 Compatibility Matrix](../../compatibility/sep/SEP-0001_COMPATIBILITY_MATRIX.md)** - Full implementation coverage details

Stellar TOML allows organizations to publish information about their Stellar integration at `.well-known/stellar.toml`. Use this to discover service endpoints, verify accounts, and find supported currencies.

**Use Cases**:
- Discover what SEP services an anchor supports
- Verify domain ownership of Stellar accounts
- Find supported currencies and their properties
- Locate service endpoints for authentication, transfers, and quotes

Code examples assume a `suspend` calling context and these imports:

```kotlin
import com.soneso.stellar.sdk.*
import com.soneso.stellar.sdk.sep.sep01.*
```

## Fetching stellar.toml from a Domain

```kotlin
// Fetch stellar.toml from a domain
val stellarToml = StellarToml.fromDomain("example.com")

// Access service endpoints
val webAuthEndpoint = stellarToml.generalInformation.webAuthEndpoint
val transferServer = stellarToml.generalInformation.transferServerSep24

// Access organization information
stellarToml.documentation?.let { doc ->
    println("Organization: ${doc.orgName}")
    println("Support: ${doc.orgSupportEmail}")
}
```

## Discovering Service Capabilities

```kotlin
// Check which SEP services an anchor supports
val stellarToml = StellarToml.fromDomain("example.com")

val supportsSEP6 = stellarToml.generalInformation.transferServer != null
val supportsSEP10 = stellarToml.generalInformation.webAuthEndpoint != null
val supportsSEP24 = stellarToml.generalInformation.transferServerSep24 != null
val supportsSEP38 = stellarToml.generalInformation.anchorQuoteServer != null
```

## Finding Supported Currencies

```kotlin
// List all currencies supported by an anchor
val stellarToml = StellarToml.fromDomain("example.com")

stellarToml.currencies?.forEach { currency ->
    println("${currency.code}: ${currency.desc}")
    println("Issuer: ${currency.issuer}")
    println("Decimals: ${currency.displayDecimals}")
}

// Find a specific currency
val usd = stellarToml.currencies?.find { it.code == "USD" }
```

## Loading External Currency Information

```kotlin
// Some currencies reference external TOML files for detailed information
val stellarToml = StellarToml.fromDomain("example.com")

stellarToml.currencies?.forEach { currency ->
    currency.toml?.let { url ->
        // Load complete currency information from external file
        val fullCurrency = StellarToml.currencyFromUrl(url)
        println("${fullCurrency.code}: ${fullCurrency.desc}")
        println("Anchored: ${fullCurrency.isAssetAnchored}")
    }
}
```

## Verifying Account Ownership

```kotlin
// Verify an account belongs to a domain
val stellarToml = StellarToml.fromDomain("example.com")
val accountToVerify = "GCVSEBHB6CTMEHUHIUY4DDFMWQ7PJTHFZGOK2JUD5EG2ARNVS6S22E3K"

val isVerified = stellarToml.generalInformation.accounts.contains(accountToVerify)
if (isVerified) {
    println("Account is verified for example.com")
}
```

## Discovering Validators

```kotlin
// Find validators operated by an organization
val stellarToml = StellarToml.fromDomain("example.com")

stellarToml.validators?.forEach { validator ->
    println("${validator.displayName}: ${validator.publicKey}")
    println("Host: ${validator.host}")
    println("History: ${validator.history}")
}
```

## Parsing from String

```kotlin
// Parse stellar.toml content from a string (useful for testing or caching)
val tomlContent = """
    VERSION = "2.0.0"
    NETWORK_PASSPHRASE = "Public Global Stellar Network ; September 2015"
    WEB_AUTH_ENDPOINT = "https://example.com/auth"

    [DOCUMENTATION]
    ORG_NAME = "Example Organization"
""".trimIndent()

val stellarToml = StellarToml.parse(tomlContent)
```

## API Reference

**Main Methods**:
- `StellarToml.fromDomain(domain: String): StellarToml` - Fetch stellar.toml from domain
- `StellarToml.parse(toml: String): StellarToml` - Parse stellar.toml from string
- `StellarToml.currencyFromUrl(url: String): Currency` - Load external currency TOML

**Data Classes**:
- `StellarToml` - Main container for stellar.toml data
- `GeneralInformation` - Service endpoints and network configuration (16 fields)
- `Documentation` - Organization information (17 fields)
- `PointOfContact` - Contact information for principals (8 fields)
- `Currency` - Currency/asset information (25 fields)
- `Validator` - Validator node information (5 fields)

**Specification**: [SEP-1: stellar.toml](https://github.com/stellar/stellar-protocol/blob/master/ecosystem/sep-0001.md)

**Implementation**: `com.soneso.stellar.sdk.sep.sep01`
