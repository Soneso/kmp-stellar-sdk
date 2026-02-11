# SEP-0009 (Standard KYC Fields) Compatibility Matrix

**Generated:** 2026-02-11 15:11:17

**SEP Version:** 1.18.0<br>
**SEP Status:** Active<br>
**SDK Version:** 1.2.1<br>
**SEP URL:** https://github.com/stellar/stellar-protocol/blob/master/ecosystem/sep-0009.md

## SEP Summary

Defines standardized KYC, AML, and financial account-related fields for use in Stellar ecosystem protocols. Applications should use these fields when sending or requesting KYC, AML, or financial account-related information. This is an evolving list designed to handle many different use cases.

## Overall Coverage

**Total Coverage:** 100.0% (76/76 fields)

- ✅ **Implemented:** 76/76
- ❌ **Not Implemented:** 0/76

## Implementation Status

✅ **Fully Implemented**

### Implementation Files

- `stellar-sdk/src/commonMain/kotlin/com/soneso/stellar/sdk/sep/sep09/StandardKYCFields.kt`
- `stellar-sdk/src/commonMain/kotlin/com/soneso/stellar/sdk/sep/sep09/NaturalPersonKYCFields.kt`
- `stellar-sdk/src/commonMain/kotlin/com/soneso/stellar/sdk/sep/sep09/OrganizationKYCFields.kt`
- `stellar-sdk/src/commonMain/kotlin/com/soneso/stellar/sdk/sep/sep09/FinancialAccountKYCFields.kt`
- `stellar-sdk/src/commonMain/kotlin/com/soneso/stellar/sdk/sep/sep09/CardKYCFields.kt`

### Key Classes

- **`StandardKYCFields`**
- **`NaturalPersonKYCFields`** - Methods: fields, files
- **`OrganizationKYCFields`** - Methods: fields, files
- **`FinancialAccountKYCFields`** - Methods: fields
- **`CardKYCFields`** - Methods: fields

### Test Coverage

**Tests:** 128 test cases

**Test Files:**

- `stellar-sdk/src/commonTest/kotlin/com/soneso/stellar/sdk/sep/sep09/StandardKYCFieldsTest.kt`
- `stellar-sdk/src/commonTest/kotlin/com/soneso/stellar/sdk/sep/sep09/NaturalPersonKYCFieldsTest.kt`
- `stellar-sdk/src/commonTest/kotlin/com/soneso/stellar/sdk/sep/sep09/OrganizationKYCFieldsTest.kt`
- `stellar-sdk/src/commonTest/kotlin/com/soneso/stellar/sdk/sep/sep09/FinancialAccountKYCFieldsTest.kt`
- `stellar-sdk/src/commonTest/kotlin/com/soneso/stellar/sdk/sep/sep09/CardKYCFieldsTest.kt`

## Coverage by Section

| Section | Coverage | Implemented | Total |
|---------|----------|-------------|-------|
| Card Fields | 100.0% | 11 | 11 |
| Financial Account Fields | 100.0% | 14 | 14 |
| Natural Person Fields - Binary | 100.0% | 6 | 6 |
| Natural Person Fields - Text | 100.0% | 28 | 28 |
| Organization Fields - Binary | 100.0% | 2 | 2 |
| Organization Fields - Text | 100.0% | 15 | 15 |

## Detailed Field Comparison

### Card Fields

| Field | Required | Status | SDK Property | Description |
|-------|----------|--------|--------------|-------------|
| `card.number` |  | ✅ | `CardKYCFields.number` | Card number |
| `card.expiration_date` |  | ✅ | `CardKYCFields.expirationDate` | Expiration in YY-MM format (e.g., 29-11 for November 2029) |
| `card.cvc` |  | ✅ | `CardKYCFields.cvc` | CVC number (digits on the back of the card) |
| `card.holder_name` |  | ✅ | `CardKYCFields.holderName` | Name of the card holder |
| `card.network` |  | ✅ | `CardKYCFields.network` | Brand of the card/network (Visa, Mastercard, AmEx, etc.) |
| `card.postal_code` |  | ✅ | `CardKYCFields.postalCode` | Billing address postal code |
| `card.country_code` |  | ✅ | `CardKYCFields.countryCode` | Billing address country code in ISO 3166-1 alpha-2 (e.g., US) |
| `card.state_or_province` |  | ✅ | `CardKYCFields.stateOrProvince` | Billing address state/province in ISO 3166-2 format |
| `card.city` |  | ✅ | `CardKYCFields.city` | Billing address city |
| `card.address` |  | ✅ | `CardKYCFields.address` | Entire billing address as a multi-line string |
| `card.token` |  | ✅ | `CardKYCFields.token` | Token representation from external payment system (e.g., Stripe) |

### Financial Account Fields

