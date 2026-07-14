package com.jing.sakura.compose.screen

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.ChangeCircle
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import coil.compose.AsyncImage
import com.jing.sakura.R
import com.jing.sakura.auth.AulamaAccount
import com.jing.sakura.category.AnimeCategoryActivity
import com.jing.sakura.compose.common.AulamaCardShape
import com.jing.sakura.compose.common.AulamaAccountAvatar
import com.jing.sakura.compose.common.AulamaAnimeBrandMark
import com.jing.sakura.compose.common.AulamaTvColors
import com.jing.sakura.compose.common.TvLanguagePreferences
import com.jing.sakura.compose.common.ChineseText
import com.jing.sakura.compose.common.TvLanguage
import com.jing.sakura.compose.common.localizedText
import com.jing.sakura.compose.common.ChangeSourceDialog
import com.jing.sakura.compose.common.ErrorTip
import com.jing.sakura.compose.common.HeroPreviewPlayer
import com.jing.sakura.compose.common.Loading
import com.jing.sakura.compose.common.rememberArtworkAccent
import com.jing.sakura.compose.common.rememberPosterImageRequest
import com.jing.sakura.compose.common.safelyRequestFocus
import com.jing.sakura.data.AnimeData
import com.jing.sakura.data.HomePageData
import com.jing.sakura.data.NamedValue
import com.jing.sakura.data.Resource
import com.jing.sakura.detail.DetailActivity
import com.jing.sakura.history.HistoryActivity
import com.jing.sakura.home.HomeViewModel
import com.jing.sakura.home.HeroPreviewState
import com.jing.sakura.search.SearchActivity
import com.jing.sakura.timeline.UpdateTimelineActivity
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.onStart
import java.util.Calendar
import kotlin.random.Random

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    account: AulamaAccount,
    isCheckingForUpdate: Boolean,
    onCheckForUpdate: () -> Unit,
    onLogout: () -> Unit
) {
    val context = LocalContext.current
    val languagePreferences = remember(context) { TvLanguagePreferences.get(context) }
    val language by languagePreferences.language.collectAsState()
    val homePageDataResource = viewModel.homePageData.collectAsState().value
    val recommendations = viewModel.recommendations.collectAsState().value
    val syncedRows = viewModel.syncedRows.collectAsState().value
    val syncedTodayUpdates = viewModel.todayUpdates.collectAsState().value
    val theaterItems = viewModel.theaterItems.collectAsState().value
    val heroDescriptions = viewModel.heroDescriptions.collectAsState().value
    val displayData: HomePageData? = when (homePageDataResource) {
        is Resource.Success -> homePageDataResource.data
        is Resource.Loading -> viewModel.lastHomePageData
        is Resource.Error -> null
    }

    val sourceRows = displayData?.seriesList.orEmpty()
    val todayName = remember { WEEKDAY_SECTION_NAMES[currentWeekdayIndex()] }
    val dailySeed = remember {
        val calendar = Calendar.getInstance()
        calendar.get(Calendar.YEAR) * 400 + calendar.get(Calendar.DAY_OF_YEAR)
    }
    val todayUpdates = remember(sourceRows, syncedTodayUpdates, todayName) {
        syncedTodayUpdates.takeIf(List<AnimeData>::isNotEmpty)
            ?: sourceRows.firstOrNull { it.name == todayName }?.value.orEmpty()
    }
    val featured = remember(
        recommendations,
        todayUpdates,
        theaterItems,
        sourceRows,
        dailySeed
    ) {
        val random = Random(dailySeed)
        buildList {
            addAll(recommendations.distinctAnime().shuffled(random).take(3))
            addAll(todayUpdates.distinctAnime().shuffled(random).take(3))
            addAll(theaterItems.distinctAnime().shuffled(random).take(2))
            addAll(sourceRows.flatMap { it.value }.distinctAnime().shuffled(random))
        }.distinctAnime().take(7)
    }
    val featuredIdentityTokens = remember(featured) {
        featured.flatMapTo(hashSetOf(), AnimeData::identityTokens)
    }
    val rows = remember(
        sourceRows,
        recommendations,
        syncedRows,
        todayUpdates,
        theaterItems,
        featuredIdentityTokens
    ) {
        buildList {
            val recommendationRow = recommendations.distinctAnime()
                .filterNot { anime ->
                    anime.identityTokens().any(featuredIdentityTokens::contains)
                }
            if (recommendationRow.isNotEmpty()) {
                add(NamedValue("為你推薦", recommendationRow))
            }
            addAll(
                syncedRows
                    .filterNot {
                        (todayUpdates.isNotEmpty() && it.name.isGenericTvSection()) ||
                            (theaterItems.isNotEmpty() && it.name.isGenericTheaterSection())
                    }
                    .map { it.copy(value = it.value.distinctAnime()) }
            )
            todayUpdates.takeIf(List<AnimeData>::isNotEmpty)
                ?.let { add(NamedValue("今日更新", it.distinctAnime())) }
            if (theaterItems.isNotEmpty()) {
                add(NamedValue("最新劇場版", theaterItems.distinctAnime()))
            }
            sourceRows
                .filterNot {
                    it.name in setOf("為你推薦", "為你推介", "为你推荐") ||
                        it.name in WEEKDAY_SECTION_NAMES
                }
                .filterNot {
                    (todayUpdates.isNotEmpty() && it.name.isGenericTvSection()) ||
                        (theaterItems.isNotEmpty() && it.name.isGenericTheaterSection())
                }
                .filter { it.value.isNotEmpty() }
                .forEach { add(it.copy(value = it.value.distinctAnime())) }
        }.distinctBy { it.name }
    }
    var heroIndex by remember { mutableStateOf(0) }
    var focusedHero by remember { mutableStateOf<AnimeData?>(null) }
    var autoRotateHero by remember { mutableStateOf(true) }
    var showSourceDialog by remember { mutableStateOf(false) }
    var showAccountDialog by remember { mutableStateOf(false) }
    var requestedInitialFocus by remember { mutableStateOf(false) }
    var focusedRowIndex by remember { mutableStateOf<Int?>(null) }
    val rowSelections = remember { mutableStateMapOf<String, Int>() }
    var previewIdle by remember { mutableStateOf(false) }
    var previewArmed by remember { mutableStateOf(false) }
    var previewFirstFrameReady by remember { mutableStateOf(false) }
    val heroPreviewState = viewModel.heroPreviewState.collectAsState().value
    val cardFocusEvents = remember {
        MutableSharedFlow<FocusedCardEvent>(
            extraBufferCapacity = 1,
            onBufferOverflow = BufferOverflow.DROP_OLDEST
        )
    }
    val interactionEvents = remember {
        MutableSharedFlow<Unit>(
            extraBufferCapacity = 1,
            onBufferOverflow = BufferOverflow.DROP_OLDEST
        )
    }
    val baseHero = focusedHero ?: featured.getOrNull(heroIndex)
    val heroDescription = baseHero?.let { heroDescriptions[it.id] }
    val hero = remember(baseHero, heroDescription) {
        baseHero?.let { anime ->
            if (!heroDescription.isNullOrBlank()) {
                anime.copy(description = heroDescription)
            } else {
                anime
            }
        }
    }
    val extractedHeroAccent = rememberArtworkAccent(hero?.imageUrl.orEmpty())
    val heroAccent = extractedHeroAccent
    val topHomeFocus = remember { FocusRequester() }
    val heroActionFocus = remember { FocusRequester() }
    val heroHeight by animateDpAsState(
        targetValue = if (focusedRowIndex == null) 304.dp else 146.dp,
        animationSpec = tween(320),
        label = "home-hero-height"
    )
    val rowShelfTop by animateDpAsState(
        targetValue = if (focusedRowIndex == null) 404.dp else 218.dp,
        animationSpec = tween(320),
        label = "home-row-shelf-top"
    )
    val readyPreview = (heroPreviewState as? HeroPreviewState.Ready)?.spec
    val previewActive = previewArmed && previewFirstFrameReady && readyPreview != null

    LaunchedEffect(featured) {
        if (heroIndex !in featured.indices) heroIndex = 0
        if (focusedHero != null && featured.none { it.id == focusedHero?.id }) {
            focusedHero = null
        }
    }
    LaunchedEffect(featured.size, autoRotateHero) {
        while (autoRotateHero && featured.size > 1) {
            delay(8_000)
            heroIndex = (heroIndex + 1) % featured.size
        }
    }
    LaunchedEffect(cardFocusEvents) {
        cardFocusEvents.collectLatest { event ->
            autoRotateHero = false
            if (focusedRowIndex != event.rowIndex) {
                focusedRowIndex = event.rowIndex
            }
            if (previewIdle) previewIdle = false
            delay(220)
            focusedHero = event.video
            heroIndex = featured.indexOfFirst { it.id == event.video.id }
                .takeIf { it >= 0 }
                ?: heroIndex
        }
    }
    LaunchedEffect(hero?.id) {
        val selected = hero ?: return@LaunchedEffect
        delay(260)
        viewModel.loadHeroDescription(selected)
    }
    LaunchedEffect(hero?.id, focusedRowIndex, interactionEvents) {
        if (focusedRowIndex == null) {
            viewModel.cancelHeroPreview()
            previewIdle = false
            previewArmed = false
            previewFirstFrameReady = false
            return@LaunchedEffect
        }
        interactionEvents
            .onStart { emit(Unit) }
            .collectLatest {
                viewModel.cancelHeroPreview()
                previewIdle = false
                previewArmed = false
                previewFirstFrameReady = false
                val selected = hero ?: return@collectLatest
                delay(1_000)
                previewIdle = true
                delay(9_000)
                viewModel.prepareHeroPreview(selected)
                previewArmed = true
            }
    }
    LaunchedEffect(previewActive, readyPreview?.key, focusedRowIndex) {
        if (!previewActive || focusedRowIndex == null) return@LaunchedEffect
        delay(60_000)
        previewIdle = true
        previewArmed = false
        previewFirstFrameReady = false
        viewModel.cancelHeroPreview()
    }
    LaunchedEffect(rows.size, hero?.id) {
        if (!requestedInitialFocus && hero != null) {
            requestedInitialFocus = true
            focusedRowIndex = null
            delay(180)
            heroActionFocus.safelyRequestFocus("home-hero-initial")
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AulamaTvColors.Background)
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) {
                    interactionEvents.tryEmit(Unit)
                    previewIdle = false
                    previewArmed = false
                    previewFirstFrameReady = false
                    viewModel.cancelHeroPreview()
                }
                false
            }
    ) {
        readyPreview?.let { spec ->
            HeroPreviewPlayer(
                spec = spec,
                onReady = { previewFirstFrameReady = true },
                onError = {
                    previewFirstFrameReady = false
                    previewArmed = false
                    viewModel.cancelHeroPreview()
                },
                onEnded = {
                    previewIdle = true
                    previewArmed = false
                    previewFirstFrameReady = false
                    viewModel.cancelHeroPreview()
                },
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = if (previewActive) 1f else 0f }
            )
        }
        CinematicBackdrop(
            hero = hero,
            accent = heroAccent,
            revealArtwork = previewIdle,
            previewActive = previewActive
        )

        HeroPanel(
            anime = hero,
            accent = heroAccent,
            sectionLabel = focusedRowIndex
                ?.let(rows::getOrNull)
                ?.name
                ?: "精選推介",
            height = heroHeight,
            compact = focusedRowIndex != null,
            featuredCount = featured.size,
            selectedIndex = heroIndex,
            actionFocusRequester = heroActionFocus,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(top = 72.dp),
            onMove = { delta ->
                if (featured.isNotEmpty()) {
                    autoRotateHero = false
                    focusedHero = null
                    heroIndex = (heroIndex + delta + featured.size) % featured.size
                }
            },
            onFocused = {
                focusedRowIndex = null
                if (previewIdle) previewIdle = false
            },
            onOpen = {
                hero?.let { DetailActivity.startActivity(context, it.id, it.sourceId) }
            }
        )

        if (rows.isNotEmpty()) {
            val activeRowIndex = (focusedRowIndex ?: 0).coerceIn(rows.indices)
            val activeRow = rows[activeRowIndex]
            val nextRow = rows.getOrNull(activeRowIndex + 1)
            val savedSelectedIndex = rowSelections[activeRow.name]
                ?.takeIf { it in activeRow.value.indices }
                ?: 0
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clipToBounds()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .offset(y = rowShelfTop)
                ) {
                    MediaRow(
                        title = activeRow.name,
                        videos = activeRow.value,
                        previewActive = previewActive,
                        initialSelectedIndex = savedSelectedIndex,
                        onSelectionChanged = { index ->
                            rowSelections[activeRow.name] = index
                        },
                        onFocused = { video ->
                            cardFocusEvents.tryEmit(FocusedCardEvent(activeRowIndex, video))
                        },
                        onMoveRow = { delta ->
                            val target = activeRowIndex + delta
                            when {
                                target in rows.indices -> {
                                    focusedRowIndex = target
                                    interactionEvents.tryEmit(Unit)
                                    true
                                }

                                delta > 0 -> true
                                else -> false
                            }
                        },
                        onOpen = { video ->
                            DetailActivity.startActivity(context, video.id, video.sourceId)
                        },
                        onRefresh = {
                            viewModel.loadData(false)
                            viewModel.refreshSyncedContent()
                        }
                    )
                    if (focusedRowIndex != null) {
                        nextRow?.let { NextRowHeading(title = it.name) }
                    }
                }
            }
        }

        HomeTopBar(
            modifier = Modifier.align(Alignment.TopCenter),
            account = account,
            homeFocusRequester = topHomeFocus,
            onAnyItemFocused = {
                focusedRowIndex = null
                if (previewIdle) previewIdle = false
            },
            showSource = viewModel.getAllSources().size > 1,
            onHome = {
                topHomeFocus.safelyRequestFocus("home-nav")
            },
            onDiscover = {
                AnimeCategoryActivity.startActivity(context, viewModel.currentSourceId)
            },
            onTimeline = {
                UpdateTimelineActivity.startActivity(context, viewModel.currentSourceId)
            },
            onHistory = { HistoryActivity.startActivity(context) },
            onSource = { showSourceDialog = true },
            onSearch = { SearchActivity.startActivity(context, viewModel.currentSourceId) },
            onAccount = { showAccountDialog = true }
        )

        if (homePageDataResource is Resource.Loading && !homePageDataResource.silent && displayData == null) {
            Loading(text = "")
        }
        if (homePageDataResource is Resource.Error) {
            ErrorTip(message = "暫時未能載入內容，請稍後再試") {
                viewModel.loadData(false)
            }
        }
    }

    if (showSourceDialog) {
        ChangeSourceDialog(
            allSources = viewModel.getAllSources(),
            currentSourceId = viewModel.currentSourceId,
            onDismissRequest = { showSourceDialog = false },
            onChangeSource = { sourceId ->
                showSourceDialog = false
                viewModel.changeSource(sourceId)
            }
        )
    }
    if (showAccountDialog) {
        AccountDialog(
            account = account,
            language = language,
            isCheckingForUpdate = isCheckingForUpdate,
            currentVersion = com.jing.sakura.BuildConfig.VERSION_NAME,
            onLanguageChange = languagePreferences::setLanguage,
            onCheckForUpdate = onCheckForUpdate,
            onDismiss = { showAccountDialog = false },
            onLogout = {
                showAccountDialog = false
                onLogout()
            }
        )
    }
}

