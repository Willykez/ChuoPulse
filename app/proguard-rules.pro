# ====================================================================
# ChuoPulse: R8 / ProGuard rules (used by the release and staging builds)
# ====================================================================

# ---- Smaller and harder to read; keep line numbers so crash reports still make sense ----
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod,RuntimeVisibleAnnotations,RuntimeVisibleParameterAnnotations
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
-repackageclasses ''
-allowaccessmodification

# ---- Remove debug logging from the shipped app ----
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}

# ---- Kotlin ----
-keep class kotlin.Metadata { *; }
-dontwarn kotlin.**
-dontwarn kotlinx.coroutines.**
-keepclassmembers class * {
    @kotlinx.coroutines.InternalCoroutinesApi *;
}

# ---- Jetpack Compose ----
-dontwarn androidx.compose.**

# ---- Room ----
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# ---- Networking (OkHttp / Retrofit / Moshi) ----
-keepclassmembers class * {
    @retrofit2.http.* <methods>;
}
-keepclasseswithmembers class * {
    @com.squareup.moshi.Json <fields>;
}
-keep class * implements com.squareup.moshi.JsonAdapter
-dontwarn retrofit2.**
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn com.squareup.moshi.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
-dontwarn org.slf4j.**
-dontwarn javax.annotation.**
-dontwarn com.google.errorprone.annotations.**

# ---- Firebase and ML Kit ----
-keep class com.google.firebase.** { *; }
-dontwarn com.google.firebase.**
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**

# ---- Coil ----
-keep class coil.** { *; }
-dontwarn coil.**
