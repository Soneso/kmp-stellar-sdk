// Test fixture: Typedef types
// Covers wrapping primitives, opaque, strings, typedef chains, and array typedefs.

typedef int Int32;
typedef unsigned int Uint32;
typedef hyper Int64;
typedef unsigned hyper Uint64;
typedef opaque DataBlob<>;
typedef string ShortString<64>;

// Typedef chain: Int32 -> SignedCount -> MyCounter
typedef Int32 SignedCount;
typedef SignedCount MyCounter;

// Variable-length array typedef
typedef int IntList<100>;
