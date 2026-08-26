# SEP-9: Standard KYC Fields

**[SEP-0009 Compatibility Matrix](../../compatibility/sep/SEP-0009_COMPATIBILITY_MATRIX.md)** - Full implementation coverage details

SEP-9 defines standardized Know Your Customer (KYC) and Anti-Money Laundering (AML) fields for identity verification in the Stellar ecosystem. Anchors, exchanges, and regulated entities use these fields for consistent identity collection across services.

**Use Cases**:
- Submit customer information for anchor deposit/withdrawal
- Register accounts on exchanges requiring identity verification
- Provide required identity documents for regulated transfers
- Supply bank account or payment card details for off-ramps

Code examples assume a `suspend` calling context and these imports:

```kotlin
import com.soneso.stellar.sdk.*
import com.soneso.stellar.sdk.sep.sep09.*
import kotlinx.datetime.LocalDate
```

## Quick Start

```kotlin
import com.soneso.stellar.sdk.sep.sep09.*
import kotlinx.datetime.LocalDate

// Create KYC fields for an individual customer
val kycFields = StandardKYCFields(
    naturalPersonKYCFields = NaturalPersonKYCFields(
        firstName = "John",
        lastName = "Doe",
        emailAddress = "john@example.com",
        birthDate = LocalDate(1990, 1, 15),
        addressCountryCode = "USA"
    )
)

// Extract text fields for submission
val textFields = kycFields.fields()
// Result: {"first_name": "John", "last_name": "Doe", "email_address": "john@example.com",
//          "birth_date": "1990-01-15", "address_country_code": "USA"}
```

## Natural Person KYC Fields

For individual customers, use `NaturalPersonKYCFields` with personal identification information.

### Basic Personal Information

```kotlin
val person = NaturalPersonKYCFields(
    // Name
    firstName = "John",
    lastName = "Doe",
    additionalName = "William",  // Middle name

    // Contact
    emailAddress = "john@example.com",
    mobileNumber = "+14155551234",  // E.164 format

    // Address
    address = "123 Main Street\nApt 4B",
    city = "San Francisco",
    stateOrProvince = "CA",
    postalCode = "94102",
    addressCountryCode = "USA",  // ISO 3166-1 alpha-3

    // Birth information
    birthDate = LocalDate(1990, 1, 15),
    birthPlace = "New York, NY",
    birthCountryCode = "USA",

    // Language preference
    languageCode = "en"  // ISO 639-1
)

val fields = person.fields()
```

### Government ID Information

```kotlin
val person = NaturalPersonKYCFields(
    firstName = "John",
    lastName = "Doe",

    // Government ID
    idType = "passport",  // passport, drivers_license, id_card
    idNumber = "123456789",
    idCountryCode = "USA",
    idIssueDate = LocalDate(2020, 6, 1),
    idExpirationDate = LocalDate(2030, 6, 1),

    // Tax information
    taxId = "123-45-6789",  // SSN for US
    taxIdName = "SSN"
)

val fields = person.fields()
```

### Employment Information

```kotlin
val person = NaturalPersonKYCFields(
    firstName = "John",
    lastName = "Doe",

    // Employment
    occupation = "251",  // ISCO08 code (Software developer)
    employerName = "Tech Corp",
    employerAddress = "456 Corporate Blvd, San Francisco, CA"
)

val fields = person.fields()
```

## Organization KYC Fields

For business customers, use `OrganizationKYCFields`. All field keys are automatically prefixed with `organization.` when serialized.

```kotlin
val organization = OrganizationKYCFields(
    // Business identity
    name = "Example Corporation",
    VATNumber = "123456789",
    registrationNumber = "987654321",
    registrationDate = "2020-01-01",

    // Registered address
    registeredAddress = "100 Business Park Drive",
    city = "San Francisco",
    stateOrProvince = "CA",
    postalCode = "94102",
    addressCountryCode = "USA",

    // Management and contact
    directorName = "Jane Smith",
    email = "contact@example.com",
    phone = "+14155559999",
    website = "https://example.com",

    // Corporate structure
    numberOfShareholders = 5,
    shareholderName = "Jane Smith"
)

val fields = organization.fields()
// Result: {"organization.name": "Example Corporation",
//          "organization.VAT_number": "123456789", ...}
```

