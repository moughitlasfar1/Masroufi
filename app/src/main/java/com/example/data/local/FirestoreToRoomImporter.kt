package com.example.data.local

import android.content.Context
import com.example.data.AccountItem
import com.example.data.DataSourceConfigStore
import com.example.data.EnvelopeConfig
import com.example.data.FinancialItem
import com.example.data.LOCAL_DEVICE_USER_ID
import com.example.data.TransactionItem
import com.example.data.UserProfile
import com.example.data.local.MasroufiEntityMappers.toEntity
import com.example.data.local.MasroufiEntityMappers.toLocalEntity
import com.example.security.SecureStore
import com.masroufi.credit.data.SavedCreditEntity
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.CancellationException
import org.json.JSONArray
import org.json.JSONObject

/**
 * Full user snapshot fetched strictly from Firestore [Source.SERVER] (read-only).
 */
data class FirestoreUserSnapshot(
    val profile: UserProfile?,
    val accounts: List<AccountItem>,
    val transactions: List<TransactionItem>,
    val envelopes: List<EnvelopeConfig>,
    val financialItems: List<FinancialItem>,
    val credits: List<SavedCreditEntity>
) {
    val expectedProfileCount: Int get() = if (profile != null) 1 else 0
    val expectedAccountCount: Int get() = accounts.size
    val expectedTransactionCount: Int get() = transactions.size
    val expectedEnvelopeCount: Int get() = envelopes.size
    val expectedFinancialItemCount: Int get() = financialItems.size
    val expectedCreditCount: Int get() = credits.size
}

/**
 * Read-only server fetcher abstraction so Firestore [Source.SERVER] imports can be verified
 * deterministically in unit tests without mutating or touching live cloud data.
 */
interface FirestoreImportSource {
    suspend fun fetchUserSnapshotFromServer(userId: String): FirestoreUserSnapshot
}

sealed interface FirestoreImportResult {
    data class AlreadyCompleted(val metadata: ImportMetadataEntity) : FirestoreImportResult
    data class Success(val metadata: ImportMetadataEntity, val encryptedBackupFile: File) : FirestoreImportResult
    data class Failed(val reasonCode: String, val localizedMessage: String) : FirestoreImportResult
}

/**
 * One-time, read-only, idempotent, and resumable Firestore (`Source.SERVER`) -> Encrypted Room importer.
 *
 * Safety invariants:
 * 1. Reads strictly from [Source.SERVER] via [FirestoreImportSource]. Never modifies or deletes Firestore data.
 * 2. If offline or any server read fails, aborts immediately with a localized message and leaves Room untouched.
 * 3. Writes an AES-256-GCM encrypted `.masroufi` backup file to private app storage BEFORE writing any row to Room.
 * 4. Persists all 6 collections into Room inside a single atomic `@Transaction`.
 * 5. Independently verifies every per-collection row count against Room before recording `ImportMetadataEntity`
 *    and flipping [DataSourceConfigStore] to Room primary.
 */
object FirestoreToRoomImporter {

    private const val BACKUP_AAD_KEY_PREFIX = "masroufi_pre_import_backup_v1_"

    fun preImportBackupFile(context: Context, userId: String): File {
        val safeUid = userId.replace(Regex("[^a-zA-Z0-9_\\-]"), "_").ifBlank { "user" }
        return File(context.applicationContext.filesDir, "pre_import_${safeUid}.masroufi")
    }

