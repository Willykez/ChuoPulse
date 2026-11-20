-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod,RuntimeVisibleAnnotations,RuntimeVisibleParameterAnnotations
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
-repackageclasses ''
-allowaccessmodification

-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}

-keep class kotlin.Metadata { *; }
-dontwarn kotlin.**
-dontwarn kotlinx.coroutines.**
-keepclassmembers class * {
    @kotlinx.coroutines.InternalCoroutinesApi *;
}

-dontwarn androidx.compose.**

-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
-dontwarn javax.annotation.**
-dontwarn com.google.errorprone.annotations.**

-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**

-keep class coil.** { *; }
-dontwarn coil.**
