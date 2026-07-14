package com.jing.sakura.compose.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import com.jing.sakura.R
import com.jing.sakura.repo.AnimationSource


@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun ChangeSourceDialog(
    allSources: List<AnimationSource>,
    currentSourceId: String,
    onDismissRequest: () -> Unit,
    onChangeSource: (sourceId: String) -> Unit,
) {
    val defaultIndex = remember(allSources, currentSourceId) {
        allSources.indexOfFirst { it.sourceId == currentSourceId }.coerceAtLeast(0)
    }
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        val listState =
            rememberLazyListState(initialFirstVisibleItemIndex = defaultIndex)
        val focusRequester = remember {
            FocusRequester()
        }
        Column(
            modifier = Modifier
                .clip(AulamaCardShape)
                .background(AulamaTvColors.Surface)
                .border(1.dp, AulamaTvColors.Outline, AulamaCardShape)
                .padding(20.dp)
                .width(400.dp)

        ) {
            Text(
                text = stringResource(R.string.choose_animation_source),
                style = MaterialTheme.typography.headlineSmall,
                color = AulamaTvColors.TextPrimary
            )
            Spacer(modifier = Modifier.height(10.dp))
            LazyColumn(state = listState, content = {
                items(count = allSources.size) { sourceIndex ->
                    val source = allSources[sourceIndex]
                    val modifier = Modifier.run {
                        if (sourceIndex == defaultIndex) {
                            focusRequester(focusRequester)
                        } else {
                            this
                        }
                    }
                    SourceItem(
                        source = source,
                        modifier = modifier,
                        textColor = if (currentSourceId == source.sourceId) colorResource(id = R.color.cyan300) else MaterialTheme.colorScheme.onSurface
                    ) {
                        onChangeSource(source.sourceId)
                    }
                }
            })
            LaunchedEffect(Unit) {
                focusRequester.requestFocus()
            }
        }
    }
}

@Composable
fun SourceItem(
    modifier: Modifier = Modifier,
    textColor: Color,
    source: AnimationSource,
    onClick: () -> Unit
) {
    var focused by remember {
        mutableStateOf(false)
    }
    val interactionSource = remember {
        MutableInteractionSource()
    }
    Text(text = source.name.toDisplayLineName(),
        color = textColor,
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = if (focused) AulamaFocusScale else 1f
                scaleY = if (focused) AulamaFocusScale else 1f
            }
            .background(if (focused) AulamaTvColors.SurfaceRaised else Color.Transparent, AulamaCardShape)
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = if (focused) AulamaTvColors.FocusBorder else AulamaTvColors.Outline,
                shape = AulamaCardShape
            )
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .onFocusChanged {
                focused = it.isFocused || it.hasFocus
            }
            .focusable()
            .clickable(
                onClick = onClick,
                indication = null,
                interactionSource =interactionSource
            )
    )

}
