package com.jing.sakura.player

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.FragmentActivity
import com.jing.sakura.R

/** Hosts the official Android TV Leanback playback surface and controls. */
class PlaybackActivity : FragmentActivity() {

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
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val playerFragment = supportFragmentManager.findFragmentByTag(TAG_PLAYER)
            as? AnimePlayerFragment
        if (playerFragment?.handlePlaybackKeyEvent(event) == true) return true
        return super.dispatchKeyEvent(event)
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
