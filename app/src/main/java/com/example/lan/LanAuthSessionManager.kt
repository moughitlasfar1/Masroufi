package com.example.lan

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Locale

sealed class LanPinAuthResult {
    data class Success(val token: String, val idleTimeoutMinutes: Int) : LanPinAuthResult()
    data class InvalidPin(val remainingAttempts: Int) : LanPinAuthResult()
    data class LockedOut(val retryAfterSeconds: Long) : LanPinAuthResult()
}

/**
 * In-memory security gate and session manager for the Local LAN Companion server.
 *
 * Security invariants:
 * - Ephemeral 6-digit PIN generated via [SecureRandom] (memory-only, never persisted or logged).
 * - Constant-time PIN verification via [MessageDigest.isEqual].
 * - Brute-force lockout: 5 consecutive failed PIN attempts lock auth for 5 minutes (HTTP 429 + Retry-After).
 * - Memory-only 256-bit random Bearer session tokens wiped on logout, PIN regeneration, or server stop.
 * - Strict `Host`, `Origin`, and `Sec-Fetch-Site` validation against DNS rebinding and cross-site attacks.
 */
class LanAuthSessionManager(
    initialIdleTimeoutMinutes: Int = DEFAULT_IDLE_TIMEOUT_MINUTES,
    private val clockMs: () -> Long = { System.currentTimeMillis() },
    private val secureRandom: SecureRandom = SecureRandom()
) {
    companion object {
        const val PIN_LENGTH = 6
        const val MAX_FAILED_ATTEMPTS = 5
        const val LOCKOUT_DURATION_MS = 5L * 60L * 1000L // 5 minutes
        const val DEFAULT_IDLE_TIMEOUT_MINUTES = 15
        const val MIN_IDLE_TIMEOUT_MINUTES = 5
        const val MAX_IDLE_TIMEOUT_MINUTES = 60
        private const val TOKEN_BYTES = 32 // 256-bit entropy
        private const val MAX_CONCURRENT_SESSIONS = 8
    }

    private val lock = Any()
    private var currentPin: String = generateSixDigitPin()
    private val activeTokens = LinkedHashMap<String, Long>() // token -> lastActivityEpochMs
    private var consecutiveFailures: Int = 0
    private var lockedUntilEpochMs: Long = 0L
    private var lastActivityEpochMs: Long = clockMs()
    private var idleTimeoutMinutes: Int = initialIdleTimeoutMinutes.coerceIn(
        MIN_IDLE_TIMEOUT_MINUTES,
        MAX_IDLE_TIMEOUT_MINUTES
    )

    fun currentPin(): String = synchronized(lock) { currentPin }

    fun activeSessionsCount(): Int = synchronized(lock) {
        pruneExpiredTokensLocked(clockMs())
        activeTokens.size
    }

    fun idleTimeoutMinutes(): Int = synchronized(lock) { idleTimeoutMinutes }

    fun setIdleTimeoutMinutes(minutes: Int) = synchronized(lock) {
        idleTimeoutMinutes = minutes.coerceIn(MIN_IDLE_TIMEOUT_MINUTES, MAX_IDLE_TIMEOUT_MINUTES)
    }

    fun lastActivityEpochMs(): Long = synchronized(lock) { lastActivityEpochMs }

    fun isIdleExpired(): Boolean = synchronized(lock) {
        val now = clockMs()
        val timeoutMs = idleTimeoutMinutes.toLong() * 60L * 1000L
        (now - lastActivityEpochMs) >= timeoutMs
    }

    fun touchActivity() = synchronized(lock) {
        lastActivityEpochMs = clockMs()
    }

    /**
     * Generates a fresh 6-digit ephemeral PIN, revokes all active Bearer tokens immediately,
     * and resets brute-force lockout counters.
     */
    fun regeneratePin(): String = synchronized(lock) {
        currentPin = generateSixDigitPin()
        activeTokens.clear()
        consecutiveFailures = 0
        lockedUntilEpochMs = 0L
        lastActivityEpochMs = clockMs()
        currentPin
    }

    /**
     * Wipes all active tokens and rotates the in-memory PIN so stopped instances hold no reusable secret.
     */
    fun wipeAll() = synchronized(lock) {
        activeTokens.clear()
        consecutiveFailures = 0
        lockedUntilEpochMs = 0L
        currentPin = ""
    }

    fun authenticatePin(candidatePin: String): LanPinAuthResult = synchronized(lock) {
        val now = clockMs()
        if (lockedUntilEpochMs > now) {
            val remainingSec = ((lockedUntilEpochMs - now + 999L) / 1000L).coerceAtLeast(1L)
            return LanPinAuthResult.LockedOut(retryAfterSeconds = remainingSec)
        }
        // Reset expired lockout window cleanly
        if (lockedUntilEpochMs in 1L..now) {
            lockedUntilEpochMs = 0L
            consecutiveFailures = 0
        }

        val expected = currentPin
        val sanitizedCandidate = candidatePin.trim()
        val validFormat = expected.length == PIN_LENGTH &&
            sanitizedCandidate.length == PIN_LENGTH &&
            sanitizedCandidate.all { it in '0'..'9' }

        val matches = validFormat && MessageDigest.isEqual(
            expected.toByteArray(Charsets.UTF_8),
            sanitizedCandidate.toByteArray(Charsets.UTF_8)
        )

        if (!matches) {
            consecutiveFailures++
            if (consecutiveFailures >= MAX_FAILED_ATTEMPTS) {
                lockedUntilEpochMs = now + LOCKOUT_DURATION_MS
                val retryAfterSec = LOCKOUT_DURATION_MS / 1000L
                return LanPinAuthResult.LockedOut(retryAfterSeconds = retryAfterSec)
            }
            return LanPinAuthResult.InvalidPin(remainingAttempts = MAX_FAILED_ATTEMPTS - consecutiveFailures)
        }

        // Auth succeeded
        consecutiveFailures = 0
        lockedUntilEpochMs = 0L
        pruneExpiredTokensLocked(now)
        if (activeTokens.size >= MAX_CONCURRENT_SESSIONS) {
            val oldestKey = activeTokens.entries.minByOrNull { it.value }?.key
            if (oldestKey != null) activeTokens.remove(oldestKey)
        }
        val token = generateRandomTokenHex()
        activeTokens[token] = now
        lastActivityEpochMs = now
        return LanPinAuthResult.Success(token = token, idleTimeoutMinutes = idleTimeoutMinutes)
    }

    /**
     * Validates `Authorization: Bearer <token>` in constant time against memory-only sessions.
     */
    fun validateBearerHeader(authorizationHeader: String?): String? = synchronized(lock) {
        val raw = authorizationHeader?.trim() ?: return null
        if (!raw.startsWith("Bearer ", ignoreCase = true)) return null
        val candidate = raw.substring(7).trim()
        if (candidate.length != TOKEN_BYTES * 2) return null

        val now = clockMs()
        pruneExpiredTokensLocked(now)
        val candidateBytes = candidate.toByteArray(Charsets.UTF_8)
        var matchedToken: String? = null
        for (stored in activeTokens.keys) {
            if (MessageDigest.isEqual(stored.toByteArray(Charsets.UTF_8), candidateBytes)) {
                matchedToken = stored
            }
        }
        if (matchedToken != null) {
            activeTokens[matchedToken] = now
            lastActivityEpochMs = now
        }
        return matchedToken
    }

    fun revokeToken(token: String): Boolean = synchronized(lock) {
        activeTokens.remove(token) != null
    }

    /**
     * Strict DNS-rebinding check: the `Host` header MUST match `<bindHost>:<boundPort>` exactly.
     */
    fun isAllowedHostHeader(hostHeader: String?, expectedHost: String, expectedPort: Int): Boolean {
        val raw = hostHeader?.trim()?.lowercase(Locale.ROOT) ?: return false
        if (raw.isEmpty()) return false
        val expected = "${expectedHost.lowercase(Locale.ROOT)}:$expectedPort"
        return raw == expected
    }

    /**
     * Strict cross-site / CSRF / DNS-rebinding check for mutating requests (`POST`).
     * - Rejects `Sec-Fetch-Site: cross-site`.
     * - If `Origin` header is present, it MUST equal `http://<bindHost>:<boundPort>` exactly.
     */
    fun isAllowedOriginForMutation(
        originHeader: String?,
        secFetchSiteHeader: String?,
        expectedHost: String,
        expectedPort: Int
    ): Boolean {
        val fetchSite = secFetchSiteHeader?.trim()?.lowercase(Locale.ROOT)
        if (fetchSite == "cross-site") return false
        val origin = originHeader?.trim()?.lowercase(Locale.ROOT) ?: return true
        if (origin.isEmpty() || origin == "null") return false
        val expectedOrigin = "http://${expectedHost.lowercase(Locale.ROOT)}:$expectedPort"
        return origin == expectedOrigin
    }

    private fun pruneExpiredTokensLocked(now: Long) {
        val timeoutMs = idleTimeoutMinutes.toLong() * 60L * 1000L
        val it = activeTokens.entries.iterator()
        while (it.hasNext()) {
            val entry = it.next()
            if ((now - entry.value) >= timeoutMs) {
                it.remove()
            }
        }
    }

    private fun generateSixDigitPin(): String {
        val number = secureRandom.nextInt(1_000_000)
        return String.format(Locale.ROOT, "%06d", number)
    }

    private fun generateRandomTokenHex(): String {
        val bytes = ByteArray(TOKEN_BYTES)
        secureRandom.nextBytes(bytes)
        val sb = StringBuilder(TOKEN_BYTES * 2)
        for (b in bytes) {
            val v = b.toInt() and 0xFF
            sb.append(HEX_CHARS[v ushr 4])
            sb.append(HEX_CHARS[v and 0x0F])
        }
        return sb.toString()
    }
}

private val HEX_CHARS = charArrayOf(
    '0', '1', '2', '3', '4', '5', '6', '7',
    '8', '9', 'a', 'b', 'c', 'd', 'e', 'f'
)
