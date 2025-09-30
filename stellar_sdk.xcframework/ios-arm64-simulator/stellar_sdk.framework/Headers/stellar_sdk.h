#import <Foundation/NSArray.h>
#import <Foundation/NSDictionary.h>
#import <Foundation/NSError.h>
#import <Foundation/NSObject.h>
#import <Foundation/NSSet.h>
#import <Foundation/NSString.h>
#import <Foundation/NSValue.h>

@class Stellar_sdkKeyPairCompanion, Stellar_sdkKotlinByteArray, Stellar_sdkKotlinCharArray, Stellar_sdkKeyPair, Stellar_sdkStellarSdk, Stellar_sdkStrKey, Stellar_sdkKotlinByteIterator, Stellar_sdkKotlinCharIterator;

@protocol Stellar_sdkEd25519Crypto, Stellar_sdkKotlinIterator;

NS_ASSUME_NONNULL_BEGIN
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wunknown-warning-option"
#pragma clang diagnostic ignored "-Wincompatible-property-type"
#pragma clang diagnostic ignored "-Wnullability"

#pragma push_macro("_Nullable_result")
#if !__has_feature(nullability_nullable_result)
#undef _Nullable_result
#define _Nullable_result _Nullable
#endif

__attribute__((swift_name("KotlinBase")))
@interface Stellar_sdkBase : NSObject
- (instancetype)init __attribute__((unavailable));
+ (instancetype)new __attribute__((unavailable));
+ (void)initialize __attribute__((objc_requires_super));
@end

@interface Stellar_sdkBase (Stellar_sdkBaseCopying) <NSCopying>
@end

__attribute__((swift_name("KotlinMutableSet")))
@interface Stellar_sdkMutableSet<ObjectType> : NSMutableSet<ObjectType>
@end

__attribute__((swift_name("KotlinMutableDictionary")))
@interface Stellar_sdkMutableDictionary<KeyType, ObjectType> : NSMutableDictionary<KeyType, ObjectType>
@end

@interface NSError (NSErrorStellar_sdkKotlinException)
@property (readonly) id _Nullable kotlinException;
@end

__attribute__((swift_name("KotlinNumber")))
@interface Stellar_sdkNumber : NSNumber
- (instancetype)initWithChar:(char)value __attribute__((unavailable));
- (instancetype)initWithUnsignedChar:(unsigned char)value __attribute__((unavailable));
- (instancetype)initWithShort:(short)value __attribute__((unavailable));
- (instancetype)initWithUnsignedShort:(unsigned short)value __attribute__((unavailable));
- (instancetype)initWithInt:(int)value __attribute__((unavailable));
- (instancetype)initWithUnsignedInt:(unsigned int)value __attribute__((unavailable));
- (instancetype)initWithLong:(long)value __attribute__((unavailable));
- (instancetype)initWithUnsignedLong:(unsigned long)value __attribute__((unavailable));
- (instancetype)initWithLongLong:(long long)value __attribute__((unavailable));
- (instancetype)initWithUnsignedLongLong:(unsigned long long)value __attribute__((unavailable));
- (instancetype)initWithFloat:(float)value __attribute__((unavailable));
- (instancetype)initWithDouble:(double)value __attribute__((unavailable));
- (instancetype)initWithBool:(BOOL)value __attribute__((unavailable));
- (instancetype)initWithInteger:(NSInteger)value __attribute__((unavailable));
- (instancetype)initWithUnsignedInteger:(NSUInteger)value __attribute__((unavailable));
+ (instancetype)numberWithChar:(char)value __attribute__((unavailable));
+ (instancetype)numberWithUnsignedChar:(unsigned char)value __attribute__((unavailable));
+ (instancetype)numberWithShort:(short)value __attribute__((unavailable));
+ (instancetype)numberWithUnsignedShort:(unsigned short)value __attribute__((unavailable));
+ (instancetype)numberWithInt:(int)value __attribute__((unavailable));
+ (instancetype)numberWithUnsignedInt:(unsigned int)value __attribute__((unavailable));
+ (instancetype)numberWithLong:(long)value __attribute__((unavailable));
+ (instancetype)numberWithUnsignedLong:(unsigned long)value __attribute__((unavailable));
+ (instancetype)numberWithLongLong:(long long)value __attribute__((unavailable));
+ (instancetype)numberWithUnsignedLongLong:(unsigned long long)value __attribute__((unavailable));
+ (instancetype)numberWithFloat:(float)value __attribute__((unavailable));
+ (instancetype)numberWithDouble:(double)value __attribute__((unavailable));
+ (instancetype)numberWithBool:(BOOL)value __attribute__((unavailable));
+ (instancetype)numberWithInteger:(NSInteger)value __attribute__((unavailable));
+ (instancetype)numberWithUnsignedInteger:(NSUInteger)value __attribute__((unavailable));
@end

