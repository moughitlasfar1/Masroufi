package com.example.ui

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TextSnippet
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat
import com.example.BuildConfig
import com.example.R
import com.example.domain.PayCycleEngine
import com.example.ui.theme.AccentEmerald
import com.example.ui.theme.fontFamilyForLanguage
import com.masroufi.core.Money
import java.io.File
import java.io.FileOutputStream

enum class ShareCardLanguage(val code: String, val label: String, val isRtl: Boolean) {
    FR("fr", "Français", false),
    AR("ar", "العربية", true),
    DARIJA("darija", "الدارجة", true);

    companion object {
        fun fromAppLanguage(lang: String): ShareCardLanguage = when (lang.trim().lowercase()) {
            "ar" -> AR
            "darija" -> DARIJA
            else -> FR
        }
    }
}

sealed class ShareCardPayload {
    data class DaretCard(
        val groupName: String,
        val turnNumber: Int,
        val totalMembers: Int,
        val amountMad: Money,
        val recipientName: String,
        val payoutDate: String,
        val currencySymbol: String = "DH"
    ) : ShareCardPayload()

    data class SellefCard(
        val personName: String,
        val totalBalanceMad: Money,
        val remainingMad: Money,
        val isLent: Boolean,
        val currencySymbol: String = "DH"
    ) : ShareCardPayload()
}

data class ShareCardRowData(
    val label: String,
    val value: String
)

data class ShareCardRenderedModel(
    val badgeTitle: String,
    val headline: String,
    val rows: List<ShareCardRowData>,
    val footerBrand: String,
    val isRtl: Boolean
)

/**
 * Pure Kotlin formatter for Daret & Sellef WhatsApp share cards and plain-text fallbacks.
 * Strictly honors [hideAmounts] (`••••`) and exposes only minimal required card fields.
 */
object ShareCardFormatter {

    fun buildCardModel(
        payload: ShareCardPayload,
        language: ShareCardLanguage,
        hideAmounts: Boolean
    ): ShareCardRenderedModel = when (payload) {
        is ShareCardPayload.DaretCard -> {
            val cur = payload.currencySymbol.ifBlank { "DH" }
            val amtText = "${PayCycleEngine.formatAmountOrMask(payload.amountMad, hideAmounts)} $cur"
            val turnText = "${payload.turnNumber} / ${payload.totalMembers.coerceAtLeast(1)}"
            val recipient = payload.recipientName.ifBlank { "—" }
            val dateVal = payload.payoutDate.ifBlank { "—" }
            when (language) {
                ShareCardLanguage.AR -> ShareCardRenderedModel(
                    badgeTitle = "دارت · متابعة الدور",
                    headline = payload.groupName.ifBlank { "مجموعة دارت" },
                    rows = listOf(
                        ShareCardRowData("الدور", turnText),
                        ShareCardRowData("المبلغ", amtText),
                        ShareCardRowData("المستفيد", recipient),
                        ShareCardRowData("التاريخ", dateVal)
                    ),
                    footerBrand = "مصروفي · Masroufi",
                    isRtl = true
                )
                ShareCardLanguage.DARIJA -> ShareCardRenderedModel(
                    badgeTitle = "دارت · النوبة",
                    headline = payload.groupName.ifBlank { "كروب دارت" },
                    rows = listOf(
                        ShareCardRowData("النوبة", turnText),
                        ShareCardRowData("المبلغ", amtText),
                        ShareCardRowData("شكون غاياخد", recipient),
                        ShareCardRowData("التاريخ", dateVal)
                    ),
                    footerBrand = "مصروفي · Masroufi",
                    isRtl = true
                )
                ShareCardLanguage.FR -> ShareCardRenderedModel(
                    badgeTitle = "DARET · SUIVI DU TOUR",
                    headline = payload.groupName.ifBlank { "Groupe Daret" },
                    rows = listOf(
                        ShareCardRowData("Tour", turnText),
                        ShareCardRowData("Montant", amtText),
                        ShareCardRowData("Bénéficiaire", recipient),
                        ShareCardRowData("Date", dateVal)
                    ),
                    footerBrand = "Masroufi",
                    isRtl = false
                )
            }
        }

        is ShareCardPayload.SellefCard -> {
            val cur = payload.currencySymbol.ifBlank { "DH" }
            val totalText = "${PayCycleEngine.formatAmountOrMask(payload.totalBalanceMad, hideAmounts)} $cur"
            val remText = "${PayCycleEngine.formatAmountOrMask(payload.remainingMad.coerceAtLeast(Money.ZERO), hideAmounts)} $cur"
            val person = payload.personName.ifBlank { "—" }
            when (language) {
                ShareCardLanguage.AR -> ShareCardRenderedModel(
                    badgeTitle = "سلف · ملخص الرصيد",
                    headline = person,
                    rows = listOf(
                        ShareCardRowData("الشخص", person),
                        ShareCardRowData("إجمالي الرصيد", totalText),
                        ShareCardRowData("المتبقي", remText)
                    ),
                    footerBrand = "مصروفي · Masroufi",
                    isRtl = true
                )
                ShareCardLanguage.DARIJA -> ShareCardRenderedModel(
                    badgeTitle = "سلف · الخلاصة",
                    headline = person,
                    rows = listOf(
                        ShareCardRowData("الشخص", person),
                        ShareCardRowData("المجموع", totalText),
                        ShareCardRowData("الباقي", remText)
                    ),
                    footerBrand = "مصروفي · Masroufi",
                    isRtl = true
                )
                ShareCardLanguage.FR -> ShareCardRenderedModel(
                    badgeTitle = "SELLEF · RÉCAPITULATIF",
                    headline = person,
                    rows = listOf(
                        ShareCardRowData("Personne", person),
                        ShareCardRowData("Montant total", totalText),
                        ShareCardRowData("Reste", remText)
                    ),
                    footerBrand = "Masroufi",
                    isRtl = false
                )
            }
        }
    }

