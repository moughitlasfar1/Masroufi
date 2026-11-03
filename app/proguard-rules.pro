# Masroufi Security Hardening ProGuard / R8 Rules

# Preserve line numbers for crash diagnostics while hiding original source file names
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Preserve Kotlin metadata, generic signatures, and annotations required by Firestore & Compose
-keepattributes Signature,InnerClasses,EnclosingMethod,*Annotation*
-keep class kotlin.Metadata { *; }

# Keep Masroufi Firestore & Backup data models so reflection / serialization works in release builds
-keep class com.example.data.** { *; }
-keepclassmembers class com.example.data.** {
    *;
}
-keep class com.example.domain.** { *; }
-keep class com.example.security.** { *; }

# Keep Firebase Firestore, Auth, and App Check classes
-keep class com.google.firebase.** { *; }
-dontwarn com.google.firebase.**
-keepnames class * implements com.google.firebase.firestore.DocumentSnapshot

# Keep Jetpack Credential Manager & Google Identity Services
-keep class androidx.credentials.** { *; }
-keep class com.google.android.libraries.identity.googleid.** { *; }
-dontwarn androidx.credentials.**
-dontwarn com.google.android.libraries.identity.googleid.**

# Coroutines & Play Services Tasks
-dontwarn kotlinx.coroutines.**
-keepclassmembernames class kotlinx.** {
    volatile <fields>;
}
