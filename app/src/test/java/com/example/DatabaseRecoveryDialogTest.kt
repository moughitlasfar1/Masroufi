package com.example

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.example.ui.DatabaseRecoveryDialog
import com.example.ui.recoveryStringsFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DatabaseRecoveryDialogTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `DatabaseRecoveryDialog warns Room is only local copy and requires explicit confirmation after import`() {
        listOf("fr", "ar", "darija", "en").forEach { lang ->
            val strings = recoveryStringsFor(lang)
            assertTrue(strings.primaryLocalCopyWarning.isNotBlank())
            assertTrue(strings.confirmResetTitle.isNotBlank())
            assertTrue(strings.confirmCheckboxLabel.isNotBlank())
            assertTrue(strings.confirmPermanentResetBtn.isNotBlank())
        }

        var resetCount = 0
        composeRule.setContent {
            DatabaseRecoveryDialog(
                language = "en",
                isRoomPrimaryAfterImport = true,
                onRestoreBackup = {},
                onResetLocalDatabase = { resetCount++ }
            )
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("db_recovery_primary_warning").assertIsDisplayed()

        // First click on Reset must NOT wipe immediately; it opens the explicit confirmation step
        composeRule.onNodeWithTag("db_recovery_reset_button").performClick()
        composeRule.waitForIdle()
        assertEquals(0, resetCount)

        composeRule.onNodeWithTag("db_recovery_confirm_reset_button").assertIsDisplayed().assertIsNotEnabled()

        // Checking the explicit acknowledgement checkbox enables the permanent reset button
        composeRule.onNodeWithTag("db_recovery_confirm_checkbox").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("db_recovery_confirm_reset_button").assertIsEnabled().performClick()
        composeRule.waitForIdle()
        assertEquals(1, resetCount)
    }

    @Test
    fun `DatabaseRecoveryDialog allows direct reset before import completes`() {
        var resetCount = 0
        composeRule.setContent {
            DatabaseRecoveryDialog(
                language = "fr",
                isRoomPrimaryAfterImport = false,
                onRestoreBackup = {},
                onResetLocalDatabase = { resetCount++ }
            )
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("db_recovery_primary_warning").assertDoesNotExist()
        composeRule.onNodeWithTag("db_recovery_reset_button").performClick()
        composeRule.waitForIdle()
        assertEquals(1, resetCount)
    }
}
