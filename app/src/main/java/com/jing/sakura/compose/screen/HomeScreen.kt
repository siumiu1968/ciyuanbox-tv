package com.jing.sakura.compose.screen

import androidx.annotation.ColorRes
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.ChangeCircle
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.foundation.ExperimentalTvFoundationApi
import androidx.tv.foundation.PivotOffsets
import androidx.tv.foundation.lazy.list.TvLazyColumn
import androidx.tv.foundation.lazy.list.TvLazyListScope
import androidx.tv.foundation.lazy.list.TvLazyListState
import androidx.tv.foundation.lazy.list.TvLazyRow
import androidx.tv.foundation.lazy.list.rememberTvLazyListState
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import com.jing.sakura.R
import com.jing.sakura.category.AnimeCategoryActivity
import com.jing.sakura.compose.common.ErrorTip
import com.jing.sakura.compose.common.FocusGroup
import com.jing.sakura.compose.common.Loading
import com.jing.sakura.compose.common.rememberDpadRepeatGate
import com.jing.sakura.compose.common.UpAndDownFocusProperties
import com.jing.sakura.compose.common.Value
import com.jing.sakura.compose.common.VideoCard
import com.jing.sakura.compose.common.applyUpAndDown
import com.jing.sakura.compose.common.getValue
import com.jing.sakura.compose.common.setValue
import com.jing.sakura.data.AnimeData
import com.jing.sakura.data.HomePageData
import com.jing.sakura.data.NamedValue
import com.jing.sakura.data.Resource
import com.jing.sakura.detail.DetailActivity
import com.jing.sakura.history.HistoryActivity
import com.jing.sakura.home.HomeViewModel
import com.jing.sakura.search.SearchActivity
import com.jing.sakura.timeline.UpdateTimelineActivity
import kotlinx.coroutines.launch
import java.util.Calendar

