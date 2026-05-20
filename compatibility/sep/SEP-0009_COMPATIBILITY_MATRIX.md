# SEP-0009 (Standard KYC Fields) Compatibility Matrix

**Generated:** 2026-05-20 11:38:48

**SEP Version:** 1.17.0  
**SEP Status:** Active  
**SDK Version:** 1.6.0  
**SEP URL:** https://github.com/stellar/stellar-protocol/blob/master/ecosystem/sep-0009.md

## SEP Summary

A standard list of KYC and financial account fields (names, addresses, ID documents, bank details) used across Stellar ecosystem protocols.

## Overall Coverage

**Total Coverage:** 100.0% (76/76 fields)

- ✅ **Implemented:** 76/76
- ❌ **Not Implemented:** 0/76

**Required Fields:** 100% (0/0)

**Optional Fields:** 100.0% (76/76)

## Implementation Status

✅ **Fully Implemented**

## Coverage by Section

| Section | Coverage | Required | Implemented | Total |
|---------|----------|----------|-------------|-------|
| Card Fields | 100.0% | N/A | 11 | 11 |
| Financial Account Fields | 100.0% | N/A | 14 | 14 |
| Natural Person Fields | 100.0% | N/A | 34 | 34 |
| Organization Fields | 100.0% | N/A | 17 | 17 |

## Detailed Field Comparison

### Card Fields

| Field | Required | Status | SDK Property | Description |
|-------|----------|--------|--------------|-------------|
| `card.number` |  | ✅ | `number` | Card number |
| `card.expiration_date` |  | ✅ | `expirationDate` | Expiration month and year in YY-MM format (e.g. `29-11`, November 2029) |
| `card.cvc` |  | ✅ | `cvc` | CVC number (Digits on the back of the card) |
| `card.holder_name` |  | ✅ | `holderName` | Name of the card holder |
| `card.network` |  | ✅ | `network` | Brand of the card/network it operates within (e.g. Visa, Mastercard, AmEx, etc.) |
| `card.postal_code` |  | ✅ | `postalCode` | Billing address postal code |
| `card.country_code` |  | ✅ | `countryCode` | Billing address country code in ISO 3166-1 alpha-2 code (e.g. US) |
| `card.state_or_province` |  | ✅ | `stateOrProvince` | Name of state/province/region/prefecture is ISO 3166-2 format |
| `card.city` |  | ✅ | `city` | Name of city/town |
| `card.address` |  | ✅ | `address` | Entire address (country, state, postal code, street address, etc...) as a multi-line string |
| `card.token` |  | ✅ | `token` | Token representation of the card in some external payment system (e.g. Stripe) |

### Financial Account Fields

| Field | Required | Status | SDK Property | Description |
|-------|----------|--------|--------------|-------------|
| `bank_name` |  | ✅ | `bankName` | Name of the bank. May be necessary in regions that don't have a unified routing system. |
| `bank_account_type` |  | ✅ | `bankAccountType` | `checking` or `savings` |
| `bank_account_number` |  | ✅ | `bankAccountNumber` | Number identifying bank account |
| `bank_number` |  | ✅ | `bankNumber` | Number identifying bank in national banking system (routing number in US) |
| `bank_phone_number` |  | ✅ | `bankPhoneNumber` | Phone number with country code for bank |
| `bank_branch_number` |  | ✅ | `bankBranchNumber` | Number identifying bank branch |
| `external_transfer_memo` |  | ✅ | `externalTransferMemo` | A destination tag/memo used to identify a transaction |
| `clabe_number` |  | ✅ | `clabeNumber` | Bank account number for Mexico |
| `cbu_number` |  | ✅ | `cbuNumber` | Clave Bancaria Uniforme (CBU) or Clave Virtual Uniforme (CVU). |
| `cbu_alias` |  | ✅ | `cbuAlias` | The alias for a Clave Bancaria Uniforme (CBU) or Clave Virtual Uniforme (CVU). |
| `mobile_money_number` |  | ✅ | `mobileMoneyNumber` | Mobile phone number in `E.164` format with which a mobile money account is associated. Note that ... |
| `mobile_money_provider` |  | ✅ | `mobileMoneyProvider` | Name of the mobile money service provider. |
| `crypto_address` |  | ✅ | `cryptoAddress` | Address for a cryptocurrency account |
| `crypto_memo` |  | ✅ | `cryptoMemo` | (**deprecated**, use `external_transfer_memo` instead) A destination tag/memo used to identify a ... |

### Natural Person Fields

