package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.domain.PayCycleEngine
import com.example.ui.theme.MasroufiPalettes
import com.example.ui.theme.paletteForKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Masroufi", appName)
    }

    @Test
    fun `custom palettes and pay cycle engine work without throwing`() {
        assertEquals(6, MasroufiPalettes.size)
        assertEquals("majorelle", paletteForKey("majorelle").key)
        assertEquals("emerald", paletteForKey("unknown_key").key)

        val window = PayCycleEngine.computeCycleWindow(payCycleDay = 25, monthOffset = 0)
        assertTrue(window.totalDays >= 28)
        assertTrue(window.remainingDays >= 1)
    }
}