@Composable
fun HomeScreen(viewModel: HomeViewModel) {
    val context = LocalContext.current
    val currentSource = viewModel.currentSource.collectAsState().value
    val buttons = remember(currentSource) {
        listOf(
            HomeScreenButton(
                icon = Icons.Default.Search,
                backgroundColor = R.color.green400,
                display = currentSource.supportSearch(),
                label = R.string.button_search
            ) {
                SearchActivity.startActivity(context, viewModel.currentSourceId)
            },
            HomeScreenButton(
                icon = Icons.Default.History,
                backgroundColor = R.color.yellow500,
                label = R.string.playback_history
            ) {
                HistoryActivity.startActivity(context)
            },
            HomeScreenButton(
                icon = Icons.Default.CalendarMonth,
                backgroundColor = R.color.cyan500,
                display = currentSource.supportTimeline(),
                label = R.string.anime_update_timeline
            ) {
                UpdateTimelineActivity.startActivity(context, viewModel.currentSourceId)
            },
            HomeScreenButton(
                icon = Icons.Default.Category,
                backgroundColor = R.color.lime600,
                display = currentSource.supportSearchByCategory(),
                label = R.string.button_anime_category
            ) {
                AnimeCategoryActivity.startActivity(context, viewModel.currentSourceId)
            },
            HomeScreenButton(
                icon = Icons.Default.Refresh,
                backgroundColor = R.color.sky400,
                label = R.string.button_refresh
            ) {
                viewModel.loadData(false)
            },
        )
    }
    val homePageDataResource = viewModel.homePageData.collectAsState().value

    val displayData: HomePageData? = when (homePageDataResource) {
        is Resource.Success -> homePageDataResource.data
        is Resource.Loading -> viewModel.lastHomePageData
        is Resource.Error -> null
    }

    val allSeriesList = displayData?.seriesList ?: emptyList()
    val weeklySeries = remember(allSeriesList) {
        WEEKDAY_SECTION_NAMES.mapNotNull { name ->
            allSeriesList.firstOrNull { it.name == name }
        }
    }
    val normalSeries = remember(allSeriesList, weeklySeries) {
        if (weeklySeries.isEmpty()) {
            allSeriesList
        } else {
            allSeriesList.filterNot { item ->
                weeklySeries.any { it.name == item.name }
            }
        }
    }
    var selectedWeekdayIndex by remember(currentSource.sourceId) {
        mutableStateOf(0)
    }
    var weeklyFocusToken by remember(currentSource.sourceId) {
        mutableStateOf(0)
    }

    val renderedSectionCount = normalSeries.size + if (weeklySeries.isNotEmpty()) 1 else 0
    val focusRequesterRows = remember(renderedSectionCount) {
        HomeScreenFocusRows(
            topButtonRow = FocusRequester(),
            seriesRows = List(renderedSectionCount) { FocusRequester() }
        )
    }
    val coroutineScope = rememberCoroutineScope()
    var haveSetDefaultFocus by remember(currentSource.sourceId) {
        Value(false)
    }
    var lastFocusedRowPosition by remember(currentSource.sourceId) {
        Value(FocusedVideoPosition.DEFAULT)
    }

    val videoWidth = dimensionResource(id = R.dimen.poster_width)
    val videoHeight = dimensionResource(id = R.dimen.poster_height)
    val focusRestoreStateMap = remember(currentSource.sourceId) {
        mutableMapOf<String, FocusPositionRestoreState<String>>()
    }
    LaunchedEffect(normalSeries) {
        val newMap = normalSeries.associate {
            it.name to (focusRestoreStateMap[it.name] ?: FocusPositionRestoreState())
        }
        focusRestoreStateMap.clear()
        focusRestoreStateMap.putAll(newMap)
    }
    val rowStateMap = remember(currentSource.sourceId) {
        mutableMapOf<String, TvLazyListState>()
    }

    LaunchedEffect(currentSource.sourceId, weeklySeries) {
        if (weeklySeries.isNotEmpty() && selectedWeekdayIndex !in weeklySeries.indices) {
            selectedWeekdayIndex = defaultWeekdayIndex(weeklySeries)
        }
    }

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        Image(
            painter = painterResource(id = R.drawable.home_backdrop),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    alpha = 0.38f
                    scaleX = 1.08f
                    scaleY = 1.08f
                }
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(
                        listOf(
                            Color(0xD9090C16),
                            Color(0xC4102444),
                            Color(0xB83A1750),
                            Color(0xB0124952),
                            Color(0xD9080C18)
                        )
                    )
                )
        )
        val columnState = rememberTvLazyListState()
        TvLazyColumn(
            state = columnState,
            modifier = Modifier.fillMaxWidth(),
            content = {
                item {
                    HomeTitleRow(
                        buttonList = buttons,
                        title = stringResource(id = R.string.app_name),
                        subtitle = "",
                        focusRequester = focusRequesterRows.topButtonRow,
                        upAndDownFocusProperties = UpAndDownFocusProperties(
                            down = focusRequesterRows.seriesRows.firstOrNull()
                        ),
                        iconSize = 50.dp,
                        iconFocusedScale = 1.25f
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    LaunchedEffect(renderedSectionCount) {
                        if (renderedSectionCount == 0) {
                            focusRequesterRows.topButtonRow.requestFocus()
                        }
                    }
                }
                if (weeklySeries.isNotEmpty()) {
                    item(key = currentSource.sourceId to "weekly-home") {
                        WeeklyUpdatesSection(
                            weekGroups = weeklySeries,
                            selectedIndex = selectedWeekdayIndex,
                            onSelectedIndexChanged = { selectedWeekdayIndex = it },
                            focusSelectedTabToken = weeklyFocusToken,
                            sourceId = currentSource.sourceId,
                            videoWidth = videoWidth,
                            videoHeight = videoHeight,
                            focusRequester = focusRequesterRows.seriesRows.first(),
                            upAndDownFocusProperties = UpAndDownFocusProperties(
                                up = focusRequesterRows.topButtonRow,
                                down = if (normalSeries.isNotEmpty()) {
                                    focusRequesterRows.seriesRows.getOrNull(1)
                                } else {
                                    null
                                }
                            ),
                            onBackPressed = {
                                if (columnState.firstVisibleItemIndex <= 1) {
                                    false
                                } else {
                                    coroutineScope.launch {
                                        columnState.scrollToItem(1)
                                        focusRequesterRows.seriesRows.first().requestFocus()
                                    }
                                    true
                                }
                            },
                            onVideoFocused = { groupName, video ->
                                lastFocusedRowPosition =
                                    FocusedVideoPosition(groupName = groupName, videoId = video.id)
                            },
                            onRequestRefresh = { viewModel.loadData(false) }
                        ) {
                            DetailActivity.startActivity(
                                context,
                                it.id,
                                currentSource.sourceId
                            )
                        }
                    }
                }
                if (normalSeries.isNotEmpty()) {
                    videoRows(
                        videoSeries = normalSeries,
                        sourceId = currentSource.sourceId,
                        videoWidth = videoWidth,
                        videoHeight = videoHeight,
                        topFocusRequester = if (weeklySeries.isNotEmpty()) {
                            focusRequesterRows.seriesRows.first()
                        } else {
                            focusRequesterRows.topButtonRow
                        },
                        focusRequesterList = if (weeklySeries.isNotEmpty()) {
                            focusRequesterRows.seriesRows.drop(1)
                        } else {
                            focusRequesterRows.seriesRows
                        },
                        focusRestoreStateProvider = { groupName ->
                            focusRestoreStateMap[groupName] ?: FocusPositionRestoreState<String>().also {
                                focusRestoreStateMap[groupName] = it
                            }
                        },
                        onBackPressed = {
                            if (columnState.firstVisibleItemIndex == 0) {
                                false
                            } else {
                                coroutineScope.launch {
                                    columnState.scrollToItem(0)
                                    focusRequesterRows.seriesRows.firstOrNull()?.requestFocus()
                                }
                                true
                            }
                        },
                        onVideoFocused = { groupName, video ->
                            lastFocusedRowPosition =
                                FocusedVideoPosition(groupName = groupName, videoId = video.id)
                        },
                        listState = { groupName ->
                            rowStateMap[groupName] ?: TvLazyListState().also {
                                rowStateMap[groupName] = it
                            }
                        },
                        onRequestRefresh = { viewModel.loadData(false) }
                    ) {
                        DetailActivity.startActivity(
                            context,
                            it.id,
                            currentSource.sourceId
                        )
                    }
                }
            }
        )

        val seriesList = normalSeries
        LaunchedEffect(displayData) {
            if (!haveSetDefaultFocus && renderedSectionCount > 0) {
                haveSetDefaultFocus = true
                if (weeklySeries.isNotEmpty()) {
                    selectedWeekdayIndex = defaultWeekdayIndex(weeklySeries)
                    columnState.scrollToItem(1)
                    weeklyFocusToken += 1
                    focusRequesterRows.seriesRows.first().requestFocus()
                } else if (seriesList.isNotEmpty()) {
                    var groupIndex = 0
                    var videoIndex = 0
                    if (lastFocusedRowPosition.groupName.isNotEmpty() && lastFocusedRowPosition.videoId.isNotEmpty()) {
                        val gIndex = seriesList.indexOfFirst { it.name == lastFocusedRowPosition.groupName }
                        if (gIndex >= 0) {
                            val vIndex = seriesList.getOrNull(gIndex)
                                ?.value
                                ?.indexOfFirst { it.id == lastFocusedRowPosition.videoId } ?: -1
                            if (vIndex >= 0) {
                                groupIndex = gIndex
                                videoIndex = vIndex
                            }
                        }
                    }
                    val rowAnchorIndex = (if (weeklySeries.isNotEmpty()) 2 else 1) + groupIndex * 2
                    if (rowAnchorIndex !in visibleItemIndex(columnState)) {
                        columnState.scrollToItem(rowAnchorIndex)
                    }
                    val groupName = seriesList[groupIndex].name
                    focusRestoreStateMap[groupName]?.rowListState?.let { state ->
                        if (videoIndex !in visibleItemIndex(state)) {
                            state.scrollToItem(videoIndex)
                        }
                    }
                    runCatching {
                        focusRestoreStateMap[groupName]
                            ?.focusRequesterMap
                            ?.get(seriesList[groupIndex].value[videoIndex].id)
                            ?.requestFocus()
                    }.onFailure {
                        val focusIndex = if (weeklySeries.isNotEmpty()) groupIndex + 1 else groupIndex
                        focusRequesterRows.seriesRows[focusIndex].requestFocus()
                    }
                } else {
                    columnState.scrollToItem(1)
                    focusRequesterRows.seriesRows.first().requestFocus()
                }
            }
        }

        if (homePageDataResource is Resource.Loading && !homePageDataResource.silent) {
            Loading(text = "")
        }
        if (homePageDataResource is Resource.Error) {
            ErrorTip(message = homePageDataResource.message) {
                viewModel.loadData(false)
            }
        }
    }

}