    suspend fun runImport(
        context: Context,
        userId: String,
        language: String,
        dao: MasroufiDao,
        serverSource: FirestoreImportSource,
        verifyCountsOverride: (suspend (MasroufiDao, String, FirestoreUserSnapshot) -> Boolean)? = null
    ): FirestoreImportResult {
        val appCtx = context.applicationContext

        // Local-only users have no Firestore server data to import.
        if (userId == LOCAL_DEVICE_USER_ID) {
            val existingMeta = dao.getImportMetadata(userId)
            val meta = existingMeta ?: ImportMetadataEntity(
                userId = userId,
                completedAtEpochMs = System.currentTimeMillis(),
                backupChecksumSha256 = "local_device_no_cloud_import"
            ).also { dao.upsertImportMetadata(it) }
            DataSourceConfigStore.markRoomImportCompleted(appCtx, userId)
            return FirestoreImportResult.AlreadyCompleted(meta)
        }

        // 1. Idempotency check: if already imported and counts still match recorded metadata, return immediately.
        val existingMeta = dao.getImportMetadata(userId)
        if (existingMeta != null && existingMeta.completedAtEpochMs > 0L) {
            val stillConsistent = dao.countProfiles(userId) == existingMeta.profileCount &&
                dao.countAccounts(userId) == existingMeta.accountCount &&
                dao.countTransactions(userId) == existingMeta.transactionCount &&
                dao.countEnvelopes(userId) == existingMeta.envelopeCount &&
                dao.countFinancialItems(userId) == existingMeta.financialItemCount &&
                dao.countSavedCredits(userId) == existingMeta.creditCount
            if (stillConsistent || DataSourceConfigStore.isRoomImportCompleted(appCtx, userId)) {
                DataSourceConfigStore.markRoomImportCompleted(appCtx, userId)
                return FirestoreImportResult.AlreadyCompleted(existingMeta)
            }
        }

        // 2. Fetch full user snapshot strictly from Firestore Source.SERVER (never cache, never mutate cloud).
        val snapshot = try {
            serverSource.fetchUserSnapshotFromServer(userId)
        } catch (ce: CancellationException) {
            throw ce
        } catch (_: Throwable) {
            return FirestoreImportResult.Failed(
                reasonCode = "server_read_failed",
                localizedMessage = localizedError(language, "server_read_failed")
            )
        }

        // 3. Create encrypted .masroufi backup on disk BEFORE touching Room.
        val backupFile = preImportBackupFile(appCtx, userId)
        val backupChecksum = try {
            writeEncryptedPreImportBackup(backupFile, userId, snapshot)
        } catch (ce: CancellationException) {
            throw ce
        } catch (_: Throwable) {
            return FirestoreImportResult.Failed(
                reasonCode = "backup_failed",
                localizedMessage = localizedError(language, "backup_failed")
            )
        }

        if (backupChecksum.isBlank()) {
            return FirestoreImportResult.Failed(
                reasonCode = "backup_failed",
                localizedMessage = localizedError(language, "backup_failed")
            )
        }

        // 4. Write all collections into Room inside a single atomic @Transaction.
        val now = System.currentTimeMillis()
        val profileEntity = snapshot.profile?.toEntity(userId, now)
        val accountEntities = snapshot.accounts.mapIndexed { idx, acc ->
            acc.toEntity(userId, sortOrder = idx, nowMs = now)
        }
        val txEntities = snapshot.transactions.map { it.toEntity(userId, now) }
        val envelopeEntities = snapshot.envelopes.map { it.toEntity(userId, now) }
        val itemEntities = snapshot.financialItems.map { it.toEntity(userId, now) }
        val creditEntities = snapshot.credits.map { it.toLocalEntity(userId, now) }

        try {
            dao.replaceAllUserData(
                userId = userId,
                profile = profileEntity,
                accounts = accountEntities,
                transactions = txEntities,
                envelopes = envelopeEntities,
                financialItems = itemEntities,
                savedCredits = creditEntities
            )
        } catch (ce: CancellationException) {
            throw ce
        } catch (_: Throwable) {
            return FirestoreImportResult.Failed(
                reasonCode = "room_write_failed",
                localizedMessage = localizedError(language, "room_write_failed")
            )
        }

        // 5. Verify EVERY per-collection row count independently before flipping the data source!
        val countsMatch = if (verifyCountsOverride != null) {
            verifyCountsOverride(dao, userId, snapshot)
        } else {
            verifyPerCollectionCounts(dao, userId, snapshot)
        }

        if (!countsMatch) {
            return FirestoreImportResult.Failed(
                reasonCode = "count_mismatch",
                localizedMessage = localizedError(language, "count_mismatch")
            )
        }

        // 6. All counts matched -> record verified metadata and flip data source to Room primary.
        val metadata = ImportMetadataEntity(
            userId = userId,
            completedAtEpochMs = System.currentTimeMillis(),
            backupChecksumSha256 = backupChecksum,
            profileCount = snapshot.expectedProfileCount,
            accountCount = snapshot.expectedAccountCount,
            transactionCount = snapshot.expectedTransactionCount,
            envelopeCount = snapshot.expectedEnvelopeCount,
            financialItemCount = snapshot.expectedFinancialItemCount,
            creditCount = snapshot.expectedCreditCount
        )
        dao.upsertImportMetadata(metadata)
        DataSourceConfigStore.markRoomImportCompleted(appCtx, userId)
        return FirestoreImportResult.Success(metadata, backupFile)
    }

