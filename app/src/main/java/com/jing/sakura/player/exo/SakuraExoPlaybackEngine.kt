package com.jing.sakura.player.exo

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.ui.PlayerView
import okhttp3.OkHttpClient
import kotlin.math.max

@OptIn(UnstableApi::class)
class SakuraExoPlaybackEngine(
    context: Context,
    private val playerView: PlayerView,
    private val okHttpClient: OkHttpClient,
    private val callback: Callback
) {

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

    private val trackSelector = DefaultTrackSelector(context).apply {
        setParameters(
            buildUponParameters()
                .setMaxVideoSize(1920, 1080)
                .setMaxVideoBitrate(8_500_000)
                .setExceedVideoConstraintsIfNecessary(false)
        )
    }

    private val player = ExoPlayer.Builder(context)
        .setTrackSelector(trackSelector)
        .setSeekBackIncrementMs(10_000)
        .setSeekForwardIncrementMs(10_000)
        .build()
        .apply {
            setSeekParameters(SeekParameters.CLOSEST_SYNC)
            addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    when (playbackState) {
                        Player.STATE_READY -> {
                            if (!hasDispatchedFileLoaded) {
                                hasDispatchedFileLoaded = true
                                if (pendingStartPositionMs > 1_000L) {
                                    seekTo(pendingStartPositionMs)
                                }
                                callback.onFileLoaded()
                            }
                            callback.onPlaybackStateChanged(isPlaying)
                        }

                        Player.STATE_ENDED -> {
                            callback.onPlaybackStateChanged(false)
                            callback.onEndOfFile()
                        }

                        else -> Unit
                    }
                }

                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    callback.onPlaybackStateChanged(isPlaying)
                }

                override fun onPlayerError(error: PlaybackException) {
                    callback.onError(error.errorCodeName.ifBlank {
                        error.message ?: "播放器載入失敗"
                    })
                }
            })
        }

    private var pendingStartPositionMs = 0L
    private var hasDispatchedFileLoaded = false

    init {
        playerView.useController = false
        playerView.player = player
    }

    fun initialize(): Boolean = true

    fun loadVideoFromUrl(
        urlString: String,
        headers: Map<String, String>,
        startPositionMs: Long
    ) {
        pendingStartPositionMs = startPositionMs
        hasDispatchedFileLoaded = false
        val mergedHeaders = linkedMapOf(
            "User-Agent" to DEFAULT_USER_AGENT,
            "Referer" to DEFAULT_REFERER,
            "Origin" to DEFAULT_ORIGIN,
            "Accept" to DEFAULT_ACCEPT
        ).apply {
            putAll(headers.filterValues { it.isNotBlank() })
        }
        val dataSourceFactory = OkHttpDataSource.Factory { request ->
            okHttpClient.newCall(request)
        }.apply {
            setUserAgent(mergedHeaders["User-Agent"])
            setDefaultRequestProperties(mergedHeaders)
        }
        val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)
        val mediaItem = MediaItem.Builder()
            .setUri(urlString)
            .apply {
                if (urlString.contains("m3u8", ignoreCase = true)) {
                    setMimeType(MimeTypes.APPLICATION_M3U8)
                }
            }
            .build()
        player.setMediaSource(mediaSourceFactory.createMediaSource(mediaItem))
        player.prepare()
        player.playWhenReady = true
        handler.removeCallbacks(progressTicker)
        handler.post(progressTicker)
    }

    fun setShaderChain(shaderChain: String) = Unit

    fun togglePlayPause() {
        player.playWhenReady = !player.playWhenReady
    }

    fun seekBySeconds(seconds: Int) {
        val duration = player.duration.takeIf { it > 0L } ?: Long.MAX_VALUE
        val target = max(0L, player.currentPosition + seconds * 1000L).coerceAtMost(duration)
        player.seekTo(target)
        publishProgress()
    }

    fun seekToMs(positionMs: Long) {
        player.seekTo(positionMs.coerceAtLeast(0L))
    }

    fun release() {
        handler.removeCallbacksAndMessages(null)
        player.stop()
        player.clearMediaItems()
        playerView.player = null
        player.release()
    }

    private fun publishProgress() {
        val durationMs = player.duration.takeIf { it > 0L } ?: 0L
        callback.onProgressUpdate(player.currentPosition.coerceAtLeast(0L), durationMs)
    }

    companion object {
        private const val DEFAULT_USER_AGENT = "cyc-desktop/1.0.8"
        private const val DEFAULT_REFERER = "https://www.cycani.org/"
        private const val DEFAULT_ORIGIN = "https://www.cycani.org"
        private const val DEFAULT_ACCEPT = "*/*"
    }
}
