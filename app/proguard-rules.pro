# ====================================================================
# Android & Jetpack Compose
# ====================================================================
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod
-dontwarn androidx.compose.**

# ====================================================================
# Kotlin Coroutines
# ====================================================================
-keepclassmembers class * {
    @kotlinx.coroutines.InternalCoroutinesApi *;
}
-dontwarn kotlinx.coroutines.**

# ====================================================================
# Room Database
# ====================================================================
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# ====================================================================
# Retrofit & OkHttp
# ====================================================================
-keepattributes RuntimeVisibleAnnotations,RuntimeVisibleParameterAnnotations
-keepclassmembers class * {
    @retrofit2.http.* <methods>;
}
-dontwarn retrofit2.**
-dontwarn okhttp3.**
-dontwarn okio.**

# ====================================================================
# Moshi (JSON Serialization)
# ====================================================================
-keepclasseswithmembers class * {
    @com.squareup.moshi.Json <fields>;
}
-keepclasseswithmembers class * {
    @com.squareup.moshi.JsonClass <fields>;
}
-keep class * implements com.squareup.moshi.JsonAdapter
-keepclassmembers class * {
    public static com.squareup.moshi.JsonAdapter jsonAdapter(com.squareup.moshi.Moshi, java.lang.reflect.Type, java.lang.annotation.Annotation[]);
}
-dontwarn com.squareup.moshi.**

# ====================================================================
# Firebase & ML Kit
# ====================================================================
-keep class com.google.firebase.** { *; }
-dontwarn com.google.firebase.**
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**

# ====================================================================
# Coil Image Loading
# ====================================================================
-keep class coil.** { *; }
-dontwarn coil.**
