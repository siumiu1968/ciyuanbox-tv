package com.jing.sakura.home

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jing.sakura.SakuraApplication
import com.jing.sakura.auth.AulamaAuthRepository
import com.jing.sakura.data.AnimeData
import com.jing.sakura.data.HomePageData
import com.jing.sakura.data.NamedValue
import com.jing.sakura.data.Resource
import com.jing.sakura.repo.AnimationSource
import com.jing.sakura.repo.CycaniSource
import com.jing.sakura.repo.WebPageRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class HomeViewModel(
    private val repository: WebPageRepository,
    private val authRepository: AulamaAuthRepository
) : ViewModel() {

    private val _homePageData = MutableStateFlow<Resource<HomePageData>>(Resource.Loading)
    private val _recommendations = MutableStateFlow<List<AnimeData>>(emptyList())

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

    @Volatile
    var lastHomePageData: HomePageData? = null
        private set

    private var loadDataJob: Pair<String, Job>? = null

    init {
        loadData(false)
        viewModelScope.launch(Dispatchers.IO) {
            authRepository.session.collectLatest { session ->
                _recommendations.value = if (session == null) {
                    emptyList()
                } else {
                    runCatching { authRepository.fetchRecommendations() }
                        .onFailure { Log.e("recommendations", "載入個人化推薦失敗", it) }
                        .getOrDefault(emptyList())
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

}