__attribute__((swift_name("KotlinByte")))
@interface Stellar_sdkByte : Stellar_sdkNumber
- (instancetype)initWithChar:(char)value;
+ (instancetype)numberWithChar:(char)value;
@end

__attribute__((swift_name("KotlinUByte")))
@interface Stellar_sdkUByte : Stellar_sdkNumber
- (instancetype)initWithUnsignedChar:(unsigned char)value;
+ (instancetype)numberWithUnsignedChar:(unsigned char)value;
@end

__attribute__((swift_name("KotlinShort")))
@interface Stellar_sdkShort : Stellar_sdkNumber
- (instancetype)initWithShort:(short)value;
+ (instancetype)numberWithShort:(short)value;
@end

__attribute__((swift_name("KotlinUShort")))
@interface Stellar_sdkUShort : Stellar_sdkNumber
- (instancetype)initWithUnsignedShort:(unsigned short)value;
+ (instancetype)numberWithUnsignedShort:(unsigned short)value;
@end

__attribute__((swift_name("KotlinInt")))
@interface Stellar_sdkInt : Stellar_sdkNumber
- (instancetype)initWithInt:(int)value;
+ (instancetype)numberWithInt:(int)value;
@end

__attribute__((swift_name("KotlinUInt")))
@interface Stellar_sdkUInt : Stellar_sdkNumber
- (instancetype)initWithUnsignedInt:(unsigned int)value;
+ (instancetype)numberWithUnsignedInt:(unsigned int)value;
@end

__attribute__((swift_name("KotlinLong")))
@interface Stellar_sdkLong : Stellar_sdkNumber
- (instancetype)initWithLongLong:(long long)value;
+ (instancetype)numberWithLongLong:(long long)value;
@end

__attribute__((swift_name("KotlinULong")))
@interface Stellar_sdkULong : Stellar_sdkNumber
- (instancetype)initWithUnsignedLongLong:(unsigned long long)value;
+ (instancetype)numberWithUnsignedLongLong:(unsigned long long)value;
@end

__attribute__((swift_name("KotlinFloat")))
@interface Stellar_sdkFloat : Stellar_sdkNumber
- (instancetype)initWithFloat:(float)value;
+ (instancetype)numberWithFloat:(float)value;
@end

__attribute__((swift_name("KotlinDouble")))
@interface Stellar_sdkDouble : Stellar_sdkNumber
- (instancetype)initWithDouble:(double)value;
+ (instancetype)numberWithDouble:(double)value;
@end

__attribute__((swift_name("KotlinBoolean")))
@interface Stellar_sdkBoolean : Stellar_sdkNumber
- (instancetype)initWithBool:(BOOL)value;
+ (instancetype)numberWithBool:(BOOL)value;
@end

__attribute__((objc_subclassing_restricted))
__attribute__((swift_name("KeyPair")))
@interface Stellar_sdkKeyPair : Stellar_sdkBase
@property (class, readonly, getter=companion) Stellar_sdkKeyPairCompanion *companion __attribute__((swift_name("companion")));
- (BOOL)canSign __attribute__((swift_name("canSign()")));
- (BOOL)isEqual:(id _Nullable)other __attribute__((swift_name("isEqual(_:)")));
- (NSString *)getAccountId __attribute__((swift_name("getAccountId()")));
- (Stellar_sdkKotlinByteArray *)getPublicKey __attribute__((swift_name("getPublicKey()")));
- (Stellar_sdkKotlinCharArray * _Nullable)getSecretSeed __attribute__((swift_name("getSecretSeed()")));
- (NSUInteger)hash __attribute__((swift_name("hash()")));
- (Stellar_sdkKotlinByteArray *)signData:(Stellar_sdkKotlinByteArray *)data __attribute__((swift_name("sign(data:)")));
- (BOOL)verifyData:(Stellar_sdkKotlinByteArray *)data signature:(Stellar_sdkKotlinByteArray *)signature __attribute__((swift_name("verify(data:signature:)")));
@end

