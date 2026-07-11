@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.jing.sakura.compose.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.tv.foundation.ExperimentalTvFoundationApi
import androidx.tv.foundation.lazy.list.TvLazyColumn
import androidx.tv.foundation.lazy.list.TvLazyRow
import androidx.tv.foundation.lazy.list.rememberTvLazyListState
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ClickableSurfaceScale
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import com.jing.sakura.compose.common.ErrorTip
import com.jing.sakura.compose.common.FocusGroup
import com.jing.sakura.compose.common.Loading
import com.jing.sakura.compose.common.AulamaCardShape
import com.jing.sakura.compose.common.AulamaFocusScale
import com.jing.sakura.compose.common.AulamaPageHeader
import com.jing.sakura.compose.common.AulamaTvColors
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
            .background(AulamaTvColors.Background),
        contentAlignment = Alignment.Center
    ) {
        Column(Modifier.fillMaxSize()) {
            AulamaPageHeader(title = stringResource(com.jing.sakura.R.string.timeline_title))
            if (timeline is Resource.Success) {
                TimeLine(timeline.data, sourceId = viewModel.sourceId)
            }
        }
        if (timeline == Resource.Loading) {
            Loading()
        } else if (timeline is Resource.Error) {
            ErrorTip(message = timeline.message) {
                viewModel.loadData()
            }
        }
    }
}

@Composable
fun TimeLine(data: UpdateTimeLine, sourceId: String) {
    val defaultFocusRequester = remember {
        FocusRequester()
    }
    val rowState = rememberTvLazyListState(data.current)
    val accents = remember {
        listOf(
            AulamaTvColors.Cyan,
            Color(0xFF75A7FF),
            AulamaTvColors.Pink,
            AulamaTvColors.Amber,
            AulamaTvColors.Green
        )
    }
    TvLazyRow(
        state = rowState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp),
        content = {
        items(count = data.timeline.size, key = { data.timeline[it].first }) { idx ->
            val timeline = data.timeline[idx]
            TimeLineColumn(
                modifier = Modifier.width(200.dp).run {
                    if (idx == data.current) {
                        focusRequester(defaultFocusRequester)
                    } else {
                        this
                    }
                },
                name = timeline.first,
                animeList = timeline.second,
                sourceId = sourceId,
                accent = accents[idx % accents.size]
            )
        }
    })
    LaunchedEffect(Unit) {
        rowState.scrollToItem(data.current)
        try {
            defaultFocusRequester.requestFocus()
        } catch (_: Exception) {
        }
    }
}

@OptIn(ExperimentalTvFoundationApi::class)
@Composable
fun TimeLineColumn(
    modifier: Modifier = Modifier,
    name: String,
    animeList: List<AnimeData>,
    sourceId: String,
    accent: Color
) {
    val context = LocalContext.current
    FocusGroup(modifier = modifier) {
        Column(
            Modifier
                .fillMaxSize()
        ) {
            Text(
                text = name,
                style = MaterialTheme.typography.titleMedium,
                color = AulamaTvColors.TextPrimary,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(accent.copy(alpha = 0.22f), AulamaCardShape)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                textAlign = TextAlign.Center
            )
            TvLazyColumn(
                contentPadding = PaddingValues(vertical = 8.dp),
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
                content = {
                items(count = animeList.size, key = { animeList[it].url }) { idx ->
                    val anime = animeList[idx]
                    AnimeName(
                        modifier = Modifier.run {
                            if (idx == 0) {
                                initiallyFocused()
                            } else {
                                restorableFocus()
                            }
                        },
                        name = anime.title
                    ) {
                        DetailActivity.startActivity(context, anime.id, sourceId)
                    }
                }
            })
        }
    }

}


@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun AnimeName(
    modifier: Modifier = Modifier, name: String, onClick: () -> Unit = {}
) {
    var focused by remember {
        mutableStateOf(false)
    }

    Surface(
        modifier = modifier.onFocusChanged {
            focused = it.hasFocus || it.isFocused
        },
        onClick = onClick,
        scale = ClickableSurfaceDefaults.scale(focusedScale = AulamaFocusScale),
        shape = ClickableSurfaceDefaults.shape(shape = AulamaCardShape),
        border = ClickableSurfaceDefaults.border(
            border = Border(
                BorderStroke(1.dp, AulamaTvColors.Outline),
                shape = AulamaCardShape
            ),
            focusedBorder = Border(
                BorderStroke(2.dp, AulamaTvColors.FocusBorder),
                shape = AulamaCardShape
            )
        ),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = AulamaTvColors.SurfaceRaised,
            focusedContainerColor = Color(0xFF173A40)
        )
    ) {
        Text(
            text = name,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp).run {
                if (focused) {
                    basicMarquee()
                } else {
                    this
                }
            },
            style = MaterialTheme.typography.bodyMedium
        )
    }

}