@OptIn(
    ExperimentalTvMaterial3Api::class,
    ExperimentalComposeUiApi::class,
    ExperimentalTvFoundationApi::class
)
@Composable
private fun WeeklyUpdatesSection(
    weekGroups: List<NamedValue<List<AnimeData>>>,
    selectedIndex: Int,
    onSelectedIndexChanged: (Int) -> Unit,
    focusSelectedTabToken: Int,
    sourceId: String,
    videoWidth: Dp,
    videoHeight: Dp,
    focusRequester: FocusRequester,
    upAndDownFocusProperties: UpAndDownFocusProperties,
    onBackPressed: () -> Boolean,
    onVideoFocused: (groupName: String, video: AnimeData) -> Unit,
    onRequestRefresh: () -> Unit,
    onClick: (AnimeData) -> Unit
) {
    if (weekGroups.isEmpty()) return

    val currentIndex = selectedIndex.coerceIn(weekGroups.indices)
    val selectedWeekday = weekGroups[currentIndex]
    val selectedVideos = selectedWeekday.value
    val lastFocusedCardIndexByWeekday = remember(weekGroups) { mutableMapOf<String, Int>() }
    val consumeRapidTabDpad = rememberDpadRepeatGate()
    val consumeRapidCardDpad = rememberDpadRepeatGate()
    val coroutineScope = rememberCoroutineScope()
    val tabRowState = rememberTvLazyListState()
    val cardRowState = remember(selectedWeekday.name) { TvLazyListState() }
    var pendingCardFocusIndex by remember { mutableStateOf<Int?>(null) }
    var allowTabSelectionSync by remember { mutableStateOf(false) }
    val tabFocusRequesters = remember(weekGroups.size) {
        List(weekGroups.size) { FocusRequester() }
    }
    val cardFocusRequesters = remember(selectedWeekday.name, selectedVideos.size) {
        List(selectedVideos.size) { FocusRequester() }
    }
    val weekAccentColors = remember {
        listOf(
            Color(0xFF4FD6E8),
            Color(0xFF58A8FF),
            Color(0xFF9E88FF),
            Color(0xFFFF8FAE),
            Color(0xFFFF9E5E),
            Color(0xFF7DE08D),
            Color(0xFFFFD36B)
        )
    }
    val selectedAccent = weekAccentColors[currentIndex % weekAccentColors.size]

    LaunchedEffect(currentIndex) {
        runCatching { tabRowState.scrollToItem(currentIndex) }
    }

    LaunchedEffect(focusSelectedTabToken) {
        allowTabSelectionSync = false
        repeat(2) { withFrameNanos { } }
        runCatching {
            tabRowState.scrollToItem(currentIndex)
            tabFocusRequesters[currentIndex].requestFocus()
        }
        repeat(2) { withFrameNanos { } }
        allowTabSelectionSync = true
    }

    LaunchedEffect(currentIndex, pendingCardFocusIndex, selectedWeekday.name) {
        if (pendingCardFocusIndex == null || pendingCardFocusIndex != currentIndex || selectedVideos.isEmpty()) {
            return@LaunchedEffect
        }
        val targetIndex =
            (lastFocusedCardIndexByWeekday[selectedWeekday.name] ?: 0).coerceIn(selectedVideos.indices)
        repeat(2) { withFrameNanos { } }
        runCatching { cardRowState.scrollToItem(targetIndex) }
        repeat(2) { withFrameNanos { } }
        runCatching { cardFocusRequesters[targetIndex].requestFocus() }
        pendingCardFocusIndex = null
    }

    FocusGroup(
        Modifier
            .focusRequester(focusRequester)
            .fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 10.dp)
                .clip(RoundedCornerShape(30.dp))
                .background(
                    Brush.linearGradient(
                        listOf(
                            selectedAccent.copy(alpha = 0.18f),
                            Color(0x8C102440),
                            selectedAccent.copy(alpha = 0.28f),
                            Color(0x880D1630)
                        )
                    )
                )
                .border(
                    width = 1.dp,
                    color = selectedAccent.copy(alpha = 0.26f),
                    shape = RoundedCornerShape(30.dp)
                )
                .padding(horizontal = 20.dp, vertical = 18.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = stringResource(id = R.string.home_weekly_updates),
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                        color = Color(0xFFF7F8FF)
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(selectedAccent.copy(alpha = 0.18f))
                        .border(
                            width = 1.dp,
                            color = selectedAccent.copy(alpha = 0.32f),
                            shape = RoundedCornerShape(999.dp)
                        )
                        .padding(horizontal = 12.dp, vertical = 7.dp)
                ) {
                    Text(
                        text = "${selectedVideos.size} 套",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = Color.White
                    )
                }
            }
            Spacer(modifier = Modifier.height(14.dp))
            TvLazyRow(
                modifier = Modifier
                    .focusProperties {
                        enter = { tabFocusRequesters[currentIndex] }
                    }
                    .onPreviewKeyEvent {
                        consumeRapidTabDpad(it)
                    },
                state = tabRowState,
                contentPadding = PaddingValues(horizontal = 34.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(count = weekGroups.size, key = { weekGroups[it].name }) { index ->
                    val isSelected = index == currentIndex
                    val accent = weekAccentColors[index % weekAccentColors.size]
                    Button(
                        onClick = { onSelectedIndexChanged(index) },
                        scale = ButtonDefaults.scale(focusedScale = 1.05f),
                        colors = ButtonDefaults.colors(
                            containerColor = if (isSelected) accent else Color(0x334A5A8B),
                            focusedContainerColor = if (isSelected) accent.copy(alpha = 0.95f) else Color(0xFF496899)
                        ),
                        shape = ButtonDefaults.shape(shape = RoundedCornerShape(18.dp)),
                        modifier = Modifier
                            .focusRequester(tabFocusRequesters[index])
                            .focusProperties {
                                upAndDownFocusProperties.up?.let { up = it }
                                if (weekGroups[index].value.isEmpty()) {
                                    upAndDownFocusProperties.down?.let { down = it }
                                }
                            }
                            .onPreviewKeyEvent { keyEvent ->
                                if (consumeRapidTabDpad(keyEvent)) return@onPreviewKeyEvent true
                                if (keyEvent.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                                when (keyEvent.key) {
                                    Key.DirectionDown -> {
                                        val targetVideos = weekGroups[index].value
                                        if (currentIndex != index) {
                                            onSelectedIndexChanged(index)
                                        }
                                        if (targetVideos.isNotEmpty()) {
                                            pendingCardFocusIndex = index
                                            true
                                        } else {
                                            upAndDownFocusProperties.down?.requestFocus()
                                            upAndDownFocusProperties.down != null
                                        }
                                    }

                                    Key.DirectionUp -> {
                                        upAndDownFocusProperties.up?.requestFocus()
                                        upAndDownFocusProperties.up != null
                                    }

                                    else -> false
                                }
                            }
                            .onFocusChanged {
                                if ((it.isFocused || it.hasFocus) && allowTabSelectionSync) {
                                    onSelectedIndexChanged(index)
                                }
                            }
                            .run {
                                if (index == currentIndex) initiallyFocused() else restorableFocus()
                            }
                    ) {
                        Text(
                            text = weekGroups[index].name,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = Color.White
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(18.dp))
            if (selectedVideos.isEmpty()) {
                Text(
                    text = stringResource(id = R.string.grid_no_data_tip),
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color(0xFFD6DBF5)
                )
            } else {
                TvLazyRow(
                    modifier = Modifier
                        .focusProperties {
                            enter = {
                                val targetIndex =
                                    (lastFocusedCardIndexByWeekday[selectedWeekday.name] ?: 0)
                                        .coerceIn(selectedVideos.indices)
                                cardFocusRequesters.getOrNull(targetIndex) ?: FocusRequester.Default
                            }
                        }
                        .onPreviewKeyEvent {
                            consumeRapidCardDpad(it)
                        },
                    state = cardRowState,
                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(count = selectedVideos.size, key = { selectedVideos[it].id }) { videoIndex ->
                        val video = selectedVideos[videoIndex]
                        VideoCard(
                            modifier = Modifier
                                .size(width = videoWidth, height = videoHeight)
                                .focusRequester(cardFocusRequesters[videoIndex])
                                .focusProperties {
                                    up = tabFocusRequesters[currentIndex]
                                    upAndDownFocusProperties.down?.let { down = it }
                                }
                                .onFocusChanged {
                                    if (it.hasFocus || it.isFocused) {
                                        lastFocusedCardIndexByWeekday[selectedWeekday.name] = videoIndex
                                        onVideoFocused(selectedWeekday.name, video)
                                    }
                                },
                            focusScale = 1.06f,
                            imageUrl = video.imageUrl,
                            title = video.title,
                            subTitle = video.currentEpisode,
                            onKeyEvent = { keyEvent ->
                                if (keyEvent.key == Key.Menu) {
                                    if (keyEvent.type == KeyEventType.KeyDown) {
                                        onRequestRefresh()
                                    }
                                    true
                                } else if (keyEvent.key == Key.DirectionUp && keyEvent.type == KeyEventType.KeyDown) {
                                    val todayIndex = defaultWeekdayIndex(weekGroups)
                                    if (selectedIndex != todayIndex) {
                                        onSelectedIndexChanged(todayIndex)
                                    }
                                    coroutineScope.launch {
                                        runCatching {
                                            tabRowState.scrollToItem(todayIndex)
                                            tabFocusRequesters[todayIndex].requestFocus()
                                        }
                                    }
                                    true
                                } else if (keyEvent.key == Key.DirectionDown && keyEvent.type == KeyEventType.KeyDown) {
                                    upAndDownFocusProperties.down?.requestFocus()
                                    upAndDownFocusProperties.down != null
                                } else if (keyEvent.key == Key.DirectionLeft && videoIndex == 0) {
                                    true
                                } else if (keyEvent.key == Key.Back && keyEvent.type == KeyEventType.KeyUp) {
                                    onBackPressed()
                                } else {
                                    false
                                }
                            },
                            onClick = { onClick(video) }
                        )
                    }
                }
            }
        }
    }
}

private fun visibleItemIndex(listState: TvLazyListState): List<Int> {
    val layoutInfo = listState.layoutInfo
    val visibleItemsInfo = layoutInfo.visibleItemsInfo
    if (visibleItemsInfo.isEmpty()) {
        return emptyList()
    }
    val fullyVisibleItemsInfo = visibleItemsInfo.toMutableList()

    val lastItem = fullyVisibleItemsInfo.last()
    val viewportHeight = layoutInfo.viewportEndOffset + layoutInfo.viewportStartOffset

    if (lastItem.offset + lastItem.size > viewportHeight) {
        fullyVisibleItemsInfo.removeLast()
    }

    val firstItemIfLeft = fullyVisibleItemsInfo.firstOrNull()
    if (firstItemIfLeft != null && firstItemIfLeft.offset < layoutInfo.viewportStartOffset) {
        fullyVisibleItemsInfo.removeFirst()
    }

    return fullyVisibleItemsInfo.map { it.index }
}

@OptIn(
    ExperimentalTvMaterial3Api::class,
    ExperimentalComposeUiApi::class
)
fun TvLazyListScope.videoRows(
    videoSeries: List<NamedValue<List<AnimeData>>>,
    sourceId: String,
    videoWidth: Dp,
    videoHeight: Dp,
    focusRestoreStateProvider: (groupName: String) -> FocusPositionRestoreState<String>,
    listState: (groupName: String) -> TvLazyListState,
    focusRequesterList: List<FocusRequester> = emptyList(),
    topFocusRequester: FocusRequester? = null,
    onVideoFocused: (groupName: String, video: AnimeData) -> Unit,
    onBackPressed: () -> Boolean,
    onRequestRefresh: () -> Unit,
    onClick: (video: AnimeData) -> Unit
) {
    fun requestRowEntry(
        targetGroupIndex: Int,
        fallbackRequester: FocusRequester? = focusRequesterList.getOrNull(targetGroupIndex)
    ): Boolean {
        if (targetGroupIndex !in videoSeries.indices) return false
        val state = focusRestoreStateProvider(videoSeries[targetGroupIndex].name)
        val requester = state.lastFocusItem?.let(state.focusRequesterMap::get)
            ?: state.initiallyFocusItem?.let(state.focusRequesterMap::get)
            ?: fallbackRequester
        return if (requester != null) {
            requester.requestFocus()
            true
        } else {
            false
        }
    }

    val focusScale = 1.04f
    val verticalPadding = videoWidth * (((focusScale - 1) / 2) + 0.01f)
    val sectionGradients = listOf(
        listOf(Color(0xCC0F233B), Color(0xCC0C7F90), Color(0xCC162E40)),
        listOf(Color(0xCC2C1635), Color(0xCCB04C72), Color(0xCC2C1933)),
        listOf(Color(0xCC1C2646), Color(0xCC5562E8), Color(0xCC162945)),
        listOf(Color(0xCC342112), Color(0xCCB66B2C), Color(0xCC341A22)),
        listOf(Color(0xCC173229), Color(0xCC1E8D7A), Color(0xCC1A2F39))
    )
    for ((groupIndex, videoGroup) in videoSeries.withIndex()) {
        val (groupName, videos) = videoGroup
        val sectionColors = sectionGradients[groupIndex % sectionGradients.size]
        val displayGroupName = when {
            groupName.contains("劇場") -> "劇場番組"
            groupName.contains("TV") -> "季度新番"
            else -> groupName
        }
        val sectionSubtitle = when {
            groupName.contains("劇場") -> "劇場版・OVA・特別篇"
            groupName.contains("TV") -> "季度連載 / 新番更新"
            else -> "精選片單"
        }
        item(key = sourceId to "t$groupName") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 10.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                sectionColors[0].copy(alpha = 0.72f),
                                sectionColors[1].copy(alpha = 0.68f),
                                sectionColors[2].copy(alpha = 0.72f)
                            )
                        )
                    )
                    .border(
                        width = 1.dp,
                        color = Color.White.copy(alpha = 0.08f),
                        shape = RoundedCornerShape(22.dp)
                    )
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        text = displayGroupName,
                        style = MaterialTheme.typography.headlineSmall.copy(
                            color = Color(0xFFF4F6FF),
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Text(
                        text = sectionSubtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFD7DFFB)
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(Color.White.copy(alpha = 0.12f))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "${videos.size} 套",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color(0xFFD4DCF9)
                    )
                }
            }
        }
        item(key = sourceId to groupName) {
            val consumeRapidDpad = rememberDpadRepeatGate()
            val focusRestoreState = focusRestoreStateProvider(groupName)
            val rowListState = listState(groupName)
            focusRestoreState.rowListState = rowListState
            val pivotOffsets = PivotOffsets()
            LaunchedEffect(videos) {
                val ids = videos.asSequence().map { it.id }.toSet()
                focusRestoreState.focusRequesterMap.keys.toList().forEach {
                    if (!ids.contains(it)) {
                        focusRestoreState.focusRequesterMap.remove(it)
                    }
                }
                val lastFocusItem = focusRestoreState.lastFocusItem
                val scrollIndex = if (lastFocusItem != null) {
                    videos.indexOfFirst { it.id == lastFocusItem }.takeIf { it > -1 } ?: 0
                } else {
                    0
                }
                rowListState.scrollToItem(
                    scrollIndex,
                    -(pivotOffsets.parentFraction * rowListState.layoutInfo.viewportSize.width).toInt()
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(
                        Brush.linearGradient(
                            sectionColors.map { it.copy(alpha = 0.56f) }
                        )
                    )
                    .border(
                        width = 1.dp,
                        color = Color.White.copy(alpha = 0.07f),
                        shape = RoundedCornerShape(28.dp)
                    )
                    .padding(vertical = 14.dp)
            ) {
                TvLazyRow(
                    state = rowListState,
                    pivotOffsets = pivotOffsets,
                    modifier = Modifier
                        .focusProperties {
                            enter = {
                                focusRestoreState.lastFocusItem?.let { focusRestoreState.focusRequesterMap[it] }
                                    ?: focusRestoreState.initiallyFocusItem?.let { focusRestoreState.focusRequesterMap[it] }
                                    ?: FocusRequester.Default
                            }
                        }
                        .onPreviewKeyEvent { keyEvent ->
                            if (consumeRapidDpad(keyEvent)) return@onPreviewKeyEvent true
                            if (keyEvent.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                            when (keyEvent.key) {
                                Key.DirectionUp -> {
                                    if (groupIndex == 0) {
                                        topFocusRequester?.requestFocus()
                                        topFocusRequester != null
                                    } else {
                                        requestRowEntry(groupIndex - 1)
                                    }
                                }

                                Key.DirectionDown -> {
                                    requestRowEntry(groupIndex + 1)
                                }

                                else -> false
                            }
                        }
                        .focusRequester(focusRequesterList[groupIndex]),
                    contentPadding = PaddingValues(
                        vertical = verticalPadding,
                        horizontal = 18.dp
                    ),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(count = videos.size, key = { videos[it].id }) { videoIndex ->
                        val video = videos[videoIndex]
                        val focusRequester = remember { FocusRequester() }
                        val upTarget = if (groupIndex == 0) {
                            topFocusRequester
                        } else {
                            focusRequesterList.getOrNull(groupIndex - 1)
                        }
                        val downTarget = focusRequesterList.getOrNull(groupIndex + 1)
                        if (videoIndex == 0) {
                            focusRestoreState.initiallyFocusItem = video.id
                        }
                        focusRestoreState.focusRequesterMap[video.id] = focusRequester
                        VideoCard(
                            modifier = Modifier
                                .size(width = videoWidth, height = videoHeight)
                                .focusRequester(focusRequester)
                                .focusProperties {
                                    upTarget?.let { up = it }
                                    downTarget?.let { down = it }
                                }
                                .onFocusChanged {
                                    if (it.hasFocus || it.isFocused) {
                                        focusRestoreState.lastFocusItem = video.id
                                        onVideoFocused(groupName, video)
                                    }
                                },
                            focusScale = focusScale,
                            imageUrl = video.imageUrl,
                            title = video.title,
                            subTitle = video.currentEpisode,
                            onKeyEvent = { keyEvent ->
                                if (keyEvent.key == Key.Menu) {
                                    if (keyEvent.type == KeyEventType.KeyDown) {
                                        onRequestRefresh()
                                    }
                                    true
                                } else if (keyEvent.key == Key.DirectionUp && keyEvent.type == KeyEventType.KeyDown) {
                                    if (groupIndex == 0) {
                                        topFocusRequester?.requestFocus()
                                        topFocusRequester != null
                                    } else {
                                        requestRowEntry(groupIndex - 1, upTarget)
                                    }
                                } else if (keyEvent.key == Key.DirectionDown && keyEvent.type == KeyEventType.KeyDown) {
                                    requestRowEntry(groupIndex + 1, downTarget)
                                } else if (keyEvent.key == Key.DirectionLeft && videoIndex == 0) {
                                    true
                                } else if (keyEvent.key == Key.Back && keyEvent.type == KeyEventType.KeyUp) {
                                    onBackPressed()
                                } else {
                                    false
                                }
                            },
                            onClick = { onClick(video) }
                        )
                    }
                }
            }
        }
    }
    if (videoSeries.isNotEmpty()) {
        item(key = sourceId to "bottom-safe-space") {
            Spacer(modifier = Modifier.height(34.dp))
        }
    }
}

