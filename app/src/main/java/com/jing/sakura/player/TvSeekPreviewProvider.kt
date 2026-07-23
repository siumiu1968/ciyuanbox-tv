package com.jing.sakura.player

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import android.view.PixelCopy
import android.view.SurfaceView
import androidx.leanback.widget.PlaybackSeekDataProvider
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt

internal object TvSeekPreviewTimeline {
    private const val MIN_INTERVAL_MS = 5_000L
    private const val TARGET_FRAME_COUNT = 120L

    fun positions(durationMs: Long): LongArray {
        if (durationMs <= 0L) return longArrayOf()
        val rawInterval = max(MIN_INTERVAL_MS, durationMs / TARGET_FRAME_COUNT)
        val interval = ceil(rawInterval / MIN_INTERVAL_MS.toDouble()).toLong() * MIN_INTERVAL_MS
        val values = ArrayList<Long>()
        var position = 0L
        while (position < durationMs) {
            values += position
            position += interval
        }
        if (values.lastOrNull() != durationMs) values += durationMs
        return values.toLongArray()
    }
}

internal data class TvSeekPreviewFrameLayout(
    val width: Int,
    val height: Int,
    val left: Int,
    val top: Int
)

internal object TvSeekPreviewFrame {
    fun layout(
        sourceAspect: Float,
        canvasWidth: Int = 320,
        canvasHeight: Int = 180
    ): TvSeekPreviewFrameLayout {
        val canvasAspect = canvasWidth / canvasHeight.toFloat()
        if (!sourceAspect.isFinite() || sourceAspect <= 0f || abs(sourceAspect - canvasAspect) < 0.01f) {
            return TvSeekPreviewFrameLayout(canvasWidth, canvasHeight, 0, 0)
        }
        return if (sourceAspect < canvasAspect) {
            val width = (canvasHeight * sourceAspect).roundToInt().coerceIn(1, canvasWidth)
            TvSeekPreviewFrameLayout(width, canvasHeight, (canvasWidth - width) / 2, 0)
        } else {
            val height = (canvasWidth / sourceAspect).roundToInt().coerceIn(1, canvasHeight)
            TvSeekPreviewFrameLayout(canvasWidth, height, 0, (canvasHeight - height) / 2)
        }
    }
}

/**
 * Leanback trick-play provider backed by frames copied from the active playback surface.
 * Low-power TV devices often expose only one hardware decoder, so a second preview player
 * can stall playback. Capturing the rendered surface preserves pillarboxing for 4:3 sources.
 */
