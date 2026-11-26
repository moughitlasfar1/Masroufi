package com.example.security

import java.security.SecureRandom
import java.util.Arrays
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Typed outcome for portable passphrase-derived backup encryption and decryption.
 */
sealed class PortableCryptoResult<out T> {
    data class Success<T>(val value: T) : PortableCryptoResult<T>()
    data class WeakPassphrase(val minLength: Int) : PortableCryptoResult<Nothing>()
    data class FileTooLarge(val actualBytes: Int, val maxBytes: Int) : PortableCryptoResult<Nothing>()
    data class UnsupportedVersion(val version: Int) : PortableCryptoResult<Nothing>()
    data class CorruptedFile(val detail: String) : PortableCryptoResult<Nothing>()
    data object WrongPassphraseOrTampered : PortableCryptoResult<Nothing>()
}

/**
 * Result of inspecting the unencrypted header/structure of a backup file before prompting or restoring.
 */
sealed class BackupEnvelopeInfo {
    data class PortableV3Encrypted(
        val encryptionVersion: Int,
        val kdf: String,
        val iterations: Int,
        val cipher: String
    ) : BackupEnvelopeInfo()

    data class LegacyV2Encrypted(
        val encryptionVersion: Int,
        val iterations: Int
    ) : BackupEnvelopeInfo()

    data object LegacyPlaintextJson : BackupEnvelopeInfo()

    data class UnsupportedVersion(val detectedVersion: Int) : BackupEnvelopeInfo()

    data class Corrupted(val reason: String) : BackupEnvelopeInfo()
}

/**
 * Cross-device portable passphrase-derived AES-256-GCM encryption engine (Backup v3).
 *
 * Design guarantees:
 * - ZERO Android Keystore or Android framework dependencies (runs identically on Android API 24+
 *   and pure JVM unit tests).
 * - KDF: PBKDF2WithHmacSHA256 with fresh random 16-byte salt, 256-bit key, default 600,000 iterations
 *   (minimum 210,000 iterations enforced; parameters stored explicitly in the envelope header).
 * - Cipher: AES/GCM/NoPadding with fresh random 12-byte IV and 128-bit authentication tag.
 * - Header integrity: Canonical versioned header (`format`, `encryptionVersion`, `kdf`, `iterations`,
 *   `cipher`, `salt`, `iv`) is bound as GCM Additional Authenticated Data (AAD), so any 1-bit tamper
 *   in header fields, salt, IV, or ciphertext fails GCM authentication immediately.
 * - Memory hygiene: Derived key bytes, plaintext UTF-8 byte buffers, and passphrase CharArrays are
 *   explicitly zeroized in `finally` blocks.
 */
object PortableBackupCrypto {
    const val FORMAT_ID = "masroufi_backup"
    const val ENCRYPTION_VERSION = 3
    const val LEGACY_V2_ENCRYPTION_VERSION = 2
    const val KDF_ALGORITHM = "PBKDF2WithHmacSHA256"
    const val CIPHER_ALGORITHM = "AES/GCM/NoPadding"
    const val DEFAULT_PBKDF2_ITERATIONS = 600_000
    const val MIN_PBKDF2_ITERATIONS = 210_000
    const val MAX_PBKDF2_ITERATIONS = 2_000_000
    const val MIN_PASSPHRASE_LENGTH = 10
    const val SALT_BYTES = 16
    const val IV_BYTES = 12
    const val KEY_BITS = 256
    const val GCM_TAG_BITS = 128
    const val GCM_TAG_BYTES = GCM_TAG_BITS / 8
    const val MAX_BACKUP_FILE_BYTES = 10 * 1024 * 1024 // 10 MB

    private val secureRandom = SecureRandom()

    fun isEncryptedBackup(rawContent: String): Boolean {
        return when (inspectEnvelope(rawContent)) {
            is BackupEnvelopeInfo.PortableV3Encrypted,
            is BackupEnvelopeInfo.LegacyV2Encrypted -> true
            else -> false
        }
    }

    /**
     * Inspects the backup file envelope/header in memory with zero DB or Keystore access.
     */
    fun inspectEnvelope(rawContent: String): BackupEnvelopeInfo {
        val rawBytes = rawContent.toByteArray(Charsets.UTF_8).size
        if (rawBytes > MAX_BACKUP_FILE_BYTES) {
            return BackupEnvelopeInfo.Corrupted("Backup exceeds maximum allowed size ($rawBytes bytes).")
        }
        val trimmed = rawContent.trim()
        if (trimmed.isEmpty()) {
            return BackupEnvelopeInfo.Corrupted("Backup file is empty.")
        }
        if (trimmed.startsWith("[")) {
            return if (trimmed.endsWith("]")) {
                BackupEnvelopeInfo.LegacyPlaintextJson
            } else {
                BackupEnvelopeInfo.Corrupted("Truncated JSON array backup.")
            }
        }
        if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) {
            return BackupEnvelopeInfo.Corrupted("Backup is not valid JSON.")
        }