@Composable
private fun CinematicBackdrop(
    hero: AnimeData?,
    accent: Color,
    revealArtwork: Boolean,
    previewActive: Boolean
) {
    val backdropRequest = rememberPosterImageRequest(
        imageUrl = hero?.imageUrl.orEmpty(),
        widthPx = 960,
        heightPx = 1360
    )
    val artworkAlpha = when {
        previewActive -> 0f
        revealArtwork -> 0.92f
        else -> 0.76f
    }
    Box(modifier = Modifier.fillMaxSize()) {
        if (!hero?.imageUrl.isNullOrBlank()) {
            AsyncImage(
                model = backdropRequest,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                alignment = Alignment.TopEnd,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        alpha = artworkAlpha
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
    )
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.horizontalGradient(
                    colorStops = if (previewActive) {
                        arrayOf(
                            0f to AulamaTvColors.Background.copy(alpha = 0.92f),
                            0.34f to AulamaTvColors.Background.copy(alpha = 0.86f),
                            0.48f to AulamaTvColors.Background.copy(alpha = 0.62f),
                            0.62f to AulamaTvColors.Background.copy(alpha = 0.24f),
                            0.76f to Color.Transparent,
                            1f to Color.Transparent
                        )
                    } else {
                        arrayOf(
                            0f to AulamaTvColors.Background,
                            0.42f to AulamaTvColors.Background,
                            0.52f to AulamaTvColors.Background.copy(alpha = 0.96f),
                            0.60f to AulamaTvColors.Background.copy(alpha = 0.76f),
                            0.69f to AulamaTvColors.Background.copy(alpha = 0.42f),
                            0.78f to AulamaTvColors.Background.copy(alpha = 0.14f),
                            0.88f to AulamaTvColors.Background.copy(alpha = 0.03f),
                            1f to Color.Transparent
                        )
                    }
                )
            )
            .background(
                Brush.verticalGradient(
                    colorStops = arrayOf(
                        0f to AulamaTvColors.Background.copy(
                            alpha = if (previewActive) 0.18f else 0.32f
                        ),
                        0.58f to Color.Transparent,
                        1f to AulamaTvColors.Background.copy(
                            alpha = if (previewActive) 0.62f else 1f
                        )
                    )
                )
            )
    )
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun HeroPanel(
    anime: AnimeData?,
    accent: Color,
    sectionLabel: String,
    height: Dp,
    compact: Boolean,
    featuredCount: Int,
    selectedIndex: Int,
    actionFocusRequester: FocusRequester,
    modifier: Modifier = Modifier,
    onMove: (Int) -> Unit,
    onFocused: () -> Unit,
    onOpen: () -> Unit
) {
    var actionFocused by remember { mutableStateOf(false) }
    val actionContentColor = if (actionFocused) Color(0xFF080A0F) else Color.White
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .padding(horizontal = 42.dp, vertical = if (compact) 10.dp else 18.dp)
    ) {
        if (anime == null) return@Box
        Column(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .widthIn(max = 510.dp),
            verticalArrangement = Arrangement.Center
        ) {
            if (!compact) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Spacer(
                        Modifier
                            .size(width = 22.dp, height = 3.dp)
                            .background(accent, RoundedCornerShape(2.dp))
                    )
                    Spacer(Modifier.width(9.dp))
                    androidx.tv.material3.Text(
                        text = localizedText(sectionLabel),
                        color = accent,
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
                Spacer(Modifier.height(10.dp))
            }
            androidx.tv.material3.Text(
                text = localizedText(anime.title),
                color = if (compact) accent else AulamaTvColors.TextPrimary,
                maxLines = if (compact) 1 else 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.displaySmall.copy(
                    fontSize = if (compact) 29.sp else 34.sp,
                    lineHeight = if (compact) 34.sp else 38.sp,
                    fontWeight = FontWeight.Bold
                )
            )
            Spacer(Modifier.height(if (compact) 7.dp else 10.dp))
            HeroMetadata(anime = anime, accent = accent)
            if (anime.description.isNotBlank()) {
                Column {
                    Spacer(Modifier.height(if (compact) 5.dp else 10.dp))
                    androidx.tv.material3.Text(
                        text = localizedText(anime.description),
                        color = AulamaTvColors.TextSecondary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontSize = if (compact) 14.sp else 16.sp,
                            lineHeight = if (compact) 19.sp else 21.sp
                        )
                    )
                }
            }
            if (!compact) {
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                Button(
                    onClick = onOpen,
                    modifier = Modifier
                        .height(48.dp)
                        .focusRequester(actionFocusRequester)
                        .onFocusChanged {
                            actionFocused = it.isFocused
                            if (it.isFocused) onFocused()
                        }
                        .onPreviewKeyEvent { event ->
                            if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                            when (event.key) {
                                Key.DirectionLeft -> {
                                    onMove(-1)
                                    true
                                }
                                Key.DirectionRight -> {
                                    onMove(1)
                                    true
                                }
                                else -> false
                            }
                        },
                    scale = ButtonDefaults.scale(focusedScale = 1.055f),
                    shape = ButtonDefaults.shape(shape = AulamaCardShape),
                    border = ButtonDefaults.border(
                        border = Border(BorderStroke(1.dp, Color.White.copy(alpha = 0.18f))),
                        focusedBorder = Border(BorderStroke(2.dp, Color.White))
                    ),
                    colors = ButtonDefaults.colors(
                        containerColor = Color.White.copy(alpha = 0.14f),
                        contentColor = Color.White,
                        focusedContainerColor = Color.White,
                        focusedContentColor = Color(0xFF080A0F)
                    ),
                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 0.dp)
                ) {
                    Icon(
                        Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = actionContentColor,
                        modifier = Modifier
                            .size(23.dp)
                            .align(Alignment.CenterVertically)
                    )
                    Spacer(Modifier.width(7.dp))
                    androidx.tv.material3.Text(
                        text = localizedText("查看詳情"),
                        color = actionContentColor,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontSize = 16.sp,
                            lineHeight = 18.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        modifier = Modifier.align(Alignment.CenterVertically)
                    )
                }
                if (featuredCount > 1) {
                    Spacer(Modifier.width(18.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        repeat(featuredCount) { index ->
                            Box(
                                modifier = Modifier
                                    .size(width = if (index == selectedIndex) 22.dp else 7.dp, height = 7.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (index == selectedIndex) accent
                                        else Color.White.copy(alpha = 0.28f)
                                    )
                            )
                        }
                    }
                }
                }
            }
        }

    }
}