## Financial Account Fields

Bank accounts, mobile money, and cryptocurrency addresses are supported via `FinancialAccountKYCFields`.

### Bank Account

```kotlin
val bankAccount = FinancialAccountKYCFields(
    bankName = "Example Bank",
    bankAccountNumber = "1234567890",
    bankNumber = "123456789",  // Routing number (US)
    bankBranchNumber = "001",
    bankAccountType = "checking"  // checking, savings
)

val fields = bankAccount.fields()
```

### Regional Banking Systems

```kotlin
// Mexico (CLABE)
val mexicanAccount = FinancialAccountKYCFields(
    bankName = "Banco Example",
    clabeNumber = "123456789012345678"
)

// Argentina (CBU/CVU)
val argentineAccount = FinancialAccountKYCFields(
    bankName = "Banco Ejemplo",
    cbuNumber = "1234567890123456789012",
    cbuAlias = "mi.alias.cuenta"
)
```

### Mobile Money

```kotlin
val mobileMoneyAccount = FinancialAccountKYCFields(
    mobileMoneyNumber = "+254712345678",  // E.164 format
    mobileMoneyProvider = "M-Pesa"
)
```

### Cryptocurrency Address

```kotlin
val cryptoAccount = FinancialAccountKYCFields(
    cryptoAddress = "GCEZWKCA5VLDNRLN3RPRJMRZOX3Z6G5CHCGSNFHEYVXM3XOJMDS674JZ",
    externalTransferMemo = "12345"  // Destination tag/memo
)
```

### Financial Account with Organization Prefix

When used within organization fields, financial account fields are prefixed with `organization.`:

```kotlin
val orgWithBankAccount = OrganizationKYCFields(
    name = "Example Corporation",
    financialAccountKYCFields = FinancialAccountKYCFields(
        bankName = "Corporate Bank",
        bankAccountNumber = "9876543210"
    )
)

val fields = orgWithBankAccount.fields()
// Result: {
//   "organization.name": "Example Corporation",
//   "organization.bank_name": "Corporate Bank",
//   "organization.bank_account_number": "9876543210"
// }
```

## Card Payment Fields

Payment card details are supported via `CardKYCFields`. All field keys are automatically prefixed with `card.` when serialized.

### Full Card Details

```kotlin
val card = CardKYCFields(
    number = "4111111111111111",
    expirationDate = "29-11",  // YY-MM format (November 2029)
    cvc = "123",
    holderName = "John Doe",
    network = "Visa",

    // Billing address
    address = "123 Main Street",
    city = "San Francisco",
    stateOrProvince = "CA",
    postalCode = "94102",
    countryCode = "US"  // ISO 3166-1 alpha-2
)

val fields = card.fields()
// Result: {"card.number": "4111...", "card.expiration_date": "29-11", ...}
```

### Tokenized Card (PCI DSS Compliant)

```kotlin
// Use tokenized cards to avoid handling sensitive card data
val tokenizedCard = CardKYCFields(
    token = "tok_visa_1234",  // Token from payment processor (e.g., Stripe)
    holderName = "John Doe",
    countryCode = "US"
)

val fields = tokenizedCard.fields()
```

## Binary Fields (Document Images)

Identity documents and proof of address are submitted as binary data (ByteArray).

### Natural Person Documents

```kotlin
// incomeDocBytes, notaryBytes, passportBackBytes, passportFrontBytes, selfieBytes, utilityBillBytes: from the previous steps of this flow
// Binary fields accept ByteArray (loaded from files in your application)
val person = NaturalPersonKYCFields(
    firstName = "John",
    lastName = "Doe",

    // Identity documents
    photoIdFront = passportFrontBytes,      // Front of passport/ID
    photoIdBack = passportBackBytes,         // Back of passport/ID
    notaryApprovalOfPhotoId = notaryBytes,   // Notary approval document

    // Proof documents
    photoProofResidence = utilityBillBytes,  // Utility bill or bank statement
    proofOfIncome = incomeDocBytes,          // Income verification
    proofOfLiveness = selfieBytes            // Liveness check photo/video
)

// Extract text fields
val textFields = person.fields()

// Extract binary fields separately
val binaryFields = person.files()
// Result: {"photo_id_front": ByteArray(...), "photo_id_back": ByteArray(...), ...}
```

