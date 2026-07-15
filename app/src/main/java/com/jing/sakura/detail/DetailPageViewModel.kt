package com.jing.sakura.detail

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jing.sakura.auth.AulamaAuthRepository
import com.jing.sakura.auth.FavoritePayload
import com.jing.sakura.auth.TvHistoryItem
import com.jing.sakura.data.AnimeDetailPageData
import com.jing.sakura.data.Resource
import com.jing.sakura.repo.WebPageRepository
import com.jing.sakura.room.VideoHistoryDao
import com.jing.sakura.room.VideoHistoryEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

class DetailPageViewModel constructor(
    private val animeId: String,
    private val repository: WebPageRepository,
    private val videoHistoryDao: VideoHistoryDao,
    private val authRepository: AulamaAuthRepository,
    val sourceId: String
) : ViewModel() {

    private var _detailPageData = MutableStateFlow<Resource<AnimeDetailPageData>>(Resource.Loading)

    val detailPageData: StateFlow<Resource<AnimeDetailPageData>>
        get() = _detailPageData

    private var _latestProgress = MutableStateFlow<Resource<VideoHistoryEntity>>(Resource.Loading)

    val latestProgress: StateFlow<Resource<VideoHistoryEntity>>
        get() = _latestProgress

    private var loadDataJob: Job? = null

    private val _favoriteUiState = MutableStateFlow(FavoriteUiState())
    val favoriteUiState: StateFlow<FavoriteUiState> = _favoriteUiState

    private val favoriteErrorChannel = Channel<String>(Channel.BUFFERED)
    val favoriteErrors = favoriteErrorChannel.receiveAsFlow()

    private var favoriteJob: Job? = null

    init {
        loadData()
        loadFavoriteState()
    }

    fun loadData() {
        loadDataJob?.cancel()
        loadDataJob = viewModelScope.launch(Dispatchers.IO) {
            _detailPageData.emit(Resource.Loading)
            try {
                val localHistoryJob = async {
                    videoHistoryDao.queryLastHistoryOfAnimeId(animeId, sourceId)
                }
                val cloudHistoryJob = async {
                    runCatching { authRepository.fetchTvLibrary() }
                        .getOrNull()
                        ?.historyItems
                        ?.firstOrNull { item ->
                            (item.animeId == animeId || item.anime.id == animeId) &&
                                (item.sourceTypeId.isBlank() || item.sourceTypeId == sourceId)
                        }
                }
                val cloudDetailJob = async {
                    runCatching { authRepository.fetchTvAnimeDetail(animeId) }.getOrNull()
                }
                val sourceData = repository.fetchDetailPage(animeId, sourceId)
                val cloudDetail = cloudDetailJob.await()
                val data = sourceData.copy(
                    otherAnimeList = cloudDetail?.related
                        ?.takeIf(List<*>::isNotEmpty)
                        ?: sourceData.otherAnimeList
                )
                val localHistory = localHistoryJob.await()
                val remoteHistory = cloudHistoryJob.await()?.toLocalHistory(data)
                val history = listOfNotNull(localHistory, remoteHistory)
                    .maxByOrNull(VideoHistoryEntity::updateTime)
                if (remoteHistory != null &&
                    (localHistory == null || remoteHistory.updateTime > localHistory.updateTime)
                ) {
                    videoHistoryDao.saveHistory(remoteHistory)
                }
                history?.let { _latestProgress.emit(Resource.Success(it)) }
                _detailPageData.emit(
                    Resource.Success(
                        data.copy(lastPlayEpisodePosition = data.positionFor(history?.episodeId))
                    )
                )
            } catch (ex: Exception) {
                if (ex is CancellationException) {
                    throw ex
                }
                Log.e("homepage", "请求数据失败", ex)

                val message = "請求資料失敗：" + ex.message
                _detailPageData.emit(Resource.Error(message))
            }
        }
    }

    fun fetchHistory() {
        viewModelScope.launch(Dispatchers.Default) {
            videoHistoryDao.queryLastHistoryOfAnimeId(animeId, sourceId)?.let {
                _latestProgress.emit(Resource.Success(it))
            }
        }
    }

    fun toggleFavorite(detail: AnimeDetailPageData) {
        val current = _favoriteUiState.value
        if (current.isLoading || current.isUpdating) return

        val shouldFavorite = !current.isFavorite
        _favoriteUiState.value = current.copy(isUpdating = true)
        favoriteJob = viewModelScope.launch(Dispatchers.IO) {
            val result = runCatching {
                if (shouldFavorite) {
                    authRepository.saveFavorite(detail.toFavoritePayload())
                } else {
                    authRepository.deleteFavorite(detail.animeId)
                }
            }
            if (result.getOrDefault(false)) {
                _favoriteUiState.value = FavoriteUiState(
                    isFavorite = shouldFavorite,
                    isLoading = false,
                    isUpdating = false
                )
            } else {
                _favoriteUiState.value = current.copy(isUpdating = false)
                favoriteErrorChannel.send(
                    if (authRepository.session.value == null) {
                        "登入狀態已失效，請重新登入"
                    } else {
                        "收藏更新失敗，請稍後再試"
                    }
                )
            }
        }
    }

    private fun loadFavoriteState() {
        favoriteJob?.cancel()
        favoriteJob = viewModelScope.launch(Dispatchers.IO) {
            if (authRepository.session.value == null) {
                _favoriteUiState.value = FavoriteUiState(isLoading = false)
                return@launch
            }
            _favoriteUiState.value = FavoriteUiState(isLoading = true)
            runCatching { authRepository.fetchFavorites() }
                .onSuccess { favorites ->
                    _favoriteUiState.value = FavoriteUiState(
                        isFavorite = favorites.any { it.id == animeId },
                        isLoading = false
                    )
                }
                .onFailure {
                    _favoriteUiState.value = FavoriteUiState(isLoading = false)
                    favoriteErrorChannel.send("未能讀取收藏狀態，請稍後再試")
                }
        }
    }

    private fun AnimeDetailPageData.toFavoritePayload(): FavoritePayload {
        val tags = infoValue("類型", "类型")
            .split('、', ',', '，', '/', '|')
            .map(String::trim)
            .filter(String::isNotBlank)
            .distinct()
        val providerRating = infoValue("評分", "评分")
            .let { RATING_PATTERN.find(it)?.value?.toDoubleOrNull() }
            ?.coerceIn(0.0, 10.0)
            ?: 0.0
        return FavoritePayload(
            id = animeId,
            title = animeName,
            poster = imageUrl,
            tags = tags,
            year = infoValue("年份", "年"),
            summary = description,
            sourceTypeId = sourceId,
            providerRating = providerRating
        )
    }

    private fun AnimeDetailPageData.infoValue(vararg labels: String): String {
        val entry = infoList.firstOrNull { info ->
            labels.any { label ->
                info.trim().startsWith("$label：") || info.trim().startsWith("$label:")
            }
        }.orEmpty()
        return entry.substringAfter('：', entry.substringAfter(':', "")).trim()
    }

    private fun TvHistoryItem.toLocalHistory(detail: AnimeDetailPageData): VideoHistoryEntity? {
        val indexedPlaylist = detail.playLists
            .getOrNull(detail.defaultPlayListIndex)
            ?.takeIf { it.episodeList.isNotEmpty() }
            ?: detail.playLists.firstOrNull { it.defaultPlayList && it.episodeList.isNotEmpty() }
            ?: detail.playLists.firstOrNull { it.episodeList.isNotEmpty() }
            ?: return null
        val exactEpisode = detail.playLists
            .asSequence()
            .flatMap { it.episodeList.asSequence() }
            .firstOrNull { it.episodeId == episodeId }
        val mappedEpisode = exactEpisode ?: indexedPlaylist.episodeList.getOrNull(
            episodeIndex.coerceIn(0, indexedPlaylist.episodeList.lastIndex)
        ) ?: return null
        return VideoHistoryEntity(
            animeId = animeId.ifBlank { anime.id },
            animeName = anime.title.ifBlank { detail.animeName },
            episodeId = mappedEpisode.episodeId,
            lastEpisodeName = episodeLabel.ifBlank { mappedEpisode.episode },
            updateTime = updatedAtEpochMs.coerceAtLeast(1L),
            lastPlayTime = currentTimeSeconds.toPositionMs(),
            coverUrl = anime.imageUrl.ifBlank { detail.imageUrl },
            videoDuration = durationSeconds.toPositionMs(),
            sourceId = sourceId
        )
    }

    private fun AnimeDetailPageData.positionFor(episodeId: String?): Pair<Int, Int> {
        if (episodeId.isNullOrBlank()) return lastPlayEpisodePosition
        playLists.forEachIndexed { playlistIndex, playlist ->
            val episodeIndex = playlist.episodeList.indexOfFirst { it.episodeId == episodeId }
            if (episodeIndex >= 0) return playlistIndex to episodeIndex
        }
        return lastPlayEpisodePosition
    }

    private fun Double.toPositionMs(): Long =
        takeIf { it.isFinite() && it > 0.0 }
            ?.times(1_000.0)
            ?.coerceAtMost(Long.MAX_VALUE.toDouble())
            ?.toLong()
            ?: 0L

    companion object {
        private val RATING_PATTERN = Regex("""\d+(?:\.\d+)?""")
    }

}

data class FavoriteUiState(
    val isFavorite: Boolean = false,
    val isLoading: Boolean = true,
    val isUpdating: Boolean = false
)
