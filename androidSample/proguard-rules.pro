# Stellar KMP SDK ProGuard Rules

# BouncyCastle - Keep crypto classes
-keep class org.bouncycastle.** { *; }
-dontwarn org.bouncycastle.**

# Stellar SDK - Keep public API
-keep class com.stellar.sdk.** { *; }
-keepclassmembers class com.stellar.sdk.** { *; }

# Kotlin serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

# Keep Kotlin metadata
-keep class kotlin.Metadata { *; }
