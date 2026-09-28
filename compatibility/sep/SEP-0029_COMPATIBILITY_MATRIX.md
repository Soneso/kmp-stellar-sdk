# SEP-0029 (Account Memo Requirements) Compatibility Matrix

**Generated:** 2026-09-28 17:10:39

**SEP Version:** 0.5.0  
**SEP Status:** Active  
**SDK Version:** 1.14.0  
**SEP URL:** https://github.com/stellar/stellar-protocol/blob/master/ecosystem/sep-0029.md

## SEP Summary

An account signals that incoming payments must carry a memo by setting the data entry `config.memo_required` to `1`. Before submitting a transaction without a memo, the sender loads the destination of every payment, path payment and account merge operation and refuses to submit when one of them requires a memo. Multiplexed destinations are exempt, because the multiplexing id already identifies the recipient.

## Overall Coverage

**Total Coverage:** 100.0% (13/13 fields)

- ✅ **Implemented:** 13/13
- ❌ **Not Implemented:** 0/13

**Required Fields:** 100.0% (13/13)

**Optional Fields:** 100% (0/0)

## Implementation Status

✅ **Fully Implemented**

## Coverage by Section

| Section | Coverage | Required | Implemented | Total |
|---------|----------|----------|-------------|-------|
| Memo Requirement Flag | 100.0% | 2/2 | 2 | 2 |
| Sender-Side Check | 100.0% | 8/8 | 8 | 8 |
| Submission Integration | 100.0% | 3/3 | 3 | 3 |

## Detailed Field Comparison

### Memo Requirement Flag

| Field | Required | Status | SDK Property | Description |
|-------|----------|--------|--------------|-------------|
| `memo_required_data_entry` | ✓ | ✅ | `Sep29Checker.accountRequiresMemo` | Reads the destination account's config.memo_required data entry and compares it with the value 1 |
| `set_memo_required_flag` | ✓ | ✅ | `ManageDataOperation` | Sets or removes the data entry with a manage data operation |

### Sender-Side Check

| Field | Required | Status | SDK Property | Description |
|-------|----------|--------|--------------|-------------|
| `payment_destination` | ✓ | ✅ | `Sep29Checker (OperationBodyXdr.PaymentOp)` | Checks the destination of a PAYMENT operation |
| `path_payment_strict_send_destination` | ✓ | ✅ | `Sep29Checker (OperationBodyXdr.PathPaymentStrictSendOp)` | Checks the destination of a PATH_PAYMENT_STRICT_SEND operation |
| `path_payment_strict_receive_destination` | ✓ | ✅ | `Sep29Checker (OperationBodyXdr.PathPaymentStrictReceiveOp)` | Checks the destination of a PATH_PAYMENT_STRICT_RECEIVE operation |
| `account_merge_destination` | ✓ | ✅ | `Sep29Checker (OperationBodyXdr.Destination)` | Checks the destination of a MERGE_ACCOUNT operation |
| `muxed_destination_exempt` | ✓ | ✅ | `Sep29Checker (MuxedAccountXdr.Ed25519 only)` | Skips multiplexed destinations, whose id already identifies the recipient |
| `memo_present_skips_lookup` | ✓ | ✅ | `Sep29Checker (memo short-circuit)` | Makes no account lookup when the transaction carries a memo |
| `fee_bump_inner_transaction` | ✓ | ✅ | `Sep29Checker (FeeBump innerTx)` | Reads the memo and operations of a fee bump envelope from its inner transaction |
| `unknown_destination_skipped` | ✓ | ✅ | `Sep29Checker (HTTP 404 skipped)` | Skips a destination Horizon does not know (HTTP 404) and lets the network report it |

### Submission Integration

| Field | Required | Status | SDK Property | Description |
|-------|----------|--------|--------------|-------------|
| `submit_transaction_opt_out` | ✓ | ✅ | `HorizonServer.submitTransaction(skipMemoRequiredCheck)` | submitTransaction runs the check before submitting unless skipMemoRequiredCheck is true |
| `submit_transaction_async_opt_out` | ✓ | ✅ | `HorizonServer.submitTransactionAsync(skipMemoRequiredCheck)` | submitTransactionAsync runs the check before submitting unless skipMemoRequiredCheck is true |
| `account_requires_memo_exception` | ✓ | ✅ | `AccountRequiresMemoException` | Refuses submission with an exception naming the account and the operation index |

## Implementation Gaps

No gaps found! All fields are implemented.

## Recommendations

The SDK has full compatibility with SEP-0029!

## Legend

- ✅ **Implemented**: Field is fully supported in the SDK
- ❌ **Not Implemented**: Field is not currently supported
- ⚠️ **Partial**: Field is partially supported with limitations
- **Server**: Server-side only feature (not applicable to client SDKs)
- ✓ **Required**: Field is required by SEP specification

## Additional Information

**Documentation:** See `docs/sep/README.md` for usage examples and API reference

**Specification:** [SEP-0029](https://github.com/stellar/stellar-protocol/blob/master/ecosystem/sep-0029.md)

**Implementation Package:** `com.soneso.stellar.sdk.horizon`
