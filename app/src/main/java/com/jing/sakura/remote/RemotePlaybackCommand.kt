package com.jing.sakura.remote

sealed interface RemotePlaybackCommand {
    val id: String

    data class OpenAnime(
        override val id: String,
        val animeId: String,
        val sourceId: String,
        val episodeIndex: Int,
        val currentTimeSeconds: Double? = null
    ) : RemotePlaybackCommand

    data class Invalid(override val id: String) : RemotePlaybackCommand
}

enum class RemoteCommandAckStatus(val apiValue: String) {
    ACCEPTED("accepted"),
    FAILED("failed")
}
