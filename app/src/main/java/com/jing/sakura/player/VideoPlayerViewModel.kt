package com.jing.sakura.player

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jing.sakura.auth.AulamaAuthRepository
import com.jing.sakura.auth.PlaybackHistoryPayload
import com.jing.sakura.data.AnimePlayListEpisode
import com.jing.sakura.data.Resource
import com.jing.sakura.repo.WebPageRepository
import com.jing.sakura.room.VideoHistoryDao
import com.jing.sakura.room.VideoHistoryEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.UUID
import java.util.regex.Pattern


class VideoPlayerViewModel(
    val anime: NavigateToPlayerArg,
    private val repository: WebPageRepository,
    private val videoHistoryDao: VideoHistoryDao,
    private val authRepository: AulamaAuthRepository
) : ViewModel() {

    private val TAG = VideoPlayerViewModel::class.java.simpleName

    private var _playList = emptyList<AnimePlayListEpisode>()

    private var _saveHistoryJob: Job? = null

    private var lastRemoteSyncAt = 0L

    private var playSessionId = UUID.randomUUID().toString()

    @Volatile
    private var currentPlayPosition: Long = 0L

    @Volatile
    private var videoDuration: Long = 0L

    val playList: List<AnimePlayListEpisode>
        get() = _playList

    private val _playIndex = MutableStateFlow(-1)
    val playIndex: Int
        get() = _playIndex.value

    private val _playerSubTitle = MutableStateFlow("")

    val playerSubTitle: MutableStateFlow<String>
        get() = _playerSubTitle

    private val _videoUrl = MutableStateFlow<Resource<EpisodeUrlAndHistory>>(Resource.Loading)
    val videoUrl: StateFlow<Resource<EpisodeUrlAndHistory>>
        get() = _videoUrl

    @Volatile
    private var playingEpisode: AnimePlayListEpisode? = null

    private var loadVideoJob: Pair<AnimePlayListEpisode, Job>? = null


    init {
        viewModelScope.launch {
            _playIndex.collectLatest { index ->
                if (index >= 0) {
                    val episode = _playList[index]
                    _playerSubTitle.emit(episode.episode)
                    fetchVideoUrl(episode)
                }
            }
        }
        init()
    }

    fun changePlayingEpisode(episode: AnimePlayListEpisode) {
        if (playingEpisode?.episodeId != episode.episodeId) {
            playSessionId = UUID.randomUUID().toString()
            lastRemoteSyncAt = 0L
        }
        this.playingEpisode = episode
    }

    fun init() {
        viewModelScope.launch {
            this@VideoPlayerViewModel._playList = anime.playlist
            _playIndex.emit(anime.playIndex)
        }
    }

    private fun fetchVideoUrl(episode: AnimePlayListEpisode) {
        val job = loadVideoJob
        if (job != null && job.first == episode) {
            return
        }
        job?.second?.cancel()
        loadVideoJob = episode to viewModelScope.launch(Dispatchers.IO) {
            try {
                _videoUrl.emit(Resource.Loading)
                val resp = repository.fetchVideoUrl(
                    episode.episodeId,
                    animeId = anime.animeId,
                    sourceId = anime.sourceId
                )
                val history = videoHistoryDao.queryHistoryByEpisodeId(
                    animeId = anime.animeId,
                    episodeId = episode.episodeId,
                    sourceId = anime.sourceId
                )
                when (resp) {
                    is Resource.Error -> _videoUrl.emit(Resource.Error(resp.message))
                    is Resource.Success -> _videoUrl.emit(
                        Resource.Success(
                            EpisodeUrlAndHistory(
                                videoUrl = resp.data.url,
                                videoDuration = history?.videoDuration ?: 0L,
                                lastPlayPosition = history?.lastPlayTime ?: 0L,
                                headers = resp.data.headers,
                                episode = episode
                            )
                        )
                    )

                    else -> {}
                }
            } catch (e: Exception) {
                if (e is CancellationException) {
                    throw e
                }
                Log.e(TAG, "fetchVideoUrl: ${e.message}", e)
                _videoUrl.emit(Resource.Error(e.message ?: ""))
            } finally {
                loadVideoJob = null
            }
        }
    }

    fun playEpisodeOfIndex(index: Int) {
        viewModelScope.launch {
            _playIndex.emit(index)
        }
    }

    fun startSaveHistory() {
        stopSaveHistory()
        _saveHistoryJob = viewModelScope.launch(Dispatchers.IO) {
            while (true) {
                playingEpisode?.let { ep ->
                    val history = VideoHistoryEntity(
                        animeId = anime.animeId,
                        animeName = anime.animeName,
                        episodeId = ep.episodeId,
                        lastEpisodeName = ep.episode,
                        updateTime = System.currentTimeMillis(),
                        lastPlayTime = currentPlayPosition,
                        coverUrl = anime.coverUrl,
                        videoDuration = videoDuration,
                        sourceId = anime.sourceId
                    )
                    videoHistoryDao.saveHistory(history)
                    val now = System.currentTimeMillis()
                    if (now - lastRemoteSyncAt >= REMOTE_SYNC_INTERVAL_MS) {
                        val duration = videoDuration.coerceAtLeast(0L)
                        runCatching {
                            authRepository.syncPlaybackHistory(
                                PlaybackHistoryPayload(
                                    animeId = anime.animeId,
                                    animeTitle = anime.animeName,
                                    poster = anime.coverUrl,
                                    episodeId = ep.episodeId,
                                    episodeLabel = ep.episode,
                                    episodeIndex = _playIndex.value.coerceAtLeast(0),
                                    episodeCount = _playList.size.coerceAtLeast(1),
                                    currentTimeSeconds = currentPlayPosition.coerceAtLeast(0L) / 1000.0,
                                    durationSeconds = duration / 1000.0,
                                    completed = duration > 0L &&
                                        currentPlayPosition >= (duration - 15_000L).coerceAtLeast(0L),
                                    sourceTypeId = anime.sourceId,
                                    playSessionId = playSessionId
                                )
                            )
                        }
                        lastRemoteSyncAt = now
                    }
                    delay(5000L)
                } ?: delay(2000L)
            }
        }
    }

    fun stopSaveHistory() {
        _saveHistoryJob?.cancel()
        _saveHistoryJob = null
    }

    fun playNextEpisodeIfExists() {
        val nextIndex = findNextEpisodeIndex()
        if (nextIndex == null || _videoUrl.value !is Resource.Success) return
        playEpisodeOfIndex(nextIndex)
    }

    fun playPreviousEpisodeIfExists() {
        val prevIndex = when {
            _playList.isEmpty() -> null
            _playIndex.value > 0 -> _playIndex.value - 1
            else -> null
        }
        if (prevIndex != null) {
            playEpisodeOfIndex(prevIndex)
        }
    }

    fun playNextEpisodeAdjacent() {
        val nextIndex = when {
            _playList.isEmpty() -> null
            _playIndex.value >= 0 && _playIndex.value + 1 < _playList.size -> _playIndex.value + 1
            else -> null
        }
        if (nextIndex != null) {
            playEpisodeOfIndex(nextIndex)
        }
    }

    private fun findNextEpisodeIndex(): Int? {
        if (_playList.size < 2) {
            return null
        }
        val idx = _playIndex.value
        if (idx < 0) {
            return null
        }
        val episode = _playList[idx]
        val num = extractNumberFromText(episode.episode)
        if (num != null) {
            if (idx + 1 < _playList.size) {
                val nextNum = extractNumberFromText(_playList[idx + 1].episode)
                if (nextNum != null && nextNum > num) {
                    return idx + 1
                }
            }
            if (idx > 0) {
                val prevNum = extractNumberFromText(_playList[idx - 1].episode)
                if (prevNum != null && prevNum > num) {
                    return idx - 1
                }
            }
        }
        return null
    }

    private fun extractNumberFromText(text: String): Int? {
        return Pattern.compile("\\d+").matcher(text)
            .takeIf { it.find() }
            ?.run {
                group().toInt()
            }
    }

    fun onPlayPositionChange(currentPosition: Long, duration: Long) {
        this.currentPlayPosition = currentPosition
        this.videoDuration = duration
    }

    fun retryLoadEpisode() {
        fetchVideoUrl(playList[_playIndex.value])
    }

    companion object {
        private const val REMOTE_SYNC_INTERVAL_MS = 15_000L
    }
}
