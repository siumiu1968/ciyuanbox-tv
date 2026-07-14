package com.jing.sakura.auth

import kotlin.math.ceil

data class AulamaAccount(
    val name: String,
    val role: String,
    val email: String = "",
    val photoUrl: String = ""
)

data class TvHomePayload(
    val recommendations: List<com.jing.sakura.data.AnimeData> = emptyList(),
    val todayUpdates: List<com.jing.sakura.data.AnimeData> = emptyList(),
    val theaterItems: List<com.jing.sakura.data.AnimeData> = emptyList()
)

data class TvLibraryPayload(
    val continueWatching: List<com.jing.sakura.data.AnimeData> = emptyList(),
    val favorites: List<com.jing.sakura.data.AnimeData> = emptyList(),
    val historyItems: List<TvHistoryItem> = emptyList()
)

data class TvHistoryItem(
    val anime: com.jing.sakura.data.AnimeData,
    val episodeId: String = "",
    val episodeLabel: String = "",
    val episodeIndex: Int = 0,
    val episodeCount: Int = 0,
    val currentTimeSeconds: Double = 0.0,
    val durationSeconds: Double = 0.0,
    val completed: Boolean = false,
    val sourceTypeId: String = "",
    val updatedAt: String = ""
)

data class PlaybackHistoryPayload(
    val animeId: String,
    val animeTitle: String,
    val poster: String,
    val episodeId: String,
    val episodeLabel: String,
    val episodeIndex: Int,
    val episodeCount: Int,
    val currentTimeSeconds: Double,
    val durationSeconds: Double,
    val completed: Boolean,
    val sourceTypeId: String,
    val playSessionId: String
)

data class FavoritePayload(
    val id: String,
    val title: String,
    val subtitle: String = "",
    val poster: String = "",
    val tags: List<String> = emptyList(),
    val year: String = "",
    val summary: String = "",
    val sourceTypeId: String = "",
    val hits: Long = 0L,
    val providerRating: Double = 0.0,
    val addedAt: String = "",
    val updatedAt: String = ""
)

data class AuthSession(
    val accessToken: String,
    val tokenType: String,
    val expiresAtEpochMs: Long,
    val account: AulamaAccount
) {
    fun isExpired(nowEpochMs: Long = System.currentTimeMillis()): Boolean =
        expiresAtEpochMs <= nowEpochMs
}

data class DeviceCode(
    val deviceCode: String,
    val userCode: String,
    val verificationUri: String,
    val expiresAtEpochMs: Long,
    val intervalSeconds: Long
) {
    fun remainingSeconds(nowEpochMs: Long = System.currentTimeMillis()): Long =
        ceil((expiresAtEpochMs - nowEpochMs).coerceAtLeast(0L) / 1000.0).toLong()

    fun isExpired(nowEpochMs: Long = System.currentTimeMillis()): Boolean =
        remainingSeconds(nowEpochMs) == 0L
}

sealed interface DeviceCodeRequestResult {
    data class Ready(val code: DeviceCode) : DeviceCodeRequestResult
    data class RateLimited(val retryAfterSeconds: Long) : DeviceCodeRequestResult
    data class Failed(val message: String) : DeviceCodeRequestResult
}

sealed interface DeviceTokenPollResult {
    data object Pending : DeviceTokenPollResult
    data object Expired : DeviceTokenPollResult
    data class RateLimited(val retryAfterSeconds: Long) : DeviceTokenPollResult
    data class Authorized(
        val accessToken: String,
        val tokenType: String,
        val expiresInSeconds: Long,
        val account: AulamaAccount
    ) : DeviceTokenPollResult
    data class Failed(val message: String) : DeviceTokenPollResult
}

sealed interface AccountValidationResult {
    data class Valid(val account: AulamaAccount) : AccountValidationResult
    data object Unauthorized : AccountValidationResult
    data object Unavailable : AccountValidationResult
}

sealed interface AuthUiState {
    data object Checking : AuthUiState
    data object RequestingCode : AuthUiState
    data class Waiting(
        val code: DeviceCode,
        val remainingSeconds: Long,
        val pending: Boolean = false
    ) : AuthUiState
    data class RateLimited(
        val code: DeviceCode?,
        val remainingSeconds: Long,
        val retryAfterSeconds: Long
    ) : AuthUiState
    data class Expired(val code: DeviceCode) : AuthUiState
    data class Error(val message: String) : AuthUiState
    data class Authenticated(val account: AulamaAccount) : AuthUiState
}