__attribute__((objc_subclassing_restricted))
__attribute__((swift_name("KeyPair.Companion")))
@interface Stellar_sdkKeyPairCompanion : Stellar_sdkBase
+ (instancetype)alloc __attribute__((unavailable));
+ (instancetype)allocWithZone:(struct _NSZone *)zone __attribute__((unavailable));
+ (instancetype)companion __attribute__((swift_name("init()")));
@property (class, readonly, getter=shared) Stellar_sdkKeyPairCompanion *shared __attribute__((swift_name("shared")));

/**
 * @note annotations
 *   kotlin.jvm.JvmStatic
*/
- (Stellar_sdkKeyPair *)fromAccountIdAccountId:(NSString *)accountId __attribute__((swift_name("fromAccountId(accountId:)")));

/**
 * @note annotations
 *   kotlin.jvm.JvmStatic
*/
- (Stellar_sdkKeyPair *)fromPublicKeyPublicKey:(Stellar_sdkKotlinByteArray *)publicKey __attribute__((swift_name("fromPublicKey(publicKey:)")));

/**
 * @note annotations
 *   kotlin.jvm.JvmStatic
*/
- (Stellar_sdkKeyPair *)fromSecretSeedSeed:(Stellar_sdkKotlinByteArray *)seed __attribute__((swift_name("fromSecretSeed(seed:)")));

/**
 * @note annotations
 *   kotlin.jvm.JvmStatic
*/
- (Stellar_sdkKeyPair *)fromSecretSeedSeed_:(Stellar_sdkKotlinCharArray *)seed __attribute__((swift_name("fromSecretSeed(seed_:)")));

/**
 * @note annotations
 *   kotlin.jvm.JvmStatic
*/
- (Stellar_sdkKeyPair *)fromSecretSeedSeed__:(NSString *)seed __attribute__((swift_name("fromSecretSeed(seed__:)")));

/**
 * @note annotations
 *   kotlin.jvm.JvmStatic
*/
- (Stellar_sdkKeyPair *)random __attribute__((swift_name("random()")));
@end

__attribute__((objc_subclassing_restricted))
__attribute__((swift_name("StellarSdk")))
@interface Stellar_sdkStellarSdk : Stellar_sdkBase
+ (instancetype)alloc __attribute__((unavailable));
+ (instancetype)allocWithZone:(struct _NSZone *)zone __attribute__((unavailable));
+ (instancetype)stellarSdk __attribute__((swift_name("init()")));
@property (class, readonly, getter=shared) Stellar_sdkStellarSdk *shared __attribute__((swift_name("shared")));
@property (readonly) NSString *VERSION __attribute__((swift_name("VERSION")));
@end

__attribute__((objc_subclassing_restricted))
__attribute__((swift_name("StrKey")))
@interface Stellar_sdkStrKey : Stellar_sdkBase
+ (instancetype)alloc __attribute__((unavailable));
+ (instancetype)allocWithZone:(struct _NSZone *)zone __attribute__((unavailable));
+ (instancetype)strKey __attribute__((swift_name("init()")));
@property (class, readonly, getter=shared) Stellar_sdkStrKey *shared __attribute__((swift_name("shared")));
- (Stellar_sdkKotlinByteArray *)decodeEd25519PublicKeyData:(NSString *)data __attribute__((swift_name("decodeEd25519PublicKey(data:)")));
- (Stellar_sdkKotlinByteArray *)decodeEd25519SecretSeedData:(Stellar_sdkKotlinCharArray *)data __attribute__((swift_name("decodeEd25519SecretSeed(data:)")));
- (NSString *)encodeEd25519PublicKeyData:(Stellar_sdkKotlinByteArray *)data __attribute__((swift_name("encodeEd25519PublicKey(data:)")));
- (Stellar_sdkKotlinCharArray *)encodeEd25519SecretSeedData:(Stellar_sdkKotlinByteArray *)data __attribute__((swift_name("encodeEd25519SecretSeed(data:)")));
- (BOOL)isValidEd25519PublicKeyAccountId:(NSString *)accountId __attribute__((swift_name("isValidEd25519PublicKey(accountId:)")));
- (BOOL)isValidEd25519SecretSeedSeed:(Stellar_sdkKotlinCharArray *)seed __attribute__((swift_name("isValidEd25519SecretSeed(seed:)")));
@end

