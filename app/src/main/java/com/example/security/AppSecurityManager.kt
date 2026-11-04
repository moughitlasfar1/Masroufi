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
import android.os.SystemClock
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.view.WindowManager
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.example.ui.theme.AccentCoral
import com.example.ui.theme.paletteForKey
import java.io.ByteArrayOutputStream
import java.io.File
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
    private const val KEY_LOCKOUT_DURATION_MS = "pin_lockout_duration_ms"
    private const val KEY_LOCKOUT_SEAL = "pin_lockout_seal"
    private const val KEY_HIDE_AMOUNTS = "ux_hide_amounts_enabled"
    private const val KEY_LAST_ACTIVE_TAB = "ux_last_active_tab"
    private const val KEY_DASHBOARD_ORDER = "ux_dashboard_card_order"
    private const val KEY_FORCE_INTERACTIVE_SIGNIN = "sec_force_interactive_signin"
    private const val KEY_PENDING_PIN_RECOVERY = "sec_pending_pin_recovery"

    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val PIN_KEYSTORE_ALIAS = "masroufi_pin_keystore_v1"
    private const val ROLLBACK_FILE_NAME = "masroufi_rollback_snapshot.enc"

    // SEC-03 & SEC-06: OWASP recommended 600,000 iterations for PBKDF2-HMAC-SHA256
    internal const val PBKDF2_ITERATIONS = 600_000
    internal const val LEGACY_PBKDF2_ITERATIONS = 65_536
    internal const val MIN_PBKDF2_ITERATIONS = 10_000
    internal const val MAX_PBKDF2_ITERATIONS = 1_000_000
    internal const val MIN_PIN_LENGTH = 6
    internal const val MAX_PIN_LENGTH = 8
    internal const val MIN_BACKUP_PASSWORD_LENGTH = 10
    internal const val MAX_BACKUP_FILE_BYTES = 10 * 1024 * 1024 // 10 MB

    private const val PBKDF2_KEY_BITS = 256
    private const val GCM_TAG_BITS = 128
    private const val GCM_IV_BYTES = 12
    private const val SALT_BYTES = 16

    @Volatile
    internal var allowPlaintextKeystoreFallbackInTests: Boolean? = null

    internal fun isRunningInUnitTest(): Boolean {
        allowPlaintextKeystoreFallbackInTests?.let { return it }
        return Build.FINGERPRINT.equals("robolectric", ignoreCase = true) ||
            System.getProperty("robolectric.offline") != null ||
            System.getProperty("org.robolectric.packages") != null
    }

    internal fun monotonicNowMs(): Long {
        val elapsed = runCatching { SystemClock.elapsedRealtime() }.getOrDefault(0L)
        return if (elapsed > 0L) elapsed else System.currentTimeMillis()
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isHideAmountsEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_HIDE_AMOUNTS, false)

    fun setHideAmountsEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_HIDE_AMOUNTS, enabled).apply()
    }

    fun getLastActiveTab(context: Context): String {
        val saved = prefs(context).getString(KEY_LAST_ACTIVE_TAB, "vue").orEmpty()
        return if (saved in listOf("vue", "semaine", "mois", "categories", "historique")) saved else "vue"
    }

    fun setLastActiveTab(context: Context, tab: String) {
        if (tab in listOf("vue", "semaine", "mois", "categories", "historique")) {
            prefs(context).edit().putString(KEY_LAST_ACTIVE_TAB, tab).apply()
        }
    }

    fun getDashboardCardOrder(context: Context): List<String>? {
        val raw = prefs(context).getString(KEY_DASHBOARD_ORDER, null) ?: return null
        val list = raw.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        return list.ifEmpty { null }
    }

    fun setDashboardCardOrder(context: Context, order: List<String>) {
        prefs(context).edit().putString(KEY_DASHBOARD_ORDER, order.joinToString(",")).apply()
    }

    /**
     * SEC-05: FLAG_SECURE is enabled by default to protect financial balances from
     * screenshots, screen recordings, and the Android Recents switcher.
     */
    fun isFlagSecureEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_FLAG_SECURE, true)

    fun setFlagSecureEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_FLAG_SECURE, enabled).apply()
    }

    fun isAppLockEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_APP_LOCK, false)

    fun setAppLockEnabled(context: Context, enabled: Boolean) {
        val editor = prefs(context).edit().putBoolean(KEY_APP_LOCK, enabled)
        if (enabled && !prefs(context).contains(KEY_FLAG_SECURE)) {
            editor.putBoolean(KEY_FLAG_SECURE, true)
        }
        editor.apply()
    }

    fun isDeviceCredentialAllowed(context: Context): Boolean =
        prefs(context).getBoolean(KEY_DEVICE_CREDENTIAL, true)

    fun setDeviceCredentialAllowed(context: Context, allowed: Boolean) {
        prefs(context).edit().putBoolean(KEY_DEVICE_CREDENTIAL, allowed).apply()
    }

    /**
     * SEC-05: Clear local Firestore cache on sign-out defaults to true so shared devices
     * never retain unencrypted cached transactions after sign-out.
     */
    fun isClearCacheOnSignOutEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_CLEAR_CACHE_ON_SIGNOUT, true)

    fun setClearCacheOnSignOutEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_CLEAR_CACHE_ON_SIGNOUT, enabled).apply()
    }

    /**
     * SEC-02: Tracks whether the user initiated a Forgot-PIN recovery sign-out,
     * disabling silent auto-select sign-in and requiring explicit Google account selection.
     */
    fun isForceInteractiveSignInRequired(context: Context): Boolean =
        prefs(context).getBoolean(KEY_FORCE_INTERACTIVE_SIGNIN, false)

    fun setForceInteractiveSignInRequired(context: Context, required: Boolean) {
        prefs(context).edit().putBoolean(KEY_FORCE_INTERACTIVE_SIGNIN, required).apply()
    }

    fun isPendingPinRecovery(context: Context): Boolean =
        prefs(context).getBoolean(KEY_PENDING_PIN_RECOVERY, false)

    fun beginPinRecoverySignOut(context: Context) {
        prefs(context).edit()
            .putBoolean(KEY_FORCE_INTERACTIVE_SIGNIN, true)
            .putBoolean(KEY_PENDING_PIN_RECOVERY, true)
            .apply()
    }

    fun completePinRecoveryAfterInteractiveSignIn(context: Context) {
        val p = prefs(context)
        val wasRecovery = p.getBoolean(KEY_PENDING_PIN_RECOVERY, false)
        p.edit()
            .putBoolean(KEY_FORCE_INTERACTIVE_SIGNIN, false)
            .putBoolean(KEY_PENDING_PIN_RECOVERY, false)
            .apply()
        if (wasRecovery) {
            clearPin(context)
        }
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
        return prefs(context).getInt(KEY_PIN_LENGTH, MIN_PIN_LENGTH).coerceIn(4, MAX_PIN_LENGTH)
    }

    /**
     * SEC-06: Derives a 256-bit PBKDF2-HMAC-SHA256 hash with a fresh 128-bit cryptographic random salt.
     * Enforces 6-to-8 digit numeric PINs (minimum 6 digits).
     */
    internal fun createSaltedPinHash(
        rawPin: String,
        iterations: Int = PBKDF2_ITERATIONS
    ): Pair<String, String>? {
        val normalized = rawPin.trim()
        if (normalized.length !in MIN_PIN_LENGTH..MAX_PIN_LENGTH || !normalized.all { it.isDigit() }) {
            return null
        }
        val safeIterations = iterations.coerceIn(MIN_PBKDF2_ITERATIONS, MAX_PBKDF2_ITERATIONS)
        val saltBytes = ByteArray(SALT_BYTES)
        SecureRandom().nextBytes(saltBytes)
        val saltHex = bytesToHex(saltBytes)
        val derivedHex = pbkdf2Hex(normalized, saltBytes, safeIterations)
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
     * SEC-06: Wraps the PBKDF2 derived hash using an AndroidKeyStore hardware-backed AES-256-GCM key.
     * Fails closed (returns null) on physical devices if the AndroidKeyStore is unavailable;
     * permits `raw1:` fallback strictly inside local JVM / Robolectric unit tests.
     */
    internal fun wrapWithAndroidKeystore(plainHex: String): String? {
        val keystoreWrapped = runCatching {
            val secretKey = getOrCreateKeystoreAesKey() ?: return@runCatching null
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)
            val iv = cipher.iv
            val cipherBytes = cipher.doFinal(plainHex.toByteArray(Charsets.UTF_8))
            "ks1:${bytesToHex(iv)}:${bytesToHex(cipherBytes)}"
        }.getOrNull()

        if (keystoreWrapped != null) return keystoreWrapped
        return if (isRunningInUnitTest()) "raw1:$plainHex" else null
    }

    internal fun unwrapFromAndroidKeystore(storedValue: String): String? {
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
                storedValue.startsWith("raw1:") -> {
                    if (isRunningInUnitTest()) storedValue.removePrefix("raw1:") else null
                }
                else -> {
                    if (isRunningInUnitTest()) storedValue else null
                }
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

    fun setPin(
        context: Context,
        rawPin: String,
        iterations: Int = PBKDF2_ITERATIONS
    ): Boolean {
        val normalized = rawPin.trim()
        val safeIterations = iterations.coerceIn(MIN_PBKDF2_ITERATIONS, MAX_PBKDF2_ITERATIONS)
        val (saltHex, derivedHex) = createSaltedPinHash(normalized, safeIterations) ?: return false
        val wrappedHash = wrapWithAndroidKeystore(derivedHex) ?: return false
        prefs(context).edit()
            .putString(KEY_PIN_SALT, saltHex)
            .putString(KEY_PIN_HASH, wrappedHash)
            .putInt(KEY_PIN_ITERATIONS, safeIterations)
            .putInt(KEY_PIN_LENGTH, normalized.length)
            .remove(KEY_LEGACY_PIN_HASH)
            .remove(KEY_LEGACY_PIN_SALT)
            .putInt(KEY_FAILED_ATTEMPTS, 0)
            .putLong(KEY_LOCKOUT_UNTIL_MS, 0L)
            .putLong(KEY_LOCKOUT_DURATION_MS, 0L)
            .remove(KEY_LOCKOUT_SEAL)
            .apply()
        return true
    }

    fun getFailedAttempts(context: Context): Int =
        prefs(context).getInt(KEY_FAILED_ATTEMPTS, 0)

    /**
     * SEC-06: Monotonic lockout calculation using SystemClock.elapsedRealtime()
     * so changing the device wall clock cannot bypass or shorten active lockouts.
     * Also detects device reboots (where elapsedRealtime resets near 0) and re-anchors
     * the remaining lockout duration safely.
     */
    fun getRemainingLockoutSeconds(context: Context, nowMs: Long = monotonicNowMs()): Int {
        val p = prefs(context)
        val failed = p.getInt(KEY_FAILED_ATTEMPTS, 0)
        var until = p.getLong(KEY_LOCKOUT_UNTIL_MS, 0L)
        val duration = p.getLong(KEY_LOCKOUT_DURATION_MS, 0L)
        if (failed >= 3 && until == 0L && duration == 0L) {
            // Tamper recovery: if failed attempts >= 3 but lockout timestamp was cleared, re-apply lockout
            val recomputed = computeLockoutDurationMs(failed)
            until = nowMs + recomputed
            p.edit()
                .putLong(KEY_LOCKOUT_UNTIL_MS, until)
                .putLong(KEY_LOCKOUT_DURATION_MS, recomputed)
                .apply()
        } else if (until > 0L && duration > 0L && nowMs + duration < until) {
            // Device rebooted and monotonic clock reset: re-anchor full lockout duration from current monotonic time
            until = nowMs + duration
            p.edit().putLong(KEY_LOCKOUT_UNTIL_MS, until).apply()
        }
        if (until <= nowMs) return 0
        return ceil((until - nowMs) / 1000.0).toInt().coerceAtLeast(1)
    }

    fun resetLockoutState(context: Context) {
        prefs(context).edit()
            .putInt(KEY_FAILED_ATTEMPTS, 0)
            .putLong(KEY_LOCKOUT_UNTIL_MS, 0L)
            .putLong(KEY_LOCKOUT_DURATION_MS, 0L)
            .remove(KEY_LOCKOUT_SEAL)
            .apply()
    }

    /**
     * SEC-06: Escalating lockout durations up to 1 hour (3,600,000 ms).
     */
    internal fun computeLockoutDurationMs(failedAttempts: Int): Long {
        return when {
            failedAttempts < 3 -> 0L
            failedAttempts == 3 -> 30_000L       // 30 seconds
            failedAttempts == 4 -> 60_000L       // 1 minute
            failedAttempts == 5 -> 300_000L      // 5 minutes
            failedAttempts == 6 -> 900_000L      // 15 minutes
            failedAttempts == 7 -> 1_800_000L    // 30 minutes
            else -> 3_600_000L                   // 1 hour
        }
    }

    /**
     * Verifies a PIN with PBKDF2-HMAC-SHA256, constant-time comparison, and progressive rate-limiting lockout.
     * Transparently migrates legacy SHA-256 PINs or lower-iteration PBKDF2 PINs to 600,000 iterations on unlock.
     */
    fun verifyPin(
        context: Context,
        rawPin: String,
        nowMs: Long = monotonicNowMs()
    ): Boolean {
        if (getRemainingLockoutSeconds(context, nowMs) > 0) {
            return false
        }

        val normalized = rawPin.trim()
        if (normalized.length !in 4..MAX_PIN_LENGTH || !normalized.all { it.isDigit() }) {
            recordFailedAttempt(context, nowMs)
            return false
        }

        val p = prefs(context)
        val saltHex = p.getString(KEY_PIN_SALT, null)
        val wrappedStoredHash = p.getString(KEY_PIN_HASH, null)

        val matched: Boolean
        if (!saltHex.isNullOrBlank() && !wrappedStoredHash.isNullOrBlank()) {
            val iterations = p.getInt(KEY_PIN_ITERATIONS, LEGACY_PBKDF2_ITERATIONS)
                .coerceIn(MIN_PBKDF2_ITERATIONS, MAX_PBKDF2_ITERATIONS)
            val storedHashHex = unwrapFromAndroidKeystore(wrappedStoredHash) ?: return false
            val candidateHashHex = pbkdf2Hex(normalized, hexToBytes(saltHex), iterations)
            matched = MessageDigest.isEqual(
                storedHashHex.toByteArray(Charsets.UTF_8),
                candidateHashHex.toByteArray(Charsets.UTF_8)
            )
            if (matched && iterations < PBKDF2_ITERATIONS && normalized.length >= MIN_PIN_LENGTH) {
                // SEC-06: Automatically upgrade PBKDF2 iteration count to 600,000 on successful unlock
                setPin(context, normalized, PBKDF2_ITERATIONS)
            }
        } else {
            // Legacy SHA-256 fallback & automatic upgrade to PBKDF2
            val legacySalt = p.getString(KEY_LEGACY_PIN_SALT, null) ?: return false
            val legacyHash = p.getString(KEY_LEGACY_PIN_HASH, null) ?: return false
            val candidateLegacy = sha256Hex("$legacySalt:$normalized")
            matched = MessageDigest.isEqual(
                legacyHash.toByteArray(Charsets.UTF_8),
                candidateLegacy.toByteArray(Charsets.UTF_8)
            )
            if (matched && normalized.length >= MIN_PIN_LENGTH) {
                setPin(context, normalized, PBKDF2_ITERATIONS)
            }
        }

        return if (matched) {
            resetLockoutState(context)
            true
        } else {
            recordFailedAttempt(context, nowMs)
            false
        }
    }

    private fun recordFailedAttempt(context: Context, nowMs: Long) {
        val p = prefs(context)
        val nextFailed = p.getInt(KEY_FAILED_ATTEMPTS, 0) + 1
        val lockoutDuration = computeLockoutDurationMs(nextFailed)
        val lockoutUntil = if (lockoutDuration > 0L) nowMs + lockoutDuration else 0L
        val seal = wrapWithAndroidKeystore("$nextFailed:$lockoutUntil:$lockoutDuration").orEmpty()
        p.edit()
            .putInt(KEY_FAILED_ATTEMPTS, nextFailed)
            .putLong(KEY_LOCKOUT_UNTIL_MS, lockoutUntil)
            .putLong(KEY_LOCKOUT_DURATION_MS, lockoutDuration)
            .putString(KEY_LOCKOUT_SEAL, seal)
            .apply()
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
            .remove(KEY_LOCKOUT_DURATION_MS)
            .remove(KEY_LOCKOUT_SEAL)
            .putBoolean(KEY_APP_LOCK, false)
            .apply()
    }

    /**
     * SEC-03: Encrypts a JSON backup string using AES-256-GCM with a key derived from [password]
     * via PBKDF2-HMAC-SHA256 (600,000 iterations by default, 128-bit random salt, 96-bit random IV),
     * and binds the envelope metadata header as GCM Additional Authenticated Data (AAD).
     * Requires a minimum password length of 10 characters.
     */
    fun encryptBackupJson(
        plainJson: String,
        password: String,
        iterations: Int = PBKDF2_ITERATIONS
    ): String {
        val cleanPass = password.trim()
        require(cleanPass.length >= MIN_BACKUP_PASSWORD_LENGTH) {
            "Le mot de passe de sauvegarde doit contenir au moins $MIN_BACKUP_PASSWORD_LENGTH caractères."
        }
        val safeIterations = iterations.coerceIn(MIN_PBKDF2_ITERATIONS, MAX_PBKDF2_ITERATIONS)

        val random = SecureRandom()
        val saltBytes = ByteArray(SALT_BYTES).also { random.nextBytes(it) }
        val ivBytes = ByteArray(GCM_IV_BYTES).also { random.nextBytes(it) }
        val saltHex = bytesToHex(saltBytes)
        val ivHex = bytesToHex(ivBytes)
        val kdfAlg = "PBKDF2WithHmacSHA256"
        val cipherAlg = "AES/GCM/NoPadding"
        val encVersion = 2

        val keyBytes = deriveAesKeyBytes(cleanPass, saltBytes, safeIterations)
        val secretKey = SecretKeySpec(keyBytes, "AES")
        val cipher = Cipher.getInstance(cipherAlg)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_BITS, ivBytes))

        val aadBytes = buildEnvelopeAad(
            version = encVersion,
            kdf = kdfAlg,
            iterations = safeIterations,
            cipher = cipherAlg,
            saltHex = saltHex,
            ivHex = ivHex
        )
        cipher.updateAAD(aadBytes)
        val cipherBytes = cipher.doFinal(plainJson.toByteArray(Charsets.UTF_8))

        return JSONObject().apply {
            put("masroufiEncrypted", true)
            put("encryptionVersion", encVersion)
            put("kdf", kdfAlg)
            put("iterations", safeIterations)
            put("cipher", cipherAlg)
            put("saltHex", saltHex)
            put("ivHex", ivHex)
            put("ciphertextBase64", Base64.encodeToString(cipherBytes, Base64.NO_WRAP))
        }.toString(2)
    }

    /**
     * SEC-03: Decrypts and authenticates an AES-256-GCM encrypted Masroufi backup envelope.
     * Supports both v2 envelopes (with GCM AAD header authentication and up to 1,000,000 PBKDF2 iterations)
     * and legacy v1 envelopes (65,536 iterations).
     * Throws IllegalArgumentException if the password is wrong or any envelope/ciphertext field was tampered with.
     */
    fun decryptBackupJson(encryptedEnvelopeJson: String, password: String): String {
        val cleanPass = password.trim()
        require(cleanPass.isNotEmpty()) { "Password required to decrypt backup." }
        return try {
            val obj = JSONObject(encryptedEnvelopeJson.trim())
            require(obj.optBoolean("masroufiEncrypted", false)) { "Not an encrypted Masroufi backup." }
            val encVersion = obj.optInt("encryptionVersion", 1)
            val rawIterations = obj.optInt("iterations", LEGACY_PBKDF2_ITERATIONS)
            require(rawIterations in MIN_PBKDF2_ITERATIONS..MAX_PBKDF2_ITERATIONS) {
                "Unsupported PBKDF2 iteration count."
            }
            val kdfAlg = obj.optString("kdf", "PBKDF2WithHmacSHA256")
            val cipherAlg = obj.optString("cipher", "AES/GCM/NoPadding")
            require(kdfAlg == "PBKDF2WithHmacSHA256" && cipherAlg == "AES/GCM/NoPadding") {
                "Unsupported cipher or KDF algorithm."
            }
            val saltHex = obj.getString("saltHex")
            val ivHex = obj.getString("ivHex")
            val saltBytes = hexToBytes(saltHex)
            val ivBytes = hexToBytes(ivHex)
            require(saltBytes.size == SALT_BYTES && ivBytes.size == GCM_IV_BYTES) {
                "Invalid salt or IV length."
            }
            val cipherBytes = Base64.decode(obj.getString("ciphertextBase64"), Base64.DEFAULT)

            val keyBytes = deriveAesKeyBytes(cleanPass, saltBytes, rawIterations)
            val secretKey = SecretKeySpec(keyBytes, "AES")
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_BITS, ivBytes))
            if (encVersion >= 2) {
                val aadBytes = buildEnvelopeAad(
                    version = encVersion,
                    kdf = kdfAlg,
                    iterations = rawIterations,
                    cipher = cipherAlg,
                    saltHex = saltHex,
                    ivHex = ivHex
                )
                cipher.updateAAD(aadBytes)
            }
            val plainBytes = cipher.doFinal(cipherBytes)
            String(plainBytes, Charsets.UTF_8)
        } catch (e: Exception) {
            throw IllegalArgumentException("Mot de passe incorrect ou fichier de sauvegarde chiffré altéré.", e)
        }
    }

    private fun buildEnvelopeAad(
        version: Int,
        kdf: String,
        iterations: Int,
        cipher: String,
        saltHex: String,
        ivHex: String
    ): ByteArray {
        return "v$version|$kdf|$iterations|$cipher|${saltHex.lowercase()}|${ivHex.lowercase()}"
            .toByteArray(Charsets.UTF_8)
    }

    fun isEncryptedBackup(rawContent: String): Boolean {
        val trimmed = rawContent.trim()
        if (!trimmed.startsWith("{")) return false
        return runCatching {
            JSONObject(trimmed).optBoolean("masroufiEncrypted", false)
        }.getOrDefault(false)
    }

    /**
     * SEC-07: Persists the pre-restore rollback snapshot encrypted with Android Keystore
     * to private app storage so it survives process death and is never stored in plaintext.
     */
    fun saveEncryptedRollbackSnapshot(context: Context, plainRollbackJson: String): Boolean {
        val wrapped = wrapWithAndroidKeystore(plainRollbackJson) ?: return false
        return runCatching {
            File(context.applicationContext.filesDir, ROLLBACK_FILE_NAME).writeText(wrapped, Charsets.UTF_8)
            true
        }.getOrDefault(false)
    }

    fun loadEncryptedRollbackSnapshot(context: Context): String? {
        return runCatching {
            val file = File(context.applicationContext.filesDir, ROLLBACK_FILE_NAME)
            if (!file.exists()) return null
            val wrapped = file.readText(Charsets.UTF_8)
            unwrapFromAndroidKeystore(wrapped)
        }.getOrNull()
    }

    fun clearEncryptedRollbackSnapshot(context: Context) {
        runCatching {
            val file = File(context.applicationContext.filesDir, ROLLBACK_FILE_NAME)
            if (file.exists()) file.delete()
        }
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

    fun canAuthenticateBiometricOrCredential(context: Context): Boolean {
        val bm = BiometricManager.from(context)
        val authenticators = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
        } else {
            BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL
        }
        return bm.canAuthenticate(authenticators) == BiometricManager.BIOMETRIC_SUCCESS || isDeviceSecure(context)
    }

    /**
     * SEC-06: Launches AndroidX BiometricPrompt (replacing deprecated createConfirmDeviceCredentialIntent).
     */
    fun launchBiometricOrDevicePrompt(
        activity: FragmentActivity,
        title: String,
        subtitle: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit = {}
    ) {
        runCatching {
            val executor = ContextCompat.getMainExecutor(activity)
            val prompt = BiometricPrompt(
                activity,
                executor,
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        super.onAuthenticationSucceeded(result)
                        resetLockoutState(activity)
                        onSuccess()
                    }

                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        super.onAuthenticationError(errorCode, errString)
                        if (errorCode != BiometricPrompt.ERROR_USER_CANCELED &&
                            errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON &&
                            errorCode != BiometricPrompt.ERROR_CANCELED
                        ) {
                            onError(errString.toString())
                        }
                    }
                }
            )
            val authenticators = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
            } else {
                BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL
            }
            val promptInfo = BiometricPrompt.PromptInfo.Builder()
                .setTitle(title)
                .setSubtitle(subtitle)
                .setAllowedAuthenticators(authenticators)
                .build()
            prompt.authenticate(promptInfo)
        }.onFailure { e ->
            onError(e.localizedMessage ?: "Biometric prompt unavailable")
        }
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

    /**
     * SEC-07: Enforces a strict maximum file size (10 MB default) when reading backup files
     * to prevent OutOfMemoryError / Denial-of-Service on malformed imports.
     */
    fun readTextFromUri(
        context: Context,
        uri: Uri,
        maxBytes: Int = MAX_BACKUP_FILE_BYTES
    ): String? {
        return runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val buffer = ByteArrayOutputStream()
                val chunk = ByteArray(8192)
                var totalRead = 0
                while (true) {
                    val read = input.read(chunk)
                    if (read == -1) break
                    totalRead += read
                    if (totalRead > maxBytes) {
                        throw IllegalArgumentException("Backup file exceeds maximum allowed size (${maxBytes / (1024 * 1024)} MB).")
                    }
                    buffer.write(chunk, 0, read)
                }
                buffer.toString(Charsets.UTF_8.name())
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
        require(clean.length % 2 == 0) { "Odd hex length" }
        val len = clean.length
        val out = ByteArray(len / 2)
        var i = 0
        while (i < len) {
            val hi = Character.digit(clean[i], 16)
            val lo = Character.digit(clean[i + 1], 16)
            require(hi != -1 && lo != -1) { "Invalid hex character" }
            out[i / 2] = ((hi shl 4) + lo).toByte()
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
    val fragmentActivity = context as? FragmentActivity
    val pal = paletteForKey(accentPalette)
    var enteredPin by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var lockoutSeconds by remember { mutableIntStateOf(AppSecurityManager.getRemainingLockoutSeconds(context)) }
    var showRecoveryConfirmDialog by remember { mutableStateOf(false) }

    val hasPin = remember(context) { AppSecurityManager.hasPinConfigured(context) }
    val pinLength = remember(context) { AppSecurityManager.getConfiguredPinLength(context) }
    val isDeviceSecure = remember(context) { AppSecurityManager.canAuthenticateBiometricOrCredential(context) }
    val canUseDeviceCredential = remember(context) {
        AppSecurityManager.isDeviceCredentialAllowed(context) && isDeviceSecure
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
        "en" -> "Unlock with Biometrics / Device Lock"
        else -> "Déverrouiller avec Biométrie / Appareil"
    }
    val signOutRecoveryLabel = when (language) {
        "ar" -> "نسيت الرمز؟ تأكيد هوية الجهاز وإعادة الدخول بحساب Google"
        "darija" -> "نسيتي الكود؟ أكد بصمة التليفون وعاود دخل بحساب Google"
        "en" -> "Forgot PIN? Verify device & re-authenticate with Google"
        else -> "PIN oublié ? Vérifier l'appareil et se reconnecter via Google"
    }

    fun launchBiometricUnlock() {
        if (fragmentActivity != null) {
            AppSecurityManager.launchBiometricOrDevicePrompt(
                activity = fragmentActivity,
                title = titleText,
                subtitle = subText,
                onSuccess = onUnlocked,
                onError = { msg -> errorMessage = msg }
            )
        }
    }

    /**
     * SEC-02: Require device biometric / screen-lock authentication before Forgot PIN recovery
     * can clear the session and sign out.
     */
    fun initiateAuthenticatedRecovery() {
        if (fragmentActivity != null && isDeviceSecure) {
            AppSecurityManager.launchBiometricOrDevicePrompt(
                activity = fragmentActivity,
                title = when (language) {
                    "ar" -> "تأكيد استرجاع رمز PIN"
                    "darija" -> "تأكيد استرجاع كود PIN"
                    "en" -> "Confirm PIN Recovery"
                    else -> "Confirmer la réinitialisation du PIN"
                },
                subtitle = when (language) {
                    "ar" -> "تحقق من بصمة أو قفل الجهاز لمسح الجلسة المحلية وإعادة تسجيل الدخول"
                    "darija" -> "أكد بصمة أو كود التليفون باش تمسح الجلسة وتعاود تدخل"
                    "en" -> "Authenticate with device lock to clear local cache and sign in again"
                    else -> "Authentifiez-vous avec l'appareil pour effacer le cache local et vous reconnecter"
                },
                onSuccess = {
                    AppSecurityManager.beginPinRecoverySignOut(context)
                    onSignOutRecovery()
                },
                onError = { msg -> errorMessage = msg }
            )
        } else {
            showRecoveryConfirmDialog = true
        }
    }

    // SEC-06: Automatically prompt for Biometrics on launch when allowed, with PIN pad as fallback
    LaunchedEffect(hasPin, canUseDeviceCredential) {
        if (!hasPin && !canUseDeviceCredential) {
            onUnlocked()
        } else if (canUseDeviceCredential && fragmentActivity != null && lockoutSeconds == 0) {
            launchBiometricUnlock()
        }
    }

    val isLockedOut = lockoutSeconds > 0

    if (showRecoveryConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showRecoveryConfirmDialog = false },
            title = {
                Text(
                    text = when (language) {
                        "ar" -> "إعادة تعيين القفل وتسجيل الخروج"
                        "darija" -> "إعادة تعيين القفل والخروج من الحساب"
                        "en" -> "Reset App Lock & Sign Out"
                        else -> "Réinitialiser le verrouillage & Déconnexion"
                    },
                    fontWeight = FontWeight.ExtraBold
                )
            },
            text = {
                Text(
                    text = when (language) {
                        "ar" -> "سيتم مسح ذاكرة التخزين المؤقت المحلية وإلغاء الجلسة الحالية، وسيتعين عليك اختيار حساب Google والمصادقة من جديد."
                        "darija" -> "غادي يتمسح الكاش المحلي وتخرج من الحساب، وغادي يخصك تختار حساب Google وتعاود تدخل."
                        "en" -> "Local cached data will be wiped and you will be required to explicitly re-select and authenticate your Google account."
                        else -> "Le cache local sera intégralement effacé et vous devrez sélectionner explicitement votre compte Google pour vous reconnecter."
                    }
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showRecoveryConfirmDialog = false
                        AppSecurityManager.beginPinRecoverySignOut(context)
                        onSignOutRecovery()
                    },
                    modifier = Modifier.testTag("confirm_forgot_pin_recovery_btn")
                ) {
                    Text(
                        text = when (language) {
                            "ar" -> "تأكيد الخروج"
                            "darija" -> "تأكيد الخروج"
                            "en" -> "Sign Out & Reset"
                            else -> "Se déconnecter"
                        },
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showRecoveryConfirmDialog = false }) {
                    Text(
                        text = when (language) {
                            "ar" -> "إلغاء"
                            "darija" -> "إلغاء"
                            "en" -> "Cancel"
                            else -> "Annuler"
                        }
                    )
                }
            }
        )
    }

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
                                                        .clickable { launchBiometricUnlock() }
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
                        onClick = { launchBiometricUnlock() },
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
                    onClick = { initiateAuthenticatedRecovery() },
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