    fun buildTextFallback(
        payload: ShareCardPayload,
        language: ShareCardLanguage,
        hideAmounts: Boolean
    ): String {
        val model = buildCardModel(payload, language, hideAmounts)
        return buildString {
            appendLine(model.badgeTitle)
            appendLine(model.headline)
            model.rows.forEach { row ->
                appendLine("${row.label}: ${row.value}")
            }
        }.trim()
    }
}

/**
 * Renders Daret & Sellef cards off-screen into crisp glass-styled PNG Bitmaps with native Arabic
 * shaping and RTL alignment, saves exclusively into `cacheDir/shared_cards/`, and shares via
 * `FileProvider` preferring WhatsApp.
 */
object ShareCardBitmapRenderer {
    const val SHARED_CARDS_DIR_NAME = "shared_cards"
    private const val CARD_WIDTH_PX = 1080
    private const val CARD_HEIGHT_PX = 720

    private fun resolveShareDir(context: Context): File {
        val dir = File(context.applicationContext.cacheDir, SHARED_CARDS_DIR_NAME)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    /** Removes previous share PNGs from `cacheDir/shared_cards/` so stale financial images do not linger. */
    fun clearOldSharedCards(context: Context) {
        runCatching {
            val dir = resolveShareDir(context)
            dir.listFiles()?.forEach { file ->
                if (file.isFile && file.name.endsWith(".png", ignoreCase = true)) {
                    file.delete()
                }
            }
        }
    }

    fun renderCardBitmap(
        context: Context,
        model: ShareCardRenderedModel,
        isDarkStyle: Boolean = true
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(CARD_WIDTH_PX, CARD_HEIGHT_PX, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // 1. Rich ambient gradient background matching Masroufi M3 Expressive Indigo/Emerald theme
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f,
                0f,
                CARD_WIDTH_PX.toFloat(),
                CARD_HEIGHT_PX.toFloat(),
                if (isDarkStyle) {
                    intArrayOf(0xFF1E1B4B.toInt(), 0xFF312E81.toInt(), 0xFF0F766E.toInt())
                } else {
                    intArrayOf(0xFF4338CA.toInt(), 0xFF5546C7.toInt(), 0xFF0D9488.toInt())
                },
                floatArrayOf(0f, 0.55f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRoundRect(
            RectF(0f, 0f, CARD_WIDTH_PX.toFloat(), CARD_HEIGHT_PX.toFloat()),
            56f,
            56f,
            bgPaint
        )

        // 2. Frosted glass inner card surface
        val glassRect = RectF(44f, 44f, CARD_WIDTH_PX - 44f, CARD_HEIGHT_PX - 44f)
        val glassFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x2EFFFFFF
            style = Paint.Style.FILL
        }
        val glassStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x55FFFFFF
            style = Paint.Style.STROKE
            strokeWidth = 3f
        }
        canvas.drawRoundRect(glassRect, 44f, 44f, glassFill)
        canvas.drawRoundRect(glassRect, 44f, 44f, glassStroke)

        val baseTypeface = runCatching {
            val fontRes = if (model.isRtl) R.font.readex_pro else R.font.plus_jakarta_sans
            ResourcesCompat.getFont(context, fontRes)
        }.getOrNull() ?: Typeface.DEFAULT

        val boldTypeface = Typeface.create(baseTypeface, Typeface.BOLD)
        val contentLeft = 92f
        val contentWidth = (CARD_WIDTH_PX - 184).coerceAtLeast(200)

        // 3. Badge Title
        val badgePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFD6CEFF.toInt()
            textSize = 30f
            typeface = boldTypeface
        }
        var cursorY = 92f
        cursorY += drawDirectionalText(canvas, model.badgeTitle, badgePaint, contentLeft, cursorY, contentWidth, model.isRtl) + 14f

        // 4. Headline (Group name / Person)
        val headlinePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFFFFFF.toInt()
            textSize = 52f
            typeface = boldTypeface
        }
        cursorY += drawDirectionalText(canvas, model.headline, headlinePaint, contentLeft, cursorY, contentWidth, model.isRtl) + 26f

