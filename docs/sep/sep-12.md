# SEP-12: KYC API

**[SEP-0012 Compatibility Matrix](../../compatibility/sep/SEP-0012_COMPATIBILITY_MATRIX.md)** - Full implementation coverage details

SEP-12 defines a standard API for collecting Know Your Customer (KYC) information for regulatory compliance. Anchors, exchanges, and regulated entities use this protocol to verify customer identity before processing deposits, withdrawals, and payments.

**Use Cases**:
- Submit personal identification for anchor services
- Check KYC verification status
- Upload identity documents (passport, ID card, proof of address)
- Verify email and phone with confirmation codes
- Receive status updates via webhooks
- Delete customer data for GDPR compliance

## Quick Start

```kotlin
import com.soneso.stellar.sdk.KeyPair
import com.soneso.stellar.sdk.Network
import com.soneso.stellar.sdk.sep.sep09.*
import com.soneso.stellar.sdk.sep.sep10.WebAuth
import com.soneso.stellar.sdk.sep.sep12.*
import kotlinx.datetime.LocalDate

// 1. Authenticate with SEP-10 to get JWT token
val webAuth = WebAuth.fromDomain("testanchor.stellar.org", Network.TESTNET)
val userKeyPair = KeyPair.fromSecretSeed("SCZANGBA5YHTNYVVV3C7CAZMTQDBJHJG6C34STKSMREMR3EOFO3SQ7LP")
val authToken = webAuth.jwtToken(
    clientAccountId = userKeyPair.getAccountId(),
    signers = listOf(userKeyPair)
)

// 2. Initialize KYC service from domain
val kycService = KYCService.fromDomain("testanchor.stellar.org")

// 3. Check what information is required
val getRequest = GetCustomerInfoRequest(jwt = authToken.token)
val response = kycService.getCustomerInfo(getRequest)

when (response.status) {
    CustomerStatus.NEEDS_INFO -> {
        // 4. Submit required information
        val putRequest = PutCustomerInfoRequest(
            jwt = authToken.token,
            kycFields = StandardKYCFields(
                naturalPersonKYCFields = NaturalPersonKYCFields(
                    firstName = "John",
                    lastName = "Doe",
                    emailAddress = "john@example.com",
                    birthDate = LocalDate(1990, 1, 15)
                )
            )
        )
        val putResponse = kycService.putCustomerInfo(putRequest)
        println("Customer ID: ${putResponse.id}")
    }
    CustomerStatus.ACCEPTED -> println("Customer verified")
    CustomerStatus.PROCESSING -> println("Verification in progress")
    CustomerStatus.REJECTED -> println("Rejected: ${response.message}")
}
```

## Service Discovery

Initialize `KYCService` from a domain's stellar.toml file:

```kotlin
// Automatic discovery from stellar.toml
val kycService = KYCService.fromDomain("testanchor.stellar.org")

// Direct initialization with known endpoint
val kycService = KYCService("https://testanchor.stellar.org/kyc")

// With custom HTTP headers (for API keys, etc.)
val kycService = KYCService.fromDomain(
    domain = "testanchor.stellar.org",
    httpRequestHeaders = mapOf("X-API-Key" to "your-api-key")
)
```

## Checking Customer Status

Use `getCustomerInfo` to check KYC requirements and current status:

```kotlin
// Check requirements for a new customer
val request = GetCustomerInfoRequest(
    jwt = authToken.token,
    type = "sep31-sender"  // Optional: specific KYC type
)
val response = kycService.getCustomerInfo(request)

// Check existing customer by ID
val request = GetCustomerInfoRequest(
    jwt = authToken.token,
    id = customerId
)
val response = kycService.getCustomerInfo(request)

// With transaction context (may require additional fields based on amount)
val request = GetCustomerInfoRequest(
    jwt = authToken.token,
    id = customerId,
    transactionId = "tx-123",
    lang = "en"  // Language for field descriptions
)
val response = kycService.getCustomerInfo(request)
```

### Processing the Response

