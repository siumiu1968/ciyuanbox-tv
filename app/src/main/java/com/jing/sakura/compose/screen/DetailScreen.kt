@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.jing.sakura.compose.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.tv.foundation.ExperimentalTvFoundationApi
import androidx.tv.foundation.PivotOffsets
import androidx.tv.foundation.lazy.list.TvLazyColumn
import androidx.tv.foundation.lazy.list.TvLazyListState
import androidx.tv.foundation.lazy.list.TvLazyRow
import androidx.tv.foundation.lazy.list.rememberTvLazyListState
import androidx.tv.material3.Border
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ClickableSurfaceScale
import androidx.tv.material3.CompactCard
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.ProvideTextStyle
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.jing.sakura.R
import com.jing.sakura.compose.common.ErrorTip
import com.jing.sakura.compose.common.FocusGroup
import com.jing.sakura.compose.common.Loading
import com.jing.sakura.compose.common.AulamaCardShape
import com.jing.sakura.compose.common.AulamaFocusScale
import com.jing.sakura.compose.common.AulamaSectionHeader
import com.jing.sakura.compose.common.AulamaTvColors
import com.jing.sakura.compose.common.rememberDpadRepeatGate
import com.jing.sakura.compose.common.rememberPosterImageRequest
import com.jing.sakura.compose.common.UpAndDownFocusProperties
import com.jing.sakura.compose.common.Value
import com.jing.sakura.compose.common.VideoCard
import com.jing.sakura.compose.common.applyUpAndDown
import com.jing.sakura.compose.common.toDisplayLineName
import com.jing.sakura.data.AnimeData
import com.jing.sakura.data.AnimeDetailPageData
import com.jing.sakura.data.AnimePlayList
import com.jing.sakura.data.AnimePlayListEpisode
import com.jing.sakura.data.Resource
import com.jing.sakura.detail.DetailActivity
import com.jing.sakura.detail.DetailPageViewModel
import com.jing.sakura.extend.secondsToMinuteAndSecondText
import com.jing.sakura.player.NavigateToPlayerArg
import com.jing.sakura.player.PlaybackActivity
import com.jing.sakura.room.VideoHistoryEntity
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.foundation.shape.RoundedCornerShape