@Composable
private fun HeroMetadata(anime: AnimeData, accent: Color) {
    val displayEpisode = localizedText(anime.currentEpisode)
    val displayTags = localizedText(anime.tags)
    val episode = remember(displayEpisode) {
        displayEpisode.trim().takeIf {
            it.length <= 18 && it.none { character -> character == ',' || character == '，' }
        }
    }
    val tags = remember(displayTags) {
        displayTags.split('、', ',', '，', '/', ' ')
            .map(String::trim)
            .filter(String::isNotBlank)
            .take(2)
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        if (anime.year.isNotBlank()) {
            MetadataLabel(text = anime.year, color = accent)
        }
        if (episode != null) {
            MetadataLabel(
                text = episode,
                color = if (anime.year.isBlank()) accent else Color.White.copy(alpha = 0.80f)
            )
        }
        tags.forEach { MetadataLabel(text = it, color = Color.White.copy(alpha = 0.80f)) }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun MetadataLabel(text: String, color: Color) {
    androidx.tv.material3.Text(
        text = text,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        color = color,
        style = MaterialTheme.typography.labelLarge.copy(
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold
        ),
        modifier = Modifier
            .widthIn(max = 170.dp)
            .clip(RoundedCornerShape(5.dp))
            .background(Color.Black.copy(alpha = 0.30f))
            .padding(horizontal = 9.dp, vertical = 5.dp)
    )
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun MediaRow(
    title: String,
    videos: List<AnimeData>,
    previewActive: Boolean,
    initialSelectedIndex: Int,
    onSelectionChanged: (Int) -> Unit,
    onFocused: (AnimeData) -> Unit,
    onMoveRow: (Int) -> Boolean,
    onOpen: (AnimeData) -> Unit,
    onRefresh: () -> Unit
) {
    if (videos.isEmpty()) return

    val videoIdentity = remember(videos) {
        videos.joinToString(separator = "|") { "${it.sourceId}:${it.id}" }
    }
    val loopEnabled = videos.size > 1
    val loopCopies = 101
    val loopStart = remember(videoIdentity) {
        if (loopEnabled) {
            videos.size * (loopCopies / 2) + initialSelectedIndex
        } else {
            initialSelectedIndex
        }
    }
    val rowState = remember(videoIdentity) {
        LazyListState(firstVisibleItemIndex = loopStart)
    }
    var selectedVirtualIndex by remember(videoIdentity) {
        mutableStateOf(loopStart)
    }
    var rowFocused by remember { mutableStateOf(false) }
    var dimUnselected by remember { mutableStateOf(false) }
    val cardStridePx = with(LocalDensity.current) { 166.dp.toPx() }
    val moveEvents = remember(videoIdentity) {
        MutableSharedFlow<Int>(
            extraBufferCapacity = 2,
            onBufferOverflow = BufferOverflow.DROP_OLDEST
        )
    }
    val selectedVideo = videos[selectedVirtualIndex % videos.size]
    val selectedAccent = rememberArtworkAccent(
        imageUrl = selectedVideo.imageUrl,
        enabled = rowFocused
    )

    LaunchedEffect(rowFocused, selectedVirtualIndex, videoIdentity) {
        if (!rowFocused) {
            dimUnselected = false
            return@LaunchedEffect
        }
        dimUnselected = false
        onFocused(selectedVideo)
        delay(3_000)
        dimUnselected = true
    }

    LaunchedEffect(videoIdentity, rowState, cardStridePx) {
        moveEvents.collect { delta ->
            if (!loopEnabled) return@collect
            dimUnselected = false
            selectedVirtualIndex += delta
            onSelectionChanged(selectedVirtualIndex % videos.size)
            rowState.animateScrollBy(
                value = delta * cardStridePx,
                animationSpec = tween(
                    durationMillis = 165,
                    easing = LinearOutSlowInEasing
                )
            )
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp, bottom = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 42.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            androidx.tv.material3.Text(
                text = localizedText(title),
                color = AulamaTvColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontSize = 22.sp,
                    lineHeight = 27.sp,
                    fontWeight = FontWeight.SemiBold
                ),
                modifier = Modifier.weight(1f)
            )
            androidx.tv.material3.Text(
                text = localizedText("${videos.size} 套"),
                color = AulamaTvColors.TextSecondary,
                style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp)
            )
        }
        Spacer(Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .requiredHeight(220.dp)
                .onFocusChanged { state ->
                    rowFocused = state.isFocused || state.hasFocus
                }
                .onPreviewKeyEvent { event ->
                    when {
                        event.type == KeyEventType.KeyDown && event.key == Key.DirectionRight -> {
                            dimUnselected = false
                            if (loopEnabled) moveEvents.tryEmit(1)
                            true
                        }
                        event.type == KeyEventType.KeyDown && event.key == Key.DirectionLeft -> {
                            dimUnselected = false
                            if (loopEnabled) moveEvents.tryEmit(-1)
                            true
                        }
                        event.type == KeyEventType.KeyDown && event.key == Key.DirectionUp -> {
                            dimUnselected = false
                            onMoveRow(-1)
                        }
                        event.type == KeyEventType.KeyDown && event.key == Key.DirectionDown -> {
                            dimUnselected = false
                            onMoveRow(1)
                        }
                        event.type == KeyEventType.KeyDown && event.key == Key.Menu -> {
                            onRefresh()
                            true
                        }
                        event.type == KeyEventType.KeyUp &&
                            (event.key == Key.DirectionCenter || event.key == Key.Enter) -> {
                            onOpen(selectedVideo)
                            true
                        }
                        event.key == Key.DirectionCenter || event.key == Key.Enter -> true
                        else -> false
                    }
                }
                .focusable()
        ) {
            LazyRow(
                state = rowState,
                contentPadding = PaddingValues(horizontal = 42.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                val itemCount = if (loopEnabled) videos.size * loopCopies else videos.size
                items(
                    count = itemCount,
                    key = { itemIndex -> "$itemIndex:${videos[itemIndex % videos.size].id}" }
                ) { itemIndex ->
                    val video = videos[itemIndex % videos.size]
                    val cardAlpha by animateFloatAsState(
                        targetValue = when {
                            !rowFocused || itemIndex == selectedVirtualIndex -> 1f
                            previewActive -> 0.14f
                            !dimUnselected -> 1f
                            else -> 0.52f
                        },
                        animationSpec = tween(
                            durationMillis = if (previewActive) 320 else 620,
                            easing = FastOutSlowInEasing
                        ),
                        label = "home-row-card-alpha"
                    )
                    CarouselPoster(
                        imageUrl = video.imageUrl,
                        title = video.title,
                        subTitle = video.currentEpisode,
                        showLabels = rowFocused,
                        modifier = Modifier
                            .requiredSize(width = 148.dp, height = 208.dp)
                            .graphicsLayer { alpha = cardAlpha }
                    )
                }
            }
            if (rowFocused) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(start = 42.dp, top = 6.dp)
                        .requiredSize(width = 148.dp, height = 208.dp)
                        .border(
                            BorderStroke(2.dp, selectedAccent),
                            CarouselCardShape
                        )
                )
            }
        }
    }
}

