package com.jing.sakura.compose.screen

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.jing.sakura.R
import com.jing.sakura.compose.common.AulamaCardShape
import com.jing.sakura.compose.common.AulamaFocusScale
import com.jing.sakura.compose.common.AulamaTvColors
import com.jing.sakura.compose.common.VideoCard
import com.jing.sakura.compose.common.aulamaTvBackground
import com.jing.sakura.compose.common.localizedText
import com.jing.sakura.compose.common.rememberArtworkAccent
import com.jing.sakura.data.AnimeData
import com.jing.sakura.data.Resource
import com.jing.sakura.detail.DetailActivity
import com.jing.sakura.home.CategoryGroupWrapper
import com.jing.sakura.home.CategoryViewModel
import com.jing.sakura.repo.VideoCategoryGroup
import kotlinx.coroutines.launch

@Composable
fun AnimeCategoryScreen(viewModel: CategoryViewModel) {
    val categoriesResource = viewModel.categories.collectAsState().value
    if (categoriesResource is Resource.Loading) {
        com.jing.sakura.compose.common.Loading()
        return
    }
    if (categoriesResource is Resource.Error) {
        com.jing.sakura.compose.common.ErrorTip(message = categoriesResource.message) {
            viewModel.loadCategories()
        }
        return
    }

    val categoryGroups =
        (categoriesResource as Resource.Success<List<CategoryGroupWrapper>>).data
            .filter { it.group.categories.isNotEmpty() }
    val selectedValues by viewModel.userSelectedCategories.collectAsState()
    val appliedValues by viewModel.selectedCategories.collectAsState()
    val pagingItems = viewModel.pager.collectAsLazyPagingItems()
    val context = LocalContext.current

    LaunchedEffect(appliedValues) {
        if (appliedValues.isNotEmpty()) pagingItems.refresh()
    }

    when (val refreshState = pagingItems.loadState.refresh) {
        is LoadState.Loading -> {
            com.jing.sakura.compose.common.Loading()
        }

        is LoadState.Error -> {
            com.jing.sakura.compose.common.ErrorTip(
                message = androidx.compose.ui.res.stringResource(
                    R.string.error_category_load_template,
                    refreshState.error.message ?: refreshState.error.toString()
                )
            ) {
                pagingItems.refresh()
            }
        }

        is LoadState.NotLoading -> {
            DiscoverGrid(
                pagingItems = pagingItems,
                categoryGroups = categoryGroups,
                selectedValues = selectedValues,
                hasPendingChanges = selectedValues != appliedValues,
                onSelect = viewModel::onUserSelect,
                onApply = { viewModel.applyUserSelectedCategories() },
                onOpen = { anime ->
                    DetailActivity.startActivity(
                        context = context,
                        animeId = anime.id,
                        sourceId = viewModel.sourceId
                    )
                }
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun DiscoverGrid(
    pagingItems: LazyPagingItems<AnimeData>,
    categoryGroups: List<CategoryGroupWrapper>,
    selectedValues: Map<String, String>,
    hasPendingChanges: Boolean,
    onSelect: (key: String, value: String) -> Unit,
    onApply: () -> Unit,
    onOpen: (AnimeData) -> Unit
) {
    val gridState = rememberLazyGridState()
    val firstCardFocusRequester = remember { FocusRequester() }
    val applyFocusRequester = remember { FocusRequester() }
    val groupKeys = categoryGroups.map { it.group.key }
    val groupFocusRequesters = remember(groupKeys) {
        List(groupKeys.size) { FocusRequester() }
    }
    val coroutineScope = rememberCoroutineScope()
    var focusedArtworkUrl by remember { mutableStateOf("") }
    val initialArtworkUrl = pagingItems.itemSnapshotList.items.firstOrNull()?.imageUrl.orEmpty()
    val extractedAccent = rememberArtworkAccent(
        imageUrl = focusedArtworkUrl.ifBlank { initialArtworkUrl },
        enabled = focusedArtworkUrl.isNotBlank() || initialArtworkUrl.isNotBlank()
    )
    val accent by animateColorAsState(
        targetValue = extractedAccent,
        animationSpec = tween(durationMillis = 420),
        label = "discover-background-accent"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .aulamaTvBackground()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        colorStops = arrayOf(
                            0f to accent.copy(alpha = 0.13f),
                            0.44f to accent.copy(alpha = 0.045f),
                            0.76f to Color.Transparent
                        )
                    )
                )
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0f to accent.copy(alpha = 0.035f),
                            0.58f to Color.Transparent,
                            1f to accent.copy(alpha = 0.025f)
                        )
                    )
                )
        )

        LazyVerticalGrid(
            columns = GridCells.Fixed(5),
            state = gridState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 36.dp,
                end = 36.dp,
                top = 18.dp,
                bottom = 36.dp
            ),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item(
                key = "discover-filter-panel",
                span = { GridItemSpan(maxLineSpan) }
            ) {
                DiscoverFilterPanel(
                    groups = categoryGroups,
                    selectedValues = selectedValues,
                    groupFocusRequesters = groupFocusRequesters,
                    applyFocusRequester = applyFocusRequester,
                    firstCardFocusRequester = firstCardFocusRequester,
                    hasPendingChanges = hasPendingChanges,
                    accent = accent,
                    onSelect = onSelect,
                    onApply = onApply
                )
            }

            if (pagingItems.itemCount == 0) {
                item(
                    key = "discover-empty",
                    span = { GridItemSpan(maxLineSpan) }
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = androidx.compose.ui.res.stringResource(R.string.grid_no_data_tip),
                            color = AulamaTvColors.TextSecondary,
                            style = MaterialTheme.typography.titleLarge
                        )
                    }
                }
            }

            items(
                count = pagingItems.itemCount,
                key = pagingItems.itemKey { it.id }
            ) { index ->
                val anime = pagingItems[index] ?: return@items
                var focused by remember(anime.id) { mutableStateOf(false) }
                val scale by animateFloatAsState(
                    targetValue = if (focused) AulamaFocusScale else 1f,
                    animationSpec = tween(durationMillis = 200),
                    label = "discover-card-scale"
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(140f / 190f),
                    contentAlignment = Alignment.Center
                ) {
                    VideoCard(
                        modifier = Modifier
                            .fillMaxSize(1f / AulamaFocusScale)
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                            }
                            .focusProperties {
                                if (index < 5) up = applyFocusRequester
                            }
                            .run {
                                if (index == 0) focusRequester(firstCardFocusRequester) else this
                            }
                            .onFocusChanged { state ->
                                focused = state.isFocused || state.hasFocus
                                if (focused) focusedArtworkUrl = anime.imageUrl
                            },
                        imageUrl = anime.imageUrl,
                        title = anime.title,
                        subTitle = anime.currentEpisode,
                        focusScale = 1f,
                        onClick = { onOpen(anime) },
                        onKeyEvent = { event ->
                            if (event.key == Key.Back && gridState.firstVisibleItemIndex > 0) {
                                if (event.type == KeyEventType.KeyUp) {
                                    coroutineScope.launch {
                                        gridState.animateScrollToItem(0)
                                        kotlin.runCatching {
                                            firstCardFocusRequester.requestFocus()
                                        }
                                    }
                                }
                                true
                            } else {
                                false
                            }
                        }
                    )
                }
            }
        }
    }

    LaunchedEffect(pagingItems.itemCount) {
        val requester = groupFocusRequesters.firstOrNull() ?: applyFocusRequester
        kotlin.runCatching { requester.requestFocus() }
    }
}