__attribute__((swift_name("Ed25519Crypto")))
@protocol Stellar_sdkEd25519Crypto
@required
- (Stellar_sdkKotlinByteArray *)derivePublicKeyPrivateKey:(Stellar_sdkKotlinByteArray *)privateKey __attribute__((swift_name("derivePublicKey(privateKey:)")));
- (Stellar_sdkKotlinByteArray *)generatePrivateKey __attribute__((swift_name("generatePrivateKey()")));
- (Stellar_sdkKotlinByteArray *)signData:(Stellar_sdkKotlinByteArray *)data privateKey:(Stellar_sdkKotlinByteArray *)privateKey __attribute__((swift_name("sign(data:privateKey:)")));
- (BOOL)verifyData:(Stellar_sdkKotlinByteArray *)data signature:(Stellar_sdkKotlinByteArray *)signature publicKey:(Stellar_sdkKotlinByteArray *)publicKey __attribute__((swift_name("verify(data:signature:publicKey:)")));
@end

__attribute__((objc_subclassing_restricted))
__attribute__((swift_name("Ed25519_nativeKt")))
@interface Stellar_sdkEd25519_nativeKt : Stellar_sdkBase
+ (id<Stellar_sdkEd25519Crypto>)getEd25519Crypto __attribute__((swift_name("getEd25519Crypto()")));
@end

__attribute__((objc_subclassing_restricted))
__attribute__((swift_name("KotlinByteArray")))
@interface Stellar_sdkKotlinByteArray : Stellar_sdkBase
+ (instancetype)arrayWithSize:(int32_t)size __attribute__((swift_name("init(size:)")));
+ (instancetype)arrayWithSize:(int32_t)size init:(Stellar_sdkByte *(^)(Stellar_sdkInt *))init __attribute__((swift_name("init(size:init:)")));
+ (instancetype)alloc __attribute__((unavailable));
+ (instancetype)allocWithZone:(struct _NSZone *)zone __attribute__((unavailable));
- (int8_t)getIndex:(int32_t)index __attribute__((swift_name("get(index:)")));
- (Stellar_sdkKotlinByteIterator *)iterator __attribute__((swift_name("iterator()")));
- (void)setIndex:(int32_t)index value:(int8_t)value __attribute__((swift_name("set(index:value:)")));
@property (readonly) int32_t size __attribute__((swift_name("size")));
@end

__attribute__((objc_subclassing_restricted))
__attribute__((swift_name("KotlinCharArray")))
@interface Stellar_sdkKotlinCharArray : Stellar_sdkBase
+ (instancetype)arrayWithSize:(int32_t)size __attribute__((swift_name("init(size:)")));
+ (instancetype)arrayWithSize:(int32_t)size init:(id (^)(Stellar_sdkInt *))init __attribute__((swift_name("init(size:init:)")));
+ (instancetype)alloc __attribute__((unavailable));
+ (instancetype)allocWithZone:(struct _NSZone *)zone __attribute__((unavailable));
- (unichar)getIndex:(int32_t)index __attribute__((swift_name("get(index:)")));
- (Stellar_sdkKotlinCharIterator *)iterator __attribute__((swift_name("iterator()")));
- (void)setIndex:(int32_t)index value:(unichar)value __attribute__((swift_name("set(index:value:)")));
@property (readonly) int32_t size __attribute__((swift_name("size")));
@end

__attribute__((swift_name("KotlinIterator")))
@protocol Stellar_sdkKotlinIterator
@required
- (BOOL)hasNext __attribute__((swift_name("hasNext()")));
- (id _Nullable)next __attribute__((swift_name("next()")));
@end

__attribute__((swift_name("KotlinByteIterator")))
@interface Stellar_sdkKotlinByteIterator : Stellar_sdkBase <Stellar_sdkKotlinIterator>
- (instancetype)init __attribute__((swift_name("init()"))) __attribute__((objc_designated_initializer));
+ (instancetype)new __attribute__((availability(swift, unavailable, message="use object initializers instead")));
- (Stellar_sdkByte *)next __attribute__((swift_name("next()")));
- (int8_t)nextByte __attribute__((swift_name("nextByte()")));
@end

__attribute__((swift_name("KotlinCharIterator")))
@interface Stellar_sdkKotlinCharIterator : Stellar_sdkBase <Stellar_sdkKotlinIterator>
- (instancetype)init __attribute__((swift_name("init()"))) __attribute__((objc_designated_initializer));
+ (instancetype)new __attribute__((availability(swift, unavailable, message="use object initializers instead")));
- (id)next __attribute__((swift_name("next()")));
- (unichar)nextChar __attribute__((swift_name("nextChar()")));
@end

#pragma pop_macro("_Nullable_result")
#pragma clang diagnostic pop
NS_ASSUME_NONNULL_END
