# v1.1.1: release is minified with R8 (shrink + optimise). Names are kept (no obfuscation) so crash reports stay
# readable, and the app's own classes are kept whole: Room entities, kotlinx-serialization DTOs, Hilt workers and
# Retrofit interfaces are reached by reflection/generated code. Libraries bring their own consumer rules.
-dontobfuscate
-keep class com.animeshadow.app.** { *; }
-keepattributes Signature, InnerClasses, EnclosingMethod, *Annotation*, KotlinMetadata

# Retrofit suspend functions
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
