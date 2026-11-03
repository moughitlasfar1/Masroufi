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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.example.ui.theme.AccentEmerald
import com.example.ui.theme.paletteForKey
import java.security.MessageDigest
import java.security.SecureRandom

object AppSecurityManager {
    private const val PREFS_NAME = "masroufi_security_prefs"
    private const val KEY_FLAG_SECURE = "flag_secure_enabled"
    private const val KEY_APP_LOCK = "app_lock_enabled"
    private const val KEY_DEVICE_CREDENTIAL = "device_credential_allowed"
    private const val KEY_PIN_HASH = "pin_sha256_hash"
    private const val KEY_PIN_SALT = "pin_sha256_salt"

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

    fun hasPinConfigured(context: Context): Boolean {
        val hash = prefs(context).getString(KEY_PIN_HASH, null)
        val salt = prefs(context).getString(KEY_PIN_SALT, null)
        return !hash.isNullOrBlank() && !salt.isNullOrBlank()
    }

    internal fun createSaltedPinHash(rawPin: String): Pair<String, String>? {
        val normalized = rawPin.trim()
        if (normalized.length < 4 || !normalized.all { it.isDigit() }) return null
        val saltBytes = ByteArray(16)
        SecureRandom().nextBytes(saltBytes)
        val saltHex = bytesToHex(saltBytes)
        val hashHex = sha256Hex("$saltHex:$normalized")
        return saltHex to hashHex
    }

    fun setPin(context: Context, rawPin: String): Boolean {
        val (saltHex, hashHex) = createSaltedPinHash(rawPin) ?: return false
        prefs(context).edit()
            .putString(KEY_PIN_SALT, saltHex)
            .putString(KEY_PIN_HASH, hashHex)
            .apply()
        return true
    }

    fun verifyPin(context: Context, rawPin: String): Boolean {
        val normalized = rawPin.trim()
        val saltHex = prefs(context).getString(KEY_PIN_SALT, null) ?: return false
        val storedHashHex = prefs(context).getString(KEY_PIN_HASH, null) ?: return false
        val candidateHashHex = sha256Hex("$saltHex:$normalized")
        return MessageDigest.isEqual(
            storedHashHex.toByteArray(Charsets.UTF_8),
            candidateHashHex.toByteArray(Charsets.UTF_8)
        )
    }

    fun clearPin(context: Context) {
        prefs(context).edit()
            .remove(KEY_PIN_HASH)
            .remove(KEY_PIN_SALT)
            .putBoolean(KEY_APP_LOCK, false)
            .apply()
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

    val hasPin = remember(context) { AppSecurityManager.hasPinConfigured(context) }
    val canUseDeviceCredential = remember(context) {
        AppSecurityManager.isDeviceCredentialAllowed(context) && AppSecurityManager.isDeviceSecure(context)
    }

    val titleText = when (language) {
        "ar" -> "التطبيق مقفل لحماية بياناتك المالية"
        "darija" -> "التطبيق مقفول باش نحميو حساباتك"
        "en" -> "Masroufi Security Lock"
        else -> "Masroufi Verrouillé"
    }
    val subText = when (language) {
        "ar" -> "أدخل رمز PIN المكون من 4 أرقام أو استخدم بصمة/قفل الجهاز للمتابعة"
        "darija" -> "دخل كود PIN ديال 4 أرقام أو حل ببصمة التليفون"
        "en" -> "Enter your 4-digit PIN or use your device screen lock to continue"
        else -> "Entrez votre code PIN à 4 chiffres ou utilisez le déverrouillage de l'appareil"
    }
    val wrongPinText = when (language) {
        "ar" -> "رمز PIN غير صحيح، حاول مرة أخرى"
        "darija" -> "الكود غالط، عاود جرب"
        "en" -> "Incorrect PIN, please try again"
        else -> "Code PIN incorrect, veuillez réessayer"
    }
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

    // If user enabled app lock without setting a custom PIN and device credential isn't available, unlock safely
    LaunchedEffect(hasPin, canUseDeviceCredential) {
        if (!hasPin && !canUseDeviceCredential) {
            onUnlocked()
        }
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
                    // 4 PIN Dots
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 8.dp)
                    ) {
                        for (i in 0 until 4) {
                            val filled = i < enteredPin.length
                            Box(
                                modifier = Modifier
                                    .size(20.dp)
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

                    if (!errorMessage.isNullOrBlank()) {
                        Text(
                            text = errorMessage!!,
                            color = AccentCoral,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Numeric Keypad
                    val rows = listOf(
                        listOf("1", "2", "3"),
                        listOf("4", "5", "6"),
                        listOf("7", "8", "9"),
                        listOf("bio", "0", "del")
                    )

                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
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
                                                    .clickable {
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
                                                    .clickable {
                                                        if (enteredPin.length < 4) {
                                                            val next = enteredPin + key
                                                            enteredPin = next
                                                            errorMessage = null
                                                            if (next.length == 4) {
                                                                if (AppSecurityManager.verifyPin(context, next)) {
                                                                    onUnlocked()
                                                                } else {
                                                                    errorMessage = wrongPinText
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
