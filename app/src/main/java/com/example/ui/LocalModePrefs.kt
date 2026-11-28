package com.example.ui

import android.content.Context
import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import org.json.JSONObject

const val LOCAL_DEVICE_USER_ID = com.example.data.LOCAL_DEVICE_USER_ID
private const val UI_PREFS_NAME = "masroufi_ui_prefs"
private const val KEY_LOCAL_MODE_ENABLED = "local_mode_enabled"
private const val KEY_MORE_FEATURES_ENABLED = "more_features_enabled"

fun isLocalModeEnabled(context: Context): Boolean =
    context.applicationContext
        .getSharedPreferences(UI_PREFS_NAME, Context.MODE_PRIVATE)
        .getBoolean(KEY_LOCAL_MODE_ENABLED, false)

fun setLocalModeEnabled(context: Context, enabled: Boolean) {
    context.applicationContext
        .getSharedPreferences(UI_PREFS_NAME, Context.MODE_PRIVATE)
        .edit()
        .putBoolean(KEY_LOCAL_MODE_ENABLED, enabled)
        .apply()
}

fun isMoreFeaturesEnabled(context: Context, hasExistingData: Boolean = false): Boolean {
    val prefs = context.applicationContext.getSharedPreferences(UI_PREFS_NAME, Context.MODE_PRIVATE)
    if (prefs.contains(KEY_MORE_FEATURES_ENABLED)) {
        return prefs.getBoolean(KEY_MORE_FEATURES_ENABLED, false)
    }
    return hasExistingData
}

fun setMoreFeaturesEnabled(context: Context, enabled: Boolean) {
    context.applicationContext
        .getSharedPreferences(UI_PREFS_NAME, Context.MODE_PRIVATE)
        .edit()
        .putBoolean(KEY_MORE_FEATURES_ENABLED, enabled)
        .apply()
}

/**
 * S8: Generates a 32-byte cryptographically random nonce and its SHA-256 hex digest.
 * Returns `(rawNonceHex, hashedNonceHex)`.
 */
internal fun generateSignInNonce(): Pair<String, String> {
    val rawBytes = ByteArray(32)
    SecureRandom().nextBytes(rawBytes)
    val rawHex = rawBytes.joinToString("") { "%02x".format(it) }
    val digestBytes = MessageDigest.getInstance("SHA-256").digest(rawHex.toByteArray(Charsets.UTF_8))
    val hashedHex = digestBytes.joinToString("") { "%02x".format(it) }
    return rawHex to hashedHex
}

/**
 * S8: Verifies that a Google ID token JWT's payload contains a `nonce` claim matching
 * [expectedNonce] using constant-time byte comparison.
 */
internal fun verifyIdTokenNonce(idToken: String, expectedNonce: String): Boolean {
    if (idToken.isBlank() || expectedNonce.isBlank()) return false
    return runCatching {
        val parts = idToken.split(".")
        if (parts.size < 2) return@runCatching false
        val payloadBytes = Base64.decode(
            parts[1],
            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
        )
        val payloadJson = JSONObject(String(payloadBytes, Charsets.UTF_8))
        val tokenNonce = payloadJson.optString("nonce", "")
        if (tokenNonce.isBlank()) return@runCatching false
        MessageDigest.isEqual(
            tokenNonce.toByteArray(Charsets.UTF_8),
            expectedNonce.toByteArray(Charsets.UTF_8)
        )
    }.getOrDefault(false)
}
