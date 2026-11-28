package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.BackupV3Codec
import com.example.platform.PlatformCloudBridge
import com.example.platform.PlatformIntegrityBridge
import com.example.platform.PlatformReceiptOcrBridge
import com.example.ui.DashboardState
import com.masroufi.importer.data.PdfExtractionOutcome
import com.masroufi.importer.data.PdfStatementExtractor
import com.masroufi.importer.data.ReceiptOcrEngine
import com.masroufi.importer.domain.CsvStatementParser
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Milestone 7C non-UI isolation and local-parity verification suite:
 * Ensures strict dependency and runtime isolation between `play` and `foss` distribution flavors
 * while preserving 100% parity on local-first features (Backup v3, CSV & digital PDF import)
 * without attaching an AndroidComposeTestRule.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Phase7CFossIsolationTest {

    private val proprietaryClasses = listOf(
        "com.google.firebase.FirebaseApp",
        "com.google.firebase.firestore.FirebaseFirestore",
        "com.google.firebase.auth.FirebaseAuth",
        "com.google.mlkit.vision.text.TextRecognition",
        "com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption"
    )

    @Test
    fun `BuildConfig and Platform bridges accurately reflect active distribution flavor`() {
        val isFoss = BuildConfig.IS_FOSS
        assertEquals(if (isFoss) "foss" else "play", BuildConfig.DISTRIBUTION_FLAVOR)
        assertEquals(if (isFoss) "foss" else "play", PlatformCloudBridge.distributionName)
        assertEquals(!isFoss, PlatformCloudBridge.isCloudSupported)
        assertEquals(!isFoss, PlatformIntegrityBridge.isIntegritySupported)
        assertEquals(!isFoss, PlatformReceiptOcrBridge.isReceiptOcrSupported)
        assertEquals(!isFoss, ReceiptOcrEngine.isSupported)

        // Core identity invariants must remain unchanged across both flavors
        assertEquals("com.masroufi.app", BuildConfig.APPLICATION_ID)
        assertEquals("1.5.0", BuildConfig.VERSION_NAME)
        assertEquals(8, BuildConfig.VERSION_CODE)
    }

    @Test
    fun `proprietary Firebase, GMS Auth, and ML Kit classes are absent in foss and present in play`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        if (BuildConfig.IS_FOSS) {
            proprietaryClasses.forEach { fqcn ->
                val loadAttempt = runCatching { Class.forName(fqcn) }
                assertTrue(
                    "FOSS classpath must NOT contain proprietary class $fqcn",
                    loadAttempt.isFailure && loadAttempt.exceptionOrNull() is ClassNotFoundException
                )
            }
            assertEquals(null, PlatformCloudBridge.currentAuthenticatedUidOrNull())
            assertEquals("", PlatformCloudBridge.resolveWebClientId(context))
            // No-op integrity bridge must never throw
            PlatformIntegrityBridge.initialize(context, null)
            PlatformIntegrityBridge.initializeFirebaseOnly(context)
        } else {
            proprietaryClasses.forEach { fqcn ->
                val loadAttempt = runCatching { Class.forName(fqcn) }
                assertTrue(
                    "Play classpath must contain $fqcn",
                    loadAttempt.isSuccess
                )
            }
            assertTrue(
                "Play webClientId must resolve to googleusercontent.com",
                PlatformCloudBridge.resolveWebClientId(context).endsWith("googleusercontent.com")
            )
        }
    }

    @Test
    fun `CSV import, Backup v3, and graceful scanned-PDF error work in both flavors`() = runBlocking {
        // 1. CSV import parity
        val sampleCsv = "Date;Libelle;Debit;Credit\n15/11/2026;MARJANE CASABLANCA;145,50;\n16/11/2026;VIREMENT EMPLOYEUR;;7500,00\n"
        val inspection = CsvStatementParser.inspectCsv(sampleCsv.toByteArray(Charsets.UTF_8))
        val rows = CsvStatementParser.extractRows(inspection.allRawRows, inspection.suggestedMapping, inspection.headerRowIndex)
        assertEquals(2, rows.size)

        // 2. Backup v3 parity
        val backupJson = BackupV3Codec.encodeV3Backup(state = DashboardState())
        assertTrue(backupJson.contains("\"backupVersion\":3") || backupJson.contains("\"backupVersion\": 3"))

        // 3. Invalid/minimal PDF bytes never crash PdfStatementExtractor
        val context = ApplicationProvider.getApplicationContext<Context>()
        val outcome = PdfStatementExtractor.extractStatement(context, "not-a-pdf".toByteArray())
        assertTrue("Non-PDF bytes must return graceful PdfExtractionOutcome.Error", outcome is PdfExtractionOutcome.Error)
    }
}
