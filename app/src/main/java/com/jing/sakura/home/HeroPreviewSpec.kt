package com.jing.sakura.home

import com.jing.sakura.player.NavigateToPlayerArg

data class HeroPreviewSpec(
    val key: String,
    val url: String,
    val headers: Map<String, String>,
    val startPositionMs: Long,
    val posterUrl: String,
    val navigateToPlayerArg: NavigateToPlayerArg
)

data class HeroPreviewHistory(
    val animeId: String,
    val sourceId: String,
    val episodeId: String,
    val positionMs: Long,
    val updatedAtMs: Long
)

sealed interface HeroPreviewState {
    object Idle : HeroPreviewState

    data class Loading(val requestKey: String) : HeroPreviewState

    data class Ready(val spec: HeroPreviewSpec) : HeroPreviewState

    data class Error(
        val requestKey: String,
        val message: String
    ) : HeroPreviewState
}
