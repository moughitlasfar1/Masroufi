package com.example.security

import android.app.Activity
import android.app.KeyguardManager
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.PersistableBundle
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.AccentCoral
import com.example.ui.theme.paletteForKey
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import kotlin.math.ceil
import kotlinx.coroutines.delay
import org.json.JSONObject

object AppSecurityManager {
    private const val PREFS_NAME = "masroufi_security_prefs"
    private const val KEY_FLAG_SECURE = "flag_secure_enabled"
    private const val KEY_APP_LOCK = "app_lock_enabled"
    private const val KEY_DEVICE_CREDENTIAL = "device_credential_allowed"
    private const val KEY_CLEAR_CACHE_ON_SIGNOUT = "clear_cache_on_signout"
    private const val KEY_PIN_HASH = "pin_pbkdf2_hash"
    private const val KEY_PIN_SALT = "pin_pbkdf2_salt"
    private const val KEY_PIN_ITERATIONS = "pin_pbkdf2_iterations"
    private const val KEY_PIN_LENGTH = "pin_configured_length"
    private const val KEY_LEGACY_PIN_HASH = "pin_sha256_hash"
    private const val KEY_LEGACY_PIN_SALT = "pin_sha256_salt"
    private const val KEY_FAILED_ATTEMPTS = "pin_failed_attempts"
    private const val KEY_LOCKOUT_UNTIL_MS = "pin_lockout_until_ms"

    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val PIN_KEYSTORE_ALIAS = "masroufi_pin_keystore_v1"

