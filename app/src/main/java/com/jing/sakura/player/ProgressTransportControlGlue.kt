package com.jing.sakura.player

import android.app.Activity
import android.content.Context
import android.view.KeyEvent
import android.view.View
import androidx.core.content.ContextCompat
import androidx.leanback.media.PlaybackTransportControlGlue
import androidx.leanback.media.PlayerAdapter
import androidx.leanback.widget.Action
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.PlaybackControlsRow.FastForwardAction
import androidx.leanback.widget.PlaybackControlsRow.PlayPauseAction
import androidx.leanback.widget.PlaybackControlsRow.RewindAction
import androidx.leanback.widget.PlaybackControlsRow.SkipNextAction
import androidx.leanback.widget.PlaybackControlsRow.SkipPreviousAction
import com.jing.sakura.R

/** Official Leanback transport controls with only episode-specific extensions. */
class ProgressTransportControlGlue<T : PlayerAdapter>(
    context: Context,
    private val activity: Activity,
    impl: T,
    private val onPlayPauseAction: (PlayPauseAction) -> Boolean = { false },
    private val updateProgress: () -> Unit,
    private val chooseEpisode: () -> Unit,
    private val playPreviousEpisode: () -> Unit,
    private val playNextEpisode: () -> Unit
) : PlaybackTransportControlGlue<T>(context, impl) {

    private val previousAction = SkipPreviousAction(context)
    private val rewindAction = RewindAction(context)
    private val fastForwardAction = FastForwardAction(context)
    private val nextAction = SkipNextAction(context)
    private val episodeListAction = Action(
        ACTION_EPISODE_LIST,
        context.getString(R.string.player_episode_list),
        null,
        ContextCompat.getDrawable(context, R.drawable.play_list)
    )

    override fun onCreatePrimaryActions(primaryActionsAdapter: ArrayObjectAdapter) {
        super.onCreatePrimaryActions(primaryActionsAdapter)
        primaryActionsAdapter.add(0, previousAction)
        primaryActionsAdapter.add(1, rewindAction)
        primaryActionsAdapter.add(fastForwardAction)
        primaryActionsAdapter.add(nextAction)
    }

    override fun onCreateSecondaryActions(secondaryActionsAdapter: ArrayObjectAdapter) {
        super.onCreateSecondaryActions(secondaryActionsAdapter)
        secondaryActionsAdapter.add(episodeListAction)
    }

    override fun onUpdateProgress() {
        super.onUpdateProgress()
        updateProgress()
    }

    override fun onActionClicked(action: Action) {
        when (action) {
            previousAction -> playPreviousEpisode()
            rewindAction -> seekBy(-SEEK_INCREMENT_MS)
            fastForwardAction -> seekBy(SEEK_INCREMENT_MS)
            nextAction -> playNextEpisode()
            episodeListAction -> chooseEpisode()
            is PlayPauseAction -> if (!onPlayPauseAction(action)) {
                super.onActionClicked(action)
            }
            else -> super.onActionClicked(action)
        }
    }

    override fun onKey(view: View?, keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (event.action == KeyEvent.ACTION_DOWN) return true
            if (host.isControlsOverlayVisible) {
                host.hideControlsOverlay(true)
            } else {
                activity.finish()
            }
            return true
        }

        if (keyCode == KeyEvent.KEYCODE_MENU) {
            if (event.action == KeyEvent.ACTION_DOWN) return true
            chooseEpisode()
            return true
        }

        if (!host.isControlsOverlayVisible) {
            when (keyCode) {
                KeyEvent.KEYCODE_DPAD_LEFT -> {
                    if (event.action == KeyEvent.ACTION_UP) seekBy(-SEEK_INCREMENT_MS)
                    return true
                }
                KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    if (event.action == KeyEvent.ACTION_UP) seekBy(SEEK_INCREMENT_MS)
                    return true
                }
                KeyEvent.KEYCODE_DPAD_CENTER,
                KeyEvent.KEYCODE_ENTER,
                KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                    if (event.action == KeyEvent.ACTION_UP) {
                        if (playerAdapter.isPlaying) playerAdapter.pause() else playerAdapter.play()
                    }
                    return true
                }
                KeyEvent.KEYCODE_DPAD_UP,
                KeyEvent.KEYCODE_DPAD_DOWN -> {
                    if (event.action == KeyEvent.ACTION_UP) host.showControlsOverlay(true)
                    return true
                }
            }
        }
        return super.onKey(view, keyCode, event)
    }

    private fun seekBy(deltaMs: Long) {
        val target = (playerAdapter.currentPosition + deltaMs).coerceAtLeast(0L)
        val duration = playerAdapter.duration
        playerAdapter.seekTo(if (duration > 0L) target.coerceAtMost(duration) else target)
    }

    companion object {
        private const val ACTION_EPISODE_LIST = 10L
        private const val SEEK_INCREMENT_MS = 10_000L
    }
}
