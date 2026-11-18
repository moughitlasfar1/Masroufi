package com.example.ui.theme

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import java.io.File
import java.io.FileOutputStream
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class BackgroundWallpaperState(
    val mode: String = "none", // "none", "zellige", "atlas", "sahara", "custom"
    val dimLevel: String = "medium", // "light", "medium", "strong"
    val hasSavedCustomPhoto: Boolean = false,
    val customBitmap: ImageBitmap? = null,
    val revision: Int = 0
) {
    val isActive: Boolean
        get() = when (mode) {
            "zellige", "atlas", "sahara" -> true
            "custom" -> customBitmap != null || hasSavedCustomPhoto
            else -> false
        }

    fun resolveDimAlpha(isDarkTheme: Boolean): Float = when (dimLevel) {
        "light" -> if (isDarkTheme) 0.42f else 0.36f
        "strong" -> if (isDarkTheme) 0.78f else 0.74f
        else -> if (isDarkTheme) 0.60f else 0.54f
    }
}

val LocalHasBackgroundWallpaper = compositionLocalOf { false }

object BackgroundWallpaperManager {
    private const val PREFS_NAME = "masroufi_wallpaper_prefs"
    private const val KEY_MODE = "wallpaper_mode"
    private const val KEY_DIM_LEVEL = "wallpaper_dim_level"
    private const val CUSTOM_BG_FILENAME = "masroufi_custom_bg.jpg"
    private const val MAX_IMAGE_DIMENSION = 1440

    val ALLOWED_MODES = setOf("none", "zellige", "atlas", "sahara", "custom")
    val ALLOWED_DIM_LEVELS = setOf("light", "medium", "strong")

    private val _stateFlow = MutableStateFlow(BackgroundWallpaperState())
    val stateFlow: StateFlow<BackgroundWallpaperState> = _stateFlow.asStateFlow()

    @Volatile
    private var isInitialized = false

    fun normalizeMode(raw: String?): String {
        val cleaned = raw?.trim()?.lowercase() ?: "none"
        return if (cleaned in ALLOWED_MODES) cleaned else "none"
    }

    fun normalizeDimLevel(raw: String?): String {
        val cleaned = raw?.trim()?.lowercase() ?: "medium"
        return if (cleaned in ALLOWED_DIM_LEVELS) cleaned else "medium"
    }

    fun customPhotoFile(context: Context): File = File(context.filesDir, CUSTOM_BG_FILENAME)

    fun ensureLoaded(context: Context) {
        if (isInitialized) return
        reloadFromDisk(context)
        isInitialized = true
    }

    fun reloadFromDisk(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val rawMode = normalizeMode(prefs.getString(KEY_MODE, "none"))
        val dimLevel = normalizeDimLevel(prefs.getString(KEY_DIM_LEVEL, "medium"))
        val file = customPhotoFile(context)
        val hasFile = file.exists() && file.length() > 0L
        val decodedBitmap = if (hasFile) {
            runCatching {
                BitmapFactory.decodeFile(file.absolutePath)?.asImageBitmap()
            }.getOrNull()
        } else null
        val effectiveMode = if (rawMode == "custom" && !hasFile) "none" else rawMode
        _stateFlow.value = BackgroundWallpaperState(
            mode = effectiveMode,
            dimLevel = dimLevel,
            hasSavedCustomPhoto = hasFile,
            customBitmap = decodedBitmap,
            revision = _stateFlow.value.revision + 1
        )
    }