@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalTvFoundationApi::class)
@Composable
fun DetailScreen(viewModel: DetailPageViewModel) {
    val videoDetailResource = viewModel.detailPageData.collectAsState().value
    if (videoDetailResource == Resource.Loading) {
        Loading()
        return
    }
    if (videoDetailResource is Resource.Error) {
        ErrorTip(message = videoDetailResource.message) {
            viewModel.loadData()
        }
        return
    }
    val resumeFocusSignal = remember {
        Value(0)
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) {
                viewModel.fetchHistory()
            }
            if (event == Lifecycle.Event.ON_RESUME) {
                resumeFocusSignal.value += 1
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
    val videoDetail = (videoDetailResource as Resource.Success).data
    val context = LocalContext.current
    var reverseEpisode by remember {
        mutableStateOf(false)
    }

    val currentFocusedEpisodeId = remember {
        Value("")
    }

    val videoHistory = viewModel.latestProgress.collectAsState().value.getOrNull()

    val nextPlayListId = remember {
        Value(0)
    }

    val playlists =
        remember(reverseEpisode, videoHistory?.episodeId) {
            if (reverseEpisode) {
                videoDetail.playLists.map {
                    PlayListWrapper(
                        id = nextPlayListId.value++,
                        playlist = it.copy(episodeList = it.episodeList.reversed())
                    )
                }
            } else {
                videoDetail.playLists.map {
                    PlayListWrapper(
                        id = nextPlayListId.value++,
                        playlist = it
                    )
                }
            }
        }

    val focusedEpisodeIndex = remember(videoHistory?.episodeId) {
        val episodeId = videoHistory?.episodeId
        var lastPlayPosition = 0 to 0
        if (episodeId != null) {
            out@ for ((playListIndex, playList) in playlists.withIndex()) {
                for ((episodeIndex, episode) in playList.playlist.episodeList.withIndex()) {
                    if (episodeId == episode.episodeId) {
                        lastPlayPosition = playListIndex to episodeIndex
                        break@out
                    }
                }
            }
        }
        Value(MutableList(videoDetail.playLists.size) { if (it == lastPlayPosition.first) lastPlayPosition.second else 0 })
    }

    // 切换正序/倒序排序时, 更新索引
    val changeFocusedEpisodeIndexForReversed = {
        videoDetail.playLists.forEachIndexed { index, _ ->
            focusedEpisodeIndex.value[index] = 0
        }
    }

    val focusRequesters = remember(playlists.size, videoDetail.otherAnimeList.isEmpty()) {
        DetailPageRowFocusRequesters(
            infoRow = FocusRequester(),
            playListReverseButton = if (playlists.isNotEmpty()) FocusRequester() else null,
            playListRows = List(playlists.size) { FocusRequester() },
            otherAnimeRow = if (videoDetail.otherAnimeList.isNotEmpty()) FocusRequester() else null
        )
    }

    val shouldFocusReverseButton = remember {
        Value(false)
    }

    // 进入播放页,返回后恢复焦点
    val restoreEpisodeFocusRequester = remember {
        FocusRequester()
    }
    val restoreEpisodePosition = remember {
        Value(-1 to -1)
    }

    val initialFocusSet = remember {
        Value(false)
    }

    TvLazyColumn(
        modifier = Modifier.fillMaxSize(), content = {
            item {
                VideoInfoRow(
                    videoDetail = videoDetail,
                    modifier = Modifier
                        .focusRequester(focusRequesters.infoRow),
                    upAndDownFocusProperties = UpAndDownFocusProperties(
                        down = focusRequesters.playListReverseButton
                            ?: focusRequesters.otherAnimeRow
                    ),
                    playHistory = videoHistory
                ) {
                    viewModel.loadData()
                }
                LaunchedEffect(Unit) {
                    if (!initialFocusSet.value) {
                        initialFocusSet.value = true
                        runCatching { focusRequesters.infoRow.requestFocus() }.onFailure { it.printStackTrace() }
                    }
                }
            }
            items(
                count = playlists.size,
                key = { playlists[it].id }
            ) { playlistIndex ->
                val playlist = playlists[playlistIndex].playlist
                val initiallyFocusedIndex = focusedEpisodeIndex.value[playlistIndex]
                val listState =
                    rememberTvLazyListState(initialFirstVisibleItemIndex = initiallyFocusedIndex)
                PlayListRow(
                    episodes = playlist.episodeList,
                    modifier = Modifier
                        .focusRequester(focusRequesters.playListRows[playlistIndex]),
                    initiallyFocusedIndex = initiallyFocusedIndex,
                    listState = listState,
                    onEpisodeFocused = { epIndex, ep ->
                        focusedEpisodeIndex.value[playlistIndex] = epIndex
                        currentFocusedEpisodeId.value = ep.episodeId
                    },
                    restoreFocusEpIndex = if (restoreEpisodePosition.value.first == playlistIndex) restoreEpisodePosition.value.second else -1,
                    restoreFocusRequester = restoreEpisodeFocusRequester,
                    upAndDownFocusProperties = UpAndDownFocusProperties(
                        up = focusRequesters.playListRows.getOrNull(playlistIndex - 1)
                            ?: focusRequesters.playListReverseButton, // 上一个播放列表 或者正序倒序按钮
                        down = focusRequesters.playListRows.getOrNull(playlistIndex + 1)
                            ?: focusRequesters.otherAnimeRow // 下一个播放列表或者推荐视频
                    ),
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = playlist.name.toDisplayLineName(),
                                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                                color = Color(0xFFF4F6FF)
                            )
                            Text(
                                text = "${playlist.episodeList.size} 集",
                                style = MaterialTheme.typography.labelMedium,
                                color = AulamaTvColors.TextSecondary
                            )
                            if (playlistIndex == 0) {
                                Surface(
                                    onClick = {
                                        changeFocusedEpisodeIndexForReversed()
                                        shouldFocusReverseButton.value = true
                                        reverseEpisode = !reverseEpisode
                                    },
                                    scale = ClickableSurfaceScale.None,
                                    colors = ClickableSurfaceDefaults.colors(
                                        focusedContainerColor = MaterialTheme.colorScheme.surface
                                    ),
                                    shape = ClickableSurfaceDefaults.shape(shape = MaterialTheme.shapes.small),
                                    border = ClickableSurfaceDefaults.border(
                                        focusedBorder = Border(
                                            BorderStroke(
                                                2.dp, MaterialTheme.colorScheme.border
                                            )
                                        )
                                    ),
                                    modifier = Modifier
                                        .focusRequester(focusRequesters.playListReverseButton!!)
                                        .focusProperties {
                                            up = focusRequesters.infoRow
                                            down = focusRequesters.playListRows[0]
                                        }
                                ) {
                                    Text(
                                        text = if (reverseEpisode) {
                                            stringResource(R.string.button_reverse_order)
                                        } else {
                                            stringResource(R.string.button_positive_order)
                                        },
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                    )
                                }
                            }
                            LaunchedEffect(shouldFocusReverseButton) {
                                if (shouldFocusReverseButton.value) {
                                    shouldFocusReverseButton.value = false
                                    runCatching { focusRequesters.playListReverseButton?.requestFocus() }
                                }
                            }
                        }
                    }) { epIndex, _ ->
                    restoreEpisodePosition.value = playlistIndex to epIndex
                    PlaybackActivity.startActivity(
                        context,
                        NavigateToPlayerArg(
                            animeName = videoDetail.animeName,
                            animeId = videoDetail.animeId,
                            coverUrl = videoDetail.imageUrl,
                            playIndex = epIndex,
                            playlist = playlist.episodeList,
                            sourceId = viewModel.sourceId
                        )
                    )
                }
            }
            item {
                if (videoDetail.otherAnimeList.isNotEmpty()) {
                    RelativeVideoRow(
                        videoDetail.otherAnimeList,
                        viewModel.sourceId,
                        Modifier
                            .focusRequester(focusRequesters.otherAnimeRow!!),
                        upAndDownFocusProperties = UpAndDownFocusProperties(
                            up = focusRequesters.playListRows.lastOrNull()
                                ?: focusRequesters.infoRow
                        )
                    )
                }
            }
        }, verticalArrangement = Arrangement.spacedBy(10.dp)
    )

    LaunchedEffect(resumeFocusSignal.value, restoreEpisodePosition.value) {
        if (resumeFocusSignal.value > 0 && restoreEpisodePosition.value.first >= 0 && restoreEpisodePosition.value.second >= 0) {
            delay(140)
            runCatching {
                restoreEpisodeFocusRequester.requestFocus()
            }
            restoreEpisodePosition.value = -1 to -1
        }
    }
}

