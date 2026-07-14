@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.jing.sakura.compose.screen

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.jing.sakura.R
import com.jing.sakura.compose.common.AulamaCardShape
import com.jing.sakura.compose.common.AulamaFocusScale
import com.jing.sakura.compose.common.AulamaPageHeader
import com.jing.sakura.compose.common.AulamaTvColors
import com.jing.sakura.compose.common.ErrorTip
import com.jing.sakura.compose.common.Loading
import com.jing.sakura.compose.common.VideoCard
import com.jing.sakura.compose.common.aulamaTvBackground
import com.jing.sakura.compose.common.localizedText
import com.jing.sakura.compose.common.rememberArtworkAccent
import com.jing.sakura.compose.common.rememberPosterImageRequest
import com.jing.sakura.data.AnimeData
import com.jing.sakura.data.Resource
import com.jing.sakura.data.UpdateTimeLine
import com.jing.sakura.detail.DetailActivity
import com.jing.sakura.timeline.TimelineViewModel

@Composable
fun TimelineScreen(viewModel: TimelineViewModel) {
    val timeline = viewModel.timelines.collectAsState().value
    Box(
        modifier = Modifier
            .fillMaxSize()
            .aulamaTvBackground()
    ) {
        when (timeline) {
            is Resource.Success -> Column(Modifier.fillMaxSize()) {
                AulamaPageHeader(title = stringResource(R.string.timeline_title))
                TimeLine(timeline.data, sourceId = viewModel.sourceId)
            }

            is Resource.Error -> ErrorTip(message = timeline.message) {
                viewModel.loadData()
            }

            is Resource.Loading -> Loading()
        }
    }
}

@Composable
fun TimeLine(data: UpdateTimeLine, sourceId: String) {
    if (data.timeline.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = stringResource(R.string.grid_no_data_tip),
                style = MaterialTheme.typography.titleLarge,
                color = AulamaTvColors.TextSecondary
            )
        }
        return
    }

    val currentDayIndex = data.current.coerceIn(0, data.timeline.lastIndex)
    var selectedDayIndex by remember(data) { mutableIntStateOf(currentDayIndex) }
    val selectedDay = data.timeline[selectedDayIndex]
    var highlightedAnimeIndex by remember(selectedDayIndex) { mutableIntStateOf(0) }
    val highlightedAnime = selectedDay.second.getOrNull(highlightedAnimeIndex)
        ?: selectedDay.second.firstOrNull()
    val dayFocusRequesters = remember(data.timeline.size) {
        List(data.timeline.size) { FocusRequester() }
    }
    val firstPosterFocusRequester = remember(selectedDayIndex) { FocusRequester() }
    val programmeRowState = rememberLazyListState()
    val context = LocalContext.current

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .padding(horizontal = 36.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically
        ) {
            data.timeline.forEachIndexed { index, day ->
                TimelineDayTab(
                    name = day.first,
                    selected = index == selectedDayIndex,
                    modifier = Modifier
                        .width(92.dp)
                        .focusRequester(dayFocusRequesters[index])
                        .focusProperties {
                            if (day.second.isNotEmpty()) {
                                down = firstPosterFocusRequester
                            }
                        },
                    onFocused = { selectedDayIndex = index },
                    onClick = { selectedDayIndex = index }
                )
            }
        }

        TimelineFocusSummary(
            anime = highlightedAnime,
            modifier = Modifier
                .fillMaxWidth()
                .height(136.dp)
        )

        TimelineStripHeader(
            count = selectedDay.second.size,
            isToday = selectedDayIndex == currentDayIndex
        )

        if (selectedDay.second.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(R.string.grid_no_data_tip),
                    style = MaterialTheme.typography.titleLarge,
                    color = AulamaTvColors.TextSecondary
                )
            }
        } else {
            LazyRow(
                state = programmeRowState,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(236.dp),
                contentPadding = PaddingValues(horizontal = 36.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                items(
                    count = selectedDay.second.size,
                    key = { index ->
                        val anime = selectedDay.second[index]
                        "${anime.sourceId}:${anime.id}:$index"
                    }
                ) { index ->
                    val anime = selectedDay.second[index]
                    Box(
                        modifier = Modifier.size(width = 156.dp, height = 220.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        VideoCard(
                            modifier = Modifier
                                .size(width = 146.dp, height = 206.dp)
                                .focusProperties {
                                    up = dayFocusRequesters[selectedDayIndex]
                                }
                                .run {
                                    if (index == 0) {
                                        focusRequester(firstPosterFocusRequester)
                                    } else {
                                        this
                                    }
                                },
                            imageUrl = anime.imageUrl,
                            title = anime.title,
                            subTitle = anime.currentEpisode,
                            focusScale = AulamaFocusScale,
                            onFocused = { highlightedAnimeIndex = index },
                            onClick = {
                                DetailActivity.startActivity(context, anime.id, sourceId)
                            }
                        )
                    }
                }
            }
        }
    }

    LaunchedEffect(data) {
        runCatching { dayFocusRequesters[currentDayIndex].requestFocus() }
    }

    LaunchedEffect(selectedDayIndex) {
        highlightedAnimeIndex = 0
        programmeRowState.scrollToItem(0)
    }
}

