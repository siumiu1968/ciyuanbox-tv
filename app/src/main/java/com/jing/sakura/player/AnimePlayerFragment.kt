package com.jing.sakura.player

import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.KeyEvent
import android.widget.TextView
import androidx.annotation.OptIn
import androidx.core.graphics.drawable.toDrawable
import androidx.leanback.app.VideoSupportFragment
import androidx.leanback.app.VideoSupportFragmentGlueHost
import androidx.leanback.widget.PlaybackControlsRow.PlayPauseAction
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.effect.LanczosResample
import androidx.media3.ui.leanback.LeanbackPlayerAdapter
import com.jing.sakura.R
import com.jing.sakura.SakuraApplication
import com.jing.sakura.data.Resource
import com.jing.sakura.extend.secondsToMinuteAndSecondText
import com.jing.sakura.extend.showLongToast
import com.jing.sakura.extend.showShortToast
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import org.koin.android.ext.android.get
import org.koin.androidx.viewmodel.ext.android.activityViewModel
import org.koin.core.parameter.parametersOf
import org.koin.core.qualifier.qualifier

@OptIn(UnstableApi::class)
class AnimePlayerFragment : VideoSupportFragment() {

    private val viewModel: VideoPlayerViewModel by activityViewModel {
        @Suppress("DEPRECATION")
        val arg = requireActivity().intent.getSerializableExtra("video") as NavigateToPlayerArg
        parametersOf(arg)
    }

    private val okHttpClient: OkHttpClient =
        get(qualifier = qualifier(SakuraApplication.KoinOkHttpClient.MEDIA))

    private var player: ExoPlayer? = null
    private var glue: ProgressTransportControlGlue<LeanbackPlayerAdapter>? = null
    private var seekPreviewProvider: TvSeekPreviewProvider? = null
    private var seekPreviewMediaItem: MediaItem? = null
    private var fast4kEnabled = false
    private var skipSegmentActions: View? = null
    private var skipSegmentButton: CountdownActionButton? = null
    private var continueOutroButton: TextView? = null
    private var activePlaybackSkip: ActivePlaybackSkip? = null
    private var continueOutroChosen = false
    private var handledEndedEpisodeIndex = -1

