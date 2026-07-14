package com.jing.sakura.player

import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
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
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
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

    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            Log.d(TAG, "playbackState=${playbackStateName(playbackState)}")
            when (playbackState) {
                Player.STATE_BUFFERING -> progressBarManager.show()
                Player.STATE_READY -> progressBarManager.hide()
                Player.STATE_ENDED -> {
                    progressBarManager.hide()
                    viewModel.playNextEpisodeIfExists()
                }
            }
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

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.playerSubTitle.collectLatest { glue?.subtitle = it }
                }
                launch {
                    viewModel.videoUrl.collectLatest(::renderVideoState)
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        if (player == null) player = buildPlayer()
    }

    override fun onStop() {
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
        val mediaSource = DefaultMediaSourceFactory(dataSourceFactory).createMediaSource(mediaItem)

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
            },
            chooseEpisode = ::openEpisodeChooser,
            playPreviousEpisode = viewModel::playPreviousEpisodeIfExists,
            playNextEpisode = viewModel::playNextEpisodeAdjacent
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
        player?.let {
            it.removeListener(playerListener)
            it.pause()
            it.release()
        }
        player = null
        glue = null
    }

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
    }
}