| Field | Required | Status | SDK Property | Description |
|-------|----------|--------|--------------|-------------|
| `bank_name` |  | ✅ | `FinancialAccountKYCFields.bankName` | Name of the bank |
| `bank_account_type` |  | ✅ | `FinancialAccountKYCFields.bankAccountType` | Type of bank account (checking or savings) |
| `bank_account_number` |  | ✅ | `FinancialAccountKYCFields.bankAccountNumber` | Number identifying bank account |
| `bank_number` |  | ✅ | `FinancialAccountKYCFields.bankNumber` | Number identifying bank (routing number in US) |
| `bank_phone_number` |  | ✅ | `FinancialAccountKYCFields.bankPhoneNumber` | Phone number with country code for bank |
| `bank_branch_number` |  | ✅ | `FinancialAccountKYCFields.bankBranchNumber` | Number identifying bank branch |
| `external_transfer_memo` |  | ✅ | `FinancialAccountKYCFields.externalTransferMemo` | Destination tag/memo used to identify a transaction |
| `clabe_number` |  | ✅ | `FinancialAccountKYCFields.clabeNumber` | Bank account number for Mexico (CLABE system) |
| `cbu_number` |  | ✅ | `FinancialAccountKYCFields.cbuNumber` | Clave Bancaria Uniforme (CBU) or Clave Virtual Uniforme (CVU) for Argentina |
| `cbu_alias` |  | ✅ | `FinancialAccountKYCFields.cbuAlias` | Alias for a CBU or CVU account |
| `mobile_money_number` |  | ✅ | `FinancialAccountKYCFields.mobileMoneyNumber` | Mobile phone number in E.164 format for mobile money account |
| `mobile_money_provider` |  | ✅ | `FinancialAccountKYCFields.mobileMoneyProvider` | Name of the mobile money service provider |
| `crypto_address` |  | ✅ | `FinancialAccountKYCFields.cryptoAddress` | Address for a cryptocurrency account |
| `crypto_memo` |  | ✅ | `FinancialAccountKYCFields.cryptoMemo` | Destination tag/memo for crypto transactions (deprecated, use external_transfer_memo) |

### Natural Person Fields - Binary

| Field | Required | Status | SDK Property | Description |
|-------|----------|--------|--------------|-------------|
| `photo_id_front` |  | ✅ | `NaturalPersonKYCFields.photoIdFront` | Image of front of photo ID or passport |
| `photo_id_back` |  | ✅ | `NaturalPersonKYCFields.photoIdBack` | Image of back of photo ID or passport |
| `notary_approval_of_photo_id` |  | ✅ | `NaturalPersonKYCFields.notaryApprovalOfPhotoId` | Image of notary's approval of photo ID |
| `photo_proof_residence` |  | ✅ | `NaturalPersonKYCFields.photoProofResidence` | Image of utility bill or bank statement with name and address |
| `proof_of_income` |  | ✅ | `NaturalPersonKYCFields.proofOfIncome` | Image of proof of income document |
| `proof_of_liveness` |  | ✅ | `NaturalPersonKYCFields.proofOfLiveness` | Video or image file as liveness proof |

### Natural Person Fields - Text

