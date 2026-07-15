package com.jing.sakura.home

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jing.sakura.SakuraApplication
import com.jing.sakura.auth.AulamaAuthRepository
import com.jing.sakura.data.AnimeData
import com.jing.sakura.data.AnimeDetailPageData
import com.jing.sakura.data.AnimePlayListEpisode
import com.jing.sakura.data.HomePageData
import com.jing.sakura.data.NamedValue
import com.jing.sakura.data.Resource
import com.jing.sakura.player.NavigateToPlayerArg
import com.jing.sakura.repo.AnimationSource
import com.jing.sakura.repo.CycaniSource
import com.jing.sakura.repo.WebPageRepository
import com.jing.sakura.room.VideoHistoryDao
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

class HomeViewModel(
    private val repository: WebPageRepository,
    private val authRepository: AulamaAuthRepository,
    private val videoHistoryDao: VideoHistoryDao
) : ViewModel() {

    private val _homePageData = MutableStateFlow<Resource<HomePageData>>(Resource.Loading)
    private val _recommendations = MutableStateFlow<List<AnimeData>>(emptyList())
    private val _syncedRows = MutableStateFlow<List<NamedValue<List<AnimeData>>>>(emptyList())
    private val _todayUpdates = MutableStateFlow<List<AnimeData>>(emptyList())
    private val _theaterItems = MutableStateFlow<List<AnimeData>>(emptyList())
    private val _heroDescriptions = MutableStateFlow<Map<String, String>>(emptyMap())
    private val requestedHeroDetails = ConcurrentHashMap.newKeySet<String>()
    private val _heroPreviewState = MutableStateFlow<HeroPreviewState>(HeroPreviewState.Idle)
    private val remoteHeroPreviewHistory = ConcurrentHashMap<String, HeroPreviewHistory>()

    private val _sp = SakuraApplication.context.getSharedPreferences("source", Context.MODE_PRIVATE)

    var currentSourceId: String = _sp.getString("id", CycaniSource.SOURCE_ID)?.takeIf { id ->
        repository.animationSources.any { it.sourceId == id }
    } ?: CycaniSource.SOURCE_ID
        private set


    private val _currentSource =
        MutableStateFlow(repository.requireAnimationSource(currentSourceId))

    val currentSource: StateFlow<AnimationSource>
        get() = _currentSource

    val homePageData: StateFlow<Resource<HomePageData>>
        get() = _homePageData

    val recommendations: StateFlow<List<AnimeData>>
        get() = _recommendations

    val syncedRows: StateFlow<List<NamedValue<List<AnimeData>>>>
        get() = _syncedRows

    val todayUpdates: StateFlow<List<AnimeData>>
        get() = _todayUpdates

    val theaterItems: StateFlow<List<AnimeData>>
        get() = _theaterItems

    val heroDescriptions: StateFlow<Map<String, String>>
        get() = _heroDescriptions

    val heroPreviewState: StateFlow<HeroPreviewState>
        get() = _heroPreviewState

    @Volatile
    var lastHomePageData: HomePageData? = null
        private set

    private var loadDataJob: Pair<String, Job>? = null
    private var heroPreviewJob: Job? = null
    private var heroPreviewRequestKey: String? = null
    private val heroPreviewGeneration = AtomicLong(0L)

    init {
        loadData(false)
        viewModelScope.launch(Dispatchers.IO) {
            authRepository.session.collectLatest { session ->
                if (session == null) {
                    clearSyncedContent()
                } else {
                    loadSyncedContent()
                }
            }
        }
    }

    fun changeSource(newSourceId: String) {
        if (newSourceId == currentSourceId) {
            return
        }
        val source = repository.requireAnimationSource(newSourceId)
        currentSourceId = newSourceId
        _sp.edit().putString("id", newSourceId).apply()
        viewModelScope.launch {
            _currentSource.emit(source)
        }
        loadData(false, saveLastData = false)
    }

    private fun processHomePageData(data: HomePageData): HomePageData {
        val map = mutableMapOf<String, MutableList<AnimeData>>()
        data.seriesList.forEach { (name, videos) ->
            var exists = map[name]
            if (exists == null) {
                exists = mutableListOf()
                map[name] = exists
            }
            exists.addAll(videos)
        }
        return data.copy(
            seriesList = map.entries.map { (name, videos) ->
                NamedValue(
                    name = name,
                    value = videos.distinctBy { it.id }
                )
            }
        )
    }

    fun loadData(silent: Boolean = false, saveLastData: Boolean = true) {
        val lastValue = _homePageData.value
        lastHomePageData = if (saveLastData && lastValue is Resource.Success) {
            lastValue.data
        } else {
            null
        }
        val sourceId = currentSourceId
        val job = loadDataJob
        if (job != null) {
            if (job.first == sourceId) {
                return
            }
            job.second.cancel()
        }
        loadDataJob = sourceId to viewModelScope.launch(Dispatchers.IO) {
            try {
                _homePageData.emit(Resource.Loading(silent = silent))
                val homeData = repository.fetchHomePage(currentSourceId)
                _homePageData.emit(Resource.Success(processHomePageData(homeData)))
            } catch (ex: Exception) {
                if (ex is CancellationException) {
                    throw ex
                }
                lastHomePageData = null
                Log.e("homepage", "请求数据失败", ex)

                val message = "請求資料失敗：" + ex.message
                _homePageData.emit(Resource.Error(message))
            } finally {
                loadDataJob = null
            }
        }
    }

    fun getAllSources(): List<AnimationSource> = repository.animationSources

    fun refreshSyncedContent() {
        if (authRepository.session.value == null) return
        viewModelScope.launch(Dispatchers.IO) { loadSyncedContent() }
    }

    fun loadHeroDescription(anime: AnimeData) {
        if (anime.id.isBlank() || !anime.description.needsHeroDescriptionRefresh()) return
        if (_heroDescriptions.value[anime.id].isNullOrBlank().not()) return
        if (!requestedHeroDetails.add(anime.id)) return

        viewModelScope.launch(Dispatchers.IO) {
            val description = runCatching {
                repository.fetchDetailPage(anime.id, anime.sourceId).description.trim()
            }.getOrDefault("")
            if (description.isNotBlank()) {
                _heroDescriptions.update { it + (anime.id to description) }
            } else {
                requestedHeroDetails.remove(anime.id)
            }
        }
    }

    fun replaceRemoteHeroPreviewHistory(items: List<HeroPreviewHistory>) {
        remoteHeroPreviewHistory.clear()
        items.groupBy { previewRequestKey(it.animeId, it.sourceId) }
            .mapValues { (_, values) -> values.maxBy(HeroPreviewHistory::updatedAtMs) }
            .forEach(remoteHeroPreviewHistory::put)
    }

    fun prepareHeroPreview(anime: AnimeData, force: Boolean = false) {
        if (anime.id.isBlank() || anime.sourceId.isBlank()) {
            cancelHeroPreview()
            return
        }
        val requestKey = previewRequestKey(anime.id, anime.sourceId)
        val currentState = _heroPreviewState.value
        if (!force && heroPreviewRequestKey == requestKey && heroPreviewJob?.isActive == true) {
            return
        }
        if (!force && currentState is HeroPreviewState.Ready &&
            currentState.spec.navigateToPlayerArg.animeId == anime.id &&
            currentState.spec.navigateToPlayerArg.sourceId == anime.sourceId
        ) {
            return
        }

        val generation = heroPreviewGeneration.incrementAndGet()
        heroPreviewJob?.cancel()
        heroPreviewRequestKey = requestKey
        _heroPreviewState.value = HeroPreviewState.Loading(requestKey)
        heroPreviewJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val detail = repository.fetchDetailPage(anime.id, anime.sourceId)
                val selection = selectPreviewEpisode(detail, anime.id, anime.sourceId)
                val response = repository.fetchVideoUrl(
                    episodeId = selection.episode.episodeId,
                    sourceId = anime.sourceId,
                    animeId = anime.id
                )
                val video = when (response) {
                    is Resource.Success -> response.data
                    is Resource.Error -> throw PreviewPreparationException(response.message)
                    is Resource.Loading -> throw PreviewPreparationException("預覽影片尚未準備好")
                }
                val playerArg = NavigateToPlayerArg(
                    animeName = detail.animeName.ifBlank { anime.title },
                    animeId = anime.id,
                    coverUrl = detail.imageUrl.ifBlank { anime.imageUrl },
                    playIndex = selection.playIndex,
                    playlist = selection.playlist,
                    sourceId = anime.sourceId
                )
                publishHeroPreview(
                    generation,
                    HeroPreviewState.Ready(
                        HeroPreviewSpec(
                            key = "$requestKey:${selection.episode.episodeId}:$generation",
                            url = video.url,
                            headers = video.headers,
                            startPositionMs = selection.startPositionMs,
                            posterUrl = playerArg.coverUrl,
                            navigateToPlayerArg = playerArg
                        )
                    )
                )
            } catch (exception: Exception) {
                if (exception is CancellationException) throw exception
                Log.e("hero-preview", "準備首頁預覽失敗", exception)
                publishHeroPreview(
                    generation,
                    HeroPreviewState.Error(
                        requestKey = requestKey,
                        message = exception.message ?: "預覽影片載入失敗"
                    )
                )
            } finally {
                if (heroPreviewGeneration.get() == generation) {
                    heroPreviewJob = null
                    heroPreviewRequestKey = null
                }
            }
        }
    }

    fun cancelHeroPreview() {
        heroPreviewGeneration.incrementAndGet()
        heroPreviewJob?.cancel()
        heroPreviewJob = null
        heroPreviewRequestKey = null
        _heroPreviewState.value = HeroPreviewState.Idle
    }

    private fun publishHeroPreview(generation: Long, state: HeroPreviewState) {
        if (heroPreviewGeneration.get() == generation) {
            _heroPreviewState.value = state
        }
    }

    private fun selectPreviewEpisode(
        detail: AnimeDetailPageData,
        animeId: String,
        sourceId: String
    ): PreviewEpisodeSelection {
        val remoteHistory = remoteHeroPreviewHistory[previewRequestKey(animeId, sourceId)]
        selectHistoryEpisode(
            detail = detail,
            episodeId = remoteHistory?.episodeId,
            episodeIndex = remoteHistory?.episodeIndex,
            positionMs = remoteHistory?.positionMs
        )?.let { return it }

        val localHistory = videoHistoryDao.queryLastHistoryOfAnimeId(animeId, sourceId)
        selectHistoryEpisode(
            detail = detail,
            episodeId = localHistory?.episodeId,
            episodeIndex = null,
            positionMs = localHistory?.lastPlayTime
        )?.let { return it }

        val defaultPlaylist = detail.playLists
            .getOrNull(detail.defaultPlayListIndex)
            ?.takeIf { it.episodeList.isNotEmpty() }
            ?: detail.playLists.firstOrNull { it.defaultPlayList && it.episodeList.isNotEmpty() }
            ?: detail.playLists.firstOrNull { it.episodeList.isNotEmpty() }
            ?: throw PreviewPreparationException("未有可播放集數")
        return PreviewEpisodeSelection(
            playlist = defaultPlaylist.episodeList,
            playIndex = 0,
            episode = defaultPlaylist.episodeList.first(),
            startPositionMs = 0L
        )
    }

    private fun selectHistoryEpisode(
        detail: AnimeDetailPageData,
        episodeId: String?,
        episodeIndex: Int?,
        positionMs: Long?
    ): PreviewEpisodeSelection? {
        if (!episodeId.isNullOrBlank()) {
            detail.playLists.forEach { playlist ->
                val playIndex = playlist.episodeList.indexOfFirst { it.episodeId == episodeId }
                if (playIndex >= 0) {
                    return PreviewEpisodeSelection(
                        playlist = playlist.episodeList,
                        playIndex = playIndex,
                        episode = playlist.episodeList[playIndex],
                        startPositionMs = positionMs?.coerceAtLeast(0L) ?: 0L
                    )
                }
            }
        }
        val fallbackPlaylist = detail.playLists
            .getOrNull(detail.defaultPlayListIndex)
            ?.takeIf { it.episodeList.isNotEmpty() }
            ?: detail.playLists.firstOrNull { it.defaultPlayList && it.episodeList.isNotEmpty() }
            ?: detail.playLists.firstOrNull { it.episodeList.isNotEmpty() }
            ?: return null
        val fallbackIndex = episodeIndex
            ?.coerceIn(0, fallbackPlaylist.episodeList.lastIndex)
            ?: return null
        return PreviewEpisodeSelection(
            playlist = fallbackPlaylist.episodeList,
            playIndex = fallbackIndex,
            episode = fallbackPlaylist.episodeList[fallbackIndex],
            startPositionMs = positionMs?.coerceAtLeast(0L) ?: 0L
        )
    }

    private suspend fun loadSyncedContent() {
        runCatching { authRepository.fetchTvHome() }
            .onSuccess { payload ->
                _recommendations.value = payload.recommendations
                _todayUpdates.value = payload.todayUpdates
                _theaterItems.value = payload.theaterItems
            }
            .onFailure { Log.e("tv-home-sync", "載入首頁同步內容失敗", it) }

        runCatching { authRepository.fetchTvLibrary() }
            .onSuccess { payload ->
                replaceRemoteHeroPreviewHistory(
                    payload.historyItems.mapIndexed { index, history ->
                        HeroPreviewHistory(
                            animeId = history.animeId.ifBlank { history.anime.id },
                            sourceId = history.sourceTypeId.ifBlank { history.anime.sourceId },
                            episodeId = history.episodeId,
                            episodeIndex = history.episodeIndex,
                            positionMs = history.currentTimeSeconds.toPositionMs(),
                            updatedAtMs = history.updatedAtEpochMs.coerceAtLeast(
                                (payload.historyItems.size - index).toLong()
                            )
                        )
                    }
                )
                _syncedRows.value = buildList {
                    if (payload.continueWatching.isNotEmpty()) {
                        add(NamedValue("繼續觀看", payload.continueWatching))
                    }
                    if (payload.favorites.isNotEmpty()) {
                        add(NamedValue("我的收藏", payload.favorites))
                    }
                }
            }
            .onFailure { Log.e("tv-library-sync", "載入帳戶收藏及紀錄失敗", it) }
    }

    private fun clearSyncedContent() {
        replaceRemoteHeroPreviewHistory(emptyList())
        _recommendations.value = emptyList()
        _syncedRows.value = emptyList()
        _todayUpdates.value = emptyList()
        _theaterItems.value = emptyList()
    }

    private data class PreviewEpisodeSelection(
        val playlist: List<AnimePlayListEpisode>,
        val playIndex: Int,
        val episode: AnimePlayListEpisode,
        val startPositionMs: Long
    )

    private class PreviewPreparationException(message: String) : Exception(message)

    private fun String.needsHeroDescriptionRefresh(): Boolean =
        isBlank() || any { char ->
            char in '\u3040'..'\u30ff' ||
                char in '\u31f0'..'\u31ff' ||
                char in '\uff66'..'\uff9f'
        }

    private fun previewRequestKey(animeId: String, sourceId: String): String =
        "$sourceId:$animeId"

    private fun Double.toPositionMs(): Long =
        takeIf { it.isFinite() && it > 0.0 }
            ?.times(1_000.0)
            ?.coerceAtMost(Long.MAX_VALUE.toDouble())
            ?.toLong()
            ?: 0L

}
