package com.example

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithText
import com.example.domain.Translations
import com.example.ui.DashboardState
import com.example.ui.SettingsBottomSheet
import com.example.ui.theme.MasroufiTheme
import com.masroufi.importer.presentation.ScanReceiptActionChip
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Milestone 7C Compose UI gating verification suite:
 * Mirrors DatabaseRecoveryDialogTest's exact createComposeRule() + Robolectric setup
 * to verify flavor-specific UI gating for Receipt OCR and Cloud Settings in both `play` and `foss`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Phase7CFossUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `ScanReceiptActionChip is hidden in foss and visible in play`() {
        composeRule.setContent {
            MasroufiTheme(darkTheme = false, accentPalette = "majorelle") {
                ScanReceiptActionChip(
                    lang = "fr",
                    onReceiptScanned = {},
                    onScanError = {}
                )
            }
        }
        composeRule.waitForIdle()

        val nodes = composeRule.onAllNodesWithTag("tx_scan_receipt_btn").fetchSemanticsNodes()
        if (BuildConfig.IS_FOSS) {
            assertTrue("Receipt OCR chip must be hidden in FOSS flavor", nodes.isEmpty())
        } else {
            assertEquals("Receipt OCR chip must render in Play flavor", 1, nodes.size)
        }
    }

    @Test
    fun `SettingsBottomSheet hides cloud Link Google card in foss and displays flavor-accurate version label`() {
        val strings = Translations.forLang("fr")
        composeRule.setContent {
            MasroufiTheme(darkTheme = false, accentPalette = "majorelle") {
                SettingsBottomSheet(
                    state = DashboardState(),
                    strings = strings,
                    isLocalMode = true,
                    onLinkGoogleAccount = {},
                    onDismiss = {},
                    onSaveSettings = { _, _, _ -> },
                    onSignOut = {}
                )
            }
        }
        composeRule.waitForIdle()

        val linkNodes = composeRule.onAllNodesWithTag("settings_link_google_account_btn").fetchSemanticsNodes()
        if (BuildConfig.IS_FOSS) {
            assertTrue("FOSS Settings must hide Link Google Account button", linkNodes.isEmpty())
            composeRule.onNodeWithText("Masroufi v1.5.0 (FOSS)").assertExists()
        } else {
            assertEquals("Play local-mode Settings must show Link Google Account button", 1, linkNodes.size)
            composeRule.onNodeWithText("Masroufi v1.5.0").assertExists()
        }
    }
}
