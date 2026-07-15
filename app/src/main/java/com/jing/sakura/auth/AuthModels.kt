package com.jing.sakura.auth

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
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
    val animeId: String,
    val anime: com.jing.sakura.data.AnimeData,
    val episodeId: String = "",
    val episodeLabel: String = "",
    val episodeIndex: Int = 0,
    val episodeCount: Int = 0,
    val currentTimeSeconds: Double = 0.0,
    val durationSeconds: Double = 0.0,
    val completed: Boolean = false,
    val sourceTypeId: String = "",
    val updatedAt: String = "",
    val updatedAtEpochMs: Long = 0L
)

data class TvAnimeDetailPayload(
    val related: List<com.jing.sakura.data.AnimeData> = emptyList(),
    val recommendations: List<com.jing.sakura.data.AnimeData> = emptyList(),
    val personalizedRecommendations: Boolean = false
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
    val playSessionId: String,
    val updatedAt: String
)

internal object CloudTimestamp {
    private val timestampPattern = Regex(
        """^(\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2})(?:\.(\d{1,9}))?(Z|[+-]\d{2}:?\d{2})$"""
    )

    fun parseEpochMs(value: String): Long {
        val match = timestampPattern.matchEntire(value.trim()) ?: return 0L
        val fraction = match.groupValues[2].padEnd(3, '0').take(3)
        val timezone = match.groupValues[3].let {
            if (it == "Z") "+0000" else it.replace(":", "")
        }
        val normalized = "${match.groupValues[1]}.$fraction$timezone"
        return runCatching {
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.US).apply {
                isLenient = false
            }.parse(normalized)?.time ?: 0L
        }.getOrDefault(0L)
    }

    fun formatEpochMs(value: Long): String =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(Date(value.coerceAtLeast(0L)))
}

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