    private suspend fun verifyPerCollectionCounts(
        dao: MasroufiDao,
        userId: String,
        snapshot: FirestoreUserSnapshot
    ): Boolean {
        val actualProfiles = dao.countProfiles(userId)
        val actualAccounts = dao.countAccounts(userId)
        val actualTransactions = dao.countTransactions(userId)
        val actualEnvelopes = dao.countEnvelopes(userId)
        val actualItems = dao.countFinancialItems(userId)
        val actualCredits = dao.countSavedCredits(userId)

        return actualProfiles == snapshot.expectedProfileCount &&
            actualAccounts == snapshot.expectedAccountCount &&
            actualTransactions == snapshot.expectedTransactionCount &&
            actualEnvelopes == snapshot.expectedEnvelopeCount &&
            actualItems == snapshot.expectedFinancialItemCount &&
            actualCredits == snapshot.expectedCreditCount
    }

    private fun writeEncryptedPreImportBackup(
        targetFile: File,
        userId: String,
        snapshot: FirestoreUserSnapshot
    ): String {
        val plainJson = serializeSnapshotJson(userId, snapshot)
        val encrypted = SecureStore.encryptValue("$BACKUP_AAD_KEY_PREFIX$userId", plainJson)
            ?: return ""
        targetFile.parentFile?.mkdirs()
        val tmpFile = File("${targetFile.path}.tmp")
        tmpFile.writeText(encrypted, Charsets.UTF_8)
        if (targetFile.exists()) {
            targetFile.delete()
        }
        val renamed = tmpFile.renameTo(targetFile)
        if (!renamed) {
            targetFile.writeText(encrypted, Charsets.UTF_8)
            tmpFile.delete()
        }
        val digest = MessageDigest.getInstance("SHA-256").digest(encrypted.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    internal fun serializeSnapshotJson(userId: String, snapshot: FirestoreUserSnapshot): String {
        val root = JSONObject().apply {
            put("format", "masroufi_pre_import_backup_v1")
            put("userId", userId)
            put("createdAtEpochMs", System.currentTimeMillis())
            put(
                "counts",
                JSONObject().apply {
                    put("profiles", snapshot.expectedProfileCount)
                    put("accounts", snapshot.expectedAccountCount)
                    put("transactions", snapshot.expectedTransactionCount)
                    put("envelopes", snapshot.expectedEnvelopeCount)
                    put("financialItems", snapshot.expectedFinancialItemCount)
                    put("credits", snapshot.expectedCreditCount)
                }
            )
            snapshot.profile?.let { p ->
                put(
                    "profile",
                    JSONObject().apply {
                        put("baseBudget", p.baseBudget.toBigDecimalDh())
                        put("payCycleDay", p.payCycleDay)
                        put("rolloverEnabled", p.rolloverEnabled)
                        put("language", p.language)
                        put("isDarkTheme", p.isDarkTheme)
                        put("seeded", p.seeded)
                    }
                )
            }
            put(
                "accounts",
                JSONArray().apply {
                    snapshot.accounts.forEach { a ->
                        put(
                            JSONObject().apply {
                                put("id", a.id)
                                put("name", a.name)
                                put("kind", a.kind)
                                put("initialBalance", a.initialBalance.toBigDecimalDh())
                                put("colorHex", a.colorHex)
                            }
                        )
                    }
                }
            )
            put(
                "transactions",
                JSONArray().apply {
                    snapshot.transactions.forEach { t ->
                        put(
                            JSONObject().apply {
                                put("id", t.id)
                                put("type", t.type)
                                put("amount", t.amount.toBigDecimalDh())
                                put("category", t.category)
                                put("envelopeKey", t.envelopeKey)
                                put("accountId", t.accountId)
                                put("toAccountId", t.toAccountId)
                                put("desc", t.desc)
                                put("isoDate", t.isoDate)
                                put("hourOfDay", t.hourOfDay)
                            }
                        )
                    }
                }
            )
            put(
                "envelopes",
                JSONArray().apply {
                    snapshot.envelopes.forEach { e ->
                        put(
                            JSONObject().apply {
                                put("id", e.id)
                                put("key", e.key)
                                put("nameFr", e.nameFr)
                                put("nameAr", e.nameAr)
                                put("nameDarija", e.nameDarija)
                                put("nameEn", e.nameEn)
                                put("percentage", e.percentage)
                                put("hasCap", e.hasCap)
                                put("colorHex", e.colorHex)
                            }
                        )
                    }
                }
            )
            put(
                "financialItems",
                JSONArray().apply {
                    snapshot.financialItems.forEach { i ->
                        put(
                            JSONObject().apply {
                                put("id", i.id)
                                put("itemType", i.itemType)
                                put("name", i.name)
                                put("subtitle", i.subtitle)
                                put("amount", i.amount.toBigDecimalDh())
                                put(
                                    "secondaryAmount",
                                    if (i.itemType == "daret") i.resolvedDaretMembers() else i.secondaryAmount.toBigDecimalDh()
                                )
                                if (i.itemType == "daret") {
                                    put("daretMembers", i.resolvedDaretMembers())
                                }
                                put("dayOrTurn", i.dayOrTurn)
                                put("extraInt", i.extraInt)
                                put("dateOrCycle", i.dateOrCycle)
                            }
                        )
                    }
                }
            )
            put(
                "credits",
                JSONArray().apply {
                    snapshot.credits.forEach { c ->
                        put(
                            JSONObject().apply {
                                put("id", c.id)
                                put("type", c.type)
                                put("loanAmount", c.loanAmount.toBigDecimalDh())
                                put("downPayment", c.downPayment.toBigDecimalDh())
                                put("financedPrincipal", c.financedPrincipal.toBigDecimalDh())
                                put("durationMonths", c.durationMonths)
                                put("deferredMonths", c.deferredMonths)
                                put("annualInterestRateHt", c.annualInterestRateHt)
                                put("applyInterestTva", c.applyInterestTva)
                                put("insuranceRate", c.insuranceRate)
                                put("monthlyPayment", c.monthlyPayment.toBigDecimalDh())
                                put("totalCost", c.totalCost.toBigDecimalDh())
                                put("debtToIncomeRatio", c.debtToIncomeRatio)
                                put("maxAllowedDti", c.maxAllowedDti)
                                put("linkToMonthlyExpenses", c.linkToMonthlyExpenses)
                            }
                        )
                    }
                }
            )
        }
        return root.toString()
    }

    fun localizedError(language: String, reasonCode: String): String {
        return when (language.lowercase()) {
            "ar" -> when (reasonCode) {
                "server_read_failed" -> "يتعذّر استيراد بياناتك الآن: يلزم اتصال نشط بالخادم لقراءة بياناتك بأمان. لم يتم تعديل قاعدة البيانات المحلية."
                "backup_failed" -> "تعذّر إنشاء النسخة الاحتياطية المشفّرة (.masroufi) قبل الاستيراد. تم إلغاء العملية دون تعديل بياناتك."
                "count_mismatch" -> "فشل التحقق من تطابق عدد السجلات لكل مجموعة. تم الإبقاء على وضع السحابة لحماية بياناتك."
                else -> "تعذّر إكمال الاستيراد المحلي. ما زالت بياناتك محفوظة في السحابة ويمكنك إعادة المحاولة لاحقاً."
            }
            "darija" -> when (reasonCode) {
                "server_read_failed" -> "ما قدّيناش نجيبو المعلومات ديالك دابا: خاصك كونيكسيون مزيانة باش نقراو من السيرفر بأمان. القاعدة المحلية ما تبدلاتش."
                "backup_failed" -> "ما قدّيناش نكريو نسخة احتياطية مشفّرة (.masroufi) قبل النقل. العملية توقفات والمعلومات ديالك بقاو سالمين."
                "count_mismatch" -> "العدد ديال المعلومات اللي تنقلو ما مطابقش مع السيرفر. بقينا خدامين بالسحابة باش نحميو بياناتك."
                else -> "النقل للقاعدة المحلية ما كملش. المعلومات ديالك مازالين فالسحابة وتقدر تعاود المحاولة من بعد."
            }
            "en" -> when (reasonCode) {
                "server_read_failed" -> "Unable to import your data right now: an active server connection is required. Your local database was left untouched."
                "backup_failed" -> "Could not create the encrypted .masroufi backup before importing. Import aborted safely with no changes."
                "count_mismatch" -> "Per-collection row count verification failed. Staying on cloud storage to keep your data safe."
                else -> "Local encrypted import could not complete. Your cloud data is untouched and you can retry anytime."
            }
            else -> when (reasonCode) {
                "server_read_failed" -> "Import impossible hors-ligne : une connexion active au serveur est requise. La base locale n'a pas été modifiée."
                "backup_failed" -> "Impossible de créer la sauvegarde chiffrée (.masroufi) préalable. L'import a été annulé sans modifier vos données."
                "count_mismatch" -> "La vérification du nombre d'enregistrements par collection a échoué. Le mode cloud reste actif par sécurité."
                else -> "L'import vers la base chiffrée a échoué. Vos données cloud restent intactes et vous pouvez réessayer."
            }
        }
    }
}
