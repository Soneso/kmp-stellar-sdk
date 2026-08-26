# SEP-12: KYC API

**Purpose:** Submit and manage customer KYC information for Know Your Customer compliance with anchors.
**Prerequisites:** Requires JWT from SEP-10 (see [sep-10.md](sep-10.md)); for contract accounts requires SEP-45.
**Package:** `com.soneso.stellar.sdk.sep.sep12`
**KYC Fields Package:** `com.soneso.stellar.sdk.sep.sep09`
**Standard KYC Fields:** See [sep-09.md](sep-09.md) for all field classes, properties, constants, and prefix behavior

Code examples assume a `suspend` calling context and these imports:

```kotlin
import com.soneso.stellar.sdk.*
import com.soneso.stellar.sdk.sep.sep12.*
import com.soneso.stellar.sdk.sep.sep12.exceptions.*
import com.soneso.stellar.sdk.sep.sep09.*
import kotlinx.datetime.LocalDate
```

## Table of Contents

- [Service initialization](#service-initialization)
- [Get customer info](#get-customer-info)
- [Put customer info](#put-customer-info)
  - [Natural person fields](#natural-person-fields)
  - [Organization fields](#organization-fields)
  - [Financial account fields](#financial-account-fields)
  - [Card fields](#card-fields)
  - [File uploads (binary fields)](#file-uploads-binary-fields)
  - [Custom fields and files](#custom-fields-and-files)
- [Put customer verification (deprecated)](#put-customer-verification-deprecated)
- [Put customer callback](#put-customer-callback)
- [Post customer file](#post-customer-file)
- [Get customer files](#get-customer-files)
- [Delete customer](#delete-customer)
- [Error handling](#error-handling)
- [Response reference](#response-reference)
- [Common pitfalls](#common-pitfalls)

---

## Service initialization

### From domain (recommended)

`KYCService.fromDomain()` is a `suspend` function that fetches the anchor's `stellar.toml`, reads `KYC_SERVER` (falls back to `TRANSFER_SERVER`), and returns a configured `KYCService`. Throws `IllegalStateException` if neither field is found.

```kotlin
// myClient: from the previous steps of this flow
import com.soneso.stellar.sdk.sep.sep12.KYCService


// With custom HTTP client and headers
val kycService = KYCService.fromDomain(
    domain = "testanchor.stellar.org",
    httpClient = myClient,
    httpRequestHeaders = mapOf("User-Agent" to "MyWallet/1.0")
)
```

Signature:
```kotlin
suspend fun fromDomain(
    domain: String,
    httpClient: HttpClient? = null,
    httpRequestHeaders: Map<String, String>? = null
): KYCService
```

### Manual construction

Use when you already know the KYC endpoint URL.

```kotlin
import com.soneso.stellar.sdk.sep.sep12.KYCService

val kycService = KYCService("https://api.anchor.com/kyc")

// With optional custom client and headers
val myClient = io.ktor.client.HttpClient()
val kycServiceCustom = KYCService(
    serviceAddress = "https://api.anchor.com/kyc",
    httpClient = myClient,
    httpRequestHeaders = mapOf("X-Custom" to "value")
)
```

Constructor signature:
```kotlin
class KYCService(
    serviceAddress: String,
    httpClient: HttpClient? = null,
    httpRequestHeaders: Map<String, String>? = null
)
```

---

## Get customer info

Retrieve a customer's current verification status and the fields the anchor needs. All request classes use constructor parameters (Kotlin data classes), not setter methods.

```kotlin
import com.soneso.stellar.sdk.sep.sep12.*
val jwtToken = "eyJhbGciOiJFUzI1NiJ9..." // JWT from SEP-10 authentication

val kycService = KYCService.fromDomain("testanchor.stellar.org")

val request = GetCustomerInfoRequest(
    jwt = jwtToken          // required: JWT from SEP-10 or SEP-45
    // Optional identification parameters:
    // id = customerId,              // anchor-assigned customer ID from a previous PUT
    // account = "GDAT5HWTGIU4TSSZ4752OUC4SABDLTLZFRPZUJ3D6LKBNEPA7V2CIG54",          // Stellar account (deprecated, inferred from JWT sub)
    // memo = "12345",               // integer memo for shared/omnibus accounts
    // memoType = "id",              // deprecated; memos should always be type id
    // type = "sep31-sender",        // e.g. sep6-deposit, sep31-sender, sep31-receiver
    // transactionId = "tx_abc",     // link to a specific transaction
    // lang = "en"                   // ISO 639-1; defaults to "en"
)

val response = kycService.getCustomerInfo(request)

// Customer status: CustomerStatus enum — ACCEPTED, PROCESSING, NEEDS_INFO, or REJECTED
// WRONG: response.status is a String — compare with string literals
// CORRECT: response.status is a CustomerStatus enum — compare with enum values
when (response.status) {
    CustomerStatus.ACCEPTED -> println("Verified")
    CustomerStatus.PROCESSING -> println("Under review: ${response.message}")
    CustomerStatus.NEEDS_INFO -> {
        // Fields the anchor still needs (null unless status is NEEDS_INFO)
        response.fields?.forEach { (fieldName, field) ->
            // field.type        — String: "string", "binary", "number", or "date"
            // field.description — String: human-readable description
            // field.optional    — Boolean?: null/false means required; true means optional
            // field.choices     — List<String>?: valid values (null when unconstrained)
            val required = if (field.optional == true) "optional" else "required"
            println("$fieldName ($required): ${field.description}")
            if (!field.choices.isNullOrEmpty()) {
                println("  Choices: ${field.choices}")
            }
        }
    }
    CustomerStatus.REJECTED -> println("Rejected: ${response.message}")
}

// Fields already provided and their status
response.providedFields?.forEach { (fieldName, field) ->
    // field.status — FieldStatus?: ACCEPTED, PROCESSING, REJECTED, or VERIFICATION_REQUIRED
    // field.error  — String?: set when status is REJECTED
    when (field.status) {
        FieldStatus.ACCEPTED -> println("$fieldName: verified")
        FieldStatus.PROCESSING -> println("$fieldName: under review")
        FieldStatus.REJECTED -> println("$fieldName: rejected — ${field.error}")
        FieldStatus.VERIFICATION_REQUIRED -> println("$fieldName: code required")
        null -> println("$fieldName: status unknown")
    }
}

println("Customer ID: ${response.id}")     // String? (null if no record yet)
```

---

## Put customer info

Submit or update customer data. Returns a `PutCustomerInfoResponse` with `id` -- save this for future requests.

### Natural person fields

All KYC field classes use constructor parameters. Date fields use `kotlinx.datetime.LocalDate`, not strings.

```kotlin
import com.soneso.stellar.sdk.sep.sep09.*
import com.soneso.stellar.sdk.sep.sep12.*
import kotlinx.datetime.LocalDate
val jwtToken = "eyJhbGciOiJFUzI1NiJ9..." // JWT from SEP-10 authentication

val kycService = KYCService.fromDomain("testanchor.stellar.org")

val personFields = NaturalPersonKYCFields(
    // Name
    firstName = "Jane",
    lastName = "Doe",
    additionalName = "Marie",              // middle name

    // Address
    address = "123 Main St, Apt 4B",
    city = "San Francisco",
    stateOrProvince = "CA",
    postalCode = "94102",
    addressCountryCode = "USA",            // ISO 3166-1 alpha-3

    // Contact
    mobileNumber = "+14155551234",         // E.164 format
    mobileNumberFormat = "E.164",          // optional
    emailAddress = "jane@example.com",
    languageCode = "en",                   // ISO 639-1

    // Birth — LocalDate objects, NOT strings
    birthDate = LocalDate(1990, 5, 15),    // serialized as "1990-05-15"
    birthPlace = "New York, NY",
    birthCountryCode = "USA",              // ISO 3166-1 alpha-3

    // Tax
    taxId = "123-45-6789",
    taxIdName = "SSN",

    // Employment — occupation is the 3-char ISCO-08 code (e.g., "111" for legislators)
    occupation = "111",
    employerName = "Acme Corp",
    employerAddress = "456 Business Ave",

    // ID document — date fields are LocalDate objects, NOT strings
    idType = "passport",                           // passport, drivers_license, id_card
    idNumber = "AB123456",
    idCountryCode = "USA",
    idIssueDate = LocalDate(2020, 1, 15),          // serialized as "2020-01-15"
    idExpirationDate = LocalDate(2030, 1, 15),     // serialized as "2030-01-15"

    // Other
    sex = "female",                        // male, female, or other
    ipAddress = "192.168.1.1",
    referralId = "REF123"
)

val kycFields = StandardKYCFields(
    naturalPersonKYCFields = personFields
)

val request = PutCustomerInfoRequest(
    jwt = jwtToken,
    kycFields = kycFields,
    type = "sep31-sender"   // optional
)

// To update an existing customer, set their ID:
// val request = PutCustomerInfoRequest(jwt = jwtToken, id = customerId, kycFields = kycFields)

val response = kycService.putCustomerInfo(request)
val customerId = response.id  // String — save for future requests
println("Customer ID: $customerId")
```

### Organization fields

All organization fields are automatically sent with the `organization.` prefix per SEP-9.

```kotlin
import com.soneso.stellar.sdk.sep.sep09.*
import com.soneso.stellar.sdk.sep.sep12.*
val jwtToken = "eyJhbGciOiJFUzI1NiJ9..." // JWT from SEP-10 authentication

val kycService = KYCService.fromDomain("testanchor.stellar.org")

val orgFields = OrganizationKYCFields(
    name = "Acme Corporation",              // organization.name
    VATNumber = "DE123456789",              // organization.VAT_number (VATNumber, not vatNumber)
    registrationNumber = "HRB 12345",       // organization.registration_number
    registrationDate = "2010-06-15",        // String (ISO 8601 date) — NOT a LocalDate
    registeredAddress = "456 Business Ave",
    city = "New York",
    stateOrProvince = "NY",
    postalCode = "10001",
    addressCountryCode = "USA",
    numberOfShareholders = 3,               // Int
    shareholderName = "John Smith",
    directorName = "Jane Doe",
    website = "https://acme.example.com",
    email = "contact@acme.example.com",
    phone = "+12125551234"                  // E.164 format
)

val kycFields = StandardKYCFields(
    organizationKYCFields = orgFields
)

val request = PutCustomerInfoRequest(
    jwt = jwtToken,
    kycFields = kycFields
)

val response = kycService.putCustomerInfo(request)
```

### Financial account fields

Attach financial account details to a natural person or organization.

```kotlin
import com.soneso.stellar.sdk.sep.sep09.*
import com.soneso.stellar.sdk.sep.sep12.*
val jwtToken = "eyJhbGciOiJFUzI1NiJ9..." // JWT from SEP-10 authentication
val kycService = KYCService.fromDomain("testanchor.stellar.org")

val financialFields = FinancialAccountKYCFields(
    // Traditional bank account
    bankName = "First National Bank",
    bankAccountType = "checking",                // checking or savings
    bankAccountNumber = "1234567890",
    bankNumber = "021000021",                    // routing number (US)
    bankBranchNumber = "001",
    bankPhoneNumber = "+18005551234",            // E.164

    // Transfer memo / reference
    externalTransferMemo = "WIRE-REF-12345",

    // Mexico CLABE
    clabeNumber = "032180000118359719",

    // Argentina CBU/CVU
    cbuNumber = "0110000000001234567890",
    cbuAlias = "mi.cuenta.arg",

    // Mobile money
    mobileMoneyNumber = "+254712345678",
    mobileMoneyProvider = "M-Pesa",

    // Crypto payout address
    cryptoAddress = "GDRXE2BQUC3AZNPVFSCEZ76NJ3WWL25FYFK6RGZGIEKWE4SOOHSUJUJ6"
)

// Attach to natural person
val personFields = NaturalPersonKYCFields(
    firstName = "Jane",
    lastName = "Doe",
    financialAccountKYCFields = financialFields
)

// OR attach to organization (fields get "organization." prefix automatically)
val orgFields = OrganizationKYCFields(
    name = "Acme Corp",
    financialAccountKYCFields = financialFields
)

val kycFields = StandardKYCFields(
    naturalPersonKYCFields = personFields
)

val request = PutCustomerInfoRequest(
    jwt = jwtToken,
    kycFields = kycFields
)

val response = kycService.putCustomerInfo(request)
```

### Card fields

Attach payment card details to a natural person or organization. All card field keys have the `card.` prefix.

```kotlin
import com.soneso.stellar.sdk.sep.sep09.*
import com.soneso.stellar.sdk.sep.sep12.*
val jwtToken = "eyJhbGciOiJFUzI1NiJ9..." // JWT from SEP-10 authentication
val kycService = KYCService.fromDomain("testanchor.stellar.org")

val cardFields = CardKYCFields(
    number = "4111111111111111",
    expirationDate = "29-11",         // YY-MM format (November 2029)
    cvc = "123",
    holderName = "John Doe",
    network = "Visa",                 // card.network
    postalCode = "94102",             // card.postal_code
    countryCode = "US",               // ISO 3166-1 alpha-2 (alpha-2, not alpha-3)
    stateOrProvince = "CA",
    city = "San Francisco",
    address = "123 Main St"
    // OR use tokenized card instead of raw number:
    // token = "tok_visa_1234"
)

val personFields = NaturalPersonKYCFields(
    firstName = "John",
    cardKYCFields = cardFields
)

val kycFields = StandardKYCFields(
    naturalPersonKYCFields = personFields
)

val request = PutCustomerInfoRequest(
    jwt = jwtToken,
    kycFields = kycFields
)

val response = kycService.putCustomerInfo(request)
```

### File uploads (binary fields)

Binary fields (photos, documents) are stored as `ByteArray` and sent via `multipart/form-data` automatically. The `java.io.File` reading shown below is JVM/Android-only; on iOS/macOS/JS targets use the platform's file-reading API to produce the same `ByteArray`.

```kotlin
import com.soneso.stellar.sdk.sep.sep09.*
import com.soneso.stellar.sdk.sep.sep12.*
import kotlinx.datetime.LocalDate
import java.io.File
val customerId = "d1ce2f48-3ff1-495d-9b96-eb2e3b6d3aec" // id from a previous putCustomer call
val jwtToken = "eyJhbGciOiJFUzI1NiJ9..." // JWT from SEP-10 authentication

val kycService = KYCService.fromDomain("testanchor.stellar.org")

val idFrontBytes = File("id_front.jpg").readBytes()
val idBackBytes = File("id_back.jpg").readBytes()
val notaryBytes = File("notary.pdf").readBytes()
val utilityBillBytes = File("utility_bill.pdf").readBytes()
val bankStatementBytes = File("bank_statement.pdf").readBytes()
val livenessBytes = File("selfie.mp4").readBytes()

val personFields = NaturalPersonKYCFields(
    idType = "passport",
    idNumber = "AB123456",
    idCountryCode = "USA",
    idIssueDate = LocalDate(2020, 1, 15),
    idExpirationDate = LocalDate(2030, 1, 15),
    photoIdFront = idFrontBytes,                // ByteArray
    photoIdBack = idBackBytes,                  // ByteArray
    notaryApprovalOfPhotoId = notaryBytes,      // ByteArray
    photoProofResidence = utilityBillBytes,      // ByteArray
    proofOfIncome = bankStatementBytes,          // ByteArray
    proofOfLiveness = livenessBytes              // ByteArray
)

val kycFields = StandardKYCFields(
    naturalPersonKYCFields = personFields
)

val request = PutCustomerInfoRequest(
    jwt = jwtToken,
    id = customerId,    // update existing customer
    kycFields = kycFields
)

val response = kycService.putCustomerInfo(request)
```

Available binary properties on `NaturalPersonKYCFields`:

| Kotlin property | Field key sent |
|---|---|
| `photoIdFront` | `photo_id_front` |
| `photoIdBack` | `photo_id_back` |
| `notaryApprovalOfPhotoId` | `notary_approval_of_photo_id` |
| `photoProofResidence` | `photo_proof_residence` |
| `proofOfIncome` | `proof_of_income` |
| `proofOfLiveness` | `proof_of_liveness` |

Available binary properties on `OrganizationKYCFields`:

| Kotlin property | Field key sent |
|---|---|
| `photoIncorporationDoc` | `organization.photo_incorporation_doc` |
| `photoProofAddress` | `organization.photo_proof_address` |

### Custom fields and files

For anchor-specific fields not covered by SEP-9, use `customFields` (text) and `customFiles` (binary).

```kotlin
import com.soneso.stellar.sdk.sep.sep12.*
import java.io.File
val customerId = "d1ce2f48-3ff1-495d-9b96-eb2e3b6d3aec" // id from a previous putCustomer call
val jwtToken = "eyJhbGciOiJFUzI1NiJ9..." // JWT from SEP-10 authentication

val kycService = KYCService.fromDomain("testanchor.stellar.org")

val request = PutCustomerInfoRequest(
    jwt = jwtToken,
    id = customerId,
    // Custom text fields — Map<String, String>
    customFields = mapOf(
        "custom_field_1" to "custom value",
        "anchor_specific_id" to "ABC123"
    ),
    // Custom binary files — Map<String, ByteArray>
    customFiles = mapOf(
        "additional_document" to File("document.pdf").readBytes()
    )
)

val response = kycService.putCustomerInfo(request)
```

---

## Put customer verification (deprecated)

`PUT /customer/verification` is **deprecated in SEP-12 v1.12.0**. The preferred approach is to submit verification codes via `PUT /customer` using `verificationFields`.

The deprecated endpoint is supported for backwards compatibility:

```kotlin
import com.soneso.stellar.sdk.sep.sep12.*
val customerId = "d1ce2f48-3ff1-495d-9b96-eb2e3b6d3aec" // id from a previous putCustomer call
val jwtToken = "eyJhbGciOiJFUzI1NiJ9..." // JWT from SEP-10 authentication

val kycService = KYCService.fromDomain("testanchor.stellar.org")

// DEPRECATED: use putCustomerInfo with verificationFields instead
@Suppress("DEPRECATION")
val request = PutCustomerVerificationRequest(
    jwt = jwtToken,
    id = customerId,
    verificationFields = mapOf(
        "mobile_number_verification" to "2735021",
        "email_address_verification" to "ABC123"
    )
)

// Returns GetCustomerInfoResponse (same type as getCustomerInfo())
// NOT PutCustomerInfoResponse
@Suppress("DEPRECATION")
val response = kycService.putCustomerVerification(request)
println("Status: ${response.status}")  // CustomerStatus enum: ACCEPTED, NEEDS_INFO, etc.
```

Return type is `GetCustomerInfoResponse`, **not** `PutCustomerInfoResponse`.

**Preferred approach** (submit verification codes via `putCustomerInfo`):

```kotlin
val customerId = "d1ce2f48-3ff1-495d-9b96-eb2e3b6d3aec" // id from a previous putCustomer call
val jwtToken = "eyJhbGciOiJFUzI1NiJ9..." // JWT from SEP-10 authentication
val kycService = KYCService.fromDomain("testanchor.stellar.org")
val request = PutCustomerInfoRequest(
    jwt = jwtToken,
    id = customerId,
    verificationFields = mapOf(
        "email_address_verification" to "123456",
        "mobile_number_verification" to "654321"
    )
)

val response = kycService.putCustomerInfo(request)
```

---

## Put customer callback

Register a URL to receive POST notifications when a customer's status changes. The new URL replaces any previously registered callback.

```kotlin
import com.soneso.stellar.sdk.sep.sep12.*
import io.ktor.client.statement.*
val customerId = "d1ce2f48-3ff1-495d-9b96-eb2e3b6d3aec" // id from a previous putCustomer call
val kycService = KYCService.fromDomain("testanchor.stellar.org")
val jwtToken = "eyJhbGciOiJFUzI1NiJ9..." // JWT from SEP-10 authentication


val request = PutCustomerCallbackRequest(
    jwt = jwtToken,
    url = "https://myapp.com/kyc-callback",   // required
    id = customerId                            // preferred: use anchor-assigned ID
    // OR identify by account:
    // account = "GDAT5HWTGIU4TSSZ4752OUC4SABDLTLZFRPZUJ3D6LKBNEPA7V2CIG54",
    // memo = "12345"    // for shared accounts
)

// Returns HttpResponse directly — NOT GetCustomerInfoResponse
// WRONG: val response: GetCustomerInfoResponse = kycService.putCustomerCallback(request)
// CORRECT: returns Ktor HttpResponse — check status manually
val response: HttpResponse = kycService.putCustomerCallback(request)
println("HTTP ${response.status.value}")  // 200 on success
```

The anchor POSTs to your callback URL with the same JSON body as `GET /customer` responses. The payload is signed with `Signature` and `X-Stellar-Signature` headers using the anchor's `SIGNING_KEY`.

### Verifying callback signatures

When the anchor delivers customer-status updates to your registered callback URL, each `POST` carries a `Signature` (or legacy `X-Stellar-Signature`) header signed with the anchor's `SIGNING_KEY` from its stellar.toml. Use `com.soneso.stellar.sdk.sep.common.CallbackSignatureVerifier` to validate them. The class is shared between SEP-12 and SEP-31.

```kotlin
import com.soneso.stellar.sdk.sep.common.CallbackSignatureVerifier
import com.soneso.stellar.sdk.sep.sep01.StellarToml

// Resolve the anchor's SIGNING_KEY once, then cache the verifier per registered URL.
val toml = StellarToml.fromDomain("testanchor.stellar.org")
val signingKey = toml.generalInformation.signingKey
    ?: error("Anchor stellar.toml missing SIGNING_KEY")

val verifier = CallbackSignatureVerifier(
    signingKey = signingKey,
    registeredCallbackUrl = "https://myapp.com/webhooks/kyc-status",
    // freshnessSeconds defaults to 120 to match the spec recommendation.
    // Do not raise above 120 in production (max allowed is 600, intended only
    // for test-environment clock skew).
)

// In your webhook handler:
val result = verifier.verify(
    signatureHeader = request.header("Signature"),
    xStellarSignatureHeader = request.header("X-Stellar-Signature"),
    body = request.bodyAsText(),
)

when (result) {
    CallbackSignatureVerifier.Result.Valid -> {
        // Authentic; process the customer status update.
    }
    CallbackSignatureVerifier.Result.MissingHeader -> {
        // Neither header was present.
    }
    CallbackSignatureVerifier.Result.MalformedHeader -> {
        // One of: header did not match the expected `t=<digits>, s=<base64>` shape,
        // the base64 payload failed to decode, or the signature bytes were rejected
        // by the Ed25519 layer (e.g., wrong byte length).
    }
    is CallbackSignatureVerifier.Result.Stale -> {
        // Timestamp outside freshness window.
        // result.ageSeconds is signed: positive = past, negative = future-dated.
        // Both are rejected; the sign exists for logging only.
    }
    CallbackSignatureVerifier.Result.SignatureMismatch -> {
        // Header was well-formed but cryptographic verification failed.
    }
}
```

The verifier:
- Pins the canonical host from the registered callback URL (port stripped) so a forwarded `Host` header cannot redirect signature scope.
- Enforces HTTPS for the registered URL, with HTTP allowed only for loopback authorities (development).
- Applies a two-sided freshness window (`|now - signedTimestamp| <= freshnessSeconds`) to defend against future-dated forgery and replay equally.

Once verification passes, decode the body — the anchor posts the same JSON shape as `GET /customer` responses:

```kotlin
// requestBody: from the previous steps of this flow
import com.soneso.stellar.sdk.sep.sep12.GetCustomerInfoResponse
import kotlinx.serialization.json.Json

val json = Json { ignoreUnknownKeys = true }
val update = json.decodeFromString<GetCustomerInfoResponse>(requestBody)
println("Customer ${update.id} status: ${update.status}")
```

The deprecated `com.soneso.stellar.sdk.sep.sep12.CallbackSignatureVerifier` object remains available as a thin shim for backwards compatibility and is scheduled for removal; see the project `CHANGELOG.md`.

---

## Post customer file

Upload a file and receive a `fileId` to reference in subsequent `PUT /customer` requests. Useful when the anchor requires `application/json` bodies (which don't support binary data directly).

```kotlin
import com.soneso.stellar.sdk.sep.sep12.*
import java.io.File
val customerId = "d1ce2f48-3ff1-495d-9b96-eb2e3b6d3aec" // id from a previous putCustomer call
val jwtToken = "eyJhbGciOiJFUzI1NiJ9..." // JWT from SEP-10 authentication

val kycService = KYCService.fromDomain("testanchor.stellar.org")

// Upload the file — takes ByteArray and jwt String.
// File-reading is JVM/Android-only; on other platforms produce the ByteArray
// with the platform's file API.
val fileBytes = File("passport_front.jpg").readBytes()
val fileResponse = kycService.postCustomerFile(fileBytes, jwtToken)

// CustomerFileResponse has val properties (NOT getter methods)
println("File ID: ${fileResponse.fileId}")           // String
println("Content-Type: ${fileResponse.contentType}") // String
println("Size: ${fileResponse.size} bytes")          // Long
println("Customer ID: ${fileResponse.customerId}")   // String? (null if not yet linked)

if (fileResponse.expiresAt != null) {
    // String? ISO 8601 timestamp; file is discarded if not linked by this time
    println("Expires: ${fileResponse.expiresAt}")
}

// Reference the file in a customer PUT using fileReferences with _file_id suffix
val request = PutCustomerInfoRequest(
    jwt = jwtToken,
    id = customerId,
    fileReferences = mapOf(
        "photo_id_front_file_id" to fileResponse.fileId
    )
)

val response = kycService.putCustomerInfo(request)
```

Method signature:
```kotlin
suspend fun postCustomerFile(file: ByteArray, jwt: String): CustomerFileResponse
```

Note: The file data is sent as a multipart field named `"file"` via `multipart/form-data`.

---

## Get customer files

Retrieve information about uploaded files, either by file ID or customer ID.

```kotlin
import com.soneso.stellar.sdk.sep.sep12.*
val customerId = "d1ce2f48-3ff1-495d-9b96-eb2e3b6d3aec" // id from a previous putCustomer call
val jwtToken = "eyJhbGciOiJFUzI1NiJ9..." // JWT from SEP-10 authentication

val kycService = KYCService.fromDomain("testanchor.stellar.org")

// Get a specific file by ID (named parameter)
val response = kycService.getCustomerFiles(jwt = jwtToken, fileId = "file_abc123")

// Get all files for a customer (named parameter)
val response2 = kycService.getCustomerFiles(jwt = jwtToken, customerId = customerId)

// Get all files for the authenticated account (no filter)
val response3 = kycService.getCustomerFiles(jwt = jwtToken)

// response.files is List<CustomerFileResponse>
for (file in response.files) {
    println("${file.fileId}: ${file.contentType} (${file.size} bytes)")
    if (file.customerId != null) {
        println("  Linked to customer: ${file.customerId}")
    }
    if (file.expiresAt != null) {
        println("  Expires: ${file.expiresAt}")
    }
}
```

Method signature:
```kotlin
suspend fun getCustomerFiles(
    jwt: String,
    fileId: String? = null,
    customerId: String? = null
): GetCustomerFilesResponse
```

`GetCustomerFilesResponse` has one property: `val files: List<CustomerFileResponse>` (empty list when no files found, never null).

---

## Delete customer

Delete all personal data stored by the anchor for a given Stellar account. Used for GDPR compliance or account closure.

```kotlin
import com.soneso.stellar.sdk.sep.sep12.*
import io.ktor.client.statement.*
val accountId = "GDAT5HWTGIU4TSSZ4752OUC4SABDLTLZFRPZUJ3D6LKBNEPA7V2CIG54"
val jwtToken = "eyJhbGciOiJFUzI1NiJ9..." // JWT from SEP-10 authentication

val kycService = KYCService.fromDomain("testanchor.stellar.org")

// First argument is the Stellar account ID (G... address) — NOT the customer UUID
val response: HttpResponse = kycService.deleteCustomer(
    account = accountId,   // String — Stellar G... address
    jwt = jwtToken         // String — SEP-10 JWT
)

println("HTTP ${response.status.value}")  // 200 on success

// For shared/omnibus accounts, include memo to identify the specific customer
val response2: HttpResponse = kycService.deleteCustomer(
    account = accountId,
    memo = "12345",
    memoType = "id",       // deprecated but supported for compatibility
    jwt = jwtToken
)
```

Method signature:
```kotlin
suspend fun deleteCustomer(
    account: String,
    memo: String? = null,
    memoType: String? = null,
    jwt: String
): HttpResponse
```

---

## Error handling

All methods throw typed exceptions from `com.soneso.stellar.sdk.sep.sep12.exceptions` on HTTP errors (400, 401, 404, 409, 413). `putCustomerCallback()` and `deleteCustomer()` return `HttpResponse` on success (status 200 or 202; 202 indicates the anchor accepted the request for asynchronous processing) but still throw exceptions for error status codes.

```kotlin
import com.soneso.stellar.sdk.sep.sep12.*
import com.soneso.stellar.sdk.sep.sep12.exceptions.*
val customerId = "d1ce2f48-3ff1-495d-9b96-eb2e3b6d3aec" // id from a previous putCustomer call
val jwtToken = "eyJhbGciOiJFUzI1NiJ9..." // JWT from SEP-10 authentication

val kycService = KYCService.fromDomain("testanchor.stellar.org")

try {
    val request = GetCustomerInfoRequest(
        jwt = jwtToken,
        id = customerId
    )
    val response = kycService.getCustomerInfo(request)

    when (response.status) {
        CustomerStatus.ACCEPTED -> println("Verified — proceed with transaction")
        CustomerStatus.PROCESSING -> println("Under review: ${response.message}")
        CustomerStatus.NEEDS_INFO -> {
            response.fields?.forEach { (name, field) ->
                println("Required: $name — ${field.description}")
            }
        }
        CustomerStatus.REJECTED -> println("Rejected: ${response.message}")
    }

} catch (e: CustomerNotFoundException) {
    // HTTP 404 — customer not registered
    println("Customer not found: ${e.accountId}")

} catch (e: UnauthorizedException) {
    // HTTP 401 — JWT invalid or expired
    println("Authentication failed — re-authenticate via SEP-10")

} catch (e: InvalidFieldException) {
    // HTTP 400 — field validation failed
    println("Invalid field: ${e.fieldName} — ${e.fieldError}")

} catch (e: CustomerAlreadyExistsException) {
    // HTTP 409 — customer already registered
    println("Customer already exists: ${e.existingCustomerId}")

} catch (e: FileTooLargeException) {
    // HTTP 413 — file exceeds size limit
    println("File too large: ${e.fileSize} bytes")

} catch (e: KYCException) {
    // Base class for all other SEP-12 errors
    println("KYC error: ${e.message}")

} catch (e: Exception) {
    println("Network or unexpected error: $e")
}
```

Exception hierarchy:

| Exception | HTTP Code | Key properties |
|---|---|---|
| `KYCException` | (base class) | `message: String` |
| `CustomerNotFoundException` | 404 | `accountId: String` |
| `UnauthorizedException` | 401 | (no extra properties) |
| `InvalidFieldException` | 400 | `fieldName: String?`, `fieldError: String?` |
| `CustomerAlreadyExistsException` | 409 | `existingCustomerId: String?` |
| `FileTooLargeException` | 413 | `fileSize: Long?` |

---

## Response reference

### GetCustomerInfoResponse

| Property | Type | Description |
|----------|------|-------------|
| `id` | `String?` | Anchor-assigned customer ID (null if no record yet) |
| `status` | `CustomerStatus` | Enum: `ACCEPTED`, `PROCESSING`, `NEEDS_INFO`, or `REJECTED` |
| `message` | `String?` | Human-readable message; required when `REJECTED` |
| `fields` | `Map<String, GetCustomerInfoField>?` | Fields still needed (keyed by SEP-9 field name) |
| `providedFields` | `Map<String, GetCustomerInfoProvidedField>?` | Fields already received (keyed by SEP-9 field name) |

### GetCustomerInfoField

| Property | Type | Description |
|----------|------|-------------|
| `type` | `String` | `"string"`, `"binary"`, `"number"`, or `"date"` |
| `description` | `String` | Human-readable description |
| `optional` | `Boolean?` | `null`/`false` = required; `true` = optional |
| `choices` | `List<String>?` | Valid values; `null` when unconstrained |

### GetCustomerInfoProvidedField

Same properties as `GetCustomerInfoField`, plus:

| Property | Type | Description |
|----------|------|-------------|
| `status` | `FieldStatus?` | Enum: `ACCEPTED`, `PROCESSING`, `REJECTED`, or `VERIFICATION_REQUIRED`. When `VERIFICATION_REQUIRED`, submit the code received out-of-band (SMS / email) by passing it in `PutCustomerInfoRequest.verificationFields` with the field name suffixed by `_verification` (e.g., `"email_address_verification" to "ABC123"`). |
| `error` | `String?` | Rejection reason when status is `REJECTED` |

### PutCustomerInfoResponse

| Property | Type | Description |
|----------|------|-------------|
| `id` | `String` | Anchor-assigned customer ID (non-null) |

### CustomerFileResponse

| Property | Type | Description |
|----------|------|-------------|
| `fileId` | `String` | Unique file identifier |
| `contentType` | `String` | MIME type of the file |
| `size` | `Long` | File size in bytes |
| `expiresAt` | `String?` | ISO 8601 expiry timestamp, or `null` |
| `customerId` | `String?` | Linked customer ID, or `null` |

### GetCustomerFilesResponse

| Property | Type | Description |
|----------|------|-------------|
| `files` | `List<CustomerFileResponse>` | List of files; empty list if none |

---

## Common pitfalls

**WRONG: status is a String -- compare with string literals**

```kotlin
// WRONG: status is CustomerStatus enum, not String
if (response.status == "ACCEPTED") { ... }         // always false — type mismatch

// CORRECT: compare with enum values
if (response.status == CustomerStatus.ACCEPTED) { ... }

// CORRECT: use when expression
when (response.status) {
    CustomerStatus.ACCEPTED -> { /* ... */ }
    CustomerStatus.NEEDS_INFO -> { /* ... */ }
    CustomerStatus.PROCESSING -> { /* ... */ }
    CustomerStatus.REJECTED -> { /* ... */ }
}
```

**WRONG: FieldStatus is a String -- compare with string literals**

```kotlin
// WRONG: FieldStatus is an enum, not a String
if (field.status == "VERIFICATION_REQUIRED") { ... }  // always false

// CORRECT: compare with enum values
if (field.status == FieldStatus.VERIFICATION_REQUIRED) { ... }
```

**WRONG: birthDate, idIssueDate, idExpirationDate expect strings -- not LocalDate**

```kotlin
import kotlinx.datetime.LocalDate

// WRONG: strings are not accepted — these properties are typed as LocalDate?
val person = NaturalPersonKYCFields(
    birthDate = "1990-05-15",           // compile error: String not LocalDate
    idIssueDate = "2020-01-15",         // compile error
    idExpirationDate = "2030-01-15"     // compile error
)

// CORRECT: LocalDate objects
val person = NaturalPersonKYCFields(
    birthDate = LocalDate(1990, 5, 15),
    idIssueDate = LocalDate(2020, 1, 15),
    idExpirationDate = LocalDate(2030, 1, 15)
)
// The SDK serializes date fields as YYYY-MM-DD (date only) per the SEP-9 spec
```

**WRONG: VATNumber is mixed-case -- not vatNumber**

```kotlin
// WRONG: property does not exist
val org = OrganizationKYCFields(vatNumber = "DE123456789")  // compile error

// CORRECT: uppercase VAT
val org = OrganizationKYCFields(VATNumber = "DE123456789")
// Sent to the server as "organization.VAT_number"
```

**WRONG: OrganizationKYCFields.registrationDate is LocalDate, not String**

```kotlin
// WRONG: registrationDate is typed String?, not LocalDate?
val org = OrganizationKYCFields(
    registrationDate = LocalDate(2010, 6, 15)  // compile error
)

// CORRECT: ISO 8601 string
val orgCorrect = OrganizationKYCFields(
    registrationDate = "2010-06-15"
)
// (Unlike NaturalPersonKYCFields.birthDate which IS a LocalDate)
```

**WRONG: using setter / getter methods on SEP-12 data classes**

All SEP-12 request and response classes are Kotlin data classes with `val` properties. There are no setters, no `getX()` methods, and no empty constructors — required fields must be supplied at construction time.

```kotlin
val customerId = "d1ce2f48-3ff1-495d-9b96-eb2e3b6d3aec" // id from a previous putCustomer call
val jwtToken = "eyJhbGciOiJFUzI1NiJ9..." // JWT from SEP-10 authentication
// WRONG: empty constructor + property assignment — required fields have no default,
// and val properties have no setter
val request = GetCustomerInfoRequest()
request.jwt = jwtToken            // compile error
val id = fileResponse.getFileId() // compile error — no such method

// CORRECT: pass all values in the constructor, access val properties directly
val request = GetCustomerInfoRequest(
    jwt = jwtToken,
    id = customerId,
)
val id = fileResponse.fileId
val sz = fileResponse.size  // Long, not Int
```

**WRONG: CustomerFileResponse.size is Int**

```kotlin
// WRONG: size is Long, not Int
val size: Int = fileResponse.size    // compile error — type mismatch

// CORRECT: size is Long
val size: Long = fileResponse.size
```

**WRONG: putCustomerVerification() returns PutCustomerInfoResponse**

```kotlin
val kycService = KYCService.fromDomain("testanchor.stellar.org")
// WRONG: treating return value as PutCustomerInfoResponse
@Suppress("DEPRECATION")
val response = kycService.putCustomerVerification(request)
println(response.id)  // This is GetCustomerInfoResponse.id (String?), not PutCustomerInfoResponse.id (String)

// CORRECT: return type is GetCustomerInfoResponse — use response.status
@Suppress("DEPRECATION")
println(response.status)  // CustomerStatus enum: ACCEPTED, NEEDS_INFO, etc.
```

**WRONG: deleteCustomer() first parameter is the customer UUID**

```kotlin
val customerId = "d1ce2f48-3ff1-495d-9b96-eb2e3b6d3aec" // id from a previous putCustomer call
val jwtToken = "eyJhbGciOiJFUzI1NiJ9..." // JWT from SEP-10 authentication
val kycService = KYCService.fromDomain("testanchor.stellar.org")
// WRONG: passing the anchor-assigned customer UUID
kycService.deleteCustomer(account = customerId, jwt = jwtToken)  // 404

// CORRECT: first argument is the Stellar account G... address
kycService.deleteCustomer(account = "GDAT5HWTGIU4TSSZ4752OUC4SABDLTLZFRPZUJ3D6LKBNEPA7V2CIG54", jwt = jwtToken)
```

**WRONG: deleteCustomer() and putCustomerCallback() return parsed response objects**

```kotlin
val accountId = "GDAT5HWTGIU4TSSZ4752OUC4SABDLTLZFRPZUJ3D6LKBNEPA7V2CIG54"
val jwtToken = "eyJhbGciOiJFUzI1NiJ9..." // JWT from SEP-10 authentication
val kycService = KYCService.fromDomain("testanchor.stellar.org")
// WRONG: treating return as a parsed response object
val response: GetCustomerInfoResponse = kycService.deleteCustomer(...)  // compile error
val cbResponse: PutCustomerInfoResponse = kycService.putCustomerCallback(...)  // compile error

// CORRECT: return type is Ktor HttpResponse — success returns the raw response
// Both methods DO throw typed exceptions for error codes (401, 404, etc.)
try {
    val response: HttpResponse = kycService.deleteCustomer(account = accountId, jwt = jwtToken)
    println("Success: ${response.status.value}")  // 200
} catch (e: CustomerNotFoundException) {
    println("Customer not found: ${e.accountId}")
} catch (e: UnauthorizedException) {
    println("JWT expired, re-authenticate")
}

// putCustomerCallback() follows the same pattern
try {
    val cbResponse: HttpResponse = kycService.putCustomerCallback(request)
    println("Callback registered: ${cbResponse.status.value}")
} catch (e: KYCException) {
    println("Failed: ${e.message}")
}
```

**WRONG: using customFields for file references -- use fileReferences**

```kotlin
val jwtToken = "eyJhbGciOiJFUzI1NiJ9..." // JWT from SEP-10 authentication
// WRONG: file references go in fileReferences, not customFields
val request = PutCustomerInfoRequest(
    jwt = jwtToken,
    customFields = mapOf("photo_id_front_file_id" to fileId)  // works but uses wrong semantic field
)

// CORRECT: use the dedicated fileReferences parameter
val requestWithFiles = PutCustomerInfoRequest(
    jwt = jwtToken,
    fileReferences = mapOf("photo_id_front_file_id" to fileId)
)
```

---