| Field | Required | Status | SDK Property | Description |
|-------|----------|--------|--------------|-------------|
| `last_name` |  | ✅ | `NaturalPersonKYCFields.lastName` | Family or last name |
| `first_name` |  | ✅ | `NaturalPersonKYCFields.firstName` | Given or first name |
| `additional_name` |  | ✅ | `NaturalPersonKYCFields.additionalName` | Middle name or other additional name |
| `address_country_code` |  | ✅ | `NaturalPersonKYCFields.addressCountryCode` | Country code for current address (ISO 3166-1 alpha-3) |
| `state_or_province` |  | ✅ | `NaturalPersonKYCFields.stateOrProvince` | Name of state/province/region/prefecture |
| `city` |  | ✅ | `NaturalPersonKYCFields.city` | Name of city/town |
| `postal_code` |  | ✅ | `NaturalPersonKYCFields.postalCode` | Postal or other code identifying user's locale |
| `address` |  | ✅ | `NaturalPersonKYCFields.address` | Entire address as a multi-line string |
| `mobile_number` |  | ✅ | `NaturalPersonKYCFields.mobileNumber` | Mobile phone number with country code in E.164 format |
| `mobile_number_format` |  | ✅ | `NaturalPersonKYCFields.mobileNumberFormat` | Expected format of mobile_number field (default: E.164) |
| `email_address` |  | ✅ | `NaturalPersonKYCFields.emailAddress` | Email address |
| `birth_date` |  | ✅ | `NaturalPersonKYCFields.birthDate` | Date of birth (ISO 8601 date-only: YYYY-MM-DD) |
| `birth_place` |  | ✅ | `NaturalPersonKYCFields.birthPlace` | Place of birth (city, state, country as on passport) |
| `birth_country_code` |  | ✅ | `NaturalPersonKYCFields.birthCountryCode` | ISO code of country of birth (ISO 3166-1 alpha-3) |
| `tax_id` |  | ✅ | `NaturalPersonKYCFields.taxId` | Tax identifier of user in their country (e.g., SSN in US) |
| `tax_id_name` |  | ✅ | `NaturalPersonKYCFields.taxIdName` | Name of the tax ID (e.g., SSN or ITIN in US) |
| `occupation` |  | ✅ | `NaturalPersonKYCFields.occupation` | Occupation ISCO08 code (3 characters) |
| `employer_name` |  | ✅ | `NaturalPersonKYCFields.employerName` | Name of employer |
| `employer_address` |  | ✅ | `NaturalPersonKYCFields.employerAddress` | Address of employer |
| `language_code` |  | ✅ | `NaturalPersonKYCFields.languageCode` | Primary language (ISO 639-1, 2 characters) |
| `id_type` |  | ✅ | `NaturalPersonKYCFields.idType` | Type of ID (passport, drivers_license, id_card, etc.) |
| `id_country_code` |  | ✅ | `NaturalPersonKYCFields.idCountryCode` | Country issuing ID (ISO 3166-1 alpha-3) |
| `id_issue_date` |  | ✅ | `NaturalPersonKYCFields.idIssueDate` | ID issue date (ISO 8601 date-only: YYYY-MM-DD) |
| `id_expiration_date` |  | ✅ | `NaturalPersonKYCFields.idExpirationDate` | ID expiration date (ISO 8601 date-only: YYYY-MM-DD) |
| `id_number` |  | ✅ | `NaturalPersonKYCFields.idNumber` | Passport or ID number |
| `ip_address` |  | ✅ | `NaturalPersonKYCFields.ipAddress` | IP address of customer's computer |
| `sex` |  | ✅ | `NaturalPersonKYCFields.sex` | Gender (male, female, or other) |
| `referral_id` |  | ✅ | `NaturalPersonKYCFields.referralId` | User's origin or referral code |

### Organization Fields - Binary

| Field | Required | Status | SDK Property | Description |
|-------|----------|--------|--------------|-------------|
| `organization.photo_incorporation_doc` |  | ✅ | `OrganizationKYCFields.photoIncorporationDoc` | Image of incorporation documents |
| `organization.photo_proof_address` |  | ✅ | `OrganizationKYCFields.photoProofAddress` | Image of utility bill or bank statement with organization's name and address |

### Organization Fields - Text

| Field | Required | Status | SDK Property | Description |
|-------|----------|--------|--------------|-------------|
| `organization.name` |  | ✅ | `OrganizationKYCFields.name` | Full organization name as on incorporation papers |
| `organization.VAT_number` |  | ✅ | `OrganizationKYCFields.VATNumber` | Organization VAT number |
| `organization.registration_number` |  | ✅ | `OrganizationKYCFields.registrationNumber` | Organization registration number |
| `organization.registration_date` |  | ✅ | `OrganizationKYCFields.registrationDate` | Date the organization was registered |
| `organization.registered_address` |  | ✅ | `OrganizationKYCFields.registeredAddress` | Organization registered address |
| `organization.number_of_shareholders` |  | ✅ | `OrganizationKYCFields.numberOfShareholders` | Organization shareholder number |
| `organization.shareholder_name` |  | ✅ | `OrganizationKYCFields.shareholderName` | Shareholder name (can be organization or person) |
| `organization.address_country_code` |  | ✅ | `OrganizationKYCFields.addressCountryCode` | Country code for address (ISO 3166-1 alpha-3) |
| `organization.state_or_province` |  | ✅ | `OrganizationKYCFields.stateOrProvince` | Name of state/province/region/prefecture |
| `organization.city` |  | ✅ | `OrganizationKYCFields.city` | Name of city/town |
| `organization.postal_code` |  | ✅ | `OrganizationKYCFields.postalCode` | Postal code identifying organization's locale |
| `organization.director_name` |  | ✅ | `OrganizationKYCFields.directorName` | Organization registered managing director |
| `organization.website` |  | ✅ | `OrganizationKYCFields.website` | Organization website URL |
| `organization.email` |  | ✅ | `OrganizationKYCFields.email` | Organization contact email |
| `organization.phone` |  | ✅ | `OrganizationKYCFields.phone` | Organization contact phone |

## Legend

- ✅ **Implemented**: Field is fully supported in the SDK
- ❌ **Not Implemented**: Field is not currently supported
- ⚠️ **Partial**: Field is partially supported with limitations

## Additional Information

**Documentation:** See `docs/sep-implementations.md` for usage examples and API reference

**Specification:** [SEP-0009](https://github.com/stellar/stellar-protocol/blob/master/ecosystem/sep-0009.md)

**Implementation Package:** `com.soneso.stellar.sdk.sep.sep0009`
