package com.jing.sakura.player

import android.os.Bundle
import androidx.activity.ComponentActivity
import com.jing.sakura.data.AnimePlayListEpisode
import com.jing.sakura.repo.CycaniSource

class SeekPreviewDebugActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        PlaybackActivity.startActivity(
            this,
            NavigateToPlayerArg(
                animeName = "4:3 跳轉預覽測試",
                animeId = "debug-seek-preview-4x3",
                coverUrl = "",
                playIndex = 0,
                playlist = listOf(
                    AnimePlayListEpisode(
                        episode = "第01集",
                        episodeId = "http://10.0.2.2:4182/aulama-preview-4x3.mp4"
                    ),
                    AnimePlayListEpisode(
                        episode = "第02集",
                        episodeId = "http://10.0.2.2:4182/aulama-preview-4x3.mp4?episode=2"
                    )
                ),
                sourceId = CycaniSource.SOURCE_ID
            )
        )
        finish()
    }
}
