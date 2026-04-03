// Test fixture: Struct types
// Covers primitives, optionals, fixed/variable arrays, opaque, strings, nested types.

typedef opaque Hash[32];

struct SimpleStruct
{
    int count;
    unsigned int flags;
    hyper bigNumber;
    unsigned hyper unsignedBig;
    bool active;
    string label<64>;
};

// Struct with a Kotlin keyword field name to test backtick escaping
struct KeywordStruct
{
    int val;
    string when<32>;
};

struct NestedStruct
{
    SimpleStruct inner;
    SimpleStruct* optionalInner;
    SimpleStruct items<10>;
    SimpleStruct fixedItems[3];
    opaque rawData<>;
    opaque fixedData[4];
    Hash hash;
};
