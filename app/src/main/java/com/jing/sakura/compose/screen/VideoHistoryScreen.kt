@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)

package com.jing.sakura.compose.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.jing.sakura.R
import com.jing.sakura.auth.TvHistoryItem
import com.jing.sakura.compose.common.AulamaActionButton
import com.jing.sakura.compose.common.AulamaPageHeader
import com.jing.sakura.compose.common.AulamaSectionHeader
import com.jing.sakura.compose.common.AulamaTvColors
import com.jing.sakura.compose.common.ErrorTip
import com.jing.sakura.compose.common.Loading
import com.jing.sakura.compose.common.VideoCard
import com.jing.sakura.compose.common.aulamaTvBackground
import com.jing.sakura.data.AnimeData
import com.jing.sakura.detail.DetailActivity
import com.jing.sakura.extend.secondsToMinuteAndSecondText
import com.jing.sakura.history.HistoryViewModel
import com.jing.sakura.room.VideoHistoryEntity

@Composable
fun VideoHistoryScreen(viewModel: HistoryViewModel) {
    val library by viewModel.library.collectAsState()
    val localHistory = viewModel.pager.collectAsLazyPagingItems()
    val localHistoryItems = localHistory.itemSnapshotList.items
    val localHistoryByAnime = remember(localHistoryItems) {
        localHistoryItems.associateBy {
            historyKey(animeId = it.animeId, sourceId = it.sourceId)
        }
    }
    val historyByAnime = remember(library.historyItems, localHistoryByAnime) {
        val remoteHistory = library.historyItems.associate { item ->
            val history = item.toVideoHistoryEntity()
            historyKey(history.animeId, history.sourceId) to history
        }
        (remoteHistory.keys + localHistoryByAnime.keys).associateWith { key ->
            listOfNotNull(remoteHistory[key], localHistoryByAnime[key])
                .maxBy(VideoHistoryEntity::updateTime)
        }
    }
    val loading by viewModel.libraryLoading.collectAsState()
    val error by viewModel.libraryError.collectAsState()
    val context = LocalContext.current
    val firstFocusRequester = remember { FocusRequester() }
    val refreshFocusRequester = remember { FocusRequester() }
    val hasContent = library.continueWatching.isNotEmpty() || library.favorites.isNotEmpty()

    if (loading && !hasContent) {
        Loading(text = "同步片庫")
        return
    }
    if (error != null && !hasContent) {
        ErrorTip(message = error ?: "未能同步片庫", retry = viewModel::refreshLibrary)
        return
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .aulamaTvBackground()
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 42.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            item(key = "library-header") {
                AulamaPageHeader(
                    title = "我的片庫",
                    subtitle = "雲端觀看紀錄與收藏"
                ) {
                    AulamaActionButton(
                        label = "重新整理",
                        modifier = if (hasContent) {
                            Modifier
                        } else {
                            Modifier.focusRequester(refreshFocusRequester)
                        },
                        onClick = viewModel::refreshLibrary
                    )
                }
            }

            item(key = "continue-watching") {
                LibraryAnimeRow(
                    title = "繼續觀看",
                    videos = library.continueWatching,
                    firstFocusRequester = firstFocusRequester.takeIf {
                        library.continueWatching.isNotEmpty()
                    },
                    onRefresh = viewModel::refreshLibrary,
                    onOpen = { video ->
                        DetailActivity.startActivity(context, video.id, video.sourceId)
                    },
                    historyByAnime = historyByAnime
                )
            }

            item(key = "favorites") {
                LibraryAnimeRow(
                    title = "我的收藏",
                    videos = library.favorites,
                    firstFocusRequester = firstFocusRequester.takeIf {
                        library.continueWatching.isEmpty() && library.favorites.isNotEmpty()
                    },
                    onRefresh = viewModel::refreshLibrary,
                    onOpen = { video ->
                        DetailActivity.startActivity(context, video.id, video.sourceId)
                    }
                )
            }
        }
    }

    LaunchedEffect(library.continueWatching, library.favorites) {
        val requester = if (hasContent) firstFocusRequester else refreshFocusRequester
        runCatching { requester.requestFocus() }
    }
}