        // Subtle divider
        val divPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x3DFFFFFF
            strokeWidth = 2f
        }
        canvas.drawLine(contentLeft, cursorY, CARD_WIDTH_PX - contentLeft, cursorY, divPaint)
        cursorY += 28f

        // 5. Key-Value Rows
        val labelPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFE2E8F0.toInt()
            textSize = 32f
            typeface = baseTypeface
        }
        val valuePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFFFFFF.toInt()
            textSize = 36f
            typeface = boldTypeface
        }

        val halfWidth = (contentWidth / 2) - 12
        model.rows.forEach { row ->
            val rowRect = RectF(contentLeft - 14f, cursorY - 10f, CARD_WIDTH_PX - contentLeft + 14f, cursorY + 62f)
            val rowBg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x1E0F172A }
            canvas.drawRoundRect(rowRect, 20f, 20f, rowBg)

            if (model.isRtl) {
                // RTL: Label on right half, Value on left half
                drawAlignedText(
                    canvas = canvas,
                    text = row.label,
                    paint = labelPaint,
                    x = contentLeft + halfWidth + 24f,
                    y = cursorY + 4f,
                    width = halfWidth,
                    isRtl = true,
                    alignOpposite = false
                )
                drawAlignedText(
                    canvas = canvas,
                    text = row.value,
                    paint = valuePaint,
                    x = contentLeft,
                    y = cursorY + 2f,
                    width = halfWidth,
                    isRtl = true,
                    alignOpposite = true
                )
            } else {
                // LTR: Label on left half, Value on right half
                drawAlignedText(
                    canvas = canvas,
                    text = row.label,
                    paint = labelPaint,
                    x = contentLeft,
                    y = cursorY + 4f,
                    width = halfWidth,
                    isRtl = false,
                    alignOpposite = false
                )
                drawAlignedText(
                    canvas = canvas,
                    text = row.value,
                    paint = valuePaint,
                    x = contentLeft + halfWidth + 24f,
                    y = cursorY + 2f,
                    width = halfWidth,
                    isRtl = false,
                    alignOpposite = true
                )
            }
            cursorY += 86f
        }

        // 6. Footer brand watermark
        val footerPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xB3FFFFFF.toInt()
            textSize = 24f
            typeface = boldTypeface
        }
        drawDirectionalText(
            canvas = canvas,
            text = model.footerBrand,
            paint = footerPaint,
            x = contentLeft,
            y = CARD_HEIGHT_PX - 92f,
            width = contentWidth,
            isRtl = model.isRtl
        )

        return bitmap
    }

    private fun drawDirectionalText(
        canvas: Canvas,
        text: String,
        paint: TextPaint,
        x: Float,
        y: Float,
        width: Int,
        isRtl: Boolean
    ): Float {
        return drawAlignedText(canvas, text, paint, x, y, width, isRtl, alignOpposite = false)
    }

    private fun drawAlignedText(
        canvas: Canvas,
        text: String,
        paint: TextPaint,
        x: Float,
        y: Float,
        width: Int,
        isRtl: Boolean,
        alignOpposite: Boolean
    ): Float {
        val dir = if (isRtl) TextDirectionHeuristics.RTL else TextDirectionHeuristics.LTR
        val alignment = if (alignOpposite) Layout.Alignment.ALIGN_OPPOSITE else Layout.Alignment.ALIGN_NORMAL
        val layout = StaticLayout.Builder
            .obtain(text, 0, text.length, paint, width.coerceAtLeast(1))
            .setAlignment(alignment)
            .setTextDirection(dir)
            .setMaxLines(1)
            .setIncludePad(false)
            .build()
        canvas.save()
        canvas.translate(x, y)
        layout.draw(canvas)
        canvas.restore()
        return layout.height.toFloat()
    }

    /**
     * Saves the rendered PNG strictly inside `cacheDir/shared_cards/` and returns the confined [File].
     */
    fun saveCardPngToCache(
        context: Context,
        bitmap: Bitmap,
        filePrefix: String = "masroufi_card"
    ): File {
        clearOldSharedCards(context)
        val dir = resolveShareDir(context)
        val safePrefix = filePrefix.replace(Regex("[^a-zA-Z0-9_-]"), "").ifBlank { "masroufi_card" }
        val targetFile = File(dir, "${safePrefix}_${System.currentTimeMillis()}.png")
        // Verify strict directory confinement
        require(targetFile.canonicalPath.startsWith(dir.canonicalPath + File.separator)) {
            "Share card path escapes shared_cards cache directory"
        }
        FileOutputStream(targetFile).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 95, out)
            out.flush()
        }
        return targetFile
    }

    fun shareCardImagePreferringWhatsApp(
        context: Context,
        payload: ShareCardPayload,
        language: ShareCardLanguage,
        hideAmounts: Boolean,
        isDarkStyle: Boolean
    ): Boolean {
        val model = ShareCardFormatter.buildCardModel(payload, language, hideAmounts)
        val fallbackCaption = ShareCardFormatter.buildTextFallback(payload, language, hideAmounts)
        val bitmap = renderCardBitmap(context, model, isDarkStyle)
        val prefix = if (payload is ShareCardPayload.DaretCard) "daret_card" else "sellef_card"
        val pngFile = saveCardPngToCache(context, bitmap, prefix)
        bitmap.recycle()

        val authority = "${BuildConfig.APPLICATION_ID}.fileprovider"
        val contentUri = FileProvider.getUriForFile(context, authority, pngFile)

        val baseIntent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, contentUri)
            putExtra(Intent.EXTRA_TEXT, fallbackCaption)
            clipData = ClipData.newUri(context.contentResolver, model.badgeTitle, contentUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        val preferredWaPkg = resolveInstalledWhatsAppPackage(context)
        if (preferredWaPkg != null) {
            val waIntent = Intent(baseIntent).apply { setPackage(preferredWaPkg) }
            try {
                context.startActivity(waIntent)
                return true
            } catch (_: ActivityNotFoundException) {
                // Fall through to system chooser
            }
        }

        val chooser = Intent.createChooser(baseIntent, model.badgeTitle).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return runCatching {
            context.startActivity(chooser)
            true
        }.getOrDefault(false)
    }

    fun shareTextSummaryPreferringWhatsApp(
        context: Context,
        payload: ShareCardPayload,
        language: ShareCardLanguage,
        hideAmounts: Boolean
    ): Boolean {
        val text = ShareCardFormatter.buildTextFallback(payload, language, hideAmounts)
        val baseIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val preferredWaPkg = resolveInstalledWhatsAppPackage(context)
        if (preferredWaPkg != null) {
            val waIntent = Intent(baseIntent).apply { setPackage(preferredWaPkg) }
            try {
                context.startActivity(waIntent)
                return true
            } catch (_: ActivityNotFoundException) {
                // Fall through to system chooser
            }
        }
        val chooser = Intent.createChooser(baseIntent, "WhatsApp").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return runCatching {
            context.startActivity(chooser)
            true
        }.getOrDefault(false)
    }

    private fun resolveInstalledWhatsAppPackage(context: Context): String? {
        val pm = context.packageManager
        val candidates = listOf("com.whatsapp", "com.whatsapp.w4b")
        for (pkg in candidates) {
            val installed = runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    pm.getPackageInfo(pkg, PackageManager.PackageInfoFlags.of(0))
                } else {
                    @Suppress("DEPRECATION")
                    pm.getPackageInfo(pkg, 0)
                }
                true
            }.getOrDefault(false)
            if (installed) return pkg
        }
        return null
    }
}