@Composable
private fun DiscoverFilterPanel(
    groups: List<CategoryGroupWrapper>,
    selectedValues: Map<String, String>,
    groupFocusRequesters: List<FocusRequester>,
    applyFocusRequester: FocusRequester,
    firstCardFocusRequester: FocusRequester,
    hasPendingChanges: Boolean,
    accent: Color,
    onSelect: (key: String, value: String) -> Unit,
    onApply: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = androidx.compose.ui.res.stringResource(R.string.category_title),
                color = AulamaTvColors.TextPrimary,
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontSize = 30.sp,
                    lineHeight = 36.sp,
                    fontWeight = FontWeight.SemiBold
                )
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = androidx.compose.ui.res.stringResource(R.string.category_choose),
                color = accent.copy(alpha = 0.92f),
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = 16.sp,
                    lineHeight = 22.sp,
                    fontWeight = FontWeight.Medium
                )
            )
        }

        groups.forEachIndexed { groupIndex, wrapper ->
            val group = wrapper.group
            val selectedIndex = group.categories
                .indexOfFirst { it.value == selectedValues[group.key] }
                .coerceAtLeast(0)
            FilterOptionRow(
                group = group,
                selectedIndex = selectedIndex,
                groupFocusRequester = groupFocusRequesters[groupIndex],
                upRequester = groupFocusRequesters.getOrNull(groupIndex - 1),
                downRequester = groupFocusRequesters.getOrNull(groupIndex + 1)
                    ?: applyFocusRequester,
                accent = accent,
                onSelect = { value -> onSelect(group.key, value) }
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            ApplyFilterButton(
                modifier = Modifier
                    .focusRequester(applyFocusRequester)
                    .focusProperties {
                        groupFocusRequesters.lastOrNull()?.let { up = it }
                        down = firstCardFocusRequester
                    },
                accent = accent,
                emphasized = hasPendingChanges,
                onClick = onApply
            )
        }
    }
}

