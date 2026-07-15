package com.jing.sakura.compose.common

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.drawable.toBitmap
import coil.imageLoader
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.pow

private object ArtworkAccentCache {
    private const val PREFERENCES_NAME = "artwork_accent_cache"
    private const val INDEX_KEY = "cached_keys"
    private const val MAX_ENTRIES = 160
    private val colors = ConcurrentHashMap<String, Int>()

    fun get(context: Context, key: String): Color? {
        colors[key]?.let { return Color(it) }
        val storageKey = storageKey(key)
        val preferences = context.applicationContext.getSharedPreferences(
            PREFERENCES_NAME,
            Context.MODE_PRIVATE
        )
        if (!preferences.contains(storageKey)) return null
        return Color(preferences.getInt(storageKey, 0)).also {
            colors[key] = it.toArgb()
        }
    }

    @Synchronized
    fun put(context: Context, key: String, color: Color) {
        colors[key] = color.toArgb()
        val preferences = context.applicationContext.getSharedPreferences(
            PREFERENCES_NAME,
            Context.MODE_PRIVATE
        )
        val storageKey = storageKey(key)
        val keys = preferences.getString(INDEX_KEY, "")
            .orEmpty()
            .lineSequence()
            .filter(String::isNotBlank)
            .filterNot { it == storageKey }
            .toMutableList()
            .apply { add(storageKey) }
        val expired = keys.take((keys.size - MAX_ENTRIES).coerceAtLeast(0))
        val retained = keys.takeLast(MAX_ENTRIES)
        preferences.edit().apply {
            expired.forEach(::remove)
            putInt(storageKey, color.toArgb())
            putString(INDEX_KEY, retained.joinToString("\n"))
        }.apply()
    }

    private fun storageKey(key: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(key.toByteArray(Charsets.UTF_8))
            .take(16)
            .joinToString("") { byte -> "%02x".format(byte) }
        return "accent_$digest"
    }
}

@Composable
fun rememberArtworkAccent(imageUrl: String, enabled: Boolean = true): Color {
    val context = LocalContext.current
    val fallback = remember(imageUrl) { fallbackArtworkAccent(imageUrl) }
    var accent by remember(imageUrl) {
        mutableStateOf(ArtworkAccentCache.get(context, imageUrl) ?: fallback)
    }
    val animatedAccent by animateColorAsState(
        targetValue = accent,
        animationSpec = tween(durationMillis = 240),
        label = "artwork-accent"
    )

    LaunchedEffect(imageUrl, enabled) {
        if (!enabled || imageUrl.isBlank()) return@LaunchedEffect
        ArtworkAccentCache.get(context, imageUrl)?.let {
            accent = it
            return@LaunchedEffect
        }

        val request = ImageRequest.Builder(context)
            .data(imageUrl)
            .allowHardware(false)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .networkCachePolicy(CachePolicy.ENABLED)
            .size(72, 72)
            .build()
        val result = runCatching { context.imageLoader.execute(request) }.getOrNull()
        val drawable = (result as? SuccessResult)?.drawable ?: return@LaunchedEffect
        val extracted = withContext(Dispatchers.Default) {
            runCatching { extractArtworkAccent(drawable.toBitmap()) }.getOrNull()
        } ?: return@LaunchedEffect
        ArtworkAccentCache.put(context, imageUrl, extracted)
        accent = extracted
    }

    return animatedAccent
}

private fun extractArtworkAccent(source: Bitmap): Color? {
    val bitmap = Bitmap.createScaledBitmap(source, 24, 24, true)
    val hueBuckets = Array(24) { FloatArray(5) }
    val hsv = FloatArray(3)

    for (y in 0 until bitmap.height) {
        for (x in 0 until bitmap.width) {
            val pixel = bitmap.getPixel(x, y)
            if (AndroidColor.alpha(pixel) < 160) continue
            AndroidColor.colorToHSV(pixel, hsv)
            val saturation = hsv[1]
            val value = hsv[2]
            if (value < 0.18f || saturation < 0.22f) continue

            val bucket = ((hsv[0] / 15f).toInt()).coerceIn(hueBuckets.indices)
            val weight = saturation.toDouble().pow(1.45).toFloat() * value
            hueBuckets[bucket][0] += AndroidColor.red(pixel) * weight
            hueBuckets[bucket][1] += AndroidColor.green(pixel) * weight
            hueBuckets[bucket][2] += AndroidColor.blue(pixel) * weight
            hueBuckets[bucket][3] += weight
            hueBuckets[bucket][4] += 1f
        }
    }

    if (bitmap !== source) bitmap.recycle()
    val winningBucket = hueBuckets.maxByOrNull { it[3] * (1f + it[4] / 40f) }
        ?.takeIf { it[3] > 0f }
        ?: return null
    val weight = winningBucket[3]
    val color = AndroidColor.rgb(
        (winningBucket[0] / weight).toInt().coerceIn(0, 255),
        (winningBucket[1] / weight).toInt().coerceIn(0, 255),
        (winningBucket[2] / weight).toInt().coerceIn(0, 255)
    )
    AndroidColor.colorToHSV(color, hsv)
    hsv[1] = hsv[1].coerceIn(0.62f, 0.9f)
    hsv[2] = hsv[2].coerceIn(0.82f, 1f)
    return Color(AndroidColor.HSVToColor(hsv))
}

private fun fallbackArtworkAccent(key: String): Color {
    val palette = listOf(
        Color(0xFF6FA8FF),
        Color(0xFF9C82FF),
        Color(0xFFFF65A3),
        Color(0xFFFFB45C),
        Color(0xFF61D89B),
        Color(0xFFE97974)
    )
    return palette[(key.hashCode() and Int.MAX_VALUE) % palette.size]
}