```kotlin
val response = kycService.getCustomerInfo(request)

when (response.status) {
    CustomerStatus.NEEDS_INFO -> {
        // Display required fields to user
        response.fields?.forEach { (fieldName, fieldInfo) ->
            println("Field: $fieldName")
            println("  Type: ${fieldInfo.type}")
            println("  Description: ${fieldInfo.description}")
            println("  Required: ${fieldInfo.optional != true}")
            fieldInfo.choices?.let { println("  Options: ${it.joinToString(", ")}") }
        }
    }
    CustomerStatus.PROCESSING -> {
        println("Verification in progress")
        println(response.message)
    }
    CustomerStatus.ACCEPTED -> {
        println("Customer verified, ID: ${response.id}")
    }
    CustomerStatus.REJECTED -> {
        println("Rejected: ${response.message}")
    }
}
```

### Checking Provided Field Status

```kotlin
// Check status of previously submitted fields
response.providedFields?.forEach { (fieldName, fieldInfo) ->
    when (fieldInfo.status) {
        FieldStatus.ACCEPTED -> println("$fieldName: Verified")
        FieldStatus.PROCESSING -> println("$fieldName: Under review")
        FieldStatus.REJECTED -> println("$fieldName: Rejected - ${fieldInfo.error}")
        FieldStatus.VERIFICATION_REQUIRED -> println("$fieldName: Needs verification code")
        null -> println("$fieldName: Status unknown")
    }
}
```

## Submitting Customer Information

Use `putCustomerInfo` to submit customer data. The request supports SEP-9 standard fields, custom fields, document uploads, and verification codes.

### Basic Information

```kotlin
val request = PutCustomerInfoRequest(
    jwt = authToken.token,
    kycFields = StandardKYCFields(
        naturalPersonKYCFields = NaturalPersonKYCFields(
            firstName = "John",
            lastName = "Doe",
            emailAddress = "john@example.com",
            mobileNumber = "+14155551234",
            birthDate = LocalDate(1990, 1, 15),
            address = "123 Main Street",
            city = "San Francisco",
            stateOrProvince = "CA",
            postalCode = "94102",
            addressCountryCode = "USA"
        )
    )
)

val response = kycService.putCustomerInfo(request)
val customerId = response.id  // Save for future requests
```

### Organization KYC

```kotlin
val request = PutCustomerInfoRequest(
    jwt = authToken.token,
    type = "sep31-receiver",
    kycFields = StandardKYCFields(
        organizationKYCFields = OrganizationKYCFields(
            name = "Acme Corporation",
            registrationNumber = "123456789",
            VATNumber = "VAT123456",
            directorName = "Jane Smith",
            email = "contact@acme.com",
            phone = "+14155559999",
            registeredAddress = "100 Business Park",
            city = "San Francisco",
            addressCountryCode = "USA"
        )
    )
)

val response = kycService.putCustomerInfo(request)
```

### Updating Existing Customer

```kotlin
// Use ID from previous response to update information
val request = PutCustomerInfoRequest(
    jwt = authToken.token,
    id = customerId,
    kycFields = StandardKYCFields(
        naturalPersonKYCFields = NaturalPersonKYCFields(
            emailAddress = "newemail@example.com"
        )
    )
)

val response = kycService.putCustomerInfo(request)
```

### Custom Fields

For anchor-specific fields not defined in SEP-9:

```kotlin
val request = PutCustomerInfoRequest(
    jwt = authToken.token,
    customFields = mapOf(
        "occupation_code" to "251",
        "employer_name" to "Tech Corp"
    )
)

val response = kycService.putCustomerInfo(request)
```

## Document Uploads

### Direct Upload with Customer Data

Binary fields (documents, photos) can be included directly in `PutCustomerInfoRequest`:

```kotlin
val request = PutCustomerInfoRequest(
    jwt = authToken.token,
    kycFields = StandardKYCFields(
        naturalPersonKYCFields = NaturalPersonKYCFields(
            firstName = "John",
            lastName = "Doe",
            photoIdFront = passportFrontBytes,  // ByteArray
            photoIdBack = passportBackBytes,    // ByteArray
            photoProofResidence = utilityBillBytes
        )
    )
)

val response = kycService.putCustomerInfo(request)
```

### Two-Step Upload (Recommended for Large Files)

Upload files separately, then reference them:

```kotlin
// Step 1: Upload files
val idFrontFile = kycService.postCustomerFile(idFrontBytes, authToken.token)
val idBackFile = kycService.postCustomerFile(idBackBytes, authToken.token)

println("Uploaded files:")
println("  Front: ${idFrontFile.fileId} (${idFrontFile.size} bytes)")
println("  Back: ${idBackFile.fileId} (${idBackFile.size} bytes)")

// Step 2: Reference files in customer update
val request = PutCustomerInfoRequest(
    jwt = authToken.token,
    id = customerId,
    fileReferences = mapOf(
        "photo_id_front_file_id" to idFrontFile.fileId,
        "photo_id_back_file_id" to idBackFile.fileId
    )
)

val response = kycService.putCustomerInfo(request)
```