@OptIn(
    ExperimentalTvMaterial3Api::class,
    ExperimentalTvFoundationApi::class
)
@Composable
fun HomeTitleRow(
    modifier: Modifier = Modifier,
    buttonList: List<HomeScreenButton>,
    title: String,
    subtitle: String,
    focusRequester: FocusRequester,
    upAndDownFocusProperties: UpAndDownFocusProperties,
    iconSize: Dp = 40.dp,
    iconFocusedScale: Float = 1.1f
) {
    val consumeRapidDpad = rememberDpadRepeatGate()
    val iconPadding = iconSize / 4
    val displayButtons = remember(buttonList) {
        buttonList.filter { it.display }
    }
    FocusGroup(Modifier.focusRequester(focusRequester)) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp)
                .clip(RoundedCornerShape(30.dp))
                .background(
                    Brush.linearGradient(
                        listOf(
                            Color(0xD90F253B),
                            Color(0xD91E6E86),
                            Color(0xCCAA4F68)
                        )
                    )
                )
                .padding(horizontal = 18.dp, vertical = 18.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TvLazyRow(
                    modifier = Modifier.onPreviewKeyEvent {
                        if (consumeRapidDpad(it)) return@onPreviewKeyEvent true
                        if (it.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        if (it.key == Key.DirectionDown) {
                            upAndDownFocusProperties.down?.requestFocus()
                            upAndDownFocusProperties.down != null
                        } else {
                            false
                        }
                    },
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(horizontal = iconSize / 2 * (iconFocusedScale - 1f))
                ) {
                    items(
                        count = displayButtons.size,
                        key = { displayButtons[it].label }
                    ) { btnIndex ->
                        val btn = displayButtons[btnIndex]
                        val bgColor = colorResource(id = btn.backgroundColor)
                        val label = stringResource(id = btn.label)
                        Button(
                            onClick = btn.onClick,
                            shape = ButtonDefaults.shape(shape = CircleShape),
                            scale = ButtonDefaults.scale(focusedScale = iconFocusedScale),
                            colors = ButtonDefaults.colors(
                                containerColor = bgColor,
                                focusedContainerColor = bgColor
                            ),
                            modifier = Modifier
                                .size(iconSize)
                                .focusProperties { applyUpAndDown(upAndDownFocusProperties) }
                                .run {
                                    if (btnIndex == 0) initiallyFocused() else restorableFocus()
                                },
                            contentPadding = PaddingValues(iconPadding)
                        ) {
                            Icon(
                                imageVector = btn.icon,
                                contentDescription = label,
                                tint = colorResource(id = btn.tint)
                            )
                        }
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    if (subtitle.isNotBlank()) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.titleMedium,
                            color = Color(0xFFD2DBFF)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                    Text(
                        text = title,
                        style = MaterialTheme.typography.headlineLarge.copy(
                            fontSize = MaterialTheme.typography.headlineLarge.fontSize * 1.2,
                            fontWeight = FontWeight.Bold
                        ),
                        color = Color.White
                    )
                }
            }
        }
    }
}

