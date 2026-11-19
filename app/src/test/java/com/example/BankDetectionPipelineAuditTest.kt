package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.notifications.BankDetectionStore
import com.example.notifications.ExtractedNotificationMessage
import com.example.notifications.MasroufiDetectionLog
import com.example.widget.MasroufiWidgetStore
import com.example.widget.WidgetSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Audit verification tests for BankDetectionStore pipeline (D1, D5, D8, D10, S4, S6)
 * and zero-PII MasroufiDetectionLog.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class BankDetectionPipelineAuditTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        BankDetectionStore.deleteAllDetectedData(context)
        context.getSharedPreferences("masroufi_bank_detection_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        MasroufiWidgetStore.consumePendingQuickCaptureTransactions(context)
        MasroufiWidgetStore.saveWidgetSnapshot(
            context,
            WidgetSnapshot(
                snapshotDateIso = "2026-11-19",
                todaySpend = 0.0,
                safeToSpendToday = 200.0,
                remainingBudget = 2000.0,
                daysRemainingInCycle = 10,
                language = "fr"
            )
        )
    }

    @Test
    fun `D1 MessagingStyle SMS thread parses second message without re-parsing or dropping`() {
        val smsPkg = "com.google.android.apps.messaging"
        BankDetectionStore.setPackageAllowed(context, smsPkg, true)

        val t1 = 1_795_090_000_000L
        val t2 = t1 + 120_000L
        val convKey = "0|$smsPkg|1|bcp_thread|1000"

        val msg1 = ExtractedNotificationMessage(
            title = "CIH Bank",
            body = "Paiement par carte de 85,00 MAD chez MARJANE",
            timestampMillis = t1
        )
        val firstBatch = BankDetectionStore.processMessagingThreadNotification(
            context = context,
            sourcePackage = smsPkg,
            conversationKey = convKey,
            messages = listOf(msg1)
        )
        assertEquals(1, firstBatch.size)
        assertEquals(85.0, firstBatch.first().amount, 0.01)

        // Second notification in the SAME SMS thread holds both msg1 and msg2
        val msg2 = ExtractedNotificationMessage(
            title = "CIH Bank",
            body = "Paiement par carte de 140,00 MAD chez CARREFOUR",
            timestampMillis = t2
        )
        val secondBatch = BankDetectionStore.processMessagingThreadNotification(
            context = context,
            sourcePackage = smsPkg,
            conversationKey = convKey,
            messages = listOf(msg1, msg2)
        )
        assertEquals("Only the newly arrived 2nd SMS should be parsed", 1, secondBatch.size)
        assertEquals(140.0, secondBatch.first().amount, 0.01)
        assertEquals("CARREFOUR", secondBatch.first().merchant)

        val pending = BankDetectionStore.loadPendingItems(context, t2 + 1000L)
        assertEquals(2, pending.size)
    }

    @Test
    fun `D5 concurrent notifications are persisted atomically with zero lost writes`() {
        val bankPkg = "com.cih.cihmobile"
        BankDetectionStore.setPackageAllowed(context, bankPkg, true)

        val totalThreads = 50
        val pool = Executors.newFixedThreadPool(8)
        val latch = CountDownLatch(totalThreads)
        val baseTime = 1_795_090_000_000L

        for (i in 1..totalThreads) {
            pool.execute {
                try {
                    BankDetectionStore.processIncomingNotification(
                        context = context,
                        sourcePackage = bankPkg,
                        rawTitle = "CIH Bank",
                        rawBody = "Paiement par carte de ${20 + i},00 MAD chez MARCHAND_$i",
                        nowMillis = baseTime + i * 1000L
                    )
                } finally {
                    latch.countDown()
                }
            }
        }
        assertTrue(latch.await(15, TimeUnit.SECONDS))
        pool.shutdown()

        val pending = BankDetectionStore.loadPendingItems(context, baseTime + 100_000L)
        assertEquals(
            "Pending queue must cap cleanly at MAX_PENDING_ITEMS (40) without race corruption",
            BankDetectionStore.MAX_PENDING_ITEMS,
            pending.size
        )
        assertEquals(
            "All persisted item IDs must be unique",
            pending.size,
            pending.map { it.id }.toSet().size
        )
    }

    @Test
    fun `S6 and D8 unconfirmed SMS sender cannot auto-confirm until confirmed once, then saves to acc_bank`() {
        val smsPkg = "com.google.android.apps.messaging"
        BankDetectionStore.setPackageAllowed(context, smsPkg, true)
        BankDetectionStore.setAutoConfirmEnabled(context, true)
        BankDetectionStore.setAutoConfirmMaxAmount(context, 500.0)

        val t1 = 1_795_090_000_000L
        // First SMS from untrusted sender "SpoofSender" MUST queue for review, NOT auto-confirm
        val first = BankDetectionStore.processIncomingNotification(
            context = context,
            sourcePackage = smsPkg,
            rawTitle = "BankSMS",
            rawBody = "Paiement par carte de 45,00 MAD chez MARJANE",
            nowMillis = t1
        )
        assertNotNull(first)
        assertEquals(1, BankDetectionStore.loadPendingItems(context, t1).size)
        assertEquals(0, MasroufiWidgetStore.consumePendingQuickCaptureTransactions(context).size)

        // User manually confirms this item -> saves to acc_bank & trusts sender "banksms"
        val createdManual = BankDetectionStore.confirmDetectedItem(context, first!!, isAutoConfirmed = false)
        assertEquals(1, createdManual.size)
        assertEquals("acc_bank", createdManual.first().accountId)
        assertEquals(first.isoDate, createdManual.first().isoDate)
        MasroufiWidgetStore.consumePendingQuickCaptureTransactions(context)

        // Second SMS from now-trusted sender under cap auto-confirms directly to acc_bank
        val second = BankDetectionStore.processIncomingNotification(
            context = context,
            sourcePackage = smsPkg,
            rawTitle = "BankSMS",
            rawBody = "Paiement par carte de 60,00 MAD chez CARREFOUR",
            nowMillis = t1 + 60_000L
        )
        assertNotNull(second)
        assertEquals("Auto-confirmed item should not stay in pending queue", 0, BankDetectionStore.loadPendingItems(context, t1 + 60_000L).size)
        val autoSaved = MasroufiWidgetStore.consumePendingQuickCaptureTransactions(context)
        assertEquals(1, autoSaved.size)
        assertEquals("acc_bank", autoSaved.first().accountId)
        assertTrue(autoSaved.first().desc.startsWith(BankDetectionStore.AUTO_DETECTED_DESC_PREFIX))
    }

    @Test
    fun `D10 and S4 pending items never persist maskedSamplePreview or plaintext prefs and log stores zero PII`() {
        val bankPkg = "com.cih.cihmobile"
        BankDetectionStore.setPackageAllowed(context, bankPkg, true)

        val detected = BankDetectionStore.processIncomingNotification(
            context = context,
            sourcePackage = bankPkg,
            rawTitle = "CIH Bank",
            rawBody = "Paiement par carte de 275,00 MAD chez SECRET_MERCHANT_XYZ",
            nowMillis = 1_795_090_000_000L
        )
        assertNotNull(detected)
        assertEquals("D10: maskedSamplePreview must be empty", "", detected!!.maskedSamplePreview)

        // S4: Plaintext shared prefs must not contain pending JSON or merchant name
        val legacyPrefs = context.getSharedPreferences("masroufi_bank_detection_prefs", Context.MODE_PRIVATE)
        assertFalse(legacyPrefs.contains("pending_detected_items_json"))
        val allPlainValues = legacyPrefs.all.values.joinToString(" ")
        assertFalse(allPlainValues.contains("SECRET_MERCHANT_XYZ"))
        assertFalse(allPlainValues.contains("275"))

        // Zero-PII diagnostic log must record structured metadata without amounts or merchants
        val logEntries = MasroufiDetectionLog.snapshotEntries()
        assertTrue(logEntries.isNotEmpty())
        val serializedLog = logEntries.joinToString(" ") {
            "${it.sourcePackage}|${it.stage}|${it.reasonCode}|${it.messageLength}"
        }
        assertFalse(serializedLog.contains("SECRET_MERCHANT_XYZ"))
        assertFalse(serializedLog.contains("275"))
    }
}