@Composable
private fun NextRowHeading(title: String) {
    androidx.tv.material3.Text(
        text = localizedText(title),
        color = AulamaTvColors.TextPrimary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        style = MaterialTheme.typography.headlineSmall.copy(
            fontSize = 22.sp,
            lineHeight = 27.sp,
            fontWeight = FontWeight.SemiBold
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 42.dp, top = 8.dp, end = 42.dp)
    )
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun CarouselPoster(
    imageUrl: String,
    title: String,
    subTitle: String,
    showLabels: Boolean,
    modifier: Modifier = Modifier
) {
    val displayTitle = localizedText(title)
    val displaySubtitle = localizedText(subTitle)
    val posterRequest = rememberPosterImageRequest(imageUrl = imageUrl)
    Box(
        modifier = modifier
            .border(
                BorderStroke(1.dp, AulamaTvColors.Outline.copy(alpha = 0.72f)),
                CarouselCardShape
            )
            .clip(CarouselCardShape)
    ) {
        AsyncImage(
            model = posterRequest,
            contentDescription = displayTitle,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        if (showLabels) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.Transparent,
                                Color(0x18000000),
                                Color(0x66000000),
                                Color(0xF2050810)
                            )
                        )
                    )
            )
        }
        if (showLabels && displaySubtitle.isNotEmpty()) {
            androidx.tv.material3.Text(
                text = displaySubtitle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = Color.White,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                ),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(9.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(Color(0xCC080B12))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
        if (showLabels) {
            androidx.tv.material3.Text(
                text = displayTitle,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = AulamaTvColors.TextPrimary,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = 16.sp,
                    lineHeight = 19.sp,
                    fontWeight = FontWeight.SemiBold
                ),
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 11.dp)
            )
        }
    }
}

