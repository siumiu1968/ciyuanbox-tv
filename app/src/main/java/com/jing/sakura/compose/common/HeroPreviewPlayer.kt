package com.jing.sakura.compose.common

import android.content.Context
import android.view.LayoutInflater
import android.view.TextureView
import android.view.View
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.jing.sakura.R
import com.jing.sakura.SakuraApplication
import com.jing.sakura.home.HeroPreviewSpec
import okhttp3.OkHttpClient
import org.koin.androidx.compose.get
import org.koin.core.qualifier.qualifier

@OptIn(UnstableApi::class)
@Composable
fun HeroPreviewPlayer(
    spec: HeroPreviewSpec,
    modifier: Modifier = Modifier,
    onReady: () -> Unit = {},
    onError: (String) -> Unit = {},
    onEnded: () -> Unit = {}
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val okHttpClient: OkHttpClient = get(
        qualifier = qualifier(SakuraApplication.KoinOkHttpClient.MEDIA)
    )
    val currentOnReady by rememberUpdatedState(onReady)
    val currentOnError by rememberUpdatedState(onError)
    val currentOnEnded by rememberUpdatedState(onEnded)
    var showPoster by remember { mutableStateOf(true) }
    val controller = remember(context, okHttpClient) {
        HeroPreviewController(
            context = context,
            okHttpClient = okHttpClient,
            onReady = {
                showPoster = false
                currentOnReady()
            },
            onError = { message ->
                showPoster = true
                currentOnError(message)
            },
            onEnded = {
                showPoster = true
                currentOnEnded()
            }
        )
    }

    DisposableEffect(lifecycleOwner, controller) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> controller.start()
                Lifecycle.Event.ON_STOP -> controller.release()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            controller.start()
        }
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            controller.release()
        }
    }

    LaunchedEffect(spec.key) {
        showPoster = true
        controller.load(spec)
    }

    Box(modifier = modifier) {
        AndroidView(
            factory = controller::createPlayerView,
            update = {},
            modifier = Modifier.fillMaxSize()
        )
        if (showPoster && spec.posterUrl.isNotBlank()) {
            AsyncImage(
                model = spec.posterUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@UnstableApi
private class HeroPreviewController(
    private val context: Context,
    private val okHttpClient: OkHttpClient,
    private val onReady: () -> Unit,
    private val onError: (String) -> Unit,
    private val onEnded: () -> Unit
) {
    private var playerView: PlayerView? = null
    private var player: ExoPlayer? = null
    private var pendingSpec: HeroPreviewSpec? = null
    private var loadedKey: String? = null
    private var started = false
    private var firstFrameDispatched = false

    private val playerListener = object : Player.Listener {
        override fun onRenderedFirstFrame() {
            if (firstFrameDispatched) return
            firstFrameDispatched = true
            onReady()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_ENDED) {
                onEnded()
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            onError(error.message ?: error.errorCodeName)
        }
    }

    fun createPlayerView(viewContext: Context): PlayerView {
        playerView?.let { return it }
        val view = LayoutInflater.from(viewContext)
            .inflate(R.layout.view_hero_preview_player, null, false) as PlayerView
        view.apply {
            useController = false
            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            keepScreenOn = true
            findViewById<View>(androidx.media3.ui.R.id.exo_shutter)?.visibility = View.INVISIBLE
        }
        check(view.videoSurfaceView is TextureView) {
            "Hero preview requires a TextureView surface"
        }
        playerView = view
        if (started) {
            ensurePlayer()
            pendingSpec?.let(::loadIfNeeded)
        }
        return view
    }

    fun start() {
        if (started) return
        started = true
        if (playerView != null) {
            ensurePlayer()
            pendingSpec?.let(::loadIfNeeded)
        }
    }

    fun load(spec: HeroPreviewSpec) {
        pendingSpec = spec
        if (started && playerView != null) {
            ensurePlayer()
            loadIfNeeded(spec)
        }
    }

    fun release() {
        started = false
        loadedKey = null
        firstFrameDispatched = false
        val currentPlayer = player ?: return
        player = null
        playerView?.player = null
        currentPlayer.removeListener(playerListener)
        currentPlayer.stop()
        currentPlayer.clearMediaItems()
        currentPlayer.release()
    }

    private fun ensurePlayer(): ExoPlayer {
        player?.let { return it }
        val trackSelector = DefaultTrackSelector(context).apply {
            setParameters(
                buildUponParameters()
                    .setMaxVideoSize(MAX_VIDEO_WIDTH, MAX_VIDEO_HEIGHT)
                    .setMaxVideoBitrate(MAX_VIDEO_BITRATE)
                    // Some sources expose a single stream above the preview target.
                    // Keep the target for adaptive streams, but never drop video entirely.
                    .setExceedVideoConstraintsIfNecessary(true)
            )
        }
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                MIN_BUFFER_MS,
                MAX_BUFFER_MS,
                BUFFER_FOR_PLAYBACK_MS,
                BUFFER_AFTER_REBUFFER_MS
            )
            .setBackBuffer(0, false)
            .build()
        return ExoPlayer.Builder(context)
            .setTrackSelector(trackSelector)
            .setLoadControl(loadControl)
            .build()
            .apply {
                volume = 1f
                repeatMode = Player.REPEAT_MODE_OFF
                addListener(playerListener)
                playerView?.player = this
            }
            .also { player = it }
    }

    private fun loadIfNeeded(spec: HeroPreviewSpec) {
        if (loadedKey == spec.key) return
        loadedKey = spec.key
        firstFrameDispatched = false
        val dataSourceFactory = OkHttpDataSource.Factory { request ->
            okHttpClient.newCall(request)
        }.apply {
            setDefaultRequestProperties(spec.headers)
        }
        val mediaItem = MediaItem.Builder()
            .setUri(spec.url)
            .apply {
                if (spec.url.contains("m3u8", ignoreCase = true)) {
                    setMimeType(MimeTypes.APPLICATION_M3U8)
                }
            }
            .build()
        ensurePlayer().apply {
            setMediaSource(DefaultMediaSourceFactory(dataSourceFactory).createMediaSource(mediaItem))
            seekTo(spec.startPositionMs.coerceAtLeast(0L))
            prepare()
            playWhenReady = true
        }
    }

    private companion object {
        const val MAX_VIDEO_WIDTH = 1280
        const val MAX_VIDEO_HEIGHT = 720
        const val MAX_VIDEO_BITRATE = 2_500_000
        const val MIN_BUFFER_MS = 1_500
        const val MAX_BUFFER_MS = 5_000
        const val BUFFER_FOR_PLAYBACK_MS = 400
        const val BUFFER_AFTER_REBUFFER_MS = 800
    }
}
