@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.jing.sakura.compose.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
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
fun Loading(text: String = "Loading"): Unit {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    listOf(
                        Color(0xFF08111E),
                        Color(0xFF132847),
                        Color(0xFF411A4C),
                        Color(0xFF080B12)
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .background(Color.White.copy(alpha = 0.07f), RoundedCornerShape(28.dp))
                .padding(horizontal = 32.dp, vertical = 28.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                painter = painterResource(id = R.drawable.app_icon_your_company),
                contentDescription = null,
                modifier = Modifier.size(182.dp)
            )
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "追新番、補劇場、一路看到最後一集",
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFFDDE7FF)
            )
            Spacer(modifier = Modifier.height(18.dp))
            CircularProgressIndicator()
            Spacer(modifier = Modifier.height(10.dp))
            Text(text = text, color = Color.White)
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
                    shape = MaterialTheme.shapes.extraLarge
                )
            ),
            shape = ButtonDefaults.shape(shape = MaterialTheme.shapes.extraLarge),
            scale = ButtonScale.None,
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