@OptIn(ExperimentalTvFoundationApi::class)
@Composable
fun RelativeVideoRow(
    videos: List<AnimeData>,
    sourceId: String,
    modifier: Modifier,
    upAndDownFocusProperties: UpAndDownFocusProperties = UpAndDownFocusProperties.DEFAULT
) {
    if (videos.isEmpty()) {
        return
    }
    val context = LocalContext.current
    FocusGroup(modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp)
        ) {
            AulamaSectionHeader(
                title = stringResource(id = R.string.related_videos),
                count = videos.size,
                accent = AulamaTvColors.Pink
            )
            Spacer(modifier = Modifier.height(12.dp))
            TvLazyRow(
                content = {
                    items(count = videos.size, key = { videos[it].id }) { videoIndex ->
                        val video = videos[videoIndex]
                        VideoCard(
                            modifier = Modifier
                                .size(
                                    dimensionResource(id = R.dimen.poster_width),
                                    dimensionResource(id = R.dimen.poster_height)
                                )
                                .focusProperties { applyUpAndDown(upAndDownFocusProperties) }
                                .run {
                                    if (videoIndex == 0) initiallyFocused() else restorableFocus()
                                },
                            imageUrl = video.imageUrl,
                            title = video.title,
                            subTitle = video.currentEpisode,
                            focusScale = 1.06f
                        ) {
                            DetailActivity.startActivity(context, video.id, sourceId)
                        }
                    }
                },
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            )
        }
    }
}

