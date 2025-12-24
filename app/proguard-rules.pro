# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.

# ========================
# Firebase Rules
# ========================

# Keep Firebase classes
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.firebase.**
-dontwarn com.google.android.gms.**

# Firebase Authentication
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes EnclosingMethod
-keepattributes InnerClasses

# Firebase Realtime Database
-keepclassmembers class * {
    @com.google.firebase.database.PropertyName *;
}

# ========================
# Your Data Classes
# ========================

# ✅ CRITICAL: Keep all data classes used with Firebase
-keep class id.xms.xcai.data.repository.RateLimitData { *; }
-keep class id.xms.xcai.data.repository.RateLimitData$* { *; }

# Keep all data classes
-keep class id.xms.xcai.data.** { *; }
-keepclassmembers class id.xms.xcai.data.** { *; }

# Keep model classes
-keep class id.xms.xcai.data.model.** { *; }
-keepclassmembers class id.xms.xcai.data.model.** {
    <fields>;
    <init>(...);
}

# Keep local database entities
-keep class id.xms.xcai.data.local.** { *; }
-keepclassmembers class id.xms.xcai.data.local.** {
    <fields>;
    <init>(...);
}

# Keep remote API classes
-keep class id.xms.xcai.data.remote.** { *; }
-keepclassmembers class id.xms.xcai.data.remote.** {
    <fields>;
    <init>(...);
}

# ========================
# Retrofit & OkHttp
# ========================

# Retrofit
-dontwarn retrofit2.**
-keep class retrofit2.** { *; }
-keepattributes Signature
-keepattributes Exceptions
-keepclasseswithmembers class * {
    @retrofit2.http.* <methods>;
}

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }

# ========================
# Gson/Moshi (if used)
# ========================

# Gson
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn sun.misc.**
-keep class com.google.gson.** { *; }
-keep class * implements com.google.gson.TypeAdapter
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

# Gson specific classes
-keepclassmembers,allowobfuscation class * {
  @com.google.gson.annotations.SerializedName <fields>;
}

# ========================
# Kotlin & Coroutines
# ========================

# Kotlin
-keep class kotlin.** { *; }
-keep class kotlin.Metadata { *; }
-dontwarn kotlin.**
-keepclassmembers class **$WhenMappings {
    <fields>;
}
-keepclassmembers class kotlin.Metadata {
    public <methods>;
}

# Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembernames class kotlinx.** {
    volatile <fields>;
}

# ========================
# Room Database
# ========================

-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# ========================
# Jetpack Compose
# ========================

-keep class androidx.compose.** { *; }
-keep interface androidx.compose.** { *; }
-dontwarn androidx.compose.**

# ========================
# DataStore
# ========================

-keep class androidx.datastore.** { *; }
-keepclassmembers class * extends androidx.datastore.core.Serializer {
    public <methods>;
}

# ========================
# General Android
# ========================

-keepattributes SourceFile,LineNumberTable
-keepattributes LocalVariableTable
-keep public class * extends java.lang.Exception

# Keep native methods
-keepclasseswithmembernames class * {
    native <methods>;
}

# Keep Parcelable implementations
-keep class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
}

# Keep Serializable implementations
-keepnames class * implements java.io.Serializable
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

# ========================
# Remove Logging (Optional)
# ========================

# Remove Log calls (saves space and improves performance)
# Uncomment if you want to remove all logging in release
# -assumenosideeffects class android.util.Log {
#     public static *** d(...);
#     public static *** v(...);
#     public static *** i(...);
#     public static *** w(...);
#     public static *** e(...);
# }

# ========================
# SLF4J (Required by iText7)
# ========================

# Ignore missing SLF4J binding classes - iText uses SLF4J but Android doesn't have it
-dontwarn org.slf4j.**
-keep class org.slf4j.** { *; }
-dontwarn org.slf4j.impl.StaticLoggerBinder
-dontwarn org.slf4j.impl.StaticMDCBinder
-dontwarn org.slf4j.impl.StaticMarkerBinder

# ========================
# iText7 PDF Library
# ========================

-dontwarn com.itextpdf.**
-keep class com.itextpdf.** { *; }
-dontwarn org.bouncycastle.**
-keep class org.bouncycastle.** { *; }

