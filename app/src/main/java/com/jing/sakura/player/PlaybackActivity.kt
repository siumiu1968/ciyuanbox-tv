package com.jing.sakura.player

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.jing.sakura.R
import com.jing.sakura.auth.AulamaAuthRepository
import com.jing.sakura.remote.RemotePlaybackCoordinator
import com.jing.sakura.repo.WebPageRepository
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

/** Hosts the official Android TV Leanback playback surface and controls. */
class PlaybackActivity : FragmentActivity() {

    private val authRepository: AulamaAuthRepository by inject()
    private val webPageRepository: WebPageRepository by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        setContentView(R.layout.activity_playback)

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(
                    R.id.playback_fragment_container,
                    AnimePlayerFragment(),
                    TAG_PLAYER
                )
                .commit()
        }
        observeRemotePlayback()
    }

    private fun observeRemotePlayback() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                RemotePlaybackCoordinator.runWhileStarted(
                    owner = this@PlaybackActivity,
                    authRepository = authRepository,
                    webPageRepository = webPageRepository
                ) { nextPlayerArg ->
                    switchRemotePlayback(nextPlayerArg)
                    true
                }
            }
        }
    }

    private fun switchRemotePlayback(nextPlayerArg: NavigateToPlayerArg) {
        if (isFinishing || isDestroyed) return
        startActivity(createIntent(this, nextPlayerArg))
        finish()
    }

    companion object {
        private const val TAG_PLAYER = "leanback_player"

        fun startActivity(context: Context, arg: NavigateToPlayerArg) {
            context.startActivity(createIntent(context, arg))
        }

        private fun createIntent(context: Context, arg: NavigateToPlayerArg): Intent =
            Intent(context, PlaybackActivity::class.java).putExtra("video", arg)
    }
}
