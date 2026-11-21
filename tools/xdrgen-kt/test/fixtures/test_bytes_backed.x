// Test fixture: bytes-backed string fields.
// Declares the type and field names BYTES_BACKED_STRING_FIELDS lists, cut down
// to the members that matter, so the snapshots pin both emissions at exactly the
// listed positions. The str arm shares the SCString typedef with the overridden
// executable_tag arm, which shows the :bytes override applying per field, not
// per typedef. SCString and SCSymbol take the :text emission as typedefs, and
// the text arm of Memo as an arm class beside an arm that keeps its data class.

const SCSYMBOL_LIMIT = 32;

typedef string SCString<>;
typedef string SCSymbol<SCSYMBOL_LIMIT>;

enum SCValType
{
    SCV_STRING = 14,
    SCV_SYMBOL = 15,
    SCV_EXECUTABLE_TAG = 22
};

union SCVal switch (SCValType type)
{
case SCV_STRING:
    SCString str;
case SCV_SYMBOL:
    SCSymbol sym;
case SCV_EXECUTABLE_TAG:
    SCString executable_tag;
};

struct ContractExecutableExternalRef
{
    opaque executable_owner[32];
    SCString tag;
};

enum MemoType
{
    MEMO_NONE = 0,
    MEMO_TEXT = 1,
    MEMO_ID = 2
};

union Memo switch (MemoType type)
{
case MEMO_NONE:
    void;
case MEMO_TEXT:
    string text<28>;
case MEMO_ID:
    Uint64 id;
};
