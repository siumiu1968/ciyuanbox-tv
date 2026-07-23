package com.jing.sakura.detail

import android.os.Bundle
import androidx.activity.ComponentActivity
import com.jing.sakura.repo.CycaniSource

class DetailFocusDebugActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DetailActivity.startActivity(this, DEBUG_ANIME_ID, CycaniSource.SOURCE_ID)
        finish()
    }

    companion object {
        private const val DEBUG_ANIME_ID = "2605"
    }
}