@OptIn(ExperimentalTvFoundationApi::class)
@Composable
fun PlayListRow(
    episodes: List<AnimePlayListEpisode>,
    modifier: Modifier,
    title: @Composable () -> Unit,
    listState: TvLazyListState,
    restoreFocusRequester: FocusRequester,
    restoreFocusEpIndex: Int,
    upAndDownFocusProperties: UpAndDownFocusProperties = UpAndDownFocusProperties.DEFAULT,
    initiallyFocusedIndex: Int = 0,
    onEpisodeFocused: (Int, AnimePlayListEpisode) -> Unit,
    onEpisodeClick: (Int, AnimePlayListEpisode) -> Unit,
) {
    val consumeRapidDpad = rememberDpadRepeatGate()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 10.dp)
    ) {
        title()
        Spacer(modifier = Modifier.height(12.dp))
        FocusGroup(modifier) {
            TvLazyRow(
                state = listState,
                pivotOffsets = PivotOffsets(0f),
                modifier = Modifier.onPreviewKeyEvent {
                    if (consumeRapidDpad(it)) return@onPreviewKeyEvent true
                    false
                },
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                content = {
                    items(count = episodes.size, key = { episodes[it].episodeId }) { epIndex ->
                        val ep = episodes[epIndex]
                        val episodeModifier =
                            if (epIndex == initiallyFocusedIndex) {
                                Modifier.initiallyFocused()
                            } else Modifier.restorableFocus()
                        VideoEpisode(
                            modifier = episodeModifier
                                .run {
                                    if (restoreFocusEpIndex == epIndex) {
                                        focusRequester(restoreFocusRequester)
                                    } else {
                                        this
                                    }
                                }
                                .focusProperties {
                                    applyUpAndDown(upAndDownFocusProperties)
                                }
                                .onFocusChanged {
                                    if (it.hasFocus || it.isFocused) {
                                        onEpisodeFocused(epIndex, ep)
                                    }
                                },
                            tagName = ep.episode
                        ) {
                            onEpisodeClick(epIndex, ep)
                        }
                    }
                },
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 2.dp)
            )
        }
    }
}