### Organization Documents

```kotlin
// businessAddressBytes, incorporationBytes: from the previous steps of this flow
val organization = OrganizationKYCFields(
    name = "Example Corporation",

    // Organization documents
    photoIncorporationDoc = incorporationBytes,  // Articles of incorporation
    photoProofAddress = businessAddressBytes     // Utility bill with business name
)

val textFields = organization.fields()
val binaryFields = organization.files()
// Result: {"organization.photo_incorporation_doc": ByteArray(...), ...}
```

## Combined StandardKYCFields

The `StandardKYCFields` container combines natural person and organization fields, aggregating all data for submission.

### Individual Customer

```kotlin
val kycFields = StandardKYCFields(
    naturalPersonKYCFields = NaturalPersonKYCFields(
        firstName = "John",
        lastName = "Doe",
        emailAddress = "john@example.com",
        birthDate = LocalDate(1990, 1, 15),
        idType = "passport",
        idNumber = "123456789",
        financialAccountKYCFields = FinancialAccountKYCFields(
            bankName = "Example Bank",
            bankAccountNumber = "1234567890"
        )
    )
)

// Get all text fields for submission
val allTextFields = kycFields.fields()

// Get all binary fields for submission
val allBinaryFields = kycFields.files()
```

### Business Customer

```kotlin
val kycFields = StandardKYCFields(
    organizationKYCFields = OrganizationKYCFields(
        name = "Example Corp",
        VATNumber = "123456789",
        registrationNumber = "987654321",
        directorName = "Jane Smith",
        email = "contact@example.com",
        financialAccountKYCFields = FinancialAccountKYCFields(
            bankName = "Corporate Bank",
            bankAccountNumber = "9876543210"
        )
    )
)

val allTextFields = kycFields.fields()
val allBinaryFields = kycFields.files()
```

## Field Format Standards

| Format | Standard | Example |
|--------|----------|---------|
| Date | ISO 8601 date-only (YYYY-MM-DD) | `1990-01-15` |
| Country (address, birth, ID) | ISO 3166-1 alpha-3 | `USA`, `DEU`, `GBR` |
| Country (card billing) | ISO 3166-1 alpha-2 | `US`, `DE`, `GB` |
| Language | ISO 639-1 | `en`, `de`, `es` |
| Phone | E.164 | `+14155551234` |
| Occupation | ISCO08 code | `251` (Software developer) |
| Card expiration | YY-MM | `29-11` (November 2029) |

## API Reference

**Main Classes**:
- `StandardKYCFields` - Top-level container aggregating all KYC data
- `NaturalPersonKYCFields` - Personal identification (34 fields: 28 text, 6 binary)
- `OrganizationKYCFields` - Business identification (17 fields: 15 text, 2 binary)
- `FinancialAccountKYCFields` - Bank accounts, mobile money, crypto (14 fields)
- `CardKYCFields` - Payment card details (11 fields)

**Key Methods**:
- `fields(): Map<String, String>` - Extract text fields for submission
- `files(): Map<String, ByteArray>` - Extract binary fields for multipart upload
- `fields(keyPrefix: String)` - Extract fields with custom prefix (FinancialAccountKYCFields)

**Field Constants**:
Each class provides companion object constants for field keys:
- `NaturalPersonKYCFields.FIRST_NAME` = `"first_name"`
- `OrganizationKYCFields.NAME` = `"organization.name"`
- `CardKYCFields.NUMBER` = `"card.number"`
- `FinancialAccountKYCFields.BANK_NAME` = `"bank_name"`

**Specification**: [SEP-9: Standard KYC Fields](https://github.com/stellar/stellar-protocol/blob/master/ecosystem/sep-0009.md)

**Implementation**: `com.soneso.stellar.sdk.sep.sep09`

**Last Updated**: 2025-12-08
