@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.jing.sakura.compose.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ButtonScale
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.jing.sakura.R


@Composable
fun Loading(text: String = "載入中"): Unit {
    val transition = rememberInfiniteTransition(label = "brandLoading")
    val pulse = transition.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.02f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900),
            repeatMode = RepeatMode.Reverse
        ),
        label = "brandPulse"
    )
    Box(
        modifier = Modifier
            .fillMaxSize()
            .aulamaTvBackground()
    ) {
        Column(
            modifier = Modifier.align(Alignment.Center),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                painter = painterResource(id = R.drawable.aulama_anime_icon),
                contentDescription = null,
                modifier = Modifier
                    .size(88.dp)
                    .scale(pulse.value)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Image(
                painter = painterResource(id = R.drawable.aulama_anime_wordmark),
                contentDescription = stringResource(R.string.app_name),
                modifier = Modifier
                    .size(width = 174.dp, height = 64.dp)
                    .alpha(0.96f)
            )
            Spacer(modifier = Modifier.height(6.dp))
            CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                color = AulamaTvColors.Cyan,
                strokeWidth = 2.dp
            )
            if (text.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = text, color = AulamaTvColors.TextSecondary)
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun ErrorTip(message: String, retry: () -> Unit = { }) {
    val focusRequester = remember {
        FocusRequester()
    }
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = message)
        Spacer(modifier = Modifier.height(10.dp))
        Button(
            onClick = retry,
            modifier = Modifier
                .focusRequester(focusRequester),
            border = ButtonDefaults.border(
                focusedBorder = Border(
                    BorderStroke(2.dp, MaterialTheme.colorScheme.border),
                    shape = AulamaCardShape
                )
            ),
            shape = ButtonDefaults.shape(shape = AulamaCardShape),
            scale = ButtonDefaults.scale(focusedScale = AulamaFocusScale),
            colors = ButtonDefaults.colors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f),
                contentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f),
                focusedContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
            )
        ) {
            Text(text = stringResource(R.string.button_retry))
        }
    }
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
}