@Composable
private fun LibraryAnimeRow(
    title: String,
    videos: List<AnimeData>,
    firstFocusRequester: FocusRequester?,
    onRefresh: () -> Unit,
    onOpen: (AnimeData) -> Unit,
    historyByAnime: Map<String, VideoHistoryEntity> = emptyMap()
) {
    AulamaSectionHeader(title = title, count = videos.size)
    if (videos.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(84.dp)
                .padding(horizontal = 42.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Text(
                text = "暫時未有內容",
                style = MaterialTheme.typography.bodyLarge,
                color = AulamaTvColors.TextSecondary
            )
        }
        return
    }

    val cardWidth = dimensionResource(id = R.dimen.history_poster_width)
    val cardHeight = dimensionResource(id = R.dimen.history_poster_height)
    LazyRow(
        // VideoCard 會在 focus 時放大並投射光暈；保留足夠間距避免相鄰卡片重疊。
        contentPadding = PaddingValues(horizontal = 42.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        items(count = videos.size, key = { "${videos[it].sourceId}:${videos[it].id}" }) { index ->
            val video = videos[index]
            val history = historyByAnime[historyKey(video.id, video.sourceId)]
            Column(
                modifier = Modifier.width(cardWidth),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                VideoCard(
                    modifier = Modifier
                        .size(width = cardWidth, height = cardHeight)
                        .run {
                            if (index == 0 && firstFocusRequester != null) {
                                focusRequester(firstFocusRequester)
                            } else this
                        },
                    imageUrl = video.imageUrl,
                    title = video.title,
                    subTitle = history?.lastEpisodeName.orEmpty().ifBlank { video.currentEpisode },
                    onKeyEvent = { event ->
                        if (event.key == Key.Menu && event.type == KeyEventType.KeyUp) {
                            onRefresh()
                            true
                        } else false
                    },
                    onClick = { onOpen(video) }
                )
                if (history != null) {
                    PlaybackProgress(history)
                }
            }
        }
    }
}

@Composable
private fun PlaybackProgress(history: VideoHistoryEntity) {
    val watchedSeconds = (history.lastPlayTime / 1000L).coerceAtLeast(0L)
    val episode = history.lastEpisodeName.trim()
    val progress = if (history.videoDuration > 0L) {
        (history.lastPlayTime.toFloat() / history.videoDuration.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    val progressText = buildList {
        if (episode.isNotEmpty()) add("看到 $episode")
        add("已看 ${watchedSeconds.secondsToMinuteAndSecondText()}")
    }.joinToString("  ·  ")

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(32.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = progressText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.labelMedium.copy(
                fontSize = 12.sp,
                lineHeight = 15.sp
            ),
            color = AulamaTvColors.TextSecondary
        )
        if (history.videoDuration > 0L) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(AulamaTvColors.Outline.copy(alpha = 0.7f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress)
                        .fillMaxHeight()
                        .background(AulamaTvColors.Cyan)
                )
            }
        }
    }
}

private fun historyKey(animeId: String, sourceId: String): String = "$sourceId:$animeId"

private fun TvHistoryItem.toVideoHistoryEntity(): VideoHistoryEntity = VideoHistoryEntity(
    animeId = animeId.ifBlank { anime.id },
    animeName = anime.title,
    episodeId = episodeId.ifBlank { "cloud:$episodeIndex" },
    lastEpisodeName = episodeLabel.ifBlank { anime.currentEpisode },
    updateTime = updatedAtEpochMs.coerceAtLeast(1L),
    lastPlayTime = currentTimeSeconds.toPositionMs(),
    videoDuration = durationSeconds.toPositionMs(),
    coverUrl = anime.imageUrl,
    sourceId = sourceTypeId.ifBlank { anime.sourceId }
)

private fun Double.toPositionMs(): Long =
    takeIf { it.isFinite() && it > 0.0 }
        ?.times(1_000.0)
        ?.coerceAtMost(Long.MAX_VALUE.toDouble())
        ?.toLong()
        ?: 0L
