# SEP-09: Standard KYC Fields

**Purpose:** Standard vocabulary for KYC (Know Your Customer) and AML (Anti-Money Laundering) data fields.
**Prerequisites:** None
**Standard:** [SEP-0009 v1.18.0](https://github.com/stellar/stellar-protocol/blob/master/ecosystem/sep-0009.md)

SEP-09 fields are used by [SEP-12](sep-12.md) (`PutCustomerInfoRequest.kycFields`), SEP-24, and SEP-31.

Code examples assume a `suspend` calling context and these imports:

```kotlin
import com.soneso.stellar.sdk.*
import com.soneso.stellar.sdk.sep.sep09.*
import kotlinx.datetime.LocalDate
```

## Table of Contents

- [Class overview](#class-overview)
- [StandardKYCFields container](#standardkycfields-container)
- [NaturalPersonKYCFields](#naturalpersonkycfields)
  - [All properties](#all-properties)
  - [File properties](#file-properties)
  - [fields() and files() output](#fields-and-files-output)
- [OrganizationKYCFields](#organizationkycfields)
  - [All properties](#all-properties-1)
  - [File properties](#file-properties-1)
  - [Organization prefix behavior](#organization-prefix-behavior)
- [FinancialAccountKYCFields](#financialaccountkycfields)
  - [All properties](#all-properties-2)
  - [keyPrefix parameter](#keyprefix-parameter)
- [CardKYCFields](#cardkycfields)
  - [All properties](#all-properties-3)
- [Field key constants](#field-key-constants)
- [Complete example: natural person with bank account](#complete-example-natural-person-with-bank-account)
- [Complete example: organization](#complete-example-organization)
- [Integration with SEP-12](#integration-with-sep-12)
- [Common pitfalls](#common-pitfalls)

---

## Class overview

| Class | Purpose | Prefix on `fields()` |
|-------|---------|----------------------|
| `StandardKYCFields` | Container for natural person + organization | n/a (delegates to nested objects) |
| `NaturalPersonKYCFields` | Individual customer data | none |
| `OrganizationKYCFields` | Business/entity data | `organization.` |
| `FinancialAccountKYCFields` | Bank, mobile money, crypto | none (or prefix from parent) |
| `CardKYCFields` | Credit/debit card data | `card.` |

All classes are **immutable `data class`es** -- set all values via constructor parameters (named arguments with defaults).

---

## StandardKYCFields container

`StandardKYCFields` is a container with two optional constructor parameters. Unlike some other SDK implementations, the KMP SDK's `StandardKYCFields` **does** have its own `fields()` and `files()` methods that aggregate from nested objects.

```kotlin
import com.soneso.stellar.sdk.sep.sep09.StandardKYCFields
import com.soneso.stellar.sdk.sep.sep09.NaturalPersonKYCFields
import com.soneso.stellar.sdk.sep.sep09.OrganizationKYCFields

val kyc = StandardKYCFields(
    naturalPersonKYCFields = NaturalPersonKYCFields(/* ... */),  // NaturalPersonKYCFields? = null
    organizationKYCFields = OrganizationKYCFields(/* ... */)     // OrganizationKYCFields? = null
)

// Aggregates fields() from both nested objects
val allFields: Map<String, String> = kyc.fields()

// Aggregates files() from both nested objects
val allFiles: Map<String, ByteArray> = kyc.files()
```

---

## NaturalPersonKYCFields

### All properties

All properties are constructor parameters with `null` defaults. Use named arguments.

```kotlin
import com.soneso.stellar.sdk.sep.sep09.NaturalPersonKYCFields
import com.soneso.stellar.sdk.sep.sep09.FinancialAccountKYCFields
import com.soneso.stellar.sdk.sep.sep09.CardKYCFields
import kotlinx.datetime.LocalDate

val p = NaturalPersonKYCFields(
    // Identity
    lastName       = "Doe",          // String?  -> "last_name"
    firstName      = "John",         // String?  -> "first_name"
    additionalName = "Michael",      // String?  -> "additional_name" (middle name)
    sex            = "male",         // String?  -> "sex" (male | female | other)

    // Birth
    birthDate        = LocalDate(1990, 5, 15),  // LocalDate?  -> "birth_date" (via toString() -> "1990-05-15")
    birthPlace       = "New York, NY",          // String?     -> "birth_place"
    birthCountryCode = "USA",                   // String?     -> "birth_country_code" (ISO 3166-1 alpha-3)

    // Contact
    emailAddress      = "john@example.com",  // String? -> "email_address"
    mobileNumber      = "+14155551234",      // String? -> "mobile_number" (E.164)
    mobileNumberFormat = "E.164",            // String? -> "mobile_number_format" (optional clarifier)

    // Current address
    address            = "123 Main St\nNew York, NY 10001", // String? -> "address" (multi-line ok)
    city               = "New York",    // String? -> "city"
    stateOrProvince    = "NY",          // String? -> "state_or_province"
    postalCode         = "10001",       // String? -> "postal_code"
    addressCountryCode = "USA",         // String? -> "address_country_code" (ISO 3166-1 alpha-3)

    // Identity document (text fields only -- images are file properties below)
    idType           = "passport",              // String?    -> "id_type" (passport | drivers_license | id_card | etc.)
    idNumber         = "AB123456",             // String?    -> "id_number"
    idCountryCode    = "USA",                  // String?    -> "id_country_code" (ISO 3166-1 alpha-3)
    idIssueDate      = LocalDate(2020, 1, 15), // LocalDate? -> "id_issue_date" (via toString() -> "2020-01-15")
    idExpirationDate = LocalDate(2030, 1, 15), // LocalDate? -> "id_expiration_date" (via toString() -> "2030-01-15")

    // Tax
    taxId     = "123-45-6789",  // String? -> "tax_id"
    taxIdName = "SSN",          // String? -> "tax_id_name"

    // Employment
    // WRONG: occupation = 2512 -- occupation is String in KMP SDK, not Int
    // CORRECT: occupation is String (ISCO-08 code, 3 characters)
    occupation      = "251",               // String? -> "occupation" (ISCO-08 code)
    employerName    = "Acme Corp",         // String? -> "employer_name"
    employerAddress = "456 Business Ave",  // String? -> "employer_address"

    // Other
    languageCode = "en",            // String? -> "language_code" (ISO 639-1)
    ipAddress    = "192.168.1.1",   // String? -> "ip_address"
    referralId   = "REF123",        // String? -> "referral_id"

    // Nested objects (merged into fields() output automatically)
    financialAccountKYCFields = FinancialAccountKYCFields(/* ... */), // FinancialAccountKYCFields? = null
    cardKYCFields             = CardKYCFields(/* ... */)              // CardKYCFields? = null
)
```

### File properties

File properties are constructor parameters on the same class but returned **only** by `files()`, not by `fields()`. Assign raw `ByteArray` bytes; the SDK sends them as multipart/form-data when submitting via SEP-12 `putCustomerInfo()`.

```kotlin
import com.soneso.stellar.sdk.sep.sep09.NaturalPersonKYCFields
import java.io.File // JVM/Android only

val idFrontBytes = File("/path/to/id_front.jpg").readBytes()
val idBackBytes  = File("/path/to/id_back.jpg").readBytes()

val p = NaturalPersonKYCFields(
    firstName = "John",
    lastName  = "Doe",
    photoIdFront            = idFrontBytes,   // ByteArray? -> "photo_id_front"
    photoIdBack             = idBackBytes,     // ByteArray? -> "photo_id_back"
    notaryApprovalOfPhotoId = null,            // ByteArray? -> "notary_approval_of_photo_id"
    photoProofResidence     = null,            // ByteArray? -> "photo_proof_residence"
    proofOfIncome           = null,            // ByteArray? -> "proof_of_income"
    proofOfLiveness         = null             // ByteArray? -> "proof_of_liveness"
)
```

### fields() and files() output

```kotlin
// Text fields -- omits all null fields, omits all file properties
val fields: Map<String, String> = p.fields()
// e.g. {"first_name": "John", "last_name": "Doe", "email_address": "john@example.com", ...}

// Binary fields only -- omits null file properties
val files: Map<String, ByteArray> = p.files()
// e.g. {"photo_id_front": ByteArray(...), "photo_id_back": ByteArray(...)}

// occupation (String) is passed through directly:
// occupation = "251"  ->  fields["occupation"] == "251"
//
// LocalDate fields are serialized with toString() (ISO 8601 date-only):
// birthDate = LocalDate(1990, 5, 15)    ->  fields["birth_date"] == "1990-05-15"
// idIssueDate = LocalDate(2020, 1, 1)   ->  fields["id_issue_date"] == "2020-01-01"
//
// financialAccountKYCFields and cardKYCFields are merged into fields() output automatically
```

---

## OrganizationKYCFields

### All properties

```kotlin
import com.soneso.stellar.sdk.sep.sep09.OrganizationKYCFields
import com.soneso.stellar.sdk.sep.sep09.FinancialAccountKYCFields
import com.soneso.stellar.sdk.sep.sep09.CardKYCFields

val org = OrganizationKYCFields(
    // Corporate identity
    name               = "Acme Corp S.L.",        // String? -> "organization.name"
    VATNumber          = "ESB12345678",            // String? -> "organization.VAT_number"  (mixed-case key)
    registrationNumber = "B-12345678",             // String? -> "organization.registration_number"
    registrationDate   = "2015-06-01",             // String? -> "organization.registration_date" (ISO 8601 string)
    registeredAddress  = "100 Gran Via, Madrid",   // String? -> "organization.registered_address"

    // Corporate structure
    numberOfShareholders = 3,             // Int?    -> "organization.number_of_shareholders" (output as string)
    shareholderName      = "John Smith",  // String? -> "organization.shareholder_name"
    directorName         = "Jane Doe",    // String? -> "organization.director_name"

    // Address / contact
    addressCountryCode = "ESP",                          // String? -> "organization.address_country_code"
    stateOrProvince    = "Madrid",                       // String? -> "organization.state_or_province"
    city               = "Madrid",                       // String? -> "organization.city"
    postalCode         = "28013",                        // String? -> "organization.postal_code"
    website            = "https://acme.example.com",     // String? -> "organization.website"
    email              = "info@acme.example.com",        // String? -> "organization.email"
    phone              = "+34911234567",                  // String? -> "organization.phone"

    // Nested objects
    financialAccountKYCFields = FinancialAccountKYCFields(/* ... */), // keys get "organization." prefix
    cardKYCFields             = CardKYCFields(/* ... */)              // keys stay "card.*" (NO org prefix)
)
```

### File properties

```kotlin
import com.soneso.stellar.sdk.sep.sep09.OrganizationKYCFields
import java.io.File // JVM/Android only

val org = OrganizationKYCFields(
    name = "Acme Corp",
    photoIncorporationDoc = File("/path/to/cert.pdf").readBytes(),   // ByteArray? -> "organization.photo_incorporation_doc"
    photoProofAddress     = File("/path/to/bill.pdf").readBytes()    // ByteArray? -> "organization.photo_proof_address"
)

val files: Map<String, ByteArray> = org.files()
// {"organization.photo_incorporation_doc": ByteArray(...), "organization.photo_proof_address": ByteArray(...)}
```

### Organization prefix behavior

`OrganizationKYCFields.fields()` automatically applies the `"organization."` prefix to all its own fields **and** to any nested `FinancialAccountKYCFields`. Card fields do NOT receive the `organization.` prefix -- they always use the `card.` prefix regardless of nesting.

```kotlin
val org = OrganizationKYCFields(
    name = "Acme Corp",
    financialAccountKYCFields = FinancialAccountKYCFields(
        bankName = "Chase"
    ),
    cardKYCFields = CardKYCFields(
        number = "4111111111111111"
    )
)

val fields: Map<String, String> = org.fields()
// "organization.name"      -> "Acme Corp"         (org prefix applied)
// "organization.bank_name" -> "Chase"             (financial gets org prefix)
// "card.number"            -> "4111111111111111"  (card prefix -- NOT org prefix)
```

---

## FinancialAccountKYCFields

### All properties

```kotlin
import com.soneso.stellar.sdk.sep.sep09.FinancialAccountKYCFields

val fin = FinancialAccountKYCFields(
    // Traditional banking
    bankName          = "First National Bank",  // String? -> "bank_name"
    bankAccountType   = "checking",             // String? -> "bank_account_type" (checking | savings)
    bankAccountNumber = "1234567890",           // String? -> "bank_account_number"
    bankNumber        = "021000021",            // String? -> "bank_number" (routing number in US)
    bankBranchNumber  = "001",                  // String? -> "bank_branch_number"
    bankPhoneNumber   = "+18005551234",         // String? -> "bank_phone_number" (E.164)

    // Transfer memo / destination tag
    externalTransferMemo = "WIRE-REF-12345",   // String? -> "external_transfer_memo"

    // Regional banking formats
    clabeNumber = "032180000118359719",          // String? -> "clabe_number" (Mexico CLABE)
    cbuNumber   = "0110000000001234567890",      // String? -> "cbu_number"   (Argentina CBU/CVU)
    cbuAlias    = "mi.cuenta.arg",               // String? -> "cbu_alias"    (Argentina alias)

    // Mobile money
    mobileMoneyNumber   = "+254712345678",   // String? -> "mobile_money_number" (E.164)
    mobileMoneyProvider = "M-Pesa",          // String? -> "mobile_money_provider"

    // Crypto
    cryptoAddress = "GDAT5HWTGIU4TSSZ4752OUC4SABDLTLZFRPZUJ3D6LKBNEPA7V2CIG54",               // String? -> "crypto_address"
    // cryptoMemo is @Deprecated -- use externalTransferMemo instead
)
```

### keyPrefix parameter

`FinancialAccountKYCFields.fields()` accepts an optional `keyPrefix` parameter (default `""`). You do not normally call this directly -- `NaturalPersonKYCFields` calls it without a prefix, and `OrganizationKYCFields` calls it with `"organization."`. You can call it directly if building custom field maps:

```kotlin
val fin = FinancialAccountKYCFields(
    bankName = "Chase"
)

// No prefix (when used with natural person)
val fields: Map<String, String> = fin.fields()
// {"bank_name": "Chase"}

// With organization prefix
val orgFields: Map<String, String> = fin.fields(keyPrefix = "organization.")
// {"organization.bank_name": "Chase"}
```

Note: `FinancialAccountKYCFields` has **no** `files()` method -- there are no binary fields for financial accounts.

---

## CardKYCFields

### All properties

```kotlin
import com.soneso.stellar.sdk.sep.sep09.CardKYCFields
val network = Network.TESTNET

val card = CardKYCFields(
    number         = "4111111111111111",      // String? -> "card.number"
    expirationDate = "29-11",                 // String? -> "card.expiration_date" (YY-MM format, e.g. November 2029)
    cvc            = "123",                   // String? -> "card.cvc"
    holderName     = "JOHN DOE",              // String? -> "card.holder_name"
    network        = "Visa",                  // String? -> "card.network" (Visa, Mastercard, AmEx, etc.)
    token          = "tok_stripe_test_token", // String? -> "card.token" (preferred over raw card data)

    // Billing address
    address         = "123 Main St, Apt 4B",  // String? -> "card.address"
    city            = "New York",              // String? -> "card.city"
    stateOrProvince = "NY",                   // String? -> "card.state_or_province" (ISO 3166-2)
    postalCode      = "10001",                 // String? -> "card.postal_code"
    countryCode     = "US"                    // String? -> "card.country_code" (ISO 3166-1 alpha-2: 2-letter)
)

val fields: Map<String, String> = card.fields()
// Returns only non-null fields, all keys prefixed with "card."
```

Note: `CardKYCFields` has **no** `files()` method -- there are no binary fields for cards.

---

## Field key constants

Every class exposes `const val` constants in its `companion object` for all field keys. Use these instead of hardcoded strings to avoid typos.

```kotlin
import com.soneso.stellar.sdk.sep.sep09.NaturalPersonKYCFields
import com.soneso.stellar.sdk.sep.sep09.OrganizationKYCFields
import com.soneso.stellar.sdk.sep.sep09.FinancialAccountKYCFields
import com.soneso.stellar.sdk.sep.sep09.CardKYCFields

// NaturalPersonKYCFields -- text field key constants (UPPER_SNAKE_CASE)
NaturalPersonKYCFields.LAST_NAME              // "last_name"
NaturalPersonKYCFields.FIRST_NAME             // "first_name"
NaturalPersonKYCFields.ADDITIONAL_NAME        // "additional_name"
NaturalPersonKYCFields.EMAIL_ADDRESS          // "email_address"
NaturalPersonKYCFields.MOBILE_NUMBER          // "mobile_number"
NaturalPersonKYCFields.MOBILE_NUMBER_FORMAT   // "mobile_number_format"
NaturalPersonKYCFields.BIRTH_DATE             // "birth_date"
NaturalPersonKYCFields.BIRTH_PLACE            // "birth_place"
NaturalPersonKYCFields.BIRTH_COUNTRY_CODE     // "birth_country_code"
NaturalPersonKYCFields.SEX                    // "sex"
NaturalPersonKYCFields.ADDRESS                // "address"
NaturalPersonKYCFields.CITY                   // "city"
NaturalPersonKYCFields.STATE_OR_PROVINCE      // "state_or_province"
NaturalPersonKYCFields.POSTAL_CODE            // "postal_code"
NaturalPersonKYCFields.ADDRESS_COUNTRY_CODE   // "address_country_code"
NaturalPersonKYCFields.ID_TYPE                // "id_type"
NaturalPersonKYCFields.ID_NUMBER              // "id_number"
NaturalPersonKYCFields.ID_COUNTRY_CODE        // "id_country_code"
NaturalPersonKYCFields.ID_ISSUE_DATE          // "id_issue_date"
NaturalPersonKYCFields.ID_EXPIRATION_DATE     // "id_expiration_date"
NaturalPersonKYCFields.TAX_ID                 // "tax_id"
NaturalPersonKYCFields.TAX_ID_NAME            // "tax_id_name"
NaturalPersonKYCFields.OCCUPATION             // "occupation"
NaturalPersonKYCFields.EMPLOYER_NAME          // "employer_name"
NaturalPersonKYCFields.EMPLOYER_ADDRESS       // "employer_address"
NaturalPersonKYCFields.LANGUAGE_CODE          // "language_code"
NaturalPersonKYCFields.IP_ADDRESS             // "ip_address"
NaturalPersonKYCFields.REFERRAL_ID            // "referral_id"
// Binary field key constants:
NaturalPersonKYCFields.PHOTO_ID_FRONT                  // "photo_id_front"
NaturalPersonKYCFields.PHOTO_ID_BACK                   // "photo_id_back"
NaturalPersonKYCFields.NOTARY_APPROVAL_OF_PHOTO_ID     // "notary_approval_of_photo_id"
NaturalPersonKYCFields.PHOTO_PROOF_RESIDENCE            // "photo_proof_residence"
NaturalPersonKYCFields.PROOF_OF_INCOME                  // "proof_of_income"
NaturalPersonKYCFields.PROOF_OF_LIVENESS                // "proof_of_liveness"

// OrganizationKYCFields -- all key values include "organization." prefix
OrganizationKYCFields.NAME                    // "organization.name"
OrganizationKYCFields.VAT_NUMBER              // "organization.VAT_number"   (mixed case)
OrganizationKYCFields.REGISTRATION_NUMBER     // "organization.registration_number"
OrganizationKYCFields.REGISTRATION_DATE       // "organization.registration_date"
OrganizationKYCFields.REGISTERED_ADDRESS      // "organization.registered_address"
OrganizationKYCFields.NUMBER_OF_SHAREHOLDERS  // "organization.number_of_shareholders"
OrganizationKYCFields.SHAREHOLDER_NAME        // "organization.shareholder_name"
OrganizationKYCFields.DIRECTOR_NAME           // "organization.director_name"
OrganizationKYCFields.ADDRESS_COUNTRY_CODE    // "organization.address_country_code"
OrganizationKYCFields.STATE_OR_PROVINCE       // "organization.state_or_province"
OrganizationKYCFields.CITY                    // "organization.city"
OrganizationKYCFields.POSTAL_CODE             // "organization.postal_code"
OrganizationKYCFields.WEBSITE                 // "organization.website"
OrganizationKYCFields.EMAIL                   // "organization.email"
OrganizationKYCFields.PHONE                   // "organization.phone"
// Binary field key constants:
OrganizationKYCFields.PHOTO_INCORPORATION_DOC // "organization.photo_incorporation_doc"
OrganizationKYCFields.PHOTO_PROOF_ADDRESS     // "organization.photo_proof_address"

// FinancialAccountKYCFields -- bare names (no prefix)
FinancialAccountKYCFields.BANK_NAME              // "bank_name"
FinancialAccountKYCFields.BANK_ACCOUNT_TYPE      // "bank_account_type"
FinancialAccountKYCFields.BANK_ACCOUNT_NUMBER    // "bank_account_number"
FinancialAccountKYCFields.BANK_NUMBER            // "bank_number"
FinancialAccountKYCFields.BANK_PHONE_NUMBER      // "bank_phone_number"
FinancialAccountKYCFields.BANK_BRANCH_NUMBER     // "bank_branch_number"
FinancialAccountKYCFields.EXTERNAL_TRANSFER_MEMO // "external_transfer_memo"
FinancialAccountKYCFields.CLABE_NUMBER           // "clabe_number"
FinancialAccountKYCFields.CBU_NUMBER             // "cbu_number"
FinancialAccountKYCFields.CBU_ALIAS              // "cbu_alias"
FinancialAccountKYCFields.MOBILE_MONEY_NUMBER    // "mobile_money_number"
FinancialAccountKYCFields.MOBILE_MONEY_PROVIDER  // "mobile_money_provider"
FinancialAccountKYCFields.CRYPTO_ADDRESS         // "crypto_address"
FinancialAccountKYCFields.CRYPTO_MEMO            // "crypto_memo" (deprecated)

// CardKYCFields -- all key values include "card." prefix
CardKYCFields.NUMBER             // "card.number"
CardKYCFields.EXPIRATION_DATE    // "card.expiration_date"
CardKYCFields.CVC                // "card.cvc"
CardKYCFields.HOLDER_NAME        // "card.holder_name"
CardKYCFields.NETWORK            // "card.network"
CardKYCFields.TOKEN              // "card.token"
CardKYCFields.ADDRESS            // "card.address"
CardKYCFields.CITY               // "card.city"
CardKYCFields.STATE_OR_PROVINCE  // "card.state_or_province"
CardKYCFields.POSTAL_CODE        // "card.postal_code"
CardKYCFields.COUNTRY_CODE       // "card.country_code"
```

---

## Complete example: natural person with bank account

```kotlin
import com.soneso.stellar.sdk.sep.sep09.StandardKYCFields
import com.soneso.stellar.sdk.sep.sep09.NaturalPersonKYCFields
import com.soneso.stellar.sdk.sep.sep09.FinancialAccountKYCFields
import kotlinx.datetime.LocalDate
import java.io.File // JVM/Android only

// Bank account (nested, merged into fields() automatically)
val bank = FinancialAccountKYCFields(
    bankName          = "First National Bank",
    bankAccountType   = "checking",
    bankAccountNumber = "1234567890",
    bankNumber        = "021000021"  // routing number
)

// Photo ID (binary -- retrieved separately via files())
val idFrontBytes = File("/path/to/passport_front.jpg").readBytes()
val idBackBytes  = File("/path/to/passport_back.jpg").readBytes()

val person = NaturalPersonKYCFields(
    // Identity
    firstName        = "Jane",
    lastName         = "Doe",
    birthDate        = LocalDate(1990, 5, 15),
    birthCountryCode = "USA",
    sex              = "female",

    // Address
    address            = "123 Main St, Apt 4B",
    city               = "San Francisco",
    stateOrProvince    = "CA",
    postalCode         = "94102",
    addressCountryCode = "USA",

    // Contact
    emailAddress = "jane@example.com",
    mobileNumber = "+14155551234",

    // Tax
    taxId     = "123-45-6789",
    taxIdName = "SSN",

    // ID document (text fields -- images go on file properties)
    idType           = "passport",
    idNumber         = "AB123456",
    idCountryCode    = "USA",
    idIssueDate      = LocalDate(2020, 1, 15),
    idExpirationDate = LocalDate(2030, 1, 15),

    // Bank account (nested)
    financialAccountKYCFields = bank,

    // Photo ID (binary)
    photoIdFront = idFrontBytes,
    photoIdBack  = idBackBytes
)

// Text fields for submission
val textFields: Map<String, String> = person.fields()
// {"first_name": "Jane", "last_name": "Doe",
//  "birth_date": "1990-05-15",
//  "bank_name": "First National Bank", "bank_account_number": "1234567890", ...}

// File fields for submission
val fileFields: Map<String, ByteArray> = person.files()
// {"photo_id_front": ByteArray(...), "photo_id_back": ByteArray(...)}

// Wrap in container and pass to SEP-12
val kyc = StandardKYCFields(
    naturalPersonKYCFields = person
)
```

---

## Complete example: organization

```kotlin
import com.soneso.stellar.sdk.sep.sep09.StandardKYCFields
import com.soneso.stellar.sdk.sep.sep09.OrganizationKYCFields
import com.soneso.stellar.sdk.sep.sep09.FinancialAccountKYCFields
import java.io.File // JVM/Android only

val bank = FinancialAccountKYCFields(
    bankName          = "Barclays Bank",
    bankAccountNumber = "GB29NWBK60161331926819"
)

val org = OrganizationKYCFields(
    name               = "TechCorp International Ltd",
    VATNumber          = "VAT123456789",
    registrationNumber = "REG2010123456",
    registrationDate   = "2010-05-15",
    registeredAddress  = "50 Canary Wharf, London EC2",
    addressCountryCode = "GBR",
    city               = "London",
    postalCode         = "EC2V 8AB",
    directorName       = "James Anderson",
    website            = "https://www.techcorp.com",
    email              = "compliance@techcorp.com",
    phone              = "+442071234567",
    numberOfShareholders = 3,

    financialAccountKYCFields = bank,

    photoIncorporationDoc = File("/path/to/certificate.pdf").readBytes()
)

val fields: Map<String, String> = org.fields()
// {"organization.name"               -> "TechCorp International Ltd",
//  "organization.VAT_number"         -> "VAT123456789",
//  "organization.registered_address" -> "50 Canary Wharf, London EC2",
//  "organization.bank_name"          -> "Barclays Bank",
//  "organization.bank_account_number"-> "GB29NWBK60161331926819", ...}

val files: Map<String, ByteArray> = org.files()
// {"organization.photo_incorporation_doc": ByteArray(...)}

val kyc = StandardKYCFields(
    organizationKYCFields = org
)
```

---

## Integration with SEP-12

Assign the `StandardKYCFields` container to `PutCustomerInfoRequest.kycFields`. The SEP-12 service calls `fields()` and `files()` on the nested objects internally when sending the request.

```kotlin
import com.soneso.stellar.sdk.sep.sep09.StandardKYCFields
import com.soneso.stellar.sdk.sep.sep09.NaturalPersonKYCFields
import com.soneso.stellar.sdk.sep.sep12.KYCService
import com.soneso.stellar.sdk.sep.sep12.PutCustomerInfoRequest
val jwt = "eyJhbGciOiJFUzI1NiJ9..." // JWT from SEP-10 authentication

suspend fun submitKYC(jwtToken: String) {
    val kycService = KYCService("https://testanchor.stellar.org/kyc")

    val person = NaturalPersonKYCFields(
        firstName    = "John",
        lastName     = "Doe",
        emailAddress = "john@example.com",
        photoIdFront = java.io.File("/path/to/id.jpg").readBytes()
    )

    val kyc = StandardKYCFields(
        naturalPersonKYCFields = person
    )

    val request = PutCustomerInfoRequest(
        jwt       = jwtToken,   // from SEP-10 authentication
        kycFields = kyc
    )

    val response = kycService.putCustomerInfo(request)
    val customerId: String = response.id
}
```

For the full SEP-12 API (status polling, verification, file uploads, etc.) see [sep-12.md](sep-12.md).

---

## Common pitfalls

**WRONG: mutable property assignment -- KMP SDK uses immutable data classes**

```kotlin
// WRONG: trying to set properties after construction (these are val, not var)
val p = NaturalPersonKYCFields()
p.firstName = "John"  // compile error -- val cannot be reassigned

// CORRECT: set all values via constructor named arguments
val p = NaturalPersonKYCFields(
    firstName = "John",
    lastName = "Doe"
)

// CORRECT: use copy() to create a modified copy
val updated = p.copy(emailAddress = "john@example.com")
```

**WRONG: passing `occupation` as an `Int`**

```kotlin
// WRONG: Int assignment -- in KMP SDK occupation is String?, not Int?
val p = NaturalPersonKYCFields(
    occupation = 2512  // compile error -- type mismatch
)

// CORRECT: occupation is String in KMP SDK (ISCO-08 code)
val person = NaturalPersonKYCFields(
    occupation = "251"
)
```

**WRONG: `birthDate`, `idIssueDate`, and `idExpirationDate` are `String`**

```kotlin
import kotlinx.datetime.LocalDate

// WRONG: string for date fields
val p = NaturalPersonKYCFields(
    birthDate        = "1990-05-15",  // compile error -- expects LocalDate?
    idIssueDate      = "2020-01-15",  // compile error
    idExpirationDate = "2030-01-15"   // compile error
)

// CORRECT: all three date fields accept kotlinx.datetime.LocalDate?
val person = NaturalPersonKYCFields(
    birthDate        = LocalDate(1990, 5, 15),
    idIssueDate      = LocalDate(2020, 1, 15),
    idExpirationDate = LocalDate(2030, 1, 15)
)
// In fields() output: LocalDate.toString() -> "1990-05-15" (date-only, no time component)
```

**WRONG: using snake_case constant names (snake_case_field_key)**

```kotlin
// first_name_field_key: from the previous steps of this flow
// WRONG: lowercase snake_case constant names do not exist here
NaturalPersonKYCFields.first_name_field_key  // does not exist

// CORRECT: KMP SDK uses UPPER_SNAKE_CASE companion object constants
NaturalPersonKYCFields.FIRST_NAME  // "first_name"
```

**WRONG: `OrganizationKYCFields.VAT_NUMBER` value is all-lowercase**

```kotlin
// WRONG: assuming the key value is all-lowercase
val key = "organization.vat_number"  // does not exist in SEP-09

// CORRECT: the key preserves the spec's mixed case
val key = OrganizationKYCFields.VAT_NUMBER  // "organization.VAT_number"
// Property: VATNumber = "ESB12345678"
```

**WRONG: card fields nested under an organization get the `organization.` prefix**

```kotlin
// WRONG: assuming org prefix applies to card fields too
val fields = org.fields()
fields["organization.card.number"]  // does not exist

// CORRECT: card fields always use "card." prefix, even under an organization
fields["card.number"]  // correct key
```

**WRONG: calling `files()` on `FinancialAccountKYCFields` or `CardKYCFields`**

```kotlin
// card, fin, person: from the previous steps of this flow
// WRONG: neither class has a files() method
fin.files()  // compile error
card.files() // compile error

// CORRECT: only NaturalPersonKYCFields and OrganizationKYCFields have files()
val personFiles: Map<String, ByteArray> = person.files()
val orgFiles: Map<String, ByteArray>    = org.files()
// StandardKYCFields also has files() -- it aggregates from both nested objects
```

**WRONG: `cryptoMemo` for new code**

```kotlin
// WRONG: deprecated -- still compiles but discouraged
val fin = FinancialAccountKYCFields(
    cryptoMemo = "12345678"  // @Deprecated warning
)

// CORRECT: use the general external_transfer_memo field instead
val financial = FinancialAccountKYCFields(
    externalTransferMemo = "12345678"
)
```

**WRONG: assuming `StandardKYCFields` has no `fields()` method**

```kotlin
// person: from the previous steps of this flow
// WRONG: assuming the container has no fields() method
val kyc = StandardKYCFields(naturalPersonKYCFields = person)
// "must call fields() on nested object directly"

// CORRECT: KMP SDK's StandardKYCFields DOES have fields() and files() methods
val allFields: Map<String, String> = kyc.fields()    // aggregates from both nested objects
val allFiles: Map<String, ByteArray> = kyc.files()   // aggregates from both nested objects

// You can also call fields() on nested objects directly
val personFields = kyc.naturalPersonKYCFields?.fields()
```

---