@Composable
private fun TimelineFocusSummary(
    anime: AnimeData?,
    modifier: Modifier = Modifier
) {
    if (anime == null) {
        Spacer(modifier)
        return
    }

    val accent = rememberArtworkAccent(anime.imageUrl)
    val posterRequest = rememberPosterImageRequest(anime.imageUrl)
    val title = localizedText(anime.title)
    val meta = remember(anime.currentEpisode, anime.year) {
        listOf(anime.currentEpisode, anime.year)
            .map(::cleanTimelineText)
            .filter(String::isNotBlank)
            .distinct()
            .joinToString("  ·  ")
    }
    val description = remember(anime.description, anime.tags) {
        cleanTimelineText(anime.description).ifBlank {
            cleanTimelineText(anime.tags)
        }
    }

    Row(
        modifier = modifier.padding(horizontal = 42.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(width = 82.dp, height = 116.dp)
                .clip(AulamaCardShape)
                .background(AulamaTvColors.SurfaceRaised)
        ) {
            AsyncImage(
                model = posterRequest,
                contentDescription = title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
        Spacer(Modifier.width(22.dp))
        Spacer(
            modifier = Modifier
                .size(width = 4.dp, height = 82.dp)
                .background(accent, AulamaCardShape)
        )
        Spacer(Modifier.width(18.dp))
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontSize = 27.sp,
                    lineHeight = 32.sp,
                    fontWeight = FontWeight.SemiBold
                ),
                color = accent
            )
            if (meta.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = meta,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontSize = 15.sp,
                        lineHeight = 19.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    color = AulamaTvColors.TextPrimary
                )
            }
            if (description.isNotBlank()) {
                Spacer(Modifier.height(5.dp))
                Text(
                    text = description,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 16.sp,
                        lineHeight = 21.sp
                    ),
                    color = AulamaTvColors.TextSecondary
                )
            }
        }
    }
}

@Composable
private fun TimelineStripHeader(count: Int, isToday: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(38.dp)
            .padding(horizontal = 42.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = if (isToday) "今日播送" else "播送節目",
            style = MaterialTheme.typography.titleLarge.copy(
                fontSize = 21.sp,
                lineHeight = 26.sp,
                fontWeight = FontWeight.SemiBold
            ),
            color = AulamaTvColors.TextPrimary
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = "$count 套",
            style = MaterialTheme.typography.labelLarge.copy(
                fontSize = 14.sp,
                lineHeight = 18.sp
            ),
            color = AulamaTvColors.TextSecondary
        )
    }
}

@Composable
private fun TimelineDayTab(
    name: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onFocused: () -> Unit,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    Surface(
        modifier = modifier
            .height(46.dp)
            .onFocusChanged {
                focused = it.isFocused || it.hasFocus
                if (focused) onFocused()
            },
        onClick = onClick,
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.035f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            focusedContainerColor = Color.Transparent,
            pressedContainerColor = AulamaTvColors.Cyan.copy(alpha = 0.08f)
        )
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = name,
                modifier = Modifier.fillMaxWidth(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = 17.sp,
                    lineHeight = 22.sp,
                    fontWeight = if (selected || focused) FontWeight.SemiBold else FontWeight.Medium
                ),
                color = if (selected || focused) {
                    AulamaTvColors.Cyan
                } else {
                    AulamaTvColors.TextSecondary
                }
            )
            Spacer(Modifier.height(4.dp))
            Box(
                modifier = Modifier
                    .size(width = 28.dp, height = 3.dp)
                    .background(
                        color = if (selected) AulamaTvColors.Cyan else Color.Transparent,
                        shape = AulamaCardShape
                    )
            )
        }
    }
}

private val TimelineHtmlTag = Regex("<[^>]+>")
private val TimelineWhitespace = Regex("\\s+")

private fun cleanTimelineText(value: String): String = value
    .replace(TimelineHtmlTag, " ")
    .replace("&nbsp;", " ")
    .replace("&amp;", "&")
    .replace(TimelineWhitespace, " ")
    .trim()