    internal const val PBKDF2_ITERATIONS = 65_536
    private const val PBKDF2_KEY_BITS = 256
    private const val GCM_TAG_BITS = 128
    private const val GCM_IV_BYTES = 12
    private const val SALT_BYTES = 16

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isFlagSecureEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_FLAG_SECURE, false)

    fun setFlagSecureEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_FLAG_SECURE, enabled).apply()
    }

    fun isAppLockEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_APP_LOCK, false)

    fun setAppLockEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_APP_LOCK, enabled).apply()
    }

    fun isDeviceCredentialAllowed(context: Context): Boolean =
        prefs(context).getBoolean(KEY_DEVICE_CREDENTIAL, true)

    fun setDeviceCredentialAllowed(context: Context, allowed: Boolean) {
        prefs(context).edit().putBoolean(KEY_DEVICE_CREDENTIAL, allowed).apply()
    }

    fun isClearCacheOnSignOutEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_CLEAR_CACHE_ON_SIGNOUT, false)

    fun setClearCacheOnSignOutEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_CLEAR_CACHE_ON_SIGNOUT, enabled).apply()
    }

    fun hasPinConfigured(context: Context): Boolean {
        val p = prefs(context)
        val hasPbkdf2 = !p.getString(KEY_PIN_HASH, null).isNullOrBlank() &&
            !p.getString(KEY_PIN_SALT, null).isNullOrBlank()
        val hasLegacy = !p.getString(KEY_LEGACY_PIN_HASH, null).isNullOrBlank() &&
            !p.getString(KEY_LEGACY_PIN_SALT, null).isNullOrBlank()
        return hasPbkdf2 || hasLegacy
    }

    fun getConfiguredPinLength(context: Context): Int {
        return prefs(context).getInt(KEY_PIN_LENGTH, 6).coerceIn(4, 8)
    }

    /**
     * Derives a 256-bit PBKDF2-HMAC-SHA256 hash with a fresh 128-bit cryptographic random salt.
     * Supports 4-to-8 digit numeric PINs (6 digits recommended).
     */
    internal fun createSaltedPinHash(
        rawPin: String,
        iterations: Int = PBKDF2_ITERATIONS
    ): Pair<String, String>? {
        val normalized = rawPin.trim()
        if (normalized.length !in 4..8 || !normalized.all { it.isDigit() }) return null
        val saltBytes = ByteArray(SALT_BYTES)
        SecureRandom().nextBytes(saltBytes)
        val saltHex = bytesToHex(saltBytes)
        val derivedHex = pbkdf2Hex(normalized, saltBytes, iterations)
        return saltHex to derivedHex
    }

    internal fun pbkdf2Hex(secret: String, saltBytes: ByteArray, iterations: Int): String {
        val spec = PBEKeySpec(secret.toCharArray(), saltBytes, iterations, PBKDF2_KEY_BITS)
        return try {
            val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            val bytes = factory.generateSecret(spec).encoded
            bytesToHex(bytes)
        } finally {
            spec.clearPassword()
        }
    }

    /**
     * Wraps the PBKDF2 derived hash using an AndroidKeyStore hardware-backed AES-256-GCM key when available,
     * falling back gracefully to raw storage in headless JVM / Robolectric unit test environments.
     */
    private fun wrapWithAndroidKeystore(plainHex: String): String {
        return runCatching {
            val secretKey = getOrCreateKeystoreAesKey() ?: return "raw1:$plainHex"
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)
            val iv = cipher.iv
            val cipherBytes = cipher.doFinal(plainHex.toByteArray(Charsets.UTF_8))
            "ks1:${bytesToHex(iv)}:${bytesToHex(cipherBytes)}"
        }.getOrDefault("raw1:$plainHex")
    }

    private fun unwrapFromAndroidKeystore(storedValue: String): String? {
        return runCatching {
            when {
                storedValue.startsWith("ks1:") -> {
                    val parts = storedValue.split(":")
                    if (parts.size != 3) return null
                    val iv = hexToBytes(parts[1])
                    val cipherBytes = hexToBytes(parts[2])
                    val secretKey = getOrCreateKeystoreAesKey() ?: return null
                    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                    cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_BITS, iv))
                    String(cipher.doFinal(cipherBytes), Charsets.UTF_8)
                }
                storedValue.startsWith("raw1:") -> storedValue.removePrefix("raw1:")
                else -> storedValue
            }
        }.getOrNull()
    }

    private fun getOrCreateKeystoreAesKey(): SecretKey? {
        return runCatching {
            val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            val existing = ks.getEntry(PIN_KEYSTORE_ALIAS, null) as? KeyStore.SecretKeyEntry
            if (existing != null) return existing.secretKey

            val keyGen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
            val spec = KeyGenParameterSpec.Builder(
                PIN_KEYSTORE_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
            keyGen.init(spec)
            keyGen.generateKey()
        }.getOrNull()
    }

    fun setPin(context: Context, rawPin: String): Boolean {
        val normalized = rawPin.trim()
        val (saltHex, derivedHex) = createSaltedPinHash(normalized, PBKDF2_ITERATIONS) ?: return false
        val wrappedHash = wrapWithAndroidKeystore(derivedHex)
        prefs(context).edit()
            .putString(KEY_PIN_SALT, saltHex)
            .putString(KEY_PIN_HASH, wrappedHash)
            .putInt(KEY_PIN_ITERATIONS, PBKDF2_ITERATIONS)
            .putInt(KEY_PIN_LENGTH, normalized.length)
            .remove(KEY_LEGACY_PIN_HASH)
            .remove(KEY_LEGACY_PIN_SALT)
            .putInt(KEY_FAILED_ATTEMPTS, 0)
            .putLong(KEY_LOCKOUT_UNTIL_MS, 0L)
            .apply()
        return true
    }

    fun getFailedAttempts(context: Context): Int =
        prefs(context).getInt(KEY_FAILED_ATTEMPTS, 0)

    fun getRemainingLockoutSeconds(context: Context, nowMs: Long = System.currentTimeMillis()): Int {
        val until = prefs(context).getLong(KEY_LOCKOUT_UNTIL_MS, 0L)
        if (until <= nowMs) return 0
        return ceil((until - nowMs) / 1000.0).toInt().coerceAtLeast(1)
    }

    fun resetLockoutState(context: Context) {
        prefs(context).edit()
            .putInt(KEY_FAILED_ATTEMPTS, 0)
            .putLong(KEY_LOCKOUT_UNTIL_MS, 0L)
            .apply()
    }

    internal fun computeLockoutDurationMs(failedAttempts: Int): Long {
        return when {
            failedAttempts < 3 -> 0L
            failedAttempts == 3 -> 15_000L
            failedAttempts == 4 -> 30_000L
            failedAttempts == 5 -> 60_000L
            else -> 300_000L
        }
    }

    /**
     * Verifies a PIN with PBKDF2-HMAC-SHA256, constant-time comparison, and progressive rate-limiting lockout.
     * Transparently migrates legacy SHA-256 PINs to PBKDF2-HMAC-SHA256 on successful match.
     */
    fun verifyPin(
        context: Context,
        rawPin: String,
        nowMs: Long = System.currentTimeMillis()
    ): Boolean {
        if (getRemainingLockoutSeconds(context, nowMs) > 0) {
            return false
        }

        val normalized = rawPin.trim()
        val p = prefs(context)
        val saltHex = p.getString(KEY_PIN_SALT, null)
        val wrappedStoredHash = p.getString(KEY_PIN_HASH, null)

        val matched: Boolean
        if (!saltHex.isNullOrBlank() && !wrappedStoredHash.isNullOrBlank()) {
            val iterations = p.getInt(KEY_PIN_ITERATIONS, PBKDF2_ITERATIONS).coerceAtLeast(10_000)
            val storedHashHex = unwrapFromAndroidKeystore(wrappedStoredHash) ?: return false
            val candidateHashHex = pbkdf2Hex(normalized, hexToBytes(saltHex), iterations)
            matched = MessageDigest.isEqual(
                storedHashHex.toByteArray(Charsets.UTF_8),
                candidateHashHex.toByteArray(Charsets.UTF_8)
            )
        } else {
            // Legacy SHA-256 fallback & automatic upgrade to PBKDF2
            val legacySalt = p.getString(KEY_LEGACY_PIN_SALT, null) ?: return false
            val legacyHash = p.getString(KEY_LEGACY_PIN_HASH, null) ?: return false
            val candidateLegacy = sha256Hex("$legacySalt:$normalized")
            matched = MessageDigest.isEqual(
                legacyHash.toByteArray(Charsets.UTF_8),
                candidateLegacy.toByteArray(Charsets.UTF_8)
            )
            if (matched) {
                setPin(context, normalized)
            }
        }

        if (matched) {
            resetLockoutState(context)
            return true
        } else {
            val nextFailed = p.getInt(KEY_FAILED_ATTEMPTS, 0) + 1
            val lockoutDuration = computeLockoutDurationMs(nextFailed)
            val lockoutUntil = if (lockoutDuration > 0L) nowMs + lockoutDuration else 0L
            p.edit()
                .putInt(KEY_FAILED_ATTEMPTS, nextFailed)
                .putLong(KEY_LOCKOUT_UNTIL_MS, lockoutUntil)
                .apply()
            return false
        }
    }

    fun clearPin(context: Context) {
        prefs(context).edit()
            .remove(KEY_PIN_HASH)
            .remove(KEY_PIN_SALT)
            .remove(KEY_PIN_ITERATIONS)
            .remove(KEY_PIN_LENGTH)
            .remove(KEY_LEGACY_PIN_HASH)
            .remove(KEY_LEGACY_PIN_SALT)
            .remove(KEY_FAILED_ATTEMPTS)
            .remove(KEY_LOCKOUT_UNTIL_MS)
            .putBoolean(KEY_APP_LOCK, false)
            .apply()
    }

    /**
     * Encrypts a JSON backup string using AES-256-GCM with a key derived from [password]
     * via PBKDF2-HMAC-SHA256 (65,536 iterations and 128-bit random salt).
     */
    fun encryptBackupJson(plainJson: String, password: String): String {
        val cleanPass = password.trim()
        require(cleanPass.length >= 4) { "Backup password must be at least 4 characters." }

        val random = SecureRandom()
        val saltBytes = ByteArray(SALT_BYTES).also { random.nextBytes(it) }
        val ivBytes = ByteArray(GCM_IV_BYTES).also { random.nextBytes(it) }

        val keyBytes = deriveAesKeyBytes(cleanPass, saltBytes, PBKDF2_ITERATIONS)
        val secretKey = SecretKeySpec(keyBytes, "AES")
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_BITS, ivBytes))
        val cipherBytes = cipher.doFinal(plainJson.toByteArray(Charsets.UTF_8))

        return JSONObject().apply {
            put("masroufiEncrypted", true)
            put("encryptionVersion", 1)
            put("kdf", "PBKDF2WithHmacSHA256")
            put("iterations", PBKDF2_ITERATIONS)
            put("cipher", "AES/GCM/NoPadding")
            put("saltHex", bytesToHex(saltBytes))
            put("ivHex", bytesToHex(ivBytes))
            put("ciphertextBase64", Base64.encodeToString(cipherBytes, Base64.NO_WRAP))
        }.toString(2)
    }

    /**
     * Decrypts and authenticates an AES-256-GCM encrypted Masroufi backup envelope.
     * Throws IllegalArgumentException if the password is wrong or the payload was tampered with.
     */
    fun decryptBackupJson(encryptedEnvelopeJson: String, password: String): String {
        val cleanPass = password.trim()
        require(cleanPass.isNotEmpty()) { "Password required to decrypt backup." }
        return try {
            val obj = JSONObject(encryptedEnvelopeJson.trim())
            require(obj.optBoolean("masroufiEncrypted", false)) { "Not an encrypted Masroufi backup." }
            val iterations = obj.optInt("iterations", PBKDF2_ITERATIONS).coerceIn(10_000, 500_000)
            val saltBytes = hexToBytes(obj.getString("saltHex"))
            val ivBytes = hexToBytes(obj.getString("ivHex"))
            val cipherBytes = Base64.decode(obj.getString("ciphertextBase64"), Base64.DEFAULT)

            val keyBytes = deriveAesKeyBytes(cleanPass, saltBytes, iterations)
            val secretKey = SecretKeySpec(keyBytes, "AES")
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_BITS, ivBytes))
            val plainBytes = cipher.doFinal(cipherBytes)
            String(plainBytes, Charsets.UTF_8)
        } catch (e: Exception) {
            throw IllegalArgumentException("Mot de passe incorrect ou fichier de sauvegarde chiffré altéré.", e)
        }
    }

    fun isEncryptedBackup(rawContent: String): Boolean {
        val trimmed = rawContent.trim()
        if (!trimmed.startsWith("{")) return false
        return runCatching {
            JSONObject(trimmed).optBoolean("masroufiEncrypted", false)
        }.getOrDefault(false)
    }

    private fun deriveAesKeyBytes(password: String, saltBytes: ByteArray, iterations: Int): ByteArray {
        val spec = PBEKeySpec(password.toCharArray(), saltBytes, iterations, PBKDF2_KEY_BITS)
        return try {
            val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            factory.generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    fun isDeviceSecure(context: Context): Boolean {
        val km = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        return km?.isDeviceSecure == true
    }

    fun applyWindowFlagSecure(activity: Activity?, enabled: Boolean) {
        val window = activity?.window ?: return
        if (enabled) {
            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    fun copySensitiveToClipboard(context: Context, label: String, content: String) {
        runCatching {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
            val clipData = ClipData.newPlainText(label, content)
            val extras = PersistableBundle().apply {
                val extraKey = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    ClipDescription.EXTRA_IS_SENSITIVE
                } else {
                    "android.content.extra.IS_SENSITIVE"
                }
                putBoolean(extraKey, true)
            }
            clipData.description.extras = extras
            clipboard.setPrimaryClip(clipData)
        }
    }

    fun clearSensitiveClipboard(context: Context) {
        runCatching {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                clipboard.clearPrimaryClip()
            } else {
                clipboard.setPrimaryClip(ClipData.newPlainText("", ""))
            }
        }
    }

    fun writeTextToUri(context: Context, uri: Uri, content: String): Boolean {
        return runCatching {
            context.contentResolver.openOutputStream(uri)?.use { out ->
                out.write(content.toByteArray(Charsets.UTF_8))
                out.flush()
            } ?: return false
            true
        }.getOrElse { false }
    }

    fun readTextFromUri(context: Context, uri: Uri): String? {
        return runCatching {
            context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use {
                it.readText()
            }
        }.getOrNull()
    }

    internal fun sha256Hex(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest(input.toByteArray(Charsets.UTF_8))
        return bytesToHex(bytes)
    }

    private fun bytesToHex(bytes: ByteArray): String {
        val sb = StringBuilder(bytes.size * 2)
        for (b in bytes) {
            sb.append(String.format("%02x", b))
        }
        return sb.toString()
    }

    private fun hexToBytes(hex: String): ByteArray {
        val clean = hex.trim()
        val len = clean.length
        val out = ByteArray(len / 2)
        var i = 0
        while (i < len) {
            out[i / 2] = ((Character.digit(clean[i], 16) shl 4) + Character.digit(clean[i + 1], 16)).toByte()
            i += 2
        }
        return out
    }
}

@Composable
fun MasroufiSecurityLockScreen(
    language: String,
    accentPalette: String,
    onUnlocked: () -> Unit,
    onSignOutRecovery: () -> Unit
) {
    val context = LocalContext.current
    val pal = paletteForKey(accentPalette)
    var enteredPin by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var lockoutSeconds by remember { mutableIntStateOf(AppSecurityManager.getRemainingLockoutSeconds(context)) }

    val hasPin = remember(context) { AppSecurityManager.hasPinConfigured(context) }
    val pinLength = remember(context) { AppSecurityManager.getConfiguredPinLength(context) }
    val canUseDeviceCredential = remember(context) {
        AppSecurityManager.isDeviceCredentialAllowed(context) && AppSecurityManager.isDeviceSecure(context)
    }

    LaunchedEffect(lockoutSeconds) {
        if (lockoutSeconds > 0) {
            delay(1_000L)
            lockoutSeconds = AppSecurityManager.getRemainingLockoutSeconds(context)
            if (lockoutSeconds == 0) {
                errorMessage = null
            }
        }
    }

    val titleText = when (language) {
        "ar" -> "التطبيق مقفل لحماية بياناتك المالية"
        "darija" -> "التطبيق مقفول باش نحميو حساباتك"
        "en" -> "Masroufi Security Lock"
        else -> "Masroufi Verrouillé"
    }
    val subText = when (language) {
        "ar" -> "أدخل رمز PIN المكون من $pinLength أرقام أو استخدم بصمة/قفل الجهاز للمتابعة"
        "darija" -> "دخل كود PIN ديال $pinLength أرقام أو حل ببصمة التليفون"
        "en" -> "Enter your $pinLength-digit PIN or use device biometrics to continue"
        else -> "Entrez votre code PIN à $pinLength chiffres ou utilisez la biométrie de l'appareil"
    }
    val wrongPinText = when (language) {
        "ar" -> "رمز PIN غير صحيح، حاول مرة أخرى"
        "darija" -> "الكود غالط، عاود جرب"
        "en" -> "Incorrect PIN, please try again"
        else -> "Code PIN incorrect, veuillez réessayer"
    }
    val lockoutBannerText = if (lockoutSeconds > 0) {
        when (language) {
            "ar" -> "تم إيقاف المحاولات مؤقتاً للحماية. انتظر ${lockoutSeconds} ثانية"
            "darija" -> "بزاف ديال المحاولات غالطة. تسنى ${lockoutSeconds} ثانية"
            "en" -> "Too many failed attempts. Try again in ${lockoutSeconds}s"
            else -> "Trop de tentatives. Réessayez dans ${lockoutSeconds}s"
        }
    } else null

    val deviceUnlockLabel = when (language) {
        "ar" -> "فتح ببصمة أو قفل الجهاز"
        "darija" -> "حل ببصمة أو كود التليفون"
        "en" -> "Unlock with Device Biometrics / PIN"
        else -> "Déverrouiller avec Biométrie / Appareil"
    }
    val signOutRecoveryLabel = when (language) {
        "ar" -> "نسيت الرمز؟ تسجيل الخروج وإعادة الدخول بحساب Google"
        "darija" -> "نسيتي الكود؟ خرج وعاود دخل بحساب Google"
        "en" -> "Forgot PIN? Sign out & re-authenticate with Google"
        else -> "PIN oublié ? Se déconnecter et se reconnecter via Google"
    }

    val deviceCredentialLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            AppSecurityManager.resetLockoutState(context)
            onUnlocked()
        }
    }

    fun launchDeviceAuth() {
        runCatching {
            val km = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
            @Suppress("DEPRECATION")
            val intent = km?.createConfirmDeviceCredentialIntent(titleText, subText)
            if (intent != null) {
                deviceCredentialLauncher.launch(intent)
            }
        }
    }

    LaunchedEffect(hasPin, canUseDeviceCredential) {
        if (!hasPin && !canUseDeviceCredential) {
            onUnlocked()
        }
    }

    val isLockedOut = lockoutSeconds > 0

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .testTag("security_lock_screen"),
        color = MaterialTheme.colorScheme.background
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 400.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(Brush.linearGradient(listOf(pal.primaryDark, pal.heroStart))),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(34.dp)
                    )
                }

                Text(
                    text = titleText,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = subText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                if (hasPin) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 8.dp)
                    ) {
                        for (i in 0 until pinLength) {
                            val filled = i < enteredPin.length
                            Box(
                                modifier = Modifier
                                    .size(18.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (filled) MaterialTheme.colorScheme.primary
                                        else Color.Transparent
                                    )
                                    .border(
                                        width = 2.dp,
                                        color = if (filled) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.outline,
                                        shape = CircleShape
                                    )
                            )
                        }
                    }

                    val activeMsg = lockoutBannerText ?: errorMessage
                    if (!activeMsg.isNullOrBlank()) {
                        Text(
                            text = activeMsg,
                            color = AccentCoral,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.testTag("pin_lock_error_text")
                        )
                    }

                    val rows = listOf(
                        listOf("1", "2", "3"),
                        listOf("4", "5", "6"),
                        listOf("7", "8", "9"),
                        listOf("bio", "0", "del")
                    )

                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.alpha(if (isLockedOut) 0.45f else 1f)
                    ) {
                        rows.forEach { row ->
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(20.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                row.forEach { key ->
                                    when (key) {
                                        "bio" -> {
                                            if (canUseDeviceCredential) {
                                                Surface(
                                                    shape = CircleShape,
                                                    color = MaterialTheme.colorScheme.primaryContainer,
                                                    modifier = Modifier
                                                        .size(68.dp)
                                                        .clip(CircleShape)
                                                        .clickable { launchDeviceAuth() }
                                                        .testTag("pin_pad_biometric")
                                                ) {
                                                    Box(contentAlignment = Alignment.Center) {
                                                        Icon(
                                                            imageVector = Icons.Default.Fingerprint,
                                                            contentDescription = deviceUnlockLabel,
                                                            tint = MaterialTheme.colorScheme.primary,
                                                            modifier = Modifier.size(30.dp)
                                                        )
                                                    }
                                                }
                                            } else {
                                                Spacer(modifier = Modifier.size(68.dp))
                                            }
                                        }

                                        "del" -> {
                                            Surface(
                                                shape = CircleShape,
                                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                                modifier = Modifier
                                                    .size(68.dp)
                                                    .clip(CircleShape)
                                                    .clickable(enabled = !isLockedOut) {
                                                        if (enteredPin.isNotEmpty()) {
                                                            enteredPin = enteredPin.dropLast(1)
                                                            errorMessage = null
                                                        }
                                                    }
                                                    .testTag("pin_pad_delete")
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Icon(
                                                        imageVector = Icons.AutoMirrored.Filled.Backspace,
                                                        contentDescription = "Delete",
                                                        modifier = Modifier.size(24.dp)
                                                    )
                                                }
                                            }
                                        }

                                        else -> {
                                            Surface(
                                                shape = CircleShape,
                                                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                                                modifier = Modifier
                                                    .size(68.dp)
                                                    .clip(CircleShape)
                                                    .clickable(enabled = !isLockedOut) {
                                                        if (enteredPin.length < pinLength) {
                                                            val next = enteredPin + key
                                                            enteredPin = next
                                                            errorMessage = null
                                                            if (next.length == pinLength) {
                                                                if (AppSecurityManager.verifyPin(context, next)) {
                                                                    onUnlocked()
                                                                } else {
                                                                    lockoutSeconds = AppSecurityManager.getRemainingLockoutSeconds(context)
                                                                    val fails = AppSecurityManager.getFailedAttempts(context)
                                                                    errorMessage = if (lockoutSeconds > 0) {
                                                                        null
                                                                    } else {
                                                                        "$wrongPinText (${(3 - fails).coerceAtLeast(1)} essais avant blocage)"
                                                                    }
                                                                    enteredPin = ""
                                                                }
                                                            }
                                                        }
                                                    }
                                                    .testTag("pin_pad_$key")
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Text(
                                                        text = key,
                                                        style = MaterialTheme.typography.titleLarge,
                                                        fontWeight = FontWeight.ExtraBold,
                                                        fontSize = 22.sp
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                if (canUseDeviceCredential) {
                    FilledTonalButton(
                        onClick = { launchDeviceAuth() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("unlock_device_credential_btn")
                    ) {
                        Icon(Icons.Default.Security, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(deviceUnlockLabel, fontWeight = FontWeight.Bold)
                    }
                }

                TextButton(
                    onClick = {
                        AppSecurityManager.clearPin(context)
                        onSignOutRecovery()
                    },
                    modifier = Modifier.testTag("forgot_pin_signout_btn")
                ) {
                    Text(
                        text = signOutRecoveryLabel,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}
