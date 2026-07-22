@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.jing.sakura.compose.screen

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
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
    val synopses = viewModel.synopses.collectAsState().value
    Box(
        modifier = Modifier
            .fillMaxSize()
            .aulamaTvBackground()
    ) {
        when (timeline) {
            is Resource.Success -> TimeLine(
                data = timeline.data,
                sourceId = viewModel.sourceId,
                synopses = synopses,
                onAnimeHighlighted = viewModel::loadSynopsis
            )

            is Resource.Error -> ErrorTip(message = timeline.message) {
                viewModel.loadData()
            }

            is Resource.Loading -> Loading()
        }
    }
}

@Composable
fun TimeLine(
    data: UpdateTimeLine,
    sourceId: String,
    synopses: Map<String, String>,
    onAnimeHighlighted: (AnimeData) -> Unit
) {
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
    val extractedAccent = highlightedAnime?.let { rememberArtworkAccent(it.imageUrl) }
        ?: Color.Transparent
    val accent by animateColorAsState(
        targetValue = extractedAccent,
        animationSpec = tween(durationMillis = 360, easing = FastOutSlowInEasing),
        label = "timeline-accent"
    )

    Box(modifier = Modifier.fillMaxSize()) {
        TimelineBackdrop(anime = highlightedAnime, accent = accent)
        Column(modifier = Modifier.fillMaxSize()) {
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .padding(horizontal = 34.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) {
                data.timeline.forEachIndexed { index, day ->
                    TimelineDayTab(
                        name = day.first,
                        selected = index == selectedDayIndex,
                        accent = accent,
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
                dayName = selectedDay.first,
                synopsis = highlightedAnime?.let { synopses[it.id] }.orEmpty(),
                accent = accent,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
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
                        .weight(1f),
                    contentPadding = PaddingValues(horizontal = 34.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
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
                            modifier = Modifier.size(width = 170.dp, height = 238.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            VideoCard(
                                modifier = Modifier
                                    .size(width = 160.dp, height = 226.dp)
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
                                focusScale = 1.04f,
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
    }

    LaunchedEffect(data) {
        runCatching { dayFocusRequesters[currentDayIndex].requestFocus() }
    }

    LaunchedEffect(selectedDayIndex) {
        highlightedAnimeIndex = 0
        programmeRowState.scrollToItem(0)
    }

    LaunchedEffect(highlightedAnime?.id) {
        highlightedAnime?.let(onAnimeHighlighted)
    }
}

@Composable
private fun TimelineBackdrop(anime: AnimeData?, accent: Color) {
    Box(modifier = Modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = anime?.imageUrl.orEmpty(),
            transitionSpec = {
                fadeIn(tween(360, easing = FastOutSlowInEasing))
                    .togetherWith(fadeOut(tween(220, easing = FastOutSlowInEasing)))
            },
            label = "timeline-backdrop"
        ) { imageUrl ->
            if (imageUrl.isNotBlank()) {
                val request = rememberPosterImageRequest(
                    imageUrl = imageUrl,
                    widthPx = 960,
                    heightPx = 1360
                )
                AsyncImage(
                    model = request,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    alignment = Alignment.TopEnd,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            alpha = 0.76f
                            scaleX = 1.42f
                            scaleY = 1.42f
                            transformOrigin = TransformOrigin(1f, 0f)
                        }
                )
            }
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(accent.copy(alpha = 0.20f), Color.Transparent),
                        radius = 820f
                    )
                )
                .background(
                    Brush.horizontalGradient(
                        colorStops = arrayOf(
                            0f to AulamaTvColors.Background,
                            0.42f to AulamaTvColors.Background,
                            0.52f to AulamaTvColors.Background.copy(alpha = 0.96f),
                            0.60f to AulamaTvColors.Background.copy(alpha = 0.76f),
                            0.69f to AulamaTvColors.Background.copy(alpha = 0.42f),
                            0.78f to AulamaTvColors.Background.copy(alpha = 0.14f),
                            0.88f to AulamaTvColors.Background.copy(alpha = 0.03f),
                            1f to Color.Transparent
                        )
                    )
                )
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0f to AulamaTvColors.Background.copy(alpha = 0.32f),
                            0.58f to Color.Transparent,
                            1f to AulamaTvColors.Background
                        )
                    )
                )
        )
    }
}

@Composable
private fun TimelineFocusSummary(
    anime: AnimeData?,
    dayName: String,
    synopsis: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    AnimatedContent(
        targetState = anime,
        modifier = modifier,
        transitionSpec = {
            fadeIn(tween(240, easing = FastOutSlowInEasing))
                .togetherWith(fadeOut(tween(150, easing = FastOutSlowInEasing)))
        },
        label = "timeline-summary"
    ) { selected ->
        if (selected == null) {
            Spacer(Modifier.fillMaxSize())
        } else {
            val title = localizedText(selected.title)
            val airInfo = remember(dayName, selected.currentEpisode) {
                listOf(dayName, selected.currentEpisode)
                    .map(::cleanTimelineText)
                    .filter(String::isNotBlank)
                    .distinct()
                    .joinToString("  ·  ")
            }
            val description = localizedText(
                cleanTimelineText(synopsis).ifBlank { "正在載入中文簡介..." }
            )
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 42.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Spacer(
                    modifier = Modifier
                        .size(width = 34.dp, height = 3.dp)
                        .background(accent, AulamaCardShape)
                )
                Spacer(Modifier.height(5.dp))
                Text(
                    text = title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontSize = 30.sp,
                        lineHeight = 34.sp,
                        fontWeight = FontWeight.ExtraBold
                    ),
                    color = accent,
                    modifier = Modifier.fillMaxWidth(0.52f)
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = localizedText("播出時間  ·  $airInfo"),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontSize = 14.sp,
                        lineHeight = 18.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    color = accent,
                    modifier = Modifier.fillMaxWidth(0.54f)
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = description,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 15.sp,
                        lineHeight = 19.sp
                    ),
                    color = AulamaTvColors.TextSecondary,
                    modifier = Modifier.fillMaxWidth(0.56f)
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
            .height(40.dp)
            .padding(horizontal = 42.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = localizedText(if (isToday) "今日播送" else "播送節目"),
            style = MaterialTheme.typography.titleLarge.copy(
                fontSize = 21.sp,
                lineHeight = 26.sp,
                fontWeight = FontWeight.SemiBold
            ),
            color = AulamaTvColors.TextPrimary
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = localizedText("$count 套"),
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
    accent: Color,
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
            pressedContainerColor = accent.copy(alpha = 0.08f)
        )
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = localizedText(name),
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
                    accent
                } else {
                    AulamaTvColors.TextSecondary
                }
            )
            Spacer(Modifier.height(4.dp))
            Box(
                modifier = Modifier
                    .size(width = 28.dp, height = 3.dp)
                    .background(
                        color = if (selected) accent else Color.Transparent,
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
