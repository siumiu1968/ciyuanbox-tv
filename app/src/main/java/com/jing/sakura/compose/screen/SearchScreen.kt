package com.jing.sakura.compose.screen

import android.Manifest
import android.graphics.Bitmap
import android.graphics.Color
import android.speech.SpeechRecognizer
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement.spacedBy
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
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
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.tv.material3.Border
import androidx.tv.material3.ButtonScale
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ClickableSurfaceScale
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.IconButton
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import coil.compose.AsyncImage
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.jing.sakura.R
import com.jing.sakura.compose.common.ConfirmDeleteDialog
import com.jing.sakura.compose.common.CustomTextField
import com.jing.sakura.compose.common.FocusGroup
import com.jing.sakura.compose.common.AulamaCardShape
import com.jing.sakura.compose.common.AulamaFocusScale
import com.jing.sakura.compose.common.AulamaIconButton
import com.jing.sakura.compose.common.AulamaPageHeader
import com.jing.sakura.compose.common.AulamaSectionHeader
import com.jing.sakura.compose.common.AulamaTvColors
import com.jing.sakura.compose.common.aulamaTvBackground
import com.jing.sakura.compose.common.SpeechToTextParser
import com.jing.sakura.compose.common.customClick
import com.jing.sakura.compose.common.safelyRequestFocus
import com.jing.sakura.http.WebServerContext
import com.jing.sakura.http.WebsocketOperation
import com.jing.sakura.http.WebsocketResult
import com.jing.sakura.http.WsMessageHandler
import com.jing.sakura.room.SearchHistoryEntity
import com.jing.sakura.search.SearchResultActivity
import com.jing.sakura.search.SearchViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun SearchScreen(viewModel: SearchViewModel) {

    val context = LocalContext.current
    val onSearch = { keyword: String ->
        if (keyword.isNotBlank()) {
            keyword.trim().let {
                viewModel.saveHistory(it)
                SearchResultActivity.startActivity(context, it, viewModel.sourceId)
            }
        }
    }
    Column(
        Modifier
            .fillMaxSize()
            .aulamaTvBackground()
    ) {
        AulamaPageHeader(title = stringResource(R.string.button_search))
        InputKeywordRow(onSearch)

        Row(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 18.dp, vertical = 10.dp)
        ) {
            val serverUrl = WebServerContext.serverUrl.collectAsState().value
            if (serverUrl.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxHeight()
                        .weight(0.42f),
                    verticalArrangement = spacedBy(10.dp)
                ) {
                    AulamaSectionHeader(
                        title = stringResource(R.string.search_mobile_input),
                        modifier = Modifier.padding(horizontal = 0.dp),
                        accent = AulamaTvColors.Pink
                    )
                    val img = remember(serverUrl) {
                        val bitMatrix =
                            QRCodeWriter().encode(serverUrl, BarcodeFormat.QR_CODE, 512, 512)
                        val bitmap = Bitmap.createBitmap(
                            bitMatrix.width,
                            bitMatrix.height,
                            Bitmap.Config.RGB_565
                        )

                        for (x in 0 until bitMatrix.width) {
                            for (y in 0 until bitMatrix.height) {
                                bitmap.setPixel(
                                    x,
                                    y,
                                    if (bitMatrix[x, y]) Color.BLACK else Color.WHITE
                                )
                            }
                        }
                        bitmap
                    }

                    AsyncImage(
                        model = img,
                        contentDescription = stringResource(R.string.search_mobile_input),
                        modifier = Modifier
                            .size(230.dp)
                            .background(androidx.compose.ui.graphics.Color.White, AulamaCardShape)
                            .border(1.dp, AulamaTvColors.Outline, AulamaCardShape)
                            .padding(10.dp)
                    )
                }
                Spacer(modifier = Modifier.width(24.dp))
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .fillMaxHeight()
                        .background(AulamaTvColors.Outline)
                )
                Spacer(modifier = Modifier.width(24.dp))
            }

            val searchHistory = viewModel.searchHistoryPager.collectAsLazyPagingItems()

            if (searchHistory.loadState.refresh is LoadState.NotLoading && searchHistory.itemCount > 0) {
                Column(
                    Modifier
                        .fillMaxHeight()
                        .weight(1f)
                ) {
                    AulamaSectionHeader(
                        title = stringResource(R.string.search_history),
                        modifier = Modifier.padding(horizontal = 0.dp),
                        accent = AulamaTvColors.Amber
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    SearchHistoryColumn(viewModel = viewModel, onKeywordClick = onSearch)

                }
            }

        }

    }

}


