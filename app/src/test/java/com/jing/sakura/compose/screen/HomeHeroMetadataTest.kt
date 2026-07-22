package com.jing.sakura.compose.screen

import org.junit.Assert.assertEquals
import org.junit.Test

class HomeHeroMetadataTest {
    @Test
    fun keepsOnlyStableMetadataInFixedOrder() {
        assertEquals(
            listOf("2004", "OVA", "第03集"),
            buildHeroMetadata(
                year = "2004",
                currentEpisode = "03|週二22:35後",
                tags = "Re: Cutie Honey / OVA / GAINAX"
            )
        )
    }

    @Test
    fun convertsSchedulePayloadToEpisodeWithoutShowingSchedule() {
        assertEquals(
            listOf("2026", "第02集"),
            buildHeroMetadata(
                year = "2026",
                currentEpisode = "02|週三25:25後",
                tags = "動作、奇幻"
            )
        )
    }

    @Test
    fun doesNotInventLabelsForUnknownData() {
        assertEquals(
            emptyList<String>(),
            buildHeroMetadata(
                year = "待定",
                currentEpisode = "更新時間未定",
                tags = "孤兒少女、殘酷、孤兒院"
            )
        )
    }

    @Test
    fun normalizesCompletionAndMovieFormat() {
        assertEquals(
            listOf("2025", "劇場版", "已完結"),
            buildHeroMetadata(
                year = "2025",
                currentEpisode = "全集已完結",
                tags = "電影 | 動作"
            )
        )
    }
}