    fun setWallpaperMode(context: Context, mode: String) {
        ensureLoaded(context)
        val normalized = normalizeMode(mode)
        val cur = _stateFlow.value
        val effectiveMode = if (normalized == "custom" && !cur.hasSavedCustomPhoto) "none" else normalized
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_MODE, effectiveMode)
            .apply()
        _stateFlow.value = cur.copy(
            mode = effectiveMode,
            revision = cur.revision + 1
        )
    }

    fun setDimLevel(context: Context, dimLevel: String) {
        ensureLoaded(context)
        val normalized = normalizeDimLevel(dimLevel)
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_DIM_LEVEL, normalized)
            .apply()
        val cur = _stateFlow.value
        _stateFlow.value = cur.copy(
            dimLevel = normalized,
            revision = cur.revision + 1
        )
    }

    fun saveCustomPhotoFromUri(context: Context, uri: Uri): Boolean {
        return runCatching {
            val resolver = context.contentResolver
            val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, boundsOpts)
            }
            if (boundsOpts.outWidth <= 0 || boundsOpts.outHeight <= 0) return false

            var sampleSize = 1
            val maxSide = max(boundsOpts.outWidth, boundsOpts.outHeight)
            while (maxSide / sampleSize > MAX_IMAGE_DIMENSION * 2) {
                sampleSize *= 2
            }

            val decodeOpts = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val rawBitmap = resolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, decodeOpts)
            } ?: return false

            val scaled = scaleDownIfNeeded(rawBitmap, MAX_IMAGE_DIMENSION)
            saveCustomPhotoBitmap(context, scaled)
        }.getOrDefault(false)
    }

    fun saveCustomPhotoBitmap(context: Context, bitmap: Bitmap): Boolean {
        return runCatching {
            val file = customPhotoFile(context)
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 86, out)
                out.flush()
            }
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_MODE, "custom")
                .apply()
            val cur = _stateFlow.value
            _stateFlow.value = cur.copy(
                mode = "custom",
                hasSavedCustomPhoto = true,
                customBitmap = bitmap.asImageBitmap(),
                revision = cur.revision + 1
            )
            isInitialized = true
            true
        }.getOrDefault(false)
    }

    fun clearCustomPhoto(context: Context) {
        runCatching {
            val file = customPhotoFile(context)
            if (file.exists()) {
                file.delete()
            }
        }
        val cur = _stateFlow.value
        val nextMode = if (cur.mode == "custom") "none" else cur.mode
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_MODE, nextMode)
            .apply()
        _stateFlow.value = cur.copy(
            mode = nextMode,
            hasSavedCustomPhoto = false,
            customBitmap = null,
            revision = cur.revision + 1
        )
    }

    private fun scaleDownIfNeeded(src: Bitmap, maxDimension: Int): Bitmap {
        val w = src.width
        val h = src.height
        val longest = max(w, h)
        if (longest <= maxDimension) return src
        val ratio = maxDimension.toFloat() / longest.toFloat()
        val targetW = (w * ratio).toInt().coerceAtLeast(1)
        val targetH = (h * ratio).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(src, targetW, targetH, true)
    }
}

@Composable
fun BackgroundWallpaperLayer(
    wallpaperState: BackgroundWallpaperState,
    accentColor: Color,
    isDarkTheme: Boolean,
    modifier: Modifier = Modifier
) {
    when (wallpaperState.mode) {
        "custom" -> {
            val bmp = wallpaperState.customBitmap
            if (bmp != null) {
                Image(
                    bitmap = bmp,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = modifier.fillMaxSize()
                )
            }
        }
        "zellige" -> MoroccanZelligeBackground(
            accentColor = accentColor,
            isDarkTheme = isDarkTheme,
            modifier = modifier
        )
        "atlas" -> MoroccanAtlasBackground(
            accentColor = accentColor,
            isDarkTheme = isDarkTheme,
            modifier = modifier
        )
        "sahara" -> MoroccanSaharaBackground(
            accentColor = accentColor,
            isDarkTheme = isDarkTheme,
            modifier = modifier
        )
    }
}

@Composable
private fun MoroccanZelligeBackground(
    accentColor: Color,
    isDarkTheme: Boolean,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val baseStart = if (isDarkTheme) Color(0xFF0A1622) else Color(0xFFE6F4F1)
        val baseEnd = if (isDarkTheme) Color(0xFF132738) else Color(0xFFD5EBE6)
        drawRect(
            brush = Brush.verticalGradient(listOf(baseStart, baseEnd))
        )

        val tileStep = 128f
        val starOuter = tileStep * 0.42f
        val starInner = tileStep * 0.20f
        val lineStroke = Stroke(width = 2.4f)
        val primaryLine = accentColor.copy(alpha = if (isDarkTheme) 0.34f else 0.28f)
        val goldLine = Color(0xFFF59E0B).copy(alpha = if (isDarkTheme) 0.24f else 0.22f)

        val cols = (w / tileStep).toInt() + 2
        val rows = (h / tileStep).toInt() + 2

        for (r in 0..rows) {
            for (c in 0..cols) {
                val cx = c * tileStep
                val cy = r * tileStep
                val starPath = Path()
                val points = 16
                for (i in 0 until points) {
                    val angle = (i * Math.PI * 2.0 / points) - (Math.PI / 2.0)
                    val rad = if (i % 2 == 0) starOuter else starInner
                    val px = cx + (cos(angle) * rad).toFloat()
                    val py = cy + (sin(angle) * rad).toFloat()
                    if (i == 0) starPath.moveTo(px, py) else starPath.lineTo(px, py)
                }
                starPath.close()
                drawPath(
                    path = starPath,
                    color = accentColor.copy(alpha = if (isDarkTheme) 0.11f else 0.09f)
                )
                drawPath(
                    path = starPath,
                    color = if ((r + c) % 2 == 0) primaryLine else goldLine,
                    style = lineStroke
                )
                drawCircle(
                    color = goldLine,
                    radius = starInner * 0.45f,
                    center = Offset(cx, cy)
                )
            }
        }
    }
}