internal class TvSeekPreviewProvider(
    context: Context,
    private val player: Player,
    private val surfaceView: SurfaceView,
    mediaItem: MediaItem,
    durationMs: Long
) : PlaybackSeekDataProvider(), AutoCloseable {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val positions = TvSeekPreviewTimeline.positions(durationMs)
    private val ioExecutor = Executors.newSingleThreadExecutor()
    private val cacheRoot = File(context.cacheDir, "seek-previews")
    private val diskCache = File(cacheRoot, previewNamespace(mediaItem))
    private val pendingDisk = linkedSetOf<Int>()
    private val cache = object : LruCache<Int, Bitmap>(MEMORY_CACHE_LIMIT) {}
    private var captureInFlight = false
    private var closed = false

    private val periodicCapture = object : Runnable {
        override fun run() {
            if (closed) return
            captureCurrentFrame()
            mainHandler.postDelayed(this, CAPTURE_INTERVAL_MS)
        }
    }

    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_READY) {
                mainHandler.postDelayed({ captureCurrentFrame() }, FIRST_FRAME_DELAY_MS)
            }
        }

        override fun onRenderedFirstFrame() {
            mainHandler.postDelayed({ captureCurrentFrame() }, FIRST_FRAME_DELAY_MS)
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying) scheduleCaptureLoop()
        }
    }

    init {
        ioExecutor.execute {
            diskCache.mkdirs()
            pruneDiskCache(cacheRoot)
        }
        player.addListener(playerListener)
        scheduleCaptureLoop()
        mainHandler.postDelayed({ captureCurrentFrame() }, FIRST_FRAME_DELAY_MS)
    }

    override fun getSeekPositions(): LongArray = positions.copyOf()

    override fun getThumbnail(index: Int, callback: ResultCallback) {
        if (closed || index !in positions.indices) {
            callback.onThumbnailLoaded(null, index)
            return
        }
        findNearestMemoryFrame(index)?.let {
            callback.onThumbnailLoaded(it, index)
            return
        }
        if (!pendingDisk.add(index)) {
            callback.onThumbnailLoaded(null, index)
            return
        }
        ioExecutor.execute {
            val stored = loadNearestFromDisk(index)
            mainHandler.post {
                pendingDisk.remove(index)
                if (closed) {
                    stored?.recycle()
                    return@post
                }
                if (stored != null) cache.put(index, stored)
                callback.onThumbnailLoaded(stored, index)
                if (stored == null) captureCurrentFrame()
            }
        }
    }

    fun captureCurrentFrame() {
        if (
            closed ||
            captureInFlight ||
            positions.isEmpty() ||
            player.playbackState != Player.STATE_READY ||
            !surfaceView.isAttachedToWindow ||
            !surfaceView.holder.surface.isValid
        ) {
            return
        }
        val index = nearestIndex(player.currentPosition.coerceAtLeast(0L))
        if (index !in positions.indices || cache.get(index) != null || pendingDisk.contains(index)) return

        val videoSize = player.videoSize
        val sourceAspect = if (videoSize.width > 0 && videoSize.height > 0) {
            videoSize.width * videoSize.pixelWidthHeightRatio / videoSize.height.toFloat()
        } else {
            PREVIEW_WIDTH_PX / PREVIEW_HEIGHT_PX.toFloat()
        }
        val frameLayout = TvSeekPreviewFrame.layout(sourceAspect, PREVIEW_WIDTH_PX, PREVIEW_HEIGHT_PX)
        val bitmap = Bitmap.createBitmap(frameLayout.width, frameLayout.height, Bitmap.Config.RGB_565)
        captureInFlight = true
        PixelCopy.request(
            surfaceView,
            bitmap,
            { result ->
                captureInFlight = false
                if (closed) {
                    bitmap.recycle()
                    return@request
                }
                if (result != PixelCopy.SUCCESS || isLikelyBlank(bitmap)) {
                    bitmap.recycle()
                    return@request
                }
                val framed = addLetterbox(bitmap, frameLayout)
                cache.put(index, framed)
                saveToDisk(index, framed)
            },
            mainHandler
        )
    }

    override fun reset() {
        pendingDisk.clear()
    }

    override fun close() {
        if (closed) return
        closed = true
        mainHandler.removeCallbacks(periodicCapture)
        player.removeListener(playerListener)
        pendingDisk.clear()
        ioExecutor.shutdown()
    }

    private fun scheduleCaptureLoop() {
        mainHandler.removeCallbacks(periodicCapture)
        mainHandler.post(periodicCapture)
    }

    private fun nearestIndex(positionMs: Long): Int {
        if (positions.isEmpty()) return -1
        return positions.indices.minByOrNull { abs(positions[it] - positionMs) } ?: -1
    }

    private fun findNearestMemoryFrame(index: Int): Bitmap? {
        cache.get(index)?.let { return it }
        return cache.snapshot()
            .filterKeys { candidate ->
                candidate in positions.indices && abs(positions[candidate] - positions[index]) <= MAX_NEAREST_GAP_MS
            }
            .minByOrNull { (candidate, _) -> abs(positions[candidate] - positions[index]) }
            ?.value
    }

    private fun previewFile(index: Int): File = File(diskCache, "${positions[index]}.jpg")

    private fun loadNearestFromDisk(index: Int): Bitmap? {
        val now = System.currentTimeMillis()
        val exact = previewFile(index)
        val candidate = if (exact.isFile && now - exact.lastModified() <= CACHE_TTL_MS) {
            exact
        } else {
            exact.delete()
            diskCache.listFiles()
                .orEmpty()
                .asSequence()
                .filter(File::isFile)
                .filter { now - it.lastModified() <= CACHE_TTL_MS }
                .mapNotNull { file ->
                    file.nameWithoutExtension.toLongOrNull()?.let { timestamp -> file to timestamp }
                }
                .filter { (_, timestamp) -> abs(timestamp - positions[index]) <= MAX_NEAREST_GAP_MS }
                .minByOrNull { (_, timestamp) -> abs(timestamp - positions[index]) }
                ?.first
        } ?: return null
        val options = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.RGB_565 }
        return BitmapFactory.decodeFile(candidate.absolutePath, options)?.also {
            candidate.setLastModified(now)
        }
    }

    private fun saveToDisk(index: Int, bitmap: Bitmap) {
        val copy = bitmap.copy(Bitmap.Config.RGB_565, false) ?: return
        ioExecutor.execute {
            try {
                diskCache.mkdirs()
                val output = previewFile(index)
                val temporary = File(output.parentFile, "${output.name}.tmp")
                FileOutputStream(temporary).use { stream ->
                    copy.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, stream)
                    stream.flush()
                }
                if (output.exists()) output.delete()
                if (!temporary.renameTo(output)) temporary.delete()
            } finally {
                copy.recycle()
            }
        }
    }

    private fun isLikelyBlank(bitmap: Bitmap): Boolean {
        var minimum = 255
        var maximum = 0
        for (xStep in 1..5) {
            for (yStep in 1..3) {
                val pixel = bitmap.getPixel(bitmap.width * xStep / 6, bitmap.height * yStep / 4)
                val luminance = ((pixel shr 16 and 0xff) * 3 + (pixel shr 8 and 0xff) * 6 + (pixel and 0xff)) / 10
                minimum = minOf(minimum, luminance)
                maximum = maxOf(maximum, luminance)
            }
        }
        return maximum < 8 || maximum - minimum < 2 && maximum < 20
    }

    private fun addLetterbox(
        captured: Bitmap,
        layout: TvSeekPreviewFrameLayout
    ): Bitmap {
        if (
            layout.left == 0 &&
            layout.top == 0 &&
            captured.width == PREVIEW_WIDTH_PX &&
            captured.height == PREVIEW_HEIGHT_PX
        ) {
            return captured
        }
        val output = Bitmap.createBitmap(PREVIEW_WIDTH_PX, PREVIEW_HEIGHT_PX, Bitmap.Config.RGB_565)
        val canvas = Canvas(output)
        canvas.drawColor(Color.BLACK)
        val destination = RectF(
            layout.left.toFloat(),
            layout.top.toFloat(),
            (layout.left + layout.width).toFloat(),
            (layout.top + layout.height).toFloat()
        )
        canvas.drawBitmap(captured, null, destination, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        captured.recycle()
        return output
    }

    companion object {
        private const val PREVIEW_WIDTH_PX = 320
        private const val PREVIEW_HEIGHT_PX = 180
        private const val MEMORY_CACHE_LIMIT = 32
        private const val CAPTURE_INTERVAL_MS = 5_000L
        private const val FIRST_FRAME_DELAY_MS = 180L
        private const val MAX_NEAREST_GAP_MS = 30_000L
        private const val CACHE_TTL_MS = 3L * 60L * 60L * 1_000L
        private const val DISK_CACHE_LIMIT = 180
        private const val JPEG_QUALITY = 76

        private fun previewNamespace(mediaItem: MediaItem): String {
            val source = "${mediaItem.mediaId}|${mediaItem.localConfiguration?.uri}"
            return MessageDigest.getInstance("SHA-256")
                .digest(source.toByteArray())
                .take(12)
                .joinToString("") { "%02x".format(it.toInt() and 0xff) }
        }

        private fun pruneDiskCache(root: File) {
            if (!root.isDirectory) return
            val now = System.currentTimeMillis()
            val files = root.walkTopDown().filter(File::isFile).toMutableList()
            files.filter { now - it.lastModified() > CACHE_TTL_MS }.forEach {
                it.delete()
                files.remove(it)
            }
            files.sortedByDescending(File::lastModified)
                .drop(DISK_CACHE_LIMIT)
                .forEach { it.delete() }
            root.walkBottomUp()
                .filter { it != root && it.isDirectory && it.list().isNullOrEmpty() }
                .forEach { it.delete() }
        }
    }
}