private val CarouselCardShape = RoundedCornerShape(12.dp)

private data class FocusedCardEvent(
    val rowIndex: Int,
    val video: AnimeData
)

private fun String.isGenericTvSection(): Boolean =
    replace(" ", "").lowercase() in setOf(
        "tv動畫", "tv动画", "tv番", "tv番劇", "tv番剧", "tv番組", "tv番组",
        "tv節目", "tv节目", "電視動畫", "电视动画"
    )

private fun String.isGenericTheaterSection(): Boolean =
    replace(" ", "") in setOf(
        "劇場版", "剧场版", "劇場番", "剧场番", "劇場番組", "剧场番组",
        "劇場動畫", "剧场动画", "電影", "电影"
    )

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun HomeTopBar(
    modifier: Modifier,
    account: AulamaAccount,
    homeFocusRequester: FocusRequester,
    onAnyItemFocused: () -> Unit,
    showSource: Boolean,
    onHome: () -> Unit,
    onDiscover: () -> Unit,
    onTimeline: () -> Unit,
    onHistory: () -> Unit,
    onSource: () -> Unit,
    onSearch: () -> Unit,
    onAccount: () -> Unit
) {
    val navItems = remember(showSource) {
        buildList {
            add(TopNavItem("首頁", Icons.Default.Home, onHome))
            add(TopNavItem("發現", Icons.Default.Category, onDiscover))
            add(TopNavItem("時間表", Icons.Default.CalendarMonth, onTimeline))
            add(TopNavItem("我的片庫", Icons.Default.History, onHistory))
            if (showSource) add(TopNavItem("線路", Icons.Default.ChangeCircle, onSource))
        }
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(72.dp)
            .background(
                Brush.verticalGradient(
                    colorStops = arrayOf(
                        0f to AulamaTvColors.Background,
                        0.82f to AulamaTvColors.Background,
                        1f to Color.Transparent
                    )
                )
            )
            .padding(horizontal = 34.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.width(170.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                AulamaAnimeBrandMark(height = 40.dp)
            }

            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                navItems.forEachIndexed { index, item ->
                    TopNavButton(
                        item = item,
                        onFocused = onAnyItemFocused,
                        modifier = Modifier
                            .run {
                                if (index == 0) focusRequester(homeFocusRequester) else this
                            }
                    )
                    if (index != navItems.lastIndex) Spacer(Modifier.width(3.dp))
                }
            }

            Row(
                modifier = Modifier.width(170.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TopIconButton(
                    icon = Icons.Default.Search,
                    contentDescription = "搜尋",
                    onClick = onSearch,
                    onFocused = onAnyItemFocused
                )
                Spacer(Modifier.width(8.dp))
                TopAccountButton(
                    account = account,
                    onClick = onAccount,
                    onFocused = onAnyItemFocused
                )
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TopNavButton(
    item: TopNavItem,
    onFocused: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = item.onClick,
        modifier = modifier
            .height(40.dp)
            .onFocusChanged { if (it.isFocused) onFocused() },
        contentPadding = PaddingValues(horizontal = 11.dp, vertical = 6.dp),
        shape = ButtonDefaults.shape(shape = RoundedCornerShape(7.dp)),
        scale = ButtonDefaults.scale(focusedScale = 1.045f),
        colors = ButtonDefaults.colors(
            containerColor = Color.Transparent,
            contentColor = AulamaTvColors.TextSecondary,
            focusedContainerColor = Color.White,
            focusedContentColor = Color(0xFF07090E)
        ),
        border = ButtonDefaults.border(
            border = Border(BorderStroke(1.dp, Color.Transparent)),
            focusedBorder = Border(BorderStroke(2.dp, Color.White))
        )
    ) {
        Icon(item.icon, contentDescription = null, modifier = Modifier.size(17.dp))
        Spacer(Modifier.width(5.dp))
        androidx.tv.material3.Text(
            text = localizedText(item.label),
            maxLines = 1,
            style = MaterialTheme.typography.titleSmall.copy(
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TopAccountButton(
    account: AulamaAccount,
    onClick: () -> Unit,
    onFocused: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .size(46.dp)
            .onFocusChanged { if (it.isFocused) onFocused() },
        contentPadding = PaddingValues(6.dp),
        shape = ButtonDefaults.shape(shape = CircleShape),
        scale = ButtonDefaults.scale(focusedScale = 1.07f),
        colors = ButtonDefaults.colors(
            containerColor = AulamaTvColors.SurfaceRaised,
            contentColor = AulamaTvColors.TextPrimary,
            focusedContainerColor = Color.White,
            focusedContentColor = Color(0xFF07090E)
        ),
        border = ButtonDefaults.border(
            border = Border(BorderStroke(1.dp, AulamaTvColors.Outline)),
            focusedBorder = Border(BorderStroke(2.dp, Color.White))
        )
    ) {
        AulamaAccountAvatar(account = account, modifier = Modifier.size(32.dp))
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TopIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    onFocused: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .size(42.dp)
            .onFocusChanged { if (it.isFocused) onFocused() },
        contentPadding = PaddingValues(9.dp),
        shape = ButtonDefaults.shape(shape = CircleShape),
        scale = ButtonDefaults.scale(focusedScale = 1.06f),
        colors = ButtonDefaults.colors(
            containerColor = Color.White.copy(alpha = 0.08f),
            contentColor = AulamaTvColors.TextPrimary,
            focusedContainerColor = Color.White,
            focusedContentColor = Color(0xFF07090E)
        ),
        border = ButtonDefaults.border(
            border = Border(BorderStroke(1.dp, Color.White.copy(alpha = 0.12f))),
            focusedBorder = Border(BorderStroke(2.dp, Color.White))
        )
    ) {
        Icon(icon, contentDescription = contentDescription, modifier = Modifier.size(21.dp))
    }
}

private data class TopNavItem(
    val label: String,
    val icon: ImageVector,
    val onClick: () -> Unit
)

private val WEEKDAY_SECTION_NAMES = listOf(
    "週一", "週二", "週三", "週四", "週五", "週六", "週日"
)

private fun currentWeekdayIndex(): Int = Calendar.getInstance().run {
    ((get(Calendar.DAY_OF_WEEK) + 5) % 7).coerceIn(0, 6)
}

private fun List<AnimeData>.distinctAnime(): List<AnimeData> {
    val seenTitles = hashSetOf<String>()
    val seenArtwork = hashSetOf<String>()
    return filter { anime ->
        val title = ChineseText.convert(anime.title, TvLanguage.Traditional)
            .lowercase()
            .replace(ANIME_IDENTITY_NOISE, "")
        val artwork = anime.imageUrl
            .substringBefore('?')
            .trimEnd('/')
            .substringAfterLast('/')
            .lowercase()
        val duplicate = (title.isNotBlank() && title in seenTitles) ||
            (artwork.isNotBlank() && artwork in seenArtwork)
        if (!duplicate) {
            if (title.isNotBlank()) seenTitles += title
            if (artwork.isNotBlank()) seenArtwork += artwork
        }
        !duplicate
    }
}

private fun AnimeData.identityTokens(): List<String> = buildList {
    val normalizedTitle = ChineseText.convert(title, TvLanguage.Traditional)
        .lowercase()
        .replace(ANIME_IDENTITY_NOISE, "")
    val normalizedArtwork = imageUrl
        .substringBefore('?')
        .trimEnd('/')
        .substringAfterLast('/')
        .lowercase()
    if (normalizedTitle.isNotBlank()) add("title:$normalizedTitle")
    if (normalizedArtwork.isNotBlank()) add("art:$normalizedArtwork")
    if (isEmpty()) add("id:${sourceId.trim()}:${id.trim()}")
}

private val ANIME_IDENTITY_NOISE = Regex("[\\p{P}\\p{S}\\s]+")