data class HomeScreenButton(
    val icon: ImageVector,
    @ColorRes
    val backgroundColor: Int,
    @ColorRes
    val tint: Int = R.color.gray100,
    @StringRes
    val label: Int,
    val display: Boolean = true,
    val onClick: () -> Unit = {},
)

private data class HomeScreenFocusRows(
    val topButtonRow: FocusRequester,
    val seriesRows: List<FocusRequester>
)

private data class FocusedVideoPosition(
    val groupName: String,
    val videoId: String
) {
    companion object {
        val DEFAULT = FocusedVideoPosition(
            groupName = "",
            videoId = ""
        )
    }
}

private val WEEKDAY_SECTION_NAMES = listOf(
    "週一",
    "週二",
    "週三",
    "週四",
    "週五",
    "週六",
    "週日"
)

private fun defaultWeekdayIndex(weekGroups: List<NamedValue<List<AnimeData>>>): Int {
    if (weekGroups.isEmpty()) return 0
    return Calendar.getInstance().run {
        val dayOfWeek = get(Calendar.DAY_OF_WEEK)
        ((dayOfWeek + 5) % 7).coerceIn(0, weekGroups.lastIndex)
    }
}

data class FocusPositionRestoreState<T>(
    var focusRequesterMap: MutableMap<T, FocusRequester> = mutableMapOf(),
    var initiallyFocusItem: T? = null,
    var lastFocusItem: T? = null,
    var rowListState: TvLazyListState? = null
)