    private val analyticsListener = object : AnalyticsListener {
        override fun onDroppedVideoFrames(
            eventTime: AnalyticsListener.EventTime,
            droppedFrames: Int,
            elapsedMs: Long
        ) {
            if (fast4kEnabled && droppedFrames >= FAST_4K_DROPPED_FRAME_LIMIT) {
                view?.post {
                    disableFast4k(R.string.player_fast_4k_overload)
                }
            }
        }
    }

    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            Log.d(TAG, "playbackState=${playbackStateName(playbackState)}")
            when (playbackState) {
                Player.STATE_BUFFERING -> progressBarManager.show()
                Player.STATE_READY -> progressBarManager.hide()
                Player.STATE_ENDED -> {
                    progressBarManager.hide()
                    advanceToNextEpisode()
                }
            }
            if (playbackState == Player.STATE_READY) installSeekPreviewProvider()
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            snapshotPlaybackPosition()
            if (isPlaying) viewModel.startSaveHistory() else viewModel.stopSaveHistory()
        }

        override fun onRenderedFirstFrame() {
            progressBarManager.hide()
        }

        override fun onPlayerError(error: PlaybackException) {
            Log.e(TAG, "playerError=${error.errorCodeName}: ${error.message}", error)
            if (fast4kEnabled && error.isVideoEffectFailure()) {
                val resumePosition = player?.currentPosition?.coerceAtLeast(0L) ?: 0L
                disableFast4k(R.string.player_fast_4k_overload)
                player?.apply {
                    prepare()
                    if (resumePosition > 0L) seekTo(resumePosition)
                    play()
                }
                return
            }
            progressBarManager.hide()
            viewModel.stopSaveHistory()
            glue?.subtitle = getString(R.string.player_retry_hint)
            requireContext().showLongToast(
                getString(R.string.player_load_error_template, error.errorCodeName)
            )
        }

        override fun onVideoSizeChanged(videoSize: VideoSize) {
            Log.d(TAG, "videoSize=${videoSize.width}x${videoSize.height}")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requireActivity().window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.background = Color.BLACK.toDrawable()
        progressBarManager.setRootView(view as ViewGroup)
        progressBarManager.enableProgressBar()
        progressBarManager.initialDelay = 0L
        skipSegmentActions = requireActivity().findViewById(R.id.player_skip_actions)
        skipSegmentButton = requireActivity().findViewById<CountdownActionButton>(R.id.player_skip_segment).apply {
            setOnClickListener { activateSkipSegment() }
        }
        continueOutroButton = requireActivity().findViewById<TextView>(R.id.player_continue_outro).apply {
            setOnClickListener {
                continueOutroChosen = true
                hideSkipSegment()
                glue?.host?.showControlsOverlay(true)
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.playerSubTitle.collectLatest { glue?.subtitle = it }
                }
                launch {
                    viewModel.videoUrl.collectLatest(::renderVideoState)
                }
                launch {
                    viewModel.playbackSegments.collectLatest {
                        renderSkipSegment(player?.currentPosition ?: 0L)
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        if (player == null) player = buildPlayer()
    }

    override fun onStop() {
        hideSkipSegment()
        snapshotPlaybackPosition()
        viewModel.stopSaveHistory()
        destroyPlayer()
        super.onStop()
    }

    private fun renderVideoState(resource: Resource<EpisodeUrlAndHistory>) {
        when (resource) {
            is Resource.Loading -> {
                progressBarManager.show()
                glue?.subtitle = getString(R.string.player_loading_episode)
            }
            is Resource.Error -> {
                progressBarManager.hide()
                glue?.subtitle = getString(R.string.player_retry_hint)
                requireContext().showLongToast(
                    getString(R.string.player_load_error_template, resource.message)
                )
            }
            is Resource.Success -> loadEpisode(resource.data)
        }
    }

    private fun loadEpisode(payload: EpisodeUrlAndHistory) {
        val localPlayer = player ?: return
        progressBarManager.show()
        handledEndedEpisodeIndex = -1
        continueOutroChosen = false
        hideSkipSegment()
        clearSeekPreviewProvider()

        val dataSourceFactory = OkHttpDataSource.Factory { request ->
            okHttpClient.newCall(request)
        }.apply {
            setDefaultRequestProperties(payload.headers)
        }
        val mediaItem = MediaItem.Builder()
            .setUri(payload.videoUrl)
            .apply {
                if (payload.videoUrl.contains("m3u8", ignoreCase = true)) {
                    setMimeType(MimeTypes.APPLICATION_M3U8)
                }
            }
            .build()
        val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)
        val mediaSource = mediaSourceFactory.createMediaSource(mediaItem)
        seekPreviewMediaItem = mediaItem

        val startPositionMs = payload.lastPlayPosition.takeIf { position ->
            position > 0L &&
                (payload.videoDuration <= 0L || payload.videoDuration - position >= 10_000L)
        } ?: 0L
        if (payload.lastPlayPosition > 0L) {
            if (startPositionMs == 0L) {
                requireContext().showShortToast(getString(R.string.player_finished_restart))
            } else {
                requireContext().showShortToast(
                    getString(
                        R.string.player_resume_template,
                        (startPositionMs / 1000L).secondsToMinuteAndSecondText()
                    )
                )
            }
        }

        viewModel.changePlayingEpisode(payload.episode)
        glue?.title = viewModel.anime.animeName
        glue?.subtitle = payload.episode.episode
        localPlayer.setMediaSource(mediaSource)
        if (startPositionMs > 0L) localPlayer.seekTo(startPositionMs)
        localPlayer.prepare()
        localPlayer.playWhenReady = true
    }

    private fun buildPlayer(): ExoPlayer {
        val trackSelector = DefaultTrackSelector(requireContext()).apply {
            setParameters(
                buildUponParameters()
                    .setMaxVideoSize(1920, 1080)
                    .setMaxVideoBitrate(8_500_000)
                    .setExceedVideoConstraintsIfNecessary(false)
            )
        }
        return ExoPlayer.Builder(requireContext())
            .setTrackSelector(trackSelector)
            .setSeekBackIncrementMs(SEEK_INCREMENT_MS)
            .setSeekForwardIncrementMs(SEEK_INCREMENT_MS)
            .build()
            .apply {
                setSeekParameters(SeekParameters.CLOSEST_SYNC)
                addListener(playerListener)
                addAnalyticsListener(analyticsListener)
                prepareGlue(this)
                playWhenReady = true
            }
    }

    private fun prepareGlue(localPlayer: ExoPlayer) {
        ProgressTransportControlGlue(
            context = requireContext(),
            activity = requireActivity(),
            impl = LeanbackPlayerAdapter(
                requireContext(),
                localPlayer,
                PLAYER_UPDATE_INTERVAL_MILLIS
            ),
            onPlayPauseAction = { action ->
                if (
                    action.index == PlayPauseAction.INDEX_PLAY &&
                    viewModel.videoUrl.value is Resource.Error
                ) {
                    viewModel.retryLoadEpisode()
                    true
                } else {
                    false
                }
            },
            updateProgress = {
                viewModel.onPlayPositionChange(
                    localPlayer.currentPosition.coerceAtLeast(0L),
                    localPlayer.contentDuration.coerceAtLeast(0L)
                )
                renderSkipSegment(localPlayer.currentPosition.coerceAtLeast(0L))
                seekPreviewProvider?.captureCurrentFrame()
            },
            chooseEpisode = ::openEpisodeChooser,
            playPreviousEpisode = viewModel::playPreviousEpisodeIfExists,
            playNextEpisode = viewModel::playNextEpisodeAdjacent,
            toggleFast4k = ::toggleFast4k
        ).apply {
            title = viewModel.anime.animeName
            subtitle = viewModel.playerSubTitle.value
            isSeekEnabled = true
            isControlsOverlayAutoHideEnabled = true
            host = VideoSupportFragmentGlueHost(this@AnimePlayerFragment)
            glue = this
        }
    }

    private fun snapshotPlaybackPosition() {
        player?.let {
            viewModel.onPlayPositionChange(
                it.currentPosition.coerceAtLeast(0L),
                it.contentDuration.coerceAtLeast(0L)
            )
        }
    }

    private fun destroyPlayer() {
        clearSeekPreviewProvider()
        player?.let {
            it.removeListener(playerListener)
            it.removeAnalyticsListener(analyticsListener)
            it.pause()
            it.release()
        }
        player = null
        glue = null
        fast4kEnabled = false
    }

    private fun installSeekPreviewProvider() {
        if (seekPreviewProvider != null) return
        val durationMs = player?.contentDuration?.takeIf { it > 0L } ?: return
        val mediaItem = seekPreviewMediaItem ?: return
        val localPlayer = player ?: return
        seekPreviewProvider = TvSeekPreviewProvider(
            context = requireContext(),
            player = localPlayer,
            surfaceView = surfaceView,
            mediaItem = mediaItem,
            durationMs = durationMs
        ).also {
            glue?.seekProvider = it
        }
    }

    private fun clearSeekPreviewProvider() {
        glue?.seekProvider = null
        seekPreviewProvider?.close()
        seekPreviewProvider = null
        seekPreviewMediaItem = null
    }

    private fun toggleFast4k(): Boolean {
        if (fast4kEnabled) {
            disableFast4k(R.string.player_fast_4k_disabled)
            return false
        }
        val localPlayer = player ?: return false
        val targetSize = fast4kTargetSize()
        if (targetSize == null) {
            requireContext().showShortToast(getString(R.string.player_fast_4k_requires_4k_output))
            return false
        }
        return runCatching {
            localPlayer.setVideoEffects(
                listOf(LanczosResample.scaleToFit(targetSize.first, targetSize.second))
            )
            fast4kEnabled = true
            requireContext().showShortToast(getString(R.string.player_fast_4k_enabled))
            true
        }.getOrElse { error ->
            Log.w(TAG, "Unable to enable fast 4K", error)
            localPlayer.setVideoEffects(emptyList())
            requireContext().showShortToast(getString(R.string.player_fast_4k_unavailable))
            false
        }
    }

    private fun disableFast4k(messageRes: Int) {
        if (!fast4kEnabled) return
        fast4kEnabled = false
        player?.setVideoEffects(emptyList())
        glue?.updateFast4kAction(false)
        requireContext().showShortToast(getString(messageRes))
    }

    private fun fast4kTargetSize(): Pair<Int, Int>? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return null
        @Suppress("DEPRECATION")
        val mode = requireActivity().windowManager.defaultDisplay.mode
        val longEdge = maxOf(mode.physicalWidth, mode.physicalHeight)
        val shortEdge = minOf(mode.physicalWidth, mode.physicalHeight)
        return if (longEdge >= FAST_4K_WIDTH && shortEdge >= FAST_4K_HEIGHT) {
            FAST_4K_WIDTH to FAST_4K_HEIGHT
        } else {
            null
        }
    }

    private fun PlaybackException.isVideoEffectFailure(): Boolean =
        errorCode == PlaybackException.ERROR_CODE_VIDEO_FRAME_PROCESSOR_INIT_FAILED ||
            errorCode == PlaybackException.ERROR_CODE_VIDEO_FRAME_PROCESSING_FAILED

    private fun openEpisodeChooser() {
        ChooseEpisodeDialog(
            dataList = viewModel.playList,
            defaultSelectIndex = viewModel.playIndex,
            viewWidthDp = EPISODE_PANEL_WIDTH_DP,
            getText = { _, item -> item.episode }
        ) { index, _ ->
            viewModel.playEpisodeOfIndex(index)
        }.showNow(parentFragmentManager, TAG_EPISODE_CHOOSER)
    }

    fun handlePlaybackKeyEvent(event: android.view.KeyEvent): Boolean {
        val primaryFocused = skipSegmentButton?.hasFocus() == true
        val continueFocused = continueOutroButton?.hasFocus() == true
        return if (primaryFocused || continueFocused) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_CENTER,
                KeyEvent.KEYCODE_ENTER,
                KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                    if (event.action == KeyEvent.ACTION_UP) {
                        if (continueFocused) continueOutroButton?.performClick()
                        else activateSkipSegment()
                    }
                    true
                }
                KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    if (event.action == KeyEvent.ACTION_DOWN && primaryFocused && continueOutroButton?.visibility == View.VISIBLE) {
                        continueOutroButton?.requestFocus()
                    }
                    true
                }
                KeyEvent.KEYCODE_DPAD_LEFT -> {
                    if (event.action == KeyEvent.ACTION_DOWN && continueFocused) {
                        skipSegmentButton?.requestFocus()
                    }
                    true
                }
                KeyEvent.KEYCODE_BACK,
                KeyEvent.KEYCODE_DPAD_DOWN -> {
                    if (event.action == KeyEvent.ACTION_UP) {
                        skipSegmentButton?.clearFocus()
                        continueOutroButton?.clearFocus()
                        glue?.host?.showControlsOverlay(true)
                    }
                    true
                }
                else -> glue?.onKey(view, event.keyCode, event) == true
            }
        } else {
            glue?.onKey(view, event.keyCode, event) == true
        }
    }

    override fun onDestroyView() {
        skipSegmentButton?.setOnClickListener(null)
        skipSegmentButton?.cancelCountdown()
        continueOutroButton?.setOnClickListener(null)
        skipSegmentActions = null
        skipSegmentButton = null
        continueOutroButton = null
        activePlaybackSkip = null
        super.onDestroyView()
    }

    private fun renderSkipSegment(positionMs: Long) {
        val button = skipSegmentButton ?: return
        val durationMs = player?.contentDuration?.coerceAtLeast(0L) ?: 0L
        if (PlaybackSkipPolicy.shouldAutoAdvanceAtEnd(positionMs, durationMs, viewModel.hasNextEpisode())) {
            advanceToNextEpisode()
            return
        }
        val active = PlaybackSkipPolicy.activeSkip(
            viewModel.playbackSegments.value,
            positionMs,
            viewModel.hasNextEpisode()
        )
        if (active == null) {
            hideSkipSegment()
            return
        }
        if (active.advancesEpisode && continueOutroChosen) {
            hideSkipSegment()
            return
        }
        val actionChanged = activePlaybackSkip != active
        activePlaybackSkip = active
        button.text = getString(
            when {
                active.type == ActivePlaybackSkip.Type.INTRO -> R.string.player_skip_intro
                active.advancesEpisode -> R.string.player_next_episode
                else -> R.string.player_skip_outro
            }
        )
        continueOutroButton?.visibility = if (active.advancesEpisode) View.VISIBLE else View.GONE
        val actions = skipSegmentActions ?: return
        if (actions.visibility != View.VISIBLE) {
            actions.alpha = 0f
            actions.visibility = View.VISIBLE
            actions.animate().alpha(1f).setDuration(180L).start()
            button.post {
                if (activePlaybackSkip == active && actions.visibility == View.VISIBLE) {
                    button.requestFocus()
                }
            }
        }
        if (actionChanged) {
            button.cancelCountdown()
            if (active.advancesEpisode) {
                button.startCountdown(AUTO_NEXT_COUNTDOWN_MS) {
                    if (activePlaybackSkip == active) advanceToNextEpisode()
                }
            }
        }
    }

    private fun hideSkipSegment() {
        activePlaybackSkip = null
        skipSegmentButton?.cancelCountdown()
        skipSegmentActions?.apply {
            animate().cancel()
            visibility = View.GONE
        }
        skipSegmentButton?.apply {
            clearFocus()
        }
        continueOutroButton?.apply {
            visibility = View.GONE
            clearFocus()
        }
    }

    private fun activateSkipSegment() {
        val active = activePlaybackSkip ?: return
        if (active.advancesEpisode) {
            advanceToNextEpisode()
        } else {
            player?.seekTo(active.targetMs)
            hideSkipSegment()
        }
    }

    private fun advanceToNextEpisode() {
        if (!viewModel.hasNextEpisode() || handledEndedEpisodeIndex == viewModel.playIndex) {
            hideSkipSegment()
            return
        }
        handledEndedEpisodeIndex = viewModel.playIndex
        hideSkipSegment()
        viewModel.playNextEpisodeAdjacent()
    }

    private fun playbackStateName(playbackState: Int): String = when (playbackState) {
        Player.STATE_IDLE -> "IDLE"
        Player.STATE_BUFFERING -> "BUFFERING"
        Player.STATE_READY -> "READY"
        Player.STATE_ENDED -> "ENDED"
        else -> "UNKNOWN($playbackState)"
    }

    companion object {
        private const val TAG = "AnimePlayerFragment"
        private const val TAG_EPISODE_CHOOSER = "episode_chooser"
        private const val PLAYER_UPDATE_INTERVAL_MILLIS = 250
        private const val SEEK_INCREMENT_MS = 10_000L
        private const val EPISODE_PANEL_WIDTH_DP = 420
        private const val FAST_4K_WIDTH = 3840
        private const val FAST_4K_HEIGHT = 2160
        private const val FAST_4K_DROPPED_FRAME_LIMIT = 24
        private const val AUTO_NEXT_COUNTDOWN_MS = 5_000L
    }
}
