package com.example.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * S4: Encrypted on-device key-value store backed by an AndroidKeyStore AES-256-GCM key.
 *
 * Security guarantees:
 * - Encrypts every value with a fresh 96-bit random IV and 128-bit GCM auth tag.
 * - Binds the preference key name as GCM Additional Authenticated Data (AAD) so ciphertext
 *   cannot be transplanted across keys.
 * - Fails closed on physical devices if AndroidKeyStore is unavailable.
 * - Uses a clearly tagged unit-test fallback strictly when [AppSecurityManager.isRunningInUnitTest] is true.
 */
object SecureStore {
    private const val PREFS_NAME = "masroufi_secure_kv_v1"
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "masroufi_secure_store_aes256_v1"
    private const val GCM_TAG_BITS = 128
    private const val PREFIX_KS = "ss1:"
    private const val PREFIX_TEST = "tst1:"

    private val lock = Any()

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun getOrCreateKey(): SecretKey? {
        return runCatching {
            val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            val existing = ks.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry
            if (existing != null) return existing.secretKey

            val keyGen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
            val spec = KeyGenParameterSpec.Builder(
                KEY_ALIAS,
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

    internal fun encryptValue(keyName: String, plaintext: String): String? {
        val ksEncrypted = runCatching {
            val key = getOrCreateKey() ?: return@runCatching null
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key)
            cipher.updateAAD(keyName.toByteArray(Charsets.UTF_8))
            val iv = cipher.iv
            val ct = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
            val ivB64 = Base64.encodeToString(iv, Base64.NO_WRAP)
            val ctB64 = Base64.encodeToString(ct, Base64.NO_WRAP)
            "$PREFIX_KS$ivB64:$ctB64"
        }.getOrNull()

        if (ksEncrypted != null) return ksEncrypted
        return if (AppSecurityManager.isRunningInUnitTest()) {
            // Distinct opaque encoding in unit tests so plaintext never appears raw in SharedPreferences
            val encoded = Base64.encodeToString(plaintext.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
            "$PREFIX_TEST$encoded"
        } else {
            null
        }
    }

    internal fun decryptValue(keyName: String, stored: String): String? {
        return runCatching {
            when {
                stored.startsWith(PREFIX_KS) -> {
                    val parts = stored.removePrefix(PREFIX_KS).split(":")
                    if (parts.size != 2) return null
                    val iv = Base64.decode(parts[0], Base64.NO_WRAP)
                    val ct = Base64.decode(parts[1], Base64.NO_WRAP)
                    val key = getOrCreateKey() ?: return null
                    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                    cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
                    cipher.updateAAD(keyName.toByteArray(Charsets.UTF_8))
                    String(cipher.doFinal(ct), Charsets.UTF_8)
                }
                stored.startsWith(PREFIX_TEST) -> {
                    if (!AppSecurityManager.isRunningInUnitTest()) return null
                    val raw = Base64.decode(stored.removePrefix(PREFIX_TEST), Base64.NO_WRAP)
                    String(raw, Charsets.UTF_8)
                }
                else -> null
            }
        }.getOrNull()
    }

    fun putString(context: Context, key: String, value: String): Boolean {
        val encrypted = encryptValue(key, value) ?: return false
        synchronized(lock) {
            prefs(context).edit().putString(key, encrypted).apply()
        }
        return true
    }

    fun getString(context: Context, key: String): String? {
        val stored = synchronized(lock) {
            prefs(context).getString(key, null)
        } ?: return null
        return decryptValue(key, stored)
    }

    fun remove(context: Context, key: String) {
        synchronized(lock) {
            prefs(context).edit().remove(key).apply()
        }
    }

    fun contains(context: Context, key: String): Boolean {
        return synchronized(lock) {
            prefs(context).contains(key)
        }
    }

    /**
     * One-time migration helper: if [legacyKey] exists in [legacyPrefsName], copies its value into
     * [SecureStore] under [secureKey] and deletes [legacyKey] from the plaintext SharedPreferences.
     */
    fun migrateStringFromLegacyPrefs(
        context: Context,
        legacyPrefsName: String,
        legacyKey: String,
        secureKey: String = legacyKey
    ): String? {
        val appCtx = context.applicationContext
        val legacyPrefs = appCtx.getSharedPreferences(legacyPrefsName, Context.MODE_PRIVATE)
        val existingSecure = getString(appCtx, secureKey)
        if (existingSecure != null) {
            if (legacyPrefs.contains(legacyKey)) {
                legacyPrefs.edit().remove(legacyKey).apply()
            }
            return existingSecure
        }
        val legacyValue = legacyPrefs.getString(legacyKey, null)
        if (!legacyValue.isNullOrBlank()) {
            if (putString(appCtx, secureKey, legacyValue)) {
                legacyPrefs.edit().remove(legacyKey).apply()
            }
            return legacyValue
        }
        return null
    }
}
