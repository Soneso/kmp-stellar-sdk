// Test fixture: Union types
// Covers enum discriminant, void arms, non-void arms, default arm,
// multiple discriminant values for the same arm.

enum ResultType
{
    RESULT_OK = 0,
    RESULT_ERROR = 1,
    RESULT_PENDING = 2,
    RESULT_UNKNOWN = 3
};

struct ErrorDetail
{
    int code;
    string message<256>;
};

// Union with enum discriminant, void arm, non-void arms, and default arm
union Result switch (ResultType type)
{
case RESULT_OK:
    void;
case RESULT_ERROR:
    ErrorDetail error;
case RESULT_PENDING:
case RESULT_UNKNOWN:
    // Pending and unknown share the same void representation
    void;
default:
    int fallbackCode;
};

// Union without default arm (strict union)
enum SimpleStatus
{
    ACTIVE = 0,
    INACTIVE = 1
};

union SimpleResult switch (SimpleStatus status)
{
case ACTIVE:
    int successCode;
case INACTIVE:
    string errorMessage<256>;
};

// Union with non-void default arm and multiple cases per arm
union MultiCaseUnion switch (int tag)
{
case 0:
    int simpleValue;
case 1:
case 2:
    string textValue<128>;
default:
    opaque rawValue<>;
};