### Query Uploaded Files

```kotlin
// Get all files for a customer
val filesResponse = kycService.getCustomerFiles(
    jwt = authToken.token,
    customerId = customerId
)

filesResponse.files.forEach { file ->
    println("File ${file.fileId}:")
    println("  Type: ${file.contentType}")
    println("  Size: ${file.size} bytes")
    file.expiresAt?.let { println("  Expires: $it") }
}

// Get specific file info
val fileInfo = kycService.getCustomerFiles(
    jwt = authToken.token,
    fileId = fileId
)
```

## Field Verification

When a field requires verification (email, phone), the anchor sends a code that must be submitted:

```kotlin
// Check if verification is required
val response = kycService.getCustomerInfo(GetCustomerInfoRequest(jwt = authToken.token, id = customerId))

val emailField = response.providedFields?.get("email_address")
if (emailField?.status == FieldStatus.VERIFICATION_REQUIRED) {
    println("Enter the verification code sent to your email")
}

// Submit verification code
val verifyRequest = PutCustomerInfoRequest(
    jwt = authToken.token,
    id = customerId,
    verificationFields = mapOf(
        "email_address_verification" to "123456"
    )
)

val verifyResponse = kycService.putCustomerInfo(verifyRequest)
```

Multiple verifications can be submitted at once:

```kotlin
val request = PutCustomerInfoRequest(
    jwt = authToken.token,
    id = customerId,
    verificationFields = mapOf(
        "email_address_verification" to "123456",
        "mobile_number_verification" to "654321"
    )
)

val response = kycService.putCustomerInfo(request)
```

## Callback Registration

Register a webhook URL to receive KYC status updates:

```kotlin
val request = PutCustomerCallbackRequest(
    jwt = authToken.token,
    url = "https://myapp.com/webhooks/kyc-status",
    id = customerId
)

kycService.putCustomerCallback(request)
```

### Verifying Callback Signatures

Anchors sign callback requests with their SIGNING_KEY. Always verify signatures:

```kotlin
import com.soneso.stellar.sdk.sep.common.CallbackSignatureVerifier
import com.soneso.stellar.sdk.sep.sep01.StellarToml
import com.soneso.stellar.sdk.sep.sep12.GetCustomerInfoResponse
import kotlinx.serialization.json.Json

// In your webhook handler
suspend fun handleKYCCallback(
    signatureHeader: String?,            // value of "Signature" header
    xStellarSignatureHeader: String?,    // value of legacy "X-Stellar-Signature" header
    requestBody: String,
    registeredCallbackUrl: String,        // URL you registered with the anchor
) {
    // Get anchor's signing key from stellar.toml (fetch once, cache for the connection lifetime).
    val stellarToml = StellarToml.fromDomain("testanchor.stellar.org")
    val signingKey = stellarToml.generalInformation.signingKey
        ?: throw IllegalStateException("Anchor signing key not found")

    val verifier = CallbackSignatureVerifier(
        signingKey = signingKey,
        registeredCallbackUrl = registeredCallbackUrl,
        freshnessSeconds = 120,  // default; do not raise above 120 in production
    )

    val isValid = when (verifier.verify(signatureHeader, xStellarSignatureHeader, requestBody)) {
        CallbackSignatureVerifier.Result.Valid -> true
        else -> false
    }

    if (!isValid) {
        throw SecurityException("Invalid callback signature")
    }

    // Process the callback
    val json = Json { ignoreUnknownKeys = true }
    val update = json.decodeFromString<GetCustomerInfoResponse>(requestBody)
    println("Customer ${update.id} status: ${update.status}")
}
```

Signature header format: `t=<timestamp>, s=<base64_signature>`. The verifier handles header parsing, freshness, and signature verification; callers do not need to parse the header themselves.

> The previous `com.soneso.stellar.sdk.sep.sep12.CallbackSignatureVerifier` object is deprecated. It remains in the SDK as a thin shim over the new shared class for backwards compatibility and will be removed in a future minor release; see the project `CHANGELOG.md` for the exact removal target.

## Customer Deletion (GDPR)

