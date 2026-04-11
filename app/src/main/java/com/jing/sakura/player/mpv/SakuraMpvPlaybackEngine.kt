package com.jing.sakura.player.mpv

import android.os.Handler
import android.os.Looper
import android.util.Log
import `is`.xyz.mpv.MPVLib
import `is`.xyz.mpv.MPVNode
import kotlin.math.max

class SakuraMpvPlaybackEngine(
    private val mpvView: CustomMPVView,
    private val callback: Callback
) : MPVLib.EventObserver {

    interface Callback {
        fun onPlaybackStateChanged(isPlaying: Boolean)
        fun onProgressUpdate(positionMs: Long, durationMs: Long)
        fun onFileLoaded()
        fun onEndOfFile()
        fun onError(message: String)
    }

    private val handler = Handler(Looper.getMainLooper())
    private val progressTicker = object : Runnable {
        override fun run() {
            publishProgress()
            handler.postDelayed(this, 500L)
        }
    }

    private var initialized = false
    private var isPlaying = false

    fun initialize(): Boolean {
        if (initialized) return true
        return try {
            MPVLib.addObserver(this)
            initialized = true
            true
        } catch (e: Exception) {
            callback.onError("播放器初始化失敗：${e.message}")
            false
        }
    }

    fun loadVideoFromUrl(
        urlString: String,
        headers: Map<String, String>,
        startPositionMs: Long
    ) {
        if (!initialized) {
            callback.onError("播放器尚未初始化")
            return
        }
        try {
            val (actualUrl, embeddedHeaders) = parseUrlWithHeaders(urlString)
            val mergedHeaders = LinkedHashMap<String, String>().apply {
                putAll(embeddedHeaders)
                putAll(headers.filterValues { it.isNotBlank() })
            }
            val normalizedHeaders = mergedHeaders.entries.associate { (key, value) ->
                key.lowercase() to value.replace("；；", "; ").trim()
            }
            val userAgent = normalizedHeaders["user-agent"].orEmpty().ifBlank { DEFAULT_USER_AGENT }
            val referer = normalizedHeaders["referer"]
                ?: normalizedHeaders["referrer"]
                ?: DEFAULT_REFERER
            val extraHeaders = linkedMapOf<String, String>().apply {
                normalizedHeaders["accept"]?.takeIf { it.isNotBlank() }?.let { put("Accept", it) }
                normalizedHeaders["origin"]?.takeIf { it.isNotBlank() }?.let { put("Origin", it) }
                normalizedHeaders["cookie"]?.takeIf { it.isNotBlank() }?.let { put("Cookie", it) }
            }
            val headerValue = extraHeaders.entries.joinToString(", ") { (key, value) ->
                "$key: $value"
            }.ifBlank { "Accept: */*" }
            MPVLib.setOptionString("user-agent", userAgent)
            MPVLib.setOptionString("referrer", referer)
            MPVLib.setOptionString("http-header-fields", headerValue)
            MPVLib.setOptionString("cache", "yes")
            MPVLib.setOptionString("cache-secs", "120")
            MPVLib.setOptionString("network-timeout", "15")
            MPVLib.setOptionString(
                "demuxer-lavf-o",
                "reconnect=1:reconnect_streamed=1:reconnect_delay_max=2"
            )
            MPVLib.setOptionString("demuxer-max-bytes", "150M")
            MPVLib.setOptionString("demuxer-seekable-cache", "yes")
            MPVLib.command("loadfile", actualUrl, "replace")
            handler.removeCallbacks(progressTicker)
            handler.postDelayed(progressTicker, 1000L)
            handler.postDelayed({
                play()
                if (startPositionMs > 1_000L) {
                    seekToMs(startPositionMs)
                }
            }, 350L)
        } catch (e: Exception) {
            callback.onError("載入影片失敗：${e.message}")
        }
    }

    fun setShaderChain(shaderChain: String) {
        try {
            MPVLib.setPropertyString("glsl-shaders", shaderChain)
        } catch (e: Exception) {
            callback.onError("套用 Anime4K 失敗：${e.message}")
        }
    }

    fun togglePlayPause() {
        if (isPlaying) pause() else play()
    }

    fun play() {
        try {
            MPVLib.setPropertyBoolean("pause", false)
            isPlaying = true
            callback.onPlaybackStateChanged(true)
        } catch (e: Exception) {
            callback.onError("開始播放失敗：${e.message}")
        }
    }

    fun pause() {
        try {
            MPVLib.setPropertyBoolean("pause", true)
            isPlaying = false
            callback.onPlaybackStateChanged(false)
        } catch (e: Exception) {
            callback.onError("暫停失敗：${e.message}")
        }
    }

    fun seekBySeconds(seconds: Int) {
        try {
            val current = MPVLib.getPropertyDouble("time-pos") ?: 0.0
            val duration = MPVLib.getPropertyDouble("duration") ?: 0.0
            val target = when {
                duration <= 0.0 -> max(0.0, current + seconds)
                else -> (current + seconds).coerceIn(0.0, duration)
            }
            MPVLib.command("seek", target.toString(), "absolute")
            publishProgress()
        } catch (e: Exception) {
            callback.onError("快進快退失敗：${e.message}")
        }
    }

    fun seekToMs(positionMs: Long) {
        try {
            MPVLib.command("seek", (positionMs / 1000.0).toString(), "absolute")
        } catch (e: Exception) {
            Log.w(TAG, "seekToMs failed", e)
        }
    }

    fun release() {
        handler.removeCallbacksAndMessages(null)
        if (initialized) {
            try {
                MPVLib.removeObserver(this)
            } catch (_: Exception) {
            }
            initialized = false
        }
    }

    private fun publishProgress() {
        try {
            val positionMs = ((MPVLib.getPropertyDouble("time-pos") ?: 0.0) * 1000.0).toLong()
            val durationMs = ((MPVLib.getPropertyDouble("duration") ?: 0.0) * 1000.0).toLong()
            callback.onProgressUpdate(positionMs, durationMs)
        } catch (_: Exception) {
        }
    }

    private fun parseUrlWithHeaders(input: String): Pair<String, Map<String, String>> {
        if (!input.contains(";{") || !input.endsWith("}")) {
            return input to emptyMap()
        }
        return try {
            val url = input.substringBefore(";{").trim()
            val headerText = input.substringAfter(";{").removeSuffix("}")
            val headers = headerText.split("&&").mapNotNull { part ->
                val chunks = part.split("@", limit = 2)
                if (chunks.size == 2) {
                    chunks[0].trim() to chunks[1].trim()
                } else {
                    null
                }
            }.toMap()
            url to headers
        } catch (_: Exception) {
            input to emptyMap()
        }
    }

    override fun event(eventId: Int) {
        when (eventId) {
            6 -> handler.post {
                try {
                    isPlaying = !(MPVLib.getPropertyBoolean("pause") ?: false)
                } catch (_: Exception) {
                    isPlaying = true
                }
                callback.onPlaybackStateChanged(isPlaying)
                callback.onFileLoaded()
            }

            7 -> handler.post {
                isPlaying = false
                callback.onPlaybackStateChanged(false)
                callback.onEndOfFile()
            }
        }
    }

    override fun eventProperty(property: String, value: Boolean) {
        if (property == "pause") {
            isPlaying = !value
            handler.post { callback.onPlaybackStateChanged(isPlaying) }
        }
    }

    override fun eventProperty(property: String, value: String) = Unit
    override fun eventProperty(property: String, value: Long) = Unit
    override fun eventProperty(property: String, value: Double) = Unit
    override fun eventProperty(property: String, value: MPVNode) = Unit
    override fun eventProperty(property: String) = Unit

    private companion object {
        private const val TAG = "SakuraMpvPlayback"
        private const val DEFAULT_REFERER = "https://www.cycani.org/"
        private const val DEFAULT_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) cyc-desktop/1.0.8 Chrome/128.0.6613.36 Electron/32.0.1 Safari/537.36"
    }
}