@OptIn(
    ExperimentalTvMaterial3Api::class, ExperimentalFoundationApi::class,
    ExperimentalTvFoundationApi::class
)
@Composable
fun VideoInfoRow(
    videoDetail: AnimeDetailPageData,
    modifier: Modifier,
    upAndDownFocusProperties: UpAndDownFocusProperties = UpAndDownFocusProperties.DEFAULT,
    playHistory: VideoHistoryEntity? = null,
    onCoverClick: () -> Unit = {}
) {
    var showDescDialog by remember {
        mutableStateOf(false)
    }
    val infoRows = remember(videoDetail.infoList) {
        videoDetail.infoList
            .map(String::trim)
            .filter { info ->
                info.isNotBlank() && !info.matches(Regex(".*[：:]\\s*$"))
            }
            .chunked(2)
    }

    FocusGroup(modifier) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(AulamaTvColors.Surface)
                .padding(20.dp)
                .heightIn(min = dimensionResource(id = R.dimen.poster_height) * 1.3f + 16.dp)
        ) {
            CompactCard(
                onClick = {
                    onCoverClick()
                },
                image = {
                    AsyncImage(
                        model = rememberPosterImageRequest(
                            imageUrl = videoDetail.imageUrl,
                            widthPx = 560,
                            heightPx = 760
                        ),
                        contentDescription = videoDetail.animeName,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                },
                title = {},
                scale = CardDefaults.scale(focusedScale = AulamaFocusScale),
                shape = CardDefaults.shape(shape = AulamaCardShape),
                modifier = Modifier
                    .initiallyFocused()
                    .size(
                        dimensionResource(id = R.dimen.poster_width) * 1.3f,
                        dimensionResource(id = R.dimen.poster_height) * 1.3f
                    )
                    .border(1.dp, AulamaTvColors.Outline, AulamaCardShape)
                    .focusProperties { applyUpAndDown(upAndDownFocusProperties) }
            )
            Spacer(modifier = Modifier.width(15.dp))
            Column(
                modifier = Modifier
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Text(
                        text = videoDetail.animeName,
                        style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        color = AulamaTvColors.TextPrimary,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Image(
                        painter = painterResource(R.drawable.aulama_anime_wordmark),
                        contentDescription = stringResource(R.string.app_name),
                        modifier = Modifier.size(width = 150.dp, height = 62.dp)
                    )
                }
                if (playHistory != null) {
                    Box(
                        modifier = Modifier
                            .clip(AulamaCardShape)
                            .background(AulamaTvColors.Cyan.copy(alpha = 0.14f))
                            .border(
                                width = 1.dp,
                                color = AulamaTvColors.Cyan.copy(alpha = 0.4f),
                                shape = AulamaCardShape
                            )
                            .padding(horizontal = 12.dp, vertical = 7.dp)
                    ) {
                        Text(
                            text = stringResource(
                                R.string.last_played_template,
                                playHistory.lastEpisodeName,
                                (playHistory.lastPlayTime / 1000).secondsToMinuteAndSecondText(),
                                (playHistory.videoDuration / 1000).secondsToMinuteAndSecondText()
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = AulamaTvColors.TextPrimary
                        )
                    }
                }

                ProvideTextStyle(value = MaterialTheme.typography.bodySmall) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        infoRows.forEach { row ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                row.forEach { info ->
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(AulamaCardShape)
                                            .background(AulamaTvColors.SurfaceRaised)
                                            .border(1.dp, AulamaTvColors.Outline, AulamaCardShape)
                                            .padding(horizontal = 12.dp, vertical = 10.dp)
                                    ) {
                                        Text(
                                            text = info,
                                            maxLines = 3,
                                            overflow = TextOverflow.Ellipsis,
                                            color = AulamaTvColors.TextSecondary
                                        )
                                    }
                                }
                                if (row.size == 1) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                        if (videoDetail.description.isNotEmpty()) {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .focusProperties {
                                        applyUpAndDown(
                                            upAndDownFocusProperties
                                        )
                                    }
                                    .restorableFocus(),
                                onClick = { showDescDialog = true },
                                scale = ClickableSurfaceDefaults.scale(focusedScale = AulamaFocusScale),
                                colors = ClickableSurfaceDefaults.colors(
                                    containerColor = AulamaTvColors.SurfaceRaised,
                                    focusedContainerColor = Color(0xFF173A40)
                                ),
                                border = ClickableSurfaceDefaults.border(
                                    border = Border(
                                        BorderStroke(1.dp, AulamaTvColors.Outline),
                                        shape = AulamaCardShape
                                    ),
                                    focusedBorder = Border(
                                        BorderStroke(
                                            2.dp, MaterialTheme.colorScheme.border
                                        ),
                                        shape = AulamaCardShape
                                    )
                                ),
                                shape = ClickableSurfaceDefaults.shape(AulamaCardShape)
                            ) {
                                Column(
                                    modifier = Modifier.padding(
                                        horizontal = 12.dp, vertical = 12.dp
                                    )
                                ) {
                                    Text(
                                        text = stringResource(R.string.video_description),
                                        style = MaterialTheme.typography.labelLarge,
                                        color = AulamaTvColors.Cyan
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = videoDetail.description,
                                        maxLines = 3,
                                        overflow = TextOverflow.Ellipsis,
                                        color = Color.White
                                    )
                                }
                            }
                        }
                    }
                }

            }
        }

    }

    // 在Dialog中显示视频简介
    if (showDescDialog) {
        val scrollState = rememberScrollState()
        val coroutineScope = rememberCoroutineScope()
        val longDescFocusRequester = remember {
            FocusRequester()
        }
        AlertDialog(onDismissRequest = { showDescDialog = false },
            confirmButton = {},
            properties = DialogProperties(usePlatformDefaultWidth = false),
            modifier = Modifier.fillMaxWidth(0.6f),
            shape = AulamaCardShape,
            containerColor = AulamaTvColors.Surface,
            title = {
                Text(
                    text = stringResource(R.string.video_description),
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.padding(bottom = 10.dp)
                )
            },
            text = {
                Text(text = videoDetail.description,
                    modifier = Modifier
                        .verticalScroll(scrollState)
                        .focusRequester(longDescFocusRequester)
                        .focusable()
                        .onPreviewKeyEvent {
                            val step = 70f
                            when (it.key) {
                                Key.DirectionUp -> {
                                    if (it.type == KeyEventType.KeyDown) {
                                        coroutineScope.launch {
                                            scrollState.animateScrollBy(-step)
                                        }
                                        true
                                    } else {
                                        false
                                    }
                                }

                                Key.DirectionDown -> {
                                    if (it.type == KeyEventType.KeyDown) {
                                        coroutineScope.launch {
                                            scrollState.animateScrollBy(step)
                                        }
                                        true
                                    } else {
                                        false
                                    }

                                }

                                else -> false
                            }

                        })
                LaunchedEffect(Unit) {
                    longDescFocusRequester.requestFocus()
                }
            }

        )

    }

}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun VideoEpisode(tagName: String, modifier: Modifier = Modifier, onClick: () -> Unit = {}) {
    var focused by remember {
        mutableStateOf(false)
    }
    Surface(
        modifier = modifier
            .onFocusChanged {
                focused = it.isFocused || it.hasFocus
            }
            .run {
                if (focused) {
                    border(
                        2.dp,
                        AulamaTvColors.FocusBorder,
                        AulamaCardShape
                    )
                } else {
                    border(1.dp, AulamaTvColors.Outline, AulamaCardShape)
                }
            },
        colors = ClickableSurfaceDefaults.colors(
            containerColor = AulamaTvColors.SurfaceRaised,
            focusedContainerColor = Color(0xFF1D555D),
            pressedContainerColor = Color(0xFF174249)
        ),
        shape = ClickableSurfaceDefaults.shape(shape = AulamaCardShape),
        border = ClickableSurfaceDefaults.border(),
        scale = ClickableSurfaceDefaults.scale(focusedScale = AulamaFocusScale),
        onClick = onClick
    ) {
        Text(
            modifier = Modifier
                .widthIn(min = 92.dp)
                .padding(horizontal = 14.dp, vertical = 8.dp),
            text = tagName,
            color = Color.White,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
        )
    }
}

private data class PlayListWrapper(
    val id: Int,
    val playlist: AnimePlayList
)

private data class DetailPageRowFocusRequesters(
    val infoRow: FocusRequester,
    val playListReverseButton: FocusRequester?,
    val playListRows: List<FocusRequester>,
    val otherAnimeRow: FocusRequester?
)