Delete customer data for privacy compliance:

```kotlin
kycService.deleteCustomer(
    account = userAccountId,
    jwt = authToken.token
)

// With memo for shared accounts
kycService.deleteCustomer(
    account = sharedAccountId,
    memo = "user_12345",
    memoType = "id",
    jwt = authToken.token
)
```

## Error Handling

```kotlin
import com.soneso.stellar.sdk.sep.sep12.exceptions.*

try {
    val response = kycService.putCustomerInfo(request)
} catch (e: UnauthorizedException) {
    // JWT token invalid or expired - re-authenticate via SEP-10
    println("Authentication failed, please log in again")
} catch (e: InvalidFieldException) {
    // Field validation failed
    println("Invalid field: ${e.fieldName}")
    println("Error: ${e.fieldError}")
} catch (e: FileTooLargeException) {
    // Uploaded file exceeds size limit
    println("File too large: ${e.fileSize} bytes")
} catch (e: CustomerNotFoundException) {
    // Customer not found (404) - need to register
    println("Customer not found: ${e.accountId}")
} catch (e: CustomerAlreadyExistsException) {
    // Customer already exists (409) - use existing ID
    println("Customer exists with ID: ${e.existingCustomerId}")
} catch (e: KYCException) {
    // Other KYC errors
    println("KYC error: ${e.message}")
}
```

## Customer Status Values

| Status | Description | Action Required |
|--------|-------------|-----------------|
| `NEEDS_INFO` | More information required | Check `fields` property and submit required data |
| `PROCESSING` | Information under review | Wait for status update (use callbacks) |
| `ACCEPTED` | Customer verified | Proceed with anchor services |
| `REJECTED` | Customer rejected | Check `message` for reason |

## Field Status Values

| Status | Description | Action Required |
|--------|-------------|-----------------|
| `ACCEPTED` | Field verified | None |
| `PROCESSING` | Field under review | Wait |
| `REJECTED` | Field rejected | Correct and resubmit (check `error`) |
| `VERIFICATION_REQUIRED` | Needs verification code | Submit code via `verificationFields` |

## API Reference

**Main Class**:
- `KYCService` - Methods: fromDomain, getCustomerInfo, putCustomerInfo, deleteCustomer, putCustomerCallback, postCustomerFile, getCustomerFiles

**Request Classes**:
- `GetCustomerInfoRequest` - Query customer status and requirements
- `PutCustomerInfoRequest` - Submit customer data, documents, verification codes
- `PutCustomerCallbackRequest` - Register webhook URL

**Response Classes**:
- `GetCustomerInfoResponse` - Customer status, required fields, provided fields
- `PutCustomerInfoResponse` - Customer ID
- `CustomerFileResponse` - Uploaded file metadata
- `GetCustomerFilesResponse` - List of uploaded files

**Field Types**:
- `GetCustomerInfoField` - Required field definition (type, description, choices)
- `GetCustomerInfoProvidedField` - Provided field with verification status

**Enums**:
- `CustomerStatus` - ACCEPTED, PROCESSING, NEEDS_INFO, REJECTED
- `FieldStatus` - ACCEPTED, PROCESSING, REJECTED, VERIFICATION_REQUIRED

**Utilities**:
- `com.soneso.stellar.sdk.sep.common.CallbackSignatureVerifier` - Verify webhook signatures (shared across SEP-12 and SEP-31)
- `com.soneso.stellar.sdk.sep.sep12.CallbackSignatureVerifier` - Deprecated shim retained for backwards compatibility; delegates to the shared class

**Exceptions**:
- `KYCException` - Base class for all SEP-12 errors
- `UnauthorizedException` - Authentication failed (401)
- `InvalidFieldException` - Field validation failed (400)
- `CustomerNotFoundException` - Customer not found (404)
- `CustomerAlreadyExistsException` - Duplicate customer (409)
- `FileTooLargeException` - File exceeds size limit (413)

**Related SEPs**:
- [SEP-9: Standard KYC Fields](sep-09.md) - Field definitions used in PUT requests
- [SEP-10: Web Authentication](sep-10.md) - JWT token authentication

**Specification**: [SEP-12: KYC API](https://github.com/stellar/stellar-protocol/blob/master/ecosystem/sep-0012.md)

**Implementation**: `com.soneso.stellar.sdk.sep.sep12`

**Last Updated**: 2025-12-08