/**
 * Step 2: Distraction-free ModalBottomSheet allowing the user to preview a Daret or Sellef card,
 * pick French / Arabic / Darija with instant RTL rendering, toggle Hide Amounts (`••••`),
 * and share as a glass PNG image on WhatsApp (or fallback to plain text).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareCardBottomSheet(
    payload: ShareCardPayload,
    initialAppLanguage: String,
    initialHideAmounts: Boolean,
    isDarkTheme: Boolean,
    onDismiss: () -> Unit,
    onSharedResult: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var cardLang by remember(initialAppLanguage) {
        mutableStateOf(ShareCardLanguage.fromAppLanguage(initialAppLanguage))
    }
    var hideAmounts by remember(initialHideAmounts) { mutableStateOf(initialHideAmounts) }

    val model = remember(payload, cardLang, hideAmounts) {
        ShareCardFormatter.buildCardModel(payload, cardLang, hideAmounts)
    }

    val titleLabel = when (cardLang) {
        ShareCardLanguage.AR -> "مشاركة بطاقة واتساب"
        ShareCardLanguage.DARIJA -> "صيفط كارط فـ واتساب"
        ShareCardLanguage.FR -> "Partager une carte WhatsApp"
    }
    val hideAmountsLabel = when (cardLang) {
        ShareCardLanguage.AR -> "إخفاء المبالغ (••••)"
        ShareCardLanguage.DARIJA -> "خبي المبالغ (••••)"
        ShareCardLanguage.FR -> "Masquer les montants (••••)"
    }
    val shareImageCta = when (cardLang) {
        ShareCardLanguage.AR -> "مشاركة الصورة عبر واتساب"
        ShareCardLanguage.DARIJA -> "صيفط التصويرة فـ واتساب"
        ShareCardLanguage.FR -> "Partager l'image sur WhatsApp"
    }
    val shareTextCta = when (cardLang) {
        ShareCardLanguage.AR -> "مشاركة كنص بديل"
        ShareCardLanguage.DARIJA -> "صيفط غير ميساج مكتوب"
        ShareCardLanguage.FR -> "Partager en résumé texte"
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .testTag("share_card_sheet"),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = titleLabel,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("share_card_close_btn")
                ) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Close")
                }
            }

            // Live Glass Card Preview (respects cardLang RTL/LTR + bundled Arabic/Latin typography)
            val previewDirection = if (model.isRtl) LayoutDirection.Rtl else LayoutDirection.Ltr
            val previewFont = remember(cardLang) { fontFamilyForLanguage(cardLang.code) }
            CompositionLocalProvider(LocalLayoutDirection provides previewDirection) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .background(
                            Brush.linearGradient(
                                colors = if (isDarkTheme) {
                                    listOf(Color(0xFF1E1B4B), Color(0xFF312E81), Color(0xFF0F766E))
                                } else {
                                    listOf(Color(0xFF4338CA), Color(0xFF5546C7), Color(0xFF0D9488))
                                }
                            )
                        )
                        .padding(14.dp)
                        .testTag("share_card_preview")
                ) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = Color.White.copy(alpha = 0.16f),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.32f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = model.badgeTitle,
                                fontFamily = previewFont,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFD6CEFF)
                            )
                            Text(
                                text = model.headline,
                                fontFamily = previewFont,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            HorizontalDivider(color = Color.White.copy(alpha = 0.24f))
                            model.rows.forEach { row ->
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = Color(0xFF0F172A).copy(alpha = 0.22f),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = row.label,
                                            fontFamily = previewFont,
                                            fontSize = 13.sp,
                                            color = Color(0xFFE2E8F0)
                                        )
                                        Text(
                                            text = row.value,
                                            fontFamily = previewFont,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = Color.White
                                        )
                                    }
                                }
                            }
                            Text(
                                text = model.footerBrand,
                                fontFamily = previewFont,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White.copy(alpha = 0.72f)
                            )
                        }
                    }
                }
            }

            // Share-time Language Selector (FR / AR / Darija)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ShareCardLanguage.entries.forEach { langOption ->
                    FilterChip(
                        selected = cardLang == langOption,
                        onClick = { cardLang = langOption },
                        label = {
                            Text(
                                text = langOption.label,
                                fontWeight = if (cardLang == langOption) FontWeight.ExtraBold else FontWeight.Medium
                            )
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("share_card_lang_${langOption.code}")
                    )
                }
            }

            // Hide Amounts Toggle
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.VisibilityOff,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = hideAmountsLabel,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Switch(
                        checked = hideAmounts,
                        onCheckedChange = { hideAmounts = it },
                        modifier = Modifier.testTag("share_card_hide_amounts_switch")
                    )
                }
            }

            // Primary CTA: Share PNG Image via FileProvider preferring WhatsApp
            Button(
                onClick = {
                    val ok = ShareCardBitmapRenderer.shareCardImagePreferringWhatsApp(
                        context = context,
                        payload = payload,
                        language = cardLang,
                        hideAmounts = hideAmounts,
                        isDarkStyle = isDarkTheme
                    )
                    if (!ok) {
                        ShareCardBitmapRenderer.shareTextSummaryPreferringWhatsApp(
                            context = context,
                            payload = payload,
                            language = cardLang,
                            hideAmounts = hideAmounts
                        )
                    }
                    onSharedResult(model.badgeTitle)
                    onDismiss()
                },
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AccentEmerald,
                    contentColor = Color.White
                ),
                contentPadding = PaddingValues(vertical = 14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("share_card_send_image_btn")
            ) {
                Icon(imageVector = Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = shareImageCta, fontWeight = FontWeight.ExtraBold)
            }

            // Fallback CTA: Share as plain text summary
            OutlinedButton(
                onClick = {
                    ShareCardBitmapRenderer.shareTextSummaryPreferringWhatsApp(
                        context = context,
                        payload = payload,
                        language = cardLang,
                        hideAmounts = hideAmounts
                    )
                    onSharedResult(model.badgeTitle)
                    onDismiss()
                },
                shape = RoundedCornerShape(16.dp),
                contentPadding = PaddingValues(vertical = 12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("share_card_send_text_btn")
            ) {
                Icon(imageVector = Icons.Default.TextSnippet, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = shareTextCta, fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(6.dp))
        }
    }
}