@OptIn(
    ExperimentalPermissionsApi::class,
    ExperimentalTvMaterial3Api::class
)
@Composable
fun InputKeywordRow(onSearch: (String) -> Unit) {
    val speechFocusRequester = remember {
        FocusRequester()
    }
    val context = LocalContext.current
    val speechToTextParser = remember {
        SpeechToTextParser(context)
    }
    val permissionState = rememberPermissionState(permission = Manifest.permission.RECORD_AUDIO) {
        if (it) {
            speechToTextParser.startListening()
        }
    }
    var inputKeyword by remember {
        mutableStateOf("")
    }

    val coroutineScope = rememberCoroutineScope()

    DisposableEffect(Unit) {
        val handler = WsMessageHandler { operation, content ->
            coroutineScope.launch(Dispatchers.Main) {
                when (operation) {
                    WebsocketOperation.INPUT -> inputKeyword = content
                    WebsocketOperation.SUBMIT -> onSearch(inputKeyword)
                    else -> {}
                }
            }
            WebsocketResult.Success
        }
        WebServerContext.registerMessageHandler(handler)

        onDispose {
            WebServerContext.unregisterMessageHandler(handler)
        }

    }

    val searchButtonFocusRequester = remember {
        FocusRequester()
    }
    val sttState by speechToTextParser.state.collectAsState()
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AulamaIconButton(
            icon = if (sttState.isSpeaking) Icons.Rounded.Stop else Icons.Rounded.Mic,
            contentDescription = stringResource(R.string.speak_search_keyword),
            onClick = {
                if (sttState.isSpeaking) {
                    speechToTextParser.stopListening()
                } else {
                    if (permissionState.status.isGranted) {
                        speechToTextParser.startListening()
                    } else {
                        permissionState.launchPermissionRequest()
                    }
                }
            },
            modifier = Modifier.focusRequester(speechFocusRequester),
            accent = if (sttState.isSpeaking) AulamaTvColors.Pink else AulamaTvColors.Cyan
        )
        Spacer(modifier = Modifier.width(20.dp))
        CustomTextField(
            value = inputKeyword,
            onValueChange = { inputKeyword = it },
            modifier = Modifier.weight(1f),
            placeholder = {
                if (sttState.isSpeaking) {
                    Text(text = stringResource(R.string.speak_search_keyword))
                } else {
                    Text(text = stringResource(R.string.input_search_keyword))
                }
            }
        )
        Spacer(modifier = Modifier.width(20.dp))

        AulamaIconButton(
            icon = Icons.Default.Search,
            contentDescription = stringResource(R.string.button_search),
            onClick = {
                onSearch(inputKeyword.trim())
            },
            enabled = inputKeyword.isNotBlank(),
            modifier = Modifier.focusRequester(searchButtonFocusRequester),
            accent = AulamaTvColors.Green
        )
    }

    LaunchedEffect(sttState) {
        if (!sttState.isSpeaking) {
            val text = sttState.text.trim()
            if (text.isNotEmpty()) {
                inputKeyword = text
                searchButtonFocusRequester.safelyRequestFocus()
            } else {
                delay(200)
                speechFocusRequester.safelyRequestFocus()
            }
        }
    }
    LaunchedEffect(Unit){
        speechFocusRequester.safelyRequestFocus()
    }
    LaunchedEffect(sttState.isSpeaking) {
        if (sttState.isSpeaking) {
            delay(200)
            speechFocusRequester.requestFocus()
        }
    }
}

@Composable
fun SearchHistoryColumn(
    viewModel: SearchViewModel,
    onKeywordClick: (keyword: String) -> Unit = {}
) {
    val pagingItems = viewModel.searchHistoryPager.collectAsLazyPagingItems()
    if (pagingItems.loadState.refresh !is LoadState.NotLoading || pagingItems.itemCount == 0) {
        return
    }
    var confirmDeleteHistory by remember {
        mutableStateOf<SearchHistoryEntity?>(null)
    }
    val coroutineScope = rememberCoroutineScope()

    val listState = rememberLazyListState()
    FocusGroup {
        LazyColumn(
            state = listState, content = {
                items(pagingItems.itemCount, key = { pagingItems[it]?.keyword ?: it }) { kwIndex ->
                    val history = pagingItems[kwIndex] ?: return@items
                    Keyword(text = history.keyword,
                        modifier = Modifier
                            .run {
                                if (kwIndex == 0) {
                                    initiallyFocused()
                                } else {
                                    restorableFocus()
                                }
                            }
                            .padding(vertical = 1.dp),
                        onLongClick = {
                            confirmDeleteHistory = history
                        }) {
                        onKeywordClick(history.keyword)
                    }
                }
            }, verticalArrangement = spacedBy(10.dp)
        )
    }

    val history = confirmDeleteHistory ?: return

    val confirmText = String.format(
        stringResource(
            id = R.string.confirm_delete_template
        ), confirmDeleteHistory?.keyword
    )
    ConfirmDeleteDialog(
        text = confirmText,
        onDeleteClick = {
            confirmDeleteHistory = null
            coroutineScope.launch {
                viewModel.deleteHistory(history.keyword)
                pagingItems.refresh()
            }
        },
        onDeleteAllClick = {
            confirmDeleteHistory = null
            coroutineScope.launch {
                viewModel.deleteAllHistory()
                pagingItems.refresh()
            }
        },
        onCancel = {
            confirmDeleteHistory = null
        }
    )
}


@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun Keyword(
    text: String,
    modifier: Modifier = Modifier,
    onLongClick: () -> Unit = {},
    onClick: () -> Unit = {}
) {
    var focused by remember {
        mutableStateOf(false)
    }
    Surface(onClick = {},
        scale = ClickableSurfaceDefaults.scale(focusedScale = AulamaFocusScale),
        shape = ClickableSurfaceDefaults.shape(shape = AulamaCardShape),
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
        colors = ClickableSurfaceDefaults.colors(
            containerColor = AulamaTvColors.SurfaceRaised,
            focusedContainerColor = androidx.compose.ui.graphics.Color(0xFF173A40)
        ),
        modifier = modifier
            .onFocusChanged {
                focused = it.isFocused || it.hasFocus
            }
            .customClick(onClick, onLongClick)
    ) {
        var textModifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        if (focused) {
            textModifier = textModifier.basicMarquee()
        }
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            modifier = textModifier,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
