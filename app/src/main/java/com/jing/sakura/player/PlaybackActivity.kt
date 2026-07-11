package com.jing.sakura.player

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.SeekBar
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.jing.sakura.SakuraApplication
import com.jing.sakura.R
import com.jing.sakura.data.Resource
import com.jing.sakura.databinding.ActivityPlaybackBinding
import com.jing.sakura.player.exo.SakuraExoPlaybackEngine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.koin.android.ext.android.get
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.core.parameter.parametersOf
import org.koin.core.qualifier.qualifier
import kotlin.math.roundToInt

class PlaybackActivity : FragmentActivity(), SakuraExoPlaybackEngine.Callback {

    private lateinit var binding: ActivityPlaybackBinding
    private lateinit var playerArg: NavigateToPlayerArg
    private val viewModel: VideoPlayerViewModel by viewModel { parametersOf(playerArg) }

    private lateinit var playbackEngine: SakuraExoPlaybackEngine

    private val overlayHandler = Handler(Looper.getMainLooper())
    private val hideOverlayRunnable = Runnable {
        if (!supportFragmentManager.isStateSaved && supportFragmentManager.findFragmentByTag(TAG_EPISODES) == null) {
            hideOverlay()
        }
    }
    private val reloadWatchdogRunnable = Runnable {
        if (!hasLoadedCurrentVideo && binding.playerLoading.isVisible) {
            if (episodeRetryCount < MAX_EPISODE_RETRY_COUNT) {
                episodeRetryCount += 1
                showLoading("連結載入逾時，正在重試…")
                viewModel.retryLoadEpisode()
            } else {
                binding.playerLoading.isGone = true
                binding.playerErrorText.text = "播放連結載入逾時，請返回上一頁再試。"
                binding.playerErrorText.isVisible = true
                showOverlay(focusPrimary = true, autoHide = false)
            }
        }
    }
    private val settleSeekRunnable = Runnable {
        if (isScrubbing && currentFocus == binding.playerProgressBar) {
            commitScrubPosition()
        }
    }

    private var overlayVisible = false
    private var currentTitle = ""
    private var isDestroyedSafely = false
    private var currentDurationMs = 0L
    private var isScrubbing = false
    private var pendingSeekPositionMs = 0L
    private var currentEpisodeId: String? = null
    private var episodeRetryCount = 0
    private var hasLoadedCurrentVideo = false

    override fun onCreate(savedInstanceState: Bundle?) {
        playerArg = getPlayerArg()
        super.onCreate(savedInstanceState)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        binding = ActivityPlaybackBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.playerProgressBar.max = PROGRESS_MAX
        binding.playbackRoot.requestFocus()

        setupButtons()
        setupProgressBar()
        setupFocusEffects(
            binding.buttonEpisodeList,
            binding.buttonPreviousEpisode,
            binding.buttonRewind,
            binding.buttonPlayPause,
            binding.buttonForward,
            binding.buttonNextEpisode,
            binding.buttonBackDetail
        )

        playbackEngine = SakuraExoPlaybackEngine(
            context = this,
            playerView = binding.playerSurface,
            okHttpClient = get(qualifier = qualifier(SakuraApplication.KoinOkHttpClient.MEDIA)),
            callback = this
        )
        playbackEngine.initialize()
        observeViewModel()
        showLoading("正在載入播放連結…")
        showOverlay(focusPrimary = false, autoHide = false)
    }

    override fun onStart() {
        super.onStart()
        viewModel.startSaveHistory()
    }

    override fun onStop() {
        viewModel.stopSaveHistory()
        super.onStop()
    }

    override fun onDestroy() {
        isDestroyedSafely = true
        overlayHandler.removeCallbacksAndMessages(null)
        if (::playbackEngine.isInitialized) {
            playbackEngine.release()
        }
        super.onDestroy()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN) {
            return super.dispatchKeyEvent(event)
        }
        return when (event.keyCode) {
            KeyEvent.KEYCODE_BACK -> {
                exitPlayback()
                true
            }

            KeyEvent.KEYCODE_MENU -> {
                openEpisodeChooser()
                true
            }

            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                if (overlayVisible && currentFocus == binding.playerProgressBar) {
                    commitScrubPosition()
                    scheduleOverlayAutoHide()
                    true
                } else
                if (!overlayVisible || currentFocus == binding.playbackRoot) {
                    playbackEngine.togglePlayPause()
                    showOverlay(focusPrimary = true)
                    true
                } else {
                    super.dispatchKeyEvent(event)
                }
            }

