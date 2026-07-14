@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)

package com.jing.sakura.compose.screen

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.jing.sakura.R
import com.jing.sakura.auth.AulamaAccount
import com.jing.sakura.auth.AuthUiState
import com.jing.sakura.auth.DeviceCode
import com.jing.sakura.compose.common.AulamaActionButton
import com.jing.sakura.compose.common.AulamaCardShape
import com.jing.sakura.compose.common.AulamaTvColors
import com.jing.sakura.compose.common.aulamaTvBackground

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun DeviceLoginScreen(
    state: AuthUiState,
    onRetry: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .aulamaTvBackground()
            .padding(horizontal = 64.dp, vertical = 42.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(64.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(0.9f),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                Image(
                    painter = painterResource(R.drawable.aulama_anime_wordmark),
                    contentDescription = null,
                    modifier = Modifier.size(width = 250.dp, height = 78.dp)
                )
                Text(
                    text = "連接你嘅 Aulama 帳戶",
                    style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = AulamaTvColors.TextPrimary
                )
                Text(
                    text = "用手機或電腦開啟右邊網址，再輸入裝置碼。",
                    style = MaterialTheme.typography.bodyLarge,
                    color = AulamaTvColors.TextSecondary
                )
            }

            Crossfade(
                targetState = state::class,
                animationSpec = tween(durationMillis = 200),
                label = "device-login-state",
                modifier = Modifier.weight(1.1f)
            ) {
                LoginStatePanel(state, onRetry)
            }
        }
    }
}

@Composable
private fun LoginStatePanel(state: AuthUiState, onRetry: () -> Unit) {
    val code = when (state) {
        is AuthUiState.Waiting -> state.code
        is AuthUiState.RateLimited -> state.code
        is AuthUiState.Expired -> state.code
        else -> null
    }
    val remaining = when (state) {
        is AuthUiState.Waiting -> state.remainingSeconds
        is AuthUiState.RateLimited -> state.remainingSeconds
        else -> 0L
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(AulamaTvColors.Surface.copy(alpha = 0.94f))
            .border(BorderStroke(1.dp, AulamaTvColors.Outline), RoundedCornerShape(8.dp))
            .padding(30.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        if (code != null) {
            DeviceCodeContent(code, remaining)
        } else if (state is AuthUiState.Checking || state is AuthUiState.RequestingCode) {
            CircularProgressIndicator(color = AulamaTvColors.Cyan, modifier = Modifier.size(42.dp))
        }

        val status = when (state) {
            AuthUiState.Checking -> "正在檢查登入狀態"
            AuthUiState.RequestingCode -> "正在取得裝置碼"
            is AuthUiState.Waiting -> if (state.pending) "等待你確認登入" else "裝置碼已準備好"
            is AuthUiState.RateLimited -> "每小時最多嘗試 3 次，請於 ${state.retryAfterSeconds} 秒後重試"
            is AuthUiState.Expired -> "裝置碼已過期"
            is AuthUiState.Error -> state.message
            is AuthUiState.Authenticated -> "登入成功"
        }
        Text(
            text = status,
            style = MaterialTheme.typography.titleMedium,
            color = if (state is AuthUiState.Error || state is AuthUiState.Expired) {
                AulamaTvColors.Pink
            } else {
                AulamaTvColors.TextSecondary
            },
            textAlign = TextAlign.Center
        )

        if (state is AuthUiState.Expired || state is AuthUiState.Error ||
            state is AuthUiState.RateLimited || state is AuthUiState.Waiting
        ) {
            AnimatedLoginButton(
                label = if (state is AuthUiState.Waiting) "重新取得裝置碼" else "重試",
                onClick = onRetry,
                enabled = state !is AuthUiState.RateLimited
            )
        }
    }
}

@Composable
private fun DeviceCodeContent(code: DeviceCode, remainingSeconds: Long) {
    Text(
        text = "裝置碼",
        style = MaterialTheme.typography.labelLarge,
        color = AulamaTvColors.TextSecondary
    )
    Text(
        text = code.userCode,
        fontSize = 44.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.sp,
        color = AulamaTvColors.TextPrimary,
        maxLines = 1
    )
    Text(
        text = code.verificationUri,
        style = MaterialTheme.typography.titleLarge,
        color = AulamaTvColors.Cyan,
        textAlign = TextAlign.Center
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        androidx.tv.material3.Icon(
            imageVector = Icons.Default.Timer,
            contentDescription = null,
            tint = AulamaTvColors.Amber,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = "%d:%02d".format(remainingSeconds / 60, remainingSeconds % 60),
            style = MaterialTheme.typography.titleMedium,
            color = AulamaTvColors.TextPrimary
        )
    }
}

@Composable
private fun AnimatedLoginButton(label: String, onClick: () -> Unit, enabled: Boolean) {
    val focusRequester = remember { FocusRequester() }
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.04f else 1f,
        animationSpec = tween(180),
        label = "login-button-scale"
    )
    val alpha by animateFloatAsState(
        targetValue = if (focused) 1f else 0.9f,
        animationSpec = tween(180),
        label = "login-button-alpha"
    )
    AulamaActionButton(
        label = label,
        icon = Icons.Default.Refresh,
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .heightIn(min = 48.dp)
            .focusRequester(focusRequester)
            .onFocusChanged { focused = it.hasFocus }
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                this.alpha = alpha
            }
    )
    LaunchedEffect(enabled) {
        if (enabled) focusRequester.requestFocus()
    }
}

@Composable
fun AccountDialog(
    account: AulamaAccount,
    onDismiss: () -> Unit,
    onLogout: () -> Unit
) {
    val dismissFocus = remember { FocusRequester() }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .width(440.dp)
                .clip(AulamaCardShape)
                .background(AulamaTvColors.SurfaceRaised)
                .border(1.dp, AulamaTvColors.Outline, AulamaCardShape)
                .padding(28.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Text(
                text = account.name,
                style = MaterialTheme.typography.headlineSmall,
                color = AulamaTvColors.TextPrimary,
                maxLines = 1
            )
            Text(
                text = account.role,
                style = MaterialTheme.typography.bodyLarge,
                color = AulamaTvColors.TextSecondary,
                maxLines = 1
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End)
            ) {
                AulamaActionButton(
                    label = "返回",
                    onClick = onDismiss,
                    modifier = Modifier.heightIn(min = 48.dp).focusRequester(dismissFocus)
                )
                AulamaActionButton(
                    label = "登出",
                    icon = Icons.Default.Logout,
                    accent = AulamaTvColors.Pink,
                    onClick = onLogout,
                    modifier = Modifier.heightIn(min = 48.dp)
                )
            }
        }
        LaunchedEffect(Unit) { dismissFocus.requestFocus() }
    }
}