@Composable
private fun MoroccanAtlasBackground(
    accentColor: Color,
    isDarkTheme: Boolean,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val skyTop = if (isDarkTheme) Color(0xFF0A1024) else Color(0xFFDCE8FA)
        val skyMid = if (isDarkTheme) Color(0xFF17254A) else Color(0xFFEAF2FF)
        val skyHorizon = if (isDarkTheme) accentColor.copy(alpha = 0.42f) else accentColor.copy(alpha = 0.28f)

        drawRect(
            brush = Brush.verticalGradient(listOf(skyTop, skyMid, skyHorizon))
        )

        // Glowing moon / sun over the High Atlas
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0xFFFBBF24).copy(alpha = if (isDarkTheme) 0.42f else 0.50f),
                    Color.Transparent
                ),
                center = Offset(w * 0.76f, h * 0.22f),
                radius = w * 0.34f
            ),
            radius = w * 0.34f,
            center = Offset(w * 0.76f, h * 0.22f)
        )
        drawCircle(
            color = Color(0xFFFEF3C7).copy(alpha = if (isDarkTheme) 0.75f else 0.85f),
            radius = w * 0.08f,
            center = Offset(w * 0.76f, h * 0.22f)
        )

        // Distant Atlas ridge
        val ridge1 = Path().apply {
            moveTo(0f, h * 0.56f)
            lineTo(w * 0.22f, h * 0.43f)
            lineTo(w * 0.46f, h * 0.51f)
            lineTo(w * 0.72f, h * 0.38f)
            lineTo(w, h * 0.48f)
            lineTo(w, h)
            lineTo(0f, h)
            close()
        }
        drawPath(
            path = ridge1,
            color = if (isDarkTheme) Color(0xFF1B2B4B).copy(alpha = 0.75f) else Color(0xFF93B4DB).copy(alpha = 0.55f)
        )

        // Mid Atlas ridge
        val ridge2 = Path().apply {
            moveTo(0f, h * 0.68f)
            lineTo(w * 0.30f, h * 0.54f)
            lineTo(w * 0.58f, h * 0.63f)
            lineTo(w * 0.85f, h * 0.52f)
            lineTo(w, h * 0.60f)
            lineTo(w, h)
            lineTo(0f, h)
            close()
        }
        drawPath(
            path = ridge2,
            color = if (isDarkTheme) Color(0xFF111C33).copy(alpha = 0.85f) else Color(0xFF648BB8).copy(alpha = 0.55f)
        )

        // Foreground foothills
        val ridge3 = Path().apply {
            moveTo(0f, h * 0.80f)
            lineTo(w * 0.38f, h * 0.70f)
            lineTo(w * 0.74f, h * 0.78f)
            lineTo(w, h * 0.69f)
            lineTo(w, h)
            lineTo(0f, h)
            close()
        }
        drawPath(
            path = ridge3,
            color = if (isDarkTheme) Color(0xFF0B1220).copy(alpha = 0.92f) else Color(0xFF3E638F).copy(alpha = 0.55f)
        )
    }
}

@Composable
private fun MoroccanSaharaBackground(
    accentColor: Color,
    isDarkTheme: Boolean,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val skyTop = if (isDarkTheme) Color(0xFF1E111A) else Color(0xFFFFF3DD)
        val skyMid = if (isDarkTheme) Color(0xFF3D1E24) else Color(0xFFFDE2B8)
        val skyLow = if (isDarkTheme) Color(0xFF6B3420) else Color(0xFFF9C784)

        drawRect(
            brush = Brush.verticalGradient(listOf(skyTop, skyMid, skyLow))
        )

        // Desert sun
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0xFFF59E0B).copy(alpha = 0.55f),
                    Color.Transparent
                ),
                center = Offset(w * 0.28f, h * 0.24f),
                radius = w * 0.36f
            ),
            radius = w * 0.36f,
            center = Offset(w * 0.28f, h * 0.24f)
        )
        drawCircle(
            color = Color(0xFFFFFBEB).copy(alpha = 0.85f),
            radius = w * 0.09f,
            center = Offset(w * 0.28f, h * 0.24f)
        )

        // Sweeping Merzouga sand dunes
        val dune1 = Path().apply {
            moveTo(0f, h * 0.58f)
            cubicTo(w * 0.28f, h * 0.48f, w * 0.62f, h * 0.64f, w, h * 0.52f)
            lineTo(w, h)
            lineTo(0f, h)
            close()
        }
        drawPath(
            path = dune1,
            color = if (isDarkTheme) Color(0xFF78350F).copy(alpha = 0.72f) else Color(0xFFF59E0B).copy(alpha = 0.38f)
        )

        val dune2 = Path().apply {
            moveTo(0f, h * 0.72f)
            cubicTo(w * 0.35f, h * 0.82f, w * 0.68f, h * 0.60f, w, h * 0.70f)
            lineTo(w, h)
            lineTo(0f, h)
            close()
        }
        drawPath(
            path = dune2,
            color = if (isDarkTheme) Color(0xFF451A03).copy(alpha = 0.84f) else Color(0xFFD97706).copy(alpha = 0.42f)
        )
    }
}