@Composable
private fun FilterOptionRow(
    group: VideoCategoryGroup.NormalCategoryGroup,
    selectedIndex: Int,
    groupFocusRequester: FocusRequester,
    upRequester: FocusRequester?,
    downRequester: FocusRequester,
    accent: Color,
    onSelect: (String) -> Unit
) {
    val rowState = rememberLazyListState(initialFirstVisibleItemIndex = selectedIndex)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = localizedText(group.name),
            modifier = Modifier.width(112.dp),
            color = AulamaTvColors.TextSecondary,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.titleMedium.copy(
                fontSize = 16.sp,
                lineHeight = 20.sp,
                fontWeight = FontWeight.Medium
            )
        )
        Spacer(Modifier.width(8.dp))
        LazyRow(
            state = rowState,
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 3.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            itemsIndexed(
                items = group.categories,
                key = { _, category -> category.value }
            ) { index, category ->
                FilterOption(
                    text = localizedText(category.label),
                    selected = index == selectedIndex,
                    accent = accent,
                    modifier = Modifier
                        .run {
                            if (index == selectedIndex) {
                                focusRequester(groupFocusRequester)
                            } else {
                                this
                            }
                        }
                        .focusProperties {
                            upRequester?.let { up = it }
                            down = downRequester
                        },
                    onClick = { onSelect(category.value) }
                )
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun FilterOption(
    text: String,
    selected: Boolean,
    accent: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.06f else 1f,
        animationSpec = tween(durationMillis = 170),
        label = "discover-filter-scale"
    )
    val textColor = when {
        focused -> Color(0xFF05070C)
        selected -> accent
        else -> AulamaTvColors.TextPrimary
    }

    Surface(
        onClick = onClick,
        modifier = modifier
            .widthIn(min = 88.dp, max = 196.dp)
            .height(42.dp)
            .onFocusChanged { focused = it.isFocused || it.hasFocus }
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        shape = ClickableSurfaceDefaults.shape(shape = AulamaCardShape),
        border = ClickableSurfaceDefaults.border(
            border = Border(
                BorderStroke(
                    width = if (selected) 1.5.dp else 1.dp,
                    color = if (selected) accent.copy(alpha = 0.9f)
                    else AulamaTvColors.Outline.copy(alpha = 0.58f)
                ),
                shape = AulamaCardShape
            ),
            focusedBorder = Border(
                BorderStroke(3.dp, accent),
                shape = AulamaCardShape
            )
        ),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) accent.copy(alpha = 0.13f) else Color.Transparent,
            focusedContainerColor = accent,
            contentColor = textColor,
            focusedContentColor = Color(0xFF05070C)
        )
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            if (selected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = textColor,
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = 10.dp)
                        .size(16.dp)
                )
            }
            Text(
                text = text,
                modifier = Modifier.padding(horizontal = 30.dp),
                color = textColor,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = 15.sp,
                    lineHeight = 18.sp,
                    fontWeight = FontWeight.SemiBold
                )
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ApplyFilterButton(
    accent: Color,
    emphasized: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.045f else 1f,
        animationSpec = tween(durationMillis = 170),
        label = "discover-apply-scale"
    )
    val contentColor = if (focused) Color(0xFF05070C) else AulamaTvColors.TextPrimary

    Surface(
        onClick = onClick,
        modifier = modifier
            .width(156.dp)
            .height(50.dp)
            .onFocusChanged { focused = it.isFocused || it.hasFocus }
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        shape = ClickableSurfaceDefaults.shape(shape = AulamaCardShape),
        border = ClickableSurfaceDefaults.border(
            border = Border(
                BorderStroke(
                    if (emphasized) 1.5.dp else 1.dp,
                    if (emphasized) accent else AulamaTvColors.Outline
                ),
                shape = AulamaCardShape
            ),
            focusedBorder = Border(
                BorderStroke(2.5.dp, accent),
                shape = AulamaCardShape
            )
        ),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (emphasized) accent.copy(alpha = 0.16f)
            else AulamaTvColors.Surface.copy(alpha = 0.72f),
            focusedContainerColor = accent,
            contentColor = contentColor,
            focusedContentColor = Color(0xFF05070C)
        )
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(19.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = androidx.compose.ui.res.stringResource(R.string.category_apply),
                color = contentColor,
                textAlign = TextAlign.Center,
                maxLines = 1,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = 16.sp,
                    lineHeight = 20.sp,
                    fontWeight = FontWeight.SemiBold
                )
            )
        }
    }
}
