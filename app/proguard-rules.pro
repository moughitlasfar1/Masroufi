# Masroufi Security Hardening ProGuard / R8 Rules (SEC-09)

# Preserve line numbers for crash diagnostics while hiding original source file names
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Preserve Kotlin metadata, generic signatures, and annotations required by Firestore & Compose
-keepattributes Signature,InnerClasses,EnclosingMethod,*Annotation*
-keep class kotlin.Metadata { *; }

# SEC-09: Strip Android Log calls in release builds
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
    public static int e(...);
}

# SEC-09: Keep ONLY the Firestore @IgnoreExtraProperties data models that require reflection;
# allow R8 to obfuscate com.example.security, com.example.domain, and repository logic.
-keep class com.example.data.UserProfile { *; }
-keep class com.example.data.TransactionItem { *; }
-keep class com.example.data.AccountItem { *; }
-keep class com.example.data.EnvelopeConfig { *; }
-keep class com.example.data.FinancialItem { *; }

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