        val topLevel = FlatJsonCodec.parseTopLevelObject(trimmed)
            ?: return BackupEnvelopeInfo.Corrupted("Malformed JSON envelope.")

        val encryptedFlag = topLevel["masroufiEncrypted"] as? Boolean ?: false
        if (!encryptedFlag) {
            val looksLikePlainBackup = topLevel.containsKey("backupVersion") ||
                topLevel.containsKey("transactions") ||
                topLevel.containsKey("profile") ||
                topLevel.containsKey("baseBudget")
            return if (looksLikePlainBackup) {
                BackupEnvelopeInfo.LegacyPlaintextJson
            } else {
                BackupEnvelopeInfo.Corrupted("Unrecognized Masroufi backup JSON structure.")
            }
        }

        val encVersion = (topLevel["encryptionVersion"] as? Long)?.toInt()
            ?: return BackupEnvelopeInfo.Corrupted("Missing encryptionVersion in encrypted header.")

        return when (encVersion) {
            ENCRYPTION_VERSION -> {
                val kdf = topLevel["kdf"] as? String
                val cipher = topLevel["cipher"] as? String
                val iterations = (topLevel["iterations"] as? Long)?.toInt()
                val salt = topLevel["salt"] as? String
                val iv = topLevel["iv"] as? String
                val ciphertext = topLevel["ciphertext"] as? String
                if (kdf.isNullOrBlank() || cipher.isNullOrBlank() || iterations == null ||
                    salt.isNullOrBlank() || iv.isNullOrBlank() || ciphertext.isNullOrBlank()
                ) {
                    BackupEnvelopeInfo.Corrupted("Incomplete Backup v3 encryption header.")
                } else {
                    BackupEnvelopeInfo.PortableV3Encrypted(
                        encryptionVersion = encVersion,
                        kdf = kdf,
                        iterations = iterations,
                        cipher = cipher
                    )
                }
            }
            LEGACY_V2_ENCRYPTION_VERSION -> {
                val iterations = (topLevel["iterations"] as? Long)?.toInt()
                    ?: return BackupEnvelopeInfo.Corrupted("Missing iterations in legacy v2 header.")
                val saltHex = topLevel["saltHex"] as? String
                val ivHex = topLevel["ivHex"] as? String
                val ct = topLevel["ciphertextBase64"] as? String
                if (saltHex.isNullOrBlank() || ivHex.isNullOrBlank() || ct.isNullOrBlank()) {
                    BackupEnvelopeInfo.Corrupted("Incomplete legacy v2 encryption header.")
                } else {
                    BackupEnvelopeInfo.LegacyV2Encrypted(
                        encryptionVersion = encVersion,
                        iterations = iterations
                    )
                }
            }
            else -> BackupEnvelopeInfo.UnsupportedVersion(encVersion)
        }
    }

    /**
     * Encrypts [plaintextUtf8] into a portable v3 JSON envelope on [Dispatchers.Default].
     */
    suspend fun encrypt(
        plaintextUtf8: String,
        passphrase: CharArray,
        iterations: Int = DEFAULT_PBKDF2_ITERATIONS,
        wipePassphrase: Boolean = true
    ): PortableCryptoResult<String> = withContext(Dispatchers.Default) {
        encryptBlocking(
            plaintextUtf8 = plaintextUtf8,
            passphrase = passphrase,
            iterations = iterations,
            wipePassphrase = wipePassphrase
        )
    }

    suspend fun encrypt(
        plaintextUtf8: String,
        passphrase: String,
        iterations: Int = DEFAULT_PBKDF2_ITERATIONS
    ): PortableCryptoResult<String> {
        val chars = passphrase.toCharArray()
        return encrypt(
            plaintextUtf8 = plaintextUtf8,
            passphrase = chars,
            iterations = iterations,
            wipePassphrase = true
        )
    }

    fun encryptBlocking(
        plaintextUtf8: String,
        passphrase: String,
        iterations: Int = DEFAULT_PBKDF2_ITERATIONS
    ): PortableCryptoResult<String> {
        val chars = passphrase.toCharArray()
        return encryptBlocking(
            plaintextUtf8 = plaintextUtf8,
            passphrase = chars,
            iterations = iterations,
            wipePassphrase = true
        )
    }

    fun encryptBlocking(
        plaintextUtf8: String,
        passphrase: CharArray,
        iterations: Int = DEFAULT_PBKDF2_ITERATIONS,
        wipePassphrase: Boolean = true
    ): PortableCryptoResult<String> {
        val trimmedPass = trimCharArray(passphrase)
        var plainBytes: ByteArray? = null
        var keyBytes: ByteArray? = null
        try {
            if (trimmedPass.size < MIN_PASSPHRASE_LENGTH) {
                return PortableCryptoResult.WeakPassphrase(MIN_PASSPHRASE_LENGTH)
            }
            if (iterations !in MIN_PBKDF2_ITERATIONS..MAX_PBKDF2_ITERATIONS) {
                return PortableCryptoResult.CorruptedFile(
                    "Invalid PBKDF2 iterations ($iterations). Expected $MIN_PBKDF2_ITERATIONS..$MAX_PBKDF2_ITERATIONS."
                )
            }

            plainBytes = plaintextUtf8.toByteArray(Charsets.UTF_8)
            if (plainBytes.size > MAX_BACKUP_FILE_BYTES) {
                return PortableCryptoResult.FileTooLarge(plainBytes.size, MAX_BACKUP_FILE_BYTES)
            }

            val salt = ByteArray(SALT_BYTES).also { secureRandom.nextBytes(it) }
            val iv = ByteArray(IV_BYTES).also { secureRandom.nextBytes(it) }
            val saltB64 = PureBase64.encode(salt)
            val ivB64 = PureBase64.encode(iv)

            val aadBytes = buildAad(
                format = FORMAT_ID,
                encryptionVersion = ENCRYPTION_VERSION,
                kdf = KDF_ALGORITHM,
                iterations = iterations,
                cipher = CIPHER_ALGORITHM,
                saltBase64 = saltB64,
                ivBase64 = ivB64
            )

            keyBytes = deriveAesKeyBytes(trimmedPass, salt, iterations)
            val secretKey = SecretKeySpec(keyBytes, "AES")
            val cipher = Cipher.getInstance(CIPHER_ALGORITHM)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_BITS, iv))
            cipher.updateAAD(aadBytes)
            val ciphertext = cipher.doFinal(plainBytes)
            val ciphertextB64 = PureBase64.encode(ciphertext)

            val envelope = FlatJsonCodec.encodeV3Envelope(
                format = FORMAT_ID,
                encryptionVersion = ENCRYPTION_VERSION,
                kdf = KDF_ALGORITHM,
                iterations = iterations,
                cipher = CIPHER_ALGORITHM,
                saltBase64 = saltB64,
                ivBase64 = ivB64,
                ciphertextBase64 = ciphertextB64
            )
            return PortableCryptoResult.Success(envelope)
        } catch (t: Throwable) {
            return PortableCryptoResult.CorruptedFile(t.message ?: "Encryption failure.")
        } finally {
            plainBytes?.let { Arrays.fill(it, 0.toByte()) }
            keyBytes?.let { Arrays.fill(it, 0.toByte()) }
            Arrays.fill(trimmedPass, '\u0000')
            if (wipePassphrase) {
                Arrays.fill(passphrase, '\u0000')
            }
        }
    }

    /**
     * Decrypts a portable v3 JSON envelope on [Dispatchers.Default].
     */
    suspend fun decrypt(
        envelopeJson: String,
        passphrase: CharArray,
        wipePassphrase: Boolean = true
    ): PortableCryptoResult<String> = withContext(Dispatchers.Default) {
        decryptBlocking(
            envelopeJson = envelopeJson,
            passphrase = passphrase,
            wipePassphrase = wipePassphrase
        )
    }

    suspend fun decrypt(
        envelopeJson: String,
        passphrase: String
    ): PortableCryptoResult<String> {
        val chars = passphrase.toCharArray()
        return decrypt(
            envelopeJson = envelopeJson,
            passphrase = chars,
            wipePassphrase = true
        )
    }

    fun decryptBlocking(
        envelopeJson: String,
        passphrase: String
    ): PortableCryptoResult<String> {
        val chars = passphrase.toCharArray()
        return decryptBlocking(
            envelopeJson = envelopeJson,
            passphrase = chars,
            wipePassphrase = true
        )
    }

    fun decryptBlocking(
        envelopeJson: String,
        passphrase: CharArray,
        wipePassphrase: Boolean = true
    ): PortableCryptoResult<String> {
        val trimmedPass = trimCharArray(passphrase)
        var keyBytes: ByteArray? = null
        var decryptedBytes: ByteArray? = null
        try {
            val rawBytes = envelopeJson.toByteArray(Charsets.UTF_8).size
            if (rawBytes > MAX_BACKUP_FILE_BYTES) {
                return PortableCryptoResult.FileTooLarge(rawBytes, MAX_BACKUP_FILE_BYTES)
            }
            val trimmed = envelopeJson.trim()
            if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) {
                return PortableCryptoResult.CorruptedFile("Encrypted envelope is not a JSON object.")
            }

            val fields = FlatJsonCodec.parseTopLevelObject(trimmed)
                ?: return PortableCryptoResult.CorruptedFile("Malformed encrypted envelope JSON.")

            val isEnc = fields["masroufiEncrypted"] as? Boolean ?: false
            if (!isEnc) {
                return PortableCryptoResult.CorruptedFile("Missing masroufiEncrypted flag.")
            }

            val version = (fields["encryptionVersion"] as? Long)?.toInt()
                ?: return PortableCryptoResult.CorruptedFile("Missing encryptionVersion.")
            if (version != ENCRYPTION_VERSION) {
                return PortableCryptoResult.UnsupportedVersion(version)
            }

            val format = fields["format"] as? String
                ?: return PortableCryptoResult.CorruptedFile("Missing format header.")
            val kdf = fields["kdf"] as? String
                ?: return PortableCryptoResult.CorruptedFile("Missing kdf header.")
            val cipherAlg = fields["cipher"] as? String
                ?: return PortableCryptoResult.CorruptedFile("Missing cipher header.")
            val iterations = (fields["iterations"] as? Long)?.toInt()
                ?: return PortableCryptoResult.CorruptedFile("Missing iterations header.")
            val saltB64 = fields["salt"] as? String
                ?: return PortableCryptoResult.CorruptedFile("Missing salt header.")
            val ivB64 = fields["iv"] as? String
                ?: return PortableCryptoResult.CorruptedFile("Missing iv header.")
            val ciphertextB64 = fields["ciphertext"] as? String
                ?: return PortableCryptoResult.CorruptedFile("Missing ciphertext payload.")

            if (kdf != KDF_ALGORITHM) {
                return PortableCryptoResult.WrongPassphraseOrTampered
            }
            if (cipherAlg != CIPHER_ALGORITHM) {
                return PortableCryptoResult.WrongPassphraseOrTampered
            }
            if (iterations !in MIN_PBKDF2_ITERATIONS..MAX_PBKDF2_ITERATIONS) {
                return PortableCryptoResult.WrongPassphraseOrTampered
            }

            val salt = PureBase64.decodeStrict(saltB64)
                ?: return PortableCryptoResult.CorruptedFile("Invalid Base64 salt.")
            val iv = PureBase64.decodeStrict(ivB64)
                ?: return PortableCryptoResult.CorruptedFile("Invalid Base64 iv.")
            val ciphertext = PureBase64.decodeStrict(ciphertextB64)
                ?: return PortableCryptoResult.CorruptedFile("Invalid Base64 ciphertext.")

            if (salt.size != SALT_BYTES || iv.size != IV_BYTES || ciphertext.size < GCM_TAG_BYTES) {
                return PortableCryptoResult.CorruptedFile("Truncated cryptographic parameters.")
            }

            if (trimmedPass.isEmpty()) {
                return PortableCryptoResult.WrongPassphraseOrTampered
            }

            val aadBytes = buildAad(
                format = format,
                encryptionVersion = version,
                kdf = kdf,
                iterations = iterations,
                cipher = cipherAlg,
                saltBase64 = saltB64,
                ivBase64 = ivB64
            )

            keyBytes = deriveAesKeyBytes(trimmedPass, salt, iterations)
            val secretKey = SecretKeySpec(keyBytes, "AES")
            val cipher = Cipher.getInstance(CIPHER_ALGORITHM)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_BITS, iv))
            cipher.updateAAD(aadBytes)
            decryptedBytes = cipher.doFinal(ciphertext)

            if (format != FORMAT_ID) {
                return PortableCryptoResult.WrongPassphraseOrTampered
            }

            return PortableCryptoResult.Success(String(decryptedBytes, Charsets.UTF_8))
        } catch (_: AEADBadTagException) {
            return PortableCryptoResult.WrongPassphraseOrTampered
        } catch (t: Throwable) {
            val cause = t.cause
            if (cause is AEADBadTagException) {
                return PortableCryptoResult.WrongPassphraseOrTampered
            }
            return PortableCryptoResult.CorruptedFile(t.message ?: "Failed to decrypt backup.")
        } finally {
            keyBytes?.let { Arrays.fill(it, 0.toByte()) }
            decryptedBytes?.let { Arrays.fill(it, 0.toByte()) }
            Arrays.fill(trimmedPass, '\u0000')
            if (wipePassphrase) {
                Arrays.fill(passphrase, '\u0000')
            }
        }
    }

    /**
     * Auto-detects and decrypts v3 portable encrypted backups, legacy v2 encrypted backups,
     * or returns legacy plaintext JSON untouched. Runs off the main thread on [Dispatchers.Default].
     */
    suspend fun decryptAnyBackup(
        rawContent: String,
        passphrase: CharArray,
        wipePassphrase: Boolean = true
    ): PortableCryptoResult<String> = withContext(Dispatchers.Default) {
        decryptAnyBackupBlocking(
            rawContent = rawContent,
            passphrase = passphrase,
            wipePassphrase = wipePassphrase
        )
    }

    fun decryptAnyBackupBlocking(
        rawContent: String,
        passphrase: CharArray,
        wipePassphrase: Boolean = true
    ): PortableCryptoResult<String> {
        val rawBytes = rawContent.toByteArray(Charsets.UTF_8).size
        if (rawBytes > MAX_BACKUP_FILE_BYTES) {
            if (wipePassphrase) Arrays.fill(passphrase, '\u0000')
            return PortableCryptoResult.FileTooLarge(rawBytes, MAX_BACKUP_FILE_BYTES)
        }
        return when (val info = inspectEnvelope(rawContent)) {
            is BackupEnvelopeInfo.PortableV3Encrypted -> {
                decryptBlocking(rawContent, passphrase, wipePassphrase = wipePassphrase)
            }
            is BackupEnvelopeInfo.LegacyV2Encrypted -> {
                decryptLegacyV2Blocking(rawContent, passphrase, wipePassphrase = wipePassphrase)
            }
            BackupEnvelopeInfo.LegacyPlaintextJson -> {
                if (wipePassphrase) Arrays.fill(passphrase, '\u0000')
                PortableCryptoResult.Success(rawContent)
            }
            is BackupEnvelopeInfo.UnsupportedVersion -> {
                if (wipePassphrase) Arrays.fill(passphrase, '\u0000')
                PortableCryptoResult.UnsupportedVersion(info.detectedVersion)
            }
            is BackupEnvelopeInfo.Corrupted -> {
                if (wipePassphrase) Arrays.fill(passphrase, '\u0000')
                PortableCryptoResult.CorruptedFile(info.reason)
            }
        }
    }

    /**
     * Pure-JCA decryption of legacy v2 envelopes (`encryptionVersion = 2`, hex salt/IV, base64 ciphertext)
     * so legacy v2 backups also restore cleanly in both Android and pure JVM environments.
     */
    private fun decryptLegacyV2Blocking(
        envelopeJson: String,
        passphrase: CharArray,
        wipePassphrase: Boolean
    ): PortableCryptoResult<String> {
        val trimmedPass = trimCharArray(passphrase)
        var keyBytes: ByteArray? = null
        var plainBytes: ByteArray? = null
        try {
            if (trimmedPass.isEmpty()) {
                return PortableCryptoResult.WrongPassphraseOrTampered
            }
            val fields = FlatJsonCodec.parseTopLevelObject(envelopeJson.trim())
                ?: return PortableCryptoResult.CorruptedFile("Malformed legacy v2 envelope.")

            val version = (fields["encryptionVersion"] as? Long)?.toInt() ?: 0
            val kdf = fields["kdf"] as? String ?: ""
            val cipherAlg = fields["cipher"] as? String ?: ""
            val iterations = (fields["iterations"] as? Long)?.toInt() ?: 0
            val saltHex = fields["saltHex"] as? String ?: ""
            val ivHex = fields["ivHex"] as? String ?: ""
            val ctB64 = fields["ciphertextBase64"] as? String ?: ""

            if (version != LEGACY_V2_ENCRYPTION_VERSION ||
                kdf != KDF_ALGORITHM ||
                cipherAlg != CIPHER_ALGORITHM ||
                iterations !in 10_000..1_000_000
            ) {
                return PortableCryptoResult.CorruptedFile("Unsupported legacy v2 crypto parameters.")
            }

            val salt = hexToBytesStrict(saltHex)
                ?: return PortableCryptoResult.CorruptedFile("Invalid legacy v2 saltHex.")
            val iv = hexToBytesStrict(ivHex)
                ?: return PortableCryptoResult.CorruptedFile("Invalid legacy v2 ivHex.")
            val ciphertext = PureBase64.decodeStrict(ctB64)
                ?: return PortableCryptoResult.CorruptedFile("Invalid legacy v2 ciphertextBase64.")

            if (salt.size != SALT_BYTES || iv.size != IV_BYTES || ciphertext.size < GCM_TAG_BYTES) {
                return PortableCryptoResult.CorruptedFile("Truncated legacy v2 crypto fields.")
            }

            val aadBytes = "v$version|$kdf|$iterations|$cipherAlg|$saltHex|$ivHex"
                .toByteArray(Charsets.UTF_8)

            keyBytes = deriveAesKeyBytes(trimmedPass, salt, iterations)
            val cipher = Cipher.getInstance(CIPHER_ALGORITHM)
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(keyBytes, "AES"), GCMParameterSpec(GCM_TAG_BITS, iv))
            cipher.updateAAD(aadBytes)
            plainBytes = cipher.doFinal(ciphertext)
            return PortableCryptoResult.Success(String(plainBytes, Charsets.UTF_8))
        } catch (_: AEADBadTagException) {
            return PortableCryptoResult.WrongPassphraseOrTampered
        } catch (t: Throwable) {
            if (t.cause is AEADBadTagException) {
                return PortableCryptoResult.WrongPassphraseOrTampered
            }
            return PortableCryptoResult.CorruptedFile(t.message ?: "Legacy v2 decryption failed.")
        } finally {
            keyBytes?.let { Arrays.fill(it, 0.toByte()) }
            plainBytes?.let { Arrays.fill(it, 0.toByte()) }
            Arrays.fill(trimmedPass, '\u0000')
            if (wipePassphrase) {
                Arrays.fill(passphrase, '\u0000')
            }
        }
    }

    internal fun buildAad(
        format: String,
        encryptionVersion: Int,
        kdf: String,
        iterations: Int,
        cipher: String,
        saltBase64: String,
        ivBase64: String
    ): ByteArray {
        return "v$encryptionVersion|$format|$kdf|$iterations|$cipher|$saltBase64|$ivBase64"
            .toByteArray(Charsets.UTF_8)
    }

    private fun deriveAesKeyBytes(
        passphrase: CharArray,
        salt: ByteArray,
        iterations: Int
    ): ByteArray {
        val spec = PBEKeySpec(passphrase, salt, iterations, KEY_BITS)
        return try {
            val factory = SecretKeyFactory.getInstance(KDF_ALGORITHM)
            factory.generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    private fun trimCharArray(input: CharArray): CharArray {
        var start = 0
        var end = input.size
        while (start < end && input[start] <= ' ') start++
        while (end > start && input[end - 1] <= ' ') end--
        return if (start >= end) CharArray(0) else input.copyOfRange(start, end)
    }

    private fun hexToBytesStrict(hex: String): ByteArray? {
        if (hex.length % 2 != 0) return null
        val out = ByteArray(hex.length / 2)
        for (i in out.indices) {
            val hi = Character.digit(hex[i * 2], 16)
            val lo = Character.digit(hex[i * 2 + 1], 16)
            if (hi < 0 || lo < 0) return null
            out[i] = ((hi shl 4) or lo).toByte()
        }
        return out
    }

    /**
     * Self-contained RFC-4648 standard Base64 encoder/decoder with strict validation.
     * Avoids both `android.util.Base64` (unavailable in pure JVM tests) and `java.util.Base64`
     * (requires Android API 26 while minSdk is 24).
     */
    internal object PureBase64 {
        private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
        private val DECODE_TABLE = IntArray(128) { -1 }.apply {
            for (i in ALPHABET.indices) {
                this[ALPHABET[i].code] = i
            }
        }

        fun encode(bytes: ByteArray): String {
            if (bytes.isEmpty()) return ""
            val out = StringBuilder(((bytes.size + 2) / 3) * 4)
            var i = 0
            while (i < bytes.size) {
                val b0 = bytes[i].toInt() and 0xFF
                val hasB1 = i + 1 < bytes.size
                val hasB2 = i + 2 < bytes.size
                val b1 = if (hasB1) bytes[i + 1].toInt() and 0xFF else 0
                val b2 = if (hasB2) bytes[i + 2].toInt() and 0xFF else 0

                val triple = (b0 shl 16) or (b1 shl 8) or b2
                out.append(ALPHABET[(triple ushr 18) and 0x3F])
                out.append(ALPHABET[(triple ushr 12) and 0x3F])
                out.append(if (hasB1) ALPHABET[(triple ushr 6) and 0x3F] else '=')
                out.append(if (hasB2) ALPHABET[triple and 0x3F] else '=')
                i += 3
            }
            return out.toString()
        }

        fun decodeStrict(input: String): ByteArray? {
            // Strip only standard whitespace (newlines in legacy base64 if any); reject invalid chars
            val clean = buildString(input.length) {
                for (ch in input) {
                    if (ch != '\n' && ch != '\r' && ch != ' ' && ch != '\t') append(ch)
                }
            }
            if (clean.isEmpty() || clean.length % 4 != 0) return null
            var pad = 0
            if (clean.endsWith("==")) pad = 2 else if (clean.endsWith("=")) pad = 1

            val outLen = (clean.length / 4) * 3 - pad
            if (outLen < 0) return null
            val out = ByteArray(outLen)

            var outIdx = 0
            var i = 0
            while (i < clean.length) {
                val c0 = decodeChar(clean[i])
                val c1 = decodeChar(clean[i + 1])
                if (c0 < 0 || c1 < 0) return null

                val isLastQuantum = i + 4 == clean.length
                val ch2 = clean[i + 2]
                val ch3 = clean[i + 3]

                val c2 = if (isLastQuantum && pad == 2) {
                    if (ch2 != '=' || ch3 != '=') return null
                    0
                } else {
                    val v = decodeChar(ch2)
                    if (v < 0) return null
                    v
                }

                val c3 = if (isLastQuantum && pad >= 1) {
                    if (ch3 != '=') return null
                    0
                } else {
                    val v = decodeChar(ch3)
                    if (v < 0) return null
                    v
                }

                // Canonical RFC-4648 non-zero trailing bit check
                if (isLastQuantum && pad == 2 && (c1 and 0x0F) != 0) return null
                if (isLastQuantum && pad == 1 && (c2 and 0x03) != 0) return null

                val triple = (c0 shl 18) or (c1 shl 12) or (c2 shl 6) or c3
                out[outIdx++] = ((triple ushr 16) and 0xFF).toByte()
                if (outIdx < outLen) out[outIdx++] = ((triple ushr 8) and 0xFF).toByte()
                if (outIdx < outLen) out[outIdx++] = (triple and 0xFF).toByte()
                i += 4
            }
            return out
        }

        private fun decodeChar(ch: Char): Int {
            val code = ch.code
            return if (code in 0..127) DECODE_TABLE[code] else -1
        }
    }

    /**
     * Minimal, zero-dependency JSON codec for the flat v3 encrypted envelope and top-level
     * header inspection (skips nested arrays/objects cleanly so plaintext backups inspect safely).
     */
    internal object FlatJsonCodec {
        fun encodeV3Envelope(
            format: String,
            encryptionVersion: Int,
            kdf: String,
            iterations: Int,
            cipher: String,
            saltBase64: String,
            ivBase64: String,
            ciphertextBase64: String
        ): String {
            return buildString(ciphertextBase64.length + 256) {
                append("{\n")
                append("  \"format\": \"").append(escapeJson(format)).append("\",\n")
                append("  \"masroufiEncrypted\": true,\n")
                append("  \"encryptionVersion\": ").append(encryptionVersion).append(",\n")
                append("  \"kdf\": \"").append(escapeJson(kdf)).append("\",\n")
                append("  \"iterations\": ").append(iterations).append(",\n")
                append("  \"cipher\": \"").append(escapeJson(cipher)).append("\",\n")
                append("  \"salt\": \"").append(escapeJson(saltBase64)).append("\",\n")
                append("  \"iv\": \"").append(escapeJson(ivBase64)).append("\",\n")
                append("  \"ciphertext\": \"").append(escapeJson(ciphertextBase64)).append("\"\n")
                append("}")
            }
        }

        private fun escapeJson(value: String): String {
            val sb = StringBuilder(value.length)
            for (ch in value) {
                when (ch) {
                    '\\' -> sb.append("\\\\")
                    '"' -> sb.append("\\\"")
                    '\n' -> sb.append("\\n")
                    '\r' -> sb.append("\\r")
                    '\t' -> sb.append("\\t")
                    else -> sb.append(ch)
                }
            }
            return sb.toString()
        }

        /**
         * Parses top-level key/value pairs from a JSON object.
         * Scalar values are returned as String, Boolean, Long, Double, or Unit (for null/nested).
         * Returns null if JSON syntax is invalid or truncated.
         */
        fun parseTopLevelObject(raw: String): Map<String, Any?>? {
            val p = Parser(raw)
            return p.parseRootObject()
        }

        private class Parser(private val s: String) {
            private var pos = 0

            fun parseRootObject(): Map<String, Any?>? {
                skipWs()
                if (pos >= s.length || s[pos] != '{') return null
                pos++
                val map = linkedMapOf<String, Any?>()
                skipWs()
                if (pos < s.length && s[pos] == '}') {
                    pos++
                    skipWs()
                    return if (pos == s.length) map else null
                }
                while (pos < s.length) {
                    skipWs()
                    val key = readString() ?: return null
                    skipWs()
                    if (pos >= s.length || s[pos] != ':') return null
                    pos++
                    skipWs()
                    val value = readValue() ?: return null
                    map[key] = if (value === NULL_SENTINEL) null else value
                    skipWs()
                    if (pos >= s.length) return null
                    when (s[pos]) {
                        ',' -> pos++
                        '}' -> {
                            pos++
                            skipWs()
                            return if (pos == s.length) map else null
                        }
                        else -> return null
                    }
                }
                return null
            }

            private fun readValue(): Any? {
                if (pos >= s.length) return null
                return when (val ch = s[pos]) {
                    '"' -> readString()
                    '{' -> if (skipComposite('{', '}')) NESTED_SENTINEL else null
                    '[' -> if (skipComposite('[', ']')) NESTED_SENTINEL else null
                    't' -> if (s.startsWith("true", pos)) { pos += 4; true } else null
                    'f' -> if (s.startsWith("false", pos)) { pos += 5; false } else null
                    'n' -> if (s.startsWith("null", pos)) { pos += 4; NULL_SENTINEL } else null
                    '-', in '0'..'9' -> readNumber()
                    else -> null
                }
            }

            private fun readString(): String? {
                if (pos >= s.length || s[pos] != '"') return null
                pos++
                val sb = StringBuilder()
                while (pos < s.length) {
                    val ch = s[pos++]
                    when (ch) {
                        '"' -> return sb.toString()
                        '\\' -> {
                            if (pos >= s.length) return null
                            when (val esc = s[pos++]) {
                                '"', '\\', '/' -> sb.append(esc)
                                'b' -> sb.append('\b')
                                'f' -> sb.append('\u000C')
                                'n' -> sb.append('\n')
                                'r' -> sb.append('\r')
                                't' -> sb.append('\t')
                                'u' -> {
                                    if (pos + 4 > s.length) return null
                                    val code = s.substring(pos, pos + 4).toIntOrNull(16) ?: return null
                                    sb.append(code.toChar())
                                    pos += 4
                                }
                                else -> return null
                            }
                        }
                        else -> {
                            if (ch.code < 0x20) return null
                            sb.append(ch)
                        }
                    }
                }
                return null
            }

            private fun readNumber(): Any? {
                val start = pos
                if (s[pos] == '-') pos++
                var hasDigits = false
                while (pos < s.length && s[pos] in '0'..'9') {
                    pos++
                    hasDigits = true
                }
                if (!hasDigits) return null
                var isFloat = false
                if (pos < s.length && s[pos] == '.') {
                    isFloat = true
                    pos++
                    var fracDigits = false
                    while (pos < s.length && s[pos] in '0'..'9') {
                        pos++
                        fracDigits = true
                    }
                    if (!fracDigits) return null
                }
                if (pos < s.length && (s[pos] == 'e' || s[pos] == 'E')) {
                    isFloat = true
                    pos++
                    if (pos < s.length && (s[pos] == '+' || s[pos] == '-')) pos++
                    var expDigits = false
                    while (pos < s.length && s[pos] in '0'..'9') {
                        pos++
                        expDigits = true
                    }
                    if (!expDigits) return null
                }
                val token = s.substring(start, pos)
                return if (isFloat) token.toDoubleOrNull() else token.toLongOrNull()
            }

            private fun skipComposite(open: Char, close: Char): Boolean {
                var depth = 0
                while (pos < s.length) {
                    when (s[pos]) {
                        '"' -> if (readString() == null) return false
                        open -> {
                            depth++
                            pos++
                        }
                        close -> {
                            depth--
                            pos++
                            if (depth == 0) return true
                        }
                        else -> pos++
                    }
                }
                return false
            }

            private fun skipWs() {
                while (pos < s.length && (s[pos] == ' ' || s[pos] == '\n' || s[pos] == '\r' || s[pos] == '\t')) {
                    pos++
                }
            }
        }

        private val NULL_SENTINEL = Any()
        private val NESTED_SENTINEL = Any()
    }
}
