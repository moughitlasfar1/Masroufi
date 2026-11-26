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
-keep class com.masroufi.credit.data.SavedCreditEntity { *; }

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

# R8 Full Mode fix: room-runtime 2.6.1 (used internally by WorkManager) only declares
# `-keep class * extends androidx.room.RoomDatabase` without `{ <init>(); }`, which causes
# R8 Full Mode to strip WorkDatabase_Impl.<init>() and crash WorkManagerInitializer on launch.
-keep class * extends androidx.room.RoomDatabase {
    public <init>();
}
-keep class androidx.work.impl.WorkDatabase_Impl { *; }
-keep class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}
-keep class com.example.notifications.MasroufiNotificationWorker { *; }
-keep class com.example.notifications.EveningReminderWorker { *; }
-keep class com.example.notifications.BankListenerHealthWorker { *; }

# R8 Full Mode fix: versionedparcelable consumer rule omits <init>()
-keep class * implements androidx.versionedparcelable.VersionedParcelable {
    <init>();
}

# SQLCipher for Android (minimal R8 rules: net.zetetic.database.** and JNI native methods only)
-keep class net.zetetic.database.** { *; }
-keepclasseswithmembernames class * {
    native <methods>;
}

# PDFBox-Android (on-device bank statement PDF text extraction)
-keep class com.tom_roush.pdfbox.** { *; }
-keep class com.tom_roush.fontbox.** { *; }
-dontwarn com.tom_roush.pdfbox.**
-dontwarn com.tom_roush.fontbox.**
-dontwarn org.bouncycastle.**
-dontwarn java.awt.**
-dontwarn javax.imageio.**

# Bundled ML Kit Latin Text Recognition (on-device receipt & scanned PDF OCR)
-keep class com.google.mlkit.vision.text.** { *; }
-dontwarn com.google.mlkit.vision.text.**

