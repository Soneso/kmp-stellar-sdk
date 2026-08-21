// Test fixture: bytes-backed string fields.
// Declares the type and field names BYTES_BACKED_STRING_FIELDS lists, cut down
// to the members that matter, so the snapshots pin the ByteArray emission at
// exactly the listed positions. The str arm shares the SCString typedef with
// the overridden arm, which shows the override applying per field, not per
// typedef.

typedef string SCString<>;

enum SCValType
{
    SCV_STRING = 14,
    SCV_EXECUTABLE_TAG = 22
};

union SCVal switch (SCValType type)
{
case SCV_STRING:
    SCString str;
case SCV_EXECUTABLE_TAG:
    SCString executable_tag;
};

struct ContractExecutableExternalRef
{
    opaque executable_owner[32];
    SCString tag;
};
