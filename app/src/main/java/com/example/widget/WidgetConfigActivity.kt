package com.example.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.PayCycleEngine
import com.example.domain.Translations
import com.example.ui.theme.AccentAmber
import com.example.ui.theme.AccentCoral
import com.example.ui.theme.AccentEmerald
import com.example.ui.theme.MasroufiTheme
import com.example.ui.theme.glass3d
import kotlin.math.roundToInt

/**
 * Phase 4: Per-Widget Configuration Activity (`android:configure="com.example.widget.WidgetConfigActivity"`)
 * allowing the user to customize:
 * - Widget Type (`SMART_SLOT`, `LEFT_TODAY`, `QUICK_ADD`, `MIC_1X1`, `ENVELOPES`, `BILLS_DARET`, `GOAL_CYCLE`)
 * - Theme (`Auto`, `Light`, `Dark`, `Accent`)
 * - Glass Effect ON/OFF + 4 Opacity Levels (25%, 45%, 65%, 85%)
 * - Density (`Compact`, `Comfortable`)
 * - Privacy Masking (`follow_app`, `always_hide`, `hide_when_locked`)
 * Includes a live interactive widget preview at the top.
 */
class WidgetConfigActivity : ComponentActivity() {

    private var appWidgetId: Int = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        com.example.security.AppSecurityManager.applyWindowFlagSecure(
            this,
            com.example.security.AppSecurityManager.isFlagSecureEnabled(this)
        )
        enableEdgeToEdge()

        appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        // Default result is CANCELED in case the user backs out
        val cancelResult = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        setResult(Activity.RESULT_CANCELED, cancelResult)

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        val snapshot = MasroufiWidgetStore.loadSnapshot(this)
        val initialStyle = MasroufiWidgetStore.loadWidgetStyle(this, appWidgetId)

