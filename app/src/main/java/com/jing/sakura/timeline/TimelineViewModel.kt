package com.jing.sakura.timeline

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.content.Context
import com.jing.sakura.SakuraApplication
import com.jing.sakura.data.AnimeData
import com.jing.sakura.data.Resource
import com.jing.sakura.data.UpdateTimeLine
import com.jing.sakura.repo.CycaniSource
import com.jing.sakura.repo.WebPageRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

class TimelineViewModel(
    private val repository: WebPageRepository,
    val sourceId:String
) : ViewModel() {

    private val _timelines: MutableStateFlow<Resource<UpdateTimeLine>> =
        MutableStateFlow(Resource.Loading)
    val timelines: StateFlow<Resource<UpdateTimeLine>>
        get() = _timelines
    private val _synopses = MutableStateFlow<Map<String, String>>(emptyMap())
    val synopses: StateFlow<Map<String, String>>
        get() = _synopses

    private val synopsisJobs = mutableMapOf<String, Job>()
    private val synopsisSemaphore = Semaphore(4)
    private var synopsisPrefetchJob: Job? = null
    private val synopsisCache = TimelineSynopsisCache(
        SakuraApplication.context.getSharedPreferences(
            "timeline_synopsis_cache",
            Context.MODE_PRIVATE
        )
    )

    init {
        loadData()
    }

    fun loadData() {
        synopsisPrefetchJob?.cancel()
        viewModelScope.launch(Dispatchers.IO) {
            try {
                _timelines.emit(Resource.Loading)
                val data = repository.fetchUpdateTimeline(sourceId)
                _timelines.emit(Resource.Success(data))
                synopsisPrefetchJob = viewModelScope.launch(Dispatchers.IO) {
                    delay(360)
                    data.timeline
                        .flatMap { it.second }
                        .distinctBy { it.id }
                        .forEach { anime ->
                            requestSynopsis(anime)
                            delay(24)
                        }
                }
            } catch (ex: Exception) {
                if (ex is CancellationException) {
                    throw ex
                }
                _timelines.emit(Resource.Error("載入失敗：${ex.message}"))
            }
        }
    }

    fun loadSynopsis(anime: AnimeData) {
        requestSynopsis(anime)
    }

    private fun requestSynopsis(anime: AnimeData) {
        if (anime.id.isBlank() || !_synopses.value[anime.id].isNullOrBlank()) return
        synopsisCache.get(anime.id)?.let { cached ->
            _synopses.update { current -> current + (anime.id to cached) }
            return
        }
        val source = repository.requireAnimationSource(sourceId) as? CycaniSource ?: return
        val job = synchronized(synopsisJobs) {
            if (synopsisJobs[anime.id]?.isActive == true) return
            viewModelScope.launch(Dispatchers.IO, start = CoroutineStart.LAZY) {
                try {
                    val synopsis = synopsisSemaphore.withPermit {
                        source.fetchTimelineSynopsis(anime.id)
                    }.trim()
                    if (synopsis.isNotBlank()) {
                        synopsisCache.put(anime.id, synopsis)
                        _synopses.update { current -> current + (anime.id to synopsis) }
                    }
                } finally {
                    synchronized(synopsisJobs) {
                        synopsisJobs.remove(anime.id)
                    }
                }
            }.also { synopsisJobs[anime.id] = it }
        }
        job.start()
    }
}

private class TimelineSynopsisCache(
    private val preferences: android.content.SharedPreferences
) {
    fun get(animeId: String): String? = preferences.getString(key(animeId), null)
        ?.takeIf(String::isNotBlank)

    @Synchronized
    fun put(animeId: String, synopsis: String) {
        val storageKey = key(animeId)
        val keys = preferences.getString(INDEX_KEY, "")
            .orEmpty()
            .lineSequence()
            .filter(String::isNotBlank)
            .filterNot { it == storageKey }
            .toMutableList()
            .apply { add(storageKey) }
        val expired = keys.take((keys.size - MAX_ENTRIES).coerceAtLeast(0))
        preferences.edit().apply {
            expired.forEach(::remove)
            putString(storageKey, synopsis)
            putString(INDEX_KEY, keys.takeLast(MAX_ENTRIES).joinToString("\n"))
        }.apply()
    }

    private fun key(animeId: String): String = "synopsis_$animeId"

    private companion object {
        const val INDEX_KEY = "cached_keys"
        const val MAX_ENTRIES = 240
    }
}