            KeyEvent.KEYCODE_DPAD_LEFT -> {
                if (overlayVisible && currentFocus == binding.playerProgressBar) {
                    nudgeSeekBar(-1, event.repeatCount)
                    true
                } else
                if (!overlayVisible || currentFocus == binding.playbackRoot) {
                    seekBy(-resolveSeekSeconds(event.repeatCount))
                    true
                } else {
                    super.dispatchKeyEvent(event)
                }
            }

            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                if (overlayVisible && currentFocus == binding.playerProgressBar) {
                    nudgeSeekBar(1, event.repeatCount)
                    true
                } else
                if (!overlayVisible || currentFocus == binding.playbackRoot) {
                    seekBy(resolveSeekSeconds(event.repeatCount))
                    true
                } else {
                    super.dispatchKeyEvent(event)
                }
            }

            KeyEvent.KEYCODE_DPAD_UP,
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                if (!overlayVisible) {
                    showOverlay(focusPrimary = true)
                    true
                } else {
                    super.dispatchKeyEvent(event)
                }
            }

            else -> super.dispatchKeyEvent(event)
        }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.playerSubTitle.collectLatest { episodeTitle ->
                        binding.playerEpisodeText.text = episodeTitle
                    }
                }
                launch {
                    viewModel.videoUrl.collectLatest { resource ->
                        renderVideoState(resource)
                    }
                }
            }
        }
    }

    private fun renderVideoState(resource: Resource<EpisodeUrlAndHistory>) {
        when (resource) {
            is Resource.Loading -> {
                showLoading(if (resource.silent) "正在切換集數…" else "正在載入播放連結…")
            }

            is Resource.Error -> {
                overlayHandler.removeCallbacks(reloadWatchdogRunnable)
                hideLoading()
                binding.playerErrorText.text = if (resource.message.isBlank()) {
                    "播放失敗，請稍後再試。"
                } else {
                    "播放失敗：${resource.message}"
                }
                binding.playerErrorText.isVisible = true
                showOverlay(focusPrimary = true, autoHide = false)
            }

            is Resource.Success -> {
                val payload = resource.data
                if (currentEpisodeId != payload.episode.episodeId) {
                    currentEpisodeId = payload.episode.episodeId
                    episodeRetryCount = 0
                }
                hasLoadedCurrentVideo = false
                currentTitle = playerArg.animeName
                binding.playerTitleText.text = currentTitle
                binding.playerEpisodeText.text = payload.episode.episode
                binding.playerErrorText.isGone = true
                binding.playerLoading.isVisible = true
                viewModel.changePlayingEpisode(payload.episode)
                overlayHandler.removeCallbacks(reloadWatchdogRunnable)
                overlayHandler.postDelayed(reloadWatchdogRunnable, LOAD_TIMEOUT_MS)
                playbackEngine.loadVideoFromUrl(
                    urlString = payload.videoUrl,
                    headers = payload.headers,
                    startPositionMs = payload.lastPlayPosition
                )
            }
        }
    }

    private fun setupButtons() {
        binding.buttonEpisodeList.setOnClickListener { openEpisodeChooser() }
        binding.buttonRewind.setOnClickListener { seekBy(-10) }
        binding.buttonPreviousEpisode.setOnClickListener {
            viewModel.playPreviousEpisodeIfExists()
            showLoading("正在切換上一集…")
            showOverlay(focusPrimary = false, autoHide = false)
        }
        binding.buttonPlayPause.setOnClickListener {
            playbackEngine.togglePlayPause()
            showOverlay(focusPrimary = true)
        }
        binding.buttonForward.setOnClickListener { seekBy(10) }
        binding.buttonNextEpisode.setOnClickListener {
            viewModel.playNextEpisodeAdjacent()
            showLoading("正在切換下一集…")
            showOverlay(focusPrimary = false, autoHide = false)
        }
        binding.buttonBackDetail.setOnClickListener { exitPlayback() }
    }

    private fun setupProgressBar() {
        binding.playerProgressBar.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                isScrubbing = true
                pendingSeekPositionMs = currentPositionFromProgress()
                overlayHandler.removeCallbacks(hideOverlayRunnable)
                overlayHandler.removeCallbacks(settleSeekRunnable)
                updateSeekPreview(pendingSeekPositionMs)
            } else {
                if (isScrubbing) {
                    commitScrubPosition()
                }
                scheduleOverlayAutoHide()
            }
        }
        binding.playerProgressBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    isScrubbing = true
                    pendingSeekPositionMs = progressToPosition(progress)
                    updateSeekPreview(pendingSeekPositionMs)
                    scheduleSeekSettle()
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                commitScrubPosition()
            }
        })
    }

    private fun setupFocusEffects(vararg views: View) {
        views.forEach { view ->
            view.setOnFocusChangeListener { v, hasFocus ->
                if (hasFocus) {
                    showOverlay(focusPrimary = false)
                }
            }
        }
    }

    private fun openEpisodeChooser() {
        showOverlay(focusPrimary = false, autoHide = false)
        val episodes = viewModel.playList
        if (episodes.isEmpty()) return
        ChooseEpisodeDialog(
            dataList = episodes,
            defaultSelectIndex = viewModel.playIndex,
            viewWidth = 300,
            getText = { _, item -> item.episode },
            onChoose = { position, item ->
                viewModel.changePlayingEpisode(item)
                viewModel.playEpisodeOfIndex(position)
                showLoading("正在切換到 ${item.episode}…")
            }
        ).apply {
            dismissListener = {
                binding.buttonEpisodeList.requestFocus()
                scheduleOverlayAutoHide()
            }
        }.show(supportFragmentManager, TAG_EPISODES)
    }

    private fun seekBy(seconds: Int) {
        playbackEngine.seekBySeconds(seconds)
        showOverlay(focusPrimary = false)
    }

    private fun nudgeSeekBar(direction: Int, repeatCount: Int) {
        val seconds = resolveSeekSeconds(repeatCount)
        val deltaMs = seconds * 1000L * direction
        isScrubbing = true
        pendingSeekPositionMs = (currentPositionFromProgress() + deltaMs).coerceIn(0L, currentDurationMs)
        val progress = positionToProgress(pendingSeekPositionMs)
        binding.playerProgressBar.progress = progress
        updateSeekPreview(pendingSeekPositionMs)
        overlayHandler.removeCallbacks(hideOverlayRunnable)
        scheduleSeekSettle()
    }

    private fun showLoading(message: String) {
        hasLoadedCurrentVideo = false
        binding.playerLoading.isVisible = true
        binding.playerLoadingText.text = message
        binding.playerErrorText.isGone = true
        binding.playerEpisodeText.text = message
    }

    private fun hideLoading() {
        overlayHandler.removeCallbacks(reloadWatchdogRunnable)
        binding.playerLoading.isGone = true
    }

    private fun showOverlay(focusPrimary: Boolean, autoHide: Boolean = true) {
        overlayHandler.removeCallbacks(hideOverlayRunnable)
        if (!overlayVisible) {
            overlayVisible = true
            binding.playerOverlay.isVisible = true
            binding.playerDimmer.isVisible = true
            binding.playerOverlay.animate().alpha(1f).setDuration(180).start()
            binding.playerDimmer.animate().alpha(1f).setDuration(180).start()
        }
        if (focusPrimary) {
            binding.buttonPlayPause.requestFocus()
        }
        if (autoHide) {
            scheduleOverlayAutoHide()
        }
    }

    private fun hideOverlay() {
        overlayHandler.removeCallbacks(hideOverlayRunnable)
        if (!overlayVisible) return
        overlayVisible = false
        binding.playerOverlay.animate().alpha(0f).setDuration(180).withEndAction {
            binding.playerOverlay.isGone = true
        }.start()
        binding.playerDimmer.animate().alpha(0f).setDuration(180).withEndAction {
            binding.playerDimmer.isGone = true
        }.start()
        binding.playbackRoot.requestFocus()
    }

    private fun scheduleOverlayAutoHide() {
        overlayHandler.removeCallbacks(hideOverlayRunnable)
        if (currentFocus != binding.playerProgressBar) {
            overlayHandler.postDelayed(hideOverlayRunnable, OVERLAY_HIDE_DELAY_MS)
        }
    }

    private fun scheduleSeekSettle() {
        overlayHandler.removeCallbacks(settleSeekRunnable)
        overlayHandler.postDelayed(settleSeekRunnable, SEEK_SETTLE_DELAY_MS)
    }

    private fun updatePlaybackButton(isPlaying: Boolean) {
        binding.buttonPlayPause.setImageResource(
            if (isPlaying) R.drawable.ic_player_pause else R.drawable.ic_player_play
        )
        binding.buttonPlayPause.contentDescription = if (isPlaying) "暫停" else "播放"
    }

    override fun onPlaybackStateChanged(isPlaying: Boolean) {
        updatePlaybackButton(isPlaying)
        scheduleOverlayAutoHide()
    }

    override fun onProgressUpdate(positionMs: Long, durationMs: Long) {
        if (durationMs > 0) {
            currentDurationMs = durationMs
            val progress = positionToProgress(positionMs)
            if (!isScrubbing) {
                binding.playerProgressBar.progress = progress
                binding.playerCurrentTime.text = formatMillis(positionMs)
            }
            binding.playerTotalTime.text = formatMillis(durationMs)
            viewModel.onPlayPositionChange(positionMs, durationMs)
        }
    }

    override fun onFileLoaded() {
        hasLoadedCurrentVideo = true
        hideLoading()
        binding.playerTitleText.text = currentTitle
        showOverlay(focusPrimary = false)
    }

    override fun onEndOfFile() {
        if (!isDestroyedSafely) {
            viewModel.playNextEpisodeIfExists()
        }
    }

    override fun onError(message: String) {
        overlayHandler.removeCallbacks(reloadWatchdogRunnable)
        hideLoading()
        binding.playerErrorText.text = if (message.isBlank()) "播放器發生錯誤" else message
        binding.playerErrorText.isVisible = true
        showOverlay(focusPrimary = true, autoHide = false)
    }

    private fun exitPlayback() {
        isDestroyedSafely = true
        overlayHandler.removeCallbacksAndMessages(null)
        viewModel.stopSaveHistory()
        if (::playbackEngine.isInitialized) {
            playbackEngine.release()
        }
        finish()
    }

    private fun getPlayerArg(): NavigateToPlayerArg {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getSerializableExtra("video", NavigateToPlayerArg::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getSerializableExtra("video") as? NavigateToPlayerArg
        } ?: error("Missing player arguments")
    }

    private fun formatMillis(value: Long): String {
        val totalSeconds = (value / 1000L).coerceAtLeast(0L)
        val hours = totalSeconds / 3600L
        val minutes = (totalSeconds % 3600L) / 60L
        val seconds = totalSeconds % 60L
        return if (hours > 0) {
            "%d:%02d:%02d".format(hours, minutes, seconds)
        } else {
            "%02d:%02d".format(minutes, seconds)
        }
    }

    private fun currentPositionFromProgress(): Long {
        return if (isScrubbing && pendingSeekPositionMs > 0L) {
            pendingSeekPositionMs
        } else {
            progressToPosition(binding.playerProgressBar.progress)
        }
    }

    private fun positionToProgress(positionMs: Long): Int {
        if (currentDurationMs <= 0L) return 0
        return ((positionMs.toDouble() / currentDurationMs.toDouble()) * PROGRESS_MAX)
            .roundToInt()
            .coerceIn(0, PROGRESS_MAX)
    }

    private fun progressToPosition(progress: Int): Long {
        if (currentDurationMs <= 0L) return 0L
        return ((progress.toDouble() / PROGRESS_MAX.toDouble()) * currentDurationMs.toDouble())
            .roundToInt()
            .toLong()
            .coerceIn(0L, currentDurationMs)
    }

    private fun updateSeekPreview(positionMs: Long) {
        binding.playerCurrentTime.text = formatMillis(positionMs)
    }

    private fun commitScrubPosition() {
        if (!isScrubbing) return
        overlayHandler.removeCallbacks(settleSeekRunnable)
        val target = pendingSeekPositionMs.coerceIn(0L, currentDurationMs)
        playbackEngine.seekToMs(target)
        binding.playerProgressBar.progress = positionToProgress(target)
        binding.playerCurrentTime.text = formatMillis(target)
        isScrubbing = false
        pendingSeekPositionMs = 0L
    }

    private fun resolveSeekSeconds(repeatCount: Int): Int {
        val durationMinutes = (currentDurationMs / 60_000L).coerceAtLeast(1L).toInt()
        val tapStep = when {
            durationMinutes <= 25 -> 10
            durationMinutes <= 45 -> 15
            durationMinutes <= 75 -> 20
            durationMinutes <= 120 -> 30
            else -> 45
        }
        if (repeatCount <= 0) {
            return tapStep
        }
        val holdBase = when {
            durationMinutes <= 25 -> 20
            durationMinutes <= 45 -> 30
            durationMinutes <= 75 -> 45
            durationMinutes <= 120 -> 60
            else -> 90
        }
        val multiplier = when {
            repeatCount < 3 -> 1
            repeatCount < 7 -> 2
            repeatCount < 12 -> 3
            else -> 4
        }
        val maxStep = (durationMinutes * 6).coerceAtLeast(holdBase)
        return (holdBase * multiplier).coerceAtMost(maxStep)
    }

    companion object {
        private const val OVERLAY_HIDE_DELAY_MS = 2600L
        private const val SEEK_SETTLE_DELAY_MS = 900L
        private const val LOAD_TIMEOUT_MS = 6500L
        private const val MAX_EPISODE_RETRY_COUNT = 1
        private const val PROGRESS_MAX = 1000
        private const val TAG_EPISODES = "player_episodes"

        fun startActivity(context: Context, arg: NavigateToPlayerArg) {
            Intent(context, PlaybackActivity::class.java).apply {
                putExtra("video", arg)
                context.startActivity(this)
            }
        }
    }
}