        setContent {
            val systemInDark = androidx.compose.foundation.isSystemInDarkTheme()
            val effectiveIsDark = snapshot.resolveEffectiveIsDark(systemInDark)
            val effectiveSnapshot = remember(snapshot, effectiveIsDark) {
                snapshot.copy(isDarkTheme = effectiveIsDark)
            }
            MasroufiTheme(
                darkTheme = effectiveIsDark,
                accentPalette = effectiveSnapshot.accentPalette
            ) {
                WidgetConfigScreen(
                    appWidgetId = appWidgetId,
                    snapshot = effectiveSnapshot,
                    initialStyle = initialStyle,
                    onSaveStyle = { finalStyle ->
                        MasroufiWidgetStore.saveWidgetStyle(this@WidgetConfigActivity, finalStyle)
                        val appWidgetManager = AppWidgetManager.getInstance(this@WidgetConfigActivity)
                        if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                            MasroufiAppWidget.updateSingleWidget(
                                this@WidgetConfigActivity,
                                appWidgetManager,
                                appWidgetId
                            )
                        }
                        val okIntent = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                        setResult(Activity.RESULT_OK, okIntent)
                        finish()
                    },
                    onCancel = { finish() }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun WidgetConfigScreen(
    appWidgetId: Int,
    snapshot: WidgetSnapshot,
    initialStyle: WidgetStyle,
    onSaveStyle: (WidgetStyle) -> Unit,
    onCancel: () -> Unit
) {
    val strings = Translations.forLang(snapshot.language)

    var selectedKind by remember { mutableStateOf(initialStyle.widgetKind) }
    var themeMode by remember { mutableStateOf(initialStyle.themeMode) }
    var glassEnabled by remember { mutableStateOf(initialStyle.glassEnabled) }
    var glassOpacityLevel by remember { mutableIntStateOf(initialStyle.glassOpacityLevel.coerceIn(1, 4)) }
    var density by remember { mutableStateOf(initialStyle.density) }
    var privacyMode by remember { mutableStateOf(initialStyle.privacyMode) }
    var onlyOver90 by remember { mutableStateOf(initialStyle.onlyOver90Envelopes) }

    val currentStyle = WidgetStyle(
        appWidgetId = appWidgetId,
        widgetKind = selectedKind,
        themeMode = themeMode,
        glassEnabled = glassEnabled,
        glassOpacityLevel = glassOpacityLevel,
        density = density,
        privacyMode = privacyMode,
        onlyOver90Envelopes = onlyOver90
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Widgets,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = strings.widgetConfigTitle,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = strings.cancelBtn)
                    }
                }
            )
        },
        bottomBar = {
            Surface(
                tonalElevation = 6.dp,
                shadowElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    Button(
                        onClick = { onSaveStyle(currentStyle) },
                        shape = RoundedCornerShape(16.dp),
                        contentPadding = PaddingValues(vertical = 14.dp, horizontal = 24.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("widget_config_save_btn")
                    ) {
                        Icon(imageVector = Icons.Default.Check, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = strings.widgetSaveConfigBtn,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // 1. Live Interactive Preview Box on Simulated Wallpaper
            WidgetLivePreviewCard(
                snapshot = snapshot,
                style = currentStyle
            )

            // 2. Widget Kind Selector (All 7 Modes)
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = strings.widgetKindLabel,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.ExtraBold
                    )
                    val kindOptions = listOf(
                        WidgetKind.SMART_SLOT to "✨ ${strings.widgetSmartSlotTitle}",
                        WidgetKind.LEFT_TODAY to "💚 ${strings.widgetLeftTodayTitle}",
                        WidgetKind.QUICK_ADD to "⚡ ${strings.widgetQuickAddBtn}",
                        WidgetKind.MIC_1X1 to "🎤 1×1 ${strings.widgetVoiceBtn}",
                        WidgetKind.ENVELOPES to "📊 ${strings.widgetEnvelopesTitle}",
                        WidgetKind.BILLS_DARET to "📅 ${strings.widgetBillsDaretTitle}",
                        WidgetKind.GOAL_CYCLE to "🎯 ${strings.widgetGoalCycleTitle}"
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        kindOptions.forEach { (kind, label) ->
                            FilterChip(
                                selected = selectedKind == kind,
                                onClick = { selectedKind = kind },
                                label = { Text(label, fontWeight = if (selectedKind == kind) FontWeight.ExtraBold else FontWeight.Medium) },
                                modifier = Modifier.testTag("widget_kind_chip_${kind.key}")
                            )
                        }
                    }

                    if (selectedKind == WidgetKind.ENVELOPES) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Afficher uniquement les enveloppes ≥ 90%",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Switch(
                                checked = onlyOver90,
                                onCheckedChange = { onlyOver90 = it }
                            )
                        }
                    }
                }
            }

            // 3. Glass Effect & Opacity Level (4 levels: 25%, 45%, 65%, 85%) + Theme
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = strings.widgetGlassLabel,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Switch(
                            checked = glassEnabled,
                            onCheckedChange = { glassEnabled = it },
                            modifier = Modifier.testTag("widget_glass_switch")
                        )
                    }

                    if (glassEnabled) {
                        val pctLabels = mapOf(1 to "25%", 2 to "45%", 3 to "65%", 4 to "85%")
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = strings.widgetOpacityLabel,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = pctLabels[glassOpacityLevel] ?: "65%",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Slider(
                            value = glassOpacityLevel.toFloat(),
                            onValueChange = { glassOpacityLevel = it.roundToInt().coerceIn(1, 4) },
                            valueRange = 1f..4f,
                            steps = 2,
                            modifier = Modifier.testTag("widget_glass_opacity_slider")
                        )
                    } else {
                        Text(
                            text = strings.widgetThemeLabel,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(
                                "auto" to "Auto",
                                "light" to "Clair",
                                "dark" to "Sombre",
                                "accent" to "Indigo"
                            ).forEach { (modeKey, modeLabel) ->
                                FilterChip(
                                    selected = themeMode == modeKey,
                                    onClick = { themeMode = modeKey },
                                    label = { Text(modeLabel) },
                                    modifier = Modifier.testTag("widget_theme_chip_$modeKey")
                                )
                            }
                        }
                    }
                }
            }

            // 4. Density & Privacy Mode
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = strings.widgetDensityLabel,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("comfortable" to "Confortable", "compact" to "Compact").forEach { (dKey, dLabel) ->
                            FilterChip(
                                selected = density == dKey,
                                onClick = { density = dKey },
                                label = { Text(dLabel) },
                                modifier = Modifier.testTag("widget_density_chip_$dKey")
                            )
                        }
                    }

                    Text(
                        text = strings.widgetPrivacyLabel,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.ExtraBold
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(
                            "follow_app" to "Suivre l'application",
                            "always_hide" to "Toujours masquer (••••)",
                            "hide_when_locked" to "Masquer si verrouillé"
                        ).forEach { (pKey, pLabel) ->
                            FilterChip(
                                selected = privacyMode == pKey,
                                onClick = { privacyMode = pKey },
                                label = { Text(pLabel) },
                                modifier = Modifier.testTag("widget_privacy_chip_$pKey")
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WidgetLivePreviewCard(
    snapshot: WidgetSnapshot,
    style: WidgetStyle
) {
    val strings = Translations.forLang(snapshot.language)
    val cur = snapshot.currencySymbol.ifBlank { "DH" }
    val mask = style.shouldMaskAmounts(snapshot.hideAmounts, snapshot.appLockEnabled)

    val smartCard = remember(snapshot, style) {
        SmartSlotEngine.resolveSmartSlot(snapshot = snapshot, style = style)
    }

    val previewContainerAlpha = if (style.glassEnabled) {
        when (style.glassOpacityLevel.coerceIn(1, 4)) {
            1 -> 0.30f
            2 -> 0.50f
            3 -> 0.70f
            else -> 0.88f
        }
    } else 1.0f

    val isLightSolid = !style.glassEnabled &&
        (style.themeMode == "light" || (style.themeMode == "auto" && !snapshot.isDarkTheme))
    val cardBgColor = when {
        style.glassEnabled -> Color(0xFF0F172A).copy(alpha = previewContainerAlpha)
        style.themeMode == "accent" -> Color(0xFF3E329E)
        isLightSolid -> Color(0xFFF8FAFC)
        else -> Color(0xFF0F172A)
    }
    val textColor = if (isLightSolid) Color(0xFF0F172A) else Color.White
    val subTextColor = if (isLightSolid) Color(0xFF475569) else Color(0xFFCBD5E1)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(Color(0xFF312E81), Color(0xFF0F766E), Color(0xFF1E1B4B))
                )
            )
            .padding(18.dp)
            .testTag("widget_live_preview_container"),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = cardBgColor,
            border = BorderStroke(
                width = 1.dp,
                color = if (style.glassEnabled) Color.White.copy(alpha = 0.28f) else Color.White.copy(alpha = 0.12f)
            ),
            modifier = if (style.widgetKind == WidgetKind.MIC_1X1) {
                Modifier.size(96.dp)
            } else {
                Modifier.fillMaxWidth()
            }
        ) {
            if (style.widgetKind == WidgetKind.MIC_1X1) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(text = "🎤", fontSize = 26.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = strings.widgetVoiceBtn,
                        color = textColor,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            } else {
                val titleText = when (style.widgetKind) {
                    WidgetKind.LEFT_TODAY -> strings.widgetLeftTodayTitle
                    WidgetKind.QUICK_ADD -> strings.widgetHeaderToday
                    WidgetKind.ENVELOPES -> strings.widgetEnvelopesTitle
                    WidgetKind.BILLS_DARET -> strings.widgetBillsDaretTitle
                    WidgetKind.GOAL_CYCLE -> strings.widgetGoalCycleTitle
                    else -> smartCard.badgeTitle
                }
                val mainValueText = when (style.widgetKind) {
                    WidgetKind.LEFT_TODAY -> PayCycleEngine.formatCurrencyOrMask(snapshot.safeToSpendToday, cur, mask)
                    WidgetKind.QUICK_ADD -> PayCycleEngine.formatCurrencyOrMask(snapshot.effectiveTodaySpend(), cur, mask)
                    WidgetKind.ENVELOPES -> {
                        val env = snapshot.envelopes.firstOrNull()
                        if (env != null) "${env.name}: ${PayCycleEngine.formatCurrencyOrMask(env.spent, cur, mask)}"
                        else PayCycleEngine.formatCurrencyOrMask(snapshot.effectiveTodaySpend(), cur, mask)
                    }
                    WidgetKind.BILLS_DARET -> {
                        val rem = snapshot.reminders.firstOrNull()
                        if (rem != null) "${rem.title} · ${PayCycleEngine.formatCurrencyOrMask(rem.amount, cur, mask)}"
                        else PayCycleEngine.formatCurrencyOrMask(snapshot.safeToSpendToday, cur, mask)
                    }
                    WidgetKind.GOAL_CYCLE -> {
                        val goal = snapshot.primaryGoal
                        if (goal != null) "${goal.name} · ${goal.progressPercent}%"
                        else "${PayCycleEngine.formatCurrencyOrMask(snapshot.cycleExpenses, cur, mask)} / ${PayCycleEngine.formatCurrencyOrMask(snapshot.totalAvailableBudget, cur, mask)}"
                    }
                    else -> smartCard.headlineValue
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = titleText,
                            style = MaterialTheme.typography.labelMedium,
                            color = subTextColor,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${strings.widgetSafePrefix} ${PayCycleEngine.formatCurrencyOrMask(snapshot.safeToSpendToday, cur, mask)}/j",
                            style = MaterialTheme.typography.labelSmall,
                            color = AccentEmerald,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                    Text(
                        text = mainValueText,
                        style = MaterialTheme.typography.headlineSmall,
                        color = textColor,
                        fontWeight = FontWeight.ExtraBold
                    )
                    if (style.density != "compact") {
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = Color(0xFF5546C7),
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier.padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = strings.widgetQuickAddBtn,
                                        color = Color.White,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = Color(0xFF0D9488),
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier.padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "🎤 ${strings.widgetVoiceBtn}",
                                        color = Color.White,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