| Field | Required | Status | SDK Property | Description |
|-------|----------|--------|--------------|-------------|
| `family_name` |  | ✅ | `lastName` | Family or last name |
| `given_name` |  | ✅ | `firstName` | Given or first name |
| `additional_name` |  | ✅ | `additionalName` | Middle name or other additional name |
| `address_country_code` |  | ✅ | `addressCountryCode` | country code for current address |
| `state_or_province` |  | ✅ | `stateOrProvince` | name of state/province/region/prefecture |
| `city` |  | ✅ | `city` | name of city/town |
| `postal_code` |  | ✅ | `postalCode` | Postal or other code identifying user's locale |
| `address` |  | ✅ | `address` | Entire address (country, state, postal code, street address, etc...) as a multi-line string |
| `mobile_number` |  | ✅ | `mobileNumber` | Mobile phone number with country code, in `E.164` format unless specified differently on `mobile_... |
| `mobile_number_format` |  | ✅ | `mobileNumberFormat` | Expected format of the `mobile_number` field. E.g.: `E.164`, `hash`, etc... In case this field is... |
| `email_address` |  | ✅ | `emailAddress` | Email address |
| `birth_date` |  | ✅ | `birthDate` | Date of birth, e.g. `1976-07-04` |
| `birth_place` |  | ✅ | `birthPlace` | Place of birth (city, state, country; as on passport) |
| `birth_country_code` |  | ✅ | `birthCountryCode` | ISO Code of country of birth |
| `tax_id` |  | ✅ | `taxId` | Tax identifier of user in their country (social security number in US) |
| `tax_id_name` |  | ✅ | `taxIdName` | Name of the tax ID (`SSN` or `ITIN` in the US) |
| `occupation` |  | ✅ | `occupation` | Occupation ISCO code |
| `employer_name` |  | ✅ | `employerName` | Name of employer |
| `employer_address` |  | ✅ | `employerAddress` | Address of employer |
| `language_code` |  | ✅ | `languageCode` | primary language |
| `id_type` |  | ✅ | `idType` | `passport`, `drivers_license`, `id_card`, etc... |
| `id_country_code` |  | ✅ | `idCountryCode` | country issuing passport or photo ID as ISO 3166-1 alpha-3 code |
| `id_issue_date` |  | ✅ | `idIssueDate` | ID issue date |
| `id_expiration_date` |  | ✅ | `idExpirationDate` | ID expiration date |
| `id_number` |  | ✅ | `idNumber` | Passport or ID number |
| `photo_id_front` |  | ✅ | `photoIdFront` | Image of front of user's photo ID or passport |
| `photo_id_back` |  | ✅ | `photoIdBack` | Image of back of user's photo ID or passport |
| `notary_approval_of_photo_id` |  | ✅ | `notaryApprovalOfPhotoId` | Image of notary's approval of photo ID or passport |
| `ip_address` |  | ✅ | `ipAddress` | IP address of customer's computer |
| `photo_proof_residence` |  | ✅ | `photoProofResidence` | Image of a utility bill, bank statement or similar with the user's name and address |
| `sex` |  | ✅ | `sex` | `male`, `female`, or `other` |
| `proof_of_income` |  | ✅ | `proofOfIncome` | Image of user's proof of income document |
| `proof_of_liveness` |  | ✅ | `proofOfLiveness` | video or image file of user as a liveness proof |
| `referral_id` |  | ✅ | `referralId` | User's origin (such as an id in another application) or a referral code |

### Organization Fields

| Field | Required | Status | SDK Property | Description |
|-------|----------|--------|--------------|-------------|
| `organization.name` |  | ✅ | `name` | Full organization name as on the incorporation |
| `organization.VAT_number` |  | ✅ | `VATNumber` | Organization VAT number |
| `organization.registration_number` |  | ✅ | `registrationNumber` | Organization registration |
| `organization.registration_date` |  | ✅ | `registrationDate` | Date the organization was registered |
| `organization.registered_address` |  | ✅ | `registeredAddress` | Organization registered address |
| `organization.number_of_shareholders` |  | ✅ | `numberOfShareholders` | Organization shareholder number |
| `organization.shareholder_name` |  | ✅ | `shareholderName` | Can be an organization or a person |
| `organization.photo_incorporation_doc` |  | ✅ | `photoIncorporationDoc` | Image of incorporation documents |
| `organization.photo_proof_address` |  | ✅ | `photoProofAddress` | Image of a utility bill, bank statement with the organization's name and address |
| `organization.address_country_code` |  | ✅ | `addressCountryCode` | country code for current address |
| `organization.state_or_province` |  | ✅ | `stateOrProvince` | name of state/province/region/prefecture |
| `organization.city` |  | ✅ | `city` | name of city/town |
| `organization.postal_code` |  | ✅ | `postalCode` | Postal or other code identifying organization's locale |
| `organization.director_name` |  | ✅ | `directorName` | Organization registered managing director |
| `organization.website` |  | ✅ | `website` | Organization website |
| `organization.email` |  | ✅ | `email` | Organization contact email |
| `organization.phone` |  | ✅ | `phone` | Organization contact phone |

## Implementation Gaps

No gaps found! All fields are implemented.

## Recommendations

The SDK has full compatibility with SEP-0009!

## Legend

- ✅ **Implemented**: Field is fully supported in the SDK
- ❌ **Not Implemented**: Field is not currently supported
- ⚠️ **Partial**: Field is partially supported with limitations
- **Server**: Server-side only feature (not applicable to client SDKs)
- ✓ **Required**: Field is required by SEP specification

## Additional Information

**Documentation:** See `docs/sep-implementations.md` for usage examples and API reference

**Specification:** [SEP-0009](https://github.com/stellar/stellar-protocol/blob/master/ecosystem/sep-0009.md)

**Implementation Package:** `com.soneso.stellar.sdk.sep.sep0009`
