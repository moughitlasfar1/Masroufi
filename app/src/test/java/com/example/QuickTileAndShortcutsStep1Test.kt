package com.example

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.example.widget.AssistantEntryActivity
import com.example.widget.GoogleAssistantExpenseBridge
import com.example.widget.MasroufiAppWidget
import com.example.widget.QuickCaptureActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.xmlpull.v1.XmlPullParser

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class QuickTileAndShortcutsStep1Test {

    @Test
    fun `all shortcuts and App Actions intents in shortcuts xml target exported AssistantEntryActivity`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val parser = context.resources.getXml(R.xml.shortcuts)
        val targetClasses = mutableListOf<String>()
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG && parser.name == "intent") {
                val targetClass = parser.getAttributeValue(
                    "http://schemas.android.com/apk/res/android",
                    "targetClass"
                )
                assertNotNull("Every shortcut/capability <intent> must declare targetClass", targetClass)
                targetClasses += targetClass!!
            }
            event = parser.next()
        }

        assertTrue("Expected at least 5 shortcut/capability intents in shortcuts.xml", targetClasses.size >= 5)
        targetClasses.forEach { cls ->
            assertEquals(
                "External shortcut/App Action intent must target exported AssistantEntryActivity",
                "com.example.widget.AssistantEntryActivity",
                cls
            )
        }
    }

    @Test
    fun `AssistantEntryActivity forwards quick add and voice intents with external confirmation flags`() {
        val entry = org.robolectric.Robolectric.buildActivity(AssistantEntryActivity::class.java).get()
        val quickAddForward = entry.buildValidatedForwardIntent(
            Intent(MasroufiAppWidget.ACTION_QUICK_ADD)
        )
        assertNotNull(quickAddForward)
        assertTrue(quickAddForward!!.getBooleanExtra(QuickCaptureActivity.EXTRA_FROM_EXTERNAL, false))
        assertTrue(quickAddForward.getBooleanExtra(QuickCaptureActivity.EXTRA_CONFIRM_REQUIRED, false))

        val voiceBiiForward = entry.buildValidatedForwardIntent(
            Intent(GoogleAssistantExpenseBridge.ACTION_ASSISTANT_ADD_EXPENSE).apply {
                putExtra(GoogleAssistantExpenseBridge.EXTRA_ASSISTANT_AMOUNT, "25")
                putExtra(GoogleAssistantExpenseBridge.EXTRA_ASSISTANT_MERCHANT, "Taxi")
            }
        )
        assertNotNull(voiceBiiForward)
        assertEquals("25.00", voiceBiiForward!!.getStringExtra(GoogleAssistantExpenseBridge.EXTRA_ASSISTANT_AMOUNT))
        assertEquals("Taxi", voiceBiiForward.getStringExtra(GoogleAssistantExpenseBridge.EXTRA_ASSISTANT_MERCHANT))
    }

    @Test
    fun `QuickAddLauncherAliasManager toggles Masroufi + power button shortcut alias and has 4-language strings`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        org.junit.Assert.assertFalse(
            com.example.notifications.QuickAddLauncherAliasManager.isQuickAddAliasEnabled(context)
        )
        com.example.notifications.QuickAddLauncherAliasManager.setQuickAddAliasEnabled(context, true)
        assertTrue(
            com.example.notifications.QuickAddLauncherAliasManager.isQuickAddAliasEnabled(context)
        )
        com.example.notifications.QuickAddLauncherAliasManager.setQuickAddAliasEnabled(context, false)
        org.junit.Assert.assertFalse(
            com.example.notifications.QuickAddLauncherAliasManager.isQuickAddAliasEnabled(context)
        )

        for (lang in listOf("fr", "en", "ar", "darija")) {
            val s = com.example.domain.Translations.forLang(lang)
            assertTrue(s.powerButtonQuickAddTitle.isNotBlank())
            assertTrue(s.powerButtonOpenSettingsBtn.isNotBlank())
            assertTrue(s.powerButtonGuideSteps.isNotBlank())
        }
    }
}
